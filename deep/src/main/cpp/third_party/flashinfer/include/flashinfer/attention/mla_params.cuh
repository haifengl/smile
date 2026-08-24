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
#ifndef FLASHINFER_MLA_PARAMS_CUH_
#define FLASHINFER_MLA_PARAMS_CUH_
#include <cuda.h>

#include "../fastdiv.cuh"
#include "../profiler.cuh"

namespace flashinfer {

template <typename DTypeQ_, typename DTypeKV_, typename DTypeO_, typename IdType_>
struct MLAParams {
  using DTypeQ = DTypeQ_;
  using DTypeKV = DTypeKV_;
  using DTypeO = DTypeO_;
  using IdType = IdType_;

  DTypeQ* q_nope;
  DTypeQ* q_pe;
  DTypeKV* ckv;
  DTypeKV* kpe;
  DTypeO* partial_o;
  float* partial_lse;
  DTypeO* final_o;
  float* final_lse;

  IdType* q_indptr;
  IdType* kv_indptr;
  IdType* partial_indptr;
  IdType* merge_packed_offset_start;
  IdType* merge_packed_offset_end;
  IdType* merge_partial_packed_offset_start;
  IdType* merge_partial_packed_offset_end;
  IdType* merge_partial_stride;
  IdType* kv_indices;
  IdType* q_len;
  IdType* kv_len;
  IdType* q_start;
  IdType* kv_start;
  IdType* kv_end;
  IdType* work_indptr;

  PROFILER_PARAMS_DECL

  uint_fastdiv block_size;
  uint_fastdiv num_heads;

  uint32_t q_nope_stride_n;
  uint32_t q_nope_stride_h;
  uint32_t q_pe_stride_n;
  uint32_t q_pe_stride_h;
  uint32_t ckv_stride_page;
  uint32_t ckv_stride_n;
  uint32_t kpe_stride_page;
  uint32_t kpe_stride_n;
  uint32_t o_stride_n;
  uint32_t o_stride_h;

  float sm_scale;
  // Per-tensor symmetric quantization scales for the FP8 KV cache path.
  // Both default to 1.0 and have no effect on the BF16/FP16 KV path.
  // dequantized = ckv_scale * static_cast<float>(ckv_fp8); same for kpe.
  float ckv_scale = 1.f;
  float kpe_scale = 1.f;
  // Row-major [token][128-element group] FP32 scales override ckv_scale.
  const float* ckv_scale_arr = nullptr;
  bool return_lse_base_on_e;
};

};  // namespace flashinfer

#endif  // FLASHINFER_MLA_PARAMS_CUH_
