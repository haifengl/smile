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
 * The fused residual-add + Gemma RMSNorm and SiLU-mul ops must match the eager chains they replace.
 *
 * @author Haifeng Li
 */
public class FusedAddNormSwigluTest {
    private static double maxAbsDiff(Tensor a, Tensor b) {
        try (Tensor diff = a.to(ScalarType.Float).sub(b.to(ScalarType.Float)).abs();
             Tensor cpu = diff.to(Device.CPU())) {
            double m = 0;
            for (float x : cpu.contiguous().floatArray()) {
                m = Math.max(m, x);
            }
            return m;
        }
    }

    @Test
    public void testGivenResidualAndMixerWhenAddNormFusedThenMatchesEager() {
        assumeTrue(smile.torch.smile_torch_h.smile_cuda_is_available() != 0, "CUDA not available");
        Device cuda = Device.CUDA();
        for (int rows : new int[]{1, 4}) {
            try (Tensor x = Tensor.randn(rows, 1, 5120).to(cuda, ScalarType.BFloat16);
                 Tensor r = Tensor.randn(rows, 1, 5120).to(cuda, ScalarType.BFloat16);
                 Tensor w = Tensor.randn(5120).mul(0.1).to(cuda, ScalarType.BFloat16)) {
                Tensor[] fused = Native.addGemmaRmsNorm(x, r, w, 1e-6);
                assumeTrue(fused != null, "fused add+norm not in libsmile_torch");
                try (Tensor sum = x.add(r);
                     Tensor ref = Native.gemmaRmsNorm(sum, w, 1e-6)) {
                    assertEquals(0.0, maxAbsDiff(fused[1], sum), 0.0, "sum must equal the eager add");
                    assertTrue(maxAbsDiff(fused[0], ref) == 0.0, "normalized output must equal the unfused norm");
                } finally {
                    fused[0].close();
                    fused[1].close();
                }
            }
        }
    }

    @Test
    public void testGivenGateAndUpWhenSiluMulFusedThenMatchesEagerWithinBf16() {
        assumeTrue(smile.torch.smile_torch_h.smile_cuda_is_available() != 0, "CUDA not available");
        Device cuda = Device.CUDA();
        try (Tensor g = Tensor.randn(3, 1, 17408).mul(2).to(cuda, ScalarType.BFloat16);
             Tensor u = Tensor.randn(3, 1, 17408).to(cuda, ScalarType.BFloat16)) {
            Tensor fused = Native.siluMul(g, u);
            assumeTrue(fused != null, "fused silu-mul not in libsmile_torch");
            try (Tensor gf = g.to(ScalarType.Float);
                 Tensor act = new smile.deep.activation.SiLU(false).forward(gf);
                 Tensor ref = act.mul(u.to(ScalarType.Float))) {
                assertNotNull(fused);
                try (Tensor d = fused.to(ScalarType.Float).sub(ref).abs();
                     Tensor scale = ref.abs().add(1.0);
                     Tensor rel = d.div(scale);
                     Tensor cpu = rel.to(Device.CPU())) {
                    double err = 0;
                    for (float v : cpu.contiguous().floatArray()) {
                        err = Math.max(err, v);
                    }
                    // one bf16 rounding of the product: relative error <= 2^-8
                    assertTrue(err < 5e-3, "silu-mul max relative diff vs float reference = " + err);
                }
            } finally {
                fused.close();
            }
        }
    }
}
