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
package smile.llm.engine;

import smile.llm.ChatOptions;
import smile.llm.GenerationListener;
import smile.llm.Message;
import smile.llm.model.qwen.QwenVlProcessor;

/**
 * One generation job submitted to {@link InferenceEngine}.
 *
 * @param promptTokens token ids (required unless {@code dialog} is set).
 * @param dialog       optional chat turns; encoded by the engine when
 *                     {@code promptTokens} is {@code null}.
 * @param multimodal   optional Qwen VL preprocess result (images/video); when
 *                     set, {@code promptTokens} should match {@code multimodal.inputIds()}.
 * @param maxGenLen    max new tokens.
 * @param temperature  sampling temperature.
 * @param topp         nucleus top-p.
 * @param logprobs     whether to return log-probabilities.
 * @param seed         RNG seed; {@code 0} = non-deterministic.
 * @param listener     optional per-request listener.
 * @param chatOptions  optional tool-calling options.
 *
 * @author Haifeng Li
 */
public record GenerationRequest(
        int[] promptTokens,
        Message[] dialog,
        QwenVlProcessor.ProcessedMultimodal multimodal,
        int maxGenLen,
        double temperature,
        double topp,
        boolean logprobs,
        long seed,
        GenerationListener listener,
        ChatOptions chatOptions) {

    /**
     * Builds a request from already-tokenized prompt ids.
     */
    public static GenerationRequest ofTokens(int[] promptTokens, int maxGenLen,
                                             double temperature, double topp,
                                             boolean logprobs, long seed,
                                             GenerationListener listener) {
        return ofTokens(promptTokens, maxGenLen, temperature, topp, logprobs, seed, listener, null);
    }

    /**
     * Builds a request from already-tokenized prompt ids with chat options.
     */
    public static GenerationRequest ofTokens(int[] promptTokens, int maxGenLen,
                                             double temperature, double topp,
                                             boolean logprobs, long seed,
                                             GenerationListener listener,
                                             ChatOptions chatOptions) {
        if (promptTokens == null) {
            throw new IllegalArgumentException("promptTokens must not be null");
        }
        return new GenerationRequest(promptTokens, null, null, maxGenLen, temperature, topp,
                logprobs, seed, listener, chatOptions);
    }

    /**
     * Builds a multimodal request (vision embeds spliced during prefill).
     */
    public static GenerationRequest ofMultimodal(QwenVlProcessor.ProcessedMultimodal multimodal,
                                                 int maxGenLen, double temperature, double topp,
                                                 boolean logprobs, long seed,
                                                 GenerationListener listener) {
        return ofMultimodal(multimodal, maxGenLen, temperature, topp, logprobs, seed, listener, null);
    }

    /**
     * Builds a multimodal request with chat options.
     */
    public static GenerationRequest ofMultimodal(QwenVlProcessor.ProcessedMultimodal multimodal,
                                                 int maxGenLen, double temperature, double topp,
                                                 boolean logprobs, long seed,
                                                 GenerationListener listener,
                                                 ChatOptions chatOptions) {
        if (multimodal == null) {
            throw new IllegalArgumentException("multimodal must not be null");
        }
        return new GenerationRequest(multimodal.inputIds(), null, multimodal, maxGenLen,
                temperature, topp, logprobs, seed, listener, chatOptions);
    }

    /**
     * Builds a request from a chat dialog (encoded at execution time).
     */
    public static GenerationRequest ofDialog(Message[] dialog, int maxGenLen,
                                             double temperature, double topp,
                                             boolean logprobs, long seed,
                                             GenerationListener listener) {
        return ofDialog(dialog, null, maxGenLen, temperature, topp, logprobs, seed, listener);
    }

    /**
     * Builds a request from a chat dialog with chat options.
     */
    public static GenerationRequest ofDialog(Message[] dialog, ChatOptions chatOptions,
                                             int maxGenLen, double temperature, double topp,
                                             boolean logprobs, long seed,
                                             GenerationListener listener) {
        if (dialog == null) {
            throw new IllegalArgumentException("dialog must not be null");
        }
        return new GenerationRequest(null, dialog, null, maxGenLen, temperature, topp,
                logprobs, seed, listener, chatOptions);
    }

    /** @return {@code true} when vision prefill is required. */
    public boolean hasMultimodal() {
        return multimodal != null && multimodal.hasVision();
    }
}
