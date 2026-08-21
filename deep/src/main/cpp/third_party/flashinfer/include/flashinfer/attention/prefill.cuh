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
#ifndef FLASHINFER_PREFILL_CUH_
#define FLASHINFER_PREFILL_CUH_

#include <cooperative_groups.h>
#include <cuda_bf16.h>
#include <cuda_fp16.h>
#include <cuda_fp8.h>
#if CUDA_VERSION >= 12080
#include <cuda_fp4.h>
#endif
#include <cuda_runtime.h>

#include "../cp_async.cuh"
#include "../fastdiv.cuh"
#ifdef FP16_QK_REDUCTION_SUPPORTED
#include "../fp16.h"
#endif
#include "../frag_layout_swizzle.cuh"
#include "../math.cuh"
#include "../mma.cuh"
#include "../page.cuh"
#include "../permuted_smem.cuh"
#include "../pos_enc.cuh"
#include "../utils.cuh"
#include "cascade.cuh"
#include "mask.cuh"
#include "variants.cuh"
namespace flashinfer {

DEFINE_HAS_MEMBER(maybe_q_rope_offset)
DEFINE_HAS_MEMBER(maybe_k_rope_offset)
DEFINE_HAS_MEMBER(maybe_prefix_len_ptr)
DEFINE_HAS_MEMBER(maybe_token_pos_in_items_ptr)
DEFINE_HAS_MEMBER(token_pos_in_items_len)
DEFINE_HAS_MEMBER(maybe_max_item_len_ptr)
DEFINE_HAS_MEMBER(maybe_k_cache_sf)
DEFINE_HAS_MEMBER(maybe_v_cache_sf)

// Type trait to detect packed NVFP4 KV cache types (__nv_fp4x2_e2m1 stores 2 FP4 per byte).
template <typename T>
struct is_fp4_type : std::false_type {};
#if CUDA_VERSION >= 12080
template <>
struct is_fp4_type<__nv_fp4x2_e2m1> : std::true_type {};
#endif
template <typename T>
inline constexpr bool is_fp4_type_v = is_fp4_type<T>::value;

namespace cg = cooperative_groups;
using cp_async::SharedMemFillMode;
using mma::MMAMode;

constexpr uint32_t WARP_SIZE = 32;
// Number of NVFP4 elements sharing one scale factor (UE4M3 byte).
constexpr uint32_t NVFP4_SF_VEC_SIZE = 16;

constexpr uint32_t get_num_warps_q(const uint32_t cta_tile_q) {
  if (cta_tile_q == 32) {
    return 1;  // HEAD_DIM_VO >= 512
  }
  if (cta_tile_q > 16) {
    return 4;
  } else {
    return 1;
  }
}

constexpr uint32_t get_num_warps_kv(const uint32_t cta_tile_kv) {
  return 4 / get_num_warps_q(cta_tile_kv);
}

constexpr uint32_t get_num_mma_q(const uint32_t cta_tile_q) {
  if (cta_tile_q == 32) {
    return 2;  // HEAD_DIM_VO >= 512
  }
  if (cta_tile_q > 64) {
    return 2;
  } else {
    return 1;
  }
}

// NVFP4 KV-cache scale-factor staging (one UE4M3 byte per NVFP4_SF_VEC_SIZE elements), used only
// when DTypeKV is FP4. Kept as an empty base for other dtypes so it adds 0 bytes via EBO instead
// of a padded placeholder: at HEAD_DIM 256 the q/k/v union already fills the 64 KiB opt-in smem
// budget on SM75/SM70, where any extra byte makes cudaFuncSetAttribute() (and the launch) fail.
template <typename DTypeKV, uint32_t CTA_TILE_KV, uint32_t HEAD_DIM_QK, uint32_t HEAD_DIM_VO,
          bool = is_fp4_type_v<DTypeKV>>
struct KVScaleFactorSmem {};
template <typename DTypeKV, uint32_t CTA_TILE_KV, uint32_t HEAD_DIM_QK, uint32_t HEAD_DIM_VO>
struct KVScaleFactorSmem<DTypeKV, CTA_TILE_KV, HEAD_DIM_QK, HEAD_DIM_VO, true> {
  alignas(16) uint8_t k_sf_smem[CTA_TILE_KV * HEAD_DIM_QK / NVFP4_SF_VEC_SIZE];
  alignas(16) uint8_t v_sf_smem[CTA_TILE_KV * HEAD_DIM_VO / NVFP4_SF_VEC_SIZE];
};

// BF16/FP16 staging buffer for the FP8 "repack" path (compute_qk REPACK_BF16). Empty base
// (0 bytes via EBO) when unused; same smem rationale as KVScaleFactorSmem above.
template <typename DTypeQ, typename DTypeKV, uint32_t CTA_TILE_Q, uint32_t CTA_TILE_KV,
          uint32_t HEAD_DIM_QK, uint32_t HEAD_DIM_VO,
          bool = ((sizeof(DTypeKV) == 1) && !is_fp4_type_v<DTypeKV> && (HEAD_DIM_VO != 64) &&
                  (HEAD_DIM_VO <= 256) && (CTA_TILE_Q > 16))>
struct KVRepackSmem {};
template <typename DTypeQ, typename DTypeKV, uint32_t CTA_TILE_Q, uint32_t CTA_TILE_KV,
          uint32_t HEAD_DIM_QK, uint32_t HEAD_DIM_VO>
struct KVRepackSmem<DTypeQ, DTypeKV, CTA_TILE_Q, CTA_TILE_KV, HEAD_DIM_QK, HEAD_DIM_VO, true> {
  // Sized to max(HEAD_DIM_QK, HEAD_DIM_VO); time-shared between K and V within a tile.
  alignas(16) DTypeQ
      kv_smem_repack[CTA_TILE_KV * (HEAD_DIM_QK > HEAD_DIM_VO ? HEAD_DIM_QK : HEAD_DIM_VO)];
};

// VO-split staging (P matrix + cross-warp m/d). Empty base when VO-split is inactive so the
// alignas(16) placeholders do not push HEAD_DIM 256 past the 64 KiB opt-in smem budget.
template <uint32_t NUM_WARPS_KV, uint32_t CTA_TILE_Q, uint32_t CTA_TILE_KV, uint32_t HEAD_DIM_VO,
          typename DTypeQ, bool kEnableVOSplitOpt,
          bool = kEnableVOSplitOpt && (HEAD_DIM_VO / 16 > 16) &&
                 ((HEAD_DIM_VO / 16) % NUM_WARPS_KV == 0)>
struct VOSplitSmem {};
template <uint32_t NUM_WARPS_KV, uint32_t CTA_TILE_Q, uint32_t CTA_TILE_KV, uint32_t HEAD_DIM_VO,
          typename DTypeQ, bool kEnableVOSplitOpt>
struct VOSplitSmem<NUM_WARPS_KV, CTA_TILE_Q, CTA_TILE_KV, HEAD_DIM_VO, DTypeQ, kEnableVOSplitOpt,
                   true> {
  alignas(16) DTypeQ p_smem[CTA_TILE_Q * CTA_TILE_KV];
  alignas(16) float2 vosplit_md_smem[NUM_WARPS_KV * CTA_TILE_Q];
};

template <uint32_t NUM_WARPS_KV, uint32_t CTA_TILE_Q, uint32_t CTA_TILE_KV, uint32_t HEAD_DIM_QK,
          uint32_t HEAD_DIM_VO, typename DTypeQ, typename DTypeKV, typename DTypeO,
          bool kEnableVOSplitOpt = false>
struct SharedStorageQKVO
    : KVScaleFactorSmem<DTypeKV, CTA_TILE_KV, HEAD_DIM_QK, HEAD_DIM_VO>,
      KVRepackSmem<DTypeQ, DTypeKV, CTA_TILE_Q, CTA_TILE_KV, HEAD_DIM_QK, HEAD_DIM_VO>,
      VOSplitSmem<NUM_WARPS_KV, CTA_TILE_Q, CTA_TILE_KV, HEAD_DIM_VO, DTypeQ, kEnableVOSplitOpt> {
  static constexpr bool kKVShareShape =
      (HEAD_DIM_VO / 16 > 16) && ((HEAD_DIM_VO / 16) % NUM_WARPS_KV == 0);
  static constexpr bool kVOSplit = kEnableVOSplitOpt && kKVShareShape;
  // K/V time-sharing (V loaded into k_smem after Q.K^T) applies to ALL kernels
  // at large head dims. Must match KernelTraits::USE_KV_SHARED_SMEM.
  // Otherwise the single/ragged kernels cannot fit head_dim=512 K+V
  // tiles on SKUs with 99KB-smem (SM86/89/120/121).
  static constexpr bool kVShareActive = kKVShareShape && !is_fp4_type_v<DTypeKV> &&
                                        (HEAD_DIM_QK == HEAD_DIM_VO) &&
                                        (sizeof(DTypeKV) == 2 || CTA_TILE_Q > 16);
  union {
    struct {
      alignas(16) DTypeQ q_smem[CTA_TILE_Q * HEAD_DIM_QK];
      alignas(16) DTypeKV k_smem[CTA_TILE_KV * HEAD_DIM_QK];
      alignas(16)
          std::conditional_t<kVShareActive, DTypeKV[1], DTypeKV[CTA_TILE_KV * HEAD_DIM_VO]> v_smem;
    };
    struct {  // NOTE(Zihao): synchronize attention states across warps
      alignas(
          16) std::conditional_t<NUM_WARPS_KV == 1 || kVOSplit, float[1],
                                 float[NUM_WARPS_KV * CTA_TILE_Q *
                                       (HEAD_DIM_VO > 256 ? 256 : HEAD_DIM_VO)]> cta_sync_o_smem;
      alignas(16) std::conditional_t<NUM_WARPS_KV == 1, float2[1],
                                     float2[NUM_WARPS_KV * CTA_TILE_Q]> cta_sync_md_smem;
    };
    alignas(16) DTypeO smem_o[CTA_TILE_Q * HEAD_DIM_VO];
  };
  static constexpr bool USE_KV_REPACK = (sizeof(DTypeKV) == 1) && !is_fp4_type_v<DTypeKV> &&
                                        (HEAD_DIM_VO != 64) && (HEAD_DIM_VO <= 256) &&
                                        (CTA_TILE_Q > 16);
  static constexpr bool VO_SPLIT_SMEM = kVOSplit;

  // Accessors for the empty-base buffers above: return the buffer when present, else nullptr.
  // The result is only dereferenced inside the matching `if constexpr` branch, and the offset is
  // folded in here so it is never applied to nullptr. We static_cast to the populated base
  // specialization so the member name is looked up on a type that actually declares it (robust
  // against eager name lookup into dependent bases on stricter/older compilers); the cast and
  // base specialization are never instantiated in the discarded branch.
  __device__ __forceinline__ uint8_t* k_sf_smem_ptr(uint32_t offset = 0) {
    if constexpr (is_fp4_type_v<DTypeKV>) {
      using Base = KVScaleFactorSmem<DTypeKV, CTA_TILE_KV, HEAD_DIM_QK, HEAD_DIM_VO, true>;
      return static_cast<Base*>(this)->k_sf_smem + offset;
    } else {
      return nullptr;
    }
  }
  __device__ __forceinline__ uint8_t* v_sf_smem_ptr(uint32_t offset = 0) {
    if constexpr (is_fp4_type_v<DTypeKV>) {
      using Base = KVScaleFactorSmem<DTypeKV, CTA_TILE_KV, HEAD_DIM_QK, HEAD_DIM_VO, true>;
      return static_cast<Base*>(this)->v_sf_smem + offset;
    } else {
      return nullptr;
    }
  }
  __device__ __forceinline__ DTypeQ* kv_smem_repack_ptr() {
    if constexpr (USE_KV_REPACK) {
      using Base =
          KVRepackSmem<DTypeQ, DTypeKV, CTA_TILE_Q, CTA_TILE_KV, HEAD_DIM_QK, HEAD_DIM_VO, true>;
      return static_cast<Base*>(this)->kv_smem_repack;
    } else {
      return nullptr;
    }
  }
  __device__ __forceinline__ DTypeQ* p_smem_ptr() {
    if constexpr (VO_SPLIT_SMEM) {
      using Base = VOSplitSmem<NUM_WARPS_KV, CTA_TILE_Q, CTA_TILE_KV, HEAD_DIM_VO, DTypeQ,
                               kEnableVOSplitOpt, true>;
      return static_cast<Base*>(this)->p_smem;
    } else {
      return nullptr;
    }
  }
  __device__ __forceinline__ float2* vosplit_md_smem_ptr() {
    if constexpr (VO_SPLIT_SMEM) {
      using Base = VOSplitSmem<NUM_WARPS_KV, CTA_TILE_Q, CTA_TILE_KV, HEAD_DIM_VO, DTypeQ,
                               kEnableVOSplitOpt, true>;
      return static_cast<Base*>(this)->vosplit_md_smem;
    } else {
      return nullptr;
    }
  }
};

template <typename BaseStorage, uint32_t NUM_ROPE_FREQ_ROWS>
struct SharedStorageWithRopeFreq : BaseStorage {
  alignas(16) float rope_freq_smem[4][NUM_ROPE_FREQ_ROWS][4];
};

template <MaskMode MASK_MODE_, uint32_t CTA_TILE_Q_, uint32_t NUM_MMA_Q_, uint32_t NUM_MMA_KV_,
          uint32_t NUM_MMA_D_QK_, uint32_t NUM_MMA_D_VO_, uint32_t NUM_WARPS_Q_,
          uint32_t NUM_WARPS_KV_, PosEncodingMode POS_ENCODING_MODE_, typename DTypeQ_,
          typename DTypeKV_, typename DTypeO_, typename DTypeQKAccum_, typename IdType_,
          typename AttentionVariant_>
struct KernelTraits {
  static constexpr uint32_t NUM_STAGES = 1;  // used for BatchAttention Template
  static constexpr MaskMode MASK_MODE = MASK_MODE_;
  static constexpr uint32_t NUM_MMA_Q = NUM_MMA_Q_;
  static constexpr uint32_t NUM_MMA_KV = NUM_MMA_KV_;
  static constexpr uint32_t NUM_MMA_D_QK = NUM_MMA_D_QK_;
  static constexpr uint32_t NUM_MMA_D_VO = NUM_MMA_D_VO_;
  static constexpr uint32_t NUM_WARPS_Q = NUM_WARPS_Q_;
  static constexpr uint32_t NUM_WARPS_KV = NUM_WARPS_KV_;
  static constexpr uint32_t NUM_THREADS = NUM_WARPS_Q * NUM_WARPS_KV * WARP_SIZE;
  static constexpr uint32_t NUM_WARPS = NUM_WARPS_Q * NUM_WARPS_KV;
  static constexpr uint32_t HEAD_DIM_QK = NUM_MMA_D_QK * 16;
  static constexpr uint32_t HEAD_DIM_VO = NUM_MMA_D_VO * 16;
  static constexpr uint32_t NUM_MMA_D_VO_TILE = NUM_MMA_D_VO > 16 ? 16 : NUM_MMA_D_VO;
  static constexpr uint32_t NUM_D_VO_TILES = NUM_MMA_D_VO / NUM_MMA_D_VO_TILE;
  static_assert(NUM_MMA_D_VO % NUM_MMA_D_VO_TILE == 0,
                "NUM_MMA_D_VO must be divisible by NUM_MMA_D_VO_TILE");
  static constexpr uint32_t HEAD_DIM_VO_TILE = NUM_MMA_D_VO_TILE * 16;
  static constexpr bool USE_VO_SPLIT = (NUM_MMA_D_VO > 16) && (NUM_MMA_D_VO % NUM_WARPS_KV == 0);
  static constexpr uint32_t NUM_MMA_D_VO_PER_WARP =
      USE_VO_SPLIT ? (NUM_MMA_D_VO / NUM_WARPS_KV) : NUM_MMA_D_VO;
  static constexpr bool USE_KV_SHARED_SMEM = USE_VO_SPLIT && !is_fp4_type_v<DTypeKV_> &&
                                             (HEAD_DIM_QK == HEAD_DIM_VO) &&
                                             (sizeof(DTypeKV_) == 2 || CTA_TILE_Q_ > 16);
  static constexpr bool USE_SOFTMAX_VO_SPLIT = USE_VO_SPLIT && AttentionVariant_::use_softmax &&
                                               ((sizeof(DTypeKV_) == 2) || is_fp4_type_v<DTypeKV_>);
  static constexpr bool USE_SINGLE_PREFILL_SOFTMAX_VO_SPLIT =
      USE_SOFTMAX_VO_SPLIT && is_fp4_type_v<DTypeKV_>;
  static constexpr bool USE_16B_VO_SPLIT =
      USE_VO_SPLIT && (sizeof(DTypeKV_) == 2) && AttentionVariant_::use_softmax;
  static constexpr bool USE_SHARED_ROPE_FREQ = POS_ENCODING_MODE_ == PosEncodingMode::kRoPELlama &&
                                               HEAD_DIM_QK > 256 && HEAD_DIM_QK == HEAD_DIM_VO;
  static constexpr uint32_t SHARED_ROPE_FREQ_BYTES =
      USE_SHARED_ROPE_FREQ ? (4 * (NUM_MMA_D_QK / 2) * 4 * sizeof(float)) : 0;
  static constexpr uint32_t UPCAST_STRIDE_Q = HEAD_DIM_QK / upcast_size<DTypeQ_>();
  static constexpr uint32_t UPCAST_STRIDE_K = HEAD_DIM_QK / upcast_size<DTypeKV_>();
  static constexpr uint32_t UPCAST_STRIDE_V = HEAD_DIM_VO / upcast_size<DTypeKV_>();
  static constexpr uint32_t UPCAST_STRIDE_O = HEAD_DIM_VO / upcast_size<DTypeO_>();
  static constexpr uint32_t CTA_TILE_Q = CTA_TILE_Q_;
  static constexpr uint32_t CTA_TILE_KV = NUM_MMA_KV * NUM_WARPS_KV * 16;

  static constexpr SwizzleMode SWIZZLE_MODE_Q = SwizzleMode::k128B;
  static constexpr SwizzleMode SWIZZLE_MODE_KV =
      (sizeof(DTypeKV_) == 1 && HEAD_DIM_VO == 64) ? SwizzleMode::k64B : SwizzleMode::k128B;
  static constexpr uint32_t KV_THR_LAYOUT_ROW = SWIZZLE_MODE_KV == SwizzleMode::k128B ? 4 : 8;
  static constexpr uint32_t KV_THR_LAYOUT_COL = SWIZZLE_MODE_KV == SwizzleMode::k128B ? 8 : 4;
  static constexpr PosEncodingMode POS_ENCODING_MODE = POS_ENCODING_MODE_;
  using DTypeQ = DTypeQ_;
  using DTypeKV = DTypeKV_;
  using DTypeO = DTypeO_;
  using DTypeQKAccum = DTypeQKAccum_;
  using IdType = IdType_;
  using AttentionVariant = AttentionVariant_;
  static constexpr bool USE_KV_REPACK = (sizeof(DTypeKV_) == 1) && !is_fp4_type_v<DTypeKV_> &&
                                        (HEAD_DIM_VO != 64) && (HEAD_DIM_VO <= 256) &&
                                        (CTA_TILE_Q > 16);  // CTA16 = decode/short-q -> in-loop
  // b128 columns per KV row in the FP8 (packed) and BF16 (repacked) layouts.
  static constexpr uint32_t REPACK_STRIDE_QK = HEAD_DIM_QK / upcast_size<DTypeQ_>();
  static constexpr uint32_t REPACK_STRIDE_VO = HEAD_DIM_VO / upcast_size<DTypeQ_>();

  static constexpr bool IsInvalid() {
    // The first clause prunes (CTA_TILE_Q, head_dim) pairs FA2DetermineCtaTileQ
    // never selects: it picks from {16, 32} when head_dim_vo >= 512 (VO-split),
    // {16} when head_dim_qk >= 512 with a smaller head_dim_vo, and
    // {16, 64, 128} otherwise. The clause keys on HEAD_DIM_QK (which bounds
    // CTA_TILE_Q <= 32 in both large-head cases); the kernel-instantiation
    // lists additionally drop the never-selected CTA_TILE_Q=32 for
    // head_dim_qk >= 512 with head_dim_vo < 512.
    return ((HEAD_DIM_QK >= 512 ? (CTA_TILE_Q > 32) : (CTA_TILE_Q == 32)) || (NUM_MMA_D_VO < 4) ||
            (NUM_MMA_D_VO == 4 && NUM_MMA_KV % 2 == 1) ||
            (POS_ENCODING_MODE == PosEncodingMode::kRoPELlama && NUM_MMA_D_VO > 4 &&
             NUM_MMA_D_VO % (2 * NUM_WARPS_Q) != 0) ||
            (NUM_MMA_Q * (8 * (USE_VO_SPLIT ? NUM_MMA_D_VO_PER_WARP : NUM_MMA_D_VO_TILE) +
                          2 * sizeof(DTypeQKAccum) * NUM_MMA_KV) >=
             256) ||
            (sizeof(DTypeKV) == 1 && NUM_MMA_KV * 2 % NUM_WARPS_Q != 0) ||
            (sizeof(DTypeKV) == 1 && !is_fp4_type_v<DTypeKV> &&
             POS_ENCODING_MODE == PosEncodingMode::kRoPELlama));
  }

  using BaseSharedStorage = SharedStorageQKVO<NUM_WARPS_KV, CTA_TILE_Q, CTA_TILE_KV, HEAD_DIM_QK,
                                              HEAD_DIM_VO, DTypeQ, DTypeKV, DTypeO>;
  using BaseSharedStoragePaged =
      SharedStorageQKVO<NUM_WARPS_KV, CTA_TILE_Q, CTA_TILE_KV, HEAD_DIM_QK, HEAD_DIM_VO, DTypeQ,
                        DTypeKV, DTypeO, /*kEnableVOSplitOpt=*/true>;
  using SharedStorage =
      std::conditional_t<USE_SHARED_ROPE_FREQ,
                         SharedStorageWithRopeFreq<BaseSharedStorage, NUM_MMA_D_QK / 2>,
                         BaseSharedStorage>;
  using SharedStoragePaged =
      std::conditional_t<USE_SHARED_ROPE_FREQ,
                         SharedStorageWithRopeFreq<BaseSharedStoragePaged, NUM_MMA_D_QK / 2>,
                         BaseSharedStoragePaged>;
#ifdef FP16_QK_REDUCTION_SUPPORTED
  template <typename DT>
  static constexpr DT getNegInf() {
    if constexpr (std::is_same<DT, __half>::value) {
      return std::bit_cast<half>(fp16_ieee_from_fp32_value(-math::inf));
    } else {
      return static_cast<DTypeQKAccum>(-math::inf);
    }
  }

  static constexpr DTypeQKAccum MaskFillValue =
      AttentionVariant::use_softmax ? getNegInf<DTypeQKAccum>() : DTypeQKAccum(0.f);
#else
  static_assert(!std::is_same<DTypeQKAccum, __half>::value,
                "Set -DFP16_QK_REDUCTION_SUPPORTED and install boost_math "
                "then recompile to support fp16 reduction");
  static constexpr DTypeQKAccum MaskFillValue =
      AttentionVariant::use_softmax ? DTypeQKAccum(-math::inf) : DTypeQKAccum(0.f);
#endif
};

namespace {

template <typename KTraits>
__device__ __forceinline__ uint32_t get_warp_idx_q(const uint32_t tid_y = threadIdx.y) {
  if constexpr (KTraits::NUM_WARPS_Q == 1) {
    return 0;
  } else {
    return tid_y;
  }
}

template <typename KTraits>
__device__ __forceinline__ uint32_t get_warp_idx_kv(const uint32_t tid_z = threadIdx.z) {
  if constexpr (KTraits::NUM_WARPS_KV == 1) {
    return 0;
  } else {
    return tid_z;
  }
}

template <typename KTraits>
__device__ __forceinline__ uint32_t get_warp_idx(const uint32_t tid_y = threadIdx.y,
                                                 const uint32_t tid_z = threadIdx.z) {
  return get_warp_idx_kv<KTraits>(tid_z) * KTraits::NUM_WARPS_Q + get_warp_idx_q<KTraits>(tid_y);
}

/*!
 * \brief Apply Llama style rotary embedding to two 16x16 fragments.
 * \tparam T The data type of the input fragments.
 * \param x_first_half First fragment x[offset:offset+16, j*16:(j+1)*16]
 * \param x_second_half Second fragment x[offset:offset*16, j*16+d/2:(j+1)*16+d/2]
 * \param rope_freq Rope frequency
 * \param offset The offset of the first row in both fragments.
 * \note The sin/cos computation is slow, especially for A100 GPUs which has low
 *   non tensor-ops flops, will optimize in the future.
 */
template <typename T>
__device__ __forceinline__ void k_frag_apply_llama_rope(T* x_first_half, T* x_second_half,
                                                        const float* rope_freq,
                                                        const uint32_t kv_offset) {
  static_assert(sizeof(T) == 2);
#pragma unroll
  for (uint32_t reg_id = 0; reg_id < 8; ++reg_id) {
    float cos, sin, tmp;
    // 0 1 | 2 3
    // ---------
    // 4 5 | 6 7
    uint32_t i = reg_id / 4, j = (reg_id % 4) / 2;
    __sincosf(float(kv_offset + 8 * i) * rope_freq[2 * j + reg_id % 2], &sin, &cos);
    tmp = x_first_half[reg_id];
    x_first_half[reg_id] = (tmp * cos - (float)x_second_half[reg_id] * sin);
    x_second_half[reg_id] = ((float)x_second_half[reg_id] * cos + tmp * sin);
  }
}

template <typename T>
__device__ __forceinline__ void q_frag_apply_llama_rope(T* x_first_half, T* x_second_half,
                                                        const float* rope_freq,
                                                        const uint32_t qo_packed_offset,
                                                        const uint_fastdiv group_size) {
#pragma unroll
  for (uint32_t reg_id = 0; reg_id < 8; ++reg_id) {
    float cos, sin, tmp;
    // 0 1 | 4 5
    // ---------
    // 2 3 | 6 7
    uint32_t i = ((reg_id % 4) / 2), j = (reg_id / 4);
    __sincosf(float((qo_packed_offset + 8 * i) / group_size) * rope_freq[2 * j + reg_id % 2], &sin,
              &cos);
    tmp = x_first_half[reg_id];
    x_first_half[reg_id] = (tmp * cos - (float)x_second_half[reg_id] * sin);
    x_second_half[reg_id] = ((float)x_second_half[reg_id] * cos + tmp * sin);
  }
}

template <typename T, typename IdType>
__device__ __forceinline__ void q_frag_apply_llama_rope_with_pos(T* x_first_half, T* x_second_half,
                                                                 const float* rope_freq,
                                                                 const uint32_t qo_packed_offset,
                                                                 const uint_fastdiv group_size,
                                                                 const IdType* q_rope_offset) {
  float pos[2] = {static_cast<float>(q_rope_offset[qo_packed_offset / group_size]),
                  static_cast<float>(q_rope_offset[(qo_packed_offset + 8) / group_size])};
#pragma unroll
  for (uint32_t reg_id = 0; reg_id < 8; ++reg_id) {
    float cos, sin, tmp;
    // 0 1 | 4 5
    // ---------
    // 2 3 | 6 7
    uint32_t i = ((reg_id % 4) / 2), j = (reg_id / 4);
    __sincosf(pos[i] * rope_freq[2 * j + reg_id % 2], &sin, &cos);
    tmp = x_first_half[reg_id];
    x_first_half[reg_id] = (tmp * cos - (float)x_second_half[reg_id] * sin);
    x_second_half[reg_id] = ((float)x_second_half[reg_id] * cos + tmp * sin);
  }
}

/*!
 * \brief Produce k/v fragments from global memory to shared memory.
 * \tparam fill_mode The fill mode of the shared memory.
 * \tparam NUM_MMA_D_VO The number of fragments in y dimension.
 * \tparam NUM_MMA_KV The number of fragments in z dimension.
 * \tparam num_warps The number of warps in the threadblock.
 * \tparam T The data type of the input tensor.
 * \param smem The shared memory to store kv fragments.
 * \param gptr The global memory pointer.
 * \param kv_idx_base The base kv index.
 * \param kv_len The length of kv tensor.
 */
template <bool produce_v, SharedMemFillMode fill_mode, typename KTraits>
__device__ __forceinline__ void produce_kv(smem_t<KTraits::SWIZZLE_MODE_KV> smem,
                                           uint32_t* smem_offset, typename KTraits::DTypeKV** gptr,
                                           const uint32_t stride_n, const uint32_t kv_idx_base,
                                           const uint32_t kv_len, const dim3 tid = threadIdx) {
  // NOTE: for fp8, this function doesn't work for head_dim = 64 at the moment
  using DTypeKV = typename KTraits::DTypeKV;
  constexpr bool IS_FP4 = is_fp4_type_v<DTypeKV>;
  constexpr uint32_t CTA_TILE_KV = KTraits::CTA_TILE_KV;
  constexpr uint32_t NUM_WARPS = KTraits::NUM_WARPS;
  constexpr uint32_t NUM_WARPS_Q = KTraits::NUM_WARPS_Q;
  constexpr uint32_t NUM_MMA_D = produce_v ? KTraits::NUM_MMA_D_VO : KTraits::NUM_MMA_D_QK;
  constexpr uint32_t NUM_MMA_KV = KTraits::NUM_MMA_KV;
  constexpr uint32_t UPCAST_STRIDE =
      produce_v ? KTraits::UPCAST_STRIDE_V : KTraits::UPCAST_STRIDE_K;
  const uint32_t warp_idx = get_warp_idx<KTraits>(tid.y, tid.z), lane_idx = tid.x;

  if constexpr (KTraits::SWIZZLE_MODE_KV == SwizzleMode::k128B) {
    uint32_t kv_idx = kv_idx_base + warp_idx * 4 + lane_idx / 8;
    // NOTE: NUM_MMA_KV * 4 / NUM_WARPS_Q = NUM_WARPS_KV * NUM_MMA_KV * 4 / num_warps
    static_assert(NUM_MMA_KV * 4 % NUM_WARPS_Q == 0);
#pragma unroll
    for (uint32_t i = 0; i < NUM_MMA_KV * 4 / NUM_WARPS_Q; ++i) {
#pragma unroll
      for (uint32_t j = 0; j < NUM_MMA_D / (8 / sizeof(DTypeKV)); ++j) {
        // FP4 GMEM rows are packed 2x denser; load 64b (upper 64b of smem slot zeroed).
        if constexpr (IS_FP4) {
          smem.template load_64b_async<fill_mode>(*smem_offset, *gptr, kv_idx < kv_len);
        } else {
          smem.template load_128b_async<fill_mode>(*smem_offset, *gptr, kv_idx < kv_len);
        }
        *smem_offset = smem.template advance_offset_by_column<8>(*smem_offset, j);
        *gptr += (IS_FP4 ? 4 : 8) * upcast_size<DTypeKV>();
      }
      kv_idx += NUM_WARPS * 4;
      *smem_offset =
          smem.template advance_offset_by_row<NUM_WARPS * 4, UPCAST_STRIDE>(*smem_offset) -
          sizeof(DTypeKV) * NUM_MMA_D;
      *gptr += NUM_WARPS * 4 * stride_n -
               (IS_FP4 ? 4 : 8) * upcast_size<DTypeKV>() * (NUM_MMA_D / (8 / sizeof(DTypeKV)));
    }
    *smem_offset -= CTA_TILE_KV * UPCAST_STRIDE;
  } else {
    uint32_t kv_idx = kv_idx_base + warp_idx * 8 + lane_idx / 4;
    // NOTE: NUM_MMA_KV * 2 / NUM_WARPS_Q = NUM_WARPS_KV * NUM_MMA_KV * 2 / num_warps
    static_assert(NUM_MMA_KV * 2 % NUM_WARPS_Q == 0);
#pragma unroll
    for (uint32_t i = 0; i < NUM_MMA_KV * 2 / NUM_WARPS_Q; ++i) {
      // FP4 GMEM rows are packed 2x denser; load 64b (upper 64b of smem slot zeroed).
      if constexpr (IS_FP4) {
        smem.template load_64b_async<fill_mode>(*smem_offset, *gptr, kv_idx < kv_len);
      } else {
        smem.load_128b_async<fill_mode>(*smem_offset, *gptr, kv_idx < kv_len);
      }
      *smem_offset =
          smem.template advance_offset_by_row<NUM_WARPS * 8, UPCAST_STRIDE>(*smem_offset);
      kv_idx += NUM_WARPS * 8;
      *gptr += NUM_WARPS * 8 * stride_n;
    }
    *smem_offset -= KTraits::CTA_TILE_KV * UPCAST_STRIDE;
  }
}

template <bool produce_v, typename KTraits, typename SmemStorage>
__device__ __forceinline__ void page_produce_kv(SmemStorage* smem_storage, uint32_t* smem_offset,
                                                typename KTraits::DTypeKV* kv_ptr,
                                                const uint32_t kv_idx_base,
                                                const size_t* thr_local_kv_offset,
                                                const uint32_t kv_len, const uint32_t warp_idx,
                                                const uint32_t lane_idx) {
  // NOTE: for fp8, this function doesn't work for head_dim = 64 at the moment
  // K/V-shared path: V is loaded into k_smem (time-shared); v_smem is a [1] stub.
  smem_t<KTraits::SWIZZLE_MODE_KV> smem(
      (produce_v && !KTraits::USE_KV_SHARED_SMEM) ? smem_storage->v_smem : smem_storage->k_smem);
  using DType = typename KTraits::DTypeKV;
  using IdType = typename KTraits::IdType;
  constexpr SharedMemFillMode fill_mode =
      produce_v ? SharedMemFillMode::kFillZero : SharedMemFillMode::kNoFill;
  constexpr uint32_t NUM_WARPS = KTraits::NUM_WARPS;
  constexpr uint32_t NUM_WARPS_Q = KTraits::NUM_WARPS_Q;
  constexpr uint32_t NUM_MMA_KV = KTraits::NUM_MMA_KV;
  constexpr uint32_t NUM_MMA_D = produce_v ? KTraits::NUM_MMA_D_VO : KTraits::NUM_MMA_D_QK;
  constexpr uint32_t UPCAST_STRIDE =
      produce_v ? KTraits::UPCAST_STRIDE_V : KTraits::UPCAST_STRIDE_K;
  // FP4 stores 2 elements per byte in GMEM (packed); SMEM uses 64b data + 64b zero per 128b slot.
  // Use a 64b async load (cp.async with src-size=8) and advance GMEM pointer by half the normal
  // amount, while SMEM addressing remains unchanged.
  constexpr bool IS_FP4 = is_fp4_type_v<DType>;
  if constexpr (KTraits::SWIZZLE_MODE_KV == SwizzleMode::k128B) {
    uint32_t kv_idx = kv_idx_base + warp_idx * 4 + lane_idx / 8;
    // NOTE: NUM_MMA_KV * 4 / NUM_WARPS_Q = NUM_WARPS_KV * NUM_MMA_KV * 4 / num_warps
    static_assert(NUM_MMA_KV * 4 % NUM_WARPS_Q == 0);
#pragma unroll
    for (uint32_t i = 0; i < NUM_MMA_KV * 4 / NUM_WARPS_Q; ++i) {
      DType* gptr = kv_ptr + thr_local_kv_offset[i];
#pragma unroll
      for (uint32_t j = 0; j < NUM_MMA_D / (8 / sizeof(DType)); ++j) {
        if constexpr (IS_FP4) {
          // Load 64b from packed GMEM into lower 64b of 128b SMEM slot (upper 64b zeroed)
          smem.load_64b_async<fill_mode>(*smem_offset, gptr, kv_idx < kv_len);
        } else {
          smem.load_128b_async<fill_mode>(*smem_offset, gptr, kv_idx < kv_len);
        }
        *smem_offset = smem.template advance_offset_by_column<8>(*smem_offset, j);
        // FP4: GMEM row is HEAD_DIM/2 bytes wide (packed), so advance by half
        gptr += (IS_FP4 ? 4 : 8) * upcast_size<DType>();
      }
      kv_idx += NUM_WARPS * 4;
      *smem_offset =
          smem.template advance_offset_by_row<NUM_WARPS * 4, UPCAST_STRIDE>(*smem_offset) -
          sizeof(DType) * NUM_MMA_D;
    }
    *smem_offset -= KTraits::CTA_TILE_KV * UPCAST_STRIDE;
  } else {
    uint32_t kv_idx = kv_idx_base + warp_idx * 8 + lane_idx / 4;
    // NOTE: NUM_MMA_KV * 2 / NUM_WARPS_Q = NUM_WARPS_KV * NUM_MMA_KV * 2 / num_warps
    static_assert(NUM_MMA_KV * 2 % NUM_WARPS_Q == 0);
#pragma unroll
    for (uint32_t i = 0; i < NUM_MMA_KV * 2 / NUM_WARPS_Q; ++i) {
      DType* gptr = kv_ptr + thr_local_kv_offset[i];
      if constexpr (IS_FP4) {
        smem.load_64b_async<fill_mode>(*smem_offset, gptr, kv_idx < kv_len);
      } else {
        smem.load_128b_async<fill_mode>(*smem_offset, gptr, kv_idx < kv_len);
      }
      kv_idx += NUM_WARPS * 8;
      *smem_offset =
          smem.template advance_offset_by_row<NUM_WARPS * 8, UPCAST_STRIDE>(*smem_offset);
    }
    *smem_offset -= KTraits::CTA_TILE_KV * UPCAST_STRIDE;
  }
}

template <bool produce_v, typename KTraits, typename PagedKV>
__device__ __forceinline__ size_t get_paged_kv_offset_for_logical_row(
    const PagedKV& paged_kv, const uint32_t packed_page_iter_base,
    const typename KTraits::IdType last_indptr, const uint32_t kv_head_idx,
    const uint32_t logical_row, const uint32_t lane_idx) {
  using DType = typename KTraits::DTypeKV;
  constexpr bool IS_FP4 = is_fp4_type_v<DType>;
  constexpr uint32_t KV_THR_LAYOUT_COL = KTraits::KV_THR_LAYOUT_COL;
  uint32_t page_iter, entry_idx;
  paged_kv.page_size.divmod(packed_page_iter_base + logical_row, page_iter, entry_idx);
  const uint32_t feat_idx =
      (lane_idx % KV_THR_LAYOUT_COL) * upcast_size<DType>() / (IS_FP4 ? 2 : 1);
  // The K and V pools may carry different strides (e.g. NVFP4 VO-split caches
  // where head_dim_qk != head_dim_vo), so route V rows through the V strides.
  if constexpr (produce_v) {
    return paged_kv.protective_get_v_offset(page_iter, kv_head_idx, entry_idx, feat_idx,
                                            last_indptr);
  } else {
    return paged_kv.protective_get_k_offset(page_iter, kv_head_idx, entry_idx, feat_idx,
                                            last_indptr);
  }
}

template <bool produce_v, typename KTraits, typename SmemStorage, typename PagedKV>
__device__ __forceinline__ void page_produce_kv_on_the_fly(
    SmemStorage* smem_storage, uint32_t* smem_offset, typename KTraits::DTypeKV* kv_ptr,
    const PagedKV& paged_kv, const uint32_t packed_page_iter_base,
    const typename KTraits::IdType last_indptr, const uint32_t kv_head_idx,
    const uint32_t kv_idx_base, const uint32_t kv_len, const uint32_t warp_idx,
    const uint32_t lane_idx) {
  smem_t<KTraits::SWIZZLE_MODE_KV> smem(
      (produce_v && !KTraits::USE_KV_SHARED_SMEM) ? smem_storage->v_smem : smem_storage->k_smem);
  using DType = typename KTraits::DTypeKV;
  constexpr SharedMemFillMode fill_mode =
      produce_v ? SharedMemFillMode::kFillZero : SharedMemFillMode::kNoFill;
  constexpr uint32_t NUM_WARPS = KTraits::NUM_WARPS;
  constexpr uint32_t NUM_WARPS_Q = KTraits::NUM_WARPS_Q;
  constexpr uint32_t NUM_MMA_KV = KTraits::NUM_MMA_KV;
  constexpr uint32_t NUM_MMA_D = produce_v ? KTraits::NUM_MMA_D_VO : KTraits::NUM_MMA_D_QK;
  constexpr uint32_t UPCAST_STRIDE =
      produce_v ? KTraits::UPCAST_STRIDE_V : KTraits::UPCAST_STRIDE_K;
  constexpr bool IS_FP4 = is_fp4_type_v<DType>;

  if constexpr (KTraits::SWIZZLE_MODE_KV == SwizzleMode::k128B) {
    constexpr uint32_t ROWS_PER_ITER = 4;
    uint32_t kv_idx = kv_idx_base + warp_idx * ROWS_PER_ITER + lane_idx / 8;
    static_assert(NUM_MMA_KV * ROWS_PER_ITER % NUM_WARPS_Q == 0);
#pragma unroll
    for (uint32_t i = 0; i < NUM_MMA_KV * ROWS_PER_ITER / NUM_WARPS_Q; ++i) {
      const uint32_t logical_row =
          warp_idx * ROWS_PER_ITER + lane_idx / 8 + NUM_WARPS * ROWS_PER_ITER * i;
      DType* gptr = kv_ptr + get_paged_kv_offset_for_logical_row<produce_v, KTraits>(
                                 paged_kv, packed_page_iter_base, last_indptr, kv_head_idx,
                                 logical_row, lane_idx);
#pragma unroll
      for (uint32_t j = 0; j < NUM_MMA_D / (8 / sizeof(DType)); ++j) {
        if constexpr (IS_FP4) {
          smem.template load_64b_async<fill_mode>(*smem_offset, gptr, kv_idx < kv_len);
        } else {
          smem.template load_128b_async<fill_mode>(*smem_offset, gptr, kv_idx < kv_len);
        }
        *smem_offset = smem.template advance_offset_by_column<8>(*smem_offset, j);
        gptr += (IS_FP4 ? 4 : 8) * upcast_size<DType>();
      }
      kv_idx += NUM_WARPS * ROWS_PER_ITER;
      *smem_offset = smem.template advance_offset_by_row<NUM_WARPS * ROWS_PER_ITER, UPCAST_STRIDE>(
                         *smem_offset) -
                     sizeof(DType) * NUM_MMA_D;
    }
    *smem_offset -= KTraits::CTA_TILE_KV * UPCAST_STRIDE;
  } else {
    constexpr uint32_t ROWS_PER_ITER = 8;
    uint32_t kv_idx = kv_idx_base + warp_idx * ROWS_PER_ITER + lane_idx / 4;
    static_assert(NUM_MMA_KV * (ROWS_PER_ITER / 4) % NUM_WARPS_Q == 0);
#pragma unroll
    for (uint32_t i = 0; i < NUM_MMA_KV * (ROWS_PER_ITER / 4) / NUM_WARPS_Q; ++i) {
      const uint32_t logical_row =
          warp_idx * ROWS_PER_ITER + lane_idx / 4 + NUM_WARPS * ROWS_PER_ITER * i;
      DType* gptr = kv_ptr + get_paged_kv_offset_for_logical_row<produce_v, KTraits>(
                                 paged_kv, packed_page_iter_base, last_indptr, kv_head_idx,
                                 logical_row, lane_idx);
      if constexpr (IS_FP4) {
        smem.template load_64b_async<fill_mode>(*smem_offset, gptr, kv_idx < kv_len);
      } else {
        smem.template load_128b_async<fill_mode>(*smem_offset, gptr, kv_idx < kv_len);
      }
      kv_idx += NUM_WARPS * ROWS_PER_ITER;
      *smem_offset = smem.template advance_offset_by_row<NUM_WARPS * ROWS_PER_ITER, UPCAST_STRIDE>(
          *smem_offset);
    }
    *smem_offset -= KTraits::CTA_TILE_KV * UPCAST_STRIDE;
  }
}

/*!
 * \brief Load NVFP4 KV scale-factors for one CTA tile into shared memory.
 *
 * Uses a fixed thread mapping independent of KV swizzle mode: each thread
 * (thread_id = warp_idx * 32 + lane_idx) issues a 32-bit LDGSTS to load 4 consecutive
 * SF bytes per iteration, advancing by NUM_WARPS * 128 bytes across iterations.
 * The SF smem layout is a plain flat byte array — no swizzle.
 *
 * SF strides are passed explicitly by the caller instead of being derived from KV data strides.
 * This lets runtimes pass scale-factor tensors from interleaved or separately allocated KV pools.
 * No-op when KTraits::DTypeKV is not FP4.
 *
 * \tparam produce_v  true → fill v_sf_smem, false → fill k_sf_smem.
 * \tparam KTraits    Kernel traits type.
 * \tparam IdType     Page index type (deduced from indices).
 * \param smem_storage        Shared storage holding k_sf_smem / v_sf_smem.
 * \param sf_ptr              Base pointer to the flat uint8_t SF array (K or V).
 * \param packed_page_iter_base  Packed page-iter for the start of this CTA tile.
 * \param packed_kv_bound     Upper bound for valid packed page-iters (last_indptr * page_size).
 * \param kv_head_idx         KV head index.
 * \param sf_stride_page      Byte stride per page in the scale tensor.
 * \param sf_stride_h         Byte stride per head in the scale tensor.
 * \param sf_stride_n         Byte stride per token in the scale tensor.
 * \param page_size           Page size (fast divisor).
 * \param indices             Page index array.
 * \param kv_idx_base         First KV row index for this tile within the chunk.
 * \param kv_len              Chunk size; rows at or beyond this are not loaded.
 * \param warp_idx            Global warp index within the CTA.
 * \param lane_idx            Lane index within the warp.
 */
#ifndef FLASHINFER_PAGED_V_SF_DESWIZZLE
#define FLASHINFER_PAGED_V_SF_DESWIZZLE 0
#endif

template <bool produce_v, typename KTraits, typename SmemStorage, typename IdType>
__device__ __forceinline__ void page_produce_kv_sf(
    SmemStorage* smem_storage, uint8_t* sf_ptr, const uint32_t packed_page_iter_base,
    const uint32_t packed_kv_bound, const uint32_t kv_head_idx, const uint32_t sf_stride_page,
    const uint32_t sf_stride_h, const uint32_t sf_stride_n, const uint_fastdiv& page_size,
    const IdType* indices, const uint32_t kv_idx_base, const uint32_t kv_len,
    const uint32_t warp_idx, const uint32_t lane_idx) {
  // Wrap the whole body (not an early return) so that for non-FP4 KV the pointer arithmetic on the
  // nullptr returned by *_sf_smem_ptr() is never instantiated (null + nonzero offset is UB).
  if constexpr (is_fp4_type_v<typename KTraits::DTypeKV>) {
    constexpr uint32_t HEAD_DIM = produce_v ? KTraits::HEAD_DIM_VO : KTraits::HEAD_DIM_QK;
    constexpr uint32_t SF_COLS = HEAD_DIM / NVFP4_SF_VEC_SIZE;  // SF bytes per KV row
    constexpr uint32_t NUM_WARPS = KTraits::NUM_WARPS;
    constexpr uint32_t CTA_TILE_KV = KTraits::CTA_TILE_KV;
    constexpr uint32_t SF_TOTAL_BYTES = CTA_TILE_KV * SF_COLS;
    static_assert(SF_TOTAL_BYTES % 4 == 0, "SF smem size must be 4-byte aligned for 32-bit LDGSTS");
    // Each thread loads 4 SF bytes (32 bits) per iteration via LDGSTS.32.
    constexpr uint32_t THREADS_PER_CTA = NUM_WARPS * 32;
    constexpr uint32_t NUM_SF_ITERS = (SF_TOTAL_BYTES / 4 + THREADS_PER_CTA - 1) / THREADS_PER_CTA;

    uint8_t* sf_smem = produce_v ? smem_storage->v_sf_smem_ptr() : smem_storage->k_sf_smem_ptr();
    const uint32_t thread_id = warp_idx * 32 + lane_idx;

#pragma unroll
    for (uint32_t k = 0; k < NUM_SF_ITERS; ++k) {
      const uint32_t flat_uint32_idx = thread_id + k * THREADS_PER_CTA;
      const uint32_t flat_byte = flat_uint32_idx * 4;
      // sf_smem_col is 4-byte aligned: flat_byte is a multiple of 4, and SF_COLS is a power of 2
      // (HEAD_DIM / 16), so flat_byte % SF_COLS is always a multiple of 4 (or 0 when SF_COLS < 4).
      const uint32_t sf_smem_row = flat_byte / SF_COLS;
      const uint32_t sf_smem_col = flat_byte % SF_COLS;
      // For k < NUM_SF_ITERS-1, (flat_byte < SF_TOTAL_BYTES) is always true (optimized away).
      const bool in_bounds = (flat_byte < SF_TOTAL_BYTES) && (kv_idx_base + sf_smem_row < kv_len);

      // packed_kv_bound guards indices[] access; returns offset 0 for out-of-range rows.
      uint32_t page_iter, entry_idx;
      const uint32_t packed_block_iter = packed_page_iter_base + sf_smem_row;
      page_size.divmod(packed_block_iter, page_iter, entry_idx);
      const size_t page_head_base =
          static_cast<size_t>(packed_block_iter < packed_kv_bound ? indices[page_iter] : 0) *
              sf_stride_page +
          kv_head_idx * sf_stride_h;

      if constexpr (produce_v && FLASHINFER_PAGED_V_SF_DESWIZZLE) {
        static_assert(SF_COLS % 4 == 0,
                      "Paged V-SF de-swizzle requires HEAD_DIM_VO divisible by 64");
        uint32_t packed = 0;
        if (in_bounds) {
          constexpr uint32_t SF_GROUPS = SF_COLS / 4;
          const uint32_t a4 = entry_idx & ~3u;
          const uint32_t e = entry_idx & 3u;
          uint8_t* packed_bytes = reinterpret_cast<uint8_t*>(&packed);
#pragma unroll
          for (uint32_t j = 0; j < 4; ++j) {
            const uint32_t dcol = sf_smem_col + j;
            const uint32_t swz_entry = a4 + dcol / SF_GROUPS;
            const uint32_t swz_sd = (dcol % SF_GROUPS) * 4 + e;
            packed_bytes[j] =
                sf_ptr[page_head_base + static_cast<size_t>(swz_entry) * sf_stride_n + swz_sd];
          }
        }
        // NUM_SF_ITERS is rounded up, so the last iter has lanes with
        // flat_byte >= SF_TOTAL_BYTES. Unlike the predicated cp_async store below,
        // this write is unconditional, so guard it on the buffer bound (not in_bounds,
        // which would also skip the intended zero-fill of in-buffer padding rows).
        if (flat_byte < SF_TOTAL_BYTES) {
          *reinterpret_cast<uint32_t*>(sf_smem + flat_byte) = packed;
        }
      } else {
        const size_t sf_gmem_offset = page_head_base + entry_idx * sf_stride_n + sf_smem_col;
        // V SF must zero-fill out-of-bounds entries: compute_sfm_v reads SF for all CTA_TILE_KV
        // rows including padding, and 0 (softmax weight) * NaN (uninitialized SF) = NaN (IEEE 754).
        // K SF can use kNoFill since NaN K scores are replaced by -inf via logits_mask before
        // update_mdo_states, so they never reach the accumulator.
        constexpr auto fill_mode = produce_v ? cp_async::SharedMemFillMode::kFillZero
                                             : cp_async::SharedMemFillMode::kNoFill;
        cp_async::pred_load_32b<fill_mode>(
            reinterpret_cast<uint32_t*>(sf_smem + flat_byte),
            reinterpret_cast<const uint32_t*>(sf_ptr + sf_gmem_offset), in_bounds);
      }
    }
  }
}

/*!
 * \brief Load NVFP4 KV scale-factors for one CTA tile (contiguous/ragged layout).
 *
 * Contiguous analog of page_produce_kv_sf — no page indirection.
 * kv_abs_base is the absolute first token index for this CTA tile
 * (kv_indptr[request_idx] + chunk_start for ragged, chunk_start for single prefill).
 * SF strides are KV byte strides / SF_CONTAINERS (exact for all valid head_dims).
 * No-op when DTypeKV is not FP4.
 *
 * \tparam produce_v  true → fill v_sf_smem, false → fill k_sf_smem.
 * \tparam KTraits    Kernel traits type.
 * \param smem_storage        Shared storage holding k_sf_smem / v_sf_smem.
 * \param sf_ptr              Base pointer to the flat uint8_t SF array (K or V).
 * \param kv_abs_base         Absolute first token index for this CTA tile.
 * \param kv_head_idx         KV head index.
 * \param sf_stride_n         Byte stride per token in the SF tensor.
 * \param sf_stride_h         Byte stride per head in the SF tensor.
 * \param kv_idx_base         First KV row index for this tile within the chunk.
 * \param kv_len              Chunk size; rows at or beyond this are not loaded.
 * \param warp_idx            Global warp index within the CTA.
 * \param lane_idx            Lane index within the warp.
 */
template <bool produce_v, typename KTraits, typename SmemStorage>
__device__ __forceinline__ void produce_kv_sf(SmemStorage* smem_storage, uint8_t* sf_ptr,
                                              const uint32_t kv_abs_base,
                                              const uint32_t kv_head_idx,
                                              const uint32_t kv_stride_n,
                                              const uint32_t kv_stride_h,
                                              const uint32_t kv_idx_base, const uint32_t kv_len,
                                              const uint32_t warp_idx, const uint32_t lane_idx) {
  // Wrap the whole body (not an early return) so that for non-FP4 KV the pointer arithmetic on the
  // nullptr returned by *_sf_smem_ptr() is never instantiated (null + nonzero offset is UB).
  if constexpr (is_fp4_type_v<typename KTraits::DTypeKV>) {
    constexpr uint32_t HEAD_DIM = produce_v ? KTraits::HEAD_DIM_VO : KTraits::HEAD_DIM_QK;
    constexpr uint32_t SF_COLS = HEAD_DIM / NVFP4_SF_VEC_SIZE;
    constexpr uint32_t NUM_WARPS = KTraits::NUM_WARPS;
    constexpr uint32_t CTA_TILE_KV = KTraits::CTA_TILE_KV;
    // DTypeKV containers per SF byte: NVFP4_SF_VEC_SIZE FP4 / 2 FP4-per-container.
    constexpr uint32_t SF_CONTAINERS = NVFP4_SF_VEC_SIZE / 2;  // = 8
    constexpr uint32_t SF_TOTAL_BYTES = CTA_TILE_KV * SF_COLS;
    static_assert(SF_TOTAL_BYTES % 4 == 0, "SF smem size must be 4-byte aligned for 32-bit LDGSTS");
    // Each thread loads 4 SF bytes (32 bits) per iteration via LDGSTS.32.
    constexpr uint32_t THREADS_PER_CTA = NUM_WARPS * 32;
    constexpr uint32_t NUM_SF_ITERS = (SF_TOTAL_BYTES / 4 + THREADS_PER_CTA - 1) / THREADS_PER_CTA;

    uint8_t* sf_smem = produce_v ? smem_storage->v_sf_smem_ptr() : smem_storage->k_sf_smem_ptr();
    const uint32_t thread_id = warp_idx * 32 + lane_idx;
    const uint32_t sf_stride_n = kv_stride_n / SF_CONTAINERS;
    const uint32_t sf_stride_h = kv_stride_h / SF_CONTAINERS;

#pragma unroll
    for (uint32_t i = 0; i < NUM_SF_ITERS; ++i) {
      const uint32_t flat_byte = (thread_id + i * THREADS_PER_CTA) * 4;
      const uint32_t sf_smem_row = flat_byte / SF_COLS;
      const uint32_t sf_smem_col = flat_byte % SF_COLS;
      const uint32_t abs_kv_row = kv_idx_base + sf_smem_row;
      const bool in_bounds = (flat_byte < SF_TOTAL_BYTES) && (abs_kv_row < kv_len);
      const size_t sf_gmem_offset =
          in_bounds ? (static_cast<size_t>(kv_abs_base + abs_kv_row) * sf_stride_n +
                       kv_head_idx * sf_stride_h + sf_smem_col)
                    : 0;
      // Same rationale as page_produce_kv_sf: zero-fill V SF to prevent 0*NaN=NaN in compute_sfm_v.
      constexpr auto fill_mode =
          produce_v ? cp_async::SharedMemFillMode::kFillZero : cp_async::SharedMemFillMode::kNoFill;
      cp_async::pred_load_32b<fill_mode>(reinterpret_cast<uint32_t*>(sf_smem + flat_byte),
                                         reinterpret_cast<const uint32_t*>(sf_ptr + sf_gmem_offset),
                                         in_bounds);
    }
  }
}

template <typename KTraits>
__device__ __forceinline__ float compute_rope_freq(const uint32_t mma_d, const uint32_t j,
                                                   const float rope_rcp_scale,
                                                   const float rope_rcp_theta,
                                                   const uint32_t lane_mod) {
  constexpr uint32_t HEAD_DIM = KTraits::NUM_MMA_D_QK * 16;
  return rope_rcp_scale *
         __powf(rope_rcp_theta,
                float(2 * ((mma_d * 16 + (j / 2) * 8 + lane_mod * 2 + (j % 2)) % (HEAD_DIM / 2))) /
                    float(HEAD_DIM));
}

template <typename KTraits>
__device__ __forceinline__ void init_rope_freq(float (*rope_freq)[4], const float rope_rcp_scale,
                                               const float rope_rcp_theta,
                                               const uint32_t tid_x = threadIdx.x) {
  const uint32_t lane_idx = tid_x;
#pragma unroll
  // rope_freq is sized [NUM_MMA_D_QK/2][4] and the rotary appliers index it up to
  // NUM_MMA_D_QK/2; for asymmetric QK/VO (e.g. qk=512, vo=256) NUM_MMA_D_VO/2 would
  // leave the upper half of the table uninitialized, so bound on QK.
  for (uint32_t mma_d = 0; mma_d < KTraits::NUM_MMA_D_QK / 2; ++mma_d) {
#pragma unroll
    for (uint32_t j = 0; j < 4; ++j) {
      rope_freq[mma_d][j] =
          compute_rope_freq<KTraits>(mma_d, j, rope_rcp_scale, rope_rcp_theta, lane_idx % 4);
    }
  }
}

template <typename KTraits>
__device__ __forceinline__ void init_shared_rope_freq(
    float (*rope_freq_smem)[KTraits::NUM_MMA_D_QK / 2][4], const float rope_rcp_scale,
    const float rope_rcp_theta, const dim3 tid = threadIdx) {
  static_assert(KTraits::USE_SHARED_ROPE_FREQ);
  static_assert(KTraits::NUM_MMA_D_QK == KTraits::NUM_MMA_D_VO);
  constexpr uint32_t NUM_ROWS = KTraits::NUM_MMA_D_QK / 2;
  constexpr uint32_t NUM_ELEMS = 4 * NUM_ROWS * 4;
  const uint32_t thread_id = get_warp_idx<KTraits>(tid.y, tid.z) * WARP_SIZE + tid.x;

  for (uint32_t elem = thread_id; elem < NUM_ELEMS; elem += KTraits::NUM_THREADS) {
    const uint32_t j = elem % 4;
    const uint32_t mma_d = (elem / 4) % NUM_ROWS;
    const uint32_t lane_mod = elem / (NUM_ROWS * 4);
    rope_freq_smem[lane_mod][mma_d][j] =
        compute_rope_freq<KTraits>(mma_d, j, rope_rcp_scale, rope_rcp_theta, lane_mod);
  }
}

template <typename KTraits>
__device__ __forceinline__ void init_states(typename KTraits::AttentionVariant variant,
                                            float (*o_frag)[KTraits::NUM_MMA_D_VO_TILE][8],
                                            typename KTraits::DTypeQKAccum (*m)[2], float (*d)[2]) {
#pragma unroll
  for (uint32_t mma_q = 0; mma_q < KTraits::NUM_MMA_Q; ++mma_q) {
#pragma unroll
    for (uint32_t mma_d = 0; mma_d < KTraits::NUM_MMA_D_VO_TILE; ++mma_d) {
#pragma unroll
      for (uint32_t reg_id = 0; reg_id < 8; ++reg_id) {
        o_frag[mma_q][mma_d][reg_id] = 0.f;
      }
    }
  }

  if constexpr (variant.use_softmax) {
#pragma unroll
    for (uint32_t mma_q = 0; mma_q < KTraits::NUM_MMA_Q; ++mma_q) {
#pragma unroll
      for (uint32_t j = 0; j < 2; ++j) {
        m[mma_q][j] = typename KTraits::DTypeQKAccum(-math::inf);
        d[mma_q][j] = 1.f;
      }
    }
  }
}

template <typename KTraits>
__device__ __forceinline__ void load_q_global_smem(
    uint32_t packed_offset, const uint32_t qo_upper_bound, typename KTraits::DTypeQ* q_ptr_base,
    const uint32_t q_stride_n, const uint32_t q_stride_h, const uint_fastdiv group_size,
    smem_t<KTraits::SWIZZLE_MODE_Q>* q_smem, const dim3 tid = threadIdx) {
  using DTypeQ = typename KTraits::DTypeQ;
  constexpr uint32_t UPCAST_STRIDE_Q = KTraits::UPCAST_STRIDE_Q;
  const uint32_t lane_idx = tid.x, warp_idx_x = get_warp_idx_q<KTraits>(tid.y);

  if (get_warp_idx_kv<KTraits>(tid.z) == 0) {
    uint32_t q_smem_offset_w = q_smem->template get_permuted_offset<UPCAST_STRIDE_Q>(
        warp_idx_x * KTraits::NUM_MMA_Q * 16 + lane_idx / 8, lane_idx % 8);

#pragma unroll
    for (uint32_t mma_q = 0; mma_q < KTraits::NUM_MMA_Q; ++mma_q) {
#pragma unroll
      for (uint32_t j = 0; j < 2 * 2; ++j) {
        uint32_t q, r;
        group_size.divmod(packed_offset + lane_idx / 8 + mma_q * 16 + j * 4, q, r);
        const uint32_t q_idx = q;
        DTypeQ* q_ptr =
            q_ptr_base + q * q_stride_n + r * q_stride_h + (lane_idx % 8) * upcast_size<DTypeQ>();
#pragma unroll
        for (uint32_t mma_do = 0; mma_do < KTraits::NUM_MMA_D_QK / 4; ++mma_do) {
          // load q fragment from gmem to smem
          q_smem->template load_128b_async<SharedMemFillMode::kNoFill>(q_smem_offset_w, q_ptr,
                                                                       q_idx < qo_upper_bound);
          q_smem_offset_w = q_smem->template advance_offset_by_column<8>(q_smem_offset_w, mma_do);
          q_ptr += 8 * upcast_size<DTypeQ>();
        }
        q_smem_offset_w =
            q_smem->template advance_offset_by_row<4, UPCAST_STRIDE_Q>(q_smem_offset_w) -
            2 * KTraits::NUM_MMA_D_QK;
      }
    }
  }
}

template <typename KTraits>
__device__ __forceinline__ void q_smem_inplace_apply_rotary(
    const uint32_t q_packed_idx, const uint32_t qo_len, const uint32_t kv_len,
    const uint_fastdiv group_size, smem_t<KTraits::SWIZZLE_MODE_Q>* q_smem,
    uint32_t* q_smem_offset_r, float (*rope_freq)[4], const dim3 tid = threadIdx) {
  if (get_warp_idx_kv<KTraits>(tid.z) == 0) {
    constexpr uint32_t UPCAST_STRIDE_Q = KTraits::UPCAST_STRIDE_Q;
    const uint32_t lane_idx = tid.x;
    uint32_t q_frag_local[2][4];
    static_assert(KTraits::NUM_MMA_D_QK % 4 == 0, "NUM_MMA_D_QK must be a multiple of 4");
#pragma unroll
    for (uint32_t mma_q = 0; mma_q < KTraits::NUM_MMA_Q; ++mma_q) {
      uint32_t q_smem_offset_r_first_half = *q_smem_offset_r;
#pragma unroll
      for (uint32_t mma_di = 0; mma_di < KTraits::NUM_MMA_D_QK / 2; ++mma_di) {
        q_smem->ldmatrix_m8n8x4(q_smem_offset_r_first_half, q_frag_local[0]);
        uint32_t q_smem_offset_r_last_half =
            q_smem->template advance_offset_by_column<KTraits::NUM_MMA_D_QK>(
                q_smem_offset_r_first_half, 0);
        q_smem->ldmatrix_m8n8x4(q_smem_offset_r_last_half, q_frag_local[1]);
        q_frag_apply_llama_rope<typename KTraits::DTypeQ>(
            (typename KTraits::DTypeQ*)q_frag_local[0], (typename KTraits::DTypeQ*)q_frag_local[1],
            rope_freq[mma_di],
            q_packed_idx + kv_len * group_size - qo_len * group_size + mma_q * 16 + lane_idx / 4,
            group_size);
        q_smem->stmatrix_m8n8x4(q_smem_offset_r_last_half, q_frag_local[1]);
        q_smem->stmatrix_m8n8x4(q_smem_offset_r_first_half, q_frag_local[0]);
        q_smem_offset_r_first_half =
            q_smem->template advance_offset_by_column<2>(q_smem_offset_r_first_half, mma_di);
      }
      *q_smem_offset_r += 16 * UPCAST_STRIDE_Q;
    }
    *q_smem_offset_r -= KTraits::NUM_MMA_Q * 16 * UPCAST_STRIDE_Q;
  }
}

template <typename KTraits>
__device__ __forceinline__ void q_smem_inplace_apply_rotary_with_pos(
    const uint32_t q_packed_idx_base, const typename KTraits::IdType* q_rope_offset,
    smem_t<KTraits::SWIZZLE_MODE_Q>* q_smem, const uint_fastdiv group_size,
    uint32_t* q_smem_offset_r, float (*rope_freq)[4], const dim3 tid = threadIdx) {
  if (get_warp_idx_kv<KTraits>(tid.z) == 0) {
    constexpr uint32_t UPCAST_STRIDE_Q = KTraits::UPCAST_STRIDE_Q;
    const uint32_t lane_idx = tid.x;
    uint32_t q_frag_local[2][4];
    static_assert(KTraits::NUM_MMA_D_QK % 4 == 0, "NUM_MMA_D_QK must be a multiple of 4");
#pragma unroll
    for (uint32_t mma_q = 0; mma_q < KTraits::NUM_MMA_Q; ++mma_q) {
      uint32_t q_smem_offset_r_first_half = *q_smem_offset_r;
#pragma unroll
      for (uint32_t mma_di = 0; mma_di < KTraits::NUM_MMA_D_QK / 2; ++mma_di) {
        q_smem->ldmatrix_m8n8x4(q_smem_offset_r_first_half, q_frag_local[0]);
        uint32_t q_smem_offset_r_last_half =
            q_smem->template advance_offset_by_column<KTraits::NUM_MMA_D_QK>(
                q_smem_offset_r_first_half, 0);
        q_smem->ldmatrix_m8n8x4(q_smem_offset_r_last_half, q_frag_local[1]);
        q_frag_apply_llama_rope_with_pos<typename KTraits::DTypeQ, typename KTraits::IdType>(
            (typename KTraits::DTypeQ*)q_frag_local[0], (typename KTraits::DTypeQ*)q_frag_local[1],
            rope_freq[mma_di], q_packed_idx_base + mma_q * 16 + lane_idx / 4, group_size,
            q_rope_offset);
        q_smem->stmatrix_m8n8x4(q_smem_offset_r_last_half, q_frag_local[1]);
        q_smem->stmatrix_m8n8x4(q_smem_offset_r_first_half, q_frag_local[0]);
        q_smem_offset_r_first_half =
            q_smem->template advance_offset_by_column<2>(q_smem_offset_r_first_half, mma_di);
      }
      *q_smem_offset_r += 16 * UPCAST_STRIDE_Q;
    }
    *q_smem_offset_r -= KTraits::NUM_MMA_Q * 16 * UPCAST_STRIDE_Q;
  }
}

template <typename KTraits>
__device__ __forceinline__ void k_smem_inplace_apply_rotary(
    const uint32_t kv_idx_base, smem_t<KTraits::SWIZZLE_MODE_KV>* k_smem, uint32_t* k_smem_offset_r,
    float (*rope_freq)[4], const dim3 tid = threadIdx) {
  using DTypeKV = typename KTraits::DTypeKV;
  static_assert(sizeof(DTypeKV) == 2);
  constexpr uint32_t UPCAST_STRIDE_K = KTraits::UPCAST_STRIDE_K;
  uint32_t k_frag_local[2][4];
  const uint32_t lane_idx = tid.x;
  if constexpr (KTraits::NUM_MMA_D_QK == 4 && KTraits::NUM_WARPS_Q == 4) {
    static_assert(KTraits::NUM_WARPS_KV == 1);
    const uint32_t warp_idx = get_warp_idx_q<KTraits>(tid.y);
    // horizontal-axis: y
    // vertical-axis: z
    //         | 1-16       | 16-32      | 32-48      | 48-64      |
    // | 1-16  | warp_idx=0 | warp_idx=1 | warp_idx=0 | warp_idx=1 |
    // | 16-32 | warp_idx=2 | warp_idx=3 | warp_idx=2 | warp_idx=3 |
    static_assert(KTraits::NUM_MMA_KV % 2 == 0,
                  "when NUM_MMA_D_QK == 4, NUM_MMA_KV must be a multiple of 2");
    uint32_t kv_idx = kv_idx_base + (warp_idx / 2) * 16 + lane_idx / 4;
    *k_smem_offset_r =
        (*k_smem_offset_r ^ (0x2 * (warp_idx % 2))) + (warp_idx / 2) * 16 * UPCAST_STRIDE_K;
#pragma unroll
    for (uint32_t i = 0; i < KTraits::NUM_MMA_KV / 2; ++i) {
      uint32_t k_smem_offset_r_first_half = *k_smem_offset_r;
      uint32_t mma_di = (warp_idx % 2);
      k_smem->ldmatrix_m8n8x4(k_smem_offset_r_first_half, k_frag_local[0]);
      uint32_t k_smem_offset_r_last_half =
          k_smem->template advance_offset_by_column<4>(k_smem_offset_r_first_half, 0);
      k_smem->ldmatrix_m8n8x4(k_smem_offset_r_last_half, k_frag_local[1]);
      k_frag_apply_llama_rope<DTypeKV>((DTypeKV*)k_frag_local[0], (DTypeKV*)k_frag_local[1],
                                       rope_freq[mma_di], kv_idx);
      k_smem->stmatrix_m8n8x4(k_smem_offset_r_last_half, k_frag_local[1]);
      k_smem->stmatrix_m8n8x4(k_smem_offset_r_first_half, k_frag_local[0]);
      *k_smem_offset_r += 32 * UPCAST_STRIDE_K;
      kv_idx += 32;
    }
    *k_smem_offset_r = (*k_smem_offset_r ^ (0x2 * (warp_idx % 2))) -
                       ((warp_idx / 2) + KTraits::NUM_MMA_KV) * 16 * UPCAST_STRIDE_K;
  } else {
    const uint32_t warp_idx_x = get_warp_idx_q<KTraits>(tid.y),
                   warp_idx_z = get_warp_idx_kv<KTraits>(tid.z);
    static_assert(KTraits::NUM_MMA_D_QK % (2 * KTraits::NUM_WARPS_Q) == 0);
    // horizontal axis: y
    // vertical axis: z
    // | (warp_idx_z, warp_idx_x)       | 1-16   | 16-32  | 32-48  | 48-64  | ...
    // | 1-16*NUM_MMA_KV                | (0, 0) | (0, 1) | (0, 2) | (0, 3) | ...
    // | 16*NUM_MMA_KV-32*NUM_MMA_KV    | (1, 0) | (1, 1) | (1, 2) | (1, 3) | ...
    // ...
    uint32_t kv_idx = kv_idx_base + (warp_idx_z * KTraits::NUM_MMA_KV * 16) + lane_idx / 4;
    *k_smem_offset_r = *k_smem_offset_r ^ (0x2 * warp_idx_x);
#pragma unroll
    for (uint32_t i = 0; i < KTraits::NUM_MMA_KV; ++i) {
      uint32_t k_smem_offset_r_first_half = *k_smem_offset_r;
#pragma unroll
      for (uint32_t j = 0; j < KTraits::NUM_MMA_D_QK / (2 * KTraits::NUM_WARPS_Q); ++j) {
        uint32_t mma_di = warp_idx_x + j * KTraits::NUM_WARPS_Q;
        k_smem->ldmatrix_m8n8x4(k_smem_offset_r_first_half, k_frag_local[0]);
        uint32_t k_smem_offset_r_last_half =
            k_smem->template advance_offset_by_column<KTraits::NUM_MMA_D_QK>(
                k_smem_offset_r_first_half, 0);
        k_smem->ldmatrix_m8n8x4(k_smem_offset_r_last_half, k_frag_local[1]);
        k_frag_apply_llama_rope<DTypeKV>((DTypeKV*)k_frag_local[0], (DTypeKV*)k_frag_local[1],
                                         rope_freq[mma_di], kv_idx);
        k_smem->stmatrix_m8n8x4(k_smem_offset_r_last_half, k_frag_local[1]);
        k_smem->stmatrix_m8n8x4(k_smem_offset_r_first_half, k_frag_local[0]);
        k_smem_offset_r_first_half =
            k_smem->template advance_offset_by_column<2 * KTraits::NUM_WARPS_Q>(
                k_smem_offset_r_first_half, j);
      }
      *k_smem_offset_r += 16 * UPCAST_STRIDE_K;
      kv_idx += 16;
    }
    *k_smem_offset_r =
        (*k_smem_offset_r ^ (0x2 * warp_idx_x)) - KTraits::NUM_MMA_KV * 16 * UPCAST_STRIDE_K;
  }
}

// Dequantize one FP8 K or V tile from its packed smem buffer into a BF16/FP16
// staging buffer, laid out exactly as a native 16-bit tile (same k128B swizzle).
// Shuffle-free, vectorized: each thread reads packed 16-byte (b128) chunks of 16
// FP8 elements and writes two 16-byte chunks of 8 BF16 elements. Afterwards the
// QK/PV MMAs use the standard 16-bit ldmatrix path (no per-fragment cross-lane
// swizzle). Numerically identical to the in-loop dequant.
template <typename KTraits, uint32_t HEAD_DIM>
__device__ __forceinline__ void repack_fp8_tile_to_bf16(typename KTraits::DTypeKV* smem_fp8,
                                                        typename KTraits::DTypeQ* smem_bf16,
                                                        uint32_t thread_id) {
  using DTypeKV = typename KTraits::DTypeKV;
  using DTypeQ = typename KTraits::DTypeQ;
  constexpr SwizzleMode SWIZZLE = KTraits::SWIZZLE_MODE_KV;
  constexpr uint32_t NUM_THREADS = KTraits::NUM_THREADS;
  constexpr uint32_t CTA_TILE_KV = KTraits::CTA_TILE_KV;
  // b128 columns per row: FP8 packs 16 elems / 16B, BF16 packs 8 elems / 16B.
  constexpr uint32_t FP8_COLS = HEAD_DIM / upcast_size<DTypeKV>();
  constexpr uint32_t BF16_COLS = HEAD_DIM / upcast_size<DTypeQ>();
  constexpr uint32_t NUM_B128 = CTA_TILE_KV * FP8_COLS;

  b128_t* src = (b128_t*)smem_fp8;
  b128_t* dst = (b128_t*)smem_bf16;
#pragma unroll
  for (uint32_t idx = thread_id; idx < NUM_B128; idx += NUM_THREADS) {
    uint32_t row = idx / FP8_COLS, col = idx % FP8_COLS;
    b128_t packed = src[get_permuted_offset<SWIZZLE, FP8_COLS>(row, col)];
    // alignas(16): conv is reinterpreted as b128_t (16B) for the smem stores below,
    // so it must be 16-byte aligned (DTypeQ alone only guarantees 2B alignment).
    alignas(16) DTypeQ conv[16];
    vec_cast<DTypeQ, DTypeKV>::template cast<16>(conv, (DTypeKV*)&packed);
    dst[get_permuted_offset<SWIZZLE, BF16_COLS>(row, 2 * col)] = *(b128_t*)&conv[0];
    dst[get_permuted_offset<SWIZZLE, BF16_COLS>(row, 2 * col + 1)] = *(b128_t*)&conv[8];
  }
}

template <typename KTraits, bool REPACK_BF16 = false>
__device__ __forceinline__ void compute_qk(
    smem_t<KTraits::SWIZZLE_MODE_Q>* q_smem, uint32_t* q_smem_offset_r,
    smem_t<KTraits::SWIZZLE_MODE_KV>* k_smem, uint32_t* k_smem_offset_r, uint8_t* k_sf_smem,
    uint32_t lane_idx, typename KTraits::DTypeQKAccum (*s_frag)[KTraits::NUM_MMA_KV][8]) {
  constexpr uint32_t UPCAST_STRIDE_Q = KTraits::UPCAST_STRIDE_Q;
  constexpr uint32_t UPCAST_STRIDE_K = KTraits::UPCAST_STRIDE_K;
  // When reading from the BF16 repack buffer, K is laid out as native 16-bit, so
  // use the 16-bit b128 stride / element size instead of the FP8 packed ones.
  constexpr uint32_t KV_STRIDE = REPACK_BF16 ? KTraits::REPACK_STRIDE_QK : UPCAST_STRIDE_K;
  constexpr uint32_t KV_ESIZE =
      REPACK_BF16 ? sizeof(typename KTraits::DTypeQ) : sizeof(typename KTraits::DTypeKV);
  uint32_t a_frag[KTraits::NUM_MMA_Q][4], b_frag[4];
  // compute q*k^T
#pragma unroll
  for (uint32_t mma_d = 0; mma_d < KTraits::NUM_MMA_D_QK; ++mma_d) {
#pragma unroll
    for (uint32_t mma_q = 0; mma_q < KTraits::NUM_MMA_Q; ++mma_q) {
      q_smem->ldmatrix_m8n8x4(*q_smem_offset_r, a_frag[mma_q]);
      *q_smem_offset_r =
          q_smem->template advance_offset_by_row<16, UPCAST_STRIDE_Q>(*q_smem_offset_r);
    }

    *q_smem_offset_r = q_smem->template advance_offset_by_column<2>(*q_smem_offset_r, mma_d) -
                       KTraits::NUM_MMA_Q * 16 * UPCAST_STRIDE_Q;

#pragma unroll
    for (uint32_t mma_kv = 0; mma_kv < KTraits::NUM_MMA_KV; ++mma_kv) {
      if constexpr (sizeof(typename KTraits::DTypeKV) == 1 && !REPACK_BF16) {
        uint32_t b_frag_quant[2];
        if (mma_d % 2 == 0) {
          k_smem->ldmatrix_m8n8x4_left_half(*k_smem_offset_r, b_frag_quant);
        } else {
          k_smem->ldmatrix_m8n8x4_right_half(*k_smem_offset_r, b_frag_quant);
        }
        if constexpr (is_fp4_type_v<typename KTraits::DTypeKV>) {
          b_frag_quant[0] = frag_layout_swizzle_16b_to_4b(b_frag_quant[0]);
          b_frag_quant[1] = frag_layout_swizzle_16b_to_4b(b_frag_quant[1]);
        } else {
          b_frag_quant[0] = frag_layout_swizzle_16b_to_8b(b_frag_quant[0]);
          b_frag_quant[1] = frag_layout_swizzle_16b_to_8b(b_frag_quant[1]);
        }
        vec_cast<typename KTraits::DTypeQ, typename KTraits::DTypeKV>::cast<8>(
            (typename KTraits::DTypeQ*)b_frag, (typename KTraits::DTypeKV*)b_frag_quant);
        if constexpr (is_fp4_type_v<typename KTraits::DTypeKV>) {
          // Apply scaling factors for K.
          // SF smem is linear: sf[kv_row * SF_COLS + hd_group], SF_COLS = HEAD_DIM_QK/16.
          // For m16n8k16 B layout, thread t's KV rows are t/4 and t/4+8 in the mma_kv tile.
          // b_frag[0,1] share KV row (t/4), b_frag[2,3] share KV row (t/4+8).
          using DTypeQ_ = typename KTraits::DTypeQ;
          using packed2_ = std::conditional_t<std::is_same_v<DTypeQ_, half>, half2, __nv_bfloat162>;
          constexpr uint32_t SF_COLS_K = KTraits::NUM_MMA_D_QK;  // HEAD_DIM_QK / 16
          uint32_t sf_base = (mma_kv * 16 + lane_idx / 4) * SF_COLS_K + mma_d;
          __nv_fp8_e4m3 sf_a_fp8, sf_b_fp8;
          sf_a_fp8.__x = k_sf_smem[sf_base];
          sf_b_fp8.__x = k_sf_smem[sf_base + 8 * SF_COLS_K];
          packed2_ scale_a{static_cast<DTypeQ_>(sf_a_fp8), static_cast<DTypeQ_>(sf_a_fp8)};
          packed2_ scale_b{static_cast<DTypeQ_>(sf_b_fp8), static_cast<DTypeQ_>(sf_b_fp8)};
          *(packed2_*)&b_frag[0] = __hmul2(*(packed2_*)&b_frag[0], scale_a);
          *(packed2_*)&b_frag[1] = __hmul2(*(packed2_*)&b_frag[1], scale_a);
          *(packed2_*)&b_frag[2] = __hmul2(*(packed2_*)&b_frag[2], scale_b);
          *(packed2_*)&b_frag[3] = __hmul2(*(packed2_*)&b_frag[3], scale_b);
        }
      } else {
        k_smem->ldmatrix_m8n8x4(*k_smem_offset_r, b_frag);
      }
      *k_smem_offset_r = k_smem->template advance_offset_by_row<16, KV_STRIDE>(*k_smem_offset_r);

#pragma unroll
      for (uint32_t mma_q = 0; mma_q < KTraits::NUM_MMA_Q; ++mma_q) {
        if constexpr (std::is_same_v<typename KTraits::DTypeQKAccum, float>) {
          if (mma_d == 0) {
            mma::mma_sync_m16n16k16_row_col_f16f16f32<typename KTraits::DTypeQ, MMAMode::kInit>(
                s_frag[mma_q][mma_kv], a_frag[mma_q], b_frag);
          } else {
            mma::mma_sync_m16n16k16_row_col_f16f16f32<typename KTraits::DTypeQ>(
                s_frag[mma_q][mma_kv], a_frag[mma_q], b_frag);
          }
        } else if (std::is_same_v<typename KTraits::DTypeQKAccum, half>) {
          if (mma_d == 0) {
            mma::mma_sync_m16n16k16_row_col_f16f16f16<MMAMode::kInit>(
                (uint32_t*)s_frag[mma_q][mma_kv], a_frag[mma_q], b_frag);
          } else {
            mma::mma_sync_m16n16k16_row_col_f16f16f16((uint32_t*)s_frag[mma_q][mma_kv],
                                                      a_frag[mma_q], b_frag);
          }
        }
      }
    }
    if constexpr (sizeof(typename KTraits::DTypeKV) == 1 && !REPACK_BF16) {
      if (mma_d % 2 == 1) {
        *k_smem_offset_r =
            k_smem->template advance_offset_by_column<2>(*k_smem_offset_r, mma_d / 2);
      }
      *k_smem_offset_r -= KTraits::NUM_MMA_KV * 16 * KV_STRIDE;
    } else {
      *k_smem_offset_r = k_smem->template advance_offset_by_column<2>(*k_smem_offset_r, mma_d) -
                         KTraits::NUM_MMA_KV * 16 * KV_STRIDE;
    }
  }
  *q_smem_offset_r -= KTraits::NUM_MMA_D_QK * 2;
  *k_smem_offset_r -= KTraits::NUM_MMA_D_QK * KV_ESIZE;
}

template <typename KTraits>
__device__ __forceinline__ void load_fp4_k_frag_scaled(smem_t<KTraits::SWIZZLE_MODE_KV>* k_smem,
                                                       const uint32_t k_smem_offset_r,
                                                       uint8_t* k_sf_smem, const uint32_t lane_idx,
                                                       const uint32_t mma_kv, const uint32_t mma_d,
                                                       uint32_t* b_frag) {
  using DTypeQ = typename KTraits::DTypeQ;
  using DTypeKV = typename KTraits::DTypeKV;
  static_assert(is_fp4_type_v<DTypeKV>);

  uint32_t b_frag_quant[2];
  if (mma_d % 2 == 0) {
    k_smem->ldmatrix_m8n8x4_left_half(k_smem_offset_r, b_frag_quant);
  } else {
    k_smem->ldmatrix_m8n8x4_right_half(k_smem_offset_r, b_frag_quant);
  }
  b_frag_quant[0] = frag_layout_swizzle_16b_to_4b(b_frag_quant[0]);
  b_frag_quant[1] = frag_layout_swizzle_16b_to_4b(b_frag_quant[1]);
  vec_cast<DTypeQ, DTypeKV>::cast<8>((DTypeQ*)b_frag, (DTypeKV*)b_frag_quant);

  using packed2 = std::conditional_t<std::is_same_v<DTypeQ, half>, half2, __nv_bfloat162>;
  constexpr uint32_t SF_COLS_K = KTraits::NUM_MMA_D_QK;
  const uint32_t sf_base = (mma_kv * 16 + lane_idx / 4) * SF_COLS_K + mma_d;
  __nv_fp8_e4m3 sf_a_fp8, sf_b_fp8;
  sf_a_fp8.__x = k_sf_smem[sf_base];
  sf_b_fp8.__x = k_sf_smem[sf_base + 8 * SF_COLS_K];
  packed2 scale_a{static_cast<DTypeQ>(sf_a_fp8), static_cast<DTypeQ>(sf_a_fp8)};
  packed2 scale_b{static_cast<DTypeQ>(sf_b_fp8), static_cast<DTypeQ>(sf_b_fp8)};
  *(packed2*)&b_frag[0] = __hmul2(*(packed2*)&b_frag[0], scale_a);
  *(packed2*)&b_frag[1] = __hmul2(*(packed2*)&b_frag[1], scale_a);
  *(packed2*)&b_frag[2] = __hmul2(*(packed2*)&b_frag[2], scale_b);
  *(packed2*)&b_frag[3] = __hmul2(*(packed2*)&b_frag[3], scale_b);
}

template <typename KTraits>
__device__ __forceinline__ void compute_qk_fp4_rope(
    smem_t<KTraits::SWIZZLE_MODE_Q>* q_smem, uint32_t* q_smem_offset_r,
    smem_t<KTraits::SWIZZLE_MODE_KV>* k_smem, uint32_t* k_smem_offset_r, uint8_t* k_sf_smem,
    const uint32_t lane_idx, const uint32_t kv_rope_base, float (*rope_freq)[4],
    typename KTraits::DTypeQKAccum (*s_frag)[KTraits::NUM_MMA_KV][8]) {
  using DTypeQ = typename KTraits::DTypeQ;
  using DTypeKV = typename KTraits::DTypeKV;
  static_assert(is_fp4_type_v<DTypeKV>);
  static_assert(KTraits::POS_ENCODING_MODE == PosEncodingMode::kRoPELlama);
  static_assert(KTraits::NUM_MMA_D_QK % 4 == 0);

  constexpr uint32_t UPCAST_STRIDE_Q = KTraits::UPCAST_STRIDE_Q;
  constexpr uint32_t UPCAST_STRIDE_K = KTraits::UPCAST_STRIDE_K;
  constexpr uint32_t NUM_MMA_D_HALF = KTraits::NUM_MMA_D_QK / 2;

  uint32_t q_smem_offset_first = *q_smem_offset_r;
  uint32_t k_smem_offset_first = *k_smem_offset_r;
  uint32_t k_smem_offset_second = *k_smem_offset_r;

#pragma unroll
  for (uint32_t mma_d = 0; mma_d < NUM_MMA_D_HALF; ++mma_d) {
    if (mma_d % 2 == 1) {
      k_smem_offset_second =
          k_smem->template advance_offset_by_column<2>(k_smem_offset_second, mma_d / 2);
    }
  }

#pragma unroll
  for (uint32_t mma_di = 0; mma_di < NUM_MMA_D_HALF; ++mma_di) {
    uint32_t a_frag_first[KTraits::NUM_MMA_Q][4], a_frag_second[KTraits::NUM_MMA_Q][4];
    uint32_t q_smem_offset_first_mma = q_smem_offset_first;
    uint32_t q_smem_offset_second_mma =
        q_smem->template advance_offset_by_column<KTraits::NUM_MMA_D_QK>(q_smem_offset_first, 0);

#pragma unroll
    for (uint32_t mma_q = 0; mma_q < KTraits::NUM_MMA_Q; ++mma_q) {
      q_smem->ldmatrix_m8n8x4(q_smem_offset_first_mma, a_frag_first[mma_q]);
      q_smem_offset_first_mma =
          q_smem->template advance_offset_by_row<16, UPCAST_STRIDE_Q>(q_smem_offset_first_mma);
      q_smem->ldmatrix_m8n8x4(q_smem_offset_second_mma, a_frag_second[mma_q]);
      q_smem_offset_second_mma =
          q_smem->template advance_offset_by_row<16, UPCAST_STRIDE_Q>(q_smem_offset_second_mma);
    }

    uint32_t k_smem_offset_first_mma = k_smem_offset_first;
    uint32_t k_smem_offset_second_mma = k_smem_offset_second;

#pragma unroll
    for (uint32_t mma_kv = 0; mma_kv < KTraits::NUM_MMA_KV; ++mma_kv) {
      uint32_t b_frag_first[4], b_frag_second[4];
      load_fp4_k_frag_scaled<KTraits>(k_smem, k_smem_offset_first_mma, k_sf_smem, lane_idx, mma_kv,
                                      mma_di, b_frag_first);
      load_fp4_k_frag_scaled<KTraits>(k_smem, k_smem_offset_second_mma, k_sf_smem, lane_idx, mma_kv,
                                      mma_di + NUM_MMA_D_HALF, b_frag_second);
      k_frag_apply_llama_rope<DTypeQ>((DTypeQ*)b_frag_first, (DTypeQ*)b_frag_second,
                                      rope_freq[mma_di], kv_rope_base + mma_kv * 16 + lane_idx / 4);

      k_smem_offset_first_mma =
          k_smem->template advance_offset_by_row<16, UPCAST_STRIDE_K>(k_smem_offset_first_mma);
      k_smem_offset_second_mma =
          k_smem->template advance_offset_by_row<16, UPCAST_STRIDE_K>(k_smem_offset_second_mma);

#pragma unroll
      for (uint32_t mma_q = 0; mma_q < KTraits::NUM_MMA_Q; ++mma_q) {
        if constexpr (std::is_same_v<typename KTraits::DTypeQKAccum, float>) {
          if (mma_di == 0) {
            mma::mma_sync_m16n16k16_row_col_f16f16f32<DTypeQ, MMAMode::kInit>(
                s_frag[mma_q][mma_kv], a_frag_first[mma_q], b_frag_first);
          } else {
            mma::mma_sync_m16n16k16_row_col_f16f16f32<DTypeQ>(s_frag[mma_q][mma_kv],
                                                              a_frag_first[mma_q], b_frag_first);
          }
          mma::mma_sync_m16n16k16_row_col_f16f16f32<DTypeQ>(s_frag[mma_q][mma_kv],
                                                            a_frag_second[mma_q], b_frag_second);
        } else if (std::is_same_v<typename KTraits::DTypeQKAccum, half>) {
          if (mma_di == 0) {
            mma::mma_sync_m16n16k16_row_col_f16f16f16<MMAMode::kInit>(
                (uint32_t*)s_frag[mma_q][mma_kv], a_frag_first[mma_q], b_frag_first);
          } else {
            mma::mma_sync_m16n16k16_row_col_f16f16f16((uint32_t*)s_frag[mma_q][mma_kv],
                                                      a_frag_first[mma_q], b_frag_first);
          }
          mma::mma_sync_m16n16k16_row_col_f16f16f16((uint32_t*)s_frag[mma_q][mma_kv],
                                                    a_frag_second[mma_q], b_frag_second);
        }
      }
    }

    q_smem_offset_first = q_smem->template advance_offset_by_column<2>(q_smem_offset_first, mma_di);
    if (mma_di % 2 == 1) {
      k_smem_offset_first =
          k_smem->template advance_offset_by_column<2>(k_smem_offset_first, mma_di / 2);
      k_smem_offset_second = k_smem->template advance_offset_by_column<2>(
          k_smem_offset_second, (mma_di + NUM_MMA_D_HALF) / 2);
    }
  }
}

template <typename KTraits, typename Params, typename DTypeQKAccum>
__device__ __forceinline__ void logits_transform(
    const Params& params, typename KTraits::AttentionVariant variant, const uint32_t batch_idx,
    const uint32_t qo_packed_idx_base, const uint32_t kv_idx_base, const uint32_t qo_len,
    const uint32_t kv_len, const uint32_t chunk_end, const uint_fastdiv group_size,
    DTypeQKAccum (*s_frag)[KTraits::NUM_MMA_KV][8], const dim3 tid = threadIdx,
    const uint32_t kv_head_idx = blockIdx.z) {
  const uint32_t lane_idx = tid.x;
  uint32_t q[KTraits::NUM_MMA_Q][2], r[KTraits::NUM_MMA_Q][2];
  float logits = 0., logitsTransformed = 0.;

#pragma unroll
  for (uint32_t mma_q = 0; mma_q < KTraits::NUM_MMA_Q; ++mma_q) {
#pragma unroll
    for (uint32_t j = 0; j < 2; ++j) {
      group_size.divmod(qo_packed_idx_base + mma_q * 16 + lane_idx / 4 + 8 * j, q[mma_q][j],
                        r[mma_q][j]);
    }
  }

#pragma unroll
  for (uint32_t mma_q = 0; mma_q < KTraits::NUM_MMA_Q; ++mma_q) {
#pragma unroll
    for (uint32_t mma_kv = 0; mma_kv < KTraits::NUM_MMA_KV; ++mma_kv) {
#pragma unroll
      for (uint32_t reg_id = 0; reg_id < 8; ++reg_id) {
        const uint32_t q_idx = q[mma_q][(reg_id % 4) / 2], kv_idx = kv_idx_base + mma_kv * 16 +
                                                                    2 * (lane_idx % 4) +
                                                                    8 * (reg_id / 4) + reg_id % 2;
        const uint32_t qo_head_idx = kv_head_idx * group_size + r[mma_q][(reg_id % 4) / 2];

        // Skip lanes beyond this CTA's kv chunk: their s_frag values are stale/garbage
        // (masked to MaskFillValue right after by logits_mask), and with a non-tile-aligned
        // chunk_end the variant hook would otherwise observe them with in-range kv_idx and
        // could leak them through side effects (e.g. dumping logits to global memory).
        if (kv_idx >= chunk_end) {
          continue;
        }

#ifdef FP16_QK_REDUCTION_SUPPORTED
        if constexpr (std::is_same<DTypeQKAccum, __half>::value) {
          logits = std::bit_cast<float>(fp16_ieee_to_fp32_value(s_frag[mma_q][mma_kv][reg_id]));
        } else if constexpr (!std::is_same<DTypeQKAccum, __half>::value) {
          logits = s_frag[mma_q][mma_kv][reg_id];
        }
#else
        static_assert(!std::is_same<DTypeQKAccum, __half>::value,
                      "Set -DFP16_QK_REDUCTION_SUPPORTED and install boost_math "
                      "then recompile to support fp16 reduction");
        logits = s_frag[mma_q][mma_kv][reg_id];
#endif
        logitsTransformed = variant.LogitsTransform(params, logits, batch_idx, q_idx, kv_idx,
                                                    qo_head_idx, kv_head_idx);
#ifdef FP16_QK_REDUCTION_SUPPORTED
        if constexpr (std::is_same<DTypeQKAccum, __half>::value) {
          s_frag[mma_q][mma_kv][reg_id] =
              std::bit_cast<half>(fp16_ieee_from_fp32_value(logitsTransformed));
        } else if constexpr (!std::is_same<DTypeQKAccum, __half>::value) {
          s_frag[mma_q][mma_kv][reg_id] = logitsTransformed;
        }
#else
        s_frag[mma_q][mma_kv][reg_id] = logitsTransformed;
#endif
      }
    }
  }
}

template <typename KTraits, typename Params>
__device__ __forceinline__ void logits_mask(
    const Params& params, typename KTraits::AttentionVariant variant, const uint32_t batch_idx,
    const uint32_t qo_packed_idx_base, const uint32_t kv_idx_base, const uint32_t qo_len,
    const uint32_t kv_len, const uint32_t chunk_end, const uint_fastdiv group_size,
    typename KTraits::DTypeQKAccum (*s_frag)[KTraits::NUM_MMA_KV][8], const dim3 tid = threadIdx,
    const uint32_t kv_head_idx = blockIdx.z) {
  const uint32_t lane_idx = tid.x;
  constexpr uint32_t NUM_MMA_Q = KTraits::NUM_MMA_Q;
  constexpr uint32_t NUM_MMA_KV = KTraits::NUM_MMA_KV;
  using DTypeQKAccum = typename KTraits::DTypeQKAccum;
  constexpr MaskMode MASK_MODE = KTraits::MASK_MODE;
  uint32_t q[NUM_MMA_Q][2], r[NUM_MMA_Q][2];
#pragma unroll
  for (uint32_t mma_q = 0; mma_q < NUM_MMA_Q; ++mma_q) {
#pragma unroll
    for (uint32_t j = 0; j < 2; ++j) {
      group_size.divmod(qo_packed_idx_base + mma_q * 16 + lane_idx / 4 + 8 * j, q[mma_q][j],
                        r[mma_q][j]);
    }
  }

#pragma unroll
  for (uint32_t mma_q = 0; mma_q < NUM_MMA_Q; ++mma_q) {
#pragma unroll
    for (uint32_t mma_kv = 0; mma_kv < NUM_MMA_KV; ++mma_kv) {
#pragma unroll
      for (uint32_t reg_id = 0; reg_id < 8; ++reg_id) {
        const uint32_t q_idx = q[mma_q][(reg_id % 4) / 2], kv_idx = kv_idx_base + mma_kv * 16 +
                                                                    2 * (lane_idx % 4) +
                                                                    8 * (reg_id / 4) + reg_id % 2;
        const uint32_t qo_head_idx = kv_head_idx * group_size + r[mma_q][(reg_id % 4) / 2];
        const bool mask =
            (!(MASK_MODE == MaskMode::kCausal || MASK_MODE == MaskMode::kMultiItemScoring
                   ? (kv_idx + qo_len > kv_len + q_idx || (kv_idx >= chunk_end))
                   : kv_idx >= chunk_end)) &&
            variant.LogitsMask(params, batch_idx, q_idx, kv_idx, qo_head_idx, kv_head_idx);
        s_frag[mma_q][mma_kv][reg_id] =
            (mask) ? s_frag[mma_q][mma_kv][reg_id] : (KTraits::MaskFillValue);
      }
    }
  }
}

template <typename KTraits, typename Params>
__device__ __forceinline__ void logits_mask_multi_item_scoring(
    const Params& params, typename KTraits::AttentionVariant variant, const uint32_t batch_idx,
    const uint32_t qo_packed_idx_base, const uint32_t kv_idx_base, const uint32_t qo_len,
    const uint32_t kv_len, const uint32_t window_left, const uint32_t chunk_end,
    const uint_fastdiv group_size, typename KTraits::DTypeQKAccum (*s_frag)[KTraits::NUM_MMA_KV][8],
    // new arguments for compact description of mask
    const uint32_t prefix_len, uint16_t* token_pos_in_items, const uint32_t lane_idx = threadIdx.x,
    const uint32_t kv_head_idx = blockIdx.z) {
  constexpr uint32_t NUM_MMA_Q = KTraits::NUM_MMA_Q;
  constexpr uint32_t NUM_MMA_KV = KTraits::NUM_MMA_KV;
  using DTypeQKAccum = typename KTraits::DTypeQKAccum;
  uint32_t q[NUM_MMA_Q][2], r[NUM_MMA_Q][2];

#pragma unroll
  for (uint32_t mma_q = 0; mma_q < NUM_MMA_Q; ++mma_q) {
#pragma unroll
    for (uint32_t j = 0; j < 2; ++j) {
      group_size.divmod(qo_packed_idx_base + mma_q * 16 + lane_idx / 4 + 8 * j, q[mma_q][j],
                        r[mma_q][j]);
    }
  }
  // prefetching global memory to registers
  uint16_t token_pos_in_items_regs[NUM_MMA_Q][(4 / 2)];
#pragma unroll
  for (uint32_t mma_q = 0; mma_q < NUM_MMA_Q; ++mma_q) {
#pragma unroll
    for (uint32_t eff_reg_id = 0; eff_reg_id < (4 / 2); ++eff_reg_id) {
      const uint32_t q_idx = q[mma_q][eff_reg_id];
      // use __ldca to hint compiler to cache in L1 for further reuse by other tiles
      const int idx_in_original_seq = q_idx + kv_len - qo_len;
      if (idx_in_original_seq >= prefix_len & idx_in_original_seq < kv_len) {
        token_pos_in_items_regs[mma_q][eff_reg_id] =
            __ldca(token_pos_in_items + idx_in_original_seq - prefix_len);
      }
    }
  }

#pragma unroll
  for (uint32_t mma_q = 0; mma_q < NUM_MMA_Q; ++mma_q) {
#pragma unroll
    for (uint32_t mma_kv = 0; mma_kv < NUM_MMA_KV; ++mma_kv) {
#pragma unroll
      for (uint32_t reg_id = 0; reg_id < 8; ++reg_id) {
        const uint32_t q_idx = q[mma_q][(reg_id % 4) / 2], kv_idx = kv_idx_base + mma_kv * 16 +
                                                                    2 * (lane_idx % 4) +
                                                                    8 * (reg_id / 4) + reg_id % 2;
        const uint32_t qo_head_idx = kv_head_idx * group_size + r[mma_q][(reg_id % 4) / 2];
        const uint32_t idx_in_original_seq = q_idx + kv_len - qo_len;
        const bool out_of_boundary = kv_idx > idx_in_original_seq || (kv_idx >= chunk_end) ||
                                     kv_idx + window_left < idx_in_original_seq;
        const bool is_prefix = idx_in_original_seq < prefix_len;
        if (out_of_boundary || is_prefix) {
          s_frag[mma_q][mma_kv][reg_id] =
              out_of_boundary ? (KTraits::MaskFillValue) : s_frag[mma_q][mma_kv][reg_id];
        } else {
          s_frag[mma_q][mma_kv][reg_id] =
              (kv_idx < prefix_len |
               (idx_in_original_seq < kv_idx + token_pos_in_items_regs[mma_q][((reg_id % 4) / 2)]))
                  ? s_frag[mma_q][mma_kv][reg_id]
                  : (KTraits::MaskFillValue);
        }
      }
    }
  }
}

template <typename KTraits>
__device__ __forceinline__ void update_mdo_states(
    typename KTraits::AttentionVariant variant,
    typename KTraits::DTypeQKAccum (*s_frag)[KTraits::NUM_MMA_KV][8],
    float (*o_frag)[KTraits::NUM_MMA_D_VO_TILE][8], typename KTraits::DTypeQKAccum (*m)[2],
    float (*d)[2]) {
  using DTypeQKAccum = typename KTraits::DTypeQKAccum;
  using AttentionVariant = typename KTraits::AttentionVariant;
  constexpr bool use_softmax = AttentionVariant::use_softmax;

  if constexpr (use_softmax) {
    const float sm_scale = variant.sm_scale_log2;
    if constexpr (std::is_same_v<DTypeQKAccum, float>) {
#pragma unroll
      for (uint32_t mma_q = 0; mma_q < KTraits::NUM_MMA_Q; ++mma_q) {
#pragma unroll
        for (uint32_t j = 0; j < 2; ++j) {
          float m_prev = m[mma_q][j];
#pragma unroll
          for (uint32_t mma_kv = 0; mma_kv < KTraits::NUM_MMA_KV; ++mma_kv) {
            float m_local =
                max(max(s_frag[mma_q][mma_kv][j * 2 + 0], s_frag[mma_q][mma_kv][j * 2 + 1]),
                    max(s_frag[mma_q][mma_kv][j * 2 + 4], s_frag[mma_q][mma_kv][j * 2 + 5]));
            m[mma_q][j] = max(m[mma_q][j], m_local);
          }
          m[mma_q][j] = max(m[mma_q][j], math::shfl_xor_sync(m[mma_q][j], 0x2));
          m[mma_q][j] = max(m[mma_q][j], math::shfl_xor_sync(m[mma_q][j], 0x1));

          // Per-row clamp: fully masked rows (m == -inf) yield exp2(-inf) = 0, not NaN.
          const float m_scaled =
              max(m[mma_q][j] * sm_scale, -cuda::std::numeric_limits<float>::max());
          float o_scale = math::ptx_exp2(m_prev * sm_scale - m_scaled);
          d[mma_q][j] *= o_scale;
#pragma unroll
          for (uint32_t mma_d = 0; mma_d < KTraits::NUM_MMA_D_VO_TILE; ++mma_d) {
            o_frag[mma_q][mma_d][j * 2 + 0] *= o_scale;
            o_frag[mma_q][mma_d][j * 2 + 1] *= o_scale;
            o_frag[mma_q][mma_d][j * 2 + 4] *= o_scale;
            o_frag[mma_q][mma_d][j * 2 + 5] *= o_scale;
          }
#pragma unroll
          for (uint32_t mma_kv = 0; mma_kv < KTraits::NUM_MMA_KV; ++mma_kv) {
            s_frag[mma_q][mma_kv][j * 2 + 0] =
                math::ptx_exp2(s_frag[mma_q][mma_kv][j * 2 + 0] * sm_scale - m_scaled);
            s_frag[mma_q][mma_kv][j * 2 + 1] =
                math::ptx_exp2(s_frag[mma_q][mma_kv][j * 2 + 1] * sm_scale - m_scaled);
            s_frag[mma_q][mma_kv][j * 2 + 4] =
                math::ptx_exp2(s_frag[mma_q][mma_kv][j * 2 + 4] * sm_scale - m_scaled);
            s_frag[mma_q][mma_kv][j * 2 + 5] =
                math::ptx_exp2(s_frag[mma_q][mma_kv][j * 2 + 5] * sm_scale - m_scaled);
          }
        }
      }
    } else if constexpr (std::is_same_v<DTypeQKAccum, half>) {
      const half2 sm_scale = __float2half2_rn(variant.sm_scale_log2);
#pragma unroll
      for (uint32_t mma_q = 0; mma_q < KTraits::NUM_MMA_Q; ++mma_q) {
        half m_prev[2];
#pragma unroll
        for (uint32_t j = 0; j < 2; ++j) {
          m_prev[j] = m[mma_q][j];
#pragma unroll
          for (uint32_t mma_kv = 0; mma_kv < KTraits::NUM_MMA_KV; ++mma_kv) {
            half2 m_local = __hmax2(*(half2*)&s_frag[mma_q][mma_kv][j * 2],
                                    *(half2*)&s_frag[mma_q][mma_kv][j * 2 + 4]);
            m[mma_q][j] = __hmax(m[mma_q][j], __hmax(m_local.x, m_local.y));
          }
        }
        *(half2*)&m[mma_q] =
            __hmax2(*(half2*)&m[mma_q], math::shfl_xor_sync(*(half2*)&m[mma_q], 0x2));
        *(half2*)&m[mma_q] =
            __hmax2(*(half2*)&m[mma_q], math::shfl_xor_sync(*(half2*)&m[mma_q], 0x1));
#pragma unroll
        for (uint32_t j = 0; j < 2; ++j) {
          // Per-row clamp to the lowest finite half (see the float path above).
          const half m_scaled_h = __hmax(__hmul(m[mma_q][j], sm_scale.x), __float2half(-65504.f));
          float o_scale = math::ptx_exp2(float(m_prev[j] * sm_scale.x) - float(m_scaled_h));
          d[mma_q][j] *= o_scale;
#pragma unroll
          for (uint32_t mma_d = 0; mma_d < KTraits::NUM_MMA_D_VO_TILE; ++mma_d) {
            o_frag[mma_q][mma_d][j * 2 + 0] *= o_scale;
            o_frag[mma_q][mma_d][j * 2 + 1] *= o_scale;
            o_frag[mma_q][mma_d][j * 2 + 4] *= o_scale;
            o_frag[mma_q][mma_d][j * 2 + 5] *= o_scale;
          }
          half2 m2s = make_half2(m_scaled_h, m_scaled_h);
#pragma unroll
          for (uint32_t mma_kv = 0; mma_kv < KTraits::NUM_MMA_KV; ++mma_kv) {
            *(half2*)&s_frag[mma_q][mma_kv][j * 2] =
                math::ptx_exp2(*(half2*)&s_frag[mma_q][mma_kv][j * 2] * sm_scale - m2s);
            *(half2*)&s_frag[mma_q][mma_kv][j * 2 + 4] =
                math::ptx_exp2(*(half2*)&s_frag[mma_q][mma_kv][j * 2 + 4] * sm_scale - m2s);
          }
        }
      }
    }
  }
}

template <typename KTraits, bool REPACK_BF16 = false>
__device__ __forceinline__ void compute_sfm_v(
    smem_t<KTraits::SWIZZLE_MODE_KV>* v_smem, uint32_t* v_smem_offset_r, uint8_t* v_sf_smem,
    uint32_t lane_idx, typename KTraits::DTypeQKAccum (*s_frag)[KTraits::NUM_MMA_KV][8],
    float (*o_frag)[KTraits::NUM_MMA_D_VO_TILE][8], float (*d)[2], const uint32_t d_base = 0) {
  constexpr uint32_t UPCAST_STRIDE_V = KTraits::UPCAST_STRIDE_V;
  // When reading from the BF16 repack buffer, V is native 16-bit.
  constexpr uint32_t VV_STRIDE = REPACK_BF16 ? KTraits::REPACK_STRIDE_VO : UPCAST_STRIDE_V;
  constexpr uint32_t VV_ESIZE =
      REPACK_BF16 ? sizeof(typename KTraits::DTypeQ) : sizeof(typename KTraits::DTypeKV);

  typename KTraits::DTypeQ s_frag_f16[KTraits::NUM_MMA_Q][KTraits::NUM_MMA_KV][8];
  if constexpr (std::is_same_v<typename KTraits::DTypeQKAccum, float>) {
#pragma unroll
    for (uint32_t mma_q = 0; mma_q < KTraits::NUM_MMA_Q; ++mma_q) {
#pragma unroll
      for (uint32_t mma_kv = 0; mma_kv < KTraits::NUM_MMA_KV; ++mma_kv) {
        vec_cast<typename KTraits::DTypeQ, float>::cast<8>(s_frag_f16[mma_q][mma_kv],
                                                           s_frag[mma_q][mma_kv]);
      }
    }
  }

  if constexpr (KTraits::AttentionVariant::use_softmax) {
#pragma unroll
    for (uint32_t mma_q = 0; mma_q < KTraits::NUM_MMA_Q; ++mma_q) {
#pragma unroll
      for (uint32_t mma_kv = 0; mma_kv < KTraits::NUM_MMA_KV; ++mma_kv) {
        if constexpr (std::is_same_v<typename KTraits::DTypeQKAccum, float>) {
          mma::m16k16_rowsum_f16f16f32(d[mma_q], s_frag_f16[mma_q][mma_kv]);
        } else {
          mma::m16k16_rowsum_f16f16f32(d[mma_q], s_frag[mma_q][mma_kv]);
        }
      }
    }
  }

#pragma unroll
  for (uint32_t mma_kv = 0; mma_kv < KTraits::NUM_MMA_KV; ++mma_kv) {
#pragma unroll
    for (uint32_t mma_d = 0; mma_d < KTraits::NUM_MMA_D_VO; ++mma_d) {
      if (mma_d >= d_base && mma_d < d_base + KTraits::NUM_MMA_D_VO_TILE) {
        uint32_t b_frag[4];
        if constexpr (sizeof(typename KTraits::DTypeKV) == 1 && !REPACK_BF16) {
          uint32_t b_frag_quant[2];
          if (mma_d % 2 == 0) {
            v_smem->ldmatrix_m8n8x4_trans_left_half(*v_smem_offset_r, b_frag_quant);
          } else {
            v_smem->ldmatrix_m8n8x4_trans_right_half(*v_smem_offset_r, b_frag_quant);
          }

          if constexpr (is_fp4_type_v<typename KTraits::DTypeKV>) {
            b_frag_quant[0] = frag_layout_swizzle_16b_to_4b_trans(b_frag_quant[0]);
            b_frag_quant[1] = frag_layout_swizzle_16b_to_4b_trans(b_frag_quant[1]);
          } else {
            b_frag_quant[0] = frag_layout_swizzle_16b_to_8b_trans(b_frag_quant[0]);
            b_frag_quant[1] = frag_layout_swizzle_16b_to_8b_trans(b_frag_quant[1]);
          }
          vec_cast<typename KTraits::DTypeQ, typename KTraits::DTypeKV>::cast<8>(
              (typename KTraits::DTypeQ*)b_frag, (typename KTraits::DTypeKV*)b_frag_quant);
          swap(b_frag[1], b_frag[2]);
          if constexpr (is_fp4_type_v<typename KTraits::DTypeKV>) {
            // Apply scaling factors for V.
            // SF smem is linear: sf[kv_row * SF_COLS + hd_group], SF_COLS = HEAD_DIM_VO/16.
            // For transposed B (V), thread t's KV rows are 2*(t%4)+{0,1} and 2*(t%4)+{8,9}
            // in the mma_kv tile. After swap, b_frag[0,2] cover rows {r0, r0+1} and
            // b_frag[1,3] cover rows {r0+8, r0+9}. Each half2 needs two distinct SFs.
            using DTypeQ_ = typename KTraits::DTypeQ;
            using packed2_ =
                std::conditional_t<std::is_same_v<DTypeQ_, half>, half2, __nv_bfloat162>;
            constexpr uint32_t SF_COLS_V = KTraits::NUM_MMA_D_VO;  // HEAD_DIM_VO / 16
            uint32_t sf_base = (mma_kv * 16 + 2 * (lane_idx % 4)) * SF_COLS_V + mma_d;
            __nv_fp8_e4m3 sf0_fp8, sf1_fp8, sf2_fp8, sf3_fp8;
            sf0_fp8.__x = v_sf_smem[sf_base];
            sf1_fp8.__x = v_sf_smem[sf_base + SF_COLS_V];
            sf2_fp8.__x = v_sf_smem[sf_base + 8 * SF_COLS_V];
            sf3_fp8.__x = v_sf_smem[sf_base + 9 * SF_COLS_V];
            packed2_ scale_lo{static_cast<DTypeQ_>(sf0_fp8), static_cast<DTypeQ_>(sf1_fp8)};
            packed2_ scale_hi{static_cast<DTypeQ_>(sf2_fp8), static_cast<DTypeQ_>(sf3_fp8)};
            *(packed2_*)&b_frag[0] = __hmul2(*(packed2_*)&b_frag[0], scale_lo);
            *(packed2_*)&b_frag[1] = __hmul2(*(packed2_*)&b_frag[1], scale_hi);
            *(packed2_*)&b_frag[2] = __hmul2(*(packed2_*)&b_frag[2], scale_lo);
            *(packed2_*)&b_frag[3] = __hmul2(*(packed2_*)&b_frag[3], scale_hi);
          }
        } else {
          v_smem->ldmatrix_m8n8x4_trans(*v_smem_offset_r, b_frag);
        }
        const uint32_t mma_d_local = mma_d - d_base;
#pragma unroll
        for (uint32_t mma_q = 0; mma_q < KTraits::NUM_MMA_Q; ++mma_q) {
          if constexpr (std::is_same_v<typename KTraits::DTypeQKAccum, float>) {
            mma::mma_sync_m16n16k16_row_col_f16f16f32<typename KTraits::DTypeQ>(
                o_frag[mma_q][mma_d_local], (uint32_t*)s_frag_f16[mma_q][mma_kv], b_frag);
          } else {
            mma::mma_sync_m16n16k16_row_col_f16f16f32<typename KTraits::DTypeQ>(
                o_frag[mma_q][mma_d_local], (uint32_t*)s_frag[mma_q][mma_kv], b_frag);
          }
        }
      }
      if constexpr (sizeof(typename KTraits::DTypeKV) == 1 && !REPACK_BF16) {
        if (mma_d % 2 == 1) {
          *v_smem_offset_r =
              v_smem->template advance_offset_by_column<2>(*v_smem_offset_r, mma_d / 2);
        }
      } else {
        *v_smem_offset_r = v_smem->template advance_offset_by_column<2>(*v_smem_offset_r, mma_d);
      }
    }
    *v_smem_offset_r = v_smem->template advance_offset_by_row<16, VV_STRIDE>(*v_smem_offset_r) -
                       VV_ESIZE * KTraits::NUM_MMA_D_VO;
  }
  *v_smem_offset_r -= 16 * KTraits::NUM_MMA_KV * VV_STRIDE;
}

template <typename KTraits>
__device__ __forceinline__ void finalize_m(typename KTraits::AttentionVariant variant,
                                           typename KTraits::DTypeQKAccum (*m)[2]) {
  if constexpr (variant.use_softmax) {
#pragma unroll
    for (uint32_t mma_q = 0; mma_q < KTraits::NUM_MMA_Q; ++mma_q) {
#pragma unroll
      for (uint32_t j = 0; j < 2; ++j) {
        if (m[mma_q][j] != typename KTraits::DTypeQKAccum(-math::inf)) {
          m[mma_q][j] *= variant.sm_scale_log2;
        }
      }
    }
  }
}

template <typename KTraits, typename Params>
__device__ __forceinline__ void transform_output(
    const Params& params, typename KTraits::AttentionVariant variant,
    float (*o_frag)[KTraits::NUM_MMA_D_VO_TILE][8], typename KTraits::DTypeQKAccum (*m)[2],
    float (*d)[2], const uint32_t batch_idx, const uint32_t kv_tile_idx,
    const uint32_t qo_packed_idx_base, const uint32_t warp_idx, const uint32_t lane_idx,
    uint32_t kv_head_idx, const uint_fastdiv group_size) {
  uint32_t q[KTraits::NUM_MMA_Q][2], r[KTraits::NUM_MMA_Q][2];
  float scale[KTraits::NUM_MMA_Q][2];
#pragma unroll
  for (uint32_t mma_q = 0; mma_q < KTraits::NUM_MMA_Q; ++mma_q) {
#pragma unroll
    for (uint32_t j = 0; j < 2; ++j) {
      group_size.divmod(qo_packed_idx_base + mma_q * 16 + lane_idx / 4 + 8 * j, q[mma_q][j],
                        r[mma_q][j]);
      uint32_t qo_head_idx = kv_head_idx * group_size + r[mma_q][j];
      // Update the m and d when attention sinks are used.
      variant.update_m_d(params, kv_tile_idx, qo_head_idx, m[mma_q][j], d[mma_q][j],
                         scale[mma_q][j]);
    }
  }

#pragma unroll
  for (uint32_t mma_q = 0; mma_q < KTraits::NUM_MMA_Q; ++mma_q) {
#pragma unroll
    for (uint32_t mma_d = 0; mma_d < KTraits::NUM_MMA_D_VO_TILE; ++mma_d) {
#pragma unroll
      for (uint32_t reg_id = 0; reg_id < 8; ++reg_id) {
        const uint32_t qo_idx = q[mma_q][(reg_id % 4) / 2];
        const uint32_t qo_head_idx = kv_head_idx * group_size + r[mma_q][(reg_id % 4) / 2];
        o_frag[mma_q][mma_d][reg_id] = variant.OutputTransform(
            params, o_frag[mma_q][mma_d][reg_id], batch_idx, qo_idx, qo_head_idx,
            m[mma_q][(reg_id % 4) / 2], d[mma_q][(reg_id % 4) / 2], scale[mma_q][(reg_id % 4) / 2]);
      }
    }
  }
}

/*!
 * \brief Synchronize the states of the MDO kernel across the threadblock along threadIdx.z.
 */
template <typename KTraits, typename SmemStorage>
__device__ __forceinline__ void threadblock_sync_mdo_states(
    float (*o_frag)[KTraits::NUM_MMA_D_VO_TILE][8], SmemStorage* smem_storage,
    typename KTraits::DTypeQKAccum (*m)[2], float (*d)[2], const uint32_t warp_idx,
    const uint32_t lane_idx, const dim3 tid = threadIdx) {
  // only necessary when blockDim.z > 1
  if constexpr (KTraits::NUM_WARPS_KV > 1) {
    float* smem_o = smem_storage->cta_sync_o_smem;
    float2* smem_md = smem_storage->cta_sync_md_smem;
    // o: [num_warps, NUM_MMA_Q, NUM_MMA_D_VO_TILE, WARP_SIZE(32), 8]
    // md: [num_warps, NUM_MMA_Q, 16, 2 (m/d)]
    // cta_sync_o_smem is sized for a single VO tile and reused across the
    // NUM_D_VO_TILES passes, so it is indexed with NUM_MMA_D_VO_TILE here.
#pragma unroll
    for (uint32_t mma_q = 0; mma_q < KTraits::NUM_MMA_Q; ++mma_q) {
#pragma unroll
      for (uint32_t mma_d = 0; mma_d < KTraits::NUM_MMA_D_VO_TILE; ++mma_d) {
        vec_t<float, 8>::memcpy(
            smem_o +
                (((warp_idx * KTraits::NUM_MMA_Q + mma_q) * KTraits::NUM_MMA_D_VO_TILE + mma_d) *
                     WARP_SIZE +
                 lane_idx) *
                    8,
            o_frag[mma_q][mma_d]);
      }
    }

    if constexpr (KTraits::AttentionVariant::use_softmax) {
#pragma unroll
      for (uint32_t mma_q = 0; mma_q < KTraits::NUM_MMA_Q; ++mma_q) {
#pragma unroll
        for (uint32_t j = 0; j < 2; ++j) {
          smem_md[((warp_idx * KTraits::NUM_MMA_Q + mma_q) * 2 + j) * 8 + lane_idx / 4] =
              make_float2(float(m[mma_q][j]), d[mma_q][j]);
        }
      }

      // synchronize m,d first
      __syncthreads();
#pragma unroll
      for (uint32_t mma_q = 0; mma_q < KTraits::NUM_MMA_Q; ++mma_q) {
        float o_scale[2][KTraits::NUM_WARPS_KV];
#pragma unroll
        for (uint32_t j = 0; j < 2; ++j) {
          float m_new = -math::inf, d_new = 1.f;
#pragma unroll
          for (uint32_t i = 0; i < KTraits::NUM_WARPS_KV; ++i) {
            float2 md = smem_md[(((i * KTraits::NUM_WARPS_Q + get_warp_idx_q<KTraits>(tid.y)) *
                                      KTraits::NUM_MMA_Q +
                                  mma_q) *
                                     2 +
                                 j) *
                                    8 +
                                lane_idx / 4];
            float m_prev = m_new, d_prev = d_new;
            m_new = max(m_new, md.x);
            // max() drops the NaN from merging two -inf maxima (fully masked rows).
            d_new = d_prev * math::ptx_exp2(max(m_prev - m_new, -math::inf)) +
                    md.y * math::ptx_exp2(max(md.x - m_new, -math::inf));
          }

#pragma unroll
          for (uint32_t i = 0; i < KTraits::NUM_WARPS_KV; ++i) {
            float2 md = smem_md[(((i * KTraits::NUM_WARPS_Q + get_warp_idx_q<KTraits>(tid.y)) *
                                      KTraits::NUM_MMA_Q +
                                  mma_q) *
                                     2 +
                                 j) *
                                    8 +
                                lane_idx / 4];
            float mi = md.x;
            o_scale[j][i] = math::ptx_exp2(max(float(mi - m_new), -math::inf));
          }
          m[mma_q][j] = typename KTraits::DTypeQKAccum(m_new);
          d[mma_q][j] = d_new;
        }

#pragma unroll
        for (uint32_t mma_d = 0; mma_d < KTraits::NUM_MMA_D_VO_TILE; ++mma_d) {
          vec_t<float, 8> o_new;
          o_new.fill(0.f);
#pragma unroll
          for (uint32_t i = 0; i < KTraits::NUM_WARPS_KV; ++i) {
            vec_t<float, 8> oi;
            oi.load(smem_o + ((((i * KTraits::NUM_WARPS_Q + get_warp_idx_q<KTraits>(tid.y)) *
                                    KTraits::NUM_MMA_Q +
                                mma_q) *
                                   KTraits::NUM_MMA_D_VO_TILE +
                               mma_d) *
                                  WARP_SIZE +
                              lane_idx) *
                                 8);

#pragma unroll
            for (uint32_t reg_id = 0; reg_id < 8; ++reg_id) {
              o_new[reg_id] += oi[reg_id] * o_scale[(reg_id % 4) / 2][i];
            }
          }
          o_new.store(o_frag[mma_q][mma_d]);
        }
      }
    } else {
      // synchronize m,d first
      __syncthreads();
#pragma unroll
      for (uint32_t mma_q = 0; mma_q < KTraits::NUM_MMA_Q; ++mma_q) {
#pragma unroll
        for (uint32_t mma_d = 0; mma_d < KTraits::NUM_MMA_D_VO_TILE; ++mma_d) {
          vec_t<float, 8> o_new;
          o_new.fill(0.f);
#pragma unroll
          for (uint32_t i = 0; i < KTraits::NUM_WARPS_KV; ++i) {
            vec_t<float, 8> oi;
            oi.load(smem_o + ((((i * KTraits::NUM_WARPS_Q + get_warp_idx_q<KTraits>(tid.y)) *
                                    KTraits::NUM_MMA_Q +
                                mma_q) *
                                   KTraits::NUM_MMA_D_VO_TILE +
                               mma_d) *
                                  WARP_SIZE +
                              lane_idx) *
                                 8);
#pragma unroll
            for (uint32_t reg_id = 0; reg_id < 8; ++reg_id) {
              o_new[reg_id] += oi[reg_id];
            }
          }
          o_new.store(o_frag[mma_q][mma_d]);
        }
      }
    }
  }
}

template <typename KTraits>
__device__ __forceinline__ void write_o_reg_gmem(
    float (*o_frag)[KTraits::NUM_MMA_D_VO_TILE][8], smem_t<KTraits::SWIZZLE_MODE_Q>* o_smem,
    typename KTraits::DTypeO* o_ptr_base, const uint32_t o_packed_idx_base,
    const uint32_t qo_upper_bound, const uint32_t o_stride_n, const uint32_t o_stride_h,
    const uint_fastdiv group_size, const dim3 tid = threadIdx) {
  using DTypeO = typename KTraits::DTypeO;
  constexpr uint32_t UPCAST_STRIDE_O = KTraits::UPCAST_STRIDE_O;
  const uint32_t warp_idx_x = get_warp_idx_q<KTraits>(tid.y);
  const uint32_t lane_idx = tid.x;

  if constexpr (sizeof(DTypeO) == 4) {
#pragma unroll
    for (uint32_t mma_q = 0; mma_q < KTraits::NUM_MMA_Q; ++mma_q) {
#pragma unroll
      for (uint32_t j = 0; j < 2; ++j) {
        uint32_t q, r;
        group_size.divmod(o_packed_idx_base + lane_idx / 4 + mma_q * 16 + j * 8, q, r);
        const uint32_t o_idx = q;
#pragma unroll
        for (uint32_t mma_d = 0; mma_d < KTraits::NUM_MMA_D_VO_TILE; ++mma_d) {
          if (o_idx < qo_upper_bound) {
            *reinterpret_cast<float2*>(o_ptr_base + q * o_stride_n + r * o_stride_h + mma_d * 16 +
                                       (lane_idx % 4) * 2) =
                *reinterpret_cast<float2*>(&o_frag[mma_q][mma_d][j * 2]);
            *reinterpret_cast<float2*>(o_ptr_base + q * o_stride_n + r * o_stride_h + mma_d * 16 +
                                       8 + (lane_idx % 4) * 2) =
                *reinterpret_cast<float2*>(&o_frag[mma_q][mma_d][4 + j * 2]);
          }
        }
      }
    }
  } else {
    if (get_warp_idx_kv<KTraits>(tid.z) == 0) {
#pragma unroll
      for (uint32_t mma_q = 0; mma_q < KTraits::NUM_MMA_Q; ++mma_q) {
#pragma unroll
        for (uint32_t mma_d = 0; mma_d < KTraits::NUM_MMA_D_VO_TILE; ++mma_d) {
          uint32_t o_frag_f16[8 / 2];
          vec_cast<DTypeO, float>::cast<8>((DTypeO*)o_frag_f16, o_frag[mma_q][mma_d]);

#ifdef FLASHINFER_STMATRIX_M8N8X4_ENABLED
          uint32_t o_smem_offset_w = o_smem->template get_permuted_offset<UPCAST_STRIDE_O>(
              (warp_idx_x * KTraits::NUM_MMA_Q + mma_q) * 16 + lane_idx % 16,
              mma_d * 2 + lane_idx / 16);
          o_smem->stmatrix_m8n8x4(o_smem_offset_w, o_frag_f16);
#else
          uint32_t o_smem_offset_w = o_smem->template get_permuted_offset<UPCAST_STRIDE_O>(
              (warp_idx_x * KTraits::NUM_MMA_Q + mma_q) * 16 + lane_idx / 4, mma_d * 2);
          ((uint32_t*)(o_smem->base + o_smem_offset_w))[lane_idx % 4] = o_frag_f16[0];
          ((uint32_t*)(o_smem->base + o_smem_offset_w + 8 * UPCAST_STRIDE_O))[lane_idx % 4] =
              o_frag_f16[1];
          ((uint32_t*)(o_smem->base + (o_smem_offset_w ^ 0x1)))[lane_idx % 4] = o_frag_f16[2];
          ((uint32_t*)(o_smem->base + (o_smem_offset_w ^ 0x1) +
                       8 * UPCAST_STRIDE_O))[lane_idx % 4] = o_frag_f16[3];
#endif
        }
      }

      uint32_t o_smem_offset_w = o_smem->template get_permuted_offset<UPCAST_STRIDE_O>(
          warp_idx_x * KTraits::NUM_MMA_Q * 16 + lane_idx / 8, lane_idx % 8);

#pragma unroll
      for (uint32_t mma_q = 0; mma_q < KTraits::NUM_MMA_Q; ++mma_q) {
#pragma unroll
        for (uint32_t j = 0; j < 2 * 2; ++j) {
          uint32_t q, r;
          group_size.divmod(o_packed_idx_base + lane_idx / 8 + mma_q * 16 + j * 4, q, r);
          const uint32_t o_idx = q;
          DTypeO* o_ptr =
              o_ptr_base + q * o_stride_n + r * o_stride_h + (lane_idx % 8) * upcast_size<DTypeO>();
#pragma unroll
          for (uint32_t mma_do = 0; mma_do < KTraits::NUM_MMA_D_VO_TILE / 4; ++mma_do) {
            if (o_idx < qo_upper_bound) {
              o_smem->store_128b(o_smem_offset_w, o_ptr);
            }
            o_ptr += 8 * upcast_size<DTypeO>();
            o_smem_offset_w = o_smem->template advance_offset_by_column<8>(o_smem_offset_w, mma_do);
          }
          o_smem_offset_w =
              o_smem->template advance_offset_by_row<4, UPCAST_STRIDE_O>(o_smem_offset_w) -
              2 * KTraits::NUM_MMA_D_VO_TILE;
        }
      }
    }
  }
}

}  // namespace

template <typename KTraits>
__device__ __forceinline__ void vosplit_softmax_store_p(
    typename KTraits::AttentionVariant variant,
    typename KTraits::DTypeQKAccum (*s_frag)[KTraits::NUM_MMA_KV][8],
    typename KTraits::DTypeQKAccum (*m)[2], float (*d)[2], float (*o_scale)[2],
    typename KTraits::SharedStoragePaged* smem_storage, const uint32_t warp_kv_idx,
    const uint32_t warp_q_idx, const uint32_t lane_idx);

template <typename KTraits>
__device__ __forceinline__ void vosplit_compute_pv(
    typename KTraits::SharedStoragePaged* smem_storage,
    float (*o_frag)[KTraits::NUM_MMA_D_VO_PER_WARP][8], float (*o_scale)[2],
    const uint32_t warp_vo_base, const uint32_t warp_q_idx, const uint32_t lane_idx);

template <typename KTraits>
__device__ __forceinline__ void vosplit_write_o(
    float (*o_frag)[KTraits::NUM_MMA_D_VO_PER_WARP][8], float (*d)[2],
    typename KTraits::DTypeO* o_ptr_base, const uint32_t o_packed_idx_base,
    const uint32_t qo_upper_bound, const uint32_t o_stride_n, const uint32_t o_stride_h,
    const uint32_t warp_vo_base, const uint_fastdiv group_size, const uint32_t lane_idx);

/*!
 * \brief FlashAttention prefill CUDA kernel for a single request.
 * \tparam partition_kv Whether to split kv_len into chunks.
 * \tparam mask_mode The mask mode used in the attention operation.
 * \tparam POS_ENCODING_MODE The positional encoding mode.
 * \tparam NUM_MMA_Q The number of fragments in x dimension.
 * \tparam NUM_MMA_D_VO The number of fragments in y dimension.
 * \tparam NUM_MMA_KV The number of fragments in z dimension.
 * \tparam num_warps The number of warps in the threadblock.
 * \tparam DTypeQ The data type of the query tensor.
 * \tparam DTypeKV The data type of the key/value tensor.
 * \tparam DTypeO The data type of the output tensor.
 * \param q The query tensor.
 * \param k The key tensor.
 * \param v The value tensor.
 * \param o The output tensor.
 * \param tmp The temporary buffer (used when partition_kv is true).
 * \param lse The logsumexp value.
 * \param rope_rcp_scale 1/(rope_scale), where rope_scale is the scaling
 *   factor used in RoPE interpolation.
 * \param rope_rcp_theta 1/(rope_theta), where rope_theta is the theta
 *   used in RoPE.
 */
template <typename KTraits, typename Params, typename SmemStorage>
__device__ __forceinline__ void SinglePrefillWithKVCacheDevice(
    const Params params, SmemStorage& smem_storage, const dim3 tid = threadIdx,
    const uint32_t bx = blockIdx.x, const uint32_t chunk_idx = blockIdx.y,
    const uint32_t kv_head_idx = blockIdx.z, const uint32_t num_chunks = gridDim.y,
    const uint32_t num_kv_heads = gridDim.z) {
  using DTypeQ = typename Params::DTypeQ;
#if (__CUDA_ARCH__ < 800)
  if constexpr (std::is_same_v<DTypeQ, nv_bfloat16>) {
    FLASHINFER_RUNTIME_ASSERT("Prefill kernels do not support bf16 on sm75.");
  } else {
#endif
    using DTypeKV = typename Params::DTypeKV;
    using DTypeO = typename Params::DTypeO;
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
    [[maybe_unused]] constexpr uint32_t CTA_TILE_Q = KTraits::CTA_TILE_Q;
    [[maybe_unused]] constexpr uint32_t CTA_TILE_KV = KTraits::CTA_TILE_KV;
    [[maybe_unused]] constexpr uint32_t NUM_WARPS_Q = KTraits::NUM_WARPS_Q;
    [[maybe_unused]] constexpr uint32_t NUM_WARPS_KV = KTraits::NUM_WARPS_KV;
    [[maybe_unused]] constexpr SwizzleMode SWIZZLE_MODE_Q = KTraits::SWIZZLE_MODE_Q;
    [[maybe_unused]] constexpr SwizzleMode SWIZZLE_MODE_KV = KTraits::SWIZZLE_MODE_KV;
    [[maybe_unused]] constexpr uint32_t KV_THR_LAYOUT_ROW = KTraits::KV_THR_LAYOUT_ROW;
    [[maybe_unused]] constexpr uint32_t KV_THR_LAYOUT_COL = KTraits::KV_THR_LAYOUT_COL;
    [[maybe_unused]] constexpr MaskMode MASK_MODE = KTraits::MASK_MODE;

    DTypeQ* q = params.q;
    DTypeKV* k = params.k;
    DTypeKV* v = params.v;
    DTypeO* o = params.o;
    float* lse = params.lse;
    const uint32_t qo_len = params.qo_len;
    const uint32_t kv_len = params.kv_len;
    const bool partition_kv = params.partition_kv;
    const uint32_t q_stride_n = params.q_stride_n;
    const uint32_t q_stride_h = params.q_stride_h;
    const uint32_t k_stride_n = params.k_stride_n;
    const uint32_t k_stride_h = params.k_stride_h;
    const uint32_t v_stride_n = params.v_stride_n;
    const uint32_t v_stride_h = params.v_stride_h;
    const uint_fastdiv& group_size = params.group_size;

    uint8_t* maybe_k_cache_sf = nullptr;
    if constexpr (has_maybe_k_cache_sf_v<Params>) {
      maybe_k_cache_sf = params.maybe_k_cache_sf;
    }
    uint8_t* maybe_v_cache_sf = nullptr;
    if constexpr (has_maybe_v_cache_sf_v<Params>) {
      maybe_v_cache_sf = params.maybe_v_cache_sf;
    }

    static_assert(sizeof(DTypeQ) == 2);
    const uint32_t lane_idx = tid.x, warp_idx = get_warp_idx<KTraits>(tid.y, tid.z);
    const uint32_t num_qo_heads = num_kv_heads * group_size;

    const uint32_t max_chunk_size = partition_kv ? ceil_div(kv_len, num_chunks) : kv_len;
    const uint32_t chunk_start = partition_kv ? chunk_idx * max_chunk_size : 0;
    const uint32_t chunk_end =
        partition_kv ? min((chunk_idx + 1) * max_chunk_size, kv_len) : kv_len;
    const uint32_t chunk_size = chunk_end - chunk_start;

    auto block = cg::this_thread_block();
    auto smem = reinterpret_cast<uint8_t*>(&smem_storage);
    AttentionVariant variant(params, /*batch_idx=*/0, smem);
    const uint32_t window_left = variant.window_left;

    DTypeQKAccum s_frag[NUM_MMA_Q][NUM_MMA_KV][8];
    constexpr uint32_t O_FRAG_D = KTraits::USE_SINGLE_PREFILL_SOFTMAX_VO_SPLIT
                                      ? KTraits::NUM_MMA_D_VO_PER_WARP
                                      : KTraits::NUM_MMA_D_VO_TILE;
    alignas(16) float o_frag[NUM_MMA_Q][O_FRAG_D][8];
    [[maybe_unused]] float o_scale[NUM_MMA_Q][2];
    DTypeQKAccum m[NUM_MMA_Q][2];
    float d[NUM_MMA_Q][2];
    constexpr uint32_t LOCAL_ROPE_FREQ_ROWS =
        (KTraits::POS_ENCODING_MODE == PosEncodingMode::kRoPELlama &&
         !KTraits::USE_SHARED_ROPE_FREQ)
            ? (NUM_MMA_D_QK / 2)
            : 1;
    alignas(16) float local_rope_freq[LOCAL_ROPE_FREQ_ROWS][4];
    float(*rope_freq)[4] = local_rope_freq;
    if constexpr (KTraits::POS_ENCODING_MODE == PosEncodingMode::kRoPELlama) {
      const float rope_rcp_scale = params.rope_rcp_scale;
      const float rope_rcp_theta = params.rope_rcp_theta;
      if constexpr (KTraits::USE_SHARED_ROPE_FREQ) {
        init_shared_rope_freq<KTraits>(smem_storage.rope_freq_smem, rope_rcp_scale, rope_rcp_theta,
                                       tid);
        rope_freq = smem_storage.rope_freq_smem[lane_idx % 4];
      } else {
        init_rope_freq<KTraits>(local_rope_freq, rope_rcp_scale, rope_rcp_theta, tid.x);
      }
    }

    // cooperative fetch q fragment from gmem to reg
    const uint32_t qo_packed_idx_base =
        (bx * NUM_WARPS_Q + get_warp_idx_q<KTraits>(tid.y)) * NUM_MMA_Q * 16;
    smem_t<SWIZZLE_MODE_Q> qo_smem(smem_storage.q_smem);
    const uint32_t o_stride_n = num_qo_heads * HEAD_DIM_VO, o_stride_h = HEAD_DIM_VO;
    DTypeQ* q_ptr_base = q + (kv_head_idx * group_size) * q_stride_h;
    DTypeO* o_ptr_base = partition_kv
                             ? o + chunk_idx * o_stride_n + (kv_head_idx * group_size) * o_stride_h
                             : o + (kv_head_idx * group_size) * o_stride_h;

#pragma unroll 1
    for (uint32_t d_tile = 0;
         d_tile < (KTraits::USE_SINGLE_PREFILL_SOFTMAX_VO_SPLIT ? 1u : KTraits::NUM_D_VO_TILES);
         ++d_tile) {
      [[maybe_unused]] const uint32_t d_base = d_tile * KTraits::NUM_MMA_D_VO_TILE;
      if constexpr (KTraits::USE_SINGLE_PREFILL_SOFTMAX_VO_SPLIT) {
#pragma unroll
        for (uint32_t mma_q = 0; mma_q < NUM_MMA_Q; ++mma_q) {
#pragma unroll
          for (uint32_t local_d = 0; local_d < KTraits::NUM_MMA_D_VO_PER_WARP; ++local_d) {
#pragma unroll
            for (uint32_t reg = 0; reg < 8; ++reg) o_frag[mma_q][local_d][reg] = 0.f;
          }
#pragma unroll
          for (uint32_t j = 0; j < 2; ++j) {
            m[mma_q][j] = DTypeQKAccum(-math::inf);
            d[mma_q][j] = 0.f;
          }
        }
      } else {
        init_states<KTraits>(variant, o_frag, m, d);
      }
      uint32_t q_smem_offset_r = qo_smem.template get_permuted_offset<UPCAST_STRIDE_Q>(
          get_warp_idx_q<KTraits>(tid.y) * NUM_MMA_Q * 16 + lane_idx % 16, lane_idx / 16);
      load_q_global_smem<KTraits>(qo_packed_idx_base, qo_len, q_ptr_base, q_stride_n, q_stride_h,
                                  group_size, &qo_smem, tid);

      cp_async::commit_group();
      if constexpr (KTraits::POS_ENCODING_MODE == PosEncodingMode::kRoPELlama) {
        cp_async::wait_group<0>();
        block.sync();
        q_smem_inplace_apply_rotary<KTraits>(qo_packed_idx_base, qo_len, kv_len, group_size,
                                             &qo_smem, &q_smem_offset_r, rope_freq, tid);
        block.sync();
      }

      // K/V-shared path: V is loaded into k_smem (time-shared); v_smem is a [1] stub.
      smem_t<SWIZZLE_MODE_KV> k_smem(smem_storage.k_smem),
          v_smem(KTraits::USE_KV_SHARED_SMEM ? smem_storage.k_smem : smem_storage.v_smem);

      const uint32_t num_iterations = ceil_div(
          MASK_MODE == MaskMode::kCausal
              ? min(chunk_size, sub_if_greater_or_zero(
                                    kv_len - qo_len + ceil_div(((bx + 1) * CTA_TILE_Q), group_size),
                                    chunk_start))
              : chunk_size,
          CTA_TILE_KV);

      const uint32_t window_iteration =
          ceil_div(sub_if_greater_or_zero(kv_len + ceil_div((bx + 1) * CTA_TILE_Q, group_size),
                                          qo_len + window_left + chunk_start),
                   CTA_TILE_KV);

      const uint32_t mask_iteration =
          (MASK_MODE == MaskMode::kCausal
               ? min(chunk_size,
                     sub_if_greater_or_zero(
                         kv_len + ceil_div((bx * CTA_TILE_Q), group_size) - qo_len, chunk_start))
               : chunk_size) /
          CTA_TILE_KV;

      constexpr uint32_t fp4_pack = is_fp4_type_v<DTypeKV> ? 2 : 1;
      DTypeKV* k_ptr =
          k +
          (chunk_start + warp_idx * KV_THR_LAYOUT_ROW + lane_idx / KV_THR_LAYOUT_COL) * k_stride_n +
          kv_head_idx * k_stride_h +
          (lane_idx % KV_THR_LAYOUT_COL) * upcast_size<DTypeKV>() / fp4_pack;
      DTypeKV* v_ptr =
          v +
          (chunk_start + warp_idx * KV_THR_LAYOUT_ROW + lane_idx / KV_THR_LAYOUT_COL) * v_stride_n +
          kv_head_idx * v_stride_h +
          (lane_idx % KV_THR_LAYOUT_COL) * upcast_size<DTypeKV>() / fp4_pack;

      uint32_t k_smem_offset_r = k_smem.template get_permuted_offset<UPCAST_STRIDE_K>(
                   get_warp_idx_kv<KTraits>(tid.z) * NUM_MMA_KV * 16 + 8 * (lane_idx / 16) +
                       lane_idx % 8,
                   (lane_idx % 16) / 8),
               v_smem_offset_r = v_smem.template get_permuted_offset<UPCAST_STRIDE_V>(
                   get_warp_idx_kv<KTraits>(tid.z) * NUM_MMA_KV * 16 + lane_idx % 16,
                   lane_idx / 16),
               k_smem_offset_w = k_smem.template get_permuted_offset<UPCAST_STRIDE_K>(
                   warp_idx * KV_THR_LAYOUT_ROW + lane_idx / KV_THR_LAYOUT_COL,
                   lane_idx % KV_THR_LAYOUT_COL),
               v_smem_offset_w = v_smem.template get_permuted_offset<UPCAST_STRIDE_V>(
                   warp_idx * KV_THR_LAYOUT_ROW + lane_idx / KV_THR_LAYOUT_COL,
                   lane_idx % KV_THR_LAYOUT_COL);
      // For single prefill, the absolute KV base is just chunk_start (no kv_indptr offset).
      const uint32_t kv_abs_base = chunk_start;
      produce_kv<false, SharedMemFillMode::kNoFill, KTraits>(k_smem, &k_smem_offset_w, &k_ptr,
                                                             k_stride_n, 0, chunk_size, tid);
      produce_kv_sf<false, KTraits>(&smem_storage, maybe_k_cache_sf, kv_abs_base, kv_head_idx,
                                    k_stride_n, k_stride_h, 0, chunk_size, warp_idx, lane_idx);
      cp_async::commit_group();
      if constexpr (!KTraits::USE_KV_SHARED_SMEM) {
        // Shared K/V: don't preload V(0) (it would clobber K(0)); V(0) is loaded
        // inside iter 0 after Q.K^T.
        produce_kv<true, SharedMemFillMode::kFillZero, KTraits>(v_smem, &v_smem_offset_w, &v_ptr,
                                                                v_stride_n, 0, chunk_size, tid);
        produce_kv_sf<true, KTraits>(&smem_storage, maybe_v_cache_sf, kv_abs_base, kv_head_idx,
                                     v_stride_n, v_stride_h, 0, chunk_size, warp_idx, lane_idx);
        cp_async::commit_group();
      }

#pragma unroll 1
      for (uint32_t iter = 0; iter < num_iterations; ++iter) {
        // Shared K/V serializes loads (no K/V prefetch overlap) -> drain fully.
        if constexpr (KTraits::USE_KV_SHARED_SMEM) {
          cp_async::wait_group<0>();
        } else {
          cp_async::wait_group<1>();
        }
        block.sync();

        uint32_t kv_idx_base =
            chunk_start + (iter * NUM_WARPS_KV + get_warp_idx_kv<KTraits>(tid.z)) * NUM_MMA_KV * 16;

        if constexpr (KTraits::POS_ENCODING_MODE == PosEncodingMode::kRoPELlama &&
                      !is_fp4_type_v<DTypeKV>) {
          k_smem_inplace_apply_rotary<KTraits>(chunk_start + iter * CTA_TILE_KV, &k_smem,
                                               &k_smem_offset_r, rope_freq, tid);
          block.sync();
        }

        // compute attention score
        if constexpr (KTraits::POS_ENCODING_MODE == PosEncodingMode::kRoPELlama &&
                      is_fp4_type_v<DTypeKV>) {
          compute_qk_fp4_rope<KTraits>(
              &qo_smem, &q_smem_offset_r, &k_smem, &k_smem_offset_r,
              smem_storage.k_sf_smem_ptr(get_warp_idx_kv<KTraits>(tid.z) * KTraits::NUM_MMA_KV *
                                         16 * KTraits::NUM_MMA_D_QK),
              lane_idx, kv_idx_base, rope_freq, s_frag);
        } else {
          compute_qk<KTraits>(
              &qo_smem, &q_smem_offset_r, &k_smem, &k_smem_offset_r,
              smem_storage.k_sf_smem_ptr(get_warp_idx_kv<KTraits>(tid.z) * KTraits::NUM_MMA_KV *
                                         16 * KTraits::NUM_MMA_D_QK),
              lane_idx, s_frag);
        }
        logits_transform<KTraits>(params, variant, /*batch_idx=*/0, qo_packed_idx_base, kv_idx_base,
                                  qo_len, kv_len, chunk_end, group_size, s_frag, tid, kv_head_idx);

        // apply mask
        if (MASK_MODE == MaskMode::kCustom || (iter >= mask_iteration || iter < window_iteration)) {
          logits_mask<KTraits>(params, variant, /*batch_idx=*/0, qo_packed_idx_base, kv_idx_base,
                               qo_len, kv_len, chunk_end, group_size, s_frag, tid, kv_head_idx);
        }

        // compute m,d states in online softmax
        if constexpr (KTraits::USE_SINGLE_PREFILL_SOFTMAX_VO_SPLIT) {
          vosplit_softmax_store_p<KTraits>(variant, s_frag, m, d, o_scale, &smem_storage,
                                           get_warp_idx_kv<KTraits>(tid.z),
                                           get_warp_idx_q<KTraits>(tid.y), lane_idx);
        } else {
          update_mdo_states<KTraits>(variant, s_frag, o_frag, m, d);
        }

        block.sync();
        if constexpr (KTraits::USE_KV_SHARED_SMEM) {
          // Load V(iter) into k_smem (time-shared) now that Q.K^T is done.
          produce_kv<true, SharedMemFillMode::kFillZero, KTraits>(
              v_smem, &v_smem_offset_w, &v_ptr, v_stride_n, iter * CTA_TILE_KV, chunk_size, tid);
          cp_async::commit_group();
          cp_async::wait_group<0>();
        } else {
          produce_kv<false, SharedMemFillMode::kNoFill, KTraits>(
              k_smem, &k_smem_offset_w, &k_ptr, k_stride_n, (iter + 1) * CTA_TILE_KV, chunk_size,
              tid);
          produce_kv_sf<false, KTraits>(&smem_storage, maybe_k_cache_sf, kv_abs_base, kv_head_idx,
                                        k_stride_n, k_stride_h, (iter + 1) * CTA_TILE_KV,
                                        chunk_size, warp_idx, lane_idx);
          cp_async::commit_group();
          cp_async::wait_group<1>();
        }
        block.sync();

        // compute sfm*v
        if constexpr (KTraits::USE_SINGLE_PREFILL_SOFTMAX_VO_SPLIT) {
          const uint32_t warp_vo_base =
              get_warp_idx_kv<KTraits>(tid.z) * KTraits::NUM_MMA_D_VO_PER_WARP;
          vosplit_compute_pv<KTraits>(&smem_storage, o_frag, o_scale, warp_vo_base,
                                      get_warp_idx_q<KTraits>(tid.y), lane_idx);
        } else {
          compute_sfm_v<KTraits>(
              &v_smem, &v_smem_offset_r,
              smem_storage.v_sf_smem_ptr(get_warp_idx_kv<KTraits>(tid.z) * KTraits::NUM_MMA_KV *
                                         16 * KTraits::NUM_MMA_D_VO),
              lane_idx, s_frag, o_frag, d, d_base);
        }

        block.sync();
        if constexpr (KTraits::USE_KV_SHARED_SMEM) {
          // K(iter+1) goes into the shared buffer only after sfm*v consumed V(iter).
          produce_kv<false, SharedMemFillMode::kNoFill, KTraits>(
              k_smem, &k_smem_offset_w, &k_ptr, k_stride_n, (iter + 1) * CTA_TILE_KV, chunk_size,
              tid);
          cp_async::commit_group();
        } else {
          produce_kv<true, SharedMemFillMode::kFillZero, KTraits>(
              v_smem, &v_smem_offset_w, &v_ptr, v_stride_n, (iter + 1) * CTA_TILE_KV, chunk_size,
              tid);
          produce_kv_sf<true, KTraits>(&smem_storage, maybe_v_cache_sf, kv_abs_base, kv_head_idx,
                                       v_stride_n, v_stride_h, (iter + 1) * CTA_TILE_KV, chunk_size,
                                       warp_idx, lane_idx);
          cp_async::commit_group();
        }
      }
      cp_async::wait_group<0>();
      block.sync();

      finalize_m<KTraits>(variant, m);

      if constexpr (KTraits::USE_SINGLE_PREFILL_SOFTMAX_VO_SPLIT) {
        vosplit_write_o<KTraits>(o_frag, d, o_ptr_base, qo_packed_idx_base, qo_len,
                                 partition_kv ? num_chunks * o_stride_n : o_stride_n, o_stride_h,
                                 get_warp_idx_kv<KTraits>(tid.z) * KTraits::NUM_MMA_D_VO_PER_WARP,
                                 group_size, lane_idx);
      } else {
        // threadblock synchronization
        threadblock_sync_mdo_states<KTraits>(o_frag, &smem_storage, m, d, warp_idx, lane_idx, tid);

        // transform output
        transform_output<KTraits, Params>(params, variant, o_frag, m, d, /*batch_idx=*/0, chunk_idx,
                                          qo_packed_idx_base, warp_idx, lane_idx, kv_head_idx,
                                          group_size);

        // write back (o_ptr_base offset to this VO tile's columns: d_base mma * 16 elems)
        write_o_reg_gmem<KTraits>(o_frag, &qo_smem, o_ptr_base + d_base * 16, qo_packed_idx_base,
                                  qo_len,
                                  /*o_stride_n=*/
                                  partition_kv ? num_chunks * o_stride_n : o_stride_n,
                                  /*o_stride_h=*/o_stride_h, group_size, tid);
      }

      // write lse (identical across VO tiles; redundant rewrite on later tiles is harmless)
      if constexpr (variant.use_softmax) {
        if (lse != nullptr || partition_kv) {
          if (get_warp_idx_kv<KTraits>(tid.z) == 0) {
#pragma unroll
            for (uint32_t mma_q = 0; mma_q < NUM_MMA_Q; ++mma_q) {
#pragma unroll
              for (uint32_t j = 0; j < 2; ++j) {
                uint32_t q, r;
                group_size.divmod(qo_packed_idx_base + lane_idx / 4 + j * 8 + mma_q * 16, q, r);
                const uint32_t qo_head_idx = kv_head_idx * group_size + r;
                const uint32_t qo_idx = q;
                if (qo_idx < qo_len) {
                  if (partition_kv) {
                    lse[(qo_idx * num_chunks + chunk_idx) * num_qo_heads + qo_head_idx] =
                        math::ptx_log2(d[mma_q][j]) + float(m[mma_q][j]);
                  } else {
                    lse[qo_idx * num_qo_heads + qo_head_idx] =
                        math::ptx_log2(d[mma_q][j]) + float(m[mma_q][j]);
                  }
                }
              }
            }
          }
        }
      }
      block.sync();
    }  // d_tile (split-D over VO) loop
#if (__CUDA_ARCH__ < 800)
  }
#endif
}

template <typename KTraits, typename Params>
__global__ __launch_bounds__(KTraits::NUM_THREADS) void SinglePrefillWithKVCacheKernel(
    const __grid_constant__ Params params) {
  extern __shared__ uint8_t smem[];
  using SmemStorage =
      std::conditional_t<KTraits::USE_SINGLE_PREFILL_SOFTMAX_VO_SPLIT,
                         typename KTraits::SharedStoragePaged, typename KTraits::SharedStorage>;
  auto& smem_storage = reinterpret_cast<SmemStorage&>(smem);
  SinglePrefillWithKVCacheDevice<KTraits>(params, smem_storage);
}

template <uint32_t HEAD_DIM_QK, uint32_t HEAD_DIM_VO, PosEncodingMode POS_ENCODING_MODE,
          bool USE_FP16_QK_REDUCTION, MaskMode MASK_MODE, typename AttentionVariant,
          typename Params>
cudaError_t SinglePrefillWithKVCacheDispatched(Params params, typename Params::DTypeO* tmp,
                                               cudaStream_t stream) {
  using DTypeQ = typename Params::DTypeQ;
  using DTypeKV = typename Params::DTypeKV;
  using DTypeO = typename Params::DTypeO;
  const uint32_t num_qo_heads = params.num_qo_heads;
  const uint32_t num_kv_heads = params.num_kv_heads;
  const uint32_t qo_len = params.qo_len;
  const uint32_t kv_len = params.kv_len;
  if (kv_len < qo_len && MASK_MODE == MaskMode::kCausal) {
    std::ostringstream err_msg;
    err_msg << "When mask_mode is set to MaskMode::kCausal, kv_len must be greater than or equal "
               "to qo_len, got kv_len"
            << kv_len << " and qo_len " << qo_len;
    FLASHINFER_ERROR(err_msg.str());
  }

  const uint32_t group_size = num_qo_heads / num_kv_heads;
  constexpr uint32_t NUM_MMA_D_QK = HEAD_DIM_QK / 16;
  constexpr uint32_t NUM_MMA_D_VO = HEAD_DIM_VO / 16;
  int64_t packed_qo_len = qo_len * group_size;
  uint32_t cta_tile_q =
      FA2DetermineCtaTileQ(packed_qo_len, HEAD_DIM_VO, HEAD_DIM_QK, sizeof(DTypeKV));

  DISPATCH_CTA_TILE_Q(cta_tile_q, CTA_TILE_Q, {
    // hd512 uses the 2-Q x 2-KV-warp layout at CTA_TILE_Q=32. FP8 must take it
    // too: it stays on the split-D merge path whose cta_sync_o_smem buffer
    // (NUM_WARPS_KV x CTA_TILE_Q x 256 floats) only fits ~99KB-per-block parts
    // (SM120/121) with NUM_WARPS_KV=2. FP4 large-head single-prefill
    // additionally uses VO-split output so it avoids that cta_sync_o_smem
    // traffic entirely.
    constexpr bool kLargeHeadWarpSplit = ((sizeof(DTypeKV) <= 2) || is_fp4_type_v<DTypeKV>) &&
                                         (HEAD_DIM_VO >= 512) && (CTA_TILE_Q == 32);
    constexpr uint32_t NUM_WARPS_Q = kLargeHeadWarpSplit ? 2 : get_num_warps_q(CTA_TILE_Q);
    constexpr uint32_t NUM_WARPS_KV = kLargeHeadWarpSplit ? 2 : get_num_warps_kv(CTA_TILE_Q);
    constexpr uint32_t NUM_MMA_Q = kLargeHeadWarpSplit ? 1 : get_num_mma_q(CTA_TILE_Q);

    using DTypeQKAccum =
        typename std::conditional<USE_FP16_QK_REDUCTION && std::is_same_v<DTypeQ, half>, half,
                                  float>::type;

    int dev_id = 0;
    FLASHINFER_CUDA_CALL(cudaGetDevice(&dev_id));
    int max_smem_per_sm = 0;
    FLASHINFER_CUDA_CALL(cudaDeviceGetAttribute(
        &max_smem_per_sm, cudaDevAttrMaxSharedMemoryPerMultiprocessor, dev_id));
    int max_smem_per_block_optin = 0;
    FLASHINFER_CUDA_CALL(cudaDeviceGetAttribute(&max_smem_per_block_optin,
                                                cudaDevAttrMaxSharedMemoryPerBlockOptin, dev_id));
    // we expect each sm execute two threadblocks
    // Per-NUM_MMA_KV K/V shared-memory cost, including the single BF16 repack
    // staging buffer (sized max(HEAD_DIM_QK, HEAD_DIM_VO)) when the FP8 repack
    // path is active, so NUM_MMA_KV is chosen to keep base+staging within the
    // occupancy budget. NOTE: single-prefill doesn't use the repack, but the
    // staging buffer still lives in SharedStorageQKVO, so it must be accounted.
    constexpr bool kUseRepack = (sizeof(DTypeKV) == 1) && !is_fp4_type_v<DTypeKV> &&
                                (HEAD_DIM_VO != 64) && (HEAD_DIM_VO <= 256) && (CTA_TILE_Q > 16);
    constexpr bool kKVShared = !is_fp4_type_v<DTypeKV> && (HEAD_DIM_VO / 16 > 16) &&
                               ((HEAD_DIM_VO / 16) % NUM_WARPS_KV == 0) &&
                               (HEAD_DIM_QK == HEAD_DIM_VO) &&
                               (sizeof(DTypeKV) == 2 || CTA_TILE_Q > 16);
    constexpr bool kSinglePrefillVOSplitDispatch =
        AttentionVariant::use_softmax && is_fp4_type_v<DTypeKV> && (HEAD_DIM_VO / 16 > 16) &&
        ((HEAD_DIM_VO / 16) % NUM_WARPS_KV == 0);
    constexpr uint32_t kKVSmemPerMmaKV =
        (kKVShared ? (HEAD_DIM_QK * 16 * NUM_WARPS_KV * sizeof(DTypeKV))
                   : ((HEAD_DIM_QK + HEAD_DIM_VO) * 16 * NUM_WARPS_KV * sizeof(DTypeKV))) +
        (kUseRepack ? ((HEAD_DIM_QK > HEAD_DIM_VO ? HEAD_DIM_QK : HEAD_DIM_VO) * 16 * NUM_WARPS_KV *
                       sizeof(DTypeQ))
                    : 0u) +
        (is_fp4_type_v<DTypeKV>
             ? ((HEAD_DIM_QK + HEAD_DIM_VO) * 16 * NUM_WARPS_KV / NVFP4_SF_VEC_SIZE)
             : 0u) +
        (kSinglePrefillVOSplitDispatch ? CTA_TILE_Q * NUM_WARPS_KV * 16 * sizeof(DTypeQ) : 0u);
    constexpr uint32_t kSharedRopeFreqSmem = (POS_ENCODING_MODE == PosEncodingMode::kRoPELlama &&
                                              HEAD_DIM_QK > 256 && HEAD_DIM_QK == HEAD_DIM_VO)
                                                 ? (4u * (NUM_MMA_D_QK / 2) * 4u * sizeof(float))
                                                 : 0u;
    constexpr uint32_t kSinglePrefillVOSplitFixedSmem =
        kSinglePrefillVOSplitDispatch ? (NUM_WARPS_KV * CTA_TILE_Q * 8u + 2048u) : 0u;
    constexpr uint32_t kFixedSmem = CTA_TILE_Q * HEAD_DIM_QK * sizeof(DTypeQ) +
                                    kSinglePrefillVOSplitFixedSmem + kSharedRopeFreqSmem;
    // Smallest NUM_MMA_KV satisfying the FP8 alignment constraint
    // (sizeof(DTypeKV)==1 requires NUM_MMA_KV*2 % NUM_WARPS_Q == 0); size the
    // occupancy budget against the minimum *valid* tile so the staging buffer
    // can't shrink NUM_MMA_KV onto an invalid value on tight-smem parts (SM120).
    constexpr uint32_t kMinValidMmaKV =
        (sizeof(DTypeKV) == 1 && NUM_WARPS_Q > 2) ? (NUM_WARPS_Q / 2) : 1;
    const int num_ctas_per_sm =
        max_smem_per_sm >= 2 * (kFixedSmem + kMinValidMmaKV * kKVSmemPerMmaKV) ? 2 : 1;
    const int max_smem_per_threadblock =
        min(max_smem_per_sm / num_ctas_per_sm, max_smem_per_block_optin);

    const uint32_t max_num_mma_kv_reg =
        (HEAD_DIM_VO >= 128 && NUM_MMA_Q == 2 && POS_ENCODING_MODE == PosEncodingMode::kRoPELlama &&
         !USE_FP16_QK_REDUCTION)
            ? 2
            : (8 / NUM_MMA_Q);
    const int max_num_mma_kv_smem = (max_smem_per_threadblock - static_cast<int>(kFixedSmem)) /
                                    static_cast<int>(kKVSmemPerMmaKV);
    if (max_num_mma_kv_smem < 1) {
      std::ostringstream err_msg;
      err_msg << "Even the smallest KV tile for head_dim_qk=" << HEAD_DIM_QK
              << ", head_dim_vo=" << HEAD_DIM_VO << ", cta_tile_q=" << CTA_TILE_Q
              << " exceeds this GPU's " << max_smem_per_block_optin
              << " bytes of shared memory per block; this configuration is not supported on "
                 "this architecture.";
      FLASHINFER_ERROR(err_msg.str());
    }

    // control NUM_MMA_KV for maximum warp occupancy
    DISPATCH_NUM_MMA_KV(
        min(static_cast<uint32_t>(max_num_mma_kv_smem), max_num_mma_kv_reg), NUM_MMA_KV, {
          using KTraits =
              KernelTraits<MASK_MODE, CTA_TILE_Q, NUM_MMA_Q, NUM_MMA_KV, NUM_MMA_D_QK, NUM_MMA_D_VO,
                           NUM_WARPS_Q, NUM_WARPS_KV, POS_ENCODING_MODE, DTypeQ, DTypeKV, DTypeO,
                           DTypeQKAccum, typename Params::IdType, AttentionVariant>;
          if constexpr (KTraits::IsInvalid()) {
            // Invalid configuration, skip
            std::ostringstream err_msg;
            err_msg
                << "FlashInfer Internal Error: Invalid configuration : NUM_MMA_Q=" << NUM_MMA_Q
                << " NUM_MMA_D_QK=" << NUM_MMA_D_QK << " NUM_MMA_D_VO=" << NUM_MMA_D_VO
                << " NUM_MMA_KV=" << NUM_MMA_KV << " NUM_WARPS_Q=" << NUM_WARPS_Q
                << " NUM_WARPS_KV=" << NUM_WARPS_KV
                << " please create an issue (https://github.com/flashinfer-ai/flashinfer/issues)"
                   " and report the issue to the developers.";
            FLASHINFER_ERROR(err_msg.str());
          } else {
            constexpr uint32_t num_threads = (NUM_WARPS_Q * NUM_WARPS_KV) * WARP_SIZE;
            auto kernel = SinglePrefillWithKVCacheKernel<KTraits, Params>;
            using SmemStorage = std::conditional_t<KTraits::USE_SINGLE_PREFILL_SOFTMAX_VO_SPLIT,
                                                   typename KTraits::SharedStoragePaged,
                                                   typename KTraits::SharedStorage>;
            size_t smem_size = sizeof(SmemStorage);
            if (smem_size > (size_t)max_smem_per_block_optin) {
              std::ostringstream err_msg;
              err_msg << "Required shared memory (" << smem_size
                      << " bytes) for head_dim_qk=" << HEAD_DIM_QK
                      << ", head_dim_vo=" << HEAD_DIM_VO << ", cta_tile_q=" << CTA_TILE_Q
                      << " exceeds this GPU's per-block limit (" << max_smem_per_block_optin
                      << " bytes); this configuration is not supported on this architecture.";
              FLASHINFER_ERROR(err_msg.str());
            }
            FLASHINFER_CUDA_CALL(cudaFuncSetAttribute(
                kernel, cudaFuncAttributeMaxDynamicSharedMemorySize, smem_size));
            int num_blocks_per_sm = 0;
            int num_sm = 0;
            FLASHINFER_CUDA_CALL(
                cudaDeviceGetAttribute(&num_sm, cudaDevAttrMultiProcessorCount, dev_id));
            FLASHINFER_CUDA_CALL(cudaOccupancyMaxActiveBlocksPerMultiprocessor(
                &num_blocks_per_sm, kernel, num_threads, smem_size));
            uint32_t max_num_kv_chunks = (num_blocks_per_sm * num_sm) /
                                         (num_kv_heads * ceil_div(qo_len * group_size, CTA_TILE_Q));
            uint32_t num_chunks;
            if (max_num_kv_chunks > 0) {
              uint32_t chunk_size = max(ceil_div(kv_len, max_num_kv_chunks), 256);
              num_chunks = ceil_div(kv_len, chunk_size);
            } else {
              num_chunks = 0;
            }

            if (num_chunks <= 1 || tmp == nullptr) {
              // Enough parallelism, do not split-kv
              params.partition_kv = false;
              void* args[] = {(void*)&params};
              dim3 nblks(ceil_div(qo_len * group_size, CTA_TILE_Q), 1, num_kv_heads);
              dim3 nthrs(32, NUM_WARPS_Q, NUM_WARPS_KV);
              FLASHINFER_CUDA_CALL(
                  cudaLaunchKernel((void*)kernel, nblks, nthrs, args, smem_size, stream));
            } else {
              // Use cooperative groups to increase occupancy
              params.partition_kv = true;
              float* tmp_lse = (float*)(tmp + num_chunks * qo_len * num_qo_heads * HEAD_DIM_VO);
              auto o = params.o;
              auto lse = params.lse;
              params.o = tmp;
              params.lse = tmp_lse;
              void* args[] = {(void*)&params};
              dim3 nblks(ceil_div(qo_len * group_size, CTA_TILE_Q), num_chunks, num_kv_heads);
              dim3 nthrs(32, NUM_WARPS_Q, NUM_WARPS_KV);

              FLASHINFER_CUDA_CALL(
                  cudaLaunchKernel((void*)kernel, nblks, nthrs, args, smem_size, stream));
              if constexpr (AttentionVariant::use_softmax) {
                FLASHINFER_CUDA_CALL(MergeStates(tmp, tmp_lse, o, lse, num_chunks, qo_len,
                                                 num_qo_heads, HEAD_DIM_VO, stream));
              } else {
                FLASHINFER_CUDA_CALL(
                    AttentionSum(tmp, o, num_chunks, qo_len, num_qo_heads, HEAD_DIM_VO, stream));
              }
            }
          }
        })
  });
  return cudaSuccess;
}

// VO-split helpers used by large-head prefill kernels. Definitions live below the
// ragged kernel so the paged device path can stay grouped with the helper bodies.
template <typename KTraits>
__device__ __forceinline__ void vosplit_softmax_store_p(
    typename KTraits::AttentionVariant variant,
    typename KTraits::DTypeQKAccum (*s_frag)[KTraits::NUM_MMA_KV][8],
    typename KTraits::DTypeQKAccum (*m)[2], float (*d)[2], float (*o_scale)[2],
    typename KTraits::SharedStoragePaged* smem_storage, const uint32_t warp_kv_idx,
    const uint32_t warp_q_idx, const uint32_t lane_idx);

template <typename KTraits>
__device__ __forceinline__ void vosplit_compute_pv(
    typename KTraits::SharedStoragePaged* smem_storage,
    float (*o_frag)[KTraits::NUM_MMA_D_VO_PER_WARP][8], float (*o_scale)[2],
    const uint32_t warp_vo_base, const uint32_t warp_q_idx, const uint32_t lane_idx);

template <typename KTraits>
__device__ __forceinline__ void vosplit_write_o(
    float (*o_frag)[KTraits::NUM_MMA_D_VO_PER_WARP][8], float (*d)[2],
    typename KTraits::DTypeO* o_ptr_base, const uint32_t o_packed_idx_base,
    const uint32_t qo_upper_bound, const uint32_t o_stride_n, const uint32_t o_stride_h,
    const uint32_t warp_vo_base, const uint_fastdiv group_size, const uint32_t lane_idx);

template <typename KTraits, typename Params>
__global__ __launch_bounds__(KTraits::NUM_THREADS) void BatchPrefillWithRaggedKVCacheKernel(
    const __grid_constant__ Params params) {
  using DTypeQ = typename Params::DTypeQ;
#if (__CUDA_ARCH__ < 800)
  if constexpr (std::is_same_v<DTypeQ, nv_bfloat16>) {
    FLASHINFER_RUNTIME_ASSERT("Prefill kernels do not support bf16 on sm75.");
  } else {
#endif
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
    [[maybe_unused]] constexpr uint32_t CTA_TILE_Q = KTraits::CTA_TILE_Q;
    [[maybe_unused]] constexpr uint32_t CTA_TILE_KV = KTraits::CTA_TILE_KV;
    [[maybe_unused]] constexpr uint32_t NUM_WARPS_Q = KTraits::NUM_WARPS_Q;
    [[maybe_unused]] constexpr uint32_t NUM_WARPS_KV = KTraits::NUM_WARPS_KV;
    [[maybe_unused]] constexpr SwizzleMode SWIZZLE_MODE_Q = KTraits::SWIZZLE_MODE_Q;
    [[maybe_unused]] constexpr SwizzleMode SWIZZLE_MODE_KV = KTraits::SWIZZLE_MODE_KV;
    [[maybe_unused]] constexpr uint32_t KV_THR_LAYOUT_ROW = KTraits::KV_THR_LAYOUT_ROW;
    [[maybe_unused]] constexpr uint32_t KV_THR_LAYOUT_COL = KTraits::KV_THR_LAYOUT_COL;
    [[maybe_unused]] constexpr MaskMode MASK_MODE = KTraits::MASK_MODE;

    DTypeQ* q = params.q;
    IdType* request_indices = params.request_indices;
    IdType* qo_tile_indices = params.qo_tile_indices;
    IdType* kv_tile_indices = params.kv_tile_indices;
    IdType* q_indptr = params.q_indptr;
    IdType* kv_indptr = params.kv_indptr;
    DTypeKV* k = params.k;
    DTypeKV* v = params.v;
    IdType* o_indptr = params.o_indptr;
    DTypeO* o = params.o;
    float* lse = params.lse;
    bool* block_valid_mask = params.block_valid_mask;
    const bool partition_kv = params.partition_kv;
    const uint32_t q_stride_n = params.q_stride_n;
    const uint32_t q_stride_h = params.q_stride_h;
    const uint32_t k_stride_n = params.k_stride_n;
    const uint32_t k_stride_h = params.k_stride_h;
    const uint32_t v_stride_n = params.v_stride_n;
    const uint32_t v_stride_h = params.v_stride_h;
    const uint_fastdiv& group_size = params.group_size;

    uint8_t* maybe_k_cache_sf = nullptr;
    if constexpr (has_maybe_k_cache_sf_v<Params>) {
      maybe_k_cache_sf = params.maybe_k_cache_sf;
    }
    uint8_t* maybe_v_cache_sf = nullptr;
    if constexpr (has_maybe_v_cache_sf_v<Params>) {
      maybe_v_cache_sf = params.maybe_v_cache_sf;
    }

    static_assert(sizeof(DTypeQ) == 2);
    const uint32_t kv_chunk_size = *(params.kv_chunk_size_ptr);
    const dim3& tid = threadIdx;

    auto block = cg::this_thread_block();
    const uint32_t bx = blockIdx.x, lane_idx = tid.x,
                   warp_idx = get_warp_idx<KTraits>(tid.y, tid.z), kv_head_idx = blockIdx.z;
    if (block_valid_mask && !block_valid_mask[bx]) {
      return;
    }
    const uint32_t num_kv_heads = gridDim.z, num_qo_heads = group_size * num_kv_heads;
    const uint32_t request_idx = request_indices[bx], qo_tile_idx = qo_tile_indices[bx],
                   kv_tile_idx = kv_tile_indices[bx];
    extern __shared__ uint8_t smem[];
    using SmemStorage =
        std::conditional_t<KTraits::USE_SOFTMAX_VO_SPLIT, typename KTraits::SharedStoragePaged,
                           typename KTraits::SharedStorage>;
    auto& smem_storage = reinterpret_cast<SmemStorage&>(smem);
    AttentionVariant variant(params, /*batch_idx=*/request_idx, smem);
    const uint32_t qo_len = variant.qo_len, kv_len = variant.kv_len,
                   window_left = variant.window_left;
    const uint32_t kv_len_safe = kv_len > 0 ? kv_len : 1;
    const uint32_t qo_upper_bound =
        min(qo_len, ceil_div((qo_tile_idx + 1) * CTA_TILE_Q, group_size));

    // skip out-of-window kv tile by add non-zero kv_start_idx offset
    const uint32_t kv_start_idx = sub_if_greater_or_zero(
        kv_len + (qo_tile_idx * CTA_TILE_Q) / group_size, qo_len + window_left);
    const uint32_t max_chunk_size = partition_kv ? kv_chunk_size : kv_len - kv_start_idx;
    const uint32_t chunk_start =
        partition_kv ? min(kv_tile_idx * max_chunk_size + kv_start_idx, kv_len) : kv_start_idx;
    const uint32_t chunk_end =
        partition_kv ? min((kv_tile_idx + 1) * max_chunk_size + kv_start_idx, kv_len) : kv_len;
    const uint32_t chunk_size = chunk_end - chunk_start;
    // Absolute first token index for this CTA tile (used by produce_kv_sf).
    const uint32_t kv_abs_base = kv_indptr[request_idx] + chunk_start;

    DTypeQKAccum s_frag[NUM_MMA_Q][NUM_MMA_KV][8];
    constexpr uint32_t O_FRAG_D =
        KTraits::USE_SOFTMAX_VO_SPLIT ? KTraits::NUM_MMA_D_VO_PER_WARP : KTraits::NUM_MMA_D_VO_TILE;
    alignas(16) float o_frag[NUM_MMA_Q][O_FRAG_D][8];
    [[maybe_unused]] float o_scale[NUM_MMA_Q][2];
    DTypeQKAccum m[NUM_MMA_Q][2];
    float d[NUM_MMA_Q][2];
    constexpr uint32_t LOCAL_ROPE_FREQ_ROWS =
        (KTraits::POS_ENCODING_MODE == PosEncodingMode::kRoPELlama &&
         !KTraits::USE_SHARED_ROPE_FREQ)
            ? (NUM_MMA_D_QK / 2)
            : 1;
    alignas(16) float local_rope_freq[LOCAL_ROPE_FREQ_ROWS][4];
    float(*rope_freq)[4] = local_rope_freq;

    if constexpr (KTraits::POS_ENCODING_MODE == PosEncodingMode::kRoPELlama) {
      const float rope_rcp_scale = params.rope_rcp_scale;
      const float rope_rcp_theta = params.rope_rcp_theta;
      if constexpr (KTraits::USE_SHARED_ROPE_FREQ) {
        init_shared_rope_freq<KTraits>(smem_storage.rope_freq_smem, rope_rcp_scale, rope_rcp_theta,
                                       tid);
        rope_freq = smem_storage.rope_freq_smem[lane_idx % 4];
      } else {
        init_rope_freq<KTraits>(local_rope_freq, rope_rcp_scale, rope_rcp_theta, tid.x);
      }
    }

    const uint32_t qo_packed_idx_base =
        (qo_tile_idx * NUM_WARPS_Q + get_warp_idx_q<KTraits>(tid.y)) * NUM_MMA_Q * 16;
    smem_t<SWIZZLE_MODE_Q> qo_smem(smem_storage.q_smem);
    const uint32_t o_stride_n = num_qo_heads * HEAD_DIM_VO, o_stride_h = HEAD_DIM_VO;

    DTypeQ* q_ptr_base =
        q + q_indptr[request_idx] * q_stride_n + kv_head_idx * group_size * q_stride_h;

    DTypeO* o_ptr_base = partition_kv ? o + (o_indptr[request_idx] + kv_tile_idx) * o_stride_n +
                                            (kv_head_idx * group_size) * o_stride_h
                                      : o + o_indptr[request_idx] * o_stride_n +
                                            (kv_head_idx * group_size) * o_stride_h;

#if (__CUDACC_VER_MAJOR__ >= 12 && defined(__CUDA_ARCH__) && (__CUDA_ARCH__ >= 900))
    asm volatile("griddepcontrol.wait;");
#endif

#pragma unroll 1
    for (uint32_t d_tile = 0;
         d_tile < (KTraits::USE_SOFTMAX_VO_SPLIT ? 1u : KTraits::NUM_D_VO_TILES); ++d_tile) {
      [[maybe_unused]] const uint32_t d_base = d_tile * KTraits::NUM_MMA_D_VO_TILE;
      if constexpr (KTraits::USE_SOFTMAX_VO_SPLIT) {
#pragma unroll
        for (uint32_t mma_q = 0; mma_q < NUM_MMA_Q; ++mma_q) {
#pragma unroll
          for (uint32_t local_d = 0; local_d < KTraits::NUM_MMA_D_VO_PER_WARP; ++local_d) {
#pragma unroll
            for (uint32_t reg = 0; reg < 8; ++reg) o_frag[mma_q][local_d][reg] = 0.f;
          }
#pragma unroll
          for (uint32_t j = 0; j < 2; ++j) {
            m[mma_q][j] = DTypeQKAccum(-math::inf);
            d[mma_q][j] = 0.f;
          }
        }
      } else {
        init_states<KTraits>(variant, o_frag, m, d);
      }
      uint32_t q_smem_offset_r = qo_smem.get_permuted_offset<UPCAST_STRIDE_Q>(
          get_warp_idx_q<KTraits>(tid.y) * NUM_MMA_Q * 16 + lane_idx % 16, lane_idx / 16);

      load_q_global_smem<KTraits>(qo_packed_idx_base, qo_upper_bound, q_ptr_base, q_stride_n,
                                  q_stride_h, group_size, &qo_smem, tid);

      cp_async::commit_group();

      if constexpr (KTraits::POS_ENCODING_MODE == PosEncodingMode::kRoPELlama) {
        cp_async::wait_group<0>();
        block.sync();
        IdType* q_rope_offset = nullptr;

        if constexpr (has_maybe_q_rope_offset_v<Params>) {
          q_rope_offset = params.maybe_q_rope_offset;
        }
        if (!q_rope_offset) {
          q_smem_inplace_apply_rotary<KTraits>(qo_packed_idx_base, qo_len, kv_len, group_size,
                                               &qo_smem, &q_smem_offset_r, rope_freq, tid);
        } else {
          q_smem_inplace_apply_rotary_with_pos<KTraits>(
              qo_packed_idx_base, q_rope_offset + q_indptr[request_idx], &qo_smem, group_size,
              &q_smem_offset_r, rope_freq, tid);
        }
        block.sync();
      }

      const uint32_t num_iterations = ceil_div(
          (MASK_MODE == MaskMode::kCausal
               ? min(chunk_size,
                     sub_if_greater_or_zero(
                         kv_len - qo_len + ceil_div(((qo_tile_idx + 1) * CTA_TILE_Q), group_size),
                         chunk_start))
               : chunk_size),
          CTA_TILE_KV);

      const uint32_t window_iteration = ceil_div(
          sub_if_greater_or_zero(kv_len + ceil_div((qo_tile_idx + 1) * CTA_TILE_Q, group_size),
                                 qo_len + window_left + chunk_start),
          CTA_TILE_KV);

      const uint32_t mask_iteration =
          (MASK_MODE == MaskMode::kCausal
               ? min(chunk_size,
                     sub_if_greater_or_zero(
                         kv_len + ceil_div((qo_tile_idx * CTA_TILE_Q), group_size) - qo_len,
                         chunk_start))
               : chunk_size) /
          CTA_TILE_KV;

      // K/V-shared path: V is loaded into k_smem (time-shared); v_smem is a [1] stub.
      smem_t<SWIZZLE_MODE_KV> k_smem(smem_storage.k_smem),
          v_smem(KTraits::USE_KV_SHARED_SMEM ? smem_storage.k_smem : smem_storage.v_smem);

      uint32_t k_smem_offset_r = k_smem.template get_permuted_offset<UPCAST_STRIDE_K>(
                   get_warp_idx_kv<KTraits>(tid.z) * NUM_MMA_KV * 16 + 8 * (lane_idx / 16) +
                       lane_idx % 8,
                   (lane_idx % 16) / 8),
               v_smem_offset_r = v_smem.template get_permuted_offset<UPCAST_STRIDE_V>(
                   get_warp_idx_kv<KTraits>(tid.z) * NUM_MMA_KV * 16 + lane_idx % 16,
                   lane_idx / 16),
               k_smem_offset_w = k_smem.template get_permuted_offset<UPCAST_STRIDE_K>(
                   warp_idx * KV_THR_LAYOUT_ROW + lane_idx / KV_THR_LAYOUT_COL,
                   lane_idx % KV_THR_LAYOUT_COL),
               v_smem_offset_w = v_smem.template get_permuted_offset<UPCAST_STRIDE_V>(
                   warp_idx * KV_THR_LAYOUT_ROW + lane_idx / KV_THR_LAYOUT_COL,
                   lane_idx % KV_THR_LAYOUT_COL);

      // FP8 repack path: BF16 staging buffers + their (16-bit-strided) read offsets.
      // Guard offsets by USE_KV_REPACK so the stride-8 get_permuted_offset isn't
      // instantiated for the k64B swizzle (HEAD_DIM_VO == 64), which requires stride==4.
      smem_t<SWIZZLE_MODE_KV> k_smem_bf16(smem_storage.kv_smem_repack_ptr()),
          v_smem_bf16(smem_storage.kv_smem_repack_ptr());
      uint32_t k_smem_offset_r_bf16 = 0, v_smem_offset_r_bf16 = 0;
      if constexpr (KTraits::USE_KV_REPACK) {
        k_smem_offset_r_bf16 = k_smem_bf16.template get_permuted_offset<KTraits::REPACK_STRIDE_QK>(
            get_warp_idx_kv<KTraits>(tid.z) * NUM_MMA_KV * 16 + 8 * (lane_idx / 16) + lane_idx % 8,
            (lane_idx % 16) / 8);
        v_smem_offset_r_bf16 = v_smem_bf16.template get_permuted_offset<KTraits::REPACK_STRIDE_VO>(
            get_warp_idx_kv<KTraits>(tid.z) * NUM_MMA_KV * 16 + lane_idx % 16, lane_idx / 16);
      }

      constexpr uint32_t fp4_pack = is_fp4_type_v<DTypeKV> ? 2 : 1;
      DTypeKV* k_ptr = k +
                       (kv_indptr[request_idx] + chunk_start + warp_idx * KV_THR_LAYOUT_ROW +
                        lane_idx / KV_THR_LAYOUT_COL) *
                           k_stride_n +
                       kv_head_idx * k_stride_h +
                       (lane_idx % KV_THR_LAYOUT_COL) * upcast_size<DTypeKV>() / fp4_pack;
      DTypeKV* v_ptr = v +
                       (kv_indptr[request_idx] + chunk_start + warp_idx * KV_THR_LAYOUT_ROW +
                        lane_idx / KV_THR_LAYOUT_COL) *
                           v_stride_n +
                       kv_head_idx * v_stride_h +
                       (lane_idx % KV_THR_LAYOUT_COL) * upcast_size<DTypeKV>() / fp4_pack;

      produce_kv<false, SharedMemFillMode::kNoFill, KTraits>(k_smem, &k_smem_offset_w, &k_ptr,
                                                             k_stride_n, 0, chunk_size, tid);
      produce_kv_sf<false, KTraits>(&smem_storage, maybe_k_cache_sf, kv_abs_base, kv_head_idx,
                                    k_stride_n, k_stride_h, 0, chunk_size, warp_idx, lane_idx);
      cp_async::commit_group();
      if constexpr (!KTraits::USE_KV_SHARED_SMEM) {
        // Shared K/V: don't preload V(0) (it would clobber K(0)); V(0) is loaded
        // inside iter 0 after Q.K^T.
        produce_kv<true, SharedMemFillMode::kFillZero, KTraits>(v_smem, &v_smem_offset_w, &v_ptr,
                                                                v_stride_n, 0, chunk_size, tid);
        produce_kv_sf<true, KTraits>(&smem_storage, maybe_v_cache_sf, kv_abs_base, kv_head_idx,
                                     v_stride_n, v_stride_h, 0, chunk_size, warp_idx, lane_idx);
        cp_async::commit_group();
      }

#pragma unroll 1
      for (uint32_t iter = 0; iter < num_iterations; ++iter) {
        // Shared K/V serializes loads (no K/V prefetch overlap) -> drain fully.
        if constexpr (KTraits::USE_KV_SHARED_SMEM) {
          cp_async::wait_group<0>();
        } else {
          cp_async::wait_group<1>();
        }
        block.sync();

        uint32_t kv_idx_base =
            chunk_start + (iter * NUM_WARPS_KV + get_warp_idx_kv<KTraits>(tid.z)) * NUM_MMA_KV * 16;
        [[maybe_unused]] uint32_t kv_rope_base = kv_idx_base;

        if constexpr (KTraits::POS_ENCODING_MODE == PosEncodingMode::kRoPELlama) {
          IdType* k_rope_offset = nullptr;
          if constexpr (has_maybe_k_rope_offset_v<Params>) {
            k_rope_offset = params.maybe_k_rope_offset;
          }
          const uint32_t rope_offset = k_rope_offset == nullptr ? 0 : k_rope_offset[request_idx];
          kv_rope_base += rope_offset;
          if constexpr (!is_fp4_type_v<DTypeKV>) {
            k_smem_inplace_apply_rotary<KTraits>(rope_offset + chunk_start + iter * CTA_TILE_KV,
                                                 &k_smem, &k_smem_offset_r, rope_freq, tid);
            block.sync();
          }
        }

        // compute attention score
        if constexpr (KTraits::POS_ENCODING_MODE == PosEncodingMode::kRoPELlama &&
                      is_fp4_type_v<DTypeKV>) {
          compute_qk_fp4_rope<KTraits>(
              &qo_smem, &q_smem_offset_r, &k_smem, &k_smem_offset_r,
              smem_storage.k_sf_smem_ptr(get_warp_idx_kv<KTraits>(tid.z) * KTraits::NUM_MMA_KV *
                                         16 * KTraits::NUM_MMA_D_QK),
              lane_idx, kv_rope_base, rope_freq, s_frag);
        } else if constexpr (KTraits::USE_KV_REPACK) {
          // Dequantize FP8 K -> BF16 staging smem (shuffle-free), then read native 16-bit.
          repack_fp8_tile_to_bf16<KTraits, KTraits::HEAD_DIM_QK>(smem_storage.k_smem,
                                                                 smem_storage.kv_smem_repack_ptr(),
                                                                 warp_idx * WARP_SIZE + lane_idx);
          block.sync();
          compute_qk<KTraits, /*REPACK_BF16=*/true>(
              &qo_smem, &q_smem_offset_r, &k_smem_bf16, &k_smem_offset_r_bf16,
              smem_storage.k_sf_smem_ptr(get_warp_idx_kv<KTraits>(tid.z) * KTraits::NUM_MMA_KV *
                                         16 * KTraits::NUM_MMA_D_QK),
              lane_idx, s_frag);
        } else {
          compute_qk<KTraits>(
              &qo_smem, &q_smem_offset_r, &k_smem, &k_smem_offset_r,
              smem_storage.k_sf_smem_ptr(get_warp_idx_kv<KTraits>(tid.z) * KTraits::NUM_MMA_KV *
                                         16 * KTraits::NUM_MMA_D_QK),
              lane_idx, s_frag);
        }
        logits_transform<KTraits>(params, variant, /*batch_idx=*/request_idx, qo_packed_idx_base,
                                  kv_idx_base, qo_len, kv_len, chunk_end, group_size, s_frag, tid,
                                  kv_head_idx);

        // apply mask
        if (MASK_MODE == MaskMode::kCustom || (iter >= mask_iteration || iter < window_iteration)) {
          logits_mask<KTraits>(params, variant, /*batch_idx=*/request_idx, qo_packed_idx_base,
                               kv_idx_base, qo_len, kv_len, chunk_end, group_size, s_frag, tid,
                               kv_head_idx);
        }

        // compute m,d states in online softmax
        if constexpr (KTraits::USE_SOFTMAX_VO_SPLIT) {
          vosplit_softmax_store_p<KTraits>(variant, s_frag, m, d, o_scale, &smem_storage,
                                           get_warp_idx_kv<KTraits>(tid.z),
                                           get_warp_idx_q<KTraits>(tid.y), lane_idx);
        } else {
          update_mdo_states<KTraits>(variant, s_frag, o_frag, m, d);
        }

        block.sync();
        if constexpr (KTraits::USE_KV_SHARED_SMEM) {
          // Load V(iter) into k_smem (time-shared) now that Q.K^T is done.
          produce_kv<true, SharedMemFillMode::kFillZero, KTraits>(
              v_smem, &v_smem_offset_w, &v_ptr, v_stride_n, iter * CTA_TILE_KV, chunk_size, tid);
          cp_async::commit_group();
          cp_async::wait_group<0>();
        } else {
          produce_kv<false, SharedMemFillMode::kNoFill, KTraits>(
              k_smem, &k_smem_offset_w, &k_ptr, k_stride_n, (iter + 1) * CTA_TILE_KV, chunk_size,
              tid);
          produce_kv_sf<false, KTraits>(&smem_storage, maybe_k_cache_sf, kv_abs_base, kv_head_idx,
                                        k_stride_n, k_stride_h, (iter + 1) * CTA_TILE_KV,
                                        chunk_size, warp_idx, lane_idx);
          cp_async::commit_group();
          cp_async::wait_group<1>();
        }
        block.sync();

        // compute sfm*v
        if constexpr (KTraits::USE_SOFTMAX_VO_SPLIT) {
          const uint32_t warp_vo_base =
              get_warp_idx_kv<KTraits>(tid.z) * KTraits::NUM_MMA_D_VO_PER_WARP;
          vosplit_compute_pv<KTraits>(&smem_storage, o_frag, o_scale, warp_vo_base,
                                      get_warp_idx_q<KTraits>(tid.y), lane_idx);
        } else if constexpr (KTraits::USE_KV_REPACK) {
          // Dequantize FP8 V -> BF16 staging smem (shuffle-free), then read native 16-bit.
          repack_fp8_tile_to_bf16<KTraits, KTraits::HEAD_DIM_VO>(smem_storage.v_smem,
                                                                 smem_storage.kv_smem_repack_ptr(),
                                                                 warp_idx * WARP_SIZE + lane_idx);
          block.sync();
          compute_sfm_v<KTraits, /*REPACK_BF16=*/true>(
              &v_smem_bf16, &v_smem_offset_r_bf16,
              smem_storage.v_sf_smem_ptr(get_warp_idx_kv<KTraits>(tid.z) * KTraits::NUM_MMA_KV *
                                         16 * KTraits::NUM_MMA_D_VO),
              lane_idx, s_frag, o_frag, d, d_base);
        } else {
          compute_sfm_v<KTraits>(
              &v_smem, &v_smem_offset_r,
              smem_storage.v_sf_smem_ptr(get_warp_idx_kv<KTraits>(tid.z) * KTraits::NUM_MMA_KV *
                                         16 * KTraits::NUM_MMA_D_VO),
              lane_idx, s_frag, o_frag, d, d_base);
        }

        block.sync();
        if constexpr (KTraits::USE_KV_SHARED_SMEM) {
          // K(iter+1) goes into the shared buffer only after sfm*v consumed V(iter).
          produce_kv<false, SharedMemFillMode::kNoFill, KTraits>(
              k_smem, &k_smem_offset_w, &k_ptr, k_stride_n, (iter + 1) * CTA_TILE_KV, chunk_size,
              tid);
          cp_async::commit_group();
        } else {
          produce_kv<true, SharedMemFillMode::kFillZero, KTraits>(
              v_smem, &v_smem_offset_w, &v_ptr, v_stride_n, (iter + 1) * CTA_TILE_KV, chunk_size,
              tid);
          produce_kv_sf<true, KTraits>(&smem_storage, maybe_v_cache_sf, kv_abs_base, kv_head_idx,
                                       v_stride_n, v_stride_h, (iter + 1) * CTA_TILE_KV, chunk_size,
                                       warp_idx, lane_idx);
          cp_async::commit_group();
        }
      }
      cp_async::wait_group<0>();
      block.sync();

      finalize_m<KTraits>(variant, m);

      const uint32_t num_kv_chunks =
          ceil_div(min(kv_len_safe, window_left + CTA_TILE_Q), kv_chunk_size);
      if constexpr (KTraits::USE_SOFTMAX_VO_SPLIT) {
        vosplit_write_o<KTraits>(o_frag, d, o_ptr_base, qo_packed_idx_base, qo_len,
                                 partition_kv ? num_kv_chunks * o_stride_n : o_stride_n, o_stride_h,
                                 get_warp_idx_kv<KTraits>(tid.z) * KTraits::NUM_MMA_D_VO_PER_WARP,
                                 group_size, lane_idx);
      } else {
        // threadblock synchronization
        threadblock_sync_mdo_states<KTraits>(o_frag, &smem_storage, m, d, warp_idx, lane_idx, tid);

        // transform output
        transform_output<KTraits, Params>(params, variant, o_frag, m, d, /*batch_idx=*/request_idx,
                                          kv_tile_idx, qo_packed_idx_base, warp_idx, lane_idx,
                                          kv_head_idx, group_size);

        // write back (o_ptr_base offset to this VO tile's columns: d_base mma * 16 elems)
        write_o_reg_gmem<KTraits>(o_frag, &qo_smem, o_ptr_base + d_base * 16, qo_packed_idx_base,
                                  qo_len,
                                  /*o_stride_n=*/
                                  partition_kv ? num_kv_chunks * o_stride_n : o_stride_n,
                                  /*o_stride_h=*/o_stride_h, group_size, tid);
      }

      // write lse (identical across VO tiles; redundant rewrite on later tiles is harmless)
      if constexpr (AttentionVariant::use_softmax) {
        if (lse != nullptr) {
          if (get_warp_idx_kv<KTraits>(tid.z) == 0) {
#pragma unroll
            for (uint32_t mma_q = 0; mma_q < NUM_MMA_Q; ++mma_q) {
#pragma unroll
              for (uint32_t j = 0; j < 2; ++j) {
                uint32_t q, r;
                group_size.divmod(qo_packed_idx_base + lane_idx / 4 + j * 8 + mma_q * 16, q, r);
                const uint32_t qo_head_idx = kv_head_idx * group_size + r;
                const uint32_t qo_idx = q;
                if (qo_idx < qo_len) {
                  if (partition_kv) {
                    lse[(o_indptr[request_idx] + qo_idx * num_kv_chunks + kv_tile_idx) *
                            num_qo_heads +
                        qo_head_idx] = math::ptx_log2(d[mma_q][j]) + float(m[mma_q][j]);
                  } else {
                    lse[(o_indptr[request_idx] + qo_idx) * num_qo_heads + qo_head_idx] =
                        math::ptx_log2(d[mma_q][j]) + float(m[mma_q][j]);
                  }
                }
              }
            }
          }
        }
      }
      block.sync();
    }  // d_tile (split-D over VO) loop
#if (__CUDACC_VER_MAJOR__ >= 12 && defined(__CUDA_ARCH__) && (__CUDA_ARCH__ >= 900))
    asm volatile("griddepcontrol.launch_dependents;");
#endif
#if (__CUDA_ARCH__ < 800)
  }
#endif
}

// VO-split helpers (HEAD_DIM_VO >= 512)
template <typename KTraits>
__device__ __forceinline__ void vosplit_softmax_store_p(
    typename KTraits::AttentionVariant variant,
    typename KTraits::DTypeQKAccum (*s_frag)[KTraits::NUM_MMA_KV][8],
    typename KTraits::DTypeQKAccum (*m)[2], float (*d)[2], float (*o_scale)[2],
    typename KTraits::SharedStoragePaged* smem_storage, const uint32_t warp_kv_idx,
    const uint32_t warp_q_idx, const uint32_t lane_idx) {
  constexpr uint32_t NUM_MMA_Q = KTraits::NUM_MMA_Q;
  constexpr uint32_t NUM_MMA_KV = KTraits::NUM_MMA_KV;
  constexpr uint32_t NUM_WARPS_Q = KTraits::NUM_WARPS_Q;
  constexpr uint32_t NUM_WARPS_KV = KTraits::NUM_WARPS_KV;
  constexpr uint32_t CTA_TILE_KV = KTraits::CTA_TILE_KV;
  const uint32_t q_row_base = (warp_q_idx * NUM_MMA_Q) * 16;
  auto md_idx = [&](uint32_t w_kv, uint32_t mma_q, uint32_t j) {
    return (((w_kv * NUM_WARPS_Q + warp_q_idx) * NUM_MMA_Q + mma_q) * 2 + j) * 8 + lane_idx / 4;
  };
  const float sm_scale = variant.sm_scale_log2;
  float2* smem_md = smem_storage->vosplit_md_smem_ptr();  // [warp][mma_q][j][8]
  typename KTraits::DTypeQ* p_smem = smem_storage->p_smem_ptr();

  // (1) per-warp tile rowmax over this warp's KV chunk -> smem_md[warp].x
#pragma unroll
  for (uint32_t mma_q = 0; mma_q < NUM_MMA_Q; ++mma_q) {
#pragma unroll
    for (uint32_t j = 0; j < 2; ++j) {
      float mt = -math::inf;
#pragma unroll
      for (uint32_t mma_kv = 0; mma_kv < NUM_MMA_KV; ++mma_kv) {
        mt = max(mt, max(max(s_frag[mma_q][mma_kv][j * 2 + 0], s_frag[mma_q][mma_kv][j * 2 + 1]),
                         max(s_frag[mma_q][mma_kv][j * 2 + 4], s_frag[mma_q][mma_kv][j * 2 + 5])));
      }
      mt = max(mt, math::shfl_xor_sync(mt, 0x2));
      mt = max(mt, math::shfl_xor_sync(mt, 0x1));
      smem_md[md_idx(warp_kv_idx, mma_q, j)].x = mt;
    }
  }
  __syncthreads();

  // (2) cross-warp max -> global m_new; update running m/d and o_scale
#pragma unroll
  for (uint32_t mma_q = 0; mma_q < NUM_MMA_Q; ++mma_q) {
#pragma unroll
    for (uint32_t j = 0; j < 2; ++j) {
      float mt = -math::inf;
#pragma unroll
      for (uint32_t w = 0; w < NUM_WARPS_KV; ++w) {
        mt = max(mt, smem_md[md_idx(w, mma_q, j)].x);
      }
      float m_prev = float(m[mma_q][j]);
      float m_new = max(m_prev, mt);
      // Per-row clamp for fully masked rows (see update_mdo_states).
      const float m_new_scaled = max(m_new * sm_scale, -cuda::std::numeric_limits<float>::max());
      o_scale[mma_q][j] = math::ptx_exp2(m_prev * sm_scale - m_new_scaled);
      d[mma_q][j] *= o_scale[mma_q][j];
      m[mma_q][j] = typename KTraits::DTypeQKAccum(m_new);
    }
  }

  // (3) exp this warp's scores with the global m -> P, store to p_smem row-major,
  // and accumulate this warp's partial rowsum into smem_md[warp].y
  const uint32_t warp_kv_base = warp_kv_idx * NUM_MMA_KV * 16;
#pragma unroll
  for (uint32_t mma_q = 0; mma_q < NUM_MMA_Q; ++mma_q) {
#pragma unroll
    for (uint32_t j = 0; j < 2; ++j) {
      // Per-row clamp for fully masked rows (see update_mdo_states).
      const float m_new =
          max(float(m[mma_q][j]) * sm_scale, -cuda::std::numeric_limits<float>::max());
      float dsum = 0.f;
#pragma unroll
      for (uint32_t mma_kv = 0; mma_kv < NUM_MMA_KV; ++mma_kv) {
#pragma unroll
        for (uint32_t k = 0; k < 2; ++k) {  // reg positions j*2+{0,1} and j*2+{4,5}
          float p0 = math::ptx_exp2(s_frag[mma_q][mma_kv][j * 2 + k] * sm_scale - m_new);
          float p1 = math::ptx_exp2(s_frag[mma_q][mma_kv][j * 2 + 4 + k] * sm_scale - m_new);
          dsum += p0 + p1;
          const uint32_t row = q_row_base + mma_q * 16 + lane_idx / 4 + 8 * j;
          const uint32_t col0 = mma_kv * 16 + 2 * (lane_idx % 4) + k;
          const uint32_t col1 = col0 + 8;
          p_smem[row * CTA_TILE_KV + warp_kv_base + col0] = typename KTraits::DTypeQ(p0);
          p_smem[row * CTA_TILE_KV + warp_kv_base + col1] = typename KTraits::DTypeQ(p1);
        }
      }
      dsum += math::shfl_xor_sync(dsum, 0x2);
      dsum += math::shfl_xor_sync(dsum, 0x1);
      smem_md[md_idx(warp_kv_idx, mma_q, j)].y = dsum;
    }
  }
  __syncthreads();

  // (4) cross-warp rowsum -> global d
#pragma unroll
  for (uint32_t mma_q = 0; mma_q < NUM_MMA_Q; ++mma_q) {
#pragma unroll
    for (uint32_t j = 0; j < 2; ++j) {
      float dt = 0.f;
#pragma unroll
      for (uint32_t w = 0; w < NUM_WARPS_KV; ++w) {
        dt += smem_md[md_idx(w, mma_q, j)].y;
      }
      d[mma_q][j] += dt;
    }
  }
}

template <typename KTraits>
__device__ __forceinline__ void vosplit_compute_pv(
    typename KTraits::SharedStoragePaged* smem_storage,
    float (*o_frag)[KTraits::NUM_MMA_D_VO_PER_WARP][8], float (*o_scale)[2],
    const uint32_t warp_vo_base, const uint32_t warp_q_idx, const uint32_t lane_idx) {
  using DTypeQ = typename KTraits::DTypeQ;
  using DTypeKV = typename KTraits::DTypeKV;
  constexpr uint32_t NUM_MMA_Q = KTraits::NUM_MMA_Q;
  const uint32_t q_row_base = (warp_q_idx * NUM_MMA_Q) * 16;
  constexpr uint32_t NUM_MMA_D_VO_PER_WARP = KTraits::NUM_MMA_D_VO_PER_WARP;
  constexpr uint32_t CTA_TILE_KV = KTraits::CTA_TILE_KV;
  constexpr uint32_t NUM_MMA_KV_FULL = CTA_TILE_KV / 16;
  constexpr uint32_t UPCAST_STRIDE_V = KTraits::UPCAST_STRIDE_V;
  constexpr uint32_t VO_COLS_PER_TILE = 16 / upcast_size<DTypeKV>();
  constexpr bool IS_FP4 = is_fp4_type_v<DTypeKV>;
  DTypeQ* p_smem = smem_storage->p_smem_ptr();
  smem_t<KTraits::SWIZZLE_MODE_KV> v_smem(KTraits::USE_KV_SHARED_SMEM ? smem_storage->k_smem
                                                                      : smem_storage->v_smem);

#pragma unroll
  for (uint32_t mma_q = 0; mma_q < NUM_MMA_Q; ++mma_q) {
#pragma unroll
    for (uint32_t j = 0; j < 2; ++j) {
#pragma unroll
      for (uint32_t local_d = 0; local_d < NUM_MMA_D_VO_PER_WARP; ++local_d) {
        o_frag[mma_q][local_d][j * 2 + 0] *= o_scale[mma_q][j];
        o_frag[mma_q][local_d][j * 2 + 1] *= o_scale[mma_q][j];
        o_frag[mma_q][local_d][j * 2 + 4] *= o_scale[mma_q][j];
        o_frag[mma_q][local_d][j * 2 + 5] *= o_scale[mma_q][j];
      }
    }
  }

#pragma unroll
  for (uint32_t mma_kv = 0; mma_kv < NUM_MMA_KV_FULL; ++mma_kv) {
    // load P[mma_q tile, mma_kv tile] from p_smem into the m16k16 A-fragment
    uint32_t a_frag[NUM_MMA_Q][4];
#pragma unroll
    for (uint32_t mma_q = 0; mma_q < NUM_MMA_Q; ++mma_q) {
      const uint32_t r0 = q_row_base + mma_q * 16 + lane_idx / 4;
      const uint32_t c0 = mma_kv * 16 + (lane_idx % 4) * 2;
      a_frag[mma_q][0] = *(uint32_t*)&p_smem[r0 * CTA_TILE_KV + c0];
      a_frag[mma_q][1] = *(uint32_t*)&p_smem[(r0 + 8) * CTA_TILE_KV + c0];
      a_frag[mma_q][2] = *(uint32_t*)&p_smem[r0 * CTA_TILE_KV + c0 + 8];
      a_frag[mma_q][3] = *(uint32_t*)&p_smem[(r0 + 8) * CTA_TILE_KV + c0 + 8];
    }
#if defined(__CUDA_ARCH__) && (__CUDA_ARCH__ >= 800) && (__CUDA_ARCH__ < 900)
    constexpr bool USE_SM8X_VEC_V_SCALE = true;
#else
    constexpr bool USE_SM8X_VEC_V_SCALE = false;
#endif
    // SM120 measurements show this path can regress there; keep it to the SM8x path where
    // scalar V-scale loads were measured as the bottleneck.
    constexpr bool USE_VEC_V_SCALE =
        USE_SM8X_VEC_V_SCALE && IS_FP4 && NUM_MMA_D_VO_PER_WARP % 4 == 0;
    constexpr uint32_t LOCAL_D_GROUP = USE_VEC_V_SCALE ? 4 : 1;
    // Call sites pass warp_vo_base as a multiple of NUM_MMA_D_VO_PER_WARP,
    // so the vectorized V-scale path is 4-byte aligned whenever it is enabled.
#pragma unroll
    for (uint32_t local_d_base = 0; local_d_base < NUM_MMA_D_VO_PER_WARP;
         local_d_base += LOCAL_D_GROUP) {
      [[maybe_unused]] uint32_t sf_row0 = 0, sf_row1 = 0, sf_row2 = 0, sf_row3 = 0;
      if constexpr (USE_VEC_V_SCALE) {
        constexpr uint32_t SF_COLS_V = KTraits::NUM_MMA_D_VO;
        const uint32_t sf_base =
            (mma_kv * 16 + 2 * (lane_idx % 4)) * SF_COLS_V + warp_vo_base + local_d_base;
        sf_row0 = *(uint32_t*)&smem_storage->v_sf_smem_ptr()[sf_base];
        sf_row1 = *(uint32_t*)&smem_storage->v_sf_smem_ptr()[sf_base + SF_COLS_V];
        sf_row2 = *(uint32_t*)&smem_storage->v_sf_smem_ptr()[sf_base + 8 * SF_COLS_V];
        sf_row3 = *(uint32_t*)&smem_storage->v_sf_smem_ptr()[sf_base + 9 * SF_COLS_V];
      }
#pragma unroll
      for (uint32_t local_d_offset = 0; local_d_offset < LOCAL_D_GROUP; ++local_d_offset) {
        const uint32_t local_d = local_d_base + local_d_offset;
        const uint32_t global_d = warp_vo_base + local_d;
        uint32_t b_frag[4];
        if constexpr (sizeof(DTypeKV) == 1) {
          // In-loop one-byte V dequant (no staging). FP8 and NVFP4 both share a
          // 2-b128-column region for a pair of VO MMA tiles (global_d, global_d^1),
          // read via the left/right ldmatrix halves; mirrors compute_sfm_v's V path.
          uint32_t b_frag_quant[2];
          const uint32_t voff = v_smem.template get_permuted_offset<UPCAST_STRIDE_V>(
              mma_kv * 16 + lane_idx % 16, lane_idx / 16 + 2 * (global_d / 2));
          if (global_d % 2 == 0) {
            v_smem.ldmatrix_m8n8x4_trans_left_half(voff, b_frag_quant);
          } else {
            v_smem.ldmatrix_m8n8x4_trans_right_half(voff, b_frag_quant);
          }
          if constexpr (IS_FP4) {
            b_frag_quant[0] = frag_layout_swizzle_16b_to_4b_trans(b_frag_quant[0]);
            b_frag_quant[1] = frag_layout_swizzle_16b_to_4b_trans(b_frag_quant[1]);
          } else {
            b_frag_quant[0] = frag_layout_swizzle_16b_to_8b_trans(b_frag_quant[0]);
            b_frag_quant[1] = frag_layout_swizzle_16b_to_8b_trans(b_frag_quant[1]);
          }
          vec_cast<DTypeQ, DTypeKV>::template cast<8>((DTypeQ*)b_frag, (DTypeKV*)b_frag_quant);
          swap(b_frag[1], b_frag[2]);
          if constexpr (IS_FP4) {
            using packed2 = std::conditional_t<std::is_same_v<DTypeQ, half>, half2, __nv_bfloat162>;
            if constexpr (USE_VEC_V_SCALE) {
              const uint32_t sf_shift = local_d_offset * 8;
              uint32_t sf_pack =
                  ((sf_row0 >> sf_shift) & 0xffU) | (((sf_row1 >> sf_shift) & 0xffU) << 8) |
                  (((sf_row2 >> sf_shift) & 0xffU) << 16) | (((sf_row3 >> sf_shift) & 0xffU) << 24);
              uint2 scale_pair;
              fast_dequant_f8f16x4<__nv_fp8_e4m3, DTypeQ>(&sf_pack, &scale_pair);
              packed2 scale_lo = *(packed2*)&scale_pair.x;
              packed2 scale_hi = *(packed2*)&scale_pair.y;
              *(packed2*)&b_frag[0] = __hmul2(*(packed2*)&b_frag[0], scale_lo);
              *(packed2*)&b_frag[1] = __hmul2(*(packed2*)&b_frag[1], scale_hi);
              *(packed2*)&b_frag[2] = __hmul2(*(packed2*)&b_frag[2], scale_lo);
              *(packed2*)&b_frag[3] = __hmul2(*(packed2*)&b_frag[3], scale_hi);
            } else {
              constexpr uint32_t SF_COLS_V = KTraits::NUM_MMA_D_VO;
              const uint32_t sf_base = (mma_kv * 16 + 2 * (lane_idx % 4)) * SF_COLS_V + global_d;
              __nv_fp8_e4m3 sf0_fp8, sf1_fp8, sf2_fp8, sf3_fp8;
              sf0_fp8.__x = smem_storage->v_sf_smem_ptr()[sf_base];
              sf1_fp8.__x = smem_storage->v_sf_smem_ptr()[sf_base + SF_COLS_V];
              sf2_fp8.__x = smem_storage->v_sf_smem_ptr()[sf_base + 8 * SF_COLS_V];
              sf3_fp8.__x = smem_storage->v_sf_smem_ptr()[sf_base + 9 * SF_COLS_V];
              packed2 scale_lo{static_cast<DTypeQ>(sf0_fp8), static_cast<DTypeQ>(sf1_fp8)};
              packed2 scale_hi{static_cast<DTypeQ>(sf2_fp8), static_cast<DTypeQ>(sf3_fp8)};
              *(packed2*)&b_frag[0] = __hmul2(*(packed2*)&b_frag[0], scale_lo);
              *(packed2*)&b_frag[1] = __hmul2(*(packed2*)&b_frag[1], scale_hi);
              *(packed2*)&b_frag[2] = __hmul2(*(packed2*)&b_frag[2], scale_lo);
              *(packed2*)&b_frag[3] = __hmul2(*(packed2*)&b_frag[3], scale_hi);
            }
          }
        } else {
          const uint32_t voff = v_smem.template get_permuted_offset<UPCAST_STRIDE_V>(
              mma_kv * 16 + lane_idx % 16, global_d * VO_COLS_PER_TILE + lane_idx / 16);
          v_smem.ldmatrix_m8n8x4_trans(voff, b_frag);
        }
#pragma unroll
        for (uint32_t mma_q = 0; mma_q < NUM_MMA_Q; ++mma_q) {
          mma::mma_sync_m16n16k16_row_col_f16f16f32<DTypeQ>(o_frag[mma_q][local_d], a_frag[mma_q],
                                                            b_frag);
        }
      }
    }
  }
}

template <typename KTraits>
__device__ __forceinline__ void vosplit_write_o(
    float (*o_frag)[KTraits::NUM_MMA_D_VO_PER_WARP][8], float (*d)[2],
    typename KTraits::DTypeO* o_ptr_base, const uint32_t o_packed_idx_base,
    const uint32_t qo_upper_bound, const uint32_t o_stride_n, const uint32_t o_stride_h,
    const uint32_t warp_vo_base, const uint_fastdiv group_size, const uint32_t lane_idx) {
  using DTypeO = typename KTraits::DTypeO;
  constexpr uint32_t NUM_MMA_Q = KTraits::NUM_MMA_Q;
  constexpr uint32_t NUM_MMA_D_VO_PER_WARP = KTraits::NUM_MMA_D_VO_PER_WARP;
  // normalize by 1/d (o_frag holds unnormalized sum_kv P*V)
#pragma unroll
  for (uint32_t mma_q = 0; mma_q < NUM_MMA_Q; ++mma_q) {
#pragma unroll
    for (uint32_t j = 0; j < 2; ++j) {
      float r = (d[mma_q][j] > 0.f) ? math::ptx_rcp(d[mma_q][j]) : 0.f;
#pragma unroll
      for (uint32_t local_d = 0; local_d < NUM_MMA_D_VO_PER_WARP; ++local_d) {
        o_frag[mma_q][local_d][j * 2 + 0] *= r;
        o_frag[mma_q][local_d][j * 2 + 1] *= r;
        o_frag[mma_q][local_d][j * 2 + 4] *= r;
        o_frag[mma_q][local_d][j * 2 + 5] *= r;
      }
    }
  }
  // o_frag element (mma_q,local_d,reg) -> row = mma_q*16 + lane/4 + 8*((reg%4)/2),
  // col = (warp_vo_base+local_d)*16 + 2*(lane%4) + 8*(reg/4) + reg%2 (C-fragment).
#pragma unroll
  for (uint32_t mma_q = 0; mma_q < NUM_MMA_Q; ++mma_q) {
#pragma unroll
    for (uint32_t j = 0; j < 2; ++j) {
      uint32_t q, r;
      group_size.divmod(o_packed_idx_base + lane_idx / 4 + j * 8 + mma_q * 16, q, r);
      if (q < qo_upper_bound) {
        DTypeO* o_row = o_ptr_base + q * o_stride_n + r * o_stride_h;
#pragma unroll
        for (uint32_t local_d = 0; local_d < NUM_MMA_D_VO_PER_WARP; ++local_d) {
          const uint32_t col_base = (warp_vo_base + local_d) * 16 + 2 * (lane_idx % 4);
#pragma unroll
          for (uint32_t k = 0; k < 2; ++k) {
            o_row[col_base + k] = DTypeO(o_frag[mma_q][local_d][j * 2 + k]);
            o_row[col_base + 8 + k] = DTypeO(o_frag[mma_q][local_d][j * 2 + 4 + k]);
          }
        }
      }
    }
  }
}

template <typename KTraits, typename Params, typename SmemStorage>
__device__ __forceinline__ void BatchPrefillWithPagedKVCacheDevice(
    const Params params, SmemStorage& smem_storage, const dim3 tid = threadIdx,
    const uint32_t bx = blockIdx.x, const uint32_t kv_head_idx = blockIdx.z,
    const uint32_t num_kv_heads = gridDim.z) {
  using DTypeQ = typename Params::DTypeQ;
#if (__CUDA_ARCH__ < 800)
  if constexpr (std::is_same_v<DTypeQ, nv_bfloat16>) {
    FLASHINFER_RUNTIME_ASSERT("Prefill kernels do not support bf16 on sm75.");
  } else {
#endif
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
    [[maybe_unused]] constexpr uint32_t KV_THR_LAYOUT_ROW = KTraits::KV_THR_LAYOUT_ROW;
    [[maybe_unused]] constexpr uint32_t KV_THR_LAYOUT_COL = KTraits::KV_THR_LAYOUT_COL;
    [[maybe_unused]] constexpr MaskMode MASK_MODE = KTraits::MASK_MODE;

    IdType* request_indices = params.request_indices;
    IdType* qo_tile_indices = params.qo_tile_indices;
    IdType* kv_tile_indices = params.kv_tile_indices;
    DTypeQ* q = params.q;
    IdType* q_indptr = params.q_indptr;
    IdType* o_indptr = params.o_indptr;
    DTypeO* o = params.o;
    float* lse = params.lse;
    bool* block_valid_mask = params.block_valid_mask;
    const paged_kv_t<DTypeKV, IdType>& paged_kv = params.paged_kv;
    const bool partition_kv = params.partition_kv;
    const uint_fastdiv& group_size = params.group_size;

    uint32_t* maybe_prefix_len_ptr = nullptr;
    if constexpr (has_maybe_prefix_len_ptr_v<Params>) {
      maybe_prefix_len_ptr = params.maybe_prefix_len_ptr;
    }
    uint16_t* maybe_token_pos_in_items_ptr = nullptr;
    if constexpr (has_maybe_token_pos_in_items_ptr_v<Params>) {
      maybe_token_pos_in_items_ptr = params.maybe_token_pos_in_items_ptr;
    }
    uint32_t token_pos_in_items_len = 0;
    if constexpr (has_token_pos_in_items_len_v<Params>) {
      token_pos_in_items_len = params.token_pos_in_items_len;
    }
    uint16_t* maybe_max_item_len_ptr = nullptr;
    if constexpr (has_maybe_max_item_len_ptr_v<Params>) {
      maybe_max_item_len_ptr = params.maybe_max_item_len_ptr;
    }
    uint8_t* maybe_k_cache_sf = nullptr;
    if constexpr (has_maybe_k_cache_sf_v<Params>) {
      maybe_k_cache_sf = params.maybe_k_cache_sf;
    }
    uint8_t* maybe_v_cache_sf = nullptr;
    if constexpr (has_maybe_v_cache_sf_v<Params>) {
      maybe_v_cache_sf = params.maybe_v_cache_sf;
    }
    const uint32_t k_sf_stride_page = params.k_sf_stride_page;
    const uint32_t k_sf_stride_n = params.k_sf_stride_n;
    const uint32_t k_sf_stride_h = params.k_sf_stride_h;
    const uint32_t v_sf_stride_page = params.v_sf_stride_page;
    const uint32_t v_sf_stride_n = params.v_sf_stride_n;
    const uint32_t v_sf_stride_h = params.v_sf_stride_h;

    static_assert(sizeof(DTypeQ) == 2);
    auto block = cg::this_thread_block();
    const uint32_t kv_chunk_size = *(params.kv_chunk_size_ptr);

    const uint32_t lane_idx = tid.x, warp_idx = get_warp_idx<KTraits>(tid.y, tid.z);
    if (block_valid_mask && !block_valid_mask[bx]) {
      return;
    }
    const uint32_t num_qo_heads = num_kv_heads * group_size;

    const uint32_t request_idx = request_indices[bx], qo_tile_idx = qo_tile_indices[bx],
                   kv_tile_idx = kv_tile_indices[bx];
    auto smem = reinterpret_cast<uint8_t*>(&smem_storage);
    AttentionVariant variant(params, /*batch_idx=*/request_idx, smem);
    const uint32_t qo_len = variant.qo_len, kv_len = variant.kv_len,
                   window_left = variant.window_left;
    const uint32_t kv_len_safe = kv_len > 0 ? kv_len : 1;
    const uint32_t qo_upper_bound =
        min(qo_len, ceil_div((qo_tile_idx + 1) * CTA_TILE_Q, group_size));

    const uint32_t kv_start_idx = sub_if_greater_or_zero(
        kv_len + (qo_tile_idx * CTA_TILE_Q) / group_size, qo_len + window_left);
    const uint32_t max_chunk_size = partition_kv ? kv_chunk_size : kv_len - kv_start_idx;
    const uint32_t chunk_start =
        partition_kv ? min(kv_tile_idx * max_chunk_size + kv_start_idx, kv_len) : kv_start_idx;
    const uint32_t chunk_end =
        partition_kv ? min((kv_tile_idx + 1) * max_chunk_size + kv_start_idx, kv_len) : kv_len;
    const uint32_t chunk_size = chunk_end - chunk_start;
    DTypeQKAccum s_frag[NUM_MMA_Q][NUM_MMA_KV][8];
    // o_frag width: split-D path holds one VO tile (NUM_MMA_D_VO_TILE); VO-split
    // path holds this warp's VO slice (NUM_MMA_D_VO_PER_WARP). Both <= 16 -> 64-128
    // regs (no 256-reg wall).
    constexpr uint32_t O_FRAG_D =
        KTraits::USE_VO_SPLIT ? KTraits::NUM_MMA_D_VO_PER_WARP : KTraits::NUM_MMA_D_VO_TILE;
    alignas(16) float o_frag[NUM_MMA_Q][O_FRAG_D][8];
    [[maybe_unused]] float o_scale[NUM_MMA_Q][2];  // VO-split online rescale (per KV tile)
    DTypeQKAccum m[NUM_MMA_Q][2];
    float d[NUM_MMA_Q][2];
    constexpr uint32_t LOCAL_ROPE_FREQ_ROWS =
        (KTraits::POS_ENCODING_MODE == PosEncodingMode::kRoPELlama &&
         !KTraits::USE_SHARED_ROPE_FREQ)
            ? (NUM_MMA_D_QK / 2)
            : 1;
    alignas(16) float local_rope_freq[LOCAL_ROPE_FREQ_ROWS][4];
    float(*rope_freq)[4] = local_rope_freq;

    if constexpr (KTraits::POS_ENCODING_MODE == PosEncodingMode::kRoPELlama) {
      const float rope_rcp_scale = params.rope_rcp_scale;
      const float rope_rcp_theta = params.rope_rcp_theta;
      if constexpr (KTraits::USE_SHARED_ROPE_FREQ) {
        init_shared_rope_freq<KTraits>(smem_storage.rope_freq_smem, rope_rcp_scale, rope_rcp_theta,
                                       tid);
        rope_freq = smem_storage.rope_freq_smem[lane_idx % 4];
      } else {
        init_rope_freq<KTraits>(local_rope_freq, rope_rcp_scale, rope_rcp_theta, tid.x);
      }
    }

    const uint32_t qo_packed_idx_base =
        (qo_tile_idx * NUM_WARPS_Q + get_warp_idx_q<KTraits>(tid.y)) * NUM_MMA_Q * 16;
    const uint32_t q_stride_n = params.q_stride_n, q_stride_h = params.q_stride_h;
    smem_t<SWIZZLE_MODE_Q> qo_smem(smem_storage.q_smem);
    const uint32_t o_stride_n = num_qo_heads * HEAD_DIM_VO, o_stride_h = HEAD_DIM_VO;

    DTypeQ* q_ptr_base =
        q + q_indptr[request_idx] * q_stride_n + (kv_head_idx * group_size) * q_stride_h;
    DTypeO* o_ptr_base = partition_kv ? o + (o_indptr[request_idx] + kv_tile_idx) * o_stride_n +
                                            (kv_head_idx * group_size) * o_stride_h
                                      : o + o_indptr[request_idx] * o_stride_n +
                                            (kv_head_idx * group_size) * o_stride_h;

#if (__CUDACC_VER_MAJOR__ >= 12 && defined(__CUDA_ARCH__) && (__CUDA_ARCH__ >= 900))
    asm volatile("griddepcontrol.wait;");
#endif

    // Split-D over the VO dimension
#pragma unroll 1
    for (uint32_t d_tile = 0; d_tile < (KTraits::USE_VO_SPLIT ? 1u : KTraits::NUM_D_VO_TILES);
         ++d_tile) {
      [[maybe_unused]] const uint32_t d_base = d_tile * KTraits::NUM_MMA_D_VO_TILE;
      if constexpr (KTraits::USE_VO_SPLIT) {
        // VO-split O accumulator init (this warp's VO slice); d starts at 0
        // (running denominator via cross-warp rowsum), m at -inf.
#pragma unroll
        for (uint32_t mma_q = 0; mma_q < NUM_MMA_Q; ++mma_q) {
#pragma unroll
          for (uint32_t local_d = 0; local_d < KTraits::NUM_MMA_D_VO_PER_WARP; ++local_d) {
#pragma unroll
            for (uint32_t reg = 0; reg < 8; ++reg) o_frag[mma_q][local_d][reg] = 0.f;
          }
#pragma unroll
          for (uint32_t j = 0; j < 2; ++j) {
            m[mma_q][j] = DTypeQKAccum(-math::inf);
            d[mma_q][j] = 0.f;
          }
        }
      } else {
        init_states<KTraits>(variant, o_frag, m, d);
      }
      uint32_t q_smem_offset_r = qo_smem.get_permuted_offset<UPCAST_STRIDE_Q>(
          get_warp_idx_q<KTraits>(tid.y) * NUM_MMA_Q * 16 + lane_idx % 16, lane_idx / 16);

      load_q_global_smem<KTraits>(qo_packed_idx_base, qo_upper_bound, q_ptr_base, q_stride_n,
                                  q_stride_h, group_size, &qo_smem, tid);

      cp_async::commit_group();

      if constexpr (KTraits::POS_ENCODING_MODE == PosEncodingMode::kRoPELlama) {
        cp_async::wait_group<0>();
        block.sync();
        IdType* q_rope_offset = nullptr;
        if constexpr (has_maybe_q_rope_offset_v<Params>) {
          q_rope_offset = params.maybe_q_rope_offset;
        }
        if (q_rope_offset == nullptr) {
          q_smem_inplace_apply_rotary<KTraits>(qo_packed_idx_base, qo_len, kv_len, group_size,
                                               &qo_smem, &q_smem_offset_r, rope_freq, tid);
        } else {
          q_smem_inplace_apply_rotary_with_pos<KTraits>(
              qo_packed_idx_base, q_rope_offset + q_indptr[request_idx], &qo_smem, group_size,
              &q_smem_offset_r, rope_freq, tid);
        }
        block.sync();
      }

      // K/V-shared path: V is loaded into k_smem (time-shared); v_smem is a [1] stub.
      smem_t<SWIZZLE_MODE_KV> k_smem(smem_storage.k_smem),
          v_smem(KTraits::USE_KV_SHARED_SMEM ? smem_storage.k_smem : smem_storage.v_smem);
      constexpr uint32_t NUM_PAGED_KV_OFFSETS = NUM_MMA_KV * KV_THR_LAYOUT_COL / 2 / NUM_WARPS_Q;
      // Separate K/V page offsets: the K and V pools may carry different strides
      // (e.g. NVFP4 VO-split caches where head_dim_qk != head_dim_vo). The K/V-shared
      // smem path computes offsets on the fly, so the arrays collapse to [1] stubs there.
      [[maybe_unused]] size_t
          thr_local_kv_offset_k[KTraits::USE_KV_SHARED_SMEM ? 1 : NUM_PAGED_KV_OFFSETS];
      [[maybe_unused]] size_t
          thr_local_kv_offset_v[KTraits::USE_KV_SHARED_SMEM ? 1 : NUM_PAGED_KV_OFFSETS];

      uint32_t k_smem_offset_r = k_smem.template get_permuted_offset<UPCAST_STRIDE_K>(
                   get_warp_idx_kv<KTraits>(tid.z) * NUM_MMA_KV * 16 + 8 * (lane_idx / 16) +
                       lane_idx % 8,
                   (lane_idx % 16) / 8),
               k_smem_offset_w = k_smem.template get_permuted_offset<UPCAST_STRIDE_K>(
                   warp_idx * KV_THR_LAYOUT_ROW + lane_idx / KV_THR_LAYOUT_COL,
                   lane_idx % KV_THR_LAYOUT_COL);
      [[maybe_unused]] uint32_t v_smem_offset_r = 0;
      uint32_t v_smem_offset_w = v_smem.template get_permuted_offset<UPCAST_STRIDE_V>(
          warp_idx * KV_THR_LAYOUT_ROW + lane_idx / KV_THR_LAYOUT_COL,
          lane_idx % KV_THR_LAYOUT_COL);
      if constexpr (!KTraits::USE_VO_SPLIT && !KTraits::USE_KV_REPACK) {
        v_smem_offset_r = v_smem.template get_permuted_offset<UPCAST_STRIDE_V>(
            get_warp_idx_kv<KTraits>(tid.z) * NUM_MMA_KV * 16 + lane_idx % 16, lane_idx / 16);
      }
      if constexpr (KTraits::USE_KV_SHARED_SMEM) {
        static_assert(UPCAST_STRIDE_K == UPCAST_STRIDE_V);
      }

      // FP8 repack path
      smem_t<SWIZZLE_MODE_KV> k_smem_bf16(smem_storage.kv_smem_repack_ptr()),
          v_smem_bf16(smem_storage.kv_smem_repack_ptr());
      uint32_t k_smem_offset_r_bf16 = 0, v_smem_offset_r_bf16 = 0;
      if constexpr (KTraits::USE_KV_REPACK) {
        k_smem_offset_r_bf16 = k_smem_bf16.template get_permuted_offset<KTraits::REPACK_STRIDE_QK>(
            get_warp_idx_kv<KTraits>(tid.z) * NUM_MMA_KV * 16 + 8 * (lane_idx / 16) + lane_idx % 8,
            (lane_idx % 16) / 8);
        v_smem_offset_r_bf16 = v_smem_bf16.template get_permuted_offset<KTraits::REPACK_STRIDE_VO>(
            get_warp_idx_kv<KTraits>(tid.z) * NUM_MMA_KV * 16 + lane_idx % 16, lane_idx / 16);
      }

      const IdType last_indptr = paged_kv.indptr[paged_kv.batch_size];

      uint32_t packed_page_iter_base =
          paged_kv.indptr[request_idx] * paged_kv.page_size + chunk_start;
      if constexpr (KTraits::USE_KV_SHARED_SMEM) {
        page_produce_kv_on_the_fly<false, KTraits>(&smem_storage, &k_smem_offset_w, paged_kv.k_data,
                                                   paged_kv, packed_page_iter_base, last_indptr,
                                                   kv_head_idx, 0, chunk_size, warp_idx, lane_idx);
      } else {
#pragma unroll
        for (uint32_t i = 0;
             i < NUM_MMA_KV * (SWIZZLE_MODE_KV == SwizzleMode::k128B ? 4 : 2) / NUM_WARPS_Q; ++i) {
          uint32_t page_iter, entry_idx;
          paged_kv.page_size.divmod(packed_page_iter_base + warp_idx * KV_THR_LAYOUT_ROW +
                                        lane_idx / KV_THR_LAYOUT_COL +
                                        KV_THR_LAYOUT_ROW * NUM_WARPS_Q * NUM_WARPS_KV * i,
                                    page_iter, entry_idx);
          // FP4: GMEM is packed (2 FP4/byte), so the column byte offset is halved relative to fp8
          constexpr uint32_t fp4_pack_factor = is_fp4_type_v<DTypeKV> ? 2 : 1;
          const uint32_t feat_idx =
              (lane_idx % KV_THR_LAYOUT_COL) * upcast_size<DTypeKV>() / fp4_pack_factor;
          thr_local_kv_offset_k[i] = paged_kv.protective_get_k_offset(
              page_iter, kv_head_idx, entry_idx, feat_idx, last_indptr);
          thr_local_kv_offset_v[i] = paged_kv.protective_get_v_offset(
              page_iter, kv_head_idx, entry_idx, feat_idx, last_indptr);
        }
        page_produce_kv<false, KTraits>(&smem_storage, &k_smem_offset_w, paged_kv.k_data, 0,
                                        thr_local_kv_offset_k, chunk_size, warp_idx, lane_idx);
      }
      page_produce_kv_sf<false, KTraits>(
          &smem_storage, maybe_k_cache_sf, packed_page_iter_base,
          last_indptr * (uint32_t)paged_kv.page_size, kv_head_idx, k_sf_stride_page, k_sf_stride_h,
          k_sf_stride_n, paged_kv.page_size, paged_kv.indices, 0, chunk_size, warp_idx, lane_idx);
      cp_async::commit_group();
      // Shared K/V loads V(0) inside iter 0 after Q.K^T; preloading it would clobber K(0).
      if constexpr (!KTraits::USE_KV_SHARED_SMEM) {
        page_produce_kv<true, KTraits>(&smem_storage, &v_smem_offset_w, paged_kv.v_data, 0,
                                       thr_local_kv_offset_v, chunk_size, warp_idx, lane_idx);
        page_produce_kv_sf<true, KTraits>(&smem_storage, maybe_v_cache_sf, packed_page_iter_base,
                                          last_indptr * (uint32_t)paged_kv.page_size, kv_head_idx,
                                          v_sf_stride_page, v_sf_stride_h, v_sf_stride_n,
                                          paged_kv.page_size, paged_kv.indices, 0, chunk_size,
                                          warp_idx, lane_idx);
        cp_async::commit_group();
      }

      uint32_t num_iterations_prefix;
      uint32_t num_iterations_mask;
      uint32_t num_iterations = 0;

      if constexpr (MASK_MODE != MaskMode::kMultiItemScoring) {
        num_iterations = ceil_div(
            (MASK_MODE == MaskMode::kCausal
                 ? min(chunk_size,
                       sub_if_greater_or_zero(
                           kv_len - qo_len + ceil_div(((qo_tile_idx + 1) * CTA_TILE_Q), group_size),
                           chunk_start))
                 : chunk_size),
            CTA_TILE_KV);
      } else if constexpr (MASK_MODE == MaskMode::kMultiItemScoring) {
        num_iterations_prefix = ceil_div(
            min(min(chunk_size,
                    sub_if_greater_or_zero(
                        kv_len - qo_len + ceil_div(((qo_tile_idx + 1) * CTA_TILE_Q), group_size),
                        chunk_start)),
                sub_if_greater_or_zero(__ldg(maybe_prefix_len_ptr + request_idx), chunk_start)),
            CTA_TILE_KV);
        num_iterations_mask =
            max(min(chunk_size,
                    sub_if_greater_or_zero(
                        sub_if_greater_or_zero(
                            kv_len - qo_len + ceil_div((qo_tile_idx * CTA_TILE_Q), group_size),
                            __ldg(maybe_max_item_len_ptr + request_idx)),
                        chunk_start)) /
                    (CTA_TILE_KV),
                num_iterations_prefix);

        num_iterations = max(
            num_iterations_mask,
            ceil_div(min(chunk_size, sub_if_greater_or_zero(
                                         kv_len - qo_len +
                                             ceil_div(((qo_tile_idx + 1) * CTA_TILE_Q), group_size),
                                         chunk_start)),
                     CTA_TILE_KV));
      }

      const uint32_t window_iteration = ceil_div(
          sub_if_greater_or_zero(kv_len + ceil_div((qo_tile_idx + 1) * CTA_TILE_Q, group_size),
                                 qo_len + window_left + chunk_start),
          CTA_TILE_KV);

      const uint32_t mask_iteration =
          (MASK_MODE == MaskMode::kCausal || MASK_MODE == MaskMode::kMultiItemScoring
               ? min(chunk_size,
                     sub_if_greater_or_zero(
                         kv_len + ceil_div((qo_tile_idx * CTA_TILE_Q), group_size) - qo_len,
                         chunk_start))
               : chunk_size) /
          CTA_TILE_KV;

#pragma unroll 1
      for (uint32_t iter = 0; iter < num_iterations;
           iter = (MASK_MODE == MaskMode::kMultiItemScoring)
                      ? ((iter + 1 == num_iterations_prefix) ? num_iterations_mask : (iter + 1))
                      : (iter + 1)) {
        const uint32_t prefetch_skip_step =
            (MASK_MODE == MaskMode::kMultiItemScoring)
                ? ((iter + 1 == num_iterations_prefix)
                       ? (num_iterations_mask - num_iterations_prefix)
                       : 0)
                : 0;
        const uint32_t next_packed_page_iter_base =
            packed_page_iter_base + (1 + prefetch_skip_step) * CTA_TILE_KV;
        if constexpr (!KTraits::USE_KV_SHARED_SMEM) {
          packed_page_iter_base = next_packed_page_iter_base;
#pragma unroll
          for (uint32_t i = 0;
               i < NUM_MMA_KV * (SWIZZLE_MODE_KV == SwizzleMode::k128B ? 4 : 2) / NUM_WARPS_Q;
               ++i) {
            uint32_t page_iter, entry_idx;
            paged_kv.page_size.divmod(packed_page_iter_base + warp_idx * KV_THR_LAYOUT_ROW +
                                          lane_idx / KV_THR_LAYOUT_COL +
                                          KV_THR_LAYOUT_ROW * NUM_WARPS_Q * NUM_WARPS_KV * i,
                                      page_iter, entry_idx);
            // FP4: GMEM is packed (2 FP4/byte), so the column byte offset is halved relative to fp8
            constexpr uint32_t fp4_pack_factor = is_fp4_type_v<DTypeKV> ? 2 : 1;
            const uint32_t feat_idx =
                (lane_idx % KV_THR_LAYOUT_COL) * upcast_size<DTypeKV>() / fp4_pack_factor;
            thr_local_kv_offset_k[i] = paged_kv.protective_get_k_offset(
                page_iter, kv_head_idx, entry_idx, feat_idx, last_indptr);
            thr_local_kv_offset_v[i] = paged_kv.protective_get_v_offset(
                page_iter, kv_head_idx, entry_idx, feat_idx, last_indptr);
          }
        }
        // Shared K/V serializes loads (no K/V prefetch overlap) -> drain fully.
        if constexpr (KTraits::USE_KV_SHARED_SMEM) {
          cp_async::wait_group<0>();
        } else {
          cp_async::wait_group<1>();
        }
        block.sync();

        uint32_t kv_idx_base =
            chunk_start + (iter * NUM_WARPS_KV + get_warp_idx_kv<KTraits>(tid.z)) * NUM_MMA_KV * 16;
        [[maybe_unused]] uint32_t kv_rope_base =
            kv_idx_base +
            (paged_kv.rope_pos_offset == nullptr ? 0 : paged_kv.rope_pos_offset[request_idx]);

        if constexpr (KTraits::POS_ENCODING_MODE == PosEncodingMode::kRoPELlama) {
          if constexpr (!is_fp4_type_v<DTypeKV>) {
            k_smem_inplace_apply_rotary<KTraits>(
                (paged_kv.rope_pos_offset == nullptr ? 0 : paged_kv.rope_pos_offset[request_idx]) +
                    chunk_start + iter * CTA_TILE_KV,
                &k_smem, &k_smem_offset_r, rope_freq, tid);
            block.sync();
          }
        }

        // compute attention score
        if constexpr (KTraits::POS_ENCODING_MODE == PosEncodingMode::kRoPELlama &&
                      is_fp4_type_v<DTypeKV>) {
          compute_qk_fp4_rope<KTraits>(
              &qo_smem, &q_smem_offset_r, &k_smem, &k_smem_offset_r,
              smem_storage.k_sf_smem_ptr(get_warp_idx_kv<KTraits>(tid.z) * KTraits::NUM_MMA_KV *
                                         16 * KTraits::NUM_MMA_D_QK),
              lane_idx, kv_rope_base, rope_freq, s_frag);
        } else if constexpr (KTraits::USE_KV_REPACK) {
          // Dequantize FP8 K -> BF16 staging smem (shuffle-free), then read native 16-bit.
          repack_fp8_tile_to_bf16<KTraits, KTraits::HEAD_DIM_QK>(smem_storage.k_smem,
                                                                 smem_storage.kv_smem_repack_ptr(),
                                                                 warp_idx * WARP_SIZE + lane_idx);
          block.sync();
          compute_qk<KTraits, /*REPACK_BF16=*/true>(
              &qo_smem, &q_smem_offset_r, &k_smem_bf16, &k_smem_offset_r_bf16,
              smem_storage.k_sf_smem_ptr(get_warp_idx_kv<KTraits>(tid.z) * KTraits::NUM_MMA_KV *
                                         16 * KTraits::NUM_MMA_D_QK),
              lane_idx, s_frag);
        } else {
          compute_qk<KTraits>(
              &qo_smem, &q_smem_offset_r, &k_smem, &k_smem_offset_r,
              smem_storage.k_sf_smem_ptr(get_warp_idx_kv<KTraits>(tid.z) * KTraits::NUM_MMA_KV *
                                         16 * KTraits::NUM_MMA_D_QK),
              lane_idx, s_frag);
        }
        logits_transform<KTraits>(params, variant, /*batch_idx=*/request_idx, qo_packed_idx_base,
                                  kv_idx_base, qo_len, kv_len, chunk_end, group_size, s_frag, tid,
                                  kv_head_idx);

        // apply mask
        if (MASK_MODE == MaskMode::kCustom) {
          logits_mask<KTraits>(params, variant, /*batch_idx=*/request_idx, qo_packed_idx_base,
                               kv_idx_base, qo_len, kv_len, chunk_end, group_size, s_frag, tid,
                               kv_head_idx);
        } else {
          if constexpr (MASK_MODE != MaskMode::kMultiItemScoring) {
            if (iter >= mask_iteration || iter < window_iteration) {
              logits_mask<KTraits>(params, variant, /*batch_idx=*/request_idx, qo_packed_idx_base,
                                   kv_idx_base, qo_len, kv_len, chunk_end, group_size, s_frag, tid,
                                   kv_head_idx);
            }
          } else if constexpr (MASK_MODE == MaskMode::kMultiItemScoring) {
            if (iter + 1 >= num_iterations_prefix) {
              logits_mask_multi_item_scoring<KTraits>(
                  params, variant, /*batch_idx=*/request_idx, qo_packed_idx_base, kv_idx_base,
                  qo_len, kv_len, window_left, chunk_end, group_size, s_frag,
                  __ldg(maybe_prefix_len_ptr + request_idx),
                  maybe_token_pos_in_items_ptr + request_idx * token_pos_in_items_len, tid.x,
                  kv_head_idx);
            } else {
              if (iter >= mask_iteration || iter < window_iteration) {
                logits_mask<KTraits>(params, variant, /*batch_idx=*/request_idx, qo_packed_idx_base,
                                     kv_idx_base, qo_len, kv_len, chunk_end, group_size, s_frag,
                                     tid, kv_head_idx);
              }
            }
          }
        }

        // compute m,d states in online softmax
        if constexpr (KTraits::USE_VO_SPLIT) {
          // VO-split: cross-warp consistent m/d + stage P to p_smem; o_scale is the
          // online rescale to apply to o_frag in vosplit_compute_pv below.
          vosplit_softmax_store_p<KTraits>(variant, s_frag, m, d, o_scale, &smem_storage,
                                           get_warp_idx_kv<KTraits>(tid.z),
                                           get_warp_idx_q<KTraits>(tid.y), lane_idx);
        } else {
          update_mdo_states<KTraits>(variant, s_frag, o_frag, m, d);
        }

        block.sync();
        if constexpr (KTraits::USE_KV_SHARED_SMEM) {
          page_produce_kv_on_the_fly<true, KTraits>(
              &smem_storage, &v_smem_offset_w, paged_kv.v_data, paged_kv, packed_page_iter_base,
              last_indptr, kv_head_idx, iter * CTA_TILE_KV, chunk_size, warp_idx, lane_idx);
          cp_async::commit_group();
          cp_async::wait_group<0>();
        } else {
          page_produce_kv<false, KTraits>(&smem_storage, &k_smem_offset_w, paged_kv.k_data,
                                          (iter + 1) * CTA_TILE_KV, thr_local_kv_offset_k,
                                          chunk_size, warp_idx, lane_idx);
          page_produce_kv_sf<false, KTraits>(
              &smem_storage, maybe_k_cache_sf, packed_page_iter_base,
              last_indptr * (uint32_t)paged_kv.page_size, kv_head_idx, k_sf_stride_page,
              k_sf_stride_h, k_sf_stride_n, paged_kv.page_size, paged_kv.indices,
              (iter + 1) * CTA_TILE_KV, chunk_size, warp_idx, lane_idx);
          cp_async::commit_group();
          cp_async::wait_group<1>();
        }
        block.sync();

        // compute sfm*v
        if constexpr (KTraits::USE_VO_SPLIT) {
          const uint32_t warp_vo_base =
              get_warp_idx_kv<KTraits>(tid.z) * KTraits::NUM_MMA_D_VO_PER_WARP;
          vosplit_compute_pv<KTraits>(&smem_storage, o_frag, o_scale, warp_vo_base,
                                      get_warp_idx_q<KTraits>(tid.y), lane_idx);
        } else if constexpr (KTraits::USE_KV_REPACK) {
          // Dequantize FP8 V -> BF16 staging smem (shuffle-free), then read native 16-bit.
          repack_fp8_tile_to_bf16<KTraits, KTraits::HEAD_DIM_VO>(smem_storage.v_smem,
                                                                 smem_storage.kv_smem_repack_ptr(),
                                                                 warp_idx * WARP_SIZE + lane_idx);
          block.sync();
          compute_sfm_v<KTraits, /*REPACK_BF16=*/true>(
              &v_smem_bf16, &v_smem_offset_r_bf16,
              smem_storage.v_sf_smem_ptr(get_warp_idx_kv<KTraits>(tid.z) * KTraits::NUM_MMA_KV *
                                         16 * KTraits::NUM_MMA_D_VO),
              lane_idx, s_frag, o_frag, d, d_base);
        } else {
          compute_sfm_v<KTraits>(
              &v_smem, &v_smem_offset_r,
              smem_storage.v_sf_smem_ptr(get_warp_idx_kv<KTraits>(tid.z) * KTraits::NUM_MMA_KV *
                                         16 * KTraits::NUM_MMA_D_VO),
              lane_idx, s_frag, o_frag, d, d_base);
        }

        block.sync();
        if constexpr (KTraits::USE_KV_SHARED_SMEM) {
          page_produce_kv_on_the_fly<false, KTraits>(
              &smem_storage, &k_smem_offset_w, paged_kv.k_data, paged_kv,
              next_packed_page_iter_base, last_indptr, kv_head_idx, (iter + 1) * CTA_TILE_KV,
              chunk_size, warp_idx, lane_idx);
          cp_async::commit_group();
          packed_page_iter_base = next_packed_page_iter_base;
        } else {
          page_produce_kv<true, KTraits>(&smem_storage, &v_smem_offset_w, paged_kv.v_data,
                                         (iter + 1) * CTA_TILE_KV, thr_local_kv_offset_v,
                                         chunk_size, warp_idx, lane_idx);
          page_produce_kv_sf<true, KTraits>(
              &smem_storage, maybe_v_cache_sf, packed_page_iter_base,
              last_indptr * (uint32_t)paged_kv.page_size, kv_head_idx, v_sf_stride_page,
              v_sf_stride_h, v_sf_stride_n, paged_kv.page_size, paged_kv.indices,
              (iter + 1) * CTA_TILE_KV, chunk_size, warp_idx, lane_idx);
          cp_async::commit_group();
        }
      }
      cp_async::wait_group<0>();
      block.sync();

      finalize_m<KTraits>(variant, m);

      const uint32_t num_kv_chunks =
          ceil_div(min(kv_len_safe, window_left + CTA_TILE_Q), kv_chunk_size);

      if constexpr (KTraits::USE_VO_SPLIT) {
        vosplit_write_o<KTraits>(o_frag, d, o_ptr_base, qo_packed_idx_base, qo_len,
                                 partition_kv ? num_kv_chunks * o_stride_n : o_stride_n, o_stride_h,
                                 get_warp_idx_kv<KTraits>(tid.z) * KTraits::NUM_MMA_D_VO_PER_WARP,
                                 group_size, lane_idx);
      } else {
        // threadblock synchronization
        threadblock_sync_mdo_states<KTraits>(o_frag, &smem_storage, m, d, warp_idx, lane_idx, tid);

        // transform output
        transform_output<KTraits, Params>(params, variant, o_frag, m, d, /*batch_idx=*/request_idx,
                                          kv_tile_idx, qo_packed_idx_base, warp_idx, lane_idx,
                                          kv_head_idx, group_size);

        // write_back (o_ptr_base offset to this VO tile's columns: d_base mma * 16 elems)
        write_o_reg_gmem<KTraits>(o_frag, &qo_smem, o_ptr_base + d_base * 16, qo_packed_idx_base,
                                  qo_len,
                                  /*o_stride_n=*/
                                  partition_kv ? num_kv_chunks * o_stride_n : o_stride_n,
                                  /*o_stride_h=*/o_stride_h, group_size, tid);
      }

      // write lse (identical across VO tiles since m/d depend only on Q.K^T; the
      // redundant rewrite on later tiles is harmless)
      if constexpr (variant.use_softmax) {
        if (lse != nullptr) {
          if (get_warp_idx_kv<KTraits>(tid.z) == 0) {
#pragma unroll
            for (uint32_t mma_q = 0; mma_q < NUM_MMA_Q; ++mma_q) {
#pragma unroll
              for (uint32_t j = 0; j < 2; ++j) {
                uint32_t q, r;
                group_size.divmod(qo_packed_idx_base + lane_idx / 4 + j * 8 + mma_q * 16, q, r);
                const uint32_t qo_head_idx = kv_head_idx * group_size + r;
                const uint32_t qo_idx = q;
                if (qo_idx < qo_upper_bound) {
                  if (partition_kv) {
                    lse[(o_indptr[request_idx] + qo_idx * num_kv_chunks + kv_tile_idx) *
                            num_qo_heads +
                        qo_head_idx] = math::ptx_log2(d[mma_q][j]) + float(m[mma_q][j]);
                  } else {
                    lse[(o_indptr[request_idx] + qo_idx) * num_qo_heads + qo_head_idx] =
                        math::ptx_log2(d[mma_q][j]) + float(m[mma_q][j]);
                  }
                }
              }
            }
          }
        }
      }
      block.sync();
    }  // d_tile (split-D over VO) loop

#if (__CUDACC_VER_MAJOR__ >= 12 && defined(__CUDA_ARCH__) && (__CUDA_ARCH__ >= 900))
    asm volatile("griddepcontrol.launch_dependents;");
#endif

#if (__CUDA_ARCH__ < 800)
  }
#endif
}

template <typename KTraits, typename Params>
__global__ __launch_bounds__(KTraits::NUM_THREADS) void BatchPrefillWithPagedKVCacheKernel(
    const __grid_constant__ Params params) {
  extern __shared__ uint8_t smem[];
  auto& smem_storage = reinterpret_cast<typename KTraits::SharedStoragePaged&>(smem);
  BatchPrefillWithPagedKVCacheDevice<KTraits>(params, smem_storage);
}

template <uint32_t CTA_TILE_Q, uint32_t HEAD_DIM_QK, uint32_t HEAD_DIM_VO,
          PosEncodingMode POS_ENCODING_MODE, bool USE_FP16_QK_REDUCTION, MaskMode MASK_MODE,
          typename AttentionVariant, typename Params>
cudaError_t BatchPrefillWithRaggedKVCacheDispatched(Params params, typename Params::DTypeO* tmp_v,
                                                    float* tmp_s, bool enable_pdl,
                                                    cudaStream_t stream) {
  using DTypeQ = typename Params::DTypeQ;
  using DTypeKV = typename Params::DTypeKV;
  using DTypeO = typename Params::DTypeO;
  const uint32_t padded_batch_size = params.padded_batch_size;
  const uint32_t num_qo_heads = params.num_qo_heads;
  const uint32_t num_kv_heads = params.num_kv_heads;
  // Large-head CTA_TILE_Q=32 uses a 2-Q x 2-KV-warp layout to keep the per-warp
  // output fragment bounded. FP8 must take it too: it stays on the split-D
  // merge path whose cta_sync_o_smem buffer (NUM_WARPS_KV x CTA_TILE_Q x 256
  // floats) only fits ~99KB-per-block parts (SM120/121) with NUM_WARPS_KV=2.
  // The 16-bit and NVFP4 softmax paths additionally use the VO-split P/V
  // helpers below, which avoid that buffer entirely.
  constexpr bool kLargeHeadWarpSplit = ((sizeof(DTypeKV) <= 2) || is_fp4_type_v<DTypeKV>) &&
                                       (HEAD_DIM_VO >= 512) && (CTA_TILE_Q == 32);
  constexpr uint32_t NUM_MMA_Q = kLargeHeadWarpSplit ? 1 : get_num_mma_q(CTA_TILE_Q);
  constexpr uint32_t NUM_WARPS_Q = kLargeHeadWarpSplit ? 2 : get_num_warps_q(CTA_TILE_Q);
  constexpr uint32_t NUM_WARPS_KV = kLargeHeadWarpSplit ? 2 : get_num_warps_kv(CTA_TILE_Q);

  if (padded_batch_size == 0) {
    // No request, skip
    // this won't happen in CUDAGraph mode because we fixed the padded_batch_size
    return cudaSuccess;
  }

  dim3 nblks(padded_batch_size, 1, num_kv_heads);
  dim3 nthrs(32, NUM_WARPS_Q, NUM_WARPS_KV);
  constexpr uint32_t NUM_MMA_D_QK = HEAD_DIM_QK / 16;
  constexpr uint32_t NUM_MMA_D_VO = HEAD_DIM_VO / 16;
  using DTypeQKAccum =
      typename std::conditional<USE_FP16_QK_REDUCTION && std::is_same_v<DTypeQ, half>, half,
                                float>::type;

  int dev_id = 0;
  FLASHINFER_CUDA_CALL(cudaGetDevice(&dev_id));
  int max_smem_per_sm = 0;
  FLASHINFER_CUDA_CALL(cudaDeviceGetAttribute(&max_smem_per_sm,
                                              cudaDevAttrMaxSharedMemoryPerMultiprocessor, dev_id));
  int max_smem_per_block_optin = 0;
  FLASHINFER_CUDA_CALL(cudaDeviceGetAttribute(&max_smem_per_block_optin,
                                              cudaDevAttrMaxSharedMemoryPerBlockOptin, dev_id));
  // we expect each sm execute two threadblocks
  // Per-NUM_MMA_KV K/V shared-memory cost, including the single BF16 repack
  // staging buffer (sized max(HEAD_DIM_QK, HEAD_DIM_VO)) when the FP8 repack path
  // is active, so NUM_MMA_KV is chosen to keep base+staging within the occupancy
  // budget (otherwise the staging silently drops blocks/SM at large head dims).
  constexpr bool kUseRepack = (sizeof(DTypeKV) == 1) && !is_fp4_type_v<DTypeKV> &&
                              (HEAD_DIM_VO != 64) && (HEAD_DIM_VO <= 256) && (CTA_TILE_Q > 16);
  // Matches KernelTraits::USE_KV_SHARED_SMEM: at large head dims K and V
  // time-share one smem buffer, so the occupancy budget counts the K/V
  // footprint once exactly when the kernel actually shares it.
  constexpr bool kKVShared = !is_fp4_type_v<DTypeKV> && (HEAD_DIM_VO / 16 > 16) &&
                             ((HEAD_DIM_VO / 16) % NUM_WARPS_KV == 0) &&
                             (HEAD_DIM_QK == HEAD_DIM_VO) &&
                             (sizeof(DTypeKV) == 2 || CTA_TILE_Q > 16);
  constexpr bool kVOSplitDispatch = ((sizeof(DTypeKV) == 2) || is_fp4_type_v<DTypeKV>) &&
                                    AttentionVariant::use_softmax && (HEAD_DIM_VO / 16 > 16) &&
                                    ((HEAD_DIM_VO / 16) % NUM_WARPS_KV == 0);
  constexpr uint32_t kKVSmemPerMmaKV =
      (kKVShared ? (HEAD_DIM_QK * 16 * NUM_WARPS_KV * sizeof(DTypeKV))
                 : ((HEAD_DIM_QK + HEAD_DIM_VO) * 16 * NUM_WARPS_KV * sizeof(DTypeKV))) +
      (kUseRepack ? ((HEAD_DIM_QK > HEAD_DIM_VO ? HEAD_DIM_QK : HEAD_DIM_VO) * 16 * NUM_WARPS_KV *
                     sizeof(DTypeQ))
                  : 0u) +
      (kVOSplitDispatch ? (CTA_TILE_Q * NUM_WARPS_KV * 16 * sizeof(DTypeQ)) : 0u);
  constexpr uint32_t kVOSplitFixedSmem =
      kVOSplitDispatch ? (NUM_WARPS_KV * CTA_TILE_Q * 8u + 2048u) : 0u;
  constexpr uint32_t kSharedRopeFreqSmem = (POS_ENCODING_MODE == PosEncodingMode::kRoPELlama &&
                                            HEAD_DIM_QK > 256 && HEAD_DIM_QK == HEAD_DIM_VO)
                                               ? (4u * (NUM_MMA_D_QK / 2) * 4u * sizeof(float))
                                               : 0u;
  constexpr uint32_t kFixedSmem =
      CTA_TILE_Q * HEAD_DIM_QK * sizeof(DTypeQ) + kVOSplitFixedSmem + kSharedRopeFreqSmem;
  // Smallest NUM_MMA_KV satisfying the FP8 alignment constraint
  // (sizeof(DTypeKV)==1 requires NUM_MMA_KV*2 % NUM_WARPS_Q == 0). When the
  // staging buffer shrinks the tile on tight-smem parts (e.g. SM120), we must
  // not land on an invalid NUM_MMA_KV, so size the occupancy budget against the
  // minimum *valid* tile rather than NUM_MMA_KV=1.
  constexpr uint32_t kMinValidMmaKV =
      (sizeof(DTypeKV) == 1 && NUM_WARPS_Q > 2) ? (NUM_WARPS_Q / 2) : 1;
  const int num_ctas_per_sm =
      max_smem_per_sm >= 2 * (kFixedSmem + kMinValidMmaKV * kKVSmemPerMmaKV) ? 2 : 1;
  // The occupancy budget (max_smem_per_sm / num_ctas_per_sm) can exceed the
  // hard per-block opt-in limit when only one CTA fits per SM (the two
  // attributes differ by 1KB on most arches), which would make
  // cudaFuncSetAttribute fail with cudaErrorInvalidValue. Clamp to the legal
  // per-block maximum so a config in that gap selects a smaller NUM_MMA_KV
  // instead of failing the launch.
  const int max_smem_per_threadblock =
      min(max_smem_per_sm / num_ctas_per_sm, max_smem_per_block_optin);

  const uint32_t max_num_mma_kv_reg =
      (HEAD_DIM_VO >= 128 && NUM_MMA_Q == 2 && POS_ENCODING_MODE == PosEncodingMode::kRoPELlama &&
       !USE_FP16_QK_REDUCTION)
          ? 2
          : (8 / NUM_MMA_Q);
  const int max_num_mma_kv_smem =
      (max_smem_per_threadblock - static_cast<int>(kFixedSmem)) / static_cast<int>(kKVSmemPerMmaKV);
  if (max_num_mma_kv_smem < 1) {
    std::ostringstream err_msg;
    err_msg << "Even the smallest KV tile for head_dim_qk=" << HEAD_DIM_QK
            << ", head_dim_vo=" << HEAD_DIM_VO << ", cta_tile_q=" << CTA_TILE_Q
            << " exceeds this GPU's " << max_smem_per_block_optin
            << " bytes of shared memory per block; this configuration is not supported on "
               "this architecture.";
    FLASHINFER_ERROR(err_msg.str());
  }

  DISPATCH_NUM_MMA_KV(
      min(static_cast<uint32_t>(max_num_mma_kv_smem), max_num_mma_kv_reg), NUM_MMA_KV, {
        using KTraits =
            KernelTraits<MASK_MODE, CTA_TILE_Q, NUM_MMA_Q, NUM_MMA_KV, NUM_MMA_D_QK, NUM_MMA_D_VO,
                         NUM_WARPS_Q, NUM_WARPS_KV, POS_ENCODING_MODE, DTypeQ, DTypeKV, DTypeO,
                         DTypeQKAccum, typename Params::IdType, AttentionVariant>;
        if constexpr (KTraits::IsInvalid()) {
          // Invalid configuration, skip
          std::ostringstream err_msg;
          err_msg << "FlashInfer Internal Error: Invalid configuration : NUM_MMA_Q=" << NUM_MMA_Q
                  << " NUM_MMA_D_QK=" << NUM_MMA_D_QK << " NUM_MMA_D_VO=" << NUM_MMA_D_VO
                  << " NUM_MMA_KV=" << NUM_MMA_KV << " NUM_WARPS_Q=" << NUM_WARPS_Q
                  << " NUM_WARPS_KV=" << NUM_WARPS_KV
                  << " please create an issue (https://github.com/flashinfer-ai/flashinfer/issues)"
                     " and report the issue to the developers.";
          FLASHINFER_ERROR(err_msg.str());
        } else {
          using SmemStorage = std::conditional_t<KTraits::USE_SOFTMAX_VO_SPLIT,
                                                 typename KTraits::SharedStoragePaged,
                                                 typename KTraits::SharedStorage>;
          size_t smem_size = sizeof(SmemStorage);
          auto kernel = BatchPrefillWithRaggedKVCacheKernel<KTraits, Params>;
          // Exact final check: the analytic NUM_MMA_KV budget can slightly
          // under-count the real struct (cross-warp merge buffers, padding);
          // fail with a clear error instead of a launch-time cudaErrorInvalidValue.
          if (smem_size > (size_t)max_smem_per_block_optin) {
            std::ostringstream err_msg;
            err_msg << "Required shared memory (" << smem_size
                    << " bytes) for head_dim_qk=" << HEAD_DIM_QK << ", head_dim_vo=" << HEAD_DIM_VO
                    << ", cta_tile_q=" << CTA_TILE_Q << " exceeds this GPU's per-block limit ("
                    << max_smem_per_block_optin
                    << " bytes); this configuration is not supported on this architecture.";
            FLASHINFER_ERROR(err_msg.str());
          }
          FLASHINFER_CUDA_CALL(
              cudaFuncSetAttribute(kernel, cudaFuncAttributeMaxDynamicSharedMemorySize, smem_size));
          // PDL launch config
          cudaLaunchAttribute attribute[1];
          cudaLaunchConfig_t config;
          if (enable_pdl) {
            attribute[0].id = cudaLaunchAttributeProgrammaticStreamSerialization;
            attribute[0].val.programmaticStreamSerializationAllowed = 1;
            config.attrs = attribute;
            config.numAttrs = 1;
            config.gridDim = nblks;
            config.blockDim = nthrs;
            config.dynamicSmemBytes = smem_size;
            config.stream = stream;
          }

          if (tmp_v == nullptr) {
            // do not partition kv
            params.partition_kv = false;
            void* args[] = {(void*)&params};
            if (enable_pdl) {
              FLASHINFER_CUDA_CALL(cudaLaunchKernelEx(&config, kernel, params));
            } else {
              FLASHINFER_CUDA_CALL(
                  cudaLaunchKernel((void*)kernel, nblks, nthrs, args, smem_size, stream));
            }
          } else {
            // partition kv
            params.partition_kv = true;
            auto o = params.o;
            auto lse = params.lse;
            params.o = tmp_v;
            params.lse = tmp_s;
            void* args[] = {(void*)&params};
            if (enable_pdl) {
              FLASHINFER_CUDA_CALL(cudaLaunchKernelEx(&config, kernel, params));
            } else {
              FLASHINFER_CUDA_CALL(
                  cudaLaunchKernel((void*)kernel, nblks, nthrs, args, smem_size, stream));
            }
            if constexpr (AttentionVariant::use_softmax) {
              FLASHINFER_CUDA_CALL(VariableLengthMergeStates(
                  tmp_v, tmp_s, params.merge_indptr, o, lse, params.max_total_num_rows,
                  params.total_num_rows, num_qo_heads, HEAD_DIM_VO, enable_pdl, stream));
            } else {
              FLASHINFER_CUDA_CALL(VariableLengthAttentionSum(
                  tmp_v, params.merge_indptr, o, params.max_total_num_rows, params.total_num_rows,
                  num_qo_heads, HEAD_DIM_VO, enable_pdl, stream));
            }
          }
        }
      });
  return cudaSuccess;
}

template <uint32_t CTA_TILE_Q, uint32_t HEAD_DIM_QK, uint32_t HEAD_DIM_VO,
          PosEncodingMode POS_ENCODING_MODE, bool USE_FP16_QK_REDUCTION, MaskMode MASK_MODE,
          typename AttentionVariant, typename Params>
cudaError_t BatchPrefillWithPagedKVCacheDispatched(Params params, typename Params::DTypeO* tmp_v,
                                                   float* tmp_s, bool enable_pdl,
                                                   cudaStream_t stream) {
  using DTypeQ = typename Params::DTypeQ;
  using DTypeKV = typename Params::DTypeKV;
  using DTypeO = typename Params::DTypeO;
  const uint32_t padded_batch_size = params.padded_batch_size;
  const uint32_t num_qo_heads = params.num_qo_heads;
  const uint32_t num_kv_heads = params.paged_kv.num_heads;
  // hd512 + 16-bit/NVFP4 KV uses the 2-Q x 2-KV-warp VO-split layout
  // (CTA_TILE_KV=32, 128-reg o_frag) so CTA_TILE_Q=32 fits 100KB; FP8 keeps the
  // default 1x4 layout.
  constexpr bool kBf16VOSplit = ((sizeof(DTypeKV) == 2) || is_fp4_type_v<DTypeKV>) &&
                                (HEAD_DIM_VO >= 512) && (CTA_TILE_Q == 32);
  constexpr uint32_t NUM_MMA_Q = kBf16VOSplit ? 1 : get_num_mma_q(CTA_TILE_Q);
  constexpr uint32_t NUM_WARPS_Q = kBf16VOSplit ? 2 : get_num_warps_q(CTA_TILE_Q);
  constexpr uint32_t NUM_WARPS_KV = kBf16VOSplit ? 2 : get_num_warps_kv(CTA_TILE_Q);

  if (padded_batch_size == 0) {
    // No request, skip
    // this won't happen in CUDAGraph mode because we fixed the padded_batch_size
    return cudaSuccess;
  }

  dim3 nblks(padded_batch_size, 1, num_kv_heads);
  dim3 nthrs(32, NUM_WARPS_Q, NUM_WARPS_KV);

  constexpr uint32_t NUM_MMA_D_QK = HEAD_DIM_QK / 16;
  constexpr uint32_t NUM_MMA_D_VO = HEAD_DIM_VO / 16;
  using DTypeQKAccum =
      typename std::conditional<USE_FP16_QK_REDUCTION && std::is_same_v<DTypeQ, half>, half,
                                float>::type;

  int dev_id = 0;
  FLASHINFER_CUDA_CALL(cudaGetDevice(&dev_id));
  int max_smem_per_sm = 0;
  FLASHINFER_CUDA_CALL(cudaDeviceGetAttribute(&max_smem_per_sm,
                                              cudaDevAttrMaxSharedMemoryPerMultiprocessor, dev_id));
  int max_smem_per_block_optin = 0;
  FLASHINFER_CUDA_CALL(cudaDeviceGetAttribute(&max_smem_per_block_optin,
                                              cudaDevAttrMaxSharedMemoryPerBlockOptin, dev_id));
  // we expect each sm execute two threadblocks
  // Per-NUM_MMA_KV K/V shared-memory cost, including the single BF16 repack
  // staging buffer (sized max(HEAD_DIM_QK, HEAD_DIM_VO)) when the FP8 repack path
  // is active, so NUM_MMA_KV is chosen to keep base+staging within the occupancy
  // budget (otherwise the staging silently drops blocks/SM at large head dims).
  constexpr bool kUseRepack = (sizeof(DTypeKV) == 1) && !is_fp4_type_v<DTypeKV> &&
                              (HEAD_DIM_VO != 64) && (HEAD_DIM_VO <= 256) && (CTA_TILE_Q > 16);
  // Matches KernelTraits::USE_KV_SHARED_SMEM: K/V share one smem buffer for bf16/fp16 at every
  // tile and for FP8 only at CTA_TILE_Q=32 (not NVFP4), so the occupancy budget counts the K/V
  // footprint once exactly when the kernel actually shares it.
  constexpr bool kKVShared = !is_fp4_type_v<DTypeKV> && (HEAD_DIM_VO / 16 > 16) &&
                             ((HEAD_DIM_VO / 16) % NUM_WARPS_KV == 0) &&
                             (HEAD_DIM_QK == HEAD_DIM_VO) &&
                             (sizeof(DTypeKV) == 2 || CTA_TILE_Q > 16);
  constexpr bool kVOSplitDispatch =
      (HEAD_DIM_VO / 16 > 16) && ((HEAD_DIM_VO / 16) % NUM_WARPS_KV == 0);
  constexpr uint32_t kKVSmemPerMmaKV =
      (kKVShared ? (HEAD_DIM_QK * 16 * NUM_WARPS_KV * sizeof(DTypeKV))
                 : ((HEAD_DIM_QK + HEAD_DIM_VO) * 16 * NUM_WARPS_KV * sizeof(DTypeKV))) +
      (kUseRepack ? ((HEAD_DIM_QK > HEAD_DIM_VO ? HEAD_DIM_QK : HEAD_DIM_VO) * 16 * NUM_WARPS_KV *
                     sizeof(DTypeQ))
                  : 0u) +
      (kVOSplitDispatch ? (CTA_TILE_Q * NUM_WARPS_KV * 16 * sizeof(DTypeQ)) : 0u);
  constexpr uint32_t kVOSplitFixedSmem =
      kVOSplitDispatch ? (NUM_WARPS_KV * CTA_TILE_Q * 8u + 2048u) : 0u;
  constexpr uint32_t kSharedRopeFreqSmem = (POS_ENCODING_MODE == PosEncodingMode::kRoPELlama &&
                                            HEAD_DIM_QK > 256 && HEAD_DIM_QK == HEAD_DIM_VO)
                                               ? (4u * (NUM_MMA_D_QK / 2) * 4u * sizeof(float))
                                               : 0u;
  constexpr uint32_t kFixedSmem =
      CTA_TILE_Q * HEAD_DIM_QK * sizeof(DTypeQ) + kVOSplitFixedSmem + kSharedRopeFreqSmem;
  // Smallest NUM_MMA_KV satisfying the FP8 alignment constraint
  // (sizeof(DTypeKV)==1 requires NUM_MMA_KV*2 % NUM_WARPS_Q == 0). When the
  // staging buffer shrinks the tile on tight-smem parts (e.g. SM120), we must
  // not land on an invalid NUM_MMA_KV, so size the occupancy budget against the
  // minimum *valid* tile rather than NUM_MMA_KV=1.
  constexpr uint32_t kMinValidMmaKV =
      (sizeof(DTypeKV) == 1 && NUM_WARPS_Q > 2) ? (NUM_WARPS_Q / 2) : 1;
  const int num_ctas_per_sm =
      max_smem_per_sm >= 2 * (kFixedSmem + kMinValidMmaKV * kKVSmemPerMmaKV) ? 2 : 1;
  const int max_smem_per_threadblock =
      min(max_smem_per_sm / num_ctas_per_sm, max_smem_per_block_optin);

  const uint32_t max_num_mma_kv_reg =
      (HEAD_DIM_VO >= 128 && NUM_MMA_Q == 2 && POS_ENCODING_MODE == PosEncodingMode::kRoPELlama &&
       !USE_FP16_QK_REDUCTION)
          ? 2
          : (8 / NUM_MMA_Q);
  const int max_num_mma_kv_smem =
      (max_smem_per_threadblock - static_cast<int>(kFixedSmem)) / static_cast<int>(kKVSmemPerMmaKV);
  if (max_num_mma_kv_smem < 1) {
    std::ostringstream err_msg;
    err_msg << "Even the smallest KV tile for head_dim_qk=" << HEAD_DIM_QK
            << ", head_dim_vo=" << HEAD_DIM_VO << ", cta_tile_q=" << CTA_TILE_Q
            << " exceeds this GPU's " << max_smem_per_block_optin
            << " bytes of shared memory per block; this configuration is not supported on "
               "this architecture.";
    FLASHINFER_ERROR(err_msg.str());
  }

  DISPATCH_NUM_MMA_KV(
      min(static_cast<uint32_t>(max_num_mma_kv_smem), max_num_mma_kv_reg), NUM_MMA_KV, {
        using KTraits =
            KernelTraits<MASK_MODE, CTA_TILE_Q, NUM_MMA_Q, NUM_MMA_KV, NUM_MMA_D_QK, NUM_MMA_D_VO,
                         NUM_WARPS_Q, NUM_WARPS_KV, POS_ENCODING_MODE, DTypeQ, DTypeKV, DTypeO,
                         DTypeQKAccum, typename Params::IdType, AttentionVariant>;
        if constexpr (KTraits::IsInvalid()) {
          // Invalid configuration, skip
          std::ostringstream err_msg;
          err_msg << "FlashInfer Internal Error: Invalid configuration : NUM_MMA_Q=" << NUM_MMA_Q
                  << " NUM_MMA_D_QK=" << NUM_MMA_D_QK << " NUM_MMA_D_VO=" << NUM_MMA_D_VO
                  << " NUM_MMA_KV=" << NUM_MMA_KV << " NUM_WARPS_Q=" << NUM_WARPS_Q
                  << " NUM_WARPS_KV=" << NUM_WARPS_KV
                  << " please create an issue (https://github.com/flashinfer-ai/flashinfer/issues)"
                     " and report the issue to the developers.";
          FLASHINFER_ERROR(err_msg.str());
        } else {
          size_t smem_size = sizeof(typename KTraits::SharedStoragePaged);
          auto kernel = BatchPrefillWithPagedKVCacheKernel<KTraits, Params>;
          // Exact final check: the analytic NUM_MMA_KV budget can slightly
          // under-count the real struct (cross-warp merge buffers, padding);
          // fail with a clear error instead of a launch-time cudaErrorInvalidValue.
          if (smem_size > (size_t)max_smem_per_block_optin) {
            std::ostringstream err_msg;
            err_msg << "Required shared memory (" << smem_size
                    << " bytes) for head_dim_qk=" << HEAD_DIM_QK << ", head_dim_vo=" << HEAD_DIM_VO
                    << ", cta_tile_q=" << CTA_TILE_Q << " exceeds this GPU's per-block limit ("
                    << max_smem_per_block_optin
                    << " bytes); this configuration is not supported on this architecture.";
            FLASHINFER_ERROR(err_msg.str());
          }
          FLASHINFER_CUDA_CALL(
              cudaFuncSetAttribute(kernel, cudaFuncAttributeMaxDynamicSharedMemorySize, smem_size));
          // PDL launch config
          cudaLaunchAttribute attribute[1];
          cudaLaunchConfig_t config;
          if (enable_pdl) {
            attribute[0].id = cudaLaunchAttributeProgrammaticStreamSerialization;
            attribute[0].val.programmaticStreamSerializationAllowed = 1;
            config.attrs = attribute;
            config.numAttrs = 1;
            config.gridDim = nblks;
            config.blockDim = nthrs;
            config.dynamicSmemBytes = smem_size;
            config.stream = stream;
          }

          if (tmp_v == nullptr) {
            // do not partition kv
            params.partition_kv = false;
            if (enable_pdl) {
              FLASHINFER_CUDA_CALL(cudaLaunchKernelEx(&config, kernel, params));
            } else {
              void* args[] = {(void*)&params};
              FLASHINFER_CUDA_CALL(
                  cudaLaunchKernel((void*)kernel, nblks, nthrs, args, smem_size, stream));
            }
          } else {
            params.partition_kv = true;
            auto o = params.o;
            auto lse = params.lse;
            params.o = tmp_v;
            params.lse = tmp_s;
            if (enable_pdl) {
              FLASHINFER_CUDA_CALL(cudaLaunchKernelEx(&config, kernel, params));
            } else {
              void* args[] = {(void*)&params};
              FLASHINFER_CUDA_CALL(
                  cudaLaunchKernel((void*)kernel, nblks, nthrs, args, smem_size, stream));
            }
            if constexpr (AttentionVariant::use_softmax) {
              FLASHINFER_CUDA_CALL(VariableLengthMergeStates(
                  tmp_v, tmp_s, params.merge_indptr, o, lse, params.max_total_num_rows,
                  params.total_num_rows, num_qo_heads, HEAD_DIM_VO, enable_pdl, stream));
            } else {
              FLASHINFER_CUDA_CALL(VariableLengthAttentionSum(
                  tmp_v, params.merge_indptr, o, params.max_total_num_rows, params.total_num_rows,
                  num_qo_heads, HEAD_DIM_VO, enable_pdl, stream));
            }
          }
        }
      });
  return cudaSuccess;
}

}  // namespace flashinfer

#endif  // FLASHINFER_PREFILL_CUH_
