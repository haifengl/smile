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

import java.io.IOException;
import java.util.Arrays;
import java.util.Random;
import org.junit.jupiter.api.*;
import smile.deep.tensor.Device;
import smile.deep.tensor.Tensor;
import smile.llm.cache.KvCachePool;
import smile.torch.Native;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for MiniMax Sparse Attention block selection and its use in the model (CPU).
 *
 * @author Haifeng Li
 */
public class MiniMaxSparseTest {

    /** Straight-loop reference of the MSA selection rule. */
    private static boolean[] referenceAllow(float[] q, float[] k, int batch, int seqlen, int groups,
                                            int dim, int length, int[] positions, int blockSize,
                                            int topk, int initBlocks, int localBlocks) {
        boolean[] allow = new boolean[batch * groups * seqlen * length];
        int numBlocks = (length + blockSize - 1) / blockSize;
        for (int b = 0; b < batch; b++) {
            for (int g = 0; g < groups; g++) {
                for (int t = 0; t < seqlen; t++) {
                    int p = positions[b] + t;
                    int visible = p / blockSize + 1;
                    double[] score = new double[numBlocks];
                    Arrays.fill(score, Double.NEGATIVE_INFINITY);
                    for (int j = 0; j <= p; j++) {
                        double dot = 0;
                        for (int d = 0; d < dim; d++) {
                            dot += q[((b * seqlen + t) * groups + g) * dim + d]
                                    * k[(b * length + j) * dim + d];
                        }
                        score[j / blockSize] = Math.max(score[j / blockSize], dot);
                    }
                    for (int i = 0; i < numBlocks; i++) {
                        if (i >= visible) score[i] = Double.NEGATIVE_INFINITY;
                        else if (i >= Math.max(0, visible - localBlocks)) score[i] = 1e29;
                        if (i < initBlocks && i < visible) score[i] = 1e30;
                    }
                    Integer[] order = new Integer[numBlocks];
                    for (int i = 0; i < numBlocks; i++) order[i] = i;
                    Arrays.sort(order, (x, y) -> Double.compare(score[y], score[x]));
                    boolean[] chosen = new boolean[numBlocks];
                    for (int i = 0; i < Math.min(topk, numBlocks); i++) {
                        if (score[order[i]] > Double.NEGATIVE_INFINITY) chosen[order[i]] = true;
                    }
                    for (int j = 0; j < length; j++) {
                        allow[((b * groups + g) * seqlen + t) * length + j] = j <= p && chosen[j / blockSize];
                    }
                }
            }
        }
        return allow;
    }

    private static float[] randomFloats(Random rnd, int n) {
        float[] v = new float[n];
        for (int i = 0; i < n; i++) v[i] = (float) rnd.nextGaussian();
        return v;
    }

    private void checkAgainstReference(int blockSize, int topk, int initBlocks, int localBlocks,
                                       int[] positions, int seqlen) {
        int batch = positions.length;
        int groups = 2;
        int dim = 8;
        int length = Arrays.stream(positions).max().getAsInt() + seqlen;
        Random rnd = new Random(11);
        float[] q = randomFloats(rnd, batch * seqlen * groups * dim);
        float[] k = randomFloats(rnd, batch * length * dim);
        boolean[] expected = referenceAllow(q, k, batch, seqlen, groups, dim, length, positions,
                blockSize, topk, initBlocks, localBlocks);
        try (var noGrad = Tensor.noGradGuard();
             Tensor tq = Tensor.of(q, batch, seqlen, groups, dim);
             Tensor tk = Tensor.of(k, batch, length, 1, dim);
             Tensor allow = MiniMaxSparse.allowMask(tq, tk, positions, blockSize, topk, initBlocks, localBlocks);
             Tensor asInt = allow.to(smile.deep.tensor.ScalarType.Int32)) {
            assertArrayEquals(new long[]{batch, groups, seqlen, length}, allow.shape());
            int[] got = asInt.copy().intArray();
            for (int i = 0; i < expected.length; i++) {
                assertEquals(expected[i] ? 1 : 0, got[i], "mask element " + i);
            }
        }
    }

    @Test
    public void testGivenPrefillWhenSelectingBlocksThenMatchesReference() {
        checkAgainstReference(4, 2, 0, 1, new int[]{0, 0}, 14);
    }

    @Test
    public void testGivenCachedPrefixAndRaggedRowsWhenSelectingThenMatchesReference() {
        checkAgainstReference(4, 2, 0, 1, new int[]{9, 3}, 3);
    }

    @Test
    public void testGivenSingleTokenDecodeWhenSelectingThenMatchesReference() {
        checkAgainstReference(4, 3, 0, 1, new int[]{17, 5}, 1);
    }

    @Test
    public void testGivenInitBlocksAndTwoLocalBlocksWhenSelectingThenMatchesReference() {
        checkAgainstReference(4, 3, 1, 2, new int[]{0}, 18);
    }

    @Test
    public void testGivenTopkLargerThanBlocksWhenSelectingThenEqualsCausalMask() {
        int length = 10;
        int[] positions = {0};
        Random rnd = new Random(3);
        try (var noGrad = Tensor.noGradGuard();
             Tensor q = Tensor.of(randomFloats(rnd, length * 8), 1, length, 1, 8);
             Tensor k = Tensor.of(randomFloats(rnd, length * 8), 1, length, 1, 8);
             Tensor allow = MiniMaxSparse.allowMask(q, k, positions, 4, 16, 0, 1);
             Tensor asInt = allow.to(smile.deep.tensor.ScalarType.Int32)) {
            int[] got = asInt.copy().intArray();
            for (int t = 0; t < length; t++) {
                for (int j = 0; j < length; j++) {
                    assertEquals(j <= t ? 1 : 0, got[t * length + j], "t=" + t + " j=" + j);
                }
            }
        }
    }

    // ---- model level

    private static MiniMaxModel tinyModel(MiniMaxModelArgs args, boolean forceDense) {
        MiniMaxModel model = new MiniMaxModel(args);
        try (var noGrad = Tensor.noGradGuard()) {
            for (MiniMaxBlock block : model.layers()) {
                if (block.moe() != null) {
                    try (Tensor r = Tensor.randn(args.numExperts(), args.dim())) {
                        Native.copy_(block.moe().gateWeight, r);
                    }
                }
            }
        }
        model.to(Device.CPU());
        model.setKvCachePool(KvCachePool.forTesting(args.kvCacheLayout(), Device.CPU()), false);
        model.layers().forEach(l -> l.selfAttn().forceDense = forceDense);
        model.eval();
        return model;
    }

    private static MiniMaxModelArgs args() throws IOException {
        return MiniMaxModelArgs.fromHuggingFace("deep/src/test/resources/minimax/config_tiny.json", 2, 0);
    }

    private static float[] lastLogits(MiniMaxModel model, long[] tokens) {
        KvCachePool pool = model.kvCachePool();
        pool.bindRequests(1, 24);
        try (var noGrad = Tensor.noGradGuard();
             Tensor t = Tensor.of(tokens, 1, tokens.length);
             Tensor logits = model.forward(t, 0, false)) {
            return logits.copy().floatArray();
        } finally {
            pool.unbindRequests();
        }
    }

    private static long[] sequence(int n) {
        long[] s = new long[n];
        for (int i = 0; i < n; i++) s[i] = (i * 7 + 3) % 90;
        return s;
    }

    @Test
    public void testGivenAllBlocksSelectedWhenForwardThenMatchesDenseAttention() throws IOException {
        MiniMaxModelArgs a = args();
        // 6 tokens, block size 4: 2 blocks <= topk 2, so MSA must equal dense attention.
        // Both models start from different random weights, so reuse one model and flip the switch.
        MiniMaxModel model = tinyModel(a, false);
        float[] sparse = lastLogits(model, sequence(6));
        model.layers().forEach(l -> l.selfAttn().forceDense = true);
        float[] dense = lastLogits(model, sequence(6));
        assertArrayEquals(dense, sparse, 1e-4f);
    }

    @Test
    public void testGivenMoreBlocksThanTopkWhenForwardThenSparseDiffersFromDense() throws IOException {
        MiniMaxModelArgs a = args();
        MiniMaxModel model = tinyModel(a, false);
        float[] sparse = lastLogits(model, sequence(20));
        model.layers().forEach(l -> l.selfAttn().forceDense = true);
        float[] dense = lastLogits(model, sequence(20));
        double maxDiff = 0;
        for (int i = 0; i < dense.length; i++) maxDiff = Math.max(maxDiff, Math.abs(dense[i] - sparse[i]));
        assertTrue(maxDiff > 1e-5, "sparse selection should drop some keys, maxDiff=" + maxDiff);
    }

    @Test
    public void testGivenSparseLayersWhenDecodingIncrementallyThenMatchesFullPrefill() throws IOException {
        MiniMaxModelArgs a = args();
        MiniMaxModel model = tinyModel(a, false);
        long[] seq = sequence(19);
        float[] full = lastLogits(model, seq);

        KvCachePool pool = model.kvCachePool();
        pool.bindRequests(1, 24);
        float[] incremental;
        try (var noGrad = Tensor.noGradGuard();
             Tensor prefix = Tensor.of(Arrays.copyOf(seq, 18), 1, 18);
             Tensor ignored = model.forward(prefix, 0, false);
             Tensor next = Tensor.of(new long[]{seq[18]}, 1, 1);
             Tensor logits = model.forward(next, new int[]{18})) {
            incremental = logits.copy().floatArray();
        } finally {
            pool.unbindRequests();
        }
        assertArrayEquals(full, incremental, 1e-3f);
    }
}
