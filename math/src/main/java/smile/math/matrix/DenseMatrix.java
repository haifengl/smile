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

package smile.math.matrix;

import smile.math.MathEx;

/**
 * An abstract interface of dense matrix.
 *
 * @author Haifeng Li
 */
public interface DenseMatrix extends Matrix, MatrixMultiplication<DenseMatrix, DenseMatrix> {
    /** Returns the array that backs this matrix (optional operation). */
    double[] data();

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
    default int ld() {
        return nrows();
    }

    /**
     * Fill the matrix with a value.
     */
    void fill(double x);

    /**
     * Set the entry value at row i and column j.
     */
    double set(int i, int j, double x);

    /**
     * Set the entry value at row i and column j. For Scala users.
     */
    default double update(int i, int j, double x) {
        return set(i, j, x);
    }

    /**
     * Returns the submatrix which top left at (i, j) and bottom right at (k, l).
     * @param i the beginning row, inclusive.
     * @param j the beginning column, inclusive,
     * @param k the ending row, exclusive.
     * @param l the ending column, exclusive.
     */
    default DenseMatrix apply(int i, int j, int k, int l) {
        return submat(i, j, k, l);
    }

    /**
     * Returns the submatrix which top left at (i, j) and bottom right at (k, l).
     * @param i the beginning row, inclusive.
     * @param j the beginning column, inclusive,
     * @param k the ending row, exclusive.
     * @param l the ending column, exclusive.
     */
    default DenseMatrix submat(int i, int j, int k, int l) {
        int p = k - i;
        int q = l - j;
        DenseMatrix w = Matrix.zeros(p, q);

        for (int n = 0; n < q; n++) {
            for (int m = 0; m < p; m++) {
                w.set(m, n, get(i+m, j+n));
            }
        }

        return w;
    }

    /**
     * Returns the LU decomposition.
     * This input matrix will be overwritten with the decomposition.
     */
    LU lu();

    /**
     * Returns the LU decomposition.
     * @param inPlace if true, this matrix will be used for matrix decomposition.
     */
    default LU lu(boolean inPlace) {
        DenseMatrix a = inPlace ? this : copy();
        return a.lu();
    }

    /**
     * Returns the Cholesky decomposition.
     * This input matrix will be overwritten with the decomposition.
     * @throws IllegalArgumentException if the matrix is not positive definite.
     */
    Cholesky cholesky();

    /**
     * Returns the Cholesky decomposition.
     * @param inPlace if true, this matrix will be used for matrix decomposition.
     * @throws IllegalArgumentException if the matrix is not positive definite.
     */
    default Cholesky cholesky(boolean inPlace) {
        DenseMatrix a = inPlace ? this : copy();
        return a.cholesky();
    }

    /**
     * Returns the QR decomposition.
     * This input matrix will be overwritten with the decomposition.
     */
    QR qr();

    /**
     * Returns the QR decomposition.
     * @param inPlace if true, this matrix will be used for matrix decomposition.
     */
    default QR qr(boolean inPlace) {
        DenseMatrix a = inPlace ? this : copy();
        return a.qr();
    }

    /**
     * Returns the singular value decomposition. Note that the input matrix
     * will hold U on output.
     */
    SVD svd();

    /**
     * Returns the singular value decomposition.
     * @param inPlace if true, this matrix will hold U on output.
     */
    default SVD svd(boolean inPlace) {
        DenseMatrix a = inPlace ? this : copy();
        return a.svd();
    }

    /**
     * Returns the eigen value decomposition. Note that the input matrix
     * will be overwritten on output.
     */
    EVD eigen();

    /**
     * Returns the eigen value decomposition.
     * @param inPlace if true, this matrix will be overwritten U on output.
     */
    default EVD eigen(boolean inPlace) {
        DenseMatrix a = inPlace ? this : copy();
        return a.eigen();
    }

    /**
     * Returns the eigen values in an array of size 2N. The first half and second half
     * of returned array contain the real and imaginary parts, respectively, of the
     * computed eigenvalues.
     */
    double[] eig();

    /**
     * Returns the eigen values in an array of size 2N. The first half and second half
     * of returned array contain the real and imaginary parts, respectively, of the
     * computed eigenvalues.
     * @param inPlace if true, this matrix will be overwritten U on output.
     */
    default double[] eig(boolean inPlace) {
        DenseMatrix a = inPlace ? this : copy();
        return a.eig();
    }

    /**
     * Returns the matrix transpose.
     */
    @Override
    DenseMatrix transpose();

    /**
     * Centers and scales the columns of matrix.
     * @return a new matrix with zero mean and unit variance for each column.
     */
    default DenseMatrix scale() {
        double[] center = colMeans();
        double[] scale = colSds();
        return scale(center, scale);
    }

    /**
     * Centers and scales the columns of matrix.
     * @param center column center. If null, no centering.
     * @param scale column scale. If null, no scaling.
     * @return a new matrix with zero mean and unit variance for each column.
     */
    default DenseMatrix scale(double[] center, double[] scale) {
        if (center == null && scale == null) {
            throw new IllegalArgumentException("Both center and scale are null");
        }

        int m = nrows();
        int n = ncols();

        DenseMatrix x = Matrix.zeros(m, n);

        if (center == null) {
            for (int j = 0; j < n; j++) {
                for (int i = 0; i < m; i++) {
                    x.set(i, j, get(i, j) / scale[j]);
                }
            }
        } else if (scale == null) {
            for (int j = 0; j < n; j++) {
                for (int i = 0; i < m; i++) {
                    x.set(i, j, get(i, j) - center[j]);
                }
            }
        } else {
            for (int j = 0; j < n; j++) {
                for (int i = 0; i < m; i++) {
                    x.set(i, j, (get(i, j) - center[j]) / scale[j]);
                }
            }
        }

        return x;
    }

    /**
     * Returns the inverse matrix.
     */
    default DenseMatrix inverse() {
        return inverse(false);
    }

    /**
     * Returns the inverse matrix.
     * @param inPlace if true, this matrix will be used for matrix decomposition.
     */
    default DenseMatrix inverse(boolean inPlace) {
        if (nrows() != ncols()) {
            throw new UnsupportedOperationException("Call inverse() on a non-square matrix");
        }

        LU lu = lu(inPlace);
        return lu.inverse();
    }

    /**
     * L1 matrix norm. Maximum column sum.
     */
    default double norm1() {
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
    default double norm2() {
        return svd(false).norm();
    }

    /**
     * L2 matrix norm. Maximum singular value.
     */
    default double norm() {
        return norm2();
    }

    /**
     * Infinity matrix norm. Maximum row sum.
     */
    default double normInf() {
        int m = nrows();
        int n = ncols();

        double[] f = new double[m];
        for (int j = 0; j < n; j++) {
            for (int i = 0; i < m; i++) {
                f[i] += Math.abs(get(i, j));
            }
        }

        return MathEx.max(f);
    }

    /**
     * Frobenius matrix norm. Sqrt of sum of squares of all elements.
     */
    default double normFro() {
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
    default double xax(double[] x) {
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
     * Returns the sum of each row.
     */
    default double[] rowSums() {
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
     * Returns the mean of each row.
     */
    default double[] rowMeans() {
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
     * Returns the standard deviations of each row.
     */
    default double[] rowSds() {
        int m = nrows();
        int n = ncols();
        double[] x = new double[m];
        double[] x2 = new double[m];

        for (int j = 0; j < n; j++) {
            for (int i = 0; i < m; i++) {
                double a = get(i, j);
                x[i] += a;
                x2[i] += a * a;
            }
        }

        for (int i = 0; i < m; i++) {
            double mu = x[i] / n;
            x[i] = Math.sqrt(x2[i] / n - mu * mu);
        }

        return x;
    }

    /**
     * Returns the sum of each column.
     */
    default double[] colSums() {
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
     * Returns the mean of each column.
     */
    default double[] colMeans() {
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
     * Returns the standard deviations of each column.
     */
    default double[] colSds() {
        int m = nrows();
        int n = ncols();
        double[] x = new double[n];

        for (int j = 0; j < n; j++) {
            double mu = 0.0;
            double sumsq = 0.0;
            for (int i = 0; i < m; i++) {
                double a = get(i, j);
                mu += a;
                sumsq += a * a;
            }
            mu /= m;
            x[j] = Math.sqrt(sumsq / m - mu * mu);
        }

        return x;
    }

    /**
     * Returns a copy of this matrix.
     */
    DenseMatrix copy();

    @Override
    DenseMatrix ata();

    @Override
    DenseMatrix aat();

    /**
     * A[i][j] += x
     */
    double add(int i, int j, double x);

    /**
     * A[i][j] -= x
     */
    double sub(int i, int j, double x);

    /**
     * A[i][j] *= x
     */
    double mul(int i, int j, double x);

    /**
     * A[i][j] /= x
     */
    double div(int i, int j, double x);

    /**
     * C = A + B
     * @return the result matrix
     */
    default DenseMatrix add(DenseMatrix b, DenseMatrix c) {
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

    /**
     * In place addition A = A + B
     * @return this matrix
     */
    default DenseMatrix add(DenseMatrix b) {
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

    /**
     * C = A - B
     * @return the result matrix
     */
    default DenseMatrix sub(DenseMatrix b, DenseMatrix c) {
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

    /**
     * In place subtraction A = A - B
     * @return this matrix
     */
    default DenseMatrix sub(DenseMatrix b) {
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

    /**
     * C = A * B
     * @return the result matrix
     */
    default DenseMatrix mul(DenseMatrix b, DenseMatrix c) {
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

    /**
     * In place element-wise multiplication A = A * B
     * @return this matrix
     */
    default DenseMatrix mul(DenseMatrix b) {
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

    /**
     * C = A / B
     * @return the result matrix
     */
    default DenseMatrix div(DenseMatrix b, DenseMatrix c) {
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

    /**
     * In place element-wise division A = A / B
     * A = A - B
     * @return this matrix
     */
    default DenseMatrix div(DenseMatrix b) {
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

    /**
     * Element-wise addition C = A + x
     */
    default DenseMatrix add(double x, DenseMatrix c) {
        if (nrows() != c.nrows() || ncols() != c.ncols()) {
            throw new IllegalArgumentException("Matrix is not of same size.");
        }

        int m = nrows();
        int n = ncols();

        for (int j = 0; j < n; j++) {
            for (int i = 0; i < m; i++) {
                c.set(i, j, get(i, j) + x);
            }
        }

        return c;
    }

    /**
     * In place element-wise addition A = A + x
     */
    default DenseMatrix add(double x) {
        int m = nrows();
        int n = ncols();

        for (int j = 0; j < n; j++) {
            for (int i = 0; i < m; i++) {
                add(i, j, x);
            }
        }

        return this;
    }

    /**
     * Element-wise addition C = A - x
     */
    default DenseMatrix sub(double x, DenseMatrix c) {
        if (nrows() != c.nrows() || ncols() != c.ncols()) {
            throw new IllegalArgumentException("Matrix is not of same size.");
        }

        int m = nrows();
        int n = ncols();

        for (int j = 0; j < n; j++) {
            for (int i = 0; i < m; i++) {
                c.set(i, j, get(i, j) - x);
            }
        }

        return c;
    }

    /**
     * In place element-wise subtraction A = A - x
     */
    default DenseMatrix sub(double x) {
        int m = nrows();
        int n = ncols();

        for (int j = 0; j < n; j++) {
            for (int i = 0; i < m; i++) {
                sub(i, j, x);
            }
        }

        return this;
    }

    /**
     * Element-wise addition C = A * x
     */
    default DenseMatrix mul(double x, DenseMatrix c) {
        if (nrows() != c.nrows() || ncols() != c.ncols()) {
            throw new IllegalArgumentException("Matrix is not of same size.");
        }

        int m = nrows();
        int n = ncols();

        for (int j = 0; j < n; j++) {
            for (int i = 0; i < m; i++) {
                c.set(i, j, get(i, j) * x);
            }
        }

        return c;
    }

    /**
     * In place element-wise multiplication A = A * x
     */
    default DenseMatrix mul(double x) {
        int m = nrows();
        int n = ncols();

        for (int j = 0; j < n; j++) {
            for (int i = 0; i < m; i++) {
                mul(i, j, x);
            }
        }

        return this;
    }

    /**
     * Element-wise addition C = A / x
     */
    default DenseMatrix div(double x, DenseMatrix c) {
        if (nrows() != c.nrows() || ncols() != c.ncols()) {
            throw new IllegalArgumentException("Matrix is not of same size.");
        }

        int m = nrows();
        int n = ncols();

        for (int j = 0; j < n; j++) {
            for (int i = 0; i < m; i++) {
                c.set(i, j, get(i, j) / x);
            }
        }

        return c;
    }

    /**
     * In place element-wise division A = A / x
     */
    default DenseMatrix div(double x) {
        int m = nrows();
        int n = ncols();

        for (int j = 0; j < n; j++) {
            for (int i = 0; i < m; i++) {
                div(i, j, x);
            }
        }

        return this;
    }

    /**
     * Replaces NaN's with given value.
     */
    default DenseMatrix replaceNaN(double x) {
        int m = nrows();
        int n = ncols();

        for (int j = 0; j < n; j++) {
            for (int i = 0; i < m; i++) {
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
    default double sum() {
        int m = nrows();
        int n = ncols();

        double s = 0.0;
        for (int j = 0; j < n; j++) {
            for (int i = 0; i < m; i++) {
                s += get(i, j);
            }
        }

        return s;
    }

    /**
     * Return the two-dimensional array of matrix.
     * @return the two-dimensional array of matrix.
     */
    default double[][] toArray() {
        int m = nrows();
        int n = ncols();

        double[][] V = new double[m][n];
        for (int j = 0; j < n; j++) {
            for (int i = 0; i < m; i++) {
                V[i][j] = get(i, j);
            }
        }
        return V;
    }
}
