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

import java.util.Arrays;
import java.util.Properties;
import java.util.stream.IntStream;
import smile.feature.extraction.PCA;
import smile.graph.AdjacencyList;
import smile.graph.NearestNeighborGraph;
import smile.math.LevenbergMarquardt;
import smile.math.MathEx;
import smile.math.distance.Metric;
import smile.math.matrix.ARPACK;
import smile.math.matrix.Matrix;
import smile.math.matrix.SparseMatrix;
import smile.stat.distribution.GaussianDistribution;
import smile.util.function.DifferentiableMultivariateFunction;

/**
 * Uniform Manifold Approximation and Projection.
 * UMAP is a dimension reduction technique that can be used for visualization
 * similarly to t-SNE, but also for general non-linear dimension reduction.
 * The algorithm is founded on three assumptions about the data:
 * <ul>
 * <li>The data is uniformly distributed on a Riemannian manifold;</li>
 * <li>The Riemannian metric is locally constant (or can be approximated as
 * such);</li>
 * <li>The manifold is locally connected.</li>
 * </ul>
 * From these assumptions it is possible to model the manifold with a fuzzy
 * topological structure. The embedding is found by searching for a low
 * dimensional projection of the data that has the closest possible equivalent
 * fuzzy topological structure.
 * <h2>References</h2>
 * <ol>
 * <li>McInnes, L, Healy, J, UMAP: Uniform Manifold Approximation and Projection for Dimension Reduction, ArXiv e-prints 1802.03426, 2018</li>
 * <li><a href="https://umap-learn.readthedocs.io/en/latest/how_umap_works.html">How UMAP Works</a></li>
 * </ol>
 *
 * @see TSNE
 *
 * @author Karl Li
 */
public class UMAP {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(UMAP.class);
    /** Large data size threshold. */
    private static final int LARGE_DATA_SIZE = 10000;

    /** Private constructor to prevent object creation. */
    private UMAP() {

    }

    /**
     * The UMAP hyperparameters.
     * @param k       k-nearest neighbors. Larger values result in more global views
     *                of the manifold, while smaller values result in more local data
     *                being preserved. Generally in the range 2 to 100.
     * @param d       The target embedding dimensions. defaults to 2 to provide easy
     *                visualization, but can reasonably be set to any integer value
     *                in the range 2 to 100.
     * @param epochs  The number of iterations to optimize the
     *                low-dimensional representation. Larger values result in more
     *                accurate embedding. Muse be at least 10. Choose wise value
     *                based on the size of the input data, e.g, 200 for large
     *                data (1000+ samples), 500 for small.
     * @param learningRate      The initial learning rate for the embedding optimization,
     *                          default 1.
     * @param minDist           The desired separation between close points in the embedding
     *                          space. Smaller values will result in a more clustered/clumped
     *                          embedding where nearby points on the manifold are drawn closer
     *                          together, while larger values will result on a more even
     *                          disperse of points. The value should be set no-greater than
     *                          and relative to the spread value, which determines the scale
     *                          at which embedded points will be spread out. default 0.1.
     * @param spread            The effective scale of embedded points. In combination with
     *                          minDist, this determines how clustered/clumped the embedded
     *                          points are. default 1.0.
     * @param negativeSamples   The number of negative samples to select per positive sample
     *                          in the optimization process. Increasing this value will result
     *                          in greater repulsive force being applied, greater optimization
     *                          cost, but slightly more accuracy, default 5.
     * @param repulsionStrength Weighting applied to negative samples in low dimensional
     *                          embedding optimization. Values higher than one will result in
     *                          greater weight being given to negative samples, default 1.0.
     * @param localConnectivity The local connectivity required. That is, the
     *                          number of nearest neighbors that should be assumed
     *                          to be connected at a local level. The higher this
     *                          value the more connected the manifold becomes locally.
     *                          In practice this should be not more than the local
     *                          intrinsic dimension of the manifold.
     */
    public record Options(int k, int d, int epochs, double learningRate,
                          double minDist, double spread, int negativeSamples,
                          double repulsionStrength, double localConnectivity) {
        /** Constructor. */
        public Options {
            if (k < 2) {
                throw new IllegalArgumentException("Invalid number of nearest neighbors: " + k);
            }
            if (d < 2) {
                throw new IllegalArgumentException("Invalid dimension of feature space: " + d);
            }
            if (minDist <= 0) {
                throw new IllegalArgumentException("minDist must greater than 0: " + minDist);
            }
            if (minDist > spread) {
                throw new IllegalArgumentException("minDist must be less than or equal to spread: " + minDist + ", spread=" + spread);
            }
            if (learningRate <= 0) {
                throw new IllegalArgumentException("learningRate must greater than 0: " + learningRate);
            }
            if (negativeSamples <= 0) {
                throw new IllegalArgumentException("negativeSamples must greater than 0: " + negativeSamples);
            }
            if (localConnectivity < 1) {
                throw new IllegalArgumentException("localConnectivity must be at least 1.0: " + localConnectivity);
            }
        }

        /**
         * Constructor.
         * @param k k-nearest neighbor.
         */
        public Options(int k) {
            this(k, 2, 0, 1.0, 0.1, 1.0, 5, 1.0, 1.0);
        }

        /**
         * Returns the persistent set of hyperparameters.
         * @return the persistent set.
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
         * Returns the options from properties.
         *
         * @param props the hyperparameters.
         * @return the options.
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
            return new Options(k, d, epochs, learningRate, minDist, spread, negativeSamples,
            repulsionStrength, localConnectivity);
        }
    }

    /**
     * Runs the UMAP algorithm with Euclidean distance.
     *
     * @param data    the input data.
     * @param options the hyperparameters.
     * @return The embedding coordinates.
     */
    public static double[][] fit(double[][] data, Options options) {
        NearestNeighborGraph nng = data.length <= LARGE_DATA_SIZE ?
                NearestNeighborGraph.of(data, options.k) :
                NearestNeighborGraph.descent(data, options.k);
        return fit(data, nng, options);
    }

    /**
     * Runs the UMAP algorithm.
     *
     * @param data     the input data.
     * @param distance the distance function.
     * @param options  the hyperparameters.
     * @param <T> The data type of points.
     * @return The embedding coordinates.
     */
    public static <T> double[][] fit(T[] data, Metric<T> distance, Options options) {
        NearestNeighborGraph nng = data.length <= LARGE_DATA_SIZE ?
                NearestNeighborGraph.of(data, distance, options.k) :
                NearestNeighborGraph.descent(data, distance, options.k);
        return fit(data, nng, options);
    }

    /**
     * Runs the UMAP algorithm.
     *
     * @param data    the input data.
     * @param nng     the k-nearest neighbor graph.
     * @param options the hyperparameters.
     * @param <T> the data type of points.
     * @return the embedding coordinates.
     */
    public static <T> double[][] fit(T[] data, NearestNeighborGraph nng, Options options) {
        int d = options.d;
        int epochs = options.epochs;
        if (epochs < 10) {
            epochs = data.length > LARGE_DATA_SIZE ? 200 : 500;
            logger.info("Set epochs = {}", epochs);
        }

        // Construct the local fuzzy simplicial set by locally approximating
        // geodesic distance at each point, and then combining all the local
        // fuzzy simplicial sets into a global one via a fuzzy union.
        SparseMatrix conorm = computeFuzzySimplicialSet(nng, options.localConnectivity);

        // Initialize embedding
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
                noisyScale(coordinates,10, 0.0001);
            } else {
                logger.info("Random initialization will be attempted.");
                coordinates = randomLayout(n, d);
            }
        }
        normalize(coordinates, 10);
        logger.info("Finish embedding initialization");

        // parameters for the differentiable curve used in lower
        // dimensional fuzzy simplicial complex construction.
        double[] curve = fitCurve(options.spread, options.minDist);
        logger.info("Finish fitting the curve parameters: {}", Arrays.toString(curve));

        // Optimizing the embedding
        SparseMatrix epochsPerSample = computeEpochPerSample(conorm, epochs);
        logger.info("Start optimizing the layout");
        optimizeLayout(coordinates, curve, epochsPerSample, epochs, options.learningRate, options.negativeSamples, options.repulsionStrength);
        return coordinates;
    }

    /**
     * The curve function:
     * <pre>
     * 1.0 / (1.0 + a * x ^ (2 * b))
     * </pre>
     */
    private static class Curve implements DifferentiableMultivariateFunction {
        @Override
        public double f(double[] x) {
            return 1 / (1 + x[0] * Math.pow(x[2], x[1]));
        }

        @Override
        public double g(double[] x, double[] g) {
            double pow = Math.pow(x[2], x[1]);
            double de = 1 + x[0] * pow;
            g[0] = -pow / (de * de);
            g[1] = -(x[0] * x[1] * Math.log(x[2]) * pow) / (de * de);
            return 1 / de;
        }
    }

    /**
     * Fits the differentiable curve used in lower dimensional fuzzy simplicial
     * complex construction. We want the smooth curve (from a pre-defined
     * family with simple gradient) that best matches an offset exponential decay.
     *
     * @param spread  The effective scale of embedded points. In combination with
     *                minDist, this determines how clustered/clumped the embedded
     *                points are. default 1.0
     * @param minDist The desired separation between close points in the embedding
     *                space. The value should be set no-greater than and relative to
     *                the spread value, which determines the scale at which embedded
     *                points will be spread out, default 0.1
     * @return the parameters of differentiable curve.
     */
    private static double[] fitCurve(double spread, double minDist) {
        int size = 300;
        double[] x = new double[size];
        double[] y = new double[size];
        double end = 3 * spread;
        double interval = end / size;
        for (int i = 0; i < size; i++) {
            x[i] = (i + 1) * interval;
            y[i] = x[i] < minDist ? 1 : Math.exp(-(x[i] - minDist) / spread);
        }
        double[] p = {0.5, 0.0};
        LevenbergMarquardt curveFit = LevenbergMarquardt.fit(new Curve(), x, y, p);
        var result = curveFit.parameters;
        result[1] /= 2; // We fit 2*b in Curve function definition.
        return result;
    }

    /**
     * Computes the fuzzy simplicial set as a fuzzy graph with the
     * probabilistic t-conorm. This is done by locally approximating
     * geodesic distance at each point, creating a fuzzy simplicial
     * set for each such point, and then combining all the local
     * fuzzy simplicial sets into a global one via a fuzzy union.
     *
     * @param nng     The k-nearest neighbor graph.
     * @param localConnectivity The local connectivity required. That is, the
     *                          number of nearest neighbors that should be assumed
     *                          to be connected at a local level. The higher this
     *                          value the more connected the manifold becomes locally.
     *                          In practice this should be not more than the local
     *                          intrinsic dimension of the manifold.
     * @return A fuzzy simplicial set represented as a sparse matrix. The (i, j)
     * entry of the matrix represents the membership strength of the
     * 1-simplex between the ith and jth sample points.
     */
    private static SparseMatrix computeFuzzySimplicialSet(NearestNeighborGraph nng, double localConnectivity) {
        // Computes a continuous version of the distance to the kth nearest neighbor.
        // That is, this is similar to knn-distance but allows continuous k values
        // rather than requiring an integral k. In essence, we are simply computing
        // the distance such that the cardinality of fuzzy set we generate is k.
        double[][] result = smoothKnnDist(nng.distances(), nng.k(), 64, localConnectivity, 1.0);
        // The smooth approximator to knn-distance
        double[] sigma = result[0];
        // The distance to nearest neighbor
        double[] rho = result[1];

        int n = nng.size();
        AdjacencyList strength = computeMembershipStrengths(nng, sigma, rho);
        // probabilistic t-conorm: (a + a' - a .* a')
        AdjacencyList conorm = new AdjacencyList(n, false);
        for (int i = 0; i < n; i++) {
            int u = i;
            strength.forEachEdge(u, (v, a) -> {
                double b = strength.getWeight(v, u);
                double w = a + b - a * b;
                conorm.setWeight(u, v, w);
            });
        }
        return conorm.toMatrix();
    }

    /**
     * Computes a continuous version of the distance to the kth nearest
     * neighbor. That is, this is similar to knn-distance but allows continuous
     * k values rather than requiring an integral k. Essentially we are simply
     * computing the distance such that the cardinality of fuzzy set we generate
     * is k.
     * @param distances Distances to nearest neighbors for each sample. Each row
     *                  should be a sorted list of distances to nearest neighbors.
     * @param k The number of nearest neighbors to approximate for.
     * @param maxIter The max number of iterations for the binary search of
     *                the correct distance value.
     * @param localConnectivity The local connectivity required. That is, the
     *                          number of nearest neighbors that should be assumed
     *                          to be connected at a local level. The higher this
     *                          value the more connected the manifold becomes locally.
     *                          In practice this should be not more than the local
     *                          intrinsic dimension of the manifold.
     * @param bandwidth The bandwidth of the kernel. Larger bandwidth will produce
     *                  larger return values.
     * @return knn: the distance to kth nearest neighbor, as suitably approximated.
     *         rho: the distance to the first nearest neighbor for each point.
     */
    private static double[][] smoothKnnDist(double[][] distances, double k, int maxIter,
                                            double localConnectivity, double bandwidth) {
        final double SMOOTH_K_TOLERANCE = 1E-5;
        final double MIN_K_DIST_SCALE = 1E-3;
        int n = distances.length;
        double target = MathEx.log2(k) * bandwidth;
        double[] rho = new double[n];
        double[] knn = new double[n];

        int length = 0;
        double mean = 0;
        for (var row : distances) {
            mean += MathEx.sum(row);
            length += row.length;
        }
        mean /= length;

        final double mu = mean;
        IntStream.range(0, n).parallel().forEach(i -> {
            double lo = 0;
            double hi = Double.POSITIVE_INFINITY;
            double mid = 1;

            double[] nonZeroDists = Arrays.stream(distances[i]).filter(x -> x > 0).toArray();
            if (nonZeroDists.length >= localConnectivity) {
                int index = (int) Math.floor(localConnectivity);
                double interpolation = localConnectivity - index;
                if (index > 0) {
                    rho[i] = nonZeroDists[index - 1];
                    if (interpolation > SMOOTH_K_TOLERANCE) {
                        rho[i] += interpolation * (nonZeroDists[index] - nonZeroDists[index - 1]);
                    }
                } else {
                    rho[i] = interpolation * nonZeroDists[0];
                }
            } else if (nonZeroDists.length > 0) {
                rho[i] = MathEx.max(nonZeroDists);
            }

            for (int iter = 0; iter < maxIter; iter++) {
                double psum  = 0.0;
                for (int j = 1; j < distances[j].length; j++) {
                    double d = distances[i][j] - rho[i];
                    psum  += d > 0 ? Math.exp(-d/mid) : 1;
                }

                if (Math.abs(psum - target) < SMOOTH_K_TOLERANCE) {
                    break;
                }

                if (psum  > target) {
                    hi = mid;
                    mid = (lo + hi) / 2.0;
                } else {
                    lo = mid;
                    if (Double.isInfinite(hi)) {
                        mid *= 2;
                    } else {
                        mid = (lo + hi) / 2.0;
                    }
                }
            }

            knn[i] = mid;
            if (rho[i] > 0) {
                double mui = MathEx.mean(distances[i]);
                if (knn[i] < MIN_K_DIST_SCALE * mui) {
                    knn[i] = MIN_K_DIST_SCALE * mui;
                }
            } else {
                if (knn[i] < MIN_K_DIST_SCALE * mu) {
                    knn[i] = MIN_K_DIST_SCALE * mu;
                }
            }
        });
        return new double[][]{knn, rho};
    }

    /**
     * Computes the membership strength for the 1-skeleton of each local
     * fuzzy simplicial set. This is formed as a sparse matrix where each row is
     * a local fuzzy simplicial set, with a membership strength for the
     * 1-simplex to each other data point.
     */
    private static AdjacencyList computeMembershipStrengths(NearestNeighborGraph nng, double[] sigma, double[] rho) {
        int n = nng.size();
        int[][] neighbors = nng.neighbors();
        double[][] distances = nng.distances();

        AdjacencyList G = new AdjacencyList(n, true);
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < neighbors[i].length; j++) {
                double d = distances[i][j] - rho[i];
                double w = d <= 0 ? 1 : Math.exp(-d / sigma[i]);
                G.setWeight(i, neighbors[i][j], w);
            }
        }
        return G;
    }

    /**
     * Computes the random initialization.
     *
     * @param n The number of data points.
     * @param d The dimension of the embedding space.
     */
    private static double[][] randomLayout(int n, int d) {
        double[][] embedding = new double[n][d];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < d; j++) {
                embedding[i][j] = MathEx.random(-10, 10);
            }
        }
        return embedding;
    }

    /**
     * Computes the PCA initialization.
     *
     * @param data The input data.
     * @param d The dimension of the embedding space.
     */
    private static double[][] pcaLayout(double[][] data, int d) {
        return PCA.fit(data).getProjection(d).apply(data);
    }

    /**
     * Computes the spectral embedding of the graph, which is
     * the eigenvectors of the (normalized) Laplacian of the graph.
     *
     * @param nng The nearest neighbor graph.
     * @param d The dimension of the embedding space.
     */
    private static double[][] spectralLayout(NearestNeighborGraph nng, int d) {
        // Algorithm 4 Spectral embedding for initialization
        int[][] neighbors = nng.neighbors();
        double[][] distances = nng.distances();
        int n = nng.size();
        double[] D = new double[n];
        IntStream.range(0, n).parallel()
                .forEach(i -> D[i] = 1.0 / Math.sqrt(MathEx.sum(distances[i])));

        // Laplacian of graph.
        logger.info("Spectral layout computes Laplacian...");
        AdjacencyList laplacian = new AdjacencyList(n, false);
        for (int i = 0; i < n; i++) {
            laplacian.setWeight(i, i, 1.0);
            int[] v = neighbors[i];
            double[] dist = distances[i];
            for (int j = 0; j < v.length; j++) {
                double w = -D[i] * dist[j] * D[v[j]];
                laplacian.setWeight(i, v[j], w);
            }
        }

        // ARPACK may not find all needed eigenvalues for k = d + 1.
        // Hack it with heuristic min(2*k+1, sqrt(n)).
        int k = d + 1;
        int numEigen = Math.max(2*k+1, (int) Math.sqrt(n));
        SparseMatrix L = laplacian.toMatrix();
        logger.info("Spectral layout computes {} eigen vectors", numEigen);
        Matrix.EVD eigen = ARPACK.syev(L, ARPACK.SymmOption.SM, numEigen);

        Matrix V = eigen.Vr;
        double[][] coordinates = new double[n][d];
        for (int j = d; --j >= 0; ) {
            int c = V.ncol() - j - 2;
            for (int i = 0; i < n; i++) {
                coordinates[i][j] = V.get(i, c);
            }
        }

        return coordinates;
    }

    /**
     * Scale coordinates so that the largest coordinate is scale,
     * then add normal-distributed noise with standard deviation noise.
     * @param coordinates coordinates to scale.
     * @param scale max value after scaling.
     * @param noise the standard deviation of noise.
     */
    private static void noisyScale(double[][] coordinates, double scale, double noise) {
        int d = coordinates[0].length;
        double max = Double.NEGATIVE_INFINITY;
        for (double[] coordinate : coordinates) {
            for (int j = 0; j < d; j++) {
                max = Math.max(max, Math.abs(coordinate[j]));
            }
        }

        double expansion = scale / max;
        GaussianDistribution gaussian = new GaussianDistribution(0.0, noise);
        for (double[] coordinate : coordinates) {
            for (int j = 0; j < d; j++) {
                coordinate[j] = expansion * coordinate[j] + gaussian.rand();
            }
        }
    }

    /**  Normalize coordinates. */
    private static void normalize(double[][] coordinates, double scale) {
        int d = coordinates[0].length;
        double[] colMax = MathEx.colMax(coordinates);
        double[] colMin = MathEx.colMin(coordinates);
        double[] length = new double[d];
        for (int j = 0; j < d; j++) {
            length[j] = colMax[j] - colMin[j];
        }

        for (double[] coordinate : coordinates) {
            for (int j = 0; j < d; j++) {
                coordinate[j] = scale * (coordinate[j] - colMin[j]) / length[j];
            }
        }
    }

    /**
     * Improve an embedding using stochastic gradient descent to minimize the
     * fuzzy set cross entropy between the 1-skeletons of the high dimensional
     * and low dimensional fuzzy simplicial sets. In practice this is done by
     * sampling edges based on their membership strength (with the (1-p) terms
     * coming from negative sampling similar to word2vec).
     *
     * @param embedding          The embeddings to be optimized
     * @param curve              The curve parameters
     * @param epochsPerSample    The number of epochs per 1-simplex between
     *                           (ith, jth) data points. 1-simplices with weaker membership
     *                           strength will have more epochs between being sampled.
     * @param negativeSamples    The number of negative samples (with membership strength 0).
     * @param initialAlpha       The initial learning rate for the SGD
     * @param gamma              The weight of negative samples
     * @param epochs             The number of iterations.
     */
    private static void optimizeLayout(double[][] embedding, double[] curve, SparseMatrix epochsPerSample,
                                       int epochs, double initialAlpha, int negativeSamples, double gamma) {
        int n = embedding.length;
        int d = embedding[0].length;
        double a = curve[0];
        double b = curve[1];
        double alpha = initialAlpha;

        SparseMatrix epochsPerNegativeSample = epochsPerSample.copy();
        epochsPerNegativeSample.nonzeros().forEach(w -> w.update(w.x / negativeSamples));
        SparseMatrix epochNextNegativeSample = epochsPerNegativeSample.copy();
        SparseMatrix epochNextSample = epochsPerSample.copy();

        for (int iter = 1; iter <= epochs; iter++) {
            for (SparseMatrix.Entry edge : epochNextSample) {
                if (edge.x > 0 && edge.x <= iter) {
                    int j = edge.i;
                    int k = edge.j;
                    int index = edge.index;

                    double[] current = embedding[j];
                    double[] other = embedding[k];

                    double distSquared = MathEx.squaredDistance(current, other);
                    if (distSquared > 0.0) {
                        double gradCoeff = -2.0 * a * b * Math.pow(distSquared, b - 1.0);
                        gradCoeff /= a * Math.pow(distSquared, b) + 1.0;

                        for (int i = 0; i < d; i++) {
                            double gradD = clamp(gradCoeff * (current[i] - other[i]));
                            current[i] += gradD * alpha;
                            other[i]   -= gradD * alpha;
                        }
                    }

                    edge.update(edge.x + epochsPerSample.get(index));

                    // negative sampling
                    int negSamples = (int) ((iter - epochNextNegativeSample.get(index)) / epochsPerNegativeSample.get(index));

                    for (int p = 0; p < negSamples; p++) {
                        k = MathEx.randomInt(n);
                        if (j == k) continue;
                        other = embedding[k];
                        distSquared = MathEx.squaredDistance(current, other);

                        double gradCoeff = 0.0;
                        if (distSquared > 0.0) {
                            gradCoeff = 2.0 * gamma * b;
                            gradCoeff /= (0.001 + distSquared) * (a * Math.pow(distSquared, b) + 1);
                        }

                        for (int i = 0; i < d; i++) {
                            double gradD = 4.0;
                            if (gradCoeff > 0.0) {
                                gradD = clamp(gradCoeff * (current[i] - other[i]));
                            }
                            current[i] += gradD * alpha;
                        }
                    }

                    epochNextNegativeSample.set(index, epochNextNegativeSample.get(index) + epochsPerNegativeSample.get(index) * negSamples);
                }
            }

            logger.info("The learning rate at {} iterations: {}", iter, alpha);
            alpha = initialAlpha * (1.0 - (double) iter / epochs);
        }
    }

    /**
     * Computes the number of epochs per sample, one for each 1-simplex.
     *
     * @param strength The strength matrix.
     * @param epochs   The number of iterations.
     * @return An array of number of epochs per sample, one for each 1-simplex
     *         between (ith, jth) sample point.
     */
    private static SparseMatrix computeEpochPerSample(SparseMatrix strength, int epochs) {
        double max = strength.nonzeros().mapToDouble(w -> w.x).max().orElse(0.0);
        double min = max / epochs;
        strength.nonzeros().forEach(w -> {
            if (w.x < min) w.update(0.0);
            else w.update(max / w.x);
        });
        return strength;
    }

    /**
     * Clamps a value to range [-4.0, 4.0].
     */
    private static double clamp(double val) {
        return Math.min(4.0, Math.max(val, -4.0));
    }
}
