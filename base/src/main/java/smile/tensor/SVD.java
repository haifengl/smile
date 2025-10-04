/*
 * Copyright (c) 2010-2025 Haifeng Li. All rights reserved.
 *
 * Smile is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Smile is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Smile. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.tensor;

import smile.linalg.Transpose;
import smile.math.MathEx;
import static smile.linalg.Transpose.*;

/**
 * Singular Value Decomposition.
 * <p>
 * For an m-by-n matrix A with {@code m >= n}, the singular value decomposition is
 * an m-by-n orthogonal matrix U, an n-by-n diagonal matrix &Sigma;, and
 * an n-by-n orthogonal matrix V so that A = U*&Sigma;*V'.
 * <p>
 * For {@code m < n}, only the first m columns of V are computed and &Sigma; is m-by-m.
 * <p>
 * The singular values, &sigma;<sub>k</sub> = &Sigma;<sub>kk</sub>, are ordered
 * so that &sigma;<sub>0</sub> &ge; &sigma;<sub>1</sub> &ge; ... &ge; &sigma;<sub>n-1</sub>.
 * <p>
 * The singular value decomposition always exists. The matrix condition number
 * and the effective numerical rank can be computed from this decomposition.
 * <p>
 * SVD is a very powerful technique for dealing with sets of equations or matrices
 * that are either singular or else numerically very close to singular. In many
 * cases where Gaussian elimination and LU decomposition fail to give satisfactory
 * results, SVD will diagnose precisely what the problem is. SVD is also the
 * method of choice for solving most linear least squares problems.
 * <p>
 * Applications which employ the SVD include computing the pseudo-inverse, least
 * squares fitting of data, matrix approximation, and determining the rank,
 * range and null space of a matrix. The SVD is also applied extensively to
 * the study of linear inverse problems, and is useful in the analysis of
 * regularization methods such as that of Tikhonov. It is widely used in
 * statistics where it is related to principal component analysis. Yet another
 * usage is latent semantic indexing in natural language text processing.
 *
 * @param m the number of rows of matrix.
 * @param n the number of columns of matrix.
 * @param s the singular values in descending order.
 * @param U the left singular vectors
 * @param Vt the transpose of right singular vectors.
 * @author Haifeng Li
 */
public record SVD(int m, int n, Vector s, DenseMatrix U, DenseMatrix Vt) {
    /**
     * Constructor.
     * @param m the number of rows of matrix.
     * @param n the number of columns of matrix.
     * @param s the singular values in descending order.
     */
    public SVD(int m, int n, Vector s) {
        this(m, n, s, null, null);
    }

    /**
     * Constructor.
     * @param s the singular values in descending order.
     * @param U the left singular vectors
     * @param Vt the transpose of right singular vectors.
     */
    public SVD(Vector s, DenseMatrix U, DenseMatrix Vt) {
        this(U.m, Vt.n, s, U, Vt);
    }

    /**
     * Returns the diagonal matrix of singular values.
     * @return the diagonal matrix of singular values.
     */
    public DenseMatrix diag() {
        DenseMatrix S = s.zeros(m, n);
        int size = s.size();
        for (int i = 0; i < size; i++) {
            S.set(i, i, s.get(i));
        }

        return S;
    }

    /**
     * Returns the L<sub>2</sub> matrix norm that is the largest singular value.
     * @return L<sub>2</sub> matrix norm.
     */
    public double norm() {
        return s.get(0);
    }

    /**
     * Returns the threshold to determine the effective rank.
     * Singular values S(i) <= RCOND are treated as zero.
     * @return the threshold to determine the effective rank.
     */
    private double rcond() {
        return 0.5 * Math.sqrt(m + n + 1) * s.get(0) * MathEx.EPSILON;
    }

    /**
     * Returns the effective numerical matrix rank. The number of non-negligible
     * singular values.
     * @return the effective numerical matrix rank.
     */
    public int rank() {
        if (s.size() != Math.min(m, n)) {
            throw new UnsupportedOperationException("The rank() operation cannot be called on a partial SVD.");
        }

        int r = 0;
        int size = s.size();
        double tol = rcond();

        for (int i = 0; i < size; i++) {
            if (s.get(i) > tol) {
                r++;
            }
        }
        return r;
    }

    /**
     * Returns the dimension of null space. The number of negligible
     * singular values.
     * @return the dimension of null space.
     */
    public int nullity() {
        return Math.min(m, n) - rank();
    }

    /**
     * Returns the L<sub>2</sub> norm condition number, which is max(S) / min(S).
     * A system of equations is considered to be well-conditioned if a small
     * change in the coefficient matrix or a small change on the right hand
     * side results in a small change in the solution vector. Otherwise, it is
     * called ill-conditioned. Condition number is defined as the product of
     * the norm of A and the norm of A<sup>-1</sup>. If we use the usual
     * L<sub>2</sub> norm on vectors and the associated matrix norm, then the
     * condition number is the ratio of the largest singular value of matrix
     * A to the smallest. The condition number depends on the underlying norm.
     * However, regardless of the norm, it is always greater or equal to 1.
     * If it is close to one, the matrix is well conditioned. If the condition
     * number is large, then the matrix is said to be ill-conditioned. A matrix
     * that is not invertible has the condition number equal to infinity.
     *
     * @return L<sub>2</sub> norm condition number.
     */
    public double condition() {
        if (s.size() != Math.min(m, n)) {
            throw new UnsupportedOperationException("The operation cannot be called on a partial SVD.");
        }

        double s0 =  s.get(0);
        double s1 =  s.get(s.size() - 1);
        return (s0 <= 0.0 || s1 <= 0.0) ? Double.POSITIVE_INFINITY : s0 / s1;
    }

    /**
     * Returns the matrix which columns are the orthonormal basis for the range space.
     * Returns null if the rank is zero (if and only if zero matrix).
     * @return the range space span matrix.
     */
    public DenseMatrix range() {
        if (s.size() != Math.min(m, n)) {
            throw new UnsupportedOperationException("The operation cannot be called on a partial SVD.");
        }

        if (U == null) {
            throw new IllegalStateException("The left singular vectors are not available.");
        }

        int r = rank();
        // zero rank, if and only if zero matrix.
        if (r == 0) {
            return null;
        }

        DenseMatrix R = U.zeros(m, r);
        for (int j = 0; j < r; j++) {
            for (int i = 0; i < m; i++) {
                R.set(i, j, U.get(i, j));
            }
        }

        return R;
    }

    /**
     * Returns the matrix which columns are the orthonormal basis for the null space.
     * Returns null if the matrix is of full rank.
     * @return the null space span matrix.
     */
    public DenseMatrix nullspace() {
        if (s.size() != Math.min(m, n)) {
            throw new UnsupportedOperationException("The operation cannot be called on a partial SVD.");
        }

        if (Vt == null) {
            throw new IllegalStateException("The right singular vectors are not available.");
        }

        int nr = nullity();
        // full rank
        if (nr == 0) {
            return null;
        }

        DenseMatrix N = Vt.zeros(n, nr);
        for (int j = 0; j < nr; j++) {
            for (int i = 0; i < n; i++) {
                N.set(i, j, Vt.get(n - j - 1, i));
            }
        }
        return N;
    }

    /**
     * Returns the pseudo inverse.
     * @return the pseudo inverse.
     */
    public DenseMatrix pinv() {
        if (U == null || Vt == null) {
            throw new IllegalStateException("The singular vectors are not available.");
        }

        int k = s.size();
        Vector sigma = s.zeros(k);
        int r = rank();
        for (int i = 0; i < r; i++) {
            sigma.set(i, 1.0 / s.get(i));
        }

        return adb(TRANSPOSE, Vt, sigma, TRANSPOSE, U);
    }

    /**
     * Solves the least squares min || B - A*X ||.
     * @param b the right hand side of overdetermined linear system.
     * @throws RuntimeException when the matrix is rank deficient.
     * @return the solution vector.
     */
    public Vector solve(double[] b) {
        if (U == null || Vt == null) {
            throw new IllegalStateException("The singular vectors are not available.");
        }

        if (b.length != m) {
            throw new IllegalArgumentException(String.format("Row dimensions do not agree: A is %d x %d, but B is %d x 1", m, n, b.length));
        }

        int r = rank();
        // The submatrix U[:, 1:r], where r is the rank of matrix.
        DenseMatrix Ur = r == U.ncol() ? U : U.submatrix(0, 0, m, r);
        DenseMatrix Vr = r == Vt.nrow() ? Vt : Vt.submatrix(0, 0, r, n);
        Vector x = Ur.vector(b.length);
        for (int i = 0; i < b.length; i++) {
            x.set(i, b[i]);
        }

        Vector Utb = Ur.vector(r);
        Ur.tv(x, Utb);
        for (int i = 0; i < r; i++) {
            Utb.set(i, Utb.get(i) / s.get(i));
        }
        return Vr.tv(Utb);
    }

    /**
     * Solves the least squares min || B - A*X ||.
     * @param b the right hand side of overdetermined linear system.
     * @throws RuntimeException when the matrix is rank deficient.
     * @return the solution vector.
     */
    public Vector solve(float[] b) {
        if (U == null || Vt == null) {
            throw new IllegalStateException("The singular vectors are not available.");
        }

        if (b.length != m) {
            throw new IllegalArgumentException(String.format("Row dimensions do not agree: A is %d x %d, but B is %d x 1", m, n, b.length));
        }

        int r = rank();
        // The submatrix U[:, 1:r], where r is the rank of matrix.
        DenseMatrix Ur = r == U.ncol() ? U : U.submatrix(0, 0, m, r);
        Vector x = Ur.vector(b.length);
        for (int i = 0; i < b.length; i++) {
            x.set(i, b[i]);
        }

        Vector Utb = Ur.vector(s.size());
        Ur.tv(x, Utb);
        for (int i = 0; i < r; i++) {
            Utb.set(i, Utb.get(i) / s.get(i));
        }
        return Vt.tv(Utb);
    }

    /**
     * Returns {@code A * D * B}, where D is a diagonal matrix.
     * @param transA normal, transpose, or conjugate transpose
     *               operation on the matrix A.
     * @param A the operand.
     * @param D the diagonal matrix.
     * @param transB normal, transpose, or conjugate transpose
     *               operation on the matrix B.
     * @param B the operand.
     * @return the multiplication.
     */
    private static DenseMatrix adb(Transpose transA, DenseMatrix A, Vector D, Transpose transB, DenseMatrix B) {
        DenseMatrix AD;
        int m = A.m, n = A.n;
        if (transA == NO_TRANSPOSE) {
            AD = A.zeros(m, n);
            for (int j = 0; j < n; j++) {
                double dj = D.get(j);
                for (int i = 0; i < m; i++) {
                    AD.set(i, j, dj * A.get(i, j));
                }
            }
        } else {
            AD = A.zeros(n, m);
            for (int j = 0; j < m; j++) {
                double dj = D.get(j);
                for (int i = 0; i < n; i++) {
                    AD.set(i, j, dj * A.get(j, i));
                }
            }
        }

        return transB == NO_TRANSPOSE ? AD.mm(B) : AD.mt(B);
    }
}
