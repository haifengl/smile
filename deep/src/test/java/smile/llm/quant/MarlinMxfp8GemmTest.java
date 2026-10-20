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
import org.junit.jupiter.api.*;
import smile.deep.tensor.Device;
import smile.deep.tensor.ScalarType;
import smile.deep.tensor.Tensor;
import smile.torch.Native;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * CUDA-only check of the MXFP8 Marlin GEMM against an exact dequantize-then-matmul reference.
 *
 * @author Haifeng Li
 */
public class MarlinMxfp8GemmTest {
    private static boolean available() {
        return smile.torch.smile_torch_h.smile_cuda_is_available() != 0 && Native.marlinFp8Available();
    }

    /** Random MXFP8 weight on the device: fp8 values and E8M0 codes around 2^0. */
    static Tensor[] randomWeight(int n, int k, long seed, Device device) {
        Random rnd = new Random(seed);
        float[] w = new float[n * k];
        for (int i = 0; i < w.length; i++) {
            w[i] = 0.5f * (float) rnd.nextGaussian();
        }
        int[] codes = new int[n * (k / MxFp8.BLOCK)];
        for (int i = 0; i < codes.length; i++) {
            codes[i] = 122 + rnd.nextInt(8);
        }
        Tensor fp8 = Tensor.of(w, n, k).to(device).to(ScalarType.Float8e4m3fn);
        Tensor scale = Tensor.of(codes, n, k / MxFp8.BLOCK).to(device).to(ScalarType.UInt8);
        return new Tensor[]{fp8, scale};
    }

    private static void check(int m, int n, int k, long seed) {
        Device cuda = Device.CUDA((byte) 0);
        try (var noGrad = Tensor.noGradGuard()) {
            Tensor[] w = randomWeight(n, k, seed, cuda);
            Random rnd = new Random(seed + 1);
            float[] av = new float[m * k];
            for (int i = 0; i < av.length; i++) av[i] = (float) rnd.nextGaussian();
            Tensor a = Tensor.of(av, m, k).to(cuda).to(ScalarType.BFloat16);

            Tensor qweight = Native.marlinFp8RepackWeight(w[0]);
            Tensor qscale = Native.marlinFp8RepackScales(w[1]);
            Tensor out = Native.marlinFp8Gemm(a, qweight, qscale);
            assertArrayEquals(new long[]{m, n}, out.shape());

            Tensor dense = MxFp8.dequantize(w[0], w[1], ScalarType.Float);
            Tensor ref = a.to(ScalarType.Float).matmul(dense.transpose(0, 1));
            float[] got = out.to(ScalarType.Float).to(Device.CPU()).copy().floatArray();
            float[] exp = ref.to(Device.CPU()).copy().floatArray();
            double sumAbs = 0;
            for (float v : exp) sumAbs += Math.abs(v);
            double mean = sumAbs / exp.length;
            double maxErr = 0;
            for (int i = 0; i < exp.length; i++) {
                maxErr = Math.max(maxErr, Math.abs(got[i] - exp[i]));
            }
            // bf16 output rounding (2^-8 relative) plus float32 accumulation-order noise.
            assertTrue(maxErr / mean < 0.03, "M=" + m + " N=" + n + " K=" + k + ": max error " + maxErr
                    + " vs mean |ref| " + mean);
            for (Tensor t : new Tensor[]{w[0], w[1], a, qweight, qscale, out, dense, ref}) t.close();
        }
    }

    @Test
    public void testGivenSingleTokenWhenMultipliedThenMatchesDequantizedMatmul() {
        assumeTrue(available(), "needs CUDA and the MXFP8 Marlin kernels");
        check(1, 512, 1024, 1);
        check(1, 6144, 3072, 2);
    }

    @Test
    public void testGivenVariousBatchSizesWhenMultipliedThenMatchesDequantizedMatmul() {
        assumeTrue(available(), "needs CUDA and the MXFP8 Marlin kernels");
        for (int m : new int[]{2, 5, 8, 16, 17, 33, 64, 100, 257}) {
            check(m, 1024, 2048, 10 + m);
        }
    }

    @Test
    public void testGivenModelShapesWhenMultipliedThenMatchesDequantizedMatmul() {
        assumeTrue(available(), "needs CUDA and the MXFP8 Marlin kernels");
        check(7, 8192, 6144, 3);   // q_proj
        check(7, 512, 6144, 4);    // k_proj / v_proj
        check(7, 128, 6144, 5);    // index_k_proj
        check(7, 6144, 8192, 6);   // o_proj
        check(7, 3072, 6144, 7);   // expert w1/w3
        check(7, 6144, 3072, 8);   // expert w2
    }

    @Test
    public void testGivenLinearLayerWhenForwardOnBatchedInputThenMatchesDenseAndKeepsShape() {
        assumeTrue(available(), "needs CUDA and the MXFP8 Marlin kernels");
        Device cuda = Device.CUDA((byte) 0);
        int n = 512;
        int k = 1024;
        try (var noGrad = Tensor.noGradGuard();
             MxFp8MarlinLinear layer = new MxFp8MarlinLinear(k, n)) {
            assertThrows(IllegalStateException.class, () -> layer.forward(Tensor.zeros(1, k)));
            Tensor[] w = randomWeight(n, k, 9, cuda);
            layer.setWeight(w[0], w[1]);
            assertTrue(layer.isLoaded());

            Random rnd = new Random(10);
            float[] av = new float[2 * 3 * k];
            for (int i = 0; i < av.length; i++) av[i] = (float) rnd.nextGaussian();
            Tensor a = Tensor.of(av, 2, 3, k).to(cuda).to(ScalarType.BFloat16);
            Tensor out = layer.forward(a);
            assertArrayEquals(new long[]{2, 3, n}, out.shape());

            Tensor dense = MxFp8.dequantize(w[0], w[1], ScalarType.Float);
            Tensor ref = a.to(ScalarType.Float).reshape(6, k).matmul(dense.transpose(0, 1));
            float[] got = out.to(ScalarType.Float).reshape(6, n).to(Device.CPU()).copy().floatArray();
            float[] exp = ref.to(Device.CPU()).copy().floatArray();
            double mean = 0;
            for (float v : exp) mean += Math.abs(v) / exp.length;
            for (int i = 0; i < exp.length; i++) {
                assertTrue(Math.abs(got[i] - exp[i]) <= 0.0125 * Math.abs(exp[i]) + 0.01 * mean, "element " + i);
            }
            assertThrows(IllegalArgumentException.class, () -> layer.forward(a.to(ScalarType.Float)));
        }
    }
}
