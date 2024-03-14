/*
 * Copyright (c) 2010-2024 Haifeng Li. All rights reserved.
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
package smile.llm;

import org.bytedeco.pytorch.*;
import org.bytedeco.pytorch.global.torch;
import smile.deep.Tensor;

/**
 * A transformer is a deep learning architecture developed based on the
 * multi-head attention mechanism, proposed in a 2017 paper "Attention
 * Is All You Need". It has no recurrent units, and thus requires less
 * training time than previous recurrent neural architectures.
 * <p>
 * Its later variation has been prevalently adopted for training large
 * language models (LLM). Text is converted to numerical representations
 * called tokens, and each token is converted into a vector via looking
 * up from a word embedding table. At each layer, each token is then
 * contextualized within the scope of the context window with other
 * (unmasked) tokens via a parallel multi-head attention mechanism
 * allowing the signal for key tokens to be amplified and less important
 * tokens to be diminished.
 * <p>
 * This architecture is now used not only in natural language processing
 * and computer vision, but also in audio and multi-modal processing.
 * It has also led to the development of pre-trained systems, such as GPTs
 * (Generative Pre-trained Transformers) and BERT (Bidirectional Encoder
 * Representations from Transformers).
 *
 * @author Haifeng Li
 */
public class Transformer {
    /** The transform architecture configuration. */
    private Options options;
    /** The transformer model. */
    private TransformerImpl transformer;
    /** The token embedding layer. */
    private EmbeddingImpl embedding;
    /** The positioning encoder. */
    private PositionalEncoding posEncoder;
    /** The decoding layer. */
    private LinearImpl decoder;

    /**
     * Creates a Transformer model with default architecture configuration.
     * @param numTokens the number of tokens in the vocabulary.
     */
    public Transformer(int numTokens) {
        this(new Options(numTokens));
    }

    /**
     * Creates a Transformer model with custom architecture configuration.
     * @param options Transformer architecture configuration.
     */
    public Transformer(Options options) {
        this.options = options;
        this.transformer = new TransformerImpl(options.value);
        this.posEncoder = new PositionalEncoding(options.dModel);
        this.embedding = new EmbeddingImpl(options.numTokens, options.dModel);
        this.decoder = new LinearImpl(options.dModel, options.numTokens);
    }

    /**
     * Forward propagation (or forward pass).
     *
     * @param source the source sequence.
     * @return the log probability of prediction.
     */
    public Tensor forward(Tensor source) {
        source = Tensor.of(embedding.forward(source.value())).mul(Math.sqrt(options.dModel));
        source = posEncoder.forward(source);
        org.bytedeco.pytorch.Tensor output = transformer.encoder().forward(source.value());
        output = decoder.forward(output);
        output = torch.log_softmax(output, -1);
        return Tensor.of(output);
    }

    /** Transformer architecture configuration. */
    public static class Options {
        TransformerOptions value = new TransformerOptions();
        int numTokens;
        int dModel = 512;
        int numHeads = 8;
        int numEncoderLayers = 6;
        int numDecoderLayers = 6;
        int dimFeedForward = 2048;
        double dropout = 0.1;
        String activation = "relu";

        /**
         * Default architecture configuration.
         * @param numTokens the number of tokens in the vocabulary.
         */
        public Options(int numTokens) {
            this.numTokens = numTokens;
        }

        /**
         * Sets the number of expected features in the encoder/decoder inputs.
         * @param dModel the number of expected features in the encoder/decoder inputs (default=512).
         * @return this configuration object.
         */
        public Options dModel(int dModel) {
            value.d_model().put(dModel);
            this.dModel = dModel;
            return this;
        }

        /**
         * Sets the number of heads in the attention models.
         * @param numHeads the number of heads in the attention models (default=8).
         * @return this configuration object.
         */
        public Options numHeads(int numHeads) {
            value.nhead().put(numHeads);
            this.numHeads = numHeads;
            return this;
        }

        /**
         * Sets the number of sub-encoder-layers in the encoder.
         * @param numEncoderLayers the number of sub-encoder-layers in the encoder (default=6).
         * @return this configuration object.
         */
        public Options numEncoderLayers(int numEncoderLayers) {
            value.num_encoder_layers().put(numEncoderLayers);
            this.numEncoderLayers = numEncoderLayers;
            return this;
        }

        /**
         * Sets the number of sub-decoder-layers in the decoder.
         * @param numDecoderLayers the number of sub-decoder-layers in the decoder (default=6).
         * @return this configuration object.
         */
        public Options numDecoderLayers(int numDecoderLayers) {
            value.num_decoder_layers().put(numDecoderLayers);
            this.numDecoderLayers = numDecoderLayers;
            return this;
        }

        /**
         * Sets the dimension of the feedforward network model.
         * @param dimFeedForward the dimension of the feedforward network model (default=2048).
         * @return this configuration object.
         */
        public Options dimFeedForward(int dimFeedForward) {
            value.dim_feedforward().put(dimFeedForward);
            this.dimFeedForward = dimFeedForward;
            return this;
        }

        /**
         * Sets the dropout value.
         * @param dropout the dropout value (default=0.1).
         * @return this configuration object.
         */
        public Options dropout(double dropout) {
            value.dropout().put(dropout);
            this.dropout = dropout;
            return this;
        }

        /**
         * Sets the activation function of encoder/decoder intermediate layer.
         * @param activation the activation function of encoder/decoder intermediate layer, e.g. “relu” or “gelu”. Default: relu
         * @return this configuration object.
         */
        public Options activation(String activation) {
            activation = activation.toLowerCase();
            switch (activation) {
                case "relu":
                    value.activation().put(new kReLU());
                    break;
                case "gelu":
                    value.activation().put(new kGELU());
                    break;
                default:
                    throw new IllegalArgumentException("Invalid activation: " + activation);
            }
            this.activation = activation;
            return this;
        }
    }
}
