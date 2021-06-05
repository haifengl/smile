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

package smile.classification;

import java.util.Properties;
import smile.math.MathEx;
import smile.math.matrix.Matrix;
import smile.projection.Projection;
import smile.sort.QuickSort;
import smile.util.IntSet;

/**
 * Fisher's linear discriminant. Fisher defined the separation between two
 * distributions to be the ratio of the variance between the classes to
 * the variance within the classes, which is, in some sense, a measure
 * of the signal-to-noise ratio for the class labeling. FLD finds a linear
 * combination of features which maximizes the separation after the projection.
 * The resulting combination may be used for dimensionality reduction
 * before later classification.
 * <p>
 * The terms Fisher's linear discriminant and LDA are often used
 * interchangeably, although FLD actually describes a slightly different
 * discriminant, which does not make some of the assumptions of LDA such
 * as normally distributed classes or equal class covariances.
 * When the assumptions of LDA are satisfied, FLD is equivalent to LDA.
 * <p>
 * FLD is also closely related to principal component analysis (PCA), which also
 * looks for linear combinations of variables which best explain the data.
 * As a supervised method, FLD explicitly attempts to model the
 * difference between the classes of data. On the other hand, PCA is a
 * unsupervised method and does not take into account any difference in class.
 * <p>
 * One complication in applying FLD (and LDA) to real data
 * occurs when the number of variables/features does not exceed
 * the number of samples. In this case, the covariance estimates do not have
 * full rank, and so cannot be inverted. This is known as small sample size
 * problem.
 *
 * <h2>References</h2>
 * <ol>
 * <li> H. Li, K. Zhang, and T. Jiang. Robust and Accurate Cancer Classification with Gene Expression Profiling. CSB'05, pp 310-321.</li>
 * </ol>
 *
 * @see LDA
 * @see smile.projection.PCA
 * 
 * @author Haifeng Li
 */
public class FLD extends AbstractClassifier<double[]> implements Projection<double[]> {
    private static final long serialVersionUID = 2L;

    /**
     * The dimensionality of data.
     */
    private final int p;
    /**
     * The number of classes.
     */
    private final int k;
    /**
     * Project matrix.
     */
    private final Matrix scaling;
    /**
     * Projected mean vector.
     */
    private final double[] mean;
    /**
     * Projected class mean vectors.
     */
    private final double[][] mu;

    /**
     * Constructor.
     * @param mean the mean vector of all samples.
     * @param mu the mean vectors of each class.
     * @param scaling the projection matrix.
     */
    public FLD(double[] mean, double[][] mu, Matrix scaling) {
        this(mean, mu, scaling, IntSet.of(mu.length));
    }

    /**
     * Constructor.
     * @param mean the mean vector of all samples.
     * @param mu the mean vectors of each class - mean.
     * @param scaling the projection matrix.
     * @param labels the class label encoder.
     */
    public FLD(double[] mean, double[][] mu, Matrix scaling, IntSet labels) {
        super(labels);
        this.k = mu.length;
        this.p = mean.length;
        this.scaling = scaling;

        int L = scaling.ncol();
        this.mean = new double[L];
        scaling.tv(mean, this.mean);

        this.mu = new double[k][L];
        for (int i = 0; i < k; i++) {
            scaling.tv(mu[i], this.mu[i]);
        }
    }

    /**
     * Fits Fisher's linear discriminant.
     * @param x training samples.
     * @param y training labels.
     * @return the model
     */
    public static FLD fit (double[][] x, int[] y) {
        return fit(x, y, -1, 1E-4);
    }

    /**
     * Fits Fisher's linear discriminant.
     * @param x training samples.
     * @param y training labels.
     * @param params the hyper-parameters.
     * @return the model
     */
    public static FLD fit (double[][] x, int[] y, Properties params) {
        int L = Integer.parseInt(params.getProperty("smile.fisher.dimension", "-1"));
        double tol = Double.parseDouble(params.getProperty("smile.fisher.tolerance", "1E-4"));
        return fit(x, y, L, tol);
    }

    /**
     * Fits Fisher's linear discriminant.
     * @param x training samples.
     * @param y training labels.
     * @param L the dimensionality of mapped space.
     * @param tol a tolerance to decide if a covariance matrix is singular;
     *            it will reject variables whose variance is less than tol<sup>2</sup>.
     * @return the model
     */
    public static FLD fit(double[][] x, int[] y, int L, double tol) {
        if (x.length != y.length) {
            throw new IllegalArgumentException(String.format("The sizes of X and Y don't match: %d != %d", x.length, y.length));
        }

        DiscriminantAnalysis da = DiscriminantAnalysis.fit(x, y, null, tol);

        int n = x.length;
        int k = da.k;
        int p = da.mean.length;

        if (L >= k) {
            throw new IllegalArgumentException(String.format("The dimensionality of mapped space is too high: %d >= %d", L, k));
        }

        if (L <= 0) {
            L = k - 1;
        }

        double[] mean = da.mean;
        double[][] mu = da.mu;

        Matrix scaling;
        if (n - k < p) {
            scaling = small(L, x, mean, mu, da.priori, tol);
        } else {
            scaling = fld(L, x, mean, mu, tol);
        }

        return new FLD(mean, mu, scaling, da.labels);
    }

    /** FLD when the sample size is large. */
    private static Matrix fld(int L, double[][] x, double[] mean, double[][] mu, double tol) {
        int k = mu.length;
        int p = mean.length;

        // Total scatter
        Matrix St = DiscriminantAnalysis.St(x, mean, k, tol);

        for (double[] mui : mu) {
            for (int j = 0; j < p; j++) {
                mui[j] -= mean[j];
            }
        }

        // Between class scatter
        Matrix Sb = new Matrix(p, p);
        for (double[] mui : mu) {
            for (int j = 0; j < p; j++) {
                for (int i = 0; i <= j; i++) {
                    Sb.add(i, j, mui[i] * mui[j]);
                }
            }
        }

        for (int j = 0; j < p; j++) {
            for (int i = 0; i <= j; i++) {
                Sb.div(i, j, k);
                Sb.set(j, i, Sb.get(i, j));
            }
        }

        // Within class scatter
        Matrix Sw = St.sub(Sb);
        Matrix SwInvSb = Sw.inverse().mm(Sb);
        Matrix.EVD evd = SwInvSb.eigen(false, true, true);

        double[] w = new double[p];
        for (int i = 0; i < p; i++) {
            w[i] = -(evd.wr[i] * evd.wr[i] + evd.wi[i] * evd.wi[i]);
        }
        int[] index = QuickSort.sort(w);

        Matrix scaling = new Matrix(p, L);
        for (int j = 0; j < L; j++) {
            int l = index[j];
            for (int i = 0; i < p; i++) {
                scaling.set(i, j, evd.Vr.get(i, l));
            }
        }

        return scaling;
    }

    /** Generalized FLD for small sample size. */
    private static Matrix small(int L, double[][] x, double[] mean, double[][] mu, double[] priori, double tol) {
        int k = mu.length;
        int p = mean.length;

        int n = x.length;
        double sqrtn = Math.sqrt(n);

        Matrix X = new Matrix(p, n);
        for (int i = 0; i < n; i++) {
            double[] xi = x[i];
            for (int j = 0; j < p; j++) {
                X.set(j, i, (xi[j] - mean[j]) / sqrtn);
            }
        }

        for (double[] mui : mu) {
            for (int j = 0; j < p; j++) {
                mui[j] -= mean[j];
            }
        }

        Matrix M = new Matrix(p, k);
        for (int i = 0; i < k; i++) {
            double pi = Math.sqrt(priori[i]);
            double[] mui = mu[i];
            for (int j = 0; j < p; j++) {
                M.set(j, i, pi * mui[j]);
            }
        }

        Matrix.SVD svd = X.svd(true, true);
        Matrix U = svd.U;
        double[] s = svd.s;

        tol = tol * tol;
        Matrix UTM = U.tm(M);
        for (int i = 0; i < n; i++) {
            // Since the rank of St is only n - k, there are some singular values of 0.
            double si = 0.0;
            if (s[i] > tol) {
                si = 1.0 / Math.sqrt(s[i]);
            }

            for (int j = 0; j < k; j++) {
                UTM.mul(i, j, si);
            }
        }

        Matrix StInvM = U.mm(UTM);
        Matrix U2 = U.tm(StInvM.svd(true, true).U.submatrix(0, 0, p-1, L-1));

        for (int i = 0; i < n; i++) {
            // Since the rank of St is only n - k, there are some singular values of 0.
            double si = 0.0;
            if (s[i] > tol) {
                si = 1.0 / Math.sqrt(s[i]);
            }

            for (int j = 0; j < L; j++) {
                U2.mul(i, j, si);
            }
        }

        return U.mm(U2);
    }

    @Override
    public int predict(double[] x) {
        if (x.length != p) {
            throw new IllegalArgumentException(String.format("Invalid input vector size: %d, expected: %d", x.length, p));
        }

        double[] wx = project(x);

        int y = 0;
        double nearest = Double.POSITIVE_INFINITY;
        for (int i = 0; i < k; i++) {
            double d = MathEx.distance(wx, mu[i]);
            if (d < nearest) {
                nearest = d;
                y = i;
            }
        }

        return classes.valueOf(y);
    }

    @Override
    public double[] project(double[] x) {
        if (x.length != p) {
            throw new IllegalArgumentException(String.format("Invalid input vector size: %d, expected: %d", x.length, p));
        }

        double[] y = scaling.tv(x);
        MathEx.sub(y, mean);
        return y;
    }

    @Override
    public double[][] project(double[][] x) {
        double[][] y = new double[x.length][scaling.ncol()];
        
        for (int i = 0; i < x.length; i++) {
            if (x[i].length != p) {
                throw new IllegalArgumentException(String.format("Invalid input vector size: %d, expected: %d", x[i].length, p));
            }

            scaling.tv(x[i], y[i]);
            MathEx.sub(y[i], mean);
        }
        
        return y;
    }

    /**
     * Returns the projection matrix W. The dimension reduced data can
     * be obtained by {@code y = W' * x}.
     * @return the projection matrix.
     */
    public Matrix getProjection() {
        return scaling;
    }
}
