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
public class SingularValueDecomposition {

    /**
     * Arrays for internal storage of left singular vectors U.
     */
    private DenseMatrix U;
    /**
     * Arrays for internal storage of right singular vectors V.
     */
    private DenseMatrix V;
    /**
     * Array for internal storage of singular values.
     */
    private double[] s;
    /**
     * Is this a full decomposition?
     */
    private boolean full;
    /**
     * The number of rows.
     */
    private int m;
    /**
     * The number of columns.
     */
    private int n;
    /**
     * Threshold of estimated roundoff.
     */
    private double tol;

    /**
     * Private constructor.
     */
    SingularValueDecomposition(DenseMatrix U, DenseMatrix V, double[] s) {
        this(U, V, s, true);
    }

    /**
     * Private constructor.
     */
    SingularValueDecomposition(DenseMatrix U, DenseMatrix V, double[] s, boolean full) {
        this.U = U;
        this.V = V;
        this.s = s;
        this.full = full;

        m = U.nrows();
        n = V.ncols();
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
        DenseMatrix S = new ColumnMajorMatrix(U.nrows(), V.nrows());

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
        DenseMatrix rnge = new ColumnMajorMatrix(m, rank());
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
        DenseMatrix nullsp = new ColumnMajorMatrix(n, nullity());
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
    public CholeskyDecomposition toCholesky() {
        DenseMatrix VD = new ColumnMajorMatrix(V.nrows(), V.ncols());
        for (int i = 0; i < V.nrows(); i++) {
            for (int j = 0; j < V.ncols(); j++) {
                VD.set(i, j, V.get(i, j) * s[j]);
            }
        }

        return new CholeskyDecomposition(VD.aat());
    }

    /**
     * Solve A * x = b using the pseudoinverse of A as obtained by SVD.
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
     * Solve A * X = B using the pseudoinverse of A as obtained by SVD.
     */
    public void solve(double[][] B, double[][] X) {
        if (!full) {
            throw new IllegalStateException("This is not a FULL singular value decomposition.");
        }

        if (B.length != n || X.length != n || B[0].length != X[0].length) {
            throw new IllegalArgumentException("Dimensions do not agree.");
        }

        double[] xx = new double[n];
        int p = B[0].length;
        for (int j = 0; j < p; j++) {
            for (int i = 0; i < n; i++) {
                xx[i] = B[i][j];
            }

            solve(xx, xx);
            for (int i = 0; i < n; i++) {
                X[i][j] = xx[i];
            }
        }
    }

    /**
     * Constructor. The decomposition will be stored in a new create
     * matrix. The input matrix will not be modified.
     * @param A    input matrix
     */
    public SingularValueDecomposition(double[][] A) {
        this(new ColumnMajorMatrix(A));
    }

    /**
     * Returns the singular value decomposition. Note that the input matrix
     * A will hold U on output.
     * @param A  rectangular matrix. Row number should be equal to or larger
     * than column number for current implementation.
     */
    public SingularValueDecomposition(DenseMatrix A) {
        int m = A.nrows();
        int n = A.ncols();

        boolean flag;
        int i, its, j, jj, k, l = 0, nm = 0;
        double anorm, c, f, g, h, s, scale, x, y, z;
        g = scale = anorm = 0.0;

        U = A;
        V = new ColumnMajorMatrix(n, n);
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
                        s += U.get(k, i) * U.get(k, i);
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
                        s += U.get(i, k) * U.get(i, k);
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
                    if (l == 0 || Math.abs(rv1[l]) <= Math.EPSILON * anorm) {
                        flag = false;
                        break;
                    }
                    if (Math.abs(w[nm]) <= Math.EPSILON * anorm) {
                        break;
                    }
                }

                if (flag) {
                    c = 0.0;
                    s = 1.0;
                    for (i = l; i < k + 1; i++) {
                        f = s * rv1[i];
                        rv1[i] = c * rv1[i];
                        if (Math.abs(f) <= Math.EPSILON * anorm) {
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
                            U.set(j,  i, z * c - y * s);
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
                    throw new IllegalStateException("no convergence in 30 iterations");
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

        // order singular values
        int inc = 1;
        double sw;
        double[] su = new double[m], sv = new double[n];

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
                if (U.get(i, k) < 0.) {
                    s++;
                }
            }
            for (j = 0; j < n; j++) {
                if (V.get(j, k) < 0.) {
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

        this.s = w;
        this.full = true;
        this.m = A.nrows();
        this.n = A.ncols();
        this.tol = 0.5 * Math.sqrt(m + n + 1.0) * this.s[0] * Math.EPSILON;
    }
}
