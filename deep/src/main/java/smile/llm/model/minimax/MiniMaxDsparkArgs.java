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
package smile.llm.model.minimax;

import java.io.File;
import java.io.IOException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Hyperparameters of the MiniMax-M3.1 DSpark draft model (the {@code dspark/config.json} shipped with the target).
 *
 * @param dim              hidden size (equals the target's).
 * @param numLayers        draft decoder layers.
 * @param numHeads         query heads.
 * @param numKvHeads       KV heads.
 * @param headDim          head size.
 * @param rotaryDim        rotated leading dims of a head (partial RoPE).
 * @param ropeTheta        RoPE base.
 * @param normEps          RMSNorm epsilon.
 * @param ffnSize          dense FFN width.
 * @param swigluAlpha      swigluoai slope.
 * @param swigluLimit      swigluoai clamp.
 * @param vocabSize        vocabulary size (equals the target's).
 * @param blockSize        tokens drafted per round (the anchor row plus {@code blockSize - 1} mask rows).
 * @param markovRank       rank of the Markov transition bias.
 * @param noiseTokenId     id of the mask token that fills the unknown block rows.
 * @param targetLayerIds   target decoder layers whose output hidden states feed the draft.
 * @param slidingWindow    attention window over the context.
 * @param confidenceHead   whether a per-position acceptance confidence is predicted.
 * @param confidenceMarkov whether the confidence head also reads the Markov embedding.
 * @author Haifeng Li
 */
public record MiniMaxDsparkArgs(
        int dim,
        int numLayers,
        int numHeads,
        int numKvHeads,
        int headDim,
        int rotaryDim,
        double ropeTheta,
        double normEps,
        int ffnSize,
        double swigluAlpha,
        double swigluLimit,
        int vocabSize,
        int blockSize,
        int markovRank,
        int noiseTokenId,
        int[] targetLayerIds,
        int slidingWindow,
        boolean confidenceHead,
        boolean confidenceMarkov) {

    /**
     * Reads {@code config.json}.
     *
     * @param path path to the draft's {@code config.json}.
     * @return the arguments.
     * @throws IOException if the file cannot be read or is not a DSpark MiniMax config.
     */
    public static MiniMaxDsparkArgs fromHuggingFace(String path) throws IOException {
        File file = new File(path);
        if (!file.exists()) {
            throw new IOException("DSpark config file not found: " + path);
        }
        JsonNode root = new ObjectMapper().readTree(file);
        JsonNode text = root.has("text_config") ? root.get("text_config") : root;
        JsonNode ids = root.has("dspark_target_layer_ids") ? root.get("dspark_target_layer_ids")
                : text.get("dspark_target_layer_ids");
        if (ids == null || !text.has("dspark_block_size")) {
            throw new IOException(path + " is not a DSpark draft config");
        }
        if (!text.path("dspark_use_dense_ffn").asBoolean(false) || text.path("dspark_hybrid_context_fusion").asBoolean(false)
                || !"vanilla".equals(text.path("dspark_markov_head_type").asString("vanilla"))) {
            throw new IOException("unsupported DSpark variant in " + path
                    + " (only dense FFN, no hybrid context fusion, vanilla Markov head)");
        }
        int[] layerIds = new int[ids.size()];
        for (int i = 0; i < layerIds.length; i++) {
            layerIds[i] = ids.get(i).asInt();
        }
        int numHeads = text.get("num_attention_heads").asInt();
        int dim = text.get("hidden_size").asInt();
        int headDim = text.has("head_dim") ? text.get("head_dim").asInt() : dim / numHeads;
        int rotaryDim = text.has("rotary_dim") ? text.get("rotary_dim").asInt()
                : (int) Math.round(headDim * text.path("partial_rotary_factor").asDouble(1.0));
        return new MiniMaxDsparkArgs(
                dim,
                text.get("num_hidden_layers").asInt(),
                numHeads,
                text.has("num_key_value_heads") ? text.get("num_key_value_heads").asInt() : numHeads,
                headDim,
                rotaryDim,
                text.path("rope_theta").asDouble(10000.0),
                text.path("rms_norm_eps").asDouble(1e-6),
                text.get("dspark_ffn_hidden_size").asInt(),
                text.path("swiglu_alpha").asDouble(1.702),
                text.path("swiglu_limit").asDouble(7.0),
                text.get("vocab_size").asInt(),
                text.get("dspark_block_size").asInt(),
                text.get("dspark_markov_rank").asInt(),
                text.get("dspark_noise_token_id").asInt(),
                layerIds,
                text.path("sliding_window").asInt(4096),
                text.path("enable_confidence_head").asBoolean(false),
                text.path("confidence_head_with_markov").asBoolean(false));
    }

    /**
     * Width of the concatenated target taps the {@code fc} projection consumes.
     * @return {@code targetLayerIds.length * dim}.
     */
    public int contextWidth() {
        return targetLayerIds.length * dim;
    }
}
