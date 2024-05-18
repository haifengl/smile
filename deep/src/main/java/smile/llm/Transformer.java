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
import smile.deep.tensor.Device;
import smile.deep.tensor.Tensor;

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
    private final Options options;
    /** The transformer model. */
    private final TransformerImpl transformer;
    /** The token embedding layer. */
    private final EmbeddingImpl embedding;
    /** The positioning encoder. */
    private final PositionalEncoding posEncoder;
    /** The decoding layer. */
    private final LinearImpl decoder;

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
        this.transformer = new TransformerImpl(options.asTorch());
        this.posEncoder = new PositionalEncoding(options.dModel, 5000);
        this.embedding = new EmbeddingImpl(options.numTokens, options.dModel);
        this.decoder = new LinearImpl(options.dModel, options.numTokens);
    }

    /**
     * Initializes the model weights.
     */
    public void init() {
        double range = 0.1;
        torch.uniform_(embedding.weight(), -range, range);
        torch.zeros_(decoder.bias());
        torch.uniform_(decoder.weight(), -range, range);
    }

    /**
     * Forward propagation (or forward pass).
     *
     * @param source the source sequence.
     * @return the log probability of prediction.
     */
    public Tensor forward(Tensor source) {
        source = new Tensor(embedding.forward(source.asTorch())).mul(Math.sqrt(options.dModel));
        source = posEncoder.forward(source);
        org.bytedeco.pytorch.Tensor output = transformer.encoder().forward(source.asTorch());
        output = decoder.forward(output);
        output = torch.log_softmax(output, -1);
        return new Tensor(output);
    }

    /**
     * Moves the model to a device.
     * @param device the compute device.
     * @return this model.
     */
    public Transformer to(Device device) {
        transformer.to(device.asTorch(), true);
        embedding.to(device.asTorch(), true);
        posEncoder.to(device);
        decoder.to(device.asTorch(), true);
        return this;
    }

    /**
     * Transformer architecture configuration.
     * @param numTokens the number of tokens in the vocabulary.
     * @param dModel the number of expected features in the encoder/decoder inputs (default=512).
     * @param numHeads the number of heads in the attention models (default=8).
     * @param numEncoderLayers the number of sub-encoder-layers in the encoder (default=6).
     * @param numDecoderLayers the number of sub-decoder-layers in the decoder (default=6).
     * @param dimFeedForward the dimension of the feedforward network model (default=2048).
     * @param dropout the dropout value (default=0.1).
     * @param activation the activation function of encoder/decoder intermediate layer,
     *                  e.g. "relu" or "gelu" (default=relu).
     */
    public record Options(int numTokens, int dModel, int numHeads, int numEncoderLayers,
            int numDecoderLayers, int dimFeedForward, double dropout, String activation) {

        /**
         * Constructor with default values.
         * @param numTokens the number of tokens in the vocabulary.
         */
        public Options(int numTokens) {
            this(numTokens, 512, 8, 6, 6, 2048, 0.1, "relu");
        }

        /**
         * Returns PyTorch option object.
         * @return PyTorch option object.
         */
        TransformerOptions asTorch() {
            TransformerOptions options = new TransformerOptions(numTokens);
            options.d_model().put(dModel);
            options.nhead().put(numHeads);
            options.num_encoder_layers().put(numEncoderLayers);
            options.num_decoder_layers().put(numDecoderLayers);
            options.dim_feedforward().put(dimFeedForward);
            options.dropout().put(dropout);
            switch (activation.toLowerCase()) {
                case "relu":
                    options.activation().put(new kReLU());
                    break;
                case "gelu":
                    options.activation().put(new kGELU());
                    break;
                default:
                    throw new IllegalArgumentException("Invalid activation: " + activation);
            }
            return options;
        }
    }
}
