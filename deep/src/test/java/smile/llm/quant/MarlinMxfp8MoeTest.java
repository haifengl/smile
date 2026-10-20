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

import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.*;
import smile.deep.tensor.Device;
import smile.deep.tensor.ScalarType;
import smile.deep.tensor.Tensor;
import smile.torch.Native;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * CUDA-only checks of the device-side token grouping and the MXFP8 Marlin MoE GEMM.
 *
 * @author Haifeng Li
 */
public class MarlinMxfp8MoeTest {
    private static boolean available() {
        return smile.torch.smile_torch_h.smile_cuda_is_available() != 0 && Native.marlinFp8Available()
                && Native.marlinMoeFp8Available();
    }

    /** Random distinct top-k expert ids, [T * k]. */
    private static long[] randomTopk(int tokens, int k, int experts, long seed) {
        Random rnd = new Random(seed);
        long[] ids = new long[tokens * k];
        for (int t = 0; t < tokens; t++) {
            Set<Long> used = new HashSet<>();
            for (int j = 0; j < k; j++) {
                long e;
                do {
                    e = rnd.nextInt(experts);
                } while (!used.add(e));
                ids[t * k + j] = e;
            }
        }
        return ids;
    }

    @Test
    public void testGivenTopkIdsWhenAlignedThenEveryLocalAssignmentIsPlacedInItsExpertBlock() {
        assumeTrue(available(), "needs CUDA and the MXFP8 Marlin kernels");
        int tokens = 37;
        int k = 4;
        int experts = 16;
        int start = 4;
        int local = 8;
        int block = 16;
        long[] ids = randomTopk(tokens, k, experts, 1);
        Device cuda = Device.CUDA((byte) 0);
        try (var noGrad = Tensor.noGradGuard();
             Tensor topk = Tensor.of(ids, tokens, k).to(cuda);
             Tensor align = Native.moeAlignBlockSize(topk, start, local, block)) {
            int numel = tokens * k;
            int padded = (numel + local * (block - 1) + block - 1) / block * block;
            int blocks = padded / block;
            int[] a = align.to(Device.CPU()).copy().intArray();
            assertEquals(padded + blocks + 1, a.length);
            int post = a[padded + blocks];
            assertEquals(0, post % block);
            boolean[] seen = new boolean[numel];
            int expectedLocal = 0;
            for (long e : ids) if (e >= start && e < start + local) expectedLocal++;
            int placed = 0;
            for (int b = 0; b < post / block; b++) {
                int expert = a[padded + b];
                assertTrue(expert >= 0 && expert < local, "block " + b + " expert " + expert);
                for (int i = 0; i < block; i++) {
                    int id = a[b * block + i];
                    if (id == numel) continue;           // padding
                    assertFalse(seen[id], "assignment " + id + " placed twice");
                    seen[id] = true;
                    placed++;
                    assertEquals(ids[id] - start, expert, "assignment " + id + " in wrong expert block");
                }
            }
            assertEquals(expectedLocal, placed);
            for (int i = post; i < padded; i++) assertEquals(numel, a[i], "tail must be padding");
        }
    }

    private static void checkGemm(int tokens, int k, int experts, int start, int local, int n, int kdim,
                                  int block, boolean mulWeights, long seed) {
        Device cuda = Device.CUDA((byte) 0);
        try (var noGrad = Tensor.noGradGuard()) {
            // Dense expert weights [local, n, kdim] as fp8 + e8m0 scales.
            Random rnd = new Random(seed);
            float[] w = new float[local * n * kdim];
            for (int i = 0; i < w.length; i++) w[i] = 0.5f * (float) rnd.nextGaussian();
            int[] codes = new int[local * n * (kdim / MxFp8.BLOCK)];
            for (int i = 0; i < codes.length; i++) codes[i] = 122 + rnd.nextInt(8);
            Tensor fp8 = Tensor.of(w, local, n, kdim).to(cuda).to(ScalarType.Float8e4m3fn);
            Tensor scale = Tensor.of(codes, local, n, kdim / MxFp8.BLOCK).to(cuda).to(ScalarType.UInt8);
            Tensor qw = Native.marlinFp8RepackWeightStack(fp8);
            Tensor qs = Native.marlinFp8RepackScalesStack(scale);

            float[] av = new float[tokens * kdim];
            for (int i = 0; i < av.length; i++) av[i] = (float) rnd.nextGaussian();
            Tensor a = Tensor.of(av, tokens, kdim).to(cuda).to(ScalarType.BFloat16);
            long[] ids = randomTopk(tokens, k, experts, seed + 1);
            float[] tw = new float[tokens * k];
            for (int i = 0; i < tw.length; i++) tw[i] = 0.2f + rnd.nextFloat();
            Tensor topk = Tensor.of(ids, tokens, k).to(cuda);
            Tensor weights = Tensor.of(tw, tokens, k).to(cuda);

            Tensor align = Native.moeAlignBlockSize(topk, start, local, block);
            Tensor out = Native.marlinMoeFp8Gemm(a, qw, qs, align, weights, block, k, mulWeights);
            assertArrayEquals(new long[]{(long) tokens * k, n}, out.shape());
            float[] got = out.to(ScalarType.Float).to(Device.CPU()).copy().floatArray();

            // Reference: per local expert, dequantize and multiply.
            float[] aF = a.to(ScalarType.Float).to(Device.CPU()).copy().floatArray();
            // Reference: per local expert, dense matmul in double; bf16 output rounding is 2^-9 relative.
            double[] ref = new double[ids.length * n];
            boolean[] have = new boolean[ids.length];
            double sum = 0;
            int count = 0;
            for (int e = 0; e < local; e++) {
                try (Tensor fe = fp8.get(smile.deep.tensor.Index.of(e)); Tensor se = scale.get(smile.deep.tensor.Index.of(e));
                     Tensor dense = MxFp8.dequantize(fe, se, ScalarType.Float)) {
                    float[] d = dense.to(Device.CPU()).copy().floatArray();
                    for (int idx = 0; idx < ids.length; idx++) {
                        if (ids[idx] - start != e) continue;
                        have[idx] = true;
                        int t = idx / k;
                        for (int c = 0; c < n; c++) {
                            double acc = 0;
                            for (int r = 0; r < kdim; r++) acc += (double) aF[t * kdim + r] * d[c * kdim + r];
                            if (mulWeights) acc *= tw[idx];
                            ref[idx * n + c] = acc;
                            sum += Math.abs(acc);
                            count++;
                        }
                    }
                }
            }
            assertTrue(count > 0);
            double mean = sum / count;
            double worst = 0;
            for (int idx = 0; idx < ids.length; idx++) {
                if (!have[idx]) continue;
                for (int c = 0; c < n; c++) {
                    double allowed = 0.0125 * Math.abs(ref[idx * n + c]) + 0.01 * mean;
                    worst = Math.max(worst, Math.abs(ref[idx * n + c] - got[idx * n + c]) / allowed);
                }
            }
            assertTrue(worst <= 1.0, "block=" + block + ": error is " + worst + "x the allowed bf16 tolerance");
            // Assignments owned by other ranks must be zero.
            for (int idx = 0; idx < ids.length; idx++) {
                if (ids[idx] >= start && ids[idx] < start + local) continue;
                for (int c = 0; c < n; c++) assertEquals(0f, got[idx * n + c], "dropped row " + idx);
            }
            for (Tensor t : new Tensor[]{fp8, scale, qw, qs, a, topk, weights, align, out}) t.close();
        }
    }

    @Test
    public void testGivenSingleRankWhenMoeGemmThenMatchesPerExpertDenseReference() {
        assumeTrue(available(), "needs CUDA and the MXFP8 Marlin kernels");
        checkGemm(24, 4, 8, 0, 8, 256, 512, 16, false, 1);
        checkGemm(24, 4, 8, 0, 8, 256, 512, 16, true, 2);
        checkGemm(3, 4, 8, 0, 8, 128, 256, 8, true, 3);
        checkGemm(100, 4, 8, 0, 8, 128, 256, 32, true, 4);
    }

    @Test
    public void testGivenExpertParallelSliceWhenMoeGemmThenOnlyLocalExpertsContribute() {
        assumeTrue(available(), "needs CUDA and the MXFP8 Marlin kernels");
        checkGemm(40, 4, 16, 8, 8, 256, 512, 16, true, 5);
        checkGemm(2, 4, 16, 0, 4, 128, 256, 8, true, 6);
    }
}
