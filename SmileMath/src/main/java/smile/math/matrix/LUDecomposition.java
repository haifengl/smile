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
 * For an m-by-n matrix A with m &ge; n, the LU decomposition is an m-by-n
 * unit lower triangular matrix L, an n-by-n upper triangular matrix U,
 * and a permutation vector piv of length m so that A(piv,:) = L*U.
 * If m &lt; n, then L is m-by-m and U is m-by-n.
 * <p>
 * The LU decompostion with pivoting always exists, even if the matrix is
 * singular. The primary use of the LU decomposition is in the solution of
 * square systems of simultaneous linear equations if it is not singular.
 * <p>
 * This decomposition can also be used to calculate the determinant.
 *
 * @author Haifeng Li
 */
public class LUDecomposition {
    /**
     * Array for internal storage of decomposition.
     */
    private double[][] LU;

    /**
     * pivot sign.
     */
    private int pivsign;

    /**
     * Internal storage of pivot vector.
     */
    private int[] piv;

    /**
     * Constructor. The decomposition will be stored in a new create
     * matrix. The input matrix will not be modified.
     * @param A    rectangular matrix
     */
    public LUDecomposition(double[][] A) {
        this(A, false);
    }

    /**
     * Constructor. The user can specify if the decomposition takes in
     * place, i.e. if the decomposition will be stored in the input matrix.
     * Otherwise, a new matrix will be allocated to store the decomposition.
     * @param  A   rectangular matrix
     * @param  overwrite  if the decomposition will be taken in place. If true,
     * the decomposition will be stored in the input matrix to save space. It
     * is very useful in practice if the matrix is huge. Otherwise, a new
     * matrix will created to store the decomposition.
     */
    public LUDecomposition(double[][] A, boolean overwrite) {
        // Use a "left-looking", dot-product, Crout/Doolittle algorithm.
        int m = A.length;
        int n = A[0].length;

        LU = A;
        if (!overwrite) {
            LU = new double[m][n];
            for (int i = 0; i < m; i++)
                System.arraycopy(A[i], 0, LU[i], 0, n);
        }

        piv = new int[m];
        for (int i = 0; i < m; i++) {
            piv[i] = i;
        }

        pivsign = 1;
        double[] LUrowi;
        double[] LUcolj = new double[m];

        for (int j = 0; j < n; j++) {

            // Make a copy of the j-th column to localize references.
            for (int i = 0; i < m; i++) {
                LUcolj[i] = LU[i][j];
            }

            // Apply previous transformations.
            for (int i = 0; i < m; i++) {
                LUrowi = LU[i];

                // Most of the time is spent in the following dot product.

                int kmax = Math.min(i, j);
                double s = 0.0;
                for (int k = 0; k < kmax; k++) {
                    s += LUrowi[k] * LUcolj[k];
                }

                LUrowi[j] = LUcolj[i] -= s;
            }

            // Find pivot and exchange if necessary.
            int p = j;
            for (int i = j + 1; i < m; i++) {
                if (Math.abs(LUcolj[i]) > Math.abs(LUcolj[p])) {
                    p = i;
                }
            }
            if (p != j) {
                for (int k = 0; k < n; k++) {
                    double t = LU[p][k];
                    LU[p][k] = LU[j][k];
                    LU[j][k] = t;
                }
                int k = piv[p];
                piv[p] = piv[j];
                piv[j] = k;
                pivsign = -pivsign;
            }

            // Compute multipliers.
            if (j < m & LU[j][j] != 0.0) {
                for (int i = j + 1; i < m; i++) {
                    LU[i][j] /= LU[j][j];
                }
            }
        }
    }

    /**
     * Returns true if the matrix is singular or false otherwise.
     */
    public boolean isSingular() {
        int n = LU[0].length;
        for (int j = 0; j < n; j++) {
            if (LU[j][j] == 0) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns the lower triangular factor.
     */
    public double[][] getL() {
        int m = LU.length;
        int n = LU[0].length;
        double[][] L = new double[m][n];
        for (int i = 0; i < m; i++) {
            for (int j = 0; j < n; j++) {
                if (i > j) {
                    L[i][j] = LU[i][j];
                } else if (i == j) {
                    L[i][j] = 1.0;
                } else {
                    L[i][j] = 0.0;
                }
            }
        }
        return L;
    }

    /**
     * Returns the upper triangular factor.
     */
    public double[][] getU() {
        int m = LU.length;
        int n = LU[0].length;
        double[][] U = new double[m][n];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                if (i <= j) {
                    U[i][j] = LU[i][j];
                } else {
                    U[i][j] = 0.0;
                }
            }
        }
        return U;
    }

    /**
     * Returns the pivot permutation vector.
     */
    public int[] getPivot() {
        return piv;
    }

    /**
     * Returns the matrix determinant
     */
    public double det() {
        int m = LU.length;
        int n = LU[0].length;

        if (m != n)
            throw new IllegalArgumentException(String.format("Matrix is not square: %d x %d", m, n));

        double d = (double) pivsign;
        for (int j = 0; j < n; j++) {
            d *= LU[j][j];
        }
        
        return d;
    }

    /**
     * Returns the matrix inverse. For pseudo inverse, use QRDecomposition.
     */
    public double[][] inverse() {
        int m = LU.length;
        int n = LU[0].length;

        if (m != n)
            throw new IllegalArgumentException(String.format("Matrix is not square: %d x %d", m, n));

        double[][] I = Math.eye(n);
        solve(I);
        return I;
    }

    /**
     * Solve A * x = b. b will be overwritten with the solution vector on output.
     * @param b   right hand side of linear system. On output, it will be
     * overwritten with the solution vector
     * @exception  RuntimeException  if matrix is singular.
     */
    public void solve(double[] b) {
        solve(b.clone(), b);
    }

    /**
     * Solve A * x = b.
     * @param b   right hand side of linear system.
     * @param x   the solution vector.
     * @exception  RuntimeException  if matrix is singular.
     */
    public void solve(double[] b, double[] x) {
        int m = LU.length;
        int n = LU[0].length;

        if (m != n) {
            throw new UnsupportedOperationException("The matrix is not square.");
        }

        if (b.length != m) {
            throw new IllegalArgumentException(String.format("Row dimensions do not agree: A is %d x %d, but b is %d x 1", LU.length, LU[0].length, b.length));
        }
        
        if (b.length != x.length) {
            throw new IllegalArgumentException("b and x dimensions do not agree.");
        }

        if (isSingular()) {
            throw new RuntimeException("Matrix is singular.");
        }

        // Copy right hand side with pivoting
        for (int i = 0; i < m; i++) {
            x[i] = b[piv[i]];
        }

        // Solve L*Y = B(piv,:)
        for (int k = 0; k < n; k++) {
            for (int i = k + 1; i < n; i++) {
                x[i] -= x[k] * LU[i][k];
            }
        }

        // Solve U*X = Y;
        for (int k = n - 1; k >= 0; k--) {
            x[k] /= LU[k][k];

            for (int i = 0; i < k; i++) {
                x[i] -= x[k] * LU[i][k];
            }
        }
    }

    /**
     * Solve A * X = B. B will be overwritten with the solution matrix on output.
     * @param B  right hand side of linear system. On output, B will be
     * overwritten with the solution matrix.
     * @throws  RuntimeException  if matrix is singular.
     */
    public void solve(double[][] B) {
        solve(B, B);
    }

    /**
     * Solve A * X = B.
     * @param B   right hand side of linear system.
     * @param X   the solution matrix.
     * @throws  RuntimeException  if matrix is singular.
     */
    public void solve(double[][] B, double[][] X) {
        int m = LU.length;
        int n = LU[0].length;

        if (B.length != m)
            throw new IllegalArgumentException(String.format("Row dimensions do not agree: A is %d x %d, but B is %d x %d", LU.length, LU[0].length, B.length, B[0].length));

        if (isSingular()) {
            throw new RuntimeException("Matrix is singular.");
        }

        if (X.length != B.length || X[0].length != B[0].length) {
            throw new IllegalArgumentException("B and X dimensions do not agree.");
        }

        // Copy right hand side with pivoting
        int nx = B[0].length;
        if (X == B) {
            double[][] x = new double[m][];
            for (int i = 0; i < m; i++) {
                x[i] = B[piv[i]];
            }
            System.arraycopy(x, 0, X, 0, m);
        } else {
            for (int i = 0; i < m; i++) {
                System.arraycopy(B[piv[i]], 0, X[i], 0, nx);
            }
        }

        // Solve L*Y = B(piv,:)
        for (int k = 0; k < n; k++) {
            for (int i = k + 1; i < n; i++) {
                for (int j = 0; j < nx; j++) {
                    X[i][j] -= X[k][j] * LU[i][k];
                }
            }
        }
        
        // Solve U*X = Y;
        for (int k = n - 1; k >= 0; k--) {
            for (int j = 0; j < nx; j++) {
                X[k][j] /= LU[k][k];
            }
            
            for (int i = 0; i < k; i++) {
                for (int j = 0; j < nx; j++) {
                    X[i][j] -= X[k][j] * LU[i][k];
                }
            }
        }
    }
}
