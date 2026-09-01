/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE Serve is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package smile.serve.model;

import java.util.List;
import java.util.Optional;

/**
 * Backend that exposes loaded models through the OpenAI {@code /models} catalog.
 */
public interface OpenAiModelContributor {

    /**
     * Returns lean OpenAI-shaped descriptors for every model this backend loads.
     *
     * @return catalog entries without retrieve-only detail blocks.
     */
    List<ModelObject> listOpenAiModels();

    /**
     * Looks up a model by public id.
     *
     * @param id       requested model id.
     * @param detailed when {@code true}, include type-specific detail blocks.
     * @return the model object when loaded; otherwise empty.
     */
    Optional<ModelObject> findOpenAiModel(String id, boolean detailed);
}
