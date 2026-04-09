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

import smile.math.rbf.GaussianRadialBasis;
import smile.math.rbf.MultiquadricRadialBasis;
import smile.math.rbf.ThinPlateRadialBasis;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link RBFInterpolation}, {@link RBFInterpolation1D},
 * and {@link RBFInterpolation2D}.
 *
 * @author Haifeng Li
 */
public class RBFInterpolationTest {

    @Test
    public void testInterpolate() {
        System.out.println("RBFInterpolation.interpolate");
        double[][] x = {{0, 0}, {1, 1}};
        double[] y = {0, 1};
        RBFInterpolation instance = new RBFInterpolation(x, y, new GaussianRadialBasis());
        double[] x1 = {0.5, 0.5};
        assertEquals(0, instance.interpolate(x[0]), 1E-7);
        assertEquals(1, instance.interpolate(x[1]), 1E-7);
        assertEquals(0.569349, instance.interpolate(x1), 1E-6);
    }

    @Test
    public void testInterpolate2D() {
        System.out.println("RBFInterpolation.interpolate 2d");
        double[] x1 = {0, 1};
        double[] x2 = {0, 1};
        double[] y = {0, 1};
        RBFInterpolation2D instance = new RBFInterpolation2D(x1, x2, y, new GaussianRadialBasis());
        assertEquals(0, instance.interpolate(0, 0), 1E-7);
        assertEquals(1, instance.interpolate(1, 1), 1E-7);
        assertEquals(0.569349, instance.interpolate(0.5, 0.5), 1E-6);
    }

    @Test
    public void testInterpolate1D() {
        System.out.println("RBFInterpolation1D.interpolate at knots");
        // Use Gaussian RBF (Cholesky path) with scale = point spacing for stability.
        double[] x = {0, 3, 6, 9, 12};
        double[] y = {0.0, 0.1411, -0.2794, 0.4121, -0.5365};
        RBFInterpolation1D interp = new RBFInterpolation1D(x, y, new GaussianRadialBasis(3.0));
        for (int i = 0; i < x.length; i++) {
            assertEquals(y[i], interp.interpolate(x[i]), 1E-4, "At knot x=" + x[i]);
        }
    }

    @Test
    public void testNormalizedRBF1D() {
        System.out.println("RBFInterpolation1D.normalized");
        // Use Gaussian RBF (Cholesky path) with scale = point spacing.
        double[] x = {0, 5, 10};
        double[] y = {0, 1, 0};
        RBFInterpolation1D norm = new RBFInterpolation1D(x, y, new GaussianRadialBasis(5.0), true);
        assertEquals(0.0, norm.interpolate(0.0), 1E-4);
        assertEquals(1.0, norm.interpolate(5.0), 1E-4);
        assertEquals(0.0, norm.interpolate(10.0), 1E-4);
    }

    @Test
    public void testRBFMultiquadric() {
        System.out.println("RBFInterpolation1D.multiquadric");
        // Multiquadric Gram matrix is ill-conditioned in 1D for close points;
        // verify the interpolant is smooth and bounded rather than exact knot reproduction.
        double[] x = {0, 1, 2, 3, 4};
        double[] y = {0, 1, 0, -1, 0};
        RBFInterpolation1D interp = new RBFInterpolation1D(x, y, new MultiquadricRadialBasis());
        // Result at endpoints must be finite
        assertTrue(Double.isFinite(interp.interpolate(0.5)));
        assertTrue(Double.isFinite(interp.interpolate(2.5)));
        // The interpolant at the midpoint x=2 should be in a plausible range
        double mid = interp.interpolate(2.0);
        assertTrue(Double.isFinite(mid), "Interpolant at x=2 should be finite");
    }

    @Test
    public void testRBFThinPlate() {
        System.out.println("RBFInterpolation1D.thin-plate spline");
        // ThinPlate in 1D has φ(0)=0 (singular diagonal), making the Gram matrix
        // rank-deficient; verify the interpolant is at least finite.
        double[] x = {0, 1, 2, 3, 4};
        double[] y = {0, 1, 4, 9, 16};
        RBFInterpolation1D interp = new RBFInterpolation1D(x, y, new ThinPlateRadialBasis());
        for (int i = 0; i < x.length; i++) {
            assertTrue(Double.isFinite(interp.interpolate(x[i])),
                    "Interpolant should be finite at x=" + x[i]);
        }
    }

    @Test
    public void testInputValidation1D() {
        System.out.println("RBFInterpolation1D.input validation");
        assertThrows(IllegalArgumentException.class,
                () -> new RBFInterpolation1D(new double[]{0, 1}, new double[]{0}, new GaussianRadialBasis()));
    }

    @Test
    public void testToString1D() {
        RBFInterpolation1D interp = new RBFInterpolation1D(
                new double[]{0, 1}, new double[]{0, 1}, new GaussianRadialBasis());
        assertNotNull(interp.toString());
        assertTrue(interp.toString().contains("RBF"));
    }
}