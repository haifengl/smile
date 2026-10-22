/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * Fused CUDA kernels that replace chains of small elementwise ops in the decode path (MiniMax-M3 and any
 * model using Gemma-style RMSNorm). Each call is one kernel launch. All functions return NULL on error and
 * set the thread-local error string (see smile_torch_last_error); they never fall back silently, so the
 * caller can use the unfused path.
 */
#pragma once

#include "smile_torch.h"

#ifdef __cplusplus
extern "C" {
#endif

/** Returns 1 when the fused kernels were compiled in. */
SMILE_API int smile_minimax_ops_available(void);

/**
 * Gemma-style RMSNorm: y = x * rsqrt(mean(x^2) + eps) * (1 + w), accumulated in float32, output in x's dtype.
 * x: contiguous CUDA float32/float16/bfloat16 [.., D]; w: [D]. Returns a tensor shaped like x.
 */
SMILE_API ST_Tensor smile_gemma_rms_norm(ST_Tensor x, ST_Tensor w, double eps);

/**
 * Residual add fused with Gemma RMSNorm: sum = x + r (rounded to the dtype like an eager add), y = gemma_rms_norm(sum).
 * x, r: CUDA tensors of one shape/dtype [.., D]; w: [D]. Returns y and writes sum to *out_sum.
 */
SMILE_API ST_Tensor smile_add_gemma_rms_norm(ST_Tensor x, ST_Tensor r, ST_Tensor w, double eps, ST_Tensor *out_sum);

/** out = silu(gate) * up (float math, one rounding). gate, up: CUDA tensors of one shape/dtype. */
SMILE_API ST_Tensor smile_silu_mul(ST_Tensor gate, ST_Tensor up);

/**
 * Two small projections in one launch: a = x . wa^T, b = x . wb^T. x: bf16 CUDA [rows, K] (rows <= 8, K % 8 == 0);
 * wa, wb: contiguous bf16 [N, K]. Returns a [rows, N] and writes b [rows, N] to *out_b.
 */
SMILE_API ST_Tensor smile_gemv2(ST_Tensor x, ST_Tensor wa, ST_Tensor wb, ST_Tensor *out_b);

/**
 * out = attn * sigmoid(gate) with both factors rounded to the dtype like the eager ops. attn: contiguous [tokens, H*D];
 * gate: [B, S, H, D] view with unit D stride and uniform token stride (a slice of an interleaved q|gate projection).
 */
SMILE_API ST_Tensor smile_sigmoid_gate_mul(ST_Tensor attn, ST_Tensor gate);

/**
 * Writes the new tokens' K and V rows into the layer caches in one launch: row t of k/v ([tokens, ...] contiguous, same
 * dtype as the caches) goes to cache slot slots[t] (int64 [tokens]). k_layer/v_layer: contiguous [numSlots, ...] with the
 * same row size. Returns 0, or -1 with the error set (nothing written) when the layout is unsupported.
 */
SMILE_API int smile_kv_write(ST_Tensor k_layer, ST_Tensor v_layer, ST_Tensor k, ST_Tensor v, ST_Tensor slots);

/**
 * Per-head Gemma RMSNorm of q and k followed by rotate-half RoPE on the first rotary_dim dims, in one launch.
 * q: CUDA [B, S, Hq, D]; k: [B, S, Hk, D] (same dtype; unit stride over D, any head stride, uniform token stride, so slices of a fused projection need no copy); qw: [D]; kw: [D]. cos/sin: float32 or q's dtype,
 * [R, rotary_dim] where token t uses row t % R (R = batch for decode, sequence length for prefill).
 * Returns the new q and writes the new k to *out_k.
 */
SMILE_API ST_Tensor smile_qk_norm_rope(ST_Tensor q, ST_Tensor k, ST_Tensor qw, ST_Tensor kw,
                                       ST_Tensor cos, ST_Tensor sin, int rotary_dim, double eps,
                                       ST_Tensor *out_k);

/**
 * swigluoai: gate' = min(gate, limit); up' = clamp(up, -limit, limit);
 * out = gate' * sigmoid(alpha * gate') * (up' + 1). gate/up: CUDA [T, I] with unit column stride (views of a
 * fused [T, 2I] projection are fine). Returns a new contiguous [T, I] tensor in gate's dtype.
 */
SMILE_API ST_Tensor smile_swiglu_oai(ST_Tensor gate, ST_Tensor up, double alpha, double limit);

/**
 * MoE routing: scores = sigmoid(logits); the top_k experts are chosen on scores + bias; their (unbiased)
 * scores are normalised to sum to one and multiplied by scale.
 * logits: float32 CUDA [T, E] (E a multiple of 32, at most 1024); bias: float32 [E].
 * Returns int32 expert ids [T, top_k] (best first) and writes float32 weights [T, top_k] to *out_weights.
 */
SMILE_API ST_Tensor smile_moe_route(ST_Tensor logits, ST_Tensor bias, int top_k, double scale,
                                    ST_Tensor *out_weights);

/**
 * MiniMax Sparse Attention block selection for single-token decode, two launches. For every (row b, index
 * head g) the raw dot product of the query with each cached index key is max-reduced per block; visible
 * blocks are ranked with the init blocks first and the local blocks next, the top_k blocks are kept, and the
 * additive attention mask ({0, -inf}, causal) is written.
 * q: CUDA [B, G, D]; cache: contiguous [numSlots, 1, D] of q's dtype (one layer of the index-key cache);
 * slots: int64 [B * length] physical slot of every position (see KvCachePool.slotIndices);
 * positions: int32 [B] position of the query token. G <= 8, ceil(length / block_size) <= 8192.
 * Returns the mask [B, G, 1, length] in q's dtype.
 */
SMILE_API ST_Tensor smile_msa_decode_mask(ST_Tensor q, ST_Tensor cache, ST_Tensor slots, ST_Tensor positions,
                                          int length, int block_size, int topk, int init_blocks,
                                          int local_blocks);

/**
 * Same scoring and selection as smile_msa_decode_mask, but returns the chosen block ids instead of a mask:
 * int32 [T, G, topk], -1 where fewer than topk blocks are visible. No mask is written. T = B * seqlen query
 * tokens (q is [T, G, D]); token t belongs to row t / seqlen and sits at position positions[row] + t % seqlen, so
 * the same call serves decode (seqlen 1) and prefill windows. positions: int32 [B].
 */
SMILE_API ST_Tensor smile_msa_decode_select(ST_Tensor q, ST_Tensor cache, ST_Tensor slots, ST_Tensor positions,
                                            int length, int block_size, int topk, int init_blocks,
                                            int local_blocks, int seqlen);

/**
 * Block-sparse decode attention: each KV group's 16 query heads attend to the keys of their selected 128-key
 * blocks only (causal, so keys past the query position are skipped), reading K/V straight from the cache by slot.
 * q: bfloat16 [T, H, 128] (H = 16 * G; T = B * seqlen tokens as in smile_msa_decode_select); k_layer, v_layer: contiguous bfloat16 [numSlots, G, 128] (one layer of
 * the KV pool); slots: int64 [B * length]; positions: int32 [B]; sel: int32 [T, G, topk] from
 * smile_msa_decode_select. Returns bfloat16 [T, H, 128]. Two launches: per-block partial softmax, then a merge.
 */
SMILE_API ST_Tensor smile_sparse_decode_attention(ST_Tensor q, ST_Tensor k_layer, ST_Tensor v_layer,
                                                  ST_Tensor slots, ST_Tensor positions, ST_Tensor sel,
                                                  int length, double scale, int seqlen);

#ifdef __cplusplus
}
#endif
