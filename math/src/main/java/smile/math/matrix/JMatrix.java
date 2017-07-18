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
    private static final long serialVersionUID = 1L;

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
     * Constructor of a column vector/matrix with given array as the internal storage.
     * @param A the array of column vector.
     */
    public JMatrix(double[] A) {
        this.nrows = A.length;
        this.ncols = 1;
        this.A = A;
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

    /**
     * LU decomposition is computed by a "left-looking", dot-product, Crout/Doolittle algorithm.
     */
    @Override
    public LU lu() {
        int m = nrows();
        int n = ncols();

        int[] piv = new int[m];
        for (int i = 0; i < m; i++) {
            piv[i] = i;
        }

        int pivsign = 1;
        double[] LUcolj = new double[m];

        for (int j = 0; j < n; j++) {

            // Make a copy of the j-th column to localize references.
            for (int i = 0; i < m; i++) {
                LUcolj[i] = get(i, j);
            }

            // Apply previous transformations.
            for (int i = 0; i < m; i++) {
                // Most of the time is spent in the following dot product.

                int kmax = Math.min(i, j);
                double s = 0.0;
                for (int k = 0; k < kmax; k++) {
                    s += get(i, k) * LUcolj[k];
                }

                LUcolj[i] -= s;
                set(i, j, LUcolj[i]);
            }

            // Find pivot and exchange if necessary.
            int p = j;
            for (int i = j + 1; i < m; i++) {
                if (Math.abs(LUcolj[i]) > Math.abs(LUcolj[p])) {
                    p = i;
                }
            }
            if (p != j) {
                for (int k = 0; k < n; k++) {
                    double t = get(p, k);
                    set(p, k, get(j, k));
                    set(j, k, t);
                }
                int k = piv[p];
                piv[p] = piv[j];
                piv[j] = k;
                pivsign = -pivsign;
            }

            // Compute multipliers.
            if (j < m & get(j, j) != 0.0) {
                for (int i = j + 1; i < m; i++) {
                    div(i, j, get(j, j));
                }
            }
        }

        boolean singular = false;
        for (int j = 0; j < n; j++) {
            if (get(j, j) == 0) {
                singular = true;
                break;
            }
        }

        return new LU(this, piv, pivsign, singular);
    }

    /**
     * Cholesky decomposition for symmetric and positive definite matrix.
     * Only the lower triangular part will be used in the decomposition.
     *
     * @throws IllegalArgumentException if the matrix is not positive definite.
     */
    @Override
    public Cholesky cholesky() {
        if (nrows() != ncols()) {
            throw new UnsupportedOperationException("Cholesky decomposition on non-square matrix");
        }

        int n = nrows();

        // Main loop.
        for (int j = 0; j < n; j++) {
            double d = 0.0;
            for (int k = 0; k < j; k++) {
                double s = 0.0;
                for (int i = 0; i < k; i++) {
                    s += get(k, i) * get(j, i);
                }
                s = (get(j, k) - s) / get(k, k);
                set(j, k, s);
                d = d + s * s;
            }
            d = get(j, j) - d;

            if (d < 0.0) {
                throw new IllegalArgumentException("The matrix is not positive definite.");
            }

            set(j, j, Math.sqrt(d));
        }

        return new Cholesky(this);
    }

    /**
     * QR Decomposition is computed by Householder reflections.
     */
    @Override
    public QR qr() {
        // Initialize.
        int m = nrows();
        int n = ncols();
        double[] rDiagonal = new double[n];

        // Main loop.
        for (int k = 0; k < n; k++) {
            // Compute 2-norm of k-th column without under/overflow.
            double nrm = 0.0;
            for (int i = k; i < m; i++) {
                nrm = Math.hypot(nrm, get(i, k));
            }

            if (nrm != 0.0) {
                // Form k-th Householder vector.
                if (get(k, k) < 0) {
                    nrm = -nrm;
                }
                for (int i = k; i < m; i++) {
                    div(i, k, nrm);
                }
                add(k, k, 1.0);

                // Apply transformation to remaining columns.
                for (int j = k + 1; j < n; j++) {
                    double s = 0.0;
                    for (int i = k; i < m; i++) {
                        s += get(i, k) * get(i, j);
                    }
                    s = -s / get(k, k);
                    for (int i = k; i < m; i++) {
                        add(i, j, s * get(i, k));
                    }
                }
            }
            rDiagonal[k] = -nrm;
        }

        boolean singular = false;
        for (int j = 0; j < rDiagonal.length; j++) {
            if (rDiagonal[j] == 0) {
                singular = true;
                break;
            }
        }

        return new QR(this, rDiagonal, singular);
    }

    @Override
    public SVD svd() {
        int m = nrows();
        int n = ncols();

        boolean flag;
        int i, its, j, jj, k, l = 0, nm = 0;
        double anorm, c, f, g, h, s, scale, x, y, z;
        g = scale = anorm = 0.0;

        DenseMatrix U = this;
        DenseMatrix V = Matrix.zeros(n, n);
        double[] w = new double[n];
        double[] rv1 = new double[n];

        for (i = 0; i < n; i++) {
            l = i + 2;
            rv1[i] = scale * g;
            g = s = scale = 0.0;

            if (i < m) {
                for (k = i; k < m; k++) {
                    scale += Math.abs(U.get(k, i));
                }

                if (scale != 0.0) {
                    for (k = i; k < m; k++) {
                        U.div(k, i, scale);
                        s += U.get(k, i) * U.get(k, i);
                    }

                    f = U.get(i, i);
                    g = -Math.copySign(Math.sqrt(s), f);
                    h = f * g - s;
                    U.set(i, i, f - g);
                    for (j = l - 1; j < n; j++) {
                        for (s = 0.0, k = i; k < m; k++) {
                            s += U.get(k, i) * U.get(k, j);
                        }
                        f = s / h;
                        for (k = i; k < m; k++) {
                            U.add(k, j, f * U.get(k, i));
                        }
                    }
                    for (k = i; k < m; k++) {
                        U.mul(k, i, scale);
                    }
                }
            }

            w[i] = scale * g;
            g = s = scale = 0.0;

            if (i + 1 <= m && i + 1 != n) {
                for (k = l - 1; k < n; k++) {
                    scale += Math.abs(U.get(i, k));
                }

                if (scale != 0.0) {
                    for (k = l - 1; k < n; k++) {
                        U.div(i, k, scale);
                        s += U.get(i, k) * U.get(i, k);
                    }

                    f = U.get(i, l - 1);
                    g = -Math.copySign(Math.sqrt(s), f);
                    h = f * g - s;
                    U.set(i, l - 1, f - g);

                    for (k = l - 1; k < n; k++) {
                        rv1[k] = U.get(i, k) / h;
                    }

                    for (j = l - 1; j < m; j++) {
                        for (s = 0.0, k = l - 1; k < n; k++) {
                            s += U.get(j, k) * U.get(i, k);
                        }

                        for (k = l - 1; k < n; k++) {
                            U.add(j, k, s * rv1[k]);
                        }
                    }

                    for (k = l - 1; k < n; k++) {
                        U.mul(i, k, scale);
                    }
                }
            }

            anorm = Math.max(anorm, (Math.abs(w[i]) + Math.abs(rv1[i])));
        }

        for (i = n - 1; i >= 0; i--) {
            if (i < n - 1) {
                if (g != 0.0) {
                    for (j = l; j < n; j++) {
                        V.set(j, i, (U.get(i, j) / U.get(i, l)) / g);
                    }
                    for (j = l; j < n; j++) {
                        for (s = 0.0, k = l; k < n; k++) {
                            s += U.get(i, k) * V.get(k, j);
                        }
                        for (k = l; k < n; k++) {
                            V.add(k, j, s * V.get(k, i));
                        }
                    }
                }
                for (j = l; j < n; j++) {
                    V.set(i, j, 0.0);
                    V.set(j, i, 0.0);
                }
            }
            V.set(i, i, 1.0);
            g = rv1[i];
            l = i;
        }

        for (i = Math.min(m, n) - 1; i >= 0; i--) {
            l = i + 1;
            g = w[i];
            for (j = l; j < n; j++) {
                U.set(i, j, 0.0);
            }

            if (g != 0.0) {
                g = 1.0 / g;
                for (j = l; j < n; j++) {
                    for (s = 0.0, k = l; k < m; k++) {
                        s += U.get(k, i) * U.get(k, j);
                    }
                    f = (s / U.get(i, i)) * g;
                    for (k = i; k < m; k++) {
                        U.add(k, j, f * U.get(k, i));
                    }
                }
                for (j = i; j < m; j++) {
                    U.mul(j, i, g);
                }
            } else {
                for (j = i; j < m; j++) {
                    U.set(j, i, 0.0);
                }
            }

            U.add(i, i, 1.0);
        }

        for (k = n - 1; k >= 0; k--) {
            for (its = 0; its < 30; its++) {
                flag = true;
                for (l = k; l >= 0; l--) {
                    nm = l - 1;
                    if (l == 0 || Math.abs(rv1[l]) <= Math.EPSILON * anorm) {
                        flag = false;
                        break;
                    }
                    if (Math.abs(w[nm]) <= Math.EPSILON * anorm) {
                        break;
                    }
                }

                if (flag) {
                    c = 0.0;
                    s = 1.0;
                    for (i = l; i < k + 1; i++) {
                        f = s * rv1[i];
                        rv1[i] = c * rv1[i];
                        if (Math.abs(f) <= Math.EPSILON * anorm) {
                            break;
                        }
                        g = w[i];
                        h = Math.hypot(f, g);
                        w[i] = h;
                        h = 1.0 / h;
                        c = g * h;
                        s = -f * h;
                        for (j = 0; j < m; j++) {
                            y = U.get(j, nm);
                            z = U.get(j, i);
                            U.set(j, nm, y * c + z * s);
                            U.set(j,  i, z * c - y * s);
                        }
                    }
                }

                z = w[k];
                if (l == k) {
                    if (z < 0.0) {
                        w[k] = -z;
                        for (j = 0; j < n; j++) {
                            V.set(j, k, -V.get(j, k));
                        }
                    }
                    break;
                }

                if (its == 29) {
                    throw new IllegalStateException("no convergence in 30 iterations");
                }

                x = w[l];
                nm = k - 1;
                y = w[nm];
                g = rv1[nm];
                h = rv1[k];
                f = ((y - z) * (y + z) + (g - h) * (g + h)) / (2.0 * h * y);
                g = Math.hypot(f, 1.0);
                f = ((x - z) * (x + z) + h * ((y / (f + Math.copySign(g, f))) - h)) / x;
                c = s = 1.0;

                for (j = l; j <= nm; j++) {
                    i = j + 1;
                    g = rv1[i];
                    y = w[i];
                    h = s * g;
                    g = c * g;
                    z = Math.hypot(f, h);
                    rv1[j] = z;
                    c = f / z;
                    s = h / z;
                    f = x * c + g * s;
                    g = g * c - x * s;
                    h = y * s;
                    y *= c;

                    for (jj = 0; jj < n; jj++) {
                        x = V.get(jj, j);
                        z = V.get(jj, i);
                        V.set(jj, j, x * c + z * s);
                        V.set(jj, i, z * c - x * s);
                    }

                    z = Math.hypot(f, h);
                    w[j] = z;
                    if (z != 0.0) {
                        z = 1.0 / z;
                        c = f * z;
                        s = h * z;
                    }

                    f = c * g + s * y;
                    x = c * y - s * g;
                    for (jj = 0; jj < m; jj++) {
                        y = U.get(jj, j);
                        z = U.get(jj, i);
                        U.set(jj, j, y * c + z * s);
                        U.set(jj, i, z * c - y * s);
                    }
                }

                rv1[l] = 0.0;
                rv1[k] = f;
                w[k] = x;
            }
        }

        // order singular values
        int inc = 1;
        double sw;
        double[] su = new double[m], sv = new double[n];

        do {
            inc *= 3;
            inc++;
        } while (inc <= n);

        do {
            inc /= 3;
            for (i = inc; i < n; i++) {
                sw = w[i];
                for (k = 0; k < m; k++) {
                    su[k] = U.get(k, i);
                }
                for (k = 0; k < n; k++) {
                    sv[k] = V.get(k, i);
                }
                j = i;
                while (w[j - inc] < sw) {
                    w[j] = w[j - inc];
                    for (k = 0; k < m; k++) {
                        U.set(k, j, U.get(k, j - inc));
                    }
                    for (k = 0; k < n; k++) {
                        V.set(k, j, V.get(k, j - inc));
                    }
                    j -= inc;
                    if (j < inc) {
                        break;
                    }
                }
                w[j] = sw;
                for (k = 0; k < m; k++) {
                    U.set(k, j, su[k]);
                }
                for (k = 0; k < n; k++) {
                    V.set(k, j, sv[k]);
                }

            }
        } while (inc > 1);

        for (k = 0; k < n; k++) {
            s = 0;
            for (i = 0; i < m; i++) {
                if (U.get(i, k) < 0.) {
                    s++;
                }
            }
            for (j = 0; j < n; j++) {
                if (V.get(j, k) < 0.) {
                    s++;
                }
            }
            if (s > (m + n) / 2) {
                for (i = 0; i < m; i++) {
                    U.set(i, k, -U.get(i, k));
                }
                for (j = 0; j < n; j++) {
                    V.set(j, k, -V.get(j, k));
                }
            }
        }

        // There are at most Math.min(m, n) nonzero singular values.
        double[] sigma = new double[Math.min(m, n)];
        System.arraycopy(w, 0, sigma, 0, sigma.length);
        return new SVD(U, V, sigma, true);
    }
}
