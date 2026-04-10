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
package smile.math.special;

import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the Erf class (error function and related functions).
 *
 * @author Haifeng Li
 */
public class ErfTest {

    @BeforeAll
    public static void setUpClass() {
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

    // ===== erf =====

    @Test
    public void testErfAtZero() {
        System.out.println("erf(0)");
        assertEquals(0.0, Erf.erf(0.0), 1E-15);
    }

    @Test
    public void testErfKnownValues() {
        System.out.println("erf known values");
        // Reference values from Abramowitz & Stegun / Wolfram Alpha
        assertEquals(0.842701, Erf.erf(1.0),  1E-6);
        assertEquals(0.995322, Erf.erf(2.0),  1E-6);
        assertEquals(0.999978, Erf.erf(3.0),  1E-6);
        assertEquals(0.520500, Erf.erf(0.5),  1E-6);
        assertEquals(0.112463, Erf.erf(0.1),  1E-6);
    }

    @Test
    public void testErfOddSymmetry() {
        System.out.println("erf odd symmetry: erf(-x) = -erf(x)");
        for (double x : new double[]{0.1, 0.5, 1.0, 2.0, 3.0}) {
            assertEquals(-Erf.erf(x), Erf.erf(-x), 1E-12, "Failed at x=" + x);
        }
    }

    @Test
    public void testErfRange() {
        System.out.println("erf range (-1, 1)");
        for (double x : new double[]{-5, -2, -1, 0, 1, 2, 5}) {
            double v = Erf.erf(x);
            assertTrue(v >= -1.0 && v <= 1.0, "Out of range at x=" + x);
        }
    }

    @Test
    public void testErfLargePositive() {
        System.out.println("erf large positive");
        assertEquals(1.0, Erf.erf(6.0), 1E-10);
    }

    @Test
    public void testErfLargeNegative() {
        System.out.println("erf large negative");
        assertEquals(-1.0, Erf.erf(-6.0), 1E-10);
    }

    // ===== erfc =====

    @Test
    public void testErfcAtZero() {
        System.out.println("erfc(0)");
        assertEquals(1.0, Erf.erfc(0.0), 1E-15);
    }

    @Test
    public void testErfcKnownValues() {
        System.out.println("erfc known values");
        assertEquals(0.157299, Erf.erfc(1.0), 1E-6);
        assertEquals(0.004678, Erf.erfc(2.0), 1E-6);
        assertEquals(0.479500, Erf.erfc(0.5), 1E-6);
    }

    @Test
    public void testErfPlusErfcEqualsOne() {
        System.out.println("erf(x) + erfc(x) = 1");
        for (double x : new double[]{-3, -2, -1, -0.5, 0, 0.5, 1, 2, 3}) {
            assertEquals(1.0, Erf.erf(x) + Erf.erfc(x), 1E-12, "Failed at x=" + x);
        }
    }

    @Test
    public void testErfcSymmetry() {
        System.out.println("erfc(-x) = 2 - erfc(x)");
        for (double x : new double[]{0.5, 1.0, 2.0}) {
            assertEquals(2.0 - Erf.erfc(x), Erf.erfc(-x), 1E-12, "Failed at x=" + x);
        }
    }

    @Test
    public void testErfcLargePositive() {
        System.out.println("erfc large positive → 0");
        assertEquals(0.0, Erf.erfc(6.0), 1E-10);
    }

    @Test
    public void testErfcLargeNegative() {
        System.out.println("erfc large negative → 2");
        assertEquals(2.0, Erf.erfc(-6.0), 1E-10);
    }

    // ===== erfcc (fast approximation) =====

    @Test
    public void testErfccAtZero() {
        System.out.println("erfcc(0)");
        assertEquals(1.0, Erf.erfcc(0.0), 1E-7);
    }

    @Test
    public void testErfccAgreesWithErfc() {
        System.out.println("erfcc agrees with erfc within tolerance");
        for (double x : new double[]{0.1, 0.5, 1.0, 1.5, 2.0, 3.0}) {
            assertEquals(Erf.erfc(x), Erf.erfcc(x), 1.2E-7, "Disagreement at x=" + x);
        }
    }

    @Test
    public void testErfccSymmetry() {
        System.out.println("erfcc symmetry: erfcc(-x) = 2 - erfcc(x)");
        for (double x : new double[]{0.5, 1.0, 2.0}) {
            assertEquals(2.0 - Erf.erfcc(x), Erf.erfcc(-x), 1E-7, "Failed at x=" + x);
        }
    }

    // ===== inverfc =====

    @Test
    public void testInverfcAtOne() {
        System.out.println("inverfc(1) = 0");
        assertEquals(0.0, Erf.inverfc(1.0), 1E-6);
    }

    @Test
    public void testInverfcRoundTrip() {
        System.out.println("inverfc round-trip: erfc(inverfc(p)) = p");
        for (double p : new double[]{0.05, 0.1, 0.3, 0.5, 0.7, 0.9, 1.5}) {
            double x = Erf.inverfc(p);
            assertEquals(p, Erf.erfc(x), 1E-6, "Round-trip failed at p=" + p);
        }
    }

    @Test
    public void testInverfcExtremes() {
        System.out.println("inverfc extreme values");
        // p >= 2 → -100
        assertEquals(-100.0, Erf.inverfc(2.0), 1E-9);
        // p <= 0 → +100
        assertEquals(100.0, Erf.inverfc(0.0), 1E-9);
    }

    // ===== inverf =====

    @Test
    public void testInverfAtZero() {
        System.out.println("inverf(0) = 0");
        assertEquals(0.0, Erf.inverf(0.0), 1E-6);
    }

    @Test
    public void testInverfRoundTrip() {
        System.out.println("inverf round-trip: erf(inverf(p)) = p");
        for (double p : new double[]{-0.9, -0.5, -0.1, 0.0, 0.1, 0.5, 0.9}) {
            double x = Erf.inverf(p);
            assertEquals(p, Erf.erf(x), 1E-6, "Round-trip failed at p=" + p);
        }
    }

    @Test
    public void testInverfOddSymmetry() {
        System.out.println("inverf odd symmetry: inverf(-p) = -inverf(p)");
        for (double p : new double[]{0.1, 0.3, 0.7}) {
            assertEquals(-Erf.inverf(p), Erf.inverf(-p), 1E-6, "Failed at p=" + p);
        }
    }

    // ===== erf-Gamma relation =====

    @Test
    public void testErfAsIncompleteGamma() {
        System.out.println("erf(x) = P(1/2, x^2) (relation to incomplete gamma)");
        for (double x : new double[]{0.5, 1.0, 1.5, 2.0}) {
            double erfVal  = Erf.erf(x);
            double gammaVal = Gamma.regularizedIncompleteGamma(0.5, x * x);
            assertEquals(erfVal, gammaVal, 1E-6, "erf-gamma relation failed at x=" + x);
        }
    }
}

