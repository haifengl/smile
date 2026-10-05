/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE Serve is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * SMILE Serve is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public
 * License along with SMILE Serve. If not, see
 * <https://www.gnu.org/licenses/>.
 */
package smile.serve;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Stream;
import io.quarkus.runtime.Startup;
import io.vertx.core.json.JsonObject;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.ClientErrorException;
import jakarta.ws.rs.InternalServerErrorException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.core.Response;
import org.jboss.logging.Logger;
import smile.io.Read;
import smile.model.Model;
import smile.model.Prediction;
import smile.serve.model.ModelObject;
import smile.serve.model.OpenAiModelContributor;
import smile.serve.model.SmileModelDetails;

/**
 * Application-scoped service that loads and manages serialized SMILE
 * models ({@code *.sml}) and delegates inference requests to them.
 *
 * <p>Models are discovered once at startup from the path configured by
 * {@code smile.serve.model}. The path may point to a single {@code .sml}
 * file or to a directory; in the latter case every {@code .sml} file in
 * the directory is loaded.
 *
 * @author Haifeng Li
 */
@Startup
@ApplicationScoped
public class InferenceService implements OpenAiModelContributor {
    private static final Logger logger = Logger.getLogger(InferenceService.class);
    /** Loaded models, keyed by {@code <id>-<version>}. Sorted for stable list order. */
    private final Map<String, InferenceModel> models = Collections.synchronizedSortedMap(new TreeMap<>());
    private final Map<String, Path> modelPaths = Collections.synchronizedMap(new TreeMap<>());
    /** Registers/removes Prometheus meters as models load and unload. */
    private final ModelMetricsBinder metricsBinder;

    /**
     * Loads ML models upon application start.
     * The {@code @ApplicationScoped} scope ensures the models are loaded once and reused.
     *
     * @param config the service configuration.
     * @param metricsBinder the Prometheus meter binder.
     */
    @Inject
    public InferenceService(InferenceServiceConfig config, ModelMetricsBinder metricsBinder) {
        this.metricsBinder = metricsBinder;
        var path = Path.of(config.model()).toAbsolutePath().normalize();
        if (Files.isRegularFile(path)) {
            loadModel(path);
        } else if (Files.isDirectory(path)) {
            try (Stream<Path> files = Files.list(path)) {
                files.filter(file -> Files.isRegularFile(file) && file.toString().endsWith(".sml"))
                     .forEach(this::loadModel);
            } catch (IOException ex) {
                logger.errorf(ex, "Failed to list model directory '%s'", path);
            }
        } else {
            logger.errorf("'%s' is not a regular file or directory", path);
        }
    }

    /**
     * Loads a single model file and registers it by its ID.
     *
     * @param path the model file path.
     */
    private void loadModel(Path path) {
        try {
            logger.infof("Loading model from '%s'", path);
            var obj = Read.object(path);
            if (obj instanceof Model m) {
                var model = new InferenceModel(m, path);
                models.put(model.id(), model);
                modelPaths.put(model.id(), path);
                metricsBinder.register(ModelMetricsBinder.SMILE_NAMESPACE, model.id(), model.metrics());
                logger.infof("Model '%s' loaded successfully", model.id());
            } else {
                logger.errorf("'%s' does not contain a valid SMILE model (got %s)",
                        path, obj == null ? "null" : obj.getClass().getName());
            }
        } catch (Exception ex) {
            logger.errorf(ex, "Failed to load model from '%s'", path);
        }
    }

    /**
     * Returns OpenAI-shaped descriptors for every loaded SMILE {@code .sml} model.
     *
     * <p>{@code owned_by} comes from the model tag {@code author} or {@code owner}
     * when present; otherwise {@link ModelObject#UNKNOWN_OWNER}.
     *
     * @return OpenAI model objects in id order.
     */
    @Override
    public List<ModelObject> listOpenAiModels() {
        List<ModelObject> result = new ArrayList<>();
        for (InferenceModel model : models.values()) {
            var meta = model.metadata();
            result.add(ModelObject.of(
                    model.id(),
                    ModelObject.createdFromPath(model.path()),
                    ModelObject.ownedByFromTags(meta.tags()),
                    meta.algorithm()));
        }
        return result;
    }

    /**
     * Looks up a loaded SMILE model as an OpenAI {@link ModelObject}.
     *
     * @param id       the model id.
     * @param detailed when {@code true}, include {@link SmileModelDetails}.
     * @return the model object when loaded; otherwise empty.
     */
    @Override
    public Optional<ModelObject> findOpenAiModel(String id, boolean detailed) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        InferenceModel model = models.get(id);
        if (model == null) {
            return Optional.empty();
        }
        var meta = model.metadata();
        SmileModelDetails smile = detailed ? SmileModelDetails.of(model.model(), meta) : null;
        return Optional.of(ModelObject.of(
                model.id(),
                ModelObject.createdFromPath(model.path()),
                ModelObject.ownedByFromTags(meta.tags()),
                meta.algorithm(),
                smile,
                null,
                null));
    }

    /**
     * Looks up a loaded SMILE model as a lean OpenAI {@link ModelObject}.
     *
     * @param id the model id.
     * @return the model object when loaded; otherwise empty.
     */
    public Optional<ModelObject> findOpenAiModel(String id) {
        return findOpenAiModel(id, false);
    }

    /**
     * Returns the model with the given ID.
     *
     * @param id the model ID.
     * @return the model instance.
     * @throws NotFoundException if no model with that ID has been loaded.
     */
    public InferenceModel getModel(String id) throws NotFoundException {
        var model = models.get(id);
        if (model == null) throw new NotFoundException("Model not found: " + id);
        return model;
    }

    /**
     * Returns a snapshot of the loaded models keyed by id, in id order.
     *
     * @return an immutable snapshot of the loaded models.
     */
    public Map<String, InferenceModel> models() {
        synchronized (models) {
            return Map.copyOf(models);
        }
    }

    /**
     * Performs inference using JSON-encoded input.
     *
     * @param modelId the model ID.
     * @param request the feature values as a JSON object.
     * @return the inference result.
     * @throws BadRequestException if the request body is malformed.
     * @throws NotFoundException   if the model ID is unknown.
     */
    public Prediction predict(String modelId, JsonObject request)
            throws BadRequestException, NotFoundException {
        return predict(modelId, request, false);
    }

    /**
     * Performs inference using JSON-encoded input with optional explanations.
     *
     * @param modelId the model ID.
     * @param request the feature values as a JSON object.
     * @param explain whether to generate model explanations.
     * @return the inference result.
     * @throws BadRequestException if the request body is malformed.
     * @throws NotFoundException   if the model ID is unknown.
     */
    public Prediction predict(String modelId, JsonObject request, boolean explain)
            throws BadRequestException, NotFoundException {
        return getModel(modelId).predict(request, explain);
    }

    /**
     * Reloads a model from its backing file on disk.
     * If the model was previously unloaded, it is reloaded from its known file path.
     *
     * @param id the model ID.
     * @return the reloaded inference model.
     * @throws NotFoundException if the model or its file does not exist.
     * @throws InternalServerErrorException if the file fails to deserialize.
     */
    public InferenceModel reloadModel(String id) throws NotFoundException, InternalServerErrorException {
        Path path = modelPaths.get(id);
        if (path == null) {
            InferenceModel existing = models.get(id);
            if (existing != null) {
                path = existing.path();
            }
        }
        if (path == null || !Files.isRegularFile(path)) {
            throw new NotFoundException("Model file not found on disk: " + path);
        }
        try {
            logger.infof("Reloading model '%s' from '%s'", id, path);
            var obj = Read.object(path);
            if (obj instanceof Model m) {
                var reloaded = new InferenceModel(m, path);
                models.put(reloaded.id(), reloaded);
                modelPaths.put(reloaded.id(), path);
                metricsBinder.unregister(ModelMetricsBinder.SMILE_NAMESPACE, reloaded.id());
                metricsBinder.register(ModelMetricsBinder.SMILE_NAMESPACE, reloaded.id(), reloaded.metrics());
                logger.infof("Model '%s' reloaded successfully", reloaded.id());
                return reloaded;
            } else {
                throw new InternalServerErrorException("File does not contain a valid SMILE model: " + path);
            }
        } catch (Exception ex) {
            logger.errorf(ex, "Failed to reload model '%s' from '%s'", id, path);
            throw new InternalServerErrorException("Failed to reload model: " + ex.getMessage(), ex);
        }
    }

    /**
     * Gracefully unloads a model from active memory, waiting for in-flight requests to complete.
     *
     * @param id           the model ID.
     * @param graceTimeout max duration to wait for in-flight requests.
     * @return {@code true} if all in-flight requests completed before timeout.
     * @throws NotFoundException if the model is not found.
     */
    public boolean unloadModel(String id, Duration graceTimeout) throws NotFoundException {
        InferenceModel existing = getModel(id);
        boolean drained = existing.unload(graceTimeout);
        models.remove(id);
        metricsBinder.unregister(ModelMetricsBinder.SMILE_NAMESPACE, id);
        logger.infof("Model '%s' unloaded (drained in-flight: %s)", id, drained);
        return drained;
    }

    /**
     * Dynamically loads a model or directory of models from disk into active memory.
     * Rejects with 409 Conflict if any model with the same ID is already loaded.
     *
     * @param path file or directory path.
     * @return list of newly loaded inference models.
     * @throws NotFoundException if path does not exist.
     * @throws ClientErrorException if model with same ID is already loaded.
     * @throws BadRequestException if the file is invalid.
     */
    public List<InferenceModel> load(Path path) throws NotFoundException, ClientErrorException, BadRequestException {
        Path absPath = path.toAbsolutePath().normalize();
        if (!Files.exists(absPath)) {
            throw new NotFoundException("Path does not exist: " + path);
        }
        List<Path> smlFiles = new ArrayList<>();
        if (Files.isRegularFile(absPath)) {
            smlFiles.add(absPath);
        } else if (Files.isDirectory(absPath)) {
            try (Stream<Path> stream = Files.list(absPath)) {
                stream.filter(f -> Files.isRegularFile(f) && f.toString().endsWith(".sml"))
                      .forEach(smlFiles::add);
            } catch (IOException e) {
                throw new BadRequestException("Failed to read directory: " + path, e);
            }
        }
        if (smlFiles.isEmpty()) {
            throw new BadRequestException("No .sml models found at: " + path);
        }

        List<InferenceModel> loaded = new ArrayList<>();
        for (Path file : smlFiles) {
            Object obj;
            try {
                obj = Read.object(file);
            } catch (Exception e) {
                throw new BadRequestException("Failed to read model from " + file + ": " + e.getMessage(), e);
            }
            if (!(obj instanceof Model m)) {
                throw new BadRequestException("File does not contain a valid SMILE model: " + file);
            }
            var model = new InferenceModel(m, file);
            if (models.containsKey(model.id())) {
                throw new ClientErrorException(
                        "Model '" + model.id() + "' is already loaded. Use POST /api/v1/smile/" + model.id() + "/reload to refresh it, or unload it first.",
                        Response.Status.CONFLICT);
            }
            loaded.add(model);
        }

        for (InferenceModel model : loaded) {
            models.put(model.id(), model);
            modelPaths.put(model.id(), model.path());
            metricsBinder.register(ModelMetricsBinder.SMILE_NAMESPACE, model.id(), model.metrics());
            logger.infof("Dynamically loaded model '%s' from '%s'", model.id(), model.path());
        }
        return loaded;
    }
}
