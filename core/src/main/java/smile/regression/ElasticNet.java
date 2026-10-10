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
package smile.regression;

import java.util.Properties;
import smile.data.DataFrame;
import smile.data.formula.Formula;
import smile.data.type.StructType;
import smile.math.MathEx;
import smile.tensor.DenseMatrix;
import smile.tensor.Vector;

/**
 * Elastic Net regularized linear regression.
 * <p>
 * Elastic Net linearly combines the L1 (LASSO) and L2 (Ridge) penalties,
 * overcoming limitations of LASSO when features are highly correlated or
 * when the number of features exceeds the sample size (p &gt; n).
 * <p>
 * The optimization problem can be reformulated as an equivalent LASSO problem
 * on augmented response and covariate matrices. Because the strictly convex
 * L2 penalty guarantees a unique global minimum, the objective remains strictly
 * convex even when the feature matrix is not full rank.
 *
 * <h2>References</h2>
 * <ul>
 *   <li>H. Zou and T. Hastie. Regularization and variable selection via the
 *       elastic net. <i>Journal of the Royal Statistical Society: Series B</i>,
 *       67(2):301-320, 2005.</li>
 *   <li>K. P. Murphy. <i>Machine Learning: A Probabilistic Perspective</i>,
 *       Section 13.5.3. MIT Press, 2012.</li>
 * </ul>
 *
 * @author Haifeng Li
 */
public class ElasticNet {

    /** Private constructor. */
    private ElasticNet() {}

    /**
     * Hyperparameters for Elastic Net regression.
     *
     * @param lambda1    L1 regularization penalty parameter.
     * @param lambda2    L2 regularization penalty parameter.
     * @param tol        convergence tolerance on relative duality gap.
     * @param maxIter    maximum interior point method (IPM) iterations.
     * @param alpha      objective function decrease ratio threshold.
     * @param beta       backtracking line search step contraction factor.
     * @param eta        preconditioned conjugate gradient termination tolerance.
     * @param lsMaxIter  maximum backtracking line search iterations.
     * @param pcgMaxIter maximum conjugate gradient iterations.
     */
    public record Options(double lambda1, double lambda2, double tol, int maxIter, double alpha,
                          double beta, double eta, int lsMaxIter, int pcgMaxIter) {

        /** Validates hyperparameter values. **/
        public Options {
            if (lambda1 <= 0.0) {
                throw new IllegalArgumentException("Please use Ridge instead, wrong L1 portion setting: " + lambda1);
            }
            if (lambda2 <= 0.0) {
                throw new IllegalArgumentException("Please use LASSO instead, wrong L2 portion setting: " + lambda2);
            }
            if (tol <= 0.0) {
                throw new IllegalArgumentException("Invalid tolerance: " + tol);
            }
            if (maxIter <= 0) {
                throw new IllegalArgumentException("Invalid maximum number of iterations: " + maxIter);
            }
            if (alpha <= 0.0) {
                throw new IllegalArgumentException("Invalid alpha: " + alpha);
            }
            if (beta <= 0.0) {
                throw new IllegalArgumentException("Invalid beta: " + beta);
            }
            if (eta <= 0.0) {
                throw new IllegalArgumentException("Invalid eta: " + eta);
            }
            if (lsMaxIter <= 0) {
                throw new IllegalArgumentException("Invalid maximum number of line search iterations: " + lsMaxIter);
            }
            if (pcgMaxIter <= 0) {
                throw new IllegalArgumentException("Invalid maximum number of PCG iterations: " + pcgMaxIter);
            }
        }

        /**
         * Constructs options with default solver tolerances and iteration limits.
         *
         * @param lambda1 L1 regularization penalty.
         * @param lambda2 L2 regularization penalty.
         */
        public Options(double lambda1, double lambda2) {
            this(lambda1, lambda2, 1E-4, 1000);
        }

        /**
         * Constructs options with specified convergence tolerance and maximum iterations.
         *
         * @param lambda1 L1 regularization penalty.
         * @param lambda2 L2 regularization penalty.
         * @param tol     convergence tolerance.
         * @param maxIter maximum iterations.
         */
        public Options(double lambda1, double lambda2, double tol, int maxIter) {
            this(lambda1, lambda2, tol, maxIter, 0.01, 0.5, 1E-3, 100, 5000);
        }

        /**
         * Serializes options to a Properties map.
         *
         * @return persistent configuration properties.
         */
        public Properties toProperties() {
            Properties props = new Properties();
            props.setProperty("smile.elastic_net.lambda1", Double.toString(lambda1));
            props.setProperty("smile.elastic_net.lambda2", Double.toString(lambda2));
            props.setProperty("smile.elastic_net.tolerance", Double.toString(tol));
            props.setProperty("smile.elastic_net.iterations", Integer.toString(maxIter));
            props.setProperty("smile.elastic_net.alpha", Double.toString(alpha));
            props.setProperty("smile.elastic_net.beta", Double.toString(beta));
            props.setProperty("smile.elastic_net.eta", Double.toString(eta));
            props.setProperty("smile.elastic_net.line_search_iterations", Integer.toString(lsMaxIter));
            props.setProperty("smile.elastic_net.pcg_iterations", Integer.toString(pcgMaxIter));
            return props;
        }

        /**
         * Deserializes options from a Properties instance.
         *
         * @param props configuration properties.
         * @return parsed Options record.
         */
        public static Options of(Properties props) {
            double lambda1 = Double.parseDouble(props.getProperty("smile.elastic_net.lambda1"));
            double lambda2 = Double.parseDouble(props.getProperty("smile.elastic_net.lambda2"));
            double tol = Double.parseDouble(props.getProperty("smile.elastic_net.tolerance", "1E-4"));
            int maxIter = Integer.parseInt(props.getProperty("smile.elastic_net.iterations", "1000"));
            double alpha = Double.parseDouble(props.getProperty("smile.elastic_net.alpha", "0.01"));
            double beta = Double.parseDouble(props.getProperty("smile.elastic_net.beta", "0.5"));
            double eta = Double.parseDouble(props.getProperty("smile.elastic_net.eta", "1E-3"));
            int lsMaxIter = Integer.parseInt(props.getProperty("smile.elastic_net.line_search_iterations", "100"));
            int pcgMaxIter = Integer.parseInt(props.getProperty("smile.elastic_net.pcg_iterations", "5000"));
            return new Options(lambda1, lambda2, tol, maxIter, alpha, beta, eta, lsMaxIter, pcgMaxIter);
        }
    }

    /**
     * Fits an Elastic Net regularized linear model.
     *
     * @param formula formula specifying response and predictors.
     * @param data    training data frame.
     * @param lambda1 L1 penalty coefficient.
     * @param lambda2 L2 penalty coefficient.
     * @return fitted LinearModel.
     */
    public static LinearModel fit(Formula formula, DataFrame data, double lambda1, double lambda2) {
        return fit(formula, data, new Options(lambda1, lambda2));
    }

    /**
     * Fits an Elastic Net regularized linear model with full options.
     *
     * @param formula formula specifying response and predictors.
     * @param data    training data frame.
     * @param options algorithm hyperparameters.
     * @return fitted LinearModel.
     */
    public static LinearModel fit(Formula formula, DataFrame data, Options options) {
        double c = 1.0 / Math.sqrt(1.0 + options.lambda2);

        formula = formula.expand(data.schema());
        StructType schema = formula.bind(data.schema());

        DenseMatrix X = formula.matrix(data, false);
        double[] y = formula.y(data).toDoubleArray();

        int n = X.nrow();
        int p = X.ncol();
        Vector center = X.colMeans();
        Vector scale = X.colSds();
        // validate column variance
        for (int j = 0; j < p; j++) {
            if (MathEx.isZero(scale.get(j))) {
                throw new IllegalArgumentException(String.format("The column '%s' is constant", schema.names()[j]));
            }
        }

        // Augmented response vector padded with p zeros
        double[] centeredY = new double[n + p];
        double ymu = MathEx.mean(y);
        for (int i = 0; i < n; i++) {
            centeredY[i] = y[i] - ymu;
        }

        // Augmented design matrix: scaled predictors stacked with scaled identity block
        DenseMatrix scaledX = X.zeros(n + p, p);
        double padding = c * Math.sqrt(options.lambda2);
        for (int j = 0; j < p; j++) {
            double scaleJ = scale.get(j);
            double centerJ = center.get(j);
            for (int i = 0; i < n; i++) {
                scaledX.set(i, j, c * (X.get(i, j) - centerJ) / scaleJ);
            }
            scaledX.set(n + j, j, padding);
        }

        // Solve augmented LASSO optimization
        var lasso = new LASSO.Options(options.lambda1 * c, options.tol, options.maxIter,
                options.alpha, options.beta, options.eta, options.lsMaxIter, options.pcgMaxIter);
        // transform back to original scale
        Vector w = LASSO.train(scaledX, centeredY, lasso);
        for (int i = 0; i < p; i++) {
            w.set(i, c * w.get(i) / scale.get(i));
        }

        double b = ymu - w.dot(center);
        return new LinearModel(formula, schema, X, y, w, b);
    }
}
