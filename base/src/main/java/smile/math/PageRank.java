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
package smile.math;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import smile.tensor.Matrix;
import smile.tensor.Vector;

/**
 * PageRank is a link analysis algorithm, and it assigns a numerical weighting
 * to each element of a hyperlinked set of documents, such as the World Wide
 * Web, with the purpose of "measuring" its relative importance within the
 * set. The algorithm may be applied to any collection of entities with
 * reciprocal quotations and references.
 * <p>
 * PageRank can be computed either iteratively or algebraically.
 * The iterative method can be viewed as the power iteration method.
 *
 * @author Haifeng Li
 */
public interface PageRank {
    private static Logger logger() {
        final class LogHolder {
            private static final Logger logger = LoggerFactory.getLogger(PageRank.class);
        }
        return LogHolder.logger;
    }

    /**
     * Calculates the page rank vector.
     * @param A the matrix supporting matrix vector multiplication operation.
     * @return the page rank vector.
     */
    static Vector of(Matrix A) {
        int n = A.nrow();
        Vector v = A.vector(n);
        v.fill(1.0 / n);
        return of(A, v);
    }

    /**
     * Calculates the page rank vector.
     * @param A the matrix supporting matrix vector multiplication operation.
     * @param v the teleportation vector.
     * @return the page rank vector.
     */
    static Vector of(Matrix A, Vector v) {
        return of(A, v, 0.85, 1E-7, 57);
    }

    /**
     * Calculates the page rank vector.
     * @param A the matrix supporting matrix vector multiplication operation.
     * @param v the teleportation vector.
     * @param damping the damper factor.
     * @param tol the desired convergence tolerance.
     * @param maxIter the maximum number of iterations in case that the
     *                algorithm does not converge.
     * @return the page rank vector.
     */
    static Vector of(Matrix A, Vector v, double damping, double tol, int maxIter) {
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

        Vector z = A.vector(n);
        Vector p = v.copy(0, n);

        for (int iter = 1; iter <= maxIter; iter++) {
            A.mv(p, z);
            double beta = 1.0 - damping * z.norm1();

            double delta = 0.0;
            for (int i = 0; i < n; i++) {
                double q = damping * z.get(i) + beta * v.get(i);
                delta += Math.abs(q - p.get(i));
                p.set(i, q);
            }

            if (iter % 10 == 0 || delta < tol) {
                logger().info("PageRank residual after {} power iterations: {}", iter, delta);
            }

            if (delta < tol) return p;
        }

        logger().error("PageRank iteration exceeded the maximum number of iterations.");
        return p;
    }
}
