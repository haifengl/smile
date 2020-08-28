/*******************************************************************************
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
 ******************************************************************************/

package smile.timeseries;

import java.io.Serializable;
import java.util.Arrays;
import smile.math.MathEx;
import smile.math.matrix.Matrix;
import smile.math.special.Beta;
import smile.stat.Hypothesis;

/**
 * Autoregressive model. The autoregressive model specifies that the output
 * variable depends linearly on its own previous values and on a stochastic
 * term; thus the model is in the form of a stochastic difference equation.
 * Together with the moving-average (MA) model, it is a special case and
 * key component of the more general autoregressive–moving-average (ARMA)
 * and autoregressive integrated moving average (ARIMA) models of time
 * series, which have a more complicated stochastic structure; it is also
 * a special case of the vector autoregressive model (VAR), which consists
 * of a system of more than one interlocking stochastic difference equation
 * in more than one evolving random variable.
 * <p>
 * Contrary to the moving-average (MA) model, the autoregressive model
 * is not always stationary as it may contain a unit root.
 * <p>
 * The notation <code>AR(p)</code> indicates an autoregressive
 * model of order p. For an <code>AR(p)</code> model to be weakly stationary,
 * the inverses of the roots of the characteristic polynomial must be less
 * than 1 in modulus.
 * <p>
 * Two general approaches are available for determining the order p.
 * The first approach is to use the partial autocorrelation function,
 * and the second approach is to use some information criteria.
 * <p>
 * Autoregression is a good start point for more complicated models.
 * They often fit quite well (don’t need the MA terms).
 * And the fitting process is fast (MLEs require some iterations).
 * In applications, easily fitting autoregressions is important
 * for obtaining initial values of parameters and in getting estimates of
 * the error process. Least squares is a popular choice, as is the
 * Yule-Walker procedure. Unlike the Yule-Walker procedure, least squares
 * can produce a non-stationary fitted model. Both the Yule-Walker and
 * least squares estimators are non-iterative and consistent,
 * so they can be used as starting values for iterative methods like MLE.
 *
 * @author Haifeng Li
 */
public class AR implements Serializable {
    private static final long serialVersionUID = 2L;
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(AR.class);

    /** The fitting method. */
    enum Method {
        Yule_Walker {
            @Override
            public String toString() {
                return "Yule-Walker";
            }
        },
        OLS,
        ML
    }

    /**
     * The fitting method.
     */
    Method method;
    /**
     * The order.
     */
    int p;
    /**
     * The intercept.
     */
    double b;
    /**
     * The linear weights.
     */
    double[] w;
    /**
     * The coefficients, their standard errors, t-scores, and p-values.
     */
    double[][] ttest;
    /**
     * The fitted values.
     */
    double[] fittedValues;
    /**
     * The residuals, that is response minus fitted values.
     */
    double[] residuals;
    /**
     * Residual sum of squares.
     */
    double RSS;
    /**
     * Estimated variance.
     */
    double variance;
    /**
     * The degree-of-freedom of residual variance.
     */
    int df;
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
    double RSquared;
    /**
     * Adjusted R<sup>2</sup>. The adjusted R<sup>2</sup> has almost same
     * explanation as R<sup>2</sup> but it penalizes the statistic as
     * extra variables are included in the model.
     */
    double adjustedRSquared;

    /**
     * Constructor.
     *
     * @param x the time series
     * @param w the weights of AR(p).
     * @param b the intercept.
     * @param method the fitting method.
     */
    public AR(double[] x, double[] w, double b, Method method) {
        this(x, w, b, method, true);
    }

    /**
     * Constructor.
     *
     * @param x the time series
     * @param w the estimated weight parameters of AR(p).
     * @param b the intercept.
     * @param method the fitting method.
     * @param stderr the flag if compute the estimated standard errors of the estimate of parameters.
     */
    public AR(double[] x, double[] w, double b, Method method, boolean stderr) {
        this.p = w.length;
        this.w = w;
        this.b = b;
        this.method = method;

        Matrix X = X(x, p);
        double[] y = y(x, p);
        double ybar = MathEx.mean(y);

        int n = y.length;
        fittedValues = new double[n];
        residuals = new double[n];

        double[] w1 = Arrays.copyOf(w, p+1);
        w1[p] = b;
        X.mv(w1, fittedValues);

        RSS = 0.0;
        double TSS = 0.0;
        for (int i = 0; i < n; i++) {
            residuals[i] = y[i] - fittedValues[i];
            RSS += MathEx.sqr(residuals[i]);
            TSS += MathEx.sqr(y[i] - ybar);
        }

        df = x.length - p;
        variance = RSS / df;

        double error = Math.sqrt(variance);
        RSquared = 1.0 - RSS / TSS;
        adjustedRSquared = 1.0 - ((1 - RSquared) * (n-1) / (n-p));

        if (stderr) {
            Matrix.Cholesky cholesky = X.ata().cholesky(true);
            Matrix inv = cholesky.inverse();

            ttest = new double[p][4];

            for (int i = 0; i < p; i++) {
                ttest[i][0] = w[i];
                double se = error * Math.sqrt(inv.get(i, i));
                ttest[i][1] = se;
                double t = w[i] / se;
                ttest[i][2] = t;
                ttest[i][3] = Beta.regularizedIncompleteBetaFunction(0.5 * df, 0.5, df / (df + t * t));
            }
        }
    }

    /** Returns the least squares design matrix. */
    private static Matrix X(double[] x, int p) {
        int n = x.length - p;
        Matrix X = new Matrix(n, p+1);
        for (int j = 0; j < p; j++) {
            for (int i = 0; i < n; i++) {
                X.set(i, j, x[i+p-j-1]);
            }
        }

        for (int i = 0; i < n; i++) {
            X.set(i, p, 1.0);
        }

        return X;
    }

    /** Returns the right-hand-side of least squares. */
    private static double[] y(double[] x, int p) {
        return Arrays.copyOfRange(x, p, x.length);
    }

    /**
     * Returns the t-test of the coefficients (including intercept).
     * The first column is the coefficients, the second column is the standard
     * error of coefficients, the third column is the t-score of the hypothesis
     * test if the coefficient is zero, the fourth column is the p-values of
     * test. The last row is of intercept.
     */
    public double[][] ttest() {
        return ttest;
    }

    /**
     * Returns the linear coefficients (without intercept).
     */
    public double[] coefficients() {
        return w;
    }

    /**
     * Returns the intercept.
     */
    public double intercept() {
        return b;
    }

    /**
     * Returns the residuals, that is response minus fitted values.
     */
    public double[] residuals() {
        return residuals;
    }

    /**
     * Returns the fitted values.
     */
    public double[] fittedValues() {
        return fittedValues;
    }

    /**
     * Returns the residual sum of squares.
     */
    public double RSS() {
        return RSS;
    }

    /**
     * Returns the residual variance.
     */
    public double variance() {
        return variance;
    }

    /**
     * Returns the degree-of-freedom of residual standard error.
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
     */
    public double RSquared() {
        return RSquared;
    }

    /**
     * Returns adjusted R<sup>2</sup> statistic. The adjusted R<sup>2</sup>
     * has almost same explanation as R<sup>2</sup> but it penalizes the
     * statistic as extra variables are included in the model.
     */
    public double adjustedRSquared() {
        return adjustedRSquared;
    }

    /**
     * Fits an autoregressive model with Yule-Walker procedure.
     *
     * @param x the time series.
     * @param p the order.
     */
    public static AR fit(double[] x, int p) {
        if (p <= 0 || p >= x.length) {
            throw new IllegalArgumentException("Invalid order p = " + p);
        }

        double mean = MathEx.mean(x);
        double[] y = new double[p];
        for (int i = 0; i < p; i++) {
            y[i] = TimeSeries.acf(x, i+1);
        }

        double[] r = new double[p];
        r[0] = 1.0;
        System.arraycopy(y, 0, r, 1, p - 1);
        Matrix toeplitz = Matrix.toeplitz(r);

        Matrix.Cholesky cholesky = toeplitz.cholesky();
        double[] w = cholesky.solve(y);

        double mu = mean * (1.0 - MathEx.sum(w));
        return new AR(x, w, mu, Method.Yule_Walker, false);
    }

    /**
     * Fits an autoregressive model with least squares method.
     *
     * @param x the time series.
     * @param p the order.
     */
    public static AR ols(double[] x, int p) {
        return ols(x, p, true);
    }

    /**
     * Fits an autoregressive model with least squares method.
     *
     * @param x the time series.
     * @param p the order.
     * @param stderr the flag if estimate the standard errors of parameters.
     */
    public static AR ols(double[] x, int p, boolean stderr) {
        if (p <= 0 || p >= x.length) {
            throw new IllegalArgumentException("Invalid order p = " + p);
        }

        Matrix X = X(x, p);
        double[] y = y(x, p);

        // weights and intercept
        Matrix.SVD svd = X.svd(true, true);
        double[] w = svd.solve(y);

        return new AR(x, Arrays.copyOf(w, p), w[p], Method.OLS, stderr);
    }

    /**
     * Returns 1-step ahead forecast.
     *
     * @param x the time series.
     */
    public double predict(double[] x) {
        return predict(x, 0);
    }

    /**
     * Returns 1-step ahead forecast.
     *
     * @param x the time series.
     * @param offset the offset of time series for forecasting.
     */
    public double predict(double[] x, int offset) {
        if (x.length != p) {
            throw new IllegalArgumentException(String.format("Invalid input vector size: %d, expected: %d", x.length, p));
        }

        double y = b;
        for (int i = 0; i < p; i++) {
            y += w[i] * x[offset + i];
        }

        return y;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append(String.format("AR(%d) by %s:\n", p, method));

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

            for (int i = 0; i < p; i++) {
                builder.append(String.format("x[-%d]\t    %10.4f %10.4f %10.4f %10.4f %s%n", i+1, ttest[i][0], ttest[i][1], ttest[i][2], ttest[i][3], Hypothesis.significance(ttest[i][3])));
            }

            builder.append("---------------------------------------------------------------------\n");
            builder.append("Significance codes:  0 '***' 0.001 '**' 0.01 '*' 0.05 '.' 0.1 ' ' 1\n");
        } else {
            if (b != 0.0) {
                builder.append(String.format("Intercept   %10.4f%n", b));
            }

            for (int i = 0; i < p; i++) {
                builder.append(String.format("x[-%d]\t    %10.4f%n", i+1, w[i]));
            }
        }

        builder.append(String.format("%nResidual  variance: %.4f on %5d degrees of freedom%n", variance, df));
        builder.append(String.format("Multiple R-squared: %.4f, Adjusted R-squared: %.4f%n", RSquared, adjustedRSquared));

        return builder.toString();
    }
}
