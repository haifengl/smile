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

import smile.math.MathEx;

/**
 * The hyperbolic tangent kernel.
 * <p>
 * <pre>
 *     k(u, v) = tanh(&gamma; u<sup>T</sup>v - &lambda;)
 * </pre>
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

    private final double scale;
    private final double offset;

    /**
     * Constructor.
     */
    public HyperbolicTangent() {
        this(1, 0);
    }

    /**
     * Constructor.
     */
    public HyperbolicTangent(double scale, double offset) {
        this.scale = scale;
        this.offset = offset;
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
        return String.format("HyperbolicTangent(%.4f, %.4f)", scale, offset);
    }

    @Override
    public double k(double dot) {
        return Math.tanh(scale * dot + offset);
    }
}
