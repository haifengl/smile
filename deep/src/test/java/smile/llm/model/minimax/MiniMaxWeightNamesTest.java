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

import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link MiniMaxWeightNames}.
 *
 * @author Haifeng Li
 */
public class MiniMaxWeightNamesTest {
    private static final String L = "language_model.model.layers.";

    @Test
    public void testGivenEmbeddingsAndHeadWhenRemappedThenMatchSmileNames() {
        assertEquals("embed_tokens.weight", MiniMaxWeightNames.remap("language_model.model.embed_tokens.weight"));
        assertEquals("norm.weight", MiniMaxWeightNames.remap("language_model.model.norm.weight"));
        assertEquals("lm_head.weight", MiniMaxWeightNames.remap("language_model.lm_head.weight"));
        assertEquals("embed_tokens.weight", MiniMaxWeightNames.remap("model.embed_tokens.weight"));
    }

    @Test
    public void testGivenAttentionWhenRemappedThenIncludesIndexAndQuantScales() {
        assertEquals("layers.3.self_attn.q_proj.weight", MiniMaxWeightNames.remap(L + "3.self_attn.q_proj.weight"));
        assertEquals("layers.3.self_attn.o_proj.weight_scale_inv",
                MiniMaxWeightNames.remap(L + "3.self_attn.o_proj.weight_scale_inv"));
        assertEquals("layers.3.self_attn.index_q_proj.weight",
                MiniMaxWeightNames.remap(L + "3.self_attn.index_q_proj.weight"));
        assertEquals("layers.3.self_attn.index_k_norm.weight",
                MiniMaxWeightNames.remap(L + "3.self_attn.index_k_norm.weight"));
        assertEquals("layers.0.input_layernorm.weight", MiniMaxWeightNames.remap(L + "0.input_layernorm.weight"));
        assertEquals("layers.0.post_attention_layernorm.weight",
                MiniMaxWeightNames.remap(L + "0.post_attention_layernorm.weight"));
    }

    @Test
    public void testGivenDenseMlpWhenRemappedThenUsesFeedForwardNames() {
        assertEquals("layers.0.mlp.w1.weight", MiniMaxWeightNames.remap(L + "0.mlp.gate_proj.weight"));
        assertEquals("layers.0.mlp.w2.weight", MiniMaxWeightNames.remap(L + "0.mlp.down_proj.weight"));
        assertEquals("layers.0.mlp.w3.weight_scale_inv", MiniMaxWeightNames.remap(L + "0.mlp.up_proj.weight_scale_inv"));
    }

    @Test
    public void testGivenMoeWhenRemappedThenRoutesExpertsRouterAndSharedExpert() {
        assertEquals("layers.3.moe.gate.weight", MiniMaxWeightNames.remap(L + "3.block_sparse_moe.gate.weight"));
        assertEquals("layers.3.moe.e_score_correction_bias",
                MiniMaxWeightNames.remap(L + "3.block_sparse_moe.e_score_correction_bias"));
        assertEquals("layers.3.moe.experts.127.w2.weight",
                MiniMaxWeightNames.remap(L + "3.block_sparse_moe.experts.127.w2.weight"));
        assertEquals("layers.59.moe.experts.0.w1.weight_scale_inv",
                MiniMaxWeightNames.remap(L + "59.block_sparse_moe.experts.0.w1.weight_scale_inv"));
        assertEquals("layers.3.moe.shared_experts.w1.weight",
                MiniMaxWeightNames.remap(L + "3.block_sparse_moe.shared_experts.gate_proj.weight"));
        assertEquals("layers.3.moe.shared_experts.w2.weight_scale_inv",
                MiniMaxWeightNames.remap(L + "3.block_sparse_moe.shared_experts.down_proj.weight_scale_inv"));
    }

    @Test
    public void testGivenVisionMtpOrUnknownWhenRemappedThenSkipped() {
        assertNull(MiniMaxWeightNames.remap("vision_tower.encoder.layers.0.fc1.weight"));
        assertNull(MiniMaxWeightNames.remap("multi_modal_projector.linear_1.weight"));
        assertNull(MiniMaxWeightNames.remap("patch_merge_mlp.fc1.weight"));
        assertNull(MiniMaxWeightNames.remap("language_model.model.mtp.layers.0.eh_proj.weight"));
        assertNull(MiniMaxWeightNames.remap(L + "3.block_sparse_moe.bogus"));
        assertNull(MiniMaxWeightNames.remap("something.else"));
    }
}
