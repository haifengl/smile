/******************************************************************************
 *                   Confidential Proprietary                                 *
 *         (c) Copyright Haifeng Li 2011, All Rights Reserved                 *
 ******************************************************************************/
package smile.math.kernel;

import smile.math.Math;
import smile.math.SparseArray;

/**
 * The linear dot product kernel on sparse arrays. When using a linear kernel, input space is
 * identical to feature space.
 *
 * @author Haifeng Li
 */
public class SparseLinearKernel implements MercerKernel<SparseArray> {

    /**
     * Constructor.
     */
    public SparseLinearKernel() {
    }

    @Override
    public String toString() {
        return "Sparse Linear Kernel";
    }

    @Override
    public double k(SparseArray x, SparseArray y) {        
        return Math.dot(x, y);
    }
}
