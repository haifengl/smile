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
package smile.llm.attention;

import smile.deep.tensor.Tensor;
import smile.llm.cache.FlashInferKvMetadata;
import smile.llm.cache.KvCachePool;

/**
 * Per-call metadata for {@link AttentionKernel#forward}.
 *
 * @param scale        attention scale; {@code <= 0} means kernel default ({@code 1/sqrt(D)}).
 * @param dropout      dropout probability (torch_native only; usually 0 at inference).
 * @param isCausal     whether to apply causal masking when {@code mask} is null.
 * @param numQoHeads   query / output head count (GQA).
 * @param numKvHeads   key / value head count.
 * @param headDim      per-head dimension.
 * @param layerId      KV pool layer index (FlashInfer).
 * @param startPos     write position in the request (FlashInfer).
 * @param seqLen       query sequence length this step.
 * @param cacheLen     total cached length after this step ({@code startPos + seqLen}).
 * @param kvPool       shared pool, or {@code null} for contiguous torch_native-only calls.
 * @param kvMetadata   CSR page table for FlashInfer, or {@code null}.
 * @param workspace    FlashInfer workspace, or {@code null}.
 *
 * @author Haifeng Li
 */
public record AttentionContext(
        double scale,
        double dropout,
        boolean isCausal,
        int numQoHeads,
        int numKvHeads,
        int headDim,
        int layerId,
        int startPos,
        int seqLen,
        int cacheLen,
        KvCachePool kvPool,
        FlashInferKvMetadata kvMetadata,
        FlashInferWorkspace workspace) {

    /**
     * Contiguous SDPA context (gather path).
     */
    public static AttentionContext contiguous(double scale, double dropout, boolean isCausal) {
        return new AttentionContext(scale, dropout, isCausal,
                0, 0, 0, -1, 0, 0, 0, null, null, null);
    }

    /**
     * FlashInfer / paged context.
     */
    public static AttentionContext paged(
            double scale, boolean isCausal,
            int numQoHeads, int numKvHeads, int headDim,
            int layerId, int startPos, int seqLen, int cacheLen,
            KvCachePool kvPool, FlashInferKvMetadata kvMetadata,
            FlashInferWorkspace workspace) {
        return new AttentionContext(scale, 0.0, isCausal,
                numQoHeads, numKvHeads, headDim,
                layerId, startPos, seqLen, cacheLen,
                kvPool, kvMetadata, workspace);
    }

    /** @return {@code true} when this call carries paged-KV metadata. */
    public boolean isPaged() {
        return kvPool != null && kvMetadata != null;
    }
}
