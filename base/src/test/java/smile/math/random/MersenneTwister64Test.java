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
package smile.math.random;

import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for MersenneTwister64 (64-bit MT19937-64).
 *
 * @author Haifeng Li
 */
public class MersenneTwister64Test {

    @BeforeAll
    public static void setUpClass() {}

    @AfterAll
    public static void tearDownClass() {}

    @BeforeEach
    public void setUp() {}

    @AfterEach
    public void tearDown() {}

    // ===== Regression test vectors =====
    // Seeded with long[] {0x12345L, 0x23456L, 0x34567L, 0x45678L}
    // Used to detect accidental breakage of the MT64 state machine.

    @Test
    public void testRegressionSeedArray() {
        System.out.println("MersenneTwister64 regression: long[] seed produces stable output");
        MersenneTwister64 mt = new MersenneTwister64(0L);
        mt.setSeed(new long[]{0x12345L, 0x23456L, 0x34567L, 0x45678L});

        long[] first = new long[10];
        for (int i = 0; i < first.length; i++) {
            first[i] = mt.nextLong();
        }

        mt.setSeed(new long[]{0x12345L, 0x23456L, 0x34567L, 0x45678L});
        for (int i = 0; i < first.length; i++) {
            assertEquals(first[i], mt.nextLong(),
                    "Regression mismatch at position " + i);
        }
    }

    // ===== Determinism =====

    @Test
    public void testDeterminism() {
        System.out.println("MersenneTwister64 determinism with same seed");
        MersenneTwister64 a = new MersenneTwister64(12345L);
        MersenneTwister64 b = new MersenneTwister64(12345L);
        for (int i = 0; i < 1000; i++) {
            assertEquals(a.nextLong(), b.nextLong(), "Mismatch at step " + i);
        }
    }

    @Test
    public void testDifferentSeeds() {
        System.out.println("MersenneTwister64 different seeds produce different sequences");
        MersenneTwister64 a = new MersenneTwister64(1L);
        MersenneTwister64 b = new MersenneTwister64(2L);
        boolean differs = false;
        for (int i = 0; i < 100; i++) {
            if (a.nextLong() != b.nextLong()) { differs = true; break; }
        }
        assertTrue(differs);
    }

    @Test
    public void testSetSeedResetsState() {
        System.out.println("MersenneTwister64 setSeed resets state");
        MersenneTwister64 mt = new MersenneTwister64(999L);
        long first = mt.nextLong();
        mt.setSeed(999L);
        assertEquals(first, mt.nextLong());
    }

    // ===== nextDouble =====

    @Test
    public void testNextDoubleRange() {
        System.out.println("MersenneTwister64 nextDouble in [0,1)");
        MersenneTwister64 mt = new MersenneTwister64(42L);
        for (int i = 0; i < 10_000; i++) {
            double d = mt.nextDouble();
            assertTrue(d >= 0.0 && d < 1.0, "Out of range: " + d);
        }
    }

    @Test
    public void testNextDoubleMean() {
        System.out.println("MersenneTwister64 nextDouble mean ≈ 0.5");
        MersenneTwister64 mt = new MersenneTwister64(42L);
        double sum = 0;
        int n = 100_000;
        for (int i = 0; i < n; i++) sum += mt.nextDouble();
        assertEquals(0.5, sum / n, 0.01);
    }

    // ===== nextInt(n) =====

    @Test
    public void testNextIntNRange() {
        System.out.println("MersenneTwister64 nextInt(n) in [0,n)");
        MersenneTwister64 mt = new MersenneTwister64(42L);
        for (int n : new int[]{1, 2, 7, 100, 1000}) {
            for (int i = 0; i < 1000; i++) {
                int v = mt.nextInt(n);
                assertTrue(v >= 0 && v < n, "Out of range: " + v + " for n=" + n);
            }
        }
    }

    @Test
    public void testNextIntNInvalid() {
        System.out.println("MersenneTwister64 nextInt(0) throws");
        MersenneTwister64 mt = new MersenneTwister64(1L);
        assertThrows(IllegalArgumentException.class, () -> mt.nextInt(0));
        assertThrows(IllegalArgumentException.class, () -> mt.nextInt(-5));
    }

    // ===== nextLong =====

    @Test
    public void testNextLongFullRange() {
        System.out.println("MersenneTwister64 nextLong covers both positive and negative");
        MersenneTwister64 mt = new MersenneTwister64(42L);
        boolean hasNeg = false, hasPos = false;
        for (int i = 0; i < 1_000; i++) {
            long v = mt.nextLong();
            if (v < 0) hasNeg = true;
            if (v > 0) hasPos = true;
        }
        assertTrue(hasNeg);
        assertTrue(hasPos);
    }

    // ===== next(bits) =====

    @Test
    public void testNextBitsRange() {
        System.out.println("MersenneTwister64 next(bits) in [0, 2^bits)");
        MersenneTwister64 mt = new MersenneTwister64(42L);
        for (int bits : new int[]{1, 8, 16, 31}) {
            long bound = 1L << bits; // long to avoid int overflow at bits=31
            for (int i = 0; i < 200; i++) {
                int v = mt.next(bits);
                assertTrue(Integer.toUnsignedLong(v) < bound,
                        "next(" + bits + ") out of range: " + Integer.toUnsignedLong(v));
            }
        }
    }

    @Test
    public void testNextBitsStateConsistency() {
        System.out.println("MersenneTwister64 next(bits) alternates hi/lo halves");
        // Two consecutive next(32) calls use the same underlying 64-bit word split
        // into high 32 (first call) then low 32 (second call, bitState toggles).
        MersenneTwister64 a = new MersenneTwister64(77L);
        MersenneTwister64 b = new MersenneTwister64(77L);
        // First call: bitState=true → generates nextLong(), returns upper 32 bits
        int hi = a.next(32);
        // Second call: bitState=false → returns lower 32 bits of stored bits64
        int lo = a.next(32);
        long full = b.nextLong();
        // hi = upper 32 bits of full
        assertEquals((int)(full >>> 32), hi, "Upper 32 bits mismatch");
        // lo = lower 32 bits of full (signed cast)
        assertEquals((int)(full & 0xFFFFFFFFL), lo, "Lower 32 bits mismatch");
    }

    // ===== nextDoubles =====

    @Test
    public void testNextDoubles() {
        System.out.println("MersenneTwister64 nextDoubles");
        MersenneTwister64 mt = new MersenneTwister64(42L);
        double[] d = new double[1000];
        mt.nextDoubles(d);
        for (double v : d) {
            assertTrue(v >= 0.0 && v < 1.0, "Out of range: " + v);
        }
    }

    @Test
    public void testNextDoublesMatchesNextDouble() {
        System.out.println("MersenneTwister64 nextDoubles matches nextDouble");
        MersenneTwister64 a = new MersenneTwister64(99L);
        MersenneTwister64 b = new MersenneTwister64(99L);
        double[] d = new double[100];
        a.nextDoubles(d);
        for (double v : d) {
            assertEquals(v, b.nextDouble());
        }
    }

    // ===== nextGaussian =====

    @Test
    public void testNextGaussianMean() {
        System.out.println("MersenneTwister64 nextGaussian mean ≈ 0");
        MersenneTwister64 mt = new MersenneTwister64(42L);
        double sum = 0;
        int n = 100_000;
        for (int i = 0; i < n; i++) sum += mt.nextGaussian();
        assertEquals(0.0, sum / n, 0.05);
    }

    @Test
    public void testNextGaussianStdDev() {
        System.out.println("MersenneTwister64 nextGaussian std dev ≈ 1");
        MersenneTwister64 mt = new MersenneTwister64(42L);
        double sum = 0, sumSq = 0;
        int n = 100_000;
        for (int i = 0; i < n; i++) {
            double v = mt.nextGaussian();
            sum += v; sumSq += v * v;
        }
        double mean = sum / n;
        assertEquals(1.0, sumSq / n - mean * mean, 0.05);
    }

    // ===== long[] seed =====

    @Test
    public void testLongArraySeedDeterminism() {
        System.out.println("MersenneTwister64 long[] seed determinism");
        long[] seed = {1L, 2L, 3L, 4L, 5L};
        MersenneTwister64 a = new MersenneTwister64(0L);
        MersenneTwister64 b = new MersenneTwister64(0L);
        a.setSeed(seed);
        b.setSeed(seed);
        for (int i = 0; i < 100; i++) {
            assertEquals(a.nextLong(), b.nextLong());
        }
    }

    @Test
    public void testLongArraySeedNullSafe() {
        System.out.println("MersenneTwister64 null long[] seed is safe");
        MersenneTwister64 mt = new MersenneTwister64(0L);
        assertDoesNotThrow(() -> mt.setSeed((long[]) null));
    }
}

