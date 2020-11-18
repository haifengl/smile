/*******************************************************************************
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
 ******************************************************************************/

package smile.math.kernel;

/**
 * The polynomial kernel.
 * <p>
 * <pre>
 *     k(u, v) = (&gamma; u<sup>T</sup>v - &lambda;)<sup>d</sup>
 * </pre>
 * where &gamma; is the scale of the used inner product, &lambda; the offset of
 * the used inner product, and <i>d</i> the order of the polynomial kernel.
 *
 * @author Haifeng Li
 */
public class Polynomial implements DotProductKernel {
    private static final long serialVersionUID = 2L;

    private final int degree;
    private final double scale;
    private final double offset;

    /**
     * Constructor with scale 1 and bias 0.
     */
    public Polynomial(int degree) {
        this(degree, 1.0, 0.0);
    }

    /**
     * Constructor.
     */
    public Polynomial(int degree, double scale, double offset) {
        if (degree <= 0) {
            throw new IllegalArgumentException("Non-positive polynomial degree: " + degree);
        }

        if (offset < 0.0) {
            throw new IllegalArgumentException("Negative offset: the kernel does not satisfy Mercer's condition: " + offset);
        }

        this.degree = degree;
        this.scale = scale;
        this.offset = offset;
    }

    /** Returns the degree of kernel. */
    public int degree() {
        return degree;
    }

    /** Returns the scale of kernel. */
    public double scale() {
        return scale;
    }

    /** Returns the offset of kernel. */
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
}
