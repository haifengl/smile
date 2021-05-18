/*
 * Copyright (c) 2010-2020 Haifeng Li. All rights reserved.
 *
 * Smile is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of
 * the License, or (at your option) any later version.
 *
 * Smile is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Smile.  If not, see <https://www.gnu.org/licenses/>.
 */

package smile.timeseries;

import java.io.Serializable;
import java.util.Arrays;
import smile.math.MathEx;
import smile.math.matrix.Matrix;
import smile.math.special.Beta;
import smile.stat.Hypothesis;

/**
 * Autoregressive moving-average model. ARMA models provide a parsimonious
 * description of a (weakly) stationary stochastic process in terms of
 * two polynomials, one for the autoregression (AR) and the second for
 * the moving average (MA).
 * <p>
 * Given a time series of data, the ARMA model is a tool for understanding
 * and, perhaps, predicting future values in this series. The AR part
 * involves regressing the variable on its own lagged values.
 * The MA part involves modeling the error term as a linear combination
 * of error terms occurring contemporaneously and at various times in
 * the past. The model is usually referred to as the ARMA(p,q) model
 * where p is the order of the AR part and q is the order of the MA part.
 *
 * @author Haifeng Li
 */
public class ARMA implements Serializable {
    private static final long serialVersionUID = 2L;

    /**
     * The time series.
     */
    private final double[] x;
    /**
     * The mean of time series.
     */
    private final double mean;
    /**
     * The order of AR.
     */
    private final int p;
    /**
     * The order of MA.
     */
    private final int q;
    /**
     * The intercept.
     */
    private final double b;
    /**
     * The linear weights of AR.
     */
    private final double[] ar;
    /**
     * The linear weights of MA.
     */
    private final double[] ma;
    /**
     * The coefficients, their standard errors, t-scores, and p-values.
     */
    private double[][] ttest;
    /**
     * The fitted values.
     */
    private final double[] fittedValues;
    /**
     * The residuals, that is response minus fitted values.
     */
    private final double[] residuals;
    /**
     * Residual sum of squares.
     */
    private double RSS;
    /**
     * Estimated variance.
     */
    private final double variance;
    /**
     * The degree-of-freedom of residual variance.
     */
    private final int df;
    /**
     * R<sup>2</sup>. R<sup>2</sup> is a statistic that will give some information
     * about the goodness of fit of a model. In regression, the R<sup>2</sup>
     * coefficient of determination is a statistical measure of how well
     * the regression line approximates the real data points. An R<sup>2</sup>
     * of 1.0 indicates that the regression line perfectly fits the data.
     * <p>
     * In the case of ordinary least-squares regression, R<sup>2</sup>
     * increases as we increase the number of variables in the model
     * (R<sup>2</sup> will not decrease). This illustrates a drawback to
     * one possible use of R<sup>2</sup>, where one might try to include
     * more variables in the model until "there is no more improvement".
     * This leads to the alternative approach of looking at the
     * adjusted R<sup>2</sup>.
     */
    private final double RSquared;
    /**
     * Adjusted R<sup>2</sup>. The adjusted R<sup>2</sup> has almost same
     * explanation as R<sup>2</sup> but it penalizes the statistic as
     * extra variables are included in the model.
     */
    private final double adjustedRSquared;

    /**
     * Constructor.
     *
     * @param x the time series
     * @param ar the estimated weight parameters of AR(p).
     * @param ma the estimated weight parameters of MA(q).
     * @param b the intercept.
     * @param fittedValues the fitted values.
     * @param residuals the residuals.
     */
    public ARMA(double[] x, double[] ar, double[] ma, double b, double[] fittedValues, double[] residuals) {
        this.x = x;
        this.p = ar.length;
        this.q = ma.length;
        this.ar = ar;
        this.ma = ma;
        this.b = b;
        this.mean = MathEx.mean(x);
        this.fittedValues = fittedValues;
        this.residuals = residuals;

        int n = residuals.length;
        double ybar = 0;
        for (int i = x.length - n; i < x.length; i++) {
            ybar += x[i];
        }
        ybar /= n;

        double TSS = 0.0;
        RSS = 0.0;

        for (int i = 0; i < n; i++) {
            RSS += MathEx.pow2(residuals[i]);
            TSS += MathEx.pow2(fittedValues[i] - ybar);
        }

        df = n;
        variance = RSS / df;

        RSquared = 1.0 - RSS / TSS;
        adjustedRSquared = 1.0 - ((1 - RSquared) * (n-1) / (n-p));
    }

    /**
     * Returns the time series.
     * @return the time series.
     */
    public double[] x() {
        return x;
    }

    /**
     * Returns the mean of time series.
     * @return the mean of time series.
     */
    public double mean() {
        return mean;
    }

    /**
     * Returns the order of AR.
     * @return the order of AR.
     */
    public int p() {
        return p;
    }

    /**
     * Returns the order of MA.
     * @return the order of MA.
     */
    public int q() {
        return q;
    }

    /**
     * Returns the t-test of the coefficients (including intercept).
     * The first column is the coefficients, the second column is the standard
     * error of coefficients, the third column is the t-score of the hypothesis
     * test if the coefficient is zero, the fourth column is the p-values of
     * test. The last row is of intercept.
     *
     * @return the t-test of the coefficients.
     */
    public double[][] ttest() {
        return ttest;
    }

    /**
     * Returns the linear coefficients of AR(p).
     * @return the linear coefficients of AR(p).
     */
    public double[] ar() {
        return ar;
    }

    /**
     * Returns the linear coefficients of MA(q).
     * @return the linear coefficients of MA(q).
     */
    public double[] ma() {
        return ma;
    }

    /**
     * Returns the intercept.
     * @return the intercept.
     */
    public double intercept() {
        return b;
    }

    /**
     * Returns the residuals, that is response minus fitted values.
     * @return the residuals.
     */
    public double[] residuals() {
        return residuals;
    }

    /**
     * Returns the fitted values.
     * @return the fitted values.
     */
    public double[] fittedValues() {
        return fittedValues;
    }

    /**
     * Returns the residual sum of squares.
     * @return the residual sum of squares.
     */
    public double RSS() {
        return RSS;
    }

    /**
     * Returns the residual variance.
     * @return the residual variance.
     */
    public double variance() {
        return variance;
    }

    /**
     * Returns the degree-of-freedom of residual standard error.
     * @return the degree-of-freedom of residual standard error.
     */
    public int df() {
        return df;
    }

    /**
     * Returns R<sup>2</sup> statistic. In regression, the R<sup>2</sup>
     * coefficient of determination is a statistical measure of how well
     * the regression line approximates the real data points. An R<sup>2</sup>
     * of 1.0 indicates that the regression line perfectly fits the data.
     * <p>
     * In the case of ordinary least-squares regression, R<sup>2</sup>
     * increases as we increase the number of variables in the model
     * (R<sup>2</sup> will not decrease). This illustrates a drawback to
     * one possible use of R<sup>2</sup>, where one might try to include more
     * variables in the model until "there is no more improvement". This leads
     * to the alternative approach of looking at the adjusted R<sup>2</sup>.
     *
     * @return R<sup>2</sup> statistic.
     */
    public double RSquared() {
        return RSquared;
    }

    /**
     * Returns adjusted R<sup>2</sup> statistic. The adjusted R<sup>2</sup>
     * has almost same explanation as R<sup>2</sup> but it penalizes the
     * statistic as extra variables are included in the model.
     *
     * @return Adjusted R<sup>2</sup> statistic.
     */
    public double adjustedRSquared() {
        return adjustedRSquared;
    }

    /**
     * Fits an ARMA model with Hannan-Rissanen algorithm.
     *
     * @param x the time series.
     * @param p the order of AR.
     * @param q the order of MA.
     * @return the model.
     */
    public static ARMA fit(double[] x, int p, int q) {
        if (p <= 0 || p >= x.length) {
            throw new IllegalArgumentException("Invalid order p = " + p);
        }

        if (q <= 0 || q >= x.length) {
            throw new IllegalArgumentException("Invalid order q = " + q);
        }

        int m = p + q + 20;
        int k = Math.max(p, q);
        int n = x.length - m - k;

        AR arm = AR.fit(x, m);
        double[] a = new double[x.length];
        System.arraycopy(arm.residuals(), 0, a, m, a.length - m);

        double[] y = Arrays.copyOfRange(x, m+k, x.length);
        Matrix X = new Matrix(n, p+q+1);
        for (int j = 0; j < p; j++) {
            for (int i = 0; i < n; i++) {
                X.set(i, j, x[m+k+i-j-1]);
            }
        }

        for (int j = 0; j < q; j++) {
            for (int i = 0; i < n; i++) {
                X.set(i, p+j, a[m+k+i-j-1]);
            }
        }

        for (int i = 0; i < n; i++) {
            X.set(i, p+q, 1.0);
        }

        Matrix.SVD svd = X.svd(true, false);
        double[] arma = svd.solve(y);
        double[] fittedValues = X.mv(arma);
        for (int j = 0; j < n; j++) {
            a[m+k+j] = x[m+k+j] - fittedValues[j];
        }

        double[] ar = Arrays.copyOf(arma, p);
        double[] ma = Arrays.copyOfRange(arma, p, p+q);
        ARMA model = new ARMA(x, ar, ma, arma[p+q], fittedValues, Arrays.copyOfRange(a, m+k, n));

        Matrix.Cholesky cholesky = X.ata().cholesky(true);
        Matrix inv = cholesky.inverse();

        int df = model.df;
        double error = Math.sqrt(model.variance);
        double[][] ttest = new double[p+q][4];
        model.ttest = ttest;

        for (int i = 0; i < p+q; i++) {
            ttest[i][0] = arma[i];
            double se = error * Math.sqrt(inv.get(i, i));
            ttest[i][1] = se;
            double t = arma[i] / se;
            ttest[i][2] = t;
            ttest[i][3] = Beta.regularizedIncompleteBetaFunction(0.5 * df, 0.5, df / (df + t * t));
        }

        return model;
    }

    /**
     * Predicts/forecasts x[offset].
     *
     * @param x the time series.
     * @param a the white noise error terms.
     * @param offset the offset of time series to forecast.
     * @return the model.
     */
    private double forecast(double[] x, double[] a, int offset) {
        double y = b;
        for (int i = 0; i < p; i++) {
            y += ar[i] * x[offset - i - 1];
        }

        offset -= x.length - a.length;
        for (int i = 0; i < q; i++) {
            y += ma[i] * a[offset - i - 1];
        }

        return y;
    }

    /**
     * Returns 1-step ahead forecast.
     * @return 1-step ahead forecast.
     */
    public double forecast() {
        return forecast(x, residuals, x.length);
    }

    /**
     * Returns l-step ahead forecast.
     * @param l the number of steps.
     * @return l-step ahead forecast.
     */
    public double[] forecast(int l) {
        int k = Math.max(p, q);
        double[] x = new double[k + l];
        double[] a = new double[k + l];
        System.arraycopy(this.x, this.x.length - k, x, 0, k);
        System.arraycopy(this.residuals, this.residuals.length - k, a, 0, k);
        for (int i = 0; i < l; i++) {
            x[p + i] = forecast(x, a, k+i);
        }
        return Arrays.copyOfRange(x, k, x.length);
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append(String.format("ARMA(%d, %d):\n", p, q));

        double[] r = residuals.clone();
        builder.append("\nResiduals:\n");
        builder.append("       Min          1Q      Median          3Q         Max\n");
        builder.append(String.format("%10.4f  %10.4f  %10.4f  %10.4f  %10.4f%n", MathEx.min(r), MathEx.q1(r), MathEx.median(r), MathEx.q3(r), MathEx.max(r)));

        builder.append("\nCoefficients:\n");
        if (ttest != null) {
            builder.append("              Estimate Std. Error    t value   Pr(>|t|)\n");
            if (b != 0.0) {
                builder.append(String.format("Intercept   %10.4f%n", b));
            }

            for (int i = 0; i < ttest.length; i++) {
                builder.append(String.format("%s[-%d]\t    %10.4f %10.4f %10.4f %10.4f %s%n", i < p ? "ar" : "ma", i+1, ttest[i][0], ttest[i][1], ttest[i][2], ttest[i][3], Hypothesis.significance(ttest[i][3])));
            }

            builder.append("---------------------------------------------------------------------\n");
            builder.append("Significance codes:  0 '***' 0.001 '**' 0.01 '*' 0.05 '.' 0.1 ' ' 1\n");
        } else {
            if (b != 0.0) {
                builder.append(String.format("Intercept   %10.4f%n", b));
            }

            for (int i = 0; i < p; i++) {
                builder.append(String.format("ar[-%d]\t    %10.4f%n", i+1, ar[i]));
            }

            for (int i = 0; i < q; i++) {
                builder.append(String.format("ma[-%d]\t    %10.4f%n", i+1, ma[i]));
            }
        }

        builder.append(String.format("%nResidual  variance: %.4f on %5d degrees of freedom%n", variance, df));
        builder.append(String.format("Multiple R-squared: %.4f, Adjusted R-squared: %.4f%n", RSquared, adjustedRSquared));

        return builder.toString();
    }
}