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

package smile.util;

import java.util.Arrays;
import smile.math.MathEx;

/**
 * 2-dimensional array of integers. Java doesn't have real multidimensional
 * arrays. They are just array of arrays, which are not friendly to modern
 * CPU due to frequent cache miss. The extra (multiple times) array index
 * checking also significantly reduces the performance. This class solves
 * these pain points by storing data in a single 1D array of int in
 * row major order. Note that this class is simple by design, as a replacement
 * of int[][] in case of row by row access. For advanced matrix computation,
 * please use <code>Matrix</code>.
 *
 * @see smile.math.matrix.Matrix
 */
public class IntArray2D {

    /**
     * The array storage.
     */
    private final int[] A;
    /**
     * The number of rows.
     */
    private final int nrow;
    /**
     * The number of columns.
     */
    private final int ncol;

    /**
     * Constructor.
     * @param A the array of matrix.
     */
    public IntArray2D(int[][] A) {
        this.nrow = A.length;
        this.ncol = A[0].length;
        this.A = new int[nrow*ncol];

        int pos = 0;
        for (int i = 0; i < nrow; i++) {
            System.arraycopy(A[i], 0, this.A, pos, ncol);
            pos += ncol;
        }
    }

    /**
     * Constructor of all-zero matrix.
     * @param nrow the number of rows.
     * @param ncol the number of columns.
     */
    public IntArray2D(int nrow, int ncol) {
        this.nrow = nrow;
        this.ncol = ncol;
        A = new int[nrow*ncol];
    }

    /**
     * Constructor. Fill the matrix with given value.
     * @param nrow the number of rows.
     * @param ncol the number of columns.
     * @param value the initial value.
     */
    public IntArray2D(int nrow, int ncol, int value) {
        this(nrow, ncol);
        if (value != 0.0)
            Arrays.fill(A, value);
    }

    /**
     * Constructor.
     * @param nrow the number of rows.
     * @param ncol the number of columns.
     * @param value the array of matrix values arranged in row major format.
     */
    public IntArray2D(int nrow, int ncol, int[] value) {
        this.nrow = nrow;
        this.ncol = ncol;
        this.A = value;
    }

    /**
     * Returns the number of rows.
     * @return the number of rows.
     */
    public int nrow() {
        return nrow;
    }

    /**
     * Returns the number of columns.
     * @return the number of columns.
     */
    public int ncol() {
        return ncol;
    }

    /**
     * Returns A[i, j]. Useful in Scala.
     * @param i the row index.
     * @param j the column index.
     * @return A[i, j].
     */
    public int apply(int i, int j) {
        return A[i*ncol + j];
    }

    /**
     * Returns A[i, j].
     * @param i the row index.
     * @param j the column index.
     * @return A[i, j].
     */
    public int get(int i, int j) {
        return A[i*ncol + j];
    }

    /**
     * Sets A[i, j].
     * @param i the row index.
     * @param j the column index.
     * @param x the value.
     */
    public void set(int i, int j, int x) {
        A[i*ncol + j] = x;
    }

    /**
     * A[i, j] += x.
     * @param i the row index.
     * @param j the column index.
     * @param x the operand.
     * @return the updated cell value.
     */
    public int add(int i, int j, int x) {
        return A[i*ncol + j] += x;
    }

    /**
     * A[i, j] -= x.
     * @param i the row index.
     * @param j the column index.
     * @param x the operand.
     * @return the updated cell value.
     */
    public int sub(int i, int j, int x) {
        return A[i*ncol + j] -= x;
    }

    /**
     * A[i, j] *= x.
     * @param i the row index.
     * @param j the column index.
     * @param x the operand.
     * @return the updated cell value.
     */
    public int mul(int i, int j, int x) {
        return A[i*ncol + j] *= x;
    }

    /**
     * A[i, j] /= x.
     * @param i the row index.
     * @param j the column index.
     * @param x the operand.
     * @return the updated cell value.
     */
    public int div(int i, int j, int x) {
        return A[i*ncol + j] /= x;
    }

    /**
     * A += B.
     * @param B the operand.
     * @return this object.
     */
    public IntArray2D add(IntArray2D B) {
        if (nrow != B.nrow || ncol != B.ncol) {
            throw new IllegalArgumentException("Matrix is not of same size.");
        }

        for (int i = 0; i < A.length; i++) {
            A[i] += B.A[i];
        }
        return this;
    }

    /**
     * A -= B.
     * @param B the operand.
     * @return this object.
     */
    public IntArray2D sub(IntArray2D B) {
        if (nrow != B.nrow || ncol != B.ncol) {
            throw new IllegalArgumentException("Matrix is not of same size.");
        }

        for (int i = 0; i < A.length; i++) {
            A[i] -= B.A[i];
        }
        return this;
    }

    /**
     * A *= B.
     * @param B the operand.
     * @return this object.
     */
    public IntArray2D mul(IntArray2D B) {
        if (nrow != B.nrow || ncol != B.ncol) {
            throw new IllegalArgumentException("Matrix is not of same size.");
        }

        for (int i = 0; i < A.length; i++) {
            A[i] *= B.A[i];
        }
        return this;
    }

    /**
     * A /= B.
     * @param B the operand.
     * @return this object.
     */
    public IntArray2D div(IntArray2D B) {
        if (nrow != B.nrow || ncol != B.ncol) {
            throw new IllegalArgumentException("Matrix is not of same size.");
        }

        for (int i = 0; i < A.length; i++) {
            A[i] /= B.A[i];
        }
        return this;
    }

    /**
     * A += x.
     * @param x the operand.
     * @return this object.
     */
    public IntArray2D add(int x) {
        for (int i = 0; i < A.length; i++) {
            A[i] += x;
        }

        return this;
    }

    /**
     * A -= x.
     * @param x the operand.
     * @return this object.
     */
    public IntArray2D sub(int x) {
        for (int i = 0; i < A.length; i++) {
            A[i] -= x;
        }

        return this;
    }

    /**
     * A *= x.
     * @param x the operand.
     * @return this object.
     */
    public IntArray2D mul(int x) {
        for (int i = 0; i < A.length; i++) {
            A[i] *= x;
        }

        return this;
    }

    /**
     * A /= x.
     * @param x the operand.
     * @return this object.
     */
    public IntArray2D div(int x) {
        for (int i = 0; i < A.length; i++) {
            A[i] /= x;
        }

        return this;
    }

    /**
     * Returns the sum of all elements.
     * @return the sum of all elements.
     */
    public long sum() {
        return MathEx.sum(A);
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
        return full ? toString(nrow, ncol) : toString(7, 7);
    }

    /**
     * Returns the string representation of matrix.
     * @param m the number of rows to print.
     * @param n the number of columns to print.
     * @return the string representation of matrix.
     */
    public String toString(int m, int n) {
        StringBuilder sb = new StringBuilder();
        m = Math.min(m, nrow);
        n = Math.min(n, ncol);

        String newline = n < ncol ? "...\n" : "\n";

        for (int i = 0; i < m; i++) {
            for (int j = 0; j < n; j++) {
                sb.append(String.format("%d  ", get(i, j)));
            }
            sb.append(newline);
        }

        if (m < nrow) {
            sb.append("  ...\n");
        }

        return sb.toString();
    }
}