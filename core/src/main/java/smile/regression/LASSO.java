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
import smile.linalg.Transpose;
import smile.math.MathEx;
import smile.tensor.*;

/**
 * Lasso (least absolute shrinkage and selection operator) regression.
 * The Lasso is a shrinkage and selection method for linear regression.
 * It minimizes the usual sum of squared errors, with a bound on the sum
 * of the absolute values of the coefficients (i.e. L<sub>1</sub>-regularized).
 * It has connections to soft-thresholding of wavelet coefficients, forward
 * stage-wise regression, and boosting methods.
 * <p>
 * The Lasso typically yields a sparse solution, of which the parameter
 * vector &beta; has relatively few nonzero coefficients. In contrast, the
 * solution of L<sub>2</sub>-regularized least squares (i.e. ridge regression)
 * typically has all coefficients nonzero. Because it effectively
 * reduces the number of variables, the Lasso is useful in some contexts.
 * <p>
 * For over-determined systems (more instances than variables, commonly in
 * machine learning), we normalize variables with mean 0 and standard deviation
 * 1. For under-determined systems (fewer instances than variables, e.g.
 * compressed sensing), we assume white noise (i.e. no intercept in the linear
 * model) and do not perform normalization. Note that the solution
 * is not unique in this case.
 * <p>
 * There is no analytic formula or expression for the optimal solution to the
 * L<sub>1</sub>-regularized least squares problems. Therefore, its solution
 * must be computed numerically. The objective function in the
 * L<sub>1</sub>-regularized least squares is convex but not differentiable,
 * so solving it is more of a computational challenge than solving the
 * L<sub>2</sub>-regularized least squares. The Lasso may be solved using
 * quadratic programming or more general convex optimization methods, as well
 * as by specific algorithms such as the least angle regression algorithm.
 * 
 * <h2>References</h2>
 * <ol>
 * <li> R. Tibshirani. Regression shrinkage and selection via the lasso. J. Royal. Statist. Soc B., 58(1):267-288, 1996.</li> 
 * <li> B. Efron, I. Johnstone, T. Hastie, and R. Tibshirani. Least angle regression. Annals of Statistics, 2003 </li>
 * <li> Seung-Jean Kim, K. Koh, M. Lustig, Stephen Boyd, and Dimitry Gorinevsky. An Interior-Point Method for Large-Scale L1-Regularized Least Squares. IEEE JOURNAL OF SELECTED TOPICS IN SIGNAL PROCESSING, VOL. 1, NO. 4, 2007.</li>
 * </ol>
 * 
 * @author Haifeng Li
 */
public class LASSO {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(LASSO.class);

    /** Private constructor to prevent object creation. */
    private LASSO() {

    }

    /**
     * Lasso regression hyperparameters.
     * @param lambda the shrinkage/regularization parameter.
     * @param tol the tolerance of convergence test (relative target duality gap).
     * @param maxIter the maximum number of IPM (Newton) iterations.
     * @param alpha the minimum fraction of decrease in the objective function.
     * @param beta the step size decrease factor
     * @param eta the tolerance for PCG termination.
     * @param lsMaxIter the maximum number of backtracking line search iterations.
     * @param pcgMaxIter the maximum number of PCG iterations.
     */
    public record Options(double lambda, double tol, int maxIter, double alpha, double beta,
                          double eta, int lsMaxIter, int pcgMaxIter) {

        /** Constructor. */
        public Options {
            if (lambda < 0.0) {
                throw new IllegalArgumentException("Invalid shrinkage/regularization parameter lambda = " + lambda);
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
         * Constructor.
         * @param lambda the shrinkage/regularization parameter.
         */
        public Options(double lambda) {
            this(lambda, 1E-4, 1000);
        }

        /**
         * Constructor.
         * @param lambda the shrinkage/regularization parameter.
         * @param tol the tolerance of convergence test (relative target duality gap).
         * @param maxIter the maximum number of IPM (Newton) iterations.
         */
        public Options(double lambda, double tol, int maxIter) {
            this(lambda, tol, maxIter, 0.01, 0.5, 1E-3, 100, 5000);
        }

        /**
         * Returns the persistent set of hyperparameters including
         * <ul>
         * <li><code>smile.lasso.lambda</code> is the shrinkage/regularization parameter. Large lambda means more shrinkage.
         *               Choosing an appropriate value of lambda is important, and also difficult.
         * <li><code>smile.lasso.tolerance</code> is the tolerance for stopping iterations (relative target duality gap).
         * <li><code>smile.lasso.iterations</code> is the maximum number of IPM (Newton) iterations.
         * </ul>
         * @return the persistent set.
         */
        public Properties toProperties() {
            Properties props = new Properties();
            props.setProperty("smile.lasso.lambda", Double.toString(lambda));
            props.setProperty("smile.lasso.tolerance", Double.toString(tol));
            props.setProperty("smile.lasso.iterations", Integer.toString(maxIter));
            props.setProperty("smile.lasso.alpha", Double.toString(alpha));
            props.setProperty("smile.lasso.beta", Double.toString(beta));
            props.setProperty("smile.lasso.eta", Double.toString(eta));
            props.setProperty("smile.lasso.line_search_iterations", Integer.toString(lsMaxIter));
            props.setProperty("smile.lasso.pcg_iterations", Integer.toString(pcgMaxIter));
            return props;
        }

        /**
         * Returns the options from properties.
         *
         * @param props the hyperparameters.
         * @return the options.
         */
        public static Options of(Properties props) {
            double lambda = Double.parseDouble(props.getProperty("smile.lasso.lambda", "1"));
            double tol = Double.parseDouble(props.getProperty("smile.lasso.tolerance", "1E-4"));
            int maxIter = Integer.parseInt(props.getProperty("smile.lasso.iterations", "1000"));
            double alpha = Double.parseDouble(props.getProperty("smile.lasso.alpha", "0.01"));
            double beta = Double.parseDouble(props.getProperty("smile.lasso.beta", "0.5"));
            double eta = Double.parseDouble(props.getProperty("smile.lasso.eta", "1E-3"));
            int lsMaxIter = Integer.parseInt(props.getProperty("smile.lasso.line_search_iterations", "100"));
            int pcgMaxIter = Integer.parseInt(props.getProperty("smile.lasso.pcg_iterations", "5000"));
            return new Options(lambda, tol, maxIter, alpha, beta, eta, lsMaxIter, pcgMaxIter);
        }
    }

    /**
     * Fits a L1-regularized least squares model.
     * @param formula a symbolic description of the model to be fitted.
     * @param data the data frame of the explanatory and response variables.
     *             NO NEED to include a constant column of 1s for bias.
     * @return the model.
     */
    public static LinearModel fit(Formula formula, DataFrame data) {
        return fit(formula, data, new Options(1.0));
    }

    /**
     * Fits a L1-regularized least squares model.
     * @param formula a symbolic description of the model to be fitted.
     * @param data the data frame of the explanatory and response variables.
     *             NO NEED to include a constant column of 1s for bias.
     * @param options the hyperparameters.
     * @return the model.
     */
    public static LinearModel fit(Formula formula, DataFrame data, Options options) {
        formula = formula.expand(data.schema());
        StructType schema = formula.bind(data.schema());

        DenseMatrix X = formula.matrix(data, false);
        double[] y = formula.y(data).toDoubleArray();

        int n = X.nrow();
        int p = X.ncol();
        Vector center = X.colMeans();
        Vector scale = X.colSds();
        for (int j = 0; j < p; j++) {
            if (MathEx.isZero(scale.get(j))) {
                throw new IllegalArgumentException(String.format("The column '%s' is constant", schema.names()[j]));
            }
        }

        DenseMatrix scaledX = X.standardize(center, scale);
        double[] centeredY = new double[n];
        double ymu = MathEx.mean(y);
        for (int i = 0; i < n; i++) {
            centeredY[i] = y[i] - ymu;
        }

        Vector w = train(scaledX, centeredY, options);
        for (int j = 0; j < p; j++) {
            w.div(j, scale.get(j));
        }

        double b = ymu - w.dot(center);
        return new LinearModel(formula, schema, X, y, w, b);
    }

    /**
     * Fits the LASSO model.
     * @param x the design matrix.
     * @param y the responsible variable.
     * @param options the hyperparameters.
     * @return the model.
     */
    static Vector train(DenseMatrix x, double[] y, Options options) {
        // IPM parameters
        final double tol = options.tol;
        final double lambda = options.lambda;
        final int maxIter = options.maxIter;
        final int MU = 2; // updating parameter of t

        // Linear search parameters
        final double alpha = options.alpha;
        final double beta = options.beta;
        final double eta = options.eta;
        final int lsMaxIter = options.lsMaxIter;
        final int pcgMaxIter = options.pcgMaxIter;


        int pitr = 0;
        int n = x.nrow();
        int p = x.ncol();

        double t = Math.min(Math.max(1.0, 1.0 / lambda), 2 * p / 1e-3);
        double dobj = Double.NEGATIVE_INFINITY; // dual objective function value
        double s = Double.POSITIVE_INFINITY;

        double[] w = new double[p];
        double[] u = new double[p];
        double[] z = new double[n];
        double[][] f = new double[2][p];
        Arrays.fill(u, 1.0);
        for (int i = 0; i < p; i++) {
            f[0][i] =  w[i] - u[i];
            f[1][i] = -w[i] - u[i];
        }

        double[] neww = new double[p];
        double[] newu = new double[p];
        double[] newz = new double[n];
        double[][] newf = new double[2][p];

        double[] dx = new double[p];
        double[] du = new double[p];
        double[] dxu = new double[2 * p];
        double[] grad = new double[2 * p];

        // diagxtx = diag(X'X)
        // X has been standardized so that diag(X'X) is just 1.0.
        // Here we initialize it to 2.0 because we actually need 2 * diag(X'X)
        // during optimization.
        double[] diagxtx = new double[p];
        Arrays.fill(diagxtx, 2.0);

        double[] nu = new double[n];
        double[] xnu = new double[p];

        double[] q1 = new double[p];
        double[] q2 = new double[p];
        double[] d1 = new double[p];
        double[] d2 = new double[p];

        double[][] gradphi = new double[2][p];
        double[] prb = new double[p];
        double[] prs = new double[p];
        PCG pcg = new PCG(x, d1, d2, prb, prs);

        Vector w_ = Vector.column(w);
        Vector z_ = Vector.column(z);
        Vector nu_ = Vector.column(nu);
        Vector xnu_ = Vector.column(xnu);
        Vector neww_ = Vector.column(neww);
        Vector newz_ = Vector.column(newz);
        Vector grad_ = Vector.column(grad);
        Vector dxu_ = Vector.column(dxu);
        Vector gradphi0 = Vector.column(gradphi[0]);

        // MAIN LOOP
        int iter = 1;
        for (; iter <= maxIter; iter++) {
            x.mv(w_, z_);
            for (int i = 0; i < n; i++) {
                z[i] -= y[i];
                nu[i] = 2 * z[i];
            }

            // CALCULATE DUALITY GAP
            x.tv(nu_, xnu_);
            double maxXnu = xnu_.normInf();
            if (maxXnu > lambda) {
                double lnu = lambda / maxXnu;
                for (int i = 0; i < n; i++) {
                    nu[i] *= lnu;
                }
            }

            // primal objective function value
            double pobj = MathEx.dot(z, z) + lambda * MathEx.norm1(w);
            dobj = Math.max(-0.25 * MathEx.dot(nu, nu) - MathEx.dot(nu, y), dobj);

            double gap = pobj - dobj;
            if (iter % 10 == 0 || gap / dobj < tol) {
                logger.info("Iteration {}: primal objective = {}, dual objective = {}", iter, pobj, dobj);
            }

            // STOPPING CRITERION
            if (gap / dobj < tol) {
                break;
            }

            // UPDATE t
            if (s >= 0.5) {
                t = Math.max(Math.min(2 * p * MU / gap, MU * t), t);
            }

            // CALCULATE NEWTON STEP    
            for (int i = 0; i < p; i++) {
                double q1i = 1.0 / (u[i] + w[i]);
                double q2i = 1.0 / (u[i] - w[i]);
                q1[i] = q1i;
                q2[i] = q2i;
                d1[i] = (q1i * q1i + q2i * q2i) / t;
                d2[i] = (q1i * q1i - q2i * q2i) / t;
            }

            // calculate gradient
            x.tv(z_, gradphi0);
            for (int i = 0; i < p; i++) {
                gradphi[0][i] = 2 * gradphi[0][i] - (q1[i] - q2[i]) / t;
                gradphi[1][i] = lambda - (q1[i] + q2[i]) / t;
                grad[i] = -gradphi[0][i];
                grad[i + p] = -gradphi[1][i];
            }

            // calculate vectors to be used in the preconditioner
            for (int i = 0; i < p; i++) {
                prb[i] = diagxtx[i] + d1[i];
                prs[i] = prb[i] * d1[i] - d2[i] * d2[i];
            }

            // set pcg tolerance (relative)
            double normg = MathEx.norm(grad);
            double pcgtol = Math.min(0.1, eta * gap / Math.min(1.0, normg));
            if (iter != 0 && pitr == 0) {
                pcgtol = pcgtol * 0.1;
            }

            // preconditioned conjugate gradient
            double error = BiconjugateGradient.solve(pcg, grad_, dxu_, pcg, pcgtol, 1, pcgMaxIter);
            if (error > pcgtol) {
                pitr = pcgMaxIter;
            }

            for (int i = 0; i < p; i++) {
                dx[i] = dxu[i];
                du[i] = dxu[i + p];
            }

            // BACKTRACKING LINE SEARCH
            double phi = MathEx.dot(z, z) + lambda * MathEx.sum(u) - sumlogneg(f) / t;
            s = 1.0;
            double gdx = MathEx.dot(grad, dxu);

            int lsiter = 0;
            for (; lsiter < lsMaxIter; lsiter++) {
                for (int i = 0; i < p; i++) {
                    neww[i] = w[i] + s * dx[i];
                    newu[i] = u[i] + s * du[i];
                    newf[0][i] = neww[i] - newu[i];
                    newf[1][i] = -neww[i] - newu[i];
                }

                if (MathEx.max(newf) < 0.0) {
                    x.mv(neww_, newz_);
                    for (int i = 0; i < n; i++) {
                        newz[i] -= y[i];
                    }

                    double newphi = MathEx.dot(newz, newz) + lambda * MathEx.sum(newu) - sumlogneg(newf) / t;
                    if (newphi - phi <= alpha * s * gdx) {
                        break;
                    }
                }
                s = beta * s;
            }

            if (lsiter == lsMaxIter) {
                logger.warn("Linear search reaches maximum number of iterations: {}", lsMaxIter);
                break;
            }

            System.arraycopy(neww, 0, w, 0, p);
            System.arraycopy(newu, 0, u, 0, p);
            System.arraycopy(newf[0], 0, f[0], 0, p);
            System.arraycopy(newf[1], 0, f[1], 0, p);
        }

        if (iter == maxIter) {
            logger.warn("IPM reaches maximum number of iterations: {}", maxIter);
        }

        return w_;
    }

    /**
     * Returns sum(log(-f)).
     * @param f the matrix.
     * @return sum(log(-f))
     */
    private static double sumlogneg(double[][] f) {
        double sum = 0.0;
        for (double[] row : f) {
            for (double x : row) {
                sum += Math.log(-x);
            }
        }

        return sum;
    }

    /**
     * Preconditioned conjugate gradients matrix.
     */
    static class PCG implements Matrix, Preconditioner {
        /** The design matrix. */
        final DenseMatrix A;
        /** A' * A */
        Matrix AtA;
        /** The number of columns of A. */
        final int p;
        /** The right bottom of Hessian matrix. */
        final double[] d1;
        /** The last row/column of Hessian matrix. */
        final double[] d2;
        /** The vector used in preconditioner. */
        final double[] prb;
        /** The vector used in preconditioner. */
        final double[] prs;
        /** A * x */
        final Vector ax;
        /** A' * A * x. */
        final Vector atax;

        /**
         * Constructor.
         */
        PCG(DenseMatrix A, double[] d1, double[] d2, double[] prb, double[] prs) {
            this.A = A;
            this.d1 = d1;
            this.d2 = d2;
            this.prb = prb;
            this.prs = prs;

            int n = A.nrow();
            p = A.ncol();
            ax = A.vector(n);
            atax = A.vector(p);

            if (A.ncol() < 10000) {
                AtA = A.ata();
            }
        }

        @Override
        public int nrow() {
            return 2 * p;
        }

        @Override
        public int ncol() {
            return 2 * p;
        }

        @Override
        public long length() {
            return A.length();
        }

        @Override
        public ScalarType scalarType() {
            return A.scalarType();
        }

        @Override
        public void mv(Vector x, Vector y) {
            // COMPUTE AX (PCG)
            // 
            // y = hessphi * x,
            // 
            // where hessphi = [A'*A*2+D1 , D2;
            //                  D2        , D1];
            if (AtA != null) {
                AtA.mv(x, atax);
            } else {
                A.mv(x, ax);
                A.tv(ax, atax);
            }

            for (int i = 0; i < p; i++) {
                y.set(i,     2 * atax.get(i) + d1[i] * x.get(i) + d2[i] * x.get(i + p));
                y.set(i + p,                   d2[i] * x.get(i) + d1[i] * x.get(i + p));
            }
        }

        @Override
        public void tv(Vector x, Vector y) {
            mv(x, y);
        }

        @Override
        public void solve(Vector b, Vector x) {
            // COMPUTE P^{-1}X (PCG)
            // y = P^{-1} * x
            for (int i = 0; i < p; i++) {
                x.set(i,   ( d1[i] * b.get(i) -  d2[i] * b.get(i+p)) / prs[i]);
                x.set(i+p, (-d2[i] * b.get(i) + prb[i] * b.get(i+p)) / prs[i]);
            }
        }

        @Override
        public void mv(Transpose trans, double alpha, Vector x, double beta, Vector y) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void tv(Vector work, int inputOffset, int outputOffset) {
            throw new UnsupportedOperationException();
        }

        @Override
        public double get(int i, int j) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void set(int i, int j, double x) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void add(int i, int j, double x) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void sub(int i, int j, double x) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void mul(int i, int j, double x) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void div(int i, int j, double x) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Matrix scale(double alpha) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Matrix copy() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Matrix transpose() {
            throw new UnsupportedOperationException();
        }
    }
}
