/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * DeepSeek-V4.1 fused kernels; see smile_deepseek_ops.h.
 */
#include "smile_deepseek_ops.h"

#include <ATen/ATen.h>
#include <ATen/cuda/CUDAContext.h>
#include <c10/cuda/CUDAGuard.h>
#include <torch/torch.h>

#include <cmath>
#include <cuda_bf16.h>
#include <cuda_fp16.h>
#include <cuda_fp8.h>
#include <string>

/* smile_torch.cpp */
extern "C" void smile_torch_set_error(const char *msg);
extern "C" void smile_torch_clear_error(void);

struct ST_Tensor_ {
    at::Tensor t;
};

namespace {

void set_err(const std::string& msg) { smile_torch_set_error(msg.c_str()); }

const at::Tensor& require_cuda(ST_Tensor h, const char* what) {
    if (!h) throw std::runtime_error(std::string(what) + ": null tensor");
    if (!h->t.is_cuda()) throw std::runtime_error(std::string(what) + ": not a CUDA tensor");
    return h->t;
}

// ---------------------------------------------------------------- hc_split_sinkhorn

constexpr int MAX_HC = 8;

/** The coefficients of one row from its (already rescaled by r) mixes; one thread, everything in registers. */
__device__ void hc_row(const float* __restrict__ mx, float r, const float* __restrict__ scale,
                       const float* __restrict__ base, float* __restrict__ pre, float* __restrict__ post,
                       float* __restrict__ comb, int hc, int iters, float eps) {
    const float s0 = scale[0], s1 = scale[1], s2 = scale[2];
    for (int i = 0; i < hc; i++) {
        pre[i] = 1.0f / (1.0f + expf(-((mx[i] * r) * s0 + base[i]))) + eps;
        post[i] = 2.0f * (1.0f / (1.0f + expf(-((mx[hc + i] * r) * s1 + base[hc + i]))));
    }
    float c[MAX_HC][MAX_HC];
    for (int i = 0; i < hc; i++) {
        float mxv = -INFINITY;
        for (int j = 0; j < hc; j++) {
            c[i][j] = (mx[2 * hc + i * hc + j] * r) * s2 + base[2 * hc + i * hc + j];
            mxv = fmaxf(mxv, c[i][j]);
        }
        float sum = 0.f;
        for (int j = 0; j < hc; j++) {
            c[i][j] = expf(c[i][j] - mxv);
            sum += c[i][j];
        }
        for (int j = 0; j < hc; j++) c[i][j] = c[i][j] / sum + eps;
    }
    for (int it = 0; it < iters; it++) {
        if (it > 0) {
            for (int i = 0; i < hc; i++) {
                float s = 0.f;
                for (int j = 0; j < hc; j++) s += c[i][j];
                s += eps;
                for (int j = 0; j < hc; j++) c[i][j] /= s;
            }
        }
        for (int j = 0; j < hc; j++) {
            float s = 0.f;
            for (int i = 0; i < hc; i++) s += c[i][j];
            s += eps;
            for (int i = 0; i < hc; i++) c[i][j] /= s;
        }
    }
    for (int i = 0; i < hc; i++)
        for (int j = 0; j < hc; j++) comb[i * hc + j] = c[i][j];
}

/** One thread per row (mixes already scaled). */
__global__ void hc_split_sinkhorn_kernel(const float* __restrict__ mixes, const float* __restrict__ scale,
                                         const float* __restrict__ base, float* __restrict__ pre,
                                         float* __restrict__ post, float* __restrict__ comb,
                                         int64_t rows, int hc, int iters, float eps) {
    const int64_t r = (int64_t) blockIdx.x * blockDim.x + threadIdx.x;
    if (r >= rows) return;
    hc_row(mixes + r * (2 + hc) * hc, 1.0f, scale, base, pre + r * hc, post + r * hc, comb + r * hc * hc, hc, iters, eps);
}

__device__ __forceinline__ float warp_sum(float v) {
    for (int o = 16; o > 0; o >>= 1) v += __shfl_xor_sync(0xffffffffu, v, o);
    return v;
}

/**
 * One block per row: r = rsqrt(mean(flat^2) + normEps), then the row's coefficients from raw * r, where raw is the
 * projection of flat (the unfused chain is mean, add, rsqrt, mul, then the split above).
 */
__global__ void hc_mixes_kernel(const float* __restrict__ flat, const float* __restrict__ raw,
                                const float* __restrict__ scale, const float* __restrict__ base,
                                float* __restrict__ pre, float* __restrict__ post, float* __restrict__ comb,
                                int64_t k, int hc, int iters, float eps, float norm_eps) {
    const int64_t row = blockIdx.x;
    const float* f = flat + row * k;
    float ss = 0.f;
    for (int64_t i = threadIdx.x; i < k; i += blockDim.x) ss += f[i] * f[i];
    __shared__ float partial[32];
    ss = warp_sum(ss);
    if ((threadIdx.x & 31) == 0) partial[threadIdx.x >> 5] = ss;
    __syncthreads();
    if (threadIdx.x < 32) {
        float v = threadIdx.x < (blockDim.x >> 5) ? partial[threadIdx.x] : 0.f;
        v = warp_sum(v);
        if (threadIdx.x == 0) partial[0] = v;
    }
    __syncthreads();
    if (threadIdx.x == 0) {
        const float r = rsqrtf(partial[0] / (float) k + norm_eps);
        hc_row(raw + row * (2 + hc) * hc, r, scale, base, pre + row * hc, post + row * hc, comb + row * hc * hc,
               hc, iters, eps);
    }
}

/**
 * hc_mixes_kernel for hc * hc <= 32: after the row reduction one warp computes the coefficients with one lane per matrix
 * element (row / column sums by shuffles, in the same order as the serial version), instead of one thread doing it all.
 */
__global__ void hc_mixes_warp_kernel(const float* __restrict__ flat, const float* __restrict__ raw,
                                     const float* __restrict__ scale, const float* __restrict__ base,
                                     float* __restrict__ pre, float* __restrict__ post, float* __restrict__ comb,
                                     int64_t k, int hc, int iters, float eps, float norm_eps) {
    const int64_t row = blockIdx.x;
    const float* f = flat + row * k;
    float ss = 0.f;
    for (int64_t i = threadIdx.x; i < k; i += blockDim.x) ss += f[i] * f[i];
    __shared__ float partial[32];
    ss = warp_sum(ss);
    if ((threadIdx.x & 31) == 0) partial[threadIdx.x >> 5] = ss;
    __syncthreads();
    if (threadIdx.x >= 32) return;
    float v = threadIdx.x < (blockDim.x >> 5) ? partial[threadIdx.x] : 0.f;
    v = warp_sum(v);
    const float r = rsqrtf(__shfl_sync(0xffffffffu, v, 0) / (float) k + norm_eps);
    const int lane = threadIdx.x;
    const int m = (2 + hc) * hc;
    const float* mx = raw + row * m;
    const float s0 = scale[0], s1 = scale[1], s2 = scale[2];
    if (lane < hc) {
        pre[row * hc + lane] = 1.0f / (1.0f + expf(-((mx[lane] * r) * s0 + base[lane]))) + eps;
        post[row * hc + lane] = 2.0f * (1.0f / (1.0f + expf(-((mx[hc + lane] * r) * s1 + base[hc + lane]))));
    }
    const bool active = lane < hc * hc;
    const int i = active ? lane / hc : 0, j = active ? lane % hc : 0;
    float c = active ? (mx[2 * hc + lane] * r) * s2 + base[2 * hc + lane] : 0.f;
    // softmax over the row (the hc lanes sharing i), summed in column order
    float mxv = -INFINITY;
    for (int jj = 0; jj < hc; jj++) mxv = fmaxf(mxv, __shfl_sync(0xffffffffu, c, i * hc + jj));
    c = expf(c - mxv);
    float sum = 0.f;
    for (int jj = 0; jj < hc; jj++) sum += __shfl_sync(0xffffffffu, c, i * hc + jj);
    c = c / sum + eps;
    for (int it = 0; it < iters; it++) {
        if (it > 0) {
            float s = 0.f;
            for (int jj = 0; jj < hc; jj++) s += __shfl_sync(0xffffffffu, c, i * hc + jj);
            c /= (s + eps);
        }
        float s = 0.f;
        for (int ii = 0; ii < hc; ii++) s += __shfl_sync(0xffffffffu, c, ii * hc + j);
        c /= (s + eps);
    }
    if (active) comb[row * hc * hc + lane] = c;
}

// ---------------------------------------------------------------- RMSNorm, hyper-connection combine

/** y = w * (x * rsqrt(mean(x^2) + eps)) in float32, rounded to T once; one block per row. */
template <typename T, typename W>
__global__ void rms_norm_kernel(const T* __restrict__ x, const W* __restrict__ w, T* __restrict__ y, int d, float eps) {
    const T* xr = x + (int64_t) blockIdx.x * d;
    T* yr = y + (int64_t) blockIdx.x * d;
    float ss = 0.f;
    for (int i = threadIdx.x; i < d; i += blockDim.x) {
        float v = static_cast<float>(xr[i]);
        ss += v * v;
    }
    __shared__ float partial[32];
    ss = warp_sum(ss);
    if ((threadIdx.x & 31) == 0) partial[threadIdx.x >> 5] = ss;
    __syncthreads();
    if (threadIdx.x < 32) {
        float v = threadIdx.x < (blockDim.x >> 5) ? partial[threadIdx.x] : 0.f;
        v = warp_sum(v);
        if (threadIdx.x == 0) partial[0] = v;
    }
    __syncthreads();
    const float r = rsqrtf(partial[0] / (float) d + eps);
    for (int i = threadIdx.x; i < d; i += blockDim.x) {
        yr[i] = static_cast<T>(static_cast<float>(w[i]) * (static_cast<float>(xr[i]) * r));
    }
}

/** out[row, d] = sum_h pre[row, h] * x[row, h, d]. */
template <typename T>
__global__ void hc_pre_kernel(const T* __restrict__ x, const float* __restrict__ pre, T* __restrict__ out,
                              int64_t rows, int hc, int dim) {
    const int64_t idx = (int64_t) blockIdx.x * blockDim.x + threadIdx.x;
    if (idx >= rows * dim) return;
    const int64_t row = idx / dim;
    const int d = (int) (idx % dim);
    float acc = 0.f;
    for (int h = 0; h < hc; h++) acc += pre[row * hc + h] * static_cast<float>(x[(row * hc + h) * dim + d]);
    out[idx] = static_cast<T>(acc);
}

/** out[row, j, d] = post[row, j] * x[row, d] + sum_i comb[row, i, j] * residual[row, i, d]. */
template <typename T>
__global__ void hc_post_kernel(const T* __restrict__ x, const T* __restrict__ res, const float* __restrict__ post,
                               const float* __restrict__ comb, T* __restrict__ out, int64_t rows, int hc, int dim) {
    const int64_t idx = (int64_t) blockIdx.x * blockDim.x + threadIdx.x;
    if (idx >= rows * hc * dim) return;
    const int d = (int) (idx % dim);
    const int j = (int) ((idx / dim) % hc);
    const int64_t row = idx / ((int64_t) hc * dim);
    float acc = 0.f;
    for (int i = 0; i < hc; i++) acc += comb[(row * hc + i) * hc + j] * static_cast<float>(res[(row * hc + i) * dim + d]);
    out[idx] = static_cast<T>(post[row * hc + j] * static_cast<float>(x[row * dim + d]) + acc);
}

// ---------------------------------------------------------------- fp8 fake quantization

/** One warp per 32-element block: amax, power-of-two scale, e4m3 round trip. */
template <typename T>
__global__ void fake_quant_fp8_kernel(const T* __restrict__ x, T* __restrict__ y, int64_t blocks) {
    const int64_t b = ((int64_t) blockIdx.x * blockDim.x + threadIdx.x) >> 5;
    if (b >= blocks) return;
    const int lane = threadIdx.x & 31;
    const float v = static_cast<float>(x[b * 32 + lane]);
    float amax = fabsf(v);
    for (int o = 16; o > 0; o >>= 1) amax = fmaxf(amax, __shfl_xor_sync(0xffffffffu, amax, o));
    amax = fmaxf(amax, 1e-4f);
    const float t = amax * (float) (1.0 / 448.0);
    int e;
    const float m = frexpf(t, &e);                 // t = m * 2^e, m in [0.5, 1)
    const int ce = (m == 0.5f) ? e - 1 : e;         // ceil(log2 t)
    const float s = ldexpf(1.0f, ce);
    const float q = fminf(fmaxf(v / s, -448.0f), 448.0f);
    const __nv_fp8_e4m3 f(q);
    y[b * 32 + lane] = static_cast<T>(static_cast<float>(f) * s);
}


// ---------------------------------------------------------------- sparse attention (one shared KV head)

constexpr int ATT_D = 512;
constexpr int ATT_WARPS = 16;
constexpr int ATT_THREADS = ATT_WARPS * 32;

__device__ __forceinline__ void load8(const __nv_bfloat16* p, float* f) {
    const uint4 v = *reinterpret_cast<const uint4*>(p);
    const __nv_bfloat162* h = reinterpret_cast<const __nv_bfloat162*>(&v);
    for (int i = 0; i < 4; i++) {
        const float2 x = __bfloat1622float2(h[i]);
        f[2 * i] = x.x;
        f[2 * i + 1] = x.y;
    }
}

/**
 * One block per (query row, head): scores of the head's query against the gathered rows, softmax with the attention
 * sink, weighted sum. Mirrors the composed op chain (gather, einsum, mask, max, exp, einsum, divide) in float32. The
 * keys are split over the warps for both the scores and the weighted sum, which is then reduced through shared memory.
 */
__global__ void sparse_attention_kernel(const __nv_bfloat16* __restrict__ q, const __nv_bfloat16* __restrict__ kv,
                                        const float* __restrict__ sink, const int* __restrict__ idxs,
                                        __nv_bfloat16* __restrict__ out, int heads, int topk, float scale,
                                        const int64_t* __restrict__ wptrs, const int64_t* __restrict__ cptrs, int window) {
    extern __shared__ float sc[];                       // topk scores, then probabilities; then the [warps, D] partial sums
    __shared__ float red[ATT_WARPS];
    const int row = blockIdx.x / heads;
    const int h = blockIdx.x % heads;
    const int lane = threadIdx.x & 31, warp = threadIdx.x >> 5;
    const int* ix = idxs + (int64_t) row * topk;
    const __nv_bfloat16* qh = q + ((int64_t) row * heads + h) * ATT_D;
    // per-sequence caches: index < window reads that sequence's window ring, the rest its compressed cache
    const __nv_bfloat16* wbase = wptrs ? reinterpret_cast<const __nv_bfloat16*>(wptrs[row]) : nullptr;
    const __nv_bfloat16* cbase = cptrs ? reinterpret_cast<const __nv_bfloat16*>(cptrs[row]) : nullptr;
    auto key_row = [&](int idx) -> const __nv_bfloat16* {
        return wbase ? (idx < window ? wbase + (int64_t) idx * ATT_D : cbase + (int64_t) (idx - window) * ATT_D)
                     : kv + (int64_t) idx * ATT_D;
    };

    float qa[8], qb[8];
    load8(qh + lane * 8, qa);
    load8(qh + 256 + lane * 8, qb);
    for (int t = warp; t < topk; t += ATT_WARPS) {
        const int idx = ix[t];
        float score = -INFINITY;
        if (idx >= 0) {
            const __nv_bfloat16* kr = key_row(idx);
            float ka[8], kb[8];
            load8(kr + lane * 8, ka);
            load8(kr + 256 + lane * 8, kb);
            float acc = 0.f;
            for (int i = 0; i < 8; i++) acc += qa[i] * ka[i] + qb[i] * kb[i];
            acc = warp_sum(acc);
            score = acc * scale;
        }
        if (lane == 0) sc[t] = score;
    }
    __syncthreads();

    float mx = -INFINITY;
    for (int t = threadIdx.x; t < topk; t += ATT_THREADS) mx = fmaxf(mx, sc[t]);
    for (int o = 16; o > 0; o >>= 1) mx = fmaxf(mx, __shfl_xor_sync(0xffffffffu, mx, o));
    if (lane == 0) red[warp] = mx;
    __syncthreads();
    mx = red[0];
    for (int w = 1; w < ATT_WARPS; w++) mx = fmaxf(mx, red[w]);
    mx = fmaxf(mx, -1e30f);
    __syncthreads();

    float sum = 0.f;
    for (int t = threadIdx.x; t < topk; t += ATT_THREADS) {
        const float pv = expf(sc[t] - mx);
        sc[t] = pv;
        sum += pv;
    }
    sum = warp_sum(sum);
    if (lane == 0) red[warp] = sum;
    __syncthreads();
    float total = 0.f;
    for (int w = 0; w < ATT_WARPS; w++) total += red[w];
    const float denom = total + expf(sink[h] - mx);

    // weighted sum: each warp accumulates its keys over all D dimensions (16 per lane)
    float acc[16];
    for (int i = 0; i < 16; i++) acc[i] = 0.f;
    for (int t = warp; t < topk; t += ATT_WARPS) {
        const int idx = ix[t];
        if (idx < 0) continue;                           // p == 0 for masked slots
        const float pv = sc[t];
        const __nv_bfloat16* kr = key_row(idx);
        float ka[8], kb[8];
        load8(kr + lane * 8, ka);
        load8(kr + 256 + lane * 8, kb);
        for (int i = 0; i < 8; i++) {
            acc[i] += pv * ka[i];
            acc[8 + i] += pv * kb[i];
        }
    }
    float* part = sc + topk;                              // [ATT_WARPS][ATT_D]
    for (int i = 0; i < 8; i++) {
        part[warp * ATT_D + lane * 8 + i] = acc[i];
        part[warp * ATT_D + 256 + lane * 8 + i] = acc[8 + i];
    }
    __syncthreads();
    for (int d = threadIdx.x; d < ATT_D; d += ATT_THREADS) {
        float v = 0.f;
        for (int w = 0; w < ATT_WARPS; w++) v += part[w * ATT_D + d];
        out[((int64_t) row * heads + h) * ATT_D + d] = __float2bfloat16_rn(v / denom);
    }
}

// ---------------------------------------------------------------- MoE: router, SwiGLU, combine

constexpr int ROUTE_THREADS = 128;
constexpr int ROUTE_MAX_E = 1024;

/** torch softplus (beta 1, threshold 20) then sqrt. */
__device__ __forceinline__ float route_score(float l) {
    const float sp = l > 20.f ? l : log1pf(expf(l));
    return sqrtf(sp);
}

/**
 * One block per token: scores = sqrt(softplus(logits)); the top-k of scores + bias pick the experts (descending), the
 * weights are the unbiased scores, renormalised (k > 1) and scaled.
 */
__global__ void route_kernel(const float* __restrict__ logits, const float* __restrict__ bias, int64_t* __restrict__ idx,
                             float* __restrict__ wts, int experts, int topk, float route_scale) {
    __shared__ float score[ROUTE_MAX_E];
    __shared__ float biased[ROUTE_MAX_E];
    __shared__ float rv[ROUTE_THREADS];
    __shared__ int ri[ROUTE_THREADS];
    __shared__ int chosen[32];
    const int64_t tok = blockIdx.x;
    for (int e = threadIdx.x; e < experts; e += ROUTE_THREADS) {
        const float sc = route_score(logits[tok * experts + e]);
        score[e] = sc;
        biased[e] = sc + bias[e];
    }
    __syncthreads();
    for (int k = 0; k < topk; k++) {
        float best = -INFINITY;
        int bi = 0x7fffffff;
        for (int e = threadIdx.x; e < experts; e += ROUTE_THREADS) {
            const float v = biased[e];
            if (v > best || (v == best && e < bi)) { best = v; bi = e; }
        }
        rv[threadIdx.x] = best;
        ri[threadIdx.x] = bi;
        __syncthreads();
        for (int o = ROUTE_THREADS / 2; o > 0; o >>= 1) {
            if (threadIdx.x < o) {
                const float v2 = rv[threadIdx.x + o];
                const int i2 = ri[threadIdx.x + o];
                if (v2 > rv[threadIdx.x] || (v2 == rv[threadIdx.x] && i2 < ri[threadIdx.x])) {
                    rv[threadIdx.x] = v2;
                    ri[threadIdx.x] = i2;
                }
            }
            __syncthreads();
        }
        if (threadIdx.x == 0) {
            chosen[k] = ri[0];
            biased[ri[0]] = -INFINITY;
        }
        __syncthreads();
    }
    if (threadIdx.x == 0) {
        float sum = 0.f;
        for (int k = 0; k < topk; k++) sum += score[chosen[k]];
        for (int k = 0; k < topk; k++) {
            float w = score[chosen[k]];
            if (topk > 1) w = w / (sum + 1e-20f);
            wts[tok * topk + k] = w * route_scale;
            idx[tok * topk + k] = chosen[k];
        }
    }
}

/**
 * h = bf16(silu(min(gate, limit)) * clamp(up, +-limit) [* w]); with quant, h = fp8 fake quantization of h per 32 columns.
 * One warp per 32 columns of a row; gate_up is [rows, 2 * inter] bf16.
 */
__global__ void swiglu_kernel(const __nv_bfloat16* __restrict__ gu, const float* __restrict__ w,
                              __nv_bfloat16* __restrict__ out, int64_t rows, int inter, float limit, int quant) {
    const int64_t gw = ((int64_t) blockIdx.x * blockDim.x + threadIdx.x) >> 5;
    const int lane = threadIdx.x & 31;
    const int blocks_per_row = inter / 32;
    if (gw >= rows * blocks_per_row) return;
    const int64_t row = gw / blocks_per_row;
    const int col = (int) (gw % blocks_per_row) * 32 + lane;
    float g = static_cast<float>(gu[row * 2 * inter + col]);
    float u = static_cast<float>(gu[row * 2 * inter + inter + col]);
    if (limit > 0.f) {
        u = fminf(fmaxf(u, -limit), limit);
        g = fminf(g, limit);
    }
    float h = (g / (1.0f + expf(-g))) * u;
    if (w != nullptr) h = w[row] * h;
    float v = static_cast<float>(__float2bfloat16_rn(h));
    if (quant) {
        float amax = fabsf(v);
        for (int o = 16; o > 0; o >>= 1) amax = fmaxf(amax, __shfl_xor_sync(0xffffffffu, amax, o));
        amax = fmaxf(amax, 1e-4f);
        const float t = amax * (float) (1.0 / 448.0);
        int e;
        const float m = frexpf(t, &e);
        const float s = ldexpf(1.0f, (m == 0.5f) ? e - 1 : e);
        const float q = fminf(fmaxf(v / s, -448.0f), 448.0f);
        const __nv_fp8_e4m3 f(q);
        v = static_cast<float>(f) * s;
    }
    out[row * inter + col] = __float2bfloat16_rn(v);
}

/** out[t, d] = sum_k float(x[t * topk + k, d]). */
__global__ void sum_topk_kernel(const __nv_bfloat16* __restrict__ x, float* __restrict__ out, int64_t tokens, int topk, int dim) {
    const int64_t idx = (int64_t) blockIdx.x * blockDim.x + threadIdx.x;
    if (idx >= tokens * dim) return;
    const int64_t t = idx / dim;
    const int d = (int) (idx % dim);
    float acc = 0.f;
    for (int k = 0; k < topk; k++) acc += static_cast<float>(x[(t * topk + k) * dim + d]);
    out[idx] = acc;
}


// ---------------------------------------------------------------- rope tail, fp4 fake quantization

/** Rotates the last rd dims (as complex pairs) of x [s, H, d] by freqs [s, rd/2]; the rest is copied. One thread per element. */
template <typename T>
__global__ void rope_tail_kernel(const T* __restrict__ x, const float2* __restrict__ freqs, T* __restrict__ out,
                                 int64_t total, int64_t per_row, int d, int rd, int inverse) {
    const int64_t e = (int64_t) blockIdx.x * blockDim.x + threadIdx.x;
    if (e >= total) return;
    const int col = (int) (e % d);
    const int rot0 = d - rd;
    if (col < rot0) {
        out[e] = x[e];
        return;
    }
    const int j = col - rot0;
    const int64_t row = e / per_row;                       // position index along the sequence axis
    const int64_t base = e - j;                            // start of the rotated tail
    const float a = static_cast<float>(x[base + (j & ~1)]);
    const float b = static_cast<float>(x[base + (j | 1)]);
    const float2 f = freqs[row * (rd / 2) + (j >> 1)];
    const float fi = inverse ? -f.y : f.y;
    const float re = a * f.x - b * fi;
    const float im = a * fi + b * f.x;
    out[e] = static_cast<T>((j & 1) ? im : re);
}

__device__ __forceinline__ float round_fp4(float v) {
    const float a = fabsf(v);
    float q = a > 0.25f ? 0.5f : 0.0f;
    q = a >= 0.75f ? 1.0f : q;
    q = a > 1.25f ? 1.5f : q;
    q = a >= 1.75f ? 2.0f : q;
    q = a > 2.5f ? 3.0f : q;
    q = a >= 3.5f ? 4.0f : q;
    q = a > 5.0f ? 6.0f : q;
    return copysignf(q, v);
}

/** Block-wise fp4 (e2m1) fake quantization; a warp covers 32 elements = 32 / block scale groups. */
template <typename T>
__global__ void fake_quant_fp4_kernel(const T* __restrict__ x, T* __restrict__ y, int64_t n, int block, int e4m3_scale) {
    const int64_t i = (int64_t) blockIdx.x * blockDim.x + threadIdx.x;
    const float v = i < n ? static_cast<float>(x[i]) : 0.f;
    float amax = fabsf(v);
    for (int o = block / 2; o > 0; o >>= 1) amax = fmaxf(amax, __shfl_xor_sync(0xffffffffu, amax, o));
    if (i >= n) return;
    float s;
    if (e4m3_scale) {
        amax = fmaxf(amax, 6.0f * (float) (1.0 / 512.0));
        s = static_cast<float>(__nv_fp8_e4m3(amax / 6.0f));
    } else {
        amax = fmaxf(amax, 6.0f * 1.1754943508222875e-38f);   // 6 * 2^-126
        const float t = amax * (float) (1.0 / 6.0);
        int e;
        const float m = frexpf(t, &e);
        s = ldexpf(1.0f, (m == 0.5f) ? e - 1 : e);
    }
    const float r = round_fp4(fminf(fmaxf(v / s, -6.0f), 6.0f));
    y[i] = static_cast<T>(r * s);
}


// ---------------------------------------------------------------- indexer score and finalize

/**
 * score[s, t] = bf16( sum_h bf16( bf16(relu(bf16(q[s,h] . k[t]))) * w[s,h] ) ): the einsum / relu / multiply / sum chain
 * in bf16 with float32 accumulation inside each op, as the composed ops round after every step. One block of 32 lanes
 * per (position t, query s); lane = head.
 */
__global__ void index_score_kernel(const __nv_bfloat16* __restrict__ q, const __nv_bfloat16* __restrict__ k,
                                   const __nv_bfloat16* __restrict__ w, __nv_bfloat16* __restrict__ out,
                                   int heads, int hd, int64_t T) {
    const int64_t t = blockIdx.x;
    const int64_t s = blockIdx.y;
    const int h = threadIdx.x;
    float prod = 0.f;
    if (h < heads) {
        const __nv_bfloat16* qh = q + (s * heads + h) * hd;
        const __nv_bfloat16* kt = k + t * hd;
        float acc = 0.f;
        for (int d = 0; d < hd; d++) acc += static_cast<float>(qh[d]) * static_cast<float>(kt[d]);
        float r = static_cast<float>(__float2bfloat16_rn(acc));
        r = fmaxf(r, 0.f);
        prod = static_cast<float>(__float2bfloat16_rn(r * static_cast<float>(w[s * heads + h])));
    }
    prod = warp_sum(prod);
    if (h == 0) out[s * T + t] = __float2bfloat16_rn(prod);
}

/** out = idx < lens ? idx + offset : -1 (int32); lens has one entry per row or a single one. */
__global__ void index_finalize_kernel(const int64_t* __restrict__ idx, const int64_t* __restrict__ lens, int lens_n,
                                      int32_t* __restrict__ out, int64_t n, int k, int64_t offset) {
    const int64_t i = (int64_t) blockIdx.x * blockDim.x + threadIdx.x;
    if (i >= n) return;
    const int64_t row = i / k;
    const int64_t len = lens[lens_n == 1 ? 0 : row];
    out[i] = idx[i] < len ? (int32_t) (idx[i] + offset) : -1;
}


// ---------------------------------------------------------------- batched decode over several sequences

/** Writes row i of src into sequence i's window ring slot pos[i] % window. */
__global__ void window_write_kernel(const __nv_bfloat16* __restrict__ src, const int64_t* __restrict__ ptrs,
                                    const int64_t* __restrict__ pos, int window, int d) {
    const int i = blockIdx.x;
    __nv_bfloat16* dst = reinterpret_cast<__nv_bfloat16*>(ptrs[i]) + (pos[i] % window) * (int64_t) d;
    for (int c = threadIdx.x; c < d; c += blockDim.x) dst[c] = src[(int64_t) i * d + c];
}

/** Writes row i of src to row (pos[i] / div) of sequence i's cache when emit[i] (or always when emit is null). */
__global__ void cache_scatter_kernel(const __nv_bfloat16* __restrict__ src, const int64_t* __restrict__ ptrs,
                                     const int64_t* __restrict__ pos, int div, const uint8_t* __restrict__ emit, int d,
                                     int gate) {
    const int i = blockIdx.x;
    if (emit && !emit[i]) return;
    if (gate && (pos[i] + 1) % div != 0) return;   // a compressed group completes only every div-th token
    __nv_bfloat16* dst = reinterpret_cast<__nv_bfloat16*>(ptrs[i]) + (pos[i] / div) * (int64_t) d;
    for (int c = threadIdx.x; c < d; c += blockDim.x) dst[c] = src[(int64_t) i * d + c];
}

/**
 * Compressor decode step for several sequences: stores this token's projection and gate score in slot pos % ratio of
 * each sequence's partial group and, when the group completes, pools it: sum_j kv[j] * softmax_j(score[j]).
 * One thread per (sequence, channel). pooled is only meaningful for rows with (pos + 1) % ratio == 0.
 */
__global__ void compress_state_kernel(const float* __restrict__ kv, const float* __restrict__ score,
                                      const int64_t* __restrict__ kvptrs, const int64_t* __restrict__ scptrs,
                                      const int64_t* __restrict__ pos, int ratio, int hd, float* __restrict__ pooled) {
    const int i = blockIdx.y;
    const int c = blockIdx.x * blockDim.x + threadIdx.x;
    if (c >= hd) return;
    float* ks = reinterpret_cast<float*>(kvptrs[i]);
    float* ss = reinterpret_cast<float*>(scptrs[i]);
    const int slot = (int) (pos[i] % ratio);
    ks[(int64_t) slot * hd + c] = kv[(int64_t) i * hd + c];
    ss[(int64_t) slot * hd + c] = score[(int64_t) i * hd + c];
    if ((pos[i] + 1) % ratio != 0) return;
    float mx = -INFINITY;
    for (int j = 0; j < ratio; j++) mx = fmaxf(mx, ss[(int64_t) j * hd + c]);
    float sum = 0.f;
    for (int j = 0; j < ratio; j++) sum += expf(ss[(int64_t) j * hd + c] - mx);
    float acc = 0.f;
    for (int j = 0; j < ratio; j++) acc += ks[(int64_t) j * hd + c] * (expf(ss[(int64_t) j * hd + c] - mx) / sum);
    pooled[(int64_t) i * hd + c] = acc;
}

/** index_score_kernel with a key cache per query row and padding (-inf) beyond that row's length. */
__global__ void index_score_ptrs_kernel(const __nv_bfloat16* __restrict__ q, const int64_t* __restrict__ kptrs,
                                        const int64_t* __restrict__ lens, const __nv_bfloat16* __restrict__ w,
                                        __nv_bfloat16* __restrict__ out, int heads, int hd, int64_t Tmax) {
    const int64_t t = blockIdx.x;
    const int64_t s = blockIdx.y;
    const int h = threadIdx.x;
    if (t >= lens[s]) {
        if (h == 0) out[s * Tmax + t] = __float2bfloat16_rn(-INFINITY);
        return;
    }
    const __nv_bfloat16* kt = reinterpret_cast<const __nv_bfloat16*>(kptrs[s]) + t * hd;
    float prod = 0.f;
    if (h < heads) {
        const __nv_bfloat16* qh = q + (s * heads + h) * hd;
        float acc = 0.f;
        for (int d = 0; d < hd; d++) acc += static_cast<float>(qh[d]) * static_cast<float>(kt[d]);
        float r = static_cast<float>(__float2bfloat16_rn(acc));
        r = fmaxf(r, 0.f);
        prod = static_cast<float>(__float2bfloat16_rn(r * static_cast<float>(w[s * heads + h])));
    }
    prod = warp_sum(prod);
    if (h == 0) out[s * Tmax + t] = __float2bfloat16_rn(prod);
}


/** out[i, j] = slot j of the sliding window (j < window) a decode token at pos[i] attends to, -1 for slots still empty. */
__global__ void window_topk_kernel(const int64_t* __restrict__ pos, int ring, int span, int32_t* __restrict__ out) {
    const int i = blockIdx.x;
    const int64_t p = pos[i];
    // the last `span` positions p-span+1 .. p, as slots of the ring (-1 before the start of the sequence)
    for (int j = threadIdx.x; j < span; j += blockDim.x) {
        const int64_t q = p - (span - 1) + j;
        out[(int64_t) i * span + j] = q < 0 ? -1 : (int32_t) (q % ring);
    }
}

/**
 * Compressor pooling for the rows of a verify step, each row independent: a row that completes a group (pos + 1 divisible by
 * ratio) pools the group's ratio positions, taking the ones before the step from its sequence's saved partial group and the
 * ones inside the step from the other rows' projections (row r - d holds position pos - d while d <= jidx[r]). The saved
 * state is not touched here; compress_commit_kernel writes it once the accepted rows are known.
 */
__global__ void compress_rows_kernel(const float* __restrict__ kv, const float* __restrict__ score,
                                     const int64_t* __restrict__ kvptrs, const int64_t* __restrict__ scptrs,
                                     const int64_t* __restrict__ pos, const int64_t* __restrict__ jidx, int ratio, int hd,
                                     float* __restrict__ pooled) {
    const int r = blockIdx.y;
    const int c = blockIdx.x * blockDim.x + threadIdx.x;
    if (c >= hd) return;
    if ((pos[r] + 1) % ratio != 0) return;
    const float* ks = reinterpret_cast<const float*>(kvptrs[r]);
    const float* ss = reinterpret_cast<const float*>(scptrs[r]);
    float k[8], s[8];
    for (int t = 0; t < ratio; t++) {
        const int d = ratio - 1 - t;                 // this slot's position is pos - d
        if (d <= jidx[r]) {
            k[t] = kv[(int64_t) (r - d) * hd + c];
            s[t] = score[(int64_t) (r - d) * hd + c];
        } else {
            k[t] = ks[(int64_t) t * hd + c];
            s[t] = ss[(int64_t) t * hd + c];
        }
    }
    float mx = -INFINITY;
    for (int t = 0; t < ratio; t++) mx = fmaxf(mx, s[t]);
    float sum = 0.f;
    for (int t = 0; t < ratio; t++) sum += expf(s[t] - mx);
    float acc = 0.f;
    for (int t = 0; t < ratio; t++) acc += k[t] * (expf(s[t] - mx) / sum);
    pooled[(int64_t) r * hd + c] = acc;
}

/**
 * After a verify step: sequence i processed rows 0 .. last[i] of its block (its first row is base[i], at position p0[i]);
 * the positions of that range that belong to the unfinished group go into its saved partial group.
 */
__global__ void compress_commit_kernel(const float* __restrict__ kv, const float* __restrict__ score,
                                       const int64_t* __restrict__ kvptrs, const int64_t* __restrict__ scptrs,
                                       const int64_t* __restrict__ base, const int64_t* __restrict__ p0,
                                       const int64_t* __restrict__ last, int ratio, int hd) {
    const int i = blockIdx.y;
    const int c = blockIdx.x * blockDim.x + threadIdx.x;
    if (c >= hd) return;
    const int64_t b = base[i];
    float* ks = reinterpret_cast<float*>(kvptrs[b]);
    float* ss = reinterpret_cast<float*>(scptrs[b]);
    const int64_t end = p0[i] + last[i] + 1;          // one past the last processed position
    const int64_t gs = (end / ratio) * ratio;         // start of the unfinished group
    for (int64_t q = gs > p0[i] ? gs : p0[i]; q < end; q++) {
        const int64_t row = b + (q - p0[i]);
        ks[(q % ratio) * hd + c] = kv[row * hd + c];
        ss[(q % ratio) * hd + c] = score[row * hd + c];
    }
}

__global__ void iota_kernel(int64_t* __restrict__ out, int64_t n) {
    const int64_t i = (int64_t) blockIdx.x * blockDim.x + threadIdx.x;
    if (i < n) out[i] = i;
}

}  // namespace

extern "C" int smile_deepseek_ops_available(void) { return 1; }

extern "C" int smile_ds_hc_split_sinkhorn(ST_Tensor mixes, ST_Tensor scale, ST_Tensor base, int hc, int iters,
                                          double eps, ST_Tensor *out) {
    try {
        smile_torch_clear_error();
        const at::Tensor& mx = require_cuda(mixes, "smile_ds_hc_split_sinkhorn: mixes");
        const at::Tensor& sc = require_cuda(scale, "smile_ds_hc_split_sinkhorn: scale");
        const at::Tensor& bs = require_cuda(base, "smile_ds_hc_split_sinkhorn: base");
        const int m = (2 + hc) * hc;
        if (hc < 1 || hc > MAX_HC || iters < 1 || mx.scalar_type() != at::kFloat || sc.scalar_type() != at::kFloat
                || bs.scalar_type() != at::kFloat || mx.size(-1) != m || sc.numel() != 3 || bs.numel() != m
                || !mx.is_contiguous() || !sc.is_contiguous() || !bs.is_contiguous()) {
            throw std::runtime_error("smile_ds_hc_split_sinkhorn: unsupported arguments");
        }
        c10::cuda::CUDAGuard guard(mx.get_device());
        const int64_t rows = mx.numel() / m;
        std::vector<int64_t> lead(mx.sizes().begin(), mx.sizes().end() - 1);
        auto shape = [&](std::initializer_list<int64_t> tail) {
            std::vector<int64_t> s = lead;
            s.insert(s.end(), tail.begin(), tail.end());
            return s;
        };
        at::Tensor pre = at::empty(shape({hc}), mx.options());
        at::Tensor post = at::empty(shape({hc}), mx.options());
        at::Tensor comb = at::empty(shape({hc, hc}), mx.options());
        if (rows > 0) {
            hc_split_sinkhorn_kernel<<<(unsigned) ((rows + 63) / 64), 64, 0, at::cuda::getCurrentCUDAStream(mx.get_device())>>>(
                    mx.data_ptr<float>(), sc.data_ptr<float>(), bs.data_ptr<float>(), pre.data_ptr<float>(),
                    post.data_ptr<float>(), comb.data_ptr<float>(), rows, hc, iters, (float) eps);
            const cudaError_t err = cudaGetLastError();
            if (err != cudaSuccess) throw std::runtime_error(std::string("smile_ds_hc_split_sinkhorn: ") + cudaGetErrorString(err));
        }
        out[0] = new ST_Tensor_{ pre };
        out[1] = new ST_Tensor_{ post };
        out[2] = new ST_Tensor_{ comb };
        return 0;
    } catch (const std::exception& e) {
        set_err(e.what());
        return -1;
    }
}

extern "C" ST_Tensor smile_ds_fake_quant_fp8(ST_Tensor x) {
    try {
        smile_torch_clear_error();
        const at::Tensor& xt = require_cuda(x, "smile_ds_fake_quant_fp8: x");
        if (!xt.is_contiguous() || xt.numel() % 32 != 0) {
            throw std::runtime_error("smile_ds_fake_quant_fp8: contiguous tensor with numel % 32 == 0 required");
        }
        c10::cuda::CUDAGuard guard(xt.get_device());
        at::Tensor out = at::empty_like(xt);
        const int64_t blocks = xt.numel() / 32;
        if (blocks > 0) {
            const unsigned grid = (unsigned) ((blocks * 32 + 255) / 256);
            cudaStream_t st = at::cuda::getCurrentCUDAStream(xt.get_device());
            switch (xt.scalar_type()) {
                case at::kFloat:
                    fake_quant_fp8_kernel<float><<<grid, 256, 0, st>>>(xt.data_ptr<float>(), out.data_ptr<float>(), blocks);
                    break;
                case at::kBFloat16:
                    fake_quant_fp8_kernel<__nv_bfloat16><<<grid, 256, 0, st>>>(
                            (const __nv_bfloat16*) xt.data_ptr(), (__nv_bfloat16*) out.data_ptr(), blocks);
                    break;
                case at::kHalf:
                    fake_quant_fp8_kernel<__half><<<grid, 256, 0, st>>>(
                            (const __half*) xt.data_ptr(), (__half*) out.data_ptr(), blocks);
                    break;
                default:
                    throw std::runtime_error("smile_ds_fake_quant_fp8: unsupported dtype");
            }
            const cudaError_t err = cudaGetLastError();
            if (err != cudaSuccess) throw std::runtime_error(std::string("smile_ds_fake_quant_fp8: ") + cudaGetErrorString(err));
        }
        return new ST_Tensor_{ out };
    } catch (const std::exception& e) {
        set_err(e.what());
        return nullptr;
    }
}

namespace {

#define DS_DISPATCH(DT, WHAT, ...)                                                                     \
    switch (DT) {                                                                                      \
        case at::kFloat: { using T = float; __VA_ARGS__; break; }                                      \
        case at::kBFloat16: { using T = __nv_bfloat16; __VA_ARGS__; break; }                           \
        case at::kHalf: { using T = __half; __VA_ARGS__; break; }                                      \
        default: throw std::runtime_error(std::string(WHAT) + ": unsupported dtype");                  \
    }

void check_launch(const char* what) {
    const cudaError_t err = cudaGetLastError();
    if (err != cudaSuccess) throw std::runtime_error(std::string(what) + ": " + cudaGetErrorString(err));
}

}  // namespace

extern "C" int smile_ds_hc_mixes(ST_Tensor flat, ST_Tensor raw, ST_Tensor scale, ST_Tensor base, int hc, int iters,
                                 double eps, double norm_eps, ST_Tensor *out) {
    try {
        smile_torch_clear_error();
        const at::Tensor& fl = require_cuda(flat, "smile_ds_hc_mixes: flat");
        const at::Tensor& rw = require_cuda(raw, "smile_ds_hc_mixes: raw");
        const at::Tensor& sc = require_cuda(scale, "smile_ds_hc_mixes: scale");
        const at::Tensor& bs = require_cuda(base, "smile_ds_hc_mixes: base");
        const int m = (2 + hc) * hc;
        if (hc < 1 || hc > MAX_HC || iters < 1 || fl.scalar_type() != at::kFloat || rw.scalar_type() != at::kFloat
                || sc.scalar_type() != at::kFloat || bs.scalar_type() != at::kFloat || rw.size(-1) != m
                || sc.numel() != 3 || bs.numel() != m || !fl.is_contiguous() || !rw.is_contiguous()
                || !sc.is_contiguous() || !bs.is_contiguous() || fl.numel() == 0
                || fl.numel() / fl.size(-1) != rw.numel() / m) {
            throw std::runtime_error("smile_ds_hc_mixes: unsupported arguments");
        }
        c10::cuda::CUDAGuard guard(fl.get_device());
        const int64_t rows = rw.numel() / m;
        const int64_t k = fl.size(-1);
        std::vector<int64_t> lead(rw.sizes().begin(), rw.sizes().end() - 1);
        auto shape = [&](std::initializer_list<int64_t> tail) {
            std::vector<int64_t> s = lead;
            s.insert(s.end(), tail.begin(), tail.end());
            return s;
        };
        at::Tensor pre = at::empty(shape({hc}), rw.options());
        at::Tensor post = at::empty(shape({hc}), rw.options());
        at::Tensor comb = at::empty(shape({hc, hc}), rw.options());
        if (hc * hc <= 32) {
            hc_mixes_warp_kernel<<<(unsigned) rows, 256, 0, at::cuda::getCurrentCUDAStream(fl.get_device())>>>(
                    fl.data_ptr<float>(), rw.data_ptr<float>(), sc.data_ptr<float>(), bs.data_ptr<float>(),
                    pre.data_ptr<float>(), post.data_ptr<float>(), comb.data_ptr<float>(), k, hc, iters, (float) eps,
                    (float) norm_eps);
        } else {
            hc_mixes_kernel<<<(unsigned) rows, 256, 0, at::cuda::getCurrentCUDAStream(fl.get_device())>>>(
                    fl.data_ptr<float>(), rw.data_ptr<float>(), sc.data_ptr<float>(), bs.data_ptr<float>(),
                    pre.data_ptr<float>(), post.data_ptr<float>(), comb.data_ptr<float>(), k, hc, iters, (float) eps,
                    (float) norm_eps);
        }
        check_launch("smile_ds_hc_mixes");
        out[0] = new ST_Tensor_{ pre };
        out[1] = new ST_Tensor_{ post };
        out[2] = new ST_Tensor_{ comb };
        return 0;
    } catch (const std::exception& e) {
        set_err(e.what());
        return -1;
    }
}

extern "C" ST_Tensor smile_ds_rms_norm(ST_Tensor x, ST_Tensor w, double eps) {
    try {
        smile_torch_clear_error();
        const at::Tensor& xt = require_cuda(x, "smile_ds_rms_norm: x");
        const at::Tensor& wt = require_cuda(w, "smile_ds_rms_norm: w");
        if (!xt.is_contiguous() || !wt.is_contiguous() || wt.dim() != 1 || xt.size(-1) != wt.size(0) || xt.numel() == 0
                || (wt.scalar_type() != at::kFloat && wt.scalar_type() != at::kBFloat16 && wt.scalar_type() != at::kHalf)) {
            throw std::runtime_error("smile_ds_rms_norm: unsupported arguments");
        }
        c10::cuda::CUDAGuard guard(xt.get_device());
        at::Tensor out = at::empty_like(xt);
        const int d = (int) xt.size(-1);
        const unsigned rows = (unsigned) (xt.numel() / d);
        cudaStream_t st = at::cuda::getCurrentCUDAStream(xt.get_device());
        const int threads = d >= 1024 ? 256 : 128;
#define DS_RMS(W)                                                                                          \
        DS_DISPATCH(xt.scalar_type(), "smile_ds_rms_norm",                                                  \
                    rms_norm_kernel<T, W><<<rows, threads, 0, st>>>((const T*) xt.data_ptr(),               \
                            (const W*) wt.data_ptr(), (T*) out.data_ptr(), d, (float) eps))
        switch (wt.scalar_type()) {
            case at::kFloat: DS_RMS(float); break;
            case at::kBFloat16: DS_RMS(__nv_bfloat16); break;
            default: DS_RMS(__half); break;
        }
#undef DS_RMS
        check_launch("smile_ds_rms_norm");
        return new ST_Tensor_{ out };
    } catch (const std::exception& e) {
        set_err(e.what());
        return nullptr;
    }
}

extern "C" ST_Tensor smile_ds_hc_pre(ST_Tensor x, ST_Tensor pre) {
    try {
        smile_torch_clear_error();
        const at::Tensor& xt = require_cuda(x, "smile_ds_hc_pre: x");
        const at::Tensor& pt = require_cuda(pre, "smile_ds_hc_pre: pre");
        if (xt.dim() < 2 || !xt.is_contiguous() || !pt.is_contiguous() || pt.scalar_type() != at::kFloat
                || pt.size(-1) != xt.size(-2) || pt.numel() / pt.size(-1) != xt.numel() / (xt.size(-1) * xt.size(-2))
                || xt.numel() == 0) {
            throw std::runtime_error("smile_ds_hc_pre: unsupported arguments");
        }
        c10::cuda::CUDAGuard guard(xt.get_device());
        const int hc = (int) xt.size(-2), dim = (int) xt.size(-1);
        const int64_t rows = xt.numel() / ((int64_t) hc * dim);
        std::vector<int64_t> shape(xt.sizes().begin(), xt.sizes().end());
        shape.erase(shape.end() - 2);
        at::Tensor out = at::empty(shape, xt.options());
        const unsigned grid = (unsigned) ((rows * dim + 255) / 256);
        cudaStream_t st = at::cuda::getCurrentCUDAStream(xt.get_device());
        DS_DISPATCH(xt.scalar_type(), "smile_ds_hc_pre",
                    hc_pre_kernel<T><<<grid, 256, 0, st>>>((const T*) xt.data_ptr(), pt.data_ptr<float>(),
                                                           (T*) out.data_ptr(), rows, hc, dim));
        check_launch("smile_ds_hc_pre");
        return new ST_Tensor_{ out };
    } catch (const std::exception& e) {
        set_err(e.what());
        return nullptr;
    }
}

extern "C" ST_Tensor smile_ds_hc_post(ST_Tensor x, ST_Tensor residual, ST_Tensor post, ST_Tensor comb) {
    try {
        smile_torch_clear_error();
        const at::Tensor& xt = require_cuda(x, "smile_ds_hc_post: x");
        const at::Tensor& rt = require_cuda(residual, "smile_ds_hc_post: residual");
        const at::Tensor& pt = require_cuda(post, "smile_ds_hc_post: post");
        const at::Tensor& ct = require_cuda(comb, "smile_ds_hc_post: comb");
        if (rt.dim() < 2 || xt.scalar_type() != rt.scalar_type() || !xt.is_contiguous() || !rt.is_contiguous()
                || !pt.is_contiguous() || !ct.is_contiguous() || pt.scalar_type() != at::kFloat
                || ct.scalar_type() != at::kFloat || rt.numel() == 0) {
            throw std::runtime_error("smile_ds_hc_post: unsupported arguments");
        }
        const int hc = (int) rt.size(-2), dim = (int) rt.size(-1);
        const int64_t rows = rt.numel() / ((int64_t) hc * dim);
        if (xt.numel() != rows * dim || pt.numel() != rows * hc || ct.numel() != rows * hc * hc) {
            throw std::runtime_error("smile_ds_hc_post: shape mismatch");
        }
        c10::cuda::CUDAGuard guard(rt.get_device());
        at::Tensor out = at::empty_like(rt);
        const unsigned grid = (unsigned) ((rows * hc * dim + 255) / 256);
        cudaStream_t st = at::cuda::getCurrentCUDAStream(rt.get_device());
        DS_DISPATCH(rt.scalar_type(), "smile_ds_hc_post",
                    hc_post_kernel<T><<<grid, 256, 0, st>>>((const T*) xt.data_ptr(), (const T*) rt.data_ptr(),
                                                            pt.data_ptr<float>(), ct.data_ptr<float>(),
                                                            (T*) out.data_ptr(), rows, hc, dim));
        check_launch("smile_ds_hc_post");
        return new ST_Tensor_{ out };
    } catch (const std::exception& e) {
        set_err(e.what());
        return nullptr;
    }
}

extern "C" ST_Tensor smile_ds_sparse_attention(ST_Tensor q, ST_Tensor kv, ST_Tensor sink, ST_Tensor idxs, double scale) {
    try {
        smile_torch_clear_error();
        const at::Tensor& qt = require_cuda(q, "smile_ds_sparse_attention: q");
        const at::Tensor& kt = require_cuda(kv, "smile_ds_sparse_attention: kv");
        const at::Tensor& st = require_cuda(sink, "smile_ds_sparse_attention: sink");
        const at::Tensor& it = require_cuda(idxs, "smile_ds_sparse_attention: idxs");
        if (qt.dim() != 4 || qt.size(0) != 1 || qt.size(3) != ATT_D || qt.scalar_type() != at::kBFloat16
                || kt.scalar_type() != at::kBFloat16 || kt.dim() != 3 || kt.size(2) != ATT_D || kt.size(0) != 1
                || st.scalar_type() != at::kFloat || st.numel() != qt.size(2) || it.scalar_type() != at::kInt
                || it.dim() != 3 || it.size(0) != 1 || it.size(1) != qt.size(1) || it.size(2) < 1
                || !qt.is_contiguous() || !kt.is_contiguous() || !st.is_contiguous() || !it.is_contiguous()
                || (it.size(2) + ATT_WARPS * ATT_D) * 4 > 48 * 1024) {
            throw std::runtime_error("smile_ds_sparse_attention: unsupported arguments");
        }
        c10::cuda::CUDAGuard guard(qt.get_device());
        const int m = (int) qt.size(1), heads = (int) qt.size(2), topk = (int) it.size(2);
        at::Tensor out = at::empty_like(qt);
        if (m > 0) {
            sparse_attention_kernel<<<(unsigned) (m * heads), ATT_THREADS, (topk + ATT_WARPS * ATT_D) * sizeof(float),
                                      at::cuda::getCurrentCUDAStream(qt.get_device())>>>(
                    (const __nv_bfloat16*) qt.data_ptr(), (const __nv_bfloat16*) kt.data_ptr(), st.data_ptr<float>(),
                    it.data_ptr<int>(), (__nv_bfloat16*) out.data_ptr(), heads, topk, (float) scale, nullptr, nullptr, 0);
            check_launch("smile_ds_sparse_attention");
        }
        return new ST_Tensor_{ out };
    } catch (const std::exception& e) {
        set_err(e.what());
        return nullptr;
    }
}

extern "C" int smile_ds_route(ST_Tensor logits, ST_Tensor bias, int topk, double route_scale, ST_Tensor *out) {
    try {
        smile_torch_clear_error();
        const at::Tensor& lt = require_cuda(logits, "smile_ds_route: logits");
        const at::Tensor& bt = require_cuda(bias, "smile_ds_route: bias");
        if (lt.dim() != 2 || lt.scalar_type() != at::kFloat || bt.scalar_type() != at::kFloat || !lt.is_contiguous()
                || !bt.is_contiguous() || bt.numel() != lt.size(1) || lt.size(1) > ROUTE_MAX_E || topk < 1
                || topk > 32 || topk > lt.size(1) || lt.size(0) < 1) {
            throw std::runtime_error("smile_ds_route: unsupported arguments");
        }
        c10::cuda::CUDAGuard guard(lt.get_device());
        const int64_t n = lt.size(0);
        at::Tensor idx = at::empty({n, topk}, lt.options().dtype(at::kLong));
        at::Tensor wts = at::empty({n, topk}, lt.options());
        route_kernel<<<(unsigned) n, ROUTE_THREADS, 0, at::cuda::getCurrentCUDAStream(lt.get_device())>>>(
                lt.data_ptr<float>(), bt.data_ptr<float>(), idx.data_ptr<int64_t>(), wts.data_ptr<float>(),
                (int) lt.size(1), topk, (float) route_scale);
        check_launch("smile_ds_route");
        out[0] = new ST_Tensor_{ idx };
        out[1] = new ST_Tensor_{ wts };
        return 0;
    } catch (const std::exception& e) {
        set_err(e.what());
        return -1;
    }
}

extern "C" ST_Tensor smile_ds_swiglu(ST_Tensor gate_up, ST_Tensor weights, double limit, int quant) {
    try {
        smile_torch_clear_error();
        const at::Tensor& gt = require_cuda(gate_up, "smile_ds_swiglu: gate_up");
        if (gt.dim() != 2 || gt.scalar_type() != at::kBFloat16 || !gt.is_contiguous() || gt.size(1) % 64 != 0
                || gt.size(0) < 1) {
            throw std::runtime_error("smile_ds_swiglu: unsupported arguments");
        }
        const float* w = nullptr;
        if (weights != nullptr) {
            const at::Tensor& wt = require_cuda(weights, "smile_ds_swiglu: weights");
            if (wt.scalar_type() != at::kFloat || !wt.is_contiguous() || wt.numel() != gt.size(0)) {
                throw std::runtime_error("smile_ds_swiglu: weights must be float32 [rows]");
            }
            w = wt.data_ptr<float>();
        }
        c10::cuda::CUDAGuard guard(gt.get_device());
        const int64_t rows = gt.size(0);
        const int inter = (int) (gt.size(1) / 2);
        at::Tensor out = at::empty({rows, inter}, gt.options());
        const int64_t warps = rows * (inter / 32);
        swiglu_kernel<<<(unsigned) ((warps * 32 + 255) / 256), 256, 0, at::cuda::getCurrentCUDAStream(gt.get_device())>>>(
                (const __nv_bfloat16*) gt.data_ptr(), w, (__nv_bfloat16*) out.data_ptr(), rows, inter, (float) limit, quant);
        check_launch("smile_ds_swiglu");
        return new ST_Tensor_{ out };
    } catch (const std::exception& e) {
        set_err(e.what());
        return nullptr;
    }
}

// ---------------------------------------------------------------- prefill attention: softmax between the two GEMMs

/**
 * Softmax of each (row, head)'s topk raw scores q.k (float32): scale, masked slots (index < 0), the attention-sink
 * logit in the denominator; writes the normalized probabilities in bf16 for the value GEMM. One warp per (row, head).
 */
__global__ void attn_softmax_kernel(const float* __restrict__ s, const int* __restrict__ idx, const float* __restrict__ sink,
                                    __nv_bfloat16* __restrict__ p, int64_t rowsHeads, int heads, int topk, float scale) {
    const int64_t gw = (int64_t) blockIdx.x * (blockDim.x >> 5) + (threadIdx.x >> 5);
    if (gw >= rowsHeads) return;
    const int lane = threadIdx.x & 31;
    const int64_t row = gw / heads;
    const int h = (int) (gw % heads);
    const float* sr = s + gw * topk;
    const int* ix = idx + row * topk;
    float mx = -INFINITY;
    for (int t = lane; t < topk; t += 32) {
        if (ix[t] >= 0) mx = fmaxf(mx, sr[t] * scale);
    }
    for (int o = 16; o > 0; o >>= 1) mx = fmaxf(mx, __shfl_xor_sync(0xffffffffu, mx, o));
    mx = fmaxf(mx, -1e30f);
    float sum = 0.f;
    for (int t = lane; t < topk; t += 32) {
        if (ix[t] >= 0) sum += expf(sr[t] * scale - mx);
    }
    sum = warp_sum(sum);
    const float denom = sum + expf(sink[h] - mx);
    __nv_bfloat16* pr = p + gw * topk;
    for (int t = lane; t < topk; t += 32) {
        pr[t] = __float2bfloat16_rn(ix[t] >= 0 ? expf(sr[t] * scale - mx) / denom : 0.f);
    }
}

extern "C" ST_Tensor smile_ds_attn_softmax(ST_Tensor scores, ST_Tensor idxs, ST_Tensor sink, double scale) {
    try {
        smile_torch_clear_error();
        const at::Tensor& st = require_cuda(scores, "smile_ds_attn_softmax: scores");
        const at::Tensor& it = require_cuda(idxs, "smile_ds_attn_softmax: idxs");
        const at::Tensor& kt = require_cuda(sink, "smile_ds_attn_softmax: sink");
        if (st.dim() != 3 || st.scalar_type() != at::kFloat || !st.is_contiguous() || it.dim() != 2
                || it.scalar_type() != at::kInt || !it.is_contiguous() || it.size(0) != st.size(0) || it.size(1) != st.size(2)
                || kt.scalar_type() != at::kFloat || kt.numel() != st.size(1) || !kt.is_contiguous() || st.numel() == 0) {
            throw std::runtime_error("smile_ds_attn_softmax: unsupported arguments");
        }
        c10::cuda::CUDAGuard guard(st.get_device());
        const int64_t rows = st.size(0);
        const int heads = (int) st.size(1), topk = (int) st.size(2);
        at::Tensor out = at::empty(st.sizes(), st.options().dtype(at::kBFloat16));
        const int64_t warps = rows * heads;
        attn_softmax_kernel<<<(unsigned) ((warps + 3) / 4), 128, 0, at::cuda::getCurrentCUDAStream(st.get_device())>>>(
                st.data_ptr<float>(), it.data_ptr<int>(), kt.data_ptr<float>(), (__nv_bfloat16*) out.data_ptr(), warps, heads,
                topk, (float) scale);
        check_launch("smile_ds_attn_softmax");
        return new ST_Tensor_{ out };
    } catch (const std::exception& e) {
        set_err(e.what());
        return nullptr;
    }
}

extern "C" ST_Tensor smile_ds_sum_topk(ST_Tensor x, int topk) {
    try {
        smile_torch_clear_error();
        const at::Tensor& xt = require_cuda(x, "smile_ds_sum_topk: x");
        if (xt.dim() != 2 || xt.scalar_type() != at::kBFloat16 || !xt.is_contiguous() || topk < 1
                || xt.size(0) % topk != 0 || xt.size(0) < 1) {
            throw std::runtime_error("smile_ds_sum_topk: unsupported arguments");
        }
        c10::cuda::CUDAGuard guard(xt.get_device());
        const int64_t tokens = xt.size(0) / topk;
        const int dim = (int) xt.size(1);
        at::Tensor out = at::empty({tokens, dim}, xt.options().dtype(at::kFloat));
        sum_topk_kernel<<<(unsigned) ((tokens * dim + 255) / 256), 256, 0, at::cuda::getCurrentCUDAStream(xt.get_device())>>>(
                (const __nv_bfloat16*) xt.data_ptr(), out.data_ptr<float>(), tokens, topk, dim);
        check_launch("smile_ds_sum_topk");
        return new ST_Tensor_{ out };
    } catch (const std::exception& e) {
        set_err(e.what());
        return nullptr;
    }
}

extern "C" ST_Tensor smile_ds_rope_tail(ST_Tensor x, ST_Tensor freqs, int rd, int inverse) {
    try {
        smile_torch_clear_error();
        const at::Tensor& xt = require_cuda(x, "smile_ds_rope_tail: x");
        const at::Tensor& ft = require_cuda(freqs, "smile_ds_rope_tail: freqs");
        if ((xt.dim() != 3 && xt.dim() != 4) || xt.size(0) != 1 || !xt.is_contiguous() || ft.dim() != 2
                || ft.scalar_type() != at::kComplexFloat || !ft.is_contiguous() || rd < 2 || rd % 2 != 0
                || rd > xt.size(-1) || ft.size(1) != rd / 2 || ft.size(0) != xt.size(1) || xt.numel() == 0) {
            throw std::runtime_error("smile_ds_rope_tail: unsupported arguments");
        }
        c10::cuda::CUDAGuard guard(xt.get_device());
        at::Tensor out = at::empty_like(xt);
        const int64_t total = xt.numel();
        const int64_t per_row = total / xt.size(1);
        const int d = (int) xt.size(-1);
        const unsigned grid = (unsigned) ((total + 255) / 256);
        cudaStream_t st = at::cuda::getCurrentCUDAStream(xt.get_device());
        const float2* fp = reinterpret_cast<const float2*>(ft.data_ptr());
        DS_DISPATCH(xt.scalar_type(), "smile_ds_rope_tail",
                    rope_tail_kernel<T><<<grid, 256, 0, st>>>((const T*) xt.data_ptr(), fp, (T*) out.data_ptr(), total,
                                                              per_row, d, rd, inverse));
        check_launch("smile_ds_rope_tail");
        return new ST_Tensor_{ out };
    } catch (const std::exception& e) {
        set_err(e.what());
        return nullptr;
    }
}

extern "C" ST_Tensor smile_ds_fake_quant_fp4(ST_Tensor x, int block, int e4m3_scale) {
    try {
        smile_torch_clear_error();
        const at::Tensor& xt = require_cuda(x, "smile_ds_fake_quant_fp4: x");
        if ((block != 16 && block != 32) || !xt.is_contiguous() || xt.numel() == 0 || xt.numel() % 32 != 0
                || xt.size(-1) % block != 0) {
            throw std::runtime_error("smile_ds_fake_quant_fp4: unsupported arguments");
        }
        c10::cuda::CUDAGuard guard(xt.get_device());
        at::Tensor out = at::empty_like(xt);
        const int64_t n = xt.numel();
        const unsigned grid = (unsigned) ((n + 255) / 256);
        cudaStream_t st = at::cuda::getCurrentCUDAStream(xt.get_device());
        DS_DISPATCH(xt.scalar_type(), "smile_ds_fake_quant_fp4",
                    fake_quant_fp4_kernel<T><<<grid, 256, 0, st>>>((const T*) xt.data_ptr(), (T*) out.data_ptr(), n, block,
                                                                   e4m3_scale));
        check_launch("smile_ds_fake_quant_fp4");
        return new ST_Tensor_{ out };
    } catch (const std::exception& e) {
        set_err(e.what());
        return nullptr;
    }
}

extern "C" ST_Tensor smile_ds_index_score(ST_Tensor q, ST_Tensor k, ST_Tensor w) {
    try {
        smile_torch_clear_error();
        const at::Tensor& qt = require_cuda(q, "smile_ds_index_score: q");
        const at::Tensor& kt = require_cuda(k, "smile_ds_index_score: k");
        const at::Tensor& wt = require_cuda(w, "smile_ds_index_score: w");
        if (qt.dim() != 4 || qt.size(0) != 1 || kt.dim() != 3 || kt.size(0) != 1 || qt.scalar_type() != at::kBFloat16
                || kt.scalar_type() != at::kBFloat16 || wt.scalar_type() != at::kBFloat16 || qt.size(2) > 32
                || qt.size(3) != kt.size(2) || wt.numel() != qt.size(1) * qt.size(2) || !qt.is_contiguous()
                || !wt.is_contiguous() || kt.size(1) < 1 || qt.size(1) < 1 || qt.size(1) > 65535) {
            throw std::runtime_error("smile_ds_index_score: unsupported arguments");
        }
        // k may be a view of the cache [1, maxT, hd] cut to its first T rows: contiguous either way
        if (!kt.is_contiguous()) throw std::runtime_error("smile_ds_index_score: k must be contiguous");
        c10::cuda::CUDAGuard guard(qt.get_device());
        const int64_t S = qt.size(1), T = kt.size(1);
        at::Tensor out = at::empty({1, S, T}, qt.options());
        index_score_kernel<<<dim3((unsigned) T, (unsigned) S), 32, 0, at::cuda::getCurrentCUDAStream(qt.get_device())>>>(
                (const __nv_bfloat16*) qt.data_ptr(), (const __nv_bfloat16*) kt.data_ptr(),
                (const __nv_bfloat16*) wt.data_ptr(), (__nv_bfloat16*) out.data_ptr(), (int) qt.size(2), (int) qt.size(3), T);
        check_launch("smile_ds_index_score");
        return new ST_Tensor_{ out };
    } catch (const std::exception& e) {
        set_err(e.what());
        return nullptr;
    }
}

extern "C" ST_Tensor smile_ds_index_finalize(ST_Tensor idxs, ST_Tensor lens, long long offset) {
    try {
        smile_torch_clear_error();
        const at::Tensor& it = require_cuda(idxs, "smile_ds_index_finalize: idxs");
        const at::Tensor& lt = require_cuda(lens, "smile_ds_index_finalize: lens");
        if (it.scalar_type() != at::kLong || lt.scalar_type() != at::kLong || !it.is_contiguous() || !lt.is_contiguous()
                || it.dim() != 3 || it.size(2) < 1 || it.numel() == 0 || (lt.numel() != 1 && lt.numel() != it.size(1))) {
            throw std::runtime_error("smile_ds_index_finalize: unsupported arguments");
        }
        c10::cuda::CUDAGuard guard(it.get_device());
        at::Tensor out = at::empty(it.sizes(), it.options().dtype(at::kInt));
        const int64_t n = it.numel();
        index_finalize_kernel<<<(unsigned) ((n + 255) / 256), 256, 0, at::cuda::getCurrentCUDAStream(it.get_device())>>>(
                it.data_ptr<int64_t>(), lt.data_ptr<int64_t>(), (int) lt.numel(), out.data_ptr<int32_t>(), n,
                (int) it.size(2), (int64_t) offset);
        check_launch("smile_ds_index_finalize");
        return new ST_Tensor_{ out };
    } catch (const std::exception& e) {
        set_err(e.what());
        return nullptr;
    }
}

// ---------------------------------------------------------------- batched decode: host entry points

extern "C" ST_Tensor smile_ds_ptr_table(const ST_Tensor *ts, int n) {
    try {
        smile_torch_clear_error();
        if (n < 1) throw std::runtime_error("smile_ds_ptr_table: empty");
        std::vector<int64_t> v(n);
        for (int i = 0; i < n; i++) {
            if (!ts[i]) throw std::runtime_error("smile_ds_ptr_table: null tensor");
            v[i] = (int64_t) ts[i]->t.data_ptr();
        }
        at::Tensor out = at::tensor(v, at::TensorOptions().dtype(at::kLong)).to(ts[0]->t.device());
        return new ST_Tensor_{ out };
    } catch (const std::exception& e) {
        set_err(e.what());
        return nullptr;
    }
}

extern "C" int smile_ds_window_write(ST_Tensor src, ST_Tensor ptrs, ST_Tensor pos, int window) {
    try {
        smile_torch_clear_error();
        const at::Tensor& st = require_cuda(src, "smile_ds_window_write: src");
        const at::Tensor& pt = require_cuda(ptrs, "smile_ds_window_write: ptrs");
        const at::Tensor& ps = require_cuda(pos, "smile_ds_window_write: pos");
        const int n = (int) pt.numel(), d = (int) st.size(-1);
        if (st.scalar_type() != at::kBFloat16 || !st.is_contiguous() || st.numel() != (int64_t) n * d
                || pt.scalar_type() != at::kLong || ps.scalar_type() != at::kLong || ps.numel() != n || window < 1) {
            throw std::runtime_error("smile_ds_window_write: unsupported arguments");
        }
        c10::cuda::CUDAGuard guard(st.get_device());
        window_write_kernel<<<n, 128, 0, at::cuda::getCurrentCUDAStream(st.get_device())>>>(
                (const __nv_bfloat16*) st.data_ptr(), pt.data_ptr<int64_t>(), ps.data_ptr<int64_t>(), window, d);
        check_launch("smile_ds_window_write");
        return 0;
    } catch (const std::exception& e) {
        set_err(e.what());
        return -1;
    }
}

extern "C" int smile_ds_cache_scatter(ST_Tensor src, ST_Tensor ptrs, ST_Tensor pos, int div, ST_Tensor emit) {
    try {
        smile_torch_clear_error();
        const at::Tensor& st = require_cuda(src, "smile_ds_cache_scatter: src");
        const at::Tensor& pt = require_cuda(ptrs, "smile_ds_cache_scatter: ptrs");
        const at::Tensor& ps = require_cuda(pos, "smile_ds_cache_scatter: pos");
        const int n = (int) pt.numel(), d = (int) st.size(-1);
        const uint8_t* em = nullptr;
        if (emit != nullptr) {
            const at::Tensor& et = require_cuda(emit, "smile_ds_cache_scatter: emit");
            if (et.scalar_type() != at::kByte || et.numel() != n || !et.is_contiguous()) {
                throw std::runtime_error("smile_ds_cache_scatter: emit must be uint8 [n]");
            }
            em = et.data_ptr<uint8_t>();
        }
        if (st.scalar_type() != at::kBFloat16 || !st.is_contiguous() || st.numel() != (int64_t) n * d
                || pt.scalar_type() != at::kLong || ps.scalar_type() != at::kLong || ps.numel() != n || div < 1) {
            throw std::runtime_error("smile_ds_cache_scatter: unsupported arguments");
        }
        c10::cuda::CUDAGuard guard(st.get_device());
        cache_scatter_kernel<<<n, 128, 0, at::cuda::getCurrentCUDAStream(st.get_device())>>>(
                (const __nv_bfloat16*) st.data_ptr(), pt.data_ptr<int64_t>(), ps.data_ptr<int64_t>(), div, em, d, 0);
        check_launch("smile_ds_cache_scatter");
        return 0;
    } catch (const std::exception& e) {
        set_err(e.what());
        return -1;
    }
}

extern "C" ST_Tensor smile_ds_compress_state(ST_Tensor kv, ST_Tensor score, ST_Tensor kvptrs, ST_Tensor scptrs,
                                             ST_Tensor pos, int ratio) {
    try {
        smile_torch_clear_error();
        const at::Tensor& kt = require_cuda(kv, "smile_ds_compress_state: kv");
        const at::Tensor& st = require_cuda(score, "smile_ds_compress_state: score");
        const at::Tensor& kp = require_cuda(kvptrs, "smile_ds_compress_state: kvptrs");
        const at::Tensor& sp = require_cuda(scptrs, "smile_ds_compress_state: scptrs");
        const at::Tensor& ps = require_cuda(pos, "smile_ds_compress_state: pos");
        const int n = (int) kp.numel(), hd = (int) kt.size(-1);
        if (kt.scalar_type() != at::kFloat || st.scalar_type() != at::kFloat || !kt.is_contiguous() || !st.is_contiguous()
                || kt.numel() != (int64_t) n * hd || st.numel() != kt.numel() || sp.numel() != n || ps.numel() != n
                || kp.scalar_type() != at::kLong || sp.scalar_type() != at::kLong || ps.scalar_type() != at::kLong
                || ratio < 2) {
            throw std::runtime_error("smile_ds_compress_state: unsupported arguments");
        }
        c10::cuda::CUDAGuard guard(kt.get_device());
        at::Tensor pooled = at::zeros({1, n, hd}, kt.options());
        compress_state_kernel<<<dim3((unsigned) ((hd + 127) / 128), (unsigned) n), 128, 0,
                                at::cuda::getCurrentCUDAStream(kt.get_device())>>>(
                kt.data_ptr<float>(), st.data_ptr<float>(), kp.data_ptr<int64_t>(), sp.data_ptr<int64_t>(),
                ps.data_ptr<int64_t>(), ratio, hd, pooled.data_ptr<float>());
        check_launch("smile_ds_compress_state");
        return new ST_Tensor_{ pooled };
    } catch (const std::exception& e) {
        set_err(e.what());
        return nullptr;
    }
}

extern "C" ST_Tensor smile_ds_index_score_ptrs(ST_Tensor q, ST_Tensor kptrs, ST_Tensor lens, ST_Tensor w, long long tmax) {
    try {
        smile_torch_clear_error();
        const at::Tensor& qt = require_cuda(q, "smile_ds_index_score_ptrs: q");
        const at::Tensor& kp = require_cuda(kptrs, "smile_ds_index_score_ptrs: kptrs");
        const at::Tensor& lt = require_cuda(lens, "smile_ds_index_score_ptrs: lens");
        const at::Tensor& wt = require_cuda(w, "smile_ds_index_score_ptrs: w");
        if (qt.dim() != 4 || qt.size(0) != 1 || qt.scalar_type() != at::kBFloat16 || wt.scalar_type() != at::kBFloat16
                || qt.size(2) > 32 || !qt.is_contiguous() || !wt.is_contiguous() || wt.numel() != qt.size(1) * qt.size(2)
                || kp.numel() != qt.size(1) || lt.numel() != qt.size(1) || kp.scalar_type() != at::kLong
                || lt.scalar_type() != at::kLong || tmax < 1 || qt.size(1) > 65535) {
            throw std::runtime_error("smile_ds_index_score_ptrs: unsupported arguments");
        }
        c10::cuda::CUDAGuard guard(qt.get_device());
        const int64_t S = qt.size(1);
        at::Tensor out = at::empty({1, S, (int64_t) tmax}, qt.options());
        index_score_ptrs_kernel<<<dim3((unsigned) tmax, (unsigned) S), 32, 0, at::cuda::getCurrentCUDAStream(qt.get_device())>>>(
                (const __nv_bfloat16*) qt.data_ptr(), kp.data_ptr<int64_t>(), lt.data_ptr<int64_t>(),
                (const __nv_bfloat16*) wt.data_ptr(), (__nv_bfloat16*) out.data_ptr(), (int) qt.size(2), (int) qt.size(3),
                (int64_t) tmax);
        check_launch("smile_ds_index_score_ptrs");
        return new ST_Tensor_{ out };
    } catch (const std::exception& e) {
        set_err(e.what());
        return nullptr;
    }
}

extern "C" ST_Tensor smile_ds_sparse_attention_ptrs(ST_Tensor q, ST_Tensor wptrs, ST_Tensor cptrs, int window, ST_Tensor sink,
                                                    ST_Tensor idxs, double scale) {
    try {
        smile_torch_clear_error();
        const at::Tensor& qt = require_cuda(q, "smile_ds_sparse_attention_ptrs: q");
        const at::Tensor& wp = require_cuda(wptrs, "smile_ds_sparse_attention_ptrs: wptrs");
        const at::Tensor& st = require_cuda(sink, "smile_ds_sparse_attention_ptrs: sink");
        const at::Tensor& it = require_cuda(idxs, "smile_ds_sparse_attention_ptrs: idxs");
        const int64_t* cp = nullptr;
        if (cptrs != nullptr) {
            const at::Tensor& ct = require_cuda(cptrs, "smile_ds_sparse_attention_ptrs: cptrs");
            if (ct.scalar_type() != at::kLong || ct.numel() != qt.size(1)) {
                throw std::runtime_error("smile_ds_sparse_attention_ptrs: cptrs must be int64 [m]");
            }
            cp = ct.data_ptr<int64_t>();
        }
        if (qt.dim() != 4 || qt.size(0) != 1 || qt.size(3) != ATT_D || qt.scalar_type() != at::kBFloat16
                || st.scalar_type() != at::kFloat || st.numel() != qt.size(2) || it.scalar_type() != at::kInt
                || it.dim() != 3 || it.size(0) != 1 || it.size(1) != qt.size(1) || it.size(2) < 1
                || !qt.is_contiguous() || !st.is_contiguous() || !it.is_contiguous() || wp.scalar_type() != at::kLong
                || wp.numel() != qt.size(1) || window < 1 || (it.size(2) + ATT_WARPS * ATT_D) * 4 > 48 * 1024) {
            throw std::runtime_error("smile_ds_sparse_attention_ptrs: unsupported arguments");
        }
        c10::cuda::CUDAGuard guard(qt.get_device());
        const int m = (int) qt.size(1), heads = (int) qt.size(2), topk = (int) it.size(2);
        at::Tensor out = at::empty_like(qt);
        sparse_attention_kernel<<<(unsigned) (m * heads), ATT_THREADS, (topk + ATT_WARPS * ATT_D) * sizeof(float),
                                  at::cuda::getCurrentCUDAStream(qt.get_device())>>>(
                (const __nv_bfloat16*) qt.data_ptr(), nullptr, st.data_ptr<float>(), it.data_ptr<int>(),
                (__nv_bfloat16*) out.data_ptr(), heads, topk, (float) scale, wp.data_ptr<int64_t>(), cp, window);
        check_launch("smile_ds_sparse_attention_ptrs");
        return new ST_Tensor_{ out };
    } catch (const std::exception& e) {
        set_err(e.what());
        return nullptr;
    }
}

/**
 * Gathers the keys each row attends to out of its own sequence's caches (index < window: the window ring, else the
 * compressed cache) into one dense [rows, topk, D] block for the tensor-core attention; masked slots (index < 0) are zero.
 */
__global__ void gather_keys_ptrs_kernel(const int64_t* __restrict__ wptrs, const int64_t* __restrict__ cptrs,
                                        const int* __restrict__ idxs, __nv_bfloat16* __restrict__ out, int topk, int window) {
    const int row = blockIdx.x;
    const int warp = threadIdx.x >> 5, lane = threadIdx.x & 31;
    const int t = blockIdx.y * (blockDim.x >> 5) + warp;
    if (t >= topk) return;
    const int idx = idxs[(int64_t) row * topk + t];
    uint4* dst = reinterpret_cast<uint4*>(out + ((int64_t) row * topk + t) * ATT_D);
    uint4 a = make_uint4(0, 0, 0, 0), b = a;
    if (idx >= 0) {
        const __nv_bfloat16* base = reinterpret_cast<const __nv_bfloat16*>(idx < window ? wptrs[row] : cptrs[row]);
        const uint4* src = reinterpret_cast<const uint4*>(base + (int64_t) (idx < window ? idx : idx - window) * ATT_D);
        a = src[lane];
        b = src[32 + lane];
    }
    dst[lane] = a;
    dst[32 + lane] = b;
}

extern "C" ST_Tensor smile_ds_gather_keys_ptrs(ST_Tensor wptrs, ST_Tensor cptrs, ST_Tensor idxs, int window) {
    try {
        smile_torch_clear_error();
        const at::Tensor& wp = require_cuda(wptrs, "smile_ds_gather_keys_ptrs: wptrs");
        const at::Tensor& it = require_cuda(idxs, "smile_ds_gather_keys_ptrs: idxs");
        if (it.dim() != 3 || it.size(0) != 1 || it.scalar_type() != at::kInt || !it.is_contiguous()
                || wp.scalar_type() != at::kLong || wp.numel() != it.size(1) || window < 1 || it.numel() == 0) {
            throw std::runtime_error("smile_ds_gather_keys_ptrs: unsupported arguments");
        }
        const int64_t* cp = nullptr;
        if (cptrs != nullptr) {
            const at::Tensor& ct = require_cuda(cptrs, "smile_ds_gather_keys_ptrs: cptrs");
            if (ct.scalar_type() != at::kLong || ct.numel() != it.size(1)) {
                throw std::runtime_error("smile_ds_gather_keys_ptrs: cptrs must be int64 [m]");
            }
            cp = ct.data_ptr<int64_t>();
        }
        c10::cuda::CUDAGuard guard(it.get_device());
        const int m = (int) it.size(1), topk = (int) it.size(2);
        at::Tensor out = at::empty({m, topk, ATT_D}, it.options().dtype(at::kBFloat16));
        const dim3 grid((unsigned) m, (unsigned) ((topk + 15) / 16));
        gather_keys_ptrs_kernel<<<grid, 512, 0, at::cuda::getCurrentCUDAStream(it.get_device())>>>(
                wp.data_ptr<int64_t>(), cp, it.data_ptr<int>(), (__nv_bfloat16*) out.data_ptr(), topk, window);
        check_launch("smile_ds_gather_keys_ptrs");
        return new ST_Tensor_{ out };
    } catch (const std::exception& e) {
        set_err(e.what());
        return nullptr;
    }
}

extern "C" ST_Tensor smile_ds_compress_rows(ST_Tensor kv, ST_Tensor score, ST_Tensor kvptrs, ST_Tensor scptrs, ST_Tensor pos,
                                            ST_Tensor jidx, int ratio) {
    try {
        smile_torch_clear_error();
        const at::Tensor& kt = require_cuda(kv, "smile_ds_compress_rows: kv");
        const at::Tensor& st = require_cuda(score, "smile_ds_compress_rows: score");
        const at::Tensor& kp = require_cuda(kvptrs, "smile_ds_compress_rows: kvptrs");
        const at::Tensor& sp = require_cuda(scptrs, "smile_ds_compress_rows: scptrs");
        const at::Tensor& ps = require_cuda(pos, "smile_ds_compress_rows: pos");
        const at::Tensor& js = require_cuda(jidx, "smile_ds_compress_rows: jidx");
        const int n = (int) kp.numel(), hd = (int) kt.size(-1);
        if (kt.scalar_type() != at::kFloat || st.scalar_type() != at::kFloat || !kt.is_contiguous() || !st.is_contiguous()
                || kt.numel() != (int64_t) n * hd || st.numel() != kt.numel() || sp.numel() != n || ps.numel() != n
                || js.numel() != n || kp.scalar_type() != at::kLong || sp.scalar_type() != at::kLong
                || ps.scalar_type() != at::kLong || js.scalar_type() != at::kLong || ratio < 2 || ratio > 8) {
            throw std::runtime_error("smile_ds_compress_rows: unsupported arguments");
        }
        c10::cuda::CUDAGuard guard(kt.get_device());
        at::Tensor pooled = at::zeros({1, n, hd}, kt.options());
        compress_rows_kernel<<<dim3((unsigned) ((hd + 127) / 128), (unsigned) n), 128, 0,
                               at::cuda::getCurrentCUDAStream(kt.get_device())>>>(
                kt.data_ptr<float>(), st.data_ptr<float>(), kp.data_ptr<int64_t>(), sp.data_ptr<int64_t>(),
                ps.data_ptr<int64_t>(), js.data_ptr<int64_t>(), ratio, hd, pooled.data_ptr<float>());
        check_launch("smile_ds_compress_rows");
        return new ST_Tensor_{ pooled };
    } catch (const std::exception& e) {
        set_err(e.what());
        return nullptr;
    }
}

extern "C" int smile_ds_compress_commit(ST_Tensor kv, ST_Tensor score, ST_Tensor kvptrs, ST_Tensor scptrs, ST_Tensor base,
                                        ST_Tensor p0, ST_Tensor last, int ratio) {
    try {
        smile_torch_clear_error();
        const at::Tensor& kt = require_cuda(kv, "smile_ds_compress_commit: kv");
        const at::Tensor& st = require_cuda(score, "smile_ds_compress_commit: score");
        const at::Tensor& kp = require_cuda(kvptrs, "smile_ds_compress_commit: kvptrs");
        const at::Tensor& sp = require_cuda(scptrs, "smile_ds_compress_commit: scptrs");
        const at::Tensor& bs = require_cuda(base, "smile_ds_compress_commit: base");
        const at::Tensor& p = require_cuda(p0, "smile_ds_compress_commit: p0");
        const at::Tensor& ls = require_cuda(last, "smile_ds_compress_commit: last");
        const int n = (int) bs.numel(), hd = (int) kt.size(-1);
        if (kt.scalar_type() != at::kFloat || st.scalar_type() != at::kFloat || !kt.is_contiguous() || !st.is_contiguous()
                || st.numel() != kt.numel() || p.numel() != n || ls.numel() != n || bs.scalar_type() != at::kLong
                || p.scalar_type() != at::kLong || ls.scalar_type() != at::kLong || kp.scalar_type() != at::kLong
                || sp.scalar_type() != at::kLong || ratio < 2 || n < 1) {
            throw std::runtime_error("smile_ds_compress_commit: unsupported arguments");
        }
        c10::cuda::CUDAGuard guard(kt.get_device());
        compress_commit_kernel<<<dim3((unsigned) ((hd + 127) / 128), (unsigned) n), 128, 0,
                                 at::cuda::getCurrentCUDAStream(kt.get_device())>>>(
                kt.data_ptr<float>(), st.data_ptr<float>(), kp.data_ptr<int64_t>(), sp.data_ptr<int64_t>(),
                bs.data_ptr<int64_t>(), p.data_ptr<int64_t>(), ls.data_ptr<int64_t>(), ratio, hd);
        check_launch("smile_ds_compress_commit");
        return 0;
    } catch (const std::exception& e) {
        set_err(e.what());
        return -1;
    }
}

extern "C" ST_Tensor smile_ds_iota(ST_Tensor like, long long n) {
    try {
        smile_torch_clear_error();
        const at::Tensor& lt = require_cuda(like, "smile_ds_iota: like");
        if (n < 1) throw std::runtime_error("smile_ds_iota: n < 1");
        c10::cuda::CUDAGuard guard(lt.get_device());
        at::Tensor out = at::empty({(int64_t) n}, lt.options().dtype(at::kLong));
        iota_kernel<<<(unsigned) ((n + 255) / 256), 256, 0, at::cuda::getCurrentCUDAStream(lt.get_device())>>>(
                out.data_ptr<int64_t>(), (int64_t) n);
        check_launch("smile_ds_iota");
        return new ST_Tensor_{ out };
    } catch (const std::exception& e) {
        set_err(e.what());
        return nullptr;
    }
}

extern "C" ST_Tensor smile_ds_window_topk(ST_Tensor pos, int ring, int window) {
    try {
        smile_torch_clear_error();
        const at::Tensor& ps = require_cuda(pos, "smile_ds_window_topk: pos");
        if (ps.scalar_type() != at::kLong || ps.dim() != 1 || window < 1 || ring < window || ps.numel() < 1) {
            throw std::runtime_error("smile_ds_window_topk: unsupported arguments");
        }
        c10::cuda::CUDAGuard guard(ps.get_device());
        const int n = (int) ps.numel();
        at::Tensor out = at::empty({1, n, window}, ps.options().dtype(at::kInt));
        window_topk_kernel<<<n, 128, 0, at::cuda::getCurrentCUDAStream(ps.get_device())>>>(
                ps.data_ptr<int64_t>(), ring, window, out.data_ptr<int32_t>());
        check_launch("smile_ds_window_topk");
        return new ST_Tensor_{ out };
    } catch (const std::exception& e) {
        set_err(e.what());
        return nullptr;
    }
}

extern "C" int smile_ds_cache_scatter_gated(ST_Tensor src, ST_Tensor ptrs, ST_Tensor pos, int div) {
    try {
        smile_torch_clear_error();
        const at::Tensor& st = require_cuda(src, "smile_ds_cache_scatter_gated: src");
        const at::Tensor& pt = require_cuda(ptrs, "smile_ds_cache_scatter_gated: ptrs");
        const at::Tensor& ps = require_cuda(pos, "smile_ds_cache_scatter_gated: pos");
        const int n = (int) pt.numel(), d = (int) st.size(-1);
        if (st.scalar_type() != at::kBFloat16 || !st.is_contiguous() || st.numel() != (int64_t) n * d
                || pt.scalar_type() != at::kLong || ps.scalar_type() != at::kLong || ps.numel() != n || div < 1) {
            throw std::runtime_error("smile_ds_cache_scatter_gated: unsupported arguments");
        }
        c10::cuda::CUDAGuard guard(st.get_device());
        cache_scatter_kernel<<<n, 128, 0, at::cuda::getCurrentCUDAStream(st.get_device())>>>(
                (const __nv_bfloat16*) st.data_ptr(), pt.data_ptr<int64_t>(), ps.data_ptr<int64_t>(), div, nullptr, d,
                div > 1 ? 1 : 0);
        check_launch("smile_ds_cache_scatter_gated");
        return 0;
    } catch (const std::exception& e) {
        set_err(e.what());
        return -1;
    }
}

extern "C" int smile_ds_data_ptrs(const ST_Tensor *ts, int n, long long *out) {
    try {
        smile_torch_clear_error();
        for (int i = 0; i < n; i++) {
            out[i] = ts[i] ? (long long) ts[i]->t.data_ptr() : 0LL;
        }
        return 0;
    } catch (const std::exception& e) {
        set_err(e.what());
        return -1;
    }
}

#include <cuda_profiler_api.h>
extern "C" int smile_ds_profiler(int start) {
    return (start ? cudaProfilerStart() : cudaProfilerStop()) == cudaSuccess ? 0 : -1;
}

__global__ void stamp_kernel(unsigned long long* buf, int idx) {
    unsigned long long t;
    asm volatile("mov.u64 %0, %%globaltimer;" : "=l"(t));
    buf[idx] = t;
}

extern "C" int smile_ds_stamp(ST_Tensor buf, int idx) {
    try {
        smile_torch_clear_error();
        const at::Tensor& bt = require_cuda(buf, "smile_ds_stamp: buf");
        if (bt.scalar_type() != at::kLong || idx < 0 || idx >= bt.numel()) {
            throw std::runtime_error("smile_ds_stamp: unsupported arguments");
        }
        c10::cuda::CUDAGuard guard(bt.get_device());
        stamp_kernel<<<1, 1, 0, at::cuda::getCurrentCUDAStream(bt.get_device())>>>(
                (unsigned long long*) bt.data_ptr(), idx);
        return 0;
    } catch (const std::exception& e) {
        set_err(e.what());
        return -1;
    }
}
