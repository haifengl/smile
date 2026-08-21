/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * Fused recurrent gated delta rule CUDA kernel for Qwen3.5 GatedDeltaNet.
 *
 * One thread-block per (batch, head). Threads cooperatively scan the K/V dims.
 * The sequence loop stays on-device so decode/prefill avoid per-token launches.
 */

#include "smile_gated_delta.cuh"

#ifdef USE_CUDA

#include <cuda_runtime.h>
#include <cstdint>
#include <string>

static thread_local std::string g_gated_delta_error;

extern "C" const char *smile_gated_delta_last_error(void) {
    return g_gated_delta_error.c_str();
}

namespace {

constexpr int kMaxKV = 256; // head dims used by Qwen3.5 (128); pad for shared mem

__global__ void gated_delta_recurrent_kernel(
        const float *__restrict__ q,
        const float *__restrict__ k,
        const float *__restrict__ v,
        const float *__restrict__ g,
        const float *__restrict__ beta,
        float *__restrict__ state,
        float *__restrict__ out,
        int64_t B, int64_t H, int64_t S, int64_t K, int64_t V,
        float scale) {
    const int64_t bh = blockIdx.x;
    if (bh >= B * H) return;
    const int64_t b = bh / H;
    const int64_t h = bh - b * H;

    // Shared buffers for one head's state row / vectors.
    extern __shared__ float smem[];
    float *s_state = smem;                 // [K * V]
    float *s_k = s_state + K * V;          // [K]
    float *s_q = s_k + K;                  // [K]
    float *s_v = s_q + K;                  // [V]
    float *s_kv = s_v + V;                 // [V]
    float *s_y = s_kv + V;                 // [V]
    float *s_delta = s_y + V;              // [V]

    const int tid = threadIdx.x;
    const int nthreads = blockDim.x;

    // Load initial state into shared memory (mutable: written back after the scan).
    float *state_bh = state + ((b * H + h) * K) * V;
    for (int64_t i = tid; i < K * V; i += nthreads) {
        s_state[i] = state_bh[i];
    }
    __syncthreads();

    for (int64_t t = 0; t < S; ++t) {
        const int64_t qk_off = ((b * H + h) * S + t) * K;
        const int64_t v_off = ((b * H + h) * S + t) * V;
        const int64_t gb_off = (b * H + h) * S + t;
        const float g_t = g[gb_off];
        const float beta_t = beta[gb_off];

        for (int64_t i = tid; i < K; i += nthreads) {
            s_k[i] = k[qk_off + i];
            s_q[i] = q[qk_off + i] * scale;
        }
        for (int64_t i = tid; i < V; i += nthreads) {
            s_v[i] = v[v_off + i];
            s_kv[i] = 0.f;
            s_y[i] = 0.f;
        }
        __syncthreads();

        // Decay: state *= g
        for (int64_t i = tid; i < K * V; i += nthreads) {
            s_state[i] *= g_t;
        }
        __syncthreads();

        // kv[v] = sum_k state[k,v] * k[k]
        for (int64_t vv = tid; vv < V; vv += nthreads) {
            float acc = 0.f;
            for (int64_t kk = 0; kk < K; ++kk) {
                acc += s_state[kk * V + vv] * s_k[kk];
            }
            s_kv[vv] = acc;
        }
        __syncthreads();

        // delta[v] = (v - kv) * beta
        for (int64_t vv = tid; vv < V; vv += nthreads) {
            s_delta[vv] = (s_v[vv] - s_kv[vv]) * beta_t;
        }
        __syncthreads();

        // state[k,v] += k[k] * delta[v]
        for (int64_t i = tid; i < K * V; i += nthreads) {
            const int64_t kk = i / V;
            const int64_t vv = i - kk * V;
            s_state[i] += s_k[kk] * s_delta[vv];
        }
        __syncthreads();

        // y[v] = sum_k q[k] * state[k,v]
        for (int64_t vv = tid; vv < V; vv += nthreads) {
            float acc = 0.f;
            for (int64_t kk = 0; kk < K; ++kk) {
                acc += s_q[kk] * s_state[kk * V + vv];
            }
            s_y[vv] = acc;
        }
        __syncthreads();

        float *out_t = out + v_off;
        for (int64_t vv = tid; vv < V; vv += nthreads) {
            out_t[vv] = s_y[vv];
        }
        __syncthreads();
    }

    // Write back final state.
    for (int64_t i = tid; i < K * V; i += nthreads) {
        state_bh[i] = s_state[i];
    }
}

} // namespace

int smile_gated_delta_recurrent_cuda(
        const float *q, const float *k, const float *v,
        const float *g, const float *beta,
        float *state, float *out,
        int64_t B, int64_t H, int64_t S, int64_t K, int64_t V,
        float scale,
        void *cuda_stream) {
    g_gated_delta_error.clear();
    if (K > kMaxKV || V > kMaxKV) {
        g_gated_delta_error = "gated_delta: head dim exceeds kernel limit";
        return -1;
    }
    if (B < 1 || H < 1 || S < 1 || K < 1 || V < 1) {
        g_gated_delta_error = "gated_delta: invalid shape";
        return -1;
    }

    const int64_t blocks = B * H;
    const int threads = 128;
    const size_t smem = static_cast<size_t>((K * V) + K + K + V + V + V + V) * sizeof(float);
    cudaStream_t stream = cuda_stream
            ? static_cast<cudaStream_t>(cuda_stream)
            : static_cast<cudaStream_t>(0);

    gated_delta_recurrent_kernel<<<static_cast<unsigned>(blocks), threads, smem, stream>>>(
            q, k, v, g, beta, state, out, B, H, S, K, V, scale);
    cudaError_t err = cudaGetLastError();
    if (err != cudaSuccess) {
        g_gated_delta_error = cudaGetErrorString(err);
        return -1;
    }
    // Do not device-synchronize here — callers chain on the same stream.
    return 0;
}

#endif /* USE_CUDA */
