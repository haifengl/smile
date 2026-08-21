/*
 * Copyright (c) 2024 by FlashInfer team.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
#ifndef FLASHINFER_NORM_CUH_
#define FLASHINFER_NORM_CUH_

#include <cstdint>
#include <numeric>

#include "flashinfer/trtllm/common/cudaTypeUtils.cuh"
#include "flashinfer/trtllm/common/cudaUtils.h"
#include "flashinfer/trtllm/common/reduceKernelUtils.cuh"
#include "flashinfer/utils.cuh"
#include "math.cuh"
#include "utils.cuh"
#include "vec_dtypes.cuh"

namespace flashinfer {

namespace norm {

using namespace tensorrt_llm::common;

template <typename T>
struct QuantTypeStaticVals;

template <>
struct QuantTypeStaticVals<int8_t> {
  static constexpr float MAX_VAL = 127.f;
  static constexpr float MIN_SCALING_FACTOR = 0.f;
  static constexpr float MIN_SCALING_FACTOR_RCP = FLT_MAX;
};

// Same values as TRT-LLM quantTypeUtils.cuh; MIN_SCALING_FACTOR bound follows
// https://github.com/pytorch/FBGEMM/blob/main/fbgemm_gpu/experimental/gen_ai/src/quantize/quantize.cu
template <>
struct QuantTypeStaticVals<__nv_fp8_e4m3> {
  static constexpr float MAX_VAL = 448.f;
  static constexpr float MIN_SCALING_FACTOR = 1.0f / (448.f * 512.f);
  static constexpr float MIN_SCALING_FACTOR_RCP = 448.f * 512.f;
};

template <>
struct QuantTypeStaticVals<__nv_fp8_e5m2> {
  static constexpr float MAX_VAL = 57344.f;
  static constexpr float MIN_SCALING_FACTOR = 1.0f / (57344.f * 512.f);
  static constexpr float MIN_SCALING_FACTOR_RCP = 57344.f * 512.f;
};

template <uint32_t VEC_SIZE, typename T>
__global__ void RMSNormKernel(T* __restrict__ input, T* __restrict__ weight, T* __restrict__ output,
                              const uint32_t d, const uint32_t stride_input,
                              const uint32_t stride_output, float weight_bias, float eps) {
  const uint32_t bx = blockIdx.x;
  const uint32_t tx = threadIdx.x, ty = threadIdx.y;
  constexpr uint32_t warp_size = 32;
  const uint32_t num_warps = blockDim.y;
  // NOTE(Zihao): it's guaranteed that num_warps should be smaller than 32
  const uint32_t thread_id = tx + ty * warp_size;
  const uint32_t num_threads = num_warps * warp_size;
  const uint32_t rounds = ceil_div(d, VEC_SIZE * num_threads);
  extern __shared__ float smem[];

  float sum_sq = 0.f;

#if (__CUDACC_VER_MAJOR__ >= 12 && defined(__CUDA_ARCH__) && (__CUDA_ARCH__ >= 900))
  asm volatile("griddepcontrol.wait;");
#endif

  for (uint32_t i = 0; i < rounds; i++) {
    vec_t<T, VEC_SIZE> input_vec;
    input_vec.fill(0.f);
    if ((i * num_threads + thread_id) * VEC_SIZE < d) {
      input_vec.load(input + bx * stride_input + i * num_threads * VEC_SIZE + thread_id * VEC_SIZE);
    }
#pragma unroll
    for (uint32_t j = 0; j < VEC_SIZE; j++) {
      sum_sq += float(input_vec[j]) * float(input_vec[j]);
    }
  }

  // first, warp reduce sum
#pragma unroll
  for (uint32_t offset = warp_size / 2; offset > 0; offset /= 2) {
    sum_sq += math::shfl_xor_sync(sum_sq, offset);
  }

  smem[ty] = sum_sq;
  __syncthreads();
  // then, cross warp reduce sum using only the first warp
  if (ty == 0) {
    sum_sq = (tx < num_warps) ? smem[tx] : 0.f;
#pragma unroll
    for (uint32_t offset = warp_size / 2; offset > 0; offset /= 2) {
      sum_sq += math::shfl_xor_sync(sum_sq, offset);
    }
    smem[0] = sum_sq;
  }
  __syncthreads();

  float rms_rcp = math::rsqrt(smem[0] / float(d) + eps);

  for (uint32_t i = 0; i < rounds; i++) {
    vec_t<T, VEC_SIZE> input_vec;
    vec_t<T, VEC_SIZE> weight_vec;
    vec_t<T, VEC_SIZE> output_vec;
    input_vec.fill(0.f);
    weight_vec.fill(0.f);
    if ((i * num_threads + thread_id) * VEC_SIZE < d) {
      input_vec.load(input + bx * stride_input + i * num_threads * VEC_SIZE + thread_id * VEC_SIZE);
      weight_vec.load(weight + i * num_threads * VEC_SIZE + thread_id * VEC_SIZE);
    }
#pragma unroll
    for (uint32_t j = 0; j < VEC_SIZE; j++) {
      output_vec[j] = float(input_vec[j]) * rms_rcp * (weight_bias + float(weight_vec[j]));
    }
    if ((i * num_threads + thread_id) * VEC_SIZE < d) {
      output_vec.store(output + bx * stride_output + i * num_threads * VEC_SIZE +
                       thread_id * VEC_SIZE);
    }
  }
#if (__CUDACC_VER_MAJOR__ >= 12 && defined(__CUDA_ARCH__) && (__CUDA_ARCH__ >= 900))
  asm volatile("griddepcontrol.launch_dependents;");
#endif
}

template <typename T>
cudaError_t RMSNorm(T* input, T* weight, T* output, uint32_t batch_size, uint32_t d,
                    uint32_t stride_input, uint32_t stride_output, float eps = 1e-5,
                    bool enable_pdl = false, cudaStream_t stream = 0) {
  const uint32_t vec_size = std::gcd(16 / sizeof(T), d);

  const uint32_t block_size = std::min<uint32_t>(1024, d / vec_size);
  const uint32_t num_warps = ceil_div(block_size, 32);
  dim3 nblks(batch_size);
  dim3 nthrs(32, num_warps);
  const uint32_t smem_size = num_warps * sizeof(float);
  float weight_bias = 0.f;
  void* args[] = {&input, &weight, &output, &d, &stride_input, &stride_output, &weight_bias, &eps};

  cudaLaunchConfig_t config;
  config.gridDim = nblks;
  config.blockDim = nthrs;
  config.dynamicSmemBytes = smem_size;
  config.stream = stream;
  cudaLaunchAttribute attrs[1];
  attrs[0].id = cudaLaunchAttributeProgrammaticStreamSerialization;
  attrs[0].val.programmaticStreamSerializationAllowed = enable_pdl;
  config.numAttrs = 1;
  config.attrs = attrs;

  DISPATCH_ALIGNED_VEC_SIZE(vec_size, VEC_SIZE, {
    auto kernel = RMSNormKernel<VEC_SIZE, T>;
    FLASHINFER_CUDA_CALL(
        cudaFuncSetAttribute(kernel, cudaFuncAttributeMaxDynamicSharedMemorySize, smem_size));
    FLASHINFER_CUDA_CALL(cudaLaunchKernelEx(&config, kernel, input, weight, output, d, stride_input,
                                            stride_output, weight_bias, eps));
  });
  return cudaSuccess;
}

template <uint32_t VEC_SIZE, typename T, typename O>
__global__ void RMSNormQuantKernel(T* __restrict__ input, T* __restrict__ weight,
                                   O* __restrict__ output, const uint32_t d,
                                   const uint32_t stride_input, const uint32_t stride_output,
                                   float weight_bias, float* scale, float eps) {
  const uint32_t bx = blockIdx.x;
  const uint32_t tx = threadIdx.x, ty = threadIdx.y;
  constexpr uint32_t warp_size = 32;
  const uint32_t num_warps = blockDim.y;
  // NOTE(Zihao): it's guaranteed that num_warps should be smaller than 32
  const uint32_t thread_id = tx + ty * warp_size;
  const uint32_t num_threads = num_warps * warp_size;
  const uint32_t rounds = ceil_div(d, VEC_SIZE * num_threads);
  const float scale_inv = 1.0f / scale[0];
  extern __shared__ float smem[];

  float sum_sq = 0.f;

#if (__CUDACC_VER_MAJOR__ >= 12 && defined(__CUDA_ARCH__) && (__CUDA_ARCH__ >= 900))
  asm volatile("griddepcontrol.wait;");
#endif

  for (uint32_t i = 0; i < rounds; i++) {
    vec_t<T, VEC_SIZE> input_vec;
    input_vec.fill(0.f);
    if ((i * num_threads + thread_id) * VEC_SIZE < d) {
      input_vec.load(input + bx * stride_input + i * num_threads * VEC_SIZE + thread_id * VEC_SIZE);
    }
#pragma unroll
    for (uint32_t j = 0; j < VEC_SIZE; j++) {
      sum_sq += float(input_vec[j]) * float(input_vec[j]);
    }
  }

  // first, warp reduce sum
#pragma unroll
  for (uint32_t offset = warp_size / 2; offset > 0; offset /= 2) {
    sum_sq += math::shfl_xor_sync(sum_sq, offset);
  }

  smem[ty] = sum_sq;
  __syncthreads();
  // then, cross warp reduce sum using only the first warp
  if (ty == 0) {
    sum_sq = (tx < num_warps) ? smem[tx] : 0.f;
#pragma unroll
    for (uint32_t offset = warp_size / 2; offset > 0; offset /= 2) {
      sum_sq += math::shfl_xor_sync(sum_sq, offset);
    }
    smem[0] = sum_sq;
  }
  __syncthreads();

  float rms_rcp = math::rsqrt(smem[0] / float(d) + eps);
  constexpr float max_val = QuantTypeStaticVals<O>::MAX_VAL;

  for (uint32_t i = 0; i < rounds; i++) {
    vec_t<T, VEC_SIZE> input_vec;
    vec_t<T, VEC_SIZE> weight_vec;
    vec_t<float, VEC_SIZE> output_vec;
    input_vec.fill(0.f);
    weight_vec.fill(0.f);
    if ((i * num_threads + thread_id) * VEC_SIZE < d) {
      input_vec.load(input + bx * stride_input + i * num_threads * VEC_SIZE + thread_id * VEC_SIZE);
      weight_vec.load(weight + i * num_threads * VEC_SIZE + thread_id * VEC_SIZE);
    }
#pragma unroll
    for (uint32_t j = 0; j < VEC_SIZE; j++) {
      output_vec[j] =
          float(input_vec[j]) * rms_rcp * (weight_bias + float(weight_vec[j])) * scale_inv;
      output_vec[j] = fmaxf(-max_val, fminf(output_vec[j], max_val));
    }
    if ((i * num_threads + thread_id) * VEC_SIZE < d) {
      output_vec.cast_store(output + bx * stride_output + i * num_threads * VEC_SIZE +
                            thread_id * VEC_SIZE);
    }
  }
#if (__CUDACC_VER_MAJOR__ >= 12 && defined(__CUDA_ARCH__) && (__CUDA_ARCH__ >= 900))
  asm volatile("griddepcontrol.launch_dependents;");
#endif
}

template <typename T, typename O>
cudaError_t RMSNormQuant(T* input, T* weight, O* output, uint32_t batch_size, uint32_t d,
                         uint32_t stride_input, uint32_t stride_output, float* scale,
                         float eps = 1e-5, bool enable_pdl = false, cudaStream_t stream = 0) {
  const uint32_t vec_size = std::gcd(16 / sizeof(T), d);

  const uint32_t block_size = std::min<uint32_t>(1024, d / vec_size);
  const uint32_t num_warps = ceil_div(block_size, 32);
  dim3 nblks(batch_size);
  dim3 nthrs(32, num_warps);
  const uint32_t smem_size = num_warps * sizeof(float);
  float weight_bias = 0.f;

  cudaLaunchConfig_t config;
  config.gridDim = nblks;
  config.blockDim = nthrs;
  config.dynamicSmemBytes = smem_size;
  config.stream = stream;
  cudaLaunchAttribute attrs[1];
  attrs[0].id = cudaLaunchAttributeProgrammaticStreamSerialization;
  attrs[0].val.programmaticStreamSerializationAllowed = enable_pdl;
  config.numAttrs = 1;
  config.attrs = attrs;

  DISPATCH_ALIGNED_VEC_SIZE(vec_size, VEC_SIZE, {
    auto kernel = RMSNormQuantKernel<VEC_SIZE, T, O>;
    FLASHINFER_CUDA_CALL(
        cudaFuncSetAttribute(kernel, cudaFuncAttributeMaxDynamicSharedMemorySize, smem_size));
    FLASHINFER_CUDA_CALL(cudaLaunchKernelEx(&config, kernel, input, weight, output, d, stride_input,
                                            stride_output, weight_bias, scale, eps));
  });
  return cudaSuccess;
}

template <uint32_t VEC_SIZE, typename T>
__global__ void QKRMSNormKernel(T* __restrict__ input, T* __restrict__ weight,
                                T* __restrict__ output, const uint32_t d, const uint32_t batch_size,
                                const uint32_t num_heads, const uint32_t stride_input_n,
                                const uint32_t stride_input_h, const uint32_t stride_output_n,
                                const uint32_t stride_output_h, float weight_bias, float eps) {
  const uint32_t num_blks = gridDim.x, num_warps = blockDim.y;
  const uint32_t num_workers = num_blks * num_warps;  // unroll on warp-dim
  const uint32_t num_jobs = batch_size * num_heads;

  const uint32_t bx = blockIdx.x;
  const uint32_t tx = threadIdx.x, ty = threadIdx.y;
  const uint32_t worker_idx = bx * num_warps + ty;

  constexpr uint32_t warp_size = 32;
  const uint32_t num_threads = warp_size;
  const uint32_t thread_id = tx;
  const uint32_t rounds = ceil_div(d, VEC_SIZE * num_threads);

#if (__CUDACC_VER_MAJOR__ >= 12 && defined(__CUDA_ARCH__) && (__CUDA_ARCH__ >= 900))
  asm volatile("griddepcontrol.wait;");
#endif

  for (uint32_t job_idx = worker_idx; job_idx < num_jobs; job_idx += num_workers) {
    // clear buffer
    float sum_sq = 0.f;

    // map back to batch-idx and head-idx; layout [batch_size, num_heads, head_dim]
    const uint32_t batch_idx = job_idx / num_heads;
    const uint32_t head_idx = job_idx % num_heads;

    for (uint32_t i = 0; i < rounds; i++) {
      vec_t<T, VEC_SIZE> input_vec;
      input_vec.fill(0.f);
      if ((i * num_threads + thread_id) * VEC_SIZE < d) {
        input_vec.load(input + batch_idx * stride_input_n + head_idx * stride_input_h +
                       i * num_threads * VEC_SIZE + thread_id * VEC_SIZE);
      }
#pragma unroll
      for (uint32_t j = 0; j < VEC_SIZE; j++) {
        sum_sq += float(input_vec[j]) * float(input_vec[j]);
      }
    }

    // only have warp reduce sum
    // no need for __syncwarps as shfl already sync
#pragma unroll
    for (uint32_t offset = warp_size / 2; offset > 0; offset /= 2) {
      sum_sq += math::shfl_xor_sync(sum_sq, offset);
    }

    float rms_rcp = math::rsqrt(sum_sq / float(d) + eps);

    for (uint32_t i = 0; i < rounds; i++) {
      vec_t<T, VEC_SIZE> input_vec;
      vec_t<T, VEC_SIZE> weight_vec;
      vec_t<T, VEC_SIZE> output_vec;
      input_vec.fill(0.f);
      weight_vec.fill(0.f);
      if ((i * num_threads + thread_id) * VEC_SIZE < d) {
        input_vec.load(input + batch_idx * stride_input_n + head_idx * stride_input_h +
                       i * num_threads * VEC_SIZE + thread_id * VEC_SIZE);
        weight_vec.load(weight + i * num_threads * VEC_SIZE + thread_id * VEC_SIZE);
      }
#pragma unroll
      for (uint32_t j = 0; j < VEC_SIZE; j++) {
        output_vec[j] = float(input_vec[j]) * rms_rcp * (weight_bias + float(weight_vec[j]));
      }
      if ((i * num_threads + thread_id) * VEC_SIZE < d) {
        output_vec.store(output + batch_idx * stride_output_n + head_idx * stride_output_h +
                         i * num_threads * VEC_SIZE + thread_id * VEC_SIZE);
      }
    }
  }
#if (__CUDACC_VER_MAJOR__ >= 12 && defined(__CUDA_ARCH__) && (__CUDA_ARCH__ >= 900))
  asm volatile("griddepcontrol.launch_dependents;");
#endif
}

template <typename T>
cudaError_t QKRMSNorm(T* input, T* weight, T* output, uint32_t batch_size, uint32_t num_heads,
                      uint32_t d, uint32_t stride_input_n, uint32_t stride_input_h,
                      uint32_t stride_output_n, uint32_t stride_output_h, float eps = 1e-5,
                      bool enable_pdl = false, cudaStream_t stream = 0) {
  const uint32_t vec_size = std::gcd(16 / sizeof(T), d);
  const uint32_t num_warps = 4;
  const uint32_t smem_size = 0;

  float weight_bias = 0.f;

  cudaLaunchConfig_t config;
  config.dynamicSmemBytes = smem_size;
  config.stream = stream;
  cudaLaunchAttribute attrs[1];
  attrs[0].id = cudaLaunchAttributeProgrammaticStreamSerialization;
  attrs[0].val.programmaticStreamSerializationAllowed = enable_pdl;
  config.numAttrs = 1;
  config.attrs = attrs;

  DISPATCH_ALIGNED_VEC_SIZE(vec_size, VEC_SIZE, {
    auto kernel = QKRMSNormKernel<VEC_SIZE, T>;

    // calculate launching blocks
    int num_blocks_per_sm = 0, num_sms = 0, dev_id = 0;
    FLASHINFER_CUDA_CALL(cudaOccupancyMaxActiveBlocksPerMultiprocessor(&num_blocks_per_sm, kernel,
                                                                       num_warps * 32, smem_size));
    FLASHINFER_CUDA_CALL(cudaGetDevice(&dev_id));
    FLASHINFER_CUDA_CALL(cudaDeviceGetAttribute(&num_sms, cudaDevAttrMultiProcessorCount, dev_id));

    const int needed_blocks = ceil_div(batch_size * num_heads, num_warps);
    dim3 nblks(std::min(num_blocks_per_sm * num_sms, needed_blocks));
    dim3 nthrs(32, num_warps);
    config.gridDim = nblks;
    config.blockDim = nthrs;

    // execute kernel
    FLASHINFER_CUDA_CALL(cudaLaunchKernelEx(&config, kernel, input, weight, output, d, batch_size,
                                            num_heads, stride_input_n, stride_input_h,
                                            stride_output_n, stride_output_h, weight_bias, eps));
  });
  return cudaSuccess;
}

template <uint32_t VEC_SIZE, typename T>
__global__ void FusedAddRMSNormKernel(T* __restrict__ input, T* __restrict__ residual,
                                      T* __restrict__ weight, const uint32_t d,
                                      const uint32_t stride_input, const uint32_t stride_residual,
                                      float weight_bias, float eps) {
  const uint32_t bx = blockIdx.x;
  const uint32_t tx = threadIdx.x, ty = threadIdx.y;
  constexpr uint32_t warp_size = 32;
  const uint32_t num_warps = blockDim.y;
  const uint32_t thread_id = tx + ty * warp_size;
  const uint32_t num_threads = num_warps * warp_size;
  const uint32_t rounds = ceil_div(d, VEC_SIZE * num_threads);
  extern __shared__ float smem[];
  float* smem_x = smem + ceil_div(num_warps, 4) * 4;

  float sum_sq = 0.f;
#if (__CUDACC_VER_MAJOR__ >= 12 && defined(__CUDA_ARCH__) && (__CUDA_ARCH__ >= 900))
  asm volatile("griddepcontrol.wait;");
#endif

  for (uint32_t i = 0; i < rounds; i++) {
    vec_t<T, VEC_SIZE> input_vec;
    input_vec.fill(0.f);
    vec_t<T, VEC_SIZE> residual_vec;
    residual_vec.fill(0.f);
    vec_t<float, VEC_SIZE> x_vec;
    x_vec.fill(0.f);
    if ((i * num_threads + thread_id) * VEC_SIZE < d) {
      input_vec.load(input + bx * stride_input + i * num_threads * VEC_SIZE + thread_id * VEC_SIZE);
      residual_vec.load(residual + bx * stride_residual + i * num_threads * VEC_SIZE +
                        thread_id * VEC_SIZE);
    }
#pragma unroll
    for (uint32_t j = 0; j < VEC_SIZE; j++) {
      float x = float(input_vec[j]);
      x += float(residual_vec[j]);
      sum_sq += x * x;
      residual_vec[j] = (T)x;
      x_vec[j] = x;
    }
    if ((i * num_threads + thread_id) * VEC_SIZE < d) {
      residual_vec.store(residual + bx * stride_residual + i * num_threads * VEC_SIZE +
                         thread_id * VEC_SIZE);
      x_vec.store(smem_x + i * num_threads * VEC_SIZE + thread_id * VEC_SIZE);
    }
  }

  // first, warp reduce sum
#pragma unroll
  for (uint32_t offset = warp_size / 2; offset > 0; offset /= 2) {
    sum_sq += math::shfl_xor_sync(sum_sq, offset);
  }

  smem[ty] = sum_sq;
  __syncthreads();
  // then, cross warp reduce sum using only the first warp
  if (ty == 0) {
    sum_sq = (tx < num_warps) ? smem[tx] : 0.f;
#pragma unroll
    for (uint32_t offset = warp_size / 2; offset > 0; offset /= 2) {
      sum_sq += math::shfl_xor_sync(sum_sq, offset);
    }
    smem[0] = sum_sq;
  }
  __syncthreads();

  float rms_rcp = math::rsqrt(smem[0] / float(d) + eps);

  for (uint32_t i = 0; i < rounds; i++) {
    vec_t<T, VEC_SIZE> input_vec;
    vec_t<T, VEC_SIZE> weight_vec;
    vec_t<float, VEC_SIZE> x_vec;
    input_vec.fill(0.f);
    weight_vec.fill(0.f);
    x_vec.fill(0.f);
    if ((i * num_threads + thread_id) * VEC_SIZE < d) {
      weight_vec.load(weight + i * num_threads * VEC_SIZE + thread_id * VEC_SIZE);
      x_vec.load(smem_x + i * num_threads * VEC_SIZE + thread_id * VEC_SIZE);
    }
#pragma unroll
    for (uint32_t j = 0; j < VEC_SIZE; j++) {
      input_vec[j] = x_vec[j] * rms_rcp * (weight_bias + float(weight_vec[j]));
    }
    if ((i * num_threads + thread_id) * VEC_SIZE < d) {
      input_vec.store(input + bx * stride_input + i * num_threads * VEC_SIZE +
                      thread_id * VEC_SIZE);
    }
  }
#if (__CUDACC_VER_MAJOR__ >= 12 && defined(__CUDA_ARCH__) && (__CUDA_ARCH__ >= 900))
  asm volatile("griddepcontrol.launch_dependents;");
#endif
}

template <typename T>
cudaError_t FusedAddRMSNorm(T* input, T* residual, T* weight, uint32_t batch_size, uint32_t d,
                            uint32_t stride_input, uint32_t stride_residual, float eps = 1e-5,
                            bool enable_pdl = false, cudaStream_t stream = 0) {
  const uint32_t vec_size = std::gcd(16 / sizeof(T), d);

  const uint32_t block_size = std::min<uint32_t>(1024, d / vec_size);
  const uint32_t num_warps = ceil_div(block_size, 32);
  dim3 nblks(batch_size);
  dim3 nthrs(32, num_warps);
  const uint32_t smem_size = (ceil_div(num_warps, 4) * 4 + d) * sizeof(float);
  float weight_bias = 0.f;
  void* args[] = {&input,        &residual,        &weight,      &d,
                  &stride_input, &stride_residual, &weight_bias, &eps};

  cudaLaunchConfig_t config;
  config.gridDim = nblks;
  config.blockDim = nthrs;
  config.dynamicSmemBytes = smem_size;
  config.stream = stream;
  cudaLaunchAttribute attrs[1];
  attrs[0].id = cudaLaunchAttributeProgrammaticStreamSerialization;
  attrs[0].val.programmaticStreamSerializationAllowed = enable_pdl;
  config.numAttrs = 1;
  config.attrs = attrs;

  DISPATCH_ALIGNED_VEC_SIZE(vec_size, VEC_SIZE, {
    auto kernel = FusedAddRMSNormKernel<VEC_SIZE, T>;
    FLASHINFER_CUDA_CALL(
        cudaFuncSetAttribute(kernel, cudaFuncAttributeMaxDynamicSharedMemorySize, smem_size));
    FLASHINFER_CUDA_CALL(cudaLaunchKernelEx(&config, kernel, input, residual, weight, d,
                                            stride_input, stride_residual, weight_bias, eps));
  });

  return cudaSuccess;
}

template <uint32_t VEC_SIZE, typename T, typename O>
__global__ void FusedAddRMSNormQuantKernel(T* __restrict__ input, T* __restrict__ residual,
                                           T* __restrict__ weight, O* __restrict__ output,
                                           const uint32_t d, const uint32_t stride_input,
                                           const uint32_t stride_residual,
                                           const uint32_t stride_output, float weight_bias,
                                           float* scale, float eps) {
  const uint32_t bx = blockIdx.x;
  const uint32_t tx = threadIdx.x, ty = threadIdx.y;
  constexpr uint32_t warp_size = 32;
  const uint32_t num_warps = blockDim.y;
  const uint32_t thread_id = tx + ty * warp_size;
  const uint32_t num_threads = num_warps * warp_size;
  const uint32_t rounds = ceil_div(d, VEC_SIZE * num_threads);
  const float scale_inv = 1.0f / scale[0];
  extern __shared__ float smem[];
  float* smem_x = smem + ceil_div(num_warps, 4) * 4;

  float sum_sq = 0.f;
#if (__CUDACC_VER_MAJOR__ >= 12 && defined(__CUDA_ARCH__) && (__CUDA_ARCH__ >= 900))
  asm volatile("griddepcontrol.wait;");
#endif

  for (uint32_t i = 0; i < rounds; i++) {
    vec_t<T, VEC_SIZE> input_vec;
    input_vec.fill(0.f);
    vec_t<T, VEC_SIZE> residual_vec;
    residual_vec.fill(0.f);
    vec_t<float, VEC_SIZE> x_vec;
    x_vec.fill(0.f);
    if ((i * num_threads + thread_id) * VEC_SIZE < d) {
      input_vec.load(input + bx * stride_input + i * num_threads * VEC_SIZE + thread_id * VEC_SIZE);
      residual_vec.load(residual + bx * stride_residual + i * num_threads * VEC_SIZE +
                        thread_id * VEC_SIZE);
    }
#pragma unroll
    for (uint32_t j = 0; j < VEC_SIZE; j++) {
      float x = float(input_vec[j]);
      x += float(residual_vec[j]);
      sum_sq += x * x;
      residual_vec[j] = (T)x;
      x_vec[j] = x;
    }
    if ((i * num_threads + thread_id) * VEC_SIZE < d) {
      residual_vec.store(residual + bx * stride_residual + i * num_threads * VEC_SIZE +
                         thread_id * VEC_SIZE);
      x_vec.store(smem_x + i * num_threads * VEC_SIZE + thread_id * VEC_SIZE);
    }
  }

  // first, warp reduce sum
#pragma unroll
  for (uint32_t offset = warp_size / 2; offset > 0; offset /= 2) {
    sum_sq += math::shfl_xor_sync(sum_sq, offset);
  }

  smem[ty] = sum_sq;
  __syncthreads();
  // then, cross warp reduce sum using only the first warp
  if (ty == 0) {
    sum_sq = (tx < num_warps) ? smem[tx] : 0.f;
#pragma unroll
    for (uint32_t offset = warp_size / 2; offset > 0; offset /= 2) {
      sum_sq += math::shfl_xor_sync(sum_sq, offset);
    }
    smem[0] = sum_sq;
  }
  __syncthreads();

  float rms_rcp = math::rsqrt(smem[0] / float(d) + eps);
  constexpr float max_val = QuantTypeStaticVals<O>::MAX_VAL;

  for (uint32_t i = 0; i < rounds; i++) {
    vec_t<float, VEC_SIZE> output_vec;
    vec_t<T, VEC_SIZE> weight_vec;
    vec_t<float, VEC_SIZE> x_vec;
    output_vec.fill(0.f);
    weight_vec.fill(0.f);
    x_vec.fill(0.f);
    if ((i * num_threads + thread_id) * VEC_SIZE < d) {
      weight_vec.load(weight + i * num_threads * VEC_SIZE + thread_id * VEC_SIZE);
      x_vec.load(smem_x + i * num_threads * VEC_SIZE + thread_id * VEC_SIZE);
    }
#pragma unroll
    for (uint32_t j = 0; j < VEC_SIZE; j++) {
      output_vec[j] = x_vec[j] * rms_rcp * (weight_bias + float(weight_vec[j])) * scale_inv;
      output_vec[j] = fmaxf(-max_val, fminf(output_vec[j], max_val));
    }
    if ((i * num_threads + thread_id) * VEC_SIZE < d) {
      output_vec.cast_store(output + bx * stride_output + i * num_threads * VEC_SIZE +
                            thread_id * VEC_SIZE);
    }
  }
#if (__CUDACC_VER_MAJOR__ >= 12 && defined(__CUDA_ARCH__) && (__CUDA_ARCH__ >= 900))
  asm volatile("griddepcontrol.launch_dependents;");
#endif
}

template <typename T, typename O>
cudaError_t FusedAddRMSNormQuant(T* input, T* residual, T* weight, O* output, uint32_t batch_size,
                                 uint32_t d, uint32_t stride_input, uint32_t stride_residual,
                                 uint32_t stride_output, float* scale, float eps = 1e-5,
                                 bool enable_pdl = false, cudaStream_t stream = 0) {
  const uint32_t vec_size = std::gcd(16 / sizeof(T), d);

  const uint32_t block_size = std::min<uint32_t>(1024, d / vec_size);
  const uint32_t num_warps = ceil_div(block_size, 32);
  dim3 nblks(batch_size);
  dim3 nthrs(32, num_warps);
  const uint32_t smem_size = (ceil_div(num_warps, 4) * 4 + d) * sizeof(float);
  float weight_bias = 0.f;

  cudaLaunchConfig_t config;
  config.gridDim = nblks;
  config.blockDim = nthrs;
  config.dynamicSmemBytes = smem_size;
  config.stream = stream;
  cudaLaunchAttribute attrs[1];
  attrs[0].id = cudaLaunchAttributeProgrammaticStreamSerialization;
  attrs[0].val.programmaticStreamSerializationAllowed = enable_pdl;
  config.numAttrs = 1;
  config.attrs = attrs;

  DISPATCH_ALIGNED_VEC_SIZE(vec_size, VEC_SIZE, {
    auto kernel = FusedAddRMSNormQuantKernel<VEC_SIZE, T, O>;
    FLASHINFER_CUDA_CALL(
        cudaFuncSetAttribute(kernel, cudaFuncAttributeMaxDynamicSharedMemorySize, smem_size));
    FLASHINFER_CUDA_CALL(cudaLaunchKernelEx(&config, kernel, input, residual, weight, output, d,
                                            stride_input, stride_residual, stride_output,
                                            weight_bias, scale, eps));
  });

  return cudaSuccess;
}

template <typename T>
cudaError_t GemmaRMSNorm(T* input, T* weight, T* output, uint32_t batch_size, uint32_t d,
                         uint32_t stride_input, uint32_t stride_output, float eps = 1e-5,
                         bool enable_pdl = false, cudaStream_t stream = 0) {
  const uint32_t vec_size = std::gcd(16 / sizeof(T), d);

  const uint32_t block_size = std::min<uint32_t>(1024, d / vec_size);
  const uint32_t num_warps = ceil_div(block_size, 32);
  dim3 nblks(batch_size);
  dim3 nthrs(32, num_warps);
  const uint32_t smem_size = num_warps * sizeof(float);
  float weight_bias = 1.f;
  void* args[] = {&input, &weight, &output, &d, &stride_input, &stride_output, &weight_bias, &eps};

  cudaLaunchConfig_t config;
  config.gridDim = nblks;
  config.blockDim = nthrs;
  config.dynamicSmemBytes = smem_size;
  config.stream = stream;
  cudaLaunchAttribute attrs[1];
  attrs[0].id = cudaLaunchAttributeProgrammaticStreamSerialization;
  attrs[0].val.programmaticStreamSerializationAllowed = enable_pdl;
  config.numAttrs = 1;
  config.attrs = attrs;

  DISPATCH_ALIGNED_VEC_SIZE(vec_size, VEC_SIZE, {
    auto kernel = RMSNormKernel<VEC_SIZE, T>;
    FLASHINFER_CUDA_CALL(
        cudaFuncSetAttribute(kernel, cudaFuncAttributeMaxDynamicSharedMemorySize, smem_size));
    FLASHINFER_CUDA_CALL(cudaLaunchKernelEx(&config, kernel, input, weight, output, d, stride_input,
                                            stride_output, weight_bias, eps));
  });
  return cudaSuccess;
}

template <typename T>
cudaError_t GemmaFusedAddRMSNorm(T* input, T* residual, T* weight, uint32_t batch_size, uint32_t d,
                                 uint32_t stride_input, uint32_t stride_residual, float eps = 1e-5,
                                 bool enable_pdl = false, cudaStream_t stream = 0) {
  const uint32_t vec_size = std::gcd(16 / sizeof(T), d);

  const uint32_t block_size = std::min<uint32_t>(1024, d / vec_size);
  const uint32_t num_warps = ceil_div(block_size, 32);
  dim3 nblks(batch_size);
  dim3 nthrs(32, num_warps);
  // NOTE(Zihao): use ceil_div(num_warps, 4) * 4 for address alignment to 16 bytes
  const uint32_t smem_size = (ceil_div(num_warps, 4) * 4 + d) * sizeof(float);
  float weight_bias = 1.f;
  void* args[] = {&input,        &residual,        &weight,      &d,
                  &stride_input, &stride_residual, &weight_bias, &eps};

  cudaLaunchConfig_t config;
  config.gridDim = nblks;
  config.blockDim = nthrs;
  config.dynamicSmemBytes = smem_size;
  config.stream = stream;
  cudaLaunchAttribute attrs[1];
  attrs[0].id = cudaLaunchAttributeProgrammaticStreamSerialization;
  attrs[0].val.programmaticStreamSerializationAllowed = enable_pdl;
  config.numAttrs = 1;
  config.attrs = attrs;

  DISPATCH_ALIGNED_VEC_SIZE(vec_size, VEC_SIZE, {
    auto kernel = FusedAddRMSNormKernel<VEC_SIZE, T>;
    FLASHINFER_CUDA_CALL(
        cudaFuncSetAttribute(kernel, cudaFuncAttributeMaxDynamicSharedMemorySize, smem_size));
    FLASHINFER_CUDA_CALL(cudaLaunchKernelEx(&config, kernel, input, residual, weight, d,
                                            stride_input, stride_residual, weight_bias, eps));
  });

  return cudaSuccess;
}

template <typename Tf, typename T>
__inline__ __device__ Tf compute_layernorm(Tf val, float s_mean, float s_variance, T const* gemma,
                                           T const* beta, int i) {
  Tf ret = (val - s_mean) * s_variance * cuda_cast<Tf>(gemma[i]);
  if (beta != nullptr) {
    ret = ret + cuda_cast<Tf>(beta[i]);
  }
  return ret;
}

template <typename T, typename Tw, typename QuantT, bool USE_SHMEM,
          bool USE_DIFF_OF_SQUARES = false>
__global__ void generalLayerNorm(T const* input, Tw const* gemma, Tw const* beta, T* normed_output,
                                 float const eps, int tokens, int hidden_dim,
                                 float const* clamp_ptr, float const* scale_orig_quant_per_tensor,
                                 float* scale_orig_quant_per_token, float* sum_per_token,
                                 QuantT* normed_output_quant, bool has_fp8_min_scaling) {
  constexpr auto num_elems_T = num_elems<T>::value;
  using QuantT_packed_t = typename packed_as<QuantT, num_elems_T>::type;
  using float_packed_t = typename packed_as<float, num_elems_T>::type;
  using T_scalar = typename packed_as<T, 1>::type;

  // The clamping minimum / maximum values.
  T const clamp_min = cuda_cast<T>(clamp_ptr ? clamp_ptr[0] : -FLT_MAX);
  T const clamp_max = cuda_cast<T>(clamp_ptr ? clamp_ptr[1] : FLT_MAX);

  // The quantized data type's maximum value (upper-bound).
  static constexpr float MAX_QUANT_VAL = QuantTypeStaticVals<QuantT>::MAX_VAL;
  // The minimum scaling factor (lower-bound)
  static constexpr float MIN_SCALING_FACTOR = QuantTypeStaticVals<QuantT>::MIN_SCALING_FACTOR;
  static constexpr float MIN_SCALING_FACTOR_RCP =
      QuantTypeStaticVals<QuantT>::MIN_SCALING_FACTOR_RCP;

  extern __shared__ __align__(sizeof(float)) char _shmem[];
  T* shmem = reinterpret_cast<T*>(_shmem);
  __shared__ float s_mean;
  __shared__ float s_variance;

  int const tidx = threadIdx.x;
  int const bidx = blockIdx.x;

  float mean = 0.0f;
  float variance = 0.0f;
  float local_sum = 0.0f;
  float local_var_sum = 0.0f;

  int const n_elems = hidden_dim / num_elems_T;
  for (int i = tidx; i < n_elems; i += blockDim.x) {
    const T val = input[bidx * n_elems + i];
    if constexpr (USE_SHMEM) {
      shmem[i] = val;
    }

    const float_packed_t val_f = cuda_cast<float_packed_t>(val);
    local_sum += cuda_sum<float>(val_f);
    if constexpr (USE_DIFF_OF_SQUARES) {
      local_var_sum += cuda_sum<float>(val_f * val_f);
    }
  }

  if constexpr (USE_DIFF_OF_SQUARES) {
    float packed[2] = {local_sum, local_var_sum};
    blockReduceSumV2<float, 2>(packed);
    mean = packed[0];
    variance = packed[1];
  } else {
    mean = blockReduceSum(local_sum);
  }

  if (threadIdx.x == 0) {
    mean = mean / hidden_dim;
    s_mean = mean;
    if constexpr (USE_DIFF_OF_SQUARES) {
      variance = (variance / hidden_dim) - (mean * mean);  // Var[x] = E[x²] - E[x]²
      s_variance = rsqrtf(variance + eps);
    }
  }
  __syncthreads();

  if constexpr (!USE_DIFF_OF_SQUARES) {
    for (int i = tidx; i < n_elems; i += blockDim.x) {
      const T val = USE_SHMEM ? shmem[i] : input[bidx * n_elems + i];
      float_packed_t diff = cuda_cast<float_packed_t>(val) - s_mean;
      local_var_sum += cuda_sum<float>(diff * diff);
    }
    variance = blockReduceSum(local_var_sum);

    if (threadIdx.x == 0) {
      s_variance = rsqrtf(variance / hidden_dim + eps);
    }
    __syncthreads();
  }

  bool const with_per_token_scaling = scale_orig_quant_per_token != nullptr;
  bool const with_per_tensor_scaling = scale_orig_quant_per_tensor != nullptr;
  bool const with_per_token_sum = sum_per_token != nullptr;

  // The reciprocal makes the per-tensor path follow the flashinfer convention
  // out = normed / scale, matching RMSNormQuant.
  const float_packed_t scale_orig_quant = cuda_cast<float_packed_t>(
      with_per_tensor_scaling ? 1.0f / (*scale_orig_quant_per_tensor) : 0.0f);
  T_scalar amax = 1e-6f;
  local_sum = 0.f;

  for (int i = tidx; i < n_elems; i += blockDim.x) {
    int const index = bidx * n_elems + i;
    const float_packed_t val_f = cuda_cast<float_packed_t>(USE_SHMEM ? shmem[i] : input[index]);
    T val = cuda_cast<T>(compute_layernorm(val_f, s_mean, s_variance, gemma, beta, i));

    if (with_per_token_scaling) {
      val = cuda_clamp(val, clamp_min, clamp_max);
      amax = cuda_max(cuda_max<T_scalar, T>(cuda_abs(val)), amax);
      if constexpr (USE_SHMEM) {
        shmem[i] = val;
      }
    } else if (with_per_tensor_scaling) {
      val = cuda_clamp(val, clamp_min, clamp_max);
      reinterpret_cast<QuantT_packed_t*>(normed_output_quant)[index] =
          cuda_cast<QuantT_packed_t>(cuda_cast<float_packed_t>(val) * scale_orig_quant);
    } else {
      normed_output[index] = val;
    }

    if (with_per_token_sum) {
      local_sum += cuda_sum<float>(cuda_cast<float_packed_t>(val));
    }
  }

  if (with_per_token_scaling) {
    float abs_max_f = blockAllReduceMax(cuda_cast<float>(amax));
    float const dynamic_per_token_scale =
        has_fp8_min_scaling ? fminf(MAX_QUANT_VAL / abs_max_f, MIN_SCALING_FACTOR_RCP)
                            : (MAX_QUANT_VAL / abs_max_f);
    for (int i = tidx; i < n_elems; i += blockDim.x) {
      int const index = bidx * n_elems + i;
      float_packed_t val_f = cuda_cast<float_packed_t>(USE_SHMEM ? shmem[i] : input[index]);
      if constexpr (!USE_SHMEM) {
        val_f = compute_layernorm(val_f, s_mean, s_variance, gemma, beta, i);
      }

      reinterpret_cast<QuantT_packed_t*>(normed_output_quant)[index] =
          cuda_cast<QuantT_packed_t>(val_f * cuda_cast<float_packed_t>(dynamic_per_token_scale));
    }
    if (tidx == 0) {
      scale_orig_quant_per_token[bidx] =
          has_fp8_min_scaling ? cuda_max(abs_max_f / MAX_QUANT_VAL, MIN_SCALING_FACTOR)
                              : abs_max_f / MAX_QUANT_VAL;
    }
  }

  if (with_per_token_sum) {
    float packed_sum[1] = {local_sum};
    blockReduceSumV2<float, 1>(packed_sum);
    if (tidx == 0) {
      sum_per_token[bidx] = packed_sum[0];
    }
  }
}

template <bool USE_DIFF_OF_SQUARES, typename T, typename Tw, typename QuantT>
void dispatch_layernorm_type_square_method(
    T const* input, Tw const* gemma, Tw const* beta, T* normed_output, float const eps, int tokens,
    int hidden_dim, float const* clamp_ptr, float const* scale_orig_quant_per_tensor,
    float* scale_orig_quant_per_token, float* sum_per_token, QuantT* normed_output_quant,
    bool const has_fp8_min_scaling, dim3 const grid, dim3 const block, size_t const shmem_size,
    cudaStream_t stream) {
  // Do we use shared memory to cache intermediate results
  bool use_shmem = true;
  if (shmem_size >= (48 << 10)) {
    cudaError_t ret =
        cudaFuncSetAttribute(generalLayerNorm<T, Tw, QuantT, true, USE_DIFF_OF_SQUARES>,
                             cudaFuncAttributeMaxDynamicSharedMemorySize, shmem_size);
    // Use shared memory when the capacity is enough
    use_shmem = (ret == cudaSuccess);
  }

  if (use_shmem) {
    generalLayerNorm<T, Tw, QuantT, true, USE_DIFF_OF_SQUARES><<<grid, block, shmem_size, stream>>>(
        input, gemma, beta, normed_output, eps, tokens, hidden_dim, clamp_ptr,
        scale_orig_quant_per_tensor, scale_orig_quant_per_token, sum_per_token, normed_output_quant,
        has_fp8_min_scaling);
  } else {
    generalLayerNorm<T, Tw, QuantT, false, USE_DIFF_OF_SQUARES><<<grid, block, 0, stream>>>(
        input, gemma, beta, normed_output, eps, tokens, hidden_dim, clamp_ptr,
        scale_orig_quant_per_tensor, scale_orig_quant_per_token, sum_per_token, normed_output_quant,
        has_fp8_min_scaling);
  }
}

template <typename T, typename Tw, typename QuantT>
void dispatch_layernorm_type(T const* input, Tw const* gemma, Tw const* beta, T* normed_output,
                             float const eps, int tokens, int hidden_dim, float const* clamp_ptr,
                             float const* scale_orig_quant_per_tensor,
                             float* scale_orig_quant_per_token, float* sum_per_token,
                             QuantT* normed_output_quant, bool const has_fp8_min_scaling,
                             dim3 const grid, dim3 const block, size_t const shmem_size,
                             cudaStream_t stream, bool const use_diff_of_squares) {
  if (use_diff_of_squares) {
    dispatch_layernorm_type_square_method<true>(
        input, gemma, beta, normed_output, eps, tokens, hidden_dim, clamp_ptr,
        scale_orig_quant_per_tensor, scale_orig_quant_per_token, sum_per_token, normed_output_quant,
        has_fp8_min_scaling, grid, block, shmem_size, stream);
  } else {
    dispatch_layernorm_type_square_method<false>(
        input, gemma, beta, normed_output, eps, tokens, hidden_dim, clamp_ptr,
        scale_orig_quant_per_tensor, scale_orig_quant_per_token, sum_per_token, normed_output_quant,
        has_fp8_min_scaling, grid, block, shmem_size, stream);
  }
}

template <typename T, typename Tw>
cudaError_t LayerNorm(T* input, Tw* gemma, Tw* beta, T* out, uint32_t tokens, uint32_t hidden_dim,
                      float eps = 1e-5, cudaStream_t stream = 0) {
  dim3 grid(tokens);
  dim3 block(min(hidden_dim, 1024));
  // Make sure block.x is multiple of 32 for warp shuffle to work
  block.x = 32 * ((block.x + 31) / 32);

  constexpr size_t vec_size = 2;
  const size_t shmem_size = hidden_dim * sizeof(T);
  bool const use_vec_type = (hidden_dim % vec_size == 0) &&
                            (std::is_same<T, half>::value || std::is_same<T, __nv_bfloat16>::value);

  // Enable min_scaling factor if it is fp8 row-wise per-token quantization
  bool has_fp8_min_scaling = false;
  float* clamp_ptr = nullptr;
  float* scale = nullptr;
  float* dynamic_scale = nullptr;
  float* sum_per_token = nullptr;
  int8_t* normed_output_quant = nullptr;
  bool use_diff_of_squares = false;

  if (use_vec_type) {
    using Tp = typename packed_as<T, vec_size>::type;
    using Twp = typename packed_as<Tw, vec_size>::type;
    dispatch_layernorm_type(reinterpret_cast<Tp const*>(input), reinterpret_cast<Twp const*>(gemma),
                            reinterpret_cast<Twp const*>(beta), reinterpret_cast<Tp*>(out), eps,
                            tokens, hidden_dim, clamp_ptr, scale, dynamic_scale, sum_per_token,
                            normed_output_quant, has_fp8_min_scaling, grid, block, shmem_size,
                            stream, use_diff_of_squares);
  } else {
    dispatch_layernorm_type(input, gemma, beta, out, eps, tokens, hidden_dim, clamp_ptr, scale,
                            dynamic_scale, sum_per_token, normed_output_quant, has_fp8_min_scaling,
                            grid, block, shmem_size, stream, use_diff_of_squares);
  }
  return cudaSuccess;
}

#ifdef ENABLE_FP8
template <typename T, typename Tw, typename QuantT>
cudaError_t LayerNormQuant(T* input, Tw* gemma, Tw* beta, QuantT* out_quant, float* scale,
                           uint32_t tokens, uint32_t hidden_dim, float eps = 1e-5,
                           cudaStream_t stream = 0) {
  dim3 grid(tokens);
  dim3 block(min(hidden_dim, 1024));
  // Make sure block.x is multiple of 32 for warp shuffle to work
  block.x = 32 * ((block.x + 31) / 32);

  constexpr size_t vec_size = 2;
  const size_t shmem_size = hidden_dim * sizeof(T);
  bool const use_vec_type = (hidden_dim % vec_size == 0) &&
                            (std::is_same<T, half>::value || std::is_same<T, __nv_bfloat16>::value);

  // Per-tensor scaling path: the non-quantized output is never written and the
  // fp8 cast saturates, so no extra clamp is needed.
  T* normed_output = nullptr;
  bool has_fp8_min_scaling = false;
  float* clamp_ptr = nullptr;
  float* dynamic_scale = nullptr;
  float* sum_per_token = nullptr;
  bool use_diff_of_squares = false;

  if (use_vec_type) {
    using Tp = typename packed_as<T, vec_size>::type;
    using Twp = typename packed_as<Tw, vec_size>::type;
    // QuantT stays scalar here; the kernel re-packs it via
    // packed_as<QuantT, num_elems<Tp>::value> to match the vectorized T.
    dispatch_layernorm_type(
        reinterpret_cast<Tp const*>(input), reinterpret_cast<Twp const*>(gemma),
        reinterpret_cast<Twp const*>(beta), reinterpret_cast<Tp*>(normed_output), eps, tokens,
        hidden_dim, clamp_ptr, scale, dynamic_scale, sum_per_token, out_quant, has_fp8_min_scaling,
        grid, block, shmem_size, stream, use_diff_of_squares);
  } else {
    dispatch_layernorm_type(input, gemma, beta, normed_output, eps, tokens, hidden_dim, clamp_ptr,
                            scale, dynamic_scale, sum_per_token, out_quant, has_fp8_min_scaling,
                            grid, block, shmem_size, stream, use_diff_of_squares);
  }
  return cudaGetLastError();
}
#endif  // ENABLE_FP8

}  // namespace norm

}  // namespace flashinfer

#endif  // FLASHINFER_NORM_CUH_
