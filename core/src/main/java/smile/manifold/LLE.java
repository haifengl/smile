/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * SMILE is distributed in the hope that it will be useful, but
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
import smile.linalg.Transpose;
import smile.tensor.*;
import static smile.tensor.ScalarType.*;

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
public class LLE {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(LLE.class);

    /** Private constructor to prevent object creation. */
    private LLE() {

    }

    /**
     * LLE hyperparameters.
     * @param k k-nearest neighbor.
     * @param d the dimension of the manifold.
     */
    public record Options(int k, int d) {
        /** Constructor. */
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
            this(k, 2);
        }

        /**
         * Returns the persistent set of hyperparameters.
         * @return the persistent set.
         */
        public Properties toProperties() {
            Properties props = new Properties();
            props.setProperty("smile.lle.k", Integer.toString(k));
            props.setProperty("smile.lle.d", Integer.toString(d));
            return props;
        }

        /**
         * Returns the options from properties.
         *
         * @param props the hyperparameters.
         * @return the options.
         */
        public static Options of(Properties props) {
            int k = Integer.parseInt(props.getProperty("smile.lle.k", "7"));
            int d = Integer.parseInt(props.getProperty("smile.lle.d", "2"));
            return new Options(k, d);
        }
    }

    /**
     * Runs the LLE algorithm.
     * @param data the input data.
     * @param options the hyperparameters.
     * @return the embedding coordinates.
     */
    public static double[][] fit(double[][] data, Options options) {
        // Use the largest connected component of nearest neighbor graph.
        NearestNeighborGraph nng = NearestNeighborGraph.of(data, options.k);
        return fit(data, nng.largest(false), options.d);
    }

    /**
     * Runs the LLE algorithm.
     * @param data the input data.
     * @param nng the k-nearest neighbor graph.
     * @param d the dimension of the manifold.
     * @return the embedding coordinates.
     */
    public static double[][] fit(double[][] data, NearestNeighborGraph nng, int d) {
        int k = nng.k();
        int D = data[0].length;

        double tol = 0.0;
        if (k > D) {
            logger.info("LLE: regularization will be used since K > D.");
            tol = 1E-3;
        }

        AdjacencyList graph = nng.graph(false);
        int[][] N = nng.neighbors();
        int[] index = nng.index();

        // The reverse index maps the original data to the largest connected component
        // in case that the graph is disconnected.
        int n = index.length;
        int[] reverseIndex = new int[data.length];
        for (int i = 0; i < index.length; i++) {
            reverseIndex[index[i]] = i;
        }

        int len = n * k;
        double[] w = new double[len];
        int[] rowIndex = new int[len];
        int[] colIndex = new int[n + 1];
        for (int i = 1; i <= n; i++) {
            colIndex[i] = colIndex[i - 1] + k;
        }

        DenseMatrix C = DenseMatrix.zeros(Float64, k, k);
        Vector b = C.vector(k);

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

            b.fill(1.0);
            LU lu = C.lu();
            lu.solve(b);

            double sum = b.sum();
            int[] ni = N[i];
            for (int p = 0; p < k; p++) {
                w[m * k + p] = b.get(p) / sum;
                rowIndex[m * k + p] = reverseIndex[ni[p]];
            }

            m++;
        }

        // This is the transpose of W in the paper.
        SparseMatrix Wt = new SparseMatrix(n, n, w, rowIndex, colIndex);

        // ARPACK may not find all needed eigenvalues for k = d + 1.
        // Hack it with 10 * (d + 1).
        EVD eigen = ARPACK.syev(new M(Wt), ARPACK.SymmOption.SM, Math.min(10*(d+1), n-1));

        DenseMatrix V = eigen.Vr();
        // Sometimes, ARPACK doesn't compute the smallest eigenvalue (i.e. 0).
        // Maybe due to numeric stability.
        int offset = eigen.wr().get(eigen.wr().size() - 1) < 1E-12 ? 2 : 1;
        double[][] coordinates = new double[n][d];
        for (int j = d; --j >= 0; ) {
            int c = V.ncol() - j - offset;
            for (int i = 0; i < n; i++) {
                coordinates[i][j] = V.get(i, c);
            }
        }

        return coordinates;
    }

    /**
     * M = (I - W)' * (I - W).
     * we have M * v = v - W * v - W' * v + W' * W * v. As W is sparse and we can
     * compute only W * v and W' * v efficiently.
     */
    private static class M implements Matrix {
        final SparseMatrix Wt;
        final Vector x;
        final Vector Wx;
        final Vector Wtx;
        final Vector WtWx;

        public M(SparseMatrix Wt) {
            this.Wt = Wt;
            x = Wt.vector(Wt.nrow());
            Wx = Wt.vector(Wt.nrow());
            Wtx = Wt.vector(Wt.ncol());
            WtWx = Wt.vector(Wt.nrow());
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
        public long length() {
            return Wt.length();
        }

        @Override
        public ScalarType scalarType() {
            return Wt.scalarType();
        }

        @Override
        public void mv(Vector work, int inputOffset, int outputOffset) {
            Vector.copy(work, inputOffset, x, 0, x.size());
            Wt.tv(x, Wx);
            Wt.mv(x, Wtx);
            Wt.mv(Wx, WtWx);

            int n = x.size();
            for (int i = 0; i < n; i++) {
                work.set(outputOffset + i, WtWx.get(i) + x.get(i) - Wx.get(i) - Wtx.get(i));
            }
        }

        @Override
        public void mv(Transpose trans, double alpha, Vector x, double beta, Vector y) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void tv(Vector work, int inputOffset, int outputOffset) {
            throw new UnsupportedOperationException();
        }

        @Override
        public double get(int i, int j) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void set(int i, int j, double x) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void add(int i, int j, double x) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void sub(int i, int j, double x) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void mul(int i, int j, double x) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void div(int i, int j, double x) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Matrix scale(double alpha) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Matrix copy() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Matrix transpose() {
            throw new UnsupportedOperationException();
        }
    }
}
