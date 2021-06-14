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
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import smile.math.MathEx;
import smile.math.blas.Transpose;
import smile.util.Strings;

import static java.util.Spliterator.*;

/**
 * A sparse matrix is a matrix populated primarily with zeros. Conceptually,
 * sparsity corresponds to systems which are loosely coupled. Huge sparse
 * matrices often appear when solving partial differential equations.
 * <p>
 * Operations using standard dense matrix structures and algorithms are slow
 * and consume large amounts of memory when applied to large sparse matrices.
 * Indeed, some very large sparse matrices are infeasible to manipulate with
 * the standard dense algorithms. Sparse data is by nature easily compressed,
 * and this compression almost always results in significantly less computer
 * data storage usage.
 * <p>
 * This class employs Harwell-Boeing column-compressed sparse matrix format.
 * Nonzero values are stored in an array (top-to-bottom, then
 * left-to-right-bottom). The row indices corresponding to the values are
 * also stored. Besides, a list of pointers are indexes where each column
 * starts. This format is efficient for arithmetic operations, column slicing,
 * and matrix-vector products. One typically uses SparseDataset for
 * construction of SparseMatrix.
 * <p>
 * For iteration through the elements of a matrix, this class provides
 * a functional API to iterate through the non-zero elements. This iteration
 * can be done by passing a lambda to be called on each non-zero element or
 * by processing a stream of objects representing each non-zero element.
 * The direct functional API is faster (and is about as fast as writing the
 * low-level loops against the internals of the matrix itself) while the
 * streaming interface is more flexible.
 *
 * @author Haifeng Li
 */
public class SparseMatrix extends IMatrix implements Iterable<SparseMatrix.Entry> {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(SparseMatrix.class);
    private static final long serialVersionUID = 2L;

    /**
     * The number of rows.
     */
    private final int m;
    /**
     * The number of columns.
     */
    private final int n;
    /**
     * The index of the start of columns.
     */
    private final int[] colIndex;
    /**
     * The row indices of nonzero values.
     */
    private final int[] rowIndex;
    /**
     * The array of nonzero values stored column by column.
     */
    private final double[] nonzeros;

    /**
     * Encapsulates an entry in a matrix for use in streaming. As typical stream object,
     * this object is immutable. But we can update the corresponding value in the matrix
     * through <code>update</code> method. This provides an efficient way to update the
     * non-zero entries of a sparse matrix.
     */
    public class Entry {
        // these fields are exposed for direct access to simplify in-lining by the JVM
        /** The row index. */
        public final int i;
        /** The column index. */
        public final int j;
        /** The value. */
        public final double x;
        /** The index to the matrix storage. */
        public final int index;

        /**
         * Private constructor. Only the enclosure matrix can creates
         * the instances of entry.
         * @param i the row index.
         * @param j the column index.
         * @param index the storage index.
         */
        private Entry(int i, int j, int index) {
            this.i = i;
            this.j = j;
            this.x = nonzeros[index];
            this.index = index;
        }

        /**
         * Update the entry value in the matrix. Note that the field <code>value</code>
         * is final and thus not updated.
         * @param value the new entry value.
         */
        public void update(double value) {
            nonzeros[index] = value;
        }

        @Override
        public String toString() {
            return String.format("(%d, %d):%s", i, j, Strings.format(x));
        }
    }

    /**
     * Constructor.
     * @param m the number of rows in the matrix.
     * @param n the number of columns in the matrix.
     * @param nvals the number of nonzero entries in the matrix.
     */
    private SparseMatrix(int m, int n, int nvals) {
        this.m = m;
        this.n = n;
        rowIndex = new int[nvals];
        colIndex = new int[n + 1];
        nonzeros = new double[nvals];
    }

    /**
     * Constructor.
     * @param m the number of rows in the matrix.
     * @param n the number of columns in the matrix.
     * @param rowIndex the row indices of nonzero values.
     * @param colIndex the index of the start of columns.
     * @param nonzeros the array of nonzero values stored column by column.
     */
    public SparseMatrix(int m, int n, double[] nonzeros, int[] rowIndex, int[] colIndex) {
        this.m = m;
        this.n = n;
        this.rowIndex = rowIndex;
        this.colIndex = colIndex;
        this.nonzeros = nonzeros;
    }

    /**
     * Constructor.
     * @param A a dense matrix to converted into sparse matrix format.
     */
    public SparseMatrix(double[][] A) {
        this(A, 100 * MathEx.EPSILON);
    }

    /**
     * Constructor.
     * @param A a dense matrix to converted into sparse matrix format.
     * @param tol the tolerance to regard a value as zero if {@code |x| < tol}.
     */
    public SparseMatrix(double[][] A, double tol) {
        m = A.length;
        n = A[0].length;

        int nvals = 0; // number of non-zero elements
        for (int i = 0; i < m; i++) {
            for (int j = 0; j < n; j++) {
                if (Math.abs(A[i][j]) >= tol) {
                    nvals++;
                }
            }
        }

        nonzeros = new double[nvals];
        rowIndex = new int[nvals];
        colIndex = new int[n + 1];
        colIndex[n] = nvals;

        int k = 0;
        for (int j = 0; j < n; j++) {
            colIndex[j] = k;
            for (int i = 0; i < m; i++) {
                if (Math.abs(A[i][j]) >= tol) {
                    rowIndex[k] = i;
                    nonzeros[k] = A[i][j];
                    k++;
                }
            }
        }
    }

    @Override
    public SparseMatrix clone() {
        return new SparseMatrix(m, n, nonzeros.clone(), rowIndex.clone(), colIndex.clone());
    }

    @Override
    public int nrow() {
        return m;
    }

    @Override
    public int ncol() {
        return n;
    }

    @Override
    public long size() {
        return colIndex[n];
    }

    /**
     * Returns the stream of the non-zero elements.
     * @return the stream of the non-zero elements
     */
    public Stream<Entry> nonzeros() {
        Spliterator<Entry> spliterator = Spliterators.spliterator(iterator(), size(), ORDERED | SIZED | IMMUTABLE);
        return StreamSupport.stream(spliterator, false);
    }

    /**
     * Returns the stream of the non-zero elements in given column range.
     *
     * @param beginColumn the beginning column, inclusive.
     * @param endColumn   the end column, exclusive.
     * @return the stream of non-zero elements.
     */
    public Stream<Entry> nonzeros(int beginColumn, int endColumn) {
        Spliterator<Entry> spliterator = Spliterators.spliterator(iterator(beginColumn, endColumn), colIndex[endColumn] - colIndex[beginColumn], ORDERED | SIZED | IMMUTABLE);
        return StreamSupport.stream(spliterator, false);
    }

    /**
     * Returns the iterator of nonzero entries.
     * @return the iterator of nonzero entries
     */
    @Override
    public Iterator<Entry> iterator() {
        return iterator(0, n);
    }

    /**
     * Returns the iterator of nonzero entries.
     * @param beginColumn the beginning column, inclusive.
     * @param endColumn   the end column, exclusive.
     * @return the iterator of nonzero entries
     */
    public Iterator<Entry> iterator(int beginColumn, int endColumn) {
        if (beginColumn < 0 || beginColumn >= n) {
            throw new IllegalArgumentException("Invalid begin column: " + beginColumn);
        }

        if (endColumn <= beginColumn || endColumn > n) {
            throw new IllegalArgumentException("Invalid end column: " + endColumn);
        }

        return new Iterator<Entry>() {
            int k = colIndex[beginColumn]; // entry index
            int j = beginColumn; // column

            @Override
            public boolean hasNext() {
                return k < colIndex[endColumn];
            }

            @Override
            public Entry next() {
                int i = rowIndex[k];
                while (k >= colIndex[j + 1]) j++;
                return new Entry(i, j, k++);
            }
        };
    }

    /**
     * For each loop on non-zero elements. This will be a bit faster than iterator or stream
     * by avoiding boxing. But it will be considerably less general.
     * <p>
     * Note that the consumer could be called on values that are either effectively or actually
     * zero. The only guarantee is that no values that are known to be zero based on the
     * structure of the matrix will be processed.
     *
     * @param consumer The matrix element consumer.
     */
    public void forEachNonZero(DoubleConsumer consumer) {
        for (int j = 0; j < n; j++) {
            for (int k = colIndex[j]; k < colIndex[j + 1]; k++) {
                int i = rowIndex[k];
                consumer.accept(i, j, nonzeros[k]);
            }
        }
    }

    /**
     * For each loop on non-zero elements. This will be a bit faster than iterator or stream
     * by avoiding boxing. But it will be considerably less general.
     * <p>
     * Note that the consumer could be called on values that are either effectively or actually
     * zero. The only guarantee is that no values that are known to be zero based on the
     * structure of the matrix will be processed.
     *
     * @param beginColumn The beginning column, inclusive.
     * @param endColumn   The end column, exclusive.
     * @param consumer    The matrix element consumer.
     */
    public void forEachNonZero(int beginColumn, int endColumn, DoubleConsumer consumer) {
        if (beginColumn < 0 || beginColumn >= n) {
            throw new IllegalArgumentException("Invalid begin column: " + beginColumn);
        }

        if (endColumn <= beginColumn || endColumn > n) {
            throw new IllegalArgumentException("Invalid end column: " + endColumn);
        }

        for (int j = beginColumn; j < endColumn; j++) {
            for (int k = colIndex[j]; k < colIndex[j + 1]; k++) {
                int i = rowIndex[k];
                consumer.accept(i, j, nonzeros[k]);
            }
        }
    }

    /**
     * Returns the element at the storage index.
     * @param index the storage index.
     * @return the element.
     */
    public double get(int index) {
        return nonzeros[index];
    }

    /**
     * Sets the element at the storage index.
     * @param index the storage index.
     * @param value the element.
     */
    public void set(int index, double value) {
        nonzeros[index] = value;
    }

    @Override
    public double get(int i, int j) {
        if (i < 0 || i >= m || j < 0 || j >= n) {
            throw new IllegalArgumentException("Invalid index: row = " + i + " col = " + j);
        }

        for (int k = colIndex[j]; k < colIndex[j + 1]; k++) {
            if (rowIndex[k] == i) {
                return nonzeros[k];
            }
        }

        return 0.0;
    }

    @Override
    public void mv(Transpose trans, double alpha, double[] x, double beta, double[] y) {
        int k = trans == Transpose.NO_TRANSPOSE ? m : n;
        double[] ax = y;
        if (beta == 0.0) {
            Arrays.fill(y, 0.0);
        } else {
            ax = new double[k];
        }

        if (trans == Transpose.NO_TRANSPOSE) {
            for (int j = 0; j < n; j++) {
                for (int i = colIndex[j]; i < colIndex[j + 1]; i++) {
                    ax[rowIndex[i]] += nonzeros[i] * x[j];
                }
            }
        } else {
            for (int i = 0; i < n; i++) {
                for (int j = colIndex[i]; j < colIndex[i + 1]; j++) {
                    ax[i] += nonzeros[j] * x[rowIndex[j]];
                }
            }
        }

        if (beta != 0.0 || alpha != 1.0) {
            for (int i = 0; i < k; i++) {
                y[i] = alpha * ax[i] + beta * y[i];
            }
        }
    }

    @Override
    public void mv(double[] work, int inputOffset, int outputOffset) {
        Arrays.fill(work, outputOffset, outputOffset + m, 0.0);

        for (int j = 0; j < n; j++) {
            for (int i = colIndex[j]; i < colIndex[j + 1]; i++) {
                work[outputOffset + rowIndex[i]] += nonzeros[i] * work[inputOffset + j];
            }
        }
    }

    @Override
    public void tv(double[] work, int inputOffset, int outputOffset) {
        Arrays.fill(work, outputOffset, outputOffset + n, 0.0);

        for (int i = 0; i < n; i++) {
            for (int j = colIndex[i]; j < colIndex[i + 1]; j++) {
                work[outputOffset + i] += nonzeros[j] * work[inputOffset + rowIndex[j]];
            }
        }
    }

    /**
     * Returns the transpose of matrix.
     * @return the transpose of matrix.
     */
    public SparseMatrix transpose() {
        SparseMatrix trans = new SparseMatrix(n, m, nonzeros.length);

        int[] count = new int[m];
        for (int i = 0; i < n; i++) {
            for (int j = colIndex[i]; j < colIndex[i + 1]; j++) {
                int k = rowIndex[j];
                count[k]++;
            }
        }

        for (int j = 0; j < m; j++) {
            trans.colIndex[j + 1] = trans.colIndex[j] + count[j];
        }

        Arrays.fill(count, 0);
        for (int i = 0; i < n; i++) {
            for (int j = colIndex[i]; j < colIndex[i + 1]; j++) {
                int k = rowIndex[j];
                int index = trans.colIndex[k] + count[k];
                trans.rowIndex[index] = i;
                trans.nonzeros[index] = nonzeros[j];
                count[k]++;
            }
        }

        return trans;
    }

    /**
     * Returns the matrix multiplication C = A * B.
     * @param B the operand.
     * @return the multiplication.
     */
    public SparseMatrix mm(SparseMatrix B) {
        if (n != B.m) {
            throw new IllegalArgumentException(String.format("Matrix dimensions do not match for matrix multiplication: %d x %d vs %d x %d", nrow(), ncol(), B.nrow(), B.ncol()));
        }

        int n = B.n;
        int anz = colIndex[n];
        int[] Bp = B.colIndex;
        int[] Bi = B.rowIndex;
        double[] Bx = B.nonzeros;
        int bnz = Bp[n];

        int[] w = new int[m];
        double[] abj = new double[m];

        int nzmax = Math.max(anz + bnz, m);

        SparseMatrix C = new SparseMatrix(m, n, nzmax);
        int[] Cp = C.colIndex;
        int[] Ci = C.rowIndex;
        double[] Cx = C.nonzeros;

        int nz = 0;
        for (int j = 0; j < n; j++) {
            if (nz + m > nzmax) {
                nzmax = 2 * nzmax + m;
                double[] Cx2 = new double[nzmax];
                int[] Ci2 = new int[nzmax];
                System.arraycopy(Ci, 0, Ci2, 0, nz);
                System.arraycopy(Cx, 0, Cx2, 0, nz);
                Ci = Ci2;
                Cx = Cx2;
                C = new SparseMatrix(m, n, Cx2, Ci2, Cp);
            }

            // column j of C starts here
            Cp[j] = nz;

            for (int p = Bp[j]; p < Bp[j + 1]; p++) {
                nz = scatter(this, Bi[p], Bx[p], w, abj, j + 1, C, nz);
            }

            for (int p = Cp[j]; p < nz; p++) {
                Cx[p] = abj[Ci[p]];
            }
        }

        // finalize the last column of C
        Cp[n] = nz;

        return C;
    }

    /**
     * x = x + beta * A(:,j), where x is a dense vector and A(:,j) is sparse.
     */
    private static int scatter(SparseMatrix A, int j, double beta, int[] w, double[] x, int mark, SparseMatrix C, int nz) {
        int[] Ap = A.colIndex;
        int[] Ai = A.rowIndex;
        double[] Ax = A.nonzeros;

        int[] Ci = C.rowIndex;
        for (int p = Ap[j]; p < Ap[j + 1]; p++) {
            int i = Ai[p];                // A(i,j) is nonzero
            if (w[i] < mark) {
                w[i] = mark;              // i is new entry in column j
                Ci[nz++] = i;             // add i to pattern of C(:,j)
                x[i] = beta * Ax[p];      // x(i) = beta*A(i,j)
            } else {
                x[i] += beta * Ax[p];     // i exists in C(:,j) already
            }
        }

        return nz;
    }

    /**
     * Returns {@code A' * A}.
     * @return {@code A' * A}
     */
    public SparseMatrix ata() {
        SparseMatrix AT = transpose();
        return AT.aat(this);
    }

    /**
     * Returns {@code A * A'}.
     * @return {@code A * A'}
     */
    public SparseMatrix aat() {
        SparseMatrix AT = transpose();
        return aat(AT);
    }

    /** Returns A * A' */
    private SparseMatrix aat(SparseMatrix AT) {
        int[] done = new int[m];
        for (int i = 0; i < m; i++) {
            done[i] = -1;
        }

        // First pass determines the number of nonzeros.
        int nvals = 0;
        // Outer loop over columns of A' in AA'
        for (int j = 0; j < m; j++) {
            for (int i = AT.colIndex[j]; i < AT.colIndex[j + 1]; i++) {
                int k = AT.rowIndex[i];
                for (int l = colIndex[k]; l < colIndex[k + 1]; l++) {
                    int h = rowIndex[l];
                    // Test if contribution already included.
                    if (done[h] != j) {
                        done[h] = j;
                        nvals++;
                    }
                }
            }
        }

        SparseMatrix aat = new SparseMatrix(m, m, nvals);

        nvals = 0;
        for (int i = 0; i < m; i++) {
            done[i] = -1;
        }

        // Second pass determines columns of aat. Code is identical to first
        // pass except colIndex and rowIndex get assigned at appropriate places.
        for (int j = 0; j < m; j++) {
            aat.colIndex[j] = nvals;
            for (int i = AT.colIndex[j]; i < AT.colIndex[j + 1]; i++) {
                int k = AT.rowIndex[i];
                for (int l = colIndex[k]; l < colIndex[k + 1]; l++) {
                    int h = rowIndex[l];
                    if (done[h] != j) {
                        done[h] = j;
                        aat.rowIndex[nvals] = h;
                        nvals++;
                    }
                }
            }
        }

        // Set last value.
        aat.colIndex[m] = nvals;

        // Sort columns.
        for (int j = 0; j < m; j++) {
            if (aat.colIndex[j + 1] - aat.colIndex[j] > 1) {
                Arrays.sort(aat.rowIndex, aat.colIndex[j], aat.colIndex[j + 1]);
            }
        }

        double[] temp = new double[m];
        for (int i = 0; i < m; i++) {
            for (int j = AT.colIndex[i]; j < AT.colIndex[i + 1]; j++) {
                int k = AT.rowIndex[j];
                for (int l = colIndex[k]; l < colIndex[k + 1]; l++) {
                    int h = rowIndex[l];
                    temp[h] += AT.nonzeros[j] * nonzeros[l];
                }
            }

            for (int j = aat.colIndex[i]; j < aat.colIndex[i + 1]; j++) {
                int k = aat.rowIndex[j];
                aat.nonzeros[j] = temp[k];
                temp[k] = 0.0;
            }
        }

        return aat;
    }

    @Override
    public double[] diag() {
        int n = Math.min(nrow(), ncol());
        double[] d = new double[n];

        for (int i = 0; i < n; i++) {
            for (int j = colIndex[i]; j < colIndex[i + 1]; j++) {
                if (rowIndex[j] == i) {
                    d[i] = nonzeros[j];
                    break;
                }
            }
        }

        return d;
    }

    /**
     * Reads a sparse matrix from a Harwell-Boeing Exchange Format file.
     * For details, see
     * <a href="http://people.sc.fsu.edu/~jburkardt/data/hb/hb.html">http://people.sc.fsu.edu/~jburkardt/data/hb/hb.html</a>.
     *
     * Note that our implementation supports only real-valued matrix and we
     * ignore the optional supplementary data (e.g. right hand side vectors).
     *
     * @param path the input file path.
     * @throws IOException when fails to read the file.
     * @return the matrix.
     */
    public static SparseMatrix harwell(Path path) throws IOException {
        logger.info("Reads sparse matrix file '{}'", path.toAbsolutePath());
        try (InputStream stream = Files.newInputStream(path);
             Scanner scanner = new Scanner(stream)) {

            // Ignore the title line.
            String line = scanner.nextLine();
            logger.info(line);

            line = scanner.nextLine().trim();
            logger.info(line);
            String[] tokens = line.split("\\s+");
            int RHSCRD = Integer.parseInt(tokens[4]);

            line = scanner.nextLine().trim();
            logger.info(line);
            if (!line.startsWith("R")) {
                throw new UnsupportedOperationException("SparseMatrix supports only real-valued matrix.");
            }

            tokens = line.split("\\s+");
            int nrow = Integer.parseInt(tokens[1]);
            int ncol = Integer.parseInt(tokens[2]);
            int nz = Integer.parseInt(tokens[3]);

            line = scanner.nextLine();
            logger.info(line);
            if (RHSCRD > 0) {
                line = scanner.nextLine();
                logger.info(line);
            }

            int[] colIndex = new int[ncol + 1];
            int[] rowIndex = new int[nz];
            double[] data = new double[nz];
            for (int i = 0; i <= ncol; i++) {
                colIndex[i] = scanner.nextInt() - 1;
            }
            for (int i = 0; i < nz; i++) {
                rowIndex[i] = scanner.nextInt() - 1;
            }
            for (int i = 0; i < nz; i++) {
                data[i] = scanner.nextDouble();
            }

            return new SparseMatrix(nrow, ncol, data, rowIndex, colIndex);
        }
    }

    /**
     * Reads a sparse matrix from a Rutherford-Boeing Exchange Format file.
     * The Rutherford Boeing format is an updated more flexible version of the
     * Harwell Boeing format.
     * For details, see
     * <a href="http://people.sc.fsu.edu/~jburkardt/data/rb/rb.html">http://people.sc.fsu.edu/~jburkardt/data/rb/rb.html</a>.
     * Especially, the supplementary data in the form of right-hand sides,
     * estimates or solutions are treated as separate files.
     *
     * Note that our implementation supports only real-valued matrix and we ignore
     * the optional supplementary data (e.g. right hand side vectors).
     *
     * @param path the input file path.
     * @throws IOException when fails to read the file.
     * @return the matrix.
     */
    public static SparseMatrix rutherford(Path path) throws IOException {
        // As we ignore the supplementary data, the parsing process
        // is same as Harwell.
        return harwell(path);
    }

    /**
     * Reads a sparse matrix from a text file.
     * The first line contains three integers, which are the number of rows,
     * the number of columns, and the number of nonzero entries in the matrix.
     * <p>
     * Following the first line, there are m + 1 integers that are the indices of
     * columns, where m is the number of columns. Then there are n integers that
     * are the row indices of nonzero entries, where n is the number of nonzero
     * entries. Finally, there are n float numbers that are the values of nonzero
     * entries.
     *
     * @param path the input file path.
     * @throws IOException when fails to read the file.
     * @return the matrix.
     */
    public static SparseMatrix text(Path path) throws IOException {
        try (InputStream stream = Files.newInputStream(path);
             Scanner scanner = new Scanner(stream)) {
            int nrow = scanner.nextInt();
            int ncol = scanner.nextInt();
            int nz = scanner.nextInt();

            int[] colIndex = new int[ncol + 1];
            int[] rowIndex = new int[nz];
            double[] data = new double[nz];
            for (int i = 0; i <= ncol; i++) {
                colIndex[i] = scanner.nextInt() - 1;
            }
            for (int i = 0; i < nz; i++) {
                rowIndex[i] = scanner.nextInt() - 1;
            }
            for (int i = 0; i < nz; i++) {
                data[i] = scanner.nextDouble();
            }

            return new SparseMatrix(nrow, ncol, data, rowIndex, colIndex);
        }
    }
}
