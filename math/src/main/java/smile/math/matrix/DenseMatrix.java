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
public interface DenseMatrix extends Matrix, MatrixMultiplication<DenseMatrix, DenseMatrix> {
    /** Returns the array of storing the matrix. */
    public double[] data();

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
    public int ld();

    /**
     * Set the entry value at row i and column j.
     */
    public double set(int i, int j, double x);

    /**
     * Set the entry value at row i and column j. For Scala users.
     */
    default public double update(int i, int j, double x) {
        return set(i, j, x);
    }

    /**
     * Returns the LU decomposition.
     */
    public default LU lu() {
        // Use a "left-looking", dot-product, Crout/Doolittle algorithm.
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
     * Returns the LU decomposition.
     * @param inPlace if true, this matrix will be used for matrix decomposition.
     */
    public default LU lu(boolean inPlace) {
        DenseMatrix a = inPlace ? this : copy();
        return a.lu();
    }

    /**
     * Returns the matrix transpose.
     */
    public DenseMatrix transpose();

    /**
     * Returns the inverse matrix.
     */
    public default DenseMatrix inverse() {
        return inverse(false);
    }

    /**
     * Returns the inverse matrix.
     * @param inPlace if true, this matrix will be used for matrix decomposition.
     */
    public default DenseMatrix inverse(boolean inPlace) {
        DenseMatrix a = inPlace ? this : copy();
        if (nrows() == ncols()) {
            LU lu = lu(inPlace);
            return lu.inverse();
        } else {
            QRDecomposition qr = new QRDecomposition(a);
            return qr.inverse();
        }
    }

    /**
     * L1 matrix norm. Maximum column sum.
     */
    public default double norm1() {
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
    public default double norm2() {
        return new SingularValueDecomposition(this).norm();
    }

    /**
     * L2 matrix norm. Maximum singular value.
     */
    public default double norm() {
        return norm2();
    }

    /**
     * Infinity matrix norm. Maximum row sum.
     */
    public default double normInf() {
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
    public default double normFro() {
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
    public default double xax(double[] x) {
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
     * Returns the row means for a matrix.
     */
    public default double[] rowMeans() {
        int m = nrows();
        int n = ncols();
        double[] x = new double[m];

        for (int j = 0; j < n; j++) {
            for (int i = 1; i < m; i++) {
                x[i] += get(i, j);
            }
        }

        for (int i = 1; i < m; i++) {
            x[i] /= n;
        }

        return x;
    }

    /**
     * Returns the column means for a matrix.
     */
    public default double[] colMeans() {
        int m = nrows();
        int n = ncols();
        double[] x = new double[n];;

        for (int j = 0; j < n; j++) {
            for (int i = 1; i < m; i++) {
                x[j] += get(i, j);
            }
            x[j] /= m;
        }

        return x;
    }

    /**
     * Returns a copy of this matrix.
     */
    public DenseMatrix copy();

    @Override
    public DenseMatrix ata();

    @Override
    public DenseMatrix aat();

    /**
     * A[i][j] += x
     */
    public double add(int i, int j, double x);

    /**
     * A[i][j] -= x
     */
    public double sub(int i, int j, double x);

    /**
     * A[i][j] *= x
     */
    public double mul(int i, int j, double x);

    /**
     * A[i][j] /= x
     */
    public double div(int i, int j, double x);

    /**
     * C = A + B
     * @return the result matrix
     */
    default public DenseMatrix add(DenseMatrix b, DenseMatrix c) {
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
    default public DenseMatrix add(DenseMatrix b) {
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
    default public DenseMatrix sub(DenseMatrix b, DenseMatrix c) {
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
    default public DenseMatrix sub(DenseMatrix b) {
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
    default public DenseMatrix mul(DenseMatrix b, DenseMatrix c) {
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
    default public DenseMatrix mul(DenseMatrix b) {
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
    default public DenseMatrix div(DenseMatrix b, DenseMatrix c) {
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
    default public DenseMatrix div(DenseMatrix b) {
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
    default public DenseMatrix add(double x, DenseMatrix c) {
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
    default public DenseMatrix add(double x) {
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
    default public DenseMatrix sub(double x, DenseMatrix c) {
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
    default public DenseMatrix sub(double x) {
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
    default public DenseMatrix mul(double x, DenseMatrix c) {
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
    default public DenseMatrix mul(double x) {
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
    default public DenseMatrix div(double x, DenseMatrix c) {
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
    default public DenseMatrix div(double x) {
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
    default public DenseMatrix replaceNaN(double x) {
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
    default public double sum() {
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
    default public double[][] array() {
        double[][] V = new double[nrows()][ncols()];
        for (int i = 0; i < nrows(); i++) {
            for (int j = 0; j < ncols(); j++) {
                V[i][j] = get(i, j);
            }
        }
        return V;
    }

    /**
     * Returns the string representation of matrix.
     * @param full Print the full matrix if true. Otherwise only print top left 7 x 7 submatrix.
     */
    default public String toString(boolean full) {
        StringBuilder sb = new StringBuilder();
        final int fields = 7;
        int m = Math.min(fields, nrows());
        int n = Math.min(fields, ncols());

        String newline = n < ncols() ? "...\n" : "\n";

        for (int i = 0; i < m; i++) {
            for (int j = 0; j < n; j++) {
                sb.append(String.format("%8.4f  ", get(i, j)));
            }
            sb.append(newline);
        }

        if (m < nrows()) {
            sb.append("  ...\n");
        }

        return sb.toString();
    }
}
