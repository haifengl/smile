/*
 * Copyright (c) 2010-2025 Haifeng Li. All rights reserved.
 *
 * Smile is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Smile is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Smile. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.tensor;

import java.io.IOException;
import java.io.LineNumberReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import smile.linalg.Transpose;
import smile.util.SparseArray;
import static smile.linalg.Transpose.*;
import static smile.linalg.UPLO.*;
import static smile.tensor.ScalarType.*;

/**
 * Mathematical matrix interface. The most important methods are matrix-matrix
 * multiplication, and matrix-vector multiplication. Matrix-matrix
 * multiplication is the core operation in deep learning. Matrix-vector
 * multiplication is the only operation needed in many iterative matrix
 * algorithms, e.g. biconjugate gradient method for solving linear equations
 * and power iteration and Lanczos algorithm for eigen decomposition, which
 * are usually very efficient for very large and sparse matrices.
 * <p>
 * A matrix is a rectangular array of numbers. An item in a matrix is called
 * an entry or an element. Entries are often denoted by a variable with two
 * subscripts. Matrices of the same size can be added and subtracted entry-wise
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
 * and D is a diagonal matrix. The eigen-decomposition or diagonalization
 * expresses A as a product VDV<sup>-1</sup>, where D is a diagonal matrix and
 * V is a suitable invertible matrix. If A can be written in this form, it is
 * called diagonalizable.
 *
 * @author Haifeng Li
 */
public interface Matrix extends Tensor {
    /**
     * Returns the number of rows.
     * @return the number of rows.
     */
    int nrow();

    /**
     * Returns the number of columns.
     * @return the number of columns.
     */
    int ncol();

    @Override
    default int dim() {
        return 2;
    }

    @Override
    default int size(int dim) {
        return switch (dim) {
            case 0 -> nrow();
            case 1 -> ncol();
            default -> throw new IllegalArgumentException("Invalid dim: " + dim);
        };
    }

    @Override
    default long length() {
        return (long) nrow() * ncol();
    }

    @Override
    default int[] shape() {
        return new int[]{nrow(), ncol()};
    }

    @Override
    default Tensor reshape(int... shape) {
        throw new UnsupportedOperationException();
    }

    @Override
    default Tensor set(Tensor value, int... index) {
        throw new UnsupportedOperationException();
    }

    @Override
    default Tensor get(int... index) {
        throw new UnsupportedOperationException();
    }

    /**
     * Returns the string representation of matrix.
     * @param full Print the full matrix if true. Otherwise,
     *             print only top left 7 x 7 submatrix.
     * @return the string representation of matrix.
     */
    default String toString(boolean full) {
        return full ? toString(nrow(), ncol()) : toString(7, 7);
    }

    /**
     * Returns the string representation of matrix.
     * @param m the number of rows to print.
     * @param n the number of columns to print.
     * @return the string representation of matrix.
     */
    default String toString(int m, int n) {
        m = Math.min(m, nrow());
        n = Math.min(n, ncol());
        StringBuilder sb = new StringBuilder(nrow() + " x " + ncol() + "\n");

        String newline = n < ncol() ? "  ...\n" : "\n";
        for (int i = 0; i < m; i++) {
            for (int j = 0; j < n; j++) {
                sb.append(String.format(" %12.12s", AbstractTensor.format(get(i, j))));
            }
            sb.append(newline);
        }

        if (m < nrow()) {
            sb.append("  ...\n");
        }

        return sb.toString();
    }

    /**
     * Creates a column vector of same scalar type.
     * @param size the vector size.
     * @return the vector.
     */
    default Vector vector(int size) {
        return switch (scalarType()) {
            case Float32 -> Vector.column(new float[size]);
            case Float64 -> Vector.column(new double[size]);
            default -> throw new UnsupportedOperationException("Unsupported scalar type: " + scalarType());
        };
    }

    /**
     * Creates a column vector of same scalar type.
     * @param x the initial vector values.
     * @return the vector.
     */
    default Vector vector(double[] x) {
        return switch (scalarType()) {
            case Float64 -> Vector.column(x);
            case Float32 -> {
                Vector v = Vector.column(new float[x.length]);
                for (int i = 0; i < x.length; i++) v.set(i, x[i]);
                yield v;
            }
            default -> throw new UnsupportedOperationException("Unsupported ScalarType: " + scalarType());
        };
    }

    /**
     * Creates a column vector of same scalar type.
     * @param x the initial vector values.
     * @return the vector.
     */
    default Vector vector(float[] x) {
        return switch (scalarType()) {
            case Float32 -> Vector.column(x);
            case Float64 -> {
                Vector v = Vector.column(new double[x.length]);
                for (int i = 0; i < x.length; i++) v.set(i, x[i]);
                yield v;
            }
            default -> throw new UnsupportedOperationException("Unsupported ScalarType: " + scalarType());
        };
    }

    /**
     * Returns {@code A[i,j]}.
     * @param i the row index.
     * @param j the column index.
     * @return the matrix element value.
     */
    double get(int i, int j);

    /**
     * Returns {@code A[i,j]} for Scala users.
     * @param i the row index.
     * @param j the column index.
     * @return the matrix element value.
     */
    default double apply(int i, int j) {
        return get(i, j);
    }

    /**
     * Sets {@code A[i,j] = x}.
     * @param i the row index.
     * @param j the column index.
     * @param x the matrix element value.
     */
    void set(int i, int j, double x);

    /**
     * Sets {@code A[i,j] = x} for Scala users.
     * @param i the row index.
     * @param j the column index.
     * @param x the matrix element value.
     */
    default void update(int i, int j, double x) {
        set(i, j, x);
    }

    /**
     * Sets {@code A[i,j] += x}.
     * @param i the row index.
     * @param j the column index.
     * @param x the operand.
     */
    void add(int i, int j, double x);

    /**
     * Sets {@code A[i,j] -= x}.
     * @param i the row index.
     * @param j the column index.
     * @param x the operand.
     */
    void sub(int i, int j, double x);

    /**
     * Sets {@code A[i,j] *= x}.
     * @param i the row index.
     * @param j the column index.
     * @param x the operand.
     */
    void mul(int i, int j, double x);

    /**
     * Sets {@code A[i,j] /= x}.
     * @param i the row index.
     * @param j the column index.
     * @param x the operand.
     */
    void div(int i, int j, double x);

    /**
     * A *= alpha
     * @param alpha the scaling factor.
     * @return this matrix.
     */
    Matrix scale(double alpha);

    /**
     * Returns the diagonal elements.
     * @return the diagonal elements.
     */
    default Vector diagonal() {
        int n = Math.min(nrow(), ncol());

        return switch (scalarType()) {
            case Float64 -> {
                double[] diag = new double[n];
                for (int i = 0; i < n; i++) {
                    diag[i] = get(i, i);
                }

                yield Vector.column(diag);
            }

            case Float32 -> {
                float[] diag = new float[n];
                for (int i = 0; i < n; i++) {
                    diag[i] = (float) get(i, i);
                }

                yield Vector.column(diag);
            }

            default -> throw new UnsupportedOperationException("Unsupported scalar type: " + scalarType());
        };
    }

    /**
     * Returns the matrix trace. The sum of the diagonal elements.
     * @return the matrix trace.
     */
    default double trace() {
        int n = Math.min(nrow(), ncol());

        double sum = 0.0;
        for (int i = 0; i < n; i++) {
            sum += get(i, i);
        }

        return sum;
    }

    /**
     * Returns a two-dimensional array containing all the elements in this matrix.
     * @param a the array into which the elements of the matrix are to be stored
     *          if it is big enough; otherwise, a new array is allocated.
     * @return an array containing the elements of the vector.
     */
    default double[][] toArray(double[][] a) {
        int m = nrow();
        int n = ncol();
        if (a.length < m || a[0].length < n) {
            a = new double[m][n];
        }

        for (int i = 0; i < m; i++) {
            for (int j = 0; j < n; j++) {
                a[i][j] = get(i, j);
            }
        }
        return a;
    }

    /**
     * Returns a two-dimensional array containing all the elements in this matrix.
     * @param a the array into which the elements of the matrix are to be stored
     *          if it is big enough; otherwise, a new array is allocated.
     * @return an array containing the elements of the vector.
     */
    default float[][] toArray(float[][] a) {
        int m = nrow();
        int n = ncol();
        if (a.length < m || a[0].length < n) {
            a = new float[m][n];
        }

        for (int i = 0; i < m; i++) {
            for (int j = 0; j < n; j++) {
                a[i][j] = (float) get(i, j);
            }
        }
        return a;
    }

    /**
     * Returns a deep copy of matrix.
     * @return a deep copy of matrix.
     */
    Matrix copy();

    /**
     * Returns the transpose of matrix. The transpose may share the storage
     * with this matrix.
     * @return the transpose of matrix.
     */
    Matrix transpose();

    /**
     * Matrix-vector multiplication.
     * <pre>{@code
     *     y = alpha * A * x + beta * y
     * }</pre>
     *
     * @param trans normal, transpose, or conjugate transpose operation on the matrix.
     * @param alpha the scalar alpha.
     * @param x the input vector.
     * @param beta the scalar beta. When beta is supplied as zero,
     *             y need not be set on input.
     * @param y  the input and output vector.
     */
    void mv(Transpose trans, double alpha, Vector x, double beta, Vector y);

    /**
     * Matrix-vector multiplication {@code A * x}.
     * @param x the vector.
     * @return the matrix-vector multiplication {@code A * x}.
     */
    default Vector mv(Vector x) {
        if (ncol() > x.size()) {
            throw new IllegalArgumentException(String.format("Dimensions do not match for matrix-vector multiplication A * x: %d x %d vs %d x 1", nrow(), ncol(), x.size()));
        }

        var y = vector(nrow());
        mv(NO_TRANSPOSE, 1.0, x, 0.0, y);
        return y;
    }

    /**
     * Matrix-vector multiplication {@code A * x}.
     *
     * @param x the input vector.
     * @param y  the input and output vector.
     */
    default void mv(Vector x, Vector y) {
        if (ncol() > x.size()) {
            throw new IllegalArgumentException(String.format("Dimensions do not match for matrix-vector multiplication A * x: %d x %d vs %d x 1", nrow(), ncol(), x.size()));
        }

        if (nrow() > y.size()) {
            throw new IllegalArgumentException(String.format("Dimensions do not match for matrix-vector multiplication y = A * x: %d x %d vs %d x 1", nrow(), ncol(), y.size()));
        }

        mv(NO_TRANSPOSE, 1.0, x, 0.0, y);
    }

    /**
     * Matrix-vector multiplication {@code A * x}.
     * @param work the workspace for both input and output vector.
     * @param inputOffset the offset of input vector in workspace.
     * @param outputOffset the offset of output vector in workspace.
     */
    default void mv(Vector work, int inputOffset, int outputOffset) {
        Vector xb = work.slice(inputOffset, inputOffset + ncol());
        Vector yb = work.slice(outputOffset, outputOffset + nrow());
        mv(NO_TRANSPOSE, 1.0, xb, 0.0, yb);
    }

    /**
     * Matrix-vector multiplication {@code A' * x}.
     * @param x the vector.
     * @return the matrix-vector multiplication {@code A' * x}.
     */
    default Vector tv(Vector x) {
        if (nrow() > x.size()) {
            throw new IllegalArgumentException(String.format("Dimensions do not match for matrix-vector multiplication A' * x: %d x %d vs %d x 1", nrow(), ncol(), x.size()));
        }

        var y = vector(ncol());
        mv(TRANSPOSE, 1.0, x, 0.0, y);
        return y;
    }

    /**
     * Matrix-vector multiplication {@code A' * x}.
     *
     * @param x the input vector.
     * @param y  the input and output vector.
     */
    default void tv(Vector x, Vector y) {
        if (nrow() > x.size()) {
            throw new IllegalArgumentException(String.format("Dimensions do not match for matrix-vector multiplication A' * x: %d x %d vs %d x 1", nrow(), ncol(), x.size()));
        }

        if (ncol() > y.size()) {
            throw new IllegalArgumentException(String.format("Dimensions do not match for matrix-vector multiplication y = A' * x: %d x %d vs %d x 1", nrow(), ncol(), y.size()));
        }

        mv(TRANSPOSE, 1.0, x, 0.0, y);
    }

    /**
     * Matrix-vector multiplication {@code A' * x}.
     * @param work the workspace for both input and output vector.
     * @param inputOffset the offset of input vector in workspace.
     * @param outputOffset the offset of output vector in workspace.
     */
    default void tv(Vector work, int inputOffset, int outputOffset) {
        Vector xb = work.slice(inputOffset, inputOffset + nrow());
        Vector yb = work.slice(outputOffset, outputOffset + ncol());
        mv(TRANSPOSE, 1.0, xb, 0.0, yb);
    }

    /**
     * Matrix-vector multiplication {@code A * x}.
     * @param x the vector.
     * @return the matrix-vector multiplication {@code A * x}.
     */
    default Vector mv(double[] x) {
        return mv(vector(x));
    }

    /**
     * Matrix-vector multiplication {@code A * x}.
     * @param x the vector.
     * @return the matrix-vector multiplication {@code A * x}.
     */
    default Vector mv(float[] x) {
        return mv(vector(x));
    }

    /**
     * Matrix-vector multiplication {@code A * x}.
     * @param x the vector.
     * @return the matrix-vector multiplication {@code A * x}.
     */
    default Vector tv(double[] x) {
        return tv(vector(x));
    }

    /**
     * Matrix-vector multiplication {@code A * x}.
     * @param x the vector.
     * @return the matrix-vector multiplication {@code A * x}.
     */
    default Vector tv(float[] x) {
        return tv(vector(x));
    }

    /**
     * Returns the quadratic form {@code x' * A * x}.
     * The left upper submatrix of A is used in the computation based
     * on the size of x.
     * @param x the vector.
     * @return the quadratic form.
     */
    default double xAx(Vector x) {
        if (nrow() != ncol()) {
            throw new IllegalArgumentException(String.format("The matrix is not square: %d x %d", nrow(), ncol()));
        }

        if (ncol() != x.size()) {
            throw new IllegalArgumentException(String.format("Matrix: %d x %d, Vector: %d", nrow(), ncol(), x.size()));
        }

        Vector Ax = mv(x);
        return x.dot(Ax);
    }

    /**
     * Reads a matrix from a Matrix Market File Format file.
     * For details, see
     * <a href="http://people.sc.fsu.edu/~jburkardt/data/mm/mm.html">http://people.sc.fsu.edu/~jburkardt/data/mm/mm.html</a>.
     * <p>
     * The returned matrix may be dense or sparse.
     *
     * @param path the input file path.
     * @throws IOException when fails to read the file.
     * @throws ParseException  when fails to parse the file.
     * @return a dense or sparse matrix.
     */
    static Matrix market(Path path) throws IOException, ParseException {
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

                DenseMatrix matrix = DenseMatrix.zeros(Float64, nrow, ncol);
                for (int j = 0; j < ncol; j++) {
                    for (int i = 0; i < nrow; i++) {
                        double x = scanner.nextDouble();
                        matrix.set(i, j, x);
                    }
                }

                if (symmetric) {
                    matrix.withUplo(LOWER);
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

                    SymmMatrix matrix = SymmMatrix.zeros(Float64, LOWER, nrow);
                    for (int k = 0; k < nz; k++) {
                        String[] tokens = scanner.nextLine().trim().split("\\s+");
                        if (tokens.length != 3) {
                            throw new ParseException("Invalid data line: " + line, reader.getLineNumber());
                        }

                        int i = Integer.parseInt(tokens[0]) - 1;
                        int j = Integer.parseInt(tokens[1]) - 1;
                        double x = Double.parseDouble(tokens[2]);

                        matrix.set(i, j, x);
                    }

                    return matrix;
                } else if (skew && nz == nrow * (nrow + 1) / 2) {
                    if (nrow != ncol) {
                        throw new IllegalStateException(String.format("Skew-symmetric matrix is not square: %d != %d", nrow, ncol));
                    }

                    DenseMatrix matrix = DenseMatrix.zeros(Float64, nrow, ncol);
                    for (int k = 0; k < nz; k++) {
                        String[] tokens = scanner.nextLine().trim().split("\\s+");
                        if (tokens.length != 3) {
                            throw new ParseException("Invalid data line: " + line, reader.getLineNumber());
                        }

                        int i = Integer.parseInt(tokens[0]) - 1;
                        int j = Integer.parseInt(tokens[1]) - 1;
                        double x = Double.parseDouble(tokens[2]);

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
                double[] x = new double[nz];

                for (int i = 0; i < nrow; i++) {
                    for (SparseArray.Entry e : rows.get(i)) {
                        int j = e.index();
                        int k = colIndex[j] + pos[j];

                        rowIndex[k] = i;
                        x[k] = e.value();
                        pos[j]++;
                    }
                }

                return new SparseMatrix(nrow, ncol, x, rowIndex, colIndex);

            }

            throw new ParseException("Invalid Matrix Market format: " + format, 0);
        }
    }
}
