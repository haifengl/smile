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

package smile.interpolation;

/**
 * Cubic spline interpolation in a two-dimensional regular grid.
 * It is similar to one-dimensional splines as it guarantees the
 * continuity of the first and second function
 * derivatives. Note that bicubic spline guarantees continuity of only gradient and
 * cross-derivative.
 *
 * @author Haifeng Li
 */
public class CubicSplineInterpolation2D implements Interpolation2D {
    private int m;
    private double[] x1;
    private double[] yv;
    private CubicSplineInterpolation1D[] srp;

    /**
     * Constructor.
     */
    public CubicSplineInterpolation2D(double[] x1, double[] x2, double[][] y) {
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
        for (int i = 0; i < m; i++)
            yv[i] = srp[i].interpolate(x2p);

        CubicSplineInterpolation1D scol = new CubicSplineInterpolation1D(x1, yv);

        return scol.interpolate(x1p);
    }

    @Override
    public String toString() {
        return "Cubic Spline Interpolation";
    }
}
