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

/**
 * The polynomial kernel.
 * <p>
 *     k(u, v) = (&gamma; u<sup>T</sup>v - &lambda;)<sup>d</sup>
 * <p>
 * where &gamma; is the scale of the used inner product, &lambda; the offset of
 * the used inner product, and <i>d</i> the order of the polynomial kernel.
 *
 * @author Haifeng Li
 */
public class Polynomial implements DotProductKernel {
    private static final long serialVersionUID = 2L;

    /** The degree of polynomial. */
    final int degree;
    /** The scale parameter. */
    final double scale;
    /** The offset parameter. */
    final double offset;
    /** The lower bound of scale and offset for hyperparameter tuning. */
    final double[] lo;
    /** The upper bound of scale and offset for hyperparameter tuning. */
    final double[] hi;

    /**
     * Constructor.
     * @param degree The degree of polynomial. The degree is fixed during hyperparameter tuning.
     * @param scale The scale parameter.
     * @param offset The offset parameter.
     * @param lo The lower bound of scale and offset for hyperparameter tuning.
     * @param hi The upper bound of scale and offset for hyperparameter tuning.
     */
    public Polynomial(int degree, double scale, double offset, double[] lo, double[] hi) {
        if (degree <= 0) {
            throw new IllegalArgumentException("Non-positive polynomial degree: " + degree);
        }

        if (offset < 0.0) {
            throw new IllegalArgumentException("Negative offset: the kernel does not satisfy Mercer's condition: " + offset);
        }

        this.degree = degree;
        this.scale = scale;
        this.offset = offset;
        this.lo = lo;
        this.hi = hi;
    }

    /**
     * Returns the degree of polynomial.
     * @return the degree of polynomial.
     */
    public int degree() {
        return degree;
    }

    /**
     * Returns the scale of kernel.
     * @return the scale of kernel.
     */
    public double scale() {
        return scale;
    }

    /**
     * Returns the offset of kernel.
     * @return the offset of kernel.
     */
    public double offset() {
        return offset;
    }

    @Override
    public String toString() {
        return String.format("PolynomialKernel(%d, %.4f, %.4f)", degree, scale, offset);
    }

    @Override
    public double k(double dot) {
        return Math.pow(scale * dot + offset, degree);
    }

    @Override
    public double[] kg(double dot) {
        double[] g = new double[3];
        double pow = Math.pow(scale * dot + offset, degree-1);
        g[0] = Math.pow(scale * dot + offset, degree);
        g[1] = dot * pow;
        g[2] = pow;
        return g;
    }
}
