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

import java.io.File;
import org.junit.jupiter.api.Test;
import smile.deep.tensor.Device;
import smile.deep.tensor.SafeTensors;
import smile.deep.tensor.ScalarType;
import smile.deep.tensor.Tensor;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Opt-in GPU check of the vision tower against a reference made with vLLM's own RoPE kernels
 * ({@code vision_ref.py}). Environment: {@code MINIMAX_VISION_DIR} (config.json + the vision tensors) and
 * {@code MINIMAX_VISION_REF} (the reference safetensors file).
 *
 * @author Haifeng Li
 */
public class MiniMaxVisionTowerTest {
    @Test
    public void testGivenRandomPatchesWhenEncodedThenMatchesReference() throws Exception {
        String dir = System.getenv("MINIMAX_VISION_DIR");
        String ref = System.getenv("MINIMAX_VISION_REF");
        assumeTrue(dir != null && ref != null && smile.torch.smile_torch_h.smile_cuda_is_available() != 0,
                "MINIMAX_VISION_* not set or no CUDA");
        Device cuda = Device.CUDA((byte) 0);
        SafeTensors expected = SafeTensors.read(ref, Device.CPU());
        try (var noGrad = Tensor.noGradGuard()) {
            MiniMaxVisionTower tower = MiniMaxVisionTower.load(new File(dir), cuda, ScalarType.BFloat16);
            assertNotNull(tower);
            int[][] grids;
            try (Tensor g = expected.tensors().get("grids").to(ScalarType.Int32)) {
                int[] flat = g.intArray();
                grids = new int[flat.length / 3][3];
                for (int i = 0; i < grids.length; i++) {
                    System.arraycopy(flat, 3 * i, grids[i], 0, 3);
                }
            }
            try (Tensor pix = expected.tensors().get("pixel_values").to(cuda, ScalarType.BFloat16);
                 Tensor out = tower.forward(pix, grids);
                 Tensor cpu = out.to(Device.CPU()).to(ScalarType.Float)) {
                float[] got = cpu.floatArray();
                float[] want = expected.tensors().get("embeddings").floatArray();
                assertEquals(want.length, got.length);
                double num = 0;
                double den = 0;
                double maxAbs = 0;
                for (int i = 0; i < want.length; i++) {
                    double d = got[i] - want[i];
                    num += d * d;
                    den += (double) want[i] * want[i];
                    maxAbs = Math.max(maxAbs, Math.abs(d));
                }
                double rel = Math.sqrt(num / den);
                System.out.println("VISION relative L2 error " + rel + ", max abs error " + maxAbs
                        + ", rms " + Math.sqrt(den / want.length));
                assertTrue(rel < 0.03, "relative L2 error " + rel);
                String exact = System.getenv("MINIMAX_VISION_REF_FP32");
                if (exact != null) {
                    // The bf16 reference is itself ~0.017 from float32: that is the noise floor to compare with.
                    float[] truth = SafeTensors.read(exact, Device.CPU()).tensors().get("embeddings").floatArray();
                    double n2 = 0;
                    double d2 = 0;
                    for (int i = 0; i < truth.length; i++) {
                        double d = got[i] - truth[i];
                        n2 += d * d;
                        d2 += (double) truth[i] * truth[i];
                    }
                    double relTruth = Math.sqrt(n2 / d2);
                    System.out.println("VISION relative L2 error vs float32 reference " + relTruth);
                    assertTrue(relTruth < 0.03, "relative L2 error vs float32 " + relTruth);
                }
            }
        }
    }
}
