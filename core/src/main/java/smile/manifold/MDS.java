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
package smile.manifold;

import java.util.Properties;
import smile.math.MathEx;
import smile.tensor.*;
import static smile.linalg.UPLO.*;
import static smile.tensor.ScalarType.*;

/**
 * Classical multidimensional scaling, also known as principal coordinates
 * analysis. Given a matrix of dissimilarities (e.g. pairwise distances), MDS
 * finds a set of points in low dimensional space that well-approximates the
 * dissimilarities. We are not restricted to using Euclidean
 * distance metric. However, when Euclidean distances are used MDS is
 * equivalent to PCA.
 *
 * @see smile.feature.extraction.PCA
 * @see SammonMapping
 *
 * @param scores the component scores.
 * @param proportion the proportion of variance contained in each principal component.
 * @param coordinates the principal coordinates
 * @author Haifeng Li
 */
public record MDS(double[] scores, double[] proportion, double[][] coordinates) {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(MDS.class);

    /**
     * MDS hyperparameters.
     * @param d the dimension of the projection.
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
     */
    public record Options(int d, boolean positive) {
        /** Constructor. */
        public Options {
            if (d < 2) {
                throw new IllegalArgumentException("Invalid dimension of feature space: " + d);
            }
        }

        /** Constructor. */
        public Options() {
            this(2, false);
        }

        /**
         * Returns the persistent set of hyperparameters.
         * @return the persistent set.
         */
        public Properties toProperties() {
            Properties props = new Properties();
            props.setProperty("smile.mds.d", Integer.toString(d));
            props.setProperty("smile.mds.positive", Boolean.toString(positive));
            return props;
        }

        /**
         * Returns the options from properties.
         *
         * @param props the hyperparameters.
         * @return the options.
         */
        public static Options of(Properties props) {
            int d = Integer.parseInt(props.getProperty("smile.mds.d", "2"));
            boolean positive = Boolean.parseBoolean(props.getProperty("smile.mds.positive", "false"));
            return new Options(d, positive);
        }
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
    public static MDS fit(double[][] proximity) {
        return fit(proximity, new Options());
    }

    /**
     * Fits the classical multidimensional scaling.
     * @param proximity the non-negative proximity matrix of dissimilarities. The
     * diagonal should be zero and all other elements should be positive and
     * symmetric. For pairwise distances matrix, it should be just the plain
     * distance, not squared.
     * @param options the hyperparameters.
     * @return the model.
     */
    public static MDS fit(double[][] proximity, Options options) {
        int m = proximity.length;
        int n = proximity[0].length;

        if (m != n) {
            throw new IllegalArgumentException("The proximity matrix is not square.");
        }

        int d = options.d;
        if (d >= n) {
            throw new IllegalArgumentException("Invalid d = " + d);
        }

        DenseMatrix B = getGram(proximity);

        if (options.positive) {
            DenseMatrix Z = B.zeros(2 * n, 2 * n);
            for (int i = 0; i < n; i++) {
                for (int j = 0; j < n; j++) {
                    Z.set(i, n + j, 2 * B.get(i, j));
                }
            }

            for (int i = 0; i < n; i++) {
                Z.set(n + i, i, -1);
            }

            double[] mean = MathEx.rowMeans(proximity);
            double mu = MathEx.mean(mean);
            for (int i = 0; i < n; i++) {
                for (int j = 0; j < n; j++) {
                    Z.set(n + i, n + j, 2 * (proximity[i][j] - mean[i] - mean[j] + mu));
                }
            }

            Vector eigvalues = Z.eigen(false, false).wr();
            double c = eigvalues.max();

            for (int i = 0; i < n; i++) {
                B.set(i, i, 0.0);
                for (int j = 0; j < i; j++) {
                    double x = -0.5 * MathEx.pow2(proximity[i][j] + c);
                    B.set(i, j, x);
                    B.set(j, i, x);
                }
            }
        }

        B.withUplo(LOWER);
        EVD eigen = ARPACK.syev(B, ARPACK.SymmOption.LA, d);

        if (eigen.wr().size() < d) {
            logger.warn("eigen({}) returns only {} eigen vectors", d, eigen.wr().size());
            d = eigen.wr().size();
        }

        double[][] coordinates = new double[n][d];
        for (int j = 0; j < d; j++) {
            if (eigen.wr().get(j) < 0) {
                throw new IllegalArgumentException(String.format("Some of the first %d eigenvalues are < 0.", d));
            }

            double scale = Math.sqrt(eigen.wr().get(j));
            for (int i = 0; i < n; i++) {
                coordinates[i][j] = eigen.Vr().get(i, j) * scale;
            }
        }

        double[] eigenvalues = eigen.wr().toArray(new double[0]);
        double[] proportion = eigenvalues.clone();
        MathEx.unitize1(proportion);

        return new MDS(eigenvalues, proportion, coordinates);
    }

    /**
     * Returns the Gram matrix X' * X.
     * @param proximity the non-negative proximity matrix of dissimilarities.
     * @return the Gram matrix.
     */
    private static DenseMatrix getGram(double[][] proximity) {
        int n = proximity[0].length;
        DenseMatrix A = DenseMatrix.zeros(Float64, n, n);
        DenseMatrix B = DenseMatrix.zeros(Float64, n, n);

        for (int i = 0; i < n; i++) {
            for (int j = 0; j < i; j++) {
                double x = -0.5 * MathEx.pow2(proximity[i][j]);
                A.set(i, j, x);
                A.set(j, i, x);
            }
        }

        Vector mean = A.rowMeans();
        double mu = mean.mean();

        for (int i = 0; i < n; i++) {
            for (int j = 0; j <= i; j++) {
                double x = A.get(i, j) - mean.get(i) - mean.get(j) + mu;
                B.set(i, j, x);
                B.set(j, i, x);
            }
        }

        return B;
    }
}
