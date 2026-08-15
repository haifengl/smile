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

import java.util.List;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * OpenAI-compatible list wrapper for {@code GET /models}.
 *
 * @param data   available models.
 * @param object always {@code "list"}.
 *
 * @author Haifeng Li
 */
public record ModelList(
        List<ModelObject> data,
        @JsonProperty("object") String object) {

    /**
     * Wraps model entries in an OpenAI list response.
     *
     * @param data the model entries.
     * @return the list envelope.
     */
    public static ModelList of(List<ModelObject> data) {
        return new ModelList(data, "list");
    }
}
