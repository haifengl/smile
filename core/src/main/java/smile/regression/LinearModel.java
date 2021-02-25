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

package smile.regression;

import java.util.Arrays;
import smile.data.CategoricalEncoder;
import smile.data.DataFrame;
import smile.data.Tuple;
import smile.data.formula.Formula;
import smile.data.type.StructType;
import smile.math.MathEx;
import smile.math.matrix.Matrix;
import smile.math.special.Beta;
import smile.stat.Hypothesis;

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
public class LinearModel implements OnlineRegression<double[]>, DataFrameRegression {
    private static final long serialVersionUID = 2L;
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(LinearModel.class);

    /**
     * Design matrix formula
     */
    Formula formula;
    /**
     * The schema of design matrix.
     */
    StructType schema;
    /**
     * The predictors of design matrix.
     */
    String[] predictors;
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
     * True if the linear weights w includes the intercept.
     */
    boolean bias;
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
    /**
     * First initialized to the matrix (X<sup>T</sup>X)<sup>-1</sup>,
     * it is updated with each new learning instance.
     */
    Matrix V;

    /**
     * Constructor.
     *
     * @param formula a symbolic description of the model to be fitted.
     * @param schema the schema of input data.
     * @param X the design matrix.
     * @param y the responsible variable.
     * @param w the linear weights.
     * @param b the intercept.
     */
    public LinearModel(Formula formula, StructType schema, Matrix X, double[] y, double[] w, double b) {
        this.formula = formula;
        this.schema = schema;
        this.predictors = X.colNames();
        this.p = X.ncol();
        this.w = w;
        this.b = b;
        this.bias = predictors[0].equals("Intercept");

        int n = X.nrow();
        fittedValues = new double[n];
        Arrays.fill(fittedValues, b);
        X.mv(1.0, w, 1.0, fittedValues);

        residuals = new double[n];
        RSS = 0.0;
        double TSS = 0.0;
        double ybar = MathEx.mean(y);
        for (int i = 0; i < n; i++) {
            residuals[i] = y[i] - fittedValues[i];
            RSS += MathEx.pow2(residuals[i]);
            TSS += MathEx.pow2(y[i] - ybar);
        }

        error = Math.sqrt(RSS / (n - p));
        df = n - p;

        RSquared = 1.0 - RSS / TSS;
        adjustedRSquared = 1.0 - ((1 - RSquared) * (n-1) / (n-p));

        F = (TSS - RSS) * (n - p) / (RSS * (p - 1));
        int df1 = p - 1;
        int df2 = n - p;

        if (df2 > 0) {
            pvalue = Beta.regularizedIncompleteBetaFunction(0.5 * df2, 0.5 * df1, df2 / (df2 + df1 * F));
        } else {
            logger.warn("Skip calculating p-value: the linear system is under-determined.");
            pvalue = Double.NaN;
        }
    }

    @Override
    public Formula formula() {
        return formula;
    }

    @Override
    public StructType schema() {
        return schema;
    }

    /**
     * Returns the t-test of the coefficients (including intercept).
     * The first column is the coefficients, the second column is the standard
     * error of coefficients, the third column is the t-score of the hypothesis
     * test if the coefficient is zero, the fourth column is the p-values of
     * test. The last row is of intercept.
     * @return the t-test of the coefficients.
     */
    public double[][] ttest() {
        return ttest;
    }

    /**
     * Returns the linear coefficients without intercept.
     * @return the linear coefficients without intercept.
     */
    public double[] coefficients() {
        return bias ? Arrays.copyOfRange(w, 1, w.length - 1) : w;
    }

    /**
     * Returns the intercept.
     * @return the intercept.
     */
    public double intercept() {
        return bias ? w[0] : b;
    }

    /**
     * Returns the residuals, which is response minus fitted values.
     * @return the residuals
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
     * Returns the residual standard error.
     * @return the residual standard error.
     */
    public double error() {
        return error;
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
     * @return adjusted R<sup>2</sup> statistic.
     */
    public double adjustedRSquared() {
        return adjustedRSquared;
    }

    /**
     * Returns the F-statistic of goodness-of-fit.
     * @return the F-statistic of goodness-of-fit.
     */
    public double ftest() {
        return F;
    }

    /**
     * Returns the p-value of goodness-of-fit test.
     * @return the p-value of goodness-of-fit test.
     */
    public double pvalue() {
        return pvalue;
    }

    @Override
    public double predict(double[] x) {
        double y = b;
        if (x.length == w.length) {
            for (int i = 0; i < x.length; i++) {
                y += x[i] * w[i];
            }
        } else if (bias && x.length == w.length - 1){
            y = w[0];
            for (int i = 0; i < x.length; i++) {
                y += x[i] * w[i+1];
            }
        } else {
            throw new IllegalArgumentException("Invalid vector size: " + x.length);
        }

        return y;
    }

    @Override
    public double predict(Tuple x) {
        return predict(formula.x(x).toArray(false, CategoricalEncoder.DUMMY));
    }

    @Override
    public double[] predict(DataFrame df) {
        if (bias) {
            Matrix X = formula.matrix(df, true);
            return X.mv(w);
        } else {
            Matrix X = formula.matrix(df, false);
            double[] y = new double[X.nrow()];
            Arrays.fill(y, b);
            X.mv(1.0, w, 1.0, y);
            return y;
        }
    }

    /**
     * Online update the regression model with a new training instance.
     * @param data the training data.
     */
    public void update(Tuple data) {
        update(formula.x(data).toArray(bias, CategoricalEncoder.DUMMY), formula.y(data));
    }

    /**
     * Online update the regression model with a new data frame.
     * @param data the training data.
     */
    public void update(DataFrame data) {
        // Don't use data.stream, which may run in parallel.
        // However, update is not multi-thread safe.
        int n = data.size();
        for (int i = 0; i < n; i++) {
            update(data.get(i));
        }
    }

    /**
     * Growing window recursive least squares with lambda = 1.
     * RLS updates an ordinary least squares with samples that
     * arrive sequentially.
     */
    @Override
    public void update(double[] x, double y) {
        update(x, y, 1.0);
    }

    /**
     * Recursive least squares. RLS updates an ordinary least squares with
     * samples that arrive sequentially.
     * <p>
     * In some adaptive configurations it can be useful not to give equal
     * importance to all the historical data but to assign higher weights
     * to the most recent data (and then to forget the oldest one). This
     * may happen when the phenomenon underlying the data is non stationary
     * or when we want to approximate a nonlinear dependence by using a
     * linear model which is local in time. Both these situations are common
     * in adaptive control problems.
     *
     * @param x training instance.
     * @param y response variable.
     * @param lambda The forgetting factor in (0, 1]. The smaller lambda is,
     *               the smaller is the contribution of previous samples to
     *               the covariance matrix. This makes the filter more
     *               sensitive to recent samples, which means more fluctuations
     *               in the filter coefficients. The lambda = 1 case is referred
     *               to as the growing window RLS algorithm. In practice, lambda
     *               is usually chosen between 0.98 and 1.
     */
    public void update(double[] x, double y, double lambda) {
        if (V == null) {
            throw new UnsupportedOperationException("The model doesn't support online learning");
        }

        if (lambda <= 0 || lambda > 1){
            throw new IllegalArgumentException("The forgetting factor must be in (0, 1]");
        }

        if (x.length != p) {
            throw new IllegalArgumentException(String.format("Invalid input vector size: %d, expected: %d", x.length, p));
        }

        double v = 1 + V.xAx(x);
        // If 1/v is NaN, then the update to V will no longer be invertible.
        // See https://en.wikipedia.org/wiki/Sherman%E2%80%93Morrison_formula#Statement
        if (Double.isNaN(1/v)){
            throw new IllegalStateException("The updated V matrix is no longer invertible.");
        }

        double[] Vx = V.mv(x);
        for (int j = 0; j < p; j++) {
            for (int i = 0; i < p; i++) {
                double tmp = V.get(i, j) - ((Vx[i] * Vx[j])/v);
                V.set(i, j, tmp/lambda);
            }
        }

        // V has been updated. Compute Vx again.
        V.mv(x, Vx);

        double err = y - predict(x);
        for (int i = 0; i < p; i++){
            w[i] += Vx[i] * err;
        }
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("Linear Model:\n");

        double[] r = residuals.clone();
        builder.append("\nResiduals:\n");
        builder.append("       Min          1Q      Median          3Q         Max\n");
        builder.append(String.format("%10.4f  %10.4f  %10.4f  %10.4f  %10.4f%n", MathEx.min(r), MathEx.q1(r), MathEx.median(r), MathEx.q3(r), MathEx.max(r)));

        builder.append("\nCoefficients:\n");
        if (ttest != null) {
            builder.append("                  Estimate Std. Error    t value   Pr(>|t|)\n");
            if (!bias) {
                builder.append(String.format("Intercept       %10.4f%n", b));
            }

            for (int i = 0; i < p; i++) {
                builder.append(String.format("%-15s %10.4f %10.4f %10.4f %10.4f %s%n", predictors[i], ttest[i][0], ttest[i][1], ttest[i][2], ttest[i][3], Hypothesis.significance(ttest[i][3])));
            }

            builder.append("---------------------------------------------------------------------\n");
            builder.append("Significance codes:  0 '***' 0.001 '**' 0.01 '*' 0.05 '.' 0.1 ' ' 1\n");
        } else {
            if (!bias) {
                builder.append(String.format("Intercept       %10.4f%n", b));
            }

            for (int i = 0; i < p; i++) {
                builder.append(String.format("%-15s %10.4f%n", predictors[i], w[i]));
            }
        }

        builder.append(String.format("%nResidual standard error: %.4f on %d degrees of freedom%n", error, df));
        builder.append(String.format("Multiple R-squared: %.4f,    Adjusted R-squared: %.4f%n", RSquared, adjustedRSquared));
        builder.append(String.format("F-statistic: %.4f on %d and %d DF,  p-value: %.4g%n", F, p, df, pvalue));

        return builder.toString();
    }
}
