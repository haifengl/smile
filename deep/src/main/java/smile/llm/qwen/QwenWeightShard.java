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
package smile.llm.qwen;

import smile.deep.tensor.Tensor;
import smile.llm.parallel.TensorShardSpec;
import smile.llm.parallel.WeightSharding;

/**
 * Applies TP weight slices for Qwen module parameter names.
 *
 * @author Haifeng Li
 */
final class QwenWeightShard {
    private QwenWeightShard() {
    }

    /**
     * Returns a tensor suitable for {@code loadStateDict} on one TP rank.
     * Caller owns any newly created view; may return {@code src} unchanged.
     */
    static Tensor shard(String smileName, Tensor src, QwenModelArgs args, TensorShardSpec shard) {
        if (shard == null || shard.tpSize() <= 1) {
            return src;
        }
        int tp = shard.tpSize();
        int rank = shard.tpRank();
        int headDim = args.headDim();

        if (smileName.endsWith("mlp.w1.weight") || smileName.endsWith("mlp.w3.weight")) {
            return WeightSharding.columnParallel(src, tp, rank);
        }
        if (smileName.endsWith("mlp.w2.weight")) {
            return WeightSharding.rowParallel(src, tp, rank);
        }
        if (smileName.endsWith("self_attn.q_proj.weight")) {
            return WeightSharding.columnParallelHeads(src, args.numHeads(), headDim * 2, tp, rank);
        }
        if (smileName.endsWith("self_attn.k_proj.weight")
                || smileName.endsWith("self_attn.v_proj.weight")) {
            return WeightSharding.columnParallelHeads(src, args.numKvHeads(), headDim, tp, rank);
        }
        if (smileName.endsWith("self_attn.o_proj.weight")) {
            return WeightSharding.rowParallelHeads(src, args.numHeads(), headDim, tp, rank);
        }
        if (smileName.endsWith("linear_attn.in_proj_qkv.weight")) {
            // Packed [2*K + V, D] with head-major packing matching local GatedDeltaNet layout.
            int kHeads = args.linearNumKeyHeads();
            int vHeads = args.linearNumValueHeads();
            int kDim = args.linearKeyHeadDim();
            int vDim = args.linearValueHeadDim();
            // Slice as three column-parallel blocks then concat is expensive; treat as
            // contiguous head shards: Q heads | K heads | V heads.
            return shardPackedQkv(src, kHeads, vHeads, kDim, vDim, tp, rank);
        }
        if (smileName.endsWith("linear_attn.in_proj_z.weight")) {
            return WeightSharding.columnParallelHeads(
                    src, args.linearNumValueHeads(), args.linearValueHeadDim(), tp, rank);
        }
        if (smileName.endsWith("linear_attn.in_proj_a.weight")
                || smileName.endsWith("linear_attn.in_proj_b.weight")
                || smileName.endsWith("linear_attn.A_log")
                || smileName.endsWith("linear_attn.dt_bias")) {
            return WeightSharding.columnParallelBias(src, tp, rank);
        }
        if (smileName.endsWith("linear_attn.out_proj.weight")) {
            return WeightSharding.rowParallelHeads(
                    src, args.linearNumValueHeads(), args.linearValueHeadDim(), tp, rank);
        }
        if (smileName.endsWith("linear_attn.conv1d.weight")) {
            // [convDim, K] — shard channels like packed QKV.
            int kHeads = args.linearNumKeyHeads();
            int vHeads = args.linearNumValueHeads();
            int kDim = args.linearKeyHeadDim();
            int vDim = args.linearValueHeadDim();
            return shardPackedQkv(src, kHeads, vHeads, kDim, vDim, tp, rank);
        }
        // Replicated: embeddings, norms, lm_head, etc.
        return src;
    }

    /**
     * Slices packed QKV channels {@code [2*Hk*Dk + Hv*Dv, ...]} into the local
     * head bands for Q, K, and V, then concatenates along dim 0.
     */
    private static Tensor shardPackedQkv(Tensor src, int kHeads, int vHeads, int kDim, int vDim,
                                         int tp, int rank) {
        int localK = kHeads / tp;
        int localV = vHeads / tp;
        long qStart = (long) rank * localK * kDim;
        long qEnd = qStart + (long) localK * kDim;
        long kBase = (long) kHeads * kDim;
        long kStart = kBase + (long) rank * localK * kDim;
        long kEnd = kStart + (long) localK * kDim;
        long vBase = 2L * kHeads * kDim;
        long vStart = vBase + (long) rank * localV * vDim;
        long vEnd = vStart + (long) localV * vDim;

        try (Tensor q = src.get(smile.deep.tensor.Index.slice(qStart, qEnd));
             Tensor k = src.get(smile.deep.tensor.Index.slice(kStart, kEnd));
             Tensor v = src.get(smile.deep.tensor.Index.slice(vStart, vEnd))) {
            // vstack materializes new storage; safe after q/k/v close.
            return Tensor.vstack(q, k, v);
        }
    }
}
