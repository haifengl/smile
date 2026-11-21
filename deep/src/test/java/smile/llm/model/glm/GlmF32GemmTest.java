/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * Smile is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Smile is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Smile.  If not, see <https://www.gnu.org/licenses/>.
 */
package smile.llm.model.glm;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import smile.deep.tensor.Device;
import smile.deep.tensor.ScalarType;
import smile.deep.tensor.Tensor;
import smile.torch.Aten;
import smile.torch.Native;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The 3xTF32 tensor-core GEMM ({@code smile_f32_gemm_x3}) against the float64 product: it must stay at fp32 accuracy
 * (plain TF32 would be ~1e-3), on the shapes of the HC projection and the router, with a row count that is not a multiple
 * of 16. Opt-in: {@code GLM_CUDA=true}; {@code GLM_BENCH=1} also prints the time against cuBLAS.
 */
public class GlmF32GemmTest {
    private static Tensor o(String op, Object... args) {
        return Aten.one(op, args);
    }

    private static double rel(Tensor a, Tensor b) {
        Tensor diff = o("aten::sub.Tensor", a, b);
        return o("aten::linalg_vector_norm", diff, 2.0).doubleValue() / o("aten::linalg_vector_norm", b, 2.0).doubleValue();
    }

    @Test
    public void testGivenSkinnyShapesThenFp32Accuracy() {
        Assumptions.assumeTrue("true".equals(System.getenv("GLM_CUDA")), "set GLM_CUDA=true");
        Assumptions.assumeTrue(Native.f32GemmX3Available(), "no x3 GEMM in the native library");
        Device dev = Device.CUDA((byte) 0);
        long[][] shapes = {{2048, 16384, 24}, {2000, 4096, 288}, {300, 16384, 24}, {257, 4096, 288}, {17, 64, 8}};
        for (long[] sh : shapes) {
            Tensor a = o("aten::randn", new long[]{sh[0], sh[1]}, ScalarType.Float).to(dev);
            Tensor w = o("aten::randn", new long[]{sh[2], sh[1]}, ScalarType.Float).to(dev);
            Tensor got = Native.f32GemmX3(a, w);
            Tensor want = o("aten::mm", o("aten::_cast_Double", a, false), o("aten::t", o("aten::_cast_Double", w, false)));
            double e = rel(o("aten::_cast_Double", got, false), want);
            Tensor tf = o("aten::linear", a, w);
            double ref = rel(o("aten::_cast_Double", tf, false), want);
            System.out.printf("M=%d K=%d N=%d: x3 rel err %.2e (cuBLAS fp32 %.2e)%n", sh[0], sh[1], sh[2], e, ref);
            // the tensor cores truncate while accumulating: ~5e-6 (cuBLAS fp32: ~5e-7, plain TF32: ~1e-3)
            assertTrue(e < 2e-5, "rel err " + e);
            Tensor again = Native.f32GemmX3(a, w);
            assertEquals(0.0, o("aten::max", o("aten::abs", o("aten::sub.Tensor", got, again))).doubleValue(), 0.0, "deterministic");
        }
        if (System.getenv("GLM_BENCH") != null) {
            for (long[] sh : new long[][]{{2048, 16384, 24}, {2048, 4096, 288}}) {
                Tensor a = o("aten::randn", new long[]{sh[0], sh[1]}, ScalarType.Float).to(dev);
                Tensor w = o("aten::randn", new long[]{sh[2], sh[1]}, ScalarType.Float).to(dev);
                for (int variant = 0; variant < 2; variant++) {
                    for (int i = 0; i < 5; i++) {
                        run(variant, a, w);
                    }
                    long t0 = System.nanoTime();
                    for (int i = 0; i < 20; i++) {
                        run(variant, a, w);
                    }
                    System.out.printf("M=%d K=%d N=%d %s: %.0f us%n", sh[0], sh[1], sh[2], variant == 0 ? "x3" : "cuBLAS",
                            (System.nanoTime() - t0) / 20e3);
                }
            }
        }
    }

    private static void run(int variant, Tensor a, Tensor w) {
        Tensor r = variant == 0 ? Native.f32GemmX3(a, w) : o("aten::linear", a, w);
        o("aten::sum", r).doubleValue();
        r.close();
    }
}
