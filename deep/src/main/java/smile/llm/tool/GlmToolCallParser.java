/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * Smile is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Smile is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Smile.  If not, see <https://www.gnu.org/licenses/>.
 */
package smile.llm.tool;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import smile.llm.FinishReason;
import smile.llm.FunctionCall;
import smile.llm.ToolCall;
import smile.llm.ToolDefinition;
import tools.jackson.databind.ObjectMapper;

/**
 * Parses GLM-5.x tool calls: {@code <tool_call>name<arg_key>k</arg_key><arg_value>v</arg_value>...</tool_call>}.
 *
 * <p>The chat template renders string arguments as they are and everything else as JSON, so a value is read back
 * with the parameter's schema type when the tool is known: {@code string} stays text, other types are parsed as JSON
 * (falling back to the text). Without a schema a value that parses as a JSON object, array, number, boolean or null
 * is taken as such and anything else is text. Reasoning ({@code <think>..</think>}) before the calls is dropped;
 * an unterminated call (token limit) is still parsed.
 *
 * @author Haifeng Li
 */
public final class GlmToolCallParser implements ToolCallParser {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Pattern CALL = Pattern.compile("<tool_call>(.*?)(?:</tool_call>|$)", Pattern.DOTALL);
    private static final Pattern ARG = Pattern.compile(
            "<arg_key>(.*?)</arg_key>\\s*<arg_value>(.*?)(?:</arg_value>|$)", Pattern.DOTALL);
    private static final Pattern THINK_BLOCK = Pattern.compile("<think>.*?</think>", Pattern.DOTALL);

    private final Map<String, Map<String, Object>> schemas = new LinkedHashMap<>();

    /** Parser without tool schemas (values are typed by their own syntax). */
    public GlmToolCallParser() {
        this(null);
    }

    /**
     * Parser that types argument values with the tools' parameter schemas.
     *
     * @param tools the tools offered to the model; may be {@code null}.
     */
    public GlmToolCallParser(ToolDefinition[] tools) {
        if (tools != null) {
            for (ToolDefinition tool : tools) {
                schemas.put(tool.function().name(),
                        tool.function().parameters() == null ? Map.of() : tool.function().parameters());
            }
        }
    }

    @Override
    public ParseResult parse(String assistantText) {
        if (assistantText == null) {
            return new ParseResult("", List.of(), FinishReason.stop);
        }
        String text = stripThinking(assistantText);
        List<ToolCall> calls = new ArrayList<>();
        Matcher m = CALL.matcher(text);
        int first = -1;
        while (m.find()) {
            if (first < 0) {
                first = m.start();
            }
            String body = m.group(1);
            int argAt = body.indexOf("<arg_key>");
            String name = (argAt < 0 ? body : body.substring(0, argAt)).strip();
            if (name.isEmpty()) {
                continue;
            }
            Map<String, Object> schema = schemas.get(name);
            Map<String, Object> args = new LinkedHashMap<>();
            Matcher a = ARG.matcher(body);
            while (a.find()) {
                String key = a.group(1).strip();
                args.put(key, value(a.group(2), propertySchema(schema, key)));
            }
            String json;
            try {
                json = MAPPER.writeValueAsString(args);
            } catch (RuntimeException e) {
                json = "{}";
            }
            calls.add(ToolCall.function("call_" + UUID.randomUUID().toString().replace("-", "").substring(0, 24),
                    new FunctionCall(name, json)));
        }
        if (calls.isEmpty()) {
            return new ParseResult(text, List.of(), FinishReason.stop);
        }
        String prefix = text.substring(0, first).strip();
        return new ParseResult(prefix.isEmpty() ? null : prefix, calls, FinishReason.tool_calls);
    }

    private static String stripThinking(String text) {
        int close = text.lastIndexOf("</think>");
        if (close >= 0) {
            return text.substring(close + "</think>".length()).strip();
        }
        return THINK_BLOCK.matcher(text).replaceAll("").strip();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> propertySchema(Map<String, Object> schema, String name) {
        if (schema == null || !(schema.get("properties") instanceof Map<?, ?> props)
                || !(props.get(name) instanceof Map<?, ?> p)) {
            return null;
        }
        return (Map<String, Object>) p;
    }

    private static String type(Map<String, Object> schema) {
        if (schema == null) {
            return null;
        }
        Object t = schema.get("type");
        if (t instanceof List<?> list) {
            for (Object o : list) {
                if (!"null".equals(o)) {
                    return String.valueOf(o);
                }
            }
            return null;
        }
        return t == null ? null : t.toString();
    }

    private static Object value(String raw, Map<String, Object> schema) {
        String type = type(schema);
        if ("string".equals(type)) {
            return raw;
        }
        String s = raw.strip();
        try {
            Object parsed = MAPPER.readValue(s, Object.class);
            // without a schema a bare JSON string literal is just text; with one of another type it is not expected
            return parsed;
        } catch (Exception e) {
            return raw;
        }
    }
}
