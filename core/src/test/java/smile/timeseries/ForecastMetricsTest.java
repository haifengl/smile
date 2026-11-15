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

import org.junit.jupiter.api.Test;
import smile.datasets.BitcoinPrice;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link ForecastMetrics} and {@link BackTest}.
 *
 * @author Haifeng Li
 */
public class ForecastMetricsTest {

    @Test
    public void testGivenPerfectForecastWhenScoredThenErrorsAreZero() {
        // Given
        double[] truth = {1.0, 2.0, 3.0, 4.0};

        // When
        ForecastMetrics metrics = ForecastMetrics.of(truth, truth.clone());

        // Then
        assertEquals(4, metrics.size());
        assertEquals(0.0, metrics.mae(), 1E-12);
        assertEquals(0.0, metrics.rmse(), 1E-12);
        assertEquals(0.0, metrics.mape(), 1E-12);
        assertEquals(0.0, metrics.smape(), 1E-12);
        assertEquals(0.0, metrics.bias(), 1E-12);
        assertTrue(Double.isNaN(metrics.mase()));
    }

    @Test
    public void testGivenConstantBiasWhenScoredThenBiasSignAndMagnitude() {
        // Given
        double[] truth = {10.0, 20.0, 30.0};
        double[] forecast = {9.0, 19.0, 29.0};

        // When
        ForecastMetrics metrics = ForecastMetrics.of(truth, forecast);

        // Then
        assertEquals(1.0, metrics.bias(), 1E-12);
        assertEquals(1.0, metrics.mae(), 1E-12);
        assertEquals(1.0, metrics.rmse(), 1E-12);
        assertEquals(100.0 * (1.0 / 10.0 + 1.0 / 20.0 + 1.0 / 30.0) / 3.0, metrics.mape(), 1E-10);
    }

    @Test
    public void testGivenZeroTruthWhenScoredThenMapeSkipsZeroValues() {
        // Given
        double[] truth = {0.0, 10.0};
        double[] forecast = {5.0, 12.0};

        // When
        ForecastMetrics metrics = ForecastMetrics.of(truth, forecast);

        // Then
        // Only the second point contributes to MAPE: |−2|/10 = 0.2 → 20%.
        assertEquals(20.0, metrics.mape(), 1E-10);
        // sMAPE: point 1 gives 2*5/(0+5) = 2; point 2 gives 2*2/(10+12).
        assertEquals(100.0 * (2.0 + 2.0 * 2.0 / 22.0) / 2.0, metrics.smape(), 1E-10);
    }

    @Test
    public void testGivenAllZeroTruthThenMapeIsNaN() {
        // Given
        double[] truth = {0.0, 0.0};
        double[] forecast = {1.0, 2.0};

        // When
        ForecastMetrics metrics = ForecastMetrics.of(truth, forecast);

        // Then
        assertTrue(Double.isNaN(metrics.mape()));
        assertEquals(1.5, metrics.mae(), 1E-12);
    }

    @Test
    public void testGivenNaiveForecastWhenScoredWithTrainingThenMaseIsOne() {
        // Given the in-sample naive forecast of a series: forecast[t] = x[t-1].
        double[] training = {3.0, 1.0, 4.0, 1.0, 5.0, 9.0};
        double[] truth = {1.0, 4.0, 1.0, 5.0, 9.0};
        double[] forecast = {3.0, 1.0, 4.0, 1.0, 5.0};

        // When
        ForecastMetrics metrics = ForecastMetrics.of(truth, forecast, training);

        // Then
        assertEquals(1.0, metrics.mase(), 1E-12);
    }

    @Test
    public void testGivenSeasonalNaiveForecastWhenScoredThenMaseIsOne() {
        // Given the in-sample seasonal-naive forecast: forecast[t] = x[t-period].
        int period = 12;
        double[] training = new double[4 * period];
        Random rng = new Random(42);
        for (int t = 0; t < training.length; t++) {
            training[t] = 10.0 + 3.0 * Math.sin(2 * Math.PI * t / period) + rng.nextGaussian() * 0.1;
        }
        double[] truth = java.util.Arrays.copyOfRange(training, period, training.length);
        double[] forecast = java.util.Arrays.copyOfRange(training, 0, training.length - period);

        // When
        ForecastMetrics metrics = ForecastMetrics.of(truth, forecast, training, period);

        // Then
        assertEquals(1.0, metrics.mase(), 1E-12);
    }

    @Test
    public void testGivenNaiveScaleWhenSeriesTooShortThenNaN() {
        assertTrue(Double.isNaN(ForecastMetrics.scale(new double[]{1.0}, 2)));
        assertTrue(Double.isNaN(ForecastMetrics.scale(new double[]{1.0, 1.0, 1.0}, 1)));
        assertThrows(IllegalArgumentException.class, () -> ForecastMetrics.scale(new double[]{1.0, 2.0}, 0));
    }

    @Test
    public void testGivenMismatchedVectorsWhenScoredThenThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> ForecastMetrics.of(new double[]{1.0, 2.0}, new double[]{1.0}));
        assertThrows(IllegalArgumentException.class,
                () -> ForecastMetrics.of(new double[0], new double[0]));
    }

    @Test
    public void testGivenRollingOriginBacktestThenFoldsCoverTheTail() {
        // Given
        int n = 120;
        double[] x = new double[n];
        Random rng = new Random(42);
        for (int t = 0; t < n; t++) {
            x[t] = 0.1 * t + rng.nextGaussian() * 0.2;
        }

        // When
        BackTest backtest = BackTest.rollingOrigin(x, history -> ARIMA.fit(history, 2, 1, 1), 5, 4, true);

        // Then
        assertEquals(4, backtest.folds());
        assertEquals(5, backtest.horizon());
        assertEquals(20, backtest.actual().length);
        assertEquals(20, backtest.predicted().length);
        assertEquals(4, backtest.metrics().length);

        // The pooled actual values are the last 20 observations of the series.
        for (int i = 0; i < 20; i++) {
            assertEquals(x[n - 20 + i], backtest.actual()[i], 1E-12);
        }

        assertTrue(backtest.total().mae() > 0.0);
        assertTrue(Double.isFinite(backtest.average("mae")));
        assertTrue(backtest.toString().contains("Rolling-origin backtest"));
    }

    @Test
    public void testGivenExpandingAndRollingWindowsThenTrainingSizesDiffer() {
        // Given
        int n = 100;
        double[] x = new double[n];
        for (int t = 0; t < n; t++) {
            x[t] = Math.sin(0.2 * t) + 0.01 * t;
        }

        // When
        int[] expandingSizes = new int[3];
        int[] rollingSizes = new int[3];
        BackTest.rollingOrigin(x, history -> {
            expandingSizes[Math.min(2, indexOf(expandingSizes))] = history.length;
            return ARIMA.fit(history, 1, 0, 1);
        }, 5, 3, true);
        BackTest.rollingOrigin(x, history -> {
            rollingSizes[Math.min(2, indexOf(rollingSizes))] = history.length;
            return ARIMA.fit(history, 1, 0, 1);
        }, 5, 3, false);

        // Then
        assertEquals(85, expandingSizes[0]);
        assertEquals(90, expandingSizes[1]);
        assertEquals(95, expandingSizes[2]);
        assertEquals(85, rollingSizes[0]);
        assertEquals(85, rollingSizes[1]);
        assertEquals(85, rollingSizes[2]);
    }

    private static int indexOf(int[] array) {
        for (int i = 0; i < array.length; i++) {
            if (array[i] == 0) {
                return i;
            }
        }
        return array.length - 1;
    }

    @Test
    public void testGivenTooManyFoldsWhenBacktestingThenThrows() {
        double[] x = new double[10];
        assertThrows(IllegalArgumentException.class,
                () -> BackTest.rollingOrigin(x, history -> ARIMA.fit(history, 1, 0, 1), 5, 2, true));
        assertThrows(IllegalArgumentException.class,
                () -> BackTest.rollingOrigin(x, history -> ARIMA.fit(history, 1, 0, 1), 0, 1, true));
        assertThrows(IllegalArgumentException.class,
                () -> BackTest.rollingOrigin(x, history -> ARIMA.fit(history, 1, 0, 1), 2, 0, true));
    }

    @Test
    public void testGivenFitFailureInAFoldThenReportsFoldIndex() {
        double[] x = new double[40];
        for (int t = 0; t < 40; t++) {
            x[t] = t;
        }

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> BackTest.rollingOrigin(x, history -> {
                    if (history.length < 35) {
                        throw new IllegalArgumentException("not enough data");
                    }
                    return ARIMA.fit(history, 1, 0, 1);
                }, 5, 2, false));
        assertTrue(ex.getMessage().contains("Fold"));
        assertTrue(ex.getMessage().contains("not enough data"));
    }

    @Test
    public void testGivenModelsImplementTimeSeriesModelThenPolymorphicForecast() throws Exception {
        // Given
        var bitcoin = new BitcoinPrice();
        double[] logPrice = bitcoin.logPrice();
        double[] diff = TimeSeries.diff(logPrice, 1);

        TimeSeriesModel[] models = {
                AR.fit(diff, 2),
                ARMA.fit(diff, 2, 2),
                ARIMA.fit(logPrice, 2, 1, 2)
        };

        // When/Then
        for (TimeSeriesModel model : models) {
            double[] forecast = model.forecast(3);
            assertEquals(3, forecast.length);
            assertTrue(Double.isFinite(forecast[0]));
            assertEquals(model.fittedValues().length, model.residuals().length);
            assertTrue(model.variance() >= 0.0);
            assertTrue(Double.isFinite(model.aic()));
            assertTrue(Double.isFinite(model.bic()));
        }
    }
}
