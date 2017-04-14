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
 * Some useful functions such as swap and swif-down used in many sorting
 * algorithms.
 * 
 * @author Haifeng Li
 */
public class SortUtils {
    /** Utility classes should not have public constructors. */
    private SortUtils() {

    }

    /**
     * Swap two positions.
     */
    public static void swap(int arr[], int i, int j) {
        int a = arr[i];
        arr[i] = arr[j];
        arr[j] = a;
    }

    /**
     * Swap two positions.
     */
    public static void swap(float arr[], int i, int j) {
        float a = arr[i];
        arr[i] = arr[j];
        arr[j] = a;
    }

    /**
     * Swap two positions.
     */
    public static void swap(double arr[], int i, int j) {
        double a;
        a = arr[i];
        arr[i] = arr[j];
        arr[j] = a;
    }

    /**
     * Swap two positions.
     */
    public static void swap(Object arr[], int i, int j) {
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
    public static void siftUp(int[] arr, int k) {
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
    public static void siftUp(float[] arr, int k) {
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
    public static void siftUp(double[] arr, int k) {
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
    public static <T extends Comparable<? super T>> void siftUp(T[] arr, int k) {
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
    public static void siftDown(int[] arr, int k, int n) {
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
    public static void siftDown(float[] arr, int k, int n) {
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
    public static void siftDown(double[] arr, int k, int n) {
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
    public static <T extends Comparable<? super T>> void siftDown(T[] arr, int k, int n) {
        while (2*k <= n) {
            int j = 2 * k;
            if (j < n && arr[j].compareTo(arr[j + 1]) < 0) {
                j++;
            }
            if (arr[k].compareTo(arr[j]) >= 0) {
                break;
            }
            SortUtils.swap(arr, k, j);
            k = j;
        }
    }
}