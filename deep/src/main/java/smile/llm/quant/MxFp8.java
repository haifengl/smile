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
import smile.util.AutoScope;

/**
 * MXFP8 (OCP microscaling) weights: FP8 E4M3 elements with one shared E8M0 scale per 32
 * consecutive elements along the reduction dimension.
 *
 * <p>An E8M0 byte {@code s} encodes the power of two {@code 2^(s - 127)}; {@code 255} is NaN.
 * A weight {@code W[n][k]} is {@code fp8[n][k] * 2^(scale[n][k / 32] - 127)}. Checkpoints store the
 * scales as {@code <name>.weight_scale_inv} (U8, {@code [N, K / 32]}).
 *
 * <p>This is the portable reference: it expands a weight to a dense tensor. Production kernels keep
 * the FP8 bytes and apply the scales in the GEMM.
 *
 * @author Haifeng Li
 */
public final class MxFp8 {
    /** Elements sharing one scale. */
    public static final int BLOCK = 32;

    private MxFp8() { }

    /**
     * Returns the value of an E8M0 scale byte.
     *
     * @param code the stored byte, {@code 0..255}.
     * @return {@code 2^(code - 127)}, or NaN for {@code 255}.
     */
    public static float e8m0ToFloat(int code) {
        if (code == 255) {
            return Float.NaN;
        }
        return (float) Math.scalb(1.0, code - 127);
    }

    /**
     * Quantizes a dense weight to MXFP8: E4M3 values with one power-of-two (E8M0) scale per 32 inputs, so that
     * {@code dequantize} reproduces it to E4M3 rounding. Rows are padded with zeros to a multiple of 64 (the Marlin
     * tile); the caller slices the product's columns back to {@code N}.
     *
     * @param weight bf16 or float32 weight {@code [N, K]} on the device; {@code K} a multiple of 128.
     * @return {@code {fp8 [Npad, K], e8m0 UInt8 [Npad, K / 32]}}; the caller owns both.
     */
    public static Tensor[] quantize(Tensor weight) {
        long n = weight.shape()[0], k = weight.shape()[1];
        long npad = (n + 63) / 64 * 64;
        Tensor q8 = Tensor.empty(new Tensor.Options().dtype(ScalarType.Float8e4m3fn).device(weight.device()), npad, k);
        Tensor code = Tensor.empty(new Tensor.Options().dtype(ScalarType.UInt8).device(weight.device()), npad, k / BLOCK);
        final long chunk = 8192;                    // rows per pass: bounds the float32 temporaries to ~0.3 GB
        for (long r0 = 0; r0 < npad; r0 += chunk) {
            long rows = Math.min(chunk, npad - r0);
            AutoScope scope = new AutoScope();
            Tensor.push(scope);
            try {
                long real = Math.max(0, Math.min(rows, n - r0));
                Tensor wf = smile.torch.Aten.one("aten::_to_copy", smile.torch.Aten.one("aten::narrow", weight, 0L, r0, real), ScalarType.Float);
                if (real < rows) {
                    Tensor zeros = Tensor.zeros(new Tensor.Options().dtype(ScalarType.Float).device(weight.device()), rows - real, k);
                    wf = smile.torch.Aten.one("aten::cat", new Tensor[]{wf, zeros}, 0L);
                }
                Tensor g = smile.torch.Aten.one("aten::reshape", wf, new long[]{rows, k / BLOCK, BLOCK});
                Tensor amax = smile.torch.Aten.one("aten::amax", smile.torch.Aten.one("aten::abs", g), new long[]{-1}, false);
                Tensor ratio = smile.torch.Aten.one("aten::clamp_min",
                        smile.torch.Aten.one("aten::mul.Scalar", amax, 1.0 / 448.0), Math.pow(2, -126));
                Tensor e = smile.torch.Aten.one("aten::clamp",
                        smile.torch.Aten.one("aten::ceil", smile.torch.Aten.one("aten::log2", ratio)), -127.0, 127.0);
                Tensor scale = smile.torch.Aten.one("aten::exp2", e);
                Tensor q = smile.torch.Aten.one("aten::clamp", smile.torch.Aten.one("aten::div.Tensor", g,
                        smile.torch.Aten.one("aten::unsqueeze", scale, -1L)), -448.0, 448.0);
                Tensor qc = smile.torch.Aten.one("aten::reshape", q, new long[]{rows, k}).to(ScalarType.Float8e4m3fn);
                Tensor cc = smile.torch.Aten.one("aten::add.Scalar", e, 127.0).to(ScalarType.UInt8);
                smile.torch.Aten.one("aten::copy_", smile.torch.Aten.one("aten::narrow", q8, 0L, r0, rows), qc);
                smile.torch.Aten.one("aten::copy_", smile.torch.Aten.one("aten::narrow", code, 0L, r0, rows), cc);
            } finally {
                Tensor.pop();
            }
        }
        return new Tensor[]{q8, code};
    }

    /**
     * Expands an MXFP8 weight to a dense tensor.
     *
     * @param weight FP8 E4M3 weight {@code [N, K]} with {@code K} a multiple of {@link #BLOCK}.
     * @param scale  E8M0 scales as {@code UInt8} {@code [N, K / 32]}, on the same device as {@code weight}.
     * @param dtype  result dtype.
     * @return the dense weight {@code [N, K]}; the caller owns it.
     * @throws IllegalArgumentException if the shapes are not an MXFP8 layout.
     */
    public static Tensor dequantize(Tensor weight, Tensor scale, ScalarType dtype) {
        long[] w = weight.shape();
        long[] s = scale.shape();
        if (w.length != 2 || s.length != 2 || w[1] % BLOCK != 0 || s[0] != w[0] || s[1] != w[1] / BLOCK) {
            throw new IllegalArgumentException("not an MXFP8 layout: weight " + java.util.Arrays.toString(w)
                    + ", scale " + java.util.Arrays.toString(s) + " (expected scale [N, K/" + BLOCK + "])");
        }
        float[] powers = new float[256];
        for (int i = 0; i < powers.length; i++) {
            powers[i] = e8m0ToFloat(i);
        }
        AutoScope scope = new AutoScope();
        Tensor.push(scope);
        try {
            Tensor table = Tensor.of(powers, 256).to(weight.device());
            Tensor factors = table.get(scale.to(ScalarType.Int64));                // [N, K/32]
            Tensor expanded = factors.unsqueeze(2).expand(w[0], s[1], BLOCK).reshape(w[0], w[1]);
            Tensor out = weight.to(ScalarType.Float).mul(expanded).to(dtype);
            out.promoteToParent();
            return out;
        } finally {
            Tensor.pop();
        }
    }
}
