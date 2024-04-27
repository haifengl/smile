/*
 * Copyright (c) 2010-2021 Haifeng Li. All rights reserved.
 *
 * Smile is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Smile is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Smile.  If not, see <https://www.gnu.org/licenses/>.
 */

package smile.timeseries;

import smile.test.data.BitcoinPrice;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 *
 * @author Haifeng Li
 */
public class TimeSeriesTest {

    public TimeSeriesTest() {
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

        // The log return series, log(p_t) - log(p_t-1), is stationary.
        double[] x = TimeSeries.diff(BitcoinPrice.logPrice, 1);
        assertEquals( 1.0, TimeSeries.acf(x, 0), 1E-8);
        assertEquals( 0.00648546, TimeSeries.acf(x, 1), 1E-8);
        assertEquals(-0.03218588, TimeSeries.acf(x, 2), 1E-8);
        assertEquals(-0.00794621, TimeSeries.acf(x, 3), 1E-8);
        assertEquals( 0.01021301, TimeSeries.acf(x, 9), 1E-8);
    }

    @Test
    public void testPacf() {
        System.out.println("PACF");

        // The log return series, log(p_t) - log(p_t-1), is stationary.
        double[] x = TimeSeries.diff(BitcoinPrice.logPrice, 1);
        assertEquals( 1.0, TimeSeries.pacf(x, 0), 1E-8);
        assertEquals( 0.00648546, TimeSeries.pacf(x, 1), 1E-8);
        assertEquals(-0.03222929, TimeSeries.pacf(x, 2), 1E-8);
        assertEquals(-0.00752986, TimeSeries.pacf(x, 3), 1E-8);
        assertEquals( 0.02646461, TimeSeries.pacf(x, 4), 1E-8);
        assertEquals( 0.00713402, TimeSeries.pacf(x, 9), 1E-8);
    }
}
