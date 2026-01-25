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
 * along with Smile. If not, see <https://www.gnu.org/licenses/>.
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
    /** Private constructor to prevent object creation. */
    private ElasticNet() {

    }

    /**
     * Elastic Net hyperparameters.
     * @param lambda1 the L1 shrinkage/regularization parameter
     * @param lambda2 the L2 shrinkage/regularization parameter
     * @param tol the tolerance of convergence test (relative target duality gap).
     * @param maxIter the maximum number of IPM (Newton) iterations.
     * @param alpha the minimum fraction of decrease in the objective function.
     * @param beta the step size decrease factor
     * @param eta the tolerance for PCG termination.
     * @param lsMaxIter the maximum number of backtracking line search iterations.
     * @param pcgMaxIter the maximum number of PCG iterations.
     */
    public record Options(double lambda1, double lambda2, double tol, int maxIter, double alpha,
                          double beta, double eta, int lsMaxIter, int pcgMaxIter) {
        /** Constructor. */
        public Options {
            if (lambda1 <= 0) {
                throw new IllegalArgumentException("Please use Ridge instead, wrong L1 portion setting: " + lambda1);
            }

            if (lambda2 <= 0) {
                throw new IllegalArgumentException("Please use LASSO instead, wrong L2 portion setting: " + lambda2);
            }

            if (tol <= 0) {
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
         * Constructor.
         * @param lambda1 the L1 shrinkage/regularization parameter
         * @param lambda2 the L2 shrinkage/regularization parameter
         */
        public Options(double lambda1, double lambda2) {
            this(lambda1, lambda2, 1E-4, 1000);
        }

        /**
         * Constructor.
         * @param lambda1 the L1 shrinkage/regularization parameter
         * @param lambda2 the L2 shrinkage/regularization parameter
         * @param tol the tolerance of convergence test (relative target duality gap).
         * @param maxIter the maximum number of IPM (Newton) iterations.
         */
        public Options(double lambda1, double lambda2, double tol, int maxIter) {
            this(lambda1, lambda2, tol, maxIter, 0.01, 0.5, 1E-3, 100, 5000);
        }

        /**
         * Returns the persistent set of hyperparameters including
         * <ul>
         * <li><code>smile.elastic_net.lambda1</code> is the L1 shrinkage/regularization parameter
         * <li><code>smile.elastic_net.lambda2</code> is the L2 shrinkage/regularization parameter
         * <li><code>smile.elastic_net.tolerance</code> is the tolerance for stopping iterations (relative target duality gap).
         * <li><code>smile.elastic_net.iterations</code> is the maximum number of IPM (Newton) iterations.
         * </ul>
         * @return the persistent set.
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
         * Returns the options from properties.
         *
         * @param props the hyperparameters.
         * @return the options.
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
     * Fits an Elastic Net model.
     * @param formula a symbolic description of the model to be fitted.
     * @param data the data frame of the explanatory and response variables.
     *             NO NEED to include a constant column of 1s for bias.
     * @param lambda1 the L1 shrinkage/regularization parameter
     * @param lambda2 the L2 shrinkage/regularization parameter
     * @return the model.
     */
    public static LinearModel fit(Formula formula, DataFrame data, double lambda1, double lambda2) {
        return fit(formula, data, new Options(lambda1, lambda2));
    }

    /**
     * Fits an Elastic Net model.
     *
     * @param formula a symbolic description of the model to be fitted.
     * @param data the data frame of the explanatory and response variables.
     *             NO NEED to include a constant column of 1s for bias.
     * @param options the hyperparameters.
     * @return the model.
     */
    public static LinearModel fit(Formula formula, DataFrame data, Options options) {
        double c = 1 / Math.sqrt(1 + options.lambda2);

        formula = formula.expand(data.schema());
        StructType schema = formula.bind(data.schema());

        DenseMatrix X = formula.matrix(data, false);
        double[] y = formula.y(data).toDoubleArray();

        int n = X.nrow();
        int p = X.ncol();
        Vector center = X.colMeans();
        Vector scale = X.colSds();

        // Pads 0 at the tail
        double[] centeredY = new double[n + p];

        // Center y2 before calling LASSO.
        // Otherwise, padding zeros become negative when LASSO centers y2 again.
        double ymu = MathEx.mean(y);
        for (int i = 0; i < n; i++) {
            centeredY[i] = y[i] - ymu;
        }

        // Scales the original data array and pads a weighted identity matrix
        DenseMatrix scaledX = X.zeros(X.nrow() + p, p);
        double padding = c * Math.sqrt(options.lambda2);
        for (int j = 0; j < p; j++) {
            for (int i = 0; i < n; i++) {
                scaledX.set(i, j, c * (X.get(i, j) - center.get(j)) / scale.get(j));
            }

            scaledX.set(j + n, j, padding);
        }

        var lasso = new LASSO.Options(options.lambda1 * c, options.tol, options.maxIter,
                options.alpha, options.beta, options.eta, options.lsMaxIter, options.pcgMaxIter);
        Vector w = LASSO.train(scaledX, centeredY, lasso);
        for (int i = 0; i < p; i++) {
            w.set(i, c * w.get(i) / scale.get(i));
        }

        double b = ymu - w.dot(center);
        return new LinearModel(formula, schema, X, y, w, b);
    }
}
