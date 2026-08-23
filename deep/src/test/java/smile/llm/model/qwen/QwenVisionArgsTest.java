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

import java.io.IOException;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link QwenVisionArgs} and {@link InterleavedMRope}.
 *
 * @author Haifeng Li
 */
public class QwenVisionArgsTest {

    @Test
    public void testGivenVlConfigWhenLoadedThenVisionFieldsMatch() throws IOException {
        QwenVisionArgs v = QwenVisionArgs.fromHuggingFace(
                "deep/src/test/resources/qwen/config_27b_vl.json");
        assertNotNull(v);
        assertEquals(27, v.depth());
        assertEquals(1152, v.hiddenSize());
        assertEquals(5120, v.outHiddenSize());
        assertEquals(16, v.patchSize());
        assertEquals(2, v.spatialMergeSize());
        assertEquals(1536, v.patchDim());
        assertFalse(v.hasDeepStack());
        assertEquals(248056, v.imageTokenId());
        assertTrue(v.mropeInterleaved());
        assertArrayEquals(new int[]{11, 11, 10}, v.mropeSection());
        assertEquals(256, v.mergedTokens(1, 32, 32));
    }

    @Test
    public void testGivenTextOnlyConfigWhenLoadedThenNull() throws IOException {
        assertNull(QwenVisionArgs.fromHuggingFace(
                "deep/src/test/resources/qwen/config_tiny.json"));
    }

    @Test
    public void testGivenEqualPlanesWhenMropeThenMatches1dGather() {
        int[] pos = new int[]{0, 1, 2, 3, 4};
        try (var cs = InterleavedMRope.computeCosSin(
                64, 1e7, new int[]{11, 11, 10}, pos, pos, pos);
             var table = PartialRotaryEncoding.computeCosSin(64, 8, 1e7)) {
            float[] a = cs.cos().floatArray();
            float[] b = table.cos().get(smile.deep.tensor.Index.of(pos)).floatArray();
            assertEquals(a.length, b.length);
            for (int i = 0; i < a.length; i++) {
                assertEquals(b[i], a[i], 1e-5f, "mismatch at " + i);
            }
        }
    }

    @Test
    public void testGivenImageRunWhenRopeIndexThenCompressedAdvance() {
        // text(2) + image(4 merged) + text(1); grid 1x4x4 merge=2 → 4 tokens, advance max(4,4)/2=2
        int[] types = new int[]{0, 0, 1, 1, 1, 1, 0};
        int[][] img = new int[][]{{1, 4, 4}};
        var m = InterleavedMRope.getRopeIndex(types, img, new int[0][], 2);
        assertEquals(7, m.t().length);
        assertEquals(0, m.t()[0]);
        assertEquals(1, m.t()[1]);
        // after text current=2; vision advances by 2 → text resumes at 4
        assertEquals(4, m.t()[6]);
        assertEquals(m.h()[6], m.t()[6]);
        int max = 0;
        for (int p : m.t()) max = Math.max(max, p);
        for (int p : m.h()) max = Math.max(max, p);
        for (int p : m.w()) max = Math.max(max, p);
        assertEquals(max + 1 - 7, m.ropeDelta());
    }
}
