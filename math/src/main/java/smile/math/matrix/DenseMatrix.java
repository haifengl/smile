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
    /**
     * Set the entry value at row i and column j.
     */
    public abstract double set(int i, int j, double x);

    /**
     * Set the entry value at row i and column j. For Scala users.
     */
    default public double update(int i, int j, double x) {
        return set(i, j, x);
    }

    /**
     * Sets the diagonal to the values of <code>diag</code> as long
     * as possible (i.e while there are elements left in diag or the dim of matrix
     * is not big enough.
     */
    /*
    default public void setDiag(double[] diag) {
        for (int i = 0; i < ncols() && i < nrows() && i < diag.length; i++) {
            set(i, i, diag[i]);
        }
    }
    */

    /**
     * Returns the matrix transpose.
     */
    public DenseMatrix transpose();

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
     * A = A + B
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
     * A = A - B
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
     * Element-wise multiplication A = A * B
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
     * Element-wise division A = A / B
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
     * Element-wise addition A = A + x
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
     * Element-wise subtraction A = A - x
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
     * Element-wise multiplication A = A * x
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
     * Element-wise division A = A / x
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
