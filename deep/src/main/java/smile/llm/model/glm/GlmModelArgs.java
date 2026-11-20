/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
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
package smile.llm.model.glm;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * GLM-5.3-Flash text-tower hyperparameters (checkpoint {@code config.json}, section {@code text_config}).
 *
 * <p>The architecture: a residual stream of {@code hcMult} parallel copies mixed by manifold-constrained
 * Hyper-Connections; two kinds of token mixers, Kimi-style delta-rule linear attention (KDA) and MLA-based
 * sparse attention (no rotary embedding at all) whose key set is chosen by a k-pooled indexer; dense MLPs in the
 * first layers and a sigmoid-routed MoE after. Layers {@code numLayers ..} belong to the MTP head.
 *
 * @param vocabSize          vocabulary size.
 * @param dim                hidden size.
 * @param numLayers          backbone layers.
 * @param normEps            RMSNorm epsilon.
 * @param sparseAttention    per layer: {@code true} for MLA + indexer, {@code false} for KDA.
 * @param sparseMlp          per layer: {@code true} for the MoE, {@code false} for the dense MLP.
 * @param denseInterSize     dense MLP width.
 * @param numHeads           MLA heads.
 * @param qkHeadDim          MLA query/key head dimension (no rotary part: {@code ropeHeadDim} is 0).
 * @param ropeHeadDim        rotary dimensions of MLA (0 in this model).
 * @param vHeadDim           MLA value head dimension.
 * @param qLoraRank          query low-rank dimension.
 * @param kvLoraRank         latent (compressed KV) dimension.
 * @param kdaHeads           KDA heads.
 * @param kdaHeadDim         KDA head dimension.
 * @param kdaConvKernel      KDA short-convolution kernel size.
 * @param kdaGateLowerBound  KDA forget-gate lower bound ({@code -5}); the gate is {@code bound * sigmoid(..)}.
 * @param indexHeads         indexer heads.
 * @param indexHeadDim       indexer head dimension.
 * @param indexTopk          tokens selected per query (pools of {@code indexKpool} tokens).
 * @param indexKpool         tokens per pool.
 * @param indexAlwaysSelectTail whether the open (incomplete) pool is always attended.
 * @param numRoutedExperts   routed experts per MoE layer.
 * @param numSharedExperts   shared experts per MoE layer.
 * @param numExpertsPerTok   experts per token.
 * @param moeInterSize       routed/shared expert width.
 * @param routeScale         multiplier on the renormalised routing weights.
 * @param swigluLimit        activation clamp ({@code gate <= limit}, {@code |up| <= limit}).
 * @param hcMult             residual copies.
 * @param hcSinkhornIters    Sinkhorn iterations of the connection mixing.
 * @param hcEps              Hyper-Connections epsilon.
 * @param numMtpLayers       MTP layers.
 * @param imageTokenId       id of the image placeholder token.
 * @param eosTokenIds        end-of-sequence token ids.
 * @param padTokenId         padding token id.
 * @param maxSeqLen          maximum sequence length served.
 *
 * @author Haifeng Li
 */
public record GlmModelArgs(
        int vocabSize,
        int dim,
        int numLayers,
        double normEps,
        boolean[] sparseAttention,
        boolean[] sparseMlp,
        int denseInterSize,
        int numHeads,
        int qkHeadDim,
        int ropeHeadDim,
        int vHeadDim,
        int qLoraRank,
        int kvLoraRank,
        int kdaHeads,
        int kdaHeadDim,
        int kdaConvKernel,
        double kdaGateLowerBound,
        int indexHeads,
        int indexHeadDim,
        int indexTopk,
        int indexKpool,
        boolean indexAlwaysSelectTail,
        int numRoutedExperts,
        int numSharedExperts,
        int numExpertsPerTok,
        int moeInterSize,
        double routeScale,
        double swigluLimit,
        int hcMult,
        int hcSinkhornIters,
        double hcEps,
        int numMtpLayers,
        int imageTokenId,
        int[] eosTokenIds,
        int padTokenId,
        int maxSeqLen) {

    /**
     * Validates the shape relations the model code relies on.
     */
    public GlmModelArgs {
        if (sparseAttention.length < numLayers || sparseMlp.length < numLayers) {
            throw new IllegalArgumentException("layer type lists are shorter than " + numLayers + " layers");
        }
        if (ropeHeadDim != 0) {
            throw new IllegalArgumentException("rotary MLA is not supported (qk_rope_head_dim=" + ropeHeadDim + ")");
        }
        if (indexTopk % indexKpool != 0) {
            throw new IllegalArgumentException("index_topk must be a multiple of index_kpool");
        }
        sparseAttention = sparseAttention.clone();
        sparseMlp = sparseMlp.clone();
        eosTokenIds = eosTokenIds.clone();
    }

    /**
     * Whether a layer mixes tokens with MLA sparse attention.
     *
     * @param layer layer index.
     * @return {@code true} for MLA + indexer, {@code false} for KDA.
     */
    public boolean isSparseAttention(int layer) {
        return sparseAttention[layer];
    }

    /**
     * Whether a layer's feed-forward is the MoE.
     *
     * @param layer layer index.
     * @return {@code true} for MoE, {@code false} for the dense MLP.
     */
    public boolean isMoe(int layer) {
        return sparseMlp[layer];
    }

    /**
     * Number of KDA layers among the first {@code numLayers}.
     *
     * @return the count.
     */
    public int kdaLayerCount() {
        int n = 0;
        for (int i = 0; i < numLayers; i++) {
            if (!sparseAttention[i]) {
                n++;
            }
        }
        return n;
    }

    /**
     * The checkpoint name prefix of a layer ({@code model.language_model.layers.N}).
     *
     * @param layer layer index (the MTP layer is {@code numLayers}).
     * @return the prefix.
     */
    public String layerPrefix(int layer) {
        return "model.language_model.layers." + layer;
    }

    /**
     * Hidden width of the KDA projections ({@code kdaHeads * kdaHeadDim}).
     *
     * @return the width.
     */
    public int kdaWidth() {
        return kdaHeads * kdaHeadDim;
    }

    /**
     * Number of pools the indexer selects per query.
     *
     * @return {@code indexTopk / indexKpool}.
     */
    public int indexPools() {
        return indexTopk / indexKpool;
    }

    /**
     * Returns the arguments of a truncated model made of the first layers, for slice validation.
     *
     * @param layers number of backbone layers to keep.
     * @return the truncated arguments (no MTP layer).
     */
    public GlmModelArgs slice(int layers) {
        if (layers < 1 || layers > numLayers) {
            throw new IllegalArgumentException("layers must be in [1, " + numLayers + "]");
        }
        return new GlmModelArgs(vocabSize, dim, layers, normEps, Arrays.copyOf(sparseAttention, layers),
                Arrays.copyOf(sparseMlp, layers), denseInterSize, numHeads, qkHeadDim, ropeHeadDim, vHeadDim,
                qLoraRank, kvLoraRank, kdaHeads, kdaHeadDim, kdaConvKernel, kdaGateLowerBound, indexHeads,
                indexHeadDim, indexTopk, indexKpool, indexAlwaysSelectTail, numRoutedExperts, numSharedExperts,
                numExpertsPerTok, moeInterSize, routeScale, swigluLimit, hcMult, hcSinkhornIters, hcEps, 0,
                imageTokenId, eosTokenIds, padTokenId, maxSeqLen);
    }

    /**
     * Returns a copy with another maximum sequence length.
     *
     * @param maxSeqLen the new limit.
     * @return the new arguments.
     */
    public GlmModelArgs withMaxSeqLen(int maxSeqLen) {
        return new GlmModelArgs(vocabSize, dim, numLayers, normEps, sparseAttention, sparseMlp, denseInterSize,
                numHeads, qkHeadDim, ropeHeadDim, vHeadDim, qLoraRank, kvLoraRank, kdaHeads, kdaHeadDim,
                kdaConvKernel, kdaGateLowerBound, indexHeads, indexHeadDim, indexTopk, indexKpool,
                indexAlwaysSelectTail, numRoutedExperts, numSharedExperts, numExpertsPerTok, moeInterSize,
                routeScale, swigluLimit, hcMult, hcSinkhornIters, hcEps, numMtpLayers, imageTokenId, eosTokenIds,
                padTokenId, maxSeqLen);
    }

    /**
     * Reads the arguments from a checkpoint's {@code config.json}.
     *
     * @param configJson the {@code config.json} file.
     * @return the arguments; {@code maxSeqLen} is {@code max_position_embeddings}.
     * @throws IOException if the file cannot be read.
     */
    public static GlmModelArgs load(File configJson) throws IOException {
        JsonNode root = new ObjectMapper().readTree(configJson);
        JsonNode t = root.has("text_config") ? root.get("text_config") : root;
        JsonNode lin = t.get("linear_attn_config");
        int layers = t.get("num_hidden_layers").asInt();
        boolean[] sparseAttn = new boolean[layers];
        boolean[] sparseMlp = new boolean[layers];
        JsonNode lt = t.get("layer_types");
        JsonNode mt = t.get("mlp_layer_types");
        for (int i = 0; i < layers; i++) {
            sparseAttn[i] = "deepseek_sparse_attention".equals(lt.get(i).asString());
            sparseMlp[i] = "sparse".equals(mt.get(i).asString());
        }
        JsonNode idx = t.get("indexer_types");
        for (int i = 0; idx != null && i < idx.size(); i++) {
            if (!"full".equals(idx.get(i).asString())) {
                throw new IllegalArgumentException("shared indexer layers are not supported (layer " + i + ")");
            }
        }
        return new GlmModelArgs(
                t.get("vocab_size").asInt(),
                t.get("hidden_size").asInt(),
                layers,
                t.get("rms_norm_eps").asDouble(),
                sparseAttn,
                sparseMlp,
                t.get("intermediate_size").asInt(),
                t.get("num_attention_heads").asInt(),
                t.get("qk_head_dim").asInt(),
                t.get("qk_rope_head_dim").asInt(),
                t.get("v_head_dim").asInt(),
                t.get("q_lora_rank").asInt(),
                t.get("kv_lora_rank").asInt(),
                lin.get("num_heads").asInt(),
                lin.get("head_dim").asInt(),
                lin.get("short_conv_kernel_size").asInt(),
                lin.get("gate_lower_bound").asDouble(),
                t.get("index_n_heads").asInt(),
                t.get("index_head_dim").asInt(),
                t.get("index_topk").asInt(),
                t.get("index_kpool").asInt(),
                t.path("index_kpool_always_select_tail").asBoolean(true),
                t.get("n_routed_experts").asInt(),
                t.path("n_shared_experts").asInt(1),
                t.get("num_experts_per_tok").asInt(),
                t.get("moe_intermediate_size").asInt(),
                t.get("routed_scaling_factor").asDouble(),
                t.path("swiglu_limit").asDouble(0.0),
                t.get("hc_mult").asInt(),
                t.get("hc_sinkhorn_iters").asInt(),
                t.get("hc_eps").asDouble(),
                t.path("num_nextn_predict_layers").asInt(0),
                root.path("image_token_id").asInt(154854),
                ints(t.path("eos_token_id")),
                t.path("pad_token_id").asInt(154820),
                t.path("max_position_embeddings").asInt(1 << 20));
    }

    private static int[] ints(JsonNode node) {
        if (node == null || node.isMissingNode()) {
            return new int[0];
        }
        if (!node.isArray()) {
            return new int[]{node.asInt()};
        }
        int[] out = new int[node.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = node.get(i).asInt();
        }
        return out;
    }
}
