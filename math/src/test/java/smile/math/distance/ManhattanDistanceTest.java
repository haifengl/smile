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

package smile.math.distance;

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
public class ManhattanDistanceTest {

    public ManhattanDistanceTest() {
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
     * Test of distance method, of class ManhattanDistance.
     */
    @Test
    public void testDistanceInt() {
        System.out.println("distance");
        int[] x = {1, 2, 3, 4};
        int[] y = {4, 3, 2, 1};
        assertEquals(8, new ManhattanDistance().d(x, y), 1E-6);
    }

    /**
     * Test of distance method, of class ManhattanDistance.
     */
    @Test
    public void testDistanceDouble() {
        System.out.println("distance");
        double[] x = {-2.1968219, -0.9559913, -0.0431738,  1.0567679,  0.3853515};
        double[] y = {-1.7781325, -0.6659839,  0.9526148, -0.9460919, -0.3925300};
        assertEquals(4.485227, new ManhattanDistance().d(x, y), 1E-6);
    }
}