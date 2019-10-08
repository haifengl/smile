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
import smile.math.matrix.DenseMatrix;
import smile.math.matrix.Matrix;

/**
 * Elastic Net regularization. The elastic net is a regularized regression
 * method that linearly combines the L1 and L2 penalties of the lasso and ridge
 * methods.
 * <p>
 * The elastic net problem can be reduced to a lasso problem on modified data
 * and response. And note that the penalty function of Elastic Net is strictly
 * convex so there is a unique global minimum, even if input data matrix is not
 * full rank.
 * 
 * <h2>References</h2>
 * <ol>
 * <li>Kevin P. Murphy: Machine Learning A Probabilistic Perspective, Section
 * 13.5.3, 2012</li>
 * <li>Zou, Hui, Hastie, Trevor: Regularization and Variable Selection via the
 * Elastic Net, 2005</li>
 * </ol>
 * 
 * @author rayeaster
 */
public class ElasticNet {
    /**
     * Fit an Elastic Net model.
     *
     * @param formula a symbolic description of the model to be fitted.
     * @param data the data frame of the explanatory and response variables.
     *             NO NEED to include a constant column of 1s for bias.
     * @param prop Training algorithm hyper-parameters and properties.
     */
    public static LinearModel fit(Formula formula, DataFrame data, Properties prop) {
        double lambda1 = Double.valueOf(prop.getProperty("smile.elastic.net.lambda1"));
        double lambda2 = Double.valueOf(prop.getProperty("smile.elastic.net.lambda2"));
        double tol = Double.valueOf(prop.getProperty("smile.elastic.net.tolerance", "1E-4"));
        int maxIter = Integer.valueOf(prop.getProperty("smile.elastic.net.max.iterations", "1000"));
        return fit(formula, data, lambda1, lambda2, tol, maxIter);
    }

    /**
     * Fit an Elastic Net model. The hyper-parameters in <code>prop</code> include
     * <ul>
     * <li><code>lambda1</code> is the shrinkage/regularization parameter for L1
     * <li><code>lambda2</code> is the shrinkage/regularization parameter for L2
     * <li><code>tolerance</code> is the tolerance for stopping iterations (relative target duality gap).
     * <li><code>max.iterations</code> is the maximum number of IPM (Newton) iterations.
     * </ul>
     *
     * @param formula a symbolic description of the model to be fitted.
     * @param data the data frame of the explanatory and response variables.
     *             NO NEED to include a constant column of 1s for bias.
     * @param lambda1 the shrinkage/regularization parameter for L1
     * @param lambda2 the shrinkage/regularization parameter for L2
     */
    public static LinearModel fit(Formula formula, DataFrame data, double lambda1, double lambda2) {
        return fit(formula, data, lambda1, lambda2, 1E-4, 1000);
    }

    /**
     * Fit an Elastic Net model. The hyper-parameters in <code>prop</code> include
     * <ul>
     * <li><code>lambda1</code> is the shrinkage/regularization parameter for L1
     * <li><code>lambda2</code> is the shrinkage/regularization parameter for L2
     * <li><code>tolerance</code> is the tolerance for stopping iterations (relative target duality gap).
     * <li><code>max.iterations</code> is the maximum number of IPM (Newton) iterations.
     * </ul>
     *
     * @param formula a symbolic description of the model to be fitted.
     * @param data the data frame of the explanatory and response variables.
     *             NO NEED to include a constant column of 1s for bias.
     * @param lambda1 the shrinkage/regularization parameter for L1
     * @param lambda2 the shrinkage/regularization parameter for L2
     * @param tol the tolerance for stopping iterations (relative target duality gap).
     * @param maxIter the maximum number of IPM (Newton) iterations.
     */
    public static LinearModel fit(Formula formula, DataFrame data, double lambda1, double lambda2, double tol, int maxIter) {
        if (lambda1 <= 0) {
            throw new IllegalArgumentException("Please use Ridge instead, wrong L1 portion setting: " + lambda1);
        }
        if (lambda2 <= 0) {
            throw new IllegalArgumentException("Please use LASSO instead, wrong L2 portion setting: " + lambda2);
        }

        double c = 1 / Math.sqrt(1 + lambda2);

        DenseMatrix X = formula.matrix(data, false);
        double[] y = formula.y(data).toDoubleArray();

        int n = X.nrows();
        int p = X.ncols();
        double[] center = X.colMeans();
        double[] scale = X.colSds();

        // Pads 0 at the tail
        double[] y2 = new double[y.length + p];
        System.arraycopy(y, 0, y2, 0, y.length);

        // Scales the original data array and pads a weighted identity matrix
        DenseMatrix X2 = Matrix.zeros(X.nrows()+ p, p);
        double padding = c * Math.sqrt(lambda2);
        for (int j = 0; j < p; j++) {
            for (int i = 0; i < n; i++) {
                X2.set(i, j, c * (X.get(i, j) - center[j]) / scale[j]);
            }

            X2.set(j + n, j, padding);
        }

        LinearModel model = LASSO.train(X2, y2,lambda1 * c, tol, maxIter);
        model.formula = formula;
        model.schema = formula.xschema();

        double[] w = new double[p];
        for (int i = 0; i < p; i++) {
            w[i] = c * model.w[i] / scale[i];
        }
        model.w = w;

        double ym = MathEx.mean(y);
        model.b = ym - MathEx.dot(model.w, center);

        double[] fittedValues = new double[y.length];
        Arrays.fill(fittedValues, model.b);
        X.axpy(model.w, fittedValues);
        model.fitness(fittedValues, y, ym);

        return model;
    }
}
