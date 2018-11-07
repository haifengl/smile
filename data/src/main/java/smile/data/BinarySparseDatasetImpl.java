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

import java.util.Arrays;
import java.util.Collection;
import java.util.stream.Stream;
import smile.math.Math;
import smile.math.matrix.SparseMatrix;

/**
 * Binary sparse dataset. Each item is stored as an integer array, which
 * are the indices of nonzero elements in ascending order.
 *
 * @author Haifeng Li
 */
class BinarySparseDatasetImpl implements BinarySparseDataset {
    /**
     * The data objects.
     */
    private int[][] data;
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
    public BinarySparseDatasetImpl(Collection<int[]> data) {
        this.data = data.toArray(new int[data.size()][]);

        int min = Math.min(this.data);
        if (min < 0) {
            throw new IllegalArgumentException(String.format("Negative index of nonzero element: %d", min));
        }

        ncols = Math.max(this.data) + 1;
        colSize = new int[ncols];
        for (int[] x : data) {
            n += x.length;
            Arrays.sort(x);
            colSize[x[0]]++;
            for (int i = 1; i < x.length; i++) {
                colSize[x[i]]++;
                if (x[i] == x[i - 1]) {
                    throw new IllegalArgumentException(String.format("Duplicated indices of nonzero elements: %d in a row", x[i]));
                }
            }
        }
    }

    @Override
    public int size() {
        return data.length;
    }

    @Override
    public int[] get(int i) {
        return data[i];
    }

    @Override
    public int ncols() {
        return ncols;
    }

    @Override
    public Stream<int[]> stream() {
        return Arrays.stream(data);
    }

    @Override
    public SparseMatrix toMatrix() {
        int[] pos = new int[ncols];
        int[] colIndex = new int[ncols + 1];
        for (int i = 0; i < ncols; i++) {
            colIndex[i + 1] = colIndex[i] + colSize[i];
        }

        int nrows = data.length;
        int[] rowIndex = new int[n];
        double[] x = new double[n];

        for (int i = 0; i < nrows; i++) {
            for (int j : data[i]) {
                int k = colIndex[j] + pos[j];

                rowIndex[k] = i;
                x[k] = 1;
                pos[j]++;
            }
        }

        return new SparseMatrix(nrows, ncols, x, rowIndex, colIndex);
    }
}
