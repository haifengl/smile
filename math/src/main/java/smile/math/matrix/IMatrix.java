/*
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
 */

package smile.math.matrix;

import java.io.Serializable;

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
public abstract class IMatrix<T> implements Cloneable, Serializable {
    /**
     * The row names.
     */
    private String[] rowNames;
    /**
     * The column names.
     */
    private String[] colNames;

    /**
     * Returns the number of rows.
     * @return the number of rows.
     */
    public abstract int nrow();

    /**
     * Returns the number of columns.
     * @return the number of columns.
     */
    public abstract int ncol();

    /**
     * Returns the number of stored matrix elements. For conventional matrix,
     * it is simplify nrow * ncol. But it is usually much less for band,
     * packed or sparse matrix.
     * @return the number of stored matrix elements.
     */
    public abstract long size();

    /**
     * Returns the row names.
     * @return the row names.
     */
    public String[] rowNames() {
        return rowNames;
    }

    /**
     * Sets the row names.
     * @param names the row names.
     */
    public void rowNames(String[] names) {
        if (names != null && names.length != nrow()) {
            throw new IllegalArgumentException(String.format("Invalid row names length: %d != %d", names.length, nrow()));
        }
        rowNames = names;
    }

    /**
     * Returns the name of i-th row.
     * @param i the row index.
     * @return the name of i-th row.
     */
    public String rowName(int i) {
        return rowNames[i];
    }

    /**
     * Returns the column names.
     * @return the column names.
     */
    public String[] colNames() {
        return colNames;
    }

    /**
     * Sets the column names.
     * @param names the column names.
     */
    public void colNames(String[] names) {
        if (names != null && names.length != ncol()) {
            throw new IllegalArgumentException(String.format("Invalid column names length: %d != %d", names.length, ncol()));
        }
        colNames = names;
    }

    /**
     * Returns the name of i-th column.
     * @param i the column index.
     * @return the name of i-th column.
     */
    public String colName(int i) {
        return colNames[i];
    }

    @Override
    public String toString() {
        return toString(false);
    }

    /**
     * Returns the string representation of matrix.
     * @param full Print the full matrix if true. Otherwise,
     *             print only top left 7 x 7 submatrix.
     * @return the string representation of matrix.
     */
    public String toString(boolean full) {
        return full ? toString(nrow(), ncol()) : toString(7, 7);
    }

    /**
     * Returns the string representation of matrix.
     * @param m the number of rows to print.
     * @param n the number of columns to print.
     * @return the string representation of matrix.
     */
    public String toString(int m, int n) {
        StringBuilder sb = new StringBuilder(nrow() + " x " + ncol() + "\n");
        m = Math.min(m, nrow());
        n = Math.min(n, ncol());

        String newline = n < ncol() ? "  ...\n" : "\n";

        if (colNames != null) {
            sb.append(rowNames == null ? "   " : "            ");

            for (int j = 0; j < n; j++) {
                sb.append(String.format(" %12.12s", colNames[j]));
            }
            sb.append(newline);
        }

        for (int i = 0; i < m; i++) {
            sb.append(rowNames == null ? "   " : String.format("%-12.12s", rowNames[i]));

            for (int j = 0; j < n; j++) {
                sb.append(String.format(" %12.12s", str(i, j)));
            }
            sb.append(newline);
        }

        if (m < nrow()) {
            sb.append("  ...\n");
        }

        return sb.toString();
    }

    /**
     * Returns the string representation of <code>A[i, j]</code>.
     * @param i the row index.
     * @param j the column index.
     * @return the string representation of <code>A[i, j]</code>.
     */
    abstract String str(int i, int j);

    /**
     * Returns the matrix-vector multiplication {@code A * x}.
     * @param x the vector.
     * @return the matrix-vector multiplication {@code A * x}.
     */
    public abstract T mv(T x);

    /**
     * Matrix-vector multiplication {@code y = A * x}.
     * @param x the input vector.
     * @param y the output vector.
     */
    public abstract void mv(T x, T y);

    /**
     * Matrix-vector multiplication {@code A * x}.
     * @param work the workspace for both input and output vector.
     * @param inputOffset the offset of input vector in workspace.
     * @param outputOffset the offset of output vector in workspace.
     */
    public abstract void mv(T work, int inputOffset, int outputOffset);

    /**
     * Returns Matrix-vector multiplication {@code A' * x}.
     * @param x the vector.
     * @return the matrix-vector multiplication {@code A' * x}.
     */
    public abstract T tv(T x);

    /**
     * Matrix-vector multiplication {@code y = A' * x}.
     * @param x the input vector.
     * @param y the output vector.
     */
    public abstract void tv(T x, T y);

    /**
     * Matrix-vector multiplication {@code A' * x}.
     * @param work the workspace for both input and output vector.
     * @param inputOffset the offset of input vector in workspace.
     * @param outputOffset the offset of output vector in workspace.
     */
    public abstract void tv(T work, int inputOffset, int outputOffset);
}
