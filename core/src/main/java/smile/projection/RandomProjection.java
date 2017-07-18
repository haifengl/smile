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
package smile.projection;

import java.io.Serializable;
import smile.math.Math;
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
public class RandomProjection implements Projection<double[]>, Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * Probability distribution to generate random projection.
     */
    private static final double[] prob = {1.0 / 6, 2.0 / 3, 1.0 / 6};
    /**
     * The dimension of feature space.
     */
    private int p;
    /**
     * The dimension of input space.
     */
    private int n;
    /**
     * Projection matrix.
     */
    private DenseMatrix projection;

    /**
     * Constructor. Generate a non-sparse random projection.
     * @param n the dimension of input space.
     * @param p the dimension of feature space.
     */
    public RandomProjection(int n, int p) {
        this(n, p, false);
    }

    /**
     * Constructor.
     * @param n the dimension of input space.
     * @param p the dimension of feature space.
     * @param sparse true to generate a sparse random projection proposed by Achlioptas.
     */
    public RandomProjection(int n, int p, boolean sparse) {
        if (n < 2) {
            throw new IllegalArgumentException("Invalid dimension of input space: " + n);
        }

        if (p < 1 || p > n) {
            throw new IllegalArgumentException("Invalid dimension of feature space: " + p);
        }

        this.n = n;
        this.p = p;

        if (sparse) {
            projection = Matrix.zeros(p, n);
            double scale = Math.sqrt(3);
            for (int i = 0; i < p; i++) {
                for (int j = 0; j < n; j++) {
                    projection.set(i, j, scale * (Math.random(prob) - 1));
                }
            }
        } else {
            double[][] proj = new double[p][n];
            GaussianDistribution gauss = GaussianDistribution.getInstance();
            for (int i = 0; i < p; i++) {
                for (int j = 0; j < n; j++) {
                    proj[i][j] = gauss.rand();
                }
            }

            // Make the columns of the projection matrix orthogonal
            // by modified Gram-Schmidt algorithm.
            Math.unitize(proj[0]);
            for (int i = 1; i < p; i++) {
                for (int j = 0; j < i; j++) {
                    double t = -Math.dot(proj[i], proj[j]);
                    Math.axpy(t, proj[j], proj[i]);
                }
                Math.unitize(proj[i]);
            }
            projection = Matrix.newInstance(proj);
        }
    }

    /**
     * Returns the projection matrix. The dimension reduced data can be obtained
     * by y = W * x.
     */
    public DenseMatrix getProjection() {
        return projection;
    }

    @Override
    public double[] project(double[] x) {
        if (x.length != n) {
            throw new IllegalArgumentException(String.format("Invalid input vector size: %d, expected: %d", x.length, n));
        }

        double[] y = new double[p];
        projection.ax(x, y);
        return y;
    }

    @Override
    public double[][] project(double[][] x) {
        if (x[0].length != n) {
            throw new IllegalArgumentException(String.format("Invalid input vector size: %d, expected: %d", x[0].length, n));
        }

        double[][] y = new double[x.length][p];
        for (int i = 0; i < x.length; i++) {
            projection.ax(x[i], y[i]);
        }
        return y;
    }
}
