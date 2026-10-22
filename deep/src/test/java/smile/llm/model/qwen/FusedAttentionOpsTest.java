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
package smile.llm.model.qwen;

import org.junit.jupiter.api.Test;
import smile.deep.tensor.Device;
import smile.deep.tensor.Index;
import smile.deep.tensor.ScalarType;
import smile.deep.tensor.Tensor;
import smile.torch.Native;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * The decode-path ops of a full-attention layer and of the DeltaNet gate projections must match the eager ops they replace.
 *
 * @author Haifeng Li
 */
public class FusedAttentionOpsTest {
    private static final Device CUDA = Device.CUDA();
    private static final ScalarType BF16 = ScalarType.BFloat16;

    private static boolean cuda() {
        return smile.torch.smile_torch_h.smile_cuda_is_available() != 0;
    }

    private static double maxAbs(Tensor a, Tensor b) {
        try (Tensor d = a.to(ScalarType.Float).sub(b.to(ScalarType.Float)).abs();
             Tensor cpu = d.to(Device.CPU())) {
            double m = 0;
            for (float x : cpu.contiguous().floatArray()) {
                m = Math.max(m, x);
            }
            return m;
        }
    }

    @Test
    public void testGivenInterleavedQueryGateWhenQkNormRopeThenMatchesNormThenRope() {
        assumeTrue(cuda(), "CUDA not available");
        int batch = 3, heads = 24, kvHeads = 4, d = 256, rot = 64;
        try (Tensor qFull = Tensor.randn(batch, 1, heads, 2 * d).to(CUDA, BF16);
             Tensor key = Tensor.randn(batch, 1, kvHeads, d).to(CUDA, BF16);
             Tensor qw = Tensor.randn(d).mul(0.1).to(CUDA, BF16);
             Tensor kw = Tensor.randn(d).mul(0.1).to(CUDA, BF16);
             Tensor cos = Tensor.randn(batch, 1, rot).to(CUDA, ScalarType.Float);
             Tensor sin = Tensor.randn(batch, 1, rot).to(CUDA, ScalarType.Float)) {
            Tensor query;
            try (var qs = Index.slice(0, d)) {
                query = qFull.get(Index.Ellipsis, qs);
            }
            Tensor[] fused = Native.qkNormRope(query, key, qw, kw, cos, sin, rot, 1e-6);
            assumeTrue(fused != null, "fused qk norm + rope not available");
            // reference: separate Gemma norms (independent kernel path on contiguous copies), then the HF-style rotation
            try (Tensor qc = query.contiguous().reshape(batch * heads, d);
                 Tensor kc = key.contiguous().reshape(batch * kvHeads, d);
                 Tensor qn = Native.gemmaRmsNorm(qc, qw, 1e-6);
                 Tensor kn = Native.gemmaRmsNorm(kc, kw, 1e-6)) {
                var rope = PartialRotaryEncoding.apply(qn.view(batch, 1, heads, d), kn.view(batch, 1, kvHeads, d),
                        cos, sin, rot);
                double eq = maxAbs(fused[0], rope._1());
                double ek = maxAbs(fused[1], rope._2());
                assertTrue(eq < 0.05 && ek < 0.05, "q diff " + eq + ", k diff " + ek);
            } finally {
                fused[0].close();
                fused[1].close();
            }
            query.close();
        }
    }

    @Test
    public void testGivenStridedGateWhenSigmoidGateMulThenMatchesEager() {
        assumeTrue(cuda(), "CUDA not available");
        int batch = 3, heads = 24, d = 256;
        try (Tensor qFull = Tensor.randn(batch, 1, heads, 2 * d).mul(2).to(CUDA, BF16);
             Tensor attn = Tensor.randn(batch, 1, heads * d).to(CUDA, BF16)) {
            Tensor gateView;
            try (var gs = Index.slice(d, 2 * d)) {
                gateView = qFull.get(Index.Ellipsis, gs);
            }
            Tensor fused = Native.sigmoidGateMul(attn, gateView);
            assumeTrue(fused != null, "fused gate multiply not available");
            try (Tensor gate = gateView.reshape(batch, 1, heads * d);
                 Tensor sig = new smile.deep.activation.Sigmoid(false).forward(gate);
                 Tensor ref = attn.mul(sig)) {
                assertEquals(0.0, maxAbs(fused, ref), 0.0, "must equal the two eager ops bit for bit");
            } finally {
                fused.close();
                gateView.close();
            }
        }
    }

    @Test
    public void testGivenRowsWhenGemv2ThenMatchesTwoLinearProjections() {
        assumeTrue(cuda(), "CUDA not available");
        int hidden = 5120, n = 48;
        for (int rows : new int[]{1, 3, 8}) {
            try (Tensor x = Tensor.randn(rows, 1, hidden).to(CUDA, BF16);
                 Tensor wa = Tensor.randn(n, hidden).mul(0.05).to(CUDA, BF16);
                 Tensor wb = Tensor.randn(n, hidden).mul(0.05).to(CUDA, BF16)) {
                Tensor[] ab = Native.gemv2(x, wa, wb);
                assumeTrue(ab != null, "fused gemv2 not available");
                try (Tensor x2 = x.reshape(rows, hidden);
                     Tensor refA = x2.matmul(wa.transpose(0, 1));
                     Tensor refB = x2.matmul(wb.transpose(0, 1))) {
                    assertEquals(rows, ab[0].shape()[0]);
                    assertEquals(n, ab[0].shape()[1]);
                    double ea = maxAbs(ab[0], refA);
                    double eb = maxAbs(ab[1], refB);
                    // both round the same fp32 sum to bf16; only the summation order differs
                    assertTrue(ea < 0.1 && eb < 0.1, "rows " + rows + ": a diff " + ea + ", b diff " + eb);
                } finally {
                    ab[0].close();
                    ab[1].close();
                }
            }
        }
    }

    @Test
    public void testGivenSlotsWhenKvWriteThenRowsLandInTheirSlotsOnly() {
        assumeTrue(cuda(), "CUDA not available");
        int slots = 64, heads = 4, d = 256, tokens = 3;
        try (Tensor kc = Tensor.zeros(slots, heads, d).to(CUDA, BF16);
             Tensor vc = Tensor.zeros(slots, heads, d).to(CUDA, BF16);
             Tensor k = Tensor.randn(tokens, heads, d).to(CUDA, BF16);
             Tensor v = Tensor.randn(tokens, heads, d).to(CUDA, BF16);
             Tensor idx = Tensor.of(new long[]{17, 3, 42}).to(CUDA)) {
            boolean ok = Native.kvWrite(kc, vc, k, v, idx);
            assumeTrue(ok, "fused kv write not available");
            long[] where = {17, 3, 42};
            for (int t = 0; t < tokens; t++) {
                try (var ti = Index.of(t); var si = Index.of(where[t]);
                     Tensor kr = k.get(ti); Tensor kd = kc.get(si);
                     Tensor vr = v.get(ti); Tensor vd = vc.get(si)) {
                    assertEquals(0.0, maxAbs(kr, kd), 0.0, "K row " + t);
                    assertEquals(0.0, maxAbs(vr, vd), 0.0, "V row " + t);
                }
            }
            try (Tensor kAbs = kc.to(ScalarType.Float).abs().sum();
                 Tensor kRef = k.to(ScalarType.Float).abs().sum()) {
                assertNotNull(kAbs);
                assertEquals(kRef.doubleValue(), kAbs.doubleValue(), 1e-2 * kRef.doubleValue(),
                        "no other slot may be touched");
            }
        }
    }

    @Test
    public void testGivenFourRowsWhenGemv2ThenWithinOneBf16RoundingOfExactSum() {
        assumeTrue(cuda(), "CUDA not available");
        int hidden = 5120, n = 48, rows = 4;
        try (Tensor x = Tensor.randn(rows, hidden).to(CUDA, BF16);
             Tensor wa = Tensor.randn(n, hidden).mul(0.05).to(CUDA, BF16);
             Tensor wb = Tensor.randn(n, hidden).mul(0.05).to(CUDA, BF16)) {
            Tensor[] ab = Native.gemv2(x, wa, wb);
            assumeTrue(ab != null, "fused gemv2 not available");
            float[] xv = x.to(Device.CPU()).to(ScalarType.Float).contiguous().floatArray();
            float[] wv = wa.to(Device.CPU()).to(ScalarType.Float).contiguous().floatArray();
            float[] got = ab[0].to(Device.CPU()).to(ScalarType.Float).contiguous().floatArray();
            try (Tensor ref = x.matmul(wa.transpose(0, 1));
                 Tensor refCpu = ref.to(Device.CPU()).to(ScalarType.Float)) {
                float[] cublas = refCpu.contiguous().floatArray();
                double worstOurs = 0, worstCublas = 0;
                for (int r = 0; r < rows; r++) {
                    for (int c = 0; c < n; c++) {
                        double exact = 0;
                        for (int k = 0; k < hidden; k++) {
                            exact += (double) xv[r * hidden + k] * wv[c * hidden + k];
                        }
                        double scale = Math.abs(exact) + 1e-3;
                        worstOurs = Math.max(worstOurs, Math.abs(got[r * n + c] - exact) / scale);
                        worstCublas = Math.max(worstCublas, Math.abs(cublas[r * n + c] - exact) / scale);
                    }
                }
                // a correctly rounded bf16 result is within 2^-9 relative of the exact sum (plus fp32 accumulation noise)
                assertTrue(worstOurs < 0.01, "gemv2 worst relative error " + worstOurs + " (cuBLAS " + worstCublas + ")");
            } finally {
                ab[0].close();
                ab[1].close();
            }
        }
    }
}
