/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE Serve is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 */
package smile.serve.model;

import java.util.List;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * OpenAI-compatible list wrapper for {@code GET /models}.
 *
 * @param data        available active models.
 * @param object      always {@code "list"}.
 * @param quarantined optional list of models that failed to load and were quarantined
 *                    (omitted when empty).
 *
 * @author Haifeng Li
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ModelList(
        @JsonInclude(JsonInclude.Include.ALWAYS) List<ModelObject> data,
        @JsonInclude(JsonInclude.Include.ALWAYS) @JsonProperty("object") String object,
        List<QuarantinedModel> quarantined) {

    /**
     * Backward-compatible two-argument constructor.
     *
     * @param data   available active models.
     * @param object always {@code "list"}.
     */
    public ModelList(List<ModelObject> data, String object) {
        this(data, object, null);
    }

    /**
     * Wraps model entries in an OpenAI list response.
     *
     * @param data the model entries.
     * @return the list envelope.
     */
    public static ModelList of(List<ModelObject> data) {
        return new ModelList(data, "list", null);
    }

    /**
     * Wraps model entries and any quarantined model diagnostics in an OpenAI
     * list response.
     *
     * @param data        the active model entries.
     * @param quarantined quarantined model failure records (omitted from JSON when empty).
     * @return the list envelope.
     */
    public static ModelList of(List<ModelObject> data, List<QuarantinedModel> quarantined) {
        return new ModelList(
                data,
                "list",
                quarantined == null || quarantined.isEmpty() ? null : List.copyOf(quarantined));
    }
}
