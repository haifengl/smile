/*
 * Copyright (c) 2010-2020 Haifeng Li. All rights reserved.
 *
 * Smile is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of
 * the License, or (at your option) any later version.
 *
 * Smile is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Smile.  If not, see <https://www.gnu.org/licenses/>.
 */

package smile.math.kernel;

import smile.math.MathEx;

/**
 * The polynomial kernel on binary sparse data.
 * <p>
 *     k(u, v) = (&gamma; u<sup>T</sup>v - &lambda;)<sup>d</sup>
 * <p>
 * where &gamma; is the scale of the used inner product, &lambda; the offset of
 * the used inner product, and <i>d</i> the order of the polynomial kernel.
 * The kernel works on sparse binary array as {@code int[]}, which are the indices of
 * nonzero elements.
 * 
 * @author Haifeng Li
 */
public class BinarySparsePolynomialKernel extends Polynomial implements MercerKernel<int[]> {
    /**
     * Constructor with scale 1 and offset 0.
     * @param degree The degree of polynomial.
     */
    public BinarySparsePolynomialKernel(int degree) {
        this(degree, 1.0, 0.0);
    }

    /**
     * Constructor.
     * @param degree The degree of polynomial.
     * @param scale The scale parameter.
     * @param offset The offset parameter.
     */
    public BinarySparsePolynomialKernel(int degree, double scale, double offset) {
        this(degree, scale, offset, new double[]{1E-2, 1E-5}, new double[]{1E2, 1E5});
    }

    /**
     * Constructor.
     * @param degree The degree of polynomial. The degree is fixed during hyperparameter tuning.
     * @param scale The scale parameter.
     * @param offset The offset parameter.
     * @param lo The lower bound of scale and offset for hyperparameter tuning.
     * @param hi The upper bound of scale and offset for hyperparameter tuning.
     */
    public BinarySparsePolynomialKernel(int degree, double scale, double offset, double[] lo, double[] hi) {
        super(degree, scale, offset, lo, hi);
    }

    @Override
    public double k(int[] x, int[] y) {
        return k(MathEx.dot(x, y));
    }

    @Override
    public double[] kg(int[] x, int[] y) {
        return kg(MathEx.dot(x, y));
    }

    @Override
    public BinarySparsePolynomialKernel of(double[] params) {
        return new BinarySparsePolynomialKernel(degree, params[0], params[1], lo, hi);
    }

    @Override
    public double[] hyperparameters() {
        return new double[] { scale, offset };
    }

    @Override
    public double[] lo() {
        return lo;
    }

    @Override
    public double[] hi() {
        return hi;
    }
}
