/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * FlashInfer-compatible paged attention C ABI (workspace helpers).
 * Tensor ops live in smile_torch.cpp to share ST_Tensor_ / set_error.
 */

#include "smile_torch.h"

#include <cstdint>

#ifdef USE_CUDA
#  include <ATen/ATen.h>
#  include <c10/cuda/CUDAGuard.h>
#  include <torch/torch.h>
#endif

struct ST_FlashInferWorkspace_ {
    int device_index = 0;
    int64_t workspace_bytes = 0;
#ifdef USE_CUDA
    at::Tensor scratch;
#endif
};

/* provided by smile_torch.cpp */
extern "C" void smile_torch_set_error(const char *msg);
extern "C" void smile_torch_clear_error(void);

extern "C" {

int smile_flashinfer_is_available(void) {
#if defined(USE_CUDA) && defined(USE_FLASHINFER)
    return 1;
#else
    return 0;
#endif
}

ST_FlashInferWorkspace smile_flashinfer_workspace_create(
        int device_index, int64_t workspace_bytes) {
#if defined(USE_CUDA) && defined(USE_FLASHINFER)
    try {
        auto *ws = new ST_FlashInferWorkspace_();
        ws->device_index = device_index;
        ws->workspace_bytes = workspace_bytes > 0 ? workspace_bytes : (32LL << 20);
        c10::cuda::CUDAGuard guard(device_index);
        ws->scratch = at::empty(
                {ws->workspace_bytes / 4},
                at::TensorOptions().dtype(at::kFloat).device(at::kCUDA, device_index));
        return ws;
    } catch (const std::exception &ex) {
        smile_torch_set_error(ex.what());
        return nullptr;
    }
#else
    (void)device_index;
    (void)workspace_bytes;
    smile_torch_set_error("FlashInfer paged attention requires CUDA + USE_FLASHINFER");
    return nullptr;
#endif
}

void smile_flashinfer_workspace_free(ST_FlashInferWorkspace ws) {
    delete ws;
}

int smile_flashinfer_workspace_device_index(ST_FlashInferWorkspace ws) {
    return ws ? ws->device_index : -1;
}

} // extern "C"
