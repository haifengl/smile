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
public class LaplaceInterpolationTest {

    public LaplaceInterpolationTest() {
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
     * Test of interpolate method, of class LaplaceInterpolation.
     */
    @Test
    public void testInterpolate() {
        System.out.println("interpolate");
        double[][] matrix = {{0, Double.NaN}, {1, 2}};
        double error = LaplaceInterpolation.interpolate(matrix);
        assertEquals(0, matrix[0][0], 1E-7);
        assertEquals(1, matrix[1][0], 1E-7);
        assertEquals(2, matrix[1][1], 1E-7);
        assertEquals(1, matrix[0][1], 1E-7);
        assertTrue(error < 1E-6);
    }
}