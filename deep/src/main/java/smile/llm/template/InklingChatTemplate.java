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
package smile.llm.template;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import tools.jackson.databind.ObjectMapper;
import smile.llm.AudioUrlPart;
import smile.llm.ChatOptions;
import smile.llm.ContentPart;
import smile.llm.ImageUrlPart;
import smile.llm.Message;
import smile.llm.Role;
import smile.llm.TextPart;
import smile.llm.ToolCall;
import smile.llm.ToolDefinition;

/**
 * Java port of the Inkling-2 {@code chat_template.jinja}. A prompt is a sequence of messages, each written as a
 * role header, a content-type token, the text and {@code <|end_message|>}:
 * <pre>
 * &lt;|message_system|&gt;tool_declare&lt;|content_xml|&gt;[tool specs as sorted compact JSON]&lt;|end_message|&gt;
 * &lt;|message_system|&gt;&lt;|content_text|&gt;Thinking effort level: 0.9&lt;|end_message|&gt;
 * &lt;|message_user|&gt;&lt;|content_text|&gt;...&lt;|end_message|&gt;
 * </pre>
 * There is no generation prompt: the model starts its reply with {@code <|message_model|>} itself. An assistant turn
 * is an optional reasoning message, a text message, one {@code <|content_invoke_tool_json|>} message per tool
 * call, and {@code <|content_model_end_sampling|>}.
 *
 * <p>The "thinking effort" system message is emitted once, right before the first non-system message (or at the
 * end if there is none). It comes from {@link ChatOptions#reasoningEffort()} ({@code none}, {@code minimal},
 * {@code low}, {@code medium}, {@code high}, {@code xhigh}, {@code max}, or a number in [0, 0.99]); when that is
 * unset, {@link ChatOptions#THINKING_DISABLED} means {@code none} and anything else the template default 0.9.
 *
 * <p>Differences from the Jinja template, all because {@link Message} is not an arbitrary JSON object: there is no
 * {@code reasoning_content} field, so reasoning is read from a {@code <think>...</think>} prefix of an assistant
 * message's content (what this server returns to clients); tool-call arguments are a JSON string that is parsed
 * (the template wants a parsed object). Output is checked byte for byte against the Jinja template rendered by
 * Hugging Face transformers.
 *
 * @author Haifeng Li
 */
public final class InklingChatTemplate implements ChatTemplate {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Map<String, Double> EFFORT = Map.of(
            "none", 0.0, "minimal", 0.1, "low", 0.2, "medium", 0.7, "high", 0.9, "xhigh", 0.99, "max", 0.99);
    /** Effort used when the request does not set one. */
    private static final double DEFAULT_EFFORT = 0.9;
    private static final String THINK_BEGIN = "<think>";
    private static final String THINK_END = "</think>";

    /** Creates the template. */
    public InklingChatTemplate() { }

    @Override
    public String encode(List<Message> dialog, ChatOptions options) {
        if (dialog == null || dialog.isEmpty()) {
            throw new IllegalArgumentException("No messages provided.");
        }
        ChatOptions opts = options == null ? ChatOptions.NONE : options;
        StringBuilder sb = new StringBuilder();

        List<ToolDefinition> tools = opts.toolsForTemplate();
        if (!tools.isEmpty()) {
            List<Map<String, Object>> specs = new ArrayList<>();
            for (ToolDefinition tool : tools) {
                Map<String, Object> spec = new LinkedHashMap<>();
                var fn = tool.function();
                spec.put("description", fn.description() == null || fn.description().isEmpty() ? "" : fn.description());
                spec.put("name", fn.name());
                spec.put("parameters", fn.parameters() == null || fn.parameters().isEmpty() ? Map.of() : fn.parameters());
                spec.put("type", tool.type());
                specs.add(spec);
            }
            sb.append("<|message_system|>tool_declare<|content_xml|>")
                    .append(PyJson.dumpsCompact(specs, true)).append("<|end_message|>");
        }

        boolean effortEmitted = false;
        for (Message message : dialog) {
            if (!effortEmitted && message.role() != Role.system) {
                appendEffort(sb, opts);
                effortEmitted = true;
            }
            appendMessage(sb, message, dialog);
        }
        if (!effortEmitted) {
            appendEffort(sb, opts);
        }
        return sb.toString();
    }

    /** The Jinja macro {@code emit_thinking_effort}. */
    private static void appendEffort(StringBuilder sb, ChatOptions opts) {
        String eff = opts.reasoningEffort();
        if (eff == null && ChatOptions.THINKING_DISABLED.equals(opts.thinkingMode())) {
            eff = "none";
        }
        double num;
        if (eff == null) {
            num = DEFAULT_EFFORT;
        } else {
            String key = PyJson.strip(eff);
            Double named = EFFORT.get(key);
            if (named != null) {
                num = named;
            } else {
                try {
                    num = Double.parseDouble(key);
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("Unknown reasoning_effort: " + eff);
                }
            }
        }
        if (!(num >= 0.0 && num <= 0.99)) {
            throw new IllegalArgumentException("reasoning_effort must be in [0.0, 0.99]");
        }
        sb.append("<|message_system|><|content_text|>Thinking effort level: ")
                .append(num == 0.0 ? "0" : PyJson.floatRepr(num)).append("<|end_message|>");
    }

    private static String roleToken(Role role) {
        return switch (role) {
            case user -> "<|message_user|>";
            case assistant -> "<|message_model|>";
            case system -> "<|message_system|>";
            case tool -> "<|message_tool|>";
        };
    }

    private static void appendMessage(StringBuilder sb, Message message, List<Message> dialog) {
        String rtok = roleToken(message.role());
        if (message.role() == Role.tool) {
            if (message.name() != null && !message.name().isEmpty()) {
                rtok += message.name();
            } else if (message.toolCallId() != null && !message.toolCallId().isEmpty()) {
                // The template keeps the last match over all earlier and later assistant messages.
                for (Message prev : dialog) {
                    if (prev.role() == Role.assistant && prev.toolCalls() != null) {
                        for (ToolCall tc : prev.toolCalls()) {
                            if (message.toolCallId().equals(tc.id())) {
                                rtok = "<|message_tool|>" + tc.function().name();
                            }
                        }
                    }
                }
            }
        } else if (message.role() == Role.assistant && message.name() != null && !message.name().isEmpty()) {
            rtok += message.name();
        }

        boolean assistant = message.role() == Role.assistant;
        boolean hasCalls = message.toolCalls() != null && !message.toolCalls().isEmpty();
        List<ContentPart> parts = message.parts();
        String reasoning = "";
        if (assistant && parts.size() == 1 && parts.getFirst() instanceof TextPart t) {
            int end = t.text().indexOf(THINK_END);
            if (end >= 0) {
                String head = t.text().substring(0, end);
                reasoning = head.startsWith(THINK_BEGIN) ? head.substring(THINK_BEGIN.length()) : head;
                parts = List.of(new TextPart(t.text().substring(end + THINK_END.length())));
            }
        }
        if (assistant && !reasoning.isEmpty()) {
            sb.append(rtok).append("<|content_thinking|>").append(reasoning).append("<|end_message|>");
        }

        if (parts.size() == 1 && parts.getFirst() instanceof TextPart t) {
            String text = t.text();
            if (!text.isEmpty() || !(assistant && (hasCalls || !reasoning.isEmpty()))) {
                sb.append(rtok).append("<|content_text|>").append(text).append("<|end_message|>");
            }
        } else {
            for (ContentPart part : parts) {
                switch (part) {
                    case TextPart t -> sb.append(rtok).append("<|content_text|>").append(t.text())
                            .append("<|end_message|>");
                    case ImageUrlPart ignored -> {
                        if (message.role() != Role.user && message.role() != Role.tool) {
                            throw new IllegalArgumentException(
                                    "Image content is only supported for user and tool messages");
                        }
                        sb.append(rtok).append("<|content_image|><|unused_200054|><|end_message|>");
                    }
                    case AudioUrlPart ignored -> throw new IllegalArgumentException(
                            "Inkling 2 does not accept audio input");
                    default -> throw new IllegalArgumentException(
                            "Unsupported content part type: " + part.getClass().getSimpleName());
                }
            }
        }

        if (assistant && hasCalls) {
            for (ToolCall tc : message.toolCalls()) {
                String name = tc.function().name();
                if (message.name() != null && !message.name().isEmpty() && !message.name().equals(name)) {
                    throw new IllegalArgumentException(
                            "assistant name " + message.name() + " does not match its tool call " + name);
                }
                Map<String, Object> args = parseArguments(tc.function().arguments());
                sb.append("<|message_model|>").append(name).append("<|content_invoke_tool_json|>")
                        .append("{\"name\":").append(PyJson.quoted(name)).append(",\"args\":{");
                List<String> keys = new ArrayList<>(args.keySet());
                keys.sort(PyJson::compareCodePoints);
                boolean first = true;
                for (String key : keys) {
                    if (!first) {
                        sb.append(',');
                    }
                    first = false;
                    sb.append(PyJson.quoted(key)).append(':').append(PyJson.dumpsCompact(args.get(key), false));
                }
                sb.append("}}<|end_message|>");
            }
        }
        if (assistant) {
            sb.append("<|content_model_end_sampling|>");
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> parseArguments(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        Object value;
        try {
            value = MAPPER.readValue(json, Object.class);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("tool call arguments must be a JSON object: " + json, e);
        }
        if (value == null) {
            return Map.of();
        }
        if (!(value instanceof Map)) {
            throw new IllegalArgumentException("tool call arguments must be an object");
        }
        return (Map<String, Object>) value;
    }
}
