/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * MXFP8 (1x32 E8M0 scales) to DeepSeek-style block FP8 (128x128 float scales) conversion, so MXFP8
 * checkpoints can run on the block-scaled FP8 GEMM of Hopper. See smile_marlin_fp8.h.
 */
#include "smile_marlin_fp8.h"

#include <ATen/cuda/CUDAContext.h>
#include <c10/cuda/CUDAGuard.h>
#include <cuda_fp8.h>
#include <torch/torch.h>

#include <string>

/* smile_torch.cpp */
extern "C" void smile_torch_set_error(const char *msg);
extern "C" void smile_torch_clear_error(void);

struct ST_Tensor_ {
    at::Tensor t;
};

namespace {

constexpr int kTile = 128;
constexpr int kGroup = 32;
constexpr int kSub = kTile / kGroup;  // 4 E8M0 groups per tile row

__device__ __forceinline__ float fp8_e4m3_to_float(uint8_t b) {
    __nv_fp8_e4m3 v;
    v.__x = b;
    return static_cast<float>(v);
}

__device__ __forceinline__ uint8_t float_to_fp8_e4m3(float x) {
    return __nv_cvt_float_to_fp8(x, __NV_SATFINITE, __NV_E4M3);
}

/**
 * One block per 128x128 tile of one matrix. The tile scale is the largest E8M0 exponent in the tile;
 * every element is shifted down by (tile exponent - its own group exponent), which is exact unless the
 * shifted value falls below the E4M3 subnormal range.
 */
__global__ void mxfp8_to_block_fp8_kernel(const uint8_t* __restrict__ w, const uint8_t* __restrict__ e8m0,
                                          uint8_t* __restrict__ out, float* __restrict__ scale_inv,
                                          int n, int k) {
    const int tile_col = blockIdx.x;
    const int tile_row = blockIdx.y;
    const int mat = blockIdx.z;
    const int tiles_k = k / kTile;
    const int tiles_n = n / kTile;
    const size_t w_off = (size_t) mat * n * k;
    const size_t e_off = (size_t) mat * n * (k / kGroup);

    __shared__ int smax[8];
    __shared__ int tile_exp;

    // Largest exponent over the 128 rows x 4 groups of this tile.
    int local = 0;
    for (int i = threadIdx.x; i < kTile * kSub; i += blockDim.x) {
        const int r = i / kSub;
        const int g = i % kSub;
        const int e = e8m0[e_off + (size_t) (tile_row * kTile + r) * (k / kGroup) + tile_col * kSub + g];
        local = max(local, e == 255 ? 0 : e);
    }
    for (int o = 16; o > 0; o >>= 1) local = max(local, __shfl_down_sync(0xffffffff, local, o));
    if ((threadIdx.x & 31) == 0) smax[threadIdx.x >> 5] = local;
    __syncthreads();
    if (threadIdx.x == 0) {
        int m = 0;
        for (int i = 0; i < blockDim.x / 32; i++) m = max(m, smax[i]);
        tile_exp = m;
        scale_inv[(size_t) mat * tiles_n * tiles_k + (size_t) tile_row * tiles_k + tile_col] =
            ldexpf(1.0f, m - 127);
    }
    __syncthreads();

    for (int i = threadIdx.x; i < kTile * kTile; i += blockDim.x) {
        const int r = i / kTile;
        const int c = i % kTile;
        const size_t row = (size_t) tile_row * kTile + r;
        const size_t col = (size_t) tile_col * kTile + c;
        const int e = e8m0[e_off + row * (k / kGroup) + col / kGroup];
        const float v = fp8_e4m3_to_float(w[w_off + row * k + col]);
        out[w_off + row * k + col] = float_to_fp8_e4m3(ldexpf(v, e - tile_exp));
    }
}

void set_err(const std::string& msg) { smile_torch_set_error(msg.c_str()); }

}  // namespace

extern "C" ST_Tensor smile_mxfp8_to_block_fp8(ST_Tensor w, ST_Tensor scale, ST_Tensor* scale_inv_out) {
    try {
        smile_torch_clear_error();
        if (!w || !scale || !scale_inv_out) {
            set_err("smile_mxfp8_to_block_fp8: null argument");
            return nullptr;
        }
        const at::Tensor& W = w->t;
        const at::Tensor& S = scale->t;
        if (!W.is_cuda() || (W.dim() != 2 && W.dim() != 3)) {
            set_err("smile_mxfp8_to_block_fp8: weight must be a 2-D or 3-D CUDA tensor");
            return nullptr;
        }
        if (W.scalar_type() != at::kFloat8_e4m3fn && W.scalar_type() != at::kByte) {
            set_err("smile_mxfp8_to_block_fp8: weight must be float8_e4m3fn or uint8");
            return nullptr;
        }
        const int64_t mats = W.dim() == 3 ? W.size(0) : 1;
        const int64_t n = W.size(W.dim() - 2);
        const int64_t k = W.size(W.dim() - 1);
        if (n % kTile != 0 || k % kTile != 0) {
            set_err("smile_mxfp8_to_block_fp8: N and K must be multiples of 128, got N=" + std::to_string(n)
                    + " K=" + std::to_string(k));
            return nullptr;
        }
        if (S.scalar_type() != at::kByte || S.numel() != mats * n * (k / kGroup)) {
            set_err("smile_mxfp8_to_block_fp8: scale must be uint8 with N*K/32 entries per matrix");
            return nullptr;
        }
        const int dev = W.get_device();
        c10::cuda::CUDAGuard guard(dev);
        at::Tensor wc = W.contiguous();
        at::Tensor sc = S.contiguous();
        at::Tensor out = torch::empty(W.sizes(), W.options());
        std::vector<int64_t> sshape;
        if (W.dim() == 3) sshape.push_back(mats);
        sshape.push_back(n / kTile);
        sshape.push_back(k / kTile);
        at::Tensor scale_inv = torch::empty(sshape, torch::dtype(torch::kFloat).device(W.device()));
        dim3 grid((unsigned) (k / kTile), (unsigned) (n / kTile), (unsigned) mats);
        mxfp8_to_block_fp8_kernel<<<grid, 256, 0, at::cuda::getCurrentCUDAStream(dev)>>>(
            reinterpret_cast<const uint8_t*>(wc.data_ptr()), reinterpret_cast<const uint8_t*>(sc.data_ptr()),
            reinterpret_cast<uint8_t*>(out.data_ptr()), scale_inv.data_ptr<float>(), (int) n, (int) k);
        cudaError_t st = cudaGetLastError();
        if (st != cudaSuccess) {
            set_err(std::string("smile_mxfp8_to_block_fp8: CUDA error: ") + cudaGetErrorString(st));
            return nullptr;
        }
        *scale_inv_out = new ST_Tensor_{ scale_inv };
        return new ST_Tensor_{ out };
    } catch (const std::exception& ex) {
        smile_torch_set_error(ex.what());
        return nullptr;
    }
}
