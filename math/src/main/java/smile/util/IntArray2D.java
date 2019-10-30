/*******************************************************************************
 * Copyright (c) 2010-2019 Haifeng Li
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
 *******************************************************************************/

package smile.util;

import java.util.Arrays;

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
    private int[] A;
    /**
     * The number of rows.
     */
    private int nrows;
    /**
     * The number of columns.
     */
    private int ncols;

    /**
     * Constructor.
     * @param A the array of matrix.
     */
    public IntArray2D(int[][] A) {
        this.nrows = A.length;
        this.ncols = A[0].length;
        this.A = new int[nrows*ncols];

        int pos = 0;
        for (int i = 0; i < nrows; i++) {
            System.arraycopy(A[i], 0, this.A, pos, ncols);
            pos += ncols;
        }
    }

    /**
     * Constructor of all-zero matrix.
     */
    public IntArray2D(int rows, int cols) {
        this.nrows = rows;
        this.ncols = cols;
        A = new int[rows*cols];
    }

    /**
     * Constructor. Fill the matrix with given value.
     */
    public IntArray2D(int rows, int cols, int value) {
        this(rows, cols);
        if (value != 0.0)
            Arrays.fill(A, value);
    }

    /**
     * Constructor.
     * @param value the array of matrix values arranged in row major format
     */
    public IntArray2D(int rows, int cols, int[] value) {
        this.nrows = rows;
        this.ncols = cols;
        this.A = value;
    }

    public int nrows() {
        return nrows;
    }

    public int ncols() {
        return ncols;
    }

    /** Returns A(i, j). Useful in Scala. */
    public int apply(int i, int j) {
        return A[i*ncols + j];
    }

    /** Returns A(i, j). */
    public int get(int i, int j) {
        return A[i*ncols + j];
    }

    /** Sets A(i, j). */
    public int set(int i, int j, int x) {
        return A[i*ncols + j] = x;
    }

    public int add(int i, int j, int x) {
        return A[i*ncols + j] += x;
    }

    public int sub(int i, int j, int x) {
        return A[i*ncols + j] -= x;
    }

    public int mul(int i, int j, int x) {
        return A[i*ncols + j] *= x;
    }

    public int div(int i, int j, int x) {
        return A[i*ncols + j] /= x;
    }

    public IntArray2D add(IntArray2D b) {
        if (nrows() != b.nrows() || ncols() != b.ncols()) {
            throw new IllegalArgumentException("Matrix is not of same size.");
        }

        for (int i = 0; i < A.length; i++) {
            A[i] += b.A[i];
        }
        return this;
    }

    public IntArray2D sub(IntArray2D b) {
        if (nrows() != b.nrows() || ncols() != b.ncols()) {
            throw new IllegalArgumentException("Matrix is not of same size.");
        }

        for (int i = 0; i < A.length; i++) {
            A[i] -= b.A[i];
        }
        return this;
    }

    public IntArray2D mul(IntArray2D b) {
        if (nrows() != b.nrows() || ncols() != b.ncols()) {
            throw new IllegalArgumentException("Matrix is not of same size.");
        }

        for (int i = 0; i < A.length; i++) {
            A[i] *= b.A[i];
        }
        return this;
    }

    public IntArray2D div(IntArray2D b) {
        if (nrows() != b.nrows() || ncols() != b.ncols()) {
            throw new IllegalArgumentException("Matrix is not of same size.");
        }

        for (int i = 0; i < A.length; i++) {
            A[i] /= b.A[i];
        }
        return this;
    }

    public IntArray2D add(int x) {
        for (int i = 0; i < A.length; i++) {
            A[i] += x;
        }

        return this;
    }

    public IntArray2D sub(int x) {
        for (int i = 0; i < A.length; i++) {
            A[i] -= x;
        }

        return this;
    }

    public IntArray2D mul(int x) {
        for (int i = 0; i < A.length; i++) {
            A[i] *= x;
        }

        return this;
    }

    public IntArray2D div(int x) {
        for (int i = 0; i < A.length; i++) {
            A[i] /= x;
        }

        return this;
    }

    public long sum() {
        long s = 0;
        for (int i = 0; i < A.length; i++) {
            s += A[i];
        }

        return s;
    }

    @Override
    public String toString() {
        return toString(false);
    }

    /**
     * Returns the string representation of matrix.
     * @param full Print the full matrix if true. Otherwise,
     *             print only top left 7 x 7 submatrix.
     */
    public String toString(boolean full) {
        return full ? toString(nrows(), ncols()) : toString(7, 7);
    }

    /**
     * Returns the string representation of matrix.
     * @param m the number of rows to print.
     * @param n the number of columns to print.
     */
    public String toString(int m, int n) {
        StringBuilder sb = new StringBuilder();
        m = Math.min(m, nrows());
        n = Math.min(n, ncols());

        String newline = n < ncols() ? "...\n" : "\n";

        for (int i = 0; i < m; i++) {
            for (int j = 0; j < n; j++) {
                sb.append(String.format("%d  ", get(i, j)));
            }
            sb.append(newline);
        }

        if (m < nrows()) {
            sb.append("  ...\n");
        }

        return sb.toString();
    }
}