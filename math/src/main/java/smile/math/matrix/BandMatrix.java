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
 * Given a n-by-n band matrix with m<sub>1</sub> rows below the diagonal and m<sub>2</sub> rows above.
 * The matrix is compactly stored in an array A[0,n-1][0,m<sub>1</sub>+m<sub>2</sub>]. The diagonal
 * elements are in A[0,n-1][m<sub>1</sub>]. Subdiagonal elements are in A[j,n-1][0,m<sub>1</sub>-1]
 * with j &gt; 0 appropriate to the number of elements on each subdiagonal.
 * Superdiagonal elements are in A[0,j][m<sub>1</sub>+1,m<sub>2</sub>+m<sub>2</sub>]
 * with j &lt; n-1 appropriate to the number of elements on each superdiagonal.
 * 
 * @author Haifeng Li
 */
public class BandMatrix extends Matrix implements LinearSolver {
    private static final long serialVersionUID = 1L;

    /**
     * Compact store of band matrix as A[0, n-1][0, m1+m2].
     */
    private double[][] A;

    /**
     * The size of matrix.
     */
    private int n;

    /**
     * The number of subdiagonal rows.
     */
    private int m1;

    /**
     * The number of superdiagonal rows.
     */
    private int m2;

    /**
     * The upper triangular matrix of LU decomposition.
     */
    private double[][] au;

    /**
     * The lower triangular matrix of LU decomposition.
     */
    private double[][] al;

    /**
     * index[0,n-1] records the row permutation effected by the partial pivoting.
     */
    private int[] index;

    /**
     * d is +/-1 depending on whether the number of row interchanges was even
     * or odd. respectively.
     */
    private double d;


    /**
     * Constructor of an n-by-n band-diagonal matrix A with m subdiagonal
     * rows and m superdiagonal rows.
     * @param n the dimensionality of matrix.
     * @param m the number of subdiagonal and superdiagonal rows.
     */
    public BandMatrix(int n, int m) {
        this(n, m, m);
    }

    /**
     * Constructor of an n-by-n band-diagonal matrix A with m<sub>1</sub> subdiagonal
     * rows and m<sub>2</sub> superdiagonal rows.
     * @param n the dimensionality of matrix.
     * @param m1 the number of subdiagonal rows.
     * @param m2 the number of superdiagonal rows.
     */
    public BandMatrix(int n, int m1, int m2) {
        this.n = n;
        this.m1 = m1;
        this.m2 = m2;
        A = new double[n][m1+m2+1];
    }

    @Override
    public int nrows() {
        return n;
    }

    @Override
    public int ncols() {
        return n;
    }

    @Override
    public double get(int i, int j) {
        return A[i][j-i+m1];
    }

    public BandMatrix set(int i, int j, double x) {
        A[i][j-i+m1] = x;
        return this;
    }

    /**
     * Returns the matrix transpose.
     * @return the transpose of matrix.
     */
    @Override
    public BandMatrix transpose() {
        BandMatrix at = new BandMatrix(n, m2, m1);
        for (int i = 0; i < n; i++) {
            for (int j = i-m2; j <= i+m1; j++) {
                if (j >= 0 && j < n) {
                    at.set(i, j, get(j, i));
                }
            }
        }

        return at;
    }

    @Override
    public BandMatrix ata() {
        throw new UnsupportedOperationException();
    }

    @Override
    public BandMatrix aat() {
        throw new UnsupportedOperationException();
    }

    /**
     * Returns the matrix determinant.
     */
    public double det() {
        if (au == null) {
            decompose();
        }
        
        double dd = d;
        for (int i = 0; i < n; i++) {
            dd *= au[i][0];
        }

        return dd;
    }

    /**
     * LU decomposition.
     */
    public void decompose() {
        final double TINY = 1.0e-40;

        int mm = m1 + m2 + 1;
        index = new int[n];
        au = new double[n][mm];
        al = new double[n][m1];

        for (int i = 0; i < A.length; i++) {
            System.arraycopy(A[i], 0, au[i], 0, A[i].length);
        }

        double dum;
        int l = m1;
        for (int i = 0; i < m1; i++) {
            for (int j = m1 - i; j < mm; j++) {
                au[i][j - l] = au[i][j];
            }
            l--;
            for (int j = mm - l - 1; j < mm; j++)
                au[i][j] = 0.0;
        }

        d = 1.0;
        l = m1;
        for (int k = 0; k < n; k++) {
            dum = au[k][0];
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

            index[k] = i + 1;
            if (dum == 0.0) {
                au[k][0] = TINY;
            }

            if (i != k) {
                d = -d;
                // swap au[k], au[i]
                double[] swap = au[k];
                au[k] = au[i];
                au[i] = swap;
            }

            for (i = k + 1; i < l; i++) {
                dum = au[i][0] / au[k][0];
                al[k][i - k - 1] = dum;
                for (int j = 1; j < mm; j++) {
                    au[i][j - 1] = au[i][j] - dum * au[k][j];
                }
                au[i][mm - 1] = 0.0;
            }
        }
    }

    @Override
    public double[] ax(double[] x, double[] y) {
        if (x.length != n) {
            throw new IllegalArgumentException(String.format("Row dimensions do not agree: A is %d x %d, but x is %d x 1", n, n, x.length));
        }

        if (y.length != n) {
            throw new IllegalArgumentException(String.format("Row dimensions do not agree: A is %d x %d, but y is %d x 1", n, n, y.length));
        }

        Arrays.fill(y, 0.0);
        for (int i = 0; i < n; i++) {
            int k = i - m1;
            int tmploop = Math.min(m1 + m2 + 1, n - k);
            for (int j = Math.max(0, -k); j < tmploop; j++) {
                y[i] += A[i][j] * x[j + k];
            }
        }

        return y;
    }

    @Override
    public double[] axpy(double[] x, double[] y) {
        if (x.length != n) {
            throw new IllegalArgumentException(String.format("Row dimensions do not agree: A is %d x %d, but x is %d x 1", n, n, x.length));
        }

        if (y.length != n) {
            throw new IllegalArgumentException(String.format("Row dimensions do not agree: A is %d x %d, but y is %d x 1", n, n, y.length));
        }

        for (int i = 0; i < n; i++) {
            int k = i - m1;
            int tmploop = Math.min(m1 + m2 + 1, n - k);
            for (int j = Math.max(0, -k); j < tmploop; j++) {
                y[i] += A[i][j] * x[j + k];
            }
        }

        return y;
    }

    @Override
    public double[] axpy(double[] x, double[] y, double b) {
        if (x.length != n) {
            throw new IllegalArgumentException(String.format("Row dimensions do not agree: A is %d x %d, but x is %d x 1", n, n, x.length));
        }

        if (y.length != n) {
            throw new IllegalArgumentException(String.format("Row dimensions do not agree: A is %d x %d, but y is %d x 1", n, n, y.length));
        }

        for (int i = 0; i < n; i++) {
            int k = i - m1;
            int tmploop = Math.min(m1 + m2 + 1, n - k);
            y[i] *= b;
            for (int j = Math.max(0, -k); j < tmploop; j++) {
                y[i] += A[i][j] * x[j + k];
            }
        }

        return y;
    }

    @Override
    public double[] atx(double[] x, double[] y) {
        if (x.length != n) {
            throw new IllegalArgumentException(String.format("Column dimensions do not agree: A is %d x %d, but x is 1 x %d", n, n, x.length));
        }

        if (y.length != n) {
            throw new IllegalArgumentException(String.format("Column dimensions do not agree: A is %d x %d, but y is 1 x %d", n, n, y.length));
        }

        Arrays.fill(y, 0.0);
        for (int i = 0; i < n; i++) {
            for (int j = -m2; j <= m1; j++) {
                if (i + j >= 0 && i + j < n) {
                    y[i] += A[i + j][m1 - j] * x[i + j];
                }
            }
        }

        return y;
    }

    @Override
    public double[] atxpy(double[] x, double[] y) {
        if (x.length != n) {
            throw new IllegalArgumentException(String.format("Column dimensions do not agree: A is %d x %d, but x is 1 x %d", n, n, x.length));
        }

        if (y.length != n) {
            throw new IllegalArgumentException(String.format("Column dimensions do not agree: A is %d x %d, but y is 1 x %d", n, n, y.length));
        }

        for (int i = 0; i < n; i++) {
            for (int j = -m2; j <= m1; j++) {
                if (i + j >= 0 && i + j < n) {
                    y[i] += A[i + j][m1 - j] * x[i + j];
                }
            }
        }

        return y;
    }

    @Override
    public double[] atxpy(double[] x, double[] y, double b) {
        if (x.length != n) {
            throw new IllegalArgumentException(String.format("Column dimensions do not agree: A is %d x %d, but x is 1 x %d", n, n, x.length));
        }

        if (y.length != n) {
            throw new IllegalArgumentException(String.format("Column dimensions do not agree: A is %d x %d, but y is 1 x %d", n, n, y.length));
        }

        for (int i = 0; i < n; i++) {
            y[i] *= b;
            for (int j = -m2; j <= m1; j++) {
                if (i + j >= 0 && i + j < n) {
                    y[i] += A[i + j][m1 - j] * x[i + j];
                }
            }
        }

        return y;
    }

    @Override
    public double[] diag() {
        double[] d = new double[n];
        for (int i = 0; i < n; i++) {
            d[i] = A[i][m1];
        }

        return d;
    }

    /**
     * Solve A*x = b.
     * @param b   a vector with as many rows as A.
     * @param x   is output vector so that L*U*X = b(piv,:)
     * @throws RuntimeException if matrix is singular.
     */
    public double[] solve(double[] b, double[] x) {
        if (b.length != n) {
            throw new IllegalArgumentException(String.format("Row dimensions do not agree: A is %d x %d, but b is %d x 1", n, n, b.length));
        }

        if (b.length != x.length) {
            throw new IllegalArgumentException("b and x dimensions do not agree.");
        }

        if (au == null) {
            decompose();
        }
        System.arraycopy(b, 0, x, 0, n);

        int mm = m1 + m2 + 1;
        int l = m1;

        for (int k = 0; k < n; k++) {
            int j = index[k] - 1;

            if (j != k) {
                // swap x[k] and x[j]
                double swap = x[k];
                x[k] = x[j];
                x[j] = swap;
            }

            if (l < n) {
                l++;
            }

            for (j = k + 1; j < l; j++) {
                x[j] -= al[k][j - k - 1] * x[k];
            }
        }

        l = 1;
        for (int i = n - 1; i >= 0; i--) {
            double dum = x[i];
            for (int k = 1; k < l; k++) {
                dum -= au[i][k] * x[k + i];
            }
            x[i] = dum / au[i][0];
            if (l < mm) {
                l++;
            }
        }

        return x;
    }

    /**
     * Iteratively improve a solution to linear equations.
     *
     * @param b right hand side of linear equations.
     * @param x a solution to linear equations.
     */
    public void improve(double[] b, double[] x) {
        if (b.length != n || x.length != n) {
            throw new IllegalArgumentException(String.format("Row dimensions do not agree: A is %d x %d, but b is %d x 1 and x is %d x 1", n, n, b.length, x.length));
        }

        // Calculate the right-hand side, accumulating the residual
        // in higher precision.
        double[] r = b.clone();
        axpy(x, r, -1.0);

        // Solve for the error term.
        solve(r, r);

        // Subtract the error from the old solution.
        for (int i = 0; i < n; i++) {
            x[i] -= r[i];
        }
    }
}
