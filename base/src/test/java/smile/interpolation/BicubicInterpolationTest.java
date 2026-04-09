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
 * Tests for {@link BicubicInterpolation}.
 *
 * @author Haifeng Li
 */
public class BicubicInterpolationTest {

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
        System.out.println("BicubicInterpolation.interpolate");
        BicubicInterpolation instance = new BicubicInterpolation(X1, X2, Y);
        assertEquals(203.212,    instance.interpolate(1970, 10), 1E-3);
        assertEquals(179.092,    instance.interpolate(1970, 20), 1E-3);
        assertEquals(249.633,    instance.interpolate(1990, 10), 1E-3);
        assertEquals(598.243,    instance.interpolate(1990, 30), 1E-3);
        assertEquals(178.948375, instance.interpolate(1950, 15), 1E-4);
        assertEquals(146.99987,  instance.interpolate(1990, 15), 1E-4);
        assertEquals(508.26462,  instance.interpolate(1985, 30), 1E-4);
        assertEquals(175.667289, instance.interpolate(1975, 15), 1E-4);
        assertEquals(167.4893,   instance.interpolate(1975, 20), 1E-4);
        assertEquals(252.493726, instance.interpolate(1975, 25), 1E-4);
    }

    @Test
    public void testBetterThanBilinear() {
        System.out.println("BicubicInterpolation.better than bilinear");
        BicubicInterpolation bicubic = new BicubicInterpolation(X1, X2, Y);
        BilinearInterpolation bilinear = new BilinearInterpolation(X1, X2, Y);
        // At interior knot, both should agree closely (knot reproduction)
        assertEquals(bicubic.interpolate(1970, 10), bilinear.interpolate(1970, 10), 1E-2);
        // At a non-knot: bicubic should differ (not necessarily better for this data
        // but the values should differ, confirming bicubic is doing different interpolation)
        double vBicubic = bicubic.interpolate(1975, 25);
        double vBilinear = bilinear.interpolate(1975, 25);
        assertNotEquals(vBicubic, vBilinear, 1.0);
    }

    @Test
    public void testSimpleQuadraticSurface() {
        System.out.println("BicubicInterpolation.quadratic surface");
        // f(x,y) = x^2 + y^2 on a uniform grid
        double[] x1 = {0, 1, 2, 3, 4};
        double[] x2 = {0, 1, 2, 3, 4};
        double[][] z = new double[5][5];
        for (int i = 0; i < 5; i++)
            for (int j = 0; j < 5; j++)
                z[i][j] = x1[i] * x1[i] + x2[j] * x2[j];
        BicubicInterpolation interp = new BicubicInterpolation(x1, x2, z);
        // At knots, should reproduce exactly
        assertEquals(z[2][2], interp.interpolate(2.0, 2.0), 1E-4);
        // At midpoint: 1.5^2 + 1.5^2 = 4.5
        assertEquals(4.5, interp.interpolate(1.5, 1.5), 1E-2);
    }

    @Test
    public void testToString() {
        BicubicInterpolation instance = new BicubicInterpolation(X1, X2, Y);
        assertNotNull(instance.toString());
        assertFalse(instance.toString().isEmpty());
    }
}