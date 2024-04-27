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

package smile.interpolation;

import smile.math.blas.UPLO;
import smile.math.matrix.Matrix;
import smile.math.rbf.GaussianRadialBasis;
import smile.math.rbf.RadialBasisFunction;

/**
 * Radial basis function interpolation is a popular method for the data points
 * are irregularly distributed in space. In its basic form, radial basis
 * function interpolation is in the form
 * <p>
 *     y(x) = &Sigma; w<sub>i</sub> &phi;(||x-c<sub>i</sub>||)
 * <p>
 * where the approximating function y(x) is represented as a sum of N radial
 * basis functions &phi;, each associated with a different center c<sub>i</sub>,
 * and weighted by an appropriate coefficient w<sub>i</sub>. For distance,
 * one usually chooses Euclidean distance. The weights w<sub>i</sub> can
 * be estimated using the matrix methods of linear least squares, because
 * the approximating function is linear in the weights.
 * <p>
 * The points c<sub>i</sub> often called the centers or collocation points
 * of the RBF interpolant. Note also that the centers c<sub>i</sub> can be
 * located at arbitrary points in the domain, and do not require a grid.
 * For certain RBF exponential convergence has been shown. Radial basis
 * functions were successfully applied to problems as diverse as computer
 * graphics, neural networks, for the solution of differential equations
 * via collocation methods and many other problems.
 * <p>
 * Other popular choices for &phi; comprise the Gaussian function and the
 * so-called thin plate splines. Thin plate splines result from the solution of
 * a variational problem. The advantage of the thin plate splines is that
 * their conditioning is invariant under scaling. Gaussians, multi-quadrics
 * and inverse multi-quadrics are infinitely smooth and involve a scale
 * or shape parameter, r<sub><small>0</small></sub> {@code > 0}.
 * Decreasing r<sub><small>0</small></sub> tends to
 * flatten the basis function. For a given function, the quality of
 * approximation may strongly depend on this parameter. In particular,
 * increasing r<sub><small>0</small></sub> has the effect of better conditioning
 * (the separation distance  of the scaled points increases).
 * <p>
 * A variant on RBF interpolation is normalized radial basis function (NRBF)
 * interpolation, in which we require the sum of the basis functions to be unity.
 * NRBF arises more naturally from a Bayesian statistical perspective. However,
 * there is no evidence that either the NRBF method is consistently superior
 * to the RBF method, or vice versa.
 *
 * @author Haifeng Li
 */
public class RBFInterpolation2D implements Interpolation2D {

    /**
     * The first dimension of tabulated control points.
     */
    private final double[] x1;
    /**
     * The second dimension of tabulated control points.
     */
    private final double[] x2;
    /**
     * The linear weights.
     */
    private final double[] w;
    /**
     * The radial basis function.
     */
    private final RadialBasisFunction rbf;
    /**
     * True to fit a normalized rbf interpolation.
     */
    private final boolean normalized;

    /**
     * Constructor. By default, it is a regular rbf interpolation without
     * normalization.
     * @param x1 the 1st dimension of data points.
     * @param x2 the 2nd dimension of data points.
     * @param y the function values at <code>(x1, x2)</code>.
     * @param rbf the radial basis function used in the interpolation
     */
    public RBFInterpolation2D(double[] x1, double[] x2, double[] y, RadialBasisFunction rbf) {
        this(x1, x2, y, rbf, false);
    }

    /**
     * Constructor.
     * @param x1 the 1st dimension of data points.
     * @param x2 the 2nd dimension of data points.
     * @param y the function values at <code>(x1, x2)</code>.
     * @param rbf the radial basis function used in the interpolation
     * @param normalized true for the normalized RBF interpolation.
     */
    public RBFInterpolation2D(double[] x1, double[] x2, double[] y, RadialBasisFunction rbf, boolean normalized) {
        if (x1.length != x2.length) {
            throw new IllegalArgumentException("x1.length != x2.length");
        }

        if (x1.length != y.length) {
            throw new IllegalArgumentException("x.length != y.length");
        }

        this.x1 = x1;
        this.x2 = x2;
        this.rbf = rbf;
        this.normalized = normalized;

        int n = y.length;

        Matrix G = new Matrix(n, n);
        double[] rhs = new double[n];
        for (int i = 0; i < n; i++) {
            double sum = 0.0;
            for (int j = 0; j <= i; j++) {
                double d1 = x1[i] - x1[j];
                double d2 = x2[i] - x2[j];
                double d = Math.sqrt(d1 * d1 + d2 * d2);
                double r = rbf.f(d);
                G.set(i, j, r);
                G.set(j, i, r);
                sum += 2 * r;
            }

            if (normalized) {
                rhs[i] = sum * y[i];
            } else {
                rhs[i] = y[i];
            }
        }

        if (rbf instanceof GaussianRadialBasis) {
            G.uplo(UPLO.LOWER);
            Matrix.Cholesky cholesky = G.cholesky(true);
            w = cholesky.solve(rhs);
        } else {
            Matrix.LU lu = G.lu(true);
            w = lu.solve(rhs);
        }
    }

    @Override
    public double interpolate(double x1, double x2) {
        int n = this.x1.length;
        double sum = 0.0, sumw = 0.0;
        for (int i = 0; i < n; i++) {
            double d1 = x1 - this.x1[i];
            double d2 = x2 - this.x2[i];
            double d = Math.sqrt(d1 * d1 + d2 * d2);

            double r = rbf.f(d);
            sumw += w[i] * r;
            sum += r;
        }

        return normalized ? sumw / sum : sumw;
    }

    @Override
    public String toString() {
        return String.format("RBF Interpolation(%s)", rbf);
    }
}
