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

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

/**
 * OpenAI-compatible response for {@code DELETE /conversations/{conversation_id}}.
 *
 * @param id      external conversation id that was deleted.
 * @param deleted always {@code true} on success.
 * @param object  always {@code "conversation.deleted"}.
 *
 * @author Haifeng Li
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record ConversationDeleted(
        String id,
        boolean deleted,
        @JsonProperty("object") String object) {

    /**
     * Builds a successful delete acknowledgement.
     *
     * @param externalId the external conversation id.
     * @return the deleted resource object.
     */
    public static ConversationDeleted of(String externalId) {
        return new ConversationDeleted(externalId, true, "conversation.deleted");
    }
}
