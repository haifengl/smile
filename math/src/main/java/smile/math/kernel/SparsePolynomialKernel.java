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
import smile.util.SparseArray;

/**
 * The polynomial kernel on sparse data.
 * <p>
 *     k(u, v) = (&gamma; u<sup>T</sup>v - &lambda;)<sup>d</sup>
 * <p>
 * where &gamma; is the scale of the used inner product, &lambda; the offset of
 * the used inner product, and <i>d</i> the order of the polynomial kernel.
 * 
 * @author Haifeng Li
 */
public class SparsePolynomialKernel extends Polynomial implements MercerKernel<SparseArray> {
    /**
     * Constructor with scale 1 and offset 0.
     * @param degree The degree of polynomial.
     */
    public SparsePolynomialKernel(int degree) {
        this(degree, 1.0, 0.0);
    }

    /**
     * Constructor.
     * @param degree The degree of polynomial.
     * @param scale The scale parameter.
     * @param offset The offset parameter.
     */
    public SparsePolynomialKernel(int degree, double scale, double offset) {
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
    public SparsePolynomialKernel(int degree, double scale, double offset, double[] lo, double[] hi) {
        super(degree, scale, offset, lo, hi);
    }

    @Override
    public double k(SparseArray x, SparseArray y) {
        return k(MathEx.dot(x, y));
    }

    @Override
    public double[] kg(SparseArray x, SparseArray y) {
        return kg(MathEx.dot(x, y));
    }

    @Override
    public SparsePolynomialKernel of(double[] params) {
        return new SparsePolynomialKernel(degree, params[0], params[1], lo, hi);
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
