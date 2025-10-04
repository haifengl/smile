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

import java.lang.foreign.MemorySegment;
import smile.linalg.*;
import smile.math.MathEx;
import smile.stat.distribution.Distribution;
import smile.stat.distribution.GaussianDistribution;
import static smile.linalg.Order.*;
import static smile.linalg.Side.*;
import static smile.linalg.Transpose.*;
import static smile.linalg.UPLO.*;
import static smile.linalg.blas.cblas_h.*;
import static smile.linalg.lapack.clapack_h.*;
import static smile.tensor.ScalarType.*;

/**
 * A dense matrix is a matrix where a large proportion of its elements
 * are non-zero. This class provides a skeletal implementation of the
 * Matrix interface.
 *
 * @author Haifeng Li
 */
public abstract class DenseMatrix implements Matrix {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(DenseMatrix.class);

    /**
     * The memory segment that stores matrix values.
     */
    transient MemorySegment memory;
    /**
     * The leading dimension.
     */
    final int ld;
    /**
     * The number of rows.
     */
    final int m;
    /**
     * The number of columns.
     */
    final int n;
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
     * Constructor.
     * @param memory the memory segment of data.
     * @param m the number of rows.
     * @param n the number of columns.
     * @param ld the leading dimension.
     * @param uplo if not null, the matrix is symmetric or triangular.
     * @param diag if not null, this flag specifies if a triangular
     *             matrix has unit diagonal elements.
     */
    DenseMatrix(MemorySegment memory, int m, int n, int ld, UPLO uplo, Diag diag) {
        if (m <= 0 || n <= 0) {
            throw new IllegalArgumentException(String.format("Invalid matrix size: %d x %d", m, n));
        }

        if (order() == COL_MAJOR && ld < m) {
            throw new IllegalArgumentException(String.format("Invalid leading dimension for COL_MAJOR: %d < %d", ld, m));
        }

        if (order() == ROW_MAJOR && ld < n) {
            throw new IllegalArgumentException(String.format("Invalid leading dimension for ROW_MAJOR: %d < %d", ld, n));
        }

        this.memory = memory;
        this.m = m;
        this.n = n;
        this.ld = ld;
        this.uplo = uplo;
        this.diag = diag;
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
    static int ld(int n) {
        int elementSize = 4;
        if (n <= 256 / elementSize) return n;

        return (((n * elementSize + 511) / 512) * 512 + 64) / elementSize;
    }

    /**
     * Returns the linearized index of matrix element.
     * @param i the row index.
     * @param j the column index.
     * @return the linearized index.
     */
    int offset(int i , int j) {
        return j * ld + i;
    }

    /**
     * Returns the size of underlying storage/array.
     * @return the size of underlying storage/array.
     */
    int capacity() {
        return ld * n;
    }

    @Override
    public abstract DenseMatrix copy();

    @Override
    public DenseMatrix transpose() {
        DenseMatrix trans = zeros(n, m);
        for (int i = 0;  i < n; i++) {
            for (int j = 0; j < m; j++) {
                trans.set(i, j, get(j, i));
            }
        }
        return trans;
    }

    @Override
    public DenseMatrix scale(double alpha) {
        int length = capacity();
        switch(scalarType()) {
            case Float64 -> cblas_dscal(length, alpha, memory, 1);
            case Float32 -> cblas_sscal(length, (float) alpha, memory, 1);
            default -> throw new UnsupportedOperationException("Unsupported scala type: " + scalarType());
        }
        return this;
    }

    @Override
    public String toString() {
        return toString(false);
    }

    @Override
    public int nrow() {
        return m;
    }

    @Override
    public int ncol() {
        return n;
    }

    /**
     * Returns the matrix layout.
     * @return the matrix layout.
     */
    public Order order() {
        return COL_MAJOR;
    }

    /**
     * Returns the memory storage of matrix.
     * @return the memory storage of matrix.
     */
    public MemorySegment memory() {
        return memory;
    }

    /**
     * Returns the leading dimension.
     * @return the leading dimension.
     */
    public int ld() {
        return ld;
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
     * @param uplo the format of packed matrix.
     * @return this matrix.
     */
    public DenseMatrix withUplo(UPLO uplo) {
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
    public DenseMatrix withDiag(Diag diag) {
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
    public Diag diag() {
        return diag;
    }

    /**
     * Assigns the specified value to each element of the specified vector.
     * @param value the value to be stored in all elements of the vector.
     */
    public abstract void fill(double value);

    /**
     * Returns the i-th row. Negative index -i means the i-th row from the end.
     * @param i the row index.
     * @return the row.
     */
    public Vector row(int i) {
        Vector x = vector(n);
        if (i < 0) i = m + i;

        for (int j = 0; j < n; j++) {
            x.set(j, get(i, j));
        }

        return x;
    }

    /**
     * Returns the j-th column. Negative index -j means the j-th row from the end.
     * @param j the column index.
     * @return the column.
     */
    public abstract Vector column(int j);

    /**
     * Returns the matrix of selected rows. Negative index -i means the i-th row from the end.
     * @param rows the row indices.
     * @return the submatrix.
     */
    public DenseMatrix rows(int... rows) {
        DenseMatrix x = zeros(rows.length, n);

        for (int i = 0; i < rows.length; i++) {
            int row = rows[i];
            if (row < 0) row = m + row;
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
    public DenseMatrix columns(int... cols) {
        DenseMatrix x = zeros(m, cols.length);

        for (int j = 0; j < cols.length; j++) {
            int col = cols[j];
            if (col < 0) col = n + col;
            for (int i = 0; i < m; i++) {
                x.set(i, j, get(i, col));
            }
        }

        return x;
    }

    /**
     * Returns the submatrix of selected rows.
     * @param from the beginning row, inclusive.
     * @param to the ending row, exclusive.
     * @return the submatrix.
     */
    public DenseMatrix rows(int from, int to) {
        if (to <= from) {
            throw new IllegalArgumentException("Invalid row range [" + from + ", " + to + ")");
        }

        int k = to - from;
        DenseMatrix x = zeros(k, n);
        for (int i = 0; i < k; i++) {
            for (int j = 0; j < n; j++) {
                x.set(i, j, get(from+i, j));
            }
        }

        return x;
    }

    /**
     * Returns the submatrix of selected columns.
     * @param from the beginning column, inclusive.
     * @param to the ending column, exclusive.
     * @return the submatrix.
     */
    public DenseMatrix columns(int from, int to) {
        if (to <= from) {
            throw new IllegalArgumentException("Invalid row range [" + from + ", " + to + ")");
        }

        int k = to - from;
        DenseMatrix x = zeros(m, k);
        for (int j = 0; j < k; j++) {
            for (int i = 0; i < m; i++) {
                x.set(i, j, get(i, from+j));
            }
        }

        return x;
    }

    /**
     * Returns the submatrix which top left at (i, j) and bottom right at (k, l).
     *
     * @param i the beginning row, inclusive.
     * @param j the beginning column, inclusive,
     * @param k the ending row, exclusive.
     * @param l the ending column, exclusive.
     * @return the submatrix.
     */
    public DenseMatrix submatrix(int i, int j, int k, int l) {
        throw new UnsupportedOperationException();
    }

    /**
     * Returns the mean of each column.
     * @return the mean of each column.
     */
    public Vector colMeans() {
        Vector means = vector(n);

        for (int j = 0; j < n; j++) {
            for (int i = 0; i < m; i++) {
                means.add(j, get(i, j));
            }
        }
        for (int j = 0; j < n; j++) {
            means.div(j, m);
        }

        return means;
    }

    /**
     * Returns the mean of each row.
     * @return the mean of each row.
     */
    public Vector rowMeans() {
        Vector means = vector(m);

        for (int j = 0; j < n; j++) {
            for (int i = 0; i < m; i++) {
                means.add(i, get(i, j));
            }
        }
        for (int i = 0; i < m; i++) {
            means.div(i, n);
        }

        return means;
    }

    /**
     * Computes a constant alpha times a matrix x plus this matrix y.
     * The result overwrites the initial values of this matrix y.
     *
     * @param alpha If {@code alpha = 0} this routine returns without any computation.
     *
     * @param x Input matrix.
     */
    public void axpy(double alpha, DenseMatrix x) {
        if (nrow() != x.nrow() || ncol() != x.ncol()) {
            throw new IllegalArgumentException(String.format("Adds matrix: %d x %d vs %d x %d", m, n, x.nrow(), x.ncol()));
        }

        if (scalarType() == x.scalarType() && ld == x.ld) {
            int length = capacity();
            switch (scalarType()) {
                case Float64 -> cblas_daxpy(length, alpha, x.memory, 1, memory, 1);
                case Float32 -> cblas_saxpy(length, (float) alpha, x.memory, 1, memory, 1);
                default -> throw new UnsupportedOperationException("Unsupported scala type: " + scalarType());
            }
        } else {
            for (int j = 0; j < n; j++) {
                for (int i = 0; i < m; i++) {
                    set(i, j, alpha * get(i, j) + alpha * x.get(i, j));
                }
            }
        }
    }

    /**
     * Sets this matrix as the sum of two matrices
     * <pre>{@code
     *     C = alpha * A + beta * B
     * }</pre>
     *
     * @param alpha the scalar alpha.
     * @param A the input matrix.
     * @param beta the scalar beta.
     * @param B the input matrix.
     */
    public void add(double alpha, DenseMatrix A, double beta, DenseMatrix B) {
        if (nrow() != A.nrow() || ncol() != A.ncol()) {
            throw new IllegalArgumentException(String.format("Adds matrix: %d x %d vs %d x %d", m, n, A.nrow(), A.ncol()));
        }
        if (nrow() != B.nrow() || ncol() != B.ncol()) {
            throw new IllegalArgumentException(String.format("Adds matrix: %d x %d vs %d x %d", m, n, B.nrow(), B.ncol()));
        }

        for (int j = 0; j < n; j++) {
            for (int i = 0; i < m; i++) {
                set(i, j, alpha * A.get(i, j) + beta * B.get(i, j));
            }
        }
    }

    /**
     * Adds two matrices
     * <pre>{@code
     *     A += B
     * }</pre>
     *
     * @param B the input matrix.
     */
    public void add(DenseMatrix B) {
        axpy(1.0, B);
    }

    /**
     * Subtracts two matrices
     * <pre>{@code
     *     A -= B
     * }</pre>
     *
     * @param B the input matrix.
     */
    public void sub(DenseMatrix B) {
        axpy(-1.0, B);
    }

    @Override
    public void mv(Transpose trans, double alpha, Vector x, double beta, Vector y) {
        if (scalarType() != x.scalarType()) {
            throw new IllegalArgumentException("Incompatible ScalarType: " + scalarType() + " != " + x.scalarType());
        }
        if (scalarType() != y.scalarType()) {
            throw new IllegalArgumentException("Incompatible ScalarType: " + scalarType() + " != " + y.scalarType());
        }
        switch (trans) {
            case NO_TRANSPOSE:
                if (ncol() != x.size()) {
                    throw new IllegalArgumentException("Incompatible x vector size: " + ncol() + " != " + x.size());
                }
                if (nrow() != y.size()) {
                    throw new IllegalArgumentException("Incompatible y vector size: " + nrow() + " != " + y.size());
                }
                break;
            case TRANSPOSE:
                if (nrow() != x.size()) {
                    throw new IllegalArgumentException("Incompatible x vector size: " + nrow() + " != " + x.size());
                }
                if (ncol() != y.size()) {
                    throw new IllegalArgumentException("Incompatible y vector size: " + ncol() + " != " + y.size());
                }
                break;
        }

        switch (scalarType()) {
            case Float64:
                if (uplo != null) {
                    if (diag != null) {
                        if (alpha == 1.0 && beta == 0.0 && x == y) {
                            cblas_dtrmv(order().blas(), uplo.blas(), trans.blas(), diag.blas(), m,
                                    memory, ld, y.memory(), 1);
                        } else {
                            cblas_dgemv(order().blas(), trans.blas(), m, n, alpha, memory,
                                    ld, x.memory(), 1, beta, y.memory(), 1);
                        }
                    } else {
                        cblas_dsymv(order().blas(), uplo.blas(), m, alpha, memory,
                                ld, x.memory(), 1, beta, y.memory(), 1);
                    }
                } else {
                    cblas_dgemv(order().blas(), trans.blas(), m, n, alpha, memory,
                            ld, x.memory(), 1, beta, y.memory(), 1);
                }
                break;

            case Float32:
                if (uplo != null) {
                    if (diag != null) {
                        if (alpha == 1.0 && beta == 0.0 && x == y) {
                            cblas_strmv(order().blas(), uplo.blas(), trans.blas(), diag.blas(), m,
                                    memory, ld, y.memory(), 1);
                        } else {
                            cblas_sgemv(order().blas(), trans.blas(), m, n, (float) alpha, memory,
                                    ld, x.memory(), 1, (float) beta, y.memory(), 1);
                        }
                    } else {
                        cblas_ssymv(order().blas(), uplo.blas(), m, (float) alpha, memory,
                                ld, x.memory(), 1, (float) beta, y.memory(), 1);
                    }
                } else {
                    cblas_sgemv(order().blas(), trans.blas(), m, n, (float) alpha, memory,
                            ld, x.memory(), 1, (float) beta, y.memory(), 1);
                }
                break;

            default:
                throw new UnsupportedOperationException("Unsupported ScalarType: " + scalarType());
        }
    }

    /**
     * Matrix-matrix multiplication.
     * <pre>{@code
     *     C := alpha*A*B + beta*C
     * }</pre>
     * @param alpha the scalar alpha.
     * @param transA normal, transpose, or conjugate transpose
     *               operation on the matrix A.
     * @param A the operand.
     * @param transB normal, transpose, or conjugate transpose
     *               operation on the matrix B.
     * @param B the operand.
     * @param beta the scalar beta.
     * @param C the operand.
     */
    public static void mm(double alpha, Transpose transA, DenseMatrix A, Transpose transB, DenseMatrix B, double beta, DenseMatrix C) {
        if (C.scalarType() != A.scalarType()) {
            throw new IllegalArgumentException("Incompatible ScalarType: " + C.scalarType() + " != " + A.scalarType());
        }
        if (C.scalarType() != B.scalarType()) {
            throw new IllegalArgumentException("Incompatible ScalarType: " + C.scalarType() + " != " + A.scalarType());
        }

        int m = C.nrow();
        int n = C.ncol();
        if (A.isSymmetric() && transB == NO_TRANSPOSE && B.order() == C.order()) {
            switch (C.scalarType()) {
                case Float64:
                    cblas_dsymm(C.order().blas(), LEFT.blas(), A.uplo().blas(), m, n,
                        alpha, A.memory(), A.ld(), B.memory(), B.ld(),
                        beta, C.memory(), C.ld());
                break;
                case Float32:
                   cblas_ssymm(C.order().blas(), LEFT.blas(), A.uplo().blas(), m, n,
                        (float) alpha, A.memory(), A.ld(), B.memory(), B.ld(),
                        (float) beta, C.memory(), C.ld());
                   break;
                default:
                    throw new UnsupportedOperationException("Unsupported ScalarType: " + A.scalarType());
            }
        } else if (B.isSymmetric() && transA == NO_TRANSPOSE && A.order() == C.order()) {
            switch (C.scalarType()) {
                case Float64:
                    cblas_dsymm(C.order().blas(), RIGHT.blas(), B.uplo().blas(), m, n,
                        alpha, B.memory(), B.ld(), A.memory(), A.ld(),
                        beta, C.memory(), C.ld());
                    break;
                case Float32:
                    cblas_ssymm(C.order().blas(), RIGHT.blas(), B.uplo().blas(), m, n,
                        (float) alpha, B.memory(), B.ld(), A.memory(), A.ld(),
                        (float) beta, C.memory(), C.ld());
                    break;
                default:
                    throw new UnsupportedOperationException("Unsupported ScalarType: " + A.scalarType());
            }
        } else {
            if (C.order() != A.order()) {
                transA = Transpose.flip(transA);
                A = A.transpose();
            }
            if (C.order() != B.order()) {
                transB = Transpose.flip(transB);
                B = B.transpose();
            }
            int k = transA == NO_TRANSPOSE ? A.ncol() : A.nrow();

            switch (C.scalarType()) {
                case Float64:
                    cblas_dgemm(C.order().blas(), transA.blas(), transB.blas(), m, n, k,
                        alpha, A.memory(), A.ld(), B.memory(), B.ld(),
                        beta, C.memory(), C.ld());
                    break;
                case Float32:
                    cblas_sgemm(C.order().blas(), transA.blas(), transB.blas(), m, n, k,
                        (float) alpha, A.memory(), A.ld(), B.memory(), B.ld(),
                        (float) beta, C.memory(), C.ld());
                    break;
                default:
                    throw new UnsupportedOperationException("Unsupported ScalarType: " + A.scalarType());
            }
        }
    }

    /**
     * Matrix multiplication {@code A * B}.
     * @param B the operand.
     * @return the multiplication.
     */
    public DenseMatrix mm(DenseMatrix B) {
        if (ncol() != B.nrow()) {
            throw new IllegalArgumentException(String.format("Matrix multiplication A * B: %d x %d vs %d x %d", m, n, B.nrow(), B.ncol()));
        }

        DenseMatrix C = zeros(nrow(), B.ncol());
        mm(1.0, NO_TRANSPOSE, this, NO_TRANSPOSE, B, 0.0, C);
        return C;
    }

    /**
     * Matrix multiplication {@code A' * B}.
     * @param B the operand.
     * @return the multiplication.
     */
    public DenseMatrix tm(DenseMatrix B) {
        if (nrow() != B.nrow()) {
            throw new IllegalArgumentException(String.format("Matrix multiplication A' * B: %d x %d vs %d x %d", m, n, B.nrow(), B.ncol()));
        }

        DenseMatrix C = zeros(ncol(), B.ncol());
        mm(1.0, TRANSPOSE, this, NO_TRANSPOSE, B, 0.0, C);
        return C;
    }

    /**
     * Matrix multiplication {@code A * B'}.
     * @param B the operand.
     * @return the multiplication.
     */
    public DenseMatrix mt(DenseMatrix B) {
        if (ncol() != B.ncol()) {
            throw new IllegalArgumentException(String.format("Matrix multiplication A * B': %d x %d vs %d x %d", m, n, B.nrow(), B.ncol()));
        }

        DenseMatrix C = zeros(nrow(), B.nrow());
        mm(1.0, NO_TRANSPOSE, this, TRANSPOSE, B, 0.0, C);
        return C;
    }

    /**
     * Returns {@code A' * A}.
     * @return {@code A' * A}.
     */
    public DenseMatrix ata() {
        DenseMatrix C = zeros(ncol(), ncol()).withUplo(LOWER);
        mm(1.0, TRANSPOSE, this, NO_TRANSPOSE, this, 0.0, C);
        return C;
    }

    /**
     * Returns {@code A * A'}.
     * @return {@code A * A'}.
     */
    public DenseMatrix aat() {
        DenseMatrix C = zeros(nrow(), nrow()).withUplo(LOWER);
        mm(1.0, NO_TRANSPOSE, this, TRANSPOSE, this, 0.0, C);
        return C;
    }

    /**
     * Performs the rank-1 update operation.
     * <pre>{@code
     *     A := A + alpha*x*y'
     * }</pre>
     *
     * @param alpha the scalar alpha.
     * @param x the left vector.
     * @param y the right vector.
     */
    public void ger(double alpha, Vector x, Vector y) {
        switch(scalarType()) {
            case Float64 -> cblas_dger(order().blas(), m, n, alpha, x.memory, 1, y.memory, 1, memory, ld);
            case Float32 -> cblas_sger(order().blas(), m, n, (float) alpha, x.memory, 1, y.memory, 1, memory, ld);
            default -> throw new UnsupportedOperationException("Unsupported scala type: " + scalarType());
        }
    }

    /**
     * Returns the inverse of matrix.
     * @return the inverse of matrix.
     */
    public DenseMatrix inverse() {
        if (m != n) {
            throw new IllegalArgumentException(String.format("The matrix is not square: %d x %d", m, n));
        }

        DenseMatrix lu = copy();
        DenseMatrix inv = eye(lu.n);
        int[] n = { lu.n };
        int[] lda = { lu.ld };
        int[] ldb = { inv.ld };
        int[] ipiv = new int[lu.n];
        int[] info = { 0 };
        MemorySegment n_ = MemorySegment.ofArray(n);
        MemorySegment lda_ = MemorySegment.ofArray(lda);
        MemorySegment ldb_ = MemorySegment.ofArray(ldb);
        MemorySegment ipiv_ = MemorySegment.ofArray(ipiv);
        MemorySegment info_ = MemorySegment.ofArray(info);
        if (isSymmetric()) {
            Vector work = lu.vector(1);
            int[] lwork = { -1 };
            byte[] uplo = { lu.uplo.lapack() };
            MemorySegment lwork_ = MemorySegment.ofArray(lwork);
            MemorySegment uplo_ = MemorySegment.ofArray(uplo);

            // query workspace size
            switch(scalarType()) {
                case Float64 -> dsysv_(uplo_, n_, n_, lu.memory, lda_, ipiv_, inv.memory, ldb_, work.memory, lwork_, info_);
                case Float32 -> ssysv_(uplo_, n_, n_, lu.memory, lda_, ipiv_, inv.memory, ldb_, work.memory, lwork_, info_);
                default -> throw new UnsupportedOperationException("Unsupported scala type: " + scalarType());
            }
            if (info[0] != 0) {
                throw new ArithmeticException("SYSV fails: " + info[0]);
            }

            work = lu.vector((int) work.get(0));
            lwork[0] = work.size();
            switch(scalarType()) {
                case Float64 -> dsysv_(uplo_, n_, n_, lu.memory, lda_, ipiv_, inv.memory, ldb_, work.memory, lwork_, info_);
                case Float32 -> ssysv_(uplo_, n_, n_, lu.memory, lda_, ipiv_, inv.memory, ldb_, work.memory, lwork_, info_);
                default -> throw new UnsupportedOperationException("Unsupported scala type: " + scalarType());
            }
            if (info[0] != 0) {
                throw new ArithmeticException("SYSV fails: " + info[0]);
            }
        } else {
            switch(scalarType()) {
                case Float64 -> dgesv_(n_, n_, lu.memory, lda_, ipiv_, inv.memory, ldb_, info_);
                case Float32 -> sgesv_(n_, n_, lu.memory, lda_, ipiv_, inv.memory, ldb_, info_);
                default -> throw new UnsupportedOperationException("Unsupported scala type: " + scalarType());
            }
            if (info[0] != 0) {
                throw new ArithmeticException("GESV fails: " + info[0]);
            }
        }

        return inv;
    }

    /**
     * LU decomposition. The decomposition will overwrite this matrix.
     * Makes a copy first if you want to keep the matrix.
     * @return LU decomposition.
     */
    public LU lu() {
        DenseMatrix lu = this;
        int[] m = { lu.m };
        int[] n = { lu.n };
        int[] lda = { lu.ld };
        int[] ipiv = new int[Math.min(lu.m, lu.n)];
        int[] info = { 0 };
        MemorySegment m_ = MemorySegment.ofArray(m);
        MemorySegment n_ = MemorySegment.ofArray(n);
        MemorySegment lda_ = MemorySegment.ofArray(lda);
        MemorySegment ipiv_ = MemorySegment.ofArray(ipiv);
        MemorySegment info_ = MemorySegment.ofArray(info);
        switch(scalarType()) {
            case Float64 -> dgetrf_(m_, n_, lu.memory, lda_, ipiv_, info_);
            case Float32 -> sgetrf_(m_, n_, lu.memory, lda_, ipiv_, info_);
            default -> throw new UnsupportedOperationException("Unsupported scala type: " + scalarType());
        }

        if (info[0] < 0) {
            logger.error("LAPACK GETRF error code: {}", info);
            throw new ArithmeticException("LAPACK GETRF error code: " + info[0]);
        }

        lu.uplo = null; // LU is not symmetric
        return new LU(lu, ipiv, info[0]);
    }

    /**
     * Cholesky decomposition for symmetric and positive definite matrix.
     * The decomposition will overwrite this matrix. Makes a copy first
     * if you want to keep the matrix.
     *
     * @throws ArithmeticException if the matrix is not positive definite.
     * @return Cholesky decomposition.
     */
    public Cholesky cholesky() {
        if (uplo == null) {
            throw new IllegalArgumentException("The matrix is not symmetric");
        }

        DenseMatrix lu = this;
        byte[] uplo = { lu.uplo.lapack() };
        int[] n = { lu.n };
        int[] lda = { lu.ld };
        int[] info = { 0 };
        MemorySegment uplo_ = MemorySegment.ofArray(uplo);
        MemorySegment n_ = MemorySegment.ofArray(n);
        MemorySegment lda_ = MemorySegment.ofArray(lda);
        MemorySegment info_ = MemorySegment.ofArray(info);
        switch(scalarType()) {
            case Float64 -> dpotrf_(uplo_, n_, lu.memory, lda_, info_);
            case Float32 -> spotrf_(uplo_, n_, lu.memory, lda_, info_);
            default -> throw new UnsupportedOperationException("Unsupported scala type: " + scalarType());
        }

        if (info[0] != 0) {
            logger.error("LAPACK POTRF error code: {}", info[0]);
            throw new ArithmeticException("LAPACK POTRF error code: " + info[0]);
        }

        return new Cholesky(lu);
    }

    /**
     * QR Decomposition. The decomposition will overwrite this matrix.
     * Makes a copy first if you want to keep the matrix.
     *
     * @return QR decomposition.
     */
    public QR qr() {
        DenseMatrix qr = this;
        Vector tau = qr.vector(Math.min(m, n));
        Vector work = vector(1);
        int[] m = { qr.m };
        int[] n = { qr.n };
        int[] lda = { qr.ld };
        int[] lwork = { -1 };
        int[] info = { 0 };
        MemorySegment m_ = MemorySegment.ofArray(m);
        MemorySegment n_ = MemorySegment.ofArray(n);
        MemorySegment lda_ = MemorySegment.ofArray(lda);
        MemorySegment lwork_ = MemorySegment.ofArray(lwork);
        MemorySegment info_ = MemorySegment.ofArray(info);

        // query workspace size for GEQRF
        switch(scalarType()) {
            case Float64 -> dgeqrf_(m_, n_, qr.memory, lda_, tau.memory, work.memory, lwork_, info_);
            case Float32 -> sgeqrf_(m_, n_, qr.memory, lda_, tau.memory, work.memory, lwork_, info_);
            default -> throw new UnsupportedOperationException("Unsupported scala type: " + scalarType());
        }

        if (info[0] != 0) {
            logger.error("LAPACK GEQRF error code: {}", info[0]);
            throw new IllegalArgumentException("LAPACK GEQRF error code: " + info[0]);
        }

        // execute GEQRF
        work = qr.vector((int) work.get(0));
        lwork[0] = work.size();
        switch(scalarType()) {
            case Float64 -> dgeqrf_(m_, n_, qr.memory, lda_, tau.memory, work.memory, lwork_, info_);
            case Float32 -> sgeqrf_(m_, n_, qr.memory, lda_, tau.memory, work.memory, lwork_, info_);
            default -> throw new UnsupportedOperationException("Unsupported scala type: " + scalarType());
        }

        if (info[0] != 0) {
            logger.error("LAPACK GEQRF error code: {}", info[0]);
            throw new ArithmeticException("LAPACK GEQRF error code: " + info[0]);
        }

        qr.uplo = null; // QR is not symmetric
        return new QR(qr, tau);
    }

    /**
     * Singular Value Decomposition. The decomposition will overwrite this matrix.
     * Makes a copy first if you want to keep the matrix.
     * Returns a compact SVD of m-by-n matrix A:
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
     * @return singular value decomposition.
     */
    public SVD svd() {
        return svd(true);
    }

    /**
     * Singular Value Decomposition. The decomposition will overwrite this matrix.
     * Makes a copy first if you want to keep the matrix.
     * Returns a compact SVD of m-by-n matrix A:
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
     * @param vectors The flag whether computing the singular vectors.
     * @return singular value decomposition.
     */
    public SVD svd(boolean vectors) {
        int k = Math.min(m, n);
        Vector s = vector(k);
        DenseMatrix A = this;
        DenseMatrix U = vectors ? zeros(A.m, k) : zeros(1, 1);
        DenseMatrix Vt = vectors ? zeros(k, A.n) : zeros(1, 1);
        Vector work = vector(1);
        byte[] jobz = { vectors ? SVDJob.COMPACT.lapack() : SVDJob.NO_VECTORS.lapack() };
        int[] m = { A.m };
        int[] n = { A.n };
        int[] lda = { A.ld };
        int[] lwork = { -1 };
        int[] iwork = new int[8 * Math.min(A.m, A.n)];
        int[] ldu = { U.ld };
        int[] ldvt = { Vt.ld };
        int[] info = { 0 };
        MemorySegment m_ = MemorySegment.ofArray(m);
        MemorySegment n_ = MemorySegment.ofArray(n);
        MemorySegment lda_ = MemorySegment.ofArray(lda);
        MemorySegment jobz_ = MemorySegment.ofArray(jobz);
        MemorySegment info_ = MemorySegment.ofArray(info);
        MemorySegment ldu_ = MemorySegment.ofArray(ldu);
        MemorySegment ldvt_ = MemorySegment.ofArray(ldvt);
        MemorySegment lwork_ = MemorySegment.ofArray(lwork);
        MemorySegment iwork_ = MemorySegment.ofArray(iwork);

        switch(scalarType()) {
            case Float64 -> dgesdd_(jobz_, m_, n_, A.memory, lda_, s.memory, U.memory, ldu_, Vt.memory, ldvt_, work.memory, lwork_, iwork_, info_);
            case Float32 -> sgesdd_(jobz_, m_, n_, A.memory, lda_, s.memory, U.memory, ldu_, Vt.memory, ldvt_, work.memory, lwork_, iwork_, info_);
            default -> throw new UnsupportedOperationException("Unsupported scala type: " + scalarType());
        }

        if (info[0] != 0) {
            logger.error("LAPACK GESDD with error code: {}", info[0]);
            throw new ArithmeticException("LAPACK GESDD with COMPACT error code: " + info[0]);
        }

        work = vector((int) work.get(0));
        lwork[0] = work.size();
        switch(scalarType()) {
            case Float64 -> dgesdd_(jobz_, m_, n_, A.memory, lda_, s.memory, U.memory, ldu_, Vt.memory, ldvt_, work.memory, lwork_, iwork_, info_);
            case Float32 -> sgesdd_(jobz_, m_, n_, A.memory, lda_, s.memory, U.memory, ldu_, Vt.memory, ldvt_, work.memory, lwork_, iwork_, info_);
            default -> throw new UnsupportedOperationException("Unsupported scala type: " + scalarType());
        }

        if (info[0] != 0) {
            logger.error("LAPACK GESDD with error code: {}", info[0]);
            throw new ArithmeticException("LAPACK GESDD with COMPACT error code: " + info[0]);
        }

        return vectors ? new SVD(s, U, Vt) : new SVD(A.m, A.n, s);
    }

    /**
     * Right Eigenvalue Decomposition. The decomposition will overwrite this matrix.
     * Makes a copy first if you want to keep the matrix.
     * For a symmetric matrix, all eigenvalues are
     * real values. Otherwise, the eigenvalues may be complex numbers.
     * <p>
     * By default <code>eigen</code> does not always return the eigenvalues
     * and eigenvectors in sorted order. Use the <code>EVD.sort</code> function
     * to put the eigenvalues in descending order and reorder the corresponding
     * eigenvectors.
     * @return eign value decomposition.
     */
    public EVD eigen() {
        return eigen(false, true);
    }

    /**
     * Eigenvalue Decomposition. The decomposition will overwrite this matrix.
     * Makes a copy first if you want to keep the matrix.
     * For a symmetric matrix, all eigenvalues are
     * real values. Otherwise, the eigenvalues may be complex numbers.
     * <p>
     * By default <code>eigen</code> does not always return the eigenvalues
     * and eigenvectors in sorted order. Use the <code>sort</code> function
     * to put the eigenvalues in descending order and reorder the corresponding
     * eigenvectors.
     *
     * @param vl The flag if computing the left eigenvectors.
     * @param vr The flag if computing the right eigenvectors.
     * @return eigen value decomposition.
     */
    public EVD eigen(boolean vl, boolean vr) {
        if (m != n) {
            throw new IllegalArgumentException(String.format("The matrix is not square: %d x %d", m, n));
        }

        DenseMatrix eig = this;
        byte[] vectors = { EVDJob.VECTORS.lapack() };
        byte[] no_vectors = { EVDJob.NO_VECTORS.lapack() };
        int[] n = { eig.n };
        int[] lda = { eig.ld };
        int[] info = { 0 };
        MemorySegment vectors_ = MemorySegment.ofArray(vectors);
        MemorySegment no_vectors_ = MemorySegment.ofArray(no_vectors);
        MemorySegment n_ = MemorySegment.ofArray(n);
        MemorySegment lda_ = MemorySegment.ofArray(lda);
        MemorySegment info_ = MemorySegment.ofArray(info);
        if (isSymmetric()) {
            Vector w = vector(eig.n);
            Vector work = vector(1);
            int[] lwork = { -1 };
            int[] iwork = new int[1];
            int[] liwork = { -1 };
            byte[] uplo = { eig.uplo.lapack() };
            MemorySegment iwork_ = MemorySegment.ofArray(iwork);
            MemorySegment lwork_ = MemorySegment.ofArray(lwork);
            MemorySegment liwork_ = MemorySegment.ofArray(liwork);
            MemorySegment uplo_ = MemorySegment.ofArray(uplo);
            // query workspace size
            switch(scalarType()) {
                case Float64 -> dsyevd_(vr ? vectors_ : no_vectors_, uplo_, n_, eig.memory, lda_, w.memory, work.memory, lwork_, iwork_, liwork_, info_);
                case Float32 -> ssyevd_(vr ? vectors_ : no_vectors_, uplo_, n_, eig.memory, lda_, w.memory, work.memory, lwork_, iwork_, liwork_, info_);
                default -> throw new UnsupportedOperationException("Unsupported scala type: " + scalarType());
            }

            if (info[0] != 0) {
                logger.error("LAPACK SYEV error code: {}", info[0]);
                throw new ArithmeticException("LAPACK SYEV error code: " + info[0]);
            }

            work = vector((int) work.get(0));
            iwork = new int[iwork[0]];
            lwork[0] = work.size();
            liwork[0] = iwork.length;
            switch(scalarType()) {
                case Float64 -> dsyevd_(vr ? vectors_ : no_vectors_, uplo_, n_, eig.memory, lda_, w.memory, work.memory, lwork_, iwork_, liwork_, info_);
                case Float32 -> ssyevd_(vr ? vectors_ : no_vectors_, uplo_, n_, eig.memory, lda_, w.memory, work.memory, lwork_, iwork_, liwork_, info_);
                default -> throw new UnsupportedOperationException("Unsupported scala type: " + scalarType());
            }

            if (info[0] != 0) {
                logger.error("LAPACK SYEV error code: {}", info[0]);
                throw new ArithmeticException("LAPACK SYEV error code: " + info[0]);
            }

            eig.uplo = null; // Vr is not symmetric
            return new EVD(w, vr ? eig : null);
        } else {
            Vector wr = vector(eig.n);
            Vector wi = vector(eig.n);
            DenseMatrix Vl = vl ? zeros(eig.n, eig.n) : zeros(1, 1);
            DenseMatrix Vr = vr ? zeros(eig.n, eig.n) : zeros(1, 1);
            Vector work = vector(1);
            int[] ldvl = { Vl.ld };
            int[] ldvr = { Vr.ld };
            int[] lwork = { -1 };
            MemorySegment ldvl_ = MemorySegment.ofArray(ldvl);
            MemorySegment ldvr_ = MemorySegment.ofArray(ldvr);
            MemorySegment lwork_ = MemorySegment.ofArray(lwork);

            // query workspace size
            switch(scalarType()) {
                case Float64 -> dgeev_(vl ? vectors_ : no_vectors_, vr ? vectors_ : no_vectors_,
                        n_, eig.memory, lda_, wr.memory, wi.memory, Vl.memory, ldvl_, Vr.memory, ldvr_,
                        work.memory, lwork_, info_);
                case Float32 -> sgeev_(vl ? vectors_ : no_vectors_, vr ? vectors_ : no_vectors_,
                        n_, eig.memory, lda_, wr.memory, wi.memory, Vl.memory, ldvl_, Vr.memory, ldvr_,
                        work.memory, lwork_, info_);
                default -> throw new UnsupportedOperationException("Unsupported scala type: " + scalarType());
            }

            if (info[0] != 0) {
                logger.error("LAPACK GEEV error code: {}", info[0]);
                throw new ArithmeticException("LAPACK GEEV error code: " + info[0]);
            }

            work = vector((int) work.get(0));
            lwork[0] = work.size();
            switch(scalarType()) {
                case Float64 -> dgeev_(vl ? vectors_ : no_vectors_, vr ? vectors_ : no_vectors_,
                        n_, eig.memory, lda_, wr.memory, wi.memory, Vl.memory, ldvl_, Vr.memory, ldvr_,
                        work.memory, lwork_, info_);
                case Float32 -> sgeev_(vl ? vectors_ : no_vectors_, vr ? vectors_ : no_vectors_,
                        n_, eig.memory, lda_, wr.memory, wi.memory, Vl.memory, ldvl_, Vr.memory, ldvr_,
                        work.memory, lwork_, info_);
                default -> throw new UnsupportedOperationException("Unsupported scala type: " + scalarType());
            }

            if (info[0] != 0) {
                logger.error("LAPACK GEEV error code: {}", info[0]);
                throw new ArithmeticException("LAPACK GEEV error code: " + info[0]);
            }

            return new EVD(wr, wi, vl ? Vl : null, vr ? Vr : null);
        }
    }

    /**
     * Returns a matrix from a two-dimensional array.
     * @param A the two-dimensional array.
     * @return the matrix.
     */
    public static DenseMatrix of(double[][] A) {
        int m = A.length;
        int n = A[0].length;
        DenseMatrix matrix = zeros(Float64, m, n);
        for (int i =  0; i < m; i++) {
            for (int j = 0; j < n; j++) {
                matrix.set(i, j, A[i][j]);
            }
        }
        return matrix;
    }

    /**
     * Returns a matrix from a two-dimensional array.
     * @param A the two-dimensional array.
     * @return the matrix.
     */
    public static DenseMatrix of(float[][] A) {
        int m = A.length;
        int n = A[0].length;
        DenseMatrix matrix = zeros(Float32, m, n);
        for (int i =  0; i < m; i++) {
            for (int j = 0; j < n; j++) {
                matrix.set(i, j, A[i][j]);
            }
        }
        return matrix;
    }

    /**
     * Returns a zero matrix.
     * @param scalarType the scalar type.
     * @param m the number of rows.
     * @param n the number of columns.
     * @return a zero matrix.
     */
    public static DenseMatrix zeros(ScalarType scalarType, int m, int n) {
        int ld = ld(m);
        return switch (scalarType) {
            case Float64 -> {
                double[] array = new double[ld * n];
                yield new DenseMatrix64(array, m, n, ld, null, null);
            }
            case Float32 -> {
                float[] array = new float[ld * n];
                yield new DenseMatrix32(array, m, n, ld, null, null);
            }
            default -> throw new UnsupportedOperationException("Unsupported ScalarType: " + scalarType);
        };
    }

    /**
     * Returns a zero matrix of the same scalar type as this matrix.
     * @param m the number of rows.
     * @param n the number of columns.
     * @return a zero matrix.
     */
    public DenseMatrix zeros(int m, int n) {
        return zeros(scalarType(), m, n);
    }

    /**
     * Returns an identity matrix.
     * @param scalarType the scalar type.
     * @param n the number of columns.
     * @return an identity matrix.
     */
    public static DenseMatrix eye(ScalarType scalarType, int n) {
        return eye(scalarType, n, n);
    }

    /**
     * Returns an identity matrix.
     * @param scalarType the scalar type.
     * @param m the number of rows.
     * @param n the number of columns.
     * @return an identity matrix.
     */
    public static DenseMatrix eye(ScalarType scalarType, int m, int n) {
        int ld = ld(m);
        DenseMatrix matrix = zeros(scalarType, m, n);
        int k = Math.min(m, n);
        for (int i = 0; i < k; i++) {
            matrix.set(i, i, 1.0);
        }
        return matrix;
    }

    /**
     * Returns an identity matrix of the same scalar type as this matrix.
     * @param n the number of columns.
     * @return an identity matrix.
     */
    public DenseMatrix eye(int n) {
        return eye(n, n);
    }

    /**
     * Returns an identity matrix of the same scalar type as this matrix.
     * @param m the number of rows.
     * @param n the number of columns.
     * @return an identity matrix.
     */
    public DenseMatrix eye(int m, int n) {
        return eye(scalarType(), m, n);
    }

    /**
     * Returns the diagonal matrix with the elements of given array.
     * @param diag the diagonal elements.
     * @return the diagonal matrix.
     */
    public static DenseMatrix diagflat(double[] diag) {
        int n = diag.length;
        DenseMatrix matrix = DenseMatrix.zeros(Float64, n, n);
        for (int i = 0; i < n; i++) {
            matrix.set(i, i, diag[i]);
        }
        return matrix;
    }

    /**
     * Returns the diagonal matrix with the elements of given array.
     * @param diag the diagonal elements.
     * @return the diagonal matrix.
     */
    public static DenseMatrix diagflat(float[] diag) {
        int n = diag.length;
        DenseMatrix matrix = DenseMatrix.zeros(Float32, n, n);
        for (int i = 0; i < n; i++) {
            matrix.set(i, i, diag[i]);
        }
        return matrix;
    }

    /**
     * Returns a random matrix of standard normal distribution.
     * @param m the number of rows.
     * @param n the number of columns.
     * @return the random matrix.
     */
    public static DenseMatrix randn(ScalarType scalarType, int m, int n) {
        return rand(scalarType, m, n, GaussianDistribution.getInstance());
    }

    /**
     * Returns a random matrix with given distribution.
     * @param m the number of rows.
     * @param n the number of columns.
     * @param distribution the distribution of random numbers.
     * @return the random matrix.
     */
    public static DenseMatrix rand(ScalarType scalarType, int m, int n, Distribution distribution) {
        DenseMatrix matrix = zeros(scalarType, m, n);

        for (int j = 0; j < n; j++) {
            for (int i = 0; i < m; i++) {
                matrix.set(i, j, distribution.rand());
            }
        }

        return matrix;
    }

    /**
     * Returns a uniformly distributed random matrix in [0, 1).
     *
     * @param m the number of rows.
     * @param n the number of columns.
     * @return the random matrix.
     */
    public static DenseMatrix rand(ScalarType scalarType, int m, int n) {
        DenseMatrix matrix = zeros(scalarType, m, n);

        for (int j = 0; j < n; j++) {
            for (int i = 0; i < m; i++) {
                matrix.set(i, j, MathEx.random());
            }
        }

        return matrix;
    }

    /**
     * Returns a uniformly distributed random matrix in [lo, hi).
     *
     * @param m the number of rows.
     * @param n the number of columns.
     * @param lo the lower bound of uniform distribution.
     * @param hi the upper bound of uniform distribution.
     * @return the random matrix.
     */
    public static DenseMatrix rand(ScalarType scalarType, int m, int n, double lo, double hi) {
        DenseMatrix matrix = zeros(scalarType, m, n);

        for (int j = 0; j < n; j++) {
            for (int i = 0; i < m; i++) {
                matrix.set(i, j, MathEx.random(lo, hi));
            }
        }

        return matrix;
    }

    /**
     * Returns a symmetric Toeplitz matrix in which each descending diagonal
     * from left to right is constant.
     *
     * @param a A[i, j] = a[i - j] for {@code i >= j} (or a[j - i] when {@code j > i})
     * @return the Toeplitz matrix.
     */
    public static DenseMatrix toeplitz(double[] a) {
        int n = a.length;
        DenseMatrix toeplitz = DenseMatrix.zeros(Float64, n, n);
        toeplitz.withUplo(LOWER);

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
     * Returns a symmetric Toeplitz matrix in which each descending diagonal
     * from left to right is constant.
     *
     * @param a A[i, j] = a[i - j] for {@code i >= j} (or a[j - i] when {@code j > i})
     * @return the Toeplitz matrix.
     */
    public static DenseMatrix toeplitz(float[] a) {
        int n = a.length;
        DenseMatrix toeplitz = DenseMatrix.zeros(Float32, n, n);
        toeplitz.withUplo(LOWER);

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
     * @return the Toeplitz matrix.
     */
    public static DenseMatrix toeplitz(double[] kl, double[] ku) {
        if (kl.length != ku.length - 1) {
            throw new IllegalArgumentException(String.format("Invalid sub-diagonals and super-diagonals size: %d != %d - 1", kl.length, ku.length));
        }

        int n = kl.length;
        DenseMatrix toeplitz = DenseMatrix.zeros(Float64, n, n);

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
     * Returns a Toeplitz matrix in which each descending diagonal
     * from left to right is constant.
     *
     * @param kl {@code A[i, j] = kl[i - j]} for {@code i >  j}
     * @param ku {@code A[i, j] = ku[j - i]} for {@code i <= j}
     * @return the Toeplitz matrix.
     */
    public static DenseMatrix toeplitz(float[] kl, float[] ku) {
        if (kl.length != ku.length - 1) {
            throw new IllegalArgumentException(String.format("Invalid sub-diagonals and super-diagonals size: %d != %d - 1", kl.length, ku.length));
        }

        int n = kl.length;
        DenseMatrix toeplitz = DenseMatrix.zeros(Float32, n, n);

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
}
