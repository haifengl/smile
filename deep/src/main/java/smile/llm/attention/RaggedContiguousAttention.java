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

import java.util.ArrayList;
import java.util.List;
import smile.deep.tensor.Index;
import smile.deep.tensor.Tensor;

/**
 * Ragged contiguous self-attention over packed {@code [N, H, D]} Q/K/V tensors.
 *
 * <p>Used when FlashInfer ragged is unavailable (torch_native backend or older
 * {@code libsmile_torch} without {@code smile_flashinfer_ragged_attention}).
 *
 * @author Haifeng Li
 */
public final class RaggedContiguousAttention {
    private static final TorchNativeAttentionKernel SDPA = new TorchNativeAttentionKernel();

    private RaggedContiguousAttention() {}

    /**
     * Runs non-causal (or causal) self-attention independently within each
     * {@code indptr} segment.
     *
     * @param query {@code [N, H, D]} NHD
     * @param key   {@code [N, H, D]}
     * @param value {@code [N, H, D]}
     * @param ctx   ragged context with {@link AttentionContext#raggedIndptr()}
     * @return output {@code [N, H, D]}
     */
    public static Tensor forward(Tensor query, Tensor key, Tensor value, AttentionContext ctx) {
        int[] indptr = ctx.raggedIndptr();
        if (indptr == null || indptr.length < 2) {
            throw new IllegalArgumentException("ragged indptr [B+1] required");
        }
        var contiguous = AttentionContext.contiguous(ctx.scale(), 0.0, ctx.isCausal());
        List<Tensor> outs = new ArrayList<>();
        try {
            for (int s = 0; s < indptr.length - 1; s++) {
                int start = indptr[s];
                int end = indptr[s + 1];
                if (end <= start) {
                    continue;
                }
                try (var span = Index.slice(start, end);
                     Tensor qs = query.get(span).transpose(0, 1).unsqueeze(0);
                     Tensor ks = key.get(span).transpose(0, 1).unsqueeze(0);
                     Tensor vs = value.get(span).transpose(0, 1).unsqueeze(0);
                     Tensor attn = SDPA.forward(qs, ks, vs, null, contiguous)) {
                    long len = end - start;
                    int heads = ctx.numQoHeads();
                    int headDim = ctx.headDim();
                    try (Tensor bhld = attn.reshape(heads, len, headDim);
                         Tensor transposed = bhld.transpose(0, 1).contiguous();
                         Tensor flat = transposed.reshape(len, (long) heads * headDim)) {
                        outs.add(flat.copy());
                    }
                }
            }
            if (outs.isEmpty()) {
                throw new IllegalStateException("ragged attention: no non-empty segments in indptr");
            }
            if (outs.size() == 1) {
                Tensor out = outs.get(0);
                out.promoteToParent();
                return out;
            }
            Tensor cat = Tensor.vstack(outs.toArray(Tensor[]::new));
            for (Tensor t : outs) {
                t.close();
            }
            cat.promoteToParent();
            return cat;
        } catch (RuntimeException e) {
            for (Tensor t : outs) {
                t.close();
            }
            throw e;
        }
    }
}
