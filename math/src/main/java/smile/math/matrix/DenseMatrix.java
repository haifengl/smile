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
public abstract class DenseMatrix implements Matrix, LinearSolver {
    /**
     * True if the matrix is symmetric.
     */
    private boolean symmetric = false;
    /**
     * True if the matrix is positive definite.
     */
    private boolean positive = false;
    /**
     * The LU decomposition of A.
     */
    private CholeskyDecomposition cholesky;
    /**
     * The LU decomposition of A.
     */
    private LUDecomposition lu;
    /**
     * The LU decomposition of A.
     */
    private QRDecomposition qr;
    /**
     * The SVD decomposition of A.
     */
    private SingularValueDecomposition svd;
    /**
     * The eigen decomposition of A.
     */
    private EigenValueDecomposition eigen;
    /**
     * Matrix determinant.
     */
    private double det;
    /**
     * The rank of Matrix
     */
    private int rank;


    /**
     * Constructor.
     * If the matrix is updated, no check on if the matrix is symmetric.
     */
    public DenseMatrix() {
        this.symmetric = false;
        this.positive = false;
    }

    /**
     * Constructor.
     * If the matrix is updated, no check on if the matrix is symmetric.
     * @param symmetric true if the matrix is symmetric.
     */
    public DenseMatrix(boolean symmetric) {
        this.symmetric = symmetric;
    }

    /**
     * Constructor.
     * If the matrix is updated, no check on if the matrix is symmetric
     * and/or positive definite. The symmetric and positive definite
     * properties are intended for read-only matrices.
     * @param symmetric true if the matrix is symmetric.
     * @param positive true if the matrix is positive definite.
     */
    public DenseMatrix(boolean symmetric, boolean positive) {
        this.symmetric = symmetric;
        this.positive = positive;
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
     * Set the entry value at row i and column j.
     */
    public abstract DenseMatrix set(int i, int j, double x);

    /**
     * Set the entry value at row i and column j. For Scala users.
     */
    public DenseMatrix update(int i, int j, double x) {
        return set(i, j, x);
    }

    /**
     * Sets the diagonal to the values of <code>diag</code> as long
     * as possible (i.e while there are elements left in diag or the dim of matrix
     * is not big enough.
     */
    public void setDiag(double[] diag) {
        for (int i = 0; i < ncols() && i < nrows() && i < diag.length; i++) {
            set(i, i, diag[i]);
        }
    }

    /**
     * A[i][j] += x
     */
    public abstract DenseMatrix add(int i, int j, double x);

    /**
     * A[i][j] -= x
     */
    public abstract DenseMatrix sub(int i, int j, double x);

    /**
     * A[i][j] *= x
     */
    public abstract DenseMatrix mul(int i, int j, double x);

    /**
     * A[i][j] /= x
     */
    public abstract DenseMatrix div(int i, int j, double x);

    /**
     * A = A + B
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
     * A = A - B
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
     * Element-wise multiplication A = A * B
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
     * Element-wise division A = A / B
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
     * Element-wise multiplication A = A * x
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
     * Element-wise division A = A / x
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

    /**
     * Returns the matrix transpose.
     */
    public abstract DenseMatrix transpose();

    /**
     * Returns the matrix inverse or pseudo inverse.
     * @return inverse of A if A is square, pseudo inverse otherwise.
     */
    public DenseMatrix inverse() {
        double[][] I = Math.eye(ncols(), nrows());
        solve(I, I);
        if (this instanceof RowMajorMatrix)
            return new RowMajorMatrix(I);
        else if (this instanceof ColumnMajorMatrix)
            return new ColumnMajorMatrix(I);
        else
            return new NaiveMatrix(I);
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

    /**
     * Returns the matrix determinant.
     */
    public double det() {
        if (nrows() != ncols()) {
            throw new IllegalArgumentException(String.format("Matrix is not square: %d x %d", nrows(), ncols()));
        }

        if (symmetric && positive) {
            cholesky();
        } else {
            lu();
        }

        return det;
    }

    /**
     * Returns the matrix rank.
     * @return effective numerical rank.
     */
    public int rank() {
        svd();
        return rank;
    }

    /**
     * Returns the eigen value decomposition.
     */
    public EigenValueDecomposition eigen() {
        if (nrows() != ncols()) {
            throw new UnsupportedOperationException("The matrix is not square.");
        }

        int n = nrows();
        if (eigen == null || eigen.getEigenVectors().length != n) {
            double[][] V = array();
            eigen = EigenValueDecomposition.decompose(V, symmetric);

            positive = true;
            for (int i = 0; i < n; i++) {
                if (eigen.getEigenValues()[i] <= 0) {
                    positive = false;
                    break;
                }
            }
        }

        return eigen;
    }

    /**
     * Returns the k largest eigen pairs. Only works for symmetric matrix.
     */
    public EigenValueDecomposition eigen(int k) {
        if (nrows() != ncols()) {
            throw new UnsupportedOperationException("The matrix is not square.");
        }

        if (!symmetric) {
            throw new UnsupportedOperationException("The current implementation of eigen value decomposition only works for symmetric matrices");
        }

        if (eigen == null || eigen.getEigenVectors().length != k) {
            eigen = EigenValueDecomposition.decompose(this, k);
        }

        return eigen;
    }

    /**
     * Returns the singular value decomposition.
     */
    public SingularValueDecomposition svd() {
        if (svd != null) {
            double[][] V = array();
            svd = SingularValueDecomposition.decompose(V);
            rank = svd.rank();
        }

        return svd;
    }

    /**
     * Returns the LU decomposition.
     */
    public LUDecomposition lu() {
        if (nrows() != ncols()) {
            throw new UnsupportedOperationException("The matrix is not square.");
        }

        if (lu == null) {
            lu = new LUDecomposition(this);
            det = lu.det();
        }
        return lu;
    }

    /**
     * Returns the Cholesky decomposition.
     */
    public CholeskyDecomposition cholesky() {
        if (nrows() != ncols()) {
            throw new UnsupportedOperationException("The matrix is not square.");
        }

        if (!symmetric || !positive) {
            throw new UnsupportedOperationException("The matrix is not symmetric positive definite.");
        }

        if (cholesky == null) {
            cholesky = new CholeskyDecomposition(this);
            det = cholesky.det();
        }

        return cholesky;
    }

    /**
     * Returns the QR decomposition.
     */
    public QRDecomposition qr() {
        if (qr == null) {
            qr = new QRDecomposition(this);
        }
        return qr;
    }

    /**
     * Solve A*x = b (exact solution if A is square, least squares
     * solution otherwise).
     * @return the solution matrix, actually b.
     */
    @Override
    public void solve(double[] b, double[] x) {
        if (nrows() == ncols()) {
            if (symmetric && positive) {
                cholesky().solve(b, x);
            } else {
                lu().solve(b, x);
            }
        } else {
            qr().solve(b, x);
        }
    }

    /**
     * Solve A*X = B in place (exact solution if A is square, least squares
     * solution otherwise), which means the results will be stored in B.
     * @return the solution matrix, actually X.
     */
    public void solve(double[][] B, double[][] X) {
        if (nrows() == ncols()) {
            if (symmetric && positive) {
                cholesky().solve(B, X);
            } else {
                lu().solve(B, X);
            }
        } else {
            qr().solve(B, X);
        }
    }

    /**
     * Iteratively improve a solution to linear equations.
     *
     * @param b right hand side of linear equations.
     * @param x a solution to linear equations.
     */
    public void improve(double[] b, double[] x) {
        if (nrows() != ncols()) {
            throw new IllegalStateException("A is not square.");
        }

        int n = nrows();

        if (x.length != n || b.length != n) {
            throw new IllegalArgumentException(String.format("Row dimensions do not agree: A is %d x %d, but b is %d x 1 and x is %d x 1", nrows(), ncols(), b.length, x.length));
        }

        if (symmetric && positive) {
            // Calculate the right-hand side, accumulating the residual
            // in higher precision.
            double[] r = new double[n];
            for (int i = 0; i < n; i++) {
                double sdp = -b[i];
                for (int j = 0; j < n; j++) {
                    sdp += get(i, j) * x[j];
                }
                r[i] = sdp;
            }

            // Solve for the error term.
            cholesky.solve(r);

            // Subtract the error from the old soluiton.
            for (int i = 0; i < n; i++) {
                x[i] -= r[i];
            }
        } else {
            // Calculate the right-hand side, accumulating the residual
            // in higher precision.
            double[] r = new double[n];
            for (int i = 0; i < n; i++) {
                double sdp = -b[i];
                for (int j = 0; j < n; j++) {
                    sdp += get(i, j) * x[j];
                }
                r[i] = sdp;
            }

            // Solve for the error term.
            lu.solve(r);

            // Subtract the error from the old soluiton.
            for (int i = 0; i < n; i++) {
                x[i] -= r[i];
            }
        }
    }
}
