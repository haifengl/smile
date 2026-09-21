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
 * Parses OpenAI-style / OGA JSON tool-call blocks:
 *
 * <pre>
 * &lt;tool_call&gt;
 * {"name":"get_weather","arguments":{"city":"SF"}}
 * &lt;/tool_call&gt;
 * </pre>
 *
 * <p>Also accepts a bare JSON object or array of {@code name}/{@code arguments}
 * (or {@code function.name} / {@code function.arguments}) when no XML wrapper
 * is present.
 *
 * @author Haifeng Li
 */
public final class JsonToolCallParser implements ToolCallParser {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Pattern TOOL_CALL_BLOCK = Pattern.compile(
            "<tool_call>\\s*(.*?)\\s*</tool_call>", Pattern.DOTALL);
    private static final Pattern THINK_BLOCK = Pattern.compile(
            "<think>.*?</think>", Pattern.DOTALL);

    /** Creates a JSON tool-call parser. */
    public JsonToolCallParser() {}

    @Override
    public ParseResult parse(String assistantText) {
        if (assistantText == null) {
            return new ParseResult("", List.of(), FinishReason.stop);
        }
        String text = THINK_BLOCK.matcher(assistantText).replaceAll("").strip();
        List<ToolCall> calls = new ArrayList<>();
        String prefix = text;

        Matcher blockMatcher = TOOL_CALL_BLOCK.matcher(text);
        if (blockMatcher.find()) {
            int firstOpen = text.indexOf("<tool_call>");
            prefix = text.substring(0, firstOpen).strip();
            blockMatcher.reset();
            while (blockMatcher.find()) {
                parseJsonPayload(blockMatcher.group(1), calls);
            }
        } else {
            String stripped = text.strip();
            if (stripped.startsWith("{") || stripped.startsWith("[")) {
                int before = calls.size();
                parseJsonPayload(stripped, calls);
                if (calls.size() > before) {
                    prefix = "";
                }
            }
        }

        if (calls.isEmpty()) {
            return new ParseResult(text, List.of(), FinishReason.stop);
        }
        if (prefix != null && prefix.isEmpty()) {
            prefix = null;
        }
        return new ParseResult(prefix == null ? "" : prefix, calls, FinishReason.tool_calls);
    }

    private static void parseJsonPayload(String raw, List<ToolCall> calls) {
        if (raw == null || raw.isBlank()) {
            return;
        }
        try {
            JsonNode node = MAPPER.readTree(raw.strip());
            if (node.isArray()) {
                for (JsonNode child : node) {
                    addCall(child, calls);
                }
            } else {
                addCall(node, calls);
            }
        } catch (Exception ignored) {
            // Not JSON — leave for other parsers.
        }
    }

    private static void addCall(JsonNode node, List<ToolCall> calls) {
        if (node == null || !node.isObject()) {
            return;
        }
        String name = text(node, "name");
        String args = null;
        if (node.has("arguments")) {
            JsonNode a = node.get("arguments");
            args = a.isString() ? a.asString() : a.toString();
        } else if (node.has("parameters")) {
            JsonNode a = node.get("parameters");
            args = a.isString() ? a.asString() : a.toString();
        }
        if ((name == null || name.isBlank()) && node.has("function")) {
            JsonNode fn = node.get("function");
            name = text(fn, "name");
            if (fn.has("arguments")) {
                JsonNode a = fn.get("arguments");
                args = a.isString() ? a.asString() : a.toString();
            }
        }
        if (name == null || name.isBlank()) {
            return;
        }
        if (args == null || args.isBlank()) {
            args = "{}";
        }
        String id = text(node, "id");
        if (id == null || id.isBlank()) {
            id = "call_" + UUID.randomUUID().toString().replace("-", "");
        }
        calls.add(ToolCall.function(id, new FunctionCall(name, args)));
    }

    private static String text(JsonNode node, String field) {
        if (node == null || !node.has(field) || node.get(field).isNull()) {
            return null;
        }
        return node.get(field).asString();
    }
}
