/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * Fused CUDA kernels for DeepSeek-V4.1 decode: each replaces a chain of small ATen calls (the host overhead of
 * ~20 us per call dominates a decode step). Functions return NULL / non-zero on error and set the thread-local
 * error string; the caller falls back to the composed ATen path.
 */
#pragma once

#include "smile_torch.h"

#ifdef __cplusplus
extern "C" {
#endif

/** Returns 1 when the DeepSeek kernels were compiled in. */
SMILE_API int smile_deepseek_ops_available(void);

/**
 * Hyper-Connections coefficients (sigmoid pre/post weights and the Sinkhorn-normalized combination matrix) in one
 * launch. mixes: CUDA float32 [rows, (2 + hc) * hc]; scale: float32 [3]; base: float32 [(2 + hc) * hc]; hc <= 8.
 * Writes out[0] = pre [rows, hc], out[1] = post [rows, hc], out[2] = comb [rows, hc, hc] (all float32). Returns 0 on success.
 */
SMILE_API int smile_ds_hc_split_sinkhorn(ST_Tensor mixes, ST_Tensor scale, ST_Tensor base, int hc, int iters,
                                         double eps, ST_Tensor *out);

/**
 * Block-wise (32) fp8 e4m3 fake quantization with power-of-two scales, in x's dtype (float32/float16/bfloat16). x: CUDA,
 * contiguous, numel % 32 == 0. Returns a tensor shaped like x.
 */
SMILE_API ST_Tensor smile_ds_fake_quant_fp8(ST_Tensor x);

/**
 * RMSNorm and the hyper-connection mix in one launch: r = rsqrt(mean(flat^2) + norm_eps) per row, then the coefficients of
 * smile_ds_hc_split_sinkhorn from raw * r. flat: float32 [rows, K]; raw: float32 [rows, (2 + hc) * hc] (the projection of
 * flat). Writes out[0..2] = pre, post, comb. Returns 0 on success.
 */
SMILE_API int smile_ds_hc_mixes(ST_Tensor flat, ST_Tensor raw, ST_Tensor scale, ST_Tensor base, int hc, int iters,
                                double eps, double norm_eps, ST_Tensor *out);

/**
 * smile_ds_hc_mixes with the projection included, for hc == 4 and few rows: flat float32 [rows, K] (K % 4 == 0), fn float32
 * [24, K]; writes out[0..2] = pre [rows, 4], post [rows, 4], comb [rows, 4, 4]. Returns 0 on success.
 */
SMILE_API int smile_ds_hc_mixes_fused(ST_Tensor flat, ST_Tensor fn, ST_Tensor scale, ST_Tensor base, int iters, double eps,
                                      double norm_eps, ST_Tensor *out);

/** y = w * (x * rsqrt(mean(x^2) + eps)) per last-dim row, float32 math, in x's dtype. w: 1-D float32/bf16/f16. */
SMILE_API ST_Tensor smile_ds_rms_norm(ST_Tensor x, ST_Tensor w, double eps);

/** Collapses the hyper-connection copies: out[.., d] = sum_h pre[.., h] * x[.., h, d]. x: [.., hc, dim]; pre: float32 [.., hc]. */
SMILE_API ST_Tensor smile_ds_hc_pre(ST_Tensor x, ST_Tensor pre);

/** Expands again: out[.., j, d] = post[.., j] * x[.., d] + sum_i comb[.., i, j] * residual[.., i, d]; x, residual in one dtype. */
SMILE_API ST_Tensor smile_ds_hc_post(ST_Tensor x, ST_Tensor residual, ST_Tensor post, ST_Tensor comb);

/**
 * Attention over gathered rows of one shared key/value head with a per-head sink logit (DeepSeekOps.sparseAttention).
 * q: bf16 [1, m, h, 512]; kv: bf16 [1, n, 512]; sink: float32 [h]; idxs: int32 [1, m, topk] (-1 = none), all contiguous.
 * Returns bf16 [1, m, h, 512]; a query with no valid index gets zeros.
 */
SMILE_API ST_Tensor smile_ds_sparse_attention(ST_Tensor q, ST_Tensor kv, ST_Tensor sink, ST_Tensor idxs, double scale);

/**
 * MoE router: scores = sqrt(softplus(logits)); the top-k of scores + bias choose the experts (descending), the weights are the
 * unbiased scores of those experts, renormalised (topk > 1) and multiplied by route_scale. logits: float32 [n, E] (E <= 1024);
 * bias: float32 [E]. Writes out[0] = indices int64 [n, topk], out[1] = weights float32 [n, topk]. Returns 0 on success.
 */
SMILE_API int smile_ds_route(ST_Tensor logits, ST_Tensor bias, int topk, double route_scale, ST_Tensor *out);

/**
 * SwiGLU of the expert FFN: h = bf16(silu(min(gate, limit)) * clamp(up, -limit, limit) [* weights[row]]) (no clamping when
 * limit <= 0); with quant != 0 the result is also fp8 fake-quantized per 32 columns. gate_up: bf16 [rows, 2 * inter],
 * inter % 32 == 0, gate first; weights: float32 [rows] or NULL. Returns bf16 [rows, inter].
 */
SMILE_API ST_Tensor smile_ds_swiglu(ST_Tensor gate_up, ST_Tensor weights, double limit, int quant);

/** Sums the topk rows of each token in float32: x bf16 [tokens * topk, dim] -> float32 [tokens, dim]. */
SMILE_API ST_Tensor smile_ds_sum_topk(ST_Tensor x, int topk);

/** Compressor pooling of the rows of a verify step (rows independent; see the kernel), and the state write after acceptance. */
SMILE_API ST_Tensor smile_ds_compress_rows(ST_Tensor kv, ST_Tensor score, ST_Tensor kvptrs, ST_Tensor scptrs, ST_Tensor pos,
                                           ST_Tensor jidx, int ratio);
SMILE_API int smile_ds_compress_commit(ST_Tensor kv, ST_Tensor score, ST_Tensor kvptrs, ST_Tensor scptrs, ST_Tensor base,
                                       ST_Tensor p0, ST_Tensor last, int ratio);

/** Gathers each row's attended keys from its sequence's caches into a dense bf16 [rows, topk, 512] block (zero for index < 0). */
SMILE_API ST_Tensor smile_ds_gather_keys_ptrs(ST_Tensor wptrs, ST_Tensor cptrs, ST_Tensor idxs, int window);

/** Prefill attention softmax between the two GEMMs: scores float32 [rows, heads, topk], idxs int32 [rows, topk]; returns bf16 probabilities. */
SMILE_API ST_Tensor smile_ds_attn_softmax(ST_Tensor scores, ST_Tensor idxs, ST_Tensor sink, double scale);

/**
 * Rotary embedding of the last rd dims of x (adjacent pairs as complex numbers), the other dims copied: out = x with
 * pairs multiplied by freqs (conjugated when inverse != 0). x: CUDA contiguous [1, s, d] or [1, s, h, d]
 * (float32/float16/bfloat16); freqs: complex64 [s, rd / 2], one row per position along dim 1. Returns x's shape and dtype.
 */
SMILE_API ST_Tensor smile_ds_rope_tail(ST_Tensor x, ST_Tensor freqs, int rd, int inverse);

/**
 * Block-wise fp4 (e2m1) fake quantization, in x's dtype. block: 16 or 32 elements per scale; e4m3_scale != 0: scales
 * rounded to e4m3 (amax / 6), otherwise powers of two. x: CUDA contiguous, numel % 32 == 0, last dim % block == 0.
 */
SMILE_API ST_Tensor smile_ds_fake_quant_fp4(ST_Tensor x, int block, int e4m3_scale);

/**
 * Indexer scores: score[s, t] = sum_h relu(q[s, h] . k[t]) * w[s, h], rounded to bf16 after each op like the composed chain.
 * q: bf16 [1, s, h <= 32, hd]; k: bf16 [1, T, hd]; w: bf16 [1, s, h], all contiguous. Returns bf16 [1, s, T].
 */
SMILE_API ST_Tensor smile_ds_index_score(ST_Tensor q, ST_Tensor k, ST_Tensor w);

/** out = idxs < lens ? idxs + offset : -1 as int32. idxs: int64 [1, s, k]; lens: int64 with one entry or one per row. */
SMILE_API ST_Tensor smile_ds_index_finalize(ST_Tensor idxs, ST_Tensor lens, long long offset);

/* ---- decode over several sequences: per-sequence caches are addressed through int64 pointer tables ---- */

/** int64 [n] device tensor of the data pointers of ts[0..n-1] (same device); the tensors must stay alive and not move. */
SMILE_API ST_Tensor smile_ds_ptr_table(const ST_Tensor *ts, int n);

/** window ring write: row i of src (bf16 [n, d]) goes to slot pos[i] % window of the cache wptrs[i] ([window, d]). Returns 0. */
SMILE_API int smile_ds_window_write(ST_Tensor src, ST_Tensor ptrs, ST_Tensor pos, int window);

/** row i of src (bf16 [n, d]) goes to row pos[i] / div of the cache ptrs[i], only where emit[i] (uint8 [n]; NULL = all). Returns 0. */
SMILE_API int smile_ds_cache_scatter(ST_Tensor src, ST_Tensor ptrs, ST_Tensor pos, int div, ST_Tensor emit);

/**
 * Compressor decode step (ratio >= 2): stores kv/score (float32 [1, n, hd]) in slot pos % ratio of each sequence's partial
 * group (float32 [ratio, hd] caches addressed by kvptrs / scptrs) and returns the pooled group float32 [1, n, hd] for the
 * rows with (pos + 1) % ratio == 0 (zeros elsewhere).
 */
SMILE_API ST_Tensor smile_ds_compress_state(ST_Tensor kv, ST_Tensor score, ST_Tensor kvptrs, ST_Tensor scptrs, ST_Tensor pos,
                                            int ratio);

/** smile_ds_index_score with one key cache per query row (kptrs [s], lens [s]) and -inf padding up to tmax: bf16 [1, s, tmax]. */
SMILE_API ST_Tensor smile_ds_index_score_ptrs(ST_Tensor q, ST_Tensor kptrs, ST_Tensor lens, ST_Tensor w, long long tmax);

/**
 * smile_ds_sparse_attention for one query per sequence: index j < window reads row j of the sequence's window ring
 * (wptrs[i]), j >= window row j - window of its compressed cache (cptrs[i], or none when cptrs is NULL). q: bf16 [1, m, h, 512].
 */
SMILE_API ST_Tensor smile_ds_sparse_attention_ptrs(ST_Tensor q, ST_Tensor wptrs, ST_Tensor cptrs, int window, ST_Tensor sink,
                                                   ST_Tensor idxs, double scale);

/* ---- graph-safe helpers: everything is derived on the device from static tensors ---- */

/** int64 [n] = 0, 1, ..., n - 1 on the device of {@code like}. */
SMILE_API ST_Tensor smile_ds_iota(ST_Tensor like, long long n);

/** int32 [1, n, window]: the window slots a decode token at pos[i] attends to (pos: int64 [n]), -1 for empty slots. */
/** The ring slots of the last {@code window} positions up to pos[i] (a ring of {@code ring >= window} slots): int32 [1, n, window], -1 before the start. */
SMILE_API ST_Tensor smile_ds_window_topk(ST_Tensor pos, int ring, int window);

/** smile_ds_cache_scatter for every row, writing only where (pos[i] + 1) % div == 0 when div > 1 (a completed group). Returns 0. */
SMILE_API int smile_ds_cache_scatter_gated(ST_Tensor src, ST_Tensor ptrs, ST_Tensor pos, int div);

/** Fills out[0..n-1] with the device data pointers of ts[i] (0 for NULL). Host-side only. Returns 0. */
SMILE_API int smile_ds_data_ptrs(const ST_Tensor *ts, int n, long long *out);

/** cudaProfilerStart / Stop for the calling thread's device (for ncu --profile-from-start off). */
SMILE_API int smile_ds_profiler(int start);

/** Writes the GPU global timer (ns) into buf[idx] (int64) from a one-thread kernel: a timestamp node inside a captured graph. */
SMILE_API int smile_ds_stamp(ST_Tensor buf, int idx);

#ifdef __cplusplus
}
#endif
