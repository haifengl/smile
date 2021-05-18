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
 * other radial basis functions. However, it is simple, fast, and often jut the
 * thing for quick and dirty applications.
 *
 * @author Haifeng Li
 */
public class ShepardInterpolation2D implements Interpolation2D {

    /** The first dimension of tabulated control points. */
    private final double[] x1;
    /** The second dimension of tabulated control points. */
    private final double[] x2;
    /** The function values. */
    private final double[] y;
    /** The parameter in the radial basis function. */
    private final double p;

    /**
     * Constructor. By default p = 2.
     * @param x1 the 1st dimension of data points.
     * @param x2 the 2nd dimension of data points.
     * @param y the function values at <code>(x1, x2)</code>.
     */
    public ShepardInterpolation2D(double[] x1, double[] x2, double[] y) {
        this(x1, x2, y, 2);
    }

    /**
     * Constructor.
     * @param x1 the 1st dimension of data points.
     * @param x2 the 2nd dimension of data points.
     * @param y the function values at <code>(x1, x2)</code>.
     * @param p the parameter in the radial basis function &phi;(r) = r<sup>-p</sup>.
     */
    public ShepardInterpolation2D(double[] x1, double[] x2, double[] y, double p) {
        if (x1.length != x2.length) {
            throw new IllegalArgumentException("x1.length != x2.length");
        }

        if (x1.length != y.length) {
            throw new IllegalArgumentException("x.length != y.length");
        }

        if (p <= 0.0) {
            throw new IllegalArgumentException("Invalid p = " + p);
        }

        this.x1 = x1;
        this.x2 = x2;
        this.y = y;
        this.p = -p;
    }

    @Override
    public double interpolate(double x1, double x2) {
        double weight = 0.0, sum = 0.0;
        for (int i = 0; i < this.y.length; i++) {
            double d1 = x1 - this.x1[i];
            double d2 = x2 - this.x2[i];
            double r = d1 * d1 + d2 * d2;
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
