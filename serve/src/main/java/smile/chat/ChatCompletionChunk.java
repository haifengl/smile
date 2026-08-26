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

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import smile.llm.FinishReason;
import java.util.List;

/**
 * A single server-sent event chunk in a streaming OpenAI-compatible
 * chat completion response.
 *
 * <p>The SSE stream consists of a sequence of these chunks, followed by
 * a terminal {@code data: [DONE]} event. Intermediate chunks carry
 * content deltas with {@code finish_reason: null}; the final chunk has
 * an empty delta and a non-null {@code finish_reason}.
 *
 * @param id      a unique identifier for this completion request.
 * @param object  always {@code "chat.completion.chunk"}.
 * @param created the Unix timestamp (seconds) when the request was created.
 * @param model   the model that generated the completion.
 * @param choices the list of completion choices (one element for non-batch requests).
 *
 * @author Haifeng Li
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record ChatCompletionChunk(
        String id,
        String object,
        long created,
        String model,
        List<Choice> choices) {

    /**
     * A single completion choice within the chunk.
     *
     * @param index        zero-based index of this choice.
     * @param delta        the content delta for this chunk.
     * @param logprobs     log-probability information, or {@code null}.
     * @param finishReason {@code null} during generation; non-null on the final chunk.
     */
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Choice(
            int index,
            Delta delta,
            Object logprobs,
            FinishReason finishReason) {}

    /**
     * The incremental text / tool-call delta for this chunk.
     *
     * <p>Fields are omitted from JSON when {@code null}, matching the
     * OpenAI streaming format.
     *
     * @param role      {@code "assistant"} on the first chunk, {@code null} thereafter.
     * @param content   the generated text fragment, {@code null} on the final chunk.
     * @param toolCalls incremental tool-call deltas.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Delta(String role, String content, List<ToolCallDelta> toolCalls) {
        /**
         * Text-only delta (no tool calls).
         */
        public Delta(String role, String content) {
            this(role, content, null);
        }
    }
}
