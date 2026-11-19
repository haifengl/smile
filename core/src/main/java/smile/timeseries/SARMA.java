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
import smile.math.MathEx;
import smile.math.special.Beta;
import smile.stat.Hypothesis;
import smile.tensor.*;
import static smile.tensor.ScalarType.*;

/**
 * Seasonal autoregressive moving-average model SARMA(p, q)(P, Q)[s].
 * <p>
 * The model extends {@link ARMA} with seasonal autoregressive and moving-average
 * terms:
 * <pre>
 * x_t = b + sum_{i=1..p} phi_i x_{t-i} + sum_{j=1..P} Phi_j x_{t-j*s}
 *         + e_t + sum_{i=1..q} theta_i e_{t-i} + sum_{j=1..Q} Theta_j e_{t-j*s}
 * </pre>
 * <p>
 * <b>Representation note.</b> Unlike the classical notation, in which the
 * seasonal polynomial is a factor of the full polynomial, the coefficients
 * here are <em>direct lag coefficients</em>: <code>ar()[i]</code> multiplies
 * <code>x_{t-i-1}</code> and <code>sar()[j]</code> multiplies
 * <code>x_{t-(j+1)*s}</code>. The two representations coincide for
 * <code>P = 1</code> and <code>Q = 1</code>; for higher seasonal orders the
 * product form introduces cross terms that are absorbed into the estimated
 * non-seasonal coefficients.
 * <p>
 * Estimation follows the two-stage Hannan–Rissanen procedure used by
 * {@link ARMA}: a long autoregression of order
 * <code>max(p + P*s, q + Q*s) + 20</code> provides residuals that proxy the
 * unobserved errors, and the SARMA coefficients are then obtained by ordinary
 * least squares on the expanded regressor matrix, solved through SVD. The
 * standard errors of the coefficients come from the inverse of the cross-product
 * matrix.
 * <p>
 * When <code>P = Q = 0</code> the fit is delegated to {@link ARMA#fit}, so the
 * two models produce identical coefficients in that case.
 *
 * <h2>References</h2>
 * <ol>
 * <li>George E. P. Box, Gwilym M. Jenkins, Gregory C. Reinsel, and Greta M. Ljung.
 *     Time Series Analysis: Forecasting and Control. 5th edition, Wiley, 2015.</li>
 * <li>Edward J. Hannan and Jorma Rissanen. Recursive estimation of mixed
 *     autoregressive-moving average order. Biometrika, 69(1):81–94, 1982.</li>
 * </ol>
 *
 * @author Haifeng Li
 */
public class SARMA implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    /** The time series. */
    private final double[] x;
    /** The non-seasonal AR order. */
    private final int p;
    /** The non-seasonal MA order. */
    private final int q;
    /** The seasonal AR order. */
    private final int P;
    /** The seasonal MA order. */
    private final int Q;
    /** The seasonal period. */
    private final int s;
    /** The non-seasonal AR coefficients. */
    private final double[] ar;
    /** The seasonal AR coefficients. */
    private final double[] sar;
    /** The non-seasonal MA coefficients. */
    private final double[] ma;
    /** The seasonal MA coefficients. */
    private final double[] sma;
    /** The intercept. */
    private final double b;
    /** The in-sample fitted values. */
    private final double[] fittedValues;
    /** The in-sample residuals. */
    private final double[] residuals;
    /** The t-tests of coefficients. */
    private final double[][] ttest;
    /** The residual sum of squares. */
    private final double RSS;
    /** The innovation variance. */
    private final double variance;

    /**
     * Constructor.
     *
     * @param x the time series.
     * @param p the non-seasonal AR order.
     * @param q the non-seasonal MA order.
     * @param P the seasonal AR order.
     * @param Q the seasonal MA order.
     * @param s the seasonal period.
     * @param ar the non-seasonal AR coefficients.
     * @param sar the seasonal AR coefficients.
     * @param ma the non-seasonal MA coefficients.
     * @param sma the seasonal MA coefficients.
     * @param b the intercept.
     * @param fittedValues the fitted values.
     * @param residuals the residuals.
     * @param ttest the t-tests of coefficients, or {@code null}.
     */
    SARMA(double[] x, int p, int q, int P, int Q, int s,
          double[] ar, double[] sar, double[] ma, double[] sma, double b,
          double[] fittedValues, double[] residuals, double[][] ttest) {
        this.x = x;
        this.p = p;
        this.q = q;
        this.P = P;
        this.Q = Q;
        this.s = s;
        this.ar = ar;
        this.sar = sar;
        this.ma = ma;
        this.sma = sma;
        this.b = b;
        this.fittedValues = fittedValues;
        this.residuals = residuals;
        this.ttest = ttest;

        double rss = 0.0;
        for (double e : residuals) {
            rss += MathEx.pow2(e);
        }
        this.RSS = rss;
        this.variance = rss / residuals.length;
    }

    /**
     * Fits a SARMA model.
     *
     * @param x the (stationary) time series.
     * @param p the non-seasonal AR order.
     * @param q the non-seasonal MA order.
     * @param P the seasonal AR order.
     * @param Q the seasonal MA order.
     * @param s the seasonal period.
     * @return the fitted model.
     */
    public static SARMA fit(double[] x, int p, int q, int P, int Q, int s) {
        if (p < 0 || p >= x.length) {
            throw new IllegalArgumentException("Invalid AR order p = " + p);
        }
        if (q < 0 || q >= x.length) {
            throw new IllegalArgumentException("Invalid MA order q = " + q);
        }
        if (P < 0) {
            throw new IllegalArgumentException("Invalid seasonal AR order P = " + P);
        }
        if (Q < 0) {
            throw new IllegalArgumentException("Invalid seasonal MA order Q = " + Q);
        }
        if (P > 0 || Q > 0) {
            if (s < 2) {
                throw new IllegalArgumentException("Invalid seasonal period s = " + s);
            }
        }
        if (p == 0 && q == 0 && P == 0 && Q == 0) {
            // Mean-only model. This is the natural fit for a series that has
            // already been reduced to white noise by differencing, e.g. the
            // pure seasonal random walk handled by SARIMA(0,0,0)(0,1,0)[s].
            double mean = MathEx.mean(x);
            double[] fitted = new double[x.length];
            double[] residuals = new double[x.length];
            Arrays.fill(fitted, mean);
            for (int i = 0; i < x.length; i++) {
                residuals[i] = x[i] - mean;
            }
            return new SARMA(x, 0, 0, 0, 0, Math.max(s, 1),
                    new double[0], new double[0], new double[0], new double[0], mean,
                    fitted, residuals, null);
        }

        if (P == 0 && Q == 0) {
            // Delegate to the shipped non-seasonal machinery so that the
            // coefficients match ARMA/AR exactly, including the p-only and
            // q-only cases.
            if (p > 0 && q > 0) {
                ARMA arma = ARMA.fit(x, p, q);
                return new SARMA(x, p, q, 0, 0, Math.max(s, 1),
                        arma.ar(), new double[0], arma.ma(), new double[0], arma.intercept(),
                        arma.fittedValues(), arma.residuals(), arma.ttest());
            } else if (p > 0) {
                AR ar = AR.ols(x, p, true);
                return new SARMA(x, p, 0, 0, 0, Math.max(s, 1),
                        ar.ar(), new double[0], new double[0], new double[0], ar.intercept(),
                        ar.fittedValues(), ar.residuals(), ar.ttest());
            } else {
                ARMA arma = ARMA.ma(x, q);
                return new SARMA(x, 0, q, 0, 0, Math.max(s, 1),
                        new double[0], new double[0], arma.ma(), new double[0], arma.intercept(),
                        arma.fittedValues(), arma.residuals(), arma.ttest());
            }
        }

        int maxLag = Math.max(p + P * s, q + Q * s);
        int m = maxLag + 20;
        int n = x.length - m - maxLag;
        if (n <= 0) {
            throw new IllegalArgumentException(String.format(
                    "Time series is too short for SARMA(%d,%d)(%d,%d)[%d]: length=%d, required>%d",
                    p, q, P, Q, s, x.length, m + maxLag));
        }

        AR arm = AR.fit(x, m);
        double[] a = new double[x.length];
        System.arraycopy(arm.residuals(), 0, a, m, a.length - m);

        int columns = p + P + q + Q + 1;
        double[] y = Arrays.copyOfRange(x, m + maxLag, x.length);
        DenseMatrix X = DenseMatrix.zeros(Float64, n, columns);
        for (int i = 0; i < n; i++) {
            int t = m + maxLag + i;
            int col = 0;
            for (int j = 0; j < p; j++) {
                X.set(i, col++, x[t - j - 1]);
            }
            for (int j = 0; j < P; j++) {
                X.set(i, col++, x[t - (j + 1) * s]);
            }
            for (int j = 0; j < q; j++) {
                X.set(i, col++, a[t - j - 1]);
            }
            for (int j = 0; j < Q; j++) {
                X.set(i, col++, a[t - (j + 1) * s]);
            }
            X.set(i, col, 1.0);
        }

        SVD svd = X.copy().svd();
        Vector coef = svd.solve(y);
        double[] fitted = X.mv(coef).toArray(new double[0]);
        for (int j = 0; j < n; j++) {
            a[m + maxLag + j] = x[m + maxLag + j] - fitted[j];
        }

        int col = 0;
        double[] ar = new double[p];
        double[] sar = new double[P];
        double[] ma = new double[q];
        double[] sma = new double[Q];
        for (int j = 0; j < p; j++) ar[j] = coef.get(col++);
        for (int j = 0; j < P; j++) sar[j] = coef.get(col++);
        for (int j = 0; j < q; j++) ma[j] = coef.get(col++);
        for (int j = 0; j < Q; j++) sma[j] = coef.get(col++);
        double b = coef.get(col);

        double[][] ttest = new double[p + P + q + Q][4];
        try {
            Cholesky cholesky = X.ata().cholesky();
            DenseMatrix inv = cholesky.inverse();
            double error = Math.sqrt(rss(fitted, y, n) / n);
            for (int i = 0; i < ttest.length; i++) {
                ttest[i][0] = coef.get(i);
                double se = error * Math.sqrt(Math.max(inv.get(i, i), 0.0));
                ttest[i][1] = se;
                double t = se > 0.0 ? coef.get(i) / se : Double.NaN;
                ttest[i][2] = t;
                int df = n;
                ttest[i][3] = Double.isNaN(t) ? Double.NaN
                        : Beta.regularizedIncompleteBetaFunction(0.5 * df, 0.5, df / (df + t * t));
            }
        } catch (RuntimeException ex) {
            // Rank-deficient design: standard errors are unavailable.
            for (int i = 0; i < ttest.length; i++) {
                ttest[i][0] = coef.get(i);
            }
        }

        return new SARMA(x, p, q, P, Q, s, ar, sar, ma, sma, b,
                fitted, Arrays.copyOfRange(a, m + maxLag, x.length), ttest);
    }

    private static double rss(double[] fitted, double[] y, int n) {
        double rss = 0.0;
        for (int i = 0; i < n; i++) {
            rss += MathEx.pow2(y[i] - fitted[i]);
        }
        return rss;
    }

    /**
     * Returns the time series.
     * @return the time series.
     */
    public double[] x() {
        return x;
    }

    /**
     * Returns the non-seasonal AR order.
     * @return p.
     */
    public int p() {
        return p;
    }

    /**
     * Returns the non-seasonal MA order.
     * @return q.
     */
    public int q() {
        return q;
    }

    /**
     * Returns the seasonal AR order.
     * @return P.
     */
    public int P() {
        return P;
    }

    /**
     * Returns the seasonal MA order.
     * @return Q.
     */
    public int Q() {
        return Q;
    }

    /**
     * Returns the seasonal period.
     * @return s.
     */
    public int period() {
        return s;
    }

    /**
     * Returns the non-seasonal AR coefficients.
     * @return the AR coefficients.
     */
    public double[] ar() {
        return ar;
    }

    /**
     * Returns the seasonal AR coefficients.
     * @return the SAR coefficients.
     */
    public double[] sar() {
        return sar;
    }

    /**
     * Returns the non-seasonal MA coefficients.
     * @return the MA coefficients.
     */
    public double[] ma() {
        return ma;
    }

    /**
     * Returns the seasonal MA coefficients.
     * @return the SMA coefficients.
     */
    public double[] sma() {
        return sma;
    }

    /**
     * Returns the intercept.
     * @return the intercept.
     */
    public double intercept() {
        return b;
    }

    /**
     * Returns the fitted values.
     * @return the fitted values.
     */
    public double[] fittedValues() {
        return fittedValues;
    }

    /**
     * Returns the residuals.
     * @return the residuals.
     */
    public double[] residuals() {
        return residuals;
    }

    /**
     * Returns the t-tests of the coefficients. Rows follow the order
     * AR(1..p), SAR(1..P), MA(1..q), SMA(1..Q).
     *
     * @return the t-tests, or {@code null} when unavailable.
     */
    public double[][] ttest() {
        return ttest;
    }

    /**
     * Returns the residual sum of squares.
     * @return the RSS.
     */
    public double RSS() {
        return RSS;
    }

    /**
     * Returns the innovation variance.
     * @return the variance.
     */
    public double variance() {
        return variance;
    }

    /**
     * Returns the residual degrees of freedom.
     * @return the degrees of freedom.
     */
    public int df() {
        return residuals.length;
    }

    /**
     * Returns the coefficient of determination.
     * @return R².
     */
    public double R2() {
        int n = residuals.length;
        double ybar = 0.0;
        for (int i = x.length - n; i < x.length; i++) {
            ybar += x[i];
        }
        ybar /= n;

        double tss = 0.0;
        for (int i = 0; i < n; i++) {
            tss += MathEx.pow2(x[x.length - n + i] - ybar);
        }
        return 1.0 - RSS / tss;
    }

    /**
     * Returns the log-likelihood of the model.
     * @return the log-likelihood.
     */
    public double logLikelihood() {
        int n = residuals.length;
        return -0.5 * n * (Math.log(2 * Math.PI) + 1.0 + Math.log(Math.max(RSS / n, 1E-300)));
    }

    /**
     * Returns the Akaike information criterion.
     * @return the AIC.
     */
    public double aic() {
        int k = p + P + q + Q + 1;
        return 2 * k - 2 * logLikelihood();
    }

    /**
     * Returns the Bayesian information criterion.
     * @return the BIC.
     */
    public double bic() {
        int k = p + P + q + Q + 1;
        return k * Math.log(residuals.length) - 2 * logLikelihood();
    }

    /** Predicts/forecasts x[offset] in a buffer-aligned coordinate frame. */
    private double forecast(double[] xf, double[] af, int offset) {
        double y = b;
        for (int i = 0; i < p; i++) {
            y += ar[i] * xf[offset - i - 1];
        }
        for (int j = 0; j < P; j++) {
            y += sar[j] * xf[offset - (j + 1) * s];
        }
        for (int i = 0; i < q; i++) {
            y += ma[i] * af[offset - i - 1];
        }
        for (int j = 0; j < Q; j++) {
            y += sma[j] * af[offset - (j + 1) * s];
        }
        return y;
    }

    /**
     * Returns the one-step ahead forecast.
     * @return the 1-step ahead forecast.
     */
    public double forecast() {
        return forecast(1)[0];
    }

    /**
     * Returns the l-step ahead forecast.
     * @param l the number of steps.
     * @return the forecasts.
     */
    public double[] forecast(int l) {
        if (l <= 0) {
            throw new IllegalArgumentException("Invalid forecast horizon l = " + l);
        }

        int lag = Math.max(p + P * s, q + Q * s);
        double[] xf = new double[lag + l];
        double[] af = new double[lag + l];
        System.arraycopy(x, x.length - lag, xf, 0, lag);
        System.arraycopy(residuals, residuals.length - lag, af, 0, lag);
        for (int i = 0; i < l; i++) {
            xf[lag + i] = forecast(xf, af, lag + i);
        }
        return Arrays.copyOfRange(xf, lag, xf.length);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(String.format("SARMA(%d, %d)(%d, %d)[%d]:%n", p, q, P, Q, s));

        double[] r = residuals.clone();
        sb.append(String.format(Locale.ROOT, "%nResiduals:%n"));
        sb.append("       Min          1Q      Median          3Q         Max\n");
        sb.append(String.format(Locale.ROOT, "%10.4f  %10.4f  %10.4f  %10.4f  %10.4f%n",
                MathEx.min(r), MathEx.q1(r), MathEx.median(r), MathEx.q3(r), MathEx.max(r)));

        sb.append(String.format(Locale.ROOT, "%nCoefficients:%n"));
        if (ttest != null) {
            sb.append("              Estimate Std. Error    t value   Pr(>|t|)\n");
            if (b != 0.0) {
                sb.append(String.format(Locale.ROOT, "Intercept   %10.4f%n", b));
            }
            String[] names = names();
            for (int i = 0; i < ttest.length; i++) {
                sb.append(String.format(Locale.ROOT, "%s\t    %10.4f %10.4f %10.4f %10.4f %s%n",
                        names[i], ttest[i][0], ttest[i][1], ttest[i][2], ttest[i][3],
                        Hypothesis.significance(ttest[i][3])));
            }
            sb.append("---------------------------------------------------------------------\n");
            sb.append("Significance codes:  0 '***' 0.001 '**' 0.01 '*' 0.05 '.' 0.1 ' ' 1\n");
        } else {
            if (b != 0.0) {
                sb.append(String.format(Locale.ROOT, "Intercept   %10.4f%n", b));
            }
            String[] names = names();
            int i = 0;
            for (double v : ar) {
                sb.append(String.format(Locale.ROOT, "%s\t    %10.4f%n", names[i++], v));
            }
            for (double v : sar) {
                sb.append(String.format(Locale.ROOT, "%s\t    %10.4f%n", names[i++], v));
            }
            for (double v : ma) {
                sb.append(String.format(Locale.ROOT, "%s\t    %10.4f%n", names[i++], v));
            }
            for (double v : sma) {
                sb.append(String.format(Locale.ROOT, "%s\t    %10.4f%n", names[i++], v));
            }
        }

        sb.append(String.format(Locale.ROOT, "%nAIC: %.4f  BIC: %.4f  Log-Likelihood: %.4f",
                aic(), bic(), logLikelihood()));
        return sb.toString();
    }

    private String[] names() {
        String[] names = new String[p + P + q + Q];
        int i = 0;
        for (int j = 0; j < p; j++) {
            names[i++] = "ar[-" + (j + 1) + "]";
        }
        for (int j = 0; j < P; j++) {
            names[i++] = "sar[-" + (j + 1) * s + "]";
        }
        for (int j = 0; j < q; j++) {
            names[i++] = "ma[-" + (j + 1) + "]";
        }
        for (int j = 0; j < Q; j++) {
            names[i++] = "sma[-" + (j + 1) * s + "]";
        }
        return names;
    }
}
