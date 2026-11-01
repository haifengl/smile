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
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the DSpark draft hyperparameters (the {@code dspark/config.json} shipped with MiniMax-M3.1-Flash).
 *
 * @author Haifeng Li
 */
public class MiniMaxDsparkArgsTest {
    @Test
    public void testGivenShippedConfigWhenParsedThenDspark() throws IOException {
        MiniMaxDsparkArgs args = MiniMaxDsparkArgs.fromHuggingFace("deep/src/test/resources/minimax/dspark_config.json");
        assertEquals(6144, args.dim());
        assertEquals(5, args.numLayers());
        assertEquals(64, args.numHeads());
        assertEquals(4, args.numKvHeads());
        assertEquals(128, args.headDim());
        assertEquals(64, args.rotaryDim());
        assertEquals(12288, args.ffnSize());
        assertEquals(200064, args.vocabSize());
        assertEquals(7, args.blockSize());
        assertEquals(256, args.markovRank());
        assertEquals(200058, args.noiseTokenId());
        assertArrayEquals(new int[]{3, 17, 31, 45, 59}, args.targetLayerIds());
        assertEquals(4096, args.slidingWindow());
        assertTrue(args.confidenceHead());
        assertTrue(args.confidenceMarkov());
        assertEquals(5 * 6144, args.contextWidth());
    }

    @Test
    public void testGivenTargetConfigWhenParsedThenRejected() {
        assertThrows(IOException.class, () -> MiniMaxDsparkArgs.fromHuggingFace(
                "deep/src/test/resources/minimax/config_tiny.json"));
    }
}
