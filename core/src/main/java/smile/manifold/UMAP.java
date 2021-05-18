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
import java.util.Collection;
import java.util.stream.IntStream;
import smile.graph.AdjacencyList;
import smile.graph.Graph.Edge;
import smile.math.DifferentiableMultivariateFunction;
import smile.math.LevenbergMarquardt;
import smile.math.MathEx;
import smile.math.distance.Distance;
import smile.math.distance.EuclideanDistance;
import smile.math.matrix.ARPACK;
import smile.math.matrix.Matrix;
import smile.math.matrix.SparseMatrix;
import smile.stat.distribution.GaussianDistribution;

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
 * <li>How UMAP Works: https://umap-learn.readthedocs.io/en/latest/how_umap_works.html</li>
 * </ol>
 *
 * @see TSNE
 *
 * @author rayeaster
 */
public class UMAP implements Serializable {
    private static final long serialVersionUID = 2L;
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(UMAP.class);

    /**
     * The coordinate matrix in embedding space.
     */
    public final double[][] coordinates;
    /**
     * The original sample index.
     */
    public final int[] index;
    /**
     * The nearest neighbor graph.
     */
    public final AdjacencyList graph;

    /**
     * Constructor.
     * @param index the original sample index.
     * @param coordinates the coordinates.
     * @param graph the nearest neighbor graph.
     */
    public UMAP(int[] index, double[][] coordinates, AdjacencyList graph) {
        this.index = index;
        this.coordinates = coordinates;
        this.graph = graph;
    }

    /**
     * Runs the UMAP algorithm.
     *
     * @param data the input data.
     * @return the model.
     */
    public static UMAP of(double[][] data) {
        return of(data, 15);
    }

    /**
     * Runs the UMAP algorithm.
     *
     * @param data     the input data.
     * @param distance the distance function.
     * @param <T> the data type of points.
     * @return the model.
     */
    public static <T> UMAP of(T[] data, Distance<T> distance) {
        return of(data, distance, 15);
    }

    /**
     * Runs the UMAP algorithm.
     *
     * @param data    the input data.
     * @param k       k-nearest neighbors. Larger values result in more global views
     *                of the manifold, while smaller values result in more local data
     *                being preserved. Generally in the range 2 to 100.
     * @return the model.
     */
    public static UMAP of(double[][] data, int k) {
        return of(data, new EuclideanDistance(), k);
    }

    /**
     * Runs the UMAP algorithm.
     *
     * @param data    the input data.
     * @param distance the distance function.
     * @param k       k-nearest neighbor. Larger values result in more global views
     *                of the manifold, while smaller values result in more local data
     *                being preserved. Generally in the range 2 to 100.
     * @param <T> the data type of points.
     * @return the model.
     */
    public static <T> UMAP of(T[] data, Distance<T> distance, int k) {
        return of(data, distance, k, 2, data.length > 10000 ? 200 : 500, 1.0, 0.1, 1.0, 5, 1.0);
    }

    /**
     * Runs the UMAP algorithm.
     *
     * @param data               the input data.
     * @param k                  k-nearest neighbors. Larger values result in more global views
     *                           of the manifold, while smaller values result in more local data
     *                           being preserved. Generally in the range 2 to 100.
     * @param d                  The target embedding dimensions. defaults to 2 to provide easy
     *                           visualization, but can reasonably be set to any integer value
     *                           in the range 2 to 100.
     * @param iterations         The number of iterations to optimize the
     *                           low-dimensional representation. Larger values result in more
     *                           accurate embedding. Muse be at least 10. Choose wise value
     *                           based on the size of the input data, e.g, 200 for large
     *                           data (10000+ samples), 500 for small.
     * @param learningRate       The initial learning rate for the embedding optimization,
     *                           default 1.
     * @param minDist            The desired separation between close points in the embedding
     *                           space. Smaller values will result in a more clustered/clumped
     *                           embedding where nearby points on the manifold are drawn closer
     *                           together, while larger values will result on a more even
     *                           disperse of points. The value should be set no-greater than
     *                           and relative to the spread value, which determines the scale
     *                           at which embedded points will be spread out. default 0.1.
     * @param spread             The effective scale of embedded points. In combination with
     *                           minDist, this determines how clustered/clumped the embedded
     *                           points are. default 1.0.
     * @param negativeSamples    The number of negative samples to select per positive sample
     *                           in the optimization process. Increasing this value will result
     *                           in greater repulsive force being applied, greater optimization
     *                           cost, but slightly more accuracy, default 5.
     * @param repulsionStrength  Weighting applied to negative samples in low dimensional
     *                           embedding optimization. Values higher than one will result in
     *                           greater weight being given to negative samples, default 1.0.
     * @return the model.
     */
    public static UMAP of(double[][] data, int k, int d, int iterations, double learningRate, double minDist, double spread, int negativeSamples, double repulsionStrength) {
        return of(data, new EuclideanDistance(), k, d, iterations, learningRate, minDist, spread, negativeSamples, repulsionStrength);
    }

    /**
     * Runs the UMAP algorithm.
     *
     * @param data               the input data.
     * @param distance           the distance function.
     * @param k                  k-nearest neighbor. Larger values result in more global views
     *                           of the manifold, while smaller values result in more local data
     *                           being preserved. Generally in the range 2 to 100.
     * @param d                  The target embedding dimensions. defaults to 2 to provide easy
     *                           visualization, but can reasonably be set to any integer value
     *                           in the range 2 to 100.
     * @param iterations         The number of iterations to optimize the
     *                           low-dimensional representation. Larger values result in more
     *                           accurate embedding. Muse be at least 10. Choose wise value
     *                           based on the size of the input data, e.g, 200 for large
     *                           data (1000+ samples), 500 for small.
     * @param learningRate       The initial learning rate for the embedding optimization,
     *                           default 1.
     * @param minDist            The desired separation between close points in the embedding
     *                           space. Smaller values will result in a more clustered/clumped
     *                           embedding where nearby points on the manifold are drawn closer
     *                           together, while larger values will result on a more even
     *                           disperse of points. The value should be set no-greater than
     *                           and relative to the spread value, which determines the scale
     *                           at which embedded points will be spread out. default 0.1.
     * @param spread             The effective scale of embedded points. In combination with
     *                           minDist, this determines how clustered/clumped the embedded
     *                           points are. default 1.0.
     * @param negativeSamples    The number of negative samples to select per positive sample
     *                           in the optimization process. Increasing this value will result
     *                           in greater repulsive force being applied, greater optimization
     *                           cost, but slightly more accuracy, default 5.
     * @param repulsionStrength  Weighting applied to negative samples in low dimensional
     *                           embedding optimization. Values higher than one will result in
     *                           greater weight being given to negative samples, default 1.0.
     * @param <T> the data type of points.
     * @return the model.
     */
    public static <T> UMAP of(T[] data, Distance<T> distance, int k, int d, int iterations, double learningRate, double minDist, double spread, int negativeSamples, double repulsionStrength) {
        if (d < 2) {
            throw new IllegalArgumentException("d must be greater than 1: " + d);
        }
        if (k < 2) {
            throw new IllegalArgumentException("k must be greater than 1: " + k);
        }
        if (minDist <= 0) {
            throw new IllegalArgumentException("minDist must greater than 0: " + minDist);
        }
        if (minDist > spread) {
            throw new IllegalArgumentException("minDist must be less than or equal to spread: " + minDist + ",spread=" + spread);
        }
        if (iterations < 10) {
            throw new IllegalArgumentException("epochs must be a positive integer of at least 10: " + iterations);
        }
        if (learningRate <= 0) {
            throw new IllegalArgumentException("learningRate must greater than 0: " + learningRate);
        }
        if (negativeSamples <= 0) {
            throw new IllegalArgumentException("negativeSamples must greater than 0: " + negativeSamples);
        }

        // Construct the local fuzzy simplicial set by locally approximating
        // geodesic distance at each point, and then combining all the local
        // fuzzy simplicial sets into a global one via a fuzzy union.
        AdjacencyList graph = NearestNeighborGraph.of(data, distance, k, true,null);
        NearestNeighborGraph nng = NearestNeighborGraph.largest(graph);

        graph = computeFuzzySimplicialSet(nng.graph, k, 64);
        SparseMatrix conorm = graph.toMatrix();

        // Spectral embedding initialization
        double[][] coordinates = spectralLayout(graph, d);
        logger.info("Finish initialization with spectral layout");

        // parameters for the differentiable curve used in lower
        // dimensional fuzzy simplicial complex construction.
        double[] curve = fitCurve(spread, minDist);
        logger.info("Finish fitting the curve parameters");

        // Optimizing the embedding
        SparseMatrix epochs = computeEpochPerSample(conorm, iterations);
        logger.info("Start optimizing the layout");
        optimizeLayout(coordinates, curve, epochs, iterations, learningRate, negativeSamples, repulsionStrength);
        return new UMAP(nng.index, coordinates, graph);
    }

    /**
     * The curve function:
     * <p>
     * <pre>
     * 1.0 / (1.0 + a * x ^ (2 * b))
     * </pre>
     */
    private static final DifferentiableMultivariateFunction func = new DifferentiableMultivariateFunction() {

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
    };

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
        for (int i = 0; i < x.length; i++) {
            x[i] = (i + 1) * interval;
            y[i] = x[i] < minDist ? 1 : Math.exp(-(x[i] - minDist) / spread);
        }
        double[] p = {0.5, 0.0};
        LevenbergMarquardt curveFit = LevenbergMarquardt.fit(func, x, y, p);
        return curveFit.parameters;
    }

    /**
     * Computes the fuzzy simplicial set as a fuzzy graph with the
     * probabilistic t-conorm. This is done by locally approximating
     * geodesic distance at each point, creating a fuzzy simplicial
     * set for each such point, and then combining all the local
     * fuzzy simplicial sets into a global one via a fuzzy union.
     *
     * @param nng        The nearest neighbor graph.
     * @param k          k-nearest neighbor.
     * @param iterations The max number of iterations of the binary search
     *                   for the correct distance value. default 64
     * @return A fuzzy simplicial set represented as a sparse matrix. The (i, j)
     * entry of the matrix represents the membership strength of the
     * 1-simplex between the ith and jth sample points.
     */
    private static AdjacencyList computeFuzzySimplicialSet(AdjacencyList nng, int k, int iterations) {
        // Algorithm 2 Constructing a local fuzzy simplicial set
        final double LogK = MathEx.log2(k);
        final double EPSILON = 1E-8;
        final double TOLERANCE = 1E-5;
        final double MIN_SCALE = 1E-3;

        int n = nng.getNumVertices();
        // The smooth approximator to knn-distance
        double[] sigma = new double[n];
        // The distance to nearest neighbor
        double[] rho = new double[n];

        double avg = IntStream.range(0, n).mapToObj(nng::getEdges)
                .flatMapToDouble(edges -> edges.stream().mapToDouble(edge -> edge.weight))
                .filter(w -> !MathEx.isZero(w, EPSILON))
                .average().orElse(0.0);

        for (int i = 0; i < n; i++) {
            double lo = 0.0;
            double hi = Double.POSITIVE_INFINITY;
            double mid = 1.0;

            Collection<Edge> knn = nng.getEdges(i);
            rho[i] = knn.stream()
                    .mapToDouble(edge -> edge.weight)
                    .filter(w -> !MathEx.isZero(w, EPSILON))
                    .min().orElse(0.0);

            // Algorithm 3 Compute the normalizing factor for distances
            // function SmoothKNNDist() by binary search
            for (int iter = 0; iter < iterations; iter++) {
                double psum = 0.0;
                for (Edge edge : knn) {
                    if (!MathEx.isZero(edge.weight, EPSILON)) {
                        double d = edge.weight - rho[i];
                        psum += d > 0.0 ? Math.exp(-d / mid) : 1.0;
                    }
                }

                if (Math.abs(psum - LogK) < TOLERANCE) {
                    break;
                }
                // Given that it is a parameterized function
                // and the whole thing is monotonic
                // a simply binary search is actually quite efficient.
                if (psum > LogK) {
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

            sigma[i] = mid;

            if (rho[i] > 0.0) {
                double avgi = knn.stream()
                        .mapToDouble(edge -> edge.weight)
                        .filter(w -> !MathEx.isZero(w, EPSILON))
                        .average().orElse(0.0);
                sigma[i] = Math.max(sigma[i], MIN_SCALE * avgi);
            } else {
                sigma[i] = Math.max(sigma[i], MIN_SCALE * avg);
            }
        }

        // Computes a continuous version of the distance to the kth nearest neighbor.
        // That is, this is similar to knn-distance but allows continuous k values
        // rather than requiring an integral k. In essence we are simply computing
        // the distance such that the cardinality of fuzzy set we generate is k.
        for (int i = 0; i < n; i++) {
            for (Edge edge : nng.getEdges(i)) {
                edge.weight = Math.exp(-Math.max(0.0, (edge.weight - rho[i])) / sigma[i]);
            }
        }

        // probabilistic t-conorm: (a + a' - a .* a')
        AdjacencyList G = new AdjacencyList(n, false);
        for (int i = 0; i < n; i++) {
            for (Edge edge : nng.getEdges(i)) {
                double w = edge.weight;
                double w2 = nng.getWeight(edge.v2, edge.v1); // weight of reverse arc.
                G.setWeight(edge.v1, edge.v2, w + w2 - w * w2);
            }
        }

        return G;
    }

    /**
     * Computes the spectral embedding of the graph, which is
     * the eigenvectors of the (normalized) Laplacian of the graph.
     *
     * @param nng The nearest neighbor graph.
     * @param d The dimension of the embedding space.
     */
    private static double[][] spectralLayout(AdjacencyList nng, int d) {
        // Algorithm 4 Spectral embedding for initialization
        int n = nng.getNumVertices();
        double[] D = new double[n];
        for (int i = 0; i < n; i++) {
            for (Edge edge : nng.getEdges(i)) {
                D[i] += edge.weight;
            }

            D[i] = 1.0 / Math.sqrt(D[i]);
        }

        // Laplacian of graph.
        AdjacencyList laplacian = new AdjacencyList(n, false);
        for (int i = 0; i < n; i++) {
            laplacian.setWeight(i, i, 1.0);
            for (Edge edge : nng.getEdges(i)) {
                double w = -D[edge.v1] * edge.weight * D[edge.v2];
                laplacian.setWeight(edge.v1, edge.v2, w);
            }
        }

        SparseMatrix L = laplacian.toMatrix();

        // ARPACK may not find all needed eigenvalues for k = d + 1.
        // Hack it with 10 * (d + 1).
        Matrix.EVD eigen = ARPACK.syev(L, ARPACK.SymmOption.SM, Math.min(10*(d+1), n-1));

        double absMax = 0;
        Matrix V = eigen.Vr;
        double[][] coordinates = new double[n][d];
        for (int j = d; --j >= 0; ) {
            int c = V.ncol() - j - 2;
            for (int i = 0; i < n; i++) {
                double x = V.get(i, c);
                coordinates[i][j] = x;

                double abs = Math.abs(x);
                if (abs > absMax) {
                    absMax = abs;
                }
            }
        }

        // We add a little noise to avoid local minima for optimization to come
        double expansion = 10.0 / absMax;
        GaussianDistribution gaussian = new GaussianDistribution(0.0, 0.0001);
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < d; j++) {
                coordinates[i][j] = coordinates[i][j] * expansion + gaussian.rand();
            }
        }

        // normalization
        double[] colMax = MathEx.colMax(coordinates);
        double[] colMin = MathEx.colMin(coordinates);
        double[] de = new double[d];
        for (int j = 0; j < d; j++) {
            de[j] = (colMax[j] - colMin[j]);
        }

        for (int i = 0; i < n; i++) {
            for (int j = 0; j < d; j++) {
                coordinates[i][j] = 10 * (coordinates[i][j] - colMin[j]) / de[j];
            }
        }

        return coordinates;
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
     * @param iterations         The number of iterations.
     */
    private static void optimizeLayout(double[][] embedding, double[] curve, SparseMatrix epochsPerSample,
                                       int iterations, double initialAlpha, int negativeSamples, double gamma) {
        int n = embedding.length;
        int d = embedding[0].length;
        double a = curve[0];
        double b = curve[1];
        double alpha = initialAlpha;

        SparseMatrix epochsPerNegativeSample = epochsPerSample.clone();
        epochsPerNegativeSample.nonzeros().forEach(w -> w.update(w.x / negativeSamples));
        SparseMatrix epochNextNegativeSample = epochsPerNegativeSample.clone();
        SparseMatrix epochNextSample = epochsPerSample.clone();

        for (int iter = 1; iter <= iterations; iter++) {
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

            logger.info(String.format("The learning rate at %3d iterations: %.5f", iter, alpha));
            alpha = initialAlpha * (1.0 - (double) iter / iterations);
        }
    }

    /**
     * Computes the number of epochs per sample, one for each 1-simplex.
     *
     * @param strength   The strength matrix.
     * @param iterations The number of iterations.
     * @return An array of number of epochs per sample, one for each 1-simplex
     * between (ith, jth) sample point.
     */
    private static SparseMatrix computeEpochPerSample(SparseMatrix strength, int iterations) {
        double max = strength.nonzeros().mapToDouble(w -> w.x).max().orElse(0.0);
        double min = max / iterations;
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