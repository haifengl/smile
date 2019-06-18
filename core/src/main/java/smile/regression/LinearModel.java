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

import java.util.Arrays;
import smile.data.DataFrame;
import smile.data.formula.Formula;
import smile.math.MathEx;
import smile.math.matrix.DenseMatrix;
import smile.math.matrix.Matrix;
import smile.math.special.Beta;

/**
 * Linear model. In linear regression,
 * the model specification is that the dependent variable is a linear
 * combination of the parameters (but need not be linear in the independent
 * variables). The residual is the difference between the value of the
 * dependent variable predicted by the model, and the true value of the
 * dependent variable.
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
public class LinearModel implements Regression<double[]> {
    private static final long serialVersionUID = 1L;
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(LinearModel.class);

    /**
     * Design matrix formula
     */
    Formula formula;
    /**
     * The variable names.
     */
    String[] names;
    /**
     * The dimensionality.
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
     * Residual standard error.
     */
    double error;
    /**
     * The degree-of-freedom of residual standard error.
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
     * The F-statistic of the goodness-of-fit of the model.
     */
    double F;
    /**
     * The p-value of the goodness-of-fit test of the model.
     */
    double pvalue;

    /** Package-wise constructor. */
    LinearModel() {

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
     * Calculate the fitness of model.
     * @param fittedValues The fitted value of training data.
     * @param y The response of training data.
     * @param ybar the mean of y
     */
    void fitness(double[] fittedValues, double[] y, double ybar) {
        int n = fittedValues.length;
        this.fittedValues = fittedValues;
        this.residuals = new double[n];

        RSS = 0.0;
        double TSS = 0.0;
        for (int i = 0; i < n; i++) {
            residuals[i] = y[i] - fittedValues[i];
            RSS += MathEx.sqr(residuals[i]);
            TSS += MathEx.sqr(y[i] - ybar);
        }

        error = Math.sqrt(RSS / (n - p - 1));
        df = n - p - 1;

        RSquared = 1.0 - RSS / TSS;
        adjustedRSquared = 1.0 - ((1 - RSquared) * (n-1) / (n-p-1));

        F = (TSS - RSS) * (n - p - 1) / (RSS * p);
        int df1 = p;
        int df2 = n - p - 1;

        if (df2 > 0) {
            pvalue = Beta.regularizedIncompleteBetaFunction(0.5 * df2, 0.5 * df1, df2 / (df2 + df1 * F));
        } else {
            logger.warn("Unable to calculate to p-value: the linear system is underdetermined.");
            pvalue = Double.NaN;
        }
    }

    @Override
    public double predict(double[] x) {
        if (x.length != p) {
            throw new IllegalArgumentException(String.format("Invalid input vector size: %d, expected: %d", x.length, p));
        }

        return b + MathEx.dot(x, w);
    }

    /** Predicts the dependent variables of instances in a data frame. */
    public double[] predict(DataFrame df) {
        DenseMatrix X = formula.matrix(df, false);
        double[] y = new double[X.nrows()];
        Arrays.fill(y, b);
        X.axpy(w, y);
        return y;
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
        builder.append(String.format("\t%10.4f\t%10.4f\t%10.4f\t%10.4f\t%10.4f%n", MathEx.min(r), MathEx.q1(r), MathEx.median(r), MathEx.q3(r), MathEx.max(r)));

        builder.append("\nCoefficients:\n");
        builder.append("            Estimate        Std. Error        t value        Pr(>|t|)\n");
        builder.append(String.format("Intercept%11.4f%18.4f%15.4f%16.4f %s%n", ttest[p][0], ttest[p][1], ttest[p][2], ttest[p][3], significance(ttest[p][3])));
        for (int i = 0; i < p; i++) {
            builder.append(String.format("%s\t %11.4f%18.4f%15.4f%16.4f %s%n", names[i], ttest[i][0], ttest[i][1], ttest[i][2], ttest[i][3], significance(ttest[i][3])));
        }

        builder.append("---------------------------------------------------------------------\n");
        builder.append("Significance codes:  0 '***' 0.001 '**' 0.01 '*' 0.05 '.' 0.1 ' ' 1\n");

        builder.append(String.format("\nResidual standard error: %.4f on %d degrees of freedom%n", error, df));
        builder.append(String.format("Multiple R-squared: %.4f,    Adjusted R-squared: %.4f%n", RSquared, adjustedRSquared));
        builder.append(String.format("F-statistic: %.4f on %d and %d DF,  p-value: %.4g%n", F, p, df, pvalue));

        return builder.toString();
    }
}
