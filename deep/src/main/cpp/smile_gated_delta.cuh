/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * Fused recurrent gated delta rule (CUDA). Declared for smile_torch.cpp.
 */
#pragma once

#include <cstdint>

#ifdef USE_CUDA

/**
 * Runs the recurrent gated delta rule on CUDA float buffers.
 *
 * Layouts (all contiguous):
 *   q,k: [B, H, S, K]
 *   v:   [B, H, S, V]
 *   g,beta: [B, H, S]  ({@code g} is decay <em>logits</em>; kernel applies {@code exp})
 *   state: [B, H, K, V]  (mutated in place)
 *   out:   [B, H, S, V]
 *
 * @param scale     multiplied into Q ({@code 1/sqrt(K)}).
 * @param qk_l2norm non-zero to L2-normalize Q/K in-kernel along the last dim.
 * @return 0 on success, non-zero on CUDA error (message via smile_last_error).
 */
int smile_gated_delta_recurrent_cuda(
        const float *q, const float *k, const float *v,
        const float *g, const float *beta,
        float *state, float *out,
        int64_t B, int64_t H, int64_t S, int64_t K, int64_t V,
        float scale,
        int qk_l2norm,
        void *cuda_stream /* cudaStream_t, may be null = default */);

/** Last CUDA gated-delta error message (valid until next call). */
extern "C" const char *smile_gated_delta_last_error(void);

/**
 * Decode {@code L==1} depthwise causal conv1d update.
 *
 * Layouts (float contiguous):
 *   x:     [B, C, 1]
 *   state: [B, C, K-1]  (mutated in place: roll left, append x)
 *   w:     [C, K]
 *   out:   [B, C, 1]    (SiLU of weighted sum)
 *
 * @return 0 on success.
 */
int smile_causal_conv1d_update_decode_cuda(
        const float *x,
        float *state,
        const float *w,
        float *out,
        int64_t B, int64_t C, int64_t K,
        void *cuda_stream);

/**
 * Decode {@code L==1} depthwise causal conv1d + QKV split + K/V head repeat.
 *
 * Channel layout: {@code [Q (num_k_heads*head_k_dim), K (...), V (num_v_heads*head_v_dim)]}.
 * Writes {@code q,k: [B,1,num_v_heads,head_k_dim]}, {@code v: [B,1,num_v_heads,head_v_dim]}.
 *
 * @return 0 on success.
 */
int smile_causal_conv1d_update_split_qkv_cuda(
        const float *x,
        float *state,
        const float *w,
        float *q,
        float *k,
        float *v,
        int64_t B, int64_t C, int64_t K,
        int num_k_heads, int num_v_heads,
        int head_k_dim, int head_v_dim,
        void *cuda_stream);

#endif /* USE_CUDA */
