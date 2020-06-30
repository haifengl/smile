/*******************************************************************************
 * Copyright (c) 2010-2020 Haifeng Li. All rights reserved.
 *
 * Smile is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of
 * the License, or (at your option) any later version.
 *
 * Smile is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Smile.  If not, see <https://www.gnu.org/licenses/>.
 ******************************************************************************/

package smile.math.matrix;

import java.io.IOException;
import java.io.LineNumberReader;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import smile.stat.distribution.GaussianDistribution;
import smile.util.SparseArray;

/**
 * An abstract interface of matrix. The most important method is the matrix vector
 * multiplication, which is the only operation needed in many iterative matrix
 * algorithms, e.g. biconjugate gradient method for solving linear equations and
 * power iteration and Lanczos algorithm for eigen decomposition, which are
 * usually very efficient for very large and sparse matrices.
 * <p>
 * A matrix is a rectangular array of numbers. An item in a matrix is called
 * an entry or an element. Entries are often denoted by a variable with two
 * subscripts. Matrices of the same size can be added and subtracted entrywise
 * and matrices of compatible size can be multiplied. These operations have
 * many of the properties of ordinary arithmetic, except that matrix
 * multiplication is not commutative, that is, AB and BA are not equal in
 * general.
 * <p>
 * Matrices are a key tool in linear algebra. One use of matrices is to
 * represent linear transformations and matrix multiplication corresponds
 * to composition of linear transformations. Matrices can also keep track of
 * the coefficients in a system of linear equations. For a square matrix,
 * the determinant and inverse matrix (when it exists) govern the behavior
 * of solutions to the corresponding system of linear equations, and
 * eigenvalues and eigenvectors provide insight into the geometry of
 * the associated linear transformation.
 * <p>
 * There are several methods to render matrices into a more easily accessible
 * form. They are generally referred to as matrix transformation or matrix
 * decomposition techniques. The interest of all these decomposition techniques
 * is that they preserve certain properties of the matrices in question, such
 * as determinant, rank or inverse, so that these quantities can be calculated
 * after applying the transformation, or that certain matrix operations are
 * algorithmically easier to carry out for some types of matrices.
 * <p>
 * The LU decomposition factors matrices as a product of lower (L) and an upper
 * triangular matrices (U). Once this decomposition is calculated, linear
 * systems can be solved more efficiently, by a simple technique called
 * forward and back substitution. Likewise, inverses of triangular matrices
 * are algorithmically easier to calculate. The QR decomposition factors matrices
 * as a product of an orthogonal (Q) and a right triangular matrix (R). QR decomposition
 * is often used to solve the linear least squares problem, and is the basis for
 * a particular eigenvalue algorithm, the QR algorithm. Singular value decomposition
 * expresses any matrix A as a product UDV', where U and V are unitary matrices
 * and D is a diagonal matrix. The eigendecomposition or diagonalization
 * expresses A as a product VDV<sup>-1</sup>, where D is a diagonal matrix and
 * V is a suitable invertible matrix. If A can be written in this form, it is
 * called diagonalizable.
 *
 * @author Haifeng Li
 */
public interface Matrix extends Serializable, Cloneable {
    /**
     * Returns an matrix initialized by given two-dimensional array.
     */
    static DenseMatrix of(double[][] A) {
        return Factory.matrix(A);
    }

    /**
     * Returns a column vector/matrix initialized by given one-dimensional array.
     */
    static DenseMatrix of(double[] A) {
        return Factory.matrix(A);
    }

    /**
     * Creates a matrix filled with given value.
     */
    static DenseMatrix of(int rows, int cols, double value) {
        return Factory.matrix(rows, cols, value);
    }

    /**
     * Returns all-zero matrix.
     */
    static DenseMatrix zeros(int rows, int cols) {
        return Factory.matrix(rows, cols);
    }

    /**
     * Return an all-one matrix.
     */
    static DenseMatrix ones(int rows, int cols) {
        return Factory.matrix(rows, cols, 1.0);
    }

    /**
     * Returns an n-by-n identity matrix.
     */
    static DenseMatrix eye(int n) {
        DenseMatrix matrix = Factory.matrix(n, n);

        for (int i = 0; i < n; i++) {
            matrix.set(i, i, 1.0);
        }

        return matrix;
    }

    /**
     * Returns an m-by-n identity matrix.
     */
    static DenseMatrix eye(int m, int n) {
        DenseMatrix matrix = Factory.matrix(m, n);

        int k = Math.min(m, n);
        for (int i = 0; i < k; i++) {
            matrix.set(i, i, 1.0);
        }

        return matrix;
    }

    /**
     * Returns a square diagonal matrix with the elements of vector diag on the main diagonal.
     * @param A the array of diagonal elements.
     */
    static DenseMatrix diag(double[] A) {
        int n = A.length;
        DenseMatrix matrix = Factory.matrix(n, n);

        for (int i = 0; i < n; i++) {
            matrix.set(i, i, A[i]);
        }

        return matrix;
    }

    /**
     * Returns a random matrix of standard normal distributed values with given mean and standard dev.
     */
    static DenseMatrix randn(int rows, int cols) {
        return randn(rows, cols, 0.0, 1.0);
    }

    /**
     * Returns a random matrix of normal distributed values with given mean and standard dev.
     */
    static DenseMatrix randn(int rows, int cols, double mu, double sigma) {
        DenseMatrix a = zeros(rows, cols);
        GaussianDistribution g = new GaussianDistribution(mu, sigma);

        for (int j = 0; j < cols; j++) {
            for (int i = 0; i < rows; i++) {
                a.set(i, j, g.rand());
            }
        }

        return a;
    }

    /**
     * Returns the string representation of matrix.
     * @param full Print the full matrix if true. Otherwise,
     *             print only top left 7 x 7 submatrix.
     */
    default String toString(boolean full) {
        return full ? toString(nrows(), ncols()) : toString(7, 7);
    }

    /**
     * Returns the string representation of matrix.
     * @param m the number of rows to print.
     * @param n the number of columns to print.
     */
    default String toString(int m, int n) {
        StringBuilder sb = new StringBuilder(nrows() + " x " + ncols() + "\n");
        m = Math.min(m, nrows());
        n = Math.min(n, ncols());

        String newline = n < ncols() ? "...\n" : "\n";

        for (int i = 0; i < m; i++) {
            for (int j = 0; j < n; j++) {
                sb.append(String.format("%8.4f  ", get(i, j)));
            }
            sb.append(newline);
        }

        if (m < nrows()) {
            sb.append("  ...\n");
        }

        return sb.toString();
    }

    /** Returns true if the matrix is symmetric. */
    default boolean isSymmetric() {
        return false;
    }

    /**
     * Sets if the matrix is symmetric. It is the caller's responsibility to
     * make sure if the matrix symmetric. Also the matrix won't update this
     * property if the matrix values are changed.
     */
    default void setSymmetric(boolean symmetric) {
        throw new UnsupportedOperationException();
    }

    /** Returns a deep copy of this matrix. */
    Matrix clone();

    /**
     * Returns the number of rows.
     */
    int nrows();

    /**
     * Returns the number of columns.
     */
    int ncols();

    /**
     * Returns the matrix transpose.
     */
    Matrix transpose();

    /**
     * Returns the entry value at row i and column j.
     */
    default double get(int i, int j) {
        throw new UnsupportedOperationException();
    }

    /**
     * Returns the entry value at row i and column j. For Scala users.
     */
    default double apply(int i, int j) {
        return get(i, j);
    }

    /**
     * Returns the diagonal elements.
     */
    default double[] diag() {
        int n = Math.min(nrows(), ncols());

        double[] d = new double[n];
        for (int i = 0; i < n; i++) {
            d[i] = get(i, i);
        }

        return d;
    }

    /**
     * Returns the matrix trace. The sum of the diagonal elements.
     */
    default double trace() {
        int n = Math.min(nrows(), ncols());

        double t = 0.0;
        for (int i = 0; i < n; i++) {
            t += get(i, i);
        }

        return t;
    }

    /**
     * Returns A' * A
     */
    default Matrix ata() {
        throw new UnsupportedOperationException();
    }

    /**
     * Returns A * A'
     */
    default Matrix aat() {
        throw new UnsupportedOperationException();
    }

    /**
     * y = A * x
     * @return y
     */
    double[] ax(double[] x, double[] y);

    /**
     * y = A * x + y
     * @return y
     */
    default double[] axpy(double[] x, double[] y) {
        throw new UnsupportedOperationException();
    }

    /**
     * y = A * x + b * y
     * @return y
     */
    default double[] axpy(double[] x, double[] y, double b) {
        throw new UnsupportedOperationException();
    }

    /**
     * y = A' * x
     * @return y
     */
    default double[] atx(double[] x, double[] y) {
        throw new UnsupportedOperationException();
    }

    /**
     * y = A' * x + y
     * @return y
     */
    default double[] atxpy(double[] x, double[] y) {
        throw new UnsupportedOperationException();
    }

    /**
     * y = A' * x + b * y
     * @return y
     */
    default double[] atxpy(double[] x, double[] y, double b) {
        throw new UnsupportedOperationException();
    }
}
