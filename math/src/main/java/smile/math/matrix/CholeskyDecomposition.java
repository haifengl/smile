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

import java.util.Arrays;
import smile.math.Math;

/**
 * Cholesky decomposition is a decomposition of a symmetric, positive-definite
 * matrix into a lower triangular matrix L and the transpose of the lower
 * triangular matrix such that A = L*L'. The lower triangular matrix is the
 * Cholesky triangle of the original, positive-definite matrix. When it is
 * applicable, the Cholesky decomposition is roughly twice as efficient as the LU
 * decomposition for solving systems of linear equations.
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
 * <p>
 * If the matrix is not positive definite, an exception will be thrown out from
 * the method decompose().
 *
 * @author Haifeng Li
 */
public class CholeskyDecomposition {

    /**
     * Array for internal storage of decomposition.
     */
    private double[][] L;

    /**
     * Constructor. Used by newInstance().
     */
    private CholeskyDecomposition() {
        
    }
    
    /**
     * Constructor. The decomposition is already available, e.g. from QR
     * decomposition.
     * @param  L   decomposition matrix.
     */
    public static CholeskyDecomposition newInstance(double[][] L) {
        CholeskyDecomposition cholesky = new CholeskyDecomposition();
        cholesky.L = L;
        return cholesky;
    }

    /**
     * Constructor. Cholesky decomposition for symmetric and positive definite matrix.
     * A new matrix will allocated to store the decomposition.
     * @param  A   square, symmetric matrix. Only the lower triangular part
     * will be used in the decomposition. The user can just store this half part
     * to save space.
     * @throws IllegalArgumentException if the matrix is not positive definite.
     */
    public CholeskyDecomposition(double[][] A) {
        this(new Matrix(A));
    }

    /**
     * Constructor. Cholesky decomposition for symmetric and positive definite matrix. The
     * user can specify if the decomposition takes in place, i.e. if
     * the decomposition will be stored in the input matrix.
     * Otherwise, a new matrix will be allocated to store the decomposition.
     *
     * @param  A   square symmetric matrix. Only the lower triangular part
     * will be used in the decomposition. The user can just store this half part
     * to save space.
     * @throws IllegalArgumentException if the matrix is not positive definite.
     */
    public CholeskyDecomposition(DenseMatrix A) {
        int n = A.nrows();
        L = new double[n][];
        for (int i = 0; i < n; i++) {
            L[i] = new double[i + 1];
        }

        // Main loop.
        for (int j = 0; j < n; j++) {
            double[] Lrowj = L[j];
            double d = 0.0;
            for (int k = 0; k < j; k++) {
                double[] Lrowk = L[k];
                double s = 0.0;
                for (int i = 0; i < k; i++) {
                    s += Lrowk[i] * Lrowj[i];
                }
                Lrowj[k] = s = (A.get(j, k) - s) / L[k][k];
                d = d + s * s;
            }
            d = A.get(j, j) - d;

            if (d < 0.0) {
                throw new IllegalArgumentException("The matrix is not positive definite.");
            }

            L[j][j] = Math.sqrt(d);
        }
    }

    /**
     * Returns lower triangular factor.
     */
    public double[][] getL() {
        return L;
    }

    /**
     * Returns the matrix determinant
     */
    public double det() {
        double d = 1.0;
        for (int i = 0; i < L.length; i++) {
            d *= L[i][i];
        }

        return d * d;
    }

    /**
     * Returns the matrix inverse.
     */
    public double[][] inverse() {
        double[][] I = Math.eye(L.length);
        solve(I);
        return I;
    }

    /**
     * Solve the linear system A * x = b. On output, b will be overwritten with
     * the solution vector.
     * @param  b   the right hand side of linear systems. On output, b will be
     * overwritten with solution vector.
     */
    public void solve(double[] b) {
        solve(b, b);
    }

    /**
     * Solve the linear system A * x = b.
     * @param b   the right hand side of linear systems.
     * @param x   the solution vector.
     */
    public void solve(double[] b, double[] x) {
        if (b.length != L.length) {
            throw new IllegalArgumentException(String.format("Row dimensions do not agree: A is %d x %d, but B is %d x 1", L.length, L.length, b.length));
        }

        if (b.length != x.length) {
            throw new IllegalArgumentException("b and x dimensions do not agree.");
        }

        int n = b.length;

        if (x != b) {
            System.arraycopy(b, 0, x, 0, n);
        }

        // Solve L*Y = B;
        for (int k = 0; k < n; k++) {
            for (int i = 0; i < k; i++) {
                x[k] -= x[i] * L[k][i];
            }
            x[k] /= L[k][k];
        }

        // Solve L'*X = Y;
        for (int k = n - 1; k >= 0; k--) {
            for (int i = k + 1; i < n; i++) {
                x[k] -= x[i] * L[i][k];
            }
            x[k] /= L[k][k];
        }
    }

    /**
     * Solve the linear system A * X = B. On output, B will be overwritten with
     * the solution matrix.
     * @param  B   the right hand side of linear systems.
     */
    public void solve(double[][] B) {
        solve(B, B);
    }

    /**
     * Solve the linear system A * X = B.
     * @param  B   the right hand side of linear systems.
     * @param  X   the solution matrix.
     */
    public void solve(double[][] B, double[][] X) {
        if (B.length != L.length) {
            throw new IllegalArgumentException(String.format("Row dimensions do not agree: A is %d x %d, but B is %d x %d", L.length, L.length, B.length, B[0].length));
        }

        if (X.length != B.length || X[0].length != B[0].length) {
            throw new IllegalArgumentException("B and X dimensions do not agree.");
        }

        int n = B.length;
        int nx = B[0].length;

        if (X != B) {
            for (int i = 0; i < n; i++) {
                System.arraycopy(B[i], 0, X[i], 0, nx);
            }
        }

        // Solve L*Y = B;
        for (int k = 0; k < n; k++) {
            for (int j = 0; j < nx; j++) {
                for (int i = 0; i < k; i++) {
                    X[k][j] -= X[i][j] * L[k][i];
                }
                X[k][j] /= L[k][k];
            }
        }

        // Solve L'*X = Y;
        for (int k = n - 1; k >= 0; k--) {
            for (int j = 0; j < nx; j++) {
                for (int i = k + 1; i < n; i++) {
                    X[k][j] -= X[i][j] * L[i][k];
                }
                X[k][j] /= L[k][k];
            }
        }
    }
}

