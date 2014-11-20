/******************************************************************************
 *                   Confidential Proprietary                                 *
 *         (c) Copyright Haifeng Li 2011, All Rights Reserved                 *
 ******************************************************************************/
package smile.math.kernel;

import smile.math.Math;

/**
 * The linear dot product kernel. When using a linear kernel, input space is
 * identical to feature space.
 *
 * @author Haifeng Li
 */
public class LinearKernel implements MercerKernel<double[]> {

    /**
     * Constructor.
     */
    public LinearKernel() {
    }

    @Override
    public String toString() {
        return "Linear Kernel";
    }

    @Override
    public double k(double[] x, double[] y) {
        return Math.dot(x, y);
    }
}
