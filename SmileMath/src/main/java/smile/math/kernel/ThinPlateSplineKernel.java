/******************************************************************************
 *                   Confidential Proprietary                                 *
 *         (c) Copyright Haifeng Li 2011, All Rights Reserved                 *
 ******************************************************************************/

package smile.math.kernel;

import smile.math.Math;

/**
 * The Thin Plate Spline Kernel. k(u, v) = (||u-v|| / &sigma;)<sup>2</sup> log (||u-v|| / &sigma;),
 * where &sigma; > 0 is the scale parameter of the kernel.
 * 
 * @author Haifeng Li
 */
public class ThinPlateSplineKernel implements MercerKernel<double[]> {

    /**
     * The width of the kernel.
     */
    private double sigma;

    /**
     * Constructor.
     * @param sigma the smooth/width parameter of Thin Plate Spline kernel.
     */
    public ThinPlateSplineKernel(double sigma) {
        if (sigma <= 0)
            throw new IllegalArgumentException("sigma is not positive.");

        this.sigma = sigma;
    }

    @Override
    public String toString() {
        return String.format("Thin Plate Spline Kernel (\u02E0 = %.4f)", sigma);
    }

    @Override
    public double k(double[] x, double[] y) {
        if (x.length != y.length)
            throw new IllegalArgumentException(String.format("Arrays have different length: x[%d], y[%d]", x.length, y.length));
        
        double d = 0.0;

        for (int i = 0; i < x.length; i++) {
            double dxy = x[i] - y[i];
            d += dxy * dxy;
        }
            
        return d/(sigma*sigma) * Math.log(Math.sqrt(d)/sigma);
    }
}
