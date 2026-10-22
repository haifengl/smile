/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * Fused decode-path kernels; see smile_minimax_ops.h.
 */
#include "smile_minimax_ops.h"

#include <ATen/ATen.h>
#include <ATen/cuda/CUDAContext.h>
#include <c10/cuda/CUDAGuard.h>
#include <torch/torch.h>

#include <cfloat>
#include <string>

/* smile_torch.cpp */
extern "C" void smile_torch_set_error(const char *msg);
extern "C" void smile_torch_clear_error(void);

struct ST_Tensor_ {
    at::Tensor t;
};

namespace {

void set_err(const std::string& msg) { smile_torch_set_error(msg.c_str()); }

__device__ __forceinline__ float warp_sum(float v) {
    for (int o = 16; o > 0; o >>= 1) v += __shfl_xor_sync(0xffffffffu, v, o);
    return v;
}

/** Rounds a float to the storage type and back, mimicking an eager op that materialises T. */
template <typename T>
__device__ __forceinline__ float round_to(float v) { return static_cast<float>(static_cast<T>(v)); }

/** Gemma scale (1 + w) as the eager path computes it: w cast to T, +1, rounded to T. */
template <typename T>
__device__ __forceinline__ float gemma_scale(const T* w, int i) {
    return round_to<T>(static_cast<float>(w[i]) + 1.0f);
}

// ---------------------------------------------------------------- RMSNorm

template <typename T>
__global__ void gemma_rms_norm_kernel(const T* __restrict__ x, const T* __restrict__ w, T* __restrict__ y,
                                      int d, float eps) {
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
    const float rstd = rsqrtf(partial[0] / (float) d + eps);
    for (int i = threadIdx.x; i < d; i += blockDim.x) {
        yr[i] = static_cast<T>(static_cast<float>(xr[i]) * rstd * gemma_scale<T>(w, i));
    }
}


// ---------------------------------------------------------------- residual add + RMSNorm, SwiGLU

/** sum = x + r (rounded to T like the eager add), y = gemma_rms_norm(sum). One row per block. */
template <typename T>
__global__ void add_gemma_rms_norm_kernel(const T* __restrict__ x, const T* __restrict__ r, const T* __restrict__ w,
                                          T* __restrict__ sum, T* __restrict__ y, int d, float eps) {
    const int64_t off = (int64_t) blockIdx.x * d;
    float ss = 0.f;
    for (int i = threadIdx.x; i < d; i += blockDim.x) {
        const T t = static_cast<T>(static_cast<float>(x[off + i]) + static_cast<float>(r[off + i]));
        sum[off + i] = t;
        const float v = static_cast<float>(t);
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
    const float rstd = rsqrtf(partial[0] / (float) d + eps);
    for (int i = threadIdx.x; i < d; i += blockDim.x) {
        y[off + i] = static_cast<T>(static_cast<float>(sum[off + i]) * rstd * gemma_scale<T>(w, i));
    }
}


/**
 * Row-per-block RMSNorm with 16-byte vector accesses and the whole row held in registers: one read of x (and r), one
 * block reduction, one write. d must be a multiple of the vector width and at most 1024 * width. ADD fuses the residual
 * add (the rounded sum is also written to sum_out).
 */
template <typename T, bool ADD>
__global__ void rms_norm_vec_kernel(const T* __restrict__ x, const T* __restrict__ r, const T* __restrict__ w,
                                    T* __restrict__ sum_out, T* __restrict__ y, int d, float eps) {
    constexpr int VEC = 16 / sizeof(T);
    const int nvec = d / VEC;
    const int tid = threadIdx.x;
    const int64_t row = (int64_t) blockIdx.x * d;
    float v[VEC];
    float ss = 0.f;
    const bool active = tid < nvec;
    if (active) {
        union { uint4 u; T t[VEC]; } xv;
        xv.u = *reinterpret_cast<const uint4*>(x + row + (int64_t) tid * VEC);
        if (ADD) {
            union { uint4 u; T t[VEC]; } rv, sv;
            rv.u = *reinterpret_cast<const uint4*>(r + row + (int64_t) tid * VEC);
            #pragma unroll
            for (int i = 0; i < VEC; ++i) {
                sv.t[i] = static_cast<T>(static_cast<float>(xv.t[i]) + static_cast<float>(rv.t[i]));
                v[i] = static_cast<float>(sv.t[i]);
            }
            *reinterpret_cast<uint4*>(sum_out + row + (int64_t) tid * VEC) = sv.u;
        } else {
            #pragma unroll
            for (int i = 0; i < VEC; ++i) v[i] = static_cast<float>(xv.t[i]);
        }
        #pragma unroll
        for (int i = 0; i < VEC; ++i) ss += v[i] * v[i];
    }
    __shared__ float partial[32];
    ss = warp_sum(ss);
    if ((tid & 31) == 0) partial[tid >> 5] = ss;
    __syncthreads();
    float tot = (tid & 31) < (blockDim.x >> 5) ? partial[tid & 31] : 0.f;
    tot = warp_sum(tot);  // every warp reduces the per-warp partials itself: no second sync needed
    const float rstd = rsqrtf(tot / (float) d + eps);
    if (active) {
        union { uint4 u; T t[VEC]; } wv, yv;
        wv.u = *reinterpret_cast<const uint4*>(w + (int64_t) tid * VEC);
        #pragma unroll
        for (int i = 0; i < VEC; ++i) {
            yv.t[i] = static_cast<T>(v[i] * rstd * round_to<T>(static_cast<float>(wv.t[i]) + 1.0f));
        }
        *reinterpret_cast<uint4*>(y + row + (int64_t) tid * VEC) = yv.u;
    }
}

/** out = silu(gate) * up in float with one rounding. */
template <typename T>
__global__ void silu_mul_kernel(const T* __restrict__ gate, const T* __restrict__ up, T* __restrict__ out, int64_t n) {
    const int64_t i = (int64_t) blockIdx.x * blockDim.x + threadIdx.x;
    if (i >= n) return;
    const float g = static_cast<float>(gate[i]);
    out[i] = static_cast<T>(g / (1.f + expf(-g)) * static_cast<float>(up[i]));
}

// ---------------------------------------------------------------- QK norm + RoPE

template <typename T>
__global__ void qk_norm_rope_kernel(const T* __restrict__ q, const T* __restrict__ k,
                                    const T* __restrict__ qw, const T* __restrict__ kw,
                                    const void* __restrict__ cos, const void* __restrict__ sin, int cos_is_float,
                                    T* __restrict__ qo, T* __restrict__ ko, int64_t q_row, int64_t k_row,
                                    int64_t tokens, int hq, int hk, int d, int rot, int cos_rows, float eps) {
    extern __shared__ float smem[];
    const int warp = threadIdx.x >> 5;
    const int lane = threadIdx.x & 31;
    const int64_t item = (int64_t) blockIdx.x * (blockDim.x >> 5) + warp;
    const int heads = hq + hk;
    if (item >= tokens * heads) return;
    float* s = smem + warp * d;

    const int64_t t = item / heads;
    const int h = (int) (item % heads);
    const bool is_q = h < hq;
    const int64_t off = is_q ? (t * hq + h) * (int64_t) d : (t * hk + (h - hq)) * (int64_t) d;
    const T* src = is_q ? q + t * q_row + (int64_t) h * d : k + t * k_row + (int64_t)(h - hq) * d;
    T* dst = (is_q ? qo : ko) + off;
    const T* w = is_q ? qw : kw;

    float ss = 0.f;
    for (int i = lane; i < d; i += 32) {
        float v = static_cast<float>(src[i]);
        s[i] = v;
        ss += v * v;
    }
    ss = warp_sum(ss);
    const float rstd = rsqrtf(ss / (float) d + eps);
    for (int i = lane; i < d; i += 32) {
        s[i] = round_to<T>(s[i] * rstd * gemma_scale<T>(w, i));
    }
    __syncwarp();

    const int64_t row = (t % cos_rows) * (int64_t) rot;
    const int half = rot / 2;
    for (int i = lane; i < d; i += 32) {
        float out = s[i];
        if (i < rot) {
            float c, sn;
            if (cos_is_float) {
                c = ((const float*) cos)[row + i];
                sn = ((const float*) sin)[row + i];
            } else {
                c = static_cast<float>(((const T*) cos)[row + i]);
                sn = static_cast<float>(((const T*) sin)[row + i]);
            }
            float rotated = i < half ? -s[i + half] : s[i - half];
            out = s[i] * c + rotated * sn;
        }
        dst[i] = static_cast<T>(out);
    }
}

// ---------------------------------------------------------------- swigluoai

template <typename T>
__global__ void swiglu_oai_kernel(const T* __restrict__ gate, const T* __restrict__ up, T* __restrict__ out,
                                  int64_t rows, int inter, int64_t gate_stride, int64_t up_stride,
                                  float alpha, float limit) {
    const int64_t n = rows * inter;
    for (int64_t idx = (int64_t) blockIdx.x * blockDim.x + threadIdx.x; idx < n;
         idx += (int64_t) gridDim.x * blockDim.x) {
        const int64_t r = idx / inter;
        const int c = (int) (idx % inter);
        float g = static_cast<float>(gate[r * gate_stride + c]);
        float u = static_cast<float>(up[r * up_stride + c]);
        g = fminf(g, limit);
        u = fminf(fmaxf(u, -limit), limit);
        const float glu = g / (1.0f + __expf(-alpha * g));
        out[idx] = static_cast<T>(glu * (u + 1.0f));
    }
}

// ---------------------------------------------------------------- MoE routing

constexpr int kMaxPerLane = 32;  // E <= 1024

__global__ void moe_route_kernel(const float* __restrict__ logits, const float* __restrict__ bias,
                                 int32_t* __restrict__ ids, float* __restrict__ weights,
                                 int tokens, int experts, int top_k, float scale) {
    const int lane = threadIdx.x & 31;
    const int token = blockIdx.x * (blockDim.x >> 5) + (threadIdx.x >> 5);
    if (token >= tokens) return;
    const int per_lane = experts / 32;
    float score[kMaxPerLane];
    float sel[kMaxPerLane];
    for (int j = 0; j < per_lane; j++) {
        const int e = lane + 32 * j;
        score[j] = 1.0f / (1.0f + __expf(-logits[(int64_t) token * experts + e]));
        sel[j] = score[j] + bias[e];
    }
    float picked_score[16];
    int picked_id[16];
    float total = 0.f;
    for (int r = 0; r < top_k; r++) {
        float best = -FLT_MAX;
        int best_e = 0x7fffffff;
        for (int j = 0; j < per_lane; j++) {
            if (sel[j] > best) {
                best = sel[j];
                best_e = lane + 32 * j;
            }
        }
        for (int o = 16; o > 0; o >>= 1) {
            float ov = __shfl_xor_sync(0xffffffffu, best, o);
            int oe = __shfl_xor_sync(0xffffffffu, best_e, o);
            if (ov > best || (ov == best && oe < best_e)) {
                best = ov;
                best_e = oe;
            }
        }
        // the owner lane reads its unbiased score and retires the expert
        float s = 0.f;
        if ((best_e & 31) == lane) {
            const int j = best_e >> 5;
            for (int jj = 0; jj < per_lane; jj++) {
                if (jj == j) {
                    s = score[jj];
                    sel[jj] = -FLT_MAX;
                }
            }
        }
        s = __shfl_sync(0xffffffffu, s, best_e & 31);
        picked_score[r] = s;
        picked_id[r] = best_e;
        total += s;
    }
    if (lane == 0) {
        for (int r = 0; r < top_k; r++) {
            ids[(int64_t) token * top_k + r] = picked_id[r];
            weights[(int64_t) token * top_k + r] = picked_score[r] / total * scale;
        }
    }
}


// ---------------------------------------------------------------- MSA decode block scoring

constexpr int kMsaMaxGroups = 8;
constexpr int kMsaMaxBlocks = 8192;
constexpr int kMaxGridY = 60000;   // gridDim.y / z limit is 65535

/** Block-max of the raw index dot products. Grid (numBlocks, B); one CTA scores one 128-key block for all groups. */
template <typename T>
__global__ void msa_block_score_kernel(const T* __restrict__ q, const T* __restrict__ cache,
                                       const int64_t* __restrict__ slots, const int* __restrict__ positions,
                                       float* __restrict__ scores, int groups, int d, int length, int block_size,
                                       int num_blocks, int seqlen, int t0) {
    const int blk = blockIdx.x;
    const int t = t0 + blockIdx.y;               // query token; row b = t / seqlen, position = start + t % seqlen
    const int b = t / seqlen;
    const int pos = positions[b] + t % seqlen;
    const int first = blk * block_size;
    const int warp = threadIdx.x >> 5;
    const int lane = threadIdx.x & 31;
    const int warps = blockDim.x >> 5;
    float* out = scores + ((int64_t) t * groups) * num_blocks + blk;
    if (first > pos) {
        if (threadIdx.x < groups) out[(int64_t) threadIdx.x * num_blocks] = -INFINITY;
        return;
    }
    extern __shared__ float smem[];
    float* qs = smem;                      // [groups * d]
    float* red = smem + groups * d;        // [warps * groups]
    for (int i = threadIdx.x; i < groups * d; i += blockDim.x) {
        qs[i] = static_cast<float>(q[(int64_t) t * groups * d + i]);
    }
    __syncthreads();
    float best[kMsaMaxGroups];
    for (int g = 0; g < groups; g++) best[g] = -INFINITY;
    const int last = min(min(first + block_size, length) - 1, pos);
    for (int l = first + warp; l <= last; l += warps) {
        const T* k = cache + slots[(int64_t) b * length + l] * (int64_t) d;
        float part[kMsaMaxGroups];
        for (int g = 0; g < groups; g++) part[g] = 0.f;
        for (int e = lane; e < d; e += 32) {
            const float kv = static_cast<float>(k[e]);
            for (int g = 0; g < groups; g++) part[g] += qs[g * d + e] * kv;
        }
        for (int g = 0; g < groups; g++) best[g] = fmaxf(best[g], warp_sum(part[g]));
    }
    if (lane == 0) {
        for (int g = 0; g < groups; g++) red[warp * groups + g] = best[g];
    }
    __syncthreads();
    if (threadIdx.x < groups) {
        float m = -INFINITY;
        for (int w = 0; w < warps; w++) m = fmaxf(m, red[w * groups + threadIdx.x]);
        out[(int64_t) threadIdx.x * num_blocks] = m;
    }
}

/** Picks the top-k visible blocks per (row, group) and writes the additive mask [B, G, 1, L]. Grid (G, B). */
template <typename T>
__global__ void msa_select_mask_kernel(const float* __restrict__ scores, const int* __restrict__ positions,
                                       T* __restrict__ mask, int* __restrict__ sel, int groups, int length,
                                       int block_size, int num_blocks, int topk, int init_blocks,
                                       int local_blocks, int seqlen, int t0) {
    const int g = blockIdx.x;
    const int t = t0 + blockIdx.y;
    const int b = t / seqlen;
    const int pos = positions[b] + t % seqlen;
    const int visible = pos / block_size + 1;
    const int local_start = max(0, visible - local_blocks);
    extern __shared__ float smem[];
    float* s = smem;                                              // [num_blocks]
    unsigned char* chosen = reinterpret_cast<unsigned char*>(s + num_blocks);
    __shared__ float red_v[32];
    __shared__ int red_i[32];
    const float* src = scores + ((int64_t) t * groups + g) * num_blocks;
    for (int i = threadIdx.x; i < num_blocks; i += blockDim.x) {
        float v = -INFINITY;
        if (i < visible) {
            v = src[i];
            if (i >= local_start) v = 1e29f;
            if (i < init_blocks) v = 1e30f;
        }
        s[i] = v;
        chosen[i] = 0;
    }
    int* sel_row = sel != nullptr ? sel + ((int64_t) t * groups + g) * topk : nullptr;
    for (int r = threadIdx.x; sel_row != nullptr && r < topk; r += blockDim.x) sel_row[r] = -1;
    __syncthreads();
    const int warps = blockDim.x >> 5;
    const int k2 = min(topk, num_blocks);
    for (int r = 0; r < k2; r++) {
        float best = -INFINITY;
        int bi = 0x7fffffff;
        for (int i = threadIdx.x; i < num_blocks; i += blockDim.x) {
            if (s[i] > best) {
                best = s[i];
                bi = i;
            }
        }
        for (int o = 16; o > 0; o >>= 1) {
            float ov = __shfl_xor_sync(0xffffffffu, best, o);
            int oi = __shfl_xor_sync(0xffffffffu, bi, o);
            if (ov > best || (ov == best && oi < bi)) {
                best = ov;
                bi = oi;
            }
        }
        if ((threadIdx.x & 31) == 0) {
            red_v[threadIdx.x >> 5] = best;
            red_i[threadIdx.x >> 5] = bi;
        }
        __syncthreads();
        if (threadIdx.x == 0) {
            float fb = red_v[0];
            int fi = red_i[0];
            for (int w = 1; w < warps; w++) {
                if (red_v[w] > fb || (red_v[w] == fb && red_i[w] < fi)) {
                    fb = red_v[w];
                    fi = red_i[w];
                }
            }
            if (fb > -1e38f) {
                chosen[fi] = 1;
                if (sel_row != nullptr) sel_row[r] = fi;
                s[fi] = -INFINITY;
            }
        }
        __syncthreads();
    }
    if (mask == nullptr) return;
    T* dst = mask + ((int64_t) t * groups + g) * length;   // mask output is decode-only: t == b
    const T zero = static_cast<T>(0.f);
    const T neg_inf = static_cast<T>(-INFINITY);
    for (int l = threadIdx.x; l < length; l += blockDim.x) {
        dst[l] = (l <= pos && chosen[l / block_size]) ? zero : neg_inf;
    }
}

// ---------------------------------------------------------------- block-sparse decode attention

constexpr int kSdBlock = 128;                    // keys per block
constexpr int kSdDim = 128;                      // head dim
constexpr int kSdRep = 16;                       // query heads per KV group
constexpr int kSdRowWords = kSdDim / 2 + 1;      // 65 words per smem row: conflict-free column reads
constexpr size_t kSdSmem = (size_t) kSdRep * kSdDim * 4 * 2 + (size_t) kSdBlock * kSdRowWords * 4;

__device__ __forceinline__ void bf16x2_to_float(uint32_t w, float& lo, float& hi) {
    lo = __uint_as_float(w << 16);
    hi = __uint_as_float(w & 0xffff0000u);
}

/**
 * Attention of the 16 query heads of one KV group over one selected 128-key block, in fp32.
 * Writes the block's partial softmax state: max m, sum l and the unnormalised output o per head.
 * Grid (topk, kv_heads, batch), 256 threads.
 */
__global__ void sparse_decode_partial_kernel(const at::BFloat16* __restrict__ q, const at::BFloat16* __restrict__ kc,
                                             const at::BFloat16* __restrict__ vc, const int64_t* __restrict__ slots,
                                             const int* __restrict__ positions, const int* __restrict__ sel,
                                             float* __restrict__ part_o, float* __restrict__ part_m,
                                             float* __restrict__ part_l, int heads, int kv_heads, int length,
                                             int topk, float scale, int seqlen, int t0) {
    extern __shared__ __align__(16) unsigned char smem_raw[];
    float* qs = reinterpret_cast<float*>(smem_raw);                 // [16][128], pre-scaled
    float* ps = qs + kSdRep * kSdDim;                                // [16][128] scores then probabilities
    uint32_t* kv = reinterpret_cast<uint32_t*>(ps + kSdRep * kSdDim);  // [128][65] words of bf16 pairs
    const int j = blockIdx.x;
    const int g = blockIdx.y;
    const int tl = blockIdx.z;                    // token within this launch (partial buffers are chunk-local)
    const int t = t0 + tl;                        // global query token
    const int b = t / seqlen;
    const int tid = threadIdx.x;
    const int64_t base = ((int64_t) tl * kv_heads + g) * topk + j;
    const int blk = sel[((int64_t) t * kv_heads + g) * topk + j];
    float* m_out = part_m + base * kSdRep;
    float* l_out = part_l + base * kSdRep;
    if (blk < 0) {
        if (tid < kSdRep) {
            m_out[tid] = -INFINITY;
            l_out[tid] = 0.f;
        }
        return;
    }
    const int pos = positions[b] + t % seqlen;
    const int first = blk * kSdBlock;

    const at::BFloat16* qh = q + ((int64_t) t * heads + (int64_t) g * kSdRep) * kSdDim;
    for (int i = tid; i < kSdRep * kSdDim; i += 256) qs[i] = static_cast<float>(qh[i]) * scale;

    auto load_tile = [&](const at::BFloat16* cache) {
        for (int i = tid; i < kSdBlock * (kSdDim / 2); i += 256) {
            const int r = i >> 6;
            const int c = i & 63;
            const int key = first + r;
            uint32_t w = 0;
            if (key < length && key <= pos) {
                const int64_t slot = slots[(int64_t) b * length + key];
                w = reinterpret_cast<const uint32_t*>(cache + (slot * kv_heads + g) * kSdDim)[c];
            }
            kv[r * kSdRowWords + c] = w;
        }
    };
    load_tile(kc);
    __syncthreads();

    {   // scores: thread = (key, half of the heads)
        const int key = tid & (kSdBlock - 1);
        const int h0 = (tid >> 7) * 8;
        float acc[8];
        for (int h = 0; h < 8; h++) acc[h] = 0.f;
        for (int c = 0; c < kSdDim / 2; c++) {
            float k0, k1;
            bf16x2_to_float(kv[key * kSdRowWords + c], k0, k1);
            for (int h = 0; h < 8; h++) {
                acc[h] += qs[(h0 + h) * kSdDim + 2 * c] * k0 + qs[(h0 + h) * kSdDim + 2 * c + 1] * k1;
            }
        }
        const bool valid = first + key < length && first + key <= pos;
        for (int h = 0; h < 8; h++) ps[(h0 + h) * kSdBlock + key] = valid ? acc[h] : -INFINITY;
    }
    __syncthreads();
    load_tile(vc);   // K is no longer needed
    {   // softmax numerators: 16 lanes per head
        const int h = tid >> 4;
        const int sub = tid & 15;
        float mx = -INFINITY;
        for (int i = 0; i < kSdBlock / 16; i++) mx = fmaxf(mx, ps[h * kSdBlock + sub + 16 * i]);
        for (int o = 8; o > 0; o >>= 1) mx = fmaxf(mx, __shfl_xor_sync(0xffffffffu, mx, o, 16));
        float sum = 0.f;
        for (int i = 0; i < kSdBlock / 16; i++) {
            const int idx = h * kSdBlock + sub + 16 * i;
            const float p = mx == -INFINITY ? 0.f : __expf(ps[idx] - mx);
            ps[idx] = p;
            sum += p;
        }
        for (int o = 8; o > 0; o >>= 1) sum += __shfl_xor_sync(0xffffffffu, sum, o, 16);
        if (sub == 0) {
            m_out[h] = mx;
            l_out[h] = sum;
        }
    }
    __syncthreads();
    {   // o = p @ V: thread = (column pair, 4 heads)
        const int dp = tid & 63;
        const int h0 = (tid >> 6) * 4;
        float acc[4][2];
        for (int h = 0; h < 4; h++) acc[h][0] = acc[h][1] = 0.f;
        for (int key = 0; key < kSdBlock; key++) {
            float v0, v1;
            bf16x2_to_float(kv[key * kSdRowWords + dp], v0, v1);
            for (int h = 0; h < 4; h++) {
                const float p = ps[(h0 + h) * kSdBlock + key];
                acc[h][0] += p * v0;
                acc[h][1] += p * v1;
            }
        }
        float* o = part_o + base * kSdRep * kSdDim;
        for (int h = 0; h < 4; h++) {
            reinterpret_cast<float2*>(o + (h0 + h) * kSdDim)[dp] = make_float2(acc[h][0], acc[h][1]);
        }
    }
}

/** Merges the per-block partial softmax states of every head. Grid (kv_heads, batch), 256 threads. */
__global__ void sparse_decode_combine_kernel(const float* __restrict__ part_o, const float* __restrict__ part_m,
                                             const float* __restrict__ part_l, at::BFloat16* __restrict__ out,
                                             int heads, int kv_heads, int topk, int t0) {
    const int g = blockIdx.x;
    const int tl = blockIdx.y;
    const int64_t base = ((int64_t) tl * kv_heads + g) * topk;
    for (int idx = threadIdx.x; idx < kSdRep * kSdDim; idx += blockDim.x) {
        const int h = idx / kSdDim;
        const int d = idx % kSdDim;
        float mx = -INFINITY;
        for (int j = 0; j < topk; j++) mx = fmaxf(mx, part_m[(base + j) * kSdRep + h]);
        float den = 0.f;
        float num = 0.f;
        if (mx != -INFINITY) {
            for (int j = 0; j < topk; j++) {
                const float mj = part_m[(base + j) * kSdRep + h];
                if (mj == -INFINITY) continue;
                const float w = __expf(mj - mx);
                den += w * part_l[(base + j) * kSdRep + h];
                num += w * part_o[((base + j) * kSdRep + h) * kSdDim + d];
            }
        }
        out[((int64_t)(t0 + tl) * heads + (int64_t) g * kSdRep + h) * kSdDim + d] =
                static_cast<at::BFloat16>(den > 0.f ? num / den : 0.f);
    }
}

at::Tensor require_cuda(const ST_Tensor t, const char* what) {
    if (!t || !t->t.defined() || !t->t.is_cuda()) {
        throw std::runtime_error(std::string(what) + " must be a CUDA tensor");
    }
    return t->t;
}

#define DISPATCH_FLOAT(DTYPE, NAME, ...)                                                          \
    [&] {                                                                                         \
        switch (DTYPE) {                                                                          \
            case at::kBFloat16: { using T = at::BFloat16; return __VA_ARGS__(); }                 \
            case at::kHalf: { using T = at::Half; return __VA_ARGS__(); }                         \
            case at::kFloat: { using T = float; return __VA_ARGS__(); }                           \
            default: throw std::runtime_error(std::string(NAME) + ": unsupported dtype");         \
        }                                                                                         \
    }()

cudaStream_t stream_of(const at::Tensor& t) { return at::cuda::getCurrentCUDAStream(t.get_device()).stream(); }

void check_launch(const char* name) {
    cudaError_t st = cudaGetLastError();
    if (st != cudaSuccess) {
        throw std::runtime_error(std::string(name) + ": CUDA error: " + cudaGetErrorString(st));
    }
}

}  // namespace

extern "C" int smile_minimax_ops_available(void) { return 1; }

extern "C" ST_Tensor smile_gemma_rms_norm(ST_Tensor x, ST_Tensor w, double eps) {
    try {
        smile_torch_clear_error();
        at::Tensor xt = require_cuda(x, "smile_gemma_rms_norm: x").contiguous();
        const int64_t d = xt.size(-1);
        at::Tensor wt = require_cuda(w, "smile_gemma_rms_norm: w").to(xt.scalar_type()).contiguous();
        if (wt.numel() != d) throw std::runtime_error("smile_gemma_rms_norm: weight length != last dim");
        c10::cuda::CUDAGuard guard(xt.get_device());
        at::Tensor y = at::empty_like(xt);
        const int64_t rows = xt.numel() / d;
        if (rows == 0) return new ST_Tensor_{ y };
        const int threads = d >= 1024 ? 512 : (d >= 256 ? 256 : 64);
        const int vec = 16 / (int) xt.element_size();
        if (d % vec == 0 && d / vec <= 1024 && d >= 256 && xt.data_ptr() && ((uintptr_t) xt.data_ptr() % 16) == 0
                && ((uintptr_t) wt.data_ptr() % 16) == 0) {
            const int vthreads = (int) (((d / vec) + 31) / 32 * 32);
            DISPATCH_FLOAT(xt.scalar_type(), "smile_gemma_rms_norm", [&] {
                rms_norm_vec_kernel<T, false><<<(unsigned) rows, vthreads, 0, stream_of(xt)>>>(
                        xt.data_ptr<T>(), nullptr, wt.data_ptr<T>(), nullptr, y.data_ptr<T>(), (int) d, (float) eps);
                return 0;
            });
            check_launch("smile_gemma_rms_norm");
            return new ST_Tensor_{ y };
        }
        DISPATCH_FLOAT(xt.scalar_type(), "smile_gemma_rms_norm", [&] {
            gemma_rms_norm_kernel<T><<<(unsigned) rows, threads, 0, stream_of(xt)>>>(
                    xt.data_ptr<T>(), wt.data_ptr<T>(), y.data_ptr<T>(), (int) d, (float) eps);
            return 0;
        });
        check_launch("smile_gemma_rms_norm");
        return new ST_Tensor_{ y };
    } catch (const std::exception& e) {
        set_err(e.what());
        return nullptr;
    }
}

extern "C" ST_Tensor smile_add_gemma_rms_norm(ST_Tensor x, ST_Tensor r, ST_Tensor w, double eps, ST_Tensor *out_sum) {
    try {
        smile_torch_clear_error();
        *out_sum = nullptr;
        at::Tensor xt = require_cuda(x, "smile_add_gemma_rms_norm: x").contiguous();
        at::Tensor rt = require_cuda(r, "smile_add_gemma_rms_norm: r").contiguous();
        if (xt.sizes() != rt.sizes() || xt.scalar_type() != rt.scalar_type()) {
            throw std::runtime_error("smile_add_gemma_rms_norm: x and r must have the same shape and dtype");
        }
        const int64_t d = xt.size(-1);
        at::Tensor wt = require_cuda(w, "smile_add_gemma_rms_norm: w").to(xt.scalar_type()).contiguous();
        if (wt.numel() != d) throw std::runtime_error("smile_add_gemma_rms_norm: weight length != last dim");
        c10::cuda::CUDAGuard guard(xt.get_device());
        at::Tensor sum = at::empty_like(xt);
        at::Tensor y = at::empty_like(xt);
        const int64_t rows = xt.numel() / d;
        if (rows > 0) {
            const int threads = d >= 1024 ? 512 : (d >= 256 ? 256 : 64);
            const int vec = 16 / (int) xt.element_size();
            const bool vectorized = d % vec == 0 && d / vec <= 1024 && d >= 256
                    && ((uintptr_t) xt.data_ptr() % 16) == 0 && ((uintptr_t) rt.data_ptr() % 16) == 0
                    && ((uintptr_t) wt.data_ptr() % 16) == 0;
            if (vectorized) {
                const int vthreads = (int) (((d / vec) + 31) / 32 * 32);
                DISPATCH_FLOAT(xt.scalar_type(), "smile_add_gemma_rms_norm", [&] {
                    rms_norm_vec_kernel<T, true><<<(unsigned) rows, vthreads, 0, stream_of(xt)>>>(
                            xt.data_ptr<T>(), rt.data_ptr<T>(), wt.data_ptr<T>(), sum.data_ptr<T>(), y.data_ptr<T>(),
                            (int) d, (float) eps);
                    return 0;
                });
                check_launch("smile_add_gemma_rms_norm");
                *out_sum = new ST_Tensor_{ sum };
                return new ST_Tensor_{ y };
            }
            DISPATCH_FLOAT(xt.scalar_type(), "smile_add_gemma_rms_norm", [&] {
                add_gemma_rms_norm_kernel<T><<<(unsigned) rows, threads, 0, stream_of(xt)>>>(
                        xt.data_ptr<T>(), rt.data_ptr<T>(), wt.data_ptr<T>(), sum.data_ptr<T>(), y.data_ptr<T>(),
                        (int) d, (float) eps);
                return 0;
            });
            check_launch("smile_add_gemma_rms_norm");
        }
        *out_sum = new ST_Tensor_{ sum };
        return new ST_Tensor_{ y };
    } catch (const std::exception& e) {
        set_err(e.what());
        return nullptr;
    }
}

extern "C" ST_Tensor smile_silu_mul(ST_Tensor gate, ST_Tensor up) {
    try {
        smile_torch_clear_error();
        at::Tensor g = require_cuda(gate, "smile_silu_mul: gate").contiguous();
        at::Tensor u = require_cuda(up, "smile_silu_mul: up").contiguous();
        if (g.sizes() != u.sizes() || g.scalar_type() != u.scalar_type()) {
            throw std::runtime_error("smile_silu_mul: gate and up must have the same shape and dtype");
        }
        c10::cuda::CUDAGuard guard(g.get_device());
        at::Tensor out = at::empty_like(g);
        const int64_t n = g.numel();
        if (n > 0) {
            DISPATCH_FLOAT(g.scalar_type(), "smile_silu_mul", [&] {
                silu_mul_kernel<T><<<(unsigned) ((n + 255) / 256), 256, 0, stream_of(g)>>>(
                        g.data_ptr<T>(), u.data_ptr<T>(), out.data_ptr<T>(), n);
                return 0;
            });
            check_launch("smile_silu_mul");
        }
        return new ST_Tensor_{ out };
    } catch (const std::exception& e) {
        set_err(e.what());
        return nullptr;
    }
}

extern "C" ST_Tensor smile_qk_norm_rope(ST_Tensor q, ST_Tensor k, ST_Tensor qw, ST_Tensor kw,
                                        ST_Tensor cos, ST_Tensor sin, int rotary_dim, double eps,
                                        ST_Tensor *out_k) {
    try {
        smile_torch_clear_error();
        *out_k = nullptr;
        at::Tensor qt = require_cuda(q, "smile_qk_norm_rope: q");
        at::Tensor kt = require_cuda(k, "smile_qk_norm_rope: k");
        // Slices of a fused projection are fine: heads must be contiguous and tokens uniformly strided.
        auto token_strided = [](const at::Tensor& t) {
            return t.dim() == 4 && t.stride(3) == 1 && t.stride(2) == t.size(3)
                   && t.stride(0) == t.size(1) * t.stride(1);
        };
        if (!token_strided(qt)) qt = qt.contiguous();
        if (!token_strided(kt)) kt = kt.contiguous();
        if (qt.dim() != 4 || kt.dim() != 4 || qt.scalar_type() != kt.scalar_type()) {
            throw std::runtime_error("smile_qk_norm_rope: q/k must be rank-4 [B,S,H,D] of one dtype");
        }
        const int64_t d = qt.size(3);
        if (kt.size(3) != d || kt.size(0) != qt.size(0) || kt.size(1) != qt.size(1)) {
            throw std::runtime_error("smile_qk_norm_rope: q/k shape mismatch");
        }
        if (rotary_dim <= 0 || (rotary_dim & 1) != 0 || rotary_dim > d) {
            throw std::runtime_error("smile_qk_norm_rope: invalid rotary_dim");
        }
        const auto dtype = qt.scalar_type();
        at::Tensor qwt = require_cuda(qw, "smile_qk_norm_rope: qw").to(dtype).contiguous();
        at::Tensor kwt = require_cuda(kw, "smile_qk_norm_rope: kw").to(dtype).contiguous();
        at::Tensor ct = require_cuda(cos, "smile_qk_norm_rope: cos").reshape({-1, rotary_dim});
        at::Tensor st = require_cuda(sin, "smile_qk_norm_rope: sin").reshape({-1, rotary_dim});
        if (ct.scalar_type() != at::kFloat && ct.scalar_type() != dtype) ct = ct.to(at::kFloat);
        if (st.scalar_type() != ct.scalar_type()) st = st.to(ct.scalar_type());
        ct = ct.contiguous();
        st = st.contiguous();
        const int64_t tokens = qt.size(0) * qt.size(1);
        const int hq = (int) qt.size(2);
        const int hk = (int) kt.size(2);
        if (ct.size(0) <= 0 || (tokens % ct.size(0)) != 0) {
            throw std::runtime_error("smile_qk_norm_rope: cos rows do not divide the token count");
        }
        c10::cuda::CUDAGuard guard(qt.get_device());
        at::Tensor qo = at::empty(qt.sizes(), qt.options());
        at::Tensor ko = at::empty(kt.sizes(), kt.options());
        const int warps = 4;
        const int64_t items = tokens * (hq + hk);
        const unsigned blocks = (unsigned) ((items + warps - 1) / warps);
        const size_t smem = (size_t) warps * d * sizeof(float);
        DISPATCH_FLOAT(dtype, "smile_qk_norm_rope", [&] {
            qk_norm_rope_kernel<T><<<blocks, warps * 32, smem, stream_of(qt)>>>(
                    qt.data_ptr<T>(), kt.data_ptr<T>(), qwt.data_ptr<T>(), kwt.data_ptr<T>(),
                    ct.data_ptr(), st.data_ptr(), ct.scalar_type() == at::kFloat ? 1 : 0,
                    qo.data_ptr<T>(), ko.data_ptr<T>(), qt.stride(1), kt.stride(1), tokens, hq, hk, (int) d, rotary_dim,
                    (int) ct.size(0), (float) eps);
            return 0;
        });
        check_launch("smile_qk_norm_rope");
        *out_k = new ST_Tensor_{ ko };
        return new ST_Tensor_{ qo };
    } catch (const std::exception& e) {
        set_err(e.what());
        return nullptr;
    }
}

extern "C" ST_Tensor smile_swiglu_oai(ST_Tensor gate, ST_Tensor up, double alpha, double limit) {
    try {
        smile_torch_clear_error();
        at::Tensor g = require_cuda(gate, "smile_swiglu_oai: gate");
        at::Tensor u = require_cuda(up, "smile_swiglu_oai: up");
        if (g.dim() != 2 || u.sizes() != g.sizes() || g.scalar_type() != u.scalar_type()
            || g.stride(1) != 1 || u.stride(1) != 1) {
            throw std::runtime_error("smile_swiglu_oai: gate/up must be matching [T, I] with unit column stride");
        }
        c10::cuda::CUDAGuard guard(g.get_device());
        at::Tensor out = at::empty({g.size(0), g.size(1)}, g.options());
        const int64_t n = out.numel();
        if (n == 0) return new ST_Tensor_{ out };
        const int threads = 256;
        const unsigned blocks = (unsigned) std::min<int64_t>((n + threads - 1) / threads, 4096);
        DISPATCH_FLOAT(g.scalar_type(), "smile_swiglu_oai", [&] {
            swiglu_oai_kernel<T><<<blocks, threads, 0, stream_of(g)>>>(
                    g.data_ptr<T>(), u.data_ptr<T>(), out.data_ptr<T>(), g.size(0), (int) g.size(1),
                    g.stride(0), u.stride(0), (float) alpha, (float) limit);
            return 0;
        });
        check_launch("smile_swiglu_oai");
        return new ST_Tensor_{ out };
    } catch (const std::exception& e) {
        set_err(e.what());
        return nullptr;
    }
}

extern "C" ST_Tensor smile_moe_route(ST_Tensor logits, ST_Tensor bias, int top_k, double scale,
                                     ST_Tensor *out_weights) {
    try {
        smile_torch_clear_error();
        *out_weights = nullptr;
        at::Tensor l = require_cuda(logits, "smile_moe_route: logits");
        at::Tensor b = require_cuda(bias, "smile_moe_route: bias");
        if (l.scalar_type() != at::kFloat || b.scalar_type() != at::kFloat || l.dim() != 2) {
            throw std::runtime_error("smile_moe_route: logits [T,E] and bias must be float32");
        }
        const int64_t tokens = l.size(0);
        const int64_t experts = l.size(1);
        if (experts % 32 != 0 || experts > 32 * kMaxPerLane || b.numel() != experts) {
            throw std::runtime_error("smile_moe_route: E must be a multiple of 32, at most 1024, and match bias");
        }
        if (top_k <= 0 || top_k > 16 || top_k > experts) {
            throw std::runtime_error("smile_moe_route: top_k must be in [1, 16]");
        }
        l = l.contiguous();
        b = b.contiguous();
        c10::cuda::CUDAGuard guard(l.get_device());
        at::Tensor ids = at::empty({tokens, top_k}, l.options().dtype(at::kInt));
        at::Tensor weights = at::empty({tokens, top_k}, l.options());
        if (tokens > 0) {
            const int warps = 4;
            moe_route_kernel<<<(unsigned) ((tokens + warps - 1) / warps), warps * 32, 0, stream_of(l)>>>(
                    l.data_ptr<float>(), b.data_ptr<float>(), ids.data_ptr<int32_t>(), weights.data_ptr<float>(),
                    (int) tokens, (int) experts, top_k, (float) scale);
            check_launch("smile_moe_route");
        }
        *out_weights = new ST_Tensor_{ weights };
        return new ST_Tensor_{ ids };
    } catch (const std::exception& e) {
        set_err(e.what());
        return nullptr;
    }
}

extern "C" ST_Tensor smile_msa_decode_mask(ST_Tensor q, ST_Tensor cache, ST_Tensor slots, ST_Tensor positions,
                                           int length, int block_size, int topk, int init_blocks,
                                           int local_blocks) {
    try {
        smile_torch_clear_error();
        at::Tensor qt = require_cuda(q, "smile_msa_decode_mask: q").contiguous();
        at::Tensor ct = require_cuda(cache, "smile_msa_decode_mask: cache");
        at::Tensor st = require_cuda(slots, "smile_msa_decode_mask: slots");
        at::Tensor pt = require_cuda(positions, "smile_msa_decode_mask: positions");
        if (qt.dim() != 3 || ct.scalar_type() != qt.scalar_type() || !ct.is_contiguous()) {
            throw std::runtime_error("smile_msa_decode_mask: q [B,G,D] and a contiguous cache of the same dtype");
        }
        const int batch = (int) qt.size(0);
        const int groups = (int) qt.size(1);
        const int d = (int) qt.size(2);
        if (groups > kMsaMaxGroups || d % 32 != 0 || d > 256 || ct.numel() % d != 0 || ct.size(-1) != d
            || (ct.dim() == 3 && ct.size(1) != 1)) {
            throw std::runtime_error("smile_msa_decode_mask: unsupported shape (groups <= 8, one shared key head)");
        }
        if (st.scalar_type() != at::kLong || st.numel() != (int64_t) batch * length
            || pt.scalar_type() != at::kInt || pt.numel() != batch) {
            throw std::runtime_error("smile_msa_decode_mask: slots must be int64 [B*L], positions int32 [B]");
        }
        const int num_blocks = (length + block_size - 1) / block_size;
        if (num_blocks > kMsaMaxBlocks || block_size <= 0 || length <= 0) {
            throw std::runtime_error("smile_msa_decode_mask: too many blocks for the shared-memory scorer");
        }
        st = st.contiguous();
        pt = pt.contiguous();
        c10::cuda::CUDAGuard guard(qt.get_device());
        at::Tensor scores = at::empty({batch, groups, num_blocks}, qt.options().dtype(at::kFloat));
        at::Tensor mask = at::empty({batch, groups, 1, length}, qt.options());
        const int threads1 = 128;
        const size_t smem1 = ((size_t) groups * d + (threads1 / 32) * groups) * sizeof(float);
        const size_t smem2 = (size_t) num_blocks * (sizeof(float) + 1);
        DISPATCH_FLOAT(qt.scalar_type(), "smile_msa_decode_mask", [&] {
            msa_block_score_kernel<T><<<dim3(num_blocks, batch), threads1, smem1, stream_of(qt)>>>(
                    qt.data_ptr<T>(), ct.data_ptr<T>(), st.data_ptr<int64_t>(), pt.data_ptr<int>(),
                    scores.data_ptr<float>(), groups, d, length, block_size, num_blocks, 1, 0);
            msa_select_mask_kernel<T><<<dim3(groups, batch), 256, smem2, stream_of(qt)>>>(
                    scores.data_ptr<float>(), pt.data_ptr<int>(), mask.data_ptr<T>(), nullptr, groups, length,
                    block_size, num_blocks, topk, init_blocks, local_blocks, 1, 0);
            return 0;
        });
        check_launch("smile_msa_decode_mask");
        return new ST_Tensor_{ mask };
    } catch (const std::exception& e) {
        set_err(e.what());
        return nullptr;
    }
}

extern "C" ST_Tensor smile_msa_decode_select(ST_Tensor q, ST_Tensor cache, ST_Tensor slots, ST_Tensor positions,
                                             int length, int block_size, int topk, int init_blocks,
                                             int local_blocks, int seqlen) {
    try {
        smile_torch_clear_error();
        at::Tensor qt = require_cuda(q, "smile_msa_decode_select: q").contiguous();
        at::Tensor ct = require_cuda(cache, "smile_msa_decode_select: cache");
        at::Tensor st = require_cuda(slots, "smile_msa_decode_select: slots");
        at::Tensor pt = require_cuda(positions, "smile_msa_decode_select: positions");
        if (qt.dim() != 3 || ct.scalar_type() != qt.scalar_type() || !ct.is_contiguous()) {
            throw std::runtime_error("smile_msa_decode_select: q [B,G,D] and a contiguous cache of the same dtype");
        }
        const int tokens = (int) qt.size(0);
        const int batch = (int) pt.numel();
        const int groups = (int) qt.size(1);
        const int d = (int) qt.size(2);
        if (groups > kMsaMaxGroups || d % 32 != 0 || d > 256 || ct.size(-1) != d
            || (ct.dim() == 3 && ct.size(1) != 1)) {
            throw std::runtime_error("smile_msa_decode_select: unsupported shape (groups <= 8, one shared key head)");
        }
        if (seqlen <= 0 || tokens != batch * seqlen || st.scalar_type() != at::kLong
            || st.numel() != (int64_t) batch * length || pt.scalar_type() != at::kInt || topk <= 0) {
            throw std::runtime_error("smile_msa_decode_select: slots must be int64 [B*L], positions int32 [B]");
        }
        const int num_blocks = (length + block_size - 1) / block_size;
        if (num_blocks > kMsaMaxBlocks || block_size <= 0 || length <= 0) {
            throw std::runtime_error("smile_msa_decode_select: too many blocks for the shared-memory scorer");
        }
        st = st.contiguous();
        pt = pt.contiguous();
        c10::cuda::CUDAGuard guard(qt.get_device());
        at::Tensor scores = at::empty({tokens, groups, num_blocks}, qt.options().dtype(at::kFloat));
        at::Tensor sel = at::empty({tokens, groups, topk}, qt.options().dtype(at::kInt));
        const int threads1 = 128;
        const size_t smem1 = ((size_t) groups * d + (threads1 / 32) * groups) * sizeof(float);
        const size_t smem2 = (size_t) num_blocks * (sizeof(float) + 1);
        DISPATCH_FLOAT(qt.scalar_type(), "smile_msa_decode_select", [&] {
            for (int t0 = 0; t0 < tokens; t0 += kMaxGridY) {
                const int n = std::min(kMaxGridY, tokens - t0);
                msa_block_score_kernel<T><<<dim3(num_blocks, n), threads1, smem1, stream_of(qt)>>>(
                        qt.data_ptr<T>(), ct.data_ptr<T>(), st.data_ptr<int64_t>(), pt.data_ptr<int>(),
                        scores.data_ptr<float>(), groups, d, length, block_size, num_blocks, seqlen, t0);
                msa_select_mask_kernel<T><<<dim3(groups, n), 256, smem2, stream_of(qt)>>>(
                        scores.data_ptr<float>(), pt.data_ptr<int>(), nullptr, sel.data_ptr<int>(), groups, length,
                        block_size, num_blocks, topk, init_blocks, local_blocks, seqlen, t0);
            }
            return 0;
        });
        check_launch("smile_msa_decode_select");
        return new ST_Tensor_{ sel };
    } catch (const std::exception& e) {
        set_err(e.what());
        return nullptr;
    }
}

extern "C" ST_Tensor smile_sparse_decode_attention(ST_Tensor q, ST_Tensor k_layer, ST_Tensor v_layer,
                                                   ST_Tensor slots, ST_Tensor positions, ST_Tensor sel,
                                                   int length, double scale, int seqlen) {
    try {
        smile_torch_clear_error();
        at::Tensor qt = require_cuda(q, "smile_sparse_decode_attention: q").contiguous();
        at::Tensor kt = require_cuda(k_layer, "smile_sparse_decode_attention: k");
        at::Tensor vt = require_cuda(v_layer, "smile_sparse_decode_attention: v");
        at::Tensor st = require_cuda(slots, "smile_sparse_decode_attention: slots");
        at::Tensor pt = require_cuda(positions, "smile_sparse_decode_attention: positions");
        at::Tensor lt = require_cuda(sel, "smile_sparse_decode_attention: sel");
        if (qt.dim() != 3 || qt.scalar_type() != at::kBFloat16 || kt.scalar_type() != at::kBFloat16
            || vt.scalar_type() != at::kBFloat16 || !kt.is_contiguous() || !vt.is_contiguous()
            || kt.dim() != 3 || vt.sizes() != kt.sizes()) {
            throw std::runtime_error("smile_sparse_decode_attention: bf16 q [B,H,D] and contiguous k/v [slots,G,D]");
        }
        const int tokens = (int) qt.size(0);
        const int batch = (int) pt.numel();
        const int heads = (int) qt.size(1);
        const int kv_heads = (int) kt.size(1);
        if (qt.size(2) != kSdDim || kt.size(2) != kSdDim || kv_heads <= 0 || heads != kv_heads * kSdRep) {
            throw std::runtime_error("smile_sparse_decode_attention: needs head_dim 128 and 16 query heads per KV head");
        }
        if (lt.scalar_type() != at::kInt || lt.dim() != 3 || lt.size(0) != tokens || lt.size(1) != kv_heads
            || st.scalar_type() != at::kLong || st.numel() != (int64_t) batch * length
            || pt.scalar_type() != at::kInt || seqlen <= 0 || tokens != batch * seqlen) {
            throw std::runtime_error("smile_sparse_decode_attention: sel/slots/positions have the wrong shape or dtype");
        }
        const int topk = (int) lt.size(2);
        st = st.contiguous();
        pt = pt.contiguous();
        lt = lt.contiguous();
        c10::cuda::CUDAGuard guard(qt.get_device());
        // The attribute is per device (ranks run on different devices). Set it once per device, during the eager
        // warmup, so nothing but stream work happens while a CUDA graph is being captured.
        static bool attr_done[64] = {false};
        const int dev = qt.get_device();
        if (dev < 0 || dev >= 64) throw std::runtime_error("smile_sparse_decode_attention: device index out of range");
        if (!attr_done[dev]) {
            const cudaError_t attr = cudaFuncSetAttribute(
                    sparse_decode_partial_kernel, cudaFuncAttributeMaxDynamicSharedMemorySize, (int) kSdSmem);
            if (attr != cudaSuccess) {
                throw std::runtime_error(std::string("smile_sparse_decode_attention: ") + cudaGetErrorString(attr));
            }
            attr_done[dev] = true;
        }
        // Partial softmax state is chunk-local: bound the workspace (~256 MB) and loop over token chunks.
        const int64_t per_token = (int64_t) kv_heads * topk * kSdRep * kSdDim * 4;
        const int chunk = (int) std::max<int64_t>(1, std::min<int64_t>(
                std::min<int64_t>(tokens, kMaxGridY), (256LL << 20) / per_token));
        const int64_t parts = (int64_t) chunk * kv_heads * topk;
        at::Tensor part_o = at::empty({parts, kSdRep, kSdDim}, qt.options().dtype(at::kFloat));
        at::Tensor part_m = at::empty({parts, kSdRep}, qt.options().dtype(at::kFloat));
        at::Tensor part_l = at::empty({parts, kSdRep}, qt.options().dtype(at::kFloat));
        at::Tensor out = at::empty({tokens, heads, kSdDim}, qt.options());
        for (int t0 = 0; t0 < tokens; t0 += chunk) {
            const int n = std::min(chunk, tokens - t0);
            sparse_decode_partial_kernel<<<dim3(topk, kv_heads, n), 256, kSdSmem, stream_of(qt)>>>(
                    qt.data_ptr<at::BFloat16>(), kt.data_ptr<at::BFloat16>(), vt.data_ptr<at::BFloat16>(),
                    st.data_ptr<int64_t>(), pt.data_ptr<int>(), lt.data_ptr<int>(), part_o.data_ptr<float>(),
                    part_m.data_ptr<float>(), part_l.data_ptr<float>(), heads, kv_heads, length, topk,
                    (float) scale, seqlen, t0);
            sparse_decode_combine_kernel<<<dim3(kv_heads, n), 256, 0, stream_of(qt)>>>(
                    part_o.data_ptr<float>(), part_m.data_ptr<float>(), part_l.data_ptr<float>(),
                    out.data_ptr<at::BFloat16>(), heads, kv_heads, topk, t0);
        }
        check_launch("smile_sparse_decode_attention");
        return new ST_Tensor_{ out };
    } catch (const std::exception& e) {
        set_err(e.what());
        return nullptr;
    }
}
