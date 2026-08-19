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
package smile.llm.qwen;

import java.util.Arrays;
import smile.deep.tensor.Index;
import smile.deep.tensor.ScalarType;
import smile.deep.tensor.Tensor;
import smile.llm.transformer.RotaryPositionalEncoding;
import smile.util.AutoScope;
import smile.util.Tuple2;

/**
 * Partial rotary positional encoding used by Qwen3.5 gated attention.
 * Only the leading {@code rotaryDim} features of each head are rotated;
 * the remainder is passed through unchanged.
 *
 * @author Haifeng Li
 */
public final class PartialRotaryEncoding {
    private PartialRotaryEncoding() {}

    /**
     * Applies partial RoPE to query and key tensors.
     *
     * @param xq        query {@code [B, S, H, D]}.
     * @param xk        key {@code [B, S, Hkv, D]}.
     * @param cis       complex frequency table for {@code rotaryDim/2} pairs,
     *                  sliced to the current positions.
     * @param rotaryDim number of leading head dims to rotate (even).
     * @return rotated (query, key).
     */
    public static Tuple2<Tensor, Tensor> apply(Tensor xq, Tensor xk, Tensor cis, int rotaryDim) {
        if (rotaryDim <= 0) {
            return new Tuple2<>(xq, xk);
        }
        long headDim = xq.shape()[xq.dim() - 1];
        if (rotaryDim > headDim) {
            throw new IllegalArgumentException("rotaryDim > headDim");
        }
        if (rotaryDim == headDim) {
            return RotaryPositionalEncoding.apply(xq, xk, cis);
        }

        AutoScope scope = new AutoScope();
        Tensor.push(scope);
        try (var rot = Index.slice(0, rotaryDim);
             var pass = Index.slice(rotaryDim, (int) headDim)) {
            Tensor xqRot = xq.get(Index.Ellipsis, rot);
            Tensor xkRot = xk.get(Index.Ellipsis, rot);
            Tensor xqPass = xq.get(Index.Ellipsis, pass);
            Tensor xkPass = xk.get(Index.Ellipsis, pass);

            var rotated = RotaryPositionalEncoding.apply(xqRot, xkRot, cis);
            Tensor qOut = concatLast(rotated._1(), xqPass);
            Tensor kOut = concatLast(rotated._2(), xkPass);
            scope.remove(qOut);
            scope.remove(kOut);
            return new Tuple2<>(qOut, kOut);
        } finally {
            Tensor.pop();
        }
    }

    /**
     * Concatenates two tensors along the last dimension via reshape + {@link Tensor#hstack}.
     */
    static Tensor concatLast(Tensor a, Tensor b) {
        long[] ashape = a.shape();
        long[] bshape = b.shape();
        if (ashape.length != bshape.length) {
            throw new IllegalArgumentException("rank mismatch");
        }
        long rows = 1;
        for (int i = 0; i < ashape.length - 1; i++) {
            if (ashape[i] != bshape[i]) {
                throw new IllegalArgumentException("leading dims mismatch");
            }
            rows *= ashape[i];
        }
        long[] outShape = Arrays.copyOf(ashape, ashape.length);
        outShape[outShape.length - 1] = ashape[ashape.length - 1] + bshape[bshape.length - 1];
        try (Tensor a2 = a.reshape(rows, ashape[ashape.length - 1]);
             Tensor b2 = b.reshape(rows, bshape[bshape.length - 1]);
             Tensor cat = Tensor.hstack(a2, b2)) {
            return cat.reshape(outShape);
        }
    }

    /**
     * Precomputes cis frequencies for the rotary subspace.
     *
     * @param rotaryDim rotary feature count (even).
     * @param end       table length (typically {@code 2 * maxSeqLen}).
     * @param theta     RoPE theta.
     * @return complex frequency tensor.
     */
    public static Tensor computeFreqCis(int rotaryDim, int end, double theta) {
        try (Tensor complex = RotaryPositionalEncoding.computeFreqCis(rotaryDim, end, theta, false)) {
            return complex.to(ScalarType.Float);
        }
    }
}
