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

/** Block-max of the raw index dot products. Grid (numBlocks, B); one CTA scores one 128-key block for all groups. */
template <typename T>
__global__ void msa_block_score_kernel(const T* __restrict__ q, const T* __restrict__ cache,
                                       const int64_t* __restrict__ slots, const int* __restrict__ positions,
                                       float* __restrict__ scores, int groups, int d, int length, int block_size,
                                       int num_blocks) {
    const int blk = blockIdx.x;
    const int b = blockIdx.y;
    const int pos = positions[b];
    const int first = blk * block_size;
    const int warp = threadIdx.x >> 5;
    const int lane = threadIdx.x & 31;
    const int warps = blockDim.x >> 5;
    float* out = scores + ((int64_t) b * groups) * num_blocks + blk;
    if (first > pos) {
        if (threadIdx.x < groups) out[(int64_t) threadIdx.x * num_blocks] = -INFINITY;
        return;
    }
    extern __shared__ float smem[];
    float* qs = smem;                      // [groups * d]
    float* red = smem + groups * d;        // [warps * groups]
    for (int i = threadIdx.x; i < groups * d; i += blockDim.x) {
        qs[i] = static_cast<float>(q[(int64_t) b * groups * d + i]);
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
                                       T* __restrict__ mask, int groups, int length, int block_size,
                                       int num_blocks, int topk, int init_blocks, int local_blocks) {
    const int g = blockIdx.x;
    const int b = blockIdx.y;
    const int pos = positions[b];
    const int visible = pos / block_size + 1;
    const int local_start = max(0, visible - local_blocks);
    extern __shared__ float smem[];
    float* s = smem;                                              // [num_blocks]
    unsigned char* chosen = reinterpret_cast<unsigned char*>(s + num_blocks);
    __shared__ float red_v[32];
    __shared__ int red_i[32];
    const float* src = scores + ((int64_t) b * groups + g) * num_blocks;
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
                s[fi] = -INFINITY;
            }
        }
        __syncthreads();
    }
    T* dst = mask + ((int64_t) b * groups + g) * length;
    const T zero = static_cast<T>(0.f);
    const T neg_inf = static_cast<T>(-INFINITY);
    for (int l = threadIdx.x; l < length; l += blockDim.x) {
        dst[l] = (l <= pos && chosen[l / block_size]) ? zero : neg_inf;
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
                    scores.data_ptr<float>(), groups, d, length, block_size, num_blocks);
            msa_select_mask_kernel<T><<<dim3(groups, batch), 256, smem2, stream_of(qt)>>>(
                    scores.data_ptr<float>(), pt.data_ptr<int>(), mask.data_ptr<T>(), groups, length, block_size,
                    num_blocks, topk, init_blocks, local_blocks);
            return 0;
        });
        check_launch("smile_msa_decode_mask");
        return new ST_Tensor_{ mask };
    } catch (const std::exception& e) {
        set_err(e.what());
        return nullptr;
    }
}
