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
 * Tests for {@link BilinearInterpolation}.
 *
 * @author Haifeng Li
 */
public class BilinearInterpolationTest {

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
        System.out.println("BilinearInterpolation.interpolate");
        BilinearInterpolation instance = new BilinearInterpolation(X1, X2, Y);
        assertEquals(190.6287, instance.interpolate(1975, 15), 1E-4);
    }

    @Test
    public void testInterpolateAtKnots() {
        System.out.println("BilinearInterpolation.interpolate at knots");
        BilinearInterpolation instance = new BilinearInterpolation(X1, X2, Y);
        for (int i = 0; i < X1.length - 1; i++) {
            for (int j = 0; j < X2.length - 1; j++) {
                assertEquals(Y[i][j], instance.interpolate(X1[i], X2[j]), 1E-7,
                        "At knot (" + X1[i] + "," + X2[j] + ")");
            }
        }
    }

    @Test
    public void testMidpointBilinear() {
        System.out.println("BilinearInterpolation.midpoint is average of corners");
        // For a bilinear function, the centre of a cell is the average of its 4 corners.
        // Cell [1960-1970] x [10-20]:
        //   corners: Y[1][0]=179.323, Y[1][1]=195.072, Y[2][0]=203.212, Y[2][1]=179.092
        // t=0.5 (1965 in [1960,1970]), u=0.5 (15 in [10,20])
        // bilinear = (1-t)(1-u)*Y[1][0] + t(1-u)*Y[2][0] + (1-t)u*Y[1][1] + t*u*Y[2][1]
        double expected = 0.25 * (179.323 + 195.072 + 203.212 + 179.092);
        BilinearInterpolation instance = new BilinearInterpolation(X1, X2, Y);
        assertEquals(expected, instance.interpolate(1965, 15), 1E-4);
    }

    @Test
    public void testSimpleGrid() {
        System.out.println("BilinearInterpolation.simple 2x2 grid");
        // f(x,y) = x*y, grid [0,1]x[0,1]
        double[] x1 = {0, 1};
        double[] x2 = {0, 1};
        double[][] z = {{0, 0}, {0, 1}};
        BilinearInterpolation interp = new BilinearInterpolation(x1, x2, z);
        assertEquals(0.25, interp.interpolate(0.5, 0.5), 1E-10);
        assertEquals(0.0,  interp.interpolate(0.0, 0.5), 1E-10);
        assertEquals(0.5,  interp.interpolate(1.0, 0.5), 1E-10);
    }

    @Test
    public void testInputValidation() {
        System.out.println("BilinearInterpolation.input validation");
        assertThrows(IllegalArgumentException.class,
                () -> new BilinearInterpolation(new double[]{0, 1, 2}, X2, Y)); // x1 length mismatch
        assertThrows(IllegalArgumentException.class,
                () -> new BilinearInterpolation(X1, new double[]{10, 20, 30, 40}, Y)); // x2 length mismatch
    }

    @Test
    public void testToString() {
        BilinearInterpolation instance = new BilinearInterpolation(X1, X2, Y);
        assertNotNull(instance.toString());
        assertFalse(instance.toString().isEmpty());
    }
}