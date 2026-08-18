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
package smile.llm.qwen;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import smile.llm.cache.KvCacheLayout;

/**
 * Qwen3.5 dense hybrid text-tower hyperparameters.
 *
 * @param dim                    token embedding / hidden size.
 * @param numLayers              number of hybrid decoder blocks.
 * @param numHeads               number of query attention heads (full-attn layers).
 * @param numKvHeads             number of key/value heads (full-attn layers).
 * @param headDim                attention head dimension (may differ from {@code dim/numHeads}).
 * @param vocabSize              vocabulary size.
 * @param intermediateSize       SwiGLU FFN hidden size.
 * @param normEps                RMSNorm epsilon.
 * @param ropeTheta              RoPE base theta.
 * @param partialRotaryFactor    fraction of {@code headDim} rotated by RoPE.
 * @param linearConvKernelDim    Gated DeltaNet causal conv kernel size.
 * @param linearKeyHeadDim       DeltaNet key head dimension.
 * @param linearValueHeadDim     DeltaNet value head dimension.
 * @param linearNumKeyHeads      DeltaNet key head count.
 * @param linearNumValueHeads    DeltaNet value head count.
 * @param layerTypes             per-layer mixer type ({@code linear_attention} or {@code full_attention}).
 * @param maxBatchSize           maximum inference batch size.
 * @param maxSeqLen              maximum sequence length.
 *
 * @author Haifeng Li
 */
public record QwenModelArgs(
        int dim,
        int numLayers,
        int numHeads,
        int numKvHeads,
        int headDim,
        int vocabSize,
        int intermediateSize,
        double normEps,
        double ropeTheta,
        double partialRotaryFactor,
        int linearConvKernelDim,
        int linearKeyHeadDim,
        int linearValueHeadDim,
        int linearNumKeyHeads,
        int linearNumValueHeads,
        String[] layerTypes,
        int maxBatchSize,
        int maxSeqLen) {

    /** Layer type for Gated DeltaNet (linear attention). */
    public static final String LINEAR_ATTENTION = "linear_attention";
    /** Layer type for gated full softmax attention. */
    public static final String FULL_ATTENTION = "full_attention";

    /**
     * Tiny defaults suitable for unit tests.
     */
    public QwenModelArgs() {
        this(64, 4, 4, 2, 16, 100, 128, 1e-6, 10000.0, 0.25,
                4, 16, 16, 2, 4, defaultLayerTypes(4, 4), 1, 32);
    }

    /**
     * Returns the rotary dimension {@code round(headDim * partialRotaryFactor)}.
     * @return rotary dim (must be even for complex RoPE).
     */
    public int rotaryDim() {
        int rot = (int) Math.round(headDim * partialRotaryFactor);
        if (rot % 2 != 0) {
            rot -= 1;
        }
        return Math.max(2, rot);
    }

    /**
     * Returns the number of full-attention layers.
     * @return full-attention layer count.
     */
    public int numFullAttentionLayers() {
        int n = 0;
        for (String t : layerTypes) {
            if (FULL_ATTENTION.equals(t)) n++;
        }
        return n;
    }

    /**
     * Returns the number of linear-attention (DeltaNet) layers.
     * @return linear-attention layer count.
     */
    public int numLinearAttentionLayers() {
        return numLayers - numFullAttentionLayers();
    }

    /**
     * Maps a stack layer id to the KV-cache pool layer index, or {@code -1}
     * when the stack layer is linear attention.
     *
     * @param stackLayerId zero-based decoder layer index.
     * @return full-attention ordinal, or {@code -1}.
     */
    public int fullAttentionLayerIndex(int stackLayerId) {
        if (stackLayerId < 0 || stackLayerId >= numLayers) {
            throw new IllegalArgumentException("stackLayerId out of range: " + stackLayerId);
        }
        if (!FULL_ATTENTION.equals(layerTypes[stackLayerId])) {
            return -1;
        }
        int idx = 0;
        for (int i = 0; i < stackLayerId; i++) {
            if (FULL_ATTENTION.equals(layerTypes[i])) idx++;
        }
        return idx;
    }

    /**
     * Maps a stack layer id to the DeltaNet state-pool layer index, or {@code -1}
     * when the stack layer is full attention.
     *
     * @param stackLayerId zero-based decoder layer index.
     * @return linear-attention ordinal, or {@code -1}.
     */
    public int linearAttentionLayerIndex(int stackLayerId) {
        if (stackLayerId < 0 || stackLayerId >= numLayers) {
            throw new IllegalArgumentException("stackLayerId out of range: " + stackLayerId);
        }
        if (!LINEAR_ATTENTION.equals(layerTypes[stackLayerId])) {
            return -1;
        }
        int idx = 0;
        for (int i = 0; i < stackLayerId; i++) {
            if (LINEAR_ATTENTION.equals(layerTypes[i])) idx++;
        }
        return idx;
    }

    /**
     * Returns a KV cache layout covering only full-attention layers.
     * @return cache layout for {@link smile.llm.cache.KvCachePool}.
     */
    public KvCacheLayout kvCacheLayout() {
        return new KvCacheLayout(numFullAttentionLayers(), numKvHeads, headDim, maxBatchSize, maxSeqLen);
    }

    /**
     * DeltaNet fused QKV channel count ({@code 2*key_dim + value_dim}).
     * @return conv channel count.
     */
    public int linearConvDim() {
        return 2 * linearKeyHeadDim * linearNumKeyHeads
                + linearValueHeadDim * linearNumValueHeads;
    }

    /**
     * Loads hyperparameters from a HuggingFace {@code config.json}.
     * Accepts either a top-level text config ({@code qwen3_5_text}) or a
     * multimodal wrapper with nested {@code text_config}.
     *
     * @param path         path to {@code config.json}.
     * @param maxBatchSize maximum batch size.
     * @param maxSeqLen    maximum sequence length.
     * @return model args.
     * @throws IOException if the file cannot be read.
     */
    public static QwenModelArgs fromHuggingFace(String path, int maxBatchSize, int maxSeqLen)
            throws IOException {
        File file = new File(path);
        if (!file.exists()) {
            throw new IOException("HuggingFace config file not found: " + path);
        }
        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(file);
        JsonNode text = root.has("text_config") ? root.get("text_config") : root;
        return fromTextConfig(text, maxBatchSize, maxSeqLen);
    }

    /**
     * Parses a text-config JSON object into model args.
     *
     * @param text         text config node.
     * @param maxBatchSize maximum batch size.
     * @param maxSeqLen    maximum sequence length.
     * @return model args.
     */
    public static QwenModelArgs fromTextConfig(JsonNode text, int maxBatchSize, int maxSeqLen) {
        int numLayers = text.get("num_hidden_layers").asInt();
        int numHeads = text.get("num_attention_heads").asInt();
        int hidden = text.get("hidden_size").asInt();
        int headDim = text.has("head_dim")
                ? text.get("head_dim").asInt()
                : hidden / numHeads;

        double partialRotary = 0.25;
        double ropeTheta = 10000.0;
        if (text.has("rope_parameters") && text.get("rope_parameters").isObject()) {
            var rope = text.get("rope_parameters");
            if (rope.has("partial_rotary_factor")) {
                partialRotary = rope.get("partial_rotary_factor").asDouble();
            }
            if (rope.has("rope_theta")) {
                ropeTheta = rope.get("rope_theta").asDouble();
            }
        }
        if (text.has("rope_theta")) {
            ropeTheta = text.get("rope_theta").asDouble();
        }
        if (text.has("partial_rotary_factor")) {
            partialRotary = text.get("partial_rotary_factor").asDouble();
        }

        String[] layerTypes;
        if (text.has("layer_types") && text.get("layer_types").isArray()) {
            List<String> list = new ArrayList<>();
            for (JsonNode n : text.get("layer_types")) {
                list.add(n.asString());
            }
            layerTypes = list.toArray(String[]::new);
        } else {
            int interval = text.has("full_attention_interval")
                    ? text.get("full_attention_interval").asInt() : 4;
            layerTypes = defaultLayerTypes(numLayers, interval);
        }
        if (layerTypes.length != numLayers) {
            throw new IllegalArgumentException(
                    "layer_types length " + layerTypes.length + " != num_hidden_layers " + numLayers);
        }

        return new QwenModelArgs(
                hidden,
                numLayers,
                numHeads,
                text.has("num_key_value_heads") ? text.get("num_key_value_heads").asInt() : numHeads,
                headDim,
                text.get("vocab_size").asInt(),
                text.get("intermediate_size").asInt(),
                text.has("rms_norm_eps") ? text.get("rms_norm_eps").asDouble() : 1e-6,
                ropeTheta,
                partialRotary,
                text.has("linear_conv_kernel_dim") ? text.get("linear_conv_kernel_dim").asInt() : 4,
                text.has("linear_key_head_dim") ? text.get("linear_key_head_dim").asInt() : 128,
                text.has("linear_value_head_dim") ? text.get("linear_value_head_dim").asInt() : 128,
                text.has("linear_num_key_heads") ? text.get("linear_num_key_heads").asInt() : 16,
                text.has("linear_num_value_heads") ? text.get("linear_num_value_heads").asInt() : 32,
                layerTypes,
                maxBatchSize,
                maxSeqLen
        );
    }

    /**
     * Builds the default 3:1 hybrid layer type pattern.
     *
     * @param numLayers number of layers.
     * @param interval  full-attention interval (every N-th layer is full).
     * @return layer type array.
     */
    public static String[] defaultLayerTypes(int numLayers, int interval) {
        String[] types = new String[numLayers];
        for (int i = 0; i < numLayers; i++) {
            types[i] = ((i + 1) % interval == 0) ? FULL_ATTENTION : LINEAR_ATTENTION;
        }
        return types;
    }
}
