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

package smile.interpolation;

import smile.math.MathEx;
import smile.math.blas.UPLO;
import smile.math.matrix.Matrix;

/**
 * Kriging interpolation for the data points irregularly distributed in space.
 * Kriging belongs to the family of linear least squares estimation algorithms,
 * also known as Gauss-Markov estimation or Gaussian process regression.
 * This class implements ordinary kriging for interpolation with power variogram.
 *
 * @author Haifeng Li
 */
public class KrigingInterpolation1D implements Interpolation {

    /** The control points. */
    private final double[] x;
    /** The linear weights. */
    private final double[] yvi;
    /** The parameter of power variogram. */
    private final double alpha;
    /** The parameter of power variogram. */
    private final double beta;

    /**
     * Constructor. The power variogram is employed for interpolation.
     *
     * @param x the tabulated points.
     * @param y the function values at <code>x</code>.
     */
    public KrigingInterpolation1D(double[] x, double[] y) {
        this(x, y, 1.5);
    }

    /**
     * Constructor. The power variogram is employed for interpolation.
     *
     * @param x the tabulated points.
     * @param y the function values at <code>x</code>.
     * @param beta the parameter of power variogram. The value of &beta;
     *             should be in the range {@code 1 <=} &beta; {@code < 2}.
     *             A good general choice is 1.5, but for functions with
     *             a strong linear trend, we may experiment with values as
     *             large as 1.99.
     */
    public KrigingInterpolation1D(double[] x, double[] y, double beta) {
        if (beta < 1.0 || beta >= 2.0) {
            throw new IllegalArgumentException("Invalid beta: " + beta);
        }

        if (x.length != y.length) {
            throw new IllegalArgumentException("x.length != y.length");
        }

        this.x = x;
        this.beta = beta;
        this.alpha = pow(x, y);

        int n = x.length;
        double[] yv = new double[n + 1];

        Matrix v = new Matrix(n + 1, n + 1);
        v.uplo(UPLO.LOWER);
        for (int i = 0; i < n; i++) {
            yv[i] = y[i];

            for (int j = i; j < n; j++) {
                double var = variogram(Math.abs(x[i] - x[j]));
                v.set(i, j, var);
                v.set(j, i, var);
            }
            v.set(n, i, 1.0);
            v.set(i, n, 1.0);
        }

        yv[n] = 0.0;
        v.set(n, n, 0.0);

        Matrix.SVD svd = v.svd(true, true);
        yvi = svd.solve(yv);
    }

    @Override
    public double interpolate(double x) {
        int n = this.x.length;
        double y = yvi[n];
        for (int i = 0; i < n; i++) {
            y += yvi[i] * variogram(Math.abs(x - this.x[i]));
        }
        return y;
    }

    private double pow(double[] x, double[] y) {
        int n = x.length;

        double num = 0.0, denom = 0.0;
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                double rb = MathEx.pow2(x[i] - x[j]);
                rb = Math.pow(rb, 0.5 * beta);
                num += rb * 0.5 * MathEx.pow2(y[i] - y[j]);
                denom += rb * rb;
            }
        }

        return num / denom;
    }

    private double variogram(double r) {
        return alpha * Math.pow(r, beta);
    }

    @Override
    public String toString() {
        return "Kriging Interpolation";
    }
}
