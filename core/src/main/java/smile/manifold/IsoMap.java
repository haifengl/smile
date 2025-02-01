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

import java.util.Properties;
import smile.graph.AdjacencyList;
import smile.graph.NearestNeighborGraph;
import smile.math.MathEx;
import smile.math.blas.UPLO;
import smile.math.distance.Distance;
import smile.math.matrix.ARPACK;
import smile.math.matrix.Matrix;

/**
 * Isometric feature mapping. Isomap is a widely used low-dimensional embedding methods,
 * where geodesic distances on a weighted graph are incorporated with the
 * classical multidimensional scaling. Isomap is used for computing a
 * quasi-isometric, low-dimensional embedding of a set of high-dimensional
 * data points. Isomap is highly efficient and generally applicable to a broad
 * range of data sources and dimensionality.
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
public class IsoMap {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(IsoMap.class);

    /**
     * IsoMap hyperparameters.
     * @param k k-nearest neighbor.
     * @param d the dimension of the manifold.
     * @param conformal C-Isomap algorithm if true, otherwise standard algorithm.
     */
    public record Options(int k, int d, boolean conformal) {
        public Options {
            if (k < 2) {
                throw new IllegalArgumentException("Invalid number of nearest neighbors: " + k);
            }
            if (d < 2) {
                throw new IllegalArgumentException("Invalid dimension of feature space: " + d);
            }
        }

        /**
         * Constructor.
         * @param k k-nearest neighbor.
         */
        public Options(int k) {
            this(k, 2, true);
        }

        /**
         * Returns the persistent set of hyperparameters.
         * @return the persistent set.
         */
        public Properties toProperties() {
            Properties props = new Properties();
            props.setProperty("smile.isomap.k", Integer.toString(k));
            props.setProperty("smile.isomap.d", Integer.toString(d));
            props.setProperty("smile.isomap.conformal", Boolean.toString(conformal));
            return props;
        }

        /**
         * Returns the options from properties.
         *
         * @param props the hyperparameters.
         * @return the options.
         */
        public static Options of(Properties props) {
            int k = Integer.parseInt(props.getProperty("smile.isomap.k", "7"));
            int d = Integer.parseInt(props.getProperty("smile.isomap.d", "2"));
            boolean conformal = Boolean.parseBoolean(props.getProperty("smile.isomap.conformal", "true"));
            return new Options(k, d, conformal);
        }
    }

    /**
     * Runs the Isomap algorithm.
     * @param data the input data.
     * @param options the hyperparameters.
     * @return the embedding coordinates.
     */
    public static double[][] of(double[][] data, Options options) {
        return of(data, MathEx::distance, options);
    }

    /**
     * Runs the Isomap algorithm.
     * @param data the input data.
     * @param distance the distance function.
     * @param options the hyperparameters.
     * @param <T> the data type of points.
     * @return the embedding coordinates.
     */
    public static <T> double[][] of(T[] data, Distance<T> distance, Options options) {
        // Use the largest connected component of nearest neighbor graph.
        NearestNeighborGraph nng = NearestNeighborGraph.of(data, distance, options.k);
        return of(nng.largest(false), options);
    }

    /**
     * Runs the Isomap algorithm.
     * @param nng the k-nearest neighbor graph.
     * @param options the hyperparameters.
     * @return the embedding coordinates.
     */
    public static double[][] of(NearestNeighborGraph nng, Options options) {
        int d = options.d;
        boolean conformal = options.conformal;
        AdjacencyList graph = nng.graph(false);

        if (conformal) {
            double[] M = MathEx.rowMeans(nng.distances());
            int n = M.length;
            for (int i = 0; i < n; i++) {
                M[i] = Math.sqrt(M[i]);
            }

            for (int i = 0; i < n; i++) {
                double Mi = M[i];
                graph.updateEdges(i, (j, w) -> w / (Mi * M[j]));
            }
        }

        int n = graph.getVertexCount();
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

        return coordinates;
    }
}
