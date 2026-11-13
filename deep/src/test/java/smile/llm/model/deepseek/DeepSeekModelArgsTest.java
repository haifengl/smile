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
package smile.llm.model.deepseek;

import java.io.File;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests of {@link DeepSeekModelArgs} on the released DeepSeek-V4.1-Flash-1017 {@code config.json}.
 */
public class DeepSeekModelArgsTest {
    private static DeepSeekModelArgs load() throws Exception {
        return DeepSeekModelArgs.load(new File("deep/src/test/resources/deepseek/config.json"));
    }

    @Test
    public void testGivenReleasedConfigThenFieldsParsed() throws Exception {
        var a = load();
        assertEquals(5120, a.dim());
        assertEquals(40, a.numLayers());
        assertEquals(129280, a.vocabSize());
        assertEquals(64, a.numHeads());
        assertEquals(512, a.headDim());
        assertEquals(64, a.ropeHeadDim());
        assertEquals(1280, a.qLoraRank());
        assertEquals(1e-20, a.normEps());
        assertEquals(384, a.numRoutedExperts());
        assertEquals(6, a.numExpertsPerTok());
        assertEquals(1.5, a.routeScale());
        assertEquals(10.0, a.swigluLimit());
        assertEquals(128, a.windowSize());
        assertEquals(65536, a.originalSeqLen());
        assertEquals(16.0, a.ropeFactor());
        assertEquals(160000.0, a.compressRopeTheta());
        assertEquals(43, a.compressRatios().length);
        assertEquals(4, a.hcMult());
        assertEquals(2, a.engramLayers().length);
        assertEquals(384006168L, a.engramNumEmbeddings()[0]);
        assertEquals(384016682L, a.engramNumEmbeddings()[1]);
        assertEquals(24, a.engramHashColumns());
        assertEquals(99092, a.engramCompressedVocab());
        assertEquals(2, a.engramPadId());
        assertEquals(3, a.numMtpLayers());
        assertEquals(1048576, a.maxSeqLen());
    }

    @Test
    public void testGivenLayersThenAttentionRolesDerived() throws Exception {
        var a = load();
        assertEquals(0, a.compressRatio(0));
        assertEquals(0, a.compressRatio(1));
        assertEquals(2, a.compressRatio(2));
        assertEquals(2, a.compressRatio(19));
        assertEquals(1, a.compressRatio(20));
        assertEquals(1, a.compressRatio(39));
        assertEquals(0, a.compressRatio(40));
        for (int l : new int[]{2, 8, 14, 20}) {
            assertTrue(a.isKvSource(l), "kv " + l);
        }
        assertFalse(a.isKvSource(3));
        assertFalse(a.isKvSource(41), "MTP layers are never sources");
        assertTrue(a.isIndexSource(24));
        assertTrue(a.isIndexSource(36));
        assertFalse(a.isIndexSource(21));
        assertTrue(a.isCandidateSource(20));
        assertFalse(a.usesCandidates(20));
        assertFalse(a.usesCandidates(14));
        assertTrue(a.usesCandidates(24));
        assertEquals(0, a.engramSlot(1) );
        assertEquals(1, a.engramSlot(14));
        assertEquals(-1, a.engramSlot(2));
        assertArrayEquals(new int[]{384, 6}, a.moeConfig(10));
        assertArrayEquals(new int[]{128, 3}, a.moeConfig(41));
    }

    @Test
    public void testGivenSliceThenTruncatedConsistently() throws Exception {
        var s = load().slice(4);
        assertEquals(4, s.numLayers());
        assertArrayEquals(new int[]{0, 0, 2, 2}, java.util.Arrays.copyOf(s.compressRatios(), 4));
        assertArrayEquals(new int[]{2}, s.kvSourceLayers());
        assertArrayEquals(new int[]{2}, s.indexSourceLayers());
        assertArrayEquals(new int[]{1}, s.engramLayers());
        assertArrayEquals(new long[]{384006168L}, s.engramNumEmbeddings());
        assertEquals(-1, s.candidateSourceLayer());
        assertFalse(s.usesCandidates(3));
        assertEquals(0, s.numMtpLayers());
        assertEquals(99092, s.engramCompressedVocab());
        assertThrows(IllegalArgumentException.class, () -> load().slice(0));
        assertThrows(IllegalArgumentException.class, () -> load().slice(41));
    }
}
