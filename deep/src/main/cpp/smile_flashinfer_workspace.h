/*
 * Cross-TU accessor for FlashInfer pooled workspace tensors.
 */
#pragma once

#include "smile_torch.h"

#include <ATen/ATen.h>

/**
 * Fills pointers to the pooled workspace tensors owned by {@code ws}.
 * @return 0 on success, -1 if {@code ws} is null.
 */
int smile_flashinfer_workspace_get_tensors(
        ST_FlashInferWorkspace ws,
        at::Tensor **float_ws,
        at::Tensor **int_ws,
        at::Tensor **pinned_ws);

/** @return address of the workspace runtime-cache slot (may be null). */
void **smile_flashinfer_workspace_runtime_cache_slot(ST_FlashInferWorkspace ws);
