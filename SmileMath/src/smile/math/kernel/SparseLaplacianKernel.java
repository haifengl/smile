/******************************************************************************
 *                   Confidential Proprietary                                 *
 *         (c) Copyright Haifeng Li 2011, All Rights Reserved                 *
 ******************************************************************************/

package smile.math.kernel;

import smile.math.Math;
import smile.math.SparseArray;

/**
 * The Laplacian Kernel. k(u, v) = e<sup>-||u-v|| / &sigma;</sup>,
 * where &sigma; > 0 is the scale parameter of the kernel.

 * @author Haifeng Li
 */
public class SparseLaplacianKernel implements MercerKernel<SparseArray> {

    /**
     * The width of the kernel.
     */
    private double gamma;

    /**
     * Constructor.
     * @param sigma the smooth/width parameter of Laplacian kernel.
     */
    public SparseLaplacianKernel(double sigma) {
        if (sigma <= 0)
            throw new IllegalArgumentException("sigma is not positive.");

        this.gamma = 1.0 / sigma;
    }

    @Override
    public String toString() {
        return String.format("Sparse Laplacian kernel (\u02E0 = %.4f)", 1.0/gamma);
    }

    @Override
    public double k(SparseArray x, SparseArray y) {
        return Math.exp(-gamma * Math.distance(x, y));
    }
}
