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
#ifndef FLASHINFER_SAMPLING_CUH_
#define FLASHINFER_SAMPLING_CUH_

#include <cuda.h>
#include <curand.h>
#include <curand_kernel.h>
#include <curand_philox4x32_x.h>

#include <cstdlib>
#include <cstring>
#include <cub/cub.cuh>
#include <cuda/functional>
#include <cuda/std/functional>
#include <cuda/std/limits>
#include <limits>
#include <numeric>
#include <tuple>

#include "allocator.h"
#include "math.cuh"
#include "topk.cuh"
#include "utils.cuh"
#include "vec_dtypes.cuh"

using MaxReduceOp = cuda::maximum<>;
using MinReduceOp = cuda::minimum<>;

namespace flashinfer {

namespace sampling {

using namespace cub;

// IEEE-754 compliant arithmetic via inline PTX (no .ftz modifier).
// Under -use_fast_math, the compiler emits .ftz variants that flush subnormal
// results to zero.  These helpers bypass that for operations where subnormal
// correctness matters (see #769, #774).
__device__ __forceinline__ float ieee_mul(float a, float b) {
  float r;
  asm("mul.rn.f32 %0, %1, %2;" : "=f"(r) : "f"(a), "f"(b));
  return r;
}
__device__ __forceinline__ float ieee_add(float a, float b) {
  float r;
  asm("add.rn.f32 %0, %1, %2;" : "=f"(r) : "f"(a), "f"(b));
  return r;
}
__device__ __forceinline__ float ieee_div(float a, float b) {
  float r;
  asm("div.rn.f32 %0, %1, %2;" : "=f"(r) : "f"(a), "f"(b));
  return r;
}

#define DISPATCH_DETERMINISTIC(deterministic, DETERMINISTIC, ...) \
  if (deterministic) {                                            \
    constexpr bool DETERMINISTIC = true;                          \
    __VA_ARGS__                                                   \
  } else {                                                        \
    constexpr bool DETERMINISTIC = false;                         \
    __VA_ARGS__                                                   \
  }

#define DISPATCH_COMPUTE_CAP_NUM_THREADS(compute_capacity, BLOCK_THREADS, ...) \
  if (compute_capacity.first >= 8) {                                           \
    constexpr uint32_t BLOCK_THREADS = 1024;                                   \
    __VA_ARGS__                                                                \
  } else {                                                                     \
    constexpr uint32_t BLOCK_THREADS = 512;                                    \
    __VA_ARGS__                                                                \
  }

#define DISPATCH_SOFTMAX_CACHE_INPUT(cache_input, CACHE_INPUT, ...) \
  if (cache_input) {                                                \
    constexpr bool CACHE_INPUT = true;                              \
    __VA_ARGS__                                                     \
  } else {                                                          \
    constexpr bool CACHE_INPUT = false;                             \
    __VA_ARGS__                                                     \
  }

constexpr BlockScanAlgorithm SCAN_ALGO = BLOCK_SCAN_WARP_SCANS;
constexpr BlockReduceAlgorithm REDUCE_ALGO = BLOCK_REDUCE_WARP_REDUCTIONS;

// On SM107 (Rubin), ptxas can allocate >64 regs/thread for these 1024-thread
// sampling kernels, which exceeds the 65536-register SM budget and fails with
// "too many resources requested for launch" (internal MR !611 / feat_sm107).
// Gate __launch_bounds__ to native sm_107* compiles only so other arches keep
// unconstrained register allocation (avoids the B300/H100 spill regression in
// NVBug 6517769). When SM107 is mapped to sm_100f at JIT time, this gate is
// inactive; that path inherits sm_100 register counts which already fit.
#if defined(__CUDA_ARCH__) && (__CUDA_ARCH__ >= 1070)
#define FLASHINFER_SAMPLING_LAUNCH_BOUNDS(block_threads) __launch_bounds__(block_threads)
#else
#define FLASHINFER_SAMPLING_LAUNCH_BOUNDS(block_threads)
#endif

#if (__CUDACC_VER_MAJOR__ * 10000 + __CUDACC_VER_MINOR__ * 100 >= 120100)
#define FLASHINFER_CUB_SUBTRACTLEFT_DEFINED
#endif

template <typename T>
struct ValueCount {
  T value;
  int count;

  __device__ ValueCount operator+(const ValueCount& other) const {
    return {value + other.value, count + other.count};
  }
  __device__ ValueCount& operator+=(const ValueCount& other) {
    value += other.value;
    count += other.count;
    return *this;
  }
};

struct BoolDiffOp {
  __device__ __forceinline__ bool operator()(const bool& lhs, const bool& rhs) const {
    return lhs != rhs;
  }
};

struct Float2SoftmaxReduceOp {
  __device__ __forceinline__ float2 operator()(const float2& a, const float2& b) const {
    if (isinf(a.x)) return b;
    if (isinf(b.x)) return a;

    float new_max = max(a.x, b.x);
    float new_denom = a.y * __expf(a.x - new_max) + b.y * __expf(b.x - new_max);
    return make_float2(new_max, new_denom);
  }
};

template <uint32_t BLOCK_THREADS, BlockScanAlgorithm SCAN_ALGORITHM,
          BlockReduceAlgorithm REDUCE_ALGORITHM>
struct SamplingTempStorage {
  union {
    float deterministic_scan[BLOCK_THREADS / 32];
    typename BlockScan<float, BLOCK_THREADS, SCAN_ALGORITHM>::TempStorage scan;
    typename BlockReduce<float, BLOCK_THREADS, REDUCE_ALGORITHM>::TempStorage reduce;
    typename BlockReduce<int, BLOCK_THREADS, REDUCE_ALGORITHM>::TempStorage reduce_int;
    typename BlockReduce<ValueCount<float>, BLOCK_THREADS, REDUCE_ALGORITHM>::TempStorage
        reduce_value_count;
    typename BlockAdjacentDifference<bool, BLOCK_THREADS>::TempStorage adj_diff;
  } block_prim;
  struct {
    int32_t sampled_id;
    int32_t last_valid_id;
    float max_val;
    union {
      float value;
      ValueCount<float> pair;
    } block_aggregate;
  };
};

template <uint32_t BLOCK_THREADS>
struct OnlineSoftmaxTempStorage {
  union {
    typename cub::BlockReduce<float, BLOCK_THREADS>::TempStorage reduce;
    typename cub::BlockReduce<float2, BLOCK_THREADS>::TempStorage reduce_pair;
  } block_prim;

  struct {
    float max_val;
    float denominator;
  } shared_state;
};

struct PartialSoftmaxResult {
  float max_val;
  float denominator;
};

/*!
 * \brief Deterministic inclusive scan implementation, use Belloch scan algorithm.
 * \note This implementation is slower than the cub::BlockScan, but it is deterministic.
 */
template <uint32_t VEC_SIZE, uint32_t BLOCK_THREADS, BlockScanAlgorithm SCAN_ALGORITHM,
          BlockReduceAlgorithm REDUCE_ALGORITHM>
__device__ __forceinline__ void DeterministicInclusiveSum(
    const float* in_data, float* out_data,
    SamplingTempStorage<BLOCK_THREADS, SCAN_ALGORITHM, REDUCE_ALGORITHM>* temp_storage) {
  float* smem_prefix_sum = temp_storage->block_prim.deterministic_scan;
  float thread_data[VEC_SIZE];
  float thread_sum = 0;
#pragma unroll
  for (uint32_t i = 0; i < VEC_SIZE; ++i) {
    thread_sum += in_data[i];
    thread_data[i] = thread_sum;
  }

  float thread_exclusive_prefix_sum = thread_sum;

#pragma unroll
  for (uint32_t offset = 1; offset < 32; offset *= 2) {
    float tmp = __shfl_up_sync(0xffffffff, thread_exclusive_prefix_sum, offset);
    if ((threadIdx.x + 1) % (offset * 2) == 0) {
      thread_exclusive_prefix_sum += tmp;
    }
  }

  float warp_sum = __shfl_sync(0xffffffff, thread_exclusive_prefix_sum, threadIdx.x | 0xffffffff);
  if (threadIdx.x % 32 == 31) {
    thread_exclusive_prefix_sum = 0;
  }

#pragma unroll
  for (uint32_t offset = 16; offset >= 1; offset /= 2) {
    float tmp = __shfl_xor_sync(0xffffffff, thread_exclusive_prefix_sum, offset);
    if ((threadIdx.x + 1) % (offset * 2) == 0) {
      thread_exclusive_prefix_sum = tmp + thread_exclusive_prefix_sum;
    }
    if ((threadIdx.x + 1) % (offset * 2) == offset) {
      thread_exclusive_prefix_sum = tmp;
    }
  }

  smem_prefix_sum[threadIdx.x / 32] = warp_sum;
  __syncthreads();

  if (threadIdx.x < 32) {
    float warp_exclusive_prefix_sum =
        (threadIdx.x < BLOCK_THREADS / 32) ? smem_prefix_sum[threadIdx.x] : 0;

#pragma unroll
    for (uint32_t offset = 1; offset < 32; offset *= 2) {
      float tmp = __shfl_up_sync(0xffffffff, warp_exclusive_prefix_sum, offset);
      if ((threadIdx.x + 1) % (offset * 2) == 0) {
        warp_exclusive_prefix_sum += tmp;
      }
    }

    if (threadIdx.x % 32 == 31) {
      warp_exclusive_prefix_sum = 0;
    }

#pragma unroll
    for (uint32_t offset = 16; offset >= 1; offset /= 2) {
      float tmp = __shfl_xor_sync(0xffffffff, warp_exclusive_prefix_sum, offset);
      if ((threadIdx.x + 1) % (offset * 2) == 0) {
        warp_exclusive_prefix_sum = tmp + warp_exclusive_prefix_sum;
      }
      if ((threadIdx.x + 1) % (offset * 2) == offset) {
        warp_exclusive_prefix_sum = tmp;
      }
    }
    if (threadIdx.x < BLOCK_THREADS / 32) {
      smem_prefix_sum[threadIdx.x] = warp_exclusive_prefix_sum;
    }
  }
  __syncthreads();

#pragma unroll
  for (uint32_t i = 0; i < VEC_SIZE; ++i) {
    out_data[i] = smem_prefix_sum[threadIdx.x / 32] + thread_exclusive_prefix_sum + thread_data[i];
  }
}

template <uint32_t VEC_SIZE, uint32_t BLOCK_THREADS, BlockReduceAlgorithm REDUCE_ALGORITHM,
          typename TempStorage>
__device__ __forceinline__ float GetMaxValue(float* in_data, uint32_t row_idx, uint32_t d,
                                             TempStorage& temp_storage) {
  const uint32_t tx = threadIdx.x;
  vec_t<float, VEC_SIZE> in_data_vec;

  // Thread-local max accumulation (deferred reduction)
  float thread_max = 0.0f;
  for (uint32_t i = 0; i < ceil_div(d, BLOCK_THREADS * VEC_SIZE); ++i) {
    in_data_vec.fill(0);
    if ((i * BLOCK_THREADS + tx) * VEC_SIZE < d) {
      in_data_vec.cast_load(in_data + row_idx * d + (i * BLOCK_THREADS + tx) * VEC_SIZE);
    }
#pragma unroll
    for (uint32_t j = 0; j < VEC_SIZE; ++j) {
      thread_max = max(thread_max, static_cast<float>(in_data_vec[j]));
    }
  }

  // Single block reduction after loop completes
  float max_val =
      BlockReduce<float, BLOCK_THREADS, REDUCE_ALGORITHM>(temp_storage.block_prim.reduce)
          .Reduce(thread_max, MaxReduceOp{});
  if (tx == 0) {
    temp_storage.max_val = max_val;
  }
  __syncthreads();
  return temp_storage.max_val;
}

template <uint32_t BLOCK_THREADS, uint32_t VEC_SIZE, typename DType, bool CACHE_INPUT>
__global__ FLASHINFER_SAMPLING_LAUNCH_BOUNDS(BLOCK_THREADS) void OnlineSoftmaxFusedKernel(
    DType* logits, DType* output, DType* temperature_arr, DType temperature_val, uint32_t d) {
  const uint32_t bx = blockIdx.x, tx = threadIdx.x;
  float temperature = temperature_arr == nullptr ? temperature_val : temperature_arr[bx];
  const float inv_temp = (temperature == 0.f) ? 0.f : 1.f / temperature;

  using TempStorage = OnlineSoftmaxTempStorage<BLOCK_THREADS>;
  extern __shared__ __align__(alignof(TempStorage)) uint8_t smem[];
  auto& temp_storage = reinterpret_cast<TempStorage&>(smem);

  DType* smem_vec_base = nullptr;
  if constexpr (CACHE_INPUT) {
    constexpr size_t vec_alignment = alignof(vec_t<DType, VEC_SIZE>);
    size_t aligned_offset = round_up(sizeof(TempStorage), vec_alignment);
    smem_vec_base = reinterpret_cast<DType*>(smem + aligned_offset);
  }

  vec_t<DType, VEC_SIZE> logits_vec;

  float running_max = -cuda::std::numeric_limits<float>::infinity();
  float running_denominator = 0.0f;
  float threadlocal_running_denominator = 0.0f;

#if (__CUDACC_VER_MAJOR__ >= 12 && defined(__CUDA_ARCH__) && (__CUDA_ARCH__ >= 900))
  asm volatile("griddepcontrol.wait;");
#endif

  // Pass 1: Compute running max and denominator
#pragma unroll 2
  for (uint32_t i = 0; i < ceil_div(d, BLOCK_THREADS * VEC_SIZE); ++i) {
    logits_vec.fill(-cuda::std::numeric_limits<DType>::infinity());
    if ((i * BLOCK_THREADS + tx) * VEC_SIZE < d) {
      logits_vec.cast_load(logits + bx * d + (i * BLOCK_THREADS + tx) * VEC_SIZE);

#pragma unroll
      for (uint32_t j = 0; j < VEC_SIZE; ++j) {
        logits_vec[j] *= inv_temp;
      }

      if constexpr (CACHE_INPUT) {
        logits_vec.store(smem_vec_base + (i * BLOCK_THREADS + tx) * VEC_SIZE);
      }
    }

    float thread_max = -cuda::std::numeric_limits<float>::infinity();
#pragma unroll
    for (uint32_t j = 0; j < VEC_SIZE; ++j) {
      thread_max = max(thread_max, logits_vec[j]);
    }
    float block_max = cub::BlockReduce<float, BLOCK_THREADS>(temp_storage.block_prim.reduce)
                          .Reduce(thread_max, MaxReduceOp{});

    if (tx == 0) {
      temp_storage.shared_state.max_val = block_max;
    }
    __syncthreads();
    block_max = temp_storage.shared_state.max_val;
    // if block_max is -inf, then this block contains all -inf values, so we can skip updating
    if (!isinf(block_max)) {
      float threadlocal_sum = 0.0f;
#pragma unroll
      for (uint32_t j = 0; j < VEC_SIZE; ++j) {
        threadlocal_sum += __expf(logits_vec[j] - block_max);
      }
      float new_max = max(running_max, block_max);
      threadlocal_running_denominator =
          threadlocal_running_denominator * __expf(running_max - new_max) +
          threadlocal_sum * __expf(block_max - new_max);
      running_max = new_max;
    }
  }

  running_denominator = cub::BlockReduce<float, BLOCK_THREADS>(temp_storage.block_prim.reduce)
                            .Sum(threadlocal_running_denominator);
  if (tx == 0) {
    temp_storage.shared_state.denominator = running_denominator;
  }
  __syncthreads();
  running_denominator = temp_storage.shared_state.denominator;

  const float final_max = running_max;
  const float inv_denominator = 1.0f / running_denominator;

  // Pass 2: Normalize in place
  vec_t<DType, VEC_SIZE> prob_vec;
  for (uint32_t i = 0; i < ceil_div(d, BLOCK_THREADS * VEC_SIZE); ++i) {
    if constexpr (CACHE_INPUT) {
      if ((i * BLOCK_THREADS + tx) * VEC_SIZE < d) {
        logits_vec.load(smem_vec_base + (i * BLOCK_THREADS + tx) * VEC_SIZE);
      }
    } else {
      if ((i * BLOCK_THREADS + tx) * VEC_SIZE < d) {
        logits_vec.cast_load(logits + bx * d + (i * BLOCK_THREADS + tx) * VEC_SIZE);

#pragma unroll
        for (uint32_t j = 0; j < VEC_SIZE; ++j) {
          logits_vec[j] *= inv_temp;
        }
      }
    }

#pragma unroll
    for (uint32_t j = 0; j < VEC_SIZE; ++j) {
      float p = __expf(static_cast<float>(logits_vec[j]) - final_max) * inv_denominator;
      prob_vec[j] = static_cast<DType>(p);
    }

    if ((i * BLOCK_THREADS + tx) * VEC_SIZE < d) {
      prob_vec.cast_store(output + bx * d + (i * BLOCK_THREADS + tx) * VEC_SIZE);
    }
  }
#if (__CUDACC_VER_MAJOR__ >= 12 && defined(__CUDA_ARCH__) && (__CUDA_ARCH__ >= 900))
  asm volatile("griddepcontrol.launch_dependents;");
#endif
}

template <uint32_t BLOCK_THREADS, uint32_t VEC_SIZE, typename DType>
__global__ FLASHINFER_SAMPLING_LAUNCH_BOUNDS(BLOCK_THREADS) void OnlineSoftmaxMapKernel(
    DType* logits, PartialSoftmaxResult* partial_results, DType* temperature_arr,
    float temperature_val, uint32_t d, uint32_t num_slices) {
  const uint32_t bx = blockIdx.x;
  const uint32_t by = blockIdx.y;  // slice index
  const uint32_t tx = threadIdx.x;
  float temperature = temperature_arr == nullptr ? temperature_val : temperature_arr[bx];
  const float inv_temp = (temperature == 0.f) ? 0.f : 1.f / temperature;

  const uint32_t vec_alignment_elems = alignof(vec_t<DType, VEC_SIZE>) / sizeof(DType);
  const uint32_t slice_stride = round_up(ceil_div(d, num_slices), vec_alignment_elems);
  const uint32_t slice_start = by * slice_stride;
  const uint32_t slice_size = min((by + 1) * slice_stride, d) - slice_start;

  if (slice_start >= d) return;

  using TempStorage = OnlineSoftmaxTempStorage<BLOCK_THREADS>;
  extern __shared__ __align__(alignof(TempStorage)) uint8_t smem[];
  auto& temp_storage = reinterpret_cast<TempStorage&>(smem);

  vec_t<DType, VEC_SIZE> logits_vec;
  float running_max = -cuda::std::numeric_limits<float>::infinity();
  float running_denominator = 0.0f;
  float threadlocal_running_denominator = 0.0f;

#if (__CUDACC_VER_MAJOR__ >= 12 && defined(__CUDA_ARCH__) && (__CUDA_ARCH__ >= 900))
  asm volatile("griddepcontrol.wait;");
#endif

#pragma unroll 2
  for (uint32_t i = 0; i < ceil_div(slice_size, BLOCK_THREADS * VEC_SIZE); ++i) {
    logits_vec.fill(-cuda::std::numeric_limits<DType>::infinity());

    if ((i * BLOCK_THREADS + tx) * VEC_SIZE < slice_size) {
      logits_vec.cast_load(logits + bx * d + slice_start + (i * BLOCK_THREADS + tx) * VEC_SIZE);
    }

    float thread_max = -cuda::std::numeric_limits<float>::infinity();
#pragma unroll
    for (uint32_t j = 0; j < VEC_SIZE; ++j) {
      logits_vec[j] *= inv_temp;
      thread_max = max(thread_max, logits_vec[j]);
    }

    float block_max = cub::BlockReduce<float, BLOCK_THREADS>(temp_storage.block_prim.reduce)
                          .Reduce(thread_max, MaxReduceOp{});

    if (tx == 0) {
      temp_storage.shared_state.max_val = block_max;
    }
    __syncthreads();
    block_max = temp_storage.shared_state.max_val;

    // if block_max is -inf, then this block contains all -inf values, so we can skip updating
    if (!isinf(block_max)) {
      float threadlocal_sum = 0.0f;
#pragma unroll
      for (uint32_t j = 0; j < VEC_SIZE; ++j) {
        threadlocal_sum += __expf(logits_vec[j] - block_max);
      }
      float new_max = max(running_max, block_max);
      threadlocal_running_denominator =
          threadlocal_running_denominator * __expf(running_max - new_max) +
          threadlocal_sum * __expf(block_max - new_max);
      running_max = new_max;
    }
  }

  running_denominator = cub::BlockReduce<float, BLOCK_THREADS>(temp_storage.block_prim.reduce)
                            .Sum(threadlocal_running_denominator);
  if (tx == 0) {
    temp_storage.shared_state.denominator = running_denominator;
  }
  __syncthreads();
  running_denominator = temp_storage.shared_state.denominator;

  if (tx == 0) {
    partial_results[bx * num_slices + by] = {running_max, running_denominator};
  }
#if (__CUDACC_VER_MAJOR__ >= 12 && defined(__CUDA_ARCH__) && (__CUDA_ARCH__ >= 900))
  asm volatile("griddepcontrol.launch_dependents;");
#endif
}

template <uint32_t BLOCK_THREADS, uint32_t VEC_SIZE, typename DType>
__global__ FLASHINFER_SAMPLING_LAUNCH_BOUNDS(BLOCK_THREADS) void OnlineSoftmaxReduceKernel(
    DType* logits, DType* output, PartialSoftmaxResult* partial_results, DType* temperature_arr,
    float temperature_val, uint32_t d, uint32_t num_slices) {
  const uint32_t bx = blockIdx.x;
  const uint32_t tx = threadIdx.x;
  float temperature = temperature_arr == nullptr ? temperature_val : temperature_arr[bx];
  const float inv_temp = (temperature == 0.f) ? 0.f : 1.f / temperature;

  // Reduce slice results
  using TempStorage = OnlineSoftmaxTempStorage<BLOCK_THREADS>;
  extern __shared__ __align__(alignof(TempStorage)) uint8_t smem[];
  auto& temp_storage = reinterpret_cast<TempStorage&>(smem);

  const Float2SoftmaxReduceOp reduce_op;

  float2 thread_aggregate = make_float2(-cuda::std::numeric_limits<float>::infinity(), 0.0f);

#if (__CUDACC_VER_MAJOR__ >= 12 && defined(__CUDA_ARCH__) && (__CUDA_ARCH__ >= 900))
  asm volatile("griddepcontrol.wait;");
#endif

  for (uint32_t i = tx; i < num_slices; i += BLOCK_THREADS) {
    PartialSoftmaxResult partial = partial_results[bx * num_slices + i];
    float2 partial_pair = make_float2(partial.max_val, partial.denominator);
    thread_aggregate = reduce_op(thread_aggregate, partial_pair);
  }

  float2 block_result = cub::BlockReduce<float2, BLOCK_THREADS>(temp_storage.block_prim.reduce_pair)
                            .Reduce(thread_aggregate, reduce_op);

  if (tx == 0) {
    temp_storage.shared_state.max_val = block_result.x;
    temp_storage.shared_state.denominator = block_result.y;
  }
  __syncthreads();

  block_result =
      make_float2(temp_storage.shared_state.max_val, temp_storage.shared_state.denominator);

  const float final_max = temp_storage.shared_state.max_val;
  const float inv_denominator = 1.0f / temp_storage.shared_state.denominator;

  // Apply normalization
  vec_t<DType, VEC_SIZE> logits_vec;
  vec_t<DType, VEC_SIZE> prob_vec;

  for (uint32_t i = 0; i < ceil_div(d, BLOCK_THREADS * VEC_SIZE); ++i) {
    logits_vec.fill(-cuda::std::numeric_limits<DType>::infinity());

    if ((i * BLOCK_THREADS + tx) * VEC_SIZE < d) {
      logits_vec.cast_load(logits + bx * d + (i * BLOCK_THREADS + tx) * VEC_SIZE);
    }

#pragma unroll
    for (uint32_t j = 0; j < VEC_SIZE; ++j) {
      logits_vec[j] *= inv_temp;
      float p = __expf(static_cast<float>(logits_vec[j]) - final_max) * inv_denominator;
      prob_vec[j] = static_cast<DType>(p);
    }

    if ((i * BLOCK_THREADS + tx) * VEC_SIZE < d) {
      prob_vec.cast_store(output + bx * d + (i * BLOCK_THREADS + tx) * VEC_SIZE);
    }
  }
#if (__CUDACC_VER_MAJOR__ >= 12 && defined(__CUDA_ARCH__) && (__CUDA_ARCH__ >= 900))
  asm volatile("griddepcontrol.launch_dependents;");
#endif
}

template <uint32_t VEC_SIZE, uint32_t BLOCK_THREADS, BlockScanAlgorithm SCAN_ALGORITHM,
          BlockReduceAlgorithm REDUCE_ALGORITHM, bool DETERMINISTIC, typename Predicate>
__device__ __forceinline__ void DeviceSamplingFromProb(
    uint32_t i, uint32_t d, Predicate pred, float u, vec_t<float, VEC_SIZE> prob_vec,
    float& aggregate,
    SamplingTempStorage<BLOCK_THREADS, SCAN_ALGORITHM, REDUCE_ALGORITHM>* temp_storage) {
  const uint32_t tx = threadIdx.x;
  float prob_greater_than_threshold[VEC_SIZE];
  float inclusive_cdf[VEC_SIZE];
  bool greater_than_u[VEC_SIZE], valid[VEC_SIZE];
#pragma unroll
  for (uint32_t j = 0; j < VEC_SIZE; ++j) {
    prob_greater_than_threshold[j] = pred(prob_vec[j]) ? prob_vec[j] : 0;
    valid[j] = pred(prob_vec[j]) && (i * BLOCK_THREADS + tx) * VEC_SIZE + j < d;
  }
  float aggregate_local =
      BlockReduce<float, BLOCK_THREADS, REDUCE_ALGORITHM>(temp_storage->block_prim.reduce)
          .template Sum<VEC_SIZE>(prob_greater_than_threshold);
  if (tx == 0) {
    temp_storage->block_aggregate.value = aggregate_local;
  }
  __syncthreads();
  aggregate_local = temp_storage->block_aggregate.value;

  if (aggregate + aggregate_local > u) {
    if constexpr (DETERMINISTIC) {
      DeterministicInclusiveSum<VEC_SIZE, BLOCK_THREADS, SCAN_ALGORITHM, REDUCE_ALGORITHM>(
          prob_greater_than_threshold, inclusive_cdf, temp_storage);
    } else {
      BlockScan<float, BLOCK_THREADS, SCAN_ALGORITHM>(temp_storage->block_prim.scan)
          .template InclusiveSum<VEC_SIZE>(prob_greater_than_threshold, inclusive_cdf);

      __syncthreads();
    }

#pragma unroll
    for (uint32_t j = 0; j < VEC_SIZE; ++j) {
      greater_than_u[j] = (inclusive_cdf[j] + aggregate > u) && valid[j];
    }

    bool greater_than_u_diff[VEC_SIZE];
#ifdef FLASHINFER_CUB_SUBTRACTLEFT_DEFINED
    BlockAdjacentDifference<bool, BLOCK_THREADS>(temp_storage->block_prim.adj_diff)
        .SubtractLeft<VEC_SIZE>(greater_than_u, greater_than_u_diff, BoolDiffOp());
#else
    BlockAdjacentDifference<bool, BLOCK_THREADS>(temp_storage->block_prim.adj_diff)
        .template FlagHeads<VEC_SIZE>(greater_than_u_diff, greater_than_u, BoolDiffOp(), 0);
#endif
    __syncthreads();

#pragma unroll
    for (uint32_t j = 0; j < VEC_SIZE; ++j) {
      if (greater_than_u_diff[j]) {
        atomicMin(&(temp_storage->sampled_id), (i * BLOCK_THREADS + tx) * VEC_SIZE + j);
      }
    }
    __syncthreads();
  }

  // update the last valid index
  int valid_index[VEC_SIZE];
#pragma unroll
  for (uint32_t j = 0; j < VEC_SIZE; ++j) {
    if (valid[j]) {
      valid_index[j] = (i * BLOCK_THREADS + tx) * VEC_SIZE + j;
    } else {
      valid_index[j] = -1;
    }
  }
  int max_valid_index =
      BlockReduce<int, BLOCK_THREADS, REDUCE_ALGORITHM>(temp_storage->block_prim.reduce_int)
          .Reduce(valid_index, MaxReduceOp{});
  if (tx == 0 && max_valid_index != -1 && max_valid_index < (int)d) {
    temp_storage->last_valid_id = max_valid_index;
  }
  __syncthreads();
  aggregate += aggregate_local;
}

template <typename DType, typename IdType>
struct DataAndIndex {
  DType data;
  IdType index;

  __device__ DataAndIndex operator+(const DataAndIndex& other) const {
    if (data > other.data) {
      return {data, index};
    } else {
      return {other.data, other.index};
    }
  }
  __device__ DataAndIndex& operator+=(const DataAndIndex& other) {
    if (data > other.data) {
      return *this;
    } else {
      data = other.data;
      index = other.index;
      return *this;
    }
  }
};

template <typename DType, uint32_t VEC_SIZE>
__device__ __forceinline__ vec_t<DType, VEC_SIZE> GenerateGumbelNoise(uint64_t philox_seed,
                                                                      uint64_t philox_offset,
                                                                      uint64_t subsequence) {
  curandStatePhilox4_32_10_t state;
  vec_t<float, VEC_SIZE> noise;
  constexpr float kSCALE = 1.0f - cuda::std::numeric_limits<float>::epsilon();
  constexpr float kLOG2 = 0.6931471806f;
  auto uniform2gumbel = [](float x) {
    // NB: cuRAND returns strictly positive normal floating point numbers, up to
    //     and including 1.0. kSCALE is used to exclude 1.0, s.t.
    //         1.18e-38 <= x * kSCALE <= 1.0f - epsilon
    //      => -4.47    <= -log(-log(...))       <= 15.9
    // log2f maps to a single PTX LG2 instruction on NVIDIA GPUs, while logf
    // internally computes log2f * ln(2). Using log2f with a single kLOG2
    // multiplication is more efficient (3 ops vs 4 ops).
    return -kLOG2 * log2f(-log2f((x * kSCALE)));
  };
#pragma unroll
  for (uint32_t i = 0; i + 4 <= VEC_SIZE; i += 4) {
    curand_init(philox_seed, subsequence + i, philox_offset, &state);
    float4 noise_vec = curand_uniform4(&state);
    noise[i] = uniform2gumbel(noise_vec.x);
    noise[i + 1] = uniform2gumbel(noise_vec.y);
    noise[i + 2] = uniform2gumbel(noise_vec.z);
    noise[i + 3] = uniform2gumbel(noise_vec.w);
  }
  if constexpr (VEC_SIZE % 4 != 0) {
    curand_init(philox_seed, subsequence + VEC_SIZE / 4 * 4, philox_offset, &state);
    float4 noise_vec = curand_uniform4(&state);
    if constexpr (VEC_SIZE % 4 == 1) {
      noise[VEC_SIZE - 1] = uniform2gumbel(noise_vec.x);
    } else if constexpr (VEC_SIZE % 4 == 2) {
      noise[VEC_SIZE - 2] = uniform2gumbel(noise_vec.x);
      noise[VEC_SIZE - 1] = uniform2gumbel(noise_vec.y);
    } else if constexpr (VEC_SIZE % 4 == 3) {
      noise[VEC_SIZE - 3] = uniform2gumbel(noise_vec.x);
      noise[VEC_SIZE - 2] = uniform2gumbel(noise_vec.y);
      noise[VEC_SIZE - 1] = uniform2gumbel(noise_vec.z);
    }
  }

  if constexpr (std::is_same_v<DType, float>) {
    return noise;
  } else {
    vec_t<DType, VEC_SIZE> ret;
#pragma unroll
    for (uint32_t i = 0; i < VEC_SIZE; ++i) {
      ret[i] = static_cast<DType>(noise[i]);
    }
    return ret;
  }
}

template <uint32_t BLOCK_THREADS, BlockScanAlgorithm SCAN_ALGORITHM,
          BlockReduceAlgorithm REDUCE_ALGORITHM, uint32_t VEC_SIZE, bool DETERMINISTIC,
          typename DType, typename IdType>
__global__ FLASHINFER_SAMPLING_LAUNCH_BOUNDS(BLOCK_THREADS) void SamplingFromLogitsKernel(
    DType* logits, IdType* output, IdType* indices, uint32_t d, uint64_t* seed_arr,
    uint64_t seed_val, uint64_t* offset_arr, uint64_t offset_val) {
  const uint32_t bx = blockIdx.x, tx = threadIdx.x;

  // Resolve seed/offset from tensor or scalar
  uint64_t philox_seed = seed_arr ? seed_arr[0] : seed_val;
  uint64_t philox_offset = offset_arr ? offset_arr[0] : offset_val;

  const uint32_t row_idx = indices == nullptr ? bx : indices[bx];
  using SharedMem = typename BlockReduce<DataAndIndex<DType, IdType>, BLOCK_THREADS,
                                         REDUCE_ALGORITHM>::TempStorage;
  extern __shared__ __align__(alignof(SharedMem)) uint8_t smem_sampling_logit[];
  auto& temp_storage = reinterpret_cast<SharedMem&>(smem_sampling_logit);

  vec_t<DType, VEC_SIZE> logits_vec;
  DataAndIndex<DType, IdType> max_data = {-cuda::std::numeric_limits<DType>::infinity(), 0};
  for (uint32_t i = 0; i < ceil_div(d, BLOCK_THREADS * VEC_SIZE); ++i) {
    logits_vec.fill(-cuda::std::numeric_limits<DType>::infinity());
    if ((i * BLOCK_THREADS + tx) * VEC_SIZE < d) {
      logits_vec.cast_load(logits + row_idx * d + i * BLOCK_THREADS * VEC_SIZE + tx * VEC_SIZE);
    }

    vec_t<DType, VEC_SIZE> gumbel_noise = GenerateGumbelNoise<DType, VEC_SIZE>(
        philox_seed, philox_offset,
        static_cast<uint64_t>(bx * d + (i * BLOCK_THREADS + tx) * VEC_SIZE));
    DataAndIndex<DType, IdType> cur_data[VEC_SIZE];
#pragma unroll
    for (uint32_t j = 0; j < VEC_SIZE; ++j) {
      const uint32_t token_idx = (i * BLOCK_THREADS + tx) * VEC_SIZE + j;
      const bool valid = token_idx < d;
      cur_data[j].data =
          valid ? logits_vec[j] + gumbel_noise[j] : -cuda::std::numeric_limits<DType>::infinity();
      cur_data[j].index = valid ? token_idx : 0;
    }

    max_data +=
        BlockReduce<DataAndIndex<DType, IdType>, BLOCK_THREADS, REDUCE_ALGORITHM>(temp_storage)
            .template Sum<VEC_SIZE>(cur_data);
    __syncthreads();
  }
  if (tx == 0) {
    output[bx] = max_data.index;
  }
}

template <uint32_t BLOCK_THREADS, BlockScanAlgorithm SCAN_ALGORITHM,
          BlockReduceAlgorithm REDUCE_ALGORITHM, uint32_t VEC_SIZE, bool DETERMINISTIC,
          typename DType, typename IdType>
__global__ FLASHINFER_SAMPLING_LAUNCH_BOUNDS(BLOCK_THREADS) void SamplingFromProbKernel(
    DType* probs, IdType* output, bool* valid, IdType* indices, uint32_t d, uint64_t* seed_arr,
    uint64_t seed_val, uint64_t* offset_arr, uint64_t offset_val) {
  curandStatePhilox4_32_10_t state;
  const uint32_t bx = blockIdx.x, tx = threadIdx.x;

  // Resolve seed/offset from tensor or scalar
  uint64_t philox_seed = seed_arr ? seed_arr[0] : seed_val;
  uint64_t philox_offset = offset_arr ? offset_arr[0] : offset_val;

  curand_init(philox_seed, bx, philox_offset, &state);
  const uint32_t row_idx = indices == nullptr ? bx : indices[bx];

  extern __shared__ __align__(
      alignof(SamplingTempStorage<BLOCK_THREADS, SCAN_ALGORITHM, REDUCE_ALGORITHM>))
      uint8_t smem_sampling[];
  auto& temp_storage =
      reinterpret_cast<SamplingTempStorage<BLOCK_THREADS, SCAN_ALGORITHM, REDUCE_ALGORITHM>&>(
          smem_sampling);
  temp_storage.sampled_id = d;
  temp_storage.last_valid_id = -1;
  __syncthreads();

  vec_t<float, VEC_SIZE> probs_vec;
  float aggregate(0);
  float u = curand_uniform(&state);

#pragma unroll 2
  for (uint32_t i = 0; i < ceil_div(d, BLOCK_THREADS * VEC_SIZE); ++i) {
    probs_vec.fill(0);
    if ((i * BLOCK_THREADS + tx) * VEC_SIZE < d) {
      probs_vec.cast_load(probs + row_idx * d + i * BLOCK_THREADS * VEC_SIZE + tx * VEC_SIZE);
    }

    DeviceSamplingFromProb<VEC_SIZE, BLOCK_THREADS, SCAN_ALGORITHM, REDUCE_ALGORITHM,
                           DETERMINISTIC>(
        i, d, [](float x) { return x > 0; }, u, probs_vec, aggregate, &temp_storage);
    if (float(aggregate) > u) {
      break;
    }
  }
  int sampled_id = temp_storage.sampled_id;
  if (sampled_id == d) {
    // NOTE(Zihao): this would happen when u is very close to 1
    // and the sum of probabilities is smaller than u
    // In this case, we use the last valid index as the sampled id
    if (temp_storage.last_valid_id == -1) {
      if (tx == 0) {
        output[bx] = 0;
        valid[bx] = false;
      }
      return;
    }
    sampled_id = temp_storage.last_valid_id;
  }
  if (tx == 0) {
    output[bx] = sampled_id;
    valid[bx] = true;
  }
}

template <uint32_t BLOCK_THREADS, BlockScanAlgorithm SCAN_ALGORITHM,
          BlockReduceAlgorithm REDUCE_ALGORITHM, uint32_t VEC_SIZE, bool DETERMINISTIC,
          typename DType, typename IdType>
__global__ FLASHINFER_SAMPLING_LAUNCH_BOUNDS(BLOCK_THREADS) void TopKSamplingFromProbKernel(
    DType* probs, IdType* output, bool* valid, IdType* indices, IdType* top_k_arr,
    uint32_t top_k_val, uint32_t d, uint64_t* seed_arr, uint64_t seed_val, uint64_t* offset_arr,
    uint64_t offset_val) {
  const uint32_t batch_size = gridDim.x;
  const uint32_t bx = blockIdx.x, tx = threadIdx.x;

  // Resolve seed/offset from tensor or scalar
  uint64_t philox_seed = seed_arr ? seed_arr[0] : seed_val;
  uint64_t philox_offset = offset_arr ? offset_arr[0] : offset_val;

  curandStatePhilox4_32_10_t state;
  curand_init(philox_seed, bx, philox_offset, &state);
  const uint32_t k = top_k_arr == nullptr ? top_k_val : top_k_arr[bx];
  const uint32_t row_idx = indices == nullptr ? bx : indices[bx];

  extern __shared__ __align__(
      alignof(SamplingTempStorage<BLOCK_THREADS, SCAN_ALGORITHM, REDUCE_ALGORITHM>))
      uint8_t smem_sampling[];
  auto& temp_storage =
      reinterpret_cast<SamplingTempStorage<BLOCK_THREADS, SCAN_ALGORITHM, REDUCE_ALGORITHM>&>(
          smem_sampling);

  vec_t<float, VEC_SIZE> probs_vec;
  float aggregate;
  float q = 1;
  float low = 0, high = 1.f;
  int sampled_id;
  int round = 0;
  do {
    round += 1;
    temp_storage.sampled_id = d;
    temp_storage.last_valid_id = -1;
    __syncthreads();
    float u = curand_uniform(&state) * q;
    aggregate = 0;
#pragma unroll 2
    for (uint32_t i = 0; i < ceil_div(d, BLOCK_THREADS * VEC_SIZE); ++i) {
      probs_vec.fill(0);
      if ((i * BLOCK_THREADS + tx) * VEC_SIZE < d) {
        probs_vec.cast_load(probs + row_idx * d + (i * BLOCK_THREADS + tx) * VEC_SIZE);
      }

      DeviceSamplingFromProb<VEC_SIZE, BLOCK_THREADS, SCAN_ALGORITHM, REDUCE_ALGORITHM,
                             DETERMINISTIC>(
          i, d, [&](float x) { return x > low; }, u, probs_vec, aggregate, &temp_storage);
      if (aggregate > u) {
        break;
      }
    }
    __syncthreads();
    sampled_id = temp_storage.sampled_id;
    if (sampled_id == d) {
      // NOTE(Zihao): this would happen when u is very close to 1
      // and the sum of probabilities is smaller than u
      // In this case, we use the last valid index as the sampled id
      if (temp_storage.last_valid_id == -1) {
        if (tx == 0) {
          output[bx] = 0;
          valid[bx] = false;
        }
        return;
      }
      sampled_id = temp_storage.last_valid_id;
    }
    // IEEE-754 arithmetic (no FTZ) for pivot computation.  See #769 / #774.
    float pivot_0 = probs[row_idx * d + sampled_id];
    float pivot_1 = ieee_mul(ieee_add(pivot_0, high), 0.5f);

    ValueCount<float> aggregate_gt_pivot_0{0, 0}, aggregate_gt_pivot_1{0, 0};
    ValueCount<float> threadlocal_gt_pivot_0{0, 0}, threadlocal_gt_pivot_1{0, 0};
#pragma unroll 2
    for (uint32_t i = 0; i < ceil_div(d, BLOCK_THREADS * VEC_SIZE); ++i) {
      probs_vec.fill(0);
      if ((i * BLOCK_THREADS + tx) * VEC_SIZE < d) {
        probs_vec.cast_load(probs + row_idx * d + (i * BLOCK_THREADS + tx) * VEC_SIZE);
      }

      ValueCount<float> probs_gt_pivot_0[VEC_SIZE], probs_gt_pivot_1[VEC_SIZE];
#pragma unroll
      for (uint32_t j = 0; j < VEC_SIZE; ++j) {
        probs_gt_pivot_0[j] = {
            (probs_vec[j] > pivot_0) ? probs_vec[j] : 0,
            (probs_vec[j] > pivot_0 && (i * BLOCK_THREADS + tx) * VEC_SIZE + j < d)};
        probs_gt_pivot_1[j] = {
            (probs_vec[j] > pivot_1) ? probs_vec[j] : 0,
            (probs_vec[j] > pivot_1 && (i * BLOCK_THREADS + tx) * VEC_SIZE + j < d)};
        threadlocal_gt_pivot_0 += probs_gt_pivot_0[j];
        threadlocal_gt_pivot_1 += probs_gt_pivot_1[j];
      }
    }
    aggregate_gt_pivot_0 += BlockReduce<ValueCount<float>, BLOCK_THREADS, REDUCE_ALGORITHM>(
                                temp_storage.block_prim.reduce_value_count)
                                .Sum(threadlocal_gt_pivot_0);
    if (tx == 0) {
      temp_storage.block_aggregate.pair = aggregate_gt_pivot_0;
    }
    __syncthreads();
    aggregate_gt_pivot_0 = temp_storage.block_aggregate.pair;

    aggregate_gt_pivot_1 += BlockReduce<ValueCount<float>, BLOCK_THREADS, REDUCE_ALGORITHM>(
                                temp_storage.block_prim.reduce_value_count)
                                .Sum(threadlocal_gt_pivot_1);
    if (tx == 0) {
      temp_storage.block_aggregate.pair = aggregate_gt_pivot_1;
    }
    __syncthreads();
    aggregate_gt_pivot_1 = temp_storage.block_aggregate.pair;
    if (aggregate_gt_pivot_0.count < k) {
      // case 1: pivot_0 accepted
      break;
    }
    if (aggregate_gt_pivot_1.count < k) {
      // case 2: pivot_0 rejected, pivot_1 accepted
      low = pivot_0;
      high = pivot_1;
      q = aggregate_gt_pivot_0.value;
    } else {
      // case 3: pivot_0 rejected, pivot_1 rejected
      low = pivot_1;
      q = aggregate_gt_pivot_1.value;
    }
  } while (low < high);
  __syncthreads();
  if (tx == 0) {
    output[bx] = sampled_id;
    valid[bx] = true;
  }
}

template <uint32_t BLOCK_THREADS, BlockScanAlgorithm SCAN_ALGORITHM,
          BlockReduceAlgorithm REDUCE_ALGORITHM, uint32_t VEC_SIZE, bool DETERMINISTIC,
          typename DType, typename IdType>
__global__ FLASHINFER_SAMPLING_LAUNCH_BOUNDS(BLOCK_THREADS) void TopPSamplingFromProbKernel(
    DType* probs, IdType* output, bool* valid, IdType* indices, float* top_p_arr, float top_p_val,
    uint32_t d, uint64_t* seed_arr, uint64_t seed_val, uint64_t* offset_arr, uint64_t offset_val) {
  const uint32_t batch_size = gridDim.x;
  const uint32_t bx = blockIdx.x, tx = threadIdx.x;

  // Resolve seed/offset from tensor or scalar
  uint64_t philox_seed = seed_arr ? seed_arr[0] : seed_val;
  uint64_t philox_offset = offset_arr ? offset_arr[0] : offset_val;

  curandStatePhilox4_32_10_t state;
  curand_init(philox_seed, bx, philox_offset, &state);
  const uint32_t row_idx = indices == nullptr ? bx : indices[bx];
  float top_p = (top_p_arr == nullptr) ? top_p_val : top_p_arr[row_idx];

  extern __shared__ __align__(
      alignof(SamplingTempStorage<BLOCK_THREADS, SCAN_ALGORITHM, REDUCE_ALGORITHM>))
      uint8_t smem_sampling[];
  auto& temp_storage =
      reinterpret_cast<SamplingTempStorage<BLOCK_THREADS, SCAN_ALGORITHM, REDUCE_ALGORITHM>&>(
          smem_sampling);

  vec_t<float, VEC_SIZE> probs_vec;
  float aggregate;
  float q = 1;
  float low = 0, high = 1.f;
  int sampled_id;
  do {
    temp_storage.sampled_id = d;
    temp_storage.last_valid_id = -1;
    __syncthreads();
    float u = curand_uniform(&state) * q;
    aggregate = 0;
#pragma unroll 2
    for (uint32_t i = 0; i < ceil_div(d, BLOCK_THREADS * VEC_SIZE); ++i) {
      probs_vec.fill(0);
      if ((i * BLOCK_THREADS + tx) * VEC_SIZE < d) {
        probs_vec.cast_load(probs + row_idx * d + (i * BLOCK_THREADS + tx) * VEC_SIZE);
      }

      DeviceSamplingFromProb<VEC_SIZE, BLOCK_THREADS, SCAN_ALGORITHM, REDUCE_ALGORITHM,
                             DETERMINISTIC>(
          i, d, [&](float x) { return x > low; }, u, probs_vec, aggregate, &temp_storage);
      if (aggregate > u) {
        break;
      }
    }
    __syncthreads();
    sampled_id = temp_storage.sampled_id;
    if (sampled_id == d) {
      // NOTE(Zihao): this would happen when u is very close to 1
      // and the sum of probabilities is smaller than u
      // In this case, we use the last valid index as the sampled id
      if (temp_storage.last_valid_id == -1) {
        if (tx == 0) {
          output[bx] = 0;
          valid[bx] = false;
        }
        return;
      }
      sampled_id = temp_storage.last_valid_id;
    }
    // IEEE-754 arithmetic (no FTZ) for pivot computation.  See #769 / #774.
    float pivot_0 = probs[row_idx * d + sampled_id];
    float pivot_1 = ieee_mul(ieee_add(pivot_0, high), 0.5f);

    float aggregate_gt_pivot_0 = 0, aggregate_gt_pivot_1 = 0;
    float threadlocal_aggregate_gt_pivot_0 = 0;
    float threadlocal_aggregate_gt_pivot_1 = 0;
#pragma unroll 2
    for (uint32_t i = 0; i < ceil_div(d, BLOCK_THREADS * VEC_SIZE); ++i) {
      probs_vec.fill(0);
      if ((i * BLOCK_THREADS + tx) * VEC_SIZE < d) {
        probs_vec.cast_load(probs + row_idx * d + (i * BLOCK_THREADS + tx) * VEC_SIZE);
      }

      float probs_gt_pivot_0[VEC_SIZE], probs_gt_pivot_1[VEC_SIZE];
#pragma unroll
      for (uint32_t j = 0; j < VEC_SIZE; ++j) {
        probs_gt_pivot_0[j] = (probs_vec[j] > pivot_0) ? probs_vec[j] : 0;
        probs_gt_pivot_1[j] = (probs_vec[j] > pivot_1) ? probs_vec[j] : 0;
        threadlocal_aggregate_gt_pivot_0 += probs_gt_pivot_0[j];
        threadlocal_aggregate_gt_pivot_1 += probs_gt_pivot_1[j];
      }
    }
    aggregate_gt_pivot_0 += BlockReduce<float, BLOCK_THREADS>(temp_storage.block_prim.reduce)
                                .Sum(threadlocal_aggregate_gt_pivot_0);
    if (tx == 0) {
      temp_storage.block_aggregate.value = aggregate_gt_pivot_0;
    }
    __syncthreads();
    aggregate_gt_pivot_0 = temp_storage.block_aggregate.value;

    aggregate_gt_pivot_1 += BlockReduce<float, BLOCK_THREADS>(temp_storage.block_prim.reduce)
                                .Sum(threadlocal_aggregate_gt_pivot_1);
    if (tx == 0) {
      temp_storage.block_aggregate.value = aggregate_gt_pivot_1;
    }
    __syncthreads();
    aggregate_gt_pivot_1 = temp_storage.block_aggregate.value;

    if (aggregate_gt_pivot_0 < top_p) {
      // case 1: pivot_0 accepted
      break;
    }
    if (aggregate_gt_pivot_1 < top_p) {
      // case 2: pivot_0 rejected, pivot_1 accepted
      low = pivot_0;
      high = pivot_1;
      q = aggregate_gt_pivot_0;
    } else {
      // case 3: pivot_0 rejected, pivot_1 rejected
      low = pivot_1;
      q = aggregate_gt_pivot_1;
    }
  } while (low < high);
  __syncthreads();
  if (tx == 0) {
    output[bx] = sampled_id;
    valid[bx] = true;
  }
}

template <uint32_t BLOCK_THREADS, BlockScanAlgorithm SCAN_ALGORITHM,
          BlockReduceAlgorithm REDUCE_ALGORITHM, uint32_t VEC_SIZE, bool DETERMINISTIC,
          typename DType, typename IdType>
__global__ FLASHINFER_SAMPLING_LAUNCH_BOUNDS(BLOCK_THREADS) void MinPSamplingFromProbKernel(
    DType* probs, float* min_p_arr, IdType* output, bool* valid, IdType* indices, float min_p_val,
    uint32_t d, uint64_t* seed_arr, uint64_t seed_val, uint64_t* offset_arr, uint64_t offset_val) {
  const uint32_t bx = blockIdx.x, tx = threadIdx.x;

  // Resolve seed/offset from tensor or scalar
  uint64_t philox_seed = seed_arr ? seed_arr[0] : seed_val;
  uint64_t philox_offset = offset_arr ? offset_arr[0] : offset_val;

  float p = (min_p_arr == nullptr) ? min_p_val : min_p_arr[bx];
  curandStatePhilox4_32_10_t state;
  curand_init(philox_seed, bx, philox_offset, &state);
  const uint32_t row_idx = indices == nullptr ? bx : indices[bx];

  extern __shared__ __align__(
      alignof(SamplingTempStorage<BLOCK_THREADS, SCAN_ALGORITHM, REDUCE_ALGORITHM>))
      uint8_t smem_sampling[];
  auto& temp_storage =
      reinterpret_cast<SamplingTempStorage<BLOCK_THREADS, SCAN_ALGORITHM, REDUCE_ALGORITHM>&>(
          smem_sampling);

  float max_val = GetMaxValue<VEC_SIZE, BLOCK_THREADS, REDUCE_ALGORITHM,
                              SamplingTempStorage<BLOCK_THREADS, SCAN_ALGORITHM, REDUCE_ALGORITHM>>(
      probs, row_idx, d, temp_storage);
  float pivot = max_val * p;

  vec_t<float, VEC_SIZE> probs_vec;
  float aggregate_gt_pivot = 0;
  float threadlocal_aggregate_gt_pivot = 0;
#pragma unroll 2
  for (uint32_t i = 0; i < ceil_div(d, BLOCK_THREADS * VEC_SIZE); ++i) {
    probs_vec.fill(0);
    if ((i * BLOCK_THREADS + tx) * VEC_SIZE < d) {
      probs_vec.cast_load(probs + row_idx * d + (i * BLOCK_THREADS + tx) * VEC_SIZE);
    }

    float probs_gt_pivot[VEC_SIZE];
#pragma unroll
    for (uint32_t j = 0; j < VEC_SIZE; ++j) {
      probs_gt_pivot[j] = (probs_vec[j] >= pivot) ? probs_vec[j] : 0;
      threadlocal_aggregate_gt_pivot += probs_gt_pivot[j];
    }
  }

  aggregate_gt_pivot += BlockReduce<float, BLOCK_THREADS>(temp_storage.block_prim.reduce)
                            .Sum(threadlocal_aggregate_gt_pivot);
  if (tx == 0) {
    temp_storage.block_aggregate.value = aggregate_gt_pivot;
  }
  __syncthreads();

  float aggregate = 0;
  float q = temp_storage.block_aggregate.value;

  int sampled_id;
  temp_storage.sampled_id = d;
  temp_storage.last_valid_id = -1;
  __syncthreads();
  float u = curand_uniform(&state) * q;
#pragma unroll 2
  for (uint32_t i = 0; i < ceil_div(d, BLOCK_THREADS * VEC_SIZE); ++i) {
    probs_vec.fill(0);
    if ((i * BLOCK_THREADS + tx) * VEC_SIZE < d) {
      probs_vec.cast_load(probs + row_idx * d + (i * BLOCK_THREADS + tx) * VEC_SIZE);
    }

    DeviceSamplingFromProb<VEC_SIZE, BLOCK_THREADS, SCAN_ALGORITHM, REDUCE_ALGORITHM,
                           DETERMINISTIC>(
        i, d, [&](float x) { return x >= pivot; }, u, probs_vec, aggregate, &temp_storage);
    if (aggregate > u) {
      break;
    }
  }
  sampled_id = temp_storage.sampled_id;
  if (sampled_id == d) {
    // NOTE(Zihao): this would happen when u is very close to 1
    // and the sum of probabilities is smaller than u
    // In this case, we use the last valid index as the sampled id
    if (temp_storage.last_valid_id == -1) {
      if (tx == 0) {
        output[bx] = 0;
        valid[bx] = false;
      }
      return;
    }
    sampled_id = temp_storage.last_valid_id;
  }
  output[bx] = sampled_id;
  valid[bx] = true;
}

template <uint32_t BLOCK_THREADS, BlockScanAlgorithm SCAN_ALGORITHM,
          BlockReduceAlgorithm REDUCE_ALGORITHM, uint32_t VEC_SIZE, bool DETERMINISTIC,
          typename DType, typename IdType>
__global__ FLASHINFER_SAMPLING_LAUNCH_BOUNDS(BLOCK_THREADS) void TopKTopPSamplingFromProbKernel(
    DType* probs, IdType* top_k_arr, float* top_p_arr, IdType* output, bool* valid, IdType* indices,
    IdType top_k_val, float top_p_val, uint32_t d, uint64_t* seed_arr, uint64_t seed_val,
    uint64_t* offset_arr, uint64_t offset_val) {
  const uint32_t batch_size = gridDim.x;
  const uint32_t bx = blockIdx.x, tx = threadIdx.x;

  // Resolve seed/offset from tensor or scalar
  uint64_t philox_seed = seed_arr ? seed_arr[0] : seed_val;
  uint64_t philox_offset = offset_arr ? offset_arr[0] : offset_val;

  curandStatePhilox4_32_10_t state;
  curand_init(philox_seed, bx, philox_offset, &state);
  const uint32_t row_idx = indices == nullptr ? bx : indices[bx];
  const uint32_t k = top_k_arr == nullptr ? top_k_val : top_k_arr[row_idx];
  const float p = top_p_arr == nullptr ? top_p_val : top_p_arr[row_idx];

  extern __shared__ __align__(
      alignof(SamplingTempStorage<BLOCK_THREADS, SCAN_ALGORITHM, REDUCE_ALGORITHM>))
      uint8_t smem_sampling[];
  auto& temp_storage =
      reinterpret_cast<SamplingTempStorage<BLOCK_THREADS, SCAN_ALGORITHM, REDUCE_ALGORITHM>&>(
          smem_sampling);

  vec_t<float, VEC_SIZE> probs_vec;
  float aggregate;
  float q = 1;
  float low = 0, high = 1.f;
  int sampled_id;
  do {
    temp_storage.sampled_id = d;
    temp_storage.last_valid_id = -1;
    __syncthreads();
    float u = curand_uniform(&state) * q;
    aggregate = 0;
#pragma unroll 2
    for (uint32_t i = 0; i < ceil_div(d, BLOCK_THREADS * VEC_SIZE); ++i) {
      probs_vec.fill(0);
      if ((i * BLOCK_THREADS + tx) * VEC_SIZE < d) {
        probs_vec.cast_load(probs + row_idx * d + (i * BLOCK_THREADS + tx) * VEC_SIZE);
      }

      DeviceSamplingFromProb<VEC_SIZE, BLOCK_THREADS, SCAN_ALGORITHM, REDUCE_ALGORITHM,
                             DETERMINISTIC>(
          i, d, [&](float x) { return x > low; }, u, probs_vec, aggregate, &temp_storage);
      if (aggregate > u) {
        break;
      }
    }
    __syncthreads();
    sampled_id = temp_storage.sampled_id;
    if (sampled_id == d) {
      // NOTE(Zihao): this would happen when u is very close to 1
      // and the sum of probabilities is smaller than u
      // In this case, we use the last valid index as the sampled id
      sampled_id = temp_storage.last_valid_id;
      if (temp_storage.last_valid_id == -1) {
        if (tx == 0) {
          output[bx] = 0;
          valid[bx] = false;
        }
        return;
      }
    }
    // IEEE-754 arithmetic (no FTZ) for pivot computation.  See #769 / #774.
    float pivot_0 = probs[row_idx * d + sampled_id];
    float pivot_1 = ieee_mul(ieee_add(pivot_0, high), 0.5f);

    ValueCount<float> aggregate_gt_pivot_0{0, 0}, aggregate_gt_pivot_1{0, 0};
    ValueCount<float> threadlocal_aggregate_gt_pivot_0{0, 0};
    ValueCount<float> threadlocal_aggregate_gt_pivot_1{0, 0};
#pragma unroll 2
    for (uint32_t i = 0; i < ceil_div(d, BLOCK_THREADS * VEC_SIZE); ++i) {
      probs_vec.fill(0);
      if ((i * BLOCK_THREADS + tx) * VEC_SIZE < d) {
        probs_vec.cast_load(probs + row_idx * d + (i * BLOCK_THREADS + tx) * VEC_SIZE);
      }

      ValueCount<float> probs_gt_pivot_0[VEC_SIZE], probs_gt_pivot_1[VEC_SIZE];
#pragma unroll
      for (uint32_t j = 0; j < VEC_SIZE; ++j) {
        probs_gt_pivot_0[j] = {
            (probs_vec[j] > pivot_0) ? probs_vec[j] : 0,
            (probs_vec[j] > pivot_0 && (i * BLOCK_THREADS + tx) * VEC_SIZE + j < d)};
        probs_gt_pivot_1[j] = {
            (probs_vec[j] > pivot_1) ? probs_vec[j] : 0,
            (probs_vec[j] > pivot_1 && (i * BLOCK_THREADS + tx) * VEC_SIZE + j < d)};
        threadlocal_aggregate_gt_pivot_0 += probs_gt_pivot_0[j];
        threadlocal_aggregate_gt_pivot_1 += probs_gt_pivot_1[j];
      }
    }
    aggregate_gt_pivot_0 +=
        BlockReduce<ValueCount<float>, BLOCK_THREADS>(temp_storage.block_prim.reduce_value_count)
            .Sum(threadlocal_aggregate_gt_pivot_0);
    if (tx == 0) {
      temp_storage.block_aggregate.pair = aggregate_gt_pivot_0;
    }
    __syncthreads();
    aggregate_gt_pivot_0 = temp_storage.block_aggregate.pair;

    aggregate_gt_pivot_1 +=
        BlockReduce<ValueCount<float>, BLOCK_THREADS>(temp_storage.block_prim.reduce_value_count)
            .Sum(threadlocal_aggregate_gt_pivot_1);
    if (tx == 0) {
      temp_storage.block_aggregate.pair = aggregate_gt_pivot_1;
    }
    __syncthreads();
    aggregate_gt_pivot_1 = temp_storage.block_aggregate.pair;
    if (aggregate_gt_pivot_0.count < k && aggregate_gt_pivot_0.value < p) {
      // case 1: pivot_0 accepted
      break;
    }
    if (aggregate_gt_pivot_1.count < k && aggregate_gt_pivot_1.value < p) {
      // case 2: pivot_0 rejected, pivot_1 accepted
      low = pivot_0;
      high = pivot_1;
      q = aggregate_gt_pivot_0.value;
    } else {
      // case 3: pivot_0 rejected, pivot_1 rejected
      low = pivot_1;
      q = aggregate_gt_pivot_1.value;
    }
  } while (low < high);
  __syncthreads();
  if (tx == 0) {
    output[bx] = sampled_id;
    valid[bx] = true;
  }
}

template <typename DType>
cudaError_t OnlineSoftmax(DType* logits, DType* output, uint32_t batch_size, uint32_t d,
                          DType* temperature_arr, DType temperature_val, void* workspace_buffer,
                          size_t workspace_buffer_size_in_bytes, bool enable_pdl,
                          cudaStream_t stream = 0) {
  constexpr uint32_t SMALL_BATCH_THRESHOLD = 128;
  constexpr uint32_t LARGE_VOCAB_THRESHOLD = 24576;
  constexpr uint32_t DEFAULT_SLICE_SIZE = 8192;

  const uint32_t vec_size = std::gcd(16 / sizeof(DType), d);
  auto compute_capacity = GetCudaComputeCapability();

  DISPATCH_COMPUTE_CAP_NUM_THREADS(
      compute_capacity, BLOCK_THREADS, {DISPATCH_ALIGNED_VEC_SIZE(vec_size, VEC_SIZE, {
        if (batch_size <= SMALL_BATCH_THRESHOLD && d >= LARGE_VOCAB_THRESHOLD) {
          // Path A: Vocab-Splitting Strategy for small-batch & large-vocab
          uint32_t num_slices = ceil_div(d, DEFAULT_SLICE_SIZE);

          const size_t partial_buffer_size = batch_size * num_slices * sizeof(PartialSoftmaxResult);
          if (workspace_buffer_size_in_bytes < partial_buffer_size) {
            return cudaErrorInvalidValue;
          }

          AlignedAllocator allocator(workspace_buffer, workspace_buffer_size_in_bytes);
          auto partial_results = allocator.aligned_alloc<PartialSoftmaxResult>(
              partial_buffer_size, alignof(PartialSoftmaxResult), "softmax_workspace");

          // Phase 1: Map-Reduce across vocab slices
          dim3 phase1_nblks(batch_size, num_slices);
          dim3 phase1_nthrs(BLOCK_THREADS);
          size_t smem_size = sizeof(OnlineSoftmaxTempStorage<BLOCK_THREADS>);

          auto phase1_kernel = OnlineSoftmaxMapKernel<BLOCK_THREADS, VEC_SIZE, DType>;
          void* phase1_args[] = {&logits, &partial_results, &temperature_arr, &temperature_val,
                                 &d,      &num_slices};

          FLASHINFER_CUDA_CALL(cudaFuncSetAttribute(
              phase1_kernel, cudaFuncAttributeMaxDynamicSharedMemorySize, smem_size));

          if (enable_pdl) {
            cudaLaunchAttribute attribute[1];
            attribute[0].id = cudaLaunchAttributeProgrammaticStreamSerialization;
            attribute[0].val.programmaticStreamSerializationAllowed = 1;

            cudaLaunchConfig_t config;
            config.gridDim = phase1_nblks;
            config.blockDim = phase1_nthrs;
            config.dynamicSmemBytes = smem_size;
            config.stream = stream;
            config.attrs = attribute;
            config.numAttrs = 1;

            FLASHINFER_CUDA_CALL(cudaLaunchKernelEx(&config, phase1_kernel, logits, partial_results,
                                                    temperature_arr, temperature_val, d,
                                                    num_slices));
          } else {
            FLASHINFER_CUDA_CALL(cudaLaunchKernel((void*)phase1_kernel, phase1_nblks, phase1_nthrs,
                                                  phase1_args, smem_size, stream));
          }

          // Phase 2: Final reduction and apply normalization
          dim3 phase2_nblks(batch_size);
          dim3 phase2_nthrs(BLOCK_THREADS);

          auto phase2_kernel = OnlineSoftmaxReduceKernel<BLOCK_THREADS, VEC_SIZE, DType>;
          void* phase2_args[] = {&logits,          &output, &partial_results, &temperature_arr,
                                 &temperature_val, &d,      &num_slices};

          FLASHINFER_CUDA_CALL(cudaFuncSetAttribute(
              phase2_kernel, cudaFuncAttributeMaxDynamicSharedMemorySize, smem_size));

          if (enable_pdl) {
            cudaLaunchAttribute attribute[1];
            attribute[0].id = cudaLaunchAttributeProgrammaticStreamSerialization;
            attribute[0].val.programmaticStreamSerializationAllowed = 1;

            cudaLaunchConfig_t config;
            config.gridDim = phase2_nblks;
            config.blockDim = phase2_nthrs;
            config.dynamicSmemBytes = smem_size;
            config.stream = stream;
            config.attrs = attribute;
            config.numAttrs = 1;

            FLASHINFER_CUDA_CALL(cudaLaunchKernelEx(&config, phase2_kernel, logits, output,
                                                    partial_results, temperature_arr,
                                                    temperature_val, d, num_slices));
          } else {
            FLASHINFER_CUDA_CALL(cudaLaunchKernel((void*)phase2_kernel, phase2_nblks, phase2_nthrs,
                                                  phase2_args, smem_size, stream));
          }
        } else {
          // Path B: Single-Block Strategy
          // Switch input cache
          uint32_t cache_threshold;
          if (batch_size <= 16) {
            cache_threshold = 4096;
          } else if (batch_size <= 32) {
            cache_threshold = 2048;
          } else {
            cache_threshold = 0;
          }
          const bool cache_input = d <= cache_threshold;

          dim3 nblks(batch_size);
          dim3 nthrs(BLOCK_THREADS);
          void* args[] = {&logits, &output, &temperature_arr, &temperature_val, &d};

          const size_t smem_logits_bytes = (round_up(d, VEC_SIZE) + VEC_SIZE) * sizeof(DType);

          uint32_t smem_size = sizeof(OnlineSoftmaxTempStorage<BLOCK_THREADS>) +
                               (cache_input ? smem_logits_bytes : 0);

          DISPATCH_SOFTMAX_CACHE_INPUT(cache_input, CACHE_INPUT, {
            auto kernel = OnlineSoftmaxFusedKernel<BLOCK_THREADS, VEC_SIZE, DType, CACHE_INPUT>;
            FLASHINFER_CUDA_CALL(cudaFuncSetAttribute(
                kernel, cudaFuncAttributeMaxDynamicSharedMemorySize, smem_size));

            if (enable_pdl) {
              cudaLaunchAttribute attribute[1];
              attribute[0].id = cudaLaunchAttributeProgrammaticStreamSerialization;
              attribute[0].val.programmaticStreamSerializationAllowed = 1;

              cudaLaunchConfig_t config;
              config.gridDim = nblks;
              config.blockDim = nthrs;
              config.dynamicSmemBytes = smem_size;
              config.stream = stream;
              config.attrs = attribute;
              config.numAttrs = 1;

              FLASHINFER_CUDA_CALL(cudaLaunchKernelEx(&config, kernel, logits, output,
                                                      temperature_arr, temperature_val, d));
            } else {
              FLASHINFER_CUDA_CALL(
                  cudaLaunchKernel((void*)kernel, nblks, nthrs, args, smem_size, stream));
            }
          });
        }
      })});
  return cudaSuccess;
}

template <typename T, typename IdType>
cudaError_t SamplingFromLogits(T* logits, IdType* output, IdType* indices, uint32_t batch_size,
                               uint32_t d, bool deterministic, uint64_t* seed_arr,
                               uint64_t seed_val, uint64_t* offset_arr, uint64_t offset_val,
                               cudaStream_t stream = 0) {
  const uint32_t vec_size = std::gcd(16 / sizeof(T), d);

  auto compute_capacity = GetCudaComputeCapability();
  DISPATCH_COMPUTE_CAP_NUM_THREADS(compute_capacity, BLOCK_THREADS, {
    dim3 nblks(batch_size);
    dim3 nthrs(BLOCK_THREADS);
    void* args[] = {&logits, &output, &indices, &d, &seed_arr, &seed_val, &offset_arr, &offset_val};
    const uint32_t smem_size = sizeof(
        typename BlockReduce<DataAndIndex<T, IdType>, BLOCK_THREADS, REDUCE_ALGO>::TempStorage);

    DISPATCH_ALIGNED_VEC_SIZE(
        vec_size, VEC_SIZE, {DISPATCH_DETERMINISTIC(deterministic, DETERMINISTIC, {
          auto kernel = SamplingFromLogitsKernel<BLOCK_THREADS, SCAN_ALGO, REDUCE_ALGO, VEC_SIZE,
                                                 DETERMINISTIC, T, IdType>;
          FLASHINFER_CUDA_CALL(
              cudaLaunchKernel((void*)kernel, nblks, nthrs, args, smem_size, stream));
        })});
    return cudaSuccess;
  });
}

template <typename T, typename IdType>
cudaError_t SamplingFromProb(T* probs, IdType* output, bool* valid, IdType* indices,
                             uint32_t batch_size, uint32_t d, bool deterministic,
                             uint64_t* seed_arr, uint64_t seed_val, uint64_t* offset_arr,
                             uint64_t offset_val, cudaStream_t stream = 0) {
  const uint32_t vec_size = std::gcd(16 / sizeof(T), d);

  auto compute_capacity = GetCudaComputeCapability();
  DISPATCH_COMPUTE_CAP_NUM_THREADS(compute_capacity, BLOCK_THREADS, {
    dim3 nblks(batch_size);
    dim3 nthrs(BLOCK_THREADS);
    void* args[] = {&probs,    &output,   &valid,      &indices,   &d,
                    &seed_arr, &seed_val, &offset_arr, &offset_val};
    const uint32_t smem_size = sizeof(SamplingTempStorage<BLOCK_THREADS, SCAN_ALGO, REDUCE_ALGO>);

    DISPATCH_ALIGNED_VEC_SIZE(
        vec_size, VEC_SIZE, {DISPATCH_DETERMINISTIC(deterministic, DETERMINISTIC, {
          auto kernel = SamplingFromProbKernel<BLOCK_THREADS, SCAN_ALGO, REDUCE_ALGO, VEC_SIZE,
                                               DETERMINISTIC, T, IdType>;
          FLASHINFER_CUDA_CALL(
              cudaLaunchKernel((void*)kernel, nblks, nthrs, args, smem_size, stream));
        })});
    return cudaSuccess;
  });
}

template <typename T, typename IdType>
cudaError_t TopKSamplingFromProb(T* probs, IdType* output, bool* valid, IdType* indices,
                                 T* top_k_arr, uint32_t batch_size, uint32_t top_k_val, uint32_t d,
                                 bool deterministic, uint64_t* seed_arr, uint64_t seed_val,
                                 uint64_t* offset_arr, uint64_t offset_val,
                                 cudaStream_t stream = 0) {
  const uint32_t vec_size = std::gcd(16 / sizeof(T), d);

  auto compute_capacity = GetCudaComputeCapability();
  DISPATCH_COMPUTE_CAP_NUM_THREADS(compute_capacity, BLOCK_THREADS, {
    const uint32_t smem_size = sizeof(SamplingTempStorage<BLOCK_THREADS, SCAN_ALGO, REDUCE_ALGO>);
    dim3 nblks(batch_size);
    dim3 nthrs(BLOCK_THREADS);
    void* args[] = {&probs, &output,   &valid,    &indices,    &top_k_arr, &top_k_val,
                    &d,     &seed_arr, &seed_val, &offset_arr, &offset_val};

    DISPATCH_ALIGNED_VEC_SIZE(
        vec_size, VEC_SIZE, {DISPATCH_DETERMINISTIC(deterministic, DETERMINISTIC, {
          auto kernel = TopKSamplingFromProbKernel<BLOCK_THREADS, SCAN_ALGO, REDUCE_ALGO, VEC_SIZE,
                                                   DETERMINISTIC, T, IdType>;
          FLASHINFER_CUDA_CALL(
              cudaFuncSetAttribute(kernel, cudaFuncAttributeMaxDynamicSharedMemorySize, smem_size));
          FLASHINFER_CUDA_CALL(
              cudaLaunchKernel((void*)kernel, nblks, nthrs, args, smem_size, stream));
        })});
    return cudaSuccess;
  });
}

template <typename T, typename IdType>
cudaError_t TopPSamplingFromProb(T* probs, IdType* output, bool* valid, IdType* indices,
                                 T* top_p_arr, uint32_t batch_size, T top_p_val, uint32_t d,
                                 bool deterministic, uint64_t* seed_arr, uint64_t seed_val,
                                 uint64_t* offset_arr, uint64_t offset_val,
                                 cudaStream_t stream = 0) {
  const uint32_t vec_size = std::gcd(16 / sizeof(T), d);

  auto compute_capacity = GetCudaComputeCapability();
  DISPATCH_COMPUTE_CAP_NUM_THREADS(compute_capacity, BLOCK_THREADS, {
    const uint32_t smem_size = sizeof(SamplingTempStorage<BLOCK_THREADS, SCAN_ALGO, REDUCE_ALGO>);
    dim3 nblks(batch_size);
    dim3 nthrs(BLOCK_THREADS);
    void* args[] = {&probs, &output,   &valid,    &indices,    &top_p_arr, &top_p_val,
                    &d,     &seed_arr, &seed_val, &offset_arr, &offset_val};

    DISPATCH_ALIGNED_VEC_SIZE(
        vec_size, VEC_SIZE, {DISPATCH_DETERMINISTIC(deterministic, DETERMINISTIC, {
          auto kernel = TopPSamplingFromProbKernel<BLOCK_THREADS, SCAN_ALGO, REDUCE_ALGO, VEC_SIZE,
                                                   DETERMINISTIC, T, IdType>;
          FLASHINFER_CUDA_CALL(
              cudaFuncSetAttribute(kernel, cudaFuncAttributeMaxDynamicSharedMemorySize, smem_size));
          FLASHINFER_CUDA_CALL(
              cudaLaunchKernel((void*)kernel, nblks, nthrs, args, smem_size, stream));
        })});
    return cudaSuccess;
  });
}

template <typename T, typename IdType>
cudaError_t MinPSamplingFromProb(T* probs, T* min_p_arr, IdType* output, bool* valid,
                                 IdType* indices, uint32_t batch_size, float min_p_val, uint32_t d,
                                 bool deterministic, uint64_t* seed_arr, uint64_t seed_val,
                                 uint64_t* offset_arr, uint64_t offset_val,
                                 cudaStream_t stream = 0) {
  const uint32_t vec_size = std::gcd(16 / sizeof(T), d);

  auto compute_capacity = GetCudaComputeCapability();
  DISPATCH_COMPUTE_CAP_NUM_THREADS(compute_capacity, BLOCK_THREADS, {
    const uint32_t smem_size = sizeof(SamplingTempStorage<BLOCK_THREADS, SCAN_ALGO, REDUCE_ALGO>);
    dim3 nblks(batch_size);
    dim3 nthrs(BLOCK_THREADS);
    void* args[] = {&probs, &min_p_arr, &output,   &valid,      &indices,   &min_p_val,
                    &d,     &seed_arr,  &seed_val, &offset_arr, &offset_val};

    DISPATCH_ALIGNED_VEC_SIZE(
        vec_size, VEC_SIZE, {DISPATCH_DETERMINISTIC(deterministic, DETERMINISTIC, {
          auto kernel = MinPSamplingFromProbKernel<BLOCK_THREADS, SCAN_ALGO, REDUCE_ALGO, VEC_SIZE,
                                                   DETERMINISTIC, T, IdType>;
          FLASHINFER_CUDA_CALL(
              cudaFuncSetAttribute(kernel, cudaFuncAttributeMaxDynamicSharedMemorySize, smem_size));
          FLASHINFER_CUDA_CALL(
              cudaLaunchKernel((void*)kernel, nblks, nthrs, args, smem_size, stream));
        })});
    return cudaSuccess;
  });
}

template <typename T, typename IdType>
cudaError_t TopKTopPSamplingFromProb(T* probs, IdType* top_k_arr, T* top_p_arr, IdType* output,
                                     bool* valid, IdType* indices, uint32_t batch_size,
                                     IdType top_k_val, T top_p_val, uint32_t d, bool deterministic,
                                     uint64_t* seed_arr, uint64_t seed_val, uint64_t* offset_arr,
                                     uint64_t offset_val, cudaStream_t stream = 0) {
  const uint32_t vec_size = std::gcd(16 / sizeof(T), d);

  auto compute_capacity = GetCudaComputeCapability();
  DISPATCH_COMPUTE_CAP_NUM_THREADS(compute_capacity, BLOCK_THREADS, {
    const uint32_t smem_size = sizeof(SamplingTempStorage<BLOCK_THREADS, SCAN_ALGO, REDUCE_ALGO>);
    dim3 nblks(batch_size);
    dim3 nthrs(BLOCK_THREADS);
    void* args[] = {&probs,    &top_k_arr,  &top_p_arr, &output, &valid,
                    &indices,  &top_k_val,  &top_p_val, &d,      &seed_arr,
                    &seed_val, &offset_arr, &offset_val};

    DISPATCH_ALIGNED_VEC_SIZE(
        vec_size, VEC_SIZE, {DISPATCH_DETERMINISTIC(deterministic, DETERMINISTIC, {
          auto kernel = TopKTopPSamplingFromProbKernel<BLOCK_THREADS, SCAN_ALGO, REDUCE_ALGO,
                                                       VEC_SIZE, DETERMINISTIC, T, IdType>;
          FLASHINFER_CUDA_CALL(
              cudaFuncSetAttribute(kernel, cudaFuncAttributeMaxDynamicSharedMemorySize, smem_size));
          FLASHINFER_CUDA_CALL(
              cudaLaunchKernel((void*)kernel, nblks, nthrs, args, smem_size, stream));
        })});
    return cudaSuccess;
  });
}

template <uint32_t BLOCK_THREADS, BlockReduceAlgorithm REDUCE_ALGORITHM>
struct RenormTempStorage {
  union {
    typename BlockReduce<float, BLOCK_THREADS, REDUCE_ALGORITHM>::TempStorage reduce;
    typename BlockReduce<int, BLOCK_THREADS, REDUCE_ALGORITHM>::TempStorage reduce_int;
    typename BlockReduce<ValueCount<float>, BLOCK_THREADS, REDUCE_ALGORITHM>::TempStorage
        reduce_value_count;
  } block_prim;
  struct {
    float max_val;
    float min_val;
    float row_sum;
    union {
      struct {
        float values[2];
      };
      struct {
        int counts[2];
      };
      struct {
        ValueCount<float> pairs[2];
      };
    } block_aggregate;
  };
};

template <uint32_t BLOCK_THREADS, BlockReduceAlgorithm REDUCE_ALGORITHM, uint32_t VEC_SIZE,
          typename DType>
__global__ FLASHINFER_SAMPLING_LAUNCH_BOUNDS(BLOCK_THREADS) void TopPRenormProbKernel(
    DType* probs, DType* renormed_prob, float* top_p_arr, float top_p_val, uint32_t d) {
  const uint32_t bx = blockIdx.x, tx = threadIdx.x;
  const uint32_t row_idx = bx;
  float p = top_p_arr == nullptr ? top_p_val : top_p_arr[bx];

  extern __shared__ __align__(alignof(RenormTempStorage<BLOCK_THREADS, REDUCE_ALGO>))
      uint8_t smem_renorm[];
  auto& temp_storage =
      reinterpret_cast<RenormTempStorage<BLOCK_THREADS, REDUCE_ALGO>&>(smem_renorm);
  vec_t<float, VEC_SIZE> probs_vec;

  // Fast-path: when p >= 1.0 (e.g., p == 1.0), perform simple sum and normalization
  if (p >= 1.0f) {
    // Stage A: per-thread float accumulation over assigned lanes (vectorized)
    float thread_sum = 0.0f;
    const uint32_t num_iters = ceil_div(d, BLOCK_THREADS * VEC_SIZE);
    for (uint32_t i = 0; i < num_iters; ++i) {
      probs_vec.fill(0.0f);
      const uint32_t base_idx = (i * BLOCK_THREADS + tx) * VEC_SIZE;
      if (base_idx < d) {
        probs_vec.cast_load(probs + row_idx * d + base_idx);
      }
#pragma unroll
      for (uint32_t j = 0; j < VEC_SIZE; ++j) {
        const uint32_t idx = base_idx + j;
        if (idx < d) thread_sum += probs_vec[j];
      }
    }

    // Block reduce (float)
    float row_sum =
        BlockReduce<float, BLOCK_THREADS, REDUCE_ALGORITHM>(temp_storage.block_prim.reduce)
            .Sum(thread_sum);
    // Broadcast via shared
    if (tx == 0) temp_storage.row_sum = row_sum;
    __syncthreads();
    row_sum = temp_storage.row_sum;

    // Guard against zero sum
    const float denom = (row_sum <= 1e-8f) ? 1.0f : row_sum;
    const float normalizer = math::ptx_rcp(denom);

    // Stage B: normalize and store
    for (uint32_t i = 0; i < num_iters; ++i) {
      probs_vec.fill(0.0f);
      const uint32_t base_idx = (i * BLOCK_THREADS + tx) * VEC_SIZE;
      if (base_idx < d) {
        probs_vec.cast_load(probs + row_idx * d + base_idx);
      }
#pragma unroll
      for (uint32_t j = 0; j < VEC_SIZE; ++j) {
        const uint32_t idx = base_idx + j;
        float v = probs_vec[j];
        probs_vec[j] = (idx < d) ? (v * normalizer) : 0.0f;
      }
      if (base_idx < d) {
        probs_vec.cast_store(renormed_prob + row_idx * d + base_idx);
      }
    }
    return;  // Exit after fast-path processing
  }

  // Original Top-P renormalization logic
  temp_storage.max_val = 0;
  float max_val = GetMaxValue<VEC_SIZE, BLOCK_THREADS, REDUCE_ALGORITHM,
                              RenormTempStorage<BLOCK_THREADS, REDUCE_ALGORITHM>>(probs, row_idx, d,
                                                                                  temp_storage);

  float low = 0, high = max_val;
  float min_gt_low, max_le_high;
  float sum_low = 1;
  // f(x) = sum(probs[probs > x]), f(x) is non-increasing
  // min_gt_low = min{p \in probs | p > low}, max_le_high = max{p \in probs | p <= high}
  // loop invariant:
  // - f(low) >= p, f(high) < p
  // - f(low) > f(min_gt_low) >= f(max_le_high) == f(high)
  // stopping condition
  // - f(low) >= p, f(min_gt_low) == f(max_le_high) == f(high) < p
  do {
    // Use IEEE-754 arithmetic (no FTZ) to prevent -use_fast_math from flushing
    // subnormal pivots to zero, which would stall the search (#769, #774).
    float pivot_0 = ieee_div(ieee_add(high, ieee_mul(2.f, low)), 3.f);
    float pivot_1 = ieee_div(ieee_add(ieee_mul(2.f, high), low), 3.f);

    float aggregate_gt_pivot_0 = 0, aggregate_gt_pivot_1 = 0;
    min_gt_low = high;
    max_le_high = low;
    float threadlocal_aggregate_gt_pivot_0 = 0;
    float threadlocal_aggregate_gt_pivot_1 = 0;
#pragma unroll 2
    for (uint32_t i = 0; i < ceil_div(d, BLOCK_THREADS * VEC_SIZE); ++i) {
      probs_vec.fill(0);
      if ((i * BLOCK_THREADS + tx) * VEC_SIZE < d) {
        probs_vec.cast_load(probs + row_idx * d + i * BLOCK_THREADS * VEC_SIZE + tx * VEC_SIZE);
      }

      float probs_gt_pivot_0[VEC_SIZE], probs_gt_pivot_1[VEC_SIZE];
#pragma unroll
      for (uint32_t j = 0; j < VEC_SIZE; ++j) {
        probs_gt_pivot_0[j] = (probs_vec[j] > pivot_0) ? probs_vec[j] : 0;
        probs_gt_pivot_1[j] = (probs_vec[j] > pivot_1) ? probs_vec[j] : 0;

        if (probs_vec[j] > low && (i * BLOCK_THREADS + tx) * VEC_SIZE + j < d) {
          min_gt_low = min(min_gt_low, probs_vec[j]);
        }
        if (probs_vec[j] <= high && (i * BLOCK_THREADS + tx) * VEC_SIZE + j < d) {
          max_le_high = max(max_le_high, probs_vec[j]);
        }
        threadlocal_aggregate_gt_pivot_0 += probs_gt_pivot_0[j];
        threadlocal_aggregate_gt_pivot_1 += probs_gt_pivot_1[j];
      }
    }
    aggregate_gt_pivot_0 =
        BlockReduce<float, BLOCK_THREADS, REDUCE_ALGORITHM>(temp_storage.block_prim.reduce)
            .Sum(threadlocal_aggregate_gt_pivot_0);
    __syncthreads();
    aggregate_gt_pivot_1 =
        BlockReduce<float, BLOCK_THREADS, REDUCE_ALGORITHM>(temp_storage.block_prim.reduce)
            .Sum(threadlocal_aggregate_gt_pivot_1);
    __syncthreads();

    min_gt_low = BlockReduce<float, BLOCK_THREADS, REDUCE_ALGORITHM>(temp_storage.block_prim.reduce)
                     .Reduce(min_gt_low, MinReduceOp{});
    __syncthreads();
    max_le_high =
        BlockReduce<float, BLOCK_THREADS, REDUCE_ALGORITHM>(temp_storage.block_prim.reduce)
            .Reduce(max_le_high, MaxReduceOp{});
    if (tx == 0) {
      temp_storage.block_aggregate.values[0] = aggregate_gt_pivot_0;
      temp_storage.block_aggregate.values[1] = aggregate_gt_pivot_1;
      temp_storage.min_val = min_gt_low;
      temp_storage.max_val = max_le_high;
    }
    __syncthreads();
    aggregate_gt_pivot_0 = temp_storage.block_aggregate.values[0];
    aggregate_gt_pivot_1 = temp_storage.block_aggregate.values[1];
    min_gt_low = temp_storage.min_val;
    max_le_high = temp_storage.max_val;

    if (aggregate_gt_pivot_1 >= p) {
      low = pivot_1;
      sum_low = aggregate_gt_pivot_1;
    } else if (aggregate_gt_pivot_0 >= p) {
      low = pivot_0;
      high = min(pivot_1, max_le_high);
      sum_low = aggregate_gt_pivot_0;
    } else {
      high = min(pivot_0, max_le_high);
    }
  } while (min_gt_low < max_le_high && nextafterf(min_gt_low, max_le_high) < max_le_high);

  float normalizer = math::ptx_rcp(max(sum_low, 1e-8));

  // normalize
#pragma unroll 2
  for (uint32_t i = 0; i < ceil_div(d, BLOCK_THREADS * VEC_SIZE); ++i) {
    probs_vec.fill(0);
    if ((i * BLOCK_THREADS + tx) * VEC_SIZE < d) {
      probs_vec.cast_load(probs + row_idx * d + i * BLOCK_THREADS * VEC_SIZE + tx * VEC_SIZE);
    }
#pragma unroll
    for (uint32_t j = 0; j < VEC_SIZE; ++j) {
      probs_vec[j] = (probs_vec[j] > low) ? probs_vec[j] * normalizer : 0;
    }
    if ((i * BLOCK_THREADS + tx) * VEC_SIZE < d) {
      probs_vec.cast_store(renormed_prob + row_idx * d + i * BLOCK_THREADS * VEC_SIZE +
                           tx * VEC_SIZE);
    }
  }
}

template <typename DType>
cudaError_t TopPRenormProb(DType* probs, DType* renormed_prob, float* top_p_arr,
                           uint32_t batch_size, float top_p_val, uint32_t d,
                           cudaStream_t stream = 0) {
  const uint32_t vec_size = std::gcd(16 / sizeof(DType), d);

  auto compute_capacity = GetCudaComputeCapability();
  DISPATCH_COMPUTE_CAP_NUM_THREADS(compute_capacity, BLOCK_THREADS, {
    const uint32_t smem_size = sizeof(RenormTempStorage<BLOCK_THREADS, REDUCE_ALGO>);
    dim3 nblks(batch_size);
    dim3 nthrs(BLOCK_THREADS);
    void* args[] = {&probs, &renormed_prob, &top_p_arr, &top_p_val, &d};
    DISPATCH_ALIGNED_VEC_SIZE(vec_size, VEC_SIZE, {
      auto kernel = TopPRenormProbKernel<BLOCK_THREADS, REDUCE_ALGO, VEC_SIZE, DType>;
      FLASHINFER_CUDA_CALL(
          cudaFuncSetAttribute(kernel, cudaFuncAttributeMaxDynamicSharedMemorySize, smem_size));
      FLASHINFER_CUDA_CALL(cudaLaunchKernel((void*)kernel, nblks, nthrs, args, smem_size, stream));
    });
    return cudaSuccess;
  });
}

template <uint32_t BLOCK_THREADS, BlockScanAlgorithm SCAN_ALGORITHM,
          BlockReduceAlgorithm REDUCE_ALGORITHM, uint32_t VEC_SIZE, bool DETERMINISTIC,
          typename DType, typename IdType>
__global__ FLASHINFER_SAMPLING_LAUNCH_BOUNDS(BLOCK_THREADS) void ChainSpeculativeSampling(
    DType* draft_probs, IdType* draft_token_ids, DType* target_probs, IdType* output_token_ids,
    IdType* output_accepted_token_num, IdType* output_emitted_draft_token_num,
    uint32_t num_speculative_tokens, uint32_t d, uint64_t* seed_arr, uint64_t seed_val,
    uint64_t* offset_arr, uint64_t offset_val) {
  const uint32_t bx = blockIdx.x, tx = threadIdx.x;
  const uint32_t row_idx = bx;

  // Resolve seed/offset from tensor or scalar
  uint64_t philox_seed = seed_arr ? seed_arr[0] : seed_val;
  uint64_t philox_offset = offset_arr ? offset_arr[0] : offset_val;

  curandStatePhilox4_32_10_t curand_state;
  curand_init(philox_seed, bx, philox_offset, &curand_state);

  extern __shared__ __align__(
      alignof(SamplingTempStorage<BLOCK_THREADS, SCAN_ALGORITHM, REDUCE_ALGORITHM>))
      uint8_t smem_sampling[];
  auto& temp_storage =
      reinterpret_cast<SamplingTempStorage<BLOCK_THREADS, SCAN_ALGORITHM, REDUCE_ALGORITHM>&>(
          smem_sampling);

  uint32_t pos = num_speculative_tokens;
  for (uint32_t i = 0; i < num_speculative_tokens; ++i) {
    IdType draft_id = draft_token_ids[row_idx * num_speculative_tokens + i];
    float q = target_probs[(row_idx * (num_speculative_tokens + 1) + i) * d + draft_id],
          p = draft_probs[(row_idx * num_speculative_tokens + i) * d + draft_id];
    float u = curand_uniform(&curand_state);
    if (u * p < q) {
      // accept the draft models output
      output_token_ids[row_idx * (num_speculative_tokens + 1) + i] = draft_id;
    } else {
      pos = i;
      break;
    }
  }

  uint32_t emitted_token_num = pos;
  uint32_t accepted_token_num = pos;
  for (uint32_t i = pos; i < num_speculative_tokens; ++i) {
    int draft_id = draft_token_ids[row_idx * num_speculative_tokens + i];
    float q = target_probs[(row_idx * (num_speculative_tokens + 1) + i) * d + draft_id],
          p = draft_probs[(row_idx * num_speculative_tokens + i) * d + draft_id];
    float u = curand_uniform(&curand_state);
    if (u * p < q) {
      ++accepted_token_num;
    }
  }

  if (tx == 0) {
    output_accepted_token_num[row_idx] += accepted_token_num;
    output_emitted_draft_token_num[row_idx] += emitted_token_num;
  }

  // sample from relu(target_probs - draft_probs)
  float sum_relu_q_minus_p = 0;
  vec_t<float, VEC_SIZE> q_vec, p_vec;
  float relu_q_minus_p[VEC_SIZE];
#pragma unroll 2
  for (uint32_t i = 0; i < ceil_div(d, BLOCK_THREADS * VEC_SIZE); ++i) {
    q_vec.fill(0);
    p_vec.fill(0);
    if ((i * BLOCK_THREADS + tx) * VEC_SIZE < d) {
      q_vec.cast_load(target_probs + (row_idx * (num_speculative_tokens + 1) + pos) * d +
                      i * BLOCK_THREADS * VEC_SIZE + tx * VEC_SIZE);
      if (pos != num_speculative_tokens) {
        // there is no draft_probs for the bonus token
        p_vec.cast_load(draft_probs + (row_idx * num_speculative_tokens + pos) * d +
                        i * BLOCK_THREADS * VEC_SIZE + tx * VEC_SIZE);
      }
    }
#pragma unroll
    for (uint32_t j = 0; j < VEC_SIZE; ++j) {
      relu_q_minus_p[j] = max(q_vec[j] - p_vec[j], 0.0f);
    }
    sum_relu_q_minus_p +=
        BlockReduce<float, BLOCK_THREADS, REDUCE_ALGORITHM>(temp_storage.block_prim.reduce)
            .Sum<VEC_SIZE>(relu_q_minus_p);
    __syncthreads();
  }
  if (tx == 0) {
    temp_storage.block_aggregate.value = sum_relu_q_minus_p;
  }
  // init the first rejected token to d
  temp_storage.sampled_id = d;
  __syncthreads();
  sum_relu_q_minus_p = temp_storage.block_aggregate.value;
  float u = curand_uniform(&curand_state) * sum_relu_q_minus_p;

  float aggregate_relu_q_minus_p(0);
#pragma unroll 2
  for (uint32_t i = 0; i < ceil_div(d, BLOCK_THREADS * VEC_SIZE); ++i) {
    q_vec.fill(0);
    p_vec.fill(0);
    if ((i * BLOCK_THREADS + tx) * VEC_SIZE < d) {
      q_vec.cast_load(target_probs + (row_idx * (num_speculative_tokens + 1) + pos) * d +
                      i * BLOCK_THREADS * VEC_SIZE + tx * VEC_SIZE);
      if (pos != num_speculative_tokens) {
        // there is no draft_probs for the bonus token
        p_vec.cast_load(draft_probs + (row_idx * num_speculative_tokens + pos) * d +
                        i * BLOCK_THREADS * VEC_SIZE + tx * VEC_SIZE);
      }
    }

    vec_t<float, VEC_SIZE> relu_q_minus_p_vec;
#pragma unroll
    for (uint32_t j = 0; j < VEC_SIZE; ++j) {
      relu_q_minus_p_vec[j] = max(q_vec[j] - p_vec[j], 0.0f);
    }

    DeviceSamplingFromProb<VEC_SIZE, BLOCK_THREADS, SCAN_ALGORITHM, REDUCE_ALGORITHM,
                           DETERMINISTIC>(
        i, d, [&](float x) { return x > 0; }, u, relu_q_minus_p_vec, aggregate_relu_q_minus_p,
        &temp_storage);
    if (aggregate_relu_q_minus_p > u) {
      break;
    }
  }
  __syncthreads();
  int sampled_id = temp_storage.sampled_id;
  if (sampled_id == d) {
    // NOTE(Zihao): this would happen when u is very close to 1
    // and the sum of probabilities is smaller than u
    // In this case, we use the last valid index as the sampled id
    sampled_id = temp_storage.last_valid_id;
  }
  // set the first rejected token
  output_token_ids[row_idx * (num_speculative_tokens + 1) + pos] = sampled_id;
  // move to the next token
  pos++;

  // pad remaining tokens with -1
  for (; pos < num_speculative_tokens + 1; ++pos) {
    output_token_ids[row_idx * (num_speculative_tokens + 1) + pos] = -1;
  }
}

template <typename DType, typename IdType>
cudaError_t ChainSpeculativeSampling(
    DType* draft_probs, IdType* draft_token_ids, DType* target_probs, IdType* output_token_ids,
    IdType* output_accepted_token_num, IdType* output_emitted_draft_token_num, uint32_t batch_size,
    uint32_t num_speculative_tokens, uint32_t d, bool deterministic, uint64_t* seed_arr,
    uint64_t seed_val, uint64_t* offset_arr, uint64_t offset_val, cudaStream_t stream = 0) {
  const uint32_t vec_size = std::gcd(16 / sizeof(DType), d);

  auto compute_capacity = GetCudaComputeCapability();
  DISPATCH_COMPUTE_CAP_NUM_THREADS(compute_capacity, BLOCK_THREADS, {
    const uint32_t smem_size = sizeof(SamplingTempStorage<BLOCK_THREADS, SCAN_ALGO, REDUCE_ALGO>);
    dim3 nblks(batch_size);
    dim3 nthrs(BLOCK_THREADS);
    void* args[] = {&draft_probs,
                    &draft_token_ids,
                    &target_probs,
                    &output_token_ids,
                    &output_accepted_token_num,
                    &output_emitted_draft_token_num,
                    &num_speculative_tokens,
                    &d,
                    &seed_arr,
                    &seed_val,
                    &offset_arr,
                    &offset_val};
    DISPATCH_ALIGNED_VEC_SIZE(
        vec_size, VEC_SIZE, {DISPATCH_DETERMINISTIC(deterministic, DETERMINISTIC, {
          auto kernel = ChainSpeculativeSampling<BLOCK_THREADS, SCAN_ALGO, REDUCE_ALGO, VEC_SIZE,
                                                 DETERMINISTIC, DType, IdType>;
          FLASHINFER_CUDA_CALL(
              cudaFuncSetAttribute(kernel, cudaFuncAttributeMaxDynamicSharedMemorySize, smem_size));
          FLASHINFER_CUDA_CALL(
              cudaLaunchKernel((void*)kernel, nblks, nthrs, args, smem_size, stream));
        })});
    return cudaSuccess;
  });
}

}  // namespace sampling

}  // namespace flashinfer

#endif  // FLASHINFER_SAMPLING_CUH_
