/******************************************************************************
 *                   Confidential Proprietary                                 *
 *         (c) Copyright Haifeng Li 2011, All Rights Reserved                 *
 ******************************************************************************/

package smile.math.kernel;

import smile.math.Math;

/**
 * The Thin Plate Spline Kernel. k(u, v) = (||u-v|| / &sigma;)<sup>2</sup> log (||u-v|| / &sigma;),
 * where &sigma; > 0 is the scale parameter of the kernel. The kernel can work
 * on sparse binary array as int[], which are the indices of nonzero elements.
 * 
 * @author Haifeng Li
 */
public class BinarySparseThinPlateSplineKernel implements MercerKernel<int[]> {

    /**
     * The width of the kernel.
     */
    private double sigma;

    /**
     * Constructor.
     * @param sigma the smooth/width parameter of Thin Plate Spline kernel.
     */
    public BinarySparseThinPlateSplineKernel(double sigma) {
        if (sigma <= 0)
            throw new IllegalArgumentException("sigma is not positive.");

        this.sigma = sigma;
    }

    @Override
    public String toString() {
        return String.format("Sparse Linear Thin Plate Spline Kernel (\u02E0 = %.4f)", sigma);
    }

    @Override
    public double k(int[] x, int[] y) {
        double d = 0.0;

        int p1 = 0, p2 = 0;
        while (p1 < x.length && p2 < y.length) {
            int i1 = x[p1];
            int i2 = y[p2];
            if (i1 == i2) {
                p1++;
                p2++;
            } else if (i1 > i2) {
                d++;
                p2++;
            } else {
                d++;
                p1++;
            }
        }

        d += x.length - p1;
        d += y.length - p2;

        return d/(sigma*sigma) * Math.log(Math.sqrt(d)/sigma);
    }
}
