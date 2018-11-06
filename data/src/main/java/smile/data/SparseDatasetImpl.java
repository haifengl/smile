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

import java.util.*;
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
class SparseDatasetImpl implements SparseDataset {
    /**
     * The data objects.
     */
    private SparseArray[] data;
    /**
     * The number of nonzero entries.
     */
    private int n;
    /**
     * The number of columns.
     */
    private int ncols;
    /**
     * The number of nonzero entries in each column.
     */
    private int[] colSize;

    /**
     * Constructor.
     * @data Each row is a data item which are the indices of nonzero elements.
     *       Every row will be sorted into ascending order.
     */
    public SparseDatasetImpl(Collection<SparseArray> data) {
        this.data = data.toArray(new SparseArray[data.size()]);

        colSize = new int[ncols];
        for (SparseArray x : data) {
            x.sort(); // sort array index into ascending order.

            n += x.size();
            int i = -1; // index of previous element
            for (SparseArray.Entry e : x) {
                if (e.i < 0) {
                    throw new IllegalArgumentException(String.format("Negative index of nonzero element: %d", e.i));
                }

                if (ncols <= e.i) {
                    ncols = e.i + 1;
                }

                colSize[e.i]++;
                if (e.i == i) {
                    throw new IllegalArgumentException(String.format("Duplicated indices of nonzero elements: %d in a row", e.i));
                }

                i = e.i;
            }
        }
    }

    @Override
    public int size() {
        return data.length;
    }

    @Override
    public int ncols() {
        return ncols;
    }

    @Override
    public SparseArray get(int i) {
        return data[i];
    }

    @Override
    public Stream<SparseArray> stream() {
        return Arrays.stream(data);
    }

    @Override
    public SparseMatrix toSparseMatrix() {
        int[] pos = new int[ncols];
        int[] colIndex = new int[ncols + 1];
        for (int i = 0; i < ncols; i++) {
            colIndex[i + 1] = colIndex[i] + colSize[i];
        }

        int nrows = size();
        int[] rowIndex = new int[n];
        double[] x = new double[n];

        for (int i = 0; i < nrows; i++) {
            for (SparseArray.Entry e : data[i]) {
                int j = e.i;
                int k = colIndex[j] + pos[j];

                rowIndex[k] = i;
                x[k] = e.x;
                pos[j]++;
            }
        }

        return new SparseMatrix(nrows, ncols, x, rowIndex, colIndex);
    }
}
