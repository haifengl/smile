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

import java.util.Comparator;

/**
 * Quicksort is a well-known sorting algorithm that, on average, makes O(n log n)
 * comparisons to sort n items. For large n (say {@code > 1000}), Quicksort is faster,
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
 * <li> if {@code a <= b} and {@code b <= a} then a = b (antisymmetry)
 * <li> if {@code a <= b} and {@code b <= c} then {@code a <= c} (transitivity)
 * <li> {@code a <= b} or {@code b <= a} (totalness or trichotomy)
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
    /** Private constructor to prevent instance creation. */
    private QuickSort() {

    }

    private static final int M = 7;
    private static final int NSTACK = 64;

    /**
     * Sorts the specified array into ascending numerical order.
     * @param x the array.
     * @return the original index of elements after sorting in range [0, n).
     */
    public static int[] sort(int[] x) {
        int[] order = new int[x.length];
        for (int i = 0; i < order.length; i++) {
            order[i] = i;
        }
        sort(x, order);
        return order;
    }

    /**
     * Besides sorting the array x, the array y will be also
     * rearranged as the same order of x.
     * @param x the array to sort.
     * @param y the associate array.
     */
    public static void sort(int[] x, int[] y) {
        sort(x, y, x.length);
    }

    /**
     * This is an efficient implementation Quick Sort algorithm without
     * recursive. Besides sorting the first n elements of array x, the first
     * n elements of array y will be also rearranged as the same order of x.
     * @param x the array to sort.
     * @param y the associate array.
     * @param n the first n elements to sort.
     */
    public static void sort(int[] x, int[] y, int n) {
        int jstack = -1;
        int l = 0;
        int[] istack = new int[NSTACK];
        int ir = n - 1;

        int i, j, k, a, b;
        for (;;) {
            if (ir - l < M) {
                for (j = l + 1; j <= ir; j++) {
                    a = x[j];
                    b = y[j];
                    for (i = j - 1; i >= l; i--) {
                        if (x[i] <= a) {
                            break;
                        }
                        x[i + 1] = x[i];
                        y[i + 1] = y[i];
                    }
                    x[i + 1] = a;
                    y[i + 1] = b;
                }
                if (jstack < 0) {
                    break;
                }
                ir = istack[jstack--];
                l = istack[jstack--];
            } else {
                k = (l + ir) >> 1;
                Sort.swap(x, k, l + 1);
                Sort.swap(y, k, l + 1);
                if (x[l] > x[ir]) {
                    Sort.swap(x, l, ir);
                    Sort.swap(y, l, ir);
                }
                if (x[l + 1] > x[ir]) {
                    Sort.swap(x, l + 1, ir);
                    Sort.swap(y, l + 1, ir);
                }
                if (x[l] > x[l + 1]) {
                    Sort.swap(x, l, l + 1);
                    Sort.swap(y, l, l + 1);
                }
                i = l + 1;
                j = ir;
                a = x[l + 1];
                b = y[l + 1];
                for (;;) {
                    do {
                        i++;
                    } while (x[i] < a);
                    do {
                        j--;
                    } while (x[j] > a);
                    if (j < i) {
                        break;
                    }
                    Sort.swap(x, i, j);
                    Sort.swap(y, i, j);
                }
                x[l + 1] = x[j];
                x[j] = a;
                y[l + 1] = y[j];
                y[j] = b;
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
     * Besides sorting the array x, the array y will be also
     * rearranged as the same order of x.
     * @param x the array to sort.
     * @param y the associate array.
     */
    public static void sort(int[] x, double[] y) {
        sort(x, y, x.length);
    }

    /**
     * This is an efficient implementation Quick Sort algorithm without
     * recursive. Besides sorting the first n elements of array x, the first
     * n elements of array y will be also rearranged as the same order of x.
     * @param x the array to sort.
     * @param y the associate array.
     * @param n the first n elements to sort.
     */
    public static void sort(int[] x, double[] y, int n) {
        int jstack = -1;
        int l = 0;
        int[] istack = new int[NSTACK];
        int ir = n - 1;

        int i, j, k, a;
        double b;
        for (;;) {
            if (ir - l < M) {
                for (j = l + 1; j <= ir; j++) {
                    a = x[j];
                    b = y[j];
                    for (i = j - 1; i >= l; i--) {
                        if (x[i] <= a) {
                            break;
                        }
                        x[i + 1] = x[i];
                        y[i + 1] = y[i];
                    }
                    x[i + 1] = a;
                    y[i + 1] = b;
                }
                if (jstack < 0) {
                    break;
                }
                ir = istack[jstack--];
                l = istack[jstack--];
            } else {
                k = (l + ir) >> 1;
                Sort.swap(x, k, l + 1);
                Sort.swap(y, k, l + 1);
                if (x[l] > x[ir]) {
                    Sort.swap(x, l, ir);
                    Sort.swap(y, l, ir);
                }
                if (x[l + 1] > x[ir]) {
                    Sort.swap(x, l + 1, ir);
                    Sort.swap(y, l + 1, ir);
                }
                if (x[l] > x[l + 1]) {
                    Sort.swap(x, l, l + 1);
                    Sort.swap(y, l, l + 1);
                }
                i = l + 1;
                j = ir;
                a = x[l + 1];
                b = y[l + 1];
                for (;;) {
                    do {
                        i++;
                    } while (x[i] < a);
                    do {
                        j--;
                    } while (x[j] > a);
                    if (j < i) {
                        break;
                    }
                    Sort.swap(x, i, j);
                    Sort.swap(y, i, j);
                }
                x[l + 1] = x[j];
                x[j] = a;
                y[l + 1] = y[j];
                y[j] = b;
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
     * Besides sorting the array x, the array y will be also
     * rearranged as the same order of x.
     * @param x the array to sort.
     * @param y the associate array.
     */
    public static void sort(int[] x, Object[] y) {
        sort(x, y, x.length);
    }

    /**
     * This is an efficient implementation Quick Sort algorithm without
     * recursive. Besides sorting the first n elements of array x, the first
     * n elements of array y will be also rearranged as the same order of x.
     * @param x the array to sort.
     * @param y the associate array.
     * @param n the first n elements to sort.
     */
    public static void sort(int[] x, Object[] y, int n) {
        int jstack = -1;
        int l = 0;
        int[] istack = new int[NSTACK];
        int ir = n - 1;

        int i, j, k, a;
        Object b;
        for (;;) {
            if (ir - l < M) {
                for (j = l + 1; j <= ir; j++) {
                    a = x[j];
                    b = y[j];
                    for (i = j - 1; i >= l; i--) {
                        if (x[i] <= a) {
                            break;
                        }
                        x[i + 1] = x[i];
                        y[i + 1] = y[i];
                    }
                    x[i + 1] = a;
                    y[i + 1] = b;
                }
                if (jstack < 0) {
                    break;
                }
                ir = istack[jstack--];
                l = istack[jstack--];
            } else {
                k = (l + ir) >> 1;
                Sort.swap(x, k, l + 1);
                Sort.swap(y, k, l + 1);
                if (x[l] > x[ir]) {
                    Sort.swap(x, l, ir);
                    Sort.swap(y, l, ir);
                }
                if (x[l + 1] > x[ir]) {
                    Sort.swap(x, l + 1, ir);
                    Sort.swap(y, l + 1, ir);
                }
                if (x[l] > x[l + 1]) {
                    Sort.swap(x, l, l + 1);
                    Sort.swap(y, l, l + 1);
                }
                i = l + 1;
                j = ir;
                a = x[l + 1];
                b = y[l + 1];
                for (;;) {
                    do {
                        i++;
                    } while (x[i] < a);
                    do {
                        j--;
                    } while (x[j] > a);
                    if (j < i) {
                        break;
                    }
                    Sort.swap(x, i, j);
                    Sort.swap(y, i, j);
                }
                x[l + 1] = x[j];
                x[j] = a;
                y[l + 1] = y[j];
                y[j] = b;
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
     * @param x the array.
     * @return the original index of elements after sorting in range [0, n).
     */
    public static int[] sort(float[] x) {
        int[] order = new int[x.length];
        for (int i = 0; i < order.length; i++) {
            order[i] = i;
        }
        sort(x, order);
        return order;
    }

    /**
     * Besides sorting the array x, the array y will be also
     * rearranged as the same order of x.
     * @param x the array to sort.
     * @param y the associate array.
     */
    public static void sort(float[] x, int[] y) {
        sort(x, y, x.length);
    }

    /**
     * This is an efficient implementation Quick Sort algorithm without
     * recursive. Besides sorting the first n elements of array x, the first
     * n elements of array y will be also rearranged as the same order of x.
     * @param x the array to sort.
     * @param y the associate array.
     * @param n the first n elements to sort.
     */
    public static void sort(float[] x, int[] y, int n) {
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
                    a = x[j];
                    b = y[j];
                    for (i = j - 1; i >= l; i--) {
                        if (x[i] <= a) {
                            break;
                        }
                        x[i + 1] = x[i];
                        y[i + 1] = y[i];
                    }
                    x[i + 1] = a;
                    y[i + 1] = b;
                }
                if (jstack < 0) {
                    break;
                }
                ir = istack[jstack--];
                l = istack[jstack--];
            } else {
                k = (l + ir) >> 1;
                Sort.swap(x, k, l + 1);
                Sort.swap(y, k, l + 1);
                if (x[l] > x[ir]) {
                    Sort.swap(x, l, ir);
                    Sort.swap(y, l, ir);
                }
                if (x[l + 1] > x[ir]) {
                    Sort.swap(x, l + 1, ir);
                    Sort.swap(y, l + 1, ir);
                }
                if (x[l] > x[l + 1]) {
                    Sort.swap(x, l, l + 1);
                    Sort.swap(y, l, l + 1);
                }
                i = l + 1;
                j = ir;
                a = x[l + 1];
                b = y[l + 1];
                for (;;) {
                    do {
                        i++;
                    } while (x[i] < a);
                    do {
                        j--;
                    } while (x[j] > a);
                    if (j < i) {
                        break;
                    }
                    Sort.swap(x, i, j);
                    Sort.swap(y, i, j);
                }
                x[l + 1] = x[j];
                x[j] = a;
                y[l + 1] = y[j];
                y[j] = b;
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
     * Besides sorting the array x, the array y will be also
     * rearranged as the same order of x.
     * @param x the array to sort.
     * @param y the associate array.
     */
    public static void sort(float[] x, float[] y) {
        sort(x, y, x.length);
    }

    /**
     * This is an efficient implementation Quick Sort algorithm without
     * recursive. Besides sorting the first n elements of array x, the first
     * n elements of array y will be also rearranged as the same order of x.
     * @param x the array to sort.
     * @param y the associate array.
     * @param n the first n elements to sort.
     */
    public static void sort(float[] x, float[] y, int n) {
        int jstack = -1;
        int l = 0;
        int[] istack = new int[NSTACK];
        int ir = n - 1;

        int i, j, k;
        float a, b;
        for (;;) {
            if (ir - l < M) {
                for (j = l + 1; j <= ir; j++) {
                    a = x[j];
                    b = y[j];
                    for (i = j - 1; i >= l; i--) {
                        if (x[i] <= a) {
                            break;
                        }
                        x[i + 1] = x[i];
                        y[i + 1] = y[i];
                    }
                    x[i + 1] = a;
                    y[i + 1] = b;
                }
                if (jstack < 0) {
                    break;
                }
                ir = istack[jstack--];
                l = istack[jstack--];
            } else {
                k = (l + ir) >> 1;
                Sort.swap(x, k, l + 1);
                Sort.swap(y, k, l + 1);
                if (x[l] > x[ir]) {
                    Sort.swap(x, l, ir);
                    Sort.swap(y, l, ir);
                }
                if (x[l + 1] > x[ir]) {
                    Sort.swap(x, l + 1, ir);
                    Sort.swap(y, l + 1, ir);
                }
                if (x[l] > x[l + 1]) {
                    Sort.swap(x, l, l + 1);
                    Sort.swap(y, l, l + 1);
                }
                i = l + 1;
                j = ir;
                a = x[l + 1];
                b = y[l + 1];
                for (;;) {
                    do {
                        i++;
                    } while (x[i] < a);
                    do {
                        j--;
                    } while (x[j] > a);
                    if (j < i) {
                        break;
                    }
                    Sort.swap(x, i, j);
                    Sort.swap(y, i, j);
                }
                x[l + 1] = x[j];
                x[j] = a;
                y[l + 1] = y[j];
                y[j] = b;
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
     * Besides sorting the array x, the array y will be also
     * rearranged as the same order of x.
     * @param x the array to sort.
     * @param y the associate array.
     */
    public static void sort(float[] x, Object[] y) {
        sort(x, y, x.length);
    }

    /**
     * This is an efficient implementation Quick Sort algorithm without
     * recursive. Besides sorting the first n elements of array x, the first
     * n elements of array y will be also rearranged as the same order of x.
     * @param x the array to sort.
     * @param y the associate array.
     * @param n the first n elements to sort.
     */
    public static void sort(float[] x, Object[] y, int n) {
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
                    a = x[j];
                    b = y[j];
                    for (i = j - 1; i >= l; i--) {
                        if (x[i] <= a) {
                            break;
                        }
                        x[i + 1] = x[i];
                        y[i + 1] = y[i];
                    }
                    x[i + 1] = a;
                    y[i + 1] = b;
                }
                if (jstack < 0) {
                    break;
                }
                ir = istack[jstack--];
                l = istack[jstack--];
            } else {
                k = (l + ir) >> 1;
                Sort.swap(x, k, l + 1);
                Sort.swap(y, k, l + 1);
                if (x[l] > x[ir]) {
                    Sort.swap(x, l, ir);
                    Sort.swap(y, l, ir);
                }
                if (x[l + 1] > x[ir]) {
                    Sort.swap(x, l + 1, ir);
                    Sort.swap(y, l + 1, ir);
                }
                if (x[l] > x[l + 1]) {
                    Sort.swap(x, l, l + 1);
                    Sort.swap(y, l, l + 1);
                }
                i = l + 1;
                j = ir;
                a = x[l + 1];
                b = y[l + 1];
                for (;;) {
                    do {
                        i++;
                    } while (x[i] < a);
                    do {
                        j--;
                    } while (x[j] > a);
                    if (j < i) {
                        break;
                    }
                    Sort.swap(x, i, j);
                    Sort.swap(y, i, j);
                }
                x[l + 1] = x[j];
                x[j] = a;
                y[l + 1] = y[j];
                y[j] = b;
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
     * @param x the array to sort.
     */
    public static int[] sort(double[] x) {
        int[] order = new int[x.length];
        for (int i = 0; i < order.length; i++) {
            order[i] = i;
        }
        sort(x, order);
        return order;
    }

    /**
     * Besides sorting the array x, the array y will be also
     * rearranged as the same order of x.
     * @param x the array to sort.
     * @param y the associate array.
     */
    public static void sort(double[] x, int[] y) {
        sort(x, y, x.length);
    }

    /**
     * This is an efficient implementation Quick Sort algorithm without
     * recursive. Besides sorting the first n elements of array x, the first
     * n elements of array y will be also rearranged as the same order of x.
     * @param x the array to sort.
     * @param y the associate array.
     * @param n the first n elements to sort.
     */
    public static void sort(double[] x, int[] y, int n) {
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
                    a = x[j];
                    b = y[j];
                    for (i = j - 1; i >= l; i--) {
                        if (x[i] <= a) {
                            break;
                        }
                        x[i + 1] = x[i];
                        y[i + 1] = y[i];
                    }
                    x[i + 1] = a;
                    y[i + 1] = b;
                }
                if (jstack < 0) {
                    break;
                }
                ir = istack[jstack--];
                l = istack[jstack--];
            } else {
                k = (l + ir) >> 1;
                Sort.swap(x, k, l + 1);
                Sort.swap(y, k, l + 1);
                if (x[l] > x[ir]) {
                    Sort.swap(x, l, ir);
                    Sort.swap(y, l, ir);
                }
                if (x[l + 1] > x[ir]) {
                    Sort.swap(x, l + 1, ir);
                    Sort.swap(y, l + 1, ir);
                }
                if (x[l] > x[l + 1]) {
                    Sort.swap(x, l, l + 1);
                    Sort.swap(y, l, l + 1);
                }
                i = l + 1;
                j = ir;
                a = x[l + 1];
                b = y[l + 1];
                for (;;) {
                    do {
                        i++;
                    } while (x[i] < a);
                    do {
                        j--;
                    } while (x[j] > a);
                    if (j < i) {
                        break;
                    }
                    Sort.swap(x, i, j);
                    Sort.swap(y, i, j);
                }
                x[l + 1] = x[j];
                x[j] = a;
                y[l + 1] = y[j];
                y[j] = b;
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
     * This is an efficient implementation Quick Sort algorithm without
     * recursive. Besides sorting the array x, the array y will be also
     * rearranged as the same order of x.
     * @param x the array to sort.
     * @param y the associate array.
     */
    public static void sort(double[] x, double[] y) {
        sort(x, y, x.length);
    }

    /**
     * This is an efficient implementation Quick Sort algorithm without
     * recursive. Besides sorting the first n elements of array x, the first
     * n elements of array y will be also rearranged as the same order of x.
     * @param x the array to sort.
     * @param y the associate array.
     * @param n the first n elements to sort.
     */
    public static void sort(double[] x, double[] y, int n) {
        int jstack = -1;
        int l = 0;
        int[] istack = new int[NSTACK];
        int ir = n - 1;

        int i, j, k;
        double a, b;
        for (;;) {
            if (ir - l < M) {
                for (j = l + 1; j <= ir; j++) {
                    a = x[j];
                    b = y[j];
                    for (i = j - 1; i >= l; i--) {
                        if (x[i] <= a) {
                            break;
                        }
                        x[i + 1] = x[i];
                        y[i + 1] = y[i];
                    }
                    x[i + 1] = a;
                    y[i + 1] = b;
                }
                if (jstack < 0) {
                    break;
                }
                ir = istack[jstack--];
                l = istack[jstack--];
            } else {
                k = (l + ir) >> 1;
                Sort.swap(x, k, l + 1);
                Sort.swap(y, k, l + 1);
                if (x[l] > x[ir]) {
                    Sort.swap(x, l, ir);
                    Sort.swap(y, l, ir);
                }
                if (x[l + 1] > x[ir]) {
                    Sort.swap(x, l + 1, ir);
                    Sort.swap(y, l + 1, ir);
                }
                if (x[l] > x[l + 1]) {
                    Sort.swap(x, l, l + 1);
                    Sort.swap(y, l, l + 1);
                }
                i = l + 1;
                j = ir;
                a = x[l + 1];
                b = y[l + 1];
                for (;;) {
                    do {
                        i++;
                    } while (x[i] < a);
                    do {
                        j--;
                    } while (x[j] > a);
                    if (j < i) {
                        break;
                    }
                    Sort.swap(x, i, j);
                    Sort.swap(y, i, j);
                }
                x[l + 1] = x[j];
                x[j] = a;
                y[l + 1] = y[j];
                y[j] = b;
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
     * Besides sorting the array x, the array y will be also
     * rearranged as the same order of x.
     * @param x the array to sort.
     * @param y the associate array.
     */
    public static void sort(double[] x, Object[] y) {
        sort(x, y, x.length);
    }

    /**
     * This is an efficient implementation Quick Sort algorithm without
     * recursive. Besides sorting the first n elements of array x, the first
     * n elements of array y will be also rearranged as the same order of x.
     * @param x the array to sort.
     * @param y the associate array.
     * @param n the first n elements to sort.
     */
    public static void sort(double[] x, Object[] y, int n) {
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
                    a = x[j];
                    b = y[j];
                    for (i = j - 1; i >= l; i--) {
                        if (x[i] <= a) {
                            break;
                        }
                        x[i + 1] = x[i];
                        y[i + 1] = y[i];
                    }
                    x[i + 1] = a;
                    y[i + 1] = b;
                }
                if (jstack < 0) {
                    break;
                }
                ir = istack[jstack--];
                l = istack[jstack--];
            } else {
                k = (l + ir) >> 1;
                Sort.swap(x, k, l + 1);
                Sort.swap(y, k, l + 1);
                if (x[l] > x[ir]) {
                    Sort.swap(x, l, ir);
                    Sort.swap(y, l, ir);
                }
                if (x[l + 1] > x[ir]) {
                    Sort.swap(x, l + 1, ir);
                    Sort.swap(y, l + 1, ir);
                }
                if (x[l] > x[l + 1]) {
                    Sort.swap(x, l, l + 1);
                    Sort.swap(y, l, l + 1);
                }
                i = l + 1;
                j = ir;
                a = x[l + 1];
                b = y[l + 1];
                for (;;) {
                    do {
                        i++;
                    } while (x[i] < a);
                    do {
                        j--;
                    } while (x[j] > a);
                    if (j < i) {
                        break;
                    }
                    Sort.swap(x, i, j);
                    Sort.swap(y, i, j);
                }
                x[l + 1] = x[j];
                x[j] = a;
                y[l + 1] = y[j];
                y[j] = b;
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
     * @param x the array to sort.
     * @param <T> the data type of array elements.
     */
    public static <T extends Comparable<? super T>>  int[] sort(T[] x) {
        int[] order = new int[x.length];
        for (int i = 0; i < order.length; i++) {
            order[i] = i;
        }
        sort(x, order);
        return order;
    }

    /**
     * Besides sorting the array x, the array y will be also
     * rearranged as the same order of x.
     * @param x the array to sort.
     * @param y the associate array.
     * @param <T> the data type of array elements.
     */
    public static <T extends Comparable<? super T>>  void sort(T[] x, int[] y) {
        sort(x, y, x.length);
    }

    /**
     * This is an efficient implementation Quick Sort algorithm without
     * recursive. Besides sorting the first n elements of array x, the first
     * n elements of array y will be also rearranged as the same order of x.
     * @param x the array to sort.
     * @param y the associate array.
     * @param n the first n elements to sort.
     * @param <T> the data type of array elements.
     */
    public static <T extends Comparable<? super T>>  void sort(T[] x, int[] y, int n) {
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
                    a = x[j];
                    b = y[j];
                    for (i = j - 1; i >= l; i--) {
                        if (x[i].compareTo(a) <= 0) {
                            break;
                        }
                        x[i + 1] = x[i];
                        y[i + 1] = y[i];
                    }
                    x[i + 1] = a;
                    y[i + 1] = b;
                }
                if (jstack < 0) {
                    break;
                }
                ir = istack[jstack--];
                l = istack[jstack--];
            } else {
                k = (l + ir) >> 1;
                Sort.swap(x, k, l + 1);
                Sort.swap(y, k, l + 1);
                if (x[l].compareTo(x[ir]) > 0) {
                    Sort.swap(x, l, ir);
                    Sort.swap(y, l, ir);
                }
                if (x[l + 1].compareTo(x[ir]) > 0) {
                    Sort.swap(x, l + 1, ir);
                    Sort.swap(y, l + 1, ir);
                }
                if (x[l].compareTo(x[l + 1]) > 0) {
                    Sort.swap(x, l, l + 1);
                    Sort.swap(y, l, l + 1);
                }
                i = l + 1;
                j = ir;
                a = x[l + 1];
                b = y[l + 1];
                for (;;) {
                    do {
                        i++;
                    } while (x[i].compareTo(a) < 0);
                    do {
                        j--;
                    } while (x[j].compareTo(a) > 0);
                    if (j < i) {
                        break;
                    }
                    Sort.swap(x, i, j);
                    Sort.swap(y, i, j);
                }
                x[l + 1] = x[j];
                x[j] = a;
                y[l + 1] = y[j];
                y[j] = b;
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
     * This is an efficient implementation Quick Sort algorithm without
     * recursive. Besides sorting the first n elements of array x, the first
     * n elements of array y will be also rearranged as the same order of x.
     * @param x the array to sort.
     * @param y the associate array.
     * @param n the first n elements to sort.
     * @param comparator the comparator.
     * @param <T> the data type of array elements.
     */
    public static <T>  void sort(T[] x, int[] y, int n, Comparator<T> comparator) {
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
                    a = x[j];
                    b = y[j];
                    for (i = j - 1; i >= l; i--) {
                        if (comparator.compare(x[i], a) <= 0) {
                            break;
                        }
                        x[i + 1] = x[i];
                        y[i + 1] = y[i];
                    }
                    x[i + 1] = a;
                    y[i + 1] = b;
                }
                if (jstack < 0) {
                    break;
                }
                ir = istack[jstack--];
                l = istack[jstack--];
            } else {
                k = (l + ir) >> 1;
                Sort.swap(x, k, l + 1);
                Sort.swap(y, k, l + 1);
                if (comparator.compare(x[l], x[ir]) > 0) {
                    Sort.swap(x, l, ir);
                    Sort.swap(y, l, ir);
                }
                if (comparator.compare(x[l + 1], x[ir]) > 0) {
                    Sort.swap(x, l + 1, ir);
                    Sort.swap(y, l + 1, ir);
                }
                if (comparator.compare(x[l], x[l + 1]) > 0) {
                    Sort.swap(x, l, l + 1);
                    Sort.swap(y, l, l + 1);
                }
                i = l + 1;
                j = ir;
                a = x[l + 1];
                b = y[l + 1];
                for (;;) {
                    do {
                        i++;
                    } while (comparator.compare(x[i], a) < 0);
                    do {
                        j--;
                    } while (comparator.compare(x[j], a) > 0);
                    if (j < i) {
                        break;
                    }
                    Sort.swap(x, i, j);
                    Sort.swap(y, i, j);
                }
                x[l + 1] = x[j];
                x[j] = a;
                y[l + 1] = y[j];
                y[j] = b;
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
     * Besides sorting the array x, the array y will be also
     * rearranged as the same order of x.
     * @param x the array to sort.
     * @param y the associate array.
     * @param <T> the data type of array elements.
     */
    public static <T extends Comparable<? super T>>  void sort(T[] x, Object[] y) {
        sort(x, y, x.length);
    }

    /**
     * This is an efficient implementation Quick Sort algorithm without
     * recursive. Besides sorting the first n elements of array x, the first
     * n elements of array y will be also rearranged as the same order of x.
     * @param x the array to sort.
     * @param y the associate array.
     * @param n the first n elements to sort.
     * @param <T> the data type of array elements.
     */
    public static <T extends Comparable<? super T>>  void sort(T[] x, Object[] y, int n) {
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
                    a = x[j];
                    b = y[j];
                    for (i = j - 1; i >= l; i--) {
                        if (x[i].compareTo(a) <= 0) {
                            break;
                        }
                        x[i + 1] = x[i];
                        y[i + 1] = y[i];
                    }
                    x[i + 1] = a;
                    y[i + 1] = b;
                }
                if (jstack < 0) {
                    break;
                }
                ir = istack[jstack--];
                l = istack[jstack--];
            } else {
                k = (l + ir) >> 1;
                Sort.swap(x, k, l + 1);
                Sort.swap(y, k, l + 1);
                if (x[l].compareTo(x[ir]) > 0) {
                    Sort.swap(x, l, ir);
                    Sort.swap(y, l, ir);
                }
                if (x[l + 1].compareTo(x[ir]) > 0) {
                    Sort.swap(x, l + 1, ir);
                    Sort.swap(y, l + 1, ir);
                }
                if (x[l].compareTo(x[l + 1]) > 0) {
                    Sort.swap(x, l, l + 1);
                    Sort.swap(y, l, l + 1);
                }
                i = l + 1;
                j = ir;
                a = x[l + 1];
                b = y[l + 1];
                for (;;) {
                    do {
                        i++;
                    } while (x[i].compareTo(a) < 0);
                    do {
                        j--;
                    } while (x[j].compareTo(a) > 0);
                    if (j < i) {
                        break;
                    }
                    Sort.swap(x, i, j);
                    Sort.swap(y, i, j);
                }
                x[l + 1] = x[j];
                x[j] = a;
                y[l + 1] = y[j];
                y[j] = b;
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
