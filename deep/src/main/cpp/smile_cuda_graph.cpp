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
    std::optional<at::cuda::CUDAStream> capture_stream;
    std::optional<at::cuda::CUDAStreamGuard> stream_guard;
    int device_index = -1;
    bool instantiated = false;
#endif
};

#ifdef USE_CUDA
static void reset_capture_state(ST_CudaGraph_ *graph) {
    graph->stream_guard.reset();
    graph->graph.reset();
    graph->capture_stream.reset();
    graph->instantiated = false;
}
#endif

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
        reset_capture_state(graph);
        graph->device_index = device_index;
        c10::cuda::CUDAGuard guard(device_index);
        // PyTorch requires a non-default stream for capture. Drain the caller's
        // current stream first so prior eager work is complete.
        at::cuda::getCurrentCUDAStream(device_index).synchronize();
        graph->capture_stream = at::cuda::getStreamFromPool(/*isHighPriority=*/false);
        graph->stream_guard.emplace(*graph->capture_stream);
        graph->graph = std::make_unique<at::cuda::CUDAGraph>();
        // Relaxed: NCCL all-reduce (TP) uses auxiliary streams.
        graph->graph->capture_begin(
                at::cuda::graph_pool_handle(), cudaStreamCaptureModeRelaxed);
        graph->instantiated = false;
        return 0;
    } catch (const std::exception &ex) {
        reset_capture_state(graph);
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
        // keep_graph=false (default): capture_end() instantiates; do not call instantiate().
        graph->graph->capture_end();
        graph->instantiated = true;
        if (graph->capture_stream.has_value()) {
            graph->capture_stream->synchronize();
        }
        graph->stream_guard.reset();
        return 0;
    } catch (const std::exception &ex) {
        reset_capture_state(graph);
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
    if (graph == nullptr || !graph->instantiated || graph->graph == nullptr
        || !graph->capture_stream.has_value()) {
        smile_torch_set_error("smile_cuda_graph_replay: graph not ready");
        return -1;
    }
    try {
        c10::cuda::CUDAGuard guard(graph->device_index);
        // Prep (token / RoPE / KV index / FlashInfer last_page_len) runs on the
        // caller's current stream; wait for it before replaying on the capture stream.
        at::cuda::getCurrentCUDAStream(graph->device_index).synchronize();
        at::cuda::CUDAStreamGuard stream_guard(*graph->capture_stream);
        graph->graph->replay();
        // Host timings and logits reads must wait for GPU completion.
        graph->capture_stream->synchronize();
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
