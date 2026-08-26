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

/**
 * Parses Qwen3 / Qwen3.5 / Qwen3.8 XML tool-call blocks:
 *
 * <pre>
 * &lt;tool_call&gt;
 * &lt;function=name&gt;
 * &lt;parameter=key&gt;
 * value
 * &lt;/parameter&gt;
 * &lt;/function&gt;
 * &lt;/tool_call&gt;
 * </pre>
 *
 * @author Haifeng Li
 */
public final class Qwen3XmlToolCallParser implements ToolCallParser {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Pattern TOOL_CALL_BLOCK = Pattern.compile(
            "<tool_call>\\s*(.*?)\\s*</tool_call>", Pattern.DOTALL);
    private static final Pattern FUNCTION_NAME = Pattern.compile(
            "<function=([^>\\s]+)\\s*>", Pattern.DOTALL);
    private static final Pattern PARAMETER = Pattern.compile(
            "<parameter=([^>\\s]+)\\s*>\\s*(.*?)\\s*</parameter>", Pattern.DOTALL);
    private static final Pattern THINK_BLOCK = Pattern.compile(
            "<think>.*?</think>", Pattern.DOTALL);

    @Override
    public ParseResult parse(String assistantText) {
        if (assistantText == null) {
            return new ParseResult("", List.of(), FinishReason.stop);
        }
        String text = stripThinking(assistantText);
        if (!text.contains("<tool_call>")) {
            return new ParseResult(text, List.of(), FinishReason.stop);
        }

        int firstOpen = text.indexOf("<tool_call>");
        String prefix = text.substring(0, firstOpen).strip();
        if (prefix.isEmpty()) {
            prefix = null;
        }

        List<ToolCall> calls = new ArrayList<>();
        Matcher blockMatcher = TOOL_CALL_BLOCK.matcher(text);
        while (blockMatcher.find()) {
            String body = blockMatcher.group(1);
            Matcher nameMatcher = FUNCTION_NAME.matcher(body);
            if (!nameMatcher.find()) {
                continue;
            }
            String name = nameMatcher.group(1).strip();
            Map<String, Object> args = new LinkedHashMap<>();
            Matcher paramMatcher = PARAMETER.matcher(body);
            while (paramMatcher.find()) {
                String key = paramMatcher.group(1).strip();
                String raw = paramMatcher.group(2);
                args.put(key, decodeValue(raw));
            }
            String argumentsJson;
            try {
                argumentsJson = MAPPER.writeValueAsString(args);
            } catch (Exception e) {
                argumentsJson = "{}";
            }
            calls.add(ToolCall.function(newCallId(), new FunctionCall(name, argumentsJson)));
        }

        if (calls.isEmpty()) {
            // Marker present but unparsable — treat as plain text.
            return new ParseResult(text, List.of(), FinishReason.stop);
        }
        return new ParseResult(prefix, calls, FinishReason.tool_calls);
    }

    private static String stripThinking(String text) {
        // Keep text after </think> when present; otherwise strip complete think blocks.
        int close = text.lastIndexOf("</think>");
        if (close >= 0) {
            return text.substring(close + "</think>".length()).strip();
        }
        return THINK_BLOCK.matcher(text).replaceAll("").strip();
    }

    private static Object decodeValue(String raw) {
        if (raw == null) {
            return "";
        }
        String value = raw.strip();
        if (value.isEmpty()) {
            return "";
        }
        char c = value.charAt(0);
        if (c == '{' || c == '[' || c == '"' || c == 't' || c == 'f' || c == 'n'
                || c == '-' || Character.isDigit(c)) {
            try {
                return MAPPER.readValue(value, Object.class);
            } catch (Exception ignored) {
                // fall through to string
            }
        }
        return value;
    }

    private static String newCallId() {
        return "call_" + UUID.randomUUID().toString().replace("-", "").substring(0, 24);
    }
}
