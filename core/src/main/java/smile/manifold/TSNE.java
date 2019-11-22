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

package smile.manifold;

import java.util.Arrays;
import java.util.stream.IntStream;
import smile.math.MathEx;
import smile.stat.distribution.GaussianDistribution;

/**
 * The t-distributed stochastic neighbor embedding (t-SNE) is a nonlinear
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
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(TSNE.class);

    /**
     * Coordinate matrix.
     */
    public final double[][] coordinates;

    private double eta             = 500;
    private double momentum        = 0.5;
    private double finalMomentum   = 0.8;
    private int momentumSwitchIter = 250;
    private double minGain         = .01;
    private int    totalIter       =   1; // total iterations

    private double[][] D;

    private double[][] dY;
    private double[][] gains; // adjust learning rate for each point

    private double[][] P;
    private double[][] Q;
    private double     Qsum;

    /** Constructor. Train t-SNE for 1000 iterations, perplexity = 20 and learning rate = 200.
     *
     * @param X input data. If X is a square matrix, it is assumed to be the squared distance/dissimilarity matrix.
     * @param d the dimension of embedding space.
     */
    public TSNE(double[][] X, int d) {
        this(X, d, 20, 200, 1000);
    }

    /** Constructor. Train t-SNE for given number of iterations.
     *
     * @param X input data. If X is a square matrix, it is assumed to be the squared distance/dissimilarity matrix.
     * @param d the dimension of embedding space.
     * @param perplexity the perplexity of the conditional distribution.
     * @param eta the learning rate.
     * @param iterations the number of iterations.
     */
    public TSNE(double[][] X, int d, double perplexity, double eta, int iterations) {
        this.eta = eta;
        int n = X.length;

        if (X.length == X[0].length) {
            D = X;
        } else {
            D = new double[n][n];
            MathEx.pdist(X, D, true, false);
        }

        coordinates = new double[n][d];
        double[][] Y = coordinates;
        dY = new double[n][d];
        gains = new double[n][d]; // adjust learning rate for each point

        // Initialize Y randomly by N(0, 0.0001)
        GaussianDistribution gaussian = new GaussianDistribution(0.0, 0.0001);
        for (int i = 0; i < n; i++) {
            Arrays.fill(gains[i], 1.0);
            double[] Yi = Y[i];
            for (int j = 0; j < d; j++) {
                Yi[j] = gaussian.rand();
            }
        }

        // Large tolerance to speed up the search of Gaussian kernel width
        // A small difference of kernel width is not important.
        P = expd(D, perplexity, 1E-3);
        Q = new double[n][n];

        // Make P symmetric
        // sum(P) = 2 * n as each row of P is normalized
        double Psum = 2 * n;
        for (int i = 0; i < n; i++) {
            double[] Pi = P[i];
            for (int j = 0; j < i; j++) {
                double p = 12.0 * (Pi[j] + P[j][i]) / Psum;
                if (Double.isNaN(p) || p < 1E-16) p = 1E-16;
                Pi[j] = p;
                P[j][i] = p;
            }
        }

        update(iterations);
    }

    /** Performs additional iterations. */
    public void update(int iterations) {
        double[][] Y = coordinates;
        int n = Y.length;
        int d = Y[0].length;

        for (int iter = 1; iter <= iterations; iter++, totalIter++) {
            MathEx.pdist(Y, Q, true, false);
            Qsum = 0.0;
            for (int i = 0; i < n; i++) {
                double[] Qi = Q[i];
                for (int j = 0; j < i; j++) {
                    double q = 1.0 / (1.0 + Qi[j]);
                    Qi[j] = q;
                    Q[j][i] = q;
                    Qsum += q;
                }
            }
            Qsum *= 2.0;

            IntStream.range(0, n).parallel().forEach(i -> sne(i));

            if (totalIter == momentumSwitchIter) {
                momentum = finalMomentum;
                for (int i = 0; i < n; i++) {
                    double[] Pi = P[i];
                    for (int j = 0; j < n; j++) {
                        Pi[j] /= 12.0;
                    }
                }
            }

            // Compute current value of cost function
            if (iter % 50 == 0)   {
                double C = 0.0;
                for (int i = 0; i < n; i++) {
                    double[] Pi = P[i];
                    double[] Qi = Q[i];
                    for (int j = 0; j < i; j++) {
                        double p = Pi[j];
                        double q = Qi[j] / Qsum;
                        if (Double.isNaN(q) || q < 1E-16) q = 1E-16;
                        C += p * MathEx.log2(p / q);
                    }
                }
                logger.info("Error after {} iterations: {}", totalIter, 2 * C);
            }
        }

        // Make solution zero-mean
        double[] colMeans = MathEx.colMeans(Y);
        for (int i = 0; i < n; i++) {
            double[] Yi = Y[i];
            for (int j = 0; j < d; j++) {
                Yi[j] -= colMeans[j];
            }
        }
    }

    /** Computes the gradients and updates the coordinates. */
    private void sne(int i) {
        double[] dC = new double[coordinates[0].length];
        double[][] Y = coordinates;
        int n = Y.length;
        int d = Y[0].length;

        // Compute gradient
        // dereference before the loop for better performance
        double[] Yi = Y[i];
        double[] Pi = P[i];
        double[] Qi = Q[i];
        double[] dYi = dY[i];
        double[] g = gains[i];
        for (int j = 0; j < n; j++) {
            if (i != j) {
                double[] Yj = Y[j];
                double q = Qi[j];
                double z = (Pi[j] - (q / Qsum)) * q;
                for (int k = 0; k < d; k++) {
                    dC[k] += 4.0 * (Yi[k] - Yj[k]) * z;
                }
            }
        }

        // Perform the update
        for (int k = 0; k < d; k++) {
            // Update gains
            g[k] = (Math.signum(dC[k]) != Math.signum(dYi[k])) ? (g[k] + .2) : (g[k] * .8);
            if (g[k] < minGain) g[k] = minGain;

            // gradient update with momentum and gains
            Yi[k] += dYi[k];
            dYi[k] = momentum * dYi[k] - eta * g[k] * dC[k];
        }
    }

    /** Compute the Gaussian kernel (search the width for given perplexity. */
    private double[][] expd(double[][] D, double perplexity, double tol) {
        int n          = D.length;
        double[][] P   = new double[n][n];
        double[] DiSum = MathEx.rowSums(D);

        IntStream.range(0, n).parallel().forEach(i -> {
            double logU = MathEx.log2(perplexity);

            double[] Pi = P[i];
            double[] Di = D[i];

            // Use sqrt(1 / avg of distance) to initialize beta
            double beta = Math.sqrt((n-1) / DiSum[i]);
            double betamin = 0.0;
            double betamax = Double.POSITIVE_INFINITY;
            logger.debug("initial beta[{}] = {}", i, beta);

            // Evaluate whether the perplexity is within tolerance
            double Hdiff = Double.MAX_VALUE;
            for (int iter = 0; Math.abs(Hdiff) > tol && iter < 50; iter++) {
                double Pisum = 0.0;
                double H = 0.0;
                for (int j = 0; j < n; j++) {
                    double d = beta * Di[j];
                    double p = Math.exp(-d);
                    Pi[j] = p;
                    Pisum += p;
                    H += p * d;
                }

                // P[i][i] should be 0
                Pi[i] = 0.0;
                Pisum -= 1.0;

                H = MathEx.log2(Pisum) + H / Pisum;
                Hdiff = H - logU;

                if (Math.abs(Hdiff) > tol) {
                    if (Hdiff > 0) {
                        betamin = beta;
                        if (Double.isInfinite(betamax))
                            beta *= 2.0;
                        else
                            beta = (beta + betamax) / 2;
                    } else {
                        betamax = beta;
                        beta = (beta + betamin) / 2;
                    }
                } else {
                    // normalize by row
                    for (int j = 0; j < n; j++) {
                        Pi[j] /= Pisum;
                    }
                }

                logger.debug("Hdiff = {}, beta[{}] = {}, H = {}, logU = {}", Hdiff, i, beta, H, logU);
            }
        });

        return P;
    }
}
