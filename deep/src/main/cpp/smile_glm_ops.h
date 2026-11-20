/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * CUDA kernels of GLM-5.3-Flash: Kimi-style delta-rule linear attention (KDA). All functions return NULL on error
 * and set the thread-local error string (see smile_torch_last_error).
 */
#pragma once

#include "smile_torch.h"

#ifdef __cplusplus
extern "C" {
#endif

/** Returns 1 when the GLM kernels were compiled in. */
SMILE_API int smile_glm_ops_available(void);

/**
 * KDA over T new tokens for each of B requests, fully fused per (request, head, 32 value columns): the short causal
 * convolution (kernel 4, SiLU) over the raw q|k|v rows, L2 normalisation of q and k, the forget gate
 * g = bound * sigmoid(exp(a_log[h]) * (fg + dt_bias)) per key channel, beta = sigmoid(braw), and the recurrence
 *   S = S * exp(g); delta = (v - S^T k) * beta; S += k delta^T; o = S^T (q * 128^-0.5).
 *
 * raw   float32 [B, T, 3*W]   q|k|v projections before the convolution (W = H * 128).
 * fg    float32 [B, T, W]     f_b(f_a(x)), without dt_bias.
 * braw  float32 [B, T, H]     b_proj(x), before the sigmoid.
 * convw float32 [3*W, 4]      depthwise convolution taps (tap j multiplies the input of step t - 3 + j).
 * a_log float32 [H], dt_bias float32 [W].
 * conv_state float32 [S, 3, 3*W]  the last three raw rows of every request slot; updated in place after the recurrence.
 * rec_state  float32 [S, H, 128, 128]  state [key, value] of every slot; updated in place.
 * slots int32 CUDA [B]        the slot of each request.
 * Returns the output float32 [B, T, H, 128] (before the gated output norm).
 */
SMILE_API ST_Tensor smile_kda_forward(ST_Tensor raw, ST_Tensor fg, ST_Tensor braw, ST_Tensor convw, ST_Tensor a_log,
                                      ST_Tensor dt_bias, ST_Tensor conv_state, ST_Tensor rec_state, ST_Tensor slots,
                                      double gate_bound);

/**
 * KDA over a speculative window: like {@link smile_kda_forward} but the conv state is left untouched and the state after
 * every row is stored in rec_save float32 [B, T, H, 128, 128]; rec_state holds the state after the whole window.
 */
SMILE_API ST_Tensor smile_kda_forward_window(ST_Tensor raw, ST_Tensor fg, ST_Tensor braw, ST_Tensor convw, ST_Tensor a_log,
                                             ST_Tensor dt_bias, ST_Tensor conv_state, ST_Tensor rec_state, ST_Tensor slots,
                                             ST_Tensor rec_save, double gate_bound);

/**
 * Keeps the first accept[b] (>= 1) rows of a window run by smile_kda_forward_window: recurrent state = state after row
 * accept[b] - 1, conv state = the last three of (old conv state, raw rows 0..accept[b] - 1). raw_save is the raw
 * float32 [B, T, 3W] given to the window forward. Returns 0, or -1 with the error set.
 */
SMILE_API int smile_kda_commit(ST_Tensor raw_save, ST_Tensor conv_state, ST_Tensor rec_state, ST_Tensor rec_save,
                               ST_Tensor slots, ST_Tensor accept);

/**
 * MLA append of a window of `rows` consecutive tokens per request (request-major rows: b * rows + j): latent rows and the
 * pools the window completes; the ring is only read. lens int32 [B] = tokens before the window.
 */
SMILE_API int smile_mla_window_append(ST_Tensor lat_new, ST_Tensor key_new, ST_Tensor gate_new, ST_Tensor ape,
                                      ST_Tensor lat_ptrs, ST_Tensor pool_ptrs, ST_Tensor pend_ptrs, ST_Tensor lens,
                                      long long rows, int fp8);

/** Writes the ring slots of the first accept[b] rows of a window (after smile_mla_window_append). */
SMILE_API int smile_mla_window_commit(ST_Tensor key_new, ST_Tensor gate_new, ST_Tensor pend_ptrs, ST_Tensor lens,
                                      ST_Tensor accept, long long rows);

/**
 * GLM's clamped SwiGLU of a fused gate|up projection: out[r, c] = silu(min(gu[r, c], limit)) * clamp(gu[r, inter + c],
 * -limit, limit) (no clamp when limit <= 0), float32 math, bfloat16 result [rows, inter]. gu: contiguous CUDA
 * float32/bfloat16 [rows, 2 * inter].
 */
SMILE_API ST_Tensor smile_glm_swiglu(ST_Tensor gu, double limit);

/**
 * The elementwise tail of GLM's MLA projections in one launch. y: contiguous CUDA float32 [n, 2336] = x . [W_q_a (1536);
 * W_kv_a (512); W_indexer_k (128); W_compress_gate (128); W_indexer_weights (32)]^T. Writes out[0] = rms_norm(y[:, :1536]) *
 * qa_w as bfloat16 [n, 1536], out[1] = rms_norm(y[:, 1536:2048]) * kv_w float32 [n, 512], out[2] = layer_norm(y[:, 2048:2176])
 * * ln_w + ln_b float32 [n, 128], out[3] = y[:, 2176:2304] [n, 128], out[4] = y[:, 2304:] * w_scale [n, 32]. Returns 0, or -1
 * with the error set.
 */
SMILE_API int smile_glm_mla_proj_epilogue(ST_Tensor y, ST_Tensor qa_w, ST_Tensor kv_w, ST_Tensor ln_w, ST_Tensor ln_b,
                                          double eps, double ln_eps, double w_scale, ST_Tensor *out);

/**
 * Decode-step absorption of W_UK into the MLA queries and conversion of the indexer queries, in one launch.
 * qi: contiguous CUDA float32 [B, heads * 256 + 4096] (q_b | indexer q); wuk: contiguous bfloat16 [heads, 256, 512].
 * out[0] = bfloat16 [B, heads, 512] with sum_n bf16(q[b, h, n]) * wuk[h, n, r]; out[1] = bfloat16 [B, 32, 128] indexer
 * queries. Returns 0, or -1 with the error set.
 */
SMILE_API int smile_glm_mla_absorb(ST_Tensor qi, ST_Tensor wuk, long long heads, ST_Tensor *out);

/**
 * Decode-step value expansion: out[b, h * 256 + v] = bf16(sum_r bf16(ctx[b, h, r]) * wuvt[h, r, v]).
 * ctx: contiguous float32 [B, heads, 512]; wuvt: contiguous bfloat16 [heads, 512, 256]. Returns bfloat16 [B, heads * 256].
 */
SMILE_API ST_Tensor smile_glm_mla_wuv(ST_Tensor ctx, ST_Tensor wuvt);

/**
 * Indexer scores of GLM-5.3-Flash for {@code n} queries against a range of pooled keys, fused on tensor cores:
 *   out[s, j] = sum_h w[s, h] * relu(scale * q[s, h] . pk[pool0 + j])   when pool (pool0 + j) is visible to query s,
 * i.e. 4 * (pool0 + j) + 3 <= p0 + s (pools hold 4 tokens), otherwise -3.4028235e38 (float32 lowest). Nothing of
 * size [n, heads, count] is materialised.
 *
 * q  bfloat16 CUDA [n, 32, 128] contiguous (indexer queries); w float32 [n, 32] (head weights, already scaled);
 * pk bfloat16 CUDA [P, 128] contiguous (all pooled keys); pool0, count select the range [pool0, pool0 + count) of pk.
 * p0 is the position of the first query. Returns float32 [n, count].
 */
SMILE_API ST_Tensor smile_indexer_scores(ST_Tensor q, ST_Tensor w, ST_Tensor pk, long long pool0, long long count,
                                         long long p0, double scale);

/*
 * Decode-step kernels of GLM's MLA + indexer for B requests at once, written for CUDA-graph capture: every shape is
 * fixed; the per-request caches are reached through device tables of base pointers ({@code int64 [B]}, refreshed
 * before each replay) and the per-request token counts through {@code lens int32 [B]} (the number of tokens
 * BEFORE this step = the write position of the new token). Caches: latents bfloat16 [cap, 512]; pooled indexer keys
 * bfloat16 [cap / 4, 128]; pending ring float32 [4, 256] (slot = position % 4: key 128, gate 128) holding the keys and
 * gates of the tokens of the open pool.
 */

/**
 * Appends the new token: latent row to the latent cache, key/gate to the ring, and when the token completes a pool
 * (position % 4 == 3) the pooled key (softmax over the 4 slots of gate + ape, per channel) to the pool cache.
 * lat_new/key_new/gate_new float32 [B, 512]/[B, 128]/[B, 128]; ape float32 [4, 128]. Returns 0, or -1 with the error set.
 */
SMILE_API int smile_mla_decode_append(ST_Tensor lat_new, ST_Tensor key_new, ST_Tensor gate_new, ST_Tensor ape,
                                      ST_Tensor lat_ptrs, ST_Tensor pool_ptrs, ST_Tensor pend_ptrs, ST_Tensor lens, int fp8);

/**
 * Indexer scores of the new token against all complete pools (after the append): scores[b, p] for p < (lens[b] + 1) / 4,
 * the head-weighted ReLU of q . pool key. q bfloat16 [B, 32, 128], w float32 [B, 32] (head weights, scaled),
 * pool_ptrs int64 [B]; max_pools is the row stride of the result float32 [B, max_pools] (entries beyond a request's
 * pool count are not written).
 */
SMILE_API ST_Tensor smile_mla_decode_scores(ST_Tensor q, ST_Tensor w, ST_Tensor pool_ptrs, ST_Tensor lens,
                                            long long max_pools, double scale);

/**
 * Top-k pools per request by radix selection, expanded to token ids: int32 [B, topk_pools * 4 + 3]: the 4 tokens of every
 * selected pool, -1 padding, and the tokens of the open pool at the end. scores as returned by smile_mla_decode_scores.
 */
SMILE_API ST_Tensor smile_mla_decode_topk(ST_Tensor scores, ST_Tensor lens, long long topk_pools);

/**
 * Multi-query attention in the latent space over the selected tokens: ctx[b, h] = sum_j softmax_j(scale * qabs[b, h] .
 * latent[idx[b, j]]) latent[idx[b, j]] (idx < 0 skipped). qabs bfloat16 [B, 64, 512]; lat_ptrs int64 [B]; idx int32
 * [B, W] with W <= 2056. Returns float32 [B, 64, 512].
 */
SMILE_API ST_Tensor smile_mla_decode_attend(ST_Tensor qabs, ST_Tensor lat_ptrs, ST_Tensor idx, double scale);

/**
 * As smile_mla_decode_attend, but with the row lengths: lens int32 [B] (tokens before the row, as given to smile_mla_decode_topk)
 * and the top_pools it was run with (idx width == 4 * top_pools + 3). Only the valid prefix of each id row (4 * min((len + 1) /
 * 4, top_pools) tokens) and the three open-pool slots are processed, which makes short contexts much cheaper.
 */
SMILE_API ST_Tensor smile_mla_decode_attend2(ST_Tensor qabs, ST_Tensor lat_ptrs, ST_Tensor idx, ST_Tensor lens,
                                             long long top_pools, double scale, int fp8);

/*
 * fp8 latent cache. With fp8 != 0 the latent cache rows are 528-byte records: 512 e4m3 values followed by four float32
 * scales (one per 128 dimensions, scale = max|x| / 448), instead of 512 bfloat16. The append calls quantize the new rows,
 * the attend call dequantizes while loading (lens may be null for the full id width).
 */

/** Quantizes float32 [n, 512] rows into the fp8 cache layout uint8 [n, 528]. Returns 0, or -1 with the error set. */
SMILE_API int smile_glm_latent_quantize(ST_Tensor src, ST_Tensor dst);

/** Dequantizes rows ids (int64 [n]) of an fp8 cache uint8 [T, 528] into bfloat16 [n, 512]. */
SMILE_API ST_Tensor smile_glm_latent_gather(ST_Tensor cache, ST_Tensor ids);

#ifdef __cplusplus
}
#endif
