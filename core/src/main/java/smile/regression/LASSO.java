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

import java.io.Serializable;
import java.util.Arrays;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import smile.math.Math;
import smile.math.matrix.Matrix;
import smile.math.matrix.DenseMatrix;
import smile.math.matrix.SparseMatrix;
import smile.math.matrix.BiconjugateGradient;
import smile.math.matrix.Preconditioner;
import smile.math.special.Beta;

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
public class LASSO  implements Regression<double[]>, Serializable {
    private static final long serialVersionUID = 1L;
    private static final Logger logger = LoggerFactory.getLogger(LASSO.class);

    /**
     * The dimensionality.
     */
    private int p;
    /**
     * The shrinkage/regularization parameter.
     */
    private double lambda;
    /**
     * The intercept.
     */
    private double b;
    /**
     * The linear coefficients.
     */
    private double[] w;
    /**
     * The mean of response variable.
     */
    private double ym;
    /**
     * The center of input vector. The input vector should be centered
     * before prediction.
     */
    private double[] center;
    /**
     * Scaling factor of input vector.
     */
    private double[] scale;
    /**
     * The residuals, that is response minus fitted values.
     */
    private double[] residuals;
    /**
     * Residual sum of squares.
     */
    private double RSS;
    /**
     * Residual standard error.
     */
    private double error;
    /**
     * The degree-of-freedom of residual standard error.
     */
    private int df;
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
    private double RSquared;
    /**
     * Adjusted R<sup>2</sup>. The adjusted R<sup>2</sup> has almost same
     * explanation as R<sup>2</sup> but it penalizes the statistic as
     * extra variables are included in the model.
     */
    private double adjustedRSquared;
    /**
     * The F-statistic of the goodness-of-fit of the model.
     */
    private double F;
    /**
     * The p-value of the goodness-of-fit test of the model.
     */
    private double pvalue;

    /**
     * Trainer for LASSO regression.
     */
    public static class Trainer extends RegressionTrainer<double[]> {

        /**
         * The shrinkage/regularization parameter.
         */
        private double lambda;
        /**
         * The tolerance for stopping iterations (relative target duality gap).
         */
        private double tol = 1E-3;
        /**
         * The maximum number of IPM (Newton) iterations.
         */
        private int maxIter = 1000;

        /**
         * Constructor.
         * 
         * @param lambda the number of trees.
         */
        public Trainer(double lambda) {
            if (lambda < 0.0) {
                throw new IllegalArgumentException("Invalid shrinkage/regularization parameter lambda = " + lambda);
            }

            this.lambda = lambda;
        }

        /**
         * Sets the tolerance for stopping iterations (relative target duality gap).
         * 
         * @param tol the tolerance for stopping iterations.
         */
        public Trainer setTolerance(double tol) {
            if (tol <= 0.0) {
                throw new IllegalArgumentException("Invalid tolerance: " + tol);
            }

            this.tol = tol;
            return this;
        }

        /**
         * Sets the maximum number of iterations.
         * 
         * @param maxIter the maximum number of iterations.
         */
        public Trainer setMaxNumIteration(int maxIter) {
            if (maxIter <= 0) {
                throw new IllegalArgumentException("Invalid maximum number of iterations: " + maxIter);
            }

            this.maxIter = maxIter;
            return this;
        }
        
        @Override
        public LASSO train(double[][] x, double[] y) {
            return new LASSO(x, y, lambda, tol, maxIter);
        }

        public LASSO train(Matrix x, double[] y) {
            return new LASSO(x, y, lambda, tol, maxIter);
        }
    }

    /**
     * Constructor. Learn the L1-regularized least squares model.
     * @param x a matrix containing the explanatory variables.
     *          NO NEED to include a constant column of 1s for bias.
     * @param y the response values.
     * @param lambda the shrinkage/regularization parameter.
     */
    public LASSO(double[][] x, double[] y, double lambda) {
        this(x, y, lambda, 1E-4, 1000);
    }

    /**
     * Constructor. Learn the L1-regularized least squares model.
     * @param x a matrix containing the explanatory variables.
     *          NO NEED to include a constant column of 1s for bias.
     * @param y the response values.
     * @param lambda the shrinkage/regularization parameter.
     * @param tol the tolerance for stopping iterations (relative target duality gap).
     * @param maxIter the maximum number of IPM (Newton) iterations.
     */
    public LASSO(double[][] x, double[] y, double lambda, double tol, int maxIter) {
        int n = x.length;
        int p = x[0].length;

        center = Math.colMeans(x);
        DenseMatrix X = Matrix.zeros(n, p);

        for (int i = 0; i < n; i++) {
            for (int j = 0; j < p; j++) {
                X.set(i, j, x[i][j] - center[j]);
            }
        }

        scale = new double[p];
        for (int j = 0; j < p; j++) {
            for (int i = 0; i < n; i++) {
                scale[j] += Math.sqr(X.get(i, j));
            }
            scale[j] = Math.sqrt(scale[j] / n);
        }

        for (int j = 0; j < p; j++) {
            if (!Math.isZero(scale[j])) {
                for (int i = 0; i < n; i++) {
                    X.div(i, j, scale[j]);
                }
            }
        }

        train(X, y, lambda, tol, maxIter);

        for (int j = 0; j < p; j++) {
            if (!Math.isZero(scale[j])) {
                w[j] /= scale[j];
            }
        }

        b = ym - Math.dot(w, center);
        fitness(Matrix.newInstance(x), y);
    }

    /**
     * Constructor. Learn the L1-regularized least squares model.
     * @param x a matrix containing the explanatory variables. The variables should be
     *          centered and standardized. NO NEED to include a constant column of 1s for bias.
     * @param y the response values.
     * @param lambda the shrinkage/regularization parameter.
     */
    public LASSO(Matrix x, double[] y, double lambda) {
        this(x, y, lambda, 1E-4, 1000);
    }
    
    /**
     * Constructor. Learn the L1-regularized least squares model.
     * @param x a matrix containing the explanatory variables. The variables should be
     *          centered and standardized. NO NEED to include a constant column of 1s for bias.
     * @param y the response values.
     * @param lambda the shrinkage/regularization parameter.
     * @param tol the tolerance for stopping iterations (relative target duality gap).
     * @param maxIter the maximum number of IPM (Newton) iterations.
     */
    public LASSO(Matrix x, double[] y, double lambda, double tol, int maxIter) {
        train(x, y, lambda, tol, maxIter);
        fitness(x, y);
    }

    private void train(Matrix x, double[] y, double lambda, double tol, int maxIter) {
        if (x.nrows() != y.length) {
            throw new IllegalArgumentException(String.format("The sizes of X and Y don't match: %d != %d", x.nrows(), y.length));
        }

        if (lambda <= 0.0) {
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
        p = x.ncols();

        double[] Y = new double[n];
        ym = Math.mean(y);
        for (int i = 0; i < n; i++) {
            Y[i] = y[i] - ym;
        }

        double t = Math.min(Math.max(1.0, 1.0 / lambda), 2 * p / 1e-3);
        double pobj = 0.0; // primal objective function value
        double dobj = Double.NEGATIVE_INFINITY; // dual objective function value
        double s = Double.POSITIVE_INFINITY;

        w = new double[p];
        b = ym;
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
            double maxXnu = Math.normInf(xnu);
            if (maxXnu > lambda) {
                double lnu = lambda / maxXnu;
                for (int i = 0; i < n; i++) {
                    nu[i] *= lnu;
                }
            }

            pobj = Math.dot(z, z) + lambda * Math.norm1(w);
            dobj = Math.max(-0.25 * Math.dot(nu, nu) - Math.dot(nu, Y), dobj);
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
            double normg = Math.norm(grad);
            double pcgtol = Math.min(0.1, eta * gap / Math.min(1.0, normg));
            if (ntiter != 0 && pitr == 0) {
                pcgtol = pcgtol * 0.1;
            }

            // preconditioned conjugate gradient
            double error = BiconjugateGradient.solve(pcg, pcg, grad, dxu, pcgtol, 1, pcgmaxi);
            if (error > pcgtol) {
                pitr = pcgmaxi;
            }

            for (int i = 0; i < p; i++) {
                dx[i] = dxu[i];
                du[i] = dxu[i + p];
            }

            // BACKTRACKING LINE SEARCH
            double phi = Math.dot(z, z) + lambda * Math.sum(u) - sumlogneg(f) / t;
            s = 1.0;
            double gdx = Math.dot(grad, dxu);

            int lsiter = 0;
            for (; lsiter < MAX_LS_ITER; lsiter++) {
                for (int i = 0; i < p; i++) {
                    neww[i] = w[i] + s * dx[i];
                    newu[i] = u[i] + s * du[i];
                    newf[0][i] = neww[i] - newu[i];
                    newf[1][i] = -neww[i] - newu[i];
                }

                if (Math.max(newf) < 0.0) {
                    x.ax(neww, newz);
                    for (int i = 0; i < n; i++) {
                        newz[i] -= Y[i];
                    }

                    double newphi = Math.dot(newz, newz) + lambda * Math.sum(newu) - sumlogneg(newf) / t;
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
    }

    private void fitness(Matrix x, double[] y) {
        int n = y.length;
        double[] yhat = new double[n];
        x.ax(w, yhat);

        double TSS = 0.0;
        RSS = 0.0;
        double ybar = Math.mean(y);
        residuals = new double[n];
        for (int i = 0; i < n; i++) {
            double r = y[i] - yhat[i] - b;
            residuals[i] = r;
            RSS += Math.sqr(r);
            TSS += Math.sqr(y[i] - ybar);
        }

        error = Math.sqrt(RSS / (n - p - 1));
        df = n - p - 1;

        RSquared = 1.0 - RSS / TSS;
        adjustedRSquared = 1.0 - ((1 - RSquared) * (n-1) / (n-p-1));

        F = (TSS - RSS) * (n - p - 1) / (RSS * p);
        int df1 = p;
        int df2 = n - p - 1;
        pvalue = Beta.regularizedIncompleteBetaFunction(0.5 * df2, 0.5 * df1, df2 / (df2 + df1 * F));
    }

    /**
     * Returns sum(log(-f)).
     * @param f a matrix.
     * @return sum(log(-f))
     */
    private double sumlogneg(double[][] f) {
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

    class PCGMatrix extends Matrix implements Preconditioner {

        Matrix A;
        Matrix AtA;
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
            setSymmetric(true);

            int n = A.nrows();
            ax = new double[n];
            atax = new double[p];

            if ((A.ncols() < 10000) && (A instanceof DenseMatrix)) {
                AtA = A.ata();
            }
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
        public void asolve(double[] b, double[] x) {
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

    /**
     * Returns the linear coefficients.
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
     * Returns the shrinkage parameter.
     */
    public double shrinkage() {
        return lambda;
    }

    @Override
    public double predict(double[] x) {
        if (x.length != p) {
            throw new IllegalArgumentException(String.format("Invalid input vector size: %d, expected: %d", x.length, p));
        }

        return Math.dot(x, w) + b;
    }

    /**
     * Returns the residuals, that is response minus fitted values.
     */
    public double[] residuals() {
        return residuals;
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

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("LASSO:\n");

        double[] r = residuals.clone();
        builder.append("\nResiduals:\n");
        builder.append("\t       Min\t        1Q\t    Median\t        3Q\t       Max\n");
        builder.append(String.format("\t%10.4f\t%10.4f\t%10.4f\t%10.4f\t%10.4f%n", Math.min(r), Math.q1(r), Math.median(r), Math.q3(r), Math.max(r)));

        builder.append("\nCoefficients:\n");
        builder.append("            Estimate\n");
        builder.append(String.format("Intercept%11.4f%n", b));
        for (int i = 0; i < p; i++) {
            builder.append(String.format("Var %d\t %11.4f%n", i+1, w[i]));
        }

        builder.append(String.format("\nResidual standard error: %.4f on %d degrees of freedom%n", error, df));
        builder.append(String.format("Multiple R-squared: %.4f,    Adjusted R-squared: %.4f%n", RSquared, adjustedRSquared));
        builder.append(String.format("F-statistic: %.4f on %d and %d DF,  p-value: %.4g%n", F, p, df, pvalue));

        return builder.toString();
    }
}
