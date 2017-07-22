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
import java.util.Comparator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import smile.graph.AdjacencyList;
import smile.graph.Graph;
import smile.math.Math;
import smile.math.distance.EuclideanDistance;
import smile.math.matrix.Matrix;
import smile.math.matrix.DenseMatrix;
import smile.math.matrix.SparseMatrix;
import smile.math.matrix.LU;
import smile.math.matrix.EVD;
import smile.math.matrix.Lanczos;
import smile.neighbor.CoverTree;
import smile.neighbor.KDTree;
import smile.neighbor.KNNSearch;
import smile.neighbor.Neighbor;

/**
 * Locally Linear Embedding. It has several advantages over Isomap, including
 * faster optimization when implemented to take advantage of sparse matrix
 * algorithms, and better results with many problems. LLE also begins by
 * finding a set of the nearest neighbors of each point. It then computes
 * a set of weights for each point that best describe the point as a linear
 * combination of its neighbors. Finally, it uses an eigenvector-based
 * optimization technique to find the low-dimensional embedding of points,
 * such that each point is still described with the same linear combination
 * of its neighbors. LLE tends to handle non-uniform sample densities poorly
 * because there is no fixed unit to prevent the weights from drifting as
 * various regions differ in sample densities.
 * 
 * @see IsoMap
 * @see LaplacianEigenmap
 * 
 * <h2>References</h2>
 * <ol>
 * <li> Sam T. Roweis and Lawrence K. Saul. Nonlinear Dimensionality Reduction by Locally Linear Embedding. Science 290(5500):2323-2326, 2000. </li>
 * </ol>
 * 
 * @author Haifeng Li
 */
public class LLE {
    private static final Logger logger = LoggerFactory.getLogger(LLE.class);

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
     * Constructor.
     * @param data the dataset.
     * @param d the dimension of the manifold.
     * @param k k-nearest neighbor.
     */
    public LLE(double[][] data, int d, int k) {
        int n = data.length;
        int D = data[0].length;

        double tol = 0.0;
        if (k > D) {
            logger.info("LLE: regularization will be used since K > D.");
            tol = 1E-3;
        }

        KNNSearch<double[], double[]> knn = null;
        if (D < 10) {
            knn = new KDTree<>(data, data);
        } else {
            knn = new CoverTree<>(data, new EuclideanDistance());
        }

        Comparator<Neighbor<double[], double[]>> comparator = new Comparator<Neighbor<double[], double[]>>() {

            @Override
            public int compare(Neighbor<double[], double[]> o1, Neighbor<double[], double[]> o2) {
                return o1.index - o2.index;
            }
        };

        int[][] N = new int[n][k];
        graph = new AdjacencyList(n);
        for (int i = 0; i < n; i++) {
            Neighbor<double[], double[]>[] neighbors = knn.knn(data[i], k);
            Arrays.sort(neighbors, comparator);

            for (int j = 0; j < k; j++) {
                graph.setWeight(i, neighbors[j].index, neighbors[j].distance);
                N[i][j] = neighbors[j].index;
            }
        }

        // Use largest connected component.
        int[][] cc = graph.bfs();
        int[] newIndex = new int[n];
        if (cc.length == 1) {
            index = new int[n];
            for (int i = 0; i < n; i++) {
                index[i] = i;
                newIndex[i] = i;
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

            logger.info("LLE: {} connected components, largest one has {} samples.", cc.length, n);

            index = cc[component];
            graph = graph.subgraph(index);
            for (int i = 0; i < index.length; i++) {
                newIndex[index[i]] = i;
            }
        }

        int len = n * k;
        double[] w = new double[len];
        int[] rowIndex = new int[len];
        int[] colIndex = new int[n + 1];
        for (int i = 1; i <= n; i++) {
            colIndex[i] = colIndex[i - 1] + k;
        }

        DenseMatrix C = Matrix.zeros(k, k);
        double[] b = new double[k];

        int m = 0;
        for (int i : index) {
            double trace = 0.0;
            for (int p = 0; p < k; p++) {
                for (int q = 0; q < k; q++) {
                    C.set(p, q, 0.0);
                    for (int l = 0; l < D; l++) {
                        C.add(p, q, (data[i][l] - data[N[i][p]][l]) * (data[i][l] - data[N[i][q]][l]));
                    }
                }
                trace += C.get(p, p);
            }

            if (tol != 0.0) {
                trace *= tol;
                for (int p = 0; p < k; p++) {
                    C.add(p, p, trace);
                }
            }

            Arrays.fill(b, 1.0);
            LU lu = C.lu(true);
            lu.solve(b);

            double sum = Math.sum(b);
            for (int p = 0; p < k; p++) {
                w[m * k + p] = b[p] / sum;
                rowIndex[m * k + p] = newIndex[N[i][p]];
            }

            m++;
        }

        // This is the transpose of W in the paper.
        SparseMatrix Wt = new SparseMatrix(n, n, w, rowIndex, colIndex);
        IM im = new IM(Wt);

        // ARPACK may not find all needed eigen values for k = d + 1.
        // Set it to 10 * (d + 1) as a hack to NCV parameter of DSAUPD.
        // Our Lanczos class has no such issue.
        EVD eigen = im.eigen(Math.min(10*(d + 1), n - 1));

        DenseMatrix V = eigen.getEigenVectors();
        coordinates = new double[n][d];
        for (int j = 0; j < d; j++) {
            for (int i = 0; i < n; i++) {
                coordinates[i][j] = V.get(i, j + 1);
            }
        }
    }

    /**
     * Returns the original sample index. Because LLE is applied to the largest
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

    /**
     * Instead of computing smallest eigen values of M, we
     * computing the largest eigen values of I - M.
     * Since M = t(I - W) * (I - W), t() as the transpose,
     * we have (I - M)v = Wv + t(W)(v - Wv). As W is sparse and we can
     * compute only Wv and t(W)v efficiently.
     */
    private static class IM extends Matrix {

        Matrix Wt;
        double[] Wx;
        double[] Wtx;

        public IM(Matrix Wt) {
            this.Wt = Wt;
            setSymmetric(true);

            Wx = new double[Wt.nrows()];
            Wtx = new double[Wt.ncols()];
        }

        @Override
        public int nrows() {
            return Wt.nrows();
        }

        @Override
        public int ncols() {
            return nrows();
        }

        @Override
        public IM transpose() {
            return this;
        }

        @Override
        public IM ata() {
            throw new UnsupportedOperationException();
        }

        @Override
        public IM aat() {
            throw new UnsupportedOperationException();
        }

        @Override
        public double[] ax(double[] x, double[] y) {
            Wt.atx(x, Wx);

            int n = Wt.nrows();
            for (int i = 0; i < n; i++) {
                Wtx[i] = x[i] - Wx[i];
            }

            Wt.ax(Wtx, y);
            for (int i = 0; i < n; i++) {
                y[i] += Wx[i];
            }

            return y;
        }

        @Override
        public double[] atx(double[] x, double[] y) {
            return ax(x, y);
        }

        @Override
        public double[] axpy(double[] x, double[] y) {
            throw new UnsupportedOperationException();
        }

        @Override
        public double[] axpy(double[] x, double[] y, double b) {
            throw new UnsupportedOperationException();
        }

        @Override
        public double get(int i, int j) {
            throw new UnsupportedOperationException();
        }

        @Override
        public double apply(int i, int j) {
            throw new UnsupportedOperationException();
        }

        @Override
        public double[] atxpy(double[] x, double[] y) {
            throw new UnsupportedOperationException();
        }

        @Override
        public double[] atxpy(double[] x, double[] y, double b) {
            throw new UnsupportedOperationException();
        }
    };
}
