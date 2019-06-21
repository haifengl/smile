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

import java.util.Arrays;
import java.util.Collection;
import java.util.stream.Stream;
import smile.math.MathEx;
import smile.math.matrix.SparseMatrix;

/**
 * Binary sparse dataset. Each item is stored as an integer array, which
 * are the indices of nonzero elements in ascending order.
 *
 * @author Haifeng Li
 */
class BinarySparseDatasetImpl implements BinarySparseDataset {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(BinarySparseDatasetImpl.class);

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

        ncols = MathEx.max(this.data) + 1;
        colSize = new int[ncols];
        for (int[] x : this.data) {
            Arrays.sort(x);

            int prev = -1; // index of previous element
            for (int xi : x) {
                if (xi < 0) {
                    throw new IllegalArgumentException(String.format("Negative index of nonzero element: %d", xi));
                }

                if (xi == prev) {
                    logger.warn(String.format("Ignore duplicated indices: %d in [%s]", xi, Arrays.toString(x)));
                } else {
                    colSize[xi]++;
                    n++;
                    prev = xi;
                }
            }
        }
    }

    @Override
    public int size() {
        return data.length;
    }

    @Override
    public int length() {
        return n;
    }

    @Override
    public int ncols() {
        return ncols;
    }

    @Override
    public int[] get(int i) {
        return data[i];
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
