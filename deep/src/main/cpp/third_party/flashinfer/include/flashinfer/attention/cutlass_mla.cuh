/*
 * Copyright (c) 2024 by FlashInfer team.
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
#ifndef FLASHINFER_ATTENTION_CUTLASS_MLA_CUH_
#define FLASHINFER_ATTENTION_CUTLASS_MLA_CUH_
#include <sstream>

#include "../cutlass_utils.cuh"
#include "../exception.h"
#include "cutlass/kernel_hardware_info.h"

// From 3rdparty/cutlass/examples/77_blackwell_fmha
#include "blackwell/device/sm100_mla.hpp"
#include "blackwell/kernel/sm100_mla_tile_scheduler.hpp"

namespace flashinfer {

namespace attention {

using namespace cute;
using namespace cutlass::fmha::kernel;

template <bool v>
struct IsPersistent {
  static const bool value = v;
};

template <typename T, typename TOut = T, typename PersistenceOption = IsPersistent<true>>
struct MlaSm100 {
  using Element = T;
  using ElementAcc = float;
  using ElementOut = TOut;

  using TileShape = Shape<_128, _128, Shape<_512, _64>>;
  using TileShapeH = cute::tuple_element_t<0, TileShape>;
  using TileShapeD = cute::tuple_element_t<2, TileShape>;

  // H K (D_latent D_rope) B
  using ProblemShape = cute::tuple<TileShapeH, int, TileShapeD, int>;

  using StrideQ = cute::tuple<int64_t, _1, int64_t>;  // H D B
  using StrideK = cute::tuple<int64_t, _1, int64_t>;  // K D B
  using StrideO = StrideK;                            // H D B
  using StrideLSE = cute::tuple<_1, int>;             // H B

  using TileScheduler =
      std::conditional_t<PersistenceOption::value, Sm100MlaPersistentTileScheduler,
                         Sm100MlaIndividualTileScheduler>;

  using FmhaKernel = cutlass::fmha::kernel::Sm100FmhaMlaKernelTmaWarpspecialized<
      TileShape, Element, ElementAcc, ElementOut, ElementAcc, TileScheduler, /*kIsCpAsync=*/true>;
  using Fmha = cutlass::fmha::device::MLA<FmhaKernel>;
};

template <typename T>
typename T::Fmha::Arguments args_from_options(void* out_ptr, void* lse_ptr, void* q_absorbed_ptr,
                                              void* ckv_kpe_cache_ptr, void* seq_lens_ptr,
                                              void* page_table_ptr, int batches,
                                              int page_count_per_seq, int page_count_total,
                                              int page_size, int device_index,
                                              float inv_o_scale = 1.0f) {
  cutlass::KernelHardwareInfo hw_info;
  hw_info.device_id = device_index;
  hw_info.sm_count =
      cutlass::KernelHardwareInfo::query_device_multiprocessor_count(hw_info.device_id);

  int max_seq_len = page_size * page_count_per_seq;
  using TileShapeH = typename T::TileShapeH;
  using TileShapeD = typename T::TileShapeD;
  auto problem_shape = cute::make_tuple(TileShapeH{}, max_seq_len, TileShapeD{}, batches);

  auto [H, K, D, B] = problem_shape;
  auto [D_latent, D_rope] = D;

  // the scale is based on the non-absorbed sizes, change as appropriate
  // we can't determine this parameter from the info we have, it's an input
  int D_non_latent = 128;
  float scale = 1.0 / sqrt(1.0 * (D_non_latent + D_rope));

  using StrideQ = typename T::StrideQ;
  using StrideK = typename T::StrideK;
  using StrideO = typename T::StrideO;
  using StrideLSE = typename T::StrideLSE;

  StrideQ stride_Q = cute::make_tuple(static_cast<int64_t>(0 + D_latent + D_rope), _1{},
                                      static_cast<int64_t>(H * (0 + D_latent + D_rope)));
  StrideK stride_C = cute::make_tuple(static_cast<int64_t>(0 + D_latent + D_rope), _1{},
                                      static_cast<int64_t>(page_size * (D_latent + D_rope)));
  StrideLSE stride_PT = cute::make_stride(_1{}, page_count_per_seq);
  StrideLSE stride_LSE = cute::make_tuple(_1{}, 0 + H);
  StrideO stride_O = cute::make_tuple(static_cast<int64_t>(0 + D_latent), _1{},
                                      static_cast<int64_t>(0 + H * D_latent));

  using Element = typename T::Element;
  using ElementOut = typename T::ElementOut;
  using ElementAcc = typename T::ElementAcc;
  auto Q_ptr = reinterpret_cast<Element*>(q_absorbed_ptr);
  auto C_ptr = reinterpret_cast<Element*>(ckv_kpe_cache_ptr);
  typename T::Fmha::Arguments arguments{
      problem_shape,
      {scale, Q_ptr, stride_Q, Q_ptr + D_latent, stride_Q, C_ptr, stride_C, C_ptr + D_latent,
       stride_C, reinterpret_cast<int*>(seq_lens_ptr), reinterpret_cast<int*>(page_table_ptr),
       stride_PT, page_count_total, page_size},
      {reinterpret_cast<ElementOut*>(out_ptr), stride_O,
       // static_cast<ElementAcc*>(lse.data_ptr()), stride_LSE},
       static_cast<ElementAcc*>(nullptr), stride_LSE, inv_o_scale},
      hw_info,
      -1,       // split_kv
      nullptr,  // is_var_split_kv=false
  };
  // TODO(kaixih@nvidia): When split_kv=-1 and is_var_split_kv=false, we compute
  // split_kv automatically based on batch size and sequence length to balance
  // workload across available SMs. Consider using var_split_kv for manual
  // control if needed.
  T::Fmha::set_split_kv(arguments);
  return arguments;
}

template <typename Element, typename ElementOut = Element>
cudaError_t runMla(void* workspace_ptr, void* out_ptr, void* lse_ptr, void* q_absorbed_ptr,
                   void* ckv_kpe_cache_ptr, void* seq_lens_ptr, void* page_table_ptr, int batches,
                   int page_count_per_seq, int page_count_total, int page_size, int device_index,
                   cudaStream_t stream, float inv_o_scale = 1.0f) {
  using MlaSm100Type = MlaSm100<Element, ElementOut>;
  typename MlaSm100Type::Fmha fmha;
  auto arguments = args_from_options<MlaSm100Type>(
      out_ptr, lse_ptr, q_absorbed_ptr, ckv_kpe_cache_ptr, seq_lens_ptr, page_table_ptr, batches,
      page_count_per_seq, page_count_total, page_size, device_index, inv_o_scale);

  CUTLASS_CHECK(fmha.can_implement(arguments));

  CUTLASS_CHECK(fmha.initialize(arguments, workspace_ptr, stream));

  CUTLASS_CHECK(fmha.run(arguments, workspace_ptr, stream));

  return cudaSuccess;
}

}  // namespace attention

}  // namespace flashinfer
#endif  // FLASHINFER_ATTENTION_CUTLASS_MLA_CUH_
