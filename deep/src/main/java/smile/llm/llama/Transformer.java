/*
 * Copyright (c) 2010-2024 Haifeng Li. All rights reserved.
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
 * along with Smile. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.llm.llama;

import java.util.ArrayList;
import java.util.List;
import org.bytedeco.pytorch.ModuleListImpl;
import smile.deep.layer.EmbeddingLayer;
import smile.deep.layer.LinearLayer;
import smile.deep.layer.LayerBlock;
import smile.deep.layer.RMSNormLayer;
import smile.deep.tensor.Device;
import smile.deep.tensor.Index;
import smile.deep.tensor.ScalarType;
import smile.deep.tensor.Tensor;
import smile.llm.RotaryPositionalEncoding;

/**
 * The Transformer model. It consists of token embeddings, stacked
 * Transformer blocks, and the final output layer. This model can
 * be used for various natural language processing tasks, such as
 * language modeling or text generation.
 *
 * @author Haifeng Li
 */
public class Transformer extends LayerBlock {
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
    final LinearLayer output;
    /** The precomputed cosine and sine frequencies. */
    final Tensor cis;

    /**
     * Constructor.
     * @param args the model configuration parameters.
     * @param device the compute device.
     */
    public Transformer(ModelArgs args, Device device) {
        this.params = args;
        this.vocabSize = params.vocabSize();
        this.numLayers = params.numLayers();
        this.tokEmbeddings = new EmbeddingLayer(params.vocabSize(), params.dim());

        this.layers = new ArrayList<>();
        var moduleList = new ModuleListImpl();
        for (int layerId = 0; layerId < params.numLayers(); layerId++) {
            var block = new TransformerBlock(layerId, params);
            this.layers.add(block);
            moduleList.push_back(block.module);
        }

        this.norm = new RMSNormLayer(params.dim(), params.normEps());
        this.output = new LinearLayer(params.dim(), params.vocabSize(), false);

        // Note that max_seq_len is multiplied by 2.
        this.cis = RotaryPositionalEncoding.computeFreqCis(
                params.dim() / params.numHeads(),
                params.maxSeqLen() * 2,
                params.ropeTheta(),
                params.scaledRope()).to(device);

        module.register_module("layers", moduleList);
        add("tok_embeddings", tokEmbeddings);
        add("norm", norm);
        add("output", output);
        to(device);
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
        Tensor h = tokEmbeddings.forward(tokens);
        Tensor freqs = cis.get(Index.slice(startPos, startPos+seqlen));

        Tensor mask = null;
        if (seqlen > 1) {
            mask = Tensor.full(Float.NEGATIVE_INFINITY, seqlen, seqlen);
            mask.triu_(1);
            // When performing key-value caching, we compute the attention scores
            // only for the new sequence. Thus, the matrix of scores is of size
            // (seqlen, cache_len + seqlen), and the only masked entries are (i, j) for
            // j > cache_len + i, since row i corresponds to token cache_len + i.
            var zeros = Tensor.zeros(seqlen, startPos);
            mask = Tensor.hstack(zeros, mask);
            mask = mask.to(h.dtype());
        }

        for (var layer : layers) {
            h = layer.forward(h, startPos, freqs, mask);
        }

        h = norm.forward(h);
        return output.forward(h).to(ScalarType.Float32);
    }

    @Override
    public Tensor forward(Tensor tokens) {
        return forward(tokens, 0);
    }
}
