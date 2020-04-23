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
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.stream.IntStream;

import smile.graph.Graph;
import smile.graph.Graph.Edge;
import smile.math.DifferentiableMultivariateFunction;
import smile.math.LevenbergMarquardt;
import smile.math.MathEx;
import smile.math.distance.Distance;
import smile.math.distance.EuclideanDistance;
import smile.math.matrix.DenseMatrix;
import smile.math.matrix.EVD;
import smile.math.matrix.SparseMatrix;
import smile.netlib.ARPACK;

/**
 * 
 * Uniform Manifold Approximation and Projection (UMAP) is a dimension reduction
 * technique that can be used for visualisation similarly to t-SNE, but also for
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
	 * Output embedding coordinate matrix.
	 */
	public double[][] coordinates;
	
	/** */
	private static final double SMOOTH_K_TOLERANCE = 1e-5;
	
	/** */
	private static final double MIN_K_DIST_SCALE = 1e-3;

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
	 *            at which embedded points will be spread out.
	 * @param metric
	 *            The {@link Distance} metric to use for the computation,
	 *            default {@link EuclideanDistance}
	 * @param nEpochs
	 *            The number of training epochs to use when optimizing the
	 *            low-dimensional representation. Larger values result in more
	 *            accurate embedding. require greater than 10, choose wise value
	 *            based on the size of the input dataset, e.g, 200 for large
	 *            datasets, 500 for small.
	 * @param sperad
	 *            The effective scale of embedded points. In combination with
	 *            minDist, this determines how clustered/clumped the embedded
	 *            points are.
	 * @param learningRate
	 *            The initial learning rate for the embedding optimization,
	 *            default 1.
	 * @param negativeSampleRate
	 *            The number of negative samples to select per positive sample
	 *            in the optimization process. Increasing this value will result
	 *            in greater repulsive force being applied, greater optimization
	 *            cost, but slightly more accuracy, default 5
	 */
	public UMAP(double[][] data, int n, int d, double minDist, Distance<double[]> metric, int nEpochs,
			double spread, double learningRate, double negativeSampleRate) {

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
			throw new IllegalArgumentException("minDist must be less than or equal to spread: " + minDist + ",spread=" + spread);
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

		double[] ab = find_ab_params(spread, minDist);
		
		// Constructing a local fuzzy simplicial set
		Distance<double[]> distance = (metric == null? new EuclideanDistance() : metric);
		int[][] neighbors = new int[data.length][n];
		Graph knns = nearest_neighbors(data, n, neighbors);
		double[] sigmas = new double[data.length];
		double[] rhos = new double[data.length];
		SparseMatrix coo = fuzzy_simplicial_set(knns, n, sigmas, rhos);

		// Spectral embedding for initialization
		coordinates = spectral_layout(coo, d);
		
		// Optimizing the embedding
		optimize_layout(coordinates, null, 1);
		
	}

	/**
	 * general curve to be fit:
	 * 
	 * <pre>
	 *    1.0 / (1.0 + a * x ** (2 * b))
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
	 *            points are.
	 * @param minDist
	 *            The desired separation between close points in the embedding
	 *            space. The value should be set no-greater than and relative to
	 *            the spread value, which determines the scale at which embedded
	 *            points will be spread out
	 * @return a, b params for the differentiable curve
	 */
	private static double[] find_ab_params(double spread, double minDist) {
		int xSize = 300;
		double[] x = new double[xSize];
		double[] y = new double[xSize];
		double xEnd = 3 * spread;
		double xInterval = xEnd / (xSize - 1);
		for (int i = 0; i < x.length; i++) {
			x[i] = i * xInterval;
			y[i] = x[i] < minDist ? 1 : Math.exp(-(x[i] - minDist) / spread);
		}
		double[] p = {1, 1};
		LevenbergMarquardt curveFit = LevenbergMarquardt.fit(func, x, y, p);
		return new double[]{curveFit.p[0], curveFit.p[1]};
	}
	
	/**
	 * Compute the n nearest points for each data point using approximate
	 * searching for speed consideration
	 * 
	 * @param data
	 *            input data for which to search nearest neighbors.
	 * @param n
	 *            The number of neighbors to consider when approximating the
	 *            neighbors.
	 * @param neighbors
	 *            record to hold nearest neighbors for each data point
	 * @return the n nearest points for each data point in a
	 *         {@link NearestNeighborGraph}
	 */
	private static Graph nearest_neighbors(double[][] data, int n,
			int[][] neighbors) {
		Graph knns = NearestNeighborGraph.of(data, n,
				Optional.of((v1, v2, weight, j) -> {
					neighbors[v1][j] = v2;
				}));
		return knns;
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
	private static SparseMatrix fuzzy_simplicial_set(Graph knns, int n, double[] sigmas, double[] rhos) {			
		smooth_knn_dist(knns, n, sigmas, rhos, 64);
		SparseMatrix ret = compute_membership_strengths(knns, sigmas, rhos);
		return ret;
	}

	/**
	 * Compute a continuous version of the distance to the kth nearest neighbor. 
	 * That is, this is similar to knn-distance but allows continuous k values rather than requiring an integral k. 
	 * In essence we are simply computing the distance 
	 * such that the cardinality of fuzzy set we generate is k.
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
	 *            We need to binary search for the correct distance value. This is the 
	 *            max number of iterations to use in such a search. default 64
	 */
	private static void smooth_knn_dist(Graph knns, int n, double[] sigmas, double[] rhos, int iterations) {
		
		double target = MathEx.log2(n);
		Arrays.setAll(sigmas, sigmai -> 0.0);
		Arrays.setAll(rhos, rhoi -> 0.0);

		double meanDistance = knns.getEdges().parallelStream()
				.mapToDouble(knn -> (knn != null && knn.weight > 0? knn.weight : 0))
				.average().orElse(0);

		for (int i = 0; i < knns.getNumVertices(); i++) {
			double lo = 0.0;
			double hi = Double.MAX_VALUE;
			double mid = 1.0;

			Collection<Edge> knn = knns.getEdges(i);
			rhos[i] = knn.parallelStream().mapToDouble(neighbor -> (neighbor != null && neighbor.weight > 0? neighbor.weight : 0)).min().orElse(0);

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
				double mean_ith_distances = knn.parallelStream().mapToDouble(neighbor -> neighbor.weight).average().orElse(0);
				if (sigmas[i] < MIN_K_DIST_SCALE * mean_ith_distances) {
					sigmas[i] = MIN_K_DIST_SCALE * mean_ith_distances;
				}
			} else {
				if (sigmas[i] < MIN_K_DIST_SCALE * meanDistance) {
					sigmas[i] = MIN_K_DIST_SCALE * meanDistance;
				}
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
	 *            Smooth approximator to knn-distance
	 * @param rhos
	 *            Distance to nearest neighbor
	 * @return A fuzzy simplicial set represented as a sparse matrix. The (i, j)
	 *         entry of the matrix represents the membership strength of the
	 *         1-simplex between the ith and jth sample points.
	 */
	private static SparseMatrix compute_membership_strengths(Graph knns, double[] sigmas, double[] rhos) {
		int dataSize = knns.getNumVertices();

		// construct original coo-format sparse matrix from knns
		Map<Integer, Map<Integer, Double>> cooColumns = new HashMap<Integer, Map<Integer, Double>>();
		IntStream.range(0, dataSize).parallel().forEach(n -> cooColumns.put(n, new HashMap<Integer, Double>()));
		int nonZeros = 0;
		for (int i = 0; i < dataSize; i++) {
			Collection<Edge> neighbors = knns.getEdges(i);
			if (neighbors == null || neighbors.size() <= 0) {
				continue;
			}
			for (Edge edge : neighbors) {
				double neighborDistance = edge.weight;
				if (neighborDistance < 0) {
					continue;
				}
				double val = -1;
				if (edge.v1 == i) {
					val = 0.0;
				} else if (neighborDistance <= rhos[i] || sigmas[i] == 0.0) {
					val = 1.0;
				} else {
					val = Math.exp(-(neighborDistance - rhos[i]) / (sigmas[i]));
				}

				if (val > 0) {
					cooColumns.get(edge.v1).put(i, val);
					nonZeros++;
				}
			}
		}
		SparseMatrix coo = convert2SpareMatrix(cooColumns, dataSize, nonZeros);

		// we need to re-construct the sparse matrix using probabilistic t-conorm: (a + b - a * b)
		SparseMatrix transpose = coo.transpose();
		SparseMatrix product = coo.aat();
		Map<Integer, Map<Integer, Double>> tCoNormCooColumns = new HashMap<Integer, Map<Integer, Double>>();
		IntStream.range(0, dataSize).parallel().forEach(n -> tCoNormCooColumns.put(n, new HashMap<Integer, Double>()));
		int tCoNormNonZeros = 0;
		for (int colI = 0; colI < dataSize; colI++) {
			for (int rowI = 0; rowI < dataSize; rowI++) {
				double newV = coo.get(rowI, colI) + transpose.get(rowI, colI) - product.get(rowI, colI);
				if (Math.abs(newV) > 0) {
					tCoNormCooColumns.get(colI).put(rowI, newV);
					tCoNormNonZeros++;
				}
			}
		}
		SparseMatrix tCoNorm = convert2SpareMatrix(tCoNormCooColumns, dataSize, tCoNormNonZeros);

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
	private static SparseMatrix convert2SpareMatrix(
			Map<Integer, Map<Integer, Double>> cooColumns, int dataSize,
			int nonZeros) {

		double[] x = new double[nonZeros];
		int[] rows = new int[nonZeros];
		int[] cols = new int[dataSize + 1];

		int colDataIdx = 0;
		for (int i = 0; i < dataSize; i++) {
			Map<Integer, Double> colData = cooColumns.get(i);
			if (colData.size() <= 0) {
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

		assert(nonZeros == colDataIdx);

		SparseMatrix coo = new SparseMatrix(dataSize, dataSize, x, rows, cols);
		return coo;
	}
	
	/**
	 * Given a graph compute the spectral embedding of the graph. This is simply
	 * the eigenvectors of the laplacian of the graph. Here we use the
	 * normalized laplacian.
	 * 
	 * @param topRep
	 *            The (weighted) adjacency matrix of the graph as a sparse matrix.
	 * @param dim
	 *            The dimension of the space into which to embed.
	 * @return The spectral embedding of the graph, of shape (n_vertices, dim)
	 */
	private double[][] spectral_layout(SparseMatrix topRep, int dim){
		int mSize = topRep.ncols();
		Map<Integer, Map<Integer, Double>> cooDiag = new HashMap<Integer, Map<Integer, Double>>(mSize);
		IntStream.range(0, mSize).parallel().forEach(n -> cooDiag.put(n, new HashMap<Integer, Double>()));
		
		Map<Integer, Map<Integer, Double>> cooIdentity = new HashMap<Integer, Map<Integer, Double>>(mSize);
		IntStream.range(0, mSize).parallel().forEach(n -> cooIdentity.put(n, new HashMap<Integer, Double>()));
		
		for(int colI = 0;colI < mSize;colI++) {
			double colSum = 0;
			for(int rowI = 0;rowI < topRep.nrows();rowI++) {
				colSum += topRep.get(rowI, colI);
			}
			cooDiag.get(colI).put(colI, 1 / Math.sqrt(colSum));
			cooIdentity.get(colI).put(colI, 1.0);
		}
		
		SparseMatrix diag = convert2SpareMatrix(cooDiag, mSize, mSize);
		SparseMatrix identity = convert2SpareMatrix(cooIdentity, mSize, mSize);
		SparseMatrix diagG = diag.abmm(topRep);
		SparseMatrix diagGDiag = diagG.abmm(diag);
		
		// construct the laplasian sparse matrix of (I - D * graph * D)
		Map<Integer, Map<Integer, Double>> normLaplacian = new HashMap<Integer, Map<Integer, Double>>(mSize);
		IntStream.range(0, mSize).parallel().forEach(n -> normLaplacian.put(n, new HashMap<Integer, Double>()));
		int normNonZeros = 0;
		for (int colI = 0; colI < mSize; colI++) {
			for (int rowI = 0; rowI < mSize; rowI++) {
				double newV = identity.get(rowI, colI) - diagGDiag.get(rowI, colI);
				if (Math.abs(newV) > 0) {
					normLaplacian.get(colI).put(rowI, newV);
					normNonZeros++;					
				}
			}
		}
		
		// calculate the first k eigen vectors for spectral manifold
		int k = dim + 1; 
		SparseMatrix embedding = convert2SpareMatrix(normLaplacian, mSize, normNonZeros);		
		EVD eigen = ARPACK.eigen(embedding, k, "SM");
		double[][] coordinates = new double[mSize][dim];
        DenseMatrix V = eigen.getEigenVectors();
		
		//TODO		
		// We add a little noise to avoid local minima for optimization to come
		
		return coordinates;		
	}
	
	/**
	 * Improve an embedding using stochastic gradient descent to minimize the
	 * fuzzy set cross entropy between the 1-skeletons of the high dimensional
	 * and low dimensional fuzzy simplicial sets. In practice this is done by
	 * sampling edges based on their membership strength (with the (1-p) terms
	 * coming from negative sampling similar to word2vec).
	 */
	private static void optimize_layout(double[][] coordinates,
			double[][] samples, double negative_sample_rate) {
		// TODO
	}

	/**
	 * Given a set of weights and number of epochs generate the number of epochs
	 * per sample for each weight.
	 * 
	 * @param graph
	 *            The nearest neighbors as {@link NearestNeighborGraph}.
	 * @param nEpochs
	 *            The total number of epochs we want to train for.
	 * @return An array of number of epochs per sample, one for each 1-simplex.
	 */
	private static double[][] make_epochs_per_sample(Graph graph, int nEpochs) {

		// TODO
		return null;
	}
	
}