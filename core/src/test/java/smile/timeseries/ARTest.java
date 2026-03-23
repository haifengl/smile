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
package smile.timeseries;

import smile.datasets.BitcoinPrice;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 *
 * @author Haifeng Li
 */
public class ARTest {
    double[] logPriceDiff;
    public ARTest() throws Exception {
        var bitcoin = new BitcoinPrice();
        var logPrice = bitcoin.logPrice();
        // The log return series, log(p_t) - log(p_t-1), is stationary.
        logPriceDiff = TimeSeries.diff(logPrice, 1);
    }

    @BeforeAll
    public static void setUpClass() throws Exception {
    }

    @AfterAll
    public static void tearDownClass() throws Exception {
    }

    @BeforeEach
    public void setUp() {
    }

    @AfterEach
    public void tearDown() {
    }

    @Test
    public void testAR6OLS() {
        System.out.println("AR(6).ols");

        AR model = AR.ols(logPriceDiff, 6);
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

        AR model = AR.fit(logPriceDiff, 6);
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
