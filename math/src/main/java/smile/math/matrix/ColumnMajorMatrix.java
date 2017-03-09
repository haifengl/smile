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
 * doubles in column major order.
 */
public class ColumnMajorMatrix extends DenseMatrix implements MatrixMultiplication<ColumnMajorMatrix, ColumnMajorMatrix> {

    /**
     * The original matrix.
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
    public ColumnMajorMatrix(double[][] A) {
        this(A, false);
    }

    /**
     * Constructor.
     * If the matrix is updated, no check on if the matrix is symmetric.
     * @param A the array of matrix.
     * @param symmetric true if the matrix is symmetric.
     */
    public ColumnMajorMatrix(double[][] A, boolean symmetric) {
        this(A, symmetric, false);
    }

    /**
     * Constructor.
     * If the matrix is updated, no check on if the matrix is symmetric
     * and/or positive definite. The symmetric and positive definite
     * properties are intended for read-only matrices.
     * @param A the array of matrix.
     * @param symmetric true if the matrix is symmetric.
     * @param positive true if the matrix is positive definite.
     */
    public ColumnMajorMatrix(double[][] A, boolean symmetric, boolean positive) {
        super(symmetric, positive);

        if (symmetric && A.length != A[0].length) {
            throw new IllegalArgumentException("A is not square");
        }

        this.nrows = A.length;
        this.ncols = A[0].length;
        this.A = new double[nrows*ncols];

        for (int i = 0; i < nrows; i++) {
            for (int j = 0; j < ncols; j++) {
                set(i, j, A[i][j]);
            }
        }
    }

    /**
     * Constructor of all-zero matrix.
     */
    public ColumnMajorMatrix(int rows, int cols) {
        this.nrows = rows;
        this.ncols = cols;
        A = new double[rows*cols];
    }

    /**
     * Constructor. Fill the matrix with given value.
     */
    public ColumnMajorMatrix(int rows, int cols, double value) {
        this(rows, cols);
        Arrays.fill(A, value);
    }

    /**
     * Constructor of matrix with normal random values with given mean and standard dev.
     */
    public ColumnMajorMatrix(int rows, int cols, double mu, double sigma) {
        this(rows, cols);
        GaussianDistribution g = new GaussianDistribution(mu, sigma);

        int n = rows * cols;
        for (int i = 0; i < n; i++) {
            A[i] = g.rand();
        }
    }

    @Override
    public ColumnMajorMatrix transpose() {
        ColumnMajorMatrix B = new ColumnMajorMatrix(ncols, nrows);
        for (int k = 0; k < A.length; k++) {
            int i = k / nrows;
            int j = k % nrows;
            B.set(j, i, A[k]);
        }

        return B;
    }

    public RowMajorMatrix toRowMajor() {
        RowMajorMatrix B = new RowMajorMatrix(nrows, ncols);
        for (int k = 0; k < A.length; k++) {
            int i = k / nrows;
            int j = k % nrows;
            B.set(i, j, A[k]);
        }

        return B;
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
        return A[j*nrows + i];
    }

    @Override
    public ColumnMajorMatrix set(int i, int j, double x) {
        A[j*nrows + i] = x;
        return this;
    }

    @Override
    public ColumnMajorMatrix add(int i, int j, double x) {
        A[j*nrows + i] += x;
        return this;
    }

    @Override
    public ColumnMajorMatrix sub(int i, int j, double x) {
        A[j*nrows + i] -= x;
        return this;
    }

    @Override
    public ColumnMajorMatrix mul(int i, int j, double x) {
        A[j*nrows + i] *= x;
        return this;
    }

    @Override
    public ColumnMajorMatrix div(int i, int j, double x) {
        A[j*nrows + i] /= x;
        return this;
    }

    @Override
    public ColumnMajorMatrix ata() {
        ColumnMajorMatrix C = new ColumnMajorMatrix(ncols, ncols);
        for (int i = 0; i < ncols; i++) {
            for (int j = 0; j < ncols; j++) {
                double v = 0.0;
                for (int k = 0; k < nrows; k++) {
                    v += get(k, i) * get(k, j);
                }
                C.set(i, j, v);
            }
        }
        return C;
    }

    @Override
    public ColumnMajorMatrix aat() {
        ColumnMajorMatrix at = transpose();
        RowMajorMatrix row = toRowMajor();
        ColumnMajorMatrix C = new ColumnMajorMatrix(nrows, nrows);
        for (int i = 0; i < nrows; i++) {
            for (int j = 0; j < nrows; j++) {
                double v = 0.0;
                for (int k = 0; k < ncols; k++) {
                    v += row.get(i, k) * at.get(k, j);
                }
                C.set(i, j, v);
            }
        }
        return C;
    }

    @Override
    public void ax(double[] x, double[] y) {
        int n = Math.min(nrows, y.length);
        int p = Math.min(ncols, x.length);

        Arrays.fill(y, 0.0);
        for (int k = 0; k < p; k++) {
            for (int i = 0; i < n; i++) {
                y[i] += get(i, k) * x[k];
            }
        }
    }

    @Override
    public void axpy(double[] x, double[] y) {
        int n = Math.min(nrows, y.length);
        int p = Math.min(ncols, x.length);

        for (int k = 0; k < p; k++) {
            for (int i = 0; i < n; i++) {
                y[i] += get(i, k) * x[k];
            }
        }
    }

    @Override
    public void axpy(double[] x, double[] y, double b) {
        int n = Math.min(nrows, y.length);
        int p = Math.min(ncols, x.length);

        for (int i = 0; i < n; i++) {
            y[i] *= b;
        }

        for (int k = 0; k < p; k++) {
            for (int i = 0; i < n; i++) {
                y[i] += get(i, k) * x[k];
            }
        }
    }

    @Override
    public void atx(double[] x, double[] y) {
        int n = Math.min(ncols, y.length);
        int p = Math.min(nrows, x.length);

        Arrays.fill(y, 0.0);
        for (int i = 0; i < n; i++) {
            for (int k = 0; k < p; k++) {
                y[i] += get(k, i) * x[k];
            }
        }
    }

    @Override
    public void atxpy(double[] x, double[] y) {
        int n = Math.min(ncols, y.length);
        int p = Math.min(nrows, x.length);

        for (int i = 0; i < n; i++) {
            for (int k = 0; k < p; k++) {
                y[i] += get(k, i) * x[k];
            }
        }
    }

    @Override
    public void atxpy(double[] x, double[] y, double b) {
        int n = Math.min(ncols, y.length);
        int p = Math.min(nrows, x.length);

        for (int i = 0; i < n; i++) {
            y[i] *= b;
            for (int k = 0; k < p; k++) {
                y[i] += get(k, i) * x[k];
            }
        }
    }

    @Override
    public ColumnMajorMatrix mm(ColumnMajorMatrix B) {
        if (ncols() != B.nrows()) {
            throw new IllegalArgumentException(String.format("Matrix multiplication A * B: %d x %d vs %d x %d", nrows(), ncols(), B.nrows(), B.ncols()));
        }

        ColumnMajorMatrix C = new ColumnMajorMatrix(nrows, B.ncols());
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
}
