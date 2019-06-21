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
 * The linear dot product kernel on sparse binary arrays in int[],
 * which are the indices of nonzero elements.
 * When using a linear kernel, input space is identical to feature space.
 *
 * @author Haifeng Li
 */
public class BinarySparseLinearKernel implements MercerKernel<int[]> {
    private static final long serialVersionUID = 1L;

    /**
     * Constructor.
     */
    public BinarySparseLinearKernel() {
    }

    @Override
    public String toString() {
        return "Sparse Binary Linear Kernel";
    }

    @Override
    public double k(int[] x, int[] y) {
        int s = 0;
        for (int p1 = 0, p2 = 0; p1 < x.length && p2 < y.length; ) {
            int i1 = x[p1];
            int i2 = y[p2];
            if (i1 == i2) {
                s++;
                p1++;
                p2++;
            } else if (i1 > i2) {
                p2++;
            } else {
                p1++;
            }
        }
        return s;
    }
}
