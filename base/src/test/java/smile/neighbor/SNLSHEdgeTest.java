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
package smile.neighbor;

import java.util.ArrayList;
import smile.hash.SimHash;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Edge-case and additional unit tests for {@link SNLSH}.
 *
 * @author Haifeng Li
 */
public class SNLSHEdgeTest {

    private static final String[] TEXTS = {
            "The quick brown fox jumps over the lazy dog",
            "The quick brown fox leaps over the lazy dog",
            "A fast brown fox jumps over the lazy dog",
            "Completely unrelated sentence about machine learning"
    };

    private SNLSH<String[], String> buildLsh(String[] sentences) {
        SNLSH<String[], String> lsh = new SNLSH<>(8, SimHash.text());
        for (String s : sentences) {
            lsh.put(tokenize(s), s);
        }
        return lsh;
    }

    private String[] tokenize(String s) {
        return s.toLowerCase().split("\\s+");
    }

    @Test
    public void testSize() {
        SNLSH<String[], String> lsh = buildLsh(TEXTS);
        assertEquals(TEXTS.length, lsh.size());
    }

    @Test
    public void testSizeEmpty() {
        SNLSH<String[], String> lsh = new SNLSH<>(8, SimHash.text());
        assertEquals(0, lsh.size());
    }

    @Test
    public void testInvalidBandSize() {
        // L must be in [2, 32]
        assertThrows(IllegalArgumentException.class, () -> new SNLSH<>(1, SimHash.text()));
        assertThrows(IllegalArgumentException.class, () -> new SNLSH<>(33, SimHash.text()));
    }

    @Test
    public void testInvalidRadius() {
        SNLSH<String[], String> lsh = buildLsh(TEXTS);
        var results = new ArrayList<Neighbor<String[], String>>();
        String[] q = tokenize(TEXTS[0]);
        // Non-integer radius
        assertThrows(IllegalArgumentException.class, () -> lsh.search(q, 0.5, results));
        // Negative radius
        assertThrows(IllegalArgumentException.class, () -> lsh.search(q, -1.0, results));
        // Zero radius
        assertThrows(IllegalArgumentException.class, () -> lsh.search(q, 0.0, results));
    }

    @Test
    public void testSimilarTextsFound() {
        SNLSH<String[], String> lsh = buildLsh(TEXTS);
        var results = new ArrayList<Neighbor<String[], String>>();
        // Texts 0 and 1 differ by one word ("jumps" vs "leaps"); should be close in Hamming space
        lsh.search(tokenize(TEXTS[0]), 10, results);
        assertFalse(results.isEmpty(), "Expected at least one neighbor for similar text");
    }

    @Test
    public void testDistancesAreIntegers() {
        SNLSH<String[], String> lsh = buildLsh(TEXTS);
        var results = new ArrayList<Neighbor<String[], String>>();
        lsh.search(tokenize(TEXTS[0]), 20, results);
        for (var n : results) {
            double d = n.distance();
            assertEquals((long) d, d, 1e-9, "Distance must be an integer (Hamming distance)");
            assertTrue(d >= 0 && d <= 64);
        }
    }

    @Test
    public void testSelfNotExcluded() {
        // SNLSH does NOT exclude the query object by reference (unlike KD-tree).
        // A query identical to a stored item may appear in results if Hamming distance <= radius.
        SNLSH<String[], String> lsh = buildLsh(new String[]{TEXTS[0]});
        var results = new ArrayList<Neighbor<String[], String>>();
        lsh.search(tokenize(TEXTS[0]), 5, results);
        // The single item has Hamming distance 0 to itself; it should be returned
        boolean found = results.stream().anyMatch(n -> n.value().equals(TEXTS[0]));
        assertTrue(found, "The identical item should appear in results at distance 0");
    }

    @Test
    public void testPutIncrementally() {
        SNLSH<String[], String> lsh = new SNLSH<>(8, SimHash.text());
        assertEquals(0, lsh.size());
        lsh.put(tokenize(TEXTS[0]), TEXTS[0]);
        assertEquals(1, lsh.size());
        lsh.put(tokenize(TEXTS[1]), TEXTS[1]);
        assertEquals(2, lsh.size());
    }

    @Test
    public void testBandSizeBoundaries() {
        // L = 2 (minimum) and L = 32 (maximum) should construct without error
        assertDoesNotThrow(() -> new SNLSH<>(2, SimHash.text()));
        assertDoesNotThrow(() -> new SNLSH<>(32, SimHash.text()));
    }

    @Test
    public void testRadiusZeroDisallowed() {
        SNLSH<String[], String> lsh = buildLsh(TEXTS);
        var results = new ArrayList<Neighbor<String[], String>>();
        assertThrows(IllegalArgumentException.class,
                () -> lsh.search(tokenize(TEXTS[0]), 0.0, results));
    }

    @Test
    public void testLargeRadius() {
        // With a very large radius (64 = max Hamming distance on 64-bit hash) all items should appear
        SNLSH<String[], String> lsh = buildLsh(TEXTS);
        var results = new ArrayList<Neighbor<String[], String>>();
        lsh.search(tokenize(TEXTS[0]), 64, results);
        // At least the first text itself should be in results
        assertFalse(results.isEmpty());
    }

    @Test
    public void testMultiplePutsWithSameKey() {
        SNLSH<String[], String> lsh = new SNLSH<>(8, SimHash.text());
        String[] key = tokenize(TEXTS[0]);
        lsh.put(key, TEXTS[0]);
        lsh.put(key, TEXTS[0] + "_dup");
        // Both should be stored (SNLSH doesn't deduplicate)
        assertEquals(2, lsh.size());
    }

    @Test
    public void testResultIndices() {
        SNLSH<String[], String> lsh = buildLsh(TEXTS);
        var results = new ArrayList<Neighbor<String[], String>>();
        lsh.search(tokenize(TEXTS[0]), 20, results);
        for (var n : results) {
            assertTrue(n.index() >= 0 && n.index() < TEXTS.length,
                    "Index out of range: " + n.index());
        }
    }
}
