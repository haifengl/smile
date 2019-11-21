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

package smile.data;

import java.io.IOException;
import java.io.LineNumberReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.ParseException;
import java.util.Collection;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import smile.math.MathEx;
import smile.math.matrix.SparseMatrix;
import smile.util.SparseArray;

/**
 * List of Lists sparse matrix format. LIL stores one list per row,
 * where each entry stores a column index and value. Typically, these
 * entries are kept sorted by column index for faster lookup.
 * This format is good for incremental matrix construction.
 * <p>
 * LIL is typically used to construct the matrix. Once the matrix is
 * constructed, it is typically converted to a format, such as Harwell-Boeing
 * column-compressed sparse matrix format, which is more efficient for matrix
 * operations.
 *
 * @author Haifeng Li
 */
public interface SparseDataset extends Dataset<SparseArray> {
    /** Returns the number of nonzero entries. */
    int nz();

    /** Returns the number of nonzero entries in column j. */
    int nz(int j);

    /**
     * Returns the number of rows.
     */
    default int nrows() {
        return size();
    }

    /**
     * Returns the number of columns.
     */
    int ncols();

    /**
     * Returns the value at entry (i, j).
     * @param i the row index.
     * @param j the column index.
     */
    default double get(int i, int j) {
        if (i < 0 || i >= size() || j < 0 || j >= ncols()) {
            throw new IllegalArgumentException("Invalid index: i = " + i + " j = " + j);
        }

        for (SparseArray.Entry e : get(i)) {
            if (e.i == j) {
                return e.x;
            }
        }

        return 0.0;
    }

    /**
     * Unitize each row so that L2 norm of x = 1.
     */
    default void unitize() {
        stream().forEach(x -> {
            double sum = 0.0;

            for (SparseArray.Entry e : x) {
                sum += MathEx.sqr(e.x);
            }

            sum = Math.sqrt(sum);

            for (SparseArray.Entry e : x) {
                e.update(e.x / sum);
            }
        });
    }

    /**
     * Unitize each row so that L1 norm of x is 1.
     */
    default void unitize1() {
        stream().forEach(x -> {
            double sum = 0.0;

            for (SparseArray.Entry e : x) {
                sum += Math.abs(e.x);
            }

            for (SparseArray.Entry e : x) {
                e.update(e.x / sum);
            }
        });
    }

    /**
     * Convert into Harwell-Boeing column-compressed sparse matrix format.
     */
    default SparseMatrix toMatrix() {
        int nz = nz();
        int ncols = ncols();

        int[] pos = new int[ncols];
        int[] colIndex = new int[ncols + 1];
        for (int i = 0; i < ncols; i++) {
            colIndex[i + 1] = colIndex[i] + nz(i);
        }

        int nrows = size();
        int[] rowIndex = new int[nz];
        double[] x = new double[nz];

        for (int i = 0; i < nrows; i++) {
            for (SparseArray.Entry e : get(i)) {
                int j = e.i;
                int k = colIndex[j] + pos[j];

                rowIndex[k] = i;
                x[k] = e.x;
                pos[j]++;
            }
        }

        return new SparseMatrix(nrows, ncols, x, rowIndex, colIndex);
    }

    /**
     * Returns a default implementation of SparseDataset from a collection.
     *
     * @param data sparse arrays.
     */
    static SparseDataset of(Stream<SparseArray> data) {
        return of(data.collect(Collectors.toList()));
    }

    /**
     * Returns a default implementation of SparseDataset from a collection.
     *
     * @param data sparse arrays.
     */
    static SparseDataset of(Collection<SparseArray> data) {
        return new SparseDatasetImpl(data);
    }

    /**
     * Returns a default implementation of SparseDataset from a collection.
     *
     * @param data sparse arrays.
     * @param ncols the number of columns.
     */
    static SparseDataset of(Collection<SparseArray> data, int ncols) {
        return new SparseDatasetImpl(data, ncols);
    }

    /** Strips the response variable and returns a SparseDataset. */
    static SparseDataset of(Dataset<Instance<SparseArray>> data) {
        return of(data.stream().map(i -> i.x()).collect(Collectors.toList()));
    }

    /**
     * Parses spare dataset in coordinate triple tuple list format.
     * Coordinate file stores a list of (row, column, value) tuples.
     *
     * @param path the input file path.
     *
     * @throws java.io.IOException
     */
    static SparseDataset from(Path path) throws IOException, ParseException {
        return from(path, 0);
    }

    /**
     * Reads spare dataset in coordinate triple tuple list format.
     * Coordinate file stores a list of (row, column, value) tuples:
     * <pre>
     * instanceID attributeID value
     * instanceID attributeID value
     * instanceID attributeID value
     * instanceID attributeID value
     * ...
     * instanceID attributeID value
     * instanceID attributeID value
     * instanceID attributeID value
     * </pre>
     * Ideally, the entries are sorted (by row index, then column index) to
     * improve random access times. This format is good for incremental matrix
     * construction.
     * <p>
     * In addition, there may a header line
     * <pre>
     * D W N   // The number of rows, columns and nonzero entries.
     * </pre>
     * or 3 header lines
     * <pre>
     * D    // The number of rows
     * W    // The number of columns
     * N    // The total number of nonzero entries in the dataset.
     * </pre>
     *
     * @param path the input file path.
     * @param arrayIndexOrigin the starting index of array. By default, it is
     * 0 as in C/C++ and Java. But it could be 1 to parse data produced
     * by other programming language such as Fortran.
     *
     * @exception IOException if stream to file cannot be read or closed.
     * @exception ParseException if an index is not an integer or the value is not a double.
     */
    static SparseDataset from(Path path, int arrayIndexOrigin) throws IOException, ParseException {
        try (LineNumberReader reader = new LineNumberReader(Files.newBufferedReader(path));
             Scanner scanner = new Scanner(reader)) {
            int nrow = scanner.nextInt();
            int ncol = scanner.nextInt();
            int nz = scanner.nextInt();
            List<SparseArray> rows = new ArrayList<>(nrow);
            for (int i = 0; i < nrow; i++) {
                rows.add(new SparseArray());
            }

            // read the EOL of header line(s).
            scanner.nextLine();
            do {
                String line = scanner.nextLine();

                String[] tokens = line.trim().split("\\s+");
                if (tokens.length != 3) {
                    throw new ParseException("Invalid line: " + line, reader.getLineNumber());
                }

                int i = Integer.parseInt(tokens[0]) - arrayIndexOrigin;
                int j = Integer.parseInt(tokens[1]) - arrayIndexOrigin;
                double x = Double.parseDouble(tokens[2]);

                SparseArray row = rows.get(i);
                row.set(j, x);
            } while (scanner.hasNextLine());

            return of(rows);
        }
    }
}
