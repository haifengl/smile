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
 * Per-head Gemma RMSNorm of q and k followed by rotate-half RoPE on the first rotary_dim dims, in one launch.
 * q: CUDA [B, S, Hq, D]; k: [B, S, Hk, D] (same dtype; unit stride over D and heads, uniform token stride, so slices of a fused projection need no copy); qw: [D]; kw: [D]. cos/sin: float32 or q's dtype,
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

#ifdef __cplusplus
}
#endif
