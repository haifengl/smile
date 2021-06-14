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

import smile.math.blas.Transpose;
import smile.util.SparseArray;
import static smile.math.blas.Transpose.*;
import static smile.math.blas.UPLO.LOWER;

/**
 * Single precision matrix base class.
 *
 * @author Haifeng Li
 */
public abstract class IMatrix implements Cloneable, Serializable {
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
     * Returns the string representation of <code>A[i, j]</code>.
     * @param i the row index.
     * @param j the column index.
     * @return the string representation of <code>A[i, j]</code>.
     */
    String str(int i, int j) {
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
     * @return this matrix.
     */
    public abstract void set(int i, int j, float x);

    /**
     * Sets {@code A[i,j] = x} for Scala users.
     * @param i the row index.
     * @param j the column index.
     * @param x the matrix cell value.
     * @return this matrix.
     */
    public void update(int i, int j, float x) {
        set(i, j, x);
    }

    /**
     * Returns {@code A[i, j]}.
     * @param i the row index.
     * @param j the column index.
     * @return the matrix cell value.
     */
    public abstract float get(int i, int j);

    /**
     * Returns {@code A[i, j]}. For Scala users.
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
     * Returns the matrix of A' * A or A * A', whichever is smaller.
     * For SVD, we compute eigenvalue decomposition of A' * A
     * when m >= n, or that of A * A' when m < n.
     */
    IMatrix square() {
        IMatrix A = this;

        return new IMatrix() {
            /**
             * The larger dimension of A.
             */
            private final int m = Math.max(A.nrow(), A.ncol());
            /**
             * The smaller dimension of A.
             */
            private final int n = Math.min(A.nrow(), A.ncol());
            /**
             * Workspace for A * x
             */
            private final float[] Ax = new float[m + n];

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
                return m + n;
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
                throw new UnsupportedOperationException();
            }

            @Override
            public void mv(Transpose trans, float alpha, float[] x, float beta, float[] y) {
                throw new UnsupportedOperationException();
            }

            @Override
            public float get(int i, int j) {
                throw new UnsupportedOperationException();
            }

            @Override
            public void set(int i, int j, float x) {
                throw new UnsupportedOperationException();
            }
        };
    }
}
