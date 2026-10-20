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
package smile.llm.model.minimax;

import smile.deep.tensor.Device;
import smile.deep.tensor.ScalarType;
import smile.deep.tensor.Tensor;
import smile.util.AutoScope;

/**
 * MiniMax Sparse Attention (MSA) index branch: scores 128-token KV blocks and selects the top-k
 * blocks per query and KV group.
 *
 * <p>For query position {@code p} and index head {@code g}, the score of block {@code j} is the
 * maximum over the block's keys {@code k <= p} of the raw dot product {@code index_q . index_k}.
 * Blocks beyond {@code p / blockSize} are invisible. The first {@code initBlocks} and the last
 * {@code localBlocks} visible blocks are always selected. The main branch then runs exact
 * attention restricted to the selected blocks' keys (and causality).
 *
 * <p>This is the reference tensor-op implementation: it materializes the full
 * {@code [B, G, S, L]} score matrix, so it is correct but O(S * L) in memory.
 *
 * @author Haifeng Li
 */
final class MiniMaxSparse {
    private static final double INIT_SCORE = 1e30;
    private static final double LOCAL_SCORE = 1e29;

    private MiniMaxSparse() { }

    /**
     * Computes which keys each query may attend to.
     *
     * @param indexQ      index queries after norm and RoPE, {@code [B, S, G, D]}.
     * @param indexK      index keys after norm and RoPE for positions {@code [0, L)}, {@code [B, L, 1, D]}
     *                    (one key head shared by all {@code G} index-query heads).
     * @param positions   absolute position of the first query token of each row.
     * @param blockSize   keys per block.
     * @param topk        blocks selected per query and group.
     * @param initBlocks  leading blocks always selected.
     * @param localBlocks most recent visible blocks always selected.
     * @return boolean mask {@code [B, G, S, L]}; {@code true} where the query may attend to the key.
     */
    static Tensor allowMask(Tensor indexQ, Tensor indexK, int[] positions,
                            int blockSize, int topk, int initBlocks, int localBlocks) {
        long[] qs = indexQ.shape();
        int batch = (int) qs[0];
        int seqlen = (int) qs[1];
        int groups = (int) qs[2];
        int length = (int) indexK.shape()[1];
        int numBlocks = (length + blockSize - 1) / blockSize;
        Device device = indexQ.device();
        var opts = new Tensor.Options().device(device).dtype(ScalarType.Float).requireGradients(false);

        // Host-side position bookkeeping (tiny integer tensors).
        long[] qpos = new long[batch * seqlen];
        long[] valid = new long[batch * seqlen];
        long[] localStart = new long[batch * seqlen];
        for (int b = 0; b < batch; b++) {
            for (int t = 0; t < seqlen; t++) {
                int p = positions[b] + t;
                int visible = p / blockSize + 1;
                qpos[b * seqlen + t] = p;
                valid[b * seqlen + t] = visible;
                localStart[b * seqlen + t] = Math.max(0, visible - localBlocks);
            }
        }
        long[] kpos = new long[length];
        long[] blockOf = new long[length];
        for (int j = 0; j < length; j++) {
            kpos[j] = j;
            blockOf[j] = j / blockSize;
        }
        long[] blk = new long[numBlocks];
        for (int i = 0; i < numBlocks; i++) {
            blk[i] = i;
        }

        AutoScope scope = new AutoScope();
        Tensor.push(scope);
        try {
            Tensor qpT = Tensor.of(qpos, batch, 1, seqlen, 1).to(device);
            Tensor kpT = Tensor.of(kpos, 1, 1, 1, length).to(device);
            Tensor causal = kpT.le(qpT);

            Tensor q = indexQ.to(ScalarType.Float).permute(0, 2, 1, 3);
            Tensor k = indexK.to(ScalarType.Float).permute(0, 2, 3, 1);
            Tensor scores = q.matmul(k);

            Tensor negInf = Tensor.zeros(opts, 1).fill_(Double.NEGATIVE_INFINITY);
            Tensor masked = Tensor.where(causal, scores, negInf);

            Tensor blockIdx = Tensor.of(blockOf, 1, 1, 1, length).to(device)
                    .expand(batch, groups, seqlen, length).contiguous();
            Tensor blockScore = Tensor.zeros(opts, batch, groups, seqlen, numBlocks)
                    .fill_(Double.NEGATIVE_INFINITY);
            blockScore.scatterReduce_(3, blockIdx, masked, "amax");

            Tensor validT = Tensor.of(valid, batch, 1, seqlen, 1).to(device);
            Tensor localT = Tensor.of(localStart, batch, 1, seqlen, 1).to(device);
            Tensor blkT = Tensor.of(blk, 1, 1, 1, numBlocks).to(device);
            Tensor visible = blkT.lt(validT);
            Tensor score = Tensor.where(visible, blockScore, negInf);
            if (initBlocks > 0) {
                Tensor init = blkT.lt(initBlocks).and(visible);
                score = Tensor.where(init, Tensor.zeros(opts, 1).fill_(INIT_SCORE), score);
            }
            Tensor local = blkT.ge(localT).and(visible);
            score = Tensor.where(local, Tensor.zeros(opts, 1).fill_(LOCAL_SCORE), score);
            if (initBlocks > 0) {
                // Init outranks local when a block is both (matches the reference ordering).
                Tensor init = blkT.lt(initBlocks).and(visible);
                score = Tensor.where(init, Tensor.zeros(opts, 1).fill_(INIT_SCORE), score);
            }

            int k2 = Math.min(topk, numBlocks);
            var top = score.topk(k2, 3, true, true);
            Tensor chosenValid = top._1().gt(-1e38).to(ScalarType.Float);
            Tensor allowBlocks = Tensor.zeros(opts, batch, groups, seqlen, numBlocks);
            allowBlocks.scatterReduce_(3, top._2(), chosenValid, "amax");

            Tensor allow = allowBlocks.gather(3, blockIdx).gt(0.5).and(causal);
            allow.promoteToParent();
            return allow;
        } finally {
            Tensor.pop();
        }
    }

    /**
     * Expands a per-group allow mask to an additive attention mask over query heads.
     *
     * @param allow  {@code [B, G, S, L]} boolean.
     * @param numRep query heads per group.
     * @param dtype  mask dtype (the attention compute dtype).
     * @return additive mask {@code [B, G * numRep, S, L]}: {@code 0} where allowed, {@code -inf} elsewhere.
     */
    static Tensor additiveMask(Tensor allow, int numRep, ScalarType dtype) {
        long[] s = allow.shape();
        var opts = new Tensor.Options().device(allow.device()).dtype(dtype).requireGradients(false);
        AutoScope scope = new AutoScope();
        Tensor.push(scope);
        try {
            Tensor perHead = allow.unsqueeze(2).expand(s[0], s[1], numRep, s[2], s[3])
                    .reshape(s[0], s[1] * numRep, s[2], s[3]);
            Tensor zero = Tensor.zeros(opts, 1);
            Tensor negInf = Tensor.zeros(opts, 1).fill_(Double.NEGATIVE_INFINITY);
            Tensor out = Tensor.where(perHead, zero, negInf);
            out.promoteToParent();
            return out;
        } finally {
            Tensor.pop();
        }
    }
}
