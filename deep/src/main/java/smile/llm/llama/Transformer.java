/*
 * Copyright (c) 2010-2021 Haifeng Li. All rights reserved.
 *
 * Smile is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Smile is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Smile.  If not, see <https://www.gnu.org/licenses/>.
 */
package smile.llm.llama;

import java.util.ArrayList;
import java.util.List;
import smile.deep.layer.EmbeddingLayer;
import smile.deep.layer.FullyConnectedLayer;
import smile.deep.layer.RMSNormLayer;
import smile.deep.tensor.Index;
import smile.deep.tensor.Tensor;
import smile.llm.RotaryPositionalEncoding;
import smile.util.AutoScope;

/**
 * The Transformer model. It consists of token embeddings, stacked
 * Transformer blocks, and the final output layer. This model can
 * be used for various natural language processing tasks, such as
 * language modeling or text generation.
 *
 * @author Haifeng Li
 */
public class Transformer {
    /** The model configuration parameters. */
    final ModelArgs params;
    /** The vocabulary size. */
    final int vocabSize;
    /** The number of transformer blocks. */
    final int numLayers;
    /** Token embeddings. */
    final EmbeddingLayer tokEmbeddings;
    /** Transformer blocks. */
    final List<TransformerBlock> layers;
    /** The layer normalization for the model output. */
    final RMSNormLayer norm;
    /** The linear layer for final output. */
    final FullyConnectedLayer output;
    /** The precomputed cosine and sine frequencies. */
    final Tensor cis;

    /**
     * Constructor.
     * @param args the model configuration parameters.
     */
    public Transformer(ModelArgs args) {
        this.params = args;
        this.vocabSize = params.vocabSize();
        this.numLayers = params.numLayers();

        this.tokEmbeddings = new EmbeddingLayer(params.vocabSize(), params.dim());

        this.layers = new ArrayList<>();
        for (int layerId = 0; layerId < params.numLayers(); layerId++) {
            this.layers.add(new TransformerBlock(layerId, params));
        }

        this.norm = new RMSNormLayer(params.dim(), params.normEps());
        this.output = new FullyConnectedLayer(params.dim(), params.vocabSize(), false);

        // Note that max_seq_len is multiplied by 2 because the token limit
        // for the Llama 2 generation of models is 4096.
        // Adding this multiplier instead of using 4096 directly allows for
        // dynamism of token lengths while training or fine-tuning.
        this.cis = RotaryPositionalEncoding.computeFreqCis(
                params.dim() / params.numHeads(),
                params.maxSeqLength() * 2,
                params.ropeTheta());
    }

    /**
     * Forward pass through the model.
     * @param tokens the input token indices.
     * @param startPos the starting position for attention caching.
     * @return the output tensor.
     */
    public Tensor forward(Tensor tokens, int startPos) {
        long[] shape = tokens.shape();
        int seqlen = (int) shape[1];
        try (var scope = new AutoScope();
             Tensor freqs = cis.get(Index.slice(startPos, startPos+seqlen))) {
            Tensor mask = null;
            if (seqlen > 1) {
                mask = Tensor.full(Float.NEGATIVE_INFINITY, 1, 1, seqlen, seqlen);
                mask = mask.triu(startPos + 1);
            }
            Tensor h = scope.add(tokEmbeddings.forward(tokens));
            for (var layer : layers) {
                h = scope.add(layer.forward(h, startPos, freqs, mask));
            }
            h = scope.add(norm.forward(h));
            return output.forward(h);
        }
    }
}
