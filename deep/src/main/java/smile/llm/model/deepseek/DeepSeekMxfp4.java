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
package smile.llm.model.deepseek;

import smile.deep.tensor.ScalarType;
import smile.deep.tensor.Tensor;
import smile.torch.Aten;

/**
 * Converts the checkpoint's MXFP4 routed-expert weights (E2M1, one power-of-two E8M0 scale per 32 inputs) to the
 * NVFP4 layout of the Marlin MoE kernel (E2M1, one E4M3 scale per 16 inputs, plus a float global scale: the weight is
 * {@code fp4 * scale / global}). The nibble order is the same, so the packed bytes are reused as they are.
 *
 * <p>An E8M0 scale {@code 2^k} becomes the E4M3 scale {@code 2^(k+5)} with global scale 32. E4M3 holds powers of two
 * from 2^-9 to 2^8 exactly, so the conversion is lossless for {@code -14 <= k <= 3}; the released checkpoint uses
 * {@code -9..-1} in the layers checked so far, and {@link #toNvfp4} throws when a scale falls outside the range.
 *
 * @author Haifeng Li
 */
final class DeepSeekMxfp4 {
    /** The global scale paired with the converted scales. */
    static final float GLOBAL = 32f;
    private static final int SHIFT = 5;

    private DeepSeekMxfp4() {
    }

    /**
     * Converts E8M0 scales {@code [N, K/32]} to E4M3 scales {@code [N, K/16]}.
     *
     * @param e8m0 the checkpoint's scales (uint8 or float8_e8m0 viewed as bytes).
     * @return float8_e4m3fn scales, each input scale repeated for its two 16-wide groups.
     */
    static Tensor toNvfp4(Tensor e8m0) {
        Tensor b = e8m0.dtype() == ScalarType.UInt8 ? e8m0 : Aten.one("aten::view.dtype", e8m0, ScalarType.UInt8);
        Tensor k = Aten.one("aten::sub.Scalar", b.to(ScalarType.Int32), 127);
        long min = Aten.one("aten::min", k).longValue();
        long max = Aten.one("aten::max", k).longValue();
        // E4M3 power of two 2^j has exponent field j + 7 (normal numbers, j >= -6), or mantissa bit 2^(j+9) (j >= -9)
        if (min + SHIFT < -6 || max + SHIFT > 8) {
            throw new IllegalArgumentException("E8M0 exponents " + min + ".." + max + " do not fit E4M3 with global scale 32");
        }
        Tensor field = Aten.one("aten::bitwise_left_shift.Tensor_Scalar",
                Aten.one("aten::add.Scalar", k, 7 + SHIFT), 3);
        Tensor bytes = Aten.one("aten::repeat_interleave.self_int", field.to(ScalarType.UInt8), 2L, 1L);
        return Aten.one("aten::view.dtype", bytes, ScalarType.Float8e4m3fn);
    }
}
