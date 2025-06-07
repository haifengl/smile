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
package smile.linalg;

import java.lang.foreign.MemorySegment;
import java.nio.DoubleBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import static smile.linalg.lapack.lapacke_h.*;

/**
 * Linear Algebra Package. LAPACK is a standard software library for numerical
 * linear algebra. It provides routines for solving systems of linear equations
 * and linear least squares, eigenvalue problems, and singular value
 * decomposition. It also includes routines to implement the associated matrix
 * factorizations such as LU, QR, Cholesky and Schur decomposition.
 *
 * @author Haifeng Li
 */
public interface LAPACK {
    /**
     * Solves a real system of linear equations.
     * <pre>{@code
     *     A * X = B
     * }</pre>
     * where A is an N-by-N matrix and X and B are N-by-NRHS matrices.
     * <p>
     * The LU decomposition with partial pivoting and row interchanges is
     * used to factor A as
     * <pre>{@code
     *     A = P * L * U
     * }</pre>
     * where P is a permutation matrix, L is unit lower triangular, and U is
     * upper triangular. The factored form of A is then used to solve the
     * system of equations {@code A * X = B}.
     *
     * @param layout The matrix layout.
     *
     * @param n The number of linear equations, i.e., the order of the matrix A.
     *
     * @param nrhs The number of right hand sides, i.e., the number of columns
     *             of the matrix B.
     *
     * @param A The matrix of dimension (LDA, N).
     *          On exit, the factors L and U from the factorization
     *          {@code A = P*L*U}; the unit diagonal elements of L are not stored.
     *
     * @param lda The leading dimension of the matrix A. {@code LDA >= max(1,N)}.
     *
     * @param ipiv The pivot indices that define the permutation matrix P;
     *             row i of the matrix was interchanged with row IPIV(i).
     *
     * @param B On entry, the N-by-NRHS matrix of right hand side matrix B.
     *          On exit, if INFO = 0, the N-by-NRHS solution matrix X.
     *
     * @param ldb The leading dimension of the matrix B. {@code LDB >= max(1,N)}.
     *
     * @return INFO flag.
     *         {@code = 0}:  successful exit
     *         {@code < 0}:  if {@code INFO = -i}, the i-th argument had an illegal value
     *         {@code > 0}:  if {@code INFO = i}, U(i,i) is exactly zero. The factorization
     *               has been completed, but the factor U is exactly
     *               singular, so the solution could not be computed.
     */
    static int gesv(Layout layout, int n, int nrhs, double[] A, int lda, int[] ipiv, double[] B, int ldb) {
        var A_ = MemorySegment.ofArray(A);
        var B_ = MemorySegment.ofArray(B);
        var ipiv_ = MemorySegment.ofArray(ipiv);
        return LAPACKE_dgesv(layout.lapack(), n, nrhs, A_, lda, ipiv_, B_, ldb);
    }

    /**
     * Solves a real system of linear equations.
     * <pre>{@code
     *     A * X = B
     * }</pre>
     * where A is an N-by-N matrix and X and B are N-by-NRHS matrices.
     * <p>
     * The LU decomposition with partial pivoting and row interchanges is
     * used to factor A as
     * <pre>{@code
     *     A = P * L * U
     * }</pre>
     * where P is a permutation matrix, L is unit lower triangular, and U is
     * upper triangular. The factored form of A is then used to solve the
     * system of equations A * X = B.
     *
     * @param layout The matrix layout.
     *
     * @param n The number of linear equations, i.e., the order of the matrix A.
     *
     * @param nrhs The number of right hand sides, i.e., the number of columns
     *             of the matrix B.
     *
     * @param A The matrix of dimension (LDA, N).
     *          On exit, the factors L and U from the factorization
     *          {@code A = P*L*U}; the unit diagonal elements of L are not stored.
     *
     * @param lda The leading dimension of the matrix A.{@code LDA >= max(1,N)}.
     *
     * @param ipiv The pivot indices that define the permutation matrix P;
     *             row i of the matrix was interchanged with row IPIV(i).
     *
     * @param B On entry, the N-by-NRHS matrix of right hand side matrix B.
     *          On exit, if INFO = 0, the N-by-NRHS solution matrix X.
     *
     * @param ldb The leading dimension of the matrix B. {@code LDB >= max(1,N)}.
     *
     * @return INFO flag.
     *         {@code = 0}:  successful exit
     *         {@code < 0}:  if {@code INFO = -i}, the i-th argument had an illegal value
     *         {@code > 0}:  if {@code INFO = i}, U(i,i) is exactly zero. The factorization
     *               has been completed, but the factor U is exactly
     *               singular, so the solution could not be computed.
     */
    static int gesv(Layout layout, int n, int nrhs, DoubleBuffer A, int lda, IntBuffer ipiv, DoubleBuffer B, int ldb) {
        var A_ = MemorySegment.ofBuffer(A);
        var B_ = MemorySegment.ofBuffer(B);
        var ipiv_ = MemorySegment.ofBuffer(ipiv);
        return LAPACKE_dgesv(layout.lapack(), n, nrhs, A_, lda, ipiv_, B_, ldb);
    }

    /**
     * Solves a real system of linear equations.
     * <pre>{@code
     *     A * X = B
     * }</pre>
     * where A is an N-by-N matrix and X and B are N-by-NRHS matrices.
     * <p>
     * The LU decomposition with partial pivoting and row interchanges is
     * used to factor A as
     * <pre>{@code
     *     A = P * L * U
     * }</pre>
     * where P is a permutation matrix, L is unit lower triangular, and U is
     * upper triangular. The factored form of A is then used to solve the
     * system of equations A * X = B.
     *
     * @param layout The matrix layout.
     *
     * @param n The number of linear equations, i.e., the order of the matrix A.
     *
     * @param nrhs The number of right hand sides, i.e., the number of columns
     *             of the matrix B.
     *
     * @param A The matrix of dimension (LDA, N).
     *          On exit, the factors L and U from the factorization
     *          {@code A = P*L*U}; the unit diagonal elements of L are not stored.
     *
     * @param lda The leading dimension of the matrix A.{@code LDA >= max(1,N)}.
     *
     * @param ipiv The pivot indices that define the permutation matrix P;
     *             row i of the matrix was interchanged with row IPIV(i).
     *
     * @param B On entry, the N-by-NRHS matrix of right hand side matrix B.
     *          On exit, if INFO = 0, the N-by-NRHS solution matrix X.
     *
     * @param ldb The leading dimension of the matrix B. {@code LDB >= max(1,N)}.
     *
     * @return INFO flag.
     *         {@code = 0}:  successful exit
     *         {@code < 0}:  if {@code INFO = -i}, the i-th argument had an illegal value
     *         {@code > 0}:  if {@code INFO = i}, U(i,i) is exactly zero. The factorization
     *               has been completed, but the factor U is exactly
     *               singular, so the solution could not be computed.
     */
    static int gesv(Layout layout, int n, int nrhs, MemorySegment A, int lda, MemorySegment ipiv, MemorySegment B, int ldb) {
        return LAPACKE_dgesv(layout.lapack(), n, nrhs, A, lda, ipiv, B, ldb);
    }

    /**
     * Solves a real system of linear equations.
     * <pre>{@code
     *     A * X = B
     * }</pre>
     * where A is an N-by-N matrix and X and B are N-by-NRHS matrices.
     * <p>
     * The LU decomposition with partial pivoting and row interchanges is
     * used to factor A as
     * <pre>{@code
     *     A = P * L * U
     * }</pre>
     * where P is a permutation matrix, L is unit lower triangular, and U is
     * upper triangular. The factored form of A is then used to solve the
     * system of equations A * X = B.
     *
     * @param layout The matrix layout.
     *
     * @param n The number of linear equations, i.e., the order of the matrix A.
     *
     * @param nrhs The number of right hand sides, i.e., the number of columns
     *             of the matrix B.
     *
     * @param A The matrix of dimension (LDA, N).
     *          On exit, the factors L and U from the factorization
     *          {@code A = P*L*U}; the unit diagonal elements of L are not stored.
     *
     * @param lda The leading dimension of the matrix A. {@code LDA >= max(1,N)}.
     *
     * @param ipiv The pivot indices that define the permutation matrix P;
     *             row i of the matrix was interchanged with row IPIV(i).
     *
     * @param B On entry, the N-by-NRHS matrix of right hand side matrix B.
     *          On exit, if INFO = 0, the N-by-NRHS solution matrix X.
     *
     * @param ldb The leading dimension of the matrix B. {@code LDB >= max(1,N)}.
     *
     * @return INFO flag.
     *         {@code = 0}:  successful exit
     *         {@code < 0}:  if {@code INFO = -i}, the i-th argument had an illegal value
     *         {@code > 0}:  if {@code INFO = i}, U(i,i) is exactly zero. The factorization
     *               has been completed, but the factor U is exactly
     *               singular, so the solution could not be computed.
     */
    static int gesv(Layout layout, int n, int nrhs, float[] A, int lda, int[] ipiv, float[] B, int ldb) {
        var A_ = MemorySegment.ofArray(A);
        var B_ = MemorySegment.ofArray(B);
        var ipiv_ = MemorySegment.ofArray(ipiv);
        return LAPACKE_sgesv(layout.lapack(), n, nrhs, A_, lda, ipiv_, B_, ldb);
    }

    /**
     * Solves a real system of linear equations.
     * <pre>{@code
     *     A * X = B
     * }</pre>
     * where A is an N-by-N matrix and X and B are N-by-NRHS matrices.
     * <p>
     * The LU decomposition with partial pivoting and row interchanges is
     * used to factor A as
     * <pre>{@code
     *     A = P * L * U
     * }</pre>
     * where P is a permutation matrix, L is unit lower triangular, and U is
     * upper triangular. The factored form of A is then used to solve the
     * system of equations A * X = B.
     *
     * @param layout The matrix layout.
     *
     * @param n The number of linear equations, i.e., the order of the matrix A.
     *
     * @param nrhs The number of right hand sides, i.e., the number of columns
     *             of the matrix B.
     *
     * @param A The matrix of dimension (LDA, N).
     *          On exit, the factors L and U from the factorization
     *          {@code A = P*L*U}; the unit diagonal elements of L are not stored.
     *
     * @param lda The leading dimension of the matrix A. {@code LDA >= max(1,N)}.
     *
     * @param ipiv The pivot indices that define the permutation matrix P;
     *             row i of the matrix was interchanged with row IPIV(i).
     *
     * @param B On entry, the N-by-NRHS matrix of right hand side matrix B.
     *          On exit, if INFO = 0, the N-by-NRHS solution matrix X.
     *
     * @param ldb The leading dimension of the matrix B. {@code LDB >= max(1,N)}.
     *
     * @return INFO flag.
     *         {@code = 0}:  successful exit
     *         {@code < 0}:  if {@code INFO = -i}, the i-th argument had an illegal value
     *         {@code > 0}:  if {@code INFO = i}, U(i,i) is exactly zero. The factorization
     *               has been completed, but the factor U is exactly
     *               singular, so the solution could not be computed.
     */
    static int gesv(Layout layout, int n, int nrhs, FloatBuffer A, int lda, IntBuffer ipiv, FloatBuffer B, int ldb) {
        var A_ = MemorySegment.ofBuffer(A);
        var B_ = MemorySegment.ofBuffer(B);
        var ipiv_ = MemorySegment.ofBuffer(ipiv);
        return LAPACKE_sgesv(layout.lapack(), n, nrhs, A_, lda, ipiv_, B_, ldb);
    }

    /**
     * Solves a real system of linear equations.
     * <pre>{@code
     *     A * X = B
     * }</pre>
     * where A is an N-by-N symmetric matrix and X and B are N-by-NRHS matrices.
     * <p>
     * The diagonal pivoting method is used to factor A as
     * <pre>{@code
     *     A = U * D * U<sup>T</sup>,  if UPLO = 'U'
     * }</pre>
     * or
     * <pre>{@code
     *     A = L * D * L<sup>T</sup>,  if UPLO = 'L'
     * }</pre>
     * where U (or L) is a product of permutation and unit upper (lower)
     * triangular matrices, and D is symmetric and block diagonal with
     * 1-by-1 and 2-by-2 diagonal blocks. The factored form of A is then
     * used to solve the system of equations A * X = B.
     *
     * @param layout The matrix layout.
     *
     * @param uplo The upper or lower triangular part of the matrix A is
     *             to be referenced.
     *
     * @param n The number of linear equations, i.e., the order of the matrix A.
     *
     * @param nrhs The number of right hand sides, i.e., the number of columns
     *             of the matrix B.
     *
     * @param A The symmetric matrix of dimension (LDA, N).
     *          On exit, the factor U or L from the Cholesky factorization
     *          A = U<sup>T</sup>*U or A = L*L<sup>T</sup>.
     *
     * @param lda The leading dimension of the matrix A. {@code LDA >= max(1,N)}.
     *
     * @param ipiv The pivot indices that define the permutation matrix P;
     *             row i of the matrix was interchanged with row IPIV(i).
     *
     * @param B On entry, the N-by-NRHS matrix of right hand side matrix B.
     *          On exit, if INFO = 0, the N-by-NRHS solution matrix X.
     *
     * @param ldb The leading dimension of the matrix B. {@code LDB >= max(1,N)}.
     *
     * @return INFO flag.
     *         {@code = 0}:  successful exit
     *         {@code < 0}:  if {@code INFO = -i}, the i-th argument had an illegal value
     *         {@code > 0}:  if {@code INFO = i}, the leading minor of order i of A is not
     *               positive definite, so the factorization could not be
     *               completed, and the solution has not been computed.
     */
    static int sysv(Layout layout, UPLO uplo, int n, int nrhs, double[] A, int lda, int[] ipiv, double[] B, int ldb) {
        var A_ = MemorySegment.ofArray(A);
        var B_ = MemorySegment.ofArray(B);
        var ipiv_ = MemorySegment.ofArray(ipiv);
        return LAPACKE_dsysv(layout.lapack(), uplo.lapack(), n, nrhs, A_, lda, ipiv_, B_, ldb);
    }

    /**
     * Solves a real system of linear equations.
     * <pre>{@code
     *     A * X = B
     * }</pre>
     * where A is an N-by-N symmetric matrix and X and B are N-by-NRHS matrices.
     * <p>
     * The diagonal pivoting method is used to factor A as
     * <pre>{@code
     *     A = U * D * U<sup>T</sup>,  if UPLO = 'U'
     * }</pre>
     * or
     * <pre>{@code
     *     A = L * D * L<sup>T</sup>,  if UPLO = 'L'
     * }</pre>
     * where U (or L) is a product of permutation and unit upper (lower)
     * triangular matrices, and D is symmetric and block diagonal with
     * 1-by-1 and 2-by-2 diagonal blocks. The factored form of A is then
     * used to solve the system of equations A * X = B.
     *
     * @param layout The matrix layout.
     *
     * @param uplo The upper or lower triangular part of the matrix A is
     *             to be referenced.
     *
     * @param n The number of linear equations, i.e., the order of the matrix A.
     *
     * @param nrhs The number of right hand sides, i.e., the number of columns
     *             of the matrix B.
     *
     * @param A The symmetric matrix of dimension (LDA, N).
     *          On exit, the factor U or L from the Cholesky factorization
     *          A = U<sup>T</sup>*U or A = L*L<sup>T</sup>.
     *
     * @param lda The leading dimension of the matrix A. {@code LDA >= max(1,N)}.
     *
     * @param ipiv The pivot indices that define the permutation matrix P;
     *             row i of the matrix was interchanged with row IPIV(i).
     *
     * @param B On entry, the N-by-NRHS matrix of right hand side matrix B.
     *          On exit, if INFO = 0, the N-by-NRHS solution matrix X.
     *
     * @param ldb The leading dimension of the matrix B. {@code LDB >= max(1,N)}.
     *
     * @return INFO flag.
     *         {@code = 0}:  successful exit
     *         {@code < 0}:  if {@code INFO = -i}, the i-th argument had an illegal value
     *         {@code > 0}:  if {@code INFO = i}, the leading minor of order i of A is not
     *               positive definite, so the factorization could not be
     *               completed, and the solution has not been computed.
     */
    static int sysv(Layout layout, UPLO uplo, int n, int nrhs, DoubleBuffer A, int lda, IntBuffer ipiv, DoubleBuffer B, int ldb) {
        var A_ = MemorySegment.ofBuffer(A);
        var B_ = MemorySegment.ofBuffer(B);
        var ipiv_ = MemorySegment.ofBuffer(ipiv);
        return LAPACKE_dsysv(layout.lapack(), uplo.lapack(), n, nrhs, A_, lda, ipiv_, B_, ldb);
    }

    /**
     * Solves a real system of linear equations.
     * <pre>{@code
     *     A * X = B
     * }</pre>
     * where A is an N-by-N symmetric matrix and X and B are N-by-NRHS matrices.
     * <p>
     * The diagonal pivoting method is used to factor A as
     * <pre>{@code
     *     A = U * D * U<sup>T</sup>,  if UPLO = 'U'
     * }</pre>
     * or
     * <pre>{@code
     *     A = L * D * L<sup>T</sup>,  if UPLO = 'L'
     * }</pre>
     * where U (or L) is a product of permutation and unit upper (lower)
     * triangular matrices, and D is symmetric and block diagonal with
     * 1-by-1 and 2-by-2 diagonal blocks. The factored form of A is then
     * used to solve the system of equations A * X = B.
     *
     * @param layout The matrix layout.
     *
     * @param uplo The upper or lower triangular part of the matrix A is
     *             to be referenced.
     *
     * @param n The number of linear equations, i.e., the order of the matrix A.
     *
     * @param nrhs The number of right hand sides, i.e., the number of columns
     *             of the matrix B.
     *
     * @param A The symmetric matrix of dimension (LDA, N).
     *          On exit, the factor U or L from the Cholesky factorization
     *          A = U<sup>T</sup>*U or A = L*L<sup>T</sup>.
     *
     * @param lda The leading dimension of the matrix A. {@code LDA >= max(1,N)}.
     *
     * @param ipiv The pivot indices that define the permutation matrix P;
     *             row i of the matrix was interchanged with row IPIV(i).
     *
     * @param B On entry, the N-by-NRHS matrix of right hand side matrix B.
     *          On exit, if INFO = 0, the N-by-NRHS solution matrix X.
     *
     * @param ldb The leading dimension of the matrix B. {@code LDB >= max(1,N)}.
     *
     * @return INFO flag.
     *         {@code = 0}:  successful exit
     *         {@code < 0}:  if {@code INFO = -i}, the i-th argument had an illegal value
     *         {@code > 0}:  if {@code INFO = i}, the leading minor of order i of A is not
     *               positive definite, so the factorization could not be
     *               completed, and the solution has not been computed.
     */
    static int sysv(Layout layout, UPLO uplo, int n, int nrhs, MemorySegment A, int lda, MemorySegment ipiv, MemorySegment B, int ldb) {
        return LAPACKE_dsysv(layout.lapack(), uplo.lapack(), n, nrhs, A, lda, ipiv, B, ldb);
    }

    /**
     * Solves a real system of linear equations.
     * <pre>{@code
     *     A * X = B
     * }</pre>
     * where A is an N-by-N symmetric matrix and X and B are N-by-NRHS matrices.
     * <p>
     * The diagonal pivoting method is used to factor A as
     * <pre>{@code
     *     A = U * D * U<sup>T</sup>,  if UPLO = 'U'
     * }</pre>
     * or
     * <pre>{@code
     *     A = L * D * L<sup>T</sup>,  if UPLO = 'L'
     * }</pre>
     * where U (or L) is a product of permutation and unit upper (lower)
     * triangular matrices, and D is symmetric and block diagonal with
     * 1-by-1 and 2-by-2 diagonal blocks. The factored form of A is then
     * used to solve the system of equations A * X = B.
     *
     * @param layout The matrix layout.
     *
     * @param uplo The upper or lower triangular part of the matrix A is
     *             to be referenced.
     *
     * @param n The number of linear equations, i.e., the order of the matrix A.
     *
     * @param nrhs The number of right hand sides, i.e., the number of columns
     *             of the matrix B.
     *
     * @param A The symmetric matrix of dimension (LDA, N).
     *          On exit, the factor U or L from the Cholesky factorization
     *          A = U<sup>T</sup>*U or A = L*L<sup>T</sup>.
     *
     * @param lda The leading dimension of the matrix A. {@code LDA >= max(1,N)}.
     *
     * @param ipiv The pivot indices that define the permutation matrix P;
     *             row i of the matrix was interchanged with row IPIV(i).
     *
     * @param B On entry, the N-by-NRHS matrix of right hand side matrix B.
     *          On exit, if INFO = 0, the N-by-NRHS solution matrix X.
     *
     * @param ldb The leading dimension of the matrix B. {@code LDB >= max(1,N)}.
     *
     * @return INFO flag.
     *         {@code = 0}:  successful exit
     *         {@code < 0}:  if {@code INFO = -i}, the i-th argument had an illegal value
     *         {@code > 0}:  if {@code INFO = i}, the leading minor of order i of A is not
     *               positive definite, so the factorization could not be
     *               completed, and the solution has not been computed.
     */
    static int sysv(Layout layout, UPLO uplo, int n, int nrhs, float[] A, int lda, int[] ipiv, float[] B, int ldb) {
        var A_ = MemorySegment.ofArray(A);
        var B_ = MemorySegment.ofArray(B);
        var ipiv_ = MemorySegment.ofArray(ipiv);
        return LAPACKE_ssysv(layout.lapack(), uplo.lapack(), n, nrhs, A_, lda, ipiv_, B_, ldb);
    }

    /**
     * Solves a real system of linear equations.
     * <pre>{@code
     *     A * X = B
     * }</pre>
     * where A is an N-by-N symmetric matrix and X and B are N-by-NRHS matrices.
     * <p>
     * The diagonal pivoting method is used to factor A as
     * <pre>{@code
     *     A = U * D * U<sup>T</sup>,  if UPLO = 'U'
     * }</pre>
     * or
     * <pre>{@code
     *     A = L * D * L<sup>T</sup>,  if UPLO = 'L'
     * }</pre>
     * where U (or L) is a product of permutation and unit upper (lower)
     * triangular matrices, and D is symmetric and block diagonal with
     * 1-by-1 and 2-by-2 diagonal blocks. The factored form of A is then
     * used to solve the system of equations A * X = B.
     *
     * @param layout The matrix layout.
     *
     * @param uplo The upper or lower triangular part of the matrix A is
     *             to be referenced.
     *
     * @param n The number of linear equations, i.e., the order of the matrix A.
     *
     * @param nrhs The number of right hand sides, i.e., the number of columns
     *             of the matrix B.
     *
     * @param A The symmetric matrix of dimension (LDA, N).
     *          On exit, the factor U or L from the Cholesky factorization
     *          A = U<sup>T</sup>*U or A = L*L<sup>T</sup>.
     *
     * @param lda The leading dimension of the matrix A. {@code LDA >= max(1,N)}.
     *
     * @param ipiv The pivot indices that define the permutation matrix P;
     *             row i of the matrix was interchanged with row IPIV(i).
     *
     * @param B On entry, the N-by-NRHS matrix of right hand side matrix B.
     *          On exit, if INFO = 0, the N-by-NRHS solution matrix X.
     *
     * @param ldb The leading dimension of the matrix B. {@code LDB >= max(1,N)}.
     *
     * @return INFO flag.
     *         {@code = 0}:  successful exit
     *         {@code < 0}:  if {@code INFO = -i}, the i-th argument had an illegal value
     *         {@code > 0}:  if {@code INFO = i}, the leading minor of order i of A is not
     *               positive definite, so the factorization could not be
     *               completed, and the solution has not been computed.
     */
    static int sysv(Layout layout, UPLO uplo, int n, int nrhs, FloatBuffer A, int lda, IntBuffer ipiv, FloatBuffer B, int ldb) {
        var A_ = MemorySegment.ofBuffer(A);
        var B_ = MemorySegment.ofBuffer(B);
        var ipiv_ = MemorySegment.ofBuffer(ipiv);
        return LAPACKE_ssysv(layout.lapack(), uplo.lapack(), n, nrhs, A_, lda, ipiv_, B_, ldb);
    }

    /**
     * Solves a real system of linear equations.
     * <pre>{@code
     *     A * X = B
     * }</pre>
     * where A is an N-by-N symmetric matrix and X and B are N-by-NRHS matrices.
     * <p>
     * The diagonal pivoting method is used to factor A as
     * <pre>{@code
     *     A = U * D * U<sup>T</sup>,  if UPLO = 'U'
     * }</pre>
     * or
     * <pre>{@code
     *     A = L * D * L<sup>T</sup>,  if UPLO = 'L'
     * }</pre>
     * where U (or L) is a product of permutation and unit upper (lower)
     * triangular matrices, and D is symmetric and block diagonal with
     * 1-by-1 and 2-by-2 diagonal blocks. The factored form of A is then
     * used to solve the system of equations A * X = B.
     *
     * @param layout The matrix layout.
     *
     * @param uplo The upper or lower triangular part of the matrix A is
     *             to be referenced.
     *
     * @param n The number of linear equations, i.e., the order of the matrix A.
     *
     * @param nrhs The number of right hand sides, i.e., the number of columns
     *             of the matrix B.
     *
     * @param A The symmetric packed matrix.
     *          On exit, the factor U or L from the Cholesky factorization
     *          A = U<sup>T</sup>*U or A = L*L<sup>T</sup>, in the same storage format as A.
     *
     * @param ipiv The pivot indices that define the permutation matrix P;
     *             row i of the matrix was interchanged with row IPIV(i).
     *
     * @param B On entry, the N-by-NRHS matrix of right hand side matrix B.
     *          On exit, if INFO = 0, the N-by-NRHS solution matrix X.
     *
     * @param ldb The leading dimension of the matrix B. {@code LDB >= max(1,N)}.
     *
     * @return INFO flag.
     *         {@code = 0}:  successful exit
     *         {@code < 0}:  if {@code INFO = -i}, the i-th argument had an illegal value
     *         {@code > 0}:  if {@code INFO = i}, the leading minor of order i of A is not
     *               positive definite, so the factorization could not be
     *               completed, and the solution has not been computed.
     */
    static int spsv(Layout layout, UPLO uplo, int n, int nrhs, double[] A, int[] ipiv, double[] B, int ldb) {
        var A_ = MemorySegment.ofArray(A);
        var B_ = MemorySegment.ofArray(B);
        var ipiv_ = MemorySegment.ofArray(ipiv);
        return LAPACKE_dspsv(layout.lapack(), uplo.lapack(), n, nrhs, A_, ipiv_, B_, ldb);
    }

    /**
     * Solves a real system of linear equations.
     * <pre>{@code
     *     A * X = B
     * }</pre>
     * where A is an N-by-N symmetric matrix and X and B are N-by-NRHS matrices.
     * <p>
     * The diagonal pivoting method is used to factor A as
     * <pre>{@code
     *     A = U * D * U<sup>T</sup>,  if UPLO = 'U'
     * }</pre>
     * or
     * <pre>{@code
     *     A = L * D * L<sup>T</sup>,  if UPLO = 'L'
     * }</pre>
     * where U (or L) is a product of permutation and unit upper (lower)
     * triangular matrices, and D is symmetric and block diagonal with
     * 1-by-1 and 2-by-2 diagonal blocks. The factored form of A is then
     * used to solve the system of equations A * X = B.
     *
     * @param layout The matrix layout.
     *
     * @param uplo The upper or lower triangular part of the matrix A is
     *             to be referenced.
     *
     * @param n The number of linear equations, i.e., the order of the matrix A.
     *
     * @param nrhs The number of right hand sides, i.e., the number of columns
     *             of the matrix B.
     *
     * @param A The symmetric packed matrix.
     *          On exit, the factor U or L from the Cholesky factorization
     *          A = U<sup>T</sup>*U or A = L*L<sup>T</sup>, in the same storage format as A.
     *
     * @param ipiv The pivot indices that define the permutation matrix P;
     *             row i of the matrix was interchanged with row IPIV(i).
     *
     * @param B On entry, the N-by-NRHS matrix of right hand side matrix B.
     *          On exit, if INFO = 0, the N-by-NRHS solution matrix X.
     *
     * @param ldb The leading dimension of the matrix B. {@code LDB >= max(1,N)}.
     *
     * @return INFO flag.
     *         {@code = 0}:  successful exit
     *         {@code < 0}:  if {@code INFO = -i}, the i-th argument had an illegal value
     *         {@code > 0}:  if {@code INFO = i}, the leading minor of order i of A is not
     *               positive definite, so the factorization could not be
     *               completed, and the solution has not been computed.
     */
    static int spsv(Layout layout, UPLO uplo, int n, int nrhs, DoubleBuffer A, IntBuffer ipiv, DoubleBuffer B, int ldb) {
        var A_ = MemorySegment.ofBuffer(A);
        var B_ = MemorySegment.ofBuffer(B);
        var ipiv_ = MemorySegment.ofBuffer(ipiv);
        return LAPACKE_dspsv(layout.lapack(), uplo.lapack(), n, nrhs, A_, ipiv_, B_, ldb);
    }

    /**
     * Solves a real system of linear equations.
     * <pre>{@code
     *     A * X = B
     * }</pre>
     * where A is an N-by-N symmetric matrix and X and B are N-by-NRHS matrices.
     * <p>
     * The diagonal pivoting method is used to factor A as
     * <pre>{@code
     *     A = U * D * U<sup>T</sup>,  if UPLO = 'U'
     * }</pre>
     * or
     * <pre>{@code
     *     A = L * D * L<sup>T</sup>,  if UPLO = 'L'
     * }</pre>
     * where U (or L) is a product of permutation and unit upper (lower)
     * triangular matrices, and D is symmetric and block diagonal with
     * 1-by-1 and 2-by-2 diagonal blocks. The factored form of A is then
     * used to solve the system of equations A * X = B.
     *
     * @param layout The matrix layout.
     *
     * @param uplo The upper or lower triangular part of the matrix A is
     *             to be referenced.
     *
     * @param n The number of linear equations, i.e., the order of the matrix A.
     *
     * @param nrhs The number of right hand sides, i.e., the number of columns
     *             of the matrix B.
     *
     * @param A The symmetric packed matrix.
     *          On exit, the factor U or L from the Cholesky factorization
     *          A = U<sup>T</sup>*U or A = L*L<sup>T</sup>, in the same storage format as A.
     *
     * @param ipiv The pivot indices that define the permutation matrix P;
     *             row i of the matrix was interchanged with row IPIV(i).
     *
     * @param B On entry, the N-by-NRHS matrix of right hand side matrix B.
     *          On exit, if INFO = 0, the N-by-NRHS solution matrix X.
     *
     * @param ldb The leading dimension of the matrix B. {@code LDB >= max(1,N)}.
     *
     * @return INFO flag.
     *         {@code = 0}:  successful exit
     *         {@code < 0}:  if {@code INFO = -i}, the i-th argument had an illegal value
     *         {@code > 0}:  if {@code INFO = i}, the leading minor of order i of A is not
     *               positive definite, so the factorization could not be
     *               completed, and the solution has not been computed.
     */
    static int spsv(Layout layout, UPLO uplo, int n, int nrhs, float[] A, int[] ipiv, float[] B, int ldb) {
        var A_ = MemorySegment.ofArray(A);
        var B_ = MemorySegment.ofArray(B);
        var ipiv_ = MemorySegment.ofArray(ipiv);
        return LAPACKE_sspsv(layout.lapack(), uplo.lapack(), n, nrhs, A_, ipiv_, B_, ldb);
    }

    /**
     * Solves a real system of linear equations.
     * <pre>{@code
     *     A * X = B
     * }</pre>
     * where A is an N-by-N symmetric matrix and X and B are N-by-NRHS matrices.
     * <p>
     * The diagonal pivoting method is used to factor A as
     * <pre>{@code
     *     A = U * D * U<sup>T</sup>,  if UPLO = 'U'
     * }</pre>
     * or
     * <pre>{@code
     *     A = L * D * L<sup>T</sup>,  if UPLO = 'L'
     * }</pre>
     * where U (or L) is a product of permutation and unit upper (lower)
     * triangular matrices, and D is symmetric and block diagonal with
     * 1-by-1 and 2-by-2 diagonal blocks. The factored form of A is then
     * used to solve the system of equations A * X = B.
     *
     * @param layout The matrix layout.
     *
     * @param uplo The upper or lower triangular part of the matrix A is
     *             to be referenced.
     *
     * @param n The number of linear equations, i.e., the order of the matrix A.
     *
     * @param nrhs The number of right hand sides, i.e., the number of columns
     *             of the matrix B.
     *
     * @param A The symmetric packed matrix.
     *          On exit, the factor U or L from the Cholesky factorization
     *          A = U<sup>T</sup>*U or A = L*L<sup>T</sup>, in the same storage format as A.
     *
     * @param ipiv The pivot indices that define the permutation matrix P;
     *             row i of the matrix was interchanged with row IPIV(i).
     *
     * @param B On entry, the N-by-NRHS matrix of right hand side matrix B.
     *          On exit, if INFO = 0, the N-by-NRHS solution matrix X.
     *
     * @param ldb The leading dimension of the matrix B. {@code LDB >= max(1,N)}.
     *
     * @return INFO flag.
     *         {@code = 0}:  successful exit
     *         {@code < 0}:  if {@code INFO = -i}, the i-th argument had an illegal value
     *         {@code > 0}:  if {@code INFO = i}, the leading minor of order i of A is not
     *               positive definite, so the factorization could not be
     *               completed, and the solution has not been computed.
     */
    static int spsv(Layout layout, UPLO uplo, int n, int nrhs, FloatBuffer A, IntBuffer ipiv, FloatBuffer B, int ldb) {
        var A_ = MemorySegment.ofBuffer(A);
        var B_ = MemorySegment.ofBuffer(B);
        var ipiv_ = MemorySegment.ofBuffer(ipiv);
        return LAPACKE_sspsv(layout.lapack(), uplo.lapack(), n, nrhs, A_, ipiv_, B_, ldb);
    }

    /**
     * Solves a real system of linear equations.
     * <pre>{@code
     *     A * X = B
     * }</pre>
     * where A is an N-by-N symmetric positive definite matrix and X and B are N-by-NRHS matrices.
     * <p>
     * The Cholesky decomposition is used to factor A as
     * <pre>{@code
     *     A = U<sup>T</sup>* U,  if UPLO = 'U'
     * }</pre>
     * or
     * <pre>{@code
     *     A = L * L<sup>T</sup>,  if UPLO = 'L'
     * }</pre>
     * where U is an upper triangular matrix and L is a lower triangular
     * matrix.  The factored form of A is then used to solve the system of
     * equations A * X = B.
     *
     * @param layout The matrix layout.
     *
     * @param uplo The upper or lower triangular part of the matrix A is
     *             to be referenced.
     *
     * @param n The number of linear equations, i.e., the order of the matrix A.
     *
     * @param nrhs The number of right hand sides, i.e., the number of columns
     *             of the matrix B.
     *
     * @param A The symmetric matrix of dimension (LDA, N).
     *          On exit, the factor U or L from the Cholesky factorization
     *          A = U<sup>T</sup>*U or A = L*L<sup>T</sup>.
     *
     * @param lda The leading dimension of the matrix A. {@code LDA >= max(1,N)}.
     *
     * @param B On entry, the N-by-NRHS matrix of right hand side matrix B.
     *          On exit, if INFO = 0, the N-by-NRHS solution matrix X.
     *
     * @param ldb The leading dimension of the matrix B. {@code LDB >= max(1,N)}.
     *
     * @return INFO flag.
     *         {@code = 0}:  successful exit
     *         {@code < 0}:  if {@code INFO = -i}, the i-th argument had an illegal value
     *         {@code > 0}:  if {@code INFO = i}, the leading minor of order i of A is not
     *               positive definite, so the factorization could not be
     *               completed, and the solution has not been computed.
     */
    static int posv(Layout layout, UPLO uplo, int n, int nrhs, double[] A, int lda, double[] B, int ldb) {
        var A_ = MemorySegment.ofArray(A);
        var B_ = MemorySegment.ofArray(B);
        return LAPACKE_dposv(layout.lapack(), uplo.lapack(), n, nrhs, A_, lda, B_, ldb);
    }

    /**
     * Solves a real system of linear equations.
     * <pre>{@code
     *     A * X = B
     * }</pre>
     * where A is an N-by-N symmetric positive definite matrix and X and B are N-by-NRHS matrices.
     * <p>
     * The Cholesky decomposition is used to factor A as
     * <pre>{@code
     *     A = U<sup>T</sup>* U,  if UPLO = 'U'
     * }</pre>
     * or
     * <pre>{@code
     *     A = L * L<sup>T</sup>,  if UPLO = 'L'
     * }</pre>
     * where U is an upper triangular matrix and L is a lower triangular
     * matrix.  The factored form of A is then used to solve the system of
     * equations A * X = B.
     *
     * @param layout The matrix layout.
     *
     * @param uplo The upper or lower triangular part of the matrix A is
     *             to be referenced.
     *
     * @param n The number of linear equations, i.e., the order of the matrix A.
     *
     * @param nrhs The number of right hand sides, i.e., the number of columns
     *             of the matrix B.
     *
     * @param A The symmetric matrix of dimension (LDA, N).
     *          On exit, the factor U or L from the Cholesky factorization
     *          A = U<sup>T</sup>*U or A = L*L<sup>T</sup>
     *
     * @param lda The leading dimension of the matrix A. {@code LDA >= max(1,N)}.
     *
     * @param B On entry, the N-by-NRHS matrix of right hand side matrix B.
     *          On exit, if INFO = 0, the N-by-NRHS solution matrix X.
     *
     * @param ldb The leading dimension of the matrix B. {@code LDB >= max(1,N)}.
     *
     * @return INFO flag.
     *         {@code = 0}:  successful exit
     *         {@code < 0}:  if {@code INFO = -i}, the i-th argument had an illegal value
     *         {@code > 0}:  if {@code INFO = i}, the leading minor of order i of A is not
     *               positive definite, so the factorization could not be
     *               completed, and the solution has not been computed.
     */
    static int posv(Layout layout, UPLO uplo, int n, int nrhs, DoubleBuffer A, int lda, DoubleBuffer B, int ldb) {
        var A_ = MemorySegment.ofBuffer(A);
        var B_ = MemorySegment.ofBuffer(B);
        return LAPACKE_dposv(layout.lapack(), uplo.lapack(), n, nrhs, A_, lda, B_, ldb);
    }

    /**
     * Solves a real system of linear equations.
     * <pre>{@code
     *     A * X = B
     * }</pre>
     * where A is an N-by-N symmetric positive definite matrix and X and B are N-by-NRHS matrices.
     * <p>
     * The Cholesky decomposition is used to factor A as
     * <pre>{@code
     *     A = U<sup>T</sup>* U,  if UPLO = 'U'
     * }</pre>
     * or
     * <pre>{@code
     *     A = L * L<sup>T</sup>,  if UPLO = 'L'
     * }</pre>
     * where U is an upper triangular matrix and L is a lower triangular
     * matrix.  The factored form of A is then used to solve the system of
     * equations A * X = B.
     *
     * @param layout The matrix layout.
     *
     * @param uplo The upper or lower triangular part of the matrix A is
     *             to be referenced.
     *
     * @param n The number of linear equations, i.e., the order of the matrix A.
     *
     * @param nrhs The number of right hand sides, i.e., the number of columns
     *             of the matrix B.
     *
     * @param A The symmetric matrix of dimension (LDA, N).
     *          On exit, the factor U or L from the Cholesky factorization
     *          A = U<sup>T</sup>*U or A = L*L<sup>T</sup>.
     *
     * @param lda The leading dimension of the matrix A. {@code LDA >= max(1,N)}.
     *
     * @param B On entry, the N-by-NRHS matrix of right hand side matrix B.
     *          On exit, if INFO = 0, the N-by-NRHS solution matrix X.
     *
     * @param ldb The leading dimension of the matrix B. {@code LDB >= max(1,N)}.
     *
     * @return INFO flag.
     *         {@code = 0}:  successful exit
     *         {@code < 0}:  if {@code INFO = -i}, the i-th argument had an illegal value
     *         {@code > 0}:  if {@code INFO = i}, the leading minor of order i of A is not
     *               positive definite, so the factorization could not be
     *               completed, and the solution has not been computed.
     */
    static int posv(Layout layout, UPLO uplo, int n, int nrhs, float[] A, int lda, float[] B, int ldb) {
        var A_ = MemorySegment.ofArray(A);
        var B_ = MemorySegment.ofArray(B);
        return LAPACKE_sposv(layout.lapack(), uplo.lapack(), n, nrhs, A_, lda, B_, ldb);
    }

    /**
     * Solves a real system of linear equations.
     * <pre>{@code
     *     A * X = B
     * }</pre>
     * where A is an N-by-N symmetric positive definite matrix and X and B are N-by-NRHS matrices.
     * <p>
     * The Cholesky decomposition is used to factor A as
     * <pre>{@code
     *     A = U<sup>T</sup>* U,  if UPLO = 'U'
     * }</pre>
     * or
     * <pre>{@code
     *     A = L * L<sup>T</sup>,  if UPLO = 'L'
     * }</pre>
     * where U is an upper triangular matrix and L is a lower triangular
     * matrix.  The factored form of A is then used to solve the system of
     * equations A * X = B.
     *
     * @param layout The matrix layout.
     *
     * @param uplo The upper or lower triangular part of the matrix A is
     *             to be referenced.
     *
     * @param n The number of linear equations, i.e., the order of the matrix A.
     *
     * @param nrhs The number of right hand sides, i.e., the number of columns
     *             of the matrix B.
     *
     * @param A The symmetric matrix of dimension (LDA, N).
     *          On exit, the factor U or L from the Cholesky factorization
     *          A = U<sup>T</sup>*U or A = L*L<sup>T</sup>.
     *
     * @param lda The leading dimension of the matrix A. {@code LDA >= max(1,N)}.
     *
     * @param B On entry, the N-by-NRHS matrix of right hand side matrix B.
     *          On exit, if INFO = 0, the N-by-NRHS solution matrix X.
     *
     * @param ldb The leading dimension of the matrix B. {@code LDB >= max(1,N)}.
     *
     * @return INFO flag.
     *         {@code = 0}:  successful exit
     *         {@code < 0}:  if {@code INFO = -i}, the i-th argument had an illegal value
     *         {@code > 0}:  if {@code INFO = i}, the leading minor of order i of A is not
     *               positive definite, so the factorization could not be
     *               completed, and the solution has not been computed.
     */
    static int posv(Layout layout, UPLO uplo, int n, int nrhs, FloatBuffer A, int lda, FloatBuffer B, int ldb) {
        var A_ = MemorySegment.ofBuffer(A);
        var B_ = MemorySegment.ofBuffer(B);
        return LAPACKE_sposv(layout.lapack(), uplo.lapack(), n, nrhs, A_, lda, B_, ldb);
    }

    /**
     * Solves a real system of linear equations.
     * <pre>{@code
     *     A * X = B
     * }</pre>
     * where A is an N-by-N symmetric positive definite matrix and X and B are N-by-NRHS matrices.
     * <p>
     * The Cholesky decomposition is used to factor A as
     * <pre>{@code
     *     A = U<sup>T</sup>* U,  if UPLO = 'U'
     * }</pre>
     * or
     * <pre>{@code
     *     A = L * L<sup>T</sup>,  if UPLO = 'L'
     * }</pre>
     * where U is an upper triangular matrix and L is a lower triangular
     * matrix.  The factored form of A is then used to solve the system of
     * equations A * X = B.
     *
     * @param layout The matrix layout.
     *
     * @param uplo The upper or lower triangular part of the matrix A is
     *             to be referenced.
     *
     * @param n The number of linear equations, i.e., the order of the matrix A.
     *
     * @param nrhs The number of right hand sides, i.e., the number of columns
     *             of the matrix B.
     *
     * @param A The symmetric packed matrix.
     *          On exit, the factor U or L from the Cholesky factorization
     *          A = U<sup>T</sup>*U or A = L*L<sup>T</sup>, in the same storage format as A.
     *
     * @param B On entry, the N-by-NRHS matrix of right hand side matrix B.
     *          On exit, if INFO = 0, the N-by-NRHS solution matrix X.
     *
     * @param ldb The leading dimension of the matrix B. {@code LDB >= max(1,N)}.
     *
     * @return INFO flag.
     *         {@code = 0}:  successful exit
     *         {@code < 0}:  if {@code INFO = -i}, the i-th argument had an illegal value
     *         {@code > 0}:  if {@code INFO = i}, the leading minor of order i of A is not
     *               positive definite, so the factorization could not be
     *               completed, and the solution has not been computed.
     */
    static int ppsv(Layout layout, UPLO uplo, int n, int nrhs, double[] A, double[] B, int ldb) {
        var A_ = MemorySegment.ofArray(A);
        var B_ = MemorySegment.ofArray(B);
        return LAPACKE_dppsv(layout.lapack(), uplo.lapack(), n, nrhs, A_, B_, ldb);
    }

    /**
     * Solves a real system of linear equations.
     * <pre>{@code
     *     A * X = B
     * }</pre>
     * where A is an N-by-N symmetric positive definite matrix and X and B are N-by-NRHS matrices.
     * <p>
     * The Cholesky decomposition is used to factor A as
     * <pre>{@code
     *     A = U<sup>T</sup>* U,  if UPLO = 'U'
     * }</pre>
     * or
     * <pre>{@code
     *     A = L * L<sup>T</sup>,  if UPLO = 'L'
     * }</pre>
     * where U is an upper triangular matrix and L is a lower triangular
     * matrix.  The factored form of A is then used to solve the system of
     * equations A * X = B.
     *
     * @param layout The matrix layout.
     *
     * @param uplo The upper or lower triangular part of the matrix A is
     *             to be referenced.
     *
     * @param n The number of linear equations, i.e., the order of the matrix A.
     *
     * @param nrhs The number of right hand sides, i.e., the number of columns
     *             of the matrix B.
     *
     * @param A The symmetric packed matrix.
     *          On exit, the factor U or L from the Cholesky factorization
     *          A = U<sup>T</sup>*U or A = L*L<sup>T</sup>, in the same storage format as A.
     *
     * @param B On entry, the N-by-NRHS matrix of right hand side matrix B.
     *          On exit, if INFO = 0, the N-by-NRHS solution matrix X.
     *
     * @param ldb The leading dimension of the matrix B. {@code LDB >= max(1,N)}.
     *
     * @return INFO flag.
     *         {@code = 0}:  successful exit
     *         {@code < 0}:  if {@code INFO = -i}, the i-th argument had an illegal value
     *         {@code > 0}:  if {@code INFO = i}, the leading minor of order i of A is not
     *               positive definite, so the factorization could not be
     *               completed, and the solution has not been computed.
     */
    static int ppsv(Layout layout, UPLO uplo, int n, int nrhs, DoubleBuffer A, DoubleBuffer B, int ldb) {
        var A_ = MemorySegment.ofBuffer(A);
        var B_ = MemorySegment.ofBuffer(B);
        return LAPACKE_dppsv(layout.lapack(), uplo.lapack(), n, nrhs, A_, B_, ldb);
    }

    /**
     * Solves a real system of linear equations.
     * <pre>{@code
     *     A * X = B
     * }</pre>
     * where A is an N-by-N symmetric positive definite matrix and X and B are N-by-NRHS matrices.
     * <p>
     * The Cholesky decomposition is used to factor A as
     * <pre>{@code
     *     A = U<sup>T</sup>* U,  if UPLO = 'U'
     * }</pre>
     * or
     * <pre>{@code
     *     A = L * L<sup>T</sup>,  if UPLO = 'L'
     * }</pre>
     * where U is an upper triangular matrix and L is a lower triangular
     * matrix.  The factored form of A is then used to solve the system of
     * equations A * X = B.
     *
     * @param layout The matrix layout.
     *
     * @param uplo The upper or lower triangular part of the matrix A is
     *             to be referenced.
     *
     * @param n The number of linear equations, i.e., the order of the matrix A.
     *
     * @param nrhs The number of right hand sides, i.e., the number of columns
     *             of the matrix B.
     *
     * @param A The symmetric packed matrix.
     *          On exit, the factor U or L from the Cholesky factorization
     *          A = U<sup>T</sup>*U or A = L*L<sup>T</sup>, in the same storage format as A.
     *
     * @param B On entry, the N-by-NRHS matrix of right hand side matrix B.
     *          On exit, if INFO = 0, the N-by-NRHS solution matrix X.
     *
     * @param ldb The leading dimension of the matrix B. {@code LDB >= max(1,N)}.
     *
     * @return INFO flag.
     *         {@code = 0}:  successful exit
     *         {@code < 0}:  if {@code INFO = -i}, the i-th argument had an illegal value
     *         {@code > 0}:  if {@code INFO = i}, the leading minor of order i of A is not
     *               positive definite, so the factorization could not be
     *               completed, and the solution has not been computed.
     */
    static int ppsv(Layout layout, UPLO uplo, int n, int nrhs, float[] A, float[] B, int ldb) {
        var A_ = MemorySegment.ofArray(A);
        var B_ = MemorySegment.ofArray(B);
        return LAPACKE_sppsv(layout.lapack(), uplo.lapack(), n, nrhs, A_, B_, ldb);
    }

    /**
     * Solves a real system of linear equations.
     * <pre>{@code
     *     A * X = B
     * }</pre>
     * where A is an N-by-N symmetric positive definite matrix and X and B are N-by-NRHS matrices.
     * <p>
     * The Cholesky decomposition is used to factor A as
     * <pre>{@code
     *     A = U<sup>T</sup>* U,  if UPLO = 'U'
     * }</pre>
     * or
     * <pre>{@code
     *     A = L * L<sup>T</sup>,  if UPLO = 'L'
     * }</pre>
     * where U is an upper triangular matrix and L is a lower triangular
     * matrix.  The factored form of A is then used to solve the system of
     * equations A * X = B.
     *
     * @param layout The matrix layout.
     *
     * @param uplo The upper or lower triangular part of the matrix A is
     *             to be referenced.
     *
     * @param n The number of linear equations, i.e., the order of the matrix A.
     *
     * @param nrhs The number of right hand sides, i.e., the number of columns
     *             of the matrix B.
     *
     * @param A The symmetric packed matrix.
     *          On exit, the factor U or L from the Cholesky factorization
     *          A = U<sup>T</sup>*U or A = L*L<sup>T</sup>, in the same storage format as A.
     *
     * @param B On entry, the N-by-NRHS matrix of right hand side matrix B.
     *          On exit, if INFO = 0, the N-by-NRHS solution matrix X.
     *
     * @param ldb The leading dimension of the matrix B. {@code LDB >= max(1,N)}.
     *
     * @return INFO flag.
     *         {@code = 0}:  successful exit
     *         {@code < 0}:  if {@code INFO = -i}, the i-th argument had an illegal value
     *         {@code > 0}:  if {@code INFO = i}, the leading minor of order i of A is not
     *               positive definite, so the factorization could not be
     *               completed, and the solution has not been computed.
     */
    static int ppsv(Layout layout, UPLO uplo, int n, int nrhs, FloatBuffer A, FloatBuffer B, int ldb) {
        var A_ = MemorySegment.ofBuffer(A);
        var B_ = MemorySegment.ofBuffer(B);
        return LAPACKE_sppsv(layout.lapack(), uplo.lapack(), n, nrhs, A_, B_, ldb);
    }

    /**
     * Solves a real system of linear equations.
     * <pre>{@code
     *     A * X = B
     * }</pre>
     * where A is an N-by-N band matrix and X and B are N-by-NRHS matrices.
     * <p>
     * The LU decomposition with partial pivoting and row interchanges is
     * used to factor A as
     * <pre>{@code
     *     A = P * L * U
     * }</pre>
     * where P is a permutation matrix, L is unit lower triangular, and U is
     * upper triangular. The factored form of A is then used to solve the
     * system of equations A * X = B.
     *
     * @param layout The matrix layout.
     *
     * @param n The number of linear equations, i.e., the order of the matrix A.
     *
     * @param kl the number of subdiagonal elements of band matrix.
     *
     * @param ku the number of superdiagonal elements of band matrix.
     *
     * @param nrhs The number of right hand sides, i.e., the number of columns
     *             of the matrix B.
     *
     * @param A The matrix of dimension (LDA, N).
     *          On entry, the matrix A in band storage, in rows {@code KL+1} to
     *          {@code 2*KL+KU+1}; rows 1 to KL of the array need not be set.
     *          The j-th column of A is stored in the j-th column of the
     *          matrix AB as follows:
     *          {@code AB(KL+KU+1+i-j,j) = A(i,j)} for {@code max(1,j-KU)<=i<=min(N,j+KL)}
     *          <p>
     *          On exit, details of the factorization: U is stored as an
     *          upper triangular band matrix with {@code KL+KU} superdiagonals in
     *          rows 1 to {@code KL+KU+1}, and the multipliers used during the
     *          factorization are stored in rows {@code KL+KU+2} to {@code 2*KL+KU+1}.
     *
     * @param lda The leading dimension of the matrix A. {@code LDA >= max(1,N)}.
     *
     * @param ipiv The pivot indices that define the permutation matrix P;
     *             row i of the matrix was interchanged with row IPIV(i).
     *
     * @param B On entry, the N-by-NRHS matrix of right hand side matrix B.
     *          On exit, if INFO = 0, the N-by-NRHS solution matrix X.
     *
     * @param ldb The leading dimension of the matrix B. {@code LDB >= max(1,N)}.
     *
     * @return INFO flag.
     *         {@code = 0}:  successful exit
     *         {@code < 0}:  if {@code INFO = -i}, the i-th argument had an illegal value
     *         {@code > 0}:  if {@code INFO = i}, U(i,i) is exactly zero. The factorization
     *               has been completed, but the factor U is exactly
     *               singular, so the solution could not be computed.
     */
    static int gbsv(Layout layout, int n, int kl, int ku, int nrhs, double[] A, int lda, int[] ipiv, double[] B, int ldb) {
        var A_ = MemorySegment.ofArray(A);
        var B_ = MemorySegment.ofArray(B);
        var ipiv_ = MemorySegment.ofArray(ipiv);
        return LAPACKE_dgbsv(layout.lapack(), n, kl, ku, nrhs, A_, lda, ipiv_, B_, ldb);
    }

    /**
     * Solves a real system of linear equations.
     * <pre>{@code
     *     A * X = B
     * }</pre>
     * where A is an N-by-N band matrix and X and B are N-by-NRHS matrices.
     * <p>
     * The LU decomposition with partial pivoting and row interchanges is
     * used to factor A as
     * <pre>{@code
     *     A = P * L * U
     * }</pre>
     * where P is a permutation matrix, L is unit lower triangular, and U is
     * upper triangular. The factored form of A is then used to solve the
     * system of equations A * X = B.
     *
     * @param layout The matrix layout.
     *
     * @param n The number of linear equations, i.e., the order of the matrix A.
     *
     * @param kl the number of subdiagonal elements of band matrix.
     *
     * @param ku the number of superdiagonal elements of band matrix.
     *
     * @param nrhs The number of right hand sides, i.e., the number of columns
     *             of the matrix B.
     *
     * @param A The matrix of dimension (LDA, N).
     *          On entry, the matrix A in band storage, in rows {@code KL+1} to
     *          {@code 2*KL+KU+1}; rows 1 to KL of the array need not be set.
     *          The j-th column of A is stored in the j-th column of the
     *          matrix AB as follows:
     *          {@code AB(KL+KU+1+i-j,j) = A(i,j)} for {@code max(1,j-KU)<=i<=min(N,j+KL)}
     *          <p>
     *          On exit, details of the factorization: U is stored as an
     *          upper triangular band matrix with {@code KL+KU} superdiagonals in
     *          rows 1 to {@code KL+KU+1}, and the multipliers used during the
     *          factorization are stored in rows {@code KL+KU+2} to {@code 2*KL+KU+1}.
     *
     * @param lda The leading dimension of the matrix A. {@code LDA >= max(1,N)}.
     *
     * @param ipiv The pivot indices that define the permutation matrix P;
     *             row i of the matrix was interchanged with row IPIV(i).
     *
     * @param B On entry, the N-by-NRHS matrix of right hand side matrix B.
     *          On exit, if INFO = 0, the N-by-NRHS solution matrix X.
     *
     * @param ldb The leading dimension of the matrix B. {@code LDB >= max(1,N)}.
     *
     * @return INFO flag.
     *         {@code = 0}:  successful exit
     *         {@code < 0}:  if {@code INFO = -i}, the i-th argument had an illegal value
     *         {@code > 0}:  if {@code INFO = i}, U(i,i) is exactly zero. The factorization
     *               has been completed, but the factor U is exactly
     *               singular, so the solution could not be computed.
     */
    static int gbsv(Layout layout, int n, int kl, int ku, int nrhs, DoubleBuffer A, int lda, IntBuffer ipiv, DoubleBuffer B, int ldb) {
        var A_ = MemorySegment.ofBuffer(A);
        var B_ = MemorySegment.ofBuffer(B);
        var ipiv_ = MemorySegment.ofBuffer(ipiv);
        return LAPACKE_dgbsv(layout.lapack(), n, kl, ku, nrhs, A_, lda, ipiv_, B_, ldb);
    }

    /**
     * Solves a real system of linear equations.
     * <pre>{@code
     *     A * X = B
     * }</pre>
     * where A is an N-by-N band matrix and X and B are N-by-NRHS matrices.
     * <p>
     * The LU decomposition with partial pivoting and row interchanges is
     * used to factor A as
     * <pre>{@code
     *     A = P * L * U
     * }</pre>
     * where P is a permutation matrix, L is unit lower triangular, and U is
     * upper triangular. The factored form of A is then used to solve the
     * system of equations A * X = B.
     *
     * @param layout The matrix layout.
     *
     * @param n The number of linear equations, i.e., the order of the matrix A.
     *
     * @param kl the number of subdiagonal elements of band matrix.
     *
     * @param ku the number of superdiagonal elements of band matrix.
     *
     * @param nrhs The number of right hand sides, i.e., the number of columns
     *             of the matrix B.
     *
     * @param A The matrix of dimension (LDA, N).
     *          On entry, the matrix A in band storage, in rows {@code KL+1} to
     *          {@code 2*KL+KU+1}; rows 1 to KL of the array need not be set.
     *          The j-th column of A is stored in the j-th column of the
     *          matrix AB as follows:
     *          {@code AB(KL+KU+1+i-j,j) = A(i,j)} for {@code max(1,j-KU)<=i<=min(N,j+KL)}
     *          <p>
     *          On exit, details of the factorization: U is stored as an
     *          upper triangular band matrix with {@code KL+KU} superdiagonals in
     *          rows 1 to {@code KL+KU+1}, and the multipliers used during the
     *          factorization are stored in rows {@code KL+KU+2} to {@code 2*KL+KU+1}.
     *
     * @param lda The leading dimension of the matrix A. {@code LDA >= max(1,N)}.
     *
     * @param ipiv The pivot indices that define the permutation matrix P;
     *             row i of the matrix was interchanged with row IPIV(i).
     *
     * @param B On entry, the N-by-NRHS matrix of right hand side matrix B.
     *          On exit, if INFO = 0, the N-by-NRHS solution matrix X.
     *
     * @param ldb The leading dimension of the matrix B. {@code LDB >= max(1,N)}.
     *
     * @return INFO flag.
     *         {@code = 0}:  successful exit
     *         {@code < 0}:  if {@code INFO = -i}, the i-th argument had an illegal value
     *         {@code > 0}:  if {@code INFO = i}, U(i,i) is exactly zero. The factorization
     *               has been completed, but the factor U is exactly
     *               singular, so the solution could not be computed.
     */
    static int gbsv(Layout layout, int n, int kl, int ku, int nrhs, float[] A, int lda, int[] ipiv, float[] B, int ldb) {
        var A_ = MemorySegment.ofArray(A);
        var B_ = MemorySegment.ofArray(B);
        var ipiv_ = MemorySegment.ofArray(ipiv);
        return LAPACKE_sgbsv(layout.lapack(), n, kl, ku, nrhs, A_, lda, ipiv_, B_, ldb);
    }

    /**
     * Solves a real system of linear equations.
     * <pre>{@code
     *     A * X = B
     * }</pre>
     * where A is an N-by-N band matrix and X and B are N-by-NRHS matrices.
     * <p>
     * The LU decomposition with partial pivoting and row interchanges is
     * used to factor A as
     * <pre>{@code
     *     A = P * L * U
     * }</pre>
     * where P is a permutation matrix, L is unit lower triangular, and U is
     * upper triangular. The factored form of A is then used to solve the
     * system of equations A * X = B.
     *
     * @param layout The matrix layout.
     *
     * @param n The number of linear equations, i.e., the order of the matrix A.
     *
     * @param kl the number of subdiagonal elements of band matrix.
     *
     * @param ku the number of superdiagonal elements of band matrix.
     *
     * @param nrhs The number of right hand sides, i.e., the number of columns
     *             of the matrix B.
     *
     * @param A The matrix of dimension (LDA, N).
     *          On entry, the matrix A in band storage, in rows {@code KL+1} to
     *          {@code 2*KL+KU+1}; rows 1 to KL of the array need not be set.
     *          The j-th column of A is stored in the j-th column of the
     *          matrix AB as follows:
     *          {@code AB(KL+KU+1+i-j,j) = A(i,j)} for {@code max(1,j-KU)<=i<=min(N,j+KL)}
     *          <p>
     *          On exit, details of the factorization: U is stored as an
     *          upper triangular band matrix with {@code KL+KU} superdiagonals in
     *          rows 1 to {@code KL+KU+1}, and the multipliers used during the
     *          factorization are stored in rows {@code KL+KU+2} to {@code 2*KL+KU+1}.
     *
     * @param lda The leading dimension of the matrix A. {@code LDA >= max(1,N)}.
     *
     * @param ipiv The pivot indices that define the permutation matrix P;
     *             row i of the matrix was interchanged with row IPIV(i).
     *
     * @param B On entry, the N-by-NRHS matrix of right hand side matrix B.
     *          On exit, if INFO = 0, the N-by-NRHS solution matrix X.
     *
     * @param ldb The leading dimension of the matrix B. {@code LDB >= max(1,N)}.
     *
     * @return INFO flag.
     *         {@code = 0}:  successful exit
     *         {@code < 0}:  if {@code INFO = -i}, the i-th argument had an illegal value
     *         {@code > 0}:  if {@code INFO = i}, U(i,i) is exactly zero. The factorization
     *               has been completed, but the factor U is exactly
     *               singular, so the solution could not be computed.
     */
    static int gbsv(Layout layout, int n, int kl, int ku, int nrhs, FloatBuffer A, int lda, IntBuffer ipiv, FloatBuffer B, int ldb) {
        var A_ = MemorySegment.ofBuffer(A);
        var B_ = MemorySegment.ofBuffer(B);
        var ipiv_ = MemorySegment.ofBuffer(ipiv);
        return LAPACKE_sgbsv(layout.lapack(), n, kl, ku, nrhs, A_, lda, ipiv_, B_, ldb);
    }

    /**
     * Solves an overdetermined or underdetermined system, using a QR or LQ
     * factorization of A. It is assumed that A has full rank.
     *
     * @param layout The matrix layout.
     *
     * @param trans The normal or transpose of the matrix A.
     *
     * @param m The number of rows of the matrix A.
     *
     * @param n The number of columns of the matrix A.
     *
     * @param nrhs The number of right hand sides, i.e., the number of columns
     *             of the matrix B.
     *
     * @param A The matrix of dimension (LDA, N).
     *          On exit, the factors L and U from the factorization
     *          A = P*L*U; the unit diagonal elements of L are not stored.
     *
     * @param lda The leading dimension of the matrix A. {@code LDA >= max(1,N)}.
     *
     * @param B On entry, the N-by-NRHS matrix of right hand side matrix B.
     *          On exit, if INFO = 0, the N-by-NRHS solution matrix X.
     *
     * @param ldb The leading dimension of the matrix B. {@code LDB >= max(1,N)}.
     *
     * @return INFO flag.
     *         {@code = 0}:  successful exit
     *         {@code < 0}:  if {@code INFO = -i}, the i-th argument had an illegal value
     *         {@code > 0}:  if INFO =  i, the i-th diagonal element of the
     *               triangular factor of A is zero, so that A does not have
     *               full rank; the least squares solution could not be
     *               computed.
     */
    static int gels(Layout layout, Transpose trans, int m, int n, int nrhs, double[] A, int lda, double[] B, int ldb) {
        var A_ = MemorySegment.ofArray(A);
        var B_ = MemorySegment.ofArray(B);
        return LAPACKE_dgels(layout.lapack(), trans.lapack(), m, n, nrhs, A_, lda, B_, ldb);
    }

    /**
     * Solves an overdetermined or underdetermined system, using a QR or LQ
     * factorization of A. It is assumed that A has full rank.
     *
     * @param layout The matrix layout.
     *
     * @param trans The normal or transpose of the matrix A.
     *
     * @param m The number of rows of the matrix A.
     *
     * @param n The number of columns of the matrix A.
     *
     * @param nrhs The number of right hand sides, i.e., the number of columns
     *             of the matrix B.
     *
     * @param A The matrix of dimension (LDA, N).
     *          On exit, the factors L and U from the factorization
     *          A = P*L*U; the unit diagonal elements of L are not stored.
     *
     * @param lda The leading dimension of the matrix A. {@code LDA >= max(1,N)}.
     *
     * @param B On entry, the N-by-NRHS matrix of right hand side matrix B.
     *          On exit, if INFO = 0, the N-by-NRHS solution matrix X.
     *
     * @param ldb The leading dimension of the matrix B. {@code LDB >= max(1,N)}.
     *
     * @return INFO flag.
     *         {@code = 0}:  successful exit
     *         {@code < 0}:  if {@code INFO = -i}, the i-th argument had an illegal value
     *         {@code > 0}:  if INFO =  i, the i-th diagonal element of the
     *               triangular factor of A is zero, so that A does not have
     *               full rank; the least squares solution could not be
     *               computed.
     */
    static int gels(Layout layout, Transpose trans, int m, int n, int nrhs, DoubleBuffer A, int lda, DoubleBuffer B, int ldb) {
        var A_ = MemorySegment.ofBuffer(A);
        var B_ = MemorySegment.ofBuffer(B);
        return LAPACKE_dgels(layout.lapack(), trans.lapack(), m, n, nrhs, A_, lda, B_, ldb);
    }

    /**
     * Solves an overdetermined or underdetermined system, using a QR or LQ
     * factorization of A. It is assumed that A has full rank.
     *
     * @param layout The matrix layout.
     *
     * @param trans The normal or transpose of the matrix A.
     *
     * @param m The number of rows of the matrix A.
     *
     * @param n The number of columns of the matrix A.
     *
     * @param nrhs The number of right hand sides, i.e., the number of columns
     *             of the matrix B.
     *
     * @param A The matrix of dimension (LDA, N).
     *          On exit, the factors L and U from the factorization
     *          A = P*L*U; the unit diagonal elements of L are not stored.
     *
     * @param lda The leading dimension of the matrix A. {@code LDA >= max(1,N)}.
     *
     * @param B On entry, the N-by-NRHS matrix of right hand side matrix B.
     *          On exit, if INFO = 0, the N-by-NRHS solution matrix X.
     *
     * @param ldb The leading dimension of the matrix B. {@code LDB >= max(1,N)}.
     *
     * @return INFO flag.
     *         {@code = 0}:  successful exit
     *         {@code < 0}:  if {@code INFO = -i}, the i-th argument had an illegal value
     *         {@code > 0}:  if INFO =  i, the i-th diagonal element of the
     *               triangular factor of A is zero, so that A does not have
     *               full rank; the least squares solution could not be
     *               computed.
     */
    static int gels(Layout layout, Transpose trans, int m, int n, int nrhs, float[] A, int lda, float[] B, int ldb) {
        var A_ = MemorySegment.ofArray(A);
        var B_ = MemorySegment.ofArray(B);
        return LAPACKE_sgels(layout.lapack(), trans.lapack(), m, n, nrhs, A_, lda, B_, ldb);
    }

    /**
     * Solves an overdetermined or underdetermined system, using a QR or LQ
     * factorization of A. It is assumed that A has full rank.
     *
     * @param layout The matrix layout.
     *
     * @param trans The normal or transpose of the matrix A.
     *
     * @param m The number of rows of the matrix A.
     *
     * @param n The number of columns of the matrix A.
     *
     * @param nrhs The number of right hand sides, i.e., the number of columns
     *             of the matrix B.
     *
     * @param A The matrix of dimension (LDA, N).
     *          On exit, the factors L and U from the factorization
     *          A = P*L*U; the unit diagonal elements of L are not stored.
     *
     * @param lda The leading dimension of the matrix A. {@code LDA >= max(1,N)}.
     *
     * @param B On entry, the N-by-NRHS matrix of right hand side matrix B.
     *          On exit, if INFO = 0, the N-by-NRHS solution matrix X.
     *
     * @param ldb The leading dimension of the matrix B. {@code LDB >= max(1,N)}.
     *
     * @return INFO flag.
     *         {@code = 0}:  successful exit
     *         {@code < 0}:  if {@code INFO = -i}, the i-th argument had an illegal value
     *         {@code > 0}:  if INFO =  i, the i-th diagonal element of the
     *               triangular factor of A is zero, so that A does not have
     *               full rank; the least squares solution could not be
     *               computed.
     */
    static int gels(Layout layout, Transpose trans, int m, int n, int nrhs, FloatBuffer A, int lda, FloatBuffer B, int ldb) {
        var A_ = MemorySegment.ofBuffer(A);
        var B_ = MemorySegment.ofBuffer(B);
        return LAPACKE_sgels(layout.lapack(), trans.lapack(), m, n, nrhs, A_, lda, B_, ldb);
    }

    /**
     * Solves an overdetermined or underdetermined system, using a complete
     * orthogonal factorization of A. A may be rank-deficient.
     *
     * @param layout The matrix layout.
     *
     * @param m The number of rows of the matrix A.
     *
     * @param n The number of columns of the matrix A.
     *
     * @param nrhs The number of right hand sides, i.e., the number of columns
     *             of the matrix B.
     *
     * @param A The matrix of dimension (LDA, N).
     *          On exit, A is overwritten by the factorization.
     *
     * @param lda The leading dimension of the matrix A. {@code LDA >= max(1,M)}.
     *
     * @param B The right hand side matrix of dimension (LDB, NRHS).
     *          On exit, if INFO = 0, B is overwritten by the solution
     *          vectors, stored columnwise.
     *
     * @param ldb The leading dimension of the matrix B. {@code LDB >= max(1,M,N)}.
     *
     * @param jpvt On entry, if JPVT(i) != 0, the i-th column of A is permuted
     *             to the front of AP, otherwise column i is a free column.
     *             On exit, if JPVT(i) = k, then the i-th column of AP
     *             was the k-th column of A.
     *
     * @param rcond RCOND is used to determine the effective rank of A, which
     *              is defined as the order of the largest leading triangular
     *              submatrix R11 in the QR factorization with pivoting of A,
     *              whose estimated condition number {@code < 1/RCOND}.
     *
     * @param rank The effective rank of A, i.e., the order of the submatrix
     *             R11.  This is the same as the order of the submatrix T11
     *             in the complete orthogonal factorization of A.
     *
     * @return INFO flag.
     *         {@code = 0}:  successful exit
     *         {@code < 0}:  if {@code INFO = -i}, the i-th argument had an illegal value
     *         {@code > 0}:  if INFO =  i, the i-th diagonal element of the
     *               triangular factor of A is zero, so that A does not have
     *               full rank; the least squares solution could not be
     *               computed.
     */
    static int gelsy(Layout layout, int m, int n, int nrhs, double[] A, int lda,
                     double[] B, int ldb, int[] jpvt, double rcond, int[] rank) {
        var A_ = MemorySegment.ofArray(A);
        var B_ = MemorySegment.ofArray(B);
        var jpvt_ = MemorySegment.ofArray(jpvt);
        var rank_ = MemorySegment.ofArray(rank);
        return LAPACKE_dgelsy(layout.lapack(), m, n, nrhs, A_, lda, B_, ldb, jpvt_, rcond, rank_);
    }

    /**
     * Solves an overdetermined or underdetermined system, using a complete
     * orthogonal factorization of A. A may be rank-deficient.
     *
     * @param layout The matrix layout.
     *
     * @param m The number of rows of the matrix A.
     *
     * @param n The number of columns of the matrix A.
     *
     * @param nrhs The number of right hand sides, i.e., the number of columns
     *             of the matrix B.
     *
     * @param A The matrix of dimension (LDA, N).
     *          On exit, A is overwritten by the factorization.
     *
     * @param lda The leading dimension of the matrix A. {@code LDA >= max(1,M)}.
     *
     * @param B The right hand side matrix of dimension (LDB, NRHS).
     *          On exit, if INFO = 0, B is overwritten by the solution
     *          vectors, stored columnwise.
     *
     * @param ldb The leading dimension of the matrix B. {@code LDB >= max(1,M,N)}.
     *
     * @param jpvt On entry, if JPVT(i) != 0, the i-th column of A is permuted
     *             to the front of AP, otherwise column i is a free column.
     *             On exit, if JPVT(i) = k, then the i-th column of AP
     *             was the k-th column of A.
     *
     * @param rcond RCOND is used to determine the effective rank of A, which
     *              is defined as the order of the largest leading triangular
     *              submatrix R11 in the QR factorization with pivoting of A,
     *              whose estimated condition number {@code < 1/RCOND}.
     *
     * @param rank The effective rank of A, i.e., the order of the submatrix
     *             R11.  This is the same as the order of the submatrix T11
     *             in the complete orthogonal factorization of A.
     *
     * @return INFO flag.
     *         {@code = 0}:  successful exit
     *         {@code < 0}:  if {@code INFO = -i}, the i-th argument had an illegal value
     *         {@code > 0}:  if INFO =  i, the i-th diagonal element of the
     *               triangular factor of A is zero, so that A does not have
     *               full rank; the least squares solution could not be
     *               computed.
     */
    static int gelsy(Layout layout, int m, int n, int nrhs, DoubleBuffer A, int lda,
                     DoubleBuffer B, int ldb, IntBuffer jpvt, double rcond, IntBuffer rank) {
        var A_ = MemorySegment.ofBuffer(A);
        var B_ = MemorySegment.ofBuffer(B);
        var jpvt_ = MemorySegment.ofBuffer(jpvt);
        var rank_ = MemorySegment.ofBuffer(rank);
        return LAPACKE_dgelsy(layout.lapack(), m, n, nrhs, A_, lda, B_, ldb, jpvt_, rcond, rank_);
    }

    /**
     * Solves an overdetermined or underdetermined system, using a complete
     * orthogonal factorization of A. A may be rank-deficient.
     *
     * @param layout The matrix layout.
     *
     * @param m The number of rows of the matrix A.
     *
     * @param n The number of columns of the matrix A.
     *
     * @param nrhs The number of right hand sides, i.e., the number of columns
     *             of the matrix B.
     *
     * @param A The matrix of dimension (LDA, N).
     *          On exit, A is overwritten by the factorization.
     *
     * @param lda The leading dimension of the matrix A. {@code LDA >= max(1,M)}.
     *
     * @param B The right hand side matrix of dimension (LDB, NRHS).
     *          On exit, if INFO = 0, B is overwritten by the solution
     *          vectors, stored columnwise.
     *
     * @param ldb The leading dimension of the matrix B. {@code LDB >= max(1,M,N)}.
     *
     * @param jpvt On entry, if JPVT(i) != 0, the i-th column of A is permuted
     *             to the front of AP, otherwise column i is a free column.
     *             On exit, if JPVT(i) = k, then the i-th column of AP
     *             was the k-th column of A.
     *
     * @param rcond RCOND is used to determine the effective rank of A, which
     *              is defined as the order of the largest leading triangular
     *              submatrix R11 in the QR factorization with pivoting of A,
     *              whose estimated condition number {@code < 1/RCOND}.
     *
     * @param rank The effective rank of A, i.e., the order of the submatrix
     *             R11.  This is the same as the order of the submatrix T11
     *             in the complete orthogonal factorization of A.
     *
     * @return INFO flag.
     *         {@code = 0}:  successful exit
     *         {@code < 0}:  if {@code INFO = -i}, the i-th argument had an illegal value
     *         {@code > 0}:  if INFO =  i, the i-th diagonal element of the
     *               triangular factor of A is zero, so that A does not have
     *               full rank; the least squares solution could not be
     *               computed.
     */
    static int gelsy(Layout layout, int m, int n, int nrhs, float[] A, int lda,
                     float[] B, int ldb, int[] jpvt, float rcond, int[] rank) {
        var A_ = MemorySegment.ofArray(A);
        var B_ = MemorySegment.ofArray(B);
        var jpvt_ = MemorySegment.ofArray(jpvt);
        var rank_ = MemorySegment.ofArray(rank);
        return LAPACKE_sgelsy(layout.lapack(), m, n, nrhs, A_, lda, B_, ldb, jpvt_, rcond, rank_);
    }

    /**
     * Solves an overdetermined or underdetermined system, using a complete
     * orthogonal factorization of A. A may be rank-deficient.
     *
     * @param layout The matrix layout.
     *
     * @param m The number of rows of the matrix A.
     *
     * @param n The number of columns of the matrix A.
     *
     * @param nrhs The number of right hand sides, i.e., the number of columns
     *             of the matrix B.
     *
     * @param A The matrix of dimension (LDA, N).
     *          On exit, A is overwritten by the factorization.
     *
     * @param lda The leading dimension of the matrix A. {@code LDA >= max(1,M)}.
     *
     * @param B The right hand side matrix of dimension (LDB, NRHS).
     *          On exit, if INFO = 0, B is overwritten by the solution
     *          vectors, stored columnwise.
     *
     * @param ldb The leading dimension of the matrix B. {@code LDB >= max(1,M,N)}.
     *
     * @param jpvt On entry, if JPVT(i) != 0, the i-th column of A is permuted
     *             to the front of AP, otherwise column i is a free column.
     *             On exit, if JPVT(i) = k, then the i-th column of AP
     *             was the k-th column of A.
     *
     * @param rcond RCOND is used to determine the effective rank of A, which
     *              is defined as the order of the largest leading triangular
     *              submatrix R11 in the QR factorization with pivoting of A,
     *              whose estimated condition number {@code < 1/RCOND}.
     *
     * @param rank The effective rank of A, i.e., the order of the submatrix
     *             R11.  This is the same as the order of the submatrix T11
     *             in the complete orthogonal factorization of A.
     *
     * @return INFO flag.
     *         {@code = 0}:  successful exit
     *         {@code < 0}:  if {@code INFO = -i}, the i-th argument had an illegal value
     *         {@code > 0}:  if INFO =  i, the i-th diagonal element of the
     *               triangular factor of A is zero, so that A does not have
     *               full rank; the least squares solution could not be
     *               computed.
     */
    static int gelsy(Layout layout, int m, int n, int nrhs, FloatBuffer A, int lda,
                     FloatBuffer B, int ldb, IntBuffer jpvt, float rcond, IntBuffer rank) {
        var A_ = MemorySegment.ofBuffer(A);
        var B_ = MemorySegment.ofBuffer(B);
        var jpvt_ = MemorySegment.ofBuffer(jpvt);
        var rank_ = MemorySegment.ofBuffer(rank);
        return LAPACKE_sgelsy(layout.lapack(), m, n, nrhs, A_, lda, B_, ldb, jpvt_, rcond, rank_);
    }

    /**
     * Solves an overdetermined or underdetermined system, using the singular
     * value decomposition (SVD) of A. A may be rank-deficient.
     * <p>
     * The effective rank of A is determined by treating as zero those
     * singular values which are less than RCOND times the largest singular
     * value.
     *
     * @param layout The matrix layout.
     *
     * @param m The number of rows of the matrix A.
     *
     * @param n The number of columns of the matrix A.
     *
     * @param nrhs The number of right hand sides, i.e., the number of columns
     *             of the matrix B.
     *
     * @param A The matrix of dimension (LDA, N).
     *          On exit, A is overwritten by the factorization.
     *
     * @param lda The leading dimension of the matrix A. {@code LDA >= max(1,M)}.
     *
     * @param B The right hand side matrix of dimension (LDB, NRHS).
     *          On exit, if INFO = 0, B is overwritten by the solution
     *          vectors, stored columnwise.
     *
     * @param ldb The leading dimension of the matrix B. {@code LDB >= max(1,M,N)}.
     *
     * @param s The singular values of A in decreasing order.
     *          The condition number of A in the 2-norm = {@code S(1)/S(min(m,n))}.
     *
     * @param rcond RCOND is used to determine the effective rank of A.
     *              Singular values {@code S(i) <= {@code RCOND*S(1)}} are treated as zero.
     *              If {@code RCOND < 0}, machine precision is used instead.
     *
     * @param rank The effective rank of A, i.e., the number of singular values
     *             which are greater than {@code RCOND*S(1)}.
     *
     * @return INFO flag.
     *         {@code = 0}:  successful exit
     *         {@code < 0}:  if {@code INFO = -i}, the i-th argument had an illegal value
     *         {@code > 0}:  if INFO =  i, the i-th diagonal element of the
     *               triangular factor of A is zero, so that A does not have
     *               full rank; the least squares solution could not be
     *               computed.
     */
    static int gelss(Layout layout, int m, int n, int nrhs, double[] A, int lda,
                     double[] B, int ldb, double[] s, double rcond, int[] rank) {
        var A_ = MemorySegment.ofArray(A);
        var B_ = MemorySegment.ofArray(B);
        var s_ = MemorySegment.ofArray(s);
        var rank_ = MemorySegment.ofArray(rank);
        return LAPACKE_dgelss(layout.lapack(), m, n, nrhs, A_, lda, B_, ldb, s_, rcond, rank_);
    }

    /**
     * Solves an overdetermined or underdetermined system, using the singular
     * value decomposition (SVD) of A. A may be rank-deficient.
     * <p>
     * The effective rank of A is determined by treating as zero those
     * singular values which are less than RCOND times the largest singular
     * value.
     *
     * @param layout The matrix layout.
     *
     * @param m The number of rows of the matrix A.
     *
     * @param n The number of columns of the matrix A.
     *
     * @param nrhs The number of right hand sides, i.e., the number of columns
     *             of the matrix B.
     *
     * @param A The matrix of dimension (LDA, N).
     *          On exit, A is overwritten by the factorization.
     *
     * @param lda The leading dimension of the matrix A. {@code LDA >= max(1,M)}.
     *
     * @param B The right hand side matrix of dimension (LDB, NRHS).
     *          On exit, if INFO = 0, B is overwritten by the solution
     *          vectors, stored columnwise.
     *
     * @param ldb The leading dimension of the matrix B. {@code LDB >= max(1,M,N)}.
     *
     * @param s The singular values of A in decreasing order.
     *          The condition number of A in the 2-norm = {@code S(1)/S(min(m,n))}.
     *
     * @param rcond RCOND is used to determine the effective rank of A.
     *              Singular values {@code S(i) <= {@code RCOND*S(1)}} are treated as zero.
     *              If {@code RCOND < 0}, machine precision is used instead.
     *
     * @param rank The effective rank of A, i.e., the number of singular values
     *             which are greater than {@code RCOND*S(1)}.
     *
     * @return INFO flag.
     *         {@code = 0}:  successful exit
     *         {@code < 0}:  if {@code INFO = -i}, the i-th argument had an illegal value
     *         {@code > 0}:  if INFO =  i, the i-th diagonal element of the
     *               triangular factor of A is zero, so that A does not have
     *               full rank; the least squares solution could not be
     *               computed.
     */
    static int gelss(Layout layout, int m, int n, int nrhs, DoubleBuffer A, int lda,
                     DoubleBuffer B, int ldb, DoubleBuffer s, double rcond, IntBuffer rank) {
        var A_ = MemorySegment.ofBuffer(A);
        var B_ = MemorySegment.ofBuffer(B);
        var s_ = MemorySegment.ofBuffer(s);
        var rank_ = MemorySegment.ofBuffer(rank);
        return LAPACKE_dgelss(layout.lapack(), m, n, nrhs, A_, lda, B_, ldb, s_, rcond, rank_);
    }

    /**
     * Solves an overdetermined or underdetermined system, using the singular
     * value decomposition (SVD) of A. A may be rank-deficient.
     * <p>
     * The effective rank of A is determined by treating as zero those
     * singular values which are less than RCOND times the largest singular
     * value.
     *
     * @param layout The matrix layout.
     *
     * @param m The number of rows of the matrix A.
     *
     * @param n The number of columns of the matrix A.
     *
     * @param nrhs The number of right hand sides, i.e., the number of columns
     *             of the matrix B.
     *
     * @param A The matrix of dimension (LDA, N).
     *          On exit, A is overwritten by the factorization.
     *
     * @param lda The leading dimension of the matrix A. {@code LDA >= max(1,M)}.
     *
     * @param B The right hand side matrix of dimension (LDB, NRHS).
     *          On exit, if INFO = 0, B is overwritten by the solution
     *          vectors, stored columnwise.
     *
     * @param ldb The leading dimension of the matrix B. {@code LDB >= max(1,M,N)}.
     *
     * @param s The singular values of A in decreasing order.
     *          The condition number of A in the 2-norm = {@code S(1)/S(min(m,n))}.
     *
     * @param rcond RCOND is used to determine the effective rank of A.
     *              Singular values {@code S(i) <= {@code RCOND*S(1)}} are treated as zero.
     *              If {@code RCOND < 0}, machine precision is used instead.
     *
     * @param rank The effective rank of A, i.e., the number of singular values
     *             which are greater than {@code RCOND*S(1)}.
     *
     * @return INFO flag.
     *         {@code = 0}:  successful exit
     *         {@code < 0}:  if {@code INFO = -i}, the i-th argument had an illegal value
     *         {@code > 0}:  if INFO =  i, the i-th diagonal element of the
     *               triangular factor of A is zero, so that A does not have
     *               full rank; the least squares solution could not be
     *               computed.
     */
    static int gelss(Layout layout, int m, int n, int nrhs, float[] A, int lda,
                     float[] B, int ldb, float[] s, float rcond, int[] rank) {
        var A_ = MemorySegment.ofArray(A);
        var B_ = MemorySegment.ofArray(B);
        var s_ = MemorySegment.ofArray(s);
        var rank_ = MemorySegment.ofArray(rank);
        return LAPACKE_sgelss(layout.lapack(), m, n, nrhs, A_, lda, B_, ldb, s_, rcond, rank_);
    }

    /**
     * Solves an overdetermined or underdetermined system, using the singular
     * value decomposition (SVD) of A. A may be rank-deficient.
     * <p>
     * The effective rank of A is determined by treating as zero those
     * singular values which are less than RCOND times the largest singular
     * value.
     *
     * @param layout The matrix layout.
     *
     * @param m The number of rows of the matrix A.
     *
     * @param n The number of columns of the matrix A.
     *
     * @param nrhs The number of right hand sides, i.e., the number of columns
     *             of the matrix B.
     *
     * @param A The matrix of dimension (LDA, N).
     *          On exit, A is overwritten by the factorization.
     *
     * @param lda The leading dimension of the matrix A. {@code LDA >= max(1,M)}.
     *
     * @param B The right hand side matrix of dimension (LDB, NRHS).
     *          On exit, if INFO = 0, B is overwritten by the solution
     *          vectors, stored columnwise.
     *
     * @param ldb The leading dimension of the matrix B. {@code LDB >= max(1,M,N)}.
     *
     * @param s The singular values of A in decreasing order.
     *          The condition number of A in the 2-norm = {@code S(1)/S(min(m,n))}.
     *
     * @param rcond RCOND is used to determine the effective rank of A.
     *              Singular values {@code S(i) <= {@code RCOND*S(1)}} are treated as zero.
     *              If {@code RCOND < 0}, machine precision is used instead.
     *
     * @param rank The effective rank of A, i.e., the number of singular values
     *             which are greater than {@code RCOND*S(1)}.
     *
     * @return INFO flag.
     *         {@code = 0}:  successful exit
     *         {@code < 0}:  if {@code INFO = -i}, the i-th argument had an illegal value
     *         {@code > 0}:  if INFO =  i, the i-th diagonal element of the
     *               triangular factor of A is zero, so that A does not have
     *               full rank; the least squares solution could not be
     *               computed.
     */
    static int gelss(Layout layout, int m, int n, int nrhs, FloatBuffer A, int lda,
                     FloatBuffer B, int ldb, FloatBuffer s, float rcond, IntBuffer rank) {
        var A_ = MemorySegment.ofBuffer(A);
        var B_ = MemorySegment.ofBuffer(B);
        var s_ = MemorySegment.ofBuffer(s);
        var rank_ = MemorySegment.ofBuffer(rank);
        return LAPACKE_sgelss(layout.lapack(), m, n, nrhs, A_, lda, B_, ldb, s_, rcond, rank_);
    }

    /**
     * Solves an overdetermined or underdetermined system, using a divide
     * and conquer algorithm with the singular value decomposition (SVD) of A.
     * A may be rank-deficient.
     * <p>
     * The effective rank of A is determined by treating as zero those
     * singular values which are less than RCOND times the largest singular
     * value.
     *
     * @param layout The matrix layout.
     *
     * @param m The number of rows of the matrix A.
     *
     * @param n The number of columns of the matrix A.
     *
     * @param nrhs The number of right hand sides, i.e., the number of columns
     *             of the matrix B.
     *
     * @param A The matrix of dimension (LDA, N).
     *          On exit, A is overwritten by the factorization.
     *
     * @param lda The leading dimension of the matrix A. {@code LDA >= max(1,M)}.
     *
     * @param B The right hand side matrix of dimension (LDB, NRHS).
     *          On exit, if INFO = 0, B is overwritten by the solution
     *          vectors, stored columnwise.
     *
     * @param ldb The leading dimension of the matrix B. {@code LDB >= max(1,M,N)}.
     *
     * @param s The singular values of A in decreasing order.
     *          The condition number of A in the 2-norm = {@code S(1)/S(min(m,n))}.
     *
     * @param rcond RCOND is used to determine the effective rank of A.
     *              Singular values {@code S(i) <= {@code RCOND*S(1)}} are treated as zero.
     *              If {@code RCOND < 0}, machine precision is used instead.
     *
     * @param rank The effective rank of A, i.e., the number of singular values
     *             which are greater than {@code RCOND*S(1)}.
     *
     * @return INFO flag.
     *         {@code = 0}:  successful exit
     *         {@code < 0}:  if {@code INFO = -i}, the i-th argument had an illegal value
     *         {@code > 0}:  if INFO =  i, the i-th diagonal element of the
     *               triangular factor of A is zero, so that A does not have
     *               full rank; the least squares solution could not be
     *               computed.
     */
    static int gelsd(Layout layout, int m, int n, int nrhs, double[] A, int lda,
                     double[] B, int ldb, double[] s, double rcond, int[] rank) {
        var A_ = MemorySegment.ofArray(A);
        var B_ = MemorySegment.ofArray(B);
        var s_ = MemorySegment.ofArray(s);
        var rank_ = MemorySegment.ofArray(rank);
        return LAPACKE_dgelsd(layout.lapack(), m, n, nrhs, A_, lda, B_, ldb, s_, rcond, rank_);
    }

    /**
     * Solves an overdetermined or underdetermined system, using a divide
     * and conquer algorithm with the singular value decomposition (SVD) of A.
     * A may be rank-deficient.
     * <p>
     * The effective rank of A is determined by treating as zero those
     * singular values which are less than RCOND times the largest singular
     * value.
     *
     * @param layout The matrix layout.
     *
     * @param m The number of rows of the matrix A.
     *
     * @param n The number of columns of the matrix A.
     *
     * @param nrhs The number of right hand sides, i.e., the number of columns
     *             of the matrix B.
     *
     * @param A The matrix of dimension (LDA, N).
     *          On exit, A is overwritten by the factorization.
     *
     * @param lda The leading dimension of the matrix A. {@code LDA >= max(1,M)}.
     *
     * @param B The right hand side matrix of dimension (LDB, NRHS).
     *          On exit, if INFO = 0, B is overwritten by the solution
     *          vectors, stored columnwise.
     *
     * @param ldb The leading dimension of the matrix B. {@code LDB >= max(1,M,N)}.
     *
     * @param s The singular values of A in decreasing order.
     *          The condition number of A in the 2-norm = {@code S(1)/S(min(m,n))}.
     *
     * @param rcond RCOND is used to determine the effective rank of A.
     *              Singular values {@code S(i) <= {@code RCOND*S(1)}} are treated as zero.
     *              If {@code RCOND < 0}, machine precision is used instead.
     *
     * @param rank The effective rank of A, i.e., the number of singular values
     *             which are greater than {@code RCOND*S(1)}.
     *
     * @return INFO flag.
     *         {@code = 0}:  successful exit
     *         {@code < 0}:  if {@code INFO = -i}, the i-th argument had an illegal value
     *         {@code > 0}:  if INFO =  i, the i-th diagonal element of the
     *               triangular factor of A is zero, so that A does not have
     *               full rank; the least squares solution could not be
     *               computed.
     */
    static int gelsd(Layout layout, int m, int n, int nrhs, DoubleBuffer A, int lda,
                     DoubleBuffer B, int ldb, DoubleBuffer s, double rcond, IntBuffer rank) {
        var A_ = MemorySegment.ofBuffer(A);
        var B_ = MemorySegment.ofBuffer(B);
        var s_ = MemorySegment.ofBuffer(s);
        var rank_ = MemorySegment.ofBuffer(rank);
        return LAPACKE_dgelsd(layout.lapack(), m, n, nrhs, A_, lda, B_, ldb, s_, rcond, rank_);
    }

    /**
     * Solves an overdetermined or underdetermined system, using a divide
     * and conquer algorithm with the singular value decomposition (SVD) of A.
     * A may be rank-deficient.
     * <p>
     * The effective rank of A is determined by treating as zero those
     * singular values which are less than RCOND times the largest singular
     * value.
     *
     * @param layout The matrix layout.
     *
     * @param m The number of rows of the matrix A.
     *
     * @param n The number of columns of the matrix A.
     *
     * @param nrhs The number of right hand sides, i.e., the number of columns
     *             of the matrix B.
     *
     * @param A The matrix of dimension (LDA, N).
     *          On exit, A is overwritten by the factorization.
     *
     * @param lda The leading dimension of the matrix A. {@code LDA >= max(1,M)}.
     *
     * @param B The right hand side matrix of dimension (LDB, NRHS).
     *          On exit, if INFO = 0, B is overwritten by the solution
     *          vectors, stored columnwise.
     *
     * @param ldb The leading dimension of the matrix B. {@code LDB >= max(1,M,N)}.
     *
     * @param s The singular values of A in decreasing order.
     *          The condition number of A in the 2-norm = {@code S(1)/S(min(m,n))}.
     *
     * @param rcond RCOND is used to determine the effective rank of A.
     *              Singular values {@code S(i) <= {@code RCOND*S(1)}} are treated as zero.
     *              If {@code RCOND < 0}, machine precision is used instead.
     *
     * @param rank The effective rank of A, i.e., the number of singular values
     *             which are greater than {@code RCOND*S(1)}.
     *
     * @return INFO flag.
     *         {@code = 0}:  successful exit
     *         {@code < 0}:  if {@code INFO = -i}, the i-th argument had an illegal value
     *         {@code > 0}:  if INFO =  i, the i-th diagonal element of the
     *               triangular factor of A is zero, so that A does not have
     *               full rank; the least squares solution could not be
     *               computed.
     */
    static int gelsd(Layout layout, int m, int n, int nrhs, float[] A, int lda,
                     float[] B, int ldb, float[] s, float rcond, int[] rank) {
        var A_ = MemorySegment.ofArray(A);
        var B_ = MemorySegment.ofArray(B);
        var s_ = MemorySegment.ofArray(s);
        var rank_ = MemorySegment.ofArray(rank);
        return LAPACKE_sgelsd(layout.lapack(), m, n, nrhs, A_, lda, B_, ldb, s_, rcond, rank_);
    }

    /**
     * Solves an overdetermined or underdetermined system, using a divide
     * and conquer algorithm with the singular value decomposition (SVD) of A.
     * A may be rank-deficient.
     * <p>
     * The effective rank of A is determined by treating as zero those
     * singular values which are less than RCOND times the largest singular
     * value.
     *
     * @param layout The matrix layout.
     *
     * @param m The number of rows of the matrix A.
     *
     * @param n The number of columns of the matrix A.
     *
     * @param nrhs The number of right hand sides, i.e., the number of columns
     *             of the matrix B.
     *
     * @param A The matrix of dimension (LDA, N).
     *          On exit, A is overwritten by the factorization.
     *
     * @param lda The leading dimension of the matrix A. {@code LDA >= max(1,M)}.
     *
     * @param B The right hand side matrix of dimension (LDB, NRHS).
     *          On exit, if INFO = 0, B is overwritten by the solution
     *          vectors, stored columnwise.
     *
     * @param ldb The leading dimension of the matrix B. {@code LDB >= max(1,M,N)}.
     *
     * @param s The singular values of A in decreasing order.
     *          The condition number of A in the 2-norm = {@code S(1)/S(min(m,n))}.
     *
     * @param rcond RCOND is used to determine the effective rank of A.
     *              Singular values {@code S(i) <= {@code RCOND*S(1)}} are treated as zero.
     *              If {@code RCOND < 0}, machine precision is used instead.
     *
     * @param rank The effective rank of A, i.e., the number of singular values
     *             which are greater than {@code RCOND*S(1)}.
     *
     * @return INFO flag.
     *         {@code = 0}:  successful exit
     *         {@code < 0}:  if {@code INFO = -i}, the i-th argument had an illegal value
     *         {@code > 0}:  if INFO =  i, the i-th diagonal element of the
     *               triangular factor of A is zero, so that A does not have
     *               full rank; the least squares solution could not be
     *               computed.
     */
    static int gelsd(Layout layout, int m, int n, int nrhs, FloatBuffer A, int lda,
                     FloatBuffer B, int ldb, FloatBuffer s, float rcond, IntBuffer rank) {
        var A_ = MemorySegment.ofBuffer(A);
        var B_ = MemorySegment.ofBuffer(B);
        var s_ = MemorySegment.ofBuffer(s);
        var rank_ = MemorySegment.ofBuffer(rank);
        return LAPACKE_sgelsd(layout.lapack(), m, n, nrhs, A_, lda, B_, ldb, s_, rcond, rank_);
    }

    /**
     * Solves a linear equality-constrained least squares (LSE) problem.
     * <pre>{@code
     *     minimize || c - A*x ||_2   subject to   B*x = d
     * }</pre>
     *  where A is an M-by-N matrix, B is a P-by-N matrix, c is a given
     *  M-vector, and d is a given P-vector. It is assumed that
     *  {@code P <= N <= M+P}, and
     * <pre>{@code
     *     rank(B) = P and  rank( (A) ) = N
     *                          ( (B) )
     * }</pre>
     *
     * These conditions ensure that the LSE problem has a unique solution,
     * which is obtained using a generalized RQ factorization of the
     * matrices (B, A) given by
     * <pre>{@code
     *     B = (0 R)*Q,   A = Z*T*Q
     * }</pre>
     *
     * @param layout The matrix layout.
     *
     * @param m The number of rows of the matrix A.
     *
     * @param n The number of columns of the matrix A and B.
     *
     * @param p The number of rows of the matrix B. {@code 0 <= P <= N <= M+P}.
     *
     * @param A The matrix of dimension (LDA, N).
     *          On exit, the elements on and above the diagonal of the array
     *          contain the min(M,N)-by-N upper trapezoidal matrix T.
     *
     * @param lda The leading dimension of the matrix A. {@code LDA >= max(1,M)}.
     *
     * @param B On entry, the P-by-N matrix B.
     *          On exit, the upper triangle of the submatrix B(1:P,N-P+1:N)
     *          contains the P-by-P upper triangular matrix R.
     *
     * @param ldb The leading dimension of the matrix B. {@code LDB >= max(1,P)}.
     *
     * @param c Dimension (M).
     *          On entry, C contains the right hand side vector for the
     *          least squares part of the LSE problem.
     *          On exit, the residual sum of squares for the solution
     *          is given by the sum of squares of elements N-P+1 to M of
     *          vector C.
     *
     * @param d Dimension (P).
     *          On entry, D contains the right hand side vector for the
     *          constrained equation.
     *          On exit, D is destroyed.
     *
     * @param x Dimension (N).
     *          On exit, X is the solution of the LSE problem.
     *
     * @return INFO flag.
     *         {@code = 0}:  successful exit
     *         {@code < 0}:  if {@code INFO = -i}, the i-th argument had an illegal value
     *         = 1:  the upper triangular factor R associated with B in the
     *               generalized RQ factorization of the pair (B, A) is
     *               singular, so that {@code rank(B) < P}; the least squares
     *               solution could not be computed.
     *         = 2:  the (N-P) by (N-P) part of the upper trapezoidal factor
     *               T associated with A in the generalized RQ factorization
     *               of the pair (B, A) is singular, so that
     *               {@code rank( A, B ) < N}; the least squares solution could not
     *               be computed.
     */
    static int gglse(Layout layout, int m, int n, int p, double[] A, int lda,
                     double[] B, int ldb, double[] c, double[] d, double[] x) {
        var A_ = MemorySegment.ofArray(A);
        var B_ = MemorySegment.ofArray(B);
        var c_ = MemorySegment.ofArray(c);
        var d_ = MemorySegment.ofArray(d);
        var x_ = MemorySegment.ofArray(x);
        return LAPACKE_dgglse(layout.lapack(), m, n, p, A_, lda, B_, ldb, c_, d_, x_);
    }

    /**
     * Solves a linear equality-constrained least squares (LSE) problem.
     * <pre>{@code
     *     minimize || c - A*x ||_2   subject to   B*x = d
     * }</pre>
     *  where A is an M-by-N matrix, B is a P-by-N matrix, c is a given
     *  M-vector, and d is a given P-vector. It is assumed that
     *  {@code P <= N <= M+P}, and
     * <pre>{@code
     *     rank(B) = P and  rank( (A) ) = N
     *                          ( (B) )
     * }</pre>
     *
     * These conditions ensure that the LSE problem has a unique solution,
     * which is obtained using a generalized RQ factorization of the
     * matrices (B, A) given by
     * <pre>{@code
     *     B = (0 R)*Q,   A = Z*T*Q
     * }</pre>
     *
     * @param layout The matrix layout.
     *
     * @param m The number of rows of the matrix A.
     *
     * @param n The number of columns of the matrix A and B.
     *
     * @param p The number of rows of the matrix B. {@code 0 <= P <= N <= M+P}.
     *
     * @param A The matrix of dimension (LDA, N).
     *          On exit, the elements on and above the diagonal of the array
     *          contain the min(M,N)-by-N upper trapezoidal matrix T.
     *
     * @param lda The leading dimension of the matrix A. {@code LDA >= max(1,M)}.
     *
     * @param B On entry, the P-by-N matrix B.
     *          On exit, the upper triangle of the submatrix B(1:P,N-P+1:N)
     *          contains the P-by-P upper triangular matrix R.
     *
     * @param ldb The leading dimension of the matrix B. {@code LDB >= max(1,P)}.
     *
     * @param c Dimension (M).
     *          On entry, C contains the right hand side vector for the
     *          least squares part of the LSE problem.
     *          On exit, the residual sum of squares for the solution
     *          is given by the sum of squares of elements N-P+1 to M of
     *          vector C.
     *
     * @param d Dimension (P).
     *          On entry, D contains the right hand side vector for the
     *          constrained equation.
     *          On exit, D is destroyed.
     *
     * @param x Dimension (N).
     *          On exit, X is the solution of the LSE problem.
     *
     * @return INFO flag.
     *         {@code = 0}:  successful exit
     *         {@code < 0}:  if {@code INFO = -i}, the i-th argument had an illegal value
     *         = 1:  the upper triangular factor R associated with B in the
     *               generalized RQ factorization of the pair (B, A) is
     *               singular, so that {@code rank(B) < P}; the least squares
     *               solution could not be computed.
     *         = 2:  the (N-P) by (N-P) part of the upper trapezoidal factor
     *               T associated with A in the generalized RQ factorization
     *               of the pair (B, A) is singular, so that
     *               {@code rank( A, B ) < N}; the least squares solution could not
     *               be computed.
     */
    static int gglse(Layout layout, int m, int n, int p, DoubleBuffer A, int lda,
                     DoubleBuffer B, int ldb, DoubleBuffer c, DoubleBuffer d, DoubleBuffer x) {
        var A_ = MemorySegment.ofBuffer(A);
        var B_ = MemorySegment.ofBuffer(B);
        var c_ = MemorySegment.ofBuffer(c);
        var d_ = MemorySegment.ofBuffer(d);
        var x_ = MemorySegment.ofBuffer(x);
        return LAPACKE_dgglse(layout.lapack(), m, n, p, A_, lda, B_, ldb, c_, d_, x_);
    }

    /**
     * Solves a linear equality-constrained least squares (LSE) problem.
     * <pre>{@code
     *     minimize || c - A*x ||_2   subject to   B*x = d
     * }</pre>
     *  where A is an M-by-N matrix, B is a P-by-N matrix, c is a given
     *  M-vector, and d is a given P-vector. It is assumed that
     *  {@code P <= N <= M+P}, and
     * <pre>{@code
     *     rank(B) = P and  rank( (A) ) = N
     *                          ( (B) )
     * }</pre>
     *
     * These conditions ensure that the LSE problem has a unique solution,
     * which is obtained using a generalized RQ factorization of the
     * matrices (B, A) given by
     * <pre>{@code
     *     B = (0 R)*Q,   A = Z*T*Q
     * }</pre>
     *
     * @param layout The matrix layout.
     *
     * @param m The number of rows of the matrix A.
     *
     * @param n The number of columns of the matrix A and B.
     *
     * @param p The number of rows of the matrix B. {@code 0 <= P <= N <= M+P}.
     *
     * @param A The matrix of dimension (LDA, N).
     *          On exit, the elements on and above the diagonal of the array
     *          contain the min(M,N)-by-N upper trapezoidal matrix T.
     *
     * @param lda The leading dimension of the matrix A. {@code LDA >= max(1,M)}.
     *
     * @param B On entry, the P-by-N matrix B.
     *          On exit, the upper triangle of the submatrix B(1:P,N-P+1:N)
     *          contains the P-by-P upper triangular matrix R.
     *
     * @param ldb The leading dimension of the matrix B. {@code LDB >= max(1,P)}.
     *
     * @param c Dimension (M).
     *          On entry, C contains the right hand side vector for the
     *          least squares part of the LSE problem.
     *          On exit, the residual sum of squares for the solution
     *          is given by the sum of squares of elements N-P+1 to M of
     *          vector C.
     *
     * @param d Dimension (P).
     *          On entry, D contains the right hand side vector for the
     *          constrained equation.
     *          On exit, D is destroyed.
     *
     * @param x Dimension (N).
     *          On exit, X is the solution of the LSE problem.
     *
     * @return INFO flag.
     *         {@code = 0}:  successful exit
     *         {@code < 0}:  if {@code INFO = -i}, the i-th argument had an illegal value
     *         = 1:  the upper triangular factor R associated with B in the
     *               generalized RQ factorization of the pair (B, A) is
     *               singular, so that {@code rank(B) < P}; the least squares
     *               solution could not be computed.
     *         = 2:  the (N-P) by (N-P) part of the upper trapezoidal factor
     *               T associated with A in the generalized RQ factorization
     *               of the pair (B, A) is singular, so that
     *               {@code rank( A, B ) < N}; the least squares solution could not
     *               be computed.
     */
    static int gglse(Layout layout, int m, int n, int p, float[] A, int lda,
                     float[] B, int ldb, float[] c, float[] d, float[] x) {
        var A_ = MemorySegment.ofArray(A);
        var B_ = MemorySegment.ofArray(B);
        var c_ = MemorySegment.ofArray(c);
        var d_ = MemorySegment.ofArray(d);
        var x_ = MemorySegment.ofArray(x);
        return LAPACKE_sgglse(layout.lapack(), m, n, p, A_, lda, B_, ldb, c_, d_, x_);
    }

    /**
     * Solves a linear equality-constrained least squares (LSE) problem.
     * <pre>{@code
     *     minimize || c - A*x ||_2   subject to   B*x = d
     * }</pre>
     *  where A is an M-by-N matrix, B is a P-by-N matrix, c is a given
     *  M-vector, and d is a given P-vector. It is assumed that
     *  {@code P <= N <= M+P}, and
     * <pre>{@code
     *     rank(B) = P and  rank( (A) ) = N
     *                          ( (B) )
     * }</pre>
     *
     * These conditions ensure that the LSE problem has a unique solution,
     * which is obtained using a generalized RQ factorization of the
     * matrices (B, A) given by
     * <pre>{@code
     *     B = (0 R)*Q,   A = Z*T*Q
     * }</pre>
     *
     * @param layout The matrix layout.
     *
     * @param m The number of rows of the matrix A.
     *
     * @param n The number of columns of the matrix A and B.
     *
     * @param p The number of rows of the matrix B. {@code 0 <= P <= N <= M+P}.
     *
     * @param A The matrix of dimension (LDA, N).
     *          On exit, the elements on and above the diagonal of the array
     *          contain the min(M,N)-by-N upper trapezoidal matrix T.
     *
     * @param lda The leading dimension of the matrix A. {@code LDA >= max(1,M)}.
     *
     * @param B On entry, the P-by-N matrix B.
     *          On exit, the upper triangle of the submatrix B(1:P,N-P+1:N)
     *          contains the P-by-P upper triangular matrix R.
     *
     * @param ldb The leading dimension of the matrix B. {@code LDB >= max(1,P)}.
     *
     * @param c Dimension (M).
     *          On entry, C contains the right hand side vector for the
     *          least squares part of the LSE problem.
     *          On exit, the residual sum of squares for the solution
     *          is given by the sum of squares of elements N-P+1 to M of
     *          vector C.
     *
     * @param d Dimension (P).
     *          On entry, D contains the right hand side vector for the
     *          constrained equation.
     *          On exit, D is destroyed.
     *
     * @param x Dimension (N).
     *          On exit, X is the solution of the LSE problem.
     *
     * @return INFO flag.
     *         {@code = 0}:  successful exit
     *         {@code < 0}:  if {@code INFO = -i}, the i-th argument had an illegal value
     *         = 1:  the upper triangular factor R associated with B in the
     *               generalized RQ factorization of the pair (B, A) is
     *               singular, so that {@code rank(B) < P}; the least squares
     *               solution could not be computed.
     *         = 2:  the (N-P) by (N-P) part of the upper trapezoidal factor
     *               T associated with A in the generalized RQ factorization
     *               of the pair (B, A) is singular, so that
     *               {@code rank( A, B ) < N}; the least squares solution could not
     *               be computed.
     */
    static int gglse(Layout layout, int m, int n, int p, FloatBuffer A, int lda,
                     FloatBuffer B, int ldb, FloatBuffer c, FloatBuffer d, FloatBuffer x) {
        var A_ = MemorySegment.ofBuffer(A);
        var B_ = MemorySegment.ofBuffer(B);
        var c_ = MemorySegment.ofBuffer(c);
        var d_ = MemorySegment.ofBuffer(d);
        var x_ = MemorySegment.ofBuffer(x);
        return LAPACKE_sgglse(layout.lapack(), m, n, p, A_, lda, B_, ldb, c_, d_, x_);
    }

    /**
     * Solves a general Gauss-Markov linear model (GLM) problem.
     * <pre>{@code
     *     minimize || y ||_2   subject to   d = A*x + B*y
     *         x
     * }</pre>
     * where A is an N-by-M matrix, B is an N-by-P matrix, and d is a
     * given N-vector. It is assumed that {@code M <= N <= M+P}, and
     * <pre>{@code
     *     rank(A) = M    and    rank( A B ) = N
     * }</pre>
     *
     * Under these assumptions, the constrained equation is always
     * consistent, and there is a unique solution x and a minimal 2-norm
     * solution y, which is obtained using a generalized QR factorization
     * of the matrices (A, B) given by
     * <pre>{@code
     *     A = Q*(R),   B = Q*T*Z
     *           (0)
     * }</pre>
     *
     * In particular, if matrix B is square nonsingular, then the problem
     * GLM is equivalent to the following weighted linear least squares
     * problem
     *
     * <pre>{@code
     *     minimize || inv(B)*(d-A*x) ||_2
     *         x
     * }</pre>
     * where inv(B) denotes the inverse of B.
     *
     * @param layout The matrix layout.
     *
     * @param n The number of rows of the matrix A and B.
     *
     * @param m The number of columns of the matrix A. {@code 0 <= M <= N}.
     *
     * @param p The number of columns of the matrix B.  {@code P >= N-M}.
     *
     * @param A The matrix of dimension (LDA, M).
     *          On exit, the upper triangular part of the matrix A contains
     *          the M-by-M upper triangular matrix R.
     *
     * @param lda The leading dimension of the matrix A. {@code LDA >= max(1,N)}.
     *
     * @param B On entry, the N-by-P matrix B.
     *          On exit, if {@code N <= P}, the upper triangle of the subarray
     *          B(1:N,P-N+1:P) contains the N-by-N upper triangular matrix T;
     *          if {@code N > P}, the elements on and above the (N-P)th subdiagonal
     *          contain the N-by-P upper trapezoidal matrix T.
     *
     * @param ldb The leading dimension of the matrix B. {@code LDB >= max(1,N)}.
     *
     * @param d Dimension (N).
     *          On entry, D is the left hand side of the GLM equation.
     *          On exit, D is destroyed.
     *
     * @param x Dimension (M).
     *          On exit, X and Y are the solutions of the GLM problem.
     *
     * @param y Dimension (P).
     *          On exit, X and Y are the solutions of the GLM problem.
     *
     * @return INFO flag.
     *         {@code = 0}:  successful exit
     *         {@code < 0}:  if {@code INFO = -i}, the i-th argument had an illegal value
     *         = 1:  the upper triangular factor R associated with A in the
     *               generalized QR factorization of the pair (A, B) is
     *               singular, so that {@code rank(A) < M}; the least squares
     *               solution could not be computed.
     *         = 2:  the bottom (N-M) by (N-M) part of the upper trapezoidal
     *               factor T associated with B in the generalized QR
     *               factorization of the pair (A, B) is singular, so that
     *               {@code rank( A B ) < N}; the least squares solution could not
     *               be computed.
     */
    static int ggglm(Layout layout, int n, int m, int p, double[] A, int lda,
                     double[] B, int ldb, double[] d, double[] x, double[] y) {
        var A_ = MemorySegment.ofArray(A);
        var B_ = MemorySegment.ofArray(B);
        var d_ = MemorySegment.ofArray(d);
        var x_ = MemorySegment.ofArray(x);
        var y_ = MemorySegment.ofArray(y);
        return LAPACKE_dggglm(layout.lapack(), n, m, p, A_, lda, B_, ldb, d_, x_, y_);
    }

    /**
     * Solves a general Gauss-Markov linear model (GLM) problem.
     * <pre>{@code
     *     minimize || y ||_2   subject to   d = A*x + B*y
     *         x
     * }</pre>
     * where A is an N-by-M matrix, B is an N-by-P matrix, and d is a
     * given N-vector. It is assumed that {@code M <= N <= M+P}, and
     * <pre>{@code
     *     rank(A) = M    and    rank( A B ) = N
     * }</pre>
     *
     * Under these assumptions, the constrained equation is always
     * consistent, and there is a unique solution x and a minimal 2-norm
     * solution y, which is obtained using a generalized QR factorization
     * of the matrices (A, B) given by
     * <pre>{@code
     *     A = Q*(R),   B = Q*T*Z
     *           (0)
     * }</pre>
     *
     * In particular, if matrix B is square nonsingular, then the problem
     * GLM is equivalent to the following weighted linear least squares
     * problem
     *
     * <pre>{@code
     *     minimize || inv(B)*(d-A*x) ||_2
     *         x
     * }</pre>
     * where inv(B) denotes the inverse of B.
     *
     * @param layout The matrix layout.
     *
     * @param n The number of rows of the matrix A and B.
     *
     * @param m The number of columns of the matrix A. {@code 0 <= M <= N}.
     *
     * @param p The number of columns of the matrix B.  {@code P >= N-M}.
     *
     * @param A The matrix of dimension (LDA, M).
     *          On exit, the upper triangular part of the matrix A contains
     *          the M-by-M upper triangular matrix R.
     *
     * @param lda The leading dimension of the matrix A. {@code LDA >= max(1,N)}.
     *
     * @param B On entry, the N-by-P matrix B.
     *          On exit, if {@code N <= P}, the upper triangle of the subarray
     *          B(1:N,P-N+1:P) contains the N-by-N upper triangular matrix T;
     *          if {@code N > P}, the elements on and above the (N-P)th subdiagonal
     *          contain the N-by-P upper trapezoidal matrix T.
     *
     * @param ldb The leading dimension of the matrix B. {@code LDB >= max(1,N)}.
     *
     * @param d Dimension (N).
     *          On entry, D is the left hand side of the GLM equation.
     *          On exit, D is destroyed.
     *
     * @param x Dimension (M).
     *          On exit, X and Y are the solutions of the GLM problem.
     *
     * @param y Dimension (P).
     *          On exit, X and Y are the solutions of the GLM problem.
     *
     * @return INFO flag.
     *         {@code = 0}:  successful exit
     *         {@code < 0}:  if {@code INFO = -i}, the i-th argument had an illegal value
     *         = 1:  the upper triangular factor R associated with A in the
     *               generalized QR factorization of the pair (A, B) is
     *               singular, so that {@code rank(A) < M}; the least squares
     *               solution could not be computed.
     *         = 2:  the bottom (N-M) by (N-M) part of the upper trapezoidal
     *               factor T associated with B in the generalized QR
     *               factorization of the pair (A, B) is singular, so that
     *               {@code rank( A B ) < N}; the least squares solution could not
     *               be computed.
     */
    static int ggglm(Layout layout, int n, int m, int p, DoubleBuffer A, int lda,
                     DoubleBuffer B, int ldb, DoubleBuffer d, DoubleBuffer x, DoubleBuffer y) {
        var A_ = MemorySegment.ofBuffer(A);
        var B_ = MemorySegment.ofBuffer(B);
        var d_ = MemorySegment.ofBuffer(d);
        var x_ = MemorySegment.ofBuffer(x);
        var y_ = MemorySegment.ofBuffer(y);
        return LAPACKE_dggglm(layout.lapack(), n, m, p, A_, lda, B_, ldb, d_, x_, y_);
    }

    /**
     * Solves a general Gauss-Markov linear model (GLM) problem.
     * <pre>{@code
     *     minimize || y ||_2   subject to   d = A*x + B*y
     *         x
     * }</pre>
     * where A is an N-by-M matrix, B is an N-by-P matrix, and d is a
     * given N-vector. It is assumed that {@code M <= N <= M+P}, and
     * <pre>{@code
     *     rank(A) = M    and    rank( A B ) = N
     * }</pre>
     *
     * Under these assumptions, the constrained equation is always
     * consistent, and there is a unique solution x and a minimal 2-norm
     * solution y, which is obtained using a generalized QR factorization
     * of the matrices (A, B) given by
     * <pre>{@code
     *     A = Q*(R),   B = Q*T*Z
     *           (0)
     * }</pre>
     *
     * In particular, if matrix B is square nonsingular, then the problem
     * GLM is equivalent to the following weighted linear least squares
     * problem
     *
     * <pre>{@code
     *     minimize || inv(B)*(d-A*x) ||_2
     *         x
     * }</pre>
     * where inv(B) denotes the inverse of B.
     *
     * @param layout The matrix layout.
     *
     * @param n The number of rows of the matrix A and B.
     *
     * @param m The number of columns of the matrix A. {@code 0 <= M <= N}.
     *
     * @param p The number of columns of the matrix B.  {@code P >= N-M}.
     *
     * @param A The matrix of dimension (LDA, M).
     *          On exit, the upper triangular part of the matrix A contains
     *          the M-by-M upper triangular matrix R.
     *
     * @param lda The leading dimension of the matrix A. {@code LDA >= max(1,N)}.
     *
     * @param B On entry, the N-by-P matrix B.
     *          On exit, if {@code N <= P}, the upper triangle of the subarray
     *          B(1:N,P-N+1:P) contains the N-by-N upper triangular matrix T;
     *          if {@code N > P}, the elements on and above the (N-P)th subdiagonal
     *          contain the N-by-P upper trapezoidal matrix T.
     *
     * @param ldb The leading dimension of the matrix B. {@code LDB >= max(1,N)}.
     *
     * @param d Dimension (N).
     *          On entry, D is the left hand side of the GLM equation.
     *          On exit, D is destroyed.
     *
     * @param x Dimension (M).
     *          On exit, X and Y are the solutions of the GLM problem.
     *
     * @param y Dimension (P).
     *          On exit, X and Y are the solutions of the GLM problem.
     *
     * @return INFO flag.
     *         {@code = 0}:  successful exit
     *         {@code < 0}:  if {@code INFO = -i}, the i-th argument had an illegal value
     *         = 1:  the upper triangular factor R associated with A in the
     *               generalized QR factorization of the pair (A, B) is
     *               singular, so that {@code rank(A) < M}; the least squares
     *               solution could not be computed.
     *         = 2:  the bottom (N-M) by (N-M) part of the upper trapezoidal
     *               factor T associated with B in the generalized QR
     *               factorization of the pair (A, B) is singular, so that
     *               {@code rank( A B ) < N}; the least squares solution could not
     *               be computed.
     */
    static int ggglm(Layout layout, int n, int m, int p, float[] A, int lda,
                     float[] B, int ldb, float[] d, float[] x, float[] y) {
        var A_ = MemorySegment.ofArray(A);
        var B_ = MemorySegment.ofArray(B);
        var d_ = MemorySegment.ofArray(d);
        var x_ = MemorySegment.ofArray(x);
        var y_ = MemorySegment.ofArray(y);
        return LAPACKE_sggglm(layout.lapack(), n, m, p, A_, lda, B_, ldb, d_, x_, y_);
    }

    /**
     * Solves a general Gauss-Markov linear model (GLM) problem.
     * <pre>{@code
     *     minimize || y ||_2   subject to   d = A*x + B*y
     *         x
     * }</pre>
     * where A is an N-by-M matrix, B is an N-by-P matrix, and d is a
     * given N-vector. It is assumed that {@code M <= N <= M+P}, and
     * <pre>{@code
     *     rank(A) = M    and    rank( A B ) = N
     * }</pre>
     *
     * Under these assumptions, the constrained equation is always
     * consistent, and there is a unique solution x and a minimal 2-norm
     * solution y, which is obtained using a generalized QR factorization
     * of the matrices (A, B) given by
     * <pre>{@code
     *     A = Q*(R),   B = Q*T*Z
     *           (0)
     * }</pre>
     *
     * In particular, if matrix B is square nonsingular, then the problem
     * GLM is equivalent to the following weighted linear least squares
     * problem
     *
     * <pre>{@code
     *     minimize || inv(B)*(d-A*x) ||_2
     *         x
     * }</pre>
     * where inv(B) denotes the inverse of B.
     *
     * @param layout The matrix layout.
     *
     * @param n The number of rows of the matrix A and B.
     *
     * @param m The number of columns of the matrix A. {@code 0 <= M <= N}.
     *
     * @param p The number of columns of the matrix B.  {@code P >= N-M}.
     *
     * @param A The matrix of dimension (LDA, M).
     *          On exit, the upper triangular part of the matrix A contains
     *          the M-by-M upper triangular matrix R.
     *
     * @param lda The leading dimension of the matrix A. {@code LDA >= max(1,N)}.
     *
     * @param B On entry, the N-by-P matrix B.
     *          On exit, if {@code N <= P}, the upper triangle of the subarray
     *          B(1:N,P-N+1:P) contains the N-by-N upper triangular matrix T;
     *          if {@code N > P}, the elements on and above the (N-P)th subdiagonal
     *          contain the N-by-P upper trapezoidal matrix T.
     *
     * @param ldb The leading dimension of the matrix B. {@code LDB >= max(1,N)}.
     *
     * @param d Dimension (N).
     *          On entry, D is the left hand side of the GLM equation.
     *          On exit, D is destroyed.
     *
     * @param x Dimension (M).
     *          On exit, X and Y are the solutions of the GLM problem.
     *
     * @param y Dimension (P).
     *          On exit, X and Y are the solutions of the GLM problem.
     *
     * @return INFO flag.
     *         {@code = 0}:  successful exit
     *         {@code < 0}:  if {@code INFO = -i}, the i-th argument had an illegal value
     *         = 1:  the upper triangular factor R associated with A in the
     *               generalized QR factorization of the pair (A, B) is
     *               singular, so that {@code rank(A) < M}; the least squares
     *               solution could not be computed.
     *         = 2:  the bottom (N-M) by (N-M) part of the upper trapezoidal
     *               factor T associated with B in the generalized QR
     *               factorization of the pair (A, B) is singular, so that
     *               {@code rank( A B ) < N}; the least squares solution could not
     *               be computed.
     */
    static int ggglm(Layout layout, int n, int m, int p, FloatBuffer A, int lda,
                     FloatBuffer B, int ldb, FloatBuffer d, FloatBuffer x, FloatBuffer y) {
        var A_ = MemorySegment.ofBuffer(A);
        var B_ = MemorySegment.ofBuffer(B);
        var d_ = MemorySegment.ofBuffer(d);
        var x_ = MemorySegment.ofBuffer(x);
        var y_ = MemorySegment.ofBuffer(y);
        return LAPACKE_sggglm(layout.lapack(), n, m, p, A_, lda, B_, ldb, d_, x_, y_);
    }

    /**
     * Computes the eigenvalues and, optionally, the left and/or right
     * eigenvectors. The computed eigenvectors are normalized to have
     * Euclidean norm equal to 1 and largest component real.
     *
     * @param layout The matrix layout.
     *
     * @param jobvl The option for computing all or part of the matrix U.
     *
     * @param jobvr The option for computing all or part of the matrix VT.
     *
     * @param n The dimension of the matrix A.
     *
     * @param A The matrix of dimension (LDA, N).
     *          On entry, the N-by-N matrix A.
     *          On exit, A has been overwritten.
     *
     * @param lda The leading dimension of the matrix A. {@code LDA >= max(1,N)}.
     *
     * @param wr Dimension N. WR and WI contain the real and imaginary parts,
     *           respectively, of the computed eigenvalues. Complex
     *           conjugate pairs of eigenvalues appear consecutively
     *           with the eigenvalue having the positive imaginary part first.
     *
     * @param wi Dimension N. WR and WI contain the real and imaginary parts,
     *           respectively, of the computed eigenvalues. Complex
     *           conjugate pairs of eigenvalues appear consecutively
     *           with the eigenvalue having the positive imaginary part first.
     *
     * @param Vl Left eigenvectors. If JOBVL = 'N', Vl is not referenced.
     *
     * @param ldvl The leading dimension of the matrix Vl.
     *
     * @param Vr Right eigenvectors. If JOBVR = 'N', Vr is not referenced.
     *
     * @param ldvr The leading dimension of the matrix Vr.
     *
     * @return INFO flag.
     *         {@code = 0}:  successful exit.
     *         {@code < 0}:  if {@code INFO = -i}, the i-th argument had an illegal value.
     *         {@code > 0}:  if {@code INFO = i}, the QR algorithm failed to compute all the
     *               eigenvalues, and no eigenvectors have been computed;
     *               elements i+1:N of WR and WI contain eigenvalues which
     *               have converged.
     */
    static int geev(Layout layout, EVDJob jobvl, EVDJob jobvr, int n, double[] A, int lda,
                    double[] wr, double[] wi, double[] Vl, int ldvl, double[] Vr, int ldvr) {
        var A_ = MemorySegment.ofArray(A);
        var wr_ = MemorySegment.ofArray(wr);
        var wi_ = MemorySegment.ofArray(wi);
        var Vl_ = MemorySegment.ofArray(Vl);
        var Vr_ = MemorySegment.ofArray(Vr);
        return LAPACKE_dgeev(layout.lapack(), jobvl.lapack(), jobvr.lapack(), n, A_, lda, wr_, wi_, Vl_, ldvl, Vr_, ldvr);
    }

    /**
     * Computes the eigenvalues and, optionally, the left and/or right
     * eigenvectors. The computed eigenvectors are normalized to have
     * Euclidean norm equal to 1 and largest component real.
     *
     * @param layout The matrix layout.
     *
     * @param jobvl The option for computing all or part of the matrix U.
     *
     * @param jobvr The option for computing all or part of the matrix VT.
     *
     * @param n The dimension of the matrix A.
     *
     * @param A The matrix of dimension (LDA, N).
     *          On entry, the N-by-N matrix A.
     *          On exit, A has been overwritten.
     *
     * @param lda The leading dimension of the matrix A. {@code LDA >= max(1,N)}.
     *
     * @param wr Dimension N. WR and WI contain the real and imaginary parts,
     *           respectively, of the computed eigenvalues. Complex
     *           conjugate pairs of eigenvalues appear consecutively
     *           with the eigenvalue having the positive imaginary part first.
     *
     * @param wi Dimension N. WR and WI contain the real and imaginary parts,
     *           respectively, of the computed eigenvalues. Complex
     *           conjugate pairs of eigenvalues appear consecutively
     *           with the eigenvalue having the positive imaginary part first.
     *
     * @param Vl Left eigenvectors. If JOBVL = 'N', Vl is not referenced.
     *
     * @param ldvl The leading dimension of the matrix Vl.
     *
     * @param Vr Right eigenvectors. If JOBVR = 'N', Vr is not referenced.
     *
     * @param ldvr The leading dimension of the matrix Vr.
     *
     * @return INFO flag.
     *         {@code = 0}:  successful exit.
     *         {@code < 0}:  if {@code INFO = -i}, the i-th argument had an illegal value.
     *         {@code > 0}:  if {@code INFO = i}, the QR algorithm failed to compute all the
     *               eigenvalues, and no eigenvectors have been computed;
     *               elements i+1:N of WR and WI contain eigenvalues which
     *               have converged.
     */
    static int geev(Layout layout, EVDJob jobvl, EVDJob jobvr, int n, DoubleBuffer A, int lda,
                    DoubleBuffer wr, DoubleBuffer wi, DoubleBuffer Vl, int ldvl, DoubleBuffer Vr, int ldvr) {
        var A_ = MemorySegment.ofBuffer(A);
        var wr_ = MemorySegment.ofBuffer(wr);
        var wi_ = MemorySegment.ofBuffer(wi);
        var Vl_ = MemorySegment.ofBuffer(Vl);
        var Vr_ = MemorySegment.ofBuffer(Vr);
        return LAPACKE_dgeev(layout.lapack(), jobvl.lapack(), jobvr.lapack(), n, A_, lda, wr_, wi_, Vl_, ldvl, Vr_, ldvr);
    }

    /**
     * Computes the eigenvalues and, optionally, the left and/or right
     * eigenvectors. The computed eigenvectors are normalized to have
     * Euclidean norm equal to 1 and largest component real.
     *
     * @param layout The matrix layout.
     *
     * @param jobvl The option for computing all or part of the matrix U.
     *
     * @param jobvr The option for computing all or part of the matrix VT.
     *
     * @param n The dimension of the matrix A.
     *
     * @param A The matrix of dimension (LDA, N).
     *          On entry, the N-by-N matrix A.
     *          On exit, A has been overwritten.
     *
     * @param lda The leading dimension of the matrix A. {@code LDA >= max(1,N)}.
     *
     * @param wr Dimension N. WR and WI contain the real and imaginary parts,
     *           respectively, of the computed eigenvalues. Complex
     *           conjugate pairs of eigenvalues appear consecutively
     *           with the eigenvalue having the positive imaginary part first.
     *
     * @param wi Dimension N. WR and WI contain the real and imaginary parts,
     *           respectively, of the computed eigenvalues. Complex
     *           conjugate pairs of eigenvalues appear consecutively
     *           with the eigenvalue having the positive imaginary part first.
     *
     * @param Vl Left eigenvectors. If JOBVL = 'N', Vl is not referenced.
     *
     * @param ldvl The leading dimension of the matrix Vl.
     *
     * @param Vr Right eigenvectors. If JOBVR = 'N', Vr is not referenced.
     *
     * @param ldvr The leading dimension of the matrix Vr.
     *
     * @return INFO flag.
     *         {@code = 0}:  successful exit.
     *         {@code < 0}:  if {@code INFO = -i}, the i-th argument had an illegal value.
     *         {@code > 0}:  if {@code INFO = i}, the QR algorithm failed to compute all the
     *               eigenvalues, and no eigenvectors have been computed;
     *               elements i+1:N of WR and WI contain eigenvalues which
     *               have converged.
     */
    static int geev(Layout layout, EVDJob jobvl, EVDJob jobvr, int n, MemorySegment A, int lda,
                    MemorySegment wr, MemorySegment wi, MemorySegment Vl, int ldvl, MemorySegment Vr, int ldvr) {
        return LAPACKE_dgeev(layout.lapack(), jobvl.lapack(), jobvr.lapack(), n, A, lda, wr, wi, Vl, ldvl, Vr, ldvr);
    }

    /**
     * Computes the eigenvalues and, optionally, the left and/or right
     * eigenvectors. The computed eigenvectors are normalized to have
     * Euclidean norm equal to 1 and largest component real.
     *
     * @param layout The matrix layout.
     *
     * @param jobvl The option for computing all or part of the matrix U.
     *
     * @param jobvr The option for computing all or part of the matrix VT.
     *
     * @param n The dimension of the matrix A.
     *
     * @param A The matrix of dimension (LDA, N).
     *          On entry, the N-by-N matrix A.
     *          On exit, A has been overwritten.
     *
     * @param lda The leading dimension of the matrix A. {@code LDA >= max(1,N)}.
     *
     * @param wr Dimension N. WR and WI contain the real and imaginary parts,
     *           respectively, of the computed eigenvalues. Complex
     *           conjugate pairs of eigenvalues appear consecutively
     *           with the eigenvalue having the positive imaginary part first.
     *
     * @param wi Dimension N. WR and WI contain the real and imaginary parts,
     *           respectively, of the computed eigenvalues. Complex
     *           conjugate pairs of eigenvalues appear consecutively
     *           with the eigenvalue having the positive imaginary part first.
     *
     * @param Vl Left eigenvectors. If JOBVL = 'N', Vl is not referenced.
     *
     * @param ldvl The leading dimension of the matrix Vl.
     *
     * @param Vr Right eigenvectors. If JOBVR = 'N', Vr is not referenced.
     *
     * @param ldvr The leading dimension of the matrix Vr.
     *
     * @return INFO flag.
     *         {@code = 0}:  successful exit.
     *         {@code < 0}:  if {@code INFO = -i}, the i-th argument had an illegal value.
     *         {@code > 0}:  if {@code INFO = i}, the QR algorithm failed to compute all the
     *               eigenvalues, and no eigenvectors have been computed;
     *               elements i+1:N of WR and WI contain eigenvalues which
     *               have converged.
     */
    static int geev(Layout layout, EVDJob jobvl, EVDJob jobvr, int n, float[] A, int lda,
                    float[] wr, float[] wi, float[] Vl, int ldvl, float[] Vr, int ldvr) {
        var A_ = MemorySegment.ofArray(A);
        var wr_ = MemorySegment.ofArray(wr);
        var wi_ = MemorySegment.ofArray(wi);
        var Vl_ = MemorySegment.ofArray(Vl);
        var Vr_ = MemorySegment.ofArray(Vr);
        return LAPACKE_sgeev(layout.lapack(), jobvl.lapack(), jobvr.lapack(), n, A_, lda, wr_, wi_, Vl_, ldvl, Vr_, ldvr);
    }

    /**
     * Computes the eigenvalues and, optionally, the left and/or right
     * eigenvectors. The computed eigenvectors are normalized to have
     * Euclidean norm equal to 1 and largest component real.
     *
     * @param layout The matrix layout.
     *
     * @param jobvl The option for computing all or part of the matrix U.
     *
     * @param jobvr The option for computing all or part of the matrix VT.
     *
     * @param n The dimension of the matrix A.
     *
     * @param A The matrix of dimension (LDA, N).
     *          On entry, the N-by-N matrix A.
     *          On exit, A has been overwritten.
     *
     * @param lda The leading dimension of the matrix A. {@code LDA >= max(1,N)}.
     *
     * @param wr Dimension N. WR and WI contain the real and imaginary parts,
     *           respectively, of the computed eigenvalues. Complex
     *           conjugate pairs of eigenvalues appear consecutively
     *           with the eigenvalue having the positive imaginary part first.
     *
     * @param wi Dimension N. WR and WI contain the real and imaginary parts,
     *           respectively, of the computed eigenvalues. Complex
     *           conjugate pairs of eigenvalues appear consecutively
     *           with the eigenvalue having the positive imaginary part first.
     *
     * @param Vl Left eigenvectors. If JOBVL = 'N', Vl is not referenced.
     *
     * @param ldvl The leading dimension of the matrix Vl.
     *
     * @param Vr Right eigenvectors. If JOBVR = 'N', Vr is not referenced.
     *
     * @param ldvr The leading dimension of the matrix Vr.
     *
     * @return INFO flag.
     *         {@code = 0}:  successful exit.
     *         {@code < 0}:  if {@code INFO = -i}, the i-th argument had an illegal value.
     *         {@code > 0}:  if {@code INFO = i}, the QR algorithm failed to compute all the
     *               eigenvalues, and no eigenvectors have been computed;
     *               elements i+1:N of WR and WI contain eigenvalues which
     *               have converged.
     */
    static int geev(Layout layout, EVDJob jobvl, EVDJob jobvr, int n, FloatBuffer A, int lda,
                    FloatBuffer wr, FloatBuffer wi, FloatBuffer Vl, int ldvl, FloatBuffer Vr, int ldvr) {
        var A_ = MemorySegment.ofBuffer(A);
        var wr_ = MemorySegment.ofBuffer(wr);
        var wi_ = MemorySegment.ofBuffer(wi);
        var Vl_ = MemorySegment.ofBuffer(Vl);
        var Vr_ = MemorySegment.ofBuffer(Vr);
        return LAPACKE_sgeev(layout.lapack(), jobvl.lapack(), jobvr.lapack(), n, A_, lda, wr_, wi_, Vl_, ldvl, Vr_, ldvr);
    }

    /**
     * Computes the eigenvalues and, optionally, the left and/or right
     * eigenvectors of a real symmetric matrix A.
     *
     * @param layout The matrix layout.
     *
     * @param jobz The option if computing eigen vectors.
     *
     * @param uplo The upper or lower triangular part of the matrix A is
     *             to be referenced.
     *
     * @param n The dimension of the matrix A.
     *
     * @param A The matrix of dimension (LDA, N).
     *          On entry, the N-by-N matrix A.
     *          On exit, A has been overwritten.
     *
     * @param lda The leading dimension of the matrix A. {@code LDA >= max(1,N)}.
     *
     * @param w Dimension N. If INFO = 0, the eigenvalues in ascending order.
     *
     * @return INFO flag.
     *         {@code = 0}:  successful exit.
     *         {@code < 0}:  if {@code INFO = -i}, the i-th argument had an illegal value.
     *         {@code > 0}:  if {@code INFO = i}, the algorithm failed to converge; i
     *               off-diagonal elements of an intermediate tridiagonal
     *               form did not converge to zero.
     */
    static int syev(Layout layout, EVDJob jobz, UPLO uplo, int n, double[] A, int lda, double[] w) {
        var A_ = MemorySegment.ofArray(A);
        var w_ = MemorySegment.ofArray(w);
        return LAPACKE_dsyev(layout.lapack(), jobz.lapack(), uplo.lapack(), n, A_, lda, w_);
    }

    /**
     * Computes the eigenvalues and, optionally, the left and/or right
     * eigenvectors of a real symmetric matrix A.
     *
     * @param layout The matrix layout.
     *
     * @param jobz The option if computing eigen vectors.
     *
     * @param uplo The upper or lower triangular part of the matrix A is
     *             to be referenced.
     *
     * @param n The dimension of the matrix A.
     *
     * @param A The matrix of dimension (LDA, N).
     *          On entry, the N-by-N matrix A.
     *          On exit, A has been overwritten.
     *
     * @param lda The leading dimension of the matrix A. {@code LDA >= max(1,N)}.
     *
     * @param w Dimension N. If INFO = 0, the eigenvalues in ascending order.
     *
     * @return INFO flag.
     *         {@code = 0}:  successful exit.
     *         {@code < 0}:  if {@code INFO = -i}, the i-th argument had an illegal value.
     *         {@code > 0}:  if {@code INFO = i}, the algorithm failed to converge; i
     *               off-diagonal elements of an intermediate tridiagonal
     *               form did not converge to zero.
     */
    static int syev(Layout layout, EVDJob jobz, UPLO uplo, int n, DoubleBuffer A, int lda, DoubleBuffer w) {
        var A_ = MemorySegment.ofBuffer(A);
        var w_ = MemorySegment.ofBuffer(w);
        return LAPACKE_dsyev(layout.lapack(), jobz.lapack(), uplo.lapack(), n, A_, lda, w_);
    }

    /**
     * Computes the eigenvalues and, optionally, the left and/or right
     * eigenvectors of a real symmetric matrix A.
     *
     * @param layout The matrix layout.
     *
     * @param jobz The option if computing eigen vectors.
     *
     * @param uplo The upper or lower triangular part of the matrix A is
     *             to be referenced.
     *
     * @param n The dimension of the matrix A.
     *
     * @param A The matrix of dimension (LDA, N).
     *          On entry, the N-by-N matrix A.
     *          On exit, A has been overwritten.
     *
     * @param lda The leading dimension of the matrix A. {@code LDA >= max(1,N)}.
     *
     * @param w Dimension N. If INFO = 0, the eigenvalues in ascending order.
     *
     * @return INFO flag.
     *         {@code = 0}:  successful exit.
     *         {@code < 0}:  if {@code INFO = -i}, the i-th argument had an illegal value.
     *         {@code > 0}:  if {@code INFO = i}, the algorithm failed to converge; i
     *               off-diagonal elements of an intermediate tridiagonal
     *               form did not converge to zero.
     */
    static int syev(Layout layout, EVDJob jobz, UPLO uplo, int n, float[] A, int lda, float[] w) {
        var A_ = MemorySegment.ofArray(A);
        var w_ = MemorySegment.ofArray(w);
        return LAPACKE_ssyev(layout.lapack(), jobz.lapack(), uplo.lapack(), n, A_, lda, w_);
    }

    /**
     * Computes the eigenvalues and, optionally, the left and/or right
     * eigenvectors of a real symmetric matrix A.
     *
     * @param layout The matrix layout.
     *
     * @param jobz The option if computing eigen vectors.
     *
     * @param uplo The upper or lower triangular part of the matrix A is
     *             to be referenced.
     *
     * @param n The dimension of the matrix A.
     *
     * @param A The matrix of dimension (LDA, N).
     *          On entry, the N-by-N matrix A.
     *          On exit, A has been overwritten.
     *
     * @param lda The leading dimension of the matrix A. {@code LDA >= max(1,N)}.
     *
     * @param w Dimension N. If INFO = 0, the eigenvalues in ascending order.
     *
     * @return INFO flag.
     *         {@code = 0}:  successful exit.
     *         {@code < 0}:  if {@code INFO = -i}, the i-th argument had an illegal value.
     *         {@code > 0}:  if {@code INFO = i}, the algorithm failed to converge; i
     *               off-diagonal elements of an intermediate tridiagonal
     *               form did not converge to zero.
     */
    static int syev(Layout layout, EVDJob jobz, UPLO uplo, int n, FloatBuffer A, int lda, FloatBuffer w) {
        var A_ = MemorySegment.ofBuffer(A);
        var w_ = MemorySegment.ofBuffer(w);
        return LAPACKE_ssyev(layout.lapack(), jobz.lapack(), uplo.lapack(), n, A_, lda, w_);
    }

    /**
     * Computes the eigenvalues and, optionally, the left and/or right
     * eigenvectors of a real symmetric matrix A. If eigenvectors are
     * desired, it uses a divide and conquer algorithm.
     *
     * @param layout The matrix layout.
     *
     * @param jobz The option if computing eigen vectors.
     *
     * @param uplo The upper or lower triangular part of the matrix A is
     *             to be referenced.
     *
     * @param n The dimension of the matrix A.
     *
     * @param A The matrix of dimension (LDA, N).
     *          On entry, the N-by-N matrix A.
     *          On exit, A has been overwritten.
     *
     * @param lda The leading dimension of the matrix A. {@code LDA >= max(1,N)}.
     *
     * @param w Dimension N. If INFO = 0, the eigenvalues in ascending order.
     *
     * @return INFO flag.
     *         {@code = 0}:  successful exit.
     *         {@code < 0}:  if {@code INFO = -i}, the i-th argument had an illegal value.
     *         {@code > 0}:  if {@code INFO = i}, the algorithm failed to converge; i
     *               off-diagonal elements of an intermediate tridiagonal
     *               form did not converge to zero.
     */
    static int syevd(Layout layout, EVDJob jobz, UPLO uplo, int n, double[] A, int lda, double[] w) {
        var A_ = MemorySegment.ofArray(A);
        var w_ = MemorySegment.ofArray(w);
        return LAPACKE_dsyevd(layout.lapack(), jobz.lapack(), uplo.lapack(), n, A_, lda, w_);
    }

    /**
     * Computes the eigenvalues and, optionally, the left and/or right
     * eigenvectors of a real symmetric matrix A. If eigenvectors are
     * desired, it uses a divide and conquer algorithm.
     *
     * @param layout The matrix layout.
     *
     * @param jobz The option if computing eigen vectors.
     *
     * @param uplo The upper or lower triangular part of the matrix A is
     *             to be referenced.
     *
     * @param n The dimension of the matrix A.
     *
     * @param A The matrix of dimension (LDA, N).
     *          On entry, the N-by-N matrix A.
     *          On exit, A has been overwritten.
     *
     * @param lda The leading dimension of the matrix A. {@code LDA >= max(1,N)}.
     *
     * @param w Dimension N. If INFO = 0, the eigenvalues in ascending order.
     *
     * @return INFO flag.
     *         {@code = 0}:  successful exit.
     *         {@code < 0}:  if {@code INFO = -i}, the i-th argument had an illegal value.
     *         {@code > 0}:  if {@code INFO = i}, the algorithm failed to converge; i
     *               off-diagonal elements of an intermediate tridiagonal
     *               form did not converge to zero.
     */
    static int syevd(Layout layout, EVDJob jobz, UPLO uplo, int n, DoubleBuffer A, int lda, DoubleBuffer w) {
        var A_ = MemorySegment.ofBuffer(A);
        var w_ = MemorySegment.ofBuffer(w);
        return LAPACKE_dsyevd(layout.lapack(), jobz.lapack(), uplo.lapack(), n, A_, lda, w_);
    }

    /**
     * Computes the eigenvalues and, optionally, the left and/or right
     * eigenvectors of a real symmetric matrix A. If eigenvectors are
     * desired, it uses a divide and conquer algorithm.
     *
     * @param layout The matrix layout.
     *
     * @param jobz The option if computing eigen vectors.
     *
     * @param uplo The upper or lower triangular part of the matrix A is
     *             to be referenced.
     *
     * @param n The dimension of the matrix A.
     *
     * @param A The matrix of dimension (LDA, N).
     *          On entry, the N-by-N matrix A.
     *          On exit, A has been overwritten.
     *
     * @param lda The leading dimension of the matrix A. {@code LDA >= max(1,N)}.
     *
     * @param w Dimension N. If INFO = 0, the eigenvalues in ascending order.
     *
     * @return INFO flag.
     *         {@code = 0}:  successful exit.
     *         {@code < 0}:  if {@code INFO = -i}, the i-th argument had an illegal value.
     *         {@code > 0}:  if {@code INFO = i}, the algorithm failed to converge; i
     *               off-diagonal elements of an intermediate tridiagonal
     *               form did not converge to zero.
     */
    static int syevd(Layout layout, EVDJob jobz, UPLO uplo, int n, MemorySegment A, int lda, MemorySegment w) {
        return LAPACKE_dsyevd(layout.lapack(), jobz.lapack(), uplo.lapack(), n, A, lda, w);
    }

    /**
     * Computes the eigenvalues and, optionally, the left and/or right
     * eigenvectors of a real symmetric matrix A. If eigenvectors are
     * desired, it uses a divide and conquer algorithm.
     *
     * @param layout The matrix layout.
     *
     * @param jobz The option if computing eigen vectors.
     *
     * @param uplo The upper or lower triangular part of the matrix A is
     *             to be referenced.
     *
     * @param n The dimension of the matrix A.
     *
     * @param A The matrix of dimension (LDA, N).
     *          On entry, the N-by-N matrix A.
     *          On exit, A has been overwritten.
     *
     * @param lda The leading dimension of the matrix A. {@code LDA >= max(1,N)}.
     *
     * @param w Dimension N. If INFO = 0, the eigenvalues in ascending order.
     *
     * @return INFO flag.
     *         {@code = 0}:  successful exit.
     *         {@code < 0}:  if {@code INFO = -i}, the i-th argument had an illegal value.
     *         {@code > 0}:  if {@code INFO = i}, the algorithm failed to converge; i
     *               off-diagonal elements of an intermediate tridiagonal
     *               form did not converge to zero.
     */
    static int syevd(Layout layout, EVDJob jobz, UPLO uplo, int n, float[] A, int lda, float[] w) {
        var A_ = MemorySegment.ofArray(A);
        var w_ = MemorySegment.ofArray(w);
        return LAPACKE_ssyevd(layout.lapack(), jobz.lapack(), uplo.lapack(), n, A_, lda, w_);
    }

    /**
     * Computes the eigenvalues and, optionally, the left and/or right
     * eigenvectors of a real symmetric matrix A. If eigenvectors are
     * desired, it uses a divide and conquer algorithm.
     *
     * @param layout The matrix layout.
     *
     * @param jobz The option if computing eigen vectors.
     *
     * @param uplo The upper or lower triangular part of the matrix A is
     *             to be referenced.
     *
     * @param n The dimension of the matrix A.
     *
     * @param A The matrix of dimension (LDA, N).
     *          On entry, the N-by-N matrix A.
     *          On exit, A has been overwritten.
     *
     * @param lda The leading dimension of the matrix A. {@code LDA >= max(1,N)}.
     *
     * @param w Dimension N. If INFO = 0, the eigenvalues in ascending order.
     *
     * @return INFO flag.
     *         {@code = 0}:  successful exit.
     *         {@code < 0}:  if {@code INFO = -i}, the i-th argument had an illegal value.
     *         {@code > 0}:  if {@code INFO = i}, the algorithm failed to converge; i
     *               off-diagonal elements of an intermediate tridiagonal
     *               form did not converge to zero.
     */
    static int syevd(Layout layout, EVDJob jobz, UPLO uplo, int n, FloatBuffer A, int lda, FloatBuffer w) {
        var A_ = MemorySegment.ofBuffer(A);
        var w_ = MemorySegment.ofBuffer(w);
        return LAPACKE_ssyevd(layout.lapack(), jobz.lapack(), uplo.lapack(), n, A_, lda, w_);
    }

    /**
     * Computes the eigenvalues and, optionally, the left and/or right
     * eigenvectors of a real symmetric matrix A. Eigenvalues and
     * eigenvectors can be selected by specifying either a range of
     * values or a range of indices for the desired eigenvalues.
     * <p>
     * SYEVR first reduces the matrix A to tridiagonal form T with a call
     * to DSYTRD. Then, whenever possible, DSYEVR calls DSTEMR to compute
     * the eigenspectrum using Relatively Robust Representations. DSTEMR
     * computes eigenvalues by the dqds algorithm, while orthogonal
     * eigenvectors are computed from various "good" L D L^T representations
     * (also known as Relatively Robust Representations). Gram-Schmidt
     * orthogonalization is avoided as far as possible. More specifically,
     * the various steps of the algorithm are as follows.
     * <p>
     * The desired accuracy of the output can be specified by the input
     * parameter ABSTOL.
     *
     * @param layout The matrix layout.
     *
     * @param jobz The option if computing eigen vectors.
     *
     * @param range The range of eigenvalues to compute.
     *
     * @param uplo The upper or lower triangular part of the matrix A is
     *             to be referenced.
     *
     * @param n The dimension of the matrix A.
     *
     * @param A The matrix of dimension (LDA, N).
     *          On entry, the N-by-N matrix A.
     *          On exit, A has been overwritten.
     *
     * @param lda The leading dimension of the matrix A. {@code LDA >= max(1,N)}.
     *
     * @param vl The lower bound of the interval to be searched for eigenvalues.
     *           Not referenced if RANGE = 'A' or 'I'.
     *
     * @param vu The upper bound of the interval to be searched for eigenvalues.
     *           Not referenced if RANGE = 'A' or 'I'.
     *
     * @param il The index of the smallest eigenvalue to be returned.
     *           Not referenced if RANGE = 'A' or 'V'.
     *
     * @param iu The index of the largest eigenvalue to be returned.
     *           Not referenced if RANGE = 'A' or 'V'.
     *
     * @param abstol The absolute error tolerance for the eigenvalues.
     *
     * @param m The total number of eigenvalues found.
     *
     * @param w The first M elements contain the selected eigenvalues in
     *          ascending order.
     *
     * @param Z Dimension (LDZ, max(1,M)).
     *          If JOBZ = 'V', then if INFO = 0, the first M columns of Z
     *          contain the orthonormal eigenvectors of the matrix A
     *          corresponding to the selected eigenvalues, with the i-th
     *          column of Z holding the eigenvector associated with W(i).
     *          If JOBZ = 'N', then Z is not referenced.
     *
     * @param ldz The leading dimension of the matrix Z.
     *
     * @param isuppz Dimension 2 * max(1,M).
     *               The support of the eigenvectors in Z, i.e., the indices
     *               indicating the nonzero elements in Z. The i-th eigenvector
     *               is nonzero only in elements ISUPPZ( 2*i-1 ) through
     *               ISUPPZ( 2*i ). This is an output of DSTEMR (tridiagonal
     *               matrix). The support of the eigenvectors of A is typically
     *               1:N because of the orthogonal transformations applied by DORMTR.
     *               Implemented only for RANGE = 'A' or 'I' and IU - IL = N - 1.
     *
     * @return INFO flag.
     *         {@code = 0}:  successful exit.
     *         {@code < 0}:  if {@code INFO = -i}, the i-th argument had an illegal value.
     *         {@code > 0}:  Internal error
     */
    static int syevr(Layout layout, EVDJob jobz, EigenRange range, UPLO uplo, int n, double[] A, int lda, double vl,
                     double vu, int il, int iu, double abstol, int[] m, double[] w, double[] Z, int ldz, int[] isuppz) {
        var A_ = MemorySegment.ofArray(A);
        var m_ = MemorySegment.ofArray(m);
        var w_ = MemorySegment.ofArray(w);
        var Z_ = MemorySegment.ofArray(Z);
        var isuppz_ = MemorySegment.ofArray(isuppz);
        return LAPACKE_dsyevr(layout.lapack(), jobz.lapack(), range.lapack(), uplo.lapack(), n,
                A_, lda, vl, vu, il, iu, abstol, m_, w_, Z_, ldz, isuppz_);
    }

    /**
     * Computes the eigenvalues and, optionally, the left and/or right
     * eigenvectors of a real symmetric matrix A. Eigenvalues and
     * eigenvectors can be selected by specifying either a range of
     * values or a range of indices for the desired eigenvalues.
     * <p>
     * SYEVR first reduces the matrix A to tridiagonal form T with a call
     * to DSYTRD. Then, whenever possible, DSYEVR calls DSTEMR to compute
     * the eigenspectrum using Relatively Robust Representations. DSTEMR
     * computes eigenvalues by the dqds algorithm, while orthogonal
     * eigenvectors are computed from various "good" L D L^T representations
     * (also known as Relatively Robust Representations). Gram-Schmidt
     * orthogonalization is avoided as far as possible. More specifically,
     * the various steps of the algorithm are as follows.
     * <p>
     * The desired accuracy of the output can be specified by the input
     * parameter ABSTOL.
     *
     * @param layout The matrix layout.
     *
     * @param jobz The option if computing eigen vectors.
     *
     * @param range The range of eigenvalues to compute.
     *
     * @param uplo The upper or lower triangular part of the matrix A is
     *             to be referenced.
     *
     * @param n The dimension of the matrix A.
     *
     * @param A The matrix of dimension (LDA, N).
     *          On entry, the N-by-N matrix A.
     *          On exit, A has been overwritten.
     *
     * @param lda The leading dimension of the matrix A. {@code LDA >= max(1,N)}.
     *
     * @param vl The lower bound of the interval to be searched for eigenvalues.
     *           Not referenced if RANGE = 'A' or 'I'.
     *
     * @param vu The upper bound of the interval to be searched for eigenvalues.
     *           Not referenced if RANGE = 'A' or 'I'.
     *
     * @param il The index of the smallest eigenvalue to be returned.
     *           Not referenced if RANGE = 'A' or 'V'.
     *
     * @param iu The index of the largest eigenvalue to be returned.
     *           Not referenced if RANGE = 'A' or 'V'.
     *
     * @param abstol The absolute error tolerance for the eigenvalues.
     *
     * @param m The total number of eigenvalues found.
     *
     * @param w The first M elements contain the selected eigenvalues in
     *          ascending order.
     *
     * @param Z Dimension (LDZ, max(1,M)).
     *          If JOBZ = 'V', then if INFO = 0, the first M columns of Z
     *          contain the orthonormal eigenvectors of the matrix A
     *          corresponding to the selected eigenvalues, with the i-th
     *          column of Z holding the eigenvector associated with W(i).
     *          If JOBZ = 'N', then Z is not referenced.
     *
     * @param ldz The leading dimension of the matrix Z.
     *
     * @param isuppz Dimension 2 * max(1,M).
     *               The support of the eigenvectors in Z, i.e., the indices
     *               indicating the nonzero elements in Z. The i-th eigenvector
     *               is nonzero only in elements ISUPPZ( 2*i-1 ) through
     *               ISUPPZ( 2*i ). This is an output of DSTEMR (tridiagonal
     *               matrix). The support of the eigenvectors of A is typically
     *               1:N because of the orthogonal transformations applied by DORMTR.
     *               Implemented only for RANGE = 'A' or 'I' and IU - IL = N - 1.
     *
     * @return INFO flag.
     *         {@code = 0}:  successful exit.
     *         {@code < 0}:  if {@code INFO = -i}, the i-th argument had an illegal value.
     *         {@code > 0}:  Internal error
     */
    static int syevr(Layout layout, EVDJob jobz, EigenRange range, UPLO uplo, int n, DoubleBuffer A, int lda, double vl,
                     double vu, int il, int iu, double abstol, IntBuffer m, DoubleBuffer w, DoubleBuffer Z, int ldz, IntBuffer isuppz) {
        var A_ = MemorySegment.ofBuffer(A);
        var m_ = MemorySegment.ofBuffer(m);
        var w_ = MemorySegment.ofBuffer(w);
        var Z_ = MemorySegment.ofBuffer(Z);
        var isuppz_ = MemorySegment.ofBuffer(isuppz);
        return LAPACKE_dsyevr(layout.lapack(), jobz.lapack(), range.lapack(), uplo.lapack(), n,
                A_, lda, vl, vu, il, iu, abstol, m_, w_, Z_, ldz, isuppz_);
    }

    /**
     * Computes the eigenvalues and, optionally, the left and/or right
     * eigenvectors of a real symmetric matrix A. Eigenvalues and
     * eigenvectors can be selected by specifying either a range of
     * values or a range of indices for the desired eigenvalues.
     * <p>
     * SYEVR first reduces the matrix A to tridiagonal form T with a call
     * to DSYTRD. Then, whenever possible, DSYEVR calls DSTEMR to compute
     * the eigenspectrum using Relatively Robust Representations. DSTEMR
     * computes eigenvalues by the dqds algorithm, while orthogonal
     * eigenvectors are computed from various "good" L D L^T representations
     * (also known as Relatively Robust Representations). Gram-Schmidt
     * orthogonalization is avoided as far as possible. More specifically,
     * the various steps of the algorithm are as follows.
     * <p>
     * The desired accuracy of the output can be specified by the input
     * parameter ABSTOL.
     *
     * @param layout The matrix layout.
     *
     * @param jobz The option if computing eigen vectors.
     *
     * @param range The range of eigenvalues to compute.
     *
     * @param uplo The upper or lower triangular part of the matrix A is
     *             to be referenced.
     *
     * @param n The dimension of the matrix A.
     *
     * @param A The matrix of dimension (LDA, N).
     *          On entry, the N-by-N matrix A.
     *          On exit, A has been overwritten.
     *
     * @param lda The leading dimension of the matrix A. {@code LDA >= max(1,N)}.
     *
     * @param vl The lower bound of the interval to be searched for eigenvalues.
     *           Not referenced if RANGE = 'A' or 'I'.
     *
     * @param vu The upper bound of the interval to be searched for eigenvalues.
     *           Not referenced if RANGE = 'A' or 'I'.
     *
     * @param il The index of the smallest eigenvalue to be returned.
     *           Not referenced if RANGE = 'A' or 'V'.
     *
     * @param iu The index of the largest eigenvalue to be returned.
     *           Not referenced if RANGE = 'A' or 'V'.
     *
     * @param abstol The absolute error tolerance for the eigenvalues.
     *
     * @param m The total number of eigenvalues found.
     *
     * @param w The first M elements contain the selected eigenvalues in
     *          ascending order.
     *
     * @param Z Dimension (LDZ, max(1,M)).
     *          If JOBZ = 'V', then if INFO = 0, the first M columns of Z
     *          contain the orthonormal eigenvectors of the matrix A
     *          corresponding to the selected eigenvalues, with the i-th
     *          column of Z holding the eigenvector associated with W(i).
     *          If JOBZ = 'N', then Z is not referenced.
     *
     * @param ldz The leading dimension of the matrix Z.
     *
     * @param isuppz Dimension 2 * max(1,M).
     *               The support of the eigenvectors in Z, i.e., the indices
     *               indicating the nonzero elements in Z. The i-th eigenvector
     *               is nonzero only in elements ISUPPZ( 2*i-1 ) through
     *               ISUPPZ( 2*i ). This is an output of DSTEMR (tridiagonal
     *               matrix). The support of the eigenvectors of A is typically
     *               1:N because of the orthogonal transformations applied by DORMTR.
     *               Implemented only for RANGE = 'A' or 'I' and IU - IL = N - 1.
     *
     * @return INFO flag.
     *         {@code = 0}:  successful exit.
     *         {@code < 0}:  if {@code INFO = -i}, the i-th argument had an illegal value.
     *         {@code > 0}:  Internal error
     */
    static int syevr(Layout layout, EVDJob jobz, EigenRange range, UPLO uplo, int n, float[] A, int lda, float vl,
                     float vu, int il, int iu, float abstol, int[] m, float[] w, float[] Z, int ldz, int[] isuppz) {
        var A_ = MemorySegment.ofArray(A);
        var m_ = MemorySegment.ofArray(m);
        var w_ = MemorySegment.ofArray(w);
        var Z_ = MemorySegment.ofArray(Z);
        var isuppz_ = MemorySegment.ofArray(isuppz);
        return LAPACKE_ssyevr(layout.lapack(), jobz.lapack(), range.lapack(), uplo.lapack(), n,
                A_, lda, vl, vu, il, iu, abstol, m_, w_, Z_, ldz, isuppz_);
    }

    /**
     * Computes the eigenvalues and, optionally, the left and/or right
     * eigenvectors of a real symmetric matrix A. Eigenvalues and
     * eigenvectors can be selected by specifying either a range of
     * values or a range of indices for the desired eigenvalues.
     * <p>
     * SYEVR first reduces the matrix A to tridiagonal form T with a call
     * to DSYTRD. Then, whenever possible, DSYEVR calls DSTEMR to compute
     * the eigenspectrum using Relatively Robust Representations. DSTEMR
     * computes eigenvalues by the dqds algorithm, while orthogonal
     * eigenvectors are computed from various "good" L D L^T representations
     * (also known as Relatively Robust Representations). Gram-Schmidt
     * orthogonalization is avoided as far as possible. More specifically,
     * the various steps of the algorithm are as follows.
     * <p>
     * The desired accuracy of the output can be specified by the input
     * parameter ABSTOL.
     *
     * @param layout The matrix layout.
     *
     * @param jobz The option if computing eigen vectors.
     *
     * @param range The range of eigenvalues to compute.
     *
     * @param uplo The upper or lower triangular part of the matrix A is
     *             to be referenced.
     *
     * @param n The dimension of the matrix A.
     *
     * @param A The matrix of dimension (LDA, N).
     *          On entry, the N-by-N matrix A.
     *          On exit, A has been overwritten.
     *
     * @param lda The leading dimension of the matrix A. {@code LDA >= max(1,N)}.
     *
     * @param vl The lower bound of the interval to be searched for eigenvalues.
     *           Not referenced if RANGE = 'A' or 'I'.
     *
     * @param vu The upper bound of the interval to be searched for eigenvalues.
     *           Not referenced if RANGE = 'A' or 'I'.
     *
     * @param il The index of the smallest eigenvalue to be returned.
     *           Not referenced if RANGE = 'A' or 'V'.
     *
     * @param iu The index of the largest eigenvalue to be returned.
     *           Not referenced if RANGE = 'A' or 'V'.
     *
     * @param abstol The absolute error tolerance for the eigenvalues.
     *
     * @param m The total number of eigenvalues found.
     *
     * @param w The first M elements contain the selected eigenvalues in
     *          ascending order.
     *
     * @param Z Dimension (LDZ, max(1,M)).
     *          If JOBZ = 'V', then if INFO = 0, the first M columns of Z
     *          contain the orthonormal eigenvectors of the matrix A
     *          corresponding to the selected eigenvalues, with the i-th
     *          column of Z holding the eigenvector associated with W(i).
     *          If JOBZ = 'N', then Z is not referenced.
     *
     * @param ldz The leading dimension of the matrix Z.
     *
     * @param isuppz Dimension 2 * max(1,M).
     *               The support of the eigenvectors in Z, i.e., the indices
     *               indicating the nonzero elements in Z. The i-th eigenvector
     *               is nonzero only in elements ISUPPZ( 2*i-1 ) through
     *               ISUPPZ( 2*i ). This is an output of DSTEMR (tridiagonal
     *               matrix). The support of the eigenvectors of A is typically
     *               1:N because of the orthogonal transformations applied by DORMTR.
     *               Implemented only for RANGE = 'A' or 'I' and IU - IL = N - 1.
     *
     * @return INFO flag.
     *         {@code = 0}:  successful exit.
     *         {@code < 0}:  if {@code INFO = -i}, the i-th argument had an illegal value.
     *         {@code > 0}:  Internal error
     */
    static int syevr(Layout layout, EVDJob jobz, EigenRange range, UPLO uplo, int n, FloatBuffer A, int lda, float vl,
                     float vu, int il, int iu, float abstol, IntBuffer m, FloatBuffer w, FloatBuffer Z, int ldz, IntBuffer isuppz) {
        var A_ = MemorySegment.ofBuffer(A);
        var m_ = MemorySegment.ofBuffer(m);
        var w_ = MemorySegment.ofBuffer(w);
        var Z_ = MemorySegment.ofBuffer(Z);
        var isuppz_ = MemorySegment.ofBuffer(isuppz);
        return LAPACKE_ssyevr(layout.lapack(), jobz.lapack(), range.lapack(), uplo.lapack(), n,
                A_, lda, vl, vu, il, iu, abstol, m_, w_, Z_, ldz, isuppz_);
    }

    /**
     * Computes the singular value decomposition (SVD) of a real
     * M-by-N matrix A, optionally computing the left and/or right singular
     * vectors.
     *
     * @param layout The matrix layout.
     *
     * @param jobu The option for computing all or part of the matrix U.
     *
     * @param jobvt The option for computing all or part of the matrix VT.
     *
     * @param m The number of rows of the matrix A.
     *
     * @param n The number of columns of the matrix A.
     *
     * @param A The matrix of dimension (LDA, N).
     * <p>
     *          If JOBU = 'O', A is overwritten with the first min(m,n)
     *          columns of U (the left singular vectors, stored columnwise).
     * <p>
     *          If JOBVT = 'O',A is overwritten with the first min(m,n)
     *          rows of VT (the right singular vectors, stored rowwise).
     * <p>
     *          If JOBU != 'O' and JOBVT != 'O', the contents of A
     *          are destroyed.
     *
     * @param lda The leading dimension of the matrix A. {@code LDA >= max(1,M)}.
     *
     * @param s The singular values of A, sorted so that {@code S(i) >= S(i+1)}.
     *          Dimension min(M,N).
     *
     * @param U If JOBU = 'N' or 'O', U is not referenced.
     *
     * @param ldu The leading dimension of the matrix U.
     *
     * @param VT If JOBVT = 'N' or 'O', VT is not referenced.
     *
     * @param ldvt The leading dimension of the matrix VT.
     *
     * @param superb The superdiagonal of the upper bidiagonal matrix B.
     *               Dimension min(M,N)-1.
     *
     * @return INFO flag.
     *         {@code = 0}:  successful exit.
     *         {@code < 0}:  if {@code INFO = -i}, the i-th argument had an illegal value.
     *         {@code > 0}:  if DBDSQR did not converge, INFO specifies how many
     *               superdiagonals of an intermediate bidiagonal form B
     *               did not converge to zero.
     */
    static int gesvd(Layout layout, SVDJob jobu, SVDJob jobvt, int m, int n, double[] A, int lda, double[] s,
                     double[] U, int ldu, double[] VT, int ldvt, double[] superb) {
        var A_ = MemorySegment.ofArray(A);
        var s_ = MemorySegment.ofArray(s);
        var U_ = MemorySegment.ofArray(U);
        var VT_ = MemorySegment.ofArray(VT);
        var superb_ = MemorySegment.ofArray(superb);
        return LAPACKE_dgesvd(layout.lapack(), jobu.lapack(), jobvt.lapack(), m, n,
                A_, lda, s_, U_, ldu, VT_, ldvt, superb_);
    }

    /**
     * Computes the singular value decomposition (SVD) of a real
     * M-by-N matrix A, optionally computing the left and/or right singular
     * vectors.
     *
     * @param layout The matrix layout.
     *
     * @param jobu The option for computing all or part of the matrix U.
     *
     * @param jobvt The option for computing all or part of the matrix VT.
     *
     * @param m The number of rows of the matrix A.
     *
     * @param n The number of columns of the matrix A.
     *
     * @param A The matrix of dimension (LDA, N).
     * <p>
     *          If JOBU = 'O', A is overwritten with the first min(m,n)
     *          columns of U (the left singular vectors, stored columnwise).
     * <p>
     *          If JOBVT = 'O',A is overwritten with the first min(m,n)
     *          rows of VT (the right singular vectors, stored rowwise).
     * <p>
     *          If JOBU != 'O' and JOBVT != 'O', the contents of A
     *          are destroyed.
     *
     * @param lda The leading dimension of the matrix A. {@code LDA >= max(1,M)}.
     *
     * @param s The singular values of A, sorted so that {@code S(i) >= S(i+1)}.
     *          Dimension min(M,N).
     *
     * @param U If JOBU = 'N' or 'O', U is not referenced.
     *
     * @param ldu The leading dimension of the matrix U.
     *
     * @param VT If JOBVT = 'N' or 'O', VT is not referenced.
     *
     * @param ldvt The leading dimension of the matrix VT.
     *
     * @param superb The superdiagonal of the upper bidiagonal matrix B.
     *               Dimension min(M,N)-1.
     *
     * @return INFO flag.
     *         {@code = 0}:  successful exit.
     *         {@code < 0}:  if {@code INFO = -i}, the i-th argument had an illegal value.
     *         {@code > 0}:  if DBDSQR did not converge, INFO specifies how many
     *               superdiagonals of an intermediate bidiagonal form B
     *               did not converge to zero.
     */
    static int gesvd(Layout layout, SVDJob jobu, SVDJob jobvt, int m, int n, DoubleBuffer A, int lda, DoubleBuffer s,
                     DoubleBuffer U, int ldu, DoubleBuffer VT, int ldvt, DoubleBuffer superb) {
        var A_ = MemorySegment.ofBuffer(A);
        var s_ = MemorySegment.ofBuffer(s);
        var U_ = MemorySegment.ofBuffer(U);
        var VT_ = MemorySegment.ofBuffer(VT);
        var superb_ = MemorySegment.ofBuffer(superb);
        return LAPACKE_dgesvd(layout.lapack(), jobu.lapack(), jobvt.lapack(), m, n,
                A_, lda, s_, U_, ldu, VT_, ldvt, superb_);
    }

    /**
     * Computes the singular value decomposition (SVD) of a real
     * M-by-N matrix A, optionally computing the left and/or right singular
     * vectors.
     *
     * @param layout The matrix layout.
     *
     * @param jobu The option for computing all or part of the matrix U.
     *
     * @param jobvt The option for computing all or part of the matrix VT.
     *
     * @param m The number of rows of the matrix A.
     *
     * @param n The number of columns of the matrix A.
     *
     * @param A The matrix of dimension (LDA, N).
     * <p>
     *          If JOBU = 'O', A is overwritten with the first min(m,n)
     *          columns of U (the left singular vectors, stored columnwise).
     * <p>
     *          If JOBVT = 'O',A is overwritten with the first min(m,n)
     *          rows of VT (the right singular vectors, stored rowwise).
     * <p>
     *          If JOBU != 'O' and JOBVT != 'O', the contents of A
     *          are destroyed.
     *
     * @param lda The leading dimension of the matrix A. {@code LDA >= max(1,M)}.
     *
     * @param s The singular values of A, sorted so that {@code S(i) >= S(i+1)}.
     *          Dimension min(M,N).
     *
     * @param U If JOBU = 'N' or 'O', U is not referenced.
     *
     * @param ldu The leading dimension of the matrix U.
     *
     * @param VT If JOBVT = 'N' or 'O', VT is not referenced.
     *
     * @param ldvt The leading dimension of the matrix VT.
     *
     * @param superb The superdiagonal of the upper bidiagonal matrix B.
     *               Dimension min(M,N)-1.
     *
     * @return INFO flag.
     *         {@code = 0}:  successful exit.
     *         {@code < 0}:  if {@code INFO = -i}, the i-th argument had an illegal value.
     *         {@code > 0}:  if DBDSQR did not converge, INFO specifies how many
     *               superdiagonals of an intermediate bidiagonal form B
     *               did not converge to zero.
     */
    static int gesvd(Layout layout, SVDJob jobu, SVDJob jobvt, int m, int n, float[] A, int lda, float[] s,
                     float[] U, int ldu, float[] VT, int ldvt, float[] superb) {
        var A_ = MemorySegment.ofArray(A);
        var s_ = MemorySegment.ofArray(s);
        var U_ = MemorySegment.ofArray(U);
        var VT_ = MemorySegment.ofArray(VT);
        var superb_ = MemorySegment.ofArray(superb);
        return LAPACKE_sgesvd(layout.lapack(), jobu.lapack(), jobvt.lapack(), m, n,
                A_, lda, s_, U_, ldu, VT_, ldvt, superb_);
    }

    /**
     * Computes the singular value decomposition (SVD) of a real
     * M-by-N matrix A, optionally computing the left and/or right singular
     * vectors.
     *
     * @param layout The matrix layout.
     *
     * @param jobu The option for computing all or part of the matrix U.
     *
     * @param jobvt The option for computing all or part of the matrix VT.
     *
     * @param m The number of rows of the matrix A.
     *
     * @param n The number of columns of the matrix A.
     *
     * @param A The matrix of dimension (LDA, N).
     * <p>
     *          If JOBU = 'O', A is overwritten with the first min(m,n)
     *          columns of U (the left singular vectors, stored columnwise).
     * <p>
     *          If JOBVT = 'O',A is overwritten with the first min(m,n)
     *          rows of VT (the right singular vectors, stored rowwise).
     * <p>
     *          If JOBU != 'O' and JOBVT != 'O', the contents of A
     *          are destroyed.
     *
     * @param lda The leading dimension of the matrix A. {@code LDA >= max(1,M)}.
     *
     * @param s The singular values of A, sorted so that {@code S(i) >= S(i+1)}.
     *          Dimension min(M,N).
     *
     * @param U If JOBU = 'N' or 'O', U is not referenced.
     *
     * @param ldu The leading dimension of the matrix U.
     *
     * @param VT If JOBVT = 'N' or 'O', VT is not referenced.
     *
     * @param ldvt The leading dimension of the matrix VT.
     *
     * @param superb The superdiagonal of the upper bidiagonal matrix B.
     *               Dimension min(M,N)-1.
     *
     * @return INFO flag.
     *         {@code = 0}:  successful exit.
     *         {@code < 0}:  if {@code INFO = -i}, the i-th argument had an illegal value.
     *         {@code > 0}:  if DBDSQR did not converge, INFO specifies how many
     *               superdiagonals of an intermediate bidiagonal form B
     *               did not converge to zero.
     */
    static int gesvd(Layout layout, SVDJob jobu, SVDJob jobvt, int m, int n, FloatBuffer A, int lda, FloatBuffer s,
                     FloatBuffer U, int ldu, FloatBuffer VT, int ldvt, FloatBuffer superb) {
        var A_ = MemorySegment.ofBuffer(A);
        var s_ = MemorySegment.ofBuffer(s);
        var U_ = MemorySegment.ofBuffer(U);
        var VT_ = MemorySegment.ofBuffer(VT);
        var superb_ = MemorySegment.ofBuffer(superb);
        return LAPACKE_sgesvd(layout.lapack(), jobu.lapack(), jobvt.lapack(), m, n,
                A_, lda, s_, U_, ldu, VT_, ldvt, superb_);
    }

    /**
     * Computes the singular value decomposition (SVD) of a real
     * M-by-N matrix A, optionally computing the left and/or right singular
     * vectors. If singular vectors are desired, it uses a
     * divide-and-conquer algorithm.
     *
     * @param layout The matrix layout.
     *
     * @param jobz The option for computing all or part of the matrix U.
     *
     * @param m The number of rows of the matrix A.
     *
     * @param n The number of columns of the matrix A.
     *
     * @param A The matrix of dimension (LDA, N).
     * <p>
     *          If JOBZ = 'O', A is overwritten with the first N columns
     *          of U (the left singular vectors, stored columnwise) if {@code M >= N};
     *          A is overwritten with the first M rows of V<sup>T</sup> (the right
     *         singular vectors, stored rowwise) otherwise.
     * <p>
     *          If JOBZ != 'O', the contents of A are destroyed.
     *
     * @param lda The leading dimension of the matrix A. {@code LDA >= max(1,M)}.
     *
     * @param s The singular values of A, sorted so that {@code S(i) >= S(i+1)}.
     *          Dimension min(M,N).
     *
     * @param U If JOBU = 'N' or 'O', U is not referenced.
     *
     * @param ldu The leading dimension of the matrix U.
     *
     * @param VT If JOBVT = 'N' or 'O', VT is not referenced.
     *
     * @param ldvt The leading dimension of the matrix VT.
     *
     * @return INFO flag.
     *         {@code = 0}:  successful exit.
     *         {@code < 0}:  if {@code INFO = -i}, the i-th argument had an illegal value.
     *         {@code > 0}:  DBDSDC did not converge, updating process failed.
     */
    static int gesdd(Layout layout, SVDJob jobz, int m, int n, double[] A, int lda, double[] s,
                     double[] U, int ldu, double[] VT, int ldvt) {
        var A_ = MemorySegment.ofArray(A);
        var s_ = MemorySegment.ofArray(s);
        var U_ = MemorySegment.ofArray(U);
        var VT_ = MemorySegment.ofArray(VT);
        return LAPACKE_dgesdd(layout.lapack(), jobz.lapack(), m, n, A_, lda, s_, U_, ldu, VT_, ldvt);
    }

    /**
     * Computes the singular value decomposition (SVD) of a real
     * M-by-N matrix A, optionally computing the left and/or right singular
     * vectors. If singular vectors are desired, it uses a
     * divide-and-conquer algorithm.
     *
     * @param layout The matrix layout.
     *
     * @param jobz The option for computing all or part of the matrix U.
     *
     * @param m The number of rows of the matrix A.
     *
     * @param n The number of columns of the matrix A.
     *
     * @param A The matrix of dimension (LDA, N).
     * <p>
     *          If JOBZ = 'O', A is overwritten with the first N columns
     *          of U (the left singular vectors, stored columnwise) if {@code M >= N};
     *          A is overwritten with the first M rows of V<sup>T</sup> (the right
     *         singular vectors, stored rowwise) otherwise.
     * <p>
     *          If JOBZ != 'O', the contents of A are destroyed.
     *
     * @param lda The leading dimension of the matrix A. {@code LDA >= max(1,M)}.
     *
     * @param s The singular values of A, sorted so that {@code S(i) >= S(i+1)}.
     *          Dimension min(M,N).
     *
     * @param U If JOBU = 'N' or 'O', U is not referenced.
     *
     * @param ldu The leading dimension of the matrix U.
     *
     * @param VT If JOBVT = 'N' or 'O', VT is not referenced.
     *
     * @param ldvt The leading dimension of the matrix VT.
     *
     * @return INFO flag.
     *         {@code = 0}:  successful exit.
     *         {@code < 0}:  if {@code INFO = -i}, the i-th argument had an illegal value.
     *         {@code > 0}:  DBDSDC did not converge, updating process failed.
     */
    static int gesdd(Layout layout, SVDJob jobz, int m, int n, DoubleBuffer A, int lda, DoubleBuffer s,
                     DoubleBuffer U, int ldu, DoubleBuffer VT, int ldvt) {
        var A_ = MemorySegment.ofBuffer(A);
        var s_ = MemorySegment.ofBuffer(s);
        var U_ = MemorySegment.ofBuffer(U);
        var VT_ = MemorySegment.ofBuffer(VT);
        return LAPACKE_dgesdd(layout.lapack(), jobz.lapack(), m, n, A_, lda, s_, U_, ldu, VT_, ldvt);
    }

    /**
     * Computes the singular value decomposition (SVD) of a real
     * M-by-N matrix A, optionally computing the left and/or right singular
     * vectors. If singular vectors are desired, it uses a
     * divide-and-conquer algorithm.
     *
     * @param layout The matrix layout.
     *
     * @param jobz The option for computing all or part of the matrix U.
     *
     * @param m The number of rows of the matrix A.
     *
     * @param n The number of columns of the matrix A.
     *
     * @param A The matrix of dimension (LDA, N).
     * <p>
     *          If JOBZ = 'O', A is overwritten with the first N columns
     *          of U (the left singular vectors, stored columnwise) if {@code M >= N};
     *          A is overwritten with the first M rows of V<sup>T</sup> (the right
     *         singular vectors, stored rowwise) otherwise.
     * <p>
     *          If JOBZ != 'O', the contents of A are destroyed.
     *
     * @param lda The leading dimension of the matrix A. {@code LDA >= max(1,M)}.
     *
     * @param s The singular values of A, sorted so that {@code S(i) >= S(i+1)}.
     *          Dimension min(M,N).
     *
     * @param U If JOBU = 'N' or 'O', U is not referenced.
     *
     * @param ldu The leading dimension of the matrix U.
     *
     * @param VT If JOBVT = 'N' or 'O', VT is not referenced.
     *
     * @param ldvt The leading dimension of the matrix VT.
     *
     * @return INFO flag.
     *         {@code = 0}:  successful exit.
     *         {@code < 0}:  if {@code INFO = -i}, the i-th argument had an illegal value.
     *         {@code > 0}:  DBDSDC did not converge, updating process failed.
     */
    static int gesdd(Layout layout, SVDJob jobz, int m, int n, MemorySegment A, int lda, MemorySegment s,
                     MemorySegment U, int ldu, MemorySegment VT, int ldvt) {
        return LAPACKE_dgesdd(layout.lapack(), jobz.lapack(), m, n, A, lda, s, U, ldu, VT, ldvt);
    }

    /**
     * Computes the singular value decomposition (SVD) of a real
     * M-by-N matrix A, optionally computing the left and/or right singular
     * vectors. If singular vectors are desired, it uses a
     * divide-and-conquer algorithm.
     *
     * @param layout The matrix layout.
     *
     * @param jobz The option for computing all or part of the matrix U.
     *
     * @param m The number of rows of the matrix A.
     *
     * @param n The number of columns of the matrix A.
     *
     * @param A The matrix of dimension (LDA, N).
     * <p>
     *          If JOBZ = 'O', A is overwritten with the first N columns
     *          of U (the left singular vectors, stored columnwise) if {@code M >= N};
     *          A is overwritten with the first M rows of V<sup>T</sup> (the right
     *         singular vectors, stored rowwise) otherwise.
     * <p>
     *          If JOBZ != 'O', the contents of A are destroyed.
     *
     * @param lda The leading dimension of the matrix A. {@code LDA >= max(1,M)}.
     *
     * @param s The singular values of A, sorted so that {@code S(i) >= S(i+1)}.
     *          Dimension min(M,N).
     *
     * @param U If JOBU = 'N' or 'O', U is not referenced.
     *
     * @param ldu The leading dimension of the matrix U.
     *
     * @param VT If JOBVT = 'N' or 'O', VT is not referenced.
     *
     * @param ldvt The leading dimension of the matrix VT.
     *
     * @return INFO flag.
     *         {@code = 0}:  successful exit.
     *         {@code < 0}:  if {@code INFO = -i}, the i-th argument had an illegal value.
     *         {@code > 0}:  DBDSDC did not converge, updating process failed.
     */
    static int gesdd(Layout layout, SVDJob jobz, int m, int n, float[] A, int lda, float[] s,
                     float[] U, int ldu, float[] VT, int ldvt) {
        var A_ = MemorySegment.ofArray(A);
        var s_ = MemorySegment.ofArray(s);
        var U_ = MemorySegment.ofArray(U);
        var VT_ = MemorySegment.ofArray(VT);
        return LAPACKE_sgesdd(layout.lapack(), jobz.lapack(), m, n, A_, lda, s_, U_, ldu, VT_, ldvt);
    }

    /**
     * Computes the singular value decomposition (SVD) of a real
     * M-by-N matrix A, optionally computing the left and/or right singular
     * vectors. If singular vectors are desired, it uses a
     * divide-and-conquer algorithm.
     *
     * @param layout The matrix layout.
     *
     * @param jobz The option for computing all or part of the matrix U.
     *
     * @param m The number of rows of the matrix A.
     *
     * @param n The number of columns of the matrix A.
     *
     * @param A The matrix of dimension (LDA, N).
     * <p>
     *          If JOBZ = 'O', A is overwritten with the first N columns
     *          of U (the left singular vectors, stored columnwise) if {@code M >= N};
     *          A is overwritten with the first M rows of V<sup>T</sup> (the right
     *         singular vectors, stored rowwise) otherwise.
     * <p>
     *          If JOBZ != 'O', the contents of A are destroyed.
     *
     * @param lda The leading dimension of the matrix A. {@code LDA >= max(1,M)}.
     *
     * @param s The singular values of A, sorted so that {@code S(i) >= S(i+1)}.
     *          Dimension min(M,N).
     *
     * @param U If JOBU = 'N' or 'O', U is not referenced.
     *
     * @param ldu The leading dimension of the matrix U.
     *
     * @param VT If JOBVT = 'N' or 'O', VT is not referenced.
     *
     * @param ldvt The leading dimension of the matrix VT.
     *
     * @return INFO flag.
     *         {@code = 0}:  successful exit.
     *         {@code < 0}:  if {@code INFO = -i}, the i-th argument had an illegal value.
     *         {@code > 0}:  DBDSDC did not converge, updating process failed.
     */
    static int gesdd(Layout layout, SVDJob jobz, int m, int n, FloatBuffer A, int lda, FloatBuffer s,
                     FloatBuffer U, int ldu, FloatBuffer VT, int ldvt) {
        var A_ = MemorySegment.ofBuffer(A);
        var s_ = MemorySegment.ofBuffer(s);
        var U_ = MemorySegment.ofBuffer(U);
        var VT_ = MemorySegment.ofBuffer(VT);
        return LAPACKE_sgesdd(layout.lapack(), jobz.lapack(), m, n, A_, lda, s_, U_, ldu, VT_, ldvt);
    }

    /**
     * Computes an LU factorization of a general M-by-N matrix A
     * using partial pivoting with row interchanges.
     *
     * @param layout The matrix layout.
     *
     * @param m The number of rows of the matrix A.
     *
     * @param n The number of columns of the matrix A.
     *
     * @param A The matrix of dimension (LDA, N).
     *          On exit, the factors L and U from the factorization
     *          A = P*L*U; the unit diagonal elements of L are not stored.
     *
     * @param lda The leading dimension of the matrix A. {@code LDA >= max(1,N)}.
     *
     * @param ipiv The pivot indices; for {@code 1 <= i <= min(M,N)}, row i of the
     *             matrix was interchanged with row IPIV(i). Dimension min(M,N).
     *
     * @return INFO flag.
     *         {@code = 0}:  successful exit
     *         {@code < 0}:  if {@code INFO = -i}, the i-th argument had an illegal value
     *         {@code > 0}:  if {@code INFO = i}, U(i,i) is exactly zero. The factorization
     *               has been completed, but the factor U is exactly
     *               singular, and division by zero will occur if it is used
     *               to solve a system of equations.
     */
    static int getrf(Layout layout, int m, int n, double[] A, int lda, int[] ipiv) {
        var A_ = MemorySegment.ofArray(A);
        var ipiv_ = MemorySegment.ofArray(ipiv);
        return LAPACKE_dgetrf(layout.lapack(), m, n, A_, lda, ipiv_);
    }

    /**
     * Computes an LU factorization of a general M-by-N matrix A
     * using partial pivoting with row interchanges.
     *
     * @param layout The matrix layout.
     *
     * @param m The number of rows of the matrix A.
     *
     * @param n The number of columns of the matrix A.
     *
     * @param A The matrix of dimension (LDA, N).
     *          On exit, the factors L and U from the factorization
     *          A = P*L*U; the unit diagonal elements of L are not stored.
     *
     * @param lda The leading dimension of the matrix A. {@code LDA >= max(1,N)}.
     *
     * @param ipiv The pivot indices; for {@code 1 <= i <= min(M,N)}, row i of the
     *             matrix was interchanged with row IPIV(i). Dimension min(M,N).
     *
     * @return INFO flag.
     *         {@code = 0}:  successful exit
     *         {@code < 0}:  if {@code INFO = -i}, the i-th argument had an illegal value
     *         {@code > 0}:  if {@code INFO = i}, U(i,i) is exactly zero. The factorization
     *               has been completed, but the factor U is exactly
     *               singular, and division by zero will occur if it is used
     *               to solve a system of equations.
     */
    static int getrf(Layout layout, int m, int n, DoubleBuffer A, int lda, IntBuffer ipiv) {
        var A_ = MemorySegment.ofBuffer(A);
        var ipiv_ = MemorySegment.ofBuffer(ipiv);
        return LAPACKE_dgetrf(layout.lapack(), m, n, A_, lda, ipiv_);
    }

    /**
     * Computes an LU factorization of a general M-by-N matrix A
     * using partial pivoting with row interchanges.
     *
     * @param layout The matrix layout.
     *
     * @param m The number of rows of the matrix A.
     *
     * @param n The number of columns of the matrix A.
     *
     * @param A The matrix of dimension (LDA, N).
     *          On exit, the factors L and U from the factorization
     *          A = P*L*U; the unit diagonal elements of L are not stored.
     *
     * @param lda The leading dimension of the matrix A. {@code LDA >= max(1,N)}.
     *
     * @param ipiv The pivot indices; for {@code 1 <= i <= min(M,N)}, row i of the
     *             matrix was interchanged with row IPIV(i). Dimension min(M,N).
     *
     * @return INFO flag.
     *         {@code = 0}:  successful exit
     *         {@code < 0}:  if {@code INFO = -i}, the i-th argument had an illegal value
     *         {@code > 0}:  if {@code INFO = i}, U(i,i) is exactly zero. The factorization
     *               has been completed, but the factor U is exactly
     *               singular, and division by zero will occur if it is used
     *               to solve a system of equations.
     */
    static int getrf(Layout layout, int m, int n, MemorySegment A, int lda, MemorySegment ipiv) {
        return LAPACKE_dgetrf(layout.lapack(), m, n, A, lda, ipiv);
    }

    /**
     * Computes an LU factorization of a general M-by-N matrix A
     * using partial pivoting with row interchanges.
     *
     * @param layout The matrix layout.
     *
     * @param m The number of rows of the matrix A.
     *
     * @param n The number of columns of the matrix A.
     *
     * @param A The matrix of dimension (LDA, N).
     *          On exit, the factors L and U from the factorization
     *          A = P*L*U; the unit diagonal elements of L are not stored.
     *
     * @param lda The leading dimension of the matrix A. {@code LDA >= max(1,N)}.
     *
     * @param ipiv The pivot indices; for {@code 1 <= i <= min(M,N)}, row i of the
     *             matrix was interchanged with row IPIV(i). Dimension min(M,N).
     *
     * @return INFO flag.
     *         {@code = 0}:  successful exit
     *         {@code < 0}:  if {@code INFO = -i}, the i-th argument had an illegal value
     *         {@code > 0}:  if {@code INFO = i}, U(i,i) is exactly zero. The factorization
     *               has been completed, but the factor U is exactly
     *               singular, and division by zero will occur if it is used
     *               to solve a system of equations.
     */
    static int getrf(Layout layout, int m, int n, float[] A, int lda, int[] ipiv) {
        var A_ = MemorySegment.ofArray(A);
        var ipiv_ = MemorySegment.ofArray(ipiv);
        return LAPACKE_sgetrf(layout.lapack(), m, n, A_, lda, ipiv_);
    }

    /**
     * Computes an LU factorization of a general M-by-N matrix A
     * using partial pivoting with row interchanges.
     *
     * @param layout The matrix layout.
     *
     * @param m The number of rows of the matrix A.
     *
     * @param n The number of columns of the matrix A.
     *
     * @param A The matrix of dimension (LDA, N).
     *          On exit, the factors L and U from the factorization
     *          A = P*L*U; the unit diagonal elements of L are not stored.
     *
     * @param lda The leading dimension of the matrix A. {@code LDA >= max(1,N)}.
     *
     * @param ipiv The pivot indices; for {@code 1 <= i <= min(M,N)}, row i of the
     *             matrix was interchanged with row IPIV(i). Dimension min(M,N).
     *
     * @return INFO flag.
     *         {@code = 0}:  successful exit
     *         {@code < 0}:  if {@code INFO = -i}, the i-th argument had an illegal value
     *         {@code > 0}:  if {@code INFO = i}, U(i,i) is exactly zero. The factorization
     *               has been completed, but the factor U is exactly
     *               singular, and division by zero will occur if it is used
     *               to solve a system of equations.
     */
    static int getrf(Layout layout, int m, int n, FloatBuffer A, int lda, IntBuffer ipiv) {
        var A_ = MemorySegment.ofBuffer(A);
        var ipiv_ = MemorySegment.ofBuffer(ipiv);
        return LAPACKE_sgetrf(layout.lapack(), m, n, A_, lda, ipiv_);
    }

    /**
     * Computes an LU factorization of a general M-by-N matrix A
     * using partial pivoting with row interchanges. This is the
     * recursive version of the algorithm.
     *
     * @param layout The matrix layout.
     *
     * @param m The number of rows of the matrix A.
     *
     * @param n The number of columns of the matrix A.
     *
     * @param A The matrix of dimension (LDA, N).
     *          On exit, the factors L and U from the factorization
     *          A = P*L*U; the unit diagonal elements of L are not stored.
     *
     * @param lda The leading dimension of the matrix A. {@code LDA >= max(1,N)}.
     *
     * @param ipiv The pivot indices; for {@code 1 <= i <= min(M,N)}, row i of the
     *             matrix was interchanged with row IPIV(i). Dimension min(M,N).
     *
     * @return INFO flag.
     *         {@code = 0}:  successful exit
     *         {@code < 0}:  if {@code INFO = -i}, the i-th argument had an illegal value
     *         {@code > 0}:  if {@code INFO = i}, U(i,i) is exactly zero. The factorization
     *               has been completed, but the factor U is exactly
     *               singular, and division by zero will occur if it is used
     *               to solve a system of equations.
     */
    static int getrf2(Layout layout, int m, int n, double[] A, int lda, int[] ipiv) {
        var A_ = MemorySegment.ofArray(A);
        var ipiv_ = MemorySegment.ofArray(ipiv);
        return LAPACKE_dgetrf2(layout.lapack(), m, n, A_, lda, ipiv_);
    }

    /**
     * Computes an LU factorization of a general M-by-N matrix A
     * using partial pivoting with row interchanges. This is the
     * recursive version of the algorithm.
     *
     * @param layout The matrix layout.
     *
     * @param m The number of rows of the matrix A.
     *
     * @param n The number of columns of the matrix A.
     *
     * @param A The matrix of dimension (LDA, N).
     *          On exit, the factors L and U from the factorization
     *          A = P*L*U; the unit diagonal elements of L are not stored.
     *
     * @param lda The leading dimension of the matrix A. {@code LDA >= max(1,N)}.
     *
     * @param ipiv The pivot indices; for {@code 1 <= i <= min(M,N)}, row i of the
     *             matrix was interchanged with row IPIV(i). Dimension min(M,N).
     *
     * @return INFO flag.
     *         {@code = 0}:  successful exit
     *         {@code < 0}:  if {@code INFO = -i}, the i-th argument had an illegal value
     *         {@code > 0}:  if {@code INFO = i}, U(i,i) is exactly zero. The factorization
     *               has been completed, but the factor U is exactly
     *               singular, and division by zero will occur if it is used
     *               to solve a system of equations.
     */
    static int getrf2(Layout layout, int m, int n, DoubleBuffer A, int lda, IntBuffer ipiv) {
        var A_ = MemorySegment.ofBuffer(A);
        var ipiv_ = MemorySegment.ofBuffer(ipiv);
        return LAPACKE_dgetrf2(layout.lapack(), m, n, A_, lda, ipiv_);
    }

    /**
     * Computes an LU factorization of a general M-by-N matrix A
     * using partial pivoting with row interchanges. This is the
     * recursive version of the algorithm.
     *
     * @param layout The matrix layout.
     *
     * @param m The number of rows of the matrix A.
     *
     * @param n The number of columns of the matrix A.
     *
     * @param A The matrix of dimension (LDA, N).
     *          On exit, the factors L and U from the factorization
     *          A = P*L*U; the unit diagonal elements of L are not stored.
     *
     * @param lda The leading dimension of the matrix A. {@code LDA >= max(1,N)}.
     *
     * @param ipiv The pivot indices; for {@code 1 <= i <= min(M,N)}, row i of the
     *             matrix was interchanged with row IPIV(i). Dimension min(M,N).
     *
     * @return INFO flag.
     *         {@code = 0}:  successful exit
     *         {@code < 0}:  if {@code INFO = -i}, the i-th argument had an illegal value
     *         {@code > 0}:  if {@code INFO = i}, U(i,i) is exactly zero. The factorization
     *               has been completed, but the factor U is exactly
     *               singular, and division by zero will occur if it is used
     *               to solve a system of equations.
     */
    static int getrf2(Layout layout, int m, int n, float[] A, int lda, int[] ipiv) {
        var A_ = MemorySegment.ofArray(A);
        var ipiv_ = MemorySegment.ofArray(ipiv);
        return LAPACKE_sgetrf2(layout.lapack(), m, n, A_, lda, ipiv_);
    }

    /**
     * Computes an LU factorization of a general M-by-N matrix A
     * using partial pivoting with row interchanges. This is the
     * recursive version of the algorithm.
     *
     * @param layout The matrix layout.
     *
     * @param m The number of rows of the matrix A.
     *
     * @param n The number of columns of the matrix A.
     *
     * @param A The matrix of dimension (LDA, N).
     *          On exit, the factors L and U from the factorization
     *          A = P*L*U; the unit diagonal elements of L are not stored.
     *
     * @param lda The leading dimension of the matrix A. {@code LDA >= max(1,N)}.
     *
     * @param ipiv The pivot indices; for {@code 1 <= i <= min(M,N)}, row i of the
     *             matrix was interchanged with row IPIV(i). Dimension min(M,N).
     *
     * @return INFO flag.
     *         {@code = 0}:  successful exit
     *         {@code < 0}:  if {@code INFO = -i}, the i-th argument had an illegal value
     *         {@code > 0}:  if {@code INFO = i}, U(i,i) is exactly zero. The factorization
     *               has been completed, but the factor U is exactly
     *               singular, and division by zero will occur if it is used
     *               to solve a system of equations.
     */
    static int getrf2(Layout layout, int m, int n, FloatBuffer A, int lda, IntBuffer ipiv) {
        var A_ = MemorySegment.ofBuffer(A);
        var ipiv_ = MemorySegment.ofBuffer(ipiv);
        return LAPACKE_sgetrf2(layout.lapack(), m, n, A_, lda, ipiv_);
    }

    /**
     * Computes an LU factorization of a band matrix A
     * using partial pivoting with row interchanges.
     *
     * @param layout The matrix layout.
     *
     * @param m The number of rows of the matrix A.
     *
     * @param n The number of columns of the matrix A.
     *
     * @param kl The number of subdiagonal elements of band matrix.
     *
     * @param ku The number of superdiagonal elements of band matrix.
     *
     * @param AB The matrix of dimension (LDA, N).
     *          On exit, the factors L and U from the factorization
     *          A = P*L*U; the unit diagonal elements of L are not stored.
     *
     * @param ldab The leading dimension of the matrix A. {@code LDA >= max(1,N)}.
     *
     * @param ipiv The pivot indices; for {@code 1 <= i <= min(M,N)}, row i of the
     *             matrix was interchanged with row IPIV(i). Dimension min(M,N).
     *
     * @return INFO flag.
     *         {@code = 0}:  successful exit
     *         {@code < 0}:  if {@code INFO = -i}, the i-th argument had an illegal value
     *         {@code > 0}:  if {@code INFO = i}, U(i,i) is exactly zero. The factorization
     *               has been completed, but the factor U is exactly
     *               singular, and division by zero will occur if it is used
     *               to solve a system of equations.
     */
    static int gbtrf(Layout layout, int m, int n, int kl, int ku, double[] AB, int ldab, int[] ipiv) {
        var AB_ = MemorySegment.ofArray(AB);
        var ipiv_ = MemorySegment.ofArray(ipiv);
        return LAPACKE_dgbtrf(layout.lapack(), m, n, kl, ku, AB_, ldab, ipiv_);
    }

    /**
     * Computes an LU factorization of a band matrix A
     * using partial pivoting with row interchanges.
     *
     * @param layout The matrix layout.
     *
     * @param m The number of rows of the matrix A.
     *
     * @param n The number of columns of the matrix A.
     *
     * @param kl The number of subdiagonal elements of band matrix.
     *
     * @param ku The number of superdiagonal elements of band matrix.
     *
     * @param AB The matrix of dimension (LDA, N).
     *          On exit, the factors L and U from the factorization
     *          A = P*L*U; the unit diagonal elements of L are not stored.
     *
     * @param ldab The leading dimension of the matrix A. {@code LDA >= max(1,N)}.
     *
     * @param ipiv The pivot indices; for {@code 1 <= i <= min(M,N)}, row i of the
     *             matrix was interchanged with row IPIV(i). Dimension min(M,N).
     *
     * @return INFO flag.
     *         {@code = 0}:  successful exit
     *         {@code < 0}:  if {@code INFO = -i}, the i-th argument had an illegal value
     *         {@code > 0}:  if {@code INFO = i}, U(i,i) is exactly zero. The factorization
     *               has been completed, but the factor U is exactly
     *               singular, and division by zero will occur if it is used
     *               to solve a system of equations.
     */
    static int gbtrf(Layout layout, int m, int n, int kl, int ku, DoubleBuffer AB, int ldab, IntBuffer ipiv) {
        var AB_ = MemorySegment.ofBuffer(AB);
        var ipiv_ = MemorySegment.ofBuffer(ipiv);
        return LAPACKE_dgbtrf(layout.lapack(), m, n, kl, ku, AB_, ldab, ipiv_);
    }

    /**
     * Computes an LU factorization of a band matrix A
     * using partial pivoting with row interchanges.
     *
     * @param layout The matrix layout.
     *
     * @param m The number of rows of the matrix A.
     *
     * @param n The number of columns of the matrix A.
     *
     * @param kl The number of subdiagonal elements of band matrix.
     *
     * @param ku The number of superdiagonal elements of band matrix.
     *
     * @param AB The matrix of dimension (LDA, N).
     *          On exit, the factors L and U from the factorization
     *          A = P*L*U; the unit diagonal elements of L are not stored.
     *
     * @param ldab The leading dimension of the matrix A. {@code LDA >= max(1,N)}.
     *
     * @param ipiv The pivot indices; for {@code 1 <= i <= min(M,N)}, row i of the
     *             matrix was interchanged with row IPIV(i). Dimension min(M,N).
     *
     * @return INFO flag.
     *         {@code = 0}:  successful exit
     *         {@code < 0}:  if {@code INFO = -i}, the i-th argument had an illegal value
     *         {@code > 0}:  if {@code INFO = i}, U(i,i) is exactly zero. The factorization
     *               has been completed, but the factor U is exactly
     *               singular, and division by zero will occur if it is used
     *               to solve a system of equations.
     */
    static int gbtrf(Layout layout, int m, int n, int kl, int ku, float[] AB, int ldab, int[] ipiv) {
        var AB_ = MemorySegment.ofArray(AB);
        var ipiv_ = MemorySegment.ofArray(ipiv);
        return LAPACKE_sgbtrf(layout.lapack(), m, n, kl, ku, AB_, ldab, ipiv_);
    }

    /**
     * Computes an LU factorization of a band matrix A
     * using partial pivoting with row interchanges.
     *
     * @param layout The matrix layout.
     *
     * @param m The number of rows of the matrix A.
     *
     * @param n The number of columns of the matrix A.
     *
     * @param kl The number of subdiagonal elements of band matrix.
     *
     * @param ku The number of superdiagonal elements of band matrix.
     *
     * @param AB The matrix of dimension (LDA, N).
     *          On exit, the factors L and U from the factorization
     *          A = P*L*U; the unit diagonal elements of L are not stored.
     *
     * @param ldab The leading dimension of the matrix A. {@code LDA >= max(1,N)}.
     *
     * @param ipiv The pivot indices; for {@code 1 <= i <= min(M,N)}, row i of the
     *             matrix was interchanged with row IPIV(i). Dimension min(M,N).
     *
     * @return INFO flag.
     *         {@code = 0}:  successful exit
     *         {@code < 0}:  if {@code INFO = -i}, the i-th argument had an illegal value
     *         {@code > 0}:  if {@code INFO = i}, U(i,i) is exactly zero. The factorization
     *               has been completed, but the factor U is exactly
     *               singular, and division by zero will occur if it is used
     *               to solve a system of equations.
     */
    static int gbtrf(Layout layout, int m, int n, int kl, int ku, FloatBuffer AB, int ldab, IntBuffer ipiv) {
        var AB_ = MemorySegment.ofBuffer(AB);
        var ipiv_ = MemorySegment.ofBuffer(ipiv);
        return LAPACKE_sgbtrf(layout.lapack(), m, n, kl, ku, AB_, ldab, ipiv_);
    }

    /**
     * Computes the Bunch–Kaufman factorization of a symmetric packed matrix A.
     *
     * @param layout The matrix layout.
     *
     * @param uplo The upper or lower triangular part of the matrix A is
     *             to be referenced.
     *
     * @param n The dimension of the matrix A.
     *
     * @param AP The packed matrix.
     *
     * @param ipiv The pivot indices; for {@code 1 <= i <= min(M,N)}, row i of the
     *             matrix was interchanged with row IPIV(i). Dimension min(M,N).
     *
     * @return INFO flag.
     *         {@code = 0}:  successful exit
     *         {@code < 0}:  if {@code INFO = -i}, the i-th argument had an illegal value
     *         {@code > 0}:  if {@code INFO = i}, D(i,i) is exactly zero. The factorization
     *               has been completed, but the block diagonal matrix D is
     *               exactly singular, and division by zero will occur if it
     *               is used to solve a system of equations.
     */
    static int sptrf(Layout layout, UPLO uplo, int n, double[] AP, int[] ipiv) {
        var AP_ = MemorySegment.ofArray(AP);
        var ipiv_ = MemorySegment.ofArray(ipiv);
        return LAPACKE_dsptrf(layout.lapack(), uplo.lapack(), n, AP_, ipiv_);
    }

    /**
     * Computes the Bunch–Kaufman factorization of a symmetric packed matrix A.
     *
     * @param layout The matrix layout.
     *
     * @param uplo The upper or lower triangular part of the matrix A is
     *             to be referenced.
     *
     * @param n The dimension of the matrix A.
     *
     * @param AP The packed matrix.
     *
     * @param ipiv The pivot indices; for {@code 1 <= i <= min(M,N)}, row i of the
     *             matrix was interchanged with row IPIV(i). Dimension min(M,N).
     *
     * @return INFO flag.
     *         {@code = 0}:  successful exit
     *         {@code < 0}:  if {@code INFO = -i}, the i-th argument had an illegal value
     *         {@code > 0}:  if {@code INFO = i}, D(i,i) is exactly zero. The factorization
     *               has been completed, but the block diagonal matrix D is
     *               exactly singular, and division by zero will occur if it
     *               is used to solve a system of equations.
     */
    static int sptrf(Layout layout, UPLO uplo, int n, DoubleBuffer AP, IntBuffer ipiv) {
        var AP_ = MemorySegment.ofBuffer(AP);
        var ipiv_ = MemorySegment.ofBuffer(ipiv);
        return LAPACKE_dsptrf(layout.lapack(), uplo.lapack(), n, AP_, ipiv_);
    }

    /**
     * Computes the Bunch–Kaufman factorization of a symmetric packed matrix A.
     *
     * @param layout The matrix layout.
     *
     * @param uplo The upper or lower triangular part of the matrix A is
     *             to be referenced.
     *
     * @param n The dimension of the matrix A.
     *
     * @param AP The packed matrix.
     *
     * @param ipiv The pivot indices; for {@code 1 <= i <= min(M,N)}, row i of the
     *             matrix was interchanged with row IPIV(i). Dimension min(M,N).
     *
     * @return INFO flag.
     *         {@code = 0}:  successful exit
     *         {@code < 0}:  if {@code INFO = -i}, the i-th argument had an illegal value
     *         {@code > 0}:  if {@code INFO = i}, D(i,i) is exactly zero. The factorization
     *               has been completed, but the block diagonal matrix D is
     *               exactly singular, and division by zero will occur if it
     *               is used to solve a system of equations.
     */
    static int sptrf(Layout layout, UPLO uplo, int n, float[] AP, int[] ipiv) {
        var AP_ = MemorySegment.ofArray(AP);
        var ipiv_ = MemorySegment.ofArray(ipiv);
        return LAPACKE_ssptrf(layout.lapack(), uplo.lapack(), n, AP_, ipiv_);
    }

    /**
     * Computes the Bunch–Kaufman factorization of a symmetric packed matrix A.
     *
     * @param layout The matrix layout.
     *
     * @param uplo The upper or lower triangular part of the matrix A is
     *             to be referenced.
     *
     * @param n The dimension of the matrix A.
     *
     * @param AP The packed matrix.
     *
     * @param ipiv The pivot indices; for {@code 1 <= i <= min(M,N)}, row i of the
     *             matrix was interchanged with row IPIV(i). Dimension min(M,N).
     *
     * @return INFO flag.
     *         {@code = 0}:  successful exit
     *         {@code < 0}:  if {@code INFO = -i}, the i-th argument had an illegal value
     *         {@code > 0}:  if {@code INFO = i}, D(i,i) is exactly zero. The factorization
     *               has been completed, but the block diagonal matrix D is
     *               exactly singular, and division by zero will occur if it
     *               is used to solve a system of equations.
     */
    static int sptrf(Layout layout, UPLO uplo, int n, FloatBuffer AP, IntBuffer ipiv) {
        var AP_ = MemorySegment.ofBuffer(AP);
        var ipiv_ = MemorySegment.ofBuffer(ipiv);
        return LAPACKE_ssptrf(layout.lapack(), uplo.lapack(), n, AP_, ipiv_);
    }

    /**
     * Solves a system of linear equations
     * <pre>{@code
     *     A * X = B
     * }</pre>
     * or
     * <pre>{@code
     *     A**T * X = B
     * }</pre>
     * where A is an N-by-N matrix and X and B are N-by-NRHS matrices
     * using the LU factorization computed by GETRF.
     *
     * @param layout The matrix layout.
     *
     * @param trans The normal or transpose of the matrix A.
     *
     * @param n The number of linear equations, i.e., the order of the matrix A.
     *
     * @param nrhs The number of right hand sides, i.e., the number of columns
     *             of the matrix B.
     *
     * @param A The LU factorization computed by GETRF.
     *
     * @param lda The leading dimension of the matrix A. {@code LDA >= max(1,N)}.
     *
     * @param ipiv The pivot indices that define the permutation matrix P;
     *             row i of the matrix was interchanged with row IPIV(i).
     *
     * @param B On entry, the N-by-NRHS matrix of right hand side matrix B.
     *          On exit, if INFO = 0, the N-by-NRHS solution matrix X.
     *
     * @param ldb The leading dimension of the matrix B. {@code LDB >= max(1,N)}.
     *
     * @return INFO flag.
     *         {@code = 0}:  successful exit
     *         {@code < 0}:  if {@code INFO = -i}, the i-th argument had an illegal value
     */
    static int getrs(Layout layout, Transpose trans, int n, int nrhs,
                     double[] A, int lda, int[] ipiv, double[] B, int ldb) {
        var A_ = MemorySegment.ofArray(A);
        var B_ = MemorySegment.ofArray(B);
        var ipiv_ = MemorySegment.ofArray(ipiv);
        return LAPACKE_dgetrs(layout.lapack(), trans.lapack(), n, nrhs, A_, lda, ipiv_, B_, ldb);
    }

    /**
     * Solves a system of linear equations
     * <pre>{@code
     *     A * X = B
     * }</pre>
     * or
     * <pre>{@code
     *     A**T * X = B
     * }</pre>
     * where A is an N-by-N matrix and X and B are N-by-NRHS matrices
     * using the LU factorization computed by GETRF.
     *
     * @param layout The matrix layout.
     *
     * @param trans The normal or transpose of the matrix A.
     *
     * @param n The number of linear equations, i.e., the order of the matrix A.
     *
     * @param nrhs The number of right hand sides, i.e., the number of columns
     *             of the matrix B.
     *
     * @param A The LU factorization computed by GETRF.
     *
     * @param lda The leading dimension of the matrix A. {@code LDA >= max(1,N)}.
     *
     * @param ipiv The pivot indices that define the permutation matrix P;
     *             row i of the matrix was interchanged with row IPIV(i).
     *
     * @param B On entry, the N-by-NRHS matrix of right hand side matrix B.
     *          On exit, if INFO = 0, the N-by-NRHS solution matrix X.
     *
     * @param ldb The leading dimension of the matrix B. {@code LDB >= max(1,N)}.
     *
     * @return INFO flag.
     *         {@code = 0}:  successful exit
     *         {@code < 0}:  if {@code INFO = -i}, the i-th argument had an illegal value
     */
    static int getrs(Layout layout, Transpose trans, int n, int nrhs,
                     DoubleBuffer A, int lda, IntBuffer ipiv, DoubleBuffer B, int ldb) {
        var A_ = MemorySegment.ofBuffer(A);
        var B_ = MemorySegment.ofBuffer(B);
        var ipiv_ = MemorySegment.ofBuffer(ipiv);
        return LAPACKE_dgetrs(layout.lapack(), trans.lapack(), n, nrhs, A_, lda, ipiv_, B_, ldb);
    }

    /**
     * Solves a system of linear equations
     * <pre>{@code
     *     A * X = B
     * }</pre>
     * or
     * <pre>{@code
     *     A**T * X = B
     * }</pre>
     * where A is an N-by-N matrix and X and B are N-by-NRHS matrices
     * using the LU factorization computed by GETRF.
     *
     * @param layout The matrix layout.
     *
     * @param trans The normal or transpose of the matrix A.
     *
     * @param n The number of linear equations, i.e., the order of the matrix A.
     *
     * @param nrhs The number of right hand sides, i.e., the number of columns
     *             of the matrix B.
     *
     * @param A The LU factorization computed by GETRF.
     *
     * @param lda The leading dimension of the matrix A. {@code LDA >= max(1,N)}.
     *
     * @param ipiv The pivot indices that define the permutation matrix P;
     *             row i of the matrix was interchanged with row IPIV(i).
     *
     * @param B On entry, the N-by-NRHS matrix of right hand side matrix B.
     *          On exit, if INFO = 0, the N-by-NRHS solution matrix X.
     *
     * @param ldb The leading dimension of the matrix B. {@code LDB >= max(1,N)}.
     *
     * @return INFO flag.
     *         {@code = 0}:  successful exit
     *         {@code < 0}:  if {@code INFO = -i}, the i-th argument had an illegal value
     */
    static int getrs(Layout layout, Transpose trans, int n, int nrhs,
                     MemorySegment A, int lda, MemorySegment ipiv, MemorySegment B, int ldb) {
        return LAPACKE_dgetrs(layout.lapack(), trans.lapack(), n, nrhs, A, lda, ipiv, B, ldb);
    }

    /**
     * Solves a system of linear equations
     * <pre>{@code
     *     A * X = B
     * }</pre>
     * or
     * <pre>{@code
     *     A**T * X = B
     * }</pre>
     * where A is an N-by-N matrix and X and B are N-by-NRHS matrices
     * using the LU factorization computed by GETRF.
     *
     * @param layout The matrix layout.
     *
     * @param trans The normal or transpose of the matrix A.
     *
     * @param n The number of linear equations, i.e., the order of the matrix A.
     *
     * @param nrhs The number of right hand sides, i.e., the number of columns
     *             of the matrix B.
     *
     * @param A The LU factorization computed by GETRF.
     *
     * @param lda The leading dimension of the matrix A. {@code LDA >= max(1,N)}.
     *
     * @param ipiv The pivot indices that define the permutation matrix P;
     *             row i of the matrix was interchanged with row IPIV(i).
     *
     * @param B On entry, the N-by-NRHS matrix of right hand side matrix B.
     *          On exit, if INFO = 0, the N-by-NRHS solution matrix X.
     *
     * @param ldb The leading dimension of the matrix B. {@code LDB >= max(1,N)}.
     *
     * @return INFO flag.
     *         {@code = 0}:  successful exit
     *         {@code < 0}:  if {@code INFO = -i}, the i-th argument had an illegal value
     */
    static int getrs(Layout layout, Transpose trans, int n, int nrhs,
                     float[] A, int lda, int[] ipiv, float[] B, int ldb) {
        var A_ = MemorySegment.ofArray(A);
        var B_ = MemorySegment.ofArray(B);
        var ipiv_ = MemorySegment.ofArray(ipiv);
        return LAPACKE_sgetrs(layout.lapack(), trans.lapack(), n, nrhs, A_, lda, ipiv_, B_, ldb);
    }

    /**
     * Solves a system of linear equations
     * <pre>{@code
     *     A * X = B
     * }</pre>
     * or
     * <pre>{@code
     *     A**T * X = B
     * }</pre>
     * where A is an N-by-N matrix and X and B are N-by-NRHS matrices
     * using the LU factorization computed by GETRF.
     *
     * @param layout The matrix layout.
     *
     * @param trans The normal or transpose of the matrix A.
     *
     * @param n The number of linear equations, i.e., the order of the matrix A.
     *
     * @param nrhs The number of right hand sides, i.e., the number of columns
     *             of the matrix B.
     *
     * @param A The LU factorization computed by GETRF.
     *
     * @param lda The leading dimension of the matrix A. {@code LDA >= max(1,N)}.
     *
     * @param ipiv The pivot indices that define the permutation matrix P;
     *             row i of the matrix was interchanged with row IPIV(i).
     *
     * @param B On entry, the N-by-NRHS matrix of right hand side matrix B.
     *          On exit, if INFO = 0, the N-by-NRHS solution matrix X.
     *
     * @param ldb The leading dimension of the matrix B. {@code LDB >= max(1,N)}.
     *
     * @return INFO flag.
     *         {@code = 0}:  successful exit
     *         {@code < 0}:  if {@code INFO = -i}, the i-th argument had an illegal value
     */
    static int getrs(Layout layout, Transpose trans, int n, int nrhs,
                     FloatBuffer A, int lda, IntBuffer ipiv, FloatBuffer B, int ldb) {
        var A_ = MemorySegment.ofBuffer(A);
        var B_ = MemorySegment.ofBuffer(B);
        var ipiv_ = MemorySegment.ofBuffer(ipiv);
        return LAPACKE_sgetrs(layout.lapack(), trans.lapack(), n, nrhs, A_, lda, ipiv_, B_, ldb);
    }

    /**
     * Solves a system of linear equations
     * <pre>{@code
     *     A * X = B
     * }</pre>
     * or
     * <pre>{@code
     *     A**T * X = B
     * }</pre>
     * where A is an N-by-N band matrix and X and B are N-by-NRHS matrices
     * using the LU factorization computed by GBTRF.
     *
     * @param layout The matrix layout.
     *
     * @param trans The normal or transpose of the matrix A.
     *
     * @param n The number of linear equations, i.e., the order of the matrix A.
     *
     * @param kl The number of subdiagonal elements of band matrix.
     *
     * @param ku The number of superdiagonal elements of band matrix.
     *
     * @param nrhs The number of right hand sides, i.e., the number of columns
     *             of the matrix B.
     *
     * @param A The LU factorization computed by GBTRF.
     *
     * @param lda The leading dimension of the matrix A. {@code LDA >= max(1,N)}.
     *
     * @param ipiv The pivot indices that define the permutation matrix P;
     *             row i of the matrix was interchanged with row IPIV(i).
     *
     * @param B On entry, the N-by-NRHS matrix of right hand side matrix B.
     *          On exit, if INFO = 0, the N-by-NRHS solution matrix X.
     *
     * @param ldb The leading dimension of the matrix B. {@code LDB >= max(1,N)}.
     *
     * @return INFO flag.
     *         {@code = 0}:  successful exit
     *         {@code < 0}:  if {@code INFO = -i}, the i-th argument had an illegal value
     */
    static int gbtrs(Layout layout, Transpose trans, int n, int kl, int ku, int nrhs,
                     double[] A, int lda, int[] ipiv, double[] B, int ldb) {
        var A_ = MemorySegment.ofArray(A);
        var B_ = MemorySegment.ofArray(B);
        var ipiv_ = MemorySegment.ofArray(ipiv);
        return LAPACKE_dgbtrs(layout.lapack(), trans.lapack(), n, kl, ku, nrhs, A_, lda, ipiv_, B_, ldb);
    }

    /**
     * Solves a system of linear equations
     * <pre>{@code
     *     A * X = B
     * }</pre>
     * or
     * <pre>{@code
     *     A**T * X = B
     * }</pre>
     * where A is an N-by-N band matrix and X and B are N-by-NRHS matrices
     * using the LU factorization computed by GBTRF.
     *
     * @param layout The matrix layout.
     *
     * @param trans The normal or transpose of the matrix A.
     *
     * @param n The number of linear equations, i.e., the order of the matrix A.
     *
     * @param kl The number of subdiagonal elements of band matrix.
     *
     * @param ku The number of superdiagonal elements of band matrix.
     *
     * @param nrhs The number of right hand sides, i.e., the number of columns
     *             of the matrix B.
     *
     * @param A The LU factorization computed by GBTRF.
     *
     * @param lda The leading dimension of the matrix A. {@code LDA >= max(1,N)}.
     *
     * @param ipiv The pivot indices that define the permutation matrix P;
     *             row i of the matrix was interchanged with row IPIV(i).
     *
     * @param B On entry, the N-by-NRHS matrix of right hand side matrix B.
     *          On exit, if INFO = 0, the N-by-NRHS solution matrix X.
     *
     * @param ldb The leading dimension of the matrix B. {@code LDB >= max(1,N)}.
     *
     * @return INFO flag.
     *         {@code = 0}:  successful exit
     *         {@code < 0}:  if {@code INFO = -i}, the i-th argument had an illegal value
     */
    static int gbtrs(Layout layout, Transpose trans, int n, int kl, int ku, int nrhs,
                     DoubleBuffer A, int lda, IntBuffer ipiv, DoubleBuffer B, int ldb) {
        var A_ = MemorySegment.ofBuffer(A);
        var B_ = MemorySegment.ofBuffer(B);
        var ipiv_ = MemorySegment.ofBuffer(ipiv);
        return LAPACKE_dgbtrs(layout.lapack(), trans.lapack(), n, kl, ku, nrhs, A_, lda, ipiv_, B_, ldb);
    }

    /**
     * Solves a system of linear equations
     * <pre>{@code
     *     A * X = B
     * }</pre>
     * or
     * <pre>{@code
     *     A**T * X = B
     * }</pre>
     * where A is an N-by-N band matrix and X and B are N-by-NRHS matrices
     * using the LU factorization computed by GBTRF.
     *
     * @param layout The matrix layout.
     *
     * @param trans The normal or transpose of the matrix A.
     *
     * @param n The number of linear equations, i.e., the order of the matrix A.
     *
     * @param kl The number of subdiagonal elements of band matrix.
     *
     * @param ku The number of superdiagonal elements of band matrix.
     *
     * @param nrhs The number of right hand sides, i.e., the number of columns
     *             of the matrix B.
     *
     * @param A The LU factorization computed by GBTRF.
     *
     * @param lda The leading dimension of the matrix A. {@code LDA >= max(1,N)}.
     *
     * @param ipiv The pivot indices that define the permutation matrix P;
     *             row i of the matrix was interchanged with row IPIV(i).
     *
     * @param B On entry, the N-by-NRHS matrix of right hand side matrix B.
     *          On exit, if INFO = 0, the N-by-NRHS solution matrix X.
     *
     * @param ldb The leading dimension of the matrix B. {@code LDB >= max(1,N)}.
     *
     * @return INFO flag.
     *         {@code = 0}:  successful exit
     *         {@code < 0}:  if {@code INFO = -i}, the i-th argument had an illegal value
     */
    static int gbtrs(Layout layout, Transpose trans, int n, int kl, int ku, int nrhs,
                     float[] A, int lda, int[] ipiv, float[] B, int ldb) {
        var A_ = MemorySegment.ofArray(A);
        var B_ = MemorySegment.ofArray(B);
        var ipiv_ = MemorySegment.ofArray(ipiv);
        return LAPACKE_sgbtrs(layout.lapack(), trans.lapack(), n, kl, ku, nrhs, A_, lda, ipiv_, B_, ldb);
    }

    /**
     * Solves a system of linear equations
     * <pre>{@code
     *     A * X = B
     * }</pre>
     * or
     * <pre>{@code
     *     A**T * X = B
     * }</pre>
     * where A is an N-by-N band matrix and X and B are N-by-NRHS matrices
     * using the LU factorization computed by GBTRF.
     *
     * @param layout The matrix layout.
     *
     * @param trans The normal or transpose of the matrix A.
     *
     * @param n The number of linear equations, i.e., the order of the matrix A.
     *
     * @param kl The number of subdiagonal elements of band matrix.
     *
     * @param ku The number of superdiagonal elements of band matrix.
     *
     * @param nrhs The number of right hand sides, i.e., the number of columns
     *             of the matrix B.
     *
     * @param A The LU factorization computed by GBTRF.
     *
     * @param lda The leading dimension of the matrix A. {@code LDA >= max(1,N)}.
     *
     * @param ipiv The pivot indices that define the permutation matrix P;
     *             row i of the matrix was interchanged with row IPIV(i).
     *
     * @param B On entry, the N-by-NRHS matrix of right hand side matrix B.
     *          On exit, if INFO = 0, the N-by-NRHS solution matrix X.
     *
     * @param ldb The leading dimension of the matrix B. {@code LDB >= max(1,N)}.
     *
     * @return INFO flag.
     *         {@code = 0}:  successful exit
     *         {@code < 0}:  if {@code INFO = -i}, the i-th argument had an illegal value
     */
    static int gbtrs(Layout layout, Transpose trans, int n, int kl, int ku, int nrhs,
                     FloatBuffer A, int lda, IntBuffer ipiv, FloatBuffer B, int ldb) {
        var A_ = MemorySegment.ofBuffer(A);
        var B_ = MemorySegment.ofBuffer(B);
        var ipiv_ = MemorySegment.ofBuffer(ipiv);
        return LAPACKE_sgbtrs(layout.lapack(), trans.lapack(), n, kl, ku, nrhs, A_, lda, ipiv_, B_, ldb);
    }

    /**
     * Solves a system of linear equations
     * <pre>{@code
     *     A * X = B
     * }</pre>
     * or
     * <pre>{@code
     *     A**T * X = B
     * }</pre>
     * where A is an N-by-N packed matrix and X and B are N-by-NRHS matrices
     * using the Bunch-Kaufman factorization computed by SPTRF.
     *
     * @param layout The matrix layout.
     *
     * @param uplo The upper or lower triangular part of the matrix A is
     *             to be referenced.
     *
     * @param n The number of linear equations, i.e., the order of the matrix A.
     *
     * @param nrhs The number of right hand sides, i.e., the number of columns
     *             of the matrix B.
     *
     * @param AP The Bunch-Kaufman factorization computed by SPTRF.
     *
     * @param ipiv The pivot indices that define the permutation matrix P;
     *             row i of the matrix was interchanged with row IPIV(i).
     *
     * @param B On entry, the N-by-NRHS matrix of right hand side matrix B.
     *          On exit, if INFO = 0, the N-by-NRHS solution matrix X.
     *
     * @param ldb The leading dimension of the matrix B. {@code LDB >= max(1,N)}.
     *
     * @return INFO flag.
     *         {@code = 0}:  successful exit
     *         {@code < 0}:  if {@code INFO = -i}, the i-th argument had an illegal value
     */
    static int sptrs(Layout layout, UPLO uplo, int n, int nrhs, double[] AP, int[] ipiv, double[] B, int ldb) {
        var AP_ = MemorySegment.ofArray(AP);
        var B_ = MemorySegment.ofArray(B);
        var ipiv_ = MemorySegment.ofArray(ipiv);
        return LAPACKE_dsptrs(layout.lapack(), uplo.lapack(), n, nrhs, AP_, ipiv_, B_, ldb);
    }

    /**
     * Solves a system of linear equations
     * <pre>{@code
     *     A * X = B
     * }</pre>
     * or
     * <pre>{@code
     *     A**T * X = B
     * }</pre>
     * where A is an N-by-N packed matrix and X and B are N-by-NRHS matrices
     * using the Bunch-Kaufman factorization computed by SPTRF.
     *
     * @param layout The matrix layout.
     *
     * @param uplo The upper or lower triangular part of the matrix A is
     *             to be referenced.
     *
     * @param n The number of linear equations, i.e., the order of the matrix A.
     *
     * @param nrhs The number of right hand sides, i.e., the number of columns
     *             of the matrix B.
     *
     * @param AP The Bunch-Kaufman factorization computed by SPTRF.
     *
     * @param ipiv The pivot indices that define the permutation matrix P;
     *             row i of the matrix was interchanged with row IPIV(i).
     *
     * @param B On entry, the N-by-NRHS matrix of right hand side matrix B.
     *          On exit, if INFO = 0, the N-by-NRHS solution matrix X.
     *
     * @param ldb The leading dimension of the matrix B. {@code LDB >= max(1,N)}.
     *
     * @return INFO flag.
     *         {@code = 0}:  successful exit
     *         {@code < 0}:  if {@code INFO = -i}, the i-th argument had an illegal value
     */
    static int sptrs(Layout layout, UPLO uplo, int n, int nrhs, DoubleBuffer AP, IntBuffer ipiv, DoubleBuffer B, int ldb) {
        var AP_ = MemorySegment.ofBuffer(AP);
        var B_ = MemorySegment.ofBuffer(B);
        var ipiv_ = MemorySegment.ofBuffer(ipiv);
        return LAPACKE_dsptrs(layout.lapack(), uplo.lapack(), n, nrhs, AP_, ipiv_, B_, ldb);
    }

    /**
     * Solves a system of linear equations
     * <pre>{@code
     *     A * X = B
     * }</pre>
     * or
     * <pre>{@code
     *     A**T * X = B
     * }</pre>
     * where A is an N-by-N packed matrix and X and B are N-by-NRHS matrices
     * using the Bunch-Kaufman factorization computed by SPTRF.
     *
     * @param layout The matrix layout.
     *
     * @param uplo The upper or lower triangular part of the matrix A is
     *             to be referenced.
     *
     * @param n The number of linear equations, i.e., the order of the matrix A.
     *
     * @param nrhs The number of right hand sides, i.e., the number of columns
     *             of the matrix B.
     *
     * @param AP The Bunch-Kaufman factorization computed by SPTRF.
     *
     * @param ipiv The pivot indices that define the permutation matrix P;
     *             row i of the matrix was interchanged with row IPIV(i).
     *
     * @param B On entry, the N-by-NRHS matrix of right hand side matrix B.
     *          On exit, if INFO = 0, the N-by-NRHS solution matrix X.
     *
     * @param ldb The leading dimension of the matrix B. {@code LDB >= max(1,N)}.
     *
     * @return INFO flag.
     *         {@code = 0}:  successful exit
     *         {@code < 0}:  if {@code INFO = -i}, the i-th argument had an illegal value
     */
    static int sptrs(Layout layout, UPLO uplo, int n, int nrhs, float[] AP, int[] ipiv, float[] B, int ldb) {
        var AP_ = MemorySegment.ofArray(AP);
        var B_ = MemorySegment.ofArray(B);
        var ipiv_ = MemorySegment.ofArray(ipiv);
        return LAPACKE_ssptrs(layout.lapack(), uplo.lapack(), n, nrhs, AP_, ipiv_, B_, ldb);
    }

    /**
     * Solves a system of linear equations
     * <pre>{@code
     *     A * X = B
     * }</pre>
     * or
     * <pre>{@code
     *     A**T * X = B
     * }</pre>
     * where A is an N-by-N packed matrix and X and B are N-by-NRHS matrices
     * using the Bunch-Kaufman factorization computed by SPTRF.
     *
     * @param layout The matrix layout.
     *
     * @param uplo The upper or lower triangular part of the matrix A is
     *             to be referenced.
     *
     * @param n The number of linear equations, i.e., the order of the matrix A.
     *
     * @param nrhs The number of right hand sides, i.e., the number of columns
     *             of the matrix B.
     *
     * @param AP The Bunch-Kaufman factorization computed by SPTRF.
     *
     * @param ipiv The pivot indices that define the permutation matrix P;
     *             row i of the matrix was interchanged with row IPIV(i).
     *
     * @param B On entry, the N-by-NRHS matrix of right hand side matrix B.
     *          On exit, if INFO = 0, the N-by-NRHS solution matrix X.
     *
     * @param ldb The leading dimension of the matrix B. {@code LDB >= max(1,N)}.
     *
     * @return INFO flag.
     *         {@code = 0}:  successful exit
     *         {@code < 0}:  if {@code INFO = -i}, the i-th argument had an illegal value
     */
    static int sptrs(Layout layout, UPLO uplo, int n, int nrhs, FloatBuffer AP, IntBuffer ipiv, FloatBuffer B, int ldb) {
        var AP_ = MemorySegment.ofBuffer(AP);
        var B_ = MemorySegment.ofBuffer(B);
        var ipiv_ = MemorySegment.ofBuffer(ipiv);
        return LAPACKE_ssptrs(layout.lapack(), uplo.lapack(), n, nrhs, AP_, ipiv_, B_, ldb);
    }

    /**
     * Computes the Cholesky factorization of a real symmetric
     * positive definite matrix A.
     *
     * @param layout The matrix layout.
     *
     * @param uplo The upper or lower triangular part of the matrix A is
     *             to be referenced.
     *
     * @param n The dimension of the matrix A.
     *
     * @param A The matrix of dimension (LDA, N).
     *          On exit, the factor U or L from the Cholesky
     *          factorization A = U<sup>T</sup>*U or A = L*L<sup>T</sup>.
     *
     * @param lda The leading dimension of the matrix A. {@code LDA >= max(1,N)}.
     *
     * @return INFO flag.
     *         {@code = 0}:  successful exit
     *         {@code < 0}:  if {@code INFO = -i}, the i-th argument had an illegal value
     *         {@code > 0}:  if {@code INFO = i}, the leading minor of order i is not
     *               positive definite, and the factorization could not be
     *               completed.
     */
    static int potrf(Layout layout, UPLO uplo, int n, double[] A, int lda) {
        var A_ = MemorySegment.ofArray(A);
        return LAPACKE_dpotrf(layout.lapack(), uplo.lapack(), n, A_, lda);
    }

    /**
     * Computes the Cholesky factorization of a real symmetric
     * positive definite matrix A.
     *
     * @param layout The matrix layout.
     *
     * @param uplo The upper or lower triangular part of the matrix A is
     *             to be referenced.
     *
     * @param n The dimension of the matrix A.
     *
     * @param A The matrix of dimension (LDA, N).
     *          On exit, the factor U or L from the Cholesky
     *          factorization A = U<sup>T</sup>*U or A = L*L<sup>T</sup>.
     *
     * @param lda The leading dimension of the matrix A. {@code LDA >= max(1,N)}.
     *
     * @return INFO flag.
     *         {@code = 0}:  successful exit
     *         {@code < 0}:  if {@code INFO = -i}, the i-th argument had an illegal value
     *         {@code > 0}:  if {@code INFO = i}, the leading minor of order i is not
     *               positive definite, and the factorization could not be
     *               completed.
     */
    static int potrf(Layout layout, UPLO uplo, int n, DoubleBuffer A, int lda) {
        var A_ = MemorySegment.ofBuffer(A);
        return LAPACKE_dpotrf(layout.lapack(), uplo.lapack(), n, A_, lda);
    }

    /**
     * Computes the Cholesky factorization of a real symmetric
     * positive definite matrix A.
     *
     * @param layout The matrix layout.
     *
     * @param uplo The upper or lower triangular part of the matrix A is
     *             to be referenced.
     *
     * @param n The dimension of the matrix A.
     *
     * @param A The matrix of dimension (LDA, N).
     *          On exit, the factor U or L from the Cholesky
     *          factorization A = U<sup>T</sup>*U or A = L*L<sup>T</sup>.
     *
     * @param lda The leading dimension of the matrix A. {@code LDA >= max(1,N)}.
     *
     * @return INFO flag.
     *         {@code = 0}:  successful exit
     *         {@code < 0}:  if {@code INFO = -i}, the i-th argument had an illegal value
     *         {@code > 0}:  if {@code INFO = i}, the leading minor of order i is not
     *               positive definite, and the factorization could not be
     *               completed.
     */
    static int potrf(Layout layout, UPLO uplo, int n, MemorySegment A, int lda) {
        return LAPACKE_dpotrf(layout.lapack(), uplo.lapack(), n, A, lda);
    }

    /**
     * Computes the Cholesky factorization of a real symmetric
     * positive definite matrix A.
     *
     * @param layout The matrix layout.
     *
     * @param uplo The upper or lower triangular part of the matrix A is
     *             to be referenced.
     *
     * @param n The dimension of the matrix A.
     *
     * @param A The matrix of dimension (LDA, N).
     *          On exit, the factor U or L from the Cholesky
     *          factorization A = U<sup>T</sup>*U or A = L*L<sup>T</sup>.
     *
     * @param lda The leading dimension of the matrix A. {@code LDA >= max(1,N)}.
     *
     * @return INFO flag.
     *         {@code = 0}:  successful exit
     *         {@code < 0}:  if {@code INFO = -i}, the i-th argument had an illegal value
     *         {@code > 0}:  if {@code INFO = i}, the leading minor of order i is not
     *               positive definite, and the factorization could not be
     *               completed.
     */
    static int potrf(Layout layout, UPLO uplo, int n, float[] A, int lda) {
        var A_ = MemorySegment.ofArray(A);
        return LAPACKE_spotrf(layout.lapack(), uplo.lapack(), n, A_, lda);
    }

    /**
     * Computes the Cholesky factorization of a real symmetric
     * positive definite matrix A.
     *
     * @param layout The matrix layout.
     *
     * @param uplo The upper or lower triangular part of the matrix A is
     *             to be referenced.
     *
     * @param n The dimension of the matrix A.
     *
     * @param A The matrix of dimension (LDA, N).
     *          On exit, the factor U or L from the Cholesky
     *          factorization A = U<sup>T</sup>*U or A = L*L<sup>T</sup>.
     *
     * @param lda The leading dimension of the matrix A. {@code LDA >= max(1,N)}.
     *
     * @return INFO flag.
     *         {@code = 0}:  successful exit
     *         {@code < 0}:  if {@code INFO = -i}, the i-th argument had an illegal value
     *         {@code > 0}:  if {@code INFO = i}, the leading minor of order i is not
     *               positive definite, and the factorization could not be
     *               completed.
     */
    static int potrf(Layout layout, UPLO uplo, int n, FloatBuffer A, int lda) {
        var A_ = MemorySegment.ofBuffer(A);
        return LAPACKE_spotrf(layout.lapack(), uplo.lapack(), n, A_, lda);
    }

    /**
     * Computes the Cholesky factorization of a real symmetric
     * positive definite matrix A using the recursive algorithm.
     *
     * @param layout The matrix layout.
     *
     * @param uplo The upper or lower triangular part of the matrix A is
     *             to be referenced.
     *
     * @param n The dimension of the matrix A.
     *
     * @param A The matrix of dimension (LDA, N).
     *          On exit, the factor U or L from the Cholesky
     *          factorization A = U<sup>T</sup>*U or A = L*L<sup>T</sup>.
     *
     * @param lda The leading dimension of the matrix A. {@code LDA >= max(1,N)}.
     *
     * @return INFO flag.
     *         {@code = 0}:  successful exit
     *         {@code < 0}:  if {@code INFO = -i}, the i-th argument had an illegal value
     *         {@code > 0}:  if {@code INFO = i}, the leading minor of order i is not
     *               positive definite, and the factorization could not be
     *               completed.
     */
    static int potrf2(Layout layout, UPLO uplo, int n, double[] A, int lda) {
        var A_ = MemorySegment.ofArray(A);
        return LAPACKE_dpotrf2(layout.lapack(), uplo.lapack(), n, A_, lda);
    }

    /**
     * Computes the Cholesky factorization of a real symmetric
     * positive definite matrix A using the recursive algorithm.
     *
     * @param layout The matrix layout.
     *
     * @param uplo The upper or lower triangular part of the matrix A is
     *             to be referenced.
     *
     * @param n The dimension of the matrix A.
     *
     * @param A The matrix of dimension (LDA, N).
     *          On exit, the factor U or L from the Cholesky
     *          factorization A = U<sup>T</sup>*U or A = L*L<sup>T</sup>.
     *
     * @param lda The leading dimension of the matrix A. {@code LDA >= max(1,N)}.
     *
     * @return INFO flag.
     *         {@code = 0}:  successful exit
     *         {@code < 0}:  if {@code INFO = -i}, the i-th argument had an illegal value
     *         {@code > 0}:  if {@code INFO = i}, the leading minor of order i is not
     *               positive definite, and the factorization could not be
     *               completed.
     */
    static int potrf2(Layout layout, UPLO uplo, int n, DoubleBuffer A, int lda) {
        var A_ = MemorySegment.ofBuffer(A);
        return LAPACKE_dpotrf2(layout.lapack(), uplo.lapack(), n, A_, lda);
    }

    /**
     * Computes the Cholesky factorization of a real symmetric
     * positive definite matrix A using the recursive algorithm.
     *
     * @param layout The matrix layout.
     *
     * @param uplo The upper or lower triangular part of the matrix A is
     *             to be referenced.
     *
     * @param n The dimension of the matrix A.
     *
     * @param A The matrix of dimension (LDA, N).
     *          On exit, the factor U or L from the Cholesky
     *          factorization A = U<sup>T</sup>*U or A = L*L<sup>T</sup>.
     *
     * @param lda The leading dimension of the matrix A. {@code LDA >= max(1,N)}.
     *
     * @return INFO flag.
     *         {@code = 0}:  successful exit
     *         {@code < 0}:  if {@code INFO = -i}, the i-th argument had an illegal value
     *         {@code > 0}:  if {@code INFO = i}, the leading minor of order i is not
     *               positive definite, and the factorization could not be
     *               completed.
     */
    static int potrf2(Layout layout, UPLO uplo, int n, float[] A, int lda) {
        var A_ = MemorySegment.ofArray(A);
        return LAPACKE_spotrf2(layout.lapack(), uplo.lapack(), n, A_, lda);
    }

    /**
     * Computes the Cholesky factorization of a real symmetric
     * positive definite matrix A using the recursive algorithm.
     *
     * @param layout The matrix layout.
     *
     * @param uplo The upper or lower triangular part of the matrix A is
     *             to be referenced.
     *
     * @param n The dimension of the matrix A.
     *
     * @param A The matrix of dimension (LDA, N).
     *          On exit, the factor U or L from the Cholesky
     *          factorization A = U<sup>T</sup>*U or A = L*L<sup>T</sup>.
     *
     * @param lda The leading dimension of the matrix A. {@code LDA >= max(1,N)}.
     *
     * @return INFO flag.
     *         {@code = 0}:  successful exit
     *         {@code < 0}:  if {@code INFO = -i}, the i-th argument had an illegal value
     *         {@code > 0}:  if {@code INFO = i}, the leading minor of order i is not
     *               positive definite, and the factorization could not be
     *               completed.
     */
    static int potrf2(Layout layout, UPLO uplo, int n, FloatBuffer A, int lda) {
        var A_ = MemorySegment.ofBuffer(A);
        return LAPACKE_spotrf2(layout.lapack(), uplo.lapack(), n, A_, lda);
    }

    /**
     * Computes the Cholesky factorization of a real symmetric
     * positive definite band matrix A.
     *
     * @param layout The matrix layout.
     *
     * @param uplo The upper or lower triangular part of the matrix A is
     *             to be referenced.
     *
     * @param n The dimension of the matrix A.
     *
     * @param kd The number of superdiagonals/subdiagonals of the matrix A.
     *
     * @param AB The band matrix of dimension (LDA, N).
     *          On exit, the factor U or L from the Cholesky
     *          factorization A = U<sup>T</sup>*U or A = L*L<sup>T</sup>.
     *
     * @param ldab The leading dimension of the matrix A. {@code LDA >= max(1,N)}.
     *
     * @return INFO flag.
     *         {@code = 0}:  successful exit
     *         {@code < 0}:  if {@code INFO = -i}, the i-th argument had an illegal value
     *         {@code > 0}:  if {@code INFO = i}, the leading minor of order i is not
     *               positive definite, and the factorization could not be
     *               completed.
     */
    static int pbtrf(Layout layout, UPLO uplo, int n, int kd, double[] AB, int ldab) {
        var AB_ = MemorySegment.ofArray(AB);
        return LAPACKE_dpbtrf(layout.lapack(), uplo.lapack(), n, kd, AB_, ldab);
    }

    /**
     * Computes the Cholesky factorization of a real symmetric
     * positive definite band matrix A.
     *
     * @param layout The matrix layout.
     *
     * @param uplo The upper or lower triangular part of the matrix A is
     *             to be referenced.
     *
     * @param n The dimension of the matrix A.
     *
     * @param kd The number of superdiagonals/subdiagonals of the matrix A.
     *
     * @param AB The band matrix of dimension (LDA, N).
     *          On exit, the factor U or L from the Cholesky
     *          factorization A = U<sup>T</sup>*U or A = L*L<sup>T</sup>.
     *
     * @param ldab The leading dimension of the matrix A. {@code LDA >= max(1,N)}.
     *
     * @return INFO flag.
     *         {@code = 0}:  successful exit
     *         {@code < 0}:  if {@code INFO = -i}, the i-th argument had an illegal value
     *         {@code > 0}:  if {@code INFO = i}, the leading minor of order i is not
     *               positive definite, and the factorization could not be
     *               completed.
     */
    static int pbtrf(Layout layout, UPLO uplo, int n, int kd, DoubleBuffer AB, int ldab) {
        var AB_ = MemorySegment.ofBuffer(AB);
        return LAPACKE_dpbtrf(layout.lapack(), uplo.lapack(), n, kd, AB_, ldab);
    }

    /**
     * Computes the Cholesky factorization of a real symmetric
     * positive definite band matrix A.
     *
     * @param layout The matrix layout.
     *
     * @param uplo The upper or lower triangular part of the matrix A is
     *             to be referenced.
     *
     * @param n The dimension of the matrix A.
     *
     * @param kd The number of superdiagonals/subdiagonals of the matrix A.
     *
     * @param AB The band matrix of dimension (LDA, N).
     *          On exit, the factor U or L from the Cholesky
     *          factorization A = U<sup>T</sup>*U or A = L*L<sup>T</sup>.
     *
     * @param ldab The leading dimension of the matrix A. {@code LDA >= max(1,N)}.
     *
     * @return INFO flag.
     *         {@code = 0}:  successful exit
     *         {@code < 0}:  if {@code INFO = -i}, the i-th argument had an illegal value
     *         {@code > 0}:  if {@code INFO = i}, the leading minor of order i is not
     *               positive definite, and the factorization could not be
     *               completed.
     */
    static int pbtrf(Layout layout, UPLO uplo, int n, int kd, float[] AB, int ldab) {
        var AB_ = MemorySegment.ofArray(AB);
        return LAPACKE_spbtrf(layout.lapack(), uplo.lapack(), n, kd, AB_, ldab);
    }

    /**
     * Computes the Cholesky factorization of a real symmetric
     * positive definite band matrix A.
     *
     * @param layout The matrix layout.
     *
     * @param uplo The upper or lower triangular part of the matrix A is
     *             to be referenced.
     *
     * @param n The dimension of the matrix A.
     *
     * @param kd The number of superdiagonals/subdiagonals of the matrix A.
     *
     * @param AB The band matrix of dimension (LDA, N).
     *          On exit, the factor U or L from the Cholesky
     *          factorization A = U<sup>T</sup>*U or A = L*L<sup>T</sup>.
     *
     * @param ldab The leading dimension of the matrix A. {@code LDA >= max(1,N)}.
     *
     * @return INFO flag.
     *         {@code = 0}:  successful exit
     *         {@code < 0}:  if {@code INFO = -i}, the i-th argument had an illegal value
     *         {@code > 0}:  if {@code INFO = i}, the leading minor of order i is not
     *               positive definite, and the factorization could not be
     *               completed.
     */
    static int pbtrf(Layout layout, UPLO uplo, int n, int kd, FloatBuffer AB, int ldab) {
        var AB_ = MemorySegment.ofBuffer(AB);
        return LAPACKE_spbtrf(layout.lapack(), uplo.lapack(), n, kd, AB_, ldab);
    }


    /**
     * Computes the Cholesky factorization of a real symmetric
     * positive definite packed matrix A.
     *
     * @param layout The matrix layout.
     *
     * @param uplo The upper or lower triangular part of the matrix A is
     *             to be referenced.
     *
     * @param n The dimension of the matrix A.
     *
     * @param AP The packed matrix.
     *          On exit, the factor U or L from the Cholesky
     *          factorization A = U<sup>T</sup>*U or A = L*L<sup>T</sup>.
     *
     * @return INFO flag.
     *         {@code = 0}:  successful exit
     *         {@code < 0}:  if {@code INFO = -i}, the i-th argument had an illegal value
     *         {@code > 0}:  if {@code INFO = i}, the leading minor of order i is not
     *               positive definite, and the factorization could not be
     *               completed.
     */
    static int pptrf(Layout layout, UPLO uplo, int n, double[] AP) {
        var AP_ = MemorySegment.ofArray(AP);
        return LAPACKE_dpptrf(layout.lapack(), uplo.lapack(), n, AP_);
    }

    /**
     * Computes the Cholesky factorization of a real symmetric
     * positive definite packed matrix A.
     *
     * @param layout The matrix layout.
     *
     * @param uplo The upper or lower triangular part of the matrix A is
     *             to be referenced.
     *
     * @param n The dimension of the matrix A.
     *
     * @param AP The packed matrix.
     *          On exit, the factor U or L from the Cholesky
     *          factorization A = U<sup>T</sup>*U or A = L*L<sup>T</sup>.
     *
     * @return INFO flag.
     *         {@code = 0}:  successful exit
     *         {@code < 0}:  if {@code INFO = -i}, the i-th argument had an illegal value
     *         {@code > 0}:  if {@code INFO = i}, the leading minor of order i is not
     *               positive definite, and the factorization could not be
     *               completed.
     */
    static int pptrf(Layout layout, UPLO uplo, int n, DoubleBuffer AP) {
        var AP_ = MemorySegment.ofBuffer(AP);
        return LAPACKE_dpptrf(layout.lapack(), uplo.lapack(), n, AP_);
    }

    /**
     * Computes the Cholesky factorization of a real symmetric
     * positive definite packed matrix A.
     *
     * @param layout The matrix layout.
     *
     * @param uplo The upper or lower triangular part of the matrix A is
     *             to be referenced.
     *
     * @param n The dimension of the matrix A.
     *
     * @param AP The packed matrix.
     *          On exit, the factor U or L from the Cholesky
     *          factorization A = U<sup>T</sup>*U or A = L*L<sup>T</sup>.
     *
     * @return INFO flag.
     *         {@code = 0}:  successful exit
     *         {@code < 0}:  if {@code INFO = -i}, the i-th argument had an illegal value
     *         {@code > 0}:  if {@code INFO = i}, the leading minor of order i is not
     *               positive definite, and the factorization could not be
     *               completed.
     */
    static int pptrf(Layout layout, UPLO uplo, int n, float[] AP) {
        var AP_ = MemorySegment.ofArray(AP);
        return LAPACKE_spptrf(layout.lapack(), uplo.lapack(), n, AP_);
    }

    /**
     * Computes the Cholesky factorization of a real symmetric
     * positive definite packed matrix A.
     *
     * @param layout The matrix layout.
     *
     * @param uplo The upper or lower triangular part of the matrix A is
     *             to be referenced.
     *
     * @param n The dimension of the matrix A.
     *
     * @param AP The packed matrix.
     *          On exit, the factor U or L from the Cholesky
     *          factorization A = U<sup>T</sup>*U or A = L*L<sup>T</sup>.
     *
     * @return INFO flag.
     *         {@code = 0}:  successful exit
     *         {@code < 0}:  if {@code INFO = -i}, the i-th argument had an illegal value
     *         {@code > 0}:  if {@code INFO = i}, the leading minor of order i is not
     *               positive definite, and the factorization could not be
     *               completed.
     */
    static int pptrf(Layout layout, UPLO uplo, int n, FloatBuffer AP) {
        var AP_ = MemorySegment.ofBuffer(AP);
        return LAPACKE_spptrf(layout.lapack(), uplo.lapack(), n, AP_);
    }

    /**
     * Solves a system of linear equations
     * <pre>{@code
     *     A * X = B
     * }</pre>
     * where A is an N-by-N symmetric positive definite matrix and
     * X and B are N-by-NRHS matrices using the Cholesky factorization
     * A = U<sup>T</sup>*U or A = L*L<sup>T</sup> computed by POTRF.
     *
     * @param layout The matrix layout.
     *
     * @param uplo The upper or lower triangular part of the matrix A is
     *             to be referenced.
     *
     * @param n The number of linear equations, i.e., the order of the matrix A.
     *
     * @param nrhs The number of right hand sides, i.e., the number of columns
     *             of the matrix B.
     *
     * @param A The triangular factor U or L from the Cholesky factorization
     *          A = U<sup>T</sup>*U or A = L*L<sup>T</sup>, as computed by POTRF.
     *
     * @param lda The leading dimension of the matrix A. {@code LDA >= max(1,N)}.
     *
     * @param B On entry, the N-by-NRHS matrix of right hand side matrix B.
     *          On exit, if INFO = 0, the N-by-NRHS solution matrix X.
     *
     * @param ldb The leading dimension of the matrix B. {@code LDB >= max(1,N)}.
     *
     * @return INFO flag.
     *         {@code = 0}:  successful exit
     *         {@code < 0}:  if {@code INFO = -i}, the i-th argument had an illegal value
     */
    static int potrs(Layout layout, UPLO uplo, int n, int nrhs, double[] A, int lda, double[] B, int ldb) {
        var A_ = MemorySegment.ofArray(A);
        var B_ = MemorySegment.ofArray(B);
        return LAPACKE_dpotrs(layout.lapack(), uplo.lapack(), n, nrhs, A_, lda, B_, ldb);
    }

    /**
     * Solves a system of linear equations
     * <pre>{@code
     *     A * X = B
     * }</pre>
     * where A is an N-by-N symmetric positive definite matrix and
     * X and B are N-by-NRHS matrices using the Cholesky factorization
     * A = U<sup>T</sup>*U or A = L*L<sup>T</sup> computed by POTRF.
     *
     * @param layout The matrix layout.
     *
     * @param uplo The upper or lower triangular part of the matrix A is
     *             to be referenced.
     *
     * @param n The number of linear equations, i.e., the order of the matrix A.
     *
     * @param nrhs The number of right hand sides, i.e., the number of columns
     *             of the matrix B.
     *
     * @param A The triangular factor U or L from the Cholesky factorization
     *          A = U<sup>T</sup>*U or A = L*L<sup>T</sup>, as computed by POTRF.
     *
     * @param lda The leading dimension of the matrix A. {@code LDA >= max(1,N)}.
     *
     * @param B On entry, the N-by-NRHS matrix of right hand side matrix B.
     *          On exit, if INFO = 0, the N-by-NRHS solution matrix X.
     *
     * @param ldb The leading dimension of the matrix B. {@code LDB >= max(1,N)}.
     *
     * @return INFO flag.
     *         {@code = 0}:  successful exit
     *         {@code < 0}:  if {@code INFO = -i}, the i-th argument had an illegal value
     */
    static int potrs(Layout layout, UPLO uplo, int n, int nrhs, DoubleBuffer A, int lda, DoubleBuffer B, int ldb) {
        var A_ = MemorySegment.ofBuffer(A);
        var B_ = MemorySegment.ofBuffer(B);
        return LAPACKE_dpotrs(layout.lapack(), uplo.lapack(), n, nrhs, A_, lda, B_, ldb);
    }

    /**
     * Solves a system of linear equations
     * <pre>{@code
     *     A * X = B
     * }</pre>
     * where A is an N-by-N symmetric positive definite matrix and
     * X and B are N-by-NRHS matrices using the Cholesky factorization
     * A = U<sup>T</sup>*U or A = L*L<sup>T</sup> computed by POTRF.
     *
     * @param layout The matrix layout.
     *
     * @param uplo The upper or lower triangular part of the matrix A is
     *             to be referenced.
     *
     * @param n The number of linear equations, i.e., the order of the matrix A.
     *
     * @param nrhs The number of right hand sides, i.e., the number of columns
     *             of the matrix B.
     *
     * @param A The triangular factor U or L from the Cholesky factorization
     *          A = U<sup>T</sup>*U or A = L*L<sup>T</sup>, as computed by POTRF.
     *
     * @param lda The leading dimension of the matrix A. {@code LDA >= max(1,N)}.
     *
     * @param B On entry, the N-by-NRHS matrix of right hand side matrix B.
     *          On exit, if INFO = 0, the N-by-NRHS solution matrix X.
     *
     * @param ldb The leading dimension of the matrix B. {@code LDB >= max(1,N)}.
     *
     * @return INFO flag.
     *         {@code = 0}:  successful exit
     *         {@code < 0}:  if {@code INFO = -i}, the i-th argument had an illegal value
     */
    static int potrs(Layout layout, UPLO uplo, int n, int nrhs, MemorySegment A, int lda, MemorySegment B, int ldb) {
        return LAPACKE_dpotrs(layout.lapack(), uplo.lapack(), n, nrhs, A, lda, B, ldb);
    }

    /**
     * Solves a system of linear equations
     * <pre>{@code
     *     A * X = B
     * }</pre>
     * where A is an N-by-N symmetric positive definite matrix and
     * X and B are N-by-NRHS matrices using the Cholesky factorization
     * A = U<sup>T</sup>*U or A = L*L<sup>T</sup> computed by POTRF.
     *
     * @param layout The matrix layout.
     *
     * @param uplo The upper or lower triangular part of the matrix A is
     *             to be referenced.
     *
     * @param n The number of linear equations, i.e., the order of the matrix A.
     *
     * @param nrhs The number of right hand sides, i.e., the number of columns
     *             of the matrix B.
     *
     * @param A The triangular factor U or L from the Cholesky factorization
     *          A = U<sup>T</sup>*U or A = L*L<sup>T</sup>, as computed by POTRF.
     *
     * @param lda The leading dimension of the matrix A. {@code LDA >= max(1,N)}.
     *
     * @param B On entry, the N-by-NRHS matrix of right hand side matrix B.
     *          On exit, if INFO = 0, the N-by-NRHS solution matrix X.
     *
     * @param ldb The leading dimension of the matrix B. {@code LDB >= max(1,N)}.
     *
     * @return INFO flag.
     *         {@code = 0}:  successful exit
     *         {@code < 0}:  if {@code INFO = -i}, the i-th argument had an illegal value
     */
    static int potrs(Layout layout, UPLO uplo, int n, int nrhs, float[] A, int lda, float[] B, int ldb) {
        var A_ = MemorySegment.ofArray(A);
        var B_ = MemorySegment.ofArray(B);
        return LAPACKE_spotrs(layout.lapack(), uplo.lapack(), n, nrhs, A_, lda, B_, ldb);
    }

    /**
     * Solves a system of linear equations
     * <pre>{@code
     *     A * X = B
     * }</pre>
     * where A is an N-by-N symmetric positive definite matrix and
     * X and B are N-by-NRHS matrices using the Cholesky factorization
     * A = U<sup>T</sup>*U or A = L*L<sup>T</sup> computed by POTRF.
     *
     * @param layout The matrix layout.
     *
     * @param uplo The upper or lower triangular part of the matrix A is
     *             to be referenced.
     *
     * @param n The number of linear equations, i.e., the order of the matrix A.
     *
     * @param nrhs The number of right hand sides, i.e., the number of columns
     *             of the matrix B.
     *
     * @param A The triangular factor U or L from the Cholesky factorization
     *          A = U<sup>T</sup>*U or A = L*L<sup>T</sup>, as computed by POTRF.
     *
     * @param lda The leading dimension of the matrix A. {@code LDA >= max(1,N)}.
     *
     * @param B On entry, the N-by-NRHS matrix of right hand side matrix B.
     *          On exit, if INFO = 0, the N-by-NRHS solution matrix X.
     *
     * @param ldb The leading dimension of the matrix B. {@code LDB >= max(1,N)}.
     *
     * @return INFO flag.
     *         {@code = 0}:  successful exit
     *         {@code < 0}:  if {@code INFO = -i}, the i-th argument had an illegal value
     */
    static int potrs(Layout layout, UPLO uplo, int n, int nrhs, FloatBuffer A, int lda, FloatBuffer B, int ldb) {
        var A_ = MemorySegment.ofBuffer(A);
        var B_ = MemorySegment.ofBuffer(B);
        return LAPACKE_spotrs(layout.lapack(), uplo.lapack(), n, nrhs, A_, lda, B_, ldb);
    }

    /**
     * Solves a system of linear equations
     * <pre>{@code
     *     A * X = B
     * }</pre>
     * where A is an N-by-N symmetric positive definite band matrix and
     * X and B are N-by-NRHS matrices using the Cholesky factorization
     * A = U<sup>T</sup>*U or A = L*L<sup>T</sup> computed by POTRF.
     *
     * @param layout The matrix layout.
     *
     * @param uplo The upper or lower triangular part of the matrix A is
     *             to be referenced.
     *
     * @param n The number of linear equations, i.e., the order of the matrix A.
     *
     * @param kd The number of superdiagonals/subdiagonals of the matrix A.
     *
     * @param nrhs The number of right hand sides, i.e., the number of columns
     *             of the matrix B.
     *
     * @param AB The triangular factor U or L from the Cholesky factorization
     *          A = U<sup>T</sup>*U or A = L*L<sup>T</sup>, as computed by PBTRF.
     *
     * @param ldab The leading dimension of the matrix AB. {@code LDA >= max(1,N)}.
     *
     * @param B On entry, the N-by-NRHS matrix of right hand side matrix B.
     *          On exit, if INFO = 0, the N-by-NRHS solution matrix X.
     *
     * @param ldb The leading dimension of the matrix B. {@code LDB >= max(1,N)}.
     *
     * @return INFO flag.
     *         {@code = 0}:  successful exit
     *         {@code < 0}:  if {@code INFO = -i}, the i-th argument had an illegal value
     */
    static int pbtrs(Layout layout, UPLO uplo, int n, int kd, int nrhs,
                     double[] AB, int ldab, double[] B, int ldb) {
        var AB_ = MemorySegment.ofArray(AB);
        var B_ = MemorySegment.ofArray(B);
        return LAPACKE_dpbtrs(layout.lapack(), uplo.lapack(), n, kd, nrhs, AB_, ldab, B_, ldb);
    }

    /**
     * Solves a system of linear equations
     * <pre>{@code
     *     A * X = B
     * }</pre>
     * where A is an N-by-N symmetric positive definite band matrix and
     * X and B are N-by-NRHS matrices using the Cholesky factorization
     * A = U<sup>T</sup>*U or A = L*L<sup>T</sup> computed by POTRF.
     *
     * @param layout The matrix layout.
     *
     * @param uplo The upper or lower triangular part of the matrix A is
     *             to be referenced.
     *
     * @param n The number of linear equations, i.e., the order of the matrix A.
     *
     * @param kd The number of superdiagonals/subdiagonals of the matrix A.
     *
     * @param nrhs The number of right hand sides, i.e., the number of columns
     *             of the matrix B.
     *
     * @param AB The triangular factor U or L from the Cholesky factorization
     *          A = U<sup>T</sup>*U or A = L*L<sup>T</sup>, as computed by PBTRF.
     *
     * @param ldab The leading dimension of the matrix AB. {@code LDA >= max(1,N)}.
     *
     * @param B On entry, the N-by-NRHS matrix of right hand side matrix B.
     *          On exit, if INFO = 0, the N-by-NRHS solution matrix X.
     *
     * @param ldb The leading dimension of the matrix B. {@code LDB >= max(1,N)}.
     *
     * @return INFO flag.
     *         {@code = 0}:  successful exit
     *         {@code < 0}:  if {@code INFO = -i}, the i-th argument had an illegal value
     */
    static int pbtrs(Layout layout, UPLO uplo, int n, int kd, int nrhs,
                     DoubleBuffer AB, int ldab, DoubleBuffer B, int ldb) {
        var AB_ = MemorySegment.ofBuffer(AB);
        var B_ = MemorySegment.ofBuffer(B);
        return LAPACKE_dpbtrs(layout.lapack(), uplo.lapack(), n, kd, nrhs, AB_, ldab, B_, ldb);
    }

    /**
     * Solves a system of linear equations
     * <pre>{@code
     *     A * X = B
     * }</pre>
     * where A is an N-by-N symmetric positive definite band matrix and
     * X and B are N-by-NRHS matrices using the Cholesky factorization
     * A = U<sup>T</sup>*U or A = L*L<sup>T</sup> computed by POTRF.
     *
     * @param layout The matrix layout.
     *
     * @param uplo The upper or lower triangular part of the matrix A is
     *             to be referenced.
     *
     * @param n The number of linear equations, i.e., the order of the matrix A.
     *
     * @param kd The number of superdiagonals/subdiagonals of the matrix A.
     *
     * @param nrhs The number of right hand sides, i.e., the number of columns
     *             of the matrix B.
     *
     * @param AB The triangular factor U or L from the Cholesky factorization
     *          A = U<sup>T</sup>*U or A = L*L<sup>T</sup>, as computed by PBTRF.
     *
     * @param ldab The leading dimension of the matrix AB. {@code LDA >= max(1,N)}.
     *
     * @param B On entry, the N-by-NRHS matrix of right hand side matrix B.
     *          On exit, if INFO = 0, the N-by-NRHS solution matrix X.
     *
     * @param ldb The leading dimension of the matrix B. {@code LDB >= max(1,N)}.
     *
     * @return INFO flag.
     *         {@code = 0}:  successful exit
     *         {@code < 0}:  if {@code INFO = -i}, the i-th argument had an illegal value
     */
    static int pbtrs(Layout layout, UPLO uplo, int n, int kd, int nrhs,
                     float[] AB, int ldab, float[] B, int ldb) {
        var AB_ = MemorySegment.ofArray(AB);
        var B_ = MemorySegment.ofArray(B);
        return LAPACKE_spbtrs(layout.lapack(), uplo.lapack(), n, kd, nrhs, AB_, ldab, B_, ldb);
    }

    /**
     * Solves a system of linear equations
     * <pre>{@code
     *     A * X = B
     * }</pre>
     * where A is an N-by-N symmetric positive definite band matrix and
     * X and B are N-by-NRHS matrices using the Cholesky factorization
     * A = U<sup>T</sup>*U or A = L*L<sup>T</sup> computed by POTRF.
     *
     * @param layout The matrix layout.
     *
     * @param uplo The upper or lower triangular part of the matrix A is
     *             to be referenced.
     *
     * @param n The number of linear equations, i.e., the order of the matrix A.
     *
     * @param kd The number of superdiagonals/subdiagonals of the matrix A.
     *
     * @param nrhs The number of right hand sides, i.e., the number of columns
     *             of the matrix B.
     *
     * @param AB The triangular factor U or L from the Cholesky factorization
     *          A = U<sup>T</sup>*U or A = L*L<sup>T</sup>, as computed by PBTRF.
     *
     * @param ldab The leading dimension of the matrix AB. {@code LDA >= max(1,N)}.
     *
     * @param B On entry, the N-by-NRHS matrix of right hand side matrix B.
     *          On exit, if INFO = 0, the N-by-NRHS solution matrix X.
     *
     * @param ldb The leading dimension of the matrix B. {@code LDB >= max(1,N)}.
     *
     * @return INFO flag.
     *         {@code = 0}:  successful exit
     *         {@code < 0}:  if {@code INFO = -i}, the i-th argument had an illegal value
     */
    static int pbtrs(Layout layout, UPLO uplo, int n, int kd, int nrhs,
                     FloatBuffer AB, int ldab, FloatBuffer B, int ldb) {
        var AB_ = MemorySegment.ofBuffer(AB);
        var B_ = MemorySegment.ofBuffer(B);
        return LAPACKE_spbtrs(layout.lapack(), uplo.lapack(), n, kd, nrhs, AB_, ldab, B_, ldb);
    }

    /**
     * Solves a system of linear equations
     * <pre>{@code
     *     A * X = B
     * }</pre>
     * where A is an N-by-N symmetric positive definite packed matrix and
     * X and B are N-by-NRHS matrices using the Cholesky factorization
     * A = U<sup>T</sup>*U or A = L*L<sup>T</sup> computed by PPTRF.
     *
     * @param layout The matrix layout.
     *
     * @param uplo The upper or lower triangular part of the matrix A is
     *             to be referenced.
     *
     * @param n The number of linear equations, i.e., the order of the matrix A.
     *
     * @param nrhs The number of right hand sides, i.e., the number of columns
     *             of the matrix B.
     *
     * @param AP The triangular factor U or L from the Cholesky factorization
     *          A = U<sup>T</sup>*U or A = L*L<sup>T</sup>, as computed by PPTRF.
     *
     * @param B On entry, the N-by-NRHS matrix of right hand side matrix B.
     *          On exit, if INFO = 0, the N-by-NRHS solution matrix X.
     *
     * @param ldb The leading dimension of the matrix B. {@code LDB >= max(1,N)}.
     *
     * @return INFO flag.
     *         {@code = 0}:  successful exit
     *         {@code < 0}:  if {@code INFO = -i}, the i-th argument had an illegal value
     */
    static int pptrs(Layout layout, UPLO uplo, int n, int nrhs, double[] AP, double[] B, int ldb) {
        var AP_ = MemorySegment.ofArray(AP);
        var B_ = MemorySegment.ofArray(B);
        return LAPACKE_dpptrs(layout.lapack(), uplo.lapack(), n, nrhs, AP_, B_, ldb);
    }

    /**
     * Solves a system of linear equations
     * <pre>{@code
     *     A * X = B
     * }</pre>
     * where A is an N-by-N symmetric positive definite packed matrix and
     * X and B are N-by-NRHS matrices using the Cholesky factorization
     * A = U<sup>T</sup>*U or A = L*L<sup>T</sup> computed by PPTRF.
     *
     * @param layout The matrix layout.
     *
     * @param uplo The upper or lower triangular part of the matrix A is
     *             to be referenced.
     *
     * @param n The number of linear equations, i.e., the order of the matrix A.
     *
     * @param nrhs The number of right hand sides, i.e., the number of columns
     *             of the matrix B.
     *
     * @param AP The triangular factor U or L from the Cholesky factorization
     *          A = U<sup>T</sup>*U or A = L*L<sup>T</sup>, as computed by PPTRF.
     *
     * @param B On entry, the N-by-NRHS matrix of right hand side matrix B.
     *          On exit, if INFO = 0, the N-by-NRHS solution matrix X.
     *
     * @param ldb The leading dimension of the matrix B. {@code LDB >= max(1,N)}.
     *
     * @return INFO flag.
     *         {@code = 0}:  successful exit
     *         {@code < 0}:  if {@code INFO = -i}, the i-th argument had an illegal value
     */
    static int pptrs(Layout layout, UPLO uplo, int n, int nrhs, DoubleBuffer AP, DoubleBuffer B, int ldb) {
        var AP_ = MemorySegment.ofBuffer(AP);
        var B_ = MemorySegment.ofBuffer(B);
        return LAPACKE_dpptrs(layout.lapack(), uplo.lapack(), n, nrhs, AP_, B_, ldb);
    }

    /**
     * Solves a system of linear equations
     * <pre>{@code
     *     A * X = B
     * }</pre>
     * where A is an N-by-N symmetric positive definite packed matrix and
     * X and B are N-by-NRHS matrices using the Cholesky factorization
     * A = U<sup>T</sup>*U or A = L*L<sup>T</sup> computed by PPTRF.
     *
     * @param layout The matrix layout.
     *
     * @param uplo The upper or lower triangular part of the matrix A is
     *             to be referenced.
     *
     * @param n The number of linear equations, i.e., the order of the matrix A.
     *
     * @param nrhs The number of right hand sides, i.e., the number of columns
     *             of the matrix B.
     *
     * @param AP The triangular factor U or L from the Cholesky factorization
     *          A = U<sup>T</sup>*U or A = L*L<sup>T</sup>, as computed by PPTRF.
     *
     * @param B On entry, the N-by-NRHS matrix of right hand side matrix B.
     *          On exit, if INFO = 0, the N-by-NRHS solution matrix X.
     *
     * @param ldb The leading dimension of the matrix B. {@code LDB >= max(1,N)}.
     *
     * @return INFO flag.
     *         {@code = 0}:  successful exit
     *         {@code < 0}:  if {@code INFO = -i}, the i-th argument had an illegal value
     */
    static int pptrs(Layout layout, UPLO uplo, int n, int nrhs, float[] AP, float[] B, int ldb) {
        var AP_ = MemorySegment.ofArray(AP);
        var B_ = MemorySegment.ofArray(B);
        return LAPACKE_spptrs(layout.lapack(), uplo.lapack(), n, nrhs, AP_, B_, ldb);
    }

    /**
     * Solves a system of linear equations
     * <pre>{@code
     *     A * X = B
     * }</pre>
     * where A is an N-by-N symmetric positive definite packed matrix and
     * X and B are N-by-NRHS matrices using the Cholesky factorization
     * A = U<sup>T</sup>*U or A = L*L<sup>T</sup> computed by PPTRF.
     *
     * @param layout The matrix layout.
     *
     * @param uplo The upper or lower triangular part of the matrix A is
     *             to be referenced.
     *
     * @param n The number of linear equations, i.e., the order of the matrix A.
     *
     * @param nrhs The number of right hand sides, i.e., the number of columns
     *             of the matrix B.
     *
     * @param AP The triangular factor U or L from the Cholesky factorization
     *          A = U<sup>T</sup>*U or A = L*L<sup>T</sup>, as computed by PPTRF.
     *
     * @param B On entry, the N-by-NRHS matrix of right hand side matrix B.
     *          On exit, if INFO = 0, the N-by-NRHS solution matrix X.
     *
     * @param ldb The leading dimension of the matrix B. {@code LDB >= max(1,N)}.
     *
     * @return INFO flag.
     *         {@code = 0}:  successful exit
     *         {@code < 0}:  if {@code INFO = -i}, the i-th argument had an illegal value
     */
    static int pptrs(Layout layout, UPLO uplo, int n, int nrhs, FloatBuffer AP, FloatBuffer B, int ldb) {
        var AP_ = MemorySegment.ofBuffer(AP);
        var B_ = MemorySegment.ofBuffer(B);
        return LAPACKE_spptrs(layout.lapack(), uplo.lapack(), n, nrhs, AP_, B_, ldb);
    }

    /**
     * Computes a QR factorization of a general M-by-N matrix A.
     *
     * @param layout The matrix layout.
     *
     * @param m The number of rows of the matrix A.
     *
     * @param n The number of columns of the matrix A.
     *
     * @param A The matrix of dimension (LDA, N).
     *          On exit, the elements on and above the diagonal of the array
     *          contain the min(M,N)-by-N upper trapezoidal matrix R (R is
     *          upper triangular if {@code m >= n}); the elements below the diagonal,
     *          with the array TAU, represent the orthogonal matrix Q as a
     *          product of min(m,n) elementary reflectors.
     *
     * @param lda The leading dimension of the matrix A. {@code LDA >= max(1,N)}.
     *
     * @param tau The scalar factors of the elementary reflectors. Dimension min(M,N).
     *
     * @return INFO flag.
     *         {@code = 0}:  successful exit
     *         {@code < 0}:  if {@code INFO = -i}, the i-th argument had an illegal value
     */
    static int geqrf(Layout layout, int m, int n, double[] A, int lda, double[] tau) {
        var A_ = MemorySegment.ofArray(A);
        var tau_ = MemorySegment.ofArray(tau);
        return LAPACKE_dgeqrf(layout.lapack(), m, n, A_, lda, tau_);
    }

    /**
     * Computes a QR factorization of a general M-by-N matrix A.
     *
     * @param layout The matrix layout.
     *
     * @param m The number of rows of the matrix A.
     *
     * @param n The number of columns of the matrix A.
     *
     * @param A The matrix of dimension (LDA, N).
     *          On exit, the elements on and above the diagonal of the array
     *          contain the min(M,N)-by-N upper trapezoidal matrix R (R is
     *          upper triangular if {@code m >= n}); the elements below the diagonal,
     *          with the array TAU, represent the orthogonal matrix Q as a
     *          product of min(m,n) elementary reflectors.
     *
     * @param lda The leading dimension of the matrix A. {@code LDA >= max(1,N)}.
     *
     * @param tau The scalar factors of the elementary reflectors. Dimension min(M,N).
     *
     * @return INFO flag.
     *         {@code = 0}:  successful exit
     *         {@code < 0}:  if {@code INFO = -i}, the i-th argument had an illegal value
     */
    static int geqrf(Layout layout, int m, int n, DoubleBuffer A, int lda, DoubleBuffer tau) {
        var A_ = MemorySegment.ofBuffer(A);
        var tau_ = MemorySegment.ofBuffer(tau);
        return LAPACKE_dgeqrf(layout.lapack(), m, n, A_, lda, tau_);
    }

    /**
     * Computes a QR factorization of a general M-by-N matrix A.
     *
     * @param layout The matrix layout.
     *
     * @param m The number of rows of the matrix A.
     *
     * @param n The number of columns of the matrix A.
     *
     * @param A The matrix of dimension (LDA, N).
     *          On exit, the elements on and above the diagonal of the array
     *          contain the min(M,N)-by-N upper trapezoidal matrix R (R is
     *          upper triangular if {@code m >= n}); the elements below the diagonal,
     *          with the array TAU, represent the orthogonal matrix Q as a
     *          product of min(m,n) elementary reflectors.
     *
     * @param lda The leading dimension of the matrix A. {@code LDA >= max(1,N)}.
     *
     * @param tau The scalar factors of the elementary reflectors. Dimension min(M,N).
     *
     * @return INFO flag.
     *         {@code = 0}:  successful exit
     *         {@code < 0}:  if {@code INFO = -i}, the i-th argument had an illegal value
     */
    static int geqrf(Layout layout, int m, int n, MemorySegment A, int lda, MemorySegment tau) {
        return LAPACKE_dgeqrf(layout.lapack(), m, n, A, lda, tau);
    }

    /**
     * Computes a QR factorization of a general M-by-N matrix A.
     *
     * @param layout The matrix layout.
     *
     * @param m The number of rows of the matrix A.
     *
     * @param n The number of columns of the matrix A.
     *
     * @param A The matrix of dimension (LDA, N).
     *          On exit, the elements on and above the diagonal of the array
     *          contain the min(M,N)-by-N upper trapezoidal matrix R (R is
     *          upper triangular if {@code m >= n}); the elements below the diagonal,
     *          with the array TAU, represent the orthogonal matrix Q as a
     *          product of min(m,n) elementary reflectors.
     *
     * @param lda The leading dimension of the matrix A. {@code LDA >= max(1,N)}.
     *
     * @param tau The scalar factors of the elementary reflectors. Dimension min(M,N).
     *
     * @return INFO flag.
     *         {@code = 0}:  successful exit
     *         {@code < 0}:  if {@code INFO = -i}, the i-th argument had an illegal value
     */
    static int geqrf(Layout layout, int m, int n, float[] A, int lda, float[] tau) {
        var A_ = MemorySegment.ofArray(A);
        var tau_ = MemorySegment.ofArray(tau);
        return LAPACKE_sgeqrf(layout.lapack(), m, n, A_, lda, tau_);
    }

    /**
     * Computes a QR factorization of a general M-by-N matrix A.
     *
     * @param layout The matrix layout.
     *
     * @param m The number of rows of the matrix A.
     *
     * @param n The number of columns of the matrix A.
     *
     * @param A The matrix of dimension (LDA, N).
     *          On exit, the elements on and above the diagonal of the array
     *          contain the min(M,N)-by-N upper trapezoidal matrix R (R is
     *          upper triangular if {@code m >= n}); the elements below the diagonal,
     *          with the array TAU, represent the orthogonal matrix Q as a
     *          product of min(m,n) elementary reflectors.
     *
     * @param lda The leading dimension of the matrix A. {@code LDA >= max(1,N)}.
     *
     * @param tau The scalar factors of the elementary reflectors. Dimension min(M,N).
     *
     * @return INFO flag.
     *         {@code = 0}:  successful exit
     *         {@code < 0}:  if {@code INFO = -i}, the i-th argument had an illegal value
     */
    static int geqrf(Layout layout, int m, int n, FloatBuffer A, int lda, FloatBuffer tau) {
        var A_ = MemorySegment.ofBuffer(A);
        var tau_ = MemorySegment.ofBuffer(tau);
        return LAPACKE_sgeqrf(layout.lapack(), m, n, A_, lda, tau_);
    }

    /**
     * Overwrites the general real M-by-N matrix C with
     * <pre>{@code
     *                  SIDE = 'L'     SIDE = 'R'
     *  TRANS = 'N':      Q * C          C * Q
     *  TRANS = 'T':      Q**T * C       C * Q**T
     * }</pre>
     * where Q is a real orthogonal matrix defined as the product of k
     * elementary reflectors
     * <pre>{@code
     *        Q = H(1) H(2) . . . H(k)
     * }</pre>
     * as returned by GEQRF. Q is of order M if SIDE = 'L' and of order N
     * if SIDE = 'R'.
     *
     * @param layout The matrix layout.
     *
     * @param side Apply Q or Q<sup>T</sup> from the Left;
     *             or apply Q or Q<sup>T</sup> from the Right.
     *
     * @param trans No transpose, apply Q;
     *              Transpose, apply Q<sup>T</sup>.
     *
     * @param m The number of rows of the matrix A.
     *
     * @param n The number of columns of the matrix A.
     *
     * @param k The number of elementary reflectors whose product defines
     *          the matrix Q.
     *
     * @param A The matrix of dimension (LDA, K).
     *          The i-th column must contain the vector which defines the
     *          elementary reflector H(i), for i = 1,2,...,k, as returned by
     *          GEQRF in the first k columns of its array argument A.
     *
     * @param lda The leading dimension of the matrix A.
     *            If SIDE = 'L', {@code LDA >= max(1,M)};
     *            if SIDE = 'R', {@code LDA >= max(1,N)}.
     *
     * @param tau The scalar factors of the elementary reflectors, as returned by GEQRF.
     *
     * @param C On entry, the M-by-N matrix C.
     *          On exit, C is overwritten by Q*C or Q<sup>T</sup>*C or C*Q<sup>T</sup> or C*Q.
     *
     * @param ldc The leading dimension of the matrix C. {@code LDC >= max(1,M)}.
     *
     * @return INFO flag.
     *         {@code = 0}:  successful exit
     *         {@code < 0}:  if {@code INFO = -i}, the i-th argument had an illegal value
     */
    static int ormqr(Layout layout, Side side, Transpose trans, int m, int n, int k,
                     double[] A, int lda, double[] tau, double[] C, int ldc) {
        var A_ = MemorySegment.ofArray(A);
        var C_ = MemorySegment.ofArray(C);
        var tau_ = MemorySegment.ofArray(tau);
        return LAPACKE_dormqr(layout.lapack(), side.lapack(), trans.lapack(), m, n, k, A_, lda, tau_, C_, ldc);
    }

    /**
     * Overwrites the general real M-by-N matrix C with
     * <pre>{@code
     *                  SIDE = 'L'     SIDE = 'R'
     *  TRANS = 'N':      Q * C          C * Q
     *  TRANS = 'T':      Q**T * C       C * Q**T
     * }</pre>
     * where Q is a real orthogonal matrix defined as the product of k
     * elementary reflectors
     * <pre>{@code
     *        Q = H(1) H(2) . . . H(k)
     * }</pre>
     * as returned by GEQRF. Q is of order M if SIDE = 'L' and of order N
     * if SIDE = 'R'.
     *
     * @param layout The matrix layout.
     *
     * @param side Apply Q or Q<sup>T</sup> from the Left;
     *             or apply Q or Q<sup>T</sup> from the Right.
     *
     * @param trans No transpose, apply Q;
     *              Transpose, apply Q<sup>T</sup>.
     *
     * @param m The number of rows of the matrix A.
     *
     * @param n The number of columns of the matrix A.
     *
     * @param k The number of elementary reflectors whose product defines
     *          the matrix Q.
     *
     * @param A The matrix of dimension (LDA, K).
     *          The i-th column must contain the vector which defines the
     *          elementary reflector H(i), for i = 1,2,...,k, as returned by
     *          GEQRF in the first k columns of its array argument A.
     *
     * @param lda The leading dimension of the matrix A.
     *            If SIDE = 'L', {@code LDA >= max(1,M)};
     *            if SIDE = 'R', {@code LDA >= max(1,N)}.
     *
     * @param tau The scalar factors of the elementary reflectors, as returned by GEQRF.
     *
     * @param C On entry, the M-by-N matrix C.
     *          On exit, C is overwritten by Q*C or Q<sup>T</sup>*C or C*Q<sup>T</sup> or C*Q.
     *
     * @param ldc The leading dimension of the matrix C. {@code LDC >= max(1,M)}.
     *
     * @return INFO flag.
     *         {@code = 0}:  successful exit
     *         {@code < 0}:  if {@code INFO = -i}, the i-th argument had an illegal value
     */
    static int ormqr(Layout layout, Side side, Transpose trans, int m, int n, int k,
                     DoubleBuffer A, int lda, DoubleBuffer tau, DoubleBuffer C, int ldc) {
        var A_ = MemorySegment.ofBuffer(A);
        var C_ = MemorySegment.ofBuffer(C);
        var tau_ = MemorySegment.ofBuffer(tau);
        return LAPACKE_dormqr(layout.lapack(), side.lapack(), trans.lapack(), m, n, k, A_, lda, tau_, C_, ldc);
    }

    /**
     * Overwrites the general real M-by-N matrix C with
     * <pre>{@code
     *                  SIDE = 'L'     SIDE = 'R'
     *  TRANS = 'N':      Q * C          C * Q
     *  TRANS = 'T':      Q**T * C       C * Q**T
     * }</pre>
     * where Q is a real orthogonal matrix defined as the product of k
     * elementary reflectors
     * <pre>{@code
     *        Q = H(1) H(2) . . . H(k)
     * }</pre>
     * as returned by GEQRF. Q is of order M if SIDE = 'L' and of order N
     * if SIDE = 'R'.
     *
     * @param layout The matrix layout.
     *
     * @param side Apply Q or Q<sup>T</sup> from the Left;
     *             or apply Q or Q<sup>T</sup> from the Right.
     *
     * @param trans No transpose, apply Q;
     *              Transpose, apply Q<sup>T</sup>.
     *
     * @param m The number of rows of the matrix A.
     *
     * @param n The number of columns of the matrix A.
     *
     * @param k The number of elementary reflectors whose product defines
     *          the matrix Q.
     *
     * @param A The matrix of dimension (LDA, K).
     *          The i-th column must contain the vector which defines the
     *          elementary reflector H(i), for i = 1,2,...,k, as returned by
     *          GEQRF in the first k columns of its array argument A.
     *
     * @param lda The leading dimension of the matrix A.
     *            If SIDE = 'L', {@code LDA >= max(1,M)};
     *            if SIDE = 'R', {@code LDA >= max(1,N)}.
     *
     * @param tau The scalar factors of the elementary reflectors, as returned by GEQRF.
     *
     * @param C On entry, the M-by-N matrix C.
     *          On exit, C is overwritten by Q*C or Q<sup>T</sup>*C or C*Q<sup>T</sup> or C*Q.
     *
     * @param ldc The leading dimension of the matrix C. {@code LDC >= max(1,M)}.
     *
     * @return INFO flag.
     *         {@code = 0}:  successful exit
     *         {@code < 0}:  if {@code INFO = -i}, the i-th argument had an illegal value
     */
    static int ormqr(Layout layout, Side side, Transpose trans, int m, int n, int k,
                     MemorySegment A, int lda, MemorySegment tau, MemorySegment C, int ldc) {
        return LAPACKE_dormqr(layout.lapack(), side.lapack(), trans.lapack(), m, n, k, A, lda, tau, C, ldc);
    }

    /**
     * Overwrites the general real M-by-N matrix C with
     * <pre>{@code
     *                  SIDE = 'L'     SIDE = 'R'
     *  TRANS = 'N':      Q * C          C * Q
     *  TRANS = 'T':      Q**T * C       C * Q**T
     * }</pre>
     * where Q is a real orthogonal matrix defined as the product of k
     * elementary reflectors
     * <pre>{@code
     *        Q = H(1) H(2) . . . H(k)
     * }</pre>
     * as returned by GEQRF. Q is of order M if SIDE = 'L' and of order N
     * if SIDE = 'R'.
     *
     * @param layout The matrix layout.
     *
     * @param side Apply Q or Q<sup>T</sup> from the Left;
     *             or apply Q or Q<sup>T</sup> from the Right.
     *
     * @param trans No transpose, apply Q;
     *              Transpose, apply Q<sup>T</sup>.
     *
     * @param m The number of rows of the matrix A.
     *
     * @param n The number of columns of the matrix A.
     *
     * @param k The number of elementary reflectors whose product defines
     *          the matrix Q.
     *
     * @param A The matrix of dimension (LDA, K).
     *          The i-th column must contain the vector which defines the
     *          elementary reflector H(i), for i = 1,2,...,k, as returned by
     *          GEQRF in the first k columns of its array argument A.
     *
     * @param lda The leading dimension of the matrix A.
     *            If SIDE = 'L', {@code LDA >= max(1,M)};
     *            if SIDE = 'R', {@code LDA >= max(1,N)}.
     *
     * @param tau The scalar factors of the elementary reflectors, as returned by GEQRF.
     *
     * @param C On entry, the M-by-N matrix C.
     *          On exit, C is overwritten by Q*C or Q<sup>T</sup>*C or C*Q<sup>T</sup> or C*Q.
     *
     * @param ldc The leading dimension of the matrix C. {@code LDC >= max(1,M)}.
     *
     * @return INFO flag.
     *         {@code = 0}:  successful exit
     *         {@code < 0}:  if {@code INFO = -i}, the i-th argument had an illegal value
     */
    static int ormqr(Layout layout, Side side, Transpose trans, int m, int n, int k,
                     float[] A, int lda, float[] tau, float[] C, int ldc) {
        var A_ = MemorySegment.ofArray(A);
        var C_ = MemorySegment.ofArray(C);
        var tau_ = MemorySegment.ofArray(tau);
        return LAPACKE_sormqr(layout.lapack(), side.lapack(), trans.lapack(), m, n, k, A_, lda, tau_, C_, ldc);
    }

    /**
     * Overwrites the general real M-by-N matrix C with
     * <pre>{@code
     *                  SIDE = 'L'     SIDE = 'R'
     *  TRANS = 'N':      Q * C          C * Q
     *  TRANS = 'T':      Q**T * C       C * Q**T
     * }</pre>
     * where Q is a real orthogonal matrix defined as the product of k
     * elementary reflectors
     * <pre>{@code
     *        Q = H(1) H(2) . . . H(k)
     * }</pre>
     * as returned by GEQRF. Q is of order M if SIDE = 'L' and of order N
     * if SIDE = 'R'.
     *
     * @param layout The matrix layout.
     *
     * @param side Apply Q or Q<sup>T</sup> from the Left;
     *             or apply Q or Q<sup>T</sup> from the Right.
     *
     * @param trans No transpose, apply Q;
     *              Transpose, apply Q<sup>T</sup>.
     *
     * @param m The number of rows of the matrix A.
     *
     * @param n The number of columns of the matrix A.
     *
     * @param k The number of elementary reflectors whose product defines
     *          the matrix Q.
     *
     * @param A The matrix of dimension (LDA, K).
     *          The i-th column must contain the vector which defines the
     *          elementary reflector H(i), for i = 1,2,...,k, as returned by
     *          GEQRF in the first k columns of its array argument A.
     *
     * @param lda The leading dimension of the matrix A.
     *            If SIDE = 'L', {@code LDA >= max(1,M)};
     *            if SIDE = 'R', {@code LDA >= max(1,N)}.
     *
     * @param tau The scalar factors of the elementary reflectors, as returned by GEQRF.
     *
     * @param C On entry, the M-by-N matrix C.
     *          On exit, C is overwritten by Q*C or Q<sup>T</sup>*C or C*Q<sup>T</sup> or C*Q.
     *
     * @param ldc The leading dimension of the matrix C. {@code LDC >= max(1,M)}.
     *
     * @return INFO flag.
     *         {@code = 0}:  successful exit
     *         {@code < 0}:  if {@code INFO = -i}, the i-th argument had an illegal value
     */
    static int ormqr(Layout layout, Side side, Transpose trans, int m, int n, int k,
                     FloatBuffer A, int lda, FloatBuffer tau, FloatBuffer C, int ldc) {
        var A_ = MemorySegment.ofBuffer(A);
        var C_ = MemorySegment.ofBuffer(C);
        var tau_ = MemorySegment.ofBuffer(tau);
        return LAPACKE_sormqr(layout.lapack(), side.lapack(), trans.lapack(), m, n, k, A_, lda, tau_, C_, ldc);
    }

    /**
     * Generates the real orthogonal matrix Q of the QR factorization formed by geqrf.
     *
     * @param layout The matrix layout.
     *
     * @param m The number of rows of the matrix A.
     *
     * @param n The number of columns of the matrix A.
     *
     * @param k The minimum number of rows and columns of the matrix A.
     *
     * @param A The matrix of dimension (LDA, N).
     *          On exit, the elements on and above the diagonal of the array
     *          contain the min(M,N)-by-N upper trapezoidal matrix R (R is
     *          upper triangular if {@code m >= n}); the elements below the diagonal,
     *          with the array TAU, represent the orthogonal matrix Q as a
     *          product of min(m,n) elementary reflectors.
     *
     * @param lda The leading dimension of the matrix A. {@code LDA >= max(1,N)}.
     *
     * @param tau The scalar factors of the elementary reflectors. Dimension min(M,N).
     *
     * @return INFO flag.
     *         {@code = 0}:  successful exit
     *         {@code < 0}:  if {@code INFO = -i}, the i-th argument had an illegal value
     */
    static int orgqr(Layout layout, int m, int n, int k, double[] A, int lda, double[] tau) {
        var A_ = MemorySegment.ofArray(A);
        var tau_ = MemorySegment.ofArray(tau);
        return LAPACKE_dorgqr(layout.lapack(), m, n, k, A_, lda, tau_);
    }

    /**
     * Generates the real orthogonal matrix Q of the QR factorization formed by geqrf.
     *
     * @param layout The matrix layout.
     *
     * @param m The number of rows of the matrix A.
     *
     * @param n The number of columns of the matrix A.
     *
     * @param k The minimum number of rows and columns of the matrix A.
     *
     * @param A The matrix of dimension (LDA, N).
     *          On exit, the elements on and above the diagonal of the array
     *          contain the min(M,N)-by-N upper trapezoidal matrix R (R is
     *          upper triangular if {@code m >= n}); the elements below the diagonal,
     *          with the array TAU, represent the orthogonal matrix Q as a
     *          product of min(m,n) elementary reflectors.
     *
     * @param lda The leading dimension of the matrix A. {@code LDA >= max(1,N)}.
     *
     * @param tau The scalar factors of the elementary reflectors. Dimension min(M,N).
     *
     * @return INFO flag.
     *         {@code = 0}:  successful exit
     *         {@code < 0}:  if {@code INFO = -i}, the i-th argument had an illegal value
     */
    static int orgqr(Layout layout, int m, int n, int k, DoubleBuffer A, int lda, DoubleBuffer tau) {
        var A_ = MemorySegment.ofBuffer(A);
        var tau_ = MemorySegment.ofBuffer(tau);
        return LAPACKE_dorgqr(layout.lapack(), m, n, k, A_, lda, tau_);
    }

    /**
     * Generates the real orthogonal matrix Q of the QR factorization formed by geqrf.
     *
     * @param layout The matrix layout.
     *
     * @param m The number of rows of the matrix A.
     *
     * @param n The number of columns of the matrix A.
     *
     * @param k The minimum number of rows and columns of the matrix A.
     *
     * @param A The matrix of dimension (LDA, N).
     *          On exit, the elements on and above the diagonal of the array
     *          contain the min(M,N)-by-N upper trapezoidal matrix R (R is
     *          upper triangular if {@code m >= n}); the elements below the diagonal,
     *          with the array TAU, represent the orthogonal matrix Q as a
     *          product of min(m,n) elementary reflectors.
     *
     * @param lda The leading dimension of the matrix A. {@code LDA >= max(1,N)}.
     *
     * @param tau The scalar factors of the elementary reflectors. Dimension min(M,N).
     *
     * @return INFO flag.
     *         {@code = 0}:  successful exit
     *         {@code < 0}:  if {@code INFO = -i}, the i-th argument had an illegal value
     */
    static int orgqr(Layout layout, int m, int n, int k, MemorySegment A, int lda, MemorySegment tau) {
        return LAPACKE_dorgqr(layout.lapack(), m, n, k, A, lda, tau);
    }

    /**
     * Generates the real orthogonal matrix Q of the QR factorization formed by geqrf.
     *
     * @param layout The matrix layout.
     *
     * @param m The number of rows of the matrix A.
     *
     * @param n The number of columns of the matrix A.
     *
     * @param k The minimum number of rows and columns of the matrix A.
     *
     * @param A The matrix of dimension (LDA, N).
     *          On exit, the elements on and above the diagonal of the array
     *          contain the min(M,N)-by-N upper trapezoidal matrix R (R is
     *          upper triangular if {@code m >= n}); the elements below the diagonal,
     *          with the array TAU, represent the orthogonal matrix Q as a
     *          product of min(m,n) elementary reflectors.
     *
     * @param lda The leading dimension of the matrix A. {@code LDA >= max(1,N)}.
     *
     * @param tau The scalar factors of the elementary reflectors. Dimension min(M,N).
     *
     * @return INFO flag.
     *         {@code = 0}:  successful exit
     *         {@code < 0}:  if {@code INFO = -i}, the i-th argument had an illegal value
     */
    static int orgqr(Layout layout, int m, int n, int k, float[] A, int lda, float[] tau) {
        var A_ = MemorySegment.ofArray(A);
        var tau_ = MemorySegment.ofArray(tau);
        return LAPACKE_sorgqr(layout.lapack(), m, n, k, A_, lda, tau_);
    }

    /**
     * Generates the real orthogonal matrix Q of the QR factorization formed by geqrf.
     *
     * @param layout The matrix layout.
     *
     * @param m The number of rows of the matrix A.
     *
     * @param n The number of columns of the matrix A.
     *
     * @param k The minimum number of rows and columns of the matrix A.
     *
     * @param A The matrix of dimension (LDA, N).
     *          On exit, the elements on and above the diagonal of the array
     *          contain the min(M,N)-by-N upper trapezoidal matrix R (R is
     *          upper triangular if {@code m >= n}); the elements below the diagonal,
     *          with the array TAU, represent the orthogonal matrix Q as a
     *          product of min(m,n) elementary reflectors.
     *
     * @param lda The leading dimension of the matrix A. {@code LDA >= max(1,N)}.
     *
     * @param tau The scalar factors of the elementary reflectors. Dimension min(M,N).
     *
     * @return INFO flag.
     *         {@code = 0}:  successful exit
     *         {@code < 0}:  if {@code INFO = -i}, the i-th argument had an illegal value
     */
    static int orgqr(Layout layout, int m, int n, int k, FloatBuffer A, int lda, FloatBuffer tau) {
        var A_ = MemorySegment.ofBuffer(A);
        var tau_ = MemorySegment.ofBuffer(tau);
        return LAPACKE_sorgqr(layout.lapack(), m, n, k, A_, lda, tau_);
    }

    /**
     * Solves a triangular system of the form
     * <pre>{@code
     *     A * X = B
     * }</pre>
     * or
     * <pre>{@code
     *     A**T * X = B
     * }</pre>
     * where A is a triangular matrix of order N, and B is an N-by-NRHS
     * matrix. A check is made to verify that A is nonsingular.
     *
     * @param layout The matrix layout.
     *
     * @param uplo The upper or lower triangular part of the matrix A is
     *             to be referenced.
     *
     * @param trans The normal or transpose of the matrix A.
     *
     * @param diag A is unit diagonal triangular or not.
     *
     * @param n The order of the matrix A.
     *
     * @param nrhs The number of right hand sides.
     *
     * @param A The triangular matrix A.
     *
     * @param lda The leading dimension of the matrix A.
     *
     * @param B On entry, the right hand side matrix B.
     *          On exit, if INFO = 0, the solution matrix X.
     *
     * @param ldb The leading dimension of the matrix B.
     *
     * @return INFO flag.
     *         {@code = 0}:  successful exit
     *         {@code < 0}:  if {@code INFO = -i}, the i-th argument had an illegal value
     *         {@code > 0}: if {@code INFO = i}, the i-th diagonal element of A is zero,
     *              indicating that the matrix is singular and the solutions
     *              X have not been computed.
     */
    static int trtrs(Layout layout, UPLO uplo, Transpose trans, Diag diag, int n, int nrhs,
                     double[] A, int lda, double[] B, int ldb) {
        var A_ = MemorySegment.ofArray(A);
        var B_ = MemorySegment.ofArray(B);
        return LAPACKE_dtrtrs(layout.lapack(), uplo.lapack(), trans.lapack(), diag.lapack(), n, nrhs, A_, lda, B_, ldb);
    }

    /**
     * Solves a triangular system of the form
     * <pre>{@code
     *     A * X = B
     * }</pre>
     * or
     * <pre>{@code
     *     A**T * X = B
     * }</pre>
     * where A is a triangular matrix of order N, and B is an N-by-NRHS
     * matrix. A check is made to verify that A is nonsingular.
     *
     * @param layout The matrix layout.
     *
     * @param uplo The upper or lower triangular part of the matrix A is
     *             to be referenced.
     *
     * @param trans The normal or transpose of the matrix A.
     *
     * @param diag A is unit diagonal triangular or not.
     *
     * @param n The order of the matrix A.
     *
     * @param nrhs The number of right hand sides.
     *
     * @param A The triangular matrix A.
     *
     * @param lda The leading dimension of the matrix A.
     *
     * @param B On entry, the right hand side matrix B.
     *          On exit, if INFO = 0, the solution matrix X.
     *
     * @param ldb The leading dimension of the matrix B.
     *
     * @return INFO flag.
     *         {@code = 0}:  successful exit
     *         {@code < 0}:  if {@code INFO = -i}, the i-th argument had an illegal value
     *         {@code > 0}: if {@code INFO = i}, the i-th diagonal element of A is zero,
     *              indicating that the matrix is singular and the solutions
     *              X have not been computed.
     */
    static int trtrs(Layout layout, UPLO uplo, Transpose trans, Diag diag, int n, int nrhs,
                     DoubleBuffer A, int lda, DoubleBuffer B, int ldb) {
        var A_ = MemorySegment.ofBuffer(A);
        var B_ = MemorySegment.ofBuffer(B);
        return LAPACKE_dtrtrs(layout.lapack(), uplo.lapack(), trans.lapack(), diag.lapack(), n, nrhs, A_, lda, B_, ldb);
    }

    /**
     * Solves a triangular system of the form
     * <pre>{@code
     *     A * X = B
     * }</pre>
     * or
     * <pre>{@code
     *     A**T * X = B
     * }</pre>
     * where A is a triangular matrix of order N, and B is an N-by-NRHS
     * matrix. A check is made to verify that A is nonsingular.
     *
     * @param layout The matrix layout.
     *
     * @param uplo The upper or lower triangular part of the matrix A is
     *             to be referenced.
     *
     * @param trans The normal or transpose of the matrix A.
     *
     * @param diag A is unit diagonal triangular or not.
     *
     * @param n The order of the matrix A.
     *
     * @param nrhs The number of right hand sides.
     *
     * @param A The triangular matrix A.
     *
     * @param lda The leading dimension of the matrix A.
     *
     * @param B On entry, the right hand side matrix B.
     *          On exit, if INFO = 0, the solution matrix X.
     *
     * @param ldb The leading dimension of the matrix B.
     *
     * @return INFO flag.
     *         {@code = 0}:  successful exit
     *         {@code < 0}:  if {@code INFO = -i}, the i-th argument had an illegal value
     *         {@code > 0}: if {@code INFO = i}, the i-th diagonal element of A is zero,
     *              indicating that the matrix is singular and the solutions
     *              X have not been computed.
     */
    static int trtrs(Layout layout, UPLO uplo, Transpose trans, Diag diag, int n, int nrhs,
                     MemorySegment A, int lda, MemorySegment B, int ldb) {
        return LAPACKE_dtrtrs(layout.lapack(), uplo.lapack(), trans.lapack(), diag.lapack(), n, nrhs, A, lda, B, ldb);
    }

    /**
     * Solves a triangular system of the form
     * <pre>{@code
     *     A * X = B
     * }</pre>
     * or
     * <pre>{@code
     *     A**T * X = B
     * }</pre>
     * where A is a triangular matrix of order N, and B is an N-by-NRHS
     * matrix. A check is made to verify that A is nonsingular.
     *
     * @param layout The matrix layout.
     *
     * @param uplo The upper or lower triangular part of the matrix A is
     *             to be referenced.
     *
     * @param trans The normal or transpose of the matrix A.
     *
     * @param diag A is unit diagonal triangular or not.
     *
     * @param n The order of the matrix A.
     *
     * @param nrhs The number of right hand sides.
     *
     * @param A The triangular matrix A.
     *
     * @param lda The leading dimension of the matrix A.
     *
     * @param B On entry, the right hand side matrix B.
     *          On exit, if INFO = 0, the solution matrix X.
     *
     * @param ldb The leading dimension of the matrix B.
     *
     * @return INFO flag.
     *         {@code = 0}:  successful exit
     *         {@code < 0}:  if {@code INFO = -i}, the i-th argument had an illegal value
     *         {@code > 0}: if {@code INFO = i}, the i-th diagonal element of A is zero,
     *              indicating that the matrix is singular and the solutions
     *              X have not been computed.
     */
    static int trtrs(Layout layout, UPLO uplo, Transpose trans, Diag diag, int n, int nrhs,
                     float[] A, int lda, float[] B, int ldb) {
        var A_ = MemorySegment.ofArray(A);
        var B_ = MemorySegment.ofArray(B);
        return LAPACKE_strtrs(layout.lapack(), uplo.lapack(), trans.lapack(), diag.lapack(), n, nrhs, A_, lda, B_, ldb);
    }

    /**
     * Solves a triangular system of the form
     * <pre>{@code
     *     A * X = B
     * }</pre>
     * or
     * <pre>{@code
     *     A**T * X = B
     * }</pre>
     * where A is a triangular matrix of order N, and B is an N-by-NRHS
     * matrix. A check is made to verify that A is nonsingular.
     *
     * @param layout The matrix layout.
     *
     * @param uplo The upper or lower triangular part of the matrix A is
     *             to be referenced.
     *
     * @param trans The normal or transpose of the matrix A.
     *
     * @param diag A is unit diagonal triangular or not.
     *
     * @param n The order of the matrix A.
     *
     * @param nrhs The number of right hand sides.
     *
     * @param A The triangular matrix A.
     *
     * @param lda The leading dimension of the matrix A.
     *
     * @param B On entry, the right hand side matrix B.
     *          On exit, if INFO = 0, the solution matrix X.
     *
     * @param ldb The leading dimension of the matrix B.
     *
     * @return INFO flag.
     *         {@code = 0}:  successful exit
     *         {@code < 0}:  if {@code INFO = -i}, the i-th argument had an illegal value
     *         {@code > 0}: if {@code INFO = i}, the i-th diagonal element of A is zero,
     *              indicating that the matrix is singular and the solutions
     *              X have not been computed.
     */
    static int trtrs(Layout layout, UPLO uplo, Transpose trans, Diag diag, int n, int nrhs,
                     FloatBuffer A, int lda, FloatBuffer B, int ldb) {
        var A_ = MemorySegment.ofBuffer(A);
        var B_ = MemorySegment.ofBuffer(B);
        return LAPACKE_strtrs(layout.lapack(), uplo.lapack(), trans.lapack(), diag.lapack(), n, nrhs, A_, lda, B_, ldb);
    }
}
