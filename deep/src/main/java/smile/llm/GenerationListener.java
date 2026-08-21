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

/**
 * Callback for autoregressive generation progress on a batch of prompts.
 *
 * <p>Every method takes {@code promptIndex} first: the index of the prompt in
 * the batch passed to {@link LanguageModel#generate} /
 * {@link LanguageModel#chat} ({@code 0 .. batchSize-1}).
 *
 * <p>Serve and other clients implement this (or compose helpers) instead of
 * passing a {@link java.util.concurrent.SubmissionPublisher} into the model.
 * A streaming adapter can forward {@link #onText} to a publisher; a metrics
 * sink can override only the counters it cares about.
 *
 * <p>Typical order for one prompt in the batch:
 * <ol>
 *   <li>{@link #onInputTokens} / {@link #onCachedInputTokens} once after prompt bind</li>
 *   <li>repeated {@link #onGeneratedTokens} (and optional {@link #onText})</li>
 *   <li>{@link #onThinkingTokens} when the engine can attribute “thinking”
 *       tokens (not emitted yet)</li>
 * </ol>
 *
 * @author Haifeng Li
 */
public interface GenerationListener {
    /**
     * Reports the number of prompt / input tokens for one sequence in the batch.
     *
     * @param promptIndex index of the prompt in the batch ({@code 0 .. batchSize-1}).
     * @param count       prompt token count for that sequence; {@code >= 0}.
     */
    default void onInputTokens(int promptIndex, int count) {}

    /**
     * Reports how many of that sequence's input tokens were served from the KV
     * prefix cache (radix hit), so they need not be recomputed in prefill.
     *
     * <p>Always {@code <=} the value passed to {@link #onInputTokens} for the
     * same {@code promptIndex}. When prefix reuse is disabled or there is no
     * match, {@code count} is {@code 0}.
     *
     * @param promptIndex index of the prompt in the batch ({@code 0 .. batchSize-1}).
     * @param count       cached input token count; {@code >= 0}.
     */
    default void onCachedInputTokens(int promptIndex, int count) {}

    /**
     * Reports newly generated completion tokens (not prompt tokens) for one
     * sequence in the batch.
     *
     * @param promptIndex index of the prompt in the batch ({@code 0 .. batchSize-1}).
     * @param count       number of tokens produced since the previous call for
     *                    this index; typically {@code 1}.
     */
    default void onGeneratedTokens(int promptIndex, int count) {}

    /**
     * Reports tokens attributed to a model “thinking” / reasoning span
     * (e.g. Qwen thinking mode), distinct from final answer tokens.
     *
     * <p><b>Reserved:</b> the engine does not emit this yet. Implementations
     * may override it for forward compatibility; the default is a no-op.
     *
     * @param promptIndex index of the prompt in the batch ({@code 0 .. batchSize-1}).
     * @param count       thinking tokens since the previous call; {@code >= 0}.
     */
    default void onThinkingTokens(int promptIndex, int count) {}

    /**
     * Reports a decoded UTF-8 text chunk suitable for streaming to a client.
     *
     * <p>May be called less often than {@link #onGeneratedTokens} (implementations
     * commonly coalesce ~20 tokens, or flush on EOS). Empty strings are not
     * delivered. Only emitted when {@code batchSize == 1} (so {@code promptIndex}
     * is {@code 0}).
     *
     * @param promptIndex index of the prompt in the batch ({@code 0 .. batchSize-1}).
     * @param chunk       decoded text (special tokens already skipped when applicable).
     */
    default void onText(int promptIndex, String chunk) {}
}
