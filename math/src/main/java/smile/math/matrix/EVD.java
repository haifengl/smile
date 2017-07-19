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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
public class EVD {
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
    public EVD(DenseMatrix V, double[] d) {
        this.V = V;
        this.d = d;
    }

    /**
     * Private constructor.
     * @param V eigenvectors.
     * @param d real part of eigenvalues.
     * @param e imaginary part of eigenvalues.
     */
    public EVD(DenseMatrix V, double[] d, double[] e) {
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
        DenseMatrix D = Matrix.zeros(n, n);
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
}
