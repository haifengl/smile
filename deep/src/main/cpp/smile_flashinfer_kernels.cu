/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * CUDA kernels for FlashInfer-compatible paged attention (CSR page table).
 * Layout: k/v cache [numSlots, Hkv, D]; pages are page_size consecutive slots.
 *
 * Prefill and decode both gather pages then call LibTorch SDPA so masking
 * matches torch_native (explicit additive mask preferred over is_causal).
 */

#include <cuda_runtime.h>

#include <cmath>
#include <cstdint>
#include <string>
#include <vector>

#include <ATen/ATen.h>
#include <ATen/cuda/CUDAContext.h>
#include <torch/torch.h>

#include "smile_flashinfer_cuda.h"

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
        const torch::Tensor *attn_mask,
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
        TORCH_CHECK(page_size > 0, "page_size must be > 0");
        TORCH_CHECK(cache_len > 0, "cache_len must be > 0");

        auto q = query.contiguous();
        auto kc = k_cache.contiguous();
        auto vc = v_cache.contiguous();
        auto indptr = paged_kv_indptr.to(at::kInt).contiguous();
        auto indices = paged_kv_indices.to(at::kInt).contiguous();
        auto last = paged_kv_last_page_len.to(at::kInt).contiguous();

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
            TORCH_CHECK(n_pages > 0, "empty page table for batch element");
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

        // Match torch_native: prefer explicit additive mask; never set both.
        std::optional<at::Tensor> mask;
        bool causal = false;
        if (attn_mask != nullptr && attn_mask->defined()) {
            mask = *attn_mask;
        } else {
            causal = is_causal != 0 && S > 1;
        }
        std::optional<double> scale_opt = static_cast<double>(scale);
        out = at::scaled_dot_product_attention(
                q, k, v, mask, 0.0, causal, scale_opt);
        at::cuda::CUDAStream stream = at::cuda::getCurrentCUDAStream();
        stream.synchronize();
        return 0;
    } catch (const std::exception &ex) {
        err = ex.what();
        return -1;
    }
}
