/******************************************************************************
 *                   Confidential Proprietary                                 *
 *         (c) Copyright Haifeng Li 2011, All Rights Reserved                 *
 ******************************************************************************/

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
public class HeapSort {
    /**
     * Sorts the specified array into ascending numerical order.
     */
    public static void sort(int[] arr) {
        int n = arr.length;
        for (int i = n / 2 - 1; i >= 0; i--)
            SortUtils.siftDown(arr, i, n - 1);

        for (int i = n - 1; i > 0; i--) {
            SortUtils.swap(arr, 0, i);
            SortUtils.siftDown(arr, 0, i - 1);
        }
    }

    /**
     * Sorts the specified array into ascending numerical order.
     */
    public static void sort(float[] arr) {
        int n = arr.length;
        for (int i = n / 2 - 1; i >= 0; i--)
            SortUtils.siftDown(arr, i, n - 1);

        for (int i = n - 1; i > 0; i--) {
            SortUtils.swap(arr, 0, i);
            SortUtils.siftDown(arr, 0, i - 1);
        }
    }

    /**
     * Sorts the specified array into ascending numerical order.
     */
    public static void sort(double[] arr) {
        int n = arr.length;
        for (int i = n / 2 - 1; i >= 0; i--)
            SortUtils.siftDown(arr, i, n - 1);

        for (int i = n - 1; i > 0; i--) {
            SortUtils.swap(arr, 0, i);
            SortUtils.siftDown(arr, 0, i - 1);
        }
    }

    /**
     * Sorts the specified array into ascending order.
     */
    public static <T extends Comparable<? super T>> void sort(T[] arr) {
        int n = arr.length;
        for (int i = n / 2 - 1; i >= 0; i--)
            SortUtils.siftDown(arr, i, n - 1);

        for (int i = n - 1; i > 0; i--) {
            SortUtils.swap(arr, 0, i);
            SortUtils.siftDown(arr, 0, i - 1);
        }
    }
}