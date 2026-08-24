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
#ifndef FLASHINFER_TOPK_CUH_
#define FLASHINFER_TOPK_CUH_

#include <cuda.h>

#include <cstdint>
#include <cstdlib>
#include <cub/cub.cuh>
#include <cuda/std/limits>
#include <numeric>
#include <type_traits>

#include "topk_common.cuh"
#include "utils.cuh"
#include "vec_dtypes.cuh"

namespace flashinfer {

namespace sampling {

enum class TopKTieBreak : uint32_t {
  None = 0,
  Small = 1,
  Large = 2,
};

template <uint32_t BLOCK_THREADS>
inline size_t GetRadixTopKAvailableOrderedSmemBytes(size_t max_smem_per_block,
                                                    size_t fixed_smem_aligned,
                                                    bool reserve_launch_headroom) {
  using RadixTopKDetBlockScanT =
      cub::BlockScan<uint32_t, BLOCK_THREADS, cub::BLOCK_SCAN_RAKING_MEMOIZE>;
  constexpr size_t RADIX_TOPK_DETERMINISTIC_BLOCK_SCAN_SMEM =
      sizeof(typename RadixTopKDetBlockScanT::TempStorage);
  constexpr size_t RADIX_TOPK_LAUNCH_SMEM_HEADROOM = 2 * RADIX_TOPK_DETERMINISTIC_BLOCK_SCAN_SMEM;
  const size_t launch_headroom =
      reserve_launch_headroom ? RADIX_TOPK_LAUNCH_SMEM_HEADROOM : size_t(0);
  if (max_smem_per_block <= fixed_smem_aligned + launch_headroom) {
    return 0;
  }
  // Reserve enough launch-time headroom for deterministic radix kernels that
  // instantiate additional static shared scratch such as BlockScan temp storage.
  return max_smem_per_block - fixed_smem_aligned - launch_headroom;
}

// ==================== Multi-CTA Top-K Implementation ====================

// Acquire/Release primitives for inter-CTA synchronization
__device__ __forceinline__ int ld_acquire(int* ptr) {
  int state = 0;

#if (__CUDA_ARCH__ >= 700)
  // SM70 and newer use memory consistency qualifiers
  // Acquire pattern using acquire modifier
  asm volatile("ld.global.acquire.gpu.b32 %0, [%1];\n" : "=r"(state) : "l"(ptr));
#else
  asm volatile("ld.cg.global.b32 %0, [%1];\n" : "=r"(state) : "l"(ptr));
#endif

  return state;
}

__device__ __forceinline__ void red_release(int* ptr, int val) {
#if (__CUDA_ARCH__ >= 700)
  // SM70 and newer use memory consistency qualifiers
  // Release pattern using acq_rel fence + relaxed modifier
  // (The fence also releases data that was weakly-written by other threads prior to the last
  // syncthreads)
  asm volatile("fence.acq_rel.gpu;\n");
  asm volatile("red.relaxed.gpu.global.add.s32 [%0], %1;\n" : : "l"(ptr), "r"(val));
#else
  __threadfence();
  atomicAdd(ptr, val);
#endif
}

__device__ __forceinline__ void st_release(int* ptr, int val) {
#if (__CUDA_ARCH__ >= 700)
  // SM70 and newer use memory consistency qualifiers
  // Release pattern: fence + release store
  asm volatile("fence.acq_rel.gpu;\n");
  asm volatile("st.release.gpu.global.b32 [%0], %1;\n" : : "l"(ptr), "r"(val));
#else
  __threadfence();
  atomicExch(ptr, val);
#endif
}

// Atomically add val to *ptr with release semantics, returning the old value
__device__ __forceinline__ int atom_add_release(int* ptr, int val) {
  int old_val = 0;
#if (__CUDA_ARCH__ >= 700)
  // SM70 and newer use memory consistency qualifiers
  // Release pattern using acq_rel fence + relaxed modifier
  // (The fence also releases data that was weakly-written by other threads prior to the last
  // syncthreads). The "memory" clobber keeps the compiler from moving memory accesses across
  // the fence/atomic pair.
  asm volatile("fence.acq_rel.gpu;\n" ::: "memory");
  asm volatile("atom.relaxed.gpu.global.add.s32 %0, [%1], %2;\n"
               : "=r"(old_val)
               : "l"(ptr), "r"(val)
               : "memory");
#else
  __threadfence();
  old_val = atomicAdd(ptr, val);
#endif
  return old_val;
}

// Wait until the value at ptr reaches target_val using acquire semantics
// Only thread 0 spins, then all threads synchronize
__device__ __forceinline__ void wait_ge(int* ptr, int target_val, int thread_idx) {
  if (thread_idx == 0) {
#pragma unroll 1
    while (ld_acquire(ptr) < target_val) {
    }
  }
  __syncthreads();
}

// ==================== Multi-CTA Radix Top-K Mask Logits ====================

// Global state for multi-CTA radix reduction (one per group)
struct RadixRowState {
  uint32_t histogram[3][256];  // Triple-buffered histograms for 1-barrier-per-round
  uint32_t remaining_k;        // Remaining k after current round
  uint32_t prefix;             // Accumulated prefix (high bits of k-th element)
  int arrival_counter;         // For inter-CTA synchronization
  int output_counter;          // For collecting top-k indices (RadixTopK)
  float sum_topk;              // For RenormProb: sum of top-k elements
};

constexpr uint32_t RADIX_TOPK_MAX_DETERMINISTIC_CTAS_PER_GROUP = 256;

struct RadixDeterministicCollectScratch {
  uint32_t gt_count[RADIX_TOPK_MAX_DETERMINISTIC_CTAS_PER_GROUP];
  uint32_t eq_count[RADIX_TOPK_MAX_DETERMINISTIC_CTAS_PER_GROUP];
};

inline RadixDeterministicCollectScratch* MaybeGetRadixDeterministicCollectScratchBuffer(
    RadixRowState* row_states_buffer, uint32_t num_groups, bool single_cta, bool deterministic) {
  return (single_cta || !deterministic || row_states_buffer == nullptr)
             ? nullptr
             : reinterpret_cast<RadixDeterministicCollectScratch*>(row_states_buffer + num_groups);
}

// ==================== Common Device Functions for Radix Top-K ====================
/*!
 * \brief Software barrier across all CTAs in the same radix group.
 *
 * Each CTA contributes exactly one arrival via tx==0, then waits until the
 * group-wide arrival counter reaches the current phase target.
 *
 * \param state Per-group radix row state that owns the arrival counter
 * \param barrier_phase Current software-barrier phase for this CTA group
 * \param ctas_per_group Number of CTAs participating in the group barrier
 * \param tx Thread index within the block
 */
__device__ __forceinline__ void AdvanceRadixGroupBarrier(RadixRowState* state, int& barrier_phase,
                                                         uint32_t ctas_per_group, uint32_t tx) {
  if (tx == 0) {
    red_release(&state->arrival_counter, 1);
  }
  int target = (barrier_phase + 1) * ctas_per_group;
  wait_ge(&state->arrival_counter, target, tx);
  barrier_phase++;
  __syncthreads();
}

/*!
 * \brief Reset the per-group state for the next launch, performed by the LAST CTA to finish.
 *
 * Each CTA marks its exit on the group arrival counter; the CTA that observes the final exit
 * knows every peer CTA has already passed its last wait_ge spin, so clearing the state cannot
 * strand a peer. Resetting from the leading CTA instead is racy: the leading CTA can pass the
 * final barrier, finish its tail work and zero the arrival counter while a peer CTA that has
 * already arrived is still spinning in wait_ge waiting to observe the barrier target. That
 * peer then spins on the zeroed counter forever, permanently wedging the stream (issue #3610,
 * observed as sampler hangs on SM120/SM121).
 *
 * \param state Per-group radix row state to reset
 * \param det_scratch Optional per-group deterministic-collect scratch to clear (nullptr if
 *                    unused)
 * \param barrier_phase Number of barrier phases completed by every CTA of the group
 * \param ctas_per_group Number of CTAs participating in the group
 * \param tx Thread index within the block
 */
template <uint32_t BLOCK_THREADS>
__device__ __forceinline__ void RadixGroupResetStateLastCTA(
    RadixRowState* state, RadixDeterministicCollectScratch* det_scratch, int barrier_phase,
    uint32_t ctas_per_group, uint32_t tx) {
  constexpr uint32_t RADIX = 256;
  __shared__ int s_is_last_cta;
  // Converge the CTA before tx0's release-ordered exit mark so the fence's cumulativity
  // covers every thread's prior writes (the syncthreads + thread-0 fence.acq_rel.gpu idiom
  // documented on red_release/st_release above).
  __syncthreads();
  if (tx == 0) {
    const int exit_target = (barrier_phase + 1) * static_cast<int>(ctas_per_group);
    s_is_last_cta = (atom_add_release(&state->arrival_counter, 1) + 1 == exit_target);
  }
  __syncthreads();
  if (s_is_last_cta) {
    for (uint32_t buf = 0; buf < 3; ++buf) {
      for (uint32_t i = tx; i < RADIX; i += BLOCK_THREADS) {
        state->histogram[buf][i] = 0;
      }
    }
    if (det_scratch != nullptr) {
      static_assert(sizeof(RadixDeterministicCollectScratch) % sizeof(uint32_t) == 0);
      uint32_t* det_words = reinterpret_cast<uint32_t*>(det_scratch);
      constexpr uint32_t DET_WORDS = sizeof(RadixDeterministicCollectScratch) / sizeof(uint32_t);
      for (uint32_t i = tx; i < DET_WORDS; i += BLOCK_THREADS) {
        det_words[i] = 0;
      }
    }
    // Ensure all threads' clears complete before tx0 publishes the counter reset.
    __syncthreads();
    if (tx == 0) {
      st_release(&state->arrival_counter, 0);
    }
  }
}

/*!
 * \brief Deterministically collect thread-strided matches with a full CTA scan.
 *
 * Threads traverse indices in the fixed order `tx, tx + BLOCK_THREADS, ...`, compute
 * per-thread match counts over the full strided chain, exclusive-scan those counts across
 * the CTA, then emit matches in that same deterministic thread-strided order.
 *
 * \tparam BLOCK_THREADS Number of threads in the CTA
 * \param tx Thread index within the CTA
 * \param length Number of elements to scan
 * \param scan_temp_storage CUB BlockScan temp storage reused by the caller
 * \param is_selected Predicate over the thread-strided index
 * \param emit_limit Maximum number of selected elements to emit
 * \param emit_selected Callback invoked as emit_selected(index, local_pos)
 */
template <uint32_t BLOCK_THREADS, typename TempStorage, typename Predicate, typename EmitFn>
__device__ __forceinline__ void DeterministicThreadStridedCollect(uint32_t tx, uint32_t length,
                                                                  TempStorage& scan_temp_storage,
                                                                  Predicate is_selected,
                                                                  uint32_t emit_limit,
                                                                  EmitFn emit_selected) {
  using BlockScan = cub::BlockScan<uint32_t, BLOCK_THREADS, cub::BLOCK_SCAN_RAKING_MEMOIZE>;

  uint32_t thread_local_selected_count = 0;
  for (uint32_t i = tx; i < length; i += BLOCK_THREADS) {
    thread_local_selected_count += static_cast<uint32_t>(is_selected(i));
  }

  uint32_t thread_local_selected_prefix = 0;
  BlockScan(scan_temp_storage)
      .ExclusiveSum(thread_local_selected_count, thread_local_selected_prefix);

  if (thread_local_selected_count > 0 && thread_local_selected_prefix < emit_limit) {
    uint32_t thread_local_emit_pos = thread_local_selected_prefix;
    const uint32_t thread_local_emit_end =
        min(thread_local_selected_prefix + thread_local_selected_count, emit_limit);
    for (uint32_t i = tx; i < length; i += BLOCK_THREADS) {
      if (is_selected(i)) {
        emit_selected(i, thread_local_emit_pos);
        if (++thread_local_emit_pos == thread_local_emit_end) {
          break;
        }
      }
    }
  }
  __syncthreads();
}

/*!
 * \brief Deterministically collect contiguous-order matches with a full CTA scan.
 *
 * Unlike DeterministicThreadStridedCollect, this helper traverses the row in contiguous index
 * order across the CTA. This is used for row-global tie-breaking where we must prefer either
 * smaller indices first or larger indices first for equal pivot values.
 *
 * \tparam BLOCK_THREADS Number of threads in the CTA
 * \tparam REVERSE If true, traverse indices in reverse order (length-1 ... 0)
 */
template <uint32_t BLOCK_THREADS, bool REVERSE, typename TempStorage, typename Predicate,
          typename EmitFn>
__device__ __forceinline__ void DeterministicContiguousCollect(uint32_t tx, uint32_t length,
                                                               TempStorage& scan_temp_storage,
                                                               Predicate is_selected,
                                                               uint32_t emit_limit,
                                                               EmitFn emit_selected) {
  if (emit_limit == 0 || length == 0) {
    __syncthreads();
    return;
  }
  using BlockScan = cub::BlockScan<uint32_t, BLOCK_THREADS, cub::BLOCK_SCAN_RAKING_MEMOIZE>;
  // TODO: maybe tune ITEMS_PER_THREAD and vectorize
  constexpr uint32_t ITEMS_PER_THREAD = 4;
  constexpr uint32_t CHUNK_ITEMS = BLOCK_THREADS * ITEMS_PER_THREAD;
  __shared__ uint32_t s_emitted;
  __shared__ uint32_t s_chunk_base;
  __shared__ uint32_t s_chunk_take;
  if (tx == 0) {
    s_emitted = 0;
    s_chunk_base = 0;
    s_chunk_take = 0;
  }
  __syncthreads();

  const uint32_t num_chunks = ceil_div(length, CHUNK_ITEMS);
  for (uint32_t chunk = 0; chunk < num_chunks; ++chunk) {
    uint32_t row_idx_per_item[ITEMS_PER_THREAD];
    uint32_t selected_per_item[ITEMS_PER_THREAD];
    uint32_t thread_local_selected_count = 0;

#pragma unroll
    for (uint32_t item = 0; item < ITEMS_PER_THREAD; ++item) {
      const uint32_t linear_idx = chunk * CHUNK_ITEMS + tx * ITEMS_PER_THREAD + item;
      const bool in_range = linear_idx < length;
      uint32_t row_idx = 0;
      if (in_range) {
        row_idx = REVERSE ? (length - 1u - linear_idx) : linear_idx;
      }
      row_idx_per_item[item] = row_idx;
      const uint32_t selected = (in_range && is_selected(row_idx)) ? 1u : 0u;
      selected_per_item[item] = selected;
      thread_local_selected_count += selected;
    }

    uint32_t selected_prefix = 0;
    uint32_t block_selected = 0;
    BlockScan(scan_temp_storage)
        .ExclusiveSum(thread_local_selected_count, selected_prefix, block_selected);

    if (tx == 0) {
      s_chunk_base = s_emitted;
      const uint32_t remaining = (s_emitted < emit_limit) ? (emit_limit - s_emitted) : 0u;
      s_chunk_take = min(remaining, block_selected);
      s_emitted += s_chunk_take;
    }
    __syncthreads();

    if (thread_local_selected_count > 0 && selected_prefix < s_chunk_take) {
      uint32_t thread_emit_pos = selected_prefix;
      const uint32_t thread_emit_end =
          min(selected_prefix + thread_local_selected_count, s_chunk_take);
#pragma unroll
      for (uint32_t item = 0; item < ITEMS_PER_THREAD; ++item) {
        if (selected_per_item[item]) {
          emit_selected(row_idx_per_item[item], s_chunk_base + thread_emit_pos);
          if (++thread_emit_pos == thread_emit_end) {
            break;
          }
        }
      }
    }
    __syncthreads();

    if (s_emitted >= emit_limit) {
      break;
    }
  }
  __syncthreads();
}

/*!
 * \brief Compute suffix sum in shared memory using parallel reduction.
 *
 * After this function, suffix_sum[i] contains the count of elements >= bucket i.
 * This is computed by summing all histogram values from bucket i to 255.
 *
 * \param suffix_sum Shared memory array of size RADIX (256)
 * \param tx Thread index within the block
 */
template <uint32_t BLOCK_THREADS>
__device__ __forceinline__ void RadixSuffixSum(uint32_t* suffix_sum, uint32_t tx) {
  constexpr uint32_t RADIX = 256;
  // Parallel suffix sum: compute count of elements >= each bucket
  for (uint32_t stride = 1; stride < RADIX; stride *= 2) {
    uint32_t val = 0;
    if (tx < RADIX) {
      val = suffix_sum[tx];
      if (tx + stride < RADIX) {
        val += suffix_sum[tx + stride];
      }
    }
    __syncthreads();
    if (tx < RADIX) {
      suffix_sum[tx] = val;
    }
    __syncthreads();
  }
}

/*!
 * \brief Find the threshold bucket that contains the k-th largest element.
 *
 * The threshold bucket satisfies: count_ge >= k && count_gt < k
 * where count_ge = suffix_sum[bucket] and count_gt = suffix_sum[bucket+1].
 *
 * \param suffix_sum Shared memory array containing suffix sums
 * \param remaining_k Number of top-k elements still to find
 * \param found_bucket Output: the found threshold bucket
 * \param found_remaining_k Output: remaining_k minus count of elements > threshold
 * \param tx Thread index within the block
 */
__device__ __forceinline__ void RadixFindThresholdBucket(uint32_t* suffix_sum, uint32_t remaining_k,
                                                         uint32_t* found_bucket,
                                                         uint32_t* found_remaining_k, uint32_t tx) {
  constexpr uint32_t RADIX = 256;
  // Initialize (only thread 0)
  if (tx == 0) {
    *found_bucket = 0;
    *found_remaining_k = remaining_k;
  }
  __syncthreads();

  // All threads in RADIX range check their bucket
  if (tx < RADIX) {
    uint32_t count_ge = suffix_sum[tx];
    uint32_t count_gt = (tx + 1 < RADIX) ? suffix_sum[tx + 1] : 0;
    if (count_ge >= remaining_k && count_gt < remaining_k) {
      *found_bucket = tx;
      *found_remaining_k = remaining_k - count_gt;
    }
  }
  __syncthreads();
}

/*!
 * \brief Build local histogram for one round of radix select.
 *
 * Counts elements in shared_ordered that match the current prefix and bins them
 * by their byte at the current shift position.
 *
 * \tparam OrderedType The ordered integer type (uint16_t or uint32_t)
 * \param shared_ordered Shared memory containing ordered values
 * \param actual_chunk_size Number of elements in this CTA's chunk
 * \param local_histogram Output shared memory histogram
 * \param prefix Current prefix (high bits determined so far)
 * \param shift Bit shift for extracting current byte
 * \param round Current round (0 to NUM_ROUNDS-1)
 * \param tx Thread index
 */
template <uint32_t BLOCK_THREADS, typename OrderedType>
__device__ __forceinline__ void RadixBuildLocalHistogram(const OrderedType* shared_ordered,
                                                         uint32_t actual_chunk_size,
                                                         uint32_t* local_histogram, uint32_t prefix,
                                                         uint32_t shift, uint32_t round,
                                                         uint32_t tx) {
  constexpr uint32_t ORDERED_BITS = sizeof(OrderedType) * 8;
  constexpr uint32_t RADIX_BITS = 8;

  for (uint32_t i = tx; i < actual_chunk_size; i += BLOCK_THREADS) {
    OrderedType ordered = shared_ordered[i];

    // Check if this element matches the prefix (high bits determined so far)
    OrderedType mask =
        (round == 0)
            ? OrderedType(0)
            : static_cast<OrderedType>(~OrderedType(0) << (ORDERED_BITS - round * RADIX_BITS));
    if ((ordered & mask) == static_cast<OrderedType>(prefix)) {
      uint32_t bucket = (ordered >> shift) & 0xFF;
      atomicAdd(&local_histogram[bucket], 1);
    }
  }
}

/*!
 * \brief Perform one round of radix select with optional multi-CTA synchronization.
 *
 * This is the core radix select logic used by all TopK kernels.
 * It builds histogram, aggregates across CTAs (if multi-CTA), computes suffix sum,
 * and finds the threshold bucket.
 *
 * \tparam BLOCK_THREADS Number of threads per block
 * \tparam SINGLE_CTA True if single-CTA mode (no inter-CTA sync needed)
 * \tparam OrderedType The ordered integer type
 *
 * \param shared_ordered Shared memory containing ordered values
 * \param actual_chunk_size Number of elements in this CTA's chunk
 * \param local_histogram Shared memory for local histogram (size RADIX)
 * \param suffix_sum Shared memory for suffix sum computation (size RADIX)
 * \param state Pointer to RadixRowState for multi-CTA sync (nullptr if SINGLE_CTA)
 * \param prefix Current prefix value
 * \param remaining_k Current remaining k value
 * \param round Current round (0 to NUM_ROUNDS-1)
 * \param barrier_phase Reference to barrier phase counter
 * \param ctas_per_group Number of CTAs per group
 * \param tx Thread index
 * \param out_new_prefix Output: updated prefix after this round
 * \param out_new_remaining_k Output: updated remaining_k after this round
 */
template <uint32_t BLOCK_THREADS, bool SINGLE_CTA, typename OrderedType>
__device__ __forceinline__ void RadixSelectOneRound(
    const OrderedType* shared_ordered, uint32_t actual_chunk_size, uint32_t* local_histogram,
    uint32_t* suffix_sum, uint32_t* shared_scalars, RadixRowState* state, uint32_t prefix,
    uint32_t remaining_k, uint32_t round, uint32_t iter, int& barrier_phase,
    uint32_t ctas_per_group, uint32_t cta_in_group, uint32_t tx, uint32_t* out_new_prefix,
    uint32_t* out_new_remaining_k) {
  constexpr uint32_t RADIX = 256;
  constexpr uint32_t ORDERED_BITS = sizeof(OrderedType) * 8;
  constexpr uint32_t RADIX_BITS = 8;
  constexpr uint32_t NUM_ROUNDS = ORDERED_BITS / RADIX_BITS;
  uint32_t shift = ORDERED_BITS - (round + 1) * RADIX_BITS;
  uint32_t global_round = iter * NUM_ROUNDS + round;

  // For multi-CTA: pointers to global histograms (triple buffer)
  uint32_t* current_hist = nullptr;
  uint32_t* next_hist = nullptr;
  if constexpr (!SINGLE_CTA) {
    current_hist = state->histogram[global_round % 3];
    next_hist = state->histogram[(global_round + 1) % 3];
  }

  // Clear local histogram only
  for (uint32_t i = tx; i < RADIX; i += BLOCK_THREADS) {
    local_histogram[i] = 0;
  }
  __syncthreads();

  // Build local histogram from shared memory
  RadixBuildLocalHistogram<BLOCK_THREADS, OrderedType>(shared_ordered, actual_chunk_size,
                                                       local_histogram, prefix, shift, round, tx);
  __syncthreads();

  // For multi-CTA: write -> (leading CTA clears next) -> barrier -> read
  // For single-CTA: local_histogram is already the complete histogram
  if constexpr (!SINGLE_CTA) {
    // Accumulate local histogram to global
    for (uint32_t i = tx; i < RADIX; i += BLOCK_THREADS) {
      if (local_histogram[i] > 0) {
        atomicAdd(&current_hist[i], local_histogram[i]);
      }
    }

    // Only leading CTA clears next round's histogram BEFORE barrier
    if (cta_in_group == 0) {
      for (uint32_t i = tx; i < RADIX; i += BLOCK_THREADS) {
        next_hist[i] = 0;
      }
    }

    // Barrier: wait for all CTAs to finish atomicAdd and clearing
    AdvanceRadixGroupBarrier(state, barrier_phase, ctas_per_group, tx);

    // Read current histogram (after barrier, all atomicAdds are complete)
    for (uint32_t i = tx; i < RADIX; i += BLOCK_THREADS) {
      suffix_sum[i] = current_hist[i];
    }
  } else {
    // Single-CTA: copy local histogram directly to suffix_sum
    for (uint32_t i = tx; i < RADIX; i += BLOCK_THREADS) {
      suffix_sum[i] = local_histogram[i];
    }
  }
  __syncthreads();

  // Compute suffix sum
  RadixSuffixSum<BLOCK_THREADS>(suffix_sum, tx);

  // Find threshold bucket using shared_scalars for found_bucket and found_remaining_k
  // shared_scalars[0] = found_bucket, shared_scalars[1] = found_remaining_k
  RadixFindThresholdBucket(suffix_sum, remaining_k, &shared_scalars[0], &shared_scalars[1], tx);

  // Output new prefix and remaining_k
  *out_new_prefix = prefix | (shared_scalars[0] << shift);
  *out_new_remaining_k = shared_scalars[1];
}

/*!
 * \brief Load data from global memory to shared memory and convert to ordered representation.
 *
 * This is the common Stage 1 for all TopK kernels. It loads data using vectorized
 * memory access and converts to ordered representation for radix select.
 *
 * \tparam BLOCK_THREADS Number of threads per block
 * \tparam VEC_SIZE Vector size for memory access
 * \tparam DType Data type (float, half, nv_bfloat16)
 * \tparam Traits Type traits for DType
 *
 * \param input Pointer to input data row start (already offset by row)
 * \param shared_ordered Shared memory for ordered values
 * \param chunk_start Start index within the row for this CTA's chunk
 * \param actual_chunk_size Number of elements in this CTA's chunk
 * \param tx Thread index
 */
template <uint32_t BLOCK_THREADS, uint32_t VEC_SIZE, typename DType, typename Traits>
__device__ __forceinline__ void LoadToSharedOrdered(const DType* input,
                                                    typename Traits::OrderedType* shared_ordered,
                                                    uint32_t chunk_start,
                                                    uint32_t actual_chunk_size, uint32_t tx) {
  using OrderedType = typename Traits::OrderedType;
  vec_t<DType, VEC_SIZE> input_vec;
  const uint32_t aligned_size = (actual_chunk_size / VEC_SIZE) * VEC_SIZE;

#pragma unroll 2
  for (uint32_t i = tx * VEC_SIZE; i < aligned_size; i += BLOCK_THREADS * VEC_SIZE) {
    input_vec.cast_load(input + chunk_start + i);
#pragma unroll
    for (uint32_t j = 0; j < VEC_SIZE; ++j) {
      shared_ordered[i + j] = Traits::ToOrdered(input_vec[j]);
    }
  }
  // Handle tail
  for (uint32_t i = aligned_size + tx; i < actual_chunk_size; i += BLOCK_THREADS) {
    shared_ordered[i] = Traits::ToOrdered(input[chunk_start + i]);
  }
  __syncthreads();
}

/*!
 * \brief Find the k-th largest element using radix select from pre-loaded shared memory.
 *
 * This function assumes data has already been loaded into shared_ordered.
 * It performs the complete radix select algorithm (initial barrier + NUM_ROUNDS)
 * and returns the ordered pivot value.
 *
 * \tparam BLOCK_THREADS Number of threads per block
 * \tparam SINGLE_CTA True if single-CTA mode
 * \tparam OrderedType The ordered integer type
 *
 * \param shared_ordered Shared memory containing ordered values (pre-loaded)
 * \param actual_chunk_size Number of elements in this CTA's chunk
 * \param k Number of top elements to select
 * \param local_histogram Shared memory for local histogram (size RADIX)
 * \param suffix_sum Shared memory for suffix sum (size RADIX)
 * \param shared_scalars Shared memory for scalars [prefix_cache, remaining_k_cache, found_bucket,
 * found_remaining_k, output_counter]
 * \param state RadixRowState pointer for multi-CTA sync (nullptr if SINGLE_CTA)
 * \param barrier_phase Reference to barrier phase counter
 * \param ctas_per_group Number of CTAs per group
 * \param cta_in_group CTA index within group
 * \param tx Thread index
 * \param iter Current iteration (for triple-buffer indexing)
 * \return The pivot value in ordered representation
 */
template <uint32_t BLOCK_THREADS, bool SINGLE_CTA, typename OrderedType, bool TRACK_EQ_COUNT>
__device__ __forceinline__ OrderedType RadixSelectFromSharedMemory(
    const OrderedType* shared_ordered, uint32_t actual_chunk_size, uint32_t k,
    uint32_t* local_histogram, uint32_t* suffix_sum, uint32_t* shared_scalars, RadixRowState* state,
    int& barrier_phase, uint32_t ctas_per_group, uint32_t cta_in_group, uint32_t tx, uint32_t iter,
    uint32_t& out_local_gt_count, uint32_t& out_local_eq_count) {
  constexpr uint32_t RADIX = 256;
  constexpr uint32_t RADIX_BITS = 8;
  constexpr uint32_t ORDERED_BITS = sizeof(OrderedType) * 8;
  constexpr uint32_t NUM_ROUNDS = ORDERED_BITS / RADIX_BITS;

// Aliases for scalar shared variables
#define prefix_cache shared_scalars[0]
#define remaining_k_cache shared_scalars[1]
#define found_bucket shared_scalars[2]
#define found_remaining_k shared_scalars[3]
#define shared_output_counter shared_scalars[4]

  // Initialize local caches
  if (tx == 0) {
    prefix_cache = 0;
    remaining_k_cache = k;
    if constexpr (SINGLE_CTA) {
      shared_output_counter = 0;
    }
  }
  __syncthreads();

  // Initial barrier (skip for single CTA)
  if constexpr (!SINGLE_CTA) {
    AdvanceRadixGroupBarrier(state, barrier_phase, ctas_per_group, tx);

    // CTA 0 clears output counter AFTER barrier
    if (cta_in_group == 0 && tx == 0) {
      st_release(&state->output_counter, 0);
    }
  }

  // NUM_ROUNDS of radix select
  for (uint32_t round = 0; round < NUM_ROUNDS; ++round) {
    uint32_t global_round = iter * NUM_ROUNDS + round;
    uint32_t shift = ORDERED_BITS - (round + 1) * RADIX_BITS;
    uint32_t prefix = prefix_cache;
    uint32_t remaining_k = remaining_k_cache;

    // For multi-CTA: pointers to global histograms (triple buffer)
    uint32_t* current_hist = nullptr;
    uint32_t* next_hist = nullptr;
    if constexpr (!SINGLE_CTA) {
      current_hist = state->histogram[global_round % 3];
      next_hist = state->histogram[(global_round + 1) % 3];
    }

    // Clear local histogram
    for (uint32_t i = tx; i < RADIX; i += BLOCK_THREADS) {
      local_histogram[i] = 0;
    }
    __syncthreads();

    // Build local histogram
#pragma unroll 2
    for (uint32_t i = tx; i < actual_chunk_size; i += BLOCK_THREADS) {
      OrderedType ordered = shared_ordered[i];
      OrderedType mask =
          (round == 0)
              ? OrderedType(0)
              : static_cast<OrderedType>(~OrderedType(0) << (ORDERED_BITS - round * RADIX_BITS));
      if ((ordered & mask) == static_cast<OrderedType>(prefix)) {
        uint32_t bucket = (ordered >> shift) & 0xFF;
        atomicAdd(&local_histogram[bucket], 1);
      }
    }
    __syncthreads();

    // Multi-CTA: accumulate to global, barrier, read back
    if constexpr (!SINGLE_CTA) {
      for (uint32_t i = tx; i < RADIX; i += BLOCK_THREADS) {
        if (local_histogram[i] > 0) {
          atomicAdd(&current_hist[i], local_histogram[i]);
        }
      }
      if (cta_in_group == 0) {
        for (uint32_t i = tx; i < RADIX; i += BLOCK_THREADS) {
          next_hist[i] = 0;
        }
      }
      AdvanceRadixGroupBarrier(state, barrier_phase, ctas_per_group, tx);

      for (uint32_t i = tx; i < RADIX; i += BLOCK_THREADS) {
        suffix_sum[i] = current_hist[i];
      }
    } else {
      for (uint32_t i = tx; i < RADIX; i += BLOCK_THREADS) {
        suffix_sum[i] = local_histogram[i];
      }
    }
    __syncthreads();

    // Compute suffix sum
    RadixSuffixSum<BLOCK_THREADS>(suffix_sum, tx);

    // Find threshold bucket
    if (tx == 0) {
      found_bucket = 0;
      found_remaining_k = remaining_k;
    }
    __syncthreads();

    if (tx < RADIX) {
      uint32_t count_ge = suffix_sum[tx];
      uint32_t count_gt = (tx + 1 < RADIX) ? suffix_sum[tx + 1] : 0;
      if (count_ge >= remaining_k && count_gt < remaining_k) {
        found_bucket = tx;
        found_remaining_k = remaining_k - count_gt;
      }
    }
    __syncthreads();

    // Update caches
    if (tx == 0) {
      prefix_cache = prefix | (found_bucket << shift);
      remaining_k_cache = found_remaining_k;
    }
    __syncthreads();
  }

  OrderedType ordered_pivot = static_cast<OrderedType>(prefix_cache);

  // Count > pivot (and optionally == pivot) elements by scanning shared_ordered.
  // This is needed because suffix_sum only tracks elements matching the current prefix,
  // not all elements > pivot (which includes elements with higher-order bits > pivot)
  if (tx == 0) {
    suffix_sum[0] = 0;
    if constexpr (TRACK_EQ_COUNT) {
      suffix_sum[1] = 0;
    }
  }
  __syncthreads();

  uint32_t my_gt_count = 0;
  uint32_t my_eq_count = 0;
#pragma unroll 2
  for (uint32_t i = tx; i < actual_chunk_size; i += BLOCK_THREADS) {
    const OrderedType ordered = shared_ordered[i];
    if (ordered > ordered_pivot) {
      my_gt_count++;
    }
    if constexpr (TRACK_EQ_COUNT) {
      if (ordered == ordered_pivot) {
        my_eq_count++;
      }
    }
  }

  // Warp-level reduction
  for (int offset = 16; offset > 0; offset /= 2) {
    my_gt_count += __shfl_down_sync(0xffffffff, my_gt_count, offset);
    if constexpr (TRACK_EQ_COUNT) {
      my_eq_count += __shfl_down_sync(0xffffffff, my_eq_count, offset);
    }
  }

  // First thread of each warp atomics to shared
  int lane = tx % 32;
  if (lane == 0 && my_gt_count > 0) {
    atomicAdd(&suffix_sum[0], my_gt_count);
  }
  if constexpr (TRACK_EQ_COUNT) {
    if (lane == 0 && my_eq_count > 0) {
      atomicAdd(&suffix_sum[1], my_eq_count);
    }
  }
  __syncthreads();

  out_local_gt_count = suffix_sum[0];
  if constexpr (TRACK_EQ_COUNT) {
    out_local_eq_count = suffix_sum[1];
  } else {
    out_local_eq_count = 0;
  }

#undef prefix_cache
#undef remaining_k_cache
#undef found_bucket
#undef found_remaining_k
#undef shared_output_counter

  return ordered_pivot;
}

/*!
 * \brief Load one CTA chunk into ordered shared memory, then find the pivot with radix select.
 *
 * This helper centralizes the shared-memory load and the exact k-th-element radix
 * select. It returns the pivot in ordered representation. Callers can optionally request the
 * CTA-local counts of elements
 * `> pivot` and `== pivot`, which are needed by deterministic collect paths.
 */
template <uint32_t BLOCK_THREADS, uint32_t VEC_SIZE, bool SINGLE_CTA, bool TRACK_EQ_COUNT,
          typename DType>
__device__ __forceinline__ typename RadixTopKTraits<DType>::OrderedType RadixSelectFindPivot(
    const DType* input, typename RadixTopKTraits<DType>::OrderedType* shared_ordered,
    uint32_t* local_histogram, uint32_t* suffix_sum, uint32_t* shared_scalars, RadixRowState* state,
    uint32_t chunk_start, uint32_t actual_chunk_size, uint32_t k, int& barrier_phase,
    uint32_t ctas_per_group, uint32_t cta_in_group, uint32_t tx, uint32_t iter,
    uint32_t& out_local_gt_count, uint32_t& out_local_eq_count) {
  using Traits = RadixTopKTraits<DType>;
  using OrderedType = typename Traits::OrderedType;

  LoadToSharedOrdered<BLOCK_THREADS, VEC_SIZE, DType, Traits>(input, shared_ordered, chunk_start,
                                                              actual_chunk_size, tx);
  return RadixSelectFromSharedMemory<BLOCK_THREADS, SINGLE_CTA, OrderedType, TRACK_EQ_COUNT>(
      shared_ordered, actual_chunk_size, k, local_histogram, suffix_sum, shared_scalars, state,
      barrier_phase, ctas_per_group, cta_in_group, tx, iter, out_local_gt_count,
      out_local_eq_count);
}

/*!
 * \brief Collect top-k indices based on pivot value with custom output transform (Single Pass).
 *
 * This optimized version uses a single pass to write all elements:
 * - > pivot: use shared memory atomic for local offset within CTA's allocation
 * - == pivot: use global memory atomic, check if pos < k before writing
 *
 * The local_gt_count is computed during the last round of radix select, so we know
 * exactly how many > pivot elements each CTA has. This allows batched global atomic
 * (one per CTA) for > pivot elements.
 *
 * \tparam BLOCK_THREADS Number of threads per block
 * \tparam SINGLE_CTA True if single-CTA mode
 * \tparam OrderedType The ordered integer type
 * \tparam OutputFunc Functor type: void(uint32_t original_idx, OrderedType ordered_val, int
 * output_pos)
 *
 * \param shared_ordered Shared memory containing ordered values
 * \param actual_chunk_size Number of elements in this CTA's chunk
 * \param chunk_start Start index in input for this chunk
 * \param k Number of top elements to select
 * \param ordered_pivot The pivot value in ordered representation
 * \param local_gt_count Number of > pivot elements in this CTA (from radix select)
 * \param local_histogram Shared memory for counters
 * \param shared_output_counter Pointer to shared output counter (SINGLE_CTA mode)
 * \param state RadixRowState pointer for multi-CTA sync (nullptr if SINGLE_CTA)
 * \param barrier_phase Reference to barrier phase counter (unused in new implementation)
 * \param ctas_per_group Number of CTAs per group
 * \param tx Thread index
 * \param output_func Functor called as output_func(original_idx, ordered_val, output_pos) for each
 * element
 */
template <uint32_t BLOCK_THREADS, bool SINGLE_CTA, typename OrderedType, typename OutputFunc>
__device__ __forceinline__ void RadixCollectIndices(
    const OrderedType* shared_ordered, uint32_t actual_chunk_size, uint32_t chunk_start, uint32_t k,
    OrderedType ordered_pivot, uint32_t local_gt_count, uint32_t* local_histogram,
    uint32_t* shared_output_counter, RadixRowState* state, int& barrier_phase,
    uint32_t ctas_per_group, uint32_t tx, OutputFunc output_func) {
// Use local_histogram for counters:
// [0]: local_offset_gt (local offset for > pivot elements within CTA's allocation)
// [1]: global_base_gt (global base position for > pivot)
#define local_offset_gt local_histogram[0]
#define global_base_gt local_histogram[1]

  // Get global base position for this CTA's > pivot elements (one atomic per CTA)
  if (tx == 0) {
    local_offset_gt = 0;
    if (local_gt_count > 0) {
      if constexpr (SINGLE_CTA) {
        global_base_gt = atomicAdd(shared_output_counter, local_gt_count);
      } else {
        global_base_gt = atomicAdd(&state->output_counter, local_gt_count);
      }
    }
  }
  __syncthreads();

  // Pass 1: Write elements > pivot
  // These are guaranteed to be in top-k, use local offset within CTA's allocation
#pragma unroll 2
  for (uint32_t i = tx; i < actual_chunk_size; i += BLOCK_THREADS) {
    OrderedType ordered_val = shared_ordered[i];
    if (ordered_val > ordered_pivot) {
      uint32_t local_pos = atomicAdd(&local_offset_gt, 1);
      int pos = global_base_gt + local_pos;
      output_func(chunk_start + i, ordered_val, pos);
    }
  }

  // Barrier to ensure all > pivot elements are collected first (only for multi-CTA)
  // This is critical: without this barrier, CTAs may write == pivot elements while
  // other CTAs are still writing > pivot elements, causing incorrect positions.
  if constexpr (!SINGLE_CTA) {
    AdvanceRadixGroupBarrier(state, barrier_phase, ctas_per_group, tx);
  } else {
    __syncthreads();
  }

  // Pass 2: Write elements == pivot
  // Use global atomic directly since we need cross-CTA coordination to respect
  // the k limit (some == pivot elements may be truncated).
#pragma unroll 2
  for (uint32_t i = tx; i < actual_chunk_size; i += BLOCK_THREADS) {
    OrderedType ordered_val = shared_ordered[i];
    if (ordered_val == ordered_pivot) {
      int pos;
      if constexpr (SINGLE_CTA) {
        pos = atomicAdd(shared_output_counter, 1);
      } else {
        pos = atomicAdd(&state->output_counter, 1);
      }
      if (pos < static_cast<int>(k)) {
        output_func(chunk_start + i, ordered_pivot, pos);
      }
    }
  }

#undef local_offset_gt
#undef global_base_gt
}

struct DeterministicCollectCountPair {
  uint32_t gt;
  uint32_t eq;
};

struct DeterministicCollectCountPairSum {
  __device__ __forceinline__ DeterministicCollectCountPair operator()(
      const DeterministicCollectCountPair& lhs, const DeterministicCollectCountPair& rhs) const {
    return {lhs.gt + rhs.gt, lhs.eq + rhs.eq};
  }
};

/*!
 * \brief Collect top-k indices with deterministic cross-CTA ordering.
 *
 * This variant preserves repeatable output by replacing cross-CTA atomic tie
 * claiming with a fixed allocation scheme:
 * - All > pivot elements are assigned output ranges in CTA order.
 * - == pivot elements are then assigned deterministic prefixes from
 *   per-CTA gt/eq counts stored in \p det_scratch.
 *
 * Single-CTA mode degenerates to a block-local deterministic collect without
 * using \p det_scratch.
 *
 * \tparam BLOCK_THREADS Number of threads per block
 * \tparam SINGLE_CTA True if single-CTA mode
 * \tparam OrderedType The ordered integer type
 * \tparam OutputFunc Functor type: void(uint32_t original_idx, OrderedType ordered_val, int
 * output_pos)
 *
 * \param shared_ordered Shared memory containing ordered values
 * \param actual_chunk_size Number of elements in this CTA's chunk
 * \param chunk_start Start index in input for this chunk
 * \param k Number of top elements to select
 * \param ordered_pivot The pivot value in ordered representation
 * \param cta_local_gt_count Number of > pivot elements in this CTA (from radix select)
 * \param cta_local_eq_count Number of == pivot elements in this CTA (from radix select)
 * \param local_histogram Shared memory scratch reused for deterministic collect state
 * \param state RadixRowState pointer for multi-CTA sync (nullptr if SINGLE_CTA)
 * \param det_scratch Per-group scratch for multi-CTA gt/eq counts (nullptr if SINGLE_CTA)
 * \param barrier_phase Reference to barrier phase counter
 * \param ctas_per_group Number of CTAs per group
 * \param cta_in_group CTA index within the current group
 * \param tx Thread index
 * \param output_func Functor called as output_func(original_idx, ordered_val, output_pos) for each
 * selected element
 */
template <uint32_t BLOCK_THREADS, bool SINGLE_CTA, typename OrderedType, typename OutputFunc>
__device__ __forceinline__ void RadixCollectIndicesDeterministic(
    const OrderedType* shared_ordered, uint32_t actual_chunk_size, uint32_t chunk_start, uint32_t k,
    OrderedType ordered_pivot, uint32_t cta_local_gt_count, uint32_t cta_local_eq_count,
    uint32_t* local_histogram, RadixRowState* state, RadixDeterministicCollectScratch* det_scratch,
    int& barrier_phase, uint32_t ctas_per_group, uint32_t cta_in_group, uint32_t tx,
    OutputFunc output_func) {
// Use local_histogram for counters:
// [0]: s_cta_local_gt_prefix   - total >pivot count from earlier CTAs
// [1]: s_cta_local_eq_prefix   - total ==pivot count from earlier CTAs
// [2]: s_row_total_gt_count    - row-wide >pivot count across all CTAs
// [3]: s_row_eq_needed         - number of ==pivot entries still needed after >pivot writes
// [4]: s_cta_local_eq_take     - this CTA's assigned ==pivot quota
#define s_cta_local_gt_prefix local_histogram[0]
#define s_cta_local_eq_prefix local_histogram[1]
#define s_row_total_gt_count local_histogram[2]
#define s_row_eq_needed local_histogram[3]
#define s_cta_local_eq_take local_histogram[4]
  uint32_t cta_local_eq_emit_limit = 0;
  uint32_t cta_local_eq_output_base = 0;
  if constexpr (SINGLE_CTA) {
    if (tx == 0) {
      s_cta_local_gt_prefix = 0;
      s_cta_local_eq_prefix = 0;
      s_row_total_gt_count = cta_local_gt_count;
      s_row_eq_needed = (k > cta_local_gt_count) ? (k - cta_local_gt_count) : 0;
      s_cta_local_eq_take = 0;
    }
    __syncthreads();
    // Single-CTA: keep the full ==pivot suffix contiguous after all >pivot entries.
    cta_local_eq_emit_limit = s_row_eq_needed;
    cta_local_eq_output_base = s_row_total_gt_count;
  } else {
    // Each CTA writes its local >pivot / ==pivot counts
    if (tx == 0) {
      s_cta_local_eq_prefix = 0;
      s_cta_local_eq_take = 0;
      det_scratch->gt_count[cta_in_group] = cta_local_gt_count;
      det_scratch->eq_count[cta_in_group] = cta_local_eq_count;
    }
    AdvanceRadixGroupBarrier(state, barrier_phase, ctas_per_group, tx);
    // Each CTA reads all >pivot / ==pivot counts
    if (tx == 0) {
      uint32_t cta_local_gt_prefix_accum = 0;
      uint32_t row_total_gt = 0;
      uint32_t cta_local_eq_prefix_accum = 0;
      for (uint32_t c = 0; c < ctas_per_group; ++c) {
        const uint32_t c_gt = det_scratch->gt_count[c];
        const uint32_t c_eq = det_scratch->eq_count[c];
        if (c < cta_in_group) {
          cta_local_gt_prefix_accum += c_gt;
          cta_local_eq_prefix_accum += c_eq;
        }
        row_total_gt += c_gt;
      }
      s_cta_local_gt_prefix = cta_local_gt_prefix_accum;
      s_row_total_gt_count = row_total_gt;
      s_row_eq_needed = (k > row_total_gt) ? (k - row_total_gt) : 0;
      s_cta_local_eq_prefix = cta_local_eq_prefix_accum;
      s_cta_local_eq_take = 0;
      if (s_row_eq_needed > cta_local_eq_prefix_accum) {
        s_cta_local_eq_take = min(cta_local_eq_count, s_row_eq_needed - cta_local_eq_prefix_accum);
      }
    }
    __syncthreads();
    // Multi-CTA: only emit this CTA's assigned ==pivot quota at its deterministic output base.
    cta_local_eq_emit_limit = s_cta_local_eq_take;
    cta_local_eq_output_base = s_row_total_gt_count + s_cta_local_eq_prefix;
  }
  const uint32_t cta_local_gt_output_base = s_cta_local_gt_prefix;
  const uint32_t cta_local_gt_emit_limit =
      (k > cta_local_gt_output_base) ? (k - cta_local_gt_output_base) : 0;

#undef s_cta_local_gt_prefix
#undef s_cta_local_eq_prefix
#undef s_row_total_gt_count
#undef s_row_eq_needed
#undef s_cta_local_eq_take

  using ScalarBlockScan = cub::BlockScan<uint32_t, BLOCK_THREADS, cub::BLOCK_SCAN_RAKING_MEMOIZE>;
  using PairBlockScan =
      cub::BlockScan<DeterministicCollectCountPair, BLOCK_THREADS, cub::BLOCK_SCAN_RAKING_MEMOIZE>;
  union DeterministicCollectScanTempStorage {
    typename ScalarBlockScan::TempStorage scalar;
    typename PairBlockScan::TempStorage pair;
  };
  __shared__ DeterministicCollectScanTempStorage scan_temp_storage;

  if (cta_local_eq_emit_limit == 0) {  // gt-only collect
    DeterministicThreadStridedCollect<BLOCK_THREADS>(
        tx, actual_chunk_size, scan_temp_storage.scalar,
        [&](uint32_t i) { return shared_ordered[i] > ordered_pivot; }, cta_local_gt_emit_limit,
        [&](uint32_t i, uint32_t local_pos) {
          output_func(chunk_start + i, shared_ordered[i], cta_local_gt_output_base + local_pos);
        });
    return;
  }

  // Collect gt and eq elements
  DeterministicCollectCountPair thread_local_counts = {0, 0};
  for (uint32_t i = tx; i < actual_chunk_size; i += BLOCK_THREADS) {
    const OrderedType ordered = shared_ordered[i];
    thread_local_counts.gt += static_cast<uint32_t>(ordered > ordered_pivot);
    thread_local_counts.eq += static_cast<uint32_t>(ordered == ordered_pivot);
  }

  DeterministicCollectCountPair thread_local_prefix = {0, 0};
  PairBlockScan(scan_temp_storage.pair)
      .ExclusiveScan(thread_local_counts, thread_local_prefix, DeterministicCollectCountPair{0, 0},
                     DeterministicCollectCountPairSum{});

  DeterministicCollectCountPair thread_local_pos = thread_local_prefix;
  for (uint32_t i = tx; i < actual_chunk_size; i += BLOCK_THREADS) {
    const OrderedType ordered = shared_ordered[i];
    if (ordered > ordered_pivot && thread_local_pos.gt < cta_local_gt_emit_limit) {
      output_func(chunk_start + i, ordered, cta_local_gt_output_base + thread_local_pos.gt);
      ++thread_local_pos.gt;
    } else if (ordered == ordered_pivot && thread_local_pos.eq < cta_local_eq_emit_limit) {
      output_func(chunk_start + i, ordered, cta_local_eq_output_base + thread_local_pos.eq);
      ++thread_local_pos.eq;
    }
  }
  __syncthreads();
}

// ==================== Unified Radix Top-K Kernel with Epilogue Modes ====================

/*!
 * \brief Epilogue mode for unified RadixTopK kernel.
 */
enum class RadixTopKMode {
  Basic,               ///< Returns (indices, values) pairs
  PageTableTransform,  ///< Gathers indices through page table
  RaggedTransform,     ///< Adds offset to indices
};

constexpr std::uintptr_t TOPK_MAX_VECTOR_BYTES = 16;

template <typename DType>
inline uint32_t ReduceVecSizeForPointerAlignment(const DType* ptr, uint32_t vec_size) {
  const auto address = reinterpret_cast<std::uintptr_t>(ptr);
  while (vec_size > 1 && address % (vec_size * sizeof(DType)) != 0) {
    vec_size >>= 1;
  }
  return vec_size;
}

// A page-table transform policy owns the score-row layout, logical-to-physical index mapping, and
// optional raw-index sink. The empty direct policy is carried through an empty kernel parameter
// pack, preserving the existing launch signature. A policy with runtime state is appended as one
// trivially-copyable kernel argument. Additional transforms that share the flat row-selection
// contract can implement the same interface without changing the selection kernels or adding
// another runtime flag to their signatures.
template <typename IdType>
struct DirectPageTableKernelPolicy {
  __host__ __device__ __forceinline__ uint32_t get_input_stride(uint32_t max_len) const {
    return max_len;
  }

  template <typename DType>
  inline uint32_t refine_vec_size(const DType* /*input*/, uint32_t vec_size) const {
    return vec_size;
  }

  __device__ __forceinline__ IdType* get_row_raw_output(uint32_t /*row_idx*/,
                                                        uint32_t /*top_k*/) const {
    return nullptr;
  }

  __device__ __forceinline__ IdType transform_index(const IdType* src_page_entry,
                                                    uint32_t page_table_row_start,
                                                    uint32_t raw_idx) const {
    return src_page_entry[page_table_row_start + raw_idx];
  }
};

template <typename IdType>
struct ConfigurablePageTableKernelPolicy {
  IdType* output_raw_indices;
  int64_t input_stride;
  uint32_t page_bits;

  ConfigurablePageTableKernelPolicy() = delete;
  ConfigurablePageTableKernelPolicy(IdType* output_raw_indices, int64_t input_stride,
                                    uint32_t page_bits)
      : output_raw_indices(output_raw_indices), input_stride(input_stride), page_bits(page_bits) {}

  __host__ __device__ __forceinline__ int64_t get_input_stride(uint32_t /*max_len*/) const {
    return input_stride;
  }

  template <typename DType>
  inline uint32_t refine_vec_size(const DType* input, uint32_t vec_size) const {
    return ReduceVecSizeForPointerAlignment(input, vec_size);
  }

  __device__ __forceinline__ IdType* get_row_raw_output(uint32_t row_idx, uint32_t top_k) const {
    return output_raw_indices != nullptr ? output_raw_indices + row_idx * top_k : nullptr;
  }

  __device__ __forceinline__ IdType transform_index(const IdType* src_page_entry,
                                                    uint32_t page_table_row_start,
                                                    uint32_t raw_idx) const {
    if (page_bits == 0) {
      return src_page_entry[page_table_row_start + raw_idx];
    }
    const uint32_t page_mask = (uint32_t(1) << page_bits) - 1;
    const uint32_t physical_page =
        static_cast<uint32_t>(src_page_entry[page_table_row_start + (raw_idx >> page_bits)]);
    return static_cast<IdType>((physical_page << page_bits) | (raw_idx & page_mask));
  }
};

// Keep the direct policy identifiable so the hot selection-kernel epilogues can retain their
// original source form where routing through an inlined policy method changes NVCC-generated SASS.
template <typename PageTablePolicy, typename IdType>
inline constexpr bool IsDirectPageTableKernelPolicy =
    std::is_same_v<PageTablePolicy, DirectPageTableKernelPolicy<IdType>>;

// Empty policies are omitted from the kernel ABI and therefore must be default-constructible.
template <typename PageTablePolicy>
inline constexpr bool IsValidPageTablePolicy =
    std::is_trivially_copyable_v<PageTablePolicy> &&
    std::is_copy_constructible_v<PageTablePolicy> && std::is_standard_layout_v<PageTablePolicy> &&
    (!std::is_empty_v<PageTablePolicy> || std::is_default_constructible_v<PageTablePolicy>);

template <typename PageTablePolicy, typename... PageTableArgs>
inline constexpr bool IsValidPageTableKernelArgs =
    IsValidPageTablePolicy<PageTablePolicy> &&
    sizeof...(PageTableArgs) == (std::is_empty_v<PageTablePolicy> ? 0 : 1) &&
    (std::is_same_v<std::decay_t<PageTableArgs>, PageTablePolicy> && ...);

/*!
 * \brief Unified Multi-CTA Radix Top-K kernel with mode-specific epilogues.
 *
 * This kernel unifies three top-k variants:
 * - Basic: Returns top-k indices and values
 * - PageTableTransform: Gathers top-k indices through a page table
 * - RaggedTransform: Adds per-row offset to top-k indices
 *
 * \tparam BLOCK_THREADS Number of threads per block
 * \tparam VEC_SIZE Vector size for memory access
 * \tparam SINGLE_CTA True if single-CTA mode
 * \tparam DETERMINISTIC True to use deterministic collect path
 * \tparam MODE Epilogue mode (Basic, PageTableTransform, or RaggedTransform)
 * \tparam DType Data type (float, half, nv_bfloat16)
 * \tparam IdType Index type
 * \tparam PageTablePolicy Index-layout and optional-output policy
 * \tparam PageTableArgs Policy arguments; empty when the policy has no runtime state
 */
template <uint32_t BLOCK_THREADS, uint32_t VEC_SIZE, bool SINGLE_CTA, bool DETERMINISTIC,
          RadixTopKMode MODE, typename DType, typename IdType,
          typename PageTablePolicy = DirectPageTableKernelPolicy<IdType>,
          typename... PageTableArgs>
__global__ void __launch_bounds__(BLOCK_THREADS) RadixTopKKernel_Unified(
    DType* input,            // [num_rows, stride]
    IdType* output_indices,  // [num_rows, top_k] - indices or page table entries
    DType* output_values,    // [num_rows, top_k] - only used in Basic mode, nullptr otherwise
    const IdType*
        aux_data,  // Mode-specific: top_k_arr (Basic), src_page_table (PageTable), offsets (Ragged)
    IdType* lengths,                      // [num_rows] lengths, nullptr for Basic
    const IdType* row_starts,             // [num_rows] score starts, nullptr => 0
    const IdType* page_table_row_starts,  // [num_rows] page-table starts
    const IdType* row_to_batch,           // [num_rows] batch mapping for PageTable
    int64_t aux_stride,                   // src_page_table stride for PageTable
    uint32_t top_k_val, uint32_t max_len, uint32_t num_rows, RadixRowState* row_states,
    RadixDeterministicCollectScratch* det_scratches, uint32_t chunk_size, uint32_t ctas_per_group,
    PageTableArgs... page_table_args) {
  static_assert(MODE == RadixTopKMode::PageTableTransform ||
                IsDirectPageTableKernelPolicy<PageTablePolicy, IdType>);
  static_assert(IsValidPageTableKernelArgs<PageTablePolicy, PageTableArgs...>);
  using Traits = RadixTopKTraits<DType>;
  using OrderedType = typename Traits::OrderedType;
  constexpr uint32_t RADIX = 256;
  const PageTablePolicy page_table_policy{page_table_args...};

  const uint32_t global_cta_id = blockIdx.x;
  const uint32_t group_id = global_cta_id / ctas_per_group;
  const uint32_t cta_in_group = global_cta_id % ctas_per_group;
  const uint32_t tx = threadIdx.x;

  extern __shared__ uint8_t smem[];

  constexpr size_t num_scalars = SINGLE_CTA ? 5 : 4;
  constexpr size_t fixed_smem_size = sizeof(uint32_t) * (RADIX + RADIX + num_scalars);
  uint32_t* local_histogram = reinterpret_cast<uint32_t*>(smem);
  uint32_t* suffix_sum = local_histogram + RADIX;
  uint32_t* shared_scalars = suffix_sum + RADIX;

  size_t ordered_offset = ((fixed_smem_size + 15) / 16) * 16;
  OrderedType* shared_ordered = reinterpret_cast<OrderedType*>(smem + ordered_offset);

#define shared_output_counter shared_scalars[4]

  RadixRowState* state = nullptr;
  if constexpr (!SINGLE_CTA) {
    state = &row_states[group_id];
  }
  RadixDeterministicCollectScratch* det_scratch = nullptr;
  if constexpr (!SINGLE_CTA && DETERMINISTIC) {
    det_scratch = &det_scratches[group_id];
  }
  uint32_t num_groups = gridDim.x / ctas_per_group;
  uint32_t total_iterations = (num_rows + num_groups - 1) / num_groups;
  const auto row_stride = page_table_policy.get_input_stride(max_len);

  int barrier_phase = 0;

  for (uint32_t iter = 0; iter < total_iterations; iter++) {
    uint32_t row_idx = group_id + iter * num_groups;
    if (row_idx >= num_rows) break;
    const uint32_t row_start =
        (row_starts != nullptr && MODE != RadixTopKMode::Basic) ? row_starts[row_idx] : 0;
    const uint32_t page_table_row_start =
        (page_table_row_starts != nullptr && MODE == RadixTopKMode::PageTableTransform)
            ? page_table_row_starts[row_idx]
            : row_start;
    DType* row_input = input + static_cast<size_t>(row_idx) * row_stride + row_start;

    // Mode-specific: get row length and k value
    uint32_t length, k;
    if constexpr (MODE == RadixTopKMode::Basic) {
      length = max_len;                                           // Fixed length for all rows
      k = (aux_data != nullptr) ? aux_data[row_idx] : top_k_val;  // aux_data = top_k_arr
    } else {
      length = lengths[row_idx];  // Per-row length
      k = top_k_val;              // Fixed k
    }

    // Mode-specific: output pointers and auxiliary data
    IdType* row_output = output_indices + row_idx * top_k_val;
    IdType* row_raw_output = nullptr;
    if constexpr (!IsDirectPageTableKernelPolicy<PageTablePolicy, IdType>) {
      row_raw_output = page_table_policy.get_row_raw_output(row_idx, top_k_val);
    }

    // Handle trivial cases
    if constexpr (MODE == RadixTopKMode::Basic) {
      if (k >= length) {
        // k >= vocab_size: return all indices
        const uint32_t chunk_start = cta_in_group * chunk_size;
        const uint32_t chunk_end = min(chunk_start + chunk_size, length);
        const uint32_t actual_chunk_size = ((chunk_start < length) ? (chunk_end - chunk_start) : 0);

        for (uint32_t i = tx; i < actual_chunk_size; i += BLOCK_THREADS) {
          if (chunk_start + i < k) {
            row_output[chunk_start + i] = static_cast<IdType>(chunk_start + i);
            if (output_values != nullptr) {
              output_values[row_idx * top_k_val + chunk_start + i] =
                  input[static_cast<size_t>(row_idx) * row_stride + chunk_start + i];
            }
          }
        }
        // Clear histogram for next iteration (in case it's k < length)
        if constexpr (!SINGLE_CTA) {
          constexpr uint32_t NUM_ROUNDS = sizeof(OrderedType) * 8 / 8;
          uint32_t next_first_hist_idx = ((iter + 1) * NUM_ROUNDS) % 3;
          if (cta_in_group == 0) {
            for (uint32_t i = tx; i < RADIX; i += BLOCK_THREADS) {
              state->histogram[next_first_hist_idx][i] = 0;
            }
          }
        }
        continue;
      }
    } else if constexpr (MODE == RadixTopKMode::PageTableTransform) {
      uint32_t batch_idx = (row_to_batch != nullptr) ? row_to_batch[row_idx] : row_idx;
      const IdType* src_page_entry = aux_data + batch_idx * aux_stride;
      if (length <= top_k_val) {
        for (uint32_t i = tx; i < top_k_val; i += BLOCK_THREADS) {
          if constexpr (IsDirectPageTableKernelPolicy<PageTablePolicy, IdType>) {
            row_output[i] =
                (i < length) ? src_page_entry[page_table_row_start + i] : static_cast<IdType>(-1);
          } else {
            const IdType raw_idx = (i < length) ? static_cast<IdType>(i) : static_cast<IdType>(-1);
            if (row_raw_output != nullptr) {
              row_raw_output[i] = raw_idx;
            }
            row_output[i] = raw_idx >= 0 ? page_table_policy.transform_index(
                                               src_page_entry, page_table_row_start,
                                               static_cast<uint32_t>(raw_idx))
                                         : static_cast<IdType>(-1);
          }
        }
        // Clear histogram for next iteration
        if constexpr (!SINGLE_CTA) {
          constexpr uint32_t NUM_ROUNDS = sizeof(OrderedType) * 8 / 8;
          uint32_t next_first_hist_idx = ((iter + 1) * NUM_ROUNDS) % 3;
          if (cta_in_group == 0) {
            for (uint32_t i = tx; i < RADIX; i += BLOCK_THREADS) {
              state->histogram[next_first_hist_idx][i] = 0;
            }
          }
        }
        continue;
      }
    } else {  // RaggedTransform
      IdType offset = aux_data[row_idx];
      if (length <= top_k_val) {
        for (uint32_t i = tx; i < top_k_val; i += BLOCK_THREADS) {
          row_output[i] = (i < length) ? static_cast<IdType>(i) + offset : static_cast<IdType>(-1);
        }
        // Clear histogram for next iteration
        if constexpr (!SINGLE_CTA) {
          constexpr uint32_t NUM_ROUNDS = sizeof(OrderedType) * 8 / 8;
          uint32_t next_first_hist_idx = ((iter + 1) * NUM_ROUNDS) % 3;
          if (cta_in_group == 0) {
            for (uint32_t i = tx; i < RADIX; i += BLOCK_THREADS) {
              state->histogram[next_first_hist_idx][i] = 0;
            }
          }
        }
        continue;
      }
    }

    const uint32_t chunk_start = cta_in_group * chunk_size;
    const uint32_t chunk_end = min(chunk_start + chunk_size, length);
    const uint32_t actual_chunk_size = ((chunk_start < length) ? (chunk_end - chunk_start) : 0);

    // Stage 1: Load the chunk into shared memory, then radix-select the pivot.
    uint32_t cta_local_gt_count = 0;
    uint32_t cta_local_eq_count = 0;
    OrderedType ordered_pivot =
        RadixSelectFindPivot<BLOCK_THREADS, VEC_SIZE, SINGLE_CTA, DETERMINISTIC, DType>(
            row_input, shared_ordered, local_histogram, suffix_sum, shared_scalars, state,
            chunk_start, actual_chunk_size, k, barrier_phase, ctas_per_group, cta_in_group, tx,
            iter, cta_local_gt_count, cta_local_eq_count);

    auto collect_indices = [&](auto&& output_func) {
      if constexpr (DETERMINISTIC) {
        RadixCollectIndicesDeterministic<BLOCK_THREADS, SINGLE_CTA, OrderedType>(
            shared_ordered, actual_chunk_size, chunk_start, k, ordered_pivot, cta_local_gt_count,
            cta_local_eq_count, local_histogram, state, det_scratch, barrier_phase, ctas_per_group,
            cta_in_group, tx, output_func);
      } else {
        RadixCollectIndices<BLOCK_THREADS, SINGLE_CTA, OrderedType>(
            shared_ordered, actual_chunk_size, chunk_start, k, ordered_pivot, cta_local_gt_count,
            local_histogram, &shared_output_counter, state, barrier_phase, ctas_per_group, tx,
            output_func);
      }
    };

    // Stage 2: Collect indices with mode-specific epilogue (single pass)
    if constexpr (MODE == RadixTopKMode::Basic) {
      // Use a single collect_indices instantiation (one lambda type) with a runtime
      // null-check on the value pointer. Instantiating collect_indices twice (a values
      // and a no-values lambda) would give each its own __shared__ scan temp storage,
      // doubling the deterministic collect's static shared memory and overflowing the
      // per-block opt-in cap on GPUs with a smaller limit (Ada/sm_89: ~99KB), which
      // fails the launch with cudaErrorInvalidValue.
      DType* row_output_values =
          (output_values != nullptr) ? output_values + row_idx * top_k_val : nullptr;
      collect_indices([&](uint32_t original_idx, OrderedType ordered_val, int pos) {
        row_output[pos] = static_cast<IdType>(original_idx);
        if (row_output_values != nullptr) {
          row_output_values[pos] = Traits::FromOrdered(ordered_val);
        }
      });
    } else if constexpr (MODE == RadixTopKMode::PageTableTransform) {
      uint32_t batch_idx = (row_to_batch != nullptr) ? row_to_batch[row_idx] : row_idx;
      const IdType* src_page_entry = aux_data + batch_idx * aux_stride;

      // Collect raw indices first
      collect_indices([&](uint32_t original_idx, OrderedType /*ordered_val*/, int pos) {
        row_output[pos] = static_cast<IdType>(original_idx);
      });

      if constexpr (SINGLE_CTA) {
        __syncthreads();
        // Transform through page table with coalesced access
        for (uint32_t i = tx; i < k; i += BLOCK_THREADS) {
          IdType idx = row_output[i];
          if constexpr (IsDirectPageTableKernelPolicy<PageTablePolicy, IdType>) {
            row_output[i] = src_page_entry[page_table_row_start + idx];
          } else {
            if (row_raw_output != nullptr) {
              row_raw_output[i] = idx;
            }
            row_output[i] = page_table_policy.transform_index(src_page_entry, page_table_row_start,
                                                              static_cast<uint32_t>(idx));
          }
        }
      } else {
        // Barrier to ensure all CTAs finished writing indices
        AdvanceRadixGroupBarrier(state, barrier_phase, ctas_per_group, tx);

        // All CTAs participate in page table transform (coalesced access)
        uint32_t elems_per_cta = (k + ctas_per_group - 1) / ctas_per_group;
        uint32_t my_start = cta_in_group * elems_per_cta;
        uint32_t my_end = min(my_start + elems_per_cta, k);
        for (uint32_t i = my_start + tx; i < my_end; i += BLOCK_THREADS) {
          IdType idx = row_output[i];
          if constexpr (IsDirectPageTableKernelPolicy<PageTablePolicy, IdType>) {
            row_output[i] = src_page_entry[page_table_row_start + idx];
          } else {
            if (row_raw_output != nullptr) {
              row_raw_output[i] = idx;
            }
            row_output[i] = page_table_policy.transform_index(src_page_entry, page_table_row_start,
                                                              static_cast<uint32_t>(idx));
          }
        }
      }
    } else {  // RaggedTransform
      IdType offset = aux_data[row_idx];
      collect_indices([&](uint32_t original_idx, OrderedType /*ordered_val*/, int pos) {
        row_output[pos] = static_cast<IdType>(original_idx) + offset;
      });
    }
  }

  // Reset group state for the next launch, from the last CTA to exit (only for multi-CTA).
  // The leading CTA must not do this: it could zero the arrival counter while a peer CTA is
  // still spinning in its final wait_ge, wedging the stream forever (issue #3610).
  if constexpr (!SINGLE_CTA) {
    RadixGroupResetStateLastCTA<BLOCK_THREADS>(state, DETERMINISTIC ? det_scratch : nullptr,
                                               barrier_phase, ctas_per_group, tx);
  }

#undef shared_output_counter
}

template <uint32_t BLOCK_THREADS, uint32_t VEC_SIZE, bool SINGLE_CTA, typename DType,
          typename IdType>
__global__ void __launch_bounds__(BLOCK_THREADS) RadixTopKMaskLogitsKernel_MultiCTA(
    DType* logits,         // [batch, vocab_size]
    DType* masked_logits,  // [batch, vocab_size]
    IdType* top_k_arr,     // [batch] or nullptr
    uint32_t top_k_val, uint32_t vocab_size, uint32_t batch_size,
    RadixRowState* row_states,  // [num_groups] (nullptr if SINGLE_CTA)
    uint32_t chunk_size,        // elements per CTA
    uint32_t ctas_per_group)    // CTAs per row (1 if SINGLE_CTA)
{
  // Type traits for FP16/BF16/FP32 support
  using Traits = RadixTopKTraits<DType>;
  using OrderedType = typename Traits::OrderedType;

  constexpr uint32_t RADIX = 256;  // 8-bit radix

  const uint32_t global_cta_id = blockIdx.x;
  const uint32_t group_id = global_cta_id / ctas_per_group;
  const uint32_t cta_in_group = global_cta_id % ctas_per_group;
  const uint32_t tx = threadIdx.x;

  // Shared memory layout: [fixed storage] [ordered values cache]
  extern __shared__ uint8_t smem[];

  // Fixed shared memory (at the beginning)
  // histogram[256] + suffix[256] + 5 scalars (for RadixSelectFromSharedMemory)
  constexpr size_t fixed_smem_size = sizeof(uint32_t) * (RADIX + RADIX + 5);
  uint32_t* local_histogram = reinterpret_cast<uint32_t*>(smem);
  uint32_t* suffix_sum = local_histogram + RADIX;
  uint32_t* shared_scalars = suffix_sum + RADIX;

  // Align ordered values cache to 16 bytes
  size_t ordered_offset = ((fixed_smem_size + 15) / 16) * 16;
  OrderedType* shared_ordered = reinterpret_cast<OrderedType*>(smem + ordered_offset);

  // State pointer only used when not SINGLE_CTA
  RadixRowState* state = nullptr;
  if constexpr (!SINGLE_CTA) {
    state = &row_states[group_id];
  }

  // Calculate total number of iterations for persistent loop
  uint32_t num_groups = gridDim.x / ctas_per_group;
  uint32_t total_iterations = (batch_size + num_groups - 1) / num_groups;

  int barrier_phase = 0;

  // Persistent loop over rows
  for (uint32_t iter = 0; iter < total_iterations; iter++) {
    uint32_t row_idx = group_id + iter * num_groups;

    if (row_idx >= batch_size) break;

    const uint32_t chunk_start = cta_in_group * chunk_size;
    const uint32_t chunk_end = min(chunk_start + chunk_size, vocab_size);
    const uint32_t actual_chunk_size = chunk_end - chunk_start;

    uint32_t k = top_k_arr == nullptr ? top_k_val : top_k_arr[row_idx];

    DType pivot = Traits::NegInf();

    if (k >= vocab_size) {
      // k >= vocab_size: no masking needed, just copy
      vec_t<DType, VEC_SIZE> logits_vec_copy;
      const uint32_t aligned_size = (actual_chunk_size / VEC_SIZE) * VEC_SIZE;
#pragma unroll 2
      for (uint32_t i = tx * VEC_SIZE; i < aligned_size; i += BLOCK_THREADS * VEC_SIZE) {
        logits_vec_copy.cast_load(logits + row_idx * vocab_size + chunk_start + i);
        logits_vec_copy.store(masked_logits + row_idx * vocab_size + chunk_start + i);
      }
      // Handle tail
      for (uint32_t i = aligned_size + tx; i < actual_chunk_size; i += BLOCK_THREADS) {
        masked_logits[row_idx * vocab_size + chunk_start + i] =
            logits[row_idx * vocab_size + chunk_start + i];
      }

      // Clear histogram for next iteration (in case it's k < vocab_size)
      // Only needed for multi-CTA mode; single-CTA uses shared memory cleared each iteration
      if constexpr (!SINGLE_CTA) {
        constexpr uint32_t NUM_ROUNDS = sizeof(OrderedType) * 8 / 8;  // ORDERED_BITS / RADIX_BITS
        uint32_t next_first_hist_idx = ((iter + 1) * NUM_ROUNDS) % 3;
        if (cta_in_group == 0) {
          for (uint32_t i = tx; i < RADIX; i += BLOCK_THREADS) {
            state->histogram[next_first_hist_idx][i] = 0;
          }
        }
        // No sync needed - next iteration's barrier will ensure visibility
      }
      continue;
    }

    // Stage 1: Load the chunk into shared memory, then radix-select the pivot.
    uint32_t local_gt_count = 0;  // Not used in this kernel
    uint32_t local_eq_count = 0;  // Not used in this kernel
    OrderedType ordered_pivot =
        RadixSelectFindPivot<BLOCK_THREADS, VEC_SIZE, SINGLE_CTA, false, DType>(
            logits + row_idx * vocab_size, shared_ordered, local_histogram, suffix_sum,
            shared_scalars, state, chunk_start, actual_chunk_size, k, barrier_phase, ctas_per_group,
            cta_in_group, tx, iter, local_gt_count, local_eq_count);

    pivot = Traits::FromOrdered(ordered_pivot);

    // Stage 2: Final masking pass
    const DType neg_inf = Traits::NegInf();
    const uint32_t aligned_size = (actual_chunk_size / VEC_SIZE) * VEC_SIZE;
    vec_t<DType, VEC_SIZE> logits_vec;

#pragma unroll 2
    for (uint32_t i = tx * VEC_SIZE; i < aligned_size; i += BLOCK_THREADS * VEC_SIZE) {
      logits_vec.cast_load(logits + row_idx * vocab_size + chunk_start + i);
#pragma unroll
      for (uint32_t j = 0; j < VEC_SIZE; ++j) {
        logits_vec[j] = (logits_vec[j] >= pivot) ? logits_vec[j] : neg_inf;
      }
      logits_vec.store(masked_logits + row_idx * vocab_size + chunk_start + i);
    }

    // Handle tail
    for (uint32_t i = aligned_size + tx; i < actual_chunk_size; i += BLOCK_THREADS) {
      DType val = logits[row_idx * vocab_size + chunk_start + i];
      masked_logits[row_idx * vocab_size + chunk_start + i] = (val >= pivot) ? val : neg_inf;
    }
  }

  // Reset group state for the next launch, from the last CTA to exit (only for multi-CTA).
  // The leading CTA must not do this: it could zero the arrival counter while a peer CTA is
  // still spinning in its final wait_ge, wedging the stream forever (issue #3610).
  if constexpr (!SINGLE_CTA) {
    RadixGroupResetStateLastCTA<BLOCK_THREADS>(state, nullptr, barrier_phase, ctas_per_group, tx);
  }
}

template <typename DType, typename IdType>
cudaError_t RadixTopKMaskLogitsMultiCTA(DType* logits, DType* masked_logits, IdType* top_k_arr,
                                        uint32_t batch_size, uint32_t top_k_val,
                                        uint32_t vocab_size, RadixRowState* row_states_buffer,
                                        cudaStream_t stream = 0) {
  using OrderedType = typename RadixTopKTraits<DType>::OrderedType;
  constexpr uint32_t BLOCK_THREADS = 1024;
  const uint32_t vec_size = std::gcd(16 / sizeof(DType), vocab_size);

  // Get device properties
  int device;
  FLASHINFER_CUDA_CALL(cudaGetDevice(&device));
  int num_sms;
  FLASHINFER_CUDA_CALL(cudaDeviceGetAttribute(&num_sms, cudaDevAttrMultiProcessorCount, device));
  int max_smem_per_block;
  FLASHINFER_CUDA_CALL(
      cudaDeviceGetAttribute(&max_smem_per_block, cudaDevAttrMaxSharedMemoryPerBlockOptin, device));

  // Fixed shared memory overhead: histogram[256] + suffix_sum[256] + 5 scalars
  constexpr size_t fixed_smem_size = sizeof(uint32_t) * (256 + 256 + 5);
  constexpr size_t fixed_smem_aligned = round_up(fixed_smem_size, 16);

  // Calculate max chunk size that fits in shared memory
  const size_t available_for_ordered = GetRadixTopKAvailableOrderedSmemBytes<BLOCK_THREADS>(
      max_smem_per_block, fixed_smem_aligned, false);
  if (available_for_ordered == 0) {
    return cudaErrorInvalidValue;
  }
  uint32_t max_chunk_elements = available_for_ordered / sizeof(OrderedType);
  max_chunk_elements = round_down(max_chunk_elements, vec_size);
  const uint32_t min_chunk_size = vec_size * BLOCK_THREADS;
  max_chunk_elements = std::max(max_chunk_elements, min_chunk_size);

  uint32_t ctas_per_group = ceil_div(vocab_size, max_chunk_elements);
  uint32_t chunk_size = ceil_div(vocab_size, ctas_per_group);
  chunk_size = round_up(chunk_size, vec_size);
  chunk_size = std::min(chunk_size, max_chunk_elements);

  const uint32_t smem_size = fixed_smem_aligned + chunk_size * sizeof(OrderedType);
  const bool single_cta = (ctas_per_group == 1);

  // The multi-CTA path uses a software barrier whose participants must all be
  // resident concurrently.  Low-SM GPUs cannot provide that guarantee once a
  // row spans multiple CTAs, so fail before launching instead of risking a
  // permanent stream hang.  The barrier-free single-CTA path remains enabled.
  if (!single_cta && num_sms <= 16) {
    return cudaErrorNotSupported;
  }

  // Calculate number of groups (how many rows to process concurrently)
  uint32_t num_groups = std::min(static_cast<uint32_t>(num_sms) / ctas_per_group, batch_size);
  if (num_groups == 0) num_groups = 1;
  uint32_t total_ctas = num_groups * ctas_per_group;

  DISPATCH_ALIGNED_VEC_SIZE(vec_size, VEC_SIZE, {
    if (single_cta) {
      auto kernel =
          RadixTopKMaskLogitsKernel_MultiCTA<BLOCK_THREADS, VEC_SIZE, true, DType, IdType>;
      FLASHINFER_CUDA_CALL(
          cudaFuncSetAttribute(kernel, cudaFuncAttributeMaxDynamicSharedMemorySize, smem_size));

      dim3 nblks(total_ctas);
      dim3 nthrs(BLOCK_THREADS);
      void* args[] = {&logits,     &masked_logits,     &top_k_arr,  &top_k_val,     &vocab_size,
                      &batch_size, &row_states_buffer, &chunk_size, &ctas_per_group};
      FLASHINFER_CUDA_CALL(cudaLaunchKernel((void*)kernel, nblks, nthrs, args, smem_size, stream));
    } else {
      auto kernel =
          RadixTopKMaskLogitsKernel_MultiCTA<BLOCK_THREADS, VEC_SIZE, false, DType, IdType>;
      FLASHINFER_CUDA_CALL(
          cudaFuncSetAttribute(kernel, cudaFuncAttributeMaxDynamicSharedMemorySize, smem_size));
      dim3 nblks(total_ctas);
      dim3 nthrs(BLOCK_THREADS);
      void* args[] = {&logits,     &masked_logits,     &top_k_arr,  &top_k_val,     &vocab_size,
                      &batch_size, &row_states_buffer, &chunk_size, &ctas_per_group};
      FLASHINFER_CUDA_CALL(cudaLaunchKernel((void*)kernel, nblks, nthrs, args, smem_size, stream));
    }
  });

  return cudaSuccess;
}

// ==================== Multi-CTA Radix Top-K Renorm Probs ====================

/*!
 * \brief Multi-CTA Radix Top-K RenormProb kernel with unified single/multi-CTA paths.
 *
 * Finds the k-th largest probability, then normalizes all probs >= pivot to sum to 1,
 * setting all others to 0. Reuses the shared load+radix-select helper.
 */
template <uint32_t BLOCK_THREADS, uint32_t VEC_SIZE, bool SINGLE_CTA, typename DType,
          typename IdType>
__global__ void __launch_bounds__(BLOCK_THREADS) RadixTopKRenormProbKernel_MultiCTA(
    DType* probs,          // [batch, vocab_size]
    DType* renormed_prob,  // [batch, vocab_size]
    IdType* top_k_arr,     // [batch] or nullptr
    uint32_t top_k_val, uint32_t vocab_size, uint32_t batch_size,
    RadixRowState* row_states,  // [num_groups] (nullptr if SINGLE_CTA)
    uint32_t chunk_size,        // elements per CTA
    uint32_t ctas_per_group)    // CTAs per row (1 if SINGLE_CTA)
{
  using Traits = RadixTopKTraits<DType>;
  using OrderedType = typename Traits::OrderedType;

  constexpr uint32_t RADIX = 256;  // 8-bit radix

  const uint32_t global_cta_id = blockIdx.x;
  const uint32_t group_id = global_cta_id / ctas_per_group;
  const uint32_t cta_in_group = global_cta_id % ctas_per_group;
  const uint32_t tx = threadIdx.x;

  // Shared memory layout: [fixed storage] [ordered values cache]
  extern __shared__ uint8_t smem[];

  // Fixed shared memory (at the beginning)
  // histogram[256] + suffix[256] + scalars[4] + sum_local[1]
  constexpr size_t fixed_smem_size = sizeof(uint32_t) * (RADIX + RADIX + 4) + sizeof(float);
  uint32_t* local_histogram = reinterpret_cast<uint32_t*>(smem);
  uint32_t* suffix_sum = local_histogram + RADIX;
  uint32_t* shared_scalars = suffix_sum + RADIX;
  float* shared_sum = reinterpret_cast<float*>(shared_scalars + 4);

  // Align ordered values cache to 16 bytes
  size_t ordered_offset = ((fixed_smem_size + 15) / 16) * 16;
  OrderedType* shared_ordered = reinterpret_cast<OrderedType*>(smem + ordered_offset);

  // State pointer only used when not SINGLE_CTA
  RadixRowState* state = nullptr;
  if constexpr (!SINGLE_CTA) {
    state = &row_states[group_id];
  }

  // Calculate total number of iterations for persistent loop
  uint32_t num_groups = gridDim.x / ctas_per_group;
  uint32_t total_iterations = (batch_size + num_groups - 1) / num_groups;

  int barrier_phase = 0;

  // Persistent loop over rows
  for (uint32_t iter = 0; iter < total_iterations; iter++) {
    uint32_t row_idx = group_id + iter * num_groups;

    if (row_idx >= batch_size) break;

    const uint32_t chunk_start = cta_in_group * chunk_size;
    const uint32_t chunk_end = min(chunk_start + chunk_size, vocab_size);
    const uint32_t actual_chunk_size = chunk_end - chunk_start;

    uint32_t k = top_k_arr == nullptr ? top_k_val : top_k_arr[row_idx];

    // For RenormProb, pivot is compared with probs (must be non-negative)
    DType pivot = DType(0);
    float normalizer = 1.0f;

    if (k >= vocab_size) {
      // k >= vocab_size: no filtering needed, just compute sum and renormalize
      // Stage 1: Compute sum
      float thread_sum = 0.0f;
      vec_t<DType, VEC_SIZE> data_vec;
      const uint32_t aligned_size = (actual_chunk_size / VEC_SIZE) * VEC_SIZE;

#pragma unroll 2
      for (uint32_t i = tx * VEC_SIZE; i < aligned_size; i += BLOCK_THREADS * VEC_SIZE) {
        data_vec.cast_load(probs + row_idx * vocab_size + chunk_start + i);
#pragma unroll
        for (uint32_t j = 0; j < VEC_SIZE; ++j) {
          thread_sum += float(data_vec[j]);
        }
      }
      // Handle tail
      for (uint32_t i = aligned_size + tx; i < actual_chunk_size; i += BLOCK_THREADS) {
        thread_sum += float(probs[row_idx * vocab_size + chunk_start + i]);
      }

      // Block reduction for sum
      typedef cub::BlockReduce<float, BLOCK_THREADS> BlockReduce;
      __shared__ typename BlockReduce::TempStorage temp_storage;
      float block_sum = BlockReduce(temp_storage).Sum(thread_sum);
      __syncthreads();

      if constexpr (!SINGLE_CTA) {
        // Multi-CTA: atomic add to global sum
        if (tx == 0) {
          if (cta_in_group == 0) {
            state->sum_topk = 0.0f;  // First CTA initializes
          }
        }
        // Barrier for initialization
        AdvanceRadixGroupBarrier(state, barrier_phase, ctas_per_group, tx);

        if (tx == 0 && block_sum > 0) {
          atomicAdd(&state->sum_topk, block_sum);
        }

        // Barrier to ensure all CTAs have contributed
        AdvanceRadixGroupBarrier(state, barrier_phase, ctas_per_group, tx);
        normalizer = math::ptx_rcp(max(state->sum_topk, 1e-8f));
      } else {
        // Single-CTA: use block_sum directly
        if (tx == 0) {
          *shared_sum = block_sum;
        }
        __syncthreads();
        normalizer = math::ptx_rcp(max(*shared_sum, 1e-8f));
      }

      // Normalize and store
#pragma unroll 2
      for (uint32_t i = tx * VEC_SIZE; i < aligned_size; i += BLOCK_THREADS * VEC_SIZE) {
        data_vec.cast_load(probs + row_idx * vocab_size + chunk_start + i);
#pragma unroll
        for (uint32_t j = 0; j < VEC_SIZE; ++j) {
          data_vec[j] = DType(float(data_vec[j]) * normalizer);
        }
        data_vec.store(renormed_prob + row_idx * vocab_size + chunk_start + i);
      }
      for (uint32_t i = aligned_size + tx; i < actual_chunk_size; i += BLOCK_THREADS) {
        renormed_prob[row_idx * vocab_size + chunk_start + i] =
            DType(float(probs[row_idx * vocab_size + chunk_start + i]) * normalizer);
      }

      // Clear histogram for next iteration (in case it's k < vocab_size)
      // Only needed for multi-CTA mode; single-CTA uses shared memory cleared each iteration
      // Next iteration (iter+1) will use histogram[((iter+1)*NUM_ROUNDS) % 3] for its first round
      if constexpr (!SINGLE_CTA) {
        constexpr uint32_t NUM_ROUNDS = sizeof(OrderedType) * 8 / 8;  // ORDERED_BITS / RADIX_BITS
        uint32_t next_first_hist_idx = ((iter + 1) * NUM_ROUNDS) % 3;
        if (cta_in_group == 0) {
          for (uint32_t i = tx; i < RADIX; i += BLOCK_THREADS) {
            state->histogram[next_first_hist_idx][i] = 0;
          }
        }
        // No sync needed - next iteration's barrier will ensure visibility
      }
      continue;
    }

    // ========== Stage 1: Find pivot ==========
    uint32_t local_gt_count = 0;  // Not used in this kernel
    uint32_t local_eq_count = 0;  // Not used in this kernel
    auto ordered_pivot = RadixSelectFindPivot<BLOCK_THREADS, VEC_SIZE, SINGLE_CTA, false, DType>(
        probs + row_idx * vocab_size, shared_ordered, local_histogram, suffix_sum, shared_scalars,
        state, chunk_start, actual_chunk_size, k, barrier_phase, ctas_per_group, cta_in_group, tx,
        iter, local_gt_count, local_eq_count);
    pivot = Traits::FromOrdered(ordered_pivot);

    // ========== Stage 2: Compute sum of elements >= pivot ==========
    float thread_sum = 0.0f;
    vec_t<DType, VEC_SIZE> data_vec;
    const uint32_t aligned_size = (actual_chunk_size / VEC_SIZE) * VEC_SIZE;

#pragma unroll 2
    for (uint32_t i = tx * VEC_SIZE; i < aligned_size; i += BLOCK_THREADS * VEC_SIZE) {
      data_vec.cast_load(probs + row_idx * vocab_size + chunk_start + i);
#pragma unroll
      for (uint32_t j = 0; j < VEC_SIZE; ++j) {
        if (data_vec[j] >= pivot) {
          thread_sum += float(data_vec[j]);
        }
      }
    }
    // Handle tail
    for (uint32_t i = aligned_size + tx; i < actual_chunk_size; i += BLOCK_THREADS) {
      DType val = probs[row_idx * vocab_size + chunk_start + i];
      if (val >= pivot) {
        thread_sum += float(val);
      }
    }

    // Block reduction for sum
    typedef cub::BlockReduce<float, BLOCK_THREADS> BlockReduce;
    __shared__ typename BlockReduce::TempStorage temp_storage;
    float block_sum = BlockReduce(temp_storage).Sum(thread_sum);
    __syncthreads();

    if constexpr (!SINGLE_CTA) {
      // Multi-CTA: atomic add to global sum
      if (tx == 0) {
        if (cta_in_group == 0) {
          state->sum_topk = 0.0f;  // First CTA initializes
        }
      }
      // Barrier for initialization
      AdvanceRadixGroupBarrier(state, barrier_phase, ctas_per_group, tx);

      if (tx == 0 && block_sum > 0) {
        atomicAdd(&state->sum_topk, block_sum);
      }

      // Barrier to ensure all CTAs have contributed
      AdvanceRadixGroupBarrier(state, barrier_phase, ctas_per_group, tx);
      normalizer = math::ptx_rcp(max(state->sum_topk, 1e-8f));
    } else {
      // Single-CTA: use block_sum directly
      if (tx == 0) {
        *shared_sum = block_sum;
      }
      __syncthreads();
      normalizer = math::ptx_rcp(max(*shared_sum, 1e-8f));
    }

    // ========== Stage 3: Normalize elements >= pivot, set others to 0 ==========
#pragma unroll 2
    for (uint32_t i = tx * VEC_SIZE; i < aligned_size; i += BLOCK_THREADS * VEC_SIZE) {
      data_vec.cast_load(probs + row_idx * vocab_size + chunk_start + i);
#pragma unroll
      for (uint32_t j = 0; j < VEC_SIZE; ++j) {
        data_vec[j] = (data_vec[j] >= pivot) ? DType(float(data_vec[j]) * normalizer) : DType(0);
      }
      data_vec.store(renormed_prob + row_idx * vocab_size + chunk_start + i);
    }
    // Handle tail
    for (uint32_t i = aligned_size + tx; i < actual_chunk_size; i += BLOCK_THREADS) {
      DType val = probs[row_idx * vocab_size + chunk_start + i];
      renormed_prob[row_idx * vocab_size + chunk_start + i] =
          (val >= pivot) ? DType(float(val) * normalizer) : DType(0);
    }
  }

  // Reset group state for the next launch, from the last CTA to exit (only for multi-CTA).
  // The leading CTA must not do this: it could zero the arrival counter while a peer CTA is
  // still spinning in its final wait_ge, wedging the stream forever (issue #3610).
  if constexpr (!SINGLE_CTA) {
    RadixGroupResetStateLastCTA<BLOCK_THREADS>(state, nullptr, barrier_phase, ctas_per_group, tx);
  }
}

template <typename DType, typename IdType>
cudaError_t RadixTopKRenormProbMultiCTA(DType* probs, DType* renormed_prob, IdType* top_k_arr,
                                        uint32_t batch_size, uint32_t top_k_val,
                                        uint32_t vocab_size, RadixRowState* row_states_buffer,
                                        cudaStream_t stream = 0) {
  using OrderedType = typename RadixTopKTraits<DType>::OrderedType;
  constexpr uint32_t BLOCK_THREADS = 1024;
  const uint32_t vec_size = std::gcd(16 / sizeof(DType), vocab_size);

  // Get device properties
  int device;
  FLASHINFER_CUDA_CALL(cudaGetDevice(&device));
  int num_sms;
  FLASHINFER_CUDA_CALL(cudaDeviceGetAttribute(&num_sms, cudaDevAttrMultiProcessorCount, device));
  int max_smem_per_block;
  FLASHINFER_CUDA_CALL(
      cudaDeviceGetAttribute(&max_smem_per_block, cudaDevAttrMaxSharedMemoryPerBlockOptin, device));

  // Fixed shared memory overhead: histogram[256] + suffix_sum[256] + 4 scalars + 1 float
  constexpr size_t fixed_smem_size = sizeof(uint32_t) * (256 + 256 + 4) + sizeof(float);
  constexpr size_t fixed_smem_aligned = round_up(fixed_smem_size, 16);

  // Calculate max chunk size that fits in shared memory
  const size_t available_for_ordered = GetRadixTopKAvailableOrderedSmemBytes<BLOCK_THREADS>(
      max_smem_per_block, fixed_smem_aligned, false);
  if (available_for_ordered == 0) {
    return cudaErrorInvalidValue;
  }
  uint32_t max_chunk_elements = available_for_ordered / sizeof(OrderedType);
  max_chunk_elements = round_down(max_chunk_elements, vec_size);
  const uint32_t min_chunk_size = vec_size * BLOCK_THREADS;
  max_chunk_elements = std::max(max_chunk_elements, min_chunk_size);

  uint32_t ctas_per_group = ceil_div(vocab_size, max_chunk_elements);
  uint32_t chunk_size = ceil_div(vocab_size, ctas_per_group);
  chunk_size = round_up(chunk_size, vec_size);
  chunk_size = std::min(chunk_size, max_chunk_elements);

  const uint32_t smem_size = fixed_smem_aligned + chunk_size * sizeof(OrderedType);
  const bool single_cta = (ctas_per_group == 1);

  // Calculate number of groups (how many rows to process concurrently)
  uint32_t num_groups = std::min(static_cast<uint32_t>(num_sms) / ctas_per_group, batch_size);
  if (num_groups == 0) num_groups = 1;
  uint32_t total_ctas = num_groups * ctas_per_group;

  DISPATCH_ALIGNED_VEC_SIZE(vec_size, VEC_SIZE, {
    if (single_cta) {
      auto kernel =
          RadixTopKRenormProbKernel_MultiCTA<BLOCK_THREADS, VEC_SIZE, true, DType, IdType>;
      FLASHINFER_CUDA_CALL(
          cudaFuncSetAttribute(kernel, cudaFuncAttributeMaxDynamicSharedMemorySize, smem_size));

      dim3 nblks(total_ctas);
      dim3 nthrs(BLOCK_THREADS);
      void* args[] = {&probs,      &renormed_prob,     &top_k_arr,  &top_k_val,     &vocab_size,
                      &batch_size, &row_states_buffer, &chunk_size, &ctas_per_group};
      FLASHINFER_CUDA_CALL(cudaLaunchKernel((void*)kernel, nblks, nthrs, args, smem_size, stream));
    } else {
      auto kernel =
          RadixTopKRenormProbKernel_MultiCTA<BLOCK_THREADS, VEC_SIZE, false, DType, IdType>;
      FLASHINFER_CUDA_CALL(
          cudaFuncSetAttribute(kernel, cudaFuncAttributeMaxDynamicSharedMemorySize, smem_size));
      dim3 nblks(total_ctas);
      dim3 nthrs(BLOCK_THREADS);
      void* args[] = {&probs,      &renormed_prob,     &top_k_arr,  &top_k_val,     &vocab_size,
                      &batch_size, &row_states_buffer, &chunk_size, &ctas_per_group};
      FLASHINFER_CUDA_CALL(cudaLaunchKernel((void*)kernel, nblks, nthrs, args, smem_size, stream));
    }
  });

  return cudaSuccess;
}

/*!
 * \brief Launch multi-CTA Radix Top-K with Page Table Transform kernel.
 *
 * Performs top-k selection and gathers indices through a page table.
 * Used for sparse attention's second stage in prefill mode.
 *
 * \param input Input scores tensor [num_rows, max_len]
 * \param output_page_table Output physical positions [num_rows, top_k]
 * \param src_page_table Source page table [batch_size, src_stride]
 * \param src_stride Stride of source page table
 * \param row_to_batch Mapping from row index to batch index [num_rows], or nullptr if 1:1
 * \param lengths Sequence lengths per row [num_rows]
 * \param row_starts Score start indices per row [num_rows], or nullptr to use 0
 * \param page_table_row_starts Page-table start indices per row [num_rows], or nullptr to use
 * row_starts
 * \param num_rows Number of rows to process
 * \param top_k_val Number of top elements to select
 * \param max_len Maximum sequence length
 * \param row_states_buffer Buffer for inter-CTA synchronization
 * \param stream CUDA stream
 * \tparam PageTablePolicy Index-layout and optional-output policy type
 */
template <typename DType, typename IdType,
          typename PageTablePolicy = DirectPageTableKernelPolicy<IdType>>
cudaError_t RadixTopKPageTableTransformMultiCTA(
    DType* input, IdType* output_page_table, const IdType* src_page_table, int64_t src_stride,
    const IdType* row_to_batch, IdType* lengths, const IdType* row_starts,
    const IdType* page_table_row_starts, uint32_t num_rows, uint32_t top_k_val, uint32_t max_len,
    RadixRowState* row_states_buffer, bool deterministic, cudaStream_t stream = 0,
    const PageTablePolicy& page_table_policy = {}) {
  static_assert(IsValidPageTablePolicy<PageTablePolicy>);
  using OrderedType = typename RadixTopKTraits<DType>::OrderedType;
  constexpr uint32_t BLOCK_THREADS = 1024;
  const auto row_stride = page_table_policy.get_input_stride(max_len);
  uint32_t vec_size =
      (row_starts != nullptr) ? 1 : std::gcd(16 / sizeof(DType), static_cast<uint32_t>(row_stride));
  vec_size = page_table_policy.refine_vec_size(input, vec_size);

  int device;
  FLASHINFER_CUDA_CALL(cudaGetDevice(&device));
  int num_sms;
  FLASHINFER_CUDA_CALL(cudaDeviceGetAttribute(&num_sms, cudaDevAttrMultiProcessorCount, device));
  int max_smem_per_block;
  FLASHINFER_CUDA_CALL(
      cudaDeviceGetAttribute(&max_smem_per_block, cudaDevAttrMaxSharedMemoryPerBlockOptin, device));

  constexpr size_t fixed_smem_size = sizeof(uint32_t) * (256 + 256 + 5);
  constexpr size_t fixed_smem_aligned = round_up(fixed_smem_size, 16);
  const size_t available_for_ordered = GetRadixTopKAvailableOrderedSmemBytes<BLOCK_THREADS>(
      max_smem_per_block, fixed_smem_aligned, deterministic);
  if (available_for_ordered == 0) {
    return cudaErrorInvalidValue;
  }

  uint32_t max_chunk_elements = available_for_ordered / sizeof(OrderedType);
  max_chunk_elements = round_down(max_chunk_elements, vec_size);
  const uint32_t min_chunk_size = vec_size * BLOCK_THREADS;
  max_chunk_elements = std::max(max_chunk_elements, min_chunk_size);

  uint32_t ctas_per_group = ceil_div(max_len, max_chunk_elements);
  if (deterministic && ctas_per_group > RADIX_TOPK_MAX_DETERMINISTIC_CTAS_PER_GROUP) {
    return cudaErrorInvalidConfiguration;
  }
  uint32_t chunk_size = ceil_div(max_len, ctas_per_group);
  chunk_size = round_up(chunk_size, vec_size);
  chunk_size = std::min(chunk_size, max_chunk_elements);

  const bool single_cta = (ctas_per_group == 1);
  const uint32_t smem_size = fixed_smem_aligned + chunk_size * sizeof(OrderedType);

  uint32_t num_groups = std::min(static_cast<uint32_t>(num_sms) / ctas_per_group, num_rows);
  if (num_groups == 0) num_groups = 1;
  uint32_t total_ctas = num_groups * ctas_per_group;
  RadixDeterministicCollectScratch* det_scratch_buffer =
      MaybeGetRadixDeterministicCollectScratchBuffer(row_states_buffer, num_groups, single_cta,
                                                     deterministic);

  // Unified kernel parameters
  DType* output_values = nullptr;  // Not used in PageTableTransform mode
  dim3 nblks(total_ctas);
  dim3 nthrs(BLOCK_THREADS);
  auto launch_kernel = [&](auto kernel, auto... page_table_args) -> cudaError_t {
    FLASHINFER_CUDA_CALL(
        cudaFuncSetAttribute(kernel, cudaFuncAttributeMaxDynamicSharedMemorySize, smem_size));
    void* args[] = {&input,
                    &output_page_table,
                    &output_values,
                    &src_page_table,
                    &lengths,
                    &row_starts,
                    &page_table_row_starts,
                    &row_to_batch,
                    &src_stride,
                    &top_k_val,
                    &max_len,
                    &num_rows,
                    &row_states_buffer,
                    &det_scratch_buffer,
                    &chunk_size,
                    &ctas_per_group,
                    &page_table_args...};
    return cudaLaunchKernel((void*)kernel, nblks, nthrs, args, smem_size, stream);
  };

#define LAUNCH_PAGE_TABLE_KERNEL(THREADS, SINGLE_CTA_FLAG, DET_FLAG)                          \
  do {                                                                                        \
    if constexpr (!std::is_empty_v<PageTablePolicy>) {                                        \
      auto kernel = RadixTopKKernel_Unified<THREADS, VEC_SIZE, SINGLE_CTA_FLAG, DET_FLAG,     \
                                            RadixTopKMode::PageTableTransform, DType, IdType, \
                                            PageTablePolicy, PageTablePolicy>;                \
      FLASHINFER_CUDA_CALL(launch_kernel(kernel, page_table_policy));                         \
    } else {                                                                                  \
      auto kernel = RadixTopKKernel_Unified<THREADS, VEC_SIZE, SINGLE_CTA_FLAG, DET_FLAG,     \
                                            RadixTopKMode::PageTableTransform, DType, IdType, \
                                            PageTablePolicy>;                                 \
      FLASHINFER_CUDA_CALL(launch_kernel(kernel));                                            \
    }                                                                                         \
  } while (0)

  DISPATCH_ALIGNED_VEC_SIZE(vec_size, VEC_SIZE, {
    if (single_cta) {
      if (!deterministic) {
        LAUNCH_PAGE_TABLE_KERNEL(BLOCK_THREADS, true, false);
      } else {
        LAUNCH_PAGE_TABLE_KERNEL(BLOCK_THREADS, true, true);
      }
    } else {
      if (!deterministic) {
        LAUNCH_PAGE_TABLE_KERNEL(BLOCK_THREADS, false, false);
      } else {
        LAUNCH_PAGE_TABLE_KERNEL(BLOCK_THREADS, false, true);
      }
    }
  });

#undef LAUNCH_PAGE_TABLE_KERNEL

  return cudaSuccess;
}

/*!
 * \brief Launch multi-CTA Radix Top-K with Ragged Index Transform kernel.
 *
 * Performs top-k selection and adds an offset to each index.
 * Used for sparse attention's second stage with ragged KV cache.
 *
 * \param input Input scores tensor [num_rows, max_len]
 * \param output_indices Output indices [num_rows, top_k]
 * \param offsets Offset to add per row [num_rows]
 * \param lengths Sequence lengths per row [num_rows]
 * \param num_rows Number of rows to process
 * \param top_k_val Number of top elements to select
 * \param max_len Maximum sequence length (input stride)
 * \param row_states_buffer Buffer for inter-CTA synchronization
 * \param stream CUDA stream
 */
template <typename DType, typename IdType>
cudaError_t RadixTopKRaggedTransformMultiCTA(DType* input, IdType* output_indices,
                                             const IdType* offsets, IdType* lengths,
                                             const IdType* row_starts, uint32_t num_rows,
                                             uint32_t top_k_val, uint32_t max_len,
                                             RadixRowState* row_states_buffer, bool deterministic,
                                             cudaStream_t stream = 0) {
  using OrderedType = typename RadixTopKTraits<DType>::OrderedType;
  constexpr uint32_t BLOCK_THREADS = 1024;
  const uint32_t vec_size = (row_starts != nullptr) ? 1 : std::gcd(16 / sizeof(DType), max_len);

  int device;
  FLASHINFER_CUDA_CALL(cudaGetDevice(&device));
  int num_sms;
  FLASHINFER_CUDA_CALL(cudaDeviceGetAttribute(&num_sms, cudaDevAttrMultiProcessorCount, device));
  int max_smem_per_block;
  FLASHINFER_CUDA_CALL(
      cudaDeviceGetAttribute(&max_smem_per_block, cudaDevAttrMaxSharedMemoryPerBlockOptin, device));

  constexpr size_t fixed_smem_size = sizeof(uint32_t) * (256 + 256 + 5);
  constexpr size_t fixed_smem_aligned = round_up(fixed_smem_size, 16);
  const size_t available_for_ordered = GetRadixTopKAvailableOrderedSmemBytes<BLOCK_THREADS>(
      max_smem_per_block, fixed_smem_aligned, deterministic);
  if (available_for_ordered == 0) {
    return cudaErrorInvalidValue;
  }

  uint32_t max_chunk_elements = available_for_ordered / sizeof(OrderedType);
  max_chunk_elements = round_down(max_chunk_elements, vec_size);
  const uint32_t min_chunk_size = vec_size * BLOCK_THREADS;
  max_chunk_elements = std::max(max_chunk_elements, min_chunk_size);

  uint32_t ctas_per_group = ceil_div(max_len, max_chunk_elements);
  if (deterministic && ctas_per_group > RADIX_TOPK_MAX_DETERMINISTIC_CTAS_PER_GROUP) {
    return cudaErrorInvalidConfiguration;
  }
  uint32_t chunk_size = ceil_div(max_len, ctas_per_group);
  chunk_size = round_up(chunk_size, vec_size);
  chunk_size = std::min(chunk_size, max_chunk_elements);

  const bool single_cta = (ctas_per_group == 1);
  const uint32_t smem_size = fixed_smem_aligned + chunk_size * sizeof(OrderedType);

  uint32_t num_groups = std::min(static_cast<uint32_t>(num_sms) / ctas_per_group, num_rows);
  if (num_groups == 0) num_groups = 1;
  uint32_t total_ctas = num_groups * ctas_per_group;
  RadixDeterministicCollectScratch* det_scratch_buffer =
      MaybeGetRadixDeterministicCollectScratchBuffer(row_states_buffer, num_groups, single_cta,
                                                     deterministic);

  // Unified kernel parameters
  DType* output_values = nullptr;        // Not used in RaggedTransform mode
  const IdType* page_starts = nullptr;   // Not used in RaggedTransform mode
  const IdType* row_to_batch = nullptr;  // Not used in RaggedTransform mode
  int64_t aux_stride = 0;                // Not used in RaggedTransform mode
  dim3 nblks(total_ctas);
  dim3 nthrs(BLOCK_THREADS);
  void* args[] = {&input,
                  &output_indices,
                  &output_values,
                  &offsets,
                  &lengths,
                  &row_starts,
                  &page_starts,
                  &row_to_batch,
                  &aux_stride,
                  &top_k_val,
                  &max_len,
                  &num_rows,
                  &row_states_buffer,
                  &det_scratch_buffer,
                  &chunk_size,
                  &ctas_per_group};

#define LAUNCH_RAGGED_KERNEL(THREADS, SINGLE_CTA_FLAG, DET_FLAG)                                  \
  do {                                                                                            \
    auto kernel = RadixTopKKernel_Unified<THREADS, VEC_SIZE, SINGLE_CTA_FLAG, DET_FLAG,           \
                                          RadixTopKMode::RaggedTransform, DType, IdType>;         \
    FLASHINFER_CUDA_CALL(                                                                         \
        cudaFuncSetAttribute(kernel, cudaFuncAttributeMaxDynamicSharedMemorySize, smem_size));    \
    FLASHINFER_CUDA_CALL(cudaLaunchKernel((void*)kernel, nblks, nthrs, args, smem_size, stream)); \
  } while (0)

  DISPATCH_ALIGNED_VEC_SIZE(vec_size, VEC_SIZE, {
    if (single_cta) {
      if (!deterministic) {
        LAUNCH_RAGGED_KERNEL(BLOCK_THREADS, true, false);
      } else {
        LAUNCH_RAGGED_KERNEL(BLOCK_THREADS, true, true);
      }
    } else {
      if (!deterministic) {
        LAUNCH_RAGGED_KERNEL(BLOCK_THREADS, false, false);
      } else {
        LAUNCH_RAGGED_KERNEL(BLOCK_THREADS, false, true);
      }
    }
  });

#undef LAUNCH_RAGGED_KERNEL

  return cudaSuccess;
}

/*!
 * \brief Launch multi-CTA Radix Top-K kernel (returns indices and values)
 *
 * \param input Input tensor [batch_size, vocab_size]
 * \param output_indices Output indices tensor [batch_size, top_k]
 * \param output_values Output values tensor [batch_size, top_k]
 * \param top_k_arr Per-row top-k values or nullptr for uniform top_k
 * \param batch_size Number of rows
 * \param top_k_val Default top-k value (used when top_k_arr is nullptr)
 * \param vocab_size Number of elements per row
 * \param row_states_buffer Buffer for inter-CTA synchronization
 * \param stream CUDA stream
 */
template <typename DType, typename IdType>
cudaError_t RadixTopKMultiCTA(DType* input, IdType* output_indices, DType* output_values,
                              IdType* top_k_arr, uint32_t batch_size, uint32_t top_k_val,
                              uint32_t vocab_size, RadixRowState* row_states_buffer,
                              bool deterministic, cudaStream_t stream = 0) {
  using OrderedType = typename RadixTopKTraits<DType>::OrderedType;
  constexpr uint32_t BLOCK_THREADS = 1024;
  const uint32_t vec_size = std::gcd(16 / sizeof(DType), vocab_size);

  int device;
  FLASHINFER_CUDA_CALL(cudaGetDevice(&device));
  int num_sms;
  FLASHINFER_CUDA_CALL(cudaDeviceGetAttribute(&num_sms, cudaDevAttrMultiProcessorCount, device));
  int max_smem_per_block;
  FLASHINFER_CUDA_CALL(
      cudaDeviceGetAttribute(&max_smem_per_block, cudaDevAttrMaxSharedMemoryPerBlockOptin, device));

  // Fixed smem: histogram[256] + suffix_sum[256] + scalars
  // Scalars: 5 for single-CTA, 4 for multi-CTA
  constexpr size_t fixed_smem_size = sizeof(uint32_t) * (256 + 256 + 5);
  constexpr size_t fixed_smem_aligned = round_up(fixed_smem_size, 16);
  const size_t available_for_ordered = GetRadixTopKAvailableOrderedSmemBytes<BLOCK_THREADS>(
      max_smem_per_block, fixed_smem_aligned, deterministic);
  if (available_for_ordered == 0) {
    return cudaErrorInvalidValue;
  }

  uint32_t max_chunk_elements = available_for_ordered / sizeof(OrderedType);
  max_chunk_elements = round_down(max_chunk_elements, vec_size);
  const uint32_t min_chunk_size = vec_size * BLOCK_THREADS;
  max_chunk_elements = std::max(max_chunk_elements, min_chunk_size);

  uint32_t ctas_per_group = ceil_div(vocab_size, max_chunk_elements);
  if (deterministic && ctas_per_group > RADIX_TOPK_MAX_DETERMINISTIC_CTAS_PER_GROUP) {
    return cudaErrorInvalidConfiguration;
  }
  uint32_t chunk_size = ceil_div(vocab_size, ctas_per_group);
  chunk_size = round_up(chunk_size, vec_size);
  chunk_size = std::min(chunk_size, max_chunk_elements);

  // Determine if we use single-CTA path
  const bool single_cta = (ctas_per_group == 1);

  // Calculate smem_size: fixed + ordered values
  const uint32_t smem_size = fixed_smem_aligned + chunk_size * sizeof(OrderedType);

  // Calculate number of groups (how many rows to process concurrently)
  uint32_t num_groups = std::min(static_cast<uint32_t>(num_sms) / ctas_per_group, batch_size);
  if (num_groups == 0) num_groups = 1;
  uint32_t total_ctas = num_groups * ctas_per_group;
  RadixDeterministicCollectScratch* det_scratch_buffer =
      MaybeGetRadixDeterministicCollectScratchBuffer(row_states_buffer, num_groups, single_cta,
                                                     deterministic);

  // Unified kernel parameters
  IdType* lengths = nullptr;             // Not used in Basic mode
  const IdType* row_starts = nullptr;    // Not used in Basic mode
  const IdType* page_starts = nullptr;   // Not used in Basic mode
  const IdType* row_to_batch = nullptr;  // Not used in Basic mode
  int64_t aux_stride = 0;                // Not used in Basic mode
  dim3 nblks(total_ctas);
  dim3 nthrs(BLOCK_THREADS);
  void* args[] = {
      &input,         &output_indices, &output_values,     &top_k_arr,          &lengths,
      &row_starts,    &page_starts,    &row_to_batch,      &aux_stride,         &top_k_val,
      &vocab_size,    &batch_size,     &row_states_buffer, &det_scratch_buffer, &chunk_size,
      &ctas_per_group};

#define LAUNCH_BASIC_KERNEL(THREADS, SINGLE_CTA_FLAG, DET_FLAG)                                   \
  do {                                                                                            \
    auto kernel = RadixTopKKernel_Unified<THREADS, VEC_SIZE, SINGLE_CTA_FLAG, DET_FLAG,           \
                                          RadixTopKMode::Basic, DType, IdType>;                   \
    FLASHINFER_CUDA_CALL(                                                                         \
        cudaFuncSetAttribute(kernel, cudaFuncAttributeMaxDynamicSharedMemorySize, smem_size));    \
    FLASHINFER_CUDA_CALL(cudaLaunchKernel((void*)kernel, nblks, nthrs, args, smem_size, stream)); \
  } while (0)

  DISPATCH_ALIGNED_VEC_SIZE(vec_size, VEC_SIZE, {
    if (single_cta) {
      if (!deterministic) {
        LAUNCH_BASIC_KERNEL(BLOCK_THREADS, true, false);
      } else {
        LAUNCH_BASIC_KERNEL(BLOCK_THREADS, true, true);
      }
    } else {
      if (!deterministic) {
        LAUNCH_BASIC_KERNEL(BLOCK_THREADS, false, false);
      } else {
        LAUNCH_BASIC_KERNEL(BLOCK_THREADS, false, true);
      }
    }
  });

#undef LAUNCH_BASIC_KERNEL

  return cudaSuccess;
}
// ==================== FilteredTopK Implementation ====================
// Based on sgl-kernel's filter algorithm with multi-dtype support

// FilteredTopK traits for different data types
template <typename DType>
struct FilteredTopKTraits;

// Specialization for float (32-bit): coarse histogram uses FP16 high 8 bits, 4 refinement rounds
template <>
struct FilteredTopKTraits<float> {
  using OrderedType = uint32_t;
  static constexpr int NUM_REFINE_ROUNDS = 4;
  static constexpr int FIRST_REFINE_SHIFT = 24;

  __device__ __forceinline__ static uint8_t ToCoarseKey(float x) {
    // Convert to FP16 representation and extract high 8 bits
    __half h = __float2half_rn(x);
    uint16_t bits = __half_as_ushort(h);
    uint16_t key =
        (bits & 0x8000) ? static_cast<uint16_t>(~bits) : static_cast<uint16_t>(bits | 0x8000);
    return static_cast<uint8_t>(key >> 8);
  }

  __device__ __forceinline__ static OrderedType ToOrdered(float x) {
    uint32_t bits = __float_as_uint(x);
    return (bits & 0x80000000u) ? ~bits : (bits | 0x80000000u);
  }
};

// Specialization for half (16-bit): coarse histogram uses high 8 bits, only need low 8 bits for
// refinement Since coarse key = high 8 bits, refinement only needs to look at low 8 bits (no
// additional rounds needed if we can determine topk from coarse pass alone)
template <>
struct FilteredTopKTraits<half> {
  using OrderedType = uint16_t;
  static constexpr int NUM_REFINE_ROUNDS = 1;   // Only 1 round for low 8 bits
  static constexpr int FIRST_REFINE_SHIFT = 0;  // Start from bit 0 (low 8 bits)

  __device__ __forceinline__ static uint8_t ToCoarseKey(half x) {
    uint16_t bits = __half_as_ushort(x);
    uint16_t key =
        (bits & 0x8000) ? static_cast<uint16_t>(~bits) : static_cast<uint16_t>(bits | 0x8000);
    return static_cast<uint8_t>(key >> 8);
  }

  __device__ __forceinline__ static OrderedType ToOrdered(half x) {
    uint16_t bits = __half_as_ushort(x);
    return (bits & 0x8000) ? static_cast<uint16_t>(~bits) : static_cast<uint16_t>(bits | 0x8000);
  }
};

// Specialization for nv_bfloat16 (16-bit): same as half
template <>
struct FilteredTopKTraits<nv_bfloat16> {
  using OrderedType = uint16_t;
  static constexpr int NUM_REFINE_ROUNDS = 1;
  static constexpr int FIRST_REFINE_SHIFT = 0;

  __device__ __forceinline__ static uint8_t ToCoarseKey(nv_bfloat16 x) {
    uint16_t bits = __bfloat16_as_ushort(x);
    uint16_t key =
        (bits & 0x8000) ? static_cast<uint16_t>(~bits) : static_cast<uint16_t>(bits | 0x8000);
    return static_cast<uint8_t>(key >> 8);
  }

  __device__ __forceinline__ static OrderedType ToOrdered(nv_bfloat16 x) {
    uint16_t bits = __bfloat16_as_ushort(x);
    return (bits & 0x8000) ? static_cast<uint16_t>(~bits) : static_cast<uint16_t>(bits | 0x8000);
  }
};

// FilteredTopK constants
constexpr uint32_t FILTERED_TOPK_MAX_K = 2048;
constexpr uint32_t FILTERED_TOPK_BLOCK_THREADS = 1024;
constexpr uint32_t FILTERED_TOPK_SMEM_INPUT_SIZE = 16 * 1024;  // 16K indices per buffer
constexpr size_t FILTERED_TOPK_SMEM_DYNAMIC =
    sizeof(int) * 2 * FILTERED_TOPK_SMEM_INPUT_SIZE;  // 128KB

// Output modes for unified FilteredTopK kernel
enum class FilteredTopKMode { Plain, PageTable, Ragged };

/*!
 * \brief Unified Filtered Top-K kernel supporting multiple output modes.
 *
 * \tparam DType Data type (float, half, nv_bfloat16)
 * \tparam IdType Index type (int32_t)
 * \tparam VEC_SIZE Vector size for input loads (1, 2, 4, or 8)
 * \tparam MODE Output mode (Plain, PageTable, Ragged)
 * \tparam PageTablePolicy Index-layout and optional-output policy
 * \tparam PageTableArgs Policy arguments; empty when the policy has no runtime state
 *
 * Parameters vary by mode:
 * - Plain: output = indices, aux_output = values, aux_input/aux_stride/row_to_batch unused
 * - PageTable: output = dst_page_table, aux_input = src_page_table, aux_stride = src_stride
 * - Ragged: output = indices, aux_input = offsets, aux_output/aux_stride/row_to_batch unused
 */
template <typename DType, typename IdType, int VEC_SIZE, bool DETERMINISTIC, FilteredTopKMode MODE,
          TopKTieBreak TIE_BREAK, typename PageTablePolicy = DirectPageTableKernelPolicy<IdType>,
          typename... PageTableArgs>
__global__ void __launch_bounds__(FILTERED_TOPK_BLOCK_THREADS)
    FilteredTopKUnifiedKernel(const DType* __restrict__ input, IdType* __restrict__ output,
                              DType* __restrict__ aux_output,           // values for Plain mode
                              const IdType* __restrict__ aux_input,     // page_table or offsets
                              int64_t aux_stride,                       // src_stride for PageTable
                              const IdType* __restrict__ row_to_batch,  // for PageTable
                              const IdType* __restrict__ lengths,
                              const IdType* __restrict__ row_starts,
                              const IdType* __restrict__ page_table_row_starts, uint32_t num_rows,
                              uint32_t top_k, uint32_t max_len, PageTableArgs... page_table_args) {
  static_assert(MODE == FilteredTopKMode::PageTable ||
                IsDirectPageTableKernelPolicy<PageTablePolicy, IdType>);
  static_assert(IsValidPageTableKernelArgs<PageTablePolicy, PageTableArgs...>);
  constexpr uint32_t BLOCK_SIZE = FILTERED_TOPK_BLOCK_THREADS;
  constexpr int RADIX = 256;
  constexpr int SMEM_INPUT_SIZE = FILTERED_TOPK_SMEM_INPUT_SIZE;
  static_assert(BLOCK_SIZE % 32 == 0, "BLOCK_SIZE must be a multiple of warp size");
  const PageTablePolicy page_table_policy{page_table_args...};

  const uint32_t bid = blockIdx.x;
  const int tx = threadIdx.x;

  if (bid >= num_rows) return;

  const int length = (lengths != nullptr) ? lengths[bid] : static_cast<int>(max_len);
  const IdType row_start =
      (row_starts != nullptr && MODE != FilteredTopKMode::Plain) ? row_starts[bid] : 0;
  const IdType page_table_row_start =
      (page_table_row_starts != nullptr && MODE == FilteredTopKMode::PageTable)
          ? page_table_row_starts[bid]
          : row_start;
  const auto row_stride = page_table_policy.get_input_stride(max_len);
  const DType* score = input + static_cast<size_t>(bid) * row_stride + row_start;
  IdType* dst = output + bid * top_k;
  IdType* dst_raw = nullptr;
  if constexpr (!IsDirectPageTableKernelPolicy<PageTablePolicy, IdType>) {
    dst_raw = page_table_policy.get_row_raw_output(bid, top_k);
  }

  // Mode-specific setup
  [[maybe_unused]] const IdType* src_page_entry = nullptr;
  [[maybe_unused]] IdType offset_val = 0;
  [[maybe_unused]] DType* dst_values = nullptr;

  if constexpr (MODE == FilteredTopKMode::PageTable) {
    const uint32_t batch_idx = (row_to_batch != nullptr) ? row_to_batch[bid] : bid;
    src_page_entry = aux_input + batch_idx * aux_stride;
  } else if constexpr (MODE == FilteredTopKMode::Ragged) {
    offset_val = aux_input[bid];
  } else {  // Plain
    dst_values = (aux_output != nullptr) ? aux_output + bid * top_k : nullptr;
  }

  // Trivial case: length <= top_k
  if (length <= static_cast<int>(top_k)) {
    for (int i = tx; i < static_cast<int>(top_k); i += BLOCK_SIZE) {
      if constexpr (MODE == FilteredTopKMode::Plain) {
        if (i < length) {
          dst[i] = static_cast<IdType>(i);
          if (dst_values != nullptr) dst_values[i] = score[i];
        } else {
          dst[i] = static_cast<IdType>(-1);
          if (dst_values != nullptr) dst_values[i] = DType(0);
        }
      } else if constexpr (DETERMINISTIC) {
        // Defer the page-table/ragged transform to the lightweight finalizer.
        dst[i] = (i < length) ? static_cast<IdType>(i) : static_cast<IdType>(-1);
      } else if constexpr (MODE == FilteredTopKMode::PageTable) {
        if constexpr (IsDirectPageTableKernelPolicy<PageTablePolicy, IdType>) {
          dst[i] =
              (i < length) ? src_page_entry[page_table_row_start + i] : static_cast<IdType>(-1);
        } else {
          const IdType raw_idx = (i < length) ? static_cast<IdType>(i) : static_cast<IdType>(-1);
          if (dst_raw != nullptr) {
            dst_raw[i] = raw_idx;
          }
          dst[i] = raw_idx >= 0
                       ? page_table_policy.transform_index(src_page_entry, page_table_row_start,
                                                           static_cast<uint32_t>(raw_idx))
                       : static_cast<IdType>(-1);
        }
      } else {  // Ragged
        dst[i] = (i < length) ? static_cast<IdType>(i) + offset_val : static_cast<IdType>(-1);
      }
    }
    return;
  }

  // Static shared memory
  alignas(128) __shared__ int s_histogram_buf[2][RADIX + 128];
  __shared__ int s_counter;
  __shared__ int s_threshold_bin_id;
  // Per-round copies of s_threshold_bin_id for deterministic pivot rebuild.
  __shared__ int s_refine_thresholds[4];
  __shared__ int s_num_input[2];
  alignas(128) __shared__ int s_indices[FILTERED_TOPK_MAX_K];
  // Set 1 when s_input_idx overflows in tie-heavy workload
  __shared__ int s_refine_overflow;
  __shared__ int s_last_remain;

  auto& s_histogram = s_histogram_buf[0];

  // Dynamic shared memory for input double buffer
  extern __shared__ int s_input_idx[][SMEM_INPUT_SIZE];

  using Traits = FilteredTopKTraits<DType>;
  using OrderedType = typename Traits::OrderedType;
  int topk = top_k;
  if (tx == 0) s_refine_overflow = 0;
  if constexpr (DETERMINISTIC) {
    if (tx < 4) {
      s_refine_thresholds[tx] = 0xFF;
    }
  }
  if (tx < RADIX + 1) s_histogram[tx] = 0;
  __syncthreads();

  // Stage 1: (shared by deterministic and non-deterministic modes)
  // build a coarse histogram and identify the threshold bin.
  // The modes diverge later when collecting == pivot elements.
  vec_t<DType, VEC_SIZE> score_vec;

  const int aligned_length = (length / VEC_SIZE) * VEC_SIZE;
  // Full-row scan helper (vectorized body + tail). Overflow fallback reuses this traversal.
  auto for_each_score_full = [&](auto&& fn) {
  // vectorized body
#pragma unroll 2
    for (int base = tx * VEC_SIZE; base < aligned_length; base += BLOCK_SIZE * VEC_SIZE) {
      score_vec.cast_load(&score[base]);
#pragma unroll
      for (int j = 0; j < VEC_SIZE; ++j) {
        fn(score_vec[j], base + j);
      }
    }
    // tail
    for (int i = aligned_length + tx; i < length; i += BLOCK_SIZE) {
      fn(score[i], i);
    }
  };
  auto accumulate_coarse_hist = [&](auto raw_input, int /*index*/) {
    const auto bin = Traits::ToCoarseKey(raw_input);
    atomicAdd(&s_histogram[bin], 1);
  };
  for_each_score_full(accumulate_coarse_hist);
  __syncthreads();

  // Suffix sum (Hillis Steele Scan)
  const auto run_cumsum = [&]() {
#pragma unroll 8
    for (int i = 0; i < 8; ++i) {
      if (tx < RADIX) {
        const auto j = 1 << i;
        const auto k = i & 1;
        auto value = s_histogram_buf[k][tx];
        if (tx < RADIX - j) {
          value += s_histogram_buf[k][tx + j];
        }
        s_histogram_buf[k ^ 1][tx] = value;
      }
      __syncthreads();
    }
  };
  auto update_refine_threshold = [&](int next_input_idx, auto reset_next_input_tag) {
    constexpr bool RESET_NEXT_INPUT = decltype(reset_next_input_tag)::value;
    run_cumsum();
    if (tx < RADIX && s_histogram[tx] > topk && s_histogram[tx + 1] <= topk) {
      s_threshold_bin_id = tx;
      if constexpr (RESET_NEXT_INPUT) {
        s_num_input[next_input_idx] = 0;
      }
      s_last_remain = topk - s_histogram[tx + 1];
    }
    __syncthreads();
  };

  run_cumsum();
  if (tx < RADIX && s_histogram[tx] > topk && s_histogram[tx + 1] <= topk) {
    s_threshold_bin_id = tx;
    s_num_input[0] = 0;
    s_counter = 0;
  }
  __syncthreads();

  const auto threshold_bin = s_threshold_bin_id;
  topk -= s_histogram[threshold_bin + 1];
  [[maybe_unused]] const int topk_after_coarse = topk;

  constexpr int NUM_ROUNDS = Traits::NUM_REFINE_ROUNDS;
  constexpr int FIRST_SHIFT = Traits::FIRST_REFINE_SHIFT;

  // fp16/bf16: stop_round = 0; fp32: stop_round = 0,1,2,3
  auto build_det_pivot = [&](int stop_round) -> OrderedType {
    if constexpr (sizeof(OrderedType) == 2) {
      return static_cast<OrderedType>((static_cast<uint32_t>(threshold_bin) << 8) |
                                      static_cast<uint32_t>(s_refine_thresholds[0]));
    } else {  // fp32
      uint32_t pivot = 0;
      for (int round = 0; round < NUM_ROUNDS; ++round) {
        uint32_t byte =
            (round <= stop_round) ? static_cast<uint32_t>(s_refine_thresholds[round]) : 0xFFu;
        pivot |= (byte << (FIRST_SHIFT - round * 8));
      }
      return static_cast<OrderedType>(pivot);
    }
  };

  if (topk == 0) {
    // Collect indices where bin > threshold
    auto collect_coarse_gt = [&](auto raw_input, int index) {
      const auto bin = static_cast<int>(Traits::ToCoarseKey(raw_input));
      if (bin > threshold_bin) {
        const auto pos = atomicAdd(&s_counter, 1);
        s_indices[pos] = index;
      }
    };
    for_each_score_full(collect_coarse_gt);
    __syncthreads();
  } else {
    __syncthreads();
    if (tx < RADIX + 1) s_histogram[tx] = 0;
    __syncthreads();

    // Both non-det and det modes use atomicAdd to append >threshold winners here;
    // only ==threshold handling diverges between the two modes.
    auto collect_gt_and_nondet_eq_threshold = [&](auto value, auto threshold, int idx,
                                                  bool collect_eq) {
      if (value > threshold) {
        const int pos = atomicAdd(&s_counter, 1);
        s_indices[pos] = idx;
      } else if constexpr (!DETERMINISTIC) {
        if (collect_eq && value == threshold) {
          const int pos = atomicAdd(&s_last_remain, -1);
          if (pos > 0) {
            s_indices[static_cast<int>(top_k) - pos] = idx;
          }
        }
      }
    };

    auto collect_det_eq_pivot = [&](OrderedType pivot, int eq_needed) {
      if (eq_needed > 0) {
        using DetCollectBlockScan =
            cub::BlockScan<uint32_t, BLOCK_SIZE, cub::BLOCK_SCAN_RAKING_MEMOIZE>;
        __shared__ typename DetCollectBlockScan::TempStorage temp_storage;
        auto emit_pivot_eq = [&](uint32_t idx, uint32_t local_pos) {
          s_indices[static_cast<int>(top_k) - eq_needed + static_cast<int>(local_pos)] =
              static_cast<int>(idx);
        };
        if constexpr (TIE_BREAK == TopKTieBreak::Small) {
          DeterministicContiguousCollect<BLOCK_SIZE, false>(
              tx, length, temp_storage,
              [&](uint32_t idx) { return Traits::ToOrdered(score[idx]) == pivot; }, eq_needed,
              emit_pivot_eq);
        } else if constexpr (TIE_BREAK == TopKTieBreak::Large) {
          DeterministicContiguousCollect<BLOCK_SIZE, true>(
              tx, length, temp_storage,
              [&](uint32_t idx) { return Traits::ToOrdered(score[idx]) == pivot; }, eq_needed,
              emit_pivot_eq);
        } else {
          DeterministicThreadStridedCollect<BLOCK_SIZE>(
              tx, length, temp_storage,
              [&](uint32_t idx) { return Traits::ToOrdered(score[idx]) == pivot; }, eq_needed,
              emit_pivot_eq);
        }
      }
    };

    // Filter + histogram for refinement
    auto filter_and_add_to_histogram = [&](auto raw_input, int index) {
      const auto bin = static_cast<int>(Traits::ToCoarseKey(raw_input));
      if (bin > threshold_bin) {
        const auto pos = atomicAdd(&s_counter, 1);
        s_indices[pos] = index;
      } else if (bin == threshold_bin) {
        const auto pos = atomicAdd(&s_num_input[0], 1);
        if (__builtin_expect(pos < SMEM_INPUT_SIZE, 1)) {
          s_input_idx[0][pos] = index;
          const auto ordered = Traits::ToOrdered(raw_input);
          const auto sub_bin = (ordered >> FIRST_SHIFT) & 0xFF;
          atomicAdd(&s_histogram[sub_bin], 1);
        } else {
          atomicOr(&s_refine_overflow, 1);
        }
      }
    };
    for_each_score_full(filter_and_add_to_histogram);
    __syncthreads();

    // Stage 2: refine with 8bit radix passes.
    // If the threshold-bin candidate buffer overflows in 1-round refine mode
    // (fp16/bf16), switch to a slow path that re-histograms the full threshold
    // bin to preserve correctness.
    auto collect_with_threshold_last_round = [&](int r_idx, int num_input, int offset,
                                                 int threshold) {
      for (int i = tx; i < num_input; i += BLOCK_SIZE) {
        const auto idx = s_input_idx[r_idx][i];
        const auto raw_input = score[idx];
        const auto bin = (Traits::ToOrdered(raw_input) >> offset) & 0xFF;
        collect_gt_and_nondet_eq_threshold(static_cast<int>(bin), threshold, idx,
                                           /*allow_eq_claim=*/true);
      }
      __syncthreads();
    };
    auto collect_with_threshold_non_last_round = [&](int r_idx, int num_input, int offset,
                                                     int threshold) {
      const auto next_r_idx = r_idx ^ 1;
      __syncthreads();
      if (tx < RADIX + 1) s_histogram[tx] = 0;
      __syncthreads();
      for (int i = tx; i < num_input; i += BLOCK_SIZE) {
        const auto idx = s_input_idx[r_idx][i];
        const auto raw_input = score[idx];
        const auto bin = (Traits::ToOrdered(raw_input) >> offset) & 0xFF;
        if (static_cast<int>(bin) > threshold) {
          const auto pos = atomicAdd(&s_counter, 1);
          s_indices[pos] = idx;
        } else if (static_cast<int>(bin) == threshold) {
          const auto pos = atomicAdd(&s_num_input[next_r_idx], 1);
          if (__builtin_expect(pos < SMEM_INPUT_SIZE, 1)) {
            s_input_idx[next_r_idx][pos] = idx;
            const auto bin32 = Traits::ToOrdered(raw_input);
            const auto sub_bin = (bin32 >> (offset - 8)) & 0xFF;
            atomicAdd(&s_histogram[sub_bin], 1);
          } else {
            atomicOr(&s_refine_overflow, 1);
          }
        }
      }
      __syncthreads();
    };
    // Returns true if this round fully resolves the pivot, i.e. no ==threshold
    // elements need to be carried into another refine round.
    auto run_refine_round = [&](int r_idx, int offset, auto is_last_round_tag) {
      constexpr bool IS_LAST_ROUND = decltype(is_last_round_tag)::value;
      const auto raw_num_input = s_num_input[r_idx];
      const auto num_input = (raw_num_input < SMEM_INPUT_SIZE) ? raw_num_input : SMEM_INPUT_SIZE;

      update_refine_threshold(r_idx ^ 1, std::true_type{});

      const auto threshold = s_threshold_bin_id;
      if constexpr (DETERMINISTIC) {
        if (tx == 0) {
          s_refine_thresholds[(FIRST_SHIFT - offset) / 8] = threshold;
        }
      }
      topk -= s_histogram[threshold + 1];
      if (topk == 0) {
        // Final round reached: only collect bins strictly greater than threshold.
        for (int i = tx; i < num_input; i += BLOCK_SIZE) {
          const auto idx = s_input_idx[r_idx][i];
          const auto bin = (Traits::ToOrdered(score[idx]) >> offset) & 0xFF;
          if (static_cast<int>(bin) > threshold) {
            const auto pos = atomicAdd(&s_counter, 1);
            s_indices[pos] = idx;
          }
        }
        __syncthreads();
        return true;
      }

      if constexpr (IS_LAST_ROUND) {
        collect_with_threshold_last_round(r_idx, num_input, offset, threshold);
      } else {
        collect_with_threshold_non_last_round(r_idx, num_input, offset, threshold);
      }
      return false;
    };
    if constexpr (NUM_ROUNDS == 1) {  // fast path for 1-round refine.
      if (s_refine_overflow) {
        if (tx < RADIX + 1) s_histogram[tx] = 0;
        __syncthreads();

        auto build_full_threshold_hist = [&](auto raw_input, int /*index*/) {
          const auto coarse_bin = static_cast<int>(Traits::ToCoarseKey(raw_input));
          if (coarse_bin == threshold_bin) {
            const auto ordered = Traits::ToOrdered(raw_input);
            const auto sub_bin = ordered & 0xFF;
            atomicAdd(&s_histogram[sub_bin], 1);
          }
        };

        for_each_score_full(build_full_threshold_hist);
        __syncthreads();

        if (tx == 0) {
          s_threshold_bin_id = 0;
          s_last_remain = 0;
        }
        __syncthreads();

        update_refine_threshold(/*next_input_idx=*/0, std::false_type{});

        const auto threshold = s_threshold_bin_id;

        // Keep s_counter continuity: it already counts coarse_bin > threshold_bin
        // elements collected in filter_and_add_to_histogram. Here we append
        // threshold-bin refined winners after that prefix.
        auto collect_from_full_threshold_bin = [&](auto raw_input, int index) {
          const auto coarse_bin = static_cast<int>(Traits::ToCoarseKey(raw_input));
          if (coarse_bin != threshold_bin) {
            return;
          }
          const auto sub_bin = Traits::ToOrdered(raw_input) & 0xFF;
          collect_gt_and_nondet_eq_threshold(static_cast<int>(sub_bin), threshold, index,
                                             /*allow_eq_claim=*/true);
        };

        for_each_score_full(collect_from_full_threshold_bin);
        __syncthreads();
        if constexpr (DETERMINISTIC) {
          int eq_needed = s_last_remain;
          collect_det_eq_pivot(static_cast<OrderedType>((static_cast<int>(threshold_bin) << 8) |
                                                        static_cast<int>(threshold)),
                               eq_needed);
        }
      } else {
        const int round = 0;
        const auto r_idx = round % 2;
        const int offset = FIRST_SHIFT;
        run_refine_round(r_idx, offset, std::true_type{});
        if constexpr (DETERMINISTIC) {
          collect_det_eq_pivot(build_det_pivot(/*stop_round=*/0), topk);
        }
      }
    } else {
      // Multi-round refine path (float32): if any refine-buffer overflow is detected,
      // switch to a correctness-first full rebuild of the threshold-bin selection.
      // This fallback may be slower than the fast path, but avoids partial-state corruption.
      int det_stop_round = NUM_ROUNDS - 1;
      if (!s_refine_overflow) {
#pragma unroll
        for (int round = 0; round < NUM_ROUNDS; ++round) {
          const auto r_idx = round % 2;
          const int offset = FIRST_SHIFT - round * 8;
          if (round == NUM_ROUNDS - 1) {
            if (run_refine_round(r_idx, offset, std::true_type{})) {
              det_stop_round = round;
              break;
            }
          } else {
            if (run_refine_round(r_idx, offset, std::false_type{})) {
              det_stop_round = round;
              break;
            }
          }
          if (s_refine_overflow) {
            break;
          }
        }
      }
      if constexpr (DETERMINISTIC) {
        if (!s_refine_overflow) {
          collect_det_eq_pivot(build_det_pivot(det_stop_round), topk);
        }
      }
      // run_refine_round can set s_refine_overflow during the loop above, so this
      // check is intentionally separate from the first if (!s_refine_overflow).
      if (s_refine_overflow) {
        static_assert(sizeof(OrderedType) == 4,
                      "Multi-round overflow fallback expects 32-bit ordered keys.");

        uint32_t topk_remain = static_cast<uint32_t>(topk_after_coarse);
        uint8_t threshold_bytes[NUM_ROUNDS];
#pragma unroll
        for (int i = 0; i < NUM_ROUNDS; ++i) {
          threshold_bytes[i] = 0xFF;
        }
        int stop_round = NUM_ROUNDS - 1;

#pragma unroll
        for (int round = 0; round < NUM_ROUNDS; ++round) {
          const int offset = FIRST_SHIFT - round * 8;

          if (tx < RADIX + 1) s_histogram[tx] = 0;
          __syncthreads();

          auto build_hist = [&](auto raw_input, int /*index*/) {
            const auto coarse_bin = static_cast<int>(Traits::ToCoarseKey(raw_input));
            if (coarse_bin != threshold_bin) {
              return;
            }
            const auto ordered = static_cast<uint32_t>(Traits::ToOrdered(raw_input));
            bool prefix_match = true;
#pragma unroll
            for (int prev = 0; prev < round; ++prev) {
              const int prev_offset = FIRST_SHIFT - prev * 8;
              if (static_cast<uint8_t>((ordered >> prev_offset) & 0xFF) != threshold_bytes[prev]) {
                prefix_match = false;
              }
            }
            if (prefix_match) {
              const auto sub_bin = (ordered >> offset) & 0xFF;
              atomicAdd(&s_histogram[sub_bin], 1);
            }
          };
          for_each_score_full(build_hist);
          __syncthreads();

          run_cumsum();
          if (tx < RADIX && s_histogram[tx] > static_cast<int>(topk_remain) &&
              s_histogram[tx + 1] <= static_cast<int>(topk_remain)) {
            s_threshold_bin_id = tx;
          }
          __syncthreads();

          const int threshold = s_threshold_bin_id;
          threshold_bytes[round] = static_cast<uint8_t>(threshold);
          topk_remain -= static_cast<uint32_t>(s_histogram[threshold + 1]);

          __syncthreads();
          if (topk_remain == 0) {
            stop_round = round;
            break;
          }
        }

        uint32_t pivot = 0;
#pragma unroll
        for (int round = 0; round < NUM_ROUNDS; ++round) {
          const int offset = FIRST_SHIFT - round * 8;
          uint32_t byte = static_cast<uint32_t>(threshold_bytes[round]);
          if (topk_remain == 0 && round > stop_round) {
            byte = 0xFFu;
          }
          pivot |= (byte << offset);
        }
        const int eq_needed = static_cast<int>(topk_remain);

        // Overflow can happen after partial writes to s_indices/s_counter in earlier rounds.
        // Reset and rebuild from full scans to avoid mixing stale partial state.
        if (tx == 0) {
          s_counter = 0;
          s_last_remain = eq_needed;
        }
        __syncthreads();

        // Re-collect all winners from scratch:
        //   1) coarse_bin > threshold_bin
        //   2) threshold_bin entries with ordered > pivot
        //   3) first eq_needed entries where ordered == pivot
        auto collect_by_pivot = [&](auto raw_input, int index) {
          const auto coarse_bin = static_cast<int>(Traits::ToCoarseKey(raw_input));
          if (coarse_bin > threshold_bin) {
            collect_gt_and_nondet_eq_threshold(coarse_bin, threshold_bin, index,
                                               /*allow_eq_claim=*/false);
            return;
          }
          if (coarse_bin != threshold_bin) {
            return;
          }
          const auto ordered = static_cast<uint32_t>(Traits::ToOrdered(raw_input));
          collect_gt_and_nondet_eq_threshold(ordered, pivot, index, eq_needed > 0);
        };
        for_each_score_full(collect_by_pivot);
        __syncthreads();
        if constexpr (DETERMINISTIC) {
          collect_det_eq_pivot(static_cast<OrderedType>(pivot), eq_needed);
        }
      }
    }
  }

  // Output phase - mode-specific
#pragma unroll 2
  for (int base = tx; base < static_cast<int>(top_k); base += BLOCK_SIZE) {
    const int idx = s_indices[base];
    if constexpr (MODE == FilteredTopKMode::Plain) {
      dst[base] = static_cast<IdType>(idx);
      if (dst_values != nullptr) dst_values[base] = score[idx];
    } else if constexpr (DETERMINISTIC) {  // transform in SortTopKByIndexKernel
      dst[base] = static_cast<IdType>(idx);
    } else if constexpr (MODE == FilteredTopKMode::PageTable) {
      if constexpr (IsDirectPageTableKernelPolicy<PageTablePolicy, IdType>) {
        dst[base] = src_page_entry[page_table_row_start + idx];
      } else {
        if (dst_raw != nullptr) {
          dst_raw[base] = static_cast<IdType>(idx);
        }
        dst[base] = page_table_policy.transform_index(src_page_entry, page_table_row_start,
                                                      static_cast<uint32_t>(idx));
      }
    } else {  // Ragged
      dst[base] = static_cast<IdType>(idx) + offset_val;
    }
  }
}

// Helper to compute GCD for VEC_SIZE selection
constexpr uint32_t gcd(uint32_t a, uint32_t b) {
  while (b != 0) {
    uint32_t t = b;
    b = a % b;
    a = t;
  }
  return a;
}

// Compute optimal VEC_SIZE based on input stride and dtype
// Returns 1, 2, 4, or 8
template <typename DType>
constexpr int ComputeFilteredTopKVecSize(int64_t input_stride, bool dsa_graph_safe = false) {
  if (dsa_graph_safe) {
    return 1;
  }
  constexpr int MAX_VEC = 16 / sizeof(DType);  // 4 for float32, 8 for fp16/bf16
  // Use GCD to find largest power-of-2 divisor
  const uint32_t g = gcd(static_cast<uint32_t>(input_stride), static_cast<uint32_t>(MAX_VEC));
  return static_cast<int>(g);
}

template <bool WITH_VALUES, uint32_t BLOCK_THREADS, uint32_t ITEMS_PER_THREAD, typename DType>
struct SortTopKByIndexBlockRadixSort;

template <uint32_t BLOCK_THREADS, uint32_t ITEMS_PER_THREAD, typename DType>
struct SortTopKByIndexBlockRadixSort<true, BLOCK_THREADS, ITEMS_PER_THREAD, DType> {
  using Type = cub::BlockRadixSort<uint32_t, BLOCK_THREADS, ITEMS_PER_THREAD, DType>;
};

template <uint32_t BLOCK_THREADS, uint32_t ITEMS_PER_THREAD, typename DType>
struct SortTopKByIndexBlockRadixSort<false, BLOCK_THREADS, ITEMS_PER_THREAD, DType> {
  using Type = cub::BlockRadixSort<uint32_t, BLOCK_THREADS, ITEMS_PER_THREAD>;
};

template <bool SORT_LOCAL_INDICES, FilteredTopKMode MODE, uint32_t BLOCK_THREADS,
          uint32_t ITEMS_PER_THREAD, typename DType, typename IdType,
          typename PageTablePolicy = DirectPageTableKernelPolicy<IdType>, typename... PageTableArgs>
__global__ void __launch_bounds__(BLOCK_THREADS)
    FinalizeTopKIndicesKernel(IdType* output_indices, DType* output_values, const IdType* aux_input,
                              int64_t aux_stride, const IdType* page_table_row_starts,
                              const IdType* row_to_batch, uint32_t top_k, uint32_t max_len,
                              PageTableArgs... page_table_args) {
  static_assert(MODE == FilteredTopKMode::PageTable ||
                IsDirectPageTableKernelPolicy<PageTablePolicy, IdType>);
  static_assert(IsValidPageTableKernelArgs<PageTablePolicy, PageTableArgs...>);
  constexpr bool WITH_VALUES = (MODE == FilteredTopKMode::Plain);
  using BlockRadixSortT = typename SortTopKByIndexBlockRadixSort<WITH_VALUES, BLOCK_THREADS,
                                                                 ITEMS_PER_THREAD, DType>::Type;
  __shared__ typename BlockRadixSortT::TempStorage temp_storage;
  const PageTablePolicy page_table_policy{page_table_args...};

  const uint32_t row = blockIdx.x;
  const uint32_t tx = threadIdx.x;
  IdType* row_output = output_indices + static_cast<size_t>(row) * top_k;
  IdType* row_raw_output = nullptr;
  if constexpr (!IsDirectPageTableKernelPolicy<PageTablePolicy, IdType>) {
    row_raw_output = page_table_policy.get_row_raw_output(row, top_k);
  }

  uint32_t keys[ITEMS_PER_THREAD];
  DType values[ITEMS_PER_THREAD];

#pragma unroll
  for (uint32_t i = 0; i < ITEMS_PER_THREAD; ++i) {
    uint32_t pos = tx * ITEMS_PER_THREAD + i;
    if (pos < top_k) {
      IdType idx = row_output[pos];
      keys[i] = (idx >= 0) ? static_cast<uint32_t>(idx) : ~0u;
      if constexpr (MODE == FilteredTopKMode::Plain) {
        values[i] = output_values[static_cast<size_t>(row) * top_k + pos];
      }
    } else {
      keys[i] = ~0u;
      if constexpr (MODE == FilteredTopKMode::Plain) {
        values[i] = DType(0);
      }
    }
  }

  if constexpr (SORT_LOCAL_INDICES) {
    int end_bit = 32 - __clz(max_len);
    if constexpr (MODE == FilteredTopKMode::Plain) {
      BlockRadixSortT(temp_storage).Sort(keys, values, 0, end_bit);
    } else {
      BlockRadixSortT(temp_storage).Sort(keys, 0, end_bit);
    }
  }

  const IdType* src_page_entry = nullptr;
  IdType offset = 0;
  IdType page_table_row_start = 0;
  if constexpr (MODE == FilteredTopKMode::PageTable) {
    const uint32_t batch_idx = (row_to_batch != nullptr) ? row_to_batch[row] : row;
    src_page_entry = aux_input + static_cast<int64_t>(batch_idx) * aux_stride;
    page_table_row_start = (page_table_row_starts != nullptr) ? page_table_row_starts[row] : 0;
  } else if constexpr (MODE == FilteredTopKMode::Ragged) {
    offset = aux_input[row];
  }

#pragma unroll
  for (uint32_t i = 0; i < ITEMS_PER_THREAD; ++i) {
    uint32_t pos = tx * ITEMS_PER_THREAD + i;
    if (pos < top_k) {
      uint32_t idx = keys[i];
      if constexpr (MODE == FilteredTopKMode::Plain) {
        row_output[pos] = static_cast<IdType>(idx);
        output_values[static_cast<size_t>(row) * top_k + pos] = values[i];
      } else if constexpr (MODE == FilteredTopKMode::PageTable) {
        if constexpr (!IsDirectPageTableKernelPolicy<PageTablePolicy, IdType>) {
          if (row_raw_output != nullptr) {
            row_raw_output[pos] = (idx != ~0u) ? static_cast<IdType>(idx) : static_cast<IdType>(-1);
          }
        }
        row_output[pos] = (idx != ~0u) ? page_table_policy.transform_index(
                                             src_page_entry, page_table_row_start, idx)
                                       : static_cast<IdType>(-1);
      } else {  // Ragged
        row_output[pos] =
            (idx != ~0u) ? static_cast<IdType>(idx) + offset : static_cast<IdType>(-1);
      }
    }
  }
}

template <bool SORT_LOCAL_INDICES, FilteredTopKMode MODE, typename DType, typename IdType,
          typename PageTablePolicy = DirectPageTableKernelPolicy<IdType>>
cudaError_t LaunchFinalizeTopKIndices(IdType* output_indices, DType* output_values,
                                      const IdType* aux_input, int64_t aux_stride,
                                      const IdType* page_table_row_starts,
                                      const IdType* row_to_batch, uint32_t num_rows,
                                      uint32_t top_k_val, uint32_t max_len, cudaStream_t stream = 0,
                                      const PageTablePolicy& page_table_policy = {}) {
  static_assert(IsValidPageTablePolicy<PageTablePolicy>);
  // Block-local sort variants cover at most 256 * 8 = 2048 elements.
  if (top_k_val > 2048) {
    return cudaErrorInvalidValue;
  }
  if constexpr (MODE == FilteredTopKMode::Plain) {
    if (top_k_val <= 1) {
      return cudaSuccess;
    }
  }
  if (top_k_val == 0) {
    return cudaSuccess;
  }

  dim3 grid(num_rows);
  auto launch_finalize = [&](auto kernel, uint32_t threads,
                             auto... page_table_args) -> cudaError_t {
    dim3 block(threads);
    void* args[] = {&output_indices,        &output_values, &aux_input, &aux_stride,
                    &page_table_row_starts, &row_to_batch,  &top_k_val, &max_len,
                    &page_table_args...};
    return cudaLaunchKernel((void*)kernel, grid, block, args, 0, stream);
  };

#define LAUNCH_FINALIZE_KERNEL(THREADS, ITEMS_PER_THREAD)                                       \
  do {                                                                                          \
    if constexpr (!std::is_empty_v<PageTablePolicy>) {                                          \
      status = launch_finalize(                                                                 \
          FinalizeTopKIndicesKernel<SORT_LOCAL_INDICES, MODE, THREADS, ITEMS_PER_THREAD, DType, \
                                    IdType, PageTablePolicy, PageTablePolicy>,                  \
          THREADS, page_table_policy);                                                          \
    } else {                                                                                    \
      status = launch_finalize(                                                                 \
          FinalizeTopKIndicesKernel<SORT_LOCAL_INDICES, MODE, THREADS, ITEMS_PER_THREAD, DType, \
                                    IdType, PageTablePolicy>,                                   \
          THREADS);                                                                             \
    }                                                                                           \
  } while (0)

  cudaError_t status;
  if (top_k_val <= 128) {
    LAUNCH_FINALIZE_KERNEL(32, 4);
  } else if (top_k_val <= 256) {
    LAUNCH_FINALIZE_KERNEL(32, 8);
  } else if (top_k_val <= 512) {
    LAUNCH_FINALIZE_KERNEL(64, 8);
  } else if (top_k_val <= 576) {
    LAUNCH_FINALIZE_KERNEL(64, 9);
  } else if (top_k_val <= 1024) {
    LAUNCH_FINALIZE_KERNEL(128, 8);
  } else {
    LAUNCH_FINALIZE_KERNEL(256, 8);
  }
#undef LAUNCH_FINALIZE_KERNEL
  return status;
}

/*!
 * \brief CUB stable radix sort: sorts top-k by value descending, carrying indices.
 *
 * Uses 32-bit flipped ordered value as key and 32-bit index as satellite data.
 * Since radix sort is stable, equal values preserve their prior relative order.
 * When preceded by an index sort, this yields (value desc, index asc) ordering.
 */
template <uint32_t BLOCK_THREADS, uint32_t ITEMS_PER_THREAD, typename IdType, typename DType>
__global__ void __launch_bounds__(BLOCK_THREADS)
    StableSortTopKByValueKernel(IdType* output_indices, DType* output_values, uint32_t k,
                                uint32_t /*max_len*/) {
  using Traits = RadixTopKTraits<DType>;
  using OrderedType = typename Traits::OrderedType;
  using BlockRadixSortT = cub::BlockRadixSort<uint32_t, BLOCK_THREADS, ITEMS_PER_THREAD, uint32_t>;
  __shared__ typename BlockRadixSortT::TempStorage temp_storage;

  const uint32_t row = blockIdx.x;
  const uint32_t tx = threadIdx.x;

  IdType* row_indices = output_indices + static_cast<size_t>(row) * k;
  DType* row_values = output_values + static_cast<size_t>(row) * k;

  uint32_t keys[ITEMS_PER_THREAD];
  uint32_t indices[ITEMS_PER_THREAD];

#pragma unroll
  for (uint32_t i = 0; i < ITEMS_PER_THREAD; i++) {
    uint32_t pos = tx * ITEMS_PER_THREAD + i;
    if (pos < k) {
      OrderedType ordered = Traits::ToOrdered(row_values[pos]);
      keys[i] = static_cast<uint32_t>(static_cast<OrderedType>(~ordered));
      indices[i] = static_cast<uint32_t>(row_indices[pos]);
    } else {
      keys[i] = ~0u;
      indices[i] = ~0u;
    }
  }

  constexpr int end_bit = sizeof(OrderedType) * 8;
  BlockRadixSortT(temp_storage).Sort(keys, indices, 0, end_bit);

#pragma unroll
  for (uint32_t i = 0; i < ITEMS_PER_THREAD; i++) {
    uint32_t pos = tx * ITEMS_PER_THREAD + i;
    if (pos < k) {
      row_indices[pos] = static_cast<IdType>(indices[i]);
      OrderedType ordered = static_cast<OrderedType>(~static_cast<OrderedType>(keys[i]));
      row_values[pos] = Traits::FromOrdered(ordered);
    }
  }
}

template <typename DType, typename IdType>
cudaError_t StableSortTopKByValue(IdType* output_indices, DType* output_values, uint32_t num_rows,
                                  uint32_t top_k_val, uint32_t max_len, cudaStream_t stream = 0) {
  // Block-local sort variants cover at most 256 * 8 = 2048 elements.
  if (top_k_val > 2048) {
    return cudaErrorInvalidValue;
  }
  if (top_k_val <= 1) {
    return cudaSuccess;
  }

  dim3 grid(num_rows);
  void* args[] = {&output_indices, &output_values, &top_k_val, &max_len};
  auto launch_sort = [&](auto kernel, uint32_t threads) -> cudaError_t {
    dim3 block(threads);
    return cudaLaunchKernel((void*)kernel, grid, block, args, 0, stream);
  };

  cudaError_t status;
  if (top_k_val <= 128) {
    status = launch_sort(StableSortTopKByValueKernel<32, 4, IdType, DType>, 32);
  } else if (top_k_val <= 256) {
    status = launch_sort(StableSortTopKByValueKernel<32, 8, IdType, DType>, 32);
  } else if (top_k_val <= 512) {
    status = launch_sort(StableSortTopKByValueKernel<64, 8, IdType, DType>, 64);
  } else if (top_k_val <= 576) {
    status = launch_sort(StableSortTopKByValueKernel<64, 9, IdType, DType>, 64);
  } else if (top_k_val <= 1024) {
    status = launch_sort(StableSortTopKByValueKernel<128, 8, IdType, DType>, 128);
  } else {
    status = launch_sort(StableSortTopKByValueKernel<256, 8, IdType, DType>, 256);
  }
  return status;
}

template <FilteredTopKMode MODE, typename DType, typename IdType,
          typename PageTablePolicy = DirectPageTableKernelPolicy<IdType>>
cudaError_t LaunchFilteredTopKUnified(DType* input, IdType* output, DType* aux_output,
                                      const IdType* aux_input, int64_t aux_stride,
                                      const IdType* row_to_batch, const IdType* lengths,
                                      const IdType* row_starts, const IdType* page_table_row_starts,
                                      uint32_t num_rows, uint32_t top_k_val, uint32_t max_len,
                                      bool deterministic = false,
                                      TopKTieBreak tie_break = TopKTieBreak::None,
                                      cudaStream_t stream = 0, bool dsa_graph_safe = false,
                                      const PageTablePolicy& page_table_policy = {}) {
  static_assert(IsValidPageTablePolicy<PageTablePolicy>);
  constexpr size_t smem_size = FILTERED_TOPK_SMEM_DYNAMIC;
  constexpr int MAX_VEC = 16 / sizeof(DType);
  const bool deterministic_selection = deterministic || tie_break != TopKTieBreak::None;

  dim3 grid(num_rows);
  dim3 block(FILTERED_TOPK_BLOCK_THREADS);
  const auto row_stride = page_table_policy.get_input_stride(max_len);
  int vec_size = (row_starts != nullptr && MODE != FilteredTopKMode::Plain)
                     ? 1
                     : ComputeFilteredTopKVecSize<DType>(row_stride, dsa_graph_safe);
  vec_size = page_table_policy.refine_vec_size(input, vec_size);

  auto launch_kernel = [&](auto kernel, auto... page_table_args) -> cudaError_t {
    FLASHINFER_CUDA_CALL(
        cudaFuncSetAttribute(kernel, cudaFuncAttributeMaxDynamicSharedMemorySize, smem_size));
    void* args[] = {&input,
                    &output,
                    &aux_output,
                    &aux_input,
                    &aux_stride,
                    &row_to_batch,
                    &lengths,
                    &row_starts,
                    &page_table_row_starts,
                    &num_rows,
                    &top_k_val,
                    &max_len,
                    &page_table_args...};
    return cudaLaunchKernel((void*)kernel, grid, block, args, smem_size, stream);
  };

#define LAUNCH_FILTERED_KERNEL(VS, DET, TIE)                                                       \
  do {                                                                                             \
    if constexpr (!std::is_empty_v<PageTablePolicy>) {                                             \
      auto kernel = FilteredTopKUnifiedKernel<DType, IdType, VS, DET, MODE, TIE, PageTablePolicy,  \
                                              PageTablePolicy>;                                    \
      FLASHINFER_CUDA_CALL(launch_kernel(kernel, page_table_policy));                              \
    } else {                                                                                       \
      auto kernel = FilteredTopKUnifiedKernel<DType, IdType, VS, DET, MODE, TIE, PageTablePolicy>; \
      FLASHINFER_CUDA_CALL(launch_kernel(kernel));                                                 \
    }                                                                                              \
  } while (0)

#define DISPATCH_VEC_SIZE(VS)                                \
  if (vec_size == VS) {                                      \
    if (!deterministic_selection) {                          \
      LAUNCH_FILTERED_KERNEL(VS, false, TopKTieBreak::None); \
    } else if (tie_break == TopKTieBreak::Small) {           \
      LAUNCH_FILTERED_KERNEL(VS, true, TopKTieBreak::Small); \
    } else if (tie_break == TopKTieBreak::Large) {           \
      LAUNCH_FILTERED_KERNEL(VS, true, TopKTieBreak::Large); \
    } else {                                                 \
      LAUNCH_FILTERED_KERNEL(VS, true, TopKTieBreak::None);  \
    }                                                        \
    return cudaSuccess;                                      \
  }

  DISPATCH_VEC_SIZE(1)
  DISPATCH_VEC_SIZE(2)
  DISPATCH_VEC_SIZE(4)
  if constexpr (MAX_VEC >= 8) {
    DISPATCH_VEC_SIZE(8)
  }
#undef DISPATCH_VEC_SIZE
#undef LAUNCH_FILTERED_KERNEL

  return cudaSuccess;
}

// Launch functions with VEC_SIZE and BLOCK_THREADS dispatch - using unified kernel
template <typename DType, typename IdType,
          typename PageTablePolicy = DirectPageTableKernelPolicy<IdType>>
cudaError_t FilteredTopKPageTableTransform(
    DType* input, IdType* output_page_table, const IdType* src_page_table, int64_t src_stride,
    const IdType* row_to_batch, IdType* lengths, const IdType* row_starts,
    const IdType* page_table_row_starts, uint32_t num_rows, uint32_t top_k_val, uint32_t max_len,
    bool deterministic = false, TopKTieBreak tie_break = TopKTieBreak::None,
    cudaStream_t stream = 0, bool dsa_graph_safe = false,
    const PageTablePolicy& page_table_policy = {}) {
  static_assert(IsValidPageTablePolicy<PageTablePolicy>);
  DType* aux_output = nullptr;  // Not used for PageTable mode
  return LaunchFilteredTopKUnified<FilteredTopKMode::PageTable, DType, IdType, PageTablePolicy>(
      input, output_page_table, aux_output, src_page_table, src_stride, row_to_batch, lengths,
      row_starts, page_table_row_starts, num_rows, top_k_val, max_len, deterministic, tie_break,
      stream, dsa_graph_safe, page_table_policy);
}

template <typename DType, typename IdType>
cudaError_t FilteredTopKRaggedTransform(DType* input, IdType* output_indices, const IdType* offsets,
                                        IdType* lengths, const IdType* row_starts,
                                        uint32_t num_rows, uint32_t top_k_val, uint32_t max_len,
                                        bool deterministic = false,
                                        TopKTieBreak tie_break = TopKTieBreak::None,
                                        cudaStream_t stream = 0, bool dsa_graph_safe = false) {
  DType* aux_output = nullptr;                    // Not used for Ragged mode
  int64_t aux_stride = 0;                         // Not used for Ragged mode
  const IdType* page_table_row_starts = nullptr;  // Not used for Ragged mode
  const IdType* row_to_batch = nullptr;           // Not used for Ragged mode
  return LaunchFilteredTopKUnified<FilteredTopKMode::Ragged, DType, IdType>(
      input, output_indices, aux_output, offsets, aux_stride, row_to_batch, lengths, row_starts,
      page_table_row_starts, num_rows, top_k_val, max_len, deterministic, tie_break, stream,
      dsa_graph_safe);
}

template <typename DType, typename IdType>
cudaError_t FilteredTopK(DType* input, IdType* output_indices, DType* output_values,
                         const IdType* lengths, uint32_t num_rows, uint32_t top_k_val,
                         uint32_t max_len, bool deterministic = false,
                         TopKTieBreak tie_break = TopKTieBreak::None, cudaStream_t stream = 0,
                         bool dsa_graph_safe = false) {
  const IdType* aux_input = nullptr;              // Not used for Plain mode
  int64_t aux_stride = 0;                         // Not used for Plain mode
  const IdType* row_starts = nullptr;             // Not used for Plain mode
  const IdType* page_table_row_starts = nullptr;  // Not used for Plain mode
  const IdType* row_to_batch = nullptr;           // Not used for Plain mode
  return LaunchFilteredTopKUnified<FilteredTopKMode::Plain, DType, IdType>(
      input, output_indices, output_values, aux_input, aux_stride, row_to_batch, lengths,
      row_starts, page_table_row_starts, num_rows, top_k_val, max_len, deterministic, tie_break,
      stream, dsa_graph_safe);
}

/*!
 * \brief Check if the GPU supports enough shared memory for FilteredTopK algorithm.
 *
 * FilteredTopK requires 128KB dynamic shared memory. This function checks if the
 * current GPU's max shared memory per SM is sufficient.
 *
 * \return true if GPU supports FilteredTopK, false otherwise
 */
inline bool CanImplementFilteredTopK() {
  int device_id;
  if (cudaGetDevice(&device_id) != cudaSuccess) return false;
  int max_smem_per_sm;
  if (cudaDeviceGetAttribute(&max_smem_per_sm, cudaDevAttrMaxSharedMemoryPerMultiprocessor,
                             device_id) != cudaSuccess) {
    return false;
  }
  return static_cast<size_t>(max_smem_per_sm) >= FILTERED_TOPK_SMEM_DYNAMIC;
}

// Algorithm override for benchmarking (controlled by FLASHINFER_TOPK_ALGO env var)
enum class TopKAlgoOverride { AUTO, FILTERED, MULTI_CTA };

inline TopKAlgoOverride GetTopKAlgoOverride() {
  const char* env = std::getenv("FLASHINFER_TOPK_ALGO");
  if (env == nullptr) return TopKAlgoOverride::AUTO;
  if (std::strcmp(env, "filtered") == 0) return TopKAlgoOverride::FILTERED;
  if (std::strcmp(env, "multi_cta") == 0) return TopKAlgoOverride::MULTI_CTA;
  return TopKAlgoOverride::AUTO;
}

/*!
 * \brief Unified heuristic to decide whether to use FilteredTopK or Multi-CTA RadixTopK.
 *
 * \tparam DType Data type (affects threshold due to memory bandwidth considerations)
 * \param num_rows Number of rows (batch size)
 * \param top_k_val Number of top elements to select
 * \param max_len Maximum sequence length
 * \param deterministic Whether deterministic top-k path is requested
 * \param tie_break Mode of tie-break
 * \return true if FilteredTopK should be used, false for Multi-CTA RadixTopK
 */
template <typename DType>
inline bool ShouldUseFilteredTopK(uint32_t num_rows, uint32_t top_k_val, uint32_t max_len,
                                  bool deterministic, TopKTieBreak tie_break,
                                  bool dsa_graph_safe = false) {
  // DSA graph safe mode alwaus uses FilteredTopK
  if (dsa_graph_safe) {
    return true;
  }
  // Tie-break modes are only supported by FilteredTopK
  if (tie_break != TopKTieBreak::None) {
    return true;
  }

  // Check if GPU supports enough shared memory for FilteredTopK
  const bool gpu_supports_filtered = CanImplementFilteredTopK();
  const bool k_fits_filtered = (top_k_val <= FILTERED_TOPK_MAX_K) && (max_len > top_k_val);

  if (!gpu_supports_filtered || !k_fits_filtered) {
    return false;
  }

  // Check for algorithm override
  const TopKAlgoOverride algo_override = GetTopKAlgoOverride();
  if (algo_override == TopKAlgoOverride::FILTERED) return true;
  if (algo_override == TopKAlgoOverride::MULTI_CTA) return false;

  // 16-bit types: simpler threshold
  // 32-bit types: more nuanced heuristic
  if (deterministic) {
    if constexpr (sizeof(DType) <= 2) {
      return num_rows > (max_len / 256);
    } else {
      if (max_len <= 16384) {
        return true;
      } else {
        const uint32_t batch_threshold = std::min(64u, std::max(16u, max_len / 4096));
        return num_rows >= batch_threshold;
      }
    }
  }

  if constexpr (sizeof(DType) <= 2) {
    return (max_len <= 16384);
  } else {
    if (max_len <= 32768) {
      return true;
    } else {
      const uint32_t batch_threshold = max_len / 16384;
      return (num_rows > batch_threshold);
    }
  }
}

// Dispatch functions with heuristics
template <typename DType, typename IdType,
          typename PageTablePolicy = DirectPageTableKernelPolicy<IdType>>
cudaError_t TopKPageTableTransformDispatch(
    DType* input, IdType* output_page_table, const IdType* src_page_table, int64_t src_stride,
    IdType* lengths, const IdType* row_starts, const IdType* row_to_batch, uint32_t num_rows,
    uint32_t top_k_val, uint32_t max_len, RadixRowState* row_states_buffer, bool deterministic,
    TopKTieBreak tie_break = TopKTieBreak::None, cudaStream_t stream = 0,
    bool dsa_graph_safe = false, const IdType* page_table_row_starts = nullptr,
    const PageTablePolicy& page_table_policy = {}) {
  static_assert(IsValidPageTablePolicy<PageTablePolicy>);
  if (page_table_row_starts == nullptr) {
    page_table_row_starts = row_starts;
  }
  const bool require_filtered = dsa_graph_safe || tie_break != TopKTieBreak::None;
  if (require_filtered && (top_k_val > FILTERED_TOPK_MAX_K || !CanImplementFilteredTopK())) {
    return cudaErrorNotSupported;
  }
  if (ShouldUseFilteredTopK<DType>(num_rows, top_k_val, max_len, deterministic, tie_break,
                                   dsa_graph_safe)) {
    FLASHINFER_CUDA_CALL((FilteredTopKPageTableTransform<DType, IdType, PageTablePolicy>(
        input, output_page_table, src_page_table, src_stride, row_to_batch, lengths, row_starts,
        page_table_row_starts, num_rows, top_k_val, max_len, deterministic, tie_break, stream,
        dsa_graph_safe, page_table_policy)));
    if (deterministic) {
      FLASHINFER_CUDA_CALL((LaunchFinalizeTopKIndices<true, FilteredTopKMode::PageTable, uint8_t,
                                                      IdType, PageTablePolicy>(
          output_page_table, static_cast<uint8_t*>(nullptr), src_page_table, src_stride,
          page_table_row_starts, row_to_batch, num_rows, top_k_val, max_len, stream,
          page_table_policy)));
    } else if (tie_break != TopKTieBreak::None) {
      FLASHINFER_CUDA_CALL((LaunchFinalizeTopKIndices<false, FilteredTopKMode::PageTable, uint8_t,
                                                      IdType, PageTablePolicy>(
          output_page_table, static_cast<uint8_t*>(nullptr), src_page_table, src_stride,
          page_table_row_starts, row_to_batch, num_rows, top_k_val, max_len, stream,
          page_table_policy)));
    }
    return cudaSuccess;
  }
  return RadixTopKPageTableTransformMultiCTA<DType, IdType, PageTablePolicy>(
      input, output_page_table, src_page_table, src_stride, row_to_batch, lengths, row_starts,
      page_table_row_starts, num_rows, top_k_val, max_len, row_states_buffer, deterministic, stream,
      page_table_policy);
}

template <typename DType, typename IdType>
cudaError_t TopKRaggedTransformDispatch(DType* input, IdType* output_indices, const IdType* offsets,
                                        IdType* lengths, const IdType* row_starts,
                                        uint32_t num_rows, uint32_t top_k_val, uint32_t max_len,
                                        RadixRowState* row_states_buffer, bool deterministic,
                                        TopKTieBreak tie_break = TopKTieBreak::None,
                                        cudaStream_t stream = 0, bool dsa_graph_safe = false) {
  const bool require_filtered = dsa_graph_safe || tie_break != TopKTieBreak::None;
  if (require_filtered && (top_k_val > FILTERED_TOPK_MAX_K || !CanImplementFilteredTopK())) {
    return cudaErrorNotSupported;
  }
  if (ShouldUseFilteredTopK<DType>(num_rows, top_k_val, max_len, deterministic, tie_break,
                                   dsa_graph_safe)) {
    FLASHINFER_CUDA_CALL((FilteredTopKRaggedTransform<DType, IdType>(
        input, output_indices, offsets, lengths, row_starts, num_rows, top_k_val, max_len,
        deterministic, tie_break, stream, dsa_graph_safe)));
    if (deterministic) {
      FLASHINFER_CUDA_CALL(
          (LaunchFinalizeTopKIndices<true, FilteredTopKMode::Ragged, uint8_t, IdType>(
              output_indices, static_cast<uint8_t*>(nullptr), offsets, 0, nullptr, nullptr,
              num_rows, top_k_val, max_len, stream)));
    } else if (tie_break != TopKTieBreak::None) {
      FLASHINFER_CUDA_CALL(
          (LaunchFinalizeTopKIndices<false, FilteredTopKMode::Ragged, uint8_t, IdType>(
              output_indices, static_cast<uint8_t*>(nullptr), offsets, 0, nullptr, nullptr,
              num_rows, top_k_val, max_len, stream)));
    }
    return cudaSuccess;
  }
  return RadixTopKRaggedTransformMultiCTA<DType, IdType>(input, output_indices, offsets, lengths,
                                                         row_starts, num_rows, top_k_val, max_len,
                                                         row_states_buffer, deterministic, stream);
}

template <typename DType, typename IdType>
cudaError_t TopKDispatch(DType* input, IdType* output_indices, DType* output_values,
                         uint32_t num_rows, uint32_t top_k_val, uint32_t max_len,
                         RadixRowState* row_states_buffer, bool sorted_output = false,
                         bool deterministic = false, TopKTieBreak tie_break = TopKTieBreak::None,
                         cudaStream_t stream = 0, bool dsa_graph_safe = false) {
  const bool require_filtered = dsa_graph_safe || tie_break != TopKTieBreak::None;
  if (require_filtered && (top_k_val > FILTERED_TOPK_MAX_K || !CanImplementFilteredTopK())) {
    return cudaErrorNotSupported;
  }
  if (ShouldUseFilteredTopK<DType>(num_rows, top_k_val, max_len, deterministic, tie_break,
                                   dsa_graph_safe)) {
    FLASHINFER_CUDA_CALL((FilteredTopK<DType, IdType>(input, output_indices, output_values, nullptr,
                                                      num_rows, top_k_val, max_len, deterministic,
                                                      tie_break, stream, dsa_graph_safe)));
    if (deterministic) {
      FLASHINFER_CUDA_CALL((LaunchFinalizeTopKIndices<true, FilteredTopKMode::Plain, DType, IdType>(
          output_indices, output_values, nullptr, 0, nullptr, nullptr, num_rows, top_k_val, max_len,
          stream)));
    }
  } else {
    FLASHINFER_CUDA_CALL((RadixTopKMultiCTA<DType, IdType>(
        input, output_indices, output_values, nullptr, num_rows, top_k_val, max_len,
        row_states_buffer, deterministic, stream)));
  }
  if (sorted_output) {
    FLASHINFER_CUDA_CALL((StableSortTopKByValue<DType, IdType>(
        output_indices, output_values, num_rows, top_k_val, max_len, stream)));
  }
  return cudaSuccess;
}

}  // namespace sampling

}  // namespace flashinfer

#endif  // FLASHINFER_TOPK_CUH_
