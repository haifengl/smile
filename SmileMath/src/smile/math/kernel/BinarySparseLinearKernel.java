/******************************************************************************
 *                   Confidential Proprietary                                 *
 *         (c) Copyright Haifeng Li 2011, All Rights Reserved                 *
 ******************************************************************************/

package smile.math.kernel;

/**
 * The linear dot product kernel on sparse binary arrays in int[],
 * which are the indices of nonzero elements.
 * When using a linear kernel, input space is identical to feature space.
 *
 * @author Haifeng Li
 */
public class BinarySparseLinearKernel implements MercerKernel<int[]> {

    /**
     * Constructor.
     */
    public BinarySparseLinearKernel() {
    }

    @Override
    public String toString() {
        return "Sparse Binary Linear Kernel";
    }

    @Override
    public double k(int[] x, int[] y) {
        int s = 0;
        for (int p1 = 0, p2 = 0; p1 < x.length && p2 < y.length; ) {
            int i1 = x[p1];
            int i2 = y[p2];
            if (i1 == i2) {
                s++;
                p1++;
                p2++;
            } else if (i1 > i2) {
                p2++;
            } else {
                p1++;
            }
        }
        return s;
    }
}
