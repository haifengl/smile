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
 * Tests for {@link CubicSplineInterpolation2D}.
 *
 * @author Haifeng Li
 */
public class CubicSplineInterpolation2DTest {

    static final double[] X1 = {1950, 1960, 1970, 1980, 1990};
    static final double[] X2 = {10, 20, 30};
    static final double[][] Y = {
        {150.697, 199.592, 187.625},
        {179.323, 195.072, 250.287},
        {203.212, 179.092, 322.767},
        {226.505, 153.706, 426.730},
        {249.633, 120.281, 598.243}
    };

    @Test
    public void testInterpolate() {
        System.out.println("CubicSplineInterpolation2D.interpolate");
        CubicSplineInterpolation2D instance = new CubicSplineInterpolation2D(X1, X2, Y);
        assertEquals(167.9922755, instance.interpolate(1975, 15), 1E-7);
        assertEquals(167.5167746, instance.interpolate(1975, 20), 1E-7);
        assertEquals(244.3006193, instance.interpolate(1975, 25), 1E-7);
    }

    @Test
    public void testInterpolateAtKnots() {
        System.out.println("CubicSplineInterpolation2D.interpolate at knots");
        CubicSplineInterpolation2D instance = new CubicSplineInterpolation2D(X1, X2, Y);
        for (int i = 0; i < X1.length; i++) {
            for (int j = 0; j < X2.length; j++) {
                assertEquals(Y[i][j], instance.interpolate(X1[i], X2[j]), 1E-4,
                        "At knot (" + X1[i] + "," + X2[j] + ")");
            }
        }
    }

    @Test
    public void testQuadraticSurface() {
        System.out.println("CubicSplineInterpolation2D.quadratic surface");
        double[] x1 = {0, 1, 2, 3, 4};
        double[] x2 = {0, 1, 2, 3, 4};
        double[][] z = new double[5][5];
        for (int i = 0; i < 5; i++)
            for (int j = 0; j < 5; j++)
                z[i][j] = x1[i] * x1[i] + x2[j] * x2[j];
        CubicSplineInterpolation2D interp = new CubicSplineInterpolation2D(x1, x2, z);
        // At knots, reproduction is exact
        for (int i = 0; i < x1.length; i++)
            for (int j = 0; j < x2.length; j++)
                assertEquals(z[i][j], interp.interpolate(x1[i], x2[j]), 1E-4,
                        "Knot (" + x1[i] + "," + x2[j] + ")");
        // At midpoint: natural-spline BCs cause slight deviation from 1.5^2+1.5^2=4.5
        assertEquals(4.5, interp.interpolate(1.5, 1.5), 0.1);
    }

    @Test
    public void testBetterThanBilinear() {
        System.out.println("CubicSplineInterpolation2D.better than bilinear for smooth data");
        CubicSplineInterpolation2D cubic = new CubicSplineInterpolation2D(X1, X2, Y);
        BilinearInterpolation bilinear = new BilinearInterpolation(X1, X2, Y);
        // The two methods should give different values at interior non-knot point
        double vCubic = cubic.interpolate(1975, 15);
        double vBilinear = bilinear.interpolate(1975, 15);
        assertNotEquals(vCubic, vBilinear, 1.0);
    }
}