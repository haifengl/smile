/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * SMILE is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with SMILE. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.data;

import java.io.IOException;
import java.util.*;
import java.util.stream.Stream;
import smile.math.MathEx;
import smile.tensor.SparseMatrix;

/**
 * Binary sparse dataset. Each item is stored as an integer array, which
 * are the indices of nonzero elements in ascending order.
 *
 * @param <T> the target type.
 *
 * @author Haifeng Li
 */
public class BinarySparseDataset<T> extends SimpleDataset<int[], T> {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(BinarySparseDataset.class);

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
    private final int[] colSize;

    /**
     * Constructor.
     * @param data The sample instances.
     */
    public BinarySparseDataset(Collection<SampleInstance<int[], T>> data) {
        super(data);

        int p = 0;
        for (var instance : instances) {
            p = Math.max(p, MathEx.max(instance.x()));
        }
        ncol = p + 1;
        colSize = new int[ncol];

        for (var instance : instances) {
            var x = instance.x();
            Arrays.sort(x);

            int prev = -1; // index of previous element
            for (int xi : x) {
                if (xi < 0) {
                    throw new IllegalArgumentException(String.format("Negative index of nonzero element: %d", xi));
                }

                if (xi == prev) {
                    logger.warn("Ignore duplicated indices: {} in {}", xi, Arrays.toString(x));
                } else {
                    colSize[xi]++;
                    n++;
                    prev = xi;
                }
            }
        }
    }

    /**
     * Returns the number of nonzero entries.
     * @return the number of nonzero entries.
     */
    public int length() {
        return n;
    }

    /**
     * Returns the number of columns.
     * @return the number of columns.
     */
    public int ncol() {
        return ncol;
    }

    /**
     * Returns the binary value at entry (i, j) by binary search.
     * @param i the row index.
     * @param j the column index.
     * @return the binary value of cell.
     */
    public int get(int i, int j) {
        if (i < 0 || i >= size()) {
            throw new IllegalArgumentException("Invalid index: i = " + i);
        }

        int[] x = get(i).x();
        if (x.length == 0) {
            return 0;
        }
        
        int low = 0;
        int high = x.length - 1;
        int mid = (low + high) / 2;
        
        while (j != x[mid] && low <= high) {
            mid = (low + high) / 2;
            if (j < x[mid]) 
                high = mid - 1;
            else
                low = mid + 1;
        }
        
        if (j == x[mid]) {
            return 1;
        } else {
            return 0;
        }
    }

    /**
     * Returns the Harwell-Boeing column-compressed sparse matrix.
     * @return the sparse matrix.
     */
    public SparseMatrix toMatrix() {
        int[] pos = new int[ncol];
        int[] colIndex = new int[ncol + 1];
        for (int i = 0; i < ncol; i++) {
            colIndex[i + 1] = colIndex[i] + colSize[i];
        }

        int nrow = instances.size();
        int[] rowIndex = new int[n];
        double[] x = new double[n];

        for (int i = 0; i < nrow; i++) {
            for (int j : instances.get(i).x()) {
                int k = colIndex[j] + pos[j];

                rowIndex[k] = i;
                x[k] = 1;
                pos[j]++;
            }
        }

        return new SparseMatrix(nrow, ncol, x, rowIndex, colIndex);
    }

    /**
     * Returns a default implementation of BinarySparseDataset without targets.
     *
     * @param data Each row is a data item which are the indices of
     *             nonzero elements. Every row will be sorted into
     *             ascending order.
     * @return the sparse dataset.
     */
    public static BinarySparseDataset<Void> of(int[][] data) {
        return new BinarySparseDataset<>(Arrays.stream(data)
                .map(x -> new SampleInstance<int[], Void>(x, null))
                .toList());
    }

    /**
     * Parse a binary sparse dataset from a file, of which each line is a data
     * item which are the indices of nonzero elements.
     *
     * @param path the input file path.
     * @exception IOException if stream to file cannot be read or closed.
     * @exception NumberFormatException if an entry is not an integer.
     * @return the sparse dataset.
     */
    public static BinarySparseDataset<Void> from(java.nio.file.Path path) throws IOException, NumberFormatException {
        try (Stream<String> stream = java.nio.file.Files.lines(path)) {
            List<SampleInstance<int[], Void>> rows = stream.map(line -> {
                String[] s = line.split("\\s+");
                int[] index = new int[s.length];
                for (int i = 0; i < s.length; i++) {
                    index[i] = Integer.parseInt(s[i]);
                }
                return new SampleInstance<int[], Void>(index, null);
            }).toList();

            return new BinarySparseDataset<>(rows);
        }
    }
}
