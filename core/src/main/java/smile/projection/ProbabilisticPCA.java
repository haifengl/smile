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
import smile.math.MathEx;
import smile.math.blas.UPLO;
import smile.math.matrix.Matrix;

/**
 * Probabilistic principal component analysis. Probabilistic PCA is
 * a simplified factor analysis that employs a latent variable model
 * with linear relationship:
 * <pre>
 *     y &sim; W * x + &mu; + &epsilon;
 * </pre>
 * where latent variables <code>x &sim; N(0, I)</code>, error (or noise)
 * <code>&epsilon; &sim; N(0, &Psi;)</code>, and &mu; is the location
 * term (mean). In probabilistic PCA, an isotropic noise model is used,
 * i.e., noise variances constrained to be equal
 * (<code>&Psi;<sub>i</sub> = &sigma;<sup>2</sup></code>).
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
public class ProbabilisticPCA implements LinearProjection, Serializable {
    private static final long serialVersionUID = 2L;

    /**
     * The sample mean.
     */
    private final double[] mu;
    /**
     * The projected sample mean.
     */
    private final double[] pmu;
    /**
     * The variance of noise part.
     */
    private final double noise;
    /**
     * The loading matrix.
     */
    private final Matrix loading;
    /**
     * The projection matrix.
     */
    private final Matrix projection;

    /**
     * Constructor.
     * @param noise the variance of noise.
     * @param mu the mean of samples.
     * @param loading the loading matrix.
     * @param projection the projection matrix.
     */
    public ProbabilisticPCA(double noise, double[] mu, Matrix loading, Matrix projection) {
        this.noise = noise;
        this.mu = mu;
        this.loading = loading;
        this.projection = projection;

        pmu = new double[projection.nrow()];
        projection.mv(mu, pmu);
    }

    /**
     * Returns the variable loading matrix, ordered from largest to smallest
     * by corresponding eigenvalues.
     * @return the variable loading matrix.
     */
    public Matrix loadings() {
        return loading;
    }

    /**
     * Returns the center of data.
     * @return the center of data.
     */
    public double[] center() {
        return mu;
    }

    /**
     * Returns the variance of noise.
     * @return the variance of noise.
     */
    public double variance() {
        return noise;
    }

    /**
     * Returns the projection matrix. Note that this is not the matrix W in the
     * latent model.
     */
    @Override
    public Matrix projection() {
        return projection;
    }

    @Override
    public double[] project(double[] x) {
        if (x.length != mu.length) {
            throw new IllegalArgumentException(String.format("Invalid input vector size: %d, expected: %d", x.length, mu.length));
        }

        double[] y = new double[projection.nrow()];
        projection.mv(x, y);
        MathEx.sub(y, pmu);
        return y;
    }

    @Override
    public double[][] project(double[][] x) {
        if (x[0].length != mu.length) {
            throw new IllegalArgumentException(String.format("Invalid input vector size: %d, expected: %d", x[0].length, mu.length));
        }

        double[][] y = new double[x.length][projection.nrow()];
        for (int i = 0; i < x.length; i++) {
            projection.mv(x[i], y[i]);
            MathEx.sub(y[i], pmu);
        }
        return y;
    }

    /**
     * Fits probabilistic principal component analysis.
     * @param data training data of which each row is a sample.
     * @param k the number of principal component to learn.
     * @return the model.
     */
    public static ProbabilisticPCA fit(double[][] data, int k) {
        int m = data.length;
        int n = data[0].length;

        double[] mu = MathEx.colMeans(data);
        Matrix cov = new Matrix(n, n);
        for (double[] datum : data) {
            for (int i = 0; i < n; i++) {
                for (int j = 0; j <= i; j++) {
                    cov.add(i, j, (datum[i] - mu[i]) * (datum[j] - mu[j]));
                }
            }
        }

        for (int i = 0; i < n; i++) {
            for (int j = 0; j <= i; j++) {
                cov.div(i, j, m);
                cov.set(j, i, cov.get(i, j));
            }
        }

        cov.uplo(UPLO.LOWER);
        Matrix.EVD eigen = cov.eigen(false, true, true).sort();
        double[] eigvalues = eigen.wr;
        Matrix eigvectors = eigen.Vr;

        double noise = 0.0;
        for (int i = k; i < n; i++) {
            noise += eigvalues[i];
        }
        noise /= (n - k);

        Matrix loading = new Matrix(n, k);
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < k; j++) {
                loading.set(i, j, eigvectors.get(i, j) * Math.sqrt(eigvalues[j] - noise));
            }
        }

        Matrix M = loading.ata();
        M.addDiag(noise);

        Matrix.Cholesky chol = M.cholesky(true);
        Matrix Mi = chol.inverse();
        Matrix projection = Mi.mt(loading);

        return new ProbabilisticPCA(noise, mu, loading, projection);
    }
}
