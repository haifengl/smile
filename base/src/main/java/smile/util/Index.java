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
 * along with Smile. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.util;

import java.io.Serializable;
import java.util.Arrays;
import java.util.stream.IntStream;

/**
 * Immutable sequence used for indexing.
 *
 * @author Karl Li
 */
public interface Index extends Serializable {
    /**
     * Returns the number of elements in the index.
     * @return the number of elements in the index.
     */
    int size();

    /**
     * Returns the index to underlying data.
     * @param i the index to data element.
     * @return the index to underlying data.
     */
    int apply(int i);

    /**
     * Returns an integer stream of elements in the index.
     * @return an integer stream of elements in the index.
     */
    IntStream stream();

    /**
     * Returns an integer array of elements in the index.
     * @return an integer array of elements in the index.
     */
    default int[] toArray() {
        return stream().toArray();
    }

    /**
     * Flatten the index of index. Returns the index to the underlying data.
     * @param index the index to the elements in this index.
     * @return the index to the underlying data.
     */
    default Index flatten(Index index) {
        int[] parent = stream().toArray();
        int[] child = index.stream().map(i -> parent[i]).toArray();
        return of(child);
    }

    /**
     * Returns the index of multiple elements in a dimension.
     *
     * @param indices the indices of multiple elements.
     * @return the index.
     */
    static Index of(int... indices) {
        return new Index() {
            @Override
            public int size() {
                return indices.length;
            }

            @Override
            public int apply(int i) {
                return indices[i];
            }

            @Override
            public IntStream stream() {
                return Arrays.stream(indices);
            }
        };
    }

    /**
     * Returns the index of multiple elements in a dimension.
     *
     * @param mask the boolean flags to select multiple elements.
     *             The length of array should match that of the
     *             corresponding dimension of tensor.
     * @return the index.
     */
    static Index of(boolean... mask) {
        int[] indices = IntStream.range(0, mask.length)
                .filter(i -> mask[i])
                .toArray();
        return of(indices);
    }

    /**
     * Returns the range index for [start, end) with incremental step 1.
     *
     * @param start the inclusive start index.
     * @param end the exclusive end index.
     * @return the range index.
     */
    static Index range(int start, int end) {
        return new Index() {
            @Override
            public int size() {
                return end - start;
            }

            @Override
            public int apply(int i) {
                return start + i;
            }

            @Override
            public IntStream stream() {
                return IntStream.range(start, end);
            }
        };
    }

    /**
     * Returns the range index for [start, end) with the given incremental step.
     *
     * @param start the inclusive start index.
     * @param end the exclusive end index.
     * @param step the incremental step.
     * @return the range index.
     */
    static Index range(Integer start, Integer end, Integer step) {
        int size = (end - start + step - 1) / step;
        return new Index() {
            @Override
            public int size() {
                return size;
            }

            @Override
            public int apply(int i) {
                return start + i * step;
            }

            @Override
            public IntStream stream() {
                return IntStream.iterate(start, i -> i + step).limit(size);
            }
        };
    }
}
