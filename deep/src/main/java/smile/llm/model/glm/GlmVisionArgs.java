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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Hyperparameters of the GLM-5.3-Flash vision encoder and its image processor.
 *
 * @param depth                 transformer blocks.
 * @param hiddenSize            width of the blocks.
 * @param numHeads              attention heads.
 * @param intermediateSize      width of the block MLPs.
 * @param outHiddenSize         width of the merged features (the language model's hidden size).
 * @param projectionIntermediate width of the patch merger's MLP.
 * @param patchSize             pixels per patch side.
 * @param temporalPatchSize     frames per patch (an image repeats its frame).
 * @param mergeSize             patches merged per side into one token.
 * @param inChannels            image channels.
 * @param rmsNormEps            epsilon of the RMS norms.
 * @param swigluLimit           clamp of the SwiGLU gate and up values.
 * @param ropeTheta             base of the axial rotary embedding.
 * @param imageTokenId          the placeholder token of an image feature.
 * @param minImageTokens        smallest number of tokens of an image.
 * @param maxImageTokens        largest number of tokens of an image.
 * @param mean                  per-channel normalization mean.
 * @param std                   per-channel normalization standard deviation.
 *
 * @author Haifeng Li
 */
public record GlmVisionArgs(int depth, int hiddenSize, int numHeads, int intermediateSize, int outHiddenSize,
                            int projectionIntermediate, int patchSize, int temporalPatchSize, int mergeSize, int inChannels,
                            double rmsNormEps, double swigluLimit, double ropeTheta, int imageTokenId, int minImageTokens,
                            int maxImageTokens, double[] mean, double[] std) {
    /** Values of one patch row the processor produces: channels * patch * patch (the repeated frame is dropped). */
    public int patchDim() {
        return inChannels * patchSize * patchSize;
    }

    /** Head width of the vision attention. */
    public int headDim() {
        return hiddenSize / numHeads;
    }

    /**
     * Reads the arguments of a checkpoint, or returns {@code null} when it has no vision config.
     *
     * @param dir the checkpoint directory ({@code config.json}, {@code processor_config.json}).
     * @return the arguments, or {@code null}.
     * @throws IOException if a file cannot be read.
     */
    public static GlmVisionArgs load(Path dir) throws IOException {
        Path cfg = dir.resolve("config.json");
        if (!Files.exists(cfg)) {
            return null;
        }
        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(cfg.toFile());
        JsonNode v = root.get("vision_config");
        if (v == null || v.isMissingNode()) {
            return null;
        }
        int minTokens = 16;
        int maxTokens = 8000;
        double[] mean = {0.48145466, 0.4578275, 0.40821073};
        double[] std = {0.26862954, 0.26130258, 0.27577711};
        Path pc = dir.resolve("processor_config.json");
        if (Files.exists(pc)) {
            JsonNode ip = mapper.readTree(pc.toFile()).path("image_processor");
            minTokens = ip.path("min_image_tokens").asInt(minTokens);
            maxTokens = ip.path("max_image_tokens").asInt(maxTokens);
            if (ip.has("image_mean")) {
                for (int i = 0; i < 3; i++) {
                    mean[i] = ip.get("image_mean").get(i).asDouble();
                    std[i] = ip.get("image_std").get(i).asDouble();
                }
            }
        }
        return new GlmVisionArgs(v.get("depth").asInt(), v.get("hidden_size").asInt(), v.get("num_heads").asInt(),
                v.get("intermediate_size").asInt(), v.get("out_hidden_size").asInt(),
                v.get("projection_intermediate_size").asInt(), v.get("patch_size").asInt(),
                v.path("temporal_patch_size").asInt(2), v.path("spatial_merge_size").asInt(2), v.path("in_channels").asInt(3),
                v.path("rms_norm_eps").asDouble(1e-5), v.path("swiglu_limit").asDouble(10.0),
                v.path("rope_parameters").path("rope_theta").asDouble(10000.0), root.path("image_token_id").asInt(154854),
                minTokens, maxTokens, mean, std);
    }
}
