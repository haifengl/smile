/*******************************************************************************
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
 ******************************************************************************/

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
import static smile.math.blas.UPLO.LOWER;

/**
 * Single precision matrix base class.
 *
 * @author Haifeng Li
 */
public abstract class SMatrix extends IMatrix<float[]> {
    /**
     * Sets A[i,j] = x.
     */
    public abstract SMatrix set(int i, int j, float x);

    /**
     * Sets A[i,j] = x for Scala users.
     */
    public SMatrix update(int i, int j, float x) {
        return set(i, j, x);
    }

    /**
     * Returns A[i, j].
     */
    public abstract float get(int i, int j);

    /**
     * Returns A[i, j]. For Scala users.
     */
    public float apply(int i, int j) {
        return get(i, j);
    }

    @Override
    String str(int i, int j) {
        return String.format("%.6g", get(i, j));
    }

    /**
     * Returns the diagonal elements.
     */
    public float[] diag() {
        int n = Math.min(nrows(), ncols());

        float[] d = new float[n];
        for (int i = 0; i < n; i++) {
            d[i] = get(i, i);
        }

        return d;
    }

    /**
     * Returns the matrix trace. The sum of the diagonal elements.
     */
    public float trace() {
        int n = Math.min(nrows(), ncols());

        float t = 0.0f;
        for (int i = 0; i < n; i++) {
            t += get(i, i);
        }

        return t;
    }

    /**
     * Matrix-vector multiplication.
     * <pre><code>
     *     y = alpha * op(A) * x + beta * y
     * </code></pre>
     * where op is the transpose operation.
     */
    public abstract void mv(Transpose trans, float alpha, float[] x, float beta, float[] y);

    @Override
    public float[] mv(float[] x) {
        float[] y = new float[nrows()];
        mv(NO_TRANSPOSE, 1.0f, x, 0.0f, y);
        return y;
    }

    @Override
    public void mv(float[] x, float[] y) {
        mv(NO_TRANSPOSE, 1.0f, x, 0.0f, y);
    }

    /**
     * Matrix-vector multiplication.
     * <pre><code>
     *     y = alpha * A * x + beta * y
     * </code></pre>
     */
    public void mv(float alpha, float[] x, float beta, float[] y) {
        mv(NO_TRANSPOSE, alpha, x, beta, y);
    }

    @Override
    public float[] tv(float[] x) {
        float[] y = new float[ncols()];
        mv(TRANSPOSE, 1.0f, x, 0.0f, y);
        return y;
    }

    @Override
    public void tv(float[] x, float[] y) {
        mv(TRANSPOSE, 1.0f, x, 0.0f, y);
    }

    /**
     * Matrix-vector multiplication.
     * <pre><code>
     *     y = alpha * A' * x + beta * y
     * </code></pre>
     */
    public void tv(float alpha, float[] x, float beta, float[] y) {
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
     * @return a dense or sparse matrix.
     * @author Haifeng Li
     */
    public static SMatrix market(Path path) throws IOException, ParseException {
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
                int nrows = s.nextInt();
                int ncols = s.nextInt();

                FloatMatrix matrix = new FloatMatrix(nrows, ncols);
                for (int j = 0; j < ncols; j++) {
                    for (int i = 0; i < nrows; i++) {
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
                int nrows = s.nextInt();
                int ncols = s.nextInt();
                int nz = s.nextInt();

                if (symmetric && nz == nrows * (nrows + 1) / 2) {
                    if (nrows != ncols) {
                        throw new IllegalStateException(String.format("Symmetric matrix is not square: %d != %d", nrows, ncols));
                    }

                    FloatSymmMatrix matrix = new FloatSymmMatrix(LOWER, nrows);
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
                } else if (skew && nz == nrows * (nrows + 1) / 2) {
                    if (nrows != ncols) {
                        throw new IllegalStateException(String.format("Skew-symmetric matrix is not square: %d != %d", nrows, ncols));
                    }

                    FloatMatrix matrix = new FloatMatrix(nrows, ncols);
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
                int[] colSize = new int[ncols];
                List<SparseArray> rows = new ArrayList<>();
                for (int i = 0; i < nrows; i++) {
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

                int[] pos = new int[ncols];
                int[] colIndex = new int[ncols + 1];
                for (int i = 0; i < ncols; i++) {
                    colIndex[i + 1] = colIndex[i] + colSize[i];
                }

                if (symmetric || skew) {
                    nz *= 2;
                }
                int[] rowIndex = new int[nz];
                float[] x = new float[nz];

                for (int i = 0; i < nrows; i++) {
                    for (SparseArray.Entry e :rows.get(i)) {
                        int j = e.i;
                        int k = colIndex[j] + pos[j];

                        rowIndex[k] = i;
                        x[k] = (float) e.x;
                        pos[j]++;
                    }
                }

                FloatSparseMatrix matrix = new FloatSparseMatrix(nrows, ncols, x, rowIndex, colIndex);
                return matrix;

            }

            throw new ParseException("Invalid Matrix Market format: " + format, 0);
        }
    }

    /**
     * Returns the matrix of A' * A or A * A', whichever is smaller.
     * For SVD, we compute eigenvalue decomposition of A' * A
     * when m >= n, or that of A * A' when m < n.
     */
    SMatrix square() {
        SMatrix A = this;

        return new SMatrix() {
            /**
             * The larger dimension of A.
             */
            private int m = Math.max(A.nrows(), A.ncols());
            /**
             * The smaller dimension of A.
             */
            private int n = Math.min(A.nrows(), A.ncols());
            /**
             * Workspace for A * x
             */
            private float[] Ax = new float[m + n];

            @Override
            public int nrows() {
                return n;
            }

            @Override
            public int ncols() {
                return n;
            }

            @Override
            public long size() {
                return m + n;
            }

            @Override
            public void mv(float[] work, int inputOffset, int outputOffset) {
                System.arraycopy(work, inputOffset, Ax, 0, n);

                if (A.nrows() >= A.ncols()) {
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
            public SMatrix set(int i, int j, float x) {
                throw new UnsupportedOperationException();
            }
        };
    }
}
