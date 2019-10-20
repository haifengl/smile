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

import smile.math.MathEx;
import smile.util.SparseArray;

/**
 * The linear dot product kernel on sparse arrays. When using a linear kernel, input space is
 * identical to feature space.
 *
 * @author Haifeng Li
 */
public class SparseLinearKernel implements MercerKernel<SparseArray> {
    private static final long serialVersionUID = 1L;

    /**
     * Constructor.
     */
    public SparseLinearKernel() {
    }

    @Override
    public String toString() {
        return "Sparse Linear Kernel";
    }

    @Override
    public double k(SparseArray x, SparseArray y) {        
        return MathEx.dot(x, y);
    }
}
