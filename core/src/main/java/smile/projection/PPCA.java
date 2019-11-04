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

package smile.projection;

import java.io.Serializable;
import smile.math.MathEx;
import smile.math.matrix.Matrix;
import smile.math.matrix.DenseMatrix;
import smile.math.matrix.Cholesky;
import smile.math.matrix.EVD;

/**
 * Probabilistic principal component analysis. Probabilistic PCA is
 * a simplified factor analysis that employs a latent variable model
 * with linear relationship:
 * <pre>
 *     y &sim; W * x + &mu; + &epsilon;
 * </pre>
 * where latent variables x &sim; N(0, I), error (or noise) &epsilon; &sim; N(0, &Psi;),
 * and &mu; is the location term (mean). In probabilistic PCA, an isotropic noise model is used,
 * i.e., noise variances constrained to be equal (&Psi;<sub>i</sub> = &sigma;<sup>2</sup>).
 * A close form of estimation of above parameters can be obtained
 * by maximum likelihood method.
 *
 * <h2>References</h2>
 * <ol>
 * <li>Michael E. Tipping and Christopher M. Bishop. Probabilistic Principal Component Analysis. Journal of the Royal Statistical Society. Series B (Statistical Methodology) 61(3):611-622, 1999. </li>
 * </ol>
 *
 * @see PCA
 *
 * @author Haifeng Li
 */
public class PPCA implements LinearProjection, Serializable {
    private static final long serialVersionUID = 2L;

    /**
     * The sample mean.
     */
    private double[] mu;
    /**
     * The projected sample mean.
     */
    private double[] pmu;
    /**
     * The variance of noise part.
     */
    private double noise;
    /**
     * The loading matrix.
     */
    private DenseMatrix loading;
    /**
     * The projection matrix.
     */
    private DenseMatrix projection;

    /**
     * Constructor.
     * @param noise the variance of noise.
     * @param mu the mean of samples.
     * @param loading the loading matrix.
     * @param projection the projection matrix.
     */
    public PPCA(double noise, double[] mu, DenseMatrix loading, DenseMatrix projection) {
        this.noise = noise;
        this.mu = mu;
        this.loading = loading;
        this.projection = projection;

        pmu = new double[projection.nrows()];
        projection.ax(mu, pmu);
    }

    /**
     * Returns the variable loading matrix, ordered from largest to smallest
     * by corresponding eigenvalues.
     */
    public DenseMatrix getLoadings() {
        return loading;
    }

    /**
     * Returns the center of data.
     */
    public double[] getCenter() {
        return mu;
    }

    /**
     * Returns the variance of noise.
     */
    public double getNoiseVariance() {
        return noise;
    }

    /**
     * Returns the projection matrix. Note that this is not the matrix W in the
     * latent model.
     */
    @Override
    public DenseMatrix getProjection() {
        return projection;
    }

    @Override
    public double[] project(double[] x) {
        if (x.length != mu.length) {
            throw new IllegalArgumentException(String.format("Invalid input vector size: %d, expected: %d", x.length, mu.length));
        }

        double[] y = new double[projection.nrows()];
        projection.ax(x, y);
        MathEx.sub(y, pmu);
        return y;
    }

    @Override
    public double[][] project(double[][] x) {
        if (x[0].length != mu.length) {
            throw new IllegalArgumentException(String.format("Invalid input vector size: %d, expected: %d", x[0].length, mu.length));
        }

        double[][] y = new double[x.length][projection.nrows()];
        for (int i = 0; i < x.length; i++) {
            projection.ax(x[i], y[i]);
            MathEx.sub(y[i], pmu);
        }
        return y;
    }

    /**
     * Fits probabilistic principal component analysis.
     * @param data training data of which each row is a sample.
     * @param k the number of principal component to learn.
     */
    public static PPCA fit(double[][] data, int k) {
        int m = data.length;
        int n = data[0].length;

        double[] mu = MathEx.colMeans(data);
        DenseMatrix cov = Matrix.zeros(n, n);
        for (int l = 0; l < m; l++) {
            for (int i = 0; i < n; i++) {
                for (int j = 0; j <= i; j++) {
                    cov.add(i, j, (data[l][i] - mu[i]) * (data[l][j] - mu[j]));
                }
            }
        }

        for (int i = 0; i < n; i++) {
            for (int j = 0; j <= i; j++) {
                cov.div(i, j, m);
                cov.set(j, i, cov.get(i, j));
            }
        }

        cov.setSymmetric(true);
        EVD eigen = cov.eigen();
        double[] evalues = eigen.getEigenValues();
        DenseMatrix evectors = eigen.getEigenVectors();

        double noise = 0.0;
        for (int i = k; i < n; i++) {
            noise += evalues[i];
        }
        noise /= (n - k);

        DenseMatrix loading = Matrix.zeros(n, k);
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < k; j++) {
                loading.set(i, j, evectors.get(i, j) * Math.sqrt(evalues[j] - noise));
            }
        }

        DenseMatrix M = loading.ata();
        for (int i = 0; i < k; i++) {
            M.add(i, i, noise);
        }

        Cholesky chol = M.cholesky();
        DenseMatrix Mi = chol.inverse();
        DenseMatrix projection = Mi.abtmm(loading);

        return new PPCA(noise, mu, loading, projection);
    }
}
