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
public class NaiveMatrix implements DenseMatrix {

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
     * Constructor of a square diagonal matrix with the elements of vector diag on the main diagonal.
     */
    public NaiveMatrix(double[] diag) {
        this(diag.length, diag.length);
        for (int i = 0; i < diag.length; i++)
            A[i][i] = diag[i];
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

    /**
     * Returns an n-by-n identity matrix with ones on the main diagonal and zeros elsewhere.
     */
    public static NaiveMatrix eye(int n) {
        return eye(n, n);
    }

    /**
     * Returns an n-by-n identity matrix with ones on the main diagonal and zeros elsewhere.
     */
    public static NaiveMatrix eye(int m, int n) {
        double[][] x = new double[m][n];
        int l = Math.min(m, n);
        for (int i = 0; i < l; i++) {
            x[i][i] = 1.0;
        }

        return new NaiveMatrix(x);
    }

    @Override
    public String toString() {
        return toString(false);
    }

    @Override
    public ColumnMajorMatrix copy() {
        return new ColumnMajorMatrix(A);
    }

    @Override
    public double[][] array() {
        double[][] B = new double[nrows()][ncols()];
        for (int i = 0; i < nrows(); i++) {
            System.arraycopy(A[i], 0, B[i], 0, ncols());
        }
        return B;
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
    public double set(int i, int j, double x) {
        return A[i][j] = x;
    }

    @Override
    public double add(int i, int j, double x) {
        return A[i][j] += x;
    }

    @Override
    public double sub(int i, int j, double x) {
        return A[i][j] -= x;
    }

    @Override
    public double mul(int i, int j, double x) {
        return A[i][j] *= x;
    }

    @Override
    public double div(int i, int j, double x) {
        return A[i][j] /= x;
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
    public double[] ax(double[] x, double[] y) {
        return Math.ax(A, x, y);
    }

    @Override
    public double[] axpy(double[] x, double[] y) {
        return Math.axpy(A, x, y);
    }

    @Override
    public double[] axpy(double[] x, double[] y, double b) {
        return Math.axpy(A, x, y, b);
    }

    @Override
    public double[] atx(double[] x, double[] y) {
        return Math.atx(A, x, y);
    }

    @Override
    public double[] atxpy(double[] x, double[] y) {
        return Math.atxpy(A, x, y);
    }

    @Override
    public double[] atxpy(double[] x, double[] y, double b) {
        return Math.atxpy(A, x, y, b);
    }

    @Override
    public NaiveMatrix abmm(DenseMatrix b) {
        if (A[0].length != b.nrows()) {
            throw new IllegalArgumentException(String.format("Matrix multiplication A * B: %d x %d vs %d x %d", A.length, A[0].length, b.nrows(), b.ncols()));
        }

        int m = A.length;
        int n = b.ncols();
        int l = b.nrows();

        double[][] C = new double[m][n];
        for (int i = 0; i < m; i++) {
            for (int j = 0; j < n; j++) {
                C[i][j] = 0.0;
                for (int k = 0; k < l; k++) {
                    C[i][j] += A[i][k] * b.get(k, j);
                }
            }
        }

        return new NaiveMatrix(C);
    }

    @Override
    public NaiveMatrix abtmm(DenseMatrix b) {
        if (A[0].length != b.nrows()) {
            throw new IllegalArgumentException(String.format("Matrix multiplication A * B: %d x %d vs %d x %d", A.length, A[0].length, b.nrows(), b.ncols()));
        }

        int m = A.length;
        int n = b.nrows();
        int l = b.ncols();

        double[][] C = new double[m][n];
        for (int i = 0; i < m; i++) {
            for (int j = 0; j < n; j++) {
                C[i][j] = 0.0;
                for (int k = 0; k < l; k++) {
                    C[i][j] += A[i][k] * b.get(j, k);
                }
            }
        }
        return new NaiveMatrix(C);
    }

    @Override
    public NaiveMatrix atbmm(DenseMatrix b) {
        if (A[0].length != b.nrows()) {
            throw new IllegalArgumentException(String.format("Matrix multiplication A * B: %d x %d vs %d x %d", A.length, A[0].length, b.nrows(), b.ncols()));
        }

        int m = A[0].length;
        int n = b.ncols();
        int l = b.nrows();

        double[][] C = new double[m][n];
        for (int i = 0; i < m; i++) {
            for (int j = 0; j < n; j++) {
                C[i][j] = 0.0;
                for (int k = 0; k < l; k++) {
                    C[i][j] += A[k][i] * b.get(k, j);
                }
            }
        }

        return new NaiveMatrix(C);
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
