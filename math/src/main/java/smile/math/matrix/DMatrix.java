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

package smile.math.matrix;

import smile.math.blas.Transpose;

/**
 * Double precision matrix.
 *
 * @author Haifeng Li
 */
public interface DMatrix extends IMatrix<double[]> {

    /**
     * Matrix-vector multiplication.
     * <pre><code>
     *     y = alpha * A * x + beta * y
     * </code></pre>
     */
    void mv(Transpose trans, double alpha, double[] x, double beta, double[] y);

    @Override
    default double[] mv(double[] x) {
        double[] y = new double[nrows()];
        mv(Transpose.NO_TRANSPOSE, 1.0, x, 0.0, y);
        return y;
    }

    @Override
    default void mv(double[] x, double[] y) {
        mv(Transpose.NO_TRANSPOSE, 1.0, x, 0.0, y);
    }

    @Override
    default double[] tv(double[] x) {
        double[] y = new double[nrows()];
        mv(Transpose.TRANSPOSE, 1.0, x, 0.0, y);
        return y;
    }

    @Override
    default void tv(double[] x, double[] y) {
        mv(Transpose.TRANSPOSE, 1.0, x, 0.0, y);
    }
}
