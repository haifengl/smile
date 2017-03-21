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

package smile.math.nd4j;

import java.util.Arrays;
import smile.math.matrix.Matrix;
import smile.math.matrix.MatrixMultiplication;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.inverse.InvertMatrix;

/**
 * ND4j matrix wrapper.
 *
 * @author Haifeng Li
 */
public class NDMatrix implements Matrix, MatrixMultiplication<NDMatrix, NDMatrix> {
    /**
     * The matrix storage.
     */
    private INDArray A;
    /**
     * True if the matrix is symmetric.
     */
    private boolean symmetric = false;
    /**
     * True if the matrix is positive definite.
     */
    private boolean positive = false;

    /**
     * Constructor.
     * @param A the array of matrix.
     */
    public NDMatrix(double[][] A) {
        this.A = Nd4j.create(A);
    }

    /**
     * Constructor.
     * If the matrix is updated, no check on if the matrix is symmetric.
     * @param A the array of matrix.
     * @param symmetric true if the matrix is symmetric.
     */
    public NDMatrix(double[][] A, boolean symmetric) {
        this(A);
        this.symmetric = symmetric;
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
    public NDMatrix(double[][] A, boolean symmetric, boolean positive) {
        this(A);
        this.symmetric = symmetric;
        this.positive = positive;
    }

    /**
     * Constructor.
     * @param A the NDArray of matrix.
     */
    public NDMatrix(INDArray A) {
        this.A = A;
    }

    /**
     * Constructor.
     * If the matrix is updated, no check on if the matrix is symmetric.
     * @param A the NDArray of matrix.
     * @param symmetric true if the matrix is symmetric.
     */
    public NDMatrix(INDArray A, boolean symmetric) {
        this(A);
        this.symmetric = symmetric;
    }

    /**
     * Constructor.
     * If the matrix is updated, no check on if the matrix is symmetric
     * and/or positive definite. The symmetric and positive definite
     * properties are intended for read-only matrices.
     * @param A the NDArray of matrix.
     * @param symmetric true if the matrix is symmetric.
     * @param positive true if the matrix is positive definite.
     */
    public NDMatrix(INDArray A, boolean symmetric, boolean positive) {
        this(A);
        this.symmetric = symmetric;
        this.positive = positive;
    }

    /**
     * Constructor of all-zero matrix.
     */
    public NDMatrix(int rows, int cols) {
        A = Nd4j.zeros(rows, cols);
    }

    /**
     * Constructor. Fill the matrix with given value.
     */
    public NDMatrix(int rows, int cols, double value) {
        A = Nd4j.zeros(rows, cols).addi(value);
    }

    public static NDMatrix eye(int n) {
        return new NDMatrix(Nd4j.eye(n));
    }

    /**
     * Returns true if the matrix is symmetric.
     */
    public boolean isSymmetric() {
        return symmetric;
    }

    /**
     * Returns true if the matrix is positive definite.
     */
    public boolean isPositive() {
        return positive;
    }

    /**
     * Sets if the matrix is symmetric.
     */
    public void setSymmetric(boolean symmetric) {
        this.symmetric = symmetric;
    }

    /**
     * Sets if the matrix is positive definite.
     */
    public void setPositive(boolean positive) {
        this.positive = positive;
    }

    @Override
    public int nrows() {
        return A.rows();
    }

    @Override
    public int ncols() {
        return A.columns();
    }

    @Override
    public double get(int i, int j) {
        return A.getDouble(i, j);
    }

    /**
     * Set the entry value at row i and column j.
     */
    public double set(int i, int j, double x) {
        A.putScalar(i, j, x);
        return x;
    }

    /**
     * Set the entry value at row i and column j. For Scala users.
     */
    public double update(int i, int j, double x) {
        return set(i, j, x);
    }

    @Override
    public double[] ax(double[] x, double[] y) {
        int n = Math.min(nrows(), y.length);
        int p = Math.min(ncols(), x.length);

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
        int n = Math.min(nrows(), y.length);
        int p = Math.min(ncols(), x.length);

        for (int k = 0; k < p; k++) {
            for (int i = 0; i < n; i++) {
                y[i] += get(i, k) * x[k];
            }
        }

        return y;
    }

    @Override
    public double[] axpy(double[] x, double[] y, double b) {
        int n = Math.min(nrows(), y.length);
        int p = Math.min(ncols(), x.length);

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
        int n = Math.min(ncols(), y.length);
        int p = Math.min(nrows(), x.length);

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
        int n = Math.min(ncols(), y.length);
        int p = Math.min(nrows(), x.length);

        for (int i = 0; i < n; i++) {
            for (int k = 0; k < p; k++) {
                y[i] += get(k, i) * x[k];
            }
        }

        return y;
    }

    @Override
    public double[] atxpy(double[] x, double[] y, double b) {
        int n = Math.min(ncols(), y.length);
        int p = Math.min(nrows(), x.length);

        for (int i = 0; i < n; i++) {
            y[i] *= b;
            for (int k = 0; k < p; k++) {
                y[i] += get(k, i) * x[k];
            }
        }

        return y;
    }

    @Override
    public NDMatrix ata() {
        return new NDMatrix(A.transpose().mmul(A));
    }

    @Override
    public NDMatrix aat() {
        return new NDMatrix(A.mmul(A.transpose()));
    }

    /**
     * A[i][j] += x
     */
    public double add(int i, int j, double x) {
        double y = get(i, j) + x;
        return set(i, j, y);
    }

    /**
     * A[i][j] -= x
     */
    public double sub(int i, int j, double x) {
        double y = get(i, j) - x;
        return set(i, j, y);
    }

    /**
     * A[i][j] *= x
     */
    public double mul(int i, int j, double x) {
        double y = get(i, j) * x;
        return set(i, j, y);
    }

    /**
     * A[i][j] /= x
     */
    public double div(int i, int j, double x) {
        double y = get(i, j) / x;
        return set(i, j, y);
    }

    @Override
    public NDMatrix abmm(NDMatrix B) {
        return new NDMatrix(A.mmul(B.A));
    }

    @Override
    public NDMatrix abtmm(NDMatrix B) {
        return new NDMatrix(A.mmul(B.A.transpose()));
    }

    @Override
    public NDMatrix atbmm(NDMatrix B) {
        return new NDMatrix(A.transpose().mmul(B.A));
    }

    /**
     * A = A + B
     * @return this matrix
     */
    public NDMatrix add(NDMatrix b) {
        if (nrows() != b.nrows() || ncols() != b.ncols()) {
            throw new IllegalArgumentException("Matrix is not of same size.");
        }

        A.add(b.A, A);
        return this;
    }

    /**
     * A = A - B
     * @return this matrix
     */
    public NDMatrix sub(NDMatrix b) {
        if (nrows() != b.nrows() || ncols() != b.ncols()) {
            throw new IllegalArgumentException("Matrix is not of same size.");
        }

        A.sub(b.A, A);
        return this;
    }

    /**
     * Element-wise multiplication A = A * B
     * @return this matrix
     */
    public NDMatrix mul(NDMatrix b) {
        if (nrows() != b.nrows() || ncols() != b.ncols()) {
            throw new IllegalArgumentException("Matrix is not of same size.");
        }

        A.mul(b.A, A);
        return this;
    }

    /**
     * Element-wise division A = A / B
     * A = A - B
     * @return this matrix
     */
    public NDMatrix div(NDMatrix b) {
        if (nrows() != b.nrows() || ncols() != b.ncols()) {
            throw new IllegalArgumentException("Matrix is not of same size.");
        }

        A.div(b.A, A);
        return this;
    }

    /**
     * Element-wise addition A = A + x
     */
    public NDMatrix add(double x) {
        A.addi(x);
        return this;
    }

    /**
     * Element-wise subtraction A = A - x
     */
    public NDMatrix sub(double x) {
        A.subi(x);
        return this;

    }

    /**
     * Element-wise multiplication A = A * x
     */
    public NDMatrix mul(double x) {
        A.muli(x);
        return this;

    }

    /**
     * Element-wise division A = A / x
     */
    public NDMatrix div(double x) {
        A.divi(x);
        return this;

    }

    /**
     * Replaces NaN's with given value.
     */
    public NDMatrix replaceNaN(double x) {
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
        return A.sumNumber().doubleValue();
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

    /**
     * Returns the matrix transpose.
     */
    public NDMatrix transpose() {
        return new NDMatrix(A.transpose());
    }

    /**
     * Returns the matrix inverse or pseudo inverse.
     * @return inverse of A if A is square, pseudo inverse otherwise.
     */
    public NDMatrix inverse() {
        return new NDMatrix(InvertMatrix.invert(A, false));
    }

    /**
     * Returns the matrix trace. The sum of the diagonal elements.
     */
    public double trace() {
        int n = Math.min(nrows(), ncols());

        double t = 0.0;
        for (int i = 0; i < n; i++) {
            t += get(i, i);
        }

        return t;
    }

    @Override
    public String toString() {
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
