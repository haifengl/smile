/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE Marlin C ABI — wraps IST-DASLab Marlin (Apache-2.0) for Ampere/Ada
 * FP16×INT4 weight-only GEMM failover.
 */
#pragma once

#include "smile_torch.h"

#ifdef __cplusplus
extern "C" {
#endif

/** Returns 1 when Marlin was compiled in (USE_MARLIN + CUDA). */
SMILE_API int smile_marlin_available(void);

/**
 * Marlin mul: C = A @ B_marlin with group scales.
 * A: FP16 [M,K], B: Marlin INT4 packed, scales: FP16, workspace: int.
 * Returns a new FP16 [M,N] tensor, or NULL on error.
 */
SMILE_API ST_Tensor smile_marlin_mul(ST_Tensor a, ST_Tensor b, ST_Tensor scales,
                                     ST_Tensor workspace, int thread_k);

#ifdef __cplusplus
}
#endif
