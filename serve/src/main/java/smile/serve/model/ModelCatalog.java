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
import java.util.List;
import java.util.Optional;
import io.quarkus.runtime.Quarkus;
import io.quarkus.runtime.StartupEvent;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.interceptor.Interceptor;
import org.jboss.logging.Logger;

/**
 * Aggregates every {@link OpenAiModelContributor} into a single catalog.
 */
@ApplicationScoped
public class ModelCatalog {
    private static final Logger logger = Logger.getLogger(ModelCatalog.class);

    @Inject
    Instance<OpenAiModelContributor> contributors;

    /**
     * Checks on startup that at least one model has been loaded; otherwise
     * exits gracefully.
     *
     * @param event the startup event.
     */
    void onStart(@Observes @Priority(Interceptor.Priority.PLATFORM_BEFORE) StartupEvent event) {
        if (isEmpty()) {
            logger.warn("No model found or loaded successfully at startup. Exiting service...");
            Quarkus.asyncExit();
        }
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
