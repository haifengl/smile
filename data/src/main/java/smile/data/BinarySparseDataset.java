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
import java.text.ParseException;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Binary sparse dataset. Each item is stored as an integer array, which
 * are the indices of nonzero elements in ascending order.
 *
 * @author Haifeng Li
 */
public interface BinarySparseDataset extends Dataset<int[]> {
    /** Returns the number of nonzero entries. */
    int length();

    /**
     * Returns the number of columns.
     */
    int ncols();
    
    /**
     * Returns the binary value at entry (i, j) by binary search.
     * @param i the row index.
     * @param j the column index.
     */
    default int get(int i, int j) {
        if (i < 0 || i >= size()) {
            throw new IllegalArgumentException("Invalid index: i = " + i);
        }

        int[] x = get(i);
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
     */
    smile.math.matrix.SparseMatrix toMatrix();

    /**
     * Returns a default implementation of BinarySparseDataset from a collection.
     *
     * @data Each row is a data item which are the indices of nonzero elements.
     *       Every row will be sorted into ascending order.
     */
    static BinarySparseDataset of(Collection<int[]> data) {
        return new BinarySparseDatasetImpl(data);
    }

    /**
     * Parse a binary sparse dataset from a file, of which each line is a data
     * item which are the indices of nonzero elements.
     *
     * @param path the input file path.
     * @exception IOException if stream to file cannot be read or closed.
     * @exception ParseException if an entry is not an integer.
     */
    static BinarySparseDataset from(java.nio.file.Path path) throws IOException, ParseException {
        try (Stream<String> stream = java.nio.file.Files.lines(path)) {
            List<int[]> rows = stream.map(line -> {
                String[] s = line.split("\\s+");
                int[] index = new int[s.length];
                for (int i = 0; i < s.length; i++) {
                    index[i] = Integer.parseInt(s[i]);
                }
                return index;
            }).collect(Collectors.toList());

            return new BinarySparseDatasetImpl(rows);
        }
    }
}
