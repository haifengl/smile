/*******************************************************************************
 * Copyright (c) 2010-2019 Haifeng Li
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
 *******************************************************************************/

package smile.math.matrix;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import smile.math.MathEx;
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
 * Nonzero values are stored in an array (top-to-bottom, then left-to-right-bottom).
 * The row indices corresponding to the values are also stored. Besides, a list
 * of pointers are indexes where each column starts. This format is efficient
 * for arithmetic operations, column slicing, and matrix-vector products.
 * One typically uses SparseDataset for construction of SparseMatrix.
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
public class SparseMatrix implements Matrix, MatrixMultiplication<SparseMatrix, SparseMatrix>, Iterable<SparseMatrix.Entry> {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(SparseMatrix.class);
    private static final long serialVersionUID = 1L;

    /**
     * The number of rows.
     */
    private int nrows;
    /**
     * The number of columns.
     */
    private int ncols;
    /**
     * The index of the start of columns.
     */
    private int[] colIndex;
    /**
     * The row indices of nonzero values.
     */
    private int[] rowIndex;
    /**
     * The array of nonzero values stored column by column.
     */
    private double[] x;
    /**
     * True if the matrix is symmetric.
     */
    private boolean symmetric = false;

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

        /** The index to the internal storage. */
        private final int index;

        /**
         * Private constructor. Only the enclosure matrix can creates
         * the instances of entry.
         */
        private Entry(int i, int j, int index) {
            this.i = i;
            this.j = j;
            this.x = SparseMatrix.this.x[index];
            this.index = index;
        }

        /**
         * Update the value of entry in the matrix. Note that the field <code>value</code>
         * is final and thus not updated.
         */
        public void update(double value) {
            SparseMatrix.this.x[index] = value;
        }

        @Override
        public String toString() {
            return String.format("(%d, %d):%s", i, j, Strings.decimal(x));
        }
    }

    /**
     * Constructor.
     * @param nrows the number of rows in the matrix.
     * @param ncols the number of columns in the matrix.
     * @param nvals the number of nonzero entries in the matrix.
     */
    private SparseMatrix(int nrows, int ncols, int nvals) {
        this.nrows = nrows;
        this.ncols = ncols;
        rowIndex = new int[nvals];
        colIndex = new int[ncols + 1];
        x = new double[nvals];
    }

    /**
     * Constructor.
     * @param nrows the number of rows in the matrix.
     * @param ncols the number of columns in the matrix.
     * @param rowIndex the row indices of nonzero values.
     * @param colIndex the index of the start of columns.
     * @param x the array of nonzero values stored column by column.
     */
    public SparseMatrix(int nrows, int ncols, double[] x, int[] rowIndex, int[] colIndex) {
        this.nrows = nrows;
        this.ncols = ncols;
        this.rowIndex = rowIndex;
        this.colIndex = colIndex;
        this.x = x;
    }

    /**
     * Constructor.
     * @param D a dense matrix to converted into sparse matrix format.
     */
    public SparseMatrix(double[][] D) {
        this(D, 100 * MathEx.EPSILON);
    }

    /**
     * Constructor.
     * @param D a dense matrix to converted into sparse matrix format.
     * @param tol the tolerance to regard a value as zero if |x| &lt; tol.
     */
    public SparseMatrix(double[][] D, double tol) {
        nrows = D.length;
        ncols = D[0].length;

        int n = 0; // number of non-zero elements
        for (int i = 0; i < nrows; i++) {
            for (int j = 0; j < ncols; j++) {
                if (Math.abs(D[i][j]) >= tol) {
                    n++;
                }
            }
        }

        x = new double[n];
        rowIndex = new int[n];
        colIndex = new int[ncols + 1];
        colIndex[ncols] = n;

        n = 0;
        for (int j = 0; j < ncols; j++) {
            colIndex[j] = n;
            for (int i = 0; i < nrows; i++) {
                if (Math.abs(D[i][j]) >= tol) {
                    rowIndex[n] = i;
                    x[n] = D[i][j];
                    n++;
                }
            }
        }
    }
    /**
     * Returns an iterator of nonzero entries.
     * @return an iterator of nonzero entries
     */
    public Iterator<Entry> iterator() {
        return iterator(0, ncols);
    }

    /**
     * Returns an iterator of nonzero entries.
     * @param beginColumn The beginning column, inclusive.
     * @param endColumn   The end column, exclusive.
     * @return an iterator of nonzero entries
     */
    public Iterator<Entry> iterator(int beginColumn, int endColumn) {
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

    @Override
    public boolean isSymmetric() {
        return symmetric;
    }

    @Override
    public void setSymmetric(boolean symmetric) {
        this.symmetric = symmetric;
    }

    @Override
    public int nrows() {
        return nrows;
    }

    @Override
    public int ncols() {
        return ncols;
    }

    /**
     * Returns the number of nonzero values.
     */
    public int length() {
        return colIndex[ncols];
    }

    /*
     * Benchmarks of iteration and stream using jmh:
     *
     * Benchmark                                       Mode  Cnt        Score       Error   Units
     * IteratorSpeed.timeDirect                        avgt    5   429888.246    3819.232   ns/op
     * IteratorSpeed.timeDirect: gc.alloc.rate         avgt    5      ~ 10^-4              MB/sec
     * IteratorSpeed.timeDirect: gc.alloc.rate.norm    avgt    5        0.088       0.001    B/op
     * IteratorSpeed.timeDirect: gc.count              avgt    5          ~ 0              counts
     * IteratorSpeed.timeDirect: stack                 avgt               NaN                 ---
     * IteratorSpeed.timeIterator                      avgt    5   430718.537    7831.509   ns/op
     * IteratorSpeed.timeIterator: gc.alloc.rate       avgt    5        0.028       0.001  MB/sec
     * IteratorSpeed.timeIterator: gc.alloc.rate.norm  avgt    5       16.089       0.011    B/op
     * IteratorSpeed.timeIterator: gc.count            avgt    5          ~ 0              counts
     * IteratorSpeed.timeIterator: stack               avgt               NaN                 ---
     * IteratorSpeed.timeStream                        avgt    5  1032370.658   55295.704   ns/op
     * IteratorSpeed.timeStream: gc.alloc.rate         avgt    5        0.077       0.004  MB/sec
     * IteratorSpeed.timeStream: gc.alloc.rate.norm    avgt    5      104.210       0.011    B/op
     * IteratorSpeed.timeStream: gc.count              avgt    5          ~ 0              counts
     * IteratorSpeed.timeStream: stack                 avgt               NaN                 ---
     *
     * The three cases are timeDirect for a direct loop over internal data structures,
     * timeIterator for #foreachNonZero and timeStream for the streaming equivalent form.
     * The timeIterator case is at most a few percent slower than the direct loops while
     * the stream is about 2-3 times slower. Note that the JVM is clever enough to optimize
     * away the creation of temporary objects in the streaming idiom.
     */

    /**
     * Provides a stream over all of the non-zero elements of a sparse matrix.
     */
    public Stream<Entry> nonzeros() {
        Spliterator<Entry> spliterator = Spliterators.spliterator(iterator(), length(), ORDERED | SIZED | IMMUTABLE);
        return StreamSupport.stream(spliterator, false);
    }

    /**
     * Provides a stream over all of the non-zero elements of range of columns of a sparse matrix.
     *
     * @param beginColumn The beginning column, inclusive.
     * @param endColumn   The end column, exclusive.
     */
    public Stream<Entry> nonzeros(int beginColumn, int endColumn) {
        Spliterator<Entry> spliterator = Spliterators.spliterator(iterator(beginColumn, endColumn), colIndex[endColumn] - colIndex[beginColumn], ORDERED | SIZED | IMMUTABLE);
        return StreamSupport.stream(spliterator, false);
    }

    @Override
    public double get(int i, int j) {
        if (i < 0 || i >= nrows || j < 0 || j >= ncols) {
            throw new IllegalArgumentException("Invalid index: row = " + i + " col = " + j);
        }

        for (int k = colIndex[j]; k < colIndex[j + 1]; k++) {
            if (rowIndex[k] == i) {
                return x[k];
            }
        }

        return 0.0;
    }

    @Override
    public double[] ax(double[] x, double[] y) {
        Arrays.fill(y, 0.0);

        for (int j = 0; j < ncols; j++) {
            for (int i = colIndex[j]; i < colIndex[j + 1]; i++) {
                y[rowIndex[i]] += this.x[i] * x[j];
            }
        }

        return y;
    }

    @Override
    public double[] axpy(double[] x, double[] y) {
        for (int j = 0; j < ncols; j++) {
            for (int i = colIndex[j]; i < colIndex[j + 1]; i++) {
                y[rowIndex[i]] += this.x[i] * x[j];
            }
        }

        return y;
    }

    @Override
    public double[] axpy(double[] x, double[] y, double b) {
        for (int i = 0; i < y.length; i++) {
            y[i] *= b;
        }

        for (int j = 0; j < ncols; j++) {
            for (int i = colIndex[j]; i < colIndex[j + 1]; i++) {
                y[rowIndex[i]] += this.x[i] * x[j];
            }
        }

        return y;
    }

    @Override
    public double[] atx(double[] x, double[] y) {
        Arrays.fill(y, 0.0);
        for (int i = 0; i < ncols; i++) {
            for (int j = colIndex[i]; j < colIndex[i + 1]; j++) {
                y[i] += this.x[j] * x[rowIndex[j]];
            }
        }

        return y;
    }


    @Override
    public double[] atxpy(double[] x, double[] y) {
        for (int i = 0; i < ncols; i++) {
            for (int j = colIndex[i]; j < colIndex[i + 1]; j++) {
                y[i] += this.x[j] * x[rowIndex[j]];
            }
        }

        return y;
    }

    @Override
    public double[] atxpy(double[] x, double[] y, double b) {
        for (int i = 0; i < ncols; i++) {
            y[i] *= b;
            for (int j = colIndex[i]; j < colIndex[i + 1]; j++) {
                y[i] += this.x[j] * x[rowIndex[j]];
            }
        }

        return y;
    }

    @Override
    public SparseMatrix transpose() {
        int m = nrows, n = ncols;
        SparseMatrix at = new SparseMatrix(n, m, x.length);

        int[] count = new int[m];
        for (int i = 0; i < n; i++) {
            for (int j = colIndex[i]; j < colIndex[i + 1]; j++) {
                int k = rowIndex[j];
                count[k]++;
            }
        }

        for (int j = 0; j < m; j++) {
            at.colIndex[j + 1] = at.colIndex[j] + count[j];
        }

        Arrays.fill(count, 0);
        for (int i = 0; i < n; i++) {
            for (int j = colIndex[i]; j < colIndex[i + 1]; j++) {
                int k = rowIndex[j];
                int index = at.colIndex[k] + count[k];
                at.rowIndex[index] = i;
                at.x[index] = x[j];
                count[k]++;
            }
        }

        return at;
    }

    /**
     * Returns the matrix multiplication C = A * B.
     */
    @Override
    public SparseMatrix abmm(SparseMatrix B) {
        if (ncols != B.nrows) {
            throw new IllegalArgumentException(String.format("Matrix dimensions do not match for matrix multiplication: %d x %d vs %d x %d", nrows(), ncols(), B.nrows(), B.ncols()));
        }

        int m = nrows;
        int anz = length();
        int n = B.ncols;
        int[] Bp = B.colIndex;
        int[] Bi = B.rowIndex;
        double[] Bx = B.x;
        int bnz = Bp[n];

        int[] w = new int[m];
        double[] abj = new double[m];

        int nzmax = Math.max(anz + bnz, m);

        SparseMatrix C = new SparseMatrix(m, n, nzmax);
        int[] Cp = C.colIndex;
        int[] Ci = C.rowIndex;
        double[] Cx = C.x;

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
                C.rowIndex = Ci;
                C.x = Cx;
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
        double[] Ax = A.x;

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

    @Override
    public SparseMatrix abtmm(SparseMatrix B) {
        SparseMatrix BT = B.transpose();
        return abmm(BT);
    }

    @Override
    public SparseMatrix atbmm(SparseMatrix B) {
        SparseMatrix AT = transpose();
        return AT.abmm(B);
    }

    @Override
    public SparseMatrix atbtmm(SparseMatrix B) {
        return B.abmm(this).transpose();
    }

    @Override
    public SparseMatrix ata() {
        SparseMatrix AT = transpose();
        return AT.aat(this);
    }

    @Override
    public SparseMatrix aat() {
        SparseMatrix AT = transpose();
        return aat(AT);
    }

    private SparseMatrix aat(SparseMatrix AT) {
        int m = nrows;
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
                    temp[h] += AT.x[j] * x[l];
                }
            }

            for (int j = aat.colIndex[i]; j < aat.colIndex[i + 1]; j++) {
                int k = aat.rowIndex[j];
                aat.x[j] = temp[k];
                temp[k] = 0.0;
            }
        }

        return aat;
    }

    @Override
    public double[] diag() {
        int n = Math.min(nrows(), ncols());
        double[] d = new double[n];

        for (int i = 0; i < n; i++) {
            for (int j = colIndex[i]; j < colIndex[i + 1]; j++) {
                if (rowIndex[j] == i) {
                    d[i] = x[j];
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
     * Note that our implementation supports only real-valued matrix and we ignore
     * the optional supplementary data (e.g. right hand side vectors).
     *
     * @param path the input file path.
     *
     * @author Haifeng Li
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
            int nrows = Integer.parseInt(tokens[1]);
            int ncols = Integer.parseInt(tokens[2]);
            int nz = Integer.parseInt(tokens[3]);

            line = scanner.nextLine();
            logger.info(line);
            if (RHSCRD > 0) {
                line = scanner.nextLine();
                logger.info(line);
            }

            int[] colIndex = new int[ncols + 1];
            int[] rowIndex = new int[nz];
            double[] data = new double[nz];
            for (int i = 0; i <= ncols; i++) {
                colIndex[i] = scanner.nextInt() - 1;
            }
            for (int i = 0; i < nz; i++) {
                rowIndex[i] = scanner.nextInt() - 1;
            }
            for (int i = 0; i < nz; i++) {
                data[i] = scanner.nextDouble();
            }

            return new SparseMatrix(nrows, ncols, data, rowIndex, colIndex);
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
     *
     * @author Haifeng Li
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
     *
     * @author Haifeng Li
     */
    public static SparseMatrix text(Path path) throws IOException {
        try (InputStream stream = Files.newInputStream(path);
             Scanner scanner = new Scanner(stream)) {
            int nrows = scanner.nextInt();
            int ncols = scanner.nextInt();
            int nz = scanner.nextInt();

            int[] colIndex = new int[ncols + 1];
            int[] rowIndex = new int[nz];
            double[] data = new double[nz];
            for (int i = 0; i <= ncols; i++) {
                colIndex[i] = scanner.nextInt() - 1;
            }
            for (int i = 0; i < nz; i++) {
                rowIndex[i] = scanner.nextInt() - 1;
            }
            for (int i = 0; i < nz; i++) {
                data[i] = scanner.nextDouble();
            }

            return new SparseMatrix(nrows, ncols, data, rowIndex, colIndex);
        }
    }
}
