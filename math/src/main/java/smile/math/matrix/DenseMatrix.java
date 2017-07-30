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

import smile.math.Math;

/**
 * An abstract interface of dense matrix.
 *
 * @author Haifeng Li
 */
public abstract class DenseMatrix extends Matrix implements MatrixMultiplication<DenseMatrix, DenseMatrix> {
    /** Returns the array of storing the matrix. */
    public abstract double[] data();

    /**
     * The LDA (and LDB, LDC, etc.) parameter in BLAS is effectively
     * the stride of the matrix as it is laid out in linear memory.
     * It is perfectly valid to have an LDA value which is larger than
     * the leading dimension of the matrix which is being operated on.
     * Typical cases where it is either useful or necessary to use a
     * larger LDA value are when you are operating on a sub matrix from
     * a larger dense matrix, and when hardware or algorithms offer
     * performance advantages when storage is padded to round multiples
     * of some optimal size (cache lines or GPU memory transaction size,
     * or load balance in multiprocessor implementations, for example).
     *
     * @return the leading dimension
     */
    public abstract int ld();

    /**
     * Set the entry value at row i and column j.
     */
    public abstract double set(int i, int j, double x);

    /**
     * Set the entry value at row i and column j. For Scala users.
     */
    public double update(int i, int j, double x) {
        return set(i, j, x);
    }

    /**
     * Returns the LU decomposition.
     * This input matrix will be overwritten with the decomposition.
     */
    public abstract LU lu();

    /**
     * Returns the LU decomposition.
     * @param inPlace if true, this matrix will be used for matrix decomposition.
     */
    public LU lu(boolean inPlace) {
        DenseMatrix a = inPlace ? this : copy();
        return a.lu();
    }

    /**
     * Returns the Cholesky decomposition.
     * This input matrix will be overwritten with the decomposition.
     * @throws IllegalArgumentException if the matrix is not positive definite.
     */
    public abstract Cholesky cholesky();

    /**
     * Returns the Cholesky decomposition.
     * @param inPlace if true, this matrix will be used for matrix decomposition.
     * @throws IllegalArgumentException if the matrix is not positive definite.
     */
    public Cholesky cholesky(boolean inPlace) {
        DenseMatrix a = inPlace ? this : copy();
        return a.cholesky();
    }

    /**
     * Returns the QR decomposition.
     * This input matrix will be overwritten with the decomposition.
     */
    public abstract QR qr();

    /**
     * Returns the QR decomposition.
     * @param inPlace if true, this matrix will be used for matrix decomposition.
     */
    public QR qr(boolean inPlace) {
        DenseMatrix a = inPlace ? this : copy();
        return a.qr();
    }

    /**
     * Returns the singular value decomposition. Note that the input matrix
     * will hold U on output.
     */
    public abstract SVD svd();

    /**
     * Returns the singular value decomposition.
     * @param inPlace if true, this matrix will hold U on output.
     */
    public SVD svd(boolean inPlace) {
        DenseMatrix a = inPlace ? this : copy();
        return a.svd();
    }

    /**
     * Returns the eigen value decomposition. Note that the input matrix
     * will be overwritten on output.
     */
    public abstract EVD eigen();

    /**
     * Returns the eigen value decomposition.
     * @param inPlace if true, this matrix will be overwritten U on output.
     */
    public EVD eigen(boolean inPlace) {
        DenseMatrix a = inPlace ? this : copy();
        return a.eigen();
    }

    /**
     * Returns the eigen values in an array of size 2N. The first half and second half
     * of returned array contain the real and imaginary parts, respectively, of the
     * computed eigenvalues.
     */
    public abstract double[] eig();

    /**
     * Returns the eigen values in an array of size 2N. The first half and second half
     * of returned array contain the real and imaginary parts, respectively, of the
     * computed eigenvalues.
     * @param inPlace if true, this matrix will be overwritten U on output.
     */
    public double[] eig(boolean inPlace) {
        DenseMatrix a = inPlace ? this : copy();
        return a.eig();
    }

    /**
     * Returns the matrix transpose.
     */
    @Override
    public abstract DenseMatrix transpose();

    /**
     * Returns the inverse matrix.
     */
    public DenseMatrix inverse() {
        return inverse(false);
    }

    /**
     * Returns the inverse matrix.
     * @param inPlace if true, this matrix will be used for matrix decomposition.
     */
    public DenseMatrix inverse(boolean inPlace) {
        if (nrows() != ncols()) {
            throw new UnsupportedOperationException("Call inverse() on a non-square matrix");
        }

        LU lu = lu(inPlace);
        return lu.inverse();
    }

    /**
     * L1 matrix norm. Maximum column sum.
     */
    public double norm1() {
        int m = nrows();
        int n = ncols();

        double f = 0.0;
        for (int j = 0; j < n; j++) {
            double s = 0.0;
            for (int i = 0; i < m; i++) {
                s += Math.abs(get(i, j));
            }
            f = Math.max(f, s);
        }

        return f;
    }

    /**
     * L2 matrix norm. Maximum singular value.
     */
    public double norm2() {
        return svd(false).norm();
    }

    /**
     * L2 matrix norm. Maximum singular value.
     */
    public double norm() {
        return norm2();
    }

    /**
     * Infinity matrix norm. Maximum row sum.
     */
    public double normInf() {
        int m = nrows();
        int n = ncols();

        double[] f = new double[m];
        for (int j = 0; j < n; j++) {
            for (int i = 0; i < m; i++) {
                f[i] += Math.abs(get(i, j));
            }
        }

        return Math.max(f);
    }

    /**
     * Frobenius matrix norm. Sqrt of sum of squares of all elements.
     */
    public double normFro() {
        int m = nrows();
        int n = ncols();

        double f = 0.0;
        for (int j = 0; j < n; j++) {
            for (int i = 0; i < m; i++) {
                f = Math.hypot(f, get(i, j));
            }
        }

        return f;
    }

    /**
     * Returns x' * A * x.
     * The left upper submatrix of A is used in the computation based
     * on the size of x.
     */
    public double xax(double[] x) {
        if (nrows() != ncols()) {
            throw new IllegalArgumentException("The matrix is not square");
        }

        if (nrows() != x.length) {
            throw new IllegalArgumentException("Matrix and vector size doesn't match for x' * A * x");
        }

        int n = x.length;
        double s = 0.0;
        for (int j = 0; j < n; j++) {
            for (int i = 0; i < n; i++) {
                s += get(i, j) * x[i] * x[j];
            }
        }

        return s;
    }

    /**
     * Returns the sum of each row for a matrix.
     */
    public double[] rowSums() {
        int m = nrows();
        int n = ncols();
        double[] x = new double[m];

        for (int j = 0; j < n; j++) {
            for (int i = 0; i < m; i++) {
                x[i] += get(i, j);
            }
        }

        return x;
    }

    /**
     * Returns the mean of each row for a matrix.
     */
    public double[] rowMeans() {
        int m = nrows();
        int n = ncols();
        double[] x = new double[m];

        for (int j = 0; j < n; j++) {
            for (int i = 0; i < m; i++) {
                x[i] += get(i, j);
            }
        }

        for (int i = 0; i < m; i++) {
            x[i] /= n;
        }

        return x;
    }

    /**
     * Returns the sum of each column for a matrix.
     */
    public double[] colSums() {
        int m = nrows();
        int n = ncols();
        double[] x = new double[n];

        for (int j = 0; j < n; j++) {
            for (int i = 0; i < m; i++) {
                x[j] += get(i, j);
            }
        }

        return x;
    }

    /**
     * Returns the mean of each column for a matrix.
     */
    public double[] colMeans() {
        int m = nrows();
        int n = ncols();
        double[] x = new double[n];

        for (int j = 0; j < n; j++) {
            for (int i = 0; i < m; i++) {
                x[j] += get(i, j);
            }
            x[j] /= m;
        }

        return x;
    }

    /**
     * Returns a copy of this matrix.
     */
    public abstract DenseMatrix copy();

    @Override
    public abstract DenseMatrix ata();

    @Override
    public abstract DenseMatrix aat();

    /**
     * A[i][j] += x
     */
    public abstract double add(int i, int j, double x);

    /**
     * A[i][j] -= x
     */
    public abstract double sub(int i, int j, double x);

    /**
     * A[i][j] *= x
     */
    public abstract double mul(int i, int j, double x);

    /**
     * A[i][j] /= x
     */
    public abstract double div(int i, int j, double x);

    /**
     * C = A + B
     * @return the result matrix
     */
    public DenseMatrix add(DenseMatrix b, DenseMatrix c) {
        if (nrows() != b.nrows() || ncols() != b.ncols()) {
            throw new IllegalArgumentException("Matrix is not of same size.");
        }

        if (nrows() != c.nrows() || ncols() != c.ncols()) {
            throw new IllegalArgumentException("Matrix is not of same size.");
        }

        int m = nrows();
        int n = ncols();

        for (int i = 0; i < m; i++) {
            for (int j = 0; j < n; j++) {
                c.set(i, j, get(i, j) + b.get(i, j));
            }
        }
        return c;
    }

    /**
     * In place addition A = A + B
     * @return this matrix
     */
    public DenseMatrix add(DenseMatrix b) {
        if (nrows() != b.nrows() || ncols() != b.ncols()) {
            throw new IllegalArgumentException("Matrix is not of same size.");
        }

        int m = nrows();
        int n = ncols();

        for (int i = 0; i < m; i++) {
            for (int j = 0; j < n; j++) {
                add(i, j, b.get(i, j));
            }
        }
        return this;
    }

    /**
     * C = A - B
     * @return the result matrix
     */
    public DenseMatrix sub(DenseMatrix b, DenseMatrix c) {
        if (nrows() != b.nrows() || ncols() != b.ncols()) {
            throw new IllegalArgumentException("Matrix is not of same size.");
        }

        if (nrows() != c.nrows() || ncols() != c.ncols()) {
            throw new IllegalArgumentException("Matrix is not of same size.");
        }

        int m = nrows();
        int n = ncols();

        for (int i = 0; i < m; i++) {
            for (int j = 0; j < n; j++) {
                c.set(i, j, get(i, j) - b.get(i, j));
            }
        }
        return c;
    }

    /**
     * In place subtraction A = A - B
     * @return this matrix
     */
    public DenseMatrix sub(DenseMatrix b) {
        if (nrows() != b.nrows() || ncols() != b.ncols()) {
            throw new IllegalArgumentException("Matrix is not of same size.");
        }

        int m = nrows();
        int n = ncols();

        for (int i = 0; i < m; i++) {
            for (int j = 0; j < n; j++) {
                sub(i, j, b.get(i, j));
            }
        }
        return this;
    }

    /**
     * C = A * B
     * @return the result matrix
     */
    public DenseMatrix mul(DenseMatrix b, DenseMatrix c) {
        if (nrows() != b.nrows() || ncols() != b.ncols()) {
            throw new IllegalArgumentException("Matrix is not of same size.");
        }

        if (nrows() != c.nrows() || ncols() != c.ncols()) {
            throw new IllegalArgumentException("Matrix is not of same size.");
        }

        int m = nrows();
        int n = ncols();

        for (int i = 0; i < m; i++) {
            for (int j = 0; j < n; j++) {
                c.set(i, j, get(i, j) * b.get(i, j));
            }
        }
        return c;
    }

    /**
     * In place element-wise multiplication A = A * B
     * @return this matrix
     */
    public DenseMatrix mul(DenseMatrix b) {
        if (nrows() != b.nrows() || ncols() != b.ncols()) {
            throw new IllegalArgumentException("Matrix is not of same size.");
        }

        int m = nrows();
        int n = ncols();

        for (int i = 0; i < m; i++) {
            for (int j = 0; j < n; j++) {
                mul(i, j, b.get(i, j));
            }
        }
        return this;
    }

    /**
     * C = A / B
     * @return the result matrix
     */
    public DenseMatrix div(DenseMatrix b, DenseMatrix c) {
        if (nrows() != b.nrows() || ncols() != b.ncols()) {
            throw new IllegalArgumentException("Matrix is not of same size.");
        }

        if (nrows() != c.nrows() || ncols() != c.ncols()) {
            throw new IllegalArgumentException("Matrix is not of same size.");
        }

        int m = nrows();
        int n = ncols();

        for (int i = 0; i < m; i++) {
            for (int j = 0; j < n; j++) {
                c.set(i, j, get(i, j) / b.get(i, j));
            }
        }
        return c;
    }

    /**
     * In place element-wise division A = A / B
     * A = A - B
     * @return this matrix
     */
    public DenseMatrix div(DenseMatrix b) {
        if (nrows() != b.nrows() || ncols() != b.ncols()) {
            throw new IllegalArgumentException("Matrix is not of same size.");
        }

        int m = nrows();
        int n = ncols();

        for (int i = 0; i < m; i++) {
            for (int j = 0; j < n; j++) {
                div(i, j, b.get(i, j));
            }
        }
        return this;
    }

    /**
     * Element-wise addition C = A + x
     */
    public DenseMatrix add(double x, DenseMatrix c) {
        if (nrows() != c.nrows() || ncols() != c.ncols()) {
            throw new IllegalArgumentException("Matrix is not of same size.");
        }

        int m = nrows();
        int n = ncols();

        for (int i = 0; i < m; i++) {
            for (int j = 0; j < n; j++) {
                c.set(i, j, get(i, j) + x);
            }
        }

        return c;
    }

    /**
     * In place element-wise addition A = A + x
     */
    public DenseMatrix add(double x) {
        int m = nrows();
        int n = ncols();

        for (int i = 0; i < m; i++) {
            for (int j = 0; j < n; j++) {
                add(i, j, x);
            }
        }

        return this;
    }

    /**
     * Element-wise addition C = A - x
     */
    public DenseMatrix sub(double x, DenseMatrix c) {
        if (nrows() != c.nrows() || ncols() != c.ncols()) {
            throw new IllegalArgumentException("Matrix is not of same size.");
        }

        int m = nrows();
        int n = ncols();

        for (int i = 0; i < m; i++) {
            for (int j = 0; j < n; j++) {
                c.set(i, j, get(i, j) - x);
            }
        }

        return c;
    }

    /**
     * In place element-wise subtraction A = A - x
     */
    public DenseMatrix sub(double x) {
        int m = nrows();
        int n = ncols();

        for (int i = 0; i < m; i++) {
            for (int j = 0; j < n; j++) {
                sub(i, j, x);
            }
        }

        return this;
    }

    /**
     * Element-wise addition C = A * x
     */
    public DenseMatrix mul(double x, DenseMatrix c) {
        if (nrows() != c.nrows() || ncols() != c.ncols()) {
            throw new IllegalArgumentException("Matrix is not of same size.");
        }

        int m = nrows();
        int n = ncols();

        for (int i = 0; i < m; i++) {
            for (int j = 0; j < n; j++) {
                c.set(i, j, get(i, j) * x);
            }
        }

        return c;
    }

    /**
     * In place element-wise multiplication A = A * x
     */
    public DenseMatrix mul(double x) {
        int m = nrows();
        int n = ncols();

        for (int i = 0; i < m; i++) {
            for (int j = 0; j < n; j++) {
                mul(i, j, x);
            }
        }

        return this;
    }

    /**
     * Element-wise addition C = A / x
     */
    public DenseMatrix div(double x, DenseMatrix c) {
        if (nrows() != c.nrows() || ncols() != c.ncols()) {
            throw new IllegalArgumentException("Matrix is not of same size.");
        }

        int m = nrows();
        int n = ncols();

        for (int i = 0; i < m; i++) {
            for (int j = 0; j < n; j++) {
                c.set(i, j, get(i, j) / x);
            }
        }

        return c;
    }

    /**
     * In place element-wise division A = A / x
     */
    public DenseMatrix div(double x) {
        int m = nrows();
        int n = ncols();

        for (int i = 0; i < m; i++) {
            for (int j = 0; j < n; j++) {
                div(i, j, x);
            }
        }

        return this;
    }

    /**
     * Replaces NaN's with given value.
     */
    public DenseMatrix replaceNaN(double x) {
        int m = nrows();
        int n = ncols();

        for (int i = 0; i < m; i++) {
            for (int j = 0; j < n; j++) {
                if (Double.isNaN(get(i, j))) {
                    set(i, j, x);
                }
            }
        }

        return this;
    }

    /**
     * Returns the sum of all elements in the matrix.
     * @return the sum of all elements.
     */
    public double sum() {
        int m = nrows();
        int n = ncols();

        double s = 0.0;
        for (int i = 0; i < m; i++) {
            for (int j = 0; j < n; j++) {
                s += get(i, j);
            }
        }

        return s;
    }

    /**
     * Return the two-dimensional array of matrix.
     * @return the two-dimensional array of matrix.
     */
    public double[][] array() {
        double[][] V = new double[nrows()][ncols()];
        for (int i = 0; i < nrows(); i++) {
            for (int j = 0; j < ncols(); j++) {
                V[i][j] = get(i, j);
            }
        }
        return V;
    }
}
