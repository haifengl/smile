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
package smile.llm.model.inkling;

import smile.deep.tensor.ScalarType;
import smile.deep.tensor.Tensor;
import smile.torch.Native;

import static smile.llm.model.inkling.Ix.*;

/**
 * A bf16 weight quantized to MXFP8 (E4M3 values, one power-of-two scale per 32 inputs) and packed for the Marlin
 * weight-only GEMM: the decode step reads half the bytes of the bf16 weight. The model's own expert weights of the last
 * layers use the same format. Rows are padded with zeros to a multiple of 64 (the kernel's tile).
 *
 * @author Haifeng Li
 */
final class InklingFp8 {
    private static final double MAX = 448.0;

    private final Tensor qweight, scales;
    /** Logical and padded output sizes. */
    final int n, npad;

    private InklingFp8(Tensor qweight, Tensor scales, int n, int npad) {
        this.qweight = qweight;
        this.scales = scales;
        this.n = n;
        this.npad = npad;
    }

    /** MXFP8 copies of the dense weights are built unless {@code SMILE_INKLING_FP8=0} or the kernel is missing. */
    static boolean enabled() {
        return !"0".equals(System.getenv("SMILE_INKLING_FP8")) && available();
    }

    /** Whether the Marlin MXFP8 kernel is compiled in. */
    static boolean available() {
        return Native.marlinFp8Available();
    }

    /**
     * Quantizes and packs a weight.
     *
     * @param w bf16 {@code [N, K]} on the device; {@code K % 128 == 0}.
     * @return the packed weight (kept out of every scope).
     */
    static InklingFp8 quantize(Tensor w) {
        int n = (int) w.size(0), k = (int) w.size(1);
        int npad = (n + 63) / 64 * 64;
        Tensor[] r = new Tensor[2];
        stepScope(() -> {
            Tensor wf = f32(w);
            if (npad != n) {
                wf = cat(0, wf, zeros(w.device(), ScalarType.Float, npad - n, k));
            }
            Tensor g = reshape(wf, npad, k / 32, 32);
            Tensor amax = o("aten::amax", o("aten::abs", g), new long[]{-1}, false);
            Tensor ratio = o("aten::clamp_min", mul(amax, 1.0 / MAX), Math.pow(2, -126));
            Tensor e = o("aten::clamp", o("aten::ceil", o("aten::log2", ratio)), -127.0, 127.0);
            Tensor scale = o("aten::exp2", e);
            Tensor q = o("aten::clamp", o("aten::div.Tensor", g, unsqueeze(scale, -1)), -MAX, MAX);
            Tensor q8 = reshape(q, npad, k).to(ScalarType.Float8e4m3fn);
            Tensor code = o("aten::add.Scalar", e, 127.0).to(ScalarType.UInt8);
            r[0] = keep(Native.marlinFp8RepackWeight(q8));
            r[1] = keep(Native.marlinFp8RepackScales(code));
        });
        return new InklingFp8(r[0], r[1], n, npad);
    }

    /**
     * Concatenates the outputs of several weights that share their input: parts must have {@code n % 64 == 0}.
     */
    static InklingFp8 quantize(Tensor... parts) {
        return quantize(cat(0, parts));
    }

    /**
     * {@code x w^T}.
     *
     * @param x bf16 {@code [M, K]}.
     * @return bf16 {@code [M, npad]} (the columns beyond {@code n} are zero).
     */
    Tensor forward(Tensor x) {
        return Native.marlinFp8Gemm(x, qweight, scales);
    }

    private static void stepScope(Runnable r) {
        smile.util.AutoScope scope = new smile.util.AutoScope();
        Tensor.push(scope);
        try {
            r.run();
        } finally {
            Tensor.pop();
        }
    }
}
