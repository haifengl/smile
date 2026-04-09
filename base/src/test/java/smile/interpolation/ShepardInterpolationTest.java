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
 * Tests for {@link ShepardInterpolation}, {@link ShepardInterpolation1D},
 * and {@link ShepardInterpolation2D}.
 *
 * @author Haifeng Li
 */
public class ShepardInterpolationTest {

    @Test
    public void testInterpolate() {
        System.out.println("ShepardInterpolation.interpolate");
        double[][] x = {{0, 0}, {1, 1}};
        double[] y = {0, 1};
        ShepardInterpolation instance = new ShepardInterpolation(x, y);
        double[] x1 = {0.5, 0.5};
        assertEquals(0, instance.interpolate(x[0]), 1E-7);
        assertEquals(1, instance.interpolate(x[1]), 1E-7);
        assertEquals(0.5, instance.interpolate(x1), 1E-7);
    }

    @Test
    public void testInterpolate2D() {
        System.out.println("ShepardInterpolation.interpolate 2d");
        double[] x1 = {0, 1};
        double[] x2 = {0, 1};
        double[] y = {0, 1};
        ShepardInterpolation2D instance = new ShepardInterpolation2D(x1, x2, y);
        assertEquals(0, instance.interpolate(0, 0), 1E-7);
        assertEquals(1, instance.interpolate(1, 1), 1E-7);
        assertEquals(0.5, instance.interpolate(0.5, 0.5), 1E-7);
    }

    @Test
    public void testInterpolate1D() {
        System.out.println("ShepardInterpolation1D.interpolate");
        double[] x = {0, 1, 2, 3, 4, 5, 6};
        double[] y = {0, 0.8415, 0.9093, 0.1411, -0.7568, -0.9589, -0.2794};
        ShepardInterpolation1D interp = new ShepardInterpolation1D(x, y);
        // At knots, should reproduce exactly
        for (int i = 0; i < x.length; i++) {
            assertEquals(y[i], interp.interpolate(x[i]), 1E-7, "At knot x=" + x[i]);
        }
    }

    @Test
    public void testInterpolate1DMidpoint() {
        System.out.println("ShepardInterpolation1D.midpoint");
        double[] x = {0, 2};
        double[] y = {0, 4};
        ShepardInterpolation1D interp = new ShepardInterpolation1D(x, y);
        // Symmetric: midpoint should be average by symmetry of r^-2 weights
        assertEquals(2.0, interp.interpolate(1.0), 1E-7);
    }

    @Test
    public void testInterpolate1DCustomP() {
        System.out.println("ShepardInterpolation1D.custom p");
        double[] x = {0, 1, 2};
        double[] y = {0, 1, 0};
        ShepardInterpolation1D p1 = new ShepardInterpolation1D(x, y, 1.0);
        ShepardInterpolation1D p3 = new ShepardInterpolation1D(x, y, 3.0);
        // With higher p, the interpolant is more "local" (peak near x=1 is more pronounced)
        assertTrue(p3.interpolate(1.0) >= p1.interpolate(1.0) - 1E-10);
    }

    @Test
    public void testInterpolate1DValidation() {
        System.out.println("ShepardInterpolation1D.input validation");
        assertThrows(IllegalArgumentException.class,
                () -> new ShepardInterpolation1D(new double[]{0, 1}, new double[]{0}));
        assertThrows(IllegalArgumentException.class,
                () -> new ShepardInterpolation1D(new double[]{0, 1}, new double[]{0, 1}, 0.0));
        assertThrows(IllegalArgumentException.class,
                () -> new ShepardInterpolation1D(new double[]{0, 1}, new double[]{0, 1}, -1.0));
    }

    @Test
    public void testToString1D() {
        ShepardInterpolation1D interp = new ShepardInterpolation1D(new double[]{0, 1}, new double[]{0, 1});
        assertTrue(interp.toString().contains("Shepard"));
    }

    @Test
    public void testMultiplePoints() {
        System.out.println("ShepardInterpolation.multiple scattered points");
        double[][] x = {{0,0},{1,0},{0,1},{1,1},{0.5,0.5}};
        double[] y = {0, 1, 1, 2, 1};
        ShepardInterpolation interp = new ShepardInterpolation(x, y);
        // At a known point, should return the known value
        for (int i = 0; i < x.length; i++) {
            assertEquals(y[i], interp.interpolate(x[i]), 1E-5, "At knot " + i);
        }
    }
}