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

import java.util.Random;
import org.junit.jupiter.api.Test;
import smile.deep.tensor.Device;
import smile.deep.tensor.ScalarType;
import smile.deep.tensor.Tensor;
import smile.torch.Native;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * CUDA-only checks of the fused decode kernels against straightforward double-precision references.
 *
 * @author Haifeng Li
 */
public class MiniMaxFusedOpsTest {
    private static final Device CUDA = Device.CUDA((byte) 0);

    private static boolean available() {
        return smile.torch.smile_torch_h.smile_cuda_is_available() != 0 && Native.fusedOpsAvailable();
    }

    private static float[] random(Random rnd, int n, float scale) {
        float[] a = new float[n];
        for (int i = 0; i < n; i++) {
            a[i] = (float) rnd.nextGaussian() * scale;
        }
        return a;
    }

    /** Uploads as bfloat16 and returns the rounded values the kernel will actually see. */
    private static float[] roundTrip(float[] data, long... shape) {
        try (Tensor t = Tensor.of(data, shape).to(ScalarType.BFloat16);
             Tensor back = t.to(Device.CPU()).to(ScalarType.Float)) {
            return back.floatArray();
        }
    }

    private static float[] host(Tensor t) {
        try (Tensor cpu = t.to(Device.CPU()); Tensor f = cpu.to(ScalarType.Float)) {
            return f.floatArray();
        }
    }

    private static void assertClose(double[] expected, float[] actual, double relTol, double absTol, String what) {
        assertEquals(expected.length, actual.length, what);
        double worst = 0;
        for (int i = 0; i < expected.length; i++) {
            double err = Math.abs(expected[i] - actual[i]);
            double tol = absTol + relTol * Math.abs(expected[i]);
            worst = Math.max(worst, err / tol);
            assertTrue(err <= tol, what + " mismatch at " + i + ": expected " + expected[i] + " got " + actual[i]);
        }
    }

    /** Gemma norm of one row in double precision; {@code w} is the zero-centred weight. */
    private static void gemmaNorm(float[] x, int off, float[] w, int d, double eps, double[] out, int outOff) {
        double ss = 0;
        for (int i = 0; i < d; i++) {
            ss += (double) x[off + i] * x[off + i];
        }
        double rstd = 1.0 / Math.sqrt(ss / d + eps);
        for (int i = 0; i < d; i++) {
            out[outOff + i] = x[off + i] * rstd * (1.0 + w[i]);
        }
    }

    @Test
    public void testGivenRowsWhenGemmaRmsNormThenMatchesReference() {
        assumeTrue(available(), "needs CUDA and the fused kernels");
        int rows = 37;
        int d = 6144;
        Random rnd = new Random(1);
        float[] x = roundTrip(random(rnd, rows * d, 2f), rows, d);
        float[] w = roundTrip(random(rnd, d, 0.3f), d);
        double[] expected = new double[rows * d];
        for (int r = 0; r < rows; r++) {
            gemmaNorm(x, r * d, w, d, 1e-6, expected, r * d);
        }
        try (var noGrad = Tensor.noGradGuard();
             Tensor xt = Tensor.of(x, rows, d).to(CUDA).to(ScalarType.BFloat16);
             Tensor wt = Tensor.of(w, d).to(CUDA).to(ScalarType.BFloat16);
             Tensor y = Native.gemmaRmsNorm(xt, wt, 1e-6)) {
            assertNotNull(y);
            assertArrayEquals(new long[]{rows, d}, y.shape());
            assertClose(expected, host(y), 0.02, 0.02, "rms_norm");
        }
    }

    private void checkQkNormRope(int batch, int seqlen, int hq, int hk, int d, int rot, int cosRows) {
        Random rnd = new Random(7);
        int tokens = batch * seqlen;
        float[] q = roundTrip(random(rnd, tokens * hq * d, 1.5f), batch, seqlen, hq, d);
        float[] k = roundTrip(random(rnd, tokens * hk * d, 1.5f), batch, seqlen, hk, d);
        float[] qw = roundTrip(random(rnd, d, 0.3f), d);
        float[] kw = roundTrip(random(rnd, d, 0.3f), d);
        float[] cos = new float[cosRows * rot];
        float[] sin = new float[cosRows * rot];
        for (int r = 0; r < cosRows; r++) {
            for (int i = 0; i < rot; i++) {
                double angle = (r + 1) * Math.pow(10000, -2.0 * (i % (rot / 2)) / rot);
                cos[r * rot + i] = (float) Math.cos(angle);
                sin[r * rot + i] = (float) Math.sin(angle);
            }
        }
        double[] qe = reference(q, qw, cos, sin, tokens, hq, d, rot, cosRows);
        double[] ke = reference(k, kw, cos, sin, tokens, hk, d, rot, cosRows);
        long[] cosShape = cosRows == tokens ? new long[]{tokens, 1, rot} : new long[]{cosRows, rot};
        try (var noGrad = Tensor.noGradGuard();
             Tensor qt = Tensor.of(q, batch, seqlen, hq, d).to(CUDA).to(ScalarType.BFloat16);
             Tensor kt = Tensor.of(k, batch, seqlen, hk, d).to(CUDA).to(ScalarType.BFloat16);
             Tensor qwt = Tensor.of(qw, d).to(CUDA).to(ScalarType.BFloat16);
             Tensor kwt = Tensor.of(kw, d).to(CUDA).to(ScalarType.BFloat16);
             Tensor ct = Tensor.of(cos, cosShape).to(CUDA);
             Tensor st = Tensor.of(sin, cosShape).to(CUDA)) {
            Tensor[] out = Native.qkNormRope(qt, kt, qwt, kwt, ct, st, rot, 1e-6);
            assertNotNull(out);
            assertClose(qe, host(out[0]), 0.03, 0.03, "q");
            assertClose(ke, host(out[1]), 0.03, 0.03, "k");
            out[0].close();
            out[1].close();
        }
    }

    private static double[] reference(float[] x, float[] w, float[] cos, float[] sin,
                                      int tokens, int heads, int d, int rot, int cosRows) {
        double[] out = new double[x.length];
        double[] normed = new double[d];
        int half = rot / 2;
        for (int t = 0; t < tokens; t++) {
            int row = (t % cosRows) * rot;
            for (int h = 0; h < heads; h++) {
                int off = (t * heads + h) * d;
                gemmaNorm(x, off, w, d, 1e-6, normed, 0);
                for (int i = 0; i < d; i++) {
                    double v = normed[i];
                    if (i < rot) {
                        double rotated = i < half ? -normed[i + half] : normed[i - half];
                        v = normed[i] * cos[row + i] + rotated * sin[row + i];
                    }
                    out[off + i] = v;
                }
            }
        }
        return out;
    }

    @Test
    public void testGivenDecodeRowsWhenQkNormRopeThenMatchesReference() {
        assumeTrue(available(), "needs CUDA and the fused kernels");
        checkQkNormRope(5, 1, 64, 4, 128, 64, 5);
    }

    @Test
    public void testGivenPrefillWindowWhenQkNormRopeThenMatchesReference() {
        assumeTrue(available(), "needs CUDA and the fused kernels");
        checkQkNormRope(2, 9, 64, 4, 128, 64, 9);
    }

    @Test
    public void testGivenIndexHeadsWhenQkNormRopeThenMatchesReference() {
        assumeTrue(available(), "needs CUDA and the fused kernels");
        checkQkNormRope(3, 1, 4, 1, 128, 64, 3);
    }

    @Test
    public void testGivenFusedProjectionViewWhenSwigluOaiThenMatchesReference() {
        assumeTrue(available(), "needs CUDA and the fused kernels");
        int rows = 7;
        int inter = 3072;
        double alpha = 1.702;
        double limit = 7.0;
        Random rnd = new Random(3);
        float[] gu = roundTrip(random(rnd, rows * 2 * inter, 6f), rows, 2L * inter);
        double[] expected = new double[rows * inter];
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < inter; c++) {
                double g = Math.min(gu[r * 2 * inter + c], limit);
                double u = Math.max(-limit, Math.min(limit, gu[r * 2 * inter + inter + c]));
                expected[r * inter + c] = g / (1 + Math.exp(-alpha * g)) * (u + 1);
            }
        }
        try (var noGrad = Tensor.noGradGuard();
             Tensor t = Tensor.of(gu, rows, 2L * inter).to(CUDA).to(ScalarType.BFloat16);
             Tensor gate = t.get(smile.deep.tensor.Index.Colon, smile.deep.tensor.Index.slice(0, inter));
             Tensor up = t.get(smile.deep.tensor.Index.Colon, smile.deep.tensor.Index.slice(inter, 2 * inter));
             Tensor out = Native.swigluOai(gate, up, alpha, limit)) {
            assertNotNull(out);
            assertClose(expected, host(out), 0.02, 0.05, "swiglu");
        }
    }

    @Test
    public void testGivenLogitsWhenMoeRouteThenMatchesReference() {
        assumeTrue(available(), "needs CUDA and the fused kernels");
        int tokens = 33;
        int experts = 128;
        int topK = 4;
        double scale = 2.0;
        Random rnd = new Random(5);
        float[] logits = random(rnd, tokens * experts, 2f);
        float[] bias = random(rnd, experts, 0.2f);
        try (var noGrad = Tensor.noGradGuard();
             Tensor lt = Tensor.of(logits, tokens, experts).to(CUDA);
             Tensor bt = Tensor.of(bias, experts).to(CUDA)) {
            Tensor[] out = Native.moeRoute(lt, bt, topK, scale);
            assertNotNull(out);
            int[] ids;
            float[] weights;
            try (Tensor cpu = out[0].to(Device.CPU())) {
                ids = cpu.intArray();
            }
            weights = host(out[1]);
            for (int t = 0; t < tokens; t++) {
                double[] score = new double[experts];
                Integer[] order = new Integer[experts];
                for (int e = 0; e < experts; e++) {
                    score[e] = 1.0 / (1.0 + Math.exp(-logits[t * experts + e]));
                    order[e] = e;
                }
                final int row = t;
                java.util.Arrays.sort(order, (a, b) -> Double.compare(score[b] + bias[b], score[a] + bias[a]));
                double sum = 0;
                for (int j = 0; j < topK; j++) {
                    sum += score[order[j]];
                }
                for (int j = 0; j < topK; j++) {
                    assertEquals(order[j], ids[row * topK + j], "expert " + j + " of token " + row);
                    assertEquals(score[order[j]] / sum * scale, weights[row * topK + j], 1e-5);
                }
            }
            out[0].close();
            out[1].close();
        }
    }
}
