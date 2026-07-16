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
 *
 * @author Haifeng Li
 */
public class GammaTest {

    public GammaTest() {
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

    // ===== gamma =====

    @Test
    public void testGamma() {
        System.out.println("gamma");
        // Γ(0) = +∞
        assertTrue(Double.isInfinite(Gamma.gamma(0)));
        assertEquals(Double.POSITIVE_INFINITY, Gamma.gamma(0));

        // Positive integers: Γ(n) = (n-1)!
        assertEquals(1.0, Gamma.gamma(1), 1E-7);
        assertEquals(1.0, Gamma.gamma(2), 1E-7);
        assertEquals(2.0, Gamma.gamma(3), 1E-7);
        assertEquals(6.0, Gamma.gamma(4), 1E-7);
        assertEquals(24.0, Gamma.gamma(5), 1E-7);

        // Half-integers
        assertEquals(0.886227, Gamma.gamma(1.5), 1E-6);
        assertEquals(1.329340, Gamma.gamma(2.5), 1E-6);
        assertEquals(3.323351, Gamma.gamma(3.5), 1E-6);
        assertEquals(11.63173, Gamma.gamma(4.5), 1E-5);
    }

    @Test
    public void testGammaRecurrence() {
        System.out.println("gamma recurrence: Γ(x+1) = x·Γ(x)");
        for (double x : new double[]{0.5, 1.5, 2.5, 3.7, 5.1}) {
            assertEquals(x * Gamma.gamma(x), Gamma.gamma(x + 1), 1E-9,
                    "Recurrence failed at x=" + x);
        }
    }

    @Test
    public void testGammaReflection() {
        System.out.println("gamma reflection: Γ(x)Γ(1-x) = π/sin(πx)");
        for (double x : new double[]{0.1, 0.3, 0.5, 0.7, 0.9}) {
            double lhs = Gamma.gamma(x) * Gamma.gamma(1.0 - x);
            double rhs = Math.PI / Math.sin(Math.PI * x);
            assertEquals(rhs, lhs, 1E-7, "Reflection failed at x=" + x);
        }
    }

    @Test
    public void testGammaHalfIsRootPi() {
        System.out.println("Γ(1/2) = sqrt(π)");
        assertEquals(Math.sqrt(Math.PI), Gamma.gamma(0.5), 1E-10);
    }

    // ===== lgamma =====

    @Test
    public void testLogGamma() {
        System.out.println("lgamma");
        assertTrue(Double.isInfinite(Gamma.lgamma(0)));

        assertEquals(0.0, Gamma.lgamma(1), 1E-7);
        assertEquals(0.0, Gamma.lgamma(2), 1E-7);
        assertEquals(Math.log(2.0), Gamma.lgamma(3), 1E-7);
        assertEquals(Math.log(6.0), Gamma.lgamma(4), 1E-7);

        assertEquals(-0.1207822, Gamma.lgamma(1.5), 1E-7);
        assertEquals(0.2846829, Gamma.lgamma(2.5), 1E-7);
        assertEquals(1.200974, Gamma.lgamma(3.5), 1E-6);
        assertEquals(2.453737, Gamma.lgamma(4.5), 1E-6);
    }

    @Test
    public void testLogGammaMatchesLogGamma() {
        System.out.println("lgamma(x) = log(gamma(x)) for x > 0");
        for (double x : new double[]{0.5, 1.0, 1.5, 2.0, 5.0, 10.0}) {
            assertEquals(Math.log(Gamma.gamma(x)), Gamma.lgamma(x), 1E-9,
                    "lgamma mismatch at x=" + x);
        }
    }

    @Test
    public void testLogGammaLarge() {
        System.out.println("lgamma large values (Stirling approximation)");
        double n = 100.0;
        double stirling = (n - 0.5) * Math.log(n) - n + 0.5 * Math.log(2 * Math.PI);
        assertEquals(stirling, Gamma.lgamma(n), 0.01);
    }

    // ===== regularizedIncompleteGamma =====

    @Test
    public void testIncompleteGamma() {
        System.out.println("incompleteGamma");
        assertEquals(0.7807, Gamma.regularizedIncompleteGamma(2.1, 3), 1E-4);
        assertEquals(0.3504, Gamma.regularizedIncompleteGamma(3, 2.1), 1E-4);
    }

    @Test
    public void testIncompleteGammaAtZero() {
        System.out.println("P(s, 0) = 0");
        assertEquals(0.0, Gamma.regularizedIncompleteGamma(1.0, 0.0), 1E-12);
        assertEquals(0.0, Gamma.regularizedIncompleteGamma(2.5, 0.0), 1E-12);
    }

    @Test
    public void testIncompleteGammaLargeX() {
        System.out.println("P(s, large x) → 1");
        assertEquals(1.0, Gamma.regularizedIncompleteGamma(1.0, 100.0), 1E-10);
        assertEquals(1.0, Gamma.regularizedIncompleteGamma(5.0, 100.0), 1E-10);
    }

    @Test
    public void testIncompleteGammaMonotonicity() {
        System.out.println("P(s,x) monotone increasing in x");
        double s = 2.0;
        double prev = 0.0;
        for (double x = 0.5; x <= 10.0; x += 0.5) {
            double curr = Gamma.regularizedIncompleteGamma(s, x);
            assertTrue(curr > prev, "Not increasing at x=" + x);
            prev = curr;
        }
    }

    @Test
    public void testIncompleteGammaInvalidArgs() {
        System.out.println("incompleteGamma invalid args");
        assertThrows(IllegalArgumentException.class,
                () -> Gamma.regularizedIncompleteGamma(-1.0, 1.0));
        assertThrows(IllegalArgumentException.class,
                () -> Gamma.regularizedIncompleteGamma(1.0, -1.0));
    }

    // ===== regularizedUpperIncompleteGamma =====

    @Test
    public void testUpperIncompleteGamma() {
        System.out.println("upperIncompleteGamma");
        assertEquals(0.2193, Gamma.regularizedUpperIncompleteGamma(2.1, 3), 1E-4);
        assertEquals(0.6496, Gamma.regularizedUpperIncompleteGamma(3, 2.1), 1E-4);
    }

    @Test
    public void testUpperIncompleteGammaAtZero() {
        System.out.println("Q(s, 0) = 1");
        assertEquals(1.0, Gamma.regularizedUpperIncompleteGamma(1.0, 0.0), 1E-12);
        assertEquals(1.0, Gamma.regularizedUpperIncompleteGamma(3.0, 0.0), 1E-12);
    }

    @Test
    public void testPlusQEqualsOne() {
        System.out.println("P(s,x) + Q(s,x) = 1");
        double[][] cases = {{1.0, 1.0}, {2.1, 3.0}, {3.0, 2.1}, {5.0, 7.0}};
        for (double[] c : cases) {
            double p = Gamma.regularizedIncompleteGamma(c[0], c[1]);
            double q = Gamma.regularizedUpperIncompleteGamma(c[0], c[1]);
            assertEquals(1.0, p + q, 1E-10,
                    String.format("P+Q≠1 for s=%.1f, x=%.1f", c[0], c[1]));
        }
    }

    @Test
    public void testUpperIncompleteGammaNaN() {
        System.out.println("Q(s, NaN) = NaN (not 1)");
        assertTrue(Double.isNaN(Gamma.regularizedUpperIncompleteGamma(1.0, Double.NaN)));
    }

    // ===== inverseRegularizedIncompleteGamma =====

    @Test
    public void testInverseIncompleteGammaRoundTrip() {
        System.out.println("inverseRegularizedIncompleteGamma round-trip");
        double[] aValues = {0.5, 1.0, 2.0, 5.0};
        double[] pValues = {0.1, 0.25, 0.5, 0.75, 0.9};
        for (double a : aValues) {
            for (double p : pValues) {
                double x = Gamma.inverseRegularizedIncompleteGamma(a, p);
                double back = Gamma.regularizedIncompleteGamma(a, x);
                assertEquals(p, back, 1E-6,
                        String.format("Round-trip failed: a=%.1f, p=%.2f", a, p));
            }
        }
    }

    @Test
    public void testInverseIncompleteGammaExtremes() {
        System.out.println("inverseRegularizedIncompleteGamma extremes");
        // p=0 → 0
        assertEquals(0.0, Gamma.inverseRegularizedIncompleteGamma(2.0, 0.0), 1E-10);
        // p>=1 → large value
        double x = Gamma.inverseRegularizedIncompleteGamma(2.0, 1.0);
        assertTrue(x > 100.0);
    }

    @Test
    public void testInverseIncompleteGammaInvalidA() {
        System.out.println("inverseRegularizedIncompleteGamma invalid a");
        assertThrows(IllegalArgumentException.class,
                () -> Gamma.inverseRegularizedIncompleteGamma(-1.0, 0.5));
        assertThrows(IllegalArgumentException.class,
                () -> Gamma.inverseRegularizedIncompleteGamma(0.0, 0.5));
    }

    // ===== digamma =====

    @Test
    public void testDigammaKnownValues() {
        System.out.println("digamma known values");
        // ψ(1) = -γ (Euler-Mascheroni constant)
        assertEquals(-0.5772156649, Gamma.digamma(1.0), 1E-9);
        // ψ(2) = 1 - γ
        assertEquals(1.0 - 0.5772156649, Gamma.digamma(2.0), 1E-9);
        // ψ(1/2) = -γ - 2 ln 2
        assertEquals(-0.5772156649 - 2 * Math.log(2), Gamma.digamma(0.5), 1E-9);
    }

    @Test
    public void testDigammaRecurrence() {
        System.out.println("digamma recurrence: ψ(x+1) = ψ(x) + 1/x");
        for (double x : new double[]{0.5, 1.0, 1.5, 2.5, 5.0}) {
            assertEquals(Gamma.digamma(x) + 1.0 / x, Gamma.digamma(x + 1.0), 1E-10,
                    "Recurrence failed at x=" + x);
        }
    }

    @Test
    public void testDigammaAsymptotics() {
        System.out.println("digamma(x) → log(x) for large x");
        double x = 1000.0;
        assertEquals(Math.log(x), Gamma.digamma(x), 1E-3);
    }

    @Test
    public void testDigammaReflection() {
        System.out.println("digamma reflection: ψ(1-x) - ψ(x) = π·cot(πx)");
        for (double x : new double[]{0.1, 0.2, 0.3, 0.4}) {
            double lhs = Gamma.digamma(1.0 - x) - Gamma.digamma(x);
            double rhs = Math.PI / Math.tan(Math.PI * x);
            assertEquals(rhs, lhs, 1E-9, "Reflection failed at x=" + x);
        }
    }

    @Test
    public void testDigammaLargeX() {
        System.out.println("digamma large x uses asymptotic branch");
        // ψ(10) ≈ 2.25175...
        assertEquals(2.251753, Gamma.digamma(10.0), 1E-6);
    }
}

