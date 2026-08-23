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

import java.lang.foreign.MemorySegment;
import smile.deep.tensor.Tensor;

import static smile.torch.smile_torch_h.smile_torch_scaled_dot_product_attention;

/**
 * LibTorch {@code scaled_dot_product_attention} backend.
 *
 * @author Haifeng Li
 */
public final class TorchNativeAttentionKernel implements AttentionKernel {
    @Override
    public Tensor forward(Tensor query, Tensor key, Tensor value, Tensor mask, AttentionContext ctx) {
        if (query == null || key == null || value == null) {
            throw new IllegalArgumentException("torch_native requires contiguous query, key, and value");
        }
        if (ctx != null && ctx.isRaggedContiguous()) {
            return RaggedContiguousAttention.forward(query, key, value, ctx);
        }
        double scale = ctx != null ? ctx.scale() : 0.0;
        double dropout = ctx != null ? ctx.dropout() : 0.0;
        boolean isCausal = ctx != null && ctx.isCausal();
        var handle = smile_torch_scaled_dot_product_attention(
                query.handle(), key.handle(), value.handle(),
                mask == null ? MemorySegment.NULL : mask.handle(),
                dropout, isCausal ? 1 : 0, scale > 0 ? 1 : 0, scale);
        return new Tensor(handle);
    }

    @Override
    public AttentionBackend backend() {
        return AttentionBackend.TORCH_NATIVE;
    }
}
