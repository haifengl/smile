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
import smile.math.matrix.Matrix;
import smile.math.matrix.DenseMatrix;
import smile.math.matrix.BiconjugateGradient;
import smile.math.matrix.Preconditioner;

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
 * 1. For under-determined systems (less instances than variables, e.g.
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
     */
    public static LinearModel fit(Formula formula, DataFrame data) {
        return fit(formula, data, new Properties());
    }

    /**
     * Fits a L1-regularized least squares model. The hyper-parameters in <code>prop</code> include
     * <ul>
     * <li><code>smile.lasso.lambda</code> is the shrinkage/regularization parameter. Large lambda means more shrinkage.
     *               Choosing an appropriate value of lambda is important, and also difficult.
     * <li><code>smile.lasso.tolerance</code> is the tolerance for stopping iterations (relative target duality gap).
     * <li><code>smile.lasso.max.iterations</code> is the maximum number of IPM (Newton) iterations.
     * </ul>
     * @param formula a symbolic description of the model to be fitted.
     * @param data the data frame of the explanatory and response variables.
     *             NO NEED to include a constant column of 1s for bias.
     * @param prop Training algorithm hyper-parameters and properties.
     */
    public static LinearModel fit(Formula formula, DataFrame data, Properties prop) {
        double lambda = Double.valueOf(prop.getProperty("smile.lasso.lambda", "1"));
        double tol = Double.valueOf(prop.getProperty("smile.lasso.tolerance", "1E-4"));
        int maxIter = Integer.valueOf(prop.getProperty("smile.lasso.max.iterations", "1000"));
        return fit(formula, data, lambda, tol, maxIter);
    }

    /**
     * Fits a L1-regularized least squares model.
     * @param formula a symbolic description of the model to be fitted.
     * @param data the data frame of the explanatory and response variables.
     *             NO NEED to include a constant column of 1s for bias.
     * @param lambda the shrinkage/regularization parameter.
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
     * @param tol the tolerance for stopping iterations (relative target duality gap).
     * @param maxIter the maximum number of IPM (Newton) iterations.
     */
    public static LinearModel fit(Formula formula, DataFrame data, double lambda, double tol, int maxIter) {
        DenseMatrix X = formula.matrix(data, false);
        double[] y = formula.y(data).toDoubleArray();

        double[] center = X.colMeans();
        double[] scale = X.colSds();

        for (int j = 0; j < scale.length; j++) {
            if (MathEx.isZero(scale[j])) {
                throw new IllegalArgumentException(String.format("The column '%s' is constant", formula.xschema().fieldName(j)));
            }
        }

        DenseMatrix scaledX = X.scale(center, scale);

        LinearModel model = train(scaledX, y, lambda, tol, maxIter);
        model.formula = formula;
        model.schema = formula.xschema();

        for (int j = 0; j < model.p; j++) {
            model.w[j] /= scale[j];
        }

        double ym = MathEx.mean(y);
        model.b = ym - MathEx.dot(model.w, center);

        double[] fittedValues = new double[y.length];
        Arrays.fill(fittedValues, model.b);
        X.axpy(model.w, fittedValues);
        model.fitness(fittedValues, y, ym);

        return model;
    }

    static LinearModel train(Matrix x, double[] y, double lambda, double tol, int maxIter) {
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
        int n = x.nrows();
        int p = x.ncols();

        double[] Y = new double[n];
        double ym = MathEx.mean(y);
        for (int i = 0; i < n; i++) {
            Y[i] = y[i] - ym;
        }

        double t = Math.min(Math.max(1.0, 1.0 / lambda), 2 * p / 1e-3);
        double pobj = 0.0; // primal objective function value
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

        PCGMatrix pcg = new PCGMatrix(x, d1, d2, prb, prs);

        // MAIN LOOP
        int ntiter = 0;
        for (; ntiter <= maxIter; ntiter++) {
            x.ax(w, z);
            for (int i = 0; i < n; i++) {
                z[i] -= Y[i];
                nu[i] = 2 * z[i];
            }

            // CALCULATE DUALITY GAP
            x.atx(nu, xnu);
            double maxXnu = MathEx.normInf(xnu);
            if (maxXnu > lambda) {
                double lnu = lambda / maxXnu;
                for (int i = 0; i < n; i++) {
                    nu[i] *= lnu;
                }
            }

            pobj = MathEx.dot(z, z) + lambda * MathEx.norm1(w);
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
            x.atx(z, gradphi[0]);
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
            BiconjugateGradient bfgs = new BiconjugateGradient(pcgtol, 1, pcgmaxi);
            bfgs.setPreconditioner(pcg);
            double error = bfgs.solve(pcg, grad, dxu);
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
                    x.ax(neww, newz);
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

        LinearModel model = new LinearModel();
        model.p = p;
        model.w = w;

        return model;
    }

    /**
     * Returns sum(log(-f)).
     * @param f a matrix.
     * @return sum(log(-f))
     */
    private static double sumlogneg(double[][] f) {
        int m = f.length;
        int n = f[0].length;

        double sum = 0.0;
        for (int i = 0; i < m; i++) {
            for (int j = 0; j < n; j++) {
                sum += Math.log(-f[i][j]);
            }
        }

        return sum;
    }

    static class PCGMatrix implements Matrix, Preconditioner {

        Matrix A;
        Matrix AtA;
        int p;
        double[] d1;
        double[] d2;
        double[] prb;
        double[] prs;
        double[] ax;
        double[] atax;

        PCGMatrix(Matrix A, double[] d1, double[] d2, double[] prb, double[] prs) {
            this.A = A;
            this.d1 = d1;
            this.d2 = d2;
            this.prb = prb;
            this.prs = prs;

            int n = A.nrows();
            p = A.ncols();
            ax = new double[n];
            atax = new double[p];

            if ((A.ncols() < 10000) && (A instanceof DenseMatrix)) {
                AtA = A.ata();
            }
        }

        @Override
        public boolean isSymmetric() {
            return true;
        }

        @Override
        public int nrows() {
            return 2 * p;
        }

        @Override
        public int ncols() {
            return 2 * p;
        }

        @Override
        public double[] ax(double[] x, double[] y) {
            // COMPUTE AX (PCG)
            // 
            // y = hessphi * x,
            // 
            // where hessphi = [A'*A*2+D1 , D2;
            //                  D2        , D1];
            if (AtA != null) {
                AtA.ax(x, atax);
            } else {
                A.ax(x, ax);
                A.atx(ax, atax);
            }

            for (int i = 0; i < p; i++) {
                y[i]     = 2 * atax[i] + d1[i] * x[i] + d2[i] * x[i + p];
                y[i + p] =               d2[i] * x[i] + d1[i] * x[i + p];
            }

            return y;
        }

        @Override
        public double[] atx(double[] x, double[] y) {
            return ax(x, y);
        }

        @Override
        public void solve(double[] b, double[] x) {
            // COMPUTE P^{-1}X (PCG)
            //
            // y = P^{-1} * x
            for (int i = 0; i < p; i++) {
                x[i]   = ( d1[i] * b[i] -  d2[i] * b[i+p]) / prs[i];
                x[i+p] = (-d2[i] * b[i] + prb[i] * b[i+p]) / prs[i];
            }
        }

        @Override
        public Matrix transpose() {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public Matrix aat() {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public Matrix ata() {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public double get(int i, int j) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public double apply(int i, int j) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public double[] axpy(double[] x, double[] y) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public double[] axpy(double[] x, double[] y, double b) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public double[] atxpy(double[] x, double[] y) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public double[] atxpy(double[] x, double[] y, double b) {
            throw new UnsupportedOperationException("Not supported yet.");
        }
    }
}
