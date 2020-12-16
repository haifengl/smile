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
import java.util.Arrays;
import smile.graph.AdjacencyList;
import smile.math.MathEx;
import smile.math.blas.Transpose;
import smile.math.matrix.ARPACK;
import smile.math.matrix.DMatrix;
import smile.math.matrix.Matrix;
import smile.math.matrix.SparseMatrix;

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
 * @see UMAP
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
     * The coordinate matrix in embedding space.
     */
    public final double[][] coordinates;
    /**
     * Nearest neighbor graph.
     */
    public AdjacencyList graph;

    /**
     * Constructor.
     * @param index the original sample index.
     * @param coordinates the coordinates.
     * @param graph the nearest neighbor graph.
     */
    public LLE(int[] index, double[][] coordinates, AdjacencyList graph) {
        this.index = index;
        this.coordinates = coordinates;
        this.graph = graph;
    }

    /**
     * Runs the LLE algorithm.
     * @param data the input data.
     * @param k k-nearest neighbor.
     * @return the model.
     */
    public static LLE of(double[][] data, int k) {
        return of(data, k, 2);
    }

    /**
     * Runs the LLE algorithm.
     * @param data the input data.
     * @param d the dimension of the manifold.
     * @param k k-nearest neighbor.
     * @return the model.
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
        AdjacencyList graph = NearestNeighborGraph.of(data, k, false, (v1, v2, weight, j) -> N[v1][j] = v2);
        NearestNeighborGraph nng = NearestNeighborGraph.largest(graph);

        int[] index = nng.index;
        int n = data.length;
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

        Matrix C = new Matrix(k, k);
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
            Matrix.LU lu = C.lu(true);
            b = lu.solve(b);

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

        // ARPACK may not find all needed eigenvalues for k = d + 1.
        // Hack it with 10 * (d + 1).
        Matrix.EVD eigen = ARPACK.syev(new M(Wt), ARPACK.SymmOption.SM, Math.min(10*(d+1), n-1));

        Matrix V = eigen.Vr;
        // Sometimes, ARPACK doesn't compute the smallest eigenvalue (i.e. 0).
        // Maybe due to numeric stability.
        int offset = eigen.wr[eigen.wr.length - 1] < 1E-12 ? 2 : 1;
        double[][] coordinates = new double[n][d];
        for (int j = d; --j >= 0; ) {
            int c = V.ncol() - j - offset;
            for (int i = 0; i < n; i++) {
                coordinates[i][j] = V.get(i, c);
            }
        }

        return new LLE(index, coordinates, graph);
    }

    /**
     * M = (I - W)' * (I - W).
     * we have M * v = v - W * v - W' * v + W' * W * v. As W is sparse and we can
     * compute only W * v and W' * v efficiently.
     */
    private static class M extends DMatrix {

        SparseMatrix Wt;
        double[] x;
        double[] Wx;
        double[] Wtx;
        double[] WtWx;

        public M(SparseMatrix Wt) {
            this.Wt = Wt;

            x = new double[Wt.nrow()];
            Wx = new double[Wt.nrow()];
            Wtx = new double[Wt.ncol()];
            WtWx = new double[Wt.nrow()];
        }

        @Override
        public int nrow() {
            return Wt.nrow();
        }

        @Override
        public int ncol() {
            return nrow();
        }

        @Override
        public long size() {
            return Wt.size();
        }

        @Override
        public void mv(double[] work, int inputOffset, int outputOffset) {
            System.arraycopy(work, inputOffset, x, 0, x.length);
            Wt.tv(x, Wx);
            Wt.mv(x, Wtx);
            Wt.mv(Wx, WtWx);

            int n = x.length;
            for (int i = 0; i < n; i++) {
                work[outputOffset + i] = WtWx[i] + x[i] - Wx[i] - Wtx[i];
            }
        }

        @Override
        public void tv(double[] work, int inputOffset, int outputOffset) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void mv(Transpose trans, double alpha, double[] x, double beta, double[] y) {
            throw new UnsupportedOperationException();
        }

        @Override
        public DMatrix set(int i, int j, double x) {
            throw new UnsupportedOperationException();
        }

        @Override
        public double get(int i, int j) {
            throw new UnsupportedOperationException();
        }
    }
}
