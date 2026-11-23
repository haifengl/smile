/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * Decode-path kernels of Inkling-2: AttnRes reads, message gates, the attention prep / decode / merge kernels (relative
 * bias, sinks, short convolutions), the router, and small fused epilogues. One dispatcher entry point takes raw device
 * pointers, so a decode step can be captured in a CUDA graph: every launch goes to torch's current stream.
 */
#include <c10/cuda/CUDAStream.h>
#include <cuda_bf16.h>
#include <cuda_fp8.h>
#include <cuda_runtime.h>

#include <cfloat>
#include <cstdint>
#include <cstdio>
#include <cstring>
#include <string>

typedef __nv_bfloat16 bf16;

namespace {

thread_local std::string g_error;

#define CHECK_LAUNCH(name)                                                                  \
    do {                                                                                    \
        cudaError_t e_ = cudaGetLastError();                                                \
        if (e_ != cudaSuccess) {                                                            \
            g_error = std::string(name) + ": " + cudaGetErrorString(e_);                   \
            return -1;                                                                      \
        }                                                                                   \
    } while (0)

__device__ __forceinline__ float warp_sum(float v) {
    for (int o = 16; o > 0; o >>= 1) v += __shfl_xor_sync(0xffffffffu, v, o);
    return v;
}

__device__ __forceinline__ float warp_max(float v) {
    for (int o = 16; o > 0; o >>= 1) v = fmaxf(v, __shfl_xor_sync(0xffffffffu, v, o));
    return v;
}

__device__ __forceinline__ float rnd(float v) { return __bfloat162float(__float2bfloat16(v)); }

/** Block-wide sum of one value; every thread gets the result. */
__device__ __forceinline__ float block_sum(float v, float* smem) {
    const int warps = blockDim.x >> 5;
    v = warp_sum(v);
    __syncthreads();
    if ((threadIdx.x & 31) == 0) smem[threadIdx.x >> 5] = v;
    __syncthreads();
    float t = 0.f;
    for (int i = 0; i < warps; i++) t += smem[i];
    return t;
}

// ------------------------------------------------------------------------------------------------ AttnRes

struct States {
    const void* p[6];
    int f32mask;        // bit i set: state i is float32, otherwise bfloat16
    int n;
};

__device__ __forceinline__ float load_state(const States& s, int i, int b, int d, int dim) {
    const int64_t o = (int64_t) b * dim + d;
    return (s.f32mask >> i & 1) ? ((const float*) s.p[i])[o] : __bfloat162float(((const bf16*) s.p[i])[o]);
}

/**
 * Softmax-weighted read of up to 6 states (rows [nb, dim]) by nq queries [nq, dim] (float32): the states are normalized
 * (unweighted RMS, eps 1e-6), scores are dot(normalized, q) * dim^-0.5, the output is the weighted sum of the normalized
 * states, float32 [nb, nq, dim]. One block per row.
 */
__global__ void attnres_kernel(States st, const float* __restrict__ q, float* __restrict__ out, int dim, int nq) {
    const int b = blockIdx.x;
    float acc[6][5];
#pragma unroll
    for (int i = 0; i < 6; i++)
#pragma unroll
        for (int j = 0; j < 5; j++) acc[i][j] = 0.f;
    for (int d = threadIdx.x; d < dim; d += blockDim.x) {
#pragma unroll
        for (int i = 0; i < 6; i++) {
            if (i < st.n) {
                const float x = load_state(st, i, b, d, dim);
                acc[i][0] += x * x;
#pragma unroll
                for (int j = 0; j < 4; j++) {
                    if (j < nq) acc[i][1 + j] += x * q[j * dim + d];
                }
            }
        }
    }
    __shared__ float red[30][32];
    __shared__ float w[6][4];
    __shared__ float rstd[6];
    const int warp = threadIdx.x >> 5;
    const int warps = blockDim.x >> 5;
#pragma unroll
    for (int i = 0; i < 6; i++)
#pragma unroll
        for (int j = 0; j < 5; j++) {
            const float v = warp_sum(acc[i][j]);
            if ((threadIdx.x & 31) == 0) red[i * 5 + j][warp] = v;
        }
    __syncthreads();
    if (threadIdx.x < 30) {
        float t = 0.f;
        for (int k = 0; k < warps; k++) t += red[threadIdx.x][k];
        red[threadIdx.x][0] = t;
    }
    __syncthreads();
    if (threadIdx.x < 4) {
        const int j = threadIdx.x;
        if (j < nq) {
            float logit[6];
            float m = -FLT_MAX;
            for (int i = 0; i < st.n; i++) {
                const float r = rsqrtf(red[i * 5][0] / (float) dim + 1e-6f);
                logit[i] = red[i * 5 + 1 + j][0] * r * rsqrtf((float) dim);
                m = fmaxf(m, logit[i]);
            }
            float s = 0.f;
            for (int i = 0; i < st.n; i++) {
                logit[i] = __expf(logit[i] - m);
                s += logit[i];
            }
            for (int i = 0; i < st.n; i++) w[i][j] = logit[i] / s;
        }
        if (j == 0) {
            for (int i = 0; i < st.n; i++) rstd[i] = rsqrtf(red[i * 5][0] / (float) dim + 1e-6f);
        }
    }
    __syncthreads();
    for (int d = threadIdx.x; d < dim; d += blockDim.x) {
        float o[4] = {0.f, 0.f, 0.f, 0.f};
        for (int i = 0; i < st.n; i++) {
            const float x = load_state(st, i, b, d, dim) * rstd[i];
#pragma unroll
            for (int j = 0; j < 4; j++) o[j] += w[i][j] * x;
        }
        for (int j = 0; j < nq; j++) out[((int64_t) b * nq + j) * dim + d] = o[j];
    }
}

// ------------------------------------------------------------------------------------------------ RMSNorm

struct RmsArgs {
    const float* x[3];          // rows [nb, dim] float32 (or bf16 if xbf16)
    const float* w[3];          // [dim] float32
    bf16* y[3];                 // [nb, dim]
    float* y32[3];              // optional float32 copy of the output, or null
    int n;
};

/** y = bf16(w * x * rsqrt(mean(x^2) + eps)); grid (nb, n). Input float32 rows of stride row_stride[sel]. */
__global__ void rms_kernel(RmsArgs a, int dim, float eps, long row_stride, long sel_stride, int xbf16) {
    const int b = blockIdx.x, s = blockIdx.y;
    __shared__ float red[32];
    const int64_t o = (int64_t) b * row_stride + (int64_t) s * sel_stride;
    float ss = 0.f;
    for (int d = threadIdx.x; d < dim; d += blockDim.x) {
        const float v = xbf16 ? __bfloat162float(((const bf16*) a.x[s])[o + d]) : a.x[s][o + d];
        ss += v * v;
    }
    ss = block_sum(ss, red);
    const float r = rsqrtf(ss / (float) dim + eps);
    for (int d = threadIdx.x; d < dim; d += blockDim.x) {
        const float v = xbf16 ? __bfloat162float(((const bf16*) a.x[s])[o + d]) : a.x[s][o + d];
        const float y = a.w[s][d] * (v * r);
        a.y[s][(int64_t) b * dim + d] = __float2bfloat16(y);
        if (a.y32[s]) a.y32[s][(int64_t) b * dim + d] = rnd(y);
    }
}

// ------------------------------------------------------------------------------------------------ message gates

struct GateArgs {
    const float* hf[3];         // destination states [nb, dim] float32 (row stride hstride)
    const float* mf[3];         // messages [nb, dim] float32 (row stride mstride)
    const bf16* down[3];        // [rank, 2 * dim]
    const float* fw[3];         // forget [dim, rank]
    const float* gw[3];         // gain [dim, rank]
    float* out[3];              // [nb, dim] float32
    float* gd[3];               // scratch [nb, rank]
    int n;
};

/** gd = bf16(down . bf16([rms(hf), rms(mf)])): one block per (row of down, request, gate); grid (rank, nb, n), block 256. */
__global__ void gate_down_kernel(GateArgs a, int dim, int rank, float eps, long hstride, long mstride) {
    const int r = blockIdx.x, b = blockIdx.y, g = blockIdx.z;
    const float* hf = a.hf[g] + (int64_t) b * hstride;
    const float* mf = a.mf[g] + (int64_t) b * mstride;
    __shared__ float red[32];
    float s1 = 0.f, s2 = 0.f;
    for (int d = threadIdx.x; d < dim; d += blockDim.x) {
        s1 += hf[d] * hf[d];
        s2 += mf[d] * mf[d];
    }
    const float r1 = rsqrtf(block_sum(s1, red) / (float) dim + eps);
    const float r2 = rsqrtf(block_sum(s2, red) / (float) dim + eps);
    const bf16* wr = a.down[g] + (int64_t) r * 2 * dim;
    float acc = 0.f;
    for (int d = threadIdx.x * 2; d < dim; d += blockDim.x * 2) {
        const __nv_bfloat162 w1 = *reinterpret_cast<const __nv_bfloat162*>(wr + d);
        const __nv_bfloat162 w2 = *reinterpret_cast<const __nv_bfloat162*>(wr + dim + d);
        acc += rnd(hf[d] * r1) * __low2float(w1) + rnd(hf[d + 1] * r1) * __high2float(w1);
        acc += rnd(mf[d] * r2) * __low2float(w2) + rnd(mf[d + 1] * r2) * __high2float(w2);
    }
    acc = block_sum(acc, red);
    if (threadIdx.x == 0) a.gd[g][(int64_t) b * rank + r] = rnd(acc);
}

/** out = (1 - tanh(forget . gd)) * hf + tanh(gain . gd) * mf: one warp per output element; grid (nb, dim / 8, n), block 256. */
__global__ void gate_apply_kernel(GateArgs a, int dim, int rank, long hstride, long mstride) {
    const int b = blockIdx.x, g = blockIdx.z;
    const int warp = threadIdx.x >> 5, lane = threadIdx.x & 31;
    const int d = blockIdx.y * 8 + warp;
    if (d >= dim) return;
    const float* gd = a.gd[g] + (int64_t) b * rank;
    const float* f = a.fw[g] + (int64_t) d * rank;
    const float* gn = a.gw[g] + (int64_t) d * rank;
    float sf = 0.f, sg = 0.f;
    for (int k = lane * 2; k < rank; k += 64) {
        const float2 x = *reinterpret_cast<const float2*>(gd + k);
        const float2 fv = *reinterpret_cast<const float2*>(f + k);
        const float2 gv = *reinterpret_cast<const float2*>(gn + k);
        sf += fv.x * x.x + fv.y * x.y;
        sg += gv.x * x.x + gv.y * x.y;
    }
    sf = warp_sum(sf);
    sg = warp_sum(sg);
    if (lane == 0) {
        const float h = a.hf[g][(int64_t) b * hstride + d];
        const float m = a.mf[g][(int64_t) b * mstride + d];
        a.out[g][(int64_t) b * dim + d] = (1.f - tanhf(sf)) * h + tanhf(sg) * m;
    }
}

// ------------------------------------------------------------------------------------------------ AttnRes + RMSNorm

struct RmsOut {
    const float* w[4];
    bf16* y[4];
    float* y32[4];
};

/**
 * AttnRes read followed by the weighted RMSNorm of every query's result, in one launch: the normalized states are
 * mixed by softmax weights, then each of the NQ results is normalized into bf16 (and a float32 copy of the rounded
 * result). N states of {bf16, float32} rows, one block per request (1024 threads).
 */
template <int N, int NQ>
__global__ void attnres_rms_kernel(States st, const float* __restrict__ q, float* __restrict__ out, RmsOut ro, int dim) {
    const int b = blockIdx.x;
    float acc[N][NQ + 1];
#pragma unroll
    for (int i = 0; i < N; i++)
#pragma unroll
        for (int j = 0; j < NQ + 1; j++) acc[i][j] = 0.f;
    for (int d = threadIdx.x; d < dim; d += blockDim.x) {
        float qv[NQ];
#pragma unroll
        for (int j = 0; j < NQ; j++) qv[j] = q[j * dim + d];
#pragma unroll
        for (int i = 0; i < N; i++) {
            const float x = load_state(st, i, b, d, dim);
            acc[i][0] += x * x;
#pragma unroll
            for (int j = 0; j < NQ; j++) acc[i][1 + j] += x * qv[j];
        }
    }
    __shared__ float red[N * (NQ + 1)][32];
    __shared__ float tot[N * (NQ + 1)];
    extern __shared__ float rows[];            // [NQ][dim]
    const int warp = threadIdx.x >> 5, lane = threadIdx.x & 31, warps = blockDim.x >> 5;
#pragma unroll
    for (int i = 0; i < N; i++)
#pragma unroll
        for (int j = 0; j < NQ + 1; j++) {
            const float v = warp_sum(acc[i][j]);
            if (lane == 0) red[i * (NQ + 1) + j][warp] = v;
        }
    __syncthreads();
    if (threadIdx.x < N * (NQ + 1)) {
        float t = 0.f;
        for (int k = 0; k < warps; k++) t += red[threadIdx.x][k];
        tot[threadIdx.x] = t;
    }
    __syncthreads();
    float rstd[N], w[N][NQ];
#pragma unroll
    for (int i = 0; i < N; i++) rstd[i] = rsqrtf(tot[i * (NQ + 1)] / (float) dim + 1e-6f);
#pragma unroll
    for (int j = 0; j < NQ; j++) {
        float m = -FLT_MAX;
#pragma unroll
        for (int i = 0; i < N; i++) {
            w[i][j] = tot[i * (NQ + 1) + 1 + j] * rstd[i] * rsqrtf((float) dim);
            m = fmaxf(m, w[i][j]);
        }
        float sum = 0.f;
#pragma unroll
        for (int i = 0; i < N; i++) { w[i][j] = __expf(w[i][j] - m); sum += w[i][j]; }
#pragma unroll
        for (int i = 0; i < N; i++) w[i][j] /= sum;
    }
    float ss[NQ];
#pragma unroll
    for (int j = 0; j < NQ; j++) ss[j] = 0.f;
    for (int d = threadIdx.x; d < dim; d += blockDim.x) {
        float o[NQ];
#pragma unroll
        for (int j = 0; j < NQ; j++) o[j] = 0.f;
#pragma unroll
        for (int i = 0; i < N; i++) {
            const float x = load_state(st, i, b, d, dim) * rstd[i];
#pragma unroll
            for (int j = 0; j < NQ; j++) o[j] += w[i][j] * x;
        }
#pragma unroll
        for (int j = 0; j < NQ; j++) {
            if (out) out[((int64_t) b * NQ + j) * dim + d] = o[j];
            if (ro.y[0]) rows[j * dim + d] = o[j];
            ss[j] += o[j] * o[j];
        }
    }
    if (!ro.y[0]) return;
    __syncthreads();
#pragma unroll
    for (int j = 0; j < NQ; j++) {
        const float v = warp_sum(ss[j]);
        if (lane == 0) red[j][warp] = v;
    }
    __syncthreads();
    if (threadIdx.x < NQ) {
        float t = 0.f;
        for (int k = 0; k < warps; k++) t += red[threadIdx.x][k];
        tot[threadIdx.x] = rsqrtf(t / (float) dim + 1e-6f);
    }
    __syncthreads();
    for (int d = threadIdx.x; d < dim; d += blockDim.x) {
#pragma unroll
        for (int j = 0; j < NQ; j++) {
            const float y = ro.w[j][d] * (rows[j * dim + d] * tot[j]);
            ro.y[j][(int64_t) b * dim + d] = __float2bfloat16(y);
            if (ro.y32[j]) ro.y32[j][(int64_t) b * dim + d] = rnd(y);
        }
    }
}

template <int N, int NQ>
static void launch_attnres_rms(const States& st, const float* q, float* out, const RmsOut& ro, int nb, int dim, cudaStream_t stream) {
    const size_t smem = ro.y[0] ? (size_t) NQ * dim * sizeof(float) : sizeof(float);
    attnres_rms_kernel<N, NQ><<<nb, 1024, smem, stream>>>(st, q, out, ro, dim);
}

template <int NQ>
static bool dispatch_attnres_rms(int n, const States& st, const float* q, float* out, const RmsOut& ro, int nb, int dim, cudaStream_t s) {
    switch (n) {
        case 1: launch_attnres_rms<1, NQ>(st, q, out, ro, nb, dim, s); return true;
        case 2: launch_attnres_rms<2, NQ>(st, q, out, ro, nb, dim, s); return true;
        case 3: launch_attnres_rms<3, NQ>(st, q, out, ro, nb, dim, s); return true;
        case 4: launch_attnres_rms<4, NQ>(st, q, out, ro, nb, dim, s); return true;
        case 5: launch_attnres_rms<5, NQ>(st, q, out, ro, nb, dim, s); return true;
        default: return false;
    }
}

// ------------------------------------------------------------------------------------------------ convolutions

/**
 * The short convolution of a sublayer output. mode 0 (after attention): x = bf16 input; conv = bf16((taps + x) / sqrt(5));
 * residual = boundary ? conv : h_in + conv (float32), and h_in is copied to blk when given. mode 1 (after the MLP):
 * x = bf16(moe float32); out = bf16(residual + bf16(conv)). State [3, dim] float32 per request from the pointer table.
 */
__global__ void conv_kernel(const void* __restrict__ xin, const bf16* __restrict__ hin, const float* __restrict__ w,
                            const int64_t* __restrict__ tbl, int entry, float* __restrict__ res, bf16* __restrict__ blk,
                            bf16* __restrict__ out, int dim, int boundary, int mode, int T) {
    const int b = blockIdx.x;
    const int c = blockIdx.y * blockDim.x + threadIdx.x;
    if (c >= dim) return;
    float* state = (float*) tbl[(int64_t) b * 8 + entry];
    auto input = [&](int row) {
        return mode == 0 ? __bfloat162float(((const bf16*) xin)[(int64_t) row * dim + c])
                         : rnd(((const float*) xin)[(int64_t) row * dim + c]);
    };
    float x = input(b);
    float s0, s1, s2;
    if (T > 1) {
        // verify: the T rows of a request are consecutive; earlier rows' inputs replace the state, which is only
        // updated by the commit kernel (state rows 3.. hold the inputs of this chunk)
        const int j = b % T;
        const int b0 = b - j;
        s0 = j >= 3 ? input(b0 + j - 3) : state[j * dim + c];
        s1 = j >= 2 ? input(b0 + j - 2) : state[(j + 1) * dim + c];
        s2 = j >= 1 ? input(b0 + j - 1) : state[(j + 2) * dim + c];
        state[(3 + j) * (int64_t) dim + c] = x;
    } else {
        s0 = state[c]; s1 = state[dim + c]; s2 = state[2 * dim + c];
    }
    const float* wc = w + (int64_t) c * 4;
    const float acc = wc[0] * s0 + wc[1] * s1 + wc[2] * s2 + wc[3] * x;
    const float conv = rnd((acc + x) * 0.4472135954999579f);       // 1 / sqrt(5)
    if (T == 1) {
        state[c] = s1;
        state[dim + c] = s2;
        state[2 * dim + c] = x;
    }
    if (mode == 0) {
        const float h = __bfloat162float(hin[(int64_t) b * dim + c]);
        res[(int64_t) b * dim + c] = boundary ? conv : h + conv;
        if (blk) blk[(int64_t) b * dim + c] = hin[(int64_t) b * dim + c];
    } else {
        out[(int64_t) b * dim + c] = __float2bfloat16(res[(int64_t) b * dim + c] + conv);
    }
}

/**
 * Rolls the convolution states forward after a verify: state[i] = ext[accepted + i] for i < 3, where ext is the state
 * (rows 0..2) followed by the T inputs of the chunk. Grid (apps, requests, 4), one pass per channel in ascending row order
 * (a source row is never below its destination row, so the in-place copy is safe).
 */
__global__ void commit_kernel(const int64_t* __restrict__ tbl, const int* __restrict__ accepted, int nbRows, int T, int dim) {
    const int app = blockIdx.x, req = blockIdx.y, kind = blockIdx.z;
    const int a = accepted[req];
    if (a <= 0) return;
    const int chan = kind < 2 ? 1024 : dim;
    float* state = (float*) tbl[((int64_t) app * nbRows + (int64_t) req * T) * 8 + 2 + kind];
    for (int c = threadIdx.x; c < chan; c += blockDim.x) {
        const float v0 = state[(int64_t) a * chan + c];
        const float v1 = state[(int64_t) (a + 1) * chan + c];
        const float v2 = state[(int64_t) (a + 2) * chan + c];
        state[c] = v0;
        state[chan + c] = v1;
        state[2 * chan + c] = v2;
    }
}

// ------------------------------------------------------------------------------------------------ attention prep

struct PrepArgs {
    const bf16* yq; const bf16* yk; const bf16* yv;
    long ldq, ldk, ldv;
    int off_q, off_rel, off_sink, off_k, off_kproj, off_v, off_vproj;
    const float* qscale; const float* relscale;
    const float* kw; const float* vw;        // local: conv [1024, 4]; global: static [4, 1024]
    const int64_t* tbl; const int* pos;
    bf16* qn; float* rel; float* sink;
    int global, nfloor, T;
    float qclip, relclip, eps, alpha;
};

/** Short convolution of one key/value channel (static taps, or dynamic taps by lag for global layers). */
__device__ __forceinline__ float kv_conv(const PrepArgs& a, const bf16* y, long ld, int off_x, int off_proj, const float* w, float* state,
                                         int b, int h, int c, float x, int global) {
    float s0, s1, s2;
    if (a.T > 1) {
        const int j = b % a.T;
        const int b0 = b - j;
        s0 = j >= 3 ? __bfloat162float(y[(int64_t) (b0 + j - 3) * ld + off_x + c]) : state[j * 1024 + c];
        s1 = j >= 2 ? __bfloat162float(y[(int64_t) (b0 + j - 2) * ld + off_x + c]) : state[(j + 1) * 1024 + c];
        s2 = j >= 1 ? __bfloat162float(y[(int64_t) (b0 + j - 1) * ld + off_x + c]) : state[(j + 2) * 1024 + c];
        state[(3 + j) * 1024 + c] = x;
    } else {
        s0 = state[c]; s1 = state[1024 + c]; s2 = state[2048 + c];
    }
    float acc, scale;
    if (!global) {
        const float* wc = w + (int64_t) c * 4;
        acc = wc[0] * s0 + wc[1] * s1 + wc[2] * s2 + wc[3] * x;
        scale = 0.4472135954999579f;         // 1 / sqrt(5)
    } else {
        float d[4];
#pragma unroll
        for (int j = 0; j < 4; j++) d[j] = __bfloat162float(y[(int64_t) b * ld + off_proj + h * 4 + j]);
        acc = (w[c] + d[0]) * x + (w[1024 + c] + d[1]) * s2 + (w[2048 + c] + d[2]) * s1 + (w[3072 + c] + d[3]) * s0;
        scale = 0.3333333333333333f;         // 1 / sqrt(9)
    }
    if (a.T == 1) {
        state[c] = s1;
        state[1024 + c] = s2;
        state[2048 + c] = x;
    }
    return rnd((acc + x) * scale);
}

/**
 * q (scale, L2 clip, log-length scale), the relative-bias state and the sink logit of every head, and the new key/value
 * rows of every KV head (short convolution, L2 clip / RMS, cache write). Grid (nb, 24), block 128.
 */
__global__ void prep_kernel(PrepArgs a) {
    const int b = blockIdx.x, h = blockIdx.y, t = threadIdx.x;
    __shared__ float red[32];
    const int pos = a.pos[b];
    float tau = 1.f;
    if (a.global) {
        const float ratio = fmaxf((float) (pos + 1) / (float) a.nfloor, 1.f);
        tau = 1.f + a.alpha * logf(ratio);
    }
    // query
    {
        const float x = __bfloat162float(a.yq[(int64_t) b * a.ldq + a.off_q + h * 128 + t]) * a.qscale[h * 128 + t];
        const float n = sqrtf(block_sum(x * x, red));
        float q = rnd(x * fminf(1.f, a.qclip / (n + 1e-6f)));
        if (a.global) q = rnd(q * tau);
        a.qn[(int64_t) b * 3072 + h * 128 + t] = __float2bfloat16(q);
    }
    // relative-bias state and sink
    if (t < 32) {
        float r = 0.f;
        if (t < 16) r = __bfloat162float(a.yq[(int64_t) b * a.ldq + a.off_rel + h * 16 + t]) * a.relscale[h * 16 + t];
        const float n = sqrtf(warp_sum(r * r));
        if (t < 16) {
            float v = rnd(r * fminf(1.f, a.relclip / (n + 1e-6f)));
            if (a.global) v = rnd(v * tau);
            a.rel[(int64_t) b * 384 + h * 16 + t] = v;
        }
        if (t == 0) a.sink[(int64_t) b * 24 + h] = __bfloat162float(a.yq[(int64_t) b * a.ldq + a.off_sink + h]);
    }
    if (h >= 8) return;
    // key and value of KV head h
    const int64_t* e = a.tbl + (int64_t) b * 8;
    bf16* kc = (bf16*) e[0];
    bf16* vc = (bf16*) e[1];
    const int cap = (int) e[6];
    const int slot = a.global ? pos : pos % cap;
    const int c = h * 128 + t;
    {
        const float x = __bfloat162float(a.yk[(int64_t) b * a.ldk + a.off_k + c]);
        const float kv = kv_conv(a, a.yk, a.ldk, a.off_k, a.off_kproj, a.kw, (float*) e[2], b, h, c, x, a.global);
        const float n = sqrtf(block_sum(kv * kv, red));
        const float kval = kv * fminf(1.f, a.qclip / (n + 1e-6f));
        if (e[7]) ((__nv_fp8_storage_t*) kc)[((int64_t) h * cap + slot) * 128 + t] = __nv_cvt_float_to_fp8(kval, __NV_SATFINITE, __NV_E4M3);
        else kc[((int64_t) h * cap + slot) * 128 + t] = __float2bfloat16(kval);
    }
    {
        const float x = __bfloat162float(a.yv[(int64_t) b * a.ldv + a.off_v + c]);
        const float vv = kv_conv(a, a.yv, a.ldv, a.off_v, a.off_vproj, a.vw, (float*) e[3], b, h, c, x, a.global);
        const float ms = block_sum(vv * vv, red) / 128.f;
        const float vval = vv * rsqrtf(ms + a.eps);
        if (e[7]) ((__nv_fp8_storage_t*) vc)[((int64_t) h * cap + slot) * 128 + t] = __nv_cvt_float_to_fp8(vval, __NV_SATFINITE, __NV_E4M3);
        else vc[((int64_t) h * cap + slot) * 128 + t] = __float2bfloat16(vval);
    }
}

// ------------------------------------------------------------------------------------------------ attention decode

struct AttnArgs {
    const bf16* qn; const float* rel; const float* sink; const bf16* yq; long ldq; int off_gate;
    const bf16* projT;                       // [1024, 16]
    const int64_t* tbl; const int* pos;
    bf16* out;                               // [nb, 3072]
    float* pm; float* pl; float* pacc;       // partials [nb, 24, nsplit] and [.., 128]
    int window;                              // 0 = global
    int nsplit;
};

__device__ __forceinline__ void load4(const bf16* p, float* f) {
    const uint2 u = *reinterpret_cast<const uint2*>(p);
    const __nv_bfloat162 a = *reinterpret_cast<const __nv_bfloat162*>(&u.x);
    const __nv_bfloat162 c = *reinterpret_cast<const __nv_bfloat162*>(&u.y);
    f[0] = __low2float(a); f[1] = __high2float(a); f[2] = __low2float(c); f[3] = __high2float(c);
}

__device__ __forceinline__ float fp8_to_float(__nv_fp8_storage_t b) {
    const __half_raw hr = __nv_cvt_fp8_to_halfraw(b, __NV_E4M3);
    return __half2float(*reinterpret_cast<const __half*>(&hr));
}

/** 8 consecutive cache elements (bf16 or E4M3) starting at element {@code elem}. */
__device__ __forceinline__ void load8(const bf16* base, int64_t elem, bool fp8, float* f) {
    if (fp8) {
        const uint2 u = *reinterpret_cast<const uint2*>(reinterpret_cast<const uint8_t*>(base) + elem);
        const uint8_t* by = reinterpret_cast<const uint8_t*>(&u);
#pragma unroll
        for (int i = 0; i < 8; i++) f[i] = fp8_to_float(by[i]);
    } else {
        const uint4 u = *reinterpret_cast<const uint4*>(base + elem);
        const __nv_bfloat162* b = reinterpret_cast<const __nv_bfloat162*>(&u);
#pragma unroll
        for (int i = 0; i < 4; i++) { f[2 * i] = __low2float(b[i]); f[2 * i + 1] = __high2float(b[i]); }
    }
}

__device__ __forceinline__ void load4v(const bf16* base, int64_t elem, bool fp8, float* f) {
    if (fp8) {
        const uint32_t u = *reinterpret_cast<const uint32_t*>(reinterpret_cast<const uint8_t*>(base) + elem);
        const uint8_t* by = reinterpret_cast<const uint8_t*>(&u);
#pragma unroll
        for (int i = 0; i < 4; i++) f[i] = fp8_to_float(by[i]);
    } else {
        load4(base + elem, f);
    }
}

/**
 * Decode attention of one KV head (3 query heads) over a range of keys, with the relative bias, in an online softmax.
 * Grid (nb, 8, nsplit), block 128 (4 warps; a lane owns 4 of the 128 head dims). With nsplit 1 the sink, the output gate
 * and the bf16 output are written directly; otherwise partial results go to the merge kernel.
 */
__global__ void attn_kernel(AttnArgs a) {
    const int b = blockIdx.x, g = blockIdx.y, z = blockIdx.z;
    const int warp = threadIdx.x >> 5, lane = threadIdx.x & 31;
    const int pos = a.pos[b];
    const int64_t* e = a.tbl + (int64_t) b * 8;
    const bf16* kc = (const bf16*) e[0];
    const bf16* vc = (const bf16*) e[1];
    const int cap = (int) e[6];
    const int lo = a.window ? max(0, pos - a.window + 1) : 0;
    const int n = pos + 1 - lo;
    const int chunk = (n + a.nsplit - 1) / a.nsplit;
    const int j0 = lo + z * chunk, j1 = min(pos + 1, j0 + chunk);
    const bool fp8 = e[7] != 0;
    float m[3] = {-1e30f, -1e30f, -1e30f}, l[3] = {0.f, 0.f, 0.f}, acc[3][4] = {};
    if (j1 - j0 <= 96) {
        // short range: one key per warp step, the 32 lanes share its dimensions (little latency chain per key)
        float q[3][4], rel[3][16];
#pragma unroll
        for (int hh = 0; hh < 3; hh++) {
            load4(a.qn + (int64_t) b * 3072 + (3 * g + hh) * 128 + lane * 4, q[hh]);
#pragma unroll
            for (int d = 0; d < 16; d++) rel[hh][d] = a.rel[(int64_t) b * 384 + (3 * g + hh) * 16 + d];
        }
        constexpr int U = 4;
        for (int jb = j0 + warp; jb < j1; jb += 4 * U) {
            float kf[U][4], vf[U][4], pw[U][16], dot[U][3];
            bool live[U];
#pragma unroll
            for (int u = 0; u < U; u++) {
                const int j = jb + 4 * u;
                live[u] = j < j1;
                if (live[u]) {
                    const int slot = a.window ? j % cap : j;
                    load4v(kc, ((int64_t) g * cap + slot) * 128 + lane * 4, fp8, kf[u]);
                    load4v(vc, ((int64_t) g * cap + slot) * 128 + lane * 4, fp8, vf[u]);
                    const int dist = pos - j;
                    if (dist < 1024) {
                        const uint4* pp = reinterpret_cast<const uint4*>(a.projT + (int64_t) dist * 16);
                        const uint4 u0 = pp[0], u1 = pp[1];
                        const __nv_bfloat162* b0 = reinterpret_cast<const __nv_bfloat162*>(&u0);
                        const __nv_bfloat162* b1 = reinterpret_cast<const __nv_bfloat162*>(&u1);
#pragma unroll
                        for (int d = 0; d < 4; d++) {
                            pw[u][2 * d] = __low2float(b0[d]); pw[u][2 * d + 1] = __high2float(b0[d]);
                            pw[u][8 + 2 * d] = __low2float(b1[d]); pw[u][8 + 2 * d + 1] = __high2float(b1[d]);
                        }
                    } else {
#pragma unroll
                        for (int d = 0; d < 16; d++) pw[u][d] = 0.f;
                    }
#pragma unroll
                    for (int hh = 0; hh < 3; hh++) {
                        dot[u][hh] = q[hh][0] * kf[u][0] + q[hh][1] * kf[u][1] + q[hh][2] * kf[u][2] + q[hh][3] * kf[u][3];
                    }
                }
            }
#pragma unroll
            for (int o = 16; o > 0; o >>= 1) {
#pragma unroll
                for (int u = 0; u < U; u++)
#pragma unroll
                    for (int hh = 0; hh < 3; hh++) dot[u][hh] += __shfl_xor_sync(0xffffffffu, dot[u][hh], o);
            }
#pragma unroll
            for (int u = 0; u < U; u++) {
                if (!live[u]) continue;
#pragma unroll
                for (int hh = 0; hh < 3; hh++) {
                    float bias = 0.f;
#pragma unroll
                    for (int d = 0; d < 16; d++) bias += rel[hh][d] * pw[u][d];
                    const float s = dot[u][hh] * 0.08838834764831845f + bias;
                    const float mn = fmaxf(m[hh], s);
                    const float al = __expf(m[hh] - mn), p = __expf(s - mn);
                    l[hh] = l[hh] * al + p;
#pragma unroll
                    for (int i = 0; i < 4; i++) acc[hh][i] = acc[hh][i] * al + p * vf[u][i];
                    m[hh] = mn;
                }
            }
        }
    } else {
        __shared__ float4 q_s[3][32];
        __shared__ float rel_s[3][16];
        if (threadIdx.x < 96) {
            const int hh = threadIdx.x >> 5, c = threadIdx.x & 31;
            const uint2 u = *reinterpret_cast<const uint2*>(a.qn + (int64_t) b * 3072 + (3 * g + hh) * 128 + c * 4);
            const __nv_bfloat162 x0 = *reinterpret_cast<const __nv_bfloat162*>(&u.x), x1 = *reinterpret_cast<const __nv_bfloat162*>(&u.y);
            q_s[hh][c] = make_float4(__low2float(x0), __high2float(x0), __low2float(x1), __high2float(x1));
        }
        if (threadIdx.x < 48) rel_s[threadIdx.x >> 4][threadIdx.x & 15] = a.rel[(int64_t) b * 384 + (3 * g) * 16 + threadIdx.x];
        __syncthreads();
        // a warp scores 32 consecutive keys at a time, one key per lane, then accumulates the values lane-per-dimension
        for (int tb = j0 + warp * 32; tb < j1; tb += 128) {
            const int j = tb + lane;
            float s[3] = {-1e30f, -1e30f, -1e30f};
            if (j < j1) {
                const int slot = a.window ? j % cap : j;
                const int64_t row = ((int64_t) g * cap + slot) * 128;
                float d[3] = {0.f, 0.f, 0.f};
                uint4 raw[16];                      // all loads first: 16 requests in flight per lane
    #pragma unroll
                for (int c = 0; c < 16; c++) {
                    if (fp8) {
                        const uint2 u = *reinterpret_cast<const uint2*>(reinterpret_cast<const uint8_t*>(kc) + row + c * 8);
                        raw[c] = make_uint4(u.x, u.y, 0u, 0u);
                    } else {
                        raw[c] = *reinterpret_cast<const uint4*>(kc + row + c * 8);
                    }
                }
    #pragma unroll
                for (int c = 0; c < 16; c++) {
                    float f[8];
                    if (fp8) {
                        const uint8_t* by = reinterpret_cast<const uint8_t*>(&raw[c]);
    #pragma unroll
                        for (int i = 0; i < 8; i++) f[i] = fp8_to_float(by[i]);
                    } else {
                        const __nv_bfloat162* bb = reinterpret_cast<const __nv_bfloat162*>(&raw[c]);
    #pragma unroll
                        for (int i = 0; i < 4; i++) { f[2 * i] = __low2float(bb[i]); f[2 * i + 1] = __high2float(bb[i]); }
                    }
    #pragma unroll
                    for (int hh = 0; hh < 3; hh++) {
                        const float4 qa = q_s[hh][2 * c], qb = q_s[hh][2 * c + 1];
                        d[hh] += qa.x * f[0] + qa.y * f[1] + qa.z * f[2] + qa.w * f[3] + qb.x * f[4] + qb.y * f[5] + qb.z * f[6] + qb.w * f[7];
                    }
                }
                const int dist = pos - j;
                float pw[16];
                const bool near = dist < 1024;
                if (near) {
                    const uint4* pp = reinterpret_cast<const uint4*>(a.projT + (int64_t) dist * 16);
                    const uint4 u0 = pp[0], u1 = pp[1];
                    const __nv_bfloat162* b0 = reinterpret_cast<const __nv_bfloat162*>(&u0);
                    const __nv_bfloat162* b1 = reinterpret_cast<const __nv_bfloat162*>(&u1);
    #pragma unroll
                    for (int i = 0; i < 4; i++) {
                        pw[2 * i] = __low2float(b0[i]); pw[2 * i + 1] = __high2float(b0[i]);
                        pw[8 + 2 * i] = __low2float(b1[i]); pw[8 + 2 * i + 1] = __high2float(b1[i]);
                    }
                }
    #pragma unroll
                for (int hh = 0; hh < 3; hh++) {
                    float bias = 0.f;
                    if (near) {
    #pragma unroll
                        for (int i = 0; i < 16; i++) bias += rel_s[hh][i] * pw[i];
                    }
                    s[hh] = d[hh] * 0.08838834764831845f + bias;
                }
            }
            float p[3];
    #pragma unroll
            for (int hh = 0; hh < 3; hh++) {
                const float mn = fmaxf(m[hh], warp_max(s[hh]));
                const float al = __expf(m[hh] - mn);
                p[hh] = __expf(s[hh] - mn);
                l[hh] = l[hh] * al + warp_sum(p[hh]);
    #pragma unroll
                for (int i = 0; i < 4; i++) acc[hh][i] *= al;
                m[hh] = mn;
            }
            // value rows in groups of 8 so that the loads of a group are in flight together (invalid keys have p = 0)
            const int last = j1 - 1;
    #pragma unroll 2
            for (int kb = 0; kb < 32; kb += 8) {
                if (tb + kb >= j1) break;
                float vf[8][4];
    #pragma unroll
                for (int u = 0; u < 8; u++) {
                    const int jk = min(tb + kb + u, last);
                    const int slot = a.window ? jk % cap : jk;
                    load4v(vc, ((int64_t) g * cap + slot) * 128 + lane * 4, fp8, vf[u]);
                }
    #pragma unroll
                for (int u = 0; u < 8; u++) {
    #pragma unroll
                    for (int hh = 0; hh < 3; hh++) {
                        const float pk = __shfl_sync(0xffffffffu, p[hh], kb + u);
    #pragma unroll
                        for (int i = 0; i < 4; i++) acc[hh][i] += pk * vf[u][i];
                    }
                }
            }
        }
    }
    // merge the 4 warps
    __shared__ float sm_m[4][3], sm_l[4][3];
    __shared__ float sm_a[4][3][128];
    if (lane == 0) {
#pragma unroll
        for (int hh = 0; hh < 3; hh++) { sm_m[warp][hh] = m[hh]; sm_l[warp][hh] = l[hh]; }
    }
#pragma unroll
    for (int hh = 0; hh < 3; hh++)
#pragma unroll
        for (int i = 0; i < 4; i++) sm_a[warp][hh][lane * 4 + i] = acc[hh][i];
    __syncthreads();
    const int t = threadIdx.x;
#pragma unroll
    for (int hh = 0; hh < 3; hh++) {
        float M = fmaxf(fmaxf(sm_m[0][hh], sm_m[1][hh]), fmaxf(sm_m[2][hh], sm_m[3][hh]));
        float L = 0.f, A = 0.f;
#pragma unroll
        for (int w = 0; w < 4; w++) {
            const float f = __expf(sm_m[w][hh] - M);
            L += sm_l[w][hh] * f;
            A += sm_a[w][hh][t] * f;
        }
        const int hq = 3 * g + hh;
        if (a.nsplit == 1) {
            const float sink = a.sink[(int64_t) b * 24 + hq];
            const float Ms = fmaxf(M, sink);
            const float f = __expf(M - Ms);
            const float L2 = L * f + __expf(sink - Ms);
            const float o = rnd(A * f / L2);
            const float gate = 1.f / (1.f + __expf(-__bfloat162float(a.yq[(int64_t) b * a.ldq + a.off_gate + hq * 128 + t])));
            a.out[(int64_t) b * 3072 + hq * 128 + t] = __float2bfloat16(o * gate);
        } else {
            const int64_t idx = ((int64_t) b * 24 + hq) * a.nsplit + z;
            if (t == 0) { a.pm[idx] = M; a.pl[idx] = L; }
            a.pacc[idx * 128 + t] = A;
        }
    }
}

/** Combines the split partials, adds the sink, applies the output gate. Grid (nb, 24), block 128. */
__global__ void merge_kernel(AttnArgs a) {
    const int b = blockIdx.x, hq = blockIdx.y, t = threadIdx.x;
    const int64_t base = ((int64_t) b * 24 + hq) * a.nsplit;
    float M = -1e30f;
    for (int z = 0; z < a.nsplit; z++) M = fmaxf(M, a.pm[base + z]);
    float L = 0.f, A = 0.f;
    for (int z = 0; z < a.nsplit; z++) {
        const float f = __expf(a.pm[base + z] - M);
        L += a.pl[base + z] * f;
        A += a.pacc[(base + z) * 128 + t] * f;
    }
    const float sink = a.sink[(int64_t) b * 24 + hq];
    const float Ms = fmaxf(M, sink);
    const float f = __expf(M - Ms);
    const float L2 = L * f + __expf(sink - Ms);
    const float o = rnd(A * f / L2);
    const float gate = 1.f / (1.f + __expf(-__bfloat162float(a.yq[(int64_t) b * a.ldq + a.off_gate + hq * 128 + t])));
    a.out[(int64_t) b * 3072 + hq * 128 + t] = __float2bfloat16(o * gate);
}

// ------------------------------------------------------------------------------------------------ router, MoE activation, embedding, head

/**
 * Sigmoid scores, top-k of score + bias, renormalized weights times scale. One warp per row (each lane holds
 * experts/32 of them); the k selections are k warp-wide arg-max reductions, no block synchronization.
 */
template <int TOPK>
__global__ void route_kernel(const float* __restrict__ logits, const float* __restrict__ bias, int* __restrict__ idx,
                             float* __restrict__ wts, int experts, float scale) {
    const int b = blockIdx.x, lane = threadIdx.x;
    constexpr int PER = 4;                       // experts per lane (<= 128 experts)
    float sc[PER], sel[PER];
#pragma unroll
    for (int k = 0; k < PER; k++) {
        const int e = lane + 32 * k;
        if (e < experts) {
            sc[k] = 1.f / (1.f + __expf(-logits[(int64_t) b * experts + e]));
            sel[k] = sc[k] + bias[e];
        } else {
            sc[k] = 0.f;
            sel[k] = -FLT_MAX;
        }
    }
    int chosen[TOPK];
    float cw[TOPK];
    float sum = 0.f;
#pragma unroll
    for (int r = 0; r < TOPK; r++) {
        float v = sel[0];
        int i = lane;
#pragma unroll
        for (int k = 1; k < PER; k++) {
            if (sel[k] > v) { v = sel[k]; i = lane + 32 * k; }
        }
        float bv = v;
        int bi = i;
#pragma unroll
        for (int o = 16; o > 0; o >>= 1) {
            const float ov = __shfl_xor_sync(0xffffffffu, bv, o);
            const int oi = __shfl_xor_sync(0xffffffffu, bi, o);
            if (ov > bv || (ov == bv && oi < bi)) { bv = ov; bi = oi; }
        }
        // the owner clears its entry and contributes its score
        float s = 0.f;
#pragma unroll
        for (int k = 0; k < PER; k++) {
            if (lane + 32 * k == bi) { s = sc[k]; sel[k] = -FLT_MAX; }
        }
        s = warp_sum(s);
        chosen[r] = bi;
        cw[r] = s;
        sum += s;
    }
    if (lane == 0) {
#pragma unroll
        for (int r = 0; r < TOPK; r++) {
            idx[(int64_t) b * TOPK + r] = chosen[r];
            wts[(int64_t) b * TOPK + r] = cw[r] / (sum + 1e-20f) * scale;
        }
    }
}

/** Softcapped SwiGLU on interleaved gate/up columns: bf16 [M, 2I] -> [M, I]. */
__global__ void moe_act_kernel(const bf16* __restrict__ gu, bf16* __restrict__ out, long total, int inter, float gcap, float ucap) {
    const long i = (long) blockIdx.x * blockDim.x + threadIdx.x;
    if (i >= total) return;
    const long row = i / inter, col = i % inter;
    const float g = __bfloat162float(gu[row * 2 * inter + 2 * col]);
    const float u = __bfloat162float(gu[row * 2 * inter + 2 * col + 1]);
    const float act = gcap * tanhf(g / gcap) * (1.f / (1.f + __expf(-g))) * (ucap * tanhf(u / ucap));
    out[i] = __float2bfloat16(act);
}

/**
 * moe_align for a few tokens: counts, an exclusive scan over the padded counts and the scatter in one block of one thread
 * per expert; the order within an expert is the order of the ids (deterministic). Same output layout as the generic
 * kernel: sorted ids (padding = numel), the expert of every block (-1 padding), the padded length.
 */
__global__ void align_decode_kernel(const int32_t* __restrict__ ids, int numel, int experts, int block, int padded,
                                    int blocks, int32_t* __restrict__ sorted, int32_t* __restrict__ expert_ids,
                                    int32_t* __restrict__ post) {
    __shared__ int cnt[1024], start[1024];
    const int e = threadIdx.x;
    for (int i = e; i < padded; i += blockDim.x) sorted[i] = numel;
    for (int i = e; i < blocks; i += blockDim.x) expert_ids[i] = -1;
    int c = 0;
    if (e < experts) {
        for (int i = 0; i < numel; i++) c += ids[i] == e;
    }
    const int pc = (c + block - 1) / block * block;
    cnt[e] = pc;
    __syncthreads();
    // exclusive scan by thread 0..: a short serial loop over warps is enough for <= 1024 experts
    if (e < 32) {
        int run = 0;
        const int per = (blockDim.x + 31) / 32;
        int mine = 0;
        for (int i = e * per; i < min(blockDim.x, (e + 1) * per); i++) mine += cnt[i];
        int incl = mine;
        for (int o = 1; o < 32; o <<= 1) {
            const int v = __shfl_up_sync(0xffffffffu, incl, o);
            if (e >= o) incl += v;
        }
        run = incl - mine;
        for (int i = e * per; i < min(blockDim.x, (e + 1) * per); i++) {
            start[i] = run;
            run += cnt[i];
        }
        if (e == 31) post[0] = incl;
    }
    __syncthreads();
    if (e < experts) {
        int off = start[e];
        for (int b = 0; b < pc / block; b++) expert_ids[off / block + b] = e;
        int k = 0;
        for (int i = 0; i < numel; i++) {
            if (ids[i] == e) sorted[off + k++] = i;
        }
    }
}

/** y = bf16(w * rmsnorm(embed[ids[b]])). */
__global__ void embed_kernel(const int64_t* __restrict__ ids, const bf16* __restrict__ table, const float* __restrict__ w,
                             bf16* __restrict__ y, int dim, float eps) {
    const int b = blockIdx.x;
    __shared__ float red[32];
    const bf16* row = table + ids[b] * dim;
    float ss = 0.f;
    for (int d = threadIdx.x; d < dim; d += blockDim.x) { const float v = __bfloat162float(row[d]); ss += v * v; }
    const float r = rsqrtf(block_sum(ss, red) / (float) dim + eps);
    for (int d = threadIdx.x; d < dim; d += blockDim.x) {
        y[(int64_t) b * dim + d] = __float2bfloat16(w[d] * (__bfloat162float(row[d]) * r));
    }
}

/** out = cap * tanh(x / cap), bf16 -> float32. */
__global__ void softcap_kernel(const bf16* __restrict__ x, float* __restrict__ out, long total, float cap, long cols, long ld) {
    const long i = (long) blockIdx.x * blockDim.x + threadIdx.x;
    if (i < total) {
        const long src = ld == cols ? i : (i / cols) * ld + i % cols;
        out[i] = cap * tanhf(__bfloat162float(x[src]) / cap);
    }
}

}  // namespace


// ------------------------------------------------------------------------------------------------ DSpark draft

/**
 * Writes rows of key and value ({@code [R, 1024]} bf16) into the draft's per-request caches {@code [8, cap, 128]} at the
 * given positions. The table row of a request holds {@code stride} addresses: K and V of every layer, then the capacity.
 */
__global__ void draft_scatter_kernel(const bf16* __restrict__ K, const bf16* __restrict__ V, const int64_t* __restrict__ ptrs,
                                     const int* __restrict__ pos, int stride, int layer, int rows_per) {
    const int r = blockIdx.x;
    const int64_t* e = ptrs + (int64_t) (r / rows_per) * stride;
    bf16* kc = (bf16*) e[2 * layer];
    bf16* vc = (bf16*) e[2 * layer + 1];
    const int64_t cap = e[stride - 1];
    const int p = pos[r];
    for (int i = threadIdx.x; i < 1024; i += blockDim.x) {
        const int h = i >> 7, d = i & 127;
        kc[((int64_t) h * cap + p) * 128 + d] = K[(int64_t) r * 1024 + i];
        vc[((int64_t) h * cap + p) * 128 + d] = V[(int64_t) r * 1024 + i];
    }
}

struct DraftAttnArgs {
    const bf16* q;              // [B * 8, 3072]
    const int64_t* ptrs;
    const int* endpos;          // [B]: anchor position + 8
    bf16* out;                  // [B * 8, 3072]
    float* pm; float* pl; float* pacc;     // partials [B, 8, S, 24] and [.., 128], S = 4 * nsplit
    int stride, layer, window, nsplit;
};

/**
 * Non-causal block attention of the draft: the 8 rows of a block (3 query heads each per KV head) read the context keys of
 * the window and the block's own keys. Grid (B, 8, nsplit), block 128: every warp keeps an online softmax over its share of
 * the keys for all 24 (head, row) queries and writes a partial result for the merge kernel.
 */
__global__ void draft_attn_kernel(DraftAttnArgs a) {
    const int b = blockIdx.x, g = blockIdx.y, z = blockIdx.z;
    const int warp = threadIdx.x >> 5, lane = threadIdx.x & 31;
    __shared__ float q[24][128];
    const int64_t* e = a.ptrs + (int64_t) b * a.stride;
    const bf16* kc = (const bf16*) e[2 * a.layer];
    const bf16* vc = (const bf16*) e[2 * a.layer + 1];
    const int64_t cap = e[a.stride - 1];
    for (int i = threadIdx.x; i < 24 * 128; i += 128) {
        const int qi = i >> 7, d = i & 127, c = qi >> 3, r = qi & 7;
        q[qi][d] = __bfloat162float(a.q[(int64_t) (b * 8 + r) * 3072 + (g * 3 + c) * 128 + d]);
    }
    __syncthreads();
    const int end = a.endpos[b];
    const int lo = a.window > 0 ? max(0, end - a.window - 8) : 0;
    const int len = end - lo;
    const int chunk = (len + a.nsplit - 1) / a.nsplit;
    const int k0 = lo + z * chunk, k1 = min(end, k0 + chunk);
    float m[24], l[24], acc[24][4];
#pragma unroll
    for (int i = 0; i < 24; i++) {
        m[i] = -INFINITY; l[i] = 0.f;
        acc[i][0] = acc[i][1] = acc[i][2] = acc[i][3] = 0.f;
    }
    const float scale = 0.08838834764831845f;       // 128^-0.5
    for (int k = k0 + warp; k < k1; k += 4) {
        float kf[4], vf[4];
        const int64_t off = ((int64_t) g * cap + k) * 128 + lane * 4;
        const uint2 uk = *reinterpret_cast<const uint2*>(kc + off);
        const uint2 uv = *reinterpret_cast<const uint2*>(vc + off);
        {
            const __nv_bfloat162 x0 = *reinterpret_cast<const __nv_bfloat162*>(&uk.x), x1 = *reinterpret_cast<const __nv_bfloat162*>(&uk.y);
            kf[0] = __low2float(x0); kf[1] = __high2float(x0); kf[2] = __low2float(x1); kf[3] = __high2float(x1);
            const __nv_bfloat162 y0 = *reinterpret_cast<const __nv_bfloat162*>(&uv.x), y1 = *reinterpret_cast<const __nv_bfloat162*>(&uv.y);
            vf[0] = __low2float(y0); vf[1] = __high2float(y0); vf[2] = __low2float(y1); vf[3] = __high2float(y1);
        }
#pragma unroll
        for (int i = 0; i < 24; i++) {
            float sc = q[i][lane * 4] * kf[0] + q[i][lane * 4 + 1] * kf[1] + q[i][lane * 4 + 2] * kf[2] + q[i][lane * 4 + 3] * kf[3];
            sc = warp_sum(sc) * scale;
            const float mn = fmaxf(m[i], sc);
            const float al = __expf(m[i] - mn), pr = __expf(sc - mn);
            l[i] = l[i] * al + pr;
            acc[i][0] = acc[i][0] * al + pr * vf[0];
            acc[i][1] = acc[i][1] * al + pr * vf[1];
            acc[i][2] = acc[i][2] * al + pr * vf[2];
            acc[i][3] = acc[i][3] * al + pr * vf[3];
            m[i] = mn;
        }
    }
    const int S = 4 * a.nsplit, s = z * 4 + warp;
#pragma unroll
    for (int i = 0; i < 24; i++) {
        const int64_t base = (((int64_t) b * 8 + g) * S + s) * 24 + i;
        if (lane == 0) { a.pm[base] = m[i]; a.pl[base] = l[i]; }
        float* pa = a.pacc + base * 128 + lane * 4;
        pa[0] = acc[i][0]; pa[1] = acc[i][1]; pa[2] = acc[i][2]; pa[3] = acc[i][3];
    }
}

/** Merges the partial softmaxes: grid (B * 8 rows, 24 heads), block 128 (one thread per head dim). */
__global__ void draft_merge_kernel(DraftAttnArgs a) {
    const int b = blockIdx.x >> 3, r = blockIdx.x & 7, h = blockIdx.y, d = threadIdx.x;
    const int g = h / 3, c = h % 3, qi = c * 8 + r, S = 4 * a.nsplit;
    float mx = -INFINITY;
    for (int s = 0; s < S; s++) mx = fmaxf(mx, a.pm[(((int64_t) b * 8 + g) * S + s) * 24 + qi]);
    float den = 0.f, num = 0.f;
    for (int s = 0; s < S; s++) {
        const int64_t base = (((int64_t) b * 8 + g) * S + s) * 24 + qi;
        const float w = __expf(a.pm[base] - mx);
        den += w * a.pl[base];
        num += w * a.pacc[base * 128 + d];
    }
    a.out[(int64_t) blockIdx.x * 3072 + h * 128 + d] = __float2bfloat16(num / den);
}

// ------------------------------------------------------------------------------------------------ dispatcher

enum Op {
    OP_ATTNRES = 1,
    OP_RMS = 2,
    OP_GATE_DOWN = 3,
    OP_GATE_APPLY = 4,
    OP_CONV = 5,
    OP_PREP = 6,
    OP_ATTN = 7,
    OP_MERGE = 8,
    OP_ROUTE = 9,
    OP_MOE_ACT = 10,
    OP_EMBED = 11,
    OP_SOFTCAP = 12,
    OP_ALIGN = 13,
    OP_ATTNRES_RMS = 14,
    OP_COMMIT = 15,
    OP_DRAFT_SCATTER = 16,
    OP_DRAFT_ATTN = 17,
    OP_DRAFT_MERGE = 18,
    OP_PEER = 20,
};

extern "C" __attribute__((visibility("default"))) const char* smile_inkling_error(void) { return g_error.c_str(); }

extern "C" __attribute__((visibility("default"))) int smile_inkling_op(int op, const int64_t* p, int np, const int64_t* iv, int ni, const double* dv, int nd) {
    cudaStream_t stream = c10::cuda::getCurrentCUDAStream().stream();
    switch (op) {
        case OP_ATTNRES: {
            // p: out, q, state0..n-1; iv: nb, nq, dim, n, f32mask
            const int nb = iv[0], nq = iv[1], dim = iv[2], n = iv[3];
            States st;
            std::memset(&st, 0, sizeof(st));
            st.n = n;
            st.f32mask = (int) iv[4];
            for (int i = 0; i < n; i++) st.p[i] = (const void*) p[2 + i];
            attnres_kernel<<<nb, 1024, 0, stream>>>(st, (const float*) p[1], (float*) p[0], dim, nq);
            CHECK_LAUNCH("attnres");
            return 0;
        }
        case OP_RMS: {
            // p: x[0..n), w[0..n), y[0..n), y32[0..n) (0 = none); iv: nb, dim, n, row_stride, sel_stride, xbf16; dv: eps
            const int nb = iv[0], dim = iv[1], n = iv[2];
            RmsArgs a;
            std::memset(&a, 0, sizeof(a));
            a.n = n;
            for (int i = 0; i < n; i++) {
                a.x[i] = (const float*) p[i];
                a.w[i] = (const float*) p[n + i];
                a.y[i] = (bf16*) p[2 * n + i];
                a.y32[i] = (float*) p[3 * n + i];
            }
            rms_kernel<<<dim3(nb, n), 256, 0, stream>>>(a, dim, (float) dv[0], iv[3], iv[4], (int) iv[5]);
            CHECK_LAUNCH("rms");
            return 0;
        }
        case OP_GATE_DOWN:
        case OP_GATE_APPLY: {
            // p: hf[n], mf[n], down[n], fw[n], gw[n], out[n], gd[n]; iv: nb, dim, rank, n, hstride, mstride; dv: eps
            const int nb = iv[0], dim = iv[1], rank = iv[2], n = iv[3];
            GateArgs a;
            std::memset(&a, 0, sizeof(a));
            a.n = n;
            for (int i = 0; i < n; i++) {
                a.hf[i] = (const float*) p[i];
                a.mf[i] = (const float*) p[n + i];
                a.down[i] = (const bf16*) p[2 * n + i];
                a.fw[i] = (const float*) p[3 * n + i];
                a.gw[i] = (const float*) p[4 * n + i];
                a.out[i] = (float*) p[5 * n + i];
                a.gd[i] = (float*) p[6 * n + i];
            }
            if (op == OP_GATE_DOWN) {
                gate_down_kernel<<<dim3(rank, nb, n), 256, 0, stream>>>(a, dim, rank, (float) dv[0], iv[4], iv[5]);
            } else {
                gate_apply_kernel<<<dim3(nb, (dim + 7) / 8, n), 256, 0, stream>>>(a, dim, rank, iv[4], iv[5]);
            }
            CHECK_LAUNCH("gate");
            return 0;
        }
        case OP_CONV: {
            // p: xin, hin, w, tbl, res, blk(0), out; iv: nb, dim, entry, boundary, mode
            const int nb = iv[0], dim = iv[1];
            conv_kernel<<<dim3(nb, (dim + 255) / 256), 256, 0, stream>>>((const void*) p[0], (const bf16*) p[1], (const float*) p[2],
                    (const int64_t*) p[3], (int) iv[2], (float*) p[4], (bf16*) p[5], (bf16*) p[6], dim, (int) iv[3], (int) iv[4], ni > 5 && iv[5] > 1 ? (int) iv[5] : 1);
            CHECK_LAUNCH("conv");
            return 0;
        }
        case OP_PREP: {
            // p: yq, yk, yv, qscale, relscale, kw, vw, tbl, pos, qn, rel, sink
            // iv: nb, ldq, ldk, ldv, off_q, off_rel, off_sink, off_k, off_kproj, off_v, off_vproj, global, nfloor
            // dv: qclip, relclip, eps, alpha
            PrepArgs a;
            std::memset(&a, 0, sizeof(a));
            a.yq = (const bf16*) p[0]; a.yk = (const bf16*) p[1]; a.yv = (const bf16*) p[2];
            a.qscale = (const float*) p[3]; a.relscale = (const float*) p[4];
            a.kw = (const float*) p[5]; a.vw = (const float*) p[6];
            a.tbl = (const int64_t*) p[7]; a.pos = (const int*) p[8];
            a.qn = (bf16*) p[9]; a.rel = (float*) p[10]; a.sink = (float*) p[11];
            a.ldq = iv[1]; a.ldk = iv[2]; a.ldv = iv[3];
            a.off_q = iv[4]; a.off_rel = iv[5]; a.off_sink = iv[6]; a.off_k = iv[7]; a.off_kproj = iv[8];
            a.off_v = iv[9]; a.off_vproj = iv[10]; a.global = iv[11]; a.nfloor = iv[12]; a.T = ni > 13 && iv[13] > 1 ? (int) iv[13] : 1;
            a.qclip = (float) dv[0]; a.relclip = (float) dv[1]; a.eps = (float) dv[2]; a.alpha = (float) dv[3];
            prep_kernel<<<dim3(iv[0], 24), 128, 0, stream>>>(a);
            CHECK_LAUNCH("prep");
            return 0;
        }
        case OP_ATTN:
        case OP_MERGE: {
            // p: qn, rel, sink, yq, projT, tbl, pos, out, pm, pl, pacc; iv: nb, ldq, off_gate, window, nsplit
            AttnArgs a;
            std::memset(&a, 0, sizeof(a));
            a.qn = (const bf16*) p[0]; a.rel = (const float*) p[1]; a.sink = (const float*) p[2]; a.yq = (const bf16*) p[3];
            a.projT = (const bf16*) p[4]; a.tbl = (const int64_t*) p[5]; a.pos = (const int*) p[6]; a.out = (bf16*) p[7];
            a.pm = (float*) p[8]; a.pl = (float*) p[9]; a.pacc = (float*) p[10];
            a.ldq = iv[1]; a.off_gate = (int) iv[2]; a.window = (int) iv[3]; a.nsplit = (int) iv[4];
            if (op == OP_ATTN) {
                attn_kernel<<<dim3(iv[0], 8, a.nsplit), 128, 0, stream>>>(a);
            } else {
                merge_kernel<<<dim3(iv[0], 24), 128, 0, stream>>>(a);
            }
            CHECK_LAUNCH("attn");
            return 0;
        }
        case OP_ROUTE: {
            // p: logits, bias, idx(int32), wts; iv: nb, experts, topk; dv: scale
            if (iv[2] != 8) {
                g_error = "route: top-k must be 8";
                return -3;
            }
            route_kernel<8><<<(int) iv[0], 32, 0, stream>>>((const float*) p[0], (const float*) p[1], (int*) p[2], (float*) p[3],
                    (int) iv[1], (float) dv[0]);
            CHECK_LAUNCH("route");
            return 0;
        }
        case OP_MOE_ACT: {
            // p: gu, out; iv: rows, inter; dv: gate cap, up cap
            const long total = iv[0] * iv[1];
            moe_act_kernel<<<(unsigned) ((total + 255) / 256), 256, 0, stream>>>((const bf16*) p[0], (bf16*) p[1], total, (int) iv[1],
                    (float) dv[0], (float) dv[1]);
            CHECK_LAUNCH("moe_act");
            return 0;
        }
        case OP_EMBED: {
            // p: ids, table, w, y; iv: nb, dim; dv: eps
            embed_kernel<<<(int) iv[0], 256, 0, stream>>>((const int64_t*) p[0], (const bf16*) p[1], (const float*) p[2], (bf16*) p[3],
                    (int) iv[1], (float) dv[0]);
            CHECK_LAUNCH("embed");
            return 0;
        }
        case OP_SOFTCAP: {
            // p: x, out; iv: total; dv: cap
            softcap_kernel<<<(unsigned) ((iv[0] + 255) / 256), 256, 0, stream>>>((const bf16*) p[0], (float*) p[1], iv[0], (float) dv[0],
                    ni > 2 ? iv[1] : 1, ni > 2 ? iv[2] : 1);
            CHECK_LAUNCH("softcap");
            return 0;
        }
        case OP_ALIGN: {
            // p: ids (int32), out; iv: numel, experts, block, padded, blocks
            const int experts = (int) iv[1];
            align_decode_kernel<<<1, 128 * ((experts + 127) / 128), 0, stream>>>((const int32_t*) p[0], (int) iv[0], experts, (int) iv[2],
                    (int) iv[3], (int) iv[4], (int32_t*) p[1], (int32_t*) p[1] + iv[3], (int32_t*) p[1] + iv[3] + iv[4]);
            CHECK_LAUNCH("align");
            return 0;
        }
        case OP_ATTNRES_RMS: {
            // p: out (0 = none), q, w[nq], y[nq], y32[nq] (0 = none), state0..n-1; iv: nb, nq, dim, n, f32mask, rms (0/1)
            const int nb = iv[0], nq = iv[1], dim = iv[2], n = iv[3];
            States st;
            std::memset(&st, 0, sizeof(st));
            st.n = n;
            st.f32mask = (int) iv[4];
            RmsOut ro;
            std::memset(&ro, 0, sizeof(ro));
            if (iv[5]) {
                for (int j = 0; j < nq; j++) {
                    ro.w[j] = (const float*) p[2 + j];
                    ro.y[j] = (bf16*) p[2 + nq + j];
                    ro.y32[j] = (float*) p[2 + 2 * nq + j];
                }
            }
            for (int i = 0; i < n; i++) st.p[i] = (const void*) p[2 + 3 * nq + i];
            bool ok = false;
            if (nq == 1) ok = dispatch_attnres_rms<1>(n, st, (const float*) p[1], (float*) p[0], ro, nb, dim, stream);
            else if (nq == 3) ok = dispatch_attnres_rms<3>(n, st, (const float*) p[1], (float*) p[0], ro, nb, dim, stream);
            else if (nq == 4) ok = dispatch_attnres_rms<4>(n, st, (const float*) p[1], (float*) p[0], ro, nb, dim, stream);
            if (!ok) {
                g_error = "attnres_rms: unsupported n / nq";
                return -3;
            }
            CHECK_LAUNCH("attnres_rms");
            return 0;
        }
        case OP_COMMIT: {
            // p: tbl, accepted (int32 [requests]); iv: apps, requests, T, rows per app in tbl, dim
            commit_kernel<<<dim3(iv[0], iv[1], 4), 256, 0, stream>>>((const int64_t*) p[0], (const int*) p[1], (int) iv[3], (int) iv[2], (int) iv[4]);
            CHECK_LAUNCH("commit");
            return 0;
        }
        case OP_DRAFT_SCATTER: {
            // p: K, V, ptrs, pos; iv: rows, stride, layer, rows_per
            draft_scatter_kernel<<<(int) iv[0], 256, 0, stream>>>((const bf16*) p[0], (const bf16*) p[1], (const int64_t*) p[2],
                    (const int*) p[3], (int) iv[1], (int) iv[2], (int) iv[3]);
            CHECK_LAUNCH("draft_scatter");
            return 0;
        }
        case OP_DRAFT_ATTN:
        case OP_DRAFT_MERGE: {
            // p: q, ptrs, endpos, out, pm, pl, pacc; iv: B, stride, layer, window, nsplit
            DraftAttnArgs a;
            std::memset(&a, 0, sizeof(a));
            a.q = (const bf16*) p[0]; a.ptrs = (const int64_t*) p[1]; a.endpos = (const int*) p[2]; a.out = (bf16*) p[3];
            a.pm = (float*) p[4]; a.pl = (float*) p[5]; a.pacc = (float*) p[6];
            a.stride = (int) iv[1]; a.layer = (int) iv[2]; a.window = (int) iv[3]; a.nsplit = (int) iv[4];
            if (op == OP_DRAFT_ATTN) {
                draft_attn_kernel<<<dim3(iv[0], 8, a.nsplit), 128, 0, stream>>>(a);
            } else {
                draft_merge_kernel<<<dim3(iv[0] * 8, 24), 128, 0, stream>>>(a);
            }
            CHECK_LAUNCH("draft_attn");
            return 0;
        }
        case OP_PEER: {
            // iv: peer device; lets kernels of the current device read and write its memory (KV caches spilled to a second GPU)
            int cur = 0, can = 0;
            cudaGetDevice(&cur);
            cudaDeviceCanAccessPeer(&can, cur, (int) iv[0]);
            if (!can) {
                g_error = "peer access from device " + std::to_string(cur) + " to " + std::to_string(iv[0]) + " is not available";
                return -4;
            }
            const cudaError_t e = cudaDeviceEnablePeerAccess((int) iv[0], 0);
            if (e != cudaSuccess && e != cudaErrorPeerAccessAlreadyEnabled) {
                g_error = std::string("peer access: ") + cudaGetErrorString(e);
                return -4;
            }
            cudaGetLastError();
            return 0;
        }
        default:
            g_error = "unknown op " + std::to_string(op);
            return -2;
    }
}
