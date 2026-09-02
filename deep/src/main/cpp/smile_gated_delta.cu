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
        float scale,
        int qk_l2norm) {
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
    float *s_blk = s_delta + V;            // [2 * nthreads] block-reduce scratch

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
        const float g_t = expf(g[gb_off]);
        const float beta_t = beta[gb_off];

        for (int64_t i = tid; i < K; i += nthreads) {
            s_k[i] = k[qk_off + i];
            s_q[i] = q[qk_off + i];
        }
        for (int64_t i = tid; i < V; i += nthreads) {
            s_v[i] = v[v_off + i];
            s_kv[i] = 0.f;
            s_y[i] = 0.f;
        }
        __syncthreads();

        if (qk_l2norm) {
            float q_acc = 0.f;
            float k_acc = 0.f;
            for (int64_t i = tid; i < K; i += nthreads) {
                q_acc += s_q[i] * s_q[i];
                k_acc += s_k[i] * s_k[i];
            }
            s_blk[tid] = q_acc;
            s_blk[nthreads + tid] = k_acc;
            __syncthreads();
            for (int offset = nthreads / 2; offset > 0; offset >>= 1) {
                if (tid < offset) {
                    s_blk[tid] += s_blk[tid + offset];
                    s_blk[nthreads + tid] += s_blk[nthreads + tid + offset];
                }
                __syncthreads();
            }
            const float q_inv = rsqrtf(s_blk[0] + 1e-6f);
            const float k_inv = rsqrtf(s_blk[nthreads] + 1e-6f);
            for (int64_t i = tid; i < K; i += nthreads) {
                s_q[i] *= q_inv;
                s_k[i] *= k_inv;
            }
            __syncthreads();
        }

        for (int64_t i = tid; i < K; i += nthreads) {
            s_q[i] *= scale;
        }
        __syncthreads();

        // Decay: state *= exp(g)
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
        int qk_l2norm,
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
    const size_t smem = static_cast<size_t>(
            (K * V) + K + K + V + V + V + V + 2 * threads) * sizeof(float);
    cudaStream_t stream = cuda_stream
            ? static_cast<cudaStream_t>(cuda_stream)
            : static_cast<cudaStream_t>(0);

    int dev = 0;
    cudaGetDevice(&dev);
    int default_smem = 0;
    int optin_smem = 0;
    cudaDeviceGetAttribute(&default_smem, cudaDevAttrMaxSharedMemoryPerBlock, dev);
    cudaDeviceGetAttribute(&optin_smem, cudaDevAttrMaxSharedMemoryPerBlockOptin, dev);
    if (optin_smem <= 0) {
        optin_smem = default_smem;
    }
    if (smem > static_cast<size_t>(optin_smem)) {
        g_gated_delta_error = "gated_delta: shared memory exceeds device opt-in limit";
        return -1;
    }
    if (smem > static_cast<size_t>(default_smem)) {
        cudaError_t attr_err = cudaFuncSetAttribute(
                gated_delta_recurrent_kernel,
                cudaFuncAttributeMaxDynamicSharedMemorySize,
                static_cast<int>(smem));
        if (attr_err != cudaSuccess) {
            g_gated_delta_error = cudaGetErrorString(attr_err);
            return -1;
        }
    }

    gated_delta_recurrent_kernel<<<static_cast<unsigned>(blocks), threads, smem, stream>>>(
            q, k, v, g, beta, state, out, B, H, S, K, V, scale, qk_l2norm);
    cudaError_t err = cudaGetLastError();
    if (err != cudaSuccess) {
        g_gated_delta_error = cudaGetErrorString(err);
        return -1;
    }
    // Do not device-synchronize here — callers chain on the same stream.
    return 0;
}

namespace {

/** One thread per (b,c): weighted sum over K + SiLU + roll state. */
__global__ void causal_conv1d_update_decode_kernel(
        const float *__restrict__ x,
        float *__restrict__ state,
        const float *__restrict__ w,
        float *__restrict__ out,
        int64_t B, int64_t C, int64_t K) {
    const int64_t idx = static_cast<int64_t>(blockIdx.x) * blockDim.x + threadIdx.x;
    const int64_t BC = B * C;
    if (idx >= BC) return;
    const int64_t b = idx / C;
    const int64_t c = idx - b * C;
    const int64_t state_len = K - 1;

    const float *x_bc = x + (b * C + c);
    float *st_bc = state + ((b * C + c) * state_len);
    const float *w_c = w + c * K;

    float acc = 0.f;
    for (int64_t k = 0; k < state_len; ++k) {
        acc += st_bc[k] * w_c[k];
    }
    const float x_val = x_bc[0];
    acc += x_val * w_c[state_len];

    // SiLU(x) = x * sigmoid(x)
    const float sig = 1.f / (1.f + expf(-acc));
    out[b * C + c] = acc * sig;

    // Roll state left and append x.
    for (int64_t k = 0; k < state_len - 1; ++k) {
        st_bc[k] = st_bc[k + 1];
    }
    if (state_len > 0) {
        st_bc[state_len - 1] = x_val;
    }
}

} // namespace

int smile_causal_conv1d_update_decode_cuda(
        const float *x, float *state, const float *w, float *out,
        int64_t B, int64_t C, int64_t K,
        void *cuda_stream) {
    g_gated_delta_error.clear();
    if (B < 1 || C < 1 || K < 1 || K > 16) {
        g_gated_delta_error = "causal_conv1d_update: invalid shape";
        return -1;
    }
    const int64_t n = B * C;
    const int threads = 256;
    const int blocks = static_cast<int>((n + threads - 1) / threads);
    cudaStream_t stream = cuda_stream
            ? static_cast<cudaStream_t>(cuda_stream)
            : static_cast<cudaStream_t>(0);
    causal_conv1d_update_decode_kernel<<<blocks, threads, 0, stream>>>(
            x, state, w, out, B, C, K);
    cudaError_t err = cudaGetLastError();
    if (err != cudaSuccess) {
        g_gated_delta_error = cudaGetErrorString(err);
        return -1;
    }
    return 0;
}

__global__ void causal_conv1d_update_split_qkv_kernel(
        const float *__restrict__ x,
        float *__restrict__ state,
        const float *__restrict__ w,
        float *__restrict__ q,
        float *__restrict__ k,
        float *__restrict__ v,
        int64_t B, int64_t C, int64_t K,
        int num_k_heads, int num_v_heads,
        int head_k_dim, int head_v_dim) {
    const int64_t idx = static_cast<int64_t>(blockIdx.x) * blockDim.x + threadIdx.x;
    const int64_t BC = B * C;
    if (idx >= BC) return;
    const int64_t b = idx / C;
    const int64_t c = idx - b * C;
    const int64_t state_len = K - 1;

    const float *x_bc = x + (b * C + c);
    float *st_bc = state + ((b * C + c) * state_len);
    const float *w_c = w + c * K;

    float acc = 0.f;
    for (int64_t ki = 0; ki < state_len; ++ki) {
        acc += st_bc[ki] * w_c[ki];
    }
    const float x_val = x_bc[0];
    acc += x_val * w_c[state_len];
    const float sig = 1.f / (1.f + expf(-acc));
    const float val = acc * sig;

    for (int64_t ki = 0; ki < state_len - 1; ++ki) {
        st_bc[ki] = st_bc[ki + 1];
    }
    if (state_len > 0) {
        st_bc[state_len - 1] = x_val;
    }

    const int key_dim = num_k_heads * head_k_dim;
    const int rep = num_v_heads / num_k_heads;
    const int64_t q_stride = static_cast<int64_t>(num_v_heads) * head_k_dim;
    const int64_t v_stride = static_cast<int64_t>(num_v_heads) * head_v_dim;
    const int64_t bq = b * q_stride;
    const int64_t bv = b * v_stride;

    if (c < key_dim) {
        const int h = static_cast<int>(c / head_k_dim);
        const int d = static_cast<int>(c % head_k_dim);
        for (int r = 0; r < rep; ++r) {
            q[bq + (h * rep + r) * head_k_dim + d] = val;
        }
    } else if (c < 2 * key_dim) {
        const int64_t cc = c - key_dim;
        const int h = static_cast<int>(cc / head_k_dim);
        const int d = static_cast<int>(cc % head_k_dim);
        for (int r = 0; r < rep; ++r) {
            k[bq + (h * rep + r) * head_k_dim + d] = val;
        }
    } else {
        const int64_t cc = c - 2 * key_dim;
        const int h = static_cast<int>(cc / head_v_dim);
        const int d = static_cast<int>(cc % head_v_dim);
        v[bv + h * head_v_dim + d] = val;
    }
}

int smile_causal_conv1d_update_split_qkv_cuda(
        const float *x, float *state, const float *w,
        float *q, float *k, float *v,
        int64_t B, int64_t C, int64_t K,
        int num_k_heads, int num_v_heads,
        int head_k_dim, int head_v_dim,
        void *cuda_stream) {
    g_gated_delta_error.clear();
    if (B < 1 || C < 1 || K < 1 || K > 16
            || num_k_heads < 1 || num_v_heads < 1
            || head_k_dim < 1 || head_v_dim < 1
            || num_v_heads % num_k_heads != 0) {
        g_gated_delta_error = "causal_conv1d_update_split_qkv: invalid shape";
        return -1;
    }
    const int key_dim = num_k_heads * head_k_dim;
    const int value_dim = num_v_heads * head_v_dim;
    if (C != 2 * key_dim + value_dim) {
        g_gated_delta_error = "causal_conv1d_update_split_qkv: channel mismatch";
        return -1;
    }
    const int64_t n = B * C;
    const int threads = 256;
    const int blocks = static_cast<int>((n + threads - 1) / threads);
    cudaStream_t stream = cuda_stream
            ? static_cast<cudaStream_t>(cuda_stream)
            : static_cast<cudaStream_t>(0);
    causal_conv1d_update_split_qkv_kernel<<<blocks, threads, 0, stream>>>(
            x, state, w, q, k, v, B, C, K,
            num_k_heads, num_v_heads, head_k_dim, head_v_dim);
    cudaError_t err = cudaGetLastError();
    if (err != cudaSuccess) {
        g_gated_delta_error = cudaGetErrorString(err);
        return -1;
    }
    return 0;
}

#endif /* USE_CUDA */
