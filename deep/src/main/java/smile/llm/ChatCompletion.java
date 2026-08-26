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
 * Chat completion.
 *
 * @param model             the model used for the chat completion.
 * @param content           the generated text completion; may be {@code null}
 *                          when {@code toolCalls} is non-empty (OpenAI behavior).
 * @param promptTokens      the list of prompt tokens.
 * @param completionTokens  the list of generated tokens.
 * @param reason            the finish reason.
 * @param logprobs          the optional list of log probabilities of generated tokens.
 * @param toolCalls         parsed tool calls; {@code null} or empty when absent.
 *
 * @author Haifeng Li
 */
public record ChatCompletion(
        String model,
        String content,
        int[] promptTokens,
        int[] completionTokens,
        FinishReason reason,
        float[] logprobs,
        List<ToolCall> toolCalls) {

    /**
     * Compact canonical constructor that validates required fields.
     */
    public ChatCompletion {
        if (model == null) {
            throw new IllegalArgumentException("model must not be null");
        }
        if (reason == null) {
            throw new IllegalArgumentException("reason must not be null");
        }
        boolean hasToolCalls = toolCalls != null && !toolCalls.isEmpty();
        if (content == null && !hasToolCalls) {
            throw new IllegalArgumentException("content must not be null unless tool_calls present");
        }
        if (toolCalls != null) {
            toolCalls = List.copyOf(toolCalls);
        }
    }

    /**
     * Backward-compatible constructor without tool calls.
     */
    public ChatCompletion(String model, String content, int[] promptTokens,
                          int[] completionTokens, FinishReason reason, float[] logprobs) {
        this(model, content, promptTokens, completionTokens, reason, logprobs, null);
    }

    /**
     * @return {@code true} when this completion includes tool calls.
     */
    public boolean hasToolCalls() {
        return toolCalls != null && !toolCalls.isEmpty();
    }
}
