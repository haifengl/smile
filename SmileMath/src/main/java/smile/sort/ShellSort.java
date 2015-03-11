/******************************************************************************
 *                   Confidential Proprietary                                 *
 *         (c) Copyright Haifeng Li 2011, All Rights Reserved                 *
 ******************************************************************************/

package smile.sort;

/**
 * Shell sort is a sorting algorithm that is a generalization of insertion
 * sort, with two observations:
 * <ul>
 * <li> insertion sort is efficient if the input is "almost sorted", and
 * <li> insertion sort is typically inefficient because it moves values
 * just one position at a time.
 * </ul>
 * Shell sort improves insertion sort by comparing elements separated by
 * a gap of several positions. This lets an element take "bigger steps"
 * toward its expected position. Multiple passes over the data are taken
 * with smaller and smaller gap sizes. The last step of Shell sort is a
 * plain insertion sort, but by then, the array of data is guaranteed to be
 * almost sorted.
 * <p>
 * The original implementation performs O(n<sup>2</sup>) comparisons and
 * exchanges in the worst case. A minor change given in V. Pratt's book
 * improved the bound to O(n log<sub><small>2</small></sub> n). This is worse than the
 * optimal comparison sorts, which are O(n log n).
 * <p>
 * For n < 50, roughly, Shell sort is competitive with the more complicated
 * Quicksort on many machines. For n > 50, Quicksort is generally faster.
 * 
 * @author Haifeng Li
 */
public class ShellSort {

    /**
     * Sorts the specified array into ascending numerical order.
     */
    public static void sort(int[] a) {
        int n = a.length;

        int inc = 1;
        do {
            inc *= 3;
            inc++;
        } while (inc <= n);

        do {
            inc /= 3;
            for (int i = inc; i < n; i++) {
                int v = a[i];
                int j = i;
                while (a[j - inc] > v) {
                    a[j] = a[j - inc];
                    j -= inc;
                    if (j < inc) {
                        break;
                    }
                }
                a[j] = v;
            }
        } while (inc > 1);
    }

    /**
     * Sorts the specified array into ascending numerical order.
     */
    public static void sort(float[] a) {
        int n = a.length;

        int inc = 1;
        do {
            inc *= 3;
            inc++;
        } while (inc <= n);

        do {
            inc /= 3;
            for (int i = inc; i < n; i++) {
                float v = a[i];
                int j = i;
                while (a[j - inc] > v) {
                    a[j] = a[j - inc];
                    j -= inc;
                    if (j < inc) {
                        break;
                    }
                }
                a[j] = v;
            }
        } while (inc > 1);
    }

    /**
     * Sorts the specified array into ascending numerical order.
     */
    public static void sort(double[] a) {
        int n = a.length;

        int inc = 1;
        do {
            inc *= 3;
            inc++;
        } while (inc <= n);

        do {
            inc /= 3;
            for (int i = inc; i < n; i++) {
                double v = a[i];
                int j = i;
                while (a[j - inc] > v) {
                    a[j] = a[j - inc];
                    j -= inc;
                    if (j < inc) {
                        break;
                    }
                }
                a[j] = v;
            }
        } while (inc > 1);
    }

    /**
     * Sorts the specified array into ascending order.
     */
    public static <T extends Comparable<? super T>> void sort(T[] a) {
        int n = a.length;

        int inc = 1;
        do {
            inc *= 3;
            inc++;
        } while (inc <= n);

        do {
            inc /= 3;
            for (int i = inc; i < n; i++) {
                T v = a[i];
                int j = i;
                while (a[j - inc].compareTo(v) > 0) {
                    a[j] = a[j - inc];
                    j -= inc;
                    if (j < inc) {
                        break;
                    }
                }
                a[j] = v;
            }
        } while (inc > 1);
    }
}
