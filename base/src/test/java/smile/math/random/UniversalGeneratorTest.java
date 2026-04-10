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
 * Tests for UniversalGenerator.
 *
 * @author Haifeng Li
 */
public class UniversalGeneratorTest {

    @BeforeAll
    public static void setUpClass() {}

    @AfterAll
    public static void tearDownClass() {}

    @BeforeEach
    public void setUp() {}

    @AfterEach
    public void tearDown() {}

    // ===== Determinism =====

    @Test
    public void testDeterminism() {
        System.out.println("UniversalGenerator determinism with same seed");
        UniversalGenerator a = new UniversalGenerator(12345);
        UniversalGenerator b = new UniversalGenerator(12345);
        for (int i = 0; i < 1000; i++) {
            assertEquals(a.nextDouble(), b.nextDouble(), "Mismatch at step " + i);
        }
    }

    @Test
    public void testLongSeedDeterminism() {
        System.out.println("UniversalGenerator long seed determinism");
        long seed = 987654321L;
        UniversalGenerator a = new UniversalGenerator(seed);
        UniversalGenerator b = new UniversalGenerator(seed);
        for (int i = 0; i < 100; i++) {
            assertEquals(a.nextDouble(), b.nextDouble());
        }
    }

    @Test
    public void testDifferentSeeds() {
        System.out.println("UniversalGenerator different seeds produce different sequences");
        UniversalGenerator a = new UniversalGenerator(1);
        UniversalGenerator b = new UniversalGenerator(2);
        boolean differs = false;
        for (int i = 0; i < 100; i++) {
            if (a.nextDouble() != b.nextDouble()) { differs = true; break; }
        }
        assertTrue(differs);
    }

    @Test
    public void testSetSeedResetsState() {
        System.out.println("UniversalGenerator setSeed resets state");
        UniversalGenerator rng = new UniversalGenerator(42);
        double first = rng.nextDouble();
        rng.setSeed(42L);
        assertEquals(first, rng.nextDouble());
    }

    // ===== nextDouble =====

    @Test
    public void testNextDoubleRange() {
        System.out.println("UniversalGenerator nextDouble in [0,1)");
        UniversalGenerator rng = new UniversalGenerator(42);
        for (int i = 0; i < 10_000; i++) {
            double d = rng.nextDouble();
            assertTrue(d >= 0.0 && d < 1.0, "Out of range: " + d);
        }
    }

    @Test
    public void testNextDoubleMean() {
        System.out.println("UniversalGenerator nextDouble mean ≈ 0.5");
        UniversalGenerator rng = new UniversalGenerator(42);
        double sum = 0;
        int n = 100_000;
        for (int i = 0; i < n; i++) sum += rng.nextDouble();
        assertEquals(0.5, sum / n, 0.01);
    }

    @Test
    public void testNextDoubleUniform() {
        System.out.println("UniversalGenerator nextDouble passes basic uniformity");
        UniversalGenerator rng = new UniversalGenerator(12345);
        int[] buckets = new int[10];
        int total = 100_000;
        for (int i = 0; i < total; i++) {
            int b = (int) (rng.nextDouble() * 10);
            if (b == 10) b = 9; // protect edge
            buckets[b]++;
        }
        for (int c : buckets) {
            assertTrue(Math.abs(c - total / 10) < total / 10 * 0.10,
                    "Non-uniform bucket: " + c);
        }
    }

    // ===== nextDoubles =====

    @Test
    public void testNextDoubles() {
        System.out.println("UniversalGenerator nextDoubles");
        UniversalGenerator rng = new UniversalGenerator(42);
        double[] d = new double[1000];
        rng.nextDoubles(d);
        for (double v : d) {
            assertTrue(v >= 0.0 && v < 1.0, "Out of range: " + v);
        }
    }

    @Test
    public void testNextDoublesMatchesNextDouble() {
        System.out.println("UniversalGenerator nextDoubles matches nextDouble");
        UniversalGenerator a = new UniversalGenerator(99);
        UniversalGenerator b = new UniversalGenerator(99);
        double[] d = new double[100];
        a.nextDoubles(d);
        for (double v : d) {
            assertEquals(v, b.nextDouble());
        }
    }

    // ===== nextInt(n) =====

    @Test
    public void testNextIntNRange() {
        System.out.println("UniversalGenerator nextInt(n) in [0,n)");
        UniversalGenerator rng = new UniversalGenerator(42);
        for (int n : new int[]{1, 2, 7, 100, 1000}) {
            for (int i = 0; i < 10_000; i++) {
                int v = rng.nextInt(n);
                assertTrue(v >= 0 && v < n, "Out of range: " + v + " for n=" + n);
            }
        }
    }

    @Test
    public void testNextIntNLarge() {
        System.out.println("UniversalGenerator nextInt(n) large n never returns n");
        // Bug fix: (int)(nextDouble() * n) can return n for large n due to FP rounding
        UniversalGenerator rng = new UniversalGenerator(42);
        int n = Integer.MAX_VALUE / 2;
        for (int i = 0; i < 10_000; i++) {
            int v = rng.nextInt(n);
            assertTrue(v >= 0 && v < n, "Out of range: " + v);
        }
    }

    @Test
    public void testNextIntNInvalid() {
        System.out.println("UniversalGenerator nextInt(0) throws");
        UniversalGenerator rng = new UniversalGenerator(1);
        assertThrows(IllegalArgumentException.class, () -> rng.nextInt(0));
        assertThrows(IllegalArgumentException.class, () -> rng.nextInt(-1));
    }

    @Test
    public void testNextIntNUniform() {
        System.out.println("UniversalGenerator nextInt(n) roughly uniform");
        UniversalGenerator rng = new UniversalGenerator(42);
        int n = 10;
        int[] counts = new int[n];
        int total = 100_000;
        for (int i = 0; i < total; i++) counts[rng.nextInt(n)]++;
        for (int c : counts) {
            assertTrue(Math.abs(c - total / n) < total / n * 0.20,
                    "Non-uniform bucket: " + c);
        }
    }

    // ===== nextInt =====

    @Test
    public void testNextIntDeterminism() {
        System.out.println("UniversalGenerator nextInt determinism");
        UniversalGenerator a = new UniversalGenerator(7);
        UniversalGenerator b = new UniversalGenerator(7);
        for (int i = 0; i < 1000; i++) {
            assertEquals(a.nextInt(), b.nextInt());
        }
    }

    @Test
    public void testNextIntRange() {
        System.out.println("UniversalGenerator nextInt covers both signs");
        // Bug fix: old implementation used Integer.MAX_VALUE*(2*d-1) which
        // could overflow; new implementation uses (int)(long)(d * 0x1p32)
        UniversalGenerator rng = new UniversalGenerator(42);
        boolean hasNeg = false, hasPos = false;
        for (int i = 0; i < 10_000; i++) {
            int v = rng.nextInt();
            if (v < 0) hasNeg = true;
            if (v > 0) hasPos = true;
        }
        assertTrue(hasNeg, "nextInt should produce negative values");
        assertTrue(hasPos, "nextInt should produce positive values");
    }

    // ===== nextLong =====

    @Test
    public void testNextLongDeterminism() {
        System.out.println("UniversalGenerator nextLong determinism");
        UniversalGenerator a = new UniversalGenerator(42);
        UniversalGenerator b = new UniversalGenerator(42);
        for (int i = 0; i < 500; i++) {
            assertEquals(a.nextLong(), b.nextLong());
        }
    }

    @Test
    public void testNextLongFullRange() {
        System.out.println("UniversalGenerator nextLong covers both positive and negative");
        UniversalGenerator rng = new UniversalGenerator(42);
        boolean hasNeg = false, hasPos = false;
        for (int i = 0; i < 1_000; i++) {
            long v = rng.nextLong();
            if (v < 0) hasNeg = true;
            if (v > 0) hasPos = true;
        }
        assertTrue(hasNeg, "nextLong should produce negative values");
        assertTrue(hasPos, "nextLong should produce positive values");
    }

    // ===== next(bits) =====

    @Test
    public void testNextBitsRange() {
        System.out.println("UniversalGenerator next(bits) in [0, 2^bits)");
        UniversalGenerator rng = new UniversalGenerator(42);
        for (int bits : new int[]{1, 8, 16, 31}) {
            long bound = 1L << bits; // long to avoid int overflow at bits=31
            for (int i = 0; i < 500; i++) {
                int v = rng.next(bits);
                assertTrue(Integer.toUnsignedLong(v) < bound,
                        "next(" + bits + ") out of range: " + Integer.toUnsignedLong(v));
            }
        }
    }

    // ===== nextGaussian =====

    @Test
    public void testNextGaussianMean() {
        System.out.println("UniversalGenerator nextGaussian mean ≈ 0");
        UniversalGenerator rng = new UniversalGenerator(42);
        double sum = 0;
        int n = 100_000;
        for (int i = 0; i < n; i++) sum += rng.nextGaussian();
        assertEquals(0.0, sum / n, 0.05);
    }

    @Test
    public void testNextGaussianStdDev() {
        System.out.println("UniversalGenerator nextGaussian std dev ≈ 1");
        UniversalGenerator rng = new UniversalGenerator(42);
        double sum = 0, sumSq = 0;
        int n = 100_000;
        for (int i = 0; i < n; i++) {
            double v = rng.nextGaussian();
            sum += v; sumSq += v * v;
        }
        double mean = sum / n;
        assertEquals(1.0, sumSq / n - mean * mean, 0.05);
    }

    // ===== Edge cases =====

    @Test
    public void testZeroSeed() {
        System.out.println("UniversalGenerator seed=0 works");
        assertDoesNotThrow(() -> new UniversalGenerator(0));
        UniversalGenerator rng = new UniversalGenerator(0);
        double d = rng.nextDouble();
        assertTrue(d >= 0.0 && d < 1.0);
    }

    @Test
    public void testDefaultConstructor() {
        System.out.println("UniversalGenerator default constructor works");
        assertDoesNotThrow(() -> new UniversalGenerator());
        UniversalGenerator rng = new UniversalGenerator();
        double d = rng.nextDouble();
        assertTrue(d >= 0.0 && d < 1.0);
    }
}

