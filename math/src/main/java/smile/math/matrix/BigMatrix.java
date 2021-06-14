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

import org.bytedeco.javacpp.DoublePointer;
import org.bytedeco.javacpp.IntPointer;
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
 * Big dense matrix of double precision values for more than
 * 2 billion elements.
 *
 * @author Haifeng Li
 */
public class BigMatrix extends IMatrix {
    private static final long serialVersionUID = 3L;
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(BigMatrix.class);

    /** Row major matrix. */
    private static class RowMajor extends BigMatrix {
        /**
         * Constructor.
         * @param m the number of rows.
         * @param n the number of columns.
         * @param ld the leading dimension.
         * @param A the matrix storage.
         */
        RowMajor(int m, int n, int ld, DoublePointer A) {
            super(m, n, ld, A);
        }

        @Override
        public Layout layout() {
            return ROW_MAJOR;
        }

        @Override
        protected long index(int i, int j) {
            return i * ld + j;
        }
    }

    /**
     * The matrix storage.
     */
    transient DoublePointer A;
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
    public BigMatrix(int m, int n) {
        this(m, n, 0.0);
    }

    /**
     * Constructor. Fills the matrix with given value.
     * @param m the number of rows.
     * @param n the number of columns.
     * @param a the initial value.
     */
    public BigMatrix(int m, int n, double a) {
        if (m <= 0 || n <= 0) {
            throw new IllegalArgumentException(String.format("Invalid matrix size: %d x %d", m, n));
        }

        this.m = m;
        this.n = n;
        this.ld = ld(m);

        A = new DoublePointer(ld * n);
        fill(a);
    }

    /**
     * Constructor.
     * @param m the number of rows.
     * @param n the number of columns.
     * @param ld the leading dimension.
     * @param A the matrix storage.
     */
    public BigMatrix(int m, int n, int ld, DoublePointer A) {
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
     * Returns a matrix from a two-dimensional array.
     * @param A the two-dimensional array.
     * @return the matrix.
     */
    public static BigMatrix of(double[][] A) {
        int m = A.length;
        int n = A[0].length;
        BigMatrix matrix = new BigMatrix(m, n);

        for (int i = 0; i < m; i++) {
            for (int j = 0; j < n; j++) {
                matrix.set(i, j, A[i][j]);
            }
        }

        return matrix;
    }

    /**
     * Returns a column vector/matrix.
     * @param A the column vector.
     * @return the column vector/matrix.
     */
    public static BigMatrix column(double[] A) {
        return column(A, 0, A.length);
    }

    /**
     * Returns a column vector/matrix.
     * @param A the column vector.
     * @param offset the offset of the subarray to be used; must be non-negative and
     *               no larger than array.length.
     * @param length the length of the subarray to be used; must be non-negative and
     *               no larger than array.length - offset.
     * @return the column vector/matrix.
     */
    public static BigMatrix column(double[] A, int offset, int length) {
        DoublePointer pointer = new DoublePointer(length);
        pointer.put(A, offset, length);
        return new BigMatrix(length, 1, length, pointer);
    }

    /**
     * Returns a row vector/matrix.
     * @param A the row vector.
     * @return the row vector/matrix.
     */
    public static BigMatrix row(double[] A) {
        return row(A, 0, A.length);
    }

    /**
     * Returns a row vector/matrix.
     * @param A the row vector.
     * @param offset the offset of the subarray to be used; must be non-negative and
     *               no larger than array.length.
     * @param length the length of the subarray to be used; must be non-negative and
     *               no larger than array.length - offset.
     * @return the row vector/matrix.
     */
    public static BigMatrix row(double[] A, int offset, int length) {
        DoublePointer pointer = new DoublePointer(length);
        pointer.put(A, offset, length);
        return new BigMatrix(1, length, 1, pointer);
    }

    /**
     * Returns a random matrix.
     *
     * @param m the number of rows.
     * @param n the number of columns.
     * @param distribution the distribution of random number.
     * @return the matrix.
     */
    public static BigMatrix rand(int m, int n, Distribution distribution) {
        BigMatrix matrix = new BigMatrix(m, n);

        for (int j = 0; j < n; j++) {
            for (int i = 0; i < m; i++) {
                matrix.set(i, j, distribution.rand());
            }
        }

        return matrix;
    }

    /**
     * Returns a random matrix of standard normal distribution.
     * @param m the number of rows.
     * @param n the number of columns.
     * @return the matrix.
     */
    public static BigMatrix randn(int m, int n) {
        return rand(m, n, GaussianDistribution.getInstance());
    }

    /**
     * Returns a uniformly distributed random matrix in [0, 1).
     *
     * @param m the number of rows.
     * @param n the number of columns.
     * @return the random matrix.
     */
    public static BigMatrix rand(int m, int n) {
        BigMatrix matrix = new BigMatrix(m, n);

        for (int j = 0; j < n; j++) {
            for (int i = 0; i < m; i++) {
                matrix.set(i, j, MathEx.random());
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
    public static BigMatrix rand(int m, int n, double lo, double hi) {
        BigMatrix matrix = new BigMatrix(m, n);

        for (int j = 0; j < n; j++) {
            for (int i = 0; i < m; i++) {
                matrix.set(i, j, MathEx.random(lo, hi));
            }
        }

        return matrix;
    }

    /**
     * Returns an identity matrix.
     * @param n the number of rows/columns.
     * @return the matrix.
     */
    public static BigMatrix eye(int n) {
        return diag(n, 1.0);
    }

    /**
     * Returns an m-by-n identity matrix.
     * @param m the number of rows.
     * @param n the number of columns.
     * @return the matrix.
     */
    public static BigMatrix eye(int m, int n) {
        return diag(m, n, 1.0);
    }

    /**
     * Returns a square diagonal matrix.
     *
     * @param n the number of rows/columns.
     * @param diag the diagonal value.
     * @return the matrix.
     */
    public static BigMatrix diag(int n, double diag) {
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
    public static BigMatrix diag(int m, int n, double diag) {
        BigMatrix D = new BigMatrix(m, n);
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
    public static BigMatrix diag(double[] diag) {
        int n = diag.length;
        BigMatrix D = new BigMatrix(n, n);
        for (int i = 0; i < n; i++) {
            D.set(i, i, diag[i]);
        }
        return D;
    }

    /**
     * Returns a square diagonal matrix.
     *
     * @param diag the diagonal elements.
     * @return the matrix.
     */
    public static BigMatrix diag(DoublePointer diag) {
        int n = (int) length(diag);
        BigMatrix D = new BigMatrix(n, n);
        for (int i = 0; i < n; i++) {
            D.set(i, i, diag.get(i));
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
    public static BigMatrix toeplitz(double[] a) {
        int n = a.length;
        BigMatrix toeplitz = new BigMatrix(n, n);
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
    public static BigMatrix toeplitz(double[] kl, double[] ku) {
        if (kl.length != ku.length - 1) {
            throw new IllegalArgumentException(String.format("Invalid subdiagonals and superdiagonals size: %d != %d - 1", kl.length, ku.length));
        }

        int n = kl.length;
        BigMatrix toeplitz = new BigMatrix(n, n);

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
                    out.writeDouble(get(i, j));
                }
            }
        } else {
            for (int i = 0; i < m; i++) {
                for (int j = 0; j < n; j++) {
                    out.writeDouble(get(i, j));
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
        // read default properties
        in.defaultReadObject();

        // read buffer data
        if (layout() == COL_MAJOR) {
            this.ld = ld(m);
            this.A = new DoublePointer(ld * n);
            for (int j = 0; j < n; j++) {
                for (int i = 0; i < m; i++) {
                    set(i, j, in.readDouble());
                }
            }
        } else {
            this.ld = ld(n);
            this.A = new DoublePointer(m * ld);
            for (int i = 0; i < m; i++) {
                for (int j = 0; j < n; j++) {
                    set(i, j, in.readDouble());
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

    /** Returns the length of double array pointer. */
    private static long length(DoublePointer A) {
        return A.limit() - A.position();
    }

    /** Returns the byte length of double array pointer. */
    private static long bytes(DoublePointer A) {
        return A.sizeof() * (A.limit() - A.position());
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
    public BigMatrix uplo(UPLO uplo) {
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
    public BigMatrix triangular(Diag diag) {
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
    public BigMatrix clone() {
        BigMatrix matrix;
        if (layout() == COL_MAJOR) {
            DoublePointer pointer = new DoublePointer(length(A));
            DoublePointer.memcpy(pointer, A, bytes(A));
            matrix = new BigMatrix(m, n, ld, pointer);
        } else {
            matrix = new BigMatrix(m, n);
            for (int j = 0; j < n; j++) {
                for (int i = 0; i < m; i++) {
                    matrix.set(i, j, get(i, j));
                }
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
    public double[][] toArray() {
        double[][] array = new double[m][n];
        for (int i = 0; i < m; i++) {
            for (int j = 0; j < n; j++) {
                array[i][j] = get(i, j);
            }
        }
        return array;
    }

    /**
     * Sets the matrix value. If the matrices have the same layout,
     * this matrix will share the underlying storage with b.
     * @param b the right hand side of assignment.
     * @return this matrix.
     */
    public BigMatrix set(BigMatrix b) {
        this.m = b.m;
        this.n = b.n;
        this.diag = b.diag;
        this.uplo = b.uplo;

        if (layout() == b.layout()) {
            this.A = b.A;
            this.ld = b.ld;
        } else {
            if (layout() == COL_MAJOR) {
                this.ld = ld(m);
                this.A = new DoublePointer(ld * n);

                for (int j = 0; j < n; j++) {
                    for (int i = 0; i < m; i++) {
                        set(i, j, get(i, j));
                    }
                }
            } else {
                this.ld = ld(n);
                this.A = new DoublePointer(ld * m);

                for (int i = 0; i < m; i++) {
                    for (int j = 0; j < n; j++) {
                        set(i, j, get(i, j));
                    }
                }
            }
        }

        return this;
    }

    /**
     * Returns the linearized index of matrix element.
     * @param i the row index.
     * @param j the column index.
     * @return the linearized index.
     */
    protected long index(int i , int j) {
        return j * ld + i;
    }

    @Override
    public double get(int i, int j) {
        return A.get(index(i, j));
    }

    @Override
    public void set(int i, int j, double x) {
        A.put(index(i, j), x);
    }

    /**
     * Returns the matrix of selected rows and columns.
     * Negative index -i means the i-th row/column from the end.
     *
     * @param rows the row indices.
     * @param cols the column indices.
     * @return the submatrix.
     */
    public BigMatrix get(int[] rows, int[] cols) {
        BigMatrix sub = new BigMatrix(rows.length, cols.length);
        for (int j = 0; j < cols.length; j++) {
            int col = cols[j];
            if (col < 0) col = n + col;
            for (int i = 0; i < rows.length; i++) {
                int row = rows[i];
                if (row < 0) row = m + row;
                sub.set(i, j, get(row, col));
            }
        }

        return sub;
    }

    /**
     * Returns the i-th row. Negative index -i means the i-th row from the end.
     * @param i the row index.
     * @return the row.
     */
    public double[] row(int i) {
        double[] x = new double[n];
        if (i < 0) i = m + i;

        for (int j = 0; j < n; j++) {
            x[j] = get(i, j);
        }

        return x;
    }

    /**
     * Returns the j-th column. Negative index -j means the j-th row from the end.
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
    public BigMatrix row(int... rows) {
        BigMatrix x = new BigMatrix(rows.length, n);

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
    public BigMatrix col(int... cols) {
        BigMatrix x = new BigMatrix(m, cols.length);

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
    public BigMatrix submatrix(int i, int j, int k, int l) {
        if (i < 0 || i >= m || k < i || k >= m || j < 0 || j >= n || l < j || l >= n) {
            throw new IllegalArgumentException(String.format("Invalid submatrix range (%d:%d, %d:%d) of %d x %d", i, k, j, l, m, n));
        }

        long offset = index(i, j);
        long length = index(k, l) - offset + 1;
        DoublePointer B = A.getPointer(offset).limit(length);

        if (layout() == COL_MAJOR) {
            return new BigMatrix(k - i + 1, l - j + 1, ld, B);
        } else {
            return new RowMajor(k - i + 1, l - j + 1, ld, B);
        }
    }

    /**
     * Fill the matrix with a value.
     * @param x the value.
     */
    public void fill(double x) {
        if (x == 0.0) {
            DoublePointer.memset(A, 0, bytes(A));
        } else {
            long length = length(A);
            for (long i = 0; i < length; i++) {
                A.put(i, x);
            }
        }
    }

    /**
     * Returns the transpose of matrix. The transpose shares the storage
     * with this matrix. Changes to this matrix's content will be visible
     * in the transpose, and vice versa.
     *
     * @return the transpose of matrix.
     */
    public BigMatrix transpose() {
        return transpose(true);
    }

    /**
     * Returns the transpose of matrix.
     * @param share if true, the transpose shares the storage with this matrix.
     *              Changes to this matrix's content will be visible in the
     *              transpose, and vice versa.
     * @return the transpose of matrix.
     */
    public BigMatrix transpose(boolean share) {
        BigMatrix matrix;
        if (share) {
            if (layout() == ROW_MAJOR) {
                matrix = new BigMatrix(n, m, ld, A);
            } else {
                matrix = new RowMajor(n, m, ld, A);
            }
        } else {
            matrix = new BigMatrix(n, m);
            for (int j = 0; j < m; j++) {
                for (int i = 0; i < n; i++) {
                    matrix.set(i, j, get(j, i));
                }
            }
        }

        if (m == n) {
            matrix.uplo(uplo);
            matrix.triangular(diag);
        }

        return matrix;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof BigMatrix)) {
            return false;
        }

        return equals((BigMatrix) o, 1E-10);
    }

    /**
     * Returns true if two matrices equal in given precision.
     *
     * @param o the other matrix.
     * @param epsilon a number close to zero.
     * @return true if two matrices equal in given precision.
     */
    public boolean equals(BigMatrix o, double epsilon) {
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
     * A[i,j] += b
     * @param i the row index.
     * @param j the column index.
     * @param b the operand.
     * @return the updated cell value.
     */
    public double add(int i, int j, double b) {
        long k = index(i, j);
        double y = A.get(k) + b;
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
    public double sub(int i, int j, double b) {
        long k = index(i, j);
        double y = A.get(k) - b;
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
    public double mul(int i, int j, double b) {
        long k = index(i, j);
        double y = A.get(k) * b;
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
    public double div(int i, int j, double b) {
        long k = index(i, j);
        double y = A.get(k) / b;
        A.put(k, y);
        return y;
    }

    /**
     * A[i, i] += b
     * @param b the operand.
     * @return this matrix.
     */
    public BigMatrix addDiag(double b) {
        int l = Math.min(m, n);
        for (int i = 0; i < l; i++) {
            long k = index(i, i);
            A.put(m, A.get(k) + b);
        }

        return this;
    }

    /**
     * A[i, i] += b[i]
     * @param b the operand.
     * @return this matrix.
     */
    public BigMatrix addDiag(double[] b) {
        int l = Math.min(m, n);
        if (b.length != l) {
            throw new IllegalArgumentException("Invalid diagonal array size: " + b.length);
        }

        for (int i = 0; i < l; i++) {
            long k = index(i, i);
            A.put(m, A.get(k) + b[i]);
        }

        return this;
    }

    /**
     * A += b
     * @param b the operand.
     * @return this matrix.
     */
    public BigMatrix add(double b) {
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
    public BigMatrix sub(double b) {
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
    public BigMatrix mul(double b) {
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
    public BigMatrix div(double b) {
        for (int j = 0; j < n; j++) {
            for (int i = 0; i < m; i++) {
                div(i, j, b);
            }
        }

        return this;
    }

    /**
     * Element-wise addition A += B
     * @param B the operand.
     * @return this matrix.
     */
    public BigMatrix add(BigMatrix B) {
        if (m != B.m || n != B.n) {
            throw new IllegalArgumentException("Matrix is not of same size.");
        }

        for (int j = 0; j < n; j++) {
            for (int i = 0; i < m; i++) {
                add(i, j, B.get(i, j));
            }
        }

        return this;
    }

    /**
     * Element-wise subtraction A -= B
     * @param B the operand.
     * @return this matrix.
     */
    public BigMatrix sub(BigMatrix B) {
        if (m != B.m || n != B.n) {
            throw new IllegalArgumentException("Matrix is not of same size.");
        }

        for (int j = 0; j < n; j++) {
            for (int i = 0; i < m; i++) {
                sub(i, j, B.get(i, j));
            }
        }

        return this;
    }

    /**
     * Element-wise multiplication A *= B
     * @param B the operand.
     * @return this matrix.
     */
    public BigMatrix mul(BigMatrix B) {
        if (m != B.m || n != B.n) {
            throw new IllegalArgumentException("Matrix is not of same size.");
        }

        for (int j = 0; j < n; j++) {
            for (int i = 0; i < m; i++) {
                mul(i, j, B.get(i, j));
            }
        }

        return this;
    }

    /**
     * Element-wise division A /= B
     * @param B the operand.
     * @return this matrix.
     */
    public BigMatrix div(BigMatrix B) {
        if (m != B.m || n != B.n) {
            throw new IllegalArgumentException("Matrix is not of same size.");
        }

        for (int j = 0; j < n; j++) {
            for (int i = 0; i < m; i++) {
                div(i, j, B.get(i, j));
            }
        }

        return this;
    }

    /**
     * Element-wise addition A += beta * B
     * @param beta the scalar alpha.
     * @param B the operand.
     * @return this matrix.
     */
    public BigMatrix add(double beta, BigMatrix B) {
        if (m != B.m || n != B.n) {
            throw new IllegalArgumentException("Matrix is not of same size.");
        }

        for (int j = 0; j < n; j++) {
            for (int i = 0; i < m; i++) {
                add(i, j, beta * B.get(i, j));
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
    public BigMatrix add(double alpha, BigMatrix A, double beta, BigMatrix B) {
        if (m != A.m || n != A.n) {
            throw new IllegalArgumentException("Matrix A is not of same size.");
        }

        if (m != B.m || n != B.n) {
            throw new IllegalArgumentException("Matrix B is not of same size.");
        }

        if (layout() == A.layout() && layout() == B.layout() && ld == A.ld && ld == B.ld) {
            long size = length(this.A);
            for (long i = 0; i < size; i++) {
                double a = A.A.get(i);
                double b = B.A.get(i);
                this.A.put(i, alpha * a + beta * b);
            }
        } else {
            for (int j = 0; j < n; j++) {
                for (int i = 0; i < m; i++) {
                    set(i, j, alpha * A.get(i, j) + beta * B.get(i, j));
                }
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
    public BigMatrix add(double alpha, double beta, BigMatrix B) {
        if (m != B.m || n != B.n) {
            throw new IllegalArgumentException("Matrix B is not of same size.");
        }

        if (layout() == B.layout() && ld == B.ld) {
            long size = (int) length(A);
            for (long i = 0; i < size; i++) {
                double a = A.get(i);
                double b = B.A.get(i);
                A.put(i, alpha * a + beta * b);
            }
        } else {
            for (int j = 0; j < n; j++) {
                for (int i = 0; i < m; i++) {
                    set(i, j, alpha * get(i, j) + beta * B.get(i, j));
                }
            }
        }

        return this;
    }

    /**
     * Element-wise addition A = alpha * A + beta * B^2
     * @param alpha the scalar alpha.
     * @param beta the scalar beta.
     * @param B the operand.
     * @return this matrix.
     */
    public BigMatrix add2(double alpha, double beta, BigMatrix B) {
        if (m != B.m || n != B.n) {
            throw new IllegalArgumentException("Matrix B is not of same size.");
        }

        if (layout() == B.layout() && ld == B.ld) {
            long size = length(A);
            for (long i = 0; i < size; i++) {
                double a = A.get(i);
                double b = B.A.get(i);
                A.put(i, alpha * a + beta * b * b);
            }
        } else {
            for (int j = 0; j < n; j++) {
                for (int i = 0; i < m; i++) {
                    set(i, j, alpha * get(i, j) + beta * B.get(i, j) * B.get(i, j));
                }
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
    public BigMatrix add(double alpha, double[] x, double[] y) {
        if (m != x.length || n != y.length) {
            throw new IllegalArgumentException("Matrix is not of same size.");
        }

        if (isSymmetric() && x == y) {
            BLAS.engine.syr(layout(), uplo, m, alpha, new DoublePointer(x), 1, A, ld);
        } else {
            BLAS.engine.ger(layout(), m, n, alpha, new DoublePointer(x), 1, new DoublePointer(x), 1, A, ld);
        }

        return this;
    }

    /**
     * Replaces NaN's with given value.
     * @param x a real number.
     * @return this matrix.
     */
    public BigMatrix replaceNaN(double x) {
        long length = length(A);
        for (int i = 0; i < length; i++) {
            if (Double.isNaN(A.get(i))) {
                A.put(i, x);
            }
        }

        return this;
    }

    /**
     * Returns the sum of all elements.
     * @return the sum of all elements.
     */
    public double sum() {
        double s = 0.0;
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
    public double norm1() {
        double f = 0.0;
        for (int j = 0; j < n; j++) {
            double s = 0.0;
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
    public double norm2() {
        return svd(false, false).s.get(0);
    }

    /**
     * L<sub>2</sub> matrix norm that is the maximum singular value.
     * @return L<sub>2</sub> matrix norm.
     */
    public double norm() {
        return norm2();
    }

    /**
     * L<sub>&infin;</sub> matrix norm that is the maximum of row sums.
     * @return L<sub>&infin;</sub> matrix norm.
     */
    public double normInf() {
        double[] f = new double[m];
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
    public double normFro() {
        double f = 0.0;
        for (int j = 0; j < n; j++) {
            for (int i = 0; i < m; i++) {
                f = Math.hypot(f, get(i, j));
            }
        }

        return f;
    }

    /**
     * Returns the quadratic form {@code x' * A * x}.
     * The left upper submatrix of A is used in the computation based
     * on the size of x.
     * @param x the vector.
     * @return the quadratic form.
     */
    public double xAx(double[] x) {
        if (m != n) {
            throw new IllegalArgumentException(String.format("The matrix is not square: %d x %d", m, n));
        }

        if (n != x.length) {
            throw new IllegalArgumentException(String.format("Matrix: %d x %d, Vector: %d", m, n, x.length));
        }

        double[] Ax = mv(x);
        return MathEx.dot(x, Ax);
    }

    /**
     * Returns the sum of each row.
     * @return the sum of each row.
     */
    public double[] rowSums() {
        double[] x = new double[m];

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
    public double[] rowMeans() {
        double[] x = rowSums();

        for (int i = 0; i < m; i++) {
            x[i] /= n;
        }

        return x;
    }

    /**
     * Returns the standard deviations of each row.
     * @return the standard deviations of each row.
     */
    public double[] rowSds() {
        double[] x = new double[m];
        double[] x2 = new double[m];

        for (int j = 0; j < n; j++) {
            for (int i = 0; i < m; i++) {
                double a = get(i, j);
                x[i] += a;
                x2[i] += a * a;
            }
        }

        for (int i = 0; i < m; i++) {
            double mu = x[i] / n;
            x[i] = Math.sqrt(x2[i] / n - mu * mu);
        }

        return x;
    }

    /**
     * Returns the sum of each column.
     * @return the sum of each column.
     */
    public double[] colSums() {
        double[] x = new double[n];

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
    public double[] colMeans() {
        double[] x = new double[n];

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
    public double[] colSds() {
        double[] x = new double[n];

        for (int j = 0; j < n; j++) {
            double mu = 0.0;
            double sumsq = 0.0;
            for (int i = 0; i < m; i++) {
                double a = get(i, j);
                mu += a;
                sumsq += a * a;
            }
            mu /= m;
            x[j] = Math.sqrt(sumsq / m - mu * mu);
        }

        return x;
    }

    /**
     * Standardizes the columns of matrix.
     * @return a new matrix with zero mean and unit variance for each column.
     */
    public BigMatrix standardize() {
        double[] center = colMeans();
        double[] scale = colSds();
        return scale(center, scale);
    }

    /**
     * Centers and scales the columns of matrix.
     * @param center column center. If null, no centering.
     * @param scale column scale. If null, no scaling.
     * @return a new matrix with zero mean and unit variance for each column.
     */
    public BigMatrix scale(double[] center, double[] scale) {
        if (center == null && scale == null) {
            throw new IllegalArgumentException("Both center and scale are null");
        }

        BigMatrix matrix = new BigMatrix(m, n);

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
    public BigMatrix inverse() {
        if (m != n) {
            throw new IllegalArgumentException(String.format("The matrix is not square: %d x %d", m, n));
        }

        BigMatrix lu = clone();
        BigMatrix inv = eye(n);
        IntPointer ipiv = new IntPointer(n);
        if (isSymmetric()) {
            int info = LAPACK.engine.sysv(lu.layout(), uplo,  n, n, lu.A, lu.ld, ipiv, inv.A, inv.ld);
            if (info != 0) {
                throw new ArithmeticException("SYSV fails: " + info);
            }
        } else {
            int info = LAPACK.engine.gesv(lu.layout(), n, n, lu.A, lu.ld, ipiv, inv.A, inv.ld);
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
    private void mv(Transpose trans, double alpha, DoublePointer x, double beta, DoublePointer y) {
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
    public void mv(Transpose trans, double alpha, double[] x, double beta, double[] y) {
        DoublePointer xp = new DoublePointer(x);
        DoublePointer yp = new DoublePointer(y);
        mv(trans, alpha, xp, beta, yp);
        yp.get(y);
    }

    @Override
    public void mv(double[] work, int inputOffset, int outputOffset) {
        DoublePointer pointer = new DoublePointer(work);
        DoublePointer xb = pointer.getPointer(inputOffset).limit(n);
        DoublePointer yb = pointer.getPointer(outputOffset).limit(m);
        mv(NO_TRANSPOSE, 1.0, xb, 0.0, yb);
        pointer.get(work);
    }

    @Override
    public void tv(double[] work, int inputOffset, int outputOffset) {
        DoublePointer pointer = new DoublePointer(work);
        DoublePointer xb = pointer.getPointer(inputOffset).limit(m);
        DoublePointer yb = pointer.getPointer(outputOffset).limit(n);
        mv(TRANSPOSE, 1.0, xb, 0.0, yb);
        pointer.get(work);
    }

    /**
     * Matrix-matrix multiplication.
     * <pre>{@code
     *     C := A*B
     * }</pre>
     * @param transA normal, transpose, or conjugate transpose
     *               operation on the matrix A.
     * @param A the operand.
     * @param transB normal, transpose, or conjugate transpose
     *               operation on the matrix B.
     * @param B the operand.
     * @return this matrix.
     */
    public BigMatrix mm(Transpose transA, BigMatrix A, Transpose transB, BigMatrix B) {
        return mm(transA, A, transB, B, 1.0, 0.0);
    }

    /**
     * Matrix-matrix multiplication.
     * <pre>{@code
     *     C := alpha*A*B + beta*C
     * }</pre>
     * @param transA normal, transpose, or conjugate transpose
     *               operation on the matrix A.
     * @param A the operand.
     * @param transB normal, transpose, or conjugate transpose
     *               operation on the matrix B.
     * @param B the operand.
     * @param alpha the scalar alpha.
     * @param beta the scalar beta.
     * @return this matrix.
     */
    public BigMatrix mm(Transpose transA, BigMatrix A, Transpose transB, BigMatrix B, double alpha, double beta) {
        if (A.isSymmetric() && transB == NO_TRANSPOSE && B.layout() == layout()) {
            BLAS.engine.symm(layout(), LEFT, A.uplo, m, n, alpha, A.A, A.ld, B.A, B.ld, beta, this.A, ld);
        } else if (B.isSymmetric() && transA == NO_TRANSPOSE && A.layout() == layout()) {
            BLAS.engine.symm(layout(), RIGHT, B.uplo, m, n, alpha, B.A, B.ld, A.A, A.ld, beta, this.A, ld);
        } else {
            if (layout() != A.layout()) transA = flip(transA);
            if (layout() != B.layout()) transB = flip(transB);
            int k = transA == NO_TRANSPOSE ? A.n : A.m;

            BLAS.engine.gemm(layout(), transA, transB, m, n, k, alpha, A.A, A.ld, B.A, B.ld, beta, this.A, ld);
        }
        return this;
    }

    /**
     * Returns {@code A' * A}.
     * @return {@code A' * A}.
     */
    public BigMatrix ata() {
        BigMatrix C = new BigMatrix(n, n);
        C.mm(TRANSPOSE, this, NO_TRANSPOSE, this);
        C.uplo(LOWER);
        return C;
    }

    /**
     * Returns {@code A * A'}.
     * @return {@code A * A'}.
     */
    public BigMatrix aat() {
        BigMatrix C = new BigMatrix(m, m);
        C.mm(NO_TRANSPOSE, this, TRANSPOSE, this);
        C.uplo(LOWER);
        return C;
    }

    /**
     * Returns {@code A * D * B}, where D is a diagonal matrix.
     * @param transA normal, transpose, or conjugate transpose
     *               operation on the matrix A.
     * @param B the operand.
     * @param D the diagonal matrix.
     * @param transB normal, transpose, or conjugate transpose
     *               operation on the matrix B.
     * @param B the operand.
     * @return the multiplication.
     */
    public static BigMatrix adb(Transpose transA, BigMatrix A, double[] D, Transpose transB, BigMatrix B) {
        BigMatrix AD;
        int m = A.m, n = A.n;
        if (transA == NO_TRANSPOSE) {
            AD = new BigMatrix(m, n);
            for (int j = 0; j < n; j++) {
                double dj = D[j];
                for (int i = 0; i < m; i++) {
                    AD.set(i, j, dj * A.get(i, j));
                }
            }
        } else {
            AD = new BigMatrix(n, m);
            for (int j = 0; j < m; j++) {
                double dj = D[j];
                for (int i = 0; i < n; i++) {
                    AD.set(i, j, dj * A.get(j, i));
                }
            }
        }

        return transB == NO_TRANSPOSE ? AD.mm(B) : AD.mt(B);
    }

    /**
     * Returns matrix multiplication {@code A * B}.
     * @param B the operand.
     * @return the multiplication.
     */
    public BigMatrix mm(BigMatrix B) {
        if (n != B.m) {
            throw new IllegalArgumentException(String.format("Matrix multiplication A * B: %d x %d vs %d x %d", m, n, B.m, B.n));
        }

        BigMatrix C = new BigMatrix(m, B.n);
        C.mm(NO_TRANSPOSE, this, NO_TRANSPOSE, B);
        return C;
    }

    /**
     * Returns matrix multiplication {@code A * B'}.
     * @param B the operand.
     * @return the multiplication.
     */
    public BigMatrix mt(BigMatrix B) {
        if (n != B.n) {
            throw new IllegalArgumentException(String.format("Matrix multiplication A * B': %d x %d vs %d x %d", m, n, B.m, B.n));
        }

        BigMatrix C = new BigMatrix(m, B.m);
        C.mm(NO_TRANSPOSE, this, TRANSPOSE, B);
        return C;
    }

    /**
     * Returns matrix multiplication {@code A' * B}.
     * @param B the operand.
     * @return the multiplication.
     */
    public BigMatrix tm(BigMatrix B) {
        if (m != B.m) {
            throw new IllegalArgumentException(String.format("Matrix multiplication A' * B: %d x %d vs %d x %d", m, n, B.m, B.n));
        }

        BigMatrix C = new BigMatrix(n, B.n);
        C.mm(TRANSPOSE, this, NO_TRANSPOSE, B);
        return C;
    }

    /**
     * Returns matrix multiplication {@code A' * B'}.
     * @param B the operand.
     * @return the multiplication.
     */
    public BigMatrix tt(BigMatrix B) {
        if (m != B.n) {
            throw new IllegalArgumentException(String.format("Matrix multiplication A' * B': %d x %d vs %d x %d", m, n, B.m, B.n));
        }

        BigMatrix C = new BigMatrix(n, B.m);
        C.mm(TRANSPOSE, this, TRANSPOSE, B);
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
        BigMatrix lu = overwrite ? this : clone();
        IntPointer ipiv = new IntPointer(Math.min(m, n));
        int info = LAPACK.engine.getrf(lu.layout(), lu.m, lu.n, lu.A, lu.ld, ipiv);
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

        BigMatrix lu = overwrite ? this : clone();
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
        BigMatrix qr = overwrite ? this : clone();
        DoublePointer tau = new DoublePointer(Math.min(m, n));
        int info = LAPACK.engine.geqrf(qr.layout(), qr.m, qr.n, qr.A, qr.ld, tau);
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
        DoublePointer s = new DoublePointer(k);

        BigMatrix W = overwrite ? this : clone();
        if (vectors) {
            BigMatrix U = new BigMatrix(m, k);
            BigMatrix VT = new BigMatrix(k, n);

            int info = LAPACK.engine.gesdd(W.layout(), SVDJob.COMPACT, W.m, W.n, W.A, W.ld, s, U.A, U.ld, VT.A, VT.ld);
            if (info != 0) {
                logger.error("LAPACK GESDD error code: {}", info);
                throw new ArithmeticException("LAPACK GESDD error code: " + info);
            }

            return new SVD(s, U, VT.transpose());
        } else {
            BigMatrix U = new BigMatrix(1, 1);
            BigMatrix VT = new BigMatrix(1, 1);

            int info = LAPACK.engine.gesdd(W.layout(), SVDJob.NO_VECTORS, W.m, W.n, W.A, W.ld, s, U.A, U.ld, VT.A, VT.ld);
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
     * @return eign value decomposition.
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

        BigMatrix eig = overwrite ? this : clone();
        if (isSymmetric()) {
            DoublePointer w = new DoublePointer(n);
            int info = LAPACK.engine.syevd(eig.layout(), vr ? EVDJob.VECTORS : EVDJob.NO_VECTORS, eig.uplo, n, eig.A, eig.ld, w);
            if (info != 0) {
                logger.error("LAPACK SYEV error code: {}", info);
                throw new ArithmeticException("LAPACK SYEV error code: " + info);
            }
            return new EVD(w, vr ? eig : null);
        } else {
            DoublePointer wr = new DoublePointer(n);
            DoublePointer wi = new DoublePointer(n);
            BigMatrix Vl = vl ? new BigMatrix(n, n) : new BigMatrix(1, 1);
            BigMatrix Vr = vr ? new BigMatrix(n, n) : new BigMatrix(1, 1);
            int info = LAPACK.engine.geev(eig.layout(), vl ? EVDJob.VECTORS : EVDJob.NO_VECTORS, vr ? EVDJob.VECTORS : EVDJob.NO_VECTORS, n, eig.A, eig.ld, wr, wi, Vl.A, Vl.ld, Vr.A, Vr.ld);
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
        public final DoublePointer s;
        /**
         * The left singular vectors U.
         */
        public final BigMatrix U;
        /**
         * The right singular vectors V.
         */
        public final BigMatrix V;

        /**
         * Constructor.
         * @param m the number of rows of matrix.
         * @param n the number of columns of matrix.
         * @param s the singular values in descending order.
         */
        public SVD(int m, int n, DoublePointer s) {
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
        public SVD(DoublePointer s, BigMatrix U, BigMatrix V) {
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
        public BigMatrix diag() {
            BigMatrix S = new BigMatrix(U.m, V.m);

            long length = length(s);
            for (int i = 0; i < length; i++) {
                S.set(i, i, s.get(i));
            }

            return S;
        }

        /**
         * Returns the L<sub>2</sub> matrix norm that is the largest singular value.
         * @return L<sub>2</sub> matrix norm.
         */
        public double norm() {
            return s.get(0);
        }

        /**
         * Returns the threshold to determine the effective rank.
         * Singular values S(i) <= RCOND are treated as zero.
         * @return the threshold to determine the effective rank.
         */
        private double rcond() {
            return 0.5 * Math.sqrt(m + n + 1) * s.get(0) * MathEx.EPSILON;
        }

        /**
         * Returns the effective numerical matrix rank. The number of non-negligible
         * singular values.
         * @return the effective numerical matrix rank.
         */
        public int rank() {
            if (length(s) != Math.min(m, n)) {
                throw new UnsupportedOperationException("The operation cannot be called on a partial SVD.");
            }

            int r = 0;
            double tol = rcond();

            long length = length(s);
            for (int i = 0; i < length; i++) {
                double si = s.get(i);
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
        public double condition() {
            int k = Math.min(m, n);
            if (length(s) != k) {
                throw new UnsupportedOperationException("The operation cannot be called on a partial SVD.");
            }

            return (s.get(0) <= 0.0 || s.get(k-1) <= 0.0) ? Double.POSITIVE_INFINITY : s.get(0) / s.get(k-1);
        }

        /**
         * Returns the matrix which columns are the orthonormal basis for the range space.
         * Returns null if the rank is zero (if and only if zero matrix).
         * @return the range space span matrix.
         */
        public BigMatrix range() {
            if (length(s) != Math.min(m, n)) {
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

            BigMatrix R = new BigMatrix(m, r);
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
        public BigMatrix nullspace() {
            if (length(s) != Math.min(m, n)) {
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

            BigMatrix N = new BigMatrix(n, nr);
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
        public BigMatrix pinv() {
            int k = (int) length(s);
            double[] sigma = new double[k];
            int r = rank();
            for (int i = 0; i < r; i++) {
                sigma[i] = 1.0 / s.get(i);
            }

            return adb(NO_TRANSPOSE, V, sigma, TRANSPOSE, U);
        }

        /**
         * Solves the least squares min || B - A*X ||.
         * @param b the right hand side of overdetermined linear system.
         * @throws RuntimeException when matrix is rank deficient.
         * @return the solution vector beta that minimizes ||Y - X*beta||.
         */
        public double[] solve(double[] b) {
            if (U == null || V == null) {
                throw new IllegalStateException("The singular vectors are not available.");
            }

            if (b.length != m) {
                throw new IllegalArgumentException(String.format("Row dimensions do not agree: A is %d x %d, but B is %d x 1", m, n, b.length));
            }

            int r = rank();
            double[] Utb = new double[(int) length(s)];
            U.submatrix(0, 0, m-1, r-1).tv(b, Utb);
            for (int i = 0; i < r; i++) {
                Utb[i] /= s.get(i);
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
     * a square matrix each of whose rows consists of non-negative real numbers,
     * with each row summing to 1. A left stochastic matrix is a square matrix
     * whose columns consist of non-negative real numbers whose sum is 1. A doubly
     * stochastic matrix where all entries are non-negative and all rows and all
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
        public final DoublePointer wr;
        /**
         * The imaginary part of eigenvalues.
         */
        public final DoublePointer wi;
        /**
         * The left eigenvectors.
         */
        public final BigMatrix Vl;
        /**
         * The right eigenvectors.
         */
        public final BigMatrix Vr;

        /**
         * Constructor.
         *
         * @param w eigenvalues.
         * @param V eigenvectors.
         */
        public EVD(DoublePointer w, BigMatrix V) {
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
        public EVD(DoublePointer wr, DoublePointer wi, BigMatrix Vl, BigMatrix Vr) {
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
        public BigMatrix diag() {
            BigMatrix D = BigMatrix.diag(wr);

            if (wi != null) {
                int n = (int) length(wr);
                for (int i = 0; i < n; i++) {
                    if (wi.get(i) > 0) {
                        D.set(i, i + 1, wi.get(i));
                    } else if (wi.get(i) < 0) {
                        D.set(i, i - 1, wi.get(i));
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
            int n = (int) length(wr);
            double[] w = new double[n];
            if (wi != null) {
                for (int i = 0; i < n; i++) {
                    w[i] = -(wr.get(i) * wr.get(i) + wi.get(i) * wi.get(i));
                }
            } else {
                for (int i = 0; i < n; i++) {
                    w[i] = -(wr.get(i) * wr.get(i));
                }
            }

            int[] index = QuickSort.sort(w);
            DoublePointer wr2 = new DoublePointer(n);
            for (int j = 0; j < n; j++) {
                wr2.put(j, wr.get(index[j]));
            }

            DoublePointer wi2 = null;
            if (wi != null) {
                wi2 = new DoublePointer(n);
                for (int j = 0; j < n; j++) {
                    wi2.put(j, wi.get(index[j]));
                }
            }

            BigMatrix Vl2 = null;
            if (Vl != null) {
                int m = Vl.m;
                Vl2 = new BigMatrix(m, n);
                for (int j = 0; j < n; j++) {
                    for (int i = 0; i < m; i++) {
                        Vl2.set(i, j, Vl.get(i, index[j]));
                    }
                }
            }

            BigMatrix Vr2 = null;
            if (Vr != null) {
                int m = Vr.m;
                Vr2 = new BigMatrix(m, n);
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
        public final BigMatrix lu;

        /**
         * The pivot vector.
         */
        public final IntPointer ipiv;

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
        public LU(BigMatrix lu, IntPointer ipiv, int info) {
            this.lu = lu;
            this.ipiv = ipiv;
            this.info = info;
        }

        /**
         * Returns true if the matrix is singular.
         * @return true if the matrix is singular.
         */
        public boolean isSingular() {
            return info > 0;
        }

        /**
         * Returns the matrix determinant.
         * @return the matrix determinant.
         */
        public double det() {
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
                if (j+1 != ipiv.get(j)) {
                    d = -d;
                }
            }

            return d;
        }

        /**
         * Returns the inverse of matrix. For pseudo inverse, use QRDecomposition.
         * @return the inverse of matrix.
         */
        public BigMatrix inverse() {
            BigMatrix inv = BigMatrix.eye(lu.n);
            solve(inv);
            return inv;
        }

        /**
         * Solve A * x = b.
         * @param b the right hand side of linear system.
         * @throws RuntimeException when the matrix is singular.
         * @return the solution vector.
         */
        public double[] solve(double[] b) {
            BigMatrix x = BigMatrix.column(b);
            solve(x);

            double[] y = new double[b.length];
            x.A.get(y);
            return y;
        }

        /**
         * Solve A * X = B. B will be overwritten with the solution matrix on output.
         * @param B the right hand side of linear system.
         *          On output, B will be overwritten with the solution matrix.
         * @throws RuntimeException when the matrix is singular.
         */
        public void solve(BigMatrix B) {
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

            int ret = LAPACK.engine.getrs(lu.layout(), NO_TRANSPOSE, lu.n, B.n, lu.A, lu.ld, ipiv, B.A, B.ld);
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
        public final BigMatrix lu;

        /**
         * Constructor.
         * @param lu the lower/upper triangular part of matrix contains the Cholesky
         *           factorization.
         */
        public Cholesky(BigMatrix lu) {
            if (lu.nrow() != lu.ncol()) {
                throw new UnsupportedOperationException("Cholesky constructor on a non-square matrix");
            }
            this.lu = lu;
        }

        /**
         * Returns the matrix determinant.
         * @return the matrix determinant.
         */
        public double det() {
            int n = lu.n;
            double d = 1.0;
            for (int i = 0; i < n; i++) {
                d *= lu.get(i, i);
            }

            return d * d;
        }

        /**
         * Returns the log of matrix determinant.
         * @return the log of matrix determinant.
         */
        public double logdet() {
            int n = lu.n;
            double d = 0.0;
            for (int i = 0; i < n; i++) {
                d += Math.log(lu.get(i, i));
            }

            return 2.0 * d;
        }

        /**
         * Returns the inverse of matrix.
         * @return the inverse of matrix.
         */
        public BigMatrix inverse() {
            BigMatrix inv = BigMatrix.eye(lu.n);
            solve(inv);
            return inv;
        }

        /**
         * Solves the linear system A * x = b.
         * @param b the right hand side of linear systems.
         * @return the solution vector.
         */
        public double[] solve(double[] b) {
            BigMatrix x = BigMatrix.column(b);
            solve(x);

            double[] y = new double[b.length];
            x.A.get(y);
            return y;
        }

        /**
         * Solves the linear system A * X = B.
         * @param B the right hand side of linear systems. On output, B will
         *          be overwritten with the solution matrix.
         */
        public void solve(BigMatrix B) {
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
        public final BigMatrix qr;
        /**
         * The scalar factors of the elementary reflectors
         */
        public final DoublePointer tau;

        /**
         * Constructor.
         * @param qr the QR decomposition.
         * @param tau the scalar factors of the elementary reflectors
         */
        public QR(BigMatrix qr, DoublePointer tau) {
            this.qr = qr;
            this.tau = tau;
        }

        /**
         * Returns the Cholesky decomposition of A'A.
         * @return the Cholesky decomposition of A'A.
         */
        public Cholesky CholeskyOfAtA() {
            int n = qr.n;
            BigMatrix L = new BigMatrix(n, n);
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
        public BigMatrix R() {
            int n = qr.n;
            BigMatrix R = BigMatrix.diag(tau);
            for (int i = 0; i < n; i++) {
                for (int j = i; j < n; j++) {
                    R.set(i, j, qr.get(i, j));
                }
            }

            return R;
        }

        /**
         * Returns the orthogonal factor.
         * @return the orthogonal factor.
         */
        public BigMatrix Q() {
            int m = qr.m;
            int n = qr.n;
            int k = Math.min(m, n);
            BigMatrix Q = qr.clone();
            int info = LAPACK.engine.orgqr(qr.layout(), m, n, k, Q.A, qr.ld, tau);
            if (info != 0) {
                logger.error("LAPACK ORGRQ error code: {}", info);
                throw new ArithmeticException("LAPACK ORGRQ error code: " + info);
            }
            return Q;
        }

        /**
         * Solves the least squares min || B - A*X ||.
         * @param b the right hand side of overdetermined linear system.
         * @throws RuntimeException when the matrix is rank deficient.
         * @return the solution vector beta that minimizes ||Y - X*beta||.
         */
        public double[] solve(double[] b) {
            if (b.length != qr.m) {
                throw new IllegalArgumentException(String.format("Row dimensions do not agree: A is %d x %d, but B is %d x 1", qr.m, qr.n, b.length));
            }

            BigMatrix x = BigMatrix.column(b);
            solve(x);

            double[] y = new double[qr.n];
            x.A.get(y);
            return y;
        }

        /**
         * Solves the least squares min || B - A*X ||.
         * @param B the right hand side of overdetermined linear system.
         *          B will be overwritten with the solution matrix on output.
         * @throws RuntimeException when the matrix is rank deficient.
         */
        public void solve(BigMatrix B) {
            if (B.m != qr.m) {
                throw new IllegalArgumentException(String.format("Row dimensions do not agree: A is %d x %d, but B is %d x %d", qr.nrow(), qr.nrow(), B.nrow(), B.ncol()));
            }

            int m = qr.m;
            int n = qr.n;
            int k = Math.min(m, n);

            int info = LAPACK.engine.ormqr(qr.layout(), LEFT, TRANSPOSE, B.nrow(), B.ncol(), k, qr.A, qr.ld, tau, B.A, B.ld);
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
