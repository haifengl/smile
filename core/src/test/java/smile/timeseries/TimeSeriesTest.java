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
    public void testAcfPrice() {
        System.out.println("ACF of price");

        double[] x = BitcoinPrice.price;
        assertEquals(1.0, TimeSeries.acf(x, 0), 1E-7);
        assertEquals(0.9937373, TimeSeries.acf(x, 1), 1E-7);
        assertEquals(0.9870269, TimeSeries.acf(x, 2), 1E-7);
        assertEquals(0.9810789, TimeSeries.acf(x, 3), 1E-7);
        assertEquals(0.9491701, TimeSeries.acf(x, 9), 1E-7);
    }

    @Test
    public void testAcfLogPrice() {
        System.out.println("ACF of log price");

        double[] x = BitcoinPrice.logPrice;
        assertEquals(1.0, TimeSeries.acf(x, 0), 1E-7);
        assertEquals(0.9969470, TimeSeries.acf(x, 1), 1E-7);
        assertEquals(0.9939463, TimeSeries.acf(x, 2), 1E-7);
        assertEquals(0.9910412, TimeSeries.acf(x, 3), 1E-7);
        assertEquals(0.9724970, TimeSeries.acf(x, 9), 1E-7);
    }

    @Test
    public void testAcfLogReturn() {
        System.out.println("ACF of log return");

        double[] x = BitcoinPrice.logReturn;
        assertEquals( 1.0, TimeSeries.acf(x, 0), 1E-8);
        assertEquals( 0.00648546, TimeSeries.acf(x, 1), 1E-8);
        assertEquals(-0.03218588, TimeSeries.acf(x, 2), 1E-8);
        assertEquals(-0.00794621, TimeSeries.acf(x, 3), 1E-8);
        assertEquals( 0.01021301, TimeSeries.acf(x, 9), 1E-8);
    }

    @Test
    public void testPacf() {
        System.out.println("PACF");

        double[] x = BitcoinPrice.logReturn;
        assertEquals( 1.0, TimeSeries.pacf(x, 0), 1E-8);
        assertEquals( 0.00648546, TimeSeries.pacf(x, 1), 1E-8);
        assertEquals(-0.03222929, TimeSeries.pacf(x, 2), 1E-8);
        assertEquals(-0.00752986, TimeSeries.pacf(x, 3), 1E-8);
        assertEquals( 0.02646461, TimeSeries.pacf(x, 4), 1E-8);
        assertEquals( 0.00713402, TimeSeries.pacf(x, 9), 1E-8);
    }
}
