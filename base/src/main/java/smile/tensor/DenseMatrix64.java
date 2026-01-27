/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * SMILE is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with SMILE. If not, see <https://www.gnu.org/licenses/>.
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
 * A dense matrix of double precision floating numbers.
 *
 * @author Haifeng Li
 */
class DenseMatrix64 extends DenseMatrix {
    /**
     * The on-heap data.
     */
    final double[] data;

    /**
     * Default constructor for readObject.
     */
    private DenseMatrix64() {
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
    public DenseMatrix64(double[] data, int m, int n, int ld, UPLO uplo, Diag diag) {
        super(MemorySegment.ofArray(data), m, n, ld, uplo, diag);
        this.data = data;
    }

    @Serial
    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        memory = MemorySegment.ofArray(data);
    }

    @Override
    public ScalarType scalarType() {
        return Float64;
    }

    @Override
    public double get(int i, int j) {
        return data[offset(i, j)];
    }

    @Override
    public void set(int i, int j, double x) {
        data[offset(i, j)] = x;
    }

    @Override
    public void add(int i, int j, double x) {
        data[offset(i, j)] += x;
    }

    @Override
    public void sub(int i, int j, double x) {
        data[offset(i, j)] -= x;
    }

    @Override
    public void mul(int i, int j, double x) {
        data[offset(i, j)] *= x;
    }

    @Override
    public void div(int i, int j, double x) {
        data[offset(i, j)] /= x;
    }

    @Override
    public void fill(double value) {
        Arrays.fill(data, value);
    }

    @Override
    public DenseMatrix copy() {
        return new DenseMatrix64(data.clone(), m, n, ld, uplo, diag);
    }

    @Override
    public Vector column(int j) {
        return Vector64.column(data, offset(0, j), m);
    }

    @Override
    public DenseMatrix submatrix(int i, int j, int k, int l) {
        if (i < 0 || i > m || k <= i || k > m || j < 0 || j > n || l <= j || l > n) {
            throw new IllegalArgumentException(String.format("Invalid submatrix range (%d:%d, %d:%d) of %d x %d", i, k, j, l, m, n));
        }

        if (i == 0 && j == 0) {
            return new DenseMatrix64(data, k, l, ld, uplo, diag);
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
