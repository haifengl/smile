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
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import smile.llm.ChatCompletion;
import smile.llm.Message;
import smile.llm.Role;
import smile.llm.ToolCall;

/**
 * OpenAI wire-format chat message (response / request shape without multimodal parts).
 *
 * @param role       message role name.
 * @param content    text content; may be {@code null} when tool_calls present.
 * @param toolCalls  assistant tool calls.
 * @param toolCallId tool result id.
 * @param name       optional name.
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ChatMessageObject(
        String role,
        String content,
        List<ChatToolCallObject> toolCalls,
        String toolCallId,
        String name) {

    /**
     * Maps an internal {@link Message} to the wire DTO.
     */
    public static ChatMessageObject of(Message message) {
        if (message == null) {
            return null;
        }
        String content = message.content();
        if (content != null && content.isEmpty() && message.hasToolCalls()) {
            content = null;
        }
        return new ChatMessageObject(
                message.role().name(),
                content,
                ChatToolCallObject.of(message.toolCalls()),
                message.toolCallId(),
                message.name());
    }

    /**
     * Builds an assistant message from a {@link ChatCompletion}.
     */
    public static ChatMessageObject of(ChatCompletion completion) {
        if (completion == null) {
            return new ChatMessageObject(Role.assistant.name(), "", null, null, null);
        }
        String content = completion.content();
        List<ChatToolCallObject> toolCalls = ChatToolCallObject.of(completion.toolCalls());
        if (completion.hasToolCalls() && (content == null || content.isEmpty())) {
            content = null;
        }
        return new ChatMessageObject(Role.assistant.name(), content, toolCalls, null, null);
    }

    /**
     * Maps from internal tool calls + optional content.
     */
    public static ChatMessageObject assistant(String content, List<ToolCall> toolCalls) {
        List<ChatToolCallObject> wire = ChatToolCallObject.of(toolCalls);
        if (wire != null && (content == null || content.isEmpty())) {
            content = null;
        }
        return new ChatMessageObject(Role.assistant.name(), content, wire, null, null);
    }
}
