/*
 * Copyright (c) 2010-2025 Haifeng Li. All rights reserved.
 *
 * Smile is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Smile is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Smile. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.math.kernel;

import smile.math.MathEx;

/**
 * The hyperbolic tangent kernel on binary sparse data.
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
 * <p>
 * The kernel works sparse binary array as int[], which are the indices of
 * nonzero elements.
 *
 * @author Haifeng Li
 */
public class BinarySparseHyperbolicTangentKernel extends HyperbolicTangent implements MercerKernel<int[]> {
    /**
     * Constructor with scale 1.0 and offset 0.0.
     */
    public BinarySparseHyperbolicTangentKernel() {
        this(1.0, 0.0);
    }

    /**
     * Constructor.
     * @param scale The scale parameter.
     * @param offset The offset parameter.
     */
    public BinarySparseHyperbolicTangentKernel(double scale, double offset) {
        this(scale, offset, new double[]{1E-2, 1E-5}, new double[]{1E2, 1E5});
    }

    /**
     * Constructor.
     * @param scale The scale parameter.
     * @param offset The offset parameter.
     * @param lo The lower bound of scale and offset for hyperparameter tuning.
     * @param hi The upper bound of scale and offset for hyperparameter tuning.
     */
    public BinarySparseHyperbolicTangentKernel(double scale, double offset, double[] lo, double[] hi) {
        super(scale, offset, lo, hi);
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
    public BinarySparseHyperbolicTangentKernel of(double[] params) {
        return new BinarySparseHyperbolicTangentKernel(params[0], params[1], lo, hi);
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
