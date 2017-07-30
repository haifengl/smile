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

package smile.manifold;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import smile.math.Math;
import smile.math.matrix.Matrix;
import smile.math.matrix.DenseMatrix;

import java.util.Arrays;

/**
 * t-distributed stochastic neighbor embedding (t-SNE) is a nonlinear
 * dimensionality reduction technique that is particularly well suited
 * for embedding high-dimensional data into a space of two or three
 * dimensions, which can then be visualized in a scatter plot. Specifically,
 * it models each high-dimensional object by a two- or three-dimensional
 * point in such a way that similar objects are modeled by nearby points
 * and dissimilar objects are modeled by distant points.
 * <p>
 * The t-SNE algorithm comprises two main stages. First, t-SNE constructs
 * a probability distribution over pairs of high-dimensional objects in
 * such a way that similar objects have a high probability of being picked,
 * whilst dissimilar points have an infinitesimal probability of being picked.
 * Second, t-SNE defines a similar probability distribution over the points
 * in the low-dimensional map, and it minimizes the Kullback–Leibler
 * divergence between the two distributions with respect to the locations
 * of the points in the map. Note that while the original algorithm uses
 * the Euclidean distance between objects as the base of its similarity
 * metric, this should be changed as appropriate.
 *
 * <h2>References</h2>
 * <ol>
 * <li>L.J.P. van der Maaten. Accelerating t-SNE using Tree-Based Algorithms. Journal of Machine Learning Research 15(Oct):3221-3245, 2014. </li>
 * <li>L.J.P. van der Maaten and G.E. Hinton. Visualizing Non-Metric Similarities in Multiple Maps. Machine Learning 87(1):33-55, 2012. </li>
 * <li>L.J.P. van der Maaten. Learning a Parametric Embedding by Preserving Local Structure. In Proceedings of the Twelfth International Conference on Artificial Intelligence & Statistics (AI-STATS), JMLR W&CP 5:384-391, 2009. </li>
 * <li>L.J.P. van der Maaten and G.E. Hinton. Visualizing High-Dimensional Data Using t-SNE. Journal of Machine Learning Research 9(Nov):2579-2605, 2008. </li>
 * </ol>
 *
 * @author Haifeng Li
 */
public class TSNE {
    private static final Logger logger = LoggerFactory.getLogger(TSNE.class);

    /**
     * Coordinate matrix.
     */
    private double[][] coordinates;

    /** Constructor.
     *
     * @param X input data.
     * @param k the dimension of embedding space.
     */
    public TSNE(double[][] X, int k) {
        this(X, k, 50, 2000);
    }

    /** Constructor.
     *
     * @param X input data.
     * @param k the dimension of embedding space.
     * @param perplexity the perplexity of the conditional distribution.
     * @param maxIter maximum number of iterations.
     */
    public TSNE(double[][] X, int k, double perplexity, int maxIter) {
        this(Matrix.newInstance(Math.pdist(X)), k, perplexity, maxIter);
    }

    /** Constructor.
     *
     * @param D distance/dissimilarity matrix.
     * @param k the dimension of embedding space.
     */
    public TSNE(DenseMatrix D, int k) {
        this(D, k, 50, 2000);
    }

    /** Constructor.
     *
     * @param D distance/dissimilarity matrix.
     * @param k the dimension of embedding space.
     * @param perplexity the perplexity of the conditional distribution.
     * @param maxIter maximum number of iterations.
     */
    public TSNE(DenseMatrix D, int k, double perplexity, int maxIter) {
        int n = D.nrows();
        double momentum        = 0.5;
        double finalMomentum   = 0.8;
        int momentumSwitchIter = 250;
        int eta                = 50; // learning rate
        double minGain         = 0.01;

        double[][] Y          = new double[n][k];
        double[][] dY         = new double[n][k];
        double[][] gains      = new double[n][k];
        double[] dC           = new double[k];

        DenseMatrix P         = expd(D, perplexity, 1e-5);
        DenseMatrix Q         = Matrix.zeros(P.nrows(), P.ncols());

        double Psum = P.sum();
        for (int j = 0; j < n; j++) {
            for (int i = 0; i < j; i++) {
                double p = (P.get(i, j) + P.get(j, i)) / Psum;
                if (Double.isNaN(p) || p < 1E-12) p = 1E-12;
                P.set(i, j, p);
                P.set(j, i, p);
            }
        }

        for (int iter = 0; iter < maxIter; iter++) {
            Math.pdist(Y, Q);
            double Qsum = 0.0;
            for (int j = 0; j < n; j++) {
                for (int i = 0; i < n; i++) {
                    if (i != j) {
                        double q = 1.0 / (1.0 + Q.get(i, j));
                        Q.set(i, j, q);
                        Qsum += q;
                    }
                }
            }

            for (int i = 0; i < n; i++) {
                // Compute gradient
                Arrays.fill(dC, 0.0);
                for (int j = 0; j < n; j++) {
                    if (i != j) {
                        double q = Q.get(i, j);
                        double mult = 4.0 * (P.get(i, j) - (q / Qsum)) * q;
                        for(int d = 0; d < k; d++) {
                            dC[d] += (Y[i][d] - Y[j][d]) * mult;
                        }
                    }
                }

                // Perform the update
                double[] g = gains[i]; // dereference before the loop for better performance
                double[] dy = dY[i];
                double[] y = Y[i];
                for (int d = 0; d < k; d++) {
                    // Update gains
                    g[d] = (Math.signum(dC[d]) != Math.signum(dy[d])) ? (g[d] + .2) : (g[d] * .8);
                    if (g[d] < minGain) g[d] = minGain;

                    // Perform gradient update (with momentum and gains)
                    dy[d] = momentum * dy[d] - eta * g[d] * dC[d];
                    y[d] += dy[d];
                }
            }

            if (iter > momentumSwitchIter) {
                momentum = finalMomentum;
            }

            // Compute current value of cost function
            if (iter % 100 == 0)   {
                double C = 0.0;
                for (int j = 0; j < n; j++) {
                    for (int i = 0; i < j; i++) {
                        if (i != j) {
                            double p = P.get(i, j);
                            double q = Q.get(i, j) / Qsum;
                            C += p * Math.log(p / q);
                        }
                    }
                }
                logger.info("Error after {} iterations: {}", iter, 2 * C);
            }
        }

        coordinates = Y;
    }

    /** Compute the Gaussian kernel (search the width for given perplexity. */
    private DenseMatrix expd(DenseMatrix D, double perplexity, double tol){
        int n              = D.nrows();
        double logU        = Math.log(perplexity);
        DenseMatrix P      = Matrix.zeros(n,n);
        double[] ds        = D.colSums();

        // Column wise is more efficient for DenseMatrix
        for (int j = 0; j < n; j++) {
            // Use sqrt(1 / avg of distance) to initialize beta
            double beta = Math.sqrt((n-1) / ds[j]);
            double betamin = 0.0;
            double betamax = 16 * beta;
            logger.debug("initial beta[{}] = {}", j, beta);

            // Evaluate whether the perplexity is within tolerance
            int iter = 0;
            double Hdiff = 0.0;
            do {
                double dp = 0.0;
                double s = 0.0;
                for (int i = 0; i < n; i++) {
                    if (i != j) {
                        double d = -beta * D.get(i, j);
                        double p = Math.exp(d);
                        P.set(i, j, p);
                        s += p;
                        dp += p * d;
                    }
                }

                double H = Math.log(s) + dp / s;
                Hdiff = H - logU;

                if (Math.abs(Hdiff) > tol) {
                    if (Hdiff > 0) {
                        beta = (beta + betamax) / 2;
                    } else {
                        beta = (beta + betamin) / 2;
                    }
                }

                logger.debug("Hdiff = {}, beta[{}] = {}", Hdiff, j, beta);
            } while (Math.abs(Hdiff) > tol && ++iter < 50);
        }

        return P;
    }

    /**
     * Returns the coordinates of projected data.
     */
    public double[][] getCoordinates() {
        return coordinates;
    }
}
