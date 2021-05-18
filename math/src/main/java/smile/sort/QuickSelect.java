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
 * Selection is asking for the k-th smallest element out of n elements.
 * This class implements the fastest general method for selection based on
 * partitioning, exactly as done in the Quicksort algorithm.
 * <p>
 * The most common use of selection is in the statistical characterization of
 * a set of data. One often wants to know the median element (quantile p = 1/2)
 * in an array or the top and bottom quartile elements (quantile p = 1/4, 3/4).
 * 
 * @author Haifeng Li
 */
public interface QuickSelect {
    /**
     * Given k in [0, n-1], returns an array value from arr such that k array
     * values are less than or equal to the one returned. The input array will
     * be rearranged to have this value in location x[k], with all smaller
     * elements moved to x[0, k-1] (in arbitrary order) and all larger elements
     * in x[k+1, n-1] (also in arbitrary order).
     * @param x the array.
     * @param k the ordinal index.
     * @return the k-th smalles value.
     */
    static int select(int[] x, int k) {
        int n = x.length;
        int l = 0;
        int ir = n - 1;

        int a;
        int i, j, mid;
        for (;;) {
            if (ir <= l + 1) {
                if (ir == l + 1 && x[ir] < x[l]) {
                    Sort.swap(x, l, ir);
                }
                return x[k];
            } else {
                mid = (l + ir) >> 1;
                Sort.swap(x, mid, l + 1);
                if (x[l] > x[ir]) {
                    Sort.swap(x, l, ir);
                }
                if (x[l + 1] > x[ir]) {
                    Sort.swap(x, l + 1, ir);
                }
                if (x[l] > x[l + 1]) {
                    Sort.swap(x, l, l + 1);
                }
                i = l + 1;
                j = ir;
                a = x[l + 1];
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
                }
                x[l + 1] = x[j];
                x[j] = a;
                if (j >= k) {
                    ir = j - 1;
                }
                if (j <= k) {
                    l = i;
                }
            }
        }
    }

    /**
     * Given k in [0, n-1], returns an array value from arr such that k array
     * values are less than or equal to the one returned. The input array will
     * be rearranged to have this value in location x[k], with all smaller
     * elements moved to x[0, k-1] (in arbitrary order) and all larger elements
     * in x[k+1, n-1] (also in arbitrary order).
     * @param x the array.
     * @param k the ordinal index.
     * @return the k-th smalles value.
     */
    static float select(float[] x, int k) {
        int n = x.length;
        int l = 0;
        int ir = n - 1;

        float a;
        int i, j, mid;
        for (;;) {
            if (ir <= l + 1) {
                if (ir == l + 1 && x[ir] < x[l]) {
                    Sort.swap(x, l, ir);
                }
                return x[k];
            } else {
                mid = (l + ir) >> 1;
                Sort.swap(x, mid, l + 1);
                if (x[l] > x[ir]) {
                    Sort.swap(x, l, ir);
                }
                if (x[l + 1] > x[ir]) {
                    Sort.swap(x, l + 1, ir);
                }
                if (x[l] > x[l + 1]) {
                    Sort.swap(x, l, l + 1);
                }
                i = l + 1;
                j = ir;
                a = x[l + 1];
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
                }
                x[l + 1] = x[j];
                x[j] = a;
                if (j >= k) {
                    ir = j - 1;
                }
                if (j <= k) {
                    l = i;
                }
            }
        }
    }

    /**
     * Given k in [0, n-1], returns an array value from arr such that k array
     * values are less than or equal to the one returned. The input array will
     * be rearranged to have this value in location x[k], with all smaller
     * elements moved to x[0, k-1] (in arbitrary order) and all larger elements
     * in x[k+1, n-1] (also in arbitrary order).
     * @param x the array.
     * @param k the ordinal index.
     * @return the k-th smalles value.
     */
    static double select(double[] x, int k) {
        int n = x.length;
        int l = 0;
        int ir = n - 1;

        double a;
        int i, j, mid;
        for (;;) {
            if (ir <= l + 1) {
                if (ir == l + 1 && x[ir] < x[l]) {
                    Sort.swap(x, l, ir);
                }
                return x[k];
            } else {
                mid = (l + ir) >> 1;
                Sort.swap(x, mid, l + 1);
                if (x[l] > x[ir]) {
                    Sort.swap(x, l, ir);
                }
                if (x[l + 1] > x[ir]) {
                    Sort.swap(x, l + 1, ir);
                }
                if (x[l] > x[l + 1]) {
                    Sort.swap(x, l, l + 1);
                }
                i = l + 1;
                j = ir;
                a = x[l + 1];
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
                }
                x[l + 1] = x[j];
                x[j] = a;
                if (j >= k) {
                    ir = j - 1;
                }
                if (j <= k) {
                    l = i;
                }
            }
        }
    }

    /**
     * Given k in [0, n-1], returns an array value from arr such that k array
     * values are less than or equal to the one returned. The input array will
     * be rearranged to have this value in location x[k], with all smaller
     * elements moved to x[0, k-1] (in arbitrary order) and all larger elements
     * in x[k+1, n-1] (also in arbitrary order).
     * @param x the array.
     * @param k the ordinal index.
     * @param <T> the data type of array elements.
     * @return the k-th smalles value.
     */
    static <T extends Comparable<? super T>> T select(T[] x, int k) {
        int n = x.length;
        int l = 0;
        int ir = n - 1;

        T a;
        int i, j, mid;
        for (;;) {
            if (ir <= l + 1) {
                if (ir == l + 1 && x[ir].compareTo(x[l]) < 0) {
                    Sort.swap(x, l, ir);
                }
                return x[k];
            } else {
                mid = (l + ir) >> 1;
                Sort.swap(x, mid, l + 1);
                if (x[l].compareTo(x[ir]) > 0) {
                    Sort.swap(x, l, ir);
                }
                if (x[l + 1].compareTo(x[ir]) > 0) {
                    Sort.swap(x, l + 1, ir);
                }
                if (x[l].compareTo(x[l + 1]) > 0) {
                    Sort.swap(x, l, l + 1);
                }
                i = l + 1;
                j = ir;
                a = x[l + 1];
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
                }
                x[l + 1] = x[j];
                x[j] = a;
                if (j >= k) {
                    ir = j - 1;
                }
                if (j <= k) {
                    l = i;
                }
            }
        }
    }

    /**
     * Find the median of an array of type integer.
     * @param x the array.
     * @return the median.
     */
    static int median(int[] x) {
        int k = x.length / 2;
        return select(x, k);
    }

    /**
     * Find the median of an array of type float.
     * @param x the array.
     * @return the median.
     */
    static float median(float[] x) {
        int k = x.length / 2;
        return select(x, k);
    }

    /**
     * Find the median of an array of type double.
     * @param x the array.
     * @return the median.
     */
    static double median(double[] x) {
        int k = x.length / 2;
        return select(x, k);
    }

    /**
     * Find the median of an array of type double.
     * @param x the array.
     * @param <T> the data type of array elements.
     * @return the median.
     */
    static <T extends Comparable<? super T>> T median(T[] x) {
        int k = x.length / 2;
        return select(x, k);
    }

    /**
     * Find the first quantile (p = 1/4) of an array of type integer.
     * @param x the array.
     * @return the first quantile.
     */
    static int q1(int[] x) {
        int k = x.length / 4;
        return select(x, k);
    }

    /**
     * Find the first quantile (p = 1/4) of an array of type float.
     * @param x the array.
     * @return the first quantile.
     */
    static float q1(float[] x) {
        int k = x.length / 4;
        return select(x, k);
    }

    /**
     * Find the first quantile (p = 1/4) of an array of type double.
     * @param x the array.
     * @return the first quantile.
     */
    static double q1(double[] x) {
        int k = x.length / 4;
        return select(x, k);
    }

    /**
     * Find the first quantile (p = 1/4) of an array of type double.
     * @param x the array.
     * @param <T> the data type of array elements.
     * @return the first quantile.
     */
    static <T extends Comparable<? super T>> T q1(T[] x) {
        int k = x.length / 4;
        return select(x, k);
    }

    /**
     * Find the third quantile (p = 3/4) of an array of type integer.
     * @param x the array.
     * @return the third quantile.
     */
    static int q3(int[] x) {
        int k = 3 * x.length / 4;
        return select(x, k);
    }

    /**
     * Find the third quantile (p = 3/4) of an array of type float.
     * @param x the array.
     * @return the third quantile.
     */
    static float q3(float[] x) {
        int k = 3 * x.length / 4;
        return select(x, k);
    }

    /**
     * Find the third quantile (p = 3/4) of an array of type double.
     * @param x the array.
     * @return the third quantile.
     */
    static double q3(double[] x) {
        int k = 3 * x.length / 4;
        return select(x, k);
    }

    /**
     * Find the third quantile (p = 3/4) of an array of type double.
     * @param x the array.
     * @param <T> the data type of array elements.
     * @return the third quantile.
     */
    static <T extends Comparable<? super T>> T q3(T[] x) {
        int k = 3 * x.length / 4;
        return select(x, k);
    }
}
