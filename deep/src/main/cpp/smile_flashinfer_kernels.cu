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

#include <atomic>
#include <cstdint>
#include <cstdio>
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
#include <flashinfer/attention/default_prefill_params.cuh>
#include <flashinfer/attention/mask.cuh>
#include <flashinfer/attention/prefill.cuh>
#include <flashinfer/attention/scheduler.cuh>
#include <flashinfer/attention/variants.cuh>
#include <flashinfer/layout.cuh>
#include <flashinfer/page.cuh>
#include <flashinfer/pos_enc.cuh>
#include <flashinfer/utils.cuh>

#include "smile_flashinfer_cuda.h"

namespace {

std::atomic<bool> g_flashinfer_sdpa_decode_warned{false};

/** Per-workspace decode plan + prefill gather cache (one plan/slot table per step). */
struct WorkspaceRuntimeCache {
    DecodePlanInfo decode_plan;
    bool decode_plan_valid = false;
    uint32_t decode_batch = 0;
    int decode_head_dim = 0;
    int decode_page_size = 0;
    int decode_num_qo_heads = 0;
    int decode_gqa_group = 0;
    std::vector<int32_t> decode_indptr;
    std::vector<int32_t> decode_last_page_len;

    torch::Tensor prefill_slots;
    bool prefill_slots_valid = false;
    int prefill_sl = 0;
    int prefill_page_size = 0;
    std::vector<int32_t> prefill_indptr;
    std::vector<int32_t> prefill_indices;

    void invalidate() {
        decode_plan_valid = false;
        prefill_slots_valid = false;
        prefill_slots = torch::Tensor();
    }
};

WorkspaceRuntimeCache *ensure_runtime_cache(void **slot) {
    if (*slot == nullptr) {
        *slot = new WorkspaceRuntimeCache();
    }
    return static_cast<WorkspaceRuntimeCache *>(*slot);
}

bool decode_plan_matches(
        const WorkspaceRuntimeCache &cache,
        uint32_t batch,
        int head_dim,
        int page_size,
        int num_qo_heads,
        int gqa_group,
        const int32_t *indptr,
        const int32_t *last_page_len) {
    if (!cache.decode_plan_valid) {
        return false;
    }
    if (cache.decode_batch != batch || cache.decode_head_dim != head_dim
            || cache.decode_page_size != page_size
            || cache.decode_num_qo_heads != num_qo_heads
            || cache.decode_gqa_group != gqa_group) {
        return false;
    }
    if (cache.decode_indptr.size() != batch + 1
            || cache.decode_last_page_len.size() != batch) {
        return false;
    }
    for (uint32_t i = 0; i <= batch; ++i) {
        if (cache.decode_indptr[i] != indptr[i]) {
            return false;
        }
    }
    for (uint32_t i = 0; i < batch; ++i) {
        if (cache.decode_last_page_len[i] != last_page_len[i]) {
            return false;
        }
    }
    return true;
}

bool prefill_slots_match(
        const WorkspaceRuntimeCache &cache,
        int sl,
        int page_size,
        const int32_t *indptr,
        int batch,
        const int32_t *indices,
        int num_indices) {
    if (!cache.prefill_slots_valid || !cache.prefill_slots.defined()) {
        return false;
    }
    if (cache.prefill_sl != sl || cache.prefill_page_size != page_size) {
        return false;
    }
    if (cache.prefill_indptr.size() != static_cast<size_t>(batch + 1)
            || cache.prefill_indices.size() != static_cast<size_t>(num_indices)) {
        return false;
    }
    for (int i = 0; i <= batch; ++i) {
        if (cache.prefill_indptr[static_cast<size_t>(i)] != indptr[i]) {
            return false;
        }
    }
    for (int i = 0; i < num_indices; ++i) {
        if (cache.prefill_indices[static_cast<size_t>(i)] != indices[i]) {
            return false;
        }
    }
    return true;
}

void warn_flashinfer_sdpa_decode_once(const char *reason) {
    if (!g_flashinfer_sdpa_decode_warned.exchange(true)) {
        fprintf(stderr,
                "WARN smile: FlashInfer decode falling back to gather+SDPA (%s); "
                "subsequent fallbacks suppressed\n",
                reason ? reason : "unknown");
        fflush(stderr);
    }
}

using flashinfer::BatchDecodeParams;
using flashinfer::BatchDecodeWithPagedKVCacheDispatched;
using flashinfer::BatchDecodeWithPagedKVCacheWorkEstimationDispatched;
using flashinfer::BatchPrefillRaggedParams;
using flashinfer::BatchPrefillWithRaggedKVCacheDispatched;
using flashinfer::DecodePlan;
using flashinfer::DecodePlanInfo;
using flashinfer::DefaultAttention;
using flashinfer::GetPtrFromBaseOffset;
using flashinfer::MaskMode;
using flashinfer::PosEncodingMode;
using flashinfer::PrefillPlan;
using flashinfer::PrefillPlanInfo;
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
        void **runtime_cache_slot,
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
    auto last_h = last_page_len.to(at::kCPU).contiguous();
    auto *indptr_ptr = static_cast<IdType *>(indptr_h.data_ptr());
    auto *last_ptr = static_cast<IdType *>(last_h.data_ptr());
    auto stream = at::cuda::getCurrentCUDAStream().stream();

    WorkspaceRuntimeCache *cache = runtime_cache_slot != nullptr
            ? ensure_runtime_cache(runtime_cache_slot)
            : nullptr;
    DecodePlanInfo plan_info;
    const int gqa_group = num_qo_heads / num_kv_heads;
    const bool plan_hit = cache != nullptr && decode_plan_matches(
            *cache, B, head_dim, page_size, num_qo_heads, gqa_group,
            indptr_ptr, last_ptr);
    if (plan_hit) {
        plan_info = cache->decode_plan;
    }
    cudaError_t status = cudaSuccess;

    if (!plan_hit) {
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
                        indptr_ptr,
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
        } else if (head_dim == 256) {
            dispatch_plan(std::integral_constant<uint32_t, 256>{});
        } else if (head_dim == 512) {
            dispatch_plan(std::integral_constant<uint32_t, 512>{});
        } else {
            err = "FlashInfer decode supports head_dim 64, 128, 256, or 512 only";
            return -1;
        }
        if (status != cudaSuccess) {
            err = std::string("DecodePlan failed: ") + cudaGetErrorString(status);
            return -1;
        }
        if (cache != nullptr) {
            cache->decode_plan = plan_info;
            cache->decode_plan_valid = true;
            cache->decode_batch = B;
            cache->decode_head_dim = head_dim;
            cache->decode_page_size = page_size;
            cache->decode_num_qo_heads = num_qo_heads;
            cache->decode_gqa_group = gqa_group;
            cache->decode_indptr.assign(indptr_ptr, indptr_ptr + B + 1);
            cache->decode_last_page_len.assign(last_ptr, last_ptr + B);
        }
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
    } else if (head_dim == 128) {
        dispatch_run(std::integral_constant<uint32_t, 128>{});
    } else if (head_dim == 256) {
        dispatch_run(std::integral_constant<uint32_t, 256>{});
    } else {
        dispatch_run(std::integral_constant<uint32_t, 512>{});
    }
    if (status != cudaSuccess) {
        err = std::string("BatchDecode failed: ") + cudaGetErrorString(status);
        return -1;
    }
    return 0;
}

/** Prefill / fp32 decode: gather pages on GPU then SDPA (causal when {@code S > 1}). */
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
        void **runtime_cache_slot,
        torch::Tensor &out,
        std::string &err) {
    try {
        const auto B = query.size(0);
        const auto Hq = query.size(1);
        const auto S = query.size(2);
        auto q = query.contiguous();
        auto indptr_cpu = indptr.cpu();
        auto indices_cpu = indices.cpu();
        auto last_cpu = last_page_len.cpu();
        auto *ip = indptr_cpu.data_ptr<int32_t>();
        auto *ix = indices_cpu.data_ptr<int32_t>();
        auto *lp = last_cpu.data_ptr<int32_t>();

        std::vector<int> seqlens(static_cast<size_t>(B));
        bool uniform = true;
        for (int64_t b = 0; b < B; ++b) {
            int ps = ip[b];
            int n_pages = ip[b + 1] - ps;
            int sl = n_pages > 0 ? page_size * (n_pages - 1) + lp[b] : 0;
            seqlens[static_cast<size_t>(b)] = sl;
            if (b > 0 && sl != seqlens[0]) {
                uniform = false;
            }
        }
        if (cache_len > 0 && uniform) {
            TORCH_CHECK(seqlens[0] == cache_len, "cache_len mismatch with CSR");
        }

        auto kc = k_pages.reshape({k_pages.size(0) * k_pages.size(1), k_pages.size(2), k_pages.size(3)});
        auto vc = v_pages.reshape({v_pages.size(0) * v_pages.size(1), v_pages.size(2), v_pages.size(3)});

        WorkspaceRuntimeCache *cache = runtime_cache_slot != nullptr
                ? ensure_runtime_cache(runtime_cache_slot)
                : nullptr;
        const int num_indices = static_cast<int>(indices_cpu.numel());

        auto gather_row = [&](int64_t b, int sl, torch::Tensor &k_out, torch::Tensor &v_out) {
            torch::Tensor slot_t;
            if (cache != nullptr && prefill_slots_match(
                    *cache, sl, page_size, ip, static_cast<int>(B), ix, num_indices)) {
                slot_t = cache->prefill_slots;
            } else {
                int ps = ip[b];
                std::vector<int64_t> slots;
                slots.reserve(static_cast<size_t>(sl));
                for (int t = 0; t < sl; ++t) {
                    int page = t / page_size;
                    int offs = t - page * page_size;
                    int phys = ix[ps + page];
                    slots.push_back(static_cast<int64_t>(phys) * page_size + offs);
                }
                slot_t = torch::tensor(
                        slots, torch::TensorOptions().dtype(torch::kLong).device(q.device()));
                if (cache != nullptr) {
                    cache->prefill_slots = slot_t;
                    cache->prefill_slots_valid = true;
                    cache->prefill_sl = sl;
                    cache->prefill_page_size = page_size;
                    cache->prefill_indptr.assign(ip, ip + B + 1);
                    cache->prefill_indices.assign(ix, ix + num_indices);
                }
            }
            auto k_flat = kc.index_select(0, slot_t);
            auto v_flat = vc.index_select(0, slot_t);
            k_out = k_flat.view({1, sl, num_kv_heads, head_dim}).transpose(1, 2).contiguous();
            v_out = v_flat.view({1, sl, num_kv_heads, head_dim}).transpose(1, 2).contiguous();
            if (Hq != num_kv_heads) {
                int rep = static_cast<int>(Hq / num_kv_heads);
                k_out = k_out.unsqueeze(2)
                              .expand({1, num_kv_heads, rep, sl, head_dim})
                              .reshape({1, Hq, sl, head_dim})
                              .contiguous();
                v_out = v_out.unsqueeze(2)
                              .expand({1, num_kv_heads, rep, sl, head_dim})
                              .reshape({1, Hq, sl, head_dim})
                              .contiguous();
            }
        };

        std::optional<at::Tensor> mask;
        bool causal = false;
        if (attn_mask != nullptr && attn_mask->defined()) {
            mask = *attn_mask;
        } else {
            causal = is_causal != 0 && S > 1;
        }
        std::optional<double> scale_opt = static_cast<double>(scale);

        // Ragged CSR (mixed cache lengths) or fp32 decode fallback: SDPA per row.
        if (!uniform) {
            out = torch::empty_like(q);
            for (int64_t b = 0; b < B; ++b) {
                int sl = seqlens[static_cast<size_t>(b)];
                torch::Tensor k_b, v_b;
                gather_row(b, sl, k_b, v_b);
                auto q_b = q.index({b}).unsqueeze(0);
                auto o_b = at::scaled_dot_product_attention(
                        q_b, k_b, v_b, mask, 0.0, causal, scale_opt);
                out.index({b}).copy_(o_b.squeeze(0));
            }
            return 0;
        }

        const int sl = seqlens[0];
        torch::Tensor slot_t;
        if (cache != nullptr && prefill_slots_match(
                *cache, sl, page_size, ip, static_cast<int>(B), ix, num_indices)
                && cache->prefill_slots.defined()
                && cache->prefill_slots.numel() == B * sl) {
            slot_t = cache->prefill_slots;
        } else {
            std::vector<int64_t> slots;
            slots.reserve(static_cast<size_t>(B * sl));
            for (int64_t b = 0; b < B; ++b) {
                int ps = ip[b];
                for (int t = 0; t < sl; ++t) {
                    int page = t / page_size;
                    int offs = t - page * page_size;
                    int phys = ix[ps + page];
                    slots.push_back(static_cast<int64_t>(phys) * page_size + offs);
                }
            }
            slot_t = torch::tensor(slots, torch::TensorOptions().dtype(torch::kLong).device(q.device()));
            if (cache != nullptr) {
                cache->prefill_slots = slot_t;
                cache->prefill_slots_valid = true;
                cache->prefill_sl = sl;
                cache->prefill_page_size = page_size;
                cache->prefill_indptr.assign(ip, ip + B + 1);
                cache->prefill_indices.assign(ix, ix + num_indices);
            }
        }
        auto k_flat = kc.index_select(0, slot_t);
        auto v_flat = vc.index_select(0, slot_t);
        auto k = k_flat.view({B, sl, num_kv_heads, head_dim}).transpose(1, 2).contiguous();
        auto v = v_flat.view({B, sl, num_kv_heads, head_dim}).transpose(1, 2).contiguous();
        if (Hq != num_kv_heads) {
            int rep = static_cast<int>(Hq / num_kv_heads);
            k = k.unsqueeze(2)
                        .expand({B, num_kv_heads, rep, sl, head_dim})
                        .reshape({B, Hq, sl, head_dim})
                        .contiguous();
            v = v.unsqueeze(2)
                        .expand({B, num_kv_heads, rep, sl, head_dim})
                        .reshape({B, Hq, sl, head_dim})
                        .contiguous();
        }
        out = at::scaled_dot_product_attention(q, k, v, mask, 0.0, causal, scale_opt);
        return 0;
    } catch (const std::exception &ex) {
        err = ex.what();
        return -1;
    }
}

/** Ragged contiguous self-attention via LibTorch SDPA (one launch per segment). */
int run_ragged_sdpa(
        const torch::Tensor &q, // [N,H,D] NHD
        const torch::Tensor &k,
        const torch::Tensor &v,
        const torch::Tensor &indptr, // int32 [B+1]
        float scale,
        int is_causal,
        const torch::Tensor *attn_mask,
        torch::Tensor &out,
        std::string &err) {
    try {
        auto qc = q.contiguous();
        auto kc = k.contiguous();
        auto vc = v.contiguous();
        auto indptr_cpu = indptr.to(at::kCPU).contiguous();
        auto *ip = indptr_cpu.data_ptr<int32_t>();
        const int64_t batch = indptr.size(0) - 1;
        out = torch::empty_like(qc);

        std::optional<at::Tensor> mask;
        if (attn_mask != nullptr && attn_mask->defined()) {
            mask = *attn_mask;
        }
        const bool causal = is_causal != 0;
        std::optional<double> scale_opt = static_cast<double>(scale);

        for (int64_t b = 0; b < batch; ++b) {
            const int start = ip[b];
            const int len = ip[b + 1] - start;
            if (len <= 0) {
                continue;
            }
            auto q_seg = qc.index({torch::indexing::Slice(start, start + len)})
                               .transpose(0, 1)
                               .unsqueeze(0);
            auto k_seg = kc.index({torch::indexing::Slice(start, start + len)})
                               .transpose(0, 1)
                               .unsqueeze(0);
            auto v_seg = vc.index({torch::indexing::Slice(start, start + len)})
                               .transpose(0, 1)
                               .unsqueeze(0);
            auto o_seg = at::scaled_dot_product_attention(
                    q_seg, k_seg, v_seg, mask, 0.0, causal, scale_opt);
            out.index({torch::indexing::Slice(start, start + len)})
                    .copy_(o_seg.squeeze(0).transpose(0, 1));
        }
        return 0;
    } catch (const std::exception &ex) {
        err = ex.what();
        return -1;
    }
}

template <typename DType, MaskMode MASK_MODE, uint32_t HEAD_DIM>
int run_batch_ragged_prefill_flashinfer(
        const torch::Tensor &q,
        const torch::Tensor &k,
        const torch::Tensor &v,
        const torch::Tensor &indptr,
        int num_qo_heads,
        int num_kv_heads,
        float sm_scale,
        torch::Tensor &float_ws,
        torch::Tensor &int_ws,
        torch::Tensor &pinned_int_ws,
        torch::Tensor &out,
        std::string &err) {
    using IdType = int32_t;
    using AttentionVariant = DefaultAttention<false, false, false, false>;
    using Params = BatchPrefillRaggedParams<DType, DType, DType, IdType>;
    constexpr PosEncodingMode POS = PosEncodingMode::kNone;
    constexpr uint32_t HEAD_DIM_QK = HEAD_DIM;
    constexpr uint32_t HEAD_DIM_VO = HEAD_DIM;
    constexpr bool USE_FP16_QK_REDUCTION = false;

    const auto batch_size = static_cast<uint32_t>(indptr.size(0) - 1);
    auto indptr_h = indptr.to(at::kCPU).contiguous();
    auto *qo_indptr_h = indptr_h.data_ptr<IdType>();
    auto *kv_indptr_h = qo_indptr_h;
    const uint32_t total_num_rows = qo_indptr_h[batch_size];

    auto qc = q.contiguous();
    auto kc = k.contiguous();
    auto vc = v.contiguous();
    out = torch::empty_like(qc);
    auto stream = at::cuda::getCurrentCUDAStream().stream();

    PrefillPlanInfo plan_info;
    cudaError_t status = PrefillPlan<IdType>(
            float_ws.data_ptr(), float_ws.numel() * float_ws.element_size(), int_ws.data_ptr(),
            pinned_int_ws.data_ptr(), int_ws.numel() * int_ws.element_size(), plan_info,
            qo_indptr_h, kv_indptr_h, total_num_rows, batch_size,
            static_cast<uint32_t>(num_qo_heads), static_cast<uint32_t>(num_kv_heads), HEAD_DIM_QK,
            HEAD_DIM_VO, /*page_size=*/1, /*enable_cuda_graph=*/false, sizeof(DType),
            /*window_left=*/-1, /*fixed_split_size=*/-1, /*disable_split_kv=*/false,
            /*num_colocated_ctas=*/0, /*uniform_q_len=*/0, stream, sizeof(DType));
    if (status != cudaSuccess) {
        err = std::string("PrefillPlan failed: ") + cudaGetErrorString(status);
        return -1;
    }

    Params params;
    params.q = static_cast<DType *>(qc.data_ptr());
    params.k = static_cast<DType *>(kc.data_ptr());
    params.v = static_cast<DType *>(vc.data_ptr());
    params.o = static_cast<DType *>(out.data_ptr());
    params.lse = nullptr;
    params.maybe_custom_mask = nullptr;
    params.q_indptr = static_cast<IdType *>(indptr.data_ptr());
    params.kv_indptr = static_cast<IdType *>(indptr.data_ptr());
    params.maybe_mask_indptr = nullptr;
    params.maybe_q_rope_offset = nullptr;
    params.maybe_k_rope_offset = nullptr;
    params.maybe_alibi_slopes = nullptr;
    params.num_qo_heads = static_cast<uint32_t>(num_qo_heads);
    params.num_kv_heads = static_cast<uint32_t>(num_kv_heads);
    params.q_stride_n = static_cast<uint32_t>(qc.stride(0));
    params.q_stride_h = static_cast<uint32_t>(qc.stride(1));
    params.k_stride_n = static_cast<uint32_t>(kc.stride(0));
    params.k_stride_h = static_cast<uint32_t>(kc.stride(1));
    params.v_stride_n = static_cast<uint32_t>(vc.stride(0));
    params.v_stride_h = static_cast<uint32_t>(vc.stride(1));
    params.window_left = -1;
    params.logits_soft_cap = 0.f;
    params.sm_scale = sm_scale;
    params.rope_rcp_scale = 1.f;
    params.rope_rcp_theta = 1.f;

    void *int_buffer = int_ws.data_ptr();
    void *float_buffer = float_ws.data_ptr();
    params.request_indices =
            GetPtrFromBaseOffset<IdType>(int_buffer, plan_info.request_indices_offset);
    params.qo_tile_indices =
            GetPtrFromBaseOffset<IdType>(int_buffer, plan_info.qo_tile_indices_offset);
    params.kv_tile_indices =
            GetPtrFromBaseOffset<IdType>(int_buffer, plan_info.kv_tile_indices_offset);
    params.o_indptr = GetPtrFromBaseOffset<IdType>(int_buffer, plan_info.o_indptr_offset);
    params.kv_chunk_size_ptr =
            GetPtrFromBaseOffset<IdType>(int_buffer, plan_info.kv_chunk_size_ptr_offset);
    DType *tmp_v = nullptr;
    float *tmp_s = nullptr;
    if (plan_info.split_kv) {
        params.merge_indptr =
                GetPtrFromBaseOffset<IdType>(int_buffer, plan_info.merge_indptr_offset);
        tmp_v = GetPtrFromBaseOffset<DType>(float_buffer, plan_info.v_offset);
        tmp_s = GetPtrFromBaseOffset<float>(float_buffer, plan_info.s_offset);
    }
    params.padded_batch_size = static_cast<uint32_t>(plan_info.padded_batch_size);
    params.max_total_num_rows = static_cast<uint32_t>(plan_info.total_num_rows);
    params.total_num_rows = nullptr;
    params.partition_kv = plan_info.split_kv;
    params.block_valid_mask = nullptr;

    const uint32_t cta_tile_q = static_cast<uint32_t>(plan_info.cta_tile_q);
    auto dispatch_run = [&](auto cta_tile_c) {
        constexpr uint32_t CTA_TILE_Q = decltype(cta_tile_c)::value;
        status = BatchPrefillWithRaggedKVCacheDispatched<
                CTA_TILE_Q, HEAD_DIM_QK, HEAD_DIM_VO, POS, USE_FP16_QK_REDUCTION, MASK_MODE,
                AttentionVariant, Params>(params, tmp_v, tmp_s, /*enable_pdl=*/false, stream);
    };

    if (cta_tile_q == 128) {
        dispatch_run(std::integral_constant<uint32_t, 128>{});
    } else if (cta_tile_q == 64) {
        dispatch_run(std::integral_constant<uint32_t, 64>{});
    } else if (cta_tile_q == 32) {
        dispatch_run(std::integral_constant<uint32_t, 32>{});
    } else if (cta_tile_q == 16) {
        dispatch_run(std::integral_constant<uint32_t, 16>{});
    } else {
        err = "unsupported cta_tile_q from PrefillPlan";
        return -1;
    }
    if (status != cudaSuccess) {
        err = std::string("BatchPrefillWithRaggedKVCache failed: ") + cudaGetErrorString(status);
        return -1;
    }
    return 0;
}

template <MaskMode MASK_MODE>
int run_ragged_prefill(
        const torch::Tensor &q,
        const torch::Tensor &k,
        const torch::Tensor &v,
        const torch::Tensor &indptr,
        int num_qo_heads,
        int num_kv_heads,
        int head_dim,
        float scale,
        int is_causal,
        const torch::Tensor *attn_mask,
        torch::Tensor &out,
        std::string &err) {
    (void)is_causal;
    if (num_qo_heads != num_kv_heads || num_qo_heads <= 0) {
        err = "ragged FlashInfer prefill requires MHA (num_qo_heads == num_kv_heads)";
        return -1;
    }
    if (head_dim != 64 && head_dim != 128 && head_dim != 256 && head_dim != 512) {
        return run_ragged_sdpa(q, k, v, indptr, scale, is_causal, attn_mask, out, err);
    }

    auto float_ws = torch::empty(
            {128LL << 20},
            torch::TensorOptions().dtype(torch::kUInt8).device(q.device()));
    auto int_ws = torch::empty(
            {16LL << 20},
            torch::TensorOptions().dtype(torch::kUInt8).device(q.device()));
    auto pinned_int = torch::empty(
            {16LL << 20},
            torch::TensorOptions().dtype(torch::kByte).pinned_memory(true));

    int rc = -1;
    if (q.scalar_type() == at::kBFloat16) {
        auto dispatch_hd = [&](auto head_dim_c) {
            constexpr uint32_t HEAD_DIM = decltype(head_dim_c)::value;
            rc = run_batch_ragged_prefill_flashinfer<nv_bfloat16, MASK_MODE, HEAD_DIM>(
                    q, k, v, indptr, num_qo_heads, num_kv_heads, scale, float_ws, int_ws,
                    pinned_int, out, err);
        };
        if (head_dim == 64) {
            dispatch_hd(std::integral_constant<uint32_t, 64>{});
        } else if (head_dim == 128) {
            dispatch_hd(std::integral_constant<uint32_t, 128>{});
        } else if (head_dim == 256) {
            dispatch_hd(std::integral_constant<uint32_t, 256>{});
        } else {
            dispatch_hd(std::integral_constant<uint32_t, 512>{});
        }
    } else if (q.scalar_type() == at::kHalf) {
        auto dispatch_hd = [&](auto head_dim_c) {
            constexpr uint32_t HEAD_DIM = decltype(head_dim_c)::value;
            rc = run_batch_ragged_prefill_flashinfer<__half, MASK_MODE, HEAD_DIM>(
                    q, k, v, indptr, num_qo_heads, num_kv_heads, scale, float_ws, int_ws,
                    pinned_int, out, err);
        };
        if (head_dim == 64) {
            dispatch_hd(std::integral_constant<uint32_t, 64>{});
        } else if (head_dim == 128) {
            dispatch_hd(std::integral_constant<uint32_t, 128>{});
        } else if (head_dim == 256) {
            dispatch_hd(std::integral_constant<uint32_t, 256>{});
        } else {
            dispatch_hd(std::integral_constant<uint32_t, 512>{});
        }
    } else {
        return run_ragged_sdpa(q, k, v, indptr, scale, is_causal, attn_mask, out, err);
    }
    if (rc == 0) {
        return 0;
    }
    return run_ragged_sdpa(q, k, v, indptr, scale, is_causal, attn_mask, out, err);
}

} // namespace

namespace {

bool is_fp8_dtype(at::ScalarType t) {
    return t == at::kFloat8_e4m3fn || t == at::kFloat8_e5m2
            || t == at::kFloat8_e4m3fnuz || t == at::kFloat8_e5m2fnuz;
}

/** Dequant FP8 KV pages to query dtype using per-pool scales (capacity path). */
void dequant_fp8_kv_pages(const torch::Tensor &k_fp8, const torch::Tensor &v_fp8,
                          float k_scale, float v_scale, at::ScalarType out_dtype,
                          torch::Tensor &k_out, torch::Tensor &v_out) {
    auto k_f = k_fp8.to(at::kFloat).mul(k_scale);
    auto v_f = v_fp8.to(at::kFloat).mul(v_scale);
    k_out = k_f.to(out_dtype);
    v_out = v_f.to(out_dtype);
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
        float k_scale,
        float v_scale,
        int is_causal,
        const torch::Tensor *attn_mask,
        torch::Tensor *float_workspace,
        torch::Tensor *int_workspace,
        torch::Tensor *pinned_int_workspace,
        void **runtime_cache_slot,
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
        if (cache_len < 0) {
            err = "cache_len must be >= 0";
            return -1;
        }

        auto indptr = paged_kv_indptr.to(at::kInt).contiguous();
        auto indices = paged_kv_indices.to(at::kInt).contiguous();
        auto last = paged_kv_last_page_len.to(at::kInt).contiguous();
        torch::Tensor k_pages = as_page_major(k_cache.contiguous(), page_size);
        torch::Tensor v_pages = as_page_major(v_cache.contiguous(), page_size);
        // FP8 KV: store stays FP8 in the pool; FlashInfer / SDPA compute in Q dtype
        // via scaled dequant (Ampere-correct; Hopper can later use native FP8 KV kernels).
        torch::Tensor k_pages_compute = k_pages;
        torch::Tensor v_pages_compute = v_pages;
        if (is_fp8_dtype(k_cache.scalar_type())) {
            float ks = k_scale > 0.f ? k_scale : 1.f;
            float vs = v_scale > 0.f ? v_scale : 1.f;
            dequant_fp8_kv_pages(k_pages, v_pages, ks, vs, query.scalar_type(),
                                 k_pages_compute, v_pages_compute);
        } else if (k_pages.scalar_type() != query.scalar_type()) {
            // e.g. FP16 KV pool + BF16 query (Marlin GEMM keeps activations in model dtype)
            k_pages_compute = k_pages.to(query.scalar_type());
            v_pages_compute = v_pages.to(query.scalar_type());
        }
        k_pages = k_pages_compute;
        v_pages = v_pages_compute;

        // Prefer caller-owned pooled workspace (KvCachePool). Fall back to
        // locals only when null — locals are 32+8 MiB per call and used to
        // inflate nvidia-smi by ~40 MiB/request when the pool was ignored.
        torch::Tensor float_ws_local;
        torch::Tensor int_ws_local;
        torch::Tensor pinned_local;
        torch::Tensor *float_ws_ptr;
        torch::Tensor *int_ws_ptr;
        torch::Tensor *pinned_ptr;
        const bool use_pooled = float_workspace != nullptr && float_workspace->defined()
                && int_workspace != nullptr && int_workspace->defined()
                && pinned_int_workspace != nullptr && pinned_int_workspace->defined();
        if (use_pooled) {
            float_ws_ptr = float_workspace;
            int_ws_ptr = int_workspace;
            pinned_ptr = pinned_int_workspace;
        } else {
            static std::atomic<bool> warned{false};
            if (!warned.exchange(true)) {
                fprintf(stderr,
                        "WARN smile: FlashInfer paged attention allocating local "
                        "32+8 MiB workspace (pooled workspace missing); "
                        "expect ~40 MiB/request GPU growth until fixed\n");
                fflush(stderr);
            }
            float_ws_local = torch::empty(
                    {32LL << 20},
                    torch::TensorOptions().dtype(torch::kUInt8).device(query.device()));
            float_ws_ptr = &float_ws_local;
            int_ws_local = torch::empty(
                    {8LL << 20},
                    torch::TensorOptions().dtype(torch::kUInt8).device(query.device()));
            int_ws_ptr = &int_ws_local;
            pinned_local = torch::empty(
                    {8LL << 20},
                    torch::TensorOptions().dtype(torch::kUInt8).pinned_memory(true));
            pinned_ptr = &pinned_local;
        }
        torch::Tensor &float_ws = *float_ws_ptr;
        torch::Tensor &int_ws = *int_ws_ptr;
        torch::Tensor &pinned_int = *pinned_ptr;

        if (S == 1) {
            if (head_dim == 64 || head_dim == 128 || head_dim == 256 || head_dim == 512) {
                int rc = -1;
                torch::Tensor o3;
                // FlashInfer BatchDecode merge kernels use DISPATCH_HEAD_DIM including
                // 512; with DType=float that requests 512-bit cp.async loads, which
                // FlashInfer does not support. Only bf16 / fp16 are compiled.
                if (query.scalar_type() == at::kBFloat16) {
                    rc = run_batch_decode<nv_bfloat16>(
                            query, k_pages, v_pages, indptr, indices, last, page_size,
                            static_cast<int>(Hq), num_kv_heads, head_dim, scale,
                            float_ws, int_ws, pinned_int, runtime_cache_slot, o3, err);
                } else if (query.scalar_type() == at::kHalf) {
                    rc = run_batch_decode<__half>(
                            query, k_pages, v_pages, indptr, indices, last, page_size,
                            static_cast<int>(Hq), num_kv_heads, head_dim, scale,
                            float_ws, int_ws, pinned_int, runtime_cache_slot, o3, err);
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
                warn_flashinfer_sdpa_decode_once("query dtype is not bf16/fp16");
            } else {
                char reason[96];
                snprintf(reason, sizeof(reason),
                         "unsupported head_dim=%d (need 64/128/256/512)", head_dim);
                warn_flashinfer_sdpa_decode_once(reason);
            }
        }

        return run_batch_prefill_sdpa(
                query, k_pages, v_pages, indptr, indices, last, page_size, num_kv_heads,
                head_dim, cache_len, scale, is_causal, attn_mask, runtime_cache_slot, out, err);
    } catch (const std::exception &ex) {
        err = ex.what();
        return -1;
    }
}

extern "C" int smile_flashinfer_ragged_attention_cuda(
        const torch::Tensor &query,
        const torch::Tensor &key,
        const torch::Tensor &value,
        const torch::Tensor &indptr,
        int num_kv_heads,
        int head_dim,
        float scale,
        int is_causal,
        const torch::Tensor *attn_mask,
        torch::Tensor &out,
        std::string &err) {
    try {
        TORCH_CHECK(query.is_cuda(), "query must be CUDA");
        TORCH_CHECK(key.is_cuda() && value.is_cuda(), "key/value must be CUDA");
        TORCH_CHECK(query.dim() == 3 && key.dim() == 3 && value.dim() == 3,
                    "ragged Q/K/V must be [N,H,D] NHD");
        TORCH_CHECK(indptr.dim() == 1 && indptr.scalar_type() == at::kInt,
                    "indptr must be int32 [B+1]");
        const auto Hq = query.size(1);
        const auto D = query.size(2);
        TORCH_CHECK(D == head_dim, "head_dim mismatch");
        TORCH_CHECK(num_kv_heads > 0 && Hq == num_kv_heads, "ragged prefill expects MHA");
        TORCH_CHECK(query.sizes() == key.sizes() && query.sizes() == value.sizes(),
                    "Q/K/V shape mismatch");

        if (is_causal != 0) {
            return run_ragged_prefill<MaskMode::kCausal>(
                    query, key, value, indptr, static_cast<int>(Hq), num_kv_heads, head_dim, scale,
                    is_causal, attn_mask, out, err);
        }
        return run_ragged_prefill<MaskMode::kNone>(
                query, key, value, indptr, static_cast<int>(Hq), num_kv_heads, head_dim, scale,
                is_causal, attn_mask, out, err);
    } catch (const std::exception &ex) {
        err = ex.what();
        return -1;
    }
}

extern "C" void smile_flashinfer_runtime_cache_invalidate(void *cache_slot) {
    if (cache_slot != nullptr) {
        static_cast<WorkspaceRuntimeCache *>(cache_slot)->invalidate();
    }
}

extern "C" void smile_flashinfer_runtime_cache_free(void *cache_slot) {
    delete static_cast<WorkspaceRuntimeCache *>(cache_slot);
}
