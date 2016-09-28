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
 * A matrix is a rectangular array of numbers. An item in a matrix is called
 * an entry or an element. Entries are often denoted by a variable with two
 * subscripts. Matrices of the same size can be added and subtracted entrywise
 * and matrices of compatible size can be multiplied. These operations have
 * many of the properties of ordinary arithmetic, except that matrix
 * multiplication is not commutative, that is, AB and BA are not equal in
 * general.
 * <p>
 * Matrices are a key tool in linear algebra. One use of matrices is to
 * represent linear transformations and matrix multiplication corresponds
 * to composition of linear transformations. Matrices can also keep track of
 * the coefficients in a system of linear equations. For a square matrix,
 * the determinant and inverse matrix (when it exists) govern the behavior
 * of solutions to the corresponding system of linear equations, and
 * eigenvalues and eigenvectors provide insight into the geometry of
 * the associated linear transformation.
 * <p>
 * There are several methods to render matrices into a more easily accessible
 * form. They are generally referred to as matrix transformation or matrix
 * decomposition techniques. The interest of all these decomposition techniques
 * is that they preserve certain properties of the matrices in question, such
 * as determinant, rank or inverse, so that these quantities can be calculated
 * after applying the transformation, or that certain matrix operations are
 * algorithmically easier to carry out for some types of matrices.
 * <p>
 * The LU decomposition factors matrices as a product of lower (L) and an upper
 * triangular matrices (U). Once this decomposition is calculated, linear
 * systems can be solved more efficiently, by a simple technique called
 * forward and back substitution. Likewise, inverses of triangular matrices
 * are algorithmically easier to calculate. The QR decomposition factors matrices
 * as a product of an orthogonal (Q) and a right triangular matrix (R). QR decomposition
 * is often used to solve the linear least squares problem, and is the basis for
 * a particular eigenvalue algorithm, the QR algorithm. Singular value decomposition
 * expresses any matrix A as a product UDV', where U and V are unitary matrices
 * and D is a diagonal matrix. The eigendecomposition or diagonalization
 * expresses A as a product VDV<sup>-1</sup>, where D is a diagonal matrix and
 * V is a suitable invertible matrix. If A can be written in this form, it is
 * called diagonalizable.
 * 
 * @author Haifeng Li
 */
public class Matrix implements DenseMatrix {

    /**
     * The original matrix.
     */
    private double[][] A;
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
     * Constructor. Note that this is a shallow copy of A.
     * @param A the array of matrix.
     */
    public Matrix(double[][] A) {
        this.A = A;
    }

    /**
     * Constructor. Note that this is a shallow copy of A.
     * If the matrix is updated, no check on if the matrix is symmetric.
     * @param A the array of matrix.
     * @param symmetric true if the matrix is symmetric.
     */
    public Matrix(double[][] A, boolean symmetric) {
        if (symmetric && A.length != A[0].length) {
            throw new IllegalArgumentException("A is not square");
        }

        this.A = A;
        this.symmetric = symmetric;
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
    public Matrix(double[][] A, boolean symmetric, boolean positive) {
        if (symmetric && A.length != A[0].length) {
            throw new IllegalArgumentException("A is not square");
        }

        this.A = A;
        this.symmetric = symmetric;
        this.positive = positive;
    }

    /**
     * Constructor of all-zero matrix.
     */
    public Matrix(int rows, int cols) {
        A = new double[rows][cols];
    }

    /**
     * Constructor. Fill the matrix with given value.
     */
    public Matrix(int rows, int cols, double value) {
        A = new double[rows][cols];
        for (int i = 0; i < rows; i++) {
            Arrays.fill(A[i], value);
        }
    }

    /**
     * Constructor of matrix with normal random values with given mean and standard dev.
     */
    public Matrix(int rows, int cols, double mu, double sigma) {
        GaussianDistribution g = new GaussianDistribution(mu, sigma);

        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                A[i][j] = g.rand();
            }
        }
    }

    /**
     * Return the two-dimensional array of matrix.
     * @return the two-dimensional array of matrix.
     */
    public double[][] array() {
        return A;
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
    public double apply(int i, int j) {
        return A[i][j];
    }

    @Override
    public Matrix set(int i, int j, double x) {
        A[i][j] = x;
        return this;
    }

    @Override
    public Matrix update(int i, int j, double x) {
        A[i][j] = x;
        return this;
    }

    @Override
    public Matrix add(int i, int j, double x) {
        A[i][j] += x;
        return this;
    }

    @Override
    public Matrix sub(int i, int j, double x) {
        A[i][j] -= x;
        return this;
    }

    @Override
    public Matrix mul(int i, int j, double x) {
        A[i][j] *= x;
        return this;
    }

    @Override
    public Matrix div(int i, int j, double x) {
        A[i][j] /= x;
        return this;
    }

    @Override
    public Matrix ata() {
        return new Matrix(Math.atamm(A));
    }

    @Override
    public Matrix aat() {
        return new Matrix(Math.aatmm(A));
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
    public void asolve(double[] b, double[] x) {
        int m = A.length;
        int n = A[0].length;

        if (m != n) {
            throw new IllegalStateException("Matrix is not square.");
        }

        for (int i = 0; i < n; i++) {
            x[i] = A[i][i] != 0.0 ? b[i] / A[i][i] : b[i];
        }
    }

    /**
     * A = A + B
     * @return this matrix
     */
    public Matrix add(Matrix b) {
        if (nrows() != b.nrows() || ncols() != b.ncols()) {
            throw new IllegalArgumentException("Matrix is not of same size.");
        }

        int m = A.length;
        int n = A[0].length;

        for (int i = 0; i < m; i++) {
            for (int j = 0; j < n; j++) {
                A[i][j] += b.A[i][j];
            }
        }
        return this;
    }

    /**
     * Element-wise multiplication A = A * x
     */
    public Matrix scale(double x) {

        int m = A.length;
        int n = A[0].length;

        for (int i = 0; i < m; i++) {
            for (int j = 0; j < n; j++) {
                A[i][j] *= x;
            }
        }

        return this;
    }

    /**
     * Element-wise division A = A / x
     */
    public Matrix divide(double x) {

        int m = A.length;
        int n = A[0].length;

        for (int i = 0; i < m; i++) {
            for (int j = 0; j < n; j++) {
                A[i][j] /= x;
            }
        }

        return this;
    }

    /**
     * Replaces NaN's with given value.
     */
    public Matrix replaceNaN(double x) {
        int m = A.length;
        int n = A[0].length;

        for (int i = 0; i < m; i++) {
            for (int j = 0; j < n; j++) {
                if (Double.isNaN(A[i][j])) {
                    A[i][j] = x;
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
        double s = 0.0;
        int m = A.length;
        int n = A[0].length;

        for (int i = 0; i < m; i++) {
            for (int j = 0; j < n; j++) {
                s += A[i][j];
            }
        }

        return s;
    }

    /**
     * Returns the matrix transpose.
     */
    @Override
    public Matrix transpose() {
        int m = A.length;
        int n = A[0].length;

        double[][] B = new double[n][m];
        for (int i = 0; i < m; i++) {
            for (int j = 0; j < n; j++) {
                B[j][i] = A[i][j];
            }
        }

        return new Matrix(B);
    }

    /**
     * Returns the matrix inverse or pseudo inverse.
     * @return inverse of A if A is square, pseudo inverse otherwise.
     */
    public double[][] inverse() {
        return solve(Math.eye(ncols(), nrows()));
    }

    /**
     * Returns the matrix trace. The sum of the diagonal elements.
     */
    public double trace() {
        int n = Math.min(A.length, A[0].length);

        double t = 0.0;
        for (int i = 0; i < n; i++) {
            t += A[i][i];
        }

        return t;
    }

    /**
     * Returns the matrix determinant.
     */
    public double det() {
        if (A.length != A[0].length) {
            throw new IllegalArgumentException(String.format("Matrix is not square: %d x %d", A.length, A[0].length));
        }

        if (symmetric && positive) {
            if (cholesky == null) {
                cholesky();
            }
        } else {
            if (lu == null) {
                lu();
            }
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
     * Returns the largest eigen pair of matrix with the power iteration
     * under the assumptions A has an eigenvalue that is strictly greater
     * in magnitude than its other eigenvalues and the starting
     * vector has a nonzero component in the direction of an eigenvector
     * associated with the dominant eigenvalue.
     * @param v on input, it is the non-zero initial guess of the eigen vector.
     * On output, it is the eigen vector corresponding largest eigen value.
     * @return the largest eigen value.
     */
    public double eigen(double[] v) {
        if (nrows() != ncols()) {
            throw new UnsupportedOperationException("The matrix is not square.");
        }

        return EigenValueDecomposition.eigen(this, v);
    }

    /**
     * Returns the eigen value decomposition.
     */
    public EigenValueDecomposition eigen() {
        if (nrows() != ncols()) {
            throw new UnsupportedOperationException("The matrix is not square.");
        }

        int n = A.length;
        if (eigen == null || eigen.getEigenVectors().length != n) {
            double[][] V = new double[n][n];

            for (int i = 0; i < n; i++) {
                System.arraycopy(A[i], 0, V[i], 0, n);
            }

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
        if (svd == null) {
            int m = A.length;
            int n = A[0].length;
            double[][] V = new double[m][n];

            for (int i = 0; i < m; i++) {
                System.arraycopy(A[i], 0, V[i], 0, n);
            }

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
            lu = new LUDecomposition(A);
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
            cholesky = new CholeskyDecomposition(A);
            det = cholesky.det();
        }
        
        return cholesky;
    }

    /**
     * Returns the QR decomposition.
     */
    public QRDecomposition qr() {
        if (qr == null) {
            qr = new QRDecomposition(A);
        }
        return qr;
    }

    /**
     * Solve A*x = b in place (exact solution if A is square, least squares
     * solution otherwise), which means the results will be stored in b.
     * @return the solution matrix, actually b.
     */
    public double[] solve(double[] b) {
        if (A.length == A[0].length) {
            if (symmetric && positive) {
                cholesky().solve(b);
            } else {
                lu().solve(b);
            }
        } else {
            qr().solve(b);
        }
        return b;
    }

    /**
     * Solve A*X = B in place (exact solution if A is square, least squares
     * solution otherwise), which means the results will be stored in B.
     * @return the solution matrix, actually B.
     */
    public double[][] solve(double[][] B) {
        if (A.length == A[0].length) {
            if (symmetric && positive) {
                cholesky().solve(B);
            } else {
                lu().solve(B);
            }
        } else {
            qr().solve(B);
        }
        return B;
    }

    /**
     * Iteratively improve a solution to linear equations.
     *
     * @param b right hand side of linear equations.
     * @param x a solution to linear equations.
     */
    public void improve(double[] b, double[] x) {
        int n = A.length;

        if (A.length != A[0].length) {
            throw new IllegalStateException("A is not square.");
        }

        if (x.length != n || b.length != n) {
            throw new IllegalArgumentException(String.format("Row dimensions do not agree: A is %d x %d, but b is %d x 1 and x is %d x 1", A.length, A[0].length, b.length, x.length));
        }

        if (symmetric && positive) {
            // Calculate the right-hand side, accumulating the residual
            // in higher precision.
            double[] r = new double[n];
            for (int i = 0; i < n; i++) {
                double sdp = -b[i];
                for (int j = 0; j < n; j++) {
                    sdp += A[i][j] * x[j];
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
                    sdp += A[i][j] * x[j];
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
