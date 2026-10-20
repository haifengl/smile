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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import tools.jackson.databind.ObjectMapper;
import smile.llm.FinishReason;
import smile.llm.FunctionCall;
import smile.llm.ToolCall;
import smile.llm.ToolDefinition;

/**
 * Parses MiniMax-M3 tool calls:
 *
 * <pre>
 * ]&lt;]minimax[&gt;[&lt;tool_call&gt;
 * ]&lt;]minimax[&gt;[&lt;invoke name="get_weather"&gt;]&lt;]minimax[&gt;[&lt;city&gt;Paris]&lt;]minimax[&gt;[&lt;/city&gt;]&lt;]minimax[&gt;[&lt;/invoke&gt;
 * ]&lt;]minimax[&gt;[&lt;/tool_call&gt;
 * </pre>
 *
 * <p>Every tag carries the {@code ]<]minimax[>[} prefix. Parameter values are text; objects are nested
 * elements and arrays are repeated {@code <item>} elements. When the tool definitions are known, a value is
 * typed from its JSON schema ({@code integer}, {@code number}, {@code boolean}, {@code array}, {@code object},
 * {@code string}); without a schema, JSON literals (numbers, booleans, {@code null}, objects, arrays) are
 * decoded and everything else stays a string. Truncated output (token limit) is tolerated: open elements
 * are closed at the end of the text.
 *
 * @author Haifeng Li
 */
public final class MiniMaxToolCallParser implements ToolCallParser {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    /** Namespace prefix shared by all tool-call tags. */
    public static final String NS = "]<]minimax[>[";
    private static final String BEGIN = NS + "<tool_call>";
    private static final Pattern TAG = Pattern.compile(
            Pattern.quote(NS) + "<(/?)([^<>\\s]+?)(?:\\s+name=\"([^\"]*)\")?\\s*>");
    private static final Pattern THINK_BLOCK = Pattern.compile("<think>.*?</think>", Pattern.DOTALL);

    private final Map<String, Map<String, Object>> schemas = new LinkedHashMap<>();

    /** Parser without tool schemas: values are typed heuristically. */
    public MiniMaxToolCallParser() {
        this(null);
    }

    /**
     * Parser that types arguments from the tools' JSON schemas.
     *
     * @param tools tool definitions of the request; may be {@code null}.
     */
    public MiniMaxToolCallParser(ToolDefinition[] tools) {
        if (tools != null) {
            for (ToolDefinition tool : tools) {
                schemas.put(tool.function().name(),
                        tool.function().parameters() == null ? Map.of() : tool.function().parameters());
            }
        }
    }

    /** One parsed element: its tag name, the text directly inside it, and its child elements. */
    private static final class Node {
        final String name;
        final String attribute;
        final StringBuilder text = new StringBuilder();
        final List<Node> children = new ArrayList<>();

        Node(String name, String attribute) {
            this.name = name;
            this.attribute = attribute;
        }
    }

    @Override
    public ParseResult parse(String assistantText) {
        if (assistantText == null) {
            return new ParseResult("", List.of(), FinishReason.stop);
        }
        String text = stripThinking(assistantText);
        int start = text.indexOf(BEGIN);
        if (start < 0) {
            return new ParseResult(text, List.of(), FinishReason.stop);
        }
        String prefix = text.substring(0, start).strip();
        List<ToolCall> calls = new ArrayList<>();
        for (Node invoke : parseInvokes(text.substring(start))) {
            Map<String, Object> schema = schemas.get(invoke.attribute);
            Map<String, Object> args = new LinkedHashMap<>();
            for (Node param : invoke.children) {
                args.put(param.name, value(param, propertySchema(schema, param.name)));
            }
            String json;
            try {
                json = MAPPER.writeValueAsString(args);
            } catch (RuntimeException e) {
                json = "{}";
            }
            calls.add(ToolCall.function("call_" + UUID.randomUUID().toString().replace("-", "").substring(0, 24),
                    new FunctionCall(invoke.attribute, json)));
        }
        if (calls.isEmpty()) {
            return new ParseResult(text, List.of(), FinishReason.stop);
        }
        return new ParseResult(prefix.isEmpty() ? null : prefix, calls, FinishReason.tool_calls);
    }

    private static String stripThinking(String text) {
        int close = text.lastIndexOf("</think>");
        if (close >= 0) {
            return text.substring(close + "</think>".length()).strip();
        }
        return THINK_BLOCK.matcher(text).replaceAll("").strip();
    }

    /** Builds the element tree below the tool-call block and returns its {@code invoke} elements. */
    private static List<Node> parseInvokes(String block) {
        List<Node> invokes = new ArrayList<>();
        java.util.ArrayDeque<Node> stack = new java.util.ArrayDeque<>();
        Matcher m = TAG.matcher(block);
        int last = 0;
        while (m.find()) {
            if (!stack.isEmpty()) {
                stack.peek().text.append(block, last, m.start());
            }
            last = m.end();
            boolean closing = !m.group(1).isEmpty();
            String name = m.group(2);
            if (name.equals("tool_call")) {
                continue;
            }
            if (closing) {
                if (!stack.isEmpty()) {
                    stack.pop();
                }
                continue;
            }
            Node node = new Node(name, m.group(3));
            if (name.equals("invoke")) {
                if (node.attribute == null || node.attribute.isBlank()) {
                    continue;
                }
                stack.clear();
                invokes.add(node);
            } else if (stack.isEmpty()) {
                continue;
            } else {
                stack.peek().children.add(node);
            }
            stack.push(node);
        }
        if (!stack.isEmpty()) {
            stack.peek().text.append(block, last, block.length());   // truncated generation
        }
        return invokes;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> propertySchema(Map<String, Object> schema, String name) {
        if (schema == null || !(schema.get("properties") instanceof Map<?, ?> props)
                || !(props.get(name) instanceof Map<?, ?> p)) {
            return null;
        }
        return (Map<String, Object>) p;
    }

    /** JSON schema type of a property, or {@code null} when unknown ("null" in a union is ignored). */
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

    @SuppressWarnings("unchecked")
    private Object value(Node node, Map<String, Object> schema) {
        if (!node.children.isEmpty()) {
            boolean items = node.children.stream().allMatch(c -> c.name.equals("item"));
            if (items && !"object".equals(type(schema))) {
                List<Object> list = new ArrayList<>();
                Map<String, Object> itemSchema = schema != null && schema.get("items") instanceof Map<?, ?> i
                        ? (Map<String, Object>) i : null;
                for (Node child : node.children) {
                    list.add(value(child, itemSchema));
                }
                return list;
            }
            Map<String, Object> object = new LinkedHashMap<>();
            for (Node child : node.children) {
                object.put(child.name, value(child, propertySchema(schema, child.name)));
            }
            return object;
        }
        String raw = node.text.toString();
        String type = type(schema);
        if (type == null) {
            return schema == null && !schemas.isEmpty() ? raw : decodeLiteral(raw);
        }
        return switch (type) {
            case "string" -> raw;
            case "integer" -> parseOr(raw, () -> Long.parseLong(raw.strip()));
            case "number" -> parseOr(raw, () -> {
                String s = raw.strip();
                double d = Double.parseDouble(s);
                return d == Math.rint(d) && !s.contains(".") && !s.contains("e") && !s.contains("E")
                        ? (Object) Long.parseLong(s) : (Object) d;
            });
            case "boolean" -> switch (raw.strip().toLowerCase(java.util.Locale.ROOT)) {
                case "true" -> Boolean.TRUE;
                case "false" -> Boolean.FALSE;
                default -> raw;
            };
            case "array", "object" -> parseOr(raw, () -> MAPPER.readValue(raw.strip(), Object.class));
            default -> decodeLiteral(raw);
        };
    }

    private interface Parser {
        Object get() throws Exception;
    }

    private static Object parseOr(String fallback, Parser parser) {
        try {
            return parser.get();
        } catch (Exception e) {
            return fallback;
        }
    }

    /** Without a schema: decode JSON literals, keep everything else as text. */
    private static Object decodeLiteral(String raw) {
        String value = raw.strip();
        if (value.isEmpty()) {
            return raw;
        }
        char c = value.charAt(0);
        if (c == '{' || c == '[' || c == 't' || c == 'f' || c == 'n' || c == '-' || Character.isDigit(c)) {
            try {
                return MAPPER.readValue(value, Object.class);
            } catch (Exception ignored) {
                // fall through
            }
        }
        return raw;
    }
}
