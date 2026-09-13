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

import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import tools.jackson.databind.ObjectMapper;

/**
 * Unit tests for MTP-related {@link QwenModelArgs} fields.
 *
 * @author Haifeng Li
 */
public class QwenModelArgsMtpTest {

    @Test
    public void testGivenNoMtpWhenResolveThenZero() {
        var args = new QwenModelArgs();
        assertFalse(args.hasMtp());
        assertEquals(0, args.resolveNumSpeculativeTokens(3));
        assertEquals(0, args.resolveNumSpeculativeTokens(0));
    }

    @Test
    public void testGivenMtpWhenResolveThenCapsAndDefaults() {
        var args = new QwenModelArgs(
                64, 4, 4, 2, 16, 100, 128, 1e-6, 10000.0, 0.25,
                4, 16, 16, 2, 4, QwenModelArgs.defaultLayerTypes(4, 4), 1, 32,
                1, 3);
        assertTrue(args.hasMtp());
        assertEquals(3, args.resolveNumSpeculativeTokens(0));
        assertEquals(2, args.resolveNumSpeculativeTokens(2));
        assertEquals(QwenModelArgs.MAX_SPECULATIVE_TOKENS,
                args.resolveNumSpeculativeTokens(99));
    }

    @Test
    public void testGivenTextConfigWithMtpWhenParsedThenLoadsDefaults() throws Exception {
        String json = """
                {
                  "hidden_size": 64,
                  "num_hidden_layers": 4,
                  "num_attention_heads": 4,
                  "num_key_value_heads": 2,
                  "head_dim": 16,
                  "vocab_size": 100,
                  "intermediate_size": 128,
                  "rms_norm_eps": 1e-6,
                  "max_position_embeddings": 32,
                  "layer_types": [
                    "linear_attention", "full_attention",
                    "linear_attention", "full_attention"
                  ],
                  "mtp_num_hidden_layers": 1,
                  "speculative_config": { "num_speculative_tokens": 2 }
                }
                """;
        var text = new ObjectMapper().readTree(json);
        var args = QwenModelArgs.fromTextConfig(text, 1, 32);
        assertEquals(1, args.mtpNumHiddenLayers());
        assertEquals(2, args.defaultNumSpeculativeTokens());
        assertTrue(args.hasMtp());
    }
}
