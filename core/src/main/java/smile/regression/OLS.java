/*******************************************************************************
 * Copyright (c) 2010 Haifeng Li
 *   
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *  
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *******************************************************************************/

package smile.regression;

import java.io.Serializable;
import java.util.Arrays;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import smile.math.Math;
import smile.math.matrix.Matrix;
import smile.math.matrix.DenseMatrix;
import smile.math.matrix.QR;
import smile.math.matrix.SVD;
import smile.math.matrix.Cholesky;
import smile.math.special.Beta;

/**
 * Ordinary least squares. In linear regression,
 * the model specification is that the dependent variable is a linear
 * combination of the parameters (but need not be linear in the independent
 * variables). The residual is the difference between the value of the
 * dependent variable predicted by the model, and the true value of the
 * dependent variable. Ordinary least squares obtains parameter estimates
 * that minimize the sum of squared residuals, SSE (also denoted RSS).
 * <p>
 * The OLS estimator is consistent when the independent variables are
 * exogenous and there is no multicollinearity, and optimal in the class
 * of linear unbiased estimators when the errors are homoscedastic and
 * serially uncorrelated. Under these conditions, the method of OLS provides
 * minimum-variance mean-unbiased estimation when the errors have finite
 * variances.
 * <p>
 * There are several different frameworks in which the linear regression
 * model can be cast in order to make the OLS technique applicable. Each
 * of these settings produces the same formulas and same results, the only
 * difference is the interpretation and the assumptions which have to be
 * imposed in order for the method to give meaningful results. The choice
 * of the applicable framework depends mostly on the nature of data at hand,
 * and on the inference task which has to be performed.
 * <p>
 * Least squares corresponds to the maximum likelihood criterion if the
 * experimental errors have a normal distribution and can also be derived
 * as a method of moments estimator.
 * <p>
 * Once a regression model has been constructed, it may be important to
 * confirm the goodness of fit of the model and the statistical significance
 * of the estimated parameters. Commonly used checks of goodness of fit
 * include the R-squared, analysis of the pattern of residuals and hypothesis
 * testing. Statistical significance can be checked by an F-test of the overall
 * fit, followed by t-tests of individual parameters.
 * <p>
 * Interpretations of these diagnostic tests rest heavily on the model
 * assumptions. Although examination of the residuals can be used to
 * invalidate a model, the results of a t-test or F-test are sometimes more
 * difficult to interpret if the model's assumptions are violated.
 * For example, if the error term does not have a normal distribution,
 * in small samples the estimated parameters will not follow normal
 * distributions and complicate inference. With relatively large samples,
 * however, a central limit theorem can be invoked such that hypothesis
 * testing may proceed using asymptotic approximations.
 * 
 * @author Haifeng Li
 */
public class OLS implements Regression<double[]>, Serializable {
    private static final long serialVersionUID = 1L;
    private static final Logger logger = LoggerFactory.getLogger(OLS.class);

    /**
     * The dimensionality.
     */
    private int p;
    /**
     * The intercept.
     */
    private double b;
    /**
     * The linear weights.
     */
    private double[] w;
    /**
     * The coefficients, their standard errors, t-scores, and p-values.
     */
    private double[][] coefficients;
    /**
     * The residuals, that is response minus fitted values.
     */
    private double[] residuals;
    /**
     * Residual sum of squares.
     */
    private double RSS;
    /**
     * Residual standard error.
     */
    private double error;
    /**
     * The degree-of-freedom of residual standard error.
     */
    private int df;
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
    private double RSquared;
    /**
     * Adjusted R<sup>2</sup>. The adjusted R<sup>2</sup> has almost same
     * explanation as R<sup>2</sup> but it penalizes the statistic as
     * extra variables are included in the model.
     */
    private double adjustedRSquared;
    /**
     * The F-statistic of the goodness-of-fit of the model.
     */
    private double F;
    /**
     * The p-value of the goodness-of-fit test of the model.
     */
    private double pvalue;

    /**
     * Trainer for linear regression by ordinary least squares.
     */
    public static class Trainer extends RegressionTrainer<double[]> {
        /**
         * Constructor.
         */
        public Trainer() {
        }

        @Override
        public OLS train(double[][] x, double[] y) {
            return new OLS(x, y);
        }
    }

    /**
     * Constructor. Learn the ordinary least squares model with QR decomposition.
     * @param x a matrix containing the explanatory variables. NO NEED to include a constant column of 1s for bias.
     * @param y the response values.
     */
    public OLS(double[][] x, double[] y) {
        this(x, y, false);
    }

    /**
     * Constructor. Learn the ordinary least squares model.
     * @param x a matrix containing the explanatory variables. NO NEED to include a constant column of 1s for bias.
     * @param y the response values.
     * @param SVD If true, use SVD to fit the model. Otherwise, use QR decomposition. SVD is slower than QR but
     *            can handle rand-deficient matrix.
     */
    public OLS(double[][] x, double[] y, boolean SVD) {
        if (x.length != y.length) {
            throw new IllegalArgumentException(String.format("The sizes of X and Y don't match: %d != %d", x.length, y.length));
        }

        int n = x.length;
        p = x[0].length;
        
        if (n <= p) {
            throw new IllegalArgumentException(String.format("The input matrix is not over determined: %d rows, %d columns", n, p));
        }

        // weights and intercept
        double[] w1 = new double[p+1];
        DenseMatrix X = Matrix.zeros(n, p+1);
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < p; j++)
                X.set(i, j, x[i][j]);
            X.set(i, p, 1.0);
        }

        QR qr = null;
        SVD svd = null;
        if (SVD) {
            svd = X.svd();
            svd.solve(y, w1);
        } else {
            try {
                qr = X.qr();
                qr.solve(y, w1);
            } catch (RuntimeException e) {
                logger.warn("Matrix is not of full rank, try SVD instead");
                SVD = true;
                svd = X.svd();
                Arrays.fill(w1, 0.0);
                svd.solve(y, w1);
            }
        }

        b = w1[p];
        w = new double[p];
        System.arraycopy(w1, 0, w, 0, p);

        double[] yhat = new double[n];
        Matrix.newInstance(x).ax(w, yhat);

        double TSS = 0.0;
        RSS = 0.0;
        double ybar = Math.mean(y);
        residuals = new double[n];
        for (int i = 0; i < n; i++) {
            double r = y[i] - yhat[i] - b;
            residuals[i] = r;
            RSS += Math.sqr(r);
            TSS += Math.sqr(y[i] - ybar);
        }

        error = Math.sqrt(RSS / (n - p - 1));
        df = n - p - 1;

        RSquared = 1.0 - RSS / TSS;
        adjustedRSquared = 1.0 - ((1 - RSquared) * (n-1) / (n-p-1));

        F = (TSS - RSS) * (n - p - 1) / (RSS * p);
        int df1 = p;
        int df2 = n - p - 1;
        pvalue = Beta.regularizedIncompleteBetaFunction(0.5 * df2, 0.5 * df1, df2 / (df2 + df1 * F));

        coefficients = new double[p+1][4];
        if (SVD) {
            for (int i = 0; i <= p; i++) {
                coefficients[i][0] = w1[i];
                double s = svd.getSingularValues()[i];
                if (!Math.isZero(s, 1E-10)) {
                    double se = error / svd.getSingularValues()[i];
                    coefficients[i][1] = se;
                    double t = w1[i] / se;
                    coefficients[i][2] = t;
                    coefficients[i][3] = Beta.regularizedIncompleteBetaFunction(0.5 * df, 0.5, df / (df + t * t));
                } else {
                    coefficients[i][1] = Double.NaN;
                    coefficients[i][2] = 0.0;
                    coefficients[i][3] = 1.0;
                }
            }
        } else {
            Cholesky cholesky = qr.CholeskyOfAtA();

            DenseMatrix inv = cholesky.inverse();

            for (int i = 0; i <= p; i++) {
                coefficients[i][0] = w1[i];
                double se = error * Math.sqrt(inv.get(i, i));
                coefficients[i][1] = se;
                double t = w1[i] / se;
                coefficients[i][2] = t;
                coefficients[i][3] = Beta.regularizedIncompleteBetaFunction(0.5 * df, 0.5, df / (df + t * t));
            }
        }
    }

    /**
     * Returns the t-test of the coefficients (including intercept).
     * The first column is the coefficients, the second column is the standard
     * error of coefficients, the third column is the t-score of the hypothesis
     * test if the coefficient is zero, the fourth column is the p-values of
     * test. The last row is of intercept.
     */
    public double[][] ttest() {
        return coefficients;
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
     * Returns the residual sum of squares.
     */
    public double RSS() {
        return RSS;
    }

    /**
     * Returns the residual standard error.
     */
    public double error() {
        return error;
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
     * Returns the F-statistic of goodness-of-fit.
     */
    public double ftest() {
        return F;
    }

    /**
     * Returns the p-value of goodness-of-fit test.
     */
    public double pvalue() {
        return pvalue;
    }

    @Override
    public double predict(double[] x) {
        if (x.length != p) {
            throw new IllegalArgumentException(String.format("Invalid input vector size: %d, expected: %d", x.length, p));
        }

        return b + Math.dot(x, w);
    }

    /**
     * Returns the significance code given a p-value.
     * Significance codes:  0 '***' 0.001 '**' 0.01 '*' 0.05 '.' 0.1 ' ' 1
     */
    private String significance(double pvalue) {
        if (pvalue < 0.001)
            return "***";
        else if (pvalue < 0.01)
            return "**";
        else if (pvalue < 0.05)
            return "*";
        else if (pvalue < 0.1)
            return ".";
        else
            return "";
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("Linear Model:\n");

        double[] r = residuals.clone();
        builder.append("\nResiduals:\n");
        builder.append("\t       Min\t        1Q\t    Median\t        3Q\t       Max\n");
        builder.append(String.format("\t%10.4f\t%10.4f\t%10.4f\t%10.4f\t%10.4f%n", Math.min(r), Math.q1(r), Math.median(r), Math.q3(r), Math.max(r)));

        builder.append("\nCoefficients:\n");
        builder.append("            Estimate        Std. Error        t value        Pr(>|t|)\n");
        builder.append(String.format("Intercept%11.4f%18.4f%15.4f%16.4f %s%n", coefficients[p][0], coefficients[p][1], coefficients[p][2], coefficients[p][3], significance(coefficients[p][3])));
        for (int i = 0; i < p; i++) {
            builder.append(String.format("Var %d\t %11.4f%18.4f%15.4f%16.4f %s%n", i+1, coefficients[i][0], coefficients[i][1], coefficients[i][2], coefficients[i][3], significance(coefficients[i][3])));
        }

        builder.append("---------------------------------------------------------------------\n");
        builder.append("Significance codes:  0 '***' 0.001 '**' 0.01 '*' 0.05 '.' 0.1 ' ' 1\n");

        builder.append(String.format("\nResidual standard error: %.4f on %d degrees of freedom%n", error, df));
        builder.append(String.format("Multiple R-squared: %.4f,    Adjusted R-squared: %.4f%n", RSquared, adjustedRSquared));
        builder.append(String.format("F-statistic: %.4f on %d and %d DF,  p-value: %.4g%n", F, p, df, pvalue));

        return builder.toString();
    }
}
