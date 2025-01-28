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

import java.io.Serial;
import java.io.Serializable;
import java.util.Arrays;
import java.util.Properties;
import java.util.function.Function;
import smile.math.MathEx;
import smile.math.blas.UPLO;
import smile.math.kernel.MercerKernel;
import smile.math.matrix.ARPACK;
import smile.math.matrix.Matrix;

/**
 * Kernel principal component analysis. Kernel PCA is an extension of
 * principal component analysis (PCA) using techniques of kernel methods.
 * Using a kernel, the originally linear operations of PCA are done in a
 * reproducing kernel Hilbert space with a non-linear mapping.
 * <p>
 * In practice, a large data set leads to a large Kernel/Gram matrix K, and
 * storing K may become a problem. One way to deal with this is to perform
 * clustering on your large dataset, and populate the kernel with the means
 * of those clusters. Since even this method may yield a relatively large K,
 * it is common to compute only the top P eigenvalues and eigenvectors of K.
 * <p>
 * Kernel PCA with an isotropic kernel function is closely related to metric MDS.
 * Carrying out metric MDS on the kernel matrix K produces an equivalent configuration
 * of points as the distance (2(1 - K(x<sub>i</sub>, x<sub>j</sub>)))<sup>1/2</sup>
 * computed in feature space.
 * <p>
 * Kernel PCA also has close connections with Isomap, LLE, and Laplacian eigenmaps.
 *
 * <h2>References</h2>
 * <ol>
 * <li>Bernhard Scholkopf, Alexander Smola, and Klaus-Robert Muller. Nonlinear Component Analysis as a Kernel Eigenvalue Problem. Neural Computation, 1998.</li>
 * </ol>
 *
 * @see smile.math.kernel.MercerKernel
 * @see smile.feature.extraction.PCA
 * @see smile.manifold.IsoMap
 * @see smile.manifold.LLE
 * @see smile.manifold.LaplacianEigenmap
 * @see smile.manifold.SammonMapping
 *
 * @param <T> the data type of model input objects.
 *
 * @author Haifeng Li
 */
public class KPCA<T> implements Function<T, double[]>, Serializable {
    @Serial
    private static final long serialVersionUID = 2L;

    /**
     * Kernel PCA hyper-parameters.
     * @param d the dimension of the projection.
     * @param threshold only principal components with eigenvalues
     *                  larger than the given threshold will be kept.
     */
    public record Options(int d, double threshold) {
        public Options {
            if (d < 2) {
                throw new IllegalArgumentException("Invalid dimension of feature space: " + d);
            }
            if (threshold < 0) {
                throw new IllegalArgumentException("Invalid threshold = " + threshold);
            }
        }

        /**
         * Constructor.
         * @param d the dimension of the projection.
         */
        public Options(int d) {
            this(d, 0.0001);
        }

        /**
         * Returns the persistent set of hyper-parameters.
         * @return the persistent set.
         */
        public Properties toProperties() {
            Properties props = new Properties();
            props.setProperty("smile.kpca.d", Integer.toString(d));
            props.setProperty("smile.kpca.threshold", Double.toString(threshold));
            return props;
        }

        /**
         * Returns the options from properties.
         *
         * @param props the hyper-parameters.
         * @return the options.
         */
        public static Options of(Properties props) {
            int d = Integer.parseInt(props.getProperty("smile.kpca.d", "2"));
            double threshold = Double.parseDouble(props.getProperty("smile.kpca.threshold", "0.0001"));
            return new Options(d, threshold);
        }
    }

    /**
     * Training data.
     */
    private final T[] data;
    /**
     * Mercer kernel.
     */
    private final MercerKernel<T> kernel;
    /**
     * The row mean of kernel matrix.
     */
    private final double[] mean;
    /**
     * The mean of kernel matrix.
     */
    private final double mu;
    /**
     * The eigenvalues of kernel principal components.
     */
    private final double[] latent;
    /**
     * The projection matrix.
     */
    private final Matrix projection;
    /**
     * The coordinates of projected training data.
     */
    private final double[][] coordinates;

    /**
     * Constructor.
     * @param data training data.
     * @param kernel Mercer kernel.
     * @param mean the row/column average of kernel matrix.
     * @param mu the average of kernel matrix.
     * @param coordinates the coordinates of projected training data.
     * @param latent the projection matrix.
     * @param projection the projection matrix.
     */
    public KPCA(T[] data, MercerKernel<T> kernel, double[] mean, double mu, double[][] coordinates, double[] latent, Matrix projection) {
        this.data = data;
        this.kernel = kernel;
        this.mean = mean;
        this.mu = mu;
        this.coordinates = coordinates;
        this.latent = latent;
        this.projection = projection;
    }

    /**
     * Fits kernel principal component analysis.
     * @param data training data.
     * @param kernel Mercer kernel.
     * @param options the hyper-parameters.
     * @param <T> the data type of samples.
     * @return the model.
     */
    public static <T> KPCA<T> fit(T[] data, MercerKernel<T> kernel, Options options) {
        int d = options.d;
        if (d > data.length) {
            throw new IllegalArgumentException("Invalid dimension of feature space: " + d);
        }

        int n = data.length;

        Matrix K = new Matrix(n, n);
        for (int i = 0; i < n; i++) {
            for (int j = 0; j <= i; j++) {
                double x = kernel.k(data[i], data[j]);
                K.set(i, j, x);
                K.set(j, i, x);
            }
        }

        double[] mean = K.rowMeans();
        double mu = MathEx.mean(mean);

        for (int i = 0; i < n; i++) {
            for (int j = 0; j <= i; j++) {
                double x = K.get(i, j) - mean[i] - mean[j] + mu;
                K.set(i, j, x);
                K.set(j, i, x);
            }
        }

        K.uplo(UPLO.LOWER);
        Matrix.EVD eigen = ARPACK.syev(K, ARPACK.SymmOption.LA, d);

        double[] eigvalues = eigen.wr;
        Matrix eigvectors = eigen.Vr;

        int p = (int) Arrays.stream(eigvalues).limit(d).filter(e -> e/n > options.threshold).count();

        double[] latent = new double[p];
        Matrix projection = new Matrix(p, n);
        for (int j = 0; j < p; j++) {
            latent[j] = eigvalues[j];
            double s = Math.sqrt(latent[j]);
            for (int i = 0; i < n; i++) {
                projection.set(j, i, eigvectors.get(i, j) / s);
            }
        }

        Matrix coord = projection.mm(K);
        double[][] coordinates = new double[n][p];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < p; j++) {
                coordinates[i][j] = coord.get(j, i);
            }
        }

        return new KPCA<>(data, kernel, mean, mu, coordinates, latent, projection);
    }

    /**
     * Returns the eigenvalues of kernel principal components, ordered from largest to smallest.
     * @return the eigenvalues of kernel principal components, ordered from largest to smallest.
     */
    public double[] variances() {
        return latent;
    }

    /**
     * Returns the projection matrix. The dimension reduced data can be obtained
     * by y = W * K(x, &middot;).
     * @return the projection matrix.
     */
    public Matrix projection() {
        return projection;
    }

    /**
     * Returns the nonlinear principal component scores, i.e., the representation
     * of learning data in the nonlinear principal component space. Rows
     * correspond to observations, columns to components.
     * @return the nonlinear principal component scores.
     */
    public double[][] coordinates() {
        return coordinates;
    }

    @Override
    public double[] apply(T x) {
        int n = data.length;

        double[] y = new double[n];
        for (int i = 0; i < n; i++) {
            y[i] = kernel.k(x, data[i]);
        }

        double my = MathEx.mean(y);
        for (int i = 0; i < n; i++) {
            y[i] = y[i] - my - mean[i] + mu;
        }

        return projection.mv(y);
    }

    /**
     * Project a set of data to the feature space.
     * @param x the data set.
     * @return the projection in the feature space.
     */
    public double[][] apply(T[] x) {
        int m = x.length;
        double[][] y = new double[m][];
        for (int i = 0; i < m; i++) {
            y[i] = apply(x[i]);
        }
        return y;
    }
}
