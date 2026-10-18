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

import smile.linalg.Diag;
import smile.linalg.Side;
import smile.linalg.Transpose;
import smile.linalg.UPLO;
import static smile.linalg.Side.*;
import static smile.linalg.Transpose.*;
import static smile.linalg.UPLO.*;

/**
 * Pure-Java Level-2 and Level-3 BLAS routines operating on column-major
 * {@link DenseMatrix}, {@link SymmMatrix}, and {@link BandMatrix}.
 *
 * @author Haifeng Li
 */
final class JBlas {
    /** Utility class; not instantiable. */
    private JBlas() {}

    /**
     * Scales a dense matrix in-place by {@code alpha}.
     */
    static void scal(DenseMatrix A, double alpha) {
        if (A instanceof DenseMatrix64 a) {
            int len = a.capacity();
            double[] d = a.data;
            for (int i = 0; i < len; i++) {
                d[i] *= alpha;
            }
        } else if (A instanceof DenseMatrix32 a) {
            int len = a.capacity();
            float[] d = a.data;
            float a32 = (float) alpha;
            for (int i = 0; i < len; i++) {
                d[i] *= a32;
            }
        } else {
            int m = A.nrow();
            int n = A.ncol();
            for (int j = 0; j < n; j++) {
                for (int i = 0; i < m; i++) {
                    A.mul(i, j, alpha);
                }
            }
        }
    }

    /**
     * Scales a packed symmetric matrix in-place by {@code alpha}.
     */
    static void scal(SymmMatrix A, double alpha) {
        if (A instanceof SymmMatrix64 a) {
            double[] ap = a.ap;
            for (int i = 0; i < ap.length; i++) {
                ap[i] *= alpha;
            }
        } else if (A instanceof SymmMatrix32 a) {
            float[] ap = a.ap;
            float a32 = (float) alpha;
            for (int i = 0; i < ap.length; i++) {
                ap[i] *= a32;
            }
        } else {
            int n = A.nrow();
            for (int j = 0; j < n; j++) {
                for (int i = 0; i <= j; i++) {
                    A.mul(i, j, alpha);
                }
            }
        }
    }

    /**
     * Scales a band matrix in-place by {@code alpha}.
     */
    static void scal(BandMatrix A, double alpha) {
        if (A instanceof BandMatrix64 a) {
            double[] ab = a.ab;
            for (int i = 0; i < ab.length; i++) {
                ab[i] *= alpha;
            }
        } else if (A instanceof BandMatrix32 a) {
            float[] ab = a.ab;
            float a32 = (float) alpha;
            for (int i = 0; i < ab.length; i++) {
                ab[i] *= a32;
            }
        }
    }

    /**
     * Computes {@code Y += alpha * X} in-place on {@code Y}.
     */
    static void axpy(double alpha, DenseMatrix X, DenseMatrix Y) {
        if (alpha == 0.0) {
            return;
        }
        if (X instanceof DenseMatrix64 x && Y instanceof DenseMatrix64 y && x.ld == y.ld) {
            int len = y.capacity();
            double[] xd = x.data;
            double[] yd = y.data;
            for (int i = 0; i < len; i++) {
                yd[i] += alpha * xd[i];
            }
        } else if (X instanceof DenseMatrix32 x && Y instanceof DenseMatrix32 y && x.ld == y.ld) {
            int len = y.capacity();
            float[] xd = x.data;
            float[] yd = y.data;
            float a32 = (float) alpha;
            for (int i = 0; i < len; i++) {
                yd[i] += a32 * xd[i];
            }
        } else {
            int m = Y.nrow();
            int n = Y.ncol();
            for (int j = 0; j < n; j++) {
                for (int i = 0; i < m; i++) {
                    Y.add(i, j, alpha * X.get(i, j));
                }
            }
        }
    }

    /**
     * Scales the first {@code len} elements of {@code y} by {@code beta}
     * (zeroing when {@code beta == 0.0}).
     */
    private static void scaleVector(Vector y, int len, double beta) {
        if (beta == 0.0) {
            y.fill(0, len, 0.0);
        } else if (beta != 1.0) {
            for (int i = 0; i < len; i++) {
                y.mul(i, beta);
            }
        }
    }

    /**
     * General matrix-vector multiplication {@code y := alpha * op(A) * x + beta * y}.
     */
    static void gemv(Transpose trans, double alpha, DenseMatrix A, Vector x, double beta, Vector y) {
        int m = A.nrow();
        int n = A.ncol();
        if (trans == NO_TRANSPOSE) {
            scaleVector(y, m, beta);
            if (alpha == 0.0) {
                return;
            }
            if (A instanceof DenseMatrix64 a && x instanceof Vector64 xv && y instanceof Vector64 yv) {
                double[] ad = a.data;
                int ld = a.ld;
                double[] xd = xv.array;
                int xoff = xv.offset;
                double[] yd = yv.array;
                int yoff = yv.offset;
                for (int j = 0; j < n; j++) {
                    double temp = alpha * xd[xoff + j];
                    if (temp != 0.0) {
                        int col = j * ld;
                        for (int i = 0; i < m; i++) {
                            yd[yoff + i] += temp * ad[col + i];
                        }
                    }
                }
            } else if (A instanceof DenseMatrix32 a && x instanceof Vector32 xv && y instanceof Vector32 yv) {
                float[] ad = a.data;
                int ld = a.ld;
                float[] xd = xv.array;
                int xoff = xv.offset;
                float[] yd = yv.array;
                int yoff = yv.offset;
                float a32 = (float) alpha;
                for (int j = 0; j < n; j++) {
                    float temp = a32 * xd[xoff + j];
                    if (temp != 0.0f) {
                        int col = j * ld;
                        for (int i = 0; i < m; i++) {
                            yd[yoff + i] += temp * ad[col + i];
                        }
                    }
                }
            } else {
                for (int j = 0; j < n; j++) {
                    double temp = alpha * x.get(j);
                    if (temp != 0.0) {
                        for (int i = 0; i < m; i++) {
                            y.add(i, temp * A.get(i, j));
                        }
                    }
                }
            }
        } else {
            scaleVector(y, n, beta);
            if (alpha == 0.0) {
                return;
            }
            if (A instanceof DenseMatrix64 a && x instanceof Vector64 xv && y instanceof Vector64 yv) {
                double[] ad = a.data;
                int ld = a.ld;
                double[] xd = xv.array;
                int xoff = xv.offset;
                double[] yd = yv.array;
                int yoff = yv.offset;
                for (int j = 0; j < n; j++) {
                    double sum = 0.0;
                    int col = j * ld;
                    for (int i = 0; i < m; i++) {
                        sum += ad[col + i] * xd[xoff + i];
                    }
                    yd[yoff + j] += alpha * sum;
                }
            } else if (A instanceof DenseMatrix32 a && x instanceof Vector32 xv && y instanceof Vector32 yv) {
                float[] ad = a.data;
                int ld = a.ld;
                float[] xd = xv.array;
                int xoff = xv.offset;
                float[] yd = yv.array;
                int yoff = yv.offset;
                float a32 = (float) alpha;
                for (int j = 0; j < n; j++) {
                    float sum = 0.0f;
                    int col = j * ld;
                    for (int i = 0; i < m; i++) {
                        sum += ad[col + i] * xd[xoff + i];
                    }
                    yd[yoff + j] += a32 * sum;
                }
            } else {
                for (int j = 0; j < n; j++) {
                    double sum = 0.0;
                    for (int i = 0; i < m; i++) {
                        sum += A.get(i, j) * x.get(i);
                    }
                    y.add(j, alpha * sum);
                }
            }
        }
    }

    /**
     * Symmetric matrix-vector multiplication {@code y := alpha * A * x + beta * y}.
     */
    static void symv(UPLO uplo, double alpha, DenseMatrix A, Vector x, double beta, Vector y) {
        int n = A.nrow();
        scaleVector(y, n, beta);
        if (alpha == 0.0) {
            return;
        }
        if (uplo == UPPER) {
            for (int j = 0; j < n; j++) {
                double temp1 = alpha * x.get(j);
                double temp2 = 0.0;
                for (int i = 0; i < j; i++) {
                    double aij = A.get(i, j);
                    y.add(i, temp1 * aij);
                    temp2 += aij * x.get(i);
                }
                y.add(j, temp1 * A.get(j, j) + alpha * temp2);
            }
        } else {
            for (int j = 0; j < n; j++) {
                double temp1 = alpha * x.get(j);
                double temp2 = 0.0;
                y.add(j, temp1 * A.get(j, j));
                for (int i = j + 1; i < n; i++) {
                    double aij = A.get(i, j);
                    y.add(i, temp1 * aij);
                    temp2 += aij * x.get(i);
                }
                y.add(j, alpha * temp2);
            }
        }
    }

    /**
     * In-place triangular matrix-vector multiplication {@code x := op(A) * x}.
     */
    static void trmv(UPLO uplo, Transpose trans, Diag diag, DenseMatrix A, Vector x) {
        int n = A.nrow();
        boolean nounit = (diag == Diag.NON_UNIT);
        if (trans == NO_TRANSPOSE) {
            if (uplo == UPPER) {
                for (int j = 0; j < n; j++) {
                    double temp = x.get(j);
                    if (temp != 0.0) {
                        for (int i = 0; i < j; i++) {
                            x.add(i, temp * A.get(i, j));
                        }
                        if (nounit) {
                            x.mul(j, A.get(j, j));
                        }
                    }
                }
            } else {
                for (int j = n - 1; j >= 0; j--) {
                    double temp = x.get(j);
                    if (temp != 0.0) {
                        for (int i = n - 1; i > j; i--) {
                            x.add(i, temp * A.get(i, j));
                        }
                        if (nounit) {
                            x.mul(j, A.get(j, j));
                        }
                    }
                }
            }
        } else {
            if (uplo == UPPER) {
                for (int j = n - 1; j >= 0; j--) {
                    double temp = nounit ? x.get(j) * A.get(j, j) : x.get(j);
                    for (int i = j - 1; i >= 0; i--) {
                        temp += A.get(i, j) * x.get(i);
                    }
                    x.set(j, temp);
                }
            } else {
                for (int j = 0; j < n; j++) {
                    double temp = nounit ? x.get(j) * A.get(j, j) : x.get(j);
                    for (int i = j + 1; i < n; i++) {
                        temp += A.get(i, j) * x.get(i);
                    }
                    x.set(j, temp);
                }
            }
        }
    }

    /**
     * Rank-1 update {@code A := A + alpha * x * y'}.
     */
    static void ger(double alpha, DenseMatrix A, Vector x, Vector y) {
        if (alpha == 0.0) {
            return;
        }
        int m = A.nrow();
        int n = A.ncol();
        if (A instanceof DenseMatrix64 a && x instanceof Vector64 xv && y instanceof Vector64 yv) {
            double[] ad = a.data;
            int ld = a.ld;
            double[] xd = xv.array;
            int xoff = xv.offset;
            double[] yd = yv.array;
            int yoff = yv.offset;
            for (int j = 0; j < n; j++) {
                double temp = alpha * yd[yoff + j];
                if (temp != 0.0) {
                    int col = j * ld;
                    for (int i = 0; i < m; i++) {
                        ad[col + i] += xd[xoff + i] * temp;
                    }
                }
            }
        } else if (A instanceof DenseMatrix32 a && x instanceof Vector32 xv && y instanceof Vector32 yv) {
            float[] ad = a.data;
            int ld = a.ld;
            float[] xd = xv.array;
            int xoff = xv.offset;
            float[] yd = yv.array;
            int yoff = yv.offset;
            float a32 = (float) alpha;
            for (int j = 0; j < n; j++) {
                float temp = a32 * yd[yoff + j];
                if (temp != 0.0f) {
                    int col = j * ld;
                    for (int i = 0; i < m; i++) {
                        ad[col + i] += xd[xoff + i] * temp;
                    }
                }
            }
        } else {
            for (int j = 0; j < n; j++) {
                double temp = alpha * y.get(j);
                if (temp != 0.0) {
                    for (int i = 0; i < m; i++) {
                        A.add(i, j, x.get(i) * temp);
                    }
                }
            }
        }
    }

    /**
     * Packed symmetric matrix-vector multiplication {@code y := alpha * A * x + beta * y}.
     */
    static void spmv(SymmMatrix A, double alpha, Vector x, double beta, Vector y) {
        int n = A.nrow();
        scaleVector(y, n, beta);
        if (alpha == 0.0) {
            return;
        }
        for (int j = 0; j < n; j++) {
            double temp1 = alpha * x.get(j);
            double temp2 = 0.0;
            y.add(j, temp1 * A.get(j, j));
            for (int i = j + 1; i < n; i++) {
                double aij = A.get(i, j);
                y.add(i, temp1 * aij);
                temp2 += aij * x.get(i);
            }
            y.add(j, alpha * temp2);
        }
    }

    /**
     * General band matrix-vector multiplication {@code y := alpha * op(A) * x + beta * y}.
     */
    static void gbmv(Transpose trans, double alpha, BandMatrix A, Vector x, double beta, Vector y) {
        int m = A.nrow();
        int n = A.ncol();
        int kl = A.kl();
        int ku = A.ku();
        if (trans == NO_TRANSPOSE) {
            scaleVector(y, m, beta);
            if (alpha == 0.0) {
                return;
            }
            for (int j = 0; j < n; j++) {
                double temp = alpha * x.get(j);
                if (temp != 0.0) {
                    int iMin = Math.max(0, j - ku);
                    int iMax = Math.min(m - 1, j + kl);
                    for (int i = iMin; i <= iMax; i++) {
                        y.add(i, temp * A.get(i, j));
                    }
                }
            }
        } else {
            scaleVector(y, n, beta);
            if (alpha == 0.0) {
                return;
            }
            for (int j = 0; j < n; j++) {
                double temp = 0.0;
                int iMin = Math.max(0, j - ku);
                int iMax = Math.min(m - 1, j + kl);
                for (int i = iMin; i <= iMax; i++) {
                    temp += A.get(i, j) * x.get(i);
                }
                y.add(j, alpha * temp);
            }
        }
    }

    /**
     * Symmetric band matrix-vector multiplication {@code y := alpha * A * x + beta * y}.
     */
    static void sbmv(double alpha, BandMatrix A, Vector x, double beta, Vector y) {
        int n = A.ncol();
        int k = A.kl();
        scaleVector(y, n, beta);
        if (alpha == 0.0) {
            return;
        }
        if (A.uplo() == UPPER) {
            for (int j = 0; j < n; j++) {
                double temp1 = alpha * x.get(j);
                double temp2 = 0.0;
                int iMin = Math.max(0, j - k);
                for (int i = iMin; i < j; i++) {
                    double aij = A.get(i, j);
                    y.add(i, temp1 * aij);
                    temp2 += aij * x.get(i);
                }
                y.add(j, temp1 * A.get(j, j) + alpha * temp2);
            }
        } else {
            for (int j = 0; j < n; j++) {
                double temp1 = alpha * x.get(j);
                double temp2 = 0.0;
                y.add(j, temp1 * A.get(j, j));
                int iMax = Math.min(n - 1, j + k);
                for (int i = j + 1; i <= iMax; i++) {
                    double aij = A.get(i, j);
                    y.add(i, temp1 * aij);
                    temp2 += aij * x.get(i);
                }
                y.add(j, alpha * temp2);
            }
        }
    }

    /**
     * Scales or zeroes a dense matrix {@code C} by {@code beta}.
     */
    private static void scaleMatrix(DenseMatrix C, double beta) {
        if (beta == 0.0) {
            C.fill(0.0);
        } else if (beta != 1.0) {
            scal(C, beta);
        }
    }

    private static double symmGet(DenseMatrix A, UPLO uplo, int i, int j) {
        if (uplo == LOWER) {
            return i >= j ? A.get(i, j) : A.get(j, i);
        } else {
            return i <= j ? A.get(i, j) : A.get(j, i);
        }
    }

    /**
     * Symmetric matrix-matrix multiplication {@code C := alpha * A * B + beta * C}
     * (when {@code side == LEFT}) or {@code C := alpha * B * A + beta * C}
     * (when {@code side == RIGHT}), where {@code A} is symmetric.
     */
    static void symm(Side side, UPLO uplo, double alpha, DenseMatrix A, DenseMatrix B, double beta, DenseMatrix C) {
        int m = C.nrow();
        int n = C.ncol();
        scaleMatrix(C, beta);
        if (alpha == 0.0) {
            return;
        }
        if (side == LEFT) {
            for (int j = 0; j < n; j++) {
                for (int k = 0; k < m; k++) {
                    double temp1 = alpha * B.get(k, j);
                    double temp2 = 0.0;
                    if (uplo == UPPER) {
                        for (int i = 0; i < k; i++) {
                            double aik = A.get(i, k);
                            C.add(i, j, temp1 * aik);
                            temp2 += B.get(i, j) * aik;
                        }
                        C.add(k, j, temp1 * A.get(k, k) + alpha * temp2);
                    } else {
                        C.add(k, j, temp1 * A.get(k, k));
                        for (int i = k + 1; i < m; i++) {
                            double aik = A.get(i, k);
                            C.add(i, j, temp1 * aik);
                            temp2 += B.get(i, j) * aik;
                        }
                        C.add(k, j, alpha * temp2);
                    }
                }
            }
        } else {
            for (int j = 0; j < n; j++) {
                for (int k = 0; k < n; k++) {
                    double akj = alpha * symmGet(A, uplo, k, j);
                    if (akj != 0.0) {
                        for (int i = 0; i < m; i++) {
                            C.add(i, j, akj * B.get(i, k));
                        }
                    }
                }
            }
        }
    }

    /**
     * General matrix-matrix multiplication {@code C := alpha * op(A) * op(B) + beta * C}.
     */
    static void gemm(double alpha, Transpose transA, DenseMatrix A, Transpose transB, DenseMatrix B, double beta, DenseMatrix C) {
        int m = C.nrow();
        int n = C.ncol();
        int k = (transA == NO_TRANSPOSE) ? A.ncol() : A.nrow();

        scaleMatrix(C, beta);
        if (alpha == 0.0) {
            return;
        }

        if (A instanceof DenseMatrix64 a && B instanceof DenseMatrix64 b && C instanceof DenseMatrix64 c) {
            gemm64(m, n, k, alpha, transA, a, transB, b, c);
        } else if (A instanceof DenseMatrix32 a && B instanceof DenseMatrix32 b && C instanceof DenseMatrix32 c) {
            gemm32(m, n, k, (float) alpha, transA, a, transB, b, c);
        } else {
            gemmGeneric(m, n, k, alpha, transA, A, transB, B, C);
        }
    }

    private static void gemm64(int m, int n, int k, double alpha, Transpose transA, DenseMatrix64 A, Transpose transB, DenseMatrix64 B, DenseMatrix64 C) {
        double[] ad = A.data;
        double[] bd = B.data;
        double[] cd = C.data;
        int lda = A.ld;
        int ldb = B.ld;
        int ldc = C.ld;

        if (transA == NO_TRANSPOSE) {
            if (transB == NO_TRANSPOSE) {
                for (int j = 0; j < n; j++) {
                    int colC = j * ldc;
                    int colB = j * ldb;
                    for (int l = 0; l < k; l++) {
                        double temp = alpha * bd[colB + l];
                        if (temp != 0.0) {
                            int colA = l * lda;
                            for (int i = 0; i < m; i++) {
                                cd[colC + i] += temp * ad[colA + i];
                            }
                        }
                    }
                }
            } else {
                for (int j = 0; j < n; j++) {
                    int colC = j * ldc;
                    for (int l = 0; l < k; l++) {
                        double temp = alpha * bd[l * ldb + j];
                        if (temp != 0.0) {
                            int colA = l * lda;
                            for (int i = 0; i < m; i++) {
                                cd[colC + i] += temp * ad[colA + i];
                            }
                        }
                    }
                }
            }
        } else {
            if (transB == NO_TRANSPOSE) {
                for (int j = 0; j < n; j++) {
                    int colC = j * ldc;
                    int colB = j * ldb;
                    for (int i = 0; i < m; i++) {
                        int colA = i * lda;
                        double sum = 0.0;
                        for (int l = 0; l < k; l++) {
                            sum += ad[colA + l] * bd[colB + l];
                        }
                        cd[colC + i] += alpha * sum;
                    }
                }
            } else {
                for (int j = 0; j < n; j++) {
                    int colC = j * ldc;
                    for (int i = 0; i < m; i++) {
                        int colA = i * lda;
                        double sum = 0.0;
                        for (int l = 0; l < k; l++) {
                            sum += ad[colA + l] * bd[l * ldb + j];
                        }
                        cd[colC + i] += alpha * sum;
                    }
                }
            }
        }
    }

    private static void gemm32(int m, int n, int k, float alpha, Transpose transA, DenseMatrix32 A, Transpose transB, DenseMatrix32 B, DenseMatrix32 C) {
        float[] ad = A.data;
        float[] bd = B.data;
        float[] cd = C.data;
        int lda = A.ld;
        int ldb = B.ld;
        int ldc = C.ld;

        if (transA == NO_TRANSPOSE) {
            if (transB == NO_TRANSPOSE) {
                for (int j = 0; j < n; j++) {
                    int colC = j * ldc;
                    int colB = j * ldb;
                    for (int l = 0; l < k; l++) {
                        float temp = alpha * bd[colB + l];
                        if (temp != 0.0f) {
                            int colA = l * lda;
                            for (int i = 0; i < m; i++) {
                                cd[colC + i] += temp * ad[colA + i];
                            }
                        }
                    }
                }
            } else {
                for (int j = 0; j < n; j++) {
                    int colC = j * ldc;
                    for (int l = 0; l < k; l++) {
                        float temp = alpha * bd[l * ldb + j];
                        if (temp != 0.0f) {
                            int colA = l * lda;
                            for (int i = 0; i < m; i++) {
                                cd[colC + i] += temp * ad[colA + i];
                            }
                        }
                    }
                }
            }
        } else {
            if (transB == NO_TRANSPOSE) {
                for (int j = 0; j < n; j++) {
                    int colC = j * ldc;
                    int colB = j * ldb;
                    for (int i = 0; i < m; i++) {
                        int colA = i * lda;
                        float sum = 0.0f;
                        for (int l = 0; l < k; l++) {
                            sum += ad[colA + l] * bd[colB + l];
                        }
                        cd[colC + i] += alpha * sum;
                    }
                }
            } else {
                for (int j = 0; j < n; j++) {
                    int colC = j * ldc;
                    for (int i = 0; i < m; i++) {
                        int colA = i * lda;
                        float sum = 0.0f;
                        for (int l = 0; l < k; l++) {
                            sum += ad[colA + l] * bd[l * ldb + j];
                        }
                        cd[colC + i] += alpha * sum;
                    }
                }
            }
        }
    }

    private static void gemmGeneric(int m, int n, int k, double alpha, Transpose transA, DenseMatrix A, Transpose transB, DenseMatrix B, DenseMatrix C) {
        for (int j = 0; j < n; j++) {
            for (int l = 0; l < k; l++) {
                double blj = (transB == NO_TRANSPOSE) ? B.get(l, j) : B.get(j, l);
                double temp = alpha * blj;
                if (temp != 0.0) {
                    for (int i = 0; i < m; i++) {
                        double ail = (transA == NO_TRANSPOSE) ? A.get(i, l) : A.get(l, i);
                        C.add(i, j, temp * ail);
                    }
                }
            }
        }
    }
}
