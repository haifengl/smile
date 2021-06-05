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
 * Principal component analysis. PCA is an orthogonal
 * linear transformation that transforms a number of possibly correlated
 * variables into a smaller number of uncorrelated variables called principal
 * components. The first principal component accounts for as much of the
 * variability in the data as possible, and each succeeding component accounts
 * for as much of the remaining variability as possible. PCA is theoretically
 * the optimum transform for given data in least square terms.
 * PCA can be thought of as revealing the internal structure of the data in
 * a way which best explains the variance in the data. If a multivariate
 * dataset is visualized as a set of coordinates in a high-dimensional data
 * space, PCA supplies the user with a lower-dimensional picture when viewed
 * from its (in some sense) most informative viewpoint.
 * <p>
 * PCA is mostly used as a tool in exploratory data analysis and for making
 * predictive models. PCA involves the calculation of the eigenvalue
 * decomposition of a data covariance matrix or singular value decomposition
 * of a data matrix, usually after mean centering the data for each attribute.
 * The results of a PCA are usually discussed in terms of component scores and
 * loadings.
 * <p>
 * As a linear technique, PCA is built for several purposes: first, it enables us to
 * decorrelate the original variables; second, to carry out data compression,
 * where we pay decreasing attention to the numerical accuracy by which we
 * encode the sequence of principal components; third, to reconstruct the
 * original input data using a reduced number of variables according to a
 * least-squares criterion; and fourth, to identify potential clusters in the data.
 * <p>
 * In certain applications, PCA can be misleading. PCA is heavily influenced
 * when there are outliers in the data. In other situations, the linearity
 * of PCA may be an obstacle to successful data reduction and compression.
 *
 * @see KPCA
 * @see ProbabilisticPCA
 * @see GHA
 * 
 * @author Haifeng Li
 */
public class PCA implements LinearProjection, Serializable {
    private static final long serialVersionUID = 2L;

    /**
     * The dimension of feature space.
     */
    private int p;
    /**
     * The dimension of input space.
     */
    private final int n;
    /**
     * The sample mean.
     */
    private final double[] mu;
    /**
     * The projected sample mean.
     */
    private double[] pmu;
    /**
     * The matrix of variable loadings, whose columns contain the eigenvectors.
     */
    private final Matrix eigvectors;
    /**
     * Eigenvalues of principal components.
     */
    private final double[] eigvalues;
    /**
     * The proportion of variance contained in each principal component.
     */
    private final double[] proportion;
    /**
     * The cumulative proportion of variance contained in principal components.
     */
    private final double[] cumulativeProportion;
    /**
     * Projection matrix.
     */
    private Matrix projection;

    /**
     * Constructor.
     * @param mu the mean of samples.
     * @param eigvalues the eigen values of principal components.
     * @param loadings the matrix of variable loadings.
     */
    public PCA(double[] mu, double[] eigvalues, Matrix loadings) {
        this.mu = mu;
        this.eigvalues = eigvalues;
        this.eigvectors = loadings;
        this.n = mu.length;

        proportion = eigvalues.clone();
        MathEx.unitize1(proportion);

        cumulativeProportion = new double[eigvalues.length];
        cumulativeProportion[0] = proportion[0];
        for (int i = 1; i < eigvalues.length; i++) {
            cumulativeProportion[i] = cumulativeProportion[i - 1] + proportion[i];
        }

        setProjection(0.95);
    }

    /**
     * Fits principal component analysis with covariance matrix.
     * @param data training data of which each row is a sample.
     *             If the sample size is larger than the data
     *             dimension and cor = false, SVD is employed for
     *             efficiency. Otherwise, eigen decomposition on
     *             covariance or correlation matrix is performed.
     * @return the model.
     */
    public static PCA fit(double[][] data) {
        int m = data.length;
        int n = data[0].length;

        double[] mu = MathEx.colMeans(data);
        Matrix X = Matrix.of(data);
        for (int j = 0; j < n; j++) {
            for (int i = 0; i < m; i++) {
                X.sub(i, j, mu[j]);
            }
        }

        double[] eigvalues;
        Matrix eigvectors;
        if (m > n) {
            Matrix.SVD svd = X.svd(true, true);
            eigvalues = svd.s;
            for (int i = 0; i < eigvalues.length; i++) {
                eigvalues[i] *= eigvalues[i];
            }

            eigvectors = svd.V;
        } else {

            Matrix cov = new Matrix(n, n);
            for (int k = 0; k < m; k++) {
                for (int i = 0; i < n; i++) {
                    for (int j = 0; j <= i; j++) {
                        cov.add(i, j, X.get(k, i) * X.get(k, j));
                    }
                }
            }

            for (int i = 0; i < n; i++) {
                for (int j = 0; j <= i; j++) {
                    cov.div(i, j, m); // divide m instead of m-1 for S-PLUS compatibility
                    cov.set(j, i, cov.get(i, j));
                }
            }

            cov.uplo(UPLO.LOWER);
            Matrix.EVD eigen = cov.eigen(false, true, true).sort();

            eigvalues = eigen.wr;
            eigvectors = eigen.Vr;
        }

        return new PCA(mu, eigvalues, eigvectors);
    }

    /**
     * Fits principal component analysis with correlation matrix.
     * @param data training data of which each row is a sample.
     *             If the sample size is larger than the data
     *             dimension and cor = false, SVD is employed for
     *             efficiency. Otherwise, eigen decomposition on
     *             covariance or correlation matrix is performed.
     * @return the model.
     */
    public static PCA cor(double[][] data) {
        int m = data.length;
        int n = data[0].length;

        double[] mu = MathEx.colMeans(data);
        Matrix x = Matrix.of(data);
        for (int j = 0; j < n; j++) {
            for (int i = 0; i < m; i++) {
                x.sub(i, j, mu[j]);
            }
        }

        Matrix cov = new Matrix(n, n);
        for (int k = 0; k < m; k++) {
            for (int i = 0; i < n; i++) {
                for (int j = 0; j <= i; j++) {
                    cov.add(i, j, x.get(k, i) * x.get(k, j));
                }
            }
        }

        for (int i = 0; i < n; i++) {
            for (int j = 0; j <= i; j++) {
                cov.div(i, j, m); // divide m instead of m-1 for S-PLUS compatibility
                cov.set(j, i, cov.get(i, j));
            }
        }

        double[] sd = new double[n];
        for (int i = 0; i < n; i++) {
            sd[i] = Math.sqrt(cov.get(i, i));
        }

        for (int i = 0; i < n; i++) {
            for (int j = 0; j <= i; j++) {
                cov.div(i, j, sd[i] * sd[j]);
                cov.set(j, i, cov.get(i, j));
            }
        }

        cov.uplo(UPLO.LOWER);
        Matrix.EVD eigen = cov.eigen(false, true, true).sort();

        Matrix loadings = eigen.Vr;
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                loadings.div(i, j, sd[i]);
            }
        }

        return new PCA(mu, eigen.wr, loadings);
    }

    /**
     * Returns the center of data.
     * @return the center of data.
     */
    public double[] center() {
        return mu;
    }

    /**
     * Returns the variable loading matrix, ordered from largest to smallest
     * by corresponding eigenvalues. The matrix columns contain the eigenvectors.
     * @return the variable loading matrix.
     */
    public Matrix loadings() {
        return eigvectors;
    }

    /**
     * Returns the principal component variances, ordered from largest to smallest,
     * which are the eigenvalues of the covariance or correlation matrix of learning data.
     * @return the principal component variances.
     */
    public double[] variance() {
        return eigvalues;
    }

    /**
     * Returns the proportion of variance contained in each principal component,
     * ordered from largest to smallest.
     * @return the proportion of variance contained in each principal component.
     */
    public double[] varianceProportion() {
        return proportion;
    }

    /**
     * Returns the cumulative proportion of variance contained in principal components,
     * ordered from largest to smallest.
     * @return the cumulative proportion of variance.
     */
    public double[] cumulativeVarianceProportion() {
        return cumulativeProportion;
    }

    @Override
    public Matrix projection() {
        return projection;
    }

    /**
     * Set the projection matrix with given number of principal components.
     * @param p choose top p principal components used for projection.
     */
    public void setProjection(int p) {
        if (p < 1 || p > n) {
            throw new IllegalArgumentException("Invalid dimension of feature space: " + p);
        }

        this.p = p;
        projection = new Matrix(p, n);
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < p; j++) {
                projection.set(j, i, eigvectors.get(i, j));
            }
        }

        pmu = projection.mv(mu);
    }

    /**
     * Set the projection matrix with top principal components that contain
     * (more than) the given percentage of variance.
     * @param p the required percentage of variance.
     */
    public void setProjection(double p) {
        if (p <= 0 || p > 1) {
            throw new IllegalArgumentException("Invalid percentage of variance: " + p);
        }

        for (int k = 0; k < n; k++) {
            if (cumulativeProportion[k] >= p) {
                setProjection(k + 1);
                break;
            }
        }
    }

    @Override
    public double[] project(double[] x) {
        if (x.length != n) {
            throw new IllegalArgumentException(String.format("Invalid input vector size: %d, expected: %d", x.length, n));
        }

        double[] y = projection.mv(x);
        MathEx.sub(y, pmu);
        return y;
    }

    @Override
    public double[][] project(double[][] x) {
        if (x[0].length != mu.length) {
            throw new IllegalArgumentException(String.format("Invalid input vector size: %d, expected: %d", x[0].length, n));
        }

        double[][] y = new double[x.length][p];
        for (int i = 0; i < x.length; i++) {
            projection.mv(x[i], y[i]);
            MathEx.sub(y[i], pmu);
        }
        return y;
    }
}
