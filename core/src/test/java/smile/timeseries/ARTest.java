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
    private static double[] logPriceDiff;
    public ARTest() throws Exception {
    }

    @BeforeAll
    public static void setUpClass() throws Exception {
        var bitcoin = new BitcoinPrice();
        var logPrice = bitcoin.logPrice();
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
    public void givenAR6OLS_whenFitWithoutStderr_thenTtestIsNull() {
        AR model = AR.ols(logPriceDiff, 6, false);
        assertNull(model.ttest(), "ttest should be null when stderr=false");
    }

    @Test
    public void givenAR6OLS_whenFitWithoutStderr_thenCoefficientsMatchStderrVersion() {
        AR withSe    = AR.ols(logPriceDiff, 6, true);
        AR withoutSe = AR.ols(logPriceDiff, 6, false);

        assertArrayEquals(withSe.ar(), withoutSe.ar(), 1E-12);
        assertEquals(withSe.intercept(), withoutSe.intercept(), 1E-12);
    }

    @Test
    public void givenAR6OLS_whenFit_thenDfEqualsNMinusP() {
        int p = 6;
        AR model = AR.ols(logPriceDiff, p);
        assertEquals(logPriceDiff.length - p, model.df());
    }

    @Test
    public void givenAR6YW_whenFit_thenDfEqualsNMinusP() {
        int p = 6;
        AR model = AR.fit(logPriceDiff, p);
        assertEquals(logPriceDiff.length - p, model.df());
    }

    @Test
    public void givenAR6OLS_whenFit_thenFittedAndResidualLengthEqualsNMinusP() {
        int p = 6;
        AR model = AR.ols(logPriceDiff, p);
        int expected = logPriceDiff.length - p;
        assertEquals(expected, model.fittedValues().length);
        assertEquals(expected, model.residuals().length);
    }

    @Test
    public void givenAR6YW_whenFit_thenFittedAndResidualLengthEqualsNMinusP() {
        int p = 6;
        AR model = AR.fit(logPriceDiff, p);
        int expected = logPriceDiff.length - p;
        assertEquals(expected, model.fittedValues().length);
        assertEquals(expected, model.residuals().length);
    }

    @Test
    public void givenAR6OLS_whenFit_thenR2andAdjustedR2AreFinite() {
        AR model = AR.ols(logPriceDiff, 6);
        assertTrue(Double.isFinite(model.R2()),
                "R2 should be finite for real-world data");
        assertTrue(Double.isFinite(model.adjustedR2()),
                "adjustedR2 should be finite for real-world data");
    }

    @Test
    public void givenAR6YW_whenFit_thenR2andAdjustedR2AreFinite() {
        AR model = AR.fit(logPriceDiff, 6);
        assertTrue(Double.isFinite(model.R2()));
        assertTrue(Double.isFinite(model.adjustedR2()));
    }

    @Test
    public void givenAR6OLS_whenFit_thenVarianceIsPositive() {
        AR model = AR.ols(logPriceDiff, 6);
        assertTrue(model.variance() > 0, "variance must be positive");
    }

    @Test
    public void givenAR6YW_whenFit_thenVarianceIsPositive() {
        AR model = AR.fit(logPriceDiff, 6);
        assertTrue(model.variance() > 0, "variance must be positive");
    }

    @Test
    public void givenAR6OLS_whenFit_thenRSSEqualsVarianceTimesDf() {
        AR model = AR.ols(logPriceDiff, 6);
        assertEquals(model.variance() * model.df(), model.RSS(), 1E-10);
    }

    @Test
    public void givenAR6OLS_whenFit_thenTtestHasPRowsAndFourColumns() {
        int p = 6;
        AR model = AR.ols(logPriceDiff, p);
        double[][] tt = model.ttest();
        assertNotNull(tt);
        assertEquals(p, tt.length);
        for (double[] row : tt) {
            assertEquals(4, row.length);
        }
    }

    @Test
    public void givenAR6OLS_whenFit_thenTtestPValuesInUnitInterval() {
        AR model = AR.ols(logPriceDiff, 6);
        for (double[] row : model.ttest()) {
            double pval = row[3];
            assertTrue(pval >= 0.0 && pval <= 1.0,
                    "p-value " + pval + " should be in [0,1]");
        }
    }

    @Test
    public void givenAR6OLS_whenForecast_thenToStringContainsOLS() {
        AR model = AR.ols(logPriceDiff, 6);
        assertTrue(model.toString().contains("OLS"),
                "OLS model toString should mention OLS method");
    }

    @Test
    public void givenAR6YW_whenForecast_thenToStringContainsYuleWalker() {
        AR model = AR.fit(logPriceDiff, 6);
        assertTrue(model.toString().contains("Yule-Walker"),
                "YW model toString should mention Yule-Walker method");
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

    @Test
    public void givenInvalidOrder_whenFittingAR_thenThrowIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> AR.fit(logPriceDiff, 0));
        assertThrows(IllegalArgumentException.class, () -> AR.ols(logPriceDiff, logPriceDiff.length));
    }

    @Test
    public void givenNonPositiveForecastHorizon_whenForecastingAR_thenThrow() {
        AR model = AR.ols(logPriceDiff, 6);
        assertThrows(IllegalArgumentException.class, () -> model.forecast(0));
        assertThrows(IllegalArgumentException.class, () -> model.forecast(-1));
    }
}
