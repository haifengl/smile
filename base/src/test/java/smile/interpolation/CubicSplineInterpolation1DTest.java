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
package smile.interpolation;

import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link CubicSplineInterpolation1D}.
 *
 * @author Haifeng Li
 */
public class CubicSplineInterpolation1DTest {

    // sin(x) sampled at integers 0..6
    static final double[] X = {0, 1, 2, 3, 4, 5, 6};
    static final double[] Y = {0, 0.8415, 0.9093, 0.1411, -0.7568, -0.9589, -0.2794};

    @Test
    public void testInterpolate() {
        System.out.println("CubicSplineInterpolation1D.interpolate");
        CubicSplineInterpolation1D interp = new CubicSplineInterpolation1D(X, Y);
        assertEquals(0.5962098, interp.interpolate(2.5), 1E-7);
    }

    @Test
    public void testInterpolateAtKnots() {
        System.out.println("CubicSplineInterpolation1D.interpolate at knots");
        CubicSplineInterpolation1D interp = new CubicSplineInterpolation1D(X, Y);
        for (int i = 0; i < X.length; i++) {
            assertEquals(Y[i], interp.interpolate(X[i]), 1E-6,
                    "At knot x=" + X[i]);
        }
    }

    @Test
    public void testBetterThanLinearForSine() {
        System.out.println("CubicSplineInterpolation1D.better than linear for sine");
        // Cubic spline should approximate sin(2.5) better than linear
        double actual = Math.sin(2.5);         // ≈ 0.5985
        CubicSplineInterpolation1D cubic = new CubicSplineInterpolation1D(X, Y);
        LinearInterpolation linear = new LinearInterpolation(X, Y);
        double errCubic = Math.abs(cubic.interpolate(2.5) - actual);
        double errLinear = Math.abs(linear.interpolate(2.5) - actual);
        assertTrue(errCubic < errLinear,
                "Cubic spline should be more accurate than linear near sin peak");
    }

    @Test
    public void testPolynomialReproduction() {
        System.out.println("CubicSplineInterpolation1D.polynomial reproduction");
        // Natural cubic spline through y=x^2 data.
        // The natural (zero-2nd-deriv) boundary conditions mean the spline does NOT
        // reproduce x^2 exactly at non-knot points; verify the interpolant is close.
        double[] x = {0, 1, 2, 3, 4};
        double[] y = new double[x.length];
        for (int i = 0; i < x.length; i++) y[i] = x[i] * x[i];
        CubicSplineInterpolation1D interp = new CubicSplineInterpolation1D(x, y);
        // At knots, reproduction is exact
        for (int i = 0; i < x.length; i++) {
            assertEquals(y[i], interp.interpolate(x[i]), 1E-8, "Knot x=" + x[i]);
        }
        // At midpoints, within 2% of true x^2
        assertEquals(2.25, interp.interpolate(1.5), 0.05);
        assertEquals(6.25, interp.interpolate(2.5), 0.05);
    }

    @Test
    public void testExtrapolation() {
        System.out.println("CubicSplineInterpolation1D.extrapolation");
        CubicSplineInterpolation1D interp = new CubicSplineInterpolation1D(X, Y);
        // Should not throw; result may not be accurate but must return a finite value
        double val = interp.interpolate(-0.5);
        assertTrue(Double.isFinite(val));
        val = interp.interpolate(7.0);
        assertTrue(Double.isFinite(val));
    }

    @Test
    public void testDecreasingGrid() {
        System.out.println("CubicSplineInterpolation1D.decreasing grid");
        double[] xd = {6, 5, 4, 3, 2, 1, 0};
        double[] yd = {-0.2794, -0.9589, -0.7568, 0.1411, 0.9093, 0.8415, 0};
        CubicSplineInterpolation1D interp = new CubicSplineInterpolation1D(xd, yd);
        assertEquals(0.5962098, interp.interpolate(2.5), 1E-6);
    }

    @Test
    public void testInputValidation() {
        System.out.println("CubicSplineInterpolation1D.input validation");
        assertThrows(IllegalArgumentException.class,
                () -> new CubicSplineInterpolation1D(new double[]{0, 1}, new double[]{0}));
    }

    @Test
    public void testToString() {
        CubicSplineInterpolation1D interp = new CubicSplineInterpolation1D(X, Y);
        assertNotNull(interp.toString());
        assertFalse(interp.toString().isEmpty());
    }
}