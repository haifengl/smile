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

package smile.sort;

/**
 * Heapsort is a comparison-based sorting algorithm, and is part of the
 * selection sort family. Although somewhat slower in practice on most
 * machines than a good implementation of quicksort, it has the advantage
 * of a worst-case O(n log n) runtime. In fact, its worst case is only 20%
 * or so worse than its average running time. Heapsort is an in-place algorithm,
 * but is not a stable sort.
 *
 * @author Haifeng Li
 */
public interface HeapSort {
    /**
     * Sorts the specified array into ascending numerical order.
     * @param x the array.
     */
    static void sort(int[] x) {
        int n = x.length;
        for (int i = n / 2 - 1; i >= 0; i--)
            Sort.siftDown(x, i, n - 1);

        for (int i = n - 1; i > 0; i--) {
            Sort.swap(x, 0, i);
            Sort.siftDown(x, 0, i - 1);
        }
    }

    /**
     * Sorts the specified array into ascending numerical order.
     * @param x the array.
     */
    static void sort(float[] x) {
        int n = x.length;
        for (int i = n / 2 - 1; i >= 0; i--)
            Sort.siftDown(x, i, n - 1);

        for (int i = n - 1; i > 0; i--) {
            Sort.swap(x, 0, i);
            Sort.siftDown(x, 0, i - 1);
        }
    }

    /**
     * Sorts the specified array into ascending numerical order.
     * @param x the array.
     */
    static void sort(double[] x) {
        int n = x.length;
        for (int i = n / 2 - 1; i >= 0; i--)
            Sort.siftDown(x, i, n - 1);

        for (int i = n - 1; i > 0; i--) {
            Sort.swap(x, 0, i);
            Sort.siftDown(x, 0, i - 1);
        }
    }

    /**
     * Sorts the specified array into ascending order.
     * @param x the array.
     * @param <T> the data type of array elements.
     */
    static <T extends Comparable<? super T>> void sort(T[] x) {
        int n = x.length;
        for (int i = n / 2 - 1; i >= 0; i--)
            Sort.siftDown(x, i, n - 1);

        for (int i = n - 1; i > 0; i--) {
            Sort.swap(x, 0, i);
            Sort.siftDown(x, 0, i - 1);
        }
    }
}