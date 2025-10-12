/*
 * Copyright (c) 2010-2025 Haifeng Li. All rights reserved.
 *
 * Smile is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Smile is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Smile. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.interpolation;

import smile.interpolation.variogram.PowerVariogram;
import smile.interpolation.variogram.Variogram;
import smile.tensor.DenseMatrix;
import smile.tensor.SVD;
import static smile.linalg.UPLO.*;
import static smile.tensor.ScalarType.*;

/**
 * Kriging interpolation for the data points irregularly distributed in space.
 * Kriging belongs to the family of linear least squares estimation algorithms,
 * also known as Gauss-Markov estimation or Gaussian process regression.
 * <p>
 * Kriging can be either an interpolation method or a fitting method. The distinction
 * between the two is whether the fitted/interpolated function goes exactly
 * through all the input data points (interpolation), or whether it allows
 * measurement errors to be specified and then "smooths" to get a statistically
 * better predictor that does not generally go through the data points.
 * <p>
 * The aim of kriging is to estimate the value of an unknown real-valued
 * function, f, at a point, x, given the values of the function at some
 * other points, x<sub>1</sub>,&hellip;, x<sub>n</sub>. Kriging computes the
 * best linear unbiased estimator based on a stochastic model of the spatial
 * dependence quantified either by the variogram &gamma;(x,y) or by expectation
 * &mu;(x) = E[f(x)] and the covariance function c(x,y) of the random field.
 * A kriging estimator is a linear combination that may be written as
 * <p>
 * &fnof;(x) = &Sigma; &lambda;<sub>i</sub>(x) f(x<sub>i</sub>)
 * <p>
 * The weights &lambda;<sub>i</sub> are solutions of a system of linear
 * equations which is obtained by assuming that f is a sample-path of a
 * random process F(x), and that the error of prediction
 * <p>
 * &epsilon;(x) = F(x) - &Sigma; &lambda;<sub>i</sub>(x) F(x<sub>i</sub>)
 * <p>
 * is to be minimized in some sense.
 * <p>
 * Depending on the stochastic properties of the random field different
 * types of kriging apply. The type of kriging determines the linear
 * constraint on the weights &lambda;<sub>i</sub> implied by the unbiasedness
 * condition; i.e. the linear constraint, and hence the method for calculating
 * the weights, depends upon the type of kriging.
 * <p>
 * This class implements ordinary kriging, which is the most commonly used type
 * of kriging. The typical assumptions for the practical application of ordinary
 * kriging are:
 * <ul>
 * <li> Intrinsic stationarity or wide sense stationarity of the field
 * <li> enough observations to estimate the variogram.
 * </ul>
 * The mathematical condition for applicability of ordinary kriging are:
 * <ul>
 * <li> The mean E[f(x)] = &mu; is unknown but constant
 * <li> The variogram &gamma;(x,y) = E[(f(x) - f(y))<sup>2</sup>] of f(x) is known.
 * </ul>
 * The kriging weights of ordinary kriging fulfill the unbiasedness condition
 * &Sigma; &lambda;<sub>i</sub> = 1
 *
 * @author Haifeng Li
 */
public class KrigingInterpolation {

    /** The control points. */
    private final double[][] x;
    /** The variogram. */
    private final Variogram variogram;
    /** The linear weights. */
    private final double[] yvi;

    /**
     * Constructor. The power variogram is employed. We assume no errors,
     * i.e. we are doing interpolation rather fitting.
     *
     * @param x the data points.
     * @param y the function values at <code>x</code>.
     */
    public KrigingInterpolation(double[][] x, double[] y) {
        this(x, y, new PowerVariogram(x, y), null);
    }

    /**
     * Constructor.
     *
     * @param x the data points.
     * @param y the function values at <code>x</code>.
     * @param variogram the variogram function of offset distance to estimate
     * the mean square variation of function y(x).
     * @param error the measure error associated with y. It is the sqrt of diagonal
     * elements of covariance matrix.
     */
    public KrigingInterpolation(double[][] x, double[] y, Variogram variogram, double[] error) {
        this.x = x;
        this.variogram = variogram;

        int n = x.length;
        double[] yv = new double[n + 1];

        DenseMatrix v = DenseMatrix.zeros(Float64, n + 1, n + 1);
        v.withUplo(LOWER);
        for (int i = 0; i < n; i++) {
            yv[i] = y[i];

            for (int j = i; j < n; j++) {
                double var = variogram.f(rdist(x[i], x[j]));
                v.set(i, j, var);
                v.set(j, i, var);
            }
            v.set(n, i, 1.0);
            v.set(i, n, 1.0);
        }

        yv[n] = 0.0;
        v.set(n, n, 0.0);

        if (error != null) {
            for (int i = 0; i < n; i++) {
                v.sub(i, i, error[i] * error[i]);
            }
        }

        SVD svd = v.svd(true);
        yvi = svd.solve(yv).toArray(new double[0]);
    }

    /**
     * Interpolate the function at given point.
     * @param x a point.
     * @return the interpolated function value.
     */
    public double interpolate(double... x) {
        if (x.length != this.x[0].length) {
            throw new IllegalArgumentException(String.format("Invalid input vector size: %d, expected: %d", x.length, this.x[0].length));
        }

        int n = this.x.length;
        double y = yvi[n];
        for (int i = 0; i < n; i++) {
            y += yvi[i] * variogram.f(rdist(x, this.x[i]));
        }

        return y;
    }

    /**
     * Cartesian distance.
     */
    private double rdist(double[] x1, double[] x2) {
        double d = 0.0;
        for (int i = 0; i < x1.length; i++) {
            double t = x1[i] - x2[i];
            d += t * t;
        }
        return Math.sqrt(d);
    }

    @Override
    public String toString() {
        return String.format("Kriging Interpolation(%s)", variogram);
    }
}
