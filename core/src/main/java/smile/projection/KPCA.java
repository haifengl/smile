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

package smile.projection;

import java.io.Serializable;
import java.util.Arrays;
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
 * @see PCA
 * @see smile.manifold.IsoMap
 * @see smile.manifold.LLE
 * @see smile.manifold.LaplacianEigenmap
 * @see smile.manifold.SammonMapping
 *
 * @author Haifeng Li
 */
public class KPCA<T> implements Projection<T>, Serializable {
    private static final long serialVersionUID = 2L;

    /**
     * The dimension of feature space.
     */
    private final int p;
    /**
     * Learning data.
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
        this.p = projection.nrow();
    }

    /**
     * Fits kernel principal component analysis.
     * @param data training data.
     * @param kernel Mercer kernel.
     * @param k choose up to k principal components (larger than 0.0001) used for projection.
     * @param <T> the data type of samples.
     * @return the model.
     */
    public static <T> KPCA<T> fit(T[] data, MercerKernel<T> kernel, int k) {
        return fit(data, kernel, k, 0.0001);
    }

    /**
     * Fits kernel principal component analysis.
     * @param data training data.
     * @param kernel Mercer kernel.
     * @param k choose top k principal components used for projection.
     * @param threshold only principal components with eigenvalues
     *                  larger than the given threshold will be kept.
     * @param <T> the data type of samples.
     * @return the model.
     */
    public static <T> KPCA<T> fit(T[] data, MercerKernel<T> kernel, int k, double threshold) {
        if (threshold < 0) {
            throw new IllegalArgumentException("Invalid threshold = " + threshold);
        }

        if (k < 1 || k > data.length) {
            throw new IllegalArgumentException("Invalid dimension of feature space: " + k);
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
        Matrix.EVD eigen = ARPACK.syev(K, ARPACK.SymmOption.LA, k);

        double[] eigvalues = eigen.wr;
        Matrix eigvectors = eigen.Vr;

        int p = (int) Arrays.stream(eigvalues).limit(k).filter(e -> e/n > threshold).count();

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
    public double[] project(T x) {
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

    @Override
    public double[][] project(T[] x) {
        int m = x.length;
        int n = data.length;
        double[][] y = new double[m][n];
        for (int i = 0; i < m; i++) {
            for (int j = 0; j < n; j++) {
                y[i][j] = kernel.k(x[i], data[j]);
            }

            double my = MathEx.mean(y[i]);
            for (int j = 0; j < n; j++) {
                y[i][j] = y[i][j] - my - mean[j] + mu;
            }

        }

        double[][] z = new double[x.length][p];
        for (int i = 0; i < y.length; i++) {
            projection.mv(y[i], z[i]);
        }
        return z;
    }
}
