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

import smile.linalg.UPLO;
import smile.math.Complex;
import smile.math.MathEx;
import static smile.linalg.UPLO.*;
import static smile.tensor.ScalarType.*;

/**
 * Pure-Java LAPACK decompositions and linear system solvers operating on
 * {@link DenseMatrix}, {@link SymmMatrix}, and {@link BandMatrix} with
 * LAPACK-compatible factor storage.
 *
 * @author Haifeng Li
 */
final class JLapack {
    /** Utility class; not instantiable. */
    private JLapack() {}

    // =========================================================================
    // 1. LU Decomposition (getrf, getrs)
    // =========================================================================

    /**
     * Computes the LU factorization of a general m-by-n matrix using partial
     * pivoting with row interchanges.
     *
     * @param lu   on entry, the matrix to be factored; on exit, the factors L and U.
     * @param ipiv on exit, 1-based pivot indices ({@code 1 <= ipiv[j] <= m}).
     * @return {@code 0} if successful, or {@code j + 1 > 0} if {@code U(j, j)} is zero.
     */
    static int getrf(DenseMatrix lu, int[] ipiv) {
        int m = lu.nrow();
        int n = lu.ncol();
        int minMN = Math.min(m, n);
        int info = 0;

        for (int j = 0; j < minMN; j++) {
            // Find pivot and exchange if necessary.
            int p = j;
            double max = Math.abs(lu.get(j, j));
            for (int i = j + 1; i < m; i++) {
                double a = Math.abs(lu.get(i, j));
                if (a > max) {
                    max = a;
                    p = i;
                }
            }
            ipiv[j] = p + 1; // 1-based pivot index for LAPACK compatibility

            if (lu.get(p, j) != 0.0) {
                if (p != j) {
                    for (int k = 0; k < n; k++) {
                        double t = lu.get(p, k);
                        lu.set(p, k, lu.get(j, k));
                        lu.set(j, k, t);
                    }
                }

                double pivot = lu.get(j, j);
                for (int i = j + 1; i < m; i++) {
                    lu.div(i, j, pivot);
                }
            } else if (info == 0) {
                info = j + 1;
            }

            // Update trailing submatrix.
            for (int k = j + 1; k < n; k++) {
                double lujk = lu.get(j, k);
                if (lujk != 0.0) {
                    for (int i = j + 1; i < m; i++) {
                        lu.sub(i, k, lu.get(i, j) * lujk);
                    }
                }
            }
        }

        return info;
    }

    /**
     * Solves a system of linear equations {@code A * X = B} with a general
     * n-by-n matrix {@code A} using the LU factorization computed by {@link #getrf}.
     *
     * @param lu   the LU factors from {@link #getrf}.
     * @param ipiv the 1-based pivot indices from {@link #getrf}.
     * @param B    on entry, the right-hand side matrix; on exit, the solution matrix.
     */
    static void getrs(DenseMatrix lu, int[] ipiv, DenseMatrix B) {
        int n = lu.ncol();
        int nrhs = B.ncol();

        // Apply row interchanges to the right-hand sides.
        for (int i = 0; i < n; i++) {
            int p = ipiv[i] - 1;
            if (p != i) {
                for (int j = 0; j < nrhs; j++) {
                    double t = B.get(i, j);
                    B.set(i, j, B.get(p, j));
                    B.set(p, j, t);
                }
            }
        }

        // Solve L * Y = P * B, overwriting B with Y.
        for (int j = 0; j < nrhs; j++) {
            for (int k = 0; k < n; k++) {
                double bkj = B.get(k, j);
                if (bkj != 0.0) {
                    for (int i = k + 1; i < n; i++) {
                        B.sub(i, j, bkj * lu.get(i, k));
                    }
                }
            }
        }

        // Solve U * X = Y, overwriting B with X.
        for (int j = 0; j < nrhs; j++) {
            for (int k = n - 1; k >= 0; k--) {
                B.div(k, j, lu.get(k, k));
                double bkj = B.get(k, j);
                if (bkj != 0.0) {
                    for (int i = 0; i < k; i++) {
                        B.sub(i, j, bkj * lu.get(i, k));
                    }
                }
            }
        }
    }

    // =========================================================================
    // 2. Cholesky Decomposition (potrf, potrs)
    // =========================================================================

    /**
     * Computes the Cholesky factorization of a real symmetric positive definite
     * matrix {@code A = L * L^T} (when {@code uplo == LOWER}) or
     * {@code A = U^T * U} (when {@code uplo == UPPER}).
     *
     * @param lu the symmetric matrix to factor in-place.
     * @throws ArithmeticException if the matrix is not positive definite.
     */
    static void potrf(DenseMatrix lu) {
        if (lu.nrow() != lu.ncol()) {
            throw new UnsupportedOperationException("Cholesky decomposition on non-square matrix");
        }

        int n = lu.nrow();
        if (lu.uplo() == UPPER) {
            for (int j = 0; j < n; j++) {
                double d = 0.0;
                for (int k = 0; k < j; k++) {
                    double s = 0.0;
                    for (int i = 0; i < k; i++) {
                        s += lu.get(i, k) * lu.get(i, j);
                    }
                    s = (lu.get(k, j) - s) / lu.get(k, k);
                    lu.set(k, j, s);
                    d += s * s;
                }
                d = lu.get(j, j) - d;
                if (d <= 0.0 || Double.isNaN(d)) {
                    throw new ArithmeticException("The matrix is not positive definite.");
                }
                lu.set(j, j, Math.sqrt(d));
            }
        } else {
            for (int j = 0; j < n; j++) {
                double d = 0.0;
                for (int k = 0; k < j; k++) {
                    double s = 0.0;
                    for (int i = 0; i < k; i++) {
                        s += lu.get(k, i) * lu.get(j, i);
                    }
                    s = (lu.get(j, k) - s) / lu.get(k, k);
                    lu.set(j, k, s);
                    d += s * s;
                }
                d = lu.get(j, j) - d;
                if (d <= 0.0 || Double.isNaN(d)) {
                    throw new ArithmeticException("The matrix is not positive definite.");
                }
                lu.set(j, j, Math.sqrt(d));
            }
        }
    }

    /**
     * Solves a system of linear equations {@code A * X = B} with a symmetric
     * positive definite matrix {@code A} using the Cholesky factorization
     * computed by {@link #potrf}.
     *
     * @param lu the Cholesky factor from {@link #potrf}.
     * @param B  on entry, the right-hand side matrix; on exit, the solution matrix.
     */
    static void potrs(DenseMatrix lu, DenseMatrix B) {
        int n = lu.nrow();
        int nrhs = B.ncol();

        if (lu.uplo() == UPPER) {
            for (int j = 0; j < nrhs; j++) {
                // Solve U^T * Y = B
                for (int k = 0; k < n; k++) {
                    double s = B.get(k, j);
                    for (int i = 0; i < k; i++) {
                        s -= lu.get(i, k) * B.get(i, j);
                    }
                    B.set(k, j, s / lu.get(k, k));
                }
                // Solve U * X = Y
                for (int k = n - 1; k >= 0; k--) {
                    double s = B.get(k, j);
                    for (int i = k + 1; i < n; i++) {
                        s -= lu.get(k, i) * B.get(i, j);
                    }
                    B.set(k, j, s / lu.get(k, k));
                }
            }
        } else {
            for (int j = 0; j < nrhs; j++) {
                // Solve L * Y = B
                for (int k = 0; k < n; k++) {
                    double s = B.get(k, j);
                    for (int i = 0; i < k; i++) {
                        s -= lu.get(k, i) * B.get(i, j);
                    }
                    B.set(k, j, s / lu.get(k, k));
                }
                // Solve L^T * X = Y
                for (int k = n - 1; k >= 0; k--) {
                    double s = B.get(k, j);
                    for (int i = k + 1; i < n; i++) {
                        s -= lu.get(i, k) * B.get(i, j);
                    }
                    B.set(k, j, s / lu.get(k, k));
                }
            }
        }
    }

    // =========================================================================
    // 3. QR Decomposition (geqrf, orgqr, qrSolve)
    // =========================================================================

    /**
     * Computes a QR factorization of a real m-by-n matrix {@code A = Q * R}
     * using LAPACK's {@code dgeqrf} Householder reflector representation:
     * <ul>
     *   <li>The upper triangle of {@code qr} (including the diagonal) stores {@code R}.</li>
     *   <li>The strictly lower triangle of {@code qr} stores the subdiagonal of
     *       reflector {@code v_k} with implicit unit diagonal {@code v_k(k) = 1}.</li>
     *   <li>{@code tau[k]} stores the scalar factor of {@code H_k = I - tau[k] * v_k * v_k^T}.</li>
     * </ul>
     *
     * @param qr  the matrix to factor in-place.
     * @param tau on exit, the scalar factors of the elementary reflectors.
     */
    static void geqrf(DenseMatrix qr, Vector tau) {
        int m = qr.nrow();
        int n = qr.ncol();
        int kmax = Math.min(m, n);

        for (int k = 0; k < kmax; k++) {
            double xk = qr.get(k, k);
            double tailNorm = 0.0;
            for (int i = k + 1; i < m; i++) {
                tailNorm = Math.hypot(tailNorm, qr.get(i, k));
            }

            if (tailNorm == 0.0) {
                tau.set(k, 0.0);
            } else {
                double norm = Math.hypot(xk, tailNorm);
                double alpha = -Math.copySign(norm, xk);
                double beta = xk - alpha;
                double tauk = -beta / alpha;

                for (int i = k + 1; i < m; i++) {
                    qr.div(i, k, beta);
                }
                qr.set(k, k, alpha);
                tau.set(k, tauk);

                // Apply H_k = I - tau_k * v_k * v_k^T to trailing columns j = k + 1 .. n - 1.
                for (int j = k + 1; j < n; j++) {
                    double s = qr.get(k, j);
                    for (int i = k + 1; i < m; i++) {
                        s += qr.get(i, k) * qr.get(i, j);
                    }
                    s *= -tauk;
                    qr.add(k, j, s);
                    for (int i = k + 1; i < m; i++) {
                        qr.add(i, j, s * qr.get(i, k));
                    }
                }
            }
        }
    }

    /**
     * Generates the m-by-n orthogonal matrix {@code Q = H_0 * H_1 * ... * H_{k-1}}
     * in-place from the elementary reflectors computed by {@link #geqrf}.
     *
     * @param Q   on entry, the {@code qr} matrix from {@link #geqrf}; on exit, the orthogonal matrix {@code Q}.
     * @param tau the scalar factors of the elementary reflectors.
     */
    static void orgqr(DenseMatrix Q, Vector tau) {
        int m = Q.nrow();
        int n = Q.ncol();
        int k = Math.min(m, n);

        for (int j = k; j < n; j++) {
            for (int i = 0; i < m; i++) {
                Q.set(i, j, i == j ? 1.0 : 0.0);
            }
        }

        for (int l = k - 1; l >= 0; l--) {
            double taul = tau.get(l);
            for (int j = l + 1; j < n; j++) {
                Q.set(l, j, 0.0);
            }

            if (taul != 0.0) {
                for (int j = l + 1; j < n; j++) {
                    double s = 0.0;
                    for (int i = l + 1; i < m; i++) {
                        s += Q.get(i, l) * Q.get(i, j);
                    }
                    s *= -taul;
                    Q.set(l, j, s);
                    for (int i = l + 1; i < m; i++) {
                        Q.add(i, j, s * Q.get(i, l));
                    }
                }
            }

            for (int i = l + 1; i < m; i++) {
                Q.mul(i, l, -taul);
            }
            Q.set(l, l, 1.0 - taul);
            for (int i = 0; i < l; i++) {
                Q.set(i, l, 0.0);
            }
        }
    }

    /**
     * Solves the least squares problem {@code min || B - A * X ||_2} in-place
     * using the QR factorization computed by {@link #geqrf}.
     *
     * @param qr  the QR factors from {@link #geqrf}.
     * @param tau the reflector scalar factors from {@link #geqrf}.
     * @param B   on entry, the m-by-nrhs right-hand side matrix; on exit, the first n rows contain {@code X}.
     */
    static void qrSolve(DenseMatrix qr, Vector tau, DenseMatrix B) {
        int m = qr.nrow();
        int n = qr.ncol();
        int nrhs = B.ncol();
        if (m < n) {
            throw new RuntimeException("Matrix is rank deficient.");
        }

        for (int k = 0; k < n; k++) {
            if (qr.get(k, k) == 0.0) {
                throw new RuntimeException("Matrix is rank deficient.");
            }
        }

        // Compute Y = Q^T * B = H_{n-1} ... H_0 * B in-place on B.
        for (int l = 0; l < n; l++) {
            double taul = tau.get(l);
            if (taul != 0.0) {
                for (int j = 0; j < nrhs; j++) {
                    double s = B.get(l, j);
                    for (int i = l + 1; i < m; i++) {
                        s += qr.get(i, l) * B.get(i, j);
                    }
                    s *= -taul;
                    B.add(l, j, s);
                    for (int i = l + 1; i < m; i++) {
                        B.add(i, j, s * qr.get(i, l));
                    }
                }
            }
        }

        // Solve R * X = Y(0:n-1, :) via back substitution.
        for (int j = 0; j < nrhs; j++) {
            for (int k = n - 1; k >= 0; k--) {
                B.div(k, j, qr.get(k, k));
                double bkj = B.get(k, j);
                if (bkj != 0.0) {
                    for (int i = 0; i < k; i++) {
                        B.sub(i, j, bkj * qr.get(i, k));
                    }
                }
            }
        }
    }

    // =========================================================================
    // 4. Singular Value Decomposition (gesdd)
    // =========================================================================

    /**
     * Mirror the active triangle of a symmetric matrix into the opposite triangle.
     */
    private static void mirrorSymmetric(DenseMatrix A) {
        if (A.uplo() == LOWER) {
            int n = A.nrow();
            for (int i = 0; i < n; i++) {
                for (int j = 0; j < i; j++) {
                    A.set(j, i, A.get(i, j));
                }
            }
        } else if (A.uplo() == UPPER) {
            int n = A.nrow();
            for (int i = 0; i < n; i++) {
                for (int j = i + 1; j < n; j++) {
                    A.set(j, i, A.get(i, j));
                }
            }
        }
    }

    /**
     * Converts a {@link DenseMatrix} to a double-precision {@link DenseMatrix}.
     */
    private static DenseMatrix toFloat64(DenseMatrix A) {
        int m = A.nrow();
        int n = A.ncol();
        DenseMatrix B = DenseMatrix.zeros(Float64, m, n);
        for (int j = 0; j < n; j++) {
            for (int i = 0; i < m; i++) {
                B.set(i, j, A.get(i, j));
            }
        }
        return B;
    }

    /**
     * Copies a double-precision {@link DenseMatrix} into a target {@link DenseMatrix}.
     */
    private static void copyMatrix(DenseMatrix src, DenseMatrix dst) {
        int m = src.nrow();
        int n = src.ncol();
        for (int j = 0; j < n; j++) {
            for (int i = 0; i < m; i++) {
                dst.set(i, j, src.get(i, j));
            }
        }
    }

    /**
     * Computes the compact Singular Value Decomposition of an m-by-n matrix {@code A}.
     *
     * @param A       the matrix to decompose (overwritten on exit).
     * @param vectors whether to return the singular vectors {@code U} and {@code Vt}.
     * @return the {@link SVD} result.
     */
    static SVD gesdd(DenseMatrix A, boolean vectors) {
        if (A.isSymmetric()) {
            mirrorSymmetric(A);
        }

        int m = A.nrow();
        int n = A.ncol();
        int k = Math.min(m, n);

        if (m < n) {
            // Decompose A^T (which is n x m with n > m):
            // A^T = U_B * S * V_B^T  ==>  A = V_B * S * U_B^T
            DenseMatrix At = DenseMatrix.zeros(Float64, n, m);
            for (int j = 0; j < n; j++) {
                for (int i = 0; i < m; i++) {
                    At.set(j, i, A.get(i, j));
                }
            }
            DenseMatrix Vb = DenseMatrix.zeros(Float64, m, m);
            double[] sigma = svdTall(At, Vb);

            Vector s = A.vector(k);
            for (int i = 0; i < k; i++) {
                s.set(i, sigma[i]);
            }
            if (!vectors) {
                return new SVD(m, n, s);
            }

            DenseMatrix U = A.zeros(m, k);
            copyMatrix(Vb, U);

            DenseMatrix Vt = A.zeros(k, n);
            for (int j = 0; j < n; j++) {
                for (int i = 0; i < k; i++) {
                    Vt.set(i, j, At.get(j, i));
                }
            }
            return new SVD(s, U, Vt);
        } else {
            DenseMatrix U64 = (A.scalarType() == Float64) ? A : toFloat64(A);
            DenseMatrix V64 = DenseMatrix.zeros(Float64, n, n);
            double[] sigma = svdTall(U64, V64);

            Vector s = A.vector(k);
            for (int i = 0; i < k; i++) {
                s.set(i, sigma[i]);
            }
            if (!vectors) {
                return new SVD(m, n, s);
            }

            DenseMatrix U;
            if (A.scalarType() == Float64) {
                U = U64;
                U.uplo = null;
            } else {
                U = A.zeros(m, k);
                copyMatrix(U64, U);
            }

            DenseMatrix Vt = A.zeros(k, n);
            for (int j = 0; j < n; j++) {
                for (int i = 0; i < k; i++) {
                    Vt.set(i, j, V64.get(j, i));
                }
            }
            return new SVD(s, U, Vt);
        }
    }

    /**
     * Golub-Kahan SVD for a matrix {@code U} with {@code m >= n}.
     * On exit, {@code U} contains the m-by-n left singular vectors,
     * {@code V} contains the n-by-n right singular vectors, and the returned
     * array contains the n singular values in descending order.
     */
    private static double[] svdTall(DenseMatrix U, DenseMatrix V) {
        int m = U.nrow();
        int n = U.ncol();

        boolean flag;
        int i, its, j, jj, k, l = 0, nm = 0;
        double anorm, c, f, g, h, s, scale, x, y, z;
        g = scale = anorm = 0.0;

        double[] w = new double[n];
        double[] rv1 = new double[n];

        for (i = 0; i < n; i++) {
            l = i + 2;
            rv1[i] = scale * g;
            g = s = scale = 0.0;

            if (i < m) {
                for (k = i; k < m; k++) {
                    scale += Math.abs(U.get(k, i));
                }

                if (scale != 0.0) {
                    for (k = i; k < m; k++) {
                        U.div(k, i, scale);
                        double uki = U.get(k, i);
                        s += uki * uki;
                    }

                    f = U.get(i, i);
                    g = -Math.copySign(Math.sqrt(s), f);
                    h = f * g - s;
                    U.set(i, i, f - g);
                    for (j = l - 1; j < n; j++) {
                        for (s = 0.0, k = i; k < m; k++) {
                            s += U.get(k, i) * U.get(k, j);
                        }
                        f = s / h;
                        for (k = i; k < m; k++) {
                            U.add(k, j, f * U.get(k, i));
                        }
                    }
                    for (k = i; k < m; k++) {
                        U.mul(k, i, scale);
                    }
                }
            }

            w[i] = scale * g;
            g = s = scale = 0.0;

            if (i + 1 <= m && i + 1 != n) {
                for (k = l - 1; k < n; k++) {
                    scale += Math.abs(U.get(i, k));
                }

                if (scale != 0.0) {
                    for (k = l - 1; k < n; k++) {
                        U.div(i, k, scale);
                        double uik = U.get(i, k);
                        s += uik * uik;
                    }

                    f = U.get(i, l - 1);
                    g = -Math.copySign(Math.sqrt(s), f);
                    h = f * g - s;
                    U.set(i, l - 1, f - g);

                    for (k = l - 1; k < n; k++) {
                        rv1[k] = U.get(i, k) / h;
                    }

                    for (j = l - 1; j < m; j++) {
                        for (s = 0.0, k = l - 1; k < n; k++) {
                            s += U.get(j, k) * U.get(i, k);
                        }

                        for (k = l - 1; k < n; k++) {
                            U.add(j, k, s * rv1[k]);
                        }
                    }

                    for (k = l - 1; k < n; k++) {
                        U.mul(i, k, scale);
                    }
                }
            }

            anorm = Math.max(anorm, (Math.abs(w[i]) + Math.abs(rv1[i])));
        }

        for (i = n - 1; i >= 0; i--) {
            if (i < n - 1) {
                if (g != 0.0) {
                    for (j = l; j < n; j++) {
                        V.set(j, i, (U.get(i, j) / U.get(i, l)) / g);
                    }
                    for (j = l; j < n; j++) {
                        for (s = 0.0, k = l; k < n; k++) {
                            s += U.get(i, k) * V.get(k, j);
                        }
                        for (k = l; k < n; k++) {
                            V.add(k, j, s * V.get(k, i));
                        }
                    }
                }
                for (j = l; j < n; j++) {
                    V.set(i, j, 0.0);
                    V.set(j, i, 0.0);
                }
            }
            V.set(i, i, 1.0);
            g = rv1[i];
            l = i;
        }

        for (i = Math.min(m, n) - 1; i >= 0; i--) {
            l = i + 1;
            g = w[i];
            for (j = l; j < n; j++) {
                U.set(i, j, 0.0);
            }

            if (g != 0.0) {
                g = 1.0 / g;
                for (j = l; j < n; j++) {
                    for (s = 0.0, k = l; k < m; k++) {
                        s += U.get(k, i) * U.get(k, j);
                    }
                    f = (s / U.get(i, i)) * g;
                    for (k = i; k < m; k++) {
                        U.add(k, j, f * U.get(k, i));
                    }
                }
                for (j = i; j < m; j++) {
                    U.mul(j, i, g);
                }
            } else {
                for (j = i; j < m; j++) {
                    U.set(j, i, 0.0);
                }
            }

            U.add(i, i, 1.0);
        }

        for (k = n - 1; k >= 0; k--) {
            for (its = 0; its < 30; its++) {
                flag = true;
                for (l = k; l >= 0; l--) {
                    nm = l - 1;
                    if (l == 0 || Math.abs(rv1[l]) <= MathEx.EPSILON * anorm) {
                        flag = false;
                        break;
                    }
                    if (Math.abs(w[nm]) <= MathEx.EPSILON * anorm) {
                        break;
                    }
                }

                if (flag) {
                    c = 0.0;
                    s = 1.0;
                    for (i = l; i < k + 1; i++) {
                        f = s * rv1[i];
                        rv1[i] = c * rv1[i];
                        if (Math.abs(f) <= MathEx.EPSILON * anorm) {
                            break;
                        }
                        g = w[i];
                        h = Math.hypot(f, g);
                        w[i] = h;
                        h = 1.0 / h;
                        c = g * h;
                        s = -f * h;
                        for (j = 0; j < m; j++) {
                            y = U.get(j, nm);
                            z = U.get(j, i);
                            U.set(j, nm, y * c + z * s);
                            U.set(j, i, z * c - y * s);
                        }
                    }
                }

                z = w[k];
                if (l == k) {
                    if (z < 0.0) {
                        w[k] = -z;
                        for (j = 0; j < n; j++) {
                            V.set(j, k, -V.get(j, k));
                        }
                    }
                    break;
                }

                if (its == 29) {
                    throw new ArithmeticException("SVD did not converge in 30 iterations");
                }

                x = w[l];
                nm = k - 1;
                y = w[nm];
                g = rv1[nm];
                h = rv1[k];
                f = ((y - z) * (y + z) + (g - h) * (g + h)) / (2.0 * h * y);
                g = Math.hypot(f, 1.0);
                f = ((x - z) * (x + z) + h * ((y / (f + Math.copySign(g, f))) - h)) / x;
                c = s = 1.0;

                for (j = l; j <= nm; j++) {
                    i = j + 1;
                    g = rv1[i];
                    y = w[i];
                    h = s * g;
                    g = c * g;
                    z = Math.hypot(f, h);
                    rv1[j] = z;
                    c = f / z;
                    s = h / z;
                    f = x * c + g * s;
                    g = g * c - x * s;
                    h = y * s;
                    y *= c;

                    for (jj = 0; jj < n; jj++) {
                        x = V.get(jj, j);
                        z = V.get(jj, i);
                        V.set(jj, j, x * c + z * s);
                        V.set(jj, i, z * c - x * s);
                    }

                    z = Math.hypot(f, h);
                    w[j] = z;
                    if (z != 0.0) {
                        z = 1.0 / z;
                        c = f * z;
                        s = h * z;
                    }

                    f = c * g + s * y;
                    x = c * y - s * g;
                    for (jj = 0; jj < m; jj++) {
                        y = U.get(jj, j);
                        z = U.get(jj, i);
                        U.set(jj, j, y * c + z * s);
                        U.set(jj, i, z * c - y * s);
                    }
                }

                rv1[l] = 0.0;
                rv1[k] = f;
                w[k] = x;
            }
        }

        // Order singular values in descending order.
        int inc = 1;
        double sw;
        double[] su = new double[m];
        double[] sv = new double[n];

        do {
            inc *= 3;
            inc++;
        } while (inc <= n);

        do {
            inc /= 3;
            for (i = inc; i < n; i++) {
                sw = w[i];
                for (k = 0; k < m; k++) {
                    su[k] = U.get(k, i);
                }
                for (k = 0; k < n; k++) {
                    sv[k] = V.get(k, i);
                }
                j = i;
                while (w[j - inc] < sw) {
                    w[j] = w[j - inc];
                    for (k = 0; k < m; k++) {
                        U.set(k, j, U.get(k, j - inc));
                    }
                    for (k = 0; k < n; k++) {
                        V.set(k, j, V.get(k, j - inc));
                    }
                    j -= inc;
                    if (j < inc) {
                        break;
                    }
                }
                w[j] = sw;
                for (k = 0; k < m; k++) {
                    U.set(k, j, su[k]);
                }
                for (k = 0; k < n; k++) {
                    V.set(k, j, sv[k]);
                }
            }
        } while (inc > 1);

        for (k = 0; k < n; k++) {
            s = 0;
            for (i = 0; i < m; i++) {
                if (U.get(i, k) < 0.0) {
                    s++;
                }
            }
            for (j = 0; j < n; j++) {
                if (V.get(j, k) < 0.0) {
                    s++;
                }
            }
            if (s > (m + n) / 2) {
                for (i = 0; i < m; i++) {
                    U.set(i, k, -U.get(i, k));
                }
                for (j = 0; j < n; j++) {
                    V.set(j, k, -V.get(j, k));
                }
            }
        }

        return w;
    }

    // =========================================================================
    // 5. Eigenvalue Decomposition (syevd, geev)
    // =========================================================================

    /**
     * Computes all eigenvalues and, optionally, eigenvectors of a real
     * symmetric matrix {@code eig}.
     *
     * @param eig the symmetric matrix (overwritten with eigenvectors when {@code vr == true}).
     * @param vr  whether to compute eigenvectors.
     * @return the {@link EVD} result.
     */
    static EVD syevd(DenseMatrix eig, boolean vr) {
        mirrorSymmetric(eig);
        int n = eig.nrow();
        double[] d = new double[n];
        double[] e = new double[n];

        DenseMatrix V = (eig.scalarType() == Float64) ? eig : toFloat64(eig);
        if (vr) {
            tred2(V, d, e);
            tql2(V, d, e);
            if (eig.scalarType() != Float64) {
                copyMatrix(V, eig);
            }
        } else {
            tred(V, d, e);
            tql(d, e, n);
        }

        Vector w = eig.vector(n);
        for (int i = 0; i < n; i++) {
            w.set(i, d[i]);
        }
        eig.uplo = null;
        return new EVD(w, vr ? eig : null);
    }

    /**
     * Computes all eigenvalues and, optionally, left and/or right eigenvectors
     * of a real nonsymmetric matrix {@code eig}.
     *
     * @param eig the square matrix (overwritten on exit).
     * @param vl  whether to compute left eigenvectors.
     * @param vr  whether to compute right eigenvectors.
     * @return the {@link EVD} result.
     */
    static EVD geev(DenseMatrix eig, boolean vl, boolean vr) {
        int n = eig.nrow();
        double[] d = new double[n];
        double[] e = new double[n];

        DenseMatrix A64 = (eig.scalarType() == Float64) ? eig : toFloat64(eig);
        DenseMatrix At64 = null;
        if (vl) {
            // Save A^T before A64 is overwritten by balance/elmhes/hqr2.
            At64 = DenseMatrix.zeros(Float64, n, n);
            for (int j = 0; j < n; j++) {
                for (int i = 0; i < n; i++) {
                    At64.set(j, i, A64.get(i, j));
                }
            }
        }

        DenseMatrix Vr = null;
        if (vr || vl) {
            double[] scale = balance(A64);
            int[] perm = elmhes(A64);
            DenseMatrix V64 = DenseMatrix.eye(Float64, n);
            eltran(A64, V64, perm);
            hqr2(A64, V64, d, e);
            balbak(V64, scale);
            normalizeEigenvectors(V64, e);
            if (vr) {
                if (eig.scalarType() == Float64) {
                    Vr = V64;
                } else {
                    Vr = eig.zeros(n, n);
                    copyMatrix(V64, Vr);
                }
            }
        } else {
            balance(A64);
            elmhes(A64);
            hqr(A64, d, e);
        }

        DenseMatrix Vl = null;
        if (vl) {
            double[] dl = new double[n];
            double[] el = new double[n];
            double[] scaleL = balance(At64);
            int[] permL = elmhes(At64);
            DenseMatrix Vl64 = DenseMatrix.eye(Float64, n);
            eltran(At64, Vl64, permL);
            hqr2(At64, Vl64, dl, el);
            balbak(Vl64, scaleL);
            normalizeEigenvectors(Vl64, el);

            DenseMatrix alignedVl64 = alignLeftEigenvectors(d, e, dl, el, Vl64);
            if (eig.scalarType() == Float64) {
                Vl = alignedVl64;
            } else {
                Vl = eig.zeros(n, n);
                copyMatrix(alignedVl64, Vl);
            }
        }

        Vector wr = eig.vector(n);
        Vector wi = eig.vector(n);
        for (int i = 0; i < n; i++) {
            wr.set(i, d[i]);
            wi.set(i, e[i]);
        }

        return new EVD(wr, wi, Vl, Vr);
    }

    /**
     * Normalizes eigenvector columns of {@code V} to unit Euclidean norm
     * (matching LAPACK's {@code dgeev} convention).
     */
    private static void normalizeEigenvectors(DenseMatrix V, double[] wi) {
        int n = V.nrow();
        for (int j = 0; j < n; j++) {
            if (wi[j] == 0.0) {
                double norm = 0.0;
                for (int i = 0; i < n; i++) {
                    norm = Math.hypot(norm, V.get(i, j));
                }
                if (norm > 0.0) {
                    for (int i = 0; i < n; i++) {
                        V.div(i, j, norm);
                    }
                }
            } else if (wi[j] > 0.0 && j + 1 < n) {
                double norm = 0.0;
                for (int i = 0; i < n; i++) {
                    norm = Math.hypot(norm, Math.hypot(V.get(i, j), V.get(i, j + 1)));
                }
                if (norm > 0.0) {
                    for (int i = 0; i < n; i++) {
                        V.div(i, j, norm);
                        V.div(i, j + 1, norm);
                    }
                }
                j++;
            }
        }
    }

    /**
     * Aligns the right eigenvectors of {@code A^T} so their columns match the
     * eigenvalue ordering {@code (d, e)} of {@code A}.
     */
    private static DenseMatrix alignLeftEigenvectors(double[] d, double[] e, double[] dl, double[] el, DenseMatrix VlRaw) {
        int n = d.length;
        DenseMatrix Vl = DenseMatrix.zeros(Float64, n, n);
        boolean[] used = new boolean[n];

        for (int j = 0; j < n; j++) {
            if (e[j] == 0.0) {
                int best = -1;
                double bestDist = Double.POSITIVE_INFINITY;
                for (int k = 0; k < n; k++) {
                    if (!used[k] && el[k] == 0.0) {
                        double dist = Math.abs(d[j] - dl[k]);
                        if (dist < bestDist) {
                            bestDist = dist;
                            best = k;
                        }
                    }
                }
                if (best < 0) {
                    for (int k = 0; k < n; k++) {
                        if (!used[k]) {
                            double dist = Math.hypot(d[j] - dl[k], el[k]);
                            if (dist < bestDist) {
                                bestDist = dist;
                                best = k;
                            }
                        }
                    }
                }
                used[best] = true;
                for (int i = 0; i < n; i++) {
                    Vl.set(i, j, VlRaw.get(i, best));
                }
            } else if (e[j] > 0.0 && j + 1 < n) {
                int best = -1;
                double bestDist = Double.POSITIVE_INFINITY;
                for (int k = 0; k < n - 1; k++) {
                    if (!used[k] && el[k] > 0.0) {
                        double dist = Math.hypot(d[j] - dl[k], e[j] - el[k]);
                        if (dist < bestDist) {
                            bestDist = dist;
                            best = k;
                        }
                    }
                }
                if (best >= 0) {
                    used[best] = true;
                    used[best + 1] = true;
                    for (int i = 0; i < n; i++) {
                        Vl.set(i, j, VlRaw.get(i, best));
                        Vl.set(i, j + 1, VlRaw.get(i, best + 1));
                    }
                }
                j++;
            }
        }
        return Vl;
    }

    /**
     * Symmetric Householder reduction to tridiagonal form (eigenvalues only).
     */
    private static void tred(DenseMatrix V, double[] d, double[] e) {
        int n = V.nrow();
        for (int i = 0; i < n; i++) {
            d[i] = V.get(n - 1, i);
        }

        for (int i = n - 1; i > 0; i--) {
            double scale = 0.0;
            double h = 0.0;
            for (int k = 0; k < i; k++) {
                scale += Math.abs(d[k]);
            }
            if (scale == 0.0) {
                e[i] = d[i - 1];
                for (int j = 0; j < i; j++) {
                    d[j] = V.get(i - 1, j);
                    V.set(i, j, 0.0);
                    V.set(j, i, 0.0);
                }
            } else {
                for (int k = 0; k < i; k++) {
                    d[k] /= scale;
                    h += d[k] * d[k];
                }
                double f = d[i - 1];
                double g = Math.sqrt(h);
                if (f > 0) {
                    g = -g;
                }
                e[i] = scale * g;
                h -= f * g;
                d[i - 1] = f - g;
                for (int j = 0; j < i; j++) {
                    e[j] = 0.0;
                }

                for (int j = 0; j < i; j++) {
                    f = d[j];
                    V.set(j, i, f);
                    g = e[j] + V.get(j, j) * f;
                    for (int k = j + 1; k <= i - 1; k++) {
                        g += V.get(k, j) * d[k];
                        e[k] += V.get(k, j) * f;
                    }
                    e[j] = g;
                }
                f = 0.0;
                for (int j = 0; j < i; j++) {
                    e[j] /= h;
                    f += e[j] * d[j];
                }
                double hh = f / (h + h);
                for (int j = 0; j < i; j++) {
                    e[j] -= hh * d[j];
                }
                for (int j = 0; j < i; j++) {
                    f = d[j];
                    g = e[j];
                    for (int k = j; k <= i - 1; k++) {
                        V.sub(k, j, (f * e[k] + g * d[k]));
                    }
                    d[j] = V.get(i - 1, j);
                    V.set(i, j, 0.0);
                }
            }
            d[i] = h;
        }

        for (int j = 0; j < n; j++) {
            d[j] = V.get(j, j);
        }
        e[0] = 0.0;
    }

    /**
     * Symmetric Householder reduction to tridiagonal form (with eigenvectors).
     */
    private static void tred2(DenseMatrix V, double[] d, double[] e) {
        int n = V.nrow();
        for (int i = 0; i < n; i++) {
            d[i] = V.get(n - 1, i);
        }

        for (int i = n - 1; i > 0; i--) {
            double scale = 0.0;
            double h = 0.0;
            for (int k = 0; k < i; k++) {
                scale += Math.abs(d[k]);
            }
            if (scale == 0.0) {
                e[i] = d[i - 1];
                for (int j = 0; j < i; j++) {
                    d[j] = V.get(i - 1, j);
                    V.set(i, j, 0.0);
                    V.set(j, i, 0.0);
                }
            } else {
                for (int k = 0; k < i; k++) {
                    d[k] /= scale;
                    h += d[k] * d[k];
                }
                double f = d[i - 1];
                double g = Math.sqrt(h);
                if (f > 0) {
                    g = -g;
                }
                e[i] = scale * g;
                h -= f * g;
                d[i - 1] = f - g;
                for (int j = 0; j < i; j++) {
                    e[j] = 0.0;
                }

                for (int j = 0; j < i; j++) {
                    f = d[j];
                    V.set(j, i, f);
                    g = e[j] + V.get(j, j) * f;
                    for (int k = j + 1; k <= i - 1; k++) {
                        g += V.get(k, j) * d[k];
                        e[k] += V.get(k, j) * f;
                    }
                    e[j] = g;
                }
                f = 0.0;
                for (int j = 0; j < i; j++) {
                    e[j] /= h;
                    f += e[j] * d[j];
                }
                double hh = f / (h + h);
                for (int j = 0; j < i; j++) {
                    e[j] -= hh * d[j];
                }
                for (int j = 0; j < i; j++) {
                    f = d[j];
                    g = e[j];
                    for (int k = j; k <= i - 1; k++) {
                        V.sub(k, j, (f * e[k] + g * d[k]));
                    }
                    d[j] = V.get(i - 1, j);
                    V.set(i, j, 0.0);
                }
            }
            d[i] = h;
        }

        for (int i = 0; i < n - 1; i++) {
            V.set(n - 1, i, V.get(i, i));
            V.set(i, i, 1.0);
            double h = d[i + 1];
            if (h != 0.0) {
                for (int k = 0; k <= i; k++) {
                    d[k] = V.get(k, i + 1) / h;
                }
                for (int j = 0; j <= i; j++) {
                    double g = 0.0;
                    for (int k = 0; k <= i; k++) {
                        g += V.get(k, i + 1) * V.get(k, j);
                    }
                    for (int k = 0; k <= i; k++) {
                        V.sub(k, j, g * d[k]);
                    }
                }
            }
            for (int k = 0; k <= i; k++) {
                V.set(k, i + 1, 0.0);
            }
        }
        for (int j = 0; j < n; j++) {
            d[j] = V.get(n - 1, j);
            V.set(n - 1, j, 0.0);
        }
        V.set(n - 1, n - 1, 1.0);
        e[0] = 0.0;
    }

    /**
     * Tridiagonal QL Implicit routine for computing eigenvalues of a symmetric,
     * real, tridiagonal matrix.
     */
    private static void tql(double[] d, double[] e, int n) {
        for (int i = 1; i < n; i++) {
            e[i - 1] = e[i];
        }
        e[n - 1] = 0.0;

        double f = 0.0;
        double tst1 = 0.0;
        for (int l = 0; l < n; l++) {
            tst1 = Math.max(tst1, Math.abs(d[l]) + Math.abs(e[l]));
            int m = l;
            for (; m < n; m++) {
                if (Math.abs(e[m]) <= MathEx.EPSILON * tst1) {
                    break;
                }
            }

            if (m > l) {
                int iter = 0;
                do {
                    if (++iter >= 30) {
                        throw new ArithmeticException("Too many iterations in tql");
                    }

                    double g = d[l];
                    double p = (d[l + 1] - g) / (2.0 * e[l]);
                    double r = Math.hypot(p, 1.0);
                    if (p < 0) {
                        r = -r;
                    }
                    d[l] = e[l] / (p + r);
                    d[l + 1] = e[l] * (p + r);
                    double dl1 = d[l + 1];
                    double h = g - d[l];
                    for (int i = l + 2; i < n; i++) {
                        d[i] -= h;
                    }
                    f += h;

                    p = d[m];
                    double c = 1.0;
                    double c2 = c;
                    double c3 = c;
                    double el1 = e[l + 1];
                    double s = 0.0;
                    double s2 = 0.0;
                    for (int i = m - 1; i >= l; i--) {
                        c3 = c2;
                        c2 = c;
                        s2 = s;
                        g = c * e[i];
                        h = c * p;
                        r = Math.hypot(p, e[i]);
                        e[i + 1] = s * r;
                        s = e[i] / r;
                        c = p / r;
                        p = c * d[i] - s * g;
                        d[i + 1] = h + s * (c * g + s * d[i]);
                    }
                    p = -s * s2 * c3 * el1 * e[l] / dl1;
                    e[l] = s * p;
                    d[l] = c * p;
                } while (Math.abs(e[l]) > MathEx.EPSILON * tst1);
            }
            d[l] += f;
            e[l] = 0.0;
        }

        // Sort eigenvalues in ascending order (matching LAPACK dsyevd).
        for (int i = 0; i < n - 1; i++) {
            int k = i;
            double p = d[i];
            for (int j = i + 1; j < n; j++) {
                if (d[j] < p) {
                    k = j;
                    p = d[j];
                }
            }
            if (k != i) {
                d[k] = d[i];
                d[i] = p;
            }
        }
    }

    /**
     * Tridiagonal QL Implicit routine for computing eigenvalues and eigenvectors
     * of a symmetric, real, tridiagonal matrix.
     */
    private static void tql2(DenseMatrix V, double[] d, double[] e) {
        int n = V.nrow();
        for (int i = 1; i < n; i++) {
            e[i - 1] = e[i];
        }
        e[n - 1] = 0.0;

        double f = 0.0;
        double tst1 = 0.0;
        for (int l = 0; l < n; l++) {
            tst1 = Math.max(tst1, Math.abs(d[l]) + Math.abs(e[l]));
            int m = l;
            for (; m < n; m++) {
                if (Math.abs(e[m]) <= MathEx.EPSILON * tst1) {
                    break;
                }
            }

            if (m > l) {
                int iter = 0;
                do {
                    if (++iter >= 30) {
                        throw new ArithmeticException("Too many iterations in tql2");
                    }

                    double g = d[l];
                    double p = (d[l + 1] - g) / (2.0 * e[l]);
                    double r = Math.hypot(p, 1.0);
                    if (p < 0) {
                        r = -r;
                    }
                    d[l] = e[l] / (p + r);
                    d[l + 1] = e[l] * (p + r);
                    double dl1 = d[l + 1];
                    double h = g - d[l];
                    for (int i = l + 2; i < n; i++) {
                        d[i] -= h;
                    }
                    f += h;

                    p = d[m];
                    double c = 1.0;
                    double c2 = c;
                    double c3 = c;
                    double el1 = e[l + 1];
                    double s = 0.0;
                    double s2 = 0.0;
                    for (int i = m - 1; i >= l; i--) {
                        c3 = c2;
                        c2 = c;
                        s2 = s;
                        g = c * e[i];
                        h = c * p;
                        r = Math.hypot(p, e[i]);
                        e[i + 1] = s * r;
                        s = e[i] / r;
                        c = p / r;
                        p = c * d[i] - s * g;
                        d[i + 1] = h + s * (c * g + s * d[i]);

                        for (int k = 0; k < n; k++) {
                            h = V.get(k, i + 1);
                            V.set(k, i + 1, s * V.get(k, i) + c * h);
                            V.set(k, i, c * V.get(k, i) - s * h);
                        }
                    }
                    p = -s * s2 * c3 * el1 * e[l] / dl1;
                    e[l] = s * p;
                    d[l] = c * p;
                } while (Math.abs(e[l]) > MathEx.EPSILON * tst1);
            }
            d[l] += f;
            e[l] = 0.0;
        }

        // Sort eigenvalues and corresponding vectors in ascending order (matching LAPACK dsyevd).
        for (int i = 0; i < n - 1; i++) {
            int k = i;
            double p = d[i];
            for (int j = i + 1; j < n; j++) {
                if (d[j] < p) {
                    k = j;
                    p = d[j];
                }
            }
            if (k != i) {
                d[k] = d[i];
                d[i] = p;
                for (int j = 0; j < n; j++) {
                    p = V.get(j, i);
                    V.set(j, i, V.get(j, k));
                    V.set(j, k, p);
                }
            }
        }
    }

    /**
     * Balances a real nonsymmetric matrix in-place.
     */
    private static double[] balance(DenseMatrix A) {
        double sqrdx = MathEx.RADIX * MathEx.RADIX;
        int n = A.nrow();
        double[] scale = new double[n];
        for (int i = 0; i < n; i++) {
            scale[i] = 1.0;
        }

        boolean done = false;
        while (!done) {
            done = true;
            for (int i = 0; i < n; i++) {
                double r = 0.0;
                double c = 0.0;
                for (int j = 0; j < n; j++) {
                    if (j != i) {
                        c += Math.abs(A.get(j, i));
                        r += Math.abs(A.get(i, j));
                    }
                }
                if (c != 0.0 && r != 0.0) {
                    double g = r / MathEx.RADIX;
                    double f = 1.0;
                    double s = c + r;
                    while (c < g) {
                        f *= MathEx.RADIX;
                        c *= sqrdx;
                    }
                    g = r * MathEx.RADIX;
                    while (c > g) {
                        f /= MathEx.RADIX;
                        c /= sqrdx;
                    }
                    if ((c + r) / f < 0.95 * s) {
                        done = false;
                        g = 1.0 / f;
                        scale[i] *= f;
                        for (int j = 0; j < n; j++) {
                            A.mul(i, j, g);
                        }
                        for (int j = 0; j < n; j++) {
                            A.mul(j, i, f);
                        }
                    }
                }
            }
        }
        return scale;
    }

    /**
     * Back-transforms eigenvectors of a balanced matrix.
     */
    private static void balbak(DenseMatrix V, double[] scale) {
        int n = V.nrow();
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                V.mul(i, j, scale[i]);
            }
        }
    }

    /**
     * Reduces a real nonsymmetric matrix to upper Hessenberg form.
     */
    private static int[] elmhes(DenseMatrix A) {
        int n = A.nrow();
        int[] perm = new int[n];

        for (int m = 1; m < n - 1; m++) {
            double x = 0.0;
            int i = m;
            for (int j = m; j < n; j++) {
                if (Math.abs(A.get(j, m - 1)) > Math.abs(x)) {
                    x = A.get(j, m - 1);
                    i = j;
                }
            }
            perm[m] = i;
            if (i != m) {
                for (int j = m - 1; j < n; j++) {
                    double swap = A.get(i, j);
                    A.set(i, j, A.get(m, j));
                    A.set(m, j, swap);
                }
                for (int j = 0; j < n; j++) {
                    double swap = A.get(j, i);
                    A.set(j, i, A.get(j, m));
                    A.set(j, m, swap);
                }
            }
            if (x != 0.0) {
                for (i = m + 1; i < n; i++) {
                    double y = A.get(i, m - 1);
                    if (y != 0.0) {
                        y /= x;
                        A.set(i, m - 1, y);
                        for (int j = m; j < n; j++) {
                            A.sub(i, j, y * A.get(m, j));
                        }
                        for (int j = 0; j < n; j++) {
                            A.add(j, m, y * A.get(j, i));
                        }
                    }
                }
            }
        }
        return perm;
    }

    /**
     * Accumulates the stabilized elementary similarity transformations from {@link #elmhes}.
     */
    private static void eltran(DenseMatrix A, DenseMatrix V, int[] perm) {
        int n = A.nrow();
        for (int mp = n - 2; mp > 0; mp--) {
            for (int k = mp + 1; k < n; k++) {
                V.set(k, mp, A.get(k, mp - 1));
            }
            int i = perm[mp];
            if (i != mp) {
                for (int j = mp; j < n; j++) {
                    V.set(mp, j, V.get(i, j));
                    V.set(i, j, 0.0);
                }
                V.set(i, mp, 1.0);
            }
        }
    }

    /**
     * Finds all eigenvalues of an upper Hessenberg matrix (eigenvalues only).
     */
    private static void hqr(DenseMatrix A, double[] d, double[] e) {
        int n = A.nrow();
        int nn, m, l, k, j, its, i, mmin;
        double z, y, x, w, v, u, t, s, r = 0.0, q = 0.0, p = 0.0, anorm = 0.0;

        for (i = 0; i < n; i++) {
            for (j = Math.max(i - 1, 0); j < n; j++) {
                anorm += Math.abs(A.get(i, j));
            }
        }
        nn = n - 1;
        t = 0.0;
        while (nn >= 0) {
            its = 0;
            do {
                for (l = nn; l > 0; l--) {
                    s = Math.abs(A.get(l - 1, l - 1)) + Math.abs(A.get(l, l));
                    if (s == 0.0) {
                        s = anorm;
                    }
                    if (Math.abs(A.get(l, l - 1)) <= MathEx.EPSILON * s) {
                        A.set(l, l - 1, 0.0);
                        break;
                    }
                }
                x = A.get(nn, nn);
                if (l == nn) {
                    d[nn--] = x + t;
                } else {
                    y = A.get(nn - 1, nn - 1);
                    w = A.get(nn, nn - 1) * A.get(nn - 1, nn);
                    if (l == nn - 1) {
                        p = 0.5 * (y - x);
                        q = p * p + w;
                        z = Math.sqrt(Math.abs(q));
                        x += t;
                        if (q >= 0.0) {
                            z = p + Math.copySign(z, p);
                            d[nn - 1] = d[nn] = x + z;
                            if (z != 0.0) {
                                d[nn] = x - w / z;
                            }
                        } else {
                            d[nn] = x + p;
                            e[nn] = -z;
                            d[nn - 1] = d[nn];
                            e[nn - 1] = -e[nn];
                        }
                        nn -= 2;
                    } else {
                        if (its == 30) {
                            throw new ArithmeticException("Too many iterations in hqr");
                        }
                        if (its == 10 || its == 20) {
                            t += x;
                            for (i = 0; i < nn + 1; i++) {
                                A.sub(i, i, x);
                            }
                            s = Math.abs(A.get(nn, nn - 1)) + Math.abs(A.get(nn - 1, nn - 2));
                            y = x = 0.75 * s;
                            w = -0.4375 * s * s;
                        }
                        ++its;
                        for (m = nn - 2; m >= l; m--) {
                            z = A.get(m, m);
                            r = x - z;
                            s = y - z;
                            p = (r * s - w) / A.get(m + 1, m) + A.get(m, m + 1);
                            q = A.get(m + 1, m + 1) - z - r - s;
                            r = A.get(m + 2, m + 1);
                            s = Math.abs(p) + Math.abs(q) + Math.abs(r);
                            p /= s;
                            q /= s;
                            r /= s;
                            if (m == l) {
                                break;
                            }
                            u = Math.abs(A.get(m, m - 1)) * (Math.abs(q) + Math.abs(r));
                            v = Math.abs(p) * (Math.abs(A.get(m - 1, m - 1)) + Math.abs(z) + Math.abs(A.get(m + 1, m + 1)));
                            if (u <= MathEx.EPSILON * v) {
                                break;
                            }
                        }
                        for (i = m; i < nn - 1; i++) {
                            A.set(i + 2, i, 0.0);
                            if (i != m) {
                                A.set(i + 2, i - 1, 0.0);
                            }
                        }
                        for (k = m; k < nn; k++) {
                            if (k != m) {
                                p = A.get(k, k - 1);
                                q = A.get(k + 1, k - 1);
                                r = 0.0;
                                if (k + 1 != nn) {
                                    r = A.get(k + 2, k - 1);
                                }
                                if ((x = Math.abs(p) + Math.abs(q) + Math.abs(r)) != 0.0) {
                                    p /= x;
                                    q /= x;
                                    r /= x;
                                }
                            }
                            if ((s = Math.copySign(Math.sqrt(p * p + q * q + r * r), p)) != 0.0) {
                                if (k == m) {
                                    if (l != m) {
                                        A.set(k, k - 1, -A.get(k, k - 1));
                                    }
                                } else {
                                    A.set(k, k - 1, -s * x);
                                }
                                p += s;
                                x = p / s;
                                y = q / s;
                                z = r / s;
                                q /= p;
                                r /= p;
                                for (j = k; j < nn + 1; j++) {
                                    p = A.get(k, j) + q * A.get(k + 1, j);
                                    if (k + 1 != nn) {
                                        p += r * A.get(k + 2, j);
                                        A.sub(k + 2, j, p * z);
                                    }
                                    A.sub(k + 1, j, p * y);
                                    A.sub(k, j, p * x);
                                }
                                mmin = nn < k + 3 ? nn : k + 3;
                                for (i = l; i < mmin + 1; i++) {
                                    p = x * A.get(i, k) + y * A.get(i, k + 1);
                                    if (k + 1 != nn) {
                                        p += z * A.get(i, k + 2);
                                        A.sub(i, k + 2, p * r);
                                    }
                                    A.sub(i, k + 1, p * q);
                                    A.sub(i, k, p);
                                }
                            }
                        }
                    }
                }
            } while (l + 1 < nn);
        }
    }

    /**
     * Finds all eigenvalues and eigenvectors of an upper Hessenberg matrix.
     */
    private static void hqr2(DenseMatrix A, DenseMatrix V, double[] d, double[] e) {
        int n = A.nrow();
        int nn, m, l, k, j, its, i, mmin, na;
        double z = 0.0, y, x, w, v, u, t, s = 0.0, r = 0.0, q = 0.0, p = 0.0, anorm = 0.0, ra, sa, vr, vi;

        for (i = 0; i < n; i++) {
            for (j = Math.max(i - 1, 0); j < n; j++) {
                anorm += Math.abs(A.get(i, j));
            }
        }
        nn = n - 1;
        t = 0.0;
        while (nn >= 0) {
            its = 0;
            do {
                for (l = nn; l > 0; l--) {
                    s = Math.abs(A.get(l - 1, l - 1)) + Math.abs(A.get(l, l));
                    if (s == 0.0) {
                        s = anorm;
                    }
                    if (Math.abs(A.get(l, l - 1)) <= MathEx.EPSILON * s) {
                        A.set(l, l - 1, 0.0);
                        break;
                    }
                }
                x = A.get(nn, nn);
                if (l == nn) {
                    d[nn] = x + t;
                    A.set(nn, nn, x + t);
                    nn--;
                } else {
                    y = A.get(nn - 1, nn - 1);
                    w = A.get(nn, nn - 1) * A.get(nn - 1, nn);
                    if (l == nn - 1) {
                        p = 0.5 * (y - x);
                        q = p * p + w;
                        z = Math.sqrt(Math.abs(q));
                        x += t;
                        A.set(nn, nn, x);
                        A.set(nn - 1, nn - 1, y + t);
                        if (q >= 0.0) {
                            z = p + Math.copySign(z, p);
                            d[nn - 1] = d[nn] = x + z;
                            if (z != 0.0) {
                                d[nn] = x - w / z;
                            }
                            x = A.get(nn, nn - 1);
                            s = Math.abs(x) + Math.abs(z);
                            p = x / s;
                            q = z / s;
                            r = Math.sqrt(p * p + q * q);
                            p /= r;
                            q /= r;
                            for (j = nn - 1; j < n; j++) {
                                z = A.get(nn - 1, j);
                                A.set(nn - 1, j, q * z + p * A.get(nn, j));
                                A.set(nn, j, q * A.get(nn, j) - p * z);
                            }
                            for (i = 0; i <= nn; i++) {
                                z = A.get(i, nn - 1);
                                A.set(i, nn - 1, q * z + p * A.get(i, nn));
                                A.set(i, nn, q * A.get(i, nn) - p * z);
                            }
                            for (i = 0; i < n; i++) {
                                z = V.get(i, nn - 1);
                                V.set(i, nn - 1, q * z + p * V.get(i, nn));
                                V.set(i, nn, q * V.get(i, nn) - p * z);
                            }
                        } else {
                            d[nn] = x + p;
                            e[nn] = -z;
                            d[nn - 1] = d[nn];
                            e[nn - 1] = -e[nn];
                        }
                        nn -= 2;
                    } else {
                        if (its == 30) {
                            throw new ArithmeticException("Too many iterations in hqr2");
                        }
                        if (its == 10 || its == 20) {
                            t += x;
                            for (i = 0; i < nn + 1; i++) {
                                A.sub(i, i, x);
                            }
                            s = Math.abs(A.get(nn, nn - 1)) + Math.abs(A.get(nn - 1, nn - 2));
                            y = x = 0.75 * s;
                            w = -0.4375 * s * s;
                        }
                        ++its;
                        for (m = nn - 2; m >= l; m--) {
                            z = A.get(m, m);
                            r = x - z;
                            s = y - z;
                            p = (r * s - w) / A.get(m + 1, m) + A.get(m, m + 1);
                            q = A.get(m + 1, m + 1) - z - r - s;
                            r = A.get(m + 2, m + 1);
                            s = Math.abs(p) + Math.abs(q) + Math.abs(r);
                            p /= s;
                            q /= s;
                            r /= s;
                            if (m == l) {
                                break;
                            }
                            u = Math.abs(A.get(m, m - 1)) * (Math.abs(q) + Math.abs(r));
                            v = Math.abs(p) * (Math.abs(A.get(m - 1, m - 1)) + Math.abs(z) + Math.abs(A.get(m + 1, m + 1)));
                            if (u <= MathEx.EPSILON * v) {
                                break;
                            }
                        }
                        for (i = m; i < nn - 1; i++) {
                            A.set(i + 2, i, 0.0);
                            if (i != m) {
                                A.set(i + 2, i - 1, 0.0);
                            }
                        }
                        for (k = m; k < nn; k++) {
                            if (k != m) {
                                p = A.get(k, k - 1);
                                q = A.get(k + 1, k - 1);
                                r = 0.0;
                                if (k + 1 != nn) {
                                    r = A.get(k + 2, k - 1);
                                }
                                if ((x = Math.abs(p) + Math.abs(q) + Math.abs(r)) != 0.0) {
                                    p /= x;
                                    q /= x;
                                    r /= x;
                                }
                            }
                            if ((s = Math.copySign(Math.sqrt(p * p + q * q + r * r), p)) != 0.0) {
                                if (k == m) {
                                    if (l != m) {
                                        A.set(k, k - 1, -A.get(k, k - 1));
                                    }
                                } else {
                                    A.set(k, k - 1, -s * x);
                                }
                                p += s;
                                x = p / s;
                                y = q / s;
                                z = r / s;
                                q /= p;
                                r /= p;
                                for (j = k; j < n; j++) {
                                    p = A.get(k, j) + q * A.get(k + 1, j);
                                    if (k + 1 != nn) {
                                        p += r * A.get(k + 2, j);
                                        A.sub(k + 2, j, p * z);
                                    }
                                    A.sub(k + 1, j, p * y);
                                    A.sub(k, j, p * x);
                                }
                                mmin = nn < k + 3 ? nn : k + 3;
                                for (i = 0; i < mmin + 1; i++) {
                                    p = x * A.get(i, k) + y * A.get(i, k + 1);
                                    if (k + 1 != nn) {
                                        p += z * A.get(i, k + 2);
                                        A.sub(i, k + 2, p * r);
                                    }
                                    A.sub(i, k + 1, p * q);
                                    A.sub(i, k, p);
                                }
                                for (i = 0; i < n; i++) {
                                    p = x * V.get(i, k) + y * V.get(i, k + 1);
                                    if (k + 1 != nn) {
                                        p += z * V.get(i, k + 2);
                                        V.sub(i, k + 2, p * r);
                                    }
                                    V.sub(i, k + 1, p * q);
                                    V.sub(i, k, p);
                                }
                            }
                        }
                    }
                }
            } while (l + 1 < nn);
        }

        if (anorm != 0.0) {
            for (nn = n - 1; nn >= 0; nn--) {
                p = d[nn];
                q = e[nn];
                na = nn - 1;
                if (q == 0.0) {
                    m = nn;
                    A.set(nn, nn, 1.0);
                    for (i = nn - 1; i >= 0; i--) {
                        w = A.get(i, i) - p;
                        r = 0.0;
                        for (j = m; j <= nn; j++) {
                            r += A.get(i, j) * A.get(j, nn);
                        }
                        if (e[i] < 0.0) {
                            z = w;
                            s = r;
                        } else {
                            m = i;
                            if (e[i] == 0.0) {
                                t = w;
                                if (t == 0.0) {
                                    t = MathEx.EPSILON * anorm;
                                }
                                A.set(i, nn, -r / t);
                            } else {
                                x = A.get(i, i + 1);
                                y = A.get(i + 1, i);
                                q = MathEx.pow2(d[i] - p) + MathEx.pow2(e[i]);
                                t = (x * s - z * r) / q;
                                A.set(i, nn, t);
                                if (Math.abs(x) > Math.abs(z)) {
                                    A.set(i + 1, nn, (-r - w * t) / x);
                                } else {
                                    A.set(i + 1, nn, (-s - y * t) / z);
                                }
                            }
                            t = Math.abs(A.get(i, nn));
                            if (MathEx.EPSILON * t * t > 1) {
                                for (j = i; j <= nn; j++) {
                                    A.div(j, nn, t);
                                }
                            }
                        }
                    }
                } else if (q < 0.0) {
                    m = na;
                    if (Math.abs(A.get(nn, na)) > Math.abs(A.get(na, nn))) {
                        A.set(na, na, q / A.get(nn, na));
                        A.set(na, nn, -(A.get(nn, nn) - p) / A.get(nn, na));
                    } else {
                        Complex temp = cdiv(0.0, -A.get(na, nn), A.get(na, na) - p, q);
                        A.set(na, na, temp.re());
                        A.set(na, nn, temp.im());
                    }
                    A.set(nn, na, 0.0);
                    A.set(nn, nn, 1.0);
                    for (i = nn - 2; i >= 0; i--) {
                        w = A.get(i, i) - p;
                        ra = sa = 0.0;
                        for (j = m; j <= nn; j++) {
                            ra += A.get(i, j) * A.get(j, na);
                            sa += A.get(i, j) * A.get(j, nn);
                        }
                        if (e[i] < 0.0) {
                            z = w;
                            r = ra;
                            s = sa;
                        } else {
                            m = i;
                            if (e[i] == 0.0) {
                                Complex temp = cdiv(-ra, -sa, w, q);
                                A.set(i, na, temp.re());
                                A.set(i, nn, temp.im());
                            } else {
                                x = A.get(i, i + 1);
                                y = A.get(i + 1, i);
                                vr = MathEx.pow2(d[i] - p) + MathEx.pow2(e[i]) - q * q;
                                vi = 2.0 * q * (d[i] - p);
                                if (vr == 0.0 && vi == 0.0) {
                                    vr = MathEx.EPSILON * anorm * (Math.abs(w) + Math.abs(q) + Math.abs(x) + Math.abs(y) + Math.abs(z));
                                }
                                Complex temp = cdiv(x * r - z * ra + q * sa, x * s - z * sa - q * ra, vr, vi);
                                A.set(i, na, temp.re());
                                A.set(i, nn, temp.im());
                                if (Math.abs(x) > Math.abs(z) + Math.abs(q)) {
                                    A.set(i + 1, na, (-ra - w * A.get(i, na) + q * A.get(i, nn)) / x);
                                    A.set(i + 1, nn, (-sa - w * A.get(i, nn) - q * A.get(i, na)) / x);
                                } else {
                                    temp = cdiv(-r - y * A.get(i, na), -s - y * A.get(i, nn), z, q);
                                    A.set(i + 1, na, temp.re());
                                    A.set(i + 1, nn, temp.im());
                                }
                            }
                        }
                        t = Math.max(Math.abs(A.get(i, na)), Math.abs(A.get(i, nn)));
                        if (MathEx.EPSILON * t * t > 1) {
                            for (j = i; j <= nn; j++) {
                                A.div(j, na, t);
                                A.div(j, nn, t);
                            }
                        }
                    }
                }
            }

            for (j = n - 1; j >= 0; j--) {
                for (i = 0; i < n; i++) {
                    z = 0.0;
                    for (k = 0; k <= j; k++) {
                        z += V.get(i, k) * A.get(k, j);
                    }
                    V.set(i, j, z);
                }
            }
        }
    }

    /**
     * Complex scalar division.
     */
    private static Complex cdiv(double xr, double xi, double yr, double yi) {
        double cdivr, cdivi;
        double r, d;
        if (Math.abs(yr) > Math.abs(yi)) {
            r = yi / yr;
            d = yr + r * yi;
            cdivr = (xr + r * xi) / d;
            cdivi = (xi - r * xr) / d;
        } else {
            r = yr / yi;
            d = yi + r * yr;
            cdivr = (r * xr + xi) / d;
            cdivi = (r * xi - xr) / d;
        }
        return new Complex(cdivr, cdivi);
    }

    // =========================================================================
    // 6. Packed Symmetric & Band Matrix Solvers (spsv, gbsv)
    // =========================================================================

    /**
     * Solves {@code A * X = B} for a packed symmetric matrix {@code A} in-place on {@code B}.
     */
    static void spsv(SymmMatrix A, DenseMatrix B) {
        int n = A.nrow();
        DenseMatrix dense = DenseMatrix.zeros(A.scalarType(), n, n);
        for (int j = 0; j < n; j++) {
            dense.set(j, j, A.get(j, j));
            for (int i = j + 1; i < n; i++) {
                double aij = A.get(i, j);
                dense.set(i, j, aij);
                dense.set(j, i, aij);
            }
        }
        int[] ipiv = new int[n];
        int info = getrf(dense, ipiv);
        if (info > 0) {
            throw new RuntimeException("The matrix is singular.");
        }
        getrs(dense, ipiv, B);
    }

    /**
     * Solves {@code A * X = B} for a square band matrix {@code A} in-place on {@code B}
     * using banded Gaussian elimination with partial pivoting.
     */
    static void gbsv(BandMatrix A, DenseMatrix B) {
        int n = A.nrow();
        int m1 = A.kl();
        int m2 = A.ku();
        int mm = m1 + m2 + 1;
        UPLO uplo = A.uplo();

        int[] index = new int[n];
        double[][] au = new double[n][mm];
        double[][] al = new double[n][m1];

        for (int i = 0; i < n; i++) {
            int jMin = Math.max(0, i - m1);
            int jMax = Math.min(n - 1, i + m2);
            for (int j = jMin; j <= jMax; j++) {
                double val;
                if (uplo == LOWER && i < j) {
                    val = A.get(j, i);
                } else if (uplo == UPPER && i > j) {
                    val = A.get(j, i);
                } else {
                    val = A.get(i, j);
                }
                au[i][j - i + m1] = val;
            }
        }

        int l = m1;
        for (int i = 0; i < m1; i++) {
            for (int j = m1 - i; j < mm; j++) {
                au[i][j - l] = au[i][j];
            }
            l--;
            for (int j = mm - l - 1; j < mm; j++) {
                au[i][j] = 0.0;
            }
        }

        l = m1;
        for (int k = 0; k < n; k++) {
            double dum = au[k][0];
            int i = k;
            if (l < n) {
                l++;
            }

            for (int j = k + 1; j < l; j++) {
                if (Math.abs(au[j][0]) > Math.abs(dum)) {
                    dum = au[j][0];
                    i = j;
                }
            }

            index[k] = i;
            if (dum == 0.0) {
                throw new RuntimeException("The matrix is singular.");
            }

            if (i != k) {
                double[] swap = au[k];
                au[k] = au[i];
                au[i] = swap;
            }

            for (int ii = k + 1; ii < l; ii++) {
                dum = au[ii][0] / au[k][0];
                al[k][ii - k - 1] = dum;
                for (int j = 1; j < mm; j++) {
                    au[ii][j - 1] = au[ii][j] - dum * au[k][j];
                }
                au[ii][mm - 1] = 0.0;
            }
        }

        int nrhs = B.ncol();
        for (int col = 0; col < nrhs; col++) {
            l = m1;
            for (int k = 0; k < n; k++) {
                int j = index[k];
                if (j != k) {
                    double swap = B.get(k, col);
                    B.set(k, col, B.get(j, col));
                    B.set(j, col, swap);
                }
                if (l < n) {
                    l++;
                }
                double bkc = B.get(k, col);
                for (j = k + 1; j < l; j++) {
                    B.sub(j, col, al[k][j - k - 1] * bkc);
                }
            }

            l = 1;
            for (int i = n - 1; i >= 0; i--) {
                double dum = B.get(i, col);
                for (int k = 1; k < l; k++) {
                    dum -= au[i][k] * B.get(k + i, col);
                }
                B.set(i, col, dum / au[i][0]);
                if (l < mm) {
                    l++;
                }
            }
        }
    }
}
