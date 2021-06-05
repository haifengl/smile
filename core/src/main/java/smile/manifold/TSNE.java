/*
 * Copyright (c) 2010-2020 Haifeng Li. All rights reserved.
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
 */

package smile.manifold;

import java.io.Serializable;
import java.util.Arrays;
import java.util.stream.IntStream;
import smile.math.MathEx;
import smile.stat.distribution.GaussianDistribution;

/**
 * The t-distributed stochastic neighbor embedding. The t-SNE is a nonlinear
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
 * <li>L.J.P. van der Maaten. Accelerating t-SNE using Tree-Based Algorithms.
 *     Journal of Machine Learning Research 15(Oct):3221-3245, 2014. </li>
 * <li>L.J.P. van der Maaten and G.E. Hinton. Visualizing Non-Metric
 *     Similarities in Multiple Maps. Machine Learning 87(1):33-55, 2012. </li>
 * <li>L.J.P. van der Maaten. Learning a Parametric Embedding by Preserving
 *     Local Structure. In Proceedings of the Twelfth International Conference
 *     on Artificial Intelligence &amp; Statistics (AI-STATS),
 *     JMLR W&amp;CP 5:384-391, 2009. </li>
 * <li>L.J.P. van der Maaten and G.E. Hinton. Visualizing High-Dimensional
 *     Data Using t-SNE. Journal of Machine Learning Research
 *     9(Nov):2579-2605, 2008. </li>
 * </ol>
 *
 * @see UMAP
 *
 * @author Haifeng Li
 */
public class TSNE implements Serializable {
    private static final long serialVersionUID = 2L;
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(TSNE.class);

    /**
     * The coordinate matrix in embedding space.
     */
    public final double[][] coordinates;

    /**
     * The learning rate.
     */
    private final double eta;
    /**
     * The number of iterations so far.
     */
    private int totalIter = 0;
    /**
     * The momentum factor.
     */
    private double momentum              = 0.5;
    /**
     * The momentum in later stage.
     */
    private final double finalMomentum   = 0.8;
    /**
     * The number of iterations at which switch the momentum to
     * finalMomentum.
     */
    private final int momentumSwitchIter = 250;
    /**
     * The floor of gain.
     */
    private final double minGain         = .01;

    /** The gain matrix. */
    private final double[][] gains; // adjust learning rate for each point
    /** The probability matrix of the distances in the input space. */
    private final double[][] P;
    /** The probability matrix of the distances in the feature space. */
    private final double[][] Q;
    /** The sum of Q matrix. */
    private double Qsum;
    /** The cost function value. */
    private double cost;

    /** Constructor. Train t-SNE for 1000 iterations, perplexity = 20 and learning rate = 200.
     *
     * @param X the input data. If X is a square matrix, it is assumed to be
     *          the squared distance/dissimilarity matrix.
     * @param d the dimension of embedding space.
     */
    public TSNE(double[][] X, int d) {
        this(X, d, 20, 200, 1000);
    }

    /** Constructor. Train t-SNE for given number of iterations.
     *
     * @param X the input data. If X is a square matrix, it is assumed to be
     *         the squared distance/dissimilarity matrix.
     * @param d the dimension of embedding space.
     * @param perplexity the perplexity of the conditional distribution.
     * @param eta the learning rate.
     * @param iterations the number of iterations.
     */
    public TSNE(double[][] X, int d, double perplexity, double eta, int iterations) {
        this.eta = eta;
        int n = X.length;

        double[][] D;
        if (X.length == X[0].length) {
            D = X;
        } else {
            D = new double[n][n];
            MathEx.pdist(X, D, MathEx::squaredDistance);
        }

        coordinates = new double[n][d];
        double[][] Y = coordinates;
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

    /**
     * Returns the cost function value.
     * @return the cost function value.
     */
    public double cost() {
        return cost;
    }

    /**
     * Performs additional iterations.
     * @param iterations the number of iterations.
     */
    public void update(int iterations) {
        double[][] Y = coordinates;
        int n = Y.length;
        int d = Y[0].length;
        double[][] dY = new double[n][d];
        double[][] dC = new double[n][d];

        for (int iter = 1; iter <= iterations; iter++, totalIter++) {
            Qsum = computeQ(Y, Q);

            IntStream.range(0, n).parallel().forEach(i -> sne(i, dY[i], dC[i]));

            // gradient update with momentum and gains
            IntStream.range(0, n).parallel().forEach(i -> {
                double[] Yi = Y[i];
                double[] dYi = dY[i];
                double[] dCi = dC[i];
                double[] g = gains[i];
                for (int k = 0; k < d; k++) {
                    dYi[k] = momentum * dYi[k] - eta * g[k] * dCi[k];
                    Yi[k] += dYi[k];
                }
            });

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
            if (iter % 100 == 0)   {
                cost = computeCost(P, Q);
                logger.info("Error after {} iterations: {}", iter, cost);
            }
        }

        // Make solution zero-mean
        double[] colMeans = MathEx.colMeans(Y);
        IntStream.range(0, n).parallel().forEach(i -> {
            double[] Yi = Y[i];
            for (int j = 0; j < d; j++) {
                Yi[j] -= colMeans[j];
            }
        });

        if (iterations % 100 != 0)   {
            cost = computeCost(P, Q);
            logger.info("Error after {} iterations: {}", iterations, cost);
        }
    }

    /** Computes the gradients and updates the coordinates. */
    private void sne(int i, double[] dY, double[] dC) {
        double[][] Y = coordinates;
        int n = Y.length;
        int d = Y[0].length;

        // Compute gradient
        // dereference before the loop for better performance
        double[] Yi = Y[i];
        double[] Pi = P[i];
        double[] Qi = Q[i];
        double[] g = gains[i];

        Arrays.fill(dC, 0.0);
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

        // Update gains
        for (int k = 0; k < d; k++) {
            g[k] = (Math.signum(dC[k]) != Math.signum(dY[k])) ? (g[k] + .2) : (g[k] * .8);
            if (g[k] < minGain) g[k] = minGain;
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

    /**
     * Computes the Q matrix.
     */
    private double computeQ(double[][] Y, double[][] Q) {
        int n = Y.length;

        // DoubleStream.sum is unreproducible across machines
        // due to scheduling randomness. Therefore, we accumulate the
        // row sum and then compute the overall sum.
        double[] rowSum = IntStream.range(0, n).parallel().mapToDouble(i -> {
            double[] Yi = Y[i];
            double[] Qi = Q[i];
            double sum = 0.0;
            for (int j = 0; j < n; j++) {
                double q = 1.0 / (1.0 + MathEx.squaredDistance(Yi, Y[j]));
                Qi[j] = q;
                sum += q;
            }
            return sum;
        }).toArray();

        return MathEx.sum(rowSum);
    }

    /**
     * Computes the cost function.
     */
    private double computeCost(double[][] P, double[][] Q) {
        return 2 * IntStream.range(0, Q.length).parallel().mapToDouble(i -> {
            double[] Pi = P[i];
            double[] Qi = Q[i];
            double C = 0.0;
            for (int j = 0; j < i; j++) {
                double p = Pi[j];
                double q = Qi[j] / Qsum;
                if (Double.isNaN(q) || q < 1E-16) q = 1E-16;
                C += p * MathEx.log2(p / q);
            }
            return C;
        }).sum();
    }
}
