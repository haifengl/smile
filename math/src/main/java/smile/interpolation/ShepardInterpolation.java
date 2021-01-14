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

/**
 * Shepard interpolation is a special case of normalized radial basis function
 * interpolation if the function &phi;(r) goes to infinity as r &rarr; 0, and is
 * finite for {@code r > 0}. In this case, the weights w<sub>i</sub> are just equal to
 * the respective function values y<sub>i</sub>. So we need not solve linear
 * equations and thus it works for very large N.
 * <p>
 * An example of such &phi; is <code>&phi;(r) = r<sup>-p</sup></code> with
 * (typically) {@code 1 < p <= 3}.
 * <p>
 * Shepard interpolation is rarely as accurate as the well-tuned application of
 * other radial basis functions. However, it is simple, fast, and often just the
 * thing for quick and dirty applications.
 *
 * @author Haifeng Li
 */
public class ShepardInterpolation {

    /** The control points. */
    private final double[][] x;
    /** The function values. */
    private final double[] y;
    /** The parameter in the radial basis function. */
    private final double p;

    /**
     * Constructor. By default p = 2.
     *
     * @param x the data points.
     * @param y the function values at <code>x</code>.
     */
    public ShepardInterpolation(double[][] x, double[] y) {
        this(x, y, 2);
    }

    /**
     * Constructor.
     *
     * @param x the data points.
     * @param y the function values at <code>x</code>.
     * @param p the parameter in the radial basis function &phi;(r) = r<sup>-p</sup>.
     */
    public ShepardInterpolation(double[][] x, double[] y, double p) {
        if (x.length != y.length) {
            throw new IllegalArgumentException("x.length != y.length");
        }

        if (p <= 0.0) {
            throw new IllegalArgumentException("Invalid p = " + p);
        }

        this.x = x;
        this.y = y;
        this.p = -p;
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

        double weight = 0.0, sum = 0.0;
        for (int i = 0; i < this.x.length; i++) {
            double r = MathEx.squaredDistance(x, this.x[i]);
            if (r == 0.0) {
                return y[i];
            }
            double w = Math.pow(r, p/2);
            weight += w;
            sum += w * y[i];
        }

        return sum / weight;
    }

    @Override
    public String toString() {
        return String.format("Shepard Interpolation(p = %.4f)", -p);
    }
}
