/******************************************************************************
 *                   Confidential Proprietary                                 *
 *         (c) Copyright Haifeng Li 2011, All Rights Reserved                 *
 ******************************************************************************/

package smile.math.kernel;

import java.util.Iterator;

import smile.math.Math;
import smile.math.SparseArray;

/**
 * The Thin Plate Spline Kernel. k(u, v) = (||u-v|| / &sigma;)<sup>2</sup> log (||u-v|| / &sigma;),
 * where &sigma; > 0 is the scale parameter of the kernel.
 * 
 * @author Haifeng Li
 */
public class SparseThinPlateSplineKernel implements MercerKernel<SparseArray> {

    /**
     * The width of the kernel.
     */
    private double sigma;

    /**
     * Constructor.
     * @param sigma the smooth/width parameter of Thin Plate Spline kernel.
     */
    public SparseThinPlateSplineKernel(double sigma) {
        if (sigma <= 0)
            throw new IllegalArgumentException("sigma is not positive.");

        this.sigma = sigma;
    }

    @Override
    public String toString() {
        return String.format("Sparse Thin Plate Spline Kernel (\u02E0 = %.4f)", sigma);
    }

    @Override
    public double k(SparseArray x, SparseArray y) {
        Iterator<SparseArray.Entry> it1 = x.iterator();
        Iterator<SparseArray.Entry> it2 = y.iterator();
        SparseArray.Entry e1 = it1.hasNext() ? it1.next() : null;
        SparseArray.Entry e2 = it2.hasNext() ? it2.next() : null;

        double s = 0.0;
        while (e1 != null && e2 != null) {
            if (e1.i == e2.i) {
                s += Math.sqr(e1.x - e2.x);
                e1 = it1.hasNext() ? it1.next() : null;
                e2 = it2.hasNext() ? it2.next() : null;
            } else if (e1.i > e2.i) {
                s += Math.sqr(e2.x);
                e2 = it2.hasNext() ? it2.next() : null;
            } else {
                s += Math.sqr(e1.x);
                e1 = it1.hasNext() ? it1.next() : null;
            }
        }
        
        while (it1.hasNext()) {
            s += Math.sqr(it1.next().x);
        }

        while (it2.hasNext()) {
            s += Math.sqr(it2.next().x);
        }

        return s/(sigma*sigma) * Math.log(Math.sqrt(s)/sigma);
    }
}
