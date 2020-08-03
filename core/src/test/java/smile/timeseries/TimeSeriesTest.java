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

/**
 *
 * @author Haifeng Li
 */
public class TimeSeriesTest {

    public TimeSeriesTest() {
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
    public void testAcf() {
        System.out.println("ACF of first-differencing");

        double[] x = BitcoinPrice.timeseries;
        assertEquals( 1.0, TimeSeries.acf(x, 0), 1E-8);
        assertEquals( 0.09288779, TimeSeries.acf(x, 1), 1E-8);
        assertEquals(-0.05545845, TimeSeries.acf(x, 2), 1E-8);
        assertEquals(-0.02800631, TimeSeries.acf(x, 3), 1E-8);
        assertEquals( 0.09273750, TimeSeries.acf(x, 9), 1E-8);
    }

    @Test
    public void testAcf2() {
        System.out.println("ACF of twice-differencing");

        double[] x = TimeSeries.diff(BitcoinPrice.data.doubleVector("Close").toDoubleArray(), 1, 2)[1];
        assertEquals( 1.0, TimeSeries.acf(x, 0), 1E-8);
        assertEquals(-0.41881183, TimeSeries.acf(x, 1), 1E-8);
        assertEquals(-0.09565346, TimeSeries.acf(x, 2), 1E-8);
        assertEquals( 0.03754026, TimeSeries.acf(x, 3), 1E-8);
        assertEquals(-0.06052846, TimeSeries.acf(x, 9), 1E-8);
    }

    @Test
    public void testPacf() {
        System.out.println("PACF");

        double[] x = BitcoinPrice.timeseries;
        assertEquals( 1.0, TimeSeries.pacf(x, 0), 1E-8);
        assertEquals( 0.09288779, TimeSeries.pacf(x, 1), 1E-8);
        assertEquals(-0.06464436, TimeSeries.pacf(x, 2), 1E-8);
        assertEquals(-0.01673088, TimeSeries.pacf(x, 3), 1E-8);
        assertEquals(-0.07145401, TimeSeries.pacf(x, 4), 1E-8);
        assertEquals( 0.09639064, TimeSeries.pacf(x, 9), 1E-8);
    }
}
