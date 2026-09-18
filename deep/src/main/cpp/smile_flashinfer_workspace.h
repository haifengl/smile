/*
 * Cross-TU accessor for FlashInfer pooled workspace tensors.
 */
#pragma once

#include "smile_torch.h"

#include <ATen/ATen.h>

/**
 * Fills pointers to the pooled workspace tensors owned by {@code ws}, shared
 * by decode's {@code DecodePlan} and ordinary (non-graph) prefill. Do not use
 * for the verify-graph path — see {@link #smile_flashinfer_workspace_get_verify_tensors}.
 * @return 0 on success, -1 if {@code ws} is null.
 */
int smile_flashinfer_workspace_get_tensors(
        ST_FlashInferWorkspace ws,
        at::Tensor **float_ws,
        at::Tensor **int_ws,
        at::Tensor **pinned_ws);

/**
 * Fills pointers to the verify-graph-dedicated workspace tensors owned by
 * {@code ws}. Deliberately a separate scratch region from
 * {@link #smile_flashinfer_workspace_get_tensors}: FlashInfer's {@code
 * PrefillPlan}/{@code DecodePlan} write scheduling metadata directly into
 * workspace bytes at offsets baked into the returned {@code PlanInfo}, and a
 * captured {@code SMILE_DECODE_CUDA_GRAPH} graph's replay reads decode's plan
 * data back from those same bytes on every replay — sharing one scratch
 * region between decode's plan and verify's eager {@code PrefillPlan} calls
 * lets verify silently clobber a live decode graph's scheduling data between
 * captures (reproduced as an illegal-memory-access / ncclAllReduce crash a
 * few dozen rounds into a run with both features enabled).
 * @return 0 on success, -1 if {@code ws} is null.
 */
int smile_flashinfer_workspace_get_verify_tensors(
        ST_FlashInferWorkspace ws,
        at::Tensor **float_ws,
        at::Tensor **int_ws,
        at::Tensor **pinned_ws);

/** @return address of the workspace runtime-cache slot (may be null). */
void **smile_flashinfer_workspace_runtime_cache_slot(ST_FlashInferWorkspace ws);
