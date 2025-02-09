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
package smile.manifold;

import java.io.Serial;
import java.io.Serializable;
import java.util.Arrays;
import java.util.Properties;
import java.util.stream.IntStream;
import smile.math.MathEx;
import smile.stat.distribution.GaussianDistribution;
import smile.util.IterativeAlgorithmController;

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
 * @param cost the objective function value.
 * @param coordinates the principal coordinates
 * @author Haifeng Li
 */
public record TSNE(double cost, double[][] coordinates) implements Serializable {
    @Serial
    private static final long serialVersionUID = 3L;
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(TSNE.class);

    /**
     * Training status per epoch.
     * @param epoch the iteration index, starting at 1.
     * @param cost the objective function value.
     */
    public record TrainingStatus(int epoch, double cost) {

    }

    /**
     * The t-SNE hyperparameters.
     * @param d the dimension of embedding space.
     * @param perplexity the perplexity of the conditional distribution.
     *                   The perplexity is related to the number of nearest
     *                   neighbors that is used in other manifold learning
     *                   algorithms. Consider selecting a value between 5
     *                   and 50. Different values can result in significantly
     *                   different results. The perplexity must be less than
     *                   the number of samples.
     * @param eta the learning rate. Usually in the range [10.0, 1000.0].
     *            If the learning rate is too high, the data may look like
     *            a "ball" with any point approximately equidistant from its
     *            nearest neighbours. If the learning rate is too low, most
     *            points may look compressed in a dense cloud with few outliers.
     * @param maxIter the maximum number of iterations. Should be at least 250.
     * @param earlyExaggeration Controls how tight natural clusters in the original
     *                          space are in the embedded space and how much space
     *                          will be between them. For larger values, the space
     *                          between natural clusters will be larger in the
     *                          embedded space. The choice of this parameter is not
     *                          very critical. If the cost function increases during
     *                          initial optimization, the early exaggeration factor
     *                          or the learning rate might be too high.
     * @param momentum the momentum factor.
     * @param finalMomentum the momentum in later stage.
     * @param momentumSwitchIter the number of iterations at which switch the
     *                           momentum to finalMomentum.
     * @param minGain the floor of gain.
     * @param controller the optional training controller.
     */
    public record Options(int d, double perplexity, double eta, int maxIter, double earlyExaggeration,
                          double momentum, double finalMomentum, int momentumSwitchIter, double minGain,
                          IterativeAlgorithmController<TrainingStatus> controller) {
        /** Constructor. */
        public Options {
            if (d < 2) {
                throw new IllegalArgumentException("Invalid dimension of feature space: " + d);
            }
            if (perplexity < 2) {
                throw new IllegalArgumentException("Invalid perplexity: " + perplexity);
            }
            if (eta <= 0) {
                throw new IllegalArgumentException("Invalid learning rate: " + eta);
            }
            if (maxIter < 250) {
                throw new IllegalArgumentException("maximum number of iterations: " + maxIter);
            }
            if (earlyExaggeration <= 0) {
                throw new IllegalArgumentException("Invalid early exaggeration: " + earlyExaggeration);
            }
            if (momentum <= 0) {
                throw new IllegalArgumentException("Invalid momentum: " + momentum);
            }
            if (finalMomentum <= 0) {
                throw new IllegalArgumentException("Invalid final momentum: " + finalMomentum);
            }
            if (momentumSwitchIter <= 0 || momentumSwitchIter >= maxIter) {
                throw new IllegalArgumentException("Invalid learning rate: " + momentumSwitchIter);
            }
            if (minGain <= 0) {
                throw new IllegalArgumentException("Invalid minimum gain: " + minGain);
            }
        }

        /**
         * Constructor.
         * @param d the dimension of embedding space.
         * @param perplexity the perplexity of the conditional distribution.
         *                   The perplexity is related to the number of nearest
         *                   neighbors that is used in other manifold learning algorithms.
         * @param eta the learning rate. Usually in the range [10.0, 1000.0].
         *            If the learning rate is too high, the data may look like
         *            a "ball" with any point approximately equidistant from its
         *            nearest neighbours. If the learning rate is too low, most
         *            points may look compressed in a dense cloud with few outliers.
         * @param maxIter the maximum number of iterations. Should be at least 250.
         */
        public Options(int d, double perplexity, double eta, int maxIter) {
            this(d, perplexity, eta, maxIter, 12, 0.5, 0.8, 250, 0.01, null);
        }

        /**
         * Returns the persistent set of hyperparameters.
         * @return the persistent set.
         */
        public Properties toProperties() {
            Properties props = new Properties();
            props.setProperty("smile.t_sne.d", Integer.toString(d));
            props.setProperty("smile.t_sne.perplexity", Double.toString(perplexity));
            props.setProperty("smile.t_sne.eta", Double.toString(eta));
            props.setProperty("smile.t_sne.iterations", Integer.toString(maxIter));
            props.setProperty("smile.t_sne.early_exaggeration", Double.toString(earlyExaggeration));
            props.setProperty("smile.t_sne.momentum", Double.toString(momentum));
            props.setProperty("smile.t_sne.final_momentum", Double.toString(finalMomentum));
            props.setProperty("smile.t_sne.momentum_switch", Integer.toString(momentumSwitchIter));
            props.setProperty("smile.t_sne.min_gain", Double.toString(minGain));
            return props;
        }

        /**
         * Returns the options from properties.
         *
         * @param props the hyperparameters.
         * @return the options.
         */
        public static Options of(Properties props) {
            int d = Integer.parseInt(props.getProperty("smile.t_sne.d", "2"));
            double perplexity = Double.parseDouble(props.getProperty("smile.t_sne.perplexity", "20"));
            double eta = Double.parseDouble(props.getProperty("smile.t_sne.eta", "200"));
            int maxIter = Integer.parseInt(props.getProperty("smile.t_sne.iterations", "1000"));
            double earlyExaggeration = Double.parseDouble(props.getProperty("smile.t_sne.early_exaggeration"));
            double momentum = Double.parseDouble(props.getProperty("smile.t_sne.momentum"));
            double finalMomentum = Double.parseDouble(props.getProperty("smile.t_sne.final_momentum"));
            int momentumSwitchIter = Integer.parseInt(props.getProperty("smile.t_sne.momentum_switch"));
            double minGain = Double.parseDouble(props.getProperty("smile.t_sne.momentum_switch"));
            return new Options(d, perplexity, eta, maxIter, earlyExaggeration, momentum, finalMomentum, momentumSwitchIter, minGain, null);
        }
    }

    /**
     * Fits t-SNE for given number of iterations.
     *
     * @param X the input data. If X is a square matrix, it is assumed to be
     *          the squared distance/dissimilarity matrix.
     * @return the model.
     */
    public static TSNE fit(double[][] X) {
        return fit(X, new Options(2, 20, 200, 1000));
    }

    /**
     * Fits t-SNE for given number of iterations.
     *
     * @param X the input data. If X is a square matrix, it is assumed to be
     *         the squared distance/dissimilarity matrix.
     * @param options the hyperparameters.
     * @return the model.
     */
    public static TSNE fit(double[][] X, Options options) {
        double eta = options.eta;
        int n = X.length;
        int d = options.d;

        double[][] D;
        if (X.length == X[0].length) {
            D = X;
        } else {
            D = new double[n][n];
            MathEx.pdist(X, D, MathEx::squaredDistance);
        }

        double[][] coordinates = new double[n][d];
        double[][] gains = new double[n][d]; // adjust learning rate for each point

        // Initialize Y randomly by N(0, 0.0001)
        GaussianDistribution gaussian = new GaussianDistribution(0.0, 0.0001);
        for (int i = 0; i < n; i++) {
            Arrays.fill(gains[i], 1.0);
            double[] Yi = coordinates[i];
            for (int j = 0; j < d; j++) {
                Yi[j] = gaussian.rand();
            }
        }

        // Large tolerance to speed up the search of Gaussian kernel width
        // A small difference of kernel width is not important.
        double[][] P = expd(D, options.perplexity, 1E-3);
        double[][] Q = new double[n][n];
        double[][] dY = new double[n][d];
        double[][] dC = new double[n][d];

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

        double cost = Double.MAX_VALUE;
        double momentum = options.momentum;
        for (int iter = 1; iter <= options.maxIter; iter++) {
            double Qsum = computeQ(coordinates, Q);
            IntStream.range(0, n).parallel().forEach(i -> sne(i, coordinates, P, Q, gains, dY[i], dC[i], Qsum, options.minGain));

            // gradient update with momentum and gains
            final double mu = momentum;
            IntStream.range(0, n).parallel().forEach(i -> {
                double[] Yi = coordinates[i];
                double[] dYi = dY[i];
                double[] dCi = dC[i];
                double[] g = gains[i];
                for (int k = 0; k < d; k++) {
                    dYi[k] = mu * dYi[k] - eta * g[k] * dCi[k];
                    Yi[k] += dYi[k];
                }
            });

            if (iter == options.momentumSwitchIter) {
                momentum = options.finalMomentum;
                for (int i = 0; i < n; i++) {
                    double[] Pi = P[i];
                    for (int j = 0; j < n; j++) {
                        Pi[j] /= options.earlyExaggeration;
                    }
                }
            }

            // Compute current value of cost function
            if (iter % 10 == 0 || iter == options.maxIter) {
                cost = computeCost(P, Q, Qsum);
                logger.info("Error after {} iterations: {}", iter, cost);

                if (options.controller != null) {
                    options.controller.submit(new TrainingStatus(iter, cost));
                    if (options.controller.isInterrupted()) break;
                }
            }
        }

        // Make solution zero-mean
        double[] colMeans = MathEx.colMeans(coordinates);
        IntStream.range(0, n).parallel().forEach(i -> {
            double[] Yi = coordinates[i];
            for (int j = 0; j < d; j++) {
                Yi[j] -= colMeans[j];
            }
        });

        return new TSNE(cost, coordinates);
    }

    /** Computes the gradients and updates the coordinates. */
    private static void sne(int i, double[][] Y, double[][] P, double[][] Q, double[][] gains, double[] dY, double[] dC, double Qsum, double minGain) {
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

    /** Compute the Gaussian kernel (search the width for given perplexity). */
    private static double[][] expd(double[][] D, double perplexity, double tol) {
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
    private static double computeQ(double[][] Y, double[][] Q) {
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
    private static double computeCost(double[][] P, double[][] Q, double Qsum) {
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
