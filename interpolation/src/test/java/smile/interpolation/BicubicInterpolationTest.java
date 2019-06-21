/*******************************************************************************
 * Copyright (c) 2010-2019 Haifeng Li
 *
 * Smile is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of
 * the License, or (at your option) any later version.
 *
 * Smile is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Smile.  If not, see <https://www.gnu.org/licenses/>.
 *******************************************************************************/

package smile.interpolation;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author Haifeng Li
 */
public class BicubicInterpolationTest {

    public BicubicInterpolationTest() {
    }

    @BeforeClass
    public static void setUpClass() throws Exception {
    }

    @AfterClass
    public static void tearDownClass() throws Exception {
    }

    @Before
    public void setUp() {
    }

    @After
    public void tearDown() {
    }

    /**
     * Test of interpolate method, of class BicubicInterpolation.
     */
    @Test
    public void testInterpolate() {
        System.out.println("interpolate");
        double[] x1 = {1950, 1960, 1970, 1980, 1990};
        double[] x2 = {10, 20, 30};
        double[][] y = {
            {150.697, 199.592, 187.625},
            {179.323, 195.072, 250.287},
            {203.212, 179.092, 322.767},
            {226.505, 153.706, 426.730},
            {249.633, 120.281, 598.243}
        };

        BicubicInterpolation instance = new BicubicInterpolation(x1, x2, y);
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
}