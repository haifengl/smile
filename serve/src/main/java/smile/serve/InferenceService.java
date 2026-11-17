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
import java.io.InvalidClassException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Stream;
import io.quarkus.arc.All;
import io.quarkus.runtime.Startup;
import io.vertx.core.json.JsonObject;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.ClientErrorException;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.InternalServerErrorException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.core.Response;
import org.jboss.logging.Logger;
import smile.feature.importance.KernelSHAP;
import smile.model.Model;
import smile.model.Prediction;
import smile.serve.model.ModelObject;
import smile.serve.model.OpenAiModelContributor;
import smile.serve.model.QuarantinedModel;
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
    /** Models on disk that failed verification or deserialization. */
    private final Map<Path, QuarantinedModel> quarantined = Collections.synchronizedMap(new TreeMap<>());
    /** Registers/removes telemetry meters as models load and unload. */
    private final List<ModelMetricsBinder> metricsBinders;
    /** Observes inference executions and model lifecycle events. */
    private final List<InferenceListener> listeners;
    /** Synchronous pre-deserialization model artifact verifiers. */
    private final List<ModelArtifactVerifier> verifiers;

    /**
     * Loads ML models upon application start.
     *
     * @param config         the service configuration.
     * @param metricsBinders telemetry meter binders discovered on the classpath.
     */
    public InferenceService(InferenceServiceConfig config, List<ModelMetricsBinder> metricsBinders) {
        this(config, metricsBinders, List.of(), List.of());
    }

    /**
     * Loads ML models upon application start.
     *
     * @param config         the service configuration.
     * @param metricsBinders telemetry meter binders discovered on the classpath.
     * @param listeners      inference and lifecycle listeners discovered on the classpath.
     */
    public InferenceService(InferenceServiceConfig config,
                            List<ModelMetricsBinder> metricsBinders,
                            List<InferenceListener> listeners) {
        this(config, metricsBinders, listeners, List.of());
    }

    /**
     * Loads ML models upon application start.
     * The {@code @ApplicationScoped} scope ensures the models are loaded once and reused.
     *
     * @param config         the service configuration.
     * @param metricsBinders telemetry meter binders discovered on the classpath.
     * @param listeners      inference and lifecycle listeners discovered on the classpath.
     * @param verifiers      synchronous pre-deserialization model artifact verifiers.
     */
    @Inject
    public InferenceService(InferenceServiceConfig config,
                            @All List<ModelMetricsBinder> metricsBinders,
                            @All List<InferenceListener> listeners,
                            @All List<ModelArtifactVerifier> verifiers) {
        this.metricsBinders = metricsBinders == null ? List.of() : List.copyOf(metricsBinders);
        this.listeners = listeners == null ? List.of() : List.copyOf(listeners);
        this.verifiers = verifiers == null ? List.of() : List.copyOf(verifiers);
        var path = Path.of(config.model()).toAbsolutePath().normalize();
        if (Files.isRegularFile(path)) {
            loadModel(path);
        } else if (Files.isDirectory(path)) {
            try (Stream<Path> files = Files.list(path)) {
                files.filter(file -> Files.isRegularFile(file) && file.toString().endsWith(".sml"))
                     .forEach(this::loadModel);
            } catch (IOException ex) {
                logger.errorf(ex, "Failed to list model directory '%s'", path);
                recordQuarantine(path, ex);
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
        Path normalized = path.toAbsolutePath().normalize();
        try {
            logger.infof("Loading model from '%s'", normalized);
            Model m = verifyAndReadModel(normalized);
            var model = new InferenceModel(m, normalized);
            models.put(model.id(), model);
            modelPaths.put(model.id(), normalized);
            quarantined.remove(normalized);
            metricsBinders.forEach(b -> b.register(ModelMetricsBinder.SMILE_NAMESPACE, model.id(), model.metrics()));
            notifyListeners(InferenceEvent.lifecycle(
                    ModelMetricsBinder.SMILE_NAMESPACE,
                    model.id(),
                    InferenceEvent.Action.MODEL_LOADED,
                    normalized.toString()));
            logger.infof("Model '%s' loaded successfully", model.id());
        } catch (Throwable ex) {
            logger.errorf(ex, "Failed to load model from '%s'", normalized);
            recordQuarantine(normalized, ex);
        }
    }

    private Model verifyAndReadModel(Path path) throws Exception {
        long t0 = System.nanoTime();
        String fileName = path.getFileName() != null ? path.getFileName().toString() : path.toString();
        try {
            for (ModelArtifactVerifier verifier : verifiers) {
                verifier.verify(ModelMetricsBinder.SMILE_NAMESPACE, path);
            }
            Model m = Model.read(path);
            for (ModelArtifactVerifier verifier : verifiers) {
                verifier.verifyLoadedSmileModel(path, m);
            }
            if (!verifiers.isEmpty() && !listeners.isEmpty()) {
                String modelId = m.getTag(Model.ID, smile.io.Paths.getFileName(path)) + "-"
                        + m.getTag(Model.VERSION, "1");
                boolean allVerified = true;
                String details = path.toString();
                for (ModelArtifactVerifier verifier : verifiers) {
                    var outcome = verifier.verificationOutcome(path);
                    if (outcome.isPresent()) {
                        allVerified &= outcome.get().verified();
                        if (outcome.get().details() != null && !outcome.get().details().isBlank()) {
                            details = outcome.get().details();
                        }
                    }
                }
                notifyListeners(InferenceEvent.verification(
                        ModelMetricsBinder.SMILE_NAMESPACE,
                        modelId,
                        allVerified,
                        details,
                        System.nanoTime() - t0));
            }
            return m;
        } catch (SecurityException ex) {
            if (!listeners.isEmpty()) {
                String details = ex.getMessage() != null ? ex.getMessage() : path.toString();
                for (ModelArtifactVerifier verifier : verifiers) {
                    var outcome = verifier.verificationOutcome(path);
                    if (outcome.isPresent() && outcome.get().details() != null && !outcome.get().details().isBlank()) {
                        details = outcome.get().details();
                    }
                }
                notifyListeners(InferenceEvent.verification(
                        ModelMetricsBinder.SMILE_NAMESPACE,
                        fileName,
                        false,
                        details,
                        System.nanoTime() - t0));
            }
            throw ex;
        }
    }

    private void recordQuarantine(Path path, Throwable ex) {
        boolean fatal = isFatalLoadFailure(ex);
        quarantined.put(path, new QuarantinedModel(
                path.toString(),
                ModelMetricsBinder.SMILE_NAMESPACE,
                ex.getClass().getName(),
                ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName(),
                fatal,
                Instant.now().getEpochSecond()));
    }

    /**
     * Classifies whether a model load exception is a fatal security/format error
     * ({@code true}) vs. a benign class-version incompatibility ({@code false}).
     *
     * <p>{@link InvalidClassException} thrown by a {@code serialVersionUID}
     * mismatch is non-fatal (eligible for quarantine when {@code fail-fast=false}),
     * whereas {@code ObjectInputFilter} rejections ({@code "filter status: REJECTED"}),
     * {@link SecurityException}, and {@code SMLM} format/CRC errors are always fatal.
     */
    static boolean isFatalLoadFailure(Throwable ex) {
        if (ex instanceof SecurityException) {
            return true;
        }
        if (ex instanceof InvalidClassException ice) {
            String msg = ice.getMessage();
            return msg == null
                    || msg.contains("filter status: REJECTED")
                    || !msg.contains("serialVersionUID");
        }
        return true;
    }

    @Override
    public List<QuarantinedModel> listQuarantinedModels() {
        synchronized (quarantined) {
            return List.copyOf(quarantined.values());
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
        synchronized (models) {
            for (InferenceModel model : models.values()) {
                var meta = model.metadata();
                result.add(ModelObject.of(
                        model.id(),
                        ModelObject.createdFromPath(model.path()),
                        ModelObject.ownedByFromTags(meta.tags()),
                        meta.algorithm()));
            }
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
        return predict(modelId, request, explain, null);
    }

    /**
     * Performs inference using JSON-encoded input with optional explanations
     * and custom {@link KernelSHAP.Options}.
     *
     * @param modelId     the model ID.
     * @param request     the feature values as a JSON object.
     * @param explain     whether to generate model explanations.
     * @param shapOptions optional KernelSHAP options (may be {@code null}).
     * @return the inference result.
     * @throws BadRequestException if the request body is malformed.
     * @throws NotFoundException   if the model ID is unknown.
     */
    public Prediction predict(String modelId, JsonObject request, boolean explain, KernelSHAP.Options shapOptions)
            throws BadRequestException, NotFoundException {
        long start = System.nanoTime();
        Prediction prediction = getModel(modelId).predict(request, explain, shapOptions);
        if (!listeners.isEmpty()) {
            notifyListeners(InferenceEvent.smilePredict(
                    modelId,
                    InferenceEvent.Action.PREDICT,
                    request != null ? request.encode() : null,
                    prediction,
                    System.nanoTime() - start,
                    explain));
        }
        return prediction;
    }

    /**
     * Notifies registered {@link InferenceListener} plugins of an event.
     *
     * @param event the event to publish.
     */
    void notifyListeners(InferenceEvent event) {
        for (InferenceListener listener : listeners) {
            try {
                listener.onEvent(event);
            } catch (Throwable ex) {
                logger.warnf(ex, "InferenceListener '%s' threw an exception on %s",
                        listener.getClass().getName(), event.action());
            }
        }
    }

    /**
     * Returns true if at least one {@link InferenceListener} is registered.
     *
     * @return true when listeners are present.
     */
    boolean hasListeners() {
        return !listeners.isEmpty();
    }

    /**
     * Online updates a model using JSON-encoded input, optionally persisting to disk.
     *
     * @param modelId the model ID.
     * @param request the feature (and optional target) values as a JSON object.
     * @param persist whether to save the updated model to its {@code .sml} file on disk.
     * @throws BadRequestException if the model is not online-capable or the request is malformed.
     * @throws NotFoundException   if the model ID is unknown.
     */
    public void update(String modelId, JsonObject request, boolean persist)
            throws BadRequestException, NotFoundException {
        long start = System.nanoTime();
        InferenceModel model = getModel(modelId);
        if (persist) {
            checkBeforePersist(model);
        }
        model.update(request);
        if (persist) {
            persistModel(model);
        }
        if (!listeners.isEmpty()) {
            notifyListeners(InferenceEvent.onlineUpdate(
                    modelId,
                    request != null ? request.encode() : null,
                    persist,
                    System.nanoTime() - start));
        }
    }

    /**
     * Checks registered {@link ModelArtifactVerifier} hooks before persisting an
     * online-updated model to disk.
     *
     * @param model the inference model.
     * @throws ForbiddenException if online persistence is disallowed by policy.
     */
    public void checkBeforePersist(InferenceModel model) throws ForbiddenException {
        if (model.path() == null) {
            return;
        }
        Path normalized = model.path().toAbsolutePath().normalize();
        try {
            for (ModelArtifactVerifier verifier : verifiers) {
                verifier.beforeModelPersist(normalized, model.model());
            }
        } catch (SecurityException ex) {
            throw new ForbiddenException("Online model persistence rejected by policy: " + ex.getMessage(), ex);
        }
    }

    /**
     * Atomically persists the in-memory model to disk and invokes post-persist
     * {@link ModelArtifactVerifier} hooks (such as local node re-signing).
     *
     * @param model the inference model.
     * @throws ForbiddenException if post-persist verification/signing fails.
     */
    public void persistModel(InferenceModel model) throws ForbiddenException {
        checkBeforePersist(model);
        model.persist(() -> {
            if (model.path() != null) {
                Path normalized = model.path().toAbsolutePath().normalize();
                try {
                    for (ModelArtifactVerifier verifier : verifiers) {
                        verifier.onModelPersisted(normalized, model.model());
                    }
                } catch (SecurityException ex) {
                    throw new ForbiddenException("Post-persist model signing failed: " + ex.getMessage(), ex);
                }
            }
        });
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
            Path normalized = path.toAbsolutePath().normalize();
            Model m = verifyAndReadModel(normalized);
            var reloaded = new InferenceModel(m, normalized);
            boolean idChanged = !reloaded.id().equals(id);
            if (idChanged) {
                models.remove(id);
                modelPaths.remove(id);
            }
            models.put(reloaded.id(), reloaded);
            modelPaths.put(reloaded.id(), normalized);
            quarantined.remove(normalized);
            metricsBinders.forEach(b -> {
                if (idChanged) {
                    b.unregister(ModelMetricsBinder.SMILE_NAMESPACE, id);
                }
                b.unregister(ModelMetricsBinder.SMILE_NAMESPACE, reloaded.id());
                b.register(ModelMetricsBinder.SMILE_NAMESPACE, reloaded.id(), reloaded.metrics());
            });
            notifyListeners(InferenceEvent.lifecycle(
                    ModelMetricsBinder.SMILE_NAMESPACE,
                    reloaded.id(),
                    InferenceEvent.Action.MODEL_RELOADED,
                    normalized.toString()));
            logger.infof("Model '%s' reloaded successfully", reloaded.id());
            return reloaded;
        } catch (SecurityException ex) {
            logger.errorf(ex, "Rejected reload of model '%s' from '%s'", id, path);
            throw new ForbiddenException("Model artifact verification failed: " + ex.getMessage(), ex);
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
        metricsBinders.forEach(b -> b.unregister(ModelMetricsBinder.SMILE_NAMESPACE, id));
        notifyListeners(InferenceEvent.lifecycle(
                ModelMetricsBinder.SMILE_NAMESPACE,
                id,
                InferenceEvent.Action.MODEL_UNLOADED,
                "{\"drained\":" + drained + "}"));
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
        Set<String> batchIds = new HashSet<>();
        for (Path file : smlFiles) {
            Path normalized = file.toAbsolutePath().normalize();
            // Pre-deserialization duplicate ID check when SMLM extended header metadata is present
            try {
                smile.model.Format.Header header = smile.model.Format.inspect(normalized);
                if (!header.legacy() && header.payloadSize() >= 0) {
                    String headerId = header.modelId();
                    String baseId = (headerId != null && !headerId.isBlank())
                            ? headerId
                            : smile.io.Paths.getFileName(normalized);
                    String version = header.modelVersion() != null && !header.modelVersion().isBlank()
                            ? header.modelVersion()
                            : "1";
                    String candidateId = baseId + "-" + version;
                    if (models.containsKey(candidateId)) {
                        throw new ClientErrorException(
                                "Model '" + candidateId + "' is already loaded. Use POST /api/v1/smile/" + candidateId + "/reload to refresh it, or unload it first.",
                                Response.Status.CONFLICT);
                    }
                }
            } catch (ClientErrorException e) {
                throw e;
            } catch (IOException ignored) {
                // Header/CRC/framing errors will be surfaced by verifyAndReadModel below
            }

            Model m;
            try {
                m = verifyAndReadModel(normalized);
            } catch (SecurityException e) {
                throw new ForbiddenException("Model artifact verification failed for " + file + ": " + e.getMessage(), e);
            } catch (Exception e) {
                throw new BadRequestException("Failed to read model from " + file + ": " + e.getMessage(), e);
            }
            var model = new InferenceModel(m, normalized);
            if (models.containsKey(model.id()) || !batchIds.add(model.id())) {
                throw new ClientErrorException(
                        "Model '" + model.id() + "' is already loaded. Use POST /api/v1/smile/" + model.id() + "/reload to refresh it, or unload it first.",
                        Response.Status.CONFLICT);
            }
            loaded.add(model);
        }

        for (InferenceModel model : loaded) {
            models.put(model.id(), model);
            modelPaths.put(model.id(), model.path());
            quarantined.remove(model.path());
            metricsBinders.forEach(b -> b.register(ModelMetricsBinder.SMILE_NAMESPACE, model.id(), model.metrics()));
            notifyListeners(InferenceEvent.lifecycle(
                    ModelMetricsBinder.SMILE_NAMESPACE,
                    model.id(),
                    InferenceEvent.Action.MODEL_LOADED,
                    model.path().toString()));
            logger.infof("Dynamically loaded model '%s' from '%s'", model.id(), model.path());
        }
        return loaded;
    }
}
