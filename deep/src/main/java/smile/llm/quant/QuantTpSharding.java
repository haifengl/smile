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
package smile.llm.quant;

import smile.deep.tensor.Index;
import smile.deep.tensor.Tensor;

/**
 * Shard-then-pack helpers for tensor-parallel quantized linears.
 *
 * <p>Column-parallel shards {@code out_features}; row-parallel shards
 * {@code in_features}. Callers pack the local shard into Marlin / FP8 after
 * sharding (never pack globally then slice).
 *
 * @author Haifeng Li
 */
public final class QuantTpSharding {
    private QuantTpSharding() {}

    /**
     * Column-parallel shard of a dense weight {@code [out, in]} (or FP8).
     *
     * @param weight global weight.
     * @param tpSize TP world size.
     * @param tpRank this rank.
     * @return local {@code [out/tp, in]} (caller owns).
     */
    public static Tensor shardColumn(Tensor weight, int tpSize, int tpRank) {
        if (tpSize <= 1) {
            return weight.copy();
        }
        long[] shape = weight.shape();
        if (shape.length != 2) {
            throw new IllegalArgumentException("weight must be 2D");
        }
        if (shape[0] % tpSize != 0) {
            throw new IllegalArgumentException(
                    "outFeatures " + shape[0] + " not divisible by tpSize " + tpSize);
        }
        int localOut = (int) (shape[0] / tpSize);
        int start = tpRank * localOut;
        try (Index rows = Index.slice(start, start + localOut)) {
            return weight.get(rows, Index.Colon).contiguous();
        }
    }

    /**
     * Row-parallel shard of a dense weight {@code [out, in]}.
     *
     * @param weight global weight.
     * @param tpSize TP world size.
     * @param tpRank this rank.
     * @return local {@code [out, in/tp]} (caller owns).
     */
    public static Tensor shardRow(Tensor weight, int tpSize, int tpRank) {
        if (tpSize <= 1) {
            return weight.copy();
        }
        long[] shape = weight.shape();
        if (shape.length != 2) {
            throw new IllegalArgumentException("weight must be 2D");
        }
        if (shape[1] % tpSize != 0) {
            throw new IllegalArgumentException(
                    "inFeatures " + shape[1] + " not divisible by tpSize " + tpSize);
        }
        int localIn = (int) (shape[1] / tpSize);
        int start = tpRank * localIn;
        try (Index cols = Index.slice(start, start + localIn)) {
            return weight.get(Index.Colon, cols).contiguous();
        }
    }

    /**
     * Column-parallel shard of GPTQ {@code qweight} packed along input dim0
     * ({@code [in/8, out]}). Shards along {@code out}.
     */
    public static Tensor shardGptqQweightColumn(Tensor qweight, int tpSize, int tpRank) {
        if (tpSize <= 1) {
            return qweight.copy();
        }
        long[] shape = qweight.shape();
        if (shape.length != 2) {
            throw new IllegalArgumentException("qweight must be 2D");
        }
        if (shape[1] % tpSize != 0) {
            throw new IllegalArgumentException(
                    "GPTQ outFeatures " + shape[1] + " not divisible by tpSize " + tpSize);
        }
        int localOut = (int) (shape[1] / tpSize);
        int start = tpRank * localOut;
        try (Index cols = Index.slice(start, start + localOut)) {
            return qweight.get(Index.Colon, cols).contiguous();
        }
    }

    /**
     * Row-parallel shard of GPTQ {@code qweight} {@code [in/8, out]} along packed K.
     */
    public static Tensor shardGptqQweightRow(Tensor qweight, int tpSize, int tpRank) {
        if (tpSize <= 1) {
            return qweight.copy();
        }
        long[] shape = qweight.shape();
        if (shape.length != 2) {
            throw new IllegalArgumentException("qweight must be 2D");
        }
        if (shape[0] % tpSize != 0) {
            throw new IllegalArgumentException(
                    "GPTQ packedIn " + shape[0] + " not divisible by tpSize " + tpSize);
        }
        int localPacked = (int) (shape[0] / tpSize);
        int start = tpRank * localPacked;
        try (Index rows = Index.slice(start, start + localPacked)) {
            return qweight.get(rows, Index.Colon).contiguous();
        }
    }

    /**
     * Column-parallel shard of AWQ {@code qweight} {@code [in, out/8]} along packed out.
     */
    public static Tensor shardAwqQweightColumn(Tensor qweight, int tpSize, int tpRank) {
        if (tpSize <= 1) {
            return qweight.copy();
        }
        long[] shape = qweight.shape();
        if (shape.length != 2) {
            throw new IllegalArgumentException("AWQ qweight must be 2D");
        }
        if (shape[1] % tpSize != 0) {
            throw new IllegalArgumentException(
                    "AWQ packedOut " + shape[1] + " not divisible by tpSize " + tpSize);
        }
        int localPacked = (int) (shape[1] / tpSize);
        int start = tpRank * localPacked;
        try (Index cols = Index.slice(start, start + localPacked)) {
            return qweight.get(Index.Colon, cols).contiguous();
        }
    }

    /**
     * Row-parallel shard of AWQ {@code qweight} {@code [in, out/8]} along K.
     */
    public static Tensor shardAwqQweightRow(Tensor qweight, int tpSize, int tpRank) {
        if (tpSize <= 1) {
            return qweight.copy();
        }
        long[] shape = qweight.shape();
        if (shape.length != 2) {
            throw new IllegalArgumentException("AWQ qweight must be 2D");
        }
        if (shape[0] % tpSize != 0) {
            throw new IllegalArgumentException(
                    "AWQ inFeatures " + shape[0] + " not divisible by tpSize " + tpSize);
        }
        int localIn = (int) (shape[0] / tpSize);
        int start = tpRank * localIn;
        try (Index rows = Index.slice(start, start + localIn)) {
            return qweight.get(rows, Index.Colon).contiguous();
        }
    }

    /**
     * Column-parallel shard of scales {@code [groups, out]} along out.
     */
    public static Tensor shardScalesColumn(Tensor scales, int tpSize, int tpRank) {
        if (tpSize <= 1) {
            return scales.copy();
        }
        long[] ss = scales.shape();
        if (ss.length != 2) {
            throw new IllegalArgumentException("scales must be 2D [groups, out]");
        }
        if (ss[1] % tpSize != 0) {
            throw new IllegalArgumentException(
                    "scales outFeatures " + ss[1] + " not divisible by tpSize " + tpSize);
        }
        return shardColumn(scales, tpSize, tpRank);
    }

    /**
     * Row-parallel shard of scales {@code [groups, out]} along groups (K groups).
     */
    public static Tensor shardScalesRow(Tensor scales, int tpSize, int tpRank) {
        if (tpSize <= 1) {
            return scales.copy();
        }
        long[] ss = scales.shape();
        if (ss.length != 2) {
            throw new IllegalArgumentException("scales must be 2D [groups, out]");
        }
        if (ss[0] % tpSize != 0) {
            throw new IllegalArgumentException(
                    "scales groups " + ss[0] + " not divisible by tpSize " + tpSize);
        }
        int localG = (int) (ss[0] / tpSize);
        int start = tpRank * localG;
        try (Index rows = Index.slice(start, start + localG)) {
            return scales.get(rows, Index.Colon).contiguous();
        }
    }
}
