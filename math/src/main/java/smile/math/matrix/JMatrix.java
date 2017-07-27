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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import smile.math.Complex;
import smile.math.Math;

/**
 * A pure Java implementation of DenseMatrix whose data is stored in a single 1D array of
 * doubles in column major order.
 */
public class JMatrix extends DenseMatrix {
    private static final long serialVersionUID = 1L;
    private static final Logger logger = LoggerFactory.getLogger(JMatrix.class);

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

    @Override
    public JMatrix copy() {
        JMatrix a = new JMatrix(nrows, ncols, A.clone());
        a.setSymmetric(isSymmetric());
        return a;
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
        return new SVD(U, V, sigma);
    }

    @Override
    public double[] eig() {
        if (nrows() != ncols()) {
            throw new IllegalArgumentException(String.format("Matrix is not square: %d x %d", nrows(), ncols()));
        }

        int n = nrows();
        double[] d = new double[n];
        double[] e = new double[n];

        if (isSymmetric()) {
            // Tridiagonalize.
            tred(this, d, e);
            // Diagonalize.
            tql(d, e, n);
        } else {
            double[] scale = balance(this);
            int[] perm = elmhes(this);
            hqr(this, d, e);
            sort(d, e);
        }

        double[] w = new double[2*n];
        System.arraycopy(d, 0, w, 0, n);
        System.arraycopy(e, 0, w, n, n);
        return w;
    }

    @Override
    public EVD eigen() {
        if (nrows() != ncols()) {
            throw new IllegalArgumentException(String.format("Matrix is not square: %d x %d", nrows(), ncols()));
        }

        int n = nrows();
        double[] d = new double[n];
        double[] e = new double[n];

        DenseMatrix V;
        if (isSymmetric()) {
            V = this;

            // Tridiagonalize.
            tred2(V, d, e);
            // Diagonalize.
            tql2(V, d, e);

        } else {
            double[] scale = balance(this);
            int[] perm = elmhes(this);

            V = Matrix.eye(n, n);

            eltran(this, V, perm);
            hqr2(this, V, d, e);
            balbak(V, scale);
            sort(d, e, V);
        }

        return new EVD(V, d, e);
    }

    /**
     * Symmetric Householder reduction to tridiagonal form.
     */
    private static void tred(DenseMatrix V, double[] d, double[] e) {
        int n = V.nrows();
        for (int i = 0; i < n; i++) {
            d[i] = V.get(n - 1, i);
        }

        // Householder reduction to tridiagonal form.
        for (int i = n - 1; i > 0; i--) {

            // Scale to avoid under/overflow.
            double scale = 0.0;
            double h = 0.0;
            for (int k = 0; k < i; k++) {
                scale = scale + Math.abs(d[k]);
            }
            if (scale == 0.0) {
                e[i] = d[i - 1];
                for (int j = 0; j < i; j++) {
                    d[j] = V.get(i - 1, j);
                    V.set(i, j, 0.0);
                    V.set(j, i, 0.0);
                }
            } else {

                // Generate Householder vector.
                for (int k = 0; k < i; k++) {
                    d[k] /= scale;
                    h += d[k] * d[k];
                }
                double f = d[i - 1];
                double g = Math.sqrt(h);
                if (f > 0) {
                    g = -g;
                }
                e[i] = scale * g;
                h = h - f * g;
                d[i - 1] = f - g;
                for (int j = 0; j < i; j++) {
                    e[j] = 0.0;
                }

                // Apply similarity transformation to remaining columns.
                for (int j = 0; j < i; j++) {
                    f = d[j];
                    V.set(j, i, f);
                    g = e[j] + V.get(j, j) * f;
                    for (int k = j + 1; k <= i - 1; k++) {
                        g += V.get(k, j) * d[k];
                        e[k] += V.get(k, j) * f;
                    }
                    e[j] = g;
                }
                f = 0.0;
                for (int j = 0; j < i; j++) {
                    e[j] /= h;
                    f += e[j] * d[j];
                }
                double hh = f / (h + h);
                for (int j = 0; j < i; j++) {
                    e[j] -= hh * d[j];
                }
                for (int j = 0; j < i; j++) {
                    f = d[j];
                    g = e[j];
                    for (int k = j; k <= i - 1; k++) {
                        V.sub(k, j, (f * e[k] + g * d[k]));
                    }
                    d[j] = V.get(i - 1, j);
                    V.set(i, j, 0.0);
                }
            }
            d[i] = h;
        }

        for (int j = 0; j < n; j++) {
            d[j] = V.get(j, j);
        }
        e[0] = 0.0;
    }

    /**
     * Symmetric Householder reduction to tridiagonal form.
     */
    private static void tred2(DenseMatrix V, double[] d, double[] e) {
        int n = V.nrows();
        for (int i = 0; i < n; i++) {
            d[i] = V.get(n - 1, i);
        }

        // Householder reduction to tridiagonal form.
        for (int i = n - 1; i > 0; i--) {
            // Scale to avoid under/overflow.
            double scale = 0.0;
            double h = 0.0;
            for (int k = 0; k < i; k++) {
                scale = scale + Math.abs(d[k]);
            }
            if (scale == 0.0) {
                e[i] = d[i - 1];
                for (int j = 0; j < i; j++) {
                    d[j] = V.get(i - 1, j);
                    V.set(i, j, 0.0);
                    V.set(j, i, 0.0);
                }
            } else {
                // Generate Householder vector.
                for (int k = 0; k < i; k++) {
                    d[k] /= scale;
                    h += d[k] * d[k];
                }
                double f = d[i - 1];
                double g = Math.sqrt(h);
                if (f > 0) {
                    g = -g;
                }
                e[i] = scale * g;
                h = h - f * g;
                d[i - 1] = f - g;
                for (int j = 0; j < i; j++) {
                    e[j] = 0.0;
                }

                // Apply similarity transformation to remaining columns.
                for (int j = 0; j < i; j++) {
                    f = d[j];
                    V.set(j, i, f);
                    g = e[j] + V.get(j, j) * f;
                    for (int k = j + 1; k <= i - 1; k++) {
                        g += V.get(k, j) * d[k];
                        e[k] += V.get(k, j) * f;
                    }
                    e[j] = g;
                }
                f = 0.0;
                for (int j = 0; j < i; j++) {
                    e[j] /= h;
                    f += e[j] * d[j];
                }
                double hh = f / (h + h);
                for (int j = 0; j < i; j++) {
                    e[j] -= hh * d[j];
                }
                for (int j = 0; j < i; j++) {
                    f = d[j];
                    g = e[j];
                    for (int k = j; k <= i - 1; k++) {
                        V.sub(k, j, (f * e[k] + g * d[k]));
                    }
                    d[j] = V.get(i - 1, j);
                    V.set(i, j, 0.0);
                }
            }
            d[i] = h;
        }

        // Accumulate transformations.
        for (int i = 0; i < n - 1; i++) {
            V.set(n - 1, i, V.get(i, i));
            V.set(i, i, 1.0);
            double h = d[i + 1];
            if (h != 0.0) {
                for (int k = 0; k <= i; k++) {
                    d[k] = V.get(k, i + 1) / h;
                }
                for (int j = 0; j <= i; j++) {
                    double g = 0.0;
                    for (int k = 0; k <= i; k++) {
                        g += V.get(k, i + 1) * V.get(k, j);
                    }
                    for (int k = 0; k <= i; k++) {
                        V.sub(k, j,  g * d[k]);
                    }
                }
            }
            for (int k = 0; k <= i; k++) {
                V.set(k, i + 1, 0.0);
            }
        }
        for (int j = 0; j < n; j++) {
            d[j] = V.get(n - 1, j);
            V.set(n - 1, j, 0.0);
        }
        V.set(n - 1, n - 1, 1.0);
        e[0] = 0.0;
    }

    /**
     * Tridiagonal QL Implicit routine for computing eigenvalues of a symmetric,
     * real, tridiagonal matrix.
     *
     * The routine works extremely well in practice. The number of iterations for the first few
     * eigenvalues might be 4 or 5, say, but meanwhile the off-diagonal elements in the lower right-hand
     * corner have been reduced too. The later eigenvalues are liberated with very little work. The
     * average number of iterations per eigenvalue is typically 1.3 - 1.6. The operation count per
     * iteration is O(n), with a fairly large effective coefficient, say, ~20n. The total operation count
     * for the diagonalization is then ~20n * (1.3 - 1.6)n = ~30n^2. If the eigenvectors are required,
     * there is an additional, much larger, workload of about 3n^3 operations.
     *
     * @param d on input, it contains the diagonal elements of the tridiagonal matrix.
     * On output, it contains the eigenvalues.
     * @param e on input, it contains the subdiagonal elements of the tridiagonal
     * matrix, with e[0] arbitrary. On output, its contents are destroyed.
     * @param n the size of all parameter arrays.
     */
    private static void tql(double[] d, double[] e, int n) {
        for (int i = 1; i < n; i++) {
            e[i - 1] = e[i];
        }
        e[n - 1] = 0.0;

        double f = 0.0;
        double tst1 = 0.0;
        for (int l = 0; l < n; l++) {

            // Find small subdiagonal element
            tst1 = Math.max(tst1, Math.abs(d[l]) + Math.abs(e[l]));
            int m = l;
            for (; m < n; m++) {
                if (Math.abs(e[m]) <= Math.EPSILON * tst1) {
                    break;
                }
            }

            // If m == l, d[l] is an eigenvalue,
            // otherwise, iterate.
            if (m > l) {
                int iter = 0;
                do {
                    if (++iter >= 30) {
                        throw new RuntimeException("Too many iterations");
                    }

                    // Compute implicit shift
                    double g = d[l];
                    double p = (d[l + 1] - d[l]) / (2.0 * e[l]);
                    double r = Math.hypot(p, 1.0);
                    if (p < 0) {
                        r = -r;
                    }
                    d[l] = e[l] / (p + r);
                    d[l + 1] = e[l] * (p + r);
                    double dl1 = d[l + 1];
                    double h = g - d[l];
                    for (int i = l + 2; i < n; i++) {
                        d[i] -= h;
                    }
                    f = f + h;

                    // Implicit QL transformation.
                    p = d[m];
                    double c = 1.0;
                    double c2 = c;
                    double c3 = c;
                    double el1 = e[l + 1];
                    double s = 0.0;
                    double s2 = 0.0;
                    for (int i = m - 1; i >= l; i--) {
                        c3 = c2;
                        c2 = c;
                        s2 = s;
                        g = c * e[i];
                        h = c * p;
                        r = Math.hypot(p, e[i]);
                        e[i + 1] = s * r;
                        s = e[i] / r;
                        c = p / r;
                        p = c * d[i] - s * g;
                        d[i + 1] = h + s * (c * g + s * d[i]);
                    }
                    p = -s * s2 * c3 * el1 * e[l] / dl1;
                    e[l] = s * p;
                    d[l] = c * p;

                    // Check for convergence.
                } while (Math.abs(e[l]) > Math.EPSILON * tst1);
            }
            d[l] = d[l] + f;
            e[l] = 0.0;
        }

        // Sort eigenvalues and corresponding vectors.
        for (int i = 0; i < n - 1; i++) {
            int k = i;
            double p = d[i];
            for (int j = i + 1; j < n; j++) {
                if (d[j] > p) {
                    k = j;
                    p = d[j];
                }
            }
            if (k != i) {
                d[k] = d[i];
                d[i] = p;
            }
        }
    }

    /**
     * Tridiagonal QL Implicit routine for computing eigenvalues and eigenvectors of a symmetric,
     * real, tridiagonal matrix.
     *
     * The routine works extremely well in practice. The number of iterations for the first few
     * eigenvalues might be 4 or 5, say, but meanwhile the off-diagonal elements in the lower right-hand
     * corner have been reduced too. The later eigenvalues are liberated with very little work. The
     * average number of iterations per eigenvalue is typically 1.3 - 1.6. The operation count per
     * iteration is O(n), with a fairly large effective coefficient, say, ~20n. The total operation count
     * for the diagonalization is then ~20n * (1.3 - 1.6)n = ~30n^2. If the eigenvectors are required,
     * there is an additional, much larger, workload of about 3n^3 operations.
     *
     * @param V on input, it contains the identity matrix. On output, the kth column
     * of V returns the normalized eigenvector corresponding to d[k].
     * @param d on input, it contains the diagonal elements of the tridiagonal matrix.
     * On output, it contains the eigenvalues.
     * @param e on input, it contains the subdiagonal elements of the tridiagonal
     * matrix, with e[0] arbitrary. On output, its contents are destroyed.
     */
    static void tql2(DenseMatrix V, double[] d, double[] e) {
        int n = V.nrows();
        for (int i = 1; i < n; i++) {
            e[i - 1] = e[i];
        }
        e[n - 1] = 0.0;

        double f = 0.0;
        double tst1 = 0.0;
        for (int l = 0; l < n; l++) {
            // Find small subdiagonal element
            tst1 = Math.max(tst1, Math.abs(d[l]) + Math.abs(e[l]));
            int m = l;
            for (; m < n; m++) {
                if (Math.abs(e[m]) <= Math.EPSILON * tst1) {
                    break;
                }
            }

            // If m == l, d[l] is an eigenvalue,
            // otherwise, iterate.
            if (m > l) {
                int iter = 0;
                do {
                    if (++iter >= 30) {
                        throw new RuntimeException("Too many iterations");
                    }

                    // Compute implicit shift
                    double g = d[l];
                    double p = (d[l + 1] - g) / (2.0 * e[l]);
                    double r = Math.hypot(p, 1.0);
                    if (p < 0) {
                        r = -r;
                    }
                    d[l] = e[l] / (p + r);
                    d[l + 1] = e[l] * (p + r);
                    double dl1 = d[l + 1];
                    double h = g - d[l];
                    for (int i = l + 2; i < n; i++) {
                        d[i] -= h;
                    }
                    f = f + h;

                    // Implicit QL transformation.
                    p = d[m];
                    double c = 1.0;
                    double c2 = c;
                    double c3 = c;
                    double el1 = e[l + 1];
                    double s = 0.0;
                    double s2 = 0.0;
                    for (int i = m - 1; i >= l; i--) {
                        c3 = c2;
                        c2 = c;
                        s2 = s;
                        g = c * e[i];
                        h = c * p;
                        r = Math.hypot(p, e[i]);
                        e[i + 1] = s * r;
                        s = e[i] / r;
                        c = p / r;
                        p = c * d[i] - s * g;
                        d[i + 1] = h + s * (c * g + s * d[i]);

                        // Accumulate transformation.
                        for (int k = 0; k < n; k++) {
                            h = V.get(k, i + 1);
                            V.set(k, i + 1, s * V.get(k, i) + c * h);
                            V.set(k, i,     c * V.get(k, i) - s * h);
                        }
                    }
                    p = -s * s2 * c3 * el1 * e[l] / dl1;
                    e[l] = s * p;
                    d[l] = c * p;

                    // Check for convergence.
                } while (Math.abs(e[l]) > Math.EPSILON * tst1);
            }
            d[l] = d[l] + f;
            e[l] = 0.0;
        }

        // Sort eigenvalues and corresponding vectors.
        for (int i = 0; i < n - 1; i++) {
            int k = i;
            double p = d[i];
            for (int j = i + 1; j < n; j++) {
                if (d[j] > p) {
                    k = j;
                    p = d[j];
                }
            }
            if (k != i) {
                d[k] = d[i];
                d[i] = p;
                for (int j = 0; j < n; j++) {
                    p = V.get(j, i);
                    V.set(j, i, V.get(j, k));
                    V.set(j, k, p);
                }
            }
        }
    }

    /**
     * Given a square matrix, this routine replaces it by a balanced matrix with
     * identical eigenvalues. A symmetric matrix is already balanced and is
     * unaffected by this procedure.
     */
    private static double[] balance(DenseMatrix A) {
        double sqrdx = Math.RADIX * Math.RADIX;

        int n = A.nrows();

        double[] scale = new double[n];
        for (int i = 0; i < n; i++) {
            scale[i] = 1.0;
        }

        boolean done = false;
        while (!done) {
            done = true;
            for (int i = 0; i < n; i++) {
                double r = 0.0, c = 0.0;
                for (int j = 0; j < n; j++) {
                    if (j != i) {
                        c += Math.abs(A.get(j, i));
                        r += Math.abs(A.get(i, j));
                    }
                }
                if (c != 0.0 && r != 0.0) {
                    double g = r / Math.RADIX;
                    double f = 1.0;
                    double s = c + r;
                    while (c < g) {
                        f *= Math.RADIX;
                        c *= sqrdx;
                    }
                    g = r * Math.RADIX;
                    while (c > g) {
                        f /= Math.RADIX;
                        c /= sqrdx;
                    }
                    if ((c + r) / f < 0.95 * s) {
                        done = false;
                        g = 1.0 / f;
                        scale[i] *= f;
                        for (int j = 0; j < n; j++) {
                            A.mul(i, j, g);
                        }
                        for (int j = 0; j < n; j++) {
                            A.mul(j, i, f);
                        }
                    }
                }
            }
        }

        return scale;
    }

    /**
     * Form the eigenvectors of a real nonsymmetric matrix by back transforming
     * those of the corresponding balanced matrix determined by balance.
     */
    private static void balbak(DenseMatrix V, double[] scale) {
        int n = V.nrows();
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                V.mul(i, j, scale[i]);
            }
        }
    }

    /**
     * Reduce a real nonsymmetric matrix to upper Hessenberg form.
     */
    private static int[] elmhes(DenseMatrix A) {
        int n = A.nrows();
        int[] perm = new int[n];

        for (int m = 1; m < n - 1; m++) {
            double x = 0.0;
            int i = m;
            for (int j = m; j < n; j++) {
                if (Math.abs(A.get(j, m - 1)) > Math.abs(x)) {
                    x = A.get(j, m - 1);
                    i = j;
                }
            }
            perm[m] = i;
            if (i != m) {
                for (int j = m - 1; j < n; j++) {
                    double swap = A.get(i, j);
                    A.set(i, j, A.get(m, j));
                    A.set(m, j, swap);
                }
                for (int j = 0; j < n; j++) {
                    double swap = A.get(j, i);
                    A.set(j, i, A.get(j, m));
                    A.set(j, m, swap);
                }
            }
            if (x != 0.0) {
                for (i = m + 1; i < n; i++) {
                    double y = A.get(i, m - 1);
                    if (y != 0.0) {
                        y /= x;
                        A.set(i, m - 1, y);
                        for (int j = m; j < n; j++) {
                            A.sub(i, j, y * A.get(m, j));
                        }
                        for (int j = 0; j < n; j++) {
                            A.add(j, m, y * A.get(j, i));
                        }
                    }
                }
            }
        }

        return perm;
    }

    /**
     * Accumulate the stabilized elementary similarity transformations used
     * in the reduction of a real nonsymmetric matrix to upper Hessenberg form by elmhes.
     */
    private static void eltran(DenseMatrix A, DenseMatrix V, int[] perm) {
        int n = A.nrows();
        for (int mp = n - 2; mp > 0; mp--) {
            for (int k = mp + 1; k < n; k++) {
                V.set(k, mp, A.get(k, mp - 1));
            }
            int i = perm[mp];
            if (i != mp) {
                for (int j = mp; j < n; j++) {
                    V.set(mp, j, V.get(i, j));
                    V.set(i, j, 0.0);
                }
                V.set(i, mp, 1.0);
            }
        }
    }

    /**
     * Find all eigenvalues of an upper Hessenberg matrix. On input, A can be
     * exactly as output from elmhes. On output, it is destroyed.
     */
    private static void hqr(DenseMatrix A, double[] d, double[] e) {
        int n = A.nrows();
        int nn, m, l, k, j, its, i, mmin;
        double z, y, x, w, v, u, t, s, r = 0.0, q = 0.0, p = 0.0, anorm = 0.0;

        for (i = 0; i < n; i++) {
            for (j = Math.max(i - 1, 0); j < n; j++) {
                anorm += Math.abs(A.get(i, j));
            }
        }
        nn = n - 1;
        t = 0.0;
        while (nn >= 0) {
            its = 0;
            do {
                for (l = nn; l > 0; l--) {
                    s = Math.abs(A.get(l - 1, l - 1) + Math.abs(A.get(l, l)));
                    if (s == 0.0) {
                        s = anorm;
                    }
                    if (Math.abs(A.get(l, l - 1)) <= Math.EPSILON * s) {
                        A.set(l, l - 1, 0.0);
                        break;
                    }
                }
                x = A.get(nn, nn);
                if (l == nn) {
                    d[nn--] = x + t;
                } else {
                    y = A.get(nn - 1, nn - 1);
                    w = A.get(nn, nn - 1) * A.get(nn - 1, nn);
                    if (l == nn - 1) {
                        p = 0.5 * (y - x);
                        q = p * p + w;
                        z = Math.sqrt(Math.abs(q));
                        x += t;
                        if (q >= 0.0) {
                            z = p + Math.copySign(z, p);
                            d[nn - 1] = d[nn] = x + z;
                            if (z != 0.0) {
                                d[nn] = x - w / z;
                            }
                        } else {
                            d[nn] = x + p;
                            e[nn] = -z;
                            d[nn - 1] = d[nn];
                            e[nn - 1] = -e[nn];
                        }
                        nn -= 2;
                    } else {
                        if (its == 30) {
                            throw new IllegalStateException("Too many iterations in hqr");
                        }
                        if (its == 10 || its == 20) {
                            t += x;
                            for (i = 0; i < nn + 1; i++) {
                                A.sub(i, i, x);
                            }
                            s = Math.abs(A.get(nn, nn - 1)) + Math.abs(A.get(nn - 1, nn - 2));
                            y = x = 0.75 * s;
                            w = -0.4375 * s * s;
                        }
                        ++its;
                        for (m = nn - 2; m >= l; m--) {
                            z = A.get(m, m);
                            r = x - z;
                            s = y - z;
                            p = (r * s - w) / A.get(m + 1, m) + A.get(m, m + 1);
                            q = A.get(m + 1, m + 1) - z - r - s;
                            r = A.get(m + 2, m + 1);
                            s = Math.abs(p) + Math.abs(q) + Math.abs(r);
                            p /= s;
                            q /= s;
                            r /= s;
                            if (m == l) {
                                break;
                            }
                            u = Math.abs(A.get(m, m - 1)) * (Math.abs(q) + Math.abs(r));
                            v = Math.abs(p) * (Math.abs(A.get(m - 1, m - 1)) + Math.abs(z) + Math.abs(A.get(m + 1, m + 1)));
                            if (u <= Math.EPSILON * v) {
                                break;
                            }
                        }
                        for (i = m; i < nn - 1; i++) {
                            A.set(i + 2, i, 0.0);
                            if (i != m) {
                                A.set(i + 2, i - 1, 0.0);
                            }
                        }
                        for (k = m; k < nn; k++) {
                            if (k != m) {
                                p = A.get(k, k - 1);
                                q = A.get(k + 1, k - 1);
                                r = 0.0;
                                if (k + 1 != nn) {
                                    r = A.get(k + 2, k - 1);
                                }
                                if ((x = Math.abs(p) + Math.abs(q) + Math.abs(r)) != 0.0) {
                                    p /= x;
                                    q /= x;
                                    r /= x;
                                }
                            }
                            if ((s = Math.copySign(Math.sqrt(p * p + q * q + r * r), p)) != 0.0) {
                                if (k == m) {
                                    if (l != m) {
                                        A.set(k, k - 1, -A.get(k, k - 1));
                                    }
                                } else {
                                    A.set(k, k - 1, -s * x);
                                }
                                p += s;
                                x = p / s;
                                y = q / s;
                                z = r / s;
                                q /= p;
                                r /= p;
                                for (j = k; j < nn + 1; j++) {
                                    p = A.get(k, j) + q * A.get(k + 1, j);
                                    if (k + 1 != nn) {
                                        p += r * A.get(k + 2, j);
                                        A.sub(k + 2, j, p * z);
                                    }
                                    A.sub(k + 1, j, p * y);
                                    A.sub(k, j, p * x);
                                }
                                mmin = nn < k + 3 ? nn : k + 3;
                                for (i = l; i < mmin + 1; i++) {
                                    p = x * A.get(i, k) + y * A.get(i, k + 1);
                                    if (k + 1 != nn) {
                                        p += z * A.get(i, k + 2);
                                        A.sub(i, k + 2, p * r);
                                    }
                                    A.sub(i, k + 1, p * q);
                                    A.sub(i, k, p);
                                }
                            }
                        }
                    }
                }
            } while (l + 1 < nn);
        }
    }

    /**
     * Finds all eigenvalues of an upper Hessenberg matrix A[0..n-1][0..n-1].
     * On input A can be exactly as output from elmhes and eltran. On output, d and e
     * contain the eigenvalues of A, while V is a matrix whose columns contain
     * the corresponding eigenvectors. The eigenvalues are not sorted, except
     * that complex conjugate pairs appear consecutively with the eigenvalue
     * having the positive imaginary part. For a complex eigenvalue, only the
     * eigenvector corresponding to the eigenvalue with positive imaginary part
     * is stored, with real part in V[0..n-1][i] and imaginary part in V[0..n-1][i+1].
     * The eigenvectors are not normalized.
     */
    private static void hqr2(DenseMatrix A, DenseMatrix V, double[] d, double[] e) {
        int n = A.nrows();
        int nn, m, l, k, j, its, i, mmin, na;
        double z = 0.0, y, x, w, v, u, t, s = 0.0, r = 0.0, q = 0.0, p = 0.0, anorm = 0.0, ra, sa, vr, vi;

        for (i = 0; i < n; i++) {
            for (j = Math.max(i - 1, 0); j < n; j++) {
                anorm += Math.abs(A.get(i, j));
            }
        }
        nn = n - 1;
        t = 0.0;
        while (nn >= 0) {
            its = 0;
            do {
                for (l = nn; l > 0; l--) {
                    s = Math.abs(A.get(l - 1, l - 1)) + Math.abs(A.get(l, l));
                    if (s == 0.0) {
                        s = anorm;
                    }
                    if (Math.abs(A.get(l, l - 1)) <= Math.EPSILON * s) {
                        A.set(l, l - 1, 0.0);
                        break;
                    }
                }
                x = A.get(nn, nn);
                if (l == nn) {
                    d[nn] = x + t;
                    A.set(nn, nn, x + t);
                    nn--;
                } else {
                    y = A.get(nn - 1, nn - 1);
                    w = A.get(nn, nn - 1) * A.get(nn - 1, nn);
                    if (l == nn - 1) {
                        p = 0.5 * (y - x);
                        q = p * p + w;
                        z = Math.sqrt(Math.abs(q));
                        x += t;
                        A.set(nn, nn, x );
                        A.set(nn - 1, nn - 1, y + t);
                        if (q >= 0.0) {
                            z = p + Math.copySign(z, p);
                            d[nn - 1] = d[nn] = x + z;
                            if (z != 0.0) {
                                d[nn] = x - w / z;
                            }
                            x = A.get(nn, nn - 1);
                            s = Math.abs(x) + Math.abs(z);
                            p = x / s;
                            q = z / s;
                            r = Math.sqrt(p * p + q * q);
                            p /= r;
                            q /= r;
                            for (j = nn - 1; j < n; j++) {
                                z = A.get(nn - 1, j);
                                A.set(nn - 1, j, q * z + p * A.get(nn, j));
                                A.set(nn, j, q * A.get(nn, j) - p * z);
                            }
                            for (i = 0; i <= nn; i++) {
                                z = A.get(i, nn - 1);
                                A.set(i, nn - 1, q * z + p * A.get(i, nn));
                                A.set(i, nn, q * A.get(i, nn) - p * z);
                            }
                            for (i = 0; i < n; i++) {
                                z = V.get(i, nn - 1);
                                V.set(i, nn - 1, q * z + p * V.get(i, nn));
                                V.set(i, nn, q * V.get(i, nn) - p * z);
                            }
                        } else {
                            d[nn] = x + p;
                            e[nn] = -z;
                            d[nn - 1] = d[nn];
                            e[nn - 1] = -e[nn];
                        }
                        nn -= 2;
                    } else {
                        if (its == 30) {
                            throw new IllegalArgumentException("Too many iterations in hqr");
                        }
                        if (its == 10 || its == 20) {
                            t += x;
                            for (i = 0; i < nn + 1; i++) {
                                A.sub(i, i, x);
                            }
                            s = Math.abs(A.get(nn, nn - 1)) + Math.abs(A.get(nn - 1, nn - 2));
                            y = x = 0.75 * s;
                            w = -0.4375 * s * s;
                        }
                        ++its;
                        for (m = nn - 2; m >= l; m--) {
                            z = A.get(m, m);
                            r = x - z;
                            s = y - z;
                            p = (r * s - w) / A.get(m + 1, m) + A.get(m, m + 1);
                            q = A.get(m + 1, m + 1) - z - r - s;
                            r = A.get(m + 2, m + 1);
                            s = Math.abs(p) + Math.abs(q) + Math.abs(r);
                            p /= s;
                            q /= s;
                            r /= s;
                            if (m == l) {
                                break;
                            }
                            u = Math.abs(A.get(m, m - 1)) * (Math.abs(q) + Math.abs(r));
                            v = Math.abs(p) * (Math.abs(A.get(m - 1, m - 1)) + Math.abs(z) + Math.abs(A.get(m + 1, m + 1)));
                            if (u <= Math.EPSILON * v) {
                                break;
                            }
                        }
                        for (i = m; i < nn - 1; i++) {
                            A.set(i + 2, i , 0.0);
                            if (i != m) {
                                A.set(i + 2, i - 1, 0.0);
                            }
                        }
                        for (k = m; k < nn; k++) {
                            if (k != m) {
                                p = A.get(k, k - 1);
                                q = A.get(k + 1, k - 1);
                                r = 0.0;
                                if (k + 1 != nn) {
                                    r = A.get(k + 2, k - 1);
                                }
                                if ((x = Math.abs(p) + Math.abs(q) + Math.abs(r)) != 0.0) {
                                    p /= x;
                                    q /= x;
                                    r /= x;
                                }
                            }
                            if ((s = Math.copySign(Math.sqrt(p * p + q * q + r * r), p)) != 0.0) {
                                if (k == m) {
                                    if (l != m) {
                                        A.set(k, k - 1, -A.get(k, k - 1));
                                    }
                                } else {
                                    A.set(k, k - 1, -s * x);
                                }
                                p += s;
                                x = p / s;
                                y = q / s;
                                z = r / s;
                                q /= p;
                                r /= p;
                                for (j = k; j < n; j++) {
                                    p = A.get(k, j) + q * A.get(k + 1, j);
                                    if (k + 1 != nn) {
                                        p += r * A.get(k + 2, j);
                                        A.sub(k + 2, j, p * z);
                                    }
                                    A.sub(k + 1, j, p * y);
                                    A.sub(k, j, p * x);
                                }
                                mmin = nn < k + 3 ? nn : k + 3;
                                for (i = 0; i < mmin + 1; i++) {
                                    p = x * A.get(i, k) + y * A.get(i, k + 1);
                                    if (k + 1 != nn) {
                                        p += z * A.get(i, k + 2);
                                        A.sub(i, k + 2, p * r);
                                    }
                                    A.sub(i, k + 1, p * q);
                                    A.sub(i, k, p);
                                }
                                for (i = 0; i < n; i++) {
                                    p = x * V.get(i, k) + y * V.get(i, k + 1);
                                    if (k + 1 != nn) {
                                        p += z * V.get(i, k + 2);
                                        V.sub(i, k + 2, p * r);
                                    }
                                    V.sub(i, k + 1, p * q);
                                    V.sub(i, k, p);
                                }
                            }
                        }
                    }
                }
            } while (l + 1 < nn);
        }

        if (anorm != 0.0) {
            for (nn = n - 1; nn >= 0; nn--) {
                p = d[nn];
                q = e[nn];
                na = nn - 1;
                if (q == 0.0) {
                    m = nn;
                    A.set(nn, nn, 1.0);
                    for (i = nn - 1; i >= 0; i--) {
                        w = A.get(i, i) - p;
                        r = 0.0;
                        for (j = m; j <= nn; j++) {
                            r += A.get(i, j) * A.get(j, nn);
                        }
                        if (e[i] < 0.0) {
                            z = w;
                            s = r;
                        } else {
                            m = i;

                            if (e[i] == 0.0) {
                                t = w;
                                if (t == 0.0) {
                                    t = Math.EPSILON * anorm;
                                }
                                A.set(i, nn, -r / t);
                            } else {
                                x = A.get(i, i + 1);
                                y = A.get(i + 1, i);
                                q = Math.sqr(d[i] - p) + Math.sqr(e[i]);
                                t = (x * s - z * r) / q;
                                A.set(i, nn, t);
                                if (Math.abs(x) > Math.abs(z)) {
                                    A.set(i + 1, nn, (-r - w * t) / x);
                                } else {
                                    A.set(i + 1, nn, (-s - y * t) / z);
                                }
                            }
                            t = Math.abs(A.get(i, nn));
                            if (Math.EPSILON * t * t > 1) {
                                for (j = i; j <= nn; j++) {
                                    A.div(j, nn, t);
                                }
                            }
                        }
                    }
                } else if (q < 0.0) {
                    m = na;
                    if (Math.abs(A.get(nn, na)) > Math.abs(A.get(na, nn))) {
                        A.set(na, na, q / A.get(nn, na));
                        A.set(na, nn, -(A.get(nn, nn) - p) / A.get(nn, na));
                    } else {
                        Complex temp = cdiv(0.0, -A.get(na, nn), A.get(na, na) - p, q);
                        A.set(na, na, temp.re());
                        A.set(na, nn, temp.im());
                    }
                    A.set(nn, na, 0.0);
                    A.set(nn, nn, 1.0);
                    for (i = nn - 2; i >= 0; i--) {
                        w = A.get(i, i) - p;
                        ra = sa = 0.0;
                        for (j = m; j <= nn; j++) {
                            ra += A.get(i, j) * A.get(j, na);
                            sa += A.get(i, j) * A.get(j, nn);
                        }
                        if (e[i] < 0.0) {
                            z = w;
                            r = ra;
                            s = sa;
                        } else {
                            m = i;
                            if (e[i] == 0.0) {
                                Complex temp = cdiv(-ra, -sa, w, q);
                                A.set(i, na, temp.re());
                                A.set(i, nn, temp.im());
                            } else {
                                x = A.get(i, i + 1);
                                y = A.get(i + 1, i);
                                vr = Math.sqr(d[i] - p) + Math.sqr(e[i]) - q * q;
                                vi = 2.0 * q * (d[i] - p);
                                if (vr == 0.0 && vi == 0.0) {
                                    vr = Math.EPSILON * anorm * (Math.abs(w) + Math.abs(q) + Math.abs(x) + Math.abs(y) + Math.abs(z));
                                }
                                Complex temp = cdiv(x * r - z * ra + q * sa, x * s - z * sa - q * ra, vr, vi);
                                A.set(i, na, temp.re());
                                A.set(i, nn, temp.im());
                                if (Math.abs(x) > Math.abs(z) + Math.abs(q)) {
                                    A.set(i + 1, na, (-ra - w * A.get(i, na) + q * A.get(i, nn)) / x);
                                    A.set(i + 1, nn, (-sa - w * A.get(i, nn) - q * A.get(i, na)) / x);
                                } else {
                                    temp = cdiv(-r - y * A.get(i, na), -s - y * A.get(i, nn), z, q);
                                    A.set(i + 1, na, temp.re());
                                    A.set(i + 1, nn, temp.im());
                                }
                            }
                        }
                        t = Math.max(Math.abs(A.get(i, na)), Math.abs(A.get(i, nn)));
                        if (Math.EPSILON * t * t > 1) {
                            for (j = i; j <= nn; j++) {
                                A.div(j, na, t);
                                A.div(j, nn, t);
                            }
                        }
                    }
                }
            }

            for (j = n - 1; j >= 0; j--) {
                for (i = 0; i < n; i++) {
                    z = 0.0;
                    for (k = 0; k <= j; k++) {
                        z += V.get(i, k) * A.get(k, j);
                    }
                    V.set(i, j, z);
                }
            }
        }
    }

    /**
     *  Complex scalar division.
     */
    private static Complex cdiv(double xr, double xi, double yr, double yi) {
        double cdivr, cdivi;
        double r, d;
        if (Math.abs(yr) > Math.abs(yi)) {
            r = yi / yr;
            d = yr + r * yi;
            cdivr = (xr + r * xi) / d;
            cdivi = (xi - r * xr) / d;
        } else {
            r = yr / yi;
            d = yi + r * yr;
            cdivr = (r * xr + xi) / d;
            cdivi = (r * xi - xr) / d;
        }

        return new Complex(cdivr, cdivi);
    }

    /**
     * Sort eigenvalues.
     */
    protected static void sort(double[] d, double[] e) {
        int i = 0;
        int n = d.length;
        for (int j = 1; j < n; j++) {
            double real = d[j];
            double img = e[j];
            for (i = j - 1; i >= 0; i--) {
                if (d[i] >= d[j]) {
                    break;
                }
                d[i + 1] = d[i];
                e[i + 1] = e[i];
            }
            d[i + 1] = real;
            e[i + 1] = img;
        }
    }

    /**
     * Sort eigenvalues and eigenvectors.
     */
    protected static void sort(double[] d, double[] e, DenseMatrix V) {
        int i = 0;
        int n = d.length;
        double[] temp = new double[n];
        for (int j = 1; j < n; j++) {
            double real = d[j];
            double img = e[j];
            for (int k = 0; k < n; k++) {
                temp[k] = V.get(k, j);
            }
            for (i = j - 1; i >= 0; i--) {
                if (d[i] >= d[j]) {
                    break;
                }
                d[i + 1] = d[i];
                e[i + 1] = e[i];
                for (int k = 0; k < n; k++) {
                    V.set(k, i + 1, V.get(k, i));
                }
            }
            d[i + 1] = real;
            e[i + 1] = img;
            for (int k = 0; k < n; k++) {
                V.set(k, i + 1, temp[k]);
            }
        }
    }
}
