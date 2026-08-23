/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * SMILE is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with SMILE. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.llm.attention;

import smile.deep.tensor.Tensor;
import smile.torch.Native;

/**
 * FlashInfer paged BatchPrefill / BatchDecode backend.
 *
 * <p>Decode ({@code S==1}) uses FlashInfer {@code BatchDecodeWithPagedKVCache}
 * (vendored headers). Prefill uses GPU page gather + LibTorch SDPA with the
 * same mask semantics as {@code torch_native}.
 *
 * <p>Ragged contiguous prefill (vision tower) uses
 * {@code BatchPrefillWithRaggedKVCache} when supported, otherwise batched SDPA
 * inside the FlashInfer module.
 *
 * <p>Expects {@link AttentionContext#isPaged()} with CSR metadata and a
 * {@link FlashInferWorkspace}, or {@link AttentionContext#isRaggedContiguous()}
 * with cumulative segment indptr. Query layout {@code [B, H, S, D]} for paged;
 * {@code [N, H, D]} NHD for ragged.
 *
 * @author Haifeng Li
 */
public final class FlashInferAttentionKernel implements AttentionKernel {
    @Override
    public Tensor forward(Tensor query, Tensor key, Tensor value, Tensor mask, AttentionContext ctx) {
        if (ctx != null && ctx.isRaggedContiguous()) {
            return Native.flashInferRaggedAttention(query, key, value, mask, ctx);
        }
        if (ctx == null || !ctx.isPaged()) {
            throw new IllegalStateException(
                    "FlashInfer requires paged or ragged contiguous AttentionContext");
        }
        if (ctx.workspace() == null) {
            throw new IllegalStateException("FlashInfer workspace not installed");
        }
        // key/value are unused — KV lives in the pool.
        return Native.flashInferAttention(query, mask, ctx);
    }

    @Override
    public AttentionBackend backend() {
        return AttentionBackend.FLASHINFER;
    }
}
