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

import smile.sort.QuickSort;

/**
 * Eigenvalue decomposition. Eigen decomposition is the factorization
 * of a matrix into a canonical form, whereby the matrix is represented in terms
 * of its eigenvalues and eigenvectors:
 * <pre>{@code
 *     A = V*D*V<sup>-1</sup>
 * }</pre>
 * If A is symmetric, then A = V*D*V' where the eigenvalue matrix D is
 * diagonal and the eigenvector matrix V is orthogonal.
 * <p>
 * Given a linear transformation A, a non-zero vector x is defined to be an
 * eigenvector of the transformation if it satisfies the eigenvalue equation
 * <pre>{@code
 *     A x = &lambda; x
 * }</pre>
 * for some scalar &lambda;. In this situation, the scalar &lambda; is called
 * an eigenvalue of A corresponding to the eigenvector x.
 * <p>
 * The word eigenvector formally refers to the right eigenvector, which is
 * defined by the above eigenvalue equation A x = &lambda; x, and is the most
 * commonly used eigenvector. However, the left eigenvector exists as well, and
 * is defined by x A = &lambda; x.
 * <p>
 * Let A be a real n-by-n matrix with strictly positive entries a<sub>ij</sub>
 * {@code > 0}. Then the following statements hold.
 * <ol>
 * <li> There is a positive real number r, called the Perron-Frobenius
 * eigenvalue, such that r is an eigenvalue of A and any other eigenvalue &lambda;
 * (possibly complex) is strictly smaller than r in absolute value,
 * |&lambda;| {@code < r}.
 * <li> The Perron-Frobenius eigenvalue is simple: r is a simple root of the
 *      characteristic polynomial of A. Consequently, both the right and the left
 *      eigenspace associated to r is one-dimensional.
 * </li>
 * <li> There exists a left eigenvector v of A associated with r (row vector)
 *      having strictly positive components. Likewise, there exists a right
 *      eigenvector w associated with r (column vector) having strictly positive
 *      components.
 * </li>
 * <li> The left eigenvector v (respectively right w) associated with r, is the
 *      only eigenvector which has positive components, i.e. for all other
 *      eigenvectors of A there exists a component which is not positive.
 * </li>
 * </ol>
 * <p>
 * A stochastic matrix, probability matrix, or transition matrix is used to
 * describe the transitions of a Markov chain. A right stochastic matrix is
 * a square matrix each of whose rows consists of non-negative real numbers,
 * with each row summing to 1. A left stochastic matrix is a square matrix
 * whose columns consist of non-negative real numbers whose sum is 1. A doubly
 * stochastic matrix where all entries are non-negative and all rows and all
 * columns sum to 1. A stationary probability vector &pi; is defined as a
 * vector that does not change under application of the transition matrix;
 * that is, it is defined as a left eigenvector of the probability matrix,
 * associated with eigenvalue 1: &pi;P = &pi;. The Perron-Frobenius theorem
 * ensures that such a vector exists, and that the largest eigenvalue
 * associated with a stochastic matrix is always 1. For a matrix with strictly
 * positive entries, this vector is unique. In general, however, there may be
 * several such vectors.
 *
 * @param wr the real part of eigenvalues. By default, the eigenvalues
 *           and eigenvectors are not always in sorted order. The
 *           <code>sort</code> function puts the eigenvalues in descending
 *           order and reorder the corresponding eigenvectors.
 * @param wi the imaginary part of eigenvalues.
 * @param Vl the left eigenvectors.
 * @param Vr the right eigenvectors.
 * @author Haifeng Li
 */
public record EVD(Vector wr, Vector wi, DenseMatrix Vl, DenseMatrix Vr) {
    /**
     * Constructor.
     *
     * @param w eigenvalues.
     * @param V eigenvectors.
     */
    public EVD(Vector w, DenseMatrix V) {
        this(w, null, V, V);
    }

    /**
     * Returns the block diagonal eigenvalue matrix whose diagonal are the real
     * part of eigenvalues, lower subdiagonal are positive imaginary parts, and
     * upper subdiagonal are negative imaginary parts.
     * @return the diagonal eigenvalue matrix.
     */
    public DenseMatrix diag() {
        DenseMatrix D = wr.diagflat();

        if (wi != null) {
            int n = wr.size();
            for (int i = 0; i < n; i++) {
                double wii = wi.get(i);
                if (wii > 0) {
                    D.set(i, i + 1, wii);
                } else if (wii < 0) {
                    D.set(i, i - 1, wii);
                }
            }
        }

        return D;
    }

    /**
     * Sorts the eigenvalues in descending order and reorders the
     * corresponding eigenvectors.
     * @return sorted eigen decomposition.
     */
    public EVD sort() {
        int n = wr.size();
        double[] w = new double[n];
        if (wi != null) {
            for (int i = 0; i < n; i++) {
                w[i] = -(wr.get(i) * wr.get(i) + wi.get(i) * wi.get(i));
            }
        } else {
            for (int i = 0; i < n; i++) {
                w[i] = -(wr.get(i) * wr.get(i));
            }
        }

        int[] index = QuickSort.sort(w);
        Vector wr2 = wr.zeros(n);
        for (int j = 0; j < n; j++) {
            wr2.set(j, wr.get(index[j]));
        }

        Vector wi2 = null;
        if (wi != null) {
            wi2 = wi.zeros(n);
            for (int j = 0; j < n; j++) {
                wi2.set(j, wi.get(index[j]));
            }
        }

        DenseMatrix Vl2 = null;
        if (Vl != null) {
            int m = Vl.m;
            Vl2 = Vl.zeros(m, n);
            for (int j = 0; j < n; j++) {
                for (int i = 0; i < m; i++) {
                    Vl2.set(i, j, Vl.get(i, index[j]));
                }
            }
        }

        DenseMatrix Vr2 = null;
        if (Vr != null) {
            int m = Vr.m;
            Vr2 = Vr.zeros(m, n);
            for (int j = 0; j < n; j++) {
                for (int i = 0; i < m; i++) {
                    Vr2.set(i, j, Vr.get(i, index[j]));
                }
            }
        }

        return new EVD(wr2, wi2, Vl2, Vr2);
    }
}
