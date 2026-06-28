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
package smile.llm.transformer;

import java.io.IOException;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the {@code smile.llm.transformer.ModelArgs} class.
 *
 * @author Haifeng Li
 */
public class ModelArgsTest {

    // -----------------------------------------------------------------------
    // ModelArgs — default constructor
    // -----------------------------------------------------------------------

    @Test
    public void testGivenDefaultModelArgsWhenCreatedThenValuesAreStandardDefaults() {
        ModelArgs args = new ModelArgs();
        assertEquals(4096, args.dim());
        assertEquals(32, args.numLayers());
        assertEquals(32, args.numHeads());
        assertNull(args.numKvHeads());
        assertEquals(-1, args.vocabSize());
        assertEquals(256, args.multipleOf(), "multipleOf should default to 256, not 356");
        assertNull(args.ffnDimMultiplier());
        assertEquals(1e-5, args.normEps(), 1e-10);
        assertEquals(500000.0, args.ropeTheta(), 1.0);
        assertFalse(args.scaledRope());
        assertEquals(32, args.maxBatchSize());
        assertEquals(2048, args.maxSeqLen());
    }

    // -----------------------------------------------------------------------
    // ModelArgs — from JSON (full params)
    // -----------------------------------------------------------------------

    @Test
    public void testGivenFullParamsJsonWhenLoadedThenAllFieldsPopulated() throws IOException {
        ModelArgs args = ModelArgs.from("deep/src/test/resources/llama/params.json", 4, 512);
        assertEquals(512, args.dim());
        assertEquals(2, args.numLayers());
        assertEquals(8, args.numHeads());
        assertEquals(4, args.numKvHeads());
        assertEquals(1000, args.vocabSize());
        assertEquals(256, args.multipleOf());
        assertNotNull(args.ffnDimMultiplier());
        assertEquals(1.3, args.ffnDimMultiplier(), 1e-9);
        assertEquals(1e-5, args.normEps(), 1e-12);
        assertEquals(500000.0, args.ropeTheta(), 1.0);
        assertFalse(args.scaledRope());
        assertEquals(4, args.maxBatchSize());
        assertEquals(512, args.maxSeqLen());
    }

    @Test
    public void testGivenMinimalParamsJsonWhenLoadedThenOptionalFieldsAreNull() throws IOException {
        // params_minimal.json omits n_kv_heads, ffn_dim_multiplier, rope_theta, use_scaled_rope
        ModelArgs args = ModelArgs.from("deep/src/test/resources/llama/params_minimal.json", 2, 128);
        assertEquals(512, args.dim());
        assertNull(args.numKvHeads(), "n_kv_heads absent → should be null");
        assertNull(args.ffnDimMultiplier(), "ffn_dim_multiplier absent → should be null");
        assertEquals(10000.0, args.ropeTheta(), 1.0, "rope_theta absent → default 10000.0");
        assertFalse(args.scaledRope(), "use_scaled_rope absent → default false");
    }

    @Test
    public void testGivenNonexistentParamsFileWhenLoadedThenThrowsIOException() {
        assertThrows(IOException.class,
                () -> ModelArgs.from("nonexistent/params.json", 1, 128));
    }

    // -----------------------------------------------------------------------
    // ModelArgs — numKvHeads fallback in Attention
    // -----------------------------------------------------------------------

    @Test
    public void testGivenNullNumKvHeadsWhenAttentionCreatedThenFallsBackToNumHeads() {
        // numKvHeads=null → Attention should fall back to numHeads (no GQA)
        ModelArgs args = new ModelArgs(64, 1, 4, null, 100, 256, null, 1e-5, 10000.0, false, 1, 64);
        GroupedQueryAttention attn = new GroupedQueryAttention(args);
        assertEquals(args.numHeads(), attn.numKvHeads,
                "numKvHeads should equal numHeads when numKvHeads param is null");
        assertEquals(1, attn.numRep, "numRep should be 1 when numKvHeads == numHeads");
    }

    @Test
    public void testGivenGqaConfigWhenAttentionCreatedThenNumRepIsCorrect() {
        // 4 query heads, 2 KV heads → numRep = 2
        ModelArgs args = new ModelArgs(64, 1, 4, 2, 100, 256, null, 1e-5, 10000.0, false, 1, 64);
        GroupedQueryAttention attn = new GroupedQueryAttention(args);
        assertEquals(2, attn.numKvHeads);
        assertEquals(2, attn.numRep);
    }
}
