#ifndef FLASHINFER_TOPK_COMMON_CUH_
#define FLASHINFER_TOPK_COMMON_CUH_

#include <cuda_bf16.h>
#include <cuda_fp16.h>

#include <cstdint>
#include <cstdlib>
#include <cuda/std/limits>
#include <numeric>
#include <type_traits>

namespace flashinfer {
namespace sampling {

// ============================================================================
// RadixTopK Type Traits - supports float, half, and bfloat16
// OrderedType: uint32_t for float, uint16_t for half/bf16
// NUM_ROUNDS is computed as: sizeof(OrderedType) * 8 / RADIX_BITS
// ============================================================================
template <typename DType>
struct RadixTopKTraits;

// Specialization for float (32-bit)
template <>
struct RadixTopKTraits<float> {
  using OrderedType = uint32_t;

  // Compute number of rounds based on radix bits (not hardcoded)
  template <uint32_t RADIX_BITS>
  static __host__ __device__ constexpr uint32_t num_rounds() {
    return sizeof(OrderedType) * 8 / RADIX_BITS;
  }

  __device__ __forceinline__ static OrderedType ToOrdered(float val) {
    uint32_t bits = __float_as_uint(val);
    // For descending order: flip all bits if negative, else flip sign bit
    return (bits & 0x80000000) ? ~bits : (bits ^ 0x80000000);
  }

  __device__ __forceinline__ static float FromOrdered(OrderedType ordered) {
    uint32_t bits = (ordered & 0x80000000) ? (ordered ^ 0x80000000) : ~ordered;
    return __uint_as_float(bits);
  }

  __device__ __forceinline__ static float NegInf() {
    return -cuda::std::numeric_limits<float>::infinity();
  }
};

// Specialization for half (16-bit)
template <>
struct RadixTopKTraits<half> {
  using OrderedType = uint16_t;

  template <uint32_t RADIX_BITS>
  static __host__ __device__ constexpr uint32_t num_rounds() {
    return sizeof(OrderedType) * 8 / RADIX_BITS;
  }

  __device__ __forceinline__ static OrderedType ToOrdered(half val) {
    uint16_t bits = __half_as_ushort(val);
    return (bits & 0x8000) ? static_cast<uint16_t>(~bits) : static_cast<uint16_t>(bits ^ 0x8000);
  }

  __device__ __forceinline__ static half FromOrdered(OrderedType ordered) {
    uint16_t bits = (ordered & 0x8000) ? static_cast<uint16_t>(ordered ^ 0x8000)
                                       : static_cast<uint16_t>(~ordered);
    return __ushort_as_half(bits);
  }

  __device__ __forceinline__ static half NegInf() {
    return __ushort_as_half(static_cast<uint16_t>(0xFC00));  // -inf in fp16
  }
};

// Specialization for nv_bfloat16 (16-bit)
template <>
struct RadixTopKTraits<nv_bfloat16> {
  using OrderedType = uint16_t;

  template <uint32_t RADIX_BITS>
  static __host__ __device__ constexpr uint32_t num_rounds() {
    return sizeof(OrderedType) * 8 / RADIX_BITS;
  }

  __device__ __forceinline__ static OrderedType ToOrdered(nv_bfloat16 val) {
    uint16_t bits = __bfloat16_as_ushort(val);
    return (bits & 0x8000) ? static_cast<uint16_t>(~bits) : static_cast<uint16_t>(bits ^ 0x8000);
  }

  __device__ __forceinline__ static nv_bfloat16 FromOrdered(OrderedType ordered) {
    uint16_t bits = (ordered & 0x8000) ? static_cast<uint16_t>(ordered ^ 0x8000)
                                       : static_cast<uint16_t>(~ordered);
    return __ushort_as_bfloat16(bits);
  }

  __device__ __forceinline__ static nv_bfloat16 NegInf() {
    return __ushort_as_bfloat16(static_cast<uint16_t>(0xFF80));  // -inf in bf16
  }
};

}  // namespace sampling
}  // namespace flashinfer

#endif  // FLASHINFER_TOPK_COMMON_CUH_
