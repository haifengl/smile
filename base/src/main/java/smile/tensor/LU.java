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

import java.io.Serializable;
import java.lang.foreign.MemorySegment;
import static smile.linalg.Transpose.*;
import static smile.linalg.lapack.clapack_h.*;

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
 * @param lu the LU decomposition matrix.
 * @param ipiv the pivot vector.
 * @param info the information code. If {@code info = 0}, the LU decomposition
 *            was successful. If {@code info = i > 0}, U(i,i) is exactly zero.
 *            The factorization has been completed, but the factor U is exactly
 *            singular, and division by zero will occur if it is used to solve
 *            a system of equations.{@code info > 0} if the matrix is singular.
 * @author Haifeng Li
 */
public record LU(DenseMatrix lu, int[] ipiv, int info) implements Serializable {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(LU.class);

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
            if (j+1 != ipiv[j]) {
                d = -d;
            }
        }

        return d;
    }

    /**
     * Returns the inverse of matrix. For pseudo inverse, use QRDecomposition.
     * @return the inverse of matrix.
     */
    public DenseMatrix inverse() {
        DenseMatrix inv = lu.eye(lu.n);
        solve(inv);
        return inv;
    }

    /**
     * Solve A * x = b.
     * @param b the right hand side of linear systems.
     * @throws RuntimeException when the matrix is singular.
     * @return the solution vector.
     */
    public Vector solve(double[] b) {
        // Don't call vector(b) as it will be overwritten.
        Vector x = lu.vector(lu.n);
        for (int i = 0; i < lu.n; i++) x.set(i, b[i]);
        solve(x);
        return x;
    }

    /**
     * Solve A * x = b.
     * @param b the right hand side of linear systems.
     * @throws RuntimeException when the matrix is singular.
     * @return the solution vector.
     */
    public Vector solve(float[] b) {
        // Don't call vector(b) as it will be overwritten.
        Vector x = lu.vector(lu.n);
        for (int i = 0; i < lu.n; i++) x.set(i, b[i]);
        solve(x);
        return x;
    }

    /**
     * Solve A * X = B. B will be overwritten with the solution matrix on output.
     * @param B the right hand side of linear system.
     *          On output, B will be overwritten with the solution matrix.
     * @throws RuntimeException when the matrix is singular.
     */
    public void solve(DenseMatrix B) {
        if (info > 0) {
            throw new RuntimeException("The matrix is singular.");
        }

        if (lu.order() != B.order()) {
            throw new IllegalArgumentException("The matrix layout is inconsistent.");
        }
        if (lu.scalarType() != B.scalarType()) {
            throw new IllegalArgumentException("Incompatible ScalarType: " + B.scalarType() + " != " + lu.scalarType());
        }
        if (lu.m != lu.n) {
            throw new IllegalArgumentException(String.format("The matrix is not square: %d x %d", lu.m, lu.n));
        }
        if (lu.m != B.m) {
            throw new IllegalArgumentException(String.format("Row dimensions do not agree: A is %d x %d, but B is %d x %d", lu.m, lu.n, B.m, B.n));
        }

        byte[] trans = { NO_TRANSPOSE.lapack() };
        int[] n = { lu.n };
        int[] nrhs = { B.n };
        int[] lda = { lu.ld };
        int[] ldb = { B.ld };
        int[] info = { 0 };
        MemorySegment trans_ = MemorySegment.ofArray(trans);
        MemorySegment n_ = MemorySegment.ofArray(n);
        MemorySegment nrhs_ = MemorySegment.ofArray(nrhs);
        MemorySegment lda_ = MemorySegment.ofArray(lda);
        MemorySegment ldb_ = MemorySegment.ofArray(ldb);
        MemorySegment ipiv_ = MemorySegment.ofArray(ipiv);
        MemorySegment info_ = MemorySegment.ofArray(info);
        switch(lu.scalarType()) {
            case Float64 -> dgetrs_(trans_, n_, nrhs_, lu.memory, lda_, ipiv_, B.memory, ldb_, info_);
            case Float32 -> sgetrs_(trans_, n_, nrhs_, lu.memory, lda_, ipiv_, B.memory, ldb_, info_);
            default -> throw new UnsupportedOperationException("Unsupported scalar type: " + lu.scalarType());
        }

        if (info[0] != 0) {
            logger.error("LAPACK GETRS error code: {}", info[0]);
            throw new ArithmeticException("LAPACK GETRS error code: " + info[0]);
        }
    }
}
