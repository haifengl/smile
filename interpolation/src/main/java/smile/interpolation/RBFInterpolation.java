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
package smile.interpolation;

import smile.math.Math;
import smile.math.matrix.Matrix;
import smile.math.matrix.DenseMatrix;
import smile.math.rbf.GaussianRadialBasis;
import smile.math.rbf.RadialBasisFunction;
import smile.math.matrix.Cholesky;
import smile.math.matrix.LU;

/**
 * Radial basis function interpolation is a popular method for the data points are
 * irregularly distributed in space. In its basic form, radial basis function
 * interpolation is in the form
 * <p>
 * y(x) = &Sigma; w<sub>i</sub> &phi;(||x-c<sub>i</sub>||)
 * <p>
 * where the approximating function y(x) is represented as a sum of N radial
 * basis functions &phi;, each associated with a different center c<sub>i</sub>, and weighted
 * by an appropriate coefficient w<sub>i</sub>. For distance, one usually chooses
 * euclidean distance. The weights w<sub>i</sub> can
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
 * Other popular choices for &phi; comprise the Gaussian function and the so
 * called thin plate splines. Thin plate splines result from the solution of
 * a variational problem. The advantage of the thin plate splines is that
 * their conditioning is invariant under scalings. Gaussians, multi-quadrics
 * and inverse multi-quadrics are infinitely smooth and and involve a scale
 * or shape parameter, r<sub><small>0</small></sub> &gt; 0.
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
public class RBFInterpolation {

    /**
     * The control points.
     */
    private double[][] x;
    /**
     * The linear weights.
     */
    private double[] w;
    /**
     * The radial basis function.
     */
    private RadialBasisFunction rbf;
    /**
     * True to fit a normalized rbf interpolation.
     */
    private boolean normalized;

    /**
     * Constructor. By default, it is a regular rbf interpolation without
     * normalization.
     * @param x the point set.
     * @param y the function values at given points.
     * @param normalized the radial basis function used in the interpolation
     */
    public RBFInterpolation(double[][] x, double[] y, RadialBasisFunction normalized) {
        this(x, y, normalized, false);
    }

    /**
     * Constructor.
     * @param x the point set.
     * @param y the function values at given points.
     * @param rbf the radial basis function used in the interpolation
     * @param normalized true for the normalized RBF interpolation.
     */
    public RBFInterpolation(double[][] x, double[] y, RadialBasisFunction rbf, boolean normalized) {
        this.x = x;
        this.rbf = rbf;
        this.normalized = normalized;

        int n = x.length;

        DenseMatrix G = Matrix.zeros(n, n);
        double[] rhs = new double[n];
        for (int i = 0; i < n; i++) {
            double sum = 0.0;
            for (int j = 0; j <= i; j++) {
                double r = rbf.f(Math.distance(x[i], x[j]));
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
            Cholesky cholesky = G.cholesky();
            cholesky.solve(rhs);
            w = rhs;
        } else {
            LU lu = G.lu(true);
            lu.solve(rhs);
            w = rhs;
        }
    }

    /**
     * Interpolate the function at given point.
     */
    public double interpolate(double... x) {
        if (x.length != this.x[0].length) {
            throw new IllegalArgumentException(String.format("Invalid input vector size: %d, expected: %d", x.length, this.x[0].length));
        }

        double sum = 0.0, sumw = 0.0;
        for (int i = 0; i < this.x.length; i++) {
            double f = rbf.f(Math.distance(x, this.x[i]));
            sumw += w[i] * f;
            sum += f;
        }

        return normalized ? sumw / sum : sumw;
    }
}
