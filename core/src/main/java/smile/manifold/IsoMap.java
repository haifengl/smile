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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import smile.graph.AdjacencyList;
import smile.graph.Graph;
import smile.graph.Graph.Edge;
import smile.math.distance.EuclideanDistance;
import smile.math.Math;
import smile.math.matrix.Matrix;
import smile.math.matrix.DenseMatrix;
import smile.math.matrix.EVD;
import smile.neighbor.CoverTree;
import smile.neighbor.KDTree;
import smile.neighbor.KNNSearch;
import smile.neighbor.Neighbor;

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
 * 
 * <h2>References</h2>
 * <ol>
 * <li> J. B. Tenenbaum, V. de Silva and J. C. Langford  A Global Geometric Framework for Nonlinear Dimensionality Reduction. Science 290(5500):2319-2323, 2000. </li> 
 * </ol>
 * 
 * @author Haifeng Li
 */
public class IsoMap {
    private static final Logger logger = LoggerFactory.getLogger(IsoMap.class);

    /**
     * The original sample index.
     */
    private int[] index;
    /**
     * Coordinate matrix.
     */
    private double[][] coordinates;
    /**
     * Nearest neighbor graph.
     */
    private Graph graph;

    /**
     * Constructor. C-Isomap algorithm by default.
     * @param data the dataset.
     * @param d the dimension of the manifold.
     * @param k k-nearest neighbor.
     */
    public IsoMap(double[][] data, int d, int k) {
        this(data, d, k, true);
    }
    
    /**
     * Constructor.
     * @param data the dataset.
     * @param d the dimension of the manifold.
     * @param k k-nearest neighbor.
     * @param CIsomap C-Isomap algorithm if true, otherwise standard algorithm.
     */
    public IsoMap(double[][] data, int d, int k, boolean CIsomap) {
        int n = data.length;

        KNNSearch<double[], double[]> knn = null;
        if (data[0].length < 10) {
            knn = new KDTree<>(data, data);
        } else {
            knn = new CoverTree<>(data, new EuclideanDistance());
        }

        graph = new AdjacencyList(n);
        double[] M = new double[n];
        for (int i = 0; i < n; i++) {
            Neighbor<double[], double[]>[] neighbors = knn.knn(data[i], k);

            for (int j = 0; j < neighbors.length; j++) {
                graph.setWeight(i, neighbors[j].index, neighbors[j].distance);
                M[i] += neighbors[j].distance;
            }
            M[i] = Math.sqrt(M[i] / neighbors.length);
        }

        // C-Isomap
        if (CIsomap) {
            for (Edge edge : graph.getEdges()) {
                edge.weight /= (M[edge.v1] * M[edge.v2]);
            }
        }
        
        // Use largest connected component.
        int[][] cc = graph.bfs();
        if (cc.length == 1) {
            index = new int[n];
            for (int i = 0; i < n; i++) {
                index[i] = i;
            }
        } else {
            n = 0;
            int component = 0;
            for (int i = 0; i < cc.length; i++) {
                if (cc[i].length > n) {
                    component = i;
                    n = cc[i].length;
                }
            }

            logger.info("IsoMap: {} connected components, largest one has {} samples.", cc.length, n);

            index = cc[component];
            graph = graph.subgraph(index);
        }

        double[][] D = graph.dijkstra();

        for (int i = 0; i < n; i++) {
            for (int j = 0; j < i; j++) {
                D[i][j] = -0.5 * D[i][j] * D[i][j];
                D[j][i] = D[i][j];
            }
        }

        double[] mean = Math.rowMeans(D);
        double mu = Math.mean(mean);

        DenseMatrix B = Matrix.zeros(n, n);
        for (int i = 0; i < n; i++) {
            for (int j = 0; j <= i; j++) {
                double b = D[i][j] - mean[i] - mean[j] + mu;
                B.set(i, j, b);
                B.set(j, i, b);
            }
        }

        B.setSymmetric(true);

        EVD eigen = B.eigen(d);

        if (eigen.getEigenValues().length < d) {
            logger.warn("eigen({}) returns only {} eigen vectors", d, eigen.getEigenValues().length);
            d = eigen.getEigenValues().length;
        }

        DenseMatrix V = eigen.getEigenVectors();
        coordinates = new double[n][d];
        for (int j = 0; j < d; j++) {
            if (eigen.getEigenValues()[j] < 0) {
                throw new IllegalArgumentException(String.format("Some of the first %d eigenvalues are < 0.", d));
            }

            double scale = Math.sqrt(eigen.getEigenValues()[j]);
            for (int i = 0; i < n; i++) {
                coordinates[i][j] = V.get(i, j) * scale;
            }
        }        
    }

    /**
     * Returns the original sample index. Because IsoMap is applied to the largest
     * connected component of k-nearest neighbor graph, we record the the original
     * indices of samples in the largest component.
     */
    public int[] getIndex() {
        return index;
    }

    /**
     * Returns the coordinates of projected data.
     */
    public double[][] getCoordinates() {
        return coordinates;
    }

    /**
     * Returns the nearest neighbor graph.
     */
    public Graph getNearestNeighborGraph() {
        return graph;
    }
}
