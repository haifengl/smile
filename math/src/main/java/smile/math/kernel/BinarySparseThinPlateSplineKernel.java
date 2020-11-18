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
 * The Thin Plate Spline Kernel on binary sparse data.
 * <p>
 * <pre>
 *     k(u, v) = (||u-v|| / &sigma;)<sup>2</sup> log (||u-v|| / &sigma;)
 * </pre>
 * where <code>&sigma; &gt; 0</code> is the scale parameter of the kernel.
 * The kernel can work on sparse binary array as int[], which are the
 * indices of nonzero elements.
 * 
 * @author Haifeng Li
 */
public class BinarySparseThinPlateSplineKernel implements MercerKernel<int[]>, IsotropicKernel {
    private static final long serialVersionUID = 2L;

    /**
     * The width of the kernel.
     */
    private double sigma;

    /**
     * Constructor.
     * @param sigma the smooth/width parameter of Thin Plate Spline kernel.
     */
    public BinarySparseThinPlateSplineKernel(double sigma) {
        if (sigma <= 0) {
            throw new IllegalArgumentException("sigma is not positive: " + sigma);
        }

        this.sigma = sigma;
    }

    @Override
    public String toString() {
        return String.format("Sparse Linear Thin Plate Spline Kernel (sigma = %.4f)", sigma);
    }

    @Override
    public double k(double dist) {
        return dist/(sigma*sigma) * Math.log(Math.sqrt(dist)/sigma);
    }

    @Override
    public double k(int[] x, int[] y) {
        return k(MathEx.distance(x, y));
    }
}
