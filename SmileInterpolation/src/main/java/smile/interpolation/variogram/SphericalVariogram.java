/******************************************************************************
 *                   Confidential Proprietary                                 *
 *         (c) Copyright Haifeng Li 2011, All Rights Reserved                 *
 ******************************************************************************/

package smile.interpolation.variogram;

/**
 * Spherical variogram.
 * <p>
 * v(r) = c + b * (1.5 * r / a - 0.5 * (r / a)<sup>3</sup>) for 0 &le; r &le; a
 * <p>
 * or
 * <p>
 * v(r) = c + b for a &le; r
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
public class SphericalVariogram implements Variogram {
    private double a;
    private double b;
    private double c;

    /**
     * Constructor. No nugget effect.
     * @param a the range parameter.
     * @param b the sill parameter.
     */
    public SphericalVariogram(double a, double b) {
        this(a, b, 0.0);
    }

    /**
     * Constructor.
     * @param a the range parameter.
     * @param b the sill parameter.
     * @param c the nugget effect parameter.
     */
    public SphericalVariogram(double a, double b, double c) {
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
        if (a <= r)
            return c + b;

        double p = r / a;
        return c + b * (1.5 * p - 0.5 * p * p * p);
    }
}
