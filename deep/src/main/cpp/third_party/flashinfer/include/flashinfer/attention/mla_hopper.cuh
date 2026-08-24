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
#ifndef FLASHINFER_MLA_HOPPER_CUH_
#define FLASHINFER_MLA_HOPPER_CUH_
#include <cooperative_groups.h>

#include <cstdint>
#include <cuda/std/limits>
#include <sstream>

#include "hopper.cuh"
#include "mla.cuh"
#include "mla_params.cuh"
#include "prefill.cuh"
#include "variant_helper.cuh"

namespace flashinfer {

namespace mla {

namespace hopper {

enum class ProfileEventType {
  kIssueLoadQ = 0U,
  kIssueLoadKV = 1U,
  kWriteO = 2U,
  kSoftmaxUpdate = 3U,
  kGemmQK = 4U,
  kGemmPV = 5U,
  kRescaleO = 6U,
  kWritePSmem = 7U,
  kSplitK = 8U,
};

enum class NamedBarriers {
  kOScaleReady = 1U,
  kBarrierO = 2U,
  kMDReady = 3U,
  // FP8 KV path only: 128-thread (consumer warpgroup) barriers used to bracket
  // the FP8->BF16 dequant of one KV tile. Producer warpgroup does not touch
  // these; cross-warpgroup ordering is provided by the existing kOScaleReady
  // (consumer arrives after QK -> producer reads BF16 staging in PV).
  kConsumerDequantBegin = 4U,
  kConsumerDequantEnd = 5U,
};

__device__ __forceinline__ void barrier_arrive(int num_threads, NamedBarriers barrier) {
  cutlass::arch::NamedBarrier::arrive(num_threads, static_cast<int>(barrier));
}

__device__ __forceinline__ void barrier_sync(int num_threads, NamedBarriers barrier) {
  cutlass::arch::NamedBarrier::sync(num_threads, static_cast<int>(barrier));
}

template <typename MainloopPipeline, uint32_t NUM_STAGES, uint32_t CTA_TILE_Q, uint32_t CTA_TILE_KV,
          uint32_t HEAD_DIM_CKV, uint32_t HEAD_DIM_KPE, typename DTypeQ, typename DTypeKV,
          typename DTypeO>
struct HopperSharedStorageQKVO {
  // FP8 KV path: store KV as FP8 (e4m3) in shmem, dequantize to BF16 in
  // dedicated staging buffers right before each WGMMA. WGMMA itself stays
  // BF16xBF16 because Hopper does not have a mixed BF16xFP8 wgmma instruction.
  // Must match HopperKernelTraits::USE_KV_REPACK exactly — same predicate
  // keeps the smem layout in sync with the kernel-side dequant.
  static constexpr bool USE_KV_REPACK = std::is_same_v<DTypeKV, __nv_fp8_e4m3>;
  // Per-stage KV data tile. `p` (softmax output) is always DTypeQ-typed so the
  // PV WGMMA can run as BF16xBF16 on both the BF16 KV and FP8 KV paths. On the
  // FP8 KV path the union with the FP8-typed `kpe` is sized for the larger
  // BF16 P, so the per-stage struct shrinks from 72KB (BF16) to 40KB (FP8).
  struct PerStageKV {
    alignas(16) DTypeKV ckv[CTA_TILE_KV * HEAD_DIM_CKV];
    union {
      alignas(16) DTypeKV kpe[CTA_TILE_KV * HEAD_DIM_KPE];
      alignas(16) DTypeQ p[CTA_TILE_Q * CTA_TILE_KV];
    };
  };
  // The output writeback overlay `o` lives at the top level (not per-stage):
  // it is written only after every stage has been consumed, so it can safely
  // share memory with the entire per-stage data path. This is critical for
  // the FP8 path, which needs ~72KB of shared BF16 dequant staging on top of
  // ~80KB of per-stage data; replicating an o overlay per stage would blow
  // the 228KB/SM budget.
  struct {
    struct {
      struct {
        alignas(16) DTypeQ nope[CTA_TILE_Q * HEAD_DIM_CKV];
        alignas(16) DTypeQ pe[CTA_TILE_Q * HEAD_DIM_KPE];
      } q_smem;
      union {
        struct {
          PerStageKV kv_o_smem[NUM_STAGES];
          // FP8-only BF16 dequant staging, shared across stages. On the BF16
          // path these collapse to a single element via std::conditional_t.
          alignas(16) std::conditional_t<USE_KV_REPACK, DTypeQ[CTA_TILE_KV * HEAD_DIM_CKV],
                                         DTypeQ[1]> ckv_bf16;
          // When HEAD_DIM_KPE=0 (no-PE MLA) the FP8 KPE staging is unused but
          // must stay a valid object, so size it as at least one element.
          alignas(16) std::conditional_t<USE_KV_REPACK,
                                         DTypeQ[HEAD_DIM_KPE > 0 ? CTA_TILE_KV* HEAD_DIM_KPE : 1],
                                         DTypeQ[1]> kpe_bf16;
        };
        alignas(16) DTypeO o[CTA_TILE_Q * HEAD_DIM_CKV];
      };
      alignas(16) float o_scale[CTA_TILE_Q];
      alignas(16) float m[CTA_TILE_Q];
      alignas(16) float d[CTA_TILE_Q];
    };

    typename MainloopPipeline::SharedStorage pipeline_q, pipeline_kv;
  };
};

template <bool CAUSAL_, uint32_t NUM_STAGES_, uint32_t HEAD_DIM_CKV_, uint32_t HEAD_DIM_KPE_,
          uint32_t CTA_TILE_Q_, uint32_t CTA_TILE_KV_, typename DTypeQ_, typename DTypeKV_,
          typename DTypeO_, typename IdType_>
struct HopperKernelTraits
    : KernelTraits<CAUSAL_, NUM_STAGES_, /*QK_SHARD_=*/false, HEAD_DIM_CKV_, HEAD_DIM_KPE_,
                   CTA_TILE_Q_, CTA_TILE_KV_, DTypeQ_, DTypeKV_, DTypeO_, IdType_> {
  static constexpr uint32_t NUM_THREADS = 256;
  static constexpr uint32_t NUM_COPY_THREADS = 128;
  static constexpr uint32_t NUM_QK_THREADS = 128;
  static constexpr uint32_t NUM_REGS_S_FRAG = CTA_TILE_KV_ / 2;
  static constexpr uint32_t NUM_REGS_O_FRAG = HEAD_DIM_CKV_ / 4;
  static constexpr uint32_t NUM_REGS_P_FRAG = CTA_TILE_KV_ / 4;
  // FP8 KV path: KV stored as FP8 e4m3 in shmem, dequantized to DTypeQ
  // before WGMMA. Match on the exact type (not sizeof==1) — other 1-byte
  // JIT dtypes (e.g. __nv_fp4x2_e2m1) have no compatible vec_cast.
  static constexpr bool USE_KV_REPACK = std::is_same_v<DTypeKV_, __nv_fp8_e4m3>;
  // FP8 KV swizzle / dequant layout is only correct for the supported MLA
  // dims; AOT/JIT must not instantiate other sizes.
  static_assert(!USE_KV_REPACK || HEAD_DIM_CKV_ == 512,
                "FP8 KV MLA path currently only supports HEAD_DIM_CKV=512");
  static_assert(!USE_KV_REPACK || HEAD_DIM_KPE_ == 0 || HEAD_DIM_KPE_ == 64,
                "FP8 KV MLA path currently only supports HEAD_DIM_KPE=0 (no PE) or 64");
  // Strides for the BF16 dequant staging buffer and the P buffer, both of
  // which are DTypeQ-typed regardless of DTypeKV.
  static constexpr uint32_t UPCAST_STRIDE_CKV_BF16 = HEAD_DIM_CKV_ / upcast_size<DTypeQ_>();
  static constexpr uint32_t UPCAST_STRIDE_KPE_BF16 = HEAD_DIM_KPE_ / upcast_size<DTypeQ_>();
  static constexpr uint32_t UPCAST_STRIDE_P_BF16 = CTA_TILE_KV_ / upcast_size<DTypeQ_>();
  // Dtype-aware TMA load loop bounds. The existing FA2-shared KernelTraits hard
  // codes `NUM_MMA_D_* / 4` based on a BF16 K=16 MMA shape, which overshoots by
  // 2x for FP8 (one b128 holds 16 fp8 elems vs 8 bf16). For HEAD_DIM_KPE=64 on
  // FP8 the row only has 4 b128, so we additionally gate 4 of 8 lanes off.
  static constexpr uint32_t CKV_B128_PER_ROW = HEAD_DIM_CKV_ * sizeof(DTypeKV_) / 16;
  static constexpr uint32_t KPE_B128_PER_ROW = HEAD_DIM_KPE_ * sizeof(DTypeKV_) / 16;
  static constexpr uint32_t LANES_PER_ROW_CKV = (CKV_B128_PER_ROW >= 8) ? 8 : CKV_B128_PER_ROW;
  static constexpr uint32_t LANES_PER_ROW_KPE =
      KPE_B128_PER_ROW == 0 ? 1 : ((KPE_B128_PER_ROW >= 8) ? 8 : KPE_B128_PER_ROW);
  static constexpr uint32_t INNER_LOADS_CKV = CKV_B128_PER_ROW / LANES_PER_ROW_CKV;
  static constexpr uint32_t INNER_LOADS_KPE = KPE_B128_PER_ROW / LANES_PER_ROW_KPE;
  // Swizzle for the FP8 *raw* KPE shmem storage. When KPE_B128_PER_ROW < 8 (FP8
  // KPE with HEAD_DIM_KPE=64 only has 4 b128 per row), the k128B swizzle's
  // N=8 col group makes (row=K, col=c) collide with (row=K+4, col=c) at the
  // same shmem offset, corrupting load_kv writes. Switch the raw-FP8 KPE
  // layout to a k64B swizzle (N=4) so the row-group bits stay distinct.
  // BF16 path is unaffected (KPE_B128_PER_ROW=8, k128B already correct).
  static constexpr SwizzleMode SWIZZLE_MODE_KPE_RAW =
      (USE_KV_REPACK && KPE_B128_PER_ROW < 8) ? SwizzleMode::k64B : SwizzleMode::k128B;
  using MainloopPipeline = cutlass::PipelineAsync<NUM_STAGES_>;
  using SharedStorage =
      HopperSharedStorageQKVO<MainloopPipeline, NUM_STAGES_, CTA_TILE_Q_, CTA_TILE_KV_,
                              HEAD_DIM_CKV_, HEAD_DIM_KPE_, DTypeQ_, DTypeKV_, DTypeO_>;
};

template <typename KTraits>
__device__ __forceinline__ void init_states_(float* o_frag, float* m, float* d, float* o_scale) {
#pragma unroll
  for (uint32_t reg_id = 0; reg_id < KTraits::NUM_REGS_O_FRAG; ++reg_id) {
    o_frag[reg_id] = 0.f;
  }

#pragma unroll
  for (uint32_t j = 0; j < 2; ++j) {
    m[j] = -math::inf;
    d[j] = 1.f;
    o_scale[j] = 1.f;
  }
}

template <typename KTraits>
__device__ __forceinline__ void load_q(
    typename KTraits::SharedStorage* smem_storage, typename KTraits::DTypeQ* q_nope,
    typename KTraits::DTypeQ* q_pe, const uint32_t q_nope_stride_n, const uint32_t q_nope_stride_h,
    const uint32_t q_pe_stride_n, const uint32_t q_pe_stride_h, const uint32_t q_len,
    const uint32_t packed_offset, const uint_fastdiv& num_heads) {
  using DTypeQ = typename KTraits::DTypeQ;
  constexpr uint32_t UPCAST_STRIDE_Q_NOPE = KTraits::UPCAST_STRIDE_Q_NOPE;
  constexpr uint32_t UPCAST_STRIDE_Q_PE = KTraits::UPCAST_STRIDE_Q_PE;
  constexpr uint32_t NUM_MMA_D_CKV = KTraits::NUM_MMA_D_CKV;
  constexpr uint32_t NUM_MMA_D_KPE = KTraits::NUM_MMA_D_KPE;
  const uint32_t lane_idx = cutlass::canonical_lane_idx();
  const uint32_t warp_group_idx = cutlass::canonical_warp_group_idx();
  const uint32_t warp_idx_in_wg = cutlass::canonical_warp_idx() % 4;

  smem_t<KTraits::SWIZZLE_MODE_Q_NOPE> q_smem_nope(smem_storage->q_smem.nope);
  smem_t<KTraits::SWIZZLE_MODE_Q_PE> q_smem_pe(smem_storage->q_smem.pe);

#pragma unroll
  for (uint32_t mma_q = 0; mma_q < 2; ++mma_q) {
#pragma unroll
    for (uint32_t j = 0; j < 2; ++j) {
      uint32_t q, r;
      num_heads.divmod(packed_offset + lane_idx / 8 + (j + mma_q * 2) * 16 + warp_idx_in_wg * 4, q,
                       r);
      DTypeQ* q_nope_ptr = q_nope + q * q_nope_stride_n + r * q_nope_stride_h +
                           (lane_idx % 8) * upcast_size<DTypeQ>();
      DTypeQ* q_pe_ptr =
          q_pe + q * q_pe_stride_n + r * q_pe_stride_h + (lane_idx % 8) * upcast_size<DTypeQ>();
      uint32_t q_smem_nope_offset_w =
          get_swizzle_offset<KTraits::SWIZZLE_MODE_Q_NOPE, UPCAST_STRIDE_Q_NOPE>(
              32 * mma_q + j * 16 + warp_idx_in_wg * 4 + lane_idx / 8, 8 * 0 + lane_idx % 8);
      uint32_t q_smem_pe_offset_w =
          get_swizzle_offset<KTraits::SWIZZLE_MODE_Q_PE, UPCAST_STRIDE_Q_PE>(
              32 * mma_q + j * 16 + warp_idx_in_wg * 4 + lane_idx / 8, 8 * 0 + lane_idx % 8);

#pragma unroll
      for (uint32_t mma_d = 0; mma_d < KTraits::NUM_MMA_D_CKV / 4; ++mma_d) {
        q_smem_nope.load_128b_async<SharedMemFillMode::kFillZero>(q_smem_nope_offset_w, q_nope_ptr,
                                                                  q < q_len);
        q_smem_nope_offset_w += 64;
        q_nope_ptr += 8 * upcast_size<DTypeQ>();
      }
#pragma unroll
      for (uint32_t mma_d = 0; mma_d < KTraits::NUM_MMA_D_KPE / 4; ++mma_d) {
        q_smem_pe.load_128b_async<SharedMemFillMode::kFillZero>(q_smem_pe_offset_w, q_pe_ptr,
                                                                q < q_len);
        q_smem_pe_offset_w += 64;
        q_pe_ptr += 8 * upcast_size<DTypeQ>();
      }
    }
  }
}

template <typename KTraits>
__device__ __forceinline__ void prefetch_offset(
    const uint32_t packed_block_iter_base, const uint32_t packed_kv_bound,
    const uint32_t ckv_stride_page, const uint32_t ckv_stride_n, const uint32_t kpe_stride_page,
    const uint32_t kpe_stride_n, const uint_fastdiv& block_size, typename KTraits::IdType* indices,
    int64_t (*ckv_offset)[2], int64_t (*kpe_offset)[2]) {
  using DTypeKV = typename KTraits::DTypeKV;
  const uint32_t lane_idx = cutlass::canonical_lane_idx();
  const uint32_t warp_idx_in_wg = cutlass::canonical_warp_idx() % 4;
#pragma unroll
  for (uint32_t mma_kv = 0; mma_kv < KTraits::NUM_MMA_KV / 2; ++mma_kv) {
#pragma unroll
    for (uint32_t j = 0; j < 2; ++j) {
      uint32_t q, r;
      uint32_t packed_block_iter =
          packed_block_iter_base + lane_idx / 8 + (j + mma_kv * 2) * 16 + warp_idx_in_wg * 4;
      block_size.divmod(packed_block_iter, q, r);
      // Widen page index to int64_t before multiplying to avoid overflow.
      ckv_offset[mma_kv][j] =
          static_cast<int64_t>(packed_block_iter < packed_kv_bound ? indices[q] : 0) *
              ckv_stride_page +
          r * ckv_stride_n + (lane_idx % 8) * upcast_size<DTypeKV>();
      kpe_offset[mma_kv][j] =
          static_cast<int64_t>(packed_block_iter < packed_kv_bound ? indices[q] : 0) *
              kpe_stride_page +
          r * kpe_stride_n + (lane_idx % 8) * upcast_size<DTypeKV>();
    }
  }
}

template <bool predicate, typename KTraits>
__device__ __forceinline__ void load_kv(typename KTraits::SharedStorage* smem_storage,
                                        typename KTraits::DTypeKV* ckv,
                                        typename KTraits::DTypeKV* kpe,
                                        const uint32_t packed_kv_bound,
                                        const uint32_t packed_block_iter_base,
                                        const uint32_t stage_idx, int64_t (*ckv_offset)[2],
                                        int64_t (*kpe_offset)[2]) {
  using DTypeKV = typename KTraits::DTypeKV;
  constexpr uint32_t UPCAST_STRIDE_CKV = KTraits::UPCAST_STRIDE_CKV;
  constexpr uint32_t UPCAST_STRIDE_KPE = KTraits::UPCAST_STRIDE_KPE;
  constexpr uint32_t NUM_MMA_D_CKV = KTraits::NUM_MMA_D_CKV;
  constexpr uint32_t NUM_MMA_D_KPE = KTraits::NUM_MMA_D_KPE;
  const uint32_t lane_idx = cutlass::canonical_lane_idx();
  const uint32_t warp_idx_in_wg = cutlass::canonical_warp_idx() % 4;

  smem_t<KTraits::SWIZZLE_MODE_CKV> ckv_smem(smem_storage->kv_o_smem[stage_idx].ckv);
  // Raw FP8 KPE storage uses SWIZZLE_MODE_KPE_RAW (k64B for FP8, k128B for
  // BF16). Mismatch with the WGMMA-side k128B layout in BF16 staging is fine
  // because the FP8 raw storage is only ever read by the consumer-side
  // repack_fp8_kv_to_bf16 helper that uses the same SWIZZLE_MODE_KPE_RAW.
  smem_t<KTraits::SWIZZLE_MODE_KPE_RAW> kpe_smem(smem_storage->kv_o_smem[stage_idx].kpe);

#pragma unroll
  for (uint32_t mma_kv = 0; mma_kv < KTraits::NUM_MMA_KV / 2; ++mma_kv) {
#pragma unroll
    for (uint32_t j = 0; j < 2; ++j) {
      uint32_t packed_block_iter =
          packed_block_iter_base + lane_idx / 8 + (j + mma_kv * 2) * 16 + warp_idx_in_wg * 4;

      DTypeKV* ckv_ptr = ckv + ckv_offset[mma_kv][j];
      DTypeKV* kpe_ptr = kpe + kpe_offset[mma_kv][j];
      uint32_t ckv_smem_offset_w = get_swizzle_offset<KTraits::SWIZZLE_MODE_CKV, UPCAST_STRIDE_CKV>(
          32 * mma_kv + j * 16 + warp_idx_in_wg * 4 + lane_idx / 8, 8 * 0 + lane_idx % 8);
      uint32_t kpe_smem_offset_w =
          get_swizzle_offset<KTraits::SWIZZLE_MODE_KPE_RAW, UPCAST_STRIDE_KPE>(
              32 * mma_kv + j * 16 + warp_idx_in_wg * 4 + lane_idx / 8, 8 * 0 + lane_idx % 8);

#pragma unroll
      for (uint32_t mma_d = 0; mma_d < KTraits::INNER_LOADS_CKV; ++mma_d) {
        if constexpr (predicate) {
          ckv_smem.load_128b_async<SharedMemFillMode::kFillZero>(
              ckv_smem_offset_w, ckv_ptr, packed_block_iter < packed_kv_bound);
        } else {
          ckv_smem.load_128b_async(ckv_smem_offset_w, ckv_ptr);
        }
        ckv_smem_offset_w += 64;
        ckv_ptr += 8 * upcast_size<DTypeKV>();
      }

      // KPE: when HEAD_DIM_KPE is too narrow for 8 lanes per row (FP8 case with
      // HEAD_DIM_KPE=64 → 4 b128/row), gate the extra lanes off.
      if (lane_idx % 8 < KTraits::LANES_PER_ROW_KPE) {
#pragma unroll
        for (uint32_t mma_d = 0; mma_d < KTraits::INNER_LOADS_KPE; ++mma_d) {
          if constexpr (predicate) {
            kpe_smem.load_128b_async<SharedMemFillMode::kFillZero>(
                kpe_smem_offset_w, kpe_ptr, packed_block_iter < packed_kv_bound);
          } else {
            kpe_smem.load_128b_async(kpe_smem_offset_w, kpe_ptr);
          }
          kpe_smem_offset_w += 64;
          kpe_ptr += 8 * upcast_size<DTypeKV>();
        }
      }
    }
  }
}

// FP8 KV path: dequantize one tile of CKV/KPE from packed FP8 shmem buffers into
// BF16 staging buffers, applying per-tensor scales. The destination layout uses
// the same k128B swizzle as the BF16 path, so existing WGMMA descriptors work
// unchanged after pointing them at the staging buffers.
//
// Each thread reads one 16-byte chunk (16 FP8 elems) and writes two
// 16-byte chunks (8 BF16 elems each); see the FP8 dequant idiom in prefill.cuh.
template <typename KTraits>
__device__ __forceinline__ void repack_fp8_kv_to_bf16(
    typename KTraits::SharedStorage* smem_storage, const uint32_t stage_idx, float ckv_scale,
    float kpe_scale, const float* ckv_scale_arr = nullptr, uint32_t row_base = 0,
    const typename KTraits::IdType* kv_indices = nullptr, uint_fastdiv block_size = uint_fastdiv(),
    uint32_t packed_kv_bound = 0) {
  using DTypeKV = typename KTraits::DTypeKV;
  using DTypeQ = typename KTraits::DTypeQ;
  static_assert(std::is_same_v<DTypeKV, __nv_fp8_e4m3>,
                "repack_fp8_kv_to_bf16 only supports DTypeKV == __nv_fp8_e4m3");

  // Only consumer warpgroup does the dequant; producer wg early-returns.
  // The helper has no internal barriers, so this is safe — the surrounding
  // __syncthreads() at the call sites covers cross-wg synchronization.
  if (threadIdx.x < KTraits::NUM_COPY_THREADS) return;
  const uint32_t thread_id = threadIdx.x - KTraits::NUM_COPY_THREADS;
  const uint32_t num_threads = KTraits::NUM_QK_THREADS;

  constexpr uint32_t CTA_TILE_KV = KTraits::CTA_TILE_KV;
  constexpr uint32_t HEAD_DIM_CKV = KTraits::HEAD_DIM_CKV;
  constexpr uint32_t HEAD_DIM_KPE = KTraits::HEAD_DIM_KPE;
  // b128 column counts (16-byte chunks per row).
  constexpr uint32_t FP8_COLS_CKV = HEAD_DIM_CKV / upcast_size<DTypeKV>();
  constexpr uint32_t BF16_COLS_CKV = HEAD_DIM_CKV / upcast_size<DTypeQ>();
  constexpr uint32_t BF16_COLS_KPE = HEAD_DIM_KPE / upcast_size<DTypeQ>();
  constexpr uint32_t NUM_B128_CKV = CTA_TILE_KV * FP8_COLS_CKV;

  using packed2_t = std::conditional_t<std::is_same_v<DTypeQ, half>, half2, nv_bfloat162>;
  packed2_t ckv_scale_packed{static_cast<DTypeQ>(ckv_scale), static_cast<DTypeQ>(ckv_scale)};

  b128_t* src_ckv = (b128_t*)smem_storage->kv_o_smem[stage_idx].ckv;
  b128_t* dst_ckv = (b128_t*)smem_storage->ckv_bf16;
#pragma unroll
  for (uint32_t idx = thread_id; idx < NUM_B128_CKV; idx += num_threads) {
    uint32_t row = idx / FP8_COLS_CKV, col = idx % FP8_COLS_CKV;
    b128_t packed =
        src_ckv[get_swizzle_offset<KTraits::SWIZZLE_MODE_CKV, KTraits::UPCAST_STRIDE_CKV>(row,
                                                                                          col)];
    alignas(16) DTypeQ conv[16];
    vec_cast<DTypeQ, DTypeKV>::template cast<16>(conv, (DTypeKV*)&packed);
    if (ckv_scale_arr) {
      uint32_t page, off;
      block_size.divmod(row_base + row, page, off);
      uint32_t phys = (row_base + row) < packed_kv_bound ? kv_indices[page] * block_size + off : 0;
      float scale =
          ckv_scale_arr[phys * (HEAD_DIM_CKV / 128) + col / (128 / upcast_size<DTypeKV>())];
#pragma unroll
      for (uint32_t k = 0; k < 16; ++k) {
        conv[k] = static_cast<DTypeQ>(static_cast<float>(conv[k]) * scale);
      }
    } else {
#pragma unroll
      for (uint32_t k = 0; k < 8; ++k) {
        ((packed2_t*)&conv[0])[k] = __hmul2(((packed2_t*)&conv[0])[k], ckv_scale_packed);
      }
    }
    dst_ckv[get_swizzle_offset<KTraits::SWIZZLE_MODE_CKV, KTraits::UPCAST_STRIDE_CKV_BF16>(
        row, 2 * col)] = *(b128_t*)&conv[0];
    dst_ckv[get_swizzle_offset<KTraits::SWIZZLE_MODE_CKV, KTraits::UPCAST_STRIDE_CKV_BF16>(
        row, 2 * col + 1)] = *(b128_t*)&conv[8];
  }

  if constexpr (HEAD_DIM_KPE > 0) {
    constexpr uint32_t FP8_COLS_KPE = HEAD_DIM_KPE / upcast_size<DTypeKV>();
    constexpr uint32_t NUM_B128_KPE = CTA_TILE_KV * FP8_COLS_KPE;
    packed2_t kpe_scale_packed{static_cast<DTypeQ>(kpe_scale), static_cast<DTypeQ>(kpe_scale)};

    b128_t* src_kpe = (b128_t*)smem_storage->kv_o_smem[stage_idx].kpe;
    b128_t* dst_kpe = (b128_t*)smem_storage->kpe_bf16;
#pragma unroll
    for (uint32_t idx = thread_id; idx < NUM_B128_KPE; idx += num_threads) {
      uint32_t row = idx / FP8_COLS_KPE, col = idx % FP8_COLS_KPE;
      b128_t packed =
          src_kpe[get_swizzle_offset<KTraits::SWIZZLE_MODE_KPE_RAW, KTraits::UPCAST_STRIDE_KPE>(
              row, col)];
      alignas(16) DTypeQ conv[16];
      vec_cast<DTypeQ, DTypeKV>::template cast<16>(conv, (DTypeKV*)&packed);
#pragma unroll
      for (uint32_t k = 0; k < 8; ++k) {
        ((packed2_t*)&conv[0])[k] = __hmul2(((packed2_t*)&conv[0])[k], kpe_scale_packed);
      }
      dst_kpe[get_swizzle_offset<KTraits::SWIZZLE_MODE_KPE, KTraits::UPCAST_STRIDE_KPE_BF16>(
          row, 2 * col)] = *(b128_t*)&conv[0];
      dst_kpe[get_swizzle_offset<KTraits::SWIZZLE_MODE_KPE, KTraits::UPCAST_STRIDE_KPE_BF16>(
          row, 2 * col + 1)] = *(b128_t*)&conv[8];
    }
  }
}

template <typename KTraits>
__device__ __forceinline__ void compute_mla_qk(typename KTraits::SharedStorage* smem_storage,
                                               const uint32_t stage_idx, float* s_frag) {
  // After dequant, the BF16 staging buffers feed the WGMMA on the FP8 KV path;
  // otherwise the per-stage native BF16 buffers are used directly.
  using KVDescType = std::conditional_t<KTraits::USE_KV_REPACK, typename KTraits::DTypeQ,
                                        typename KTraits::DTypeKV>;
  auto* kpe_smem_ptr = KTraits::USE_KV_REPACK ? &smem_storage->kpe_bf16[0]
                                              : (KVDescType*)smem_storage->kv_o_smem[stage_idx].kpe;
  auto* ckv_smem_ptr = KTraits::USE_KV_REPACK ? &smem_storage->ckv_bf16[0]
                                              : (KVDescType*)smem_storage->kv_o_smem[stage_idx].ckv;

  auto desc_q_pe =
      make_smem_desc<KTraits::SWIZZLE_MODE_Q_PE, /*leading_byte_offset=*/16,
                     /*stride_byte_offset=*/KTraits::HEAD_DIM_KPE * 16, typename KTraits::DTypeQ>(
          smem_storage->q_smem.pe);
  auto desc_k_pe =
      make_smem_desc<KTraits::SWIZZLE_MODE_KPE, /*leading_byte_offset=*/16,
                     /*stride_byte_offset=*/KTraits::HEAD_DIM_KPE * 16, KVDescType>(kpe_smem_ptr);
  using wgmma = WGMMA_ASYNC_SS<KVDescType, float, 64, KTraits::CTA_TILE_KV, 16, Major::K, Major::K,
                               ScaleIn::One, ScaleIn::One>;

  warpgroup_fence_frag<KTraits::NUM_REGS_S_FRAG>(s_frag);
  warpgroup_arrive();
#pragma unroll
  for (uint32_t mma_d_pe = 0; mma_d_pe < KTraits::NUM_MMA_D_KPE; ++mma_d_pe) {
    if (mma_d_pe == 0) {
      wgmma::op</*init=*/true>(desc_q_pe, desc_k_pe, s_frag);
    } else {
      wgmma::op</*init=*/false>(desc_q_pe, desc_k_pe, s_frag);
    }
    if ((mma_d_pe + 1) % 4 == 0) {
      desc_q_pe += 64 - 6;
      desc_k_pe += 64 - 6;
    } else {
      desc_q_pe += 2;
      desc_k_pe += 2;
    }
  }

  auto desc_q_nope =
      make_smem_desc<KTraits::SWIZZLE_MODE_Q_NOPE, /*leading_byte_offset=*/16,
                     /*stride_byte_offset=*/KTraits::HEAD_DIM_CKV * 16, typename KTraits::DTypeQ>(
          smem_storage->q_smem.nope);
  auto desc_ckv =
      make_smem_desc<KTraits::SWIZZLE_MODE_CKV, /*leading_byte_offset=*/16,
                     /*stride_byte_offset=*/KTraits::HEAD_DIM_CKV * 16, KVDescType>(ckv_smem_ptr);

  if constexpr (KTraits::NUM_MMA_D_KPE == 0) {
    wgmma::op</*init=*/true>(desc_q_nope, desc_ckv, s_frag);
    desc_q_nope += 2;
    desc_ckv += 2;
  }
#pragma unroll
  for (uint32_t mma_d_ckv = KTraits::NUM_MMA_D_KPE == 0 ? 1 : 0; mma_d_ckv < KTraits::NUM_MMA_D_CKV;
       ++mma_d_ckv) {
    wgmma::op</*init=*/false>(desc_q_nope, desc_ckv, s_frag);
    if ((mma_d_ckv + 1) % 4 == 0) {
      desc_q_nope += 64 - 6;
      desc_ckv += 64 - 6;
    } else {
      desc_q_nope += 2;
      desc_ckv += 2;
    }
  }

  warpgroup_commit_batch();
  warpgroup_fence_frag<KTraits::NUM_REGS_S_FRAG>(s_frag);
}

template <typename KTraits>
__device__ __forceinline__ void compute_mla_pv(typename KTraits::SharedStorage* smem_storage,
                                               const uint32_t stage_idx, float* o_frag) {
  const uint32_t lane_idx = cutlass::canonical_lane_idx();
  const uint32_t warp_idx_in_wg = cutlass::canonical_warp_idx() % 4;
  const uint32_t warp_group_idx = cutlass::canonical_warp_group_idx();

  // P is always DTypeQ-typed; V (= CKV) comes from the BF16 staging buffer on
  // the FP8 KV path and from the native BF16 per-stage buffer otherwise.
  using KVDescType = std::conditional_t<KTraits::USE_KV_REPACK, typename KTraits::DTypeQ,
                                        typename KTraits::DTypeKV>;
  KVDescType* ckv_base = KTraits::USE_KV_REPACK
                             ? (KVDescType*)smem_storage->ckv_bf16
                             : (KVDescType*)smem_storage->kv_o_smem[stage_idx].ckv;

  auto desc_p =
      make_smem_desc<KTraits::SWIZZLE_MODE_P, /*leading_byte_offset=*/16,
                     /*stride_byte_offset=*/KTraits::CTA_TILE_KV * 16, typename KTraits::DTypeQ>(
          smem_storage->kv_o_smem[stage_idx].p);
  auto desc_ckv = make_smem_desc<KTraits::SWIZZLE_MODE_CKV,
                                 /*leading_byte_offset=*/KTraits::CTA_TILE_KV * 16,
                                 /*stride_byte_offset=*/KTraits::HEAD_DIM_CKV * 16, KVDescType>(
      ckv_base + warp_group_idx * 8 * (KTraits::HEAD_DIM_CKV / 2));
  warpgroup_fence_frag<KTraits::NUM_REGS_O_FRAG>(o_frag);
  warpgroup_arrive();
  using wgmma = WGMMA_ASYNC_SS<KVDescType, float, 64, KTraits::HEAD_DIM_CKV / 2, 16, Major::K,
                               Major::MN, ScaleIn::One, ScaleIn::One>;

#pragma unroll
  for (uint32_t mma_kv = 0; mma_kv < KTraits::NUM_MMA_KV; ++mma_kv) {
    wgmma::op</*init=*/false>(desc_p, desc_ckv, o_frag);
    desc_p += 2;
    desc_ckv += 1024;
  }
  warpgroup_commit_batch();
  warpgroup_fence_frag<KTraits::NUM_REGS_O_FRAG>(o_frag);
}

template <typename KTraits>
__device__ __forceinline__ void logits_mask_(const uint32_t qo_packed_idx_base,
                                             const uint32_t kv_idx_base, const uint32_t qo_len,
                                             const uint32_t kv_len, const uint32_t kv_end,
                                             const uint_fastdiv num_heads, float* s_frag) {
  const uint32_t lane_idx = cutlass::canonical_lane_idx();
  const uint32_t warp_idx_in_wg = cutlass::canonical_warp_idx() % 4;
  constexpr uint32_t NUM_MMA_KV = KTraits::NUM_MMA_KV;
  uint32_t q[2];
#pragma unroll
  for (uint32_t j = 0; j < 2; ++j) {
    q[j] = (qo_packed_idx_base + warp_idx_in_wg * 16 + lane_idx / 4 + 8 * j) / num_heads;
  }

#pragma unroll
  for (uint32_t reg_id = 0; reg_id < KTraits::NUM_REGS_S_FRAG; ++reg_id) {
    const uint32_t q_idx = q[(reg_id % 4) / 2],
                   kv_idx = kv_idx_base + 2 * (lane_idx % 4) + 8 * (reg_id / 4) + reg_id % 2;
    const bool mask = (!(KTraits::CAUSAL ? (kv_idx + qo_len > kv_len + q_idx || (kv_idx >= kv_end))
                                         : kv_idx >= kv_end));
    s_frag[reg_id] = (mask) ? s_frag[reg_id] : (KTraits::MaskFillValue);
  }
}

template <typename KTraits>
__device__ __forceinline__ void rescale_o_(float* o_scale, float* o_frag) {
  const uint32_t lane_idx = cutlass::canonical_lane_idx();
  const uint32_t warp_idx_in_wg = cutlass::canonical_warp_idx() % 4;
#pragma unroll
  for (uint32_t reg_id = 0; reg_id < KTraits::NUM_REGS_O_FRAG; ++reg_id) {
    o_frag[reg_id] *= o_scale[(reg_id % 4) / 2];
  }
}

template <typename KTraits>
__device__ __forceinline__ void update_md_(typename KTraits::SharedStorage* smem_storage,
                                           typename KTraits::AttentionVariant variant,
                                           float* s_frag, float* m, float* d, float* o_scale) {
  using AttentionVariant = typename KTraits::AttentionVariant;
  const float sm_scale = variant.sm_scale_log2;
  const uint32_t lane_idx = cutlass::canonical_lane_idx();
  const uint32_t warp_idx_in_wg = cutlass::canonical_warp_idx() % 4;
  float m_prev[2];
#pragma unroll
  for (uint32_t j = 0; j < 2; ++j) {
    m_prev[j] = m[j];
#pragma unroll
    for (uint32_t k = 0; k < KTraits::NUM_REGS_S_FRAG / 4; ++k) {
      float m_local = max(s_frag[k * 4 + j * 2 + 0], s_frag[k * 4 + j * 2 + 1]);
      m[j] = max(m[j], m_local);
    }
    m[j] = max(m[j], math::shfl_xor_sync(m[j], 0x2));
    m[j] = max(m[j], math::shfl_xor_sync(m[j], 0x1));
  }

#pragma unroll
  for (uint32_t j = 0; j < 2; ++j) {
    // Per-row clamp for fully masked rows (see update_mdo_states in prefill.cuh).
    const float m_scaled = max(m[j] * sm_scale, -cuda::std::numeric_limits<float>::max());
    o_scale[j] = math::ptx_exp2(m_prev[j] * sm_scale - m_scaled);
    float d_local = 0.f;
#pragma unroll
    for (uint32_t k = 0; k < KTraits::NUM_REGS_S_FRAG / 4; ++k) {
      s_frag[k * 4 + j * 2 + 0] = math::ptx_exp2(s_frag[k * 4 + j * 2 + 0] * sm_scale - m_scaled);
      s_frag[k * 4 + j * 2 + 1] = math::ptx_exp2(s_frag[k * 4 + j * 2 + 1] * sm_scale - m_scaled);

      d_local += s_frag[k * 4 + j * 2 + 0] + s_frag[k * 4 + j * 2 + 1];
    }
    d[j] = d[j] * o_scale[j] + d_local;
  }
}

template <typename KTraits>
__device__ __forceinline__ void write_p_rmem_smem(typename KTraits::SharedStorage* smem_storage,
                                                  const uint32_t stage_idx, uint32_t* p_frag) {
  // P is DTypeQ-typed, so use UPCAST_STRIDE_P_BF16 (BF16 elements per b128) for
  // the swizzle offset on both the BF16 and FP8 KV paths.
  static constexpr uint32_t NUM_MMA_KV = KTraits::NUM_MMA_KV;
  const uint32_t lane_idx = cutlass::canonical_lane_idx();
  const uint32_t warp_idx_in_wg = cutlass::canonical_warp_idx() % 4;
  smem_t<KTraits::SWIZZLE_MODE_P> p_smem(smem_storage->kv_o_smem[stage_idx].p);
#pragma unroll
  for (uint32_t mma_kv = 0; mma_kv < NUM_MMA_KV; ++mma_kv) {
    uint32_t p_smem_offset_w =
        get_swizzle_offset<KTraits::SWIZZLE_MODE_P, KTraits::UPCAST_STRIDE_P_BF16>(
            warp_idx_in_wg * 16 + lane_idx % 16, mma_kv * 2 + lane_idx / 16);
    p_smem.stmatrix_m8n8x4(p_smem_offset_w, p_frag + mma_kv * 4);
  }
}

template <typename KTraits>
__device__ __forceinline__ void normalize_d_(typename KTraits::SharedStorage* smem_storage,
                                             float* o_frag, float* m, float* d) {
  float d_rcp[2];
  // compute reciprocal of d
#pragma unroll
  for (uint32_t j = 0; j < 2; ++j) {
    d_rcp[j] = (m[j] != -math::inf) ? math::ptx_rcp(d[j]) : 0.f;
  }

#pragma unroll
  for (uint32_t reg_id = 0; reg_id < KTraits::NUM_REGS_O_FRAG; ++reg_id) {
    o_frag[reg_id] = o_frag[reg_id] * d_rcp[(reg_id % 4) / 2];
  }
}

template <bool write_lse, typename KTraits>
__device__ __forceinline__ void write_o(
    typename KTraits::SharedStorage* smem_storage, typename KTraits::DTypeO* final_o,
    float* final_lse, typename KTraits::DTypeO* partial_o, float* partial_lse, float(*o_frag),
    float* m, float* d, const uint32_t o_stride_n, const uint32_t o_stride_h, const uint32_t q_len,
    const uint32_t packed_offset, const uint_fastdiv& num_heads, const bool& return_lse_base_on_e) {
  using DTypeO = typename KTraits::DTypeO;
  constexpr uint32_t NUM_MMA_D_CKV = KTraits::NUM_MMA_D_CKV;
  constexpr uint32_t HEAD_DIM_CKV = KTraits::HEAD_DIM_CKV;
  constexpr uint32_t UPCAST_STRIDE_FINAL_O = KTraits::UPCAST_STRIDE_FINAL_O;
  const uint32_t lane_idx = cutlass::canonical_lane_idx();
  const uint32_t warp_group_idx = cutlass::canonical_warp_group_idx();
  const uint32_t warp_idx_in_wg = cutlass::canonical_warp_idx() % 4;
  // o is a top-level (not per-stage) shmem region. Both warpgroups write
  // disjoint halves of the same buffer, offset by warp_group_idx * NUM_MMA_D_CKV.
  smem_t<KTraits::SWIZZLE_MODE_O> o_smem;
  o_smem = smem_storage->o;

  // step 0. rmem to smem
#pragma unroll
  for (uint32_t k = 0; k < HEAD_DIM_CKV / 32; ++k) {
    uint32_t o_frag_f16[8 / 2];
    vec_cast<DTypeO, float>::cast<8>((DTypeO*)o_frag_f16, &o_frag[k * 8]);
    uint32_t o_smem_offset_w = get_swizzle_offset<KTraits::SWIZZLE_MODE_O, UPCAST_STRIDE_FINAL_O>(
        warp_idx_in_wg * 16 + lane_idx % 16,
        warp_group_idx * NUM_MMA_D_CKV + k * 2 + lane_idx / 16);
    o_smem.template stmatrix_m8n8x4(o_smem_offset_w, o_frag_f16);
  }

  if (partial_o != nullptr) {
    // NOTE(Zihao): o_smem is not used if write to partial_o, and we can avoid the barrier
    // write to partial_o

#pragma unroll
    for (uint32_t j = 0; j < 4; ++j) {
      uint32_t q_idx = (packed_offset + warp_idx_in_wg * 16 + 4 * j + lane_idx / 8) / num_heads;
      DTypeO* o_partial_ptr =
          partial_o +
          ((blockIdx.x * 4 + warp_idx_in_wg) * 16 + 4 * j + lane_idx / 8) * HEAD_DIM_CKV +
          warp_group_idx * (HEAD_DIM_CKV / 2) + (lane_idx % 8) * upcast_size<DTypeO>();
      uint32_t o_smem_offset_w = get_swizzle_offset<KTraits::SWIZZLE_MODE_O, UPCAST_STRIDE_FINAL_O>(
          warp_idx_in_wg * 16 + 4 * j + lane_idx / 8,
          warp_group_idx * NUM_MMA_D_CKV + lane_idx % 8);
#pragma unroll
      for (uint32_t k = 0; k < HEAD_DIM_CKV / 128; ++k) {
        if (q_idx < q_len) {
          o_smem.template store_128b(o_smem_offset_w, o_partial_ptr);
        }
        o_partial_ptr += 8 * upcast_size<DTypeO>();
        o_smem_offset_w += 64;
      }
    }

    if constexpr (write_lse) {
#pragma unroll
      for (uint32_t j = 0; j < 2; ++j) {
        uint32_t q_idx = (packed_offset + warp_idx_in_wg * 16 + 8 * j + lane_idx / 4) / num_heads;
        if (lane_idx % 4 == 0 && q_idx < q_len) {
          float lse = (m[j] == -math::inf) ? -cuda::std::numeric_limits<float>::infinity()
                                           : math::ptx_log2(d[j]) + float(m[j]);
          partial_lse[(blockIdx.x * 4 + warp_idx_in_wg) * 16 + 8 * j + lane_idx / 4] = lse;
        }
      }
    }
  } else {
    // write to final_o

// step 1. smem to gmem
#pragma unroll
    for (uint32_t j = 0; j < 4; ++j) {
      uint32_t q, r;
      num_heads.divmod(packed_offset + warp_idx_in_wg * 16 + 4 * j + lane_idx / 8, q, r);
      DTypeO* o_final_ptr = final_o + q * o_stride_n + r * o_stride_h +
                            warp_group_idx * (HEAD_DIM_CKV / 2) +
                            (lane_idx % 8) * upcast_size<DTypeO>();
      uint32_t o_smem_offset_w = get_swizzle_offset<KTraits::SWIZZLE_MODE_O, UPCAST_STRIDE_FINAL_O>(
          warp_idx_in_wg * 16 + 4 * j + lane_idx / 8,
          warp_group_idx * NUM_MMA_D_CKV + lane_idx % 8);
#pragma unroll
      for (uint32_t k = 0; k < HEAD_DIM_CKV / 128; ++k) {
        if (q < q_len) {
          o_smem.template store_128b(o_smem_offset_w, o_final_ptr);
        }
        o_final_ptr += 8 * upcast_size<DTypeO>();
        o_smem_offset_w += 64;
      }
    }

    if constexpr (write_lse) {
      if (final_lse) {
#pragma unroll
        for (uint32_t j = 0; j < 2; ++j) {
          uint32_t q, r;
          num_heads.divmod(packed_offset + warp_idx_in_wg * 16 + 8 * j + lane_idx / 4, q, r);
          if (lane_idx % 4 == 0 && q < q_len) {
            final_lse[q * num_heads + r] = (m[j] == -math::inf)
                                               ? -cuda::std::numeric_limits<float>::infinity()
                                               : math::ptx_log2(d[j]) + float(m[j]);
            if (return_lse_base_on_e) {
              final_lse[q * num_heads + r] *= math::loge2;
            }
          }
        }
      }
    }
  }
}

template <typename Params>
__device__ __forceinline__ auto get_block_coord(const Params& params, const uint32_t work_idx) {
  return std::tuple(params.q_indptr[work_idx], params.kv_indptr[work_idx],
                    params.partial_indptr[work_idx], params.q_len[work_idx],
                    params.kv_len[work_idx], params.q_start[work_idx], params.kv_start[work_idx],
                    params.kv_end[work_idx]);
}

template <typename KTraits>
__device__ __forceinline__ void convert_s_to_p(float* s_frag, uint32_t* p_frag) {
  // P is always DTypeQ-typed in shmem (see HopperSharedStorageQKVO::kv_o_smem.p)
  // so the PV WGMMA can run as BF16xBF16 on the FP8 KV path. On the BF16 KV
  // path DTypeQ == DTypeKV so this is unchanged.
#pragma unroll
  for (uint32_t i = 0; i < KTraits::NUM_REGS_S_FRAG / 8; ++i) {
    vec_cast<typename KTraits::DTypeQ, float>::cast<8>(((typename KTraits::DTypeQ*)p_frag) + i * 8,
                                                       s_frag + i * 8);
  }
}

template <typename KTraits>
__device__ __forceinline__ void write_o_scale_smem(typename KTraits::SharedStorage* smem_storage,
                                                   float* o_scale) {
  const uint32_t lane_idx = cutlass::canonical_lane_idx();
  const uint32_t warp_idx_in_wg = cutlass::canonical_warp_idx() % 4;
#pragma unroll
  for (uint32_t j = 0; j < 2; ++j) {
    if (lane_idx % 4 == 0) {
      smem_storage->o_scale[warp_idx_in_wg * 16 + j * 8 + lane_idx / 4] = o_scale[j];
    }
  }
}

template <typename KTraits>
__device__ __forceinline__ void load_o_scale_smem(typename KTraits::SharedStorage* smem_storage,
                                                  float* o_scale) {
  const uint32_t lane_idx = cutlass::canonical_lane_idx();
  const uint32_t warp_idx_in_wg = cutlass::canonical_warp_idx() % 4;
#pragma unroll
  for (uint32_t j = 0; j < 2; ++j) {
    o_scale[j] = smem_storage->o_scale[warp_idx_in_wg * 16 + j * 8 + lane_idx / 4];
  }
}

template <typename KTraits, typename Params>
__global__ __launch_bounds__(KTraits::NUM_THREADS) void BatchMLAPageAttentionHopperKernel(
    const __grid_constant__ Params params) {
  using DTypeQ = typename Params::DTypeQ;
  using DTypeKV = typename Params::DTypeKV;
  using DTypeO = typename Params::DTypeO;
  using IdType = typename Params::IdType;

  extern __shared__ __align__(alignof(typename KTraits::SharedStorage)) uint8_t smem[];
  auto& smem_storage = reinterpret_cast<typename KTraits::SharedStorage&>(smem);

  typename KTraits::AttentionVariant variant(params, blockIdx.y, smem);
  [[maybe_unused]] constexpr SwizzleMode SWIZZLE_MODE_Q_NOPE = KTraits::SWIZZLE_MODE_Q_NOPE;
  [[maybe_unused]] constexpr SwizzleMode SWIZZLE_MODE_Q_PE = KTraits::SWIZZLE_MODE_Q_PE;
  [[maybe_unused]] constexpr SwizzleMode SWIZZLE_MODE_CKV = KTraits::SWIZZLE_MODE_CKV;
  [[maybe_unused]] constexpr SwizzleMode SWIZZLE_MODE_KPE = KTraits::SWIZZLE_MODE_KPE;
  [[maybe_unused]] constexpr uint32_t NUM_MMA_KV = KTraits::NUM_MMA_KV;
  [[maybe_unused]] constexpr uint32_t NUM_MMA_D_CKV = KTraits::NUM_MMA_D_CKV;
  [[maybe_unused]] constexpr uint32_t HEAD_DIM_CKV = KTraits::HEAD_DIM_CKV;
  [[maybe_unused]] constexpr uint32_t CTA_TILE_Q = KTraits::CTA_TILE_Q;
  [[maybe_unused]] constexpr uint32_t CTA_TILE_KV = KTraits::CTA_TILE_KV;
  [[maybe_unused]] constexpr int32_t NUM_STAGES = KTraits::NUM_STAGES;
  [[maybe_unused]] constexpr uint32_t NUM_COPY_THREADS = KTraits::NUM_COPY_THREADS;
  [[maybe_unused]] constexpr bool CAUSAL = KTraits::CAUSAL;

  DTypeQ* q_nope = params.q_nope;
  DTypeQ* q_pe = params.q_pe;
  DTypeKV* ckv = params.ckv;
  DTypeKV* kpe = params.kpe;
  IdType* kv_indices = params.kv_indices;
  DTypeO* partial_o = params.partial_o;
  float* partial_lse = params.partial_lse;
  DTypeO* final_o = params.final_o;
  float* final_lse = params.final_lse;
  IdType* work_indptr = params.work_indptr;

  const uint_fastdiv& num_heads = params.num_heads;
  const uint_fastdiv& block_size = params.block_size;
  const uint32_t q_nope_stride_n = params.q_nope_stride_n;
  const uint32_t q_nope_stride_h = params.q_nope_stride_h;
  const uint32_t q_pe_stride_n = params.q_pe_stride_n;
  const uint32_t q_pe_stride_h = params.q_pe_stride_h;
  const uint32_t ckv_stride_page = params.ckv_stride_page;
  const uint32_t ckv_stride_n = params.ckv_stride_n;
  const uint32_t kpe_stride_page = params.kpe_stride_page;
  const uint32_t kpe_stride_n = params.kpe_stride_n;
  const uint32_t o_stride_n = params.o_stride_n;
  const uint32_t o_stride_h = params.o_stride_h;
  const uint32_t cluster_tile_q = gridDim.x * KTraits::CTA_TILE_Q;

  const uint32_t lane_predicate = cute::elect_one_sync();
  const uint32_t lane_idx = cutlass::canonical_lane_idx();
  const uint32_t warp_group_idx = cutlass::canonical_warp_group_idx();
  const uint32_t warp_idx = cutlass::canonical_warp_idx();
  const uint32_t warp_idx_in_wg = cutlass::canonical_warp_idx() % 4;

  PROFILER_INIT(params, smem_storage, variant, warp_group_idx, 2, (threadIdx.x % 128 == 0));

  using MainloopPipeline = typename KTraits::MainloopPipeline;
  using PipelineParams = typename MainloopPipeline::Params;
  using PipelineState = typename MainloopPipeline::PipelineState;
  PipelineParams pipeline_params;
  pipeline_params.role = warp_group_idx == 0 ? MainloopPipeline::ThreadCategory::Producer
                                             : MainloopPipeline::ThreadCategory::Consumer;
  pipeline_params.producer_arv_count = 128;
  pipeline_params.consumer_arv_count = 128;
  MainloopPipeline pipeline_q(smem_storage.pipeline_q, pipeline_params);
  pipeline_params.role = warp_group_idx == 0 ? MainloopPipeline::ThreadCategory::ProducerConsumer
                                             : MainloopPipeline::ThreadCategory::Consumer;
  pipeline_params.producer_arv_count = 128;
  pipeline_params.consumer_arv_count = 256;
  MainloopPipeline pipeline_kv(smem_storage.pipeline_kv, pipeline_params);

  __syncthreads();
  alignas(16) float o_frag[KTraits::NUM_REGS_O_FRAG];
  float m[2];
  float d[2];
  float o_scale[2];
  auto consumer_wait = [](auto& pipeline, auto& smem_pipe_read) {
    auto barrier_token = pipeline.consumer_try_wait(smem_pipe_read);
    pipeline.consumer_wait(smem_pipe_read, barrier_token);
  };

  if (warp_group_idx == 0) {
    // load q & kv, compute pv1
    PipelineState smem_pipe_write_q = cutlass::make_producer_start_state<MainloopPipeline>();
    PipelineState smem_pipe_write_kv = cutlass::make_producer_start_state<MainloopPipeline>();
    PipelineState smem_pipe_read_kv;

    int64_t ckv_offset[KTraits::NUM_MMA_KV / 2][2];
    int64_t kpe_offset[KTraits::NUM_MMA_KV / 2][2];

#pragma unroll 1
    for (IdType work_idx = work_indptr[blockIdx.y]; work_idx < work_indptr[blockIdx.y + 1];
         ++work_idx) {
      auto [q_indptr, kv_indptr, partial_indptr, q_len, kv_len, packed_qo_start, kv_start, kv_end] =
          get_block_coord(params, work_idx);

      init_states_<KTraits>(o_frag, m, d, o_scale);

      const uint32_t qo_packed_idx_base = packed_qo_start + blockIdx.x * KTraits::CTA_TILE_Q;
      const uint32_t qo_upperbound =
          min(q_len, ceil_div(qo_packed_idx_base + KTraits::CTA_TILE_Q, num_heads));

      uint32_t packed_kv_bound = kv_indptr * block_size + kv_len;
      int kv_tile_idx =
          ceil_div(
              (CAUSAL ? min(kv_end, kv_len - q_len + (packed_qo_start + cluster_tile_q) / num_heads)
                      : kv_end),
              CTA_TILE_KV) -
          1 - (kv_start / CTA_TILE_KV);

      bool has_kv = kv_tile_idx >= 0;

      const uint32_t block_iter_base = kv_indptr * block_size + kv_start;

      if (has_kv) {
        prefetch_offset<KTraits>(block_iter_base + kv_tile_idx * CTA_TILE_KV, packed_kv_bound,
                                 ckv_stride_page, ckv_stride_n, kpe_stride_page, kpe_stride_n,
                                 block_size, kv_indices, ckv_offset, kpe_offset);
        pipeline_kv.producer_acquire(smem_pipe_write_kv);
        PROFILER_EVENT_START(variant, ProfileEventType::kIssueLoadKV);
        load_kv<true, KTraits>(&smem_storage, ckv, kpe, packed_kv_bound,
                               block_iter_base + kv_tile_idx * CTA_TILE_KV,
                               smem_pipe_write_kv.index(), ckv_offset, kpe_offset);
        PROFILER_EVENT_END(variant, ProfileEventType::kIssueLoadKV);
        pipeline_kv.producer_commit(smem_pipe_write_kv, cutlass::arch::cpasync_barrier_arrive);
        kv_tile_idx -= 1;
        ++smem_pipe_write_kv;
        if (kv_tile_idx >= 0) {
          prefetch_offset<KTraits>(block_iter_base + kv_tile_idx * CTA_TILE_KV, packed_kv_bound,
                                   ckv_stride_page, ckv_stride_n, kpe_stride_page, kpe_stride_n,
                                   block_size, kv_indices, ckv_offset, kpe_offset);
        }
      }

      pipeline_q.producer_acquire(smem_pipe_write_q);
      PROFILER_EVENT_START(variant, ProfileEventType::kIssueLoadQ);
      load_q<KTraits>(&smem_storage, q_nope + q_indptr * q_nope_stride_n,
                      q_pe + q_indptr * q_pe_stride_n, q_nope_stride_n, q_nope_stride_h,
                      q_pe_stride_n, q_pe_stride_h, qo_upperbound, qo_packed_idx_base,
                      params.num_heads);
      PROFILER_EVENT_END(variant, ProfileEventType::kIssueLoadQ);
      pipeline_q.producer_commit(smem_pipe_write_q, cutlass::arch::cpasync_barrier_arrive);
      ++smem_pipe_write_q;

#pragma unroll 1
      for (; kv_tile_idx >= 0; --kv_tile_idx) {
        pipeline_kv.producer_acquire(smem_pipe_write_kv);
        PROFILER_EVENT_START(variant, ProfileEventType::kIssueLoadKV);
        load_kv<false, KTraits>(&smem_storage, ckv, kpe, packed_kv_bound,
                                block_iter_base + kv_tile_idx * CTA_TILE_KV,
                                smem_pipe_write_kv.index(), ckv_offset, kpe_offset);
        PROFILER_EVENT_END(variant, ProfileEventType::kIssueLoadKV);
        if (kv_tile_idx > 0) {
          prefetch_offset<KTraits>(block_iter_base + (kv_tile_idx - 1) * CTA_TILE_KV,
                                   packed_kv_bound, ckv_stride_page, ckv_stride_n, kpe_stride_page,
                                   kpe_stride_n, block_size, kv_indices, ckv_offset, kpe_offset);
        }
        pipeline_kv.producer_commit(smem_pipe_write_kv, cutlass::arch::cpasync_barrier_arrive);
        ++smem_pipe_write_kv;

        if constexpr (KTraits::USE_KV_REPACK) {
          // d1: pair with consumer's s1 (consumer is at __syncthreads_pre_dequant
          // right after its own consumer_wait). Producer participates as a
          // pass-through here — only the consumer warpgroup writes the BF16
          // staging buffer below.
          __syncthreads();
          // d2: pair with consumer's s2 (post-dequant). Once we cross this
          // barrier, the BF16 staging buffer has the current stage's data and
          // is safe to read in the PV WGMMA below.
          __syncthreads();
        }
        barrier_sync(KTraits::NUM_THREADS, NamedBarriers::kOScaleReady);
        load_o_scale_smem<KTraits>(&smem_storage, o_scale);
        PROFILER_EVENT_START(variant, ProfileEventType::kRescaleO);
        rescale_o_<KTraits>(o_scale, o_frag);
        PROFILER_EVENT_END(variant, ProfileEventType::kRescaleO);
        consumer_wait(pipeline_kv, smem_pipe_read_kv);
        __syncthreads();
        PROFILER_EVENT_START(variant, ProfileEventType::kGemmPV);
        compute_mla_pv<KTraits>(&smem_storage, smem_pipe_read_kv.index(), o_frag);
        warpgroup_wait<0>();
        PROFILER_EVENT_END(variant, ProfileEventType::kGemmPV);
        pipeline_kv.consumer_release(smem_pipe_read_kv);
        ++smem_pipe_read_kv;
        if constexpr (KTraits::USE_KV_REPACK) {
          // iter-end sync: ensures both wgs finished this iter's WGMMA reads
          // of the BF16 staging buffer before any wg overwrites it in the
          // next iter's dequant.
          __syncthreads();
        }
      }

      if (has_kv) {
        if constexpr (KTraits::USE_KV_REPACK) {
          // d1: pair with consumer's s1 (consumer is at __syncthreads_pre_dequant
          // right after its own consumer_wait). Producer participates as a
          // pass-through here — only the consumer warpgroup writes the BF16
          // staging buffer below.
          __syncthreads();
          // d2: pair with consumer's s2 (post-dequant). Once we cross this
          // barrier, the BF16 staging buffer has the current stage's data and
          // is safe to read in the PV WGMMA below.
          __syncthreads();
        }
        barrier_sync(KTraits::NUM_THREADS, NamedBarriers::kOScaleReady);
        load_o_scale_smem<KTraits>(&smem_storage, o_scale);
        PROFILER_EVENT_START(variant, ProfileEventType::kRescaleO);
        rescale_o_<KTraits>(o_scale, o_frag);
        PROFILER_EVENT_END(variant, ProfileEventType::kRescaleO);
        consumer_wait(pipeline_kv, smem_pipe_read_kv);
        __syncthreads();
        PROFILER_EVENT_START(variant, ProfileEventType::kGemmPV);
        compute_mla_pv<KTraits>(&smem_storage, smem_pipe_read_kv.index(), o_frag);
        warpgroup_wait<0>();
        PROFILER_EVENT_END(variant, ProfileEventType::kGemmPV);
        pipeline_kv.consumer_release(smem_pipe_read_kv);
        ++smem_pipe_read_kv;
        if constexpr (KTraits::USE_KV_REPACK) {
          // iter-end sync: ensures both wgs finished this iter's WGMMA reads
          // of the BF16 staging buffer before any wg overwrites it in the
          // next iter's dequant.
          __syncthreads();
        }
      }

      barrier_sync(KTraits::NUM_THREADS, NamedBarriers::kMDReady);
#pragma unroll
      for (uint32_t j = 0; j < 2; ++j) {
        m[j] = smem_storage.m[warp_idx_in_wg * 16 + j * 8 + lane_idx / 4];
        d[j] = smem_storage.d[warp_idx_in_wg * 16 + j * 8 + lane_idx / 4];
      }
      normalize_d_<KTraits>(&smem_storage, o_frag, m, d);
      finalize_m_<KTraits>(variant, m);
      PROFILER_EVENT_START(variant, ProfileEventType::kWriteO);
      write_o<false, KTraits>(
          &smem_storage, final_o + q_indptr * o_stride_n,
          final_lse ? final_lse + q_indptr * num_heads : nullptr,
          (partial_indptr == -1) ? nullptr : partial_o + partial_indptr * KTraits::HEAD_DIM_CKV,
          (partial_indptr == -1) ? nullptr : partial_lse + partial_indptr, o_frag, m, d, o_stride_n,
          o_stride_h, qo_upperbound, qo_packed_idx_base, num_heads, params.return_lse_base_on_e);
      PROFILER_EVENT_END(variant, ProfileEventType::kWriteO);
      __syncthreads();
    }
  } else {
    // compute qk, pv2
    PipelineState smem_pipe_read_q;
    PipelineState smem_pipe_read_kv;
    float s_frag[KTraits::NUM_REGS_S_FRAG];
    uint32_t p_frag[KTraits::NUM_REGS_P_FRAG];

#pragma unroll 1
    for (IdType work_idx = work_indptr[blockIdx.y]; work_idx < work_indptr[blockIdx.y + 1];
         ++work_idx) {
      auto [q_indptr, kv_indptr, partial_indptr, q_len, kv_len, packed_qo_start, kv_start, kv_end] =
          get_block_coord(params, work_idx);
      const uint32_t qo_packed_idx_base = packed_qo_start + blockIdx.x * KTraits::CTA_TILE_Q;
      const uint32_t qo_upperbound =
          min(q_len, ceil_div(qo_packed_idx_base + KTraits::CTA_TILE_Q, num_heads));

      init_states_<KTraits>(o_frag, m, d, o_scale);

      int kv_tile_idx =
          ceil_div(
              (CAUSAL ? min(kv_end, kv_len - q_len + (packed_qo_start + cluster_tile_q) / num_heads)
                      : kv_end),
              CTA_TILE_KV) -
          1 - (kv_start / CTA_TILE_KV);

      int mask_tile_idx =
          (CAUSAL ? min(kv_end, kv_len - q_len + static_cast<uint32_t>(packed_qo_start) / num_heads)
                  : kv_end) /
              CTA_TILE_KV -
          (kv_start / CTA_TILE_KV);

      consumer_wait(pipeline_q, smem_pipe_read_q);
#pragma unroll 1
      for (; kv_tile_idx >= mask_tile_idx && kv_tile_idx > 0; --kv_tile_idx) {
        consumer_wait(pipeline_kv, smem_pipe_read_kv);
        if constexpr (KTraits::USE_KV_REPACK) {
          // s1: pair with producer's d1 (matches per-iter __syncthreads count).
          __syncthreads();
          // Only consumer warpgroup (128 threads) dequants FP8 KV -> BF16
          // staging buffers. Producer wg passes through the d1/d2/d_end syncs.
          repack_fp8_kv_to_bf16<KTraits>(
              &smem_storage, smem_pipe_read_kv.index(), variant.ckv_scale, variant.kpe_scale,
              variant.ckv_scale_arr,
              kv_indptr * block_size + kv_start +
                  static_cast<uint32_t>(kv_tile_idx) * KTraits::CTA_TILE_KV,
              kv_indices, block_size, kv_indptr * block_size + kv_len);
          // s2: ensures dequant is complete before any wg reads BF16 staging.
          __syncthreads();
        }
        PROFILER_EVENT_START(variant, ProfileEventType::kGemmQK);
        compute_mla_qk<KTraits>(&smem_storage, smem_pipe_read_kv.index(), s_frag);
        warpgroup_wait<0>();
        PROFILER_EVENT_END(variant, ProfileEventType::kGemmQK);
        logits_mask_<KTraits>(qo_packed_idx_base, kv_start + kv_tile_idx * CTA_TILE_KV, q_len,
                              kv_len, kv_end, num_heads, s_frag);
        PROFILER_EVENT_START(variant, ProfileEventType::kSoftmaxUpdate);
        update_md_<KTraits>(&smem_storage, variant, s_frag, m, d, o_scale);
        PROFILER_EVENT_END(variant, ProfileEventType::kSoftmaxUpdate);
        write_o_scale_smem<KTraits>(&smem_storage, o_scale);

        convert_s_to_p<KTraits>(s_frag, p_frag);
        write_p_rmem_smem<KTraits>(&smem_storage, smem_pipe_read_kv.index(), p_frag);
        barrier_arrive(KTraits::NUM_THREADS, NamedBarriers::kOScaleReady);
        PROFILER_EVENT_START(variant, ProfileEventType::kRescaleO);
        rescale_o_<KTraits>(o_scale, o_frag);
        PROFILER_EVENT_END(variant, ProfileEventType::kRescaleO);
        __syncthreads();
        PROFILER_EVENT_START(variant, ProfileEventType::kGemmPV);
        compute_mla_pv<KTraits>(&smem_storage, smem_pipe_read_kv.index(), o_frag);
        warpgroup_wait<0>();
        PROFILER_EVENT_END(variant, ProfileEventType::kGemmPV);
        pipeline_kv.consumer_release(smem_pipe_read_kv);
        ++smem_pipe_read_kv;
        if constexpr (KTraits::USE_KV_REPACK) {
          // iter-end sync: ensures both wgs finished this iter's WGMMA reads
          // of the BF16 staging buffer before any wg overwrites it in the
          // next iter's dequant.
          __syncthreads();
        }
      }

#pragma unroll 1
      for (; kv_tile_idx + 1 > NUM_STAGES; --kv_tile_idx) {
        consumer_wait(pipeline_kv, smem_pipe_read_kv);
        if constexpr (KTraits::USE_KV_REPACK) {
          // s1: pair with producer's d1 (matches per-iter __syncthreads count).
          __syncthreads();
          // Only consumer warpgroup (128 threads) dequants FP8 KV -> BF16
          // staging buffers. Producer wg passes through the d1/d2/d_end syncs.
          repack_fp8_kv_to_bf16<KTraits>(
              &smem_storage, smem_pipe_read_kv.index(), variant.ckv_scale, variant.kpe_scale,
              variant.ckv_scale_arr,
              kv_indptr * block_size + kv_start +
                  static_cast<uint32_t>(kv_tile_idx) * KTraits::CTA_TILE_KV,
              kv_indices, block_size, kv_indptr * block_size + kv_len);
          // s2: ensures dequant is complete before any wg reads BF16 staging.
          __syncthreads();
        }
        PROFILER_EVENT_START(variant, ProfileEventType::kGemmQK);
        compute_mla_qk<KTraits>(&smem_storage, smem_pipe_read_kv.index(), s_frag);
        warpgroup_wait<0>();
        PROFILER_EVENT_END(variant, ProfileEventType::kGemmQK);
        PROFILER_EVENT_START(variant, ProfileEventType::kSoftmaxUpdate);
        update_md_<KTraits>(&smem_storage, variant, s_frag, m, d, o_scale);
        PROFILER_EVENT_END(variant, ProfileEventType::kSoftmaxUpdate);
        write_o_scale_smem<KTraits>(&smem_storage, o_scale);
        convert_s_to_p<KTraits>(s_frag, p_frag);
        write_p_rmem_smem<KTraits>(&smem_storage, smem_pipe_read_kv.index(), p_frag);
        barrier_arrive(KTraits::NUM_THREADS, NamedBarriers::kOScaleReady);
        PROFILER_EVENT_START(variant, ProfileEventType::kRescaleO);
        rescale_o_<KTraits>(o_scale, o_frag);
        PROFILER_EVENT_END(variant, ProfileEventType::kRescaleO);
        __syncthreads();
        PROFILER_EVENT_START(variant, ProfileEventType::kGemmPV);
        compute_mla_pv<KTraits>(&smem_storage, smem_pipe_read_kv.index(), o_frag);
        warpgroup_wait<0>();
        PROFILER_EVENT_END(variant, ProfileEventType::kGemmPV);
        pipeline_kv.consumer_release(smem_pipe_read_kv);
        ++smem_pipe_read_kv;
        if constexpr (KTraits::USE_KV_REPACK) {
          // iter-end sync: ensures both wgs finished this iter's WGMMA reads
          // of the BF16 staging buffer before any wg overwrites it in the
          // next iter's dequant.
          __syncthreads();
        }
      }

#pragma unroll 1
      for (; kv_tile_idx >= 0; --kv_tile_idx) {
        consumer_wait(pipeline_kv, smem_pipe_read_kv);
        if constexpr (KTraits::USE_KV_REPACK) {
          // s1: pair with producer's d1 (matches per-iter __syncthreads count).
          __syncthreads();
          // Only consumer warpgroup (128 threads) dequants FP8 KV -> BF16
          // staging buffers. Producer wg passes through the d1/d2/d_end syncs.
          repack_fp8_kv_to_bf16<KTraits>(
              &smem_storage, smem_pipe_read_kv.index(), variant.ckv_scale, variant.kpe_scale,
              variant.ckv_scale_arr,
              kv_indptr * block_size + kv_start +
                  static_cast<uint32_t>(kv_tile_idx) * KTraits::CTA_TILE_KV,
              kv_indices, block_size, kv_indptr * block_size + kv_len);
          // s2: ensures dequant is complete before any wg reads BF16 staging.
          __syncthreads();
        }
        PROFILER_EVENT_START(variant, ProfileEventType::kGemmQK);
        compute_mla_qk<KTraits>(&smem_storage, smem_pipe_read_kv.index(), s_frag);
        warpgroup_wait<0>();
        PROFILER_EVENT_END(variant, ProfileEventType::kGemmQK);
        logits_mask_<KTraits>(qo_packed_idx_base, kv_start + kv_tile_idx * CTA_TILE_KV, q_len,
                              kv_len, kv_end, num_heads, s_frag);
        PROFILER_EVENT_START(variant, ProfileEventType::kSoftmaxUpdate);
        update_md_<KTraits>(&smem_storage, variant, s_frag, m, d, o_scale);
        PROFILER_EVENT_END(variant, ProfileEventType::kSoftmaxUpdate);
        write_o_scale_smem<KTraits>(&smem_storage, o_scale);
        convert_s_to_p<KTraits>(s_frag, p_frag);
        write_p_rmem_smem<KTraits>(&smem_storage, smem_pipe_read_kv.index(), p_frag);
        barrier_arrive(KTraits::NUM_THREADS, NamedBarriers::kOScaleReady);
        PROFILER_EVENT_START(variant, ProfileEventType::kRescaleO);
        rescale_o_<KTraits>(o_scale, o_frag);
        PROFILER_EVENT_END(variant, ProfileEventType::kRescaleO);
        __syncthreads();
        PROFILER_EVENT_START(variant, ProfileEventType::kGemmPV);
        compute_mla_pv<KTraits>(&smem_storage, smem_pipe_read_kv.index(), o_frag);
        warpgroup_wait<0>();
        PROFILER_EVENT_END(variant, ProfileEventType::kGemmPV);
        pipeline_kv.consumer_release(smem_pipe_read_kv);
        ++smem_pipe_read_kv;
        if constexpr (KTraits::USE_KV_REPACK) {
          // iter-end sync: ensures both wgs finished this iter's WGMMA reads
          // of the BF16 staging buffer before any wg overwrites it in the
          // next iter's dequant.
          __syncthreads();
        }
      }

      pipeline_q.consumer_release(smem_pipe_read_q);
      ++smem_pipe_read_q;

#pragma unroll
      for (uint32_t j = 0; j < 2; ++j) {
        d[j] += __shfl_xor_sync(0x11111111, d[j], 0x2);
        d[j] += __shfl_xor_sync(0x11111111, d[j], 0x1);
        if (lane_idx % 4 == 0) {
          smem_storage.m[warp_idx_in_wg * 16 + j * 8 + lane_idx / 4] = m[j];
          smem_storage.d[warp_idx_in_wg * 16 + j * 8 + lane_idx / 4] = d[j];
        }
      }
      normalize_d_<KTraits>(&smem_storage, o_frag, m, d);
      finalize_m_<KTraits>(variant, m);
      barrier_arrive(KTraits::NUM_THREADS, NamedBarriers::kMDReady);
      PROFILER_EVENT_START(variant, ProfileEventType::kWriteO);
      write_o<true, KTraits>(
          &smem_storage, final_o + q_indptr * o_stride_n,
          final_lse ? final_lse + q_indptr * num_heads : nullptr,
          (partial_indptr == -1) ? nullptr : partial_o + partial_indptr * KTraits::HEAD_DIM_CKV,
          (partial_indptr == -1) ? nullptr : partial_lse + partial_indptr, o_frag, m, d, o_stride_n,
          o_stride_h, qo_upperbound, qo_packed_idx_base, num_heads, params.return_lse_base_on_e);
      PROFILER_EVENT_END(variant, ProfileEventType::kWriteO);
      __syncthreads();
    }
  }

  auto grid = cg::this_grid();
  grid.sync();

  PROFILER_EVENT_START(variant, ProfileEventType::kSplitK);

  __syncthreads();
  // the second stage, merge partial outputs
  DevicePersistentMergeStates<KTraits>(
      params.merge_packed_offset_start, params.merge_packed_offset_end,
      params.merge_partial_packed_offset_start, params.merge_partial_packed_offset_end,
      params.merge_partial_stride, partial_o, partial_lse, final_o, final_lse, o_stride_n,
      o_stride_h, num_heads, params.return_lse_base_on_e);

  PROFILER_EVENT_END(variant, ProfileEventType::kSplitK);
}

}  // namespace hopper

template <MaskMode MASK_MODE, uint32_t HEAD_DIM_CKV, uint32_t HEAD_DIM_KPE, typename Params>
cudaError_t BatchMLAPageAttentionHopper(Params params, uint32_t num_blks_x, uint32_t num_blks_y,
                                        cudaStream_t stream) {
  using DTypeQ = typename Params::DTypeQ;
  using DTypeKV = typename Params::DTypeKV;
  using DTypeO = typename Params::DTypeO;
  using IdType = typename Params::IdType;

  if (MASK_MODE == MaskMode::kCustom) {
    return cudaErrorNotSupported;
  }
  constexpr bool CAUSAL = MASK_MODE == MaskMode::kCausal;

  // get GPU shared memory size
  int device;
  int smem_limit_per_sm;
  cudaGetDevice(&device);
  cudaDeviceGetAttribute(&smem_limit_per_sm, cudaDevAttrMaxSharedMemoryPerMultiprocessor, device);

  // NUM_STAGES=2 for both paths. The FP8 KV path fits within the 228KB/SM
  // budget by sharing a single (non-per-stage) `o` writeback overlay across
  // the whole data path, freeing up room for the BF16 dequant staging buffers.
  constexpr uint32_t NUM_STAGES = 2;
  constexpr uint32_t CTA_TILE_Q = 64;
  constexpr uint32_t CTA_TILE_KV = 64;

  using KTraits =
      hopper::HopperKernelTraits<CAUSAL, NUM_STAGES, HEAD_DIM_CKV, HEAD_DIM_KPE, CTA_TILE_Q,
                                 CTA_TILE_KV, DTypeQ, DTypeKV, DTypeO, IdType>;
  dim3 nblks(num_blks_x, num_blks_y);
  dim3 nthrs(KTraits::NUM_THREADS);
  size_t smem_size = sizeof(typename KTraits::SharedStorage);

  auto kernel = hopper::BatchMLAPageAttentionHopperKernel<KTraits, Params>;
  void* args[] = {(void*)&params};

  FLASHINFER_CUDA_CALL(
      cudaFuncSetAttribute(kernel, cudaFuncAttributeMaxDynamicSharedMemorySize, smem_size));
  FLASHINFER_CUDA_CALL(
      cudaLaunchCooperativeKernel((void*)kernel, nblks, nthrs, args, smem_size, stream));

  return cudaSuccess;
}

}  // namespace mla

}  // namespace flashinfer

#endif  // FLASHINFER_MLA_HOPPER_CUH_
