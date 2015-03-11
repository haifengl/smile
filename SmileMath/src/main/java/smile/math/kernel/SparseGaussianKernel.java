/******************************************************************************
 *                   Confidential Proprietary                                 *
 *         (c) Copyright Haifeng Li 2011, All Rights Reserved                 *
 ******************************************************************************/

package smile.math.kernel;

import smile.math.SparseArray;
import smile.math.Math;

/**
 * The Gaussian Mercer Kernel. k(u, v) = e<sup>-||u-v||<sup>2</sup> / (2 * &sigma;<sup>2</sup>)</sup>,
 * where &sigma; > 0 is the scale parameter of the kernel.
 * <p>
 * The Gaussian kernel is a good choice for a great deal of applications,
 * although sometimes it is remarked as being over used.

 * @author Haifeng Li
 */
public class SparseGaussianKernel implements MercerKernel<SparseArray> {

    /**
     * The width of the kernel.
     */
    private double gamma;
    
    /**
     * Constructor.
     * @param sigma the smooth/width parameter of Gaussian kernel.
     */
    public SparseGaussianKernel(double sigma) {
        if (sigma <= 0)
            throw new IllegalArgumentException("sigma is not positive.");

        this.gamma = 0.5 / (sigma * sigma);
    }

    @Override
    public String toString() {
        return String.format("Sparse Gaussian Kernel (\u02E0 = %.4f)", Math.sqrt(0.5/gamma));
    }

    @Override
    public double k(SparseArray x, SparseArray y) {
        return Math.exp(-gamma * Math.squaredDistance(x, y));
    }
}
