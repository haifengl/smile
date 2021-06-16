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

package smile.math.matrix.fp32;

import java.io.Serializable;
import java.nio.FloatBuffer;
import smile.math.MathEx;
import smile.math.blas.*;
import static smile.math.blas.Layout.*;
import static smile.math.blas.Transpose.*;
import static smile.math.blas.UPLO.*;

/**
 * A band matrix is a sparse matrix, whose non-zero entries are confined to
 * a diagonal band, comprising the main diagonal and zero or more diagonals
 * on either side.
 * <p>
 * In numerical analysis, matrices from finite element or finite difference
 * problems are often banded. Such matrices can be viewed as descriptions
 * of the coupling between the problem variables; the bandedness corresponds
 * to the fact that variables are not coupled over arbitrarily large distances.
 * Such matrices can be further divided - for instance, banded matrices exist
 * where every element in the band is nonzero. These often arise when
 * discretizing one-dimensional problems. Problems in higher dimensions also
 * lead to banded matrices, in which case the band itself also tends to be sparse.
 * For instance, a partial differential equation on a square domain (using
 * central differences) will yield a matrix with a half-bandwidth equal to the
 * square root of the matrix dimension, but inside the band only 5 diagonals are
 * nonzero. Unfortunately, applying Gaussian elimination (or equivalently an
 * LU decomposition) to such a matrix results in the band being filled in by
 * many non-zero elements. As sparse matrices lend themselves to more efficient
 * computation than dense matrices, there has been much research focused on
 * finding ways to minimize the bandwidth (or directly minimize the fill in)
 * by applying permutations to the matrix, or other such equivalence or
 * similarity transformations.
 * <p>
 * From a computational point of view, working with band matrices is always
 * preferential to working with similarly dimensioned dense square matrices.
 * A band matrix can be likened in complexity to a rectangular matrix whose
 * row dimension is equal to the bandwidth of the band matrix. Thus the work
 * involved in performing operations such as multiplication falls significantly,
 * often leading to huge savings in terms of calculation time and complexity.
 * <p>
 * Given a n-by-n band matrix with m<sub>1</sub> rows below the diagonal
 * and m<sub>2</sub> rows above. The matrix is compactly stored in an array
 * A[0,n-1][0,m<sub>1</sub>+m<sub>2</sub>]. The diagonal elements are in
 * A[0,n-1][m<sub>1</sub>]. The subdiagonal elements are in A[j,n-1][0,m<sub>1</sub>-1]
 * with {@code j > 0} appropriate to the number of elements on each subdiagonal.
 * The superdiagonal elements are in A[0,j][m<sub>1</sub>+1,m<sub>2</sub>+m<sub>2</sub>]
 * with {@code j < n-1} appropriate to the number of elements on each superdiagonal.
 * 
 * @author Haifeng Li
 */
public class BandMatrix extends IMatrix {
    private static final long serialVersionUID = 2L;
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(BandMatrix.class);

    /**
     * The band matrix storage.
     */
    final float[] AB;
    /**
     * The number of rows.
     */
    final int m;
    /**
     * The number of columns.
     */
    final int n;
    /**
     * The number of subdiagonal rows.
     */
    final int kl;
    /**
     * The number of superdiagonal rows.
     */
    final int ku;
    /**
     * The leading dimension.
     */
    final int ld;
    /**
     * The upper or lower triangle of the symmetric band matrix.
     */
    UPLO uplo = null;

    /**
     * Constructor.
     * @param m the number of rows.
     * @param n the number of columns.
     * @param kl the number of subdiagonals.
     * @param ku the number of superdiagonals.
     */
    public BandMatrix(int m, int n, int kl, int ku) {
        if (m <= 0 || n <= 0) {
            throw new IllegalArgumentException(String.format("Invalid matrix size: %d x %d", m, n));
        }

        if (kl < 0 || ku < 0) {
            throw new IllegalArgumentException(String.format("Invalid subdiagonals or superdiagonals: kl = %d, ku = %d", kl, ku));
        }

        if (kl >= m) {
            throw new IllegalArgumentException(String.format("Invalid subdiagonals %d >= %d", kl, m));
        }

        if (ku >= n) {
            throw new IllegalArgumentException(String.format("Invalid superdiagonals %d >= %d", ku, n));
        }

        this.m = m;
        this.n = n;
        this.kl = kl;
        this.ku = ku;
        this.ld = kl + ku + 1;
        this.AB = new float[ld * n];
    }

    /**
     * Constructor.
     * @param m the number of rows.
     * @param n the number of columns.
     * @param kl the number of subdiagonals.
     * @param ku the number of superdiagonals.
     * @param AB the band matrix. A[i,j] is stored in {@code AB[ku+i-j, j]}
     *           for {@code max(0, j-ku) <= i <= min(m-1, j+kl)}.
     */
    public BandMatrix(int m, int n, int kl, int ku, float[][] AB) {
        this(m, n, kl, ku);

        for (int j = 0; j < n; j++) {
            for (int i = 0; i < ld; i++) {
                this.AB[j * ld + i] = AB[i][j];
            }
        }
    }

    @Override
    public BandMatrix clone() {
        BandMatrix matrix = new BandMatrix(m, n, kl, ku);
        System.arraycopy(AB, 0, matrix.AB, 0, AB.length);

        if (m == n && kl == ku) {
            matrix.uplo(uplo);
        }

        return matrix;
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
        return AB.length;
    }

    /**
     * Returns the number of subdiagonals.
     * @return the number of subdiagonals.
     */
    public int kl() {
        return kl;
    }

    /**
     * Returns the number of superdiagonals.
     * @return the number of superdiagonals.
     */
    public int ku() {
        return ku;
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
     * Return true if the matrix is symmetric (uplo != null).
     * @return true if the matrix is symmetric (uplo != null).
     */
    public boolean isSymmetric() {
        return uplo != null;
    }

    /**
     * Sets the format of symmetric band matrix.
     * @param uplo the format of symmetric band matrix.
     * @return this matrix.
     */
    public BandMatrix uplo(UPLO uplo) {
        if (m != n) {
            throw new IllegalArgumentException(String.format("The matrix is not square: %d x %d", m, n));
        }

        if (kl != ku) {
            throw new IllegalArgumentException(String.format("kl != ku: %d != %d", kl, ku));
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

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof BandMatrix)) {
            return false;
        }

        return equals((BandMatrix) o, 1E-7f);
    }

    /**
     * Returns true if two matrices equal in given precision.
     *
     * @param o the other matrix.
     * @param epsilon a number close to zero.
     * @return true if two matrices equal in given precision.
     */
    public boolean equals(BandMatrix o, float epsilon) {
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

    @Override
    public float get(int i, int j) {
        if (Math.max(0, j-ku) <= i && i <= Math.min(m-1, j+kl)) {
            return AB[j * ld + ku + i - j];
        } else {
            return 0.0f;
        }
    }

    @Override
    public void set(int i, int j, float x) {
        if (Math.max(0, j-ku) <= i && i <= Math.min(m-1, j+kl)) {
            AB[j * ld + ku + i - j] = x;
        } else {
            throw new UnsupportedOperationException(String.format("Set element at (%d, %d)", i, j));
        }
    }

    @Override
    public void mv(Transpose trans, float alpha, float[] x, float beta, float[] y) {
        if (uplo != null) {
            BLAS.engine.sbmv(layout(), uplo, n, kl, alpha, AB, ld, x, 1, beta, y, 1);
        } else {
            BLAS.engine.gbmv(layout(), trans, m, n, kl, ku, alpha, AB, ld, x, 1, beta, y, 1);
        }
    }

    @Override
    public void mv(float[] work, int inputOffset, int outputOffset) {
        FloatBuffer xb = FloatBuffer.wrap(work, inputOffset, n);
        FloatBuffer yb = FloatBuffer.wrap(work, outputOffset, m);
        if (uplo != null) {
            BLAS.engine.sbmv(layout(), uplo, n, kl, 1.0f, FloatBuffer.wrap(AB), ld, xb, 1, 0.0f, yb, 1);
        } else {
            BLAS.engine.gbmv(layout(), NO_TRANSPOSE, m, n, kl, ku, 1.0f, FloatBuffer.wrap(AB), ld, xb, 1, 0.0f, yb, 1);
        }
    }

    @Override
    public void tv(float[] work, int inputOffset, int outputOffset) {
        FloatBuffer xb = FloatBuffer.wrap(work, inputOffset, m);
        FloatBuffer yb = FloatBuffer.wrap(work, outputOffset, n);
        if (uplo != null) {
            BLAS.engine.sbmv(layout(), uplo, n, kl, 1.0f, FloatBuffer.wrap(AB), ld, xb, 1, 0.0f, yb, 1);
        } else {
            BLAS.engine.gbmv(layout(), TRANSPOSE, m, n, kl, ku, 1.0f, FloatBuffer.wrap(AB), ld, xb, 1, 0.0f, yb, 1);
        }
    }

    /**
     * LU decomposition.
     * @return LU decomposition.
     */
    public LU lu() {
        BandMatrix lu = new BandMatrix(m, n, 2*kl, ku);
        for (int j = 0; j < n; j++) {
            for (int i = 0; i < ld; i++) {
                lu.AB[j * lu.ld + kl + i] = AB[j * ld + i];
            }
        }
        int[] ipiv = new int[n];
        int info = LAPACK.engine.gbtrf(lu.layout(), lu.m, lu.n, lu.kl/2, lu.ku, lu.AB, lu.ld, ipiv);
        if (info < 0) {
            logger.error("LAPACK GBTRF error code: {}", info);
            throw new ArithmeticException("LAPACK GBTRF error code: " + info);
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
        if (uplo == null) {
            throw new IllegalArgumentException("The matrix is not symmetric");
        }

        BandMatrix lu = new BandMatrix(m, n, uplo == LOWER ? kl : 0, uplo == LOWER ? 0 : ku);
        lu.uplo = uplo;
        if (uplo == UPLO.LOWER) {
            for (int j = 0; j < n; j++) {
                for (int i = 0; i <= kl; i++) {
                    lu.AB[j * lu.ld + i] = get(j + i, j);
                }
            }
        } else {
            for (int j = 0; j < n; j++) {
                for (int i = 0; i <= ku; i++) {
                    lu.AB[j * lu.ld + ku - i] = get(j - i, j);
                }
            }
        }

        int info = LAPACK.engine.pbtrf(lu.layout(), lu.uplo, lu.n, lu.uplo == LOWER ? lu.kl : lu.ku, lu.AB, lu.ld);
        if (info != 0) {
            logger.error("LAPACK PBTRF error code: {}", info);
            throw new ArithmeticException("LAPACK PBTRF error code: " + info);
        }

        return new Cholesky(lu);
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
        public final BandMatrix lu;

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
        public LU(BandMatrix lu, int[] ipiv, int info) {
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
        public float det() {
            int m = lu.m;
            int n = lu.n;

            if (m != n) {
                throw new IllegalArgumentException(String.format("The matrix is not square: %d x %d", m, n));
            }

            double d = 1.0;
            for (int j = 0; j < n; j++) {
                d *= lu.AB[j * lu.ld + lu.kl/2 + lu.ku];
            }

            for (int j = 0; j < n; j++){
                if (j+1 != ipiv[j]) {
                    d = -d;
                }
            }

            return (float) d;
        }

        /**
         * Returns the inverse of matrix.
         * @return the inverse of matrix.
         */
        public Matrix inverse() {
            Matrix inv = Matrix.eye(lu.n);
            solve(inv);
            return inv;
        }

        /**
         * Solve {@code A * x = b}.
         * @param b the right hand side of linear system.
         * @throws RuntimeException when the matrix is singular.
         * @return the solution vector.
         */
        public float[] solve(float[] b) {
            Matrix x = Matrix.column(b);
            solve(x);
            return x.A;
        }

        /**
         * Solve {@code A * X = B}. B will be overwritten with the solution matrix on output.
         * @param B the right hand side of linear system.
         *          On output, B will be overwritten with the solution matrix.
         * @throws RuntimeException when the matrix is singular.
         */
        public void solve(Matrix B) {
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

            int ret = LAPACK.engine.gbtrs(lu.layout(), NO_TRANSPOSE, lu.n, lu.kl/2, lu.ku, B.n, lu.AB, lu.ld, ipiv, B.A, B.ld);
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
        public final BandMatrix lu;

        /**
         * Constructor.
         * @param lu the lower/upper triangular part of matrix contains the Cholesky
         *           factorization.
         */
        public Cholesky(BandMatrix lu) {
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
        public Matrix inverse() {
            Matrix inv = Matrix.eye(lu.n);
            solve(inv);
            return inv;
        }

        /**
         * Solves the linear system {@code A * x = b}.
         * @param b the right hand side of linear systems.
         * @return the solution vector.
         */
        public float[] solve(float[] b) {
            Matrix x = Matrix.column(b);
            solve(x);
            return x.A;
        }

        /**
         * Solves the linear system {@code A * X = B}.
         * @param B the right hand side of linear systems. On output, B will
         *          be overwritten with the solution matrix.
         */
        public void solve(Matrix B) {
            if (B.m != lu.m) {
                throw new IllegalArgumentException(String.format("Row dimensions do not agree: A is %d x %d, but B is %d x %d", lu.m, lu.n, B.m, B.n));
            }

            int info = LAPACK.engine.pbtrs(lu.layout(), lu.uplo, lu.n, lu.uplo == LOWER ? lu.kl : lu.ku, B.n, lu.AB, lu.ld, B.A, B.ld);
            if (info != 0) {
                logger.error("LAPACK POTRS error code: {}", info);
                throw new ArithmeticException("LAPACK POTRS error code: " + info);
            }
        }
    }
}
