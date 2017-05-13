/*******************************************************************************
 * Copyright (c) 2010 Haifeng Li
 *   
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *  
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *******************************************************************************/

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
    /** Utility classes should not have public constructors. */
    private HeapSort() {

    }

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