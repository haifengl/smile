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
import static smile.linalg.Layout.COL_MAJOR;
import static smile.linalg.Layout.ROW_MAJOR;
import static smile.linalg.Side.LEFT;
import static smile.linalg.Side.RIGHT;
import static smile.linalg.Transpose.NO_TRANSPOSE;
import static smile.linalg.Transpose.TRANSPOSE;
import static smile.linalg.UPLO.LOWER;
import static smile.linalg.blas.cblas_openblas_h.*;
import static smile.linalg.blas.cblas_openblas_h.cblas_dgemm;

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
     * Return the two-dimensional double array of matrix.
     * @return the two-dimensional double array of matrix.
     */
    public double[][] toArray() {
        int m = nrow();
        int n = ncol();
        double[][] array = new double[m][n];
        for (int i = 0; i < m; i++) {
            for (int j = 0; j < n; j++) {
                array[i][j] = get(i, j);
            }
        }
        return array;
    }

    /**
     * Return the two-dimensional float array of matrix.
     * @return the two-dimensional float array of matrix.
     */
    public float[][] toFloatArray() {
        int m = nrow();
        int n = ncol();
        float[][] array = new float[m][n];
        for (int i = 0; i < m; i++) {
            for (int j = 0; j < n; j++) {
                array[i][j] = (float) get(i, j);
            }
        }
        return array;
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
    public void mv(double alpha, Vector x, double beta, Vector y) {
        mv(NO_TRANSPOSE, this, alpha, x, beta, y);
    }

    @Override
    public abstract DenseMatrix transpose();

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
}
