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
package smile.onnx.genai;

import java.util.List;
import smile.llm.AudioUrlPart;
import smile.llm.ChatOptions;
import smile.llm.ContentPart;
import smile.llm.FunctionDefinition;
import smile.llm.ImageUrlPart;
import smile.llm.Message;
import smile.llm.TextPart;
import smile.llm.ToolCall;
import smile.llm.ToolDefinition;
import smile.llm.VideoUrlPart;

/**
 * Builds GenAI chat-template JSON for {@link Tokenizer#applyChatTemplate}.
 *
 * @author Haifeng Li
 */
final class ChatTemplateJson {
    private ChatTemplateJson() {}

    /**
     * Serializes dialog messages to a JSON array GenAI chat templates expect.
     *
     * @param dialog conversation turns.
     * @return JSON array string.
     */
    static String messages(Message... dialog) {
        if (dialog == null) {
            throw new IllegalArgumentException("dialog must not be null");
        }
        StringBuilder sb = new StringBuilder(256);
        sb.append('[');
        for (int i = 0; i < dialog.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            appendMessage(sb, dialog[i]);
        }
        sb.append(']');
        return sb.toString();
    }

    /**
     * Serializes tools for the chat template, or {@code null} when none.
     *
     * @param options chat options; may be {@code null}.
     * @return tools JSON array, or {@code null}.
     */
    static String tools(ChatOptions options) {
        if (options == null || !options.hasTools()) {
            return null;
        }
        List<ToolDefinition> tools = options.toolsForTemplate();
        if (tools.isEmpty()) {
            return null;
        }
        StringBuilder sb = new StringBuilder(256);
        sb.append('[');
        for (int i = 0; i < tools.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            appendTool(sb, tools.get(i));
        }
        sb.append(']');
        return sb.toString();
    }

    /**
     * Returns whether any message contains non-text media parts.
     *
     * @param dialog conversation turns.
     * @return {@code true} when images / audio / video are present.
     */
    static boolean hasMedia(Message... dialog) {
        if (dialog == null) {
            return false;
        }
        for (Message message : dialog) {
            for (ContentPart part : message.parts()) {
                if (!(part instanceof TextPart)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Collects local image file paths from dialog messages.
     *
     * @param dialog conversation turns.
     * @return image paths (may be empty).
     */
    static String[] imagePaths(Message... dialog) {
        return dialog == null ? new String[0]
                : java.util.Arrays.stream(dialog)
                .flatMap(m -> m.parts().stream())
                .filter(ImageUrlPart.class::isInstance)
                .map(ImageUrlPart.class::cast)
                .map(ImageUrlPart::url)
                .map(ChatTemplateJson::toLocalPath)
                .toArray(String[]::new);
    }

    /**
     * Collects local audio file paths from dialog messages.
     *
     * @param dialog conversation turns.
     * @return audio paths (may be empty).
     */
    static String[] audioPaths(Message... dialog) {
        return dialog == null ? new String[0]
                : java.util.Arrays.stream(dialog)
                .flatMap(m -> m.parts().stream())
                .filter(AudioUrlPart.class::isInstance)
                .map(AudioUrlPart.class::cast)
                .map(AudioUrlPart::url)
                .map(ChatTemplateJson::toLocalPath)
                .toArray(String[]::new);
    }

    private static void appendMessage(StringBuilder sb, Message message) {
        sb.append("{\"role\":\"").append(escape(message.role().name())).append('"');
        if (message.name() != null && !message.name().isBlank()) {
            sb.append(",\"name\":\"").append(escape(message.name())).append('"');
        }
        if (message.toolCallId() != null && !message.toolCallId().isBlank()) {
            sb.append(",\"tool_call_id\":\"").append(escape(message.toolCallId())).append('"');
        }
        if (message.toolCalls() != null && !message.toolCalls().isEmpty()) {
            sb.append(",\"tool_calls\":[");
            List<ToolCall> calls = message.toolCalls();
            for (int i = 0; i < calls.size(); i++) {
                if (i > 0) {
                    sb.append(',');
                }
                appendToolCall(sb, calls.get(i));
            }
            sb.append(']');
        }
        List<ContentPart> parts = message.parts();
        if (parts.size() == 1 && parts.get(0) instanceof TextPart text) {
            sb.append(",\"content\":\"").append(escape(text.text())).append('"');
        } else if (!parts.isEmpty()) {
            sb.append(",\"content\":[");
            for (int i = 0; i < parts.size(); i++) {
                if (i > 0) {
                    sb.append(',');
                }
                appendPart(sb, parts.get(i));
            }
            sb.append(']');
        }
        sb.append('}');
    }

    private static void appendPart(StringBuilder sb, ContentPart part) {
        switch (part) {
            case TextPart text -> sb.append("{\"type\":\"text\",\"text\":\"")
                    .append(escape(text.text())).append("\"}");
            case ImageUrlPart image -> sb.append("{\"type\":\"image_url\",\"image_url\":{\"url\":\"")
                    .append(escape(image.url())).append("\"}}");
            case AudioUrlPart audio -> sb.append("{\"type\":\"audio_url\",\"audio_url\":{\"url\":\"")
                    .append(escape(audio.url())).append("\"}}");
            case VideoUrlPart video -> sb.append("{\"type\":\"video_url\",\"video_url\":{\"url\":\"")
                    .append(escape(video.url())).append("\"}}");
        }
    }

    private static void appendToolCall(StringBuilder sb, ToolCall call) {
        sb.append("{\"id\":\"").append(escape(call.id())).append('"')
                .append(",\"type\":\"").append(escape(call.type())).append('"')
                .append(",\"function\":{\"name\":\"")
                .append(escape(call.function().name())).append('"');
        if (call.function().arguments() != null) {
            sb.append(",\"arguments\":\"")
                    .append(escape(call.function().arguments())).append('"');
        }
        sb.append("}}");
    }

    private static void appendTool(StringBuilder sb, ToolDefinition tool) {
        FunctionDefinition fn = tool.function();
        sb.append("{\"type\":\"").append(escape(tool.type())).append('"')
                .append(",\"function\":{\"name\":\"").append(escape(fn.name())).append('"');
        if (fn.description() != null) {
            sb.append(",\"description\":\"").append(escape(fn.description())).append('"');
        }
        if (fn.parameters() != null) {
            sb.append(",\"parameters\":");
            appendJsonValue(sb, fn.parameters());
        }
        sb.append("}}");
    }

    @SuppressWarnings("unchecked")
    private static void appendJsonValue(StringBuilder sb, Object value) {
        switch (value) {
            case null -> sb.append("null");
            case String s -> sb.append('"').append(escape(s)).append('"');
            case Number n -> sb.append(n);
            case Boolean b -> sb.append(b);
            case java.util.Map<?, ?> map -> {
                sb.append('{');
                boolean first = true;
                for (var e : map.entrySet()) {
                    if (!first) {
                        sb.append(',');
                    }
                    first = false;
                    sb.append('"').append(escape(String.valueOf(e.getKey()))).append("\":");
                    appendJsonValue(sb, e.getValue());
                }
                sb.append('}');
            }
            case List<?> list -> {
                sb.append('[');
                for (int i = 0; i < list.size(); i++) {
                    if (i > 0) {
                        sb.append(',');
                    }
                    appendJsonValue(sb, list.get(i));
                }
                sb.append(']');
            }
            case Object[] arr -> {
                sb.append('[');
                for (int i = 0; i < arr.length; i++) {
                    if (i > 0) {
                        sb.append(',');
                    }
                    appendJsonValue(sb, arr[i]);
                }
                sb.append(']');
            }
            default -> sb.append('"').append(escape(value.toString())).append('"');
        }
    }

    private static String toLocalPath(String url) {
        if (url.startsWith("file:")) {
            return java.nio.file.Path.of(java.net.URI.create(url)).toString();
        }
        if (url.startsWith("http://") || url.startsWith("https://") || url.startsWith("data:")) {
            throw new GenAIException(
                    "GenAI multimodal inputs currently require local file paths; got: " + url);
        }
        return url;
    }

    private static String escape(String s) {
        if (s == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.toString();
    }
}
