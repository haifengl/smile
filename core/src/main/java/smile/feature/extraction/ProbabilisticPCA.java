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
 * along with SMILE. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.feature.extraction;

import java.io.Serial;
import smile.data.DataFrame;
import smile.math.MathEx;
import smile.linalg.UPLO;
import smile.tensor.*;
import static smile.tensor.ScalarType.*;

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
public class ProbabilisticPCA extends Projection {
    @Serial
    private static final long serialVersionUID = 2L;

    /**
     * The sample mean.
     */
    private final Vector mu;
    /**
     * The projected sample mean.
     */
    private final Vector pmu;
    /**
     * The variance of noise part.
     */
    private final double noise;
    /**
     * The loading matrix.
     */
    private final DenseMatrix loading;

    /**
     * Constructor.
     * @param noise the variance of noise.
     * @param mu the mean of samples.
     * @param loading the loading matrix.
     * @param projection the projection matrix. Note that this is not
     *                   the matrix W in the latent model.
     * @param columns the columns to transform when applied on Tuple/DataFrame.
     */
    public ProbabilisticPCA(double noise, double[] mu, DenseMatrix loading, DenseMatrix projection, String... columns) {
        super(projection, "PPCA", columns);

        this.noise = noise;
        this.mu = Vector.column(mu);
        this.pmu = projection.mv(this.mu);
        this.loading = loading;
    }

    /**
     * Returns the variable loading matrix, ordered from largest to smallest
     * by corresponding eigenvalues.
     * @return the variable loading matrix.
     */
    public DenseMatrix loadings() {
        return loading;
    }

    /**
     * Returns the center of data.
     * @return the center of data.
     */
    public Vector center() {
        return mu;
    }

    /**
     * Returns the variance of noise.
     * @return the variance of noise.
     */
    public double variance() {
        return noise;
    }

    @Override
    protected double[] postprocess(double[] x) {
        for (int i = 0; i < x.length; i++) {
            x[i] -= pmu.get(i);
        }
        return x;
    }

    /**
     * Fits probabilistic principal component analysis.
     * @param data training data of which each row is a sample.
     * @param k the number of principal component to learn.
     * @param columns the columns to fit PCA. If empty, all columns
     *                will be used.
     * @return the model.
     */
    public static ProbabilisticPCA fit(DataFrame data, int k, String... columns) {
        double[][] x = data.toArray(columns);
        return fit(x, k, columns);
    }

    /**
     * Fits probabilistic principal component analysis.
     * @param data training data of which each row is a sample.
     * @param k the number of principal component to learn.
     * @param columns the columns to transform when applied on Tuple/DataFrame.
     * @return the model.
     */
    public static ProbabilisticPCA fit(double[][] data, int k, String... columns) {
        int m = data.length;
        int n = data[0].length;

        double[] mu = MathEx.colMeans(data);
        DenseMatrix cov = DenseMatrix.zeros(Float64, n, n);
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

        cov.withUplo(UPLO.LOWER);
        EVD eigen = cov.eigen().sort();
        Vector eigvalues = eigen.wr();
        DenseMatrix eigvectors = eigen.Vr();

        double noise = 0.0;
        for (int i = k; i < n; i++) {
            noise += eigvalues.get(i);
        }
        noise /= (n - k);

        DenseMatrix loading = DenseMatrix.zeros(Float64, n, k);
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < k; j++) {
                loading.set(i, j, eigvectors.get(i, j) * Math.sqrt(eigvalues.get(j) - noise));
            }
        }

        DenseMatrix M = loading.ata();
        for (int i = 0; i < n; i++) M.add(i, i, noise);

        Cholesky chol = M.cholesky();
        DenseMatrix Mi = chol.inverse();
        DenseMatrix projection = Mi.mt(loading);

        return new ProbabilisticPCA(noise, mu, loading, projection, columns);
    }
}
