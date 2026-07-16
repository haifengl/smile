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
 * Tests for the Beta class.
 *
 * @author Haifeng Li
 */
public class BetaTest {

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

    // ===== Beta function =====

    @Test
    public void testBetaKnownValues() {
        System.out.println("beta known values");
        // B(1,1) = 1
        assertEquals(1.0, Beta.beta(1.0, 1.0), 1E-10);
        // B(2,2) = 1/6
        assertEquals(1.0 / 6.0, Beta.beta(2.0, 2.0), 1E-10);
        // B(0.5, 0.5) = pi
        assertEquals(Math.PI, Beta.beta(0.5, 0.5), 1E-9);
        // B(a,b) = Γ(a)Γ(b)/Γ(a+b)
        assertEquals(1.0 / 12.0, Beta.beta(2.0, 3.0), 1E-10);
        assertEquals(1.0 / 30.0, Beta.beta(3.0, 3.0), 1E-10);
    }

    @Test
    public void testBetaSymmetry() {
        System.out.println("beta symmetry: B(x,y) = B(y,x)");
        double[][] pairs = {{1.5, 2.5}, {0.5, 3.0}, {2.0, 4.0}, {0.1, 0.9}};
        for (double[] p : pairs) {
            assertEquals(Beta.beta(p[0], p[1]), Beta.beta(p[1], p[0]), 1E-12,
                    String.format("Symmetry failed for B(%.1f,%.1f)", p[0], p[1]));
        }
    }

    @Test
    public void testBetaRelationToGamma() {
        System.out.println("beta = exp(lgamma(a)+lgamma(b)-lgamma(a+b))");
        double a = 2.5, b = 3.5;
        double expected = Math.exp(Gamma.lgamma(a) + Gamma.lgamma(b) - Gamma.lgamma(a + b));
        assertEquals(expected, Beta.beta(a, b), 1E-12);
    }

    // ===== Regularized Incomplete Beta =====

    @Test
    public void testRegularizedIncompleteBetaAtZero() {
        System.out.println("regularizedIncompleteBeta(a,b,0) = 0");
        assertEquals(0.0, Beta.regularizedIncompleteBetaFunction(1.0, 1.0, 0.0), 1E-12);
        assertEquals(0.0, Beta.regularizedIncompleteBetaFunction(2.0, 3.0, 0.0), 1E-12);
    }

    @Test
    public void testRegularizedIncompleteBetaAtOne() {
        System.out.println("regularizedIncompleteBeta(a,b,1) = 1");
        assertEquals(1.0, Beta.regularizedIncompleteBetaFunction(1.0, 1.0, 1.0), 1E-12);
        assertEquals(1.0, Beta.regularizedIncompleteBetaFunction(2.0, 3.0, 1.0), 1E-12);
    }

    @Test
    public void testRegularizedIncompleteBetaKnownValues() {
        System.out.println("regularizedIncompleteBeta known values");
        // For a=b=1, I_x(1,1) = x (uniform CDF)
        assertEquals(0.5, Beta.regularizedIncompleteBetaFunction(1.0, 1.0, 0.5), 1E-10);
        assertEquals(0.3, Beta.regularizedIncompleteBetaFunction(1.0, 1.0, 0.3), 1E-10);

        // I_x(2,2) = 3x^2 - 2x^3
        double x = 0.4;
        double expected = 3 * x * x - 2 * x * x * x;
        assertEquals(expected, Beta.regularizedIncompleteBetaFunction(2.0, 2.0, x), 1E-8);

        // Reference values verified by closed-form integration:
        // I_x(2,5): ∫₀ˣ t(1-t)⁴dt / B(2,5), B(2,5)=1/30
        //   = 30·[-t(1-t)⁵/5 - (1-t)⁶/30]₀ˣ
        //   at x=0.4: 30·[-0.4·0.6⁵/5 - 0.6⁶/30 + 1/30] ≈ 0.76672
        assertEquals(0.76672, Beta.regularizedIncompleteBetaFunction(2.0, 5.0, 0.4), 1E-5);
        // I_x(5,2): 30·∫₀ˣ t⁴(1-t)dt = 30·[t⁵/5 - t⁶/6]₀ˣ
        //   at x=0.4: 30·(0.4⁵/5 - 0.4⁶/6) = 30·(0.002048/5 - ...) ≈ 0.04096
        assertEquals(0.04096, Beta.regularizedIncompleteBetaFunction(5.0, 2.0, 0.4), 1E-5);
    }

    @Test
    public void testRegularizedIncompleteBetaSymmetry() {
        System.out.println("I_x(a,b) + I_{1-x}(b,a) = 1");
        double[][] cases = {{1.0, 2.0, 0.3}, {3.0, 5.0, 0.6}, {0.5, 1.5, 0.7}};
        for (double[] c : cases) {
            double a = c[0], b = c[1], x = c[2];
            double sum = Beta.regularizedIncompleteBetaFunction(a, b, x)
                       + Beta.regularizedIncompleteBetaFunction(b, a, 1.0 - x);
            assertEquals(1.0, sum, 1E-10,
                    String.format("Symmetry failed for I_%.1f(%.1f,%.1f)", x, a, b));
        }
    }

    @Test
    public void testRegularizedIncompleteBetaSlackNearZero() {
        System.out.println("regularizedIncompleteBeta slack for x slightly < 0");
        // Should clamp to 0 for tiny negative x (floating-point noise tolerance)
        assertEquals(0.0, Beta.regularizedIncompleteBetaFunction(1.0, 1.0, -1E-10), 1E-12);
    }

    @Test
    public void testRegularizedIncompleteBetaSlackNearOne() {
        System.out.println("regularizedIncompleteBeta slack for x slightly > 1");
        assertEquals(1.0, Beta.regularizedIncompleteBetaFunction(1.0, 1.0, 1.0 + 1E-10), 1E-12);
    }

    @Test
    public void testRegularizedIncompleteBetaInvalidX() {
        System.out.println("regularizedIncompleteBeta invalid x");
        assertThrows(IllegalArgumentException.class,
                () -> Beta.regularizedIncompleteBetaFunction(2.0, 2.0, -0.5));
        assertThrows(IllegalArgumentException.class,
                () -> Beta.regularizedIncompleteBetaFunction(2.0, 2.0, 1.5));
    }

    @Test
    public void testRegularizedIncompleteBetaMonotonicity() {
        System.out.println("regularizedIncompleteBeta monotone increasing in x");
        double prev = 0.0;
        for (double x = 0.1; x <= 0.95; x += 0.1) {
            double curr = Beta.regularizedIncompleteBetaFunction(2.0, 3.0, x);
            assertTrue(curr > prev, "Not increasing at x=" + x);
            prev = curr;
        }
    }

    // ===== Inverse Regularized Incomplete Beta =====

    @Test
    public void testInverseRegularizedIncompleteBetaRoundTrip() {
        System.out.println("inverseRegularizedIncompleteBeta round-trip");
        double[][] params = {{1.0, 1.0}, {2.0, 3.0}, {5.0, 2.0}, {0.5, 0.5}};
        for (double[] ab : params) {
            double a = ab[0], b = ab[1];
            for (double p : new double[]{0.1, 0.25, 0.5, 0.75, 0.9}) {
                double x = Beta.inverseRegularizedIncompleteBetaFunction(a, b, p);
                double back = Beta.regularizedIncompleteBetaFunction(a, b, x);
                assertEquals(p, back, 1E-6,
                        String.format("Round-trip failed: a=%.1f, b=%.1f, p=%.2f", a, b, p));
            }
        }
    }

    @Test
    public void testInverseRegularizedIncompleteBetaExtremes() {
        System.out.println("inverseRegularizedIncompleteBeta extremes");
        assertEquals(0.0, Beta.inverseRegularizedIncompleteBetaFunction(2.0, 2.0, 0.0), 1E-10);
        assertEquals(1.0, Beta.inverseRegularizedIncompleteBetaFunction(2.0, 2.0, 1.0), 1E-10);
    }

    @Test
    public void testInverseRegularizedIncompleteBetaHalf() {
        System.out.println("inverseRegularizedIncompleteBeta: I_{0.5}(a,a) = 0.5 for symmetric");
        // For a=b (symmetric beta), median is 0.5
        double x = Beta.inverseRegularizedIncompleteBetaFunction(2.0, 2.0, 0.5);
        assertEquals(0.5, x, 1E-6);
    }
}

