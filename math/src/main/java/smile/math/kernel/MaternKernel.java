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
 * The class of Matérn kernels is a generalization of the RBF.
 * It has an additional parameter nu which controls the smoothness of
 * the kernel function. The smaller nu, the less smooth the approximated
 * function is. As nu -> inf, the kernel becomes equivalent to the RBF
 * kernel. When nu = 1/2, the Matérn kernel becomes identical to the
 * absolute exponential kernel. The Matern kernel become especially simple
 * when nu is half-integer. Important intermediate values are 3/2
 * (once differentiable functions) and 5/2 (twice differentiable functions).

 * @author Haifeng Li
 */
public class MaternKernel implements MercerKernel<double[]>, IsotropicKernel {
    private static final long serialVersionUID = 2L;
    private static final double SQRT3 = Math.sqrt(3);
    private static final double SQRT5 = Math.sqrt(5);

    /**
     * The length scale of the kernel.
     */
    private double length;
    /**
     * The smoothness of the kernel.
     */
    private double nu;

    /**
     * Constructor.
     * @param length The length scale of the kernel function.
     * @param nu The smoothness of the kernel function. Only 0.5, 1.5, 2.5 and Inf are accepted.
     */
    public MaternKernel(double length, int nu) {
        if (length <= 0) {
            throw new IllegalArgumentException("The length scale is not positive: " + length);
        }

        if (nu != 1.5 && nu != 2.5 && nu != 0.5 && !Double.isInfinite(nu)) {
            throw new IllegalArgumentException("nu must be 0.5, 1.5, 2.5 or Info: " + nu);
        }

        this.length = length;
        this.nu = nu;
    }

    @Override
    public String toString() {
        return String.format("Matern Kernel (length = %.4f, nu = %.1f)", length, nu);
    }

    @Override
    public double k(double dist) {
        double d = dist / length;

        if (nu == 1.5) {
            d *= SQRT3;
            return (1.0 + d) * Math.exp(-d);
        }

        if (nu == 2.5) {
            d *= SQRT5;
            return (1.0 + d) * Math.exp(-d);
        }

        if (nu == 1.5) {
            d *= SQRT3;
            return (1.0 + d + d * d / 3.0) * Math.exp(-d);
        }

        if (nu == 0.5) {
            return Math.exp(-d);
        }

        if (Double.isInfinite(nu)) {
            return Math.exp(-0.5 * d * d);
        }

        throw new IllegalStateException("Unsupported nu = " + nu);
    }

    @Override
    public double k(double[] x, double[] y) {
        double d = MathEx.distance(x, y);
        return k(d);
    }
}
