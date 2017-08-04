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
import smile.util.MulticoreExecutor;

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
     * @param X input data. If X is a square matrix, it is assumed to be the distance/dissimilarity matrix.
     * @param d the dimension of embedding space.
     */
    public TSNE(double[][] X, int d) {
        this(X, d, 50, 20.0, 2000);
    }

    /** Constructor.
     *
     * @param X input data. If X is a square matrix, it is assumed to be the distance/dissimilarity matrix.
     * @param d the dimension of embedding space.
     * @param perplexity the perplexity of the conditional distribution.
     * @param eta the learning rate.
     * @param iterations the number of iterations.
     */
    public TSNE(double[][] X, int d, double perplexity, double eta, int iterations) {
        int n = X.length;
        double momentum        = 0.5;
        double finalMomentum   = 0.8;
        int momentumSwitchIter = 250;
        double minGain         = .01;

        double[][] D;
        if (X.length == X[0].length) {
            D = X;
        } else {
            D = Math.pdist(X);
        }

        double[][] Y           = new double[n][d];
        double[][] dY          = new double[n][d];
        double[][] gains       = new double[n][d]; // adjust learning rate for each point

        // Initialize Y randomly
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < d; j++) {
                Y[i][j] = Math.random();
            }
        }

        // Large tolerance to speed up the search of Gaussian kernel width
        // A small difference of kernel width is not important.
        double[][] P = expd(D, perplexity, 1E-3);
        double[][] Q = new double[n][n];

        // Make P symmetric
        double Psum = 0.0;
        for (int i = 0; i < n; i++) {
            double[] Pi = P[i];
            for (int j = 0; j < n; j++) {
                Psum += Pi[j];
            }
        }

        for (int i = 0; i < n; i++) {
            for (int j = 0; j < i; j++) {
                double p = (P[i][j] + P[j][i]) / Psum;
                if (Double.isNaN(p) || p < 1E-12) p = 1E-12;
                P[i][j] = p;
                P[j][i] = p;
            }
        }

        List<SNETask> tasks = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            SNETask task = new SNETask(i, P, Q, Y, dY, gains, minGain, eta);
            tasks.add(task);
        }

        for (int iter = 1; iter <= iterations; iter++) {
            Math.pdist(Y, Q);
            double Qsum = 0.0;
            for (int i = 0; i < n; i++) {
                double[] Qi = Q[i];
                for (int j = 0; j < i; j++) {
                    double q = 1.0 / (1.0 + Qi[j]);
                    Qi[j] = q;
                    Q[j][i] = q;
                    Qsum += q;
                }
            }

            for (SNETask task : tasks) {
                task.Qsum = Qsum;
                task.momentum = momentum;
            }

            try {
                MulticoreExecutor.run(tasks);
            } catch (Exception e) {
                logger.error("t-SNE iteration task fails: {}", e);
            }

            if (iter > momentumSwitchIter) {
                momentum = finalMomentum;
            }

            // Compute current value of cost function
            if (iter % 100 == 0)   {
                double C = 0.0;
                for (int i = 0; i < n; i++) {
                    double[] Pi = P[i];
                    double[] Qi = Q[i];
                    for (int j = 0; j < i; j++) {
                        double p = Pi[j];
                        double q = Qi[j] / Qsum;
                        if (Double.isNaN(q) || q < 1E-12) q = 1E-12;
                        C += p * Math.log(p / q);
                    }
                }
                logger.info("Error after {} iterations: {}", iter, 2 * C);
            }
        }

        coordinates = Y;
    }

    private class SNETask implements Callable<Void> {
        int i;
        double[][] Y;
        double[][] dY;
        double[][] gains;
        double Qsum;
        double[][] P;
        double[][] Q;
        double minGain;
        double momentum;
        double eta;
        double[] dC;


        SNETask(int i, double[][] P, double[][] Q, double[][] Y, double[][] dY, double[][] gains, double minGain, double eta) {
            this.i = i;
            this.P = P;
            this.Q = Q;
            this.Y = Y;
            this.dY = dY;
            this.gains = gains;
            this.minGain = minGain;
            this.eta = eta;
            this.dC = new double[Y[0].length];
        }

        @Override
        public Void call() {
            int n = Y.length;
            int d = Y[0].length;
            Arrays.fill(dC, 0.0);

            // Compute gradient
            double[] Yi = Y[i];
            double[] Pi = P[i];
            double[] Qi = Q[i];
            for (int j = 0; j < n; j++) {
                if (i != j) {
                    double[] Yj = Y[j];
                    double q = Qi[j];
                    double z = 4.0 * (Pi[j] - (q / Qsum)) * q;
                    for (int k = 0; k < d; k++) {
                        dC[k] += (Yi[k] - Yj[k]) * z;
                    }
                }
            }

            // Perform the update
            double[] g = gains[i]; // dereference before the loop for better performance
            double[] dYi = dY[i];
            for (int k = 0; k < d; k++) {
                // Update gains
                g[k] = (Math.signum(dC[k]) != Math.signum(dYi[k])) ? (g[k] + .2) : (g[k] * .8);
                if (g[k] < minGain) g[k] = minGain;

                // Perform gradient update (with momentum and gains)
                dYi[k] = momentum * dYi[k] - eta * g[k] * dC[k];
                Yi[k] += dYi[k];
            }

            return null;
        }
    }

    /** Compute the Gaussian kernel (search the width for given perplexity. */
    private double[][] expd(double[][] D, double perplexity, double tol){
        int n          = D.length;
        double[][] P   = new double[n][n];
        double[] ds    = Math.rowSums(D);

        List<PerplexityTask> tasks = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            PerplexityTask task = new PerplexityTask(i, D, P, ds, perplexity, tol);
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
        int i;
        double[][] D;
        double[][] P;
        double[] ds;
        double perplexity;
        double tol;

        PerplexityTask(int i, double[][] D, double[][] P, double[] ds, double perplexity, double tol) {
            this.i = i;
            this.D = D;
            this.P = P;
            this.ds = ds;
            this.perplexity = perplexity;
            this.tol = tol;
        }

        @Override
        public Void call() {
            int n       = D.length;
            double logU = Math.log(perplexity);

            // Use sqrt(1 / avg of distance) to initialize beta
            double beta = Math.sqrt((n-1) / ds[i]);
            double betamin = 0.0;
            double betamax = 16 * beta;
            logger.debug("initial beta[{}] = {}", i, beta);

            // Evaluate whether the perplexity is within tolerance
            int iter = 0;
            double Hdiff = 0.0;
            do {
                double H = 0.0;
                double sum = 0.0;
                double[] Pi = P[i];
                double[] Di = D[i];
                for (int j = 0; j < n; j++) {
                    double d = beta * Di[j];
                    double p = Math.exp(-d);
                    Pi[j] = p;
                    sum += p;
                    H += p * d;
                }

                // discount D[i][i]
                Pi[i] = 0.0;
                sum -= 1.0;

                H = Math.log(sum) + H / sum;
                Hdiff = H - logU;

                if (Math.abs(Hdiff) > tol) {
                    if (Hdiff > 0) {
                        betamin = beta;
                        beta = (beta + betamax) / 2;
                    } else {
                        betamax = beta;
                        beta = (beta + betamin) / 2;
                    }
                }

                logger.debug("Hdiff = {}, beta[{}] = {}", Hdiff, i, beta);
            } while (Math.abs(Hdiff) > tol && ++iter < 50);

            return null;
        }
    }

    /**
     * Returns the coordinates of projected data.
     */
    public double[][] getCoordinates() {
        return coordinates;
    }
}
