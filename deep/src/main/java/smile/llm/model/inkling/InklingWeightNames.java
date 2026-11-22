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

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Maps Inkling-2-Mini tensor names onto SMILE module paths and lists the tensors a checkpoint must provide.
 *
 * <p>The text tower lives under {@code model.llm.*}; the SMILE path is the same name without that prefix
 * (for example {@code layers.6.attn.wq_du.weight}), so the module tree mirrors the checkpoint. The vision tower
 * ({@code model.visual.*}) is not loaded yet, and the draft head's tensors live in {@code mtp.safetensors}
 * under their own, unprefixed names.
 *
 * <p>Layer structure that the names encode:
 * <ul>
 * <li>Local (sliding-window) layers have {@code attn_norm} and per-channel {@code k_sconv}/{@code v_sconv}
 *     weights; global layers have {@code q_input_norm}/{@code k_input_norm}/{@code v_input_norm} and
 *     input-dependent sconvs ({@code static_w} + {@code weight_proj}).</li>
 * <li>Layers inside the loop have {@code controllers.{attn,k,mlp,v}} (message gates).</li>
 * <li>{@code attn_res.attn_queries.i} belongs to layer {@code i + 1} (layer 0 reads only the embedding) and has
 *     three rows for a global layer (q, k and v inputs); {@code attn_res.mlp_queries.i} belongs to layer
 *     {@code i}.</li>
 * <li>Expert tensors are stacked over experts: {@code w13_weight} holds gate and up interleaved row by row;
 *     NVFP4 layers add {@code *_amax} and {@code *_outer_scale_denominator}.</li>
 * </ul>
 *
 * @author Haifeng Li
 */
final class InklingWeightNames {
    private static final String TEXT = "model.llm.";

    private InklingWeightNames() { }

    /**
     * Maps a checkpoint tensor name to its SMILE module path.
     *
     * @param hfName tensor name in {@code model-*.safetensors}.
     * @return the module path, or {@code null} for tensors that are not loaded (vision tower, unknown names).
     */
    static String remap(String hfName) {
        if (hfName.startsWith(TEXT)) {
            return hfName.substring(TEXT.length());
        }
        return null;
    }

    /**
     * Returns whether a tensor belongs to the vision tower.
     * @param hfName tensor name.
     * @return {@code true} for {@code model.visual.*}.
     */
    static boolean isVision(String hfName) {
        return hfName.startsWith("model.visual.");
    }

    /**
     * Returns every SMILE parameter name the text tower needs. A tensor missing from the checkpoint, or a name
     * the remapper does not produce, would otherwise be left uninitialized by {@code loadStateDict(strict=false)}.
     *
     * @param args model hyperparameters.
     * @return the required parameter names, in layer order.
     */
    static Set<String> expectedNames(InklingModelArgs args) {
        Set<String> names = new LinkedHashSet<>();
        names.add("embed.weight");
        names.add("embed_norm.weight");
        names.add("norm.weight");
        names.add("unembed.weight");
        for (int i = 0; i < args.numLayers() - 1; i++) {
            names.add("attn_res.attn_queries." + i);
        }
        for (int i = 0; i < args.numLayers(); i++) {
            names.add("attn_res.mlp_queries." + i);
        }
        names.add("attn_res.output_query");
        names.add("middle_cycle_cross_pass_queries");
        names.add("middle_cycle_cross_pass_kv_queries");
        for (int i = 0; i < args.numLayers(); i++) {
            layer(args, i, names);
        }
        return names;
    }

    private static void layer(InklingModelArgs args, int i, Set<String> names) {
        String p = "layers." + i + ".";
        String a = p + "attn.";
        for (String s : new String[]{"attn_sink_proj.weight", "gate_attn.weight", "q_l2_clip.scale",
                "rel_l2_clip.scale", "rel_logits_proj.proj", "wk_dv.weight", "wo_ud.weight", "wq_du.weight",
                "wr_du.weight", "wv_dv.weight"}) {
            names.add(a + s);
        }
        if (args.isLocal(i)) {
            names.add(a + "k_sconv.weight");
            names.add(a + "v_sconv.weight");
            names.add(p + "attn_norm.weight");
        } else {
            for (String s : new String[]{"k_sconv", "v_sconv"}) {
                names.add(a + s + ".static_w");
                names.add(a + s + ".weight_proj");
            }
            for (String s : new String[]{"q_input_norm", "k_input_norm", "v_input_norm"}) {
                names.add(p + s + ".weight");
            }
        }
        names.add(p + "attn_sconv.weight");
        names.add(p + "mlp_sconv.weight");
        names.add(p + "mlp_norm.weight");
        names.add(p + "mlp.gate.weight");
        if (args.useGateBias()) {
            names.add(p + "mlp.gate.bias");
        }
        if (args.isLooped(i)) {
            for (String c : new String[]{"attn", "k", "mlp", "v"}) {
                for (String s : new String[]{"down_weight", "forget_weight", "gain_weight"}) {
                    names.add(p + "controllers." + c + "." + s);
                }
            }
        }
        String e = p + "mlp.experts.";
        for (String w : new String[]{"w13", "w2"}) {
            names.add(e + w + "_weight");
            if (args.expertFormat(i) != InklingModelArgs.ExpertFormat.BF16) {
                names.add(e + w + "_weight_scale");
            }
            if (args.expertFormat(i) == InklingModelArgs.ExpertFormat.NVFP4) {
                names.add(e + w + "_weight_amax");
                names.add(e + w + "_weight_outer_scale_denominator");
            }
        }
    }
}
