/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * Smile is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Smile is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Smile.  If not, see <https://www.gnu.org/licenses/>.
 */
package smile.llm.model.glm;

import java.io.File;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests of {@link GlmModelArgs} on the released GLM-5.3-Flash {@code config.json} (quantization section removed).
 */
public class GlmModelArgsTest {
    private static GlmModelArgs load() throws Exception {
        return GlmModelArgs.load(new File(System.getProperty("glm.config", "deep/src/test/resources/glm/config.json")));
    }

    @Test
    public void testGivenReleasedConfigThenFieldsParsed() throws Exception {
        var a = load();
        assertEquals(4096, a.dim());
        assertEquals(45, a.numLayers());
        assertEquals(154880, a.vocabSize());
        assertEquals(1e-5, a.normEps());
        assertEquals(64, a.numHeads());
        assertEquals(256, a.qkHeadDim());
        assertEquals(0, a.ropeHeadDim());
        assertEquals(256, a.vHeadDim());
        assertEquals(1536, a.qLoraRank());
        assertEquals(512, a.kvLoraRank());
        assertEquals(64, a.kdaHeads());
        assertEquals(128, a.kdaHeadDim());
        assertEquals(8192, a.kdaWidth());
        assertEquals(4, a.kdaConvKernel());
        assertEquals(-5.0, a.kdaGateLowerBound());
        assertEquals(32, a.indexHeads());
        assertEquals(128, a.indexHeadDim());
        assertEquals(2048, a.indexTopk());
        assertEquals(4, a.indexKpool());
        assertEquals(512, a.indexPools());
        assertTrue(a.indexAlwaysSelectTail());
        assertEquals(288, a.numRoutedExperts());
        assertEquals(1, a.numSharedExperts());
        assertEquals(8, a.numExpertsPerTok());
        assertEquals(2048, a.moeInterSize());
        assertEquals(12288, a.denseInterSize());
        assertEquals(2.5, a.routeScale());
        assertEquals(10.0, a.swigluLimit());
        assertEquals(4, a.hcMult());
        assertEquals(20, a.hcSinkhornIters());
        assertEquals(1e-6, a.hcEps());
        assertEquals(1, a.numMtpLayers());
        assertEquals(154854, a.imageTokenId());
        assertArrayEquals(new int[]{154820, 154827, 154829}, a.eosTokenIds());
        assertEquals(154820, a.padTokenId());
        assertEquals(1048576, a.maxSeqLen());
    }

    @Test
    public void testGivenLayerTypesThenSparseEveryFourthFromThree() throws Exception {
        var a = load();
        int sparse = 0;
        for (int i = 0; i < a.numLayers(); i++) {
            assertEquals(i % 4 == 3, a.isSparseAttention(i), "attention layer " + i);
            assertEquals(i >= 3, a.isMoe(i), "mlp layer " + i);
            if (a.isSparseAttention(i)) {
                sparse++;
            }
        }
        assertEquals(11, sparse);
        assertEquals(34, a.kdaLayerCount());
    }

    @Test
    public void testGivenSliceThenFirstLayersKept() throws Exception {
        var s = load().slice(8);
        assertEquals(8, s.numLayers());
        assertEquals(6, s.kdaLayerCount());
        assertTrue(s.isSparseAttention(3) && s.isSparseAttention(7));
        assertTrue(s.isMoe(7) && !s.isMoe(2));
        assertEquals(1, s.numMtpLayers());
        assertEquals("model.language_model.layers.5", s.layerPrefix(5));
        assertThrows(IllegalArgumentException.class, () -> load().slice(46));
        assertEquals(4096, load().withMaxSeqLen(4096).maxSeqLen());
    }
}
