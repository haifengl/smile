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
public class ShepardInterpolationTest {

    public ShepardInterpolationTest() {
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
     * Test of interpolate method, of class ShepardInterpolation.
     */
    @Test
    public void testInterpolate() {
        System.out.println("interpolate");
        double[][] x = {{0, 0}, {1, 1}};
        double[] y = {0, 1};
        ShepardInterpolation instance = new ShepardInterpolation(x, y);
        double[] x1 = {0.5, 0.5};
        assertEquals(0, instance.interpolate(x[0]), 1E-7);
        assertEquals(1, instance.interpolate(x[1]), 1E-7);
        assertEquals(0.5, instance.interpolate(x1), 1E-7);
    }
}