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

import java.io.Serializable;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import smile.math.MathEx;
import smile.math.blas.*;
import static smile.math.blas.Layout.*;
import static smile.math.blas.UPLO.*;

/**
 * They symmetric matrix in packed storage.
 *
 * @author Haifeng Li
 */
public class FloatSymmMatrix extends SMatrix {
    private static final long serialVersionUID = 2L;
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(FloatSymmMatrix.class);

    /**
     * The packed matrix storage.
     */
    final float[] AP;
    /**
     * The number of rows/columns.
     */
    final int n;
    /**
     * The upper or lower triangle of the symmetric matrix.
     */
    final UPLO uplo;

    /**
     * Constructor.
     * @param uplo the symmetric matrix stores the upper or lower triangle.
     * @param n the dimension of matrix.
     */
    public FloatSymmMatrix(UPLO uplo, int n) {
        if (uplo == null) {
            throw new NullPointerException("UPLO is null");
        }

        this.uplo = uplo;
        this.n = n;
        this.AP = new float[n * (n+1) / 2];
    }

    /**
     * Constructor.
     * @param uplo the symmetric matrix stores the upper or lower triangle.
     * @param AP the symmetric matrix.
     */
    public FloatSymmMatrix(UPLO uplo, float[][] AP) {
        this(uplo, AP.length);

        if (uplo == LOWER) {
            for (int i = 0; i < n; i++) {
                for (int j = 0; j <= i; j++) {
                    this.AP[i + ((2*n-j-1) * j / 2)] = AP[i][j];
                }
            }
        } else {
            for (int i = 0; i < n; i++) {
                for (int j = i; j < n; j++) {
                    this.AP[i + (j * (j+1) / 2)] = AP[i][j];
                }
            }
        }
    }

    @Override
    public FloatSymmMatrix clone() {
        FloatSymmMatrix matrix = new FloatSymmMatrix(uplo, n);
        System.arraycopy(AP, 0, matrix.AP, 0, AP.length);
        return matrix;
    }

    @Override
    public int nrow() {
        return n;
    }

    @Override
    public int ncol() {
        return n;
    }

    @Override
    public long size() {
        return AP.length;
    }

    /**
     * Returns the matrix layout.
     * @return the matrix layout.
     */
    public Layout layout() {
        return COL_MAJOR;
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
        if (!(o instanceof FloatSymmMatrix)) {
            return false;
        }

        return equals((FloatSymmMatrix) o, 1E-7f);
    }

    /**
     * Returns true if two matrices equal in given precision.
     *
     * @param o the other matrix.
     * @param epsilon a number close to zero.
     * @return true if two matrices equal in given precision.
     */
    public boolean equals(FloatSymmMatrix o, float epsilon) {
        if (n != o.n) {
            return false;
        }

        for (int j = 0; j < n; j++) {
            for (int i = 0; i < n; i++) {
                if (!MathEx.isZero(get(i, j) - o.get(i, j), epsilon)) {
                    return false;
                }
            }
        }

        return true;
    }

    @Override
    public float get(int i, int j) {
        if (uplo == LOWER) {
            if (j > i) {
                int tmp = i;
                i = j;
                j = tmp;
            }
            return AP[i + ((2*n-j-1) * j / 2)];
        } else {
            if (i > j) {
                int tmp = i;
                i = j;
                j = tmp;
            }
            return AP[i + (j * (j+1) / 2)];
        }
    }

    @Override
    public FloatSymmMatrix set(int i, int j, float x) {
        if (uplo == LOWER) {
            if (j > i) {
                int tmp = i;
                i = j;
                j = tmp;
            }
            AP[i + ((2*n-j-1) * j / 2)] = x;
        } else {
            if (i > j) {
                int tmp = i;
                i = j;
                j = tmp;
            }
            AP[i + (j * (j+1) / 2)] = x;
        }

        return this;
    }

    @Override
    public void mv(Transpose trans, float alpha, float[] x, float beta, float[] y) {
        BLAS.engine.spmv(layout(), uplo, n, alpha, AP, x, 1, beta, y, 1);
    }

    @Override
    public void mv(float[] work, int inputOffset, int outputOffset) {
        FloatBuffer xb = FloatBuffer.wrap(work, inputOffset, n);
        FloatBuffer yb = FloatBuffer.wrap(work, outputOffset, n);
        BLAS.engine.spmv(layout(), uplo, n, 1.0f, FloatBuffer.wrap(AP), xb, 1, 0.0f, yb, 1);
    }

    @Override
    public void tv(float[] work, int inputOffset, int outputOffset) {
        mv(work, inputOffset, outputOffset);
    }

    /**
     * Bunch-Kaufman decomposition.
     * @return Bunch-Kaufman decomposition.
     */
    public BunchKaufman bk() {
        FloatSymmMatrix lu = clone();
        int[] ipiv = new int[n];
        int info = LAPACK.engine.sptrf(lu.layout(), lu.uplo, lu.n, lu.AP, ipiv);
        if (info < 0) {
            logger.error("LAPACK SPTRF error code: {}", info);
            throw new ArithmeticException("LAPACK SPTRF error code: " + info);
        }

        return new BunchKaufman(lu, ipiv, info);
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

        FloatSymmMatrix lu = clone();
        int info = LAPACK.engine.pptrf(lu.layout(), lu.uplo, lu.n, lu.AP);
        if (info != 0) {
            logger.error("LAPACK PPTRF error code: {}", info);
            throw new ArithmeticException("LAPACK PPTRF error code: " + info);
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
    public static class BunchKaufman implements Serializable {
        private static final long serialVersionUID = 2L;
        /**
         * The Bunch–Kaufman decomposition.
         */
        public final FloatSymmMatrix lu;

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
        public BunchKaufman(FloatSymmMatrix lu, int[] ipiv, int info) {
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
            int n = lu.n;
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
            if (B.m != lu.n) {
                throw new IllegalArgumentException(String.format("Row dimensions do not agree: A is %d x %d, but B is %d x %d", lu.n, lu.n, B.m, B.n));
            }

            if (lu.layout() != B.layout()) {
                throw new IllegalArgumentException("The matrix layout is inconsistent.");
            }

            if (info > 0) {
                throw new RuntimeException("The matrix is singular.");
            }

            int ret = LAPACK.engine.sptrs(lu.layout(), lu.uplo, lu.n, B.n, FloatBuffer.wrap(lu.AP), IntBuffer.wrap(ipiv), B.A, B.ld);
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
        public final FloatSymmMatrix lu;

        /**
         * Constructor.
         * @param lu the lower/upper triangular part of matrix contains the Cholesky
         *           factorization.
         */
        public Cholesky(FloatSymmMatrix lu) {
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
            if (B.m != lu.n) {
                throw new IllegalArgumentException(String.format("Row dimensions do not agree: A is %d x %d, but B is %d x %d", lu.n, lu.n, B.m, B.n));
            }

            int info = LAPACK.engine.pptrs(lu.layout(), lu.uplo, lu.n, B.n, FloatBuffer.wrap(lu.AP), B.A, B.ld);
            if (info != 0) {
                logger.error("LAPACK POTRS error code: {}", info);
                throw new ArithmeticException("LAPACK POTRS error code: " + info);
            }
        }
    }
}
