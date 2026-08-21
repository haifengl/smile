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
 * <p>Expects {@link AttentionContext#isPaged()} with CSR metadata and a
 * {@link FlashInferWorkspace}. Query layout {@code [B, H, S, D]}.
 *
 * @author Haifeng Li
 */
public final class FlashInferAttentionKernel implements AttentionKernel {
    @Override
    public Tensor forward(Tensor query, Tensor key, Tensor value, Tensor mask, AttentionContext ctx) {
        if (ctx == null || !ctx.isPaged()) {
            throw new IllegalArgumentException("FlashInfer requires paged AttentionContext");
        }
        if (ctx.workspace() == null) {
            throw new IllegalStateException("FlashInfer workspace not installed");
        }
        // key/value are unused — KV lives in the pool; mask unused (causal flag).
        return Native.flashInferAttention(query, ctx);
    }

    @Override
    public AttentionBackend backend() {
        return AttentionBackend.FLASHINFER;
    }
}
