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
package smile.llm.model.qwen;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Qwen3.8 / Qwen3.5 native vision-tower hyperparameters plus multimodal token ids.
 *
 * <p>DeepStack indexes are parsed but unused when empty (Qwen3.8-27B).
 *
 * @param depth                   ViT block count.
 * @param hiddenSize              ViT width.
 * @param intermediateSize        ViT MLP width.
 * @param numHeads                ViT attention heads.
 * @param inChannels              input channels (typically 3).
 * @param patchSize               spatial patch size.
 * @param temporalPatchSize       temporal patch size (images duplicate to fill).
 * @param spatialMergeSize        2×2 spatial merge factor.
 * @param outHiddenSize           merger output (= text hidden size).
 * @param numPositionEmbeddings   learned 2D absolute pos-embed table length.
 * @param deepstackVisualIndexes  DeepStack layer indexes (empty = disabled).
 * @param imageTokenId            {@code <|image_pad|>} id.
 * @param videoTokenId            {@code <|video_pad|>} id.
 * @param visionStartTokenId      {@code <|vision_start|>} id.
 * @param visionEndTokenId        {@code <|vision_end|>} id.
 * @param mropeInterleaved        whether LLM RoPE uses interleaved mRoPE.
 * @param mropeSection            T/H/W section widths over half-freqs (e.g. 11,11,10).
 * @author Haifeng Li
 */
public record QwenVisionArgs(
        int depth,
        int hiddenSize,
        int intermediateSize,
        int numHeads,
        int inChannels,
        int patchSize,
        int temporalPatchSize,
        int spatialMergeSize,
        int outHiddenSize,
        int numPositionEmbeddings,
        int[] deepstackVisualIndexes,
        int imageTokenId,
        int videoTokenId,
        int visionStartTokenId,
        int visionEndTokenId,
        boolean mropeInterleaved,
        int[] mropeSection) {

    /** Tiny defaults for unit tests. */
    public QwenVisionArgs() {
        this(2, 64, 128, 4, 3, 16, 2, 2, 64, 256,
                new int[0], 56, 57, 53, 54, true, new int[]{1, 1, 0});
    }

    /**
     * Flattened Conv3d patch input features {@code in * t * p * p}.
     *
     * @return patch feature count (e.g. 1536).
     */
    public int patchDim() {
        return inChannels * temporalPatchSize * patchSize * patchSize;
    }

    /**
     * ViT head dimension.
     *
     * @return {@code hiddenSize / numHeads}.
     */
    public int headDim() {
        return hiddenSize / numHeads;
    }

    /**
     * @return {@code true} when DeepStack fusion is configured.
     */
    public boolean hasDeepStack() {
        return deepstackVisualIndexes != null && deepstackVisualIndexes.length > 0;
    }

    /**
     * Merged vision token count for one media grid.
     *
     * @param t temporal patches.
     * @param h height patches.
     * @param w width patches.
     * @return LLM visual token count.
     */
    public int mergedTokens(int t, int h, int w) {
        int m = spatialMergeSize;
        return (t * h * w) / (m * m);
    }

    /**
     * Loads vision args and multimodal token ids from a HuggingFace {@code config.json}.
     *
     * @param path path to {@code config.json}.
     * @return vision args, or {@code null} when no {@code vision_config} is present.
     * @throws IOException if the file cannot be read.
     */
    public static QwenVisionArgs fromHuggingFace(String path) throws IOException {
        File file = new File(path);
        if (!file.exists()) {
            throw new IOException("HuggingFace config file not found: " + path);
        }
        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(file);
        if (!root.has("vision_config") || !root.get("vision_config").isObject()) {
            return null;
        }
        return fromConfig(root);
    }

    /**
     * Parses root multimodal config (with nested {@code vision_config}).
     *
     * @param root HF config root.
     * @return vision args.
     */
    public static QwenVisionArgs fromConfig(JsonNode root) {
        JsonNode v = root.get("vision_config");
        int[] deepstack = intArray(v, "deepstack_visual_indexes");
        JsonNode text = root.has("text_config") ? root.get("text_config") : root;
        boolean interleaved = false;
        int[] section = defaultMropeSection(text);
        if (text.has("rope_parameters") && text.get("rope_parameters").isObject()) {
            JsonNode rope = text.get("rope_parameters");
            if (rope.has("mrope_interleaved")) {
                interleaved = rope.get("mrope_interleaved").asBoolean();
            }
            if (rope.has("mrope_section") && rope.get("mrope_section").isArray()) {
                section = intArray(rope, "mrope_section");
            }
        }
        if (text.has("mrope_interleaved")) {
            interleaved = text.get("mrope_interleaved").asBoolean();
        }
        if (text.has("mrope_section") && text.get("mrope_section").isArray()) {
            section = intArray(text, "mrope_section");
        }
        int outHidden = v.has("out_hidden_size")
                ? v.get("out_hidden_size").asInt()
                : (text.has("hidden_size") ? text.get("hidden_size").asInt() : 5120);
        return new QwenVisionArgs(
                v.get("depth").asInt(),
                v.get("hidden_size").asInt(),
                v.has("intermediate_size") ? v.get("intermediate_size").asInt() : 4304,
                v.has("num_heads") ? v.get("num_heads").asInt() : 16,
                v.has("in_channels") ? v.get("in_channels").asInt() : 3,
                v.has("patch_size") ? v.get("patch_size").asInt() : 16,
                v.has("temporal_patch_size") ? v.get("temporal_patch_size").asInt() : 2,
                v.has("spatial_merge_size") ? v.get("spatial_merge_size").asInt() : 2,
                outHidden,
                v.has("num_position_embeddings") ? v.get("num_position_embeddings").asInt() : 2304,
                deepstack,
                root.has("image_token_id") ? root.get("image_token_id").asInt() : 248056,
                root.has("video_token_id") ? root.get("video_token_id").asInt() : 248057,
                root.has("vision_start_token_id") ? root.get("vision_start_token_id").asInt() : 248053,
                root.has("vision_end_token_id") ? root.get("vision_end_token_id").asInt() : 248054,
                interleaved,
                section
        );
    }

    private static int[] defaultMropeSection(JsonNode text) {
        // half rotary freqs = rotaryDim/2; 27B: head_dim=256 * 0.25 = 64 → 32 = 11+11+10
        double partial = 0.25;
        int headDim = text.has("head_dim") ? text.get("head_dim").asInt() : 256;
        if (text.has("partial_rotary_factor")) {
            partial = text.get("partial_rotary_factor").asDouble();
        }
        if (text.has("rope_parameters") && text.get("rope_parameters").has("partial_rotary_factor")) {
            partial = text.get("rope_parameters").get("partial_rotary_factor").asDouble();
        }
        int rotaryDim = (int) Math.round(headDim * partial);
        if ((rotaryDim & 1) != 0) {
            rotaryDim -= 1;
        }
        int half = Math.max(2, rotaryDim) / 2;
        int a = half / 3;
        int b = half / 3;
        int c = half - a - b;
        return new int[]{a, b, c};
    }

    private static int[] intArray(JsonNode parent, String field) {
        if (!parent.has(field) || !parent.get(field).isArray()) {
            return new int[0];
        }
        List<Integer> list = new ArrayList<>();
        for (JsonNode n : parent.get(field)) {
            list.add(n.asInt());
        }
        return list.stream().mapToInt(Integer::intValue).toArray();
    }
}
