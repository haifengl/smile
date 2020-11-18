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
 * The class of Matérn kernels is a generalization of the Gaussian/RBF.
 * It has an additional parameter nu which controls the smoothness of
 * the kernel function. The smaller nu, the less smooth the approximated
 * function is. As nu -> inf, the kernel becomes equivalent to the
 * Gaussian/RBF kernel. When nu = 1/2, the kernel becomes identical to the
 * Laplacian kernel. The Matern kernel become especially simple
 * when nu is half-integer. Important intermediate values are 3/2
 * (once differentiable functions) and 5/2 (twice differentiable functions).
 *
 * @see BinarySparseGaussianKernel
 * @see BinarySparseLaplacianKernel
 *
 * @author Haifeng Li
 */
public class BinarySparseMaternKernel extends Matern implements MercerKernel<int[]> {
    /**
     * Constructor.
     * @param length The length scale of kernel.
     * @param nu The smoothness of the kernel function. Only 0.5, 1.5, 2.5 and Inf are accepted.
     */
    public BinarySparseMaternKernel(double length, int nu) {
        super(length, nu);
    }

    @Override
    public double k(int[] x, int[] y) {
        return k(MathEx.distance(x, y));
    }
}
