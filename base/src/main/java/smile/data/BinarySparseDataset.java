/*
 * Copyright (c) 2010-2021 Haifeng Li. All rights reserved.
 *
 * Smile is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Smile is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Smile.  If not, see <https://www.gnu.org/licenses/>.
 */

package smile.data;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Binary sparse dataset. Each item is stored as an integer array, which
 * are the indices of nonzero elements in ascending order.
 *
 * @param <T> the target type.
 *
 * @author Haifeng Li
 */
public interface BinarySparseDataset<T> extends Dataset<int[], T> {
    /**
     * Returns the number of nonzero entries.
     * @return the number of nonzero entries.
     */
    int length();

    /**
     * Returns the number of columns.
     * @return the number of columns.
     */
    int ncol();
    
    /**
     * Returns the binary value at entry (i, j) by binary search.
     * @param i the row index.
     * @param j the column index.
     * @return the binary value of cell.
     */
    default int get(int i, int j) {
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
    smile.math.matrix.SparseMatrix toMatrix();

    /**
     * Returns a default implementation of BinarySparseDataset.
     *
     * @param data The sample instances.
     * @param <T> the target type.
     * @return the sparse dataset.
     */
    static <T> BinarySparseDataset<T> of(Collection<SampleInstance<int[], T>> data) {
        return new BinarySparseDatasetImpl<>(data);
    }

    /**
     * Returns a default implementation of BinarySparseDataset without targets.
     *
     * @param data Each row is a data item which are the indices of
     *             nonzero elements. Every row will be sorted into
     *             ascending order.
     * @return the sparse dataset.
     */
    static BinarySparseDataset<Void> of(int[][] data) {
        return new BinarySparseDatasetImpl<>(Arrays.stream(data)
                .map(x -> new SampleInstance<int[], Void>(x, null))
                .collect(Collectors.toList()));
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
    static BinarySparseDataset<Void> from(java.nio.file.Path path) throws IOException, NumberFormatException {
        try (Stream<String> stream = java.nio.file.Files.lines(path)) {
            List<SampleInstance<int[], Void>> rows = stream.map(line -> {
                String[] s = line.split("\\s+");
                int[] index = new int[s.length];
                for (int i = 0; i < s.length; i++) {
                    index[i] = Integer.parseInt(s[i]);
                }
                return new SampleInstance<int[], Void>(index, null);
            }).collect(java.util.stream.Collectors.toList());

            return new BinarySparseDatasetImpl<>(rows);
        }
    }
}
