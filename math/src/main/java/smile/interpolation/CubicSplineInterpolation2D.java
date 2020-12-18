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
 * Cubic spline interpolation in a two-dimensional regular grid.
 * It is similar to one-dimensional splines as it guarantees the
 * continuity of the first and second function derivatives. Note
 * that bicubic spline guarantees continuity of only gradient and
 * cross-derivative.
 *
 * @author Haifeng Li
 */
public class CubicSplineInterpolation2D implements Interpolation2D {
    /**
     * The number of control points on the first dimension.
     */
    private final int m;
    /**
     * The function values at xx.
     */
    private final double[]yv;
    /**
     * The first dimension of tabulated control points.
     */
    private final double[] x1;
    /**
     * The interpolation along the second dimension
     * on every control point of first dimension.
     */
    private final CubicSplineInterpolation1D[] srp;

    /**
     * Constructor.
     * @param x1 the 1st dimension of data points.
     * @param x2 the 2nd dimension of data points.
     * @param y the function values at <code>(x1, x2)</code>.
     */
    public CubicSplineInterpolation2D(double[] x1, double[] x2, double[][] y) {
        if (x1.length != y.length) {
            throw new IllegalArgumentException("x1.length != y.length");
        }

        if (x2.length != y[0].length) {
            throw new IllegalArgumentException("x2.length != y[0].length");
        }

        m = x1.length;

        this.x1 = x1;
        yv = new double[m];

        srp = new CubicSplineInterpolation1D[m];
        for (int i = 0; i < m; i++) {
            srp[i] = new CubicSplineInterpolation1D(x2, y[i]);
        }
    }

    @Override
    public double interpolate(double x1p, double x2p) {
        for (int i = 0; i < m; i++) {
            yv[i] = srp[i].interpolate(x2p);
        }

        CubicSplineInterpolation1D scol = new CubicSplineInterpolation1D(x1, yv);

        return scol.interpolate(x1p);
    }

    @Override
    public String toString() {
        return "Cubic Spline Interpolation";
    }
}
