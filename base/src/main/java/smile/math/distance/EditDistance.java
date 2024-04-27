/*
 * Copyright (c) 2010-2021 Haifeng Li. All rights reserved.
 *
 * Smile is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Smile is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Smile.  If not, see <https://www.gnu.org/licenses/>.
 */

package smile.math.distance;

import smile.math.MathEx;
import smile.util.IntArray2D;

import java.io.Serial;

/**
 * The Edit distance between two strings is a metric for measuring the amount
 * of difference between two sequences. The Levenshtein distance between two
 * strings is given by the minimum number of operations needed to transform one
 * string into the other, where an operation is an insertion, deletion, or
 * substitution of a single character. A generalization of the Levenshtein
 * distance (Damerau-Levenshtein distance) allows the transposition of two
 * characters as an operation.
 * <p>
 * Given two strings x and y of length m and n (suppose {@code n >= m}), this
 * implementation takes O(ne) time and O(mn) space by an extended Ukkonen's
 * algorithm in case of unit cost, where e is the edit distance between x and y.
 * Thus, this algorithm is output sensitive. The smaller the distance, the faster
 * it runs.
 * <p>
 * For weighted cost, this implements the regular dynamic programming algorithm,
 * which takes O(mn) time and O(m) space.
 *
 * @author Haifeng Li
 */
public class EditDistance implements Metric<String> {
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * Weight matrix for weighted Levenshtein distance.
     */
    private IntArray2D weight;

    /**
     * Radius of Sakoe-Chiba band
     */
    private double r = -1;

    /**
     * Calculate Damerau or basic Levenshitein distance.
     */
    private boolean damerau = false;

    /**
     * Cost matrix. Because Java automatically initialize arrays, it
     * takes O(mn) to declare this cost matrix every time before
     * calculate edit distance. But the whole point of Berghel and Roach
     * algorithm is to calculate fewer cells than O(mn). Therefore,
     * we create this cost matrix here. Therefore, the methods using
     * this cost matrix is not multi-thread safe.
     */
    private IntArray2D FKP;

    /**
     * The lambda to calculate FKP array.
     */
    private BRF brf;


    /**
     * Constructor. Multi-thread safe Levenshtein distance.
     */
    public EditDistance() {
        this(false);
    }

    /**
     * Constructor. Multi-thread safe Damerau-Levenshtein distance.
     * @param damerau if true, calculate Damerau-Levenshtein distance
     *                instead of plain Levenshtein distance.
     */
    public EditDistance(boolean damerau) {
        this.damerau = damerau;
    }

    /**
     * Constructor. Highly efficient Levenshtein distance but not multi-thread safe.
     * @param maxStringLength the maximum length of strings that will be
     * feed to this algorithm.
     */
    public EditDistance(int maxStringLength) {
        this(maxStringLength, false);
    }

    /**
     * Constructor. Highly efficient Damerau-Levenshtein distance but not multi-thread safe.
     * @param maxStringLength the maximum length of strings that will be
     *                        feed to this algorithm.
     * @param damerau if true, calculate Damerau-Levenshtein distance
     *                instead of plain Levenshtein distance.
     */
    public EditDistance(int maxStringLength, boolean damerau) {
        this.damerau = damerau;
        FKP = new IntArray2D(2*maxStringLength+1, maxStringLength+2);
        brf = damerau ? new DamerauBRF() : new LevenshteinBRF();
    }

    /**
     * Constructor. Weighted Levenshtein distance without path
     * constraints. Only insertion, deletion, and substitution operations are
     * supported.
     * @param weight the weight matrix.
     */
    public EditDistance(int[][] weight) {
        this(weight, -1);
    }

    /**
     * Constructor. Weighted Levenshtein distance with
     * Sakoe-Chiba band, which improve computational cost. Only
     * insertion, deletion, and substitution operations are supported.
     * @param weight the weight matrix.
     * @param radius the window width of Sakoe-Chiba band in terms of percentage of sequence length.
     */
    public EditDistance(int[][] weight, double radius) {
        this.weight = new IntArray2D(weight);
        this.r = radius;
    }

    @Override
    public String toString() {
        if (damerau) {
            if (weight != null)
                return String.format("Damerau-Levenshtein Distance(radius = %f, weight = %s)", r, weight);
            else
                return "Damerau-Levenshtein Distance";
        } else {
            if (weight != null)
                return String.format("Levenshtein Distance(radius = %f, weight = %s)", r, weight);
            else
                return  "Levenshtein Distance";
        }
    }

    /**
     * Edit distance between two strings. O(mn) time and O(n) space for weighted
     * edit distance. O(ne) time and O(mn) space for unit cost edit distance.
     * For weighted edit distance, this method is multi-thread safe. However,
     * it is NOT multi-thread safe for unit cost edit distance.
     */
    @Override
    public double d(String a, String b) {
        if (weight != null)
            return weightedEdit(a, b);
        else if (FKP == null || a.length() == 1 || b.length() == 1)
            return damerau ? damerau(a, b) : levenshtein(a, b);
        else
            return br(a, b);
    }

    /**
     * Edit distance between two strings. O(mn) time and O(n) space for weighted
     * edit distance. O(ne) time and O(mn) space for unit cost edit distance.
     * For weighted edit distance, this method is multi-thread safe. However,
     * it is NOT multi-thread safe for unit cost edit distance.
     * @param a a string.
     * @param b a string.
     * @return the distance.
     */
    public double d(char[] a, char[] b) {
        if (weight != null) {
            return weightedEdit(a, b);
        } else if (FKP == null || a.length == 1 || b.length == 1) {
            return damerau ? damerau(a, b) : levenshtein(a, b);
        } else {
            return br(a, b);
        }
    }

    /**
     * Weighted edit distance.
     * @param a a string.
     * @param b a string.
     * @return the distance.
     */
    private double weightedEdit(char[] a, char[] b) {
        // switch parameters to use the shorter one as y to save space.
        if (a.length < b.length) {
            char[] swap = a;
            a = b;
            b = swap;
        }

        int radius = (int) Math.round(r * a.length);

        double[][] d = new double[2][b.length + 1];

        d[0][0] = 0.0;
        for (int j = 1; j <= b.length; j++) {
            d[0][j] = d[0][j - 1] + weight.get(0, b[j]);
        }

        for (int i = 1; i <= a.length; i++) {
            d[1][0] = d[0][0] + weight.get(a[i], 0);

            int start = 1;
            int end = b.length;

            if (radius > 0) {
                start = i - radius;
                if (start > 1)
                    d[1][start - 1] = Double.POSITIVE_INFINITY;
                else
                    start = 1;

                end = i + radius;
                if (end < b.length)
                    d[1][end+1] = Double.POSITIVE_INFINITY;
                else
                    end = b.length;
            }

            for (int j = start; j <= end; j++) {
                double cost = weight.get(a[i - 1], b[j - 1]);
                d[1][j] = MathEx.min(
                        d[0][j] + weight.get(a[i - 1], 0), // deletion
                        d[1][j - 1] + weight.get(0, b[j - 1]), // insertion
                        d[0][j - 1] + cost); // substitution
            }

            double[] swap = d[0];
            d[0] = d[1];
            d[1] = swap;
        }

        return d[0][b.length];
    }

    /**
     * Weighted edit distance.
     * @param a a string.
     * @param b a string.
     * @return the distance.
     */
    private double weightedEdit(String a, String b) {
        // switch parameters to use the shorter one as y to save space.
        if (a.length() < b.length()) {
            String swap = a;
            a = b;
            b = swap;
        }

        int radius = (int) Math.round(r * a.length());

        double[][] d = new double[2][b.length() + 1];

        d[0][0] = 0.0;
        for (int j = 1; j <= b.length(); j++) {
            d[0][j] = d[0][j - 1] + weight.get(0, b.charAt(j));
        }

        for (int i = 1; i <= a.length(); i++) {
            d[1][0] = d[0][0] + weight.get(a.charAt(i), 0);

            int start = 1;
            int end = b.length();

            if (radius > 0) {
                start = i - radius;
                if (start > 1)
                    d[1][start - 1] = Double.POSITIVE_INFINITY;
                else
                    start = 1;

                end = i + radius;
                if (end < b.length())
                    d[1][end+1] = Double.POSITIVE_INFINITY;
                else
                    end = b.length();
            }

            for (int j = start; j <= end; j++) {
                double cost = weight.get(a.charAt(i - 1), b.charAt(j - 1));
                d[1][j] = MathEx.min(
                        d[0][j] + weight.get(a.charAt(i - 1), 0), // deletion
                        d[1][j - 1] + weight.get(0, b.charAt(j - 1)), // insertion
                        d[0][j - 1] + cost); // substitution
            }

            double[] swap = d[0];
            d[0] = d[1];
            d[1] = swap;
        }

        return d[0][b.length()];
    }

    /**
     * Berghel & Roach's extended Ukkonen's algorithm.
     * @param a a string.
     * @param b a string.
     * @return the distance.
     */
    private int br(char[] a, char[] b) {
        if (a.length > b.length) {
            char[] swap = a;
            a = b;
            b = swap;
        }

        final int m = a.length;
        final int n = b.length;

        int ZERO_K = n;

        if (n+2 > FKP.ncol())
            FKP = new IntArray2D(2*n+1, n+2);

        for (int k = -ZERO_K; k < 0; k++) {
            int p = -k - 1;
            FKP.set(k + ZERO_K, p + 1, Math.abs(k) - 1);
            FKP.set(k + ZERO_K, p, Integer.MIN_VALUE);
        }

        FKP.set(ZERO_K, 0, -1);

        for (int k = 1; k <= ZERO_K; k++) {
            int p = k - 1;
            FKP.set(k + ZERO_K, p + 1, -1);
            FKP.set(k + ZERO_K, p, Integer.MIN_VALUE);
        }

        int p = n - m - 1;

        do {
            p++;

            for (int i = (p - (n-m))/2; i >= 1; i--) {
                brf.f(a, b, FKP, ZERO_K, n-m+i, p-i);
            }

            for (int i = (n-m+p)/2; i >= 1; i--) {
                brf.f(a, b, FKP, ZERO_K, n-m-i, p-i);
            }

            brf.f(a, b, FKP, ZERO_K, n - m, p);
        } while (FKP.get((n - m) + ZERO_K, p) != m);

        return p - 1;
    }

    /**
     * Berghel & Roach's extended Ukkonen's algorithm.
     * @param a a string.
     * @param b a string.
     * @return the distance.
     */
    private int br(String a, String b) {
        if (a.length() > b.length()) {
            String swap = a;
            a = b;
            b = swap;
        }

        final int m = a.length();
        final int n = b.length();

        int ZERO_K = n;

        if (n+3 > FKP.ncol())
            FKP = new IntArray2D(2*n+1, n+3);

        for (int k = -ZERO_K; k < 0; k++) {
            int p = -k - 1;
            FKP.set(k + ZERO_K, p + 1, Math.abs(k) - 1);
            FKP.set(k + ZERO_K, p, Integer.MIN_VALUE);
        }

        FKP.set(ZERO_K, 0, -1);

        for (int k = 1; k <= ZERO_K; k++) {
            int p = k - 1;
            FKP.set(k + ZERO_K, p + 1, -1);
            FKP.set(k + ZERO_K, p, Integer.MIN_VALUE);
        }

        int p = n - m - 1;

        do {
            p++;

            for (int i = (p - (n-m))/2; i >= 1; i--) {
                brf.f(a, b, FKP, ZERO_K, n-m+i, p-i);
            }

            for (int i = (n-m+p)/2; i >= 1; i--) {
                brf.f(a, b, FKP, ZERO_K, n-m-i, p-i);
            }

            brf.f(a, b, FKP, ZERO_K, n - m, p);
        } while (FKP.get((n - m) + ZERO_K, p) != m);

        return p - 1;
    }

    private interface BRF {
        /**
         * Calculate FKP arrays in BR's algorithm.
         */
        void f(char[] x, char[] y, IntArray2D FKP, int ZERO_K, int k, int p);
        /**
         * Calculate FKP arrays in BR's algorithm.
         */
        void f(String x, String y, IntArray2D FKP, int ZERO_K, int k, int p);
    }
    
    private static class LevenshteinBRF implements BRF {
        @Override
        public void f(char[] x, char[] y, IntArray2D FKP, int ZERO_K, int k, int p) {
            int t = MathEx.max(FKP.get(k + ZERO_K, p) + 1, FKP.get(k - 1 + ZERO_K, p), FKP.get(k + 1 + ZERO_K, p) + 1);
            int mnk = Math.min(x.length, y.length - k);

            while (t < mnk && x[t] == y[t + k]) {
                t++;
            }

            FKP.set(k + ZERO_K, p + 1, t);
        }

        @Override
        public void f(String x, String y, IntArray2D FKP, int ZERO_K, int k, int p) {
            int t = MathEx.max(FKP.get(k + ZERO_K, p) + 1, FKP.get(k - 1 + ZERO_K, p), FKP.get(k + 1 + ZERO_K, p) + 1);
            int mnk = Math.min(x.length(), y.length() - k);

            while (t < mnk && x.charAt(t) == y.charAt(t + k)) {
                t++;
            }

            FKP.set(k + ZERO_K, p + 1, t);
        }
    }

    /**
     * Calculate FKP arrays in BR's algorithm with support of transposition operation.
     */
    private static class DamerauBRF implements BRF {
        @Override
        public void f(char[] x, char[] y, IntArray2D FKP, int ZERO_K, int k, int p) {
            int t = FKP.get(k + ZERO_K, p) + 1;
            int mnk = Math.min(x.length, y.length - k);

            if (t >= 1 && k + t >= 1 && t < mnk) {
                if (x[t - 1] == y[k + t] && x[t] == y[k + t - 1]) {
                    t++;
                }
            }

            t = MathEx.max(FKP.get(k - 1 + ZERO_K, p), FKP.get(k + 1 + ZERO_K, p) + 1, t);

            while (t < mnk && x[t] == y[t + k]) {
                t++;
            }

            FKP.set(k + ZERO_K, p + 1, t);
        }

        @Override
        public void f(String x, String y, IntArray2D FKP, int ZERO_K, int k, int p) {
            int t = FKP.get(k + ZERO_K, p) + 1;
            int mnk = Math.min(x.length(), y.length() - k);

            if (t >= 1 && k + t >= 1 && t < mnk) {
                if (x.charAt(t - 1) == y.charAt(k + t) && x.charAt(t) == y.charAt(k + t - 1)) {
                    t++;
                }
            }

            t = MathEx.max(FKP.get(k - 1 + ZERO_K, p), FKP.get(k + 1 + ZERO_K, p) + 1, t);

            while (t < mnk && x.charAt(t) == y.charAt(t + k)) {
                t++;
            }

            FKP.set(k + ZERO_K, p + 1, t);
        }
    }

    /**
     * Levenshtein distance between two strings allows insertion, deletion,
     * or substitution of characters. O(mn) time and O(n) space.
     * Multi-thread safe.
     * @param a a string.
     * @param b a string.
     * @return the distance.
     */
    public static int levenshtein(String a, String b) {
        // switch parameters to use the shorter one as b to save space.
        if (a.length() < b.length()) {
            String swap = a;
            a = b;
            b = swap;
        }

        int[][] d = new int[2][b.length() + 1];

        for (int j = 0; j <= b.length(); j++) {
            d[0][j] = j;
        }

        for (int i = 1; i <= a.length(); i++) {
            d[1][0] = i;

            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                d[1][j] = MathEx.min(
                        d[0][j] + 1, // deletion
                        d[1][j - 1] + 1, // insertion
                        d[0][j - 1] + cost); // substitution
            }
            int[] swap = d[0];
            d[0] = d[1];
            d[1] = swap;
        }

        return d[0][b.length()];
    }

    /**
     * Levenshtein distance between two strings allows insertion, deletion,
     * or substitution of characters. O(mn) time and O(n) space.
     * Multi-thread safe.
     * @param a a string.
     * @param b a string.
     * @return the distance.
     */
    public static int levenshtein(char[] a, char[] b) {
        // switch parameters to use the shorter one as b to save space.
        if (a.length < b.length) {
            char[] swap = a;
            a = b;
            b = swap;
        }

        int[][] d = new int[2][b.length + 1];

        for (int j = 0; j <= b.length; j++) {
            d[0][j] = j;
        }

        for (int i = 1; i <= a.length; i++) {
            d[1][0] = i;

            for (int j = 1; j <= b.length; j++) {
                int cost = a[i - 1] == b[j - 1] ? 0 : 1;
                d[1][j] = MathEx.min(
                        d[0][j] + 1, // deletion
                        d[1][j - 1] + 1, // insertion
                        d[0][j - 1] + cost); // substitution
            }
            int[] swap = d[0];
            d[0] = d[1];
            d[1] = swap;
        }

        return d[0][b.length];
    }

    /**
     * Damerau-Levenshtein distance between two strings allows insertion,
     * deletion, substitution, or transposition of characters.
     * O(mn) time and O(n) space. Multi-thread safe.
     * @param a a string.
     * @param b a string.
     * @return the distance.
     */
    public static int damerau(String a, String b) {
        // switch parameters to use the shorter one as b to save space.
        if (a.length() < b.length()) {
            String swap = a;
            a = b;
            b = swap;
        }

        int[][] d = new int[3][b.length() + 1];

        for (int j = 0; j <= b.length(); j++) {
            d[1][j] = j;
        }

        for (int i = 1; i <= a.length(); i++) {
            d[2][0] = i;

            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i-1) == b.charAt(j-1) ? 0 : 1;
                d[2][j] = MathEx.min(
                        d[1][j] + 1,       // deletion
                        d[2][j-1] + 1,       // insertion
                        d[1][j-1] + cost); // substitution

                if (i > 1 && j > 1) {
                    if (a.charAt(i-1) == b.charAt(j-2) && a.charAt(i-2) == b.charAt(j-1))
                        d[2][j] = Math.min(d[2][j], d[0][j-2] + cost);   // damerau
                }
            }

            int[] swap = d[0];
            d[0] = d[1];
            d[1] = d[2];
            d[2] = swap;
        }

        return d[1][b.length()];
    }

    /**
     * Damerau-Levenshtein distance between two strings allows insertion,
     * deletion, substitution, or transposition of characters.
     * O(mn) time and O(n) space. Multi-thread safe.
     * @param a a string.
     * @param b a string.
     * @return the distance.
     */
    public static int damerau(char[] a, char[] b) {
        // switch parameters to use the shorter one as b to save space.
        if (a.length < b.length) {
            char[] swap = a;
            a = b;
            b = swap;
        }

        int[][] d = new int[3][b.length + 1];

        for (int j = 0; j <= b.length; j++) {
            d[1][j] = j;
        }

        for (int i = 1; i <= a.length; i++) {
            d[2][0] = i;

            for (int j = 1; j <= b.length; j++) {
                int cost = a[i-1] == b[j-1] ? 0 : 1;
                d[2][j] = MathEx.min(
                        d[1][j] + 1,       // deletion
                        d[2][j-1] + 1,       // insertion
                        d[1][j-1] + cost); // substitution

                if (i > 1 && j > 1) {
                    if (a[i-1] == b[j-2] && a[i-2] == b[j-1])
                        d[2][j] = Math.min(d[2][j], d[0][j-2] + cost);   // damerau
                }
            }

            int[] swap = d[0];
            d[0] = d[1];
            d[1] = d[2];
            d[2] = swap;
        }

        return d[1][b.length];
    }
}

