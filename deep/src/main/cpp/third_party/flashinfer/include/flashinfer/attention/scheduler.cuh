/*
 * Copyright (c) 2023 by FlashInfer team.
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
#ifndef FLASHINFER_ATTENTION_SCHEDULER_CUH_
#define FLASHINFER_ATTENTION_SCHEDULER_CUH_

#include <cuda_runtime_api.h>
#include <driver_types.h>

#include <algorithm>
#include <cstddef>
#include <cstdint>
#include <sstream>
#include <vector>

#include "../allocator.h"
#include "../exception.h"
#include "../pos_enc.cuh"
#include "../utils.cuh"
#include "heap.h"

namespace flashinfer {

template <PosEncodingMode POS_ENCODING_MODE, uint32_t num_stages_smem, uint32_t tile_size_per_bdx,
          uint32_t vec_size, uint32_t bdx, uint32_t bdy, uint32_t bdz, typename AttentionVariant,
          typename Params>
__global__ void BatchDecodeWithPagedKVCacheKernel(const __grid_constant__ Params params);

template <uint32_t num_stages_smem, uint32_t vec_size_ckv, uint32_t vec_size_kpe, uint32_t bdx,
          uint32_t bdy, uint32_t bdz, uint32_t tile_size_qo_heads, typename AttentionVariant,
          typename Params>
__global__ void BatchDecodeWithPagedKVCacheKernelMLA(Params params);

template <uint32_t HEAD_DIM_CKV, uint32_t HEAD_DIM_KPE, uint32_t QO_TILE_LEN, typename DTypeKV>
std::tuple<uint32_t, uint32_t, uint32_t> LaunchSpecForDecodeKernelMlaCuteSM80(
    const uint32_t num_qo_heads);

template <uint32_t HEAD_DIM_CKV, uint32_t HEAD_DIM_KPE, uint32_t QO_TILE_LEN, typename Params>
__global__ void BatchDecodeWithPagedKVCacheKernelMlaCuteSM80(Params params);

template <typename DType>
inline void CopyToPageLockedBuffer(void* page_locked_int_buffer, int64_t offset,
                                   const std::vector<DType>& vec) {
  DType* ptr = GetPtrFromBaseOffset<DType>(page_locked_int_buffer, offset);
  std::copy(vec.begin(), vec.end(), ptr);
}

/*!
 * \brief Compute the maximum number of pages per batch and the new batch size
 *   after we partition Paged KV-Cache into multiple chunks on KV sequence length
 *   dimension.
 * \tparam IdType A template type indicates the index data type
 * \param max_grid_size The maximum grid size of the kernel
 * \param gdy gridDim.y
 * \param num_pages The number of pages per request in the batch
 * \param max_num_pages_per_batch_lb The pre-set lower bound of maximum number of
 *   pages per batch, default to 1
 * \return (max_num_pages_per_batch, new_batch_size) The number of pages per batch and
 *   the new batch size after the partition.
 */
template <typename IdType>
inline auto PartitionPagedKVCacheBinarySearchMinNumPagePerBatch(
    const uint32_t max_grid_size, const uint32_t gdy, const std::vector<IdType>& num_pages,
    const uint32_t min_num_pages_per_batch = 1) {
  uint32_t low = min_num_pages_per_batch, high = 0;
  for (const IdType& elem : num_pages) {
    high = max(high, elem);
  }
  uint32_t new_batch_size;
  while (low < high) {
    uint32_t mid = (low + high) / 2;
    new_batch_size = 0;
    for (const IdType& elem : num_pages) {
      new_batch_size += ceil_div(elem, mid);
    }
    if (new_batch_size * gdy > max_grid_size) {
      low = mid + 1;
    } else {
      high = mid;
    }
  }
  new_batch_size = 0;
  for (const IdType& elem : num_pages) {
    new_batch_size += ceil_div(std::max(elem, 1), low);
  }
  return std::make_tuple(low, new_batch_size);
}

inline auto PrefillBinarySearchKVChunkSize(const bool enable_cuda_graph,
                                           const uint32_t max_batch_size_if_split,
                                           const std::vector<int64_t>& packed_qo_len_arr,
                                           const std::vector<int64_t>& kv_len_arr,
                                           const uint32_t qo_chunk_size,
                                           const uint32_t min_kv_chunk_size = 1) {
  const int64_t batch_size = packed_qo_len_arr.size();
  int64_t max_kv_len = 1;
  for (const int64_t& kv_len : kv_len_arr) {
    max_kv_len = std::max(max_kv_len, kv_len);
  }

  int64_t low = min_kv_chunk_size;
  int64_t high = max_kv_len;
  constexpr int64_t min_kv_len = 1;
  while (low < high) {
    const int64_t mid = (low + high) / 2;
    int64_t new_batch_size = 0;
    for (uint32_t i = 0; i < batch_size; ++i) {
      new_batch_size += ceil_div(packed_qo_len_arr[i], qo_chunk_size) *
                        ceil_div(std::max(kv_len_arr[i], min_kv_len), mid);
    }
    if (new_batch_size > max_batch_size_if_split) {
      low = mid + 1;
    } else {
      high = mid;
    }
  }
  return std::make_tuple(enable_cuda_graph || low < max_kv_len, low);
}

/*!
 * \brief Estimate the temporary buffer size and the maximum grid size for the
 *   partition-kv BatchDecodeWithPagedKVCache kernel
 * \tparam DTypeKV A template type indicates the key-value data type
 * \tparam DTypeO A template type indicates the output data type
 * \tparam IdType A template type indicates the index data type
 * \param split_kv Whether to split the KV cache into multiple chunks
 * \param max_grid_size The maximum grid size that can be used in a partiton-kv kernel
 * \param max_num_pages_per_batch The maximum number of pages per batch
 * \param new_batch_size The new batch size after the partition
 * \param paged_kv The paged kv cache data structure
 * \param num_qo_heads A integer indicates the number of heads of query and output
 * \param pos_encoding_mode The positional encoding mode
 * \param stream The cuda stream to launch the kernel
 * \return status Indicates whether CUDA calls are successful
 */
template <uint32_t GROUP_SIZE, uint32_t HEAD_DIM, PosEncodingMode POS_ENCODING_MODE,
          typename AttentionVariant, typename Params>
inline cudaError_t BatchDecodeWithPagedKVCacheWorkEstimationDispatched(
    bool& split_kv, uint32_t& max_grid_size, uint32_t& max_num_pages_per_batch,
    uint32_t& new_batch_size, uint32_t& gdy, uint32_t batch_size,
    typename Params::IdType* kv_indptr_h, const uint32_t num_qo_heads, const uint32_t page_size,
    bool enable_cuda_graph, cudaStream_t stream) {
  using DTypeKV = typename Params::DTypeKV;
  using IdType = typename Params::IdType;
  constexpr uint32_t vec_size = std::max(16UL / sizeof(DTypeKV), HEAD_DIM / 32UL);
  auto compute_capacity = GetCudaComputeCapability();
  DISPATCH_COMPUTE_CAP_DECODE_NUM_STAGES_SMEM(compute_capacity, NUM_STAGES_SMEM, {
    constexpr uint32_t bdx = HEAD_DIM / vec_size;
    static_assert(bdx <= 32);
    constexpr uint32_t bdy = GROUP_SIZE;
    constexpr uint32_t num_threads = std::max(128U, bdx * bdy);
    constexpr uint32_t bdz = num_threads / (bdx * bdy);
    constexpr uint32_t tile_size_per_bdx = GROUP_SIZE == 1 ? (sizeof(DTypeKV) == 1 ? 2U : 4U) : 1U;
    const uint32_t num_kv_heads = num_qo_heads / GROUP_SIZE;
    gdy = num_kv_heads;
    const uint32_t smem_size =
        2 * NUM_STAGES_SMEM * tile_size_per_bdx * bdy * bdz * HEAD_DIM * sizeof(DTypeKV) +
        std::max(tile_size_per_bdx * num_threads * sizeof(DTypeKV*), 2 * bdy * bdz * sizeof(float));

    auto kernel =
        BatchDecodeWithPagedKVCacheKernel<POS_ENCODING_MODE, NUM_STAGES_SMEM, tile_size_per_bdx,
                                          vec_size, bdx, bdy, bdz, AttentionVariant, Params>;
    int num_blocks_per_sm = 0;
    int num_sm = 0;
    int dev_id = 0;
    FLASHINFER_CUDA_CALL(cudaGetDevice(&dev_id));
    FLASHINFER_CUDA_CALL(cudaDeviceGetAttribute(&num_sm, cudaDevAttrMultiProcessorCount, dev_id));
    FLASHINFER_CUDA_CALL(cudaOccupancyMaxActiveBlocksPerMultiprocessor(&num_blocks_per_sm, kernel,
                                                                       num_threads, smem_size));
    max_grid_size = num_blocks_per_sm * num_sm;
    if (batch_size * gdy >= max_grid_size) {
      split_kv = false;
      max_num_pages_per_batch = 1;
      for (uint32_t batch_idx = 0; batch_idx < batch_size; ++batch_idx) {
        max_num_pages_per_batch = std::max<uint32_t>(
            max_num_pages_per_batch, kv_indptr_h[batch_idx + 1] - kv_indptr_h[batch_idx]);
      }
      new_batch_size = batch_size;
    } else {
      // compute max_num_pages_per_batch and new_batch_size
      std::vector<IdType> num_pages(batch_size);
      for (uint32_t batch_idx = 0; batch_idx < batch_size; ++batch_idx) {
        num_pages[batch_idx] = kv_indptr_h[batch_idx + 1] - kv_indptr_h[batch_idx];
      }
      std::tie(max_num_pages_per_batch, new_batch_size) =
          PartitionPagedKVCacheBinarySearchMinNumPagePerBatch(max_grid_size, gdy, num_pages,
                                                              std::max(128 / page_size, 1U));
      if (new_batch_size == batch_size && !enable_cuda_graph) {
        // do not use partition-kv kernel for short sequence, when not using CUDAGraph
        split_kv = false;
      } else {
        // when using CUDAGraph, we always use partition-kv kernel
        split_kv = true;
      }
    }
    return cudaSuccess;
  })
}

template <uint32_t HEAD_DIM_CKV, uint32_t HEAD_DIM_KPE, typename AttentionVariant, typename Params>
inline cudaError_t BatchDecodeWithPagedKVCacheWorkEstimationDispatchedMLA(
    bool& split_kv, uint32_t& max_grid_size, uint32_t& max_num_pages_per_batch,
    uint32_t& new_batch_size, uint32_t& gdy, uint32_t batch_size,
    typename Params::IdType* kv_indptr_h, const uint32_t num_qo_heads, const uint32_t page_size,
    bool enable_cuda_graph, cudaStream_t stream) {
  using DTypeKV = typename Params::DTypeKV;
  using IdType = typename Params::IdType;

  auto compute_capacity = GetCudaComputeCapability();
  DISPATCH_COMPUTE_CAP_DECODE_NUM_STAGES_SMEM(compute_capacity, NUM_STAGES_SMEM, {
    constexpr uint32_t vec_size_ckv = std::max(16UL / sizeof(DTypeKV), HEAD_DIM_CKV / 32UL);
    constexpr uint32_t bdx = HEAD_DIM_CKV / vec_size_ckv;
    constexpr uint32_t vec_size_kpe = HEAD_DIM_KPE / bdx;

    constexpr uint32_t bdy = 8;
    constexpr uint32_t tile_size_qo_heads = 2;
    constexpr uint32_t qo_heads_per_block = bdy * tile_size_qo_heads;
    constexpr uint32_t num_threads = std::max(128U, bdx * bdy);
    constexpr uint32_t bdz = num_threads / (bdx * bdy);
    gdy = ceil_div(num_qo_heads, qo_heads_per_block);

    const uint32_t smem_size =
        NUM_STAGES_SMEM * bdy * bdz * (HEAD_DIM_CKV + HEAD_DIM_KPE) * sizeof(DTypeKV) +
        std::max(num_threads * sizeof(size_t) * 2, 2 * bdy * bdz * sizeof(float));

    auto kernel =
        BatchDecodeWithPagedKVCacheKernelMLA<NUM_STAGES_SMEM, vec_size_ckv, vec_size_kpe, bdx, bdy,
                                             bdz, tile_size_qo_heads, AttentionVariant, Params>;
    int num_blocks_per_sm = 0;
    int num_sm = 0;
    int dev_id = 0;
    FLASHINFER_CUDA_CALL(cudaGetDevice(&dev_id));
    FLASHINFER_CUDA_CALL(cudaDeviceGetAttribute(&num_sm, cudaDevAttrMultiProcessorCount, dev_id));
    FLASHINFER_CUDA_CALL(cudaOccupancyMaxActiveBlocksPerMultiprocessor(&num_blocks_per_sm, kernel,
                                                                       num_threads, smem_size));
    max_grid_size = num_blocks_per_sm * num_sm;
    if (batch_size * gdy >= max_grid_size) {
      split_kv = false;
      max_num_pages_per_batch = 1;
      for (uint32_t batch_idx = 0; batch_idx < batch_size; ++batch_idx) {
        max_num_pages_per_batch = std::max<uint32_t>(
            max_num_pages_per_batch, kv_indptr_h[batch_idx + 1] - kv_indptr_h[batch_idx]);
      }
      new_batch_size = batch_size;
    } else {
      // compute max_num_pages_per_batch and new_batch_size
      std::vector<IdType> num_pages(batch_size);
      for (uint32_t batch_idx = 0; batch_idx < batch_size; ++batch_idx) {
        num_pages[batch_idx] = kv_indptr_h[batch_idx + 1] - kv_indptr_h[batch_idx];
      }
      std::tie(max_num_pages_per_batch, new_batch_size) =
          PartitionPagedKVCacheBinarySearchMinNumPagePerBatch(max_grid_size, gdy, num_pages,
                                                              std::max(128 / page_size, 1U));
      if (new_batch_size == batch_size && !enable_cuda_graph) {
        // do not use partition-kv kernel for short sequence, when not using CUDAGraph
        split_kv = false;
      } else {
        // when using CUDAGraph, we always use partition-kv kernel
        split_kv = true;
      }
    }

    return cudaSuccess;
  });
}

template <uint32_t HEAD_DIM_CKV, uint32_t HEAD_DIM_KPE, uint32_t QO_TILE_LEN,
          typename AttentionVariant, typename Params>
inline cudaError_t BatchDecodeWithPagedKVCacheWorkEstimationDispatchedMlaCuteSM80(
    bool& split_kv, uint32_t& max_grid_size, uint32_t& max_num_pages_per_batch,
    uint32_t& new_batch_size, uint32_t& gdy_, uint32_t batch_size,
    typename Params::IdType* kv_indptr_h, const uint32_t num_qo_heads, const uint32_t page_size,
    bool enable_cuda_graph, cudaStream_t stream) {
  using DTypeKV = typename Params::DTypeKV;
  using IdType = typename Params::IdType;

  auto [smem_size, gdy, k_warps] =
      LaunchSpecForDecodeKernelMlaCuteSM80<HEAD_DIM_CKV, HEAD_DIM_KPE, QO_TILE_LEN, DTypeKV>(
          num_qo_heads);
  gdy_ = gdy;
  const uint32_t num_threads = k_warps * 32;
  auto kernel =
      BatchDecodeWithPagedKVCacheKernelMlaCuteSM80<HEAD_DIM_CKV, HEAD_DIM_KPE, QO_TILE_LEN, Params>;
  int num_blocks_per_sm;
  int num_sm = 0;
  int dev_id = 0;
  FLASHINFER_CUDA_CALL(cudaGetDevice(&dev_id));
  FLASHINFER_CUDA_CALL(cudaDeviceGetAttribute(&num_sm, cudaDevAttrMultiProcessorCount, dev_id));

  // FLASHINFER_CUDA_CALL(cudaOccupancyMaxActiveBlocksPerMultiprocessor(&num_blocks_per_sm, kernel,
  //                                   num_threads, smem_size));
  // fixme: num_blocks_per_sm is 0 derived from cudaOccupancyMaxActiveBlocksPerMultiprocessor at
  // times, and we fill smem with q-heads as many as possible, so num_blocks_per_sm should be 1
  num_blocks_per_sm = 1;

  max_grid_size = num_blocks_per_sm * num_sm;
  if (batch_size * gdy >= max_grid_size) {
    split_kv = false;
    max_num_pages_per_batch = 1;
    for (uint32_t batch_idx = 0; batch_idx < batch_size; ++batch_idx) {
      max_num_pages_per_batch = std::max<uint32_t>(
          max_num_pages_per_batch, kv_indptr_h[batch_idx + 1] - kv_indptr_h[batch_idx]);
    }
    new_batch_size = batch_size;
  } else {
    // compute max_num_pages_per_batch and new_batch_size
    std::vector<IdType> num_pages(batch_size);
    for (uint32_t batch_idx = 0; batch_idx < batch_size; ++batch_idx) {
      num_pages[batch_idx] = kv_indptr_h[batch_idx + 1] - kv_indptr_h[batch_idx];
    }
    std::tie(max_num_pages_per_batch, new_batch_size) =
        PartitionPagedKVCacheBinarySearchMinNumPagePerBatch(max_grid_size, gdy, num_pages,
                                                            std::max(128 / page_size, 1U));
    if (new_batch_size == batch_size && !enable_cuda_graph) {
      // do not use partition-kv kernel for short sequence, when not using CUDAGraph
      split_kv = false;
    } else {
      // when using CUDAGraph, we always use partition-kv kernel
      split_kv = true;
    }
  }

  return cudaSuccess;
}

/*!
 * \brief Partition Paged KV-Cache into multiple chunks on KV sequence length
 * \tparam IdType A template type indicates the index data type
 * \param old_batch_size The batch size of the old Paged KV-Cache
 * \param old_page_indptr_h The host-side page indptr of the old Paged KV-Cache
 * \param max_num_pages_per_batch The maximum number of pages per batch
 * \param new_paged_kv_d The device-side new Paged KV-Cache
 * \param stream The cuda stream to launch the kernel
 * \return status Indicates whether CUDA calls are successful
 */
template <typename IdType>
inline auto DecodeSplitKVIndptr(IdType* indptr_h, uint32_t batch_size, uint32_t kv_chunk_size) {
  std::vector<IdType> request_indices, kv_tile_indices, o_indptr;
  o_indptr.push_back(0);

  for (uint32_t batch_idx = 0; batch_idx < batch_size; batch_idx++) {
    uint32_t num_chunks_kv = ceil_div(
        std::max<uint32_t>(indptr_h[batch_idx + 1] - indptr_h[batch_idx], 1U), kv_chunk_size);
    for (uint32_t kv_tile_idx = 0; kv_tile_idx < num_chunks_kv; ++kv_tile_idx) {
      request_indices.push_back(batch_idx);
      kv_tile_indices.push_back(kv_tile_idx);
    }
    o_indptr.push_back(o_indptr.back() + num_chunks_kv);
  }

  return std::make_tuple(request_indices, kv_tile_indices, o_indptr);
}

struct DecodePlanInfo {
  int64_t padded_batch_size;
  int64_t v_offset;
  int64_t s_offset;
  int64_t request_indices_offset;
  int64_t kv_tile_indices_offset;
  int64_t o_indptr_offset;
  int64_t block_valid_mask_offset;
  int64_t kv_chunk_size_ptr_offset;
  bool enable_cuda_graph;
  bool split_kv;

  DecodePlanInfo()
      : padded_batch_size(0),
        v_offset(0),
        s_offset(0),
        request_indices_offset(0),
        kv_tile_indices_offset(0),
        o_indptr_offset(0),
        block_valid_mask_offset(0),
        kv_chunk_size_ptr_offset(0),
        enable_cuda_graph(false),
        split_kv(false) {}

  // convert DecodePlanInfo to std::vector<int64_t>
  std::vector<int64_t> ToVector() const {
    return {padded_batch_size,
            v_offset,
            s_offset,
            request_indices_offset,
            kv_tile_indices_offset,
            o_indptr_offset,
            block_valid_mask_offset,
            kv_chunk_size_ptr_offset,
            enable_cuda_graph,
            split_kv};
  }

  // From std::vector<int64_t> to DecodePlanInfo
  void FromVector(const std::vector<int64_t>& vec) {
    if (vec.size() != 10) {
      std::ostringstream err_msg;
      err_msg << "DecodePlanInfo::FromVector: vec.size() should be 10, but got " << vec.size();
      FLASHINFER_ERROR(err_msg.str());
    }
    padded_batch_size = vec[0];
    v_offset = vec[1];
    s_offset = vec[2];
    request_indices_offset = vec[3];
    kv_tile_indices_offset = vec[4];
    o_indptr_offset = vec[5];
    block_valid_mask_offset = vec[6];
    kv_chunk_size_ptr_offset = vec[7];
    enable_cuda_graph = vec[8];
    split_kv = vec[9];
  }
};

template <bool MATERIALIZE, uint32_t HEAD_DIM, PosEncodingMode POS_ENCODING_MODE,
          typename AttentionVariant, typename Params, typename WorkEstimationFunc>
inline cudaError_t DecodePlanImpl(size_t& float_workspace_size_out, size_t& int_workspace_size_out,
                                  void* float_buffer, size_t float_workspace_size_in_bytes,
                                  void* int_buffer, void* page_locked_int_buffer,
                                  size_t int_workspace_size_in_bytes, DecodePlanInfo& plan_info,
                                  typename Params::IdType* indptr_h, uint32_t batch_size,
                                  uint32_t num_qo_heads, uint32_t page_size, bool enable_cuda_graph,
                                  cudaStream_t stream, WorkEstimationFunc work_estimation_func) {
  using IdType = typename Params::IdType;
  bool split_kv;
  uint32_t max_grid_size, kv_chunk_size_in_pages, new_batch_size, gdy;

  FLASHINFER_CUDA_CALL(work_estimation_func(split_kv, max_grid_size, kv_chunk_size_in_pages,
                                            new_batch_size, gdy, batch_size, indptr_h, num_qo_heads,
                                            page_size, enable_cuda_graph, stream));
  size_t padded_batch_size;
  plan_info.enable_cuda_graph = enable_cuda_graph;
  plan_info.split_kv = split_kv;
  padded_batch_size =
      (enable_cuda_graph) ? (split_kv ? max_grid_size / gdy : batch_size) : new_batch_size;
  plan_info.padded_batch_size = padded_batch_size;
  auto [request_indices_vec, kv_tile_indices_vec, o_indptr_vec] =
      DecodeSplitKVIndptr(indptr_h, batch_size, kv_chunk_size_in_pages);

  AlignedAllocator int_allocator =
      MATERIALIZE ? AlignedAllocator(int_buffer, int_workspace_size_in_bytes) : AlignedAllocator();
  plan_info.request_indices_offset = int_allocator.aligned_alloc_offset(
      padded_batch_size * sizeof(IdType), 16, "batch_decode_request_indices");
  plan_info.kv_tile_indices_offset = int_allocator.aligned_alloc_offset(
      padded_batch_size * sizeof(IdType), 16, "batch_decode_kv_tile_indices");
  plan_info.o_indptr_offset = int_allocator.aligned_alloc_offset(
      (padded_batch_size + 1) * sizeof(IdType), 16, "batch_decode_o_indptr");
  plan_info.kv_chunk_size_ptr_offset =
      int_allocator.aligned_alloc_offset(sizeof(IdType), 1, "batch_decode_kv_chunk_size_ptr");
  if constexpr (MATERIALIZE) {
    IdType* request_indices_h =
        GetPtrFromBaseOffset<IdType>(page_locked_int_buffer, plan_info.request_indices_offset);
    IdType* kv_tile_indices_h =
        GetPtrFromBaseOffset<IdType>(page_locked_int_buffer, plan_info.kv_tile_indices_offset);
    IdType* o_indptr_h =
        GetPtrFromBaseOffset<IdType>(page_locked_int_buffer, plan_info.o_indptr_offset);
    IdType* kv_chunk_size_ptr_h =
        GetPtrFromBaseOffset<IdType>(page_locked_int_buffer, plan_info.kv_chunk_size_ptr_offset);
    std::copy(request_indices_vec.begin(), request_indices_vec.end(), request_indices_h);
    std::copy(kv_tile_indices_vec.begin(), kv_tile_indices_vec.end(), kv_tile_indices_h);
    std::copy(o_indptr_vec.begin(), o_indptr_vec.end(), o_indptr_h);
    kv_chunk_size_ptr_h[0] = kv_chunk_size_in_pages * page_size;
  } else {
    (void)kv_chunk_size_in_pages;
    (void)new_batch_size;
    (void)request_indices_vec;
    (void)kv_tile_indices_vec;
    (void)o_indptr_vec;
  }

  AlignedAllocator float_allocator;
  if (split_kv) {
    if constexpr (MATERIALIZE) {
      float_allocator = AlignedAllocator(float_buffer, float_workspace_size_in_bytes);
    }
    plan_info.v_offset = float_allocator.aligned_alloc_offset(
        num_qo_heads * padded_batch_size * HEAD_DIM * sizeof(float), 16, "batch_decode_tmp_v");
    plan_info.s_offset = float_allocator.aligned_alloc_offset(
        num_qo_heads * padded_batch_size * sizeof(float), 16, "batch_decode_tmp_s");

    plan_info.block_valid_mask_offset = int_allocator.aligned_alloc_offset(
        padded_batch_size * sizeof(bool), 16, "batch_decode_block_valid_mask");
    if constexpr (MATERIALIZE) {
      bool* block_valid_mask_h =
          GetPtrFromBaseOffset<bool>(page_locked_int_buffer, plan_info.block_valid_mask_offset);
      for (uint32_t i = 0; i < padded_batch_size; ++i) {
        block_valid_mask_h[i] = i < new_batch_size;
      }
    }
  }

  float_workspace_size_out = float_allocator.num_allocated_bytes();
  int_workspace_size_out = int_allocator.num_allocated_bytes();
  if constexpr (MATERIALIZE) {
    FLASHINFER_CUDA_CALL(cudaMemcpyAsync(int_buffer, page_locked_int_buffer, int_workspace_size_out,
                                         cudaMemcpyHostToDevice, stream));
  }
  return cudaSuccess;
}

template <uint32_t HEAD_DIM, PosEncodingMode POS_ENCODING_MODE, typename AttentionVariant,
          typename Params, typename WorkEstimationFunc>
inline cudaError_t DecodePlan(void* float_buffer, size_t float_workspace_size_in_bytes,
                              void* int_buffer, void* page_locked_int_buffer,
                              size_t int_workspace_size_in_bytes, DecodePlanInfo& plan_info,
                              typename Params::IdType* indptr_h, uint32_t batch_size,
                              uint32_t num_qo_heads, uint32_t page_size, bool enable_cuda_graph,
                              cudaStream_t stream, WorkEstimationFunc work_estimation_func) {
  size_t used_float_workspace_size = 0;
  size_t used_int_workspace_size = 0;
  return DecodePlanImpl<true, HEAD_DIM, POS_ENCODING_MODE, AttentionVariant, Params>(
      used_float_workspace_size, used_int_workspace_size, float_buffer,
      float_workspace_size_in_bytes, int_buffer, page_locked_int_buffer,
      int_workspace_size_in_bytes, plan_info, indptr_h, batch_size, num_qo_heads, page_size,
      enable_cuda_graph, stream, work_estimation_func);
}

template <uint32_t HEAD_DIM, PosEncodingMode POS_ENCODING_MODE, typename AttentionVariant,
          typename Params, typename WorkEstimationFunc>
inline cudaError_t DecodePlanWorkspaceSize(size_t& float_workspace_size_in_bytes,
                                           size_t& int_workspace_size_in_bytes,
                                           typename Params::IdType* indptr_h, uint32_t batch_size,
                                           uint32_t num_qo_heads, uint32_t page_size,
                                           bool enable_cuda_graph, cudaStream_t stream,
                                           WorkEstimationFunc work_estimation_func) {
  DecodePlanInfo plan_info;
  return DecodePlanImpl<false, HEAD_DIM, POS_ENCODING_MODE, AttentionVariant, Params>(
      float_workspace_size_in_bytes, int_workspace_size_in_bytes,
      /*float_buffer=*/nullptr, /*float_workspace_size_in_bytes=*/0,
      /*int_buffer=*/nullptr, /*page_locked_int_buffer=*/nullptr,
      /*int_workspace_size_in_bytes=*/0, plan_info, indptr_h, batch_size, num_qo_heads, page_size,
      enable_cuda_graph, stream, work_estimation_func);
}

template <typename IdType>
inline auto PrefillSplitQOKVIndptr(IdType* qo_indptr_h, IdType* kv_indptr_h,
                                   uint32_t total_num_rows, uint32_t batch_size,
                                   uint32_t num_qo_heads, uint32_t num_kv_heads, uint32_t head_dim,
                                   uint32_t page_size, uint32_t max_batch_size_if_split,
                                   bool enable_cuda_graph, int32_t window_left,
                                   int32_t fixed_split_size, bool disable_split_kv,
                                   int64_t uniform_q_len, uint32_t head_dim_qk = 0,
                                   uint32_t kv_dtype_bytes = 2) {
  std::vector<IdType> request_indices, qo_tile_indices, kv_tile_indices, merge_indptr, o_indptr;
  merge_indptr.push_back(0);
  o_indptr.push_back(0);

  const uint32_t gqa_group_size = num_qo_heads / num_kv_heads;

  // step 1: determine packed_qo_len_arr and verify qo_indptr contents.
  std::vector<int64_t> packed_qo_len_arr(batch_size), kv_len_arr(batch_size);
  for (uint32_t i = 0; i < batch_size; ++i) {
    packed_qo_len_arr[i] = int64_t(qo_indptr_h[i + 1] - qo_indptr_h[i]) * int64_t(gqa_group_size);
    if (packed_qo_len_arr[i] < 0) {
      std::ostringstream err_msg;
      err_msg << "qo_indptr[" << i + 1 << "]" << qo_indptr_h[i + 1] << " - qo_indptr[" << i << "]"
              << qo_indptr_h[i] << " should be non-negative";
      FLASHINFER_ERROR(err_msg.str());
    }
    kv_len_arr[i] = int64_t(kv_indptr_h[i + 1] - kv_indptr_h[i]);
    if (kv_len_arr[i] < 0) {
      std::ostringstream err_msg;
      err_msg << "kv_indptr[" << i + 1 << "]" << kv_indptr_h[i + 1] << " - kv_indptr[" << i << "]"
              << kv_indptr_h[i] << " should be non-negative";
      FLASHINFER_ERROR(err_msg.str());
    }
  }

  // step 2: determine cta_tile_q, kv_chunk_size and total_num_tiles_q
  const uint32_t min_kv_chunk_size = std::max((128 / page_size), 1U);
  uint32_t cta_tile_q;
  uint32_t total_num_tiles_q;
  if (enable_cuda_graph) {
    if (uniform_q_len > 0) {
      // The caller guarantees that every request has exactly uniform_q_len
      // rows in every plan replayed through this graph, so tiles can be
      // sized for that instead of the ragged worst case below.
      const uint64_t packed_uniform_len = uint64_t(uniform_q_len) * gqa_group_size;
      for (uint32_t i = 0; i < batch_size; ++i) {
        if (packed_qo_len_arr[i] != int64_t(packed_uniform_len)) {
          std::ostringstream err_msg;
          err_msg << "qo_indptr gives request " << i << " a packed qo length of "
                  << packed_qo_len_arr[i] << ", but uniform_q_len=" << uniform_q_len << " promises "
                  << packed_uniform_len;
          FLASHINFER_ERROR(err_msg.str());
        }
      }
      cta_tile_q = FA2DetermineCtaTileQ(packed_uniform_len, head_dim, head_dim_qk, kv_dtype_bytes);
      total_num_tiles_q = batch_size * ceil_div(packed_uniform_len, cta_tile_q);
    } else {
      // When CUDA graphs are enabled, the lengths of sequences determined by
      // qo_indptr_h can vary. We assume that the dummy data based on which
      // the CUDA graph is created fixes the maximum number of tokens.
      const uint64_t max_seq_len = total_num_rows - batch_size + 1;
      uint64_t max_qo_len = uint64_t(max_seq_len) * gqa_group_size;
      cta_tile_q = FA2DetermineCtaTileQ(max_qo_len, head_dim, head_dim_qk, kv_dtype_bytes);

      // Find an upper bound for the number of tiles, derived from the total
      // number of rows and the batch size.  The sum of qo lengths rounded
      // up to cta_tile_q will not exceed this number derived from the total
      // number of rows.
      total_num_tiles_q = ceil_div(total_num_rows * gqa_group_size, cta_tile_q) + batch_size - 1;
    }
  } else {
    int64_t sum_packed_qo_len = 0;
    for (uint32_t i = 0; i < batch_size; ++i) {
      sum_packed_qo_len += packed_qo_len_arr[i];
    }
    const int64_t avg_packed_qo_len = sum_packed_qo_len / batch_size;
    cta_tile_q = FA2DetermineCtaTileQ(avg_packed_qo_len, head_dim, head_dim_qk, kv_dtype_bytes);

    total_num_tiles_q = 0;
    for (uint32_t i = 0; i < batch_size; ++i) {
      total_num_tiles_q += ceil_div(packed_qo_len_arr[i], cta_tile_q);
    }
  }

  // Calculate the actual needed CTA when considering sliding window
  std::vector<int64_t> effective_kv_len_arr(batch_size);
  for (uint32_t i = 0; i < batch_size; ++i) {
    // pad CTA_TILE_Q to consider the causal kv-len
    effective_kv_len_arr[i] =
        std::min(window_left >= 0 ? ceil_div(window_left + cta_tile_q, page_size) : kv_len_arr[i],
                 kv_len_arr[i]);
  }
  bool split_kv = false;
  int64_t kv_chunk_size;
  if (disable_split_kv) {
    kv_chunk_size = std::numeric_limits<int64_t>::max();
  } else if (!disable_split_kv && fixed_split_size > 0) {
    kv_chunk_size = fixed_split_size;
  } else {
    std::tie(split_kv, kv_chunk_size) = PrefillBinarySearchKVChunkSize(
        enable_cuda_graph, max_batch_size_if_split, packed_qo_len_arr, effective_kv_len_arr,
        cta_tile_q, min_kv_chunk_size);
  }
  // step 3: split qo_indptr and kv_indptr
  uint32_t new_batch_size = 0;
  for (uint32_t request_idx = 0; request_idx < batch_size; ++request_idx) {
    const int64_t packed_qo_len = packed_qo_len_arr[request_idx];
    const int64_t num_tiles_q = ceil_div(packed_qo_len, cta_tile_q);
    const int64_t kv_len = std::max(int(effective_kv_len_arr[request_idx]), 1);
    const int64_t num_chunks_kv = disable_split_kv ? 1 : ceil_div(kv_len, kv_chunk_size);
    if (fixed_split_size > 0 && !disable_split_kv) {
      split_kv = split_kv || num_chunks_kv > 1;
    }
    for (uint32_t q_tile_idx = 0; q_tile_idx < num_tiles_q; ++q_tile_idx) {
      for (uint32_t kv_tile_idx = 0; kv_tile_idx < num_chunks_kv; ++kv_tile_idx) {
        new_batch_size += 1;
        request_indices.push_back(request_idx);
        qo_tile_indices.push_back(q_tile_idx);
        kv_tile_indices.push_back(kv_tile_idx);
      }
    }

    int64_t qo_len = packed_qo_len / gqa_group_size;
    for (uint32_t row = 0; row < qo_len; ++row) {
      merge_indptr.push_back(merge_indptr.back() + num_chunks_kv);
    }
    o_indptr.push_back(o_indptr.back() + qo_len * num_chunks_kv);
  }

  const size_t padded_batch_size =
      enable_cuda_graph ? std::max(max_batch_size_if_split, total_num_tiles_q) : new_batch_size;
  FLASHINFER_CHECK(new_batch_size <= padded_batch_size,
                   "new batch size should not exceed padded batch size. If you are using fixed "
                   "split size, please consider disabling cuda graph.");

  // step 4: multiply kv_chunk_size by page_size
  kv_chunk_size *= page_size;
  return std::make_tuple(split_kv, new_batch_size, padded_batch_size, cta_tile_q, kv_chunk_size,
                         std::move(request_indices), std::move(qo_tile_indices),
                         std::move(kv_tile_indices), std::move(merge_indptr), std::move(o_indptr));
}

struct PrefillPlanInfo {
  int64_t padded_batch_size;
  int64_t total_num_rows;
  int64_t total_num_rows_offset;
  int64_t cta_tile_q;
  int64_t request_indices_offset;
  int64_t qo_tile_indices_offset;
  int64_t kv_tile_indices_offset;
  int64_t merge_indptr_offset;
  int64_t o_indptr_offset;
  int64_t kv_chunk_size_ptr_offset;
  int64_t v_offset;
  int64_t s_offset;
  int64_t block_valid_mask_offset;
  bool enable_cuda_graph;
  bool split_kv;

  PrefillPlanInfo()
      : padded_batch_size(0),
        total_num_rows(0),
        total_num_rows_offset(0),
        cta_tile_q(0),
        request_indices_offset(0),
        qo_tile_indices_offset(0),
        kv_tile_indices_offset(0),
        merge_indptr_offset(0),
        o_indptr_offset(0),
        kv_chunk_size_ptr_offset(0),
        v_offset(0),
        s_offset(0),
        block_valid_mask_offset(0),
        enable_cuda_graph(false),
        split_kv(false) {}

  // convert PrefillPlanInfo to std::vector<int64_t>
  std::vector<int64_t> ToVector() const {
    return {padded_batch_size,
            total_num_rows,
            total_num_rows_offset,
            cta_tile_q,
            request_indices_offset,
            qo_tile_indices_offset,
            kv_tile_indices_offset,
            merge_indptr_offset,
            o_indptr_offset,
            kv_chunk_size_ptr_offset,
            v_offset,
            s_offset,
            block_valid_mask_offset,
            enable_cuda_graph,
            split_kv};
  }

  // From std::vector<int64_t> to PrefillPlanInfo
  void FromVector(const std::vector<int64_t>& vec) {
    if (vec.size() != 15) {
      std::ostringstream err_msg;
      err_msg << "PrefillPlanInfo::FromVector: vec.size() should be 15, but got " << vec.size();
      FLASHINFER_ERROR(err_msg.str());
    }
    padded_batch_size = vec[0];
    total_num_rows = vec[1];
    total_num_rows_offset = vec[2];
    cta_tile_q = vec[3];
    request_indices_offset = vec[4];
    qo_tile_indices_offset = vec[5];
    kv_tile_indices_offset = vec[6];
    merge_indptr_offset = vec[7];
    o_indptr_offset = vec[8];
    kv_chunk_size_ptr_offset = vec[9];
    v_offset = vec[10];
    s_offset = vec[11];
    block_valid_mask_offset = vec[12];
    enable_cuda_graph = vec[13];
    split_kv = vec[14];
  }
};

template <bool MATERIALIZE, typename IdType>
inline cudaError_t PrefillPlanImpl(
    size_t& float_workspace_size_out, size_t& int_workspace_size_out, void* float_buffer,
    size_t float_workspace_size_in_bytes, void* int_buffer, void* page_locked_int_buffer,
    size_t int_workspace_size_in_bytes, PrefillPlanInfo& plan_info, IdType* qo_indptr_h,
    IdType* kv_indptr_h, uint32_t total_num_rows, uint32_t batch_size, uint32_t num_qo_heads,
    uint32_t num_kv_heads, uint32_t head_dim_qk, uint32_t head_dim_vo, uint32_t page_size,
    bool enable_cuda_graph, uint32_t sizeof_dtype_o, int32_t window_left, int32_t fixed_split_size,
    bool disable_split_kv,
    int64_t num_colocated_ctas,  // for POD attention, limit prefill
                                 // splits by #colocated decode CTAs
    int64_t uniform_q_len, cudaStream_t stream, uint32_t kv_dtype_bytes = 2) {
  (void)sizeof_dtype_o;
  if (num_qo_heads % num_kv_heads != 0) {
    std::ostringstream err_msg;
    err_msg << "num_qo_heads " << num_qo_heads << " should be divisible by num_kv_heads "
            << num_kv_heads;
    FLASHINFER_ERROR(err_msg.str());
  }

  // step 0: get the number of SMs
  int num_sm = 0;
  int dev_id = 0;
  FLASHINFER_CUDA_CALL(cudaGetDevice(&dev_id));
  FLASHINFER_CUDA_CALL(cudaDeviceGetAttribute(&num_sm, cudaDevAttrMultiProcessorCount, dev_id));
  int num_blocks_per_sm = 2;
  int64_t available_ctas = static_cast<int64_t>(num_blocks_per_sm) * num_sm - num_colocated_ctas;
  int max_grid_size = static_cast<int>(std::max<int64_t>(0, available_ctas));
  uint32_t max_batch_size_if_split = max_grid_size / num_kv_heads;

  // step 2: determine kv_chunk_size
  auto [split_kv, new_batch_size, padded_batch_size, cta_tile_q, kv_chunk_size, request_indices_vec,
        qo_tile_indices_vec, kv_tile_indices_vec, merge_indptr_vec, o_indptr_vec] =
      PrefillSplitQOKVIndptr(qo_indptr_h, kv_indptr_h, total_num_rows, batch_size, num_qo_heads,
                             num_kv_heads, head_dim_vo, page_size, max_batch_size_if_split,
                             enable_cuda_graph, window_left, fixed_split_size, disable_split_kv,
                             uniform_q_len, head_dim_qk, kv_dtype_bytes);

  plan_info.cta_tile_q = cta_tile_q;
  plan_info.total_num_rows = total_num_rows;
  plan_info.enable_cuda_graph = enable_cuda_graph;
  plan_info.padded_batch_size = padded_batch_size;
  plan_info.split_kv = split_kv;

  AlignedAllocator int_allocator =
      MATERIALIZE ? AlignedAllocator(int_buffer, int_workspace_size_in_bytes) : AlignedAllocator();
  plan_info.request_indices_offset = int_allocator.aligned_alloc_offset(
      sizeof(IdType) * padded_batch_size, 16, "batch_prefill_request_indices");
  plan_info.qo_tile_indices_offset = int_allocator.aligned_alloc_offset(
      sizeof(IdType) * padded_batch_size, 16, "batch_prefill_qo_tile_indices");
  plan_info.kv_tile_indices_offset = int_allocator.aligned_alloc_offset(
      sizeof(IdType) * padded_batch_size, 16, "batch_prefill_kv_tile_indices");
  plan_info.o_indptr_offset = int_allocator.aligned_alloc_offset(sizeof(IdType) * (batch_size + 1),
                                                                 16, "batch_prefill_o_indptr");
  plan_info.kv_chunk_size_ptr_offset =
      int_allocator.aligned_alloc_offset(sizeof(IdType), 1, "batch_prefill_kv_chunk_size_ptr");

  if (plan_info.enable_cuda_graph) {
    plan_info.total_num_rows_offset =
        int_allocator.aligned_alloc_offset(sizeof(uint32_t), 16, "batch_prefill_total_num_rows");
    if constexpr (MATERIALIZE) {
      uint32_t* total_num_rows_h =
          GetPtrFromBaseOffset<uint32_t>(page_locked_int_buffer, plan_info.total_num_rows_offset);
      *total_num_rows_h = qo_indptr_h[batch_size];
    }
  }

  if constexpr (MATERIALIZE) {
    IdType* request_indices_h =
        GetPtrFromBaseOffset<IdType>(page_locked_int_buffer, plan_info.request_indices_offset);
    IdType* qo_tile_indices_h =
        GetPtrFromBaseOffset<IdType>(page_locked_int_buffer, plan_info.qo_tile_indices_offset);
    IdType* kv_tile_indices_h =
        GetPtrFromBaseOffset<IdType>(page_locked_int_buffer, plan_info.kv_tile_indices_offset);
    IdType* o_indptr_h =
        GetPtrFromBaseOffset<IdType>(page_locked_int_buffer, plan_info.o_indptr_offset);
    IdType* kv_chunk_size_ptr_h =
        GetPtrFromBaseOffset<IdType>(page_locked_int_buffer, plan_info.kv_chunk_size_ptr_offset);
    std::copy(request_indices_vec.begin(), request_indices_vec.end(), request_indices_h);
    std::copy(qo_tile_indices_vec.begin(), qo_tile_indices_vec.end(), qo_tile_indices_h);
    std::copy(kv_tile_indices_vec.begin(), kv_tile_indices_vec.end(), kv_tile_indices_h);
    std::copy(o_indptr_vec.begin(), o_indptr_vec.end(), o_indptr_h);
    kv_chunk_size_ptr_h[0] = kv_chunk_size;
  } else {
    (void)new_batch_size;
    (void)kv_chunk_size;
    (void)request_indices_vec;
    (void)qo_tile_indices_vec;
    (void)kv_tile_indices_vec;
    (void)o_indptr_vec;
  }

  AlignedAllocator float_allocator;
  if (split_kv) {
    if constexpr (MATERIALIZE) {
      float_allocator = AlignedAllocator(float_buffer, float_workspace_size_in_bytes);
    }
    plan_info.v_offset = float_allocator.aligned_alloc_offset(
        num_qo_heads * padded_batch_size * cta_tile_q * head_dim_vo * sizeof(float), 16,
        "batch_prefill_tmp_v");
    plan_info.s_offset = float_allocator.aligned_alloc_offset(
        num_qo_heads * padded_batch_size * cta_tile_q * sizeof(float), 16, "batch_prefill_tmp_s");
    plan_info.merge_indptr_offset = int_allocator.aligned_alloc_offset(
        sizeof(IdType) * (plan_info.total_num_rows + 1), 16, "batch_prefill_merge_indptr");
    plan_info.block_valid_mask_offset = int_allocator.aligned_alloc_offset(
        sizeof(bool) * padded_batch_size, 16, "batch_prefill_block_valid_mask");

    if constexpr (MATERIALIZE) {
      IdType* merge_indptr_h =
          GetPtrFromBaseOffset<IdType>(page_locked_int_buffer, plan_info.merge_indptr_offset);
      bool* block_valid_mask_h =
          GetPtrFromBaseOffset<bool>(page_locked_int_buffer, plan_info.block_valid_mask_offset);
      std::copy(merge_indptr_vec.begin(), merge_indptr_vec.end(), merge_indptr_h);
      for (uint32_t i = 0; i < padded_batch_size; ++i) {
        block_valid_mask_h[i] = i < new_batch_size;
      }
    } else {
      (void)merge_indptr_vec;
    }
  }

  float_workspace_size_out = float_allocator.num_allocated_bytes();
  int_workspace_size_out = int_allocator.num_allocated_bytes();
  if constexpr (MATERIALIZE) {
    FLASHINFER_CUDA_CALL(cudaMemcpyAsync(int_buffer, page_locked_int_buffer, int_workspace_size_out,
                                         cudaMemcpyHostToDevice, stream));
  } else {
    (void)stream;
  }

  return cudaSuccess;
}

template <typename IdType>
inline cudaError_t PrefillPlan(void* float_buffer, size_t float_workspace_size_in_bytes,
                               void* int_buffer, void* page_locked_int_buffer,
                               size_t int_workspace_size_in_bytes, PrefillPlanInfo& plan_info,
                               IdType* qo_indptr_h, IdType* kv_indptr_h, uint32_t total_num_rows,
                               uint32_t batch_size, uint32_t num_qo_heads, uint32_t num_kv_heads,
                               uint32_t head_dim_qk, uint32_t head_dim_vo, uint32_t page_size,
                               bool enable_cuda_graph, uint32_t sizeof_dtype_o, int32_t window_left,
                               int32_t fixed_split_size, bool disable_split_kv,
                               int64_t num_colocated_ctas,  // for POD attention, limit prefill
                                                            // splits by #colocated decode CTAs
                               int64_t uniform_q_len, cudaStream_t stream,
                               uint32_t kv_dtype_bytes = 2) {
  size_t used_float_workspace_size = 0;
  size_t used_int_workspace_size = 0;
  return PrefillPlanImpl<true>(used_float_workspace_size, used_int_workspace_size, float_buffer,
                               float_workspace_size_in_bytes, int_buffer, page_locked_int_buffer,
                               int_workspace_size_in_bytes, plan_info, qo_indptr_h, kv_indptr_h,
                               total_num_rows, batch_size, num_qo_heads, num_kv_heads, head_dim_qk,
                               head_dim_vo, page_size, enable_cuda_graph, sizeof_dtype_o,
                               window_left, fixed_split_size, disable_split_kv, num_colocated_ctas,
                               uniform_q_len, stream, kv_dtype_bytes);
}

template <typename IdType>
inline cudaError_t PrefillPlanWorkspaceSize(
    size_t& float_workspace_size_in_bytes, size_t& int_workspace_size_in_bytes, IdType* qo_indptr_h,
    IdType* kv_indptr_h, uint32_t total_num_rows, uint32_t batch_size, uint32_t num_qo_heads,
    uint32_t num_kv_heads, uint32_t head_dim_qk, uint32_t head_dim_vo, uint32_t page_size,
    bool enable_cuda_graph, uint32_t sizeof_dtype_o, int32_t window_left, int32_t fixed_split_size,
    bool disable_split_kv, int64_t num_colocated_ctas, int64_t uniform_q_len, cudaStream_t stream,
    uint32_t kv_dtype_bytes = 2) {
  PrefillPlanInfo plan_info;
  return PrefillPlanImpl<false>(float_workspace_size_in_bytes, int_workspace_size_in_bytes,
                                /*float_buffer=*/nullptr, /*float_workspace_size_in_bytes=*/0,
                                /*int_buffer=*/nullptr, /*page_locked_int_buffer=*/nullptr,
                                /*int_workspace_size_in_bytes=*/0, plan_info, qo_indptr_h,
                                kv_indptr_h, total_num_rows, batch_size, num_qo_heads, num_kv_heads,
                                head_dim_qk, head_dim_vo, page_size, enable_cuda_graph,
                                sizeof_dtype_o, window_left, fixed_split_size, disable_split_kv,
                                num_colocated_ctas, uniform_q_len, stream, kv_dtype_bytes);
}

inline float cost_function(int qo_len, int kv_len) { return 2 * float(qo_len) + kv_len; }

template <typename T>
std::vector<T> flatten(const std::vector<std::vector<T>>& vec, int size_after_flatten) {
  std::vector<T> result;
  result.reserve(size_after_flatten);
  for (const auto& inner_vec : vec) {
    result.insert(result.end(), inner_vec.begin(), inner_vec.end());
  }
  return result;
}

inline int packed_causal_kv_end(int qo_len, int kv_len, int qo_tile_idx, int cluster_tile_q,
                                int num_qo_tiles, int group_size) {
  if (qo_tile_idx + 1 == num_qo_tiles) {
    return kv_len;
  }
  int kv_len_init = kv_len - qo_len;  // right aligned
  return max(min(kv_len_init + ceil_div((qo_tile_idx + 1) * cluster_tile_q, group_size), kv_len),
             0);
}

struct PrefillPlanSM90Info {
  int64_t qo_tile_indices_offset;
  int64_t qo_indptr_offset;
  int64_t kv_indptr_offset;
  int64_t qo_len_offset;
  int64_t kv_len_offset;
  int64_t head_indices_offset;
  int64_t work_indptr_offset;
  int64_t batch_indices_offset;
  bool same_schedule_for_all_heads;

  PrefillPlanSM90Info()
      : qo_tile_indices_offset(0),
        qo_indptr_offset(0),
        kv_indptr_offset(0),
        qo_len_offset(0),
        kv_len_offset(0),
        head_indices_offset(0),
        work_indptr_offset(0),
        batch_indices_offset(0),
        same_schedule_for_all_heads(false) {}

  // convert PrefillPlanSM90Info to std::vector<int64_t>
  std::vector<int64_t> ToVector() const {
    return {qo_tile_indices_offset, qo_indptr_offset,     kv_indptr_offset,
            qo_len_offset,          kv_len_offset,        head_indices_offset,
            work_indptr_offset,     batch_indices_offset, same_schedule_for_all_heads};
  }

  // From std::vector<int64_t> to PrefillPlanSM90Info
  void FromVector(const std::vector<int64_t>& vec) {
    if (vec.size() != 9) {
      std::ostringstream err_msg;
      err_msg << "PrefillPlanSM90Info::FromVector: vec.size() should be 9, but got " << vec.size();
      FLASHINFER_ERROR(err_msg.str());
    }
    qo_tile_indices_offset = vec[0];
    qo_indptr_offset = vec[1];
    kv_indptr_offset = vec[2];
    qo_len_offset = vec[3];
    kv_len_offset = vec[4];
    head_indices_offset = vec[5];
    work_indptr_offset = vec[6];
    batch_indices_offset = vec[7];
    same_schedule_for_all_heads = vec[8];
  }
};

template <typename IdType>
inline cudaError_t PrefillSM90Plan(
    void* float_buffer, size_t float_workspace_size_in_bytes, void* int_buffer,
    void* page_locked_int_buffer, size_t int_workspace_size_in_bytes,
    PrefillPlanSM90Info& plan_info, IdType* qo_indptr_h, IdType* kv_indptr_h, IdType* kv_len_arr_h,
    uint32_t total_num_rows, uint32_t batch_size, uint32_t num_qo_heads, uint32_t num_kv_heads,
    uint32_t head_dim_qk, uint32_t head_dim_vo, uint32_t page_size, bool causal,
    bool enable_cuda_graph, uint32_t sizeof_dtype_o, cudaStream_t stream) {
  if (num_qo_heads % num_kv_heads != 0) {
    std::ostringstream err_msg;
    err_msg << "num_qo_heads " << num_qo_heads << " should be divisible by num_kv_heads "
            << num_kv_heads;
    FLASHINFER_ERROR(err_msg.str());
  }

  std::vector<std::tuple<int, int, int>> idx_qo_kv_len_vec;
  for (uint32_t i = 0; i < batch_size; ++i) {
    int qo_len = qo_indptr_h[i + 1] - qo_indptr_h[i];
    int kv_len = kv_len_arr_h[i];
    if (kv_len < 0) {
      std::ostringstream err_msg;
      err_msg << "kv_len[" << i << "]" << kv_len << " should be non-negative";
      FLASHINFER_ERROR(err_msg.str());
    }
    if (qo_len < 0) {
      std::ostringstream err_msg;
      err_msg << "qo_indptr[" << i + 1 << "]" << qo_indptr_h[i + 1] << " - qo_indptr[" << i << "]"
              << qo_indptr_h[i] << " should be non-negative";
      FLASHINFER_ERROR(err_msg.str());
    }
    idx_qo_kv_len_vec.push_back({i, qo_len, kv_len});
  }

  std::sort(idx_qo_kv_len_vec.begin(), idx_qo_kv_len_vec.end(),
            [](const auto& a, const auto& b) { return std::get<2>(a) > std::get<2>(b); });
  int cta_tile_q = 128;
  if (head_dim_vo == 64) {
    cta_tile_q = 192;
  }

  int device = 0;
  FLASHINFER_CUDA_CALL(cudaGetDevice(&device));
  int num_sm90_ctas = 0;
  FLASHINFER_CUDA_CALL(
      cudaDeviceGetAttribute(&num_sm90_ctas, cudaDevAttrMultiProcessorCount, device));

  MinHeap cta_cost_heap(num_sm90_ctas);
  std::vector<std::vector<IdType>> cta_qo_tile_indices(num_sm90_ctas, std::vector<IdType>()),
      cta_qo_indptr(num_sm90_ctas, std::vector<IdType>()),
      cta_kv_indptr(num_sm90_ctas, std::vector<IdType>()),
      cta_qo_len(num_sm90_ctas, std::vector<IdType>()),
      cta_kv_len(num_sm90_ctas, std::vector<IdType>()),
      cta_head_indices(num_sm90_ctas, std::vector<IdType>()),
      cta_batch_indices(num_sm90_ctas, std::vector<IdType>());

  int max_num_works_per_head = ceil_div(total_num_rows, cta_tile_q) + batch_size - 1;
  plan_info.same_schedule_for_all_heads = max_num_works_per_head > 4096;

  for (int qo_head_idx = 0;
       qo_head_idx < (plan_info.same_schedule_for_all_heads ? 1 : num_qo_heads); ++qo_head_idx) {
    for (auto& [i, qo_len, kv_len] : idx_qo_kv_len_vec) {
      int num_qo_tiles = ceil_div(qo_len, cta_tile_q);
      for (int qo_tile_idx = num_qo_tiles - 1; qo_tile_idx >= 0; --qo_tile_idx) {
        auto [cta_idx, accum_cost] = cta_cost_heap.pop();
        // NOTE(Zihao): our current FA3 implementation do not fuse query and group heads
        // so the group_size in cost_function is always 1
        int effective_kv_len =
            causal ? packed_causal_kv_end(qo_len, kv_len, qo_tile_idx, cta_tile_q, num_qo_tiles, 1)
                   : kv_len;
        cta_cost_heap.insert({cta_idx, accum_cost + cost_function(cta_tile_q, effective_kv_len)});
        cta_qo_tile_indices[cta_idx].push_back(qo_tile_idx);
        cta_qo_indptr[cta_idx].push_back(qo_indptr_h[i]);
        cta_qo_len[cta_idx].push_back(qo_len);
        cta_kv_indptr[cta_idx].push_back(kv_indptr_h[i]);
        cta_kv_len[cta_idx].push_back(kv_len);
        cta_head_indices[cta_idx].push_back(qo_head_idx);
        cta_batch_indices[cta_idx].push_back(i);
      }
    }
  }

  std::vector<IdType> work_indptr_vec(num_sm90_ctas + 1, 0);
  for (uint32_t i = 0; i < num_sm90_ctas; ++i) {
    work_indptr_vec[i + 1] = work_indptr_vec[i] + cta_qo_tile_indices[i].size();
  }
  int total_num_works = work_indptr_vec.back();
  auto qo_tile_indices_vec = flatten(cta_qo_tile_indices, total_num_works);
  auto qo_indptr_vec = flatten(cta_qo_indptr, total_num_works);
  auto kv_indptr_vec = flatten(cta_kv_indptr, total_num_works);
  auto qo_len_vec = flatten(cta_qo_len, total_num_works);
  auto kv_len_vec = flatten(cta_kv_len, total_num_works);
  auto head_indices_vec = flatten(cta_head_indices, total_num_works);
  auto batch_indices_vec = flatten(cta_batch_indices, total_num_works);

  AlignedAllocator int_allocator(int_buffer, int_workspace_size_in_bytes);
  int max_total_num_works;

  if (enable_cuda_graph) {
    max_total_num_works = plan_info.same_schedule_for_all_heads
                              ? max_num_works_per_head
                              : max_num_works_per_head * num_qo_heads;
  } else {
    max_total_num_works = total_num_works;
  }

  plan_info.qo_tile_indices_offset = int_allocator.aligned_alloc_offset(
      sizeof(IdType) * max_total_num_works, 16, "batch_prefill_sm90_qo_tile_indices");
  plan_info.qo_indptr_offset = int_allocator.aligned_alloc_offset(
      sizeof(IdType) * max_total_num_works, 16, "batch_prefill_sm90_qo_offset");
  plan_info.kv_indptr_offset = int_allocator.aligned_alloc_offset(
      sizeof(IdType) * max_total_num_works, 16, "batch_prefill_sm90_kv_offset");
  plan_info.qo_len_offset = int_allocator.aligned_alloc_offset(sizeof(IdType) * max_total_num_works,
                                                               16, "batch_prefill_sm90_qo_len");
  plan_info.kv_len_offset = int_allocator.aligned_alloc_offset(sizeof(IdType) * max_total_num_works,
                                                               16, "batch_prefill_sm90_kv_len");
  plan_info.head_indices_offset = int_allocator.aligned_alloc_offset(
      sizeof(IdType) * max_total_num_works, 16, "batch_prefill_sm90_head_indices");
  plan_info.work_indptr_offset = int_allocator.aligned_alloc_offset(
      sizeof(IdType) * (num_sm90_ctas + 1), 16, "batch_prefill_sm90_work_indptr");
  plan_info.batch_indices_offset = int_allocator.aligned_alloc_offset(
      sizeof(IdType) * max_total_num_works, 16, "batch_prefill_sm90_batch_indices");

  IdType* qo_tile_indices_h =
      GetPtrFromBaseOffset<IdType>(page_locked_int_buffer, plan_info.qo_tile_indices_offset);
  IdType* qo_offset_h =
      GetPtrFromBaseOffset<IdType>(page_locked_int_buffer, plan_info.qo_indptr_offset);
  IdType* kv_offset_h =
      GetPtrFromBaseOffset<IdType>(page_locked_int_buffer, plan_info.kv_indptr_offset);
  IdType* qo_len_h = GetPtrFromBaseOffset<IdType>(page_locked_int_buffer, plan_info.qo_len_offset);
  IdType* kv_len_h = GetPtrFromBaseOffset<IdType>(page_locked_int_buffer, plan_info.kv_len_offset);
  IdType* head_indices_h =
      GetPtrFromBaseOffset<IdType>(page_locked_int_buffer, plan_info.head_indices_offset);
  IdType* work_indptr_h =
      GetPtrFromBaseOffset<IdType>(page_locked_int_buffer, plan_info.work_indptr_offset);
  IdType* batch_indices_h =
      GetPtrFromBaseOffset<IdType>(page_locked_int_buffer, plan_info.batch_indices_offset);

  std::copy(qo_tile_indices_vec.begin(), qo_tile_indices_vec.end(), qo_tile_indices_h);
  std::copy(qo_indptr_vec.begin(), qo_indptr_vec.end(), qo_offset_h);
  std::copy(kv_indptr_vec.begin(), kv_indptr_vec.end(), kv_offset_h);
  std::copy(qo_len_vec.begin(), qo_len_vec.end(), qo_len_h);
  std::copy(kv_len_vec.begin(), kv_len_vec.end(), kv_len_h);
  std::copy(head_indices_vec.begin(), head_indices_vec.end(), head_indices_h);
  std::copy(work_indptr_vec.begin(), work_indptr_vec.end(), work_indptr_h);
  std::copy(batch_indices_vec.begin(), batch_indices_vec.end(), batch_indices_h);

  size_t num_bytes_to_copy = int_allocator.num_allocated_bytes();
  FLASHINFER_CUDA_CALL(cudaMemcpyAsync(int_buffer, page_locked_int_buffer, num_bytes_to_copy,
                                       cudaMemcpyHostToDevice, stream));
  return cudaSuccess;
}

template <uint32_t NUM_TASKS>
struct HolisticPlanInfo {
  int64_t num_blks_x;
  int64_t num_blks_y;
  struct {
    int64_t q_indptr_offset;
    int64_t kv_indptr_offset;
    int64_t partial_indptr_offset;
    int64_t q_len_offset;
    int64_t kv_len_offset;
    int64_t q_start_offset;
    int64_t kv_start_offset;
    int64_t kv_end_offset;
    int64_t kv_head_idx_offset;
    int64_t work_indptr_offset;
  } tasks[NUM_TASKS];
  int64_t len_kv_chunk_offset;
  int64_t partial_o_offset;
  int64_t partial_lse_offset;
  int64_t merge_indptr_offset;
  int64_t merge_o_indices_offset;
  int64_t num_qo_len_offset;

  static constexpr uint32_t NUM_TASK_ARGS = 10;
  static constexpr uint32_t NUM_SHARED_ARGS = 8;

  std::vector<int64_t> ToVector() const {
    std::vector<int64_t> vec;
    vec.push_back(num_blks_x);
    vec.push_back(num_blks_y);
    for (uint32_t i = 0; i < NUM_TASKS; ++i) {
      vec.push_back(tasks[i].q_indptr_offset);
      vec.push_back(tasks[i].kv_indptr_offset);
      vec.push_back(tasks[i].partial_indptr_offset);
      vec.push_back(tasks[i].q_len_offset);
      vec.push_back(tasks[i].kv_len_offset);
      vec.push_back(tasks[i].q_start_offset);
      vec.push_back(tasks[i].kv_start_offset);
      vec.push_back(tasks[i].kv_end_offset);
      vec.push_back(tasks[i].kv_head_idx_offset);
      vec.push_back(tasks[i].work_indptr_offset);
    }
    vec.push_back(len_kv_chunk_offset);
    vec.push_back(partial_o_offset);
    vec.push_back(partial_lse_offset);
    vec.push_back(merge_indptr_offset);
    vec.push_back(merge_o_indices_offset);
    vec.push_back(num_qo_len_offset);
    return vec;
  }

  void FromVector(const std::vector<int64_t>& vec) {
    if (vec.size() != NUM_SHARED_ARGS + NUM_TASKS * NUM_TASK_ARGS) {
      std::ostringstream err_msg;
      err_msg << "HolisticPlanInfo::FromVector: vec.size() should be "
              << NUM_SHARED_ARGS + NUM_TASKS * NUM_TASK_ARGS << ", but got " << vec.size();
      FLASHINFER_ERROR(err_msg.str());
    }
    num_blks_x = vec[0];
    num_blks_y = vec[1];
    for (uint32_t i = 0; i < NUM_TASKS; ++i) {
      tasks[i].q_indptr_offset = vec[2 + i * NUM_TASK_ARGS + 0];
      tasks[i].kv_indptr_offset = vec[2 + i * NUM_TASK_ARGS + 1];
      tasks[i].partial_indptr_offset = vec[2 + i * NUM_TASK_ARGS + 2];
      tasks[i].q_len_offset = vec[2 + i * NUM_TASK_ARGS + 3];
      tasks[i].kv_len_offset = vec[2 + i * NUM_TASK_ARGS + 4];
      tasks[i].q_start_offset = vec[2 + i * NUM_TASK_ARGS + 5];
      tasks[i].kv_start_offset = vec[2 + i * NUM_TASK_ARGS + 6];
      tasks[i].kv_end_offset = vec[2 + i * NUM_TASK_ARGS + 7];
      tasks[i].kv_head_idx_offset = vec[2 + i * NUM_TASK_ARGS + 8];
      tasks[i].work_indptr_offset = vec[2 + i * NUM_TASK_ARGS + 9];
    }
    len_kv_chunk_offset = vec[2 + NUM_TASKS * NUM_TASK_ARGS];
    partial_o_offset = vec[3 + NUM_TASKS * NUM_TASK_ARGS];
    partial_lse_offset = vec[4 + NUM_TASKS * NUM_TASK_ARGS];
    merge_indptr_offset = vec[5 + NUM_TASKS * NUM_TASK_ARGS];
    merge_o_indices_offset = vec[6 + NUM_TASKS * NUM_TASK_ARGS];
    num_qo_len_offset = vec[7 + NUM_TASKS * NUM_TASK_ARGS];
  }
};

template <typename IdType>
inline cudaError_t TwoStageHolisticPlan(void* float_buffer, size_t float_workspace_size_in_bytes,
                                        void* int_buffer, void* page_locked_int_buffer,
                                        size_t int_workspace_size_in_bytes,
                                        HolisticPlanInfo<2>& plan_info, IdType* qo_indptr_h,
                                        IdType* kv_indptr_h, IdType* kv_len_arr_h,
                                        uint32_t batch_size, uint32_t num_qo_heads,
                                        uint32_t num_kv_heads, uint32_t head_dim, bool causal,
                                        cudaStream_t stream) {
  constexpr uint32_t NUM_TASKS = 2;
  const uint32_t CTA_TILE_Q_SIZES[NUM_TASKS] = {128, 16};
  int num_sm = 0;
  int dev_id = 0;

  uint32_t gqa_group_size = num_qo_heads / num_kv_heads;
  FLASHINFER_CUDA_CALL(cudaGetDevice(&dev_id));
  FLASHINFER_CUDA_CALL(cudaDeviceGetAttribute(&num_sm, cudaDevAttrMultiProcessorCount, dev_id));

  if (head_dim >= 256) {
    // NOTE (Yilong): optimize this code path
    // constraint gridDim due to cooperative group
    num_sm *= 1;
  } else {
    // NOTE(Zihao): two cta per sm
    num_sm *= 2;
  }

  // step 0. determine the number of blocks in x and y dimensions
  std::vector<std::tuple<int, int, int>> idx_qo_kv_len_vec[NUM_TASKS];
  for (uint32_t i = 0; i < batch_size; ++i) {
    if (qo_indptr_h[i + 1] - qo_indptr_h[i] < 0) {
      std::ostringstream err_msg;
      err_msg << "qo_indptr[" << i + 1 << "]" << qo_indptr_h[i + 1] << " - qo_indptr[" << i << "]"
              << qo_indptr_h[i] << " should be non-negative";
      FLASHINFER_ERROR(err_msg.str());
    }

    int qo_len = qo_indptr_h[i + 1] - qo_indptr_h[i];
    int packed_qo_len = qo_len * gqa_group_size;
    int kv_len = kv_len_arr_h[i];

    if (packed_qo_len > CTA_TILE_Q_SIZES[1]) {
      idx_qo_kv_len_vec[0].push_back({i, qo_len, kv_len});
    } else {
      idx_qo_kv_len_vec[1].push_back({i, qo_len, kv_len});
    }
  }

  int cluster_size = 1;
  int num_clusters = num_sm / cluster_size;
  plan_info.num_blks_x = cluster_size;
  plan_info.num_blks_y = num_clusters;

  auto f = [](int x) {
    if (x <= 128) {
      // This aligns with CTA_TILE_KV in persistent mainloop
      // NOTE (Yilong): Optimize here for smaller batch/seqlen scenarios
      return 128;
    }
    return ceil_div(x, 256) * 256;
  };

  MinHeap cluster_cost_heap(num_clusters);
  AlignedAllocator int_allocator(int_buffer, int_workspace_size_in_bytes);

  // NOTE(Zihao): adjust it later
  const int max_total_num_works = 65536;
  const int max_num_kv_splits =
      4 * num_clusters * cluster_size * (CTA_TILE_Q_SIZES[0] + CTA_TILE_Q_SIZES[1]);

  // calculate kv_len_limit first, considering all workloads
  int64_t total_kv_lens = 0;
  for (uint32_t task = 0; task < NUM_TASKS; ++task) {
    int cluster_tile_q = CTA_TILE_Q_SIZES[task] * cluster_size;
    for (auto& [_, qo_len, kv_len] : idx_qo_kv_len_vec[task]) {
      int packed_qo_len = qo_len * gqa_group_size;
      int num_qo_tiles = ceil_div(packed_qo_len, cluster_tile_q);
      for (int qo_tile_idx = num_qo_tiles - 1; qo_tile_idx >= 0; --qo_tile_idx) {
        int effective_kv_len =
            causal ? packed_causal_kv_end(qo_len, kv_len, qo_tile_idx, cluster_tile_q, num_qo_tiles,
                                          gqa_group_size)
                   : kv_len;
        total_kv_lens += effective_kv_len;
      }
    }
  }

  // used for remapping the output offsets
  // layout [packed_qo_len x num_kv_tiles, num_kv_heads, head_dim]
  int partial_o_nnz = 0;
  std::vector<IdType> merge_indptr, merge_o_indices, num_expand_qo_len_vec;
  std::vector<IdType> cluster_len_kv_chunk(NUM_TASKS, 0);
  merge_indptr.push_back(partial_o_nnz);
  for (uint32_t task = 0; task < NUM_TASKS; ++task) {
    int cluster_tile_q = CTA_TILE_Q_SIZES[task] * cluster_size;
    int kv_len_limit = f(std::max(ceil_div(total_kv_lens * num_kv_heads, num_clusters), 1L));
    if (cluster_tile_q >= 64) {
      // chunked-prefill workloads are much more expensive than decode
      // so we use a smaller kv_len_limit for chunked-prefill workloads
      kv_len_limit /= std::min(num_kv_heads, 2U);
    }
    cluster_len_kv_chunk[task] = kv_len_limit;
    std::vector<std::vector<IdType>> cluster_q_indptr(num_clusters, std::vector<IdType>()),
        cluster_kv_indptr(num_clusters, std::vector<IdType>()),
        cluster_q_len(num_clusters, std::vector<IdType>()),
        cluster_kv_len(num_clusters, std::vector<IdType>()),
        cluster_q_start(num_clusters, std::vector<IdType>()),
        cluster_kv_start(num_clusters, std::vector<IdType>()),
        cluster_kv_end(num_clusters, std::vector<IdType>()),
        cluster_kv_head_idx(num_clusters, std::vector<IdType>()),
        cluster_partial_indptr(num_clusters, std::vector<IdType>());

    for (auto& [i, qo_len, kv_len] : idx_qo_kv_len_vec[task]) {
      int packed_qo_len = qo_len * gqa_group_size;
      int num_qo_tiles = ceil_div(packed_qo_len, cluster_tile_q);
      // NOTE (Yilong): this ordering correspoinds to the layout of reduction kernel
      for (int qo_tile_idx = 0; qo_tile_idx < num_qo_tiles; ++qo_tile_idx) {
        int remaining_len = causal
                                ? packed_causal_kv_end(qo_len, kv_len, qo_tile_idx, cluster_tile_q,
                                                       num_qo_tiles, gqa_group_size)
                                : kv_len;
        int kv_start = 0;
        bool split_kv = remaining_len > kv_len_limit;
        int num_kv_tiles = split_kv ? ceil_div(remaining_len, kv_len_limit) : 1;
        int row_tile_size = std::min(cluster_tile_q, packed_qo_len - qo_tile_idx * cluster_tile_q);
        bool zero_kv_len = (remaining_len == 0);
        while (remaining_len > 0 || zero_kv_len) {
          int actual_len = std::min(remaining_len, kv_len_limit);
          for (uint32_t kv_head_idx = 0; kv_head_idx < num_kv_heads; ++kv_head_idx) {
            auto [cluster_idx, accum_cost] = cluster_cost_heap.pop();
            cluster_cost_heap.insert(
                {cluster_idx, accum_cost + cost_function(cluster_tile_q, actual_len)});
            cluster_q_len[cluster_idx].push_back(qo_len);
            cluster_kv_len[cluster_idx].push_back(kv_len);
            cluster_q_indptr[cluster_idx].push_back(qo_indptr_h[i]);
            cluster_kv_indptr[cluster_idx].push_back(kv_indptr_h[i]);

            // use kv_chunk to rematerize num_kv_tiles and kv_tile_idx
            cluster_partial_indptr[cluster_idx].push_back(partial_o_nnz);

            cluster_q_start[cluster_idx].push_back(qo_tile_idx * cluster_tile_q);
            cluster_kv_start[cluster_idx].push_back(kv_start);
            cluster_kv_end[cluster_idx].push_back(kv_start + actual_len);
            cluster_kv_head_idx[cluster_idx].push_back(kv_head_idx);
          }
          remaining_len -= actual_len;
          zero_kv_len = (remaining_len == 0);
          kv_start += actual_len;
          if (zero_kv_len) {
            break;
          }
        }
        if (split_kv) {
          // non-split kv is directly written through
          for (int row = 0; row < row_tile_size; ++row) {
            merge_indptr.push_back(merge_indptr.back() + num_kv_tiles);
            // output layout: [qo_len, num_kv_heads, gqa_group_size, head_dim]
            // merge_o_indices is the indices of `gqa_group_size` dimension
            auto q = (qo_tile_idx * cluster_tile_q + row) / gqa_group_size,
                 r = (qo_tile_idx * cluster_tile_q + row) % gqa_group_size;
            merge_o_indices.push_back((qo_indptr_h[i] + q) * num_kv_heads * gqa_group_size + r);
          }
          partial_o_nnz += row_tile_size * num_kv_tiles;
        }
      }
    }

    std::vector<IdType> work_indptr_vec(num_clusters + 1, 0);
    for (uint32_t i = 0; i < num_clusters; ++i) {
      work_indptr_vec[i + 1] = work_indptr_vec[i] + cluster_q_indptr[i].size();
    }
    int total_num_works = work_indptr_vec.back();
    if (total_num_works > max_total_num_works) {
      std::ostringstream err_msg;
      err_msg << "total_num_works (#q tiles * #kv tiles) " << total_num_works
              << " exceeds max_total_num_works " << max_total_num_works;
      FLASHINFER_ERROR(err_msg.str());
    }
    auto q_indptr_vec = flatten(cluster_q_indptr, total_num_works);
    auto kv_indptr_vec = flatten(cluster_kv_indptr, total_num_works);
    auto partial_indptr_vec = flatten(cluster_partial_indptr, total_num_works);
    auto q_len_vec = flatten(cluster_q_len, total_num_works);
    auto kv_len_vec = flatten(cluster_kv_len, total_num_works);
    auto q_start_vec = flatten(cluster_q_start, total_num_works);
    auto kv_start_vec = flatten(cluster_kv_start, total_num_works);
    auto kv_end_vec = flatten(cluster_kv_end, total_num_works);
    auto kv_head_idx_vec = flatten(cluster_kv_head_idx, total_num_works);

    plan_info.tasks[task].q_indptr_offset =
        int_allocator.aligned_alloc_offset(sizeof(IdType) * max_total_num_works, 16, "q_indptr");
    plan_info.tasks[task].kv_indptr_offset =
        int_allocator.aligned_alloc_offset(sizeof(IdType) * max_total_num_works, 16, "kv_indptr");
    plan_info.tasks[task].partial_indptr_offset = int_allocator.aligned_alloc_offset(
        sizeof(IdType) * max_total_num_works, 16, "partial_indptr");
    plan_info.tasks[task].q_len_offset =
        int_allocator.aligned_alloc_offset(sizeof(IdType) * max_total_num_works, 16, "q_len");
    plan_info.tasks[task].kv_len_offset =
        int_allocator.aligned_alloc_offset(sizeof(IdType) * max_total_num_works, 16, "kv_len");
    plan_info.tasks[task].q_start_offset =
        int_allocator.aligned_alloc_offset(sizeof(IdType) * max_total_num_works, 16, "q_start");
    plan_info.tasks[task].kv_start_offset =
        int_allocator.aligned_alloc_offset(sizeof(IdType) * max_total_num_works, 16, "kv_start");
    plan_info.tasks[task].kv_end_offset =
        int_allocator.aligned_alloc_offset(sizeof(IdType) * max_total_num_works, 16, "kv_end");
    plan_info.tasks[task].kv_head_idx_offset =
        int_allocator.aligned_alloc_offset(sizeof(IdType) * max_total_num_works, 16, "kv_head_idx");
    plan_info.tasks[task].work_indptr_offset =
        int_allocator.aligned_alloc_offset(sizeof(IdType) * max_total_num_works, 16, "work_indptr");

    CopyToPageLockedBuffer(page_locked_int_buffer, plan_info.tasks[task].q_indptr_offset,
                           q_indptr_vec);
    CopyToPageLockedBuffer(page_locked_int_buffer, plan_info.tasks[task].kv_indptr_offset,
                           kv_indptr_vec);
    CopyToPageLockedBuffer(page_locked_int_buffer, plan_info.tasks[task].partial_indptr_offset,
                           partial_indptr_vec);
    CopyToPageLockedBuffer(page_locked_int_buffer, plan_info.tasks[task].q_len_offset, q_len_vec);
    CopyToPageLockedBuffer(page_locked_int_buffer, plan_info.tasks[task].kv_len_offset, kv_len_vec);
    CopyToPageLockedBuffer(page_locked_int_buffer, plan_info.tasks[task].q_start_offset,
                           q_start_vec);
    CopyToPageLockedBuffer(page_locked_int_buffer, plan_info.tasks[task].kv_start_offset,
                           kv_start_vec);
    CopyToPageLockedBuffer(page_locked_int_buffer, plan_info.tasks[task].kv_end_offset, kv_end_vec);
    CopyToPageLockedBuffer(page_locked_int_buffer, plan_info.tasks[task].kv_head_idx_offset,
                           kv_head_idx_vec);
    CopyToPageLockedBuffer(page_locked_int_buffer, plan_info.tasks[task].work_indptr_offset,
                           work_indptr_vec);
  }
  plan_info.len_kv_chunk_offset =
      int_allocator.aligned_alloc_offset(sizeof(IdType) * NUM_TASKS, 16, "len_kv_chunk");
  CopyToPageLockedBuffer(page_locked_int_buffer, plan_info.len_kv_chunk_offset,
                         cluster_len_kv_chunk);

  if (merge_indptr.size() > max_num_kv_splits) {
    std::ostringstream err_msg;
    err_msg << "Number of kv splits " << merge_indptr.size() << " exceeds max buffer size "
            << max_num_kv_splits << ". Please increase the threshold.";
    FLASHINFER_ERROR(err_msg.str());
  }

  // update num_qo_len_vec
  num_expand_qo_len_vec.push_back(merge_indptr.size() - 1);
  // allocate buffer for state merge function
  plan_info.merge_indptr_offset =
      int_allocator.aligned_alloc_offset(sizeof(IdType) * max_num_kv_splits, 16, "merge_indptr");
  plan_info.merge_o_indices_offset =
      int_allocator.aligned_alloc_offset(sizeof(IdType) * max_num_kv_splits, 16, "merge_o_indices");
  plan_info.num_qo_len_offset =
      int_allocator.aligned_alloc_offset(sizeof(IdType), 16, "num_qo_len_offset");
  // copy data to paged cpu buffer
  CopyToPageLockedBuffer(page_locked_int_buffer, plan_info.merge_indptr_offset, merge_indptr);
  CopyToPageLockedBuffer(page_locked_int_buffer, plan_info.merge_o_indices_offset, merge_o_indices);
  CopyToPageLockedBuffer(page_locked_int_buffer, plan_info.num_qo_len_offset,
                         num_expand_qo_len_vec);

  size_t num_bytes_to_copy = int_allocator.num_allocated_bytes();
  FLASHINFER_CUDA_CALL(cudaMemcpyAsync(int_buffer, page_locked_int_buffer, num_bytes_to_copy,
                                       cudaMemcpyHostToDevice, stream));
  constexpr size_t sizeof_dtype_o = 2;  // NOTE (Yilong): assume fp16

  // Note(Yilong): times num_kv_heads as it is not counted in partial_o_nnz
  AlignedAllocator float_allocator(float_buffer, float_workspace_size_in_bytes);
  plan_info.partial_o_offset = float_allocator.aligned_alloc_offset(
      max_num_kv_splits * sizeof_dtype_o * head_dim * num_kv_heads, 16, "holistic_partial_o");
  plan_info.partial_lse_offset = float_allocator.aligned_alloc_offset(
      max_num_kv_splits * sizeof(float) * num_kv_heads, 16, "holistic_partial_lse");

  return cudaSuccess;
}

struct MLAPlanInfo {
  int64_t num_blks_x;
  int64_t num_blks_y;
  int64_t q_indptr_offset;
  int64_t kv_indptr_offset;
  int64_t partial_indptr_offset;
  int64_t merge_packed_offset_start_offset;
  int64_t merge_packed_offset_end_offset;
  int64_t merge_partial_packed_offset_start_offset;
  int64_t merge_partial_packed_offset_end_offset;
  int64_t merge_partial_stride_offset;
  int64_t q_len_offset;
  int64_t kv_len_offset;
  int64_t q_start_offset;
  int64_t kv_start_offset;
  int64_t kv_end_offset;
  int64_t work_indptr_offset;
  int64_t partial_o_offset;
  int64_t partial_lse_offset;

  std::vector<int64_t> ToVector() const {
    return {num_blks_x,
            num_blks_y,
            q_indptr_offset,
            kv_indptr_offset,
            partial_indptr_offset,
            merge_packed_offset_start_offset,
            merge_packed_offset_end_offset,
            merge_partial_packed_offset_start_offset,
            merge_partial_packed_offset_end_offset,
            merge_partial_stride_offset,
            q_len_offset,
            kv_len_offset,
            q_start_offset,
            kv_start_offset,
            kv_end_offset,
            work_indptr_offset,
            partial_o_offset,
            partial_lse_offset};
  }

  void FromVector(const std::vector<int64_t>& vec) {
    if (vec.size() != 18) {
      std::ostringstream err_msg;
      err_msg << "MLAPlanInfo::FromVector: vec.size() should be 18, but got " << vec.size();
      FLASHINFER_ERROR(err_msg.str());
    }
    num_blks_x = vec[0];
    num_blks_y = vec[1];
    q_indptr_offset = vec[2];
    kv_indptr_offset = vec[3];
    partial_indptr_offset = vec[4];
    merge_packed_offset_start_offset = vec[5];
    merge_packed_offset_end_offset = vec[6];
    merge_partial_packed_offset_start_offset = vec[7];
    merge_partial_packed_offset_end_offset = vec[8];
    merge_partial_stride_offset = vec[9];
    q_len_offset = vec[10];
    kv_len_offset = vec[11];
    q_start_offset = vec[12];
    kv_start_offset = vec[13];
    kv_end_offset = vec[14];
    work_indptr_offset = vec[15];
    partial_o_offset = vec[16];
    partial_lse_offset = vec[17];
  }
};

template <typename IdType>
inline cudaError_t MLAPlan(void* float_buffer, size_t float_workspace_size_in_bytes,
                           void* int_buffer, void* page_locked_int_buffer,
                           size_t int_workspace_size_in_bytes, MLAPlanInfo& plan_info,
                           IdType* qo_indptr_h, IdType* kv_indptr_h, IdType* kv_len_arr_h,
                           uint32_t batch_size, uint32_t num_heads, uint32_t head_dim_o,
                           bool causal, cudaStream_t stream) {
  int num_sm = 0;
  int dev_id = 0;
  FLASHINFER_CUDA_CALL(cudaGetDevice(&dev_id));
  FLASHINFER_CUDA_CALL(cudaDeviceGetAttribute(&num_sm, cudaDevAttrMultiProcessorCount, dev_id));

  // step 0. determine the number of blocks in x and y dimensions
  int accum_packed_qo_len = 0;
  std::vector<std::tuple<int, int, int>> idx_qo_kv_len_vec;
  for (uint32_t i = 0; i < batch_size; ++i) {
    if (qo_indptr_h[i + 1] - qo_indptr_h[i] < 0) {
      std::ostringstream err_msg;
      err_msg << "qo_indptr[" << i + 1 << "]" << qo_indptr_h[i + 1] << " - qo_indptr[" << i << "]"
              << qo_indptr_h[i] << " should be non-negative";
      FLASHINFER_ERROR(err_msg.str());
    }

    int qo_len = qo_indptr_h[i + 1] - qo_indptr_h[i];
    int packed_qo_len = qo_len * num_heads;
    accum_packed_qo_len += packed_qo_len;

    int kv_len = kv_len_arr_h[i];
    idx_qo_kv_len_vec.push_back({i, qo_len, kv_len});
  }
  int avg_packed_qo_len = accum_packed_qo_len / batch_size;

  int cluster_size;
  if (avg_packed_qo_len > 64) {
    cluster_size = 2;  // two ctas in a cluster
  } else {
    cluster_size = 1;  // one cta in a cluster
  }
  uint32_t num_clusters = num_sm / cluster_size;
  plan_info.num_blks_x = cluster_size;
  plan_info.num_blks_y = num_clusters;
  const int cta_tile_q = 64;
  int cluster_tile_q = cluster_size * cta_tile_q;

  int64_t total_kv_lens = 0;
  for (auto& [_, qo_len, kv_len] : idx_qo_kv_len_vec) {
    int packed_qo_len = qo_len * num_heads;
    int num_qo_tiles = ceil_div(packed_qo_len, cluster_tile_q);
    for (int qo_tile_idx = num_qo_tiles - 1; qo_tile_idx >= 0; --qo_tile_idx) {
      int effective_kv_len = causal ? packed_causal_kv_end(qo_len, kv_len, qo_tile_idx,
                                                           cluster_tile_q, num_qo_tiles, num_heads)
                                    : kv_len;
      total_kv_lens += effective_kv_len;
    }
  }

  auto f = [](int x) {
    if (x <= 8) {
      return 32;
    } else if (x <= 16) {
      return 64;
    } else if (x <= 32) {
      return 128;
    } else if (x <= 64) {
      return 192;
    }
    return ceil_div(x, 256) * 256;
  };

  int kv_len_limit = f(std::max(ceil_div(total_kv_lens, num_clusters), 1L));

  // step 1. load-balancing scheduling algorithm
  MinHeap cluster_cost_heap(num_clusters);
  std::vector<std::vector<IdType>> cluster_q_indptr(num_clusters, std::vector<IdType>()),
      cluster_kv_indptr(num_clusters, std::vector<IdType>()),
      cluster_q_len(num_clusters, std::vector<IdType>()),
      cluster_kv_len(num_clusters, std::vector<IdType>()),
      cluster_q_start(num_clusters, std::vector<IdType>()),
      cluster_kv_start(num_clusters, std::vector<IdType>()),
      cluster_kv_end(num_clusters, std::vector<IdType>()),
      cluster_partial_indptr(num_clusters, std::vector<IdType>());

  std::vector<IdType> merge_packed_offset_start(num_sm, 0), merge_packed_offset_end(num_sm, 0),
      merge_partial_packed_offset_start(num_sm, 0), merge_partial_packed_offset_end(num_sm, 0),
      merge_partial_stride(num_sm, 0);

  int merge_cta_counter = 0;
  int partial_o_nnz = 0;

  for (auto& [i, qo_len, kv_len] : idx_qo_kv_len_vec) {
    int packed_qo_len = qo_len * num_heads;
    int num_qo_tiles = ceil_div(packed_qo_len, cluster_tile_q);
    for (int qo_tile_idx = num_qo_tiles - 1; qo_tile_idx >= 0; --qo_tile_idx) {
      int remaining_len = causal ? packed_causal_kv_end(qo_len, kv_len, qo_tile_idx, cluster_tile_q,
                                                        num_qo_tiles, num_heads)
                                 : kv_len;
      int kv_start = 0;
      bool split_kv = remaining_len > kv_len_limit;
      int row_tile_size = std::min(cluster_tile_q, packed_qo_len - qo_tile_idx * cluster_tile_q);
      if (split_kv) {
        /*
         * Proof(Zihao): merge_cta_counter <= num_sm (num_sm == num_clusters * cluster_size)
         *
         * Precondition:
         * 1. kv_len_limit * num_clusters >= total_kv_lens == sum(remaining_len)
         * 2. num_qo_chunks <= max((remaining_len * cluster_size) // kv_len_limit, 1)
         * 3. num_qo_tiles_requires_split <= num_clusters

         * Implication:
         * 1. sum(num_qo_chunks) <= max(sum(remaining_len) * cluster_size / kv_len_limit,
         num_qo_tiles_requires_split)
         * 2. sum(num_qo_chunks) <= max(cluster_size * num_clusters, num_qo_tiles_requires_split)
         */
        int num_qo_chunks = std::max(remaining_len * cluster_size / kv_len_limit, 1);
        // row_chunk_size * num_qo_chunks >= row_tile_size
        int row_chunk_size = ceil_div(row_tile_size, num_qo_chunks);
        int current_q_tile_end =
            std::min(cluster_tile_q, packed_qo_len - qo_tile_idx * cluster_tile_q);
        for (int offset_start = 0; offset_start < row_tile_size; offset_start += row_chunk_size) {
          merge_packed_offset_start[merge_cta_counter] =
              qo_indptr_h[i] * num_heads + qo_tile_idx * cluster_tile_q + offset_start;
          merge_packed_offset_end[merge_cta_counter] =
              qo_indptr_h[i] * num_heads + qo_tile_idx * cluster_tile_q +
              std::min(offset_start + row_chunk_size, current_q_tile_end);
          merge_partial_packed_offset_start[merge_cta_counter] = partial_o_nnz + offset_start;
          merge_partial_packed_offset_end[merge_cta_counter] =
              partial_o_nnz + ceil_div(remaining_len, kv_len_limit) * row_tile_size;
          merge_partial_stride[merge_cta_counter] = row_tile_size;
          merge_cta_counter++;
        }
      }
      bool zero_kv_len = (remaining_len == 0);
      while (remaining_len > 0 || zero_kv_len) {
        auto [cluster_idx, accum_cost] = cluster_cost_heap.pop();
        int actual_len = std::min(remaining_len, kv_len_limit);
        cluster_cost_heap.insert(
            {cluster_idx, accum_cost + cost_function(cluster_tile_q, actual_len)});
        cluster_q_len[cluster_idx].push_back(qo_len);
        cluster_kv_len[cluster_idx].push_back(kv_len);
        cluster_q_indptr[cluster_idx].push_back(qo_indptr_h[i]);
        cluster_kv_indptr[cluster_idx].push_back(kv_indptr_h[i]);
        if (split_kv) {
          cluster_partial_indptr[cluster_idx].push_back(partial_o_nnz);
          partial_o_nnz += row_tile_size;
        } else {
          cluster_partial_indptr[cluster_idx].push_back(-1);
        }
        cluster_q_start[cluster_idx].push_back(qo_tile_idx * cluster_tile_q);
        cluster_kv_start[cluster_idx].push_back(kv_start);
        cluster_kv_end[cluster_idx].push_back(kv_start + actual_len);
        remaining_len -= actual_len;
        kv_start += actual_len;
        if (zero_kv_len) break;
      }
    }
  }

  FLASHINFER_CHECK(merge_cta_counter <= num_sm,
                   "Internal Error: merge_cta_counter should be less than or equal to num_sm, "
                   "please report this bug to the developers");

  int max_total_num_works = 16384;  // NOTE(Zihao): adjust it later

  std::vector<IdType> work_indptr_vec(num_clusters + 1, 0);
  for (uint32_t i = 0; i < num_clusters; ++i) {
    work_indptr_vec[i + 1] = work_indptr_vec[i] + cluster_q_indptr[i].size();
  }
  int total_num_works = work_indptr_vec.back();
  auto q_indptr_vec = flatten(cluster_q_indptr, total_num_works);
  auto kv_indptr_vec = flatten(cluster_kv_indptr, total_num_works);
  auto partial_indptr_vec = flatten(cluster_partial_indptr, total_num_works);
  auto q_len_vec = flatten(cluster_q_len, total_num_works);
  auto kv_len_vec = flatten(cluster_kv_len, total_num_works);
  auto q_start_vec = flatten(cluster_q_start, total_num_works);
  auto kv_start_vec = flatten(cluster_kv_start, total_num_works);
  auto kv_end_vec = flatten(cluster_kv_end, total_num_works);

  AlignedAllocator int_allocator(int_buffer, int_workspace_size_in_bytes);
  plan_info.q_indptr_offset =
      int_allocator.aligned_alloc_offset(sizeof(IdType) * max_total_num_works, 16, "mla_q_indptr");
  plan_info.kv_indptr_offset =
      int_allocator.aligned_alloc_offset(sizeof(IdType) * max_total_num_works, 16, "mla_kv_indptr");
  plan_info.partial_indptr_offset = int_allocator.aligned_alloc_offset(
      sizeof(IdType) * max_total_num_works, 16, "mla_partial_indptr");
  plan_info.merge_packed_offset_start_offset = int_allocator.aligned_alloc_offset(
      sizeof(IdType) * num_sm, 16, "mla_merge_packed_offset_start");
  plan_info.merge_packed_offset_end_offset = int_allocator.aligned_alloc_offset(
      sizeof(IdType) * num_sm, 16, "mla_merge_packed_offset_end");
  plan_info.merge_partial_packed_offset_start_offset = int_allocator.aligned_alloc_offset(
      sizeof(IdType) * num_sm, 16, "mla_merge_partial_packed_offset_start");
  plan_info.merge_partial_packed_offset_end_offset = int_allocator.aligned_alloc_offset(
      sizeof(IdType) * num_sm, 16, "mla_merge_partial_packed_offset_end");
  plan_info.merge_partial_stride_offset =
      int_allocator.aligned_alloc_offset(sizeof(IdType) * num_sm, 16, "mla_merge_partial_stride");
  plan_info.q_len_offset =
      int_allocator.aligned_alloc_offset(sizeof(IdType) * max_total_num_works, 16, "mla_q_len");
  plan_info.kv_len_offset =
      int_allocator.aligned_alloc_offset(sizeof(IdType) * max_total_num_works, 16, "mla_kv_len");
  plan_info.q_start_offset =
      int_allocator.aligned_alloc_offset(sizeof(IdType) * max_total_num_works, 16, "mla_q_start");
  plan_info.kv_start_offset =
      int_allocator.aligned_alloc_offset(sizeof(IdType) * max_total_num_works, 16, "mla_kv_start");
  plan_info.kv_end_offset =
      int_allocator.aligned_alloc_offset(sizeof(IdType) * max_total_num_works, 16, "mla_kv_end");
  plan_info.work_indptr_offset = int_allocator.aligned_alloc_offset(
      sizeof(IdType) * max_total_num_works, 16, "mla_work_indptr");

  IdType* cluster_q_indptr_h =
      GetPtrFromBaseOffset<IdType>(page_locked_int_buffer, plan_info.q_indptr_offset);
  IdType* cluster_kv_indptr_h =
      GetPtrFromBaseOffset<IdType>(page_locked_int_buffer, plan_info.kv_indptr_offset);
  IdType* cluster_partial_indptr_h =
      GetPtrFromBaseOffset<IdType>(page_locked_int_buffer, plan_info.partial_indptr_offset);
  IdType* cluster_merge_packed_offset_start_h = GetPtrFromBaseOffset<IdType>(
      page_locked_int_buffer, plan_info.merge_packed_offset_start_offset);
  IdType* cluster_merge_packed_offset_end_h = GetPtrFromBaseOffset<IdType>(
      page_locked_int_buffer, plan_info.merge_packed_offset_end_offset);
  IdType* cluster_merge_partial_packed_offset_start_h = GetPtrFromBaseOffset<IdType>(
      page_locked_int_buffer, plan_info.merge_partial_packed_offset_start_offset);
  IdType* cluster_merge_partial_packed_offset_end_h = GetPtrFromBaseOffset<IdType>(
      page_locked_int_buffer, plan_info.merge_partial_packed_offset_end_offset);
  IdType* cluster_merge_partial_stride_h =
      GetPtrFromBaseOffset<IdType>(page_locked_int_buffer, plan_info.merge_partial_stride_offset);
  IdType* cluster_q_len_h =
      GetPtrFromBaseOffset<IdType>(page_locked_int_buffer, plan_info.q_len_offset);
  IdType* cluster_kv_len_h =
      GetPtrFromBaseOffset<IdType>(page_locked_int_buffer, plan_info.kv_len_offset);
  IdType* cluster_q_start_h =
      GetPtrFromBaseOffset<IdType>(page_locked_int_buffer, plan_info.q_start_offset);
  IdType* cluster_kv_start_h =
      GetPtrFromBaseOffset<IdType>(page_locked_int_buffer, plan_info.kv_start_offset);
  IdType* cluster_kv_end_h =
      GetPtrFromBaseOffset<IdType>(page_locked_int_buffer, plan_info.kv_end_offset);
  IdType* cluster_work_indptr_h =
      GetPtrFromBaseOffset<IdType>(page_locked_int_buffer, plan_info.work_indptr_offset);

  std::copy(q_indptr_vec.begin(), q_indptr_vec.end(), cluster_q_indptr_h);
  std::copy(kv_indptr_vec.begin(), kv_indptr_vec.end(), cluster_kv_indptr_h);
  std::copy(partial_indptr_vec.begin(), partial_indptr_vec.end(), cluster_partial_indptr_h);
  std::copy(merge_packed_offset_start.begin(), merge_packed_offset_start.end(),
            cluster_merge_packed_offset_start_h);
  std::copy(merge_packed_offset_end.begin(), merge_packed_offset_end.end(),
            cluster_merge_packed_offset_end_h);
  std::copy(merge_partial_packed_offset_start.begin(), merge_partial_packed_offset_start.end(),
            cluster_merge_partial_packed_offset_start_h);
  std::copy(merge_partial_packed_offset_end.begin(), merge_partial_packed_offset_end.end(),
            cluster_merge_partial_packed_offset_end_h);
  std::copy(merge_partial_stride.begin(), merge_partial_stride.end(),
            cluster_merge_partial_stride_h);
  std::copy(q_len_vec.begin(), q_len_vec.end(), cluster_q_len_h);
  std::copy(kv_len_vec.begin(), kv_len_vec.end(), cluster_kv_len_h);
  std::copy(q_start_vec.begin(), q_start_vec.end(), cluster_q_start_h);
  std::copy(kv_start_vec.begin(), kv_start_vec.end(), cluster_kv_start_h);
  std::copy(kv_end_vec.begin(), kv_end_vec.end(), cluster_kv_end_h);
  std::copy(work_indptr_vec.begin(), work_indptr_vec.end(), cluster_work_indptr_h);

  size_t num_bytes_to_copy = int_allocator.num_allocated_bytes();
  FLASHINFER_CUDA_CALL(cudaMemcpyAsync(int_buffer, page_locked_int_buffer, num_bytes_to_copy,
                                       cudaMemcpyHostToDevice, stream));

  constexpr size_t sizeof_dtype_o = 2;
  AlignedAllocator float_allocator(float_buffer, float_workspace_size_in_bytes);
  plan_info.partial_o_offset = float_allocator.aligned_alloc_offset(
      2 * num_clusters * cluster_tile_q * sizeof_dtype_o * head_dim_o, 16, "mla_partial_o");
  plan_info.partial_lse_offset = float_allocator.aligned_alloc_offset(
      2 * num_clusters * cluster_tile_q * sizeof(float), 16, "mla_partial_lse");

  return cudaSuccess;
}

}  // namespace flashinfer
#endif  // FLASHINFER_ATTENTION_SCHEDULER_CUH_
