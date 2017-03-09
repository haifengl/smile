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
 * Naive implementation of Matrix interface. The data is stored in
 * Java 2D array, which is not friendly for heavy matrix computation
 * on modern CPU. If possible, please use RowMajorMatrix or
 * ColumnMajorMatrix that use 1D array.
 *
 * @see RowMajorMatrix
 * @see ColumnMajorMatrix
 * 
 * @author Haifeng Li
 */
public class NaiveMatrix extends DenseMatrix implements MatrixMultiplication<NaiveMatrix, NaiveMatrix> {

    /**
     * The original matrix.
     */
    private double[][] A;

    /**
     * Constructor. Note that this is a shallow copy of A.
     * @param A the array of matrix.
     */
    public NaiveMatrix(double[][] A) {
        this.A = A;
    }

    /**
     * Constructor. Note that this is a shallow copy of A.
     * If the matrix is updated, no check on if the matrix is symmetric.
     * @param A the array of matrix.
     * @param symmetric true if the matrix is symmetric.
     */
    public NaiveMatrix(double[][] A, boolean symmetric) {
        super(symmetric);

        if (symmetric && A.length != A[0].length) {
            throw new IllegalArgumentException("A is not square");
        }

        this.A = A;
    }

    /**
     * Constructor. Note that this is a shallow copy of A.
     * If the matrix is updated, no check on if the matrix is symmetric
     * and/or positive definite. The symmetric and positive definite
     * properties are intended for read-only matrices.
     * @param A the array of matrix.
     * @param symmetric true if the matrix is symmetric.
     * @param positive true if the matrix is positive definite.
     */
    public NaiveMatrix(double[][] A, boolean symmetric, boolean positive) {
        super(symmetric, positive);

        if (symmetric && A.length != A[0].length) {
            throw new IllegalArgumentException("A is not square");
        }

        this.A = A;
    }

    /**
     * Constructor of all-zero matrix.
     */
    public NaiveMatrix(int rows, int cols) {
        A = new double[rows][cols];
    }

    /**
     * Constructor. Fill the matrix with given value.
     */
    public NaiveMatrix(int rows, int cols, double value) {
        A = new double[rows][cols];
        for (int i = 0; i < rows; i++) {
            Arrays.fill(A[i], value);
        }
    }

    /**
     * Constructor of matrix with normal random values with given mean and standard dev.
     */
    public NaiveMatrix(int rows, int cols, double mu, double sigma) {
        GaussianDistribution g = new GaussianDistribution(mu, sigma);

        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                A[i][j] = g.rand();
            }
        }
    }

    @Override
    public double[][] array() {
        double[][] B = new double[nrows()][ncols()];
        for (int i = 0; i < nrows(); i++) {
            System.arraycopy(A[i], 0, B[i], 0, ncols());
        }
        return B;
    }

    public RowMajorMatrix toRowMajor() {
        return new RowMajorMatrix(A, isSymmetric(), isPositive());
    }

    public ColumnMajorMatrix toColumnMajor() {
        return new ColumnMajorMatrix(A, isSymmetric(), isPositive());
    }

    @Override
    public int nrows() {
        return A.length;
    }

    @Override
    public int ncols() {
        return A[0].length;
    }

    @Override
    public double get(int i, int j) {
        return A[i][j];
    }

    @Override
    public NaiveMatrix set(int i, int j, double x) {
        A[i][j] = x;
        return this;
    }

    @Override
    public NaiveMatrix add(int i, int j, double x) {
        A[i][j] += x;
        return this;
    }

    @Override
    public NaiveMatrix sub(int i, int j, double x) {
        A[i][j] -= x;
        return this;
    }

    @Override
    public NaiveMatrix mul(int i, int j, double x) {
        A[i][j] *= x;
        return this;
    }

    @Override
    public NaiveMatrix div(int i, int j, double x) {
        A[i][j] /= x;
        return this;
    }

    @Override
    public NaiveMatrix ata() {
        return new NaiveMatrix(Math.atamm(A));
    }

    @Override
    public NaiveMatrix aat() {
        return new NaiveMatrix(Math.aatmm(A));
    }

    @Override
    public void ax(double[] x, double[] y) {
        Math.ax(A, x, y);
    }

    @Override
    public void axpy(double[] x, double[] y) {
        Math.axpy(A, x, y);
    }

    @Override
    public void axpy(double[] x, double[] y, double b) {
        Math.axpy(A, x, y, b);
    }

    @Override
    public void atx(double[] x, double[] y) {
        Math.atx(A, x, y);
    }

    @Override
    public void atxpy(double[] x, double[] y) {
        Math.atxpy(A, x, y);
    }

    @Override
    public void atxpy(double[] x, double[] y, double b) {
        Math.atxpy(A, x, y, b);
    }

    @Override
    public NaiveMatrix mm(NaiveMatrix b) {
        return new NaiveMatrix(Math.abmm(A, b.A));
    }

    /**
     * Returns the matrix transpose.
     */
    @Override
    public NaiveMatrix transpose() {
        int m = A.length;
        int n = A[0].length;

        double[][] B = new double[n][m];
        for (int i = 0; i < m; i++) {
            for (int j = 0; j < n; j++) {
                B[j][i] = A[i][j];
            }
        }

        return new NaiveMatrix(B);
    }
}
