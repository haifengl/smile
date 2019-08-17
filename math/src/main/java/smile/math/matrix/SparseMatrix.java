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
import java.util.Spliterator;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import smile.math.Math;

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
 * streaming interface is more flexible. Here are some benchmarks that were
 * produced using jmh (and allowing access to internal data structures):
 * <verbatim>
 * Benchmark                                       Mode  Cnt        Score       Error   Units
 * IteratorSpeed.timeDirect                        avgt    5   429888.246 ±  3819.232   ns/op
 * IteratorSpeed.timeDirect:·gc.alloc.rate         avgt    5       ≈ 10⁻⁴              MB/sec
 * IteratorSpeed.timeDirect:·gc.alloc.rate.norm    avgt    5        0.088 ±     0.001    B/op
 * IteratorSpeed.timeDirect:·gc.count              avgt    5          ≈ 0              counts
 * IteratorSpeed.timeDirect:·stack                 avgt               NaN                 ---
 * IteratorSpeed.timeIterator                      avgt    5   430718.537 ±  7831.509   ns/op
 * IteratorSpeed.timeIterator:·gc.alloc.rate       avgt    5        0.028 ±     0.001  MB/sec
 * IteratorSpeed.timeIterator:·gc.alloc.rate.norm  avgt    5       16.089 ±     0.011    B/op
 * IteratorSpeed.timeIterator:·gc.count            avgt    5          ≈ 0              counts
 * IteratorSpeed.timeIterator:·stack               avgt               NaN                 ---
 * IteratorSpeed.timeStream                        avgt    5  1032370.658 ± 55295.704   ns/op
 * IteratorSpeed.timeStream:·gc.alloc.rate         avgt    5        0.077 ±     0.004  MB/sec
 * IteratorSpeed.timeStream:·gc.alloc.rate.norm    avgt    5      104.210 ±     0.011    B/op
 * IteratorSpeed.timeStream:·gc.count              avgt    5          ≈ 0              counts
 * IteratorSpeed.timeStream:·stack                 avgt               NaN                 ---
 * </verbatim>
 * The three cases are timeDirect for a direct loop over internal data structures,
 * timeIterator for #foreachNonZero and timeStream for the streaming equivalent form. The
 * timeIterator case is at most a few percent slower than the direct loops while the stream
 * is about 2-3 times slower. Note that the JVM is clever enough to optimize away the
 * creation of temporary objects in the streaming idiom.
 * </p>
 *
 * @author Haifeng Li
 */
public class SparseMatrix implements Matrix, MatrixMultiplication<SparseMatrix, SparseMatrix> {
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
        this(D, 100 * Math.EPSILON);
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
    public int size() {
        return colIndex[ncols];
    }

    /**
     * Calls a lambda with each non-zero value. This will be a bit faster than using a stream
     * because we can avoid boxing up the coordinates of the element being processed, but it
     * will be considerably less general.
     * <p>
     * Note that the action could be called on values that are either effectively or actually
     * zero. The only guarantee is that no values that are known to be zero based on the
     * structure of the matrix will be processed.
     *
     * @param action Action to perform on each non-zero, typically this is a lambda.
     */
    public void foreachNonzero(MatrixElementConsumer action) {
        for (int j = 0; j < ncols; j++) {
            for (int k = colIndex[j]; k < colIndex[j + 1]; k++) {
                int i = rowIndex[k];
                action.accept(i, j, x[k]);
            }
        }
    }


    /**
     * Calls a lambda with each non-zero value. This will be a bit faster than using a stream
     * because we can avoid boxing up the coordinates of the element being processed, but it
     * will be considerably less general.
     * <p>
     * Note that the action could be called on values that are either effectively or actually
     * zero. The only guarantee is that no values that are known to be zero based on the
     * structure of the matrix will be processed.
     *
     * @param startColumn The first column to scan.
     * @param endColumn   One past the last column to scan.
     * @param consumer      Action to perform on each non-zero, typically this is a lambda.
     */
    public void foreachNonzero(int startColumn, int endColumn, MatrixElementConsumer consumer) {
        if (startColumn < 0 || startColumn >= ncols) {
            throw new IllegalArgumentException("Start column must be in range [0,ncols)");
        }
        if (endColumn < 0 || endColumn > ncols) {
            throw new IllegalArgumentException("End column must be in range [startColumn,ncols]");
        }

        for (int j = startColumn; j < endColumn; j++) {
            for (int k = colIndex[j]; k < colIndex[j + 1]; k++) {
                int i = rowIndex[k];
                consumer.accept(i, j, x[k]);
            }
        }
    }

    /**
     * Provides a stream over all of the non-zero elements of a sparse matrix.
     */
    public Stream<Entry> nonzeros() {
        return StreamSupport.stream(new SparseMatrixSpliterator(0, ncols), false);
    }

    /**
     * Provides a stream over all of the non-zero elements of range of columns of a sparse matrix.
     *
     * @param startColumn The first column to scan
     * @param endColumn   The first column after the ones that we should scan.
     */
    public Stream<Entry> nonzeros(int startColumn, int endColumn) {
        return StreamSupport.stream(new SparseMatrixSpliterator(startColumn, endColumn), false);
    }

    /**
     * Provides a spliterator for access to a sparse matrix in column major order.
     * <p>
     * This is exposed to facilitate lower level access to the stream API for a matrix.
     */
    private class SparseMatrixSpliterator implements Spliterator<Entry> {
        private int col;      // current column, advanced on split or traversal
        private int index;    // current element within column
        private final int fence; // one past the last column to process

        SparseMatrixSpliterator(int col, int fence) {
            this.col = col;
            this.index = colIndex[col];
            this.fence = fence;
        }

        public void forEachRemaining(Consumer<? super Entry> action) {
            for (; col < fence; col++) {
                for (; index < colIndex[col + 1]; index++) {
                    action.accept(new Entry(rowIndex[index], col, x[index], index));
                }
            }
        }

        public boolean tryAdvance(Consumer<? super Entry> action) {
            if (col < fence) {
                while (col < fence && index >= colIndex[col + 1]) {
                    col++;
                }
                if (col < fence) {
                    action.accept(new Entry(rowIndex[index], col, x[index], index));
                    index++;
                    return true;
                } else {
                    return false;
                }
            } else {
                // cannot advance
                return false;
            }
        }

        public Spliterator<Entry> trySplit() {
            int lo = col; // divide range in half, assuming roughly constant size of all columns
            int mid = ((lo + fence) >>> 1) & ~1; // force midpoint to be even
            if (lo < mid) { // split out left half
                col = mid; // reset this Spliterator's origin
                return new SparseMatrixSpliterator(lo, mid);
            } else {
                // too small to split
                return null;
            }
        }

        public long estimateSize() {
            return (long) (colIndex[fence] - colIndex[col]);
        }

        public int characteristics() {
            return ORDERED | SIZED | IMMUTABLE | SUBSIZED;
        }
    }

    /**
     * Encapsulates an entry in a matrix for use in streaming. As typical stream object,
     * this object is immutable. But we can update the corresponding value in the matrix
     * through <code>update</code> method. This provides an efficient way to update the
     * non-zero entries of a sparse matrix.
     */
    public class Entry {
        // these fields are exposed for direct access to simplify in-lining by the JVM
        public final int row;
        public final int col;
        public final double value;

        // these are hidden due to internal dependency
        private final int index;

        /**
         * Private constructor. Only the enclosure matrix can creates
         * the instances of entry.
         */
        private Entry(int row, int col, double value, int index) {
            this.row = row;
            this.col = col;
            this.value = value;
            this.index = index;
        }

        /**
         * Update the value of entry in the matrix. Note that the field <code>value</code>
         * is final and thus not updated.
         */
        public void update(double value) {
            x[index] = value;
        }
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
        int anz = size();
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
        int n = smile.math.Math.min(nrows(), ncols());
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
}
