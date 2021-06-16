/*
 * Copyright (c) 2010-2020 Haifeng Li. All rights reserved.
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
 */

package smile.math.matrix.fp32;

import java.io.IOException;
import java.io.LineNumberReader;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

import smile.math.MathEx;
import smile.math.blas.Transpose;
import smile.util.SparseArray;

import static smile.math.blas.Transpose.NO_TRANSPOSE;
import static smile.math.blas.Transpose.TRANSPOSE;
import static smile.math.blas.UPLO.LOWER;

/**
 * Matrix base class. The most important method is the matrix vector
 * multiplication, which is the only operation needed in many iterative matrix
 * algorithms, e.g. biconjugate gradient method for solving linear equations and
 * power iteration and Lanczos algorithm for eigen decomposition, which are
 * usually very efficient for very large and sparse matrices.
 * <p>
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
public abstract class IMatrix implements Cloneable, Serializable {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(IMatrix.class);

    /**
     * The row names.
     */
    private String[] rowNames;
    /**
     * The column names.
     */
    private String[] colNames;

    /**
     * Returns the number of rows.
     * @return the number of rows.
     */
    public abstract int nrow();

    /**
     * Returns the number of columns.
     * @return the number of columns.
     */
    public abstract int ncol();

    /**
     * Returns the number of stored matrix elements. For conventional matrix,
     * it is simplify nrow * ncol. But it is usually much less for band,
     * packed or sparse matrix.
     * @return the number of stored matrix elements.
     */
    public abstract long size();

    /**
     * Returns the row names.
     * @return the row names.
     */
    public String[] rowNames() {
        return rowNames;
    }

    /**
     * Sets the row names.
     * @param names the row names.
     */
    public void rowNames(String[] names) {
        if (names != null && names.length != nrow()) {
            throw new IllegalArgumentException(String.format("Invalid row names length: %d != %d", names.length, nrow()));
        }
        rowNames = names;
    }

    /**
     * Returns the name of i-th row.
     * @param i the row index.
     * @return the name of i-th row.
     */
    public String rowName(int i) {
        return rowNames[i];
    }

    /**
     * Returns the column names.
     * @return the column names.
     */
    public String[] colNames() {
        return colNames;
    }

    /**
     * Sets the column names.
     * @param names the column names.
     */
    public void colNames(String[] names) {
        if (names != null && names.length != ncol()) {
            throw new IllegalArgumentException(String.format("Invalid column names length: %d != %d", names.length, ncol()));
        }
        colNames = names;
    }

    /**
     * Returns the name of i-th column.
     * @param i the column index.
     * @return the name of i-th column.
     */
    public String colName(int i) {
        return colNames[i];
    }

    @Override
    public String toString() {
        return toString(false);
    }

    /**
     * Returns the string representation of matrix.
     * @param full Print the full matrix if true. Otherwise,
     *             print only top left 7 x 7 submatrix.
     * @return the string representation of matrix.
     */
    public String toString(boolean full) {
        return full ? toString(nrow(), ncol()) : toString(7, 7);
    }

    /**
     * Returns the string representation of matrix.
     * @param m the number of rows to print.
     * @param n the number of columns to print.
     * @return the string representation of matrix.
     */
    public String toString(int m, int n) {
        StringBuilder sb = new StringBuilder(nrow() + " x " + ncol() + "\n");
        m = Math.min(m, nrow());
        n = Math.min(n, ncol());

        String newline = n < ncol() ? "  ...\n" : "\n";

        if (colNames != null) {
            if (rowNames != null) sb.append("            ");

            for (int j = 0; j < n; j++) {
                sb.append(String.format(" %12.12s", colNames[j]));
            }
            sb.append(newline);
        }

        for (int i = 0; i < m; i++) {
            if (rowNames != null) sb.append(String.format("%-12.12s", rowNames[i]));

            for (int j = 0; j < n; j++) {
                sb.append(String.format(" %12.12s", str(i, j)));
            }
            sb.append(newline);
        }

        if (m < nrow()) {
            sb.append("  ...\n");
        }

        return sb.toString();
    }

    /**
     * Returns the string representation of <code>A[i,j]</code>.
     * @param i the row index.
     * @param j the column index.
     * @return the string representation of <code>A[i,j]</code>.
     */
    private String str(int i, int j) {
        return smile.util.Strings.format(get(i, j), true);
    }

    /**
     * Matrix-vector multiplication.
     * <pre>{@code
     *     y = alpha * op(A) * x + beta * y
     * }</pre>
     * where op is the transpose operation.
     *
     * @param trans normal, transpose, or conjugate transpose
     *              operation on the matrix.
     * @param alpha the scalar alpha.
     * @param x the input vector.
     * @param beta the scalar beta. When beta is supplied as zero
     *             then y need not be set on input.
     * @param y  the input and output vector.
     */
    public abstract void mv(Transpose trans, float alpha, float[] x, float beta, float[] y);

    /**
     * Returns the matrix-vector multiplication {@code A * x}.
     * @param x the vector.
     * @return the matrix-vector multiplication {@code A * x}.
     */
    public float[] mv(float[] x) {
        float[] y = new float[nrow()];
        mv(NO_TRANSPOSE, 1.0f, x, 0.0f, y);
        return y;
    }

    /**
     * Matrix-vector multiplication {@code y = A * x}.
     * @param x the input vector.
     * @param y the output vector.
     */
    public void mv(float[] x, float[] y) {
        mv(NO_TRANSPOSE, 1.0f, x, 0.0f, y);
    }

    /**
     * Matrix-vector multiplication.
     * <pre>{@code
     *     y = alpha * A * x + beta * y
     * }</pre>
     *
     * @param alpha the scalar alpha.
     * @param x the input vector.
     * @param beta the scalar beta. When beta is supplied as zero
     *             then y need not be set on input.
     * @param y  the input and output vector.
     */
    public void mv(float alpha, float[] x, float beta, float[] y) {
        mv(NO_TRANSPOSE, alpha, x, beta, y);
    }

    /**
     * Matrix-vector multiplication {@code A * x}.
     * @param work the workspace for both input and output vector.
     * @param inputOffset the offset of input vector in workspace.
     * @param outputOffset the offset of output vector in workspace.
     */
    public abstract void mv(float[] work, int inputOffset, int outputOffset);

    /**
     * Returns Matrix-vector multiplication {@code A' * x}.
     * @param x the vector.
     * @return the matrix-vector multiplication {@code A' * x}.
     */
    public float[] tv(float[] x) {
        float[] y = new float[ncol()];
        mv(TRANSPOSE, 1.0f, x, 0.0f, y);
        return y;
    }

    /**
     * Matrix-vector multiplication {@code y = A' * x}.
     * @param x the input vector.
     * @param y the output vector.
     */
    public void tv(float[] x, float[] y) {
        mv(TRANSPOSE, 1.0f, x, 0.0f, y);
    }

    /**
     * Matrix-vector multiplication.
     * <pre>{@code
     *     y = alpha * A' * x + beta * y
     * }</pre>
     *
     * @param alpha the scalar alpha.
     * @param x the input vector.
     * @param beta the scalar beta. When beta is supplied as zero
     *             then y need not be set on input.
     * @param y  the input and output vector.
     */
    public void tv(float alpha, float[] x, float beta, float[] y) {
        mv(TRANSPOSE, alpha, x, beta, y);
    }

    /**
     * Matrix-vector multiplication {@code A' * x}.
     * @param work the workspace for both input and output vector.
     * @param inputOffset the offset of input vector in workspace.
     * @param outputOffset the offset of output vector in workspace.
     */
    public abstract void tv(float[] work, int inputOffset, int outputOffset);

    /**
     * Returns the optimal leading dimension. The present process have
     * cascade caches. And read/write cache are 64 byte (multiple of 16
     * for single precision) related on Intel CPUs. In order to avoid
     * cache conflict, we expected the leading dimensions should be
     * multiple of cache line (multiple of 16 for single precision),
     * but not the power of 2, like not multiple of 256, not multiple
     * of 128 etc.
     * <p>
     * To improve performance, ensure that the leading dimensions of
     * the arrays are divisible by 64/element_size, where element_size
     * is the number of bytes for the matrix elements (4 for
     * single-precision real, 8 for double-precision real and
     * single precision complex, and 16 for double-precision complex).
     * <p>
     * But as present processor use cache-cascading structure: set->cache
     * line. In order to avoid the cache stall issue, we suggest to avoid
     * leading dimension are multiples of 128, If ld % 128 = 0, then add
     * 16 to the leading dimension.
     * <p>
     * Generally, set the leading dimension to the following integer expression:
     * (((n * element_size + 511) / 512) * 512 + 64) /element_size,
     * where n is the matrix dimension along the leading dimension.
     */
    static int ld(int n) {
        int elementSize = 4;
        if (n <= 256 / elementSize) return n;

        return (((n * elementSize + 511) / 512) * 512 + 64) / elementSize;
    }

    /** Flips the transpose operation. */
    static Transpose flip(Transpose trans) {
        return trans == NO_TRANSPOSE ? TRANSPOSE : NO_TRANSPOSE;
    }

    /**
     * Sets {@code A[i,j] = x}.
     * @param i the row index.
     * @param j the column index.
     * @param x the matrix cell value.
     */
    public void set(int i, int j, float x) {
        throw new UnsupportedOperationException();
    }

    /**
     * Sets {@code A[i,j] = x} for Scala users.
     * @param i the row index.
     * @param j the column index.
     * @param x the matrix cell value.
     */
    public void update(int i, int j, float x) {
        set(i, j, x);
    }

    /**
     * Returns {@code A[i,j]}.
     * @param i the row index.
     * @param j the column index.
     * @return the matrix cell value.
     */
    public float get(int i, int j) {
        throw new UnsupportedOperationException();
    }

    /**
     * Returns {@code A[i,j]}. For Scala users.
     * @param i the row index.
     * @param j the column index.
     * @return the matrix cell value.
     */
    public float apply(int i, int j) {
        return get(i, j);
    }

    /**
     * Returns the diagonal elements.
     * @return the diagonal elements.
     */
    public float[] diag() {
        int n = Math.min(nrow(), ncol());

        float[] d = new float[n];
        for (int i = 0; i < n; i++) {
            d[i] = get(i, i);
        }

        return d;
    }

    /**
     * Returns the matrix trace. The sum of the diagonal elements.
     * @return the matrix trace.
     */
    public float trace() {
        int n = Math.min(nrow(), ncol());

        float t = 0.0f;
        for (int i = 0; i < n; i++) {
            t += get(i, i);
        }

        return t;
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
    public double eigen(float[] v) {
        return eigen(v, 0.0f, Math.max(1.0E-6f, nrow() * MathEx.FLOAT_EPSILON), Math.max(20, 2 * nrow()));
    }

    /**
     * Returns the largest eigen pair of matrix with the power iteration
     * under the assumptions A has an eigenvalue that is strictly greater
     * in magnitude than its other eigenvalues and the starting
     * vector has a nonzero component in the direction of an eigenvector
     * associated with the dominant eigenvalue.
     * @param v on input, it is the non-zero initial guess of the eigen vector.
     * On output, it is the eigen vector corresponding largest eigen value.
     * @param p the origin in the shifting power method. A - pI will be
     * used in the iteration to accelerate the method. p should be such that
     * |(&lambda;<sub>2</sub> - p) / (&lambda;<sub>1</sub> - p)| &lt; |&lambda;<sub>2</sub> / &lambda;<sub>1</sub>|,
     * where &lambda;<sub>2</sub> is the second largest eigenvalue in magnitude.
     * If we known the eigenvalue spectrum of A, (&lambda;<sub>2</sub> + &lambda;<sub>n</sub>)/2
     * is the optimal choice of p, where &lambda;<sub>n</sub> is the smallest eigenvalue
     * in magnitude. Good estimates of &lambda;<sub>2</sub> are more difficult
     * to compute. However, if &mu; is an approximation to largest eigenvector,
     * then using any x<sub>0</sub> such that x<sub>0</sub>*&mu; = 0 as the initial
     * vector for a few iterations may yield a reasonable estimate of &lambda;<sub>2</sub>.
     * @param tol the desired convergence tolerance.
     * @param maxIter the maximum number of iterations in case that the algorithm
     * does not converge.
     * @return the largest eigen value.
     */
    public double eigen(float[] v, float p, float tol, int maxIter) {
        if (nrow() != ncol()) {
            throw new IllegalArgumentException("Matrix is not square.");
        }

        if (tol <= 0.0) {
            throw new IllegalArgumentException("Invalid tolerance: " + tol);
        }

        if (maxIter <= 0) {
            throw new IllegalArgumentException("Invalid maximum number of iterations: " + maxIter);
        }

        int n = nrow();
        tol = Math.max(tol, MathEx.FLOAT_EPSILON * n);

        float[] z = new float[n];
        double lambda = power(v, z, p);

        for (int iter = 1; iter <= maxIter; iter++) {
            double l = lambda;
            lambda = power(v, z, p);

            double eps = Math.abs(lambda - l);
            if (iter % 10 == 0) {
                logger.trace(String.format("Largest eigenvalue after %3d power iterations: %.4f", iter, lambda + p));
            }

            if (eps < tol) {
                logger.info(String.format("Largest eigenvalue after %3d power iterations: %.4f", iter, lambda + p));
                return lambda + p;
            }
        }

        logger.info(String.format("Largest eigenvalue after %3d power iterations: %.4f", maxIter, lambda + p));
        logger.error("Power iteration exceeded the maximum number of iterations.");
        return lambda + p;
    }

    /**
     * Calculate and normalize y = (A - pI) x.
     * Returns the largest element of y in magnitude.
     */
    private double power(float[] x, float[] y, float p) {
        mv(x, y);

        if (p != 0.0f) {
            for (int i = 0; i < y.length; i++) {
                y[i] -= p * x[i];
            }
        }

        float lambda = y[0];
        for (int i = 1; i < y.length; i++) {
            if (Math.abs(y[i]) > Math.abs(lambda)) {
                lambda = y[i];
            }
        }

        for (int i = 0; i < y.length; i++) {
            x[i] = y[i] / lambda;
        }

        return lambda;
    }

    /**
     * Reads a matrix from a Matrix Market File Format file.
     * For details, see
     * <a href="http://people.sc.fsu.edu/~jburkardt/data/mm/mm.html">http://people.sc.fsu.edu/~jburkardt/data/mm/mm.html</a>.
     *
     * The returned matrix may be dense or sparse.
     *
     * @param path the input file path.
     * @throws IOException when fails to read the file.
     * @throws ParseException  when fails to parse the file.
     * @return a dense or sparse matrix.
     */
    public static IMatrix market(Path path) throws IOException, ParseException {
        try (LineNumberReader reader = new LineNumberReader(Files.newBufferedReader(path));
             Scanner scanner = new Scanner(reader)) {

            // The header line has the form
            // %%MatrixMarket object format field symmetry
            String header = scanner.next();
            if (!header.equals("%%MatrixMarket")) {
                throw new ParseException("Invalid Matrix Market file header", reader.getLineNumber());
            }

            String object = scanner.next();
            if (!object.equals("matrix")) {
                throw new UnsupportedOperationException("The object is not a matrix file: " + object);
            }

            String format = scanner.next();
            String field = scanner.next();
            if (field.equals("complex") || field.equals("pattern")) {
                throw new UnsupportedOperationException("No support of complex or pattern matrix");
            }

            String symmetry = scanner.nextLine().trim();
            if (symmetry.equals("Hermitian")) {
                throw new UnsupportedOperationException("No support of Hermitian matrix");
            }

            boolean symmetric = symmetry.equals("symmetric");
            boolean skew = symmetry.equals("skew-symmetric");

            // Ignore comment lines
            String line = scanner.nextLine();
            while (line.startsWith("%")) {
                line = scanner.nextLine();
            }

            if (format.equals("array")) {
                // Size line
                Scanner s = new Scanner(line);
                int nrow = s.nextInt();
                int ncol = s.nextInt();

                Matrix matrix = new Matrix(nrow, ncol);
                for (int j = 0; j < ncol; j++) {
                    for (int i = 0; i < nrow; i++) {
                        float x = scanner.nextFloat();
                        matrix.set(i, j, x);
                    }
                }

                if (symmetric) {
                    matrix.uplo(LOWER);
                }

                return matrix;
            }

            if (format.equals("coordinate")) {
                // Size line
                Scanner s = new Scanner(line);
                int nrow = s.nextInt();
                int ncol = s.nextInt();
                int nz = s.nextInt();

                if (symmetric && nz == nrow * (nrow + 1) / 2) {
                    if (nrow != ncol) {
                        throw new IllegalStateException(String.format("Symmetric matrix is not square: %d != %d", nrow, ncol));
                    }

                    SymmMatrix matrix = new SymmMatrix(LOWER, nrow);
                    for (int k = 0; k < nz; k++) {
                        String[] tokens = scanner.nextLine().trim().split("\\s+");
                        if (tokens.length != 3) {
                            throw new ParseException("Invalid data line: " + line, reader.getLineNumber());
                        }

                        int i = Integer.parseInt(tokens[0]) - 1;
                        int j = Integer.parseInt(tokens[1]) - 1;
                        float x = Float.parseFloat(tokens[2]);

                        matrix.set(i, j, x);
                    }

                    return matrix;
                } else if (skew && nz == nrow * (nrow + 1) / 2) {
                    if (nrow != ncol) {
                        throw new IllegalStateException(String.format("Skew-symmetric matrix is not square: %d != %d", nrow, ncol));
                    }

                    Matrix matrix = new Matrix(nrow, ncol);
                    for (int k = 0; k < nz; k++) {
                        String[] tokens = scanner.nextLine().trim().split("\\s+");
                        if (tokens.length != 3) {
                            throw new ParseException("Invalid data line: " + line, reader.getLineNumber());
                        }

                        int i = Integer.parseInt(tokens[0]) - 1;
                        int j = Integer.parseInt(tokens[1]) - 1;
                        float x = Float.parseFloat(tokens[2]);

                        matrix.set(i, j, x);
                        matrix.set(j, i, -x);
                    }

                    return matrix;
                }

                // General sparse matrix
                int[] colSize = new int[ncol];
                List<SparseArray> rows = new ArrayList<>();
                for (int i = 0; i < nrow; i++) {
                    rows.add(new SparseArray());
                }

                for (int k = 0; k < nz; k++) {
                    String[] tokens = scanner.nextLine().trim().split("\\s+");
                    if (tokens.length != 3) {
                        throw new ParseException("Invalid data line: " + line, reader.getLineNumber());
                    }

                    int i = Integer.parseInt(tokens[0]) - 1;
                    int j = Integer.parseInt(tokens[1]) - 1;
                    double x = Double.parseDouble(tokens[2]);

                    SparseArray row = rows.get(i);
                    row.set(j, x);
                    colSize[j] += 1;

                    if (symmetric) {
                        row = rows.get(j);
                        row.set(i, x);
                        colSize[i] += 1;
                    } else if (skew) {
                        row = rows.get(j);
                        row.set(i, -x);
                        colSize[i] += 1;
                    }
                }

                int[] pos = new int[ncol];
                int[] colIndex = new int[ncol + 1];
                for (int i = 0; i < ncol; i++) {
                    colIndex[i + 1] = colIndex[i] + colSize[i];
                }

                if (symmetric || skew) {
                    nz *= 2;
                }
                int[] rowIndex = new int[nz];
                float[] x = new float[nz];

                for (int i = 0; i < nrow; i++) {
                    for (SparseArray.Entry e :rows.get(i)) {
                        int j = e.i;
                        int k = colIndex[j] + pos[j];

                        rowIndex[k] = i;
                        x[k] = (float) e.x;
                        pos[j]++;
                    }
                }

                return new SparseMatrix(nrow, ncol, x, rowIndex, colIndex);

            }

            throw new ParseException("Invalid Matrix Market format: " + format, 0);
        }
    }

    /**
     * The square matrix of A' * A or A * A', whichever is smaller.
     * For SVD, we compute eigenvalue decomposition of A' * A
     * when m >= n, or that of A * A' when m < n.
     */
    static class Square extends IMatrix {
        /**
         * The base matrix.
         */
        private final IMatrix A;
        /**
         * The larger dimension of A.
         */
        private final int m;
        /**
         * The smaller dimension of A.
         */
        private final int n;
        /**
         * Workspace for A * x
         */
        private final float[] Ax;

        /**
         * Constructor.
         * @param A the base matrix.
         */
        public Square(IMatrix A) {
            this.A = A;
            this.m = Math.max(A.nrow(), A.ncol());
            this.n = Math.min(A.nrow(), A.ncol());
            this.Ax = new float[m + n];
        }

        @Override
        public int nrow() {
            return n;
        }

        @Override
        public int ncol() {
            return n;
        }

        @Override
        public long size() {
            return A.size();
        }

        @Override
        public void mv(Transpose trans, float alpha, float[] x, float beta, float[] y) {
            if (A.nrow() >= A.ncol()) {
                A.mv(x, Ax);
                A.tv(alpha, Ax, beta, y);
            } else {
                A.tv(x, Ax);
                A.mv(alpha, Ax, beta, y);
            }
        }

        @Override
        public void mv(float[] work, int inputOffset, int outputOffset) {
            System.arraycopy(work, inputOffset, Ax, 0, n);

            if (A.nrow() >= A.ncol()) {
                A.mv(Ax, 0, n);
                A.tv(Ax, n, 0);
            } else {
                A.tv(Ax, 0, n);
                A.mv(Ax, n, 0);
            }

            System.arraycopy(Ax, 0, work, outputOffset, n);
        }

        @Override
        public void tv(float[] work, int inputOffset, int outputOffset) {
            // The square matrix (AA' or A'A) is symmetric.
            mv(work, inputOffset, outputOffset);
        }
    }

    /**
     * Returns the matrix of A' * A or A * A', whichever is smaller.
     * For SVD, we compute eigenvalue decomposition of A' * A
     * when m >= n, or that of A * A' when m < n.
     */
    public IMatrix square() {
        return new IMatrix.Square(this);
    }
    /**
     * The preconditioner matrix. A preconditioner P of a matrix A is a matrix
     * such that P<sup>-1</sup>A has a smaller condition number than A.
     * Preconditioners are useful in iterative methods to solve a linear
     * system A * x = b since the rate of convergence for most iterative
     * linear solvers increases because the condition number of a matrix
     * decreases as a result of preconditioning. Preconditioned iterative
     * solvers typically outperform direct solvers for large, especially
     * for sparse, matrices.
     * <p>
     * The preconditioner matrix P is close to A and should be easy to
     * solve for linear systems. The preconditioner matrix could be as
     * simple as the trivial diagonal part of A in some cases.
     */
    public interface Preconditioner {
        /**
         * Solve P * x = b for the preconditioner matrix P.
         *
         * @param b the right hand side of linear system.
         * @param x the output solution vector.
         */
        void asolve(float[] b, float[] x);
    }

    /**
     * Returns a simple Jacobi preconditioner matrix that is the
     * trivial diagonal part of A in some cases.
     * @return the preconditioner matrix.
     */
    public Preconditioner Jacobi() {
        float[] diag = diag();
        return (b, x) -> {
            int n = diag.length;

            for (int i = 0; i < n; i++) {
                x[i] = diag[i] != 0.0 ? b[i] / diag[i] : b[i];
            }
        };
    }

    /**
     * Solves A * x = b by iterative biconjugate gradient method with Jacobi
     * preconditioner matrix.
     *
     * @param b the right hand side of linear equations.
     * @param x on input, x should be set to an initial guess of the solution
     * (or all zeros). On output, x is reset to the improved solution.
     * @return the estimated error.
     */
    public double solve(float[] b, float[] x) {
        return solve(b, x, Jacobi(), 1E-6f, 1, 2 * Math.max(nrow(), ncol()));
    }

    /**
     * Solves A * x = b by iterative biconjugate gradient method.
     *
     * @param b the right hand side of linear equations.
     * @param x on input, x should be set to an initial guess of the solution
     * (or all zeros). On output, x is reset to the improved solution.
     * @param P The preconditioner matrix.
     * @param tol The desired convergence tolerance.
     * @param itol Which convergence test is applied.
     *             If itol = 1, iteration stops when |Ax - b| / |b| is less
     *             than the parameter tolerance.
     *             If itol = 2, the stop criterion is that |A<sup>-1</sup> (Ax - b)| / |A<sup>-1</sup>b|
     *             is less than tolerance.
     *             If tol = 3, |x<sub>k+1</sub> - x<sub>k</sub>|<sub>2</sub> is less than
     *             tolerance.
     *             The setting of tol = 4 is same as tol = 3 except that the
     *             L<sub>&infin;</sub> norm instead of L<sub>2</sub>.
     * @param maxIter The maximum number of iterations.
     * @return the estimated error.
     */
    public double solve(float[] b, float[] x, Preconditioner P, float tol, int itol, int maxIter) {
        if (tol <= 0.0) {
            throw new IllegalArgumentException("Invalid tolerance: " + tol);
        }

        if (itol < 1 || itol > 4) {
            throw new IllegalArgumentException("Invalid itol: " + itol);
        }

        if (maxIter <= 0) {
            throw new IllegalArgumentException("Invalid maximum iterations: " + maxIter);
        }

        float err = 0.0f;
        float ak, akden, bk, bkden = 1.0f, bknum, bnrm, dxnrm, xnrm, zm1nrm, znrm = 0.0f;
        int j, n = b.length;

        float[] p = new float[n];
        float[] pp = new float[n];
        float[] r = new float[n];
        float[] rr = new float[n];
        float[] z = new float[n];
        float[] zz = new float[n];

        mv(x, r);
        for (j = 0; j < n; j++) {
            r[j] = b[j] - r[j];
            rr[j] = r[j];
        }

        if (itol == 1) {
            bnrm = norm(b, itol);
            P.asolve(r, z);
        } else if (itol == 2) {
            P.asolve(b, z);
            bnrm = norm(z, itol);
            P.asolve(r, z);
        } else if (itol == 3 || itol == 4) {
            P.asolve(b, z);
            bnrm = norm(z, itol);
            P.asolve(r, z);
            znrm = norm(z, itol);
        } else {
            throw new IllegalArgumentException(String.format("Illegal itol: %d", itol));
        }

        for (int iter = 1; iter <= maxIter; iter++) {
            P.asolve(rr, zz);
            for (bknum = 0.0f, j = 0; j < n; j++) {
                bknum += z[j] * rr[j];
            }
            if (iter == 1) {
                for (j = 0; j < n; j++) {
                    p[j] = z[j];
                    pp[j] = zz[j];
                }
            } else {
                bk = bknum / bkden;
                for (j = 0; j < n; j++) {
                    p[j] = bk * p[j] + z[j];
                    pp[j] = bk * pp[j] + zz[j];
                }
            }
            bkden = bknum;
            mv(p, z);
            for (akden = 0.0f, j = 0; j < n; j++) {
                akden += z[j] * pp[j];
            }
            ak = bknum / akden;
            tv(pp, zz);
            for (j = 0; j < n; j++) {
                x[j] += ak * p[j];
                r[j] -= ak * z[j];
                rr[j] -= ak * zz[j];
            }
            P.asolve(r, z);
            if (itol == 1) {
                err = norm(r, itol) / bnrm;
            } else if (itol == 2) {
                err = norm(z, itol) / bnrm;
            } else if (itol == 3 || itol == 4) {
                zm1nrm = znrm;
                znrm = norm(z, itol);
                if (Math.abs(zm1nrm - znrm) > MathEx.EPSILON * znrm) {
                    dxnrm = Math.abs(ak) * norm(p, itol);
                    err = znrm / Math.abs(zm1nrm - znrm) * dxnrm;
                } else {
                    err = znrm / bnrm;
                    continue;
                }
                xnrm = norm(x, itol);
                if (err <= 0.5 * xnrm) {
                    err /= xnrm;
                } else {
                    err = znrm / bnrm;
                    continue;
                }
            }

            if (iter % 10 == 0) {
                logger.info(String.format("BCG: the error after %3d iterations: %.5g", iter, err));
            }

            if (err <= tol) {
                logger.info(String.format("BCG: the error after %3d iterations: %.5g", iter, err));
                break;
            }
        }

        return err;
    }

    /**
     * Computes L2 or L-infinity norms for a vector x, as signaled by itol.
     */
    private static float norm(float[] x, int itol) {
        int n = x.length;

        if (itol <= 3) {
            float ans = 0.0f;
            for (double v : x) {
                ans += v * v;
            }
            return (float) Math.sqrt(ans);
        } else {
            int isamax = 0;
            for (int i = 0; i < n; i++) {
                if (Math.abs(x[i]) > Math.abs(x[isamax])) {
                    isamax = i;
                }
            }

            return Math.abs(x[isamax]);
        }
    }
}
