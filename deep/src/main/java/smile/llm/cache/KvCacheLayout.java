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
package smile.llm.cache;

/**
 * Family-agnostic layout used to size a {@link KvCachePool}.
 *
 * @param numLayers    number of transformer layers that store KV in the pool.
 * @param numKvHeads   number of key/value heads.
 * @param headDim      dimension of each attention head.
 * @param maxBatchSize maximum concurrent batch size (used for CPU / test sizing).
 * @param maxSeqLen    maximum sequence length (used for CPU / test sizing and minima).
 *
 * @author Haifeng Li
 */
public record KvCacheLayout(
        int numLayers,
        int numKvHeads,
        int headDim,
        int maxBatchSize,
        int maxSeqLen) {

    /**
     * Builds a layout from common dense-decoder dimensions.
     *
     * @param numLayers    layer count.
     * @param dim          token embedding dimension.
     * @param numHeads     query head count.
     * @param numKvHeads   key/value head count, or {@code null} to use {@code numHeads}.
     * @param maxBatchSize max batch size.
     * @param maxSeqLen    max sequence length.
     * @return the cache layout.
     */
    public static KvCacheLayout of(int numLayers, int dim, int numHeads, Integer numKvHeads,
                                   int maxBatchSize, int maxSeqLen) {
        if (numHeads < 1) {
            throw new IllegalArgumentException("numHeads must be >= 1");
        }
        if (dim % numHeads != 0) {
            throw new IllegalArgumentException("dim must be divisible by numHeads");
        }
        int kvHeads = numKvHeads != null ? numKvHeads : numHeads;
        return new KvCacheLayout(numLayers, kvHeads, dim / numHeads, maxBatchSize, maxSeqLen);
    }
}
