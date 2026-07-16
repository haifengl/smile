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
package smile.hash;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link SimHash}.
 *
 * @author Haifeng Li
 */
public class SimHashTest {

    public SimHashTest() {
    }

    @BeforeAll
    public static void setUpClass() throws Exception {
    }

    @AfterAll
    public static void tearDownClass() throws Exception {
    }

    @BeforeEach
    public void setUp() {
    }

    @AfterEach
    public void tearDown() {
    }

    @Test
    public void testTextDeterministic() {
        System.out.println("SimHash text() deterministic");
        SimHash<String[]> sh = SimHash.text();
        String[] tokens = {"the", "quick", "brown", "fox"};
        long h1 = sh.hash(tokens);
        long h2 = sh.hash(tokens);
        assertEquals(h1, h2);
    }

    @Test
    public void testTextEmptyInput() {
        System.out.println("SimHash text() empty token list");
        SimHash<String[]> sh = SimHash.text();
        // All bit-counters are 0 -> bits where count >= 0 are set
        long h = sh.hash(new String[0]);
        // With no tokens every count is 0 which is >= 0, so all 64 bits are set
        assertEquals(-1L, h); // -1L == 0xFFFFFFFFFFFFFFFFL
    }

    @Test
    public void testTextSimilarDocumentsAreClose() {
        System.out.println("SimHash text() similar documents have low Hamming distance");
        SimHash<String[]> sh = SimHash.text();
        String[] doc1 = {"the", "quick", "brown", "fox", "jumps"};
        String[] doc2 = {"the", "quick", "brown", "fox", "leaps"}; // one word changed
        long h1 = sh.hash(doc1);
        long h2 = sh.hash(doc2);
        int hamming = Long.bitCount(h1 ^ h2);
        // Near-duplicate documents should have low Hamming distance (< 20)
        assertTrue(hamming < 20,
            "Hamming distance " + hamming + " unexpectedly large for near-duplicate docs");
    }

    @Test
    public void testTextDifferentDocumentsAreFar() {
        System.out.println("SimHash text() different documents have high Hamming distance");
        SimHash<String[]> sh = SimHash.text();
        String[] doc1 = {"cat", "sat", "mat"};
        String[] doc2 = {"quantum", "physics", "relativity", "entropy"};
        long h1 = sh.hash(doc1);
        long h2 = sh.hash(doc2);
        int hamming = Long.bitCount(h1 ^ h2);
        // Completely different documents should differ in many bits
        assertTrue(hamming > 5,
            "Hamming distance " + hamming + " unexpectedly small for different docs");
    }

    @Test
    public void testOfDeterministic() {
        System.out.println("SimHash.of() deterministic");
        byte[][] features = {
            "feature1".getBytes(StandardCharsets.UTF_8),
            "feature2".getBytes(StandardCharsets.UTF_8),
            "feature3".getBytes(StandardCharsets.UTF_8)
        };
        SimHash<int[]> sh = SimHash.of(features);
        int[] weights = {3, 2, 1};
        long h1 = sh.hash(weights);
        long h2 = sh.hash(weights);
        assertEquals(h1, h2);
    }

    @Test
    public void testOfWrongWeightLengthThrows() {
        System.out.println("SimHash.of() wrong weight length");
        byte[][] features = {
            "a".getBytes(StandardCharsets.UTF_8),
            "b".getBytes(StandardCharsets.UTF_8)
        };
        SimHash<int[]> sh = SimHash.of(features);
        assertThrows(IllegalArgumentException.class,
            () -> sh.hash(new int[]{1, 2, 3})); // 3 != 2
    }

    @Test
    public void testOfAllBitsSetForZeroWeights() {
        System.out.println("SimHash.of() zero weights -> all bits set");
        byte[][] features = {"x".getBytes(StandardCharsets.UTF_8)};
        SimHash<int[]> sh = SimHash.of(features);
        // weight 0: count stays at 0 for bits where hash bit is 1, or
        // -0 = 0 where hash bit is 0 -> all counts >= 0 -> all bits set
        long h = sh.hash(new int[]{0});
        assertEquals(-1L, h);
    }

    @Test
    public void testOfDifferentWeightsDifferentHash() {
        System.out.println("SimHash.of() different weights change hash");
        byte[][] features = {
            "a".getBytes(StandardCharsets.UTF_8),
            "b".getBytes(StandardCharsets.UTF_8)
        };
        SimHash<int[]> sh = SimHash.of(features);
        long h1 = sh.hash(new int[]{1, 0});
        long h2 = sh.hash(new int[]{0, 1});
        assertNotEquals(h1, h2);
    }
}

