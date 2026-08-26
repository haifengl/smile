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

import java.util.List;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import smile.llm.ChatCompletion;
import smile.llm.FinishReason;

/**
 * OpenAI-compatible non-streaming chat completion response
 * ({@code object: "chat.completion"}).
 *
 * @param id      unique completion id ({@code chatcmpl-...}).
 * @param object  always {@code "chat.completion"}.
 * @param created Unix epoch seconds.
 * @param model   public model id.
 * @param choices completion choices (typically one).
 * @param usage   token usage totals.
 *
 * @author Haifeng Li
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@JsonInclude(JsonInclude.Include.ALWAYS)
public record ChatCompletionObject(
        String id,
        @JsonProperty("object") String object,
        long created,
        String model,
        List<Choice> choices,
        Usage usage) {

    /**
     * Builds a completion object from a model result.
     *
     * @param id         completion id.
     * @param created    creation timestamp (Unix seconds).
     * @param modelName  public model id.
     * @param completion generated completion; may be {@code null}.
     * @return the OpenAI-shaped response.
     */
    public static ChatCompletionObject of(String id, long created, String modelName,
                                          ChatCompletion completion) {
        FinishReason reason = completion != null ? completion.reason() : FinishReason.stop;
        int promptTokens = completion != null && completion.promptTokens() != null
                ? completion.promptTokens().length : 0;
        int completionTokens = completion != null && completion.completionTokens() != null
                ? completion.completionTokens().length : 0;

        Choice choice = new Choice(
                0,
                ChatMessageObject.of(completion),
                null,
                reason);
        Usage usage = new Usage(promptTokens, completionTokens, promptTokens + completionTokens);
        return new ChatCompletionObject(id, "chat.completion", created, modelName, List.of(choice), usage);
    }

    /**
     * Builds a completion object from model results (first element is used).
     *
     * @param id          completion id.
     * @param created     creation timestamp (Unix seconds).
     * @param modelName   public model id.
     * @param completions generated completions; first is used for the choice.
     * @return the OpenAI-shaped response.
     */
    public static ChatCompletionObject of(String id, long created, String modelName,
                                          ChatCompletion[] completions) {
        ChatCompletion completion = (completions != null && completions.length > 0)
                ? completions[0]
                : null;
        return of(id, created, modelName, completion);
    }

    /**
     * A single completion choice.
     *
     * @param index        zero-based index.
     * @param message      assistant message (OpenAI wire shape).
     * @param logprobs     log-probability payload, or {@code null}.
     * @param finishReason why generation stopped.
     */
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Choice(
            int index,
            ChatMessageObject message,
            Object logprobs,
            FinishReason finishReason) {}

    /**
     * Token usage counters.
     *
     * @param promptTokens     prompt token count.
     * @param completionTokens generated token count.
     * @param totalTokens      sum of prompt and completion tokens.
     */
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Usage(
            int promptTokens,
            int completionTokens,
            int totalTokens) {}
}
