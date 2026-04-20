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
public class TimeSeriesTest {
    // Simple deterministic series for manual calculation
    private static final double[] SERIES = {1.0, 2.0, 4.0, 8.0, 16.0};
    // Arithmetic series for cov tests
    private static final double[] ARITH = {1.0, 2.0, 3.0, 4.0, 5.0};

    private static double[] price;
    private static double[] logPrice;
    private static double[] logPriceDiff;

    public TimeSeriesTest() throws Exception {
    }

    @BeforeAll
    public static void setUpClass() throws Exception {
        var bitcoin = new BitcoinPrice();
        price = bitcoin.price();
        logPrice = bitcoin.logPrice();
        // The log return series, log(p_t) - log(p_t-1), is stationary.
        logPriceDiff = TimeSeries.diff(logPrice, 1);
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
    public void givenArithmeticSeries_whenCovLag0_thenReturnsSumOfSquaredDeviations() {
        // mu=3, variance = (1-3)^2+(2-3)^2+0+(4-3)^2+(5-3)^2 = 4+1+0+1+4 = 10
        double result = TimeSeries.cov(ARITH, 0);
        assertEquals(10.0, result, 1E-10);
    }

    @Test
    public void givenArithmeticSeries_whenCovLag1_thenReturnsSumOfCrossProducts() {
        // cov(lag=1) = sum_{i=1}^{4} (x[i]-3)(x[i-1]-3)
        // = (2-3)(1-3) + (3-3)(2-3) + (4-3)(3-3) + (5-3)(4-3)
        // = (-1)(-2) + 0*(-1) + 1*0 + 2*1 = 2+0+0+2 = 4
        double result = TimeSeries.cov(ARITH, 1);
        assertEquals(4.0, result, 1E-10);
    }

    @Test
    public void givenArithmeticSeries_whenCovLag2_thenReturnsSumOfCrossProducts() {
        // cov(lag=2) = sum_{i=2}^{4} (x[i]-3)(x[i-2]-3)
        // = (3-3)(1-3) + (4-3)(2-3) + (5-3)(3-3)
        // = 0*(-2) + 1*(-1) + 2*0 = 0-1+0 = -1
        double result = TimeSeries.cov(ARITH, 2);
        assertEquals(-1.0, result, 1E-10);
    }

    @Test
    public void givenSeries_whenCovLagEqualsTsLengthMinus1_thenReturnsOneTerm() {
        // lag=4: only i=4 contributes: (5-3)(1-3) = 2*(-2) = -4
        double result = TimeSeries.cov(ARITH, 4);
        assertEquals(-4.0, result, 1E-10);
    }

    @Test
    public void givenSeries_whenCovInvalidLag_thenThrows() {
        assertThrows(IllegalArgumentException.class, () -> TimeSeries.cov(ARITH, ARITH.length));
    }

    @Test
    public void givenGeometricSeries_whenDiffLag2SingleOrder_thenReturnsLag2Differences() {
        // {1,2,4,8,16}: diff(lag=2) -> y[i] = x[i+2]-x[i]
        // y = {4-1, 8-2, 16-4} = {3, 6, 12}
        double[] result = TimeSeries.diff(SERIES, 2);
        assertEquals(3, result.length);
        assertEquals(3.0,  result[0], 1E-10);
        assertEquals(6.0,  result[1], 1E-10);
        assertEquals(12.0, result[2], 1E-10);
    }

    @Test
    public void givenGeometricSeries_whenDiffLag1TwoOrders_thenReturnsSecondDifferences() {
        // First pass (d=0, lag=1): {2-1, 4-2, 8-4, 16-8} = {1, 2, 4, 8}
        // Second pass (d=1, lag=1): {2-1, 4-2, 8-4} = {1, 2, 4}
        double[][] result = TimeSeries.diff(SERIES, 1, 2);
        assertEquals(2, result.length);
        assertArrayEquals(new double[]{1.0, 2.0, 4.0, 8.0}, result[0], 1E-10);
        assertArrayEquals(new double[]{1.0, 2.0, 4.0},       result[1], 1E-10);
    }

    @Test
    public void givenGeometricSeries_whenDiffLag2TwoOrders_thenReturnsTwoRoundsOfLag2Diffs() {
        // Series has length 5; lag*differences=4 < 5, so valid.
        // d=0 (lag=2): {4-1, 8-2, 16-4} = {3, 6, 12} (length 3)
        // d=1 (lag=2): apply lag-2 diff to {3,6,12} -> {12-3} = {9} (length 1)
        double[][] result = TimeSeries.diff(SERIES, 2, 2);
        assertEquals(2, result.length);
        assertArrayEquals(new double[]{3.0, 6.0, 12.0}, result[0], 1E-10);
        assertArrayEquals(new double[]{9.0},             result[1], 1E-10);
    }

    @Test
    public void givenSeries_whenDiffSingleOrderLag1_thenMatchesDiffConvenienceOverload() {
        double[] convenience = TimeSeries.diff(SERIES, 1);
        double[][] full = TimeSeries.diff(SERIES, 1, 1);
        assertArrayEquals(convenience, full[0], 1E-10);
    }

    @Test
    public void givenSeries_whenDiffLagTimesOrderEqualsLength_thenThrows() {
        // lag=1, differences=5, length=5 -> 1*5 >= 5 -> illegal
        assertThrows(IllegalArgumentException.class, () -> TimeSeries.diff(ARITH, 1, 5));
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

    @Test
    public void givenNegativeLag_whenAcfCalled_thenUseAbsoluteLag() {
        double acfLag3 = TimeSeries.acf(logPriceDiff, 3);
        double acfNegativeLag3 = TimeSeries.acf(logPriceDiff, -3);
        assertEquals(acfLag3, acfNegativeLag3, 1E-12);
    }

    @Test
    public void givenInvalidDiffArguments_whenDiffCalled_thenThrowIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> TimeSeries.diff(logPrice, 0));
        assertThrows(IllegalArgumentException.class, () -> TimeSeries.diff(logPrice, 1, 0));
        assertThrows(IllegalArgumentException.class, () -> TimeSeries.diff(logPrice, logPrice.length, 1));
    }

    @Test
    public void givenLagOutOfRange_whenAcfPacfCovCalled_thenThrowIllegalArgumentException() {
        int lag = logPriceDiff.length;
        assertThrows(IllegalArgumentException.class, () -> TimeSeries.acf(logPriceDiff, lag));
        assertThrows(IllegalArgumentException.class, () -> TimeSeries.pacf(logPriceDiff, lag));
        assertThrows(IllegalArgumentException.class, () -> TimeSeries.cov(logPriceDiff, lag));
    }
}
