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

import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import smile.math.Math;
import smile.stat.distribution.GaussianDistribution;
import smile.util.MulticoreExecutor;

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
    private static final Logger logger = LoggerFactory.getLogger(TSNE.class);

    /**
     * Coordinate matrix.
     */
    private double[][] coordinates;

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
            Math.pdist(X, D, true, false);
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

        learn(iterations);
    }

    /** Continue to learn additional iterations. */
    public void learn(int iterations) {
        double[][] Y = coordinates;
        int n = Y.length;
        int d = Y[0].length;

        int nprocs = MulticoreExecutor.getThreadPoolSize();
        int chunk = n / nprocs;
        List<SNETask> tasks = new ArrayList<>();
        for (int i = 0; i < nprocs; i++) {
            int start = i * chunk;
            int end = i == nprocs-1 ? n : (i+1) * chunk;
            SNETask task = new SNETask(start, end);
            tasks.add(task);
        }

        for (int iter = 1; iter <= iterations; iter++, totalIter++) {
            Math.pdist(Y, Q, true, false);
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

            try {
                MulticoreExecutor.run(tasks);
            } catch (Exception e) {
                logger.error("t-SNE iteration task fails: {}", e);
            }

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
                        C += p * Math.log2(p / q);
                    }
                }
                logger.info("Error after {} iterations: {}", totalIter, 2 * C);
            }
        }

        // Make solution zero-mean
        double[] colMeans = Math.colMeans(Y);
        for (int i = 0; i < n; i++) {
            double[] Yi = Y[i];
            for (int j = 0; j < d; j++) {
                Yi[j] -= colMeans[j];
            }
        }
    }

    private class SNETask implements Callable<Void> {
        int start, end;
        double[] dC;


        SNETask(int start, int end) {
            this.start = start;
            this.end = end;
            this.dC = new double[coordinates[0].length];
        }

        @Override
        public Void call() {
            for (int i = start; i < end; i++) {
                compute(i);
            }

            return null;
        }

        private void compute(int i) {
            double[][] Y = coordinates;
            int n = Y.length;
            int d = Y[0].length;
            Arrays.fill(dC, 0.0);

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
    }

    /** Compute the Gaussian kernel (search the width for given perplexity. */
    private double[][] expd(double[][] D, double perplexity, double tol) {
        int n          = D.length;
        double[][] P   = new double[n][n];
        double[] DiSum = Math.rowSums(D);

        int nprocs = MulticoreExecutor.getThreadPoolSize();
        int chunk = n / nprocs;
        List<PerplexityTask> tasks = new ArrayList<>();
        for (int i = 0; i < nprocs; i++) {
            int start = i * chunk;
            int end = i == nprocs-1 ? n : (i+1) * chunk;
            PerplexityTask task = new PerplexityTask(start, end, D, P, DiSum, perplexity, tol);
            tasks.add(task);
        }

        try {
            MulticoreExecutor.run(tasks);
        } catch (Exception e) {
            logger.error("t-SNE Gaussian kernel width search task fails: {}", e);
        }

        return P;
    }

    private class PerplexityTask implements Callable<Void> {
        int start, end;
        double[][] D;
        double[][] P;
        double[] DiSum;
        double perplexity;
        double tol;

        PerplexityTask(int start, int end, double[][] D, double[][] P, double[] DiSum, double perplexity, double tol) {
            this.start = start;
            this.end = end;
            this.D = D;
            this.P = P;
            this.DiSum = DiSum;
            this.perplexity = perplexity;
            this.tol = tol;
        }

        @Override
        public Void call() {
            for (int i = start; i < end; i++) {
                compute(i);
            }
            return null;
        }

        private void compute(int i) {
            int n       = D.length;
            double logU = Math.log2(perplexity);

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

                H = Math.log2(Pisum) + H / Pisum;
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
        }
    }

    /**
     * Returns the coordinates of projected data.
     */
    public double[][] getCoordinates() {
        return coordinates;
    }
}
