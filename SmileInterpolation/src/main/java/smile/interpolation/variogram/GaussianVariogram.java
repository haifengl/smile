/******************************************************************************
 *                   Confidential Proprietary                                 *
 *         (c) Copyright Haifeng Li 2011, All Rights Reserved                 *
 ******************************************************************************/

package smile.interpolation.variogram;

import smile.math.Math;

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
        return c + b * (1 - Math.exp(-3.0*Math.sqr(r/a)));
    }
}
