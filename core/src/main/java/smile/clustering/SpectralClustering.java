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
package smile.clustering;

import java.util.Arrays;
import java.util.Properties;
import java.util.stream.IntStream;
import smile.data.SparseDataset;
import smile.math.MathEx;
import smile.math.blas.Transpose;
import smile.math.blas.UPLO;
import smile.math.matrix.ARPACK;
import smile.math.matrix.IMatrix;
import smile.math.matrix.Matrix;
import smile.util.AlgoStatus;
import smile.util.IterativeAlgorithmController;
import smile.util.SparseArray;
import smile.util.SparseIntArray;

/**
 * Spectral Clustering. Given a set of data points, the similarity matrix may
 * be defined as a matrix S where S<sub>ij</sub> represents a measure of the
 * similarity between points. Spectral clustering techniques make use of the
 * spectrum of the similarity matrix of the data to perform dimensionality
 * reduction for clustering in fewer dimensions. Then the clustering will
 * be performed in the dimension-reduce space, in which clusters of non-convex
 * shape may become tight. There are some intriguing similarities between
 * spectral clustering methods and kernel PCA, which has been empirically
 * observed to perform clustering.
 *
 * <h2>References</h2>
 * <ol>
 * <li> A.Y. Ng, M.I. Jordan, and Y. Weiss. On Spectral Clustering: Analysis and an algorithm. NIPS, 2001. </li>
 * <li> Marina Maila and Jianbo Shi. Learning segmentation by random walks. NIPS, 2000. </li>
 * <li> Deepak Verma and Marina Meila. A Comparison of Spectral Clustering Algorithms. 2003. </li>
 * <li> Kai Zhang, Nathan R. Zemke, Ethan J. Armand and Bing Ren. A fast, scalable and versatile tool for analysis of single-cell omics data. 2024.</li>
 * </ol>
 * 
 * @author Haifeng Li
 */
public class SpectralClustering {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(SpectralClustering.class);

    /** Constructor. */
    private SpectralClustering() {

    }

    /**
     * Spectral clustering hyperparameters.
     * @param k the number of clusters.
     * @param l the number of random samples for Nystrom approximation.
     *          Uses 0 to disable approximation.
     * @param sigma the smooth/width parameter of Gaussian kernel, which is
     *              a somewhat sensitive parameter. To search for the best
     *              setting, one may pick the value that gives the tightest
     *              clusters (smallest distortion) in feature space.
     * @param maxIter the maximum number of iterations.
     * @param tol the tolerance of convergence test.
     * @param controller the optional training controller.
     */
    public record Options(int k, int l, double sigma, int maxIter, double tol,
                          IterativeAlgorithmController<AlgoStatus> controller) {
        /** Constructor. */
        public Options {
            if (k < 2) {
                throw new IllegalArgumentException("Invalid number of clusters: " + k);
            }

            if (l < k && l > 0) {
                throw new IllegalArgumentException("Invalid number of random samples: " + l);
            }

            if (sigma <= 0.0) {
                throw new IllegalArgumentException("Invalid standard deviation of Gaussian kernel: " + sigma);
            }

            if (maxIter <= 0) {
                throw new IllegalArgumentException("Invalid maximum number of iterations: " + maxIter);
            }

            if (tol < 0) {
                throw new IllegalArgumentException("Invalid tolerance: " + tol);
            }
        }

        /**
         * Constructor.
         * @param k the number of clusters.
         * @param sigma the smooth/width parameter of Gaussian kernel.
         * @param maxIter the maximum number of iterations.
         */
        public Options(int k, double sigma, int maxIter) {
            this(k, 0, sigma, maxIter);
        }

        /**
         * Constructor.
         * @param k the number of clusters.
         * @param l the number of random samples for Nystrom approximation.
         *          Uses 0 to disable approximation.
         * @param sigma the smooth/width parameter of Gaussian kernel.
         * @param maxIter the maximum number of iterations.
         */
        public Options(int k, int l, double sigma, int maxIter) {
            this(k, l, sigma, maxIter, 1E-4, null);
        }

        /**
         * Returns the persistent set of hyperparameters.
         * @return the persistent set.
         */
        public Properties toProperties() {
            Properties props = new Properties();
            props.setProperty("smile.spectral_clustering.k", Integer.toString(k));
            props.setProperty("smile.spectral_clustering.l", Integer.toString(l));
            props.setProperty("smile.spectral_clustering.sigma", Double.toString(sigma));
            props.setProperty("smile.spectral_clustering.iterations", Integer.toString(maxIter));
            props.setProperty("smile.spectral_clustering.tolerance", Double.toString(tol));
            return props;
        }

        /**
         * Returns the options from properties.
         *
         * @param props the hyperparameters.
         * @return the options.
         */
        public static Options of(Properties props) {
            int k = Integer.parseInt(props.getProperty("smile.spectral_clustering.k", "2"));
            int l = Integer.parseInt(props.getProperty("smile.spectral_clustering.l", "0"));
            double sigma = Double.parseDouble(props.getProperty("smile.spectral_clustering.sigma", "1.0"));
            int maxIter = Integer.parseInt(props.getProperty("smile.spectral_clustering.iterations", "100"));
            double tol = Double.parseDouble(props.getProperty("smile.spectral_clustering.tolerance", "1E-4"));
            return new Options(k, l, sigma, maxIter, tol, null);
        }
    }

    /**
     * Spectral clustering the nonnegative count data with cosine similarity.
     * @param data the nonnegative count matrix.
     * @param p the number of features.
     * @param options the hyperparameters.
     * @return the model.
     */
    public static CentroidClustering<double[], double[]> fit(SparseIntArray[] data, int p, Clustering.Options options) {
        double[][] Y = embed(data, p, options.k());
        return KMeans.fit(Y, options);
    }

    /**
     * Spectral clustering the data.
     * @param data the input data of which each row is an observation.
     * @param options the hyperparameters.
     * @return the model.
     */
    public static CentroidClustering<double[], double[]> fit(double[][] data, Options options) {
        if (options.l >= options.k) {
            return nystrom(data, options);
        } else {
            double[][] Y = embed(data, options.k, options.sigma);
            return KMeans.fit(Y, new Clustering.Options(options.k, options.maxIter, options.tol, options.controller));
        }
    }

    /**
     * Spectral clustering with Nystrom approximation.
     * @param data the input data of which each row is an observation.
     * @param options the hyperparameters.
     * @return the model.
     */
    public static CentroidClustering<double[], double[]> nystrom(double[][] data, Options options) {
        int n = data.length;
        int k = options.k;
        int l = options.l;
        double sigma = options.sigma;
        double gamma = -0.5 / (sigma * sigma);

        if (l < k || l >= n) {
            throw new IllegalArgumentException("Invalid number of random samples: " + l);
        }

        int[] index = MathEx.permutate(n);
        double[][] x = new double[n][];
        for (int i = 0; i < n; i++) {
            x[i] = data[index[i]];
        }

        Matrix C = new Matrix(n, l);
        double[] D = new double[n];

        IntStream.range(0, n).parallel().forEach(i -> {
            for (int j = 0; j < n; j++) {
                if (i != j) {
                    double w = Math.exp(gamma * MathEx.squaredDistance(x[i], x[j]));
                    D[i] += w;
                    if (j < l) {
                        C.set(i, j, w);
                    }
                }
            }
        });

        for (int i = 0; i < n; i++) {
            if (D[i] < 1E-4) {
                logger.error("Small D[{}] = {}. The data may contain outliers.", i, D[i]);
            }
            
            D[i] = 1.0 / Math.sqrt(D[i]);
        }
        
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < l; j++) {
                C.set(i, j, D[i] * C.get(i, j) * D[j]);
            }
        }

        Matrix W = C.submatrix(0, 0, l-1, l-1);
        W.uplo(UPLO.LOWER);
        Matrix.EVD eigen = ARPACK.syev(W, ARPACK.SymmOption.LA, k);
        double[] e = eigen.wr;
        double scale = Math.sqrt((double)l / n);
        for (int i = 0; i < k; i++) {
            if (e[i] <= 1E-8) {
                throw new IllegalStateException("Non-positive eigen value: " + e[i]);
            }
            
            e[i] = scale / e[i];
        }
        
        Matrix U = eigen.Vr;
        for (int i = 0; i < l; i++) {
            for (int j = 0; j < k; j++) {
                U.mul(i, j, e[j]);
            }
        }
        
        double[][] Y = C.mm(U).toArray();
        for (int i = 0; i < n; i++) {
            MathEx.unitize2(Y[i]);
        }

        double[][] features = new double[n][];
        for (int i = 0; i < n; i++) {
            features[index[i]] = Y[i];
        }
        return KMeans.fit(features, new Clustering.Options(k, options.maxIter, options.tol, options.controller));
    }

    /**
     * Returns the embedding for spectral clustering.
     * @param W the adjacency matrix of graph, which will be modified.
     * @param d the dimension of feature space.
     * @return the embedding.
     */
    public static double[][] embed(Matrix W, int d) {
        int n = W.nrow();
        double[] D = W.colSums();
        for (int i = 0; i < n; i++) {
            if (D[i] == 0.0) {
                throw new IllegalArgumentException("Isolated vertex: " + i);
            }

            D[i] = 1.0 / Math.sqrt(D[i]);
        }

        for (int i = 0; i < n; i++) {
            for (int j = 0; j < i; j++) {
                double w = D[i] * W.get(i, j) * D[j];
                W.set(i, j, w);
                W.set(j, i, w);
            }
        }

        W.uplo(UPLO.LOWER);
        Matrix.EVD eigen = ARPACK.syev(W, ARPACK.SymmOption.LA, d);
        double[][] Y = eigen.Vr.toArray();
        for (int i = 0; i < n; i++) {
            MathEx.unitize2(Y[i]);
        }

        return Y;
    }

    /**
     * Returns the embedding for spectral clustering.
     * @param data the input data of which each row is an observation.
     * @param d the dimension of feature space.
     * @param sigma the smooth/width parameter of Gaussian kernel.
     * @return the embedding.
     */
    public static double[][] embed(double[][] data, int d, double sigma) {
        int n = data.length;
        double gamma = -0.5 / (sigma * sigma);

        Matrix W = new Matrix(n, n);
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < i; j++) {
                double w = Math.exp(gamma * MathEx.squaredDistance(data[i], data[j]));
                W.set(i, j, w);
                W.set(j, i, w);
            }
        }

        return embed(W, d);
    }

    /**
     * Returns the embedding for the nonnegative count data with cosine similarity.
     * @param data the nonnegative count matrix.
     * @param p the number of features.
     * @param d the dimension of feature space.
     * @return the embedding.
     */
    public static double[][] embed(SparseIntArray[] data, int p, int d) {
        int n = data.length;

        double[] idf = new double[p];
        for (int i = 0; i < n; i++) {
            data[i].forEach((j, count) -> idf[j] += count > 0 ? 1 : 0);
        }
        for (int j = 0; j < p; j++) {
            idf[j] = Math.log(n / (1 + idf[j]));
        }

        double[] x = new double[p];
        double[] D = new double[n];
        SparseArray[] X = new SparseArray[n];
        for (int i = 0; i < n; i++) {
            Arrays.fill(x, 0.0);
            data[i].forEach((j, count) -> {
                if (count > 0) x[j] = count / idf[j];
            });
            MathEx.normalize(x);
            SparseArray Xi = new SparseArray(data[i].size());
            for (int j = 0; j < p; j++) {
                if (x[j] > 0) {
                    Xi.set(j, x[j]);
                }
            }
            X[i] = Xi;
        }

        for (int i = 0; i < n; i++) {
            double Di = -1;
            for (int j = 0; j < n; j++) {
                Di += MathEx.dot(X[i], X[j]);
            }

            D[i] = Di;
            double di = Math.sqrt(Di);
            X[i].update((j, xj) -> xj / di);
        }

        var W = new CountMatrix(SparseDataset.of(X, p).toMatrix(), D);
        Matrix.EVD eigen = ARPACK.syev(W, ARPACK.SymmOption.LA, d);
        double[][] Y = eigen.Vr.toArray();
        for (int i = 0; i < n; i++) {
            MathEx.unitize2(Y[i]);
        }

        return Y;
    }

    /**
     * Normalized feature count matrix.
     */
    static class CountMatrix extends IMatrix {
        /** The design matrix. */
        final IMatrix X;
        final double[] D;
        final double[] x;
        final double[] ax;
        final double[] y;

        /**
         * Constructor.
         */
        CountMatrix(IMatrix X, double[] D) {
            this.X = X;
            this.D = D;

            int n = X.nrow();
            int p = X.ncol();
            x = new double[n];
            y = new double[n];
            ax = new double[p];
        }

        @Override
        public int nrow() {
            return X.nrow();
        }

        @Override
        public int ncol() {
            return X.nrow();
        }

        @Override
        public long size() {
            return X.size();
        }

        @Override
        public void mv(double[] x, double[] y) {
            X.tv(x, ax);
            X.mv(ax, y);

            for (int i = 0; i < y.length; i++) {
                y[i] -= x[i] / D[i];
            }
        }

        @Override
        public void tv(double[] x, double[] y) {
            mv(x, y);
        }

        @Override
        public void mv(Transpose trans, double alpha, double[] x, double beta, double[] y) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void mv(double[] work, int inputOffset, int outputOffset) {
            System.arraycopy(work, inputOffset, x, 0, x.length);
            X.tv(work, ax);
            X.mv(ax, y);

            for (int i = 0; i < y.length; i++) {
                y[i] -= x[i] / D[i];
            }
            System.arraycopy(y, 0, work, outputOffset, y.length);
        }

        @Override
        public void tv(double[] work, int inputOffset, int outputOffset) {
            throw new UnsupportedOperationException();
        }
    }
}
