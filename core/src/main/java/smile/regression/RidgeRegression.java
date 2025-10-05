/*
 * Copyright (c) 2010-2025 Haifeng Li. All rights reserved.
 *
 * Smile is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Smile is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Smile. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.regression;

import java.util.Arrays;
import java.util.Properties;
import smile.data.DataFrame;
import smile.data.formula.Formula;
import smile.data.type.StructType;
import smile.math.MathEx;
import smile.tensor.Cholesky;
import smile.tensor.DenseMatrix;
import smile.tensor.Vector;
import smile.util.Strings;
import static smile.linalg.UPLO.*;

/**
 * Ridge Regression. Coefficient estimates for multiple linear regression
 * models rely on the independence of the model terms. When terms are
 * correlated and the columns of the design matrix X have an approximate
 * linear dependence, the matrix <code>X'X</code> becomes close to singular.
 * As a result, the least-squares estimate becomes highly sensitive to random
 * errors in the observed response <code>Y</code>, producing a large variance.
 * <p>
 * Ridge regression is one method to address these issues. In ridge regression,
 * the matrix <code>X'X</code> is perturbed to make its determinant
 * appreciably different from 0.
 * <p>
 * Ridge regression is a kind of Tikhonov regularization, which is the most
 * commonly used method of regularization of ill-posed problems.
 * Ridge regression shrinks the regression coefficients by imposing a penalty
 * on their size. By allowing a small amount of bias in the estimates, more
 * reasonable coefficients may often be obtained. Often, small amounts of bias
 * lead to dramatic reductions in the variance of the estimated model
 * coefficients.
 * <p>
 * Another interpretation of ridge regression is available through Bayesian
 * estimation. In this setting the belief that weight should be small is
 * coded into a prior distribution.
 * <p>
 * The penalty term is unfair if the predictor variables are not on the
 * same scale. Therefore, if we know that the variables are not measured
 * in the same units, we typically scale the columns of X (to have sample
 * variance 1), and then we perform ridge regression.
 * <p>
 * When including an intercept term in the regression, we usually leave
 * this coefficient unpenalized. Otherwise, we could add some constant
 * amount to the vector <code>y</code>, and this would not result in
 * the same solution. If we center the columns of <code>X</code>, then
 * the intercept estimate ends up just being the mean of <code>y</code>.
 * <p>
 * Ridge regression does not set coefficients exactly to zero unless
 * <code>&lambda; = &infin;</code>, in which case they're all zero.
 * Hence, ridge regression cannot perform variable selection, and
 * even though it performs well in terms of prediction accuracy,
 * it does poorly in terms of offering a clear interpretation.
 *
 * @author Haifeng Li
 */
public class RidgeRegression {
    /** Private constructor to prevent object creation. */
    private RidgeRegression() {

    }

    /**
     * Ridge regression hyperparameters.
     * @param lambda the shrinkage/regularization parameter. Large lambda
     *               means more shrinkage. Choosing an appropriate value of
     *               lambda is important, and also difficult. Its length may
     *               be 1 so that its value is applied to all variables.
     * @param beta0 the generalized ridge penalty target. Its length may
     *              be 1 so that its value is applied to all variables.
     */
    public record Options(double[] lambda, double[] beta0) {
        /** Constructor. */
        public Options {
            for (var value : lambda) {
                if (value < 0.0) {
                    throw new IllegalArgumentException("Invalid shrinkage/regularization parameter lambda = " + value);
                }
            }

            for (var value : beta0) {
                if (value < 0.0) {
                    throw new IllegalArgumentException("Invalid generalized ridge penalty target beta0 = " + value);
                }
            }
        }

        /**
         * Constructor.
         * @param lambda the shrinkage/regularization parameter.
         */
        public Options(double lambda) {
            this(lambda, 0.0);
        }

        /**
         * Constructor.
         * @param lambda the shrinkage/regularization parameter.
         * @param beta0 the generalized ridge penalty target.
         */
        public Options(double lambda, double beta0) {
            this(new double[]{lambda}, new double[]{beta0});
        }

        /**
         * Returns the persistent set of hyperparameters including
         * <ul>
         * <li><code>smile.ridge.lambda</code> is the shrinkage/regularization parameter. Large lambda means more shrinkage.
         *               Choosing an appropriate value of lambda is important, and also difficult.
         * <li><code>smile.ridge.standard.error</code> is a boolean. If true, compute the estimated standard
         *     errors of the estimate of parameters
         * </ul>
         * @return the persistent set.
         */
        public Properties toProperties() {
            Properties props = new Properties();
            props.setProperty("smile.ridge.lambda", Arrays.toString(lambda));
            props.setProperty("smile.ridge.beta0", Arrays.toString(beta0));
            return props;
        }

        /**
         * Returns the options from properties.
         *
         * @param props the hyperparameters.
         * @return the options.
         */
        public static Options of(Properties props) {
            var lambda = props.getProperty("smile.ridge.lambda", "1");
            var beta0 = props.getProperty("smile.ridge.beta0", "0");
            try {
                return new Options(Double.parseDouble(lambda), Double.parseDouble(beta0));
            } catch (Exception e) {
                return new Options(Strings.parseDoubleArray(lambda), Strings.parseDoubleArray(beta0));
            }
        }
    }

    /**
     * Fits a ridge regression model.
     * @param formula a symbolic description of the model to be fitted.
     * @param data the data frame of the explanatory and response variables.
     *             NO NEED to include a constant column of 1s for bias.
     * @param lambda the shrinkage/regularization parameter. Large lambda means more shrinkage.
     *               Choosing an appropriate value of lambda is important, and also difficult.
     * @return the model.
     */
    public static LinearModel fit(Formula formula, DataFrame data, double lambda) {
        int n = data.size();
        double[] weights = new double[n];
        Arrays.fill(weights, 1.0);
        return fit(formula, data, weights, new Options(lambda));
    }

    /**
     * Fits a generalized ridge regression model that minimizes a
     * weighted least squares criterion augmented with a
     * generalized ridge penalty:
     * <pre>{@code
     *     (Y - X'*beta)' * W * (Y - X'*beta) + (beta - beta0)' * lambda * (beta - beta0)
     * }</pre>
     *
     * @param formula a symbolic description of the model to be fitted.
     * @param data the data frame of the explanatory and response variables.
     *             NO NEED to include a constant column of 1s for bias.
     * @param weights sample weights.
     * @param options the hyperparameters.
     * @return the model.
     */
    public static LinearModel fit(Formula formula, DataFrame data, double[] weights, Options options) {
        formula = formula.expand(data.schema());
        StructType schema = formula.bind(data.schema());

        DenseMatrix X = formula.matrix(data, false);
        double[] y = formula.y(data).toDoubleArray();

        int n = X.nrow();
        int p = X.ncol();

        if (weights.length != n) {
            throw new IllegalArgumentException(String.format("Invalid weights vector size: %d != %d", weights.length, n));
        }

        for (int i = 0; i < n; i++) {
            if (weights[i] <= 0.0) {
                throw new IllegalArgumentException(String.format("Invalid weights[%d] = %f", i, weights[i]));
            }
        }

        var lambda = options.lambda;
        if (lambda.length == 1) {
            double shrinkage = lambda[0];
            lambda = new double[p];
            Arrays.fill(lambda, shrinkage);
        } else if (lambda.length != p) {
            throw new IllegalArgumentException(String.format("Invalid lambda vector size: %d != %d", lambda.length, p));
        }

        for (int i = 0; i < p; i++) {
            if (lambda[i] < 0.0) {
                throw new IllegalArgumentException(String.format("Invalid lambda[%d] = %f", i, lambda[i]));
            }
        }

        var beta0 = options.beta0;
        if (beta0.length == 1) {
            double beta = beta0[0];
            beta0 = new double[p];
            Arrays.fill(beta0, beta);
        } else if (beta0.length != p) {
            throw new IllegalArgumentException(String.format("Invalid beta0 vector size: %d != %d", beta0.length, p));
        }

        Vector center = X.colMeans();
        Vector scale = X.colSds();
        for (int j = 0; j < scale.size(); j++) {
            if (MathEx.isZero(scale.get(j))) {
                throw new IllegalArgumentException(String.format("The column '%s' is constant", schema.names()[j]));
            }
        }

        DenseMatrix scaledX = X.standardize(center, scale);
        DenseMatrix XtW = X.zeros(p, n);
        for (int i = 0; i < p; i++) {
            for (int j = 0; j < n; j++) {
                XtW.set(i, j, weights[j] * scaledX.get(j, i));
            }
        }

        Vector w = XtW.mv(y);
        for (int i = 0; i < p; i++) {
            w.add(i, lambda[i] * beta0[i]);
        }

        DenseMatrix XtX = XtW.mm(scaledX);
        XtX.withUplo(LOWER);
        for (int i = 0; i < XtX.nrow(); i++) {
            XtX.add(i, i, lambda[i]);
        }

        Cholesky cholesky = XtX.cholesky();
        cholesky.solve(w);
        for (int j = 0; j < p; j++) {
            w.div(j, scale.get(j));
        }

        double b = MathEx.mean(y) - w.dot(center);
        return new LinearModel(formula, schema, X, y, w, b);
    }
}
