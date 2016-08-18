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
public class QRDecomposition {

    /**
     * Array for internal storage of decomposition.
     */
    private DenseMatrix QR;
    /**
     * Array for internal storage of diagonal of R.
     */
    private double[] Rdiagonal;
    /**
     * Indicate if the matrix is singular.
     */
    private boolean singular;

    /**
     * Constructor. QR Decomposition is computed by Householder reflections.
     * The decomposition will be stored in a new create matrix. The input matrix
     * will not be modified.
     * @param A input matrix
     */
    public QRDecomposition(double[][] A) {
        this(new ColumnMajorMatrix(A));
    }

    /**
     * Constructor. QR Decomposition is computed by Householder reflections.
     * The decomposition will be stored in the input matrix.
     * Otherwise, a new matrix will be allocated to store the decomposition.
     * @param A    input  matrix
     */
    public QRDecomposition(DenseMatrix A) {
        // Initialize.
        int m = A.nrows();
        int n = A.ncols();
        Rdiagonal = new double[n];

        QR = A;

        // Main loop.
        for (int k = 0; k < n; k++) {
            // Compute 2-norm of k-th column without under/overflow.
            double nrm = 0.0;
            for (int i = k; i < m; i++) {
                nrm = Math.hypot(nrm, QR.get(i, k));
            }

            if (nrm != 0.0) {
                // Form k-th Householder vector.
                if (QR.get(k, k) < 0) {
                    nrm = -nrm;
                }
                for (int i = k; i < m; i++) {
                    QR.div(i, k, nrm);
                }
                QR.add(k, k, 1.0);

                // Apply transformation to remaining columns.
                for (int j = k + 1; j < n; j++) {
                    double s = 0.0;
                    for (int i = k; i < m; i++) {
                        s += QR.get(i, k) * QR.get(i, j);
                    }
                    s = -s / QR.get(k, k);
                    for (int i = k; i < m; i++) {
                        QR.add(i, j, s * QR.get(i, k));
                    }
                }
            }
            Rdiagonal[k] = -nrm;
        }

        singular = false;
        for (int j = 0; j < Rdiagonal.length; j++) {
            if (Rdiagonal[j] == 0) {
                singular = true;
                break;
            }
        }
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
    public double[][] getH() {
        int m = QR.nrows();
        int n = QR.ncols();
        double[][] H = new double[m][n];
        for (int i = 0; i < m; i++) {
            for (int j = 0; j < n; j++) {
                if (i >= j) {
                    H[i][j] = QR.get(i, j);
                } else {
                    H[i][j] = 0.0;
                }
            }
        }
        return H;
    }

    /**
     * Returns the Cholesky decomposition of A'A.
     */
    public CholeskyDecomposition toCholesky() {
        int n = QR.ncols();

        double[][] L = new double[n][];
        for (int i = 0; i < n; i++) {
            L[i] = new double[i+1];
            L[i][i] = Rdiagonal[i];

            for (int j = 0; j < i; j++) {
                L[i][j] = QR.get(j, i);
            }
        }

        return CholeskyDecomposition.newInstance(L);
    }

    /**
     * Returns the upper triangular factor.
     */
    public double[][] getR() {
        int m = QR.nrows();
        int n = QR.ncols();
        double[][] R = new double[m][n];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                if (i < j) {
                    R[i][j] = QR.get(i, j);
                } else if (i == j) {
                    R[i][j] = Rdiagonal[i];
                } else {
                    R[i][j] = 0.0;
                }
            }
        }
        return R;
    }

    /**
     * Returns the orthogonal factor.
     */
    public double[][] getQ() {
        int m = QR.nrows();
        int n = QR.ncols();
        double[][] Q = new double[m][n];
        for (int k = n - 1; k >= 0; k--) {
            for (int i = 0; i < m; i++) {
                Q[i][k] = 0.0;
            }
            Q[k][k] = 1.0;
            for (int j = k; j < n; j++) {
                if (QR.get(k, k) != 0) {
                    double s = 0.0;
                    for (int i = k; i < m; i++) {
                        s += QR.get(i, k) * Q[i][j];
                    }
                    s = -s / QR.get(k, k);
                    for (int i = k; i < m; i++) {
                        Q[i][j] += s * QR.get(i, k);
                    }
                }
            }
        }
        return Q;
    }

    /**
     * Returns the matrix pseudo inverse.
     */
    public double[][] inverse() {
        double[][] I = Math.eye(QR.ncols(), QR.nrows());
        solve(I);
        return I;
    }

    /**
     * Solve the least squares A * x = b. On output, b will be overwritten with
     * the solution vector.
     * @param b   right hand side of linear system. On output, b will be
     * overwritten with the solution vector.
     * @exception  RuntimeException  if matrix is rank deficient.
     */
    public void solve(double[] b) {
        if (QR.nrows() != QR.ncols()) {
            throw new UnsupportedOperationException("In-place solver supports only square matrix.");
        }

        solve(b, b);
    }

    /**
     * Solve the least squares A*x = b.
     * @param b   right hand side of linear system.
     * @param x   the solution vector that minimizes the L2 norm of Q*R*x - b.
     * @exception  RuntimeException if matrix is rank deficient.
     */
    public void solve(double[] b, double[] x) {
        int m = QR.nrows();
        int n = QR.ncols();

        if (b.length != m) {
            throw new IllegalArgumentException(String.format("Row dimensions do not agree: A is %d x %d, but b is %d x 1", QR.nrows(), QR.ncols(), b.length));
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
                s += QR.get(i, k) * y[i];
            }
            s = -s / QR.get(k, k);
            for (int i = k; i < m; i++) {
                y[i] += s * QR.get(i, k);
            }
        }

        // Solve R*X = Y;
        for (int k = n - 1; k >= 0; k--) {
            x[k] = y[k] / Rdiagonal[k];
            for (int i = 0; i < k; i++) {
                y[i] -= x[k] * QR.get(i, k);
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
    public void solve(double[][] B) {
        if (QR.nrows() != QR.ncols()) {
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
    public void solve(double[][] B, double[][] X) {
        if (B.length != QR.nrows()) {
            throw new IllegalArgumentException(String.format("Row dimensions do not agree: A is %d x %d, but B is %d x %d", QR.nrows(), QR.nrows(), B.length, B[0].length));
        }

        if (X.length != QR.ncols()) {
            throw new IllegalArgumentException(String.format("Row dimensions do not agree: A is %d x %d, but X is %d x %d", QR.nrows(), QR.nrows(), X.length, X[0].length));
        }

        if (B[0].length != X[0].length) {
            throw new IllegalArgumentException(String.format("B and X column dimension do not agree: B is %d x %d, but X is %d x %d", B.length, B[0].length, X.length, X[0].length));
        }

        if (!isFullColumnRank()) {
            throw new RuntimeException("Matrix is rank deficient.");
        }

        if (X[0].length != B[0].length) {
            throw new IllegalArgumentException("B and X dimensions do not agree.");
        }

        // Copy right hand side
        int m = QR.nrows();
        int n = QR.ncols();
        int nx = B[0].length;

        // Compute Y = transpose(Q)*B
        double[][] Y = B;
        if (B != X) {
            Y = Math.clone(B);
        }
        
        for (int k = 0; k < n; k++) {
            for (int j = 0; j < nx; j++) {
                double s = 0.0;
                for (int i = k; i < m; i++) {
                    s += QR.get(i, k) * Y[i][j];
                }
                s = -s / QR.get(k, k);
                for (int i = k; i < m; i++) {
                    Y[i][j] += s * QR.get(i, k);
                }
            }
        }

        // Solve R*X = Y;
        for (int k = n - 1; k >= 0; k--) {
            for (int j = 0; j < nx; j++) {
                X[k][j] = Y[k][j] / Rdiagonal[k];
            }
            
            for (int i = 0; i < k; i++) {
                for (int j = 0; j < nx; j++) {
                    Y[i][j] -= X[k][j] * QR.get(i, k);
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
        int m = QR.nrows();
        int n = QR.ncols();

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

        Rdiagonal[0] += u[0] * v[0];
        for (int i = 1; i < n; i++) {
            QR.add(0, i, u[0] * v[i]);
        }

        for (int i = 0; i < k; i++) {
            rotate(i, Rdiagonal[i], -QR.get(i+1, i));
        }

        for (int i = 0; i < n; i++) {
            if (Rdiagonal[i] == 0.0) {
                singular = true;
            }
        }
    }

    /*
     * Utility used by update for Jacobi rotation on rows i and i+1 of QR.
     * a and b are the paramters of the rotation:
     * cos &theta; = a / sqrt(a<sup>2</sup>+b<sup>2</sub>)
     * sin &theta; = b / sqrt(a<sup>2</sup>+b<sup>2</sub>)
     */
    private void rotate(int i, double a, double b) {
        int n = QR.ncols();

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
            y = i == j ? Rdiagonal[i] : QR.get(i, j);
            w = QR.get(i+1, j);
            QR.set(i, j, c * y - s * w);
            QR.set(i+1, j, s * y + c * w);
        }

        for (int j = 0; j < n; j++) {
            y = QR.get(i, j);
            w = QR.get(i+1, j);
            QR.set(i, j, c * y - s * w);
            QR.set(i+1, j, s * y + c * w);
        }
    }
}
