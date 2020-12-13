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
 * The linear dot product kernel on sparse binary arrays in {@code int[]},
 * which are the indices of nonzero elements.
 * When using a linear kernel, input space is identical to feature space.
 *
 * @author Haifeng Li
 */
public class BinarySparseLinearKernel implements MercerKernel<int[]>, DotProductKernel {
    private static final long serialVersionUID = 2L;

    /**
     * Constructor.
     */
    public BinarySparseLinearKernel() {

    }

    @Override
    public String toString() {
        return "LinearKernel()";
    }

    @Override
    public double k(double dot) {
        return dot;
    }

    @Override
    public double[] kg(double dot) {
        return new double[] { dot };
    }

    @Override
    public double k(int[] x, int[] y) {
        return MathEx.dot(x, y);
    }

    @Override
    public double[] kg(int[] x, int[] y) {
        return new double[] { k(x, y) };
    }

    @Override
    public BinarySparseLinearKernel of(double[] params) {
        return new BinarySparseLinearKernel();
    }

    @Override
    public double[] hyperparameters() {
        return new double[0];
    }

    @Override
    public double[] lo() {
        return new double[0];
    }

    @Override
    public double[] hi() {
        return new double[0];
    }
}
