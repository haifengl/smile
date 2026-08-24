/*
 * Copyright (c) 2025 by FlashInfer team.
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
#ifndef FLASHINFER_PERSISTENT_CUH_
#define FLASHINFER_PERSISTENT_CUH_

#include "../cp_async.cuh"
#include "../math.cuh"
#include "../utils.cuh"
#include "mask.cuh"
#include "persistent_template.cuh"
#include "prefill.cuh"
#include "state.cuh"

namespace flashinfer {

using cp_async::PrefetchMode;
using cp_async::SharedMemFillMode;

template <typename Params>
__device__ __forceinline__ auto get_block_coord(const Params& params, const uint32_t work_idx) {
  return std::tuple(params.q_indptr[work_idx], params.kv_indptr[work_idx],
                    params.partial_indptr[work_idx], params.q_len[work_idx],
                    params.kv_len[work_idx], params.q_start[work_idx], params.kv_start[work_idx],
                    params.kv_end[work_idx], params.kv_head_idx_arr[work_idx],
                    *params.len_kv_chunk);
}

template <typename KTraits>
__device__ __forceinline__ void prefetch_offest(
    const uint32_t packed_block_iter_base, const uint32_t packed_kv_bound,
    const uint32_t kv_head_idx, const uint32_t kv_stride_page, const uint32_t kv_stride_h,
    const uint32_t kv_stride_n, const uint_fastdiv& block_size, typename KTraits::IdType* indices,
    size_t* kv_offset) {
  using DTypeKV = typename KTraits::DTypeKV;
  constexpr uint32_t KV_THR_LAYOUT_ROW = KTraits::KV_THR_LAYOUT_ROW;
  constexpr uint32_t KV_THR_LAYOUT_COL = KTraits::KV_THR_LAYOUT_COL;
  constexpr uint32_t NUM_WARPS_Q = KTraits::NUM_WARPS_Q;
  constexpr uint32_t NUM_WARPS_KV = KTraits::NUM_WARPS_KV;
  constexpr uint32_t NUM_MMA_KV = KTraits::NUM_MMA_KV;
  constexpr SwizzleMode SWIZZLE_MODE_KV = KTraits::SWIZZLE_MODE_KV;
  const uint32_t lane_idx = threadIdx.x % 32, warp_idx = threadIdx.x / 32;

#pragma unroll
  for (uint32_t i = 0;
       i < NUM_MMA_KV * (SWIZZLE_MODE_KV == SwizzleMode::k128B ? 4 : 2) / NUM_WARPS_Q; ++i) {
    uint32_t page_iter, entry_idx;
    uint32_t packed_block_iter = packed_block_iter_base + warp_idx * KV_THR_LAYOUT_ROW +
                                 lane_idx / KV_THR_LAYOUT_COL +
                                 KV_THR_LAYOUT_ROW * NUM_WARPS_Q * NUM_WARPS_KV * i;
    block_size.divmod(packed_block_iter, page_iter, entry_idx);
    // FP4: GMEM is packed (2 FP4/byte), so the column byte offset is halved relative to fp8
    constexpr uint32_t fp4_pack_factor = is_fp4_type_v<DTypeKV> ? 2 : 1;
    kv_offset[i] = (packed_block_iter < packed_kv_bound ? indices[page_iter] : 0) * kv_stride_page +
                   entry_idx * kv_stride_n + kv_head_idx * kv_stride_h +
                   (lane_idx % KV_THR_LAYOUT_COL) * upcast_size<DTypeKV>() / fp4_pack_factor;
  }
}

template <typename KTraits>
__device__ __forceinline__ void write_o_(float (*o_frag)[KTraits::NUM_MMA_D_VO][8],
                                         smem_t<KTraits::SWIZZLE_MODE_Q>* o_smem,
                                         typename KTraits::DTypeO* o_ptr_base,
                                         const uint32_t o_packed_idx_base_warp,
                                         const uint32_t o_packed_idx_base_cta,
                                         const uint32_t qo_upper_bound, const uint32_t o_stride_n,
                                         const uint_fastdiv group_size, const uint32_t warp_idx,
                                         const uint32_t lane_idx, const dim3 tid) {
  using DTypeO = typename KTraits::DTypeO;
  constexpr uint32_t UPCAST_STRIDE_O = KTraits::UPCAST_STRIDE_O;
  const uint32_t warp_idx_x = get_warp_idx_q<KTraits>(tid.y),
                 warp_idx_z = get_warp_idx_kv<KTraits>(tid.z);

  static_assert(sizeof(DTypeO) == 2);
  if (warp_idx_z == 0) {
#pragma unroll
    for (uint32_t mma_q = 0; mma_q < KTraits::NUM_MMA_Q; ++mma_q) {
#pragma unroll
      for (uint32_t mma_d = 0; mma_d < KTraits::NUM_MMA_D_VO; ++mma_d) {
        uint32_t o_frag_f16[8 / 2];
        vec_cast<DTypeO, float>::cast<8>((DTypeO*)o_frag_f16, o_frag[mma_q][mma_d]);

#ifdef FLASHINFER_STMATRIX_M8N8X4_ENABLED
        uint32_t o_smem_offset_w = o_smem->get_permuted_offset<UPCAST_STRIDE_O>(
            (warp_idx_x * KTraits::NUM_MMA_Q + mma_q) * 16 + lane_idx % 16,
            mma_d * 2 + lane_idx / 16);
        o_smem->stmatrix_m8n8x4(o_smem_offset_w, o_frag_f16);
#else
        uint32_t o_smem_offset_w = o_smem->get_permuted_offset<UPCAST_STRIDE_O>(
            (warp_idx_x * KTraits::NUM_MMA_Q + mma_q) * 16 + lane_idx / 4, mma_d * 2);
        ((uint32_t*)(o_smem->base + o_smem_offset_w))[lane_idx % 4] = o_frag_f16[0];
        ((uint32_t*)(o_smem->base + o_smem_offset_w + 8 * UPCAST_STRIDE_O))[lane_idx % 4] =
            o_frag_f16[1];
        ((uint32_t*)(o_smem->base + (o_smem_offset_w ^ 0x1)))[lane_idx % 4] = o_frag_f16[2];
        ((uint32_t*)(o_smem->base + (o_smem_offset_w ^ 0x1) + 8 * UPCAST_STRIDE_O))[lane_idx % 4] =
            o_frag_f16[3];
#endif
      }
    }

    uint32_t o_smem_offset_w = o_smem->get_permuted_offset<UPCAST_STRIDE_O>(
        warp_idx_x * KTraits::NUM_MMA_Q * 16 + lane_idx / 8, lane_idx % 8);

#pragma unroll
    for (uint32_t mma_q = 0; mma_q < KTraits::NUM_MMA_Q; ++mma_q) {
#pragma unroll
      for (uint32_t j = 0; j < 2 * 2; ++j) {
        uint32_t q, r;
        const uint32_t o_packed_idx = o_packed_idx_base_warp + lane_idx / 8 + mma_q * 16 + j * 4;
        group_size.divmod(o_packed_idx, q, r);

        const uint32_t o_idx = q;
        DTypeO* o_ptr = o_ptr_base + (o_packed_idx - o_packed_idx_base_cta) * o_stride_n +
                        (lane_idx % 8) * upcast_size<DTypeO>();
#pragma unroll
        for (uint32_t mma_do = 0; mma_do < KTraits::NUM_MMA_D_VO / 4; ++mma_do) {
          if (o_idx < qo_upper_bound) {
            o_smem->store_128b(o_smem_offset_w, o_ptr);
          }
          o_ptr += 8 * upcast_size<DTypeO>();
          o_smem_offset_w = o_smem->template advance_offset_by_column<8>(o_smem_offset_w, mma_do);
        }
        o_smem_offset_w =
            o_smem->template advance_offset_by_row<4, UPCAST_STRIDE_O>(o_smem_offset_w) -
            2 * KTraits::NUM_MMA_D_VO;
      }
    }
  }
}

template <typename KTraits>
__device__ __forceinline__ void normalize_d(float (*o_frag)[KTraits::NUM_MMA_D_VO][8],
                                            typename KTraits::DTypeQKAccum (*m)[2], float (*d)[2],
                                            float v_scale = 1.0f) {
  using AttentionVariant = typename KTraits::AttentionVariant;
  if constexpr (AttentionVariant::use_softmax) {
    float d_rcp[KTraits::NUM_MMA_Q][2];
    // compute reciprocal of d
#pragma unroll
    for (uint32_t mma_q = 0; mma_q < KTraits::NUM_MMA_Q; ++mma_q) {
#pragma unroll
      for (uint32_t j = 0; j < 2; ++j) {
        d_rcp[mma_q][j] = (m[mma_q][j] != typename KTraits::DTypeQKAccum(-math::inf))
                              ? math::ptx_rcp(d[mma_q][j])
                              : 0.f;
      }
    }

#pragma unroll
    for (uint32_t mma_q = 0; mma_q < KTraits::NUM_MMA_Q; ++mma_q) {
#pragma unroll
      for (uint32_t mma_d = 0; mma_d < KTraits::NUM_MMA_D_VO; ++mma_d) {
#pragma unroll
        for (uint32_t reg_id = 0; reg_id < 8; ++reg_id) {
          o_frag[mma_q][mma_d][reg_id] =
              o_frag[mma_q][mma_d][reg_id] * d_rcp[mma_q][(reg_id >> 1) & 1];
          if (v_scale != 1.0f) {
            o_frag[mma_q][mma_d][reg_id] *= v_scale;
          }
        }
      }
    }
  }
}

template <typename KTraits_, typename Params_>
struct BlockBatchPagedAttentionPersistent {
  using KTraits = KTraits_;
  using Params = Params_;

  static __device__ __forceinline__ void Run(const Params& params,
                                             typename KTraits::SharedStorage* smem_storage
                                                 PROFILER_CLOSURE_FUNC_PARAMS) {
    using DTypeQ = typename Params::DTypeQ;
    using DTypeKV = typename Params::DTypeKV;
    using DTypeO = typename Params::DTypeO;
    using IdType = typename Params::IdType;
    using DTypeQKAccum = typename KTraits::DTypeQKAccum;
    using AttentionVariant = typename KTraits::AttentionVariant;
    [[maybe_unused]] constexpr uint32_t NUM_MMA_Q = KTraits::NUM_MMA_Q;
    [[maybe_unused]] constexpr uint32_t NUM_MMA_KV = KTraits::NUM_MMA_KV;
    [[maybe_unused]] constexpr uint32_t NUM_MMA_D_QK = KTraits::NUM_MMA_D_QK;
    [[maybe_unused]] constexpr uint32_t NUM_MMA_D_VO = KTraits::NUM_MMA_D_VO;
    [[maybe_unused]] constexpr uint32_t HEAD_DIM_QK = KTraits::HEAD_DIM_QK;
    [[maybe_unused]] constexpr uint32_t HEAD_DIM_VO = KTraits::HEAD_DIM_VO;
    [[maybe_unused]] constexpr uint32_t UPCAST_STRIDE_Q = KTraits::UPCAST_STRIDE_Q;
    [[maybe_unused]] constexpr uint32_t UPCAST_STRIDE_K = KTraits::UPCAST_STRIDE_K;
    [[maybe_unused]] constexpr uint32_t UPCAST_STRIDE_V = KTraits::UPCAST_STRIDE_V;
    [[maybe_unused]] constexpr uint32_t UPCAST_STRIDE_O = KTraits::UPCAST_STRIDE_O;
    [[maybe_unused]] constexpr uint32_t NUM_WARPS_Q = KTraits::NUM_WARPS_Q;
    [[maybe_unused]] constexpr uint32_t NUM_WARPS_KV = KTraits::NUM_WARPS_KV;
    [[maybe_unused]] constexpr SwizzleMode SWIZZLE_MODE_Q = KTraits::SWIZZLE_MODE_Q;
    [[maybe_unused]] constexpr SwizzleMode SWIZZLE_MODE_KV = KTraits::SWIZZLE_MODE_KV;
    [[maybe_unused]] constexpr uint32_t CTA_TILE_Q = KTraits::CTA_TILE_Q;
    [[maybe_unused]] constexpr uint32_t CTA_TILE_KV = KTraits::CTA_TILE_KV;
    [[maybe_unused]] constexpr bool CAUSAL = KTraits::MASK_MODE == MaskMode::kCausal;
    [[maybe_unused]] constexpr uint32_t NUM_STAGES = KTraits::NUM_STAGES;

    DTypeQ* q = params.q;
    DTypeKV* k = params.k;
    DTypeKV* v = params.v;
    IdType* kv_indices = params.kv_indices;
    uint8_t* maybe_k_cache_sf = nullptr;
    if constexpr (has_maybe_k_cache_sf_v<Params>) {
      maybe_k_cache_sf = params.maybe_k_cache_sf;
    }
    uint8_t* maybe_v_cache_sf = nullptr;
    if constexpr (has_maybe_v_cache_sf_v<Params>) {
      maybe_v_cache_sf = params.maybe_v_cache_sf;
    }
    float* partial_lse = params.partial_lse;
    IdType* work_indptr = params.work_indptr;

    float s_frag[NUM_MMA_Q][NUM_MMA_KV][8];
    alignas(16) float o_frag[NUM_MMA_Q][NUM_MMA_D_VO][8];
    float m[NUM_MMA_Q][2];
    float d[NUM_MMA_Q][2];

    const uint_fastdiv& gqa_group_size = params.gqa_group_size;
    const uint32_t num_kv_heads = params.num_kv_heads;
    const uint_fastdiv& block_size = params.page_size;
    const uint32_t q_stride_n = params.q_stride_n;
    const uint32_t q_stride_h = params.q_stride_h;
    const uint32_t k_stride_page = params.k_stride_page;
    const uint32_t k_stride_h = params.k_stride_h;
    const uint32_t k_stride_n = params.k_stride_n;
    const uint32_t v_stride_page = params.v_stride_page;
    const uint32_t v_stride_h = params.v_stride_h;
    const uint32_t v_stride_n = params.v_stride_n;
    const uint32_t cluster_tile_q = gridDim.x * CTA_TILE_Q;
    smem_t<SWIZZLE_MODE_Q> q_smem(smem_storage->q_smem);

    AttentionVariant variant(params, /*batch_idx=*/0, nullptr);

    const uint32_t lane_idx = threadIdx.x % 32;
    const uint32_t warp_idx = threadIdx.x / 32;

    // threadIdx: [32, NUM_WARPS_Q, NUM_WARPS_KV]
    // remap to utilize tool function in FA2 prefill
    const dim3 tid = dim3(lane_idx, warp_idx % NUM_WARPS_Q, warp_idx / NUM_WARPS_Q);

    uint32_t q_smem_offset_r = get_permuted_offset<SWIZZLE_MODE_Q, UPCAST_STRIDE_Q>(
        get_warp_idx_q<KTraits>(tid.y) * NUM_MMA_Q * 16 + lane_idx % 16, lane_idx / 16);
    uint32_t k_smem_offset_r = get_permuted_offset<SWIZZLE_MODE_KV, UPCAST_STRIDE_K>(
                 get_warp_idx_kv<KTraits>(tid.z) * NUM_MMA_KV * 16 + 8 * (lane_idx / 16) +
                     lane_idx % 8,
                 (lane_idx % 16) / 8),
             v_smem_offset_r = get_permuted_offset<SWIZZLE_MODE_KV, UPCAST_STRIDE_V>(
                 get_warp_idx_kv<KTraits>(tid.z) * NUM_MMA_KV * 16 + lane_idx % 16, lane_idx / 16);
    uint32_t k_smem_offset_w = get_permuted_offset<SWIZZLE_MODE_KV, UPCAST_STRIDE_K>(
                 warp_idx * KTraits::KV_THR_LAYOUT_ROW + lane_idx / KTraits::KV_THR_LAYOUT_COL,
                 lane_idx % KTraits::KV_THR_LAYOUT_COL),
             v_smem_offset_w = get_permuted_offset<SWIZZLE_MODE_KV, UPCAST_STRIDE_V>(
                 warp_idx * KTraits::KV_THR_LAYOUT_ROW + lane_idx / KTraits::KV_THR_LAYOUT_COL,
                 lane_idx % KTraits::KV_THR_LAYOUT_COL);
    size_t
        thr_local_kv_offset_k[NUM_MMA_KV * KTraits::KV_THR_LAYOUT_COL / 2 / KTraits::NUM_WARPS_Q];
    size_t
        thr_local_kv_offset_v[NUM_MMA_KV * KTraits::KV_THR_LAYOUT_COL / 2 / KTraits::NUM_WARPS_Q];

#pragma unroll 1
    for (IdType work_idx = work_indptr[blockIdx.y]; work_idx < work_indptr[blockIdx.y + 1];
         ++work_idx) {
      // profile log
      if constexpr (CTA_TILE_Q > 64) {
        PROFILER_EVENT_START(profiler_closure, PersistentProfileEventType::kRunner1);
      } else {
        PROFILER_EVENT_START(profiler_closure, PersistentProfileEventType::kRunner2);
      }

      const auto [q_indptr, kv_indptr, o_indptr, q_len, kv_len, packed_qo_start, kv_start, kv_end,
                  kv_head_idx, len_kv_chunk] = get_block_coord(params, work_idx);

      const uint32_t kv_chunk_idx = kv_start / len_kv_chunk;
      const uint32_t num_kv_chunks = ceil_div(
          CAUSAL
              ? min((kv_len - q_len) + ceil_div(packed_qo_start + cluster_tile_q, gqa_group_size),
                    kv_len)
              : kv_len,
          len_kv_chunk);
      const uint32_t qo_packed_idx_base = packed_qo_start + blockIdx.x * CTA_TILE_Q +
                                          get_warp_idx_q<KTraits>(tid.y) * NUM_MMA_Q * 16;
      const uint32_t qo_upperbound =
          min(q_len, ceil_div(qo_packed_idx_base + CTA_TILE_Q, gqa_group_size));

      init_states<KTraits>(variant, o_frag, m, d);

      DTypeQ* q_ptr_base = q + q_indptr * q_stride_n + (kv_head_idx * gqa_group_size) * q_stride_h;

      // load_q
      load_q_global_smem<KTraits>(qo_packed_idx_base, qo_upperbound, q_ptr_base, q_stride_n,
                                  q_stride_h, gqa_group_size, &q_smem, tid);

      smem_t<SWIZZLE_MODE_KV> k_smem(smem_storage->k_smem), v_smem(smem_storage->v_smem);
      int kv_tile_idx =
          ceil_div((CAUSAL ? min(kv_end,
                                 kv_len - q_len +
                                     ceil_div((packed_qo_start + cluster_tile_q), gqa_group_size))
                           : kv_end),
                   CTA_TILE_KV) -
          1 - (kv_start / CTA_TILE_KV);

      int mask_tile_idx =
          (CAUSAL ? min(kv_end, kv_len - q_len + ceil_div(packed_qo_start, gqa_group_size))
                  : kv_end) /
              CTA_TILE_KV -
          (kv_start / CTA_TILE_KV);

      uint32_t block_iter_base = kv_indptr * block_size + kv_start;
      // last kv tile
      __syncthreads();
      uint32_t packed_kv_bound = kv_indptr * block_size + kv_len;

      prefetch_offest<KTraits>(block_iter_base + kv_tile_idx * CTA_TILE_KV, packed_kv_bound,
                               kv_head_idx, k_stride_page, k_stride_h, k_stride_n, block_size,
                               kv_indices, thr_local_kv_offset_k);
      prefetch_offest<KTraits>(block_iter_base + kv_tile_idx * CTA_TILE_KV, packed_kv_bound,
                               kv_head_idx, v_stride_page, v_stride_h, v_stride_n, block_size,
                               kv_indices, thr_local_kv_offset_v);
      page_produce_kv<false, KTraits>(smem_storage, &k_smem_offset_w, k,
                                      kv_start + kv_tile_idx * CTA_TILE_KV, thr_local_kv_offset_k,
                                      kv_end, warp_idx, lane_idx);
      page_produce_kv_sf<false, KTraits>(
          smem_storage, maybe_k_cache_sf, block_iter_base + kv_tile_idx * CTA_TILE_KV,
          packed_kv_bound, kv_head_idx, params.k_sf_stride_page, params.k_sf_stride_h,
          params.k_sf_stride_n, block_size, kv_indices, kv_start + kv_tile_idx * CTA_TILE_KV,
          kv_end, warp_idx, lane_idx);
      cp_async::commit_group();
      page_produce_kv<true, KTraits>(smem_storage, &v_smem_offset_w, v,
                                     kv_start + kv_tile_idx * CTA_TILE_KV, thr_local_kv_offset_v,
                                     kv_end, warp_idx, lane_idx);
      page_produce_kv_sf<true, KTraits>(
          smem_storage, maybe_v_cache_sf, block_iter_base + kv_tile_idx * CTA_TILE_KV,
          packed_kv_bound, kv_head_idx, params.v_sf_stride_page, params.v_sf_stride_h,
          params.v_sf_stride_n, block_size, kv_indices, kv_start + kv_tile_idx * CTA_TILE_KV,
          kv_end, warp_idx, lane_idx);
      cp_async::commit_group();

      // loop with mask
      LOOP_SPLIT_MASK(
          kv_tile_idx, kv_tile_idx >= mask_tile_idx && kv_tile_idx > 0,
          kv_tile_idx + 1 > NUM_STAGES, {
            prefetch_offest<KTraits>(block_iter_base + (kv_tile_idx - 1) * CTA_TILE_KV,
                                     packed_kv_bound, kv_head_idx, k_stride_page, k_stride_h,
                                     k_stride_n, block_size, kv_indices, thr_local_kv_offset_k);
            prefetch_offest<KTraits>(block_iter_base + (kv_tile_idx - 1) * CTA_TILE_KV,
                                     packed_kv_bound, kv_head_idx, v_stride_page, v_stride_h,
                                     v_stride_n, block_size, kv_indices, thr_local_kv_offset_v);
            cp_async::wait_group<1>();
            __syncthreads();

            compute_qk<KTraits>(
                &q_smem, &q_smem_offset_r, &k_smem, &k_smem_offset_r,
                smem_storage->k_sf_smem_ptr(get_warp_idx_kv<KTraits>(tid.z) * KTraits::NUM_MMA_KV *
                                            16 * KTraits::NUM_MMA_D_QK),
                lane_idx, s_frag);
            if constexpr (AttentionVariant::use_logits_soft_cap) {
              logits_transform<KTraits>(
                  params, variant, /*batch_idx=*/0, qo_packed_idx_base,
                  kv_start + (kv_tile_idx * NUM_WARPS_KV + get_warp_idx_kv<KTraits>(tid.z)) *
                                 NUM_MMA_KV * 16,
                  q_len, kv_len, kv_end, gqa_group_size, s_frag, tid, kv_head_idx);
            }
            if constexpr (WITH_MASK) {
              logits_mask<KTraits>(
                  params, variant, /*batch_idx=*/0, qo_packed_idx_base,
                  kv_start + (kv_tile_idx * NUM_WARPS_KV + get_warp_idx_kv<KTraits>(tid.z)) *
                                 NUM_MMA_KV * 16,
                  q_len, kv_len, kv_end, gqa_group_size, s_frag, tid, kv_head_idx);
            }
            update_mdo_states<KTraits>(variant, s_frag, o_frag, m, d);

            __syncthreads();
            page_produce_kv<false, KTraits>(smem_storage, &k_smem_offset_w, k,
                                            kv_start + (kv_tile_idx - 1) * CTA_TILE_KV,
                                            thr_local_kv_offset_k, kv_end, warp_idx, lane_idx);
            page_produce_kv_sf<false, KTraits>(
                smem_storage, maybe_k_cache_sf, block_iter_base + (kv_tile_idx - 1) * CTA_TILE_KV,
                packed_kv_bound, kv_head_idx, params.k_sf_stride_page, params.k_sf_stride_h,
                params.k_sf_stride_n, block_size, kv_indices,
                kv_start + (kv_tile_idx - 1) * CTA_TILE_KV, kv_end, warp_idx, lane_idx);
            cp_async::commit_group();
            cp_async::wait_group<1>();

            __syncthreads();
            compute_sfm_v<KTraits>(
                &v_smem, &v_smem_offset_r,
                smem_storage->v_sf_smem_ptr(get_warp_idx_kv<KTraits>(tid.z) * KTraits::NUM_MMA_KV *
                                            16 * KTraits::NUM_MMA_D_VO),
                lane_idx, s_frag, o_frag, d);
            __syncthreads();

            page_produce_kv<true, KTraits>(smem_storage, &v_smem_offset_w, v,
                                           kv_start + (kv_tile_idx - 1) * CTA_TILE_KV,
                                           thr_local_kv_offset_v, kv_end, warp_idx, lane_idx);
            page_produce_kv_sf<true, KTraits>(
                smem_storage, maybe_v_cache_sf, block_iter_base + (kv_tile_idx - 1) * CTA_TILE_KV,
                packed_kv_bound, kv_head_idx, params.v_sf_stride_page, params.v_sf_stride_h,
                params.v_sf_stride_n, block_size, kv_indices,
                kv_start + (kv_tile_idx - 1) * CTA_TILE_KV, kv_end, warp_idx, lane_idx);
            cp_async::commit_group();
          });
      cp_async::wait_group<0>();
      __syncthreads();

#pragma unroll
      for (; kv_tile_idx >= 0; --kv_tile_idx) {
        compute_qk<KTraits>(
            &q_smem, &q_smem_offset_r, &k_smem, &k_smem_offset_r,
            smem_storage->k_sf_smem_ptr(get_warp_idx_kv<KTraits>(tid.z) * KTraits::NUM_MMA_KV * 16 *
                                        KTraits::NUM_MMA_D_QK),
            lane_idx, s_frag);
        if constexpr (AttentionVariant::use_logits_soft_cap) {
          logits_transform<KTraits>(
              params, variant, /*batch_idx=*/0, qo_packed_idx_base,
              kv_start +
                  (kv_tile_idx * NUM_WARPS_KV + get_warp_idx_kv<KTraits>(tid.z)) * NUM_MMA_KV * 16,
              q_len, kv_len, kv_end, gqa_group_size, s_frag, tid, kv_head_idx);
        }
        logits_mask<KTraits>(
            params, variant, /*batch_idx=*/0, qo_packed_idx_base,
            kv_start +
                (kv_tile_idx * NUM_WARPS_KV + get_warp_idx_kv<KTraits>(tid.z)) * NUM_MMA_KV * 16,
            q_len, kv_len, kv_end, gqa_group_size, s_frag, tid, kv_head_idx);
        update_mdo_states<KTraits>(variant, s_frag, o_frag, m, d);
        compute_sfm_v<KTraits>(
            &v_smem, &v_smem_offset_r,
            smem_storage->v_sf_smem_ptr(get_warp_idx_kv<KTraits>(tid.z) * KTraits::NUM_MMA_KV * 16 *
                                        KTraits::NUM_MMA_D_VO),
            lane_idx, s_frag, o_frag, d);
      }

      __syncthreads();

      finalize_m<KTraits>(variant, m);

      // threadblock synchronization
      threadblock_sync_mdo_states<KTraits>(o_frag, smem_storage, m, d, warp_idx, lane_idx, tid);

      // normalize d
      normalize_d<KTraits>(o_frag, m, d, params.v_scale);

      // write back to global memory
      // o_indptr (partial_o): [packed_qo_len * num_kv_chunks, num_kv_heads, head_dim]
      // q_indpt (final_o): [qo_len, num_kv_heads, gqa_group_size, head_dim]
      if (num_kv_chunks > 1) {
        DTypeO* o_ptr_base = params.partial_o +
                             ((o_indptr + kv_chunk_idx) * num_kv_heads + kv_head_idx) * HEAD_DIM_VO;
        write_o_<KTraits>(o_frag, &q_smem, o_ptr_base, qo_packed_idx_base, packed_qo_start,
                          qo_upperbound, num_kv_chunks * num_kv_heads * HEAD_DIM_VO, gqa_group_size,
                          warp_idx, lane_idx, tid);
      } else {
        // write through
        // o_stride_n = num_qo_heads* head_dim
        const uint32_t o_stride_n = num_kv_heads * gqa_group_size * HEAD_DIM_VO,
                       o_stride_h = HEAD_DIM_VO;
        DTypeO* o_ptr_base =
            params.final_o + q_indptr * o_stride_n + (kv_head_idx * gqa_group_size) * o_stride_h;
        write_o_reg_gmem<KTraits>(o_frag, &q_smem, o_ptr_base, qo_packed_idx_base, q_len,
                                  o_stride_n, o_stride_h, gqa_group_size, tid);
      }

      if constexpr (variant.use_softmax) {
        if (get_warp_idx_kv<KTraits>(tid.z) == 0) {
#pragma unroll
          for (uint32_t mma_q = 0; mma_q < NUM_MMA_Q; ++mma_q) {
#pragma unroll
            for (uint32_t j = 0; j < 2; ++j) {
              uint32_t q, r;
              const uint32_t packed_qo_idx = qo_packed_idx_base + lane_idx / 4 + j * 8 + mma_q * 16;
              gqa_group_size.divmod(packed_qo_idx, q, r);
              if (q < qo_upperbound) {
                if (num_kv_chunks > 1) {
                  partial_lse[(o_indptr + (packed_qo_idx - packed_qo_start) * num_kv_chunks +
                               kv_chunk_idx) *
                                  num_kv_heads +
                              kv_head_idx] = math::ptx_log2(d[mma_q][j]) + float(m[mma_q][j]);
                } else if (params.final_lse != nullptr) {
                  // write through
                  const uint32_t qo_head_idx = kv_head_idx * gqa_group_size + r;
                  params.final_lse[(q_indptr + q) * num_kv_heads * gqa_group_size + qo_head_idx] =
                      math::ptx_log2(d[mma_q][j]) + float(m[mma_q][j]);
                }
              }
            }
          }
        }
      }

      // profile
      if constexpr (CTA_TILE_Q > 64) {
        PROFILER_EVENT_END(profiler_closure, PersistentProfileEventType::kRunner1);
      } else {
        PROFILER_EVENT_END(profiler_closure, PersistentProfileEventType::kRunner2);
      }
    }
  }
};

template <uint32_t HEAD_DIM_VO_, uint32_t NUM_SMEM_STAGES_, uint32_t NUM_THREADS_,
          typename DTypeIn_, typename DTypeO_, typename IdType_>
struct StateReductionKernelTraits {
  using DTypeIn = DTypeIn_;
  using DTypeO = DTypeO_;
  using IdType = IdType_;

  static constexpr uint32_t HEAD_DIM_VO = HEAD_DIM_VO_;
  static constexpr uint32_t NUM_SMEM_STAGES = NUM_SMEM_STAGES_;
  static constexpr uint32_t NUM_THREADS = NUM_THREADS_;
  static constexpr uint32_t NUM_WARPS = NUM_THREADS / 32;

  static constexpr uint32_t vec_size =
      std::max<uint32_t>(16U / static_cast<uint32_t>(sizeof(DTypeIn)), HEAD_DIM_VO / 32U);
  static constexpr uint32_t bdx = HEAD_DIM_VO / vec_size;

  // gridDim is accessed by runtime variable and should be set by core attention
  // workload layout [bdx, bdy, num_warps]
  static_assert(NUM_THREADS % bdx == 0);
  static constexpr uint32_t bdy = 32 / bdx;

  // pipeline load & reduction
  static constexpr size_t SMEM_SIZE =
      NUM_WARPS * NUM_SMEM_STAGES * bdy * HEAD_DIM_VO * sizeof(DTypeIn) +
      NUM_THREADS * sizeof(float);
};

template <typename KTraits_>
struct BlockBatchReductionPersistent {
  using KTraits = KTraits_;

  static __device__ __forceinline__ void Run(
      typename KTraits::DTypeIn* __restrict__ V, typename KTraits::DTypeO* __restrict__ v_merged,
      float* __restrict__ S, float* __restrict__ s_merged,
      const typename KTraits::IdType num_packed_qo_len, const uint_fastdiv gqa_group_size,
      const uint32_t num_kv_heads, const typename KTraits::IdType* indptr,
      const typename KTraits::IdType* o_indices, uint8_t* smem PROFILER_CLOSURE_FUNC_PARAMS) {
    __syncthreads();  // NOTE(Zihao): required for guarantee correctness on blackwell
    using DTypeIn = typename KTraits::DTypeIn;
    using DTypeO = typename KTraits::DTypeO;
    using IdType = typename KTraits::IdType;

    [[maybe_unused]] constexpr uint32_t bdx = KTraits::bdx;
    [[maybe_unused]] constexpr uint32_t bdy = KTraits::bdy;
    [[maybe_unused]] constexpr uint32_t num_warps = KTraits::NUM_WARPS;

    [[maybe_unused]] constexpr uint32_t vec_size = KTraits::vec_size;
    [[maybe_unused]] constexpr uint32_t head_dim = KTraits::HEAD_DIM_VO;
    [[maybe_unused]] constexpr uint32_t num_smem_stages = KTraits::NUM_SMEM_STAGES;
    [[maybe_unused]] constexpr uint32_t vec_bits = sizeof(DTypeIn) * vec_size * 8;

    // control flow metadata
    const uint32_t warp_idx = threadIdx.x / 32;
    const uint32_t tx = (threadIdx.x % 32) % bdx, ty = (threadIdx.x % 32) / bdx;

    const uint32_t worker_id = blockIdx.y * num_warps + warp_idx;
    const uint32_t num_workers = gridDim.x * gridDim.y * gridDim.z * num_warps;

    DTypeIn* v_smem = (DTypeIn*)smem + warp_idx * num_smem_stages * bdy * head_dim;
    // FIXME: fix the offset calculation
    float* s_smem = (float*)(smem + num_warps * num_smem_stages * bdy * head_dim * sizeof(DTypeIn) +
                             warp_idx * 32 * sizeof(float));

    // V: [num_packed_qo_len x num_kv_tiles, num_kv_heads, head_dim]
    // v_merged: [qo_len, num_kv_heads, gqa_group_size, head_dim]
#pragma unroll 1
    for (uint32_t i = worker_id; i < num_packed_qo_len * num_kv_heads; i += num_workers) {
      PROFILER_EVENT_START(profiler_closure, PersistentProfileEventType::kReduction);
      __syncwarp();  // avoid data hazard due to reordering st.cast_store
      // remap workload
      uint32_t packed_qo_idx = i / num_kv_heads;
      uint32_t kv_head_idx = i % num_kv_heads;
      const uint32_t num_index_sets = indptr[packed_qo_idx + 1] - indptr[packed_qo_idx];
      if (num_index_sets == 0 || num_index_sets == 1) {
        // already write through, bypass
        PROFILER_EVENT_END(profiler_closure, PersistentProfileEventType::kReduction);
        continue;
      }

      // index calculation
      auto partial_idx_to_offset = [&](uint32_t off) {
        return (indptr[packed_qo_idx] + off) * num_kv_heads + kv_head_idx;
      };
      auto merge_idx_to_offset = [&]() {
        // NOTE (Yilong): qo_head_idx has been calculated in schedule.plan
        return o_indices[packed_qo_idx] + kv_head_idx * gqa_group_size;
      };

      state_t<vec_size> st;

#pragma unroll
      for (uint32_t iter = 0; iter < num_smem_stages; ++iter) {
        cp_async::pred_load<vec_bits, PrefetchMode::kPrefetch, SharedMemFillMode::kNoFill>(
            v_smem + (iter * bdy + ty) * head_dim + tx * vec_size,
            V + partial_idx_to_offset(iter * bdy + ty) * head_dim + tx * vec_size,
            (iter * bdy + ty) < num_index_sets);
        cp_async::commit_group();
      }
#pragma unroll 4
      for (uint32_t iter = 0; iter < ceil_div(num_index_sets, bdy); ++iter) {
        if (iter % bdx == 0) {
          s_smem[ty * bdx + tx] = iter * bdy + (ty * bdx + tx) < num_index_sets
                                      ? S[partial_idx_to_offset(iter * bdy + ty * bdx + tx)]
                                      : 0.f;
          __syncwarp();
        }
        cp_async::wait_group<num_smem_stages - 1>();
        __syncwarp();
        vec_t<float, vec_size> v;
        v.cast_load(v_smem + ((iter % num_smem_stages) * bdy + ty) * head_dim + tx * vec_size);
        if (iter * bdy + ty < num_index_sets) {
          float s = s_smem[(iter % bdx) * bdy + ty];
          st.merge(v, s, 1);
        }
        __syncwarp();
        cp_async::pred_load<vec_bits, PrefetchMode::kPrefetch, SharedMemFillMode::kNoFill>(
            v_smem + ((iter % num_smem_stages) * bdy + ty) * head_dim + tx * vec_size,
            V + partial_idx_to_offset((iter + num_smem_stages) * bdy + ty) * head_dim +
                tx * vec_size,
            (iter + num_smem_stages) * bdy + ty < num_index_sets);
        cp_async::commit_group();
      }
      cp_async::wait_group<0>();
      __syncwarp();

      st.normalize();
      if constexpr (bdy > 1) {
        warp_sync_state<bdx, bdy, vec_size>(st, v_smem, s_smem, tx, ty);
        st.normalize();
      }

      st.o.cast_store(v_merged + merge_idx_to_offset() * head_dim + tx * vec_size);
      if (s_merged != nullptr) {
        s_merged[merge_idx_to_offset()] = st.get_lse();
      }
      PROFILER_EVENT_END(profiler_closure, PersistentProfileEventType::kReduction);
    }
  }
};

template <uint32_t CTA_TILE_Q_1, uint32_t CTA_TILE_Q_2, uint32_t HEAD_DIM_QK, uint32_t HEAD_DIM_VO,
          MaskMode MASK_MODE, typename AttentionVariant, typename Params>
cudaError_t BatchPagedAttentionPersistent(const Params params_1, const Params params_2,
                                          const uint32_t num_blks_x, const uint32_t num_blks_y,
                                          const cudaStream_t stream) {
  static_assert(HEAD_DIM_VO <= 256 && HEAD_DIM_QK <= 256,
                "BatchAttention (holistic persistent) kernel does not support "
                "head_dim > 256; use the FA2 prefill/decode path for large head dims.");
  using DTypeQ = typename Params::DTypeQ;
  using DTypeKV = typename Params::DTypeKV;
  using DTypeO = typename Params::DTypeO;
  using IdType = typename Params::IdType;
  constexpr uint32_t NUM_WARPS_Q_1 = get_num_warps_q(CTA_TILE_Q_1);
  constexpr uint32_t NUM_WARPS_KV_1 = get_num_warps_kv(CTA_TILE_Q_1);
  constexpr uint32_t NUM_MMA_Q_1 = get_num_mma_q(CTA_TILE_Q_1);
  constexpr uint32_t NUM_MMA_KV_1 = 4;
  constexpr uint32_t NUM_MMA_D_QK = HEAD_DIM_QK / 16;
  constexpr uint32_t NUM_MMA_D_VO = HEAD_DIM_VO / 16;
  using KTraits1 = KernelTraits<MASK_MODE, CTA_TILE_Q_1, NUM_MMA_Q_1, NUM_MMA_KV_1, NUM_MMA_D_QK,
                                NUM_MMA_D_VO, NUM_WARPS_Q_1, NUM_WARPS_KV_1, PosEncodingMode::kNone,
                                DTypeQ, DTypeKV, DTypeO, float, IdType, AttentionVariant>;
  constexpr uint32_t NUM_WARPS_Q_2 = get_num_warps_q(CTA_TILE_Q_2);
  constexpr uint32_t NUM_WARPS_KV_2 = get_num_warps_kv(CTA_TILE_Q_2);
  constexpr uint32_t NUM_MMA_Q_2 = get_num_mma_q(CTA_TILE_Q_2);
  constexpr uint32_t NUM_MMA_KV_2 = 2;
  using KTraits2 = KernelTraits<MASK_MODE, CTA_TILE_Q_2, NUM_MMA_Q_2, NUM_MMA_KV_2, NUM_MMA_D_QK,
                                NUM_MMA_D_VO, NUM_WARPS_Q_2, NUM_WARPS_KV_2, PosEncodingMode::kNone,
                                DTypeQ, DTypeKV, DTypeO, float, IdType, AttentionVariant>;

  // Attention state reduction kernel
  constexpr uint32_t NUM_THREADS =
      KTraits1::NUM_THREADS > KTraits2::NUM_THREADS ? KTraits1::NUM_THREADS : KTraits2::NUM_THREADS;
  using ReductionKTraits =
      StateReductionKernelTraits<HEAD_DIM_VO, 4, NUM_THREADS, DTypeO, DTypeO, IdType>;
  size_t smem_size =
      max(sizeof(typename KTraits1::SharedStorage), sizeof(typename KTraits2::SharedStorage));
  smem_size = max(smem_size, ReductionKTraits::SMEM_SIZE);

  // Launch persistent kernel
  auto kernel = PersistentKernelTemplate<BlockBatchPagedAttentionPersistent<KTraits1, Params>,
                                         BlockBatchPagedAttentionPersistent<KTraits2, Params>,
                                         BlockBatchReductionPersistent<ReductionKTraits>>;
  FLASHINFER_CUDA_CALL(
      cudaFuncSetAttribute(kernel, cudaFuncAttributeMaxDynamicSharedMemorySize, smem_size));
  dim3 nblks(num_blks_x, num_blks_y);
  dim3 nthrs(NUM_THREADS);
  void* args[] = {(void*)&params_1, (void*)&params_2};
  FLASHINFER_CUDA_CALL(
      cudaLaunchCooperativeKernel((void*)kernel, nblks, nthrs, args, smem_size, stream));
  return cudaSuccess;
}

};  // namespace flashinfer

#endif  // FLASHINFER_PERSISTENT_CUH_
