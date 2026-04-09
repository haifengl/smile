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
package smile.stat;

import smile.math.MathEx;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the Sampling utility class.
 *
 * @author Haifeng Li
 */
public class SamplingTest {

    @BeforeAll
    public static void setUpClass() {
        MathEx.setSeed(19650218);
    }

    @AfterAll
    public static void tearDownClass() {
    }

    @BeforeEach
    public void setUp() {
    }

    @AfterEach
    public void tearDown() {
    }

    /**
     * Test random sampling with replacement.
     */
    @Test
    public void testRandomWithReplacement() {
        System.out.println("random with replacement");
        int n = 100;
        int[] samples = Sampling.random(n, 1.0);
        assertEquals(n, samples.length);
        for (int s : samples) {
            assertTrue(s >= 0 && s < n, "Sample index out of range: " + s);
        }
    }

    /**
     * Test random sampling without replacement.
     */
    @Test
    public void testRandomWithoutReplacement() {
        System.out.println("random without replacement");
        int n = 100;
        double rate = 0.7;
        int[] samples = Sampling.random(n, rate);
        int expected = (int) Math.round(rate * n);
        assertEquals(expected, samples.length);
        for (int s : samples) {
            assertTrue(s >= 0 && s < n, "Sample index out of range: " + s);
        }
        // No duplicates expected without replacement
        java.util.Set<Integer> unique = new java.util.HashSet<>();
        for (int s : samples) unique.add(s);
        assertEquals(expected, unique.size());
    }

    /**
     * Test strata() returns correct partitioning.
     */
    @Test
    public void testStrata() {
        System.out.println("strata");
        int[] category = {0, 1, 2, 0, 1, 0, 2, 1, 0, 2};
        int[][] strata = Sampling.strata(category);
        assertEquals(3, strata.length);
        assertEquals(4, strata[0].length); // class 0 appears 4 times
        assertEquals(3, strata[1].length); // class 1 appears 3 times
        assertEquals(3, strata[2].length); // class 2 appears 3 times
        // Verify indices belong to correct class
        for (int s : strata[0]) assertEquals(0, category[s]);
        for (int s : strata[1]) assertEquals(1, category[s]);
        for (int s : strata[2]) assertEquals(2, category[s]);
    }

    /**
     * Test stratified sampling with replacement.
     */
    @Test
    public void testStratifyWithReplacement() {
        System.out.println("stratify with replacement");
        int[] category = {0, 0, 0, 1, 1, 2, 2, 2, 2, 2};
        int[] samples = Sampling.stratify(category, 1.0);
        assertEquals(category.length, samples.length);
        for (int s : samples) {
            assertTrue(s >= 0 && s < category.length, "Sample index out of range: " + s);
        }
    }

    /**
     * Test stratified sampling without replacement.
     */
    @Test
    public void testStratifyWithoutReplacement() {
        System.out.println("stratify without replacement");
        int[] category = {0, 0, 0, 0, 1, 1, 1, 2, 2, 2};
        double rate = 0.5;
        int[] samples = Sampling.stratify(category, rate);
        // Should have approximately 50% from each stratum
        // class 0: 4 * 0.5 = 2, class 1: 3 * 0.5 = 1 or 2, class 2: 3 * 0.5 = 1 or 2
        int expected = (int) Math.round(4 * rate) + (int) Math.round(3 * rate) + (int) Math.round(3 * rate);
        assertEquals(expected, samples.length);
        for (int s : samples) {
            assertTrue(s >= 0 && s < category.length, "Sample index out of range: " + s);
        }
    }

    /**
     * Test Latin Hypercube Sampling dimensions and range.
     */
    @Test
    public void testLatin() {
        System.out.println("latin hypercube");
        int n = 10;
        int d = 4;
        int[][] lhs = Sampling.latin(n, d);
        assertEquals(n, lhs.length);
        assertEquals(d, lhs[0].length);

        // Each column should be a permutation of 0..n-1
        for (int j = 0; j < d; j++) {
            boolean[] seen = new boolean[n];
            for (int i = 0; i < n; i++) {
                int v = lhs[i][j];
                assertTrue(v >= 0 && v < n, "LHS value out of range: " + v);
                assertFalse(seen[v], "Duplicate LHS value: " + v);
                seen[v] = true;
            }
        }
    }

    /**
     * Test Latin Hypercube with n=1 (edge case).
     */
    @Test
    public void testLatinSingleRow() {
        System.out.println("latin hypercube n=1");
        int[][] lhs = Sampling.latin(1, 3);
        assertEquals(1, lhs.length);
        assertEquals(3, lhs[0].length);
        for (int v : lhs[0]) {
            assertEquals(0, v);
        }
    }

    /**
     * Test strata with non-contiguous labels (e.g. 0, 2, 5).
     */
    @Test
    public void testStrataNonContiguous() {
        System.out.println("strata non-contiguous labels");
        int[] category = {0, 2, 5, 0, 2, 5, 0};
        int[][] strata = Sampling.strata(category);
        assertEquals(3, strata.length);
        // class 0 appears 3 times, class 2 appears 2 times, class 5 appears 2 times
        int total = strata[0].length + strata[1].length + strata[2].length;
        assertEquals(category.length, total);
    }

    /**
     * Test random with full subsample rate returns exactly n elements.
     */
    @Test
    public void testRandomFullRate() {
        System.out.println("random full rate");
        int[] samples = Sampling.random(50, 1.0);
        assertEquals(50, samples.length);
    }

    /**
     * Test stratify preserves class proportions approximately.
     */
    @Test
    public void testStratifyPreservesProportions() {
        System.out.println("stratify preserves proportions");
        MathEx.setSeed(12345);
        // Balanced classes
        int[] category = new int[200];
        for (int i = 0; i < 100; i++) category[i] = 0;
        for (int i = 100; i < 200; i++) category[i] = 1;

        int[] samples = Sampling.stratify(category, 0.5);
        // Should get exactly 50 from each class
        assertEquals(100, samples.length);
        int c0 = 0, c1 = 0;
        for (int s : samples) {
            if (category[s] == 0) c0++;
            else c1++;
        }
        assertEquals(50, c0);
        assertEquals(50, c1);
    }
}

