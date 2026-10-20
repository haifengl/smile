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
import smile.llm.cache.KvCacheLayout;
import smile.llm.parallel.TensorShardSpec;

/**
 * MiniMax-M3 text-tower hyperparameters: GQA with MiniMax Sparse Attention (MSA)
 * and a sigmoid-routed mixture of experts.
 *
 * @param dim                    token embedding / hidden size.
 * @param numLayers              number of decoder blocks.
 * @param numHeads               number of query heads.
 * @param numKvHeads             number of key/value heads.
 * @param headDim                attention head dimension.
 * @param vocabSize              vocabulary size.
 * @param normEps                RMSNorm epsilon (Gemma-style {@code 1 + weight} scale).
 * @param ropeTheta              RoPE base theta.
 * @param rotaryDim              number of rotated head dimensions (partial RoPE).
 * @param denseIntermediateSize  FFN width of the dense (non-MoE) layers.
 * @param expertIntermediateSize FFN width of one routed expert.
 * @param sharedIntermediateSize FFN width of the shared expert.
 * @param numExperts             routed experts per MoE layer.
 * @param numExpertsPerTok       experts selected per token.
 * @param numSharedExperts       shared experts per MoE layer.
 * @param routedScalingFactor    multiplier applied to the renormalized routing weights.
 * @param swigluAlpha            {@code swigluoai} sigmoid slope.
 * @param swigluLimit            {@code swigluoai} clamp.
 * @param moeLayers              per-layer flag: {@code true} for MoE, {@code false} for dense FFN.
 * @param sparseLayers           per-layer flag: {@code true} for MSA, {@code false} for dense GQA.
 * @param indexDim               MSA index head dimension.
 * @param numIndexHeads          MSA index-query heads (equals {@code numKvHeads}; one per GQA group).
 * @param topkBlocks             MSA blocks selected per query and index head.
 * @param blockSize              MSA block size in tokens (equals the KV page size).
 * @param initBlocks             leading blocks always selected.
 * @param localBlocks            most recent blocks always selected.
 * @param numMtpModules          native multi-token-prediction modules ({@code 0} = none).
 * @param maxBatchSize           maximum inference batch size.
 * @param maxSeqLen              maximum sequence length.
 * @param weightMode             how linear weights are stored and multiplied (chosen by the loader,
 *                               not read from {@code config.json}).
 *
 * @author Haifeng Li
 */
public record MiniMaxModelArgs(
        int dim,
        int numLayers,
        int numHeads,
        int numKvHeads,
        int headDim,
        int vocabSize,
        double normEps,
        double ropeTheta,
        int rotaryDim,
        int denseIntermediateSize,
        int expertIntermediateSize,
        int sharedIntermediateSize,
        int numExperts,
        int numExpertsPerTok,
        int numSharedExperts,
        double routedScalingFactor,
        double swigluAlpha,
        double swigluLimit,
        boolean[] moeLayers,
        boolean[] sparseLayers,
        int indexDim,
        int numIndexHeads,
        int topkBlocks,
        int blockSize,
        int initBlocks,
        int localBlocks,
        int numMtpModules,
        int maxBatchSize,
        int maxSeqLen,
        MiniMaxWeightMode weightMode) {

    /**
     * Returns whether a layer uses a mixture of experts.
     * @param layerId zero-based decoder layer index.
     * @return {@code true} for MoE, {@code false} for the dense FFN.
     */
    public boolean isMoe(int layerId) {
        checkLayer(layerId);
        return moeLayers[layerId];
    }

    /**
     * Returns whether a layer uses MiniMax Sparse Attention.
     * @param layerId zero-based decoder layer index.
     * @return {@code true} for MSA, {@code false} for dense GQA.
     */
    public boolean isSparse(int layerId) {
        checkLayer(layerId);
        return sparseLayers[layerId];
    }

    /**
     * Returns the number of MSA layers (each also keeps an index-key cache).
     * @return MSA layer count.
     */
    public int numSparseLayers() {
        int n = 0;
        for (boolean s : sparseLayers) {
            if (s) n++;
        }
        return n;
    }

    /**
     * Returns the number of MoE layers.
     * @return MoE layer count.
     */
    public int numMoeLayers() {
        int n = 0;
        for (boolean m : moeLayers) {
            if (m) n++;
        }
        return n;
    }

    /**
     * Returns the number of index-key heads per token. The checkpoint's {@code index_k_proj} has width
     * {@code indexDim}, i.e. one key head shared by all {@code numIndexHeads} index-query heads.
     * @return index-key heads.
     */
    public int numIndexKeyHeads() {
        return 1;
    }

    /**
     * Returns a copy with another weight mode.
     *
     * @param mode how linear weights are stored and multiplied.
     * @return the new args.
     */
    public MiniMaxModelArgs withWeightMode(MiniMaxWeightMode mode) {
        return new MiniMaxModelArgs(dim, numLayers, numHeads, numKvHeads, headDim, vocabSize, normEps, ropeTheta,
                rotaryDim, denseIntermediateSize, expertIntermediateSize, sharedIntermediateSize, numExperts,
                numExpertsPerTok, numSharedExperts, routedScalingFactor, swigluAlpha, swigluLimit, moeLayers,
                sparseLayers, indexDim, numIndexHeads, topkBlocks, blockSize, initBlocks, localBlocks,
                numMtpModules, maxBatchSize, maxSeqLen, mode);
    }

    /**
     * Returns whether native MTP modules are configured.
     * @return {@code true} when MTP modules exist.
     */
    public boolean hasMtp() {
        return numMtpModules > 0;
    }

    /**
     * Returns the KV cache layout. Every layer, sparse or dense, keeps a full K/V cache.
     * @return cache layout for {@link smile.llm.cache.KvCachePool}.
     */
    public KvCacheLayout kvCacheLayout() {
        return new KvCacheLayout(numLayers, numKvHeads, headDim, maxBatchSize, maxSeqLen);
    }

    /**
     * KV layout for a tensor-parallel shard (local KV head count).
     * @param shard TP shard description, or {@code null} for the full layout.
     * @return cache layout for {@link smile.llm.cache.KvCachePool}.
     */
    public KvCacheLayout kvCacheLayout(TensorShardSpec shard) {
        int kvHeads = shard != null && shard.tpSize() > 1 ? shard.numKvHeads() : numKvHeads;
        return new KvCacheLayout(numLayers, kvHeads, headDim, maxBatchSize, maxSeqLen);
    }

    private void checkLayer(int layerId) {
        if (layerId < 0 || layerId >= numLayers) {
            throw new IllegalArgumentException("layerId out of range: " + layerId);
        }
    }

    /**
     * Loads hyperparameters from a HuggingFace {@code config.json}. Accepts the
     * multimodal wrapper ({@code minimax_m3_vl}, nested {@code text_config}) or a
     * bare text config.
     *
     * @param path         path to {@code config.json}.
     * @param maxBatchSize maximum batch size.
     * @param maxSeqLen    maximum sequence length; {@code <= 0} uses {@code max_position_embeddings}.
     * @return model args.
     * @throws IOException if the file cannot be read.
     */
    public static MiniMaxModelArgs fromHuggingFace(String path, int maxBatchSize, int maxSeqLen)
            throws IOException {
        File file = new File(path);
        if (!file.exists()) {
            throw new IOException("HuggingFace config file not found: " + path);
        }
        JsonNode root = new ObjectMapper().readTree(file);
        JsonNode text = root.has("text_config") ? root.get("text_config") : root;
        return fromTextConfig(text, maxBatchSize, maxSeqLen);
    }

    /**
     * Parses a text-config JSON object into model args.
     *
     * @param text         text config node.
     * @param maxBatchSize maximum batch size.
     * @param maxSeqLen    maximum sequence length; {@code <= 0} uses {@code max_position_embeddings}.
     * @return model args.
     */
    public static MiniMaxModelArgs fromTextConfig(JsonNode text, int maxBatchSize, int maxSeqLen) {
        int numLayers = text.get("num_hidden_layers").asInt();
        int numHeads = text.get("num_attention_heads").asInt();
        int dim = text.get("hidden_size").asInt();
        int headDim = text.has("head_dim") ? text.get("head_dim").asInt() : dim / numHeads;
        int numKvHeads = text.has("num_key_value_heads")
                ? text.get("num_key_value_heads").asInt() : numHeads;

        int rotaryDim;
        if (text.has("rotary_dim")) {
            rotaryDim = text.get("rotary_dim").asInt();
        } else {
            double factor = text.has("partial_rotary_factor")
                    ? text.get("partial_rotary_factor").asDouble() : 1.0;
            rotaryDim = (int) Math.round(headDim * factor);
        }
        if (rotaryDim <= 0 || rotaryDim > headDim || rotaryDim % 2 != 0) {
            throw new IllegalArgumentException("Invalid rotary_dim " + rotaryDim + " for head_dim " + headDim);
        }

        String act = text.has("hidden_act") ? text.get("hidden_act").asString() : "swigluoai";
        if (!"swigluoai".equals(act)) {
            throw new IllegalArgumentException("Unsupported hidden_act: " + act);
        }
        String scoring = text.has("scoring_func") ? text.get("scoring_func").asString() : "sigmoid";
        if (!"sigmoid".equals(scoring)) {
            throw new IllegalArgumentException("Unsupported scoring_func: " + scoring);
        }

        boolean[] moe = flags(text.get("moe_layer_freq"), numLayers, true, "moe_layer_freq");

        JsonNode sparse = text.get("sparse_attention_config");
        boolean useSparse = sparse != null && sparse.has("use_sparse_attention")
                && sparse.get("use_sparse_attention").asBoolean();
        boolean[] sparseLayers;
        int indexDim = 0;
        int numIndexHeads = 0;
        int topk = 0;
        int blockSize = 0;
        int initBlocks = 0;
        int localBlocks = 0;
        if (useSparse) {
            sparseLayers = flags(sparse.get("sparse_attention_freq"), numLayers, true, "sparse_attention_freq");
            indexDim = sparse.get("sparse_index_dim").asInt();
            numIndexHeads = sparse.get("sparse_num_index_heads").asInt();
            topk = sparse.get("sparse_topk_blocks").asInt();
            blockSize = sparse.get("sparse_block_size").asInt();
            initBlocks = sparse.has("sparse_init_block") ? sparse.get("sparse_init_block").asInt() : 0;
            localBlocks = sparse.has("sparse_local_block") ? sparse.get("sparse_local_block").asInt() : 1;
            String score = sparse.has("sparse_score_type") ? sparse.get("sparse_score_type").asString() : "max";
            if (!"max".equals(score)) {
                throw new IllegalArgumentException("Unsupported sparse_score_type: " + score);
            }
            if (numIndexHeads != numKvHeads) {
                throw new IllegalArgumentException("sparse_num_index_heads (" + numIndexHeads
                        + ") must equal num_key_value_heads (" + numKvHeads + ")");
            }
        } else {
            sparseLayers = new boolean[numLayers];
        }

        int experts = text.has("num_local_experts") ? text.get("num_local_experts").asInt() : 0;
        int perTok = text.has("num_experts_per_tok") ? text.get("num_experts_per_tok").asInt() : 0;
        for (boolean m : moe) {
            if (m && (experts <= 0 || perTok <= 0 || perTok > experts)) {
                throw new IllegalArgumentException("Invalid MoE config: experts=" + experts + ", top-k=" + perTok);
            }
        }
        int intermediate = text.get("intermediate_size").asInt();

        return new MiniMaxModelArgs(
                dim,
                numLayers,
                numHeads,
                numKvHeads,
                headDim,
                text.get("vocab_size").asInt(),
                text.has("rms_norm_eps") ? text.get("rms_norm_eps").asDouble() : 1e-6,
                text.has("rope_theta") ? text.get("rope_theta").asDouble() : 10000.0,
                rotaryDim,
                text.has("dense_intermediate_size") ? text.get("dense_intermediate_size").asInt() : intermediate,
                intermediate,
                text.has("shared_intermediate_size") ? text.get("shared_intermediate_size").asInt() : intermediate,
                experts,
                perTok,
                text.has("n_shared_experts") ? text.get("n_shared_experts").asInt() : 0,
                text.has("routed_scaling_factor") ? text.get("routed_scaling_factor").asDouble() : 1.0,
                text.has("swiglu_alpha") ? text.get("swiglu_alpha").asDouble() : 1.702,
                text.has("swiglu_limit") ? text.get("swiglu_limit").asDouble() : 7.0,
                moe,
                sparseLayers,
                indexDim,
                numIndexHeads,
                topk,
                blockSize,
                initBlocks,
                localBlocks,
                text.has("num_mtp_modules") ? text.get("num_mtp_modules").asInt() : 0,
                maxBatchSize,
                resolveMaxSeqLen(text, maxSeqLen),
                MiniMaxWeightMode.DENSE);
    }

    /** Parses a 0/1 per-layer array; a missing node means every layer is {@code dflt}. */
    private static boolean[] flags(JsonNode node, int numLayers, boolean dflt, String name) {
        boolean[] out = new boolean[numLayers];
        if (node == null || !node.isArray()) {
            java.util.Arrays.fill(out, dflt);
            return out;
        }
        if (node.size() != numLayers) {
            throw new IllegalArgumentException(name + " length " + node.size()
                    + " != num_hidden_layers " + numLayers);
        }
        for (int i = 0; i < numLayers; i++) {
            out[i] = node.get(i).asInt() != 0;
        }
        return out;
    }

    /**
     * Resolves serving context length: an explicit positive {@code maxSeqLen} wins,
     * otherwise {@code max_position_embeddings}.
     */
    static int resolveMaxSeqLen(JsonNode text, int maxSeqLen) {
        if (maxSeqLen > 0) {
            return maxSeqLen;
        }
        if (text.has("max_position_embeddings")) {
            return text.get("max_position_embeddings").asInt();
        }
        return 8192;
    }
}
