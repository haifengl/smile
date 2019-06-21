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

package smile.stat;

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
public class StatTest {

    public StatTest() {
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
     * Test of GoodTuring method, of class Stat.
     */
    @Test
    public void testGoodTuring() {
        System.out.println("GoodTuring");
        int[] r = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 12};
        int[] Nr = {120, 40, 24, 13, 15, 5, 11, 2, 2, 1, 3};
        double p0 = 0.2047782;
        double[] p = {
                0.0009267, 0.0024393, 0.0040945, 0.0058063, 0.0075464,
                0.0093026, 0.0110689, 0.0128418, 0.0146194, 0.0164005, 0.0199696};

        double[] result = new double[r.length];
        assertEquals(p0, Stat.GoodTuring(r, Nr, result), 1E-7);
        for (int i = 0; i < r.length; i++) {
            assertEquals(p[i], result[i], 1E-7);
        }
    }
}
