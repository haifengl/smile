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
 * Hyperparameters of the MiniMax-M3 vision encoder (CLIP-style ViT with 3D RoPE), the projector and the
 * 2x2 patch merger, read from {@code vision_config} of {@code config.json}.
 *
 * @param hiddenSize        ViT width.
 * @param numHeads          attention heads.
 * @param numLayers         encoder layers.
 * @param intermediateSize  MLP width.
 * @param patchSize         spatial patch edge in pixels.
 * @param temporalPatchSize frames folded into one patch.
 * @param mergeSize         spatial merge factor (merge x merge patches become one token).
 * @param numChannels       input channels.
 * @param layerNormEps      LayerNorm epsilon.
 * @param ropeTheta         3D RoPE base.
 * @param maxSegmentFrames  attention never spans more than this many temporal groups (longer grids are split).
 * @param textHidden        width of the language model embeddings (projector output).
 * @param projectorHidden   projector / merger hidden width.
 * @param imageTokenId      placeholder token id of image patches.
 * @param videoTokenId      placeholder token id of video patches.
 *
 * @author Haifeng Li
 */
public record MiniMaxVisionArgs(
        int hiddenSize,
        int numHeads,
        int numLayers,
        int intermediateSize,
        int patchSize,
        int temporalPatchSize,
        int mergeSize,
        int numChannels,
        double layerNormEps,
        double ropeTheta,
        int maxSegmentFrames,
        int textHidden,
        int projectorHidden,
        int imageTokenId,
        int videoTokenId) {

    /** @return per-head width. */
    public int headDim() {
        return hiddenSize / numHeads;
    }

    /** @return length of one flattened patch ({@code C * T * P * P}). */
    public int patchDim() {
        return numChannels * temporalPatchSize * patchSize * patchSize;
    }

    /** @return rotary dimensions per axis (t, h, w each get this many). */
    public int axisDim() {
        int ropeDims = 2 * (headDim() / 2);
        return 2 * ((ropeDims / 3) / 2);
    }

    /** @return rotated dimensions of each head ({@code 3 * axisDim}); the remaining head dims pass through. */
    public int rotaryDim() {
        return 3 * axisDim();
    }

    /**
     * Parses a checkpoint's {@code config.json}.
     *
     * @param configFile the file.
     * @return the vision arguments, or {@code null} when the config has no {@code vision_config}.
     * @throws IOException if the file cannot be read.
     */
    public static MiniMaxVisionArgs fromConfigFile(File configFile) throws IOException {
        return fromConfig(new ObjectMapper().readTree(configFile));
    }

    /**
     * Parses the top-level config object.
     *
     * @param root the parsed {@code config.json}.
     * @return the vision arguments, or {@code null} when there is no {@code vision_config}.
     */
    public static MiniMaxVisionArgs fromConfig(JsonNode root) {
        JsonNode v = root.get("vision_config");
        if (v == null || v.isNull()) {
            return null;
        }
        JsonNode compression = v.path("img_token_compression_config");
        int textHidden = root.path("text_config").path("hidden_size").asInt(v.path("projection_dim").asInt(6144));
        return new MiniMaxVisionArgs(
                v.path("hidden_size").asInt(1280),
                v.path("num_attention_heads").asInt(16),
                v.path("num_hidden_layers").asInt(32),
                v.path("intermediate_size").asInt(5120),
                v.path("patch_size").asInt(14),
                compression.path("temporal_patch_size").asInt(2),
                compression.path("spatial_merge_size").asInt(2),
                v.path("num_channels").asInt(3),
                v.path("layer_norm_eps").asDouble(1e-5),
                v.path("rope_theta").asDouble(10000.0),
                v.path("vision_segment_max_frames").asInt(4),
                textHidden,
                root.path("projector_hidden_size").asInt(textHidden),
                root.path("image_token_index").asInt(200025),
                root.path("video_token_index").asInt(200026));
    }
}
