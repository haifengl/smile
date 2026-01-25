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
package smile.sort;

import java.util.Arrays;

/**
 * This class tracks the smallest values seen thus far in a stream of values.
 * This implements a single-pass selection for large data sets. That is,
 * we have a stream of input values, each of which we get to see only once.
 * We want to be able to report at any time, say after n values, the
 * i-<i>th</i> smallest value see so far.
 *
 * @param <T> the data type of stream elements.
 *
 * @author Haifeng Li
 */
public class HeapSelect<T extends Comparable<? super T>> {

    /**
     * The heap size.
     */
    private final int k;
    /**
     * The heap array. The root is at position 1.
     */
    private final T[] heap;
    /**
     * The number of objects that have been added into heap.
     */
    private int n;
    /**
     * True if the heap is fully sorted.
     */
    private boolean sorted;

    /**
     * Constructor.
     * @param clazz the data type of elements.
     * @param k the size of heap.
     */
    @SuppressWarnings("unchecked")
    public HeapSelect(Class<?> clazz, int k) {
        this.heap = (T[]) java.lang.reflect.Array.newInstance(clazz, k+1);
        this.k = k;
        n = 0;
        sorted = false;
    }

    /**
     * Returns the number of objects that have been added into heap.
     * @return the number of objects that have been added into heap.
     */
    public int size() {
        return n;
    }

    /**
     * Returns the array back the heap.
     * @return the array back the heap.
     */
    public T[] toArray() {
        return Arrays.copyOfRange(heap, 1, Math.min(k, n)+1);
    }

    /**
     * Returns the array back the heap.
     * @param a the array to copy into.
     * @return the array back the heap.
     */
    public T[] toArray(T[] a) {
        System.arraycopy(heap, 1, a, 0, Math.min(k, n));
        return a;
    }

    /**
     * Assimilate a new value from the stream.
     * @param datum a new value.
     */
    public void add(T datum) {
        sorted = false;
        if (n < k) {
            heap[++n] = datum;
            Sort.siftUp(heap, n);
        } else {
            n++;
            if (datum.compareTo(heap[1]) < 0) {
                heap[1] = datum;
                Sort.siftDown(heap, 1, k);
            }
        }
    }

    /**
     * In case of avoiding creating new objects frequently, one may check and
     * update the peek object directly and call this method to sort the internal
     * array in heap order.
     */
    public void siftDown() {
        Sort.siftDown(heap, 1, Math.min(k, n));
    }

    /**
     * Returns the k-<i>th</i> smallest value seen so far.
     * @return the k-<i>th</i> smallest value seen so far.
     */
    public T peek() {
        return heap[1];
    }

    /**
     * Returns the i-<i>th</i> smallest value seen so far. i = 0 returns the smallest
     * value seen, i = 1 the second largest, ..., i = k-1 the last position
     * tracked. Also, i must be less than the number of previous assimilated.
     *
     * @param i the ordinal index of smallest values.
     * @return the i-<i>th</i> smallest value.
     */
    public T get(int i) {
        int len = Math.min(k, n);
        if (i > len - 1) {
            throw new IllegalArgumentException("HeapSelect i is greater than the number of data received so far.");
        }

        if (i == len-1) {
            return heap[1];
        }

        sort();
        return heap[len-i];
    }

    /**
     * Sort the smallest values.
     */
    public void sort() {
        if (!sorted) {
            sort(heap, 1, Math.min(k,n));
            sorted = true;
        }
    }

    /**
     * Place the array in max-heap order. Note that the array is not fully sorted.
     */
    private static <T extends Comparable<? super T>> void heapify(T[] arr, int n) {
        for (int i = n / 2; i >= 1; i--) {
            Sort.siftDown(arr, i, n);
        }
    }

    /**
     * Sorts the specified array into descending order. It is based on Shell
     * sort, which is very efficient because the array is almost sorted by
     * heapifying.
     */
    private static <T extends Comparable<? super T>> void sort(T[] a, int l, int r) {
        int h;
        for (h = 1; h <= (r-l)/9; h = 3*h+1);
        for (; h > 0; h /= 3) {
            for (int i = l + h; i <= r; i++) {
                int j = i;
                T v = a[i];
                while (j >= l+h && a[j-h].compareTo(v) < 0) {
                    a[j] = a[j-h];
                    j -= h;
                    a[j] = v;
                }
            }
        }
    }
}