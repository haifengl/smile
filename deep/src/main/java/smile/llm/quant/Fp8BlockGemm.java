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

import smile.deep.tensor.ScalarType;
import smile.deep.tensor.Tensor;
import smile.torch.Native;
import smile.util.AutoScope;

/**
 * DeepSeek-style W8A8 block FP8 matrix multiply: activations are quantized on the fly to E4M3 with one
 * float32 scale per 1x128 group, weights are E4M3 with one float32 scale per 128x128 tile.
 *
 * <p>The native path calls the Hopper block-scaled GEMM exactly as {@link Fp8BlockLinear} does. The
 * emulated path performs the same arithmetic (same activation quantizer, exact dequantization of both
 * operands, float32 matmul) on any CUDA GPU, so everything except the tensor-core call itself can be
 * validated without Hopper hardware.
 *
 * @author Haifeng Li
 */
public final class Fp8BlockGemm {
    private Fp8BlockGemm() { }

    /**
     * Computes {@code A @ W^T}.
     *
     * @param a        activations {@code [M, K]}, bfloat16 or float16, {@code K} a multiple of 128.
     * @param weight   FP8 weight {@code [N, K]}.
     * @param scaleInv float32 tile scales {@code [N/128, K/128]} ({@code W = fp8 * scaleInv}).
     * @param outDtype result dtype.
     * @param emulate  {@code true} to emulate the tensor-core GEMM with dequantization.
     * @return the product {@code [M, N]}; the caller owns it.
     */
    public static Tensor mm(Tensor a, Tensor weight, Tensor scaleInv, ScalarType outDtype, boolean emulate) {
        Tensor[] quant = Native.fp8Quant1x128(a);
        if (quant == null) {
            throw new IllegalStateException("smile_fp8_quant_1x128 unavailable; rebuild libsmile_torch with CUDA");
        }
        AutoScope scope = new AutoScope();
        Tensor.push(scope);
        try {
            Tensor out;
            if (emulate) {
                Tensor aDeq = dequantActivation(quant[0], quant[1]);
                Tensor wDeq = Fp8BlockDequant.dequant(weight, scaleInv, ScalarType.Float);
                out = aDeq.matmul(wDeq.transpose(0, 1)).to(outDtype);
            } else {
                out = Native.scaledMmV2(quant[0], weight, quant[1], scaleInv,
                        Native.SCALING_BLOCK_WISE_1X128, Native.SCALING_BLOCK_WISE_128X128, outDtype.code());
                if (out == null) {
                    throw new IllegalStateException(
                            "smile_scaled_mm_v2 failed; rebuild libsmile_torch with CUDA / Hopper FP8");
                }
            }
            out.promoteToParent();
            return out;
        } finally {
            Tensor.pop();
            quant[0].close();
            quant[1].close();
        }
    }

    /**
     * Runs the native block-scaled GEMM on a small random problem and compares it with the emulated
     * arithmetic. Use it before trusting the native path on hardware this code has not run on.
     *
     * @param device a CUDA device.
     * @return {@code null} when the native result matches, otherwise a description of the failure.
     */
    public static String selfCheck(smile.deep.tensor.Device device) {
        try (var noGrad = Tensor.noGradGuard()) {
            int m = 16;
            int k = 256;
            int n = 128;
            java.util.Random rnd = new java.util.Random(42);
            float[] av = new float[m * k];
            float[] wv = new float[n * k];
            for (int i = 0; i < av.length; i++) av[i] = (float) rnd.nextGaussian();
            for (int i = 0; i < wv.length; i++) wv[i] = (float) rnd.nextGaussian();
            float[] sv = new float[(n / 128) * (k / 128)];
            for (int i = 0; i < sv.length; i++) sv[i] = 0.5f + rnd.nextFloat();
            Tensor a = Tensor.of(av, m, k).to(device).to(ScalarType.BFloat16);
            Tensor w = Tensor.of(wv, n, k).to(device).to(ScalarType.Float8e4m3fn);
            Tensor s = Tensor.of(sv, n / 128, k / 128).to(device);
            Tensor nativeOut = mm(a, w, s, ScalarType.BFloat16, false);
            Tensor emulated = mm(a, w, s, ScalarType.BFloat16, true);
            float[] x = nativeOut.to(ScalarType.Float).to(smile.deep.tensor.Device.CPU()).copy().floatArray();
            float[] y = emulated.to(ScalarType.Float).to(smile.deep.tensor.Device.CPU()).copy().floatArray();
            double diff = 0;
            double norm = 0;
            for (int i = 0; i < x.length; i++) {
                diff += (x[i] - y[i]) * (double) (x[i] - y[i]);
                norm += y[i] * (double) y[i];
            }
            for (Tensor t : new Tensor[]{a, w, s, nativeOut, emulated}) t.close();
            double rel = Math.sqrt(diff / Math.max(norm, 1e-30));
            return rel < 0.03 && Double.isFinite(rel) ? null
                    : "native block FP8 GEMM disagrees with the reference (relative error " + rel + ")";
        } catch (RuntimeException e) {
            return "native block FP8 GEMM failed: " + e.getMessage();
        }
    }

    /** Expands {@code fp8 [M, K]} with group scales {@code [M, K/128]} back to float32. */
    static Tensor dequantActivation(Tensor fp8, Tensor scale) {
        long m = fp8.shape()[0];
        long k = fp8.shape()[1];
        Tensor expanded = scale.unsqueeze(2).expand(m, k / 128, 128).reshape(m, k);
        return fp8.to(ScalarType.Float).mul(expanded);
    }
}
