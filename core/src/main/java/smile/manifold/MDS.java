/*
 * Copyright (c) 2010-2021 Haifeng Li. All rights reserved.
 *
 * Smile is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Smile is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Smile.  If not, see <https://www.gnu.org/licenses/>.
 */

package smile.manifold;

import java.util.Properties;
import smile.math.MathEx;
import smile.math.blas.UPLO;
import smile.math.matrix.ARPACK;
import smile.math.matrix.Matrix;

/**
 * Classical multidimensional scaling, also known as principal coordinates
 * analysis. Given a matrix of dissimilarities (e.g. pairwise distances), MDS
 * finds a set of points in low dimensional space that well-approximates the
 * dissimilarities. We are not restricted to using an Euclidean
 * distance metric. However, when Euclidean distances are used MDS is
 * equivalent to PCA.
 *
 * @see smile.feature.extraction.PCA
 * @see SammonMapping
 * 
 * @author Haifeng Li
 */
public class MDS {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(MDS.class);

    /**
     * The component scores.
     */
    public final double[] scores;
    /**
     * The principal coordinates.
     */
    public final double[][] coordinates;
    /**
     * The proportion of variance contained in each principal component.
     */
    public final double[] proportion;

    /**
     * Constructor.
     *
     * @param scores the component scores.
     * @param proportion the proportion of variance contained in each principal component.
     * @param coordinates the principal coordinates
     */
    public MDS(double[] scores, double[] proportion, double[][] coordinates) {
        this.scores = scores;
        this.proportion = proportion;
        this.coordinates = coordinates;
    }

    /**
     * Fits the classical multidimensional scaling.
     * Map original data into 2-dimensional Euclidean space.
     * @param proximity the non-negative proximity matrix of dissimilarities. The
     * diagonal should be zero and all other elements should be positive and
     * symmetric. For pairwise distances matrix, it should be just the plain
     * distance, not squared.
     * @return the model.
     */
    public static MDS of(double[][] proximity) {
        return of(proximity, new Properties());
    }

    /**
     * Fits the classical multidimensional scaling.
     * @param proximity the non-negative proximity matrix of dissimilarities. The
     * diagonal should be zero and all other elements should be positive and
     * symmetric. For pairwise distances matrix, it should be just the plain
     * distance, not squared.
     * @param k the dimension of the projection.
     * @return the model.
     */
    public static MDS of(double[][] proximity, int k) {
        return of(proximity, k, false);
    }

    /**
     * Fits the classical multidimensional scaling.
     *
     * @param proximity the non-negative proximity matrix of dissimilarities. The
     * diagonal should be zero and all other elements should be positive and
     * symmetric. For pairwise distances matrix, it should be just the plain
     * distance, not squared.
     * @param params the hyper-parameters.
     * @return the model.
     */
    public static MDS of(double[][] proximity, Properties params) {
        int k = Integer.parseInt(params.getProperty("smile.mds.k", "2"));
        boolean positive = Boolean.parseBoolean(params.getProperty("smile.mds.positive", "false"));
        return of(proximity, k, positive);
    }

    /**
     * Fits the classical multidimensional scaling.
     * @param proximity the non-negative proximity matrix of dissimilarities. The
     * diagonal should be zero and all other elements should be positive and
     * symmetric. For pairwise distances matrix, it should be just the plain
     * distance, not squared.
     * @param k the dimension of the projection.
     * @param positive if true, estimate an appropriate constant to be added
     * to all the dissimilarities, apart from the self-dissimilarities, that
     * makes the learning matrix positive semi-definite. The other formulation of
     * the additive constant problem is as follows. If the proximity is
     * measured in an interval scale, where there is no natural origin, then there
     * is not a sympathy of the dissimilarities to the distances in the Euclidean
     * space used to represent the objects. In this case, we can estimate a constant c
     * such that proximity + c may be taken as ratio data, and also possibly
     * to minimize the dimensionality of the Euclidean space required for
     * representing the objects.
     * @return the model.
     */
    public static MDS of(double[][] proximity, int k, boolean positive) {
        int m = proximity.length;
        int n = proximity[0].length;

        if (m != n) {
            throw new IllegalArgumentException("The proximity matrix is not square.");
        }

        if (k < 1 || k >= n) {
            throw new IllegalArgumentException("Invalid k = " + k);
        }

        Matrix A = new Matrix(n, n);
        Matrix B = new Matrix(n, n);

        for (int i = 0; i < n; i++) {
            for (int j = 0; j < i; j++) {
                double x = -0.5 * MathEx.pow2(proximity[i][j]);
                A.set(i, j, x);
                A.set(j, i, x);
            }
        }

        double[] mean = A.rowMeans();
        double mu = MathEx.mean(mean);

        for (int i = 0; i < n; i++) {
            for (int j = 0; j <= i; j++) {
                double x = A.get(i, j) - mean[i] - mean[j] + mu;
                B.set(i, j, x);
                B.set(j, i, x);
            }
        }

        if (positive) {
            Matrix Z = new Matrix(2 * n, 2 * n);
            for (int i = 0; i < n; i++) {
                for (int j = 0; j < n; j++) {
                    Z.set(i, n + j, 2 * B.get(i, j));
                }
            }

            for (int i = 0; i < n; i++) {
                Z.set(n + i, i, -1);
            }

            mean = MathEx.rowMeans(proximity);
            mu = MathEx.mean(mean);
            for (int i = 0; i < n; i++) {
                for (int j = 0; j < n; j++) {
                    Z.set(n + i, n + j, 2 * (proximity[i][j] - mean[i] - mean[j] + mu));
                }
            }

            double[] eigvalues = Z.eigen(false, false, true).wr;
            double c = MathEx.max(eigvalues);

            for (int i = 0; i < n; i++) {
                B.set(i, i, 0.0);
                for (int j = 0; j < i; j++) {
                    double x = -0.5 * MathEx.pow2(proximity[i][j] + c);
                    B.set(i, j, x);
                    B.set(j, i, x);
                }
            }
        }

        B.uplo(UPLO.LOWER);
        Matrix.EVD eigen = ARPACK.syev(B, ARPACK.SymmOption.LA, k);

        if (eigen.wr.length < k) {
            logger.warn("eigen({}) returns only {} eigen vectors", k, eigen.wr.length);
            k = eigen.wr.length;
        }

        double[][] coordinates = new double[n][k];
        for (int j = 0; j < k; j++) {
            if (eigen.wr[j] < 0) {
                throw new IllegalArgumentException(String.format("Some of the first %d eigenvalues are < 0.", k));
            }

            double scale = Math.sqrt(eigen.wr[j]);
            for (int i = 0; i < n; i++) {
                coordinates[i][j] = eigen.Vr.get(i, j) * scale;
            }
        }

        double[] eigenvalues = eigen.wr;
        double[] proportion = eigenvalues.clone();
        MathEx.unitize1(proportion);

        return new MDS(eigenvalues, proportion, coordinates);
    }
}
