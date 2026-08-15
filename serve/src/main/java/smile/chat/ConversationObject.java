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

import java.util.Map;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

/**
 * OpenAI-compatible conversation resource returned by create/retrieve/update
 * (and by the smile-specific list endpoint).
 *
 * @param id        external conversation id ({@code conv_<numeric>}).
 * @param createdAt Unix epoch seconds.
 * @param metadata  optional string key/value tags (at most 16 pairs).
 * @param object    always {@code "conversation"}.
 *
 * @author Haifeng Li
 * @see <a href="https://developers.openai.com/api/reference/resources/conversations">OpenAI Conversations</a>
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ConversationObject(
        String id,
        long createdAt,
        Map<String, String> metadata,
        @JsonProperty("object") String object) {

    /**
     * Maps a persisted entity to the OpenAI response shape.
     *
     * @param conversation the JPA entity.
     * @return the API object.
     */
    public static ConversationObject from(Conversation conversation) {
        Map<String, String> metadata = conversation.metadata == null
                ? Map.of()
                : Map.copyOf(conversation.metadata);
        return new ConversationObject(
                ConversationIds.toExternalId(conversation.id),
                conversation.createdAt.getEpochSecond(),
                metadata,
                "conversation");
    }
}
