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
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import smile.llm.ToolCall;

/**
 * OpenAI wire-format tool call object.
 *
 * @param id       call id.
 * @param type     {@code "function"}.
 * @param function function payload.
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ChatToolCallObject(String id, String type, ChatFunctionCallObject function) {

    /**
     * Maps an internal {@link ToolCall} to the wire DTO.
     */
    public static ChatToolCallObject of(ToolCall call) {
        if (call == null) {
            return null;
        }
        return new ChatToolCallObject(
                call.id(),
                call.type(),
                new ChatFunctionCallObject(call.function().name(), call.function().arguments()));
    }

    /**
     * Maps a list of internal tool calls.
     */
    public static java.util.List<ChatToolCallObject> of(java.util.List<ToolCall> calls) {
        if (calls == null || calls.isEmpty()) {
            return null;
        }
        return calls.stream().map(ChatToolCallObject::of).toList();
    }
}
