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
 * Gaussian kernel, also referred as RBF kernel or squared exponential kernel.
 * <p>
 *     k(u, v) = exp(-||u-v||<sup>2</sup> / 2&sigma;<sup>2</sup>)
 * <p>
 * where &sigma; {@code > 0} is the scale parameter of the kernel.
 * The kernel works on sparse binary array as {@code int[]}, which are
 * the indices of nonzero elements.
 * <p>
 * The Gaussian kernel is a good choice for a great deal of applications,
 * although sometimes it is remarked as being overused.

 * @author Haifeng Li
 */
public class BinarySparseGaussianKernel extends Gaussian implements MercerKernel<int[]> {
    /**
     * Constructor.
     * @param sigma The length scale of kernel.
     */
    public BinarySparseGaussianKernel(double sigma) {
        this(sigma, 1E-05, 1E5);
    }

    /**
     * Constructor.
     * @param sigma The length scale of kernel.
     * @param lo The lower bound of length scale for hyperparameter tuning.
     * @param hi The upper bound of length scale for hyperparameter tuning.
     */
    public BinarySparseGaussianKernel(double sigma, double lo, double hi) {
        super(sigma, lo, hi);
    }

    @Override
    public double k(int[] x, int[] y) {
        return k(MathEx.distance(x, y));
    }

    @Override
    public double[] kg(int[] x, int[] y) {
        return kg(MathEx.distance(x, y));
    }

    @Override
    public BinarySparseGaussianKernel of(double[] params) {
        return new BinarySparseGaussianKernel(params[0], lo, hi);
    }

    @Override
    public double[] hyperparameters() {
        return new double[] { sigma };
    }

    @Override
    public double[] lo() {
        return new double[] { lo };
    }

    @Override
    public double[] hi() {
        return new double[] { hi };
    }
}
