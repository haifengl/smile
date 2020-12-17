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

package smile.math.matrix;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.IOException;
import java.io.Serializable;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.Arrays;

import smile.math.MathEx;
import smile.math.blas.*;
import smile.sort.QuickSort;
import smile.stat.distribution.Distribution;
import smile.stat.distribution.GaussianDistribution;

import static smile.math.blas.Diag.*;
import static smile.math.blas.Layout.*;
import static smile.math.blas.Side.*;
import static smile.math.blas.Transpose.*;
import static smile.math.blas.UPLO.*;

/**
 * Dense matrix of single precision values.
 *
 * @author Haifeng Li
 */
public class FloatMatrix extends SMatrix {
    private static final long serialVersionUID = 2L;
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(FloatMatrix.class);

    /**
     * The matrix storage.
     */
    transient FloatBuffer A;
    /**
     * The leading dimension.
     */
    transient int ld;
    /**
     * The number of rows.
     */
    int m;
    /**
     * The number of columns.
     */
    int n;
    /**
     * If not null, the matrix is symmetric or triangular.
     */
    UPLO uplo;
    /**
     * If not null, the matrix is triangular. The flag specifies if a
     * triangular matrix has unit diagonal elements.
     */
    Diag diag;

    /**
     * Constructor of zero matrix.
     * @param m the number of rows.
     * @param n the number of columns.
     */
    public FloatMatrix(int m, int n) {
        this(m, n, 0.0f);
    }

    /**
     * Constructor. Fills the matrix with given value.
     * @param m the number of rows.
     * @param n the number of columns.
     * @param a the initial value.
     */
    public FloatMatrix(int m, int n, float a) {
        if (m <= 0 || n <= 0) {
            throw new IllegalArgumentException(String.format("Invalid matrix size: %d x %d", m, n));
        }

        this.m = m;
        this.n = n;
        this.ld = ld(m);

        float[] array = new float[ld * n];
        if (a != 0.0) Arrays.fill(array, a);
        A = FloatBuffer.wrap(array);
    }

    /**
     * Constructor.
     * @param m the number of rows.
     * @param n the number of columns.
     * @param A the array of matrix.
     */
    public FloatMatrix(int m, int n, float[][] A) {
        this(m, n);

        for (int i = 0; i < m; i++) {
            for (int j = 0; j < n; j++) {
                set(i, j, A[i][j]);
            }
        }
    }

    /**
     * Constructor.
     * @param A the array of matrix.
     */
    public FloatMatrix(float[][] A) {
        this(A.length, A[0].length, A);
    }

    /**
     * Constructor of a column vector/matrix with given array as the internal storage.
     * @param A The array of column vector.
     */
    public FloatMatrix(float[] A) {
        this(A, 0, A.length);
    }

    /**
     * Constructor of a column vector/matrix with given array as the internal storage.
     * @param A The array of column vector.
     * @param offset The offset of the subarray to be used; must be non-negative and
     *               no larger than array.length.
     * @param length The length of the subarray to be used; must be non-negative and
     *               no larger than array.length - offset.
     */
    public FloatMatrix(float[] A, int offset, int length) {
        this.m = length;
        this.n = 1;
        this.ld = length;
        this.A = FloatBuffer.wrap(A, offset, length);
    }

    /**
     * Constructor.
     * @param m the number of rows.
     * @param n the number of columns.
     * @param ld the leading dimension.
     * @param A the matrix storage.
     */
    public FloatMatrix(int m, int n, int ld, FloatBuffer A) {
        if (layout() == COL_MAJOR && ld < m) {
            throw new IllegalArgumentException(String.format("Invalid leading dimension for COL_MAJOR: %d < %d", ld, m));
        }

        if (layout() == ROW_MAJOR && ld < n) {
            throw new IllegalArgumentException(String.format("Invalid leading dimension for ROW_MAJOR: %d < %d", ld, n));
        }

        this.m = m;
        this.n = n;
        this.ld = ld;
        this.A = A;
    }

    /**
     * Creates a matrix.
     * @param layout the matrix layout.
     * @param m the number of rows.
     * @param n the number of columns.
     * @return the matrix.
     */
    public static FloatMatrix of(Layout layout, int m, int n) {
        if (layout == COL_MAJOR) {
            int ld = ld(m);
            int size = ld * n;
            return of(layout, m, n, ld, FloatBuffer.allocate(size));
        } else {
            int ld = ld(n);
            int size = ld * m;
            return of(layout, m, n, ld, FloatBuffer.allocate(size));
        }
    }

    /**
     * Creates a matrix.
     * @param layout the matrix layout.
     * @param m the number of rows.
     * @param n the number of columns.
     * @param ld the leading dimension.
     * @param A the matrix storage.
     * @return the matrix.
     */
    public static FloatMatrix of(Layout layout, int m, int n, int ld, FloatBuffer A) {
        if (layout == COL_MAJOR && ld < m) {
            throw new IllegalArgumentException(String.format("Invalid leading dimension for COL_MAJOR: %d < %d", ld, m));
        }

        if (layout == ROW_MAJOR && ld < n) {
            throw new IllegalArgumentException(String.format("Invalid leading dimension for ROW_MAJOR: %d < %d", ld, n));
        }

        if (layout == COL_MAJOR) {
            return new FloatMatrix(m, n, ld, A);
        } else {
            return new FloatMatrix(m, n, ld, A) {
                @Override
                public Layout layout() {
                    return ROW_MAJOR;
                }

                @Override
                protected int index(int i , int j) {
                    return i * ld + j + A.position();
                }

                @Override
                public FloatMatrix transpose() {
                    return new FloatMatrix(n, m, ld, A);
                }
            };
        }
    }

    /**
     * Returns a random matrix of standard normal distribution.
     * @param m the number of rows.
     * @param n the number of columns.
     * @return the matrix.
     */
    public static FloatMatrix randn(int m, int n) {
        return rand(m, n, GaussianDistribution.getInstance());
    }

    /**
     * Returns a random matrix.
     *
     * @param m the number of rows.
     * @param n the number of columns.
     * @param distribution the distribution of random number.
     * @return the matrix.
     */
    public static FloatMatrix rand(int m, int n, Distribution distribution) {
        FloatMatrix matrix = new FloatMatrix(m, n);

        for (int j = 0; j < n; j++) {
            for (int i = 0; i < m; i++) {
                matrix.set(i, j, (float) distribution.rand());
            }
        }

        return matrix;
    }

    /**
     * Returns a random matrix of uniform distribution.
     *
     * @param m the number of rows.
     * @param n the number of columns.
     * @param lo the lower bound of uniform distribution.
     * @param hi the upper bound of uniform distribution.
     * @return the matrix.
     */
    public static FloatMatrix rand(int m, int n, float lo, float hi) {
        FloatMatrix matrix = new FloatMatrix(m, n);

        for (int j = 0; j < n; j++) {
            for (int i = 0; i < m; i++) {
                matrix.set(i, j, (float) MathEx.random(lo, hi));
            }
        }

        return matrix;
    }

    /**
     * Returns an identity matrix.
     * @param n the number of rows/columns.
     * @return the matrix.
     */
    public static FloatMatrix eye(int n) {
        return diag(n, 1.0f);
    }

    /**
     * Returns an m-by-n identity matrix.
     * @param m the number of rows.
     * @param n the number of columns.
     * @return the matrix.
     */
    public static FloatMatrix eye(int m, int n) {
        return diag(m, n, 1.0f);
    }

    /**
     * Returns a square diagonal matrix.
     *
     * @param n the number of rows/columns.
     * @param diag the diagonal value.
     * @return the matrix.
     */
    public static FloatMatrix diag(int n, float diag) {
        return diag(n, n, diag);
    }

    /**
     * Returns an m-by-n diagonal matrix.
     *
     * @param m the number of rows.
     * @param n the number of columns.
     * @param diag the diagonal value.
     * @return the matrix.
     */
    public static FloatMatrix diag(int m, int n, float diag) {
        FloatMatrix D = new FloatMatrix(m, n);
        int k = Math.min(m, n);
        for (int i = 0; i < k; i++) {
            D.set(i, i, diag);
        }
        return D;
    }

    /**
     * Returns a square diagonal matrix.
     *
     * @param diag the diagonal elements.
     * @return the matrix.
     */
    public static FloatMatrix diag(float[] diag) {
        int n = diag.length;
        FloatMatrix D = new FloatMatrix(n, n);
        for (int i = 0; i < n; i++) {
            D.set(i, i, diag[i]);
        }
        return D;
    }

    /**
     * Returns a symmetric Toeplitz matrix in which each descending diagonal
     * from left to right is constant.
     *
     * @param a A[i, j] = a[i - j] for {@code i >= j} (or a[j - i] when {@code j > i})
     * @return the matrix.
     */
    public static FloatMatrix toeplitz(float[] a) {
        int n = a.length;
        FloatMatrix toeplitz = new FloatMatrix(n, n);
        toeplitz.uplo(LOWER);

        for (int i = 0; i < n; i++) {
            for (int j = 0; j < i; j++) {
                toeplitz.set(i, j, a[i - j]);
            }

            for (int j = i; j < n; j++) {
                toeplitz.set(i, j, a[j - i]);
            }
        }

        return toeplitz;
    }

    /**
     * Returns a Toeplitz matrix in which each descending diagonal
     * from left to right is constant.
     *
     * @param kl {@code A[i, j] = kl[i - j]} for {@code i >  j}
     * @param ku {@code A[i, j] = ku[j - i]} for {@code i <= j}
     * @return the matrix.
     */
    public static FloatMatrix toeplitz(float[] kl, float[] ku) {
        if (kl.length != ku.length - 1) {
            throw new IllegalArgumentException(String.format("Invalid subdiagonals and superdiagonals size: %d != %d - 1", kl.length, ku.length));
        }

        int n = kl.length;
        FloatMatrix toeplitz = new FloatMatrix(n, n);

        for (int i = 0; i < n; i++) {
            for (int j = 0; j < i; j++) {
                toeplitz.set(i, j, kl[i - j]);
            }

            for (int j = i; j < n; j++) {
                toeplitz.set(i, j, ku[j - i]);
            }
        }

        return toeplitz;
    }

    /**
     * Returns the optimal leading dimension. The present process have
     * cascade caches. And read/write cache are 64 byte (multiple of 16
     * for single precision) related on Intel CPUs. In order to avoid
     * cache conflict, we expected the leading dimensions should be
     * multiple of cache line (multiple of 16 for single precision),
     * but not the power of 2, like not multiple of 256, not multiple
     * of 128 etc.
     * <p>
     * To improve performance, ensure that the leading dimensions of
     * the arrays are divisible by 64/element_size, where element_size
     * is the number of bytes for the matrix elements (4 for
     * single-precision real, 8 for double-precision real and
     * single precision complex, and 16 for double-precision complex).
     * <p>
     * But as present processor use cache-cascading structure: set->cache
     * line. In order to avoid the cache stall issue, we suggest to avoid
     * leading dimension are multiples of 128, If ld % 128 = 0, then add
     * 16 to the leading dimension.
     * <p>
     * Generally, set the leading dimension to the following integer expression:
     * (((n * element_size + 511) / 512) * 512 + 64) /element_size,
     * where n is the matrix dimension along the leading dimension.
     */
    private static int ld(int n) {
        int elementSize = 4;
        if (n <= 256 / elementSize) return n;

        return (((n * elementSize + 511) / 512) * 512 + 64) / elementSize;
    }

    /**
     * Customized object serialization.
     * @param out the output stream.
     * @throws IOException when fails to write to the stream.
     */
    private void writeObject(ObjectOutputStream out) throws IOException {
        // write default properties
        out.defaultWriteObject();

        // write buffer
        if (layout() == COL_MAJOR) {
            for (int j = 0; j < n; j++) {
                for (int i = 0; i < m; i++) {
                    out.writeFloat(get(i, j));
                }
            }
        } else {
            for (int i = 0; i < m; i++) {
                for (int j = 0; j < n; j++) {
                    out.writeFloat(get(i, j));
                }
            }
        }
    }

    /**
     * Customized object serialization.
     * @param in the input stream.
     * @throws IOException when fails to read the stream.
     * @throws ClassNotFoundException when fails to load the class.
     */
    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        //read default properties
        in.defaultReadObject();

        // read buffer data
        this.A = FloatBuffer.wrap(new float[m * n]);

        if (layout() == COL_MAJOR) {
            this.ld = m;
            for (int j = 0; j < n; j++) {
                for (int i = 0; i < m; i++) {
                    set(i, j, in.readFloat());
                }
            }
        } else {
            this.ld = n;
            for (int i = 0; i < m; i++) {
                for (int j = 0; j < n; j++) {
                    set(i, j, in.readFloat());
                }
            }
        }
    }

    @Override
    public int nrow() {
        return m;
    }

    @Override
    public int ncol() {
        return n;
    }

    @Override
    public long size() {
        return m * n;
    }

    /**
     * Returns the matrix layout.
     * @return the matrix layout.
     */
    public Layout layout() {
        return COL_MAJOR;
    }

    /**
     * Returns the leading dimension.
     * @return the leading dimension.
     */
    public int ld() {
        return ld;
    }

    /**
     * Returns true if the matrix is a submatrix (sharing the storage with larger matrix).
     * @return true if the matrix is a submatrix.
     */
    public boolean isSubmatrix() {
        return A.position() != 0 || A.limit() != A.capacity();
    }

    /**
     * Return true if the matrix is symmetric ({@code uplo != null && diag == null}).
     * @return true if the matrix is symmetric.
     */
    public boolean isSymmetric() {
        return uplo != null && diag == null;
    }

    /**
     * Sets the format of packed matrix.
     * @param uplo the format of packed matrix..
     * @return this matrix.
     */
    public FloatMatrix uplo(UPLO uplo) {
        if (m != n) {
            throw new IllegalArgumentException(String.format("The matrix is not square: %d x %d", m, n));
        }

        this.uplo = uplo;
        return this;
    }

    /**
     * Gets the format of packed matrix.
     * @return the format of packed matrix.
     */
    public UPLO uplo() {
        return uplo;
    }

    /**
     * Sets/unsets if the matrix is triangular.
     * @param diag if not null, it specifies if the triangular matrix has unit diagonal elements.
     * @return this matrix.
     */
    public FloatMatrix triangular(Diag diag) {
        if (m != n) {
            throw new IllegalArgumentException(String.format("The matrix is not square: %d x %d", m, n));
        }

        this.diag = diag;
        return this;
    }

    /**
     * Gets the flag if a triangular matrix has unit diagonal elements.
     * Returns null if the matrix is not triangular.
     * @return the flag if a triangular matrix has unit diagonal elements.
     */
    public Diag triangular() {
        return diag;
    }

    /** Returns a deep copy of matrix. */
    @Override
    public FloatMatrix clone() {
        FloatMatrix matrix = new FloatMatrix(m, n);
        for (int j = 0; j < n; j++) {
            for (int i = 0; i < m; i++) {
                matrix.set(i, j, get(i, j));
            }
        }

        if (m == n) {
            matrix.uplo(uplo);
            matrix.triangular(diag);
        }

        return matrix;
    }

    /**
     * Return the two-dimensional array of matrix.
     * @return the two-dimensional array of matrix.
     */
    public float[][] toArray() {
        float[][] array = new float[m][n];
        for (int i = 0; i < m; i++) {
            for (int j = 0; j < n; j++) {
                array[i][j] = get(i, j);
            }
        }
        return array;
    }

    /**
     * Returns the i-th row.
     * @param i the row index.
     * @return the row.
     */
    public double[] row(int i) {
        double[] x = new double[n];

        for (int j = 0; j < n; j++) {
            x[j] = get(i, j);
        }

        return x;
    }

    /**
     * Returns the j-th column.
     * @param j the column index.
     * @return the column.
     */
    public double[] col(int j) {
        double[] x = new double[m];

        for (int i = 0; i < m; i++) {
            x[i] = get(i, j);
        }

        return x;
    }

    /**
     * Returns the matrix of selected rows.
     * @param rows the row indices.
     * @return the submatrix.
     */
    public FloatMatrix row(int... rows) {
        FloatMatrix x = new FloatMatrix(rows.length, n);

        for (int i = 0; i < rows.length; i++) {
            int row = rows[i];
            for (int j = 0; j < n; j++) {
                x.set(i, j, get(row, j));
            }
        }

        return x;
    }

    /**
     * Returns the matrix of selected columns.
     * @param cols the column indices.
     * @return the submatrix.
     */
    public FloatMatrix col(int... cols) {
        FloatMatrix x = new FloatMatrix(m, cols.length);

        for (int j = 0; j < cols.length; j++) {
            int col = cols[j];
            for (int i = 0; i < m; i++) {
                x.set(i, j, get(i, col));
            }
        }

        return x;
    }

    /**
     * Returns the submatrix which top left at (i, j) and bottom right at (k, l).
     * The content of the submatrix will be that of this matrix. Changes to this
     * matrix's content will be visible in the submatrix, and vice versa.
     *
     * @param i the beginning row, inclusive.
     * @param j the beginning column, inclusive,
     * @param k the ending row, inclusive.
     * @param l the ending column, inclusive.
     * @return the submatrix.
     */
    public FloatMatrix submatrix(int i, int j, int k, int l) {
        if (i < 0 || i >= m || k < i || k >= m || j < 0 || j >= n || l < j || l >= n) {
            throw new IllegalArgumentException(String.format("Invalid submatrix range (%d:%d, %d:%d) of %d x %d", i, k, j, l, m, n));
        }

        int offset = index(i, j);
        int length = index(k, l) - offset + 1;
        FloatBuffer B = FloatBuffer.wrap(A.array(), offset, length);

        return of(layout(),k - i + 1, l - j + 1, ld, B);
    }

    /**
     * Fill the matrix with a value.
     * @param x the value.
     */
    public void fill(float x) {
        if (isSubmatrix()) {
            for (int j = 0; j < n; j++) {
                for (int i = 0; i < m; i++) {
                    set(i, j, x);
                }
            }
        } else {
            Arrays.fill(A.array(), x);
        }
    }

    /**
     * Returns the transpose of matrix.
     * @return the transpose of matrix.
     */
    public FloatMatrix transpose() {
        return of(ROW_MAJOR, n, m, ld, A);
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof FloatMatrix)) {
            return false;
        }

        return equals((FloatMatrix) o, 1E-7f);
    }

    /**
     * Returns true if two matrices equal in given precision.
     *
     * @param o the other matrix.
     * @param epsilon a number close to zero.
     * @return true if two matrices equal in given precision.
     */
    public boolean equals(FloatMatrix o, float epsilon) {
        if (m != o.m || n != o.n) {
            return false;
        }

        for (int j = 0; j < n; j++) {
            for (int i = 0; i < m; i++) {
                if (!MathEx.isZero(get(i, j) - o.get(i, j), epsilon)) {
                    return false;
                }
            }
        }

        return true;
    }

    /**
     * Returns the linearized index of matrix element.
     * @param i the row index.
     * @param j the column index.
     * @return the linearized index.
     */
    protected int index(int i , int j) {
        return j * ld + i + A.position();
    }

    @Override
    public float get(int i, int j) {
        return A.get(index(i, j));
    }

    @Override
    public FloatMatrix set(int i, int j, float x) {
        A.put(index(i, j), x);
        return this;
    }

    /**
     * Sets submatrix A[i,j] = B.
     * @param i the row index of left top corner of submatrix.
     * @param j the column index of left top corner of submatrix.
     * @param B the right-hand-side submatrix.
     * @return this matrix.
     */
    public FloatMatrix set(int i, int j, FloatMatrix B) {
        for (int jj = 0; jj < B.n; jj++) {
            for (int ii = 0; ii < B.m; ii++) {
                set(i+ii, j+jj, B.get(ii, jj));
            }
        }
        return this;
    }

    /**
     * A[i,j] += b
     * @param i the row index.
     * @param j the column index.
     * @param b the operand.
     * @return the updated cell value.
     */
    public float add(int i, int j, float b) {
        int k = index(i, j);
        float y = A.get(k) + b;
        A.put(k, y);
        return y;
    }

    /**
     * A[i,j] -= b
     * @param i the row index.
     * @param j the column index.
     * @param b the operand.
     * @return the updated cell value.
     */
    public float sub(int i, int j, float b) {
        int k = index(i, j);
        float y = A.get(k) - b;
        A.put(k, y);
        return y;
    }

    /**
     * A[i,j] *= b
     * @param i the row index.
     * @param j the column index.
     * @param b the operand.
     * @return the updated cell value.
     */
    public float mul(int i, int j, float b) {
        int k = index(i, j);
        float y = A.get(k) * b;
        A.put(k, y);
        return y;
    }

    /**
     * A[i,j] /= b
     * @param i the row index.
     * @param j the column index.
     * @param b the operand.
     * @return the updated cell value.
     */
    public float div(int i, int j, float b) {
        int k = index(i, j);
        float y = A.get(k) / b;
        A.put(k, y);
        return y;
    }

    /**
     * A += b
     * @param b the operand.
     * @return this matrix.
     */
    public FloatMatrix add(float b) {
        for (int j = 0; j < n; j++) {
            for (int i = 0; i < m; i++) {
                add(i, j, b);
            }
        }

        return this;
    }

    /**
     * A -= b
     * @param b the operand.
     * @return this matrix.
     */
    public FloatMatrix sub(float b) {
        for (int j = 0; j < n; j++) {
            for (int i = 0; i < m; i++) {
                sub(i, j, b);
            }
        }

        return this;
    }

    /**
     * A *= b
     * @param b the operand.
     * @return this matrix.
     */
    public FloatMatrix mul(float b) {
        for (int j = 0; j < n; j++) {
            for (int i = 0; i < m; i++) {
                mul(i, j, b);
            }
        }

        return this;
    }

    /**
     * A /= b
     * @param b the operand.
     * @return this matrix.
     */
    public FloatMatrix div(float b) {
        for (int j = 0; j < n; j++) {
            for (int i = 0; i < m; i++) {
                div(i, j, b);
            }
        }

        return this;
    }

    /**
     * Element-wise submatrix addition A[i, j] += alpha * B
     * @param i the row index of left top corner of submatrix.
     * @param j the column index of left top corner of submatrix.
     * @param alpha the scalar alpha.
     * @param B the submatrix operand.
     * @return this matrix.
     */
    public FloatMatrix add(int i, int j, float alpha, FloatMatrix B) {
        for (int jj = 0; jj < B.n; jj++) {
            for (int ii = 0; ii < B.m; ii++) {
                add(i+ii, j+jj, alpha * B.get(ii, jj));
            }
        }
        return this;
    }

    /**
     * Element-wise submatrix subtraction A[i, j] -= alpha * B
     * @param i the row index of left top corner of submatrix.
     * @param j the column index of left top corner of submatrix.
     * @param alpha the scalar alpha.
     * @param B the submatrix operand.
     * @return this matrix.
     */
    public FloatMatrix sub(int i, int j, float alpha, FloatMatrix B) {
        for (int jj = 0; jj < B.n; jj++) {
            for (int ii = 0; ii < B.m; ii++) {
                sub(i+ii, j+jj, alpha * B.get(ii, jj));
            }
        }
        return this;
    }

    /**
     * Element-wise submatrix multiplication A[i, j] *= alpha * B
     * @param i the row index of left top corner of submatrix.
     * @param j the column index of left top corner of submatrix.
     * @param alpha the scalar alpha.
     * @param B the submatrix operand.
     * @return this matrix.
     */
    public FloatMatrix mul(int i, int j, float alpha, FloatMatrix B) {
        for (int jj = 0; jj < B.n; jj++) {
            for (int ii = 0; ii < B.m; ii++) {
                mul(i+ii, j+jj, alpha * B.get(ii, jj));
            }
        }
        return this;
    }

    /**
     * Element-wise submatrix division A[i, j] /= alpha * B
     * @param i the row index of left top corner of submatrix.
     * @param j the column index of left top corner of submatrix.
     * @param alpha the scalar alpha.
     * @param B the submatrix operand.
     * @return this matrix.
     */
    public FloatMatrix div(int i, int j, float alpha, FloatMatrix B) {
        for (int jj = 0; jj < B.n; jj++) {
            for (int ii = 0; ii < B.m; ii++) {
                div(i+ii, j+jj, alpha * B.get(ii, jj));
            }
        }
        return this;
    }

    /**
     * Element-wise addition A += B
     * @param B the operand.
     * @return this matrix.
     */
    public FloatMatrix add(FloatMatrix B) {
        return add(1.0f, B);
    }

    /**
     * Element-wise subtraction A -= B
     * @param B the operand.
     * @return this matrix.
     */
    public FloatMatrix sub(FloatMatrix B) {
        return sub(1.0f, B);
    }

    /**
     * Element-wise multiplication A *= B
     * @param B the operand.
     * @return this matrix.
     */
    public FloatMatrix mul(FloatMatrix B) {
        return mul(1.0f, B);
    }

    /**
     * Element-wise division A /= B
     * @param B the operand.
     * @return this matrix.
     */
    public FloatMatrix div(FloatMatrix B) {
        return div(1.0f, B);
    }

    /**
     * Element-wise addition A += alpha * B
     * @param alpha the scalar alpha.
     * @param B the operand.
     * @return this matrix.
     */
    public FloatMatrix add(float alpha, FloatMatrix B) {
        if (m != B.m || n != B.n) {
            throw new IllegalArgumentException("Matrix is not of same size.");
        }

        for (int j = 0; j < n; j++) {
            for (int i = 0; i < m; i++) {
                add(i, j, alpha * B.get(i, j));
            }
        }
        return this;
    }

    /**
     * Element-wise subtraction A -= alpha * B
     * @param alpha the scalar alpha.
     * @param B the operand.
     * @return this matrix.
     */
    public FloatMatrix sub(float alpha, FloatMatrix B) {
        if (m != B.m || n != B.n) {
            throw new IllegalArgumentException("Matrix is not of same size.");
        }

        for (int j = 0; j < n; j++) {
            for (int i = 0; i < m; i++) {
                sub(i, j, alpha * B.get(i, j));
            }
        }
        return this;
    }

    /**
     * Element-wise multiplication A *= alpha * B
     * @param alpha the scalar alpha.
     * @param B the operand.
     * @return this matrix.
     */
    public FloatMatrix mul(float alpha, FloatMatrix B) {
        if (m != B.m || n != B.n) {
            throw new IllegalArgumentException("Matrix is not of same size.");
        }

        for (int j = 0; j < n; j++) {
            for (int i = 0; i < m; i++) {
                mul(i, j, alpha * B.get(i, j));
            }
        }
        return this;
    }

    /**
     * Element-wise division A /= alpha * B
     * @param alpha the scalar alpha.
     * @param B the operand.
     * @return this matrix.
     */
    public FloatMatrix div(float alpha, FloatMatrix B) {
        if (m != B.m || n != B.n) {
            throw new IllegalArgumentException("Matrix is not of same size.");
        }

        for (int j = 0; j < n; j++) {
            for (int i = 0; i < m; i++) {
                div(i, j, alpha * B.get(i, j));
            }
        }
        return this;
    }

    /**
     * Element-wise addition C = alpha * A + beta * B
     * @param alpha the scalar alpha.
     * @param A the operand.
     * @param beta the scalar beta.
     * @param B the operand.
     * @return this matrix.
     */
    public FloatMatrix add(float alpha, FloatMatrix A, float beta, FloatMatrix B) {
        if (m != A.m || n != A.n) {
            throw new IllegalArgumentException("Matrix A is not of same size.");
        }

        if (m != B.m || n != B.n) {
            throw new IllegalArgumentException("Matrix B is not of same size.");
        }

        for (int j = 0; j < n; j++) {
            for (int i = 0; i < m; i++) {
                set(i, j, alpha * A.get(i, j) + beta * B.get(i, j));
            }
        }
        return this;
    }

    /**
     * Element-wise subtraction C = alpha * A - beta * B
     * @param alpha the scalar alpha.
     * @param A the operand.
     * @param beta the scalar beta.
     * @param B the operand.
     * @return this matrix.
     */
    public FloatMatrix sub(float alpha, FloatMatrix A, float beta, FloatMatrix B) {
        return add(alpha, A, -beta, B);
    }

    /**
     * Element-wise multiplication C = alpha * A * B
     * @param alpha the scalar alpha.
     * @param A the operand.
     * @param B the operand.
     * @return this matrix.
     */
    public FloatMatrix mul(float alpha, FloatMatrix A, FloatMatrix B) {
        if (m != A.m || n != A.n) {
            throw new IllegalArgumentException("Matrix A is not of same size.");
        }

        if (m != B.m || n != B.n) {
            throw new IllegalArgumentException("Matrix B is not of same size.");
        }

        for (int j = 0; j < n; j++) {
            for (int i = 0; i < m; i++) {
                set(i, j, alpha * A.get(i, j) * B.get(i, j));
            }
        }
        return this;
    }

    /**
     * Element-wise division C = alpha * A / B
     * @param alpha the scalar alpha.
     * @param A the operand.
     * @param B the operand.
     * @return this matrix.
     */
    public FloatMatrix div(float alpha, FloatMatrix A, FloatMatrix B) {
        if (m != A.m || n != A.n) {
            throw new IllegalArgumentException("Matrix A is not of same size.");
        }

        if (m != B.m || n != B.n) {
            throw new IllegalArgumentException("Matrix B is not of same size.");
        }

        for (int j = 0; j < n; j++) {
            for (int i = 0; i < m; i++) {
                set(i, j, alpha * A.get(i, j) / B.get(i, j));
            }
        }
        return this;
    }

    /**
     * A[i,j] = alpha * A[i,j] + beta
     * @param i the row index.
     * @param j the column index.
     * @param alpha the scalar alpha.
     * @param beta the operand.
     * @return the updated A[i,j]
     */
    public double add(int i, int j, float alpha, float beta) {
        int k = index(i, j);
        float y = alpha * A.get(k) + beta;
        A.put(k, y);
        return y;
    }

    /**
     * Element-wise submatrix addition A[i, j] = alpha * A[i, j] + beta * B
     * @param i the row index of left top corner of submatrix.
     * @param j the column index of left top corner of submatrix.
     * @param alpha the scalar alpha.
     * @param beta the scalar beta.
     * @param B the submatrix operand.
     * @return this matrix.
     */
    public FloatMatrix add(int i, int j, float alpha, float beta, FloatMatrix B) {
        for (int jj = 0; jj < B.n; jj++) {
            for (int ii = 0; ii < B.m; ii++) {
                add(i+ii, j+jj, alpha, beta * B.get(ii, jj));
            }
        }
        return this;
    }

    /**
     * Element-wise addition A = alpha * A + beta * B
     * @param alpha the scalar alpha.
     * @param beta the scalar beta.
     * @param B the operand.
     * @return this matrix.
     */
    public FloatMatrix add(float alpha, float beta, FloatMatrix B) {
        if (m != B.m || n != B.n) {
            throw new IllegalArgumentException("Matrix B is not of same size.");
        }

        for (int j = 0; j < n; j++) {
            for (int i = 0; i < m; i++) {
                set(i, j, alpha * get(i, j) + beta * B.get(i, j));
            }
        }

        return this;
    }

    /**
     * Rank-1 update A += alpha * x * y'
     * @param alpha the scalar alpha.
     * @param x the column vector.
     * @param y the row vector.
     * @return this matrix.
     */
    public FloatMatrix add(float alpha, float[] x, float[] y) {
        if (m != x.length || n != y.length) {
            throw new IllegalArgumentException("Matrix is not of same size.");
        }

        if (isSymmetric() && x == y) {
            BLAS.engine.syr(layout(), uplo, m, alpha, FloatBuffer.wrap(x), 1, A, ld);
        } else {
            BLAS.engine.ger(layout(), m, n, alpha, FloatBuffer.wrap(x), 1, FloatBuffer.wrap(y), 1, A, ld);
        }

        return this;
    }

    /**
     * Replaces NaN's with given value.
     * @param x a real number.
     * @return this matrix.
     */
    public FloatMatrix replaceNaN(float x) {
        for (int j = 0; j < n; j++) {
            for (int i = 0; i < m; i++) {
                if (Float.isNaN(get(i, j))) {
                    set(i, j, x);
                }
            }
        }

        return this;
    }

    /**
     * Returns the sum of all elements.
     * @return the sum of all elements.
     */
    public float sum() {
        float s = 0.0f;
        for (int j = 0; j < n; j++) {
            for (int i = 0; i < m; i++) {
                s += get(i, j);
            }
        }

        return s;
    }

    /**
     * L<sub>1</sub> matrix norm that is the maximum of column sums.
     * @return L<sub>1</sub> matrix norm.
     */
    public float norm1() {
        float f = 0.0f;
        for (int j = 0; j < n; j++) {
            float s = 0.0f;
            for (int i = 0; i < m; i++) {
                s += Math.abs(get(i, j));
            }
            f = Math.max(f, s);
        }

        return f;
    }

    /**
     * L<sub>2</sub> matrix norm that is the maximum singular value.
     * @return L<sub>2</sub> matrix norm.
     */
    public float norm2() {
        return svd(false, false).s[0];
    }

    /**
     * L<sub>2</sub> matrix norm that is the maximum singular value.
     * @return L<sub>2</sub> matrix norm.
     */
    public float norm() {
        return norm2();
    }

    /**
     * L<sub>&infin;</sub> matrix norm that is the maximum of row sums.
     * @return L<sub>&infin;</sub> matrix norm.
     */
    public float normInf() {
        float[] f = new float[m];
        for (int j = 0; j < n; j++) {
            for (int i = 0; i < m; i++) {
                f[i] += Math.abs(get(i, j));
            }
        }

        return MathEx.max(f);
    }

    /**
     * Frobenius matrix norm that is the square root of sum of squares of all elements.
     * @return Frobenius matrix norm.
     */
    public float normFro() {
        double f = 0.0;
        for (int j = 0; j < n; j++) {
            for (int i = 0; i < m; i++) {
                f = Math.hypot(f, get(i, j));
            }
        }

        return (float) f;
    }

    /**
     * Returns the quadratic form {@code x' * A * x}.
     * The left upper submatrix of A is used in the computation based
     * on the size of x.
     * @param x the vector.
     * @return the quadratic form.
     */
    public float xAx(float[] x) {
        if (m != n) {
            throw new IllegalArgumentException(String.format("The matrix is not square: %d x %d", m, n));
        }

        if (n != x.length) {
            throw new IllegalArgumentException(String.format("Matrix: %d x %d, Vector: %d", m, n, x.length));
        }

        int n = x.length;
        float s = 0.0f;
        for (int j = 0; j < n; j++) {
            for (int i = 0; i < n; i++) {
                s += get(i, j) * x[i] * x[j];
            }
        }

        return s;
    }

    /**
     * Returns the sum of each row.
     * @return the sum of each row.
     */
    public float[] rowSums() {
        float[] x = new float[m];

        for (int j = 0; j < n; j++) {
            for (int i = 0; i < m; i++) {
                x[i] += get(i, j);
            }
        }

        return x;
    }

    /**
     * Returns the mean of each row.
     * @return the mean of each row.
     */
    public float[] rowMeans() {
        float[] x = new float[m];

        for (int j = 0; j < n; j++) {
            for (int i = 0; i < m; i++) {
                x[i] += get(i, j);
            }
        }

        for (int i = 0; i < m; i++) {
            x[i] /= n;
        }

        return x;
    }

    /**
     * Returns the standard deviations of each row.
     * @return the standard deviations of each row.
     */
    public float[] rowSds() {
        float[] x = new float[m];
        float[] x2 = new float[m];

        for (int j = 0; j < n; j++) {
            for (int i = 0; i < m; i++) {
                float a = get(i, j);
                x[i] += a;
                x2[i] += a * a;
            }
        }

        for (int i = 0; i < m; i++) {
            float mu = x[i] / n;
            x[i] = (float) Math.sqrt(x2[i] / n - mu * mu);
        }

        return x;
    }

    /**
     * Returns the sum of each column.
     * @return the sum of each column.
     */
    public float[] colSums() {
        float[] x = new float[n];

        for (int j = 0; j < n; j++) {
            for (int i = 0; i < m; i++) {
                x[j] += get(i, j);
            }
        }

        return x;
    }

    /**
     * Returns the mean of each column.
     * @return the mean of each column.
    */
    public float[] colMeans() {
        float[] x = new float[n];

        for (int j = 0; j < n; j++) {
            for (int i = 0; i < m; i++) {
                x[j] += get(i, j);
            }
            x[j] /= m;
        }

        return x;
    }

    /**
     * Returns the standard deviations of each column.
     * @return the standard deviations of each column.
     */
    public float[] colSds() {
        float[] x = new float[n];

        for (int j = 0; j < n; j++) {
            float mu = 0.0f;
            float sumsq = 0.0f;
            for (int i = 0; i < m; i++) {
                float a = get(i, j);
                mu += a;
                sumsq += a * a;
            }
            mu /= m;
            x[j] = (float) Math.sqrt(sumsq / m - mu * mu);
        }

        return x;
    }

    /**
     * Centers and scales the columns of matrix.
     * @return a new matrix with zero mean and unit variance for each column.
     */
    public FloatMatrix scale() {
        float[] center = colMeans();
        float[] scale = colSds();
        return scale(center, scale);
    }

    /**
     * Centers and scales the columns of matrix.
     * @param center column center. If null, no centering.
     * @param scale column scale. If null, no scaling.
     * @return a new matrix with zero mean and unit variance for each column.
     */
    public FloatMatrix scale(float[] center, float[] scale) {
        if (center == null && scale == null) {
            throw new IllegalArgumentException("Both center and scale are null");
        }

        FloatMatrix matrix = new FloatMatrix(m, n);

        if (center == null) {
            for (int j = 0; j < n; j++) {
                for (int i = 0; i < m; i++) {
                    matrix.set(i, j, get(i, j) / scale[j]);
                }
            }
        } else if (scale == null) {
            for (int j = 0; j < n; j++) {
                for (int i = 0; i < m; i++) {
                    matrix.set(i, j, get(i, j) - center[j]);
                }
            }
        } else {
            for (int j = 0; j < n; j++) {
                for (int i = 0; i < m; i++) {
                    matrix.set(i, j, (get(i, j) - center[j]) / scale[j]);
                }
            }
        }

        return matrix;
    }

    /**
     * Returns the inverse of matrix.
     * @return the inverse of matrix.
     */
    public FloatMatrix inverse() {
        if (m != n) {
            throw new IllegalArgumentException(String.format("The matrix is not square: %d x %d", m, n));
        }

        FloatMatrix lu = clone();
        FloatMatrix inv = eye(n);
        int[] ipiv = new int[n];
        if (isSymmetric()) {
            int info = LAPACK.engine.sysv(lu.layout(), uplo,  n, n, lu.A, lu.ld, IntBuffer.wrap(ipiv), inv.A, inv.ld);
            if (info != 0) {
                throw new ArithmeticException("SYSV fails: " + info);
            }
        } else {
            int info = LAPACK.engine.gesv(lu.layout(), n, n, lu.A, lu.ld, IntBuffer.wrap(ipiv), inv.A, inv.ld);
            if (info != 0) {
                throw new ArithmeticException("GESV fails: " + info);
            }
        }

        return inv;
    }

    /**
     * Matrix-vector multiplication.
     * <pre>{@code
     *     y = alpha * A * x + beta * y
     * }</pre>
     * @param trans normal, transpose, or conjugate transpose
     *               operation on the matrix A.
     * @param alpha the scalar alpha.
     * @param x the operand.
     * @param beta the scalar beta.
     * @param y the operand.
     */
    public void mv(Transpose trans, float alpha, FloatBuffer x, float beta, FloatBuffer y) {
        if (uplo != null) {
            if (diag != null) {
                if (alpha == 1.0 && beta == 0.0 && x == y) {
                    BLAS.engine.trmv(layout(), uplo, trans, diag, m, A, ld, y, 1);
                } else {
                    BLAS.engine.gemv(layout(), trans, m, n, alpha, A, ld, x, 1, beta, y, 1);
                }
            } else {
                BLAS.engine.symv(layout(), uplo, m, alpha, A, ld, x, 1, beta, y, 1);
            }
        } else {
            BLAS.engine.gemv(layout(), trans, m, n, alpha, A, ld, x, 1, beta, y, 1);
        }
    }

    @Override
    public void mv(Transpose trans, float alpha, float[] x, float beta, float[] y) {
        mv(trans, alpha, FloatBuffer.wrap(x), beta, FloatBuffer.wrap(y));
    }

    @Override
    public void mv(float[] work, int inputOffset, int outputOffset) {
        FloatBuffer xb = FloatBuffer.wrap(work, inputOffset, n);
        FloatBuffer yb = FloatBuffer.wrap(work, outputOffset, m);
        mv(NO_TRANSPOSE, 1.0f, xb, 0.0f, yb);
    }

    @Override
    public void tv(float[] work, int inputOffset, int outputOffset) {
        FloatBuffer xb = FloatBuffer.wrap(work, inputOffset, m);
        FloatBuffer yb = FloatBuffer.wrap(work, outputOffset, n);
        mv(TRANSPOSE, 1.0f, xb, 0.0f, yb);
    }

    /** Flips the transpose operation. */
    private Transpose flip(Transpose trans) {
        return trans == NO_TRANSPOSE ? TRANSPOSE : NO_TRANSPOSE;
    }

    /**
     * Matrix-matrix multiplication.
     * <pre>{@code
     *     C := alpha*A*B + beta*C
     * }</pre>
     * @param transA normal, transpose, or conjugate transpose
     *               operation on the matrix A.
     * @param transB normal, transpose, or conjugate transpose
     *               operation on the matrix B.
     * @param alpha the scalar alpha.
     * @param B the operand.
     * @param beta the scalar beta.
     * @param C the operand.
     */
    public void mm(Transpose transA, Transpose transB, float alpha, FloatMatrix B, float beta, FloatMatrix C) {
        if (isSymmetric() && transB == NO_TRANSPOSE && B.layout() == C.layout()) {
            BLAS.engine.symm(C.layout(), LEFT, uplo, C.m, C.n, alpha, A, ld, B.A, B.ld, beta, C.A, C.ld);
        } else if (B.isSymmetric() && transA == NO_TRANSPOSE && layout() == C.layout()) {
            BLAS.engine.symm(C.layout(), RIGHT, B.uplo, C.m, C.n, alpha, B.A, B.ld, A, ld, beta, C.A, C.ld);
        } else {
            if (C.layout() != layout()) transA = flip(transA);
            if (C.layout() != B.layout()) transB = flip(transB);
            int k = transA == NO_TRANSPOSE ? n : m;

            BLAS.engine.gemm(layout(), transA, transB, C.m, C.n, k, alpha,  A, ld,  B.A, B.ld, beta, C.A, C.ld);
        }
    }

    /**
     * Returns {@code A' * A}.
     * @return {@code A' * A}.
     */
    public FloatMatrix ata() {
        FloatMatrix C = new FloatMatrix(n, n);
        mm(TRANSPOSE, NO_TRANSPOSE, 1.0f, this, 0.0f, C);
        C.uplo(LOWER);
        return C;
    }

    /**
     * Returns {@code A * A'}.
     * @return {@code A * A'}.
     */
    public FloatMatrix aat() {
        FloatMatrix C = new FloatMatrix(m, m);
        mm(NO_TRANSPOSE, TRANSPOSE, 1.0f, this, 0.0f, C);
        C.uplo(LOWER);
        return C;
    }

    /**
     * Returns {@code A * D * B}, where D is a diagonal matrix.
     * @param transA normal, transpose, or conjugate transpose
     *               operation on the matrix A.
     * @param transB normal, transpose, or conjugate transpose
     *               operation on the matrix B.
     * @param B the operand.
     * @param diag the diagonal matrix.
     * @return the multiplication.
     */
    public FloatMatrix adb(Transpose transA, Transpose transB, FloatMatrix B, float[] diag) {
        FloatMatrix C;
        if (transA == NO_TRANSPOSE) {
            C = new FloatMatrix(m, n);
            for (int j = 0; j < n; j++) {
                for (int i = 0; i < m; i++) {
                    C.set(i, j, diag[j] * get(i, j));
                }
            }
        } else {
            C = new FloatMatrix(n, m);
            for (int j = 0; j < m; j++) {
                for (int i = 0; i < n; i++) {
                    C.set(i, j, diag[j] * get(j, i));
                }
            }
        }

        return transB == NO_TRANSPOSE ? C.mm(B) : C.mt(B);
    }

    /**
     * Returns matrix multiplication {@code A * B}.
     * @param B the operand.
     * @return the multiplication.
     */
    public FloatMatrix mm(FloatMatrix B) {
        if (n != B.m) {
            throw new IllegalArgumentException(String.format("Matrix multiplication A * B: %d x %d vs %d x %d", m, n, B.m, B.n));
        }

        FloatMatrix C = new FloatMatrix(m, B.n);
        mm(NO_TRANSPOSE, NO_TRANSPOSE, 1.0f, B, 0.0f, C);
        return C;
    }

    /**
     * Returns matrix multiplication {@code A * B'}.
     * @param B the operand.
     * @return the multiplication.
     */
    public FloatMatrix mt(FloatMatrix B) {
        if (n != B.n) {
            throw new IllegalArgumentException(String.format("Matrix multiplication A * B': %d x %d vs %d x %d", m, n, B.m, B.n));
        }

        FloatMatrix C = new FloatMatrix(m, B.m);
        mm(NO_TRANSPOSE, TRANSPOSE, 1.0f, B, 0.0f, C);
        return C;
    }

    /**
     * Returns matrix multiplication {@code A' * B}.
     * @param B the operand.
     * @return the multiplication.
     */
    public FloatMatrix tm(FloatMatrix B) {
        if (m != B.m) {
            throw new IllegalArgumentException(String.format("Matrix multiplication A' * B: %d x %d vs %d x %d", m, n, B.m, B.n));
        }

        FloatMatrix C = new FloatMatrix(n, B.n);
        mm(TRANSPOSE, NO_TRANSPOSE, 1.0f, B, 0.0f, C);
        return C;
    }

    /**
     * Returns matrix multiplication {@code A' * B'}.
     * @param B the operand.
     * @return the multiplication.
     */
    public FloatMatrix tt(FloatMatrix B) {
        if (m != B.n) {
            throw new IllegalArgumentException(String.format("Matrix multiplication A' * B': %d x %d vs %d x %d", m, n, B.m, B.n));
        }

        FloatMatrix C = new FloatMatrix(n, B.m);
        mm(TRANSPOSE, TRANSPOSE, 1.0f, B, 0.0f, C);
        return C;
    }

    /**
     * LU decomposition.
     * @return LU decomposition.
     */
    public LU lu() {
        return lu(false);
    }

    /**
     * LU decomposition.
     *
     * @param overwrite The flag if the decomposition overwrites this matrix.
     * @return LU decomposition.
     */
    public LU lu(boolean overwrite) {
        FloatMatrix lu = overwrite ? this : clone();
        int[] ipiv = new int[Math.min(m, n)];
        int info = LAPACK.engine.getrf(lu.layout(), lu.m, lu.n, lu.A, lu.ld, IntBuffer.wrap(ipiv));
        if (info < 0) {
            logger.error("LAPACK GETRF error code: {}", info);
            throw new ArithmeticException("LAPACK GETRF error code: " + info);
        }

        return new LU(lu, ipiv, info);
    }

    /**
     * Cholesky decomposition for symmetric and positive definite matrix.
     *
     * @throws ArithmeticException if the matrix is not positive definite.
     * @return Cholesky decomposition.
     */
    public Cholesky cholesky() {
        return cholesky(false);
    }

    /**
     * Cholesky decomposition for symmetric and positive definite matrix.
     *
     * @param overwrite The flag if the decomposition overwrites this matrix.
     * @throws ArithmeticException if the matrix is not positive definite.
     * @return Cholesky decomposition.
     */
    public Cholesky cholesky(boolean overwrite) {
        if (uplo == null) {
            throw new IllegalArgumentException("The matrix is not symmetric");
        }

        FloatMatrix lu = overwrite ? this : clone();
        int info = LAPACK.engine.potrf(lu.layout(), lu.uplo, lu.n, lu.A, lu.ld);
        if (info != 0) {
            logger.error("LAPACK GETRF error code: {}", info);
            throw new ArithmeticException("LAPACK GETRF error code: " + info);
        }

        return new Cholesky(lu);
    }

    /**
     * QR Decomposition.
     * @return QR decomposition.
     */
    public QR qr() {
        return qr(false);
    }

    /**
     * QR Decomposition.
     *
     * @param overwrite The flag if the decomposition overwrites this matrix.
     * @return QR decomposition.
     */
    public QR qr(boolean overwrite) {
        FloatMatrix qr = overwrite ? this : clone();
        float[] tau = new float[Math.min(m, n)];
        int info = LAPACK.engine.geqrf(qr.layout(), qr.m, qr.n, qr.A, qr.ld, FloatBuffer.wrap(tau));
        if (info != 0) {
            logger.error("LAPACK GEQRF error code: {}", info);
            throw new ArithmeticException("LAPACK GEQRF error code: " + info);
        }

        return new QR(qr, tau);
    }

    /**
     * Singular Value Decomposition.
     * Returns an compact SVD of m-by-n matrix A:
     * <ul>
     * <li>{@code m > n} — Only the first n columns of U are computed, and S is n-by-n.</li>
     * <li>{@code m = n} — Equivalent to full SVD.</li>
     * <li>{@code m < n} — Only the first m columns of V are computed, and S is m-by-m.</li>
     * </ul>
     * The compact decomposition removes extra rows or columns of zeros from
     * the diagonal matrix of singular values, S, along with the columns in either
     * U or V that multiply those zeros in the expression A = U*S*V'. Removing these
     * zeros and columns can improve execution time and reduce storage requirements
     * without compromising the accuracy of the decomposition.
     * @return singular value decomposition.
     */
    public SVD svd() {
        return svd(true, false);
    }

    /**
     * Singular Value Decomposition.
     * Returns an compact SVD of m-by-n matrix A:
     * <ul>
     * <li>{@code m > n} — Only the first n columns of U are computed, and S is n-by-n.</li>
     * <li>{@code m = n} — Equivalent to full SVD.</li>
     * <li>{@code m < n} — Only the first m columns of V are computed, and S is m-by-m.</li>
     * </ul>
     * The compact decomposition removes extra rows or columns of zeros from
     * the diagonal matrix of singular values, S, along with the columns in either
     * U or V that multiply those zeros in the expression A = U*S*V'. Removing these
     * zeros and columns can improve execution time and reduce storage requirements
     * without compromising the accuracy of the decomposition.
     *
     * @param vectors The flag if computing the singular vectors.
     * @param overwrite The flag if the decomposition overwrites this matrix.
     * @return singular value decomposition.
     */
    public SVD svd(boolean vectors, boolean overwrite) {
        int k = Math.min(m, n);
        float[] s = new float[k];

        FloatMatrix W = overwrite ? this : clone();
        if (vectors) {
            FloatMatrix U = new FloatMatrix(m, k);
            FloatMatrix VT = new FloatMatrix(k, n);

            int info = LAPACK.engine.gesdd(W.layout(), SVDJob.COMPACT, W.m, W.n, W.A, W.ld, FloatBuffer.wrap(s), U.A, U.ld, VT.A, VT.ld);
            if (info != 0) {
                logger.error("LAPACK GESDD error code: {}", info);
                throw new ArithmeticException("LAPACK GESDD error code: " + info);
            }

            return new SVD(s, U, VT.transpose());
        } else {
            FloatMatrix U = new FloatMatrix(1, 1);
            FloatMatrix VT = new FloatMatrix(1, 1);

            int info = LAPACK.engine.gesdd(W.layout(), SVDJob.NO_VECTORS, W.m, W.n, W.A, W.ld, FloatBuffer.wrap(s), U.A, U.ld, VT.A, VT.ld);
            if (info != 0) {
                logger.error("LAPACK GESDD error code: {}", info);
                throw new ArithmeticException("LAPACK GESDD error code: " + info);
            }

            return new SVD(m, n, s);
        }
    }

    /**
     * Eigenvalue Decomposition. For a symmetric matrix, all eigenvalues are
     * real values. Otherwise, the eigenvalues may be complex numbers.
     * <p>
     * By default <code>eigen</code> does not always return the eigenvalues
     * and eigenvectors in sorted order. Use the <code>EVD.sort</code> function
     * to put the eigenvalues in descending order and reorder the corresponding
     * eigenvectors.
     * @return eigen value decomposition.
     */
    public EVD eigen() {
        return eigen(false, true, false);
    }

    /**
     * Eigenvalue Decomposition. For a symmetric matrix, all eigenvalues are
     * real values. Otherwise, the eigenvalues may be complex numbers.
     * <p>
     * By default <code>eigen</code> does not always return the eigenvalues
     * and eigenvectors in sorted order. Use the <code>sort</code> function
     * to put the eigenvalues in descending order and reorder the corresponding
     * eigenvectors.
     *
     * @param vl The flag if computing the left eigenvectors.
     * @param vr The flag if computing the right eigenvectors.
     * @param overwrite The flag if the decomposition overwrites this matrix.
     * @return eigen value decomposition.
     */
    public EVD eigen(boolean vl, boolean vr, boolean overwrite) {
        if (m != n) {
            throw new IllegalArgumentException(String.format("The matrix is not square: %d x %d", m, n));
        }

        FloatMatrix eig = overwrite ? this : clone();
        if (isSymmetric()) {
            float[] w = new float[n];
            int info = LAPACK.engine.syevd(eig.layout(), vr ? EVDJob.VECTORS : EVDJob.NO_VECTORS, eig.uplo, n, eig.A, eig.ld, FloatBuffer.wrap(w));
            if (info != 0) {
                logger.error("LAPACK SYEV error code: {}", info);
                throw new ArithmeticException("LAPACK SYEV error code: " + info);
            }
            return new EVD(w, vr ? eig : null);
        } else {
            float[] wr = new float[n];
            float[] wi = new float[n];
            FloatMatrix Vl = vl ? new FloatMatrix(n, n) : new FloatMatrix(1, 1);
            FloatMatrix Vr = vr ? new FloatMatrix(n, n) : new FloatMatrix(1, 1);
            int info = LAPACK.engine.geev(eig.layout(), vl ? EVDJob.VECTORS : EVDJob.NO_VECTORS, vr ? EVDJob.VECTORS : EVDJob.NO_VECTORS, n, eig.A, eig.ld, FloatBuffer.wrap(wr), FloatBuffer.wrap(wi), Vl.A, Vl.ld, Vr.A, Vr.ld);
            if (info != 0) {
                logger.error("LAPACK GEEV error code: {}", info);
                throw new ArithmeticException("LAPACK GEEV error code: " + info);
            }

            return new EVD(wr, wi, vl ? Vl : null, vr ? Vr : null);
        }
    }

    /**
     * Singular Value Decomposition.
     * <p>
     * For an m-by-n matrix A with {@code m >= n}, the singular value decomposition is
     * an m-by-n orthogonal matrix U, an n-by-n diagonal matrix &Sigma;, and
     * an n-by-n orthogonal matrix V so that A = U*&Sigma;*V'.
     * <p>
     * For {@code m < n}, only the first m columns of V are computed and &Sigma; is m-by-m.
     * <p>
     * The singular values, &sigma;<sub>k</sub> = &Sigma;<sub>kk</sub>, are ordered
     * so that &sigma;<sub>0</sub> &ge; &sigma;<sub>1</sub> &ge; ... &ge; &sigma;<sub>n-1</sub>.
     * <p>
     * The singular value decomposition always exists. The matrix condition number
     * and the effective numerical rank can be computed from this decomposition.
     * <p>
     * SVD is a very powerful technique for dealing with sets of equations or matrices
     * that are either singular or else numerically very close to singular. In many
     * cases where Gaussian elimination and LU decomposition fail to give satisfactory
     * results, SVD will diagnose precisely what the problem is. SVD is also the
     * method of choice for solving most linear least squares problems.
     * <p>
     * Applications which employ the SVD include computing the pseudo-inverse, least
     * squares fitting of data, matrix approximation, and determining the rank,
     * range and null space of a matrix. The SVD is also applied extensively to
     * the study of linear inverse problems, and is useful in the analysis of
     * regularization methods such as that of Tikhonov. It is widely used in
     * statistics where it is related to principal component analysis. Yet another
     * usage is latent semantic indexing in natural language text processing.
     *
     * @author Haifeng Li
     */
    public static class SVD implements Serializable {
        private static final long serialVersionUID = 2L;
        /**
         * The number of rows of matrix.
         */
        public final int m;
        /**
         * The number of columns of matrix.
         */
        public final int n;
        /**
         * The singular values in descending order.
         */
        public final float[] s;
        /**
         * The left singular vectors U.
         */
        public final FloatMatrix U;
        /**
         * The right singular vectors V.
         */
        public final FloatMatrix V;

        /**
         * Constructor.
         * @param m the number of rows of matrix.
         * @param n the number of columns of matrix.
         * @param s the singular values in descending order.
         */
        public SVD(int m, int n, float[] s) {
            this.m = m;
            this.n = n;
            this.s = s;
            this.U = null;
            this.V = null;
        }

        /**
         * Constructor.
         * @param s the singular values in descending order.
         * @param U the left singular vectors
         * @param V the right singular vectors.
         */
        public SVD(float[] s, FloatMatrix U, FloatMatrix V) {
            this.m = U.m;
            this.n = V.m;
            this.s = s;
            this.U = U;
            this.V = V;
        }

        /**
         * Returns the diagonal matrix of singular values.
         * @return the diagonal matrix of singular values.
         */
        public FloatMatrix diag() {
            FloatMatrix S = new FloatMatrix(U.m, V.m);

            for (int i = 0; i < s.length; i++) {
                S.set(i, i, s[i]);
            }

            return S;
        }

        /**
         * Returns the L<sub>2</sub> matrix norm that is the largest singular value.
         * @return L<sub>2</sub> matrix norm.
         */
        public double norm() {
            return s[0];
        }

        /**
         * Returns the threshold to determine the effective rank.
         * Singular values S(i) <= RCOND are treated as zero.
         * @return the threshold to determine the effective rank.
         */
        private float rcond() {
            return 0.5f * (float) Math.sqrt(m + n + 1) * s[0] * MathEx.FLOAT_EPSILON;
        }

        /**
         * Returns the effective numerical matrix rank. The number of non-negligible
         * singular values.
         * @return the effective numerical matrix rank.
         */
        public int rank() {
            if (s.length != Math.min(m, n)) {
                throw new UnsupportedOperationException("The operation cannot be called on a partial SVD.");
            }

            int r = 0;
            float tol = rcond();

            for (float si : s) {
                if (si > tol) {
                    r++;
                }
            }
            return r;
        }

        /**
         * Returns the dimension of null space. The number of negligible
         * singular values.
         * @return the dimension of null space.
         */
        public int nullity() {
            return Math.min(m, n) - rank();
        }

        /**
         * Returns the L<sub>2</sub> norm condition number, which is max(S) / min(S).
         * A system of equations is considered to be well-conditioned if a small
         * change in the coefficient matrix or a small change in the right hand
         * side results in a small change in the solution vector. Otherwise, it is
         * called ill-conditioned. Condition number is defined as the product of
         * the norm of A and the norm of A<sup>-1</sup>. If we use the usual
         * L<sub>2</sub> norm on vectors and the associated matrix norm, then the
         * condition number is the ratio of the largest singular value of matrix
         * A to the smallest. The condition number depends on the underlying norm.
         * However, regardless of the norm, it is always greater or equal to 1.
         * If it is close to one, the matrix is well conditioned. If the condition
         * number is large, then the matrix is said to be ill-conditioned. A matrix
         * that is not invertible has the condition number equal to infinity.
         *
         * @return L<sub>2</sub> norm condition number.
         */
        public float condition() {
            if (s.length != Math.min(m, n)) {
                throw new UnsupportedOperationException("The operation cannot be called on a partial SVD.");
            }

            return (s[0] <= 0.0f || s[s.length - 1] <= 0.0f) ? Float.POSITIVE_INFINITY : s[0] / s[s.length - 1];
        }

        /**
         * Returns the matrix which columns are the orthonormal basis for the range space.
         * Returns null if the rank is zero (if and only if zero matrix).
         * @return the range space span matrix.
         */
        public FloatMatrix range() {
            if (s.length != Math.min(m, n)) {
                throw new UnsupportedOperationException("The operation cannot be called on a partial SVD.");
            }

            if (U == null) {
                throw new IllegalStateException("The left singular vectors are not available.");
            }

            int r = rank();
            // zero rank, if and only if zero matrix.
            if (r == 0) {
                return null;
            }

            FloatMatrix R = new FloatMatrix(m, r);
            for (int j = 0; j < r; j++) {
                for (int i = 0; i < m; i++) {
                    R.set(i, j, U.get(i, j));
                }
            }

            return R;
        }

        /**
         * Returns the matrix which columns are the orthonormal basis for the null space.
         * Returns null if the matrix is of full rank.
         * @return the null space span matrix.
         */
        public FloatMatrix nullspace() {
            if (s.length != Math.min(m, n)) {
                throw new UnsupportedOperationException("The operation cannot be called on a partial SVD.");
            }

            if (V == null) {
                throw new IllegalStateException("The right singular vectors are not available.");
            }

            int nr = nullity();
            // full rank
            if (nr == 0) {
                return null;
            }

            FloatMatrix N = new FloatMatrix(n, nr);
            for (int j = 0; j < nr; j++) {
                    for (int i = 0; i < n; i++) {
                        N.set(i, j, V.get(i, n - j - 1));
                    }
            }
            return N;
        }

        /**
         * Returns the pseudo inverse.
         * @return the pseudo inverse.
         */
        public FloatMatrix pinv() {
            int k = s.length;
            float[] sigma = new float[k];
            int r = rank();
            for (int i = 0; i < r; i++) {
                sigma[i] = 1.0f / s[i];
            }

            return V.adb(NO_TRANSPOSE, TRANSPOSE, U, sigma);
        }

        /**
         * Solves the least squares min || B - A*X ||.
         * @param b the right hand side of overdetermined linear system.
         * @throws RuntimeException when matrix is rank deficient.
         * @return the solution vector beta that minimizes ||Y - X*beta||.
         */
        public float[] solve(float[] b) {
            if (U == null || V == null) {
                throw new IllegalStateException("The singular vectors are not available.");
            }

            if (b.length != m) {
                throw new IllegalArgumentException(String.format("Row dimensions do not agree: A is %d x %d, but B is %d x 1", m, n, b.length));
            }

            int r = rank();
            float[] Utb = new float[s.length];
            U.submatrix(0, 0, m-1, r-1).tv(b, Utb);
            for (int i = 0; i < r; i++) {
                Utb[i] /= s[i];
            }
            return V.mv(Utb);
        }
    }

    /**
     * Eigenvalue decomposition. Eigen decomposition is the factorization
     * of a matrix into a canonical form, whereby the matrix is represented in terms
     * of its eigenvalues and eigenvectors:
     * <pre>{@code
     *     A = V*D*V<sup>-1</sup>
     * }</pre>
     * If A is symmetric, then A = V*D*V' where the eigenvalue matrix D is
     * diagonal and the eigenvector matrix V is orthogonal.
     * <p>
     * Given a linear transformation A, a non-zero vector x is defined to be an
     * eigenvector of the transformation if it satisfies the eigenvalue equation
     * <pre>{@code
     *     A x = &lambda; x
     * }</pre>
     * for some scalar &lambda;. In this situation, the scalar &lambda; is called
     * an eigenvalue of A corresponding to the eigenvector x.
     * <p>
     * The word eigenvector formally refers to the right eigenvector, which is
     * defined by the above eigenvalue equation A x = &lambda; x, and is the most
     * commonly used eigenvector. However, the left eigenvector exists as well, and
     * is defined by x A = &lambda; x.
     * <p>
     * Let A be a real n-by-n matrix with strictly positive entries a<sub>ij</sub>
     * {@code > 0}. Then the following statements hold.
     * <ol>
     * <li> There is a positive real number r, called the Perron-Frobenius
     * eigenvalue, such that r is an eigenvalue of A and any other eigenvalue &lambda;
     * (possibly complex) is strictly smaller than r in absolute value,
     * |&lambda;| {@code < r}.
     * <li> The Perron-Frobenius eigenvalue is simple: r is a simple root of the
     *      characteristic polynomial of A. Consequently, both the right and the left
     *      eigenspace associated to r is one-dimensional.
     * </li>
     * <li> There exists a left eigenvector v of A associated with r (row vector)
     *      having strictly positive components. Likewise, there exists a right
     *      eigenvector w associated with r (column vector) having strictly positive
     *      components.
     * </li>
     * <li> The left eigenvector v (respectively right w) associated with r, is the
     *      only eigenvector which has positive components, i.e. for all other
     *      eigenvectors of A there exists a component which is not positive.
     * </li>
     * </ol>
     * <p>
     * A stochastic matrix, probability matrix, or transition matrix is used to
     * describe the transitions of a Markov chain. A right stochastic matrix is
     * a square matrix each of whose rows consists of nonnegative real numbers,
     * with each row summing to 1. A left stochastic matrix is a square matrix
     * whose columns consist of nonnegative real numbers whose sum is 1. A doubly
     * stochastic matrix where all entries are nonnegative and all rows and all
     * columns sum to 1. A stationary probability vector &pi; is defined as a
     * vector that does not change under application of the transition matrix;
     * that is, it is defined as a left eigenvector of the probability matrix,
     * associated with eigenvalue 1: &pi;P = &pi;. The Perron-Frobenius theorem
     * ensures that such a vector exists, and that the largest eigenvalue
     * associated with a stochastic matrix is always 1. For a matrix with strictly
     * positive entries, this vector is unique. In general, however, there may be
     * several such vectors.
     *
     * @author Haifeng Li
     */
    public static class EVD implements Serializable {
        private static final long serialVersionUID = 2L;
        /**
         * The real part of eigenvalues.
         * By default the eigenvalues and eigenvectors are not always in
         * sorted order. The <code>sort</code> function puts the eigenvalues
         * in descending order and reorder the corresponding eigenvectors.
         */
        public final float[] wr;
        /**
         * The imaginary part of eigenvalues.
         */
        public final float[] wi;
        /**
         * The left eigenvectors.
         */
        public final FloatMatrix Vl;
        /**
         * The right eigenvectors.
         */
        public final FloatMatrix Vr;

        /**
         * Constructor.
         *
         * @param w eigenvalues.
         * @param V eigenvectors.
         */
        public EVD(float[] w, FloatMatrix V) {
            this.wr = w;
            this.wi = null;
            this.Vl = V;
            this.Vr = V;
        }

        /**
         * Constructor.
         *
         * @param wr the real part of eigenvalues.
         * @param wi the imaginary part of eigenvalues.
         * @param Vl the left eigenvectors.
         * @param Vr the right eigenvectors.
         */
        public EVD(float[] wr, float[] wi, FloatMatrix Vl, FloatMatrix Vr) {
            this.wr = wr;
            this.wi = wi;
            this.Vl = Vl;
            this.Vr = Vr;
        }

        /**
         * Returns the block diagonal eigenvalue matrix whose diagonal are the real
         * part of eigenvalues, lower subdiagonal are positive imaginary parts, and
         * upper subdiagonal are negative imaginary parts.
         * @return the diagonal eigenvalue matrix.
         */
        public FloatMatrix diag() {
            FloatMatrix D = FloatMatrix.diag(wr);

            if (wi != null) {
                int n = wr.length;
                for (int i = 0; i < n; i++) {
                    if (wi[i] > 0) {
                        D.set(i, i + 1, wi[i]);
                    } else if (wi[i] < 0) {
                        D.set(i, i - 1, wi[i]);
                    }
                }
            }

            return D;
        }

        /**
         * Sorts the eigenvalues in descending order and reorders the
         * corresponding eigenvectors.
         * @return sorted eigen decomposition.
         */
        public EVD sort() {
            int n = wr.length;
            float[] w = new float[n];
            if (wi != null) {
                for (int i = 0; i < n; i++) {
                    w[i] = -(wr[i] * wr[i] + wi[i] * wi[i]);
                }
            } else {
                for (int i = 0; i < n; i++) {
                    w[i] = -(wr[i] * wr[i]);
                }
            }

            int[] index = QuickSort.sort(w);
            float[] wr2 = new float[n];
            for (int j = 0; j < n; j++) {
                wr2[j] = wr[index[j]];
            }

            float[] wi2 = null;
            if (wi != null) {
                wi2 = new float[n];
                for (int j = 0; j < n; j++) {
                    wi2[j] = wi[index[j]];
                }
            }

            FloatMatrix Vl2 = null;
            if (Vl != null) {
                int m = Vl.m;
                Vl2 = new FloatMatrix(m, n);
                for (int j = 0; j < n; j++) {
                    for (int i = 0; i < m; i++) {
                        Vl2.set(i, j, Vl.get(i, index[j]));
                    }
                }
            }

            FloatMatrix Vr2 = null;
            if (Vr != null) {
                int m = Vr.m;
                Vr2 = new FloatMatrix(m, n);
                for (int j = 0; j < n; j++) {
                    for (int i = 0; i < m; i++) {
                        Vr2.set(i, j, Vr.get(i, index[j]));
                    }
                }
            }

            return new EVD(wr2, wi2, Vl2, Vr2);
        }
    }

    /**
     * The LU decomposition. For an m-by-n matrix A with {@code m >= n}, the LU
     * decomposition is an m-by-n unit lower triangular matrix L, an n-by-n
     * upper triangular matrix U, and a permutation vector piv of length m
     * so that A(piv,:) = L*U. If {@code m < n}, then L is m-by-m and U is m-by-n.
     * <p>
     * The LU decomposition with pivoting always exists, even if the matrix is
     * singular. The primary use of the LU decomposition is in the solution of
     * square systems of simultaneous linear equations if it is not singular.
     * The decomposition can also be used to calculate the determinant.
     *
     * @author Haifeng Li
     */
    public static class LU implements Serializable {
        private static final long serialVersionUID = 2L;
        /**
         * The LU decomposition.
         */
        public final FloatMatrix lu;

        /**
         * The pivot vector.
         */
        public final int[] ipiv;

        /**
         * If {@code info = 0}, the LU decomposition was successful.
         * If {@code info = i > 0}, U(i,i) is exactly zero. The factorization
         * has been completed, but the factor U is exactly
         * singular, and division by zero will occur if it is used
         * to solve a system of equations.
         */
        public final int info;

        /**
         * Constructor.
         * @param lu   LU decomposition matrix.
         * @param ipiv the pivot vector.
         * @param info {@code info > 0} if the matrix is singular.
         */
        public LU(FloatMatrix lu, int[] ipiv, int info) {
            this.lu = lu;
            this.ipiv = ipiv;
            this.info = info;
        }

        /**
         * Returns if the matrix is singular.
         * @return true if the matrix is singular.
         */
        public boolean isSingular() {
            return info > 0;
        }

        /**
         * Returns the matrix determinant.
         * @return the matrix determinant.
         */
        public float det() {
            int m = lu.m;
            int n = lu.n;

            if (m != n) {
                throw new IllegalArgumentException(String.format("The matrix is not square: %d x %d", m, n));
            }

            double d = 1.0;
            for (int j = 0; j < n; j++) {
                d *= lu.get(j, j);
            }

            for (int j = 0; j < n; j++){
                if (j+1 != ipiv[j]) {
                    d = -d;
                }
            }

            return (float) d;
        }

        /**
         * Returns the inverse of matrix. For pseudo inverse, use QRDecomposition.
         * @return the inverse of matrix.
         */
        public FloatMatrix inverse() {
            FloatMatrix inv = FloatMatrix.eye(lu.n);
            solve(inv);
            return inv;
        }

        /**
         * Solve A * x = b.
         * @param b the right hand side of linear system.
         * @throws RuntimeException when the matrix is singular.
         * @return the solution vector.
         */
        public float[] solve(float[] b) {
            float[] x = b.clone();
            solve(new FloatMatrix(x));
            return x;
        }

        /**
         * Solve A * X = B. B will be overwritten with the solution matrix on output.
         * @param B the right hand side of linear system.
         *          On output, B will be overwritten with the solution matrix.
         * @throws RuntimeException when the matrix is singular.
         */
        public void solve(FloatMatrix B) {
            if (lu.m != lu.n) {
                throw new IllegalArgumentException(String.format("The matrix is not square: %d x %d", lu.m, lu.n));
            }

            if (B.m != lu.m) {
                throw new IllegalArgumentException(String.format("Row dimensions do not agree: A is %d x %d, but B is %d x %d", lu.m, lu.n, B.m, B.n));
            }

            if (lu.layout() != B.layout()) {
                throw new IllegalArgumentException("The matrix layout is inconsistent.");
            }

            if (info > 0) {
                throw new RuntimeException("The matrix is singular.");
            }

            int ret = LAPACK.engine.getrs(lu.layout(), NO_TRANSPOSE, lu.n, B.n, lu.A, lu.ld, IntBuffer.wrap(ipiv), B.A, B.ld);
            if (ret != 0) {
                logger.error("LAPACK GETRS error code: {}", ret);
                throw new ArithmeticException("LAPACK GETRS error code: " + ret);
            }
        }
    }

    /**
     * The Cholesky decomposition of a symmetric, positive-definite matrix.
     * When it is applicable, the Cholesky decomposition is roughly twice as
     * efficient as the LU decomposition for solving systems of linear equations.
     * <p>
     * The Cholesky decomposition is mainly used for the numerical solution of
     * linear equations. The Cholesky decomposition is also commonly used in
     * the Monte Carlo method for simulating systems with multiple correlated
     * variables: The matrix of inter-variable correlations is decomposed,
     * to give the lower-triangular L. Applying this to a vector of uncorrelated
     * simulated shocks, u, produces a shock vector Lu with the covariance
     * properties of the system being modeled.
     * <p>
     * Unscented Kalman filters commonly use the Cholesky decomposition to choose
     * a set of so-called sigma points. The Kalman filter tracks the average
     * state of a system as a vector x of length n and covariance as an n-by-n
     * matrix P. The matrix P is always positive semi-definite, and can be
     * decomposed into L*L'. The columns of L can be added and subtracted from
     * the mean x to form a set of 2n vectors called sigma points. These sigma
     * points completely capture the mean and covariance of the system state.
     *
     * @author Haifeng Li
     */
    public static class Cholesky implements Serializable {
        private static final long serialVersionUID = 2L;
        /**
         * The Cholesky decomposition.
         */
        public final FloatMatrix lu;

        /**
         * Constructor.
         * @param lu the lower/upper triangular part of matrix contains the Cholesky
         *           factorization.
         */
        public Cholesky(FloatMatrix lu) {
            if (lu.nrow() != lu.ncol()) {
                throw new UnsupportedOperationException("Cholesky constructor on a non-square matrix");
            }
            this.lu = lu;
        }

        /**
         * Returns the matrix determinant.
         * @return the matrix determinant.
         */
        public float det() {
            double d = 1.0;
            for (int i = 0; i < lu.n; i++) {
                d *= lu.get(i, i);
            }

            return (float) (d * d);
        }

        /**
         * Returns the log of matrix determinant.
         * @return the log of matrix determinant.
         */
        public float logdet() {
            int n = lu.n;
            double d = 0.0;
            for (int i = 0; i < n; i++) {
                d += Math.log(lu.get(i, i));
            }

            return (float) (2.0 * d);
        }

        /**
         * Returns the inverse of matrix.
         * @return the inverse of matrix.
         */
        public FloatMatrix inverse() {
            FloatMatrix inv = FloatMatrix.eye(lu.n);
            solve(inv);
            return inv;
        }

        /**
         * Solves the linear system A * x = b.
         * @param b the right hand side of linear systems.
         * @return the solution vector.
         */
        public float[] solve(float[] b) {
            float[] x = b.clone();
            solve(new FloatMatrix(x));
            return x;
        }

        /**
         * Solves the linear system A * X = B.
         * @param B the right hand side of linear systems. On output, B will
         *          be overwritten with the solution matrix.
         */
        public void solve(FloatMatrix B) {
            if (B.m != lu.m) {
                throw new IllegalArgumentException(String.format("Row dimensions do not agree: A is %d x %d, but B is %d x %d", lu.m, lu.n, B.m, B.n));
            }

            int info = LAPACK.engine.potrs(lu.layout(), lu.uplo, lu.n, B.n, lu.A, lu.ld, B.A, B.ld);
            if (info != 0) {
                logger.error("LAPACK POTRS error code: {}", info);
                throw new ArithmeticException("LAPACK POTRS error code: " + info);
            }
        }
    }

    /**
     * The QR decomposition. For an m-by-n matrix A with {@code m >= n},
     * the QR decomposition is an m-by-n orthogonal matrix Q and
     * an n-by-n upper triangular matrix R such that A = Q*R.
     * <p>
     * The QR decomposition always exists, even if the matrix does not have
     * full rank. The primary use of the QR decomposition is in the least squares
     * solution of non-square systems of simultaneous linear equations.
     *
     * @author Haifeng Li
     */
    public static class QR implements Serializable {
        private static final long serialVersionUID = 2L;
        /**
         * The QR decomposition.
         */
        public final FloatMatrix qr;
        /**
         * The scalar factors of the elementary reflectors.
         */
        public final float[] tau;

        /**
         * Constructor.
         * @param qr the QR decomposition.
         * @param tau the scalar factors of the elementary reflectors
         */
        public QR(FloatMatrix qr, float[] tau) {
            this.qr = qr;
            this.tau = tau;
        }

        /**
         * Returns the Cholesky decomposition of A'A.
         * @return the Cholesky decomposition of A'A.
         */
        public Cholesky CholeskyOfAtA() {
            int n = qr.n;
            FloatMatrix L = new FloatMatrix(n, n);
            for (int i = 0; i < n; i++) {
                for (int j = 0; j <= i; j++) {
                    L.set(i, j, qr.get(j, i));
                }
            }

            L.uplo(LOWER);
            return new Cholesky(L);
        }

        /**
         * Returns the upper triangular factor.
         * @return the upper triangular factor.
         */
        public FloatMatrix R() {
            int n = qr.n;
            FloatMatrix R = FloatMatrix.diag(tau);
            for (int i = 0; i < n; i++) {
                for (int j = i+1; j < n; j++) {
                    R.set(i, j, qr.get(i, j));
                }
            }

            return R;
        }

        /**
         * Returns the orthogonal factor.
         * @return the orthogonal factor.
         */
        public FloatMatrix Q() {
            int m = qr.m;
            int n = qr.n;
            FloatMatrix Q = new FloatMatrix(m, n);
            for (int k = n - 1; k >= 0; k--) {
                Q.set(k, k, 1.0f);
                for (int j = k; j < n; j++) {
                    if (qr.get(k, k) != 0) {
                        float s = 0.0f;
                        for (int i = k; i < m; i++) {
                            s += qr.get(i, k) * Q.get(i, j);
                        }
                        s = -s / qr.get(k, k);
                        for (int i = k; i < m; i++) {
                            Q.add(i, j, s * qr.get(i, k));
                        }
                    }
                }
            }
            return Q;
        }

        /**
         * Solves the least squares min || B - A*X ||.
         * @param b the right hand side of overdetermined linear system.
         * @throws RuntimeException when the matrix is rank deficient.
         * @return the solution vector beta that minimizes ||Y - X*beta||.
         */
        public float[] solve(float[] b) {
            if (b.length != qr.m) {
                throw new IllegalArgumentException(String.format("Row dimensions do not agree: A is %d x %d, but B is %d x 1", qr.m, qr.n, b.length));
            }

            float[] y = b.clone();
            solve(new FloatMatrix(y));
            float[] x = new float[qr.n];
            System.arraycopy(y, 0, x, 0, x.length);
            return x;
        }

        /**
         * Solves the least squares min || B - A*X ||.
         * @param B the right hand side of overdetermined linear system.
         *          B will be overwritten with the solution matrix on output.
         * @throws RuntimeException when the matrix is rank deficient.
         */
        public void solve(FloatMatrix B) {
            if (B.m != qr.m) {
                throw new IllegalArgumentException(String.format("Row dimensions do not agree: A is %d x %d, but B is %d x %d", qr.nrow(), qr.nrow(), B.nrow(), B.ncol()));
            }

            int m = qr.m;
            int n = qr.n;
            int k = Math.min(m, n);

            int info = LAPACK.engine.ormqr(qr.layout(), LEFT, TRANSPOSE, B.nrow(), B.ncol(), k, qr.A, qr.ld, FloatBuffer.wrap(tau), B.A, B.ld);
            if (info != 0) {
                logger.error("LAPACK ORMQR error code: {}", info);
                throw new IllegalArgumentException("LAPACK ORMQR error code: " + info);
            }

            info = LAPACK.engine.trtrs(qr.layout(), UPPER, NO_TRANSPOSE, NON_UNIT, qr.n, B.n, qr.A, qr.ld, B.A, B.ld);

            if (info != 0) {
                logger.error("LAPACK TRTRS error code: {}", info);
                throw new IllegalArgumentException("LAPACK TRTRS error code: " + info);
            }
        }
    }
}
