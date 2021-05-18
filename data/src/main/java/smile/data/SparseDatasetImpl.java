/*
 * Copyright (c) 2010-2020 Haifeng Li. All rights reserved.
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
 */

package smile.data;

import java.util.*;
import java.util.stream.Stream;
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
class SparseDatasetImpl implements SparseDataset {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(SparseDatasetImpl.class);

    /**
     * The data objects.
     */
    private final SparseArray[] data;
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
     * @param data Each row is a data item which are the indices
     *             of nonzero elements. Every row will be sorted
     *             into ascending order.
     */
    public SparseDatasetImpl(Collection<SparseArray> data) {
        this(data, 1 + data.stream().flatMap(SparseArray::stream).mapToInt(e -> e.i).max().orElse(0));
    }

    /**
     * Constructor.
     * @param data Each row is a data item which are the indices
     *             of nonzero elements. Every row will be sorted
     *             into ascending order.
     * @param ncol The number of columns.
     */
    public SparseDatasetImpl(Collection<SparseArray> data, int ncol) {
        this.data = data.toArray(new SparseArray[0]);
        this.ncol = ncol;
        colSize = new int[ncol];

        for (SparseArray x : data) {
            x.sort(); // sort array index into ascending order.

            int i = -1; // index of previous element
            for (SparseArray.Entry e : x) {
                if (e.i < 0) {
                    throw new IllegalArgumentException(String.format("Negative index of nonzero element: %d", e.i));
                }

                if (e.i == i) {
                    logger.warn(String.format("Ignore duplicated indices: %d in [%s]", e.i, x));
                } else {
                    if (ncol <= e.i) {
                        ncol = e.i + 1;
                        int[] newColSize = new int[3 * ncol / 2];
                        System.arraycopy(colSize, 0, newColSize, 0, colSize.length);
                        colSize = newColSize;
                    }

                    colSize[e.i]++;
                    n++;
                    i = e.i;
                }
            }
        }
    }

    @Override
    public int size() {
        return data.length;
    }

    @Override
    public int nz() {
        return n;
    }

    @Override
    public int nz(int j) {
        return colSize[j];
    }

    @Override
    public int ncol() {
        return ncol;
    }

    @Override
    public SparseArray get(int i) {
        return data[i];
    }

    @Override
    public Stream<SparseArray> stream() {
        return Arrays.stream(data);
    }
}
