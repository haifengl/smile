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
package smile.data;

import java.io.IOException;
import java.io.LineNumberReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.ParseException;
import java.util.*;
import smile.tensor.SparseMatrix;
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
 * @param <T> the target type.
 *
 * @author Haifeng Li
 */
public class SparseDataset<T> extends SimpleDataset<SparseArray, T> {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(SparseDataset.class);

    /**
     * The number of nonzero entries.
     */
    private int n;
    /**
     * The number of columns.
     */
    private final int ncol;
    /**
     * The number of nonzero entries in each column.
     */
    private int[] colSize;

    /**
     * Constructor.
     * @param data The sample instances.
     */
    public SparseDataset(Collection<SampleInstance<SparseArray, T>> data) {
        this(data, data.stream()
                .flatMapToInt(instance -> instance.x().indexStream())
                .max()
                .orElse(0) + 1);
    }

    /**
     * Constructor.
     * @param data The sample instances.
     * @param ncol The number of columns.
     */
    public SparseDataset(Collection<SampleInstance<SparseArray, T>> data, int ncol) {
        super(data);
        this.ncol = ncol;
        colSize = new int[ncol];

        for (var instance : data) {
            var x = instance.x();
            x.sort(); // sort array index into ascending order.

            int i = -1; // index of previous element
            for (SparseArray.Entry e : x) {
                if (e.index() < 0) {
                    throw new IllegalArgumentException(String.format("Negative index of nonzero element: %d", e.index()));
                }

                if (e.index() == i) {
                    logger.warn("Ignore duplicated indices: {} in {}", e.index(), x);
                } else {
                    if (ncol <= e.index()) {
                        ncol = e.index() + 1;
                        int[] newColSize = new int[3 * ncol / 2];
                        System.arraycopy(colSize, 0, newColSize, 0, colSize.length);
                        colSize = newColSize;
                    }

                    colSize[e.index()]++;
                    n++;
                    i = e.index();
                }
            }
        }
    }

    /**
     * Returns the number of nonzero entries.
     * @return the number of nonzero entries.
     */
    public int nz() {
        return n;
    }

    /**
     * Returns the number of nonzero entries in column j.
     * @param j the column index.
     * @return the number of nonzero entries in column j.
     */
    public int nz(int j) {
        return colSize[j];
    }

    /**
     * Returns the number of rows.
     * @return the number of rows.
     */
    public int nrow() {
        return size();
    }

    /**
     * Returns the number of columns.
     * @return the number of columns.
     */
    public int ncol() {
        return ncol;
    }

    /**
     * Returns the value at entry (i, j).
     * @param i the row index.
     * @param j the column index.
     * @return the cell value.
     */
    public double get(int i, int j) {
        if (i < 0 || i >= size() || j < 0 || j >= ncol()) {
            throw new IllegalArgumentException("Invalid index: i = " + i + " j = " + j);
        }

        return get(i).x().get(j);
    }

    /**
     * Unitize each row so that L2 norm of x = 1.
     */
    public void unitize() {
        stream().forEach(instance -> {
            double sum = Math.sqrt(instance.x().valueStream().map(x -> x*x).sum());
            instance.x().update((i, x) -> x / sum);
        });
    }

    /**
     * Unitize each row so that L1 norm of x is 1.
     */
    public void unitize1() {
        stream().forEach(instance -> {
            double sum = instance.x().valueStream().map(Math::abs).sum();
            instance.x().update((i, x) -> x / sum);
        });
    }

    /**
     * Convert into Harwell-Boeing column-compressed sparse matrix format.
     * @return the sparse matrix.
     */
    public SparseMatrix toMatrix() {
        int nz = nz();
        int ncol = ncol();

        int[] pos = new int[ncol];
        int[] colIndex = new int[ncol + 1];
        for (int i = 0; i < ncol; i++) {
            colIndex[i + 1] = colIndex[i] + nz(i);
        }

        int nrow = size();
        int[] rowIndex = new int[nz];
        double[] x = new double[nz];

        for (int i = 0; i < nrow; i++) {
            final int row = i;
            get(i).x().forEach((j, value) -> {
                int k = colIndex[j] + pos[j];
                rowIndex[k] = row;
                x[k] = value;
                pos[j]++;
            });
        }

        return new SparseMatrix(nrow, ncol, x, rowIndex, colIndex);
    }

    /**
     * Returns a default implementation of SparseDataset without targets.
     *
     * @param data sparse arrays.
     * @return the sparse dataset.
     */
    public static SparseDataset<Void> of(SparseArray[] data) {
        return new SparseDataset<>(Arrays.stream(data)
                .map(x -> new SampleInstance<SparseArray, Void>(x, null))
                .toList());
    }

    /**
     * Returns a default implementation of SparseDataset without targets.
     *
     * @param data sparse arrays.
     * @param ncol the number of columns.
     * @return the sparse dataset.
     */
    public static SparseDataset<Void> of(SparseArray[] data, int ncol) {
        return new SparseDataset<>(Arrays.stream(data)
                .map(x -> new SampleInstance<SparseArray, Void>(x, null))
                .toList(), ncol);
    }

    /**
     * Parses spare dataset in coordinate triple tuple list format.
     * Coordinate file stores a list of (row, column, value) tuples.
     *
     * @param path the input file path.
     * @throws IOException when fails to read file.
     * @throws ParseException when fails to parse data.
     * @return the sparse dataset.
     */
    public static SparseDataset<Void> from(Path path) throws IOException, ParseException {
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
     * @return the sparse dataset.
     */
    public static SparseDataset<Void> from(Path path, int arrayIndexOrigin) throws IOException, ParseException {
        try (LineNumberReader reader = new LineNumberReader(Files.newBufferedReader(path));
             Scanner scanner = new Scanner(reader)) {
            int nrow = scanner.nextInt();
            int ncol = scanner.nextInt();
            int nz = scanner.nextInt();
            SparseArray[] rows = new SparseArray[nrow];
            for (int i = 0; i < nrow; i++) {
                rows[i] = new SparseArray();
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

                SparseArray row = rows[i];
                row.set(j, x);
            } while (scanner.hasNextLine());

            return of(rows);
        }
    }
}
