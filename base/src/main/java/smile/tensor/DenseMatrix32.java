/*
 * Copyright (c) 2010-2025 Haifeng Li. All rights reserved.
 *
 * Smile is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Smile is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Smile. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.tensor;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serial;
import java.lang.foreign.MemorySegment;
import java.util.Arrays;
import smile.linalg.Diag;
import smile.linalg.UPLO;
import smile.math.MathEx;
import static smile.tensor.ScalarType.*;

/**
 * A dense matrix of single precision floating numbers.
 *
 * @author Haifeng Li
 */
class DenseMatrix32 extends DenseMatrix {
    /**
     * The on-heap data.
     */
    final float[] data;

    /**
     * Default constructor for readObject.
     */
    private DenseMatrix32() {
        this.data = null;
    }

    /**
     * Constructor.
     * @param data the data array.
     * @param m the number of rows.
     * @param n the number of columns.
     * @param ld the leading dimension.
     * @param uplo if not null, the matrix is symmetric or triangular.
     * @param diag if not null, this flag specifies if a triangular
     *             matrix has unit diagonal elements.
     */
    public DenseMatrix32(float[] data, int m, int n, int ld, UPLO uplo, Diag diag) {
        super(MemorySegment.ofArray(data), m, n, ld, uplo, diag);
        this.data = data;
    }

    @Serial
    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        memory = MemorySegment.ofArray(data);
    }

    @Override
    public boolean equals(Object other) {
        float tol = 10 * MathEx.FLOAT_EPSILON;
        if (other instanceof DenseMatrix32 b) {
            if (nrow() == b.nrow() && ncol() == b.ncol()) {
                for (int j = 0; j < n; j++) {
                    for (int i = 0; i < m; i++) {
                        if (Math.abs(get(i, j) - b.get(i, j)) > tol) {
                            return false;
                        }
                    }
                }

                return true;
            }
        }
        return false;
    }

    @Override
    public ScalarType scalarType() {
        return Float32;
    }

    @Override
    public double get(int i, int j) {
        return data[offset(i, j)];
    }

    @Override
    public void set(int i, int j, double x) {
        data[offset(i, j)] = (float) x;
    }

    @Override
    public void add(int i, int j, double x) {
        data[offset(i, j)] += (float) x;
    }

    @Override
    public void sub(int i, int j, double x) {
        data[offset(i, j)] -= (float) x;
    }

    @Override
    public void mul(int i, int j, double x) {
        data[offset(i, j)] *= (float) x;
    }

    @Override
    public void div(int i, int j, double x) {
        data[offset(i, j)] /= (float) x;
    }

    @Override
    public void fill(double value) {
        Arrays.fill(data, (float) value);
    }

    @Override
    public DenseMatrix copy() {
        return new DenseMatrix32(data.clone(), m, n, ld, uplo, diag);
    }

    @Override
    public Vector column(int j) {
        return Vector32.column(data, offset(0, j), m);
    }

    @Override
    public DenseMatrix submatrix(int i, int j, int k, int l) {
        if (i < 0 || i > m || k <= i || k > m || j < 0 || j > n || l <= j || l > n) {
            throw new IllegalArgumentException(String.format("Invalid submatrix range (%d:%d, %d:%d) of %d x %d", i, k, j, l, m, n));
        }

        if (i == 0 && j == 0) {
            return new DenseMatrix32(data, k, l, ld, uplo, diag);
        } else {
            int nrow = k - i;
            int ncol = l - j;
            DenseMatrix sub = zeros(nrow, ncol);
            for (int q = 0; q < ncol; q++) {
                for (int p = 0; p < nrow; p++) {
                    sub.set(p, q, get(p + i, q + j));
                }
            }
            return sub;
        }
    }
}
