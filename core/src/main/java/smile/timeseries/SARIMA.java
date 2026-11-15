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
import java.util.Arrays;
import java.util.Locale;
import smile.math.MathEx;

/**
 * Seasonal autoregressive integrated moving average model
 * SARIMA(p, d, q)(P, D, Q)[s].
 * <p>
 * The model generalizes {@link ARIMA} by adding a seasonal (integrated)
 * component. A series is first seasonally differenced <code>D</code> times at
 * lag <code>s</code>, then regularly differenced <code>d</code> times, and a
 * {@link SARMA} model is fitted to the resulting stationary series:
 * <pre>
 * y_t = (1 - B)^d (1 - B^s)^D x_t
 * </pre>
 * Forecasts are produced on the differenced series and integrated back
 * layer by layer in reverse order, exactly as {@link ARIMA} does for the
 * non-seasonal layers.
 * <p>
 * The same representation caveat as {@link SARMA} applies: seasonal
 * coefficients are direct lag coefficients (<code>sar()[j]</code> multiplies
 * <code>x_{t-(j+1)s}</code>), not the factored polynomial form.
 * <p>
 * When <code>d = D = 0</code> the model is a pure SARMA; when additionally
 * <code>P = Q = 0</code> it reduces to an ARIMA model fitted on the same data.
 *
 * <h2>References</h2>
 * <ol>
 * <li>George E. P. Box, Gwilym M. Jenkins, Gregory C. Reinsel, and Greta M. Ljung.
 *     Time Series Analysis: Forecasting and Control. 5th edition, Wiley, 2015.</li>
 * <li>Peter J. Brockwell and Richard A. Davis. Introduction to Time Series and
 *     Forecasting. 3rd edition, Springer, 2016.</li>
 * </ol>
 *
 * @author Haifeng Li
 */
public class SARIMA implements TimeSeriesModel {
    @Serial
    private static final long serialVersionUID = 1L;

    private final double[] x;
    private final int p;
    private final int d;
    private final int q;
    private final int P;
    private final int D;
    private final int Q;
    private final int s;
    private final SARMA sarma;
    private final double[][] regularDiff;
    private final double[][] seasonalDiff;
    private final double[] fittedValues;
    private final double[] residuals;

    /**
     * Constructor.
     *
     * @param x the original time series.
     * @param p the non-seasonal AR order.
     * @param d the non-seasonal differencing order.
     * @param q the non-seasonal MA order.
     * @param P the seasonal AR order.
     * @param D the seasonal differencing order.
     * @param Q the seasonal MA order.
     * @param s the seasonal period.
     * @param sarma the SARMA model on the fully differenced series.
     * @param regularDiff the intermediate regular differencing stages.
     * @param seasonalDiff the intermediate seasonal differencing stages.
     */
    public SARIMA(double[] x, int p, int d, int q, int P, int D, int Q, int s,
                  SARMA sarma, double[][] regularDiff, double[][] seasonalDiff) {
        this.x = x;
        this.p = p;
        this.d = d;
        this.q = q;
        this.P = P;
        this.D = D;
        this.Q = Q;
        this.s = s;
        this.sarma = sarma;
        this.regularDiff = regularDiff;
        this.seasonalDiff = seasonalDiff;

        this.residuals = sarma.residuals();
        int n = residuals.length;
        this.fittedValues = new double[n];
        for (int i = 0; i < n; i++) {
            int offset = x.length - n + i;
            this.fittedValues[i] = x[offset] - residuals[i];
        }
    }

    /**
     * Fits a SARIMA model.
     *
     * @param x the time series.
     * @param p the non-seasonal AR order.
     * @param d the non-seasonal differencing order.
     * @param q the non-seasonal MA order.
     * @param P the seasonal AR order.
     * @param D the seasonal differencing order.
     * @param Q the seasonal MA order.
     * @param s the seasonal period.
     * @return the fitted model.
     */
    public static SARIMA fit(double[] x, int p, int d, int q, int P, int D, int Q, int s) {
        if (x == null || x.length < 4) {
            throw new IllegalArgumentException("Time series is too short: " + (x == null ? 0 : x.length));
        }
        if (p < 0 || p >= x.length) {
            throw new IllegalArgumentException("Invalid order p = " + p);
        }
        if (d < 0 || d >= x.length) {
            throw new IllegalArgumentException("Invalid differencing order d = " + d);
        }
        if (q < 0 || q >= x.length) {
            throw new IllegalArgumentException("Invalid order q = " + q);
        }
        if (P < 0) {
            throw new IllegalArgumentException("Invalid seasonal order P = " + P);
        }
        if (D < 0) {
            throw new IllegalArgumentException("Invalid seasonal differencing order D = " + D);
        }
        if (Q < 0) {
            throw new IllegalArgumentException("Invalid seasonal order Q = " + Q);
        }
        if (p == 0 && q == 0 && P == 0 && Q == 0 && d == 0 && D == 0) {
            throw new IllegalArgumentException("All AR/MA orders and differencing orders cannot be 0");
        }
        if ((P > 0 || Q > 0 || D > 0) && s < 2) {
            throw new IllegalArgumentException("Invalid seasonal period s = " + s);
        }

        double[] stationary = x;
        double[][] seasonalDiff = null;
        if (D > 0) {
            if ((long) s * D >= x.length) {
                throw new IllegalArgumentException(String.format(
                        "Seasonal differencing too deep: s=%d, D=%d, length=%d", s, D, x.length));
            }
            seasonalDiff = TimeSeries.diff(x, s, D);
            stationary = seasonalDiff[D - 1];
        }

        double[][] regularDiff = null;
        if (d > 0) {
            if (d >= stationary.length) {
                throw new IllegalArgumentException("Invalid differencing order d = " + d);
            }
            regularDiff = TimeSeries.diff(stationary, 1, d);
            stationary = regularDiff[d - 1];
        }

        SARMA sarma = SARMA.fit(stationary, p, q, P, Q, s);
        return new SARIMA(x, p, d, q, P, D, Q, s, sarma, regularDiff, seasonalDiff);
    }

    /** Returns the original time series. @return the time series. */
    public double[] x() {
        return x;
    }

    /** Returns the non-seasonal AR order. @return p. */
    public int p() {
        return p;
    }

    /** Returns the non-seasonal differencing order. @return d. */
    public int d() {
        return d;
    }

    /** Returns the non-seasonal MA order. @return q. */
    public int q() {
        return q;
    }

    /** Returns the seasonal AR order. @return P. */
    public int P() {
        return P;
    }

    /** Returns the seasonal differencing order. @return D. */
    public int D() {
        return D;
    }

    /** Returns the seasonal MA order. @return Q. */
    public int Q() {
        return Q;
    }

    /** Returns the seasonal period. @return s. */
    public int period() {
        return s;
    }

    /** Returns the underlying SARMA model on the differenced series. @return the SARMA model. */
    public SARMA sarma() {
        return sarma;
    }

    /** Returns the non-seasonal AR coefficients. @return the AR coefficients. */
    public double[] ar() {
        return sarma.ar();
    }

    /** Returns the seasonal AR coefficients. @return the SAR coefficients. */
    public double[] sar() {
        return sarma.sar();
    }

    /** Returns the non-seasonal MA coefficients. @return the MA coefficients. */
    public double[] ma() {
        return sarma.ma();
    }

    /** Returns the seasonal MA coefficients. @return the SMA coefficients. */
    public double[] sma() {
        return sarma.sma();
    }

    /** Returns the intercept. @return the intercept. */
    public double intercept() {
        return sarma.intercept();
    }

    /** Returns the mean of the fitted (differenced) series. @return the mean. */
    public double mean() {
        return MathEx.mean(x);
    }

    @Override
    public double[] fittedValues() {
        return fittedValues;
    }

    @Override
    public double[] residuals() {
        return residuals;
    }

    /** Returns the residual sum of squares. @return the RSS. */
    public double RSS() {
        return sarma.RSS();
    }

    @Override
    public double variance() {
        return sarma.variance();
    }

    /** Returns the residual degrees of freedom. @return the degrees of freedom. */
    public int df() {
        return sarma.df();
    }

    /** Returns the coefficient of determination. @return R². */
    public double R2() {
        return sarma.R2();
    }

    /** Returns the t-tests of the coefficients. @return the t-tests. */
    public double[][] ttest() {
        return sarma.ttest();
    }

    @Override
    public double logLikelihood() {
        return sarma.logLikelihood();
    }

    @Override
    public double aic() {
        return sarma.aic();
    }

    @Override
    public double bic() {
        return sarma.bic();
    }

    /**
     * Returns the one-step ahead forecast on the original scale.
     * @return the 1-step ahead forecast.
     */
    public double forecast() {
        return forecast(1)[0];
    }

    @Override
    public double[] forecast(int steps) {
        if (steps <= 0) {
            throw new IllegalArgumentException("Invalid forecast horizon l = " + steps);
        }

        double[] forecasts = sarma.forecast(steps);

        // Invert the regular differencing, deepest layer first.
        for (int level = d - 1; level >= 0; level--) {
            double[] series = baseRegularSeries(level);
            invert(series, forecasts, 1);
        }

        // Invert the seasonal differencing on top of the regular layers.
        for (int level = D - 1; level >= 0; level--) {
            double[] series = baseSeasonalSeries(level);
            invert(series, forecasts, s);
        }

        return forecasts;
    }

    private double[] baseRegularSeries(int level) {
        if (level == 0) {
            return D == 0 ? x : seasonalDiff[D - 1];
        }
        return regularDiff[level - 1];
    }

    private double[] baseSeasonalSeries(int level) {
        return level == 0 ? x : seasonalDiff[level - 1];
    }

    /**
     * Inverts a differencing pass of the given lag: {@code z_t = s_t - s_{t-lag}}
     * is cumulated back onto the last {@code lag} observations of {@code series}.
     */
    private static void invert(double[] series, double[] forecasts, int lag) {
        int n = series.length;
        if (lag >= n) {
            throw new IllegalArgumentException("Differencing lag exceeds series length");
        }

        for (int i = 0; i < forecasts.length; i++) {
            double previous = i < lag ? series[n - lag + i] : forecasts[i - lag];
            forecasts[i] = previous + forecasts[i];
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(String.format(
                "SARIMA(%d, %d, %d)(%d, %d, %d)[%d]:%n", p, d, q, P, D, Q, s));

        double[] r = Arrays.copyOf(residuals, residuals.length);
        Arrays.sort(r);
        sb.append(String.format(Locale.ROOT, "%nResiduals:%n"));
        sb.append("       Min          1Q      Median          3Q         Max\n");
        sb.append(String.format(Locale.ROOT, "%10.4f  %10.4f  %10.4f  %10.4f  %10.4f%n",
                MathEx.min(r), MathEx.q1(r), MathEx.median(r), MathEx.q3(r), MathEx.max(r)));

        sb.append(String.format(Locale.ROOT, "%nCoefficients:%n"));
        double[][] ttest = sarma.ttest();
        if (ttest != null) {
            sb.append("              Estimate Std. Error    t value   Pr(>|t|)\n");
            double b = intercept();
            if (b != 0.0) {
                sb.append(String.format(Locale.ROOT, "Intercept   %10.4f%n", b));
            }
            String[] labels = coefficientLabels();
            for (int i = 0; i < ttest.length; i++) {
                sb.append(String.format(Locale.ROOT, "%s\t    %10.4f %10.4f %10.4f %10.4f%n",
                        labels[i], ttest[i][0], ttest[i][1], ttest[i][2], ttest[i][3]));
            }
        }

        sb.append(String.format(Locale.ROOT, "%nAIC: %.4f  BIC: %.4f  Log-Likelihood: %.4f",
                aic(), bic(), logLikelihood()));
        return sb.toString();
    }

    private String[] coefficientLabels() {
        String[] labels = new String[p + P + q + Q];
        int i = 0;
        for (int j = 0; j < p; j++) {
            labels[i++] = "ar[-" + (j + 1) + "]";
        }
        for (int j = 0; j < P; j++) {
            labels[i++] = "sar[-" + (j + 1) * s + "]";
        }
        for (int j = 0; j < q; j++) {
            labels[i++] = "ma[-" + (j + 1) + "]";
        }
        for (int j = 0; j < Q; j++) {
            labels[i++] = "sma[-" + (j + 1) * s + "]";
        }
        return labels;
    }
}
