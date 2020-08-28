/*******************************************************************************
 * Copyright (c) 2010-2020 Haifeng Li. All rights reserved.
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
 ******************************************************************************/

package smile.timeseries;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import smile.data.BitcoinPrice;

import java.util.Arrays;

/**
 *
 * @author Haifeng Li
 */
public class ARTest {

    public ARTest() {
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

    @Test
    public void testAR6OLS() {
        System.out.println("AR(6).ols");

        double[] x = BitcoinPrice.logReturn;
        AR ar = AR.ols(x, 6);
        System.out.println(ar);
        assertEquals( 0.0029, ar.w[0], 1E-4);
        assertEquals(-0.0359, ar.w[1], 1E-4);
        assertEquals(-0.0051, ar.w[2], 1E-4);
        assertEquals( 0.0279, ar.w[3], 1E-4);
        assertEquals( 0.0570, ar.w[4], 1E-4);
        assertEquals( 0.0742, ar.w[5], 1E-4);
        assertEquals(-0.00210066, ar.b, 1E-8);
        assertEquals( 0.00199942, ar.variance, 1E-8);
    }

    @Test
    public void testAR6YW() {
        System.out.println("AR(6).yw");

        double[] x = BitcoinPrice.logReturn;
        AR ar = AR.fit(x, 6);
        System.out.println(ar);
        assertEquals( 0.0011, ar.w[0], 1E-4);
        assertEquals(-0.0328, ar.w[1], 1E-4);
        assertEquals(-0.0055, ar.w[2], 1E-4);
        assertEquals( 0.0283, ar.w[3], 1E-4);
        assertEquals( 0.0557, ar.w[4], 1E-4);
        assertEquals( 0.0723, ar.w[5], 1E-4);
        assertEquals(-0.002224494, ar.b, 1E-8);
        assertEquals( 3.505072140, ar.RSS, 1E-8);
        assertEquals( 0.001999471, ar.variance, 1E-8);
    }
}
