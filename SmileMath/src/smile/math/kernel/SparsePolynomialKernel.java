/******************************************************************************
 *                   Confidential Proprietary                                 *
 *         (c) Copyright Haifeng Li 2011, All Rights Reserved                 *
 ******************************************************************************/

package smile.math.kernel;

import smile.math.Math;
import smile.math.SparseArray;

/**
 * The polynomial kernel. k(u, v) = (&gamma; u<sup>T</sup>v - &lambda;)<sup>d</sup>,
 * where &gamma; is the scale of the used inner product, &lambda; the offset of
 * the used inner product, and <i>d</i> the order of the polynomial kernel.
 * 
 * @author Haifeng Li
 */
public class SparsePolynomialKernel implements MercerKernel<SparseArray> {

    private int degree;
    private double scale;
    private double offset;

    /**
     * Constructor with scale 1 and bias 0.
     */
    public SparsePolynomialKernel(int degree) {
        this(degree, 1.0, 0.0);
    }

    /**
     * Constructor.
     */
    public SparsePolynomialKernel(int degree, double scale, double offset) {
        if (degree <= 0) {
            throw new IllegalArgumentException("Non-positive polynomial degree.");
        }

        if (offset < 0.0) {
            throw new IllegalArgumentException("Negative offset: the kernel does not satisfy Mercer's condition.");
        }
        
        this.degree = degree;
        this.scale = scale;
        this.offset = offset;
    }

    @Override
    public String toString() {
        return String.format("Sparse Polynomial Kernel (scale = %.4f, offset = %.4f)", scale, offset);
    }

    @Override
    public double k(SparseArray x, SparseArray y) {
        double dot = Math.dot(x, y);
        return Math.pow(scale * dot + offset, degree);
    }
}
