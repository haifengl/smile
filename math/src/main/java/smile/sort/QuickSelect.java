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
 * Selection is asking for the k th smallest element out of n elements.
 * This class implements the fastest general method for selection based on
 * partitioning, exactly as done in the Quicksort algorithm.
 * <p>
 * The most common use of selection is in the statistical characterization of
 * a set of data. One often wants to know the median element (quantile p = 1/2)
 * in an array or the top and bottom quartile elements (quantile p = 1/4, 3/4).
 * 
 * @author Haifeng Li
 */
public class QuickSelect {
    /** Utility classes should not have public constructors. */
    private QuickSelect() {

    }

    /**
     * Given k in [0, n-1], returns an array value from arr such that k array
     * values are less than or equal to the one returned. The input array will
     * be rearranged to have this value in location arr[k], with all smaller
     * elements moved to arr[0, k-1] (in arbitrary order) and all larger elements
     * in arr[k+1, n-1] (also in arbitrary order).
     */
    public static int select(int[] arr, int k) {
        int n = arr.length;
        int l = 0;
        int ir = n - 1;

        int a;
        int i, j, mid;
        for (;;) {
            if (ir <= l + 1) {
                if (ir == l + 1 && arr[ir] < arr[l]) {
                    SortUtils.swap(arr, l, ir);
                }
                return arr[k];
            } else {
                mid = (l + ir) >> 1;
                SortUtils.swap(arr, mid, l + 1);
                if (arr[l] > arr[ir]) {
                    SortUtils.swap(arr, l, ir);
                }
                if (arr[l + 1] > arr[ir]) {
                    SortUtils.swap(arr, l + 1, ir);
                }
                if (arr[l] > arr[l + 1]) {
                    SortUtils.swap(arr, l, l + 1);
                }
                i = l + 1;
                j = ir;
                a = arr[l + 1];
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
                }
                arr[l + 1] = arr[j];
                arr[j] = a;
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
     * be rearranged to have this value in location arr[k], with all smaller
     * elements moved to arr[0, k-1] (in arbitrary order) and all larger elements
     * in arr[k+1, n-1] (also in arbitrary order).
     */
    public static float select(float[] arr, int k) {
        int n = arr.length;
        int l = 0;
        int ir = n - 1;

        float a;
        int i, j, mid;
        for (;;) {
            if (ir <= l + 1) {
                if (ir == l + 1 && arr[ir] < arr[l]) {
                    SortUtils.swap(arr, l, ir);
                }
                return arr[k];
            } else {
                mid = (l + ir) >> 1;
                SortUtils.swap(arr, mid, l + 1);
                if (arr[l] > arr[ir]) {
                    SortUtils.swap(arr, l, ir);
                }
                if (arr[l + 1] > arr[ir]) {
                    SortUtils.swap(arr, l + 1, ir);
                }
                if (arr[l] > arr[l + 1]) {
                    SortUtils.swap(arr, l, l + 1);
                }
                i = l + 1;
                j = ir;
                a = arr[l + 1];
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
                }
                arr[l + 1] = arr[j];
                arr[j] = a;
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
     * be rearranged to have this value in location arr[k], with all smaller
     * elements moved to arr[0, k-1] (in arbitrary order) and all larger elements
     * in arr[k+1, n-1] (also in arbitrary order).
     */
    public static double select(double[] arr, int k) {
        int n = arr.length;
        int l = 0;
        int ir = n - 1;

        double a;
        int i, j, mid;
        for (;;) {
            if (ir <= l + 1) {
                if (ir == l + 1 && arr[ir] < arr[l]) {
                    SortUtils.swap(arr, l, ir);
                }
                return arr[k];
            } else {
                mid = (l + ir) >> 1;
                SortUtils.swap(arr, mid, l + 1);
                if (arr[l] > arr[ir]) {
                    SortUtils.swap(arr, l, ir);
                }
                if (arr[l + 1] > arr[ir]) {
                    SortUtils.swap(arr, l + 1, ir);
                }
                if (arr[l] > arr[l + 1]) {
                    SortUtils.swap(arr, l, l + 1);
                }
                i = l + 1;
                j = ir;
                a = arr[l + 1];
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
                }
                arr[l + 1] = arr[j];
                arr[j] = a;
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
     * be rearranged to have this value in location arr[k], with all smaller
     * elements moved to arr[0, k-1] (in arbitrary order) and all larger elements
     * in arr[k+1, n-1] (also in arbitrary order).
     */
    public static <T extends Comparable<? super T>> T select(T[] arr, int k) {
        int n = arr.length;
        int l = 0;
        int ir = n - 1;

        T a;
        int i, j, mid;
        for (;;) {
            if (ir <= l + 1) {
                if (ir == l + 1 && arr[ir].compareTo(arr[l]) < 0) {
                    SortUtils.swap(arr, l, ir);
                }
                return arr[k];
            } else {
                mid = (l + ir) >> 1;
                SortUtils.swap(arr, mid, l + 1);
                if (arr[l].compareTo(arr[ir]) > 0) {
                    SortUtils.swap(arr, l, ir);
                }
                if (arr[l + 1].compareTo(arr[ir]) > 0) {
                    SortUtils.swap(arr, l + 1, ir);
                }
                if (arr[l].compareTo(arr[l + 1]) > 0) {
                    SortUtils.swap(arr, l, l + 1);
                }
                i = l + 1;
                j = ir;
                a = arr[l + 1];
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
                }
                arr[l + 1] = arr[j];
                arr[j] = a;
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
     */
    public static int median(int[] a) {
        int k = a.length / 2;
        return select(a, k);
    }

    /**
     * Find the median of an array of type float.
     */
    public static float median(float[] a) {
        int k = a.length / 2;
        return select(a, k);
    }

    /**
     * Find the median of an array of type double.
     */
    public static double median(double[] a) {
        int k = a.length / 2;
        return select(a, k);
    }

    /**
     * Find the median of an array of type double.
     */
    public static <T extends Comparable<? super T>> T median(T[] a) {
        int k = a.length / 2;
        return select(a, k);
    }

    /**
     * Find the first quantile (p = 1/4) of an array of type integer.
     */
    public static int q1(int[] a) {
        int k = a.length / 4;
        return select(a, k);
    }

    /**
     * Find the first quantile (p = 1/4) of an array of type float.
     */
    public static float q1(float[] a) {
        int k = a.length / 4;
        return select(a, k);
    }

    /**
     * Find the first quantile (p = 1/4) of an array of type double.
     */
    public static double q1(double[] a) {
        int k = a.length / 4;
        return select(a, k);
    }

    /**
     * Find the first quantile (p = 1/4) of an array of type double.
     */
    public static <T extends Comparable<? super T>> T q1(T[] a) {
        int k = a.length / 4;
        return select(a, k);
    }

    /**
     * Find the third quantile (p = 3/4) of an array of type integer.
     */
    public static int q3(int[] a) {
        int k = 3 * a.length / 4;
        return select(a, k);
    }

    /**
     * Find the third quantile (p = 3/4) of an array of type float.
     */
    public static float q3(float[] a) {
        int k = 3 * a.length / 4;
        return select(a, k);
    }

    /**
     * Find the third quantile (p = 3/4) of an array of type double.
     */
    public static double q3(double[] a) {
        int k = 3 * a.length / 4;
        return select(a, k);
    }

    /**
     * Find the third quantile (p = 3/4) of an array of type double.
     */
    public static <T extends Comparable<? super T>> T q3(T[] a) {
        int k = 3 * a.length / 4;
        return select(a, k);
    }
}
