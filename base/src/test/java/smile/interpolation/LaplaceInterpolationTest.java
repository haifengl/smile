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
 * Tests for {@link LaplaceInterpolation}.
 *
 * @author Haifeng Li
 */
public class LaplaceInterpolationTest {

    @Test
    public void testInterpolate() {
        System.out.println("LaplaceInterpolation.interpolate");
        double[][] matrix = {{0, Double.NaN}, {1, 2}};
        double error = LaplaceInterpolation.interpolate(matrix);
        assertEquals(0, matrix[0][0], 1E-7);
        assertEquals(1, matrix[1][0], 1E-7);
        assertEquals(2, matrix[1][1], 1E-7);
        assertEquals(1, matrix[0][1], 1E-7);
        assertTrue(error < 1E-6);
    }

    @Test
    public void testInterpolateCentrePoint() {
        System.out.println("LaplaceInterpolation.centre missing");
        // Known values on all borders, missing in the centre
        double[][] m = {
            {0.0, 1.0, 2.0},
            {1.0, Double.NaN, 3.0},
            {2.0, 3.0, 4.0}
        };
        LaplaceInterpolation.interpolate(m);
        assertTrue(Double.isFinite(m[1][1]));
        // The Laplace solution is the average of the four direct neighbours: (1+3+1+3)/4 = 2
        assertEquals(2.0, m[1][1], 0.05);
    }

    @Test
    public void testNoMissingValues() {
        System.out.println("LaplaceInterpolation.no missing values");
        // When there are no NaN values, the solver should leave all values unchanged.
        double[][] m = {{1.0, 2.0}, {3.0, 4.0}};
        LaplaceInterpolation.interpolate(m);
        assertEquals(1.0, m[0][0], 1E-10);
        assertEquals(2.0, m[0][1], 1E-10);
        assertEquals(3.0, m[1][0], 1E-10);
        assertEquals(4.0, m[1][1], 1E-10);
    }

    @Test
    public void testAllMissingExceptCorners() {
        System.out.println("LaplaceInterpolation.only corners known");
        double v = 5.0;
        double[][] m = {
            {v,          Double.NaN, v},
            {Double.NaN, Double.NaN, Double.NaN},
            {v,          Double.NaN, v}
        };
        LaplaceInterpolation.interpolate(m, 1E-6, 1000);
        // All interpolated values must be finite
        for (double[] row : m) {
            for (double x : row) {
                assertTrue(Double.isFinite(x), "Should be finite after interpolation");
            }
        }
        // With uniform corner values v, all entries should converge to v
        for (double[] row : m) {
            for (double x : row) {
                assertEquals(v, x, 1.0, "Value should be near " + v);
            }
        }
    }

    @Test
    public void testConvergenceTolerance() {
        System.out.println("LaplaceInterpolation.custom tolerance");
        double[][] matrix = {{0, Double.NaN}, {1, 2}};
        double err = LaplaceInterpolation.interpolate(matrix, 1E-10);
        assertTrue(err < 1E-6);
    }

    @Test
    public void testLinearGradient() {
        System.out.println("LaplaceInterpolation.linear gradient");
        // Linear function f(i,j) = i + j is harmonic; missing values should be recovered exactly
        int n = 5;
        double[][] m = new double[n][n];
        for (int i = 0; i < n; i++)
            for (int j = 0; j < n; j++)
                m[i][j] = i + j;
        // Blank out the interior
        for (int i = 1; i < n - 1; i++)
            for (int j = 1; j < n - 1; j++)
                m[i][j] = Double.NaN;
        LaplaceInterpolation.interpolate(m);
        for (int i = 1; i < n - 1; i++)
            for (int j = 1; j < n - 1; j++)
                assertEquals(i + j, m[i][j], 0.01,
                        "Linear function should be recovered at (" + i + "," + j + ")");
    }
}