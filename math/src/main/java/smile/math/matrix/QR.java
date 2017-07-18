/*******************************************************************************
 * Copyright (c) 2010 Haifeng Li
 *   
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *  
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *******************************************************************************/
package smile.math.matrix;

import smile.math.Math;

/**
 * For an m-by-n matrix A with m &ge; n, the QR decomposition is an m-by-n
 * orthogonal matrix Q and an n-by-n upper triangular matrix R such that
 * A = Q*R.
 * <p>
 * The QR decomposition always exists, even if the matrix does not have
 * full rank. The primary use of the QR decomposition is in the least squares
 * solution of non-square systems of simultaneous linear equations, where
 * {@link #isSingular()} has to be false.
 * <p>
 * QR decomposition is also the basis for a particular eigenvalue algorithm,
 * the QR algorithm.
 *
 * @author Haifeng Li
 */
public class QR {

    /**
     * Array for internal storage of decomposition.
     */
    protected DenseMatrix qr;
    /**
     * The diagonal of R for this implementation.
     * For netlib based QR, this is the scales for the reflectors.
     */
    protected double[] tau;
    /**
     * Indicate if the matrix is singular.
     */
    protected boolean singular;

    /**
     * Constructor.
     */
    public QR(DenseMatrix qr, double[] tau, boolean singular) {
        this.qr = qr;
        this.tau = tau;
        this.singular = singular;
    }

    /**
     * Returns true if the matrix is singular (not full column rank).
     */
    public boolean isSingular() {
        return singular;
    }

    /**
     * Returns the Cholesky decomposition of A'A.
     */
    public Cholesky CholeskyOfAtA() {
        int n = qr.ncols();

        DenseMatrix L = Matrix.zeros(n, n);
        for (int i = 0; i < n; i++) {
            L.set(i, i, tau[i]);

            for (int j = 0; j < i; j++) {
                L.set(i, j, qr.get(j, i));
            }
        }

        return new Cholesky(L);
    }

    /**
     * Returns the upper triangular factor.
     */
    public DenseMatrix getR() {
        int n = qr.ncols();
        DenseMatrix R = Matrix.zeros(n, n);
        for (int i = 0; i < n; i++) {
            R.set(i, i, tau[i]);
            for (int j = i+1; j < n; j++) {
                R.set(i, j, qr.get(i, j));
            }
        }
        return R;
    }

    /**
     * Returns the orthogonal factor.
     */
    public DenseMatrix getQ() {
        int m = qr.nrows();
        int n = qr.ncols();
        DenseMatrix Q = Matrix.zeros(m, n);
        for (int k = n - 1; k >= 0; k--) {
            Q.set(k, k, 1.0);
            for (int j = k; j < n; j++) {
                if (qr.get(k, k) != 0) {
                    double s = 0.0;
                    for (int i = k; i < m; i++) {
                        s += qr.get(i, k) * Q.get(i, j);
                    }
                    s = -s / qr.get(k, k);
                    for (int i = k; i < m; i++) {
                        Q.add(i, j, s * qr.get(i, k));
                    }
                }
            }
        }
        return Q;
    }

   /**
     * Solve the least squares A*x = b.
     * @param b   right hand side of linear system.
    *  @param x   the output solution vector that minimizes the L2 norm of Q*R*x - b.
     * @exception  RuntimeException if matrix is rank deficient.
     */
    public void solve(double[] b, double[] x) {
        if (b.length != qr.nrows()) {
            throw new IllegalArgumentException(String.format("Row dimensions do not agree: A is %d x %d, but B is %d x 1", qr.nrows(), qr.nrows(), b.length));
        }

        if (x.length != qr.ncols()) {
            throw new IllegalArgumentException("A and x dimensions don't match.");
        }

        if (singular) {
            throw new RuntimeException("Matrix is rank deficient.");
        }

        double[] B = b.clone();
        solve(Matrix.newInstance(B));
        System.arraycopy(B, 0, x, 0, x.length);
    }

    /**
     * Solve the least squares A * X = B. B will be overwritten with the solution
     * matrix on output.
     * @param B    right hand side of linear system. B will be overwritten with
     * the solution matrix on output.
     * @exception  RuntimeException  Matrix is rank deficient.
     */
    public void solve(DenseMatrix B) {
        if (B.nrows() != qr.nrows()) {
            throw new IllegalArgumentException(String.format("Row dimensions do not agree: A is %d x %d, but B is %d x %d", qr.nrows(), qr.nrows(), B.nrows(), B.ncols()));
        }

        if (singular) {
            throw new RuntimeException("Matrix is rank deficient.");
        }

        int m = qr.nrows();
        int n = qr.ncols();
        int nrhs = B.ncols();

        // Compute Y = Q' * B
        for (int k = 0; k < n; k++) {
            for (int j = 0; j < nrhs; j++) {
                double s = 0.0;
                for (int i = k; i < m; i++) {
                    s += qr.get(i, k) * B.get(i, j);
                }
                s = -s / qr.get(k, k);
                for (int i = k; i < m; i++) {
                    B.add(i, j, s * qr.get(i, k));
                }
            }
        }

        // Solve R*X = Y;
        for (int k = n - 1; k >= 0; k--) {
            for (int j = 0; j < nrhs; j++) {
                B.set(k, j, B.get(k, j) / tau[k]);
            }
            
            for (int i = 0; i < k; i++) {
                for (int j = 0; j < nrhs; j++) {
                    B.sub(i, j, B.get(k, j) * qr.get(i, k));
                }
            }
        }
    }

    /**
     * Rank-1 update of the QR decomposition for A = A + u * v.
     * Instead of a full decomposition from scratch in O(N<sup>3</sup>),
     * we can update the QR factorization in O(N<sup>2</sup>).
     * Note that u is modified during the update.
     */
    public void update(double[] u, double[] v) {
        int m = qr.nrows();
        int n = qr.ncols();

        if (u.length != m || v.length != n) {
            throw new IllegalArgumentException("u.length = " + u.length + " v.length = " + v.length);
        }

        int k;
        for (k = m - 1; k >= 0; k--) {
            if (u[k] != 0.0) {
                break;
            }
        }

        if (k < 0) {
            return;
        }

        for (int i = k - 1; i >= 0; i--) {
            rotate(i, u[i], -u[i + 1]);

            if (u[i] == 0.0) {
                u[i] = Math.abs(u[i + 1]);
            } else if (Math.abs(u[i]) > Math.abs(u[i + 1])) {
                u[i] = Math.abs(u[i]) * Math.sqrt(1.0 + Math.sqr(u[i + 1] / u[i]));
            } else {
                u[i] = Math.abs(u[i + 1]) * Math.sqrt(1.0 + Math.sqr(u[i] / u[i + 1]));
            }
        }

        tau[0] += u[0] * v[0];
        for (int i = 1; i < n; i++) {
            qr.add(0, i, u[0] * v[i]);
        }

        for (int i = 0; i < k; i++) {
            rotate(i, tau[i], -qr.get(i+1, i));
        }

        for (int i = 0; i < n; i++) {
            if (tau[i] == 0.0) {
                singular = true;
            }
        }
    }

    /*
     * Utility used by update for Jacobi rotation on rows i and i+1 of qr.
     * a and b are the parameters of the rotation:
     * cos &theta; = a / sqrt(a<sup>2</sup>+b<sup>2</sub>)
     * sin &theta; = b / sqrt(a<sup>2</sup>+b<sup>2</sub>)
     */
    private void rotate(int i, double a, double b) {
        int n = qr.ncols();

        double c, fact, s, w, y;
        if (a == 0.0) {
            c = 0.0;
            s = (b >= 0.0 ? 1.0 : -1.0);
        } else if (Math.abs(a) > Math.abs(b)) {
            fact = b / a;
            c = Math.copySign(1.0 / Math.sqrt(1.0 + (fact * fact)), a);
            s = fact * c;
        } else {
            fact = a / b;
            s = Math.copySign(1.0 / Math.sqrt(1.0 + (fact * fact)), b);
            c = fact * s;
        }

        for (int j = i; j < n; j++) {
            y = i == j ? tau[i] : qr.get(i, j);
            w = qr.get(i+1, j);
            qr.set(i, j, c * y - s * w);
            qr.set(i+1, j, s * y + c * w);
        }

        for (int j = 0; j < n; j++) {
            y = qr.get(i, j);
            w = qr.get(i+1, j);
            qr.set(i, j, c * y - s * w);
            qr.set(i+1, j, s * y + c * w);
        }
    }
}
