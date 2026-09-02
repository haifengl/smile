/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * FlashInfer paged attention C ABI (workspace + availability + AOT dir).
 */

#include "smile_flashinfer_cuda.h"
#include "smile_torch.h"

#include <cstdint>
#include <cstdlib>
#include <mutex>
#include <string>

#ifdef USE_CUDA
#  include <ATen/ATen.h>
#  include <c10/cuda/CUDAGuard.h>
#  include <torch/torch.h>
#endif

struct ST_FlashInferWorkspace_ {
    int device_index = 0;
    int64_t workspace_bytes = 0;
    void *runtime_cache = nullptr;
#ifdef USE_CUDA
    at::Tensor float_workspace;   // uint8
    at::Tensor int_workspace;     // uint8 device
    at::Tensor pinned_int_workspace; // uint8 pinned
#endif
};

static std::mutex g_aot_mu;
static std::string g_aot_dir;

/* provided by smile_torch.cpp */
extern "C" void smile_torch_set_error(const char *msg);
extern "C" void smile_torch_clear_error(void);

extern "C" {

void smile_flashinfer_set_aot_dir(const char *path) {
    std::lock_guard<std::mutex> lock(g_aot_mu);
    if (path == nullptr || path[0] == '\0') {
        g_aot_dir.clear();
    } else {
        g_aot_dir = path;
    }
}

const char *smile_flashinfer_aot_dir(void) {
    std::lock_guard<std::mutex> lock(g_aot_mu);
    if (g_aot_dir.empty()) {
        const char *env = std::getenv("FLASHINFER_AOT_DIR");
        if (env && env[0]) {
            return env;
        }
        const char *smile = std::getenv("SMILE_FLASHINFER_AOT_DIR");
        if (smile && smile[0]) {
            return smile;
        }
        return "";
    }
    // Return stable pointer into static string under lock — callers must not
    // free; value may change on next set. For probes this is fine.
    static thread_local std::string tls;
    tls = g_aot_dir;
    return tls.c_str();
}

int smile_flashinfer_is_available(void) {
#if defined(USE_CUDA) && defined(USE_FLASHINFER)
    // Real FlashInfer decode kernels are compiled into libsmile_torch when
    // USE_FLASHINFER is on. AOT dir is optional (jit-cache for future TVM load).
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
        // FlashInfer decode plans need sizable float + int workspaces.
        int64_t float_bytes = workspace_bytes > 0 ? workspace_bytes : (128LL << 20);
        int64_t int_bytes = 16LL << 20;
        ws->workspace_bytes = float_bytes;
        c10::cuda::CUDAGuard guard(device_index);
        auto opts = at::TensorOptions().dtype(at::kByte).device(at::kCUDA, device_index);
        ws->float_workspace = at::empty({float_bytes}, opts);
        ws->int_workspace = at::empty({int_bytes}, opts);
        ws->pinned_int_workspace = at::empty(
                {int_bytes},
                at::TensorOptions().dtype(at::kByte).pinned_memory(true));
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
    if (ws != nullptr) {
#if defined(USE_CUDA) && defined(USE_FLASHINFER)
        if (ws->runtime_cache != nullptr) {
            smile_flashinfer_runtime_cache_free(ws->runtime_cache);
            ws->runtime_cache = nullptr;
        }
#endif
        delete ws;
    }
}

void smile_flashinfer_workspace_invalidate_runtime_cache(ST_FlashInferWorkspace ws) {
#if defined(USE_CUDA) && defined(USE_FLASHINFER)
    if (ws != nullptr && ws->runtime_cache != nullptr) {
        smile_flashinfer_runtime_cache_invalidate(ws->runtime_cache);
    }
#else
    (void)ws;
#endif
}

void smile_flashinfer_workspace_invalidate_prefill_runtime_cache(ST_FlashInferWorkspace ws) {
#if defined(USE_CUDA) && defined(USE_FLASHINFER)
    if (ws != nullptr && ws->runtime_cache != nullptr) {
        smile_flashinfer_runtime_cache_invalidate_prefill(ws->runtime_cache);
    }
#else
    (void)ws;
#endif
}

int smile_flashinfer_workspace_device_index(ST_FlashInferWorkspace ws) {
    return ws ? ws->device_index : -1;
}

} // extern "C"

#if defined(USE_CUDA) && defined(USE_FLASHINFER)
void **smile_flashinfer_workspace_runtime_cache_slot(ST_FlashInferWorkspace ws) {
    return ws == nullptr ? nullptr : &ws->runtime_cache;
}

int smile_flashinfer_workspace_get_tensors(
        ST_FlashInferWorkspace ws,
        at::Tensor **float_ws,
        at::Tensor **int_ws,
        at::Tensor **pinned_ws) {
    if (ws == nullptr || float_ws == nullptr || int_ws == nullptr || pinned_ws == nullptr) {
        return -1;
    }
    *float_ws = &ws->float_workspace;
    *int_ws = &ws->int_workspace;
    *pinned_ws = &ws->pinned_int_workspace;
    return 0;
}
#endif
