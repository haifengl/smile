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

import java.util.Random;
import org.junit.jupiter.api.Test;
import smile.deep.tensor.Device;
import smile.deep.tensor.ScalarType;
import smile.deep.tensor.Tensor;
import smile.torch.Native;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * CUDA-only check of the block-FP8 Marlin GEMM (Qwen/DeepSeek 128x128 scales on Ampere) against a
 * dequantize-then-matmul reference.
 *
 * @author Haifeng Li
 */
public class Fp8BlockMarlinTest {
    private static boolean available() {
        return smile.torch.smile_torch_h.smile_cuda_is_available() != 0 && Native.marlinFp8Available()
                && Native.marlinFp8BlockScalesAvailable();
    }

    /** Random FP8 weight and float32 block scales around {@code scaleMean} on the device. */
    private static Tensor[] randomWeight(int n, int k, double scaleMean, long seed, Device device) {
        Random rnd = new Random(seed);
        float[] w = new float[n * k];
        for (int i = 0; i < w.length; i++) {
            w[i] = 100f * (float) rnd.nextGaussian();   // spans the E4M3 range
        }
        float[] s = new float[(n / 128) * (k / 128)];
        for (int i = 0; i < s.length; i++) {
            s[i] = (float) (scaleMean * (0.25 + 1.5 * rnd.nextDouble()));
        }
        Tensor fp8 = Tensor.of(w, n, k).to(device).to(ScalarType.Float8e4m3fn);
        Tensor scale = Tensor.of(s, n / 128, k / 128).to(device);
        return new Tensor[]{fp8, scale};
    }

    private static void check(int m, int n, int k, double scaleMean, long seed) {
        Device cuda = Device.CUDA((byte) 0);
        try (var noGrad = Tensor.noGradGuard()) {
            Tensor[] w = randomWeight(n, k, scaleMean, seed, cuda);
            Random rnd = new Random(seed + 1);
            float[] av = new float[m * k];
            for (int i = 0; i < av.length; i++) av[i] = (float) rnd.nextGaussian();
            Tensor a = Tensor.of(av, m, k).to(cuda).to(ScalarType.BFloat16);

            // The kernel sees the scales rounded to bfloat16: the reference does too.
            Tensor rounded = w[1].to(ScalarType.BFloat16).to(ScalarType.Float);
            Tensor dense = Fp8BlockDequant.dequant(w[0], rounded, ScalarType.Float);
            Tensor ref = a.to(ScalarType.Float).matmul(dense.transpose(0, 1));

            Tensor qweight = Native.marlinFp8RepackWeight(w[0]);
            Tensor qscale = Native.marlinFp8RepackBlockScales(w[1]);
            assertEquals(ScalarType.BFloat16, qscale.dtype());
            assertArrayEquals(new long[]{k / 128, n}, qscale.shape());
            Tensor out = Native.marlinFp8Gemm(a, qweight, qscale);
            assertArrayEquals(new long[]{m, n}, out.shape());

            float[] got = out.to(ScalarType.Float).to(Device.CPU()).copy().floatArray();
            float[] exp = ref.to(Device.CPU()).copy().floatArray();
            double sumAbs = 0;
            for (float v : exp) sumAbs += Math.abs(v);
            double mean = sumAbs / exp.length;
            double maxErr = 0;
            for (int i = 0; i < exp.length; i++) {
                maxErr = Math.max(maxErr, Math.abs(got[i] - exp[i]));
            }
            // bf16 output rounding plus float32 accumulation-order noise
            assertTrue(maxErr / mean < 0.03, "M=" + m + " N=" + n + " K=" + k + " scale~" + scaleMean
                    + ": max error " + maxErr + " vs mean |ref| " + mean);
            for (Tensor t : new Tensor[]{w[0], w[1], a, rounded, dense, ref, qweight, qscale, out}) t.close();
        }
    }

    @Test
    public void testGivenQuantizationScalesWhenMultipliedThenMatchesDequantizedMatmul() {
        assumeTrue(available(), "needs CUDA and the Marlin block-FP8 kernels");
        check(1, 512, 1024, 0.01, 1);
        check(5, 1024, 2048, 0.002, 2);
        check(64, 1024, 2048, 0.05, 3);
        check(257, 2048, 1024, 0.01, 4);
    }

    @Test
    public void testGivenLargeScalesWhenMultipliedThenExponentBiasDoesNotOverflow() {
        assumeTrue(available(), "needs CUDA and the Marlin block-FP8 kernels");
        check(3, 512, 1024, 20.0, 5);       // far above real quantization scales, below the 2^8 guard
    }

    @Test
    public void testGivenQwenShapesWhenMultipliedThenMatchesDequantizedMatmul() {
        assumeTrue(available(), "needs CUDA and the Marlin block-FP8 kernels");
        check(7, 17408, 5120, 0.004, 6);    // gate/up_proj
        check(7, 5120, 17408, 0.004, 7);    // down_proj
        check(7, 10240, 5120, 0.004, 8);    // linear-attention in_proj_qkv
        check(7, 6144, 5120, 0.004, 9);     // in_proj_z
        check(7, 5120, 6144, 0.004, 10);    // out_proj
        check(7, 12288, 5120, 0.004, 11);   // full-attention q_proj (query + gate)
        check(7, 1024, 5120, 0.004, 12);    // k_proj / v_proj
    }

    @Test
    public void testGivenLayerWhenForwardOnBatchedInputThenKeepsShapeAndRejectsNonBf16() {
        assumeTrue(available(), "needs CUDA and the Marlin block-FP8 kernels");
        Device cuda = Device.CUDA((byte) 0);
        try (var noGrad = Tensor.noGradGuard()) {
            Tensor[] w = randomWeight(512, 1024, 0.01, 21, cuda);
            try (Fp8BlockMarlinLinear layer = new Fp8BlockMarlinLinear(w[0], w[1], null);
                 Tensor x = Tensor.ones(2, 3, 1024).to(cuda).to(ScalarType.BFloat16);
                 Tensor y = layer.forward(x);
                 Tensor half = Tensor.ones(2, 1024).to(cuda).to(ScalarType.Half)) {
                assertArrayEquals(new long[]{2, 3, 512}, y.shape());
                assertThrows(IllegalArgumentException.class, () -> layer.forward(half));
            }
            Tensor[] bad = randomWeight(512, 1024, 0.01, 22, cuda);
            Tensor wrongScale = Tensor.ones(3, 3).to(cuda);
            assertThrows(IllegalArgumentException.class, () -> new Fp8BlockMarlinLinear(bad[0], wrongScale, null));
        }
    }
}
