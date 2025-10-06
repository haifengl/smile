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
package smile.feature.extraction;

import java.io.Serial;
import smile.data.DataFrame;
import smile.math.MathEx;
import smile.tensor.DenseMatrix;
import smile.tensor.EVD;
import smile.tensor.SVD;
import smile.tensor.Vector;
import static smile.linalg.UPLO.*;

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
 * @see KernelPCA
 * @see ProbabilisticPCA
 * @see GHA
 * 
 * @author Haifeng Li
 */
public class PCA extends Projection {
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
     * The matrix of variable loadings, whose columns contain the eigenvectors.
     */
    private final DenseMatrix eigvectors;
    /**
     * Eigenvalues of principal components.
     */
    private final Vector eigvalues;
    /**
     * The proportion of variance contained in each principal component.
     */
    private final Vector proportion;
    /**
     * The cumulative proportion of variance contained in principal components.
     */
    private final Vector cumulativeProportion;

    /**
     * Constructor.
     * @param mu the mean of samples.
     * @param eigvalues the eigen values of principal components.
     * @param loadings the matrix of variable loadings.
     * @param projection the projection matrix.
     * @param columns the columns to transform when applied on Tuple/DataFrame.
     */
    public PCA(Vector mu, Vector eigvalues, DenseMatrix loadings, DenseMatrix projection, String... columns) {
        super(projection, "PCA", columns);

        this.mu = mu;
        this.eigvalues = eigvalues;
        this.eigvectors = loadings;

        proportion = eigvalues.copy();
        double norm1 = proportion.norm1();
        proportion.scale(1 / norm1);

        cumulativeProportion = proportion.copy();
        for (int i = 1; i < proportion.size(); i++) {
            cumulativeProportion.set(i, cumulativeProportion.get(i-1) + proportion.get(i));
        }

        pmu = projection.mv(mu);
    }

    /**
     * Fits principal component analysis with covariance matrix.
     * @param data training data of which each row is a sample.
     * @param columns the columns to fit PCA. If empty, all columns
     *                will be used.
     * @return the model.
     */
    public static PCA fit(DataFrame data, String... columns) {
        double[][] x = data.toArray(columns);
        return fit(x, columns);
    }

    /**
     * Fits principal component analysis with correlation matrix.
     * @param data training data of which each row is a sample.
     * @param columns the columns to fit PCA. If empty, all columns
     *                will be used.
     * @return the model.
     */
    public static PCA cor(DataFrame data, String... columns) {
        double[][] x = data.toArray(columns);
        return cor(x, columns);
    }

    /**
     * Fits principal component analysis with covariance matrix.
     * @param data training data of which each row is a sample.
     * @param columns the columns to transform when applied on Tuple/DataFrame.
     * @return the model.
     */
    public static PCA fit(double[][] data, String... columns) {
        int m = data.length;
        int n = data[0].length;

        DenseMatrix X = DenseMatrix.of(data);
        Vector mu = X.colMeans();
        for (int j = 0; j < n; j++) {
            for (int i = 0; i < m; i++) {
                X.sub(i, j, mu.get(j));
            }
        }

        Vector eigvalues;
        DenseMatrix eigvectors;
        if (m > n) {
            SVD svd = X.svd();
            eigvalues = svd.s();
            for (int i = 0; i < eigvalues.size(); i++) {
                double si = eigvalues.get(i);
                eigvalues.set(i, si * si);
            }

            eigvectors = svd.Vt().transpose();
        } else {

            DenseMatrix cov = X.zeros(n, n);
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

            cov.withUplo(LOWER);
            EVD eigen = cov.eigen().sort();

            eigvalues = eigen.wr();
            eigvectors = eigen.Vr();
        }

        DenseMatrix projection = getProjection(eigvalues, eigvectors, 0.95);
        return new PCA(mu, eigvalues, eigvectors, projection, columns);
    }

    /**
     * Fits principal component analysis with correlation matrix.
     * @param data training data of which each row is a sample.
     * @param columns the columns to transform when applied on Tuple/DataFrame.
     * @return the model.
     */
    public static PCA cor(double[][] data, String... columns) {
        int m = data.length;
        int n = data[0].length;

        DenseMatrix X = DenseMatrix.of(data);
        Vector mu = X.colMeans();
        for (int j = 0; j < n; j++) {
            for (int i = 0; i < m; i++) {
                X.sub(i, j, mu.get(j));
            }
        }

        DenseMatrix cov = X.zeros(n, n);
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

        cov.withUplo(LOWER);
        EVD eigen = cov.eigen().sort();

        DenseMatrix loadings = eigen.Vr();
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                loadings.div(i, j, sd[i]);
            }
        }

        DenseMatrix projection = getProjection(eigen.wr(), loadings, 0.95);
        return new PCA(mu, eigen.wr(), loadings, projection, columns);
    }

    /**
     * Returns the center of data.
     * @return the center of data.
     */
    public Vector center() {
        return mu;
    }

    /**
     * Returns the variable loading matrix, ordered from largest to smallest
     * by corresponding eigenvalues. The matrix columns contain the eigenvectors.
     * @return the variable loading matrix.
     */
    public DenseMatrix loadings() {
        return eigvectors;
    }

    /**
     * Returns the principal component variances, ordered from largest to smallest,
     * which are the eigenvalues of the covariance or correlation matrix of learning data.
     * @return the principal component variances.
     */
    public Vector variance() {
        return eigvalues;
    }

    /**
     * Returns the proportion of variance contained in each principal component,
     * ordered from largest to smallest.
     * @return the proportion of variance contained in each principal component.
     */
    public Vector varianceProportion() {
        return proportion;
    }

    /**
     * Returns the cumulative proportion of variance contained in principal components,
     * ordered from largest to smallest.
     * @return the cumulative proportion of variance.
     */
    public Vector cumulativeVarianceProportion() {
        return cumulativeProportion;
    }

    /**
     * Returns the projection matrix with top principal components that contain
     * (more than) the given percentage of variance.
     *
     * @param eigvalues the eigen values of principal components.
     * @param loadings the matrix of variable loadings.
     * @param p the required percentage of variance.
     * @return the projection matrix.
     */
    private static DenseMatrix getProjection(Vector eigvalues, DenseMatrix loadings, double p) {
        if (p <= 0.0 || p > 1.0) {
            throw new IllegalArgumentException("Invalid percentage of variance: " + p);
        }

        double[] proportion = eigvalues.toArray(new double[0]);
        MathEx.unitize1(proportion);

        int k = 0;
        double sum = 0.0;
        for (; k < proportion.length; k++) {
            sum += proportion[k];
            if (sum >= p) {
                break;
            }
        }

        return getProjection(loadings, k+1);
    }

    /**
     * Returns the projection matrix with given number of principal components.
     *
     * @param loadings the matrix of variable loadings.
     * @param p the required percentage of variance.
     * @return the projection matrix.
     */
    private static DenseMatrix getProjection(DenseMatrix loadings, int p) {
        int n = loadings.nrow();
        if (p < 1 || p > n) {
            throw new IllegalArgumentException("Invalid dimension of feature space: " + p);
        }

        DenseMatrix projection = loadings.zeros(p, n);
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < p; j++) {
                projection.set(j, i, loadings.get(i, j));
            }
        }

        return projection;
    }

    /**
     * Returns the projection with given number of principal components.
     * @param p choose top p principal components used for projection.
     * @return a new PCA projection.
     */
    public PCA getProjection(int p) {
        DenseMatrix projection = getProjection(eigvectors, p);
        return new PCA(mu, eigvalues, eigvectors, projection, columns);
    }

    /**
     * Returns the projection with top principal components that contain
     * (more than) the given percentage of variance.
     * @param p the required percentage of variance.
     * @return a new PCA projection.
     */
    public PCA getProjection(double p) {
        if (p <= 0.0 || p > 1.0) {
            throw new IllegalArgumentException("Invalid percentage of variance: " + p);
        }

        int k = 0;
        for (; k < cumulativeProportion.size(); k++) {
            if (cumulativeProportion.get(k) >= p) {
                break;
            }
        }

        return getProjection(k);
    }

    @Override
    protected double[] postprocess(double[] x) {
        for (int i = 0; i < x.length; i++) {
            x[i] -= pmu.get(i);
        }
        return x;
    }
}
