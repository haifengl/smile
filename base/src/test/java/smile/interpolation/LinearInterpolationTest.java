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
 * Tests for {@link LinearInterpolation}.
 *
 * @author Haifeng Li
 */
public class LinearInterpolationTest {

    // sin(x) sampled at integers 0..6
    static final double[] X = {0, 1, 2, 3, 4, 5, 6};
    static final double[] Y = {0, 0.8415, 0.9093, 0.1411, -0.7568, -0.9589, -0.2794};

    @Test
    public void testInterpolate() {
        System.out.println("LinearInterpolation.interpolate");
        LinearInterpolation interp = new LinearInterpolation(X, Y);
        assertEquals(0.5252, interp.interpolate(2.5), 1E-4);
    }

    @Test
    public void testInterpolateAtKnots() {
        System.out.println("LinearInterpolation.interpolate at knots");
        LinearInterpolation interp = new LinearInterpolation(X, Y);
        for (int i = 0; i < X.length; i++) {
            assertEquals(Y[i], interp.interpolate(X[i]), 1E-10,
                    "At knot x=" + X[i]);
        }
    }

    @Test
    public void testExtrapolationLeft() {
        System.out.println("LinearInterpolation.extrapolate left");
        LinearInterpolation interp = new LinearInterpolation(X, Y);
        // Below x[0]: search clamps to index 0, so uses segment [0,1]
        double expected = Y[0] + ((-0.5 - X[0]) / (X[1] - X[0])) * (Y[1] - Y[0]);
        assertEquals(expected, interp.interpolate(-0.5), 1E-10);
    }

    @Test
    public void testExtrapolationRight() {
        System.out.println("LinearInterpolation.extrapolate right");
        LinearInterpolation interp = new LinearInterpolation(X, Y);
        // Above x[n-1]: search clamps to index n-2, uses last segment
        int n = X.length;
        double expected = Y[n - 2] + ((6.5 - X[n - 2]) / (X[n - 1] - X[n - 2])) * (Y[n - 1] - Y[n - 2]);
        assertEquals(expected, interp.interpolate(6.5), 1E-10);
    }

    @Test
    public void testDecreasingGrid() {
        System.out.println("LinearInterpolation.decreasing grid");
        // Reversed order — AbstractInterpolation supports monotone decreasing
        double[] xd = {6, 5, 4, 3, 2, 1, 0};
        double[] yd = {-0.2794, -0.9589, -0.7568, 0.1411, 0.9093, 0.8415, 0};
        LinearInterpolation interp = new LinearInterpolation(xd, yd);
        assertEquals(0.5252, interp.interpolate(2.5), 1E-4);
    }

    @Test
    public void testCorrelatedCallsHuntPath() {
        System.out.println("LinearInterpolation.correlated sequential calls");
        // Make many sequential calls to exercise the hunt() branch
        double[] x = new double[100];
        double[] y = new double[100];
        for (int i = 0; i < 100; i++) {
            x[i] = i;
            y[i] = Math.sin(i * 0.1);
        }
        LinearInterpolation interp = new LinearInterpolation(x, y);
        for (int i = 0; i < 99; i++) {
            double xi = i + 0.5;
            double expected = y[i] + 0.5 * (y[i + 1] - y[i]);
            assertEquals(expected, interp.interpolate(xi), 1E-10);
        }
    }

    @Test
    public void testTwoPoints() {
        System.out.println("LinearInterpolation.two points");
        LinearInterpolation interp = new LinearInterpolation(new double[]{0, 1}, new double[]{2, 4});
        assertEquals(3.0, interp.interpolate(0.5), 1E-10);
        assertEquals(2.0, interp.interpolate(0.0), 1E-10);
        assertEquals(4.0, interp.interpolate(1.0), 1E-10);
    }

    @Test
    public void testConstantFunction() {
        System.out.println("LinearInterpolation.constant function");
        double[] x = {0, 1, 2, 3};
        double[] y = {5, 5, 5, 5};
        LinearInterpolation interp = new LinearInterpolation(x, y);
        assertEquals(5.0, interp.interpolate(1.5), 1E-10);
    }

    @Test
    public void testInputValidation() {
        System.out.println("LinearInterpolation.input validation");
        assertThrows(IllegalArgumentException.class,
                () -> new LinearInterpolation(new double[]{0, 1}, new double[]{0}));
        assertThrows(IllegalArgumentException.class,
                () -> new LinearInterpolation(new double[]{0}, new double[]{0}));
    }

    @Test
    public void testToString() {
        LinearInterpolation interp = new LinearInterpolation(X, Y);
        assertNotNull(interp.toString());
        assertFalse(interp.toString().isEmpty());
    }
}