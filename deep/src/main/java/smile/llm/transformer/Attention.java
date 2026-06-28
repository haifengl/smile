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
package smile.llm.transformer;

import java.lang.foreign.MemorySegment;
import smile.deep.tensor.Index;
import smile.deep.tensor.Tensor;
import static smile.torch.smile_torch_h.smile_torch_scaled_dot_product_attention;

/**
 * Multi-head attention. Multi-head attention is a core component of
 * the Transformer deep learning architecture. It allows AI models to
 * analyze data simultaneously from multiple representation subspaces,
 * improving their ability to capture complex, varied relationships
 * between words or tokens in a sequence.
 * <p>
 * Instead of performing a single attention function, multi-head attention
 * linearly projects queries, keys, and values into multiple smaller
 * dimensions. These projections are processed in parallel by distinct
 * attention heads.
 *
 * @author Haifeng Li
 */
public interface Attention {
    /**
     * Forward pass through the attention module.
     * @param x the input tensor.
     * @param startPos the starting position for attention caching.
     * @param cis the precomputed frequency tensor.
     * @param mask the attention mask tensor.
     * @return the output tensor.
     */
    Tensor forward(Tensor x, int startPos, Tensor cis, Tensor mask);

    /**
     * Returns pytorch module.
     * @return pytorch module.
     */
    MemorySegment module();

    /**
     * Computes the scaled dot product attention on query, key and value tensors, using
     * an optional attention mask if passed, and applying dropout if a probability
     * greater than 0.0 is specified.
     * @param query the query tensor.
     * @param key the key tensor.
     * @param value the value tensor.
     * @param mask the attention mask.
     * @return the attention output.
     */
    default Tensor apply(Tensor query, Tensor key, Tensor value, Tensor mask) {
        return apply(query, key, value, mask, 0.0, false, 0.0);
    }

    /**
     * Computes the scaled dot product attention on query, key and value tensors, using
     * an optional attention mask if passed, and applying dropout if a probability
     * greater than 0.0 is specified.
     * @param query the query tensor.
     * @param key the key tensor.
     * @param value the value tensor.
     * @param mask the attention mask.
     * @param dropout the dropout probability; if greater than 0.0, dropout is applied.
     * @param isCausal If set to true, the attention masking is a lower triangular
     *                 matrix when the mask is a square matrix. The attention masking
     *                 has the form of the upper left causal bias due to the alignment
     *                 when the mask is a non-square matrix. An error is thrown if both
     *                 attn_mask and is_causal are set.
     * @param scale Optional scaling factor applied prior to softmax. If <= 0, the standard
     *              scaling factor is used.
     * @return the attention output.
     */
    default Tensor apply(Tensor query, Tensor key, Tensor value, Tensor mask,
                         double dropout, boolean isCausal, double scale) {
        var handle = smile_torch_scaled_dot_product_attention(query.handle(), key.handle(), value.handle(),
                mask == null ? MemorySegment.NULL : mask.handle(),
                dropout, isCausal ? 1 : 0, scale > 0 ? 1 : 0, scale);
        return new Tensor(handle);
    }

    /**
     * Efficiently repeat a tensor.
     * @param input the input tensor to repeat.
     * @param numRep the number of times to repeat.
     * @return the repeated tensor.
     */
    default Tensor repeatKV(Tensor input, int numRep) {
        if (numRep == 1) {
            return input;
        } else {
            long[] shape = input.shape();
            long batchSize = shape[0];
            long seqlen = shape[1];
            long numKvHeads = shape[2];
            long headDim = shape[3];
            try (var x = input.get(Index.Colon, Index.Colon, Index.Colon, Index.None, Index.Colon)) {
                return x.expand(batchSize, seqlen, numKvHeads, numRep, headDim)
                        .reshape(batchSize, seqlen, numKvHeads * numRep, headDim);
            }
        }
    }
}
