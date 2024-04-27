/*
 * Copyright (c) 2010-2021 Haifeng Li. All rights reserved.
 *
 * Smile is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Smile is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Smile.  If not, see <https://www.gnu.org/licenses/>.
 */

package smile.regression;

import java.util.Arrays;
import java.util.Properties;
import smile.data.DataFrame;
import smile.data.formula.Formula;
import smile.data.type.StructType;
import smile.math.MathEx;
import smile.math.blas.Transpose;
import smile.math.matrix.IMatrix;
import smile.math.matrix.Matrix;

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

    /**
     * Fits a L1-regularized least squares model.
     * @param formula a symbolic description of the model to be fitted.
     * @param data the data frame of the explanatory and response variables.
     *             NO NEED to include a constant column of 1s for bias.
     * @return the model.
     */
    public static LinearModel fit(Formula formula, DataFrame data) {
        return fit(formula, data, new Properties());
    }

    /**
     * Fits a L1-regularized least squares model. The hyperparameters in <code>prop</code> include
     * <ul>
     * <li><code>smile.lasso.lambda</code> is the shrinkage/regularization parameter. Large lambda means more shrinkage.
     *               Choosing an appropriate value of lambda is important, and also difficult.
     * <li><code>smile.lasso.tolerance</code> is the tolerance for stopping iterations (relative target duality gap).
     * <li><code>smile.lasso.iterations</code> is the maximum number of IPM (Newton) iterations.
     * </ul>
     * @param formula a symbolic description of the model to be fitted.
     * @param data the data frame of the explanatory and response variables.
     *             NO NEED to include a constant column of 1s for bias.
     * @param params the hyperparameters.
     * @return the model.
     */
    public static LinearModel fit(Formula formula, DataFrame data, Properties params) {
        double lambda = Double.parseDouble(params.getProperty("smile.lasso.lambda", "1"));
        double tol = Double.parseDouble(params.getProperty("smile.lasso.tolerance", "1E-4"));
        int maxIter = Integer.parseInt(params.getProperty("smile.lasso.iterations", "1000"));
        return fit(formula, data, lambda, tol, maxIter);
    }

    /**
     * Fits a L1-regularized least squares model.
     * @param formula a symbolic description of the model to be fitted.
     * @param data the data frame of the explanatory and response variables.
     *             NO NEED to include a constant column of 1s for bias.
     * @param lambda the shrinkage/regularization parameter.
     * @return the model.
     */
    public static LinearModel fit(Formula formula, DataFrame data, double lambda) {
        return fit(formula, data, lambda, 1E-4, 1000);
    }

    /**
     * Fits a L1-regularized least squares model.
     * @param formula a symbolic description of the model to be fitted.
     * @param data the data frame of the explanatory and response variables.
     *             NO NEED to include a constant column of 1s for bias.
     * @param lambda the shrinkage/regularization parameter.
     * @param tol the tolerance to stop iterations (relative target duality gap).
     * @param maxIter the maximum number of IPM (Newton) iterations.
     * @return the model.
     */
    public static LinearModel fit(Formula formula, DataFrame data, double lambda, double tol, int maxIter) {
        formula = formula.expand(data.schema());
        StructType schema = formula.bind(data.schema());

        Matrix X = formula.matrix(data, false);
        double[] y = formula.y(data).toDoubleArray();

        double[] center = X.colMeans();
        double[] scale = X.colSds();

        for (int j = 0; j < scale.length; j++) {
            if (MathEx.isZero(scale[j])) {
                throw new IllegalArgumentException(String.format("The column '%s' is constant", X.colName(j)));
            }
        }

        Matrix scaledX = X.scale(center, scale);

        double[] w = train(scaledX, y, lambda, tol, maxIter);

        int p = w.length;
        for (int j = 0; j < p; j++) {
            w[j] /= scale[j];
        }

        double b = MathEx.mean(y) - MathEx.dot(w, center);
        return new LinearModel(formula, schema, X, y, w, b);
    }

    /**
     * Fits the LASSO model.
     * @param x the design matrix.
     * @param y the responsible variable.
     * @param lambda the shrinkage/regularization parameter.
     * @param tol the tolerance for stopping iterations (relative target duality gap).
     * @param maxIter the maximum number of IPM (Newton) iterations.
     * @return the model.
     */
    static double[] train(Matrix x, double[] y, double lambda, double tol, int maxIter) {
        if (lambda < 0.0) {
            throw new IllegalArgumentException("Invalid shrinkage/regularization parameter lambda = " + lambda);
        }

        if (tol <= 0.0) {
            throw new IllegalArgumentException("Invalid tolerance: " + tol);
        }

        if (maxIter <= 0) {
            throw new IllegalArgumentException("Invalid maximum number of iterations: " + maxIter);
        }

        // INITIALIZE
        // IPM PARAMETERS
        final int MU = 2;             // updating parameter of t

        // LINE SEARCH PARAMETERS
        final double ALPHA = 0.01;    // minimum fraction of decrease in the objective
        final double BETA = 0.5;      // stepsize decrease factor
        final int MAX_LS_ITER = 100;  // maximum backtracking line search iteration
        final int pcgmaxi = 5000; // maximum number of maximum PCG iterations
        final double eta = 1E-3;  // tolerance for PCG termination

        int pitr = 0;
        int n = x.nrow();
        int p = x.ncol();

        double[] Y = new double[n];
        double ym = MathEx.mean(y);
        for (int i = 0; i < n; i++) {
            Y[i] = y[i] - ym;
        }

        double t = Math.min(Math.max(1.0, 1.0 / lambda), 2 * p / 1e-3);
        double dobj = Double.NEGATIVE_INFINITY; // dual objective function value
        double s = Double.POSITIVE_INFINITY;

        double[] w = new double[p];
        double[] u = new double[p];
        double[] z = new double[n];
        double[][] f = new double[2][p];
        Arrays.fill(u, 1.0);
        for (int i = 0; i < p; i++) {
            f[0][i] = w[i] - u[i];
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

        // MAIN LOOP
        int ntiter = 0;
        for (; ntiter <= maxIter; ntiter++) {
            x.mv(w, z);
            for (int i = 0; i < n; i++) {
                z[i] -= Y[i];
                nu[i] = 2 * z[i];
            }

            // CALCULATE DUALITY GAP
            x.tv(nu, xnu);
            double maxXnu = MathEx.normInf(xnu);
            if (maxXnu > lambda) {
                double lnu = lambda / maxXnu;
                for (int i = 0; i < n; i++) {
                    nu[i] *= lnu;
                }
            }

            // primal objective function value
            double pobj = MathEx.dot(z, z) + lambda * MathEx.norm1(w);
            dobj = Math.max(-0.25 * MathEx.dot(nu, nu) - MathEx.dot(nu, Y), dobj);
            if (ntiter % 10 == 0) {
                logger.info(String.format("LASSO: primal and dual objective function value after %3d iterations: %.5g\t%.5g%n", ntiter, pobj, dobj));
            }

            double gap = pobj - dobj;
            // STOPPING CRITERION
            if (gap / dobj < tol) {
                logger.info(String.format("LASSO: primal and dual objective function value after %3d iterations: %.5g\t%.5g%n", ntiter, pobj, dobj));
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
            x.tv(z, gradphi[0]);
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
            if (ntiter != 0 && pitr == 0) {
                pcgtol = pcgtol * 0.1;
            }

            // preconditioned conjugate gradient
            double error = pcg.solve(grad, dxu, pcg, pcgtol, 1, pcgmaxi);
            if (error > pcgtol) {
                pitr = pcgmaxi;
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
            for (; lsiter < MAX_LS_ITER; lsiter++) {
                for (int i = 0; i < p; i++) {
                    neww[i] = w[i] + s * dx[i];
                    newu[i] = u[i] + s * du[i];
                    newf[0][i] = neww[i] - newu[i];
                    newf[1][i] = -neww[i] - newu[i];
                }

                if (MathEx.max(newf) < 0.0) {
                    x.mv(neww, newz);
                    for (int i = 0; i < n; i++) {
                        newz[i] -= Y[i];
                    }

                    double newphi = MathEx.dot(newz, newz) + lambda * MathEx.sum(newu) - sumlogneg(newf) / t;
                    if (newphi - phi <= ALPHA * s * gdx) {
                        break;
                    }
                }
                s = BETA * s;
            }

            if (lsiter == MAX_LS_ITER) {
                logger.error("LASSO: Too many iterations of line search.");
                break;
            }

            System.arraycopy(neww, 0, w, 0, p);
            System.arraycopy(newu, 0, u, 0, p);
            System.arraycopy(newf[0], 0, f[0], 0, p);
            System.arraycopy(newf[1], 0, f[1], 0, p);
        }

        if (ntiter == maxIter) {
            logger.error("LASSO: Too many iterations.");
        }

        return w;
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
    static class PCG extends IMatrix implements IMatrix.Preconditioner {
        /** The design matrix. */
        final Matrix A;
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
        final double[] ax;
        /** A' * A * x. */
        final double[] atax;

        /**
         * Constructor.
         */
        PCG(Matrix A, double[] d1, double[] d2, double[] prb, double[] prs) {
            this.A = A;
            this.d1 = d1;
            this.d2 = d2;
            this.prb = prb;
            this.prs = prs;

            int n = A.nrow();
            p = A.ncol();
            ax = new double[n];
            atax = new double[p];

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
        public long size() {
            return A.size();
        }

        @Override
        public void mv(double[] x, double[] y) {
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
                y[i]     = 2 * atax[i] + d1[i] * x[i] + d2[i] * x[i + p];
                y[i + p] =               d2[i] * x[i] + d1[i] * x[i + p];
            }
        }

        @Override
        public void tv(double[] x, double[] y) {
            mv(x, y);
        }

        @Override
        public void asolve(double[] b, double[] x) {
            // COMPUTE P^{-1}X (PCG)
            // y = P^{-1} * x
            for (int i = 0; i < p; i++) {
                x[i]   = ( d1[i] * b[i] -  d2[i] * b[i+p]) / prs[i];
                x[i+p] = (-d2[i] * b[i] + prb[i] * b[i+p]) / prs[i];
            }
        }

        @Override
        public void mv(Transpose trans, double alpha, double[] x, double beta, double[] y) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void mv(double[] work, int inputOffset, int outputOffset) {
            throw new UnsupportedOperationException();
        }


        @Override
        public void tv(double[] work, int inputOffset, int outputOffset) {
            throw new UnsupportedOperationException();
        }
    }
}
