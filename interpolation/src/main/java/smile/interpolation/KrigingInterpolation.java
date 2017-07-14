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
import smile.math.matrix.LU;
import smile.interpolation.variogram.PowerVariogram;
import smile.interpolation.variogram.Variogram;

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

    private double[][] x;
    private Variogram variogram;
    private double[] yvi;
    private double[] vstar;

    /**
     * Constructor. The power variogram is employed. We assume no errors,
     * i.e. we are doing interpolation rather fitting.
     */
    public KrigingInterpolation(double[][] x, double[] y) {
        this(x, y, new PowerVariogram(x, y), null);
    }

    /**
     * Constructor.
     * @param x the point set.
     * @param y the function values at given points.
     * @param variogram the variogram function of offset distance to estimate
     * the mean square variation of function y(x).
     * @param error the measure error associated with y. It is the sqrt of diagonal
     * elements of covariance matrix.
     */
    public KrigingInterpolation(double[][] x, double[] y, Variogram variogram, double[] error) {
        this.x = x;
        this.variogram = variogram;

        int n = x.length;

        yvi = new double[n + 1];
        vstar = new double[n + 1];
        DenseMatrix v = Matrix.zeros(n + 1, n + 1);

        for (int i = 0; i < n; i++) {
            yvi[i] = y[i];

            for (int j = i; j < n; j++) {
                double var = variogram.f(rdist(x[i], x[j]));
                v.set(i, j, var);
                v.set(j, i, var);
            }
            v.set(n, i, 1.0);
            v.set(i, n, 1.0);
        }

        yvi[n] = 0.0;
        v.set(n, n, 0.0);

        if (error != null) {
            for (int i = 0; i < n; i++) {
                v.sub(i, i, error[i] * error[i]);
            }
        }

        LU lu = v.lu(true);
        lu.solve(yvi);
    }

    /**
     * Interpolate the function at given point.
     */
    public double interpolate(double... x) {
        if (x.length != this.x[0].length) {
            throw new IllegalArgumentException(String.format("Invalid input vector size: %d, expected: %d", x.length, this.x[0].length));
        }

        int n = this.x.length;
        for (int i = 0; i < n; i++) {
            vstar[i] = variogram.f(rdist(x, this.x[i]));
        }
        vstar[n] = 1.0;

        double y = 0.0;
        for (int i = 0; i <= n; i++) {
            y += yvi[i] * vstar[i];
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
}
