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

import smile.deep.tensor.Index;
import smile.deep.tensor.ScalarType;
import smile.deep.tensor.Tensor;

/**
 * Dequantizes DeepSeek / Qwen-style fine-grained FP8 weights
 * ({@code weight_block_size = [128, 128]} + {@code weight_scale_inv}).
 *
 * <p>Official Qwen3.8-FP8 checkpoints use block scales; {@link Fp8BlockLinear}
 * runs them via {@code at::_scaled_mm_v2}. This helper dequantizes for reference
 * tests and debug only.
 *
 * @author Haifeng Li
 */
public final class Fp8BlockDequant {
    /** Qwen / DeepSeek fine-grained FP8 block size. */
    public static final int BLOCK = 128;

    private Fp8BlockDequant() {}

    /**
     * @return {@code true} when {@code scale} is a {@code [ceil(N/128), ceil(K/128)]}
     *         block layout for {@code weight} shaped {@code [N, K]}.
     */
    public static boolean isBlockScale(Tensor weight, Tensor scale) {
        if (weight == null || scale == null) {
            return false;
        }
        long[] ws = weight.shape();
        long[] ss = scale.shape();
        if (ws.length != 2 || ss.length != 2) {
            return false;
        }
        long expectN = (ws[0] + BLOCK - 1) / BLOCK;
        long expectK = (ws[1] + BLOCK - 1) / BLOCK;
        return ss[0] == expectN && ss[1] == expectK;
    }

    /**
     * @return {@code true} when {@code scale} is a scalar / length-1 tensor scale.
     */
    public static boolean isTensorScale(Tensor scale) {
        if (scale == null) {
            return false;
        }
        long n = 1;
        for (long d : scale.shape()) {
            n *= d;
        }
        return n == 1;
    }

    /**
     * Dequantizes {@code weight_fp8 * broadcast(scale_inv)} to {@code outDtype}.
     *
     * @param weightFp8 FP8 weight {@code [N, K]}.
     * @param scaleInv  block inverse scales {@code [ceil(N/128), ceil(K/128)]}.
     * @param outDtype  compute dtype (BF16 or FP16).
     * @return dense weight on the same device as {@code weightFp8} (caller owns).
     */
    public static Tensor dequant(Tensor weightFp8, Tensor scaleInv, ScalarType outDtype) {
        if (!isBlockScale(weightFp8, scaleInv)) {
            throw new IllegalArgumentException(
                    "expected block-128 weight_scale_inv for weight shape "
                            + java.util.Arrays.toString(weightFp8.shape())
                            + ", got scale shape " + java.util.Arrays.toString(scaleInv.shape()));
        }
        long n = weightFp8.shape()[0];
        long k = weightFp8.shape()[1];
        long nBlocks = scaleInv.shape()[0];
        long kBlocks = scaleInv.shape()[1];

        Tensor scaleF = scaleInv.to(ScalarType.Float);
        Tensor uns0 = scaleF.unsqueeze(1);
        Tensor uns1 = uns0.unsqueeze(3); // [nB, 1, kB, 1]
        Tensor expanded = uns1.expand(nBlocks, BLOCK, kBlocks, BLOCK);
        Tensor flat = expanded.reshape(nBlocks * BLOCK, kBlocks * BLOCK);
        Tensor cropped;
        try (Index rows = Index.slice(0L, n); Index cols = Index.slice(0L, k)) {
            cropped = flat.get(rows, cols).contiguous();
        }
        Tensor wF = weightFp8.to(ScalarType.Float);
        Tensor mul = wF.mul(cropped);
        Tensor out = mul.to(outDtype == null ? ScalarType.BFloat16 : outDtype);

        if (scaleF != scaleInv) {
            scaleF.close();
        }
        uns0.close();
        uns1.close();
        expanded.close();
        flat.close();
        cropped.close();
        wF.close();
        mul.close();
        return out;
    }
}
