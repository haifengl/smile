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
package smile.chat;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

/**
 * OpenAI-compatible model object returned by {@code GET /models}.
 *
 * @param id           model identifier referenced by chat completions.
 * @param created      Unix epoch seconds when the model became available here.
 * @param object       always {@code "model"}.
 * @param ownedBy      organization / hub owner (HF owner, or family owner for local loads).
 * @param shutdownDate optional retirement date; always {@code null} for smile-serve.
 *
 * @author Haifeng Li
 * @see <a href="https://developers.openai.com/api/reference/resources/models/methods/list">OpenAI List models</a>
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@JsonInclude(JsonInclude.Include.ALWAYS)
public record ModelObject(
        String id,
        long created,
        @JsonProperty("object") String object,
        String ownedBy,
        String shutdownDate) {

    /**
     * Builds a model object with {@code object=model} and no shutdown date.
     *
     * @param id      public model id.
     * @param created load / availability timestamp (Unix seconds).
     * @param ownedBy owner string.
     * @return the model object.
     */
    public static ModelObject of(String id, long created, String ownedBy) {
        return new ModelObject(id, created, "model", ownedBy, null);
    }
}
