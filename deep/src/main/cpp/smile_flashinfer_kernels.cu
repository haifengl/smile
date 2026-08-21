/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * CUDA kernels for FlashInfer-compatible paged attention (CSR page table).
 * Layout: k/v cache [numSlots, Hkv, D]; pages are page_size consecutive slots.
 */

#include <cuda_bf16.h>
#include <cuda_fp16.h>
#include <cuda_runtime.h>

#include <cmath>
#include <cstdint>
#include <string>
#include <vector>

#include <ATen/ATen.h>
#include <ATen/cuda/CUDAContext.h>
#include <torch/torch.h>

#include "smile_flashinfer_cuda.h"

namespace {

__device__ inline float load_f(nv_bfloat16 x) { return __bfloat162float(x); }
__device__ inline float load_f(__half x) { return __half2float(x); }
__device__ inline float load_f(float x) { return x; }

__device__ inline nv_bfloat16 store_f(float x, nv_bfloat16) {
    return __float2bfloat16(x);
}
__device__ inline __half store_f(float x, __half) { return __float2half(x); }
__device__ inline float store_f(float x, float) { return x; }

template <typename T>
__global__ void paged_decode_kernel(
        const T *__restrict__ q,       // [B, Hq, 1, D]
        const T *__restrict__ k_cache, // [numSlots, Hkv, D]
        const T *__restrict__ v_cache,
        const int32_t *__restrict__ indptr,
        const int32_t *__restrict__ indices,
        const int32_t *__restrict__ last_page_len,
        T *__restrict__ out, // [B, Hq, 1, D]
        int B, int Hq, int Hkv, int D, int page_size, float scale) {
    const int bh = blockIdx.x;
    if (bh >= B * Hq) return;
    const int b = bh / Hq;
    const int hq = bh - b * Hq;
    const int hkv = hq * Hkv / Hq;
    const int tid = threadIdx.x;

    const int page_start = indptr[b];
    const int page_end = indptr[b + 1];
    const int n_pages = page_end - page_start;
    if (n_pages <= 0) {
        for (int d = tid; d < D; d += blockDim.x) {
            out[(b * Hq + hq) * D + d] = store_f(0.f, T{});
        }
        return;
    }
    const int seqlen = page_size * (n_pages - 1) + last_page_len[b];

    extern __shared__ float scores[];

    for (int t = tid; t < seqlen; t += blockDim.x) {
        const int page = t / page_size;
        const int offs = t - page * page_size;
        const int phys_page = indices[page_start + page];
        const int slot = phys_page * page_size + offs;
        float dot = 0.f;
        for (int d = 0; d < D; ++d) {
            float qv = load_f(q[(b * Hq + hq) * D + d]);
            float kv = load_f(k_cache[(slot * (long long)Hkv + hkv) * D + d]);
            dot += qv * kv;
        }
        scores[t] = dot * scale;
    }
    __syncthreads();

    if (tid == 0) {
        float mm = -INFINITY;
        for (int t = 0; t < seqlen; ++t) mm = fmaxf(mm, scores[t]);
        float sum = 0.f;
        for (int t = 0; t < seqlen; ++t) {
            scores[t] = expf(scores[t] - mm);
            sum += scores[t];
        }
        float inv = 1.f / sum;
        for (int t = 0; t < seqlen; ++t) scores[t] *= inv;
    }
    __syncthreads();

    for (int d = tid; d < D; d += blockDim.x) {
        float acc = 0.f;
        for (int t = 0; t < seqlen; ++t) {
            const int page = t / page_size;
            const int offs = t - page * page_size;
            const int phys_page = indices[page_start + page];
            const int slot = phys_page * page_size + offs;
            float vv = load_f(v_cache[(slot * (long long)Hkv + hkv) * D + d]);
            acc += scores[t] * vv;
        }
        out[(b * Hq + hq) * D + d] = store_f(acc, T{});
    }
}

} // namespace

extern "C" int smile_flashinfer_paged_attention_cuda(
        const torch::Tensor &query,
        const torch::Tensor &k_cache,
        const torch::Tensor &v_cache,
        const torch::Tensor &paged_kv_indptr,
        const torch::Tensor &paged_kv_indices,
        const torch::Tensor &paged_kv_last_page_len,
        int page_size,
        int num_kv_heads,
        int head_dim,
        int cache_len,
        float scale,
        int is_causal,
        torch::Tensor &out,
        std::string &err) {
    try {
        TORCH_CHECK(query.is_cuda(), "query must be CUDA");
        TORCH_CHECK(query.dim() == 4, "query must be [B,H,S,D]");
        const auto B = query.size(0);
        const auto Hq = query.size(1);
        const auto S = query.size(2);
        const auto D = query.size(3);
        TORCH_CHECK(D == head_dim, "head_dim mismatch");
        TORCH_CHECK(num_kv_heads > 0 && Hq % num_kv_heads == 0, "invalid GQA heads");

        auto q = query.contiguous();
        auto kc = k_cache.contiguous();
        auto vc = v_cache.contiguous();
        auto indptr = paged_kv_indptr.to(at::kInt).contiguous();
        auto indices = paged_kv_indices.to(at::kInt).contiguous();
        auto last = paged_kv_last_page_len.to(at::kInt).contiguous();

        if (S > 1) {
            std::vector<int64_t> slots;
            slots.reserve(static_cast<size_t>(B * cache_len));
            auto indptr_cpu = indptr.cpu();
            auto indices_cpu = indices.cpu();
            auto last_cpu = last.cpu();
            auto *ip = indptr_cpu.data_ptr<int32_t>();
            auto *ix = indices_cpu.data_ptr<int32_t>();
            auto *lp = last_cpu.data_ptr<int32_t>();
            for (int64_t b = 0; b < B; ++b) {
                int ps = ip[b], pe = ip[b + 1];
                int n_pages = pe - ps;
                int seqlen = page_size * (n_pages - 1) + lp[b];
                TORCH_CHECK(seqlen == cache_len, "cache_len mismatch with CSR");
                for (int t = 0; t < seqlen; ++t) {
                    int page = t / page_size;
                    int offs = t - page * page_size;
                    int phys = ix[ps + page];
                    slots.push_back(static_cast<int64_t>(phys) * page_size + offs);
                }
            }
            auto slot_t = torch::tensor(slots, torch::TensorOptions().dtype(torch::kLong))
                                  .to(q.device());
            auto k_flat = kc.index_select(0, slot_t);
            auto v_flat = vc.index_select(0, slot_t);
            auto k = k_flat.view({B, cache_len, num_kv_heads, D})
                             .transpose(1, 2).contiguous();
            auto v = v_flat.view({B, cache_len, num_kv_heads, D})
                             .transpose(1, 2).contiguous();
            if (Hq != num_kv_heads) {
                int rep = static_cast<int>(Hq / num_kv_heads);
                k = k.unsqueeze(2)
                            .expand({B, num_kv_heads, rep, cache_len, D})
                            .reshape({B, Hq, cache_len, D})
                            .contiguous();
                v = v.unsqueeze(2)
                            .expand({B, num_kv_heads, rep, cache_len, D})
                            .reshape({B, Hq, cache_len, D})
                            .contiguous();
            }
            out = at::scaled_dot_product_attention(
                    q, k, v, c10::nullopt, 0.0, static_cast<bool>(is_causal), scale);
            return 0;
        }

        TORCH_CHECK(cache_len <= 8192, "decode flashinfer kernel shared-mem seqlen cap exceeded");
        const int threads = 128;
        const size_t smem = static_cast<size_t>(cache_len) * sizeof(float);
        out = torch::empty_like(q);
        auto stream = at::cuda::getCurrentCUDAStream();
        // q is [B,Hq,1,D] — kernel indexes as (b*Hq+hq)*D+d
        auto q2 = q.reshape({B, Hq, D}).contiguous();
        out = torch::empty_like(q2);

        if (q.scalar_type() == at::kBFloat16) {
            paged_decode_kernel<nv_bfloat16>
                    <<<static_cast<unsigned>(B * Hq), threads, smem, stream.stream()>>>(
                            reinterpret_cast<const nv_bfloat16 *>(q2.data_ptr()),
                            reinterpret_cast<const nv_bfloat16 *>(kc.data_ptr()),
                            reinterpret_cast<const nv_bfloat16 *>(vc.data_ptr()),
                            indptr.data_ptr<int32_t>(), indices.data_ptr<int32_t>(),
                            last.data_ptr<int32_t>(),
                            reinterpret_cast<nv_bfloat16 *>(out.data_ptr()),
                            static_cast<int>(B), static_cast<int>(Hq), num_kv_heads,
                            static_cast<int>(D), page_size, scale);
        } else if (q.scalar_type() == at::kHalf) {
            paged_decode_kernel<__half>
                    <<<static_cast<unsigned>(B * Hq), threads, smem, stream.stream()>>>(
                            reinterpret_cast<const __half *>(q2.data_ptr()),
                            reinterpret_cast<const __half *>(kc.data_ptr()),
                            reinterpret_cast<const __half *>(vc.data_ptr()),
                            indptr.data_ptr<int32_t>(), indices.data_ptr<int32_t>(),
                            last.data_ptr<int32_t>(),
                            reinterpret_cast<__half *>(out.data_ptr()),
                            static_cast<int>(B), static_cast<int>(Hq), num_kv_heads,
                            static_cast<int>(D), page_size, scale);
        } else if (q.scalar_type() == at::kFloat) {
            paged_decode_kernel<float>
                    <<<static_cast<unsigned>(B * Hq), threads, smem, stream.stream()>>>(
                            q2.data_ptr<float>(), kc.data_ptr<float>(), vc.data_ptr<float>(),
                            indptr.data_ptr<int32_t>(), indices.data_ptr<int32_t>(),
                            last.data_ptr<int32_t>(), out.data_ptr<float>(),
                            static_cast<int>(B), static_cast<int>(Hq), num_kv_heads,
                            static_cast<int>(D), page_size, scale);
        } else {
            err = "unsupported dtype for flashinfer paged attention";
            return -1;
        }
        cudaError_t e = cudaGetLastError();
        if (e != cudaSuccess) {
            err = cudaGetErrorString(e);
            return -1;
        }
        out = out.view({B, Hq, 1, D});
        return 0;
    } catch (const std::exception &ex) {
        err = ex.what();
        return -1;
    }
}
