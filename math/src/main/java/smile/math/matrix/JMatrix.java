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
 * A pure Java implementation of DenseMatrix whose data is stored in a single 1D array of
 * doubles in column major order.
 */
public class JMatrix implements DenseMatrix {

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
    public JMatrix(double[][] A) {
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
     * Constructor of a column vector/matrix initialized with given array.
     * @param A the array of column vector.
     */
    public JMatrix(double[] A) {
        this(A.length, 1);
        System.arraycopy(A, 0, this.A, 0, A.length);
    }

    /**
     * Constructor of all-zero matrix.
     */
    public JMatrix(int rows, int cols) {
        this.nrows = rows;
        this.ncols = cols;
        A = new double[rows*cols];
    }

    /**
     * Constructor. Fill the matrix with given value.
     */
    public JMatrix(int rows, int cols, double value) {
        this(rows, cols);
        Arrays.fill(A, value);
    }

    /**
     * Constructor.
     * @param value the array of matrix values arranged in column major format
     */
    public JMatrix(int rows, int cols, double[] value) {
        this.nrows = rows;
        this.ncols = cols;
        this.A = value;
    }

    /**
     * Constructor of matrix with normal random values with given mean and standard dev.
     */
    public JMatrix(int rows, int cols, double mu, double sigma) {
        this(rows, cols);
        GaussianDistribution g = new GaussianDistribution(mu, sigma);

        int n = rows * cols;
        for (int i = 0; i < n; i++) {
            A[i] = g.rand();
        }
    }

    @Override
    public String toString() {
        return toString(false);
    }

    @Override
    public JMatrix copy() {
        return new JMatrix(nrows, ncols, A.clone());
    }

    @Override
    public double[] data() {
        return A;
    }

    @Override
    public JMatrix transpose() {
        JMatrix B = new JMatrix(ncols(), nrows());
        for (int i = 0; i < nrows(); i++) {
            for (int j = 0; j < ncols(); j++) {
                B.set(j, i, get(i, j));
            }
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
    public int ld() {
        return nrows;
    }

    @Override
    public double get(int i, int j) {
        return A[j*nrows + i];
    }

    @Override
    public double set(int i, int j, double x) {
        return A[j*nrows + i] = x;
    }

    @Override
    public double add(int i, int j, double x) {
        return A[j*nrows + i] += x;
    }

    @Override
    public double sub(int i, int j, double x) {
        return A[j*nrows + i] -= x;
    }

    @Override
    public double mul(int i, int j, double x) {
        return A[j*nrows + i] *= x;
    }

    @Override
    public double div(int i, int j, double x) {
        return A[j*nrows + i] /= x;
    }

    @Override
    public JMatrix add(DenseMatrix b) {
        if (b instanceof JMatrix) {
            return add((JMatrix) b);
        } else {
            if (nrows() != b.nrows() || ncols() != b.ncols()) {
                throw new IllegalArgumentException("Matrix is not of same size.");
            }

            int m = nrows();
            int n = ncols();

            for (int j = 0; j < n; j++) {
                for (int i = 0; i < m; i++) {
                    add(i, j, b.get(i, j));
                }
            }
            return this;
        }
    }

    public JMatrix add(JMatrix b) {
        if (nrows() != b.nrows() || ncols() != b.ncols()) {
            throw new IllegalArgumentException("Matrix is not of same size.");
        }

        for (int i = 0; i < A.length; i++) {
            A[i] += b.A[i];
        }
        return this;
    }

    @Override
    public DenseMatrix add(DenseMatrix b, DenseMatrix c) {
        if (b instanceof JMatrix && c instanceof JMatrix) {
            return add((JMatrix) b, (JMatrix) c);
        }

        if (nrows() != b.nrows() || ncols() != b.ncols()) {
            throw new IllegalArgumentException("Matrix is not of same size.");
        }

        if (nrows() != c.nrows() || ncols() != c.ncols()) {
            throw new IllegalArgumentException("Matrix is not of same size.");
        }

        int m = nrows();
        int n = ncols();

        for (int j = 0; j < n; j++) {
            for (int i = 0; i < m; i++) {
                c.set(i, j, get(i, j) + b.get(i, j));
            }
        }
        return c;
    }

    public JMatrix add(JMatrix b, JMatrix c) {
        if (nrows() != b.nrows() || ncols() != b.ncols()) {
            throw new IllegalArgumentException("Matrix is not of same size.");
        }

        if (nrows() != c.nrows() || ncols() != c.ncols()) {
            throw new IllegalArgumentException("Matrix is not of same size.");
        }

        for (int i = 0; i < A.length; i++) {
            c.A[i] = A[i] + b.A[i];
        }
        return c;
    }

    @Override
    public JMatrix sub(DenseMatrix b) {
        if (b instanceof JMatrix) {
            return sub((JMatrix) b);
        }

        if (nrows() != b.nrows() || ncols() != b.ncols()) {
            throw new IllegalArgumentException("Matrix is not of same size.");
        }

        int m = nrows();
        int n = ncols();

        for (int j = 0; j < n; j++) {
            for (int i = 0; i < m; i++) {
                sub(i, j, b.get(i, j));
            }
        }
        return this;
    }

    public JMatrix sub(JMatrix b) {
        if (nrows() != b.nrows() || ncols() != b.ncols()) {
            throw new IllegalArgumentException("Matrix is not of same size.");
        }

        for (int i = 0; i < A.length; i++) {
            A[i] -= b.A[i];
        }
        return this;
    }


    @Override
    public DenseMatrix sub(DenseMatrix b, DenseMatrix c) {
        if (b instanceof JMatrix && c instanceof JMatrix) {
            return sub((JMatrix) b, (JMatrix) c);
        }

        if (nrows() != b.nrows() || ncols() != b.ncols()) {
            throw new IllegalArgumentException("Matrix is not of same size.");
        }

        if (nrows() != c.nrows() || ncols() != c.ncols()) {
            throw new IllegalArgumentException("Matrix is not of same size.");
        }

        int m = nrows();
        int n = ncols();

        for (int j = 0; j < n; j++) {
            for (int i = 0; i < m; i++) {
                c.set(i, j, get(i, j) - b.get(i, j));
            }
        }
        return c;
    }

    public JMatrix sub(JMatrix b, JMatrix c) {
        if (nrows() != b.nrows() || ncols() != b.ncols()) {
            throw new IllegalArgumentException("Matrix is not of same size.");
        }

        if (nrows() != c.nrows() || ncols() != c.ncols()) {
            throw new IllegalArgumentException("Matrix is not of same size.");
        }

        for (int i = 0; i < A.length; i++) {
            c.A[i] = A[i] - b.A[i];
        }
        return c;
    }

    @Override
    public JMatrix mul(DenseMatrix b) {
        if (b instanceof JMatrix) {
            return mul((JMatrix) b);
        }

        if (nrows() != b.nrows() || ncols() != b.ncols()) {
            throw new IllegalArgumentException("Matrix is not of same size.");
        }

        int m = nrows();
        int n = ncols();

        for (int j = 0; j < n; j++) {
            for (int i = 0; i < m; i++) {
                mul(i, j, b.get(i, j));
            }
        }
        return this;
    }

    public JMatrix mul(JMatrix b) {
        if (nrows() != b.nrows() || ncols() != b.ncols()) {
            throw new IllegalArgumentException("Matrix is not of same size.");
        }

        for (int i = 0; i < A.length; i++) {
            A[i] *= b.A[i];
        }
        return this;
    }

    @Override
    public DenseMatrix mul(DenseMatrix b, DenseMatrix c) {
        if (b instanceof JMatrix && c instanceof JMatrix) {
            return mul((JMatrix) b, (JMatrix) c);
        }

        if (nrows() != b.nrows() || ncols() != b.ncols()) {
            throw new IllegalArgumentException("Matrix is not of same size.");
        }

        if (nrows() != c.nrows() || ncols() != c.ncols()) {
            throw new IllegalArgumentException("Matrix is not of same size.");
        }

        int m = nrows();
        int n = ncols();

        for (int j = 0; j < n; j++) {
            for (int i = 0; i < m; i++) {
                c.set(i, j, get(i, j) * b.get(i, j));
            }
        }
        return c;
    }

    public JMatrix mul(JMatrix b, JMatrix c) {
        if (nrows() != b.nrows() || ncols() != b.ncols()) {
            throw new IllegalArgumentException("Matrix is not of same size.");
        }

        if (nrows() != c.nrows() || ncols() != c.ncols()) {
            throw new IllegalArgumentException("Matrix is not of same size.");
        }

        for (int i = 0; i < A.length; i++) {
            c.A[i] = A[i] * b.A[i];
        }
        return c;
    }

    @Override
    public JMatrix div(DenseMatrix b) {
        if (b instanceof JMatrix) {
            return div((JMatrix) b);
        }

        if (nrows() != b.nrows() || ncols() != b.ncols()) {
            throw new IllegalArgumentException("Matrix is not of same size.");
        }

        int m = nrows();
        int n = ncols();

        for (int j = 0; j < n; j++) {
            for (int i = 0; i < m; i++) {
                div(i, j, b.get(i, j));
            }
        }
        return this;
    }

    public JMatrix div(JMatrix b) {
        if (nrows() != b.nrows() || ncols() != b.ncols()) {
            throw new IllegalArgumentException("Matrix is not of same size.");
        }

        for (int i = 0; i < A.length; i++) {
            A[i] /= b.A[i];
        }
        return this;
    }

    @Override
    public DenseMatrix div(DenseMatrix b, DenseMatrix c) {
        if (b instanceof JMatrix && c instanceof JMatrix) {
            return div((JMatrix) b, (JMatrix) c);
        }

        if (nrows() != b.nrows() || ncols() != b.ncols()) {
            throw new IllegalArgumentException("Matrix is not of same size.");
        }

        if (nrows() != c.nrows() || ncols() != c.ncols()) {
            throw new IllegalArgumentException("Matrix is not of same size.");
        }

        int m = nrows();
        int n = ncols();

        for (int j = 0; j < n; j++) {
            for (int i = 0; i < m; i++) {
                c.set(i, j, get(i, j) / b.get(i, j));
            }
        }
        return c;
    }

    public JMatrix div(JMatrix b, JMatrix c) {
        if (nrows() != b.nrows() || ncols() != b.ncols()) {
            throw new IllegalArgumentException("Matrix is not of same size.");
        }

        if (nrows() != c.nrows() || ncols() != c.ncols()) {
            throw new IllegalArgumentException("Matrix is not of same size.");
        }

        for (int i = 0; i < A.length; i++) {
            c.A[i] = A[i] / b.A[i];
        }
        return c;
    }

    @Override
    public JMatrix add(double x) {
        for (int i = 0; i < A.length; i++) {
            A[i] += x;
        }

        return this;
    }

    @Override
    public DenseMatrix add(double x, DenseMatrix c) {
        if (c instanceof JMatrix) {
            return add(x, (JMatrix)c);
        }

        if (nrows() != c.nrows() || ncols() != c.ncols()) {
            throw new IllegalArgumentException("Matrix is not of same size.");
        }

        for (int j = 0; j < ncols; j++) {
            for (int i = 0; i < nrows; i++) {
                c.set(i, j, get(i, j) + x);
            }
        }

        return c;
    }

    public JMatrix add(double x, JMatrix c) {
        if (nrows() != c.nrows() || ncols() != c.ncols()) {
            throw new IllegalArgumentException("Matrix is not of same size.");
        }

        for (int i = 0; i < A.length; i++) {
            c.A[i] = A[i] + x;
        }

        return c;
    }

    @Override
    public JMatrix sub(double x) {
        for (int i = 0; i < A.length; i++) {
            A[i] -= x;
        }

        return this;
    }

    @Override
    public DenseMatrix sub(double x, DenseMatrix c) {
        if (c instanceof JMatrix) {
            return sub(x, (JMatrix) c);
        }

        if (nrows() != c.nrows() || ncols() != c.ncols()) {
            throw new IllegalArgumentException("Matrix is not of same size.");
        }

        for (int j = 0; j < ncols; j++) {
            for (int i = 0; i < nrows; i++) {
                c.set(i, j, get(i, j) - x);
            }
        }

        return c;
    }

    public JMatrix sub(double x, JMatrix c) {
        if (nrows() != c.nrows() || ncols() != c.ncols()) {
            throw new IllegalArgumentException("Matrix is not of same size.");
        }

        for (int i = 0; i < A.length; i++) {
            c.A[i] = A[i] - x;
        }

        return c;
    }

    @Override
    public JMatrix mul(double x) {
        for (int i = 0; i < A.length; i++) {
            A[i] *= x;
        }

        return this;
    }

    @Override
    public DenseMatrix mul(double x, DenseMatrix c) {
        if (c instanceof JMatrix) {
            return mul(x, (JMatrix)c);
        }

        if (nrows() != c.nrows() || ncols() != c.ncols()) {
            throw new IllegalArgumentException("Matrix is not of same size.");
        }

        for (int j = 0; j < ncols; j++) {
            for (int i = 0; i < nrows; i++) {
                c.set(i, j, get(i, j) * x);
            }
        }

        return c;
    }

    public JMatrix mul(double x, JMatrix c) {
        if (nrows() != c.nrows() || ncols() != c.ncols()) {
            throw new IllegalArgumentException("Matrix is not of same size.");
        }

        for (int i = 0; i < A.length; i++) {
            c.A[i] = A[i] * x;
        }

        return c;
    }

    @Override
    public JMatrix div(double x) {
        for (int i = 0; i < A.length; i++) {
            A[i] /= x;
        }

        return this;
    }

    @Override
    public DenseMatrix div(double x, DenseMatrix c) {
        if (c instanceof JMatrix) {
            return div(x, (JMatrix) c);
        }

        if (nrows() != c.nrows() || ncols() != c.ncols()) {
            throw new IllegalArgumentException("Matrix is not of same size.");
        }

        for (int j = 0; j < ncols; j++) {
            for (int i = 0; i < nrows; i++) {
                c.set(i, j, get(i, j) / x);
            }
        }

        return c;
    }

    public JMatrix div(double x, JMatrix c) {
        if (nrows() != c.nrows() || ncols() != c.ncols()) {
            throw new IllegalArgumentException("Matrix is not of same size.");
        }

        for (int i = 0; i < A.length; i++) {
            c.A[i] = A[i] / x;
        }

        return c;
    }

    @Override
    public JMatrix replaceNaN(double x) {
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
    public JMatrix ata() {
        JMatrix C = new JMatrix(ncols, ncols);
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
    public JMatrix aat() {
        JMatrix C = new JMatrix(nrows, nrows);
        for (int k = 0; k < ncols; k++) {
            for (int i = 0; i < nrows; i++) {
                for (int j = 0; j < nrows; j++) {
                    C.add(i, j, get(i, k) * get(j, k));
                }
            }
        }
        return C;
    }

    @Override
    public double[] ax(double[] x, double[] y) {
        int n = Math.min(nrows, y.length);
        int p = Math.min(ncols, x.length);

        Arrays.fill(y, 0.0);
        for (int k = 0; k < p; k++) {
            for (int i = 0; i < n; i++) {
                y[i] += get(i, k) * x[k];
            }
        }

        return y;
    }

    @Override
    public double[] axpy(double[] x, double[] y) {
        int n = Math.min(nrows, y.length);
        int p = Math.min(ncols, x.length);

        for (int k = 0; k < p; k++) {
            for (int i = 0; i < n; i++) {
                y[i] += get(i, k) * x[k];
            }
        }

        return y;
    }

    @Override
    public double[] axpy(double[] x, double[] y, double b) {
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

        return y;
    }

    @Override
    public double[] atx(double[] x, double[] y) {
        int n = Math.min(ncols, y.length);
        int p = Math.min(nrows, x.length);

        Arrays.fill(y, 0.0);
        for (int i = 0; i < n; i++) {
            for (int k = 0; k < p; k++) {
                y[i] += get(k, i) * x[k];
            }
        }

        return y;
    }

    @Override
    public double[] atxpy(double[] x, double[] y) {
        int n = Math.min(ncols, y.length);
        int p = Math.min(nrows, x.length);

        for (int i = 0; i < n; i++) {
            for (int k = 0; k < p; k++) {
                y[i] += get(k, i) * x[k];
            }
        }

        return y;
    }

    @Override
    public double[] atxpy(double[] x, double[] y, double b) {
        int n = Math.min(ncols, y.length);
        int p = Math.min(nrows, x.length);

        for (int i = 0; i < n; i++) {
            y[i] *= b;
            for (int k = 0; k < p; k++) {
                y[i] += get(k, i) * x[k];
            }
        }

        return y;
    }

    @Override
    public JMatrix abmm(DenseMatrix B) {
        if (ncols() != B.nrows()) {
            throw new IllegalArgumentException(String.format("Matrix multiplication A * B: %d x %d vs %d x %d", nrows(), ncols(), B.nrows(), B.ncols()));
        }

        JMatrix C = new JMatrix(nrows, B.ncols());
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
    public JMatrix abtmm(DenseMatrix B) {
        if (ncols() != B.ncols()) {
            throw new IllegalArgumentException(String.format("Matrix multiplication A * B': %d x %d vs %d x %d", nrows(), ncols(), B.nrows(), B.ncols()));
        }

        JMatrix C = new JMatrix(nrows, B.nrows());
        for (int k = 0; k < ncols; k++) {
            for (int i = 0; i < nrows; i++) {
                for (int j = 0; j < B.nrows(); j++) {
                    C.add(i, j, get(i, k) * B.get(j, k));
                }
            }
        }
        return C;
    }

    @Override
    public JMatrix atbmm(DenseMatrix B) {
        if (nrows() != B.nrows()) {
            throw new IllegalArgumentException(String.format("Matrix multiplication A' * B: %d x %d vs %d x %d", nrows(), ncols(), B.nrows(), B.ncols()));
        }

        JMatrix C = new JMatrix(ncols, B.ncols());
        for (int i = 0; i < ncols; i++) {
            for (int j = 0; j < B.ncols(); j++) {
                double v = 0.0;
                for (int k = 0; k < nrows; k++) {
                    v += get(k, i) * B.get(k, j);
                }
                C.set(i, j, v);
            }
        }
        return C;
    }
}
