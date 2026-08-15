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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

/**
 * A simplified OpenAI conversation input item accepted on create.
 *
 * <p>Full OpenAI item unions (tool calls, computer use, etc.) are ignored;
 * only message-like payloads with a usable text {@code content} are stored.
 *
 * @author Haifeng Li
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@JsonIgnoreProperties(ignoreUnknown = true)
public class ConversationItemInput {
    /** Item type; typically {@code "message"} when present. */
    public String type;
    /** Message role ({@code user}, {@code assistant}, {@code system}, {@code developer}). */
    public String role;
    /**
     * Message content: a string, or an array of content parts that include a
     * {@code text} field (OpenAI input_text / output_text shapes).
     */
    public JsonNode content;

    /**
     * Extracts plain text suitable for persistence.
     *
     * @return the text content, or {@code null} when none can be derived.
     */
    public String contentText() {
        if (content == null || content.isNull()) {
            return null;
        }
        if (content.isTextual()) {
            return content.asText();
        }
        if (content.isArray()) {
            StringBuilder sb = new StringBuilder();
            for (JsonNode part : content) {
                if (part == null || part.isNull()) {
                    continue;
                }
                if (part.isTextual()) {
                    sb.append(part.asText());
                } else if (part.hasNonNull("text")) {
                    sb.append(part.get("text").asText());
                }
            }
            return sb.isEmpty() ? null : sb.toString();
        }
        if (content.hasNonNull("text")) {
            return content.get("text").asText();
        }
        return null;
    }
}
