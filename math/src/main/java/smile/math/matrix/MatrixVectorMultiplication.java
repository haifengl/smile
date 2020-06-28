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
 * Matrix-vector multiplication.
 *
 * @author Haifeng Li
 */
public interface MatrixVectorMultiplication<T> {
    /**
     * Returns the number of rows.
     */
    int nrows();

    /**
     * Returns the number of columns.
     */
    int ncols();

    /**
     * Returns the matrix-vector multiplication A * x.
     */
    default T mv(T x) {
        return mv(Transpose.NO_TRANSPOSE, x);
    }

    /**
     * Matrix-vector multiplication y = A * x.
     */
    default void mv(T x, T y) {
        mv(Transpose.NO_TRANSPOSE, x, y);
    }

    /**
     * Matrix-vector multiplication A * x.
     * @param work the workspace for both input and output vector.
     * @param inputOffset the offset of input vector in workspace.
     * @param outputOffset the offset of output vector in workspace.
     */
    default void mv(T work, int inputOffset, int outputOffset) {
        mv(Transpose.NO_TRANSPOSE, work, inputOffset, outputOffset);
    }

    /**
     * Matrix-vector multiplication with transpose option.
     */
    T mv(Transpose trans, T x);

    /**
     * Matrix-vector multiplication with transpose option.
     * y = A' * x.
     */
    void mv(Transpose trans, T x, T y);

    /**
     * Matrix-vector multiplication with transpose option.
     * @param work the workspace for both input and output vector.
     * @param inputOffset the offset of input vector in workspace.
     * @param outputOffset the offset of output vector in workspace.
     */
    void mv(Transpose trans, T work, int inputOffset, int outputOffset);
}
