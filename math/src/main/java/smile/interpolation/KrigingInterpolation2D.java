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
public class KrigingInterpolation2D implements Interpolation2D {

    /** The first dimension of tabulated control points. */
    private final double[] x1;
    /** The second dimension of tabulated control points. */
    private final double[] x2;
    /** The linear weights. */
    private final double[] yvi;
    /** The parameter of power variogram. */
    private final double alpha;
    /** The parameter of power variogram. */
    private final double beta;

    /**
     * Constructor. The power variogram is employed for interpolation.
     * @param x1 the 1st dimension of data points.
     * @param x2 the 2nd dimension of data points.
     * @param y the function values at <code>(x1, x2)</code>.
     */
    public KrigingInterpolation2D(double[] x1, double[] x2, double[] y) {
        this(x1, x2, y, 1.5);
    }

    /**
     * Constructor. The power variogram is employed for interpolation.
     * @param x1 the 1st dimension of data points.
     * @param x2 the 2nd dimension of data points.
     * @param y the function values at <code>(x1, x2)</code>.
     * @param beta the parameter of power variogram. The value of &beta;
     *             should be in the range {@code 1 <=} &beta; {@code < 2}.
     *             A good general choice is 1.5, but for functions with
     *             a strong linear trend, we may experiment with values as
     *             large as 1.99.
     */
    public KrigingInterpolation2D(double[] x1, double[] x2, double[] y, double beta) {
        if (beta < 1.0 || beta >= 2.0) {
            throw new IllegalArgumentException("Invalid beta: " + beta);
        }

        if (x1.length != x2.length) {
            throw new IllegalArgumentException("x1.length != x2.length");
        }

        if (x1.length != y.length) {
            throw new IllegalArgumentException("x.length != y.length");
        }

        this.x1 = x1;
        this.x2 = x2;
        this.beta = beta;
        this.alpha = pow(x1, x2, y);

        int n = x1.length;
        double[] yv = new double[n + 1];

        Matrix v = new Matrix(n + 1, n + 1);
        v.uplo(UPLO.LOWER);
        for (int i = 0; i < n; i++) {
            yv[i] = y[i];

            for (int j = i; j < n; j++) {
                double d1 = x1[i] - x1[j];
                double d2 = x2[i] - x2[j];
                double d = d1 * d1 + d2 * d2;

                double var = variogram(d);
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
    public double interpolate(double x1, double x2) {
        int n = this.x1.length;
        double y = yvi[n];
        for (int i = 0; i < n; i++) {
            double d1 = x1 - this.x1[i];
            double d2 = x2 - this.x2[i];
            double d = d1 * d1 + d2 * d2;

            y += yvi[i] * variogram(d);
        }

        return y;
    }

    private double pow(double[] x1, double[] x2, double[] y) {
        int n = x1.length;

        double num = 0.0, denom = 0.0;
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                double d1 = x1[i] - x1[j];
                double d2 = x2[i] - x2[j];
                double d = d1 * d1 + d2 * d2;

                double rb = Math.pow(d, beta/2);
                num += rb * 0.5 * MathEx.pow2(y[i] - y[j]);
                denom += rb * rb;
            }
        }

        return num / denom;
    }

    private double variogram(double r) {
        return alpha * Math.pow(r, beta/2);
    }

    @Override
    public String toString() {
        return "Kriging Interpolation";
    }
}
