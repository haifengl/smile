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
#ifndef FLASHINFER_ATTENTION_HOPPER_HEADER_CUH_
#define FLASHINFER_ATTENTION_HOPPER_HEADER_CUH_
#include <cuda.h>
#include <cuda_runtime.h>
#include <cutlass/arch/reg_reconfig.h>
#include <cutlass/cutlass.h>

#include <cute/arch/copy_sm90_tma.hpp>
#include <cute/arch/mma_sm90_desc.hpp>
#include <cute/arch/mma_sm90_gmma.hpp>
#include <cute/swizzle.hpp>
#include <cutlass/pipeline/sm90_pipeline.hpp>

#include "../permuted_smem.cuh"

namespace flashinfer {

using namespace cute::SM90::GMMA;

// using WGMMA_NN_64x32x16_F32BF16BF16_SS =
template <typename DTypeIn, typename DTypeOut, int M, int N, int K, Major TransposeA,
          Major TransposeB, ScaleIn scaleA, ScaleIn scaleB>
struct WGMMA_ASYNC_SS {};

template <typename DTypeIn, typename DTypeOut, int M, int N, int K, Major TransposeA,
          Major TransposeB, ScaleIn scaleA, ScaleIn scaleB>
struct WGMMA_ASYNC_RS {};

#define EXPAND_FRAG_ARGS_4(x) x[0], x[1], x[2], x[3]
#define EXPAND_FRAG_ARGS_8(x) x[0], x[1], x[2], x[3], x[4], x[5], x[6], x[7]
#define EXPAND_FRAG_ARGS_16(x)                                                                   \
  x[0], x[1], x[2], x[3], x[4], x[5], x[6], x[7], x[8], x[9], x[10], x[11], x[12], x[13], x[14], \
      x[15]
#define EXPAND_FRAG_ARGS_32(x)                                                                   \
  x[0], x[1], x[2], x[3], x[4], x[5], x[6], x[7], x[8], x[9], x[10], x[11], x[12], x[13], x[14], \
      x[15], x[16], x[17], x[18], x[19], x[20], x[21], x[22], x[23], x[24], x[25], x[26], x[27], \
      x[28], x[29], x[30], x[31]
#define EXPAND_FRAG_ARGS_64(x)                                                                   \
  x[0], x[1], x[2], x[3], x[4], x[5], x[6], x[7], x[8], x[9], x[10], x[11], x[12], x[13], x[14], \
      x[15], x[16], x[17], x[18], x[19], x[20], x[21], x[22], x[23], x[24], x[25], x[26], x[27], \
      x[28], x[29], x[30], x[31], x[32], x[33], x[34], x[35], x[36], x[37], x[38], x[39], x[40], \
      x[41], x[42], x[43], x[44], x[45], x[46], x[47], x[48], x[49], x[50], x[51], x[52], x[53], \
      x[54], x[55], x[56], x[57], x[58], x[59], x[60], x[61], x[62], x[63]
#define EXPAND_FRAG_ARGS_128(x)                                                                  \
  x[0], x[1], x[2], x[3], x[4], x[5], x[6], x[7], x[8], x[9], x[10], x[11], x[12], x[13], x[14], \
      x[15], x[16], x[17], x[18], x[19], x[20], x[21], x[22], x[23], x[24], x[25], x[26], x[27], \
      x[28], x[29], x[30], x[31], x[32], x[33], x[34], x[35], x[36], x[37], x[38], x[39], x[40], \
      x[41], x[42], x[43], x[44], x[45], x[46], x[47], x[48], x[49], x[50], x[51], x[52], x[53], \
      x[54], x[55], x[56], x[57], x[58], x[59], x[60], x[61], x[62], x[63], x[64], x[65], x[66], \
      x[67], x[68], x[69], x[70], x[71], x[72], x[73], x[74], x[75], x[76], x[77], x[78], x[79], \
      x[80], x[81], x[82], x[83], x[84], x[85], x[86], x[87], x[88], x[89], x[90], x[91], x[92], \
      x[93], x[94], x[95], x[96], x[97], x[98], x[99], x[100], x[101], x[102], x[103], x[104],   \
      x[105], x[106], x[107], x[108], x[109], x[110], x[111], x[112], x[113], x[114], x[115],    \
      x[116], x[117], x[118], x[119], x[120], x[121], x[122], x[123], x[124], x[125], x[126],    \
      x[127]

template <Major TransposeA, Major TransposeB, ScaleIn scaleA, ScaleIn scaleB>
struct WGMMA_ASYNC_RS<__half, float, 64, 16, 16, TransposeA, TransposeB, scaleA, scaleB> {
  template <bool init>
  static __device__ __forceinline__ void op(uint32_t* a_frag, uint64_t desc_b, float* d_frag) {
    constexpr auto scale_d = init ? ScaleOut::Zero : ScaleOut::One;
    MMA_64x16x16_F32F16F16_RS<TransposeA, TransposeB, scaleA, scaleB>::fma(
        EXPAND_FRAG_ARGS_4(a_frag), desc_b, EXPAND_FRAG_ARGS_8(d_frag), scale_d);
  }
};

template <Major TransposeA, Major TransposeB, ScaleIn scaleA, ScaleIn scaleB>
struct WGMMA_ASYNC_SS<__half, float, 64, 16, 16, TransposeA, TransposeB, scaleA, scaleB> {
  template <bool init>
  static __device__ __forceinline__ void op(uint64_t desc_a, uint64_t desc_b, float* d_frag) {
    constexpr auto scale_d = init ? ScaleOut::Zero : ScaleOut::One;
    MMA_64x16x16_F32F16F16_SS<TransposeA, TransposeB, scaleA, scaleB>::fma(
        desc_a, desc_b, EXPAND_FRAG_ARGS_8(d_frag), scale_d);
  }
};

template <Major TransposeA, Major TransposeB, ScaleIn scaleA, ScaleIn scaleB>
struct WGMMA_ASYNC_RS<__half, float, 64, 32, 16, TransposeA, TransposeB, scaleA, scaleB> {
  template <bool init>
  static __device__ __forceinline__ void op(uint32_t* a_frag, uint64_t desc_b, float* d_frag) {
    constexpr auto scale_d = init ? ScaleOut::Zero : ScaleOut::One;
    MMA_64x32x16_F32F16F16_RS<TransposeA, TransposeB, scaleA, scaleB>::fma(
        EXPAND_FRAG_ARGS_4(a_frag), desc_b, EXPAND_FRAG_ARGS_16(d_frag), scale_d);
  }
};

template <Major TransposeA, Major TransposeB, ScaleIn scaleA, ScaleIn scaleB>
struct WGMMA_ASYNC_SS<__half, float, 64, 32, 16, TransposeA, TransposeB, scaleA, scaleB> {
  template <bool init>
  static __device__ __forceinline__ void op(uint64_t desc_a, uint64_t desc_b, float* d_frag) {
    constexpr auto scale_d = init ? ScaleOut::Zero : ScaleOut::One;
    MMA_64x32x16_F32F16F16_SS<TransposeA, TransposeB, scaleA, scaleB>::fma(
        desc_a, desc_b, EXPAND_FRAG_ARGS_16(d_frag), scale_d);
  }
};

template <Major TransposeA, Major TransposeB, ScaleIn scaleA, ScaleIn scaleB>
struct WGMMA_ASYNC_RS<__half, float, 64, 64, 16, TransposeA, TransposeB, scaleA, scaleB> {
  template <bool init>
  static __device__ __forceinline__ void op(uint32_t* a_frag, uint64_t desc_b, float* d_frag) {
    constexpr auto scale_d = init ? ScaleOut::Zero : ScaleOut::One;
    MMA_64x64x16_F32F16F16_RS<TransposeA, TransposeB, scaleA, scaleB>::fma(
        EXPAND_FRAG_ARGS_4(a_frag), desc_b, EXPAND_FRAG_ARGS_32(d_frag), scale_d);
  }
};

template <Major TransposeA, Major TransposeB, ScaleIn scaleA, ScaleIn scaleB>
struct WGMMA_ASYNC_SS<__half, float, 64, 64, 16, TransposeA, TransposeB, scaleA, scaleB> {
  template <bool init>
  static __device__ __forceinline__ void op(uint64_t desc_a, uint64_t desc_b, float* d_frag) {
    constexpr auto scale_d = init ? ScaleOut::Zero : ScaleOut::One;
    MMA_64x64x16_F32F16F16_SS<TransposeA, TransposeB, scaleA, scaleB>::fma(
        desc_a, desc_b, EXPAND_FRAG_ARGS_32(d_frag), scale_d);
  }
};

template <Major TransposeA, Major TransposeB, ScaleIn scaleA, ScaleIn scaleB>
struct WGMMA_ASYNC_RS<__half, float, 64, 128, 16, TransposeA, TransposeB, scaleA, scaleB> {
  template <bool init>
  static __device__ __forceinline__ void op(uint32_t* a_frag, uint64_t desc_b, float* d_frag) {
    constexpr auto scale_d = init ? ScaleOut::Zero : ScaleOut::One;
    MMA_64x128x16_F32F16F16_RS<TransposeA, TransposeB, scaleA, scaleB>::fma(
        EXPAND_FRAG_ARGS_4(a_frag), desc_b, EXPAND_FRAG_ARGS_64(d_frag), scale_d);
  }
};

template <Major TransposeA, Major TransposeB, ScaleIn scaleA, ScaleIn scaleB>
struct WGMMA_ASYNC_SS<__half, float, 64, 128, 16, TransposeA, TransposeB, scaleA, scaleB> {
  template <bool init>
  static __device__ __forceinline__ void op(uint64_t desc_a, uint64_t desc_b, float* d_frag) {
    constexpr auto scale_d = init ? ScaleOut::Zero : ScaleOut::One;
    MMA_64x128x16_F32F16F16_SS<TransposeA, TransposeB, scaleA, scaleB>::fma(
        desc_a, desc_b, EXPAND_FRAG_ARGS_64(d_frag), scale_d);
  }
};

template <Major TransposeA, Major TransposeB, ScaleIn scaleA, ScaleIn scaleB>
struct WGMMA_ASYNC_RS<__half, float, 64, 256, 16, TransposeA, TransposeB, scaleA, scaleB> {
  template <bool init>
  static __device__ __forceinline__ void op(uint32_t* a_frag, uint64_t desc_b, float* d_frag) {
    constexpr auto scale_d = init ? ScaleOut::Zero : ScaleOut::One;
    MMA_64x256x16_F32F16F16_RS<TransposeA, TransposeB, scaleA, scaleB>::fma(
        EXPAND_FRAG_ARGS_4(a_frag), desc_b, EXPAND_FRAG_ARGS_128(d_frag), scale_d);
  }
};

template <Major TransposeA, Major TransposeB, ScaleIn scaleA, ScaleIn scaleB>
struct WGMMA_ASYNC_SS<__half, float, 64, 256, 16, TransposeA, TransposeB, scaleA, scaleB> {
  template <bool init>
  static __device__ __forceinline__ void op(uint64_t desc_a, uint64_t desc_b, float* d_frag) {
    constexpr auto scale_d = init ? ScaleOut::Zero : ScaleOut::One;
    MMA_64x256x16_F32F16F16_SS<TransposeA, TransposeB, scaleA, scaleB>::fma(
        desc_a, desc_b, EXPAND_FRAG_ARGS_128(d_frag), scale_d);
  }
};

template <Major TransposeA, Major TransposeB, ScaleIn scaleA, ScaleIn scaleB>
struct WGMMA_ASYNC_SS<__nv_bfloat16, float, 64, 16, 16, TransposeA, TransposeB, scaleA, scaleB> {
  template <bool init>
  static __device__ __forceinline__ void op(uint64_t desc_a, uint64_t desc_b, float* d_frag) {
    constexpr auto scale_d = init ? ScaleOut::Zero : ScaleOut::One;
    MMA_64x16x16_F32BF16BF16_SS<TransposeA, TransposeB, scaleA, scaleB>::fma(
        desc_a, desc_b, EXPAND_FRAG_ARGS_8(d_frag), scale_d);
  }
};

template <Major TransposeA, Major TransposeB, ScaleIn scaleA, ScaleIn scaleB>
struct WGMMA_ASYNC_SS<__nv_bfloat16, float, 64, 32, 16, TransposeA, TransposeB, scaleA, scaleB> {
  template <bool init>
  static __device__ __forceinline__ void op(uint64_t desc_a, uint64_t desc_b, float* d_frag) {
    constexpr auto scale_d = init ? ScaleOut::Zero : ScaleOut::One;
    MMA_64x32x16_F32BF16BF16_SS<TransposeA, TransposeB, scaleA, scaleB>::fma(
        desc_a, desc_b, EXPAND_FRAG_ARGS_16(d_frag), scale_d);
  }
};

template <Major TransposeA, Major TransposeB, ScaleIn scaleA, ScaleIn scaleB>
struct WGMMA_ASYNC_SS<__nv_bfloat16, float, 64, 64, 16, TransposeA, TransposeB, scaleA, scaleB> {
  template <bool init>
  static __device__ __forceinline__ void op(uint64_t desc_a, uint64_t desc_b, float* d_frag) {
    constexpr auto scale_d = init ? ScaleOut::Zero : ScaleOut::One;
    MMA_64x64x16_F32BF16BF16_SS<TransposeA, TransposeB, scaleA, scaleB>::fma(
        desc_a, desc_b, EXPAND_FRAG_ARGS_32(d_frag), scale_d);
  }
};

template <Major TransposeA, Major TransposeB, ScaleIn scaleA, ScaleIn scaleB>
struct WGMMA_ASYNC_SS<__nv_bfloat16, float, 64, 128, 16, TransposeA, TransposeB, scaleA, scaleB> {
  template <bool init>
  static __device__ __forceinline__ void op(uint64_t desc_a, uint64_t desc_b, float* d_frag) {
    constexpr auto scale_d = init ? ScaleOut::Zero : ScaleOut::One;
    MMA_64x128x16_F32BF16BF16_SS<TransposeA, TransposeB, scaleA, scaleB>::fma(
        desc_a, desc_b, EXPAND_FRAG_ARGS_64(d_frag), scale_d);
  }
};

template <Major TransposeA, Major TransposeB, ScaleIn scaleA, ScaleIn scaleB>
struct WGMMA_ASYNC_SS<__nv_bfloat16, float, 64, 256, 16, TransposeA, TransposeB, scaleA, scaleB> {
  template <bool init>
  static __device__ __forceinline__ void op(uint64_t desc_a, uint64_t desc_b, float* d_frag) {
    constexpr auto scale_d = init ? ScaleOut::Zero : ScaleOut::One;
    MMA_64x256x16_F32BF16BF16_SS<TransposeA, TransposeB, scaleA, scaleB>::fma(
        desc_a, desc_b, EXPAND_FRAG_ARGS_128(d_frag), scale_d);
  }
};

using Swizzle128B = cute::Swizzle<3, 4, 3>;
using Swizzle64B = cute::Swizzle<2, 4, 3>;
using Swizzle32B = cute::Swizzle<1, 4, 3>;

template <SwizzleMode swizzle_mode, uint32_t stride>
__device__ __forceinline__ uint32_t get_swizzle_offset(uint32_t i, uint32_t j) {
  constexpr uint32_t M = 8;
  if constexpr (swizzle_mode == SwizzleMode::k128B) {
    constexpr uint32_t N = 8;
    return Swizzle128B{}(((i / M) * M * stride + ((j / N) * M + i % M) * N + (j % N)) << 4) >> 4;
  } else {
    constexpr uint32_t N = 4;
    return Swizzle64B{}(((i / M) * M * stride + ((j / N) * M + i % M) * N + (j % N)) << 4) >> 4;
  }
}

__device__ __forceinline__ uint64_t matrix_descriptor_encode(uint64_t x) {
  return (((x) & 0x3FFFF) >> 0x4);
}

template <SwizzleMode swizzle_mode, uint64_t leading_byte_offset, uint64_t stride_byte_offset,
          typename T>
__device__ uint64_t make_smem_desc(T* ptr) {
  uint32_t addr = static_cast<uint32_t>(__cvta_generic_to_shared(ptr));
  uint64_t desc = 0x0000000000000000;
  desc |= matrix_descriptor_encode(addr);
  // leading byte offset
  desc |= matrix_descriptor_encode(leading_byte_offset) << 16;
  // stride byte offset
  desc |= matrix_descriptor_encode(stride_byte_offset) << 32;
  desc |= ((swizzle_mode == SwizzleMode::k128B)  ? 1llu
           : (swizzle_mode == SwizzleMode::k64B) ? 2llu
                                                 : 3llu)
          << 62;
  return desc;
}

__device__ __forceinline__ void warpgroup_arrive() { cute::warpgroup_arrive(); }

template <int N>
__device__ __forceinline__ void warpgroup_wait() {
  cute::warpgroup_wait<N>();
}

__device__ __forceinline__ void warpgroup_commit_batch() { cute::warpgroup_commit_batch(); }

template <uint32_t size>
__device__ __forceinline__ void warpgroup_fence_frag(float* frag) {
#pragma unroll
  for (uint32_t i = 0; i < size; ++i) {
    cute::warpgroup_fence_operand(frag[i]);
  }
}

};  // namespace flashinfer

#endif  // FLASHINFER_ATTENTION_HOPPER_HEADER_CUH_
