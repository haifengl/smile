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
public abstract class FMatrix extends IMatrix<float[]> {
    /**
     * Sets A[i,j] = x.
     */
    public abstract FMatrix set(int i, int j, float x);

    /**
     * Returns A[i, j].
     */
    public abstract float get(int i, int j);

    /**
     * Returns A[i, j]. For Scala users.
     */
    public float apply(int i, int j) {
        return get(i, j);
    }

    @Override
    String str(int i, int j) {
        return String.format("%.4f", get(i, j));
    }

    /**
     * Returns the diagonal elements.
     */
    public float[] diag() {
        int n = Math.min(nrows(), ncols());

        float[] d = new float[n];
        for (int i = 0; i < n; i++) {
            d[i] = get(i, i);
        }

        return d;
    }

    /**
     * Returns the matrix trace. The sum of the diagonal elements.
     */
    public float trace() {
        int n = Math.min(nrows(), ncols());

        float t = 0.0f;
        for (int i = 0; i < n; i++) {
            t += get(i, i);
        }

        return t;
    }

    /**
     * Matrix-vector multiplication.
     * <pre><code>
     *     y = alpha * A * x + beta * y
     * </code></pre>
     */
    public abstract void mv(Transpose trans, float alpha, float[] x, float beta, float[] y);

    @Override
    public float[] mv(float[] x) {
        float[] y = new float[nrows()];
        mv(Transpose.NO_TRANSPOSE, 1.0f, x, 0.0f, y);
        return y;
    }

    @Override
    public void mv(float[] x, float[] y) {
        mv(Transpose.NO_TRANSPOSE, 1.0f, x, 0.0f, y);
    }

    @Override
    public float[] tv(float[] x) {
        float[] y = new float[nrows()];
        mv(Transpose.TRANSPOSE, 1.0f, x, 0.0f, y);
        return y;
    }

    @Override
    public void tv(float[] x, float[] y) {
        mv(Transpose.TRANSPOSE, 1.0f, x, 0.0f, y);
    }
}
