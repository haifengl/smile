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
#ifndef FLASHINFER_ATTENTION_PERSISTENT_TEMPLATE_CUH
#define FLASHINFER_ATTENTION_PERSISTENT_TEMPLATE_CUH

#include <cooperative_groups.h>
#include <cuda_runtime.h>

#include <cstdint>

#include "../profiler.cuh"

namespace flashinfer {
namespace cg = cooperative_groups;

// Define profiler event types for persistent kernels
enum class PersistentProfileEventType {
  kRunner1 = 0U,
  kRunner2 = 1U,
  kReduction = 2U,
};

struct ProfilerClosure {
  PROFILER_CLOSURE_PARAMS_DECL
};

// Helper metafunction to find maximum threads among multiple BlockPersistentRunners
template <typename... Runners>
struct max_threads;

template <typename Runner>
struct max_threads<Runner> {
  static constexpr size_t value = Runner::KTraits::NUM_THREADS;
};

template <typename Runner1, typename Runner2, typename... RestRunners>
struct max_threads<Runner1, Runner2, RestRunners...> {
  static constexpr size_t value = Runner1::KTraits::NUM_THREADS > Runner2::KTraits::NUM_THREADS
                                      ? max_threads<Runner1, RestRunners...>::value
                                      : max_threads<Runner2, RestRunners...>::value;
};

// Two runners version
template <class BlockPersistentRunner1, class BlockPersistentRunner2, class BlockReductionRunner>
__global__ __launch_bounds__(
    max_threads<BlockPersistentRunner1, BlockPersistentRunner2, BlockReductionRunner>::
        value) void PersistentKernelTemplate(const __grid_constant__
                                             typename BlockPersistentRunner1::Params params_1,
                                             const __grid_constant__
                                             typename BlockPersistentRunner2::Params params_2) {
  extern __shared__ uint8_t smem[];

#ifdef FLASHINFER_ENABLE_PROFILER
  ProfilerClosure
      profiler_closure;  // no volatile as this is scope.CTA, and only threadIdx == 0 is modifying
  PROFILER_INIT(params_1, smem, profiler_closure, 0, 1, (threadIdx.x == 0));
#endif

  auto& smem_storage_1 =
      reinterpret_cast<typename BlockPersistentRunner1::KTraits::SharedStorage&>(smem);
  auto& smem_storage_2 =
      reinterpret_cast<typename BlockPersistentRunner2::KTraits::SharedStorage&>(smem);
  auto grid = cg::this_grid();

#ifndef FLASHINFER_ENABLE_PROFILER
  BlockPersistentRunner1::Run(params_1, &smem_storage_1);
  BlockPersistentRunner2::Run(params_2, &smem_storage_2);

  grid.sync();
  BlockReductionRunner::Run(params_1.partial_o, params_1.final_o, params_1.partial_lse,
                            params_1.final_lse, *(params_1.num_packed_qo_len),
                            params_1.gqa_group_size, params_1.num_kv_heads, params_1.merge_indptr,
                            params_1.merge_o_indices, smem);
#else
  BlockPersistentRunner1::Run(params_1, &smem_storage_1, profiler_closure);
  BlockPersistentRunner2::Run(params_2, &smem_storage_2, profiler_closure);

  grid.sync();
  BlockReductionRunner::Run(params_1.partial_o, params_1.final_o, params_1.partial_lse,
                            params_1.final_lse, *(params_1.num_packed_qo_len),
                            params_1.gqa_group_size, params_1.num_kv_heads, params_1.merge_indptr,
                            params_1.merge_o_indices, smem, profiler_closure);
#endif
}
}  // namespace flashinfer

#endif  // FLASHINFER_ATTENTION_PERSISTENT_TEMPLATE_CUH
