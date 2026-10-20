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

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Maps MiniMax-M3 HuggingFace tensor names onto SMILE module paths.
 *
 * <p>Checkpoints nest the text tower under {@code language_model.model.*}; text-only
 * exports use {@code model.*}. Quantization sidecar tensors keep their suffix
 * ({@code .weight_scale_inv}) so a quantized loader can pair them with the weight.
 *
 * @author Haifeng Li
 */
final class MiniMaxWeightNames {
    private static final Pattern LAYER = Pattern.compile(
            "^model\\.layers\\.(\\d+)\\.(self_attn|mlp|block_sparse_moe|input_layernorm|post_attention_layernorm)\\.(.+)$");
    private static final Pattern EXPERT = Pattern.compile("^experts\\.(\\d+)\\.w([123])\\.(.+)$");
    private static final Pattern PROJ = Pattern.compile("^(gate|up|down)_proj\\.(.+)$");

    private MiniMaxWeightNames() { }

    /**
     * Returns every SMILE parameter name a BF16 checkpoint must provide. A weight missing from
     * the checkpoint (or a name the remapper does not produce) would otherwise be left
     * uninitialized by {@code loadStateDict(strict=false)}.
     *
     * @param args model hyperparameters.
     * @return the required parameter names.
     */
    static java.util.Set<String> expectedNames(MiniMaxModelArgs args) {
        return expectedNames(args, MiniMaxShard.single());
    }

    /**
     * Returns the SMILE parameter names a rank must load: everything except the routed experts owned
     * by other ranks.
     *
     * @param args  model hyperparameters.
     * @param shard the rank's expert slice.
     * @return the required parameter names.
     */
    static java.util.Set<String> expectedNames(MiniMaxModelArgs args, MiniMaxShard shard) {
        int first = shard.expertStart(args.numExperts());
        int last = first + shard.localExperts(args.numExperts());
        java.util.Set<String> names = new java.util.LinkedHashSet<>();
        names.add("embed_tokens.weight");
        names.add("norm.weight");
        names.add("lm_head.weight");
        for (int i = 0; i < args.numLayers(); i++) {
            String p = "layers." + i + ".";
            names.add(p + "input_layernorm.weight");
            names.add(p + "post_attention_layernorm.weight");
            for (String s : new String[]{"q_proj", "k_proj", "v_proj", "o_proj", "q_norm", "k_norm"}) {
                names.add(p + "self_attn." + s + ".weight");
            }
            if (args.isSparse(i)) {
                for (String s : new String[]{"index_q_proj", "index_k_proj", "index_q_norm", "index_k_norm"}) {
                    names.add(p + "self_attn." + s + ".weight");
                }
            }
            if (args.isMoe(i)) {
                names.add(p + "moe.gate.weight");
                names.add(p + "moe.e_score_correction_bias");
                for (int e = first; e < last; e++) {
                    for (String w : new String[]{"w1", "w2", "w3"}) {
                        names.add(p + "moe.experts." + e + "." + w + ".weight");
                    }
                }
                if (args.numSharedExperts() > 0) {
                    for (String w : new String[]{"w1", "w2", "w3"}) {
                        names.add(p + "moe.shared_experts." + w + ".weight");
                    }
                }
            } else {
                for (String w : new String[]{"w1", "w2", "w3"}) {
                    names.add(p + "mlp." + w + ".weight");
                }
            }
        }
        return names;
    }

    /**
     * Maps a HuggingFace parameter name onto the registered SMILE module path.
     *
     * @param hfName tensor name from the safetensors index.
     * @return module path, or {@code null} for tensors that are not loaded
     *         (vision tower, projector, MTP modules, unknown names).
     */
    static String remap(String hfName) {
        if (hfName.startsWith("vision_tower.") || hfName.startsWith("multi_modal_projector.")
                || hfName.startsWith("patch_merge_mlp.") || hfName.startsWith("model.vision_tower.")) {
            return null;
        }
        String name = hfName;
        if (name.startsWith("language_model.")) {
            name = name.substring("language_model.".length());
        }
        // MTP modules live at model.mtp.layers.{i}.* (Phase 7).
        if (name.startsWith("model.mtp.") || name.startsWith("mtp.")) {
            return null;
        }
        switch (name) {
            case "model.embed_tokens.weight": return "embed_tokens.weight";
            case "model.norm.weight": return "norm.weight";
            case "lm_head.weight":
            case "model.lm_head.weight": return "lm_head.weight";
            default: break;
        }

        Matcher m = LAYER.matcher(name);
        if (!m.matches()) {
            return null;
        }
        String prefix = "layers." + m.group(1) + ".";
        String rest = m.group(3);
        return switch (m.group(2)) {
            case "self_attn" -> prefix + "self_attn." + rest;
            case "input_layernorm", "post_attention_layernorm" -> prefix + m.group(2) + "." + rest;
            case "mlp" -> {
                String ffn = ffnName(rest);
                yield ffn == null ? null : prefix + "mlp." + ffn;
            }
            case "block_sparse_moe" -> {
                String moe = moeName(rest);
                yield moe == null ? null : prefix + "moe." + moe;
            }
            default -> null;
        };
    }

    /** {@code gate/up/down_proj.X} to the FeedForward names {@code w1/w3/w2.X}. */
    private static String ffnName(String rest) {
        Matcher p = PROJ.matcher(rest);
        if (!p.matches()) {
            return null;
        }
        return switch (p.group(1)) {
            case "gate" -> "w1." + p.group(2);
            case "down" -> "w2." + p.group(2);
            default -> "w3." + p.group(2);
        };
    }

    private static String moeName(String rest) {
        if (rest.equals("gate.weight") || rest.equals("e_score_correction_bias")) {
            return rest;
        }
        Matcher e = EXPERT.matcher(rest);
        if (e.matches()) {
            return "experts." + e.group(1) + ".w" + e.group(2) + "." + e.group(3);
        }
        if (rest.startsWith("shared_experts.")) {
            String ffn = ffnName(rest.substring("shared_experts.".length()));
            return ffn == null ? null : "shared_experts." + ffn;
        }
        return null;
    }
}
