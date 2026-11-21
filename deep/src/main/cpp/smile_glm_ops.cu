/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * GLM-5.3-Flash kernels; see smile_glm_ops.h.
 */
#include "smile_glm_ops.h"

#include <ATen/ATen.h>
#include <ATen/cuda/CUDAContext.h>
#include <c10/cuda/CUDAGuard.h>
#include <torch/torch.h>

#include <cuda_bf16.h>
#include <mutex>
#include <string>

/* smile_torch.cpp */
#include <cupti.h>
#include <cuda_fp8.h>
#include <unordered_map>
#include <memory>
#include <mutex>
#include <algorithm>
#include <cstdlib>

extern "C" void smile_torch_set_error(const char *msg);
extern "C" void smile_torch_clear_error(void);

struct ST_Tensor_ {
    at::Tensor t;
};

namespace {

void set_err(const std::string& msg) { smile_torch_set_error(msg.c_str()); }

constexpr int kKD = 128;        // key and value head dimension
constexpr int kCols = 32;       // value columns per block
constexpr int kRows = kKD / 4;  // state rows per thread (one warp per 32 rows)

__device__ __forceinline__ float silu_f(float x) { return x / (1.f + expf(-x)); }
__device__ __forceinline__ float sigmoid_f(float x) { return 1.f / (1.f + expf(-x)); }

/**
 * One block owns a 128 x 32 slice of one head's state (one column per lane, 32 rows per warp in registers); the
 * columns are independent given q, k, so the recurrence needs one cross-warp reduction per token:
 *   a = (k * gk) . S0, b = (q * gk) . S0, delta = (v - a) * beta, y = b + (q . k) delta, S1 = gk * S0 + k delta.
 */
__global__ void __launch_bounds__(128) kda_kernel(
        const float* __restrict__ raw, const float* __restrict__ fg, const float* __restrict__ braw,
        const float* __restrict__ convw, const float* __restrict__ a_log, const float* __restrict__ dt_bias,
        const float* __restrict__ conv_state, float* __restrict__ rec_state, const int* __restrict__ slots,
        float* __restrict__ out, float* __restrict__ rec_save, int T, int H, float bound) {
    const int v0 = blockIdx.x * kCols;
    const int bh = blockIdx.y;
    const int b = bh / H;
    const int h = bh % H;
    const int tid = threadIdx.x;
    const int lane = tid & 31;
    const int warp = tid >> 5;
    const int col = v0 + lane;
    const int W = H * kKD;
    const int C = 3 * W;
    const int slot = slots[b];

    const float* rawb = raw + (int64_t) b * T * C;
    const float* cs = conv_state + (int64_t) slot * 3 * C;
    float* S = rec_state + ((int64_t) slot * H + h) * kKD * kKD;
    const float decay_rate = expf(a_log[h]);
    const float qscale = rsqrtf((float) kKD);

    const int qc = h * kKD + tid;
    const int kc = W + h * kKD + tid;
    const int vc = 2 * W + h * kKD + col;
    const float4 wq = *reinterpret_cast<const float4*>(convw + (int64_t) qc * 4);
    const float4 wk = *reinterpret_cast<const float4*>(convw + (int64_t) kc * 4);
    const float4 wv = *reinterpret_cast<const float4*>(convw + (int64_t) vc * 4);
    const float dtb = dt_bias[h * kKD + tid];

    __shared__ float s_norm[2][4][3];
    __shared__ float s_w[2][4][kKD];     // ka, qa, kn, g by row
    __shared__ float s_red[2][4][kCols][2];

    float st[kRows];
#pragma unroll
    for (int r = 0; r < kRows; ++r) st[r] = S[(int64_t) (warp * kRows + r) * kKD + col];

    // the 4-tap window of q, k and v slides by one raw value per token: keep the three previous values in registers
    // (initially the conv state) and prefetch the next token's inputs while this one is computed
    float qw0 = cs[qc], qw1 = cs[(int64_t) C + qc], qw2 = cs[(int64_t) 2 * C + qc];
    float kw0 = cs[kc], kw1 = cs[(int64_t) C + kc], kw2 = cs[(int64_t) 2 * C + kc];
    float vw0 = cs[vc], vw1 = cs[(int64_t) C + vc], vw2 = cs[(int64_t) 2 * C + vc];
    float nq = rawb[qc], nk = rawb[kc], nv = rawb[vc];
    float nfg = fg[(int64_t) b * T * W + h * kKD + tid];
    float nbeta = braw[(int64_t) b * T * H + h];

    for (int t = 0; t < T; ++t) {
        const int p = t & 1;
        const float xq = nq, xk = nk, xv = nv, fgv = nfg, braw_t = nbeta;
        if (t + 1 < T) {
            nq = rawb[(int64_t) (t + 1) * C + qc];
            nk = rawb[(int64_t) (t + 1) * C + kc];
            nv = rawb[(int64_t) (t + 1) * C + vc];
            nfg = fg[((int64_t) b * T + t + 1) * W + h * kKD + tid];
            nbeta = braw[((int64_t) b * T + t + 1) * H + h];
        }
        float qv = wq.x * qw0 + wq.y * qw1 + wq.z * qw2 + wq.w * xq;
        float kv = wk.x * kw0 + wk.y * kw1 + wk.z * kw2 + wk.w * xk;
        float vv = wv.x * vw0 + wv.y * vw1 + wv.z * vw2 + wv.w * xv;
        qw0 = qw1; qw1 = qw2; qw2 = xq;
        kw0 = kw1; kw1 = kw2; kw2 = xk;
        vw0 = vw1; vw1 = vw2; vw2 = xv;
        qv = silu_f(qv);
        kv = silu_f(kv);
        vv = silu_f(vv);

        float qq = qv * qv, kk = kv * kv, qk = qv * kv;
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
        __syncthreads();
        qq = s_norm[p][0][0] + s_norm[p][1][0] + s_norm[p][2][0] + s_norm[p][3][0];
        kk = s_norm[p][0][1] + s_norm[p][1][1] + s_norm[p][2][1] + s_norm[p][3][1];
        qk = s_norm[p][0][2] + s_norm[p][1][2] + s_norm[p][2][2] + s_norm[p][3][2];
        const float q_inv = 1.f / sqrtf(qq + 1e-6f);
        const float k_inv = 1.f / sqrtf(kk + 1e-6f);
        const float qdotk = qk * q_inv * k_inv * qscale;
        const float kn = kv * k_inv;
        const float qn = qv * q_inv * qscale;
        const float gk = expf(bound * sigmoid_f(decay_rate * (fgv + dtb)));
        s_w[p][0][tid] = kn * gk;
        s_w[p][1][tid] = qn * gk;
        s_w[p][2][tid] = kn;
        s_w[p][3][tid] = gk;
        const float beta = sigmoid_f(braw_t);
        __syncthreads();

        float a = 0.f, bsum = 0.f;
#pragma unroll
        for (int r = 0; r < kRows; ++r) {
            const int row = warp * kRows + r;
            a += st[r] * s_w[p][0][row];
            bsum += st[r] * s_w[p][1][row];
        }
        s_red[p][warp][lane][0] = a;
        s_red[p][warp][lane][1] = bsum;
        __syncthreads();
        a = s_red[p][0][lane][0] + s_red[p][1][lane][0] + s_red[p][2][lane][0] + s_red[p][3][lane][0];
        bsum = s_red[p][0][lane][1] + s_red[p][1][lane][1] + s_red[p][2][lane][1] + s_red[p][3][lane][1];
        const float delta = (vv - a) * beta;
        if (warp == 0) {
            out[(((int64_t) b * T + t) * H + h) * kKD + col] = bsum + qdotk * delta;
        }
#pragma unroll
        for (int r = 0; r < kRows; ++r) {
            const int row = warp * kRows + r;
            st[r] = s_w[p][3][row] * st[r] + s_w[p][2][row] * delta;
        }
        if (rec_save != nullptr) {
            // the state after token t of this request, for rolling a speculative window back to any row
            float* dst = rec_save + (((int64_t) b * T + t) * H + h) * kKD * kKD;
#pragma unroll
            for (int r = 0; r < kRows; ++r) dst[(int64_t) (warp * kRows + r) * kKD + col] = st[r];
        }
    }
#pragma unroll
    for (int r = 0; r < kRows; ++r) S[(int64_t) (warp * kRows + r) * kKD + col] = st[r];
}

/**
 * Prefill, step 1: everything of a token that does not depend on the recurrent state, computed for all tokens in parallel.
 * One block per (token, head), thread d = key/value dimension d: the causal conv over q, k, v (the three rows before the
 * chunk come from the conv state), SiLU, the L2 norms of q and k, the per-channel decay. Writes per row
 * w = {k_n g, q_n g, k_n, g}, v, and {beta, q_n . k_n}.
 */
__global__ void __launch_bounds__(128) kda_prep_kernel(
        const float* __restrict__ raw, const float* __restrict__ fg, const float* __restrict__ braw,
        const float* __restrict__ convw, const float* __restrict__ a_log, const float* __restrict__ dt_bias,
        const float* __restrict__ conv_state, const int* __restrict__ slots,
        float* __restrict__ pw, float* __restrict__ pv, float* __restrict__ ps, int T, int H, float bound) {
    const int t = blockIdx.x;
    const int bh = blockIdx.y;
    const int b = bh / H;
    const int h = bh % H;
    const int tid = threadIdx.x;
    const int W = H * kKD;
    const int C = 3 * W;
    const float* rawb = raw + (int64_t) b * T * C;
    const float* cs = conv_state + (int64_t) slots[b] * 3 * C;
    const int qc = h * kKD + tid;
    const int kc = W + h * kKD + tid;
    const int vc = 2 * W + h * kKD + tid;
    float qx[4], kx[4], vx[4];
#pragma unroll
    for (int j = 0; j < 4; ++j) {
        const int tau = t - 3 + j;
        if (tau >= 0) {
            qx[j] = rawb[(int64_t) tau * C + qc];
            kx[j] = rawb[(int64_t) tau * C + kc];
            vx[j] = rawb[(int64_t) tau * C + vc];
        } else {
            qx[j] = cs[(int64_t) (3 + tau) * C + qc];
            kx[j] = cs[(int64_t) (3 + tau) * C + kc];
            vx[j] = cs[(int64_t) (3 + tau) * C + vc];
        }
    }
    const float4 wq = *reinterpret_cast<const float4*>(convw + (int64_t) qc * 4);
    const float4 wk = *reinterpret_cast<const float4*>(convw + (int64_t) kc * 4);
    const float4 wv = *reinterpret_cast<const float4*>(convw + (int64_t) vc * 4);
    float qv = silu_f(wq.x * qx[0] + wq.y * qx[1] + wq.z * qx[2] + wq.w * qx[3]);
    float kv = silu_f(wk.x * kx[0] + wk.y * kx[1] + wk.z * kx[2] + wk.w * kx[3]);
    float vv = silu_f(wv.x * vx[0] + wv.y * vx[1] + wv.z * vx[2] + wv.w * vx[3]);
    float qq = qv * qv, kk = kv * kv, qk = qv * kv;
#pragma unroll
    for (int o = 16; o > 0; o >>= 1) {
        qq += __shfl_xor_sync(0xffffffffu, qq, o);
        kk += __shfl_xor_sync(0xffffffffu, kk, o);
        qk += __shfl_xor_sync(0xffffffffu, qk, o);
    }
    __shared__ float s_norm[4][3];
    if ((tid & 31) == 0) {
        s_norm[tid >> 5][0] = qq;
        s_norm[tid >> 5][1] = kk;
        s_norm[tid >> 5][2] = qk;
    }
    __syncthreads();
    qq = s_norm[0][0] + s_norm[1][0] + s_norm[2][0] + s_norm[3][0];
    kk = s_norm[0][1] + s_norm[1][1] + s_norm[2][1] + s_norm[3][1];
    qk = s_norm[0][2] + s_norm[1][2] + s_norm[2][2] + s_norm[3][2];
    const float q_inv = 1.f / sqrtf(qq + 1e-6f);
    const float k_inv = 1.f / sqrtf(kk + 1e-6f);
    const float kn = kv * k_inv;
    const float qn = qv * q_inv * rsqrtf((float) kKD);
    const float gk = expf(bound * sigmoid_f(expf(a_log[h]) * (fg[((int64_t) b * T + t) * W + h * kKD + tid] + dt_bias[h * kKD + tid])));
    const int64_t row = ((int64_t) b * T + t) * H + h;
    float* w = pw + row * 4 * kKD;
    w[tid] = kn * gk;
    w[kKD + tid] = qn * gk;
    w[2 * kKD + tid] = kn;
    w[3 * kKD + tid] = gk;
    pv[row * kKD + tid] = vv;
    if (tid == 0) {
        ps[row * 2] = sigmoid_f(braw[row]);
        ps[row * 2 + 1] = qk * q_inv * k_inv * rsqrtf((float) kKD);
    }
}

/**
 * Prefill, step 2: the recurrence over the prepared rows. The row weights of a token sit in the registers of the lane that
 * owns the row; a warp only reads rows of its own 32 (shared memory + __syncwarp), so a token costs one block barrier
 * (the cross-warp sum of the two dot products).
 */
__global__ void __launch_bounds__(128) kda_scan_kernel(
        const float* __restrict__ pw, const float* __restrict__ pv, const float* __restrict__ ps,
        float* __restrict__ rec_state, const int* __restrict__ slots, float* __restrict__ out, int T, int H) {
    const int v0 = blockIdx.x * kCols;
    const int bh = blockIdx.y;
    const int b = bh / H;
    const int h = bh % H;
    const int tid = threadIdx.x;
    const int lane = tid & 31;
    const int warp = tid >> 5;
    const int col = v0 + lane;
    float* S = rec_state + ((int64_t) slots[b] * H + h) * kKD * kKD;

    __shared__ float4 s_w[2][kKD];
    __shared__ float2 s_red[2][4][kCols];

    float st[kRows];
#pragma unroll
    for (int r = 0; r < kRows; ++r) st[r] = S[(int64_t) (warp * kRows + r) * kKD + col];

    // the rows of the next group of kU tokens are loaded while the current group runs (an L2 round trip is longer than a token)
    constexpr int kU = 4;
    const int64_t row0 = (int64_t) b * T * H + h;
    float4 cw[kU], nw[kU];
    float cv[kU], nv[kU];
    float2 cs[kU], ns[kU];
#pragma unroll
    for (int u = 0; u < kU; ++u) {
        const int64_t row = row0 + (int64_t) u * H;
        if (u < T) {
            cw[u] = make_float4(pw[row * 4 * kKD + tid], pw[row * 4 * kKD + kKD + tid],
                                pw[row * 4 * kKD + 2 * kKD + tid], pw[row * 4 * kKD + 3 * kKD + tid]);
            cv[u] = pv[row * kKD + col];
            cs[u] = *reinterpret_cast<const float2*>(ps + row * 2);
        }
    }
    for (int t0 = 0; t0 < T; t0 += kU) {
#pragma unroll
        for (int u = 0; u < kU; ++u) {
            const int64_t row = row0 + (int64_t) (t0 + kU + u) * H;
            if (t0 + kU + u < T) {
                nw[u] = make_float4(pw[row * 4 * kKD + tid], pw[row * 4 * kKD + kKD + tid],
                                    pw[row * 4 * kKD + 2 * kKD + tid], pw[row * 4 * kKD + 3 * kKD + tid]);
                nv[u] = pv[row * kKD + col];
                ns[u] = *reinterpret_cast<const float2*>(ps + row * 2);
            }
        }
#pragma unroll
        for (int u = 0; u < kU; ++u) {
            const int t = t0 + u;
            if (t < T) {
                s_w[u & 1][tid] = cw[u];
                __syncwarp();
                float a0 = 0.f, a1 = 0.f, b0 = 0.f, b1 = 0.f;
#pragma unroll
                for (int r = 0; r < kRows; r += 2) {
                    const float4 x0 = s_w[u & 1][warp * kRows + r];
                    const float4 x1 = s_w[u & 1][warp * kRows + r + 1];
                    a0 += st[r] * x0.x;
                    b0 += st[r] * x0.y;
                    a1 += st[r + 1] * x1.x;
                    b1 += st[r + 1] * x1.y;
                }
                s_red[u & 1][warp][lane] = make_float2(a0 + a1, b0 + b1);
                __syncthreads();
                const float a = s_red[u & 1][0][lane].x + s_red[u & 1][1][lane].x + s_red[u & 1][2][lane].x + s_red[u & 1][3][lane].x;
                const float bsum = s_red[u & 1][0][lane].y + s_red[u & 1][1][lane].y + s_red[u & 1][2][lane].y + s_red[u & 1][3][lane].y;
                const float delta = (cv[u] - a) * cs[u].x;
                if (warp == 0) {
                    out[(((int64_t) b * T + t) * H + h) * kKD + col] = bsum + cs[u].y * delta;
                }
#pragma unroll
                for (int r = 0; r < kRows; ++r) {
                    const float4 x = s_w[u & 1][warp * kRows + r];
                    st[r] = x.w * st[r] + x.z * delta;
                }
            }
        }
#pragma unroll
        for (int u = 0; u < kU; ++u) {
            cw[u] = nw[u];
            cv[u] = nv[u];
            cs[u] = ns[u];
        }
    }
#pragma unroll
    for (int r = 0; r < kRows; ++r) S[(int64_t) (warp * kRows + r) * kKD + col] = st[r];
}


// ---- fp32 GEMM on tensor cores (3xTF32) for the skinny fp32 products of prefill: HC projection (N = 24), router (N = 288)
__device__ __forceinline__ void tf32_split(float x, uint32_t& hi, uint32_t& lo) {
    uint32_t h;
    asm("cvt.rna.tf32.f32 %0, %1;" : "=r"(h) : "f"(x));
    const float r = x - __uint_as_float(h);
    uint32_t l;
    asm("cvt.rna.tf32.f32 %0, %1;" : "=r"(l) : "f"(r));
    hi = h;
    lo = l;
}

__device__ __forceinline__ void mma_tf32(float* c, const uint32_t* a, uint32_t b0, uint32_t b1) {
    asm volatile("mma.sync.aligned.m16n8k8.row.col.f32.tf32.tf32.f32 {%0,%1,%2,%3}, {%4,%5,%6,%7}, {%8,%9}, {%0,%1,%2,%3};"
                 : "+f"(c[0]), "+f"(c[1]), "+f"(c[2]), "+f"(c[3])
                 : "r"(a[0]), "r"(a[1]), "r"(a[2]), "r"(a[3]), "r"(b0), "r"(b1));
}

constexpr int kGemmNT = 6;      // n8 tiles per warp

/**
 * One warp computes a 16-row x (up to 48)-column tile of A W^T over one K range: hi*hi + hi*lo + lo*hi of the TF32 splits
 * of both operands (about fp32 accuracy). Partial sums go to part[split][M][N]; a second kernel adds the splits in order.
 */
__global__ void __launch_bounds__(128) gemm_x3_kernel(
        const float* __restrict__ A, const float* __restrict__ W, float* __restrict__ part,
        int M, int N, int K, int nsplit, int kchunk, int numNg) {
    const int lane = threadIdx.x & 31;
    const long task = (long) blockIdx.x * 4 + (threadIdx.x >> 5);
    const int s = (int) (task % nsplit);
    const long r = task / nsplit;
    const int ng = (int) (r % numNg);
    const int mt = (int) (r / numNg);
    if (mt * 16 >= M) return;
    const int g = lane >> 2;
    const int t = lane & 3;
    const int row0 = mt * 16 + g;
    const int row1 = row0 + 8;
    const float* a0p = A + (long) min(row0, M - 1) * K;
    const float* a1p = A + (long) min(row1, M - 1) * K;
    const int nbase = ng * kGemmNT * 8;
    int nt = min(kGemmNT, (N - nbase) / 8);
    const float* wp[kGemmNT];
#pragma unroll
    for (int j = 0; j < kGemmNT; ++j) wp[j] = W + (long) min(nbase + j * 8 + g, N - 1) * K;
    float c[kGemmNT][4];
#pragma unroll
    for (int j = 0; j < kGemmNT; ++j) { c[j][0] = c[j][1] = c[j][2] = c[j][3] = 0.f; }
    const int kend = min(K, (s + 1) * kchunk);
    for (int k = s * kchunk; k < kend; k += 8) {
        uint32_t ah[4], al[4];
        tf32_split(row0 < M ? a0p[k + t] : 0.f, ah[0], al[0]);
        tf32_split(row1 < M ? a1p[k + t] : 0.f, ah[1], al[1]);
        tf32_split(row0 < M ? a0p[k + t + 4] : 0.f, ah[2], al[2]);
        tf32_split(row1 < M ? a1p[k + t + 4] : 0.f, ah[3], al[3]);
#pragma unroll
        for (int j = 0; j < kGemmNT; ++j) {
            if (j < nt) {
                uint32_t bh0, bl0, bh1, bl1;
                tf32_split(wp[j][k + t], bh0, bl0);
                tf32_split(wp[j][k + t + 4], bh1, bl1);
                mma_tf32(c[j], al, bh0, bh1);
                mma_tf32(c[j], ah, bl0, bl1);
                mma_tf32(c[j], ah, bh0, bh1);
            }
        }
    }
    float* dst = part + (long) s * M * N;
#pragma unroll
    for (int j = 0; j < kGemmNT; ++j) {
        if (j < nt) {
            const int n = nbase + j * 8 + t * 2;
            if (row0 < M) { dst[(long) row0 * N + n] = c[j][0]; dst[(long) row0 * N + n + 1] = c[j][1]; }
            if (row1 < M) { dst[(long) row1 * N + n] = c[j][2]; dst[(long) row1 * N + n + 1] = c[j][3]; }
        }
    }
}

__global__ void gemm_x3_reduce_kernel(const float* __restrict__ part, float* __restrict__ out, long mn, int nsplit) {
    for (long i = (long) blockIdx.x * blockDim.x + threadIdx.x; i < mn; i += (long) gridDim.x * blockDim.x) {
        float v = 0.f;
        for (int s = 0; s < nsplit; ++s) v += part[(long) s * mn + i];
        out[i] = v;
    }
}


// ---- bf16x3: the same skinny fp32 products with the weights pre-split into two bf16 tensors (hi, lo) and the activations
// split in the kernel: hi*hi + hi*lo + lo*hi on m16n8k16 bf16 tensor cores (~1e-5 relative error; one mma instruction covers
// twice the K of the TF32 version and the weights need no per-step conversion)
__device__ __forceinline__ uint32_t pack_bf16(float lo, float hi_) {
    uint32_t d;
    asm("cvt.rn.bf16x2.f32 %0, %1, %2;" : "=r"(d) : "f"(hi_), "f"(lo));
    return d;
}

__device__ __forceinline__ void split_bf16x2(float x, float y, uint32_t& hi, uint32_t& lo) {
    hi = pack_bf16(x, y);
    const float xh = __uint_as_float(hi << 16);
    const float yh = __uint_as_float(hi & 0xffff0000u);
    lo = pack_bf16(x - xh, y - yh);
}

__device__ __forceinline__ void mma_x3_bf16(float* c, const uint32_t* a, uint32_t b0, uint32_t b1) {
    asm volatile("mma.sync.aligned.m16n8k16.row.col.f32.bf16.bf16.f32 {%0,%1,%2,%3}, {%4,%5,%6,%7}, {%8,%9}, {%0,%1,%2,%3};"
                 : "+f"(c[0]), "+f"(c[1]), "+f"(c[2]), "+f"(c[3])
                 : "r"(a[0]), "r"(a[1]), "r"(a[2]), "r"(a[3]), "r"(b0), "r"(b1));
}

__global__ void __launch_bounds__(128) gemm_bf16x3_kernel(
        const float* __restrict__ A, const uint4* __restrict__ Wf,
        float* __restrict__ part, int M, int N, int K, int nsplit, int kchunk, int numNg) {
    const int lane = threadIdx.x & 31;
    const long task = (long) blockIdx.x * 4 + (threadIdx.x >> 5);
    const int s = (int) (task % nsplit);
    const long r = task / nsplit;
    const int ng = (int) (r % numNg);
    const int mt = (int) (r / numNg);
    if (mt * 16 >= M) return;
    const int g = lane >> 2;
    const int t = lane & 3;
    const int row0 = mt * 16 + g;
    const int row1 = row0 + 8;
    const float* a0p = A + (long) min(row0, M - 1) * K + 2 * t;
    const float* a1p = A + (long) min(row1, M - 1) * K + 2 * t;
    const int nbase = ng * kGemmNT * 8;
    const int nt = min(kGemmNT, (N - nbase) / 8);
    // weight fragments: [N/8 tiles][K/16 steps][32 lanes] x {hi b0, hi b1, lo b0, lo b1}: one coalesced 16-byte load per lane
    const long ks = K / 16;
    const uint4* wfp[kGemmNT];
#pragma unroll
    for (int j = 0; j < kGemmNT; ++j) {
        wfp[j] = Wf + ((long) min(nbase / 8 + j, N / 8 - 1) * ks) * 32 + lane;
    }
    float c[kGemmNT][4];
#pragma unroll
    for (int j = 0; j < kGemmNT; ++j) { c[j][0] = c[j][1] = c[j][2] = c[j][3] = 0.f; }
    const int kend = min(K, (s + 1) * kchunk);
    const int kbeg = s * kchunk;
    const bool r0ok = row0 < M, r1ok = row1 < M;
    float2 x0, x1, x2, x3, y0, y1, y2, y3;
    uint32_t bh0[kGemmNT], bh1[kGemmNT], bl0[kGemmNT], bl1[kGemmNT], nh0[kGemmNT], nh1[kGemmNT], nl0[kGemmNT], nl1[kGemmNT];
    auto load = [&](int k, float2& l0, float2& l1, float2& l2, float2& l3, uint32_t* h0, uint32_t* h1, uint32_t* l0w, uint32_t* l1w) {
        l0 = r0ok ? *reinterpret_cast<const float2*>(a0p + k) : make_float2(0.f, 0.f);
        l1 = r1ok ? *reinterpret_cast<const float2*>(a1p + k) : make_float2(0.f, 0.f);
        l2 = r0ok ? *reinterpret_cast<const float2*>(a0p + k + 8) : make_float2(0.f, 0.f);
        l3 = r1ok ? *reinterpret_cast<const float2*>(a1p + k + 8) : make_float2(0.f, 0.f);
#pragma unroll
        for (int j = 0; j < kGemmNT; ++j) {
            if (j < nt) {
                const uint4 w = wfp[j][(long) (k / 16) * 32];
                h0[j] = w.x;
                h1[j] = w.y;
                l0w[j] = w.z;
                l1w[j] = w.w;
            }
        }
    };
    if (kbeg < kend) {
        load(kbeg, x0, x1, x2, x3, bh0, bh1, bl0, bl1);
    }
    for (int k = kbeg; k < kend; k += 16) {
        if (k + 16 < kend) {
            load(k + 16, y0, y1, y2, y3, nh0, nh1, nl0, nl1);       // the next step's loads are in flight during this step's mma
        }
        uint32_t ah[4], al[4];
        split_bf16x2(x0.x, x0.y, ah[0], al[0]);
        split_bf16x2(x1.x, x1.y, ah[1], al[1]);
        split_bf16x2(x2.x, x2.y, ah[2], al[2]);
        split_bf16x2(x3.x, x3.y, ah[3], al[3]);
#pragma unroll
        for (int j = 0; j < kGemmNT; ++j) {
            if (j < nt) {
                mma_x3_bf16(c[j], al, bh0[j], bh1[j]);
                mma_x3_bf16(c[j], ah, bl0[j], bl1[j]);
                mma_x3_bf16(c[j], ah, bh0[j], bh1[j]);
            }
        }
        x0 = y0; x1 = y1; x2 = y2; x3 = y3;
#pragma unroll
        for (int j = 0; j < kGemmNT; ++j) {
            bh0[j] = nh0[j]; bh1[j] = nh1[j]; bl0[j] = nl0[j]; bl1[j] = nl1[j];
        }
    }
    float* dst = part + (long) s * M * N;
#pragma unroll
    for (int j = 0; j < kGemmNT; ++j) {
        if (j < nt) {
            const int n = nbase + j * 8 + t * 2;
            if (row0 < M) { dst[(long) row0 * N + n] = c[j][0]; dst[(long) row0 * N + n + 1] = c[j][1]; }
            if (row1 < M) { dst[(long) row1 * N + n] = c[j][2]; dst[(long) row1 * N + n + 1] = c[j][3]; }
        }
    }
}


// ---- MoE combine: out[n, dim] = sum_k down[n * topk + k, :] (+ extra[n, dim]) in fp32, straight from the bf16 expert outputs
__global__ void moe_sum_topk_kernel(const __nv_bfloat16* __restrict__ down, const float* __restrict__ extra,
                                    float* __restrict__ out, int topk, int dim) {
    const long row = blockIdx.x;
    for (int d = (blockIdx.y * blockDim.x + threadIdx.x) * 4; d < dim; d += gridDim.y * blockDim.x * 4) {
        float4 acc = extra != nullptr ? *reinterpret_cast<const float4*>(extra + row * dim + d) : make_float4(0.f, 0.f, 0.f, 0.f);
#pragma unroll 8
        for (int k = 0; k < topk; ++k) {
            const uint2 v = *reinterpret_cast<const uint2*>(down + (row * topk + k) * dim + d);
            const __nv_bfloat162 lo = *reinterpret_cast<const __nv_bfloat162*>(&v.x);
            const __nv_bfloat162 hi = *reinterpret_cast<const __nv_bfloat162*>(&v.y);
            acc.x += __low2float(lo);
            acc.y += __high2float(lo);
            acc.z += __low2float(hi);
            acc.w += __high2float(hi);
        }
        *reinterpret_cast<float4*>(out + row * dim + d) = acc;
    }
}

/** Rolls the last three raw rows into the conv state (a thread handles all three steps of its channel, in order). */
__global__ void kda_conv_state_kernel(const float* __restrict__ raw, float* __restrict__ conv_state,
                                      const int* __restrict__ slots, int T, int C) {
    const int c = blockIdx.x * blockDim.x + threadIdx.x;
    if (c >= C) return;
    const int b = blockIdx.y;
    const float* rawb = raw + (int64_t) b * T * C;
    float* cs = conv_state + (int64_t) slots[b] * 3 * C;
    for (int j = 0; j < 3; ++j) {
        const int tau = T - 3 + j;
        cs[(int64_t) j * C + c] = tau >= 0 ? rawb[(int64_t) tau * C + c] : cs[(int64_t) (3 + tau) * C + c];
    }
}

/**
 * Keeps the first accept[b] rows of a speculative window: the recurrent state becomes the state after row accept[b] - 1
 * and the conv state the last three raw rows of (old conv state, rows 0..accept[b]-1). The verify pass left the conv
 * state untouched and the recurrent state after the whole window.
 */
__global__ void kda_commit_kernel(const float* __restrict__ raw, float* __restrict__ conv_state,
                                  float* __restrict__ rec_state, const float* __restrict__ rec_save,
                                  const int* __restrict__ slots, const int* __restrict__ accept, int T, int C, int H) {
    const int b = blockIdx.y;
    const int a = accept[b];
    const int slot = slots[b];
    const int64_t sz = (int64_t) H * kKD * kKD;
    float* dst = rec_state + (int64_t) slot * sz;
    const float* src = rec_save + ((int64_t) b * T + (a - 1)) * sz;
    for (int64_t i = (int64_t) blockIdx.x * blockDim.x + threadIdx.x; i < sz; i += (int64_t) gridDim.x * blockDim.x) {
        dst[i] = src[i];
    }
    // conv state: one thread per channel (blocks of the first grid row only; the others skip it)
    if (blockIdx.x * blockDim.x + threadIdx.x < C) {
        const int c = blockIdx.x * blockDim.x + threadIdx.x;
        const float* rawb = raw + (int64_t) b * T * C;
        float* cs = conv_state + (int64_t) slot * 3 * C;
        for (int j = 0; j < 3; ++j) {
            const int tau = a - 3 + j;
            cs[(int64_t) j * C + c] = tau >= 0 ? rawb[(int64_t) tau * C + c] : cs[(int64_t) (3 + tau) * C + c];
        }
    }
}

/** out[r, c] = silu(min(gu[r, c], limit)) * clamp(gu[r, inter + c], -limit, limit) in float32 math, written as bfloat16. */
template <typename T>
__global__ void glm_swiglu_kernel(const T* __restrict__ gu, __nv_bfloat16* __restrict__ out, int64_t total, int inter,
                                  float limit) {
    const int64_t i = (int64_t) blockIdx.x * blockDim.x + threadIdx.x;
    if (i >= total) return;
    const int64_t r = i / inter;
    const int c = (int) (i % inter);
    float g = (float) gu[r * 2 * inter + c];
    float u = (float) gu[r * 2 * inter + inter + c];
    if (limit > 0.f) {
        g = fminf(g, limit);
        u = fminf(fmaxf(u, -limit), limit);
    }
    out[i] = __float2bfloat16(g / (1.f + expf(-g)) * u);
}

constexpr int kLat = 512;           // latent width
constexpr int kPoolDim = 128;       // indexer key width

// ---- fp8 latent cache: a row is 512 e4m3 values followed by four float32 scales (one per 128 dimensions)
constexpr int kLatRow8 = 528;

__device__ __forceinline__ float fp8_to_f(unsigned char b) {
    const __half_raw h = __nv_cvt_fp8_to_halfraw(b, __NV_E4M3);
    return __half2float(__half(h));
}

/** Quantizes one 512-value row with a block of 128 threads (thread d owns dimension d of every tile); red: [4][4] floats. */
__device__ __forceinline__ void quantize_latent_row(const float* __restrict__ src, unsigned char* __restrict__ dst, float (*red)[4]) {
    const int d = threadIdx.x;
    const int lane = d & 31;
    const int warp = d >> 5;
    float v[4];
#pragma unroll
    for (int t = 0; t < 4; ++t) {
        v[t] = src[t * 128 + d];
        float a = fabsf(v[t]);
#pragma unroll
        for (int o = 16; o > 0; o >>= 1) a = fmaxf(a, __shfl_xor_sync(0xffffffffu, a, o));
        if (lane == 0) red[t][warp] = a;
    }
    __syncthreads();
#pragma unroll
    for (int t = 0; t < 4; ++t) {
        const float m = fmaxf(fmaxf(red[t][0], red[t][1]), fmaxf(red[t][2], red[t][3]));
        const float scale = m > 0.f ? m / 448.f : 1.f;
        dst[t * 128 + d] = __nv_cvt_float_to_fp8(v[t] / scale, __NV_SATFINITE, __NV_E4M3);
        if (d == 0) reinterpret_cast<float*>(dst + 512)[t] = scale;
    }
    __syncthreads();
}
constexpr int kProjQa = 1536;
constexpr int kProjKv = 512;
constexpr int kProjIdx = 128;
constexpr int kProjGate = 128;
constexpr int kProjW = 32;
constexpr int kProjCols = kProjQa + kProjKv + kProjIdx + kProjGate + kProjW;

__device__ __forceinline__ float block_sum(float v, float* red) {
#pragma unroll
    for (int o = 16; o > 0; o >>= 1) v += __shfl_xor_sync(0xffffffffu, v, o);
    __syncthreads();
    if ((threadIdx.x & 31) == 0) red[threadIdx.x >> 5] = v;
    __syncthreads();
    float t = 0.f;
    for (int w = 0; w < (int) (blockDim.x >> 5); ++w) t += red[w];
    return t;
}

/**
 * The elementwise tail of GLM's MLA projections of one row y = x . [W_qa; W_kva; W_idxk; W_gate; W_idxw]^T:
 * qresid = rms_norm(y[0:1536]) * w (bfloat16: it only feeds bf16 GEMMs), latent = rms_norm(y[1536:2048]) * w,
 * key = layer_norm(y[2048:2176]) * w + b, gate = y[2176:2304], weights = y[2304:2336] * w_scale.
 */
__global__ void __launch_bounds__(256) glm_mla_proj_epilogue_kernel(
        const float* __restrict__ y, const float* __restrict__ qa_w, const float* __restrict__ kv_w,
        const float* __restrict__ ln_w, const float* __restrict__ ln_b, float eps, float ln_eps, float w_scale,
        __nv_bfloat16* __restrict__ qresid, float* __restrict__ latent, float* __restrict__ key,
        float* __restrict__ gate, float* __restrict__ weights) {
    __shared__ float red[8];
    const int row = blockIdx.x;
    const float* r = y + (int64_t) row * kProjCols;
    const int t = threadIdx.x;
    // q-lora residual
    float ss = 0.f;
    for (int i = t; i < kProjQa; i += 256) ss += r[i] * r[i];
    float inv = rsqrtf(block_sum(ss, red) / kProjQa + eps);
    for (int i = t; i < kProjQa; i += 256) qresid[(int64_t) row * kProjQa + i] = __float2bfloat16(r[i] * inv * qa_w[i]);
    // kv latent
    const float* kv = r + kProjQa;
    ss = 0.f;
    for (int i = t; i < kProjKv; i += 256) ss += kv[i] * kv[i];
    inv = rsqrtf(block_sum(ss, red) / kProjKv + eps);
    for (int i = t; i < kProjKv; i += 256) latent[(int64_t) row * kProjKv + i] = kv[i] * inv * kv_w[i];
    // indexer key: layer norm
    const float* k = kv + kProjKv;
    float v = t < kProjIdx ? k[t] : 0.f;
    const float mean = block_sum(v, red) / kProjIdx;
    const float d = t < kProjIdx ? k[t] - mean : 0.f;
    const float var = block_sum(d * d, red) / kProjIdx;
    if (t < kProjIdx) key[(int64_t) row * kProjIdx + t] = d * rsqrtf(var + ln_eps) * ln_w[t] + ln_b[t];
    // gate and head weights
    const float* g = k + kProjIdx;
    if (t < kProjGate) gate[(int64_t) row * kProjGate + t] = g[t];
    const float* w = g + kProjGate;
    if (t < kProjW) weights[(int64_t) row * kProjW + t] = w[t] * w_scale;
}

constexpr int kNope = 256;          // per-head query width (no rotary part)
constexpr int kValueDim = 256;

__device__ __forceinline__ float bf16_round(float x) { return __bfloat162float(__float2bfloat16(x)); }

/**
 * Absorbs W_UK into the queries of the decode step: qabs[b, h, r] = sum_n bf16(q[b, h, n]) * w_uk[h, n, r] (float32
 * accumulation, bfloat16 result), and converts the indexer queries to bfloat16. Grid (B, heads + 1, 4): block (b, h, z) for
 * h < heads computes the 128 latent columns [128 z, 128 z + 128) with 8 groups of 128 threads, each reducing 32 of the 256
 * query values (all loads in flight), the groups meet in shared memory; block (b, heads, 0) converts the 4096 indexer
 * query values of row b. qi: float32 [B, ld] = q_b | indexer q.
 */
__global__ void __launch_bounds__(1024) glm_mla_absorb_kernel(
        const float* __restrict__ qi, int ld, const __nv_bfloat16* __restrict__ wuk, __nv_bfloat16* __restrict__ qabs,
        __nv_bfloat16* __restrict__ idxq, int heads) {
    const int b = blockIdx.x;
    const int h = blockIdx.y;
    const int tid = threadIdx.x;
    const float* row = qi + (int64_t) b * ld;
    if (h == heads) {
        if (blockIdx.z == 0) {
            for (int i = tid; i < 4096; i += 1024) idxq[(int64_t) b * 4096 + i] = __float2bfloat16(row[heads * kNope + i]);
        }
        return;
    }
    __shared__ float qs[kNope];
    __shared__ float part[8][128];
    if (tid < kNope) qs[tid] = bf16_round(row[h * kNope + tid]);
    __syncthreads();
    const int g = tid >> 7;
    const int rr = tid & 127;
    const int r = blockIdx.z * 128 + rr;
    const __nv_bfloat16* w = wuk + ((int64_t) h * kNope + g * 32) * kLat + r;
    float acc = 0.f;
#pragma unroll
    for (int n = 0; n < 32; ++n) acc += qs[g * 32 + n] * __bfloat162float(w[(int64_t) n * kLat]);
    part[g][rr] = acc;
    __syncthreads();
    if (g == 0) {
        float t = 0.f;
#pragma unroll
        for (int k = 0; k < 8; ++k) t += part[k][rr];
        qabs[((int64_t) b * heads + h) * kLat + r] = __float2bfloat16(t);
    }
}

/**
 * out[b, h * 256 + v] = bf16(sum_r bf16(ctx[b, h, r]) * w_uv_t[h, r, v]): the value expansion and head merge of the decode
 * step. Grid (B, heads, 2); 8 groups of 128 threads each reduce 64 of the 512 latent values for the 128 outputs of a tile.
 */
__global__ void __launch_bounds__(1024) glm_mla_wuv_kernel(
        const float* __restrict__ ctx, const __nv_bfloat16* __restrict__ wuvt, __nv_bfloat16* __restrict__ out, int heads) {
    const int b = blockIdx.x;
    const int h = blockIdx.y;
    const int tid = threadIdx.x;
    __shared__ float cs[kLat];
    __shared__ float part[8][128];
    if (tid < kLat) cs[tid] = bf16_round(ctx[((int64_t) b * heads + h) * kLat + tid]);
    __syncthreads();
    const int g = tid >> 7;
    const int vv = tid & 127;
    const int v = blockIdx.z * 128 + vv;
    const __nv_bfloat16* w = wuvt + ((int64_t) h * kLat + g * 64) * kValueDim + v;
    float acc = 0.f;
#pragma unroll
    for (int r = 0; r < 64; ++r) acc += cs[g * 64 + r] * __bfloat162float(w[(int64_t) r * kValueDim]);
    part[g][vv] = acc;
    __syncthreads();
    if (g == 0) {
        float t = 0.f;
#pragma unroll
        for (int k = 0; k < 8; ++k) t += part[k][vv];
        out[(int64_t) b * heads * kValueDim + h * kValueDim + v] = __float2bfloat16(t);
    }
}

// ---------------------------------------------------------------- indexer scores

constexpr int kIdxHeads = 32;
constexpr int kIdxDim = 128;
constexpr int kIdxWarps = 8;        // queries per block (one per warp)
constexpr int kIdxSub = 64;         // pools per shared-memory sub-tile
constexpr int kIdxChunk = 512;      // pools per block
constexpr int kIdxPad = kIdxDim + 8;
constexpr float kIdxLowest = -3.4028234663852886e38f;

__device__ __forceinline__ void mma_bf16(float* c, const uint32_t* a, uint32_t b0, uint32_t b1) {
    asm volatile("mma.sync.aligned.m16n8k16.row.col.f32.bf16.bf16.f32 {%0,%1,%2,%3}, {%4,%5,%6,%7}, {%8,%9}, {%0,%1,%2,%3};"
                 : "+f"(c[0]), "+f"(c[1]), "+f"(c[2]), "+f"(c[3])
                 : "r"(a[0]), "r"(a[1]), "r"(a[2]), "r"(a[3]), "r"(b0), "r"(b1));
}

/**
 * Block = 8 warps, one query per warp. The warp keeps the query's 32 heads x 128 as mma A fragments (2 head tiles
 * x 8 k-steps) in registers and streams the pooled keys of the block's chunk through shared memory (64 pools at a
 * time, shared by the 8 warps); per 8 pools the epilogue applies relu and the head weights to the accumulators and
 * reduces over the heads with warp shuffles.
 */
__global__ void __launch_bounds__(256) indexer_score_kernel(
        const __nv_bfloat16* __restrict__ q, const float* __restrict__ w, const __nv_bfloat16* __restrict__ pk,
        long long pool0, long long count, long long p0, float scale, int n, float* __restrict__ out) {
    __shared__ __align__(16) __nv_bfloat16 sm[kIdxSub][kIdxPad];
    const int tid = threadIdx.x;
    const int lane = tid & 31;
    const int warp = tid >> 5;
    const int s = blockIdx.x * kIdxWarps + warp;
    const bool active = s < n;
    const int row = lane >> 2;          // groupID
    const int tig = lane & 3;           // threadID_in_group
    const long long chunk0 = (long long) blockIdx.y * kIdxChunk;

    uint32_t a[2][8][4];
    float wt[2][2];
    if (active) {
        const __nv_bfloat16* qs = q + (long long) s * kIdxHeads * kIdxDim;
#pragma unroll
        for (int t = 0; t < 2; ++t) {
#pragma unroll
            for (int ks = 0; ks < 8; ++ks) {
                const __nv_bfloat16* r0 = qs + (long long) (t * 16 + row) * kIdxDim + ks * 16 + 2 * tig;
                const __nv_bfloat16* r1 = r0 + 8 * kIdxDim;
                a[t][ks][0] = *reinterpret_cast<const uint32_t*>(r0);
                a[t][ks][1] = *reinterpret_cast<const uint32_t*>(r1);
                a[t][ks][2] = *reinterpret_cast<const uint32_t*>(r0 + 8);
                a[t][ks][3] = *reinterpret_cast<const uint32_t*>(r1 + 8);
            }
            wt[t][0] = w[(long long) s * kIdxHeads + t * 16 + row];
            wt[t][1] = w[(long long) s * kIdxHeads + t * 16 + row + 8];
        }
    }
    const long long qpos = p0 + s;

    for (int sub = 0; sub < kIdxChunk / kIdxSub; ++sub) {
        const long long base = chunk0 + (long long) sub * kIdxSub;      // first pool of the sub-tile within the range
        if (base >= count) break;                                        // uniform over the block
        __syncthreads();
        for (int i = tid; i < kIdxSub * (kIdxDim / 8); i += 256) {
            const int r = i / (kIdxDim / 8);
            const int c = (i % (kIdxDim / 8)) * 8;
            uint4 v = make_uint4(0, 0, 0, 0);
            if (base + r < count) {
                v = *reinterpret_cast<const uint4*>(pk + (pool0 + base + r) * kIdxDim + c);
            }
            *reinterpret_cast<uint4*>(&sm[r][c]) = v;
        }
        __syncthreads();
        if (!active) continue;
#pragma unroll
        for (int g = 0; g < kIdxSub / 8; ++g) {
            float acc[2][4] = {{0.f, 0.f, 0.f, 0.f}, {0.f, 0.f, 0.f, 0.f}};
#pragma unroll
            for (int ks = 0; ks < 8; ++ks) {
                const __nv_bfloat16* bp = &sm[g * 8 + row][ks * 16 + 2 * tig];
                const uint32_t b0 = *reinterpret_cast<const uint32_t*>(bp);
                const uint32_t b1 = *reinterpret_cast<const uint32_t*>(bp + 8);
                mma_bf16(acc[0], a[0][ks], b0, b1);
                mma_bf16(acc[1], a[1][ks], b0, b1);
            }
            float v0 = 0.f, v1 = 0.f;
#pragma unroll
            for (int t = 0; t < 2; ++t) {
                v0 += wt[t][0] * fmaxf(acc[t][0] * scale, 0.f) + wt[t][1] * fmaxf(acc[t][2] * scale, 0.f);
                v1 += wt[t][0] * fmaxf(acc[t][1] * scale, 0.f) + wt[t][1] * fmaxf(acc[t][3] * scale, 0.f);
            }
#pragma unroll
            for (int o = 4; o < 32; o <<= 1) {
                v0 += __shfl_xor_sync(0xffffffffu, v0, o);
                v1 += __shfl_xor_sync(0xffffffffu, v1, o);
            }
            if (row == 0) {
#pragma unroll
                for (int cc = 0; cc < 2; ++cc) {
                    const long long j = base + g * 8 + 2 * tig + cc;
                    if (j < count) {
                        const bool visible = 4 * (pool0 + j) + 3 <= qpos;
                        out[(long long) s * count + j] = visible ? (cc == 0 ? v0 : v1) : kIdxLowest;
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- MLA decode step (graph-safe)


__global__ void mla_append_kernel(const float* __restrict__ lat_new, const float* __restrict__ key_new,
                                  const float* __restrict__ gate_new, const float* __restrict__ ape,
                                  const long long* __restrict__ lat_ptrs, const long long* __restrict__ pool_ptrs,
                                  const long long* __restrict__ pend_ptrs, const int* __restrict__ lens, int fp8) {
    const int b = blockIdx.x;
    const int d = threadIdx.x;               // 128 threads: one indexer channel each
    const int len = lens[b];
    if (fp8) {
        __shared__ float red[4][4];
        quantize_latent_row(lat_new + (int64_t) b * kLat, reinterpret_cast<unsigned char*>(lat_ptrs[b]) + (int64_t) len * kLatRow8, red);
    } else {
        __nv_bfloat16* lat = reinterpret_cast<__nv_bfloat16*>(lat_ptrs[b]);
        for (int i = d; i < kLat; i += kPoolDim) {
            lat[(int64_t) len * kLat + i] = __float2bfloat16(lat_new[(int64_t) b * kLat + i]);
        }
    }
    float* pend = reinterpret_cast<float*>(pend_ptrs[b]);
    const int slot = len & 3;
    pend[slot * 256 + d] = key_new[(int64_t) b * kPoolDim + d];
    pend[slot * 256 + 128 + d] = gate_new[(int64_t) b * kPoolDim + d];
    if (((len + 1) & 3) == 0) {
        float lg[4], mx = -INFINITY;
#pragma unroll
        for (int j = 0; j < 4; ++j) {
            lg[j] = pend[j * 256 + 128 + d] + ape[j * kPoolDim + d];
            mx = fmaxf(mx, lg[j]);
        }
        float sum = 0.f, acc = 0.f;
#pragma unroll
        for (int j = 0; j < 4; ++j) {
            const float e = expf(lg[j] - mx);
            sum += e;
            acc += e * pend[j * 256 + d];
        }
        __nv_bfloat16* pool = reinterpret_cast<__nv_bfloat16*>(pool_ptrs[b]);
        pool[(int64_t) (len >> 2) * kPoolDim + d] = __float2bfloat16(acc / sum);
    }
}


/**
 * Appends a window of R consecutive tokens per request in order (block = request, thread = indexer channel): latent rows,
 * and the pools the window completes. The ring is read but not written: a window may be only partly accepted, and a
 * rejected row would overwrite a pending slot of a later group; {@code mla_window_commit_kernel} writes the ring.
 */
__global__ void mla_window_append_kernel(const float* __restrict__ lat_new, const float* __restrict__ key_new,
                                         const float* __restrict__ gate_new, const float* __restrict__ ape,
                                         const long long* __restrict__ lat_ptrs, const long long* __restrict__ pool_ptrs,
                                         const long long* __restrict__ pend_ptrs, const int* __restrict__ lens, int R, int fp8) {
    const int b = blockIdx.x;
    const int d = threadIdx.x;
    const int base = lens[b];
    __shared__ float red[4][4];
    __nv_bfloat16* lat = reinterpret_cast<__nv_bfloat16*>(lat_ptrs[b]);
    __nv_bfloat16* pool = reinterpret_cast<__nv_bfloat16*>(pool_ptrs[b]);
    const float* pend = reinterpret_cast<const float*>(pend_ptrs[b]);
    float k[4], g[4];
#pragma unroll
    for (int j = 0; j < 4; ++j) {
        k[j] = pend[j * 256 + d];
        g[j] = pend[j * 256 + 128 + d];
    }
    for (int j = 0; j < R; ++j) {
        const int len = base + j;
        const int64_t row = (int64_t) b * R + j;
        if (fp8) {
            quantize_latent_row(lat_new + row * kLat, reinterpret_cast<unsigned char*>(lat_ptrs[b]) + (int64_t) len * kLatRow8, red);
        } else {
            for (int i = d; i < kLat; i += kPoolDim) {
                lat[(int64_t) len * kLat + i] = __float2bfloat16(lat_new[row * kLat + i]);
            }
        }
        const int slot = len & 3;
        k[slot] = key_new[row * kPoolDim + d];
        g[slot] = gate_new[row * kPoolDim + d];
        if (((len + 1) & 3) == 0) {
            float lg[4], mx = -INFINITY;
#pragma unroll
            for (int q = 0; q < 4; ++q) {
                lg[q] = g[q] + ape[q * kPoolDim + d];
                mx = fmaxf(mx, lg[q]);
            }
            float sum = 0.f, acc = 0.f;
#pragma unroll
            for (int q = 0; q < 4; ++q) {
                const float e = expf(lg[q] - mx);
                sum += e;
                acc += e * k[q];
            }
            pool[(int64_t) (len >> 2) * kPoolDim + d] = __float2bfloat16(acc / sum);
        }
    }
}

/** Writes the ring slots of the accepted rows of a window (the part of the append that was deferred). */
__global__ void mla_window_commit_kernel(const float* __restrict__ key_new, const float* __restrict__ gate_new,
                                         const long long* __restrict__ pend_ptrs, const int* __restrict__ lens,
                                         const int* __restrict__ accept, int R) {
    const int b = blockIdx.x;
    const int d = threadIdx.x;
    const int base = lens[b];
    float* pend = reinterpret_cast<float*>(pend_ptrs[b]);
    for (int j = 0; j < accept[b]; ++j) {
        const int slot = (base + j) & 3;
        pend[slot * 256 + d] = key_new[((int64_t) b * R + j) * kPoolDim + d];
        pend[slot * 256 + 128 + d] = gate_new[((int64_t) b * R + j) * kPoolDim + d];
    }
}


/** dst[r] = quantized row of src[r] (float32 [rows, 512]) in the fp8 cache layout; one block of 128 threads per row. */
__global__ void __launch_bounds__(128) latent_quantize_kernel(const float* __restrict__ src, unsigned char* __restrict__ dst) {
    __shared__ float red[4][4];
    quantize_latent_row(src + (int64_t) blockIdx.x * kLat, dst + (int64_t) blockIdx.x * kLatRow8, red);
}

/** out[i] = dequantized row ids[i] of the fp8 cache (bfloat16 [n, 512]); one block of 128 threads per row. */
__global__ void __launch_bounds__(128) latent_gather_kernel(const unsigned char* __restrict__ cache, const long long* __restrict__ ids,
                                                           __nv_bfloat16* __restrict__ out) {
    const unsigned char* row = cache + ids[blockIdx.x] * kLatRow8;
    const float* sc = reinterpret_cast<const float*>(row + 512);
    __nv_bfloat16* o = out + (int64_t) blockIdx.x * kLat;
#pragma unroll
    for (int t = 0; t < 4; ++t) o[t * 128 + threadIdx.x] = __float2bfloat16(fp8_to_f(row[t * 128 + threadIdx.x]) * sc[t]);
}

/** One query per request; the 8 warps of a block split its 512 pools (8 groups of 8 per warp), B fragments straight from global. */
__global__ void __launch_bounds__(256) mla_scores_kernel(
        const __nv_bfloat16* __restrict__ q, const float* __restrict__ w, const long long* __restrict__ pool_ptrs,
        const int* __restrict__ lens, long long max_pools, float scale, float* __restrict__ out) {
    const int b = blockIdx.x;
    const int lane = threadIdx.x & 31;
    const int warp = threadIdx.x >> 5;
    const int row = lane >> 2;
    const int tig = lane & 3;
    const long long pools = (lens[b] + 1) >> 2;
    const long long base = (long long) blockIdx.y * 512 + warp * 64;
    if (base >= pools) return;
    const __nv_bfloat16* pk = reinterpret_cast<const __nv_bfloat16*>(pool_ptrs[b]);
    const __nv_bfloat16* qs = q + (long long) b * kIdxHeads * kIdxDim;
    uint32_t a[2][8][4];
    float wt[2][2];
#pragma unroll
    for (int t = 0; t < 2; ++t) {
#pragma unroll
        for (int ks = 0; ks < 8; ++ks) {
            const __nv_bfloat16* r0 = qs + (long long) (t * 16 + row) * kIdxDim + ks * 16 + 2 * tig;
            const __nv_bfloat16* r1 = r0 + 8 * kIdxDim;
            a[t][ks][0] = *reinterpret_cast<const uint32_t*>(r0);
            a[t][ks][1] = *reinterpret_cast<const uint32_t*>(r1);
            a[t][ks][2] = *reinterpret_cast<const uint32_t*>(r0 + 8);
            a[t][ks][3] = *reinterpret_cast<const uint32_t*>(r1 + 8);
        }
        wt[t][0] = w[(long long) b * kIdxHeads + t * 16 + row];
        wt[t][1] = w[(long long) b * kIdxHeads + t * 16 + row + 8];
    }
    for (int g = 0; g < 8; ++g) {
        const long long p0 = base + g * 8;
        if (p0 >= pools) break;
        long long pr = p0 + row;
        if (pr >= pools) pr = pools - 1;                 // discarded, but never read out of range
        const __nv_bfloat16* bp = pk + pr * kIdxDim + 2 * tig;
        float acc[2][4] = {{0.f, 0.f, 0.f, 0.f}, {0.f, 0.f, 0.f, 0.f}};
#pragma unroll
        for (int ks = 0; ks < 8; ++ks) {
            const uint32_t b0 = *reinterpret_cast<const uint32_t*>(bp + ks * 16);
            const uint32_t b1 = *reinterpret_cast<const uint32_t*>(bp + ks * 16 + 8);
            mma_bf16(acc[0], a[0][ks], b0, b1);
            mma_bf16(acc[1], a[1][ks], b0, b1);
        }
        float v0 = 0.f, v1 = 0.f;
#pragma unroll
        for (int t = 0; t < 2; ++t) {
            v0 += wt[t][0] * fmaxf(acc[t][0] * scale, 0.f) + wt[t][1] * fmaxf(acc[t][2] * scale, 0.f);
            v1 += wt[t][0] * fmaxf(acc[t][1] * scale, 0.f) + wt[t][1] * fmaxf(acc[t][3] * scale, 0.f);
        }
#pragma unroll
        for (int o = 4; o < 32; o <<= 1) {
            v0 += __shfl_xor_sync(0xffffffffu, v0, o);
            v1 += __shfl_xor_sync(0xffffffffu, v1, o);
        }
        if (row == 0) {
            const long long j0 = p0 + 2 * tig;
            if (j0 < pools) out[(long long) b * max_pools + j0] = v0;
            if (j0 + 1 < pools) out[(long long) b * max_pools + j0 + 1] = v1;
        }
    }
}

__device__ __forceinline__ uint32_t ordered_bits(float f) {
    const uint32_t u = __float_as_uint(f);
    return (u & 0x80000000u) ? ~u : (u | 0x80000000u);   // larger float, larger key
}

/** One block per request: 4-pass radix selection of the k largest scores, then expansion to token ids. */
__global__ void __launch_bounds__(1024) mla_topk_kernel(
        const float* __restrict__ scores, long long max_pools, const int* __restrict__ lens, int top_pools,
        int width, int* __restrict__ idx) {
    const int b = blockIdx.x;
    const int tid = threadIdx.x;
    const int len = lens[b];
    const int pools = (len + 1) >> 2;
    const int k = pools < top_pools ? pools : top_pools;
    const float* s = scores + (long long) b * max_pools;
    int* out = idx + (long long) b * width;
    __shared__ unsigned hist[256];
    __shared__ unsigned s_prefix;
    __shared__ int s_krem;
    __shared__ int s_count;
    __shared__ int s_ties;
    for (int i = tid; i < width; i += 1024) out[i] = -1;
    if (k > 0) {
        unsigned prefix = 0;
        int krem = k;
        for (int pass = 0; pass < 4; ++pass) {
            const int shift = 24 - 8 * pass;
            for (int i = tid; i < 256; i += 1024) hist[i] = 0;
            __syncthreads();
            for (int i = tid; i < pools; i += 1024) {
                const uint32_t u = ordered_bits(s[i]);
                if (pass == 0 || (u >> (shift + 8)) == prefix) atomicAdd(&hist[(u >> shift) & 255], 1u);
            }
            __syncthreads();
            if (tid == 0) {
                int rem = krem;
                int d = 255;
                for (; d > 0; --d) {
                    const int c = (int) hist[d];
                    if (rem <= c) break;
                    rem -= c;
                }
                s_krem = rem;
                s_prefix = (pass == 0 ? 0u : prefix << 8) | (unsigned) d;
            }
            __syncthreads();
            krem = s_krem;
            prefix = s_prefix;
        }
        if (tid == 0) {
            s_count = 0;
            s_ties = 0;
        }
        __syncthreads();
        for (int i = tid; i < pools; i += 1024) {
            const uint32_t u = ordered_bits(s[i]);
            bool take = u > prefix;
            if (!take && u == prefix) take = atomicAdd(&s_ties, 1) < krem;
            if (take) {
                const int j = atomicAdd(&s_count, 1);
#pragma unroll
                for (int t = 0; t < 4; ++t) out[j * 4 + t] = i * 4 + t;
            }
        }
    }
    if (tid < 3) {
        const int tail = (len + 1) & 3;
        out[width - 3 + tid] = tid < tail ? (len + 1 - tail + tid) : -1;
    }
}

constexpr int kAttnHeads = 4;       // most heads per block
constexpr int kAttnMaxW = 2056;

/**
 * Block = kAttnHeads heads of one row (a decode request, or a prefill query: only the pointer table and the id row differ). The query
 * of the block's heads lives in registers as packed bf16 (each lane owns 16 of the 512 latent dims of every head), the token ids
 * in shared memory; scores are computed 4 tokens per warp at a time with all loads in flight, softmax per head, then each
 * thread (2 latent dims) accumulates 8 tokens per step. Invalid tokens (id < 0) get probability 0 and read row 0.
 */
template <int HPB, bool FP8>
__global__ void __launch_bounds__(256, 2) mla_attend_kernel(
        const __nv_bfloat16* __restrict__ qabs, const long long* __restrict__ lat_ptrs, const int* __restrict__ idx,
        const int* __restrict__ lens, int top_pools, int width_all, int heads, float scale, float* __restrict__ ctx) {
    extern __shared__ float smem[];
    float* sc = smem;                                                // [HPB][kAttnMaxW]
    int* sid = reinterpret_cast<int*>(smem + HPB * kAttnMaxW);  // [kAttnMaxW]
    const int b = blockIdx.x;
    const int h0 = blockIdx.y * HPB;
    const int tid = threadIdx.x;
    const int lane = tid & 31;
    const int warp = tid >> 5;
    const __nv_bfloat16* lat = reinterpret_cast<const __nv_bfloat16*>(lat_ptrs[b]);
    const unsigned char* latb = reinterpret_cast<const unsigned char*>(lat_ptrs[b]);
    // the ids of a row: the tokens of the selected pools first, -1 padding, the tokens of the open pool in the last three
    // slots; with the row length known only the valid prefix and those three are processed
    int width = width_all;
    if (lens != nullptr) {
        const int kp = min((lens[b] + 1) >> 2, top_pools);
        const int nv = 4 * kp;
        for (int j = tid; j < nv; j += 256) sid[j] = idx[(long long) b * width_all + j];
        if (tid < 3) sid[nv + tid] = idx[(long long) b * width_all + width_all - 3 + tid];
        width = nv + 3;
    } else {
        for (int j = tid; j < width; j += 256) sid[j] = idx[(long long) b * width_all + j];
    }
    // this lane's slice of every head's query: dims lane*8..+7 and 256 + lane*8..+7
    uint4 qr[HPB][2];
#pragma unroll
    for (int h = 0; h < HPB; ++h) {
        const __nv_bfloat16* qp = qabs + ((long long) b * heads + h0 + h) * kLat;
        qr[h][0] = *reinterpret_cast<const uint4*>(qp + lane * 8);
        qr[h][1] = *reinterpret_cast<const uint4*>(qp + 256 + lane * 8);
    }
    __syncthreads();
    constexpr int U = 4;
    for (int j0 = warp; j0 < width; j0 += 8 * U) {
        uint4 r0[U], r1[U];
        float4 s4[U];
#pragma unroll
        for (int u = 0; u < U; ++u) {
            const int j = j0 + 8 * u;
            int id = j < width ? sid[j] : 0;
            if (id < 0) id = 0;
            if constexpr (FP8) {
                const unsigned char* row = latb + (long long) id * kLatRow8;
                const uint2 a8 = *reinterpret_cast<const uint2*>(row + lane * 8);
                const uint2 b8 = *reinterpret_cast<const uint2*>(row + 256 + lane * 8);
                r0[u] = make_uint4(a8.x, a8.y, 0u, 0u);
                r1[u] = make_uint4(b8.x, b8.y, 0u, 0u);
                s4[u] = *reinterpret_cast<const float4*>(row + 512);
            } else {
                const __nv_bfloat16* row = lat + (long long) id * kLat;
                r0[u] = *reinterpret_cast<const uint4*>(row + lane * 8);
                r1[u] = *reinterpret_cast<const uint4*>(row + 256 + lane * 8);
            }
        }
#pragma unroll
        for (int u = 0; u < U; ++u) {
            const int j = j0 + 8 * u;
            float x[16];
            if constexpr (FP8) {
                const float sA = (lane >> 4) ? s4[u].y : s4[u].x;      // dims lane * 8 .. lane * 8 + 7 lie in tile lane / 16
                const float sB = (lane >> 4) ? s4[u].w : s4[u].z;      // dims 256 + lane * 8 .. in tile 2 + lane / 16
                const unsigned int w0[2] = {r0[u].x, r0[u].y};
                const unsigned int w1[2] = {r1[u].x, r1[u].y};
#pragma unroll
                for (int i = 0; i < 8; ++i) {
                    x[i] = fp8_to_f((unsigned char) ((w0[i >> 2] >> (8 * (i & 3))) & 0xFF)) * sA;
                    x[8 + i] = fp8_to_f((unsigned char) ((w1[i >> 2] >> (8 * (i & 3))) & 0xFF)) * sB;
                }
            } else {
                const __nv_bfloat162* a = reinterpret_cast<const __nv_bfloat162*>(&r0[u]);
                const __nv_bfloat162* c2 = reinterpret_cast<const __nv_bfloat162*>(&r1[u]);
#pragma unroll
                for (int i = 0; i < 4; ++i) {
                    x[2 * i] = __low2float(a[i]);
                    x[2 * i + 1] = __high2float(a[i]);
                    x[8 + 2 * i] = __low2float(c2[i]);
                    x[8 + 2 * i + 1] = __high2float(c2[i]);
                }
            }
            float part[HPB];
#pragma unroll
            for (int h = 0; h < HPB; ++h) {
                const __nv_bfloat162* q0 = reinterpret_cast<const __nv_bfloat162*>(&qr[h][0]);
                const __nv_bfloat162* q1 = reinterpret_cast<const __nv_bfloat162*>(&qr[h][1]);
                float s = 0.f;
#pragma unroll
                for (int i = 0; i < 4; ++i) {
                    s += __low2float(q0[i]) * x[2 * i] + __high2float(q0[i]) * x[2 * i + 1]
                       + __low2float(q1[i]) * x[8 + 2 * i] + __high2float(q1[i]) * x[8 + 2 * i + 1];
                }
                part[h] = s;
            }
#pragma unroll
            for (int h = 0; h < HPB; ++h) {
#pragma unroll
                for (int o = 16; o > 0; o >>= 1) part[h] += __shfl_xor_sync(0xffffffffu, part[h], o);
            }
            if (lane < HPB && j < width) {
                float v = part[0];
#pragma unroll
                for (int h = 1; h < HPB; ++h) if (lane == h) v = part[h];
                sc[lane * kAttnMaxW + j] = sid[j] < 0 ? -INFINITY : v * scale;
            }
        }
    }
    __syncthreads();
    // softmax: warp h owns head h
    if (warp < HPB) {
        float* row = sc + warp * kAttnMaxW;
        float mx = -INFINITY;
        for (int j = lane; j < width; j += 32) mx = fmaxf(mx, row[j]);
#pragma unroll
        for (int o = 16; o > 0; o >>= 1) mx = fmaxf(mx, __shfl_xor_sync(0xffffffffu, mx, o));
        float sum = 0.f;
        for (int j = lane; j < width; j += 32) {
            const float e = expf(row[j] - mx);
            row[j] = e;
            sum += e;
        }
#pragma unroll
        for (int o = 16; o > 0; o >>= 1) sum += __shfl_xor_sync(0xffffffffu, sum, o);
        const float inv = 1.f / sum;
        for (int j = lane; j < width; j += 32) row[j] *= inv;
    }
    __syncthreads();
    // weighted sum: thread t owns latent dims 2t, 2t + 1; 8 tokens per step with the loads in flight
    float acc[HPB][2];
#pragma unroll
    for (int h = 0; h < HPB; ++h) acc[h][0] = acc[h][1] = 0.f;
    constexpr int V = 8;
    for (int j0 = 0; j0 < width; j0 += V) {
        __nv_bfloat162 v[V];
        unsigned short b2[V];
        float vs[V];
#pragma unroll
        for (int u = 0; u < V; ++u) {
            const int j = j0 + u;
            int id = j < width ? sid[j] : 0;
            if (id < 0) id = 0;
            if constexpr (FP8) {
                const unsigned char* row = latb + (long long) id * kLatRow8;
                b2[u] = *reinterpret_cast<const unsigned short*>(row + 2 * tid);
                vs[u] = reinterpret_cast<const float*>(row + 512)[tid >> 6];
            } else {
                v[u] = *reinterpret_cast<const __nv_bfloat162*>(lat + (long long) id * kLat + 2 * tid);
            }
        }
#pragma unroll
        for (int u = 0; u < V; ++u) {
            const int j = j0 + u;
            float v0, v1;
            if constexpr (FP8) {
                v0 = fp8_to_f((unsigned char) (b2[u] & 0xFF)) * vs[u];
                v1 = fp8_to_f((unsigned char) (b2[u] >> 8)) * vs[u];
            } else {
                v0 = __low2float(v[u]);
                v1 = __high2float(v[u]);
            }
#pragma unroll
            for (int h = 0; h < HPB; ++h) {
                const float p = j < width ? sc[h * kAttnMaxW + j] : 0.f;
                acc[h][0] += p * v0;
                acc[h][1] += p * v1;
            }
        }
    }
#pragma unroll
    for (int h = 0; h < HPB; ++h) {
        *reinterpret_cast<float2*>(ctx + (((long long) b * heads + h0 + h) * kLat + 2 * tid)) = make_float2(acc[h][0], acc[h][1]);
    }
}

bool check(const at::Tensor& t, const char* name, at::ScalarType type, int dim, std::string& err) {
    if (!t.is_cuda() || t.scalar_type() != type || t.dim() != dim || !t.is_contiguous()) {
        err = std::string(name) + ": expected a contiguous " + std::to_string(dim) + "-D CUDA tensor of the right dtype";
        return false;
    }
    return true;
}

}  // namespace

extern "C" int smile_glm_ops_available(void) { return 1; }

// ---------------------------------------------------------------- one-shot peer-to-peer all-reduce (small messages)

namespace {

constexpr int kArMaxWorld = 8;
constexpr int kArMaxBlocks = 32;
constexpr size_t kArCapacity = 1 << 20;           // bytes per staging buffer (two per rank)

struct ArGroup {
    int world = 0;
    int dev[kArMaxWorld] = {0};
    float* buf[kArMaxWorld][2] = {{nullptr}};
    float* res[kArMaxWorld][2] = {{nullptr}};     // two-shot: the reduced slices, written by their owners
    unsigned* flags[kArMaxWorld] = {nullptr};     // [kArMaxBlocks]: the epoch each block of the rank has staged
    unsigned* flags2[kArMaxWorld] = {nullptr};    // two-shot: the epoch each block of the rank has reduced
    unsigned* epoch[kArMaxWorld] = {nullptr};     // [kArMaxBlocks]: calls so far of each block (private to the block)
};

struct ArPtrs {
    float* buf[kArMaxWorld][2];
    float* res[kArMaxWorld][2];
    unsigned* flags[kArMaxWorld];
    unsigned* flags2[kArMaxWorld];
};

__device__ __forceinline__ unsigned ld_volatile(const unsigned* p) {
    unsigned v;
    asm volatile("ld.volatile.global.u32 %0, [%1];" : "=r"(v) : "l"(p) : "memory");
    return v;
}

/**
 * Block b of every rank handles the same chunk of the vector: it copies the chunk of its own input into its rank's staging
 * buffer (the buffer alternates with the call number), raises its flag, waits for the flags of the same block of every other
 * rank and then sums the staged chunks of all ranks in rank order, so every rank produces identical bits. The epochs are
 * kept in device memory and advance with every call, so the kernel can be captured in a CUDA graph and replayed.
 */
__global__ void __launch_bounds__(256) ar_kernel(ArPtrs a, int rank, int world, float* __restrict__ data, int n4, int chunk4,
                                                 unsigned* __restrict__ epochs) {
    const int b = blockIdx.x;
    const int tid = threadIdx.x;
    __shared__ unsigned s_epoch;
    if (tid == 0) {
        s_epoch = epochs[b] + 1;
        epochs[b] = s_epoch;
    }
    __syncthreads();
    const unsigned e = s_epoch;
    const int par = e & 1;
    const int lo = b * chunk4;
    const int hi = min(n4, lo + chunk4);
    float4* mine = reinterpret_cast<float4*>(a.buf[rank][par]);
    const float4* in = reinterpret_cast<const float4*>(data);
    for (int i = lo + tid; i < hi; i += 256) mine[i] = in[i];
    __threadfence_system();
    __syncthreads();
    if (tid == 0) {
        asm volatile("st.volatile.global.u32 [%0], %1;" ::"l"(a.flags[rank] + b), "r"(e) : "memory");
    }
    if (tid < world) {
        const unsigned* f = a.flags[tid] + b;
        long long spins = 0;
        while ((int) (ld_volatile(f) - e) < 0) {
            if (++spins > 4000000000LL) {
                printf("smile all-reduce: rank %d block %d timed out waiting for rank %d (epoch %u)\n", rank, b, tid, e);
                __trap();
            }
        }
    }
    __syncthreads();
    __threadfence();
    float4* out = reinterpret_cast<float4*>(data);
    for (int i = lo + tid; i < hi; i += 256) {
        float4 sum = __ldcg(reinterpret_cast<const float4*>(a.buf[0][par]) + i);
        for (int p = 1; p < world; ++p) {
            const float4 v = __ldcg(reinterpret_cast<const float4*>(a.buf[p][par]) + i);
            sum.x += v.x;
            sum.y += v.y;
            sum.z += v.z;
            sum.w += v.w;
        }
        out[i] = sum;
    }
}

/**
 * Two-shot variant for larger messages: the vector is cut into one slice per rank; every rank stages its input, then reduces
 * only its own slice (reading 1/world of every rank's data, in rank order) and writes the sum into the result buffer of every
 * rank; after a second barrier every rank copies the gathered result back. About a quarter of the NVLink traffic of the
 * one-shot kernel at 8 ranks. Block b owns sub-slice b of every slice, so both barriers are per block.
 */
__global__ void __launch_bounds__(256) ar2_kernel(ArPtrs a, int rank, int world, float* __restrict__ data, int n4, int slice4,
                                                  int sub4, unsigned* __restrict__ epochs) {
    const int b = blockIdx.x;
    const int tid = threadIdx.x;
    __shared__ unsigned s_epoch;
    if (tid == 0) {
        s_epoch = epochs[b] + 1;
        epochs[b] = s_epoch;
    }
    __syncthreads();
    const unsigned e = s_epoch;
    const int par = e & 1;
    float4* mine = reinterpret_cast<float4*>(a.buf[rank][par]);
    const float4* in = reinterpret_cast<const float4*>(data);
    const int off = b * sub4;
    const int cnt = max(0, min(sub4, slice4 - off));
    for (int q = 0; q < world; ++q) {
        const int base = q * slice4 + off;
        const int len = max(0, min(cnt, n4 - base));
        for (int i = tid; i < len; i += 256) mine[base + i] = in[base + i];
    }
    __threadfence_system();
    __syncthreads();
    if (tid == 0) asm volatile("st.volatile.global.u32 [%0], %1;" ::"l"(a.flags[rank] + b), "r"(e) : "memory");
    if (tid < world) {
        const unsigned* f = a.flags[tid] + b;
        long long spins = 0;
        while ((int) (ld_volatile(f) - e) < 0) {
            if (++spins > 4000000000LL) {
                printf("smile all-reduce(2): rank %d block %d timed out in stage waiting for rank %d (epoch %u)\n", rank, b, tid, e);
                __trap();
            }
        }
    }
    __syncthreads();
    __threadfence();
    {
        const int base = rank * slice4 + off;
        const int len = max(0, min(cnt, n4 - base));
        for (int i = tid; i < len; i += 256) {
            float4 sum = __ldcg(reinterpret_cast<const float4*>(a.buf[0][par]) + base + i);
            for (int p = 1; p < world; ++p) {
                const float4 v = __ldcg(reinterpret_cast<const float4*>(a.buf[p][par]) + base + i);
                sum.x += v.x;
                sum.y += v.y;
                sum.z += v.z;
                sum.w += v.w;
            }
            for (int w = 0; w < world; ++w) reinterpret_cast<float4*>(a.res[w][par])[base + i] = sum;
        }
    }
    __threadfence_system();
    __syncthreads();
    if (tid == 0) asm volatile("st.volatile.global.u32 [%0], %1;" ::"l"(a.flags2[rank] + b), "r"(e) : "memory");
    if (tid < world) {
        const unsigned* f = a.flags2[tid] + b;
        long long spins = 0;
        while ((int) (ld_volatile(f) - e) < 0) {
            if (++spins > 4000000000LL) {
                printf("smile all-reduce(2): rank %d block %d timed out in gather waiting for rank %d (epoch %u)\n", rank, b, tid, e);
                __trap();
            }
        }
    }
    __syncthreads();
    __threadfence();
    float4* out = reinterpret_cast<float4*>(data);
    const float4* res = reinterpret_cast<const float4*>(a.res[rank][par]);
    for (int q = 0; q < world; ++q) {
        const int base = q * slice4 + off;
        const int len = max(0, min(cnt, n4 - base));
        for (int i = tid; i < len; i += 256) out[base + i] = __ldcg(res + base + i);
    }
}

}  // namespace

/** Creates a peer-to-peer all-reduce group over the devices (peer access enabled between all pairs); 0 if unsupported. */
extern "C" long long smile_ar_create(int world, const int* devices) {
    try {
        smile_torch_clear_error();
        if (world < 2 || world > kArMaxWorld) {
            set_err("smile_ar_create: 2 to 8 ranks");
            return 0;
        }
        std::unique_ptr<ArGroup> g(new ArGroup());
        g->world = world;
        for (int i = 0; i < world; ++i) g->dev[i] = devices[i];
        int saved = 0;
        cudaGetDevice(&saved);
        for (int i = 0; i < world; ++i) {
            cudaSetDevice(g->dev[i]);
            for (int j = 0; j < world; ++j) {
                if (i == j) continue;
                int can = 0;
                cudaDeviceCanAccessPeer(&can, g->dev[i], g->dev[j]);
                if (!can) {
                    cudaSetDevice(saved);
                    set_err("smile_ar_create: no peer access between devices " + std::to_string(g->dev[i]) + " and " + std::to_string(g->dev[j]));
                    return 0;
                }
                cudaError_t e = cudaDeviceEnablePeerAccess(g->dev[j], 0);
                if (e != cudaSuccess && e != cudaErrorPeerAccessAlreadyEnabled) {
                    cudaSetDevice(saved);
                    set_err(std::string("smile_ar_create: cannot enable peer access: ") + cudaGetErrorString(e));
                    return 0;
                }
                cudaGetLastError();
            }
            for (int k = 0; k < 2; ++k) {
                if (cudaMalloc(&g->buf[i][k], kArCapacity) != cudaSuccess) {
                    cudaSetDevice(saved);
                    set_err("smile_ar_create: out of memory");
                    return 0;
                }
            }
            for (int k = 0; k < 2; ++k) {
                if (cudaMalloc(&g->res[i][k], kArCapacity) != cudaSuccess) {
                    cudaSetDevice(saved);
                    set_err("smile_ar_create: out of memory");
                    return 0;
                }
            }
            cudaMalloc(&g->flags[i], kArMaxBlocks * sizeof(unsigned));
            cudaMalloc(&g->flags2[i], kArMaxBlocks * sizeof(unsigned));
            cudaMalloc(&g->epoch[i], kArMaxBlocks * sizeof(unsigned));
            cudaMemset(g->flags[i], 0, kArMaxBlocks * sizeof(unsigned));
            cudaMemset(g->flags2[i], 0, kArMaxBlocks * sizeof(unsigned));
            cudaMemset(g->epoch[i], 0, kArMaxBlocks * sizeof(unsigned));
            cudaDeviceSynchronize();
        }
        cudaSetDevice(saved);
        return (long long) g.release();
    } catch (const std::exception& ex) {
        smile_torch_set_error(ex.what());
        return 0;
    }
}

/** Sums a float32 tensor over the ranks in place (rank = index in the group). Returns 0, -1 if the tensor does not qualify. */
extern "C" int smile_ar_all_reduce(long long handle, int rank, ST_Tensor t) {
    try {
        smile_torch_clear_error();
        ArGroup* g = reinterpret_cast<ArGroup*>(handle);
        const at::Tensor& x = t->t;
        const int64_t n = x.numel();
        if (g == nullptr || rank < 0 || rank >= g->world || !x.is_cuda() || x.scalar_type() != at::kFloat || !x.is_contiguous()
                || n % 4 != 0 || n == 0 || (size_t) n * sizeof(float) > kArCapacity || x.get_device() != g->dev[rank]
                || ((uintptr_t) x.data_ptr()) % 16 != 0) {
            set_err("smile_ar_all_reduce: unsupported tensor");
            return -1;
        }
        c10::cuda::CUDAGuard guard(g->dev[rank]);
        ArPtrs a;
        for (int i = 0; i < g->world; ++i) {
            a.buf[i][0] = g->buf[i][0];
            a.buf[i][1] = g->buf[i][1];
            a.res[i][0] = g->res[i][0];
            a.res[i][1] = g->res[i][1];
            a.flags[i] = g->flags[i];
            a.flags2[i] = g->flags2[i];
        }
        const int n4 = (int) (n / 4);
        cudaStream_t stream = at::cuda::getCurrentCUDAStream(g->dev[rank]);
        static const int two_shot_from = std::getenv("SMILE_AR_TWO_SHOT_FLOATS") != nullptr
                ? std::atoi(std::getenv("SMILE_AR_TWO_SHOT_FLOATS")) : 16384;
        if (n >= two_shot_from) {
            const int slice4 = (n4 + g->world - 1) / g->world;
            const int blocks = (int) std::min<int64_t>(kArMaxBlocks, std::max<int64_t>(1, slice4 / 256));
            const int sub4 = (slice4 + blocks - 1) / blocks;
            ar2_kernel<<<blocks, 256, 0, stream>>>(a, rank, g->world, x.data_ptr<float>(), n4, slice4, sub4, g->epoch[rank]);
        } else {
            const int blocks = (int) std::min<int64_t>(kArMaxBlocks, std::max<int64_t>(1, n4 / 256));
            const int chunk4 = (n4 + blocks - 1) / blocks;
            ar_kernel<<<blocks, 256, 0, stream>>>(a, rank, g->world, x.data_ptr<float>(), n4, chunk4, g->epoch[rank]);
        }
        cudaError_t e = cudaGetLastError();
        if (e != cudaSuccess) {
            set_err(std::string("smile_ar_all_reduce: CUDA error: ") + cudaGetErrorString(e));
            return -1;
        }
        return 0;
    } catch (const std::exception& ex) {
        smile_torch_set_error(ex.what());
        return -1;
    }
}

extern "C" void smile_ar_destroy(long long handle) {
    ArGroup* g = reinterpret_cast<ArGroup*>(handle);
    if (g == nullptr) return;
    int saved = 0;
    cudaGetDevice(&saved);
    for (int i = 0; i < g->world; ++i) {
        cudaSetDevice(g->dev[i]);
        cudaFree(g->buf[i][0]);
        cudaFree(g->buf[i][1]);
        cudaFree(g->res[i][0]);
        cudaFree(g->res[i][1]);
        cudaFree(g->flags2[i]);
        cudaFree(g->flags[i]);
        cudaFree(g->epoch[i]);
    }
    cudaSetDevice(saved);
    delete g;
}


// ---------------------------------------------------------------- kernel time profile (CUPTI activity records)

namespace {

std::mutex g_prof_mu;
std::unordered_map<std::string, std::pair<uint64_t, uint64_t>> g_prof;   // name -> {count, nanoseconds}
int g_prof_device = 0;
std::string g_prof_report;

void CUPTIAPI prof_buffer_requested(uint8_t** buffer, size_t* size, size_t* max_records) {
    *size = 8 << 20;
    *buffer = (uint8_t*) std::aligned_alloc(8, *size);
    *max_records = 0;
}

void CUPTIAPI prof_buffer_completed(CUcontext, uint32_t, uint8_t* buffer, size_t, size_t valid) {
    CUpti_Activity* rec = nullptr;
    while (cuptiActivityGetNextRecord(buffer, valid, &rec) == CUPTI_SUCCESS) {
        if (rec->kind == CUPTI_ACTIVITY_KIND_CONCURRENT_KERNEL || rec->kind == CUPTI_ACTIVITY_KIND_KERNEL) {
            const CUpti_ActivityKernel10* k = reinterpret_cast<const CUpti_ActivityKernel10*>(rec);
            if ((int) k->deviceId == g_prof_device && k->name != nullptr) {
                std::lock_guard<std::mutex> lock(g_prof_mu);
                auto& e = g_prof[k->name];
                e.first++;
                e.second += k->end - k->start;
            }
        }
    }
    std::free(buffer);
}

}  // namespace

/** Starts recording the duration of every kernel (including the kernels of replayed CUDA graphs) that runs on one device. */
extern "C" int smile_prof_start(int device) {
    {
        std::lock_guard<std::mutex> lock(g_prof_mu);
        g_prof.clear();
        g_prof_device = device;
    }
    if (cuptiActivityRegisterCallbacks(prof_buffer_requested, prof_buffer_completed) != CUPTI_SUCCESS) return -1;
    if (cuptiActivityEnable(CUPTI_ACTIVITY_KIND_CONCURRENT_KERNEL) != CUPTI_SUCCESS) return -2;
    return 0;
}

/** Stops the recording and returns the report (one line per kernel name, longest total first); valid until the next call. */
extern "C" const char* smile_prof_stop(void) {
    cudaDeviceSynchronize();
    cuptiActivityFlushAll(1);
    cuptiActivityDisable(CUPTI_ACTIVITY_KIND_CONCURRENT_KERNEL);
    std::lock_guard<std::mutex> lock(g_prof_mu);
    std::vector<std::pair<std::string, std::pair<uint64_t, uint64_t>>> v(g_prof.begin(), g_prof.end());
    std::sort(v.begin(), v.end(), [](const auto& a, const auto& b) { return a.second.second > b.second.second; });
    std::string out;
    for (const auto& e : v) {
        char line[512];
        std::string name = e.first.substr(0, 150);
        std::snprintf(line, sizeof line, "%10.1f us %7llu x %8.2f us  %s\n", e.second.second / 1e3, (unsigned long long) e.second.first,
                      e.second.second / 1e3 / (double) e.second.first, name.c_str());
        out += line;
    }
    g_prof_report = out;
    return g_prof_report.c_str();
}


extern "C" ST_Tensor smile_indexer_scores(ST_Tensor q, ST_Tensor w, ST_Tensor pk, long long pool0, long long count,
                                          long long p0, double scale) {
    try {
        smile_torch_clear_error();
        std::string err;
        const at::Tensor& Q = q->t;
        const at::Tensor& Wt = w->t;
        const at::Tensor& PK = pk->t;
        if (!check(Q, "q", at::kBFloat16, 3, err) || !check(Wt, "w", at::kFloat, 2, err)
                || !check(PK, "pk", at::kBFloat16, 2, err)) {
            set_err("smile_indexer_scores: " + err);
            return nullptr;
        }
        const int64_t n = Q.size(0);
        if (Q.size(1) != kIdxHeads || Q.size(2) != kIdxDim || Wt.size(0) != n || Wt.size(1) != kIdxHeads
                || PK.size(1) != kIdxDim || pool0 < 0 || count < 1 || pool0 + count > PK.size(0)
                || ((uintptr_t) PK.data_ptr()) % 16 != 0) {
            set_err("smile_indexer_scores: inconsistent shapes or range");
            return nullptr;
        }
        const int dev = Q.get_device();
        c10::cuda::CUDAGuard guard(dev);
        at::Tensor out = torch::empty({n, (int64_t) count}, Q.options().dtype(at::kFloat));
        dim3 grid((unsigned) ((n + kIdxWarps - 1) / kIdxWarps), (unsigned) ((count + kIdxChunk - 1) / kIdxChunk));
        indexer_score_kernel<<<grid, 256, 0, at::cuda::getCurrentCUDAStream(dev)>>>(
                reinterpret_cast<const __nv_bfloat16*>(Q.data_ptr()), Wt.data_ptr<float>(),
                reinterpret_cast<const __nv_bfloat16*>(PK.data_ptr()), pool0, count, p0, (float) scale, (int) n,
                out.data_ptr<float>());
        cudaError_t st = cudaGetLastError();
        if (st != cudaSuccess) {
            set_err(std::string("smile_indexer_scores: CUDA error: ") + cudaGetErrorString(st));
            return nullptr;
        }
        return new ST_Tensor_{ out };
    } catch (const std::exception& ex) {
        smile_torch_set_error(ex.what());
        return nullptr;
    }
}

extern "C" ST_Tensor smile_kda_forward(ST_Tensor raw, ST_Tensor fg, ST_Tensor braw, ST_Tensor convw, ST_Tensor a_log,
                                       ST_Tensor dt_bias, ST_Tensor conv_state, ST_Tensor rec_state, ST_Tensor slots,
                                       double gate_bound) {
    try {
        smile_torch_clear_error();
        std::string err;
        const at::Tensor& R = raw->t;
        const at::Tensor& F = fg->t;
        const at::Tensor& Bt = braw->t;
        const at::Tensor& CW = convw->t;
        const at::Tensor& AL = a_log->t;
        const at::Tensor& DT = dt_bias->t;
        const at::Tensor& CS = conv_state->t;
        const at::Tensor& RS = rec_state->t;
        const at::Tensor& SL = slots->t;
        if (!check(R, "raw", at::kFloat, 3, err) || !check(F, "fg", at::kFloat, 3, err)
                || !check(Bt, "braw", at::kFloat, 3, err) || !check(CW, "convw", at::kFloat, 2, err)
                || !check(AL, "a_log", at::kFloat, 1, err) || !check(DT, "dt_bias", at::kFloat, 1, err)
                || !check(CS, "conv_state", at::kFloat, 3, err) || !check(RS, "rec_state", at::kFloat, 4, err)
                || !check(SL, "slots", at::kInt, 1, err)) {
            set_err("smile_kda_forward: " + err);
            return nullptr;
        }
        const int64_t B = R.size(0), T = R.size(1), C = R.size(2), H = Bt.size(2), W = H * kKD;
        if (C != 3 * W || F.size(0) != B || F.size(1) != T || F.size(2) != W || Bt.size(0) != B || Bt.size(1) != T
                || CW.size(0) != C || CW.size(1) != 4 || AL.size(0) != H || DT.size(0) != W
                || CS.size(1) != 3 || CS.size(2) != C || RS.size(1) != H || RS.size(2) != kKD || RS.size(3) != kKD
                || CS.size(0) != RS.size(0) || SL.size(0) != B) {
            set_err("smile_kda_forward: inconsistent shapes");
            return nullptr;
        }
        const int dev = R.get_device();
        c10::cuda::CUDAGuard guard(dev);
        at::Tensor out = torch::empty({B, T, H, (int64_t) kKD}, R.options());
        cudaStream_t stream = at::cuda::getCurrentCUDAStream(dev);
        dim3 grid(kKD / kCols, (unsigned) (B * H));
        static const bool two_step = std::getenv("SMILE_KDA_ONE_PASS") == nullptr;
        if (two_step && T >= 64) {
            // prefill: the state-independent terms of all tokens in parallel, then the recurrence with one barrier per token
            at::Tensor pw = torch::empty({B, T, H, 4, (int64_t) kKD}, R.options());
            at::Tensor pv = torch::empty({B, T, H, (int64_t) kKD}, R.options());
            at::Tensor ps = torch::empty({B, T, H, 2}, R.options());
            dim3 pgrid((unsigned) T, (unsigned) (B * H));
            kda_prep_kernel<<<pgrid, 128, 0, stream>>>(R.data_ptr<float>(), F.data_ptr<float>(), Bt.data_ptr<float>(),
                                                       CW.data_ptr<float>(), AL.data_ptr<float>(), DT.data_ptr<float>(),
                                                       CS.data_ptr<float>(), SL.data_ptr<int>(), pw.data_ptr<float>(),
                                                       pv.data_ptr<float>(), ps.data_ptr<float>(), (int) T, (int) H,
                                                       (float) gate_bound);
            kda_scan_kernel<<<grid, 128, 0, stream>>>(pw.data_ptr<float>(), pv.data_ptr<float>(), ps.data_ptr<float>(),
                                                      RS.data_ptr<float>(), SL.data_ptr<int>(), out.data_ptr<float>(),
                                                      (int) T, (int) H);
        } else {
            kda_kernel<<<grid, 128, 0, stream>>>(R.data_ptr<float>(), F.data_ptr<float>(), Bt.data_ptr<float>(),
                                                 CW.data_ptr<float>(), AL.data_ptr<float>(), DT.data_ptr<float>(),
                                                 CS.data_ptr<float>(), RS.data_ptr<float>(), SL.data_ptr<int>(),
                                                 out.data_ptr<float>(), nullptr, (int) T, (int) H, (float) gate_bound);
        }
        dim3 cgrid((unsigned) ((C + 255) / 256), (unsigned) B);
        kda_conv_state_kernel<<<cgrid, 256, 0, stream>>>(R.data_ptr<float>(), CS.data_ptr<float>(),
                                                         SL.data_ptr<int>(), (int) T, (int) C);
        cudaError_t st = cudaGetLastError();
        if (st != cudaSuccess) {
            set_err(std::string("smile_kda_forward: CUDA error: ") + cudaGetErrorString(st));
            return nullptr;
        }
        return new ST_Tensor_{ out };
    } catch (const std::exception& ex) {
        smile_torch_set_error(ex.what());
        return nullptr;
    }
}


extern "C" ST_Tensor smile_kda_forward_window(ST_Tensor raw, ST_Tensor fg, ST_Tensor braw, ST_Tensor convw,
                                              ST_Tensor a_log, ST_Tensor dt_bias, ST_Tensor conv_state,
                                              ST_Tensor rec_state, ST_Tensor slots, ST_Tensor rec_save,
                                              double gate_bound) {
    try {
        smile_torch_clear_error();
        std::string err;
        const at::Tensor& R = raw->t;
        const at::Tensor& F = fg->t;
        const at::Tensor& Bt = braw->t;
        const at::Tensor& CW = convw->t;
        const at::Tensor& AL = a_log->t;
        const at::Tensor& DT = dt_bias->t;
        const at::Tensor& CS = conv_state->t;
        const at::Tensor& RS = rec_state->t;
        const at::Tensor& SL = slots->t;
        const at::Tensor& SV = rec_save->t;
        if (!check(R, "raw", at::kFloat, 3, err) || !check(F, "fg", at::kFloat, 3, err)
                || !check(Bt, "braw", at::kFloat, 3, err) || !check(CW, "convw", at::kFloat, 2, err)
                || !check(AL, "a_log", at::kFloat, 1, err) || !check(DT, "dt_bias", at::kFloat, 1, err)
                || !check(CS, "conv_state", at::kFloat, 3, err) || !check(RS, "rec_state", at::kFloat, 4, err)
                || !check(SL, "slots", at::kInt, 1, err) || !check(SV, "rec_save", at::kFloat, 5, err)) {
            set_err("smile_kda_forward_window: " + err);
            return nullptr;
        }
        const int64_t B = R.size(0), T = R.size(1), C = R.size(2), H = Bt.size(2), W = H * kKD;
        if (C != 3 * W || F.size(0) != B || F.size(1) != T || F.size(2) != W || Bt.size(0) != B || Bt.size(1) != T
                || CW.size(0) != C || CW.size(1) != 4 || AL.size(0) != H || DT.size(0) != W
                || CS.size(1) != 3 || CS.size(2) != C || RS.size(1) != H || RS.size(2) != kKD || RS.size(3) != kKD
                || CS.size(0) != RS.size(0) || SL.size(0) != B
                || SV.size(0) != B || SV.size(1) != T || SV.size(2) != H || SV.size(3) != kKD || SV.size(4) != kKD) {
            set_err("smile_kda_forward_window: inconsistent shapes");
            return nullptr;
        }
        const int dev = R.get_device();
        c10::cuda::CUDAGuard guard(dev);
        at::Tensor out = torch::empty({B, T, H, (int64_t) kKD}, R.options());
        dim3 grid(kKD / kCols, (unsigned) (B * H));
        kda_kernel<<<grid, 128, 0, at::cuda::getCurrentCUDAStream(dev)>>>(
                R.data_ptr<float>(), F.data_ptr<float>(), Bt.data_ptr<float>(), CW.data_ptr<float>(),
                AL.data_ptr<float>(), DT.data_ptr<float>(), CS.data_ptr<float>(), RS.data_ptr<float>(),
                SL.data_ptr<int>(), out.data_ptr<float>(), SV.data_ptr<float>(), (int) T, (int) H, (float) gate_bound);
        cudaError_t st = cudaGetLastError();
        if (st != cudaSuccess) {
            set_err(std::string("smile_kda_forward_window: CUDA error: ") + cudaGetErrorString(st));
            return nullptr;
        }
        return new ST_Tensor_{ out };
    } catch (const std::exception& ex) {
        smile_torch_set_error(ex.what());
        return nullptr;
    }
}

extern "C" int smile_kda_commit(ST_Tensor raw_save, ST_Tensor conv_state, ST_Tensor rec_state, ST_Tensor rec_save,
                                ST_Tensor slots, ST_Tensor accept) {
    try {
        smile_torch_clear_error();
        std::string err;
        const at::Tensor& R = raw_save->t;
        const at::Tensor& CS = conv_state->t;
        const at::Tensor& RS = rec_state->t;
        const at::Tensor& SV = rec_save->t;
        if (!check(R, "raw_save", at::kFloat, 3, err) || !check(CS, "conv_state", at::kFloat, 3, err)
                || !check(RS, "rec_state", at::kFloat, 4, err) || !check(SV, "rec_save", at::kFloat, 5, err)
                || !check(slots->t, "slots", at::kInt, 1, err) || !check(accept->t, "accept", at::kInt, 1, err)) {
            set_err("smile_kda_commit: " + err);
            return -1;
        }
        const int64_t B = R.size(0), T = R.size(1), C = R.size(2), H = RS.size(1);
        if (SV.size(0) != B || SV.size(1) != T || SV.size(2) != H || slots->t.size(0) != B || accept->t.size(0) != B
                || CS.size(2) != C || C != 3 * H * kKD) {
            set_err("smile_kda_commit: inconsistent shapes");
            return -1;
        }
        const int dev = R.get_device();
        c10::cuda::CUDAGuard guard(dev);
        const int64_t sz = H * kKD * kKD;
        dim3 grid((unsigned) std::min<int64_t>((std::max<int64_t>(sz, C) + 255) / 256, 256), (unsigned) B);
        kda_commit_kernel<<<grid, 256, 0, at::cuda::getCurrentCUDAStream(dev)>>>(
                R.data_ptr<float>(), CS.data_ptr<float>(), RS.data_ptr<float>(), SV.data_ptr<float>(),
                slots->t.data_ptr<int>(), accept->t.data_ptr<int>(), (int) T, (int) C, (int) H);
        cudaError_t st = cudaGetLastError();
        if (st != cudaSuccess) {
            set_err(std::string("smile_kda_commit: CUDA error: ") + cudaGetErrorString(st));
            return -1;
        }
        return 0;
    } catch (const std::exception& ex) {
        smile_torch_set_error(ex.what());
        return -1;
    }
}

extern "C" int smile_mla_window_append(ST_Tensor lat_new, ST_Tensor key_new, ST_Tensor gate_new, ST_Tensor ape,
                                       ST_Tensor lat_ptrs, ST_Tensor pool_ptrs, ST_Tensor pend_ptrs, ST_Tensor lens,
                                       long long rows, int fp8) {
    try {
        smile_torch_clear_error();
        std::string err;
        if (!check(lat_new->t, "lat_new", at::kFloat, 2, err) || !check(key_new->t, "key_new", at::kFloat, 2, err)
                || !check(gate_new->t, "gate_new", at::kFloat, 2, err) || !check(ape->t, "ape", at::kFloat, 2, err)
                || !check(lat_ptrs->t, "lat_ptrs", at::kLong, 1, err) || !check(pool_ptrs->t, "pool_ptrs", at::kLong, 1, err)
                || !check(pend_ptrs->t, "pend_ptrs", at::kLong, 1, err) || !check(lens->t, "lens", at::kInt, 1, err)) {
            set_err("smile_mla_window_append: " + err);
            return -1;
        }
        const int64_t B = lens->t.size(0);
        if (rows < 1 || lat_new->t.size(0) != B * rows || lat_new->t.size(1) != kLat || key_new->t.size(0) != B * rows
                || key_new->t.size(1) != kPoolDim || gate_new->t.size(0) != B * rows || gate_new->t.size(1) != kPoolDim
                || ape->t.size(0) != 4 || ape->t.size(1) != kPoolDim || lat_ptrs->t.size(0) != B
                || pool_ptrs->t.size(0) != B || pend_ptrs->t.size(0) != B) {
            set_err("smile_mla_window_append: inconsistent shapes");
            return -1;
        }
        const int dev = lat_new->t.get_device();
        c10::cuda::CUDAGuard guard(dev);
        mla_window_append_kernel<<<(unsigned) B, kPoolDim, 0, at::cuda::getCurrentCUDAStream(dev)>>>(
                lat_new->t.data_ptr<float>(), key_new->t.data_ptr<float>(), gate_new->t.data_ptr<float>(),
                ape->t.data_ptr<float>(), reinterpret_cast<const long long*>(lat_ptrs->t.data_ptr<int64_t>()),
                reinterpret_cast<const long long*>(pool_ptrs->t.data_ptr<int64_t>()),
                reinterpret_cast<const long long*>(pend_ptrs->t.data_ptr<int64_t>()), lens->t.data_ptr<int>(), (int) rows, fp8);
        cudaError_t st = cudaGetLastError();
        if (st != cudaSuccess) {
            set_err(std::string("smile_mla_window_append: CUDA error: ") + cudaGetErrorString(st));
            return -1;
        }
        return 0;
    } catch (const std::exception& ex) {
        smile_torch_set_error(ex.what());
        return -1;
    }
}

extern "C" int smile_mla_window_commit(ST_Tensor key_new, ST_Tensor gate_new, ST_Tensor pend_ptrs, ST_Tensor lens,
                                       ST_Tensor accept, long long rows) {
    try {
        smile_torch_clear_error();
        std::string err;
        if (!check(key_new->t, "key_new", at::kFloat, 2, err) || !check(gate_new->t, "gate_new", at::kFloat, 2, err)
                || !check(pend_ptrs->t, "pend_ptrs", at::kLong, 1, err) || !check(lens->t, "lens", at::kInt, 1, err)
                || !check(accept->t, "accept", at::kInt, 1, err)) {
            set_err("smile_mla_window_commit: " + err);
            return -1;
        }
        const int64_t B = lens->t.size(0);
        if (rows < 1 || key_new->t.size(0) != B * rows || key_new->t.size(1) != kPoolDim || gate_new->t.size(0) != B * rows
                || pend_ptrs->t.size(0) != B || accept->t.size(0) != B) {
            set_err("smile_mla_window_commit: inconsistent shapes");
            return -1;
        }
        const int dev = key_new->t.get_device();
        c10::cuda::CUDAGuard guard(dev);
        mla_window_commit_kernel<<<(unsigned) B, kPoolDim, 0, at::cuda::getCurrentCUDAStream(dev)>>>(
                key_new->t.data_ptr<float>(), gate_new->t.data_ptr<float>(),
                reinterpret_cast<const long long*>(pend_ptrs->t.data_ptr<int64_t>()), lens->t.data_ptr<int>(),
                accept->t.data_ptr<int>(), (int) rows);
        cudaError_t st = cudaGetLastError();
        if (st != cudaSuccess) {
            set_err(std::string("smile_mla_window_commit: CUDA error: ") + cudaGetErrorString(st));
            return -1;
        }
        return 0;
    } catch (const std::exception& ex) {
        smile_torch_set_error(ex.what());
        return -1;
    }
}


extern "C" ST_Tensor smile_glm_swiglu(ST_Tensor gu, double limit) {
    try {
        smile_torch_clear_error();
        const at::Tensor& G = gu->t;
        if (!G.is_cuda() || G.dim() != 2 || !G.is_contiguous() || G.size(1) % 2 != 0
                || (G.scalar_type() != at::kFloat && G.scalar_type() != at::kBFloat16)) {
            set_err("smile_glm_swiglu: needs a contiguous CUDA float32/bfloat16 [rows, 2 * inter]");
            return nullptr;
        }
        const int64_t rows = G.size(0);
        const int inter = (int) (G.size(1) / 2);
        c10::cuda::CUDAGuard guard(G.get_device());
        at::Tensor out = torch::empty({rows, (int64_t) inter}, G.options().dtype(at::kBFloat16));
        const int64_t total = rows * inter;
        if (total > 0) {
            const unsigned grid = (unsigned) ((total + 255) / 256);
            cudaStream_t st = at::cuda::getCurrentCUDAStream(G.get_device());
            if (G.scalar_type() == at::kFloat) {
                glm_swiglu_kernel<float><<<grid, 256, 0, st>>>(G.data_ptr<float>(),
                        reinterpret_cast<__nv_bfloat16*>(out.data_ptr()), total, inter, (float) limit);
            } else {
                glm_swiglu_kernel<__nv_bfloat16><<<grid, 256, 0, st>>>(reinterpret_cast<const __nv_bfloat16*>(G.data_ptr()),
                        reinterpret_cast<__nv_bfloat16*>(out.data_ptr()), total, inter, (float) limit);
            }
        }
        cudaError_t e = cudaGetLastError();
        if (e != cudaSuccess) {
            set_err(std::string("smile_glm_swiglu: CUDA error: ") + cudaGetErrorString(e));
            return nullptr;
        }
        return new ST_Tensor_{ out };
    } catch (const std::exception& ex) {
        smile_torch_set_error(ex.what());
        return nullptr;
    }
}


extern "C" int smile_glm_mla_proj_epilogue(ST_Tensor y, ST_Tensor qa_w, ST_Tensor kv_w, ST_Tensor ln_w, ST_Tensor ln_b,
                                           double eps, double ln_eps, double w_scale, ST_Tensor *out) {
    try {
        smile_torch_clear_error();
        std::string err;
        const at::Tensor& Y = y->t;
        if (!check(Y, "y", at::kFloat, 2, err) || !Y.is_contiguous() || Y.size(1) != kProjCols
                || !check(qa_w->t, "qa_w", at::kFloat, 1, err) || qa_w->t.size(0) != kProjQa
                || !check(kv_w->t, "kv_w", at::kFloat, 1, err) || kv_w->t.size(0) != kProjKv
                || !check(ln_w->t, "ln_w", at::kFloat, 1, err) || ln_w->t.size(0) != kProjIdx
                || !check(ln_b->t, "ln_b", at::kFloat, 1, err) || ln_b->t.size(0) != kProjIdx) {
            set_err("smile_glm_mla_proj_epilogue: needs a contiguous float32 [n, 2336] and float32 norm weights " + err);
            return -1;
        }
        const int64_t n = Y.size(0);
        c10::cuda::CUDAGuard guard(Y.get_device());
        at::Tensor qresid = torch::empty({n, (int64_t) kProjQa}, Y.options().dtype(at::kBFloat16));
        at::Tensor latent = torch::empty({n, (int64_t) kProjKv}, Y.options());
        at::Tensor key = torch::empty({n, (int64_t) kProjIdx}, Y.options());
        at::Tensor gate = torch::empty({n, (int64_t) kProjGate}, Y.options());
        at::Tensor weights = torch::empty({n, (int64_t) kProjW}, Y.options());
        if (n > 0) {
            glm_mla_proj_epilogue_kernel<<<(unsigned) n, 256, 0, at::cuda::getCurrentCUDAStream(Y.get_device())>>>(
                    Y.data_ptr<float>(), qa_w->t.data_ptr<float>(), kv_w->t.data_ptr<float>(), ln_w->t.data_ptr<float>(),
                    ln_b->t.data_ptr<float>(), (float) eps, (float) ln_eps, (float) w_scale,
                    reinterpret_cast<__nv_bfloat16*>(qresid.data_ptr()), latent.data_ptr<float>(), key.data_ptr<float>(),
                    gate.data_ptr<float>(), weights.data_ptr<float>());
        }
        cudaError_t e = cudaGetLastError();
        if (e != cudaSuccess) {
            set_err(std::string("smile_glm_mla_proj_epilogue: CUDA error: ") + cudaGetErrorString(e));
            return -1;
        }
        out[0] = new ST_Tensor_{ qresid };
        out[1] = new ST_Tensor_{ latent };
        out[2] = new ST_Tensor_{ key };
        out[3] = new ST_Tensor_{ gate };
        out[4] = new ST_Tensor_{ weights };
        return 0;
    } catch (const std::exception& ex) {
        smile_torch_set_error(ex.what());
        return -1;
    }
}


extern "C" int smile_glm_mla_absorb(ST_Tensor qi, ST_Tensor wuk, long long heads, ST_Tensor *out) {
    try {
        smile_torch_clear_error();
        std::string err;
        const at::Tensor& Q = qi->t;
        const at::Tensor& W = wuk->t;
        if (!check(Q, "qi", at::kFloat, 2, err) || !Q.is_contiguous() || !check(W, "wuk", at::kBFloat16, 3, err)
                || !W.is_contiguous() || W.size(0) != heads || W.size(1) != kNope || W.size(2) != kLat
                || Q.size(1) != heads * kNope + 4096) {
            set_err("smile_glm_mla_absorb: needs contiguous float32 qi [B, heads * 256 + 4096] and bfloat16 w_uk [heads, 256, 512] " + err);
            return -1;
        }
        const int64_t B = Q.size(0);
        c10::cuda::CUDAGuard guard(Q.get_device());
        at::Tensor qabs = torch::empty({B, heads, (int64_t) kLat}, Q.options().dtype(at::kBFloat16));
        at::Tensor idxq = torch::empty({B, 32, 128}, Q.options().dtype(at::kBFloat16));
        if (B > 0) {
            glm_mla_absorb_kernel<<<dim3((unsigned) B, (unsigned) heads + 1, 4), 1024, 0, at::cuda::getCurrentCUDAStream(Q.get_device())>>>(
                    Q.data_ptr<float>(), (int) Q.size(1), reinterpret_cast<const __nv_bfloat16*>(W.data_ptr()),
                    reinterpret_cast<__nv_bfloat16*>(qabs.data_ptr()), reinterpret_cast<__nv_bfloat16*>(idxq.data_ptr()), (int) heads);
        }
        cudaError_t e = cudaGetLastError();
        if (e != cudaSuccess) {
            set_err(std::string("smile_glm_mla_absorb: CUDA error: ") + cudaGetErrorString(e));
            return -1;
        }
        out[0] = new ST_Tensor_{ qabs };
        out[1] = new ST_Tensor_{ idxq };
        return 0;
    } catch (const std::exception& ex) {
        smile_torch_set_error(ex.what());
        return -1;
    }
}

extern "C" ST_Tensor smile_glm_mla_wuv(ST_Tensor ctx, ST_Tensor wuvt) {
    try {
        smile_torch_clear_error();
        std::string err;
        const at::Tensor& C = ctx->t;
        const at::Tensor& W = wuvt->t;
        if (!check(C, "ctx", at::kFloat, 3, err) || !C.is_contiguous() || !check(W, "wuvt", at::kBFloat16, 3, err)
                || !W.is_contiguous() || W.size(0) != C.size(1) || W.size(1) != kLat || W.size(2) != kValueDim
                || C.size(2) != kLat) {
            set_err("smile_glm_mla_wuv: needs contiguous float32 ctx [B, heads, 512] and bfloat16 w_uv_t [heads, 512, 256] " + err);
            return nullptr;
        }
        const int64_t B = C.size(0), heads = C.size(1);
        c10::cuda::CUDAGuard guard(C.get_device());
        at::Tensor out = torch::empty({B, heads * kValueDim}, C.options().dtype(at::kBFloat16));
        if (B > 0) {
            glm_mla_wuv_kernel<<<dim3((unsigned) B, (unsigned) heads, 2), 1024, 0, at::cuda::getCurrentCUDAStream(C.get_device())>>>(
                    C.data_ptr<float>(), reinterpret_cast<const __nv_bfloat16*>(W.data_ptr()),
                    reinterpret_cast<__nv_bfloat16*>(out.data_ptr()), (int) heads);
        }
        cudaError_t e = cudaGetLastError();
        if (e != cudaSuccess) {
            set_err(std::string("smile_glm_mla_wuv: CUDA error: ") + cudaGetErrorString(e));
            return nullptr;
        }
        return new ST_Tensor_{ out };
    } catch (const std::exception& ex) {
        smile_torch_set_error(ex.what());
        return nullptr;
    }
}


extern "C" int smile_glm_latent_quantize(ST_Tensor src, ST_Tensor dst) {
    try {
        smile_torch_clear_error();
        const at::Tensor& S = src->t;
        const at::Tensor& D = dst->t;
        if (!S.is_cuda() || S.scalar_type() != at::kFloat || S.dim() != 2 || S.size(1) != kLat || !S.is_contiguous()
                || !D.is_cuda() || D.scalar_type() != at::kByte || D.dim() != 2 || D.size(0) != S.size(0) || D.size(1) != kLatRow8
                || !D.is_contiguous()) {
            set_err("smile_glm_latent_quantize: needs contiguous float32 [n, 512] -> uint8 [n, 528] on the GPU");
            return -1;
        }
        const int64_t n = S.size(0);
        c10::cuda::CUDAGuard guard(S.get_device());
        if (n > 0) {
            latent_quantize_kernel<<<(unsigned) n, 128, 0, at::cuda::getCurrentCUDAStream(S.get_device())>>>(
                    S.data_ptr<float>(), D.data_ptr<unsigned char>());
        }
        cudaError_t e = cudaGetLastError();
        if (e != cudaSuccess) {
            set_err(std::string("smile_glm_latent_quantize: CUDA error: ") + cudaGetErrorString(e));
            return -1;
        }
        return 0;
    } catch (const std::exception& ex) {
        smile_torch_set_error(ex.what());
        return -1;
    }
}

extern "C" ST_Tensor smile_glm_latent_gather(ST_Tensor cache, ST_Tensor ids) {
    try {
        smile_torch_clear_error();
        const at::Tensor& C = cache->t;
        const at::Tensor& I = ids->t;
        if (!C.is_cuda() || C.scalar_type() != at::kByte || C.dim() != 2 || C.size(1) != kLatRow8 || !C.is_contiguous()
                || !I.is_cuda() || I.scalar_type() != at::kLong || I.dim() != 1 || !I.is_contiguous()) {
            set_err("smile_glm_latent_gather: needs a contiguous uint8 [T, 528] cache and int64 [n] ids on the GPU");
            return nullptr;
        }
        const int64_t n = I.size(0);
        c10::cuda::CUDAGuard guard(C.get_device());
        at::Tensor out = torch::empty({n, (int64_t) kLat}, C.options().dtype(at::kBFloat16));
        if (n > 0) {
            latent_gather_kernel<<<(unsigned) n, 128, 0, at::cuda::getCurrentCUDAStream(C.get_device())>>>(
                    C.data_ptr<unsigned char>(), I.data_ptr<int64_t>() == nullptr ? nullptr : reinterpret_cast<const long long*>(I.data_ptr<int64_t>()),
                    reinterpret_cast<__nv_bfloat16*>(out.data_ptr()));
        }
        cudaError_t e = cudaGetLastError();
        if (e != cudaSuccess) {
            set_err(std::string("smile_glm_latent_gather: CUDA error: ") + cudaGetErrorString(e));
            return nullptr;
        }
        return new ST_Tensor_{ out };
    } catch (const std::exception& ex) {
        smile_torch_set_error(ex.what());
        return nullptr;
    }
}

extern "C" int smile_mla_decode_append(ST_Tensor lat_new, ST_Tensor key_new, ST_Tensor gate_new, ST_Tensor ape,
                                       ST_Tensor lat_ptrs, ST_Tensor pool_ptrs, ST_Tensor pend_ptrs, ST_Tensor lens, int fp8) {
    try {
        smile_torch_clear_error();
        std::string err;
        if (!check(lat_new->t, "lat_new", at::kFloat, 2, err) || !check(key_new->t, "key_new", at::kFloat, 2, err)
                || !check(gate_new->t, "gate_new", at::kFloat, 2, err) || !check(ape->t, "ape", at::kFloat, 2, err)
                || !check(lat_ptrs->t, "lat_ptrs", at::kLong, 1, err) || !check(pool_ptrs->t, "pool_ptrs", at::kLong, 1, err)
                || !check(pend_ptrs->t, "pend_ptrs", at::kLong, 1, err) || !check(lens->t, "lens", at::kInt, 1, err)) {
            set_err("smile_mla_decode_append: " + err);
            return -1;
        }
        const int64_t B = lat_new->t.size(0);
        if (lat_new->t.size(1) != kLat || key_new->t.size(1) != kPoolDim || gate_new->t.size(1) != kPoolDim
                || ape->t.size(0) != 4 || ape->t.size(1) != kPoolDim || lens->t.size(0) != B) {
            set_err("smile_mla_decode_append: inconsistent shapes");
            return -1;
        }
        const int dev = lat_new->t.get_device();
        c10::cuda::CUDAGuard guard(dev);
        mla_append_kernel<<<(unsigned) B, kPoolDim, 0, at::cuda::getCurrentCUDAStream(dev)>>>(
                lat_new->t.data_ptr<float>(), key_new->t.data_ptr<float>(), gate_new->t.data_ptr<float>(),
                ape->t.data_ptr<float>(), reinterpret_cast<const long long*>(lat_ptrs->t.data_ptr<int64_t>()), reinterpret_cast<const long long*>(pool_ptrs->t.data_ptr<int64_t>()),
                reinterpret_cast<const long long*>(pend_ptrs->t.data_ptr<int64_t>()), lens->t.data_ptr<int>(), fp8);
        cudaError_t st = cudaGetLastError();
        if (st != cudaSuccess) {
            set_err(std::string("smile_mla_decode_append: CUDA error: ") + cudaGetErrorString(st));
            return -1;
        }
        return 0;
    } catch (const std::exception& ex) {
        smile_torch_set_error(ex.what());
        return -1;
    }
}

extern "C" ST_Tensor smile_mla_decode_scores(ST_Tensor q, ST_Tensor w, ST_Tensor pool_ptrs, ST_Tensor lens,
                                             long long max_pools, double scale) {
    try {
        smile_torch_clear_error();
        std::string err;
        if (!check(q->t, "q", at::kBFloat16, 3, err) || !check(w->t, "w", at::kFloat, 2, err)
                || !check(pool_ptrs->t, "pool_ptrs", at::kLong, 1, err) || !check(lens->t, "lens", at::kInt, 1, err)) {
            set_err("smile_mla_decode_scores: " + err);
            return nullptr;
        }
        const int64_t B = q->t.size(0);
        if (q->t.size(1) != kIdxHeads || q->t.size(2) != kIdxDim || w->t.size(0) != B || lens->t.size(0) != B || max_pools < 1) {
            set_err("smile_mla_decode_scores: inconsistent shapes");
            return nullptr;
        }
        const int dev = q->t.get_device();
        c10::cuda::CUDAGuard guard(dev);
        at::Tensor out = torch::empty({B, (int64_t) max_pools}, q->t.options().dtype(at::kFloat));
        dim3 grid((unsigned) B, (unsigned) ((max_pools + 511) / 512));
        mla_scores_kernel<<<grid, 256, 0, at::cuda::getCurrentCUDAStream(dev)>>>(
                reinterpret_cast<const __nv_bfloat16*>(q->t.data_ptr()), w->t.data_ptr<float>(),
                reinterpret_cast<const long long*>(pool_ptrs->t.data_ptr<int64_t>()), lens->t.data_ptr<int>(), max_pools, (float) scale, out.data_ptr<float>());
        cudaError_t st = cudaGetLastError();
        if (st != cudaSuccess) {
            set_err(std::string("smile_mla_decode_scores: CUDA error: ") + cudaGetErrorString(st));
            return nullptr;
        }
        return new ST_Tensor_{ out };
    } catch (const std::exception& ex) {
        smile_torch_set_error(ex.what());
        return nullptr;
    }
}

extern "C" ST_Tensor smile_mla_decode_topk(ST_Tensor scores, ST_Tensor lens, long long topk_pools) {
    try {
        smile_torch_clear_error();
        std::string err;
        if (!check(scores->t, "scores", at::kFloat, 2, err) || !check(lens->t, "lens", at::kInt, 1, err)) {
            set_err("smile_mla_decode_topk: " + err);
            return nullptr;
        }
        const int64_t B = scores->t.size(0);
        if (lens->t.size(0) != B || topk_pools < 1) {
            set_err("smile_mla_decode_topk: inconsistent shapes");
            return nullptr;
        }
        const int width = (int) (topk_pools * 4 + 3);
        const int dev = scores->t.get_device();
        c10::cuda::CUDAGuard guard(dev);
        at::Tensor out = torch::empty({B, (int64_t) width}, scores->t.options().dtype(at::kInt));
        mla_topk_kernel<<<(unsigned) B, 1024, 0, at::cuda::getCurrentCUDAStream(dev)>>>(
                scores->t.data_ptr<float>(), scores->t.size(1), lens->t.data_ptr<int>(), (int) topk_pools, width,
                out.data_ptr<int>());
        cudaError_t st = cudaGetLastError();
        if (st != cudaSuccess) {
            set_err(std::string("smile_mla_decode_topk: CUDA error: ") + cudaGetErrorString(st));
            return nullptr;
        }
        return new ST_Tensor_{ out };
    } catch (const std::exception& ex) {
        smile_torch_set_error(ex.what());
        return nullptr;
    }
}

// ---- tensor-core latent attention for prefill chunks (bf16 latent cache): one block per row and 8 heads, the keys of the
// row gathered in tiles of 32 (cp.async, double buffered) and read ONCE (scores and values are the same latent rows), online
// softmax, m16n8k16 bf16 mma with the 8 heads padded to 16 rows, the probabilities split in hi/lo bf16 for ~fp32 accuracy.
namespace tc {
constexpr int kKeys = 32;
constexpr int kTileBytes = kKeys * kLat * 2;                 // 32 KB
constexpr int kSmemBytes = 8192 + 2 * kTileBytes + 1056 + 2 * 640 + 64 + 2 * kKeys * 4 + 64;

__device__ __forceinline__ uint32_t su32(const void* p) { return (uint32_t) __cvta_generic_to_shared(p); }
__device__ __forceinline__ void ldsm_x2(uint32_t& r0, uint32_t& r1, const void* p) {
    asm volatile("ldmatrix.sync.aligned.m8n8.x2.shared.b16 {%0,%1}, [%2];" : "=r"(r0), "=r"(r1) : "r"(su32(p)));
}
__device__ __forceinline__ void ldsm_x4t(uint32_t& r0, uint32_t& r1, uint32_t& r2, uint32_t& r3, const void* p) {
    asm volatile("ldmatrix.sync.aligned.m8n8.x4.trans.shared.b16 {%0,%1,%2,%3}, [%4];"
                 : "=r"(r0), "=r"(r1), "=r"(r2), "=r"(r3) : "r"(su32(p)));
}
__device__ __forceinline__ void cp16(void* dst, const void* src) {
    asm volatile("cp.async.cg.shared.global [%0], [%1], 16;" :: "r"(su32(dst)), "l"(src));
}
__device__ __forceinline__ void mma16816(float* c, const uint32_t* a, uint32_t b0, uint32_t b1) {
    asm volatile("mma.sync.aligned.m16n8k16.row.col.f32.bf16.bf16.f32 {%0,%1,%2,%3}, {%4,%5,%6,%7}, {%8,%9}, {%0,%1,%2,%3};"
                 : "+f"(c[0]), "+f"(c[1]), "+f"(c[2]), "+f"(c[3])
                 : "r"(a[0]), "r"(a[1]), "r"(a[2]), "r"(a[3]), "r"(b0), "r"(b1));
}
}  // namespace tc

__global__ void __launch_bounds__(128, 2) mla_attend_tc_kernel(
        const __nv_bfloat16* __restrict__ qabs, const long long* __restrict__ lat_ptrs, const int* __restrict__ idx,
        const int* __restrict__ lens, int top_pools, int width_all, int heads, float scale, float* __restrict__ ctx) {
    extern __shared__ __align__(16) unsigned char tsm[];
    unsigned char* Qs = tsm;                                              // [8 heads][1024 B], 16 B chunks XOR-swizzled by row
    unsigned char* Ks = tsm + 8192;                                       // 2 x [32 keys][1024 B], swizzled by key
    float* Ss = reinterpret_cast<float*>(tsm + 8192 + 2 * tc::kTileBytes);   // [8][33] scores of the tile
    __nv_bfloat16* Ph = reinterpret_cast<__nv_bfloat16*>(Ss + 8 * 33);       // [8][40] probabilities, hi part
    __nv_bfloat16* Pl = Ph + 8 * 40;                                         // lo part
    float* alpha_s = reinterpret_cast<float*>(Pl + 8 * 40);                  // [8] rescale of the running sum
    float* l_s = alpha_s + 8;                                                // [8] final sums
    int* tid_s = reinterpret_cast<int*>(l_s + 8);                            // [2][32] ids of the tile's keys
    const int b = blockIdx.x;
    const int h0 = blockIdx.y * 8;
    const int tid = threadIdx.x;
    const int lane = tid & 31;
    const int warp = tid >> 5;
    const int g = lane >> 2;
    const int t4 = lane & 3;
    const unsigned char* latb = reinterpret_cast<const unsigned char*>(lat_ptrs[b]);
    const int* rowIds = idx + (long long) b * width_all;
    int width = width_all;
    int nv = width_all;
    if (lens != nullptr) {
        nv = 4 * min((lens[b] + 1) >> 2, top_pools);
        width = nv + 3;
    }
    // queries of the 8 heads into shared memory
    for (int e = tid; e < 8 * 64; e += 128) {
        const int row = e >> 6, ch = e & 63;
        *reinterpret_cast<uint4*>(Qs + row * 1024 + ((ch ^ (row & 7)) << 4)) =
                *reinterpret_cast<const uint4*>(qabs + ((long long) b * heads + h0 + row) * kLat + ch * 8);
    }
    auto issue = [&](int t, int buf) {
        for (int e = tid; e < tc::kKeys * 64; e += 128) {
            const int key = e >> 6, ch = e & 63;
            const int j = t * tc::kKeys + key;
            int id = -1;
            if (j < width) id = j < nv ? rowIds[j] : rowIds[width_all - 3 + (j - nv)];
            if (ch == 0) tid_s[buf * tc::kKeys + key] = id;
            tc::cp16(Ks + buf * tc::kTileBytes + key * 1024 + ((ch ^ (key & 7)) << 4),
                     latb + (long long) (id < 0 ? 0 : id) * (kLat * 2) + ch * 16);
        }
        asm volatile("cp.async.commit_group;");
    };
    float O[16][4];
#pragma unroll
    for (int j = 0; j < 16; ++j) O[j][0] = O[j][1] = O[j][2] = O[j][3] = 0.f;
    const int head = tid >> 4;
    const int jj = tid & 15;
    float m = -INFINITY, l = 0.f;
    const int ntiles = (width + tc::kKeys - 1) / tc::kKeys;
    issue(0, 0);
    for (int t = 0; t < ntiles; ++t) {
        const int buf = t & 1;
        asm volatile("cp.async.wait_group 0;");
        __syncthreads();                                   // tile t (and Q, ids) visible; everyone is done with tile t - 1
        if (t + 1 < ntiles) issue(t + 1, buf ^ 1);         // loads of the next tile run during this tile's math
        const unsigned char* Kb = Ks + buf * tc::kTileBytes;
        // scores: warp w takes the 8 keys 8w..8w+7
        {
            float c[4] = {0.f, 0.f, 0.f, 0.f};
            const int key = warp * 8 + (lane & 7);
#pragma unroll 8
            for (int ks = 0; ks < 32; ++ks) {
                const int half = (lane >> 3) & 1;
                uint32_t a0, a2, b0, b1;
                tc::ldsm_x2(a0, a2, Qs + (lane & 7) * 1024 + (((2 * ks + half) ^ (lane & 7)) << 4));
                tc::ldsm_x2(b0, b1, Kb + key * 1024 + (((2 * ks + half) ^ (key & 7)) << 4));
                const uint32_t af[4] = {a0, 0u, a2, 0u};
                tc::mma16816(c, af, b0, b1);
            }
            const int k0 = warp * 8 + 2 * t4;
            const int id0 = tid_s[buf * tc::kKeys + k0], id1 = tid_s[buf * tc::kKeys + k0 + 1];
            Ss[g * 33 + k0] = id0 < 0 ? -INFINITY : c[0] * scale;
            Ss[g * 33 + k0 + 1] = id1 < 0 ? -INFINITY : c[1] * scale;
        }
        __syncthreads();
        // online softmax: 16 threads per head, two keys each
        {
            const float s0 = Ss[head * 33 + jj], s1 = Ss[head * 33 + jj + 16];
            float mx = fmaxf(s0, s1);
#pragma unroll
            for (int o = 8; o > 0; o >>= 1) mx = fmaxf(mx, __shfl_xor_sync(0xffffffffu, mx, o));
            const float mnew = fmaxf(m, mx);
            float alpha = 1.f, p0 = 0.f, p1 = 0.f;
            if (mnew != -INFINITY) {
                alpha = m == -INFINITY ? 0.f : __expf(m - mnew);
                p0 = __expf(s0 - mnew);
                p1 = __expf(s1 - mnew);
            }
            float sum = p0 + p1;
#pragma unroll
            for (int o = 8; o > 0; o >>= 1) sum += __shfl_xor_sync(0xffffffffu, sum, o);
            l = l * alpha + sum;
            m = mnew;
            const __nv_bfloat16 h0b = __float2bfloat16(p0), h1b = __float2bfloat16(p1);
            Ph[head * 40 + jj] = h0b;
            Ph[head * 40 + jj + 16] = h1b;
            Pl[head * 40 + jj] = __float2bfloat16(p0 - __bfloat162float(h0b));
            Pl[head * 40 + jj + 16] = __float2bfloat16(p1 - __bfloat162float(h1b));
            if (jj == 0) alpha_s[head] = alpha;
        }
        __syncthreads();
        // values: warp w owns the dims 128w..128w+127 (16 n8 tiles)
        {
            const float al = alpha_s[g];
#pragma unroll
            for (int j = 0; j < 16; ++j) { O[j][0] *= al; O[j][1] *= al; }
#pragma unroll
            for (int ks = 0; ks < 2; ++ks) {
                const uint32_t ah[4] = {*reinterpret_cast<const uint32_t*>(Ph + g * 40 + ks * 16 + 2 * t4), 0u,
                                        *reinterpret_cast<const uint32_t*>(Ph + g * 40 + ks * 16 + 8 + 2 * t4), 0u};
                const uint32_t alo[4] = {*reinterpret_cast<const uint32_t*>(Pl + g * 40 + ks * 16 + 2 * t4), 0u,
                                         *reinterpret_cast<const uint32_t*>(Pl + g * 40 + ks * 16 + 8 + 2 * t4), 0u};
                const int key = 16 * ks + ((lane >> 3) & 1) * 8 + (lane & 7);
#pragma unroll
                for (int jp = 0; jp < 8; ++jp) {
                    const int c = warp * 16 + 2 * jp + (lane >> 4);
                    uint32_t b0, b1, b2, b3;
                    tc::ldsm_x4t(b0, b1, b2, b3, Kb + key * 1024 + ((c ^ (key & 7)) << 4));
                    tc::mma16816(O[2 * jp], alo, b0, b1);
                    tc::mma16816(O[2 * jp], ah, b0, b1);
                    tc::mma16816(O[2 * jp + 1], alo, b2, b3);
                    tc::mma16816(O[2 * jp + 1], ah, b2, b3);
                }
            }
        }
    }
    if (jj == 0) l_s[head] = l;
    __syncthreads();
    const float inv = 1.f / l_s[g];
    float* dst = ctx + ((long long) b * heads + h0 + g) * kLat + warp * 128;
#pragma unroll
    for (int j = 0; j < 16; ++j) {
        *reinterpret_cast<float2*>(dst + j * 8 + 2 * t4) = make_float2(O[j][0] * inv, O[j][1] * inv);
    }
}

static ST_Tensor mla_attend_launch(ST_Tensor qabs, ST_Tensor lat_ptrs, ST_Tensor idx, ST_Tensor lens, long long top_pools,
                                   double scale, int fp8, const char* name) {
    try {
        smile_torch_clear_error();
        std::string err;
        if (!check(qabs->t, "qabs", at::kBFloat16, 3, err) || !check(lat_ptrs->t, "lat_ptrs", at::kLong, 1, err)
                || !check(idx->t, "idx", at::kInt, 2, err) || (lens != nullptr && !check(lens->t, "lens", at::kInt, 1, err))) {
            set_err(std::string(name) + ": " + err);
            return nullptr;
        }
        const int64_t B = qabs->t.size(0), H = qabs->t.size(1), W = idx->t.size(1);
        if (qabs->t.size(2) != kLat || H % 4 != 0 || idx->t.size(0) != B || lat_ptrs->t.size(0) != B || W > kAttnMaxW
                || (lens != nullptr && (lens->t.size(0) != B || top_pools * 4 + 3 != W))) {
            set_err(std::string(name) + ": inconsistent shapes");
            return nullptr;
        }
        const int dev = qabs->t.get_device();
        c10::cuda::CUDAGuard guard(dev);
        at::Tensor out = torch::empty({B, H, (int64_t) kLat}, qabs->t.options().dtype(at::kFloat));
        // enough blocks to fill the GPU: with few rows give each block fewer heads
        const int hpb = B * (H / 4) >= 96 ? 4 : (B * (H / 2) >= 96 ? 2 : 1);
        const size_t smem = (size_t) (hpb * kAttnMaxW + kAttnMaxW) * sizeof(float);
        // the opt-in to more than 48 KB of dynamic shared memory is a property of the function on each device
        static std::mutex configure_mutex;
        static bool configured[6][64] = {{false}};
        const int variant = (hpb == 4 ? 0 : (hpb == 2 ? 1 : 2)) + (fp8 ? 3 : 0);
        {
            std::lock_guard<std::mutex> lock(configure_mutex);
            if (dev >= 0 && dev < 64 && !configured[variant][dev]) {
                const int sz = (int) smem;
                cudaError_t attr = fp8
                        ? (hpb == 4 ? cudaFuncSetAttribute(mla_attend_kernel<4, true>, cudaFuncAttributeMaxDynamicSharedMemorySize, sz)
                           : hpb == 2 ? cudaFuncSetAttribute(mla_attend_kernel<2, true>, cudaFuncAttributeMaxDynamicSharedMemorySize, sz)
                           : cudaFuncSetAttribute(mla_attend_kernel<1, true>, cudaFuncAttributeMaxDynamicSharedMemorySize, sz))
                        : (hpb == 4 ? cudaFuncSetAttribute(mla_attend_kernel<4, false>, cudaFuncAttributeMaxDynamicSharedMemorySize, sz)
                           : hpb == 2 ? cudaFuncSetAttribute(mla_attend_kernel<2, false>, cudaFuncAttributeMaxDynamicSharedMemorySize, sz)
                           : cudaFuncSetAttribute(mla_attend_kernel<1, false>, cudaFuncAttributeMaxDynamicSharedMemorySize, sz));
                if (attr != cudaSuccess) {
                    set_err(std::string(name) + ": cannot raise the shared memory limit: " + cudaGetErrorString(attr));
                    return nullptr;
                }
                configured[variant][dev] = true;
            }
        }
        static const bool tc_on = std::getenv("GLM_ATTEND_TC") == nullptr || std::string(std::getenv("GLM_ATTEND_TC")) != "0";
        if (tc_on && !fp8 && H % 8 == 0 && B >= 32) {
            // prefill chunks: the tensor-core kernel (each latent row read once)
            static std::mutex tc_mutex;
            static bool tc_configured[64] = {false};
            {
                std::lock_guard<std::mutex> lock(tc_mutex);
                if (dev >= 0 && dev < 64 && !tc_configured[dev]) {
                    cudaError_t attr = cudaFuncSetAttribute(mla_attend_tc_kernel, cudaFuncAttributeMaxDynamicSharedMemorySize, tc::kSmemBytes);
                    if (attr != cudaSuccess) {
                        set_err(std::string(name) + ": cannot raise the shared memory limit: " + cudaGetErrorString(attr));
                        return nullptr;
                    }
                    tc_configured[dev] = true;
                }
            }
            dim3 tgrid((unsigned) B, (unsigned) (H / 8));
            mla_attend_tc_kernel<<<tgrid, 128, tc::kSmemBytes, at::cuda::getCurrentCUDAStream(dev)>>>(
                    reinterpret_cast<const __nv_bfloat16*>(qabs->t.data_ptr()),
                    reinterpret_cast<const long long*>(lat_ptrs->t.data_ptr<int64_t>()), idx->t.data_ptr<int>(),
                    lens == nullptr ? nullptr : lens->t.data_ptr<int>(), (int) top_pools, (int) W, (int) H, (float) scale,
                    out.data_ptr<float>());
            cudaError_t tst = cudaGetLastError();
            if (tst != cudaSuccess) {
                set_err(std::string(name) + ": CUDA error: " + cudaGetErrorString(tst));
                return nullptr;
            }
            return new ST_Tensor_{ out };
        }
        dim3 grid((unsigned) B, (unsigned) (H / hpb));
        const __nv_bfloat16* q = reinterpret_cast<const __nv_bfloat16*>(qabs->t.data_ptr());
        const long long* lp = reinterpret_cast<const long long*>(lat_ptrs->t.data_ptr<int64_t>());
        const int* ln = lens == nullptr ? nullptr : lens->t.data_ptr<int>();
        cudaStream_t stream = at::cuda::getCurrentCUDAStream(dev);
        const int* ip = idx->t.data_ptr<int>();
        float* op = out.data_ptr<float>();
#define ATTEND_LAUNCH(H, F) mla_attend_kernel<H, F><<<grid, 256, smem, stream>>>(q, lp, ip, ln, (int) top_pools, (int) W, (int) H_, (float) scale, op)
        const int H_ = (int) H;
        if (fp8) {
            if (hpb == 4) { ATTEND_LAUNCH(4, true); } else if (hpb == 2) { ATTEND_LAUNCH(2, true); } else { ATTEND_LAUNCH(1, true); }
        } else {
            if (hpb == 4) { ATTEND_LAUNCH(4, false); } else if (hpb == 2) { ATTEND_LAUNCH(2, false); } else { ATTEND_LAUNCH(1, false); }
        }
#undef ATTEND_LAUNCH
        cudaError_t st = cudaGetLastError();
        if (st != cudaSuccess) {
            set_err(std::string(name) + ": CUDA error: " + cudaGetErrorString(st));
            return nullptr;
        }
        return new ST_Tensor_{ out };
    } catch (const std::exception& ex) {
        smile_torch_set_error(ex.what());
        return nullptr;
    }
}

extern "C" ST_Tensor smile_mla_decode_attend(ST_Tensor qabs, ST_Tensor lat_ptrs, ST_Tensor idx, double scale) {
    return mla_attend_launch(qabs, lat_ptrs, idx, nullptr, 0, scale, 0, "smile_mla_decode_attend");
}

extern "C" ST_Tensor smile_mla_decode_attend2(ST_Tensor qabs, ST_Tensor lat_ptrs, ST_Tensor idx, ST_Tensor lens,
                                              long long top_pools, double scale, int fp8) {
    return mla_attend_launch(qabs, lat_ptrs, idx, lens, top_pools, scale, fp8, "smile_mla_decode_attend2");
}

extern "C" ST_Tensor smile_f32_gemm_x3(ST_Tensor a, ST_Tensor w) {
    try {
        smile_torch_clear_error();
        std::string err;
        const at::Tensor& A = a->t;
        const at::Tensor& Wt = w->t;
        if (!check(A, "a", at::kFloat, 2, err) || !check(Wt, "w", at::kFloat, 2, err)) {
            set_err("smile_f32_gemm_x3: " + err);
            return nullptr;
        }
        const int64_t M = A.size(0), K = A.size(1), N = Wt.size(0);
        if (Wt.size(1) != K || K % 8 != 0 || N % 8 != 0 || !A.is_contiguous() || !Wt.is_contiguous()) {
            set_err("smile_f32_gemm_x3: need contiguous [M,K] x [N,K] with K and N multiples of 8");
            return nullptr;
        }
        const int dev = A.get_device();
        c10::cuda::CUDAGuard guard(dev);
        cudaStream_t stream = at::cuda::getCurrentCUDAStream(dev);
        const int numMt = (int) ((M + 15) / 16);
        const int numNg = (int) ((N + kGemmNT * 8 - 1) / (kGemmNT * 8));
        // enough warps to fill the device: split K until there are ~4096 of them, chunks of at least 256
        int nsplit = (int) std::max<int64_t>(1, std::min<int64_t>(K / 256, (4096 + (int64_t) numMt * numNg - 1) / ((int64_t) numMt * numNg)));
        int kchunk = (int) ((((K + nsplit - 1) / nsplit) + 7) / 8 * 8);
        nsplit = (int) ((K + kchunk - 1) / kchunk);
        at::Tensor out = torch::empty({M, N}, A.options());
        at::Tensor part = nsplit == 1 ? out : torch::empty({nsplit, M, N}, A.options());
        const long tasks = (long) numMt * numNg * nsplit;
        gemm_x3_kernel<<<(unsigned) ((tasks + 3) / 4), 128, 0, stream>>>(
                A.data_ptr<float>(), Wt.data_ptr<float>(), part.data_ptr<float>(), (int) M, (int) N, (int) K, nsplit, kchunk, numNg);
        if (nsplit > 1) {
            gemm_x3_reduce_kernel<<<(unsigned) std::min<int64_t>(1024, (M * N + 255) / 256), 256, 0, stream>>>(
                    part.data_ptr<float>(), out.data_ptr<float>(), M * N, nsplit);
        }
        cudaError_t st = cudaGetLastError();
        if (st != cudaSuccess) {
            set_err(std::string("smile_f32_gemm_x3: CUDA error: ") + cudaGetErrorString(st));
            return nullptr;
        }
        return new ST_Tensor_{ out };
    } catch (const std::exception& ex) {
        smile_torch_set_error(ex.what());
        return nullptr;
    }
}

extern "C" ST_Tensor smile_f32_gemm_bf16x3(ST_Tensor a, ST_Tensor wsplit) {
    try {
        smile_torch_clear_error();
        std::string err;
        const at::Tensor& A = a->t;
        const at::Tensor& Ws = wsplit->t;
        if (!check(A, "a", at::kFloat, 2, err) || !check(Ws, "wsplit", at::kBFloat16, 4, err)) {
            set_err("smile_f32_gemm_bf16x3: " + err);
            return nullptr;
        }
        const int64_t M = A.size(0), K = A.size(1), N = Ws.size(0) * 8;
        if (Ws.size(1) * 16 != K || Ws.size(2) != 32 || Ws.size(3) != 8 || K % 16 != 0) {
            set_err("smile_f32_gemm_bf16x3: need a [M,K] fp32 and a [N/8, K/16, 32, 8] bf16 fragment-ordered split weight");
            return nullptr;
        }
        const int dev = A.get_device();
        c10::cuda::CUDAGuard guard(dev);
        cudaStream_t stream = at::cuda::getCurrentCUDAStream(dev);
        const int numMt = (int) ((M + 15) / 16);
        const int numNg = (int) ((N + kGemmNT * 8 - 1) / (kGemmNT * 8));
        int nsplit = (int) std::max<int64_t>(1, std::min<int64_t>(K / 256, (4096 + (int64_t) numMt * numNg - 1) / ((int64_t) numMt * numNg)));
        int kchunk = (int) ((((K + nsplit - 1) / nsplit) + 15) / 16 * 16);
        nsplit = (int) ((K + kchunk - 1) / kchunk);
        at::Tensor out = torch::empty({M, N}, A.options());
        at::Tensor part = nsplit == 1 ? out : torch::empty({nsplit, M, N}, A.options());
        const long tasks = (long) numMt * numNg * nsplit;
        gemm_bf16x3_kernel<<<(unsigned) ((tasks + 3) / 4), 128, 0, stream>>>(
                A.data_ptr<float>(), reinterpret_cast<const uint4*>(Ws.data_ptr()), part.data_ptr<float>(), (int) M, (int) N, (int) K, nsplit, kchunk, numNg);
        if (nsplit > 1) {
            gemm_x3_reduce_kernel<<<(unsigned) std::min<int64_t>(1024, (M * N + 255) / 256), 256, 0, stream>>>(
                    part.data_ptr<float>(), out.data_ptr<float>(), M * N, nsplit);
        }
        cudaError_t st = cudaGetLastError();
        if (st != cudaSuccess) {
            set_err(std::string("smile_f32_gemm_bf16x3: CUDA error: ") + cudaGetErrorString(st));
            return nullptr;
        }
        return new ST_Tensor_{ out };
    } catch (const std::exception& ex) {
        smile_torch_set_error(ex.what());
        return nullptr;
    }
}

extern "C" ST_Tensor smile_moe_sum_topk(ST_Tensor down, long long topk, ST_Tensor extra) {
    try {
        smile_torch_clear_error();
        std::string err;
        const at::Tensor& D = down->t;
        if (!check(D, "down", at::kBFloat16, 2, err) || (extra != nullptr && !check(extra->t, "extra", at::kFloat, 2, err))) {
            set_err("smile_moe_sum_topk: " + err);
            return nullptr;
        }
        const int64_t dim = D.size(1);
        if (topk <= 0 || D.size(0) % topk != 0 || dim % 4 != 0) {
            set_err("smile_moe_sum_topk: rows must be a multiple of topk and dim of 4");
            return nullptr;
        }
        const int64_t n = D.size(0) / topk;
        if (extra != nullptr && (extra->t.size(0) != n || extra->t.size(1) != dim)) {
            set_err("smile_moe_sum_topk: extra has the wrong shape");
            return nullptr;
        }
        const int dev = D.get_device();
        c10::cuda::CUDAGuard guard(dev);
        at::Tensor out = torch::empty({n, dim}, D.options().dtype(at::kFloat));
        dim3 grid((unsigned) n, (unsigned) std::max<int64_t>(1, std::min<int64_t>(4, dim / 1024)));
        moe_sum_topk_kernel<<<grid, 256, 0, at::cuda::getCurrentCUDAStream(dev)>>>(
                reinterpret_cast<const __nv_bfloat16*>(D.data_ptr()), extra == nullptr ? nullptr : extra->t.data_ptr<float>(),
                out.data_ptr<float>(), (int) topk, (int) dim);
        cudaError_t st = cudaGetLastError();
        if (st != cudaSuccess) {
            set_err(std::string("smile_moe_sum_topk: CUDA error: ") + cudaGetErrorString(st));
            return nullptr;
        }
        return new ST_Tensor_{ out };
    } catch (const std::exception& ex) {
        smile_torch_set_error(ex.what());
        return nullptr;
    }
}
