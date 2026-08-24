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
package smile.llm.parallel;

import smile.deep.tensor.Index;
import smile.deep.tensor.Tensor;

/**
 * Slices full (unsharded) HuggingFace / Meta weights into the local TP shard.
 *
 * <p>Linear weights use PyTorch layout {@code [out_features, in_features]}.
 * Column-parallel layers take a contiguous out-feature band; row-parallel
 * layers take a contiguous in-feature band.
 *
 * @author Haifeng Li
 */
public final class WeightSharding {
    private WeightSharding() {
    }

    /**
     * Column-parallel slice along dim 0 (output features).
     *
     * @param weight   full weight {@code [globalOut, in]}.
     * @param tpSize   TP size.
     * @param tpRank   this rank.
     * @return a view/narrow of the local out-feature rows (caller should clone if needed).
     */
    public static Tensor columnParallel(Tensor weight, int tpSize, int tpRank) {
        if (tpSize <= 1) {
            return weight;
        }
        long globalOut = weight.shape()[0];
        if (globalOut % tpSize != 0) {
            throw new IllegalArgumentException(
                    "columnParallel: out=" + globalOut + " not divisible by tp=" + tpSize);
        }
        long local = globalOut / tpSize;
        long start = tpRank * local;
        return weight.get(Index.slice(start, start + local));
    }

    /**
     * Column-parallel bias {@code [globalOut]}.
     *
     * @param bias   full bias vector.
     * @param tpSize TP size.
     * @param tpRank this rank.
     * @return local bias shard.
     */
    public static Tensor columnParallelBias(Tensor bias, int tpSize, int tpRank) {
        if (tpSize <= 1) {
            return bias;
        }
        long globalOut = bias.shape()[0];
        long local = globalOut / tpSize;
        long start = tpRank * local;
        return bias.get(Index.slice(start, start + local));
    }

    /**
     * Row-parallel slice along dim 1 (input features).
     *
     * @param weight full weight {@code [out, globalIn]}.
     * @param tpSize TP size.
     * @param tpRank this rank.
     * @return local in-feature columns.
     */
    public static Tensor rowParallel(Tensor weight, int tpSize, int tpRank) {
        if (tpSize <= 1) {
            return weight;
        }
        long globalIn = weight.shape()[1];
        if (globalIn % tpSize != 0) {
            throw new IllegalArgumentException(
                    "rowParallel: in=" + globalIn + " not divisible by tp=" + tpSize);
        }
        long local = globalIn / tpSize;
        long start = tpRank * local;
        return weight.get(Index.Colon, Index.slice(start, start + local));
    }

    /**
     * Head-wise column shard for fused QKV / gate projections packed as
     * {@code [numHeads * headDim * pack, dim]} where {@code pack} is 1 or 2.
     *
     * @param weight    full projection weight.
     * @param numHeads  global head count.
     * @param headWidth channels per head ({@code headDim} or {@code headDim * pack}).
     * @param tpSize    TP size.
     * @param tpRank    this rank.
     * @return local head rows.
     */
    public static Tensor columnParallelHeads(Tensor weight, int numHeads, int headWidth,
                                             int tpSize, int tpRank) {
        if (tpSize <= 1) {
            return weight;
        }
        if (numHeads % tpSize != 0) {
            throw new IllegalArgumentException("numHeads not divisible by tpSize");
        }
        int localHeads = numHeads / tpSize;
        long start = (long) tpRank * localHeads * headWidth;
        long end = start + (long) localHeads * headWidth;
        return weight.get(Index.slice(start, end));
    }

    /**
     * Head-wise row shard for output projections {@code [dim, numHeads * headDim]}.
     *
     * @param weight   full output-projection weight.
     * @param numHeads global head count.
     * @param headDim  per-head dimension.
     * @param tpSize   TP size.
     * @param tpRank   this rank.
     * @return local head columns.
     */
    public static Tensor rowParallelHeads(Tensor weight, int numHeads, int headDim,
                                          int tpSize, int tpRank) {
        if (tpSize <= 1) {
            return weight;
        }
        if (numHeads % tpSize != 0) {
            throw new IllegalArgumentException("numHeads not divisible by tpSize");
        }
        int localHeads = numHeads / tpSize;
        long start = (long) tpRank * localHeads * headDim;
        long end = start + (long) localHeads * headDim;
        return weight.get(Index.Colon, Index.slice(start, end));
    }
}
