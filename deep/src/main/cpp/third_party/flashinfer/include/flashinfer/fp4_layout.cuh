/*
 * Copyright (c) 2022-2024, NVIDIA CORPORATION.  All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#pragma once

namespace flashinfer {

enum class QuantizationSFLayout {
  // Block scale factors are stored in swizzled layout for cutlass FP4 kernel. Scale factor
  // blocks are organized in 512-byte blocks in global memory, with each block having 128x4 FP8
  // values. The SF matrix dimensions are therefore padded - rows to the nearest multiple of 128 and
  // columns to the nearest multiple of 4.
  //
  // The scale factor block rows map to data block rows in an interleaved pattern:
  // For a scale factor row 'i', it maps to data block row: (i % 4) * 32 + (i / 4)
  // Column 'j' in the scale factor block corresponds to scaling the j-th block in the data tensor.
  SWIZZLED_128x4,
  SWIZZLED_8x4,

  // Block scale factors are stored in linear layout (row-major). This is used in some trtllm-gen
  // kernels standard.
  LINEAR
};
};
