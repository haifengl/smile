/*******************************************************************************
 * Copyright (c) 2010-2019 Haifeng Li
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
 *******************************************************************************/

package smile.math.kernel;

/**
 * The Thin Plate Spline Kernel. k(u, v) = (||u-v|| / &sigma;)<sup>2</sup> log (||u-v|| / &sigma;),
 * where &sigma; &gt; 0 is the scale parameter of the kernel. The kernel can work
 * on sparse binary array as int[], which are the indices of nonzero elements.
 * 
 * @author Haifeng Li
 */
public class BinarySparseThinPlateSplineKernel implements MercerKernel<int[]> {
    private static final long serialVersionUID = 1L;

    /**
     * The width of the kernel.
     */
    private double sigma;

    /**
     * Constructor.
     * @param sigma the smooth/width parameter of Thin Plate Spline kernel.
     */
    public BinarySparseThinPlateSplineKernel(double sigma) {
        if (sigma <= 0)
            throw new IllegalArgumentException("sigma is not positive.");

        this.sigma = sigma;
    }

    @Override
    public String toString() {
        return String.format("Sparse Linear Thin Plate Spline Kernel (\u02E0 = %.4f)", sigma);
    }

    @Override
    public double k(int[] x, int[] y) {
        double d = 0.0;

        int p1 = 0, p2 = 0;
        while (p1 < x.length && p2 < y.length) {
            int i1 = x[p1];
            int i2 = y[p2];
            if (i1 == i2) {
                p1++;
                p2++;
            } else if (i1 > i2) {
                d++;
                p2++;
            } else {
                d++;
                p1++;
            }
        }

        d += x.length - p1;
        d += y.length - p2;

        return d/(sigma*sigma) * Math.log(Math.sqrt(d)/sigma);
    }
}
