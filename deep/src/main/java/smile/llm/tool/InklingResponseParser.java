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
package smile.llm.tool;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import smile.llm.FinishReason;
import smile.llm.FunctionCall;
import smile.llm.ToolCall;

/**
 * Splits a decoded Inkling-2 reply (special tokens kept) into reasoning, text and tool calls, following the
 * {@code response_template} of the checkpoint's {@code tokenizer_config.json}.
 *
 * <p>A reply is a series of messages. Each is an optional {@code <|message_model|>name} header, a content-type
 * token and a body that ends at {@code <|end_message|>}, {@code <|content_model_end_sampling|>} or the end of the
 * text (a reply cut by the token limit):
 * <ul>
 * <li>{@code <|content_thinking|>} bodies are concatenated into the reasoning;</li>
 * <li>{@code <|content_text|>}, {@code <|content_tool_error|>} and {@code <|content_invoke_tool_text|>} bodies
 *     are concatenated into the content;</li>
 * <li>{@code <|content_invoke_tool_json|>} bodies are {@code {"name":..., "args":{...}}} tool calls
 *     (the function name falls back to the header's).</li>
 * </ul>
 * Text with no message markers at all is returned as content.
 *
 * @author Haifeng Li
 */
public final class InklingResponseParser implements ToolCallParser {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Pattern MESSAGE = Pattern.compile(
            "(?:<\\|message_model\\|>([^<]*))?"
                    + "<\\|content_(thinking|text|tool_error|invoke_tool_text|invoke_tool_json)\\|>"
                    + "(.*?)(?:<\\|end_message\\|>|<\\|content_model_end_sampling\\|>|\\z)",
            Pattern.DOTALL);

    /**
     * A parsed reply.
     *
     * @param reasoning the concatenated reasoning, empty if none.
     * @param content   the concatenated text, empty if none.
     * @param toolCalls the tool calls in order.
     */
    public record Parsed(String reasoning, String content, List<ToolCall> toolCalls) { }

    /** Creates the parser. */
    public InklingResponseParser() { }

    /**
     * Splits a reply.
     *
     * @param text decoded reply including the special tokens.
     * @return reasoning, content and tool calls.
     */
    public Parsed split(String text) {
        StringBuilder reasoning = new StringBuilder();
        StringBuilder content = new StringBuilder();
        List<ToolCall> calls = new ArrayList<>();
        Matcher m = MESSAGE.matcher(text == null ? "" : text);
        boolean any = false;
        while (m.find()) {
            any = true;
            String header = m.group(1);
            String body = m.group(3);
            switch (m.group(2)) {
                case "thinking" -> reasoning.append(body);
                case "invoke_tool_json" -> {
                    ToolCall call = toolCall(header, body);
                    if (call == null) {
                        content.append(body);
                    } else {
                        calls.add(call);
                    }
                }
                default -> content.append(body);
            }
            if (m.end() == m.start()) {
                break;
            }
        }
        if (!any && text != null) {
            content.append(text);
        }
        return new Parsed(reasoning.toString(), content.toString(), calls);
    }

    private static ToolCall toolCall(String header, String body) {
        try {
            JsonNode json = MAPPER.readTree(body);
            String name = json.has("name") ? json.get("name").asString() : header;
            if (name == null || name.isEmpty()) {
                return null;
            }
            JsonNode args = json.has("args") ? json.get("args") : json.get("arguments");
            String arguments = args == null || args.isNull() ? "{}" : MAPPER.writeValueAsString(args);
            return ToolCall.function("call_" + UUID.randomUUID().toString().replace("-", "").substring(0, 24),
                    new FunctionCall(name, arguments));
        } catch (RuntimeException e) {
            return null;
        }
    }

    @Override
    public ParseResult parse(String assistantText) {
        Parsed parsed = split(assistantText);
        String content = parsed.content();
        if (parsed.toolCalls().isEmpty()) {
            return new ParseResult(content, List.of(), FinishReason.stop);
        }
        return new ParseResult(content.isEmpty() ? null : content, parsed.toolCalls(), FinishReason.tool_calls);
    }
}
