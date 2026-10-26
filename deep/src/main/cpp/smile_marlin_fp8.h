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
 * Repacks block-FP8 scales (float32 [N/128, K/128], W = fp8 * scale per 128x128 tile) for the group-128 Marlin kernel:
 * returns bfloat16 [K/128, N] in the Marlin layout with the FP8 exponent bias folded in. NULL on error.
 * Weights are repacked with smile_marlin_fp8_repack_weight; smile_marlin_fp8_gemm accepts these scales.
 */
SMILE_API ST_Tensor smile_marlin_fp8_repack_block_scales(ST_Tensor s);

/**
 * Stacked variants for MoE experts: w [E, N, K] -> int32 [E, K / 16, N * 4];
 * s [E, N, K / 32] -> uint8 [E, K / 32, N]. NULL on error.
 */
SMILE_API ST_Tensor smile_marlin_fp8_repack_weight_stack(ST_Tensor w);
SMILE_API ST_Tensor smile_marlin_fp8_repack_scales_stack(ST_Tensor s);

/**
 * NVFP4 experts (FP4 E2M1 weights, E4M3 scales per 16 elements, one float32 global scale per expert).
 * smile_marlin_nvfp4_repack_weight_stack: w uint8 [E, N, K / 2] (two nibbles per byte) -> int32 [E, K / 16, N * 2].
 * smile_marlin_nvfp4_repack_scales_stack: s float8_e4m3fn or uint8 [E, N, K / 16] and the checkpoint's
 * weight_global_scale float32 [E] -> uint8 [E, K / 16, N] (Marlin layout, S0E5M3); *global_out receives the float32
 * [E] per-expert scale smile_marlin_moe_nvfp4_gemm expects. NULL on error.
 */
SMILE_API ST_Tensor smile_marlin_nvfp4_repack_weight_stack(ST_Tensor w);
SMILE_API ST_Tensor smile_marlin_nvfp4_repack_scales_stack(ST_Tensor s, ST_Tensor global, ST_Tensor* global_out);

/**
 * Dequantizes NVFP4 weights to bfloat16: packed uint8 [.., N, K/2], E4M3 scale [.., N, K/16];
 * the value is fp4 * scale / global_scale (the checkpoint's weight_global_scale). NULL on error.
 */
SMILE_API ST_Tensor smile_nvfp4_dequantize(ST_Tensor packed, ST_Tensor scale, float global_scale);

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
 * Same as smile_marlin_moe_fp8_gemm for NVFP4 experts: qweight int32 [E, K/16, N*2] and scales uint8 [E, K/16, N]
 * from the smile_marlin_nvfp4_repack_* functions, global_scale float32 [E] (their global_out). NULL on error.
 */
SMILE_API ST_Tensor smile_marlin_moe_nvfp4_gemm(ST_Tensor a, ST_Tensor qweight, ST_Tensor scales,
                                                ST_Tensor global_scale, ST_Tensor align,
                                                ST_Tensor topk_weights, int block_size, int top_k,
                                                int mul_topk_weights);

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
 * a: bfloat16 [M, K] on CUDA; qweight/scales from the repack calls above (uint8 E8M0 scales per 32 K for MXFP8,
 * or bfloat16 scales per 128 K for block FP8).
 * Returns a new bfloat16 [M, N] tensor, or NULL on error.
 */
SMILE_API ST_Tensor smile_marlin_fp8_gemm(ST_Tensor a, ST_Tensor qweight, ST_Tensor scales);

#ifdef __cplusplus
}
#endif
