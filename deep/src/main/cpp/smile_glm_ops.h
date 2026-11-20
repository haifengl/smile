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
                                      ST_Tensor lat_ptrs, ST_Tensor pool_ptrs, ST_Tensor pend_ptrs, ST_Tensor lens);

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

#ifdef __cplusplus
}
#endif
