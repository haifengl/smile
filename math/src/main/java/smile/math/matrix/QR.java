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
 * {@link #isFullColumnRank} has to be true.
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
    private DenseMatrix qr;
    /**
     * Array for internal storage of diagonal of Q or R (depending on the implementation).
     */
    private double[] tau;
    /**
     * Indicate if the matrix is singular.
     */
    private boolean singular;

    /**
     * Constructor.
     */
    public QR(DenseMatrix qr, double[] tau, boolean singular) {
        this.qr = qr;
        this.tau = tau;
        this.singular = singular;
    }

    /**
     * Returns true if the matrix is full column rank.
     */
    public boolean isFullColumnRank() {
        return !singular;
    }

    /**
     * Returns true if the matrix is singular.
     */
    public boolean isSingular() {
        return singular;
    }

    /**
     * Returns the Householder vectors.
     * @return  Lower trapezoidal matrix whose columns define the reflections
     */
    public DenseMatrix getH() {
        int m = qr.nrows();
        int n = qr.ncols();
        DenseMatrix H = Matrix.zeros(m, n);
        for (int i = 0; i < m; i++) {
            for (int j = 0; j <= i; j++) {
                H.set(i, j, qr.get(i, j));
            }
        }
        return H;
    }

    /**
     * Returns the Cholesky decomposition of A'A.
     */
    public CholeskyDecomposition toCholesky() {
        int n = qr.ncols();

        double[][] L = new double[n][];
        for (int i = 0; i < n; i++) {
            L[i] = new double[i+1];
            L[i][i] = tau[i];

            for (int j = 0; j < i; j++) {
                L[i][j] = qr.get(j, i);
            }
        }

        return CholeskyDecomposition.newInstance(L);
    }

    /**
     * Returns the upper triangular factor.
     */
    public DenseMatrix getR() {
        int m = qr.nrows();
        int n = qr.ncols();
        DenseMatrix R = Matrix.zeros(m, n);
        for (int i = 0; i < n; i++) {
            R.set(i, i, tau[i]);
            for (int j = i; j < n; j++) {
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
     * Returns the matrix pseudo inverse.
     */
    public DenseMatrix inverse() {
        DenseMatrix inv = Matrix.eye(qr.ncols(), qr.nrows());
        solve(inv);
        return inv;
    }

    /**
     * Solve the least squares A*x = b.
     * @param b   right hand side of linear system.
     * @param x   the solution vector that minimizes the L2 norm of Q*R*x - b.
     * @exception  RuntimeException if matrix is rank deficient.
     */
    public void solve(double[] b, double[] x) {
        int m = qr.nrows();
        int n = qr.ncols();

        if (b.length != m) {
            throw new IllegalArgumentException(String.format("Row dimensions do not agree: A is %d x %d, but b is %d x 1", qr.nrows(), qr.ncols(), b.length));
        }

        if (x.length != n) {
            throw new IllegalArgumentException("A and x dimensions do not agree.");
        }

        if (!isFullColumnRank()) {
            throw new RuntimeException("Matrix is rank deficient.");
        }

        // Compute Y = transpose(Q) * b
        double[] y = b;
        if (b != x) {
            y = b.clone();
        }
        
        for (int k = 0; k < n; k++) {
            double s = 0.0;
            for (int i = k; i < m; i++) {
                s += qr.get(i, k) * y[i];
            }
            s = -s / qr.get(k, k);
            for (int i = k; i < m; i++) {
                y[i] += s * qr.get(i, k);
            }
        }

        // Solve R*X = Y;
        for (int k = n - 1; k >= 0; k--) {
            x[k] = y[k] / tau[k];
            for (int i = 0; i < k; i++) {
                y[i] -= x[k] * qr.get(i, k);
            }
        }
    }

    /**
     * Solve the least squares A * X = B. B will be overwritten with the solution
     * matrix on output.
     * @param B    right hand side of linear system. B will be overwritten with
     * the solution matrix on output.
     * @exception  RuntimeException  Matrix is rank deficient.
     */
    public void solve(DenseMatrix B) {
        if (qr.nrows() != qr.ncols()) {
            throw new UnsupportedOperationException("In-place solver supports only square matrix.");
        }

        solve(B, B);
    }

    /**
     * Solve the least squares A * X = B.
     * @param B    right hand side of linear system.
     * @param X    the solution matrix that minimizes the L2 norm of Q*R*X - B.
     * @exception  RuntimeException  Matrix is rank deficient.
     */
    public void solve(DenseMatrix B, DenseMatrix X) {
        if (B.nrows() != qr.nrows()) {
            throw new IllegalArgumentException(String.format("Row dimensions do not agree: A is %d x %d, but B is %d x %d", qr.nrows(), qr.nrows(), B.nrows(), B.ncols()));
        }

        if (X.nrows() != qr.ncols()) {
            throw new IllegalArgumentException(String.format("Row dimensions do not agree: A is %d x %d, but X is %d x %d", qr.nrows(), qr.nrows(), X.nrows(), X.ncols()));
        }

        if (B.ncols() != X.ncols()) {
            throw new IllegalArgumentException(String.format("B and X column dimension do not agree: B is %d x %d, but X is %d x %d", B.nrows(), B.ncols(), X.nrows(), X.ncols()));
        }

        if (!isFullColumnRank()) {
            throw new RuntimeException("Matrix is rank deficient.");
        }

        if (X.ncols() != B.ncols()) {
            throw new IllegalArgumentException("B and X dimensions do not agree.");
        }

        // Copy right hand side
        int m = qr.nrows();
        int n = qr.ncols();
        int nx = B.ncols();

        // Compute Y = transpose(Q)*B
        if (B != X) {
            for (int i = 0; i < n; i++) {
                for (int j = 0; j < nx; j++) {
                    X.set(i, j, B.get(i, j));
                }
            }
        }
        
        for (int k = 0; k < n; k++) {
            for (int j = 0; j < nx; j++) {
                double s = 0.0;
                for (int i = k; i < m; i++) {
                    s += qr.get(i, k) * X.get(i, j);
                }
                s = -s / qr.get(k, k);
                for (int i = k; i < m; i++) {
                    X.add(i, j, s * qr.get(i, k));
                }
            }
        }

        // Solve R*X = Y;
        for (int k = n - 1; k >= 0; k--) {
            for (int j = 0; j < nx; j++) {
                X.set(k, j, X.get(k, j) / tau[k]);
            }
            
            for (int i = 0; i < k; i++) {
                for (int j = 0; j < nx; j++) {
                    X.sub(i, j, X.get(k, j) * qr.get(i, k));
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
     * a and b are the paramters of the rotation:
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
