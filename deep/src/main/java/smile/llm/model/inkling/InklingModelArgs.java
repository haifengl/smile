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
package smile.llm.model.inkling;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Inkling-2-Mini (model type {@code inkling2_loop}) text-tower hyperparameters.
 *
 * <p>The network has {@code numLayers} distinct decoder layers, but layers {@code [coreStart, coreEnd)} are applied
 * {@code numPasses} times, so one token goes through {@link #numApplications()} layer applications. Every application
 * owns its own attention cache and short-convolution state (see {@link #layerOfApplication(int)}).
 * Attention is relative-bias attention with sinks and no RoPE; layers not in {@code localLayerIds} are global.
 *
 * @param dim                  hidden size.
 * @param numLayers            distinct decoder layers.
 * @param vocabSize            embedding rows (padded).
 * @param unpaddedVocabSize    real vocabulary size (logits beyond it are masked).
 * @param numHeads             query heads of a global layer.
 * @param numKvHeads           key/value heads of a global layer.
 * @param headDim              head dimension of a global layer.
 * @param swaHeads             query heads of a local (sliding-window) layer.
 * @param swaKvHeads           key/value heads of a local layer.
 * @param swaHeadDim           head dimension of a local layer.
 * @param relDim               width of the relative-bias state per head ({@code d_rel}).
 * @param relExtent            how far back the relative bias reaches (positions).
 * @param normEps              RMSNorm epsilon.
 * @param valueNormNoGamma     whether values are RMS-normalized without a learned scale.
 * @param qkL2ClipThreshold    L2 clip of query/key heads.
 * @param relL2ClipThreshold   L2 clip of the relative-bias state.
 * @param logScalingNFloor     context length below which the global-layer log scaling is 1.
 * @param logScalingAlpha      global-layer log-scaling slope.
 * @param local                per-layer flag: {@code true} for a sliding-window layer, {@code false} for global.
 * @param slidingWindow        sliding-window size of local layers.
 * @param sconvKernel          short-convolution kernel size.
 * @param finalLogitSoftcap    {@code tanh} soft cap applied to the logits.
 * @param layersPerAttnResBlock layers per AttnRes block.
 * @param attnResNormalizeValues whether AttnRes normalizes the block values it mixes.
 * @param numExperts           routed experts per layer.
 * @param numExpertsPerTok     experts per token.
 * @param numSharedExperts     shared experts per layer.
 * @param intermediateSize     FFN width of one expert.
 * @param gateSoftcap          soft cap of the SwiGLU gate branch.
 * @param upSoftcap            soft cap of the SwiGLU up branch.
 * @param routeScale           multiplier on the routing weights.
 * @param useGateBias          whether the router has a selection bias.
 * @param coreStart            first looped layer.
 * @param coreEnd              one past the last looped layer.
 * @param numPasses            passes through the looped layers.
 * @param gateRank             rank of the loop message gates.
 * @param varLoop              whether the number of passes varies per token (unsupported).
 * @param eosTokenId           end-of-sampling token id.
 * @param maxSeqLen            maximum sequence length served.
 * @param experts              per-layer expert storage format.
 * @author Haifeng Li
 */
public record InklingModelArgs(
        int dim,
        int numLayers,
        int vocabSize,
        int unpaddedVocabSize,
        int numHeads,
        int numKvHeads,
        int headDim,
        int swaHeads,
        int swaKvHeads,
        int swaHeadDim,
        int relDim,
        int relExtent,
        double normEps,
        boolean valueNormNoGamma,
        double qkL2ClipThreshold,
        double relL2ClipThreshold,
        int logScalingNFloor,
        double logScalingAlpha,
        boolean[] local,
        int slidingWindow,
        int sconvKernel,
        double finalLogitSoftcap,
        int layersPerAttnResBlock,
        boolean attnResNormalizeValues,
        int numExperts,
        int numExpertsPerTok,
        int numSharedExperts,
        int intermediateSize,
        double gateSoftcap,
        double upSoftcap,
        double routeScale,
        boolean useGateBias,
        int coreStart,
        int coreEnd,
        int numPasses,
        int gateRank,
        boolean varLoop,
        int eosTokenId,
        int maxSeqLen,
        ExpertFormat[] experts) {

    /** Storage format of a layer's routed-expert weights. */
    public enum ExpertFormat {
        /** NVFP4: packed E2M1 nibbles, FP8 group-16 scales, per-expert amax and one outer denominator. */
        NVFP4,
        /** MXFP8: E4M3 bytes with E8M0 (power of two) scales per 32 along the reduction dimension. */
        MXFP8,
        /** Unquantized BF16 (only for hand-made slices). */
        BF16
    }

    /**
     * Returns whether a layer uses sliding-window attention.
     * @param layer zero-based layer index.
     * @return {@code true} for a local layer, {@code false} for a global layer.
     */
    public boolean isLocal(int layer) {
        check(layer);
        return local[layer];
    }

    /**
     * Returns whether a layer is looped (applied {@link #numPasses()} times).
     * @param layer zero-based layer index.
     * @return {@code true} if the layer lies in {@code [coreStart, coreEnd)}.
     */
    public boolean isLooped(int layer) {
        check(layer);
        return layer >= coreStart && layer < coreEnd;
    }

    /**
     * Returns the number of layer applications per token: {@code coreStart + passes * (coreEnd - coreStart) +
     * (numLayers - coreEnd)}.
     * @return the application count (48 for Inkling-2-Mini).
     */
    public int numApplications() {
        return coreStart + numPasses * (coreEnd - coreStart) + (numLayers - coreEnd);
    }

    /**
     * Maps an application index to its layer.
     * @param application zero-based application index.
     * @return the layer applied there.
     */
    public int layerOfApplication(int application) {
        if (application < 0 || application >= numApplications()) {
            throw new IndexOutOfBoundsException("application " + application);
        }
        int core = coreEnd - coreStart;
        if (application < coreStart) {
            return application;
        }
        int rel = application - coreStart;
        if (rel < numPasses * core) {
            return coreStart + rel % core;
        }
        return coreEnd + (rel - numPasses * core);
    }

    /**
     * Maps an application index to its pass (0 outside the loop and for the first pass).
     * @param application zero-based application index.
     * @return the pass index.
     */
    public int passOfApplication(int application) {
        layerOfApplication(application);
        int core = coreEnd - coreStart;
        int rel = application - coreStart;
        return rel >= 0 && rel < numPasses * core ? rel / core : 0;
    }

    /**
     * Returns the number of applications of local (sliding-window) layers, each with its own cache.
     * @return local application count.
     */
    public int numLocalApplications() {
        int n = 0;
        for (int a = 0; a < numApplications(); a++) {
            if (local[layerOfApplication(a)]) {
                n++;
            }
        }
        return n;
    }

    /**
     * Returns the number of applications of global layers, each with a full-history cache.
     * @return global application count.
     */
    public int numGlobalApplications() {
        return numApplications() - numLocalApplications();
    }

    /**
     * Returns the expert format of a layer.
     * @param layer zero-based layer index.
     * @return the format.
     */
    public ExpertFormat expertFormat(int layer) {
        check(layer);
        return experts[layer];
    }

    /**
     * Returns a copy with another maximum sequence length.
     * @param length new maximum, positive.
     * @return the new args.
     */
    public InklingModelArgs withMaxSeqLen(int length) {
        return new InklingModelArgs(dim, numLayers, vocabSize, unpaddedVocabSize, numHeads, numKvHeads, headDim,
                swaHeads, swaKvHeads, swaHeadDim, relDim, relExtent, normEps, valueNormNoGamma, qkL2ClipThreshold,
                relL2ClipThreshold, logScalingNFloor, logScalingAlpha, local, slidingWindow, sconvKernel,
                finalLogitSoftcap, layersPerAttnResBlock, attnResNormalizeValues, numExperts, numExpertsPerTok,
                numSharedExperts, intermediateSize, gateSoftcap, upSoftcap, routeScale, useGateBias, coreStart,
                coreEnd, numPasses, gateRank, varLoop, eosTokenId, length, experts);
    }

    private void check(int layer) {
        if (layer < 0 || layer >= numLayers) {
            throw new IndexOutOfBoundsException("layer " + layer);
        }
    }

    /**
     * Reads the hyperparameters of a checkpoint directory ({@code config.json} and, for the expert formats,
     * {@code hf_quant_config.json}).
     *
     * @param dir       checkpoint directory.
     * @param maxSeqLen maximum sequence length; {@code <= 0} uses {@code model_max_length}.
     * @return the args.
     * @throws IOException if {@code config.json} is missing or malformed.
     */
    public static InklingModelArgs fromCheckpoint(String dir, int maxSeqLen) throws IOException {
        File config = new File(dir, "config.json");
        if (!config.exists()) {
            throw new IOException("config.json not found in " + dir);
        }
        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(config);
        Map<Integer, ExpertFormat> formats = new HashMap<>();
        Path quant = Path.of(dir, "hf_quant_config.json");
        if (Files.exists(quant)) {
            formats = expertFormats(mapper.readTree(quant.toFile()));
        }
        int eos = root.has("eos_token_id") ? root.get("eos_token_id").asInt() : 200006;
        return fromConfig(root, eos, maxSeqLen, formats);
    }

    /**
     * Collects the per-layer expert format from the parameter groups of {@code hf_quant_config.json}.
     * @param quant the parsed {@code hf_quant_config.json}.
     * @return layer index to format.
     */
    static Map<Integer, ExpertFormat> expertFormats(JsonNode quant) {
        Map<Integer, ExpertFormat> out = new HashMap<>();
        for (JsonNode group : quant.path("model").path("parameter_groups")) {
            String dtype = group.path("compute_dtype").asString("");
            ExpertFormat f = switch (dtype) {
                case "nvfp4" -> ExpertFormat.NVFP4;
                case "mxfp8" -> ExpertFormat.MXFP8;
                default -> null;
            };
            if (f == null) {
                continue;
            }
            for (JsonNode t : group.path("tensors")) {
                String name = t.asString();
                String prefix = "model.llm.layers.";
                if (name.startsWith(prefix) && name.contains(".mlp.experts.")) {
                    String rest = name.substring(prefix.length());
                    out.put(Integer.parseInt(rest.substring(0, rest.indexOf('.'))), f);
                }
            }
        }
        return out;
    }

    /**
     * Parses {@code config.json}.
     *
     * @param root      the parsed config (with {@code text_config}).
     * @param eos       end-of-sampling token id.
     * @param maxSeqLen maximum sequence length; {@code <= 0} uses {@code model_max_length}.
     * @param formats   expert format per layer; missing layers default to {@link ExpertFormat#BF16}.
     * @return the args.
     */
    static InklingModelArgs fromConfig(JsonNode root, int eos, int maxSeqLen, Map<Integer, ExpertFormat> formats) {
        JsonNode t = root.has("text_config") ? root.get("text_config") : root;
        int numLayers = t.get("num_hidden_layers").asInt();
        int heads = t.get("num_attention_heads").asInt();
        int kvHeads = t.get("num_key_value_heads").asInt();
        int headDim = t.get("head_dim").asInt();
        boolean[] local = new boolean[numLayers];
        for (JsonNode id : t.get("local_layer_ids")) {
            local[id.asInt()] = true;
        }
        JsonNode loop = t.get("loop_config");
        boolean varLoop = loop.path("is_varloop").asBoolean(false);
        if (varLoop) {
            throw new IllegalArgumentException("variable-depth loops (is_varloop) are not supported");
        }
        int coreStart = loop.get("core_start").asInt();
        int coreEnd = loop.get("core_end").asInt();
        int passes = loop.get("num_passes").asInt();
        if (coreStart < 0 || coreEnd <= coreStart || coreEnd > numLayers || passes < 1) {
            throw new IllegalArgumentException("invalid loop_config " + loop);
        }
        int len = maxSeqLen > 0 ? maxSeqLen : t.get("model_max_length").asInt();
        ExpertFormat[] experts = new ExpertFormat[numLayers];
        Arrays.setAll(experts, i -> formats.getOrDefault(i, ExpertFormat.BF16));
        return new InklingModelArgs(
                t.get("hidden_size").asInt(), numLayers, t.get("vocab_size").asInt(),
                t.path("unpadded_vocab_size").asInt(t.get("vocab_size").asInt()),
                heads, kvHeads, headDim,
                t.path("swa_num_attention_heads").asInt(heads), t.path("swa_num_key_value_heads").asInt(kvHeads),
                t.path("swa_head_dim").asInt(headDim),
                t.get("d_rel").asInt(), t.get("rel_extent").asInt(),
                t.get("rms_norm_eps").asDouble(), t.path("use_value_norm_no_gamma").asBoolean(false),
                t.get("qk_l2_clip_threshold").asDouble(), t.get("rel_l2_clip_threshold").asDouble(),
                t.get("log_scaling_n_floor").asInt(), t.get("log_scaling_alpha").asDouble(),
                local, t.get("sliding_window_size").asInt(), t.get("sconv_kernel_size").asInt(),
                t.get("final_logit_softcapping").asDouble(),
                t.get("n_layers_per_attn_res_block").asInt(), t.path("attn_res_normalize_values").asBoolean(true),
                t.get("n_routed_experts").asInt(), t.get("num_experts_per_tok").asInt(),
                t.path("n_shared_experts").asInt(0), t.get("intermediate_size").asInt(),
                t.get("mlp_gate_softcap").asDouble(), t.get("mlp_up_softcap").asDouble(),
                t.get("route_scale").asDouble(), t.path("use_gate_bias").asBoolean(false),
                coreStart, coreEnd, passes, loop.get("gate_rank").asInt(), false,
                eos, len, experts);
    }
}
