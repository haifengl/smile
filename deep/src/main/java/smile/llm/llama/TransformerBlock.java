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
package smile.llm.llama;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import smile.deep.layer.RMSNormLayer;
import smile.torch.Native;
import smile.deep.tensor.Tensor;

import static smile.torch.Native.check;
import static smile.torch.smile_torch_h.smile_module_create;
import static smile.torch.smile_torch_h.smile_module_free;
import static smile.torch.smile_torch_h.smile_module_register_module;

/**
 * A block in Transformer model. It consists of an attention mechanism
 * followed by a feedforward neural network. This module can be stacked
 * multiple times to create a complete Transformer model.
 *
 * @author Haifeng Li
 */
public class TransformerBlock {
    /** The id of layer block. */
    final int layerId;
    /** The number of attention heads. */
    final int numHeads;
    /** The dimension of token embedding. */
    final int dim;
    /** The dimension of each attention head. */
    final int headDim;
    /** The attention module. */
    final Attention attention;
    /** the feed forward module. */
    final FeedForward feedForward;
    /** The layer normalization for attention output. */
    final RMSNormLayer attentionNorm;
    /** The layer normalization for feed forward output. */
    final RMSNormLayer ffnNorm;
    /** PyTorch module. */
    final MemorySegment module;

    /**
     * Constructor.
     * @param layerId the identifier of the block.
     * @param args the model configuration parameters.
     */
    public TransformerBlock(int layerId, ModelArgs args) {
        this.layerId = layerId;
        this.numHeads = args.numHeads();
        this.dim = args.dim();
        this.headDim = args.dim() / args.numHeads();
        this.attention = new Attention(args);
        this.feedForward = new FeedForward(
                args.dim(),
                4 * args.dim(),
                args.multipleOf(),
                args.ffnDimMultiplier()
        );
        this.attentionNorm = new RMSNormLayer(args.dim(), args.normEps());
        this.ffnNorm = new RMSNormLayer(args.dim(), args.normEps());

        try (Arena arena = Arena.ofConfined()) {
            this.module = check(smile_module_create(MemorySegment.NULL));
            smile_module_register_module(module, arena.allocateFrom("attention"), attention.module);
            smile_module_register_module(module, arena.allocateFrom("feed_forward"), feedForward.module);
            smile_module_register_module(module, arena.allocateFrom("attention_norm"), attentionNorm.module());
            smile_module_register_module(module, arena.allocateFrom("ffn_norm"), ffnNorm.module());
        }
        MemorySegment m = this.module;
        Native.CLEANER.register(this, () -> smile_module_free(m));
    }

    /**
     * Forward pass through the block.
     * @param x the input tensor.
     * @param startPos the starting position for attention caching.
     * @param cis the precomputed frequency tensor.
     * @param mask the attention mask tensor.
     * @return the output tensor.
     */
    public Tensor forward(Tensor x, int startPos, Tensor cis, Tensor mask) {
        try (Tensor anorm = attentionNorm.forward(x);
             Tensor ax = attention.forward(anorm, startPos, cis, mask);
             Tensor h = x.add(ax);
             Tensor fnorm = ffnNorm.forward(h);
             Tensor fx = feedForward.forward(fnorm)) {
            return h.add(fx);
        }
    }
}
