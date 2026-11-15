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

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import smile.datasets.BitcoinPrice;
import smile.io.Read;
import smile.io.Write;

import java.nio.file.Path;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for SARMA and SARIMA.
 *
 * @author Haifeng Li
 */
public class SARIMATest {
    private static double[] logPrice;
    private static double[] logPriceDiff;

    @BeforeAll
    public static void setUpClass() throws Exception {
        var bitcoin = new BitcoinPrice();
        logPrice = bitcoin.logPrice();
        logPriceDiff = TimeSeries.diff(logPrice, 1);
    }

    @Test
    public void testGivenNonSeasonalOrdersWhenFittingSRMAToARMAThenCoefficientsMatch() {
        // Given
        ARMA arma = ARMA.fit(logPriceDiff, 2, 2);

        // When
        SARMA sarma = SARMA.fit(logPriceDiff, 2, 2, 0, 0, 1);

        // Then
        assertEquals(2, sarma.p());
        assertEquals(2, sarma.q());
        assertEquals(0, sarma.P());
        assertEquals(0, sarma.Q());
        assertArrayEquals(arma.ar(), sarma.ar(), 1E-10);
        assertArrayEquals(arma.ma(), sarma.ma(), 1E-10);
        assertEquals(arma.intercept(), sarma.intercept(), 1E-10);
        assertEquals(arma.aic(), sarma.aic(), 1E-10);
        assertEquals(arma.bic(), sarma.bic(), 1E-10);
    }

    @Test
    public void testGivenPureARWhenFittingNonSeasonalThenMatchesAR() {
        // Given
        AR ar = AR.ols(logPriceDiff, 3, true);

        // When
        SARMA sarma = SARMA.fit(logPriceDiff, 3, 0, 0, 0, 1);

        // Then
        assertArrayEquals(ar.ar(), sarma.ar(), 1E-10);
        assertArrayEquals(ar.residuals(), sarma.residuals(), 1E-10);
        ArrayListAssert.additionalCheck(sarma);
    }

    @Test
    public void testGivenSARIMAD0D0WhenFittingThenMatchesSARMA() {
        // Given
        SARMA sarma = SARMA.fit(logPriceDiff, 1, 1, 1, 1, 12);

        // When
        SARIMA sarima = SARIMA.fit(logPriceDiff, 1, 0, 1, 1, 0, 1, 12);

        // Then
        assertEquals(0, sarima.d());
        assertEquals(0, sarima.D());
        assertArrayEquals(sarma.ar(), sarima.ar(), 1E-10);
        assertArrayEquals(sarma.sar(), sarima.sar(), 1E-10);
        assertArrayEquals(sarma.ma(), sarima.ma(), 1E-10);
        assertArrayEquals(sarma.sma(), sarima.sma(), 1E-10);
    }

    @Test
    public void testGivenSARIMANonSeasonalWhenFittingThenMatchesARIMA() {
        // Given
        ARIMA arima = ARIMA.fit(logPrice, 2, 1, 2);

        // When
        SARIMA sarima = SARIMA.fit(logPrice, 2, 1, 2, 0, 0, 0, 1);

        // Then
        assertArrayEquals(arima.ar(), sarima.ar(), 1E-10);
        assertArrayEquals(arima.ma(), sarima.ma(), 1E-10);
        assertEquals(arima.aic(), sarima.aic(), 1E-10);
        // The forecasts should agree numerically as well.
        double[] expected = arima.forecast(5);
        double[] actual = sarima.forecast(5);
        assertArrayEquals(expected, actual, 1E-6);
    }

    @Test
    public void testGivenSyntheticSeasonalARWhenFittingThenRecoversCoefficient() {
        // Given a period-12 seasonal AR(1) with a small non-seasonal AR(1).
        int period = 12;
        int n = 240;
        double[] x = new double[n];
        Random rng = new Random(42);
        double phi = 0.6;
        for (int t = 0; t < n; t++) {
            x[t] = 5.0 + (t >= period ? phi * x[t - period] : 0.0) + rng.nextGaussian();
        }

        // When
        SARIMA model = SARIMA.fit(x, 0, 0, 0, 1, 0, 0, period);

        // Then
        assertEquals(1, model.sar().length);
        assertEquals(phi, model.sar()[0], 0.08);
        double[] forecast = model.forecast(12);
        assertEquals(12, forecast.length);
        for (double f : forecast) {
            assertTrue(Double.isFinite(f));
        }
    }

    @Test
    public void testGivenSeasonalDifferencingWhenFittingThenResidualVarianceIsSmall() {
        // Given a series whose dominant variation is a period-12 seasonal pattern.
        int period = 12;
        int n = 240;
        double[] x = new double[n];
        Random rng = new Random(7);
        for (int t = 0; t < n; t++) {
            x[t] = 50.0 + 4.0 * Math.cos(2 * Math.PI * (t % period) / period) + rng.nextGaussian() * 0.5;
        }

        // When
        SARIMA model = SARIMA.fit(x, 0, 0, 0, 0, 1, 0, period);

        // Then: seasonally differencing removes the pattern, so the residual
        // variance is an order of magnitude smaller than the series variance.
        double seriesVar = 0.0;
        double mean = 0.0;
        for (double v : x) {
            mean += v;
        }
        mean /= n;
        for (double v : x) {
            seriesVar += (v - mean) * (v - mean);
        }
        seriesVar /= n;

        assertTrue(model.variance() < 0.1 * seriesVar,
                "residual variance " + model.variance() + " vs series variance " + seriesVar);
        assertEquals(1, model.D());
        assertEquals(period, model.period());
    }

    @Test
    public void testGivenSeasonallyIntegratedSeriesWhenForecastingThenPeriodicityIsPreserved() {
        // Given a noisy period-4 pattern with a small positive level.
        int period = 4;
        int n = 120;
        double[] x = new double[n];
        Random rng = new Random(3);
        double[] pattern = {0.0, 1.0, 2.0, 1.0};
        for (int t = 0; t < n; t++) {
            x[t] = 10.0 + pattern[t % period] + rng.nextGaussian() * 0.05;
        }

        // When: D=1 removes the seasonal pattern; the forecast must still
        // reproduce it because the seasonal difference is inverted.
        SARIMA model = SARIMA.fit(x, 0, 0, 0, 0, 1, 0, period);
        double[] forecast = model.forecast(2 * period);

        // Then
        assertEquals(2 * period, forecast.length);
        for (int h = period; h < forecast.length; h++) {
            assertEquals(forecast[h - period], forecast[h], 0.5);
        }
        // And it continues the level of the series.
        assertEquals(11.0, forecast[0], 2.0);
        assertEquals(12.0, forecast[1], 2.0);
    }

    @Test
    public void testGivenInvalidOrdersWhenFittingThenThrows() {
        assertThrows(IllegalArgumentException.class, () -> SARIMA.fit(logPrice, -1, 1, 1, 0, 0, 0, 12));
        assertThrows(IllegalArgumentException.class, () -> SARIMA.fit(logPrice, 1, -1, 1, 0, 0, 0, 12));
        assertThrows(IllegalArgumentException.class, () -> SARIMA.fit(logPrice, 1, 1, -1, 0, 0, 0, 12));
        assertThrows(IllegalArgumentException.class, () -> SARIMA.fit(logPrice, 0, 0, 0, 0, 0, 0, 12));
        assertThrows(IllegalArgumentException.class, () -> SARIMA.fit(logPrice, 1, 0, 0, 1, 0, 0, 1));
        assertThrows(IllegalArgumentException.class, () -> SARIMA.fit(logPrice, 1, 0, 0, 0, 1, 0, 1));
        assertThrows(IllegalArgumentException.class, () -> SARIMA.fit(new double[3], 1, 0, 0, 0, 0, 0, 1));
        // Too few observations for a long-lag SARMA fit.
        double[] shortSeries = new double[40];
        for (int t = 0; t < shortSeries.length; t++) {
            shortSeries[t] = Math.sin(0.5 * t);
        }
        assertThrows(IllegalArgumentException.class, () -> SARMA.fit(shortSeries, 2, 0, 2, 0, 12));
        assertThrows(IllegalArgumentException.class, () -> {
            SARIMA model = SARIMA.fit(logPrice, 1, 1, 1, 0, 0, 0, 12);
            model.forecast(0);
        });
    }

    @Test
    public void testGivenSARMAWhenSerializedThenRoundTrips() throws Exception {
        // Given
        SARMA model = SARMA.fit(logPriceDiff, 1, 1, 0, 0, 12);

        // When
        Path path = Write.object(model);
        SARMA restored = (SARMA) Read.object(path);

        // Then
        assertEquals(model.p(), restored.p());
        assertEquals(model.q(), restored.q());
        assertArrayEquals(model.ar(), restored.ar(), 1E-12);
        assertEquals(model.forecast(3)[2], restored.forecast(3)[2], 1E-10);
    }

    @Test
    public void testGivenSARIMAWhenSerializedThenRoundTrips() throws Exception {
        // Given
        SARIMA model = SARIMA.fit(logPrice, 1, 1, 1, 0, 1, 0, 12);

        // When
        Path path = Write.object(model);
        SARIMA restored = (SARIMA) Read.object(path);

        // Then
        assertEquals(model.p(), restored.p());
        assertEquals(model.d(), restored.d());
        assertEquals(model.D(), restored.D());
        assertEquals(model.forecast(4)[3], restored.forecast(4)[3], 1E-8);
        assertEquals(model.aic(), restored.aic(), 1E-12);
    }

    @Test
    public void testGivenFittedModelsWhenPrintingThenSummaryMentionsOrders() {
        SARMA sarma = SARMA.fit(logPriceDiff, 1, 1, 0, 0, 12);
        assertTrue(sarma.toString().contains("SARMA(1, 1)(0, 0)[12]"));

        SARIMA sarima = SARIMA.fit(logPrice, 1, 1, 1, 0, 1, 0, 12);
        String summary = sarima.toString();
        assertTrue(summary.contains("SARIMA(1, 1, 1)(0, 1, 0)[12]"));
        assertTrue(summary.contains("Residuals:"));
        assertTrue(summary.contains("AIC:"));
    }

    /** Helper to keep a rare assertion out of the main body. */
    private static final class ArrayListAssert {
        static void additionalCheck(SARMA sarma) {
            assertTrue(sarma.variance() > 0.0);
            assertTrue(Double.isFinite(sarma.logLikelihood()));
        }
    }
}
