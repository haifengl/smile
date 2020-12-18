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
 * Bilinear interpolation in a two-dimensional regular grid. Bilinear
 * interpolation is an extension of linear interpolation for interpolating
 * functions of two variables on a regular grid. The key idea is to perform
 * linear interpolation first in one direction, and then again in the other
 * direction.
 *
 * @author Haifeng Li
 */
public class BilinearInterpolation implements Interpolation2D {

    /**
     * The function values at xx.
     */
    private final double[][] y;
    /**
     * To locate the control point in the first dimension.
     */
    private final LinearInterpolation x1terp;
    /**
     * To locate the control point in the second dimension.
     */
    private final LinearInterpolation x2terp;

    /**
     * Constructor.
     * @param x1 the 1st dimension value.
     * @param x2 the 2nd dimension value.
     * @param y the function values at <code>(x1, x2)</code>.
     */
    public BilinearInterpolation(double[] x1, double[] x2, double[][] y) {
        if (x1.length != y.length) {
            throw new IllegalArgumentException("x1.length != y.length");
        }

        if (x2.length != y[0].length) {
            throw new IllegalArgumentException("x2.length != y[0].length");
        }

        this.y = y;
        x1terp = new LinearInterpolation(x1, x1);
        x2terp = new LinearInterpolation(x2, x2);
    }

    @Override
    public double interpolate(double x1, double x2) {
        int i = x1terp.search(x1);
        int j = x2terp.search(x2);

        double t = (x1-x1terp.xx[i])/(x1terp.xx[i+1]-x1terp.xx[i]);
        double u = (x2-x2terp.xx[j])/(x2terp.xx[j+1]-x2terp.xx[j]);

        return (1.-t)*(1.-u)*y[i][j] + t*(1.-u)*y[i+1][j] + (1.-t)*u*y[i][j+1] + t*u*y[i+1][j+1];
    }

    @Override
    public String toString() {
        return "BiLinear Interpolation";
    }
}