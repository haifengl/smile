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
package smile.util.function;

import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

public class RootTest {

    // f(x) = x³ + x² − 5x + 3, roots at x = -3, 1 (double)
    private static final Function POLY = x -> x * x * x + x * x - 5 * x + 3;
    private static final DifferentiableFunction DIFF_POLY = new DifferentiableFunction() {
        @Override public double f(double x) { return x * x * x + x * x - 5 * x + 3; }
        @Override public double g(double x) { return 3 * x * x + 2 * x - 5; }
    };

    @Test
    public void testBrent() {
        double result = POLY.root(-4, -2, 1E-7, 500);
        assertEquals(-3, result, 1E-7);
    }

    @Test
    public void testBrentSqrt2() {
        Function f = x -> x * x - 2;
        assertEquals(Math.sqrt(2), f.root(1.0, 2.0, 1e-12, 500), 1e-11);
    }

    @Test
    public void testBrentTolNotMutated() {
        // Regression test for the tol-mutation bug:
        // calling root() twice with the same tol must yield the same result.
        Function f = x -> x * x - 2;
        double tol = 1e-8;
        double r1 = f.root(1.0, 2.0, tol, 500);
        double r2 = f.root(1.0, 2.0, tol, 500);
        assertEquals(r1, r2, 1e-15,
                "Brent root() must not mutate tol: two calls gave different results");
    }

    @Test
    public void testBrentInvalidArgs() {
        assertThrows(IllegalArgumentException.class, () -> POLY.root(-4, -2, 0.0,  500));
        assertThrows(IllegalArgumentException.class, () -> POLY.root(-4, -2, 1e-7, 0));
        assertThrows(IllegalArgumentException.class, () -> POLY.root(-2, -1, 1e-7, 500));
    }

    @Test
    public void testNewton() {
        double result = DIFF_POLY.root(-4, -2, 1E-7, 500);
        assertEquals(-3, result, 1E-7);
    }

    @Test
    public void testNewtonSqrt2() {
        DifferentiableFunction f = new DifferentiableFunction() {
            @Override public double f(double x) { return x * x - 2; }
            @Override public double g(double x) { return 2 * x; }
        };
        assertEquals(Math.sqrt(2), f.root(1.0, 2.0, 1e-12, 500), 1e-11);
    }

    @Test
    public void testNewtonInvalidArgs() {
        assertThrows(IllegalArgumentException.class, () -> DIFF_POLY.root(-4, -2, 0.0,  500));
        assertThrows(IllegalArgumentException.class, () -> DIFF_POLY.root(-4, -2, 1e-7, 0));
        assertThrows(IllegalArgumentException.class, () -> DIFF_POLY.root(-2, -1, 1e-7, 500));
    }
}
