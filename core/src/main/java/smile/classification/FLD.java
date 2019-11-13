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

package smile.classification;

import java.util.Properties;
import smile.data.DataFrame;
import smile.data.formula.Formula;
import smile.math.MathEx;
import smile.math.matrix.Matrix;
import smile.math.matrix.DenseMatrix;
import smile.math.matrix.EVD;
import smile.math.matrix.SVD;
import smile.projection.Projection;
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
 * <li> Robust and Accurate Cancer Classification with Gene Expression Profiling http://alumni.cs.ucr.edu/~hli/paper/hli05tumor.pdf.</li>
 * </ol>
 *
 * @see LDA
 * @see smile.projection.PCA
 * 
 * @author Haifeng Li
 */
public class FLD implements Classifier<double[]>, Projection<double[]> {
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
    private final DenseMatrix scaling;
    /**
     * Projected mean vector.
     */
    private final double[] mean;
    /**
     * Projected class mean vectors.
     */
    private final double[][] mu;
    /**
     * The class label encoder.
     */
    private final IntSet labels;

    /**
     * Constructor.
     * @param mean the mean vector of all samples.
     * @param mu the mean vectors of each class.
     * @param scaling the projection matrix.
     */
    public FLD(double[] mean, double[][] mu, DenseMatrix scaling) {
        this(mean, mu, scaling, IntSet.of(mu.length));
    }

    /**
     * Constructor.
     * @param mean the mean vector of all samples.
     * @param mu the mean vectors of each class - mean.
     * @param scaling the projection matrix.
     * @param labels class labels
     */
    public FLD(double[] mean, double[][] mu, DenseMatrix scaling, IntSet labels) {
        this.k = mu.length;
        this.p = mean.length;
        this.scaling = scaling;
        this.labels = labels;

        int L = scaling.ncols();
        this.mean = new double[L];
        scaling.atx(mean, this.mean);

        this.mu = new double[k][L];
        for (int i = 0; i < k; i++) {
            scaling.atx(mu[i], this.mu[i]);
        }
    }

    /**
     * Learn Fisher's linear discriminant.
     *
     * @param formula a symbolic description of the model to be fitted.
     * @param data the data frame of the explanatory and response variables.
     */
    public static FLD fit(Formula formula, DataFrame data) {
        return fit(formula, data, new Properties());
    }

    /**
     * Learn Fisher's linear discriminant.
     *
     * @param formula a symbolic description of the model to be fitted.
     * @param data the data frame of the explanatory and response variables.
     */
    public static FLD fit(Formula formula, DataFrame data, Properties prop) {
        int L = Integer.valueOf(prop.getProperty("smile.fld.dimension", "-1"));
        double tol = Double.valueOf(prop.getProperty("smile.fld.tolerance", "1E-4"));
        double[][] x = formula.x(data).toArray();
        int[] y = formula.y(data).toIntArray();
        return fit(x, y, L, tol);
    }

    /**
     * Learn Fisher's linear discriminant.
     * @param x training samples.
     * @param y training labels.
     */
    public static FLD fit (double[][] x, int[] y) {
        return fit(x, y, -1, 1E-4);
    }

    /**
     * Learn Fisher's linear discriminant.
     * @param x training samples.
     * @param y training labels.
     * @param L the dimensionality of mapped space.
     * @param tol a tolerance to decide if a covariance matrix is singular; it
     * will reject variables whose variance is less than tol<sup>2</sup>.
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

        DenseMatrix scaling;
        if (n - k < p) {
            scaling = small(L, x, mean, mu, da.priori, tol);
        } else {
            scaling = fld(L, x, mean, mu, tol);
        }

        return new FLD(mean, mu, scaling, da.labels);
    }

    /** FLD when the sample size is large. */
    private static DenseMatrix fld(int L, double[][] x, double[] mean, double[][] mu, double tol) {
        int k = mu.length;
        int p = mean.length;

        DenseMatrix St = DiscriminantAnalysis.St(x, mean, k, tol);
        EVD eigen = St.eigen();

        tol = tol * tol;
        double[] s = eigen.getEigenValues();
        for (int i = 0; i < s.length; i++) {
            if (s[i] < tol) {
                throw new IllegalArgumentException("The covariance matrix is close to singular.");
            }

            s[i] = 1.0 / s[i];
        }

        for (int i = 0; i < k; i++) {
            double[] mui = mu[i];
            for (int j = 0; j < p; j++) {
                mui[j] -= mean[j];
            }
        }

        // Between class scatter
        DenseMatrix Sb = Matrix.zeros(p, p);
        for (int c = 0; c < k; c++) {
            double[] mui = mu[c];
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

        DenseMatrix U = eigen.getEigenVectors();
        DenseMatrix UB = U.atbmm(Sb);

        for (int j = 0; j < p; j++) {
            double sj = s[j];
            for (int i = 0; i < k; i++) {
                UB.mul(i, j, sj);
            }
        }

        DenseMatrix StInvSb = U.abmm(UB);
        StInvSb.setSymmetric(true);
        DenseMatrix scaling = StInvSb.eigen().getEigenVectors().submat(0, 0, p, L);

        return scaling;
    }

    /** Generalized FLD for small sample size. */
    private static DenseMatrix small(int L, double[][] x, double[] mean, double[][] mu, double[] priori, double tol) {
        int k = mu.length;
        int p = mean.length;

        int n = x.length;
        double sqrtn = Math.sqrt(n);

        DenseMatrix X = Matrix.zeros(p, n);
        for (int i = 0; i < n; i++) {
            double[] xi = x[i];
            for (int j = 0; j < p; j++) {
                X.set(j, i, (xi[j] - mean[j]) / sqrtn);
            }
        }

        for (int i = 0; i < k; i++) {
            double[] mui = mu[i];
            for (int j = 0; j < p; j++) {
                mui[j] -= mean[j];
            }
        }

        DenseMatrix M = Matrix.zeros(p, k);
        for (int i = 0; i < k; i++) {
            double pi = Math.sqrt(priori[i]);
            double[] mui = mu[i];
            for (int j = 0; j < p; j++) {
                M.set(j, i, pi * mui[j]);
            }
        }

        SVD svd = X.svd(true);
        DenseMatrix U = svd.getU();
        double[] s = svd.getSingularValues();

        tol = tol * tol;
        DenseMatrix UTM = U.atbmm(M);
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

        DenseMatrix StInvM = U.abmm(UTM);
        DenseMatrix U2 = U.atbmm(StInvM.svd(true).getU().submat(0, 0, p+1, L));

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

        DenseMatrix scaling = U.abmm(U2);
        return scaling;
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

        return labels.valueOf(y);
    }

    @Override
    public double[] project(double[] x) {
        if (x.length != p) {
            throw new IllegalArgumentException(String.format("Invalid input vector size: %d, expected: %d", x.length, p));
        }

        double[] y = new double[scaling.ncols()];
        scaling.atx(x, y);
        MathEx.sub(y, mean);
        return y;
    }

    @Override
    public double[][] project(double[][] x) {
        double[][] y = new double[x.length][scaling.ncols()];
        
        for (int i = 0; i < x.length; i++) {
            if (x[i].length != p) {
                throw new IllegalArgumentException(String.format("Invalid input vector size: %d, expected: %d", x[i].length, p));
            }

            scaling.atx(x[i], y[i]);
            MathEx.sub(y[i], mean);
        }
        
        return y;
    }

    /**
     * Returns the projection matrix W. The dimension reduced data can be obtained
     * by y = W' * x.
     */
    public DenseMatrix getProjection() {
        return scaling;
    }
}
