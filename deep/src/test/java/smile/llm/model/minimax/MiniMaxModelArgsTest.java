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

import java.io.IOException;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link MiniMaxModelArgs}.
 *
 * @author Haifeng Li
 */
public class MiniMaxModelArgsTest {

    @Test
    public void testGivenM3ConfigWhenLoadedThenFieldsMatch() throws IOException {
        MiniMaxModelArgs a = MiniMaxModelArgs.fromHuggingFace(
                "deep/src/test/resources/minimax/config_m3.json", 4, 0);
        assertEquals(6144, a.dim());
        assertEquals(60, a.numLayers());
        assertEquals(64, a.numHeads());
        assertEquals(4, a.numKvHeads());
        assertEquals(128, a.headDim());
        assertEquals(200064, a.vocabSize());
        assertEquals(64, a.rotaryDim());
        assertEquals(12288, a.denseIntermediateSize());
        assertEquals(3072, a.expertIntermediateSize());
        assertEquals(3072, a.sharedIntermediateSize());
        assertEquals(128, a.numExperts());
        assertEquals(4, a.numExpertsPerTok());
        assertEquals(1, a.numSharedExperts());
        assertEquals(2.0, a.routedScalingFactor(), 1e-12);
        assertEquals(1.702, a.swigluAlpha(), 1e-12);
        assertEquals(7.0, a.swigluLimit(), 1e-12);
        assertEquals(5_000_000.0, a.ropeTheta(), 1e-6);
        assertEquals(7, a.numMtpModules());
        assertEquals(1_048_576, a.maxSeqLen());
        assertEquals(4, a.maxBatchSize());
    }

    @Test
    public void testGivenM3ConfigWhenQueriedThenFirstThreeLayersAreDense() throws IOException {
        MiniMaxModelArgs a = MiniMaxModelArgs.fromHuggingFace(
                "deep/src/test/resources/minimax/config_m3.json", 1, 4096);
        for (int i = 0; i < 3; i++) {
            assertFalse(a.isMoe(i));
            assertFalse(a.isSparse(i));
        }
        assertTrue(a.isMoe(3));
        assertTrue(a.isSparse(59));
        assertEquals(57, a.numMoeLayers());
        assertEquals(57, a.numSparseLayers());
        assertEquals(4, a.numIndexHeads());
        assertEquals(128, a.indexDim());
        assertEquals(16, a.topkBlocks());
        assertEquals(128, a.blockSize());
        assertEquals(0, a.initBlocks());
        assertEquals(1, a.localBlocks());
        assertEquals(4096, a.maxSeqLen());
        assertEquals(60, a.kvCacheLayout().numLayers());
        assertEquals(4, a.kvCacheLayout().numKvHeads());
        assertThrows(IllegalArgumentException.class, () -> a.isMoe(60));
    }

    @Test
    public void testGivenTinyConfigWhenLoadedThenFieldsMatch() throws IOException {
        MiniMaxModelArgs a = MiniMaxModelArgs.fromHuggingFace(
                "deep/src/test/resources/minimax/config_tiny.json", 2, 0);
        assertEquals(64, a.dim());
        assertEquals(4, a.numLayers());
        assertEquals(8, a.rotaryDim());
        assertEquals(3, a.numMoeLayers());
        assertEquals(8, a.numExperts());
        assertEquals(2, a.numIndexHeads());
        assertEquals(4, a.blockSize());
        assertFalse(a.hasMtp());
        assertEquals(256, a.maxSeqLen());
    }
}
