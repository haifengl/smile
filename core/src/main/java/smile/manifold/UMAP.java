/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * SMILE is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with SMILE. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.manifold;

import java.util.Arrays;
import java.util.Properties;
import java.util.stream.IntStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import smile.feature.extraction.PCA;
import smile.graph.AdjacencyList;
import smile.graph.NearestNeighborGraph;
import smile.math.LevenbergMarquardt;
import smile.math.MathEx;
import smile.math.distance.Metric;
import smile.stat.distribution.GaussianDistribution;
import smile.tensor.ARPACK;
import smile.tensor.DenseMatrix;
import smile.tensor.EVD;
import smile.tensor.SparseMatrix;
import smile.util.function.DifferentiableMultivariateFunction;

/**
 * Uniform Manifold Approximation and Projection (UMAP).
 * <p>
 * UMAP is a nonlinear dimensionality reduction method designed for manifold
 * visualization and unsupervised representation learning. It rests upon three
 * key assumptions:
 * <ul>
 *   <li>The data manifold is locally Riemannian.</li>
 *   <li>The local metric is approximately constant in local neighborhoods.</li>
 *   <li>The manifold is locally connected.</li>
 * </ul>
 * <p>
 * From these topological assumptions, high-dimensional data is represented as a
 * weighted fuzzy simplicial set, and embedded into low dimensions by minimizing
 * cross-entropy via stochastic gradient descent.
 * <h3>References</h3>
 * <ul>
 *   <li>L. McInnes, J. Healy, and J. Melville. UMAP: Uniform Manifold Approximation
 *       and Projection for Dimension Reduction. arXiv:1802.03426, 2018.</li>
 *   <li><a href="https://umap-learn.readthedocs.io/en/latest/how_umap_works.html">How UMAP Works</a></li>
 * </ul>
 *
 * @see TSNE
 * @author Karl Li
 * @author Haifeng Li
 */
public class UMAP {
    private static final Logger logger = LoggerFactory.getLogger(UMAP.class);
    /** Threshold size for triggering approximate or sub-sampled operations. */
    private static final int LARGE_DATA_SIZE = 10000;

    /** Private constructor. */
    private UMAP() {}

    /**
     * UMAP hyperparameters.
     * @param k                 the number of nearest neighbors for local metric computation.
     * @param d                 the target embedding dimensionality.
     * @param epochs            the number of optimization epochs.
     * @param learningRate      the initial learning rate for stochastic gradient descent.
     * @param minDist           the effective minimum distance between embedded points.
     * @param spread            the effective scale of embedded points.
     * @param negativeSamples   the count of negative samples drawn per positive edge sample.
     * @param repulsionStrength weighting factor applied to negative sample repulsion.
     * @param localConnectivity the number of nearest neighbors assumed to be locally connected.
     */
    public record Options(int k, int d, int epochs, double learningRate,
                          double minDist, double spread, int negativeSamples,
                          double repulsionStrength, double localConnectivity) {

        /** Validates hyperparameter values. */
        public Options {
            if (k <= 1) {
                throw new IllegalArgumentException("Invalid k: " + k);
            }
            if (d <= 1) {
                throw new IllegalArgumentException("Invalid d: " + d);
            }
            if (learningRate <= 0.0) {
                throw new IllegalArgumentException("Invalid learningRate: " + learningRate);
            }
            if (minDist <= 0.0 || minDist > spread) {
                throw new IllegalArgumentException(String.format("Invalid minDist: %f, spread: %f", minDist, spread));
            }
            if (negativeSamples <= 0) {
                throw new IllegalArgumentException("Invalid negativeSamples: " + negativeSamples);
            }
            if (repulsionStrength <= 0.0) {
                throw new IllegalArgumentException("Invalid repulsionStrength: " + repulsionStrength);
            }
            if (localConnectivity <= 0.0) {
                throw new IllegalArgumentException("Invalid localConnectivity: " + localConnectivity);
            }
        }

        /**
         * Constructor with default parameters for a given neighborhood size k.
         * @param k the number of nearest neighbors.
         */
        public Options(int k) {
            this(k, 2, 0, 1.0, 0.1, 1.0, 5, 1.0, 1.0);
        }

        /**
         * Returns hyperparameters as a Properties object.
         * @return the hyperparameters properties.
         */
        public Properties toProperties() {
            Properties props = new Properties();
            props.setProperty("smile.umap.k", Integer.toString(k));
            props.setProperty("smile.umap.d", Integer.toString(d));
            props.setProperty("smile.umap.epochs", Integer.toString(epochs));
            props.setProperty("smile.umap.learning_rate", Double.toString(learningRate));
            props.setProperty("smile.umap.min_dist", Double.toString(minDist));
            props.setProperty("smile.umap.spread", Double.toString(spread));
            props.setProperty("smile.umap.negative_samples", Integer.toString(negativeSamples));
            props.setProperty("smile.umap.repulsion_strength", Double.toString(repulsionStrength));
            props.setProperty("smile.umap.local_connectivity", Double.toString(localConnectivity));
            return props;
        }

        /**
         * Parses hyperparameters from a Properties instance.
         * @param props configuration properties.
         * @return the parsed Options.
         */
        public static Options of(Properties props) {
            int k = Integer.parseInt(props.getProperty("smile.umap.k", "15"));
            int d = Integer.parseInt(props.getProperty("smile.umap.d", "2"));
            int epochs = Integer.parseInt(props.getProperty("smile.umap.epochs", "0"));
            double learningRate = Double.parseDouble(props.getProperty("smile.umap.learning_rate", "1.0"));
            double minDist = Double.parseDouble(props.getProperty("smile.umap.min_dist", "0.1"));
            double spread = Double.parseDouble(props.getProperty("smile.umap.spread", "1.0"));
            int negativeSamples = Integer.parseInt(props.getProperty("smile.umap.negative_samples", "5"));
            double repulsionStrength = Double.parseDouble(props.getProperty("smile.umap.repulsion_strength", "1.0"));
            double localConnectivity = Double.parseDouble(props.getProperty("smile.umap.local_connectivity", "1.0"));
            return new Options(k, d, epochs, learningRate, minDist, spread,
                    negativeSamples, repulsionStrength, localConnectivity);
        }
    }

    /**
     * Executes UMAP on coordinate observations using Euclidean distance.
     * @param data    input observations.
     * @param options algorithm hyperparameters.
     * @return low-dimensional coordinates matrix.
     */
    public static double[][] fit(double[][] data, Options options) {
        NearestNeighborGraph nng = data.length <= LARGE_DATA_SIZE
                ? NearestNeighborGraph.of(data, options.k)
                : NearestNeighborGraph.descent(data, options.k);
        return fit(data, nng, options);
    }

    /**
     * Executes UMAP with a custom metric distance function.
     * @param data     input observations.
     * @param distance distance metric.
     * @param options  algorithm hyperparameters.
     * @param <T>      point data type.
     * @return low-dimensional coordinates matrix.
     */
    public static <T> double[][] fit(T[] data, Metric<T> distance, Options options) {
        NearestNeighborGraph nng = data.length <= LARGE_DATA_SIZE
                ? NearestNeighborGraph.of(data, distance, options.k)
                : NearestNeighborGraph.descent(data, distance, options.k);
        return fit(data, nng, options);
    }

    /**
     * Executes UMAP using a precomputed nearest neighbor graph.
     * @param data    input observations.
     * @param nng     nearest neighbor graph.
     * @param options algorithm hyperparameters.
     * @param <T>     point data type.
     * @return low-dimensional coordinates matrix.
     */
    public static <T> double[][] fit(T[] data, NearestNeighborGraph nng, Options options) {
        int d = options.d;
        int epochs = options.epochs;
        if (epochs < 10) {
            epochs = data.length > LARGE_DATA_SIZE ? 200 : 500;
            logger.info("Set epochs = {}", epochs);
        }

        SparseMatrix conorm = computeFuzzySimplicialSet(nng, options.localConnectivity);

        int n = nng.size();
        double[][] coordinates;
        boolean connected = false;
        if (n <= LARGE_DATA_SIZE) {
            int[][] cc = nng.graph(false).bfcc();
            logger.info("The nearest neighbor graph has {} connected component(s).", cc.length);
            connected = cc.length == 1;
        }

        if (connected) {
            logger.info("Spectral initialization will be attempted.");
            coordinates = spectralLayout(nng, d);
            noisyScale(coordinates, 10, 0.0001);
        } else {
            if (data instanceof double[][]) {
                logger.info("PCA-based initialization will be attempted.");
                coordinates = pcaLayout((double[][]) data, d);
                noisyScale(coordinates, 10, 0.0001);
            } else {
                logger.info("Random initialization will be attempted.");
                coordinates = randomLayout(n, d);
            }
        }
        normalize(coordinates, 10);
        logger.info("Finish embedding initialization");

        double[] curve = fitCurve(options.spread, options.minDist);
        logger.info("Finish fitting the curve parameters: {}", Arrays.toString(curve));
        // Layout optimization schedule
        SparseMatrix epochsPerSample = computeEpochPerSample(conorm, epochs);
        logger.info("Start optimizing the layout");
        optimizeLayout(coordinates, curve, epochsPerSample, epochs,
                options.learningRate, options.negativeSamples, options.repulsionStrength);
        return coordinates;
    }

    /** Differentiable similarity function: psi(d) = 1 / (1 + a * d^(2b)). */
    private static final class Curve implements DifferentiableMultivariateFunction {
        @Override public double f(double[] x) {
            double a = x[0], exponent = x[1], d = x[2];
            return 1.0 / (1.0 + a * Math.pow(d, exponent));
        }

        @Override public double g(double[] x, double[] grad) {
            double a = x[0], exponent = x[1], d = x[2];
            double dPow = Math.pow(d, exponent);
            double denom = 1.0 + a * dPow;
            double denomSq = denom * denom;

            grad[0] = -dPow / denomSq;
            grad[1] = -(a * exponent * Math.log(d) * dPow) / denomSq;
            return 1.0 / denom;
        }
    }

    /**
     * Fits the nonlinear curve parameters (a, b) approximating the exponential decay.
     * @param spread  scale of embedding.
     * @param minDist minimum distance between points.
     * @return fitted curve parameters [a, b].
     */
    private static double[] fitCurve(double spread, double minDist) {
        final int numPoints = 300;
        double[] x = new double[numPoints];
        double[] y = new double[numPoints];
        double step = (3.0 * spread) / numPoints;

        // evaluate exponential decay points
        for (int i = 0; i < numPoints; i++) {
            double dist = (i + 1) * step;
            x[i] = dist;
            y[i] = dist < minDist ? 1.0 : Math.exp(-(dist - minDist) / spread);
        }

        double[] initialGuess = {0.5, 0.0};
        LevenbergMarquardt fit = LevenbergMarquardt.fit(new Curve(), x, y, initialGuess);
        double[] parameters = fit.parameters();
        parameters[1] *= 0.5;
        return parameters;
    }

    /**
     * Constructs the fuzzy simplicial set across all observations.
     * @param nng               nearest neighbor graph.
     * @param localConnectivity local connectivity scale factor.
     * @return fuzzy simplicial adjacency matrix.
     */
    private static SparseMatrix computeFuzzySimplicialSet(NearestNeighborGraph nng, double localConnectivity) {
        double[][] scales = smoothKnnDist(nng.distances(), nng.k(), 64, localConnectivity, 1.0);
        double[] sigma = scales[0];
        double[] rho = scales[1];

        int n = nng.size();
        AdjacencyList directGraph = computeMembershipStrengths(nng, sigma, rho);
        AdjacencyList conormGraph = new AdjacencyList(n, false);

        // combine fuzzy simplicial sets
        for (int u = 0; u < n; u++) {
            int src = u;
            directGraph.forEachEdge(src, (dst, weightA) -> {
                double weightB = directGraph.getWeight(dst, src);
                double combined = weightA + weightB - (weightA * weightB);
                conormGraph.setWeight(src, dst, combined);
            });
        }
        return conormGraph.toMatrix();
    }

    /**
     * Computes the continuous local metric bandwidth (sigma) and distance to nearest
     * neighbor (rho) via binary search.
     * @param distances         sorted distance matrix to nearest neighbors.
     * @param k                 target number of neighbors.
     * @param maxIter           maximum iterations for binary search.
     * @param localConnectivity minimum connected neighbor count.
     * @param bandwidth         kernel bandwidth factor.
     * @return array containing [sigma, rho].
     */
    private static double[][] smoothKnnDist(double[][] distances, double k, int maxIter,
                                            double localConnectivity, double bandwidth) {
        final double TOLERANCE = 1E-5;
        final double MIN_DIST_SCALE = 1E-3;
        final int n = distances.length;
        final double targetCardinality = MathEx.log2(k) * bandwidth;
        double[] rho = new double[n];
        double[] sigma = new double[n];

        long totalEntries = 0;
        double sumDistance = 0.0;
        for (double[] row : distances) {
            sumDistance += MathEx.sum(row);
            totalEntries += row.length;
        }
        final double globalMeanDistance = sumDistance / totalEntries;

        // Parallel smooth knn distance evaluation
        IntStream.range(0, n).parallel().forEach(i -> {
            double[] row = distances[i];
            double[] positiveDists = Arrays.stream(row).filter(d -> d > 0.0).toArray();

            if (positiveDists.length >= localConnectivity) {
                int baseIndex = (int) Math.floor(localConnectivity);
                double frac = localConnectivity - baseIndex;
                if (baseIndex > 0) {
                    rho[i] = positiveDists[baseIndex - 1];
                    if (frac > TOLERANCE) {
                        rho[i] += frac * (positiveDists[baseIndex] - positiveDists[baseIndex - 1]);
                    }
                } else {
                    rho[i] = frac * positiveDists[0];
                }
            } else if (positiveDists.length > 0) {
                rho[i] = MathEx.max(positiveDists);
            }

            // Binary search for local metric scale
            double low = 0.0, high = Double.POSITIVE_INFINITY, mid = 1.0;
            for (int iter = 0; iter < maxIter; iter++) {
                double pSum = 0.0;
                for (int j = 1; j < row.length; j++) {
                    double diff = row[j] - rho[i];
                    pSum += (diff > 0.0) ? Math.exp(-diff / mid) : 1.0;
                }

                if (Math.abs(pSum - targetCardinality) < TOLERANCE) {
                    break; // convergence met
                }

                if (pSum > targetCardinality) {
                    high = mid;
                    mid = 0.5 * (low + high);
                } else {
                    low = mid;
                    mid = Double.isInfinite(high) ? (mid * 2.0) : (0.5 * (low + high));
                }
            }

            double minScale = (rho[i] > 0.0)
                    ? (MIN_DIST_SCALE * MathEx.mean(row))
                    : (MIN_DIST_SCALE * globalMeanDistance);
            sigma[i] = Math.max(mid, minScale);
        });
        return new double[][]{sigma, rho};
    }

    /** Determines directional edge weights for the 1-skeleton of each local fuzzy simplicial set. */
    private static AdjacencyList computeMembershipStrengths(NearestNeighborGraph nng, double[] sigma, double[] rho) {
        int n = nng.size();
        int[][] neighbors = nng.neighbors();
        double[][] distances = nng.distances();

        AdjacencyList graph = new AdjacencyList(n, true);
        for (int i = 0; i < n; i++) {
            int[] nbrs = neighbors[i];
            double[] dists = distances[i];
            double rhoI = rho[i];
            double sigmaI = sigma[i];

            for (int j = 0; j < nbrs.length; j++) {
                double dist = dists[j] - rhoI;
                double weight = dist <= 0.0 ? 1.0 : Math.exp(-dist / sigmaI);
                graph.setWeight(i, nbrs[j], weight);
            }
        }
        return graph;
    }

    /** Generates uniformly distributed random coordinates in [-10, 10]. */
    private static double[][] randomLayout(int n, int d) {
        double[][] coords = new double[n][d];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < d; j++) {
                coords[i][j] = MathEx.random(-10.0, 10.0);
            }
        }
        return coords;
    }

    /** Generates initial coordinates using Principal Component Analysis. */
    private static double[][] pcaLayout(double[][] data, int d) {
        return PCA.fit(data).getProjection(d).apply(data);
    }

    /** Computes initial coordinates using the normalized graph Laplacian. */
    private static double[][] spectralLayout(NearestNeighborGraph nng, int d) {
        int[][] neighbors = nng.neighbors();
        double[][] distances = nng.distances();
        int n = nng.size();
        double[] degrees = new double[n];

        IntStream.range(0, n).parallel()
                .forEach(i -> degrees[i] = 1.0 / Math.sqrt(MathEx.sum(distances[i])));

        logger.info("Spectral layout computes Laplacian...");
        AdjacencyList laplacian = new AdjacencyList(n, false);
        for (int i = 0; i < n; i++) {
            laplacian.setWeight(i, i, 1.0);
            int[] nbrs = neighbors[i];
            double[] dists = distances[i];
            for (int j = 0; j < nbrs.length; j++) {
                double w = -degrees[i] * dists[j] * degrees[nbrs[j]];
                laplacian.setWeight(i, nbrs[j], w);
            }
        }

        int k = d + 1;
        int numEigen = Math.min(2 * k + 1, (int) Math.sqrt(n));
        numEigen = Math.max(numEigen, k);
        numEigen = Math.min(numEigen, n);

        SparseMatrix L = laplacian.toMatrix();
        logger.info("Spectral layout computes {} eigen vectors", numEigen);
        EVD eigen = ARPACK.syev(L, ARPACK.SymmOption.SM, numEigen);
        DenseMatrix vectors = eigen.Vr();
        double[][] coordinates = new double[n][d];
        for (int j = d; --j >= 0; ) {
            int col = vectors.ncol() - j - 2;
            for (int i = 0; i < n; i++) {
                coordinates[i][j] = vectors.get(i, col);
            }
        }
        return coordinates;
    }

    /** Adds Gaussian jitter to scaled coordinates. */
    private static void noisyScale(double[][] coordinates, double scale, double noise) {
        int d = coordinates[0].length;
        double maxCoord = Double.NEGATIVE_INFINITY;
        for (double[] point : coordinates) {
            for (int j = 0; j < d; j++) {
                maxCoord = Math.max(maxCoord, Math.abs(point[j]));
            }
        }
        if (maxCoord <= 0.0) return;
        double factor = scale / maxCoord;
        GaussianDistribution normal = new GaussianDistribution(0.0, noise);
        for (double[] point : coordinates) {
            for (int j = 0; j < d; j++) {
                point[j] = factor * point[j] + normal.rand();
            }
        }
    }

    /** Normalizes coordinates to fit within [0, scale]. */
    private static void normalize(double[][] coordinates, double scale) {
        int d = coordinates[0].length;
        double[] maxVal = MathEx.colMax(coordinates);
        double[] minVal = MathEx.colMin(coordinates);
        double[] span = new double[d];
        for (int j = 0; j < d; j++) {
            span[j] = maxVal[j] - minVal[j];
        }

        for (double[] point : coordinates) {
            for (int j = 0; j < d; j++) {
                if (span[j] == 0.0) {
                    point[j] = 0.0;
                } else {
                    point[j] = scale * (point[j] - minVal[j]) / span[j];
                }
            }
        }
    }

    /** Minimizes fuzzy set cross-entropy using stochastic gradient descent and negative sampling. */
    private static void optimizeLayout(double[][] embedding, double[] curve, SparseMatrix epochsPerSample,
                                       int epochs, double initialAlpha, int negativeSamples, double gamma) {
        int n = embedding.length;
        int d = embedding[0].length;
        double a = curve[0], b = curve[1], currentLearningRate = initialAlpha;
        // Negative sampling rate schedule
        SparseMatrix epochsPerNegativeSample = epochsPerSample.copy();
        epochsPerNegativeSample.nonzeros().forEach(entry -> entry.update(entry.x / negativeSamples));
        SparseMatrix epochNextNegativeSample = epochsPerNegativeSample.copy();
        SparseMatrix epochNextSample = epochsPerSample.copy();
        // Stochastic gradient descent iterations
        for (int iter = 1; iter <= epochs; iter++) {
            for (SparseMatrix.Entry edge : epochNextSample) {
                if (edge.x > 0.0 && edge.x <= iter) {
                    int src = edge.i, dst = edge.j, edgeIndex = edge.index;
                    double[] current = embedding[src];
                    double[] target = embedding[dst];

                    double sqDist = MathEx.squaredDistance(current, target);
                    if (sqDist > 0.0) {
                        double gradFactor = -2.0 * a * b * Math.pow(sqDist, b - 1.0);
                        gradFactor /= (a * Math.pow(sqDist, b) + 1.0);

                        for (int i = 0; i < d; i++) {
                            double grad = clamp(gradFactor * (current[i] - target[i]));
                            current[i] += grad * currentLearningRate;
                            target[i] -= grad * currentLearningRate;
                        }
                    }

                    edge.update(edge.x + epochsPerSample.get(edgeIndex));

                    int negCount = (int) ((iter - epochNextNegativeSample.get(edgeIndex)) / epochsPerNegativeSample.get(edgeIndex));

                    for (int p = 0; p < negCount; p++) {
                        int sampled = MathEx.randomInt(n);
                        if (src == sampled) continue;
                        double[] negTarget = embedding[sampled];
                        double negSqDist = MathEx.squaredDistance(current, negTarget);

                        double negGradFactor = 0.0;
                        if (negSqDist > 0.0) {
                            negGradFactor = 2.0 * gamma * b;
                            negGradFactor /= (0.001 + negSqDist) * (a * Math.pow(negSqDist, b) + 1.0);
                        }
                        for (int i = 0; i < d; i++) {
                            double grad = (negGradFactor > 0.0) ? clamp(negGradFactor * (current[i] - negTarget[i])) : 4.0;
                            current[i] += grad * currentLearningRate;
                        }
                    }

                    epochNextNegativeSample.set(edgeIndex, epochNextNegativeSample.get(edgeIndex) + epochsPerNegativeSample.get(edgeIndex) * negCount);
                }
            }
            logger.info("The learning rate at {} iterations: {}", iter, currentLearningRate);
            currentLearningRate = initialAlpha * (1.0 - (double) iter / epochs);
        }
    }

    /** Calculates the sample spacing (epochs per sample) for each 1-simplex edge. */
    private static SparseMatrix computeEpochPerSample(SparseMatrix strength, int epochs) {
        double maxWeight = strength.nonzeros().mapToDouble(w -> w.x).max().orElse(0.0);
        double minThreshold = maxWeight / epochs;
        strength.nonzeros().forEach(entry -> {
            if (entry.x < minThreshold) {
                entry.update(0.0);
            } else {
                entry.update(maxWeight / entry.x);
            }
        });
        return strength;
    }

    /** Restricts values to the interval [-4.0, 4.0]. */
    private static double clamp(double val) {
        return Math.min(4.0, Math.max(val, -4.0));
    }
}
