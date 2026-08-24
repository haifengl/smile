#ifndef FLASHINFER_TOPK_CLUSTERS_CUH_
#define FLASHINFER_TOPK_CLUSTERS_CUH_
#include <cooperative_groups.h>
#include <cuda_fp16.h>
#include <tvm/ffi/error.h>

#include "topk_common.cuh"
#include "vec_dtypes.cuh"

namespace flashinfer {
namespace sampling {

namespace cg = cooperative_groups;

__device__ __forceinline__ uint32_t getLaneId() {
  uint32_t laneId;
  asm("mov.u32 %0, %%laneid;" : "=r"(laneId));
  return laneId;
}

template <int num_threads>
__device__ __forceinline__ uint32_t InclusiveWarpDownScan(uint32_t val) {
#pragma unroll
  for (int i = 1; i <= (num_threads >> 1); i <<= 1)  // 16 = LANE_COUNT >> 1
  {
    const uint32_t t = __shfl_down_sync(0xffffffff, val, i, 32);
    if (getLaneId() < num_threads - i) val += t;
  }

  return val;
}

__device__ __forceinline__ int cum_sum(int* s_hist_buf, int* reduce_buf) {
  constexpr int RADIX = 256;
  const int warp_idx = threadIdx.x / 32;

  int val = 0;
  if (threadIdx.x < RADIX) {
    val = s_hist_buf[threadIdx.x];
    val = InclusiveWarpDownScan<32>(val);
    if (getLaneId() == 0 && warp_idx < 8) {
      reduce_buf[warp_idx] = val;
    }
  }
  __syncthreads();
  if (threadIdx.x < 32) {
    int cum_val = InclusiveWarpDownScan<8>(threadIdx.x < 8 ? reduce_buf[threadIdx.x] : 0);
    __syncwarp();
    if (threadIdx.x < 8) {
      reduce_buf[threadIdx.x] = cum_val;
    }
  }
  __syncthreads();
  if (warp_idx < 7) {
    val += reduce_buf[warp_idx + 1];
  }
  return val;
}

struct PackedCachedData {
  uint32_t bits;
  int index;
};

struct TopkResults {
  int local_topk_num;
  int local_topk_start;
  int* local_topk_inds;
};

// Global overflow cache layout (per cluster section):
//   g_bits [ ph * NClusters * overflow_stride + block_id * overflow_stride + i
//   ] g_inds [ overflow_stride * 2 * NClusters  + same offset ]
// Total per cluster: overflow_stride * 4 * NClusters int32 elements.

// Cluster cooperative groups (cg::this_cluster, cluster barriers, etc.) require
// SM 9.0 (Hopper) or newer.
#if !defined(__CUDA_ARCH__) || __CUDA_ARCH__ >= 900

template <typename T, int NClusters, bool PDL_ENABLED>
__device__ __forceinline__ TopkResults fast_topk_cuda_v4(
    const T* __restrict__ logits,                    // Input logits [max_num_pages * 64]
    const int* __restrict__ pre_hist,                // Histogram of the first bin, if
                                                     // PRE_HISTOGRAM is enabled, [256]
    PackedCachedData* __restrict__ cached_overflow,  // [overflow_stride * 2]
    const int overflow_stride, const int seq_len, const int num_cached, const int TopK) {
  static_assert(sizeof(T) == 4 || sizeof(T) == 2, "the size of T must be 4 or 2 bytes");

  using Ord = RadixTopKTraits<T>;
  constexpr int NRemainingRounds =
      sizeof(T) - 1;  // each round takes a byte, first round is already done
  constexpr int LShiftStart = sizeof(T) * 8 - 8;  // 24 for float, 8 for half
  constexpr int RADIX = 256;

  alignas(128) extern __shared__ uint8_t shared_cache[];

  uint32_t* s_cached_logit_bits = (uint32_t*)shared_cache;
  int* s_cached_indices = (int*)(2 * num_cached + s_cached_logit_bits);
  int* s_topk_inds = (int*)(2 * num_cached + s_cached_indices);

  int* shared_hist = s_topk_inds + TopK;                      // [3 * 256]
  int* shared_final_idx_count = shared_hist + 3 * 256;        // [1]
  int* shared_num_cached_count = shared_final_idx_count + 1;  // [2]
  int* shared_threshold_bin = shared_num_cached_count + 2;    // [1]
  int* s_cum_reduce_buf = shared_threshold_bin + 1;           // [8]
  int* s_k_remaining_counter = s_cum_reduce_buf + 8;          // [1]

  auto cluster = cg::this_cluster();
  const int block_id = cluster.block_rank();
  const bool radix_thread = threadIdx.x < RADIX;
  constexpr bool RUN_PHASE1 = true;
  constexpr bool ENABLE_CLUSTER_SAFETY = true;

  auto get_cached_overflow = [&](int phase) { return cached_overflow + phase * overflow_stride; };

  auto get_threshold_bin = [&](int hist_idx, int& k_remaining, bool sum_hist = true) {
    __syncthreads();
    // first reduce cum sum locally
    int cum_val = cum_sum(shared_hist + hist_idx * 256, s_cum_reduce_buf);
    if (NClusters > 1 && sum_hist) {
      if (radix_thread) {
        shared_hist[hist_idx * 256 + threadIdx.x] = cum_val;
      }
      cluster.sync();  // now first block in cluster has its local cum sum

      if (radix_thread) {
#pragma unroll
        for (int cl = 0; cl < NClusters - 1; cl++) {
          cum_val += cluster.map_shared_rank(&shared_hist[hist_idx * 256 + threadIdx.x],
                                             (cl + block_id + 1) % NClusters)[0];
        }
        shared_hist[2 * 256 + threadIdx.x] = cum_val;
      }
    } else {
      if (radix_thread) {
        shared_hist[2 * 256 + threadIdx.x] = cum_val;
      }
    }

    __syncthreads();

    int cum_val1 = threadIdx.x < RADIX - 1 ? shared_hist[2 * 256 + threadIdx.x + 1] : 0;

    if (radix_thread && cum_val > k_remaining && cum_val1 <= k_remaining) {
      *shared_threshold_bin = threadIdx.x;
    }

    __syncthreads();
    const int threshold_bin = *shared_threshold_bin;
    if (threshold_bin < RADIX - 1) {
      k_remaining -= shared_hist[2 * 256 + threshold_bin + 1];
    }
    return threshold_bin;
  };

  if (PDL_ENABLED) cudaGridDependencySynchronize();

  if (radix_thread) {
    if (pre_hist != nullptr) {
      shared_hist[threadIdx.x] = pre_hist[threadIdx.x];
    } else {
      shared_hist[threadIdx.x] = 0;
    }
    shared_hist[256 + threadIdx.x] = 0;
  }
  if (threadIdx.x == 0) {
    *shared_final_idx_count = 0;
    *s_k_remaining_counter = 0;
    shared_num_cached_count[0] = 0;
  }

  if (pre_hist == nullptr) {
    __syncthreads();
    for (int i = threadIdx.x + block_id * 1024; i < seq_len; i += 1024 * NClusters) {
      T res = logits[i];

      auto bin = Ord::ToOrdered(res) >> LShiftStart;
      atomicAdd(shared_hist + bin, 1);
    }
  }

  int top_k_remaining = TopK;
  if (RUN_PHASE1) {
    // get_threshold_bin synchronizes
    // if we use the PRE_HISTOGRAM then we don't need to sum the first histogram
    // across clusters
    const int threshold_bin = get_threshold_bin(0, top_k_remaining, pre_hist == nullptr);

    auto compute_phase1 = [&](T logit, int i) {
      auto bits = Ord::ToOrdered(logit);
      int bin = (bits >> LShiftStart);

      if (bin > threshold_bin) {
        int topk_offset = atomicAdd(shared_final_idx_count, 1);
        // if (topk_offset < TopK) {
        s_topk_inds[topk_offset] = i;
        // }
      }

      if (bin == threshold_bin) {
        int cached_offset = atomicAdd(&shared_num_cached_count[0], 1);
        if (cached_offset < num_cached) {
          s_cached_indices[cached_offset] = i;
          s_cached_logit_bits[cached_offset] =
              static_cast<uint32_t>(bits);  // widen to 32 bits if from half
          atomicAdd(shared_hist + 256 + ((bits >> (LShiftStart - 8)) & 0xff), 1);
        } else {
          // Shared buffer full: spill to per-CTA global overflow cache.
          int g_off = cached_offset - num_cached;
          if (g_off < overflow_stride) {
            PackedCachedData cached_res = {static_cast<uint32_t>(bits), i};
            get_cached_overflow(0)[g_off] = cached_res;
            atomicAdd(shared_hist + 256 + ((bits >> (LShiftStart - 8)) & 0xff), 1);
          }
        }
      }
    };
    if (ENABLE_CLUSTER_SAFETY && NClusters > 1) {
      cluster.barrier_arrive();  // arrive at this point to signal that we are
                                 // done with shared_hist[0], which we need in
                                 // distributed shared memory to communicate
                                 // histograms within the cluster, otherwise there
                                 // is a race condition that produces slightly wrong
                                 // results, for slightly better performance
    }

    int tid = block_id * 1024 + threadIdx.x;
    constexpr int VEC_SIZE = 4;
    vec_t<T, VEC_SIZE> score_vec;
    int seq_len_aligned = seq_len / VEC_SIZE * VEC_SIZE;
    for (int i = tid * VEC_SIZE; i < seq_len_aligned; i += NClusters * 1024 * VEC_SIZE) {
      score_vec.cast_load(&logits[i]);
      for (int j = 0; j < VEC_SIZE; j++) {
        compute_phase1(score_vec[j], i + j);
      }
    }
    for (int i = seq_len_aligned + tid; i < seq_len; i += NClusters * 1024) {
      compute_phase1(logits[i], i);
    }
  }
#pragma unroll
  for (int t = 1; t <= NRemainingRounds; t++) {
    const int phase = t % 2;
    // we are now using histogram buffers at phase, and cached_offsets at phase
    // ^ 1 clear the next stage to prepare

    if (ENABLE_CLUSTER_SAFETY && NClusters > 1) {
      cluster.barrier_wait();  // wait because we need to clear shared_hist for this
                               // next histogram; there is a dependency between
                               // get_threshold_bin and phase ^ 1 of the histogram
    }
    if (radix_thread) {
      shared_hist[(phase ^ 1) * 256 + threadIdx.x] = 0;
    }
    if (threadIdx.x == 0) {
      shared_num_cached_count[phase] = 0;
    }
    // get_threshold_bin synchronizes the block
    const int threshold_bin = get_threshold_bin(phase, top_k_remaining);
    if (ENABLE_CLUSTER_SAFETY && NClusters > 1 && t < NRemainingRounds) {
      cluster.barrier_arrive();  // same reasoning as above, for the last
                                 // iteration there is no dependency because no
                                 // more calls to get_threshold_bin
    }
    int raw_buf_len = shared_num_cached_count[phase ^ 1];
    int buf_len = min(num_cached, raw_buf_len);
    int g_buf_len = min(overflow_stride, max(0, raw_buf_len - num_cached));
    bool exceeded_k_remaining_cnt = false;

    // --- Process shared cache ---
    // using cached indices, it's a local slice so don't partition between
    // blocks
    for (int i = threadIdx.x; i < buf_len; i += 1024) {
      uint32_t bits = s_cached_logit_bits[i + (phase ^ 1) * num_cached];
      int cached_idx = s_cached_indices[i + (phase ^ 1) * num_cached];
      int bin = (bits >> (LShiftStart - t * 8)) & 0xff;

      if (bin > threshold_bin) {
        int topk_offset = atomicAdd(shared_final_idx_count, 1);
        s_topk_inds[topk_offset] = cached_idx;
      }
      if (bin == threshold_bin) {
        if (t < NRemainingRounds) {
          int cached_offset = atomicAdd(&shared_num_cached_count[phase], 1);
          if (cached_offset < num_cached) {
            s_cached_indices[cached_offset + phase * num_cached] = cached_idx;
            s_cached_logit_bits[cached_offset + phase * num_cached] = bits;
            atomicAdd(
                shared_hist + (phase ^ 1) * 256 + (bits >> (LShiftStart - (t + 1) * 8) & 0xff), 1);
          } else {
            int g_off = cached_offset - num_cached;
            if (g_off < overflow_stride) {
              PackedCachedData data = {bits, cached_idx};
              get_cached_overflow(phase)[g_off] = data;
              atomicAdd(
                  shared_hist + (phase ^ 1) * 256 + (bits >> (LShiftStart - (t + 1) * 8) & 0xff),
                  1);
            }
          }
        } else {
          // t == NRemainingRounds
          // if top_k_remaining > 0, that means that in the final round, there's still
          // top_k_remaining items left after considering items bigger than the threshold_bin,
          // therefore the top_k_remaining items must be == to the threshold_bin, partitioned
          // amongst the cluster ranks
          if (top_k_remaining > 0 && !exceeded_k_remaining_cnt) {
            // here just pick an arbitrary index, since it should be bit equal as we be bin ==
            // threshold_bin in the last bin
            int k_remaining_cnt = atomicAdd(cluster.map_shared_rank(s_k_remaining_counter, 0), 1);
            if (k_remaining_cnt < top_k_remaining) {
              int off = atomicAdd(shared_final_idx_count, 1);
              s_topk_inds[off] = cached_idx;  // this should always be inbounds since the sum of
              // shared_final_idx_count amongst the cluster ranks +
              // top_k_remaining == TopK
            } else {
              exceeded_k_remaining_cnt = true;
            }
          }
        }
      }
    }

    // --- Process global overflow cache from previous phase ---
    for (int i = threadIdx.x; i < g_buf_len; i += 1024) {
      PackedCachedData data = get_cached_overflow(phase ^ 1)[i];
      uint32_t bits = data.bits;
      int cached_idx = data.index;
      int bin = (bits >> (LShiftStart - t * 8)) & 0xff;

      if (bin > threshold_bin) {
        int topk_offset = atomicAdd(shared_final_idx_count, 1);
        s_topk_inds[topk_offset] = cached_idx;
      }
      if (bin == threshold_bin) {
        if (t < NRemainingRounds) {
          int cached_offset = atomicAdd(&shared_num_cached_count[phase], 1);
          if (cached_offset < num_cached) {
            s_cached_indices[cached_offset + phase * num_cached] = cached_idx;
            s_cached_logit_bits[cached_offset + phase * num_cached] = bits;
            atomicAdd(
                shared_hist + (phase ^ 1) * 256 + (bits >> (LShiftStart - (t + 1) * 8) & 0xff), 1);
          } else {
            int g_off = cached_offset - num_cached;
            if (g_off < overflow_stride) {
              get_cached_overflow(phase)[g_off] = {bits, cached_idx};
              atomicAdd(
                  shared_hist + (phase ^ 1) * 256 + (bits >> (LShiftStart - (t + 1) * 8) & 0xff),
                  1);
            }
          }
        } else {
          // t == NRemainingRounds
          // if top_k_remaining > 0, that means that in the final round, there's still
          // top_k_remaining items left after considering items bigger than the threshold_bin,
          // therefore the top_k_remaining items must be == to the threshold_bin, partitioned
          // amongst the cluster ranks
          if (top_k_remaining > 0 && !exceeded_k_remaining_cnt) {
            // here just pick an arbitrary index, since it should be bit equal as we be bin ==
            // threshold_bin in the last bin
            int k_remaining_cnt = atomicAdd(cluster.map_shared_rank(s_k_remaining_counter, 0), 1);
            if (k_remaining_cnt < top_k_remaining) {
              int off = atomicAdd(shared_final_idx_count, 1);
              s_topk_inds[off] = cached_idx;  // this should always be inbounds since the sum of
              // shared_final_idx_count amongst the cluster ranks +
              // top_k_remaining == TopK
            } else {
              exceeded_k_remaining_cnt = true;
            }
          }
        }
      }
    }
  }
  __syncthreads();
  // now shared_final_idx_count contains the local topK in each
  // block in a cluster, and
  // s_topk_inds[0:shared_final_idx_count] contains the local
  // slice.
  if (NClusters > 1) {
    int topk_start = 0;
    int topk_num = *shared_final_idx_count;
    // if (threadIdx.x == 0)
    //   printf("cluster id %d, topk_num %d, top_k_remaining %d\n", block_id, topk_num,
    //          top_k_remaining);

    cluster.sync();  // sync to get the shared_final_idx_count across CTAs in
                     // current cluster

    if (block_id > 0) {
      if (threadIdx.x == 0) {
        topk_start += atomicAdd(cluster.map_shared_rank(shared_final_idx_count, 0), topk_num);
        *shared_final_idx_count = topk_start;
      }
      __syncthreads();
      topk_start = *shared_final_idx_count;
    }

    cluster.sync();  // sync so CTAs don't exit when other CTAs depend on
                     // shared_final_idx_count
    return {min(TopK, topk_num), topk_start, s_topk_inds};

  } else {
    return {TopK, 0, s_topk_inds};
  }
}

template <typename T, typename IdxT, int NClusters, bool PDL_ENABLED>
__global__ __launch_bounds__(1024) void __cluster_dims__(NClusters, 1, 1)
    fast_topk_clusters_exact_kernel(
        const T* __restrict__ logits,  // [batchsize, max_num_pages * 64]
        IdxT* __restrict__ output_indices, T* __restrict__ output_values, int seq_len,
        int* __restrict__ pre_hist, int* __restrict__ cached_overflow, int overflow_stride,
        int logit_stride, int indices_stride, int num_cached, const int TopK) {
  int64_t cluster_id = blockIdx.x / NClusters;
  int64_t logit_offset = cluster_id * logit_stride;
  int64_t ind_offset = cluster_id * indices_stride;
  if (seq_len <= TopK) {
    for (int64_t i = threadIdx.x + (blockIdx.x % NClusters) * 1024; i < TopK;
         i += 1024 * NClusters) {
      if (i < seq_len) {
        output_indices[ind_offset + i] = static_cast<IdxT>(i);
        if (output_values != nullptr) {
          output_values[ind_offset + i] = logits[logit_offset + i];
        }
      } else {
        output_indices[ind_offset + i] = static_cast<IdxT>(-1);
      }
    }

  } else {
    // Each cluster gets its own overflow section of size overflow_stride * 4 *
    // NClusters int32 elements.
    int* block_overflow = cached_overflow + blockIdx.x * overflow_stride * 4;
    auto topk_res = fast_topk_cuda_v4<T, NClusters, PDL_ENABLED>(
        logits + logit_offset, (pre_hist == nullptr) ? nullptr : pre_hist + cluster_id * 256,
        (PackedCachedData*)block_overflow, overflow_stride, seq_len, num_cached, TopK);

    for (int64_t i = threadIdx.x; i < topk_res.local_topk_num; i += 1024) {
      int offs = i + topk_res.local_topk_start;
      if (offs < TopK) {
        auto ind = topk_res.local_topk_inds[i];
        output_indices[offs + ind_offset] = static_cast<IdxT>(ind);
        if (output_values != nullptr) {
          output_values[offs + ind_offset] = logits[logit_offset + ind];
        }
      }
    }
  }
}

template <typename T, int NClusters, bool PDL_ENABLED>
__global__ __launch_bounds__(1024) void __cluster_dims__(NClusters, 1, 1)
    fast_topk_clusters_exact_page_table_transform_kernel(
        const T* __restrict__ logits,       // [batchsize, logits_stride]
        int* __restrict__ output_indices,   // [batchsize, indices_stride]
        int* __restrict__ seq_lens,         // [batchsize]
        int* __restrict__ page_table,       // [batchsize, page_table_stride]
        int* __restrict__ pre_hist,         // optional[batchsize, 256]
        int* __restrict__ cached_overflow,  // [batchsize, 4 * NClusters * overflow_stride]
        int overflow_stride, int logit_stride, int indices_stride, int page_table_stride,
        int num_cached, const int TopK) {
  int64_t cluster_id = blockIdx.x / NClusters;
  int64_t logit_offset = cluster_id * logit_stride;
  int64_t ind_offset = cluster_id * indices_stride;
  int64_t page_table_offset = cluster_id * page_table_stride;
  int seq_len = seq_lens[cluster_id];
  if (seq_len <= TopK) {
    for (int64_t i = threadIdx.x + (blockIdx.x % NClusters) * 1024; i < TopK;
         i += 1024 * NClusters) {
      if (i < seq_len) {
        output_indices[ind_offset + i] = page_table[page_table_offset + i];

      } else {
        output_indices[ind_offset + i] = -1;
      }
    }

  } else {
    // Each cluster gets its own overflow section of size overflow_stride * 4 *
    // NClusters int32 elements.
    int* block_overflow = cached_overflow + blockIdx.x * overflow_stride * 4;
    auto topk_res = fast_topk_cuda_v4<T, NClusters, PDL_ENABLED>(
        logits + logit_offset, (pre_hist == nullptr) ? nullptr : pre_hist + cluster_id * 256,
        (PackedCachedData*)block_overflow, overflow_stride, seq_len, num_cached, TopK);

    for (int64_t i = threadIdx.x; i < topk_res.local_topk_num; i += 1024) {
      int offs = i + topk_res.local_topk_start;
      if (offs < TopK) {
        auto ind = topk_res.local_topk_inds[i];
        output_indices[offs + ind_offset] = page_table[page_table_offset + ind];
      }
    }
  }
}

template <typename T, int NClusters, bool PDL_ENABLED>
__global__ __launch_bounds__(1024) void __cluster_dims__(NClusters, 1, 1)
    fast_topk_clusters_exact_ragged_transform_kernel(
        const T* __restrict__ logits,       // [batchsize, logits_stride]
        int* __restrict__ output_indices,   // [batchsize, indices_stride]
        int* __restrict__ seq_lens,         // [batchsize]
        int* __restrict__ offsets,          // [batchsize]
        int* __restrict__ pre_hist,         // optional[batchsize, 256]
        int* __restrict__ cached_overflow,  // [batchsize, 4 * NClusters * overflow_stride]
        int overflow_stride, int logit_stride, int indices_stride, int num_cached, const int TopK) {
  int cluster_id = blockIdx.x / NClusters;
  int64_t logit_offset = cluster_id * logit_stride;
  int64_t ind_offset = cluster_id * indices_stride;
  int64_t seq_len = seq_lens[cluster_id];
  int64_t ragged_offset = offsets[cluster_id];
  if (seq_len <= TopK) {
    for (int64_t i = threadIdx.x + (blockIdx.x % NClusters) * 1024; i < TopK;
         i += 1024 * NClusters) {
      if (i < seq_len) {
        output_indices[ind_offset + i] = i + ragged_offset;

      } else {
        output_indices[ind_offset + i] = -1;
      }
    }

  } else {
    // Each cluster gets its own overflow section of size overflow_stride * 4 *
    // NClusters int32 elements.
    int* block_overflow = cached_overflow + blockIdx.x * overflow_stride * 4;
    auto topk_res = fast_topk_cuda_v4<T, NClusters, PDL_ENABLED>(
        logits + logit_offset, (pre_hist == nullptr) ? nullptr : pre_hist + cluster_id * 256,
        (PackedCachedData*)block_overflow, overflow_stride, seq_len, num_cached, TopK);

    for (int64_t i = threadIdx.x; i < topk_res.local_topk_num; i += 1024) {
      int offs = i + topk_res.local_topk_start;
      if (offs < TopK) {
        auto ind = topk_res.local_topk_inds[i];
        output_indices[offs + ind_offset] = ind + ragged_offset;
      }
    }
  }
}

#endif  // !defined(__CUDA_ARCH__) || __CUDA_ARCH__ >= 900

constexpr int MAX_SMEM_CARVEOUT = 227 * 1024;

#define DISPATCH_TOPK_EXACT(kernel_name, dtype, CLUSTERS, PDL) \
  if (num_clusters == CLUSTERS && pdl_enabled == PDL) {        \
    kernel = (void*)&kernel_name<dtype, CLUSTERS, PDL>;        \
  }

// Variant for kernels with an additional IdxT template parameter (index output dtype).
#define DISPATCH_TOPK_EXACT_IDX(kernel_name, dtype, idx_type, CLUSTERS, PDL) \
  if (num_clusters == CLUSTERS && pdl_enabled == PDL) {                      \
    kernel = (void*)&kernel_name<dtype, idx_type, CLUSTERS, PDL>;            \
  }

inline void launch_topk_cluster_kernel(void* kernel, void** args, int grid_dim, int smem_bytes,
                                       int num_clusters, bool pdl_enabled, cudaStream_t stream) {
  cudaFuncSetAttribute(kernel, cudaFuncAttributeMaxDynamicSharedMemorySize, MAX_SMEM_CARVEOUT);
  cudaFuncSetAttribute(kernel, cudaFuncAttributePreferredSharedMemoryCarveout, 100);

  cudaLaunchConfig_t config;
  config.numAttrs = 0;
  cudaLaunchAttribute attribute[2];
  attribute[config.numAttrs].id = cudaLaunchAttributeClusterDimension;
  attribute[config.numAttrs].val.clusterDim = {(unsigned)num_clusters, 1, 1};
  config.numAttrs += 1;
  if (pdl_enabled) {
    attribute[config.numAttrs].id = cudaLaunchAttributeProgrammaticStreamSerialization;
    attribute[config.numAttrs].val.programmaticStreamSerializationAllowed = 1;
    config.numAttrs += 1;
  }
  config.blockDim = 1024;
  config.dynamicSmemBytes = smem_bytes;
  config.gridDim = grid_dim;
  config.stream = stream;
  config.attrs = attribute;
  cudaLaunchKernelExC(&config, kernel, args);
}

int get_shared_mem_bytes(int TopK, int num_cached) {
  return (num_cached * 2 * sizeof(float) + num_cached * 2 * sizeof(int) + TopK * sizeof(int) +
          5 * sizeof(int) + 3 * 256 * sizeof(int) + 8 * sizeof(int));
}

template <typename T, typename IdxT = int>
void launch_fast_topk_clusters_exact(const T* logits, IdxT* indices, T* output_values, int seq_len,
                                     int* pre_hist, int* cached_overflow, int overflow_stride,
                                     int batch_size, int logit_stride, int indices_stride,
                                     int num_cached, int num_clusters, bool pdl_enabled, int TopK,
                                     cudaStream_t stream) {
#if !defined(__CUDA_ARCH__) || __CUDA_ARCH__ >= 900
  int extern_shared_mem = get_shared_mem_bytes(TopK, num_cached);

  void* args[11] = {
      &logits,          &indices,      &output_values,  &seq_len,    &pre_hist, &cached_overflow,
      &overflow_stride, &logit_stride, &indices_stride, &num_cached, &TopK};

  void* kernel = nullptr;
  DISPATCH_TOPK_EXACT_IDX(fast_topk_clusters_exact_kernel, T, IdxT, 1, true);
  DISPATCH_TOPK_EXACT_IDX(fast_topk_clusters_exact_kernel, T, IdxT, 2, true);
  DISPATCH_TOPK_EXACT_IDX(fast_topk_clusters_exact_kernel, T, IdxT, 4, true);
  DISPATCH_TOPK_EXACT_IDX(fast_topk_clusters_exact_kernel, T, IdxT, 8, true);
  DISPATCH_TOPK_EXACT_IDX(fast_topk_clusters_exact_kernel, T, IdxT, 1, false);
  DISPATCH_TOPK_EXACT_IDX(fast_topk_clusters_exact_kernel, T, IdxT, 2, false);
  DISPATCH_TOPK_EXACT_IDX(fast_topk_clusters_exact_kernel, T, IdxT, 4, false);
  DISPATCH_TOPK_EXACT_IDX(fast_topk_clusters_exact_kernel, T, IdxT, 8, false);

  if (kernel == nullptr) {
    num_clusters = 1;
    pdl_enabled = false;
    DISPATCH_TOPK_EXACT_IDX(fast_topk_clusters_exact_kernel, T, IdxT, 1, false);
  }

  // cudaLaunchKernelExC requires an explicit cluster dimension attribute even for kernels
  // annotated with __cluster_dims__; without it the launch fails with invalid argument.
  launch_topk_cluster_kernel(kernel, args, batch_size * num_clusters, extern_shared_mem,
                             num_clusters, pdl_enabled, stream);
#else
  TVM_FFI_ICHECK(false) << "fast_topk_clusters_exact requires SM 9.0 (Hopper) or newer";
#endif
}

template <typename T>
void launch_fast_topk_clusters_exact_page_table_transform(
    const T* logits, int* indices, int* seq_lens, int* page_table, int* pre_hist,
    int* cached_overflow, int overflow_stride, int batch_size, int logit_stride, int indices_stride,
    int page_table_stride, int num_cached, int num_clusters, bool pdl_enabled, int TopK,
    cudaStream_t stream) {
#if !defined(__CUDA_ARCH__) || __CUDA_ARCH__ >= 900
  int extern_shared_mem = get_shared_mem_bytes(TopK, num_cached);

  void* args[12] = {&logits,         &indices,           &seq_lens,        &page_table,
                    &pre_hist,       &cached_overflow,   &overflow_stride, &logit_stride,
                    &indices_stride, &page_table_stride, &num_cached,      &TopK};

  void* kernel = nullptr;
  DISPATCH_TOPK_EXACT(fast_topk_clusters_exact_page_table_transform_kernel, T, 1, true);
  DISPATCH_TOPK_EXACT(fast_topk_clusters_exact_page_table_transform_kernel, T, 2, true);
  DISPATCH_TOPK_EXACT(fast_topk_clusters_exact_page_table_transform_kernel, T, 4, true);
  DISPATCH_TOPK_EXACT(fast_topk_clusters_exact_page_table_transform_kernel, T, 8, true);
  DISPATCH_TOPK_EXACT(fast_topk_clusters_exact_page_table_transform_kernel, T, 1, false);
  DISPATCH_TOPK_EXACT(fast_topk_clusters_exact_page_table_transform_kernel, T, 2, false);
  DISPATCH_TOPK_EXACT(fast_topk_clusters_exact_page_table_transform_kernel, T, 4, false);
  DISPATCH_TOPK_EXACT(fast_topk_clusters_exact_page_table_transform_kernel, T, 8, false);

  if (kernel == nullptr) {
    num_clusters = 1;
    pdl_enabled = false;
    DISPATCH_TOPK_EXACT(fast_topk_clusters_exact_page_table_transform_kernel, T, 1, false);
  }

  launch_topk_cluster_kernel(kernel, args, batch_size * num_clusters, extern_shared_mem,
                             num_clusters, pdl_enabled, stream);
#else
  TVM_FFI_ICHECK(false)
      << "fast_topk_clusters_exact_page_table_transform requires SM 9.0 (Hopper) or newer";
#endif
}

template <typename T>
void launch_fast_topk_clusters_exact_ragged_transform(
    const T* logits, int* indices, int* seq_lens, int* offsets, int* pre_hist, int* cached_overflow,
    int overflow_stride, int batch_size, int logit_stride, int indices_stride, int num_cached,
    int num_clusters, bool pdl_enabled, int TopK, cudaStream_t stream) {
#if !defined(__CUDA_ARCH__) || __CUDA_ARCH__ >= 900
  int extern_shared_mem = get_shared_mem_bytes(TopK, num_cached);

  void* args[11] = {
      &logits,          &indices,      &seq_lens,       &offsets,    &pre_hist, &cached_overflow,
      &overflow_stride, &logit_stride, &indices_stride, &num_cached, &TopK};

  void* kernel = nullptr;
  DISPATCH_TOPK_EXACT(fast_topk_clusters_exact_ragged_transform_kernel, T, 1, true);
  DISPATCH_TOPK_EXACT(fast_topk_clusters_exact_ragged_transform_kernel, T, 2, true);
  DISPATCH_TOPK_EXACT(fast_topk_clusters_exact_ragged_transform_kernel, T, 4, true);
  DISPATCH_TOPK_EXACT(fast_topk_clusters_exact_ragged_transform_kernel, T, 8, true);
  DISPATCH_TOPK_EXACT(fast_topk_clusters_exact_ragged_transform_kernel, T, 1, false);
  DISPATCH_TOPK_EXACT(fast_topk_clusters_exact_ragged_transform_kernel, T, 2, false);
  DISPATCH_TOPK_EXACT(fast_topk_clusters_exact_ragged_transform_kernel, T, 4, false);
  DISPATCH_TOPK_EXACT(fast_topk_clusters_exact_ragged_transform_kernel, T, 8, false);

  if (kernel == nullptr) {
    num_clusters = 1;
    pdl_enabled = false;
    DISPATCH_TOPK_EXACT(fast_topk_clusters_exact_ragged_transform_kernel, T, 1, false);
  }

  launch_topk_cluster_kernel(kernel, args, batch_size * num_clusters, extern_shared_mem,
                             num_clusters, pdl_enabled, stream);
#else
  TVM_FFI_ICHECK(false)
      << "fast_topk_clusters_exact_ragged_transform requires SM 9.0 (Hopper) or newer";
#endif
}

}  // namespace sampling
}  // namespace flashinfer

#endif  // FLASHINFER_TOPK_CLUSTERS_CUH_
