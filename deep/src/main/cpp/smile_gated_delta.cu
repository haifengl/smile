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
#include <cuda_bf16.h>
#include <cuda_fp16.h>
#include <cstdint>
#include <cstdlib>
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
        int qk_l2norm,
        GatedDeltaCkpt ckpt) {
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
        // Per-step state snapshot (checkpoint-replay for MTP verify windows).
        if (t < ckpt.n && ckpt.ptr[t] != nullptr) {
            float *ck_bh = ckpt.ptr[t] + ((b * H + h) * K) * V;
            for (int64_t i = tid; i < K * V; i += nthreads) {
                ck_bh[i] = s_state[i];
            }
        }
        __syncthreads();
    }

    // Write back final state.
    for (int64_t i = tid; i < K * V; i += nthreads) {
        state_bh[i] = s_state[i];
    }
}

/**
 * Decode/short-window variant for K == 128, V % 32 == 0 (Qwen3.5/3.8). The state columns are independent given q, k, so
 * the grid is (V / 32, B * H): each block owns a 128 x 32 slice of one head's state, one warp per 32 rows, one state
 * element per register per thread. With S1 = g * S0 + k (x) delta the per-token work needs a single cross-warp reduction:
 *   kv = g * (k . S0),  delta = (v - kv) * beta,  y = g * (q . S0) + (q . k) * delta.
 */
constexpr int kFastK = 128;
constexpr int kFastCols = 32;
constexpr int kFastRows = kFastK / 4; // rows per warp

__global__ void __launch_bounds__(128) gated_delta_recurrent_fast_kernel(
        const float *__restrict__ q,
        const float *__restrict__ k,
        const float *__restrict__ v,
        const float *__restrict__ g,
        const float *__restrict__ beta,
        float *__restrict__ state,
        float *__restrict__ out,
        int64_t H, int64_t S, int64_t V,
        float scale,
        int qk_l2norm,
        GatedDeltaCkpt ckpt,
        const __nv_bfloat16 *__restrict__ a_raw = nullptr,
        const __nv_bfloat16 *__restrict__ b_raw = nullptr,
        const float *__restrict__ a_log = nullptr,
        const float *__restrict__ dt_bias = nullptr,
        __nv_bfloat16 *__restrict__ out_bf16 = nullptr) {
    const int64_t bh = blockIdx.y;
    const int v0 = blockIdx.x * kFastCols;
    const int tid = threadIdx.x;
    const int lane = tid & 31;
    const int warp = tid >> 5;
    const int col = v0 + lane;

    __shared__ float s_k[2][kFastK];
    __shared__ float s_q[2][kFastK];
    __shared__ float s_norm[2][4][3];
    __shared__ float s_red[2][4][kFastCols][2];

    float *state_bh = state + bh * kFastK * V;
    float st[kFastRows];
    #pragma unroll
    for (int r = 0; r < kFastRows; ++r) {
        st[r] = state_bh[static_cast<int64_t>(warp * kFastRows + r) * V + col];
    }

    for (int64_t t = 0; t < S; ++t) {
        const int p = static_cast<int>(t & 1);
        const int64_t qk_off = (bh * S + t) * kFastK;
        const int64_t v_off = (bh * S + t) * V;
        float g_t, beta_t;
        if (a_raw != nullptr) {
            // Decode (S == 1) with the gates folded in: g = -exp(A_log) * softplus(a + dt_bias), beta = sigmoid(b).
            const int hh = static_cast<int>(bh % H);
            const int64_t ab_idx = ((bh / H) * S + t) * H + hh;  // a, b are [B, S, H]
            const float x = __bfloat162float(a_raw[ab_idx]) + dt_bias[hh];
            const float sp = x > 20.f ? x : log1pf(expf(x));
            g_t = expf(-expf(a_log[hh]) * sp);
            beta_t = 1.f / (1.f + expf(-__bfloat162float(b_raw[ab_idx])));
        } else {
            g_t = expf(g[bh * S + t]);
            beta_t = beta[bh * S + t];
        }

        const float kr = k[qk_off + tid];
        const float qr = q[qk_off + tid];
        s_k[p][tid] = kr;
        s_q[p][tid] = qr;
        float qq = qr * qr, kk = kr * kr, qk = qr * kr;
        #pragma unroll
        for (int o = 16; o > 0; o >>= 1) {
            qq += __shfl_xor_sync(0xffffffffu, qq, o);
            kk += __shfl_xor_sync(0xffffffffu, kk, o);
            qk += __shfl_xor_sync(0xffffffffu, qk, o);
        }
        if (lane == 0) {
            s_norm[p][warp][0] = qq;
            s_norm[p][warp][1] = kk;
            s_norm[p][warp][2] = qk;
        }
        const float vv = v[v_off + col];
        __syncthreads();

        qq = s_norm[p][0][0] + s_norm[p][1][0] + s_norm[p][2][0] + s_norm[p][3][0];
        kk = s_norm[p][0][1] + s_norm[p][1][1] + s_norm[p][2][1] + s_norm[p][3][1];
        qk = s_norm[p][0][2] + s_norm[p][1][2] + s_norm[p][2][2] + s_norm[p][3][2];
        float q_mul = scale;
        float k_mul = 1.f;
        if (qk_l2norm) {
            const float q_inv = rsqrtf(qq + 1e-6f);
            const float k_inv = rsqrtf(kk + 1e-6f);
            q_mul = q_inv * scale;
            k_mul = k_inv;
            qk *= q_inv * k_inv;
        }
        qk *= scale;

        float a = 0.f, b = 0.f;
        #pragma unroll
        for (int r = 0; r < kFastRows; ++r) {
            const int row = warp * kFastRows + r;
            a += st[r] * (s_k[p][row] * k_mul);
            b += st[r] * (s_q[p][row] * q_mul);
        }
        s_red[p][warp][lane][0] = a;
        s_red[p][warp][lane][1] = b;
        __syncthreads();

        a = s_red[p][0][lane][0] + s_red[p][1][lane][0] + s_red[p][2][lane][0] + s_red[p][3][lane][0];
        b = s_red[p][0][lane][1] + s_red[p][1][lane][1] + s_red[p][2][lane][1] + s_red[p][3][lane][1];
        const float delta = (vv - g_t * a) * beta_t;
        if (warp == 0) {
            const float y = g_t * b + qk * delta;
            if (out_bf16 != nullptr) {
                // [B, S, H, V]: the layout the output projection consumes (for S == 1 this is v_off)
                out_bf16[(((bh / H) * S + t) * H + (bh % H)) * V + col] = __float2bfloat16(y);
            } else {
                out[v_off + col] = y;
            }
        }
        #pragma unroll
        for (int r = 0; r < kFastRows; ++r) {
            st[r] = g_t * st[r] + (s_k[p][warp * kFastRows + r] * k_mul) * delta;
        }
        if (t < ckpt.n && ckpt.ptr[t] != nullptr) {
            float *ck_bh = ckpt.ptr[t] + bh * kFastK * V;
            #pragma unroll
            for (int r = 0; r < kFastRows; ++r) {
                ck_bh[static_cast<int64_t>(warp * kFastRows + r) * V + col] = st[r];
            }
        }
    }

    #pragma unroll
    for (int r = 0; r < kFastRows; ++r) {
        state_bh[static_cast<int64_t>(warp * kFastRows + r) * V + col] = st[r];
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
        void *cuda_stream,
        const GatedDeltaCkpt *ckpt_in) {
    g_gated_delta_error.clear();
    GatedDeltaCkpt ckpt{};
    if (ckpt_in != nullptr) {
        if (ckpt_in->n < 0 || ckpt_in->n > kGatedDeltaMaxCkpt) {
            g_gated_delta_error = "gated_delta: too many checkpoint slots";
            return -1;
        }
        ckpt = *ckpt_in;
    }
    if (K > kMaxKV || V > kMaxKV) {
        g_gated_delta_error = "gated_delta: head dim exceeds kernel limit";
        return -1;
    }
    if (B < 1 || H < 1 || S < 1 || K < 1 || V < 1) {
        g_gated_delta_error = "gated_delta: invalid shape";
        return -1;
    }

    cudaStream_t fast_stream = cuda_stream
            ? static_cast<cudaStream_t>(cuda_stream)
            : static_cast<cudaStream_t>(0);
    static const bool disable_fast = std::getenv("SMILE_GATED_DELTA_REFERENCE") != nullptr;
    if (K == kFastK && V % kFastCols == 0 && !disable_fast) {
        dim3 grid(static_cast<unsigned>(V / kFastCols), static_cast<unsigned>(B * H));
        gated_delta_recurrent_fast_kernel<<<grid, 128, 0, fast_stream>>>(
                q, k, v, g, beta, state, out, H, S, V, scale, qk_l2norm, ckpt);
        cudaError_t fast_err = cudaGetLastError();
        if (fast_err != cudaSuccess) {
            g_gated_delta_error = cudaGetErrorString(fast_err);
            return -1;
        }
        return 0;
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
            q, k, v, g, beta, state, out, B, H, S, K, V, scale, qk_l2norm, ckpt);
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

namespace {

/** One thread per (b, c): bf16 input and bf16 conv state, float taps; outputs are rounded to bf16 like the unfused path. */
__global__ void causal_conv1d_decode_bf16_kernel(
        const __nv_bfloat16 *__restrict__ x,
        __nv_bfloat16 *__restrict__ state,
        const float *__restrict__ w,
        float *__restrict__ q, float *__restrict__ k, float *__restrict__ v,
        int64_t B, int64_t C, int K,
        int num_k_heads, int num_v_heads, int head_k_dim, int head_v_dim) {
    const int64_t idx = static_cast<int64_t>(blockIdx.x) * blockDim.x + threadIdx.x;
    if (idx >= B * C) return;
    const int64_t b = idx / C;
    const int64_t c = idx - b * C;
    const int state_len = K - 1;
    __nv_bfloat16 *st = state + idx * state_len;
    const float *w_c = w + c * K;

    float acc = 0.f;
    for (int i = 0; i < state_len; ++i) {
        acc += __bfloat162float(st[i]) * w_c[i];
    }
    const __nv_bfloat16 x_bf = x[idx];
    acc += __bfloat162float(x_bf) * w_c[state_len];
    // SiLU then round to the compute dtype, as the unfused path does when it converts q/k/v back to bf16.
    const float val = __bfloat162float(__float2bfloat16(acc / (1.f + expf(-acc))));

    for (int i = 0; i + 1 < state_len; ++i) {
        st[i] = st[i + 1];
    }
    if (state_len > 0) {
        st[state_len - 1] = x_bf;
    }

    const int key_dim = num_k_heads * head_k_dim;
    const int rep = num_v_heads / num_k_heads;
    if (c < key_dim) {
        const int h = static_cast<int>(c / head_k_dim), d = static_cast<int>(c % head_k_dim);
        for (int r = 0; r < rep; ++r) {
            q[(b * num_v_heads + h * rep + r) * head_k_dim + d] = val;
        }
    } else if (c < 2 * key_dim) {
        const int64_t cc = c - key_dim;
        const int h = static_cast<int>(cc / head_k_dim), d = static_cast<int>(cc % head_k_dim);
        for (int r = 0; r < rep; ++r) {
            k[(b * num_v_heads + h * rep + r) * head_k_dim + d] = val;
        }
    } else {
        const int64_t cc = c - 2 * key_dim;
        v[b * num_v_heads * head_v_dim + cc] = val;
    }
}

} // namespace

int smile_gated_delta_decode_fused_cuda(
        const void *hidden, void *conv_state, const float *conv_w,
        const void *a, const void *b, const float *a_log, const float *dt_bias,
        float *rec_state, float *q_buf, float *k_buf, float *v_buf, void *out,
        int64_t B, int64_t C, int K,
        int num_k_heads, int num_v_heads, int head_k_dim, int head_v_dim,
        float scale, void *cuda_stream) {
    g_gated_delta_error.clear();
    if (B < 1 || K < 2 || K > 16 || head_k_dim != kFastK || head_v_dim % kFastCols != 0
            || num_k_heads < 1 || num_v_heads % num_k_heads != 0
            || C != int64_t(2) * num_k_heads * head_k_dim + int64_t(num_v_heads) * head_v_dim) {
        g_gated_delta_error = "gated_delta_decode_fused: unsupported shape";
        return -1;
    }
    cudaStream_t stream = cuda_stream ? static_cast<cudaStream_t>(cuda_stream) : static_cast<cudaStream_t>(0);
    const int64_t n = B * C;
    causal_conv1d_decode_bf16_kernel<<<static_cast<unsigned>((n + 255) / 256), 256, 0, stream>>>(
            static_cast<const __nv_bfloat16 *>(hidden), static_cast<__nv_bfloat16 *>(conv_state), conv_w,
            q_buf, k_buf, v_buf, B, C, K, num_k_heads, num_v_heads, head_k_dim, head_v_dim);
    dim3 grid(static_cast<unsigned>(head_v_dim / kFastCols), static_cast<unsigned>(B * num_v_heads));
    gated_delta_recurrent_fast_kernel<<<grid, 128, 0, stream>>>(
            q_buf, k_buf, v_buf, nullptr, nullptr, rec_state, nullptr, num_v_heads, 1, head_v_dim,
            scale, /*qk_l2norm=*/1, GatedDeltaCkpt{},
            static_cast<const __nv_bfloat16 *>(a), static_cast<const __nv_bfloat16 *>(b), a_log, dt_bias,
            static_cast<__nv_bfloat16 *>(out));
    cudaError_t err = cudaGetLastError();
    if (err != cudaSuccess) {
        g_gated_delta_error = cudaGetErrorString(err);
        return -1;
    }
    return 0;
}

int smile_gated_delta_recurrent_gates_cuda(
        const float *q, const float *k, const float *v,
        const void *a, const void *b, const float *a_log, const float *dt_bias,
        float *state, void *out_bf16,
        int64_t B, int64_t H, int64_t S, int64_t K, int64_t V,
        float scale, int qk_l2norm, void *cuda_stream, const GatedDeltaCkpt *ckpt_in) {
    g_gated_delta_error.clear();
    GatedDeltaCkpt ckpt{};
    if (ckpt_in != nullptr) {
        if (ckpt_in->n < 0 || ckpt_in->n > kGatedDeltaMaxCkpt) {
            g_gated_delta_error = "gated_delta: too many checkpoint slots";
            return -1;
        }
        ckpt = *ckpt_in;
    }
    if (K != kFastK || V % kFastCols != 0 || B < 1 || H < 1 || S < 1) {
        g_gated_delta_error = "gated_delta_recurrent_gates: unsupported shape";
        return -1;
    }
    cudaStream_t stream = cuda_stream ? static_cast<cudaStream_t>(cuda_stream) : static_cast<cudaStream_t>(0);
    dim3 grid(static_cast<unsigned>(V / kFastCols), static_cast<unsigned>(B * H));
    gated_delta_recurrent_fast_kernel<<<grid, 128, 0, stream>>>(
            q, k, v, nullptr, nullptr, state, nullptr, H, S, V, scale, qk_l2norm, ckpt,
            static_cast<const __nv_bfloat16 *>(a), static_cast<const __nv_bfloat16 *>(b), a_log, dt_bias,
            static_cast<__nv_bfloat16 *>(out_bf16));
    cudaError_t err = cudaGetLastError();
    if (err != cudaSuccess) {
        g_gated_delta_error = cudaGetErrorString(err);
        return -1;
    }
    return 0;
}

namespace {

__device__ __forceinline__ float ld_f(float x) { return x; }
__device__ __forceinline__ float ld_f(__nv_bfloat16 x) { return __bfloat162float(x); }
__device__ __forceinline__ float ld_f(__half x) { return __half2float(x); }
template <typename T> __device__ __forceinline__ T st_t(float x);
template <> __device__ __forceinline__ float st_t<float>(float x) { return x; }
template <> __device__ __forceinline__ __nv_bfloat16 st_t<__nv_bfloat16>(float x) { return __float2bfloat16(x); }
template <> __device__ __forceinline__ __half st_t<__half>(float x) { return __float2half(x); }

/** One thread per (b,c); loops over the S window positions with the conv window in registers. */
template <typename T>
__global__ void causal_conv1d_window_split_qkv_kernel(
        const T *__restrict__ x,
        T *__restrict__ state,
        const T *__restrict__ w,
        float *__restrict__ q,
        float *__restrict__ k,
        float *__restrict__ v,
        GatedDeltaConvCkpt ck,
        int64_t B, int64_t C, int64_t K, int64_t S,
        int num_k_heads, int num_v_heads, int head_k_dim, int head_v_dim) {
    const int64_t idx = static_cast<int64_t>(blockIdx.x) * blockDim.x + threadIdx.x;
    if (idx >= B * C) return;
    const int64_t b = idx / C;
    const int64_t c = idx - b * C;
    const int sl = static_cast<int>(K - 1);

    T *st_bc = state + idx * sl;
    float win[16];
    float wc[16];
    for (int j = 0; j < sl; ++j) win[j] = ld_f(st_bc[j]);
    for (int j = 0; j < K; ++j) wc[j] = ld_f(w[c * K + j]);

    const int key_dim = num_k_heads * head_k_dim;
    const int rep = num_v_heads / num_k_heads;

    for (int64_t t = 0; t < S; ++t) {
        const float xv = ld_f(x[(b * S + t) * C + c]);
        float acc = xv * wc[sl];
        for (int j = 0; j < sl; ++j) acc += win[j] * wc[j];
        const float sig = 1.f / (1.f + expf(-acc));
        // Round through the activation dtype like the per-token decode path
        // (which materializes bf16/half q/k/v before the recurrent rule).
        const float val = ld_f(st_t<T>(acc * sig));

        if (c < key_dim) {
            const int h = static_cast<int>(c / head_k_dim);
            const int d = static_cast<int>(c % head_k_dim);
            for (int r = 0; r < rep; ++r) {
                q[(((b * num_v_heads) + h * rep + r) * S + t) * head_k_dim + d] = val;
            }
        } else if (c < 2 * key_dim) {
            const int64_t cc = c - key_dim;
            const int h = static_cast<int>(cc / head_k_dim);
            const int d = static_cast<int>(cc % head_k_dim);
            for (int r = 0; r < rep; ++r) {
                k[(((b * num_v_heads) + h * rep + r) * S + t) * head_k_dim + d] = val;
            }
        } else {
            const int64_t cc = c - 2 * key_dim;
            const int h = static_cast<int>(cc / head_v_dim);
            const int d = static_cast<int>(cc % head_v_dim);
            v[(((b * num_v_heads) + h) * S + t) * head_v_dim + d] = val;
        }

        for (int j = 0; j + 1 < sl; ++j) win[j] = win[j + 1];
        if (sl > 0) win[sl - 1] = xv;
        if (t < ck.n && ck.ptr[t] != nullptr) {
            T *cp = static_cast<T *>(ck.ptr[t]) + idx * sl;
            for (int j = 0; j < sl; ++j) cp[j] = st_t<T>(win[j]);
        }
    }
    for (int j = 0; j < sl; ++j) st_bc[j] = st_t<T>(win[j]);
}

template <typename T>
int launch_conv_window(
        const void *x, void *state, const void *w,
        float *q, float *k, float *v, const GatedDeltaConvCkpt &ck,
        int64_t B, int64_t C, int64_t K, int64_t S,
        int hk, int hv, int dk, int dv, cudaStream_t stream) {
    const int64_t n = B * C;
    const int threads = 256;
    const int blocks = static_cast<int>((n + threads - 1) / threads);
    causal_conv1d_window_split_qkv_kernel<T><<<blocks, threads, 0, stream>>>(
            static_cast<const T *>(x), static_cast<T *>(state), static_cast<const T *>(w),
            q, k, v, ck, B, C, K, S, hk, hv, dk, dv);
    return cudaGetLastError() == cudaSuccess ? 0 : -1;
}

} // namespace

int smile_causal_conv1d_window_split_qkv_cuda(
        const void *x, void *state, const void *w, GatedDeltaDtype dtype,
        float *q, float *k, float *v,
        int64_t B, int64_t C, int64_t K, int64_t S,
        int num_k_heads, int num_v_heads, int head_k_dim, int head_v_dim,
        const GatedDeltaConvCkpt *ckpt,
        void *cuda_stream) {
    g_gated_delta_error.clear();
    if (B < 1 || C < 1 || K < 1 || K > 16 || S < 1
            || num_k_heads < 1 || num_v_heads < 1 || head_k_dim < 1 || head_v_dim < 1
            || num_v_heads % num_k_heads != 0
            || C != 2LL * num_k_heads * head_k_dim + static_cast<int64_t>(num_v_heads) * head_v_dim) {
        g_gated_delta_error = "causal_conv1d_window_split_qkv: invalid shape";
        return -1;
    }
    GatedDeltaConvCkpt ck{};
    if (ckpt != nullptr) {
        if (ckpt->n < 0 || ckpt->n > kGatedDeltaMaxCkpt) {
            g_gated_delta_error = "causal_conv1d_window_split_qkv: too many checkpoints";
            return -1;
        }
        ck = *ckpt;
    }
    cudaStream_t stream = cuda_stream
            ? static_cast<cudaStream_t>(cuda_stream)
            : static_cast<cudaStream_t>(0);
    int rc;
    switch (dtype) {
        case GatedDeltaDtype::kFloat:
            rc = launch_conv_window<float>(x, state, w, q, k, v, ck, B, C, K, S,
                    num_k_heads, num_v_heads, head_k_dim, head_v_dim, stream);
            break;
        case GatedDeltaDtype::kBFloat16:
            rc = launch_conv_window<__nv_bfloat16>(x, state, w, q, k, v, ck, B, C, K, S,
                    num_k_heads, num_v_heads, head_k_dim, head_v_dim, stream);
            break;
        default:
            rc = launch_conv_window<__half>(x, state, w, q, k, v, ck, B, C, K, S,
                    num_k_heads, num_v_heads, head_k_dim, head_v_dim, stream);
            break;
    }
    if (rc != 0) {
        g_gated_delta_error = cudaGetErrorString(cudaGetLastError());
        return -1;
    }
    return 0;
}

#endif /* USE_CUDA */
