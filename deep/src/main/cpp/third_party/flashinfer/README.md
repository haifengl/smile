/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * Vendored FlashInfer C++/CUDA headers (Apache License 2.0).
 * Upstream: https://github.com/flashinfer-ai/flashinfer
 * Version:  v0.7.5.post1 (commit 61f84d72101563f78f51598dc4d9d82c24197055), unmodified.
 *
 * Only the transitive include closure of smile_flashinfer_kernels.cu is kept (25 headers under
 * include/flashinfer). To upgrade: check out the new tag, recompute that closure (follow every
 * #include <flashinfer/...> and relative "..." include starting from smile_flashinfer_kernels.cu and
 * smile_flashinfer_cuda.h), copy exactly those files, and update this note.
 *
 * Used for BatchDecodeWithPagedKVCache and BatchPrefill{Paged,Ragged}WithKVCache. The kernels are
 * compiled into libsmile_torch.so; no flashinfer-jit-cache / AOT modules are bundled or loaded.
 */
