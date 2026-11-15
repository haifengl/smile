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
import java.util.Locale;
import smile.validation.metric.MAD;
import smile.validation.metric.RMSE;

/**
 * Accuracy metrics for point forecasts.
 * <p>
 * The metrics are defined as follows, where {@code e_t = truth[t] - forecast[t]}:
 * <ul>
 * <li><b>MAE</b> — mean absolute error, {@code mean(|e_t|)}. Same scale as the data.</li>
 * <li><b>RMSE</b> — root mean squared error, {@code sqrt(mean(e_t²))}. Same scale as the data.</li>
 * <li><b>MAPE</b> — mean absolute percentage error, {@code 100 · mean(|e_t| / |truth[t]|)}.
 *     Observations with a zero truth value are skipped; the metric is {@link Double#NaN}
 *     when every truth value is zero.</li>
 * <li><b>sMAPE</b> — symmetric MAPE, {@code 100 · mean(2|e_t| / (|truth[t]| + |forecast[t]|))}.
 *     Terms whose denominator is zero are skipped; NaN when all denominators vanish.</li>
 * <li><b>MASE</b> — mean absolute scaled error, {@code MAE / scale}, where {@code scale} is the
 *     mean absolute error of the in-sample naive (or, with a period, seasonal-naive) forecast
 *     computed from the training series. A value of 1.0 means the forecast is no better than
 *     the naive benchmark; smaller is better, and the metric is scale-free.</li>
 * <li><b>bias</b> — mean error, {@code mean(e_t)}. Positive means the forecast is consistently
 *     too low.</li>
 * </ul>
 *
 * @param size    the number of forecast points.
 * @param mae     the mean absolute error.
 * @param rmse    the root mean squared error.
 * @param mape    the mean absolute percentage error (in percent).
 * @param smape   the symmetric mean absolute percentage error (in percent).
 * @param mase    the mean absolute scaled error.
 * @param bias    the mean error.
 *
 * @author Haifeng Li
 */
public record ForecastMetrics(int size, double mae, double rmse, double mape,
                              double smape, double mase, double bias) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * Computes the metrics that do not need a training series. {@code mase} is
     * {@link Double#NaN} because its scale is unknown.
     *
     * @param truth    the actual values.
     * @param forecast the forecast values.
     * @return the forecast metrics.
     */
    public static ForecastMetrics of(double[] truth, double[] forecast) {
        return of(truth, forecast, Double.NaN);
    }

    /**
     * Computes the metrics with the MASE scale taken from a training series,
     * using the standard naive (one-step, lag 1) benchmark.
     *
     * @param truth    the actual values.
     * @param forecast the forecast values.
     * @param training the history used to compute the MASE scale.
     * @return the forecast metrics.
     */
    public static ForecastMetrics of(double[] truth, double[] forecast, double[] training) {
        return of(truth, forecast, scale(training, 1));
    }

    /**
     * Computes the metrics with the MASE scale taken from a training series
     * and a seasonal-naive benchmark of the given period.
     *
     * @param truth    the actual values.
     * @param forecast the forecast values.
     * @param training the history used to compute the MASE scale.
     * @param period   the seasonal period of the naive benchmark; 1 for the standard naive.
     * @return the forecast metrics.
     */
    public static ForecastMetrics of(double[] truth, double[] forecast, double[] training, int period) {
        return of(truth, forecast, scale(training, period));
    }

    /**
     * Computes the metrics given a precomputed MASE scale.
     *
     * @param truth    the actual values.
     * @param forecast the forecast values.
     * @param scale    the MASE scale; NaN to leave MASE undefined.
     * @return the forecast metrics.
     */
    public static ForecastMetrics of(double[] truth, double[] forecast, double scale) {
        validate(truth, forecast);

        int n = truth.length;
        double mae = MAD.of(truth, forecast);
        double rmse = RMSE.of(truth, forecast);

        double apeSum = 0.0;
        int apeCount = 0;
        double sapeSum = 0.0;
        int sapeCount = 0;
        double biasSum = 0.0;

        for (int i = 0; i < n; i++) {
            double e = truth[i] - forecast[i];
            biasSum += e;

            double a = Math.abs(truth[i]);
            if (a > 0.0) {
                apeSum += Math.abs(e) / a;
                apeCount++;
            }

            double denom = Math.abs(truth[i]) + Math.abs(forecast[i]);
            if (denom > 0.0) {
                sapeSum += 2.0 * Math.abs(e) / denom;
                sapeCount++;
            }
        }

        double mape = apeCount == 0 ? Double.NaN : 100.0 * apeSum / apeCount;
        double smape = sapeCount == 0 ? Double.NaN : 100.0 * sapeSum / sapeCount;
        double mase = (Double.isNaN(scale) || scale == 0.0) ? Double.NaN : mae / scale;

        return new ForecastMetrics(n, mae, rmse, mape, smape, mase, biasSum / n);
    }

    /**
     * Computes the MASE scale of the naive benchmark: the mean absolute error of
     * {@code training[t] - training[t-period]}.
     *
     * @param training the training series.
     * @param period   the seasonal period; 1 for the standard naive benchmark.
     * @return the scale, or {@link Double#NaN} if the series is too short.
     */
    public static double scale(double[] training, int period) {
        if (period < 1) {
            throw new IllegalArgumentException("Invalid period: " + period);
        }
        if (training.length <= period) {
            return Double.NaN;
        }

        double sum = 0.0;
        int n = training.length;
        for (int t = period; t < n; t++) {
            sum += Math.abs(training[t] - training[t - period]);
        }

        double scale = sum / (n - period);
        return scale == 0.0 ? Double.NaN : scale;
    }

    private static void validate(double[] truth, double[] forecast) {
        if (truth.length != forecast.length) {
            throw new IllegalArgumentException(String.format(
                    "The vector sizes don't match: %d != %d.", truth.length, forecast.length));
        }
        if (truth.length == 0) {
            throw new IllegalArgumentException("Empty forecast vectors");
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(String.format("Forecast accuracy over %d points:%n", size));
        sb.append(String.format(Locale.ROOT, "  MAE   %12.4f%n", mae));
        sb.append(String.format(Locale.ROOT, "  RMSE  %12.4f%n", rmse));
        sb.append(String.format(Locale.ROOT, "  MAPE  %12.4f%%%n", mape));
        sb.append(String.format(Locale.ROOT, "  sMAPE %12.4f%%%n", smape));
        sb.append(String.format(Locale.ROOT, "  MASE  %12.4f%n", mase));
        sb.append(String.format(Locale.ROOT, "  bias  %12.4f", bias));
        return sb.toString();
    }
}
