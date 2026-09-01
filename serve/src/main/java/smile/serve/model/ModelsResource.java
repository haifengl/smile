/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE Serve is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package smile.serve.model;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import io.smallrye.common.annotation.RunOnVirtualThread;

/**
 * OpenAI-compatible models API at {@code /api/v1/models}.
 *
 * <p>Lists and retrieves every loaded model in one catalog: chat LLMs, ONNX
 * graphs, and SMILE {@code .sml} models. Classic SMILE inference remains under
 * {@code /api/v1/ml/models/{id}}; ONNX inference under {@code /api/v1/onnx/{id}}.
 *
 * @author Haifeng Li
 * @see <a href="https://developers.openai.com/api/reference/resources/models/methods/list">OpenAI List models</a>
 * @see <a href="https://developers.openai.com/api/reference/resources/models/methods/retrieve">OpenAI Retrieve model</a>
 */
@Path("/models")
@RunOnVirtualThread
@Produces(MediaType.APPLICATION_JSON)
public class ModelsResource {

    @Inject
    ModelCatalog catalog;

    /**
     * Lists all currently available models (chat, ONNX, and SMILE).
     *
     * @return OpenAI-shaped {@code { object: "list", data: [...] }}.
     */
    @GET
    public ModelList list() {
        return ModelList.of(catalog.list());
    }

    /**
     * Retrieves a single model by id (OpenAI retrieve-model parity).
     *
     * <p>The path accepts ids that contain slashes (e.g. Hugging Face repo ids)
     * via a greedy path segment. Inference is <em>not</em> performed here —
     * use the type-specific endpoints for that.
     *
     * @param id the public model id.
     * @return the model object.
     * @throws NotFoundException if no loaded model has this id.
     */
    @GET
    @Path("/{id:.+}")
    public ModelObject retrieve(@PathParam("id") String id) {
        return catalog.find(id, true)
                .orElseThrow(() -> new NotFoundException("Model not found: " + id));
    }
}
