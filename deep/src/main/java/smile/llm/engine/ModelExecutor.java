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

import smile.deep.tensor.Tensor;
import smile.llm.LanguageModel;
import smile.llm.cache.KvCachePool;

/**
 * Low-level execution surface used by {@link InferenceEngine}.
 *
 * <p>Continuous batching uses {@link #bind}/{@link #prefill}/{@link #decodeStep}
 * instead of monolithic {@link LanguageModel#generate}. Offline callers may
 * still use {@link LanguageModel#generate}.
 *
 * @author Haifeng Li
 */
public interface ModelExecutor {
    /** Underlying language-model façade. */
    LanguageModel model();

    /**
     * Primary KV cache pool (rank 0 for TP). May be {@code null} only for
     * models without full-attention layers.
     */
    KvCachePool kvCachePool();

    /** Pad token id. */
    int padToken();

    /** Stop / EOS token ids. */
    int[] stopTokens();

    /** Decodes token ids to UTF-8 text. */
    String decode(int[] tokens);

    /**
     * Attempts a streaming decode; returns empty on incomplete UTF-8.
     *
     * @throws java.nio.charset.CharacterCodingException when the sequence is incomplete.
     */
    String tryDecode(int[] tokens, boolean skipSpecial)
            throws java.nio.charset.CharacterCodingException;

    /**
     * When {@code false}, {@link InferenceEngine} uses serial
     * {@link LanguageModel#generate} (test stubs without tensors / KV).
     */
    default boolean supportsStepApi() {
        return true;
    }

    /**
     * Binds one request into the KV pool (multi-request safe).
     *
     * @param prompt        prompt token ids.
     * @param totalCapacity desired slots (prompt + generation).
     * @return request id ({@code > 0}).
     */
    int bind(int[] prompt, int totalCapacity);

    /**
     * Page-aligned matched prefix length for a bound request.
     *
     * @param requestId id from {@link #bind}.
     * @return prefix length ({@code >= 0}).
     */
    int prefixLen(int requestId);

    /**
     * Prefills the unbound suffix of a prompt (activates this request alone).
     *
     * @param requestId request id.
     * @param prompt    full prompt tokens.
     * @param prefixLen cached prefix to skip (from {@link #prefixLen}).
     * @return last-position logits shaped {@code [1, vocab]} (caller owns).
     */
    Tensor prefill(int requestId, int[] prompt, int prefixLen);

    /**
     * Prefills {@code prompt[from, to)} (chunked Continuous Batching).
     * Returns last-token logits only when {@code to == prompt.length}.
     *
     * @param requestId request id.
     * @param prompt    full prompt tokens.
     * @param from      inclusive start position in the prompt.
     * @param to        exclusive end position.
     * @return logits {@code [1, vocab]} when the prompt is fully prefilled;
     *         otherwise {@code null}.
     */
    default Tensor prefillChunk(int requestId, int[] prompt, int from, int to) {
        if (to < prompt.length) {
            throw new UnsupportedOperationException(
                    "chunked prefill not supported; override prefillChunk");
        }
        return prefill(requestId, prompt, from);
    }

    /**
     * After a radix KV prefix hit of length {@code prefixLen}, restores any
     * non-KV state required before suffix prefill may start at {@code prefixLen}.
     *
     * <p>Default is a no-op (pure Transformer KV reuse). Hybrid models that
     * keep DeltaNet (or similar) state must replay or restore that state here
     * when prefix reuse is enabled.
     *
     * @param requestId request id from {@link #bind}.
     * @param prompt    full prompt tokens.
     * @param prefixLen page-aligned matched prefix ({@code >= 0}).
     */
    default void warmPrefix(int requestId, int[] prompt, int prefixLen) {
        // no-op
    }

    /**
     * Prefills a multimodal prompt using vision embeds + interleaved mRoPE.
     * Default throws; Qwen overrides.
     *
     * @param requestId request id.
     * @param multimodal preprocess result.
     * @param from      inclusive start (normally 0; prefix reuse disabled for VL).
     * @param to        exclusive end.
     * @return logits when {@code to == inputIds.length}, else null.
     */
    default Tensor prefillMultimodal(int requestId,
                                     smile.llm.model.qwen.QwenVlProcessor.ProcessedMultimodal multimodal,
                                     int from, int to) {
        throw new UnsupportedOperationException("multimodal prefill not supported");
    }

    /**
     * One decode step for an active set (same or mixed positions; engine may
     * cohort by position). Tokens are {@code [B]} last tokens; positions are
     * the write index for each row.
     *
     * @param requestIds active KV request ids (order = batch).
     * @param lastTokens token id per request.
     * @param positions  absolute position per request.
     * @return logits {@code [B, vocab]} (caller owns).
     */
    Tensor decodeStep(int[] requestIds, int[] lastTokens, int[] positions);

    /**
     * Advances pending decode-graph prefetch when the scheduler is idle but KV
     * remains bound (e.g. between continuous-batching waves).
     */
    default void idleDecodeGraphPrefetch() {}

    /**
     * Inserts into the radix tree (when enabled) and unbinds the request.
     *
     * @param requestId      request id.
     * @param sequenceTokens prompt + completion (no pad).
     */
    void finish(int requestId, int[] sequenceTokens);

    /**
     * Instant Eviction without radix insert.
     *
     * @param requestId request id.
     */
    void evict(int requestId);

    /**
     * Max sequence length for this model (prompt + completion).
     *
     * @return max seq len.
     */
    default int maxSeqLen() {
        return model().maxSeqLen();
    }
}
