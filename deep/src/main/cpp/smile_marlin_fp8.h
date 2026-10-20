/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE MXFP8 Marlin C ABI: weight-only GEMM with FP8 E4M3 weights and E8M0 scales (32-element groups)
 * and bfloat16 activations, for GPUs without native FP8 (Ampere). Kernel: vLLM generalized Marlin
 * (third_party/vllm_marlin, Apache-2.0).
 */
#pragma once

#include "smile_torch.h"

#ifdef __cplusplus
extern "C" {
#endif

/** Returns 1 when the MXFP8 Marlin kernels were compiled in. */
SMILE_API int smile_marlin_fp8_available(void);

/**
 * Repacks an MXFP8 weight into the Marlin tile layout.
 * w: FP8 E4M3 (or uint8 holding E4M3 bytes) [N, K] on CUDA; N % 64 == 0, K % 128 == 0.
 * Returns int32 [K / 16, N * 4], or NULL on error.
 */
SMILE_API ST_Tensor smile_marlin_fp8_repack_weight(ST_Tensor w);

/**
 * Permutes E8M0 scales ([N, K / 32] uint8) into the Marlin layout.
 * Returns uint8 [K / 32, N], or NULL on error.
 */
SMILE_API ST_Tensor smile_marlin_fp8_repack_scales(ST_Tensor s);

/**
 * Stacked variants for MoE experts: w [E, N, K] -> int32 [E, K / 16, N * 4];
 * s [E, N, K / 32] -> uint8 [E, K / 32, N]. NULL on error.
 */
SMILE_API ST_Tensor smile_marlin_fp8_repack_weight_stack(ST_Tensor w);
SMILE_API ST_Tensor smile_marlin_fp8_repack_scales_stack(ST_Tensor s);

/**
 * Groups top-k assignments by expert for the Marlin MoE kernel, entirely on the device (no host sync,
 * CUDA-graph friendly). Only experts in [expert_start, expert_start + num_local_experts) are kept; every
 * other assignment is dropped (expert parallelism).
 *
 * topk_ids: int64 or int32 [T, top_k] of global expert ids. Returns one int32 tensor laid out as
 * [sorted_token_ids (max_padded) | expert_ids (max_padded / block_size) | num_tokens_post_padded (1)],
 * where max_padded = round_up(T * top_k + num_local_experts * (block_size - 1), block_size). Padding
 * entries of sorted_token_ids equal T * top_k.
 */
SMILE_API ST_Tensor smile_moe_align_block_size(ST_Tensor topk_ids, int expert_start,
                                               int num_local_experts, int block_size);

/**
 * Marlin MoE GEMM over experts.
 * a: bfloat16 [size_m, K]; qweight: int32 [E, K/16, N*4]; scales: uint8 [E, K/32, N];
 * align: output of smile_moe_align_block_size; topk_weights: float32, T * top_k values.
 * Rows of A are addressed as sorted_id / top_k. Returns bfloat16 [size_m * top_k, N] whose row
 * sorted_id holds that assignment's result (optionally scaled by its top-k weight); rows of
 * dropped assignments are zero. NULL on error.
 */
SMILE_API ST_Tensor smile_marlin_moe_fp8_gemm(ST_Tensor a, ST_Tensor qweight, ST_Tensor scales,
                                              ST_Tensor align, ST_Tensor topk_weights,
                                              int block_size, int top_k, int mul_topk_weights);

/**
 * Re-expresses MXFP8 weights (E4M3 + one E8M0 scale per 32 elements) as DeepSeek-style block FP8 (E4M3 +
 * one float32 scale per 128x128 tile) for the block-scaled FP8 GEMM of Hopper.
 *
 * w: FP8 E4M3 (or uint8) [N, K] or [E, N, K] on CUDA, N and K multiples of 128; scale: uint8 [.., N, K/32].
 * Each tile keeps its largest group exponent as the tile scale and shifts the other groups' elements down
 * by the exponent difference (exact unless a value falls below the E4M3 subnormal range; on real MiniMax-M3
 * weights the relative error is ~1e-7). Returns the FP8 tensor (same shape as w) and writes the float32
 * scales [.., N/128, K/128] ("weight_scale_inv": W = fp8 * scale) to *scale_inv_out. NULL on error.
 */
SMILE_API ST_Tensor smile_mxfp8_to_block_fp8(ST_Tensor w, ST_Tensor scale, ST_Tensor *scale_inv_out);

/**
 * C = A @ dequant(W)^T.
 * a: bfloat16 [M, K] on CUDA; qweight/scales from the two repack calls above.
 * Returns a new bfloat16 [M, N] tensor, or NULL on error.
 */
SMILE_API ST_Tensor smile_marlin_fp8_gemm(ST_Tensor a, ST_Tensor qweight, ST_Tensor scales);

#ifdef __cplusplus
}
#endif
