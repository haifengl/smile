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

import java.util.Arrays;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import smile.math.Math;

/**
 * PageRank is a link analysis algorithm and it assigns a numerical weighting
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
public class PageRank {
    private static final Logger logger = LoggerFactory.getLogger(PageRank.class);
    /**
     * Calculate the page rank vector.
     * @param A the matrix supporting matrix vector multiplication operation.
     * @return the page rank vector.
     */
    public static double[] pagerank(Matrix A) {
        int n = A.nrows();
        double[] v = new double[n];
        Arrays.fill(v, 1.0 / n);
        return pagerank(A, v);
    }

    /**
     * Calculate the page rank vector.
     * @param A the matrix supporting matrix vector multiplication operation.
     * @param v the teleportation vector.
     * @return the page rank vector.
     */
    public static double[] pagerank(Matrix A, double[] v) {
        return pagerank(A, v, 0.85, 1E-7, 57);
    }

    /**
     * Calculate the page rank vector.
     * @param A the matrix supporting matrix vector multiplication operation.
     * @param v the teleportation vector.
     * @param damping the damper factor.
     * @param tol the desired convergence tolerance.
     * @param maxIter the maximum number of iterations in case that the algorithm
     * does not converge.
     * @return the page rank vector.
     */
    public static double[] pagerank(Matrix A, double[] v, double damping, double tol, int maxIter) {
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
        tol = smile.math.Math.max(tol, Math.EPSILON * n);

        double[] z = new double[n];
        double[] p = Arrays.copyOf(v, n);

        for (int iter = 1; iter <= maxIter; iter++) {
            A.ax(p, z);
            double beta = 1.0 - damping * Math.norm1(z);

            double delta = 0.0;
            for (int i = 0; i < n; i++) {
                double q = damping * z[i] + beta * v[i];
                delta += Math.abs(q - p[i]);
                p[i] = q;
            }

            if (iter % 10 == 0) {
                logger.info(String.format("PageRank residual after %3d power iterations: %.7f\n", iter, delta));
            }

            if (delta < tol) {
                logger.info(String.format("PageRank residual after %3d power iterations: %.7f\n", iter, delta));
                return p;
            }
        }

        logger.error("PageRank iteration exceeded the maximum number of iterations.");
        return p;
    }
}
