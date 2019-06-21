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
     */
    static void swap(int arr[], int i, int j) {
        int a = arr[i];
        arr[i] = arr[j];
        arr[j] = a;
    }

    /**
     * Swap two positions.
     */
    static void swap(float arr[], int i, int j) {
        float a = arr[i];
        arr[i] = arr[j];
        arr[j] = a;
    }

    /**
     * Swap two positions.
     */
    static void swap(double arr[], int i, int j) {
        double a;
        a = arr[i];
        arr[i] = arr[j];
        arr[j] = a;
    }

    /**
     * Swap two positions.
     */
    static void swap(Object arr[], int i, int j) {
        Object a;
        a = arr[i];
        arr[i] = arr[j];
        arr[j] = a;
    }

    /**
     * To restore the max-heap condition when a node's priority is increased.
     * We move up the heap, exchaning the node at position k with its parent
     * (at postion k/2) if necessary, continuing as long as a[k/2] &lt; a[k] or
     * until we reach the top of the heap.
     */
    static void siftUp(int[] arr, int k) {
        while (k > 1 && arr[k/2] < arr[k]) {
            swap(arr, k, k/2);
            k = k/2;
        }
    }

    /**
     * To restore the max-heap condition when a node's priority is increased.
     * We move up the heap, exchaning the node at position k with its parent
     * (at postion k/2) if necessary, continuing as long as a[k/2] &lt; a[k] or
     * until we reach the top of the heap.
     */
    static void siftUp(float[] arr, int k) {
        while (k > 1 && arr[k/2] < arr[k]) {
            swap(arr, k, k/2);
            k = k/2;
        }
    }

    /**
     * To restore the max-heap condition when a node's priority is increased.
     * We move up the heap, exchaning the node at position k with its parent
     * (at postion k/2) if necessary, continuing as long as a[k/2] &lt; a[k] or
     * until we reach the top of the heap.
     */
    static void siftUp(double[] arr, int k) {
        while (k > 1 && arr[k/2] < arr[k]) {
            swap(arr, k, k/2);
            k = k/2;
        }
    }

    /**
     * To restore the max-heap condition when a node's priority is increased.
     * We move up the heap, exchaning the node at position k with its parent
     * (at postion k/2) if necessary, continuing as long as a[k/2] &lt; a[k] or
     * until we reach the top of the heap.
     */
    static <T extends Comparable<? super T>> void siftUp(T[] arr, int k) {
        while (k > 1 && arr[k/2].compareTo(arr[k]) < 0) {
            swap(arr, k, k/2);
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
     */
    static void siftDown(int[] arr, int k, int n) {
        while (2*k <= n) {
            int j = 2 * k;
            if (j < n && arr[j] < arr[j + 1]) {
                j++;
            }
            if (arr[k] >= arr[j]) {
                break;
            }
            swap(arr, k, j);
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
     */
    static void siftDown(float[] arr, int k, int n) {
        while (2*k <= n) {
            int j = 2 * k;
            if (j < n && arr[j] < arr[j + 1]) {
                j++;
            }
            if (arr[k] >= arr[j]) {
                break;
            }
            swap(arr, k, j);
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
     */
    static void siftDown(double[] arr, int k, int n) {
        while (2*k <= n) {
            int j = 2 * k;
            if (j < n && arr[j] < arr[j + 1]) {
                j++;
            }
            if (arr[k] >= arr[j]) {
                break;
            }
            swap(arr, k, j);
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
     */
    static <T extends Comparable<? super T>> void siftDown(T[] arr, int k, int n) {
        while (2*k <= n) {
            int j = 2 * k;
            if (j < n && arr[j].compareTo(arr[j + 1]) < 0) {
                j++;
            }
            if (arr[k].compareTo(arr[j]) >= 0) {
                break;
            }
            swap(arr, k, j);
            k = j;
        }
    }
}