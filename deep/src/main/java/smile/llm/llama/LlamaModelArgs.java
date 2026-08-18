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

import java.io.File;
import java.io.IOException;
import tools.jackson.databind.ObjectMapper;
import smile.llm.cache.KvCacheLayout;

/**
 * Llama / Meta dense-decoder hyperparameters.
 *
 * @param dim the dimension of token embedding.
 * @param numLayers the number of transformer blocks.
 * @param numHeads the number of attention heads.
 * @param numKvHeads the number of key and value heads.
 * @param vocabSize the size of the vocabulary.
 * @param multipleOf make SwiGLU hidden layer size multiple of large power of 2.
 * @param ffnDimMultiplier the multiplier for the hidden dimension of the feedforward layers.
 * @param intermediateSize the explicit FFN hidden dimension size (takes priority over multipleOf/ffnDimMultiplier).
 *                         When non-null, the FeedForward layer uses this size directly, as provided by
 *                         HuggingFace {@code config.json}'s {@code intermediate_size} field.
 * @param normEps the epsilon value used for numerical stability in normalization layers.
 * @param ropeTheta the theta parameter in rotary positional encoding.
 * @param scaledRope scale RoPE positional encoding if true.
 * @param maxBatchSize the maximum batch size.
 * @param maxSeqLen the maximum sequence length for input data.
 *
 * @author Haifeng Li
 */
public record LlamaModelArgs(int dim,
                             int numLayers,
                             int numHeads,
                             Integer numKvHeads,
                             int vocabSize,
                             int multipleOf,
                             Double ffnDimMultiplier,
                             Integer intermediateSize,
                             double normEps,
                             double ropeTheta,
                             boolean scaledRope,
                             int maxBatchSize,
                             int maxSeqLen) {

    /**
     * Constructor with default parameter values.
     */
    public LlamaModelArgs() {
        this(4096, 32, 32, null, -1, 256, null, null, 1E-5, 500000, false, 32, 2048);
    }

    /**
     * Constructor without an explicit FFN intermediate size (Meta-format layout).
     * The feed-forward hidden dimension is derived from {@code multipleOf} /
     * {@code ffnDimMultiplier}.
     */
    public LlamaModelArgs(int dim, int numLayers, int numHeads, Integer numKvHeads,
                          int vocabSize, int multipleOf, Double ffnDimMultiplier,
                          double normEps, double ropeTheta, boolean scaledRope,
                          int maxBatchSize, int maxSeqLen) {
        this(dim, numLayers, numHeads, numKvHeads, vocabSize, multipleOf,
                ffnDimMultiplier, null, normEps, ropeTheta, scaledRope, maxBatchSize, maxSeqLen);
    }

    /**
     * Returns the resolved number of key/value heads (falls back to {@link #numHeads()}).
     * @return KV head count.
     */
    public int resolvedNumKvHeads() {
        return numKvHeads != null ? numKvHeads : numHeads;
    }

    /**
     * Returns {@code dim / numHeads}.
     * @return attention head dimension.
     */
    public int headDim() {
        return dim / numHeads;
    }

    /**
     * Returns a {@link KvCacheLayout} derived from these hyperparameters.
     * @return cache layout for {@link smile.llm.cache.KvCachePool}.
     */
    public KvCacheLayout kvCacheLayout() {
        return KvCacheLayout.of(numLayers, dim, numHeads, numKvHeads, maxBatchSize, maxSeqLen);
    }

    /**
     * Loads the model hyperparameters from a Meta-format {@code params.json} file.
     * @param path the file path.
     * @param maxBatchSize the maximum batch size.
     * @param maxSeqLen the maximum sequence length for input data.
     * @throws IOException if fail to open the parameter file.
     * @return the model hyperparameters.
     */
    public static LlamaModelArgs from(String path, int maxBatchSize, int maxSeqLen) throws IOException {
        File file = new File(path);
        if (!file.exists()) {
            throw new IOException("Model params file not found: " + path);
        }
        ObjectMapper mapper = new ObjectMapper();
        var node = mapper.readTree(new File(path));
        return new LlamaModelArgs(
                node.get("dim").asInt(),
                node.get("n_layers").asInt(),
                node.get("n_heads").asInt(),
                node.has("n_kv_heads") ? node.get("n_kv_heads").asInt() : null,
                node.get("vocab_size").asInt(),
                node.get("multiple_of").asInt(),
                node.has("ffn_dim_multiplier") ? node.get("ffn_dim_multiplier").asDouble() : null,
                null,
                node.get("norm_eps").asDouble(),
                node.has("rope_theta") ? node.get("rope_theta").asDouble() : 10000.0,
                node.has("use_scaled_rope") && node.get("use_scaled_rope").asBoolean(),
                maxBatchSize,
                maxSeqLen
        );
    }

    /**
     * Loads the model hyperparameters from a HuggingFace {@code config.json} file.
     * The {@code config.json} uses different field names and conventions from Meta's
     * {@code params.json}, notably {@code hidden_size} instead of {@code dim} and
     * {@code intermediate_size} instead of a computed FFN hidden dim.
     * @param path the file path.
     * @param maxBatchSize the maximum batch size.
     * @param maxSeqLen the maximum sequence length for input data.
     * @throws IOException if fail to open the config file.
     * @return the model hyperparameters.
     */
    public static LlamaModelArgs fromHuggingFace(String path, int maxBatchSize, int maxSeqLen)
            throws IOException {
        File file = new File(path);
        if (!file.exists()) {
            throw new IOException("HuggingFace config file not found: " + path);
        }
        ObjectMapper mapper = new ObjectMapper();
        var node = mapper.readTree(file);

        boolean scaledRope = false;
        if (node.has("rope_scaling")) {
            var scaling = node.get("rope_scaling");
            if (scaling.has("rope_type")) {
                scaledRope = "llama3".equalsIgnoreCase(scaling.get("rope_type").asString());
            }
        }

        return new LlamaModelArgs(
                node.get("hidden_size").asInt(),
                node.get("num_hidden_layers").asInt(),
                node.get("num_attention_heads").asInt(),
                node.has("num_key_value_heads") ? node.get("num_key_value_heads").asInt() : null,
                node.get("vocab_size").asInt(),
                1,
                null,
                node.has("intermediate_size") ? node.get("intermediate_size").asInt() : null,
                node.has("rms_norm_eps") ? node.get("rms_norm_eps").asDouble() : 1e-5,
                node.has("rope_theta") ? node.get("rope_theta").asDouble() : 10000.0,
                scaledRope,
                maxBatchSize,
                maxSeqLen
        );
    }
}
