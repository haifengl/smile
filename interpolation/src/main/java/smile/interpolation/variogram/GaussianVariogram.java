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

package smile.interpolation.variogram;

import smile.math.MathEx;

/**
 * Gaussian variogram.
 * <p>
 * v(r) = c + b * (1 - e<sup>-3r<sup>2</sup>/a<sup>2</sup></sup>)
 * <p>
 * where a is the range parameter and b is sill paramter. The distance of two
 * pairs increase, the variogram of those two pairs also increase. Eventually,
 * the increase of the distance can not cause the variogram increase. The
 * distance which cause the variogram reach plateau is called range. The sill
 * parameter b is the maximum variogram value (by default, we set c = 0).
 * <p>
 * The parameter c is the so-called nugget effect. Though the value of the
 * variogram for h = 0 is strictly 0, several factors, such as sampling error
 * and short scale variability, may cause sample values separated by extremely
 * small distances to be quite dissimilar. This causes a discontinuity at the
 * origin of the variogram. The vertical jump from the value of 0 at the origin
 * to the value of the variogram at extremely small separation distances is
 * called the nugget effect.
 *
 * @author Haifeng Li
 */
public class GaussianVariogram implements Variogram {
    private double a;
    private double b;
    private double c;

    /**
     * Constructor. No nugget effect.
     * @param a the range parameter.
     * @param b the sill parameter.
     */
    public GaussianVariogram(double a, double b) {
        this(a, b, 0.0);
    }

    /**
     * Constructor.
     * @param a the range parameter.
     * @param b the sill parameter.
     * @param c the nugget effect parameter.
     */
    public GaussianVariogram(double a, double b, double c) {
        if (a <= 0)
            throw new IllegalArgumentException("Invalid parameter a = " + a);

        if (b <= 0)
            throw new IllegalArgumentException("Invalid parameter b = " + b);

        if (c < 0)
            throw new IllegalArgumentException("Invalid parameter c = " + c);

        this.a = a;
        this.b = b;
        this.c = c;
    }

    @Override
    public double f(double r) {
        return c + b * (1 - Math.exp(-3.0* MathEx.sqr(r/a)));
    }

    @Override
    public String toString() {
        return String.format("Gaussian Variogram(range = %.4f, sill = %.4f, nugget effect = %.4f)", a, b, c);
    }
}
