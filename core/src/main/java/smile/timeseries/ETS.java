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
import java.util.Locale;
import java.util.Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import smile.math.BFGS;
import smile.math.MathEx;
import smile.util.function.DifferentiableMultivariateFunction;

/**
 * Exponential smoothing state space model (ETS) with additive errors.
 * <p>
 * The model decomposes a series into level, trend, and seasonal states.
 * With <code>e_t = x_t - yhat_t</code> the one-step-ahead forecast and the
 * state recursion are:
 * <pre>
 * level    l_t = l_{t-1} + phi*b_{t-1} + alpha*e_t
 * trend    b_t = phi*b_{t-1} + beta*e_t
 * seasonal s_t = s_{t-m} + gamma*e_t                (additive)
 *          s_t = s_{t-m} + gamma*e_t / l_t          (multiplicative)
 * forecast yhat_t = l_{t-1} + phi*b_{t-1} + s_{t-m}      (additive seasonal)
 *          yhat_t = (l_{t-1} + phi*b_{t-1}) * s_{t-m}     (multiplicative seasonal)
 * </pre>
 * The seasonal recursion operates in-place on an array of the last <code>m</code>
 * seasonal states, so that element <code>t mod m</code> always holds
 * <code>s_{t-m}</code>. When there is no trend, <code>b</code> is fixed at 0 and
 * <code>phi</code> is 1. When the trend is not damped, <code>phi</code> is 1.
 * <p>
 * The smoothing parameters <code>alpha</code>, <code>beta</code>, <code>gamma</code>
 * and damping <code>phi</code> are estimated by minimizing the concentrated
 * Gaussian negative log-likelihood with {@link BFGS}. The admissible region is
 * <code>0 &lt;= alpha &lt;= 1</code>, <code>0 &lt;= beta &lt;= alpha</code>,
 * <code>0 &lt;= gamma &lt;= 1 - alpha</code> and <code>0.8 &lt;= phi &lt;= 0.99</code>
 * (matching the usual ETS admissibility constraints).
 * <p>
 * Only additive-error models are implemented. Multiplicative-error variants and
 * Box-Cox transformations are outside the scope of this class.
 *
 * <h2>References</h2>
 * <ol>
 * <li>Rob J. Hyndman, Anne B. Koehler, J. Keith Ord, and Ralph D. Snyder.
 *     Forecasting with Exponential Smoothing: The State Space Approach. Springer, 2008.</li>
 * <li>Everette S. Gardner Jr. Exponential smoothing: The state of the art—Part II.
 *     International Journal of Forecasting, 22(4):637–666, 2006.</li>
 * </ol>
 *
 * @author Haifeng Li
 */
public class ETS implements TimeSeriesModel {
    @Serial
    private static final long serialVersionUID = 1L;
    private static final Logger logger = LoggerFactory.getLogger(ETS.class);

    /** The trend component. */
    public enum Trend {
        /** No trend. */
        NONE,
        /** Additive (linear) trend. */
        ADDITIVE,
        /** Additive trend damped by phi. */
        ADDITIVE_DAMPED
    }

    /** The seasonal component. */
    public enum Seasonal {
        /** No seasonality. */
        NONE,
        /** Additive seasonality. */
        ADDITIVE,
        /** Multiplicative seasonality; requires strictly positive data. */
        MULTIPLICATIVE
    }

    /**
     * Hyperparameters of ETS.
     *
     * @param period   the seasonal period; 1 for non-seasonal data.
     * @param trend    the trend component.
     * @param seasonal the seasonal component.
     */
    public record Options(int period, Trend trend, Seasonal seasonal) {
        /**
         * Constructor.
         */
        public Options {
            if (period < 1) {
                throw new IllegalArgumentException("Invalid period: " + period);
            }
            if (trend == null || seasonal == null) {
                throw new IllegalArgumentException("Null trend or seasonal component");
            }
        }

        /** Default constructor: no trend, no seasonality. */
        public Options() {
            this(1, Trend.NONE, Seasonal.NONE);
        }

        /**
         * Returns the persistent set of hyperparameters.
         *
         * @return the persistent set.
         */
        public Properties toProperties() {
            Properties props = new Properties();
            props.setProperty("smile.ets.period", Integer.toString(period));
            props.setProperty("smile.ets.trend", trend.name());
            props.setProperty("smile.ets.seasonal", seasonal.name());
            return props;
        }

        /**
         * Returns the options from properties.
         *
         * @param props the hyperparameters.
         * @return the options.
         */
        public static Options of(Properties props) {
            int period = Integer.parseInt(props.getProperty("smile.ets.period", "1"));
            Trend trend = Trend.valueOf(props.getProperty("smile.ets.trend", "ADDITIVE"));
            Seasonal seasonal = Seasonal.valueOf(props.getProperty("smile.ets.seasonal", "NONE"));
            return new Options(period, trend, seasonal);
        }
    }

    /** The observed time series. */
    private final double[] x;
    /** The seasonal period. */
    private final int period;
    /** The trend component type. */
    private final Trend trend;
    /** The seasonal component type. */
    private final Seasonal seasonal;
    /** The level smoothing parameter. */
    private final double alpha;
    /** The trend smoothing parameter. */
    private final double beta;
    /** The seasonal smoothing parameter. */
    private final double gamma;
    /** The trend damping parameter. */
    private final double phi;
    /** The initial level state. */
    private final double level0;
    /** The initial trend state. */
    private final double trend0;
    /** The initial seasonal states. */
    private final double[] seasonal0;
    /** The in-sample one-step-ahead fitted values. */
    private final double[] fittedValues;
    /** The in-sample one-step-ahead residuals. */
    private final double[] residuals;
    /** The residual variance. */
    private final double sigma2;
    /** The Gaussian log-likelihood. */
    private final double logLikelihood;
    /** The Akaike information criterion. */
    private final double aic;
    /** The bias-corrected Akaike information criterion. */
    private final double aicc;
    /** The Bayesian information criterion. */
    private final double bic;
    /** The terminal level state. */
    private final double lastLevel;
    /** The terminal trend state. */
    private final double lastTrend;
    /** The terminal seasonal states. */
    private final double[] lastSeasonal;

    /**
     * Constructor.
     */
    private ETS(double[] x, int period, Trend trend, Seasonal seasonal,
                double alpha, double beta, double gamma, double phi,
                double level0, double trend0, double[] seasonal0,
                double[] fittedValues, double[] residuals,
                double sigma2, double logLikelihood, double aic, double aicc, double bic,
                States terminal) {
        this.x = x;
        this.period = period;
        this.trend = trend;
        this.seasonal = seasonal;
        this.alpha = alpha;
        this.beta = beta;
        this.gamma = gamma;
        this.phi = phi;
        this.level0 = level0;
        this.trend0 = trend0;
        this.seasonal0 = seasonal0;
        this.fittedValues = fittedValues;
        this.residuals = residuals;
        this.sigma2 = sigma2;
        this.logLikelihood = logLikelihood;
        this.aic = aic;
        this.aicc = aicc;
        this.bic = bic;
        this.lastLevel = terminal.level;
        this.lastTrend = terminal.trend;
        this.lastSeasonal = terminal.seasonal.clone();
    }

    /**
     * Fits an ETS model.
     *
     * @param x the time series.
     * @param options the model structure.
     * @return the fitted model.
     */
    public static ETS fit(double[] x, Options options) {
        validate(x, options);

        boolean hasTrend = options.trend() != Trend.NONE;
        boolean damped = options.trend() == Trend.ADDITIVE_DAMPED;
        boolean hasSeasonal = options.seasonal() != Seasonal.NONE;
        boolean multiplicative = options.seasonal() == Seasonal.MULTIPLICATIVE;
        int m = hasSeasonal ? options.period() : 1;
        int n = x.length;

        // ----- initial states -------------------------------------------------
        int init = Math.min(n, Math.max(1, m));
        double level = 0.0;
        for (int i = 0; i < init; i++) {
            level += x[i];
        }
        level /= init;

        double b = 0.0;
        if (hasTrend) {
            if (2 * m <= n) {
                double a = 0.0;
                double c = 0.0;
                for (int i = 0; i < m; i++) {
                    a += x[i];
                    c += x[m + i];
                }
                b = (c - a) / (m * m);
            } else if (n > 1) {
                b = (x[n - 1] - x[0]) / (n - 1);
            }
        }

        double[] s = new double[m];
        if (hasSeasonal) {
            double[] sums = new double[m];
            int[] counts = new int[m];
            for (int i = 0; i < n; i++) {
                sums[i % m] += x[i];
                counts[i % m]++;
            }
            if (multiplicative) {
                double mean = 0.0;
                for (int j = 0; j < m; j++) {
                    s[j] = sums[j] / counts[j] / level;
                    mean += s[j];
                }
                mean /= m;
                for (int j = 0; j < m; j++) {
                    s[j] /= mean;
                }
            } else {
                for (int j = 0; j < m; j++) {
                    s[j] = sums[j] / counts[j] - level;
                }
            }
        }

        // ----- estimate smoothing parameters ---------------------------------
        boolean[] active = {true, hasTrend, hasSeasonal, damped};
        int nParams = 0;
        for (boolean flag : active) {
            if (flag) nParams++;
        }

        double[] p = new double[nParams];
        double[] lower = new double[nParams];
        double[] upper = new double[nParams];
        int k = 0;
        p[k] = 0.2; lower[k] = 1E-4; upper[k] = 0.999; k++;      // alpha
        if (hasTrend) { p[k] = 0.1; lower[k] = 0.0; upper[k] = 0.999; k++; }  // beta
        if (hasSeasonal) { p[k] = 0.1; lower[k] = 0.0; upper[k] = 0.999; k++; } // gamma
        if (damped) { p[k] = 0.9; lower[k] = 0.8; upper[k] = 0.99; k++; }     // phi

        States initStates = new States(level, b, s.clone(), new double[n], new double[n]);
        Objective objective = new Objective(x, m, hasTrend, damped, hasSeasonal,
                multiplicative, initStates, nParams);

        if (nParams == 1) {
            // One parameter: the bounded optimizer needs at least two; use a
            // coarse golden-section style scan instead.
            p[0] = scanAlpha(objective, lower[0], upper[0]);
        } else {
            try {
                BFGS.minimize(objective, nParams, p, lower, upper, 1E-8, 500);
            } catch (RuntimeException ex) {
                logger.warn("ETS parameter estimation did not converge: {}", ex.getMessage());
            }
        }

        // Re-run the recursion at the final parameters to capture states.
        States states = objective.evaluate(p);
        double sse = 0.0;
        for (int i = 0; i < n; i++) {
            sse += MathEx.pow2(states.residuals[i]);
        }

        double sigma2 = sse / n;
        double ll = -0.5 * n * (Math.log(2 * Math.PI) + 1.0 + Math.log(Math.max(sigma2, 1E-300)));
        int nState = (hasTrend ? 2 : 1) + (hasSeasonal ? m : 0);
        int pcount = nState + nParams;
        double aic = 2 * pcount - 2 * ll;
        double aicc = n - pcount - 1 > 0 ? aic + 2.0 * pcount * (pcount + 1) / (n - pcount - 1) : Double.POSITIVE_INFINITY;
        double bic = pcount * Math.log(n) - 2 * ll;

        return new ETS(x, m, options.trend(), options.seasonal(),
                p[0],
                hasTrend ? p[1] : 0.0,
                hasSeasonal ? p[hasTrend ? 2 : 1] : 0.0,
                damped ? p[nParams - 1] : 1.0,
                level, b, initStates.seasonal,
                states.fitted, states.residuals, sigma2, ll, aic, aicc, bic, states);
    }

    /**
     * Fits an ETS model with automatic selection of the trend and seasonal
     * components by AICc.
     *
     * @param x the time series.
     * @param period the seasonal period; 1 for non-seasonal data.
     * @return the best model by AICc.
     */
    public static ETS auto(double[] x, int period) {
        if (period < 1) {
            throw new IllegalArgumentException("Invalid period: " + period);
        }
        if (x == null || x.length < 4) {
            throw new IllegalArgumentException("Time series is too short: " + (x == null ? 0 : x.length));
        }

        boolean positive = true;
        for (double v : x) {
            if (v <= 0.0) {
                positive = false;
                break;
            }
        }

        ETS best = null;
        Trend[] trends = {Trend.NONE, Trend.ADDITIVE, Trend.ADDITIVE_DAMPED};
        Seasonal[] seasonals = {Seasonal.NONE, Seasonal.ADDITIVE, Seasonal.MULTIPLICATIVE};

        for (Trend t : trends) {
            for (Seasonal s : seasonals) {
                if (s != Seasonal.NONE && (period < 2 || x.length < 2 * period)) {
                    continue;
                }
                if (s == Seasonal.MULTIPLICATIVE && !positive) {
                    continue;
                }
                try {
                    ETS model = fit(x, new Options(period, t, s));
                    if (best == null || model.aicc() < best.aicc()) {
                        best = model;
                    }
                } catch (IllegalArgumentException ex) {
                    logger.debug("ETS candidate ({}, {}) rejected: {}", t, s, ex.getMessage());
                }
            }
        }

        if (best == null) {
            throw new IllegalArgumentException("No admissible ETS model for the series");
        }
        return best;
    }

    private static double scanAlpha(Objective objective, double lower, double upper) {
        double best = lower;
        double bestValue = Double.POSITIVE_INFINITY;
        int steps = 50;
        for (int i = 0; i <= steps; i++) {
            double v = lower + (upper - lower) * i / steps;
            double f = objective.f(new double[]{v});
            if (f < bestValue) {
                bestValue = f;
                best = v;
            }
        }
        return best;
    }

    private static void validate(double[] x, Options options) {
        if (x == null || x.length < 4) {
            throw new IllegalArgumentException("Time series is too short: " + (x == null ? 0 : x.length));
        }
        if (options.seasonal() != Seasonal.NONE) {
            int m = options.period();
            if (m < 2) {
                throw new IllegalArgumentException("Seasonal period must be at least 2: " + m);
            }
            if (x.length < 2 * m) {
                throw new IllegalArgumentException(String.format(
                        "Time series is too short for seasonal period %d: length=%d", m, x.length));
            }
            if (options.seasonal() == Seasonal.MULTIPLICATIVE) {
                for (double v : x) {
                    if (v <= 0.0) {
                        throw new IllegalArgumentException("Multiplicative seasonality requires positive data");
                    }
                }
            }
        }
    }

    /** Mutable state bundle used by the objective. */
    private static final class States {
        double level;
        double trend;
        double[] seasonal;
        double[] fitted;
        double[] residuals;

        States(double level, double trend, double[] seasonal, double[] fitted, double[] residuals) {
            this.level = level;
            this.trend = trend;
            this.seasonal = seasonal;
            this.fitted = fitted;
            this.residuals = residuals;
        }
    }

    /** Concentrated negative log-likelihood over the active smoothing parameters. */
    private static final class Objective implements DifferentiableMultivariateFunction {
        private final double[] x;
        private final int m;
        private final boolean hasTrend;
        private final boolean damped;
        private final boolean hasSeasonal;
        private final boolean multiplicative;
        private final States init;
        private final int nParams;

        Objective(double[] x, int m, boolean hasTrend, boolean damped, boolean hasSeasonal,
                  boolean multiplicative, States init, int nParams) {
            this.x = x;
            this.m = m;
            this.hasTrend = hasTrend;
            this.damped = damped;
            this.hasSeasonal = hasSeasonal;
            this.multiplicative = multiplicative;
            this.init = init;
            this.nParams = nParams;
        }

        @Override
        public double f(double[] p) {
            double alpha = p[0];
            double beta = hasTrend ? p[1] : 0.0;
            double gamma = hasSeasonal ? p[hasTrend ? 2 : 1] : 0.0;
            double phi = damped ? p[nParams - 1] : 1.0;

            // Admissibility penalty for the cross constraints.
            double penalty = 0.0;
            if (beta > alpha) {
                penalty += 1E6 * MathEx.pow2(beta - alpha);
            }
            if (gamma > 1.0 - alpha) {
                penalty += 1E6 * MathEx.pow2(gamma - (1.0 - alpha));
            }

            States states = run(x, m, hasTrend, damped, hasSeasonal, multiplicative,
                    alpha, beta, gamma, phi, init);
            double sse = 0.0;
            for (double e : states.residuals) {
                sse += MathEx.pow2(e);
            }
            if (sse <= 0.0 || !Double.isFinite(sse)) {
                return Double.POSITIVE_INFINITY;
            }
            return 0.5 * x.length * Math.log(sse / x.length) + penalty;
        }

        States evaluate(double[] p) {
            double alpha = p[0];
            double beta = hasTrend ? p[1] : 0.0;
            double gamma = hasSeasonal ? p[hasTrend ? 2 : 1] : 0.0;
            double phi = damped ? p[nParams - 1] : 1.0;
            return run(x, m, hasTrend, damped, hasSeasonal, multiplicative,
                    alpha, beta, gamma, phi, init);
        }
    }

    private static States run(double[] x, int m, boolean hasTrend, boolean damped,
                              boolean hasSeasonal, boolean multiplicative,
                              double alpha, double beta, double gamma, double phi,
                              States init) {
        int n = x.length;
        double level = init.level;
        double b = init.trend;
        double[] s = init.seasonal.clone();
        double[] fitted = new double[n];
        double[] residuals = new double[n];

        for (int t = 0; t < n; t++) {
            int si = hasSeasonal ? t % m : 0;
            double trendTerm = hasTrend ? (damped ? phi * b : b) : 0.0;
            double forecast;
            if (multiplicative) {
                forecast = (level + trendTerm) * s[si];
            } else {
                forecast = level + trendTerm + (hasSeasonal ? s[si] : 0.0);
            }

            double e = x[t] - forecast;
            fitted[t] = forecast;
            residuals[t] = e;

            double newLevel = level + trendTerm + alpha * e;
            double newTrend = hasTrend ? (damped ? phi * b + beta * e : b + beta * e) : 0.0;
            if (hasSeasonal) {
                if (multiplicative) {
                    double denom = Math.abs(newLevel) < 1E-12 ? Math.copySign(1E-12, newLevel == 0.0 ? 1.0 : newLevel) : newLevel;
                    s[si] = s[si] + gamma * e / denom;
                } else {
                    s[si] = s[si] + gamma * e;
                }
            }

            level = newLevel;
            b = newTrend;
        }

        States states = new States(level, b, s, fitted, residuals);
        return states;
    }

    /**
     * Returns the smoothing parameters alpha, beta, gamma, phi.
     * @return the smoothing parameters {@code [alpha, beta, gamma, phi]}.
     */
    public double[] smoothingParameters() {
        return new double[]{alpha, beta, gamma, phi};
    }

    /**
     * Returns the smoothing level parameter.
     * @return alpha.
     */
    public double alpha() {
        return alpha;
    }

    /**
     * Returns the smoothing trend parameter.
     * @return beta.
     */
    public double beta() {
        return beta;
    }

    /**
     * Returns the smoothing seasonal parameter.
     * @return gamma.
     */
    public double gamma() {
        return gamma;
    }

    /**
     * Returns the damping parameter; 1 for undamped models.
     * @return phi.
     */
    public double phi() {
        return phi;
    }

    /**
     * Returns the seasonal period; 1 when there is no seasonality.
     * @return the seasonal period.
     */
    public int period() {
        return period;
    }

    /**
     * Returns the trend component.
     * @return the trend component.
     */
    public Trend trend() {
        return trend;
    }

    /**
     * Returns the seasonal component.
     * @return the seasonal component.
     */
    public Seasonal seasonal() {
        return seasonal;
    }

    /**
     * Returns the initial level state.
     * @return the initial level.
     */
    public double level0() {
        return level0;
    }

    /**
     * Returns the initial trend state.
     * @return the initial trend.
     */
    public double trend0() {
        return trend0;
    }

    /**
     * Returns the initial seasonal states.
     * @return a copy of the initial seasonal states.
     */
    public double[] seasonal0() {
        return seasonal0.clone();
    }

    @Override
    public double[] fittedValues() {
        return fittedValues;
    }

    @Override
    public double[] residuals() {
        return residuals;
    }

    @Override
    public double variance() {
        return sigma2;
    }

    @Override
    public double logLikelihood() {
        return logLikelihood;
    }

    @Override
    public double aic() {
        return aic;
    }

    /**
     * Returns the corrected Akaike information criterion.
     * @return the AICc.
     */
    public double aicc() {
        return aicc;
    }

    @Override
    public double bic() {
        return bic;
    }

    /**
     * Filters a continuation of the series with the fitted smoothing constants,
     * starting from the terminal state of the training series. This is the
     * correct way to obtain one-step forecasts for new observations: unlike a
     * refit, it does not re-estimate the initial states and therefore does not
     * shift the seasonal phase at the junction.
     *
     * @param series the continuation, in time order.
     * @return the one-step-ahead forecasts for the continuation.
     */
    public double[] filter(double[] series) {
        if (series == null) {
            throw new IllegalArgumentException("Null series");
        }

        boolean damped = trend == Trend.ADDITIVE_DAMPED;
        boolean hasTrend = trend != Trend.NONE;
        boolean hasSeasonal = seasonal != Seasonal.NONE;
        boolean multiplicative = seasonal == Seasonal.MULTIPLICATIVE;
        int m = hasSeasonal ? period : 1;

        double level = lastLevel;
        double b = lastTrend;
        double[] s = lastSeasonal.clone();

        double[] forecasts = new double[series.length];
        for (int t = 0; t < series.length; t++) {
            int si = hasSeasonal ? (x.length + t) % m : 0;
            double trendTerm = hasTrend ? (damped ? phi * b : b) : 0.0;
            double forecast = multiplicative
                    ? (level + trendTerm) * s[si]
                    : level + trendTerm + (hasSeasonal ? s[si] : 0.0);
            forecasts[t] = forecast;

            double e = series[t] - forecast;
            double newLevel = level + trendTerm + alpha * e;
            double newTrend = hasTrend ? (damped ? phi * b + beta * e : b + beta * e) : 0.0;
            if (hasSeasonal) {
                if (multiplicative) {
                    double denom = Math.abs(newLevel) < 1E-12 ? 1E-12 : newLevel;
                    s[si] = s[si] + gamma * e / denom;
                } else {
                    s[si] = s[si] + gamma * e;
                }
            }
            level = newLevel;
            b = newTrend;
        }

        return forecasts;
    }

    @Override
    public double[] forecast(int steps) {
        if (steps <= 0) {
            throw new IllegalArgumentException("Invalid forecast horizon l = " + steps);
        }

        boolean damped = trend == Trend.ADDITIVE_DAMPED;
        boolean hasTrend = trend != Trend.NONE;
        boolean hasSeasonal = seasonal != Seasonal.NONE;
        boolean multiplicative = seasonal == Seasonal.MULTIPLICATIVE;
        int m = hasSeasonal ? period : 1;

        double level = lastLevel;
        double b = lastTrend;
        double[] s = lastSeasonal.clone();

        double[] forecasts = new double[steps];
        for (int h = 1; h <= steps; h++) {
            double trendTerm = hasTrend ? (damped ? phi * b : b) : 0.0;
            int si = hasSeasonal ? (x.length + h - 1) % m : 0;
            forecasts[h - 1] = multiplicative
                    ? (level + trendTerm) * s[si]
                    : level + trendTerm + (hasSeasonal ? s[si] : 0.0);

            level = level + trendTerm;
            b = damped ? phi * b : b;
        }

        return forecasts;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("ETS(");
        sb.append(trend == Trend.NONE ? "N" : (trend == Trend.ADDITIVE_DAMPED ? "Ad" : "A"))
                .append(',')
                .append(seasonal == Seasonal.NONE ? "N" : (seasonal == Seasonal.ADDITIVE ? "A" : "M"))
                .append(',')
                .append(seasonal == Seasonal.NONE ? "N" : "A")
                .append(")\n");

        sb.append(String.format(Locale.ROOT, "%nSmoothing parameters:%n"));
        sb.append(String.format(Locale.ROOT, "  alpha  %10.6f%n", alpha));
        if (trend != Trend.NONE) {
            sb.append(String.format(Locale.ROOT, "  beta   %10.6f%n", beta));
        }
        if (seasonal != Seasonal.NONE) {
            sb.append(String.format(Locale.ROOT, "  gamma  %10.6f%n", gamma));
        }
        if (trend == Trend.ADDITIVE_DAMPED) {
            sb.append(String.format(Locale.ROOT, "  phi    %10.6f%n", phi));
        }

        double[] r = residuals.clone();
        sb.append(String.format(Locale.ROOT, "%nResiduals:%n"));
        sb.append(String.format(Locale.ROOT, "       Min          1Q      Median          3Q         Max%n"));
        sb.append(String.format(Locale.ROOT, "%10.4f  %10.4f  %10.4f  %10.4f  %10.4f%n",
                MathEx.min(r), MathEx.q1(r), MathEx.median(r), MathEx.q3(r), MathEx.max(r)));
        sb.append(String.format(Locale.ROOT, "%nAIC: %.4f  AICc: %.4f  BIC: %.4f", aic, aicc, bic));
        return sb.toString();
    }
}
