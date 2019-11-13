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

import smile.math.MathEx;
import smile.math.matrix.DenseMatrix;
import smile.math.matrix.Matrix;
import smile.util.IntSet;

/** Common functions for various discriminant analysis. */
class DiscriminantAnalysis {
    /** The number of classes. */
    int k;
    /** The class labels in [0, k). */
    int[] y;
    /** The original class labels. */
    IntSet labels;
    /** The number of instances in each class. */
    int[] ni;
    /** The priori probabilities. */
    double[] priori;
    /** The mean vector. */
    double[] mean;
    /** THe mean vector per class. */
    double[][] mu;

    /**
     * Constructor.
     * @param priori a priori probabilities of each class.
     * @param mean the mean vector of all samples.
     * @param mu the mean vectors of each class.
     */
    public DiscriminantAnalysis(ClassLabels codec, double[] priori, double[] mean, double[][] mu) {
        this.k = codec.k;
        this.ni = codec.ni;
        this.y = codec.y;
        this.labels = codec.labels;
        this.priori = priori;
        this.mean = mean;
        this.mu = mu;
    }

    /**
     * Computes the common information in various discriminant analysis algorithms.
     * @param x training samples.
     * @param y training labels.
     * @param priori the priori probability of each class. If null, it will be
     * estimated from the training data.
     * @param tol a tolerance to decide if a covariance matrix is singular; it
     * will reject variables whose variance is less than tol<sup>2</sup>.
     */
    public static DiscriminantAnalysis fit(double[][] x, int[] y, double[] priori, double tol) {
        if (x.length != y.length) {
            throw new IllegalArgumentException(String.format("The sizes of X and Y don't match: %d != %d", x.length, y.length));
        }

        if (tol <= 0.0) {
            throw new IllegalArgumentException("Invalid tol: " + tol);
        }

        int n = x.length;

        // class label set.
        ClassLabels codec = ClassLabels.fit(y);
        int k = codec.k;
        y = codec.y;
        int[] ni = codec.ni;

        if (n <= k) {
            throw new IllegalArgumentException(String.format("Sample size is too small: %d <= %d", n, k));
        }

        if (priori == null) {
            priori = codec.priori;
        } else {
            if (priori.length != k) {
                throw new IllegalArgumentException("Invalid number of priori probabilities: " + priori.length);
            }

            double sum = 0.0;
            for (double pr : priori) {
                if (pr <= 0.0 || pr >= 1.0) {
                    throw new IllegalArgumentException("Invalid priori probability: " + pr);
                }
                sum += pr;
            }

            if (Math.abs(sum - 1.0) > 1E-10) {
                throw new IllegalArgumentException("The sum of priori probabilities is not one: " + sum);
            }
        }

        int p = x[0].length;
        // Common mean vector.
        double[] mean = MathEx.colMeans(x);
        // Class mean vectors.
        double[][] mu = new double[k][p];

        for (int i = 0; i < n; i++) {
            double[] xi = x[i];
            double[] mui = mu[y[i]];
            for (int j = 0; j < p; j++) {
                mui[j] += xi[j];
            }
        }

        for (int i = 0; i < k; i++) {
            int m = ni[i];
            double[] mui = mu[i];
            for (int j = 0; j < p; j++) {
                mui[j] /= m;
            }
        }

        return new DiscriminantAnalysis(codec, priori, mean, mu);
    }

    /** Computes the covariance matrix of all samples. */
    public static DenseMatrix St(double[][] x, double[] mean, int k, double tol) {
        int n = x.length;
        int p = x[0].length;

        DenseMatrix St = Matrix.zeros(p, p);
        St.setSymmetric(true);

        for (int i = 0; i < n; i++) {
            double[] xi = x[i];
            for (int j = 0; j < p; j++) {
                for (int l = 0; l <= j; l++) {
                    St.add(j, l, (xi[j] - mean[j]) * (xi[l] - mean[l]));
                }
            }
        }

        tol = tol * tol;
        for (int j = 0; j < p; j++) {
            for (int l = 0; l <= j; l++) {
                St.div(j, l, (n - k));
                St.set(l, j, St.get(j, l));
            }

            if (St.get(j, j) < tol) {
                throw new IllegalArgumentException(String.format("Covariance matrix (column %d) is close to singular.", j));
            }
        }

        return St;
    }

    /** Computes the covariance matrix of each class. */
    public static DenseMatrix[] cov(double[][] x, int[] y, double[][] mu, int[] ni) {
        int n = x.length;
        int p = x[0].length;
        int k = mu.length;

        DenseMatrix[] cov = new DenseMatrix[k];

        for (int i = 0; i < k; i++) {
            if (ni[i] <= p) {
                throw new IllegalArgumentException(String.format("The sample size of class %d is too small.", i));
            }

            cov[i] = Matrix.zeros(p, p);
            cov[i].setSymmetric(true);
        }

        for (int i = 0; i < n; i++) {
            DenseMatrix v = cov[y[i]];
            double[] mui = mu[y[i]];
            double[] xi = x[i];
            for (int j = 0; j < p; j++) {
                for (int l = 0; l <= j; l++) {
                    v.add(j, l, (xi[j] - mui[j]) * (xi[l] - mui[l]));
                }
            }
        }

        for (int i = 0; i < k; i++) {
            DenseMatrix v = cov[i];
            int m = ni[i] - 1;
            for (int j = 0; j < p; j++) {
                for (int l = 0; l <= j; l++) {
                    v.div(j, l, m);
                    v.set(l, j, v.get(j, l));
                }
            }
        }

        return cov;
    }
}
