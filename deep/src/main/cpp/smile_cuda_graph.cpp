/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * CUDA graph capture / replay for decode (Phase 2c).
 */

#define SMILE_TORCH_BUILD

#include "smile_torch.h"

#ifdef USE_CUDA
#  include <ATen/cuda/CUDAGraph.h>
#  include <c10/cuda/CUDAGuard.h>
#  include <c10/cuda/CUDAStream.h>
#endif

#include <memory>
#include <optional>
#include <string>

extern "C" void smile_torch_set_error(const char *msg);

struct ST_CudaGraph_ {
#ifdef USE_CUDA
    std::unique_ptr<at::cuda::CUDAGraph> graph;
    at::cuda::CUDAStream capture_stream = at::cuda::CUDAStream();
    std::optional<at::cuda::CUDAStreamGuard> stream_guard;
    int device_index = -1;
    bool instantiated = false;
#endif
};

extern "C" {

ST_CudaGraph smile_cuda_graph_create(void) {
#ifdef USE_CUDA
    try {
        return new ST_CudaGraph_();
    } catch (const std::exception &ex) {
        smile_torch_set_error(ex.what());
        return nullptr;
    }
#else
    smile_torch_set_error("CUDA not available");
    return nullptr;
#endif
}

void smile_cuda_graph_destroy(ST_CudaGraph graph) {
    delete graph;
}

int smile_cuda_graph_capture_begin(ST_CudaGraph graph, int device_index) {
#ifdef USE_CUDA
    if (graph == nullptr) {
        smile_torch_set_error("smile_cuda_graph_capture_begin: null graph");
        return -1;
    }
    try {
        graph->device_index = device_index;
        c10::cuda::CUDAGuard guard(device_index);
        graph->capture_stream = at::cuda::getStreamFromPool();
        graph->stream_guard.emplace(graph->capture_stream);
        graph->graph = std::make_unique<at::cuda::CUDAGraph>();
        graph->graph->capture_begin(
                at::cuda::graph_pool_handle(), cudaStreamCaptureModeThreadLocal);
        graph->instantiated = false;
        return 0;
    } catch (const std::exception &ex) {
        smile_torch_set_error(ex.what());
        return -1;
    }
#else
    (void)graph;
    (void)device_index;
    smile_torch_set_error("CUDA not available");
    return -1;
#endif
}

int smile_cuda_graph_capture_end(ST_CudaGraph graph) {
#ifdef USE_CUDA
    if (graph == nullptr || graph->graph == nullptr) {
        smile_torch_set_error("smile_cuda_graph_capture_end: graph not capturing");
        return -1;
    }
    try {
        c10::cuda::CUDAGuard guard(graph->device_index);
        graph->graph->capture_end();
        graph->graph->instantiate();
        graph->instantiated = true;
        graph->stream_guard.reset();
        return 0;
    } catch (const std::exception &ex) {
        smile_torch_set_error(ex.what());
        return -1;
    }
#else
    (void)graph;
    smile_torch_set_error("CUDA not available");
    return -1;
#endif
}

int smile_cuda_graph_replay(ST_CudaGraph graph) {
#ifdef USE_CUDA
    if (graph == nullptr || !graph->instantiated || graph->graph == nullptr) {
        smile_torch_set_error("smile_cuda_graph_replay: graph not ready");
        return -1;
    }
    try {
        c10::cuda::CUDAGuard guard(graph->device_index);
        at::cuda::CUDAStreamGuard stream_guard(graph->capture_stream);
        graph->graph->replay();
        return 0;
    } catch (const std::exception &ex) {
        smile_torch_set_error(ex.what());
        return -1;
    }
#else
    (void)graph;
    smile_torch_set_error("CUDA not available");
    return -1;
#endif
}

int smile_cuda_graph_is_ready(ST_CudaGraph graph) {
#ifdef USE_CUDA
    return (graph != nullptr && graph->instantiated && graph->graph != nullptr) ? 1 : 0;
#else
    (void)graph;
    return 0;
#endif
}

} // extern "C"
