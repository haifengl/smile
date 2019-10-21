/*******************************************************************************
 * Copyright (c) 2010-2019 Haifeng Li
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
 *******************************************************************************/

package smile.regression;

import java.util.Arrays;
import java.util.Properties;
import smile.data.DataFrame;
import smile.data.formula.Formula;
import smile.math.MathEx;
import smile.math.matrix.Cholesky;
import smile.math.matrix.DenseMatrix;

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
 * The penalty term is unfair if the predictor variables are not on the
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
 * <code>&lambda; = &infin;</code>, in which case they’re all zero.
 * Hence ridge regression cannot perform variable selection, and
 * even though it performs well in terms of prediction accuracy,
 * it does poorly in terms of offering a clear interpretation.
 *
 * @author Haifeng Li
 */
public class RidgeRegression {
    /**
     * Fits a ridge regression model.
     * @param formula a symbolic description of the model to be fitted.
     * @param data the data frame of the explanatory and response variables.
     *             NO NEED to include a constant column of 1s for bias.
     */
    public static LinearModel fit(Formula formula, DataFrame data) {
        return fit(formula, data, new Properties());
    }

    /**
     * Fits a ridge regression model. The hyper-parameters in <code>prop</code> include
     * <ul>
     * <li><code>smile.ridge.lambda</code> is the shrinkage/regularization parameter. Large lambda means more shrinkage.
     *               Choosing an appropriate value of lambda is important, and also difficult.
     * <li><code>smile.ridge.standard.error</code> is a boolean. If true, compute the estimated standard
     *     errors of the estimate of parameters
     * </ul>
     * @param formula a symbolic description of the model to be fitted.
     * @param data the data frame of the explanatory and response variables.
     *             NO NEED to include a constant column of 1s for bias.
     * @param prop Training algorithm hyper-parameters and properties.
     */
    public static LinearModel fit(Formula formula, DataFrame data, Properties prop) {
        double lambda = Double.valueOf(prop.getProperty("smile.ridge.lambda", "1"));
        return fit(formula, data, lambda);
    }

    /**
     * Fits a ridge regression model.
     * @param formula a symbolic description of the model to be fitted.
     * @param data the data frame of the explanatory and response variables.
     *             NO NEED to include a constant column of 1s for bias.
     * @param lambda the shrinkage/regularization parameter. Large lambda means more shrinkage.
     *               Choosing an appropriate value of lambda is important, and also difficult.
     */
    public static LinearModel fit(Formula formula, DataFrame data, double lambda) {
        if (lambda < 0.0) {
            throw new IllegalArgumentException("Invalid shrinkage/regularization parameter lambda = " + lambda);
        }

        DenseMatrix X = formula.matrix(data, false);
        double[] y = formula.y(data).toDoubleArray();

        int n = X.nrows();
        int p = X.ncols();

        if (n <= p) {
            throw new IllegalArgumentException(String.format("The input matrix is not over determined: %d rows, %d columns", n, p));
        }

        LinearModel model = new LinearModel();
        model.formula = formula;
        model.schema = formula.xschema();
        model.p = p;
        double[] center = X.colMeans();
        double[] scale = X.colSds();

        for (int j = 0; j < scale.length; j++) {
            if (MathEx.isZero(scale[j])) {
                throw new IllegalArgumentException(String.format("The column '%s' is constant", formula.schema().fieldName(j)));
            }
        }

        DenseMatrix scaledX = X.scale(center, scale);

        model.w = new double[p];
        scaledX.atx(y, model.w);

        DenseMatrix XtX = scaledX.ata();
        for (int i = 0; i < p; i++) {
            XtX.add(i, i, lambda);
        }
        Cholesky cholesky = XtX.cholesky();

        cholesky.solve(model.w);
        
        for (int j = 0; j < p; j++) {
            model.w[j] /= scale[j];
        }

        double ym = MathEx.mean(y);
        model.b = ym - MathEx.dot(model.w, center);

        double[] fittedValues = new double[n];
        Arrays.fill(fittedValues, model.b);
        X.axpy(model.w, fittedValues);
        model.fitness(fittedValues, y, ym);

        return model;
    }
}
