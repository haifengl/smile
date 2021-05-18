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
 * The class of Matérn kernels is a generalization of the Gaussian/RBF.
 * It has an additional parameter nu which controls the smoothness of
 * the kernel function. The smaller nu, the less smooth the approximated
 * function is. As {@code nu -> inf}, the kernel becomes equivalent to the
 * Gaussian/RBF kernel. When nu = 1/2, the kernel becomes identical to the
 * Laplacian kernel. The Matern kernel become especially simple
 * when nu is half-integer. Important intermediate values are 3/2
 * (once differentiable functions) and 5/2 (twice differentiable functions).
 *
 * @see Gaussian
 * @see Laplacian
 *
 * @author Haifeng Li
 */
public class Matern implements IsotropicKernel {
    private static final long serialVersionUID = 2L;
    private static final double SQRT3 = Math.sqrt(3);
    private static final double SQRT5 = Math.sqrt(5);

    /** The length scale of the kernel. */
    final double sigma;
    /** The smoothness of the kernel. */
    final double nu;
    /** The lower bound of length scale for hyperparameter tuning. */
    final double lo;
    /** The upper bound of length scale for hyperparameter tuning. */
    final double hi;

    /**
     * Constructor.
     * @param sigma The length scale of kernel.
     * @param nu The smoothness of the kernel function. Only 0.5, 1.5, 2.5 and Inf are accepted.
     *           The smoothness parameter is fixed during hyperparameter for tuning.
     * @param lo The lower bound of length scale for hyperparameter tuning.
     * @param hi The upper bound of length scale for hyperparameter tuning.
     */
    public Matern(double sigma, double nu, double lo, double hi) {
        if (sigma <= 0) {
            throw new IllegalArgumentException("The length scale is not positive: " + sigma);
        }

        if (nu != 1.5 && nu != 2.5 && nu != 0.5 && !Double.isInfinite(nu)) {
            throw new IllegalArgumentException("nu must be 0.5, 1.5, 2.5 or Info: " + nu);
        }

        this.sigma = sigma;
        this.nu = nu;
        this.lo = lo;
        this.hi = hi;
    }

    /**
     * Returns the length scale of kernel.
     * @return the length scale of kernel.
     */
    public double scale() {
        return sigma;
    }

    /**
     * Returns the smoothness of kernel.
     * @return the smoothness of kernel.
     */
    public double smoothness() {
        return nu;
    }

    @Override
    public String toString() {
        return String.format("MaternKernel(%.4f, %.1f)", sigma, nu);
    }

    @Override
    public double f(double dist) {
        return k(dist);
    }

    @Override
    public double k(double dist) {
        double d = dist / sigma;

        if (nu == 1.5) {
            d *= SQRT3;
            return (1.0 + d) * Math.exp(-d);
        }

        if (nu == 2.5) {
            d *= SQRT5;
            return (1.0 + d) * Math.exp(-d);
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
    public double[] kg(double dist) {
        double d = dist / sigma;
        double k, g;

        if (nu == 1.5) {
            d *= SQRT3;
            k = (1.0 + d) * Math.exp(-d);
            g = (2.0 + d) * Math.exp(-d) * d / sigma;
        } else if (nu == 2.5) {
            d *= SQRT5;
            k = (1.0 + d) * Math.exp(-d);
            g = (2.0 + d) * Math.exp(-d) * d / sigma;
        } else if (nu == 0.5) {
            k = Math.exp(-d);
            g = k * d / sigma;
        } else if (Double.isInfinite(nu)) {
            k = Math.exp(-0.5 * d * d);
            g = k * d * d / sigma;
        } else {
            throw new IllegalStateException("Unsupported nu = " + nu);
        }

        return new double[] { k, g };
    }
}
