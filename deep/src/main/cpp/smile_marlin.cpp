/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * Thin C ABI over Marlin (third_party/marlin). Apache-2.0 kernel; Smile GPL wrapper.
 */
#include "smile_marlin.h"

#include <string>

#ifdef USE_CUDA
#  include <ATen/cuda/CUDAContext.h>
#  include <torch/torch.h>
#  include <cuda_runtime.h>
#endif

/* smile_torch.cpp */
extern "C" void smile_torch_set_error(const char *msg);
extern "C" void smile_torch_clear_error(void);

/* Opaque tensor layout must match smile_torch.cpp */
struct ST_Tensor_ {
#ifdef USE_CUDA
    at::Tensor t;
#else
    void *t;
#endif
};

#if defined(USE_CUDA) && defined(USE_MARLIN)

int marlin_cuda(
  const void* A,
  const void* B,
        void* C,
        void* s,
  int prob_m,
  int prob_n,
  int prob_k,
  void* workspace,
  int groupsize,
  int dev,
  cudaStream_t stream,
  int thread_k,
  int thread_n,
  int sms,
  int max_par
);

static const int ERR_PROB_SHAPE = 1;
static const int ERR_KERN_SHAPE = 2;

#endif

extern "C" int smile_marlin_available(void) {
#if defined(USE_CUDA) && defined(USE_MARLIN)
    return 1;
#else
    return 0;
#endif
}

extern "C" ST_Tensor smile_marlin_mul(ST_Tensor a, ST_Tensor b, ST_Tensor scales,
                                      ST_Tensor workspace, int thread_k) {
#if defined(USE_CUDA) && defined(USE_MARLIN)
    try {
        smile_torch_clear_error();
        if (!a || !b || !scales) {
            smile_torch_set_error("smile_marlin_mul: null tensor");
            return nullptr;
        }
        auto& A = a->t;
        auto& B = b->t;
        auto& s = scales->t;
        int prob_m = static_cast<int>(A.size(0));
        int prob_k = static_cast<int>(A.size(1));
        int prob_n = static_cast<int>(s.size(s.dim() - 1));
        int groupsize = (s.size(0) == 1) ? -1 : prob_k / static_cast<int>(s.size(0));
        if (groupsize != -1 && groupsize * static_cast<int>(s.size(0)) != prob_k) {
            smile_torch_set_error("smile_marlin_mul: k not compatible with scale groups");
            return nullptr;
        }
        int max_par = 8;
        torch::Tensor ws;
        if (workspace && workspace->t.defined() && workspace->t.numel() > 0) {
            ws = workspace->t;
        } else {
            ws = torch::zeros({std::max(1, prob_n / 128 * max_par)},
                              torch::dtype(torch::kInt).device(A.device()));
        }
        if (ws.numel() < prob_n / 128 * max_par) {
            smile_torch_set_error("smile_marlin_mul: workspace too small");
            return nullptr;
        }
        auto C = torch::empty({prob_m, prob_n},
                              torch::dtype(torch::kHalf).device(A.device()));
        int dev = A.get_device();
        int err = marlin_cuda(
                A.data_ptr(),
                B.data_ptr(),
                C.data_ptr(),
                s.data_ptr(),
                prob_m, prob_n, prob_k,
                ws.data_ptr(),
                groupsize,
                dev,
                at::cuda::getCurrentCUDAStream(dev),
                thread_k,
                -1,
                -1,
                max_par);
        if (err == ERR_PROB_SHAPE) {
            smile_torch_set_error("smile_marlin_mul: problem shape not supported");
            return nullptr;
        }
        if (err == ERR_KERN_SHAPE) {
            smile_torch_set_error("smile_marlin_mul: no Marlin kernel for this shape");
            return nullptr;
        }
        if (err != 0) {
            smile_torch_set_error("smile_marlin_mul: kernel error");
            return nullptr;
        }
        return new ST_Tensor_{ C };
    } catch (const std::exception& ex) {
        smile_torch_set_error(ex.what());
        return nullptr;
    } catch (...) {
        smile_torch_set_error("unknown C++ exception in smile_marlin_mul");
        return nullptr;
    }
#else
    (void)a; (void)b; (void)scales; (void)workspace; (void)thread_k;
#if !defined(USE_CUDA)
    smile_torch_set_error("smile_torch was built without CUDA");
#else
    smile_torch_set_error("Marlin not enabled (rebuild with -DUSE_MARLIN=ON)");
#endif
    return nullptr;
#endif
}
