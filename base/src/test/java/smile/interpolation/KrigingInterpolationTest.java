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
 * Tests for {@link KrigingInterpolation}, {@link KrigingInterpolation1D},
 * and {@link KrigingInterpolation2D}.
 *
 * @author Haifeng Li
 */
public class KrigingInterpolationTest {

    @Test
    public void testInterpolate() {
        System.out.println("KrigingInterpolation.interpolate");
        double[][] x = {{0, 0}, {1, 1}};
        double[] y = {0, 1};
        KrigingInterpolation instance = new KrigingInterpolation(x, y);
        double[] x1 = {0.5, 0.5};
        assertEquals(0, instance.interpolate(x[0]), 1E-7);
        assertEquals(1, instance.interpolate(x[1]), 1E-7);
        assertEquals(0.5, instance.interpolate(x1), 1E-7);
    }

    @Test
    public void testInterpolate2D() {
        System.out.println("KrigingInterpolation.interpolate 2d");
        double[] x1 = {0, 1};
        double[] x2 = {0, 1};
        double[] y = {0, 1};
        KrigingInterpolation2D instance = new KrigingInterpolation2D(x1, x2, y);
        assertEquals(0, instance.interpolate(0, 0), 1E-7);
        assertEquals(1, instance.interpolate(1, 1), 1E-7);
        assertEquals(0.5, instance.interpolate(0.5, 0.5), 1E-7);
    }

    @Test
    public void testInterpolate1D() {
        System.out.println("KrigingInterpolation1D.interpolate at knots");
        double[] x = {0, 1, 2, 3, 4, 5, 6};
        double[] y = {0, 0.8415, 0.9093, 0.1411, -0.7568, -0.9589, -0.2794};
        KrigingInterpolation1D interp = new KrigingInterpolation1D(x, y);
        for (int i = 0; i < x.length; i++) {
            assertEquals(y[i], interp.interpolate(x[i]), 1E-4, "At knot x=" + x[i]);
        }
    }

    @Test
    public void testInterpolate1DMidpoint() {
        System.out.println("KrigingInterpolation1D.symmetric midpoint");
        double[] x = {0, 2};
        double[] y = {0, 4};
        KrigingInterpolation1D interp = new KrigingInterpolation1D(x, y);
        // By symmetry the midpoint estimate should be near the midpoint value
        double v = interp.interpolate(1.0);
        assertTrue(v > 0 && v < 4, "Midpoint should be between the endpoint values");
    }

    @Test
    public void testInterpolate1DCustomBeta() {
        System.out.println("KrigingInterpolation1D.custom beta");
        double[] x = {0, 1, 2, 3, 4};
        double[] y = {0, 1, 4, 9, 16};
        KrigingInterpolation1D interp = new KrigingInterpolation1D(x, y, 1.9);
        for (int i = 0; i < x.length; i++) {
            assertEquals(y[i], interp.interpolate(x[i]), 1E-3, "At knot x=" + x[i]);
        }
    }

    @Test
    public void testInterpolate1DValidation() {
        System.out.println("KrigingInterpolation1D.input validation");
        assertThrows(IllegalArgumentException.class,
                () -> new KrigingInterpolation1D(new double[]{0, 1}, new double[]{0}));
        assertThrows(IllegalArgumentException.class,
                () -> new KrigingInterpolation1D(new double[]{0, 1}, new double[]{0, 1}, 0.5)); // beta < 1
        assertThrows(IllegalArgumentException.class,
                () -> new KrigingInterpolation1D(new double[]{0, 1}, new double[]{0, 1}, 2.0)); // beta >= 2
    }

    @Test
    public void testInterpolateWrongDimension() {
        System.out.println("KrigingInterpolation.wrong dimension throws");
        double[][] x = {{0, 0}, {1, 1}};
        double[] y = {0, 1};
        KrigingInterpolation interp = new KrigingInterpolation(x, y);
        assertThrows(IllegalArgumentException.class,
                () -> interp.interpolate(0.5)); // 1D query into 2D interpolant
    }

    @Test
    public void testToString1D() {
        KrigingInterpolation1D interp = new KrigingInterpolation1D(
                new double[]{0, 1, 2}, new double[]{0, 1, 0});
        assertNotNull(interp.toString());
        assertTrue(interp.toString().contains("Kriging"));
    }
}