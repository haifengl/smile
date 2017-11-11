/*******************************************************************************
 * Copyright (c) 2010 Haifeng Li
 *   
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *  
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *******************************************************************************/
package smile.mds;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import smile.math.Math;
import smile.math.matrix.Matrix;
import smile.math.matrix.DenseMatrix;
import smile.math.matrix.EVD;

/**
 * Classical multidimensional scaling, also known as principal coordinates
 * analysis. Given a matrix of dissimilarities (e.g. pairwise distances), MDS
 * finds a set of points in low dimensional space that well-approximates the
 * dissimilarities in A. We are not restricted to using a Euclidean
 * distance metric. However, when Euclidean distances are used MDS is
 * equivalent to PCA.
 *
 * @see smile.projection.PCA
 * @see SammonMapping
 * 
 * @author Haifeng Li
 */
public class MDS {
    private static final Logger logger = LoggerFactory.getLogger(MDS.class);

    /**
     * Component scores.
     */
    private double[] eigenvalues;
    /**
     * Coordinate matrix.
     */
    private double[][] coordinates;
    /**
     * The proportion of variance contained in each principal component.
     */
    private double[] proportion;

    /**
     * Returns the component scores, ordered from largest to smallest.
     */
    public double[] getEigenValues() {
        return eigenvalues;
    }

    /**
     * Returns the proportion of variance contained in each eigenvectors,
     * ordered from largest to smallest.
     */
    public double[] getProportion() {
        return proportion;
    }

    /**
     * Returns the principal coordinates of projected data.
     */
    public double[][] getCoordinates() {
        return coordinates;
    }

    /**
     * Constructor. Learn the classical multidimensional scaling.
     * Map original data into 2-dimensional Euclidean space.
     * @param proximity the nonnegative proximity matrix of dissimilarities. The
     * diagonal should be zero and all other elements should be positive and
     * symmetric. For pairwise distances matrix, it should be just the plain
     * distance, not squared.
     */
    public MDS(double[][] proximity) {
        this(proximity, 2);
    }

    /**
     * Constructor. Learn the classical multidimensional scaling.
     * @param proximity the nonnegative proximity matrix of dissimilarities. The
     * diagonal should be zero and all other elements should be positive and
     * symmetric. For pairwise distances matrix, it should be just the plain
     * distance, not squared.
     * @param k the dimension of the projection.
     */
    public MDS(double[][] proximity, int k) {
        this(proximity, k, false);
    }

    /**
     * Constructor. Learn the classical multidimensional scaling.
     * @param proximity the nonnegative proximity matrix of dissimilarities. The
     * diagonal should be zero and all other elements should be positive and
     * symmetric. For pairwise distances matrix, it should be just the plain
     * distance, not squared.
     * @param k the dimension of the projection.
     * @param add true to estimate an appropriate constant to be added
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
    public MDS(double[][] proximity, int k, boolean add) {
        int m = proximity.length;
        int n = proximity[0].length;

        if (m != n) {
            throw new IllegalArgumentException("The proximity matrix is not square.");
        }

        if (k < 1 || k >= n) {
            throw new IllegalArgumentException("Invalid k = " + k);
        }

        DenseMatrix A = Matrix.zeros(n, n);
        DenseMatrix B = Matrix.zeros(n, n);

        for (int i = 0; i < n; i++) {
            for (int j = 0; j < i; j++) {
                double x = -0.5 * Math.sqr(proximity[i][j]);
                A.set(i, j, x);
                A.set(j, i, x);
            }
        }

        double[] mean = A.rowMeans();
        double mu = Math.mean(mean);

        for (int i = 0; i < n; i++) {
            for (int j = 0; j <= i; j++) {
                double x = A.get(i, j) - mean[i] - mean[j] + mu;
                B.set(i, j, x);
                B.set(j, i, x);;
            }
        }

        if (add) {
            DenseMatrix Z = Matrix.zeros(2 * n, 2 * n);
            for (int i = 0; i < n; i++) {
                for (int j = 0; j < n; j++) {
                    Z.set(i, n + j, 2 * B.get(i, j));
                }
            }

            for (int i = 0; i < n; i++) {
                Z.set(n + i, i, -1);
            }

            mean = Math.rowMeans(proximity);
            mu = Math.mean(mean);
            for (int i = 0; i < n; i++) {
                for (int j = 0; j < n; j++) {
                    Z.set(n + i, n + j, 2 * (proximity[i][j] - mean[i] - mean[j] + mu));
                }
            }

            double[] evalues = Z.eig();
            double c = Math.max(evalues);

            for (int i = 0; i < n; i++) {
                B.set(i, i, 0.0);
                for (int j = 0; j < i; j++) {
                    double x = -0.5 * Math.sqr(proximity[i][j] + c);
                    B.set(i, j, x);
                    B.set(j, i, x);
                }
            }
        }

        B.setSymmetric(true);
        EVD eigen = B.eigen(k);

        if (eigen.getEigenValues().length < k) {
            logger.warn("eigen({}) returns only {} eigen vectors", k, eigen.getEigenValues().length);
            k = eigen.getEigenValues().length;
        }

        coordinates = new double[n][k];
        for (int j = 0; j < k; j++) {
            if (eigen.getEigenValues()[j] < 0) {
                throw new IllegalArgumentException(String.format("Some of the first %d eigenvalues are < 0.", k));
            }

            double scale = Math.sqrt(eigen.getEigenValues()[j]);
            for (int i = 0; i < n; i++) {
                coordinates[i][j] = eigen.getEigenVectors().get(i, j) * scale;
            }
        }

        eigenvalues = eigen.getEigenValues();
        proportion = eigenvalues.clone();
        Math.unitize1(proportion);
    }
}
