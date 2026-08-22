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
 * Common façade for chat-capable decoder LLMs (Llama, Qwen, …).
 *
 * <p>Implementations load a checkpoint, expose chat-template encoding, and
 * run autoregressive generation for a <em>single</em> prompt. Concurrent
 * multi-request scheduling belongs in {@link smile.llm.engine.InferenceEngine},
 * not on this interface.
 *
 * <p>Streaming and metrics use a single optional {@link GenerationListener}.
 * Transport-specific types such as {@link java.util.concurrent.SubmissionPublisher}
 * belong at the serve boundary (see {@link GenerationListeners#toPublisher}).
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
     * Generates a completion from an already-tokenized prompt.
     *
     * @param prompt      prompt token id sequence.
     * @param maxGenLen   maximum number of <em>new</em> tokens to generate
     *                    (not including the prompt itself).
     * @param temperature sampling temperature; higher values increase randomness.
     * @param topp        nucleus-sampling top-p threshold in {@code (0, 1]}.
     * @param logprobs    {@code true} to include per-token log-probabilities in
     *                    the result.
     * @param seed        optional RNG seed for deterministic sampling;
     *                    {@code 0} means non-deterministic.
     * @param listener    optional progress callback; may be {@code null}.
     * @return the completion for {@code prompt}.
     */
    ChatCompletion generate(int[] prompt, int maxGenLen, double temperature,
                            double topp, boolean logprobs, long seed,
                            GenerationListener listener);

    /**
     * Generates an assistant reply for a dialog.
     *
     * <p>Equivalent to {@link #encodeChat} followed by {@link #generate}.
     *
     * @param dialog      ordered conversation turns.
     * @param maxGenLen   maximum number of <em>new</em> tokens to generate.
     * @param temperature sampling temperature; higher values increase randomness.
     * @param topp        nucleus-sampling top-p threshold in {@code (0, 1]}.
     * @param logprobs    {@code true} to include per-token log-probabilities in
     *                    the result.
     * @param seed        optional RNG seed for deterministic sampling;
     *                    {@code 0} means non-deterministic.
     * @param listener    optional progress callback; may be {@code null}.
     * @return the completion for {@code dialog}.
     */
    ChatCompletion chat(Message[] dialog, int maxGenLen, double temperature,
                        double topp, boolean logprobs, long seed,
                        GenerationListener listener);
}
