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

/**
 * Run one Marlin GEMM. Prefer m <= 16 so the kernel uses the well-tested
 * thread_m_blocks=1 configs (128×128 tiles). Larger m is chunked by the caller.
 */
static int run_marlin(
        const at::Tensor& A,
        const at::Tensor& B,
        at::Tensor& C,
        const at::Tensor& s,
        at::Tensor& ws,
        int groupsize,
        int thread_k,
        int thread_n) {
    int prob_m = static_cast<int>(A.size(0));
    int prob_k = static_cast<int>(A.size(1));
    int prob_n = static_cast<int>(C.size(1));
    int dev = A.get_device();
    int max_par = 8;
    ws.zero_();
    return marlin_cuda(
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
            thread_n,
            -1,
            max_par);
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
        auto A = a->t.contiguous();
        auto B = b->t.contiguous();
        auto s = scales->t.contiguous();

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

        int tot_m = static_cast<int>(A.size(0));
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
        if (prob_n % 128 != 0) {
            // tile_n=128 path; 256 is preferred for large-m but 128 works for m<=16 chunks
            set_err("smile_marlin_mul: n must be divisible by 128; got " + std::to_string(prob_n));
            return nullptr;
        }

        int max_par = 8;
        int64_t ws_need = std::max<int64_t>(1, (prob_n / 128) * max_par);
        torch::Tensor ws;
        if (workspace && workspace->t.defined() && workspace->t.numel() > 0) {
            ws = workspace->t.contiguous();
            if (!ws.is_cuda() || ws.device() != A.device()) {
                set_err("smile_marlin_mul: workspace must be on same CUDA device as A");
                return nullptr;
            }
            if (ws.scalar_type() != at::kInt) {
                set_err("smile_marlin_mul: workspace must be int32");
                return nullptr;
            }
        } else {
            ws = torch::zeros({ws_need}, torch::dtype(torch::kInt).device(A.device()));
        }
        if (ws.numel() < ws_need) {
            set_err("smile_marlin_mul: workspace too small need=" + std::to_string(ws_need)
                    + " got=" + std::to_string(ws.numel()));
            return nullptr;
        }

        auto C = torch::empty({tot_m, prob_n},
                              torch::dtype(torch::kHalf).device(A.device()));

        // Chunk M into strips of at most 16 so we only exercise THREAD_M_BLOCKS=1
        // kernels (CALL_IF(1, 8, 8, *)). The m=17..64 path (thread_m_blocks=2..4
        // with 64×256 tiles) has been a common source of launch/shape failures.
        const int chunk = 16;
        // Force 128×128 tiles (requires n%128==0, k%128==0 — already checked).
        const int tk = (thread_k > 0) ? thread_k : 128;
        const int tn = 128;

        for (int row = 0; row < tot_m; row += chunk) {
            int rows = std::min(chunk, tot_m - row);
            at::Tensor A_chunk;
            at::Tensor C_chunk;
            if (rows == chunk) {
                A_chunk = A.narrow(0, row, rows);
                C_chunk = C.narrow(0, row, rows);
            } else {
                // Pad up to 16 rows (Marlin tile); unused pad rows are ignored on write-back.
                A_chunk = torch::zeros({chunk, prob_k},
                                       torch::dtype(torch::kHalf).device(A.device()));
                A_chunk.narrow(0, 0, rows).copy_(A.narrow(0, row, rows));
                C_chunk = torch::empty({chunk, prob_n},
                                       torch::dtype(torch::kHalf).device(A.device()));
            }

            (void) cudaGetLastError(); // clear sticky
            int err = run_marlin(A_chunk, B, C_chunk, s, ws, groupsize, tk, tn);
            if (err == ERR_PROB_SHAPE) {
                set_err("smile_marlin_mul: problem shape not supported m="
                        + std::to_string(rows) + " (padded=" + std::to_string(chunk)
                        + ") n=" + std::to_string(prob_n) + " k=" + std::to_string(prob_k)
                        + " groupsize=" + std::to_string(groupsize)
                        + " thread_k=" + std::to_string(tk)
                        + " thread_n=" + std::to_string(tn));
                return nullptr;
            }
            if (err == ERR_KERN_SHAPE) {
                set_err("smile_marlin_mul: no Marlin kernel for m="
                        + std::to_string(chunk) + " n=" + std::to_string(prob_n)
                        + " k=" + std::to_string(prob_k) + " groupsize="
                        + std::to_string(groupsize) + " thread_k=" + std::to_string(tk)
                        + " thread_n=" + std::to_string(tn));
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
            // Ensure the chunk finished before we free padded temps / advance.
            st = cudaStreamSynchronize(at::cuda::getCurrentCUDAStream(A.get_device()));
            if (st != cudaSuccess) {
                set_err(std::string("smile_marlin_mul: CUDA sync error: ")
                        + cudaGetErrorString(st));
                return nullptr;
            }

            if (rows != chunk) {
                C.narrow(0, row, rows).copy_(C_chunk.narrow(0, 0, rows));
            }
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
