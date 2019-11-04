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

package smile.projection;

import java.io.Serializable;
import smile.math.MathEx;
import smile.math.matrix.DenseMatrix;
import smile.math.matrix.Matrix;
import smile.stat.distribution.GaussianDistribution;

/**
 * Random projection is a promising dimensionality reduction technique for
 * learning mixtures of Gaussians. According to Johnson-Lindenstrauss lemma,
 * any n data points in high dimension can be mapped down to
 * d = O(log n / &epsilon;<sup>2</sup>) dimension without
 * distorting their pairwise distances by more than (1 + &epsilon;). However,
 * this reduced dimension is still far too high. Let &epsilon; = 1, we need
 * 2<sup>d</sup> data points, and this usually exceeds n by many orders of magnitude.
 * <p>
 * Fortunately, we can reduce the dimension of the data far more drastically for
 * the particular case of mixtures of Gaussians. In fact, we can map the data
 * into just d = O(log k) dimensions, where k is the number of Gaussians. Therefore,
 * the amount of data we will need is only polynomial in k. Note that this projected
 * dimension is independent of the number of data points and of their original
 * dimension. Experiments show that a value of log k works nicely.
 * <p>
 * Besides, even if the original clusters are highly eccentric (that is, far from
 * spherical), random projection will make them more spherical. Note that eccentric
 * clusters are problematic for the EM algorithm because intermediate covariance
 * matrices may become singular or close to singular. Note that for high enough
 * dimension, almost the entire Gaussian distribution lies in a thin shell.
 *
 * <h2>References</h2>
 * <ol>
 * <li> S. Dasgupta. Experiments with random projection. UAI, 2000.</li>
 * <li> D. Achlioptas. Database-friendly random projections. 2001.</li>
 * <li> Chinmay Hegde, Michael Wakin, and Richard Baraniuk. Random projections for manifold learning. NIPS, 2007.</li>
 * </ol>
 *
 * @author Haifeng Li
 */
public class RandomProjection implements LinearProjection, Serializable {
    private static final long serialVersionUID = 2L;

    /**
     * Probability distribution to generate random projection.
     */
    private static final double[] prob = {1.0 / 6, 2.0 / 3, 1.0 / 6};
    /**
     * Projection matrix.
     */
    private DenseMatrix projection;

    /**
     * Constructor.
     * @param projection the projection matrix.
     */
    public RandomProjection(DenseMatrix projection) {
        this.projection = projection;
    }

    /**
     * Generates a non-sparse random projection.
     * @param n the dimension of input space.
     * @param p the dimension of feature space.
     */
    public static RandomProjection of(int n, int p) {
        if (n < 2) {
            throw new IllegalArgumentException("Invalid dimension of input space: " + n);
        }

        if (p < 1 || p > n) {
            throw new IllegalArgumentException("Invalid dimension of feature space: " + p);
        }

        double[][] projection = new double[p][n];
        GaussianDistribution gauss = GaussianDistribution.getInstance();
        for (int i = 0; i < p; i++) {
            for (int j = 0; j < n; j++) {
                projection[i][j] = gauss.rand();
            }
        }

        // Make the columns of the projection matrix orthogonal
        // by modified Gram-Schmidt algorithm.
        MathEx.unitize(projection[0]);
        for (int i = 1; i < p; i++) {
            for (int j = 0; j < i; j++) {
                double t = -MathEx.dot(projection[i], projection[j]);
                MathEx.axpy(t, projection[j], projection[i]);
            }
            MathEx.unitize(projection[i]);
        }

        return new RandomProjection(Matrix.of(projection));
    }

    /**
     * Generates a sparse random projection.
     * @param n the dimension of input space.
     * @param p the dimension of feature space.
     */
    public static RandomProjection sparse(int n, int p) {
        if (n < 2) {
            throw new IllegalArgumentException("Invalid dimension of input space: " + n);
        }

        if (p < 1 || p > n) {
            throw new IllegalArgumentException("Invalid dimension of feature space: " + p);
        }

        DenseMatrix projection = Matrix.zeros(p, n);
        double scale = Math.sqrt(3);
        for (int i = 0; i < p; i++) {
            for (int j = 0; j < n; j++) {
                projection.set(i, j, scale * (MathEx.random(prob) - 1));
            }
        }
        return new RandomProjection(projection);
    }

    @Override
    public DenseMatrix getProjection() {
        return projection;
    }
}
