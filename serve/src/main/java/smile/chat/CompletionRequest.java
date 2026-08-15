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
    public Message[] messages;
    /**
     * Maximum number of new tokens to generate. Prefer
     * {@link #maxCompletionTokens} when both are set. Default when neither is
     * set: {@code 2048}.
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
     * Resolves the generation length limit.
     *
     * <p>{@code max_completion_tokens} wins when set; otherwise {@code max_tokens};
     * otherwise {@code 2048}.
     *
     * @return positive max new tokens.
     */
    public int resolveMaxTokens() {
        if (maxCompletionTokens != null) {
            return maxCompletionTokens;
        }
        if (maxTokens != null) {
            return maxTokens;
        }
        return 2048;
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
