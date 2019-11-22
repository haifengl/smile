/*******************************************************************************
 * Copyright (c) 2010-2019 Haifeng Li
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
 *******************************************************************************/

package smile.manifold;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Optional;
import smile.graph.Graph;
import smile.math.MathEx;
import smile.math.matrix.Matrix;
import smile.math.matrix.DenseMatrix;
import smile.math.matrix.SparseMatrix;
import smile.math.matrix.LU;
import smile.math.matrix.EVD;
import smile.netlib.ARPACK;

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
public class LLE implements Serializable {
    private static final long serialVersionUID = 2L;
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(LLE.class);

    /**
     * The original sample index.
     */
    public final int[] index;
    /**
     * Coordinate matrix.
     */
    public final double[][] coordinates;
    /**
     * Nearest neighbor graph.
     */
    public Graph graph;

    /**
     * Constructor.
     * @param index the original sample index.
     * @param coordinates the coordinates.
     * @param graph the nearest neighbor graph.
     */
    public LLE(int[] index, double[][] coordinates, Graph graph) {
        this.index = index;
        this.coordinates = coordinates;
        this.graph = graph;
    }

    /**
     * Runs the LLE algorithm.
     * @param data the dataset.
     * @param k k-nearest neighbor.
     */
    public static LLE of(double[][] data, int k) {
        return of(data, k, 2);
    }

    /**
     * Runs the LLE algorithm.
     * @param data the dataset.
     * @param d the dimension of the manifold.
     * @param k k-nearest neighbor.
     */
    public static LLE of(double[][] data, int k, int d) {
        int D = data[0].length;

        double tol = 0.0;
        if (k > D) {
            logger.info("LLE: regularization will be used since K > D.");
            tol = 1E-3;
        }

        // Use largest connected component of nearest neighbor graph.
        int[][] N = new int[data.length][k];
        Graph graph = NearestNeighborGraph.of(data, k, Optional.of((v1, v2, weight, j) -> {
            N[v1][j] = v2;
        }));
        NearestNeighborGraph nng = NearestNeighborGraph.largest(graph);

        int[] index = nng.index;
        int n = index.length;
        graph = nng.graph;

        // The reverse index maps the original data to the largest connected component
        // in case that the graph is disconnected.
        int[] reverseIndex = new int[n];
        if (index.length == n) {
            for (int i = 0; i < n; i++) {
                reverseIndex[i] = i;
            }
        } else {
            n = index.length;
            for (int i = 0; i < index.length; i++) {
                reverseIndex[index[i]] = i;
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
            double[] xi = data[i];
            for (int p = 0; p < k; p++) {
                double[] xip = data[N[i][p]];
                for (int q = 0; q < k; q++) {
                    double[] xiq = data[N[i][q]];
                    C.set(p, q, 0.0);
                    for (int l = 0; l < D; l++) {
                        C.add(p, q, (xi[l] - xip[l]) * (xi[l] - xiq[l]));
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

            double sum = MathEx.sum(b);
            int[] ni = N[i];
            for (int p = 0; p < k; p++) {
                w[m * k + p] = b[p] / sum;
                rowIndex[m * k + p] = reverseIndex[ni[p]];
            }

            m++;
        }

        // This is the transpose of W in the paper.
        SparseMatrix Wt = new SparseMatrix(n, n, w, rowIndex, colIndex);

        // ARPACK may not find all needed eigen values for k = d + 1.
        // Set it to 10 * (d + 1) as a hack to NCV parameter of DSAUPD.
        // Our Lanczos class has no such issue.
        EVD eigen = ARPACK.eigen(new M(Wt), Math.min(10*(d+1), n-1), "SM");

        DenseMatrix V = eigen.getEigenVectors();
        double[][] coordinates = new double[n][d];
        for (int j = d; --j >= 0; ) {
            int c = V.ncols() - j - 2;
            for (int i = 0; i < n; i++) {
                coordinates[i][j] = V.get(i, c);
            }
        }

        return new LLE(index, coordinates, graph);
    }

    /**
     * M = t(I - W) * (I - W) , t() as the transpose.
     * we have Mv = v - Wv - t(W)v + t(W)Wv. As W is sparse and we can
     * compute only Wv and t(W)v efficiently.
     */
    private static class M implements Matrix {

        Matrix Wt;
        double[] Wx;
        double[] Wtx;

        public M(Matrix Wt) {
            this.Wt = Wt;

            Wx = new double[Wt.nrows()];
            Wtx = new double[Wt.ncols()];
        }

        @Override
        public boolean isSymmetric() {
            return true;
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
        public M transpose() {
            return this;
        }

        @Override
        public M ata() {
            throw new UnsupportedOperationException();
        }

        @Override
        public M aat() {
            throw new UnsupportedOperationException();
        }

        @Override
        public double[] ax(double[] x, double[] y) {
            Wt.atx(x, Wx);
            Wt.ax(x, Wtx);
            Wt.ax(Wx, y);

            int n = Wt.nrows();
            for (int i = 0; i < n; i++) {
                y[i] = y[i] + x[i] - Wx[i] - Wtx[i];
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
    }
}
