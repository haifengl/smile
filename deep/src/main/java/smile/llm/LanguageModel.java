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

import java.util.concurrent.SubmissionPublisher;

/**
 * Common façade for chat-capable decoder LLMs (Llama, Qwen, …).
 *
 * <p>Implementations load a checkpoint, expose chat-template encoding, and
 * run autoregressive generation. Serve and other clients should prefer this
 * interface over concrete model types when only inference is required.
 *
 * @author Haifeng Li
 */
public interface LanguageModel {
    /**
     * Returns the architecture family label.
     *
     * <p>Examples: {@code meta/llama3}, {@code alibaba/qwen3.5}. This is not
     * necessarily the public API model id used by serve.
     *
     * @return the family label string.
     */
    String family();

    /**
     * Returns the model instance / checkpoint name.
     *
     * @return the instance name (e.g. directory or HF repo leaf name).
     */
    String name();

    /**
     * Returns the configured maximum sequence length
     * ({@code max-model-len} / context length).
     *
     * @return the maximum number of tokens allowed for prompt plus completion.
     */
    int maxSeqLen();

    /**
     * Encodes a dialog with the model chat template, leaving the assistant
     * turn open for completion.
     *
     * @param dialog ordered conversation turns ({@code system} / {@code user} /
     *               {@code assistant}).
     * @return prompt token ids ready for {@link #generate}.
     */
    int[] encodeChat(Message... dialog);

    /**
     * Generates completions from already-tokenized prompts.
     *
     * @param prompts   batch of prompt token id sequences; batch size is
     *                  typically {@code 1} for serve.
     * @param maxGenLen maximum number of <em>new</em> tokens to generate per
     *                  prompt (not including the prompt itself).
     * @param temperature sampling temperature; higher values increase randomness.
     * @param topp      nucleus-sampling top-p threshold in {@code (0, 1]}.
     * @param logprobs  {@code true} to include per-token log-probabilities in
     *                  the result.
     * @param seed      optional RNG seed for deterministic sampling;
     *                  {@code 0} means non-deterministic.
     * @param publisher optional flow publisher that receives streamed text
     *                  chunks; may be {@code null} for non-streaming calls.
     *                  When non-null, batch size must be {@code 1}.
     * @return one {@link ChatCompletion} per prompt in the batch.
     */
    default ChatCompletion[] generate(int[][] prompts, int maxGenLen, double temperature,
                                      double topp, boolean logprobs, long seed,
                                      SubmissionPublisher<String> publisher) {
        return generate(prompts, maxGenLen, temperature, topp, logprobs, seed, publisher, null);
    }

    /**
     * Generates completions from already-tokenized prompts.
     *
     * @param prompts   batch of prompt token id sequences; batch size is
     *                  typically {@code 1} for serve.
     * @param maxGenLen maximum number of <em>new</em> tokens to generate per
     *                  prompt (not including the prompt itself).
     * @param temperature sampling temperature; higher values increase randomness.
     * @param topp      nucleus-sampling top-p threshold in {@code (0, 1]}.
     * @param logprobs  {@code true} to include per-token log-probabilities in
     *                  the result.
     * @param seed      optional RNG seed for deterministic sampling;
     *                  {@code 0} means non-deterministic.
     * @param publisher optional flow publisher that receives streamed text
     *                  chunks; may be {@code null} for non-streaming calls.
     *                  When non-null, batch size must be {@code 1}.
     * @param progress  optional listener notified once per newly generated token;
     *                  may be {@code null}.
     * @return one {@link ChatCompletion} per prompt in the batch.
     */
    ChatCompletion[] generate(int[][] prompts, int maxGenLen, double temperature,
                              double topp, boolean logprobs, long seed,
                              SubmissionPublisher<String> publisher,
                              GenerationListener progress);

    /**
     * Generates assistant replies for dialogs.
     *
     * <p>Equivalent to {@link #encodeChat} on each dialog followed by
     * {@link #generate}.
     *
     * @param dialogs   batch of dialogs; each dialog is an ordered array of
     *                  {@link Message} turns.
     * @param maxGenLen maximum number of <em>new</em> tokens to generate per
     *                  dialog.
     * @param temperature sampling temperature; higher values increase randomness.
     * @param topp      nucleus-sampling top-p threshold in {@code (0, 1]}.
     * @param logprobs  {@code true} to include per-token log-probabilities in
     *                  the result.
     * @param seed      optional RNG seed for deterministic sampling;
     *                  {@code 0} means non-deterministic.
     * @param publisher optional flow publisher that receives streamed text
     *                  chunks; may be {@code null} for non-streaming calls.
     *                  When non-null, batch size must be {@code 1}.
     * @return one {@link ChatCompletion} per dialog in the batch.
     */
    ChatCompletion[] chat(Message[][] dialogs, int maxGenLen, double temperature,
                          double topp, boolean logprobs, long seed,
                          SubmissionPublisher<String> publisher);
}
