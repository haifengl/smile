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

package smile.math.blas;

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
     * <pre><code>
     *     A * X = B
     * </code></pre>
     * where A is an N-by-N matrix and X and B are N-by-NRHS matrices.
     *
     * The LU decomposition with partial pivoting and row interchanges is
     * used to factor A as
     * <pre><code>
     *     A = P * L * U
     * </code></pre>
     * where P is a permutation matrix, L is unit lower triangular, and U is
     * upper triangular. The factored form of A is then used to solve the
     * system of equations A * X = B.
     *
     * @param layout matrix layout.
     *
     * @param n The number of linear equations, i.e., the order of the matrix A.
     *
     * @param nrhs The number of right hand sides, i.e., the number of columns
     *             of the matrix B.
     *
     * @param A The matrix of dimension (LDA, N).
     *          On exit, the factors L and U from the factorization
     *          A = P*L*U; the unit diagonal elements of L are not stored.
     *
     * @param lda The leading dimension of the array A. LDA >= max(1,N).
     *
     * @param ipiv The pivot indices that define the permutation matrix P;
     *             row i of the matrix was interchanged with row IPIV(i).
     *
     * @param B On entry, the N-by-NRHS matrix of right hand side matrix B.
     *          On exit, if INFO = 0, the N-by-NRHS solution matrix X.
     *
     * @param ldb The leading dimension of the array B. LDB >= max(1,N).
     *
     * @return INFO flag.
     *         = 0:  successful exit
     *         < 0:  if INFO = -i, the i-th argument had an illegal value
     *         > 0:  if INFO = i, U(i,i) is exactly zero. The factorization
     *               has been completed, but the factor U is exactly
     *               singular, so the solution could not be computed.
     */
    int gesv(Layout layout, int n, int nrhs, double[] A, int lda, int[] ipiv, double[] B, int ldb);

    /**
     * Solves a real system of linear equations.
     * <pre><code>
     *     A * X = B
     * </code></pre>
     * where A is an N-by-N matrix and X and B are N-by-NRHS matrices.
     *
     * The LU decomposition with partial pivoting and row interchanges is
     * used to factor A as
     * <pre><code>
     *     A = P * L * U
     * </code></pre>
     * where P is a permutation matrix, L is unit lower triangular, and U is
     * upper triangular. The factored form of A is then used to solve the
     * system of equations A * X = B.
     *
     * @param layout matrix layout.
     *
     * @param n The number of linear equations, i.e., the order of the matrix A.
     *
     * @param nrhs The number of right hand sides, i.e., the number of columns
     *             of the matrix B.
     *
     * @param A The matrix of dimension (LDA, N).
     *          On exit, the factors L and U from the factorization
     *          A = P*L*U; the unit diagonal elements of L are not stored.
     *
     * @param lda The leading dimension of the array A. LDA >= max(1,N).
     *
     * @param ipiv The pivot indices that define the permutation matrix P;
     *             row i of the matrix was interchanged with row IPIV(i).
     *
     * @param B On entry, the N-by-NRHS matrix of right hand side matrix B.
     *          On exit, if INFO = 0, the N-by-NRHS solution matrix X.
     *
     * @param ldb The leading dimension of the array B. LDB >= max(1,N).
     *
     * @return INFO flag.
     *         = 0:  successful exit
     *         < 0:  if INFO = -i, the i-th argument had an illegal value
     *         > 0:  if INFO = i, U(i,i) is exactly zero. The factorization
     *               has been completed, but the factor U is exactly
     *               singular, so the solution could not be computed.
     */
    int gesv(Layout layout, int n, int nrhs, float[] A, int lda, int[] ipiv, float[] B, int ldb);

    /**
     * Solves a real system of linear equations.
     * <pre><code>
     *     A * X = B
     * </code></pre>
     * where A is an N-by-N symmetric matrix and X and B are N-by-NRHS matrices.
     *
     * The diagonal pivoting method is used to factor A as
     * <pre><code>
     *     A = U * D * U**T,  if UPLO = 'U'
     * </code></pre>
     * or
     * <pre><code>
     *     A = L * D * L**T,  if UPLO = 'L'
     * </code></pre>
     * where U (or L) is a product of permutation and unit upper (lower)
     * triangular matrices, and D is symmetric and block diagonal with
     * 1-by-1 and 2-by-2 diagonal blocks. The factored form of A is then
     * used to solve the system of equations A * X = B.
     *
     * @param layout matrix layout.
     *
     * @param uplo the upper or lower triangular part of the matrix A is
     *             to be referenced.
     *
     * @param n The number of linear equations, i.e., the order of the matrix A.
     *
     * @param nrhs The number of right hand sides, i.e., the number of columns
     *             of the matrix B.
     *
     * @param A The symmetric matrix of dimension (LDA, N).
     *          On exit, the factor U or L from the Cholesky factorization
     *          A = U**T*U or A = L*L**T.
     *
     * @param lda The leading dimension of the array A. LDA >= max(1,N).
     *
     * @param B On entry, the N-by-NRHS matrix of right hand side matrix B.
     *          On exit, if INFO = 0, the N-by-NRHS solution matrix X.
     *
     * @param ldb The leading dimension of the array B. LDB >= max(1,N).
     *
     * @return INFO flag.
     *         = 0:  successful exit
     *         < 0:  if INFO = -i, the i-th argument had an illegal value
     *         > 0:  if INFO = i, the leading minor of order i of A is not
     *               positive definite, so the factorization could not be
     *               completed, and the solution has not been computed.
     */
    int sysv(Layout layout, UPLO uplo, int n, int nrhs, double[] A, int lda, int[] ipiv, double[] B, int ldb);

    /**
     * Solves a real system of linear equations.
     * <pre><code>
     *     A * X = B
     * </code></pre>
     * where A is an N-by-N symmetric matrix and X and B are N-by-NRHS matrices.
     *
     * The diagonal pivoting method is used to factor A as
     * <pre><code>
     *     A = U * D * U**T,  if UPLO = 'U'
     * </code></pre>
     * or
     * <pre><code>
     *     A = L * D * L**T,  if UPLO = 'L'
     * </code></pre>
     * where U (or L) is a product of permutation and unit upper (lower)
     * triangular matrices, and D is symmetric and block diagonal with
     * 1-by-1 and 2-by-2 diagonal blocks. The factored form of A is then
     * used to solve the system of equations A * X = B.
     *
     * @param layout matrix layout.
     *
     * @param uplo the upper or lower triangular part of the matrix A is
     *             to be referenced.
     *
     * @param n The number of linear equations, i.e., the order of the matrix A.
     *
     * @param nrhs The number of right hand sides, i.e., the number of columns
     *             of the matrix B.
     *
     * @param A The symmetric matrix of dimension (LDA, N).
     *          On exit, the factor U or L from the Cholesky factorization
     *          A = U**T*U or A = L*L**T.
     *
     * @param lda The leading dimension of the array A. LDA >= max(1,N).
     *
     * @param B On entry, the N-by-NRHS matrix of right hand side matrix B.
     *          On exit, if INFO = 0, the N-by-NRHS solution matrix X.
     *
     * @param ldb The leading dimension of the array B. LDB >= max(1,N).
     *
     * @return INFO flag.
     *         = 0:  successful exit
     *         < 0:  if INFO = -i, the i-th argument had an illegal value
     *         > 0:  if INFO = i, the leading minor of order i of A is not
     *               positive definite, so the factorization could not be
     *               completed, and the solution has not been computed.
     */
    int sysv(Layout layout, UPLO uplo, int n, int nrhs, float[] A, int lda, int[] ipiv, float[] B, int ldb);

    /**
     * Solves a real system of linear equations.
     * <pre><code>
     *     A * X = B
     * </code></pre>
     * where A is an N-by-N symmetric matrix and X and B are N-by-NRHS matrices.
     *
     * The diagonal pivoting method is used to factor A as
     * <pre><code>
     *     A = U * D * U**T,  if UPLO = 'U'
     * </code></pre>
     * or
     * <pre><code>
     *     A = L * D * L**T,  if UPLO = 'L'
     * </code></pre>
     * where U (or L) is a product of permutation and unit upper (lower)
     * triangular matrices, and D is symmetric and block diagonal with
     * 1-by-1 and 2-by-2 diagonal blocks. The factored form of A is then
     * used to solve the system of equations A * X = B.
     *
     * @param layout matrix layout.
     *
     * @param uplo the upper or lower triangular part of the matrix A is
     *             to be referenced.
     *
     * @param n The number of linear equations, i.e., the order of the matrix A.
     *
     * @param nrhs The number of right hand sides, i.e., the number of columns
     *             of the matrix B.
     *
     * @param A The symmetric packed matrix.
     *          On exit, the factor U or L from the Cholesky factorization
     *          A = U**T*U or A = L*L**T, in the same storage format as A.
     *
     * @param B On entry, the N-by-NRHS matrix of right hand side matrix B.
     *          On exit, if INFO = 0, the N-by-NRHS solution matrix X.
     *
     * @param ldb The leading dimension of the array B. LDB >= max(1,N).
     *
     * @return INFO flag.
     *         = 0:  successful exit
     *         < 0:  if INFO = -i, the i-th argument had an illegal value
     *         > 0:  if INFO = i, the leading minor of order i of A is not
     *               positive definite, so the factorization could not be
     *               completed, and the solution has not been computed.
     */
    int spsv(Layout layout, UPLO uplo, int n, int nrhs, double[] A, int[] ipiv, double[] B, int ldb);

    /**
     * Solves a real system of linear equations.
     * <pre><code>
     *     A * X = B
     * </code></pre>
     * where A is an N-by-N symmetric matrix and X and B are N-by-NRHS matrices.
     *
     * The diagonal pivoting method is used to factor A as
     * <pre><code>
     *     A = U * D * U**T,  if UPLO = 'U'
     * </code></pre>
     * or
     * <pre><code>
     *     A = L * D * L**T,  if UPLO = 'L'
     * </code></pre>
     * where U (or L) is a product of permutation and unit upper (lower)
     * triangular matrices, and D is symmetric and block diagonal with
     * 1-by-1 and 2-by-2 diagonal blocks. The factored form of A is then
     * used to solve the system of equations A * X = B.
     *
     * @param layout matrix layout.
     *
     * @param uplo the upper or lower triangular part of the matrix A is
     *             to be referenced.
     *
     * @param n The number of linear equations, i.e., the order of the matrix A.
     *
     * @param nrhs The number of right hand sides, i.e., the number of columns
     *             of the matrix B.
     *
     * @param A The symmetric packed matrix.
     *          On exit, the factor U or L from the Cholesky factorization
     *          A = U**T*U or A = L*L**T, in the same storage format as A.
     *
     * @param B On entry, the N-by-NRHS matrix of right hand side matrix B.
     *          On exit, if INFO = 0, the N-by-NRHS solution matrix X.
     *
     * @param ldb The leading dimension of the array B. LDB >= max(1,N).
     *
     * @return INFO flag.
     *         = 0:  successful exit
     *         < 0:  if INFO = -i, the i-th argument had an illegal value
     *         > 0:  if INFO = i, the leading minor of order i of A is not
     *               positive definite, so the factorization could not be
     *               completed, and the solution has not been computed.
     */
    int spsv(Layout layout, UPLO uplo, int n, int nrhs, float[] A, int[] ipiv, float[] B, int ldb);

    /**
     * Solves a real system of linear equations.
     * <pre><code>
     *     A * X = B
     * </code></pre>
     * where A is an N-by-N symmetric positive definite matrix and X and B are N-by-NRHS matrices.
     *
     * The Cholesky decomposition is used to factor A as
     * <pre><code>
     *     A = U**T* U,  if UPLO = 'U'
     * </code></pre>
     * or
     * <pre><code>
     *     A = L * L**T,  if UPLO = 'L'
     * </code></pre>
     * where U is an upper triangular matrix and L is a lower triangular
     * matrix.  The factored form of A is then used to solve the system of
     * equations A * X = B.
     *
     * @param layout matrix layout.
     *
     * @param uplo the upper or lower triangular part of the matrix A is
     *             to be referenced.
     *
     * @param n The number of linear equations, i.e., the order of the matrix A.
     *
     * @param nrhs The number of right hand sides, i.e., the number of columns
     *             of the matrix B.
     *
     * @param A The symmetric matrix of dimension (LDA, N).
     *          On exit, the factor U or L from the Cholesky factorization
     *          A = U**T*U or A = L*L**T.
     *
     * @param lda The leading dimension of the array A. LDA >= max(1,N).
     *
     * @param B On entry, the N-by-NRHS matrix of right hand side matrix B.
     *          On exit, if INFO = 0, the N-by-NRHS solution matrix X.
     *
     * @param ldb The leading dimension of the array B. LDB >= max(1,N).
     *
     * @return INFO flag.
     *         = 0:  successful exit
     *         < 0:  if INFO = -i, the i-th argument had an illegal value
     *         > 0:  if INFO = i, the leading minor of order i of A is not
     *               positive definite, so the factorization could not be
     *               completed, and the solution has not been computed.
     */
    int posv(Layout layout, UPLO uplo, int n, int nrhs, double[] A, int lda, double[] B, int ldb);

    /**
     * Solves a real system of linear equations.
     * <pre><code>
     *     A * X = B
     * </code></pre>
     * where A is an N-by-N symmetric positive definite matrix and X and B are N-by-NRHS matrices.
     *
     * The Cholesky decomposition is used to factor A as
     * <pre><code>
     *     A = U**T* U,  if UPLO = 'U'
     * </code></pre>
     * or
     * <pre><code>
     *     A = L * L**T,  if UPLO = 'L'
     * </code></pre>
     * where U is an upper triangular matrix and L is a lower triangular
     * matrix.  The factored form of A is then used to solve the system of
     * equations A * X = B.
     *
     * @param layout matrix layout.
     *
     * @param uplo the upper or lower triangular part of the matrix A is
     *             to be referenced.
     *
     * @param n The number of linear equations, i.e., the order of the matrix A.
     *
     * @param nrhs The number of right hand sides, i.e., the number of columns
     *             of the matrix B.
     *
     * @param A The symmetric matrix of dimension (LDA, N).
     *          On exit, the factor U or L from the Cholesky factorization
     *          A = U**T*U or A = L*L**T.
     *
     * @param lda The leading dimension of the array A. LDA >= max(1,N).
     *
     * @param B On entry, the N-by-NRHS matrix of right hand side matrix B.
     *          On exit, if INFO = 0, the N-by-NRHS solution matrix X.
     *
     * @param ldb The leading dimension of the array B. LDB >= max(1,N).
     *
     * @return INFO flag.
     *         = 0:  successful exit
     *         < 0:  if INFO = -i, the i-th argument had an illegal value
     *         > 0:  if INFO = i, the leading minor of order i of A is not
     *               positive definite, so the factorization could not be
     *               completed, and the solution has not been computed.
     */
    int posv(Layout layout, UPLO uplo, int n, int nrhs, float[] A, int lda, float[] B, int ldb);

    /**
     * Solves a real system of linear equations.
     * <pre><code>
     *     A * X = B
     * </code></pre>
     * where A is an N-by-N symmetric positive definite matrix and X and B are N-by-NRHS matrices.
     *
     * The Cholesky decomposition is used to factor A as
     * <pre><code>
     *     A = U**T* U,  if UPLO = 'U'
     * </code></pre>
     * or
     * <pre><code>
     *     A = L * L**T,  if UPLO = 'L'
     * </code></pre>
     * where U is an upper triangular matrix and L is a lower triangular
     * matrix.  The factored form of A is then used to solve the system of
     * equations A * X = B.
     *
     * @param layout matrix layout.
     *
     * @param uplo the upper or lower triangular part of the matrix A is
     *             to be referenced.
     *
     * @param n The number of linear equations, i.e., the order of the matrix A.
     *
     * @param nrhs The number of right hand sides, i.e., the number of columns
     *             of the matrix B.
     *
     * @param A The symmetric packed matrix.
     *          On exit, the factor U or L from the Cholesky factorization
     *          A = U**T*U or A = L*L**T, in the same storage format as A.
     *
     * @param B On entry, the N-by-NRHS matrix of right hand side matrix B.
     *          On exit, if INFO = 0, the N-by-NRHS solution matrix X.
     *
     * @param ldb The leading dimension of the array B. LDB >= max(1,N).
     *
     * @return INFO flag.
     *         = 0:  successful exit
     *         < 0:  if INFO = -i, the i-th argument had an illegal value
     *         > 0:  if INFO = i, the leading minor of order i of A is not
     *               positive definite, so the factorization could not be
     *               completed, and the solution has not been computed.
     */
    int ppsv(Layout layout, UPLO uplo, int n, int nrhs, double[] A, double[] B, int ldb);

    /**
     * Solves a real system of linear equations.
     * <pre><code>
     *     A * X = B
     * </code></pre>
     * where A is an N-by-N symmetric positive definite matrix and X and B are N-by-NRHS matrices.
     *
     * The Cholesky decomposition is used to factor A as
     * <pre><code>
     *     A = U**T* U,  if UPLO = 'U'
     * </code></pre>
     * or
     * <pre><code>
     *     A = L * L**T,  if UPLO = 'L'
     * </code></pre>
     * where U is an upper triangular matrix and L is a lower triangular
     * matrix.  The factored form of A is then used to solve the system of
     * equations A * X = B.
     *
     * @param layout matrix layout.
     *
     * @param uplo the upper or lower triangular part of the matrix A is
     *             to be referenced.
     *
     * @param n The number of linear equations, i.e., the order of the matrix A.
     *
     * @param nrhs The number of right hand sides, i.e., the number of columns
     *             of the matrix B.
     *
     * @param A The symmetric packed matrix.
     *          On exit, the factor U or L from the Cholesky factorization
     *          A = U**T*U or A = L*L**T, in the same storage format as A.
     *
     * @param B On entry, the N-by-NRHS matrix of right hand side matrix B.
     *          On exit, if INFO = 0, the N-by-NRHS solution matrix X.
     *
     * @param ldb The leading dimension of the array B. LDB >= max(1,N).
     *
     * @return INFO flag.
     *         = 0:  successful exit
     *         < 0:  if INFO = -i, the i-th argument had an illegal value
     *         > 0:  if INFO = i, the leading minor of order i of A is not
     *               positive definite, so the factorization could not be
     *               completed, and the solution has not been computed.
     */
    int ppsv(Layout layout, UPLO uplo, int n, int nrhs, float[] A, float[] B, int ldb);

    /**
     * Solves a real system of linear equations.
     * <pre><code>
     *     A * X = B
     * </code></pre>
     * where A is an N-by-N band matrix and X and B are N-by-NRHS matrices.
     *
     * The LU decomposition with partial pivoting and row interchanges is
     * used to factor A as
     * <pre><code>
     *     A = P * L * U
     * </code></pre>
     * where P is a permutation matrix, L is unit lower triangular, and U is
     * upper triangular. The factored form of A is then used to solve the
     * system of equations A * X = B.
     *
     * @param layout matrix layout.
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
     *          On entry, the matrix A in band storage, in rows KL+1 to
     *          2*KL+KU+1; rows 1 to KL of the array need not be set.
     *          The j-th column of A is stored in the j-th column of the
     *          array AB as follows:
     *          AB(KL+KU+1+i-j,j) = A(i,j) for max(1,j-KU)<=i<=min(N,j+KL)
     *
     *          On exit, details of the factorization: U is stored as an
     *          upper triangular band matrix with KL+KU superdiagonals in
     *          rows 1 to KL+KU+1, and the multipliers used during the
     *          factorization are stored in rows KL+KU+2 to 2*KL+KU+1.
     *
     * @param lda The leading dimension of the array A. LDA >= max(1,N).
     *
     * @param ipiv The pivot indices that define the permutation matrix P;
     *             row i of the matrix was interchanged with row IPIV(i).
     *
     * @param B On entry, the N-by-NRHS matrix of right hand side matrix B.
     *          On exit, if INFO = 0, the N-by-NRHS solution matrix X.
     *
     * @param ldb The leading dimension of the array B. LDB >= max(1,N).
     *
     * @return INFO flag.
     *         = 0:  successful exit
     *         < 0:  if INFO = -i, the i-th argument had an illegal value
     *         > 0:  if INFO = i, U(i,i) is exactly zero. The factorization
     *               has been completed, but the factor U is exactly
     *               singular, so the solution could not be computed.
     */
    int gbsv(Layout layout, int n, int kl, int ku, int nrhs, double[] A, int lda, int[] ipiv, double[] B, int ldb);

    /**
     * Solves a real system of linear equations.
     * <pre><code>
     *     A * X = B
     * </code></pre>
     * where A is an N-by-N band matrix and X and B are N-by-NRHS matrices.
     *
     * The LU decomposition with partial pivoting and row interchanges is
     * used to factor A as
     * <pre><code>
     *     A = P * L * U
     * </code></pre>
     * where P is a permutation matrix, L is unit lower triangular, and U is
     * upper triangular. The factored form of A is then used to solve the
     * system of equations A * X = B.
     *
     * @param layout matrix layout.
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
     *          On entry, the matrix A in band storage, in rows KL+1 to
     *          2*KL+KU+1; rows 1 to KL of the array need not be set.
     *          The j-th column of A is stored in the j-th column of the
     *          array AB as follows:
     *          AB(KL+KU+1+i-j,j) = A(i,j) for max(1,j-KU)<=i<=min(N,j+KL)
     *
     *          On exit, details of the factorization: U is stored as an
     *          upper triangular band matrix with KL+KU superdiagonals in
     *          rows 1 to KL+KU+1, and the multipliers used during the
     *          factorization are stored in rows KL+KU+2 to 2*KL+KU+1.
     *
     * @param lda The leading dimension of the array A. LDA >= max(1,N).
     *
     * @param ipiv The pivot indices that define the permutation matrix P;
     *             row i of the matrix was interchanged with row IPIV(i).
     *
     * @param B On entry, the N-by-NRHS matrix of right hand side matrix B.
     *          On exit, if INFO = 0, the N-by-NRHS solution matrix X.
     *
     * @param ldb The leading dimension of the array B. LDB >= max(1,N).
     *
     * @return INFO flag.
     *         = 0:  successful exit
     *         < 0:  if INFO = -i, the i-th argument had an illegal value
     *         > 0:  if INFO = i, U(i,i) is exactly zero. The factorization
     *               has been completed, but the factor U is exactly
     *               singular, so the solution could not be computed.
     */
    int gbsv(Layout layout, int n, int kl, int ku, int nrhs, float[] A, int lda, int[] ipiv, float[] B, int ldb);

    /**
     * Solves an overdetermined or underdetermined system, using a QR or LQ
     * factorization of A. It is assumed that A has full rank.
     *
     * @param layout matrix layout.
     *
     * @param trans normal or transpose of the matrix A.
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
     * @param lda The leading dimension of the array A. LDA >= max(1,N).
     *
     * @param B On entry, the N-by-NRHS matrix of right hand side matrix B.
     *          On exit, if INFO = 0, the N-by-NRHS solution matrix X.
     *
     * @param ldb The leading dimension of the array B. LDB >= max(1,N).
     *
     * @return INFO flag.
     *         = 0:  successful exit
     *         < 0:  if INFO = -i, the i-th argument had an illegal value
     *         > 0:  if INFO =  i, the i-th diagonal element of the
     *               triangular factor of A is zero, so that A does not have
     *               full rank; the least squares solution could not be
     *               computed.
     */
    int gels(Layout layout, Transpose trans, int m, int n, int nrhs, double[] A, int lda, double[] B, int ldb);

    /**
     * Solves an overdetermined or underdetermined system, using a QR or LQ
     * factorization of A. It is assumed that A has full rank.
     *
     * @param layout matrix layout.
     *
     * @param trans normal or transpose of the matrix A.
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
     * @param lda The leading dimension of the array A. LDA >= max(1,N).
     *
     * @param B On entry, the N-by-NRHS matrix of right hand side matrix B.
     *          On exit, if INFO = 0, the N-by-NRHS solution matrix X.
     *
     * @param ldb The leading dimension of the array B. LDB >= max(1,N).
     *
     * @return INFO flag.
     *         = 0:  successful exit
     *         < 0:  if INFO = -i, the i-th argument had an illegal value
     *         > 0:  if INFO =  i, the i-th diagonal element of the
     *               triangular factor of A is zero, so that A does not have
     *               full rank; the least squares solution could not be
     *               computed.
     */
    int gels(Layout layout, Transpose trans, int m, int n, int nrhs, float[] A, int lda, float[] B, int ldb);

    /**
     * Solves an overdetermined or underdetermined system, using a complete
     * orthogonal factorization of A. A may be rank-deficient.
     *
     * @param layout matrix layout.
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
     * @param lda The leading dimension of the array A. LDA >= max(1,M).
     *
     * @param B The right hand side matrix of dimension (LDB, NRHS).
     *          On exit, if INFO = 0, B is overwritten by the solution
     *          vectors, stored columnwise.
     *
     * @param ldb The leading dimension of the array B. LDB >= max(1,M,N).
     *
     * @param jpvt On entry, if JPVT(i) != 0, the i-th column of A is permuted
     *             to the front of AP, otherwise column i is a free column.
     *             On exit, if JPVT(i) = k, then the i-th column of AP
     *             was the k-th column of A.
     *
     * @param rcond RCOND is used to determine the effective rank of A, which
     *              is defined as the order of the largest leading triangular
     *              submatrix R11 in the QR factorization with pivoting of A,
     *              whose estimated condition number < 1/RCOND.
     *
     * @param rank The effective rank of A, i.e., the order of the submatrix
     *             R11.  This is the same as the order of the submatrix T11
     *             in the complete orthogonal factorization of A.
     *
     * @return INFO flag.
     *         = 0:  successful exit
     *         < 0:  if INFO = -i, the i-th argument had an illegal value
     *         > 0:  if INFO =  i, the i-th diagonal element of the
     *               triangular factor of A is zero, so that A does not have
     *               full rank; the least squares solution could not be
     *               computed.
     */
    int gelsy(Layout layout, int m, int n, int nrhs, double[] A, int lda, double[] B, int ldb, int[] jpvt, double rcond, int[] rank);

    /**
     * Solves an overdetermined or underdetermined system, using a complete
     * orthogonal factorization of A. A may be rank-deficient.
     *
     * @param layout matrix layout.
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
     * @param lda The leading dimension of the array A. LDA >= max(1,M).
     *
     * @param B The right hand side matrix of dimension (LDB, NRHS).
     *          On exit, if INFO = 0, B is overwritten by the solution
     *          vectors, stored columnwise.
     *
     * @param ldb The leading dimension of the array B. LDB >= max(1,M,N).
     *
     * @param jpvt On entry, if JPVT(i) != 0, the i-th column of A is permuted
     *             to the front of AP, otherwise column i is a free column.
     *             On exit, if JPVT(i) = k, then the i-th column of AP
     *             was the k-th column of A.
     *
     * @param rcond RCOND is used to determine the effective rank of A, which
     *              is defined as the order of the largest leading triangular
     *              submatrix R11 in the QR factorization with pivoting of A,
     *              whose estimated condition number < 1/RCOND.
     *
     * @param rank The effective rank of A, i.e., the order of the submatrix
     *             R11.  This is the same as the order of the submatrix T11
     *             in the complete orthogonal factorization of A.
     *
     * @return INFO flag.
     *         = 0:  successful exit
     *         < 0:  if INFO = -i, the i-th argument had an illegal value
     *         > 0:  if INFO =  i, the i-th diagonal element of the
     *               triangular factor of A is zero, so that A does not have
     *               full rank; the least squares solution could not be
     *               computed.
     */
    int gelsy(Layout layout, int m, int n, int nrhs, float[] A, int lda, float[] B, int ldb, int[] jpvt, float rcond, int[] rank);

    /**
     * Solves an overdetermined or underdetermined system, using the singular
     * value decomposition (SVD) of A. A may be rank-deficient.
     *
     * The effective rank of A is determined by treating as zero those
     * singular values which are less than RCOND times the largest singular
     * value.
     *
     * @param layout matrix layout.
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
     * @param lda The leading dimension of the array A. LDA >= max(1,M).
     *
     * @param B The right hand side matrix of dimension (LDB, NRHS).
     *          On exit, if INFO = 0, B is overwritten by the solution
     *          vectors, stored columnwise.
     *
     * @param ldb The leading dimension of the array B. LDB >= max(1,M,N).
     *
     * @param s The singular values of A in decreasing order.
     *          The condition number of A in the 2-norm = S(1)/S(min(m,n)).
     *
     * @param rcond RCOND is used to determine the effective rank of A.
     *              Singular values S(i) <= RCOND*S(1) are treated as zero.
     *              If RCOND < 0, machine precision is used instead.
     *
     * @param rank The effective rank of A, i.e., the number of singular values
     *             which are greater than RCOND*S(1).
     *
     * @return INFO flag.
     *         = 0:  successful exit
     *         < 0:  if INFO = -i, the i-th argument had an illegal value
     *         > 0:  if INFO =  i, the i-th diagonal element of the
     *               triangular factor of A is zero, so that A does not have
     *               full rank; the least squares solution could not be
     *               computed.
     */
    int gelss(Layout layout, int m, int n, int nrhs, double[] A, int lda, double[] B, int ldb, double[] s, double rcond, int[] rank);

    /**
     * Solves an overdetermined or underdetermined system, using the singular
     * value decomposition (SVD) of A. A may be rank-deficient.
     *
     * The effective rank of A is determined by treating as zero those
     * singular values which are less than RCOND times the largest singular
     * value.
     *
     * @param layout matrix layout.
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
     * @param lda The leading dimension of the array A. LDA >= max(1,M).
     *
     * @param B The right hand side matrix of dimension (LDB, NRHS).
     *          On exit, if INFO = 0, B is overwritten by the solution
     *          vectors, stored columnwise.
     *
     * @param ldb The leading dimension of the array B. LDB >= max(1,M,N).
     *
     * @param s The singular values of A in decreasing order.
     *          The condition number of A in the 2-norm = S(1)/S(min(m,n)).
     *
     * @param rcond RCOND is used to determine the effective rank of A.
     *              Singular values S(i) <= RCOND*S(1) are treated as zero.
     *              If RCOND < 0, machine precision is used instead.
     *
     * @param rank The effective rank of A, i.e., the number of singular values
     *             which are greater than RCOND*S(1).
     *
     * @return INFO flag.
     *         = 0:  successful exit
     *         < 0:  if INFO = -i, the i-th argument had an illegal value
     *         > 0:  if INFO =  i, the i-th diagonal element of the
     *               triangular factor of A is zero, so that A does not have
     *               full rank; the least squares solution could not be
     *               computed.
     */
    int gelss(Layout layout, int m, int n, int nrhs, float[] A, int lda, float[] B, int ldb, float[] s, float rcond, int[] rank);

    /**
     * Solves an overdetermined or underdetermined system, using a divide
     * and conquer algorithm with the singular value decomposition (SVD) of A.
     * A may be rank-deficient.
     *
     * The effective rank of A is determined by treating as zero those
     * singular values which are less than RCOND times the largest singular
     * value.
     *
     * @param layout matrix layout.
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
     * @param lda The leading dimension of the array A. LDA >= max(1,M).
     *
     * @param B The right hand side matrix of dimension (LDB, NRHS).
     *          On exit, if INFO = 0, B is overwritten by the solution
     *          vectors, stored columnwise.
     *
     * @param ldb The leading dimension of the array B. LDB >= max(1,M,N).
     *
     * @param s The singular values of A in decreasing order.
     *          The condition number of A in the 2-norm = S(1)/S(min(m,n)).
     *
     * @param rcond RCOND is used to determine the effective rank of A.
     *              Singular values S(i) <= RCOND*S(1) are treated as zero.
     *              If RCOND < 0, machine precision is used instead.
     *
     * @param rank The effective rank of A, i.e., the number of singular values
     *             which are greater than RCOND*S(1).
     *
     * @return INFO flag.
     *         = 0:  successful exit
     *         < 0:  if INFO = -i, the i-th argument had an illegal value
     *         > 0:  if INFO =  i, the i-th diagonal element of the
     *               triangular factor of A is zero, so that A does not have
     *               full rank; the least squares solution could not be
     *               computed.
     */
    int gelsd(Layout layout, int m, int n, int nrhs, double[] A, int lda, double[] B, int ldb, double[] s, double rcond, int[] rank);

    /**
     * Solves an overdetermined or underdetermined system, using a divide
     * and conquer algorithm with the singular value decomposition (SVD) of A.
     * A may be rank-deficient.
     *
     * The effective rank of A is determined by treating as zero those
     * singular values which are less than RCOND times the largest singular
     * value.
     *
     * @param layout matrix layout.
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
     * @param lda The leading dimension of the array A. LDA >= max(1,M).
     *
     * @param B The right hand side matrix of dimension (LDB, NRHS).
     *          On exit, if INFO = 0, B is overwritten by the solution
     *          vectors, stored columnwise.
     *
     * @param ldb The leading dimension of the array B. LDB >= max(1,M,N).
     *
     * @param s The singular values of A in decreasing order.
     *          The condition number of A in the 2-norm = S(1)/S(min(m,n)).
     *
     * @param rcond RCOND is used to determine the effective rank of A.
     *              Singular values S(i) <= RCOND*S(1) are treated as zero.
     *              If RCOND < 0, machine precision is used instead.
     *
     * @param rank The effective rank of A, i.e., the number of singular values
     *             which are greater than RCOND*S(1).
     *
     * @return INFO flag.
     *         = 0:  successful exit
     *         < 0:  if INFO = -i, the i-th argument had an illegal value
     *         > 0:  if INFO =  i, the i-th diagonal element of the
     *               triangular factor of A is zero, so that A does not have
     *               full rank; the least squares solution could not be
     *               computed.
     */
    int gelsd(Layout layout, int m, int n, int nrhs, float[] A, int lda, float[] B, int ldb, float[] s, float rcond, int[] rank);

    /**
     * Solves a linear equality-constrained least squares (LSE) problem.
     * <pre><code>
     *     minimize || c - A*x ||_2   subject to   B*x = d
     * </code></pre>
     *  where A is an M-by-N matrix, B is a P-by-N matrix, c is a given
     *  M-vector, and d is a given P-vector. It is assumed that
     *  P <= N <= M+P, and
     * <pre><code>
     *     rank(B) = P and  rank( (A) ) = N
     *                          ( (B) )
     * </code></pre>
     *
     * These conditions ensure that the LSE problem has a unique solution,
     * which is obtained using a generalized RQ factorization of the
     * matrices (B, A) given by
     * <pre><code>
     *     B = (0 R)*Q,   A = Z*T*Q
     * </code></pre>
     *
     * @param layout matrix layout.
     *
     * @param m The number of rows of the matrix A.
     *
     * @param n The number of columns of the matrix A and B.
     *
     * @param p The number of rows of the matrix B. 0 <= P <= N <= M+P.
     *
     * @param A The matrix of dimension (LDA, N).
     *          On exit, the elements on and above the diagonal of the array
     *          contain the min(M,N)-by-N upper trapezoidal matrix T.
     *
     * @param lda The leading dimension of the array A. LDA >= max(1,M).
     *
     * @param B On entry, the P-by-N matrix B.
     *          On exit, the upper triangle of the subarray B(1:P,N-P+1:N)
     *          contains the P-by-P upper triangular matrix R.
     *
     * @param ldb The leading dimension of the array B. LDB >= max(1,P).
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
     *         = 0:  successful exit
     *         < 0:  if INFO = -i, the i-th argument had an illegal value
     *         = 1:  the upper triangular factor R associated with B in the
     *               generalized RQ factorization of the pair (B, A) is
     *               singular, so that rank(B) < P; the least squares
     *               solution could not be computed.
     *         = 2:  the (N-P) by (N-P) part of the upper trapezoidal factor
     *               T associated with A in the generalized RQ factorization
     *               of the pair (B, A) is singular, so that
     *               rank( A, B ) < N; the least squares solution could not
     *               be computed.
     */
    int gglse(Layout layout, int m, int n, int p, double[] A, int lda, double[] B, int ldb, double[] c, double[] d, double[] x);

    /**
     * Solves a linear equality-constrained least squares (LSE) problem.
     * <pre><code>
     *     minimize || c - A*x ||_2   subject to   B*x = d
     * </code></pre>
     *  where A is an M-by-N matrix, B is a P-by-N matrix, c is a given
     *  M-vector, and d is a given P-vector. It is assumed that
     *  P <= N <= M+P, and
     * <pre><code>
     *     rank(B) = P and  rank( (A) ) = N
     *                          ( (B) )
     * </code></pre>
     *
     * These conditions ensure that the LSE problem has a unique solution,
     * which is obtained using a generalized RQ factorization of the
     * matrices (B, A) given by
     * <pre><code>
     *     B = (0 R)*Q,   A = Z*T*Q
     * </code></pre>
     *
     * @param layout matrix layout.
     *
     * @param m The number of rows of the matrix A.
     *
     * @param n The number of columns of the matrix A and B.
     *
     * @param p The number of rows of the matrix B. 0 <= P <= N <= M+P.
     *
     * @param A The matrix of dimension (LDA, N).
     *          On exit, the elements on and above the diagonal of the array
     *          contain the min(M,N)-by-N upper trapezoidal matrix T.
     *
     * @param lda The leading dimension of the array A. LDA >= max(1,M).
     *
     * @param B On entry, the P-by-N matrix B.
     *          On exit, the upper triangle of the subarray B(1:P,N-P+1:N)
     *          contains the P-by-P upper triangular matrix R.
     *
     * @param ldb The leading dimension of the array B. LDB >= max(1,P).
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
     *         = 0:  successful exit
     *         < 0:  if INFO = -i, the i-th argument had an illegal value
     *         = 1:  the upper triangular factor R associated with B in the
     *               generalized RQ factorization of the pair (B, A) is
     *               singular, so that rank(B) < P; the least squares
     *               solution could not be computed.
     *         = 2:  the (N-P) by (N-P) part of the upper trapezoidal factor
     *               T associated with A in the generalized RQ factorization
     *               of the pair (B, A) is singular, so that
     *               rank( A, B ) < N; the least squares solution could not
     *               be computed.
     */
    int gglse(Layout layout, int m, int n, int p, float[] A, int lda, float[] B, int ldb, float[] c, float[] d, float[] x);

    /**
     * Solves a general Gauss-Markov linear model (GLM) problem.
     * <pre><code>
     *     minimize || y ||_2   subject to   d = A*x + B*y
     *         x
     * </code></pre>
     * where A is an N-by-M matrix, B is an N-by-P matrix, and d is a
     * given N-vector. It is assumed that M <= N <= M+P, and
     * <pre><code>
     *     rank(A) = M    and    rank( A B ) = N
     * </code></pre>
     *
     * Under these assumptions, the constrained equation is always
     * consistent, and there is a unique solution x and a minimal 2-norm
     * solution y, which is obtained using a generalized QR factorization
     * of the matrices (A, B) given by
     * <pre><code>
     *     A = Q*(R),   B = Q*T*Z
     *           (0)
     * </code></pre>
     *
     * In particular, if matrix B is square nonsingular, then the problem
     * GLM is equivalent to the following weighted linear least squares
     * problem
     *
     * <pre><code>
     *     minimize || inv(B)*(d-A*x) ||_2
     *         x
     * </code></pre>
     * where inv(B) denotes the inverse of B.
     *
     * @param layout matrix layout.
     *
     * @param n The number of rows of the matrix A and B.
     *
     * @param m The number of columns of the matrix A. 0 <= M <= N.
     *
     * @param p The number of columns of the matrix B.  P >= N-M.
     *
     * @param A The matrix of dimension (LDA, M).
     *          On exit, the upper triangular part of the array A contains
     *          the M-by-M upper triangular matrix R.
     *
     * @param lda The leading dimension of the array A. LDA >= max(1,N).
     *
     * @param B On entry, the N-by-P matrix B.
     *          On exit, if N <= P, the upper triangle of the subarray
     *          B(1:N,P-N+1:P) contains the N-by-N upper triangular matrix T;
     *          if N > P, the elements on and above the (N-P)th subdiagonal
     *          contain the N-by-P upper trapezoidal matrix T.
     *
     * @param ldb The leading dimension of the array B. LDB >= max(1,N).
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
     *         = 0:  successful exit
     *         < 0:  if INFO = -i, the i-th argument had an illegal value
     *         = 1:  the upper triangular factor R associated with A in the
     *               generalized QR factorization of the pair (A, B) is
     *               singular, so that rank(A) < M; the least squares
     *               solution could not be computed.
     *         = 2:  the bottom (N-M) by (N-M) part of the upper trapezoidal
     *               factor T associated with B in the generalized QR
     *               factorization of the pair (A, B) is singular, so that
     *               rank( A B ) < N; the least squares solution could not
     *               be computed.
     */
    int ggglm(Layout layout, int n, int m, int p, double[] A, int lda, double[] B, int ldb, double[] d, double[] x, double[] y);

    /**
     * Solves a general Gauss-Markov linear model (GLM) problem.
     * <pre><code>
     *     minimize || y ||_2   subject to   d = A*x + B*y
     *         x
     * </code></pre>
     * where A is an N-by-M matrix, B is an N-by-P matrix, and d is a
     * given N-vector. It is assumed that M <= N <= M+P, and
     * <pre><code>
     *     rank(A) = M    and    rank( A B ) = N
     * </code></pre>
     *
     * Under these assumptions, the constrained equation is always
     * consistent, and there is a unique solution x and a minimal 2-norm
     * solution y, which is obtained using a generalized QR factorization
     * of the matrices (A, B) given by
     * <pre><code>
     *     A = Q*(R),   B = Q*T*Z
     *           (0)
     * </code></pre>
     *
     * In particular, if matrix B is square nonsingular, then the problem
     * GLM is equivalent to the following weighted linear least squares
     * problem
     *
     * <pre><code>
     *     minimize || inv(B)*(d-A*x) ||_2
     *         x
     * </code></pre>
     * where inv(B) denotes the inverse of B.
     *
     * @param layout matrix layout.
     *
     * @param n The number of rows of the matrix A and B.
     *
     * @param m The number of columns of the matrix A. 0 <= M <= N.
     *
     * @param p The number of columns of the matrix B.  P >= N-M.
     *
     * @param A The matrix of dimension (LDA, M).
     *          On exit, the upper triangular part of the array A contains
     *          the M-by-M upper triangular matrix R.
     *
     * @param lda The leading dimension of the array A. LDA >= max(1,N).
     *
     * @param B On entry, the N-by-P matrix B.
     *          On exit, if N <= P, the upper triangle of the subarray
     *          B(1:N,P-N+1:P) contains the N-by-N upper triangular matrix T;
     *          if N > P, the elements on and above the (N-P)th subdiagonal
     *          contain the N-by-P upper trapezoidal matrix T.
     *
     * @param ldb The leading dimension of the array B. LDB >= max(1,N).
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
     *         = 0:  successful exit
     *         < 0:  if INFO = -i, the i-th argument had an illegal value
     *         = 1:  the upper triangular factor R associated with A in the
     *               generalized QR factorization of the pair (A, B) is
     *               singular, so that rank(A) < M; the least squares
     *               solution could not be computed.
     *         = 2:  the bottom (N-M) by (N-M) part of the upper trapezoidal
     *               factor T associated with B in the generalized QR
     *               factorization of the pair (A, B) is singular, so that
     *               rank( A B ) < N; the least squares solution could not
     *               be computed.
     */
    int ggglm(Layout layout, int n, int m, int p, float[] A, int lda, float[] B, int ldb, float[] d, float[] x, float[] y);

}
