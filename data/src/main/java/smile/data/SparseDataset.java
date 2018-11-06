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
package smile.data;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.ParseException;
import java.util.Collection;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import smile.math.Math;
import smile.math.SparseArray;
import smile.math.matrix.SparseMatrix;

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
                sum += Math.sqr(e.x);
            }

            sum = Math.sqrt(sum);

            for (SparseArray.Entry e : x) {
                e.x /= sum;
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
                e.x /= sum;
            }
        });
    }

    /**
     * Convert into Harwell-Boeing column-compressed sparse matrix format.
     */
    SparseMatrix toSparseMatrix();

    /**
     * Returns a default implementation of SparseDataset from a collection.
     *
     * @data Each row is a data item which are the indices of nonzero elements.
     */
    static SparseDataset of(Collection<SparseArray> data) {
        return new SparseDatasetImpl(data);
    }

    /**
     * Parses spare dataset in coordinate triple tuple list format.
     * Coordinate file stores a list of (row, column, value) tuples.
     *
     * @param path the input file path.
     *
     * @throws java.io.IOException
     */
    static SparseDataset from(String path) throws IOException, ParseException {
        return from(path, 0);
    }

    /**
     * Parses spare dataset in coordinate triple tuple list format.
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
     * Optionally, there may be 2 header lines
     * <pre>
     * D    // The number of instances
     * W    // The number of attributes
     * </pre>
     * or 3 header lines
     * <pre>
     * D    // The number of instances
     * W    // The number of attributes
     * N    // The total number of nonzero items in the dataset.
     * </pre>
     * These header lines will be ignored.
     *
     * @param path the input file path.
     * @param arrayIndexOrigin the starting index of array. By default, it is
     * 0 as in C/C++ and Java. But it could be 1 to parse data produced
     * by other programming language such as Fortran.
     *
     * @exception IOException if stream to file cannot be read or closed.
     * @exception ParseException if an index is not an integer or the value is not a double.
     */
    static SparseDataset from(String path, int arrayIndexOrigin) throws IOException, ParseException {
        try (Stream<String> stream = Files.lines(Paths.get(path))) {
            List<SparseArray> rows = new ArrayList<>();
            stream.forEach(line -> {
                String[] tokens = line.trim().split("\\s+");
                if (tokens.length != 3) {
                    return; // Silent on header rows
                }

                int i = Integer.parseInt(tokens[0]) - arrayIndexOrigin;
                int j = Integer.parseInt(tokens[1]) - arrayIndexOrigin;
                double x = Double.parseDouble(tokens[2]);

                if (i >= rows.size() || rows.get(i) == null) {
                    rows.set(i, new SparseArray());
                }

                SparseArray row = rows.get(i);
                row.set(j, x);
            });

            return new SparseDatasetImpl(rows);
        }
    }
}
