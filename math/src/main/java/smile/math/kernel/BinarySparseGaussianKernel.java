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
 * The Gaussian Kernel on binary sparse data.
 * <p>
 * <pre>
 *     k(u, v) = e<sup>-||u-v||<sup>2</sup> / (2 * &sigma;<sup>2</sup>)</sup>
 * </pre>
 * where <code>&sigma; &gt; 0</code> is the scale parameter of the kernel. The kernel works
 * on sparse binary array as int[], which are the indices of nonzero elements.
 * <p>
 * The Gaussian kernel is a good choice for a great deal of applications,
 * although sometimes it is remarked as being overused.

 * @author Haifeng Li
 */
public class BinarySparseGaussianKernel extends Gaussian implements MercerKernel<int[]> {
    /**
     * Constructor.
     * @param sigma the smooth/width parameter of Gaussian kernel.
     */
    public BinarySparseGaussianKernel(double sigma) {
        super(sigma);
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

        return k(d);
    }
}
