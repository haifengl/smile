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

import java.util.Arrays;
import smile.math.Math;
import smile.math.matrix.IMatrix;

/**
 * Least absolute shrinkage and selection operator.
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
public class LASSO  implements Regression<double[]> {

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
        private int maxIter = 500;

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
    }
    
    /**
     * Constructor. Learn the L1-regularized least squares model.
     * @param x a matrix containing the explanatory variables.
     * @param y the response values.
     * @param lambda the shrinkage/regularization parameter.
     */
    public LASSO(double[][] x, double[] y, double lambda) {
        this(x, y, lambda, 1E-3, 5000);
    }
    
    /**
     * Constructor. Learn the L1-regularized least squares model.
     * @param x a matrix containing the explanatory variables.
     * @param y the response values.
     * @param lambda the shrinkage/regularization parameter.
     * @param tol the tolerance for stopping iterations (relative target duality gap).
     * @param maxIter the maximum number of iterations.
     */
    public LASSO(double[][] x, double[] y, double lambda, double tol, int maxIter) {
        if (x.length != y.length) {
            throw new IllegalArgumentException(String.format("The sizes of X and Y don't match: %d != %d", x.length, y.length));
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
        int n = x.length;
        p = x[0].length;        
        
        double[][] X = x;
        double[] Y = y;
        if (n > p) {
        center = Math.colMean(x);            
        X = new double[n][p];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < p; j++) {
                X[i][j] = x[i][j] - center[j];
            }
        }
        
        scale = new double[p];
        for (int j = 0; j < p; j++) {
            for (int i = 0; i < n; i++) {
                scale[j] += Math.sqr(X[i][j]);
            }
            scale[j] = Math.sqrt(scale[j] / n);
        }
        
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < p; j++) {
                X[i][j] /= scale[j];
            }
        }
        
        Y = new double[n];
        ym = Math.mean(y);
        for (int i = 0; i < n; i++) {
            Y[i] = y[i] - ym;
        }
        }

        double t = Math.min(Math.max(1.0, 1.0 / lambda), 2 * p / 1e-3);
        double pobj = 0.0; // primal objective function value
        double dobj = Double.NEGATIVE_INFINITY; // dual objective function value
        double s = Double.POSITIVE_INFINITY;

        w = new double[p];
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
        double[] dxu = new double[2*p];
        double[] grad = new double[2*p];

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

        IMatrix pcg = new PCGMatrix(X, d1, d2, prb, prs);
                
        // MAIN LOOP
        int ntiter = 0;
        for (; ntiter <= maxIter; ntiter++) {
            Math.ax(X, w, z);
            for (int i = 0; i < n; i++) {
                z[i] -= Y[i];
                nu[i] = 2 * z[i];
            }

            // CALCULATE DUALITY GAP
            Math.atx(X, nu, xnu);
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
                System.out.format("LASSO: primal and dual objective function value after %3d iterations: %.5g\t%.5g\n", ntiter, pobj, dobj);
            }

            double gap = pobj - dobj;
            // STOPPING CRITERION
            if (gap / dobj < tol) {
                System.out.format("LASSO: primal and dual objective function value after %3d iterations: %.5g\t%.5g\n", ntiter, pobj, dobj);
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
            Math.atx(X, z, gradphi[0]);
            for (int i = 0; i < p; i++) {
                gradphi[0][i] = 2 * gradphi[0][i] - (q1[i] - q2[i]) / t;
                gradphi[1][i] = lambda - (q1[i] + q2[i]) / t;
                grad[i] = -gradphi[0][i];
                grad[i+p] = -gradphi[1][i];
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
            double error = Math.solve(pcg, grad, dxu, pcgtol, 1, pcgmaxi);
            if (error > pcgtol) {
                pitr = pcgmaxi;
            }
            
            for (int i = 0; i < p; i++) {
                dx[i] = dxu[i];
                du[i] = dxu[i+p];
            }

            // BACKTRACKING LINE SEARCH
            double phi = Math.dot(z, z) + lambda * Math.sum(u) - sumlogneg(f)/t;
            s = 1.0;
            double gdx = Math.dot(grad, dxu);

            int lsiter = 0;
            for (; lsiter < MAX_LS_ITER; lsiter++) {
                for (int i = 0; i < p; i++) {
                    neww[i] = w[i] + s * dx[i];
                    newu[i] = u[i] + s * du[i];
                    newf[0][i] =  neww[i] - newu[i];
                    newf[1][i] = -neww[i] - newu[i];
                }

                if (Math.max(newf) < 0.0) {
                    Math.ax(X, neww, newz);
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
                System.err.println("LASSO: Too many iterations of line search.");
                break;
            }

            System.arraycopy(neww, 0, w, 0, p);
            System.arraycopy(newu, 0, u, 0, p);
            System.arraycopy(newf[0], 0, f[0], 0, p);
            System.arraycopy(newf[1], 0, f[1], 0, p);
        }
        
        if (ntiter == maxIter) {
            System.err.println("LASSO: Too many iterations.");
        }
        
        if (n > p) {
        for (int j = 0; j < p; j++) {
            w[j] /= scale[j];
        }
        b = ym - Math.dot(w, center);
        }
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
    
    class PCGMatrix implements IMatrix {

        double[][] A;
        double[] d1;
        double[] d2;
        double[] prb;
        double[] prs;
        double[] ax;
        double[] atax;

        PCGMatrix(double[][] A, double[] d1, double[] d2, double[] prb, double[] prs) {
            this.A = A;
            this.d1 = d1;
            this.d2 = d2;
            this.prb = prb;
            this.prs = prs;
            
            int n = A.length;
            ax = new double[n];
            atax = new double[p];
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
        public void ax(double[] x, double[] y) {
            // COMPUTE AX (PCG)
            // 
            // y = hessphi * x,
            // 
            // where hessphi = [A'*A*2+D1 , D2;
            //                  D2        , D1];
            ax(A, x, ax);
            Math.atx(A, ax, atax);
            for (int i = 0; i < p; i++) {
                y[i]     = 2 * atax[i] + d1[i] * x[i] + d2[i] * x[i + p];
                y[i + p] =              d2[i] * x[i] + d1[i] * x[i + p];
            }
        }
        
        /**
         * Product of a matrix and a vector y = A * x according to the rules
         * of linear algebra. No size check.
         */
        public void ax(double[][] A, double[] x, double[] y) {
            Arrays.fill(y, 0.0);
            for (int i = 0; i < y.length; i++) {
                for (int k = 0; k < A[i].length; k++) {
                    y[i] += A[i][k] * x[k];
                }
            }
        }


        @Override
        public void atx(double[] x, double[] y) {
            ax(x, y);
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
        public double get(int i, int j) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public PCGMatrix set(int i, int j, double x) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public void axpy(double[] x, double[] y) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public void axpy(double[] x, double[] y, double b) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public void atxpy(double[] x, double[] y) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public void atxpy(double[] x, double[] y, double b) {
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
}
