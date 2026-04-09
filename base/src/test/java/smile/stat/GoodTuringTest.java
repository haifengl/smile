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

import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 *
 * @author Haifeng Li
 */
public class GoodTuringTest {

    public GoodTuringTest() {
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
    public void test() {
        System.out.println("GoodTuring");
        int[] r = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 12};
        int[] Nr = {120, 40, 24, 13, 15, 5, 11, 2, 2, 1, 3};
        double p0 = 0.2047782;
        double[] p = {
                0.0009267, 0.0024393, 0.0040945, 0.0058063, 0.0075464,
                0.0093026, 0.0110689, 0.0128418, 0.0146194, 0.0164005, 0.0199696};

        GoodTuring result = GoodTuring.of(r, Nr);
        assertEquals(p0, result.p0, 1E-7);
        for (int i = 0; i < r.length; i++) {
            assertEquals(p[i], result.p[i], 1E-7);
        }
    }

    /**
     * Test that p0 + sum(Nr[j]*p[j]) = 1 (probability conservation).
     */
    @Test
    public void testProbabilityConservation() {
        System.out.println("GoodTuring probability conservation");
        int[] r = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 12};
        int[] Nr = {120, 40, 24, 13, 15, 5, 11, 2, 2, 1, 3};

        GoodTuring result = GoodTuring.of(r, Nr);

        // p0 + sum of (Nr[j] * p[j]) should ≈ 1
        double total = result.p0;
        for (int j = 0; j < r.length; j++) {
            total += Nr[j] * result.p[j];
        }
        assertEquals(1.0, total, 1E-6);
    }

    /**
     * Test that all estimated probabilities are positive.
     */
    @Test
    public void testPositiveProbabilities() {
        System.out.println("GoodTuring positive probabilities");
        int[] r = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 12};
        int[] Nr = {120, 40, 24, 13, 15, 5, 11, 2, 2, 1, 3};

        GoodTuring result = GoodTuring.of(r, Nr);
        assertTrue(result.p0 > 0 && result.p0 < 1);
        for (double p : result.p) {
            assertTrue(p > 0, "Probability must be positive: " + p);
        }
    }

    /**
     * Test that p0 == 0 when r[0] != 1 (no singletons).
     */
    @Test
    public void testNoSingletons() {
        System.out.println("GoodTuring no singletons");
        // r starts at 2, so n1=0, p0=0
        int[] r = {2, 4, 6, 8, 10, 12};
        int[] Nr = {30, 20, 15, 10, 5, 3};
        GoodTuring result = GoodTuring.of(r, Nr);
        assertEquals(0.0, result.p0, 1E-10);
    }

    /**
     * Test input validation: different sizes.
     */
    @Test
    public void testDifferentSizes() {
        assertThrows(IllegalArgumentException.class, () ->
                GoodTuring.of(new int[]{1, 2, 3}, new int[]{5, 3}));
    }

    /**
     * Test input validation: too few buckets.
     */
    @Test
    public void testTooFewBuckets() {
        assertThrows(IllegalArgumentException.class, () ->
                GoodTuring.of(new int[]{1}, new int[]{10}));
    }

    /**
     * Test input validation: non-ascending frequencies.
     */
    @Test
    public void testNonAscendingFrequencies() {
        assertThrows(IllegalArgumentException.class, () ->
                GoodTuring.of(new int[]{3, 2, 5}, new int[]{10, 5, 3}));
    }

    /**
     * Test input validation: non-positive frequency count.
     */
    @Test
    public void testNonPositiveNr() {
        assertThrows(IllegalArgumentException.class, () ->
                GoodTuring.of(new int[]{1, 2, 3}, new int[]{10, 0, 5}));
    }

    /**
     * Test with minimal valid input (2 buckets).
     */
    @Test
    public void testMinimalInput() {
        System.out.println("GoodTuring minimal input");
        int[] r = {1, 2};
        int[] Nr = {10, 5};
        GoodTuring result = GoodTuring.of(r, Nr);
        assertNotNull(result);
        assertEquals(2, result.p.length);
        assertTrue(result.p0 >= 0 && result.p0 < 1);
    }
}
