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
package smile.llm.qwen;

import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for HuggingFace weight name remapping.
 *
 * @author Haifeng Li
 */
public class QwenRemapTest {

    @Test
    public void testGivenEmbeddingsAndNormWhenRemappedThenMatchSmileNames() {
        assertEquals("embed_tokens.weight", Qwen.remapHuggingFaceName("model.embed_tokens.weight"));
        assertEquals("norm.weight", Qwen.remapHuggingFaceName("model.norm.weight"));
        assertEquals("lm_head.weight", Qwen.remapHuggingFaceName("lm_head.weight"));
        assertEquals("embed_tokens.weight",
                Qwen.remapHuggingFaceName("language_model.model.embed_tokens.weight"));
    }

    @Test
    public void testGivenSelfAttnWhenRemappedThenKeepsSelfAttnPrefix() {
        assertEquals("layers.3.self_attn.q_proj.weight",
                Qwen.remapHuggingFaceName("model.layers.3.self_attn.q_proj.weight"));
        assertEquals("layers.3.self_attn.q_norm.weight",
                Qwen.remapHuggingFaceName("model.layers.3.self_attn.q_norm.weight"));
    }

    @Test
    public void testGivenLinearAttnWhenRemappedThenKeepsLinearAttnPrefix() {
        assertEquals("layers.0.linear_attn.in_proj_qkv.weight",
                Qwen.remapHuggingFaceName("model.layers.0.linear_attn.in_proj_qkv.weight"));
        assertEquals("layers.0.linear_attn.A_log",
                Qwen.remapHuggingFaceName("model.layers.0.linear_attn.A_log"));
        assertEquals("layers.0.linear_attn.conv1d.weight",
                Qwen.remapHuggingFaceName("model.layers.0.linear_attn.conv1d.weight"));
    }

    @Test
    public void testGivenMlpWhenRemappedThenMapsToFeedForward() {
        assertEquals("layers.1.mlp.w1.weight",
                Qwen.remapHuggingFaceName("model.layers.1.mlp.gate_proj.weight"));
        assertEquals("layers.1.mlp.w2.weight",
                Qwen.remapHuggingFaceName("model.layers.1.mlp.down_proj.weight"));
        assertEquals("layers.1.mlp.w3.weight",
                Qwen.remapHuggingFaceName("model.layers.1.mlp.up_proj.weight"));
    }

    @Test
    public void testGivenVisionOrMtpWhenRemappedThenNull() {
        assertNull(Qwen.remapHuggingFaceName("visual.blocks.0.attn.qkv.weight"));
        assertNull(Qwen.remapHuggingFaceName("mtp.layers.0.weight"));
    }
}
