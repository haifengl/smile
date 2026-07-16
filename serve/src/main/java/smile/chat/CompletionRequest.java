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
    /** Optional ID of an existing conversation to append to. */
    public Long conversation;
    /** The ordered list of dialog messages. Must not be {@code null}. */
    public Message[] messages;
    /** Maximum number of new tokens to generate. Default: {@code 2048}. */
    public int maxTokens = 2048;
    /** Sampling temperature; higher values → more random. Default: {@code 0.6}. */
    public double temperature = 0.6;
    /** Nucleus-sampling top-p threshold. Default: {@code 0.9}. */
    public double topP = 0.9;
    /** Whether to include log-probabilities in the response. Default: {@code false}. */
    public boolean logprobs = false;
    /** Random seed for reproducible generation ({@code 0} = non-deterministic). */
    public long seed = 0;
    /**
     * Whether the client expects a streaming (SSE) response.
     * Currently always streamed; this field is reserved for future non-streaming support.
     */
    public boolean stream = true;
}
