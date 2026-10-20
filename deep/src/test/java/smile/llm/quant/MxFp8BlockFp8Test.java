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
 * CUDA-only tests of the MXFP8 to block-FP8 conversion and of {@link MxFp8BlockLinear}. The converter
 * and the emulated GEMM run on any CUDA GPU; the native GEMM comparison needs Hopper.
 *
 * @author Haifeng Li
 */
public class MxFp8BlockFp8Test {
    private static boolean available() {
        return smile.torch.smile_torch_h.smile_cuda_is_available() != 0 && Native.mxfp8ToBlockFp8Available()
                && Native.fp8Quant1x128Available();
    }

    private static Tensor[] randomWeight(int n, int k, int codeLo, int codeSpan, long seed, Device device) {
        Random rnd = new Random(seed);
        float[] w = new float[n * k];
        for (int i = 0; i < w.length; i++) w[i] = 0.5f * (float) rnd.nextGaussian();
        int[] codes = new int[n * (k / MxFp8.BLOCK)];
        for (int i = 0; i < codes.length; i++) codes[i] = codeLo + rnd.nextInt(codeSpan);
        return new Tensor[]{Tensor.of(w, n, k).to(device).to(ScalarType.Float8e4m3fn),
                Tensor.of(codes, n, k / MxFp8.BLOCK).to(device).to(ScalarType.UInt8)};
    }

    /** Dense value of a block-FP8 weight: fp8 * tile scale. */
    private static Tensor expandBlock(Tensor fp8, Tensor scaleInv) {
        return Fp8BlockDequant.dequant(fp8, scaleInv, ScalarType.Float);
    }

    @Test
    public void testGivenEqualScalesInATileWhenConvertedThenDenseValuesAreIdentical() {
        assumeTrue(available(), "needs CUDA and the converter kernel");
        Device cuda = Device.CUDA((byte) 0);
        try (var noGrad = Tensor.noGradGuard()) {
            Tensor[] w = randomWeight(256, 512, 126, 1, 1, cuda);   // every group has the same exponent
            Tensor[] b = Native.mxfp8ToBlockFp8(w[0], w[1]);
            assertArrayEquals(new long[]{256, 512}, b[0].shape());
            assertArrayEquals(new long[]{2, 4}, b[1].shape());
            Tensor ref = MxFp8.dequantize(w[0], w[1], ScalarType.Float);
            Tensor got = expandBlock(b[0], b[1]);
            assertArrayEquals(ref.to(Device.CPU()).copy().floatArray(), got.to(Device.CPU()).copy().floatArray(), 0f);
            for (Tensor t : new Tensor[]{w[0], w[1], b[0], b[1], ref, got}) t.close();
        }
    }

    @Test
    public void testGivenClusteredScalesWhenConvertedThenNormErrorIsNegligible() {
        assumeTrue(available(), "needs CUDA and the converter kernel");
        Device cuda = Device.CUDA((byte) 0);
        try (var noGrad = Tensor.noGradGuard()) {
            // Exponents within 3 of each other. Only elements that become subnormal after the shift change.
            Tensor[] w = randomWeight(256, 512, 124, 4, 1, cuda);
            Tensor[] b = Native.mxfp8ToBlockFp8(w[0], w[1]);
            Tensor ref = MxFp8.dequantize(w[0], w[1], ScalarType.Float);
            Tensor got = expandBlock(b[0], b[1]);
            float[] r = ref.to(Device.CPU()).copy().floatArray();
            float[] g = got.to(Device.CPU()).copy().floatArray();
            double diff = 0, norm = 0;
            for (int i = 0; i < r.length; i++) {
                diff += (r[i] - g[i]) * (double) (r[i] - g[i]);
                norm += r[i] * (double) r[i];
            }
            assertTrue(Math.sqrt(diff / norm) < 2e-3, "relative error " + Math.sqrt(diff / norm));
            for (Tensor t : new Tensor[]{w[0], w[1], b[0], b[1], ref, got}) t.close();
        }
    }

    @Test
    public void testGivenWideScaleSpreadWhenConvertedThenErrorStaysSmall() {
        assumeTrue(available(), "needs CUDA and the converter kernel");
        Device cuda = Device.CUDA((byte) 0);
        try (var noGrad = Tensor.noGradGuard()) {
            Tensor[] w = randomWeight(128, 256, 110, 24, 2, cuda);   // up to 23 octaves apart
            Tensor[] b = Native.mxfp8ToBlockFp8(w[0], w[1]);
            Tensor ref = MxFp8.dequantize(w[0], w[1], ScalarType.Float);
            Tensor got = expandBlock(b[0], b[1]);
            float[] r = ref.to(Device.CPU()).copy().floatArray();
            float[] g = got.to(Device.CPU()).copy().floatArray();
            double diff = 0, norm = 0;
            for (int i = 0; i < r.length; i++) {
                diff += (r[i] - g[i]) * (double) (r[i] - g[i]);
                norm += r[i] * (double) r[i];
            }
            // The weight norm is dominated by the largest-scale groups, which convert exactly.
            assertTrue(Math.sqrt(diff / norm) < 0.02, "relative error " + Math.sqrt(diff / norm));
            for (Tensor t : new Tensor[]{w[0], w[1], b[0], b[1], ref, got}) t.close();
        }
    }

    @Test
    public void testGivenStackedExpertsWhenConvertedThenEachMatrixMatchesTheSingleCall() {
        assumeTrue(available(), "needs CUDA and the converter kernel");
        Device cuda = Device.CUDA((byte) 0);
        try (var noGrad = Tensor.noGradGuard()) {
            int e = 3, n = 128, k = 256;
            Tensor[] w = randomWeight(e * n, k, 122, 6, 3, cuda);
            Tensor fp8 = w[0].reshape(e, n, k);
            Tensor sc = w[1].reshape(e, n, k / MxFp8.BLOCK);
            Tensor[] stacked = Native.mxfp8ToBlockFp8(fp8, sc);
            assertArrayEquals(new long[]{e, n, k}, stacked[0].shape());
            assertArrayEquals(new long[]{e, 1, 2}, stacked[1].shape());
            for (int i = 0; i < e; i++) {
                try (var idx = smile.deep.tensor.Index.of(i);
                     Tensor wi = fp8.get(idx).contiguous(); Tensor si = sc.get(idx).contiguous()) {
                    Tensor[] single = Native.mxfp8ToBlockFp8(wi, si);
                    float[] a = single[0].to(ScalarType.Float).to(Device.CPU()).copy().floatArray();
                    try (Tensor part = stacked[0].get(idx)) {
                        float[] b = part.to(ScalarType.Float).to(Device.CPU()).copy().floatArray();
                        assertArrayEquals(a, b, 0f, "matrix " + i);
                    }
                    single[0].close();
                    single[1].close();
                }
            }
        }
    }

    @Test
    public void testGivenEmulatedLayerWhenForwardThenMatchesActivationRoundedDenseMatmul() {
        assumeTrue(available(), "needs CUDA and the converter kernel");
        Device cuda = Device.CUDA((byte) 0);
        int n = 256, k = 512;
        try (var noGrad = Tensor.noGradGuard();
             MxFp8BlockLinear layer = new MxFp8BlockLinear(k, n, true)) {
            Tensor[] w = randomWeight(n, k, 123, 5, 4, cuda);
            layer.setWeight(w[0], w[1]);
            Random rnd = new Random(5);
            float[] av = new float[2 * 5 * k];
            for (int i = 0; i < av.length; i++) av[i] = (float) rnd.nextGaussian();
            Tensor a = Tensor.of(av, 2, 5, k).to(cuda).to(ScalarType.BFloat16);
            Tensor out = layer.forward(a);
            assertArrayEquals(new long[]{2, 5, n}, out.shape());

            // Reference: exact dense weight, activations rounded by the same 1x128 FP8 quantizer.
            Tensor[] q = Native.fp8Quant1x128(a.reshape(10, k));
            Tensor aq = Fp8BlockGemm.dequantActivation(q[0], q[1]);
            Tensor dense = MxFp8.dequantize(w[0], w[1], ScalarType.Float);
            Tensor ref = aq.matmul(dense.transpose(0, 1));
            float[] got = out.to(ScalarType.Float).reshape(10, n).to(Device.CPU()).copy().floatArray();
            float[] exp = ref.to(Device.CPU()).copy().floatArray();
            double mean = 0;
            for (float v : exp) mean += Math.abs(v) / exp.length;
            for (int i = 0; i < exp.length; i++) {
                assertTrue(Math.abs(got[i] - exp[i]) <= 0.0125 * Math.abs(exp[i]) + 0.01 * mean, "element " + i);
            }
        }
    }

    @Test
    public void testGivenHopperWhenNativeGemmThenMatchesEmulation() {
        assumeTrue(available() && Native.scaledMmV2Available(), "needs CUDA and the native block FP8 GEMM");
        assumeTrue(Native.cudaComputeCapability(0)[0] >= 9, "needs a Hopper (sm_90) or newer GPU");
        String failure = Fp8BlockGemm.selfCheck(Device.CUDA((byte) 0));
        assertNull(failure, failure);

        // Shapes and scale layouts of the real model.
        Device cuda = Device.CUDA((byte) 0);
        try (var noGrad = Tensor.noGradGuard()) {
            for (int[] shape : new int[][]{{8192, 6144}, {512, 6144}, {6144, 3072}, {128, 6144}}) {
                int n = shape[0], k = shape[1];
                try (MxFp8BlockLinear nativeLayer = new MxFp8BlockLinear(k, n, false);
                     MxFp8BlockLinear emulated = new MxFp8BlockLinear(k, n, true)) {
                    Tensor[] w = randomWeight(n, k, 123, 5, n + k, cuda);
                    nativeLayer.setWeight(w[0], w[1]);
                    emulated.setWeight(w[0], w[1]);
                    for (int m : new int[]{1, 7, 64, 300}) {
                        Random rnd = new Random(m);
                        float[] av = new float[m * k];
                        for (int i = 0; i < av.length; i++) av[i] = (float) rnd.nextGaussian();
                        Tensor a = Tensor.of(av, m, k).to(cuda).to(ScalarType.BFloat16);
                        float[] x = nativeLayer.forward(a).to(ScalarType.Float).to(Device.CPU()).copy().floatArray();
                        float[] y = emulated.forward(a).to(ScalarType.Float).to(Device.CPU()).copy().floatArray();
                        double diff = 0, norm = 0;
                        for (int i = 0; i < x.length; i++) {
                            diff += (x[i] - y[i]) * (double) (x[i] - y[i]);
                            norm += y[i] * (double) y[i];
                        }
                        assertTrue(Math.sqrt(diff / norm) < 0.02,
                                "N=" + n + " K=" + k + " M=" + m + ": relative error " + Math.sqrt(diff / norm));
                    }
                }
            }
        }
    }
}
