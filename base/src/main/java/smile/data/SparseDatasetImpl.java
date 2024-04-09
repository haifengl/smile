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
class SparseDatasetImpl<T> implements SparseDataset<T> {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(SparseDatasetImpl.class);

    /**
     * The data objects.
     */
    private final ArrayList<SampleInstance<SparseArray, T>> instances;
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
    public SparseDatasetImpl(Collection<SampleInstance<SparseArray, T>> data) {
        this(data, 1 + data.stream().flatMap(instance -> instance.x().stream()).mapToInt(e -> e.i).max().orElse(0));
    }

    /**
     * Constructor.
     * @param data The sample instances.
     * @param ncol The number of columns.
     */
    public SparseDatasetImpl(Collection<SampleInstance<SparseArray, T>> data, int ncol) {
        this.instances = new ArrayList<>(data);
        this.ncol = ncol;
        colSize = new int[ncol];

        for (var instance : data) {
            var x = instance.x();
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
        return instances.size();
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
    public SampleInstance<SparseArray, T> get(int i) {
        return instances.get(i);
    }

    @Override
    public Stream<SampleInstance<SparseArray, T>> stream() {
        return instances.stream();
    }

    @Override
    public Iterator<SampleInstance<SparseArray, T>> iterator() {
        return instances.iterator();
    }
}
