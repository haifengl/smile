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
        int is_causal,
        torch::Tensor &out,
        std::string &err);

#ifdef __cplusplus
}
#endif
