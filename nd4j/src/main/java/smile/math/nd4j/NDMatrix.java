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
import smile.math.matrix.DenseMatrix;
import smile.math.matrix.MatrixMultiplication;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.inverse.InvertMatrix;

/**
 * ND4j matrix wrapper.
 *
 * @author Haifeng Li
 */
public class NDMatrix implements DenseMatrix {
    /**
     * The matrix storage.
     */
    private INDArray A;

    /**
     * Constructor.
     * @param A the array of matrix.
     */
    public NDMatrix(double[][] A) {
        this.A = Nd4j.create(A);
    }

    /**
     * Constructor.
     * @param A the array of column vector.
     */
    public NDMatrix(double[] A) {
        this.A = Nd4j.create(A, new int[]{A.length, 1});
    }

    /**
     * Constructor.
     * @param A the NDArray of matrix.
     */
    public NDMatrix(INDArray A) {
        this.A = A;
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
        if (value == 0.0)
            A = Nd4j.zeros(rows, cols);
        else if (value == 1.0)
            A = Nd4j.ones(rows, cols);
        else
            A = Nd4j.zeros(rows, cols).addi(value);
    }

    public static NDMatrix eye(int n) {
        return new NDMatrix(Nd4j.eye(n));
    }

    @Override
    public String toString() {
        return toString(false);
    }

    @Override
    public NDMatrix copy() {
        return new NDMatrix(A.dup());
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
    public NDMatrix abmm(DenseMatrix B) {
        if (B instanceof NDMatrix) {
            return new NDMatrix(A.mmul(((NDMatrix)B).A));
        }

        throw new IllegalArgumentException("NDMatrix.abmm() parameter must be NDMatrix");
    }

    @Override
    public NDMatrix abtmm(DenseMatrix B) {
        if (B instanceof NDMatrix) {
            return new NDMatrix(A.mmul(((NDMatrix)B).A.transpose()));
        }

        throw new IllegalArgumentException("NDMatrix.abtmm() parameter must be NDMatrix");
    }

    @Override
    public NDMatrix atbmm(DenseMatrix B) {
        if (B instanceof NDMatrix) {
            return new NDMatrix(A.transpose().mmul(((NDMatrix)B).A));
        }

        throw new IllegalArgumentException("NDMatrix.atbmm() parameter must be NDMatrix");
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
     * Returns the sum of all elements in the matrix.
     * @return the sum of all elements.
     */
    public double sum() {
        return A.sumNumber().doubleValue();
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
}
