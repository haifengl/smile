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
package smile.llm.model.qwen;

import java.util.Arrays;
import smile.deep.tensor.Index;
import smile.deep.tensor.ScalarType;
import smile.deep.tensor.Tensor;
import smile.util.AutoScope;
import smile.util.Tuple2;

/**
 * Partial rotary positional encoding for Qwen3.5 gated attention.
 *
 * <p>Matches HuggingFace {@code apply_rotary_pos_emb} / {@code rotate_half}
 * (NeoX-style half-rotation), <em>not</em> Meta interleaved complex RoPE.
 * Only the leading {@code rotaryDim} features of each head are rotated;
 * the remainder is passed through unchanged.
 *
 * @author Haifeng Li
 */
public final class PartialRotaryEncoding {
    private PartialRotaryEncoding() {}

    /**
     * Precomputed cos/sin tables for HF-style partial RoPE.
     *
     * @param cos {@code [end, rotaryDim]} cosine table.
     * @param sin {@code [end, rotaryDim]} sine table.
     */
    public record CosSin(Tensor cos, Tensor sin) implements AutoCloseable {
        /** Detaches both tables from every AutoScope on this thread. */
        public void detachFromScopes() {
            cos.detachFromScopes();
            sin.detachFromScopes();
        }

        @Override
        public void close() {
            cos.close();
            sin.close();
        }
    }

    /**
     * Applies HF {@code rotate_half} RoPE to query and key tensors.
     *
     * @param xq        query {@code [B, S, H, D]}.
     * @param xk        key {@code [B, S, Hkv, D]}.
     * @param cos       cosines for the current positions {@code [S, rotaryDim]},
     *                  {@code [B, S, rotaryDim]} (per-row decode), or broadcastable.
     * @param sin       sines with the same layout as {@code cos}.
     * @param rotaryDim number of leading head dims to rotate (even).
     * @return rotated (query, key).
     */
    public static Tuple2<Tensor, Tensor> apply(Tensor xq, Tensor xk,
                                               Tensor cos, Tensor sin, int rotaryDim) {
        if (rotaryDim <= 0) {
            return new Tuple2<>(xq, xk);
        }
        long headDim = xq.shape()[xq.dim() - 1];
        if (rotaryDim > headDim) {
            throw new IllegalArgumentException("rotaryDim > headDim");
        }
        if ((rotaryDim & 1) != 0) {
            throw new IllegalArgumentException("rotaryDim must be even");
        }

        AutoScope scope = new AutoScope();
        Tensor.push(scope);
        try (var rot = Index.slice(0, rotaryDim);
             var pass = Index.slice(rotaryDim, (int) headDim)) {
            Tensor cosF = cos.to(ScalarType.Float);
            Tensor sinF = sin.to(ScalarType.Float);
            // [S, R] → [1, S, 1, R] for broadcast over batch and heads.
            Tensor cosB = broadcastCosSin(cosF, xq);
            Tensor sinB = broadcastCosSin(sinF, xq);

            Tensor xqRot = xq.get(Index.Ellipsis, rot).to(ScalarType.Float);
            Tensor xkRot = xk.get(Index.Ellipsis, rot).to(ScalarType.Float);
            Tensor xqPass = xq.get(Index.Ellipsis, pass);
            Tensor xkPass = xk.get(Index.Ellipsis, pass);

            Tensor qEmbF = applyRotateHalf(xqRot, cosB, sinB);
            Tensor kEmbF = applyRotateHalf(xkRot, cosB, sinB);
            Tensor qEmb = qEmbF.to(xq.dtype());
            Tensor kEmb = kEmbF.to(xk.dtype());
            if (qEmb != qEmbF) {
                qEmbF.close();
            }
            if (kEmb != kEmbF) {
                kEmbF.close();
            }

            Tensor qOut;
            Tensor kOut;
            if (rotaryDim == headDim) {
                qOut = qEmb;
                kOut = kEmb;
            } else {
                qOut = concatLast(qEmb, xqPass);
                kOut = concatLast(kEmb, xkPass);
                qEmb.close();
                kEmb.close();
            }
            qOut.promoteToParent();
            kOut.promoteToParent();
            return new Tuple2<>(qOut, kOut);
        } finally {
            Tensor.pop();
        }
    }

    /**
     * {@code (x * cos) + (rotate_half(x) * sin)}.
     */
    static Tensor applyRotateHalf(Tensor x, Tensor cos, Tensor sin) {
        try (Tensor rotated = rotateHalf(x);
             Tensor term1 = x.mul(cos);
             Tensor term2 = rotated.mul(sin)) {
            return term1.add(term2);
        }
    }

    /**
     * HuggingFace {@code rotate_half}: {@code cat(-x2, x1)} on the last dim.
     *
     * @param x input with even last dimension.
     * @return rotated tensor (caller owns).
     */
    static Tensor rotateHalf(Tensor x) {
        long d = x.shape()[x.dim() - 1];
        int half = (int) (d / 2);
        try (var first = Index.slice(0, half);
             var second = Index.slice(half, (int) d);
             Tensor x1 = x.get(Index.Ellipsis, first);
             Tensor x2 = x.get(Index.Ellipsis, second);
             Tensor negX2 = x2.neg()) {
            return concatLast(negX2, x1);
        }
    }

    /**
     * Gathers cos/sin rows for per-request decode positions.
     *
     * @param table     {@code [maxPos, rotaryDim]} table.
     * @param positions absolute positions, one per batch row.
     * @return {@code [B, 1, rotaryDim]} (caller owns).
     */
    public static Tensor gather(Tensor table, int[] positions) {
        if (positions == null || positions.length == 0) {
            throw new IllegalArgumentException("positions must be non-empty");
        }
        try (var idx = Index.of(positions);
             Tensor rows = table.get(idx); // [B, R]
             Tensor unsqueezed = rows.unsqueeze(1)) { // [B, 1, R]
            Tensor copy = unsqueezed.copy();
            copy.promoteToParent();
            return copy;
        }
    }

    /**
     * Reshapes {@code [S, R]}, {@code [B, S, R]}, or {@code [R]} cos/sin for
     * {@code [B, S, H, D]} query/key broadcast.
     */
    static Tensor broadcastCosSin(Tensor table, Tensor xq) {
        long[] shape = table.shape();
        long batch = xq.shape()[0];
        long xSeq = xq.shape()[1];
        long seq;
        long rot;
        if (shape.length == 2) {
            seq = shape[0];
            rot = shape[1];
            if (seq != xSeq) {
                throw new IllegalArgumentException(
                        "cos/sin seqLen=" + seq + " != query seqLen=" + xSeq);
            }
            return table.view(1, seq, 1, rot);
        }
        if (shape.length == 1) {
            // Single position decode: [R] → treat as S=1.
            seq = 1;
            rot = shape[0];
            if (seq != xSeq) {
                throw new IllegalArgumentException(
                        "cos/sin seqLen=" + seq + " != query seqLen=" + xSeq);
            }
            return table.view(1, 1, 1, rot);
        }
        if (shape.length == 3) {
            // [B, S, R] per-row decode, or [1, S, R].
            long b = shape[0];
            seq = shape[1];
            rot = shape[2];
            if (seq != xSeq) {
                throw new IllegalArgumentException(
                        "cos/sin seqLen=" + seq + " != query seqLen=" + xSeq);
            }
            if (b == batch) {
                return table.view(batch, seq, 1, rot);
            }
            if (b == 1) {
                return table.view(1, seq, 1, rot);
            }
            throw new IllegalArgumentException(
                    "cos/sin batch=" + b + " incompatible with query batch=" + batch);
        }
        throw new IllegalArgumentException(
                "cos/sin must be [S,R], [B,S,R], or [R], got " + Arrays.toString(shape));
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
             Tensor cat = Tensor.hstack(a2, b2);
             Tensor viewed = cat.reshape(outShape)) {
            // Must copy: try-with closes cat/viewed; a reshape view would dangle.
            return viewed.copy();
        }
    }

    /**
     * Precomputes HF-style cos/sin tables for the rotary subspace.
     *
     * <p>For each position {@code t} and frequency index {@code i}:
     * {@code θ = t / base^(2i/dim)}, then
     * {@code cos = cat(cos θ, cos θ)}, {@code sin = cat(sin θ, sin θ)}
     * along the last dim (matches Transformers {@code emb = cat(freqs, freqs)}).
     *
     * @param rotaryDim rotary feature count (even).
     * @param end       table length (typically {@code 2 * maxSeqLen}).
     * @param theta     RoPE base theta.
     * @return cos/sin tables of shape {@code [end, rotaryDim]} in float32.
     */
    public static CosSin computeCosSin(int rotaryDim, int end, double theta) {
        if (rotaryDim <= 0 || (rotaryDim & 1) != 0) {
            throw new IllegalArgumentException("rotaryDim must be positive and even");
        }
        if (end < 1) {
            throw new IllegalArgumentException("end must be >= 1");
        }
        // inv_freq[i] = base^(-2i/dim) for i = 0..dim/2-1
        Tensor cos;
        Tensor sin;
        try (Tensor t0 = Tensor.arange(0, end, 1);
             Tensor t = t0.to(ScalarType.Float);
             Tensor f0 = Tensor.arange(0, rotaryDim, 2);
             Tensor invFreq = f0.to(ScalarType.Float).mul_(-Math.log(theta) / rotaryDim).exp_();
             Tensor freqs = t.outer(invFreq);              // [end, rotaryDim/2]
             Tensor freqsCopy = freqs.copy();
             Tensor emb = Tensor.hstack(freqs, freqsCopy)) { // [end, rotaryDim]
            cos = emb.cos();
            sin = emb.sin();
        }
        cos.detachFromScopes();
        sin.detachFromScopes();
        return new CosSin(cos, sin);
    }

    /**
     * @deprecated use {@link #computeCosSin(int, int, double)}; kept only if
     *             callers still expect a single complex table.
     */
    @Deprecated
    public static Tensor computeFreqCis(int rotaryDim, int end, double theta) {
        throw new UnsupportedOperationException(
                "Qwen RoPE uses HF cos/sin tables; call computeCosSin instead");
    }
}
