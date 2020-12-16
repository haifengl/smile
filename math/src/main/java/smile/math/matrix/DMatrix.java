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

package smile.math.matrix;

import java.io.IOException;
import java.io.LineNumberReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

import smile.math.blas.Transpose;
import smile.util.SparseArray;
import static smile.math.blas.Transpose.*;
import static smile.math.blas.UPLO.*;

/**
 * Double precision matrix base class.
 *
 * @author Haifeng Li
 */
public abstract class DMatrix extends IMatrix<double[]> {
    /**
     * Sets {@code A[i, j] = x}.
     * @param i the row index.
     * @param j the column index.
     * @param x the matrix cell value.
     * @return this matrix.
     */
    public abstract DMatrix set(int i, int j, double x);

    /**
     * Sets {@code A[i, j] = x} for Scala users.
     * @param i the row index.
     * @param j the column index.
     * @param x the matrix cell value.
     * @return this matrix.
     */
    public DMatrix update(int i, int j, double x) {
        return set(i, j, x);
    }

    /**
     * Returns {@code A[i, j]}.
     * @param i the row index.
     * @param j the column index.
     * @return the matrix cell value.
     */
    public abstract double get(int i, int j);

    /**
     * Returns {@code A[i, j]} for Scala users.
     * @param i the row index.
     * @param j the column index.
     * @return the matrix cell value.
     */
    public double apply(int i, int j) {
        return get(i, j);
    }

    @Override
    String str(int i, int j) {
        return smile.util.Strings.format(get(i, j), true);
    }

    /**
     * Returns the diagonal elements.
     * @return the diagonal elements.
     */
    public double[] diag() {
        int n = Math.min(nrow(), ncol());

        double[] d = new double[n];
        for (int i = 0; i < n; i++) {
            d[i] = get(i, i);
        }

        return d;
    }

    /**
     * Returns the matrix trace. The sum of the diagonal elements.
     * @return the matrix trace.
     */
    public double trace() {
        int n = Math.min(nrow(), ncol());

        double t = 0.0;
        for (int i = 0; i < n; i++) {
            t += get(i, i);
        }

        return t;
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
    public abstract void mv(Transpose trans, double alpha, double[] x, double beta, double[] y);

    @Override
    public double[] mv(double[] x) {
        double[] y = new double[nrow()];
        mv(NO_TRANSPOSE, 1.0, x, 0.0, y);
        return y;
    }

    @Override
    public void mv(double[] x, double[] y) {
        mv(NO_TRANSPOSE, 1.0, x, 0.0, y);
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
    public void mv(double alpha, double[] x, double beta, double[] y) {
        mv(NO_TRANSPOSE, alpha, x, beta, y);
    }

    @Override
    public double[] tv(double[] x) {
        double[] y = new double[ncol()];
        mv(TRANSPOSE, 1.0, x, 0.0, y);
        return y;
    }

    @Override
    public void tv(double[] x, double[] y) {
        mv(TRANSPOSE, 1.0, x, 0.0, y);
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
    public void tv(double alpha, double[] x, double beta, double[] y) {
        mv(TRANSPOSE, alpha, x, beta, y);
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
    public static DMatrix market(Path path) throws IOException, ParseException {
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
                        double x = scanner.nextDouble();
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
                        double x = Double.parseDouble(tokens[2]);

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
                    for (SparseArray.Entry e :rows.get(i)) {
                        int j = e.i;
                        int k = colIndex[j] + pos[j];

                        rowIndex[k] = i;
                        x[k] = e.x;
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
    DMatrix square() {
        DMatrix A = this;

        return new DMatrix() {
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
            private final double[] Ax = new double[m + n];

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
            public void mv(double[] work, int inputOffset, int outputOffset) {
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
            public void tv(double[] work, int inputOffset, int outputOffset) {
                throw new UnsupportedOperationException();
            }

            @Override
            public void mv(Transpose trans, double alpha, double[] x, double beta, double[] y) {
                throw new UnsupportedOperationException();
            }

            @Override
            public double get(int i, int j) {
                throw new UnsupportedOperationException();
            }

            @Override
            public DMatrix set(int i, int j, double x) {
                throw new UnsupportedOperationException();
            }
        };
    }
}
