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

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import io.quarkus.arc.All;
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
import smile.io.Paths;
import smile.onnx.InferenceSession;
import smile.serve.model.ModelDiscovery;
import smile.serve.model.ModelLoadOptions;
import smile.serve.model.ModelManifest;
import smile.serve.model.ModelObject;
import smile.serve.model.ModelRef;
import smile.serve.model.ModelRegistry;
import smile.serve.model.ModelSource;
import smile.serve.model.OnnxModelDetails;
import smile.serve.model.OpenAiModelContributor;
import smile.serve.model.QuarantinedModel;

/**
 * Application-scoped service that loads {@code .onnx} models at startup and
 * executes inference requests via ONNX Runtime.
 *
 * <p>Models are registered by structured {@link ModelRef} {@code (slug, version)}
 * in {@link ModelRegistry}, supporting flat files, versioned directory trees
 * ({@code <dir>/<slug>/<version>/<file>.onnx}), declarative {@code models.json}
 * manifests, and mutable routing aliases ({@code default}, {@code latest}, and
 * custom aliases).
 *
 * @author Haifeng Li
 */
@ApplicationScoped
public class OnnxService implements OpenAiModelContributor {
    private static final Logger logger = Logger.getLogger(OnnxService.class);

    private final ModelRegistry<OnnxModel> registry = new ModelRegistry<>();
    private final Map<Path, QuarantinedModel> quarantined = Collections.synchronizedMap(new TreeMap<>());
    private final List<ModelMetricsBinder> metricsBinders;
    private final List<InferenceListener> listeners;
    private final List<ModelArtifactVerifier> verifiers;

    /**
     * Loads ONNX models upon application start without external telemetry binders,
     * inference listeners, or artifact verifiers.
     */
    public OnnxService(OnnxServiceConfig config) {
        this(config, List.of(), List.of(), List.of());
    }

    /**
     * Loads ONNX models upon application start without inference listeners or artifact verifiers.
     */
    public OnnxService(OnnxServiceConfig config, List<ModelMetricsBinder> metricsBinders) {
        this(config, metricsBinders, List.of(), List.of());
    }

    /**
     * Loads ONNX models upon application start without artifact verifiers.
     */
    public OnnxService(OnnxServiceConfig config,
                       List<ModelMetricsBinder> metricsBinders,
                       List<InferenceListener> listeners) {
        this(config, metricsBinders, listeners, List.of());
    }

    /**
     * Loads ONNX models upon application start.
     *
     * @param config         the ONNX service configuration.
     * @param metricsBinders telemetry meter binders discovered on the classpath.
     * @param listeners      inference and lifecycle listeners discovered on the classpath.
     * @param verifiers      synchronous pre-deserialization model artifact verifiers.
     */
    @Inject
    public OnnxService(OnnxServiceConfig config,
                       @All List<ModelMetricsBinder> metricsBinders,
                       @All List<InferenceListener> listeners,
                       @All List<ModelArtifactVerifier> verifiers) {
        this.metricsBinders = metricsBinders == null ? List.of() : List.copyOf(metricsBinders);
        this.listeners = listeners == null ? List.of() : List.copyOf(listeners);
        this.verifiers = verifiers == null ? List.of() : List.copyOf(verifiers);

        String servedId = config.servedModelId()
                .map(String::strip)
                .filter(s -> !s.isEmpty())
                .orElse(null);
        Path manifestPath = config.manifest()
                .map(String::strip)
                .filter(s -> !s.isEmpty())
                .map(Path::of)
                .orElse(null);
        var path = Path.of(config.model()).toAbsolutePath().normalize();

        if (!Files.exists(path) && manifestPath == null) {
            logger.infof("ONNX model path '%s' not found — ONNX service starting empty.", path);
            return;
        }
        if (Files.isRegularFile(path) && !path.toString().endsWith(".onnx") && manifestPath == null) {
            logger.infof("ONNX model path '%s' is not a .onnx file — ONNX service starting empty.", path);
            return;
        }

        ModelDiscovery.Plan plan = ModelDiscovery.discover(
                path, ModelManifest.Kind.ONNX, manifestPath, servedId, true);
        for (ModelDiscovery.DiscoveryError err : plan.errors()) {
            logger.errorf(err.error(), "ONNX model discovery failed for '%s'", err.path());
            recordQuarantine(err.path(), err.error());
        }

        for (ModelDiscovery.Candidate candidate : plan.candidates()) {
            loadStartupModel(candidate.source());
        }

        applyStartupRouting(plan);
    }

    private void loadStartupModel(ModelSource source) {
        Path normalized = source.path();
        InferenceSession session = null;
        try {
            logger.infof("Loading ONNX model from '%s'", normalized);
            session = verifyAndCreateSession(source);
            ModelRef ref = OnnxModel.resolveRef(session.metadata(), source);
            var inputs = session.inputNames();
            var outputs = session.outputNames();
            var model = new OnnxModel(ref, normalized, session);
            session = null; // owned by model
            try {
                registry.register(new ModelRegistry.Entry<>(ref, model, source));
            } catch (ModelRegistry.ConflictException ex) {
                model.close();
                throw ex;
            }
            quarantined.remove(normalized);
            registerMetrics(model);
            notifyListeners(InferenceEvent.lifecycle(
                    ModelMetricsBinder.ONNX_NAMESPACE,
                    model.slug(),
                    model.version(),
                    InferenceEvent.Action.MODEL_LOADED,
                    normalized.toString()));
            logger.infof("ONNX model '%s' (version '%s') loaded successfully (inputs=%s, outputs=%s)",
                    model.slug(), model.version(), inputs, outputs);
        } catch (Throwable ex) {
            if (session != null) {
                try {
                    session.close();
                } catch (Exception ignored) {
                }
            }
            // ExceptionInInitializerError / NoClassDefFoundError are Errors, not
            // Exceptions — typically missing libonnxruntime on java.library.path.
            logger.errorf(ex,
                    "Failed to load ONNX model from '%s'. "
                            + "Ensure the onnxruntime native library (%s) is on "
                            + "java.library.path (or the OS library search path) and "
                            + "the JVM is started with --enable-native-access=ALL-UNNAMED. "
                            + "ONNX endpoints will return 404 until a model loads.",
                    normalized, System.mapLibraryName("onnxruntime"));
            // Do not quarantine when the native onnxruntime library is absent on a JVM without it
            if (!(ex instanceof LinkageError)) {
                recordQuarantine(normalized, ex);
            }
        }
    }

    private void applyStartupRouting(ModelDiscovery.Plan plan) {
        for (var e : plan.defaultVersions().entrySet()) {
            try {
                if (registry.contains(new ModelRef(e.getKey(), e.getValue()))) {
                    registry.setAlias(e.getKey(), ModelRef.ALIAS_DEFAULT, e.getValue());
                } else {
                    logger.warnf("Skipping manifest default version '%s' for ONNX model '%s': version did not load",
                            e.getValue(), e.getKey());
                }
            } catch (IllegalArgumentException ex) {
                logger.warnf(ex, "Failed to apply default version '%s' for ONNX model '%s'", e.getValue(), e.getKey());
            }
        }
        for (var slugEntry : plan.aliases().entrySet()) {
            String slug = slugEntry.getKey();
            for (var aliasEntry : slugEntry.getValue().entrySet()) {
                try {
                    if (registry.contains(new ModelRef(slug, aliasEntry.getValue()))) {
                        registry.setAlias(slug, aliasEntry.getKey(), aliasEntry.getValue());
                    } else {
                        logger.warnf("Skipping manifest alias '%s' -> '%s' for ONNX model '%s': target version did not load",
                                aliasEntry.getKey(), aliasEntry.getValue(), slug);
                    }
                } catch (IllegalArgumentException ex) {
                    logger.warnf(ex, "Failed to apply alias '%s' for ONNX model '%s'", aliasEntry.getKey(), slug);
                }
            }
        }
    }

    private InferenceSession verifyAndCreateSession(ModelSource source) {
        Path path = source.path();
        long t0 = System.nanoTime();
        String fallbackId = source.slugOverride() != null ? source.slugOverride() : Paths.getFileName(path);
        try {
            for (ModelArtifactVerifier verifier : verifiers) {
                verifier.verify(ModelMetricsBinder.ONNX_NAMESPACE, path);
            }
            InferenceSession session = InferenceSession.create(path.toString());
            if (!verifiers.isEmpty() && !listeners.isEmpty()) {
                ModelRef ref = OnnxModel.resolveRef(session.metadata(), source);
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
                        ModelMetricsBinder.ONNX_NAMESPACE,
                        ref.slug(),
                        ref.version(),
                        allVerified,
                        details,
                        System.nanoTime() - t0));
            }
            return session;
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
                        ModelMetricsBinder.ONNX_NAMESPACE,
                        fallbackId,
                        source.versionOverride(),
                        false,
                        details,
                        System.nanoTime() - t0));
            }
            throw ex;
        }
    }

    private void recordQuarantine(Path path, Throwable ex) {
        quarantined.put(path, new QuarantinedModel(
                path.toString(),
                ModelMetricsBinder.ONNX_NAMESPACE,
                ex.getClass().getName(),
                ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName(),
                true,
                Instant.now().getEpochSecond()));
    }

    private void registerMetrics(OnnxModel model) {
        metricsBinders.forEach(b -> b.register(
                ModelMetricsBinder.ONNX_NAMESPACE, model.slug(), model.version(), model.metrics()));
    }

    private void unregisterMetrics(ModelRef ref) {
        metricsBinders.forEach(b -> b.unregister(
                ModelMetricsBinder.ONNX_NAMESPACE, ref.slug(), ref.version()));
    }

    @Override
    public List<QuarantinedModel> listQuarantinedModels() {
        synchronized (quarantined) {
            return List.copyOf(quarantined.values());
        }
    }

    /**
     * Returns one OpenAI-shaped descriptor per loaded ONNX model slug (representing
     * the slug's active default version, with {@code version} and {@code versions}).
     */
    @Override
    public List<ModelObject> listOpenAiModels() {
        List<ModelObject> result = new ArrayList<>();
        for (var entry : registry.defaultEntries()) {
            OnnxModel model = entry.model();
            var info = registry.versionsInfo(model.slug()).orElse(null);
            result.add(ModelObject.of(
                    model.slug(),
                    ModelObject.createdFromPath(model.path()),
                    ModelObject.ownedByFromMap(model.info().customMeta()),
                    ModelObject.KIND_ONNX,
                    model.version(),
                    info != null ? info.versions() : List.of(model.version())));
        }
        return result;
    }

    /**
     * Looks up a loaded ONNX model as an OpenAI {@link ModelObject}.
     *
     * <p>Accepts a bare {@code {slug}}, {@code {slug}/versions/{version}}, or
     * {@code {slug}/aliases/{alias}}.
     */
    @Override
    public Optional<ModelObject> findOpenAiModel(String id, boolean detailed) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        Optional<ModelRegistry.Entry<OnnxModel>> entry = resolveOpenAiSelector(id);
        if (entry.isEmpty()) {
            return Optional.empty();
        }
        OnnxModel model = entry.get().model();
        var info = registry.versionsInfo(model.slug()).orElse(null);
        List<String> versions = info != null ? info.versions() : List.of(model.version());
        String defaultVer = info != null ? info.defaultVersion() : model.version();
        Map<String, String> aliases = info != null ? info.aliases() : Map.of();
        OnnxModelDetails onnx = detailed
                ? model.details(defaultVer, versions, aliases)
                : null;
        return Optional.of(ModelObject.of(
                model.slug(),
                ModelObject.createdFromPath(model.path()),
                ModelObject.ownedByFromMap(model.info().customMeta()),
                ModelObject.KIND_ONNX,
                model.version(),
                versions,
                null,
                onnx,
                null));
    }

    private Optional<ModelRegistry.Entry<OnnxModel>> resolveOpenAiSelector(String id) {
        int versionsIdx = id.indexOf("/versions/");
        if (versionsIdx > 0) {
            String slug = id.substring(0, versionsIdx);
            String version = id.substring(versionsIdx + "/versions/".length());
            return registry.resolveVersion(slug, version);
        }
        int aliasesIdx = id.indexOf("/aliases/");
        if (aliasesIdx > 0) {
            String slug = id.substring(0, aliasesIdx);
            String alias = id.substring(aliasesIdx + "/aliases/".length());
            return registry.resolveAlias(slug, alias);
        }
        return registry.resolve(id);
    }

    /**
     * Looks up a loaded ONNX model as a lean OpenAI {@link ModelObject}.
     */
    public Optional<ModelObject> findOpenAiModel(String id) {
        return findOpenAiModel(id, false);
    }

    /**
     * Returns whether any version of {@code slug} is currently loaded.
     */
    public boolean hasSlug(String slug) {
        return registry.containsSlug(slug);
    }

    /**
     * Returns the active default version of the ONNX model for {@code slug}.
     */
    public OnnxModel getModel(String slug) throws NotFoundException {
        return registry.resolve(slug)
                .map(ModelRegistry.Entry::model)
                .orElseThrow(() -> new NotFoundException("ONNX model not found: " + slug));
    }

    /**
     * Returns a specific version of the ONNX model for {@code slug}.
     */
    public OnnxModel getModelVersion(String slug, String version) throws NotFoundException {
        return registry.resolveVersion(slug, version)
                .map(ModelRegistry.Entry::model)
                .orElseThrow(() -> new NotFoundException(
                        "ONNX model '" + slug + "' version '" + version + "' not found"));
    }

    /**
     * Returns the version of {@code slug} resolved by {@code alias}.
     */
    public OnnxModel getModelAlias(String slug, String alias) throws NotFoundException {
        return registry.resolveAlias(slug, alias)
                .map(ModelRegistry.Entry::model)
                .orElseThrow(() -> new NotFoundException(
                        "ONNX model '" + slug + "' alias '" + alias + "' not found"));
    }

    /**
     * Returns info for {@code model} enriched with registry version and alias summary.
     */
    public OnnxModelInfo infoFor(OnnxModel model) {
        var info = registry.versionsInfo(model.slug()).orElse(null);
        if (info == null) {
            return model.info();
        }
        return model.info(info.versions(), info.aliases());
    }

    /**
     * Returns version and alias summary for {@code slug}.
     */
    public ModelRegistry.SlugVersions getVersions(String slug) throws NotFoundException {
        return registry.versionsInfo(slug)
                .orElseThrow(() -> new NotFoundException("ONNX model not found: " + slug));
    }

    /**
     * Assigns or updates an alias (including {@code "default"}) for {@code slug}.
     */
    public ModelRegistry.SlugVersions setAlias(String slug, String alias, String version)
            throws NotFoundException, BadRequestException {
        String validSlug;
        String validAlias;
        String validVersion;
        try {
            validSlug = ModelRef.validateSlug(slug);
            validAlias = ModelRef.validateAlias(alias);
            validVersion = ModelRef.validateVersion(version);
            if (ModelRef.ALIAS_LATEST.equals(validAlias)) {
                throw new BadRequestException("Alias 'latest' is reserved and always resolves to the highest version");
            }
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException(ex.getMessage(), ex);
        }
        if (!registry.containsSlug(validSlug)) {
            throw new NotFoundException("ONNX model not found: " + validSlug);
        }
        if (!registry.contains(new ModelRef(validSlug, validVersion))) {
            throw new NotFoundException("ONNX model '" + validSlug + "' version '" + validVersion + "' not found");
        }
        return registry.setAlias(validSlug, validAlias, validVersion);
    }

    /**
     * Deletes a custom alias, or unpins {@code "default"} so it reverts to tracking {@code latest}.
     */
    public ModelRegistry.SlugVersions deleteAlias(String slug, String alias)
            throws NotFoundException, BadRequestException {
        String validSlug;
        String validAlias;
        try {
            validSlug = ModelRef.validateSlug(slug);
            validAlias = ModelRef.validateAlias(alias);
            if (ModelRef.ALIAS_LATEST.equals(validAlias)) {
                throw new BadRequestException("Alias 'latest' is reserved and cannot be deleted");
            }
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException(ex.getMessage(), ex);
        }
        if (!registry.containsSlug(validSlug)) {
            throw new NotFoundException("ONNX model not found: " + validSlug);
        }
        boolean removed = registry.deleteAlias(validSlug, validAlias);
        if (!removed && !ModelRef.ALIAS_DEFAULT.equals(validAlias)) {
            throw new NotFoundException("Alias '" + validAlias + "' not found on ONNX model '" + validSlug + "'");
        }
        return getVersions(validSlug);
    }

    /**
     * Returns a snapshot of the default loaded ONNX model per slug, in slug order.
     */
    public Map<String, OnnxModel> models() {
        Map<String, OnnxModel> map = new LinkedHashMap<>();
        for (var entry : registry.defaultEntries()) {
            map.put(entry.ref().slug(), entry.model());
        }
        return Collections.unmodifiableMap(map);
    }

    /**
     * Returns every active loaded ONNX model version across all slugs.
     */
    public List<OnnxModel> allModels() {
        return registry.allEntries().stream().map(ModelRegistry.Entry::model).toList();
    }

    /**
     * Runs ONNX inference with JSON-encoded inputs on the default version of {@code slug}.
     */
    public JsonObject predict(String slug, JsonObject request)
            throws BadRequestException, NotFoundException {
        return predict(getModel(slug), request);
    }

    /**
     * Runs ONNX inference against a resolved {@link OnnxModel} and emits audit events.
     */
    public JsonObject predict(OnnxModel model, JsonObject request) throws BadRequestException {
        long start = System.nanoTime();
        JsonObject result = model.predict(request);
        if (!listeners.isEmpty()) {
            notifyListeners(InferenceEvent.onnxPredict(
                    model.slug(),
                    model.version(),
                    InferenceEvent.Action.PREDICT,
                    request != null ? request.encode() : null,
                    result != null ? result.encode() : null,
                    System.nanoTime() - start));
        }
        return result;
    }

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

    boolean hasListeners() {
        return !listeners.isEmpty();
    }

    /**
     * Reloads the default (or last known) version of {@code slug} from disk.
     */
    public OnnxModel reloadModel(String slug)
            throws NotFoundException, ClientErrorException, InternalServerErrorException {
        ModelRef target = registry.reloadTargetForSlug(slug)
                .orElseThrow(() -> new NotFoundException("ONNX model not found: " + slug));
        return reloadModelVersion(target.slug(), target.version());
    }

    /**
     * Reloads a specific ONNX {@code (slug, version)} from its backing file on disk.
     * Preserves any explicit slug/version overrides configured at load time.
     */
    public OnnxModel reloadModelVersion(String slug, String version)
            throws NotFoundException, ClientErrorException, InternalServerErrorException {
        ModelRef oldRef;
        try {
            oldRef = new ModelRef(slug, version);
        } catch (IllegalArgumentException ex) {
            throw new NotFoundException("ONNX model '" + slug + "' version '" + version + "' not found");
        }
        ModelSource source = registry.knownSource(oldRef).orElse(null);
        if (source == null || !Files.isRegularFile(source.path())) {
            throw new NotFoundException("ONNX model file not found on disk for '" + oldRef + "'");
        }

        InferenceSession session = null;
        try {
            logger.infof("Reloading ONNX model '%s' from '%s'", oldRef, source.path());
            session = verifyAndCreateSession(source);
            ModelRef newRef = OnnxModel.resolveRef(session.metadata(), source);
            var reloaded = new OnnxModel(newRef, source.path(), session);
            session = null; // owned by reloaded

            ModelRegistry.Entry<OnnxModel> oldEntry;
            try {
                oldEntry = registry.replace(oldRef, new ModelRegistry.Entry<>(newRef, reloaded, source));
            } catch (ModelRegistry.ConflictException ex) {
                reloaded.close();
                throw new ClientErrorException(ex.getMessage(), Response.Status.CONFLICT);
            }

            quarantined.remove(source.path());
            if (!oldRef.equals(newRef)) {
                unregisterMetrics(oldRef);
            }
            unregisterMetrics(newRef);
            registerMetrics(reloaded);
            notifyListeners(InferenceEvent.lifecycle(
                    ModelMetricsBinder.ONNX_NAMESPACE,
                    reloaded.slug(),
                    reloaded.version(),
                    InferenceEvent.Action.MODEL_RELOADED,
                    source.path().toString()));
            if (oldEntry != null) {
                oldEntry.model().close();
            }
            logger.infof("ONNX model '%s' (version '%s') reloaded successfully",
                    reloaded.slug(), reloaded.version());
            return reloaded;
        } catch (ClientErrorException ex) {
            throw ex;
        } catch (SecurityException ex) {
            logger.errorf(ex, "Rejected reload of ONNX model '%s' from '%s'", oldRef, source.path());
            throw new ForbiddenException("ONNX model artifact verification failed: " + ex.getMessage(), ex);
        } catch (Exception ex) {
            if (session != null) {
                try {
                    session.close();
                } catch (Exception ignored) {
                }
            }
            logger.errorf(ex, "Failed to reload ONNX model '%s' from '%s'", oldRef, source.path());
            throw new InternalServerErrorException("Failed to reload ONNX model: " + ex.getMessage(), ex);
        }
    }

    /**
     * Gracefully unloads all active versions of {@code slug}.
     */
    public boolean unloadModel(String slug, Duration graceTimeout) throws NotFoundException {
        List<ModelRegistry.Entry<OnnxModel>> removed = registry.removeSlug(slug);
        if (removed.isEmpty()) {
            throw new NotFoundException("ONNX model not found: " + slug);
        }
        boolean allDrained = true;
        for (var entry : removed) {
            OnnxModel existing = entry.model();
            boolean drained = existing.unload(graceTimeout);
            allDrained &= drained;
            unregisterMetrics(entry.ref());
            notifyListeners(InferenceEvent.lifecycle(
                    ModelMetricsBinder.ONNX_NAMESPACE,
                    existing.slug(),
                    existing.version(),
                    InferenceEvent.Action.MODEL_UNLOADED,
                    "{\"drained\":" + drained + "}"));
            logger.infof("ONNX model '%s' (version '%s') unloaded (drained in-flight: %s)",
                    existing.slug(), existing.version(), drained);
        }
        return allDrained;
    }

    /**
     * Gracefully unloads a single ONNX {@code (slug, version)}.
     */
    public boolean unloadModelVersion(String slug, String version, Duration graceTimeout) throws NotFoundException {
        ModelRef ref;
        try {
            ref = new ModelRef(slug, version);
        } catch (IllegalArgumentException ex) {
            throw new NotFoundException("ONNX model '" + slug + "' version '" + version + "' not found");
        }
        var removed = registry.removeVersion(ref)
                .orElseThrow(() -> new NotFoundException(
                        "ONNX model '" + slug + "' version '" + version + "' not found"));
        OnnxModel existing = removed.model();
        boolean drained = existing.unload(graceTimeout);
        unregisterMetrics(ref);
        notifyListeners(InferenceEvent.lifecycle(
                ModelMetricsBinder.ONNX_NAMESPACE,
                existing.slug(),
                existing.version(),
                InferenceEvent.Action.MODEL_UNLOADED,
                "{\"drained\":" + drained + "}"));
        logger.infof("ONNX model '%s' (version '%s') unloaded (drained in-flight: %s)",
                existing.slug(), existing.version(), drained);
        return drained;
    }

    /**
     * Dynamically loads an ONNX model or directory of models from disk into active memory.
     */
    public List<OnnxModel> load(Path path)
            throws NotFoundException, ClientErrorException, BadRequestException {
        return load(path, ModelLoadOptions.EMPTY);
    }

    /**
     * Dynamically loads an ONNX model or directory of models from disk with optional
     * slug, version, alias, and default-routing overrides.
     */
    public List<OnnxModel> load(Path path, ModelLoadOptions options)
            throws NotFoundException, ClientErrorException, BadRequestException {
        ModelLoadOptions opts = options != null ? options : ModelLoadOptions.EMPTY;
        Path absPath = path.toAbsolutePath().normalize();
        if (!Files.exists(absPath)) {
            throw new NotFoundException("Path does not exist: " + path);
        }

        List<ModelSource> sources = new ArrayList<>();
        Map<String, String> defaults = new LinkedHashMap<>();
        Map<String, Map<String, String>> aliases = new LinkedHashMap<>();
        Set<String> pinExistingDefaultSlugs = new HashSet<>();

        if (Files.isRegularFile(absPath)) {
            if (!absPath.toString().endsWith(".onnx")) {
                throw new BadRequestException("Not a .onnx file: " + path);
            }
            sources.add(new ModelSource(absPath, opts.slug(), opts.version()));
        } else if (Files.isDirectory(absPath)) {
            ModelDiscovery.Plan plan = ModelDiscovery.discover(
                    absPath, ModelManifest.Kind.ONNX, null, null, true);
            if (!plan.errors().isEmpty()) {
                var first = plan.errors().getFirst();
                throw new BadRequestException(
                        "Failed to scan '" + first.path() + "': " + first.error().getMessage(),
                        first.error());
            }
            if (plan.candidates().isEmpty()) {
                throw new BadRequestException("No .onnx models found at: " + path);
            }
            if (opts.hasSingleArtifactOverrides() && plan.candidates().size() > 1) {
                throw new BadRequestException(
                        "Options 'slug', 'version', 'aliases', and 'set_default' are only supported when loading a single model");
            }
            if (plan.candidates().size() == 1 && opts.hasSingleArtifactOverrides()) {
                ModelSource base = plan.candidates().getFirst().source();
                sources.add(new ModelSource(
                        base.path(),
                        opts.slug() != null ? opts.slug() : base.slugOverride(),
                        opts.version() != null ? opts.version() : base.versionOverride()));
            } else {
                for (var c : plan.candidates()) {
                    sources.add(c.source());
                }
            }
            defaults.putAll(plan.defaultVersions());
            aliases.putAll(plan.aliases());
        } else {
            throw new BadRequestException("Path is not a regular file or directory: " + path);
        }

        List<ModelRegistry.Entry<OnnxModel>> staged = new ArrayList<>();
        Set<ModelRef> batchRefs = new HashSet<>();

        for (ModelSource source : sources) {
            Path normalized = source.path();
            // Fast pre-check when both slug & version are already fixed by manifest/dir/options
            if (source.slugOverride() != null && source.versionOverride() != null) {
                ModelRef preRef = new ModelRef(source.slugOverride(), source.versionOverride());
                if (registry.contains(preRef) || batchRefs.contains(preRef)) {
                    closeAll(staged);
                    throw conflictException(preRef);
                }
            }

            InferenceSession session = null;
            try {
                session = verifyAndCreateSession(source);
                ModelRef ref = OnnxModel.resolveRef(session.metadata(), source);
                if (registry.contains(ref) || !batchRefs.add(ref)) {
                    throw conflictException(ref);
                }
                var model = new OnnxModel(ref, normalized, session);
                session = null;
                staged.add(new ModelRegistry.Entry<>(ref, model, source));
            } catch (ClientErrorException ex) {
                if (session != null) {
                    try {
                        session.close();
                    } catch (Exception ignored) {
                    }
                }
                closeAll(staged);
                throw ex;
            } catch (SecurityException ex) {
                if (session != null) {
                    try {
                        session.close();
                    } catch (Exception ignored) {
                    }
                }
                closeAll(staged);
                throw new ForbiddenException("ONNX model artifact verification failed for " + normalized + ": " + ex.getMessage(), ex);
            } catch (Throwable ex) {
                if (session != null) {
                    try {
                        session.close();
                    } catch (Exception ignored) {
                    }
                }
                closeAll(staged);
                throw new BadRequestException("Failed to load ONNX model from " + normalized + ": " + ex.getMessage(), ex);
            }
        }

        if (staged.size() == 1 && opts.hasSingleArtifactOverrides()) {
            ModelRef singleRef = staged.getFirst().ref();
            if (Boolean.TRUE.equals(opts.setDefault())) {
                defaults.put(singleRef.slug(), singleRef.version());
            } else if (Boolean.FALSE.equals(opts.setDefault())) {
                pinExistingDefaultSlugs.add(singleRef.slug());
            }
            if (!opts.aliases().isEmpty()) {
                Map<String, String> slugAliases = new HashMap<>(aliases.getOrDefault(singleRef.slug(), Map.of()));
                for (String a : opts.aliases()) {
                    if (ModelRef.ALIAS_LATEST.equals(a)) {
                        closeAll(staged);
                        throw new BadRequestException("Alias 'latest' is reserved and cannot be assigned");
                    }
                    if (ModelRef.ALIAS_DEFAULT.equals(a)) {
                        defaults.put(singleRef.slug(), singleRef.version());
                    } else {
                        slugAliases.put(a, singleRef.version());
                    }
                }
                if (!slugAliases.isEmpty()) {
                    aliases.put(singleRef.slug(), slugAliases);
                }
            }
        }

        try {
            registry.registerBatch(staged, defaults, aliases, pinExistingDefaultSlugs);
        } catch (ModelRegistry.ConflictException ex) {
            closeAll(staged);
            throw conflictException(ex.ref());
        } catch (IllegalArgumentException ex) {
            closeAll(staged);
            throw new BadRequestException(ex.getMessage(), ex);
        }

        List<OnnxModel> loaded = new ArrayList<>(staged.size());
        for (var entry : staged) {
            OnnxModel model = entry.model();
            loaded.add(model);
            quarantined.remove(model.path());
            registerMetrics(model);
            notifyListeners(InferenceEvent.lifecycle(
                    ModelMetricsBinder.ONNX_NAMESPACE,
                    model.slug(),
                    model.version(),
                    InferenceEvent.Action.MODEL_LOADED,
                    model.path().toString()));
            logger.infof("Dynamically loaded ONNX model '%s' (version '%s') from '%s'",
                    model.slug(), model.version(), model.path());
        }
        return loaded;
    }

    private static void closeAll(List<ModelRegistry.Entry<OnnxModel>> staged) {
        for (var entry : staged) {
            try {
                entry.model().close();
            } catch (Exception ignored) {
            }
        }
    }

    private static ClientErrorException conflictException(ModelRef ref) {
        return new ClientErrorException(
                "ONNX model '" + ref.slug() + "' version '" + ref.version()
                        + "' is already loaded. Use POST /v1/onnx/" + ref.slug()
                        + "/versions/" + ref.version() + "/reload to refresh it, or unload it first.",
                Response.Status.CONFLICT);
    }
}
