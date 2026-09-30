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
import smile.math.MathEx;
import smile.stat.Hypothesis;

/**
 * Autoregressive integrated moving average (ARIMA) model.
 * An ARIMA model generalizes the autoregressive moving-average (ARMA) model
 * to non-stationary time series by applying differencing one or more times
 * to eliminate trend and achieve stationarity.
 * <p>
 * The model is conventionally denoted as <code>ARIMA(p, d, q)</code>, where:
 * <ul>
 *   <li><code>p</code> is the order of the autoregressive (AR) part,</li>
 *   <li><code>d</code> is the degree of differencing (the integrated part),</li>
 *   <li><code>q</code> is the order of the moving-average (MA) part.</li>
 * </ul>
 * <p>
 * Given a time series \(X_t\), the differenced series is:
 * \[
 * Y_t = (1 - B)^d X_t = \Delta^d X_t
 * \]
 * where \(B\) is the backshift operator (\(B X_t = X_{t-1}\)). An \(\text{ARMA}(p, q)\)
 * process is then fitted to \(Y_t\):
 * \[
 * Y_t = c + \sum_{i=1}^p \phi_i Y_{t-i} + \epsilon_t + \sum_{j=1}^q \theta_j \epsilon_{t-j}
 * \]
 * When forecasting future values, predictions are first generated on the differenced
 * series and then integrated (undifferenced) back to the original level of the series.
 *
 * <h2>References</h2>
 * <ol>
 * <li>George E. P. Box, Gwilym M. Jenkins, Gregory C. Reinsel, and Greta M. Ljung. Time Series Analysis: Forecasting and Control. 5th edition, John Wiley &amp; Sons, 2015.</li>
 * <li>Peter J. Brockwell and Richard A. Davis. Introduction to Time Series and Forecasting. 3rd edition, Springer, 2016.</li>
 * </ol>
 *
 * @author Haifeng Li
 */
public class ARIMA implements Serializable {
    @Serial
    private static final long serialVersionUID = 2L;

    /**
     * The original time series.
     */
    private final double[] x;
    /**
     * The order of AR.
     */
    private final int p;
    /**
     * The degree of differencing.
     */
    private final int d;
    /**
     * The order of MA.
     */
    private final int q;
    /**
     * The underlying ARMA model on the differenced series.
     */
    private final ARMA arma;
    /**
     * Intermediate differenced series, where diff[d-1] is the stationary series.
     */
    private final double[][] diff;
    /**
     * In-sample fitted values on the original scale.
     */
    private final double[] fittedValues;
    /**
     * In-sample residuals on the original scale.
     */
    private final double[] residuals;

    /**
     * Constructor.
     *
     * @param x the original time series.
     * @param p the order of AR.
     * @param d the degree of differencing.
     * @param q the order of MA.
     * @param arma the underlying ARMA model.
     * @param diff the intermediate differencing stages.
     */
    public ARIMA(double[] x, int p, int d, int q, ARMA arma, double[][] diff) {
        this.x = x;
        this.p = p;
        this.d = d;
        this.q = q;
        this.arma = arma;
        this.diff = diff;

        int n = arma.residuals().length;
        this.residuals = arma.residuals();
        this.fittedValues = new double[n];
        for (int i = 0; i < n; i++) {
            int offset = x.length - n + i;
            this.fittedValues[i] = x[offset] - residuals[i];
        }
    }

    /**
     * Returns the original time series.
     * @return the original time series.
     */
    public double[] x() {
        return x;
    }

    /**
     * Returns the order of AR.
     * @return the order of AR.
     */
    public int p() {
        return p;
    }

    /**
     * Returns the degree of differencing.
     * @return the degree of differencing.
     */
    public int d() {
        return d;
    }

    /**
     * Returns the order of MA.
     * @return the order of MA.
     */
    public int q() {
        return q;
    }

    /**
     * Returns the underlying ARMA model on the differenced series.
     * @return the ARMA model.
     */
    public ARMA arma() {
        return arma;
    }

    /**
     * Returns the mean of the fitted (differenced) series.
     * @return the mean.
     */
    public double mean() {
        return arma.mean();
    }

    /**
     * Returns the linear coefficients of AR(p).
     * @return the AR coefficients.
     */
    public double[] ar() {
        return arma.ar();
    }

    /**
     * Returns the linear coefficients of MA(q).
     * @return the MA coefficients.
     */
    public double[] ma() {
        return arma.ma();
    }

    /**
     * Returns the intercept / drift term.
     * @return the intercept.
     */
    public double intercept() {
        return arma.intercept();
    }

    /**
     * Returns in-sample fitted values on the original series scale.
     * @return in-sample fitted values.
     */
    public double[] fittedValues() {
        return fittedValues;
    }

    /**
     * Returns in-sample residuals on the original series scale.
     * @return in-sample residuals.
     */
    public double[] residuals() {
        return residuals;
    }

    /**
     * Returns the residual sum of squares.
     * @return the residual sum of squares.
     */
    public double RSS() {
        return arma.RSS();
    }

    /**
     * Returns the residual variance.
     * @return the residual variance.
     */
    public double variance() {
        return arma.variance();
    }

    /**
     * Returns the degrees of freedom of residual variance.
     * @return the degrees of freedom.
     */
    public int df() {
        return arma.df();
    }

    /**
     * Returns the coefficient of determination R<sup>2</sup> on the fitted series.
     * @return R<sup>2</sup>.
     */
    public double R2() {
        return arma.R2();
    }

    /**
     * Returns the adjusted R<sup>2</sup> on the fitted series.
     * @return adjusted R<sup>2</sup>.
     */
    public double adjustedR2() {
        return arma.adjustedR2();
    }

    /**
     * Returns the log-likelihood of the model under Gaussian errors.
     * @return the log-likelihood.
     */
    public double logLikelihood() {
        return arma.logLikelihood();
    }

    /**
     * Returns the Akaike information criterion (AIC).
     * @return the AIC.
     */
    public double aic() {
        return arma.aic();
    }

    /**
     * Returns the Bayesian information criterion (BIC).
     * @return the BIC.
     */
    public double bic() {
        return arma.bic();
    }

    /**
     * Returns the hypothesis testing of the coefficients.
     * @return the hypothesis testing table.
     */
    public double[][] ttest() {
        return arma.ttest();
    }

    /**
     * Fits an ARIMA(p, d, q) model.
     *
     * @param x the time series.
     * @param p the order of AR.
     * @param d the degree of differencing.
     * @param q the order of MA.
     * @return the fitted ARIMA model.
     */
    public static ARIMA fit(double[] x, int p, int d, int q) {
        if (p < 0 || p >= x.length) {
            throw new IllegalArgumentException("Invalid order p = " + p);
        }

        if (d < 0 || d >= x.length) {
            throw new IllegalArgumentException("Invalid differencing order d = " + d);
        }

        if (q < 0 || q >= x.length) {
            throw new IllegalArgumentException("Invalid order q = " + q);
        }

        if (p == 0 && q == 0) {
            throw new IllegalArgumentException("Both p and q cannot be 0");
        }

        double[] stationary;
        double[][] diff = null;
        if (d == 0) {
            stationary = x;
        } else {
            diff = TimeSeries.diff(x, 1, d);
            stationary = diff[d - 1];
        }

        ARMA arma;
        if (p > 0 && q > 0) {
            arma = ARMA.fit(stationary, p, q);
        } else if (p > 0) {
            AR ar = AR.ols(stationary, p, true);
            arma = new ARMA(stationary, ar.ar(), new double[0], ar.intercept(), ar.fittedValues(), ar.residuals());
        } else {
            arma = ARMA.ma(stationary, q);
        }

        return new ARIMA(x, p, d, q, arma, diff);
    }

    /**
     * Fits an ARIMA(p, 0, q) model without differencing (equivalent to ARMA(p, q)).
     *
     * @param x the time series.
     * @param p the order of AR.
     * @param q the order of MA.
     * @return the fitted ARIMA model.
     */
    public static ARIMA fit(double[] x, int p, int q) {
        return fit(x, p, 0, q);
    }

    /**
     * Returns 1-step ahead forecast on the original scale.
     * @return 1-step ahead forecast.
     */
    public double forecast() {
        return forecast(1)[0];
    }

    /**
     * Returns l-step ahead forecast on the original scale.
     *
     * @param l the number of forecast steps.
     * @return l-step ahead forecast.
     */
    public double[] forecast(int l) {
        if (l <= 0) {
            throw new IllegalArgumentException("Invalid forecast horizon l = " + l);
        }

        double[] forecasts = arma.forecast(l);
        if (d == 0) {
            return forecasts;
        }

        // Invert differencing layer-by-layer
        for (int level = d - 1; level >= 0; level--) {
            double[] series = (level == 0) ? x : diff[level - 1];
            double last = series[series.length - 1];

            forecasts[0] = last + forecasts[0];
            for (int i = 1; i < l; i++) {
                forecasts[i] = forecasts[i - 1] + forecasts[i];
            }
        }

        return forecasts;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append(String.format("ARIMA(%d, %d, %d):\n", p, d, q));

        double[] r = residuals.clone();
        builder.append("\nResiduals:\n");
        builder.append("       Min          1Q      Median          3Q         Max\n");
        builder.append(String.format("%10.4f  %10.4f  %10.4f  %10.4f  %10.4f%n",
                MathEx.min(r), MathEx.q1(r), MathEx.median(r), MathEx.q3(r), MathEx.max(r)));

        builder.append("\nCoefficients:\n");
        double[][] ttest = ttest();
        double b = intercept();
        if (ttest != null) {
            builder.append("              Estimate Std. Error    t value   Pr(>|t|)\n");
            if (b != 0.0) {
                builder.append(String.format("Intercept   %10.4f%n", b));
            }

            for (int i = 0; i < ttest.length; i++) {
                String name = i < p ? "ar" : "ma";
                int lag = i < p ? (i + 1) : (i - p + 1);
                builder.append(String.format("%s[-%d]\t    %10.4f %10.4f %10.4f %10.4f %s%n",
                        name, lag, ttest[i][0], ttest[i][1], ttest[i][2], ttest[i][3],
                        Hypothesis.significance(ttest[i][3])));
            }

            builder.append("---------------------------------------------------------------------\n");
            builder.append("Significance codes:  0 '***' 0.001 '**' 0.01 '*' 0.05 '.' 0.1 ' ' 1\n");
        } else {
            if (b != 0.0) {
                builder.append(String.format("Intercept   %10.4f%n", b));
            }

            double[] ar = ar();
            for (int i = 0; i < p; i++) {
                builder.append(String.format("ar[-%d]\t    %10.4f%n", i + 1, ar[i]));
            }

            double[] ma = ma();
            for (int i = 0; i < q; i++) {
                builder.append(String.format("ma[-%d]\t    %10.4f%n", i + 1, ma[i]));
            }
        }

        builder.append(String.format("%nResidual  variance: %.4f on %5d degrees of freedom%n", variance(), df()));
        builder.append(String.format("Multiple R-squared: %.4f, Adjusted R-squared: %.4f%n", R2(), adjustedR2()));
        builder.append(String.format("AIC: %.4f, BIC: %.4f%n", aic(), bic()));

        return builder.toString();
    }
}
