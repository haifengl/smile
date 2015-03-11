/******************************************************************************
 *                   Confidential Proprietary                                 *
 *         (c) Copyright Haifeng Li 2011, All Rights Reserved                 *
 ******************************************************************************/
package smile.interpolation;

/**
 * Piecewise linear interpolation. Linear interpolation, sometimes known as
 * lerp, is quick and easy, but it is not very precise. Another disadvantage
 * is that the interpolant is not differentiable at the control points x.
 * 
 * @author Haifeng Li
 */
public class LinearInterpolation extends AbstractInterpolation {

    /**
     * Constructor.
     */
    public LinearInterpolation(double[] x, double[] y) {
        super(x, y);
    }

    @Override
    double rawinterp(int j, double x) {
        if (xx[j] == xx[j + 1]) {
            return yy[j];
        } else {
            return yy[j] + ((x - xx[j]) / (xx[j + 1] - xx[j])) * (yy[j + 1] - yy[j]);
        }
    }
}

