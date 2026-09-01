/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE Serve is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * SMILE Serve is distributed in the hope that it will be useful,
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with SMILE. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.serve;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
import jakarta.ws.rs.NotFoundException;
import org.jboss.logging.Logger;
import smile.onnx.InferenceSession;
import smile.io.Paths;
import smile.serve.model.ModelObject;
import smile.serve.model.OnnxModelDetails;
import smile.serve.model.OpenAiModelContributor;

/**
 * Application-scoped service that discovers, loads, and manages ONNX models
 * ({@code *.onnx}) at startup.
 *
 * <p>The model path is configured via {@code smile.onnx.model}. It may point
 * to a single {@code .onnx} file or to a directory; in the latter case every
 * {@code .onnx} file in the directory is loaded.
 *
 * <p>If the configured path does not exist or contains no ONNX models this
 * service starts empty and all list/predict requests return 404.
 *
 * @author Haifeng Li
 */
@Startup
@ApplicationScoped
public class OnnxService implements OpenAiModelContributor {
    private static final Logger logger = Logger.getLogger(OnnxService.class);
    /** Loaded models, keyed by model ID. Sorted for stable list order. */
    private final Map<String, OnnxModel> models = Collections.synchronizedSortedMap(new TreeMap<>());

    /**
     * Loads ONNX models upon application start.
     *
     * @param config the ONNX service configuration.
     */
    @Inject
    public OnnxService(OnnxServiceConfig config) {
        var path = Path.of(config.model()).toAbsolutePath().normalize();
        if (Files.isRegularFile(path) && path.toString().endsWith(".onnx")) {
            loadModel(path);
        } else if (Files.isDirectory(path)) {
            try (Stream<Path> files = Files.list(path)) {
                files.filter(f -> Files.isRegularFile(f) && f.toString().endsWith(".onnx"))
                     .forEach(this::loadModel);
            } catch (IOException ex) {
                logger.errorf(ex, "Failed to list ONNX model directory '%s'", path);
            }
        } else {
            logger.infof("ONNX model path '%s' not found or not a .onnx file — ONNX service starting empty.", path);
        }
    }

    /**
     * Loads a single ONNX model file and registers it.
     *
     * @param path the {@code .onnx} file path.
     */
    private void loadModel(Path path) {
        try {
            logger.infof("Loading ONNX model from '%s'", path);
            String id = Paths.getFileName(path);
            var session = InferenceSession.create(path.toString());
            var model = new OnnxModel(id, path, session);
            models.put(id, model);
            logger.infof("ONNX model '%s' loaded successfully (inputs=%s, outputs=%s)",
                    id, session.inputNames(), session.outputNames());
        } catch (Throwable ex) {
            // ExceptionInInitializerError / NoClassDefFoundError are Errors, not
            // Exceptions — typically missing libonnxruntime on java.library.path.
            logger.errorf(ex,
                    "Failed to load ONNX model from '%s'. "
                            + "Ensure the onnxruntime native library (%s) is on "
                            + "java.library.path (or the OS library search path) and "
                            + "the JVM is started with --enable-native-access=ALL-UNNAMED. "
                            + "ONNX endpoints will return 404 until a model loads.",
                    path, System.mapLibraryName("onnxruntime"));
        }
    }

    /**
     * Returns OpenAI-shaped descriptors for every loaded ONNX model.
     *
     * <p>{@code owned_by} uses ONNX custom metadata {@code author}/{@code owner}
     * when present; otherwise {@link ModelObject#UNKNOWN_OWNER}.
     *
     * @return OpenAI model objects in id order.
     */
    @Override
    public List<ModelObject> listOpenAiModels() {
        List<ModelObject> result = new ArrayList<>();
        for (OnnxModel model : models.values()) {
            result.add(ModelObject.of(
                    model.id(),
                    ModelObject.createdFromPath(model.path()),
                    ModelObject.ownedByFromMap(model.info().customMeta()),
                    ModelObject.KIND_ONNX));
        }
        return result;
    }

    /**
     * Looks up a loaded ONNX model as an OpenAI {@link ModelObject}.
     *
     * @param id       the model id.
     * @param detailed when {@code true}, include {@link OnnxModelDetails}.
     * @return the model object when loaded; otherwise empty.
     */
    @Override
    public Optional<ModelObject> findOpenAiModel(String id, boolean detailed) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        OnnxModel model = models.get(id);
        if (model == null) {
            return Optional.empty();
        }
        OnnxModelDetails onnx = detailed ? model.details() : null;
        return Optional.of(ModelObject.of(
                model.id(),
                ModelObject.createdFromPath(model.path()),
                ModelObject.ownedByFromMap(model.info().customMeta()),
                ModelObject.KIND_ONNX,
                null,
                onnx,
                null));
    }

    /**
     * Looks up a loaded ONNX model as a lean OpenAI {@link ModelObject}.
     *
     * @param id the model id.
     * @return the model object when loaded; otherwise empty.
     */
    public Optional<ModelObject> findOpenAiModel(String id) {
        return findOpenAiModel(id, false);
    }

    /**
     * Returns the ONNX model with the given ID.
     *
     * @param id the model ID.
     * @return the model instance.
     * @throws NotFoundException if no model with that ID has been loaded.
     */
    public OnnxModel getModel(String id) throws NotFoundException {
        var model = models.get(id);
        if (model == null) throw new NotFoundException("ONNX model not found: " + id);
        return model;
    }

    /**
     * Runs ONNX inference with JSON-encoded inputs.
     *
     * @param modelId the model ID.
     * @param request JSON object mapping input names to flat numeric arrays.
     * @return JSON object mapping output names to flat numeric arrays.
     * @throws BadRequestException if the request is malformed.
     * @throws NotFoundException   if the model ID is unknown.
     */
    public JsonObject predict(String modelId, JsonObject request)
            throws BadRequestException, NotFoundException {
        return getModel(modelId).predict(request);
    }
}

