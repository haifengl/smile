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
import smile.graph.AdjacencyList;
import smile.graph.Graph.Edge;
import smile.math.MathEx;
import smile.math.blas.UPLO;
import smile.math.distance.Distance;
import smile.math.distance.EuclideanDistance;
import smile.math.matrix.ARPACK;
import smile.math.matrix.Matrix;

/**
 * Isometric feature mapping. Isomap is a widely used low-dimensional embedding methods,
 * where geodesic distances on a weighted graph are incorporated with the
 * classical multidimensional scaling. Isomap is used for computing a
 * quasi-isometric, low-dimensional embedding of a set of high-dimensional
 * data points. Isomap is highly efficient and generally applicable to a broad
 * range of data sources and dimensionalities.
 * <p>
 * To be specific, the classical MDS performs low-dimensional embedding based
 * on the pairwise distance between data points, which is generally measured
 * using straight-line Euclidean distance. Isomap is distinguished by
 * its use of the geodesic distance induced by a neighborhood graph
 * embedded in the classical scaling. This is done to incorporate manifold
 * structure in the resulting embedding. Isomap defines the geodesic distance
 * to be the sum of edge weights along the shortest path between two nodes.
 * The top n eigenvectors of the geodesic distance matrix, represent the
 * coordinates in the new n-dimensional Euclidean space.
 * <p>
 * The connectivity of each data point in the neighborhood graph is defined
 * as its nearest k Euclidean neighbors in the high-dimensional space. This
 * step is vulnerable to "short-circuit errors" if k is too large with
 * respect to the manifold structure or if noise in the data moves the
 * points slightly off the manifold. Even a single short-circuit error
 * can alter many entries in the geodesic distance matrix, which in turn
 * can lead to a drastically different (and incorrect) low-dimensional
 * embedding. Conversely, if k is too small, the neighborhood graph may
 * become too sparse to approximate geodesic paths accurately.
 * <p>
 * This class implements C-Isomap that involves magnifying the regions
 * of high density and shrink the regions of low density of data points
 * in the manifold. Edge weights that are maximized in Multi-Dimensional
 * Scaling(MDS) are modified, with everything else remaining unaffected.
 * 
 * @see LLE
 * @see LaplacianEigenmap
 * @see UMAP
 * 
 * <h2>References</h2>
 * <ol>
 * <li> J. B. Tenenbaum, V. de Silva and J. C. Langford  A Global Geometric Framework for Nonlinear Dimensionality Reduction. Science 290(5500):2319-2323, 2000. </li> 
 * </ol>
 * 
 * @author Haifeng Li
 */
public class IsoMap implements Serializable {
    private static final long serialVersionUID = 2L;
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(IsoMap.class);

    /**
     * The original sample index.
     */
    public final int[] index;
    /**
     * The coordinate matrix in embedding space.
     */
    public final double[][] coordinates;
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
    public IsoMap(int[] index, double[][] coordinates, AdjacencyList graph) {
        this.index = index;
        this.coordinates = coordinates;
        this.graph = graph;
    }

    /**
     * Runs the C-Isomap algorithm with Euclidean distance.
     * @param data the input data.
     * @param k k-nearest neighbor.
     * @return the model.
     */
    public static IsoMap of(double[][] data, int k) {
        return of(data, k, 2, true);
    }

    /**
     * Runs the Isomap algorithm.
     * @param data the input data.
     * @param d the dimension of the manifold.
     * @param k k-nearest neighbor.
     * @param conformal C-Isomap algorithm if true, otherwise standard algorithm.
     * @return the model.
     */
    public static IsoMap of(double[][] data, int k, int d, boolean conformal) {
        return of(data, new EuclideanDistance(), k, d, conformal);
    }

    /**
     * Runs the C-Isomap algorithm.
     * @param data the input data.
     * @param k k-nearest neighbor.
     * @param distance the distance function.
     * @param <T> the data type of points.
     * @return the model.
     */
    public static <T> IsoMap of(T[] data, Distance<T> distance, int k) {
        return of(data, distance, k, 2, true);
    }
    
    /**
     * Runs the Isomap algorithm.
     * @param data the input data.
     * @param distance the distance function.
     * @param k k-nearest neighbor.
     * @param d the dimension of the manifold.
     * @param conformal C-Isomap algorithm if true, otherwise standard algorithm.
     * @param <T> the data type of points.
     * @return the model.
     */
    public static <T> IsoMap of(T[] data, Distance<T> distance, int k, int d, boolean conformal) {
        AdjacencyList graph;
        if (!conformal) {
            graph = NearestNeighborGraph.of(data, distance, k, false, null);
        } else {
            int n = data.length;
            double[] M = new double[n];
            graph = NearestNeighborGraph.of(data, distance, k, false, (v1, v2, weight, j) -> M[v1] += weight);

            for (int i = 0; i < n; i++) {
                M[i] = Math.sqrt(M[i] / k);
            }

            for (Edge edge : graph.getEdges()) {
                edge.weight /= (M[edge.v1] * M[edge.v2]);
            }
        }

        // Use largest connected component of nearest neighbor graph.
        NearestNeighborGraph nng = NearestNeighborGraph.largest(graph);

        int[] index = nng.index;
        int n = index.length;
        graph = nng.graph;

        double[][] D = graph.dijkstra();
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < i; j++) {
                D[i][j] = -0.5 * D[i][j] * D[i][j];
                D[j][i] = D[i][j];
            }
        }

        double[] mean = MathEx.rowMeans(D);
        double mu = MathEx.mean(mean);

        Matrix B = new Matrix(n, n);
        for (int i = 0; i < n; i++) {
            for (int j = 0; j <= i; j++) {
                double b = D[i][j] - mean[i] - mean[j] + mu;
                B.set(i, j, b);
                B.set(j, i, b);
            }
        }

        B.uplo(UPLO.LOWER);
        Matrix.EVD eigen = ARPACK.syev(B, ARPACK.SymmOption.LA, d);

        if (eigen.wr.length < d) {
            logger.warn("eigen({}) returns only {} eigen vectors", d, eigen.wr.length);
            d = eigen.wr.length;
        }

        Matrix V = eigen.Vr;
        double[][] coordinates = new double[n][d];
        for (int j = 0; j < d; j++) {
            if (eigen.wr[j] < 0) {
                throw new IllegalArgumentException(String.format("Some of the first %d eigenvalues are < 0.", d));
            }

            double scale = Math.sqrt(eigen.wr[j]);
            for (int i = 0; i < n; i++) {
                coordinates[i][j] = V.get(i, j) * scale;
            }
        }

        return new IsoMap(index, coordinates, graph);
    }
}
