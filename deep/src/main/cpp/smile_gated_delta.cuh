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
 *   g,beta: [B, H, S]  (g already exponentiated decay factors)
 *   state: [B, H, K, V]  (mutated in place)
 *   out:   [B, H, S, V]
 *
 * @return 0 on success, non-zero on CUDA error (message via smile_last_error).
 */
int smile_gated_delta_recurrent_cuda(
        const float *q, const float *k, const float *v,
        const float *g, const float *beta,
        float *state, float *out,
        int64_t B, int64_t H, int64_t S, int64_t K, int64_t V,
        float scale,
        void *cuda_stream /* cudaStream_t, may be null = default */);

/** Last CUDA gated-delta error message (valid until next call). */
extern "C" const char *smile_gated_delta_last_error(void);

#endif /* USE_CUDA */
