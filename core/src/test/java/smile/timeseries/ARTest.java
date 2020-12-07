/*
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
 */

package smile.timeseries;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import smile.data.BitcoinPrice;

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
        AR model = AR.ols(x, 6);
        System.out.println(model);
        assertEquals(6, model.p());
        assertEquals( 0.0029, model.ar()[0], 1E-4);
        assertEquals(-0.0359, model.ar()[1], 1E-4);
        assertEquals(-0.0051, model.ar()[2], 1E-4);
        assertEquals( 0.0279, model.ar()[3], 1E-4);
        assertEquals( 0.0570, model.ar()[4], 1E-4);
        assertEquals( 0.0742, model.ar()[5], 1E-4);
        assertEquals(-0.00210066, model.intercept(), 1E-8);
        assertEquals( 0.00199942, model.variance(), 1E-8);

        assertEquals( -0.09426902, model.residuals()[0], 1E-8);
        assertEquals(  0.04130677, model.residuals()[1], 1E-8);
        assertEquals( -0.09436017, model.residuals()[2], 1E-8);
        assertEquals(  0.07036025, model.residuals()[3], 1E-8);
        assertEquals(  0.04814890, model.residuals()[1751], 1E-8);
        assertEquals( -0.05724721, model.residuals()[1752], 1E-8);

        assertEquals(-0.007884497, model.forecast(), 1E-8);

        double[] forecast = model.forecast(3);
        assertEquals(-0.007884497, forecast[0], 1E-8);
        assertEquals( 0.016664739, forecast[1], 1E-8);
        assertEquals( 0.017413334, forecast[2], 1E-8);
    }

    @Test
    public void testAR6YW() {
        System.out.println("AR(6).yw");

        double[] x = BitcoinPrice.logReturn;
        AR model = AR.fit(x, 6);
        System.out.println(model);
        assertEquals(6, model.p());
        assertEquals( 0.0011, model.ar()[0], 1E-4);
        assertEquals(-0.0328, model.ar()[1], 1E-4);
        assertEquals(-0.0055, model.ar()[2], 1E-4);
        assertEquals( 0.0283, model.ar()[3], 1E-4);
        assertEquals( 0.0557, model.ar()[4], 1E-4);
        assertEquals( 0.0723, model.ar()[5], 1E-4);
        assertEquals(-0.002224494, model.intercept(), 1E-8);
        assertEquals( 3.505072140, model.RSS(), 1E-8);
        assertEquals( 0.002011217, model.variance(), 1E-8);
    }
}
