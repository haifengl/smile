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
 * along with Smile. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.timeseries;

import smile.datasets.BitcoinPrice;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 *
 * @author Haifeng Li
 */
public class TimeSeriesTest {
    double[] price;
    double[] logPrice;
    double[] logPriceDiff;
    public TimeSeriesTest() throws Exception {
        var bitcoin = new BitcoinPrice();
        price = bitcoin.price();
        logPrice = bitcoin.logPrice();
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
    public void testAcfPrice() {
        System.out.println("ACF of price");
        assertEquals(1.0, TimeSeries.acf(price, 0), 1E-7);
        assertEquals(0.9937373, TimeSeries.acf(price, 1), 1E-7);
        assertEquals(0.9870269, TimeSeries.acf(price, 2), 1E-7);
        assertEquals(0.9810789, TimeSeries.acf(price, 3), 1E-7);
        assertEquals(0.9491701, TimeSeries.acf(price, 9), 1E-7);
    }

    @Test
    public void testAcfLogPrice() {
        System.out.println("ACF of log price");
        assertEquals(1.0, TimeSeries.acf(logPrice, 0), 1E-7);
        assertEquals(0.9969470, TimeSeries.acf(logPrice, 1), 1E-7);
        assertEquals(0.9939463, TimeSeries.acf(logPrice, 2), 1E-7);
        assertEquals(0.9910412, TimeSeries.acf(logPrice, 3), 1E-7);
        assertEquals(0.9724970, TimeSeries.acf(logPrice, 9), 1E-7);
    }

    @Test
    public void testAcfLogReturn() {
        System.out.println("ACF of log return");
        assertEquals( 1.0, TimeSeries.acf(logPriceDiff, 0), 1E-8);
        assertEquals( 0.00648546, TimeSeries.acf(logPriceDiff, 1), 1E-8);
        assertEquals(-0.03218588, TimeSeries.acf(logPriceDiff, 2), 1E-8);
        assertEquals(-0.00794621, TimeSeries.acf(logPriceDiff, 3), 1E-8);
        assertEquals( 0.01021301, TimeSeries.acf(logPriceDiff, 9), 1E-8);
    }

    @Test
    public void testPacf() {
        System.out.println("PACF");
        assertEquals( 1.0, TimeSeries.pacf(logPriceDiff, 0), 1E-8);
        assertEquals( 0.00648546, TimeSeries.pacf(logPriceDiff, 1), 1E-8);
        assertEquals(-0.03222929, TimeSeries.pacf(logPriceDiff, 2), 1E-8);
        assertEquals(-0.00752986, TimeSeries.pacf(logPriceDiff, 3), 1E-8);
        assertEquals( 0.02646461, TimeSeries.pacf(logPriceDiff, 4), 1E-8);
        assertEquals( 0.00713402, TimeSeries.pacf(logPriceDiff, 9), 1E-8);
    }
}
