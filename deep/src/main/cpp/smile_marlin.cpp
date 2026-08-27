/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * Thin C ABI over Marlin (third_party/marlin). Apache-2.0 kernel; Smile GPL wrapper.
 */
#include "smile_marlin.h"

#include <algorithm>
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

static void set_err(const std::string& msg) {
    smile_torch_set_error(msg.c_str());
}

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
            set_err("smile_marlin_mul: null tensor");
            return nullptr;
        }
        // Prefer views when already contiguous — decode is latency-sensitive.
        auto A = a->t.is_contiguous() ? a->t : a->t.contiguous();
        auto B = b->t.is_contiguous() ? b->t : b->t.contiguous();
        auto s = scales->t.is_contiguous() ? scales->t : scales->t.contiguous();

        if (!A.is_cuda() || !B.is_cuda() || !s.is_cuda()) {
            set_err("smile_marlin_mul: A/B/scales must be CUDA tensors");
            return nullptr;
        }
        if (A.device() != B.device() || A.device() != s.device()) {
            set_err("smile_marlin_mul: A/B/scales device mismatch");
            return nullptr;
        }
        if (A.scalar_type() != at::kHalf) {
            set_err("smile_marlin_mul: A must be float16 (got "
                    + std::string(c10::toString(A.scalar_type())) + ")");
            return nullptr;
        }
        if (B.scalar_type() != at::kInt) {
            set_err("smile_marlin_mul: B (qweight) must be int32 (got "
                    + std::string(c10::toString(B.scalar_type())) + ")");
            return nullptr;
        }
        if (s.scalar_type() != at::kHalf) {
            set_err("smile_marlin_mul: scales must be float16 (got "
                    + std::string(c10::toString(s.scalar_type())) + ")");
            return nullptr;
        }
        if (A.dim() != 2 || B.dim() != 2 || s.dim() != 2) {
            set_err("smile_marlin_mul: A/B/scales must be 2D");
            return nullptr;
        }

        int prob_m = static_cast<int>(A.size(0));
        int prob_k = static_cast<int>(A.size(1));
        int prob_n = static_cast<int>(s.size(1));
        int groupsize = (s.size(0) == 1) ? -1 : prob_k / static_cast<int>(s.size(0));
        if (groupsize != -1 && groupsize * static_cast<int>(s.size(0)) != prob_k) {
            set_err("smile_marlin_mul: k=" + std::to_string(prob_k)
                    + " not compatible with scale rows=" + std::to_string(s.size(0)));
            return nullptr;
        }
        long expected_b0 = prob_k / 16;
        long expected_b1 = static_cast<long>(prob_n) * 16 / 8;
        if (B.size(0) != expected_b0 || B.size(1) != expected_b1) {
            set_err("smile_marlin_mul: B shape [" + std::to_string(B.size(0)) + ","
                    + std::to_string(B.size(1)) + "] != expected ["
                    + std::to_string(expected_b0) + "," + std::to_string(expected_b1)
                    + "] for k=" + std::to_string(prob_k) + " n=" + std::to_string(prob_n));
            return nullptr;
        }
        if (prob_k % 128 != 0) {
            set_err("smile_marlin_mul: k must be divisible by 128; got " + std::to_string(prob_k));
            return nullptr;
        }
        // Auto tile: m<=16 → thread_n=128; m>16 → thread_n=256.
        int n_align = (prob_m <= 16) ? 128 : 256;
        if (prob_n % n_align != 0) {
            set_err("smile_marlin_mul: n=" + std::to_string(prob_n)
                    + " not divisible by " + std::to_string(n_align)
                    + " (required for m=" + std::to_string(prob_m) + ")");
            return nullptr;
        }

        int max_par = 8;
        int64_t ws_need = std::max<int64_t>(1, (prob_n / 128) * max_par);
        torch::Tensor ws;
        // Locks start at 0 from Tensor.zeros / fresh alloc. Marlin's last
        // barrier_release(reset=true) clears them; avoid a memset every GEMM.
        if (!workspace || !workspace->t.defined() || workspace->t.numel() == 0) {
            ws = torch::zeros({ws_need}, torch::dtype(torch::kInt).device(A.device()));
        } else {
            ws = workspace->t.is_contiguous() ? workspace->t : workspace->t.contiguous();
            if (!ws.is_cuda() || ws.device() != A.device()) {
                set_err("smile_marlin_mul: workspace must be on same CUDA device as A");
                return nullptr;
            }
            if (ws.scalar_type() != at::kInt) {
                set_err("smile_marlin_mul: workspace must be int32");
                return nullptr;
            }
            if (ws.numel() < ws_need) {
                set_err("smile_marlin_mul: workspace too small need=" + std::to_string(ws_need)
                        + " got=" + std::to_string(ws.numel()));
                return nullptr;
            }
        }

        auto C = torch::empty({prob_m, prob_n},
                              torch::dtype(torch::kHalf).device(A.device()));

        (void) cudaGetLastError();
        // Single launch: marlin_cuda pads m to 16-tile internally. Do NOT
        // chunk+synchronize — that was ~200 host syncs/token and killed decode.
        int err = marlin_cuda(
                A.data_ptr(),
                B.data_ptr(),
                C.data_ptr(),
                s.data_ptr(),
                prob_m, prob_n, prob_k,
                ws.data_ptr(),
                groupsize,
                A.get_device(),
                at::cuda::getCurrentCUDAStream(A.get_device()),
                thread_k,  // -1 = auto (128×128 for m<=16, 64×256 otherwise)
                -1,
                -1,
                max_par);
        if (err == ERR_PROB_SHAPE) {
            set_err("smile_marlin_mul: problem shape not supported m="
                    + std::to_string(prob_m) + " n=" + std::to_string(prob_n)
                    + " k=" + std::to_string(prob_k) + " groupsize="
                    + std::to_string(groupsize));
            return nullptr;
        }
        if (err == ERR_KERN_SHAPE) {
            set_err("smile_marlin_mul: no Marlin kernel for m="
                    + std::to_string(prob_m) + " n=" + std::to_string(prob_n)
                    + " k=" + std::to_string(prob_k) + " groupsize="
                    + std::to_string(groupsize));
            return nullptr;
        }
        if (err != 0) {
            set_err("smile_marlin_mul: kernel error code=" + std::to_string(err));
            return nullptr;
        }
        cudaError_t st = cudaGetLastError();
        if (st != cudaSuccess) {
            set_err(std::string("smile_marlin_mul: CUDA launch error: ")
                    + cudaGetErrorString(st));
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
