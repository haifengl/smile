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

import java.util.Arrays;
import smile.math.Math;
import smile.stat.distribution.GaussianDistribution;

/**
 * A dense matrix whose data is stored in a single 1D array of
 * doubles in row major order.
 */
public class RowMajorMatrix implements DenseMatrix {

    /**
     * The matrix storage.
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
    public RowMajorMatrix(double[][] A) {
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
    public RowMajorMatrix(int rows, int cols) {
        this.nrows = rows;
        this.ncols = cols;
        A = new double[rows*cols];
    }

    /**
     * Constructor. Fill the matrix with given value.
     */
    public RowMajorMatrix(int rows, int cols, double value) {
        this(rows, cols);
        if (value != 0.0)
            Arrays.fill(A, value);
    }

    /**
     * Constructor.
     * @param value the array of matrix values arranged in row major format
     */
    public RowMajorMatrix(int rows, int cols, double[] value) {
        this.nrows = rows;
        this.ncols = cols;
        this.A = value;
    }

    /**
     * Constructor of a square diagonal matrix with the elements of vector diag on the main diagonal.
     */
    public RowMajorMatrix(double[] diag) {
        this(diag.length, diag.length);
        for (int i = 0; i < diag.length; i++)
            set(i, i, diag[i]);
    }

    /**
     * Constructor of matrix with normal random values with given mean and standard dev.
     */
    public RowMajorMatrix(int rows, int cols, double mu, double sigma) {
        this(rows, cols);
        GaussianDistribution g = new GaussianDistribution(mu, sigma);

        int n = rows * cols;
        for (int i = 0; i < n; i++) {
            A[i] = g.rand();
        }
    }

    /**
     * Returns an n-by-n identity matrix with ones on the main diagonal and zeros elsewhere.
     */
    public static RowMajorMatrix eye(int n) {
        return eye(n, n);
    }

    /**
     * Returns an n-by-n identity matrix with ones on the main diagonal and zeros elsewhere.
     */
    public static RowMajorMatrix eye(int m, int n) {
        RowMajorMatrix matrix = new RowMajorMatrix(m, n);
        int l = Math.min(m, n);
        for (int i = 0; i < l; i++) {
            matrix.set(i, i, 1.0);
        }

        return matrix;
    }

    @Override
    public String toString() {
        return toString(false);
    }

    @Override
    public RowMajorMatrix copy() {
        return new RowMajorMatrix(nrows, ncols, A.clone());
    }

    /**
     * Returns the transpose that shares the same underlying array
     * with this matrix. The result matrix should only be used for
     * read only operations, which is the typical cases in linear algebra.
     */
    @Override
    public ColumnMajorMatrix transpose() {
        return new ColumnMajorMatrix(ncols, nrows, A);
    }

    @Override
    public int nrows() {
        return nrows;
    }

    @Override
    public int ncols() {
        return ncols;
    }

    @Override
    public double get(int i, int j) {
        return A[i*ncols + j];
    }

    @Override
    public double set(int i, int j, double x) {
        return A[i*ncols + j] = x;
    }

    @Override
    public double add(int i, int j, double x) {
        return A[i*ncols + j] += x;
    }

    @Override
    public double sub(int i, int j, double x) {
        return A[i*ncols + j] -= x;
    }

    @Override
    public double mul(int i, int j, double x) {
        return A[i*ncols + j] *= x;
    }

    @Override
    public double div(int i, int j, double x) {
        return A[i*ncols + j] /= x;
    }

    public RowMajorMatrix add(RowMajorMatrix b) {
        if (nrows() != b.nrows() || ncols() != b.ncols()) {
            throw new IllegalArgumentException("Matrix is not of same size.");
        }

        for (int i = 0; i < A.length; i++) {
            A[i] += b.A[i];
        }
        return this;
    }

    public RowMajorMatrix sub(RowMajorMatrix b) {
        if (nrows() != b.nrows() || ncols() != b.ncols()) {
            throw new IllegalArgumentException("Matrix is not of same size.");
        }

        for (int i = 0; i < A.length; i++) {
            A[i] -= b.A[i];
        }
        return this;
    }

    public RowMajorMatrix mul(RowMajorMatrix b) {
        if (nrows() != b.nrows() || ncols() != b.ncols()) {
            throw new IllegalArgumentException("Matrix is not of same size.");
        }

        for (int i = 0; i < A.length; i++) {
            A[i] *= b.A[i];
        }
        return this;
    }

    public RowMajorMatrix div(RowMajorMatrix b) {
        if (nrows() != b.nrows() || ncols() != b.ncols()) {
            throw new IllegalArgumentException("Matrix is not of same size.");
        }

        for (int i = 0; i < A.length; i++) {
            A[i] /= b.A[i];
        }
        return this;
    }

    @Override
    public RowMajorMatrix add(double x) {
        for (int i = 0; i < A.length; i++) {
            A[i] += x;
        }

        return this;
    }

    @Override
    public RowMajorMatrix sub(double x) {
        for (int i = 0; i < A.length; i++) {
            A[i] -= x;
        }

        return this;
    }

    @Override
    public RowMajorMatrix mul(double x) {
        for (int i = 0; i < A.length; i++) {
            A[i] *= x;
        }

        return this;
    }

    @Override
    public RowMajorMatrix div(double x) {
        for (int i = 0; i < A.length; i++) {
            A[i] /= x;
        }

        return this;
    }

    @Override
    public RowMajorMatrix replaceNaN(double x) {
        for (int i = 0; i < A.length; i++) {
            if (Double.isNaN(A[i])) {
                A[i] = x;
            }
        }

        return this;
    }

    @Override
    public double sum() {
        double s = 0.0;
        for (int i = 0; i < A.length; i++) {
            s += A[i];
        }

        return s;
    }

    @Override
    public RowMajorMatrix ata() {
        RowMajorMatrix C = new RowMajorMatrix(ncols, ncols);
        for (int k = 0; k < nrows; k++) {
            for (int i = 0; i < ncols; i++) {
                for (int j = 0; j < ncols; j++) {
                    C.add(i, j, get(k, i) * get(k, j));
                }
            }
        }
        return C;
    }

    @Override
    public RowMajorMatrix aat() {
        RowMajorMatrix C = new RowMajorMatrix(nrows, nrows);
        for (int i = 0; i < nrows; i++) {
            for (int j = 0; j < nrows; j++) {
                double v = 0.0;
                for (int k = 0; k < ncols; k++) {
                    v += get(i, k) * get(j, k);
                }
                C.set(i, j, v);
            }
        }
        return C;
    }

    @Override
    public double[] ax(double[] x, double[] y) {
        Arrays.fill(y, 0.0);
        for (int i = 0, j = 0; i < nrows; i++) {
            for (int k = 0; k < ncols; k++, j++) {
                y[i] += A[j] * x[k];
            }
        }

        return y;
    }

    @Override
    public double[] axpy(double[] x, double[] y) {
        for (int i = 0, j = 0; i < nrows; i++) {
            for (int k = 0; k < ncols; k++, j++) {
                y[i] += A[j] * x[k];
            }
        }

        return y;
    }

    @Override
    public double[] axpy(double[] x, double[] y, double b) {
        for (int i = 0, j = 0; i < nrows; i++) {
            y[i] *= b;
            for (int k = 0; k < ncols; k++, j++) {
                y[i] += A[j] * x[k];
            }
        }

        return y;
    }

    @Override
    public double[] atx(double[] x, double[] y) {
        Arrays.fill(y, 0.0);
        for (int k = 0, j = 0; k < nrows; k++) {
            for (int i = 0; i < ncols; i++, j++) {
                y[i] += A[j] * x[k];
            }
        }

        return y;
    }

    @Override
    public double[] atxpy(double[] x, double[] y) {
        for (int k = 0, j = 0; k < nrows; k++) {
            for (int i = 0; i < ncols; i++, j++) {
                y[i] += A[j] * x[k];
            }
        }

        return y;
    }

    @Override
    public double[] atxpy(double[] x, double[] y, double b) {
        for (int i = 0; i < y.length; i++) {
            y[i] *= b;
        }

        for (int k = 0, j = 0; k < nrows; k++) {
            for (int i = 0; i < ncols; i++, j++) {
                y[i] += A[j] * x[k];
            }
        }

        return y;
    }

    @Override
    public RowMajorMatrix abmm(DenseMatrix B) {
        if (ncols() != B.nrows()) {
            throw new IllegalArgumentException(String.format("Matrix multiplication A * B: %d x %d vs %d x %d", nrows(), ncols(), B.nrows(), B.ncols()));
        }

        RowMajorMatrix C = new RowMajorMatrix(nrows, B.ncols());
        for (int i = 0; i < nrows; i++) {
            for (int j = 0; j < B.ncols(); j++) {
                double v = 0.0;
                for (int k = 0; k < ncols; k++) {
                    v += get(i, k) * B.get(k, j);
                }
                C.set(i, j, v);
            }
        }
        return C;
    }

    @Override
    public RowMajorMatrix abtmm(DenseMatrix B) {
        if (ncols() != B.ncols()) {
            throw new IllegalArgumentException(String.format("Matrix multiplication A * B': %d x %d vs %d x %d", nrows(), ncols(), B.nrows(), B.ncols()));
        }

        RowMajorMatrix C = new RowMajorMatrix(nrows, B.nrows());
        for (int i = 0; i < nrows; i++) {
            for (int j = 0; j < B.nrows(); j++) {
                double v = 0.0;
                for (int k = 0; k < ncols; k++) {
                    v += get(i, k) * B.get(j, k);
                }
                C.set(i, j, v);
            }
        }
        return C;
    }

    @Override
    public RowMajorMatrix atbmm(DenseMatrix B) {
        if (nrows() != B.nrows()) {
            throw new IllegalArgumentException(String.format("Matrix multiplication A' * B: %d x %d vs %d x %d", nrows(), ncols(), B.nrows(), B.ncols()));
        }

        RowMajorMatrix C = new RowMajorMatrix(ncols, B.ncols());
        for (int k = 0; k < nrows; k++) {
            for (int i = 0; i < ncols; i++) {
                for (int j = 0; j < B.ncols(); j++) {
                    C.add(i, j, get(k, i) * B.get(k, j));
                }
            }
        }
        return C;
    }
}
