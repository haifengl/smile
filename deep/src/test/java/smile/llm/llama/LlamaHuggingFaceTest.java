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
package smile.llm.llama;

import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for HuggingFace weight-name remapping used by {@link Llama}.
 *
 * @author Haifeng Li
 */
public class LlamaHuggingFaceTest {

    @Test
    public void testGivenEmbedTokensWhenRemappedThenTokEmbeddings() {
        assertEquals("tok_embeddings.weight",
                Llama.remapHuggingFaceName("model.embed_tokens.weight"));
    }

    @Test
    public void testGivenLmHeadWhenRemappedThenOutput() {
        assertEquals("output.weight",
                Llama.remapHuggingFaceName("lm_head.weight"));
    }

    @Test
    public void testGivenFinalNormWhenRemappedThenNorm() {
        assertEquals("norm.weight",
                Llama.remapHuggingFaceName("model.norm.weight"));
    }

    @Test
    public void testGivenAttentionProjectionsWhenRemappedThenMetaNames() {
        assertEquals("layers.0.attention.wq.weight",
                Llama.remapHuggingFaceName("model.layers.0.self_attn.q_proj.weight"));
        assertEquals("layers.3.attention.wk.weight",
                Llama.remapHuggingFaceName("model.layers.3.self_attn.k_proj.weight"));
        assertEquals("layers.12.attention.wv.weight",
                Llama.remapHuggingFaceName("model.layers.12.self_attn.v_proj.weight"));
        assertEquals("layers.31.attention.wo.weight",
                Llama.remapHuggingFaceName("model.layers.31.self_attn.o_proj.weight"));
    }

    @Test
    public void testGivenMlpProjectionsWhenRemappedThenFeedForwardNames() {
        assertEquals("layers.1.feed_forward.w1.weight",
                Llama.remapHuggingFaceName("model.layers.1.mlp.gate_proj.weight"));
        assertEquals("layers.1.feed_forward.w2.weight",
                Llama.remapHuggingFaceName("model.layers.1.mlp.down_proj.weight"));
        assertEquals("layers.1.feed_forward.w3.weight",
                Llama.remapHuggingFaceName("model.layers.1.mlp.up_proj.weight"));
    }

    @Test
    public void testGivenLayerNormsWhenRemappedThenMetaNames() {
        assertEquals("layers.5.attention_norm.weight",
                Llama.remapHuggingFaceName("model.layers.5.input_layernorm.weight"));
        assertEquals("layers.5.ffn_norm.weight",
                Llama.remapHuggingFaceName("model.layers.5.post_attention_layernorm.weight"));
    }

    @Test
    public void testGivenUnknownWeightWhenRemappedThenNull() {
        assertNull(Llama.remapHuggingFaceName("model.layers.0.self_attn.rotary_emb.inv_freq"));
        assertNull(Llama.remapHuggingFaceName("some.random.weight"));
    }
}
