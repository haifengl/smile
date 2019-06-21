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
 * Shell sort is a generalization of insertion sort.
 * For n &lt; 50, roughly, Shell sort is competitive with the more complicated
 * Quicksort on many machines. For n &gt; 50, Quicksort is generally faster.
 * <p>
 * Shell sort is based on two observations:
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
 * improved the bound to O(n log<sub><small>2</small></sub> n). This is
 * worse than the optimal comparison sorts, which are O(n log n).
 *
 * @author Haifeng Li
 */
public interface ShellSort {
    /**
     * Sorts the specified array into ascending numerical order.
     */
    static void sort(int[] a) {
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
    static void sort(float[] a) {
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
    static void sort(double[] a) {
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
    static <T extends Comparable<? super T>> void sort(T[] a) {
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
