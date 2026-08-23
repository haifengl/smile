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

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import smile.llm.Message;

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
}
