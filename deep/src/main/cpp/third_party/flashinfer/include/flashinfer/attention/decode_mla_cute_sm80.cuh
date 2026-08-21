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
#ifndef FLASHINFER_DECODE_CUTE_SM80_CUH_
#define FLASHINFER_DECODE_CUTE_SM80_CUH_
#include <cooperative_groups.h>
#include <cuda_bf16.h>
#include <cuda_fp16.h>
#include <cuda_fp8.h>
#include <cuda_runtime.h>

#include <iostream>

#include "../cp_async.cuh"
#include "../math.cuh"
#include "../pos_enc.cuh"
#include "../utils.cuh"
#include "../vec_dtypes.cuh"
#include "cascade.cuh"
#include "cute/tensor.hpp"
#include "state.cuh"

namespace flashinfer {

using namespace cute;

namespace cg = cooperative_groups;
using cp_async::PrefetchMode;
using cp_async::SharedMemFillMode;

template <uint32_t HEAD_DIM_CKV, uint32_t HEAD_DIM_KPE, uint32_t QO_TILE_LEN, typename DTypeKV>
std::tuple<uint32_t, uint32_t, uint32_t> LaunchSpecForDecodeKernelMlaCuteSM80(
    const uint32_t num_qo_heads) {
  // fixme: below types and consts are duplicated from the ones from MLA decode kernel, we may
  // refactor the duplication later
  constexpr int k_smem_stages = 2;
  constexpr int k_kv_tile_len = 8;
  constexpr int k_warp_rows = 4;
  constexpr int k_warp_cols = 2;

  using LayoutQo =
      Layout<Shape<Int<QO_TILE_LEN>, Int<HEAD_DIM_CKV>>, Stride<Int<HEAD_DIM_CKV>, _1>>;

  using LayoutQnope =
      Layout<Shape<Int<QO_TILE_LEN>, Int<HEAD_DIM_CKV>>, Stride<Int<HEAD_DIM_CKV>, _1>>;
  using LayoutQpe =
      Layout<Shape<Int<QO_TILE_LEN>, Int<HEAD_DIM_KPE>>, Stride<Int<HEAD_DIM_KPE>, _1>>;

  using LayoutAtt =
      Layout<Shape<Int<QO_TILE_LEN>, Int<k_kv_tile_len>>, Stride<Int<k_kv_tile_len + 2>, _1>>;

  using LayoutOScaleVec = Layout<Shape<Int<QO_TILE_LEN>>>;

  using LayoutSwizzleAtomKV = decltype(composition(
      Swizzle<3, 3, 3>{}, make_layout(make_shape(_8{}, _64{}), make_stride(_64{}, _1{}))));
  using LayoutSwizzleQnope = decltype(tile_to_shape(LayoutSwizzleAtomKV{}, LayoutQnope{}.shape()));
  using LayoutSwizzleCkv = decltype(tile_to_shape(
      LayoutSwizzleAtomKV{}, Shape<Int<k_kv_tile_len>, Int<HEAD_DIM_CKV>, Int<k_smem_stages>>{}));
  using LayoutSwizzleQpe = decltype(tile_to_shape(LayoutSwizzleAtomKV{}, LayoutQpe{}.shape()));
  using LayoutSwizzleKpe = decltype(tile_to_shape(
      LayoutSwizzleAtomKV{}, Shape<Int<k_kv_tile_len>, Int<HEAD_DIM_KPE>, Int<k_smem_stages>>{}));

  uint32_t smem_size = k_warp_rows * 32 * sizeof(size_t) * 2 +
                       (cosize(LayoutSwizzleQnope{}) + cosize(LayoutSwizzleQpe{}) +
                        cosize(LayoutSwizzleCkv{}) + cosize(LayoutSwizzleKpe{})) *
                           sizeof(DTypeKV) +
                       cosize(LayoutAtt{}) * sizeof(float) +
                       cosize(LayoutOScaleVec{}) * sizeof(float) * 2;

  const uint32_t gdy = ceil_div(num_qo_heads, QO_TILE_LEN);

  return {smem_size, gdy, k_warp_rows * k_warp_cols};
}

template <uint32_t HEAD_DIM_CKV, uint32_t HEAD_DIM_KPE, uint32_t QO_TILE_LEN, typename Params>
__global__ void BatchDecodeWithPagedKVCacheKernelMlaCuteSM80(Params params) {
  auto block = cooperative_groups::this_thread_block();

  static_assert(std::is_same<typename Params::DTypeQ, half>::value, "DTypeQ is expeted to be fp16");
  static_assert(std::is_same<typename Params::DTypeKV, half>::value,
                "DTypeKV is expeted to be fp16");
  static_assert(std::is_same<typename Params::DTypeO, half>::value, "DTypeO is expeted to be fp16");

  using IdType = typename Params::IdType;
  using DTypeKV = half;
  const DTypeKV* q_nope_ptr = params.q_nope;
  const DTypeKV* q_pe_ptr = params.q_pe;
  DTypeKV* output_ptr = params.o;
  float* lse = params.lse;
  const auto& paged_kv = params.paged_kv;
  const bool* block_valid_mask = params.block_valid_mask;
  const uint32_t num_qo_heads = params.num_qo_heads;
  const bool partition_kv = params.partition_kv;

  const uint32_t batch_idx = blockIdx.x;
  const uint32_t tx = threadIdx.x;

  // when CUDAGraph is enabled, we will launch more blocks than
  // the actual batch size, so we need to check if the current batch is valid
  if (block_valid_mask && !block_valid_mask[batch_idx]) return;
  const uint32_t mapped_batch_idx = params.request_indices[batch_idx];

  const uint32_t orig_seq_len = paged_kv.get_length(mapped_batch_idx);

  const uint32_t kv_chunk_idx_in_orig_mapped_batch = params.kv_tile_indices[batch_idx];
  const uint32_t kv_chunk_size = *(params.kv_chunk_size_ptr);
  const uint32_t cur_chunk_start =
      partition_kv ? kv_chunk_idx_in_orig_mapped_batch * kv_chunk_size : 0;
  const uint32_t cur_chunk_end =
      partition_kv ? min((kv_chunk_idx_in_orig_mapped_batch + 1) * kv_chunk_size, orig_seq_len)
                   : orig_seq_len;
  const uint32_t cur_chunk_len = cur_chunk_end - cur_chunk_start;

  uint32_t packed_page_iter_base =
      paged_kv.indptr[mapped_batch_idx] * paged_kv.page_size + cur_chunk_start;
  const IdType last_indptr = paged_kv.indptr[paged_kv.batch_size];

  const auto sm_scale = params.sm_scale * math::log2e;

  constexpr int k_smem_stages = 2;
  constexpr int k_kv_tile_len = 8;
  constexpr int k_warp_rows = 4;
  constexpr int k_warp_cols = 2;

  using LayoutQo =
      Layout<Shape<Int<QO_TILE_LEN>, Int<HEAD_DIM_CKV>>, Stride<Int<HEAD_DIM_CKV>, _1>>;

  using LayoutQnope =
      Layout<Shape<Int<QO_TILE_LEN>, Int<HEAD_DIM_CKV>>, Stride<Int<HEAD_DIM_CKV>, _1>>;
  using LayoutQpe =
      Layout<Shape<Int<QO_TILE_LEN>, Int<HEAD_DIM_KPE>>, Stride<Int<HEAD_DIM_KPE>, _1>>;

  using LayoutAtt =
      Layout<Shape<Int<QO_TILE_LEN>, Int<k_kv_tile_len>>, Stride<Int<k_kv_tile_len + 2>, _1>>;

  using LayoutOScaleVec = Layout<Shape<Int<QO_TILE_LEN>>>;

  using LayoutSwizzleAtomKV = decltype(composition(
      Swizzle<3, 3, 3>{}, make_layout(make_shape(_8{}, _64{}), make_stride(_64{}, _1{}))));
  using LayoutSwizzleQnope = decltype(tile_to_shape(LayoutSwizzleAtomKV{}, LayoutQnope{}.shape()));
  using LayoutSwizzleCkv = decltype(tile_to_shape(
      LayoutSwizzleAtomKV{}, Shape<Int<k_kv_tile_len>, Int<HEAD_DIM_CKV>, Int<k_smem_stages>>{}));

  using LayoutSwizzleQpe = decltype(tile_to_shape(LayoutSwizzleAtomKV{}, LayoutQpe{}.shape()));
  using LayoutSwizzleKpe = decltype(tile_to_shape(
      LayoutSwizzleAtomKV{}, Shape<Int<k_kv_tile_len>, Int<HEAD_DIM_KPE>, Int<k_smem_stages>>{}));

  const uint32_t q_head_idx_start = mapped_batch_idx * num_qo_heads + blockIdx.y * QO_TILE_LEN;
  const uint32_t o_head_idx_start = batch_idx * num_qo_heads + blockIdx.y * QO_TILE_LEN;

  Tensor gmem_q_nope_chunk =
      make_tensor(make_gmem_ptr(q_nope_ptr + q_head_idx_start * HEAD_DIM_CKV), LayoutQnope{});
  Tensor gmem_q_pe_chunk =
      make_tensor(make_gmem_ptr(q_pe_ptr + q_head_idx_start * HEAD_DIM_KPE), LayoutQpe{});
  Tensor gmem_output_chunk =
      make_tensor(make_gmem_ptr(output_ptr + o_head_idx_start * HEAD_DIM_CKV), LayoutQo{});

  extern __shared__ char smem_data[];
  size_t* ckv_offset_smem = (size_t*)smem_data;
  size_t* kpe_offset_smem = ckv_offset_smem + k_warp_rows * 32;
  Tensor smem_q_nope = make_tensor(make_smem_ptr((DTypeKV*)(kpe_offset_smem + k_warp_rows * 32)),
                                   LayoutSwizzleQnope{});
  Tensor smem_q_pe = make_tensor(
      make_smem_ptr(smem_q_nope.data() + cute::cosize(LayoutSwizzleQnope{})), LayoutSwizzleQpe{});

  Tensor smem_ckv_chunk = make_tensor(
      make_smem_ptr(smem_q_pe.data() + cute::cosize(LayoutSwizzleQpe{})), LayoutSwizzleCkv{});
  Tensor smem_kpe_chunk = make_tensor(
      make_smem_ptr(smem_ckv_chunk.data() + cute::cosize(LayoutSwizzleCkv{})), LayoutSwizzleKpe{});

  Tensor smem_att = make_tensor(
      make_smem_ptr((float*)(smem_kpe_chunk.data().ptr_ + cute::cosize(LayoutSwizzleKpe{}))),
      LayoutAtt{});

  float* ptr_o_scale = (float*)(smem_att.data().ptr_ + cute::cosize(LayoutAtt{}));
  Tensor smem_o_scale = make_tensor(make_smem_ptr(ptr_o_scale), LayoutOScaleVec{});
  float* ptr_denom = ptr_o_scale + cute::cosize(LayoutOScaleVec{});
  Tensor smem_denom = make_tensor(make_smem_ptr(ptr_denom), LayoutOScaleVec{});

  constexpr uint32_t k_thr_g2s_tile_m = k_kv_tile_len;                        // 8
  constexpr uint32_t k_thr_g2s_tile_k = k_warp_rows * 32 / k_thr_g2s_tile_m;  // 16
  auto layout_thr_g2s_tile =
      make_layout(make_shape(Int<k_thr_g2s_tile_m>{}, Int<k_thr_g2s_tile_k>{}), LayoutLeft{});
  const uint32_t thr_m_idx_within_tile =
      tx % k_thr_g2s_tile_m;  // it's also kv-idx for ckv and kpe sequence
  const uint32_t thr_k_idx_within_tile = tx / k_thr_g2s_tile_m;

  // load q data to smem
  Tensor gmem_q_nope_chunk_128bit = recast<cute::uint128_t>(gmem_q_nope_chunk);
  Tensor gmem_q_nope_part_128bit =
      local_partition(gmem_q_nope_chunk_128bit, layout_thr_g2s_tile, tx);
  Tensor smem_q_nope_128bit = recast<cute::uint128_t>(smem_q_nope);
  Tensor smem_q_nope_part_128bit = local_partition(smem_q_nope_128bit, layout_thr_g2s_tile, tx);
  if (tx < k_warp_rows * 32) {
#pragma unroll
    for (int n = 0; n < size<0>(gmem_q_nope_part_128bit); ++n)
#pragma unroll
      for (int k = 0; k < size<1>(gmem_q_nope_part_128bit); ++k) {
        smem_q_nope_part_128bit(n, k) = gmem_q_nope_part_128bit(n, k);
      }
    if (thr_k_idx_within_tile < (HEAD_DIM_KPE * sizeof(DTypeKV) / sizeof(cute::uint128_t))) {
      Tensor gmem_q_pe_chunk_128bit = recast<cute::uint128_t>(gmem_q_pe_chunk);
      Tensor gmem_q_pe_part_128bit =
          local_partition(gmem_q_pe_chunk_128bit, layout_thr_g2s_tile, tx);
      Tensor smem_q_pe_128bit = recast<cute::uint128_t>(smem_q_pe);
      Tensor smem_q_pe_part_128bit = local_partition(smem_q_pe_128bit, layout_thr_g2s_tile, tx);
      static_assert(size<1>(gmem_q_pe_part_128bit) == 1);
#pragma unroll
      for (int n = 0; n < size<0>(gmem_q_pe_part_128bit); ++n) {
        smem_q_pe_part_128bit(n, _0{}) = gmem_q_pe_part_128bit(n, _0{});
      }
    }
  }
  block.sync();

  // initialize variables needed by phase2
  Tensor smem_ckv_chunk_128bit = recast<cute::uint128_t>(smem_ckv_chunk);
  Tensor smem_ckv_load_part_128bit =
      local_partition(smem_ckv_chunk_128bit, layout_thr_g2s_tile, tx);
  Tensor smem_kpe_chunk_128bit = recast<cute::uint128_t>(smem_kpe_chunk);
  Tensor smem_kpe_load_part_128bit =
      local_partition(smem_kpe_chunk_128bit, layout_thr_g2s_tile, tx);

  constexpr uint32_t k_mma_att_tile_k = 16;
  using TiledMmaAtt =
      decltype(make_tiled_mma(MMA_Atom<MMA_Traits<SM80_16x8x16_F32F16F16F32_TN>>{},
                              make_layout(Shape<Int<k_warp_rows>, _1, _1>{}, LayoutRight{})));
  TiledMmaAtt tiled_mma_att;
  auto thr_mma = tiled_mma_att.get_slice(tx);

  Tensor smem_q_nope_local_tiles = local_tile(
      smem_q_nope, make_tile(Int<QO_TILE_LEN>{}, Int<k_mma_att_tile_k>{}), make_coord(_0{}, _));
  Tensor reg_q_nope_tile_part = thr_mma.partition_fragment_A(smem_q_nope_local_tiles(_, _, 0));

  Tensor smem_ckv_local_tiles =
      local_tile(smem_ckv_chunk, make_tile(Int<k_kv_tile_len>{}, Int<k_mma_att_tile_k>{}),
                 make_coord(_0{}, _));
  Tensor reg_ckv_tile_part = thr_mma.partition_fragment_B(smem_ckv_local_tiles(_, _, _0{}, _0{}));

  auto s2r_tiled_copy_a = make_tiled_copy_A(Copy_Atom<SM75_U32x4_LDSM_N, DTypeKV>{}, tiled_mma_att);
  auto s2r_thr_copy_a = s2r_tiled_copy_a.get_slice(tx);
  Tensor smem_q_nope_tiles_part = s2r_thr_copy_a.partition_S(smem_q_nope_local_tiles);
  Tensor reg_q_nope_tile_part_view = s2r_thr_copy_a.retile_D(reg_q_nope_tile_part);

  auto s2r_tiled_copy_b = make_tiled_copy_B(Copy_Atom<SM75_U32x2_LDSM_N, DTypeKV>{}, tiled_mma_att);
  auto s2r_thr_copy_b = s2r_tiled_copy_b.get_slice(tx);

  Tensor smem_ckv_tiles_part = s2r_thr_copy_b.partition_S(smem_ckv_local_tiles);
  Tensor reg_ckv_tile_part_view = s2r_thr_copy_b.retile_D(reg_ckv_tile_part);

  Tensor smem_q_pe_local_tiles = local_tile(
      smem_q_pe, make_tile(Int<QO_TILE_LEN>{}, Int<k_mma_att_tile_k>{}), make_coord(_0{}, _));
  Tensor reg_q_pe_tile_part = thr_mma.partition_fragment_A(smem_q_pe_local_tiles(_, _, _0{}));

  Tensor smem_kpe_local_tiles =
      local_tile(smem_kpe_chunk, make_tile(Int<k_kv_tile_len>{}, Int<k_mma_att_tile_k>{}),
                 make_coord(_0{}, _));
  Tensor reg_kpe_tile_part = thr_mma.partition_fragment_B(smem_kpe_local_tiles(_, _, _0{}, _0{}));

  Tensor smem_q_pe_tiles_part = s2r_thr_copy_a.partition_S(smem_q_pe_local_tiles);
  Tensor reg_q_pe_tile_part_view = s2r_thr_copy_a.retile_D(reg_q_pe_tile_part);

  Tensor smem_kpe_tiles_part = s2r_thr_copy_b.partition_S(smem_kpe_local_tiles);
  Tensor reg_kpe_tile_part_view = s2r_thr_copy_b.retile_D(reg_kpe_tile_part);

  Tensor smem_att_part_c = thr_mma.partition_C(smem_att);
  Tensor reg_att_part_c = make_fragment_like(smem_att_part_c);

  using LayoutOScaleMat = Layout<Shape<Int<QO_TILE_LEN>, Int<HEAD_DIM_CKV>>, Stride<_1, _0>>;
  Tensor o_scale_broadcast_mat = make_tensor((ptr_o_scale), LayoutOScaleMat{});
  Tensor denom_broadcast_mat = make_tensor(make_smem_ptr(ptr_denom), LayoutOScaleMat{});

  // initialize variables needed by phase3
  using TiledMmaOutput = decltype(make_tiled_mma(
      MMA_Atom<MMA_Traits<SM80_16x8x8_F32F16F16F32_TN>>{},
      make_layout(Shape<Int<k_warp_rows>, Int<k_warp_cols>, _1>{}, LayoutRight{})));
  TiledMmaOutput tiled_mma_output;
  auto thr_mma_output = tiled_mma_output.get_slice(tx);

  Tensor smem_att_part_a = thr_mma_output.partition_A(smem_att);
  Tensor reg_att_part_a =
      thr_mma_output.partition_fragment_A(make_tensor((DTypeKV*)0x0, LayoutAtt{}));

  auto layout_ckv_trans =
      make_layout(make_shape(Int<HEAD_DIM_CKV>{}, Int<k_kv_tile_len>{}, Int<k_smem_stages>{}),
                  make_stride(Int<k_kv_tile_len>{}, _1{}, Int<HEAD_DIM_CKV * k_kv_tile_len>{}));
  auto layout_ckv_trans_cps = composition(smem_ckv_chunk.layout(), layout_ckv_trans);
  Tensor smem_ckv_trans = make_tensor(smem_ckv_chunk.data(), layout_ckv_trans_cps);

  auto s2r_tiled_copy_b_ckv =
      make_tiled_copy_B(Copy_Atom<SM75_U16x2_LDSM_T, DTypeKV>{}, tiled_mma_output);
  auto s2r_thr_copy_b_ckv = s2r_tiled_copy_b_ckv.get_slice(tx);
  Tensor smem_v_part = s2r_thr_copy_b_ckv.partition_S(smem_ckv_trans);

  auto layout_ckv_trans_no_stage =
      make_layout(make_shape(Int<HEAD_DIM_CKV>{}, Int<k_kv_tile_len>{}),
                  make_stride(Int<k_kv_tile_len>{}, _1{}));
  Tensor reg_v_part =
      thr_mma_output.partition_fragment_B(make_tensor((DTypeKV*)0x0, layout_ckv_trans_no_stage));
  Tensor reg_v_part_view = s2r_thr_copy_b_ckv.retile_D(reg_v_part);

  Tensor gmem_output_chunk_part = thr_mma_output.partition_C(gmem_output_chunk);
  // Tensor reg_output_part = make_fragment_like(gmem_output_chunk_part);
  Tensor reg_output_part =
      thr_mma_output.partition_fragment_C(make_tensor((float*)0x0, LayoutQo{}));
  clear(reg_output_part);

  Tensor o_scale_mat_part = thr_mma_output.partition_C(o_scale_broadcast_mat);
  Tensor denom_mat_part = thr_mma_output.partition_C(denom_broadcast_mat);

  // init paged-cache read offset to be used
  uint32_t q, r;
  if (tx < k_warp_rows * 32) {
    paged_kv.page_size.divmod(packed_page_iter_base + tx, q, r);
    ckv_offset_smem[tx] = paged_kv.protective_get_offset_ckv(q, r, /*feat_idx*/ 0, last_indptr);
    kpe_offset_smem[tx] = paged_kv.protective_get_offset_kpe(q, r, /*feat_idx*/ 0, last_indptr);
  }
  block.sync();

  uint32_t stage_idx = 0;
  size_t offset_bytes;
  bool is_valid_range;
  if (tx < k_warp_rows * 32) {
#pragma unroll
    for (uint32_t iter = 0; iter < k_smem_stages; ++iter) {
      uint32_t kv_idx = iter * k_kv_tile_len + thr_m_idx_within_tile;
      is_valid_range = kv_idx < cur_chunk_len;

      offset_bytes = ckv_offset_smem[kv_idx];
      static_assert(size<0>(smem_ckv_load_part_128bit) == 1);
#pragma unroll
      for (int k = 0; k < size<1>(smem_ckv_load_part_128bit); ++k) {
        cp_async::pred_load<128, cp_async::PrefetchMode::kPrefetch,
                            cp_async::SharedMemFillMode::kNoFill>(
            &smem_ckv_load_part_128bit(_0{}, k, stage_idx),
            (cute::uint128_t*)(paged_kv.ckv_data + offset_bytes) + k * k_thr_g2s_tile_k +
                thr_k_idx_within_tile,
            is_valid_range);
      }

      offset_bytes = kpe_offset_smem[kv_idx];
      is_valid_range =
          is_valid_range &&
          (thr_k_idx_within_tile < (HEAD_DIM_KPE * sizeof(DTypeKV) / sizeof(cute::uint128_t)));
      static_assert(size<0>(smem_kpe_load_part_128bit) == 1 &&
                    size<1>(smem_kpe_load_part_128bit) == 1);
      cp_async::pred_load<128, cp_async::PrefetchMode::kPrefetch,
                          cp_async::SharedMemFillMode::kNoFill>(
          &smem_kpe_load_part_128bit(_0{}, _0{}, stage_idx),
          (cute::uint128_t*)(paged_kv.kpe_data + offset_bytes) + thr_k_idx_within_tile,
          is_valid_range);

      cp_async::commit_group();
      stage_idx = (stage_idx + 1) % k_smem_stages;
    }
  }

  // start rolling update
  float row_max = -flashinfer::math::inf;
  float row_denom = 1.0;
  for (uint32_t iter = 0; iter < ceil_div(cur_chunk_len, k_kv_tile_len); ++iter) {
    if (tx < k_warp_rows * 32) {
      cp_async::wait_group<1 * k_smem_stages - 1>();
    }
    block.sync();

    if (tx < k_warp_rows * 32) {
      clear(reg_att_part_c);
#pragma unroll
      for (int k_tile = 0; k_tile < size<3>(smem_q_nope_tiles_part); ++k_tile) {
        cute::copy(s2r_tiled_copy_a, smem_q_nope_tiles_part(_, _, _, k_tile),
                   reg_q_nope_tile_part_view);
        cute::copy(s2r_tiled_copy_b, smem_ckv_tiles_part(_, _, _, k_tile, stage_idx),
                   reg_ckv_tile_part_view);
        cute::gemm(tiled_mma_att, reg_att_part_c, reg_q_nope_tile_part, reg_ckv_tile_part,
                   reg_att_part_c);
      }
#pragma unroll
      for (int k_tile = 0; k_tile < size<3>(smem_q_pe_tiles_part); ++k_tile) {
        cute::copy(s2r_tiled_copy_a, smem_q_pe_tiles_part(_, _, _, k_tile),
                   reg_q_pe_tile_part_view);
        cute::copy(s2r_tiled_copy_b, smem_kpe_tiles_part(_, _, _, k_tile, stage_idx),
                   reg_kpe_tile_part_view);
        cute::gemm(tiled_mma_att, reg_att_part_c, reg_q_pe_tile_part, reg_kpe_tile_part,
                   reg_att_part_c);
      }
#pragma unroll
      for (int i = 0; i < cute::size(reg_att_part_c); ++i) {
        reg_att_part_c(i) *= sm_scale;
      }
      cute::copy(reg_att_part_c, smem_att_part_c);
    }
    block.sync();

    // Phase2 compute softmax
    if (tx < QO_TILE_LEN) {
      uint32_t valid_kv_len = cur_chunk_len - iter * k_kv_tile_len;
      valid_kv_len = (valid_kv_len < k_kv_tile_len) ? valid_kv_len : k_kv_tile_len;

      float row_max_prev = row_max;
#pragma unroll
      for (int i = 0; i < k_kv_tile_len; ++i) {
        if (i >= valid_kv_len) smem_att(tx, i) = -flashinfer::math::inf;
        row_max = max(row_max, smem_att(tx, i));
      }

      float row_o_scale = math::ptx_exp2(max(row_max_prev - row_max, -flashinfer::math::inf));
      smem_o_scale(tx) = row_o_scale;

      row_denom *= row_o_scale;
#pragma unroll
      for (int i = 0; i < k_kv_tile_len; ++i) {
        smem_att(tx, i) = math::ptx_exp2(max(smem_att(tx, i) - row_max, -flashinfer::math::inf));
        row_denom += smem_att(tx, i);
      }
      smem_denom(tx) = row_denom;
    }
    block.sync();

    // Phase3 compute output

    // below code block is executed by all 8 warps
    {
#pragma unroll
      for (int i = 0; i < cute::size(reg_output_part); ++i)
        reg_output_part(i) = reg_output_part(i) * o_scale_mat_part(i);

      cute::copy(smem_att_part_a, reg_att_part_a);
      cute::copy(s2r_tiled_copy_b_ckv, smem_v_part(_, _, _, stage_idx), reg_v_part_view);
      cute::gemm(tiled_mma_output, reg_output_part, reg_att_part_a, reg_v_part, reg_output_part);
    }

    if (tx < k_warp_rows * 32) {
      // refill offset_smem
      constexpr uint32_t how_many__kv_tile_len__in__offset_smem = k_warp_rows * 32 / k_kv_tile_len;
      if (((iter + k_smem_stages) % how_many__kv_tile_len__in__offset_smem) == 0) {
        uint32_t q, r;
        paged_kv.page_size.divmod(
            packed_page_iter_base + (iter + k_smem_stages) * k_kv_tile_len + tx, q, r);
        ckv_offset_smem[tx] = paged_kv.protective_get_offset_ckv(q, r, 0, last_indptr);
        kpe_offset_smem[tx] = paged_kv.protective_get_offset_kpe(q, r, 0, last_indptr);
      }
    }
    block.sync();

    if (tx < k_warp_rows * 32) {
      // commit next async copy task to pipeline
      uint32_t kv_idx = (iter + k_smem_stages) * k_kv_tile_len + thr_m_idx_within_tile;
      is_valid_range = kv_idx < cur_chunk_len;

      offset_bytes = ckv_offset_smem[kv_idx % (k_warp_rows * 32)];
      static_assert(size<0>(smem_ckv_load_part_128bit) == 1);
#pragma unroll
      for (int k = 0; k < size<1>(smem_ckv_load_part_128bit); ++k) {
        cp_async::pred_load<128, cp_async::PrefetchMode::kPrefetch,
                            cp_async::SharedMemFillMode::kNoFill>(
            &smem_ckv_load_part_128bit(_0{}, k, stage_idx),
            (cute::uint128_t*)(paged_kv.ckv_data + offset_bytes) + k * k_thr_g2s_tile_k +
                thr_k_idx_within_tile,
            is_valid_range);
      }

      offset_bytes = kpe_offset_smem[kv_idx % (k_warp_rows * 32)];
      is_valid_range =
          is_valid_range &&
          (thr_k_idx_within_tile < (HEAD_DIM_KPE * sizeof(DTypeKV) / sizeof(cute::uint128_t)));
      static_assert(size<0>(smem_kpe_load_part_128bit) == 1 &&
                    size<1>(smem_kpe_load_part_128bit) == 1);
      cp_async::pred_load<128, cp_async::PrefetchMode::kPrefetch,
                          cp_async::SharedMemFillMode::kNoFill>(
          &smem_kpe_load_part_128bit(_0{}, _0{}, stage_idx),
          (cute::uint128_t*)(paged_kv.kpe_data + offset_bytes) + thr_k_idx_within_tile,
          is_valid_range);
      cp_async::commit_group();
    }

    stage_idx = (stage_idx + 1) % k_smem_stages;
  }  // end for kv tile iteration
  if (tx < k_warp_rows * 32) {
    cp_async::wait_group<0>();
  }
  block.sync();

  // final output phase
#pragma unroll
  for (int i = 0; i < cute::size(reg_output_part); ++i) {
    reg_output_part(i) = reg_output_part(i) / denom_mat_part(i);
  }

  cute::copy(reg_output_part, gmem_output_chunk_part);

  if (lse != nullptr && tx < QO_TILE_LEN) {
    lse[o_head_idx_start + tx] = row_max + math::ptx_log2(row_denom);
  }
}

template <uint32_t HEAD_DIM_CKV, uint32_t HEAD_DIM_KPE, uint32_t QO_TILE_LEN,
          typename AttentionVariant, typename Params>
cudaError_t BatchDecodeWithPagedKVCacheDispatchedMlaCuteSM80(Params params,
                                                             typename Params::DTypeO* tmp_v,
                                                             float* tmp_s, cudaStream_t stream) {
  using DTypeQ = typename Params::DTypeQ;
  using DTypeKV = typename Params::DTypeKV;
  using DTypeO = typename Params::DTypeO;
  using IdType = typename Params::IdType;
  const uint32_t num_qo_heads = params.num_qo_heads;
  const uint32_t padded_batch_size = params.padded_batch_size;

  auto [smem_size, gdy, k_warps] =
      LaunchSpecForDecodeKernelMlaCuteSM80<HEAD_DIM_CKV, HEAD_DIM_KPE, QO_TILE_LEN, DTypeKV>(
          num_qo_heads);
  auto kernel =
      BatchDecodeWithPagedKVCacheKernelMlaCuteSM80<HEAD_DIM_CKV, HEAD_DIM_KPE, QO_TILE_LEN, Params>;

  FLASHINFER_CUDA_CALL(
      cudaFuncSetAttribute(kernel, cudaFuncAttributeMaxDynamicSharedMemorySize, smem_size));

  if (tmp_v == nullptr) {
    // do not use partition-kv kernel
    dim3 nblks(padded_batch_size, gdy);
    dim3 nthrs(k_warps * 32);
    params.partition_kv = false;
    void* args[] = {(void*)&params};
    FLASHINFER_CUDA_CALL(cudaLaunchKernel((void*)kernel, nblks, nthrs, args, smem_size, stream));
  } else {
    // use partition-kv kernel
    params.partition_kv = true;
    auto o = params.o;
    auto lse = params.lse;
    params.o = tmp_v;
    params.lse = tmp_s;
    void* args[] = {(void*)&params};
    dim3 nblks(padded_batch_size, gdy);
    dim3 nthrs(k_warps * 32);
    FLASHINFER_CUDA_CALL(cudaLaunchKernel((void*)kernel, nblks, nthrs, args, smem_size, stream));
    FLASHINFER_CUDA_CALL(VariableLengthMergeStates(tmp_v, tmp_s, params.o_indptr, o, lse,
                                                   params.paged_kv.batch_size, nullptr,
                                                   num_qo_heads, HEAD_DIM_CKV, stream));
  }

  return cudaSuccess;
}

}  // namespace flashinfer

#endif  // FLASHINFER_DECODE_CUTE_SM80_CUH_
