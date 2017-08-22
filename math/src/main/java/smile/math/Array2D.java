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

package smile.math;

import java.util.Arrays;

/**
 * 2-dimensional array of doubles. Java doesn't have real multidimensional
 * arrays. They are just array of arrays, which are not friendly to modern
 * CPU due to frequent cache miss. The extra (multiple times) array index
 * checking also significantly reduces the performance. This class solves
 * these pain points by storing data in a single 1D array of doubles in
 * row major order. Note that this class is simple by design, as a replacement
 * of double[][] in case of row by row access. For advanced matrix computation,
 * please use <code>Matrix</code>.
 *
 * @see smile.math.matrix.Matrix
 */
public class Array2D {

    /**
     * The array storage.
     */
    private double[] A;
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
    public Array2D(double[][] A) {
        this.nrows = A.length;
        this.ncols = A[0].length;
        this.A = new double[nrows*ncols];

        int pos = 0;
        for (int i = 0; i < nrows; i++) {
            System.arraycopy(A[i], 0, this.A, pos, ncols);
            pos += ncols;
        }
    }

    /**
     * Constructor of all-zero matrix.
     */
    public Array2D(int rows, int cols) {
        this.nrows = rows;
        this.ncols = cols;
        A = new double[rows*cols];
    }

    /**
     * Constructor. Fill the matrix with given value.
     */
    public Array2D(int rows, int cols, double value) {
        this(rows, cols);
        if (value != 0.0)
            Arrays.fill(A, value);
    }

    /**
     * Constructor.
     * @param value the array of matrix values arranged in row major format
     */
    public Array2D(int rows, int cols, double[] value) {
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
    public double apply(int i, int j) {
        return A[i*ncols + j];
    }

    /** Returns A(i, j). */
    public double get(int i, int j) {
        return A[i*ncols + j];
    }

    /** Sets A(i, j). */
    public double set(int i, int j, double x) {
        return A[i*ncols + j] = x;
    }

    public double add(int i, int j, double x) {
        return A[i*ncols + j] += x;
    }

    public double sub(int i, int j, double x) {
        return A[i*ncols + j] -= x;
    }

    public double mul(int i, int j, double x) {
        return A[i*ncols + j] *= x;
    }

    public double div(int i, int j, double x) {
        return A[i*ncols + j] /= x;
    }

    public Array2D add(Array2D b) {
        if (nrows() != b.nrows() || ncols() != b.ncols()) {
            throw new IllegalArgumentException("Matrix is not of same size.");
        }

        for (int i = 0; i < A.length; i++) {
            A[i] += b.A[i];
        }
        return this;
    }

    public Array2D sub(Array2D b) {
        if (nrows() != b.nrows() || ncols() != b.ncols()) {
            throw new IllegalArgumentException("Matrix is not of same size.");
        }

        for (int i = 0; i < A.length; i++) {
            A[i] -= b.A[i];
        }
        return this;
    }

    public Array2D mul(Array2D b) {
        if (nrows() != b.nrows() || ncols() != b.ncols()) {
            throw new IllegalArgumentException("Matrix is not of same size.");
        }

        for (int i = 0; i < A.length; i++) {
            A[i] *= b.A[i];
        }
        return this;
    }

    public Array2D div(Array2D b) {
        if (nrows() != b.nrows() || ncols() != b.ncols()) {
            throw new IllegalArgumentException("Matrix is not of same size.");
        }

        for (int i = 0; i < A.length; i++) {
            A[i] /= b.A[i];
        }
        return this;
    }

    public Array2D add(double x) {
        for (int i = 0; i < A.length; i++) {
            A[i] += x;
        }

        return this;
    }

    public Array2D sub(double x) {
        for (int i = 0; i < A.length; i++) {
            A[i] -= x;
        }

        return this;
    }

    public Array2D mul(double x) {
        for (int i = 0; i < A.length; i++) {
            A[i] *= x;
        }

        return this;
    }

    public Array2D div(double x) {
        for (int i = 0; i < A.length; i++) {
            A[i] /= x;
        }

        return this;
    }

    public Array2D replaceNaN(double x) {
        for (int i = 0; i < A.length; i++) {
            if (Double.isNaN(A[i])) {
                A[i] = x;
            }
        }

        return this;
    }

    public double sum() {
        double s = 0.0;
        for (int i = 0; i < A.length; i++) {
            s += A[i];
        }

        return s;
    }
}