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

import java.io.Serializable;
import java.util.Arrays;
import smile.linalg.LAPACK;
import static smile.linalg.Diag.NON_UNIT;
import static smile.linalg.Side.LEFT;
import static smile.linalg.Transpose.NO_TRANSPOSE;
import static smile.linalg.Transpose.TRANSPOSE;
import static smile.linalg.UPLO.LOWER;
import static smile.linalg.UPLO.UPPER;

/**
 * The QR decomposition. For an m-by-n matrix A with {@code m >= n},
 * the QR decomposition is an m-by-n orthogonal matrix Q and
 * an n-by-n upper triangular matrix R such that A = Q*R.
 * <p>
 * The QR decomposition always exists, even if the matrix does not have
 * full rank. The primary use of the QR decomposition is in the least squares
 * solution of non-square systems of simultaneous linear equations.
 *
 * @param qr the QR decomposition.
 * @param tau the scalar factors of the elementary reflectors.
 *
 * @author Haifeng Li
 */
public record QR(DenseMatrix qr, Vector tau) implements Serializable {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(QR.class);

    /**
     * Returns the Cholesky decomposition of A'A.
     * @return the Cholesky decomposition of A'A.
     */
    public Cholesky toCholesky() {
        int n = qr.n;
        DenseMatrix L = qr.zeros(n, n);
        for (int i = 0; i < n; i++) {
            for (int j = 0; j <= i; j++) {
                L.set(i, j, qr.get(j, i));
            }
        }

        L.withUplo(LOWER);
        return new Cholesky(L);
    }

    /**
     * Returns the upper triangular factor.
     * @return the upper triangular factor.
     */
    public DenseMatrix R() {
        int n = qr.n;
        DenseMatrix R = tau.diagflat();
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
    public DenseMatrix Q() {
        int m = qr.m;
        int n = qr.n;
        int k = Math.min(m, n);
        DenseMatrix Q = qr.copy();
        int info = LAPACK.orgqr(qr.layout(), m, n, k, Q.memory, qr.ld, tau.memory);
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

        Vector x = Vector.column(b);
        solve(x);
        return Arrays.copyOfRange(b, 0, qr.n);
    }

    /**
     * Solves the least squares min || B - A*X ||.
     * @param B the right hand side of overdetermined linear system.
     *          B will be overwritten with the solution matrix on output.
     * @throws RuntimeException when the matrix is rank deficient.
     */
    public void solve(DenseMatrix B) {
        if (qr.scalarType() != B.scalarType()) {
            throw new IllegalArgumentException("Incompatible ScalarType: " + B.scalarType() + " != " + qr.scalarType());
        }
        if (qr.m != B.m) {
            throw new IllegalArgumentException(String.format("Row dimensions do not agree: A is %d x %d, but B is %d x %d", qr.nrow(), qr.nrow(), B.nrow(), B.ncol()));
        }

        int m = qr.m;
        int n = qr.n;
        int k = Math.min(m, n);

        int info = LAPACK.ormqr(qr.layout(), LEFT, TRANSPOSE, B.nrow(), B.ncol(), k, qr.memory, qr.ld, tau.memory, B.memory, B.ld);
        if (info != 0) {
            logger.error("LAPACK ORMQR error code: {}", info);
            throw new IllegalArgumentException("LAPACK ORMQR error code: " + info);
        }

        info = LAPACK.trtrs(qr.layout(), UPPER, NO_TRANSPOSE, NON_UNIT, qr.n, B.n, qr.memory, qr.ld, B.memory, B.ld);

        if (info != 0) {
            logger.error("LAPACK TRTRS error code: {}", info);
            throw new IllegalArgumentException("LAPACK TRTRS error code: " + info);
        }
    }
}
