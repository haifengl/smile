/******************************************************************************
 *                   Confidential Proprietary                                 *
 *         (c) Copyright Haifeng Li 2011, All Rights Reserved                 *
 ******************************************************************************/

package smile.math.kernel;

import smile.math.Math;

/**
 * The Gaussian Mercer Kernel. k(u, v) = e<sup>-||u-v||<sup>2</sup> / (2 * &sigma;<sup>2</sup>)</sup>,
 * where &sigma; > 0 is the scale parameter of the kernel.
 * <p>
 * The Gaussian kernel is a good choice for a great deal of applications,
 * although sometimes it is remarked as being overused.

 * @author Haifeng Li
 */
public class GaussianKernel implements MercerKernel<double[]> {

    /**
     * The width of the kernel.
     */
    private double gamma;
    
    /**
     * Constructor.
     * @param sigma the smooth/width parameter of Gaussian kernel.
     */
    public GaussianKernel(double sigma) {
        if (sigma <= 0)
            throw new IllegalArgumentException("sigma is not positive.");

        this.gamma = 0.5 / (sigma * sigma);
    }

    @Override
    public String toString() {
        return String.format("Gaussian Kernel (\u02E0 = %.4f)", Math.sqrt(0.5/gamma));
    }

    @Override
    public double k(double[] x, double[] y) {
        if (x.length != y.length)
            throw new IllegalArgumentException(String.format("Arrays have different length: x[%d], y[%d]", x.length, y.length));

        return Math.exp(-gamma * Math.squaredDistance(x, y));
    }
}
