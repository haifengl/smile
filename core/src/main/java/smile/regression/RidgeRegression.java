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
import smile.math.Math;
import smile.math.matrix.Matrix;
import smile.math.matrix.Cholesky;
import smile.math.matrix.DenseMatrix;
import smile.math.special.Beta;

/**
 * Ridge Regression. Coefficient estimates for multiple linear regression models rely on
 * the independence of the model terms. When terms are correlated and
 * the columns of the design matrix X have an approximate linear dependence,
 * the matrix <code>X'X</code> becomes close to singular. As a result, the least-squares estimate
 * becomes highly sensitive to random errors in the observed response <code>Y</code>,
 * producing a large variance.
 * <p>
 * Ridge regression is one method to address these issues. In ridge regression,
 * the matrix <code>X'X</code> is perturbed so as to make its determinant appreciably
 * different from 0.
 * <p>
 * Ridge regression is a kind of Tikhonov regularization, which is the most
 * commonly used method of regularization of ill-posed problems.
 * Ridge regression shrinks the regression coefficients by imposing a penalty
 * on their size. By allowing a small amount of bias in the estimates, more
 * reasonable coefficients may often be obtained. Often, small amounts of bias lead to
 * dramatic reductions in the variance of the estimated model coefficients.</p>
 * <p>
 * Another interpretation of ridge regression is available through Bayesian estimation.
 * In this setting the belief that weight should be small is coded into a prior
 * distribution.
 * <p>
 * The penalty term is unfair is the predictor variables are not on the
 * same scale. Therefore, if we know that the variables are not measured
 * in the same units, we typically scale the columns of X (to have sample
 * variance 1), and then we perform ridge regression.
 * <p>
 * When including an intercept term in the regression, we usually leave
 * this coefficient unpenalized. Otherwise we could add some constant amount
 * to the vector <code>y</code>, and this would not result in the same solution.
 * If we center the columns of <code>X</code>, then the intercept estimate
 * ends up just being the mean of <code>y</code>.
 * <p>
 * Ridge regression doesn’t set coefficients exactly to zero unless
 * <code>&lambda; = &infin;</code>, in which case they’re all zero. Hence ridge regression cannot
 * perform variable selection, and even though it performs well in terms
 * of prediction accuracy, it does poorly in terms of offering a clear interpretation.
 *
 * @author Haifeng Li
 */
public class RidgeRegression implements Regression<double[]>, Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * The dimensionality.
     */
    private int p;
    /**
     * The shrinkage/regularization parameter.
     */
    private double lambda;
    /**
     * The centered intercept.
     */
    private double b;
    /**
     * The scaled linear coefficients.
     */
    private double[] w;
    /**
     * The mean of response variable.
     */
    private double ym;
    /**
     * The center of input vector. The input vector should be centered
     * before prediction.
     */
    private double[] center;
    /**
     * Scaling factor of input vector.
     */
    private double[] scale;
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
     * Trainer for ridge regression.
     */
    public static class Trainer extends RegressionTrainer<double[]> {

        /**
         * The shrinkage/regularization parameter.
         */
        private double lambda;

        /**
         * Constructor.
         * 
         * @param lambda the number of trees.
         */
        public Trainer(double lambda) {
            if (lambda < 0.0) {
                throw new IllegalArgumentException("Invalid shrinkage/regularization parameter lambda = " + lambda);
            }

            this.lambda = lambda;
        }

        @Override
        public RidgeRegression train(double[][] x, double[] y) {
            return new RidgeRegression(x, y, lambda);
        }
    }
    
    /**
     * Constructor. Learn the ridge regression model.
     * @param x a matrix containing the explanatory variables. NO NEED to include a constant column of 1s for bias.
     * @param y the response values.
     * @param lambda the shrinkage/regularization parameter. Large lambda means more shrinkage.
     *               Choosing an appropriate value of lambda is important, and also difficult.
     */
    public RidgeRegression(double[][] x, double[] y, double lambda) {
        if (x.length != y.length) {
            throw new IllegalArgumentException(String.format("The sizes of X and Y don't match: %d != %d", x.length, y.length));
        }

        if (lambda < 0.0) {
            throw new IllegalArgumentException("Invalid shrinkage/regularization parameter lambda = " + lambda);
        }

        int n = x.length;
        p = x[0].length;

        if (n <= p) {
            throw new IllegalArgumentException(String.format("The input matrix is not over determined: %d rows, %d columns", n, p));
        }

        ym = Math.mean(y);                
        center = Math.colMeans(x);
        
        DenseMatrix X = Matrix.zeros(n, p);
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < p; j++) {
                X.set(i, j, x[i][j] - center[j]);
            }
        }
        
        scale = new double[p];
        for (int j = 0; j < p; j++) {
            for (int i = 0; i < n; i++) {
                scale[j] += Math.sqr(X.get(i, j));
            }
            scale[j] = Math.sqrt(scale[j] / n);
        }

        for (int j = 0; j < p; j++) {
            if (!Math.isZero(scale[j])) {
                for (int i = 0; i < n; i++) {
                    X.div(i, j, scale[j]);
                }
            }
        }

        w = new double[p];
        X.atx(y, w);

        DenseMatrix XtX = X.ata();;
        for (int i = 0; i < p; i++) {
            XtX.add(i, i, lambda);
        }
        Cholesky cholesky = XtX.cholesky();;

        cholesky.solve(w);
        
        for (int j = 0; j < p; j++) {
            if (!Math.isZero(scale[j])) {
                w[j] /= scale[j];
            }
        }
        b = ym - Math.dot(w, center);

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

        DenseMatrix inv = cholesky.inverse();

        coefficients = new double[p][4];
        for (int i = 0; i < p; i++) {
            coefficients[i][0] = w[i];
            double se = error * Math.sqrt(inv.get(i, i));
            coefficients[i][1] = se;
            double t = w[i] / se;
            coefficients[i][2] = t;
            coefficients[i][3] = Beta.regularizedIncompleteBetaFunction(0.5 * df, 0.5, df / (df + t * t));
        }
    }

    /**
     * Returns the (scaled) linear coefficients.
     */
    public double[] coefficients() {
        return w;
    }

    /**
     * Returns the (centered) intercept.
     */
    public double intercept() {
        return b;
    }

    /**
     * Returns the shrinkage parameter.
     */
    public double shrinkage() {
        return lambda;
    }

    @Override
    public double predict(double[] x) {
        if (x.length != p) {
            throw new IllegalArgumentException(String.format("Invalid input vector size: %d, expected: %d", x.length, p));
        }

        return Math.dot(x, w) + b;
    }

    /**
     * Returns the t-test of the coefficients (without intercept).
     * The first column is the coefficients, the second column is the standard
     * error of coefficients, the third column is the t-score of the hypothesis
     * test if the coefficient is zero, the fourth column is the p-values of
     * test. The last row is of intercept.
     */
    public double[][] ttest() {
        return coefficients;
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
        builder.append("Ridge Regression:\n");

        double[] r = residuals.clone();
        builder.append("\nResiduals:\n");
        builder.append("\t       Min\t        1Q\t    Median\t        3Q\t       Max\n");
        builder.append(String.format("\t%10.4f\t%10.4f\t%10.4f\t%10.4f\t%10.4f%n", Math.min(r), Math.q1(r), Math.median(r), Math.q3(r), Math.max(r)));

        builder.append("\nCoefficients:\n");
        builder.append("            Estimate        Std. Error        t value        Pr(>|t|)\n");
        builder.append(String.format("Intercept%11.4f                NA             NA              NA%n", b));
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
