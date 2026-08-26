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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import smile.llm.ChatOptions;
import smile.llm.FunctionDefinition;
import smile.llm.Message;
import smile.llm.ToolChoice;
import smile.llm.ToolDefinition;

/**
 * JSON body for a {@code POST /chat/completions} request.
 * Field names are mapped to/from snake_case by Jackson.
 *
 * @author Haifeng Li
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class CompletionRequest {
    /**
     * Model id to use. When {@code null}, blank, or omitted, the loaded chat
     * model is used. When set, must equal the loaded model id (Hugging Face
     * repo id or local directory name from {@code smile.chat.model}).
     */
    public String model;
    /** Optional ID of an existing conversation to append to ({@code conv_<id>}). */
    public String conversation;
    /** The ordered list of dialog messages. Must not be {@code null}. */
    @JsonDeserialize(contentUsing = MessageDeserializer.class)
    public Message[] messages;
    /**
     * Maximum number of new tokens to generate. Prefer
     * {@link #maxCompletionTokens} when both are set. When neither is set,
     * {@link #resolveMaxTokens(int, int)} uses remaining context
     * ({@code maxSeqLen - promptLen}). Explicit values are still capped so
     * {@code promptLen + max_tokens <= maxSeqLen}.
     */
    public Integer maxTokens;
    /**
     * OpenAI alias for {@link #maxTokens} ({@code max_completion_tokens}).
     * Takes precedence when non-null.
     */
    public Integer maxCompletionTokens;
    /** Sampling temperature; higher values → more random. Default: {@code 0.6}. */
    public double temperature = 0.6;
    /** Nucleus-sampling top-p threshold. Default: {@code 0.9}. */
    public double topP = 0.9;
    /** Whether to include log-probabilities in the response. Default: {@code false}. */
    public boolean logprobs = false;
    /** Random seed for reproducible generation ({@code 0} = non-deterministic). */
    public long seed = 0;
    /**
     * When {@code true}, respond with SSE token chunks.
     * When {@code false} or omitted (OpenAI default), respond with a single
     * OpenAI {@code chat.completion} JSON body.
     */
    public Boolean stream = Boolean.FALSE;
    /** OpenAI tools array; may be {@code null}. */
    public JsonNode tools;
    /**
     * OpenAI {@code tool_choice}: {@code "auto"}|{@code "none"}|{@code "required"}
     * or {@code {"type":"function","function":{"name":"…"}}}.
     */
    public JsonNode toolChoice;
    /** Whether parallel tool calls are allowed. Default: {@code true}. */
    public Boolean parallelToolCalls;

    /**
     * Returns {@code true} when the client set {@code max_completion_tokens}
     * or {@code max_tokens}.
     */
    public boolean hasExplicitMaxTokens() {
        return maxCompletionTokens != null || maxTokens != null;
    }

    /**
     * Resolves max new tokens to generate.
     *
     * <p>{@code max_completion_tokens} wins when set; otherwise {@code max_tokens};
     * otherwise remaining context {@code max(0, maxSeqLen - promptLen)}.
     * In all cases the result is capped so
     * {@code promptLen + result <= maxSeqLen}.
     *
     * @param maxSeqLen configured max model / context length (already resolved;
     *                  never {@code 0} after model load).
     * @param promptLen chat-templated prompt token count.
     * @return non-negative max new tokens ({@code 0} when the prompt already
     *         fills the context window).
     */
    public int resolveMaxTokens(int maxSeqLen, int promptLen) {
        int remaining = Math.max(0, maxSeqLen - promptLen);
        int requested;
        if (maxCompletionTokens != null) {
            requested = maxCompletionTokens;
        } else if (maxTokens != null) {
            requested = maxTokens;
        } else {
            requested = remaining;
        }
        if (requested < 0) {
            requested = 0;
        }
        return Math.min(requested, remaining);
    }

    /**
     * Whether this request should use SSE streaming.
     *
     * <p>{@code null} is treated as {@code false} (OpenAI default).
     *
     * @return {@code true} for SSE; {@code false} for a single JSON completion.
     */
    public boolean isStream() {
        return Boolean.TRUE.equals(stream);
    }

    /**
     * Converts OpenAI tool fields into internal {@link ChatOptions}.
     *
     * @return chat options (never {@code null}).
     */
    public ChatOptions toChatOptions() {
        ToolDefinition[] defs = parseTools(tools);
        ToolChoice choice = parseToolChoice(toolChoice);
        boolean parallel = parallelToolCalls == null || parallelToolCalls;
        if ((defs == null || defs.length == 0)
                && (choice instanceof ToolChoice.Required || choice instanceof ToolChoice.Named)) {
            throw new IllegalArgumentException("tool_choice requires a non-empty tools array");
        }
        return new ChatOptions(defs, choice, parallel);
    }

    static ToolDefinition[] parseTools(JsonNode toolsNode) {
        if (toolsNode == null || toolsNode.isNull() || !toolsNode.isArray() || toolsNode.isEmpty()) {
            return null;
        }
        List<ToolDefinition> list = new ArrayList<>();
        for (JsonNode tool : toolsNode) {
            String type = tool.path("type").asText("function");
            JsonNode fn = tool.path("function");
            String name = fn.path("name").asText(null);
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("tool.function.name required");
            }
            String description = fn.has("description") && !fn.get("description").isNull()
                    ? fn.get("description").asText() : null;
            Map<String, Object> parameters = null;
            if (fn.has("parameters") && !fn.get("parameters").isNull()) {
                parameters = jsonToMap(fn.get("parameters"));
            }
            list.add(new ToolDefinition(type, new FunctionDefinition(name, description, parameters)));
        }
        return list.toArray(ToolDefinition[]::new);
    }

    static ToolChoice parseToolChoice(JsonNode node) {
        if (node == null || node.isNull()) {
            return ToolChoice.AUTO;
        }
        if (node.isTextual()) {
            return switch (node.asText()) {
                case "none" -> ToolChoice.NONE;
                case "required" -> ToolChoice.REQUIRED;
                default -> ToolChoice.AUTO;
            };
        }
        if (node.isObject()) {
            String type = node.path("type").asText("");
            if ("function".equals(type)) {
                String name = node.path("function").path("name").asText(null);
                if (name == null || name.isBlank()) {
                    throw new IllegalArgumentException("tool_choice.function.name required");
                }
                return new ToolChoice.Named(name);
            }
        }
        return ToolChoice.AUTO;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> jsonToMap(JsonNode node) {
        if (node == null || !node.isObject()) {
            return Map.of();
        }
        Map<String, Object> map = new LinkedHashMap<>();
        Iterator<Map.Entry<String, JsonNode>> fields = node.properties().iterator();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> e = fields.next();
            map.put(e.getKey(), jsonToJava(e.getValue()));
        }
        return map;
    }

    private static Object jsonToJava(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isTextual()) {
            return node.asText();
        }
        if (node.isBoolean()) {
            return node.asBoolean();
        }
        if (node.isIntegralNumber()) {
            return node.asLong();
        }
        if (node.isFloatingPointNumber()) {
            return node.asDouble();
        }
        if (node.isArray()) {
            List<Object> list = new ArrayList<>();
            for (JsonNode child : node) {
                list.add(jsonToJava(child));
            }
            return list;
        }
        if (node.isObject()) {
            return jsonToMap(node);
        }
        return node.asText();
    }
}
