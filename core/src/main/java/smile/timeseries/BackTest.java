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

import java.io.Serial;
import java.io.Serializable;
import java.util.Arrays;
import java.util.Locale;

/**
 * Rolling-origin (walk-forward) backtesting of a forecasting procedure.
 * <p>
 * The series is split into {@code folds} consecutive test blocks of
 * {@code horizon} points each, taken from the end of the series. Fold
 * {@code k} (0-based, in chronological order) trains on the data that
 * precedes its test block and is scored on that block. With an
 * <em>expanding</em> window every fold sees all history up to its origin;
 * with a <em>rolling</em> window every fold sees the same number of
 * observations, namely the size of the first fold's training set.
 * <p>
 * Only one-step models are required: a model is fitted per fold and its
 * multi-step {@link TimeSeriesModel#forecast(int)} is called once with
 * the fold horizon, so static (non-updating) multi-step forecasts are
 * evaluated. This matches the standard "fixed-origin, multi-step"
 * evaluation of forecast accuracy.
 *
 * @param folds    the number of test blocks.
 * @param horizon  the number of points in each test block.
 * @param metrics  the per-fold accuracy metrics, in chronological order.
 * @param total    the accuracy metrics over all folds pooled together.
 * @param actual   the concatenated actual values of all test blocks.
 * @param predicted the concatenated forecasts of all test blocks.
 *
 * @author Haifeng Li
 */
public record BackTest(int folds, int horizon, ForecastMetrics[] metrics,
                       ForecastMetrics total, double[] actual, double[] predicted)
        implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * A factory that fits a model to a history window.
     */
    @FunctionalInterface
    public interface Fit {
        /**
         * Fits a model to the given history.
         *
         * @param history the training series, in chronological order.
         * @return the fitted model.
         */
        TimeSeriesModel fit(double[] history);
    }

    /**
     * Runs a rolling-origin backtest with the standard naive MASE benchmark.
     *
     * @param x         the series, in chronological order.
     * @param fit       the model factory.
     * @param horizon   the number of points in each test block.
     * @param folds     the number of test blocks.
     * @param expanding whether the training window expands (true) or rolls (false).
     * @return the backtest results.
     */
    public static BackTest rollingOrigin(double[] x, Fit fit, int horizon, int folds, boolean expanding) {
        return rollingOrigin(x, fit, horizon, folds, expanding, 1);
    }

    /**
     * Runs a rolling-origin backtest.
     *
     * @param x         the series, in chronological order.
     * @param fit       the model factory.
     * @param horizon   the number of points in each test block.
     * @param folds     the number of test blocks.
     * @param expanding whether the training window expands (true) or rolls (false).
     * @param period    the seasonal period used for the MASE benchmark scale.
     * @return the backtest results.
     */
    public static BackTest rollingOrigin(double[] x, Fit fit, int horizon, int folds,
                                         boolean expanding, int period) {
        if (fit == null) {
            throw new IllegalArgumentException("Null model factory");
        }
        if (x == null || x.length == 0) {
            throw new IllegalArgumentException("Empty series");
        }
        if (horizon < 1) {
            throw new IllegalArgumentException("Invalid horizon: " + horizon);
        }
        if (folds < 1) {
            throw new IllegalArgumentException("Invalid number of folds: " + folds);
        }
        if (period < 1) {
            throw new IllegalArgumentException("Invalid period: " + period);
        }

        int testSize = folds * horizon;
        if (testSize >= x.length) {
            throw new IllegalArgumentException(String.format(
                    "Not enough observations for %d folds of horizon %d: length=%d",
                    folds, horizon, x.length));
        }

        int firstTrainSize = x.length - testSize;
        ForecastMetrics[] metrics = new ForecastMetrics[folds];
        double[] actual = new double[testSize];
        double[] predicted = new double[testSize];

        for (int k = 0; k < folds; k++) {
            int trainEnd = firstTrainSize + k * horizon;
            int trainStart = expanding ? 0 : Math.max(0, trainEnd - firstTrainSize);
            double[] history = Arrays.copyOfRange(x, trainStart, trainEnd);

            TimeSeriesModel model;
            try {
                model = fit.fit(history);
            } catch (IllegalArgumentException ex) {
                throw new IllegalArgumentException(String.format(
                        "Fold %d failed to fit on %d observations: %s",
                        k, history.length, ex.getMessage()), ex);
            }

            double[] forecast = model.forecast(horizon);
            if (forecast.length != horizon) {
                throw new IllegalStateException(String.format(
                        "Fold %d returned %d forecasts, expected %d", k, forecast.length, horizon));
            }

            System.arraycopy(x, trainEnd, actual, k * horizon, horizon);
            System.arraycopy(forecast, 0, predicted, k * horizon, horizon);
            metrics[k] = ForecastMetrics.of(
                    Arrays.copyOfRange(actual, k * horizon, (k + 1) * horizon), forecast, history, period);
        }

        ForecastMetrics total = ForecastMetrics.of(actual, predicted, x, period);
        return new BackTest(folds, horizon, metrics, total, actual, predicted);
    }

    /**
     * Returns the mean of the per-fold metric values.
     *
     * @param metric the name of the metric: "mae", "rmse", "mape", "smape", "mase", or "bias".
     * @return the average of the metric across folds; NaN terms are ignored.
     */
    public double average(String metric) {
        double sum = 0.0;
        int count = 0;
        for (ForecastMetrics m : metrics) {
            double value = switch (metric.toLowerCase(Locale.ROOT)) {
                case "mae" -> m.mae();
                case "rmse" -> m.rmse();
                case "mape" -> m.mape();
                case "smape" -> m.smape();
                case "mase" -> m.mase();
                case "bias" -> m.bias();
                default -> throw new IllegalArgumentException("Unknown metric: " + metric);
            };
            if (!Double.isNaN(value)) {
                sum += value;
                count++;
            }
        }
        return count == 0 ? Double.NaN : sum / count;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(String.format(
                "Rolling-origin backtest: %d folds x horizon %d%n", folds, horizon));
        sb.append(String.format(Locale.ROOT, "%6s %10s %10s %10s %10s %10s%n",
                "fold", "MAE", "RMSE", "MAPE", "sMAPE", "MASE"));
        for (int k = 0; k < folds; k++) {
            ForecastMetrics m = metrics[k];
            sb.append(String.format(Locale.ROOT, "%6d %10.4f %10.4f %9.2f%% %9.2f%% %10.4f%n",
                    k, m.mae(), m.rmse(), m.mape(), m.smape(), m.mase()));
        }
        sb.append("----------------------------------------------------------------------\n");
        sb.append(total);
        return sb.toString();
    }
}
