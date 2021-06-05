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
 * The hyperbolic tangent kernel.
 * <p>
 *     k(u, v) = tanh(&gamma; u<sup>T</sup>v - &lambda;)
 * <p>
 * where &gamma; is the scale of the used inner product and &lambda; is
 * the offset of the used inner product. If the offset is negative the
 * likelihood of obtaining a kernel matrix that is not positive definite
 * is much higher (since then even some diagonal elements may be negative),
 * hence if this kernel has to be used, the offset should always be positive.
 * Note, however, that this is no guarantee that the kernel will be positive.
 * <p>
 * The hyperbolic tangent kernel was quite popular for support vector machines
 * due to its origin from neural networks. However, it should be used carefully
 * since the kernel matrix may not be positive semi-definite. Besides, it was
 * reported the hyperbolic tangent kernel is not better than the Gaussian kernel
 * in general.
 *
 * @author Haifeng Li
 */
public class HyperbolicTangent implements DotProductKernel {
    private static final long serialVersionUID = 2L;

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
     * @param scale The scale parameter.
     * @param offset The offset parameter.
     * @param lo The lower bound of scale and offset for hyperparameter tuning.
     * @param hi The upper bound of scale and offset for hyperparameter tuning.
     */
    public HyperbolicTangent(double scale, double offset, double[] lo, double[] hi) {
        this.scale = scale;
        this.offset = offset;
        this.lo = lo;
        this.hi = hi;
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
        return String.format("TanhKernel(%.4f, %.4f)", scale, offset);
    }

    @Override
    public double k(double dot) {
        return Math.tanh(scale * dot + offset);
    }

    @Override
    public double[] kg(double dot) {
        double[] g = new double[3];
        double cosh = Math.cosh(scale * dot + offset);
        double sech = 1.0 / (cosh * cosh);
        g[0] = Math.tanh(scale * dot + offset);
        g[1] = dot * sech;
        g[2] = sech;
        return g;
    }
}
