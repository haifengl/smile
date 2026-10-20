/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * MXFP8 Marlin MoE entry points; see smile_marlin_fp8.h. The launcher is vendored from vLLM (see the
 * header of smile_marlin_moe_fp8_launcher.cuh); the token grouping kernel is SMILE's.
 */
#include "smile_marlin_fp8.h"
#include "smile_marlin_moe_fp8_launcher.cuh"

#include <string>
#include <vector>

/* smile_torch.cpp */
extern "C" void smile_torch_set_error(const char *msg);
extern "C" void smile_torch_clear_error(void);

struct ST_Tensor_ {
    at::Tensor t;
};

namespace {

constexpr int kScaleGroup = 32;

int64_t round_up(int64_t x, int64_t m) { return (x + m - 1) / m * m; }

/** Padded length of the sorted id list: every expert may waste up to block_size - 1 slots. */
int64_t max_padded(int64_t numel, int64_t experts, int64_t block) {
    return round_up(numel + experts * (block - 1), block);
}

/**
 * One block: count assignments per local expert, lay the experts out back to back padded to block_size,
 * then scatter the assignment indices. Padding entries hold `numel`, which the Marlin kernel skips.
 */
__global__ void moe_align_kernel(const int32_t* __restrict__ topk_ids, int numel, int expert_start,
                                 int num_local, int block, int max_padded_len, int max_blocks,
                                 int32_t* __restrict__ sorted_ids, int32_t* __restrict__ expert_ids,
                                 int32_t* __restrict__ num_post_padded) {
    extern __shared__ int smem[];
    int* counts = smem;
    int* offsets = smem + num_local;

    for (int i = threadIdx.x; i < num_local; i += blockDim.x) counts[i] = 0;
    for (int i = threadIdx.x; i < max_padded_len; i += blockDim.x) sorted_ids[i] = numel;
    for (int i = threadIdx.x; i < max_blocks; i += blockDim.x) expert_ids[i] = -1;
    __syncthreads();

    for (int i = threadIdx.x; i < numel; i += blockDim.x) {
        int e = topk_ids[i] - expert_start;
        if (e >= 0 && e < num_local) atomicAdd(&counts[e], 1);
    }
    __syncthreads();

    if (threadIdx.x == 0) {
        int off = 0;
        for (int e = 0; e < num_local; e++) {
            offsets[e] = off;
            int padded = (counts[e] + block - 1) / block * block;
            for (int b = 0; b < padded / block; b++) expert_ids[off / block + b] = e;
            off += padded;
            counts[e] = 0;  // reused as the scatter cursor
        }
        num_post_padded[0] = off;
    }
    __syncthreads();

    for (int i = threadIdx.x; i < numel; i += blockDim.x) {
        int e = topk_ids[i] - expert_start;
        if (e >= 0 && e < num_local) {
            int pos = atomicAdd(&counts[e], 1);
            sorted_ids[offsets[e] + pos] = i;
        }
    }
}

void set_err(const std::string& msg) { smile_torch_set_error(msg.c_str()); }

}  // namespace

extern "C" ST_Tensor smile_moe_align_block_size(ST_Tensor topk_ids, int expert_start,
                                                int num_local_experts, int block_size) {
    try {
        smile_torch_clear_error();
        const at::Tensor& ids = topk_ids->t;
        if (!ids.is_cuda()) {
            set_err("smile_moe_align_block_size: topk_ids must be a CUDA tensor");
            return nullptr;
        }
        if (num_local_experts <= 0 || block_size <= 0) {
            set_err("smile_moe_align_block_size: invalid expert count or block size");
            return nullptr;
        }
        const int dev = ids.get_device();
        c10::cuda::CUDAGuard guard(dev);
        at::Tensor flat = ids.to(at::kInt).contiguous().view({-1});
        const int64_t numel = flat.numel();
        const int64_t padded = max_padded(numel, num_local_experts, block_size);
        const int64_t blocks = padded / block_size;
        at::Tensor out = torch::empty({padded + blocks + 1}, torch::dtype(torch::kInt).device(ids.device()));
        int32_t* base = out.data_ptr<int32_t>();
        const size_t smem = 2 * (size_t) num_local_experts * sizeof(int);
        moe_align_kernel<<<1, 1024, smem, at::cuda::getCurrentCUDAStream(dev)>>>(
            flat.data_ptr<int32_t>(), (int) numel, expert_start, num_local_experts, block_size,
            (int) padded, (int) blocks, base, base + padded, base + padded + blocks);
        cudaError_t st = cudaGetLastError();
        if (st != cudaSuccess) {
            set_err(std::string("smile_moe_align_block_size: CUDA error: ") + cudaGetErrorString(st));
            return nullptr;
        }
        return new ST_Tensor_{ out };
    } catch (const std::exception& ex) {
        smile_torch_set_error(ex.what());
        return nullptr;
    }
}

extern "C" ST_Tensor smile_marlin_moe_fp8_gemm(ST_Tensor a, ST_Tensor qweight, ST_Tensor scales,
                                               ST_Tensor align, ST_Tensor topk_weights,
                                               int block_size, int top_k, int mul_topk_weights) {
    try {
        smile_torch_clear_error();
        const at::Tensor& A = a->t;
        const at::Tensor& B = qweight->t;
        const at::Tensor& S = scales->t;
        const at::Tensor& AL = align->t;
        const at::Tensor& TW = topk_weights->t;
        namespace M = marlin_moe_wna16;
        if (!A.is_cuda() || A.dim() != 2 || A.scalar_type() != at::kBFloat16) {
            set_err("smile_marlin_moe_fp8_gemm: activations must be a 2-D bfloat16 CUDA tensor");
            return nullptr;
        }
        if (A.stride(1) != 1 || A.stride(0) % 8 != 0 || ((uintptr_t) A.data_ptr()) % 16 != 0) {
            set_err("smile_marlin_moe_fp8_gemm: activations must be row-major with 16-byte alignment");
            return nullptr;
        }
        if (block_size != 8 && (block_size % 16 != 0 || block_size < 16 || block_size > 64)) {
            set_err("smile_marlin_moe_fp8_gemm: unsupported block_size " + std::to_string(block_size));
            return nullptr;
        }
        const int64_t size_m = A.size(0);
        const int64_t size_k = A.size(1);
        const int64_t experts = B.size(0);
        const int64_t size_n = B.size(2) / M::tile_size * 4;
        if (B.dim() != 3 || B.scalar_type() != at::kInt || !B.is_contiguous()
                || B.size(1) != size_k / M::tile_size) {
            set_err("smile_marlin_moe_fp8_gemm: qweight must be int32 [E, K/16, N*4]");
            return nullptr;
        }
        if (S.dim() != 3 || S.scalar_type() != at::kByte || !S.is_contiguous() || S.size(0) != experts
                || S.size(1) != size_k / kScaleGroup || S.size(2) != size_n) {
            set_err("smile_marlin_moe_fp8_gemm: scales must be uint8 [E, K/32, N]");
            return nullptr;
        }
        if (TW.scalar_type() != at::kFloat || !TW.is_contiguous()) {
            set_err("smile_marlin_moe_fp8_gemm: topk_weights must be contiguous float32");
            return nullptr;
        }
        const int64_t numel = TW.numel();
        const int64_t padded = max_padded(numel, experts, block_size);
        const int64_t blocks = padded / block_size;
        if (numel != size_m * top_k && !(top_k == 1 && numel == size_m)) {
            set_err("smile_marlin_moe_fp8_gemm: topk_weights has " + std::to_string(numel)
                    + " entries, expected size_m * top_k = " + std::to_string(size_m * top_k));
            return nullptr;
        }
        if (AL.scalar_type() != at::kInt || AL.numel() < padded + blocks + 1) {
            set_err("smile_marlin_moe_fp8_gemm: align tensor does not match this problem size");
            return nullptr;
        }
        const int dev = A.get_device();
        c10::cuda::CUDAGuard guard(dev);
        int sms = 0;
        cudaDeviceGetAttribute(&sms, cudaDevAttrMultiProcessorCount, dev);

        // Rows of dropped (non-local) assignments are never written, so start from zeros.
        at::Tensor C = torch::zeros({size_m * top_k, size_n}, A.options());
        int64_t max_c_tmp = std::min<int64_t>(size_n * padded,
                                              (int64_t) sms * 4 * block_size * M::max_thread_n);
        if (block_size == 8) max_c_tmp *= 2;
        at::Tensor c_tmp = torch::empty({max_c_tmp}, torch::dtype(torch::kFloat).device(A.device()));
        at::Tensor workspace = torch::zeros({sms * 4}, torch::dtype(torch::kInt).device(A.device()));
        at::Tensor empty_f = torch::empty({0}, torch::dtype(torch::kFloat).device(A.device()));
        at::Tensor empty_b = torch::empty({0}, A.options());

        const int32_t* al = AL.data_ptr<int32_t>();
        (void) cudaGetLastError();
        M::marlin_mm(A.data_ptr(), B.data_ptr(), C.data_ptr(), c_tmp.data_ptr(), empty_b.data_ptr(),
                     empty_f.data_ptr(), S.data_ptr(), empty_f.data_ptr(), empty_b.data_ptr(),
                     (void*) al, (void*) (al + padded), (void*) (al + padded + blocks),
                     TW.data_ptr(), block_size, (int) experts, top_k, mul_topk_weights != 0,
                     (int) size_m, (int) size_n, (int) size_k, workspace.data_ptr(), vllm::kBFloat16,
                     vllm::kFE4M3fn, vllm::kBFloat16, vllm::kFE8M0fnu, /*has_bias=*/false,
                     /*has_zp=*/false, kScaleGroup, dev, at::cuda::getCurrentCUDAStream(dev),
                     /*thread_k=*/-1, /*thread_n=*/-1, sms, /*blocks_per_sm=*/-1,
                     /*use_atomic_add=*/false, /*use_fp32_reduce=*/true, /*is_zp_float=*/false);
        cudaError_t st = cudaGetLastError();
        if (st != cudaSuccess) {
            set_err(std::string("smile_marlin_moe_fp8_gemm: CUDA launch error: ") + cudaGetErrorString(st));
            return nullptr;
        }
        return new ST_Tensor_{ C };
    } catch (const std::exception& ex) {
        smile_torch_set_error(ex.what());
        return nullptr;
    }
}
