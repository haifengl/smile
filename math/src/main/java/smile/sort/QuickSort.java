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

import java.util.Comparator;

/**
 * Quicksort is a well-known sorting algorithm that, on average, makes O(n log n)
 * comparisons to sort n items. For large n (say &gt; 1000), Quicksort is faster,
 * on most machines, by a factor of 1.5 or 2 than other O(n log n) algorithms.
 * However, in the worst case, it makes O(n<sup>2</sup>) comparisons. Quicksort
 * requires a bit of extra memory.
 * <p>
 * Quicksort is a comparison sort. A comparison sort is a type of sorting
 * algorithm that only reads the list elements through a single abstract
 * comparison operation (often a "less than or equal to" operator) that
 * determines which of two elements should occur first in the final sorted list.
 * The only requirement is that the operator obey the three defining properties
 * of a total order:
 * <ul>
 * <li> if a &le; b and b &le; a then a = b (antisymmetry)
 * <li> if a &le; b and b &le; c then a &le; c (transitivity)
 * <li> a &le; b or b &le; a (totalness or trichotomy)
 * </ul>
 * <p>
 * Quicksort, however, is not a stable sort in efficient implementations.
 * Stable sorting algorithms maintain the relative order of records with
 * equal keys. If all keys are different then this distinction is not
 * necessary. But if there are equal keys, then a sorting algorithm is
 * stable if whenever there are two records(let's say R and S) with the
 * same key, and R appears before S in the original list, then R will
 * always appear before S in the sorted list.
 * <p>
 * For speed of execution, we implement it without recursion. Instead,
 * we requires an auxiliary array (stack) of storage, of length
 * 2 log<sub><small>2</small></sub> n. When a subarray has gotten down to size 7,
 * we sort it by straight insertion.
 * 
 * @author Haifeng Li
 */
public class QuickSort {
    /** Utility classes should not have public constructors. */
    private QuickSort() {

    }

    private static final int M = 7;
    private static final int NSTACK = 64;

    /**
     * Sorts the specified array into ascending numerical order.
     * @return the original index of elements after sorting in range [0, n).
     */
    public static int[] sort(int[] arr) {
        int[] order = new int[arr.length];
        for (int i = 0; i < order.length; i++) {
            order[i] = i;
        }
        sort(arr, order);
        return order;
    }

    /**
     * Besides sorting the array arr, the array brr will be also
     * rearranged as the same order of arr.
     */
    public static void sort(int[] arr, int[] brr) {
        sort(arr, brr, arr.length);
    }

    /**
     * This is an effecient implementation Quick Sort algorithm without
     * recursive. Besides sorting the first n elements of array arr, the first
     * n elements of array brr will be also rearranged as the same order of arr.
     */
    public static void sort(int[] arr, int[] brr, int n) {
        int jstack = -1;
        int l = 0;
        int[] istack = new int[NSTACK];
        int ir = n - 1;

        int i, j, k, a, b;
        for (;;) {
            if (ir - l < M) {
                for (j = l + 1; j <= ir; j++) {
                    a = arr[j];
                    b = brr[j];
                    for (i = j - 1; i >= l; i--) {
                        if (arr[i] <= a) {
                            break;
                        }
                        arr[i + 1] = arr[i];
                        brr[i + 1] = brr[i];
                    }
                    arr[i + 1] = a;
                    brr[i + 1] = b;
                }
                if (jstack < 0) {
                    break;
                }
                ir = istack[jstack--];
                l = istack[jstack--];
            } else {
                k = (l + ir) >> 1;
                SortUtils.swap(arr, k, l + 1);
                SortUtils.swap(brr, k, l + 1);
                if (arr[l] > arr[ir]) {
                    SortUtils.swap(arr, l, ir);
                    SortUtils.swap(brr, l, ir);
                }
                if (arr[l + 1] > arr[ir]) {
                    SortUtils.swap(arr, l + 1, ir);
                    SortUtils.swap(brr, l + 1, ir);
                }
                if (arr[l] > arr[l + 1]) {
                    SortUtils.swap(arr, l, l + 1);
                    SortUtils.swap(brr, l, l + 1);
                }
                i = l + 1;
                j = ir;
                a = arr[l + 1];
                b = brr[l + 1];
                for (;;) {
                    do {
                        i++;
                    } while (arr[i] < a);
                    do {
                        j--;
                    } while (arr[j] > a);
                    if (j < i) {
                        break;
                    }
                    SortUtils.swap(arr, i, j);
                    SortUtils.swap(brr, i, j);
                }
                arr[l + 1] = arr[j];
                arr[j] = a;
                brr[l + 1] = brr[j];
                brr[j] = b;
                jstack += 2;

                if (jstack >= NSTACK) {
                    throw new IllegalStateException("NSTACK too small in sort.");
                }

                if (ir - i + 1 >= j - l) {
                    istack[jstack] = ir;
                    istack[jstack - 1] = i;
                    ir = j - 1;
                } else {
                    istack[jstack] = j - 1;
                    istack[jstack - 1] = l;
                    l = i;
                }
            }
        }
    }

    /**
     * Besides sorting the array arr, the array brr will be also
     * rearranged as the same order of arr.
     */
    public static void sort(int[] arr, Object[] brr) {
        sort(arr, brr, arr.length);
    }

    /**
     * This is an effecient implementation Quick Sort algorithm without
     * recursive. Besides sorting the first n elements of array arr, the first
     * n elements of array brr will be also rearranged as the same order of arr.
     */
    public static void sort(int[] arr, Object[] brr, int n) {
        int jstack = -1;
        int l = 0;
        int[] istack = new int[NSTACK];
        int ir = n - 1;

        int i, j, k, a;
        Object b;
        for (;;) {
            if (ir - l < M) {
                for (j = l + 1; j <= ir; j++) {
                    a = arr[j];
                    b = brr[j];
                    for (i = j - 1; i >= l; i--) {
                        if (arr[i] <= a) {
                            break;
                        }
                        arr[i + 1] = arr[i];
                        brr[i + 1] = brr[i];
                    }
                    arr[i + 1] = a;
                    brr[i + 1] = b;
                }
                if (jstack < 0) {
                    break;
                }
                ir = istack[jstack--];
                l = istack[jstack--];
            } else {
                k = (l + ir) >> 1;
                SortUtils.swap(arr, k, l + 1);
                SortUtils.swap(brr, k, l + 1);
                if (arr[l] > arr[ir]) {
                    SortUtils.swap(arr, l, ir);
                    SortUtils.swap(brr, l, ir);
                }
                if (arr[l + 1] > arr[ir]) {
                    SortUtils.swap(arr, l + 1, ir);
                    SortUtils.swap(brr, l + 1, ir);
                }
                if (arr[l] > arr[l + 1]) {
                    SortUtils.swap(arr, l, l + 1);
                    SortUtils.swap(brr, l, l + 1);
                }
                i = l + 1;
                j = ir;
                a = arr[l + 1];
                b = brr[l + 1];
                for (;;) {
                    do {
                        i++;
                    } while (arr[i] < a);
                    do {
                        j--;
                    } while (arr[j] > a);
                    if (j < i) {
                        break;
                    }
                    SortUtils.swap(arr, i, j);
                    SortUtils.swap(brr, i, j);
                }
                arr[l + 1] = arr[j];
                arr[j] = a;
                brr[l + 1] = brr[j];
                brr[j] = b;
                jstack += 2;

                if (jstack >= NSTACK) {
                    throw new IllegalStateException("NSTACK too small in sort.");
                }

                if (ir - i + 1 >= j - l) {
                    istack[jstack] = ir;
                    istack[jstack - 1] = i;
                    ir = j - 1;
                } else {
                    istack[jstack] = j - 1;
                    istack[jstack - 1] = l;
                    l = i;
                }
            }
        }
    }

    /**
     * Sorts the specified array into ascending numerical order.
     * @return the original index of elements after sorting in range [0, n).
     */
    public static int[] sort(float[] arr) {
        int[] order = new int[arr.length];
        for (int i = 0; i < order.length; i++) {
            order[i] = i;
        }
        sort(arr, order);
        return order;
    }

    /**
     * Besides sorting the array arr, the array brr will be also
     * rearranged as the same order of arr.
     */
    public static void sort(float[] arr, int[] brr) {
        sort(arr, brr, arr.length);
    }

    /**
     * This is an effecient implementation Quick Sort algorithm without
     * recursive. Besides sorting the first n elements of array arr, the first
     * n elements of array brr will be also rearranged as the same order of arr.
     */
    public static void sort(float[] arr, int[] brr, int n) {
        int jstack = -1;
        int l = 0;
        int[] istack = new int[NSTACK];
        int ir = n - 1;

        int i, j, k;
        float a;
        int b;
        for (;;) {
            if (ir - l < M) {
                for (j = l + 1; j <= ir; j++) {
                    a = arr[j];
                    b = brr[j];
                    for (i = j - 1; i >= l; i--) {
                        if (arr[i] <= a) {
                            break;
                        }
                        arr[i + 1] = arr[i];
                        brr[i + 1] = brr[i];
                    }
                    arr[i + 1] = a;
                    brr[i + 1] = b;
                }
                if (jstack < 0) {
                    break;
                }
                ir = istack[jstack--];
                l = istack[jstack--];
            } else {
                k = (l + ir) >> 1;
                SortUtils.swap(arr, k, l + 1);
                SortUtils.swap(brr, k, l + 1);
                if (arr[l] > arr[ir]) {
                    SortUtils.swap(arr, l, ir);
                    SortUtils.swap(brr, l, ir);
                }
                if (arr[l + 1] > arr[ir]) {
                    SortUtils.swap(arr, l + 1, ir);
                    SortUtils.swap(brr, l + 1, ir);
                }
                if (arr[l] > arr[l + 1]) {
                    SortUtils.swap(arr, l, l + 1);
                    SortUtils.swap(brr, l, l + 1);
                }
                i = l + 1;
                j = ir;
                a = arr[l + 1];
                b = brr[l + 1];
                for (;;) {
                    do {
                        i++;
                    } while (arr[i] < a);
                    do {
                        j--;
                    } while (arr[j] > a);
                    if (j < i) {
                        break;
                    }
                    SortUtils.swap(arr, i, j);
                    SortUtils.swap(brr, i, j);
                }
                arr[l + 1] = arr[j];
                arr[j] = a;
                brr[l + 1] = brr[j];
                brr[j] = b;
                jstack += 2;

                if (jstack >= NSTACK) {
                    throw new IllegalStateException("NSTACK too small in sort.");
                }

                if (ir - i + 1 >= j - l) {
                    istack[jstack] = ir;
                    istack[jstack - 1] = i;
                    ir = j - 1;
                } else {
                    istack[jstack] = j - 1;
                    istack[jstack - 1] = l;
                    l = i;
                }
            }
        }
    }

    /**
     * Besides sorting the array arr, the array brr will be also
     * rearranged as the same order of arr.
     */
    public static void sort(float[] arr, float[] brr) {
        sort(arr, brr, arr.length);
    }

    /**
     * This is an effecient implementation Quick Sort algorithm without
     * recursive. Besides sorting the first n elements of array arr, the first
     * n elements of array brr will be also rearranged as the same order of arr.
     */
    public static void sort(float[] arr, float[] brr, int n) {
        int jstack = -1;
        int l = 0;
        int[] istack = new int[NSTACK];
        int ir = n - 1;

        int i, j, k;
        float a, b;
        for (;;) {
            if (ir - l < M) {
                for (j = l + 1; j <= ir; j++) {
                    a = arr[j];
                    b = brr[j];
                    for (i = j - 1; i >= l; i--) {
                        if (arr[i] <= a) {
                            break;
                        }
                        arr[i + 1] = arr[i];
                        brr[i + 1] = brr[i];
                    }
                    arr[i + 1] = a;
                    brr[i + 1] = b;
                }
                if (jstack < 0) {
                    break;
                }
                ir = istack[jstack--];
                l = istack[jstack--];
            } else {
                k = (l + ir) >> 1;
                SortUtils.swap(arr, k, l + 1);
                SortUtils.swap(brr, k, l + 1);
                if (arr[l] > arr[ir]) {
                    SortUtils.swap(arr, l, ir);
                    SortUtils.swap(brr, l, ir);
                }
                if (arr[l + 1] > arr[ir]) {
                    SortUtils.swap(arr, l + 1, ir);
                    SortUtils.swap(brr, l + 1, ir);
                }
                if (arr[l] > arr[l + 1]) {
                    SortUtils.swap(arr, l, l + 1);
                    SortUtils.swap(brr, l, l + 1);
                }
                i = l + 1;
                j = ir;
                a = arr[l + 1];
                b = brr[l + 1];
                for (;;) {
                    do {
                        i++;
                    } while (arr[i] < a);
                    do {
                        j--;
                    } while (arr[j] > a);
                    if (j < i) {
                        break;
                    }
                    SortUtils.swap(arr, i, j);
                    SortUtils.swap(brr, i, j);
                }
                arr[l + 1] = arr[j];
                arr[j] = a;
                brr[l + 1] = brr[j];
                brr[j] = b;
                jstack += 2;

                if (jstack >= NSTACK) {
                    throw new IllegalStateException("NSTACK too small in sort.");
                }

                if (ir - i + 1 >= j - l) {
                    istack[jstack] = ir;
                    istack[jstack - 1] = i;
                    ir = j - 1;
                } else {
                    istack[jstack] = j - 1;
                    istack[jstack - 1] = l;
                    l = i;
                }
            }
        }
    }

    /**
     * Besides sorting the array arr, the array brr will be also
     * rearranged as the same order of arr.
     */
    public static void sort(float[] arr, Object[] brr) {
        sort(arr, brr, arr.length);
    }

    /**
     * This is an effecient implementation Quick Sort algorithm without
     * recursive. Besides sorting the first n elements of array arr, the first
     * n elements of array brr will be also rearranged as the same order of arr.
     */
    public static void sort(float[] arr, Object[] brr, int n) {
        int jstack = -1;
        int l = 0;
        int[] istack = new int[NSTACK];
        int ir = n - 1;

        int i, j, k;
        float a;
        Object b;
        for (;;) {
            if (ir - l < M) {
                for (j = l + 1; j <= ir; j++) {
                    a = arr[j];
                    b = brr[j];
                    for (i = j - 1; i >= l; i--) {
                        if (arr[i] <= a) {
                            break;
                        }
                        arr[i + 1] = arr[i];
                        brr[i + 1] = brr[i];
                    }
                    arr[i + 1] = a;
                    brr[i + 1] = b;
                }
                if (jstack < 0) {
                    break;
                }
                ir = istack[jstack--];
                l = istack[jstack--];
            } else {
                k = (l + ir) >> 1;
                SortUtils.swap(arr, k, l + 1);
                SortUtils.swap(brr, k, l + 1);
                if (arr[l] > arr[ir]) {
                    SortUtils.swap(arr, l, ir);
                    SortUtils.swap(brr, l, ir);
                }
                if (arr[l + 1] > arr[ir]) {
                    SortUtils.swap(arr, l + 1, ir);
                    SortUtils.swap(brr, l + 1, ir);
                }
                if (arr[l] > arr[l + 1]) {
                    SortUtils.swap(arr, l, l + 1);
                    SortUtils.swap(brr, l, l + 1);
                }
                i = l + 1;
                j = ir;
                a = arr[l + 1];
                b = brr[l + 1];
                for (;;) {
                    do {
                        i++;
                    } while (arr[i] < a);
                    do {
                        j--;
                    } while (arr[j] > a);
                    if (j < i) {
                        break;
                    }
                    SortUtils.swap(arr, i, j);
                    SortUtils.swap(brr, i, j);
                }
                arr[l + 1] = arr[j];
                arr[j] = a;
                brr[l + 1] = brr[j];
                brr[j] = b;
                jstack += 2;

                if (jstack >= NSTACK) {
                    throw new IllegalStateException("NSTACK too small in sort.");
                }

                if (ir - i + 1 >= j - l) {
                    istack[jstack] = ir;
                    istack[jstack - 1] = i;
                    ir = j - 1;
                } else {
                    istack[jstack] = j - 1;
                    istack[jstack - 1] = l;
                    l = i;
                }
            }
        }
    }

    /**
     * Sorts the specified array into ascending numerical order.
     * @return the original index of elements after sorting in range [0, n).
     */
    public static int[] sort(double[] arr) {
        int[] order = new int[arr.length];
        for (int i = 0; i < order.length; i++) {
            order[i] = i;
        }
        sort(arr, order);
        return order;
    }

    /**
     * Besides sorting the array arr, the array brr will be also
     * rearranged as the same order of arr.
     */
    public static void sort(double[] arr, int[] brr) {
        sort(arr, brr, arr.length);
    }

    /**
     * This is an effecient implementation Quick Sort algorithm without
     * recursive. Besides sorting the first n elements of array arr, the first
     * n elements of array brr will be also rearranged as the same order of arr.
     */
    public static void sort(double[] arr, int[] brr, int n) {
        int jstack = -1;
        int l = 0;
        int[] istack = new int[NSTACK];
        int ir = n - 1;

        int i, j, k;
        double a;
        int b;
        for (;;) {
            if (ir - l < M) {
                for (j = l + 1; j <= ir; j++) {
                    a = arr[j];
                    b = brr[j];
                    for (i = j - 1; i >= l; i--) {
                        if (arr[i] <= a) {
                            break;
                        }
                        arr[i + 1] = arr[i];
                        brr[i + 1] = brr[i];
                    }
                    arr[i + 1] = a;
                    brr[i + 1] = b;
                }
                if (jstack < 0) {
                    break;
                }
                ir = istack[jstack--];
                l = istack[jstack--];
            } else {
                k = (l + ir) >> 1;
                SortUtils.swap(arr, k, l + 1);
                SortUtils.swap(brr, k, l + 1);
                if (arr[l] > arr[ir]) {
                    SortUtils.swap(arr, l, ir);
                    SortUtils.swap(brr, l, ir);
                }
                if (arr[l + 1] > arr[ir]) {
                    SortUtils.swap(arr, l + 1, ir);
                    SortUtils.swap(brr, l + 1, ir);
                }
                if (arr[l] > arr[l + 1]) {
                    SortUtils.swap(arr, l, l + 1);
                    SortUtils.swap(brr, l, l + 1);
                }
                i = l + 1;
                j = ir;
                a = arr[l + 1];
                b = brr[l + 1];
                for (;;) {
                    do {
                        i++;
                    } while (arr[i] < a);
                    do {
                        j--;
                    } while (arr[j] > a);
                    if (j < i) {
                        break;
                    }
                    SortUtils.swap(arr, i, j);
                    SortUtils.swap(brr, i, j);
                }
                arr[l + 1] = arr[j];
                arr[j] = a;
                brr[l + 1] = brr[j];
                brr[j] = b;
                jstack += 2;

                if (jstack >= NSTACK) {
                    throw new IllegalStateException("NSTACK too small in sort.");
                }

                if (ir - i + 1 >= j - l) {
                    istack[jstack] = ir;
                    istack[jstack - 1] = i;
                    ir = j - 1;
                } else {
                    istack[jstack] = j - 1;
                    istack[jstack - 1] = l;
                    l = i;
                }
            }
        }
    }

    /**
     * This is an effecient implementation Quick Sort algorithm without
     * recursive. Besides sorting the array arr, the array brr will be also
     * rearranged as the same order of arr.
     */
    public static void sort(double[] arr, double[] brr) {
        sort(arr, brr, arr.length);
    }

    /**
     * This is an effecient implementation Quick Sort algorithm without
     * recursive. Besides sorting the first n elements of array arr, the first
     * n elements of array brr will be also rearranged as the same order of arr.
     */
    public static void sort(double[] arr, double[] brr, int n) {
        int jstack = -1;
        int l = 0;
        int[] istack = new int[NSTACK];
        int ir = n - 1;

        int i, j, k;
        double a, b;
        for (;;) {
            if (ir - l < M) {
                for (j = l + 1; j <= ir; j++) {
                    a = arr[j];
                    b = brr[j];
                    for (i = j - 1; i >= l; i--) {
                        if (arr[i] <= a) {
                            break;
                        }
                        arr[i + 1] = arr[i];
                        brr[i + 1] = brr[i];
                    }
                    arr[i + 1] = a;
                    brr[i + 1] = b;
                }
                if (jstack < 0) {
                    break;
                }
                ir = istack[jstack--];
                l = istack[jstack--];
            } else {
                k = (l + ir) >> 1;
                SortUtils.swap(arr, k, l + 1);
                SortUtils.swap(brr, k, l + 1);
                if (arr[l] > arr[ir]) {
                    SortUtils.swap(arr, l, ir);
                    SortUtils.swap(brr, l, ir);
                }
                if (arr[l + 1] > arr[ir]) {
                    SortUtils.swap(arr, l + 1, ir);
                    SortUtils.swap(brr, l + 1, ir);
                }
                if (arr[l] > arr[l + 1]) {
                    SortUtils.swap(arr, l, l + 1);
                    SortUtils.swap(brr, l, l + 1);
                }
                i = l + 1;
                j = ir;
                a = arr[l + 1];
                b = brr[l + 1];
                for (;;) {
                    do {
                        i++;
                    } while (arr[i] < a);
                    do {
                        j--;
                    } while (arr[j] > a);
                    if (j < i) {
                        break;
                    }
                    SortUtils.swap(arr, i, j);
                    SortUtils.swap(brr, i, j);
                }
                arr[l + 1] = arr[j];
                arr[j] = a;
                brr[l + 1] = brr[j];
                brr[j] = b;
                jstack += 2;

                if (jstack >= NSTACK) {
                    throw new IllegalStateException("NSTACK too small in sort.");
                }

                if (ir - i + 1 >= j - l) {
                    istack[jstack] = ir;
                    istack[jstack - 1] = i;
                    ir = j - 1;
                } else {
                    istack[jstack] = j - 1;
                    istack[jstack - 1] = l;
                    l = i;
                }
            }
        }
    }

    /**
     * Besides sorting the array arr, the array brr will be also
     * rearranged as the same order of arr.
     */
    public static void sort(double[] arr, Object[] brr) {
        sort(arr, brr, arr.length);
    }

    /**
     * This is an effecient implementation Quick Sort algorithm without
     * recursive. Besides sorting the first n elements of array arr, the first
     * n elements of array brr will be also rearranged as the same order of arr.
     */
    public static void sort(double[] arr, Object[] brr, int n) {
        int jstack = -1;
        int l = 0;
        int[] istack = new int[NSTACK];
        int ir = n - 1;

        int i, j, k;
        double a;
        Object b;
        for (;;) {
            if (ir - l < M) {
                for (j = l + 1; j <= ir; j++) {
                    a = arr[j];
                    b = brr[j];
                    for (i = j - 1; i >= l; i--) {
                        if (arr[i] <= a) {
                            break;
                        }
                        arr[i + 1] = arr[i];
                        brr[i + 1] = brr[i];
                    }
                    arr[i + 1] = a;
                    brr[i + 1] = b;
                }
                if (jstack < 0) {
                    break;
                }
                ir = istack[jstack--];
                l = istack[jstack--];
            } else {
                k = (l + ir) >> 1;
                SortUtils.swap(arr, k, l + 1);
                SortUtils.swap(brr, k, l + 1);
                if (arr[l] > arr[ir]) {
                    SortUtils.swap(arr, l, ir);
                    SortUtils.swap(brr, l, ir);
                }
                if (arr[l + 1] > arr[ir]) {
                    SortUtils.swap(arr, l + 1, ir);
                    SortUtils.swap(brr, l + 1, ir);
                }
                if (arr[l] > arr[l + 1]) {
                    SortUtils.swap(arr, l, l + 1);
                    SortUtils.swap(brr, l, l + 1);
                }
                i = l + 1;
                j = ir;
                a = arr[l + 1];
                b = brr[l + 1];
                for (;;) {
                    do {
                        i++;
                    } while (arr[i] < a);
                    do {
                        j--;
                    } while (arr[j] > a);
                    if (j < i) {
                        break;
                    }
                    SortUtils.swap(arr, i, j);
                    SortUtils.swap(brr, i, j);
                }
                arr[l + 1] = arr[j];
                arr[j] = a;
                brr[l + 1] = brr[j];
                brr[j] = b;
                jstack += 2;

                if (jstack >= NSTACK) {
                    throw new IllegalStateException("NSTACK too small in sort.");
                }

                if (ir - i + 1 >= j - l) {
                    istack[jstack] = ir;
                    istack[jstack - 1] = i;
                    ir = j - 1;
                } else {
                    istack[jstack] = j - 1;
                    istack[jstack - 1] = l;
                    l = i;
                }
            }
        }
    }

    /**
     * Sorts the specified array into ascending order.
     * @return the original index of elements after sorting in range [0, n).
     */
    public static <T extends Comparable<? super T>>  int[] sort(T[] arr) {
        int[] order = new int[arr.length];
        for (int i = 0; i < order.length; i++) {
            order[i] = i;
        }
        sort(arr, order);
        return order;
    }

    /**
     * Besides sorting the array arr, the array brr will be also
     * rearranged as the same order of arr.
     */
    public static <T extends Comparable<? super T>>  void sort(T[] arr, int[] brr) {
        sort(arr, brr, arr.length);
    }

    /**
     * This is an effecient implementation Quick Sort algorithm without
     * recursive. Besides sorting the first n elements of array arr, the first
     * n elements of array brr will be also rearranged as the same order of arr.
     */
    public static <T extends Comparable<? super T>>  void sort(T[] arr, int[] brr, int n) {
        int jstack = -1;
        int l = 0;
        int[] istack = new int[NSTACK];
        int ir = n - 1;

        int i, j, k;
        T a;
        int b;
        for (;;) {
            if (ir - l < M) {
                for (j = l + 1; j <= ir; j++) {
                    a = arr[j];
                    b = brr[j];
                    for (i = j - 1; i >= l; i--) {
                        if (arr[i].compareTo(a) <= 0) {
                            break;
                        }
                        arr[i + 1] = arr[i];
                        brr[i + 1] = brr[i];
                    }
                    arr[i + 1] = a;
                    brr[i + 1] = b;
                }
                if (jstack < 0) {
                    break;
                }
                ir = istack[jstack--];
                l = istack[jstack--];
            } else {
                k = (l + ir) >> 1;
                SortUtils.swap(arr, k, l + 1);
                SortUtils.swap(brr, k, l + 1);
                if (arr[l].compareTo(arr[ir]) > 0) {
                    SortUtils.swap(arr, l, ir);
                    SortUtils.swap(brr, l, ir);
                }
                if (arr[l + 1].compareTo(arr[ir]) > 0) {
                    SortUtils.swap(arr, l + 1, ir);
                    SortUtils.swap(brr, l + 1, ir);
                }
                if (arr[l].compareTo(arr[l + 1]) > 0) {
                    SortUtils.swap(arr, l, l + 1);
                    SortUtils.swap(brr, l, l + 1);
                }
                i = l + 1;
                j = ir;
                a = arr[l + 1];
                b = brr[l + 1];
                for (;;) {
                    do {
                        i++;
                    } while (arr[i].compareTo(a) < 0);
                    do {
                        j--;
                    } while (arr[j].compareTo(a) > 0);
                    if (j < i) {
                        break;
                    }
                    SortUtils.swap(arr, i, j);
                    SortUtils.swap(brr, i, j);
                }
                arr[l + 1] = arr[j];
                arr[j] = a;
                brr[l + 1] = brr[j];
                brr[j] = b;
                jstack += 2;

                if (jstack >= NSTACK) {
                    throw new IllegalStateException("NSTACK too small in sort.");
                }

                if (ir - i + 1 >= j - l) {
                    istack[jstack] = ir;
                    istack[jstack - 1] = i;
                    ir = j - 1;
                } else {
                    istack[jstack] = j - 1;
                    istack[jstack - 1] = l;
                    l = i;
                }
            }
        }
    }

    /**
     * This is an effecient implementation Quick Sort algorithm without
     * recursive. Besides sorting the first n elements of array arr, the first
     * n elements of array brr will be also rearranged as the same order of arr.
     */
    public static <T>  void sort(T[] arr, int[] brr, int n, Comparator<T> comparator) {
        int jstack = -1;
        int l = 0;
        int[] istack = new int[NSTACK];
        int ir = n - 1;

        int i, j, k;
        T a;
        int b;
        for (;;) {
            if (ir - l < M) {
                for (j = l + 1; j <= ir; j++) {
                    a = arr[j];
                    b = brr[j];
                    for (i = j - 1; i >= l; i--) {
                        if (comparator.compare(arr[i], a) <= 0) {
                            break;
                        }
                        arr[i + 1] = arr[i];
                        brr[i + 1] = brr[i];
                    }
                    arr[i + 1] = a;
                    brr[i + 1] = b;
                }
                if (jstack < 0) {
                    break;
                }
                ir = istack[jstack--];
                l = istack[jstack--];
            } else {
                k = (l + ir) >> 1;
                SortUtils.swap(arr, k, l + 1);
                SortUtils.swap(brr, k, l + 1);
                if (comparator.compare(arr[l], arr[ir]) > 0) {
                    SortUtils.swap(arr, l, ir);
                    SortUtils.swap(brr, l, ir);
                }
                if (comparator.compare(arr[l + 1], arr[ir]) > 0) {
                    SortUtils.swap(arr, l + 1, ir);
                    SortUtils.swap(brr, l + 1, ir);
                }
                if (comparator.compare(arr[l], arr[l + 1]) > 0) {
                    SortUtils.swap(arr, l, l + 1);
                    SortUtils.swap(brr, l, l + 1);
                }
                i = l + 1;
                j = ir;
                a = arr[l + 1];
                b = brr[l + 1];
                for (;;) {
                    do {
                        i++;
                    } while (comparator.compare(arr[i], a) < 0);
                    do {
                        j--;
                    } while (comparator.compare(arr[j], a) > 0);
                    if (j < i) {
                        break;
                    }
                    SortUtils.swap(arr, i, j);
                    SortUtils.swap(brr, i, j);
                }
                arr[l + 1] = arr[j];
                arr[j] = a;
                brr[l + 1] = brr[j];
                brr[j] = b;
                jstack += 2;

                if (jstack >= NSTACK) {
                    throw new IllegalStateException("NSTACK too small in sort.");
                }

                if (ir - i + 1 >= j - l) {
                    istack[jstack] = ir;
                    istack[jstack - 1] = i;
                    ir = j - 1;
                } else {
                    istack[jstack] = j - 1;
                    istack[jstack - 1] = l;
                    l = i;
                }
            }
        }
    }

    /**
     * Besides sorting the array arr, the array brr will be also
     * rearranged as the same order of arr.
     */
    public static <T extends Comparable<? super T>>  void sort(T[] arr, Object[] brr) {
        sort(arr, brr, arr.length);
    }

    /**
     * This is an effecient implementation Quick Sort algorithm without
     * recursive. Besides sorting the first n elements of array arr, the first
     * n elements of array brr will be also rearranged as the same order of arr.
     */
    public static <T extends Comparable<? super T>>  void sort(T[] arr, Object[] brr, int n) {
        int jstack = -1;
        int l = 0;
        int[] istack = new int[NSTACK];
        int ir = n - 1;

        int i, j, k;
        T a;
        Object b;
        for (;;) {
            if (ir - l < M) {
                for (j = l + 1; j <= ir; j++) {
                    a = arr[j];
                    b = brr[j];
                    for (i = j - 1; i >= l; i--) {
                        if (arr[i].compareTo(a) <= 0) {
                            break;
                        }
                        arr[i + 1] = arr[i];
                        brr[i + 1] = brr[i];
                    }
                    arr[i + 1] = a;
                    brr[i + 1] = b;
                }
                if (jstack < 0) {
                    break;
                }
                ir = istack[jstack--];
                l = istack[jstack--];
            } else {
                k = (l + ir) >> 1;
                SortUtils.swap(arr, k, l + 1);
                SortUtils.swap(brr, k, l + 1);
                if (arr[l].compareTo(arr[ir]) > 0) {
                    SortUtils.swap(arr, l, ir);
                    SortUtils.swap(brr, l, ir);
                }
                if (arr[l + 1].compareTo(arr[ir]) > 0) {
                    SortUtils.swap(arr, l + 1, ir);
                    SortUtils.swap(brr, l + 1, ir);
                }
                if (arr[l].compareTo(arr[l + 1]) > 0) {
                    SortUtils.swap(arr, l, l + 1);
                    SortUtils.swap(brr, l, l + 1);
                }
                i = l + 1;
                j = ir;
                a = arr[l + 1];
                b = brr[l + 1];
                for (;;) {
                    do {
                        i++;
                    } while (arr[i].compareTo(a) < 0);
                    do {
                        j--;
                    } while (arr[j].compareTo(a) > 0);
                    if (j < i) {
                        break;
                    }
                    SortUtils.swap(arr, i, j);
                    SortUtils.swap(brr, i, j);
                }
                arr[l + 1] = arr[j];
                arr[j] = a;
                brr[l + 1] = brr[j];
                brr[j] = b;
                jstack += 2;

                if (jstack >= NSTACK) {
                    throw new IllegalStateException("NSTACK too small in sort.");
                }

                if (ir - i + 1 >= j - l) {
                    istack[jstack] = ir;
                    istack[jstack - 1] = i;
                    ir = j - 1;
                } else {
                    istack[jstack] = j - 1;
                    istack[jstack - 1] = l;
                    l = i;
                }
            }
        }
    }
}
