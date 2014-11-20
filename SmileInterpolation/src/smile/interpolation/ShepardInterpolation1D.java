/******************************************************************************
 *                   Confidential Proprietary                                 *
 *         (c) Copyright Haifeng Li 2011, All Rights Reserved                 *
 ******************************************************************************/
package smile.interpolation;

import smile.math.Math;

/**
 * Shepard interplation is a special case of normalized radial basis function
 * interpolation if the function &phi;(r) goes to infinity as r &rarr; 0, and is
 * finite for r > 0. In this case, the weights w<sub>i</sub> are just equal to
 * the respective function values y<sub>i</sub>. So we need not solve linear
 * equations and thus it works for very large N.
 * <p>
 * An example of such &phi; is &phi;(r) = r<sup>-p</sup> with (typically)
 * 1 < p &le; 3.
 * <p>
 * Shepard interpolation is rarely as accurate as the well-tuned application of
 * other radial basis functions. However, it is simple, fast, and often jut the
 * thing for quick and dirty applications.
 *
 * @author Haifeng Li
 */
public class ShepardInterpolation1D implements Interpolation {

    private double[] x;
    private double[] y;
    private double p;

    /**
     * Constructor. By default p = 2.
     * @param x the point set.
     * @param y the function values at given points.
     */
    public ShepardInterpolation1D(double[] x, double[] y) {
        this(x, y, 2);
    }

    /**
     * Constructor.
     * @param x the point set.
     * @param y the function values at given points.
     * @param p the parameter in the radial basis function &phi(r) = r<sup>-p</sup>.
     */
    public ShepardInterpolation1D(double[] x, double[] y, double p) {
        this.x = x;
        this.y = y;
        this.p = -p;
    }

    @Override
    public double interpolate(double x) {
        double weight = 0.0, sum = 0.0;
        for (int i = 0; i < this.x.length; i++) {
            double r = Math.abs(x - this.x[i]);
            if (r == 0.0) {
                return y[i];
            }
            double w = Math.pow(r, p);
            weight += w;
            sum += w * y[i];
        }

        return sum / weight;
    }
}
