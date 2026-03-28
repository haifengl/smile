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
public class ARMATest {

    public ARMATest() {
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
    public void testARMA63() throws Exception {
        System.out.println("ARMA(6, 3)");

        // The log return series, log(p_t) - log(p_t-1), is stationary.
        var bitcoin = new BitcoinPrice();
        var logPrice = bitcoin.logPrice();
        double[] x = TimeSeries.diff(logPrice, 1);
        ARMA model = ARMA.fit(x, 6, 3);
        System.out.println(model);
        assertEquals(6, model.p());
        assertEquals(3, model.q());

        assertEquals(-0.09093207, model.ar()[0], 1E-4);
        assertEquals(-0.05366566, model.ar()[1], 1E-4);
        assertEquals(-0.18934546, model.ar()[2], 1E-4);
        assertEquals( 0.03141842, model.ar()[3], 1E-4);
        assertEquals( 0.05509103, model.ar()[4], 1E-4);
        assertEquals( 0.06895746, model.ar()[5], 1E-4);

        assertEquals( 0.10075336, model.ma()[0], 1E-4);
        assertEquals( 0.01953658, model.ma()[1], 1E-4);
        assertEquals( 0.18196731, model.ma()[2], 1E-4);
        assertEquals(-0.003009289, model.intercept(), 1E-8);
        assertEquals( 0.001934966, model.variance(), 1E-8);

        assertEquals(-0.032786532, model.forecast(), 1E-8);

        double[] forecast = model.forecast(3);
        assertEquals(-0.032786532, forecast[0], 1E-8);
        assertEquals( 0.014282034, forecast[1], 1E-8);
        assertEquals( 0.028407633, forecast[2], 1E-8);
    }
}
