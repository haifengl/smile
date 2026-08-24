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
#ifndef FLASHINFER_MATH_CUH_
#define FLASHINFER_MATH_CUH_

#include <cuda_fp16.h>
#include <cuda_runtime.h>

#include <cmath>
#include <cstdint>
#include <cuda/std/limits>

namespace flashinfer {
namespace math {

// log2(e)
constexpr float log2e = 1.44269504088896340736f;

constexpr float loge2 = 0.693147180559945309417f;

// Masked-logit infinity. IEEE -inf (not a finite sentinel like the historical
// -5e4, which misclassified valid logits below it); softmax consumers clamp
// their exponent subtrahends to keep (-inf) - (-inf) from producing NaN.
constexpr float inf = cuda::std::numeric_limits<float>::infinity();

__forceinline__ __device__ half2 uint32_as_half2(uint32_t x) { return *(half2*)&x; }

__forceinline__ __device__ uint32_t half2_as_uint32(half2 x) { return *(uint32_t*)&x; }

/*!
 * \brief Wrapper of PTX ex2.approx instruction, which computes 2^x
 * \param x input
 */
__forceinline__ __device__ float ptx_exp2(float x) {
  float y;
  asm volatile("ex2.approx.ftz.f32 %0, %1;" : "=f"(y) : "f"(x));
  return y;
}

template <typename T>
struct MaxOp {
  __device__ __forceinline__ T operator()(T const& x, T const& y) { return x > y ? x : y; }
};

template <>
struct MaxOp<float> {
  __device__ __forceinline__ float operator()(float const& x, float const& y) {
    return fmaxf(x, y);
  }
};

template <typename T>
struct SumOp {
  __device__ __forceinline__ T operator()(T const& x, T const& y) { return x + y; }
};

__forceinline__ __device__ void add(float2& c, float2 const& a, float2 const& b) {
  c.x = a.x + b.x;
  c.y = a.y + b.y;
}

__forceinline__ __device__ void mul(float2& c, float2 const& a, float2 const& b) {
  c.x = a.x * b.x;
  c.y = a.y * b.y;
}

__forceinline__ __device__ void fma(float2& d, float2 const& a, float2 const& b, float2 const& c) {
  d.x = fmaf(a.x, b.x, c.x);
  d.y = fmaf(a.y, b.y, c.y);
}

__forceinline__ __device__ float exp2_fma_poly(float x) {
  float n = rintf(x);
  float f = x - n;

  float poly = ((0.0771f * f + 0.2276f) * f + 0.6951f) * f + 1.0f;

  int n_int = __float2int_rn(n);
  uint32_t poly_bits = __float_as_uint(poly);
  poly_bits += static_cast<uint32_t>(n_int) << 23;
  return __uint_as_float(poly_bits);
}

__forceinline__ __device__ uint32_t fp32_vec_to_e4m3(float const& f0, float const& f1,
                                                     float const& f2, float const& f3) {
#if defined(__CUDA_ARCH__) && (__CUDA_ARCH__ >= 1000)
  uint32_t val;
  asm volatile(
      "{\n"
      ".reg .b16 lo;\n"
      ".reg .b16 hi;\n"
      "cvt.rn.satfinite.e4m3x2.f32   lo, %2, %1;\n"
      "cvt.rn.satfinite.e4m3x2.f32   hi, %4, %3;\n"
      "mov.b32 %0, {lo, hi};\n"
      "}"
      : "=r"(val)
      : "f"(f0), "f"(f1), "f"(f2), "f"(f3));
  return val;
#elif defined(__CUDA_ARCH__)
  asm volatile("trap;");
  return 0;
#else
  return 0;
#endif
}

__forceinline__ __device__ uint32_t fp32_vec_to_e4m3(float const (&array)[4]) {
  return fp32_vec_to_e4m3(array[0], array[1], array[2], array[3]);
}

__forceinline__ __device__ uint32_t fp32_vec_to_e2m1(float const& f0, float const& f1,
                                                     float const& f2, float const& f3,
                                                     float const& f4, float const& f5,
                                                     float const& f6, float const& f7) {
#if defined(__CUDA_ARCH__) && (__CUDA_ARCH__ >= 1000)
  uint32_t val;
  asm volatile(
      "{\n"
      ".reg .b8 byte0;\n"
      ".reg .b8 byte1;\n"
      ".reg .b8 byte2;\n"
      ".reg .b8 byte3;\n"
      "cvt.rn.satfinite.e2m1x2.f32   byte0, %2, %1;\n"
      "cvt.rn.satfinite.e2m1x2.f32   byte1, %4, %3;\n"
      "cvt.rn.satfinite.e2m1x2.f32   byte2, %6, %5;\n"
      "cvt.rn.satfinite.e2m1x2.f32   byte3, %8, %7;\n"
      "mov.b32 %0, {byte0, byte1, byte2, byte3};\n"
      "}"
      : "=r"(val)
      : "f"(f0), "f"(f1), "f"(f2), "f"(f3), "f"(f4), "f"(f5), "f"(f6), "f"(f7));
  return val;
#elif defined(__CUDA_ARCH__)
  asm volatile("trap;");
  return 0;
#else
  return 0;
#endif
}

__forceinline__ __device__ uint32_t fp32_vec_to_e2m1(float const (&array)[8]) {
  return fp32_vec_to_e2m1(array[0], array[1], array[2], array[3], array[4], array[5], array[6],
                          array[7]);
}

__forceinline__ __device__ uint32_t fp32_vec_to_e2m1(float2 const (&array)[4]) {
  return fp32_vec_to_e2m1(array[0].x, array[0].y, array[1].x, array[1].y, array[2].x, array[2].y,
                          array[3].x, array[3].y);
}

__forceinline__ __device__ uint32_t fp32_vec_to_e2m1(float2 const* array) {
  return fp32_vec_to_e2m1(array[0].x, array[0].y, array[1].x, array[1].y, array[2].x, array[2].y,
                          array[3].x, array[3].y);
}

/*!
 * \brief Wrapper of PTX lg2.approx instruction, which computes log2(x)
 * \param x input
 */
__forceinline__ __device__ float ptx_log2(float x) {
  float y;
  asm volatile("lg2.approx.ftz.f32 %0, %1;" : "=f"(y) : "f"(x));
  return y;
}

/*!
 * \brief Wrapper of PTX ex2.approx.f16x2 instruction, which computes 2^x
 * \param x input
 */
__forceinline__ __device__ half2 ptx_exp2(half2 x) {
  uint32_t y_u32;
  uint32_t x_u32 = half2_as_uint32(x);
  asm volatile("ex2.approx.f16x2 %0, %1;" : "=r"(y_u32) : "r"(x_u32));
  return uint32_as_half2(y_u32);
}

/*!
 * \brief Wrapper of PTX ex2.approx.f16 instruction, which computes 2^x
 * \param x input
 */
__forceinline__ __device__ half ptx_exp2(half x) {
  ushort y_u16;
  asm volatile("ex2.approx.f16 %0, %1;" : "=h"(y_u16) : "h"(__half_as_ushort(x)));
  return __ushort_as_half(y_u16);
}

/*!
 * \brief Wrapper of PTX rcp.approx instruction, which computes 1/x
 * \param x input
 */
__forceinline__ __device__ float ptx_rcp(float x) {
  float y;
  asm volatile("rcp.approx.ftz.f32 %0, %1;" : "=f"(y) : "f"(x));
  return y;
}

/*!
 * \brief Wrapper of PTX shfl.sync.bfly instruction, which performs a butterfly shuffle
 *   between threads in a warp.
 * \param x The value in the source lane
 * \param lane_mask The mask to perform thread index xor with: y[i] <- x[i ^ delta]
 */
__forceinline__ __device__ float shfl_xor_sync(float x, int lane_mask) {
  float y;
  asm volatile("shfl.sync.bfly.b32 %0, %1, %2, 0x1f, 0xffffffff;"
               : "=f"(y)
               : "f"(x), "r"(lane_mask));
  return y;
}

/*!
 * \brief Wrapper of PTX shfl.sync.bfly instruction on half2, which performs a butterfly
 *   shuffle between threads in a warp.
 * \param x The value in the source lane
 * \param lane_mask The mask to perform thread index xor with: y[i] <- x[i ^ lane_mask]
 */
__forceinline__ __device__ half2 shfl_xor_sync(half2 x, int lane_mask) {
  return __shfl_xor_sync(0xffffffff, x, lane_mask);
}

/*!
 * \brief Wrapper of PTX rsqrt approximation instruction, which computes 1/sqrt(x)
 * \param x input
 */
__forceinline__ __device__ float rsqrt(float x) {
  float y;
  asm volatile("rsqrt.approx.ftz.f32 %0, %1;" : "=f"(y) : "f"(x));
  return y;
}

/*!
 * \brief Wrapper of PTX tanh.approx.f32 instruction, which computes tanh(x)
 * \param x input
 */
__forceinline__ __device__ float tanh(float x) {
  float y;
  asm volatile("tanh.approx.f32 %0, %1;" : "=f"(y) : "f"(x));
  return y;
}

/*!
 * \brief Wrapper of PTX tanh.approx.f16x2 instruction, which computes tanh(x)
 * \param x input
 */
__forceinline__ __device__ half2 tanh(half2 x) {
  uint32_t y_u32;
  uint32_t x_u32 = half2_as_uint32(x);
  asm volatile("tanh.approx.f16x2 %0, %1;" : "=r"(y_u32) : "r"(x_u32));
  return uint32_as_half2(y_u32);
}

/*!
 * \brief Wrapper of PTX tanh.approx.f16 instruction, which computes tanh(x)
 * \param x input
 */
__forceinline__ __device__ half tanh(half x) {
  ushort y_u16;
  asm volatile("tanh.approx.f16 %0, %1;" : "=h"(y_u16) : "h"(__half_as_ushort(x)));
  return __ushort_as_half(y_u16);
}

}  // namespace math
}  // namespace flashinfer
#endif  // FLASHINFER_MATH_CUH_
