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

import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.stream.Stream;
import smile.math.MathEx;
import smile.math.matrix.SparseMatrix;

/**
 * Binary sparse dataset. Each item is stored as an integer array, which
 * are the indices of nonzero elements in ascending order.
 *
 * @param <T> the target type.
 *
 * @author Haifeng Li
 */
class BinarySparseDatasetImpl<T> implements BinarySparseDataset<T> {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(BinarySparseDatasetImpl.class);

    /**
     * The sample instances.
     */
    private final ArrayList<Instance<int[], T>> instances;
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
    public BinarySparseDatasetImpl(Collection<Instance<int[], T>> data) {
        this.instances = new ArrayList<>(data);

        int p = 0;
        for (var instance : instances) {
            p = Math.max(p, MathEx.max(instance.x));
        }
        ncol = p + 1;
        colSize = new int[ncol];

        for (var instance : instances) {
            var x = instance.x;
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
        return instances.size();
    }

    @Override
    public int length() {
        return n;
    }

    @Override
    public int ncol() {
        return ncol;
    }

    @Override
    public Instance<int[], T> get(int i) {
        return instances.get(i);
    }

    @Override
    public Stream<Instance<int[], T>> stream() {
        return instances.stream();
    }

    @Override
    public Iterator<Instance<int[], T>> iterator() {
        return instances.iterator();
    }

    @Override
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
            for (int j : instances.get(i).x) {
                int k = colIndex[j] + pos[j];

                rowIndex[k] = i;
                x[k] = 1;
                pos[j]++;
            }
        }

        return new SparseMatrix(nrow, ncol, x, rowIndex, colIndex);
    }
}
