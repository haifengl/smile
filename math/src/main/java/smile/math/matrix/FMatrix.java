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
 * Single precision matrix.
 *
 * @author Haifeng Li
 */
public interface FMatrix extends IMatrix<float[]> {

    /**
     * Matrix-vector multiplication.
     * <pre><code>
     *     y = alpha * A * x + beta * y
     * </code></pre>
     */
    void mv(Transpose trans, float alpha, float[] x, float beta, float[] y);

    @Override
    default float[] mv(float[] x) {
        float[] y = new float[nrows()];
        mv(Transpose.NO_TRANSPOSE, 1.0f, x, 0.0f, y);
        return y;
    }

    @Override
    default void mv(float[] x, float[] y) {
        mv(Transpose.NO_TRANSPOSE, 1.0f, x, 0.0f, y);
    }

    @Override
    default float[] tv(float[] x) {
        float[] y = new float[nrows()];
        mv(Transpose.TRANSPOSE, 1.0f, x, 0.0f, y);
        return y;
    }

    @Override
    default void tv(float[] x, float[] y) {
        mv(Transpose.TRANSPOSE, 1.0f, x, 0.0f, y);
    }
}
