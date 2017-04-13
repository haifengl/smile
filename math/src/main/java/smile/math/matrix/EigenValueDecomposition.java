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

import smile.math.Complex;
import smile.math.Math;

/**
 * Eigen decomposition of a real matrix. Eigen decomposition is the factorization
 * of a matrix into a canonical form, whereby the matrix is represented in terms
 * of its eigenvalues and eigenvectors:
 * <p>
 * A = V*D*V<sup>-1</sup>
 * <p>
 * If A is symmetric, then A = V*D*V' where the eigenvalue matrix D is
 * diagonal and the eigenvector matrix V is orthogonal.
 * <p>
 * Given a linear transformation A, a non-zero vector x is defined to be an
 * eigenvector of the transformation if it satisfies the eigenvalue equation
 * <p>
 * A x = &lambda; x
 * <p>
 * for some scalar &lambda;. In this situation, the scalar &lambda; is called
 * an eigenvalue of A corresponding to the eigenvector x.
 * <p>
 * The word eigenvector formally refers to the right eigenvector, which is
 * defined by the above eigenvalue equation A x = &lambda; x, and is the most
 * commonly used eigenvector. However, the left eigenvector exists as well, and
 * is defined by x A = &lambda; x.
 * <p>
 * Let A be a real n-by-n matrix with strictly positive entries a<sub>ij</sub>
 * &gt; 0. Then the following statements hold.
 * <ol>
 * <li> There is a positive real number r, called the Perron-Frobenius
 * eigenvalue, such that r is an eigenvalue of A and any other eigenvalue &lambda;
 * (possibly complex) is strictly smaller than r in absolute value,
 * |&lambda;| &lt; r.
 * <li> The Perron-Frobenius eigenvalue is simple: r is a simple root of the
 * characteristic polynomial of A. Consequently, both the right and the left
 * eigenspace associated to r is one-dimensional.
 * <li> There exists a left eigenvector v of A associated with r (row vector)
 * having strictly positive components. Likewise, there exists a right
 * eigenvector w associated with r (column vector) having strictly positive
 * components.
 * <li> The left eigenvector v (respectively right w) associated with r, is the
 * only eigenvector which has positive components, i.e. for all other
 * eigenvectors of A there exists a component which is not positive.
 * </ol>
 * <p>
 * A stochastic matrix, probability matrix, or transition matrix is used to
 * describe the transitions of a Markov chain. A right stochastic matrix is
 * a square matrix each of whose rows consists of nonnegative real numbers,
 * with each row summing to 1. A left stochastic matrix is a square matrix
 * whose columns consist of nonnegative real numbers whose sum is 1. A doubly
 * stochastic matrix where all entries are nonnegative and all rows and all
 * columns sum to 1. A stationary probability vector &pi; is defined as a
 * vector that does not change under application of the transition matrix;
 * that is, it is defined as a left eigenvector of the probability matrix,
 * associated with eigenvalue 1: &pi;P = &pi;. The Perron-Frobenius theorem
 * ensures that such a vector exists, and that the largest eigenvalue
 * associated with a stochastic matrix is always 1. For a matrix with strictly
 * positive entries, this vector is unique. In general, however, there may be
 * several such vectors.
 * 
 * @author Haifeng Li
 */
public class EigenValueDecomposition {
    private static final Logger logger = LoggerFactory.getLogger(EigenValueDecomposition.class);

    /**
     * Array of (real part of) eigenvalues.
     */
    private double[] d;
    /**
     * Array of imaginary part of eigenvalues.
     */
    private double[] e;
    /**
     * Array of eigen vectors.
     */
    private DenseMatrix V;

    /**
     * Private constructor.
     * @param V eigenvectors.
     * @param d eigenvalues.
     */
    EigenValueDecomposition(DenseMatrix V, double[] d) {
        this.V = V;
        this.d = d;
    }

    /**
     * Private constructor.
     * @param V eigenvectors.
     * @param d real part of eigenvalues.
     * @param e imaginary part of eigenvalues.
     */
    EigenValueDecomposition(DenseMatrix V, double[] d, double[] e) {
        this.V = V;
        this.d = d;
        this.e = e;
    }

    /**
     * Returns the eigenvector matrix, ordered by eigen values from largest to smallest.
     */
    public DenseMatrix getEigenVectors() {
        return V;
    }

    /**
     * Returns the eigenvalues, ordered from largest to smallest.
     */
    public double[] getEigenValues() {
        return d;
    }

    /**
     * Returns the real parts of the eigenvalues, ordered in real part from
     * largest to smallest.
     */
    public double[] getRealEigenValues() {
        return d;
    }

    /**
     * Returns the imaginary parts of the eigenvalues, ordered in real part
     * from largest to smallest.
     */
    public double[] getImagEigenValues() {
        return e;
    }

    /**
     * Returns the block diagonal eigenvalue matrix whose diagonal are the real
     * part of eigenvalues, lower subdiagonal are positive imaginary parts, and
     * upper subdiagonal are negative imaginary parts.
     */
    public DenseMatrix getD() {
        int n = V.nrows();
        DenseMatrix D = new ColumnMajorMatrix(n, n);
        for (int i = 0; i < n; i++) {
            D.set(i, i, d[i]);
            if (e != null) {
                if (e[i] > 0) {
                    D.set(i, i + 1, e[i]);
                } else if (e[i] < 0) {
                    D.set(i, i - 1, e[i]);
                }
            }
        }
        return D;
    }

    /**
     * Full eigen value decomposition of a square matrix. Note that the input
     * matrix will be altered during decomposition.
     * @param A    square matrix which will be altered during decomposition.
     */
    public EigenValueDecomposition(double[][] A) {
        this(new ColumnMajorMatrix(A));
    }

    /**
     * Full eigen value decomposition of a square matrix. Note that the input
     * matrix will be altered during decomposition.
     * @param A    square matrix which will be altered during decomposition.
     * @param symmetric if the matrix A is symmetric.
     */
    public EigenValueDecomposition(double[][] A, boolean symmetric) {
        this(new ColumnMajorMatrix(A), symmetric);
    }

    /**
     * Full eigen value decomposition of a square matrix. Note that the input
     * matrix will be altered during decomposition.
     * @param A    square matrix which will be altered during decomposition.
     * @param symmetric if the matrix A is symmetric.
     * @param onlyValues if true, only compute eigenvalues; the default is to compute eigenvectors also.
     */
    public EigenValueDecomposition(double[][] A, boolean symmetric, boolean onlyValues) {
        this(new ColumnMajorMatrix(A), symmetric, onlyValues);
    }

    /**
     * Full eigen value decomposition of a square matrix. Note that the input
     * matrix will be altered during decomposition.
     * @param A    square matrix which will be altered during decomposition.
     */
    public EigenValueDecomposition(DenseMatrix A) {
        this(A, false);
    }

    /**
     * Full eigen value decomposition of a square matrix. Note that the input
     * matrix will be altered during decomposition.
     * @param A    square matrix which will be altered during decomposition.
     * @param symmetric if the matrix A is symmetric.
     */
    public EigenValueDecomposition(DenseMatrix A, boolean symmetric) {
        this(A, symmetric, false);
    }

    /**
     * Full eigen value decomposition of a square matrix. Note that the input
     * matrix will be altered during decomposition.
     * @param A    square matrix which will be altered during decomposition.
     * @param symmetric if the matrix A is symmetric.
     * @param onlyValues if true, only compute eigenvalues; the default is to compute eigenvectors also.
     */
    public EigenValueDecomposition(DenseMatrix A, boolean symmetric, boolean onlyValues) {
        if (A.nrows() != A.ncols()) {
            throw new IllegalArgumentException(String.format("Matrix is not square: %d x %d", A.nrows(), A.ncols()));
        }

        int n = A.nrows();
        d = new double[n];
        e = new double[n];

        if (symmetric) {
            V = A;

            if (onlyValues) {
                // Tridiagonalize.
                tred(V, d, e);
                // Diagonalize.
                tql(d, e, n);
                V = null;
            } else {
                // Tridiagonalize.
                tred2(V, d, e);
                // Diagonalize.
                tql2(V, d, e, n);
            }

        } else {
            double[] scale = balance(A);
            int[] perm = elmhes(A);
            if (onlyValues) {
                hqr(A, d, e);
                sort(d, e);
                V = null;
            } else {
                V = new ColumnMajorMatrix(n, n);
                for (int i = 0; i < n; i++) {
                    V.set(i, i, 1.0);
                }

                eltran(A, V, perm);

                hqr2(A, V, d, e);
                balbak(V, scale);
                sort(d, e, V);
            }
        }
    }

    /**
     * Symmetric Householder reduction to tridiagonal form.
     */
    private static void tred(DenseMatrix V, double[] d, double[] e) {
        int n = V.nrows();
        for (int i = 0; i < n; i++) {
            d[i] = V.get(n - 1, i);
        }

        // Householder reduction to tridiagonal form.
        for (int i = n - 1; i > 0; i--) {

            // Scale to avoid under/overflow.
            double scale = 0.0;
            double h = 0.0;
            for (int k = 0; k < i; k++) {
                scale = scale + Math.abs(d[k]);
            }
            if (scale == 0.0) {
                e[i] = d[i - 1];
                for (int j = 0; j < i; j++) {
                    d[j] = V.get(i - 1, j);
                    V.set(i, j, 0.0);
                    V.set(j, i, 0.0);
                }
            } else {

                // Generate Householder vector.
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
                h = h - f * g;
                d[i - 1] = f - g;
                for (int j = 0; j < i; j++) {
                    e[j] = 0.0;
                }

                // Apply similarity transformation to remaining columns.
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
     * Symmetric Householder reduction to tridiagonal form.
     */
    private static void tred2(DenseMatrix V, double[] d, double[] e) {
        int n = V.nrows();
        for (int i = 0; i < n; i++) {
            d[i] = V.get(n - 1, i);
        }

        // Householder reduction to tridiagonal form.
        for (int i = n - 1; i > 0; i--) {

            // Scale to avoid under/overflow.
            double scale = 0.0;
            double h = 0.0;
            for (int k = 0; k < i; k++) {
                scale = scale + Math.abs(d[k]);
            }
            if (scale == 0.0) {
                e[i] = d[i - 1];
                for (int j = 0; j < i; j++) {
                    d[j] = V.get(i - 1, j);
                    V.set(i, j, 0.0);
                    V.set(j, i, 0.0);
                }
            } else {

                // Generate Householder vector.
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
                h = h - f * g;
                d[i - 1] = f - g;
                for (int j = 0; j < i; j++) {
                    e[j] = 0.0;
                }

                // Apply similarity transformation to remaining columns.
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

        // Accumulate transformations.
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
                        V.sub(k, j,  g * d[k]);
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
     *
     * The routine works extremely well in practice. The number of iterations for the first few
     * eigenvalues might be 4 or 5, say, but meanwhile the off-diagonal elements in the lower right-hand
     * corner have been reduced too. The later eigenvalues are liberated with very little work. The
     * average number of iterations per eigenvalue is typically 1.3 - 1.6. The operation count per
     * iteration is O(n), with a fairly large effective coefficient, say, ~20n. The total operation count
     * for the diagonalization is then ~20n * (1.3 - 1.6)n = ~30n^2. If the eigenvectors are required,
     * there is an additional, much larger, workload of about 3n^3 operations.
     *
     * @param d on input, it contains the diagonal elements of the tridiagonal matrix.
     * On output, it contains the eigenvalues.
     * @param e on input, it contains the subdiagonal elements of the tridiagonal
     * matrix, with e[0] arbitrary. On output, its contents are destroyed.
     * @param n the size of all parameter arrays.
     */
    private static void tql(double[] d, double[] e, int n) {
        for (int i = 1; i < n; i++) {
            e[i - 1] = e[i];
        }
        e[n - 1] = 0.0;

        double f = 0.0;
        double tst1 = 0.0;
        for (int l = 0; l < n; l++) {

            // Find small subdiagonal element
            tst1 = Math.max(tst1, Math.abs(d[l]) + Math.abs(e[l]));
            int m = l;
            for (; m < n; m++) {
                if (Math.abs(e[m]) <= Math.EPSILON * tst1) {
                    break;
                }
            }

            // If m == l, d[l] is an eigenvalue,
            // otherwise, iterate.
            if (m > l) {
                int iter = 0;
                do {
                    if (++iter >= 30) {
                        throw new RuntimeException("Too many iterations");
                    }

                    // Compute implicit shift
                    double g = d[l];
                    double p = (d[l + 1] - d[l]) / (2.0 * e[l]);
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
                    f = f + h;

                    // Implicit QL transformation.
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

                // Check for convergence.
                } while (Math.abs(e[l]) > Math.EPSILON * tst1);
            }
            d[l] = d[l] + f;
            e[l] = 0.0;
        }

        // Sort eigenvalues and corresponding vectors.
        for (int i = 0; i < n - 1; i++) {
            int k = i;
            double p = d[i];
            for (int j = i + 1; j < n; j++) {
                if (d[j] > p) {
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
     * Tridiagonal QL Implicit routine for computing eigenvalues and eigenvectors of a symmetric,
     * real, tridiagonal matrix.
     *
     * The routine works extremely well in practice. The number of iterations for the first few
     * eigenvalues might be 4 or 5, say, but meanwhile the off-diagonal elements in the lower right-hand
     * corner have been reduced too. The later eigenvalues are liberated with very little work. The
     * average number of iterations per eigenvalue is typically 1.3 - 1.6. The operation count per
     * iteration is O(n), with a fairly large effective coefficient, say, ~20n. The total operation count
     * for the diagonalization is then ~20n * (1.3 - 1.6)n = ~30n^2. If the eigenvectors are required,
     * there is an additional, much larger, workload of about 3n^3 operations.
     *
     * @param V on input, it contains the identity matrix. On output, the kth column
     * of V returns the normalized eigenvector corresponding to d[k].
     * @param d on input, it contains the diagonal elements of the tridiagonal matrix.
     * On output, it contains the eigenvalues.
     * @param e on input, it contains the subdiagonal elements of the tridiagonal
     * matrix, with e[0] arbitrary. On output, its contents are destroyed.
     * @param n the size of all parameter arrays.
     */
    static void tql2(DenseMatrix V, double[] d, double[] e, int n) {
        for (int i = 1; i < n; i++) {
            e[i - 1] = e[i];
        }
        e[n - 1] = 0.0;

        double f = 0.0;
        double tst1 = 0.0;
        for (int l = 0; l < n; l++) {

            // Find small subdiagonal element
            tst1 = Math.max(tst1, Math.abs(d[l]) + Math.abs(e[l]));
            int m = l;
            for (; m < n; m++) {
                if (Math.abs(e[m]) <= Math.EPSILON * tst1) {
                    break;
                }
            }

            // If m == l, d[l] is an eigenvalue,
            // otherwise, iterate.
            if (m > l) {
                int iter = 0;
                do {
                    if (++iter >= 30) {
                        throw new RuntimeException("Too many iterations");
                    }

                    // Compute implicit shift
                    double g = d[l];
                    double p = (d[l + 1] - d[l]) / (2.0 * e[l]);
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
                    f = f + h;

                    // Implicit QL transformation.
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

                        // Accumulate transformation.
                        for (int k = 0; k < n; k++) {
                            h = V.get(k, i + 1);
                            V.set(k, i + 1, s * V.get(k, i) + c * h);
                            V.set(k, i,     c * V.get(k, i) - s * h);
                        }
                    }
                    p = -s * s2 * c3 * el1 * e[l] / dl1;
                    e[l] = s * p;
                    d[l] = c * p;

                // Check for convergence.
                } while (Math.abs(e[l]) > Math.EPSILON * tst1);
            }
            d[l] = d[l] + f;
            e[l] = 0.0;
        }

        // Sort eigenvalues and corresponding vectors.
        for (int i = 0; i < n - 1; i++) {
            int k = i;
            double p = d[i];
            for (int j = i + 1; j < n; j++) {
                if (d[j] > p) {
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
     * Given a square matrix, this routine replaces it by a balanced matrix with
     * identical eigenvalues. A symmetric matrix is already balanced and is
     * unaffected by this procedure.
     */
    private static double[] balance(DenseMatrix A) {
        double sqrdx = Math.RADIX * Math.RADIX;

        int n = A.nrows();

        double[] scale = new double[n];
        for (int i = 0; i < n; i++) {
            scale[i] = 1.0;
        }

        boolean done = false;
        while (!done) {
            done = true;
            for (int i = 0; i < n; i++) {
                double r = 0.0, c = 0.0;
                for (int j = 0; j < n; j++) {
                    if (j != i) {
                        c += Math.abs(A.get(j, i));
                        r += Math.abs(A.get(i, j));
                    }
                }
                if (c != 0.0 && r != 0.0) {
                    double g = r / Math.RADIX;
                    double f = 1.0;
                    double s = c + r;
                    while (c < g) {
                        f *= Math.RADIX;
                        c *= sqrdx;
                    }
                    g = r * Math.RADIX;
                    while (c > g) {
                        f /= Math.RADIX;
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
     * Form the eigenvectors of a real nonsymmetric matrix by back transforming
     * those of the corresponding balanced matrix determined by balance.
     */
    private static void balbak(DenseMatrix V, double[] scale) {
        int n = V.nrows();
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                V.mul(i, j, scale[i]);
            }
        }
    }

    /**
     * Reduce a real nonsymmetric matrix to upper Hessenberg form.
     */
    private static int[] elmhes(DenseMatrix A) {
        int n = A.nrows();
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
     * Accumulate the stabilized elementary similarity transformations used
     * in the reduction of a real nonsymmetric matrix to upper Hessenberg form by elmhes.
     */
    private static void eltran(DenseMatrix A, DenseMatrix V, int[] perm) {
        int n = A.nrows();
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
     * Find all eigenvalues of an upper Hessenberg matrix. On input, A can be
     * exactly as output from elmhes. On output, it is destroyed.
     */
    private static void hqr(DenseMatrix A, double[] d, double[] e) {
        int n = A.nrows();
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
                    s = Math.abs(A.get(l - 1, l - 1) + Math.abs(A.get(l, l)));
                    if (s == 0.0) {
                        s = anorm;
                    }
                    if (Math.abs(A.get(l, l - 1)) <= Math.EPSILON * s) {
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
                            throw new IllegalStateException("Too many iterations in hqr");
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
                            if (u <= Math.EPSILON * v) {
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
     * Finds all eigenvalues of an upper Hessenberg matrix A[0..n-1][0..n-1].
     * On input A can be exactly as output from elmhes and eltran. On output, d and e
     * contain the eigenvalues of A, while V is a matrix whose columns contain
     * the corresponding eigenvectors. The eigenvalues are not sorted, except
     * that complex conjugate pairs appear consecutively with the eigenvalue
     * having the positive imaginary part. For a complex eigenvalue, only the
     * eigenvector corresponding to the eigenvalue with positive imaginary part
     * is stored, with real part in V[0..n-1][i] and imaginary part in V[0..n-1][i+1].
     * The eigenvectors are not normalized.
     */
    private static void hqr2(DenseMatrix A, DenseMatrix V, double[] d, double[] e) {
        int n = A.nrows();
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
                    if (Math.abs(A.get(l, l - 1)) <= Math.EPSILON * s) {
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
                        A.set(nn, nn, x );
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
                            throw new IllegalArgumentException("Too many iterations in hqr");
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
                            if (u <= Math.EPSILON * v) {
                                break;
                            }
                        }
                        for (i = m; i < nn - 1; i++) {
                            A.set(i + 2, i , 0.0);
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
                                    t = Math.EPSILON * anorm;
                                }
                                A.set(i, nn, -r / t);
                            } else {
                                x = A.get(i, i + 1);
                                y = A.get(i + 1, i);
                                q = Math.sqr(d[i] - p) + Math.sqr(e[i]);
                                t = (x * s - z * r) / q;
                                A.set(i, nn, t);
                                if (Math.abs(x) > Math.abs(z)) {
                                    A.set(i + 1, nn, (-r - w * t) / x);
                                } else {
                                    A.set(i + 1, nn, (-s - y * t) / z);
                                }
                            }
                            t = Math.abs(A.get(i, nn));
                            if (Math.EPSILON * t * t > 1) {
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
                                vr = Math.sqr(d[i] - p) + Math.sqr(e[i]) - q * q;
                                vi = 2.0 * q * (d[i] - p);
                                if (vr == 0.0 && vi == 0.0) {
                                    vr = Math.EPSILON * anorm * (Math.abs(w) + Math.abs(q) + Math.abs(x) + Math.abs(y) + Math.abs(z));
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
                        if (Math.EPSILON * t * t > 1) {
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
     *  Complex scalar division.
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

    /**
     * Sort eigenvalues.
     */
    private static void sort(double[] d, double[] e) {
        int i = 0;
        int n = d.length;
        for (int j = 1; j < n; j++) {
            double real = d[j];
            double img = e[j];
            for (i = j - 1; i >= 0; i--) {
                if (d[i] >= d[j]) {
                    break;
                }
                d[i + 1] = d[i];
                e[i + 1] = e[i];
            }
            d[i + 1] = real;
            e[i + 1] = img;
        }
    }

    /**
     * Sort eigenvalues and eigenvectors.
     */
    private static void sort(double[] d, double[] e, DenseMatrix V) {
        int i = 0;
        int n = d.length;
        double[] temp = new double[n];
        for (int j = 1; j < n; j++) {
            double real = d[j];
            double img = e[j];
            for (int k = 0; k < n; k++) {
                temp[k] = V.get(k, j);
            }
            for (i = j - 1; i >= 0; i--) {
                if (d[i] >= d[j]) {
                    break;
                }
                d[i + 1] = d[i];
                e[i + 1] = e[i];
                for (int k = 0; k < n; k++) {
                    V.set(k, i + 1, V.get(k, i));
                }
            }
            d[i + 1] = real;
            e[i + 1] = img;
            for (int k = 0; k < n; k++) {
                V.set(k, i + 1, temp[k]);
            }
        }
    }
}
