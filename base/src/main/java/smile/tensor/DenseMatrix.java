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

import static smile.linalg.Layout.COL_MAJOR;
import static smile.linalg.Layout.ROW_MAJOR;
import static smile.linalg.Side.LEFT;
import static smile.linalg.Side.RIGHT;
import static smile.linalg.Transpose.NO_TRANSPOSE;
import static smile.linalg.Transpose.TRANSPOSE;
import static smile.linalg.UPLO.LOWER;
import static smile.linalg.blas.cblas_openblas_h.*;
import static smile.linalg.lapack.lapacke_h.*;

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

        if (layout() == COL_MAJOR && ld < m) {
            throw new IllegalArgumentException(String.format("Invalid leading dimension for COL_MAJOR: %d < %d", ld, m));
        }

        if (layout() == ROW_MAJOR && ld < n) {
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

    @Override
    public abstract DenseMatrix copy();

    @Override
    public abstract DenseMatrix transpose();

    @Override
    public DenseMatrix scale(double alpha) {
        switch(scalarType()) {
            case Float64 -> cblas_dscal((int) length(), alpha, memory, 1);
            case Float32 -> cblas_sscal((int) length(), (float) alpha, memory, 1);
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
    public Layout layout() {
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
    public Vector column(int j) {
        Vector x = vector(m);

        for (int i = 0; i < m; i++) {
            x.set(i, get(i, j));
        }

        return x;
    }

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
        if (i < 0 || i >= m || k <= i || k >= m || j < 0 || j >= n || l <= j || l >= n) {
            throw new IllegalArgumentException(String.format("Invalid submatrix range (%d:%d, %d:%d) of %d x %d", i, k, j, l, m, n));
        }

        int nrow = k - i;
        int ncol = l - j;
        DenseMatrix x = zeros(nrow, ncol);
        for (int q = 0; q < ncol; q++) {
            for (int p = 0; p < nrow; p++) {
                x.set(p, q, get(p+i, q+j));
            }
        }

        return x;
    }

    /**
     * Matrix-vector multiplication.
     * <pre>{@code
     *     y = alpha * op(A) * x + beta * y
     * }</pre>
     * where op is the transpose operation.
     *
     * @param trans normal, transpose, or conjugate transpose
     *              operation on the matrix.
     * @param alpha the scalar alpha.
     * @param x the input vector.
     * @param beta the scalar beta. When beta is supplied as zero,
     *             y need not be set on input.
     * @param y  the input and output vector.
     */
    public static void mv(Transpose trans, DenseMatrix A, double alpha, Vector x, double beta, Vector y) {
        if (A.scalarType() != x.scalarType()) {
            throw new IllegalArgumentException("Incompatible ScalarType: " + A.scalarType() + " != " + x.scalarType());
        }
        if (A.scalarType() != y.scalarType()) {
            throw new IllegalArgumentException("Incompatible ScalarType: " + A.scalarType() + " != " + y.scalarType());
        }

        var uplo = A.uplo();
        var diag = A.diag();
        int m = A.nrow();
        int n = A.ncol();
        int ld = A.ld();
        switch (A.scalarType()) {
            case Float64:
                if (uplo != null) {
                    if (diag != null) {
                        if (alpha == 1.0 && beta == 0.0 && x == y) {
                            cblas_dtrmv(A.layout().blas(), uplo.blas(), trans.blas(), diag.blas(), m,
                                    A.memory(), ld, y.memory(), 1);
                        } else {
                            cblas_dgemv(A.layout().blas(), trans.blas(), m, n, alpha, A.memory(),
                                    ld, x.memory(), 1, beta, y.memory(), 1);
                        }
                    } else {
                        cblas_dsymv(A.layout().blas(), trans.blas(), m, alpha, A.memory(),
                                ld, x.memory(), 1, beta, y.memory(), 1);
                    }
                } else {
                    cblas_dgemv(A.layout().blas(), trans.blas(), m, n, alpha, A.memory(),
                            ld, x.memory(), 1, beta, y.memory(), 1);
                }
                break;

            case Float32:
                if (uplo != null) {
                    if (diag != null) {
                        if (alpha == 1.0 && beta == 0.0 && x == y) {
                            cblas_strmv(A.layout().blas(), uplo.blas(), trans.blas(), diag.blas(), m,
                                    A.memory(), ld, y.memory(), 1);
                        } else {
                            cblas_sgemv(A.layout().blas(), trans.blas(), m, n, (float) alpha, A.memory(),
                                    ld, x.memory(), 1, (float) beta, y.memory(), 1);
                        }
                    } else {
                        cblas_ssymv(A.layout().blas(), trans.blas(), m, (float) alpha, A.memory(),
                                ld, x.memory(), 1, (float) beta, y.memory(), 1);
                    }
                } else {
                    cblas_sgemv(A.layout().blas(), trans.blas(), m, n, (float) alpha, A.memory(),
                            ld, x.memory(), 1, (float) beta, y.memory(), 1);
                }
                break;

            default:
                throw new UnsupportedOperationException("Unsupported ScalarType: " + A.scalarType());
        }
    }

    @Override
    public void mv(Transpose trans, double alpha, Vector x, double beta, Vector y) {
        mv(trans, this, alpha, x, beta, y);
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
        if (A.isSymmetric() && transB == NO_TRANSPOSE && B.layout() == C.layout()) {
            switch (C.scalarType()) {
                case Float64:
                    cblas_dsymm(C.layout().blas(), LEFT.blas(), A.uplo().blas(), m, n,
                        alpha, A.memory(), A.ld(), B.memory(), B.ld(),
                        beta, C.memory(), C.ld());
                break;
                case Float32:
                   cblas_ssymm(C.layout().blas(), LEFT.blas(), A.uplo().blas(), m, n,
                        (float) alpha, A.memory(), A.ld(), B.memory(), B.ld(),
                        (float) beta, C.memory(), C.ld());
                   break;
                default:
                    throw new UnsupportedOperationException("Unsupported ScalarType: " + A.scalarType());
            }
        } else if (B.isSymmetric() && transA == NO_TRANSPOSE && A.layout() == C.layout()) {
            switch (C.scalarType()) {
                case Float64:
                    cblas_dsymm(C.layout().blas(), RIGHT.blas(), B.uplo().blas(), m, n,
                        alpha, B.memory(), B.ld(), A.memory(), A.ld(),
                        beta, C.memory(), C.ld());
                    break;
                case Float32:
                    cblas_ssymm(C.layout().blas(), RIGHT.blas(), B.uplo().blas(), m, n,
                        (float) alpha, B.memory(), B.ld(), A.memory(), A.ld(),
                        (float) beta, C.memory(), C.ld());
                    break;
                default:
                    throw new UnsupportedOperationException("Unsupported ScalarType: " + A.scalarType());
            }
        } else {
            if (C.layout() != A.layout()) {
                transA = Transpose.flip(transA);
                A = A.transpose();
            }
            if (C.layout() != B.layout()) {
                transB = Transpose.flip(transB);
                B = B.transpose();
            }
            int k = transA == NO_TRANSPOSE ? A.ncol() : A.nrow();

            switch (C.scalarType()) {
                case Float64:
                    cblas_dgemm(C.layout().blas(), transA.blas(), transB.blas(), m, n, k,
                        alpha, A.memory(), A.ld(), B.memory(), B.ld(),
                        beta, C.memory(), C.ld());
                    break;
                case Float32:
                    cblas_sgemm(C.layout().blas(), transA.blas(), transB.blas(), m, n, k,
                        (float) alpha, A.memory(), A.ld(), B.memory(), B.ld(),
                        (float) beta, C.memory(), C.ld());
                    break;
                default:
                    throw new UnsupportedOperationException("Unsupported ScalarType: " + A.scalarType());
            }
        }
    }

    @Override
    public DenseMatrix mm(Matrix other) {
        if (ncol() != other.nrow()) {
            throw new IllegalArgumentException(String.format("Matrix multiplication A * B: %d x %d vs %d x %d", m, n, other.nrow(), other.ncol()));
        }

        if (other instanceof DenseMatrix B) {
            DenseMatrix C = zeros(nrow(), B.ncol());
            mm(1.0, NO_TRANSPOSE, this, NO_TRANSPOSE, B, 0.0, C);
            return C;
        }

        throw new UnsupportedOperationException("Unsupported matrix type: " + other.getClass());
    }

    @Override
    public DenseMatrix tm(Matrix other) {
        if (nrow() != other.nrow()) {
            throw new IllegalArgumentException(String.format("Matrix multiplication A' * B: %d x %d vs %d x %d", m, n, other.nrow(), other.ncol()));
        }

        if (other instanceof DenseMatrix B) {
            DenseMatrix C = zeros(nrow(), B.ncol());
            mm(1.0, TRANSPOSE, this, NO_TRANSPOSE, B, 0.0, C);
            return C;
        }

        throw new UnsupportedOperationException("Unsupported matrix type: " + other.getClass());
    }

    @Override
    public DenseMatrix mt(Matrix other) {
        if (ncol() != other.ncol()) {
            throw new IllegalArgumentException(String.format("Matrix multiplication A * B': %d x %d vs %d x %d", m, n, other.nrow(), other.ncol()));
        }

        if (other instanceof DenseMatrix B) {
            DenseMatrix C = zeros(nrow(), B.nrow());
            mm(1.0, NO_TRANSPOSE, this, TRANSPOSE, B, 0.0, C);
            return C;
        }

        throw new UnsupportedOperationException("Unsupported matrix type: " + other.getClass());
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
            case Float64 -> cblas_dger(layout().blas(), m, n, alpha, x.memory, 1, y.memory, 1, memory, ld);
            case Float32 -> cblas_sger(layout().blas(), m, n, (float) alpha, x.memory, 1, y.memory, 1, memory, ld);
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
        DenseMatrix inv = eye(n);
        int[] ipiv = new int[n];
        MemorySegment ipiv_ = MemorySegment.ofArray(ipiv);
        if (isSymmetric()) {
            int info = switch(scalarType()) {
                case Float64 -> LAPACKE_dsysv(lu.layout().lapack(), uplo.lapack(), n, n, lu.memory, lu.ld, ipiv_, inv.memory, inv.ld);
                case Float32 -> LAPACKE_ssysv(lu.layout().lapack(), uplo.lapack(), n, n, lu.memory, lu.ld, ipiv_, inv.memory, inv.ld);
                default -> throw new UnsupportedOperationException("Unsupported scala type: " + scalarType());
            };
            if (info != 0) {
                throw new ArithmeticException("SYSV fails: " + info);
            }
        } else {
            int info = switch(scalarType()) {
                case Float64 -> LAPACKE_dgesv(lu.layout().lapack(), n, n, lu.memory, lu.ld, ipiv_, inv.memory, inv.ld);
                case Float32 -> LAPACKE_sgesv(lu.layout().lapack(), n, n, lu.memory, lu.ld, ipiv_, inv.memory, inv.ld);
                default -> throw new UnsupportedOperationException("Unsupported scala type: " + scalarType());
            };
            if (info != 0) {
                throw new ArithmeticException("GESV fails: " + info);
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
        int[] ipiv = new int[Math.min(m, n)];
        MemorySegment ipiv_ = MemorySegment.ofArray(ipiv);
        int info = switch(scalarType()) {
            case Float64 -> LAPACKE_dgetrf(lu.layout().lapack(), lu.m, lu.n, lu.memory, lu.ld, ipiv_);
            case Float32 -> LAPACKE_sgetrf(lu.layout().lapack(), lu.m, lu.n, lu.memory, lu.ld, ipiv_);
            default -> throw new UnsupportedOperationException("Unsupported scala type: " + scalarType());
        };

        if (info < 0) {
            logger.error("LAPACK GETRF error code: {}", info);
            throw new ArithmeticException("LAPACK GETRF error code: " + info);
        }

        lu.uplo = null; // LU is not symmetric
        return new LU(lu, ipiv, info);
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
        int info = switch(scalarType()) {
            case Float64 -> LAPACKE_dpotrf(lu.layout().lapack(), lu.uplo.lapack(), lu.n, lu.memory, lu.ld);
            case Float32 -> LAPACKE_spotrf(lu.layout().lapack(), lu.uplo.lapack(), lu.n, lu.memory, lu.ld);
            default -> throw new UnsupportedOperationException("Unsupported scala type: " + scalarType());
        };

        if (info != 0) {
            logger.error("LAPACK POTRF error code: {}", info);
            throw new ArithmeticException("LAPACK POTRF error code: " + info);
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
        int info = switch(scalarType()) {
            case Float64 -> LAPACKE_dgeqrf(qr.layout().lapack(), qr.m, qr.n, qr.memory, qr.ld, tau.memory);
            case Float32 -> LAPACKE_sgeqrf(qr.layout().lapack(), qr.m, qr.n, qr.memory, qr.ld, tau.memory);
            default -> throw new UnsupportedOperationException("Unsupported scala type: " + scalarType());
        };

        if (info != 0) {
            logger.error("LAPACK GEQRF error code: {}", info);
            throw new ArithmeticException("LAPACK GEQRF error code: " + info);
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
        return svd(false);
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
     * @param vectors The flag if computing only the singular vectors.
     * @return singular value decomposition.
     */
    public SVD svd(boolean vectors) {
        int k = Math.min(m, n);
        Vector s = vector(k);

        DenseMatrix W = this;
        if (vectors) {
            DenseMatrix U = zeros(m, k);
            DenseMatrix VT = zeros(k, n);

            int info = switch(scalarType()) {
                case Float64 -> LAPACKE_dgesdd(W.layout().lapack(), SVDJob.COMPACT.lapack(), W.m, W.n,
                        W.memory, W.ld, s.memory, U.memory, U.ld, VT.memory, VT.ld);
                case Float32 -> LAPACKE_sgesdd(W.layout().lapack(), SVDJob.COMPACT.lapack(), W.m, W.n,
                        W.memory, W.ld, s.memory, U.memory, U.ld, VT.memory, VT.ld);
                default -> throw new UnsupportedOperationException("Unsupported scala type: " + scalarType());
            };

            if (info != 0) {
                logger.error("LAPACK GESDD with error code: {}", info);
                throw new ArithmeticException("LAPACK GESDD with COMPACT error code: " + info);
            }

            return new SVD(s, U, VT.transpose());
        } else {
            DenseMatrix U = zeros(1, 1);
            DenseMatrix VT = zeros(1, 1);

            int info = switch(scalarType()) {
                case Float64 -> LAPACKE_dgesdd(W.layout().lapack(), SVDJob.NO_VECTORS.lapack(), W.m, W.n,
                        W.memory, W.ld, s.memory, U.memory, U.ld, VT.memory, VT.ld);
                case Float32 -> LAPACKE_sgesdd(W.layout().lapack(), SVDJob.NO_VECTORS.lapack(), W.m, W.n,
                        W.memory, W.ld, s.memory, U.memory, U.ld, VT.memory, VT.ld);
                default -> throw new UnsupportedOperationException("Unsupported scala type: " + scalarType());
            };

            if (info != 0) {
                logger.error("LAPACK GESDD error code: {}", info);
                throw new ArithmeticException("LAPACK GESDD with NO_VECTORS error code: " + info);
            }

            return new SVD(m, n, s);
        }
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
        if (isSymmetric()) {
            Vector w = vector(n);
            int info = switch(scalarType()) {
                case Float64 -> LAPACKE_dsyevd(eig.layout().lapack(), vr ? EVDJob.VECTORS.lapack() : EVDJob.NO_VECTORS.lapack(), eig.uplo.lapack(), n, eig.memory, eig.ld, w.memory);
                case Float32 -> LAPACKE_ssyevd(eig.layout().lapack(), vr ? EVDJob.VECTORS.lapack() : EVDJob.NO_VECTORS.lapack(), eig.uplo.lapack(), n, eig.memory, eig.ld, w.memory);
                default -> throw new UnsupportedOperationException("Unsupported scala type: " + scalarType());
            };

            if (info != 0) {
                logger.error("LAPACK SYEV error code: {}", info);
                throw new ArithmeticException("LAPACK SYEV error code: " + info);
            }

            eig.uplo = null; // Vr is not symmetric
            return new EVD(w, vr ? eig : null);
        } else {
            Vector wr = vector(n);
            Vector wi = vector(n);
            DenseMatrix Vl = vl ? zeros(n, n) : zeros(1, 1);
            DenseMatrix Vr = vr ? zeros(n, n) : zeros(1, 1);
            int info = switch(scalarType()) {
                case Float64 -> LAPACKE_dgeev(eig.layout().lapack(), vl ? EVDJob.VECTORS.lapack() : EVDJob.NO_VECTORS.lapack(),
                        vr ? EVDJob.VECTORS.lapack() : EVDJob.NO_VECTORS.lapack(), n, eig.memory, eig.ld,
                        wr.memory, wi.memory, Vl.memory, Vl.ld, Vr.memory, Vr.ld);
                case Float32 -> LAPACKE_sgeev(eig.layout().lapack(), vl ? EVDJob.VECTORS.lapack() : EVDJob.NO_VECTORS.lapack(),
                        vr ? EVDJob.VECTORS.lapack() : EVDJob.NO_VECTORS.lapack(), n, eig.memory, eig.ld,
                        wr.memory, wi.memory, Vl.memory, Vl.ld, Vr.memory, Vr.ld);
                default -> throw new UnsupportedOperationException("Unsupported scala type: " + scalarType());
            };

            if (info != 0) {
                logger.error("LAPACK GEEV error code: {}", info);
                throw new ArithmeticException("LAPACK GEEV error code: " + info);
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
        DenseMatrix matrix = zeros(ScalarType.Float64, m, n);
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
        DenseMatrix matrix = zeros(ScalarType.Float32, m, n);
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
        DenseMatrix matrix = DenseMatrix.zeros(ScalarType.Float64, n, n);
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
        DenseMatrix matrix = DenseMatrix.zeros(ScalarType.Float32, n, n);
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
}
