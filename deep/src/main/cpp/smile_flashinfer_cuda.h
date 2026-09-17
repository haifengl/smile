/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * CUDA entry for FlashInfer-compatible paged attention.
 * Must use C linkage so smile_torch.cpp's call (and Docker nm checks)
 * resolve the same symbol as smile_flashinfer_kernels.cu.
 */

#pragma once

#include <string>

#include <torch/torch.h>

#ifdef __cplusplus
extern "C" {
#endif

int smile_flashinfer_paged_attention_cuda(
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
        torch::Tensor *float_workspace,       /* nullable → allocate locals */
        torch::Tensor *int_workspace,         /* nullable */
        torch::Tensor *pinned_int_workspace,  /* nullable */
        void **runtime_cache_slot,            /* nullable → no plan/gather reuse */
        torch::Tensor &out,
        std::string &err);

/** Invalidates cached decode plans and prefill gather slots. */
void smile_flashinfer_runtime_cache_invalidate(void *cache_slot);
void smile_flashinfer_runtime_cache_invalidate_prefill(void *cache_slot);

/**
 * Invalidates only the cached verify-graph (SMILE_VERIFY_CUDA_GRAPH) plan.
 * Independent of smile_flashinfer_runtime_cache_invalidate: decode's routine
 * cohort/CSR-rebuild invalidation must not clear a still-valid verify plan.
 */
void smile_flashinfer_runtime_cache_invalidate_verify(void *cache_slot);

/** Frees a runtime cache allocated for a workspace. */
void smile_flashinfer_runtime_cache_free(void *cache_slot);

/**
 * Stage 1 (SMILE_VERIFY_CUDA_GRAPH): graph-capturable multi-token (S>1) causal
 * paged attention, isolated from smile_flashinfer_paged_attention_cuda's S==1/
 * S>1 dispatch (see smile_flashinfer_kernels.cu for rationale). Always causal;
 * falls back internally to the same eager gather+SDPA path
 * (run_batch_prefill_sdpa) for unsupported dtype/head_dim.
 */
int smile_flashinfer_paged_attention_verify_cuda(
        const torch::Tensor &query,
        const torch::Tensor &k_cache,
        const torch::Tensor &v_cache,
        const torch::Tensor &qo_indptr,
        const torch::Tensor &kv_indptr,
        const torch::Tensor &kv_indices,
        const torch::Tensor &kv_last_page_len,
        int page_size,
        int num_kv_heads,
        int head_dim,
        int qo_len,
        float scale,
        float k_scale,
        float v_scale,
        torch::Tensor *float_workspace,       /* nullable → allocate locals */
        torch::Tensor *int_workspace,         /* nullable */
        torch::Tensor *pinned_int_workspace,  /* nullable */
        void **runtime_cache_slot,            /* nullable → no plan/gather reuse */
        torch::Tensor &out,
        std::string &err);

int smile_flashinfer_ragged_attention_cuda(
        const torch::Tensor &query,
        const torch::Tensor &key,
        const torch::Tensor &value,
        const torch::Tensor &indptr,
        int num_kv_heads,
        int head_dim,
        float scale,
        int is_causal,
        const torch::Tensor *attn_mask, /* nullable */
        torch::Tensor &out,
        std::string &err);

#ifdef __cplusplus
}
#endif
