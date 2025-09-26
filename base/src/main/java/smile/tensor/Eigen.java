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
package smile.tensor;

import smile.math.MathEx;

public interface Eigen {
    org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(Eigen.class);

    /**
     * Returns the largest eigen pair of matrix with the power iteration
     * under the assumptions A has an eigenvalue that is strictly greater
     * in magnitude than its other eigenvalues and the starting
     * vector has a nonzero component in the direction of an eigenvector
     * associated with the dominant eigenvalue.
     * @param v on input, it is the non-zero initial guess of the eigen vector.
     * On output, it is the eigen vector corresponding largest eigen value.
     * @return the largest eigen value.
     */
    static double power(Matrix A, Vector v) {
        return power(A, v, 0.0, Math.max(1.0E-6, A.nrow() * MathEx.EPSILON), Math.max(20, 2 * A.nrow()));
    }

    /**
     * Returns the largest eigen pair of matrix with the power iteration
     * under the assumptions A has an eigenvalue that is strictly greater
     * in magnitude than its other eigenvalues and the starting
     * vector has a nonzero component in the direction of an eigenvector
     * associated with the dominant eigenvalue.
     * @param v on input, it is the non-zero initial guess of the eigen vector.
     * On output, it is the eigen vector corresponding largest eigen value.
     * @param p the origin in the shifting power method. A - pI will be
     * used in the iteration to accelerate the method. p should be such that
     * |(&lambda;<sub>2</sub> - p) / (&lambda;<sub>1</sub> - p)| &lt; |&lambda;<sub>2</sub> / &lambda;<sub>1</sub>|,
     * where &lambda;<sub>2</sub> is the second-largest eigenvalue in magnitude.
     * If we know the eigenvalue spectrum of A, (&lambda;<sub>2</sub> + &lambda;<sub>n</sub>)/2
     * is the optimal choice of p, where &lambda;<sub>n</sub> is the smallest eigenvalue
     * in magnitude. Good estimates of &lambda;<sub>2</sub> are more difficult
     * to compute. However, if &mu; is an approximation to the largest eigenvector,
     * then using any x<sub>0</sub> such that x<sub>0</sub>*&mu; = 0 as the initial
     * vector for a few iterations may yield a reasonable estimate of &lambda;<sub>2</sub>.
     * @param tol the desired convergence tolerance.
     * @param maxIter the maximum number of iterations in case that the algorithm
     * does not converge.
     * @return the largest eigen value.
     */
    static double power(Matrix A, Vector v, double p, double tol, int maxIter) {
        if (A.nrow() != A.ncol()) {
            throw new IllegalArgumentException("Matrix is not square.");
        }

        if (tol <= 0.0) {
            throw new IllegalArgumentException("Invalid tolerance: " + tol);
        }

        if (maxIter <= 0) {
            throw new IllegalArgumentException("Invalid maximum number of iterations: " + maxIter);
        }

        int n = A.nrow();
        tol = Math.max(tol, MathEx.EPSILON * n);

        var z = A.vector(n);
        double lambda = power(A, v, z, p);

        for (int iter = 1; iter <= maxIter; iter++) {
            double l = lambda;
            lambda = power(A, v, z, p);

            double eps = Math.abs(lambda - l);
            if (iter % 10 == 0) {
                logger.trace("Largest eigenvalue after {} power iterations: {}", iter, lambda + p);
            }

            if (eps < tol) {
                logger.info("Largest eigenvalue after {} power iterations: {}", iter, lambda + p);
                return lambda + p;
            }
        }

        logger.info("Largest eigenvalue after {} power iterations: {}", maxIter, lambda + p);
        logger.error("Power iteration exceeded the maximum number of iterations.");
        return lambda + p;
    }

    /**
     * Computes y = (A - pI) x and then normalize x = y / norm1(y).
     * Returns the largest element of y in magnitude.
     */
    private static double power(Matrix A, Vector x, Vector y, double p) {
        A.mv(x, y);
        if (p != 0.0) {
            x.axpy(-p, y);
        }

        double lambda = y.norm1();
        y.scale(1.0 / lambda);
        x.swap(y);
        return lambda;
    }
}
