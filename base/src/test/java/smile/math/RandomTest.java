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
package smile.math;

import java.util.Arrays;
import java.util.HashSet;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for smile.math.Random.
 *
 * @author Haifeng Li
 */
public class RandomTest {

    /** Fixed seed for reproducible tests. */
    private static final long SEED = 19650218L;

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
    public void testNextDouble() {
        System.out.println("nextDouble [0, 1)");
        Random rng = new Random(SEED);
        for (int i = 0; i < 100_000; i++) {
            double v = rng.nextDouble();
            assertTrue(v >= 0.0, "value below 0: " + v);
            assertTrue(v <  1.0, "value not below 1: " + v);
        }
    }

    @Test
    public void testNextDoubleRange() {
        System.out.println("nextDouble [lo, hi)");
        double lo = -10.0, hi = 20.0;
        Random rng = new Random(SEED);
        for (int i = 0; i < 100_000; i++) {
            double v = rng.nextDouble(lo, hi);
            assertTrue(v >= lo, "value below lo: " + v);
            assertTrue(v <  hi, "value not below hi: " + v);
        }
    }

    @Test
    public void testNextDoubleRangeInvalid() {
        System.out.println("nextDouble invalid range");
        Random rng = new Random(SEED);
        assertThrows(IllegalArgumentException.class, () -> rng.nextDouble(5.0, 5.0));
        assertThrows(IllegalArgumentException.class, () -> rng.nextDouble(6.0, 5.0));
    }

    @Test
    public void testNextDoublesArray() {
        System.out.println("nextDoubles(double[])");
        Random rng = new Random(SEED);
        double[] d = new double[10_000];
        rng.nextDoubles(d);
        for (double v : d) {
            assertTrue(v >= 0.0 && v < 1.0);
        }
    }

    @Test
    public void testNextDoublesArrayRange() {
        System.out.println("nextDoubles(double[], lo, hi)");
        double lo = -5.0, hi = 15.0;
        Random rng = new Random(SEED);
        double[] d = new double[10_000];
        rng.nextDoubles(d, lo, hi);
        for (double v : d) {
            assertTrue(v >= lo && v < hi);
        }
    }

    @Test
    public void testNextDoublesArrayRangeInvalid() {
        System.out.println("nextDoubles invalid range");
        Random rng = new Random(SEED);
        double[] d = new double[10];
        assertThrows(IllegalArgumentException.class, () -> rng.nextDoubles(d, 5.0, 5.0));
        assertThrows(IllegalArgumentException.class, () -> rng.nextDoubles(d, 6.0, 5.0));
    }

    @Test
    public void testNextInt() {
        System.out.println("nextInt");
        Random rng = new Random(SEED);
        // just verify it runs and produces a mix of positive and negative
        int pos = 0, neg = 0;
        for (int i = 0; i < 10_000; i++) {
            if (rng.nextInt() >= 0) pos++; else neg++;
        }
        assertTrue(pos > 4000, "Expected roughly half positive: " + pos);
        assertTrue(neg > 4000, "Expected roughly half negative: " + neg);
    }

    @Test
    public void testNextIntBounded() {
        System.out.println("nextInt(n)");
        Random rng = new Random(SEED);
        for (int n : new int[]{1, 2, 3, 7, 100, 1_000_000}) {
            for (int i = 0; i < 1000; i++) {
                int v = rng.nextInt(n);
                assertTrue(v >= 0 && v < n, "nextInt(" + n + ") out of range: " + v);
            }
        }
    }

    @Test
    public void testNextLong() {
        System.out.println("nextLong");
        Random rng = new Random(SEED);
        // verify full 64-bit range is used: should see values > Integer.MAX_VALUE
        boolean seenLarge = false;
        for (int i = 0; i < 10_000; i++) {
            if (rng.nextLong() > (long) Integer.MAX_VALUE) {
                seenLarge = true;
                break;
            }
        }
        assertTrue(seenLarge, "nextLong should produce values > Integer.MAX_VALUE");
    }

    @Test
    public void testNextBoolean() {
        System.out.println("nextBoolean");
        Random rng = new Random(SEED);
        int trueCount = 0;
        int n = 100_000;
        for (int i = 0; i < n; i++) {
            if (rng.nextBoolean()) trueCount++;
        }
        // Should be approximately 50%; allow ±2%
        double ratio = (double) trueCount / n;
        assertEquals(0.5, ratio, 0.02, "nextBoolean true ratio out of range: " + ratio);
    }

    @Test
    public void testNextFloat() {
        System.out.println("nextFloat");
        Random rng = new Random(SEED);
        for (int i = 0; i < 100_000; i++) {
            float v = rng.nextFloat();
            assertTrue(v >= 0.0f, "float below 0: " + v);
            assertTrue(v <  1.0f, "float not below 1: " + v);
        }
    }

    @Test
    public void testPermutateN() {
        System.out.println("permutate(n)");
        Random rng = new Random(SEED);
        int n = 100;
        int[] p = rng.permutate(n);
        assertEquals(n, p.length);
        // every value 0..n-1 appears exactly once
        int[] sorted = p.clone();
        Arrays.sort(sorted);
        for (int i = 0; i < n; i++) {
            assertEquals(i, sorted[i]);
        }
    }

    @Test
    public void testPermutateIntArray() {
        System.out.println("permutate(int[])");
        Random rng = new Random(SEED);
        int n = 20;
        int[] x = new int[n];
        for (int i = 0; i < n; i++) x[i] = i;
        rng.permutate(x);
        // all elements still present
        int[] sorted = x.clone();
        Arrays.sort(sorted);
        for (int i = 0; i < n; i++) assertEquals(i, sorted[i]);
        // verify it's actually shuffled (almost certainly differs from identity)
        boolean changed = false;
        for (int i = 0; i < n; i++) if (x[i] != i) { changed = true; break; }
        assertTrue(changed, "permutate left array unchanged");
    }

    @Test
    public void testPermutateDoubleArray() {
        System.out.println("permutate(double[])");
        Random rng = new Random(SEED);
        double[] x = {1.0, 2.0, 3.0, 4.0, 5.0};
        double[] orig = x.clone();
        rng.permutate(x);
        // same elements, just reordered
        double[] sorted = x.clone();
        Arrays.sort(sorted);
        assertArrayEquals(orig, sorted, 1E-15);
    }

    @Test
    public void testPermutateFloatArray() {
        System.out.println("permutate(float[])");
        Random rng = new Random(SEED);
        float[] x = {1.0f, 2.0f, 3.0f, 4.0f, 5.0f};
        float[] orig = x.clone();
        rng.permutate(x);
        float[] sorted = x.clone();
        Arrays.sort(sorted);
        assertArrayEquals(orig, sorted, 1E-6f);
    }

    @Test
    public void testPermutateObjectArray() {
        System.out.println("permutate(Object[])");
        Random rng = new Random(SEED);
        String[] x = {"a", "b", "c", "d", "e"};
        rng.permutate(x);
        // all original elements still present
        var set = new HashSet<>(Arrays.asList(x));
        assertTrue(set.contains("a") && set.contains("b") && set.contains("c")
                && set.contains("d") && set.contains("e"));
    }

    @Test
    public void testPermutateUniformity() {
        System.out.println("permutate uniformity (Fisher-Yates correctness)");
        // For an array of length 3, each of 6 permutations should appear ~equally often.
        Random rng = new Random(SEED);
        int[] counts = new int[6];
        int trials = 60_000;
        for (int t = 0; t < trials; t++) {
            int[] x = {0, 1, 2};
            rng.permutate(x);
            int idx = x[0] * 4 + x[1];  // unique encoding for 3-element permutation index
            // map (x[0],x[1]) -> permutation index
            switch (x[0] * 10 + x[1]) {
                case  1 -> counts[0]++;   // 0,1,2
                case  2 -> counts[1]++;   // 0,2,1
                case 10 -> counts[2]++;   // 1,0,2
                case 12 -> counts[3]++;   // 1,2,0
                case 20 -> counts[4]++;   // 2,0,1
                case 21 -> counts[5]++;   // 2,1,0
            }
        }
        // Each permutation should appear ~10000 times; allow ±5% relative error
        for (int i = 0; i < 6; i++) {
            assertEquals(10_000, counts[i], 500,
                "Permutation " + i + " count out of range: " + counts[i]);
        }
    }

    @Test
    public void testPermutateLength1() {
        System.out.println("permutate length-1 array");
        // Should not throw (loop condition i > 0 skips immediately)
        Random rng = new Random(SEED);
        int[] x = {42};
        assertDoesNotThrow(() -> rng.permutate(x));
        assertEquals(42, x[0]);
    }

    @Test
    public void testPermutateLength0() {
        System.out.println("permutate length-0 array");
        Random rng = new Random(SEED);
        int[] x = {};
        assertDoesNotThrow(() -> rng.permutate(x));
    }

    @Test
    public void testSetSeedReproducibility() {
        System.out.println("setSeed reproducibility");
        Random rng1 = new Random(SEED);
        Random rng2 = new Random(SEED);
        for (int i = 0; i < 1000; i++) {
            assertEquals(rng1.nextDouble(), rng2.nextDouble(), 1E-15);
            assertEquals(rng1.nextInt(),    rng2.nextInt());
            assertEquals(rng1.nextLong(),   rng2.nextLong());
        }
    }

    @Test
    public void testSetSeedReset() {
        System.out.println("setSeed reset");
        Random rng = new Random(SEED);
        double first = rng.nextDouble();
        rng.setSeed(SEED);  // reset
        assertEquals(first, rng.nextDouble(), 1E-15,
            "After setSeed reset, first value should match");
    }
}
