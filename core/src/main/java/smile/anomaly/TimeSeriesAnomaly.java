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
package smile.anomaly;

import java.io.Serial;
import java.io.Serializable;
import java.util.Locale;
import java.util.Properties;
import smile.math.MathEx;
import smile.timeseries.ETS;
import smile.timeseries.TimeSeriesModel;

/**
 * Residual-based anomaly detection for univariate time series.
 * <p>
 * A {@link TimeSeriesModel} is fitted to a training history. Each observation
 * of a new series is compared with the one-step-ahead forecast that the model
 * makes from the observations preceding it, and the residual
 * <code>e_t = x_t - yhat_t</code> is standardized by a robust scale:
 * <ul>
 * <li>{@link Scale#MAD} — median absolute deviation, <code>1.4826 * MAD</code>
 *     (the default; insensitive to the anomalies themselves);</li>
 * <li>{@link Scale#STD} — standard deviation of the in-sample residuals;</li>
 * <li>{@link Scale#EWMA} — exponentially weighted root mean square of the
 *     residuals, with smoothing factor {@code alpha}.</li>
 * </ul>
 * An observation is anomalous when its standardized absolute residual
 * <code>|z_t|</code> exceeds the threshold. The threshold is expressed in units
 * of the scale; the default 3.0 corresponds to "three robust standard
 * deviations". When the model is fitted on the history alone, the score of a
 * value is evaluated against the last stored observations.
 * <p>
 * <b>Order matters.</b> Unlike the i.i.d. detectors of this package
 * ({@link IsolationForest}, {@link LOF}, {@link SVM}), the score of an
 * observation depends on the observations that precede it. The batch methods
 * therefore always consume the input sequentially, and the wrapping
 * {@link smile.model.AnomalyModel} declares the same contract: a single numeric
 * column and row order equal to time order.
 *
 * <h2>References</h2>
 * <ol>
 * <li>Rob J. Hyndman and Anne B. Koehler. Another look at measures of forecast
 *     accuracy. International Journal of Forecasting, 22(4):679–688, 2006.</li>
 * <li>Douglas M. Hawkins. Identification of Outliers. Chapman and Hall, 1980.</li>
 * </ol>
 *
 * @author Haifeng Li
 */
public class TimeSeriesAnomaly implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    /** The consistency constant that makes the MAD comparable to the standard deviation. */
    private static final double MAD_CONSTANT = 1.4826;

    /** The standardization method of the residuals. */
    public enum Scale {
        /** Median absolute deviation; robust to outliers. */
        MAD,
        /** Standard deviation. */
        STD,
        /** Exponentially weighted standard deviation. */
        EWMA
    }

    /**
     * Hyperparameters of the detector.
     *
     * @param scale      the robust scale estimator.
     * @param threshold  the decision threshold in standardized units.
     * @param ewmaAlpha  the smoothing factor of the EWMA scale, in (0, 1].
     */
    public record Options(Scale scale, double threshold, double ewmaAlpha) {
        /**
         * Constructor.
         */
        public Options {
            if (scale == null) {
                throw new IllegalArgumentException("Null scale");
            }
            if (threshold <= 0.0) {
                throw new IllegalArgumentException("Invalid threshold: " + threshold);
            }
            if (!(ewmaAlpha > 0.0 && ewmaAlpha <= 1.0)) {
                throw new IllegalArgumentException("Invalid EWMA alpha: " + ewmaAlpha);
            }
        }

        /** Default constructor: MAD scale, threshold 3.0, EWMA alpha 0.2. */
        public Options() {
            this(Scale.MAD, 3.0, 0.2);
        }

        /**
         * Returns the persistent set of hyperparameters.
         *
         * @return the persistent set.
         */
        public Properties toProperties() {
            Properties props = new Properties();
            props.setProperty("smile.timeseries_anomaly.scale", scale.name());
            props.setProperty("smile.timeseries_anomaly.threshold", Double.toString(threshold));
            props.setProperty("smile.timeseries_anomaly.ewma_alpha", Double.toString(ewmaAlpha));
            return props;
        }

        /**
         * Returns the options from properties.
         *
         * @param props the hyperparameters.
         * @return the options.
         */
        public static Options of(Properties props) {
            Scale scale = Scale.valueOf(props.getProperty("smile.timeseries_anomaly.scale", "MAD"));
            double threshold = Double.parseDouble(
                    props.getProperty("smile.timeseries_anomaly.threshold", "3.0"));
            double alpha = Double.parseDouble(
                    props.getProperty("smile.timeseries_anomaly.ewma_alpha", "0.2"));
            return new Options(scale, threshold, alpha);
        }
    }

    /** The fitted model. */
    private final TimeSeriesModel model;
    /** The training history. */
    private final double[] history;
    /** The one-step-ahead residuals of the history. */
    private final double[] residuals;
    /** The standardization method. */
    private final Scale scale;
    /** The robust scale of the standardized residuals. */
    private final double sigma;
    /** The decision threshold in standardized units. */
    private final double threshold;
    /** The EWMA smoothing factor. */
    private final double ewmaAlpha;

    private TimeSeriesAnomaly(TimeSeriesModel model, double[] history, double[] residuals,
                              Scale scale, double sigma, double threshold, double ewmaAlpha) {
        this.model = model;
        this.history = history.clone();
        this.residuals = residuals.clone();
        this.scale = scale;
        this.sigma = sigma;
        this.threshold = threshold;
        this.ewmaAlpha = ewmaAlpha;
    }

    /**
     * Fits the detector from a history and an already-fitted model.
     *
     * @param history the training history.
     * @param model the fitted time-series model.
     * @param options the detector options.
     * @return the detector.
     */
    public static TimeSeriesAnomaly fit(double[] history, TimeSeriesModel model, Options options) {
        if (history == null || history.length < 4) {
            throw new IllegalArgumentException("History is too short: " + (history == null ? 0 : history.length));
        }
        if (model == null) {
            throw new IllegalArgumentException("Null model");
        }
        if (options == null) {
            throw new IllegalArgumentException("Null options");
        }

        double[] residuals = model.residuals();
        if (residuals == null || residuals.length < 3) {
            throw new IllegalArgumentException("The model provides too few residuals");
        }

        double sigma = robustScale(residuals, options.scale(), options.ewmaAlpha());
        if (!(sigma > 0.0) || !Double.isFinite(sigma)) {
            throw new IllegalArgumentException("Degenerate residual scale: " + sigma);
        }

        return new TimeSeriesAnomaly(model, history, residuals, options.scale(), sigma,
                options.threshold(), options.ewmaAlpha());
    }

    /**
     * Fits the detector with an ETS model selected automatically.
     *
     * @param history the training history.
     * @param options the detector options.
     * @return the detector.
     */
    public static TimeSeriesAnomaly fit(double[] history, Options options) {
        if (history == null || history.length < 8) {
            throw new IllegalArgumentException("History is too short: " + (history == null ? 0 : history.length));
        }
        return fit(history, ETS.auto(history, 1), options);
    }

    /**
     * Fits the detector with an ETS model selected automatically and the default options.
     *
     * @param history the training history.
     * @return the detector.
     */
    public static TimeSeriesAnomaly fit(double[] history) {
        return fit(history, new Options());
    }

    /**
     * Computes the robust scale of the residuals.
     *
     * @param residuals the residuals.
     * @param scale the estimator.
     * @param alpha the EWMA smoothing factor.
     * @return the scale.
     */
    static double robustScale(double[] residuals, Scale scale, double alpha) {
        return switch (scale) {
            case MAD -> {
                double mad = MathEx.mad(residuals.clone());
                yield MAD_CONSTANT * mad;
            }
            case STD -> Math.sqrt(MathEx.var(residuals));
            case EWMA -> {
                double variance = MathEx.pow2(residuals[0]);
                for (int i = 1; i < residuals.length; i++) {
                    variance = (1.0 - alpha) * variance + alpha * MathEx.pow2(residuals[i]);
                }
                yield Math.sqrt(variance);
            }
        };
    }

    /** Returns the fitted model. @return the model. */
    public TimeSeriesModel model() {
        return model;
    }

    /** Returns the training history. @return a copy of the history. */
    public double[] history() {
        return history.clone();
    }

    /** Returns the one-step residuals of the history. @return the residuals. */
    public double[] residuals() {
        return residuals.clone();
    }

    /** Returns the standardization method. @return the scale estimator. */
    public Scale scale() {
        return scale;
    }

    /** Returns the robust scale of the residuals. @return the sigma. */
    public double sigma() {
        return sigma;
    }

    /** Returns the decision threshold in standardized units. @return the threshold. */
    public double threshold() {
        return threshold;
    }

    /** Returns the EWMA smoothing factor. @return the EWMA alpha. */
    public double ewmaAlpha() {
        return ewmaAlpha;
    }

    /**
     * Scores the next observation against the stored history.
     * <p>
     * The forecast is produced by the fitted model from the history, and the
     * residual is standardized by {@link #sigma()}. The method does not mutate
     * the detector, so repeated calls return the same value.
     *
     * @param value the observation that follows the training history.
     * @return the standardized absolute residual.
     */
    public double score(double value) {
        double forecast;
        if (model instanceof ETS ets) {
            forecast = ets.filter(new double[]{value})[0];
        } else {
            forecast = model.forecast(1)[0];
        }
        double residual = value - forecast;
        return Math.abs(residual) / sigma;
    }

    /**
     * Scores a series sequentially with true one-step-ahead residuals.
     * <p>
     * Point {@code i} is scored against the model refitted to the training
     * history extended by points {@code 0..i-1}, so every score corresponds to
     * a genuine one-step forecast rather than a static multi-step projection.
     * The cost is therefore <code>O(n)</code> model fits, which is the price of
     * an exact rolling-origin evaluation; for long streams, score in windows.
     *
     * @param series the observations to score, in time order.
     * @return the standardized absolute residuals.
     */
    public double[] score(double[] series) {
        if (series == null || series.length == 0) {
            throw new IllegalArgumentException("Empty series");
        }

        if (model instanceof ETS ets) {
            // Filter the continuation from the training terminal state: this
            // keeps the seasonal phase contiguous and avoids a refit seam.
            double[] forecast = ets.filter(series);
            double[] scores = new double[series.length];
            for (int i = 0; i < series.length; i++) {
                scores[i] = Math.abs(series[i] - forecast[i]) / sigma;
            }
            return scores;
        }

        // Other models: extend the history and read the one-step residuals of
        // one refit. Residuals are one-step errors by construction, so no
        // per-observation refit is needed.
        double[] extended = new double[history.length + series.length];
        System.arraycopy(history, 0, extended, 0, history.length);
        System.arraycopy(series, 0, extended, history.length, series.length);

        TimeSeriesModel fitted;
        try {
            fitted = refit(extended);
        } catch (IllegalArgumentException ex) {
            fitted = model;
        }

        double[] residuals = fitted.residuals();
        if (residuals.length < series.length) {
            double[] scores = new double[series.length];
            double[] forecast = fitted.forecast(series.length);
            for (int i = 0; i < series.length; i++) {
                scores[i] = Math.abs(series[i] - forecast[i]) / sigma;
            }
            return scores;
        }

        double[] scores = new double[series.length];
        int offset = residuals.length - series.length;
        for (int i = 0; i < series.length; i++) {
            scores[i] = Math.abs(residuals[offset + i]) / sigma;
        }
        return scores;
    }

    private TimeSeriesModel refit(double[] training) {
        if (model instanceof ETS ets) {
            return ETS.fit(training, new ETS.Options(ets.period(), ets.trend(), ets.seasonal()));
        }
        if (model instanceof smile.timeseries.ARIMA arima) {
            return smile.timeseries.ARIMA.fit(training, arima.p(), arima.d(), arima.q());
        }
        if (model instanceof smile.timeseries.SARIMA sarima) {
            return smile.timeseries.SARIMA.fit(training, sarima.p(), sarima.d(), sarima.q(),
                    sarima.P(), sarima.D(), sarima.Q(), sarima.period());
        }
        if (model instanceof smile.timeseries.ARMA arma) {
            return smile.timeseries.ARMA.fit(training, arma.p(), arma.q());
        }
        if (model instanceof smile.timeseries.AR ar) {
            return smile.timeseries.AR.fit(training, ar.p());
        }
        return model;
    }

    /**
     * Predicts whether the observation that follows the history is an anomaly.
     *
     * @param value the observation.
     * @return true if the observation is anomalous.
     */
    public boolean predict(double value) {
        return score(value) > threshold;
    }

    /**
     * Predicts the anomaly flags of a series.
     *
     * @param series the observations, in time order.
     * @return the anomaly flags.
     */
    public boolean[] predict(double[] series) {
        double[] scores = score(series);
        boolean[] flags = new boolean[scores.length];
        for (int i = 0; i < scores.length; i++) {
            flags[i] = scores[i] > threshold;
        }
        return flags;
    }

    @Override
    public String toString() {
        return String.format(Locale.ROOT,
                "TimeSeriesAnomaly(model=%s, scale=%s, sigma=%.6f, threshold=%.3f, history=%d)",
                model.getClass().getSimpleName(), scale, sigma, threshold, history.length);
    }
}
