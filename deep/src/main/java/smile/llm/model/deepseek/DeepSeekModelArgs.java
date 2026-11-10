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
package smile.llm.model.deepseek;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * DeepSeek-V4.1 text-tower hyperparameters (checkpoint {@code config.json}, section {@code text_config}).
 *
 * <p>The architecture: a residual stream of {@code hcMult} parallel copies mixed by Hyper-Connections; a
 * single-KV-head attention with an attention sink over a sliding window plus compressed KV selected by a
 * two-level indexer; n-gram "engram" lookups in a few layers; and a sqrt-softplus routed MoE with FP4 experts.
 * Layers {@code numLayers ..} of {@link #compressRatios} belong to the MTP/DSpark head.
 *
 * @param vocabSize            vocabulary size.
 * @param dim                  hidden size.
 * @param numLayers            backbone layers.
 * @param numHeads             query heads (one shared KV head).
 * @param headDim              attention head dimension (rope dims are the last {@code ropeHeadDim}).
 * @param ropeHeadDim          rotated head dimensions.
 * @param qLoraRank            query low-rank dimension.
 * @param oLoraRank            output low-rank dimension per group.
 * @param oGroups              output projection groups.
 * @param normEps              RMSNorm epsilon ({@code 1e-20} in the released model).
 * @param moeInterSize         routed/shared expert FFN width.
 * @param numRoutedExperts     routed experts per layer.
 * @param numSharedExperts     shared experts per layer (1).
 * @param numExpertsPerTok     experts per token.
 * @param routeScale           multiplier on the renormalised routing weights.
 * @param swigluLimit          expert activation clamp.
 * @param windowSize           sliding-window length.
 * @param compressRatios       per layer (backbone then MTP): 0 = window only, r = KV compressed r-to-1.
 * @param kvSourceLayers       layers that compute the compressed KV the following layers share.
 * @param indexSourceLayers    layers that run an indexer (the others reuse the last result).
 * @param originalSeqLen       YaRN original context length.
 * @param ropeTheta            RoPE base for window-only layers.
 * @param compressRopeTheta    RoPE base for layers with compressed KV.
 * @param ropeFactor           YaRN factor.
 * @param betaFast             YaRN beta fast.
 * @param betaSlow             YaRN beta slow.
 * @param indexNumHeads        indexer heads.
 * @param indexHeadDim         indexer head dimension.
 * @param indexTopk            compressed positions kept per query.
 * @param candidateSourceLayer layer whose block scores restrict later indexers ({@code -1} = off).
 * @param candidateTopkBlocks  candidate blocks kept.
 * @param candidateBlockSize   positions per candidate block.
 * @param hcMult               residual copies.
 * @param hcSinkhornIters      Sinkhorn iterations of the connection mixing.
 * @param hcEps                Hyper-Connections epsilon.
 * @param engramLayers         layers with an engram lookup.
 * @param engramNumEmbeddings  table rows per engram layer.
 * @param engramMaxNgram       largest n-gram (n-grams of size 2..n are hashed).
 * @param engramVocabSize      bucket size each (n-gram, head) starts searching primes from.
 * @param engramHeads          hash heads per n-gram.
 * @param engramHeadDim        table row width.
 * @param engramPadId          token filling n-gram slots without history.
 * @param engramCompressedVocab compressed vocabulary size (every hash multiplier derives from it).
 * @param numMtpLayers         MTP/DSpark layers.
 * @param dsparkBlockSize      DSpark block size.
 * @param dsparkNoiseToken     DSpark mask token.
 * @param dsparkTargetLayers   backbone layers tapped by the DSpark head.
 * @param dsparkMarkovRank     DSpark Markov head rank.
 * @param dsparkRoutedExperts  routed experts of the MTP layers.
 * @param dsparkExpertsPerTok  experts per token of the MTP layers.
 * @param imageTokenId         id of the image placeholder token.
 * @param maxSeqLen            maximum sequence length served (runtime, not from the config).
 *
 * @author Haifeng Li
 */
public record DeepSeekModelArgs(
        int vocabSize,
        int dim,
        int numLayers,
        int numHeads,
        int headDim,
        int ropeHeadDim,
        int qLoraRank,
        int oLoraRank,
        int oGroups,
        double normEps,
        int moeInterSize,
        int numRoutedExperts,
        int numSharedExperts,
        int numExpertsPerTok,
        double routeScale,
        double swigluLimit,
        int windowSize,
        int[] compressRatios,
        int[] kvSourceLayers,
        int[] indexSourceLayers,
        int originalSeqLen,
        double ropeTheta,
        double compressRopeTheta,
        double ropeFactor,
        int betaFast,
        int betaSlow,
        int indexNumHeads,
        int indexHeadDim,
        int indexTopk,
        int candidateSourceLayer,
        int candidateTopkBlocks,
        int candidateBlockSize,
        int hcMult,
        int hcSinkhornIters,
        double hcEps,
        int[] engramLayers,
        long[] engramNumEmbeddings,
        int engramMaxNgram,
        int engramVocabSize,
        int engramHeads,
        int engramHeadDim,
        int engramPadId,
        int engramCompressedVocab,
        int numMtpLayers,
        int dsparkBlockSize,
        int dsparkNoiseToken,
        int[] dsparkTargetLayers,
        int dsparkMarkovRank,
        int dsparkRoutedExperts,
        int dsparkExpertsPerTok,
        int imageTokenId,
        int maxSeqLen) {

    /**
     * Validates the shape relations the model code relies on.
     */
    public DeepSeekModelArgs {
        if (compressRatios.length < numLayers) {
            throw new IllegalArgumentException("compress_ratios has " + compressRatios.length
                    + " entries for " + numLayers + " layers");
        }
        if (ropeHeadDim <= 0 || ropeHeadDim > headDim || ropeHeadDim % 2 != 0) {
            throw new IllegalArgumentException("invalid rope_head_dim " + ropeHeadDim);
        }
        if ((numHeads * headDim) % oGroups != 0) {
            throw new IllegalArgumentException("o_groups must divide num_heads * head_dim");
        }
        if (engramLayers.length != engramNumEmbeddings.length) {
            throw new IllegalArgumentException("engram layer and table size lists differ in length");
        }
        compressRatios = compressRatios.clone();
        kvSourceLayers = kvSourceLayers.clone();
        indexSourceLayers = indexSourceLayers.clone();
        engramLayers = engramLayers.clone();
        engramNumEmbeddings = engramNumEmbeddings.clone();
        dsparkTargetLayers = dsparkTargetLayers.clone();
    }

    /**
     * Compression ratio of a layer.
     *
     * @param layer layer index (backbone, or {@code numLayers + k} for MTP layer k).
     * @return 0 for window-only attention, otherwise the KV compression ratio.
     */
    public int compressRatio(int layer) {
        return compressRatios[layer];
    }

    /**
     * Whether the layer computes (and owns the cache of) the compressed KV.
     *
     * @param layer backbone layer index.
     * @return {@code true} for the KV source layers.
     */
    public boolean isKvSource(int layer) {
        return layer < numLayers && contains(kvSourceLayers, layer);
    }

    /**
     * Whether the layer runs an indexer.
     *
     * @param layer backbone layer index.
     * @return {@code true} for the index source layers.
     */
    public boolean isIndexSource(int layer) {
        return layer < numLayers && contains(indexSourceLayers, layer);
    }

    /**
     * Whether the layer is the candidate-block source of the two-level top-k.
     *
     * @param layer backbone layer index.
     * @return {@code true} for the candidate source layer.
     */
    public boolean isCandidateSource(int layer) {
        return layer == candidateSourceLayer;
    }

    /**
     * Whether the layer's indexer only scores inside the candidate blocks.
     *
     * @param layer backbone layer index.
     * @return {@code true} when a candidate source precedes the layer.
     */
    public boolean usesCandidates(int layer) {
        return candidateSourceLayer >= 0 && candidateSourceLayer < layer;
    }

    /**
     * Index of the layer in {@link #engramLayers}.
     *
     * @param layer backbone layer index.
     * @return the engram slot, or -1 when the layer has no engram.
     */
    public int engramSlot(int layer) {
        for (int i = 0; i < engramLayers.length; i++) {
            if (engramLayers[i] == layer) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Number of hashed columns per token and engram layer ({@code (maxNgram - 1) * heads}).
     *
     * @return the hash column count.
     */
    public int engramHashColumns() {
        return (engramMaxNgram - 1) * engramHeads;
    }

    /**
     * Routed experts and experts per token of a layer (the MTP layers use their own counts).
     *
     * @param layer layer index.
     * @return {@code {routed, perToken}}.
     */
    public int[] moeConfig(int layer) {
        if (layer < numLayers) {
            return new int[]{numRoutedExperts, numExpertsPerTok};
        }
        return new int[]{dsparkRoutedExperts > 0 ? dsparkRoutedExperts : numRoutedExperts,
                dsparkExpertsPerTok > 0 ? dsparkExpertsPerTok : numExpertsPerTok};
    }

    /**
     * Returns the arguments of a truncated model made of the first layers, for slice validation. Engram layers
     * beyond the slice are dropped; the remaining engram layers keep their order, so primes and multipliers of a
     * slice equal those of the full model.
     *
     * @param layers number of backbone layers to keep.
     * @return the truncated arguments.
     */
    public DeepSeekModelArgs slice(int layers) {
        if (layers < 1 || layers > numLayers) {
            throw new IllegalArgumentException("layers must be in [1, " + numLayers + "]");
        }
        int keep = 0;
        while (keep < engramLayers.length && engramLayers[keep] < layers) {
            keep++;
        }
        return new DeepSeekModelArgs(vocabSize, dim, layers, numHeads, headDim, ropeHeadDim, qLoraRank, oLoraRank,
                oGroups, normEps, moeInterSize, numRoutedExperts, numSharedExperts, numExpertsPerTok, routeScale,
                swigluLimit, windowSize, Arrays.copyOf(compressRatios, layers),
                Arrays.stream(kvSourceLayers).filter(l -> l < layers).toArray(),
                Arrays.stream(indexSourceLayers).filter(l -> l < layers).toArray(),
                originalSeqLen, ropeTheta, compressRopeTheta, ropeFactor, betaFast, betaSlow, indexNumHeads,
                indexHeadDim, indexTopk, candidateSourceLayer < layers ? candidateSourceLayer : -1,
                candidateTopkBlocks, candidateBlockSize, hcMult, hcSinkhornIters, hcEps,
                Arrays.copyOf(engramLayers, keep), Arrays.copyOf(engramNumEmbeddings, keep), engramMaxNgram,
                engramVocabSize, engramHeads, engramHeadDim, engramPadId, engramCompressedVocab, 0, 0,
                dsparkNoiseToken, new int[0], dsparkMarkovRank, dsparkRoutedExperts, dsparkExpertsPerTok,
                imageTokenId, maxSeqLen);
    }

    /**
     * Returns a copy with another maximum sequence length.
     *
     * @param maxSeqLen the new limit.
     * @return the new arguments.
     */
    public DeepSeekModelArgs withMaxSeqLen(int maxSeqLen) {
        return new DeepSeekModelArgs(vocabSize, dim, numLayers, numHeads, headDim, ropeHeadDim, qLoraRank,
                oLoraRank, oGroups, normEps, moeInterSize, numRoutedExperts, numSharedExperts, numExpertsPerTok,
                routeScale, swigluLimit, windowSize, compressRatios, kvSourceLayers, indexSourceLayers,
                originalSeqLen, ropeTheta, compressRopeTheta, ropeFactor, betaFast, betaSlow, indexNumHeads,
                indexHeadDim, indexTopk, candidateSourceLayer, candidateTopkBlocks, candidateBlockSize, hcMult,
                hcSinkhornIters, hcEps, engramLayers, engramNumEmbeddings, engramMaxNgram, engramVocabSize,
                engramHeads, engramHeadDim, engramPadId, engramCompressedVocab, numMtpLayers, dsparkBlockSize,
                dsparkNoiseToken, dsparkTargetLayers, dsparkMarkovRank, dsparkRoutedExperts, dsparkExpertsPerTok,
                imageTokenId, maxSeqLen);
    }

    /**
     * Reads the arguments from a checkpoint's {@code config.json}.
     *
     * @param configJson the {@code config.json} file.
     * @return the arguments; {@code maxSeqLen} is {@code max_position_embeddings}.
     * @throws IOException if the file cannot be read.
     */
    public static DeepSeekModelArgs load(File configJson) throws IOException {
        JsonNode root = new ObjectMapper().readTree(configJson);
        JsonNode t = root.has("text_config") ? root.get("text_config") : root;
        JsonNode rope = t.path("rope_scaling");
        long[] tables = longs(t.path("engram_num_embeddings"));
        return new DeepSeekModelArgs(
                t.get("vocab_size").asInt(),
                t.get("hidden_size").asInt(),
                t.get("num_hidden_layers").asInt(),
                t.get("num_attention_heads").asInt(),
                t.get("head_dim").asInt(),
                t.get("qk_rope_head_dim").asInt(),
                t.get("q_lora_rank").asInt(),
                t.get("o_lora_rank").asInt(),
                t.get("o_groups").asInt(),
                t.get("rms_norm_eps").asDouble(),
                t.get("moe_intermediate_size").asInt(),
                t.get("n_routed_experts").asInt(),
                t.path("n_shared_experts").asInt(1),
                t.get("num_experts_per_tok").asInt(),
                t.get("routed_scaling_factor").asDouble(),
                t.path("swiglu_limit").asDouble(0.0),
                t.get("sliding_window").asInt(),
                ints(t.get("compress_ratios")),
                ints(t.get("kv_source_layer_ids")),
                ints(t.get("index_source_layer_ids")),
                rope.path("original_max_position_embeddings").asInt(0),
                t.get("rope_theta").asDouble(),
                t.get("compress_rope_theta").asDouble(),
                rope.path("factor").asDouble(1.0),
                rope.path("beta_fast").asInt(32),
                rope.path("beta_slow").asInt(1),
                t.get("index_n_heads").asInt(),
                t.get("index_head_dim").asInt(),
                t.get("index_topk").asInt(),
                t.path("candidate_source_layer_id").asInt(-1),
                t.path("candidate_topk_blocks").asInt(0),
                t.path("candidate_block_size").asInt(0),
                t.get("hc_mult").asInt(),
                t.get("hc_sinkhorn_iters").asInt(),
                t.get("hc_eps").asDouble(),
                ints(t.path("engram_layer_ids")),
                tables,
                t.path("engram_max_ngram_size").asInt(1),
                t.path("engram_vocab_size").asInt(0),
                t.path("engram_n_heads").asInt(0),
                t.path("engram_head_dim").asInt(0),
                t.path("engram_pad_token_id").asInt(2),
                t.path("engram_compressed_vocab_size").asInt(0),
                t.path("num_nextn_predict_layers").asInt(0),
                t.path("dspark_block_size").asInt(0),
                t.path("dspark_noise_token_id").asInt(0),
                ints(t.path("dspark_target_layer_ids")),
                t.path("dspark_markov_rank").asInt(256),
                t.path("dspark_n_routed_experts").asInt(0),
                t.path("dspark_num_experts_per_tok").asInt(0),
                root.path("image_token_id").asInt(129264),
                t.path("max_position_embeddings").asInt(65536));
    }

    private static boolean contains(int[] a, int v) {
        for (int x : a) {
            if (x == v) {
                return true;
            }
        }
        return false;
    }

    private static int[] ints(JsonNode node) {
        if (node == null || !node.isArray()) {
            return new int[0];
        }
        int[] out = new int[node.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = node.get(i).asInt();
        }
        return out;
    }

    private static long[] longs(JsonNode node) {
        if (node == null || !node.isArray()) {
            return new long[0];
        }
        long[] out = new long[node.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = node.get(i).asLong();
        }
        return out;
    }
}
