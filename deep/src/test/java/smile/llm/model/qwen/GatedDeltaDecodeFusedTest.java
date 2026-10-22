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
import smile.deep.tensor.ScalarType;
import smile.deep.tensor.Tensor;
import smile.torch.Native;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * The fused DeltaNet decode core (conv update + split + gates + recurrent rule) must follow the multi-kernel path
 * it replaces, step after step, on the Qwen3.5/3.8 head layout.
 *
 * @author Haifeng Li
 */
public class GatedDeltaDecodeFusedTest {
    private static double maxAbsDiff(Tensor a, Tensor b) {
        try (Tensor diff = a.to(ScalarType.Float).sub(b.to(ScalarType.Float)).abs();
             Tensor cpu = diff.to(Device.CPU())) {
            float[] v = cpu.contiguous().floatArray();
            double m = 0;
            for (float x : v) {
                m = Math.max(m, x);
            }
            return m;
        }
    }

    @Test
    public void testGivenQwenLayoutWhenDecodedForManyStepsThenFusedMatchesUnfusedPath() {
        assumeTrue(smile.torch.smile_torch_h.smile_cuda_is_available() != 0, "CUDA not available");
        assumeTrue(Native.hasGatedDeltaDecodeFused(), "fused decode op not in libsmile_torch");
        int batch = 3, hk = 16, hv = 48, dk = 128, dv = 128, kernel = 4;
        int channels = 2 * hk * dk + hv * dv;
        Device cuda = Device.CUDA();
        ScalarType bf16 = ScalarType.BFloat16;

        try (Tensor weight = Tensor.randn(channels, kernel).mul(0.5).to(cuda, bf16);
             Tensor weightF = weight.to(ScalarType.Float).contiguous();
             Tensor aLog = Tensor.randn(hv).mul(0.3).to(cuda, ScalarType.Float);
             Tensor dtBias = Tensor.randn(hv).to(cuda, ScalarType.Float);
             Tensor convFused = Tensor.randn(batch, channels, kernel - 1).to(cuda, bf16);
             Tensor convRef = convFused.copy();
             Tensor recFused = Tensor.randn(batch, hv, dk, dv).mul(0.1).to(cuda, ScalarType.Float);
             Tensor recRef = recFused.copy()) {
            for (int step = 0; step < 6; step++) {
                try (Tensor mixed = Tensor.randn(batch, 1, channels).to(cuda, bf16);
                     Tensor a = Tensor.randn(batch, 1, hv).to(cuda, bf16);
                     Tensor b = Tensor.randn(batch, 1, hv).to(cuda, bf16);
                     Tensor fused = Native.gatedDeltaDecodeFused(mixed.reshape(batch, channels), convFused, weightF,
                             a.reshape(batch, hv), b.reshape(batch, hv), aLog, dtBias,
                             recFused, hk, hv, dk, dv)) {
                    assertNotNull(fused, "fused op declined a supported layout");
                    assertEquals(batch * hv, fused.shape()[0]);
                    assertEquals(dv, fused.shape()[1]);

                    Tensor[] qkv = GatedDeltaRule.causalConv1dUpdateSplitQkv(
                            mixed.reshape(batch, channels, 1), convRef, weight, hk, hv, dk, dv);
                    Tensor[] gates = GatedDeltaRule.computeBetaAndDecayGate(
                            a, b, aLog, dtBias);
                    var ref = GatedDeltaRule.recurrentGatedDeltaRule(
                            qkv[0], qkv[1], qkv[2], gates[0], gates[1], recRef, true, true);
                    try (Tensor core = ref._1().reshape(batch * hv, dv)) {
                        double out = maxAbsDiff(fused, core);
                        assertTrue(out < 2e-2, "step " + step + " core max|diff|=" + out);
                    }
                    double conv = maxAbsDiff(convFused, convRef);
                    assertEquals(0.0, conv, 0.0, "conv state must match exactly at step " + step);
                    double rec = maxAbsDiff(recFused, recRef);
                    assertTrue(rec < 1e-3, "step " + step + " recurrent state max|diff|=" + rec);
                }
            }
        }
    }

    @Test
    public void testGivenVerifyWindowWhenFusedThenMatchesUnfusedWindowWithCheckpoints() {
        assumeTrue(smile.torch.smile_torch_h.smile_cuda_is_available() != 0, "CUDA not available");
        assumeTrue(Native.hasGatedDeltaVerifyWindowFused() && Native.hasGatedDeltaVerifyWindow(),
                "fused verify window not in libsmile_torch");
        int batch = 2, seq = 4, hk = 16, hv = 48, dk = 128, dv = 128, kernel = 4;
        int channels = 2 * hk * dk + hv * dv;
        Device cuda = Device.CUDA();
        ScalarType bf16 = ScalarType.BFloat16;
        try (Tensor weightB = Tensor.randn(channels, kernel).mul(0.5).to(cuda, bf16);
             Tensor aLog = Tensor.randn(hv).mul(0.3).to(cuda, ScalarType.Float);
             Tensor dtBias = Tensor.randn(hv).to(cuda, ScalarType.Float);
             Tensor convF = Tensor.randn(batch, channels, kernel - 1).to(cuda, bf16);
             Tensor convR = convF.copy();
             Tensor recF = Tensor.randn(batch, hv, dk, dv).mul(0.1).to(cuda, ScalarType.Float);
             Tensor recR = recF.copy();
             Tensor hidden = Tensor.randn(batch, seq, channels).to(cuda, bf16);
             Tensor a = Tensor.randn(batch, seq, hv).to(cuda, bf16);
             Tensor b = Tensor.randn(batch, seq, hv).to(cuda, bf16)) {
            int n = seq - 1;
            Tensor[] convCkF = new Tensor[n], recCkF = new Tensor[n], convCkR = new Tensor[n], recCkR = new Tensor[n];
            for (int i = 0; i < n; i++) {
                convCkF[i] = Tensor.zeros(batch, channels, kernel - 1).to(cuda, bf16);
                convCkR[i] = Tensor.zeros(batch, channels, kernel - 1).to(cuda, bf16);
                recCkF[i] = Tensor.zeros(batch, hv, dk, dv).to(cuda, ScalarType.Float);
                recCkR[i] = Tensor.zeros(batch, hv, dk, dv).to(cuda, ScalarType.Float);
            }
            Tensor fused = Native.gatedDeltaVerifyWindowFused(hidden, convF, weightB, a, b, aLog, dtBias, recF,
                    hk, hv, dk, dv, convCkF, recCkF);
            assertNotNull(fused, "fused verify window declined a supported layout");
            Tensor[] gates = GatedDeltaRule.computeBetaAndDecayGate(a, b, aLog, dtBias);
            Tensor ref = Native.gatedDeltaVerifyWindow(hidden, convR, weightB, gates[0], gates[1], recR,
                    hk, hv, dk, dv, true, convCkR, recCkR);
            assertNotNull(ref);
            assertEquals(java.util.Arrays.toString(ref.shape()), java.util.Arrays.toString(fused.shape()));
            double core = maxAbsDiff(fused, ref);
            assertTrue(core < 2e-2, "core max|diff|=" + core);
            assertEquals(0.0, maxAbsDiff(convF, convR), 0.0, "conv state");
            assertTrue(maxAbsDiff(recF, recR) < 1e-3, "recurrent state");
            for (int i = 0; i < n; i++) {
                assertEquals(0.0, maxAbsDiff(convCkF[i], convCkR[i]), 0.0, "conv checkpoint " + i);
                double rc = maxAbsDiff(recCkF[i], recCkR[i]);
                assertTrue(rc < 1e-3, "recurrent checkpoint " + i + " diff " + rc);
            }
        }
    }
}
