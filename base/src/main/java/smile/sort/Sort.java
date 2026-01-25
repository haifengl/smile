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

/**
 * Sort algorithm trait that includes useful static functions
 * such as swap and swift up/down used in many sorting algorithms.
 * 
 * @author Haifeng Li
 */
public interface Sort {

    /**
     * Swap two positions.
     * @param a the array.
     * @param i the index of array element.
     * @param j the index of other element.
     */
    static void swap(int[] a, int i, int j) {
        int temp = a[i];
        a[i] = a[j];
        a[j] = temp;
    }

    /**
     * Swap two positions.
     * @param a the array.
     * @param i the index of array element.
     * @param j the index of other element.
     */
    static void swap(float[] a, int i, int j) {
        float temp = a[i];
        a[i] = a[j];
        a[j] = temp;
    }

    /**
     * Swap two positions.
     * @param a the array.
     * @param i the index of array element.
     * @param j the index of other element.
     */
    static void swap(double[] a, int i, int j) {
        double temp = a[i];
        a[i] = a[j];
        a[j] = temp;
    }

    /**
     * Swap two positions.
     * @param a the array.
     * @param i the index of array element.
     * @param j the index of other element.
     */
    static void swap(Object[] a, int i, int j) {
        Object temp = a[i];
        a[i] = a[j];
        a[j] = temp;
    }

    /**
     * To restore the max-heap condition when a node's priority is increased.
     * We move up the heap, exchanging the node at position k with its parent
     * (at position k/2) if necessary, continuing as long as {@code a[k/2] < a[k]} or
     * until we reach the top of the heap.
     * @param a the heap array.
     * @param k the index of the node to sift up.
     */
    static void siftUp(int[] a, int k) {
        while (k > 1 && a[k/2] < a[k]) {
            swap(a, k, k/2);
            k = k/2;
        }
    }

    /**
     * To restore the max-heap condition when a node's priority is increased.
     * We move up the heap, exchanging the node at position k with its parent
     * (at position k/2) if necessary, continuing as long as {@code a[k/2] < a[k]} or
     * until we reach the top of the heap.
     * @param a the heap array.
     * @param k the index of the node to sift up.
     */
    static void siftUp(float[] a, int k) {
        while (k > 1 && a[k/2] < a[k]) {
            swap(a, k, k/2);
            k = k/2;
        }
    }

    /**
     * To restore the max-heap condition when a node's priority is increased.
     * We move up the heap, exchanging the node at position k with its parent
     * (at position k/2) if necessary, continuing as long as {@code a[k/2] < a[k]} or
     * until we reach the top of the heap.
     * @param a the heap array.
     * @param k the index of the node to sift up.
     */
    static void siftUp(double[] a, int k) {
        while (k > 1 && a[k/2] < a[k]) {
            swap(a, k, k/2);
            k = k/2;
        }
    }

    /**
     * To restore the max-heap condition when a node's priority is increased.
     * We move up the heap, exchanging the node at position k with its parent
     * (at position k/2) if necessary, continuing as long as {@code a[k/2] < a[k]} or
     * until we reach the top of the heap.
     * @param a the heap array.
     * @param k the index of the node to sift up.
     * @param <T> the data type of array elements.
     */
    static <T extends Comparable<? super T>> void siftUp(T[] a, int k) {
        while (k > 1 && a[k/2].compareTo(a[k]) < 0) {
            swap(a, k, k/2);
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
     * @param a the heap array.
     * @param k the index of the node to sift down.
     * @param n the current size of the heap {@code n > k}.
     */
    static void siftDown(int[] a, int k, int n) {
        while (2*k <= n) {
            int j = 2 * k;
            if (j < n && a[j] < a[j + 1]) {
                j++;
            }
            if (a[k] >= a[j]) {
                break;
            }
            swap(a, k, j);
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
     * @param a the heap array.
     * @param k the index of the node to sift down.
     * @param n the current size of the heap {@code n > k}.
     */
    static void siftDown(float[] a, int k, int n) {
        while (2*k <= n) {
            int j = 2 * k;
            if (j < n && a[j] < a[j + 1]) {
                j++;
            }
            if (a[k] >= a[j]) {
                break;
            }
            swap(a, k, j);
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
     * @param a the heap array.
     * @param k the index of the node to sift down.
     * @param n the current size of the heap {@code n > k}.
     */
    static void siftDown(double[] a, int k, int n) {
        while (2*k <= n) {
            int j = 2 * k;
            if (j < n && a[j] < a[j + 1]) {
                j++;
            }
            if (a[k] >= a[j]) {
                break;
            }
            swap(a, k, j);
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
     * @param a the heap array.
     * @param k the index of the node to sift down.
     * @param n the current size of the heap {@code n > k}.
     * @param <T> the data type of array elements.
     */
    static <T extends Comparable<? super T>> void siftDown(T[] a, int k, int n) {
        while (2*k <= n) {
            int j = 2 * k;
            if (j < n && a[j].compareTo(a[j + 1]) < 0) {
                j++;
            }
            if (a[k].compareTo(a[j]) >= 0) {
                break;
            }
            swap(a, k, j);
            k = j;
        }
    }
}