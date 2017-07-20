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
package smile.projection;

import java.io.Serializable;
import smile.math.Math;
import smile.math.kernel.MercerKernel;
import smile.math.matrix.Matrix;
import smile.math.matrix.DenseMatrix;
import smile.math.matrix.EVD;

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
 * @see smile.mds.SammonMapping
 *
 * @author Haifeng Li
 */
public class KPCA<T> implements Projection<T>, Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * The dimension of feature space.
     */
    private int p;
    /**
     * Learning data.
     */
    private T[] data;
    /**
     * Mercer kernel.
     */
    private MercerKernel<T> kernel;
    /**
     * The row mean of kernel matrix.
     */
    private double[] mean;
    /**
     * The mean of kernel matrix.
     */
    private double mu;
    /**
     * Eigenvalues of kernel principal components.
     */
    private double[] latent;
    /**
     * Projection matrix.
     */
    private DenseMatrix projection;
    /**
     * The coordinates of projected training data.
     */
    private double[][] coordinates;

    /**
     * Constructor. Learn kernel principal component analysis.
     * @param data learning data.
     * @param kernel Mercer kernel to compute kernel matrix.
     * @param threshold only principal components with eigenvalues larger than
     * the given threshold will be kept.
     */
    public KPCA(T[] data, MercerKernel<T> kernel, double threshold) {
        this(data, kernel, data.length, threshold);
    }

    /**
     * Constructor. Learn kernel principal component analysis.
     * @param data learning data.
     * @param kernel Mercer kernel to compute kernel matrix.
     * @param k choose upto k principal components (larger than 0.0001) used for projection.
     */
    public KPCA(T[] data, MercerKernel<T> kernel, int k) {
        this(data, kernel, k, 0.0001);
    }

    /**
     * Constructor. Constructor. Learn kernel principal component analysis.
     * @param data learning data.
     * @param kernel Mercer kernel to compute kernel matrix.
     * @param k choose top k principal components used for projection.
     * @param threshold only principal components with eigenvalues larger than
     * the given threshold will be kept.
     */
    public KPCA(T[] data, MercerKernel<T> kernel, int k, double threshold) {
        if (threshold < 0) {
            throw new IllegalArgumentException("Invalid threshold = " + threshold);
        }

        if (k < 1 || k > data.length) {
            throw new IllegalArgumentException("Invalid dimension of feature space: " + k);
        }

        this.data = data;
        this.kernel = kernel;
        int n = data.length;

        DenseMatrix K = Matrix.zeros(n, n);
        for (int i = 0; i < n; i++) {
            for (int j = 0; j <= i; j++) {
                double x = kernel.k(data[i], data[j]);
                K.set(i, j, x);
                K.set(j, i, x);
            }
        }

        mean = K.rowMeans();
        mu = Math.mean(mean);

        for (int i = 0; i < n; i++) {
            for (int j = 0; j <= i; j++) {
                double x = K.get(i, j) - mean[i] - mean[j] + mu;
                K.set(i, j, x);
                K.set(j, i, x);
            }
        }

        K.setSymmetric(true);
        EVD eigen = K.eigen(k);

        p = 0;
        for (int i = 0; i < k; i++) {
            double e = (eigen.getEigenValues()[i] /= n);
            if (e > threshold) {
                p++;
            } else {
                break;
            }
        }

        latent = new double[p];
        projection = Matrix.zeros(p, n);
        for (int j = 0; j < p; j++) {
            latent[j] = eigen.getEigenValues()[j];
            double s = Math.sqrt(latent[j]);
            for (int i = 0; i < n; i++) {
                projection.set(j, i, eigen.getEigenVectors().get(i, j) / s);
            }
        }

        DenseMatrix coord = projection.abmm(K);
        coordinates = new double[n][p];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < p; j++) {
                coordinates[i][j] = coord.get(j, i);
            }
        }
    }

    /**
     * Returns the eigenvalues of kernel principal components, ordered from largest to smallest.
     */
    public double[] getVariances() {
        return latent;
    }

    /**
     * Returns the projection matrix. The dimension reduced data can be obtained
     * by y = W * K(x, &middot;).
     */
    public DenseMatrix getProjection() {
        return projection;
    }

    /**
     * Returns the nonlinear principal component scores, i.e., the representation
     * of learning data in the nonlinear principal component space. Rows
     * correspond to observations, columns to components.
     */
    public double[][] getCoordinates() {
        return coordinates;
    }

    @Override
    public double[] project(T x) {
        int n = data.length;

        double[] y = new double[n];
        for (int i = 0; i < n; i++) {
            y[i] = kernel.k(x, data[i]);
        }

        double my = Math.mean(y);
        for (int i = 0; i < n; i++) {
            y[i] = y[i] - my - mean[i] + mu;
        }

        double[] z = new double[p];
        projection.ax(y, z);
        return z;
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

            double my = Math.mean(y[i]);
            for (int j = 0; j < n; j++) {
                y[i][j] = y[i][j] - my - mean[j] + mu;
            }

        }

        double[][] z = new double[x.length][p];
        for (int i = 0; i < y.length; i++) {
            projection.ax(y[i], z[i]);
        }
        return z;
    }
}
