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
 * Sort algorithm trait that includes useful static functions
 * such as swap and swift up/down used in many sorting algorithms.
 * 
 * @author Haifeng Li
 */
public interface Sort {

    /**
     * Swap two positions.
     * @param x the array.
     * @param i the index of array element.
     * @param j the index of other element.
     */
    static void swap(int[] x, int i, int j) {
        int a = x[i];
        x[i] = x[j];
        x[j] = a;
    }

    /**
     * Swap two positions.
     * @param x the array.
     * @param i the index of array element.
     * @param j the index of other element.
     */
    static void swap(float[] x, int i, int j) {
        float a = x[i];
        x[i] = x[j];
        x[j] = a;
    }

    /**
     * Swap two positions.
     * @param x the array.
     * @param i the index of array element.
     * @param j the index of other element.
     */
    static void swap(double[] x, int i, int j) {
        double a;
        a = x[i];
        x[i] = x[j];
        x[j] = a;
    }

    /**
     * Swap two positions.
     * @param x the array.
     * @param i the index of array element.
     * @param j the index of other element.
     */
    static void swap(Object[] x, int i, int j) {
        Object a;
        a = x[i];
        x[i] = x[j];
        x[j] = a;
    }

    /**
     * To restore the max-heap condition when a node's priority is increased.
     * We move up the heap, exchaning the node at position k with its parent
     * (at postion k/2) if necessary, continuing as long as {@code a[k/2] < a[k]} or
     * until we reach the top of the heap.
     * @param x the array.
     * @param k the index of array element.
     */
    static void siftUp(int[] x, int k) {
        while (k > 1 && x[k/2] < x[k]) {
            swap(x, k, k/2);
            k = k/2;
        }
    }

    /**
     * To restore the max-heap condition when a node's priority is increased.
     * We move up the heap, exchaning the node at position k with its parent
     * (at postion k/2) if necessary, continuing as long as {@code a[k/2] < a[k]} or
     * until we reach the top of the heap.
     * @param x the array.
     * @param k the index of array element.
     */
    static void siftUp(float[] x, int k) {
        while (k > 1 && x[k/2] < x[k]) {
            swap(x, k, k/2);
            k = k/2;
        }
    }

    /**
     * To restore the max-heap condition when a node's priority is increased.
     * We move up the heap, exchaning the node at position k with its parent
     * (at postion k/2) if necessary, continuing as long as {@code a[k/2] < a[k]} or
     * until we reach the top of the heap.
     * @param x the array.
     * @param k the index of array element.
     */
    static void siftUp(double[] x, int k) {
        while (k > 1 && x[k/2] < x[k]) {
            swap(x, k, k/2);
            k = k/2;
        }
    }

    /**
     * To restore the max-heap condition when a node's priority is increased.
     * We move up the heap, exchaning the node at position k with its parent
     * (at postion k/2) if necessary, continuing as long as {@code a[k/2] < a[k]} or
     * until we reach the top of the heap.
     * @param x the array.
     * @param k the index of array element.
     * @param <T> the data type of array elements.
     */
    static <T extends Comparable<? super T>> void siftUp(T[] x, int k) {
        while (k > 1 && x[k/2].compareTo(x[k]) < 0) {
            swap(x, k, k/2);
            k = k/2;
        }
    }

    /**
     * To restore the max-heap condition when a node's priority is decreased.
     * We move down the heap, exchanging the node at position k with the larger
     * of that node's two children if necessary and stopping when the node at
     * k is not smaller than either child or the bottom is reached. Note that
     * if n is even and k is n/2, then the node at k has only one child -- this
     * case must be treated properly.
     * @param x the array.
     * @param k the index of array element.
     * @param n the index {@code n > k}.
     */
    static void siftDown(int[] x, int k, int n) {
        while (2*k <= n) {
            int j = 2 * k;
            if (j < n && x[j] < x[j + 1]) {
                j++;
            }
            if (x[k] >= x[j]) {
                break;
            }
            swap(x, k, j);
            k = j;
        }
    }

    /**
     * To restore the max-heap condition when a node's priority is decreased.
     * We move down the heap, exchanging the node at position k with the larger
     * of that node's two children if necessary and stopping when the node at
     * k is not smaller than either child or the bottom is reached. Note that
     * if n is even and k is n/2, then the node at k has only one child -- this
     * case must be treated properly.
     * @param x the array.
     * @param k the index of array element.
     * @param n the index {@code n > k}.
     */
    static void siftDown(float[] x, int k, int n) {
        while (2*k <= n) {
            int j = 2 * k;
            if (j < n && x[j] < x[j + 1]) {
                j++;
            }
            if (x[k] >= x[j]) {
                break;
            }
            swap(x, k, j);
            k = j;
        }
    }

    /**
     * To restore the max-heap condition when a node's priority is decreased.
     * We move down the heap, exchanging the node at position k with the larger
     * of that node's two children if necessary and stopping when the node at
     * k is not smaller than either child or the bottom is reached. Note that
     * if n is even and k is n/2, then the node at k has only one child -- this
     * case must be treated properly.
     * @param x the array.
     * @param k the index of array element.
     * @param n the index {@code n > k}.
     */
    static void siftDown(double[] x, int k, int n) {
        while (2*k <= n) {
            int j = 2 * k;
            if (j < n && x[j] < x[j + 1]) {
                j++;
            }
            if (x[k] >= x[j]) {
                break;
            }
            swap(x, k, j);
            k = j;
        }
    }

    /**
     * To restore the max-heap condition when a node's priority is decreased.
     * We move down the heap, exchanging the node at position k with the larger
     * of that node's two children if necessary and stopping when the node at
     * k is not smaller than either child or the bottom is reached. Note that
     * if n is even and k is n/2, then the node at k has only one child -- this
     * case must be treated properly.
     * @param x the array.
     * @param k the index of array element.
     * @param n the index {@code n > k}.
     * @param <T> the data type of array elements.
     */
    static <T extends Comparable<? super T>> void siftDown(T[] x, int k, int n) {
        while (2*k <= n) {
            int j = 2 * k;
            if (j < n && x[j].compareTo(x[j + 1]) < 0) {
                j++;
            }
            if (x[k].compareTo(x[j]) >= 0) {
                break;
            }
            swap(x, k, j);
            k = j;
        }
    }
}