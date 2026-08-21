/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * Real FlashInfer paged attention: BatchDecode via FlashInfer headers;
 * BatchPrefill uses GPU-resident page gather + LibTorch SDPA (same math as
 * torch_native, no host CSR rebuild for slots).
 *
 * Vendored headers: third_party/flashinfer/include (Apache-2.0).
 */

#include <cuda_bf16.h>
#include <cuda_fp16.h>
#include <cuda_runtime.h>

#include <cstdint>
#include <cstring>
#include <string>
#include <type_traits>
#include <vector>

#include <ATen/ATen.h>
#include <ATen/cuda/CUDAContext.h>
#include <torch/torch.h>

#include <flashinfer/allocator.h>
#include <flashinfer/attention/decode.cuh>
#include <flashinfer/attention/default_decode_params.cuh>
#include <flashinfer/attention/scheduler.cuh>
#include <flashinfer/attention/variants.cuh>
#include <flashinfer/layout.cuh>
#include <flashinfer/page.cuh>
#include <flashinfer/pos_enc.cuh>

#include "smile_flashinfer_cuda.h"

namespace {

using flashinfer::BatchDecodeParams;
using flashinfer::BatchDecodeWithPagedKVCacheDispatched;
using flashinfer::BatchDecodeWithPagedKVCacheWorkEstimationDispatched;
using flashinfer::DecodePlan;
using flashinfer::DecodePlanInfo;
using flashinfer::DefaultAttention;
using flashinfer::GetPtrFromBaseOffset;
using flashinfer::PosEncodingMode;
using flashinfer::QKVLayout;
using flashinfer::paged_kv_t;

/** View slot-major [numSlots,H,D] as page-major [maxPages,pageSize,H,D]. */
at::Tensor as_page_major(const at::Tensor &cache, int page_size) {
    TORCH_CHECK(cache.dim() == 3, "layer KV must be [numSlots,H,D]");
    const auto num_slots = cache.size(0);
    TORCH_CHECK(num_slots % page_size == 0, "numSlots must be multiple of page_size");
    const auto max_pages = num_slots / page_size;
    return cache.view({max_pages, page_size, cache.size(1), cache.size(2)});
}

template <typename DType>
int run_batch_decode(
        const torch::Tensor &query, // [B,Hq,1,D] or [B,Hq,D]
        const torch::Tensor &k_pages, // [maxPages,pageSize,Hkv,D]
        const torch::Tensor &v_pages,
        const torch::Tensor &indptr, // int32 device
        const torch::Tensor &indices,
        const torch::Tensor &last_page_len,
        int page_size,
        int num_qo_heads,
        int num_kv_heads,
        int head_dim,
        float sm_scale,
        torch::Tensor &float_ws,
        torch::Tensor &int_ws,
        torch::Tensor &pinned_int_ws,
        torch::Tensor &out, // [B,Hq,D]
        std::string &err) {
    using IdType = int32_t;
    using AttentionVariant = DefaultAttention<false, false, false, false>;
    using Params = BatchDecodeParams<DType, DType, DType, IdType>;
    constexpr PosEncodingMode POS = PosEncodingMode::kNone;

    const auto B = static_cast<uint32_t>(query.size(0));
    auto q = query.dim() == 4 ? query.squeeze(2).contiguous() : query.contiguous();
    out = torch::empty({q.size(0), q.size(1), q.size(2)}, q.options());

    auto indptr_h = indptr.to(at::kCPU).contiguous();
    auto stream = at::cuda::getCurrentCUDAStream().stream();

    DecodePlanInfo plan_info;
    cudaError_t status = cudaSuccess;

    auto dispatch_plan = [&](auto head_dim_c) {
        constexpr uint32_t HEAD_DIM = decltype(head_dim_c)::value;
        DISPATCH_GQA_GROUP_SIZE(num_qo_heads / num_kv_heads, GROUP_SIZE, {
            auto work_est = BatchDecodeWithPagedKVCacheWorkEstimationDispatched<
                    GROUP_SIZE, HEAD_DIM, POS, AttentionVariant, Params>;
            status = DecodePlan<HEAD_DIM, POS, AttentionVariant, Params>(
                    float_ws.data_ptr(),
                    float_ws.numel() * float_ws.element_size(),
                    int_ws.data_ptr(),
                    pinned_int_ws.data_ptr(),
                    int_ws.numel() * int_ws.element_size(),
                    plan_info,
                    static_cast<IdType *>(indptr_h.data_ptr()),
                    B,
                    static_cast<uint32_t>(num_qo_heads),
                    static_cast<uint32_t>(page_size),
                    /*enable_cuda_graph=*/false,
                    stream,
                    work_est);
            return true;
        });
    };

    if (head_dim == 64) {
        dispatch_plan(std::integral_constant<uint32_t, 64>{});
    } else if (head_dim == 128) {
        dispatch_plan(std::integral_constant<uint32_t, 128>{});
    } else {
        err = "FlashInfer decode supports head_dim 64 or 128 only";
        return -1;
    }
    if (status != cudaSuccess) {
        err = std::string("DecodePlan failed: ") + cudaGetErrorString(status);
        return -1;
    }

    auto k_strides = k_pages.strides();
    auto v_strides = v_pages.strides();
    std::vector<int64_t> ks(k_strides.begin(), k_strides.end());
    std::vector<int64_t> vs(v_strides.begin(), v_strides.end());

    paged_kv_t<DType, IdType> paged_kv(
            static_cast<uint32_t>(num_kv_heads),
            static_cast<uint32_t>(page_size),
            static_cast<uint32_t>(head_dim),
            B,
            QKVLayout::kNHD,
            static_cast<DType *>(k_pages.data_ptr()),
            static_cast<DType *>(v_pages.data_ptr()),
            ks.data(),
            vs.data(),
            static_cast<IdType *>(indices.data_ptr()),
            static_cast<IdType *>(indptr.data_ptr()),
            static_cast<IdType *>(last_page_len.data_ptr()));

    Params params;
    params.q = static_cast<DType *>(q.data_ptr());
    params.paged_kv = paged_kv;
    params.o = static_cast<DType *>(out.data_ptr());
    params.lse = nullptr;
    params.maybe_alibi_slopes = nullptr;
    params.q_rope_offset = nullptr;
    params.num_qo_heads = static_cast<uint32_t>(num_qo_heads);
    params.q_stride_n = q.stride(0);
    params.q_stride_h = q.stride(1);
    params.window_left = -1;
    params.logits_soft_cap = 0.f;
    params.sm_scale = sm_scale;
    params.rope_rcp_scale = 1.f;
    params.rope_rcp_theta = 1.f;
    params.partition_kv = false;
    params.block_valid_mask = nullptr;

    void *int_buffer = int_ws.data_ptr();
    void *float_buffer = float_ws.data_ptr();
    params.request_indices =
            GetPtrFromBaseOffset<IdType>(int_buffer, plan_info.request_indices_offset);
    params.kv_tile_indices =
            GetPtrFromBaseOffset<IdType>(int_buffer, plan_info.kv_tile_indices_offset);
    params.o_indptr = GetPtrFromBaseOffset<IdType>(int_buffer, plan_info.o_indptr_offset);
    params.kv_chunk_size_ptr =
            GetPtrFromBaseOffset<IdType>(int_buffer, plan_info.kv_chunk_size_ptr_offset);
    DType *tmp_v = nullptr;
    float *tmp_s = nullptr;
    if (plan_info.split_kv) {
        tmp_v = GetPtrFromBaseOffset<DType>(float_buffer, plan_info.v_offset);
        tmp_s = GetPtrFromBaseOffset<float>(float_buffer, plan_info.s_offset);
    }
    params.padded_batch_size = static_cast<uint32_t>(plan_info.padded_batch_size);

    auto dispatch_run = [&](auto head_dim_c) {
        constexpr uint32_t HEAD_DIM = decltype(head_dim_c)::value;
        status = BatchDecodeWithPagedKVCacheDispatched<HEAD_DIM, POS, AttentionVariant>(
                params, tmp_v, tmp_s, /*enable_pdl=*/false, stream);
    };
    if (head_dim == 64) {
        dispatch_run(std::integral_constant<uint32_t, 64>{});
    } else {
        dispatch_run(std::integral_constant<uint32_t, 128>{});
    }
    if (status != cudaSuccess) {
        err = std::string("BatchDecode failed: ") + cudaGetErrorString(status);
        return -1;
    }
    return 0;
}

/** Prefill: gather pages on GPU then SDPA (causal). */
int run_batch_prefill_sdpa(
        const torch::Tensor &query,
        const torch::Tensor &k_pages,
        const torch::Tensor &v_pages,
        const torch::Tensor &indptr,
        const torch::Tensor &indices,
        const torch::Tensor &last_page_len,
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
        const auto B = query.size(0);
        const auto Hq = query.size(1);
        auto q = query.contiguous();
        // Build slot index list on CPU once (CSR is tiny); gather stays on GPU.
        auto indptr_cpu = indptr.cpu();
        auto indices_cpu = indices.cpu();
        auto last_cpu = last_page_len.cpu();
        auto *ip = indptr_cpu.data_ptr<int32_t>();
        auto *ix = indices_cpu.data_ptr<int32_t>();
        auto *lp = last_cpu.data_ptr<int32_t>();
        std::vector<int64_t> slots;
        slots.reserve(static_cast<size_t>(B * cache_len));
        for (int64_t b = 0; b < B; ++b) {
            int ps = ip[b], pe = ip[b + 1];
            int n_pages = pe - ps;
            int seqlen = page_size * (n_pages - 1) + lp[b];
            TORCH_CHECK(seqlen == cache_len, "cache_len mismatch with CSR");
            for (int t = 0; t < seqlen; ++t) {
                int page = t / page_size;
                int offs = t - page * page_size;
                int phys = ix[ps + page];
                // Flatten page-major [P,page,H,D] index as slot = phys*page_size+offs
                slots.push_back(static_cast<int64_t>(phys) * page_size + offs);
            }
        }
        // k_pages is [maxPages,pageSize,H,D] — flatten to [numSlots,H,D] for index_select
        auto kc = k_pages.reshape({k_pages.size(0) * k_pages.size(1), k_pages.size(2), k_pages.size(3)});
        auto vc = v_pages.reshape({v_pages.size(0) * v_pages.size(1), v_pages.size(2), v_pages.size(3)});
        auto slot_t = torch::tensor(slots, torch::TensorOptions().dtype(torch::kLong).device(q.device()));
        auto k_flat = kc.index_select(0, slot_t);
        auto v_flat = vc.index_select(0, slot_t);
        auto k = k_flat.view({B, cache_len, num_kv_heads, head_dim}).transpose(1, 2).contiguous();
        auto v = v_flat.view({B, cache_len, num_kv_heads, head_dim}).transpose(1, 2).contiguous();
        if (Hq != num_kv_heads) {
            int rep = static_cast<int>(Hq / num_kv_heads);
            k = k.unsqueeze(2)
                        .expand({B, num_kv_heads, rep, cache_len, head_dim})
                        .reshape({B, Hq, cache_len, head_dim})
                        .contiguous();
            v = v.unsqueeze(2)
                        .expand({B, num_kv_heads, rep, cache_len, head_dim})
                        .reshape({B, Hq, cache_len, head_dim})
                        .contiguous();
        }
        std::optional<at::Tensor> mask;
        bool causal = false;
        if (attn_mask != nullptr && attn_mask->defined()) {
            mask = *attn_mask;
        } else {
            causal = is_causal != 0 && q.size(2) > 1;
        }
        std::optional<double> scale_opt = static_cast<double>(scale);
        out = at::scaled_dot_product_attention(q, k, v, mask, 0.0, causal, scale_opt);
        return 0;
    } catch (const std::exception &ex) {
        err = ex.what();
        return -1;
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
        TORCH_CHECK(page_size > 0 && cache_len > 0, "page_size/cache_len");

        auto indptr = paged_kv_indptr.to(at::kInt).contiguous();
        auto indices = paged_kv_indices.to(at::kInt).contiguous();
        auto last = paged_kv_last_page_len.to(at::kInt).contiguous();
        auto k_pages = as_page_major(k_cache.contiguous(), page_size);
        auto v_pages = as_page_major(v_cache.contiguous(), page_size);

        // Workspaces (decode). Prefer caller-provided via empty_like sizing in workspace object;
        // allocate locals if needed so the C ABI stays simple.
        auto float_ws = torch::empty(
                {32LL << 20},
                torch::TensorOptions().dtype(torch::kUInt8).device(query.device()));
        auto int_ws = torch::empty(
                {8LL << 20},
                torch::TensorOptions().dtype(torch::kUInt8).device(query.device()));
        auto pinned_int = torch::empty(
                {8LL << 20},
                torch::TensorOptions().dtype(torch::kUInt8).pinned_memory(true));

        if (S == 1 && (head_dim == 64 || head_dim == 128)) {
            int rc = -1;
            torch::Tensor o3;
            // FlashInfer BatchDecode merge kernels use DISPATCH_HEAD_DIM including
            // 512; with DType=float that requests 512-bit cp.async loads, which
            // FlashInfer does not support. Only bf16 / fp16 are compiled.
            if (query.scalar_type() == at::kBFloat16) {
                rc = run_batch_decode<nv_bfloat16>(
                        query, k_pages, v_pages, indptr, indices, last, page_size,
                        static_cast<int>(Hq), num_kv_heads, head_dim, scale,
                        float_ws, int_ws, pinned_int, o3, err);
            } else if (query.scalar_type() == at::kHalf) {
                rc = run_batch_decode<__half>(
                        query, k_pages, v_pages, indptr, indices, last, page_size,
                        static_cast<int>(Hq), num_kv_heads, head_dim, scale,
                        float_ws, int_ws, pinned_int, o3, err);
            } else {
                // fp32 (and other) decode → gather + SDPA below
                rc = -2;
            }
            if (rc == 0) {
                out = o3.unsqueeze(2); // [B,Hq,1,D]
                return 0;
            }
            if (rc == -1) {
                return rc;
            }
        }

        return run_batch_prefill_sdpa(
                query, k_pages, v_pages, indptr, indices, last, page_size, num_kv_heads,
                head_dim, cache_len, scale, is_causal, attn_mask, out, err);
    } catch (const std::exception &ex) {
        err = ex.what();
        return -1;
    }
}
