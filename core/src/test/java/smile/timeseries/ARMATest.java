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
 * Unit tests targeting specific ARMA correctness issues:
 * <ol>
 *   <li>{@code forecast(int l)} index bug when {@code p < q = k}.</li>
 *   <li>R² TSS computed from actual observations, not fitted values.</li>
 * </ol>
 *
 * @author Haifeng Li
 */
public class ARMATest {
    private static double[] logPriceDiff;
    public ARMATest() {
    }

    @BeforeAll
    public static void setUpClass() throws Exception {
        var bitcoin = new BitcoinPrice();
        var logPrice = bitcoin.logPrice();
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

    /**
     * ARMA(2,3) has p=2 < q=3 so k=max(p,q)=3. Previously forecast(int l)
     * wrote results at index x[p+i]=x[2+i] instead of x[k+i]=x[3+i],
     * corrupting the last history element and leaving the first returned slot
     * uninitialized (0.0). Verify that forecast(1)[0] equals forecast().
     */
    @Test
    public void givenARMA23_whenForecastMultiStep_thenFirstStepMatchesSingleStepForecast() throws Exception {
        ARMA model = ARMA.fit(logPriceDiff, 2, 3);

        double oneStep = model.forecast();
        double[] multiStep = model.forecast(3);

        // First element of multi-step forecast must equal the one-step forecast.
        // Without the p+i → k+i fix, multiStep[0] would be 0.0 or wrong.
        assertEquals(oneStep, multiStep[0], 1E-12);
    }

    /**
     * Continuation of the forecast index test: subsequent steps must be finite
     * and non-zero (for Bitcoin data the intercept is non-zero, so a pure-zero
     * result is a sign of the uninitialized-slot bug).
     */
    @Test
    public void givenARMA23_whenForecastMultiStep_thenAllStepsAreFinite() throws Exception {
        ARMA model = ARMA.fit(logPriceDiff, 2, 3);
        double[] forecast = model.forecast(3);

        for (int i = 0; i < forecast.length; i++) {
            assertTrue(Double.isFinite(forecast[i]),
                    "forecast[" + i + "] should be finite");
        }
    }

    /**
     * Verify that ARMA R² is computed with TSS from actual observations, not
     * from fitted values. When fitted values are used the TSS for near-random-
     * walk data (small coefficients, fitted ≈ intercept ≈ 0) collapses to
     * ≈ 0, making R² = -Inf or NaN. With actual observations TSS is the
     * usual sum of squared deviations from the sample mean, always positive.
     */
    @Test
    public void givenARMA63_whenR2Computed_thenR2IsFinite() throws Exception {
        ARMA model = ARMA.fit(logPriceDiff, 6, 3);
        assertTrue(Double.isFinite(model.R2()),
                "R2 should be finite; TSS must use observations, not fitted values");
        assertTrue(Double.isFinite(model.adjustedR2()),
                "adjustedR2 should be finite");
    }

    /**
     * ARMA(3,6) forces q > p (k = max(3,6) = 6) — a symmetric test to
     * givenARMA23 but with a larger MA order.
     */
    @Test
    public void givenARMA36_whenForecastMultiStep_thenFirstStepMatchesSingleStepForecast() throws Exception {
        ARMA model = ARMA.fit(logPriceDiff, 3, 6);

        double oneStep = model.forecast();
        double[] multiStep = model.forecast(4);

        assertEquals(oneStep, multiStep[0], 1E-12);
        for (double v : multiStep) {
            assertTrue(Double.isFinite(v), "all multi-step forecasts should be finite");
        }
    }

    /**
     * Sanity: residuals and fittedValues have the same length for ARMA(2,3).
     */
    @Test
    public void givenARMA23_whenFit_thenResidualLengthMatchesFittedValues() throws Exception {
        ARMA model = ARMA.fit(logPriceDiff, 2, 3);
        assertEquals(model.fittedValues().length, model.residuals().length);
    }

    /**
     * df equals the number of regression observations used in the second-stage
     * Hannan-Rissanen LS (n = x.length - (p+q+20) - max(p,q)).
     */
    @Test
    public void givenARMA23_whenFit_thenDfEqualsSecondStageObservations() throws Exception {
        int p = 2, q = 3;
        int m = p + q + 20;   // 25
        int k = Math.max(p, q); // 3
        int expectedDf = logPriceDiff.length - m - k;

        ARMA model = ARMA.fit(logPriceDiff, p, q);
        assertEquals(expectedDf, model.df());
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
        assertEquals( 0.001947063, model.variance(), 1E-8);
        assertEquals(model.fittedValues().length, model.residuals().length);

        double oneStepForecast = model.forecast();
        double[] forecast = model.forecast(3);
        assertEquals(oneStepForecast, forecast[0], 1E-12);
        assertTrue(Double.isFinite(forecast[1]));
        assertTrue(Double.isFinite(forecast[2]));
    }

    @Test
    public void givenInvalidOrder_whenFittingARMA_thenThrowIllegalArgumentException() {
        double[] x = {1.0, 0.8, 0.4, 0.2, 0.1};
        assertThrows(IllegalArgumentException.class, () -> ARMA.fit(x, 0, 1));
        assertThrows(IllegalArgumentException.class, () -> ARMA.fit(x, 1, 0));
    }

    @Test
    public void givenTooShortSeries_whenFittingARMA_thenThrowIllegalArgumentException() {
        double[] shortSeries = new double[40];
        for (int i = 0; i < shortSeries.length; i++) {
            shortSeries[i] = Math.sin(i * 0.1);
        }

        assertThrows(IllegalArgumentException.class, () -> ARMA.fit(shortSeries, 10, 10));
    }

    @Test
    public void givenNonPositiveForecastHorizon_whenForecastingARMA_thenThrow() throws Exception {
        var bitcoin = new BitcoinPrice();
        var logPrice = bitcoin.logPrice();
        double[] x = TimeSeries.diff(logPrice, 1);
        ARMA model = ARMA.fit(x, 6, 3);

        assertThrows(IllegalArgumentException.class, () -> model.forecast(0));
        assertThrows(IllegalArgumentException.class, () -> model.forecast(-1));
    }
}
