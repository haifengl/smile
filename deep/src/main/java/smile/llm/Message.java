/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * SMILE is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with SMILE. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.llm;

import java.util.List;

/**
 * Dialog message with OpenAI-style multimodal content parts and optional tool fields.
 *
 * <p>Text-only messages use a single {@link TextPart}. Multimodal turns may
 * interleave text with {@link ImageUrlPart} / {@link VideoUrlPart}. Assistant
 * turns may carry {@link #toolCalls()}; tool-result turns use {@link #toolCallId()}.
 *
 * @param role       speaker role.
 * @param parts      ordered content parts (may be empty when tool fields are set).
 * @param toolCalls  assistant tool calls; {@code null} or empty when absent.
 * @param toolCallId id of the tool call this message answers ({@code role=tool}).
 * @param name       optional function / participant name.
 * @author Haifeng Li
 */
public record Message(
        Role role,
        List<ContentPart> parts,
        List<ToolCall> toolCalls,
        String toolCallId,
        String name) {
    /**
     * Compact canonical constructor that validates inputs.
     */
    public Message {
        if (role == null) {
            throw new IllegalArgumentException("Message role must not be null");
        }
        boolean hasToolCalls = toolCalls != null && !toolCalls.isEmpty();
        boolean hasToolCallId = toolCallId != null && !toolCallId.isBlank();
        if (parts == null) {
            parts = List.of();
        }
        if (parts.isEmpty() && !hasToolCalls && !hasToolCallId) {
            throw new IllegalArgumentException(
                    "Message parts must not be empty unless tool_calls or tool_call_id is set");
        }
        for (ContentPart part : parts) {
            if (part == null) {
                throw new IllegalArgumentException("Message parts must not contain null");
            }
        }
        parts = List.copyOf(parts);
        if (toolCalls != null) {
            toolCalls = List.copyOf(toolCalls);
        }
    }

    /**
     * Text-only convenience constructor.
     *
     * @param role    speaker role.
     * @param content plain text body.
     */
    public Message(Role role, String content) {
        this(role,
                content == null
                        ? throwNullContent()
                        : List.of(new TextPart(content)),
                null, null, null);
    }

    private static List<ContentPart> throwNullContent() {
        throw new IllegalArgumentException("Message content must not be null");
    }

    /**
     * Role + content parts (no tool fields).
     *
     * @param role  speaker role.
     * @param parts content parts.
     */
    public Message(Role role, List<ContentPart> parts) {
        this(role, parts, null, null, null);
    }

    /**
     * Varargs content parts constructor.
     *
     * @param role  speaker role.
     * @param parts content parts.
     */
    public Message(Role role, ContentPart... parts) {
        this(role, parts == null ? null : List.of(parts), null, null, null);
    }

    /**
     * Concatenates all {@link TextPart} text (ignores media parts).
     *
     * @return combined text, possibly empty.
     */
    public String content() {
        StringBuilder sb = new StringBuilder();
        for (ContentPart part : parts) {
            if (part instanceof TextPart text) {
                sb.append(text.text());
            }
        }
        return sb.toString();
    }

    /**
     * @return {@code true} when any part is an image, video, or audio.
     */
    public boolean hasMedia() {
        for (ContentPart part : parts) {
            if (part instanceof ImageUrlPart || part instanceof VideoUrlPart
                    || part instanceof AudioUrlPart) {
                return true;
            }
        }
        return false;
    }

    /**
     * @return {@code true} when any part is audio.
     */
    public boolean hasAudio() {
        for (ContentPart part : parts) {
            if (part instanceof AudioUrlPart) {
                return true;
            }
        }
        return false;
    }

    /**
     * @return {@code true} when this assistant message includes tool calls.
     */
    public boolean hasToolCalls() {
        return toolCalls != null && !toolCalls.isEmpty();
    }

    /**
     * Copy with replaced content parts, preserving tool metadata.
     *
     * @param newParts replacement parts.
     * @return new message.
     */
    public Message withParts(List<ContentPart> newParts) {
        return new Message(role, newParts, toolCalls, toolCallId, name);
    }

    /**
     * Factory method for a system message.
     *
     * @param content the message content.
     * @return a system message.
     */
    public static Message system(String content) {
        return new Message(Role.system, content);
    }

    /**
     * Factory method for a user message.
     *
     * @param content the message content.
     * @return a user message.
     */
    public static Message user(String content) {
        return new Message(Role.user, content);
    }

    /**
     * Multimodal user message.
     *
     * @param parts ordered content parts.
     * @return a user message.
     */
    public static Message user(ContentPart... parts) {
        return new Message(Role.user, parts);
    }

    /**
     * Factory method for an assistant message.
     *
     * @param content the message content.
     * @return an assistant message.
     */
    public static Message assistant(String content) {
        return new Message(Role.assistant, content);
    }

    /**
     * Assistant message with tool calls.
     *
     * @param content   optional text prefix; may be {@code null} or blank.
     * @param toolCalls tool calls (required, non-empty).
     * @return assistant message.
     */
    public static Message assistant(String content, List<ToolCall> toolCalls) {
        List<ContentPart> parts = (content == null || content.isEmpty())
                ? List.of()
                : List.of(new TextPart(content));
        return new Message(Role.assistant, parts, toolCalls, null, null);
    }

    /**
     * Tool-result message.
     *
     * @param toolCallId id of the tool call being answered.
     * @param content    tool result text.
     * @return tool message.
     */
    public static Message tool(String toolCallId, String content) {
        List<ContentPart> parts = content == null ? List.of() : List.of(new TextPart(content));
        return new Message(Role.tool, parts, null, toolCallId, null);
    }
}
