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

import java.io.InvalidClassException;
import java.io.IOException;
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
import smile.feature.importance.KernelSHAP;
import smile.model.Model;
import smile.model.Prediction;
import smile.serve.model.ModelDiscovery;
import smile.serve.model.ModelLoadOptions;
import smile.serve.model.ModelManifest;
import smile.serve.model.ModelObject;
import smile.serve.model.ModelRef;
import smile.serve.model.ModelRegistry;
import smile.serve.model.ModelSource;
import smile.serve.model.OpenAiModelContributor;
import smile.serve.model.QuarantinedModel;
import smile.serve.model.SmileModelDetails;

/**
 * Inference service that loads serialized SMILE models and executes predictions.
 *
 * <p>Models are registered by structured {@link ModelRef} {@code (slug, version)}
 * in {@link ModelRegistry}, supporting flat files, versioned directory trees
 * ({@code <dir>/<slug>/<version>/<file>.sml}), declarative {@code models.json}
 * manifests, and mutable routing aliases ({@code default}, {@code latest}, and
 * custom aliases).
 *
 * @author Haifeng Li
 */
@ApplicationScoped
public class InferenceService implements OpenAiModelContributor {
    private static final Logger logger = Logger.getLogger(InferenceService.class);

    private final ModelRegistry<InferenceModel> registry = new ModelRegistry<>();
    private final Map<Path, QuarantinedModel> quarantined = Collections.synchronizedMap(new TreeMap<>());
    private final List<ModelMetricsBinder> metricsBinders;
    private final List<InferenceListener> listeners;
    private final List<ModelArtifactVerifier> verifiers;

    /**
     * Loads models upon application start without external telemetry binders,
     * inference listeners, or artifact verifiers.
     *
     * @param config the inference service configuration.
     */
    public InferenceService(InferenceServiceConfig config) {
        this(config, List.of(), List.of(), List.of());
    }

    /**
     * Loads models upon application start without inference listeners or artifact verifiers.
     *
     * @param config         the inference service configuration.
     * @param metricsBinders telemetry meter binders discovered on the classpath.
     */
    public InferenceService(InferenceServiceConfig config, List<ModelMetricsBinder> metricsBinders) {
        this(config, metricsBinders, List.of(), List.of());
    }

    /**
     * Loads models upon application start without artifact verifiers.
     *
     * @param config         the inference service configuration.
     * @param metricsBinders telemetry meter binders discovered on the classpath.
     * @param listeners      inference and lifecycle listeners discovered on the classpath.
     */
    public InferenceService(InferenceServiceConfig config,
                            List<ModelMetricsBinder> metricsBinders,
                            List<InferenceListener> listeners) {
        this(config, metricsBinders, listeners, List.of());
    }

    /**
     * Loads models upon application start.
     *
     * @param config         the inference service configuration.
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
            logger.infof("SMILE model path '%s' not found — SMILE inference service starting empty.", path);
            return;
        }

        ModelDiscovery.Plan plan = ModelDiscovery.discover(
                path, ModelManifest.Kind.SML, manifestPath, servedId, false);
        for (ModelDiscovery.DiscoveryError err : plan.errors()) {
            logger.errorf(err.error(), "Model discovery failed for '%s'", err.path());
            recordQuarantine(err.path(), err.error());
        }

        for (ModelDiscovery.Candidate candidate : plan.candidates()) {
            loadStartupModel(candidate.source());
        }

        applyStartupRouting(plan);
    }

    private void loadStartupModel(ModelSource source) {
        Path normalized = source.path();
        try {
            logger.infof("Loading model from '%s'", normalized);
            Model m = verifyAndReadModel(source);
            ModelRef ref = InferenceModel.resolveRef(m, source);
            var model = new InferenceModel(m, source, ref);
            registry.register(new ModelRegistry.Entry<>(ref, model, source));
            quarantined.remove(normalized);
            registerMetrics(model);
            notifyListeners(InferenceEvent.lifecycle(
                    ModelMetricsBinder.SMILE_NAMESPACE,
                    model.slug(),
                    model.version(),
                    InferenceEvent.Action.MODEL_LOADED,
                    normalized.toString()));
            logger.infof("Model '%s' (version '%s') loaded successfully", model.slug(), model.version());
        } catch (Throwable ex) {
            logger.errorf(ex, "Failed to load model from '%s'", normalized);
            recordQuarantine(normalized, ex);
        }
    }

    private void applyStartupRouting(ModelDiscovery.Plan plan) {
        for (var e : plan.defaultVersions().entrySet()) {
            try {
                if (registry.contains(new ModelRef(e.getKey(), e.getValue()))) {
                    registry.setAlias(e.getKey(), ModelRef.ALIAS_DEFAULT, e.getValue());
                } else {
                    logger.warnf("Skipping manifest default version '%s' for '%s': version did not load",
                            e.getValue(), e.getKey());
                }
            } catch (IllegalArgumentException ex) {
                logger.warnf(ex, "Failed to apply default version '%s' for '%s'", e.getValue(), e.getKey());
            }
        }
        for (var slugEntry : plan.aliases().entrySet()) {
            String slug = slugEntry.getKey();
            for (var aliasEntry : slugEntry.getValue().entrySet()) {
                try {
                    if (registry.contains(new ModelRef(slug, aliasEntry.getValue()))) {
                        registry.setAlias(slug, aliasEntry.getKey(), aliasEntry.getValue());
                    } else {
                        logger.warnf("Skipping manifest alias '%s' -> '%s' for '%s': target version did not load",
                                aliasEntry.getKey(), aliasEntry.getValue(), slug);
                    }
                } catch (IllegalArgumentException ex) {
                    logger.warnf(ex, "Failed to apply alias '%s' for '%s'", aliasEntry.getKey(), slug);
                }
            }
        }
    }

    private Model verifyAndReadModel(ModelSource source) throws Exception {
        Path path = source.path();
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
                ModelRef ref = InferenceModel.resolveRef(m, source);
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
                        ref.slug(),
                        ref.version(),
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
                        source.slugOverride() != null ? source.slugOverride() : fileName,
                        source.versionOverride(),
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

    private void registerMetrics(InferenceModel model) {
        metricsBinders.forEach(b -> b.register(
                ModelMetricsBinder.SMILE_NAMESPACE, model.slug(), model.version(), model.metrics()));
    }

    private void unregisterMetrics(ModelRef ref) {
        metricsBinders.forEach(b -> b.unregister(
                ModelMetricsBinder.SMILE_NAMESPACE, ref.slug(), ref.version()));
    }

    @Override
    public List<QuarantinedModel> listQuarantinedModels() {
        synchronized (quarantined) {
            return List.copyOf(quarantined.values());
        }
    }

    /**
     * Returns one OpenAI-shaped descriptor per loaded SMILE model slug (representing
     * the slug's active default version, with {@code version} and {@code versions}).
     *
     * @return OpenAI model objects in slug order.
     */
    @Override
    public List<ModelObject> listOpenAiModels() {
        List<ModelObject> result = new ArrayList<>();
        for (var entry : registry.defaultEntries()) {
            InferenceModel model = entry.model();
            var info = registry.versionsInfo(model.slug()).orElse(null);
            var meta = model.metadata();
            result.add(ModelObject.of(
                    model.slug(),
                    ModelObject.createdFromPath(model.path()),
                    ModelObject.ownedByFromTags(meta.tags()),
                    meta.algorithm(),
                    model.version(),
                    info != null ? info.versions() : List.of(model.version())));
        }
        return result;
    }

    /**
     * Looks up a loaded SMILE model as an OpenAI {@link ModelObject}.
     *
     * <p>Accepts a bare {@code {slug}}, {@code {slug}/versions/{version}}, or
     * {@code {slug}/aliases/{alias}}.
     *
     * @param id       the model identifier or versioned/aliased selector.
     * @param detailed when {@code true}, include {@link SmileModelDetails}.
     * @return the model object when loaded; otherwise empty.
     */
    @Override
    public Optional<ModelObject> findOpenAiModel(String id, boolean detailed) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        Optional<ModelRegistry.Entry<InferenceModel>> entry = resolveOpenAiSelector(id);
        if (entry.isEmpty()) {
            return Optional.empty();
        }
        InferenceModel model = entry.get().model();
        var info = registry.versionsInfo(model.slug()).orElse(null);
        List<String> versions = info != null ? info.versions() : List.of(model.version());
        String defaultVer = info != null ? info.defaultVersion() : model.version();
        Map<String, String> aliases = info != null ? info.aliases() : Map.of();
        var meta = model.metadata(versions, aliases);
        SmileModelDetails smile = detailed
                ? SmileModelDetails.of(model.model(), meta, defaultVer, versions, aliases)
                : null;
        return Optional.of(ModelObject.of(
                model.slug(),
                ModelObject.createdFromPath(model.path()),
                ModelObject.ownedByFromTags(meta.tags()),
                meta.algorithm(),
                model.version(),
                versions,
                smile,
                null,
                null));
    }

    private Optional<ModelRegistry.Entry<InferenceModel>> resolveOpenAiSelector(String id) {
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
     * Looks up a loaded SMILE model as a lean OpenAI {@link ModelObject}.
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
     * Returns the active default version for {@code slug}.
     *
     * @param slug the model slug.
     * @return the default model instance.
     * @throws NotFoundException if no version of {@code slug} is loaded.
     */
    public InferenceModel getModel(String slug) throws NotFoundException {
        return registry.resolve(slug)
                .map(ModelRegistry.Entry::model)
                .orElseThrow(() -> new NotFoundException("Model not found: " + slug));
    }

    /**
     * Returns a specific version of {@code slug}.
     *
     * @param slug    the model slug.
     * @param version the model version.
     * @return the model instance.
     * @throws NotFoundException if not loaded.
     */
    public InferenceModel getModelVersion(String slug, String version) throws NotFoundException {
        return registry.resolveVersion(slug, version)
                .map(ModelRegistry.Entry::model)
                .orElseThrow(() -> new NotFoundException(
                        "Model '" + slug + "' version '" + version + "' not found"));
    }

    /**
     * Returns the version of {@code slug} resolved by {@code alias}
     * ({@code "default"}, {@code "latest"}, or a custom alias).
     *
     * @param slug  the model slug.
     * @param alias the alias name.
     * @return the resolved model instance.
     * @throws NotFoundException if slug or alias is not found.
     */
    public InferenceModel getModelAlias(String slug, String alias) throws NotFoundException {
        return registry.resolveAlias(slug, alias)
                .map(ModelRegistry.Entry::model)
                .orElseThrow(() -> new NotFoundException(
                        "Model '" + slug + "' alias '" + alias + "' not found"));
    }

    /**
     * Returns metadata for {@code model} enriched with sibling version and alias info.
     */
    public ModelMetadata metadataFor(InferenceModel model) {
        var info = registry.versionsInfo(model.slug()).orElse(null);
        if (info == null) {
            return model.metadata();
        }
        return model.metadata(info.versions(), info.aliases());
    }

    /**
     * Returns version and alias summary for {@code slug}.
     *
     * @param slug the model slug.
     * @return version and alias summary.
     * @throws NotFoundException if {@code slug} is not loaded.
     */
    public ModelRegistry.SlugVersions getVersions(String slug) throws NotFoundException {
        return registry.versionsInfo(slug)
                .orElseThrow(() -> new NotFoundException("Model not found: " + slug));
    }

    /**
     * Assigns or updates an alias (including {@code "default"}) for {@code slug}.
     *
     * @param slug    the model slug.
     * @param alias   the alias name (cannot be {@code "latest"}).
     * @param version target loaded version.
     * @return updated version summary.
     * @throws NotFoundException   if the slug or target version is not loaded.
     * @throws BadRequestException if the alias name is invalid or reserved.
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
            throw new NotFoundException("Model not found: " + validSlug);
        }
        if (!registry.contains(new ModelRef(validSlug, validVersion))) {
            throw new NotFoundException("Model '" + validSlug + "' version '" + validVersion + "' not found");
        }
        return registry.setAlias(validSlug, validAlias, validVersion);
    }

    /**
     * Deletes a custom alias, or unpins {@code "default"} so it reverts to tracking {@code latest}.
     *
     * @param slug  the model slug.
     * @param alias the alias name.
     * @return updated version summary.
     * @throws NotFoundException   if the slug or custom alias does not exist.
     * @throws BadRequestException if the alias is invalid or reserved ({@code "latest"}).
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
            throw new NotFoundException("Model not found: " + validSlug);
        }
        boolean removed = registry.deleteAlias(validSlug, validAlias);
        if (!removed && !ModelRef.ALIAS_DEFAULT.equals(validAlias)) {
            throw new NotFoundException("Alias '" + validAlias + "' not found on model '" + validSlug + "'");
        }
        return getVersions(validSlug);
    }

    /**
     * Returns a snapshot of the default loaded model per slug, in slug order.
     */
    public Map<String, InferenceModel> models() {
        Map<String, InferenceModel> map = new LinkedHashMap<>();
        for (var entry : registry.defaultEntries()) {
            map.put(entry.ref().slug(), entry.model());
        }
        return Collections.unmodifiableMap(map);
    }

    /**
     * Returns every active loaded model version across all slugs.
     */
    public List<InferenceModel> allModels() {
        return registry.allEntries().stream().map(ModelRegistry.Entry::model).toList();
    }

    /**
     * Performs inference using JSON-encoded input on the default version of {@code slug}.
     */
    public Prediction predict(String slug, JsonObject request)
            throws BadRequestException, NotFoundException {
        return predict(slug, request, false, null);
    }

    /**
     * Performs inference using JSON-encoded input with optional explanations on the default version of {@code slug}.
     */
    public Prediction predict(String slug, JsonObject request, boolean explain)
            throws BadRequestException, NotFoundException {
        return predict(slug, request, explain, null);
    }

    /**
     * Performs inference using JSON-encoded input on the default version of {@code slug}.
     */
    public Prediction predict(String slug, JsonObject request, boolean explain, KernelSHAP.Options shapOptions)
            throws BadRequestException, NotFoundException {
        return predict(getModel(slug), request, explain, shapOptions);
    }

    /**
     * Performs inference against a resolved {@link InferenceModel} instance and emits audit events.
     */
    public Prediction predict(InferenceModel model, JsonObject request, boolean explain, KernelSHAP.Options shapOptions)
            throws BadRequestException {
        long start = System.nanoTime();
        Prediction prediction = model.predict(request, explain, shapOptions);
        if (!listeners.isEmpty()) {
            notifyListeners(InferenceEvent.smilePredict(
                    model.slug(),
                    model.version(),
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
     */
    boolean hasListeners() {
        return !listeners.isEmpty();
    }

    /**
     * Online updates the default version of {@code slug} using JSON-encoded input.
     */
    public void update(String slug, JsonObject request, boolean persist)
            throws BadRequestException, NotFoundException {
        update(getModel(slug), request, persist);
    }

    /**
     * Online updates a resolved {@link InferenceModel} using JSON-encoded input,
     * optionally persisting to disk.
     */
    public void update(InferenceModel model, JsonObject request, boolean persist)
            throws BadRequestException {
        long start = System.nanoTime();
        if (persist) {
            checkBeforePersist(model);
        }
        model.update(request);
        if (persist) {
            persistModel(model);
        }
        if (!listeners.isEmpty()) {
            notifyListeners(InferenceEvent.onlineUpdate(
                    model.slug(),
                    model.version(),
                    request != null ? request.encode() : null,
                    persist,
                    System.nanoTime() - start));
        }
    }

    /**
     * Checks registered {@link ModelArtifactVerifier} hooks before persisting an
     * online-updated model to disk.
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
     * Reloads the default (or last known) version of {@code slug} from disk.
     *
     * @param slug the model slug.
     * @return the reloaded inference model.
     * @throws NotFoundException            if the model or its backing file does not exist.
     * @throws ClientErrorException         if reloading would collide with another loaded version.
     * @throws InternalServerErrorException if the file fails to deserialize.
     */
    public InferenceModel reloadModel(String slug)
            throws NotFoundException, ClientErrorException, InternalServerErrorException {
        ModelRef target = registry.reloadTargetForSlug(slug)
                .orElseThrow(() -> new NotFoundException("Model not found: " + slug));
        return reloadModelVersion(target.slug(), target.version());
    }

    /**
     * Reloads a specific {@code (slug, version)} from its backing file on disk.
     * Preserves any explicit slug/version overrides that were configured at load time.
     *
     * @param slug    the model slug.
     * @param version the model version.
     * @return the reloaded inference model.
     */
    public InferenceModel reloadModelVersion(String slug, String version)
            throws NotFoundException, ClientErrorException, InternalServerErrorException {
        ModelRef oldRef;
        try {
            oldRef = new ModelRef(slug, version);
        } catch (IllegalArgumentException ex) {
            throw new NotFoundException("Model '" + slug + "' version '" + version + "' not found");
        }
        ModelSource source = registry.knownSource(oldRef).orElse(null);
        if (source == null || !Files.isRegularFile(source.path())) {
            throw new NotFoundException("Model file not found on disk for '" + oldRef + "'");
        }

        try {
            logger.infof("Reloading model '%s' from '%s'", oldRef, source.path());
            Model m = verifyAndReadModel(source);
            ModelRef newRef = InferenceModel.resolveRef(m, source);
            var reloaded = new InferenceModel(m, source, newRef);

            try {
                registry.replace(oldRef, new ModelRegistry.Entry<>(newRef, reloaded, source));
            } catch (ModelRegistry.ConflictException ex) {
                throw new ClientErrorException(ex.getMessage(), Response.Status.CONFLICT);
            }

            quarantined.remove(source.path());
            if (!oldRef.equals(newRef)) {
                unregisterMetrics(oldRef);
            }
            unregisterMetrics(newRef);
            registerMetrics(reloaded);
            notifyListeners(InferenceEvent.lifecycle(
                    ModelMetricsBinder.SMILE_NAMESPACE,
                    reloaded.slug(),
                    reloaded.version(),
                    InferenceEvent.Action.MODEL_RELOADED,
                    source.path().toString()));
            logger.infof("Model '%s' (version '%s') reloaded successfully", reloaded.slug(), reloaded.version());
            return reloaded;
        } catch (ClientErrorException ex) {
            throw ex;
        } catch (SecurityException ex) {
            logger.errorf(ex, "Rejected reload of model '%s' from '%s'", oldRef, source.path());
            throw new ForbiddenException("Model artifact verification failed: " + ex.getMessage(), ex);
        } catch (Exception ex) {
            logger.errorf(ex, "Failed to reload model '%s' from '%s'", oldRef, source.path());
            throw new InternalServerErrorException("Failed to reload model: " + ex.getMessage(), ex);
        }
    }

    /**
     * Gracefully unloads all active versions of {@code slug}, waiting for in-flight requests.
     *
     * @param slug         the model slug.
     * @param graceTimeout max duration to wait per version for in-flight requests.
     * @return {@code true} if all versions drained cleanly before timeout.
     * @throws NotFoundException if {@code slug} is not loaded.
     */
    public boolean unloadModel(String slug, Duration graceTimeout) throws NotFoundException {
        List<ModelRegistry.Entry<InferenceModel>> removed = registry.removeSlug(slug);
        if (removed.isEmpty()) {
            throw new NotFoundException("Model not found: " + slug);
        }
        boolean allDrained = true;
        for (var entry : removed) {
            InferenceModel existing = entry.model();
            boolean drained = existing.unload(graceTimeout);
            allDrained &= drained;
            unregisterMetrics(entry.ref());
            notifyListeners(InferenceEvent.lifecycle(
                    ModelMetricsBinder.SMILE_NAMESPACE,
                    existing.slug(),
                    existing.version(),
                    InferenceEvent.Action.MODEL_UNLOADED,
                    "{\"drained\":" + drained + "}"));
            logger.infof("Model '%s' (version '%s') unloaded (drained in-flight: %s)",
                    existing.slug(), existing.version(), drained);
        }
        return allDrained;
    }

    /**
     * Gracefully unloads a single {@code (slug, version)} from active memory.
     *
     * @param slug         the model slug.
     * @param version      the model version.
     * @param graceTimeout max duration to wait for in-flight requests.
     * @return {@code true} if all in-flight requests completed before timeout.
     * @throws NotFoundException if the version is not loaded.
     */
    public boolean unloadModelVersion(String slug, String version, Duration graceTimeout) throws NotFoundException {
        ModelRef ref;
        try {
            ref = new ModelRef(slug, version);
        } catch (IllegalArgumentException ex) {
            throw new NotFoundException("Model '" + slug + "' version '" + version + "' not found");
        }
        var removed = registry.removeVersion(ref)
                .orElseThrow(() -> new NotFoundException(
                        "Model '" + slug + "' version '" + version + "' not found"));
        InferenceModel existing = removed.model();
        boolean drained = existing.unload(graceTimeout);
        unregisterMetrics(ref);
        notifyListeners(InferenceEvent.lifecycle(
                ModelMetricsBinder.SMILE_NAMESPACE,
                existing.slug(),
                existing.version(),
                InferenceEvent.Action.MODEL_UNLOADED,
                "{\"drained\":" + drained + "}"));
        logger.infof("Model '%s' (version '%s') unloaded (drained in-flight: %s)",
                existing.slug(), existing.version(), drained);
        return drained;
    }

    /**
     * Dynamically loads a model or directory of models from disk into active memory.
     */
    public List<InferenceModel> load(Path path)
            throws NotFoundException, ClientErrorException, BadRequestException {
        return load(path, ModelLoadOptions.EMPTY);
    }

    /**
     * Dynamically loads a model or directory of models from disk with optional
     * slug, version, alias, and default-routing overrides.
     *
     * @param path    file or directory path.
     * @param options optional identity and alias overrides.
     * @return list of newly loaded inference models.
     */
    public List<InferenceModel> load(Path path, ModelLoadOptions options)
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
            sources.add(new ModelSource(absPath, opts.slug(), opts.version()));
        } else if (Files.isDirectory(absPath)) {
            ModelDiscovery.Plan plan = ModelDiscovery.discover(
                    absPath, ModelManifest.Kind.SML, null, null, false);
            if (!plan.errors().isEmpty()) {
                var first = plan.errors().getFirst();
                throw new BadRequestException(
                        "Failed to scan '" + first.path() + "': " + first.error().getMessage(),
                        first.error());
            }
            if (plan.candidates().isEmpty()) {
                throw new BadRequestException("No .sml models found at: " + path);
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

        List<ModelRegistry.Entry<InferenceModel>> staged = new ArrayList<>();
        Set<ModelRef> batchRefs = new HashSet<>();

        for (ModelSource source : sources) {
            Path normalized = source.path();
            // Pre-deserialization duplicate check when SMLM extended header is present
            try {
                smile.model.Format.Header header = smile.model.Format.inspect(normalized);
                if (!header.legacy() && header.payloadSize() >= 0) {
                    ModelRef candidateRef = InferenceModel.resolveRefFromHeader(header, source);
                    if (registry.contains(candidateRef) || batchRefs.contains(candidateRef)) {
                        throw conflictException(candidateRef);
                    }
                }
            } catch (ClientErrorException e) {
                throw e;
            } catch (IllegalArgumentException e) {
                throw new BadRequestException("Invalid model identity for " + normalized + ": " + e.getMessage(), e);
            } catch (IOException ignored) {
                // Header/CRC/framing errors will be surfaced by verifyAndReadModel below
            }

            Model m;
            try {
                m = verifyAndReadModel(source);
            } catch (SecurityException e) {
                throw new ForbiddenException("Model artifact verification failed for " + normalized + ": " + e.getMessage(), e);
            } catch (Exception e) {
                throw new BadRequestException("Failed to read model from " + normalized + ": " + e.getMessage(), e);
            }

            ModelRef ref;
            try {
                ref = InferenceModel.resolveRef(m, source);
            } catch (IllegalArgumentException e) {
                throw new BadRequestException("Invalid model identity for " + normalized + ": " + e.getMessage(), e);
            }

            if (registry.contains(ref) || !batchRefs.add(ref)) {
                throw conflictException(ref);
            }
            var model = new InferenceModel(m, source, ref);
            staged.add(new ModelRegistry.Entry<>(ref, model, source));
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
            throw conflictException(ex.ref());
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException(ex.getMessage(), ex);
        }

        List<InferenceModel> loaded = new ArrayList<>(staged.size());
        for (var entry : staged) {
            InferenceModel model = entry.model();
            loaded.add(model);
            quarantined.remove(model.path());
            registerMetrics(model);
            notifyListeners(InferenceEvent.lifecycle(
                    ModelMetricsBinder.SMILE_NAMESPACE,
                    model.slug(),
                    model.version(),
                    InferenceEvent.Action.MODEL_LOADED,
                    model.path().toString()));
            logger.infof("Dynamically loaded model '%s' (version '%s') from '%s'",
                    model.slug(), model.version(), model.path());
        }
        return loaded;
    }

    private static ClientErrorException conflictException(ModelRef ref) {
        return new ClientErrorException(
                "Model '" + ref.slug() + "' version '" + ref.version()
                        + "' is already loaded. Use POST /v1/smile/" + ref.slug()
                        + "/versions/" + ref.version() + "/reload to refresh it, or unload it first.",
                Response.Status.CONFLICT);
    }
}
