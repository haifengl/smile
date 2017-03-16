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
package smile.math.matrix;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import smile.math.Math;

/**
 * The power iteration (also known as power method) is an eigenvalue algorithm
 * that will produce the greatest (in absolute value) eigenvalue and a nonzero
 * vector the corresponding eigenvector.
 *
 * @author Haifeng Li
 */
public class PowerIteration {
    private static final Logger logger = LoggerFactory.getLogger(PowerIteration.class);

    /**
     * Returns the largest eigen pair of matrix with the power iteration
     * under the assumptions A has an eigenvalue that is strictly greater
     * in magnitude than its other eigenvalues and the starting
     * vector has a nonzero component in the direction of an eigenvector
     * associated with the dominant eigenvalue.
     * @param A the matrix supporting matrix vector multiplication operation.
     * @param v on input, it is the non-zero initial guess of the eigen vector.
     * On output, it is the eigen vector corresponding largest eigen value.
     * @return the largest eigen value.
     */
    public static double eigen(Matrix A, double[] v) {
        return eigen(A, v, smile.math.Math.max(1.0E-10, A.nrows() * Math.EPSILON));
    }

    /**
     * Returns the largest eigen pair of matrix with the power iteration
     * under the assumptions A has an eigenvalue that is strictly greater
     * in magnitude than its other eigenvalues and the starting
     * vector has a nonzero component in the direction of an eigenvector
     * associated with the dominant eigenvalue.
     * @param A the matrix supporting matrix vector multiplication operation.
     * @param v on input, it is the non-zero initial guess of the eigen vector.
     * On output, it is the eigen vector corresponding largest eigen value.
     * @param tol the desired convergence tolerance.
     * @return the largest eigen value.
     */
    public static double eigen(Matrix A, double[] v, double tol) {
        return eigen(A, v, 0.0, tol);
    }

    /**
     * Returns the largest eigen pair of matrix with the power iteration
     * under the assumptions A has an eigenvalue that is strictly greater
     * in magnitude than its other eigenvalues and the starting
     * vector has a nonzero component in the direction of an eigenvector
     * associated with the dominant eigenvalue.
     * @param A the matrix supporting matrix vector multiplication operation.
     * @param v on input, it is the non-zero initial guess of the eigen vector.
     * On output, it is the eigen vector corresponding largest eigen value.
     * @param tol the desired convergence tolerance.
     * @param maxIter the maximum number of iterations in case that the algorithm
     * does not converge.
     * @return the largest eigen value.
     */
    public static double eigen(Matrix A, double[] v, double tol, int maxIter) {
        return eigen(A, v, 0.0, tol, maxIter);
    }

    /**
     * Returns the largest eigen pair of matrix with the power iteration
     * under the assumptions A has an eigenvalue that is strictly greater
     * in magnitude than its other eigenvalues and the starting
     * vector has a nonzero component in the direction of an eigenvector
     * associated with the dominant eigenvalue.
     * @param A the matrix supporting matrix vector multiplication operation.
     * @param v on input, it is the non-zero initial guess of the eigen vector.
     * On output, it is the eigen vector corresponding largest eigen value.
     * @param p the origin in the shifting power method. A - pI will be
     * used in the iteration to accelerate the method. p should be such that
     * |(&lambda;<sub>2</sub> - p) / (&lambda;<sub>1</sub> - p)| &lt; |&lambda;<sub>2</sub> / &lambda;<sub>1</sub>|,
     * where &lambda;<sub>2</sub> is the second largest eigenvalue in magnitude.
     * If we known the eigenvalue spectrum of A, (&lambda;<sub>2</sub> + &lambda;<sub>n</sub>)/2
     * is the optimal choice of p, where &lambda;<sub>n</sub> is the smallest eigenvalue
     * in magnitude. Good estimates of &lambda;<sub>2</sub> are more difficult
     * to compute. However, if &mu; is an approximation to largest eigenvector,
     * then using any x<sub>0</sub> such that x<sub>0</sub>*&mu; = 0 as the initial
     * vector for a few iterations may yield a reasonable estimate of &lambda;<sub>2</sub>.
     * @param tol the desired convergence tolerance.
     * @return the largest eigen value.
     */
    public static double eigen(Matrix A, double[] v, double p, double tol) {
        return eigen(A, v, p, tol, Math.max(20, 2 * A.nrows()));
    }

    /**
     * Returns the largest eigen pair of matrix with the power iteration
     * under the assumptions A has an eigenvalue that is strictly greater
     * in magnitude than its other eigenvalues and the starting
     * vector has a nonzero component in the direction of an eigenvector
     * associated with the dominant eigenvalue.
     * @param A the matrix supporting matrix vector multiplication operation.
     * @param v on input, it is the non-zero initial guess of the eigen vector.
     * On output, it is the eigen vector corresponding largest eigen value.
     * @param p the origin in the shifting power method. A - pI will be
     * used in the iteration to accelerate the method. p should be such that
     * |(&lambda;<sub>2</sub> - p) / (&lambda;<sub>1</sub> - p)| &lt; |&lambda;<sub>2</sub> / &lambda;<sub>1</sub>|,
     * where &lambda;<sub>2</sub> is the second largest eigenvalue in magnitude.
     * If we known the eigenvalue spectrum of A, (&lambda;<sub>2</sub> + &lambda;<sub>n</sub>)/2
     * is the optimal choice of p, where &lambda;<sub>n</sub> is the smallest eigenvalue
     * in magnitude. Good estimates of &lambda;<sub>2</sub> are more difficult
     * to compute. However, if &mu; is an approximation to largest eigenvector,
     * then using any x<sub>0</sub> such that x<sub>0</sub>*&mu; = 0 as the initial
     * vector for a few iterations may yield a reasonable estimate of &lambda;<sub>2</sub>.
     * @param tol the desired convergence tolerance.
     * @param maxIter the maximum number of iterations in case that the algorithm
     * does not converge.
     * @return the largest eigen value.
     */
    public static double eigen(Matrix A, double[] v, double p, double tol, int maxIter) {
        if (A.nrows() != A.ncols()) {
            throw new IllegalArgumentException("Matrix is not square.");
        }

        if (tol <= 0.0) {
            throw new IllegalArgumentException("Invalid tolerance: " + tol);
        }

        if (maxIter <= 0) {
            throw new IllegalArgumentException("Invalid maximum number of iterations: " + maxIter);
        }

        int n = A.nrows();
        tol = Math.max(tol, Math.EPSILON * n);

        double[] z = new double[n];
        double lambda = ax(A, v, z, p);

        for (int iter = 1; iter <= maxIter; iter++) {
            double l = lambda;
            lambda = ax(A, v, z, p);

            double eps = Math.abs(lambda - l);
            if (iter % 10 == 0) {
                logger.trace(String.format("Largest eigenvalue after %3d power iterations: %.5f\n", iter, lambda + p));
            }

            if (eps < tol) {
                logger.info(String.format("Largest eigenvalue after %3d power iterations: %.5f\n", iter, lambda + p));
                return lambda + p;
            }
        }

        logger.info(String.format("Largest eigenvalue after %3d power iterations: %.5f\n", maxIter, lambda + p));
        logger.error("Power iteration exceeded the maximum number of iterations.");
        return lambda + p;
    }

    /**
     * Calculate and normalize y = (A - pI) x.
     * Returns the largest element of y in magnitude.
     */
    private static double ax(Matrix A, double[] x, double[] y, double p) {
        A.ax(x, y);

        if (p != 0.0) {
            for (int i = 0; i < y.length; i++) {
                y[i] -= p * x[i];
            }
        }

        double lambda = y[0];
        for (int i = 1; i < y.length; i++) {
            if (Math.abs(y[i]) > Math.abs(lambda)) {
                lambda = y[i];
            }
        }

        for (int i = 0; i < y.length; i++) {
            x[i] = y[i] / lambda;
        }

        return lambda;
    }
}
