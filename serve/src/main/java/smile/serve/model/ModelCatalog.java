/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE Serve is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 */
package smile.serve.model;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import io.quarkus.runtime.Quarkus;
import io.quarkus.runtime.StartupEvent;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.interceptor.Interceptor;
import org.jboss.logging.Logger;
import smile.serve.InferenceServiceConfig;

/**
 * Aggregates every {@link OpenAiModelContributor} into a single catalog.
 */
@ApplicationScoped
public class ModelCatalog {
    private static final Logger logger = Logger.getLogger(ModelCatalog.class);

    @Inject
    Instance<OpenAiModelContributor> contributors;

    @Inject
    InferenceServiceConfig config;

    /**
     * Checks on startup that at least one model has been loaded; otherwise
     * exits gracefully.
     *
     * <p>When {@code smile.serve.allow-empty=true} the service stays up with an
     * empty catalog so models can be loaded later through
     * {@code POST /v1/models/load}. Without this, a dynamic-load client
     * (Studio) would race the exit: it starts the service empty and loads a
     * model immediately after, but the service would already have shut down.
     *
     * @param event the startup event.
     */
    void onStart(@Observes @Priority(Interceptor.Priority.PLATFORM_BEFORE) StartupEvent event) {
        List<QuarantinedModel> quarantined = listQuarantined();
        boolean failFast = config == null || config.failFast();
        boolean allowEmpty = config != null && config.allowEmpty();
        if (shouldExitOnFatalQuarantine(quarantined, failFast)) {
            logger.errorf("Aborting startup: %d model(s) failed verification or deserialization "
                    + "(smile.serve.fail-fast=%s): %s",
                    quarantined.size(), failFast, quarantined);
            Quarkus.asyncExit(1);
            return;
        }
        if (shouldExitOnEmptyStartup(isEmpty(), allowEmpty)) {
            logger.warn("No model found or loaded successfully at startup. Exiting service...");
            Quarkus.asyncExit();
        } else if (isEmpty()) {
            logger.info("No model loaded at startup; waiting for dynamic loads "
                    + "(smile.serve.allow-empty=true)");
        } else {
            warnOnCrossBackendCollisions();
        }
    }

    private void warnOnCrossBackendCollisions() {
        Set<String> seen = new HashSet<>();
        for (ModelObject model : list()) {
            if (!seen.add(model.id())) {
                logger.warnf("Model id '%s' is registered by multiple backends; "
                        + "GET /v1/models/%s will return the first contributor match. "
                        + "Assign distinct slugs via models.json to avoid shadowing.",
                        model.id(), model.id());
            }
        }
    }

    /**
     * Decides whether quarantined startup models should terminate the service.
     *
     * <p>Security rejections ({@code fatal == true} from {@link SecurityException}
     * or {@code ObjectInputFilter} rejection) or any failure when
     * {@code failFast == true} aborts startup.
     *
     * @param quarantined quarantined model failure records.
     * @param failFast    whether {@code smile.serve.fail-fast} is enabled.
     * @return {@code true} when startup should abort.
     */
    static boolean shouldExitOnFatalQuarantine(List<QuarantinedModel> quarantined, boolean failFast) {
        if (quarantined == null || quarantined.isEmpty()) {
            return false;
        }
        if (failFast) {
            return true;
        }
        return quarantined.stream().anyMatch(QuarantinedModel::fatal);
    }

    /**
     * Decides whether an empty catalog at startup should terminate the service.
     *
     * <p>Extracted as a pure function so the policy is unit-testable: the
     * {@code Quarkus.asyncExit()} it guards cannot be observed from a
     * {@code @QuarkusTest}, because test mode keeps the HTTP server alive.
     *
     * @param isEmpty    whether no model is loaded.
     * @param allowEmpty whether {@code smile.serve.allow-empty} is set.
     * @return {@code true} when the service should exit.
     */
    static boolean shouldExitOnEmptyStartup(boolean isEmpty, boolean allowEmpty) {
        return isEmpty && !allowEmpty;
    }

    /**
     * Returns {@code true} if no models have been loaded.
     *
     * @return {@code true} if no models are loaded.
     */
    public boolean isEmpty() {
        return list().isEmpty();
    }

    /**
     * Lists all loaded models from every backend.
     *
     * @return merged catalog entries.
     */
    public List<ModelObject> list() {
        List<ModelObject> data = new ArrayList<>();
        for (OpenAiModelContributor contributor : contributors) {
            data.addAll(contributor.listOpenAiModels());
        }
        return data;
    }

    /**
     * Lists all quarantined models that failed to load across every backend.
     *
     * @return merged quarantined model entries.
     */
    public List<QuarantinedModel> listQuarantined() {
        List<QuarantinedModel> quarantined = new ArrayList<>();
        for (OpenAiModelContributor contributor : contributors) {
            quarantined.addAll(contributor.listQuarantinedModels());
        }
        return quarantined;
    }

    /**
     * Retrieves a single model with optional detail blocks.
     *
     * @param id       public model id.
     * @param detailed when {@code true}, include type-specific detail blocks.
     * @return the first matching model across contributors.
     */
    public Optional<ModelObject> find(String id, boolean detailed) {
        for (OpenAiModelContributor contributor : contributors) {
            Optional<ModelObject> found = contributor.findOpenAiModel(id, detailed);
            if (found.isPresent()) {
                return found;
            }
        }
        return Optional.empty();
    }
}
