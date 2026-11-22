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
#ifndef FLASHINFER_POS_ENC_CUH_
#define FLASHINFER_POS_ENC_CUH_

#include <cmath>
#include <cstdint>
#include <iostream>
#include <string>
#include <type_traits>
#include <vector>

#include "layout.cuh"
#include "math.cuh"
#include "page.cuh"
#include "utils.cuh"
#include "vec_dtypes.cuh"

namespace flashinfer {

struct RopeQuantizeAppendPagedKVCacheParams {
  uint32_t nnz;
  uint32_t num_qo_heads;
  uint32_t num_kv_heads;
  uint32_t rope_dim;
  uint32_t no_rope_dim;
  size_t q_rope_in_stride_n;
  size_t q_rope_in_stride_h;
  size_t q_nope_in_stride_n;
  size_t q_nope_in_stride_h;
  size_t q_rope_out_stride_n;
  size_t q_rope_out_stride_h;
  size_t q_nope_out_stride_n;
  size_t q_nope_out_stride_h;
  size_t k_rope_in_stride;
  size_t k_rope_in_stride_h;
  size_t k_nope_in_stride;
  size_t k_nope_in_stride_h;
  size_t v_in_stride;
  size_t v_in_stride_h;
  float quant_scale_q;
  float quant_scale_kv;
};

/*!
 * \brief An enumeration class that defines different modes for applying RoPE
 *   (Rotary Positional Embeddings).
 */
enum class PosEncodingMode {
  // No rotary positional embeddings
  kNone = 0U,
  // Apply Llama-style rope.
  kRoPELlama = 1U,
  // Apply ALiBi bias
  kALiBi = 2U
};

/*!
 * \brief Convert PosEncodingMode to string
 * \param pos_encoding_mode A PosEncodingMode value
 */
inline std::string PosEncodingModeToString(const PosEncodingMode& pos_encoding_mode) {
  switch (pos_encoding_mode) {
    case PosEncodingMode::kNone:
      return "None";
    case PosEncodingMode::kRoPELlama:
      return "Llama";
    case PosEncodingMode::kALiBi:
      return "ALiBi";
    default:
      return "Unknown";
  }
}

__device__ __forceinline__ float get_alibi_slope(uint32_t head_idx, uint32_t num_heads) {
  int n = math::ptx_exp2((int)math::ptx_log2(num_heads));
  return head_idx < n ? math::ptx_exp2(-8. * float(head_idx + 1) / float(n))
                      : math::ptx_exp2(-4. * float((head_idx + 1 - n) * 2 - 1) / float(n));
}

/*!
 * \brief Apply RoPE (Rotary Positional Embeddings) to x[0: head_dim],
 *   return thread-local vector
 * \tparam vec_size A template integer indicates the vector size used
 *   in the kernel
 * \tparam bdx A template integer indicates the blockDim.x
 * \tparam T A template type indicates the x data type
 * \param x A pointer to the start of x data
 * \param freq A vector of float indicates the thread-local rope frequency
 * \param offset A integer indicates the offset of the position in RoPE
 */
template <uint32_t vec_size, uint32_t bdx, typename T>
__device__ __forceinline__ vec_t<float, vec_size> vec_apply_llama_rope(
    const T* x, const vec_t<float, vec_size>& freq, int32_t offset,
    const uint32_t rotary_dim = vec_size * bdx) {
  vec_t<float, vec_size> permuted_vec, vec;
  vec.cast_load(x + threadIdx.x * vec_size);

  if (threadIdx.x * vec_size < rotary_dim) {
    permuted_vec.cast_load(x + ((threadIdx.x * vec_size < rotary_dim / 2)
                                    ? threadIdx.x * vec_size + rotary_dim / 2
                                    : threadIdx.x * vec_size - rotary_dim / 2));
#pragma unroll
    for (uint32_t i = 0; i < vec_size; ++i) {
      float embed = float(offset) * freq[i];
      float cos, sin;
      __sincosf(embed, &sin, &cos);
      vec[i] =
          vec[i] * cos +
          ((threadIdx.x * vec_size < rotary_dim / 2) ? -permuted_vec[i] : permuted_vec[i]) * sin;
    }
  }
  return vec;
}

template <uint32_t vec_size, uint32_t bdx, typename T>
__device__ __forceinline__ vec_t<float, vec_size> vec_apply_llama_rope_cos_sin(
    const T* x, const vec_t<float, vec_size>& cos, const vec_t<float, vec_size>& sin,
    const uint32_t rotary_dim = vec_size * bdx) {
  vec_t<float, vec_size> permuted_vec, vec;
  vec.cast_load(x + threadIdx.x * vec_size);

  if (threadIdx.x * vec_size < rotary_dim) {
    permuted_vec.cast_load(x + ((threadIdx.x * vec_size < rotary_dim / 2)
                                    ? threadIdx.x * vec_size + rotary_dim / 2
                                    : threadIdx.x * vec_size - rotary_dim / 2));
#pragma unroll
    for (uint32_t i = 0; i < vec_size; ++i) {
      vec[i] =
          vec[i] * cos[i] +
          ((threadIdx.x * vec_size < rotary_dim / 2) ? -permuted_vec[i] : permuted_vec[i]) * sin[i];
    }
  }
  return vec;
}

/*!
 * \brief Apply RoPE (Rotary Positional Embeddings) to x[0: head_dim] with interleave,
 *   return thread-local vector.
 * \tparam vec_size A template integer indicates the vector size used
 *   in the kernel
 * \tparam bdx A template integer indicates the blockDim.x
 * \tparam T A template type indicates the x data type
 * \param x A pointer to the start of x data
 * \param freq A vector of float indicates the thread-local rope frequency
 * \param offset A integer indicates the offset of the position in RoPE
 */
template <uint32_t vec_size, uint32_t bdx, typename T>
__device__ __forceinline__ vec_t<float, vec_size> vec_apply_llama_rope_interleave(
    const T* x, const vec_t<float, vec_size>& freq, int32_t offset,
    const uint32_t rotary_dim = vec_size * bdx) {
  vec_t<float, vec_size> vec, vec_before;
  vec.cast_load(x + threadIdx.x * vec_size);

  if (threadIdx.x * vec_size < rotary_dim) {
    vec_before = vec;
#pragma unroll
    for (uint32_t i = 0; i < vec_size; ++i) {
      float embed = float(offset) * freq[i];
      float cos, sin;
      __sincosf(embed, &sin, &cos);
      vec[i] = vec[i] * cos + ((i % 2 == 0) ? -vec_before[i ^ 1] : vec_before[i ^ 1]) * sin;
    }
  }
  return vec;
}

template <uint32_t vec_size, uint32_t bdx, typename T>
__device__ __forceinline__ vec_t<float, vec_size> vec_apply_llama_rope_cos_sin_interleave(
    const T* x, const vec_t<float, vec_size>& cos, const vec_t<float, vec_size>& sin,
    const uint32_t rotary_dim = vec_size * bdx) {
  vec_t<float, vec_size> vec, vec_before;
  vec.cast_load(x + threadIdx.x * vec_size);

  if (threadIdx.x * vec_size < rotary_dim) {
    vec_before = vec;
#pragma unroll
    for (uint32_t i = 0; i < vec_size; ++i) {
      vec[i] = vec[i] * cos[i] + ((i % 2 == 0) ? -vec_before[i ^ 1] : vec_before[i ^ 1]) * sin[i];
    }
  }
  return vec;
}

/*
HACK (ByronHsu): in the interleave mode with cos_sin_cache, we actually only use the first half of
cos and sin

For example,
In the below example, the vec_size is 4
the computation in the kernel is:
    [x1, x2, x3, x4...] * [cos1, cos1, cos2, cos2] + [-x2, x1, -x4, x3...] * [sin1, sin1, sin2,
sin2] the data we loaded are:
    - loaded vec = [x1, x2, x3, x4]
    - loaded cos = [cos1, cos2, cos3, cos4]
    - loaded sin = [sin1, sin2, sin3, sin4]
But only the first half of cos and sin is used in the computation.

However, we argue the additional overhead is acceptable:
    1. loading additional elements of cos and sin is not adding much overhead. The arithmetic
intensity is the same as non-interleave mode. Each elements of cos and sin is load twice
    2. we don't want two code paths of cos and sin vector for interleave and non-interleave mode.
*/
template <uint32_t vec_size, uint32_t bdx, typename T>
__device__ __forceinline__ vec_t<float, vec_size>
vec_apply_llama_rope_cos_sin_interleave_reuse_half(const T* x, const vec_t<float, vec_size>& cos,
                                                   const vec_t<float, vec_size>& sin,
                                                   const uint32_t rotary_dim = vec_size * bdx) {
  vec_t<float, vec_size> vec, vec_before;
  vec.cast_load(x + threadIdx.x * vec_size);

  if (threadIdx.x * vec_size < rotary_dim) {
    vec_before = vec;
#pragma unroll
    for (uint32_t i = 0; i < vec_size; ++i) {
      // i / 2 is to get the index of the first half of cos and sin
      vec[i] = vec[i] * cos[i / 2] +
               ((i % 2 == 0) ? -vec_before[i ^ 1] : vec_before[i ^ 1]) * sin[i / 2];
    }
  }
  return vec;
}

/*
HACK (raayandhar): RoPE + quant kernels expect `no_rope_dim` to be a multiple of `rope_dim`.
That meant that every chunk covered a full vector load/store.

Now, since BatchQKApplyRotaryPosIdsCosSinCache is routed to the RopeQuantize kernel, the
"no rope" slice can be smaller than `rope_dim`.
e.g. consider head_dim = 192, rope_dim = 128, no_rope_dim = 64.

We want to write 64 values only, but the kernel will load and write 128, and cause
an error.

We guard these partial chunks by zero-padding unused lanes, and only writing
back the elements that actually exist in the chunk.
*/
template <typename DType, typename QuantType, uint32_t vec_size>
__device__ __forceinline__ void scale_store_partial_chunk(const DType* in_ptr, QuantType* out_ptr,
                                                          uint32_t lane_elem_offset,
                                                          uint32_t chunk_valid, float scale) {
  if (chunk_valid == 0 || lane_elem_offset >= chunk_valid) {
    return;
  }
  vec_t<float, vec_size> vec;
  if (lane_elem_offset + vec_size <= chunk_valid) {
    vec.cast_load(in_ptr + lane_elem_offset);
  } else {
#pragma unroll
    for (uint32_t i = 0; i < vec_size; ++i) {
      uint32_t elem_idx = lane_elem_offset + i;
      if (elem_idx < chunk_valid) {
        vec_t<float, 1> tmp;
        tmp.cast_load(in_ptr + elem_idx);
        vec[i] = tmp[0];
      } else {
        vec[i] = 0.f;
      }
    }
  }
#pragma unroll
  for (uint32_t i = 0; i < vec_size; ++i) {
    vec[i] = vec[i] * scale;
  }
  if (lane_elem_offset + vec_size <= chunk_valid) {
    vec.cast_store(out_ptr + lane_elem_offset);
  } else {
#pragma unroll
    for (uint32_t i = 0; i < vec_size; ++i) {
      uint32_t elem_idx = lane_elem_offset + i;
      if (elem_idx < chunk_valid) {
        vec_t<float, 1> tmp;
        tmp[0] = vec[i];
        tmp.cast_store(out_ptr + elem_idx);
      }
    }
  }
}

// Limits of the vector path (rotary size, 16-byte alignment, in-place warp span); by_lane has none.
inline bool RopeVectorPathIsWrong(bool interleave, uint32_t rope_dim, uint32_t vec_size) {
  return (interleave ? rope_dim : rope_dim / 2) % vec_size != 0;
}

template <typename T>
inline bool RopeVectorsAligned(const T* ptr, size_t stride_n, size_t stride_h) {
  return reinterpret_cast<uintptr_t>(ptr) % 16 == 0 && (stride_n * sizeof(T)) % 16 == 0 &&
         (stride_h * sizeof(T)) % 16 == 0;
}

// blockDim.x threads of up to vec_size / 2 pairs each cover the rope_dim / 2 pairs. Dealing the
// pairs out one thread at a time keeps a warp's elements next to each other, so its loads coalesce.
template <bool interleave, uint32_t vec_size, typename DType, typename QuantType>
__device__ __forceinline__ void rope_cos_sin_by_lane(const DType* x, QuantType* out,
                                                     const float* cos_sin, uint32_t rope_dim,
                                                     float scale) {
  const uint32_t half_rope_dim = rope_dim / 2;
#pragma unroll
  for (uint32_t i = 0; i < vec_size / 2; ++i) {
    const uint32_t pair = threadIdx.x + i * blockDim.x;
    if (pair < half_rope_dim) {
      const uint32_t first = interleave ? 2 * pair : pair;
      const uint32_t second = interleave ? first + 1 : first + half_rope_dim;
      vec_t<float, 1> a, b;
      a.cast_load(x + first);
      b.cast_load(x + second);
      const float cos = cos_sin[pair];
      const float sin = cos_sin[half_rope_dim + pair];
      vec_t<float, 1> a_rope, b_rope;
      a_rope[0] = (a[0] * cos - b[0] * sin) * scale;
      b_rope[0] = (b[0] * cos + a[0] * sin) * scale;
      a_rope.cast_store(out + first);
      b_rope.cast_store(out + second);
    }
  }
}

template <uint32_t vec_size, typename DType, typename QuantType>
__device__ __forceinline__ void scale_store_by_lane(const DType* in_ptr, QuantType* out_ptr,
                                                    uint32_t chunk_valid, float scale) {
#pragma unroll
  for (uint32_t i = 0; i < vec_size; ++i) {
    const uint32_t elem_idx = threadIdx.x + i * blockDim.x;
    if (elem_idx < chunk_valid) {
      vec_t<float, 1> tmp;
      tmp.cast_load(in_ptr + elem_idx);
      tmp[0] = tmp[0] * scale;
      tmp.cast_store(out_ptr + elem_idx);
    }
  }
}

template <bool interleave, uint32_t head_dim, uint32_t vec_size, uint32_t bdx, typename DType,
          typename IdType>
__global__ void BatchQKApplyRotaryPosIdsCosSinCacheHeadParallelismKernel(
    DType* q, DType* k, DType* q_rope, DType* k_rope, float* __restrict__ cos_sin_cache,
    IdType* __restrict__ pos_ids, uint32_t nnz, uint32_t num_qo_heads, uint32_t num_kv_heads,
    uint32_t rotary_dim, size_t q_stride_n, size_t q_stride_h, size_t k_stride_n, size_t k_stride_h,
    size_t q_rope_stride_n, size_t q_rope_stride_h, size_t k_rope_stride_n,
    size_t k_rope_stride_h) {
  uint32_t bx = blockIdx.x, tx = threadIdx.x, ty = threadIdx.y;
  uint32_t by = blockIdx.y;
  const uint32_t bdy = blockDim.y;

  vec_t<float, vec_size> cos, sin;
  if (bx * bdy + ty < nnz) {
    const uint32_t idx = bx * bdy + ty;
    const IdType pos = pos_ids[idx];

    const int half_rotary_dim = rotary_dim / 2;

    // 1. if interleave:
    //  - cos = cos_sin_cache[pos_id][tx * vec_size // 2]
    //  - sin = cos_sin_cache[pos_id][(rot_dim // 2) + tx * vec_size // 2]
    // 2. if not interleave
    //  - cos = cos_cache[pos_id][(tx * vec_size) % (rot_dim // 2)]
    //  - sin = sin_cache[pos_id][(rot_dim // 2) + (tx * vec_size) % (rot_dim // 2)]
    if (tx * vec_size < rotary_dim) {
      int sin_offset = rotary_dim / 2;
      int vec_idx;
      if constexpr (interleave) {
        vec_idx = (tx * vec_size) / 2;  // Force integer division
      } else {
        vec_idx = (tx * vec_size) % half_rotary_dim;  // Use half_rotary_dim
      }
      cos.load(cos_sin_cache + (pos * rotary_dim) + vec_idx);
      sin.load(cos_sin_cache + (pos * rotary_dim) + (sin_offset + vec_idx));
    }

    if (by < num_qo_heads) {
      uint32_t qo_head_idx = by;
      DType* q_ptr = q + get_elem_offset_impl(idx, qo_head_idx, 0, q_stride_n, q_stride_h);
      DType* q_rope_ptr =
          q_rope + get_elem_offset_impl(idx, qo_head_idx, 0, q_rope_stride_n, q_rope_stride_h);
      vec_t<float, vec_size> q_vec;
      if constexpr (interleave) {
        q_vec = vec_apply_llama_rope_cos_sin_interleave_reuse_half<vec_size, bdx>(q_ptr, cos, sin,
                                                                                  rotary_dim);
      } else {
        q_vec = vec_apply_llama_rope_cos_sin<vec_size, bdx>(q_ptr, cos, sin, rotary_dim);
      }
      q_vec.cast_store(q_rope_ptr + tx * vec_size);
    } else {
      uint32_t kv_head_idx = by - num_qo_heads;
      DType* k_ptr = k + get_elem_offset_impl(idx, kv_head_idx, 0, k_stride_n, k_stride_h);
      DType* k_rope_ptr =
          k_rope + get_elem_offset_impl(idx, kv_head_idx, 0, k_rope_stride_n, k_rope_stride_h);
      vec_t<float, vec_size> k_vec;
      if constexpr (interleave) {
        k_vec = vec_apply_llama_rope_cos_sin_interleave_reuse_half<vec_size, bdx>(k_ptr, cos, sin,
                                                                                  rotary_dim);
      } else {
        k_vec = vec_apply_llama_rope_cos_sin<vec_size, bdx>(k_ptr, cos, sin, rotary_dim);
      }
      k_vec.cast_store(k_rope_ptr + tx * vec_size);
    }
  }
}

template <bool interleave, uint32_t head_dim, uint32_t vec_size, uint32_t bdx, typename DType,
          typename IdType, uint32_t heads_per_step = 1, bool split_heads = false>
__global__ void BatchQKApplyRotaryPosIdsCosSinCacheKernel(
    DType* q, DType* k, DType* q_rope, DType* k_rope, float* __restrict__ cos_sin_cache,
    IdType* __restrict__ pos_ids, uint32_t nnz, uint32_t num_qo_heads, uint32_t num_kv_heads,
    uint32_t rotary_dim, size_t q_stride_n, size_t q_stride_h, size_t k_stride_n, size_t k_stride_h,
    size_t q_rope_stride_n, size_t q_rope_stride_h, size_t k_rope_stride_n,
    size_t k_rope_stride_h) {
  uint32_t bx = blockIdx.x, tx = threadIdx.x, ty = threadIdx.y;
  const uint32_t bdy = blockDim.y;

  vec_t<float, vec_size> cos, sin;
  if (bx * bdy + ty < nnz) {
    const uint32_t idx = bx * bdy + ty;
    const IdType pos = pos_ids[idx];
    const int half_rotary_dim = rotary_dim / 2;

    // 1. if interleave:
    //  - cos = cos_sin_cache[pos_id][tx * vec_size // 2]
    //  - sin = cos_sin_cache[pos_id][(rot_dim // 2) + tx * vec_size // 2]
    // 2. if not interleave
    //  - cos = cos_cache[pos_id][(tx * vec_size) % (rot_dim // 2)]
    //  - sin = sin_cache[pos_id][(rot_dim // 2) + (tx * vec_size) % (rot_dim // 2)]
    if (tx * vec_size < rotary_dim) {
      int sin_offset = rotary_dim / 2;
      int vec_idx;
      if constexpr (interleave) {
        vec_idx = (tx * vec_size) / 2;  // Force integer division
      } else {
        vec_idx = (tx * vec_size) % half_rotary_dim;  // Use half_rotary_dim
      }
      cos.load(cos_sin_cache + (pos * rotary_dim) + vec_idx);
      sin.load(cos_sin_cache + (pos * rotary_dim) + (sin_offset + vec_idx));
    }

    // not to unroll the loop, because num head might be large and might lead to worse performance
    if constexpr (heads_per_step == 2) {
      // Heads (q, then kv) strided by gridDim.y, two per step: both loads issue before
      // either store.
      const uint32_t num_heads = num_qo_heads + num_kv_heads;
      auto head_ptrs = [&](uint32_t h, DType*& in_ptr, DType*& out_ptr) {
        if (h < num_qo_heads) {
          in_ptr = q + get_elem_offset_impl(idx, h, 0, q_stride_n, q_stride_h);
          out_ptr = q_rope + get_elem_offset_impl(idx, h, 0, q_rope_stride_n, q_rope_stride_h);
        } else {
          in_ptr = k + get_elem_offset_impl(idx, h - num_qo_heads, 0, k_stride_n, k_stride_h);
          out_ptr = k_rope + get_elem_offset_impl(idx, h - num_qo_heads, 0, k_rope_stride_n,
                                                  k_rope_stride_h);
        }
      };
      auto apply_head = [&](DType* in_ptr) {
        if constexpr (interleave) {
          return vec_apply_llama_rope_cos_sin_interleave_reuse_half<vec_size, bdx>(in_ptr, cos, sin,
                                                                                   rotary_dim);
        } else {
          return vec_apply_llama_rope_cos_sin<vec_size, bdx>(in_ptr, cos, sin, rotary_dim);
        }
      };
#pragma unroll 1
      for (uint32_t h0 = blockIdx.y; h0 < num_heads; h0 += 2 * gridDim.y) {
        const uint32_t h1 = h0 + gridDim.y;
        const bool has_h1 = h1 < num_heads;
        // Odd tail re-reads h0 so both loads stay branch-free.
        DType *in0, *out0, *in1, *out1;
        head_ptrs(h0, in0, out0);
        head_ptrs(has_h1 ? h1 : h0, in1, out1);
        vec_t<float, vec_size> v0 = apply_head(in0);
        vec_t<float, vec_size> v1 = apply_head(in1);
        v0.cast_store(out0 + tx * vec_size);
        if (has_h1) {
          v1.cast_store(out1 + tx * vec_size);
        }
      }
    } else {
      // q heads, then kv heads; split_heads strides them by gridDim.y (two extra registers,
      // which can cost a resident CTA, so the unsplit variant is kept for gridDim.y == 1).
      const uint32_t h_begin = split_heads ? blockIdx.y : 0;
      const uint32_t h_step = split_heads ? gridDim.y : 1;
#pragma unroll 1
      for (uint32_t qo_head_idx = h_begin; qo_head_idx < num_qo_heads; qo_head_idx += h_step) {
        DType* q_ptr = q + get_elem_offset_impl(idx, qo_head_idx, 0, q_stride_n, q_stride_h);
        DType* q_rope_ptr =
            q_rope + get_elem_offset_impl(idx, qo_head_idx, 0, q_rope_stride_n, q_rope_stride_h);
        vec_t<float, vec_size> q_vec;
        if constexpr (interleave) {
          q_vec = vec_apply_llama_rope_cos_sin_interleave_reuse_half<vec_size, bdx>(q_ptr, cos, sin,
                                                                                    rotary_dim);
        } else {
          q_vec = vec_apply_llama_rope_cos_sin<vec_size, bdx>(q_ptr, cos, sin, rotary_dim);
        }
        q_vec.cast_store(q_rope_ptr + tx * vec_size);
      }

#pragma unroll 1
      for (uint32_t kv_head_idx = h_begin; kv_head_idx < num_kv_heads; kv_head_idx += h_step) {
        DType* k_ptr = k + get_elem_offset_impl(idx, kv_head_idx, 0, k_stride_n, k_stride_h);
        DType* k_rope_ptr =
            k_rope + get_elem_offset_impl(idx, kv_head_idx, 0, k_rope_stride_n, k_rope_stride_h);
        vec_t<float, vec_size> k_vec;
        if constexpr (interleave) {
          k_vec = vec_apply_llama_rope_cos_sin_interleave_reuse_half<vec_size, bdx>(k_ptr, cos, sin,
                                                                                    rotary_dim);
        } else {
          k_vec = vec_apply_llama_rope_cos_sin<vec_size, bdx>(k_ptr, cos, sin, rotary_dim);
        }
        k_vec.cast_store(k_rope_ptr + tx * vec_size);
      }
    }
  }
}

template <bool interleave, bool by_lane, uint32_t vec_size, uint32_t bdx, typename DType,
          typename IdType, typename QuantType>
__global__ void RopeQuantizeKernel(
    DType* q_rope_in, DType* k_rope_in, DType* q_nope_in, DType* k_nope_in, QuantType* q_rope_out,
    QuantType* k_rope_out, QuantType* q_nope_out, QuantType* k_nope_out,
    float* __restrict__ cos_sin_cache, IdType* __restrict__ pos_ids, uint32_t nnz,
    uint32_t num_qo_heads, uint32_t num_kv_heads, uint32_t rope_dim, uint32_t no_rope_dim,
    size_t q_rope_in_stride_n, size_t q_rope_in_stride_h, size_t q_nope_in_stride_n,
    size_t q_nope_in_stride_h, size_t q_rope_out_stride_n, size_t q_rope_out_stride_h,
    size_t q_nope_out_stride_n, size_t q_nope_out_stride_h, size_t k_rope_in_stride,
    size_t k_rope_in_stride_h, size_t k_nope_in_stride, size_t k_nope_in_stride_h,
    size_t k_rope_out_stride, size_t k_rope_out_stride_h, size_t k_nope_out_stride,
    size_t k_nope_out_stride_h, float quant_scale_q, float quant_scale_kv) {  // generalized kernel
#if (__CUDACC_VER_MAJOR__ >= 12 && defined(__CUDA_ARCH__) && (__CUDA_ARCH__ >= 900))
  asm volatile("griddepcontrol.wait;");
#endif
  uint32_t bx = blockIdx.x, tx = threadIdx.x, ty = threadIdx.y;
  uint32_t by = blockIdx.y;
  uint32_t bdy = blockDim.y;

  // Calculate flexible boundaries for block allocation
  uint32_t rope_chunk_size = rope_dim;  // Process entire rope_dim per chunk
  uint32_t rope_chunks = (rope_dim + rope_chunk_size - 1) / rope_chunk_size;
  uint32_t no_rope_chunks = (no_rope_dim + rope_chunk_size - 1) / rope_chunk_size;

  uint32_t q_rope_end = num_qo_heads * rope_chunks;
  uint32_t k_rope_end = q_rope_end + num_kv_heads * rope_chunks;
  uint32_t k_nope_end = k_rope_end + num_kv_heads * no_rope_chunks;

  vec_t<float, vec_size> cos, sin;
  if (bx * bdy + ty < nnz) {
    const uint32_t idx = bx * bdy + ty;
    const IdType pos = pos_ids[idx];

    const int half_rope_dim = rope_dim / 2;
    // Load cos/sin for RoPE processing blocks only
    // 1. if interleave:
    //  - cos = cos_sin_cache[pos_id][tx * vec_size // 2]
    //  - sin = cos_sin_cache[pos_id][(rot_dim // 2) + tx * vec_size // 2]
    // 2. if not interleave
    //  - cos = cos_cache[pos_id][(tx * vec_size) % (rot_dim // 2)]
    //  - sin = sin_cache[pos_id][(rot_dim // 2) + (tx * vec_size) % (rot_dim // 2)]
    if constexpr (!by_lane) {
      if ((tx * vec_size < rope_dim) && (by < k_rope_end)) {
        int sin_offset = rope_dim / 2;
        int vec_idx;
        if constexpr (interleave) {
          vec_idx = (tx * vec_size) / 2;  // Force integer division
        } else {
          vec_idx = (tx * vec_size) % half_rope_dim;  // Use half_rotary_dim
        }
        cos.load(cos_sin_cache + (pos * rope_dim) + vec_idx);
        sin.load(cos_sin_cache + (pos * rope_dim) + (sin_offset + vec_idx));
      }
    }

    if (by < q_rope_end) {
      // Q RoPE processing: num_qo_heads * rope_chunks blocks
      uint32_t q_head_idx = by / rope_chunks;
      uint32_t rope_chunk_idx = by % rope_chunks;
      uint32_t elem_offset = rope_chunk_idx * rope_chunk_size;

      DType* q_rope_in_ptr =
          q_rope_in + get_elem_offset_impl(idx, q_head_idx, elem_offset, q_rope_in_stride_n,
                                           q_rope_in_stride_h);
      QuantType* q_rope_out_ptr =
          q_rope_out + get_elem_offset_impl(idx, q_head_idx, elem_offset, q_rope_out_stride_n,
                                            q_rope_out_stride_h);

      if constexpr (by_lane) {
        rope_cos_sin_by_lane<interleave, vec_size>(q_rope_in_ptr, q_rope_out_ptr,
                                                   cos_sin_cache + (pos * rope_dim), rope_dim,
                                                   quant_scale_q);
      } else if (tx * vec_size < rope_dim) {
        vec_t<float, vec_size> q_rope_vec;
        if constexpr (interleave) {
          q_rope_vec = vec_apply_llama_rope_cos_sin_interleave_reuse_half<vec_size, bdx>(
              q_rope_in_ptr, cos, sin, rope_dim);
        } else {
          q_rope_vec =
              vec_apply_llama_rope_cos_sin<vec_size, bdx>(q_rope_in_ptr, cos, sin, rope_dim);
        }
#pragma unroll
        for (uint32_t i = 0; i < vec_size; ++i) {
          q_rope_vec[i] = q_rope_vec[i] * quant_scale_q;
        }
        q_rope_vec.cast_store(q_rope_out_ptr + tx * vec_size);
      }

    } else if (by < k_rope_end) {
      // K RoPE processing: num_kv_heads * rope_chunks blocks
      uint32_t k_head_idx = (by - q_rope_end) / rope_chunks;
      uint32_t rope_chunk_idx = (by - q_rope_end) % rope_chunks;
      uint32_t elem_offset = rope_chunk_idx * rope_chunk_size;

      DType* k_rope_in_ptr = k_rope_in + get_elem_offset_impl(idx, k_head_idx, elem_offset,
                                                              k_rope_in_stride, k_rope_in_stride_h);
      QuantType* k_rope_out_ptr =
          k_rope_out + get_elem_offset_impl(idx, k_head_idx, elem_offset, k_rope_out_stride,
                                            k_rope_out_stride_h);

      if constexpr (by_lane) {
        rope_cos_sin_by_lane<interleave, vec_size>(k_rope_in_ptr, k_rope_out_ptr,
                                                   cos_sin_cache + (pos * rope_dim), rope_dim,
                                                   quant_scale_kv);
      } else if (tx * vec_size < rope_dim) {
        vec_t<float, vec_size> k_rope_vec;
        if constexpr (interleave) {
          k_rope_vec = vec_apply_llama_rope_cos_sin_interleave_reuse_half<vec_size, bdx>(
              k_rope_in_ptr, cos, sin, rope_dim);
        } else {
          k_rope_vec =
              vec_apply_llama_rope_cos_sin<vec_size, bdx>(k_rope_in_ptr, cos, sin, rope_dim);
        }
#pragma unroll
        for (uint32_t i = 0; i < vec_size; ++i) {
          k_rope_vec[i] = k_rope_vec[i] * quant_scale_kv;
        }
        k_rope_vec.cast_store(k_rope_out_ptr + tx * vec_size);
      }

    } else if (by < k_nope_end) {
      // K Non-RoPE processing: num_kv_heads * no_rope_chunks blocks
      uint32_t k_head_idx = (by - k_rope_end) / no_rope_chunks;
      uint32_t nope_chunk_idx = (by - k_rope_end) % no_rope_chunks;
      uint32_t elem_offset = nope_chunk_idx * rope_chunk_size;  // Use same chunk size

      DType* k_nope_in_ptr = k_nope_in + get_elem_offset_impl(idx, k_head_idx, elem_offset,
                                                              k_nope_in_stride, k_nope_in_stride_h);
      QuantType* k_nope_out_ptr =
          k_nope_out + get_elem_offset_impl(idx, k_head_idx, elem_offset, k_nope_out_stride,
                                            k_nope_out_stride_h);

      uint32_t chunk_valid =
          (elem_offset < no_rope_dim) ? min(rope_chunk_size, no_rope_dim - elem_offset) : 0u;
      uint32_t lane_elem_offset = tx * vec_size;
      if constexpr (by_lane) {
        scale_store_by_lane<vec_size>(k_nope_in_ptr, k_nope_out_ptr, chunk_valid, quant_scale_kv);
      } else {
        // Handle tail chunks where no_rope_dim is not a multiple of rope_dim.
        scale_store_partial_chunk<DType, QuantType, vec_size>(
            k_nope_in_ptr, k_nope_out_ptr, lane_elem_offset, chunk_valid, quant_scale_kv);
      }

    } else {
      // Q Non-RoPE processing: num_qo_heads * no_rope_chunks blocks
      uint32_t q_head_idx = (by - k_nope_end) / no_rope_chunks;
      uint32_t nope_chunk_idx = (by - k_nope_end) % no_rope_chunks;
      uint32_t elem_offset = nope_chunk_idx * rope_chunk_size;  // Use same chunk size

      DType* q_nope_in_ptr =
          q_nope_in + get_elem_offset_impl(idx, q_head_idx, elem_offset, q_nope_in_stride_n,
                                           q_nope_in_stride_h);
      QuantType* q_nope_out_ptr =
          q_nope_out + get_elem_offset_impl(idx, q_head_idx, elem_offset, q_nope_out_stride_n,
                                            q_nope_out_stride_h);

      uint32_t chunk_valid =
          (elem_offset < no_rope_dim) ? min(rope_chunk_size, no_rope_dim - elem_offset) : 0u;
      uint32_t lane_elem_offset = tx * vec_size;
      if constexpr (by_lane) {
        scale_store_by_lane<vec_size>(q_nope_in_ptr, q_nope_out_ptr, chunk_valid, quant_scale_q);
      } else {
        // Handle tail chunks where no_rope_dim is not a multiple of rope_dim.
        scale_store_partial_chunk<DType, QuantType, vec_size>(
            q_nope_in_ptr, q_nope_out_ptr, lane_elem_offset, chunk_valid, quant_scale_q);
      }
    }
  }
#if (__CUDACC_VER_MAJOR__ >= 12 && defined(__CUDA_ARCH__) && (__CUDA_ARCH__ >= 900))
  asm volatile("griddepcontrol.launch_dependents;");
#endif
}

template <bool interleave, uint32_t head_dim, uint32_t vec_size, uint32_t bdx, typename DType,
          typename IdType>
__global__ void BatchQKApplyRotaryPosIdsHeadParallelismKernel(
    DType* q, DType* k, DType* q_rope, DType* k_rope, IdType* __restrict__ pos_ids, uint32_t nnz,
    uint32_t num_qo_heads, uint32_t num_kv_heads, uint32_t rotary_dim, size_t q_stride_n,
    size_t q_stride_h, size_t k_stride_n, size_t k_stride_h, size_t q_rope_stride_n,
    size_t q_rope_stride_h, size_t k_rope_stride_n, size_t k_rope_stride_h, float smooth_a,
    float smooth_b, float rope_rcp_scale, float rope_rcp_theta) {
  // NOTE: q and q_rope may be the same ptr, so do k and k_rope
  uint32_t bx = blockIdx.x, tx = threadIdx.x, ty = threadIdx.y;
  uint32_t by = blockIdx.y;
  const uint32_t bdy = blockDim.y;
  vec_t<float, vec_size> freq;
  if (tx * vec_size < rotary_dim) {
#pragma unroll
    for (uint32_t i = 0; i < vec_size; ++i) {
      if constexpr (interleave) {
        freq[i] = __powf(rope_rcp_theta, float(2 * ((tx * vec_size + i) / 2)) / float(rotary_dim));
      } else {
        freq[i] = __powf(rope_rcp_theta,
                         float(2 * ((tx * vec_size + i) % (rotary_dim / 2))) / float(rotary_dim));
      }

      float smooth = freq[i] * smooth_a + smooth_b;
      smooth = max(0.0f, min(1.0f, smooth));  // clamp to [0, 1]
      freq[i] = (1 - smooth) * (freq[i] * rope_rcp_scale) + smooth * freq[i];
    }
  }

  vec_t<float, vec_size> cos, sin;

  if (bx * bdy + ty < nnz) {
    const uint32_t idx = bx * bdy + ty;
    const IdType pos = pos_ids[idx];

    if (tx * vec_size < rotary_dim) {
#pragma unroll
      for (uint32_t i = 0; i < vec_size; ++i) {
        float embed = float(pos) * freq[i];
        __sincosf(embed, &sin[i], &cos[i]);
      }
    }

    if (by < num_qo_heads) {
      uint32_t qo_head_idx = by;
      DType* q_ptr = q + get_elem_offset_impl(idx, qo_head_idx, 0, q_stride_n, q_stride_h);
      DType* q_rope_ptr =
          q_rope + get_elem_offset_impl(idx, qo_head_idx, 0, q_rope_stride_n, q_rope_stride_h);
      vec_t<float, vec_size> q_vec;
      if constexpr (interleave) {
        q_vec = vec_apply_llama_rope_cos_sin_interleave<vec_size, bdx>(q_ptr, cos, sin, rotary_dim);
      } else {
        q_vec = vec_apply_llama_rope_cos_sin<vec_size, bdx>(q_ptr, cos, sin, rotary_dim);
      }
      q_vec.cast_store(q_rope_ptr + tx * vec_size);
    } else {
      uint32_t kv_head_idx = by - num_qo_heads;
      DType* k_ptr = k + get_elem_offset_impl(idx, kv_head_idx, 0, k_stride_n, k_stride_h);
      DType* k_rope_ptr =
          k_rope + get_elem_offset_impl(idx, kv_head_idx, 0, k_rope_stride_n, k_rope_stride_h);
      vec_t<float, vec_size> k_vec;
      if constexpr (interleave) {
        k_vec = vec_apply_llama_rope_cos_sin_interleave<vec_size, bdx>(k_ptr, cos, sin, rotary_dim);
      } else {
        k_vec = vec_apply_llama_rope_cos_sin<vec_size, bdx>(k_ptr, cos, sin, rotary_dim);
      }
      k_vec.cast_store(k_rope_ptr + tx * vec_size);
    }
  }
}

template <bool interleave, uint32_t head_dim, uint32_t vec_size, uint32_t bdx, typename DType,
          typename IdType, uint32_t heads_per_step = 1, bool split_heads = false>
__global__ void BatchQKApplyRotaryPosIdsKernel(
    DType* q, DType* k, DType* q_rope, DType* k_rope, IdType* __restrict__ pos_ids, uint32_t nnz,
    uint32_t num_qo_heads, uint32_t num_kv_heads, uint32_t rotary_dim, size_t q_stride_n,
    size_t q_stride_h, size_t k_stride_n, size_t k_stride_h, size_t q_rope_stride_n,
    size_t q_rope_stride_h, size_t k_rope_stride_n, size_t k_rope_stride_h, float smooth_a,
    float smooth_b, float rope_rcp_scale, float rope_rcp_theta) {
  // NOTE: q and q_rope may be the same ptr, so do k and k_rope
  uint32_t bx = blockIdx.x, tx = threadIdx.x, ty = threadIdx.y;
  const uint32_t bdy = blockDim.y;
  vec_t<float, vec_size> freq;
  if (tx * vec_size < rotary_dim) {
#pragma unroll
    for (uint32_t i = 0; i < vec_size; ++i) {
      if constexpr (interleave) {
        freq[i] = __powf(rope_rcp_theta, float(2 * ((tx * vec_size + i) / 2)) / float(rotary_dim));
      } else {
        freq[i] = __powf(rope_rcp_theta,
                         float(2 * ((tx * vec_size + i) % (rotary_dim / 2))) / float(rotary_dim));
      }

      float smooth = freq[i] * smooth_a + smooth_b;
      smooth = max(0.0f, min(1.0f, smooth));  // clamp to [0, 1]
      freq[i] = (1 - smooth) * (freq[i] * rope_rcp_scale) + smooth * freq[i];
    }
  }

  vec_t<float, vec_size> cos, sin;

  if (bx * bdy + ty < nnz) {
    const uint32_t idx = bx * bdy + ty;
    const IdType pos = pos_ids[idx];

    if (tx * vec_size < rotary_dim) {
#pragma unroll
      for (uint32_t i = 0; i < vec_size; ++i) {
        float embed = float(pos) * freq[i];
        __sincosf(embed, &sin[i], &cos[i]);
      }
    }

    if constexpr (heads_per_step == 2) {
      // Heads (q, then kv) strided by gridDim.y, two per step: both loads issue before
      // either store.
      const uint32_t num_heads = num_qo_heads + num_kv_heads;
      auto head_ptrs = [&](uint32_t h, DType*& in_ptr, DType*& out_ptr) {
        if (h < num_qo_heads) {
          in_ptr = q + get_elem_offset_impl(idx, h, 0, q_stride_n, q_stride_h);
          out_ptr = q_rope + get_elem_offset_impl(idx, h, 0, q_rope_stride_n, q_rope_stride_h);
        } else {
          in_ptr = k + get_elem_offset_impl(idx, h - num_qo_heads, 0, k_stride_n, k_stride_h);
          out_ptr = k_rope + get_elem_offset_impl(idx, h - num_qo_heads, 0, k_rope_stride_n,
                                                  k_rope_stride_h);
        }
      };
      auto apply_head = [&](DType* in_ptr) {
        if constexpr (interleave) {
          return vec_apply_llama_rope_cos_sin_interleave<vec_size, bdx>(in_ptr, cos, sin,
                                                                        rotary_dim);
        } else {
          return vec_apply_llama_rope_cos_sin<vec_size, bdx>(in_ptr, cos, sin, rotary_dim);
        }
      };
#pragma unroll 1
      for (uint32_t h0 = blockIdx.y; h0 < num_heads; h0 += 2 * gridDim.y) {
        const uint32_t h1 = h0 + gridDim.y;
        const bool has_h1 = h1 < num_heads;
        // Odd tail re-reads h0 so both loads stay branch-free.
        DType *in0, *out0, *in1, *out1;
        head_ptrs(h0, in0, out0);
        head_ptrs(has_h1 ? h1 : h0, in1, out1);
        vec_t<float, vec_size> v0 = apply_head(in0);
        vec_t<float, vec_size> v1 = apply_head(in1);
        v0.cast_store(out0 + tx * vec_size);
        if (has_h1) {
          v1.cast_store(out1 + tx * vec_size);
        }
      }
    } else {
      // q heads, then kv heads; split_heads strides them by gridDim.y (two extra registers,
      // which can cost a resident CTA, so the unsplit variant is kept for gridDim.y == 1).
      const uint32_t h_begin = split_heads ? blockIdx.y : 0;
      const uint32_t h_step = split_heads ? gridDim.y : 1;
#pragma unroll 1
      for (uint32_t qo_head_idx = h_begin; qo_head_idx < num_qo_heads; qo_head_idx += h_step) {
        DType* q_ptr = q + get_elem_offset_impl(idx, qo_head_idx, 0, q_stride_n, q_stride_h);
        DType* q_rope_ptr =
            q_rope + get_elem_offset_impl(idx, qo_head_idx, 0, q_rope_stride_n, q_rope_stride_h);
        vec_t<float, vec_size> q_vec;
        if constexpr (interleave) {
          q_vec =
              vec_apply_llama_rope_cos_sin_interleave<vec_size, bdx>(q_ptr, cos, sin, rotary_dim);
        } else {
          q_vec = vec_apply_llama_rope_cos_sin<vec_size, bdx>(q_ptr, cos, sin, rotary_dim);
        }
        q_vec.cast_store(q_rope_ptr + tx * vec_size);
      }

#pragma unroll 1
      for (uint32_t kv_head_idx = h_begin; kv_head_idx < num_kv_heads; kv_head_idx += h_step) {
        DType* k_ptr = k + get_elem_offset_impl(idx, kv_head_idx, 0, k_stride_n, k_stride_h);
        DType* k_rope_ptr =
            k_rope + get_elem_offset_impl(idx, kv_head_idx, 0, k_rope_stride_n, k_rope_stride_h);
        vec_t<float, vec_size> k_vec;
        if constexpr (interleave) {
          k_vec =
              vec_apply_llama_rope_cos_sin_interleave<vec_size, bdx>(k_ptr, cos, sin, rotary_dim);
        } else {
          k_vec = vec_apply_llama_rope_cos_sin<vec_size, bdx>(k_ptr, cos, sin, rotary_dim);
        }
        k_vec.cast_store(k_rope_ptr + tx * vec_size);
      }
    }
  }
}

template <bool interleave, uint32_t head_dim, uint32_t vec_size, uint32_t bdx, typename DType,
          typename IdType>
__global__ void BatchQKApplyRotaryKernel(
    DType* q, DType* k, DType* q_rope, DType* k_rope, IdType* __restrict__ indptr,
    IdType* __restrict__ offsets, uint32_t batch_size, uint32_t num_qo_heads, uint32_t num_kv_heads,
    uint32_t rotary_dim, size_t q_stride_n, size_t q_stride_h, size_t k_stride_n, size_t k_stride_h,
    size_t q_rope_stride_n, size_t q_rope_stride_h, size_t k_rope_stride_n, size_t k_rope_stride_h,
    float smooth_a, float smooth_b, float rope_rcp_scale, float rope_rcp_theta) {
  uint32_t bx = blockIdx.x, tx = threadIdx.x, ty = threadIdx.y;
  const uint32_t bdy = blockDim.y;
  vec_t<float, vec_size> freq;
  if (tx * vec_size < rotary_dim) {
#pragma unroll
    for (uint32_t i = 0; i < vec_size; ++i) {
      if constexpr (interleave) {
        freq[i] = __powf(rope_rcp_theta, float(2 * ((tx * vec_size + i) / 2)) / float(rotary_dim));
      } else {
        freq[i] = __powf(rope_rcp_theta,
                         float(2 * ((tx * vec_size + i) % (rotary_dim / 2))) / float(rotary_dim));
      }

      float smooth = freq[i] * smooth_a + smooth_b;
      smooth = max(0.0f, min(1.0f, smooth));  // clamp to [0, 1]
      freq[i] = (1 - smooth) * (freq[i] * rope_rcp_scale) + smooth * freq[i];
    }
  }

  if (bx < batch_size * num_qo_heads) {
    // apply rotary to q
    const uint32_t batch_idx = bx / num_qo_heads;
    const uint32_t qo_head_idx = bx % num_qo_heads;
    const uint32_t seq_len = indptr[batch_idx + 1] - indptr[batch_idx];
    const uint32_t offset = offsets[batch_idx];
#pragma unroll 2
    for (uint32_t i = blockIdx.y; i < (seq_len + bdy - 1) / bdy; i += gridDim.y) {
      vec_t<float, vec_size> q_vec;
      if (i * bdy + ty < seq_len) {
        DType* q_ptr = q + get_elem_offset_impl(indptr[batch_idx] + i * bdy + ty, qo_head_idx, 0,
                                                q_stride_n, q_stride_h);
        DType* q_rope_ptr =
            q_rope + get_elem_offset_impl(indptr[batch_idx] + i * bdy + ty, qo_head_idx, 0,
                                          q_rope_stride_n, q_rope_stride_h);
        if constexpr (interleave) {
          q_vec = vec_apply_llama_rope_interleave<vec_size, bdx>(q_ptr, freq, offset + i * bdy + ty,
                                                                 rotary_dim);
        } else {
          q_vec =
              vec_apply_llama_rope<vec_size, bdx>(q_ptr, freq, offset + i * bdy + ty, rotary_dim);
        }
        q_vec.cast_store(q_rope_ptr + tx * vec_size);
      }
    }
  } else {
    // apply rotary to k
    uint32_t batch_idx = (bx - batch_size * num_qo_heads) / num_kv_heads;
    uint32_t kv_head_idx = (bx - batch_size * num_qo_heads) % num_kv_heads;
    const uint32_t seq_len = indptr[batch_idx + 1] - indptr[batch_idx];
    const uint32_t offset = offsets[batch_idx];
#pragma unroll 2
    for (uint32_t i = blockIdx.y; i < (seq_len + bdy - 1) / bdy; i += gridDim.y) {
      vec_t<float, vec_size> k_vec;
      if (i * bdy + ty < seq_len) {
        DType* k_ptr = k + get_elem_offset_impl(indptr[batch_idx] + i * bdy + ty, kv_head_idx, 0,
                                                k_stride_n, k_stride_h);
        DType* k_rope_ptr =
            k_rope + get_elem_offset_impl(indptr[batch_idx] + i * bdy + ty, kv_head_idx, 0,
                                          k_rope_stride_n, k_rope_stride_h);
        if constexpr (interleave) {
          k_vec = vec_apply_llama_rope_interleave<vec_size, bdx>(k_ptr, freq, offset + i * bdy + ty,
                                                                 rotary_dim);
        } else {
          k_vec =
              vec_apply_llama_rope<vec_size, bdx>(k_ptr, freq, offset + i * bdy + ty, rotary_dim);
        }
        k_vec.cast_store(k_rope_ptr + tx * vec_size);
      }
    }
  }
}

/*!
 * \brief Unified CUDA kernel to apply RoPE, quantize to FP8, and append to paged cache.
 *
 * Templated on CacheT to support both GQA/MHA (paged_kv_t) and MLA (paged_kv_mla_t).
 * Cache-only behaviors are selected with constexpr on the CacheT.
 */
template <bool interleave, bool by_lane, uint32_t vec_size, uint32_t bdx, typename DType,
          typename RoPEIdType, typename PagedKVIdType, typename QuantType, typename CacheT>
__global__ void RopeQuantizeAppendPagedKVCacheKernel(
    DType* q_rope_in, DType* k_rope_in, DType* q_nope_in, DType* k_nope_in, DType* v_in,
    QuantType* q_rope_out, QuantType* q_nope_out, CacheT paged_kv_like,
    PagedKVIdType* __restrict__ batch_indices, PagedKVIdType* __restrict__ positions,
    float* __restrict__ cos_sin_cache, RoPEIdType* __restrict__ pos_ids,
    const RopeQuantizeAppendPagedKVCacheParams params) {
#if (__CUDACC_VER_MAJOR__ >= 12 && defined(__CUDA_ARCH__) && (__CUDA_ARCH__ >= 900))
  asm volatile("griddepcontrol.wait;");
#endif
  uint32_t bx = blockIdx.x, tx = threadIdx.x, ty = threadIdx.y;
  uint32_t by = blockIdx.y;
  uint32_t bdy = blockDim.y;

  // Local aliases for params for readability
  const uint32_t nnz = params.nnz;
  const uint32_t num_qo_heads = params.num_qo_heads;
  const uint32_t num_kv_heads = params.num_kv_heads;
  const uint32_t rope_dim = params.rope_dim;
  const uint32_t no_rope_dim = params.no_rope_dim;
  const size_t q_rope_in_stride_n = params.q_rope_in_stride_n;
  const size_t q_rope_in_stride_h = params.q_rope_in_stride_h;
  const size_t q_nope_in_stride_n = params.q_nope_in_stride_n;
  const size_t q_nope_in_stride_h = params.q_nope_in_stride_h;
  const size_t q_rope_out_stride_n = params.q_rope_out_stride_n;
  const size_t q_rope_out_stride_h = params.q_rope_out_stride_h;
  const size_t q_nope_out_stride_n = params.q_nope_out_stride_n;
  const size_t q_nope_out_stride_h = params.q_nope_out_stride_h;
  const size_t k_rope_in_stride = params.k_rope_in_stride;
  const size_t k_rope_in_stride_h = params.k_rope_in_stride_h;
  const size_t k_nope_in_stride = params.k_nope_in_stride;
  const size_t k_nope_in_stride_h = params.k_nope_in_stride_h;
  const size_t v_in_stride = params.v_in_stride;
  const size_t v_in_stride_h = params.v_in_stride_h;
  const float quant_scale_q = params.quant_scale_q;
  const float quant_scale_kv = params.quant_scale_kv;

  // Calculate flexible boundaries for block allocation
  uint32_t rope_chunk_size = rope_dim;
  uint32_t rope_chunks = (rope_dim + rope_chunk_size - 1) / rope_chunk_size;
  uint32_t no_rope_chunks = (no_rope_dim + rope_chunk_size - 1) / rope_chunk_size;

  uint32_t q_rope_end = num_qo_heads * rope_chunks;
  // For MLA, num_kv_heads is effectively 1
  uint32_t k_rope_end = q_rope_end + num_kv_heads * rope_chunks;
  uint32_t k_nope_end = k_rope_end + num_kv_heads * no_rope_chunks;

  // Deduce MLA vs GQA/MHA from CacheT
  constexpr bool IS_MLA = std::is_same<CacheT, paged_kv_mla_t<QuantType, PagedKVIdType>>::value;

  vec_t<float, vec_size> cos, sin;
  if (bx * bdy + ty < nnz) {
    const uint32_t idx = bx * bdy + ty;
    const RoPEIdType pos = pos_ids[idx];

    // skip padding tokens with batch_indices < 0
    if (batch_indices[idx] >= 0) {
      // Compute page location for this token
      uint32_t page_iter, entry_idx;
      paged_kv_like.page_size.divmod(
          paged_kv_like.indptr[batch_indices[idx]] * paged_kv_like.page_size + positions[idx],
          page_iter, entry_idx);

      const int half_rope_dim = rope_dim / 2;
      // Load cos/sin for RoPE processing blocks only
      if constexpr (!by_lane) {
        if ((tx * vec_size < rope_dim) && (by < k_rope_end)) {
          int sin_offset = rope_dim / 2;
          int vec_idx;
          if constexpr (interleave) {
            vec_idx = (tx * vec_size) / 2;  // Force integer division
          } else {
            vec_idx = (tx * vec_size) % half_rope_dim;
          }
          cos.load(cos_sin_cache + (pos * rope_dim) + vec_idx);
          sin.load(cos_sin_cache + (pos * rope_dim) + (sin_offset + vec_idx));
        }
      }

      if (by < q_rope_end) {
        // ============ Q RoPE processing ============
        uint32_t q_head_idx = by / rope_chunks;
        uint32_t rope_chunk_idx = by % rope_chunks;
        uint32_t elem_offset = rope_chunk_idx * rope_chunk_size;

        DType* q_rope_in_ptr =
            q_rope_in + get_elem_offset_impl(idx, q_head_idx, elem_offset, q_rope_in_stride_n,
                                             q_rope_in_stride_h);
        QuantType* q_rope_out_ptr =
            q_rope_out + get_elem_offset_impl(idx, q_head_idx, elem_offset, q_rope_out_stride_n,
                                              q_rope_out_stride_h);

        if constexpr (by_lane) {
          rope_cos_sin_by_lane<interleave, vec_size>(q_rope_in_ptr, q_rope_out_ptr,
                                                     cos_sin_cache + (pos * rope_dim), rope_dim,
                                                     quant_scale_q);
        } else {
          vec_t<float, vec_size> q_rope_vec;
          if constexpr (interleave) {
            q_rope_vec = vec_apply_llama_rope_cos_sin_interleave_reuse_half<vec_size, bdx>(
                q_rope_in_ptr, cos, sin, rope_dim);
          } else {
            q_rope_vec =
                vec_apply_llama_rope_cos_sin<vec_size, bdx>(q_rope_in_ptr, cos, sin, rope_dim);
          }
#pragma unroll
          for (uint32_t i = 0; i < vec_size; ++i) {
            q_rope_vec[i] = q_rope_vec[i] * quant_scale_q;
          }
          q_rope_vec.cast_store(q_rope_out_ptr + tx * vec_size);
        }

      } else if (by < k_rope_end) {
        // ============ K RoPE processing & Cache Append ============
        uint32_t k_head_idx = (by - q_rope_end) / rope_chunks;
        uint32_t rope_chunk_idx = (by - q_rope_end) % rope_chunks;
        uint32_t elem_offset = rope_chunk_idx * rope_chunk_size;

        DType* k_rope_in_ptr;
        if constexpr (IS_MLA) {
          // MLA: 2D K
          k_rope_in_ptr = k_rope_in + idx * k_rope_in_stride + elem_offset;
        } else {
          // GQA/MHA: 3D K
          k_rope_in_ptr = k_rope_in + get_elem_offset_impl(idx, k_head_idx, elem_offset,
                                                           k_rope_in_stride, k_rope_in_stride_h);
        }

        if constexpr (by_lane) {
          QuantType* k_out_ptr;
          if constexpr (IS_MLA) {
            k_out_ptr = paged_kv_like.get_kpe_ptr(page_iter, entry_idx, elem_offset);
          } else {
            k_out_ptr = paged_kv_like.get_k_ptr(page_iter, k_head_idx, entry_idx, 0);
          }
          rope_cos_sin_by_lane<interleave, vec_size>(
              k_rope_in_ptr, k_out_ptr, cos_sin_cache + (pos * rope_dim), rope_dim, quant_scale_kv);
        } else {
          vec_t<float, vec_size> k_rope_vec;
          if constexpr (interleave) {
            k_rope_vec = vec_apply_llama_rope_cos_sin_interleave_reuse_half<vec_size, bdx>(
                k_rope_in_ptr, cos, sin, rope_dim);
          } else {
            k_rope_vec =
                vec_apply_llama_rope_cos_sin<vec_size, bdx>(k_rope_in_ptr, cos, sin, rope_dim);
          }
#pragma unroll
          for (uint32_t i = 0; i < vec_size; ++i) {
            k_rope_vec[i] = k_rope_vec[i] * quant_scale_kv;
          }

          if constexpr (IS_MLA) {
            QuantType* kpe_ptr =
                paged_kv_like.get_kpe_ptr(page_iter, entry_idx, elem_offset + tx * vec_size);
            k_rope_vec.cast_store(kpe_ptr);
          } else {
            QuantType* k_ptr =
                paged_kv_like.get_k_ptr(page_iter, k_head_idx, entry_idx, tx * vec_size);
            k_rope_vec.cast_store(k_ptr);
          }
        }

      } else if (by < k_nope_end) {
        // ============ K Non-RoPE processing & Cache Append ============
        uint32_t k_head_idx = (by - k_rope_end) / no_rope_chunks;
        uint32_t nope_chunk_idx = (by - k_rope_end) % no_rope_chunks;
        uint32_t elem_offset = nope_chunk_idx * rope_chunk_size;

        DType* k_nope_in_ptr;
        QuantType* k_nope_out_ptr;
        if constexpr (IS_MLA) {
          k_nope_in_ptr = k_nope_in + idx * k_nope_in_stride + elem_offset;
          k_nope_out_ptr = paged_kv_like.get_ckv_ptr(page_iter, entry_idx, elem_offset);
        } else {
          k_nope_in_ptr = k_nope_in + get_elem_offset_impl(idx, k_head_idx, elem_offset,
                                                           k_nope_in_stride, k_nope_in_stride_h);
          k_nope_out_ptr =
              paged_kv_like.get_k_ptr(page_iter, k_head_idx, entry_idx, rope_dim + elem_offset);
        }

        uint32_t chunk_valid =
            (elem_offset < no_rope_dim) ? min(rope_chunk_size, no_rope_dim - elem_offset) : 0u;
        if (chunk_valid == rope_chunk_size) {
          // Full chunk (uniform per block): every lane holds a whole vector, since the host
          // requires rope_dim to be a multiple of 16.
          vec_t<float, vec_size> k_nope_vec;
          k_nope_vec.cast_load(k_nope_in_ptr + tx * vec_size);
#pragma unroll
          for (uint32_t i = 0; i < vec_size; ++i) {
            k_nope_vec[i] = k_nope_vec[i] * quant_scale_kv;
          }
          k_nope_vec.cast_store(k_nope_out_ptr + tx * vec_size);
        } else {
          // Tail chunk where no_rope_dim is not a multiple of rope_dim.
          scale_store_partial_chunk<DType, QuantType, vec_size>(
              k_nope_in_ptr, k_nope_out_ptr, tx * vec_size, chunk_valid, quant_scale_kv);
        }

      } else if (by < k_nope_end + (IS_MLA ? 0u : num_kv_heads)) {
        // ============ V processing & Cache Append (GQA/MHA only) ============
        if constexpr (!IS_MLA) {
          uint32_t kv_head_idx = by - k_nope_end;
          DType* v_in_ptr =
              v_in + get_elem_offset_impl(idx, kv_head_idx, 0, v_in_stride, v_in_stride_h);
          // Cover the full head dimension (rope_dim + no_rope_dim) in chunks of rope_chunk_size
          uint32_t head_dim_total = rope_dim + no_rope_dim;
          uint32_t v_chunks = (head_dim_total + rope_chunk_size - 1) / rope_chunk_size;
#pragma unroll 1
          for (uint32_t j = 0; j < v_chunks; ++j) {
            uint32_t v_elem_offset = j * rope_chunk_size;
            if (v_elem_offset + tx * vec_size < head_dim_total) {
              vec_t<float, vec_size> v_vec;
              v_vec.cast_load(v_in_ptr + v_elem_offset + tx * vec_size);
#pragma unroll
              for (uint32_t i = 0; i < vec_size; ++i) {
                v_vec[i] = v_vec[i] * quant_scale_kv;
              }
              QuantType* v_ptr = paged_kv_like.get_v_ptr(page_iter, kv_head_idx, entry_idx,
                                                         v_elem_offset + tx * vec_size);
              v_vec.cast_store(v_ptr);
            }
          }
        }

      } else {
        // ============ Q Non-RoPE processing ============
        // MLA has no V section, so Q-nope starts immediately after K-nope.
        // GQA/MHA has a V section of length num_kv_heads blocks.
        uint32_t q_nope_start = k_nope_end + (IS_MLA ? 0u : num_kv_heads);
        uint32_t q_head_idx = (by - q_nope_start) / no_rope_chunks;
        uint32_t nope_chunk_idx = (by - q_nope_start) % no_rope_chunks;
        uint32_t elem_offset = nope_chunk_idx * rope_chunk_size;

        DType* q_nope_in_ptr =
            q_nope_in + get_elem_offset_impl(idx, q_head_idx, elem_offset, q_nope_in_stride_n,
                                             q_nope_in_stride_h);
        QuantType* q_nope_out_ptr =
            q_nope_out + get_elem_offset_impl(idx, q_head_idx, elem_offset, q_nope_out_stride_n,
                                              q_nope_out_stride_h);

        uint32_t chunk_valid =
            (elem_offset < no_rope_dim) ? min(rope_chunk_size, no_rope_dim - elem_offset) : 0u;
        if (chunk_valid == rope_chunk_size) {
          // Full chunk (uniform per block), as for K above.
          vec_t<float, vec_size> q_nope_vec;
          q_nope_vec.cast_load(q_nope_in_ptr + tx * vec_size);
#pragma unroll
          for (uint32_t i = 0; i < vec_size; ++i) {
            q_nope_vec[i] = q_nope_vec[i] * quant_scale_q;
          }
          q_nope_vec.cast_store(q_nope_out_ptr + tx * vec_size);
        } else {
          // Tail chunk where no_rope_dim is not a multiple of rope_dim.
          scale_store_partial_chunk<DType, QuantType, vec_size>(
              q_nope_in_ptr, q_nope_out_ptr, tx * vec_size, chunk_valid, quant_scale_q);
        }
      }
    }
  }
#if (__CUDACC_VER_MAJOR__ >= 12 && defined(__CUDA_ARCH__) && (__CUDA_ARCH__ >= 900))
  asm volatile("griddepcontrol.launch_dependents;");
#endif
}

// Split a CTA's serial loop over gridDim.y = g CTAs (1 <= g <= max_split), minimising
// waves(g) * (steps(g) + 1); steps(g) is the CTA's loop trip count and the +1 models per-CTA
// setup (freqs, sin/cos, index loads).
template <typename StepsFn>
inline uint64_t RopeSplitCost(uint32_t nblks, uint32_t num_ctas_resident, uint32_t g,
                              StepsFn steps) {
  const uint64_t waves =
      (static_cast<uint64_t>(nblks) * g + num_ctas_resident - 1) / num_ctas_resident;
  return waves * (static_cast<uint64_t>(steps(g)) + 1);
}

template <typename StepsFn>
inline uint32_t RopeGridSplit(uint32_t nblks, uint32_t num_ctas_resident, uint32_t max_split,
                              StepsFn steps, uint64_t* cost_out = nullptr) {
  if (nblks == 0 || num_ctas_resident == 0 || max_split <= 1) {
    if (cost_out != nullptr) *cost_out = UINT64_MAX;
    return 1;
  }
  max_split = std::min(max_split, 128u);
  uint32_t best = 1;
  uint64_t best_cost = UINT64_MAX;
  for (uint32_t g = 1; g <= max_split; ++g) {
    const uint64_t cost = RopeSplitCost(nblks, num_ctas_resident, g, steps);
    if (cost < best_cost) {
      best_cost = cost;
      best = g;
    }
  }
  if (cost_out != nullptr) *cost_out = best_cost;
  return best;
}

// Resident CTAs (SMs x occupancy) of `kernel` on the current device, cached per thread by
// (device, kernel, block size) so launches skip the attribute and occupancy queries.
inline cudaError_t RopeResidentCtas(const void* kernel, uint32_t num_threads, uint32_t& num_ctas) {
  struct Entry {
    int device;
    const void* kernel;
    uint32_t num_threads;
    uint32_t num_ctas;
  };
  static thread_local std::vector<Entry> cache;
  int device = 0;
  FLASHINFER_CUDA_CALL(cudaGetDevice(&device));
  for (const Entry& e : cache) {
    if (e.device == device && e.kernel == kernel && e.num_threads == num_threads) {
      num_ctas = e.num_ctas;
      return cudaSuccess;
    }
  }
  int num_sms = 0, num_blocks_per_sm = 0;
  FLASHINFER_CUDA_CALL(cudaDeviceGetAttribute(&num_sms, cudaDevAttrMultiProcessorCount, device));
  FLASHINFER_CUDA_CALL(
      cudaOccupancyMaxActiveBlocksPerMultiprocessor(&num_blocks_per_sm, kernel, num_threads, 0));
  num_ctas = static_cast<uint32_t>(num_sms * num_blocks_per_sm);
  cache.push_back({device, kernel, num_threads, num_ctas});
  return cudaSuccess;
}

// Test-only launch override (set via rope_set_launch_override): two_heads < 0 and split == 0
// mean automatic. A forced split launches the token-parallel split kernel with gridDim.y = split,
// so tests can run the two-heads and strided kernels on any GPU.
struct RopeLaunchOverride {
  int32_t two_heads = -1;
  uint32_t split = 0;
};

inline RopeLaunchOverride& GetRopeLaunchOverride() {
  static RopeLaunchOverride launch_override;
  return launch_override;
}

// Two heads per step is measured only on SM107, where the 1024-thread resident limit already
// caps occupancy; on 2048-thread SMs its extra registers cost resident CTAs.
inline cudaError_t RopeUseTwoHeadsPerStep(bool& two_heads) {
  if (GetRopeLaunchOverride().two_heads >= 0) {
    two_heads = GetRopeLaunchOverride().two_heads != 0;
    return cudaSuccess;
  }
  static thread_local int cached_device = -1;
  static thread_local bool cached_value = false;
  int device = 0;
  FLASHINFER_CUDA_CALL(cudaGetDevice(&device));
  if (device != cached_device) {
    int major = 0, minor = 0;
    FLASHINFER_CUDA_CALL(cudaDeviceGetAttribute(&major, cudaDevAttrComputeCapabilityMajor, device));
    FLASHINFER_CUDA_CALL(cudaDeviceGetAttribute(&minor, cudaDevAttrComputeCapabilityMinor, device));
    cached_value = major == 10 && minor == 7;
    cached_device = device;
  }
  two_heads = cached_value;
  return cudaSuccess;
}

// gridDim.y for the token-parallel pos_ids kernels; a step is one head, or a pair of heads
// with two heads per step. With one head per step the unsplit
// kernel (gridDim.y == 1, fewer registers) is kept unless the strided kernel, at its own
// occupancy, models faster (by > 1.1x once the unsplit grid exceeds one wave);
// `use_split_kernel` reports which kernel to launch.
inline cudaError_t RopeHeadSplit(uint32_t nblks, const void* split_kernel,
                                 const void* unsplit_kernel, uint32_t num_threads,
                                 uint32_t num_qo_heads, uint32_t num_kv_heads, bool two_heads,
                                 uint32_t& split, bool& use_split_kernel) {
  if (GetRopeLaunchOverride().split > 0) {
    split = GetRopeLaunchOverride().split;
    use_split_kernel = true;
    return cudaSuccess;
  }
  auto steps = [&](uint32_t g) {
    const uint32_t heads = ceil_div(num_qo_heads + num_kv_heads, g);
    return two_heads ? ceil_div(heads, 2u) : heads;
  };
  uint32_t split_ctas = 0;
  FLASHINFER_CUDA_CALL(RopeResidentCtas(split_kernel, num_threads, split_ctas));
  uint64_t split_cost = UINT64_MAX;
  split = RopeGridSplit(nblks, split_ctas, num_qo_heads + num_kv_heads, steps, &split_cost);
  use_split_kernel = true;
  if (!two_heads) {
    uint32_t unsplit_ctas = 0;
    FLASHINFER_CUDA_CALL(RopeResidentCtas(unsplit_kernel, num_threads, unsplit_ctas));
    // Below one wave a split fills idle SMs. Past it the modelled saving is only the last
    // partial wave, which the strided kernel's overhead can outweigh, so require > 1.1x.
    const uint64_t unsplit_cost =
        unsplit_ctas == 0 ? 0 : RopeSplitCost(nblks, unsplit_ctas, 1, steps);
    const bool multi_wave = nblks > unsplit_ctas;
    if (unsplit_ctas == 0 ||
        (multi_wave ? unsplit_cost * 10 <= split_cost * 11 : unsplit_cost <= split_cost)) {
      split = 1;
      use_split_kernel = false;
    }
  }
  return cudaSuccess;
}

template <typename DType, typename IdType, typename QuantType>
cudaError_t RopeQuantize(
    DType* q_rope_in, DType* k_rope_in, DType* q_nope_in, DType* k_nope_in, QuantType* q_rope_out,
    QuantType* k_rope_out, QuantType* q_nope_out, QuantType* k_nope_out, float* cos_sin_cache,
    IdType* pos_ids, uint32_t nnz, uint32_t num_qo_heads, uint32_t num_kv_heads, uint32_t rope_dim,
    uint32_t no_rope_dim, size_t q_rope_in_stride_n, size_t q_rope_in_stride_h,
    size_t q_nope_in_stride_n, size_t q_nope_in_stride_h, size_t q_rope_out_stride_n,
    size_t q_rope_out_stride_h, size_t q_nope_out_stride_n, size_t q_nope_out_stride_h,
    size_t k_rope_in_stride, size_t k_rope_in_stride_h, size_t k_nope_in_stride,
    size_t k_nope_in_stride_h, size_t k_rope_out_stride, size_t k_rope_out_stride_h,
    size_t k_nope_out_stride, size_t k_nope_out_stride_h, float quant_scale_q, float quant_scale_kv,
    bool interleave, bool enable_pdl = false, cudaStream_t stream = nullptr) {
  if (rope_dim == 0 || rope_dim % 2 != 0) {
    std::ostringstream err_msg;
    err_msg << "rope_dim (" << rope_dim << ") must be positive and even";
    FLASHINFER_ERROR(err_msg.str());
  }

  int dev_id = 0;
  int num_sms = 0;
  FLASHINFER_CUDA_CALL(cudaGetDevice(&dev_id));
  FLASHINFER_CUDA_CALL(cudaDeviceGetAttribute(&num_sms, cudaDevAttrMultiProcessorCount, dev_id));

  // Use nested macros for runtime->compile-time dispatch for required constexpr values
  DISPATCH_INTERLEAVE(interleave, INTERLEAVE, {
    constexpr uint32_t vec_size = 32 / sizeof(DType);
    uint32_t bdx = (rope_dim + vec_size - 1) / vec_size;
    bdx = std::max(1u, bdx);
    const bool in_place = static_cast<const void*>(q_rope_in) == q_rope_out ||
                          static_cast<const void*>(k_rope_in) == k_rope_out;
    if (!INTERLEAVE && in_place && bdx < 32) {
      while (32 % bdx != 0) {
        ++bdx;  // The spare threads skip the rope.
      }
    }
    uint32_t num_threads = std::max(128U, bdx);
    uint32_t bdy = std::max(1u, num_threads / bdx);
    uint32_t nblks_x = (nnz + bdy - 1) / bdy;
    uint32_t rope_chunk_size = rope_dim;
    uint32_t rope_chunks = (rope_dim + rope_chunk_size - 1) / rope_chunk_size;
    uint32_t no_rope_chunks = (no_rope_dim + rope_chunk_size - 1) / rope_chunk_size;
    uint32_t total_blocks_y = num_qo_heads * rope_chunks + num_kv_heads * rope_chunks +
                              num_kv_heads * no_rope_chunks + num_qo_heads * no_rope_chunks;
    void* args[] = {(void*)&q_rope_in,
                    (void*)&k_rope_in,
                    (void*)&q_nope_in,
                    (void*)&k_nope_in,
                    (void*)&q_rope_out,
                    (void*)&k_rope_out,
                    (void*)&q_nope_out,
                    (void*)&k_nope_out,
                    (void*)&cos_sin_cache,
                    (void*)&pos_ids,
                    (void*)&nnz,
                    (void*)&num_qo_heads,
                    (void*)&num_kv_heads,
                    (void*)&rope_dim,
                    (void*)&no_rope_dim,
                    (void*)&q_rope_in_stride_n,
                    (void*)&q_rope_in_stride_h,
                    (void*)&q_nope_in_stride_n,
                    (void*)&q_nope_in_stride_h,
                    (void*)&q_rope_out_stride_n,
                    (void*)&q_rope_out_stride_h,
                    (void*)&q_nope_out_stride_n,
                    (void*)&q_nope_out_stride_h,
                    (void*)&k_rope_in_stride,
                    (void*)&k_rope_in_stride_h,
                    (void*)&k_nope_in_stride,
                    (void*)&k_nope_in_stride_h,
                    (void*)&k_rope_out_stride,
                    (void*)&k_rope_out_stride_h,
                    (void*)&k_nope_out_stride,
                    (void*)&k_nope_out_stride_h,
                    (void*)&quant_scale_q,
                    (void*)&quant_scale_kv};
    // Strides of size-1 dims are unused, and with no_rope_dim 0 no block touches the nope tensors.
    auto q_aligned = [&](const auto* ptr, size_t stride_n, size_t stride_h) {
      return RopeVectorsAligned(ptr, nnz > 1 ? stride_n : 0, num_qo_heads > 1 ? stride_h : 0);
    };
    auto k_aligned = [&](const auto* ptr, size_t stride_n, size_t stride_h) {
      return RopeVectorsAligned(ptr, nnz > 1 ? stride_n : 0, num_kv_heads > 1 ? stride_h : 0);
    };
    const bool nope_aligned =
        no_rope_dim == 0 || (q_aligned(q_nope_in, q_nope_in_stride_n, q_nope_in_stride_h) &&
                             k_aligned(k_nope_in, k_nope_in_stride, k_nope_in_stride_h) &&
                             q_aligned(q_nope_out, q_nope_out_stride_n, q_nope_out_stride_h) &&
                             k_aligned(k_nope_out, k_nope_out_stride, k_nope_out_stride_h));
    const bool by_lane = RopeVectorPathIsWrong(INTERLEAVE, rope_dim, vec_size) ||
                         !q_aligned(q_rope_in, q_rope_in_stride_n, q_rope_in_stride_h) ||
                         !k_aligned(k_rope_in, k_rope_in_stride, k_rope_in_stride_h) ||
                         !q_aligned(q_rope_out, q_rope_out_stride_n, q_rope_out_stride_h) ||
                         !k_aligned(k_rope_out, k_rope_out_stride, k_rope_out_stride_h) ||
                         !nope_aligned || !RopeVectorsAligned(cos_sin_cache, 0, 0) ||
                         (!INTERLEAVE && in_place && bdx > 32);
    auto kernel =
        by_lane ? RopeQuantizeKernel<INTERLEAVE, true, vec_size, 1, DType, IdType, QuantType>
                : RopeQuantizeKernel<INTERLEAVE, false, vec_size, 1, DType, IdType, QuantType>;
    dim3 nblks(nblks_x, total_blocks_y);
    dim3 nthrs(bdx, bdy);

    cudaLaunchAttribute attribute[1];
    attribute[0].id = cudaLaunchAttributeProgrammaticStreamSerialization;
    attribute[0].val.programmaticStreamSerializationAllowed = enable_pdl ? 1 : 0;
    cudaLaunchConfig_t config;
    config.gridDim = nblks;
    config.blockDim = nthrs;
    config.stream = stream;
    config.dynamicSmemBytes = 0;
    config.attrs = attribute;
    config.numAttrs = 1;

    FLASHINFER_CUDA_CALL(cudaLaunchKernelEx(
        &config, kernel, q_rope_in, k_rope_in, q_nope_in, k_nope_in, q_rope_out, k_rope_out,
        q_nope_out, k_nope_out, cos_sin_cache, pos_ids, nnz, num_qo_heads, num_kv_heads, rope_dim,
        no_rope_dim, q_rope_in_stride_n, q_rope_in_stride_h, q_nope_in_stride_n, q_nope_in_stride_h,
        q_rope_out_stride_n, q_rope_out_stride_h, q_nope_out_stride_n, q_nope_out_stride_h,
        k_rope_in_stride, k_rope_in_stride_h, k_nope_in_stride, k_nope_in_stride_h,
        k_rope_out_stride, k_rope_out_stride_h, k_nope_out_stride, k_nope_out_stride_h,
        quant_scale_q, quant_scale_kv));
  });

  return cudaSuccess;
}

/*!
 * \brief Host function to apply RoPE, quantize to FP8, and append K/V to paged cache (GQA/MHA)
 */
template <typename DType, typename RoPEIdType, typename PagedKVIdType, typename QuantType>
cudaError_t RopeQuantizeAppendPagedKVCache(
    DType* q_rope_in, DType* k_rope_in, DType* q_nope_in, DType* k_nope_in, DType* v_in,
    QuantType* q_rope_out, QuantType* q_nope_out, paged_kv_t<QuantType, PagedKVIdType> paged_kv,
    PagedKVIdType* batch_indices, PagedKVIdType* positions, float* cos_sin_cache,
    RoPEIdType* pos_ids, uint32_t nnz, uint32_t num_qo_heads, uint32_t num_kv_heads,
    uint32_t rope_dim, uint32_t no_rope_dim, size_t q_rope_in_stride_n, size_t q_rope_in_stride_h,
    size_t q_nope_in_stride_n, size_t q_nope_in_stride_h, size_t q_rope_out_stride_n,
    size_t q_rope_out_stride_h, size_t q_nope_out_stride_n, size_t q_nope_out_stride_h,
    size_t k_rope_in_stride, size_t k_rope_in_stride_h, size_t k_nope_in_stride,
    size_t k_nope_in_stride_h, size_t v_in_stride, size_t v_in_stride_h, float quant_scale_q,
    float quant_scale_kv, bool interleave, bool enable_pdl = false, cudaStream_t stream = nullptr) {
  DISPATCH_INTERLEAVE(interleave, INTERLEAVE, {
    constexpr uint32_t vec_size = 32 / sizeof(DType);
    uint32_t bdx = (rope_dim + vec_size - 1) / vec_size;
    bdx = std::max(1u, bdx);
    uint32_t num_threads = std::max(128U, bdx);
    uint32_t bdy = std::max(1u, num_threads / bdx);
    uint32_t nblks_x = (nnz + bdy - 1) / bdy;
    uint32_t rope_chunks = 1;
    uint32_t no_rope_chunks = (no_rope_dim + rope_dim - 1) / rope_dim;

    uint32_t total_blocks_y = num_qo_heads * rope_chunks + num_kv_heads * rope_chunks +
                              num_kv_heads * no_rope_chunks + num_kv_heads +
                              num_qo_heads * no_rope_chunks;

    dim3 nblks(nblks_x, total_blocks_y);
    dim3 nthrs(bdx, bdy);

    cudaLaunchAttribute attribute[1];
    attribute[0].id = cudaLaunchAttributeProgrammaticStreamSerialization;
    attribute[0].val.programmaticStreamSerializationAllowed = enable_pdl ? 1 : 0;
    cudaLaunchConfig_t config;
    config.gridDim = nblks;
    config.blockDim = nthrs;
    config.stream = stream;
    config.dynamicSmemBytes = 0;
    config.attrs = attribute;
    config.numAttrs = 1;

    auto kernel =
        (RopeVectorPathIsWrong(INTERLEAVE, rope_dim, vec_size) ||
         !RopeVectorsAligned(cos_sin_cache, 0, 0))
            ? RopeQuantizeAppendPagedKVCacheKernel<INTERLEAVE, true, vec_size, /*bdx=*/1, DType,
                                                   RoPEIdType, PagedKVIdType, QuantType,
                                                   paged_kv_t<QuantType, PagedKVIdType>>
            : RopeQuantizeAppendPagedKVCacheKernel<INTERLEAVE, false, vec_size, /*bdx=*/1, DType,
                                                   RoPEIdType, PagedKVIdType, QuantType,
                                                   paged_kv_t<QuantType, PagedKVIdType>>;
    RopeQuantizeAppendPagedKVCacheParams params;
    params.nnz = nnz;
    params.num_qo_heads = num_qo_heads;
    params.num_kv_heads = num_kv_heads;
    params.rope_dim = rope_dim;
    params.no_rope_dim = no_rope_dim;
    params.q_rope_in_stride_n = q_rope_in_stride_n;
    params.q_rope_in_stride_h = q_rope_in_stride_h;
    params.q_nope_in_stride_n = q_nope_in_stride_n;
    params.q_nope_in_stride_h = q_nope_in_stride_h;
    params.q_rope_out_stride_n = q_rope_out_stride_n;
    params.q_rope_out_stride_h = q_rope_out_stride_h;
    params.q_nope_out_stride_n = q_nope_out_stride_n;
    params.q_nope_out_stride_h = q_nope_out_stride_h;
    params.k_rope_in_stride = k_rope_in_stride;
    params.k_rope_in_stride_h = k_rope_in_stride_h;
    params.k_nope_in_stride = k_nope_in_stride;
    params.k_nope_in_stride_h = k_nope_in_stride_h;
    params.v_in_stride = v_in_stride;
    params.v_in_stride_h = v_in_stride_h;
    params.quant_scale_q = quant_scale_q;
    params.quant_scale_kv = quant_scale_kv;

    FLASHINFER_CUDA_CALL(cudaLaunchKernelEx(
        &config, kernel, q_rope_in, k_rope_in, q_nope_in, k_nope_in, v_in, q_rope_out, q_nope_out,
        paged_kv, batch_indices, positions, cos_sin_cache, pos_ids, params));
  });

  return cudaSuccess;
}

/*!
 * \brief Host function to apply RoPE, quantize to FP8, and append to MLA paged cache
 */
template <typename DType, typename RoPEIdType, typename PagedKVIdType, typename QuantType>
cudaError_t RopeQuantizeAppendPagedMLACache(
    DType* q_rope_in, DType* k_rope_in, DType* q_nope_in, DType* k_nope_in, QuantType* q_rope_out,
    QuantType* q_nope_out, paged_kv_mla_t<QuantType, PagedKVIdType> paged_kv_mla,
    PagedKVIdType* batch_indices, PagedKVIdType* positions, float* cos_sin_cache,
    RoPEIdType* pos_ids, uint32_t nnz, uint32_t num_qo_heads, uint32_t rope_dim,
    uint32_t no_rope_dim, size_t q_rope_in_stride_n, size_t q_rope_in_stride_h,
    size_t q_nope_in_stride_n, size_t q_nope_in_stride_h, size_t q_rope_out_stride_n,
    size_t q_rope_out_stride_h, size_t q_nope_out_stride_n, size_t q_nope_out_stride_h,
    size_t k_rope_in_stride, size_t k_nope_in_stride, float quant_scale_q, float quant_scale_kv,
    bool interleave, bool enable_pdl = false, cudaStream_t stream = nullptr) {
  DISPATCH_INTERLEAVE(interleave, INTERLEAVE, {
    constexpr uint32_t vec_size = 32 / sizeof(DType);
    uint32_t bdx = (rope_dim + vec_size - 1) / vec_size;
    bdx = std::max(1u, bdx);
    uint32_t num_threads = std::max(128U, bdx);
    uint32_t bdy = std::max(1u, num_threads / bdx);
    uint32_t nblks_x = (nnz + bdy - 1) / bdy;
    uint32_t rope_chunks = 1;
    uint32_t no_rope_chunks = (no_rope_dim + rope_dim - 1) / rope_dim;
    constexpr uint32_t num_kv_heads = 1;
    uint32_t total_blocks_y = num_qo_heads * rope_chunks + num_kv_heads * rope_chunks +
                              num_kv_heads * no_rope_chunks + num_qo_heads * no_rope_chunks;

    dim3 nblks(nblks_x, total_blocks_y);
    dim3 nthrs(bdx, bdy);

    cudaLaunchAttribute attribute[1];
    attribute[0].id = cudaLaunchAttributeProgrammaticStreamSerialization;
    attribute[0].val.programmaticStreamSerializationAllowed = enable_pdl ? 1 : 0;
    cudaLaunchConfig_t config;
    config.gridDim = nblks;
    config.blockDim = nthrs;
    config.stream = stream;
    config.dynamicSmemBytes = 0;
    config.attrs = attribute;
    config.numAttrs = 1;

    auto kernel =
        (RopeVectorPathIsWrong(INTERLEAVE, rope_dim, vec_size) ||
         !RopeVectorsAligned(cos_sin_cache, 0, 0))
            ? RopeQuantizeAppendPagedKVCacheKernel<INTERLEAVE, true, vec_size, /*bdx=*/1, DType,
                                                   RoPEIdType, PagedKVIdType, QuantType,
                                                   paged_kv_mla_t<QuantType, PagedKVIdType>>
            : RopeQuantizeAppendPagedKVCacheKernel<INTERLEAVE, false, vec_size, /*bdx=*/1, DType,
                                                   RoPEIdType, PagedKVIdType, QuantType,
                                                   paged_kv_mla_t<QuantType, PagedKVIdType>>;
    DType* v_in_nullptr = nullptr;
    uint32_t num_kv_heads_1 = 1;
    size_t k_rope_in_stride_h_dup = k_rope_in_stride;
    size_t k_nope_in_stride_h_dup = k_nope_in_stride;
    RopeQuantizeAppendPagedKVCacheParams params;
    params.nnz = nnz;
    params.num_qo_heads = num_qo_heads;
    params.num_kv_heads = 1u;
    params.rope_dim = rope_dim;
    params.no_rope_dim = no_rope_dim;
    params.q_rope_in_stride_n = q_rope_in_stride_n;
    params.q_rope_in_stride_h = q_rope_in_stride_h;
    params.q_nope_in_stride_n = q_nope_in_stride_n;
    params.q_nope_in_stride_h = q_nope_in_stride_h;
    params.q_rope_out_stride_n = q_rope_out_stride_n;
    params.q_rope_out_stride_h = q_rope_out_stride_h;
    params.q_nope_out_stride_n = q_nope_out_stride_n;
    params.q_nope_out_stride_h = q_nope_out_stride_h;
    params.k_rope_in_stride = k_rope_in_stride;
    params.k_rope_in_stride_h = k_rope_in_stride_h_dup;
    params.k_nope_in_stride = k_nope_in_stride;
    params.k_nope_in_stride_h = k_nope_in_stride_h_dup;
    params.v_in_stride = 0;
    params.v_in_stride_h = 0;
    params.quant_scale_q = quant_scale_q;
    params.quant_scale_kv = quant_scale_kv;

    FLASHINFER_CUDA_CALL(cudaLaunchKernelEx(&config, kernel,
                                            // inputs
                                            q_rope_in, k_rope_in, q_nope_in, k_nope_in,
                                            v_in_nullptr,
                                            // q outputs
                                            q_rope_out, q_nope_out,
                                            // cache + indices
                                            paged_kv_mla, batch_indices, positions,
                                            // rope tables
                                            cos_sin_cache, pos_ids,
                                            // params
                                            params));
  });

  return cudaSuccess;
}

template <typename DType, typename IdType>
cudaError_t BatchQKApplyRotaryPosIdsCosSinCache(
    DType* q, DType* k, DType* q_rope, DType* k_rope, float* cos_sin_cache, IdType* pos_ids,
    uint32_t nnz, uint32_t num_qo_heads, uint32_t num_kv_heads, uint32_t rotary_dim,
    uint32_t head_dim, size_t q_stride_n, size_t q_stride_h, size_t k_stride_n, size_t k_stride_h,
    size_t q_rope_stride_n, size_t q_rope_stride_h, size_t k_rope_stride_n, size_t k_rope_stride_h,
    bool interleave, cudaStream_t stream = nullptr) {
  if (head_dim < rotary_dim) {
    std::ostringstream err_msg;
    err_msg << "head_dim (" << head_dim << ") must be >= rotary_dim (" << rotary_dim << ")";
    FLASHINFER_ERROR(err_msg.str());
  }
  if (rotary_dim % 2 != 0) {
    std::ostringstream err_msg;
    err_msg << "rotary_dim (" << rotary_dim << ") must be even";
    FLASHINFER_ERROR(err_msg.str());
  }

  // We have better performance with this kernel with these head_dim instead of RopeQuantize. Its
  // vectors have the same limits on rotary_dim and on tensor alignment.
  if ((head_dim == 64 || head_dim == 128 || head_dim == 256 || head_dim == 512) &&
      !RopeVectorPathIsWrong(interleave, rotary_dim,
                             std::max<uint32_t>(16 / sizeof(DType), head_dim / 32)) &&
      RopeVectorsAligned(q, q_stride_n, q_stride_h) &&
      RopeVectorsAligned(k, k_stride_n, k_stride_h) &&
      RopeVectorsAligned(q_rope, q_rope_stride_n, q_rope_stride_h) &&
      RopeVectorsAligned(k_rope, k_rope_stride_n, k_rope_stride_h) &&
      RopeVectorsAligned(cos_sin_cache, 0, 0)) {
    bool two_heads = false;
    FLASHINFER_CUDA_CALL(RopeUseTwoHeadsPerStep(two_heads));

    DISPATCH_INTERLEAVE(interleave, INTERLEAVE, {
      DISPATCH_HEAD_DIM(head_dim, HEAD_DIM, {
        // operate on 16 Bytes at a time
        constexpr uint32_t vec_size = std::max(16 / sizeof(DType), HEAD_DIM / 32);
        // how many threads needed per head_dim
        constexpr uint32_t bdx = HEAD_DIM / vec_size;
        // how many threads needed per block
        uint32_t num_threads = std::max(128U, bdx);
        // how many tokens can we process in a block
        uint32_t bdy = num_threads / bdx;
        // how many blocks needed to process all tokens
        uint32_t nblks_x = (nnz + bdy - 1) / bdy;
        void* args[] = {(void*)&q,
                        (void*)&k,
                        (void*)&q_rope,
                        (void*)&k_rope,
                        (void*)&cos_sin_cache,
                        (void*)&pos_ids,
                        (void*)&nnz,
                        (void*)&num_qo_heads,
                        (void*)&num_kv_heads,
                        (void*)&rotary_dim,
                        (void*)&q_stride_n,
                        (void*)&q_stride_h,
                        (void*)&k_stride_n,
                        (void*)&k_stride_h,
                        (void*)&q_rope_stride_n,
                        (void*)&q_rope_stride_h,
                        (void*)&k_rope_stride_n,
                        (void*)&k_rope_stride_h};
        auto split_kernel =
            two_heads ? BatchQKApplyRotaryPosIdsCosSinCacheKernel<INTERLEAVE, HEAD_DIM, vec_size,
                                                                  bdx, DType, IdType, 2, true>
                      : BatchQKApplyRotaryPosIdsCosSinCacheKernel<INTERLEAVE, HEAD_DIM, vec_size,
                                                                  bdx, DType, IdType, 1, true>;
        auto unsplit_kernel =
            BatchQKApplyRotaryPosIdsCosSinCacheKernel<INTERLEAVE, HEAD_DIM, vec_size, bdx, DType,
                                                      IdType>;

        uint32_t num_ctas_0 = 0;
        FLASHINFER_CUDA_CALL(RopeResidentCtas(
            two_heads ? (void*)split_kernel : (void*)unsplit_kernel, num_threads, num_ctas_0));

        if ((nnz + bdy - 1) / bdy >= num_ctas_0 || GetRopeLaunchOverride().split > 0) {
          uint32_t split = 1;
          bool use_split_kernel = false;
          FLASHINFER_CUDA_CALL(RopeHeadSplit(nblks_x, (void*)split_kernel, (void*)unsplit_kernel,
                                             num_threads, num_qo_heads, num_kv_heads, two_heads,
                                             split, use_split_kernel));
          dim3 nblks(nblks_x, split);
          dim3 nthrs(bdx, bdy);
          FLASHINFER_CUDA_CALL(
              cudaLaunchKernel(use_split_kernel ? (void*)split_kernel : (void*)unsplit_kernel,
                               nblks, nthrs, args, 0, stream));
        } else {
          dim3 nblks(nblks_x, num_qo_heads + num_kv_heads);
          dim3 nthrs(bdx, bdy);
          auto kernel_1 = BatchQKApplyRotaryPosIdsCosSinCacheHeadParallelismKernel<
              INTERLEAVE, HEAD_DIM, vec_size, bdx, DType, IdType>;
          FLASHINFER_CUDA_CALL(cudaLaunchKernel((void*)kernel_1, nblks, nthrs, args, 0, stream));
        }
      });
    });
    return cudaSuccess;
  }

  const uint32_t rope_dim = rotary_dim;
  const uint32_t no_rope_dim = head_dim - rotary_dim;

  // Route to RopeQuantize kernel
  DType* q_rope_in = q;
  DType* k_rope_in = k;
  DType* q_nope_in = q + rotary_dim;
  DType* k_nope_in = k + rotary_dim;
  DType* q_rope_out = q_rope;
  DType* k_rope_out = k_rope;
  DType* q_nope_out = q_rope + rotary_dim;
  DType* k_nope_out = k_rope + rotary_dim;

  return RopeQuantize<DType, IdType, DType>(
      q_rope_in, k_rope_in, q_nope_in, k_nope_in, q_rope_out, k_rope_out, q_nope_out, k_nope_out,
      cos_sin_cache, pos_ids, nnz, num_qo_heads, num_kv_heads, rope_dim, no_rope_dim, q_stride_n,
      q_stride_h, q_stride_n, q_stride_h, q_rope_stride_n, q_rope_stride_h, q_rope_stride_n,
      q_rope_stride_h, k_stride_n, k_stride_h, k_stride_n, k_stride_h, k_rope_stride_n,
      k_rope_stride_h, k_rope_stride_n, k_rope_stride_h, /*quant_scale_q=*/1.0f,
      /*quant_scale_kv=*/1.0f, interleave, /*enable_pdl=*/false, stream);
}

template <typename DType, typename IdType>
cudaError_t BatchQKApplyRotaryPosIds(
    DType* q, DType* k, DType* q_rope, DType* k_rope, IdType* __restrict__ pos_ids, uint32_t nnz,
    uint32_t num_qo_heads, uint32_t num_kv_heads, uint32_t rotary_dim, uint32_t head_dim,
    size_t q_stride_n, size_t q_stride_h, size_t k_stride_n, size_t k_stride_h,
    size_t q_rope_stride_n, size_t q_rope_stride_h, size_t k_rope_stride_n, size_t k_rope_stride_h,
    bool interleave, float rope_scale, float rope_theta, cudaStream_t stream = nullptr) {
  if (head_dim < rotary_dim) {
    std::ostringstream err_msg;
    err_msg << "head_dim (" << head_dim << ") must be >= rotary_dim (" << rotary_dim << ")";
    FLASHINFER_ERROR(err_msg.str());
  }

  float rope_rcp_scale = 1.0f / rope_scale;
  float rope_rcp_theta = 1.0f / rope_theta;
  float smooth_a = 0.f;
  float smooth_b = 0.f;
  bool two_heads = false;
  FLASHINFER_CUDA_CALL(RopeUseTwoHeadsPerStep(two_heads));

  DISPATCH_INTERLEAVE(interleave, INTERLEAVE, {
    DISPATCH_HEAD_DIM(head_dim, HEAD_DIM, {
      constexpr uint32_t vec_size = std::max(16 / sizeof(DType), HEAD_DIM / 32);
      constexpr uint32_t bdx = HEAD_DIM / vec_size;
      uint32_t num_threads = std::max(128U, bdx);
      uint32_t bdy = num_threads / bdx;
      uint32_t nblks_x = (nnz + bdy - 1) / bdy;

      void* args[] = {(void*)&q,
                      (void*)&k,
                      (void*)&q_rope,
                      (void*)&k_rope,
                      (void*)&pos_ids,
                      (void*)&nnz,
                      (void*)&num_qo_heads,
                      (void*)&num_kv_heads,
                      (void*)&rotary_dim,
                      (void*)&q_stride_n,
                      (void*)&q_stride_h,
                      (void*)&k_stride_n,
                      (void*)&k_stride_h,
                      (void*)&q_rope_stride_n,
                      (void*)&q_rope_stride_h,
                      (void*)&k_rope_stride_n,
                      (void*)&k_rope_stride_h,
                      (void*)&smooth_a,
                      (void*)&smooth_b,
                      (void*)&rope_rcp_scale,
                      (void*)&rope_rcp_theta};
      auto split_kernel = two_heads ? BatchQKApplyRotaryPosIdsKernel<INTERLEAVE, HEAD_DIM, vec_size,
                                                                     bdx, DType, IdType, 2, true>
                                    : BatchQKApplyRotaryPosIdsKernel<INTERLEAVE, HEAD_DIM, vec_size,
                                                                     bdx, DType, IdType, 1, true>;
      auto unsplit_kernel =
          BatchQKApplyRotaryPosIdsKernel<INTERLEAVE, HEAD_DIM, vec_size, bdx, DType, IdType>;

      uint32_t num_ctas_0 = 0;
      FLASHINFER_CUDA_CALL(RopeResidentCtas(two_heads ? (void*)split_kernel : (void*)unsplit_kernel,
                                            num_threads, num_ctas_0));
      if (nblks_x >= num_ctas_0 || GetRopeLaunchOverride().split > 0) {
        uint32_t split = 1;
        bool use_split_kernel = false;
        FLASHINFER_CUDA_CALL(RopeHeadSplit(nblks_x, (void*)split_kernel, (void*)unsplit_kernel,
                                           num_threads, num_qo_heads, num_kv_heads, two_heads,
                                           split, use_split_kernel));
        dim3 nblks(nblks_x, split);
        dim3 nthrs(bdx, bdy);

        FLASHINFER_CUDA_CALL(
            cudaLaunchKernel(use_split_kernel ? (void*)split_kernel : (void*)unsplit_kernel, nblks,
                             nthrs, args, 0, stream));
      } else {
        dim3 nblks(nblks_x, num_qo_heads + num_kv_heads);
        dim3 nthrs(bdx, bdy);
        auto kernel_1 = BatchQKApplyRotaryPosIdsHeadParallelismKernel<INTERLEAVE, HEAD_DIM,
                                                                      vec_size, bdx, DType, IdType>;

        FLASHINFER_CUDA_CALL(cudaLaunchKernel((void*)kernel_1, nblks, nthrs, args, 0, stream));
      }
    });
  });

  return cudaSuccess;
}

template <typename DType, typename IdType>
cudaError_t BatchQKApplyRotary(DType* q, DType* k, DType* q_rope, DType* k_rope,
                               IdType* __restrict__ indptr, IdType* __restrict__ offsets,
                               uint32_t batch_size, uint32_t num_qo_heads, uint32_t num_kv_heads,
                               uint32_t rotary_dim, uint32_t head_dim, size_t q_stride_n,
                               size_t q_stride_h, size_t k_stride_n, size_t k_stride_h,
                               size_t q_rope_stride_n, size_t q_rope_stride_h,
                               size_t k_rope_stride_n, size_t k_rope_stride_h, bool interleave,
                               float rope_scale, float rope_theta, cudaStream_t stream = nullptr,
                               uint32_t nnz = 0) {
  if (head_dim < rotary_dim) {
    std::ostringstream err_msg;
    err_msg << "head_dim (" << head_dim << ") must be >= rotary_dim (" << rotary_dim << ")";
    FLASHINFER_ERROR(err_msg.str());
  }

  float rope_rcp_scale = 1.0f / rope_scale;
  float rope_rcp_theta = 1.0f / rope_theta;
  float smooth_a = 0.f;
  float smooth_b = 0.f;

  DISPATCH_INTERLEAVE(interleave, INTERLEAVE, {
    DISPATCH_HEAD_DIM(head_dim, HEAD_DIM, {
      constexpr uint32_t vec_size = std::max(16 / sizeof(DType), HEAD_DIM / 32);
      constexpr uint32_t bdx = HEAD_DIM / vec_size;
      uint32_t num_threads = std::max(128U, bdx);
      uint32_t bdy = num_threads / bdx;
      auto kernel = BatchQKApplyRotaryKernel<INTERLEAVE, HEAD_DIM, vec_size, bdx, DType, IdType>;
      // Split each (request, head) token walk over gridDim.y; nnz == 0 disables the split.
      const uint32_t nblks_x = batch_size * (num_qo_heads + num_kv_heads);
      uint32_t split = 1;
      if (nnz > 0 && batch_size > 0) {
        uint32_t num_ctas = 0;
        FLASHINFER_CUDA_CALL(RopeResidentCtas((void*)kernel, num_threads, num_ctas));
        const uint32_t mean_iters = ceil_div(ceil_div(nnz, batch_size), bdy);
        split = RopeGridSplit(nblks_x, num_ctas, mean_iters,
                              [&](uint32_t g) { return ceil_div(mean_iters, g); });
      }
      if (GetRopeLaunchOverride().split > 0) split = GetRopeLaunchOverride().split;
      dim3 nblks(nblks_x, split);
      dim3 nthrs(bdx, bdy);
      void* args[] = {(void*)&q,
                      (void*)&k,
                      (void*)&q_rope,
                      (void*)&k_rope,
                      (void*)&indptr,
                      (void*)&offsets,
                      (void*)&batch_size,
                      (void*)&num_qo_heads,
                      (void*)&num_kv_heads,
                      (void*)&rotary_dim,
                      (void*)&q_stride_n,
                      (void*)&q_stride_h,
                      (void*)&k_stride_n,
                      (void*)&k_stride_h,
                      (void*)&q_rope_stride_n,
                      (void*)&q_rope_stride_h,
                      (void*)&k_rope_stride_n,
                      (void*)&k_rope_stride_h,
                      (void*)&smooth_a,
                      (void*)&smooth_b,
                      (void*)&rope_rcp_scale,
                      (void*)&rope_rcp_theta};
      FLASHINFER_CUDA_CALL(cudaLaunchKernel((void*)kernel, nblks, nthrs, args, 0, stream));
    });
  });

  return cudaSuccess;
}

template <typename DType, typename IdType>
cudaError_t BatchQKApplyRotaryInPlace(DType* __restrict__ q, DType* __restrict__ k,
                                      IdType* __restrict__ indptr, IdType* __restrict__ offsets,
                                      uint32_t batch_size, uint32_t num_qo_heads,
                                      uint32_t num_kv_heads, uint32_t rotary_dim, uint32_t head_dim,
                                      size_t q_stride_n, size_t q_stride_h, size_t k_stride_n,
                                      size_t k_stride_h, bool interleave, float rope_scale,
                                      float rope_theta, cudaStream_t stream = nullptr,
                                      uint32_t nnz = 0) {
  return BatchQKApplyRotary<DType, IdType>(
      q, k, q, k, indptr, offsets, batch_size, num_qo_heads, num_kv_heads, rotary_dim, head_dim,
      q_stride_n, q_stride_h, k_stride_n, k_stride_h, q_stride_n, q_stride_h, k_stride_n,
      k_stride_h, interleave, rope_scale, rope_theta, stream, nnz);
}

template <typename DType, typename IdType>
cudaError_t BatchQKApplyLlama31Rotary(
    DType* q, DType* k, DType* q_rope, DType* k_rope, IdType* __restrict__ indptr,
    IdType* __restrict__ offsets, uint32_t batch_size, uint32_t num_qo_heads, uint32_t num_kv_heads,
    uint32_t rotary_dim, uint32_t head_dim, size_t q_stride_n, size_t q_stride_h, size_t k_stride_n,
    size_t k_stride_h, size_t q_rope_stride_n, size_t q_rope_stride_h, size_t k_rope_stride_n,
    size_t k_rope_stride_h, bool interleave, float rope_scale, float rope_theta,
    float low_freq_factor, float high_freq_factor, float old_context_length,
    cudaStream_t stream = nullptr, uint32_t nnz = 0) {
  if (head_dim < rotary_dim) {
    std::ostringstream err_msg;
    err_msg << "head_dim (" << head_dim << ") must be >= rotary_dim (" << rotary_dim << ")";
    FLASHINFER_ERROR(err_msg.str());
  }

  float rope_rcp_scale = 1.0f / rope_scale;
  float rope_rcp_theta = 1.0f / rope_theta;
  float smooth_a = old_context_length / (2 * M_PI * high_freq_factor - 2 * M_PI * low_freq_factor);
  float smooth_b = -1.0f / (high_freq_factor / low_freq_factor - 1.0f);

  DISPATCH_INTERLEAVE(interleave, INTERLEAVE, {
    DISPATCH_HEAD_DIM(head_dim, HEAD_DIM, {
      constexpr uint32_t vec_size = std::max(16 / sizeof(DType), HEAD_DIM / 32);
      constexpr uint32_t bdx = HEAD_DIM / vec_size;
      uint32_t num_threads = std::max(128U, bdx);
      uint32_t bdy = num_threads / bdx;
      auto kernel = BatchQKApplyRotaryKernel<INTERLEAVE, HEAD_DIM, vec_size, bdx, DType, IdType>;
      // Split each (request, head) token walk over gridDim.y; nnz == 0 disables the split.
      const uint32_t nblks_x = batch_size * (num_qo_heads + num_kv_heads);
      uint32_t split = 1;
      if (nnz > 0 && batch_size > 0) {
        uint32_t num_ctas = 0;
        FLASHINFER_CUDA_CALL(RopeResidentCtas((void*)kernel, num_threads, num_ctas));
        const uint32_t mean_iters = ceil_div(ceil_div(nnz, batch_size), bdy);
        split = RopeGridSplit(nblks_x, num_ctas, mean_iters,
                              [&](uint32_t g) { return ceil_div(mean_iters, g); });
      }
      if (GetRopeLaunchOverride().split > 0) split = GetRopeLaunchOverride().split;
      dim3 nblks(nblks_x, split);
      dim3 nthrs(bdx, bdy);
      void* args[] = {(void*)&q,
                      (void*)&k,
                      (void*)&q_rope,
                      (void*)&k_rope,
                      (void*)&indptr,
                      (void*)&offsets,
                      (void*)&batch_size,
                      (void*)&num_qo_heads,
                      (void*)&num_kv_heads,
                      (void*)&rotary_dim,
                      (void*)&q_stride_n,
                      (void*)&q_stride_h,
                      (void*)&k_stride_n,
                      (void*)&k_stride_h,
                      (void*)&q_rope_stride_n,
                      (void*)&q_rope_stride_h,
                      (void*)&k_rope_stride_n,
                      (void*)&k_rope_stride_h,
                      (void*)&smooth_a,
                      (void*)&smooth_b,
                      (void*)&rope_rcp_scale,
                      (void*)&rope_rcp_theta};
      FLASHINFER_CUDA_CALL(cudaLaunchKernel((void*)kernel, nblks, nthrs, args, 0, stream));
    });
  });

  return cudaSuccess;
}

template <typename DType, typename IdType>
cudaError_t BatchQKApplyLlama31RotaryPosIds(
    DType* q, DType* k, DType* q_rope, DType* k_rope, IdType* pos_ids, uint32_t nnz,
    uint32_t num_qo_heads, uint32_t num_kv_heads, uint32_t rotary_dim, uint32_t head_dim,
    size_t q_stride_n, size_t q_stride_h, size_t k_stride_n, size_t k_stride_h,
    size_t q_rope_stride_n, size_t q_rope_stride_h, size_t k_rope_stride_n, size_t k_rope_stride_h,
    bool interleave, float rope_scale, float rope_theta, float low_freq_factor,
    float high_freq_factor, float old_context_length, cudaStream_t stream = nullptr) {
  if (head_dim < rotary_dim) {
    std::ostringstream err_msg;
    err_msg << "head_dim (" << head_dim << ") must be >= rotary_dim (" << rotary_dim << ")";
    FLASHINFER_ERROR(err_msg.str());
  }

  float rope_rcp_scale = 1.0f / rope_scale;
  float rope_rcp_theta = 1.0f / rope_theta;
  float smooth_a = old_context_length / (2 * M_PI * high_freq_factor - 2 * M_PI * low_freq_factor);
  float smooth_b = -1.0f / (high_freq_factor / low_freq_factor - 1.0f);
  bool two_heads = false;
  FLASHINFER_CUDA_CALL(RopeUseTwoHeadsPerStep(two_heads));

  DISPATCH_INTERLEAVE(interleave, INTERLEAVE, {
    DISPATCH_HEAD_DIM(head_dim, HEAD_DIM, {
      constexpr uint32_t vec_size = std::max(16 / sizeof(DType), HEAD_DIM / 32);
      constexpr uint32_t bdx = HEAD_DIM / vec_size;
      uint32_t num_threads = std::max(128U, bdx);
      uint32_t bdy = num_threads / bdx;
      const uint32_t nblks_x = (nnz + bdy - 1) / bdy;
      auto split_kernel = two_heads ? BatchQKApplyRotaryPosIdsKernel<INTERLEAVE, HEAD_DIM, vec_size,
                                                                     bdx, DType, IdType, 2, true>
                                    : BatchQKApplyRotaryPosIdsKernel<INTERLEAVE, HEAD_DIM, vec_size,
                                                                     bdx, DType, IdType, 1, true>;
      auto unsplit_kernel =
          BatchQKApplyRotaryPosIdsKernel<INTERLEAVE, HEAD_DIM, vec_size, bdx, DType, IdType>;
      uint32_t split = 1;
      bool use_split_kernel = false;
      FLASHINFER_CUDA_CALL(RopeHeadSplit(nblks_x, (void*)split_kernel, (void*)unsplit_kernel,
                                         num_threads, num_qo_heads, num_kv_heads, two_heads, split,
                                         use_split_kernel));
      void* kernel = use_split_kernel ? (void*)split_kernel : (void*)unsplit_kernel;
      dim3 nblks(nblks_x, split);
      dim3 nthrs(bdx, bdy);
      void* args[] = {(void*)&q,
                      (void*)&k,
                      (void*)&q_rope,
                      (void*)&k_rope,
                      (void*)&pos_ids,
                      (void*)&nnz,
                      (void*)&num_qo_heads,
                      (void*)&num_kv_heads,
                      (void*)&rotary_dim,
                      (void*)&q_stride_n,
                      (void*)&q_stride_h,
                      (void*)&k_stride_n,
                      (void*)&k_stride_h,
                      (void*)&q_rope_stride_n,
                      (void*)&q_rope_stride_h,
                      (void*)&k_rope_stride_n,
                      (void*)&k_rope_stride_h,
                      (void*)&smooth_a,
                      (void*)&smooth_b,
                      (void*)&rope_rcp_scale,
                      (void*)&rope_rcp_theta};
      FLASHINFER_CUDA_CALL(cudaLaunchKernel((void*)kernel, nblks, nthrs, args, 0, stream));
    });
  });

  return cudaSuccess;
}

}  // namespace flashinfer

#endif  // FLASHINFER_POS_ENC_CUH_
