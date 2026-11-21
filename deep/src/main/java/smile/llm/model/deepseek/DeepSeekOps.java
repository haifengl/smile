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
 * Tensor operations of the DeepSeek-V4.1 forward pass, written with generic ATen ops ({@link Aten}) so they run on
 * any device. They follow the formulas of the reference kernels ({@code act_quant}, {@code fp4_act_quant},
 * {@code hc_split_sinkhorn}, {@code sparse_attn}, ...) and are checked against a pure-PyTorch port of them.
 *
 * <p>Quantized weights are dequantized to float32 and multiplied with fake-quantized activations: the products are
 * exact, only the fp32 accumulation order differs from the fused kernels.
 *
 * @author Haifeng Li
 */
public final class DeepSeekOps {
    private DeepSeekOps() {
    }

    /** The e2m1 values by 4-bit code (sign bit 3). */
    private static final float[] FP4_TABLE = {0f, 0.5f, 1f, 1.5f, 2f, 3f, 4f, 6f, 0f, -0.5f, -1f, -1.5f, -2f, -3f, -4f, -6f};

    private static Tensor op(String name, Object... args) {
        return Aten.one(name, args);
    }

    /**
     * Casts to float32.
     *
     * @param x a tensor.
     * @return {@code x} as float32.
     */
    public static Tensor f32(Tensor x) {
        return x.dtype() == ScalarType.Float ? x : x.to(ScalarType.Float);
    }

    /**
     * {@code 2^ceil(log2 v)} for positive float32 values (the reference {@code fast_round_scale}).
     *
     * @param v positive float32 values.
     * @return powers of two.
     */
    public static Tensor pow2Ceil(Tensor v) {
        Tensor[] fr = Aten.call("aten::frexp.Tensor", v);
        Tensor exact = op("aten::eq.Scalar", fr[0], 0.5);
        Tensor ce = op("aten::where.self", exact, op("aten::sub.Scalar", fr[1], 1), fr[1]);
        return op("aten::ldexp.Tensor", op("aten::ones_like", v), ce);
    }

    /**
     * Block-wise fp8 e4m3 fake quantization with power-of-two scales (the reference {@code act_quant} with
     * {@code inplace=True}): values go through fp8 and back, in the dtype of {@code x}.
     *
     * @param x     activations, the last dimension a multiple of {@code block}.
     * @param block elements per scale (32).
     * @return the fake-quantized tensor.
     */
    public static Tensor fakeQuantFp8(Tensor x, int block) {
        if (block == 32) {
            Tensor fused = smile.torch.Native.dsFakeQuantFp8(x);
            if (fused != null) {
                return fused;
            }
        }
        long[] shape = x.shape();
        long n = shape[shape.length - 1];
        Tensor xf = f32(x).reshape(-1, n / block, block);
        Tensor amax = op("aten::clamp_min", op("aten::amax", op("aten::abs", xf), new long[]{-1}, true), 1e-4);
        Tensor s = pow2Ceil(op("aten::mul.Scalar", amax, 1.0 / 448.0));
        Tensor q = op("aten::clamp", op("aten::div.Tensor", xf, s), -448.0, 448.0);
        Tensor y = op("aten::mul.Tensor", q.to(ScalarType.Float8e4m3fn).to(ScalarType.Float), s);
        return y.reshape(shape).to(x.dtype());
    }

    /**
     * Rounds float32 values in [-6, 6] to the nearest e2m1 value, ties to the even code.
     *
     * @param v values in [-6, 6].
     * @return the rounded values.
     */
    public static Tensor roundFp4(Tensor v) {
        Tensor a = op("aten::abs", v);
        Tensor q = op("aten::where.Scalar", op("aten::gt.Scalar", a, 0.25), 0.5, 0.0);
        q = op("aten::where.ScalarSelf", op("aten::ge.Scalar", a, 0.75), 1.0, q);
        q = op("aten::where.ScalarSelf", op("aten::gt.Scalar", a, 1.25), 1.5, q);
        q = op("aten::where.ScalarSelf", op("aten::ge.Scalar", a, 1.75), 2.0, q);
        q = op("aten::where.ScalarSelf", op("aten::gt.Scalar", a, 2.5), 3.0, q);
        q = op("aten::where.ScalarSelf", op("aten::ge.Scalar", a, 3.5), 4.0, q);
        q = op("aten::where.ScalarSelf", op("aten::gt.Scalar", a, 5.0), 6.0, q);
        return op("aten::copysign.Tensor", q, v);
    }

    /**
     * Block-wise fp4 fake quantization (the reference {@code fp4_act_quant} with {@code inplace=True}).
     *
     * @param x          activations, the last dimension a multiple of {@code block}.
     * @param block      elements per scale: 32 with power-of-two scales (indexer), 16 with e4m3 scales (compressed KV).
     * @param e4m3Scale  {@code true} for e4m3 scales, {@code false} for power-of-two scales.
     * @return the fake-quantized tensor, in the dtype of {@code x}.
     */
    public static Tensor fakeQuantFp4(Tensor x, int block, boolean e4m3Scale) {
        Tensor fused = smile.torch.Native.dsFakeQuantFp4(x, block, e4m3Scale);
        if (fused != null) {
            return fused;
        }
        long[] shape = x.shape();
        long n = shape[shape.length - 1];
        Tensor xf = f32(x).reshape(-1, n / block, block);
        Tensor amax = op("aten::amax", op("aten::abs", xf), new long[]{-1}, true);
        Tensor s;
        if (e4m3Scale) {
            amax = op("aten::clamp_min", amax, 6.0 * Math.pow(2, -9));
            s = op("aten::div.Scalar", amax, 6.0).to(ScalarType.Float8e4m3fn).to(ScalarType.Float);
        } else {
            amax = op("aten::clamp_min", amax, 6.0 * Math.pow(2, -126));
            s = pow2Ceil(op("aten::mul.Scalar", amax, 1.0 / 6.0));
        }
        Tensor r = roundFp4(op("aten::clamp", op("aten::div.Tensor", xf, s), -6.0, 6.0));
        return op("aten::mul.Tensor", r, s).reshape(shape).to(x.dtype());
    }

    /**
     * Converts e8m0 scale bytes to float32 ({@code 2^(byte - 127)}).
     *
     * @param e8m0 the raw bytes as a uint8 tensor.
     * @return float32 scales.
     */
    public static Tensor e8m0ToFloat(Tensor e8m0) {
        Tensor e = op("aten::sub.Scalar", e8m0.to(ScalarType.Int32), 127);
        return op("aten::ldexp.Tensor", op("aten::ones_like", e.to(ScalarType.Float)), e);
    }

    /**
     * Dequantizes a block-FP8 weight with 32x32 e8m0 scales.
     *
     * @param w      fp8 e4m3 weight {@code [N, K]}.
     * @param scale  e8m0 bytes {@code [ceil(N/32), ceil(K/32)]} (uint8).
     * @return the float32 weight.
     */
    public static Tensor dequantFp8(Tensor w, Tensor scale) {
        long n = w.size(0);
        long k = w.size(1);
        Tensor sf = e8m0ToFloat(scale);
        sf = op("aten::repeat_interleave.self_int", sf, 32L, 0L);
        sf = op("aten::repeat_interleave.self_int", sf, 32L, 1L);
        sf = op("aten::narrow", op("aten::narrow", sf, 0L, 0L, n), 1L, 0L, k);
        return op("aten::mul.Tensor", w.to(ScalarType.Float), sf);
    }

    /**
     * Dequantizes an FP4 (e2m1) weight with per-32 e8m0 scales along K.
     *
     * @param w     packed weight {@code [N, K/2]} (int8 or uint8 bytes, the low nibble is the even element).
     * @param scale e8m0 bytes {@code [N, K/32]} (uint8).
     * @return the float32 weight {@code [N, K]}.
     */
    public static Tensor dequantFp4(Tensor w, Tensor scale) {
        Tensor u = w.dtype() == ScalarType.UInt8 ? w : op("aten::view.dtype", w, ScalarType.UInt8);
        Tensor table = Dsv.dev(Tensor.of(FP4_TABLE, 16));
        Tensor lo = op("aten::bitwise_and.Scalar", u, 15);
        Tensor hi = op("aten::bitwise_right_shift.Tensor_Scalar", u, 4);
        long[] shape = u.shape();
        Tensor vlo = op("aten::index_select", table, 0L, lo.to(ScalarType.Int64).reshape(-1)).reshape(shape);
        Tensor vhi = op("aten::index_select", table, 0L, hi.to(ScalarType.Int64).reshape(-1)).reshape(shape);
        Tensor v = op("aten::stack", new Tensor[]{vlo, vhi}, -1L).reshape(shape[0], shape[1] * 2);
        return op("aten::mul.Tensor", v, op("aten::repeat_interleave.self_int", e8m0ToFloat(scale), 32L, 1L));
    }

    /**
     * RMS normalization {@code weight * x / sqrt(mean(x^2) + eps)}, computed in float32 and cast back.
     *
     * @param x      the input.
     * @param weight the scale.
     * @param eps    epsilon (1e-20 in the released model).
     * @return the normalized tensor in the dtype of {@code x}.
     */
    public static Tensor rmsNorm(Tensor x, Tensor weight, double eps) {
        Tensor fused = smile.torch.Native.dsRmsNorm(x, weight, eps);
        if (fused != null) {
            return fused;
        }
        Tensor xf = f32(x);
        Tensor var = op("aten::mean.dim", op("aten::mul.Tensor", xf, xf), new long[]{-1}, true);
        Tensor r = op("aten::rsqrt", op("aten::add.Scalar", var, eps));
        return op("aten::mul.Tensor", weight, op("aten::mul.Tensor", xf, r)).to(x.dtype());
    }

    /**
     * Rotary frequencies as unit complex numbers, with YaRN extrapolation when {@code originalSeqLen > 0}.
     *
     * @param dim            rotated dimension.
     * @param seqLen         number of positions.
     * @param originalSeqLen YaRN original context (0 disables YaRN).
     * @param base           RoPE base.
     * @param factor         YaRN factor.
     * @param betaFast       YaRN beta fast.
     * @param betaSlow       YaRN beta slow.
     * @return complex64 {@code [seqLen, dim/2]}.
     */
    public static Tensor precomputeFreqsCis(int dim, int seqLen, int originalSeqLen, double base, double factor,
                                            int betaFast, int betaSlow) {
        Tensor ar = op("aten::arange.start_step", 0L, (long) dim, 2L, ScalarType.Float);
        Tensor freqs = op("aten::mul.Scalar", op("aten::reciprocal",
                op("aten::pow.Scalar", base, op("aten::div.Scalar", ar, (double) dim))), 1.0);
        if (originalSeqLen > 0) {
            int low = Math.max((int) Math.floor(corrected(dim, originalSeqLen, base, betaFast)), 0);
            int high = Math.min((int) Math.ceil(corrected(dim, originalSeqLen, base, betaSlow)), dim - 1);
            Tensor idx = op("aten::arange.start_step", 0L, (long) (dim / 2), 1L, ScalarType.Float);
            Tensor ramp = op("aten::clamp", op("aten::div.Scalar", op("aten::sub.Scalar", idx, (double) low),
                    Math.max(high - low, 1e-3)), 0.0, 1.0);
            Tensor smooth = op("aten::rsub.Scalar", ramp, 1.0);
            Tensor a = op("aten::mul.Tensor", op("aten::div.Scalar", freqs, factor), op("aten::rsub.Scalar", smooth, 1.0));
            freqs = op("aten::add.Tensor", a, op("aten::mul.Tensor", freqs, smooth));
        }
        Tensor pos = op("aten::arange.start_step", 0L, (long) seqLen, 1L, ScalarType.Float);
        Tensor angles = op("aten::outer", pos, freqs);
        return Dsv.dev(op("aten::polar", op("aten::ones_like", angles), angles));
    }

    private static double corrected(int dim, int originalSeqLen, double base, double rotations) {
        return dim * Math.log(originalSeqLen / (rotations * 2 * Math.PI)) / (2 * Math.log(base));
    }

    /**
     * Applies rotary embedding to adjacent element pairs of the last dimension.
     *
     * @param x         {@code [b, s, d]} or {@code [b, s, h, d]}.
     * @param freqsCis  complex {@code [s, d/2]}.
     * @param inverse   conjugate the rotation (removes a previous rotation).
     * @return the rotated tensor in the dtype of {@code x}.
     */
    public static Tensor rope(Tensor x, Tensor freqsCis, boolean inverse) {
        long[] shape = x.shape();
        Tensor pairs = f32(x).reshape(prefix(shape, shape[shape.length - 1] / 2, 2));
        Tensor xc = pairs.viewAsComplex();
        Tensor f = inverse ? op("aten::conj_physical", freqsCis) : freqsCis;
        long s = xc.size(1);
        long half = xc.size(xc.dim() - 1);
        f = xc.dim() == 3 ? f.reshape(1, s, half) : f.reshape(1, s, 1, half);
        Tensor y = op("aten::view_as_real", op("aten::mul.Tensor", xc, f));
        return y.reshape(shape).to(x.dtype());
    }

    private static long[] prefix(long[] shape, long a, long b) {
        long[] out = new long[shape.length + 1];
        System.arraycopy(shape, 0, out, 0, shape.length - 1);
        out[shape.length - 1] = a;
        out[shape.length] = b;
        return out;
    }

    /**
     * The Hyper-Connections coefficients from the projected stream: sigmoid pre/post weights and a doubly
     * stochastic (Sinkhorn) combination matrix.
     *
     * @param mixes {@code [..., (2 + hc) * hc]} float32.
     * @param scale {@code [3]} float32.
     * @param base  {@code [(2 + hc) * hc]} float32.
     * @param hc    residual copies.
     * @param iters Sinkhorn iterations.
     * @param eps   epsilon.
     * @return {@code {pre [..., hc], post [..., hc], comb [..., hc, hc]}}.
     */
    public static Tensor[] hcSplitSinkhorn(Tensor mixes, Tensor scale, Tensor base, int hc, int iters, double eps) {
        Tensor[] fused = smile.torch.Native.dsHcSplitSinkhorn(mixes, scale, base, hc, iters, eps);
        if (fused != null) {
            return fused;
        }
        Tensor s0 = op("aten::select.int", scale, 0L, 0L);
        Tensor s1 = op("aten::select.int", scale, 0L, 1L);
        Tensor s2 = op("aten::select.int", scale, 0L, 2L);
        Tensor pre = op("aten::add.Scalar", op("aten::sigmoid", op("aten::add.Tensor",
                op("aten::mul.Tensor", op("aten::narrow", mixes, -1L, 0L, (long) hc), s0),
                op("aten::narrow", base, 0L, 0L, (long) hc))), eps);
        Tensor post = op("aten::mul.Scalar", op("aten::sigmoid", op("aten::add.Tensor",
                op("aten::mul.Tensor", op("aten::narrow", mixes, -1L, (long) hc, (long) hc), s1),
                op("aten::narrow", base, 0L, (long) hc, (long) hc))), 2.0);
        Tensor c = op("aten::add.Tensor",
                op("aten::mul.Tensor", op("aten::narrow", mixes, -1L, 2L * hc, (long) hc * hc), s2),
                op("aten::narrow", base, 0L, 2L * hc, (long) hc * hc));
        long[] lead = mixes.shape();
        long[] cs = new long[lead.length + 1];
        System.arraycopy(lead, 0, cs, 0, lead.length - 1);
        cs[lead.length - 1] = hc;
        cs[lead.length] = hc;
        Tensor comb = op("aten::add.Scalar", op("aten::softmax.int", c.reshape(cs), -1L), eps);
        comb = op("aten::div.Tensor", comb, op("aten::add.Scalar", op("aten::sum.dim_IntList", comb, new long[]{-2}, true), eps));
        for (int i = 0; i < iters - 1; i++) {
            comb = op("aten::div.Tensor", comb, op("aten::add.Scalar", op("aten::sum.dim_IntList", comb, new long[]{-1}, true), eps));
            comb = op("aten::div.Tensor", comb, op("aten::add.Scalar", op("aten::sum.dim_IntList", comb, new long[]{-2}, true), eps));
        }
        return new Tensor[]{pre, post, comb};
    }

    /** Prefill chunks of at least this many query rows use the tensor-core path (the per-head kernel re-reads every key per head). */
    static final int GEMM_MIN_ROWS = Integer.getInteger("smile.ds.attn.gemm.rows", 32);
    /** Tensor-core prefill attention and index scores (default on; {@code SMILE_DS_ATTN_GEMM=0} selects the per-head kernels). */
    static volatile boolean gemmPrefill = !"0".equals(System.getenv("SMILE_DS_ATTN_GEMM"));
    /**
     * The tensor-core attention kernel (one block per query row and 16 heads, keys read once) serves both the prefill chunks
     * and the pointer-table decode rows instead of the gather + GEMM and the per-head kernels (default on;
     * {@code SMILE_DS_ATTN_TC=0} turns it off; the native library falls back to the per-head kernel by itself).
     */
    static final boolean TC_ATTENTION = !"0".equals(System.getenv("SMILE_DS_ATTN_TC"));
    private static volatile boolean gemmUnavailable;
    /** Gathered key rows per chunk of the tensor-core path (each row is 1 KB). */
    private static final long GEMM_CHUNK_KEYS = Long.getLong("smile.ds.attn.gemm.keys", 160L << 10);
    /** Decode rows from at least this many sequences use the tensor-core attention over per-sequence caches. */
    static final int GEMM_MIN_SEQS = Integer.getInteger("smile.ds.attn.gemm.seqs", 64);

    /**
     * Sparse attention for one query per sequence over each sequence's caches (pointer tables) as gather + two GEMMs
     * (see {@link #sparseAttentionGemm}); {@code null} when unavailable. Graph-capturable: no host reads, static shapes.
     */
    static Tensor sparseAttentionPtrsGemm(Tensor q, Tensor windowPtrs, Tensor compressPtrs, int window, Tensor sink,
                                          Tensor idxs, double scale) {
        if (!gemmPrefill || gemmUnavailable || q.dtype() != ScalarType.BFloat16 || q.size(0) != 1) {
            return null;
        }
        Tensor keys = smile.torch.Native.dsGatherKeysPtrs(windowPtrs, compressPtrs, idxs, window);   // [m, topk, d]
        if (keys == null) {
            return null;
        }
        try {
            Tensor s = op("aten::bmm.dtype", op("aten::select.int", q, 0L, 0L), keys.transpose(1, 2), ScalarType.Float);
            Tensor ix = op("aten::select.int", idxs, 0L, 0L);
            Tensor p = smile.torch.Native.dsAttnSoftmax(s, ix, sink, scale);
            if (p == null) {
                return null;
            }
            return op("aten::bmm", p, keys).unsqueeze(0);
        } catch (RuntimeException e) {
            gemmUnavailable = true;
            return null;
        }
    }

    /**
     * {@link #sparseAttention} for many query rows as batched bf16 GEMMs on tensor cores (float32 accumulation and
     * scores, probabilities rounded to bf16 for the value product, as the reference kernel does): the shared keys of
     * each row are gathered once and serve all heads. Rows go in chunks that bound the gathered copy.
     *
     * @return the output, or {@code null} when {@code aten::bmm.dtype} is unavailable.
     */
    static Tensor sparseAttentionGemm(Tensor q, Tensor kv, Tensor sink, Tensor idxs, double scale) {
        if (gemmUnavailable || q.dtype() != ScalarType.BFloat16 || kv.dtype() != ScalarType.BFloat16 || q.size(0) != 1) {
            return null;
        }
        long m = q.size(1);
        long h = q.size(2);
        long d = q.size(3);
        long topk = idxs.size(2);
        long chunk = Math.max(16, Math.min(m, GEMM_CHUNK_KEYS / topk));      // rows per chunk: ~160K gathered key rows
        Tensor keys = op("aten::select.int", kv, 0L, 0L);                    // [n, d]
        Tensor q3 = op("aten::select.int", q, 0L, 0L);                       // [m, h, d]
        Tensor ix = op("aten::select.int", idxs, 0L, 0L);                    // [m, topk]
        Tensor sinkRow = f32(sink).reshape(1, h, 1);
        Tensor[] parts = new Tensor[(int) ((m + chunk - 1) / chunk)];
        try {
            for (int c = 0; c < parts.length; c++) {
                long r0 = c * chunk;
                long rows = Math.min(chunk, m - r0);
                Tensor idx = op("aten::narrow", ix, 0L, r0, rows);
                Tensor invalid = op("aten::lt.Scalar", idx, 0);
                Tensor ids = op("aten::clamp_min", idx, 0).to(ScalarType.Int64).reshape(-1);
                Tensor gathered = op("aten::index_select", keys, 0L, ids).reshape(rows, topk, d);   // [rows, topk, d]
                Tensor qc = op("aten::narrow", q3, 0L, r0, rows);                                   // [rows, h, d]
                Tensor s = op("aten::bmm.dtype", qc, gathered.transpose(1, 2), ScalarType.Float);   // [rows, h, topk]
                Tensor p = smile.torch.Native.dsAttnSoftmax(s, idx, sink, scale);                    // normalized, bf16
                if (p != null) {
                    parts[c] = op("aten::bmm", p, gathered);                                          // [rows, h, d] bf16
                    continue;
                }
                s = op("aten::mul.Scalar", s, scale);
                s = op("aten::masked_fill.Scalar", s, invalid.unsqueeze(1), Double.NEGATIVE_INFINITY);
                Tensor mx = op("aten::clamp_min", op("aten::amax", s, new long[]{-1}, true), -1e30);   // [rows, h, 1]
                Tensor e = op("aten::exp", op("aten::sub.Tensor", s, mx));
                Tensor denom = op("aten::add.Tensor", op("aten::sum.dim_IntList", e, new long[]{-1}, true),
                        op("aten::exp", op("aten::sub.Tensor", sinkRow, mx)));                  // [rows, h, 1]
                p = op("aten::div.Tensor", e, denom).to(ScalarType.BFloat16);
                parts[c] = op("aten::bmm", p, gathered);
            }
        } catch (RuntimeException e) {
            if (String.valueOf(e.getMessage()).contains("bmm")) {
                gemmUnavailable = true;
                return null;
            }
            throw e;
        }
        Tensor out = parts.length == 1 ? parts[0] : op("aten::cat", parts, 0L);
        return out.unsqueeze(0);
    }

    /**
     * Attention over gathered key/value rows with an attention-sink logit per head (the reference
     * {@code sparse_attn}); a query with no valid index gets a zero output.
     *
     * @param q      queries {@code [1, m, h, d]}.
     * @param kv     shared keys/values {@code [1, n, d]}.
     * @param sink   sink logits {@code [h]} float32.
     * @param idxs   int32 indices into {@code kv} {@code [1, m, topk]}, {@code -1} for none.
     * @param scale  softmax scale.
     * @return the output {@code [1, m, h, d]} in bf16.
     */
    public static Tensor sparseAttention(Tensor q, Tensor kv, Tensor sink, Tensor idxs, double scale) {
        if (!TC_ATTENTION && q.size(1) >= GEMM_MIN_ROWS && q.device().isCUDA() && gemmPrefill) {
            Tensor g = sparseAttentionGemm(q, kv, sink, idxs, scale);
            if (g != null) {
                return g;
            }
        }
        Tensor fused = smile.torch.Native.dsSparseAttention(q, kv, sink, idxs, scale);
        if (fused != null) {
            return fused;
        }
        long m = q.size(1);
        long topk = idxs.size(2);
        long d = q.size(3);
        Tensor idx = op("aten::select.int", idxs, 0L, 0L);                                      // [m, topk]
        Tensor valid = op("aten::ge.Scalar", idx, 0);
        Tensor rows = op("aten::index_select", op("aten::select.int", kv, 0L, 0L), 0L,
                op("aten::clamp_min", idx, 0).to(ScalarType.Int64).reshape(-1)).reshape(m, topk, d);
        Tensor g = f32(rows);
        Tensor qf = f32(op("aten::select.int", q, 0L, 0L));                                     // [m, h, d]
        Tensor s = op("aten::mul.Scalar", op("aten::einsum", "mhd,mtd->mht", new Tensor[]{qf, g}), scale);
        s = op("aten::masked_fill.Scalar", s, op("aten::logical_not", valid).unsqueeze(1), Double.NEGATIVE_INFINITY);
        Tensor mx = op("aten::clamp_min", op("aten::amax", s, new long[]{-1}), -1e30);          // [m, h]
        Tensor p = op("aten::exp", op("aten::sub.Tensor", s, mx.unsqueeze(-1)));
        Tensor denom = op("aten::add.Tensor", op("aten::sum.dim_IntList", p, new long[]{-1}),
                op("aten::exp", op("aten::sub.Tensor", f32(sink).unsqueeze(0), mx)));
        Tensor o = op("aten::div.Tensor", op("aten::einsum", "mht,mtd->mhd", new Tensor[]{p, g}), denom.unsqueeze(-1));
        return o.unsqueeze(0).to(ScalarType.BFloat16);
    }

    /**
     * The sliding-window slots each query attends to, for prefill (one row per query) or one decode step.
     *
     * @param window   window size.
     * @param ring     slots of the window ring (decode).
     * @param seqLen   number of queries (1 for decode).
     * @param startPos position of the first query.
     * @return int32 {@code [1, seqLen, width]}, {@code -1} for slots holding nothing.
     */
    public static Tensor windowTopkIdxs(int window, int ring, int seqLen, int startPos) {
        if (startPos > 0 && seqLen == 1) {
            // one decode token: identical for every layer of the step, so build and upload it once
            return Dsv.cached("window:" + window + ":" + ring + ":" + startPos, () -> windowTopkIdxs0(window, ring, seqLen, startPos));
        }
        return windowTopkIdxs0(window, ring, seqLen, startPos);
    }

    private static Tensor windowTopkIdxs0(int window, int ring, int seqLen, int startPos) {
        int[] data;
        int rows;
        int width;
        if (startPos == 0) {
            rows = seqLen;
            width = Math.min(seqLen, window);
            data = new int[rows * width];
            for (int i = 0; i < rows; i++) {
                int base = Math.max(i - window + 1, 0);
                for (int j = 0; j < width; j++) {
                    int v = base + j;
                    data[i * width + j] = v > i ? -1 : v;
                }
            }
        } else {
            rows = 1;
            width = window;
            data = new int[width];
            for (int j = 0; j < width; j++) {
                int q = startPos - (window - 1) + j;
                data[j] = q < 0 ? -1 : q % ring;
            }
        }
        return Dsv.dev(Tensor.of(data, 1, rows, width));
    }
}
