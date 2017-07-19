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
 * Singular Value Decomposition.
 * <p>
 * For an m-by-n matrix A with m &ge; n, the singular value decomposition is
 * an m-by-n orthogonal matrix U, an n-by-n diagonal matrix &Sigma;, and
 * an n-by-n orthogonal matrix V so that A = U*&Sigma;*V'.
 * <p>
 * For m &lt; n, only the first m columns of V are computed and &Sigma; is m-by-m.
 * <p>
 * The singular values, &sigma;<sub>k</sub> = &Sigma;<sub>kk</sub>, are ordered
 * so that &sigma;<sub>0</sub> &ge; &sigma;<sub>1</sub> &ge; ... &ge; &sigma;<sub>n-1</sub>.
 * <p>
 * The singular value decompostion always exists. The matrix condition number
 * and the effective numerical rank can be computed from this decomposition.
 * <p>
 * SVD is a very powerful technique for dealing with sets of equations or matrices
 * that are either singular or else numerically very close to singular. In many
 * cases where Gaussian elimination and LU decomposition fail to give satisfactory
 * results, SVD will diagnose precisely what the problem is. SVD is also the
 * method of choice for solving most linear least squares problems.
 * <p>
 * Applications which employ the SVD include computing the pseudoinverse, least
 * squares fitting of data, matrix approximation, and determining the rank,
 * range and null space of a matrix. The SVD is also applied extensively to
 * the study of linear inverse problems, and is useful in the analysis of
 * regularization methods such as that of Tikhonov. It is widely used in
 * statistics where it is related to principal component analysis. Yet another
 * usage is latent semantic indexing in natural language text processing.
 *
 * @author Haifeng Li
 */
public class SVD {

    /**
     * Arrays for internal storage of left singular vectors U.
     */
    protected DenseMatrix U;
    /**
     * Arrays for internal storage of right singular vectors V.
     */
    protected DenseMatrix V;
    /**
     * Array for internal storage of singular values.
     */
    protected double[] s;
    /**
     * Is this a full decomposition?
     */
    protected boolean full;
    /**
     * The number of rows.
     */
    protected int m;
    /**
     * The number of columns.
     */
    protected int n;
    /**
     * Threshold of estimated roundoff.
     */
    protected double tol;

    /**
     * Constructor.
     */
    public SVD(DenseMatrix U, DenseMatrix V, double[] s) {
        this.U = U;
        this.V = V;
        this.s = s;

        m = U.nrows();
        n = V.nrows();
        full = s.length == Math.min(m, n);
        tol = 0.5 * Math.sqrt(m + n + 1.0) * s[0] * Math.EPSILON;
    }

    /**
     * Returns the left singular vectors
     */
    public DenseMatrix getU() {
        return U;
    }

    /**
     * Returns the right singular vectors
     */
    public DenseMatrix getV() {
        return V;
    }

    /**
     * Returns the one-dimensional array of singular values, ordered by
     * from largest to smallest.
     */
    public double[] getSingularValues() {
        return s;
    }

    /**
     * Returns the diagonal matrix of singular values
     */
    public DenseMatrix getS() {
        DenseMatrix S = Matrix.zeros(U.nrows(), V.nrows());

        for (int i = 0; i < s.length; i++) {
            S.set(i, i, s[i]);
        }

        return S;
    }

    /**
     * Returns the L2 matrix norm. The largest singular value.
     */
    public double norm() {
        return s[0];
    }

    /**
     * Returns the effective numerical matrix rank. The number of nonnegligible
     * singular values.
     */
    public int rank() {
        if (!full) {
            throw new IllegalStateException("This is not a FULL singular value decomposition.");
        }

        int r = 0;
        for (int i = 0; i < s.length; i++) {
            if (s[i] > tol) {
                r++;
            }
        }
        return r;
    }

    /**
     * Returns the dimension of null space. The number of negligible
     * singular values.
     */
    public int nullity() {
        if (!full) {
            throw new IllegalStateException("This is not a FULL singular value decomposition.");
        }

        int r = 0;
        for (int i = 0; i < s.length; i++) {
            if (s[i] <= tol) {
                r++;
            }
        }
        return r;
    }

    /**
     * Returns the L<sub>2</sub> norm condition number, which is max(S) / min(S).
     * A system of equations is considered to be well-conditioned if a small
     * change in the coefficient matrix or a small change in the right hand
     * side results in a small change in the solution vector. Otherwise, it is
     * called ill-conditioned. Condition number is defined as the product of
     * the norm of A and the norm of A<sup>-1</sup>. If we use the usual
     * L<sub>2</sub> norm on vectors and the associated matrix norm, then the
     * condition number is the ratio of the largest singular value of matrix
     * A to the smallest. Condition number depends on the underlying norm.
     * However, regardless of the norm, it is always greater or equal to 1.
     * If it is close to one, the matrix is well conditioned. If the condition
     * number is large, then the matrix is said to be ill-conditioned. A matrix
     * that is not invertible has the condition number equal to infinity.
     */
    public double condition() {
        if (!full) {
            throw new IllegalStateException("This is not a FULL singular value decomposition.");
        }

        return (s[0] <= 0.0 || s[n - 1] <= 0.0) ? Double.POSITIVE_INFINITY : s[0] / s[n - 1];
    }

    /**
     * Returns a matrix of which columns give an orthonormal basis for the range space.
     */
    public DenseMatrix range() {
        if (!full) {
            throw new IllegalStateException("This is not a FULL singular value decomposition.");
        }

        int nr = 0;
        DenseMatrix rnge = Matrix.zeros(m, rank());
        for (int j = 0; j < n; j++) {
            if (s[j] > tol) {
                for (int i = 0; i < m; i++) {
                    rnge.set(i, nr, U.get(i, j));
                }
                nr++;
            }
        }
        return rnge;
    }

    /**
     * Returns a matrix of which columns give an orthonormal basis for the null space.
     */
    public DenseMatrix nullspace() {
        if (!full) {
            throw new IllegalStateException("This is not a FULL singular value decomposition.");
        }

        int nn = 0;
        DenseMatrix nullsp = Matrix.zeros(n, nullity());
        for (int j = 0; j < n; j++) {
            if (s[j] <= tol) {
                for (int jj = 0; jj < n; jj++) {
                    nullsp.set(jj, nn, V.get(jj, j));
                }
                nn++;
            }
        }
        return nullsp;
    }

    /**
     * Returns the Cholesky decomposition of A'A.
     */
    public Cholesky CholeskyOfAtA() {
        DenseMatrix VD = Matrix.zeros(V.nrows(), V.ncols());
        for (int i = 0; i < V.nrows(); i++) {
            for (int j = 0; j < V.ncols(); j++) {
                VD.set(i, j, V.get(i, j) * s[j]);
            }
        }

        return new Cholesky(VD.aat());
    }

    /**
     * Solve the least squares A*x = b.
     * @param b   right hand side of linear system.
     * @param x   the output solution vector that minimizes the L2 norm of Q*R*x - b.
     * @exception  RuntimeException if matrix is rank deficient.
     */
    public void solve(double[] b, double[] x) {
        if (!full) {
            throw new IllegalStateException("This is not a FULL singular value decomposition.");
        }

        if (b.length != m || x.length != n) {
            throw new IllegalArgumentException("Dimensions do not agree.");
        }

        double[] tmp = new double[n];
        for (int j = 0; j < n; j++) {
            double r = 0.0;
            if (s[j] > tol) {
                for (int i = 0; i < m; i++) {
                    r += U.get(i, j) * b[i];
                }
                r /= s[j];
            }
            tmp[j] = r;
        }

        for (int j = 0; j < n; j++) {
            double r = 0.0;
            for (int jj = 0; jj < n; jj++) {
                r += V.get(j, jj) * tmp[jj];
            }
            x[j] = r;
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
        if (!full) {
            throw new IllegalStateException("This is not a FULL singular value decomposition.");
        }

        if (B.nrows() != m) {
            throw new IllegalArgumentException("Dimensions do not agree.");
        }

        double[] b = new double[m];
        double[] x = new double[n];
        int p = B.ncols();
        for (int j = 0; j < p; j++) {
            for (int i = 0; i < m; i++) {
                b[i] = B.get(i, j);
            }

            solve(b, x);
            for (int i = 0; i < n; i++) {
                B.set(i, j, x[i]);
            }
        }
    }
}
