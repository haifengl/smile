/*
 * Copyright (c) 2026 by FlashInfer team.
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
// AIR Top-P Renorm: radix-based top-p threshold finding + renormalization.
// Core algorithm from TensorRT-LLM's AIR Top-P (samplingAirTopPKernels.cu),
// adapted as a standalone header with no TRT-LLM dependencies.

#pragma once

#include <cooperative_groups.h>
#include <cooperative_groups/reduce.h>

#include <cub/cub.cuh>
#include <cuda/atomic>
#include <cuda/std/limits>
#include <type_traits>

namespace flashinfer {
namespace sampling {
namespace air_top_p {

using IdxT = int;
using AccT = float;

static constexpr int BITS_PER_PASS = 11;
static constexpr int NUM_BUCKETS = 1 << BITS_PER_PASS;
static constexpr int BLOCK_SIZE = 512;
using WideT = float4;

template <typename T>
static constexpr int NUM_PASSES = (sizeof(T) * 8 + BITS_PER_PASS - 1) / BITS_PER_PASS;

template <bool IsDeterministic, typename T>
using HisT =
    std::conditional_t<IsDeterministic,
                       std::conditional_t<std::is_same_v<T, float>, uint64_t, uint32_t>, float>;

// ======================== Counter ========================

template <typename T>
struct alignas(128) Counter {
  T const* in;
  IdxT oriLen;
  AccT sum;
  IdxT len;
  float p;
  IdxT previousLen;
  typename cub::Traits<T>::UnsignedBits kthValueBits;
  alignas(128) IdxT filterCnt;
  alignas(128) uint32_t finishedBlockCnt;
};

// ======================== Helpers ========================

template <typename IntType>
constexpr __host__ __device__ IntType ceilDiv(IntType a, IntType b) {
  return (a + b - 1) / b;
}

template <typename IntType>
constexpr __host__ __device__ IntType alignTo(IntType a, IntType b) {
  return ceilDiv(a, b) * b;
}

template <typename T>
__device__ int constexpr calcStartBit(int pass) {
  int startBit = static_cast<int>(sizeof(T) * 8) - (pass + 1) * BITS_PER_PASS;
  return startBit < 0 ? 0 : startBit;
}

template <typename T>
__device__ uint32_t constexpr calcMask(int pass) {
  int numBits = calcStartBit<T>(pass - 1) - calcStartBit<T>(pass);
  return (1 << numBits) - 1;
}

template <typename T>
__device__ typename cub::Traits<T>::UnsignedBits twiddleIn(T key, bool selectMin) {
  auto bits = reinterpret_cast<typename cub::Traits<T>::UnsignedBits&>(key);
  bits = cub::Traits<T>::TwiddleIn(bits);
  if (!selectMin) bits = ~bits;
  return bits;
}

template <typename T>
__device__ T twiddleOut(typename cub::Traits<T>::UnsignedBits bits, bool selectMin) {
  if (!selectMin) bits = ~bits;
  bits = cub::Traits<T>::TwiddleOut(bits);
  return reinterpret_cast<T&>(bits);
}

template <typename T>
__device__ int calcBucket(T x, int startBit, uint32_t mask) {
  return (twiddleIn(x, false) >> startBit) & mask;
}

template <typename T>
__host__ __device__ IdxT calcBufLen(IdxT len) {
  IdxT constexpr ratio = 2 + sizeof(IdxT) * 2 / sizeof(T);
  IdxT bufLen = len / (ratio * 8);
  bufLen = alignTo(bufLen, 256);
  return bufLen;
}

template <typename T>
__host__ __device__ void setBufPointers(T const* in, T* buf1, T* buf2, int pass, T const*& inBuf,
                                        T*& outBuf) {
  if (pass == 0) {
    inBuf = in;
    outBuf = nullptr;
  } else if (pass == 1) {
    inBuf = in;
    outBuf = buf1;
  } else if (pass % 2 == 0) {
    inBuf = buf1;
    outBuf = buf2;
  } else {
    inBuf = buf2;
    outBuf = buf1;
  }
}

// ======================== Deterministic helpers ========================

__device__ inline uint32_t calcMantissa(float value) {
  union {
    uint32_t bits;
    float value;
  } input;
  input.value = value;
  constexpr uint32_t numMantissa = 23;
  return input.bits & ((1u << numMantissa) - 1);
}

__device__ inline uint32_t calcExponent(float value) {
  union {
    uint32_t bits;
    float value;
  } input;
  input.value = value;
  constexpr uint32_t numMantissa = 23;
  return input.bits & ~((1u << numMantissa) - 1);
}

__device__ inline float calcFloatValue(uint32_t count, uint32_t exponent, uint64_t bitSum) {
  constexpr uint32_t numTotalBits = 64;
  constexpr uint32_t numMantissa = 23;
  uint64_t extraInMantissa = (bitSum >> numMantissa);
  extraInMantissa = (exponent == 0) ? extraInMantissa : extraInMantissa + count;
  uint32_t numExtra = numTotalBits - __clzll(extraInMantissa);
  int numNorm = (exponent == 0) ? 0 : -1;
  exponent = exponent + ((numExtra + numNorm) << numMantissa);
  uint32_t mantissa;
  if (extraInMantissa != 0) {
    int numMove = numMantissa - (numExtra - 1);
    uint32_t mask = (1u << (numExtra - 1)) - 1;
    extraInMantissa = extraInMantissa & mask;
    if (numMove > 0) {
      extraInMantissa = extraInMantissa << numMove;
      mask = (1u << numMantissa) - 1;
      mantissa = ((bitSum & mask) >> (numExtra - 1)) | extraInMantissa;
    } else {
      mantissa = extraInMantissa >> (-1 * numMove);
    }
  } else {
    mantissa = bitSum;
  }
  uint32_t bitFloat = exponent | mantissa;
  return reinterpret_cast<float&>(bitFloat);
}

template <bool IsDeterministic, typename T, typename HisT_>
__device__ constexpr void histAtomicAdd(HisT_* dst, T value) {
  if constexpr (IsDeterministic) {
    uint32_t m = calcMantissa(value);
    atomicAdd(reinterpret_cast<unsigned long long*>(dst), static_cast<uint64_t>(m));
  } else {
    atomicAdd(dst, static_cast<float>(value));
  }
}

// ======================== Vectorized process ========================

template <typename T, typename Func>
__device__ void vectorizedProcess(size_t threadRank, size_t numThreads, T const* in, IdxT len,
                                  Func f) {
  if constexpr (sizeof(T) >= sizeof(WideT)) {
    for (IdxT i = threadRank; i < len; i += numThreads) f(in[i], i);
  } else {
    static_assert(sizeof(WideT) % sizeof(T) == 0);
    constexpr int itemsPerScalar = sizeof(WideT) / sizeof(T);
    union {
      WideT scalar;
      T array[itemsPerScalar];
    } wide;
    int skipCnt = (reinterpret_cast<size_t>(in) % sizeof(WideT))
                      ? ((sizeof(WideT) - reinterpret_cast<size_t>(in) % sizeof(WideT)) / sizeof(T))
                      : 0;
    if (skipCnt > len) skipCnt = len;
    WideT const* inCast = reinterpret_cast<decltype(inCast)>(in + skipCnt);
    IdxT const lenCast = (len - skipCnt) / itemsPerScalar;
    for (IdxT i = threadRank; i < lenCast; i += numThreads) {
      wide.scalar = inCast[i];
      IdxT const real_i = skipCnt + i * itemsPerScalar;
#pragma unroll
      for (int j = 0; j < itemsPerScalar; ++j) f(wide.array[j], real_i + j);
    }
    if (static_cast<IdxT>(threadRank) < skipCnt) f(in[threadRank], static_cast<IdxT>(threadRank));
    IdxT const remain_i = skipCnt + lenCast * itemsPerScalar + threadRank;
    if (remain_i < len) f(in[remain_i], remain_i);
  }
}

// ======================== Filter + Histogram ========================

template <bool IsDeterministic, typename T, typename HisT_>
__device__ __forceinline__ void filterAndHistogram(T const* inBuf, T* outBuf, int previousLen,
                                                   Counter<T>* counter, HisT_* histogram,
                                                   IdxT* countHistogram, HisT_* histogramSmem,
                                                   IdxT* countHistogramSmem, int pass) {
  for (IdxT i = threadIdx.x; i < NUM_BUCKETS; i += blockDim.x) {
    histogramSmem[i] = 0;
    countHistogramSmem[i] = 0;
  }
  __syncthreads();
  int const startBit = calcStartBit<T>(pass);
  uint32_t const mask = calcMask<T>(pass);
  if (pass == 0) {
    auto f = [startBit, mask, histogramSmem, countHistogramSmem](T value, IdxT) {
      int bucket = calcBucket<T>(value, startBit, mask);
      histAtomicAdd<IsDeterministic, T>(histogramSmem + bucket, value);
      atomicAdd(countHistogramSmem + bucket, static_cast<IdxT>(1));
    };
    vectorizedProcess<T>(static_cast<size_t>(blockIdx.x) * blockDim.x + threadIdx.x,
                         static_cast<size_t>(blockDim.x) * gridDim.x, inBuf, previousLen, f);
  } else {
    IdxT* pFilterCnt = &counter->filterCnt;
    auto const kthValueBits = counter->kthValueBits;
    int const previousStartBit = calcStartBit<T>(pass - 1);
    auto f = [outBuf, startBit, mask, previousStartBit, kthValueBits, pFilterCnt, histogramSmem,
              countHistogramSmem](T value, IdxT) {
      auto const previousBits = (twiddleIn(value, false) >> previousStartBit) << previousStartBit;
      if (previousBits == kthValueBits) {
        if (outBuf) {
          IdxT pos = atomicAdd(pFilterCnt, static_cast<IdxT>(1));
          outBuf[pos] = value;
        }
        int bucket = calcBucket<T>(value, startBit, mask);
        histAtomicAdd<IsDeterministic, T>(histogramSmem + bucket, value);
        atomicAdd(countHistogramSmem + bucket, static_cast<IdxT>(1));
      }
    };
    vectorizedProcess<T>(static_cast<size_t>(blockIdx.x) * blockDim.x + threadIdx.x,
                         static_cast<size_t>(blockDim.x) * gridDim.x, inBuf, previousLen, f);
  }
  __syncthreads();
  for (int i = threadIdx.x; i < NUM_BUCKETS; i += blockDim.x) {
    if (histogramSmem[i] != 0) {
      if constexpr (IsDeterministic)
        atomicAdd(reinterpret_cast<unsigned long long*>(histogram + i),
                  static_cast<unsigned long long>(histogramSmem[i]));
      else
        atomicAdd(histogram + i, histogramSmem[i]);
    }
    if (countHistogramSmem[i] != 0) atomicAdd(countHistogram + i, countHistogramSmem[i]);
  }
}

// ======================== Main radix kernel ========================

template <bool IsDeterministic, typename T>
__global__ void AirTopPRenormRadixKernel(Counter<T>* counters, HisT<IsDeterministic, T>* histograms,
                                         IdxT* countHistograms, int const pass, T* buf1, T* buf2) {
  using HisT_ = HisT<IsDeterministic, T>;
  int const batchId = blockIdx.y;
  auto counter = counters + batchId;
  AccT currentSum;
  IdxT previousLen, currentLen;
  if (pass == 0) {
    currentSum = 0;
    previousLen = counter->len;
    currentLen = counter->len;
  } else {
    currentSum = counter->sum;
    currentLen = counter->len;
    previousLen = counter->previousLen;
  }
  if (currentLen == 0) return;
  IdxT const bufLen = calcBufLen<T>(counter->oriLen);
  T const* inBuf = nullptr;
  T* outBuf = nullptr;
  setBufPointers(counter->in, buf1 + bufLen * batchId, buf2 + bufLen * batchId, pass, inBuf,
                 outBuf);
  if (pass == 0 || pass == 1 || previousLen > bufLen) {
    inBuf = counter->in;
    previousLen = counter->oriLen;
  }
  if (pass == 0 || currentLen > bufLen) {
    outBuf = nullptr;
  }
  auto histogram = histograms + batchId * NUM_BUCKETS;
  auto countHistogram = countHistograms + batchId * NUM_BUCKETS;
  __shared__ HisT_ histogramSmem[NUM_BUCKETS];
  __shared__ IdxT countHistogramSmem[NUM_BUCKETS];
  filterAndHistogram<IsDeterministic, T, HisT_>(inBuf, outBuf, previousLen, counter, histogram,
                                                countHistogram, histogramSmem, countHistogramSmem,
                                                pass);
  __syncthreads();
  __threadfence();
  bool isLastBlock = false;
  if (threadIdx.x == 0) {
    uint32_t finished = atomicInc(&counter->finishedBlockCnt, gridDim.x - 1);
    isLastBlock = (finished == (gridDim.x - 1));
  }
  if (__syncthreads_or(isLastBlock)) {
    AccT* histValueSmem = reinterpret_cast<AccT*>(histogramSmem);
    if constexpr (IsDeterministic) {
      for (int i = threadIdx.x; i < NUM_BUCKETS; i += blockDim.x) {
        uint64_t value = static_cast<uint64_t>(histogram[i]);
        IdxT count = countHistogram[i];
        if (count != 0) {
          uint32_t sb = calcStartBit<T>(pass);
          uint32_t bv = counter->kthValueBits;
          if (pass == 0) bv = i << sb;
          histValueSmem[i] = calcFloatValue(static_cast<uint32_t>(count),
                                            calcExponent(twiddleOut<T>(bv, false)), value);
        } else {
          histValueSmem[i] = 0.0f;
        }
      }
      __syncthreads();
    }
    constexpr int WARP_SIZE = 32;
    constexpr int WARP_COUNT = NUM_BUCKETS / WARP_SIZE;
    namespace cg = cooperative_groups;
    cg::thread_block block = cg::this_thread_block();
    cg::thread_block_tile<32> warp = cg::tiled_partition<32>(block);
    AccT* histPtr = IsDeterministic ? histValueSmem : reinterpret_cast<AccT*>(histogram);
    __shared__ AccT warpSum[WARP_COUNT];
    __shared__ cuda::atomic<AccT, cuda::thread_scope_block> blockSum;
    if constexpr (BITS_PER_PASS != 11) {
      for (int i = threadIdx.x; i < NUM_BUCKETS; i += BLOCK_SIZE) warpSum[i] = 0;
      __syncthreads();
    }
    for (int i = threadIdx.x; i < NUM_BUCKETS; i += BLOCK_SIZE)
      reduce_store_async(warp, warpSum + i / WARP_SIZE, histPtr[i], cg::plus<float>{});
    __syncthreads();
    if (threadIdx.x < WARP_SIZE) {
      reduce_store_async(warp, blockSum, warpSum[threadIdx.x], cg::plus<float>{});
      if constexpr (BITS_PER_PASS == 11)
        reduce_update_async(warp, blockSum, warpSum[threadIdx.x + WARP_SIZE], cg::plus<float>{});
    }
    __syncthreads();
    if (pass == 0) currentSum = blockSum * counter->p;
    if (threadIdx.x == 0) {
      AccT prev = 0;
      int targetStep = 0;
      for (int i = 0; i < WARP_COUNT; i++) {
        if (warpSum[i]) {
          targetStep = i;
          if ((prev + warpSum[i]) >= currentSum) break;
          prev += warpSum[i];
        }
      }
      int targetIdx = 0;
      for (int i = targetStep * WARP_SIZE; i < NUM_BUCKETS; i++) {
        if (countHistogram[i]) {
          targetIdx = i;
          if ((prev + histPtr[i]) >= currentSum) break;
          prev += histPtr[i];
        }
      }
      counter->sum = currentSum - prev;
      counter->len = countHistogram[targetIdx];
      typename cub::Traits<T>::UnsignedBits bucket = targetIdx;
      counter->kthValueBits |= bucket << calcStartBit<T>(pass);
    }
    __syncthreads();
    if (pass != NUM_PASSES<T> - 1) {
      for (int i = threadIdx.x; i < NUM_BUCKETS; i += blockDim.x) {
        histogram[i] = 0;
        countHistogram[i] = 0;
      }
    }
    if (threadIdx.x == 0) {
      counter->previousLen = currentLen;
      counter->filterCnt = 0;
    }
  }
}

// ======================== Init kernel ========================

template <typename T, typename HisT_>
__global__ void AirTopPRenormInitKernel(Counter<T>* counters, int len, T const* in,
                                        float* top_p_arr, float top_p_val, HisT_* histograms,
                                        IdxT* countHistograms) {
  auto const batchIdx = blockIdx.x;
  Counter<T>* counter = counters + batchIdx;
  if (threadIdx.x == 0) {
    counter->in = in + batchIdx * len;
    counter->len = len;
    counter->oriLen = len;
    counter->previousLen = len;
    counter->p = top_p_arr ? top_p_arr[batchIdx] : top_p_val;
    counter->sum = 0;
    counter->kthValueBits = 0;
    counter->finishedBlockCnt = 0;
    counter->filterCnt = 0;
  }
  HisT_* hist = histograms + batchIdx * NUM_BUCKETS;
  IdxT* cntHist = countHistograms + batchIdx * NUM_BUCKETS;
  for (int i = threadIdx.x; i < NUM_BUCKETS; i += blockDim.x) {
    hist[i] = 0;
    cntHist[i] = 0;
  }
}

// ======================== Renorm apply kernel ========================

template <typename T>
__global__ void AirTopPRenormApplyKernel(T const* probs, T* renormedProbs, Counter<T>* counters,
                                         int vocabSize) {
  int const batchId = blockIdx.x;
  int const tid = threadIdx.x;
  T const threshold = twiddleOut<T>(counters[batchId].kthValueBits, false);
  float threadSum = 0.0f;
  for (int i = tid; i < vocabSize; i += blockDim.x) {
    T val = probs[batchId * vocabSize + i];
    if (val >= threshold) threadSum += static_cast<float>(val);
  }
  typedef cub::BlockReduce<float, 1024> BlockReduce;
  __shared__ typename BlockReduce::TempStorage temp;
  float totalSum = BlockReduce(temp).Sum(threadSum);
  __shared__ float sharedNorm;
  if (tid == 0) sharedNorm = (totalSum > 1e-8f) ? (1.0f / totalSum) : 1.0f;
  __syncthreads();
  float norm = sharedNorm;
  for (int i = tid; i < vocabSize; i += blockDim.x) {
    T val = probs[batchId * vocabSize + i];
    renormedProbs[batchId * vocabSize + i] =
        (val >= threshold) ? static_cast<T>(static_cast<float>(val) * norm) : static_cast<T>(0);
  }
}

// ======================== Block num calculation ========================

template <bool IsDeterministic, typename T>
uint32_t CalcAirTopPBlockNum(int batchSize, int len, int smCnt) {
  constexpr int VECTORIZED_READ_SIZE = 16;
  int activeBlocks;
  cudaOccupancyMaxActiveBlocksPerMultiprocessor(
      &activeBlocks, AirTopPRenormRadixKernel<IsDeterministic, T>, BLOCK_SIZE, 0);
  activeBlocks *= smCnt;
  IdxT bestNumBlocks = 0;
  float bestTailWavePenalty = 1.0f;
  IdxT const maxNumBlocks = ceilDiv<IdxT>(len, VECTORIZED_READ_SIZE / (int)sizeof(T) * BLOCK_SIZE);
  for (int numWaves = 1;; ++numWaves) {
    IdxT numBlocks =
        std::min(maxNumBlocks, static_cast<IdxT>(std::max(numWaves * activeBlocks / batchSize, 1)));
    IdxT itemsPerThread = ceilDiv<IdxT>(len, numBlocks * BLOCK_SIZE);
    itemsPerThread = alignTo<IdxT>(itemsPerThread, VECTORIZED_READ_SIZE / (int)sizeof(T));
    numBlocks = ceilDiv<IdxT>(len, itemsPerThread * BLOCK_SIZE);
    float actualNumWaves = static_cast<float>(numBlocks) * batchSize / activeBlocks;
    float tailWavePenalty = (ceilf(actualNumWaves) - actualNumWaves) / ceilf(actualNumWaves);
    if (tailWavePenalty < 0.15f) {
      bestNumBlocks = numBlocks;
      break;
    } else if (tailWavePenalty < bestTailWavePenalty) {
      bestNumBlocks = numBlocks;
      bestTailWavePenalty = tailWavePenalty;
    }
    if (numBlocks == maxNumBlocks) break;
  }
  return bestNumBlocks;
}

// ======================== Host launcher ========================

template <bool IsDeterministic, typename DType>
cudaError_t AirTopPRenormProb(DType* probs, DType* renormed_prob, float* top_p_arr,
                              uint32_t batch_size, float top_p_val, uint32_t d, void* workspace,
                              cudaStream_t stream = 0) {
  using HisT_ = HisT<IsDeterministic, DType>;

  int dev, smCnt;
  cudaGetDevice(&dev);
  cudaDeviceGetAttribute(&smCnt, cudaDevAttrMultiProcessorCount, dev);
  uint32_t blockNum = CalcAirTopPBlockNum<IsDeterministic, DType>(batch_size, d, smCnt);
  IdxT const bufLen = calcBufLen<DType>(d);

  auto align256 = [](size_t x) { return ((x + 255) / 256) * 256; };
  size_t countersSize = align256(sizeof(Counter<DType>) * batch_size);
  size_t histSize = align256(sizeof(HisT_) * NUM_BUCKETS * batch_size);
  size_t countHistSize = align256(sizeof(IdxT) * NUM_BUCKETS * batch_size);
  size_t buf1Size = align256(sizeof(DType) * bufLen * batch_size);

  uint8_t* ws = static_cast<uint8_t*>(workspace);
  Counter<DType>* counters = reinterpret_cast<Counter<DType>*>(ws);
  HisT_* histograms = reinterpret_cast<HisT_*>(ws + countersSize);
  IdxT* countHistograms = reinterpret_cast<IdxT*>(ws + countersSize + histSize);
  DType* buf1 = reinterpret_cast<DType*>(ws + countersSize + histSize + countHistSize);
  DType* buf2 = reinterpret_cast<DType*>(ws + countersSize + histSize + countHistSize + buf1Size);

  AirTopPRenormInitKernel<DType, HisT_><<<batch_size, 256, 0, stream>>>(
      counters, d, probs, top_p_arr, top_p_val, histograms, countHistograms);

  dim3 grid(blockNum, batch_size);
  constexpr int numPasses = NUM_PASSES<DType>;
  for (int pass = 0; pass < numPasses; ++pass) {
    AirTopPRenormRadixKernel<IsDeterministic, DType>
        <<<grid, BLOCK_SIZE, 0, stream>>>(counters, histograms, countHistograms, pass, buf1, buf2);
  }

  AirTopPRenormApplyKernel<DType>
      <<<batch_size, 1024, 0, stream>>>(probs, renormed_prob, counters, d);

  return cudaSuccess;
}

}  // namespace air_top_p
}  // namespace sampling
}  // namespace flashinfer
