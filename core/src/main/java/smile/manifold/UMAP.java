/*******************************************************************************
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
 ******************************************************************************/

package smile.manifold;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.IntStream;

import smile.graph.Graph;
import smile.graph.Graph.Edge;
import smile.math.DifferentiableMultivariateFunction;
import smile.math.LevenbergMarquardt;
import smile.math.MathEx;
import smile.math.distance.EuclideanDistance;
import smile.math.matrix.DenseMatrix;
import smile.math.matrix.EVD;
import smile.math.matrix.SparseMatrix;
import smile.netlib.ARPACK;
import smile.stat.distribution.GaussianDistribution;

/**
 * Uniform Manifold Approximation and Projection.
 * <p>
 * 
 * Uniform Manifold Approximation and Projection (UMAP) is a dimension reduction
 * technique that can be used for visualization similarly to t-SNE, but also for
 * general non-linear dimension reduction. The algorithm is founded on three
 * assumptions about the data:
 * 
 * <ol>
 * <li>The data is uniformly distributed on a Riemannian manifold;</li>
 * <li>The Riemannian metric is locally constant (or can be approximated as
 * such);</li>
 * <li>The manifold is locally connected.</li>
 * </ol>
 * 
 * From these assumptions it is possible to model the manifold with a fuzzy
 * topological structure. The embedding is found by searching for a low
 * dimensional projection of the data that has the closest possible equivalent
 * fuzzy topological structure.
 * 
 * <h2>References</h2>
 * <ol>
 * <li>McInnes, L, Healy, J, UMAP: Uniform Manifold Approximation and Projection for Dimension Reduction, ArXiv e-prints 1802.03426, 2018</li>
 * <li>How UMAP Works: https://umap-learn.readthedocs.io/en/latest/how_umap_works.html</li>
 * </ol>
 * 
 * @author rayeaster
 *
 */
public class UMAP {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(UMAP.class);

    /**
     * Output embedding of size (dataSize, embeddingDimension).
     */
    public double[][] coordinates;

    /**
     * absolute tolerance used to find correct smooth approximator in
     * knn-distance, see {@link UMAP#smoothKnnDist}
     */
    private static final double SMOOTH_K_TOLERANCE = 1e-5;

    /**
     * minimum scale to smooth too small knn-distance, see
     * {@link UMAP#smoothKnnDist}
     */
    private static final double MIN_K_DIST_SCALE = 1e-3;
    
    /**
     * for small datasize, we use more optimization epochs
     */
    private static final int OPTI_EPOCH_DATA_SIZE = 1000;
    
    /**
     * optimization epochs for small datasize
     */
    private static final int EPOCHS_SMALL_DSIZE = 500;
    
    /**
     * optimization epochs for bigger datasize
     */
    private static final int EPOCHS_BIG_DSIZE = 200;

    /**
     * Constructor.
     *
     * @param data
     *            input data to train the UMAP.
     */
    public UMAP(double[][] data) {

        this(data, 3, // knn neighbor numbers
                2, // output embedding dimension
                0.1, // min distance in embedding space
                1, // effective scale of embedded points
                data.length > OPTI_EPOCH_DATA_SIZE ? EPOCHS_BIG_DSIZE : EPOCHS_SMALL_DSIZE, // optimization epoch
                1, // weighting applied to negative samples in optimization
                1, // initial learning rate for the embedding optimization
                5 // number of negative samples
        );
    }
    
    /**
     * Constructor.
     *
     * @param data
     *            input data to train the UMAP.
     * @param n
     *            The number of neighbors to consider when approximating the
     *            local metric. Larger values result in more global views of the
     *            manifold, while smaller values result in more local data being
     *            preserved. In general values should be in the range 2 to 100
     * @param minDist
     *            The desired separation between close points in the embedding
     *            space. Smaller values will result in a more clustered/clumped
     *            embedding where nearby points on the manifold are drawn closer
     *            together, while larger values will result on a more even
     *            disperse of points. The value should be set no-greater than
     *            and relative to the spread value, which determines the scale
     *            at which embedded points will be spread out. default 0.1
     */
    public UMAP(double[][] data, int n, double minDist) {

        this(data, n, // knn neighbor numbers
                2, // output embedding dimension
                minDist, // min distance in embedding space
                1, // effective scale of embedded points
                data.length > OPTI_EPOCH_DATA_SIZE ? EPOCHS_BIG_DSIZE : EPOCHS_SMALL_DSIZE, // optimization epoch
                1, // weighting applied to negative samples in optimization
                1, // initial learning rate for the embedding optimization
                5 // number of negative samples
        );
    }

    /**
     * Constructor.
     *
     * @param data
     *            input data to train the UMAP.
     * @param n
     *            The number of neighbors to consider when approximating the
     *            local metric. Larger values result in more global views of the
     *            manifold, while smaller values result in more local data being
     *            preserved. In general values should be in the range 2 to 100
     * @param d
     *            The target embedding dimensions. defaults to 2 to provide easy
     *            visualization, but can reasonably be set to any integer value
     *            in the range 2 to 100
     * @param minDist
     *            The desired separation between close points in the embedding
     *            space. Smaller values will result in a more clustered/clumped
     *            embedding where nearby points on the manifold are drawn closer
     *            together, while larger values will result on a more even
     *            disperse of points. The value should be set no-greater than
     *            and relative to the spread value, which determines the scale
     *            at which embedded points will be spread out. default 0.1
     * @param spread
     *            The effective scale of embedded points. In combination with
     *            minDist, this determines how clustered/clumped the embedded
     *            points are. default 1.0
     * @param nEpochs
     *            The number of training epochs to use when optimizing the
     *            low-dimensional representation. Larger values result in more
     *            accurate embedding. require greater than 10, choose wise value
     *            based on the size of the input dataset, e.g, 200 for large
     *            datasets, 500 for small.
     * @param repulsionStrength
     *            Weighting applied to negative samples in low dimensional
     *            embedding optimization. Values higher than one will result in
     *            greater weight being given to negative samples, default 1.0
     * @param learningRate
     *            The initial learning rate for the embedding optimization,
     *            default 1.
     * @param negativeSampleRate
     *            The number of negative samples to select per positive sample
     *            in the optimization process. Increasing this value will result
     *            in greater repulsive force being applied, greater optimization
     *            cost, but slightly more accuracy, default 5
     */
    public UMAP(double[][] data, int n, int d, double minDist, double spread, int nEpochs, double repulsionStrength,
            double learningRate, double negativeSampleRate) {

        if (d < 1) {
            throw new IllegalArgumentException("d must be greater than 0: " + d);
        }
        if (n < 2) {
            throw new IllegalArgumentException("n must be greater than 1: " + n);
        }
        if (minDist < 0) {
            throw new IllegalArgumentException("minDist must greater than 0: " + minDist);
        }
        if (minDist > spread) {
            throw new IllegalArgumentException(
                    "minDist must be less than or equal to spread: " + minDist + ",spread=" + spread);
        }
        if (nEpochs < 10) {
            throw new IllegalArgumentException("nEpochs must be a positive integer of at least 10: " + nEpochs);
        }
        if (learningRate < 0) {
            throw new IllegalArgumentException("learningRate must greater than 0: " + learningRate);
        }
        if (negativeSampleRate < 0) {
            throw new IllegalArgumentException("negativeSampleRate must greater than 0: " + negativeSampleRate);
        }

        // parameters for the differentiable curve used in lower
        // dimensional fuzzy simplicial complex construction.
        double[] ab = findABParams(spread, minDist);

        // Constructing a local fuzzy simplicial set
        Graph knns = NearestNeighborGraph.of(data, n, null);
        double[] sigmas = new double[data.length];
        double[] rhos = new double[data.length];
        SparseMatrix coo = fuzzySimplicialSet(knns, n, sigmas, rhos);

        // Spectral embedding initialization
        coordinates = spectralLayout(coo, d);

        // Optimizing the embedding
        Map<Integer, Map<Integer, Double>> epochsPerSample = makeEpochsPerSample(coo, nEpochs);
        optimizeLayout(coordinates, ab, epochsPerSample, negativeSampleRate, learningRate, repulsionStrength, nEpochs);
    }

    /**
     * general curve to be fit:
     * 
     * <pre>
     * 1.0 / (1.0 + a * x ^ (2 * b))
     * </pre>
     */
    private static DifferentiableMultivariateFunction func = new DifferentiableMultivariateFunction() {

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
     * Fit a, b params for the differentiable curve used in lower dimensional
     * fuzzy simplicial complex construction. We want the smooth curve (from a
     * pre-defined family with simple gradient) that best matches an offset
     * exponential decay.
     * 
     * @param spread
     *            The effective scale of embedded points. In combination with
     *            minDist, this determines how clustered/clumped the embedded
     *            points are. default 1.0
     * @param minDist
     *            The desired separation between close points in the embedding
     *            space. The value should be set no-greater than and relative to
     *            the spread value, which determines the scale at which embedded
     *            points will be spread out, default 0.1
     * @return a, b params for the differentiable curve
     */
    private static double[] findABParams(double spread, double minDist) {
        int size = 300;
        double[] x = new double[size];
        double[] y = new double[size];
        double xEnd = 3 * spread;
        double xInterval = xEnd / (size - 1);
        for (int i = 0; i < x.length; i++) {
            x[i] = (i + 1) * xInterval;
            y[i] = x[i] < minDist ? 1 : Math.exp(-(x[i] - minDist) / spread);
        }
        double[] p = {0.5, 0.0};
        LevenbergMarquardt curveFit = LevenbergMarquardt.fit(func, x, y, p);
        return curveFit.p;
    }

    /**
     * Given a set of nearest neighbors and a neighborhood size, the fuzzy
     * simplicial set (here represented as a fuzzy graph in the form of a sparse
     * matrix) associated to the data. This is done by locally approximating
     * geodesic distance at each point, creating a fuzzy simplicial set for each
     * such point, and then combining all the local fuzzy simplicial sets into a
     * global one via a fuzzy union.
     * 
     * @param knns
     *            n nearest points for each data point
     * @param n
     *            The number of neighbors to consider when approximating the
     *            neighbors.
     * @param sigmas
     *            Smooth approximator to knn-distance
     * @param rhos
     *            Distance to nearest neighbor
     * @return A fuzzy simplicial set represented as a sparse matrix. The (i, j)
     *         entry of the matrix represents the membership strength of the
     *         1-simplex between the ith and jth sample points.
     */
    private static SparseMatrix fuzzySimplicialSet(Graph knns, int n, double[] sigmas, double[] rhos) {
        smoothKnnDist(knns, n, sigmas, rhos, 64);
        SparseMatrix ret = computeMembershipStrengths(knns, sigmas, rhos);
        return ret;
    }

    /**
     * Compute a continuous version of the distance to the kth nearest neighbor.
     * That is, this is similar to knn-distance but allows continuous k values
     * rather than requiring an integral k. In essence we are simply computing
     * the distance such that the cardinality of fuzzy set we generate is k.
     * 
     * @param knns
     *            n nearest points for each data point
     * @param n
     *            The number of neighbors to consider when approximating the
     *            neighbors.
     * @param sigmas
     *            Smooth approximator to knn-distance
     * @param rhos
     *            Distance to nearest neighbor
     * @param iterations
     *            We need to binary search for the correct distance value. This
     *            is the max number of iterations to use in such a search.
     *            default 64
     */
    private static void smoothKnnDist(Graph knns, int n, double[] sigmas, double[] rhos, int iterations) {

        double target = MathEx.log2(n);
        Arrays.setAll(sigmas, sigmai -> 0.0);
        Arrays.setAll(rhos, rhoi -> 0.0);

        double meanDistance = knns.getEdges().parallelStream()
                .mapToDouble(knn -> (knn != null && knn.weight > 0 ? knn.weight : 0)).average().orElse(0);

        for (int i = 0; i < knns.getNumVertices(); i++) {
            double lo = 0.0;
            double hi = Double.MAX_VALUE;
            double mid = 1.0;

            Collection<Edge> knn = knns.getEdges(i);
            rhos[i] = knn.parallelStream()
                    .mapToDouble(neighbor -> (neighbor != null && neighbor.weight > 0 ? neighbor.weight : 0)).min()
                    .orElse(0);

            for (int iter = 0; iter < iterations; iter++) {
                double psum = 0.0;
                for (Edge edge : knn) {
                    double neighborDistance = edge.weight;
                    if (neighborDistance < 0) {
                        continue;
                    }
                    double d = neighborDistance - rhos[i];
                    if (d > 0) {
                        psum += Math.exp(-(d / mid));
                    } else {
                        psum += 1.0;
                    }
                }

                if (Math.abs(psum - target) < SMOOTH_K_TOLERANCE) {
                    break;
                }
                // Given that it is a parameterized function
                // and the whole thing is monotonic
                // a simply binary search is actually quite efficient.
                if (psum > target) {
                    hi = mid;
                    mid = (lo + hi) / 2.0;
                } else {
                    lo = mid;
                    if (hi == Double.MAX_VALUE) {
                        mid *= 2;
                    } else {
                        mid = (lo + hi) / 2.0;
                    }
                }
            }

            sigmas[i] = mid;

            if (rhos[i] > 0.0) {
                double mean_ith_distances = knn.parallelStream()
                        .mapToDouble(neighbor -> (neighbor != null && neighbor.weight > 0 ? neighbor.weight : 0))
                        .average().orElse(0);
                sigmas[i] = Math.max(sigmas[i], MIN_K_DIST_SCALE * mean_ith_distances);
            } else {
                sigmas[i] = Math.max(sigmas[i], MIN_K_DIST_SCALE * meanDistance);
            }
        } // end for iteration over every data point:knns
    }

    /**
     * Construct the membership strength data for the 1-skeleton of each local
     * fuzzy simplicial set. This is formed as a sparse matrix where each row is
     * a local fuzzy simplicial set, with a membership strength for the
     * 1-simplex to each other data point.
     * 
     * @param knns
     *            n nearest points for each data point
     * @param sigmas
     *            Smooth approximator to knn-distance from {@link UMAP#smoothKnnDist}
     * @param rhos
     *            Distance to nearest neighbor from {@link UMAP#smoothKnnDist}
     * @return A fuzzy simplicial set represented as a sparse matrix. The (i, j)
     *         entry of the matrix represents the membership strength of the
     *         1-simplex between the ith and jth sample points.
     */
    private static SparseMatrix computeMembershipStrengths(Graph knns, double[] sigmas, double[] rhos) {

        int size = knns.getNumVertices();

        // construct original coo-format sparse matrix from knns
        Map<Integer, Map<Integer, Double>> cooColumns = new HashMap<>();
        IntStream.range(0, size).forEach(n -> cooColumns.put(n, new HashMap<Integer, Double>()));
        int nonZeros = 0;
        for (int i = 0; i < size; i++) {
            Collection<Edge> neighbors = knns.getEdges(i);
            if (neighbors == null || neighbors.size() <= 0) {
                continue;
            }
            for (Edge edge : neighbors) {
                if (edge == null) {
                    continue;
                }
                double neighborDistance = edge.weight;
                if (neighborDistance < 0) {
                    continue;
                }
                double val = -1;
                if (edge.v2 == i) {
                    val = 0.0;
                } else if (neighborDistance <= rhos[i] || sigmas[i] == 0.0) {
                    val = 1.0;
                } else {
                    val = Math.exp(-(neighborDistance - rhos[i]) / (sigmas[i]));
                }

                if (val > 0) {
                    cooColumns.get(edge.v1).put(edge.v2, val);
                    nonZeros++;
                }
            }
        }
        SparseMatrix coo = convert2SpareMatrix(cooColumns, size, nonZeros);

        // we need to re-construct the sparse matrix using probabilistic
        // t-conorm: (a + b - a * b)
        SparseMatrix transpose = coo.transpose();
        SparseMatrix product = coo.aat();
        Map<Integer, Map<Integer, Double>> tCoNormCooColumns = new HashMap<>();
        IntStream.range(0, size).forEach(n -> tCoNormCooColumns.put(n, new HashMap<Integer, Double>()));
        int tCoNormNonZeros = 0;
        for (int colI = 0; colI < size; colI++) {
            for (int rowI = 0; rowI < size; rowI++) {
                double newV = coo.get(rowI, colI) + transpose.get(rowI, colI) - product.get(rowI, colI);
                if (Math.abs(newV) > 0) {
                    tCoNormCooColumns.get(colI).put(rowI, newV);
                    tCoNormNonZeros++;
                }
            }
        }
        SparseMatrix tCoNorm = convert2SpareMatrix(tCoNormCooColumns, size, tCoNormNonZeros);

        return tCoNorm;
    }

    /**
     * convert the given coo-format data to {@link SparseMatrix}
     * 
     * @param cooColumns
     *            data column by column stored in hashmap: col -> (row -> data)
     * @param dataSize
     *            returned matrix has size of (dataSize, dataSize)
     * @param nonZeros
     *            non-zero value counts
     * @return {@link SparseMatrix} from given coo-format data (column by column)
     */
    private static SparseMatrix convert2SpareMatrix(Map<Integer, Map<Integer, Double>> cooColumns, int dataSize,
            int nonZeros) {

        double[] x = new double[nonZeros];
        int[] rows = new int[nonZeros];
        int[] cols = new int[dataSize + 1];

        int colDataIdx = 0;
        for (int i = 0; i < dataSize; i++) {
            Map<Integer, Double> colData = cooColumns.get(i);
            if (colData.size() <= 0) {
                cols[i] = colDataIdx;// start at same as next non-zero column
                continue;
            }
            cols[i] = colDataIdx;
            for (Entry<Integer, Double> entry : colData.entrySet()) {
                rows[colDataIdx] = entry.getKey();
                x[colDataIdx] = entry.getValue();
                colDataIdx++;
            }
        }
        cols[cols.length - 1] = colDataIdx;

        assert (nonZeros == colDataIdx);

        SparseMatrix coo = new SparseMatrix(dataSize, dataSize, x, rows, cols);
        coo.setSymmetric(true);
        return coo;
    }

    /**
     * Given a graph compute the spectral embedding of the graph. This is simply
     * the eigenvectors of the laplacian of the graph. Here we use the
     * normalized laplacian.
     * 
     * @param topRep
     *            The (weighted) adjacency matrix of the graph as a sparse
     *            matrix.
     * @param dim
     *            The dimension of the space into which to embed.
     * @return The spectral embedding of the graph, of shape (n_vertices, dim)
     */
    private double[][] spectralLayout(SparseMatrix topRep, int dim) {
        int size = topRep.ncols();
        Map<Integer, Map<Integer, Double>> cooDiag = new HashMap<>(size);
        IntStream.range(0, size).forEach(n -> cooDiag.put(n, new HashMap<Integer, Double>()));

        Map<Integer, Map<Integer, Double>> cooIdentity = new HashMap<>(size);
        IntStream.range(0, size).forEach(n -> cooIdentity.put(n, new HashMap<Integer, Double>()));

        for (int colI = 0; colI < size; colI++) {
            double colSum = 0;
            for (int rowI = 0; rowI < topRep.nrows(); rowI++) {
                colSum += topRep.get(rowI, colI);
            }
            cooDiag.get(colI).put(colI, 1 / Math.sqrt(colSum));
            cooIdentity.get(colI).put(colI, 1.0);
        }

        SparseMatrix diag = convert2SpareMatrix(cooDiag, size, size);
        SparseMatrix identity = convert2SpareMatrix(cooIdentity, size, size);
        SparseMatrix diagG = diag.abmm(topRep);
        SparseMatrix diagGDiag = diagG.abmm(diag);

        // construct the laplacian sparse matrix of (I - D * graph * D)
        Map<Integer, Map<Integer, Double>> normLaplacian = new HashMap<>(size);
        IntStream.range(0, size).forEach(n -> normLaplacian.put(n, new HashMap<Integer, Double>()));
        int normNonZeros = 0;
        for (int colI = 0; colI < size; colI++) {
            for (int rowI = 0; rowI < size; rowI++) {
                double newV = identity.get(rowI, colI) - diagGDiag.get(rowI, colI);
                if (Math.abs(newV) > 0) {
                    normLaplacian.get(colI).put(rowI, newV);
                    normNonZeros++;
                }
            }
        }
        SparseMatrix embedding = convert2SpareMatrix(normLaplacian, size, normNonZeros);

        // calculate the first k eigen vectors for spectral manifold
        int k = dim + 1;
        EVD eigen = ARPACK.eigen(embedding, k, "SM");
        DenseMatrix V = eigen.getEigenVectors();

        // get the [1st, 2nd, 3rd, 4th, ..., (dim + 1)th] 
        // eigenvectors ascendingly from above V
        double absMax = 0;
        double[][] coordinates = new double[size][dim];
        for (int colI = 0; colI < dim; colI++) {
            for (int rowI = 0; rowI < size; rowI++) {
                double val = V.get(rowI, colI);
                coordinates[rowI][dim - colI - 1] = val;
                double absVal = Math.abs(val);
                if (absVal > absMax) {
                    absMax = absVal;
                }
            }
        }

        // We add a little noise to avoid local minima for optimization to come
        double expansion = 10.0 / absMax;
        GaussianDistribution gaussian = new GaussianDistribution(0.0, 0.0001);
        double[] colMax = new double[dim];
        Arrays.setAll(colMax, i -> Double.MIN_VALUE);
        double[] colMin = new double[dim];
        Arrays.setAll(colMin, i -> Double.MAX_VALUE);
        for (int colI = 0; colI < dim; colI++) {
            for (int rowI = 0; rowI < size; rowI++) {
                coordinates[rowI][colI] *= expansion;
                coordinates[rowI][colI] += gaussian.rand();
                if (coordinates[rowI][colI] > colMax[colI]) {
                    colMax[colI] = coordinates[rowI][colI];
                }
                if (coordinates[rowI][colI] < colMin[colI]) {
                    colMin[colI] = coordinates[rowI][colI];
                }
            }
        }
        // normalization
        for (int colI = 0; colI < dim; colI++) {
            for (int rowI = 0; rowI < size; rowI++) {
                coordinates[rowI][colI] -= colMin[colI];
                coordinates[rowI][colI] *= 10.0;
                coordinates[rowI][colI] /= (colMax[colI] - colMin[colI]);
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
     * @param embedding
     *            embeddings to be optimized
     * @param optimizeParams
     *            Parameter of differentiable approximation of right adjoint
     *            functor, check {@link UMAP#func}
     * @param epochsPerSamples
     *            A float value of the number of epochs per 1-simplex betweeb
     *            (ith, jth) data points. 1-simplices with weaker membership
     *            strength will have more epochs between being sampled.
     * @param negativeSampleRate
     *            Number of negative samples to use per positive sample. default
     *            5
     * @param initialAlpha
     *            Initial learning rate for the SGD, default 1.0
     * @param gamma
     *            Weight to apply to negative samples, default 1.0
     * @param nEpochs
     *            The number of training epochs to use in optimization.
     */
    private static void optimizeLayout(double[][] embedding, double[] optimizeParams,
            Map<Integer, Map<Integer, Double>> epochsPerSamples, double negativeSampleRate, double initialAlpha,
            double gamma, int nEpochs) {

        int nVertices = embedding.length;
        double a = optimizeParams[0];
        double b = optimizeParams[1];
        double alpha = initialAlpha;
        int dim = embedding[0].length;

        Map<Integer, Map<Integer, Double>> epochPerNegativeSample = new HashMap<>(epochsPerSamples.size());
        Map<Integer, Map<Integer, Double>> epochNextNegativeSample = new HashMap<>(epochsPerSamples.size());
        Map<Integer, Map<Integer, Double>> epochNextSample = new HashMap<>(epochsPerSamples.size());
        for (Entry<Integer, Map<Integer, Double>> ent : epochsPerSamples.entrySet()) {
            epochPerNegativeSample.put(ent.getKey(), new HashMap<Integer, Double>(ent.getValue().size()));
            epochNextNegativeSample.put(ent.getKey(), new HashMap<Integer, Double>(ent.getValue().size()));
            epochNextSample.put(ent.getKey(), new HashMap<Integer, Double>(ent.getValue().size()));
            for (Entry<Integer, Double> samp : ent.getValue().entrySet()) {
                epochPerNegativeSample.get(ent.getKey()).put(samp.getKey(), samp.getValue() / negativeSampleRate);
                epochNextNegativeSample.get(ent.getKey()).put(samp.getKey(), samp.getValue() / negativeSampleRate);
                epochNextSample.get(ent.getKey()).put(samp.getKey(), samp.getValue());
            }
        }

        // we optimize Euclidean distance output
        EuclideanDistance metric = new EuclideanDistance();
        try {
            for (int n = 0; n < nEpochs; n++) {
                for (Entry<Integer, Map<Integer, Double>> ent : epochsPerSamples.entrySet()) {
                    int j = ent.getKey();
                    for (Entry<Integer, Double> samp : ent.getValue().entrySet()) {
                        int k = samp.getKey();
                        double prob = getDoubleValueFrom(epochNextSample, j, k);
                        if (prob <= n) {
                            double[] current = embedding[j];
                            double[] other = embedding[k];

                            double distSquared = metric.d(current, other);
                            double gradCoeff = 0;
                            if (distSquared > 0.0) {
                                gradCoeff = -2.0 * a * b * Math.pow(distSquared, b - 1.0);
                                gradCoeff /= a * Math.pow(distSquared, b) + 1.0;
                            } else {
                                gradCoeff = 0.0;
                            }

                            double gradD = 0;
                            for (int d = 0; d < dim; d++) {
                                gradD = clip(gradCoeff * (current[d] - other[d]));
                                current[d] += gradD * alpha;
                                other[d] += -gradD * alpha;
                            }
                            epochNextSample.get(j).put(k, samp.getValue() + getDoubleValueFrom(epochNextSample, j, k));

                            // negative sampling
                            int nNegSamples = (int) ((n - epochNextNegativeSample.get(j).get(k)) / epochPerNegativeSample.get(j).get(k));
                            for (int p = 0; p < nNegSamples; p++) {
                                k = MathEx.randomInt(Integer.MAX_VALUE) % nVertices;

                                other = embedding[k];

                                distSquared = metric.d(current, other);

                                if (distSquared > 0.0) {
                                    gradCoeff = 2.0 * gamma * b;
                                    gradCoeff /= (0.001 + distSquared) * (a * Math.pow(distSquared, b) + 1);
                                } else if (j == k) {
                                    continue;
                                } else {
                                    gradCoeff = 0.0;
                                }

                                for (int d = 0; d < dim; d++) {
                                    if (gradCoeff > 0.0) {
                                        gradD = clip(gradCoeff * (current[d] - other[d]));
                                    } else {
                                        gradD = 4.0;
                                    }
                                    current[d] += gradD * alpha;
                                }
                            }

                            epochNextNegativeSample.get(j).put(k, getDoubleValueFrom(epochNextNegativeSample, j, k)
                                    + getDoubleValueFrom(epochPerNegativeSample, j, k) * nNegSamples);
                        }
                    }
                }

                alpha = initialAlpha * (1.0 - (float) n / (float) nEpochs);
                logger.debug("completed " + (n + 1) + " epochs optimization on embedding.");
            }
        } catch (Exception e) {
            e.printStackTrace();
            logger.debug(e.getMessage());
        }
    }

    /**
     * return value from given hashmaps, possible 0 if no mapping existing for
     * given i->j->val
     * 
     * @param values
     *            hashmaps to find value
     * @param i
     *            first mapping index
     * @param j
     *            second mapping index
     * @return value for given mapping indexes: i->j->val
     */
    private static double getDoubleValueFrom(Map<Integer, Map<Integer, Double>> values, int i, int j) {
        if (values.get(i) != null) {
            Map<Integer, Double> iVals = values.get(i);
            if (iVals.get(j) != null) {
                return iVals.get(j);
            } else {
                return 0;
            }
        } else {
            return 0;
        }
    }

    /**
     * Given a set of weights and number of epochs generate the number of epochs
     * per sample for each weight.
     * 
     * @param weight
     *            The weights of how much we wish to sample each 1-simplex as
     *            {@link SparseMatrix}.
     * @param nEpochs
     *            The total number of epochs we want to train for.
     * @return An array of number of epochs per sample, one for each 1-simplex
     *         between (ith, jth) sample point.
     */
    private static Map<Integer, Map<Integer, Double>> makeEpochsPerSample(SparseMatrix weight, int nEpochs) {

        Map<Integer, Map<Integer, Double>> ret = new HashMap<Integer, Map<Integer, Double>>();

        double maxW = Double.MIN_VALUE;
        Iterator<SparseMatrix.Entry> nonZeros = weight.iterator();
        while (nonZeros != null && nonZeros.hasNext()) {
            SparseMatrix.Entry nonZero = nonZeros.next();
            if (nonZero != null) {
                if (nonZero.x > maxW) {
                    maxW = nonZero.x;
                }
            } else {
                break;
            }
        }

        nonZeros = weight.iterator();
        while (nonZeros != null && nonZeros.hasNext()) {
            SparseMatrix.Entry nonZero = nonZeros.next();
            if (nonZero != null) {
                double nSamples = (double) nEpochs * (nonZero.x / maxW);
                Map<Integer, Double> weights = ret.get(nonZero.i);
                if (weights == null) {
                    weights = new HashMap<Integer, Double>();
                    ret.put(nonZero.i, weights);
                }
                if (nSamples > 0) {
                    weights.put(nonZero.j, (double) nEpochs / nSamples);
                } else {
                    weights.put(nonZero.j, -1.0);
                }
            } else {
                break;
            }
        }

        return ret;
    }

    /**
     * Standard clamping of a value into a fixed range,e.g.,[-4.0,4.0]
     * 
     * @param val
     *            The value to be clamped.
     * @return The clamped value, now fixed to be in the range -4.0 to 4.0.
     */
    private static double clip(double val) {
        if (val > 4.0) {
            return 4.0;
        } else if (val < -4.0) {
            return -4.0;
        } else {
            return val;
        }
    }

}