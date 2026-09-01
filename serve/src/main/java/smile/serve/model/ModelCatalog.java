/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE Serve is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package smile.serve.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

/**
 * Aggregates every {@link OpenAiModelContributor} into a single catalog.
 */
@ApplicationScoped
public class ModelCatalog {

    @Inject
    Instance<OpenAiModelContributor> contributors;

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
