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

/**
 * Generalized Hebbian Algorithm. GHA is a linear feed-forward neural
 * network model for unsupervised learning with applications primarily in
 * principal components analysis. It is single-layer process -- that is, a
 * synaptic weight changes only depending on the response of the inputs and
 * outputs of that layer.
 * <p>
 * It guarantees that GHA finds the first k eigenvectors of the covariance matrix,
 * assuming that the associated eigenvalues are distinct. The convergence theorem
 * is formulated in terms of a time-varying learning rate &eta;. In practice, the
 * learning rate &eta; is chosen to be a small constant, in which case convergence is
 * guaranteed with mean-squared error in synaptic weights of order &eta;.
 * <p>
 * It also has a simple and predictable trade-off between learning speed and
 * accuracy of convergence as set by the learning rate parameter &eta;. It was
 * shown that a larger learning rate &eta; leads to faster convergence
 * and larger asymptotic mean-square error, which is intuitively satisfying.
 * <p>
 * Compared to regular batch PCA algorithm based on eigen decomposition, GHA is
 * an adaptive method and works with an arbitrarily large sample size. The storage
 * requirement is modest. Another attractive feature is that, in a nonstationary
 * environment, it has an inherent ability to track gradual changes in the
 * optimal solution in an inexpensive way.
 *
 * <h2>References</h2>
 * <ol>
 * <li> Terence D. Sanger. Optimal unsupervised learning in a single-layer linear feedforward neural network. Neural Networks 2(6):459-473, 1989.</li>
 * <li> Simon Haykin. Neural Networks: A Comprehensive Foundation (2 ed.). 1998. </li>
 * </ol>
 * 
 * @see PCA
 *
 * @author Haifeng Li
 */
public class GHA implements LinearProjection, Serializable {
    private static final long serialVersionUID = 2L;

    /**
     * The dimension of feature space.
     */
    private int p;
    /**
     * The dimension of input space.
     */
    private int n;
    /**
     * The learning rate;
     */
    private double r;
    /**
     * Projection matrix.
     */
    private DenseMatrix projection;
    /**
     * Workspace for W * x.
     */
    private double[] y;
    /**
     * Workspace for W' * y.
     */
    private double[] wy;

    /**
     * Constructor.
     * @param n the dimension of input space.
     * @param p the dimension of feature space.
     * @param r the learning rate.
     */
    public GHA(int n, int p, double r) {
        if (n < 2) {
            throw new IllegalArgumentException("Invalid dimension of input space: " + n);
        }

        if (p < 1 || p > n) {
            throw new IllegalArgumentException("Invalid dimension of feature space: " + p);
        }

        this.n = n;
        this.p = p;
        this.r = r;

        y = new double[p];
        wy = new double[n];
        projection = Matrix.zeros(p, n);
        for (int i = 0; i < p; i++) {
            for (int j = 0; j < n; j++) {
                projection.set(i, j, 0.1 * MathEx.random());
            }
        }
    }

    /**
     * Constructor.
     * @param w the initial projection matrix.
     * @param r the learning rate.
     */
    public GHA(double[][] w, double r) {
        this.p = w.length;
        this.n = w[0].length;
        this.r = r;

        y = new double[p];
        wy = new double[n];
        projection = Matrix.of(w);
    }

    /**
     * Returns the projection matrix. When GHA converges, the column of projection
     * matrix are the first p eigenvectors of covariance matrix, ordered by decreasing
     * eigenvalues. The dimension reduced data can be obtained by y = W * x.
     */
    @Override
    public DenseMatrix getProjection() {
        return projection;
    }

    /**
     * Returns the learning rate.
     */
    public double getLearningRate() {
        return r;
    }

    /**
     * Set the learning rate.
     */
    public GHA setLearningRate(double r) {
        this.r = r;
        return this;
    }

    /**
     * Update the model with a new sample.
     * @param x the centered learning sample whose E(x) = 0.
     * @return the approximation error for input sample.
     */
    public double update(double[] x) {
        if (x.length != n) {
            throw new IllegalArgumentException(String.format("Invalid input vector size: %d, expected: %d", x.length, n));
        }

        projection.ax(x, y);

        for (int j = 0; j < p; j++) {
            for (int i = 0; i < n; i++) {
                double delta = x[i];
                for (int l = 0; l <= j; l++) {
                    delta -= projection.get(l, i) * y[l];
                }
                projection.add(j, i, r * y[j] * delta);

                if (Double.isInfinite(projection.get(j, i))) {
                    throw new IllegalStateException("GHA lost convergence. Lower learning rate?");
                }
            }
        }

        projection.ax(x, y);
        projection.atx(y, wy);
        return MathEx.squaredDistance(x, wy);
    }
}
