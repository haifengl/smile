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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Inkling-2 vision tower and image processor hyperparameters.
 *
 * @param hidden       ViT width.
 * @param layers       encoder layers.
 * @param heads        attention heads.
 * @param patch        native patch size in pixels.
 * @param merge        side, in native patches, of the square merged into one language-model token.
 * @param posGrid      side of the learned position-embedding grid.
 * @param intermediate MLP width of an encoder layer.
 * @param projection   hidden width of the projector.
 * @param textHidden   language-model width.
 * @param eps          LayerNorm epsilon.
 * @param ropeTheta    base of the axial rotary embedding.
 * @param tile         side of an image tile (one language-model token) in pixels: {@code patch * merge}.
 * @param imageToken   the placeholder token id.
 * @param maxTokens    largest number of tokens one image may take; larger images are scaled down.
 *
 * @author Haifeng Li
 */
public record InklingVisionArgs(int hidden, int layers, int heads, int patch, int merge, int posGrid, int intermediate,
                                int projection, int textHidden, double eps, double ropeTheta, int tile, int imageToken,
                                int maxTokens) {
    /** The image placeholder token of Inkling-2 ({@code <|unused_200054|>}). */
    public static final int IMAGE_TOKEN = 200054;

    /**
     * Reads {@code vision_config} of a checkpoint's {@code config.json}.
     *
     * @param dir checkpoint directory.
     * @return the arguments, or {@code null} when the checkpoint has no vision config.
     * @throws IOException if {@code config.json} cannot be read.
     */
    public static InklingVisionArgs load(String dir) throws IOException {
        JsonNode root = new ObjectMapper().readTree(new File(dir, "config.json"));
        JsonNode v = root.get("vision_config");
        if (v == null) {
            return null;
        }
        int hidden = v.path("hidden_size").asInt(1024);
        int patch = v.path("native_patch_size").asInt(v.path("patch_size").asInt(20));
        int merge = v.path("adapter_merge_size").asInt(v.path("spatial_merge_size").asInt(2));
        int inter = v.has("intermediate_size") ? v.get("intermediate_size").asInt()
                : (int) (hidden * v.path("mlp_ratio").asDouble(4.0));
        double theta = v.path("rope_parameters").path("rope_theta").asDouble(10000.0);
        return new InklingVisionArgs(hidden, v.path("num_hidden_layers").asInt(23), v.path("num_attention_heads").asInt(16),
                patch, merge, v.path("posemb_grid_size").asInt(32), inter, v.path("projection_hidden_dim").asInt(4096),
                v.path("text_hidden_size").asInt(2304), v.path("layer_norm_eps").asDouble(1e-5), theta, patch * merge,
                root.path("image_token_id").asInt(IMAGE_TOKEN), Integer.getInteger("smile.inkling.vision.max.tokens", 2048));
    }
}
