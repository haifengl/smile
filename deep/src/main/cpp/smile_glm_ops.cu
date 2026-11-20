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
        float* __restrict__ out, int T, int H, float bound) {
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
    }
#pragma unroll
    for (int r = 0; r < kRows; ++r) S[(int64_t) (warp * kRows + r) * kKD + col] = st[r];
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

constexpr int kLat = 512;           // latent width
constexpr int kPoolDim = 128;       // indexer key width

__global__ void mla_append_kernel(const float* __restrict__ lat_new, const float* __restrict__ key_new,
                                  const float* __restrict__ gate_new, const float* __restrict__ ape,
                                  const long long* __restrict__ lat_ptrs, const long long* __restrict__ pool_ptrs,
                                  const long long* __restrict__ pend_ptrs, const int* __restrict__ lens) {
    const int b = blockIdx.x;
    const int d = threadIdx.x;               // 128 threads: one indexer channel each
    const int len = lens[b];
    __nv_bfloat16* lat = reinterpret_cast<__nv_bfloat16*>(lat_ptrs[b]);
    for (int i = d; i < kLat; i += kPoolDim) {
        lat[(int64_t) len * kLat + i] = __float2bfloat16(lat_new[(int64_t) b * kLat + i]);
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

constexpr int kAttnHeads = 4;       // heads per block
constexpr int kAttnMaxW = 2056;

/**
 * Block = kAttnHeads heads of one row (a decode request, or a prefill query: only the pointer table and the id row differ). The query
 * of the block's heads lives in registers as packed bf16 (each lane owns 16 of the 512 latent dims of every head), the token ids
 * in shared memory; scores are computed 4 tokens per warp at a time with all loads in flight, softmax per head, then each
 * thread (2 latent dims) accumulates 8 tokens per step. Invalid tokens (id < 0) get probability 0 and read row 0.
 */
__global__ void __launch_bounds__(256, 2) mla_attend_kernel(
        const __nv_bfloat16* __restrict__ qabs, const long long* __restrict__ lat_ptrs, const int* __restrict__ idx,
        int width, int heads, float scale, float* __restrict__ ctx) {
    extern __shared__ float smem[];
    float* sc = smem;                                                // [8][kAttnMaxW]
    int* sid = reinterpret_cast<int*>(smem + kAttnHeads * kAttnMaxW);  // [kAttnMaxW]
    const int b = blockIdx.x;
    const int h0 = blockIdx.y * kAttnHeads;
    const int tid = threadIdx.x;
    const int lane = tid & 31;
    const int warp = tid >> 5;
    const __nv_bfloat16* lat = reinterpret_cast<const __nv_bfloat16*>(lat_ptrs[b]);
    for (int j = tid; j < width; j += 256) sid[j] = idx[(long long) b * width + j];
    // this lane's slice of every head's query: dims lane*8..+7 and 256 + lane*8..+7
    uint4 qr[kAttnHeads][2];
#pragma unroll
    for (int h = 0; h < kAttnHeads; ++h) {
        const __nv_bfloat16* qp = qabs + ((long long) b * heads + h0 + h) * kLat;
        qr[h][0] = *reinterpret_cast<const uint4*>(qp + lane * 8);
        qr[h][1] = *reinterpret_cast<const uint4*>(qp + 256 + lane * 8);
    }
    __syncthreads();
    constexpr int U = 4;
    for (int j0 = warp; j0 < width; j0 += 8 * U) {
        uint4 r0[U], r1[U];
#pragma unroll
        for (int u = 0; u < U; ++u) {
            const int j = j0 + 8 * u;
            int id = j < width ? sid[j] : 0;
            if (id < 0) id = 0;
            const __nv_bfloat16* row = lat + (long long) id * kLat;
            r0[u] = *reinterpret_cast<const uint4*>(row + lane * 8);
            r1[u] = *reinterpret_cast<const uint4*>(row + 256 + lane * 8);
        }
#pragma unroll
        for (int u = 0; u < U; ++u) {
            const int j = j0 + 8 * u;
            float x[16];
            const __nv_bfloat162* a = reinterpret_cast<const __nv_bfloat162*>(&r0[u]);
            const __nv_bfloat162* c2 = reinterpret_cast<const __nv_bfloat162*>(&r1[u]);
#pragma unroll
            for (int i = 0; i < 4; ++i) {
                x[2 * i] = __low2float(a[i]);
                x[2 * i + 1] = __high2float(a[i]);
                x[8 + 2 * i] = __low2float(c2[i]);
                x[8 + 2 * i + 1] = __high2float(c2[i]);
            }
            float part[kAttnHeads];
#pragma unroll
            for (int h = 0; h < kAttnHeads; ++h) {
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
            for (int h = 0; h < kAttnHeads; ++h) {
#pragma unroll
                for (int o = 16; o > 0; o >>= 1) part[h] += __shfl_xor_sync(0xffffffffu, part[h], o);
            }
            if (lane < kAttnHeads && j < width) {
                float v = part[0];
#pragma unroll
                for (int h = 1; h < kAttnHeads; ++h) if (lane == h) v = part[h];
                sc[lane * kAttnMaxW + j] = sid[j] < 0 ? -INFINITY : v * scale;
            }
        }
    }
    __syncthreads();
    // softmax: warp h owns head h
    if (warp < kAttnHeads) {
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
    float acc[kAttnHeads][2];
#pragma unroll
    for (int h = 0; h < kAttnHeads; ++h) acc[h][0] = acc[h][1] = 0.f;
    constexpr int V = 8;
    for (int j0 = 0; j0 < width; j0 += V) {
        __nv_bfloat162 v[V];
#pragma unroll
        for (int u = 0; u < V; ++u) {
            const int j = j0 + u;
            int id = j < width ? sid[j] : 0;
            if (id < 0) id = 0;
            v[u] = *reinterpret_cast<const __nv_bfloat162*>(lat + (long long) id * kLat + 2 * tid);
        }
#pragma unroll
        for (int u = 0; u < V; ++u) {
            const int j = j0 + u;
            const float v0 = __low2float(v[u]), v1 = __high2float(v[u]);
#pragma unroll
            for (int h = 0; h < kAttnHeads; ++h) {
                const float p = j < width ? sc[h * kAttnMaxW + j] : 0.f;
                acc[h][0] += p * v0;
                acc[h][1] += p * v1;
            }
        }
    }
#pragma unroll
    for (int h = 0; h < kAttnHeads; ++h) {
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
        kda_kernel<<<grid, 128, 0, stream>>>(R.data_ptr<float>(), F.data_ptr<float>(), Bt.data_ptr<float>(),
                                             CW.data_ptr<float>(), AL.data_ptr<float>(), DT.data_ptr<float>(),
                                             CS.data_ptr<float>(), RS.data_ptr<float>(), SL.data_ptr<int>(),
                                             out.data_ptr<float>(), (int) T, (int) H, (float) gate_bound);
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

extern "C" int smile_mla_decode_append(ST_Tensor lat_new, ST_Tensor key_new, ST_Tensor gate_new, ST_Tensor ape,
                                       ST_Tensor lat_ptrs, ST_Tensor pool_ptrs, ST_Tensor pend_ptrs, ST_Tensor lens) {
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
                reinterpret_cast<const long long*>(pend_ptrs->t.data_ptr<int64_t>()), lens->t.data_ptr<int>());
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

extern "C" ST_Tensor smile_mla_decode_attend(ST_Tensor qabs, ST_Tensor lat_ptrs, ST_Tensor idx, double scale) {
    try {
        smile_torch_clear_error();
        std::string err;
        if (!check(qabs->t, "qabs", at::kBFloat16, 3, err) || !check(lat_ptrs->t, "lat_ptrs", at::kLong, 1, err)
                || !check(idx->t, "idx", at::kInt, 2, err)) {
            set_err("smile_mla_decode_attend: " + err);
            return nullptr;
        }
        const int64_t B = qabs->t.size(0), H = qabs->t.size(1), W = idx->t.size(1);
        if (qabs->t.size(2) != kLat || H % kAttnHeads != 0 || idx->t.size(0) != B || lat_ptrs->t.size(0) != B || W > kAttnMaxW) {
            set_err("smile_mla_decode_attend: inconsistent shapes");
            return nullptr;
        }
        const int dev = qabs->t.get_device();
        c10::cuda::CUDAGuard guard(dev);
        at::Tensor out = torch::empty({B, H, (int64_t) kLat}, qabs->t.options().dtype(at::kFloat));
        const size_t smem = (size_t) (kAttnHeads * kAttnMaxW + kAttnMaxW) * sizeof(float);
        // the opt-in to more than 48 KB of dynamic shared memory is a property of the function on each device
        static std::mutex configure_mutex;
        static bool configured[64] = {false};
        {
            std::lock_guard<std::mutex> lock(configure_mutex);
            if (dev >= 0 && dev < 64 && !configured[dev]) {
                cudaError_t attr = cudaFuncSetAttribute(mla_attend_kernel, cudaFuncAttributeMaxDynamicSharedMemorySize, (int) smem);
                if (attr != cudaSuccess) {
                    set_err(std::string("smile_mla_decode_attend: cannot raise the shared memory limit: ") + cudaGetErrorString(attr));
                    return nullptr;
                }
                configured[dev] = true;
            }
        }
        dim3 grid((unsigned) B, (unsigned) (H / kAttnHeads));
        mla_attend_kernel<<<grid, 256, smem, at::cuda::getCurrentCUDAStream(dev)>>>(
                reinterpret_cast<const __nv_bfloat16*>(qabs->t.data_ptr()), reinterpret_cast<const long long*>(lat_ptrs->t.data_ptr<int64_t>()),
                idx->t.data_ptr<int>(), (int) W, (int) H, (float) scale, out.data_ptr<float>());
        cudaError_t st = cudaGetLastError();
        if (st != cudaSuccess) {
            set_err(std::string("smile_mla_decode_attend: CUDA error: ") + cudaGetErrorString(st));
            return nullptr;
        }
        return new ST_Tensor_{ out };
    } catch (const std::exception& ex) {
        smile_torch_set_error(ex.what());
        return nullptr;
    }
}
