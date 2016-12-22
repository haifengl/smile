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

package smile.math.distance;

import java.io.Serializable;
import smile.math.Math;

/**
 * The Edit distance between two strings is a metric for measuring the amount
 * of difference between two sequences. The Levenshtein distance between two
 * strings is given by the minimum number of operations needed to transform one
 * string into the other, where an operation is an insertion, deletion, or
 * substitution of a single character. A generalization of the Levenshtein
 * distance (Damerau-Levenshtein distance) allows the transposition of two
 * characters as an operation.
 * <p>
 * Given two strings x and y of length m and n (suppose n &ge; m), this
 * implementation takes O(ne) time and O(mn) space by an extended Ukkonen's
 * algorithm in case of unit cost, where e is the edit distance between x and y.
 * Thus this algorithm is output sensitive. The smaller the distance, the faster
 * it runs.
 * <p>
 * For weighted cost, this implements the regular dynamic programming algorithm,
 * which takes O(mn) time and O(m) space.
 *
 * @author Haifeng Li
 */
public class EditDistance implements Metric<String>, Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * Weight matrix for weighted Levenshtein distance.
     */
    private double[][] weight;

    /**
     * Radius of Sakoe-Chiba band
     */
    private double r = -1;

    /**
     * Calculate Damerau or basic Levenshitein distance.
     */
    private boolean damerauDistance = false;

    /**
     * Cost matrix. Because Java automatically initialize arrays, it
     * is very time consuming to declare this cost matrix every time
     * before calculate edit distance. Therefore, I create this
     * cost matrix here. Note that methods using this cost matrix
     * is not multi-thread safe.
     */
    private int[][] FKP;

    /**
     * The object to calculate FKP array.
     */
    private BRF brf;

    /**
     * Constructor. Weighted Levenshtein distance without path
     * constraints. Only insertion, deletion, and substitution operations are
     * supported.
     */
    public EditDistance(double[][] weight) {
        this.weight = weight;
    }

    /**
     * Constructor. Weighted Levenshtein distance with
     * Sakoe-Chiba band, which improve computational cost. Only
     * insertion, deletion, and substitution operations are supported.
     * @param radius the window width of Sakoe-Chiba band in terms of percentage of sequence length.
     */
    public EditDistance(double[][] weight, double radius) {
        this.weight = weight;
        this.r = radius;
    }

    /**
     * Constructor. Unit cost edit distance.
     * @param maxStringLength the maximum length of strings that will be
     * feed to this algorithm.
     */
    public EditDistance(int maxStringLength) {
        this(maxStringLength, false);
    }

    /**
     * Constructor. Damerau-Levenshtein distance.
     * @param maxStringLength the maximum length of strings that will be
     * feed to this algorithm.
     * @param damerau if true, calculate Damerau-Levenshtein distance instead
     * of plain Levenshtein distance.
     */
    public EditDistance(int maxStringLength, boolean damerau) {
        FKP = new int[2*maxStringLength+1][maxStringLength+2];
        damerauDistance = damerau;
        if (damerau)
            brf = new BRF2();
        else
            brf = new BRF1();
    }

    @Override
    public String toString() {
        if (damerauDistance)
            return "Damerau-Levenshtein distance";
        else
            return "Levenshtein distance";
    }

    /**
     * Edit distance between two strings. O(mn) time and O(n) space for weighted
     * edit distance. O(ne) time and O(mn) space for unit cost edit distance.
     * For weighted edit distance, this method is multi-thread safe. However,
     * it is NOT multi-thread safe for unit cost edit distance.
     */
    @Override
    public double d(String x, String y) {
        if (weight != null)
            return weightedEdit(x, y);
        else if (x.length() == 1 || y.length() == 1)
            if (damerauDistance)
                return damerau(x, y);
            else
                return levenshtein(x, y);
        else
            return br(x, y);
    }

    /**
     * Edit distance between two strings. O(mn) time and O(n) space for weighted
     * edit distance. O(ne) time and O(mn) space for unit cost edit distance.
     * For weighted edit distance, this method is multi-thread safe. However,
     * it is NOT multi-thread safe for unit cost edit distance.
     */
    public double d(char[] x, char[] y) {
        if (weight != null)
            return weightedEdit(x, y);
        else if (x.length == 1 || y.length == 1)
            if (damerauDistance)
                return damerau(x, y);
            else
                return levenshtein(x, y);
        else
            return br(x, y);
    }

    /**
     * Weighted edit distance.
     */
    private double weightedEdit(char[] x, char[] y) {
        // switch parameters to use the shorter one as y to save space.
        if (x.length < y.length) {
            char[] swap = x;
            x = y;
            y = swap;
        }

        int radius = (int) Math.round(r * Math.max(x.length, y.length));

        double[][] d = new double[2][y.length + 1];

        d[0][0] = 0.0;
        for (int j = 1; j <= y.length; j++) {
            d[0][j] = d[0][j - 1] + weight[0][y[j]];
        }

        for (int i = 1; i <= x.length; i++) {
            d[1][0] = d[0][0] + weight[x[i]][0];

            int start = 1;
            int end = y.length;

            if (radius > 0) {
                start = i - radius;
                if (start > 1)
                    d[1][start - 1] = Double.POSITIVE_INFINITY;
                else
                    start = 1;

                end = i + radius;
                if (end < y.length)
                    d[1][end+1] = Double.POSITIVE_INFINITY;
                else
                    end = y.length;
            }

            for (int j = start; j <= end; j++) {
                double cost = weight[x[i - 1]][y[j - 1]];
                d[1][j] = Math.min(
                        d[0][j] + weight[x[i - 1]][0], // deletion
                        d[1][j - 1] + weight[0][y[j - 1]], // insertion
                        d[0][j - 1] + cost); // substitution
            }

            double[] swap = d[0];
            d[0] = d[1];
            d[1] = swap;
        }

        return d[0][y.length];
    }

    /**
     * Weighted edit distance.
     */
    private double weightedEdit(String x, String y) {
        // switch parameters to use the shorter one as y to save space.
        if (x.length() < y.length()) {
            String swap = x;
            x = y;
            y = swap;
        }

        int radius = (int) Math.round(r * Math.max(x.length(), y.length()));

        double[][] d = new double[2][y.length() + 1];

        d[0][0] = 0.0;
        for (int j = 1; j <= y.length(); j++) {
            d[0][j] = d[0][j - 1] + weight[0][y.charAt(j)];
        }

        for (int i = 1; i <= x.length(); i++) {
            d[1][0] = d[0][0] + weight[x.charAt(i)][0];

            int start = 1;
            int end = y.length();

            if (radius > 0) {
                start = i - radius;
                if (start > 1)
                    d[1][start - 1] = Double.POSITIVE_INFINITY;
                else
                    start = 1;

                end = i + radius;
                if (end < y.length())
                    d[1][end+1] = Double.POSITIVE_INFINITY;
                else
                    end = y.length();
            }

            for (int j = start; j <= end; j++) {
                double cost = weight[x.charAt(i - 1)][y.charAt(j - 1)];
                d[1][j] = Math.min(
                        d[0][j] + weight[x.charAt(i - 1)][0], // deletion
                        d[1][j - 1] + weight[0][y.charAt(j - 1)], // insertion
                        d[0][j - 1] + cost); // substitution
            }

            double[] swap = d[0];
            d[0] = d[1];
            d[1] = swap;
        }

        return d[0][y.length()];
    }

    /**
     * Berghel & Roach's extended Ukkonen's algorithm.
     */
    private int br(char[] x, char[] y) {
        if (x.length > y.length) {
            char[] swap = x;
            x = y;
            y = swap;
        }

        final int m = x.length;
        final int n = y.length;

        int ZERO_K = n;

        if (n+2 > FKP[0].length)
            FKP = new int[2*n+1][n+2];

        for (int k = -ZERO_K; k < 0; k++) {
            int p = -k - 1;
            FKP[k + ZERO_K][p + 1] = Math.abs(k) - 1;
            FKP[k + ZERO_K][p] = -Integer.MAX_VALUE;
        }

        FKP[ZERO_K][0] = -1;

        for (int k = 1; k <= ZERO_K; k++) {
            int p = k - 1;
            FKP[k + ZERO_K][p + 1] = -1;
            FKP[k + ZERO_K][p] = -Integer.MAX_VALUE;
        }

        int p = n - m - 1;

        do {
            p++;

            for (int i = (p - (n-m))/2; i >= 1; i--) {
                brf.f(x, y, FKP, ZERO_K, n-m+i, p-i);
            }

            for (int i = (n-m+p)/2; i >= 1; i--) {
                brf.f(x, y, FKP, ZERO_K, n-m-i, p-i);
            }

            brf.f(x, y, FKP, ZERO_K, n - m, p);
        } while (FKP[(n - m) + ZERO_K][p] != m);

        return p - 1;
    }

    /**
     * Berghel & Roach's extended Ukkonen's algorithm.
     */
    private int br(String x, String y) {
        if (x.length() > y.length()) {
            String swap = x;
            x = y;
            y = swap;
        }

        final int m = x.length();
        final int n = y.length();

        int ZERO_K = n;

        if (n+3 > FKP[0].length)
            FKP = new int[2*n+1][n+3];

        for (int k = -ZERO_K; k < 0; k++) {
            int p = -k - 1;
            FKP[k + ZERO_K][p + 1] = Math.abs(k) - 1;
            FKP[k + ZERO_K][p] = -Integer.MAX_VALUE;
        }

        FKP[ZERO_K][0] = -1;

        for (int k = 1; k <= ZERO_K; k++) {
            int p = k - 1;
            FKP[k + ZERO_K][p + 1] = -1;
            FKP[k + ZERO_K][p] = -Integer.MAX_VALUE;
        }

        int p = n - m - 1;

        do {
            p++;

            for (int i = (p - (n-m))/2; i >= 1; i--) {
                brf.f(x, y, FKP, ZERO_K, n-m+i, p-i);
            }

            for (int i = (n-m+p)/2; i >= 1; i--) {
                brf.f(x, y, FKP, ZERO_K, n-m-i, p-i);
            }

            brf.f(x, y, FKP, ZERO_K, n - m, p);
        } while (FKP[(n - m) + ZERO_K][p] != m);

        return p - 1;
    }

    private static interface BRF {
        /**
         * Calculate FKP arrays in BR's algorithm.
         */
        public void f(char[] x, char[] y, int[][] FKP, int ZERO_K, int k, int p);
        /**
         * Calculate FKP arrays in BR's algorithm.
         */
        public void f(String x, String y, int[][] FKP, int ZERO_K, int k, int p);
    }
    
    private static class BRF1 implements BRF {
        @Override
        public void f(char[] x, char[] y, int[][] FKP, int ZERO_K, int k, int p) {
            int t = Math.max(FKP[k + ZERO_K][p] + 1, FKP[k - 1 + ZERO_K][p], FKP[k + 1 + ZERO_K][p] + 1);

            while (t < Math.min(x.length, y.length - k) && x[t] == y[t + k]) {
                t++;
            }

            FKP[k + ZERO_K][p + 1] = t;
        }

        @Override
        public void f(String x, String y, int[][] FKP, int ZERO_K, int k, int p) {
            int t = Math.max(FKP[k + ZERO_K][p] + 1, FKP[k - 1 + ZERO_K][p], FKP[k + 1 + ZERO_K][p] + 1);

            while (t < Math.min(x.length(), y.length() - k) && x.charAt(t) == y.charAt(t + k)) {
                t++;
            }

            FKP[k + ZERO_K][p + 1] = t;
        }
    }

    /**
     * Calculate FKP arrays in BR's algorithm with support of transposition operation.
     */
    private static class BRF2 implements BRF {
        @Override
        public void f(char[] x, char[] y, int[][] FKP, int ZERO_K, int k, int p) {
            int t = FKP[k + ZERO_K][p] + 1;

            if (t > 1 && k + t > 1 && t < Math.min(x.length, y.length - k)) {
                if (x[t - 1] == y[k + t] && x[t] == y[k + t - 1]) {
                    t++;
                }
            }

            t = Math.max(FKP[k - 1 + ZERO_K][p], FKP[k + 1 + ZERO_K][p] + 1, t);

            while (t < Math.min(x.length, y.length - k) && x[t] == y[t + k]) {
                t++;
            }

            FKP[k + ZERO_K][p + 1] = t;
        }

        @Override
        public void f(String x, String y, int[][] FKP, int ZERO_K, int k, int p) {
            int t = FKP[k + ZERO_K][p] + 1;

            if (t > 1 && k + t > 1 && t < Math.min(x.length(), y.length() - k)) {
                if (x.charAt(t - 1) == y.charAt(k + t) && x.charAt(t) == y.charAt(k + t - 1)) {
                    t++;
                }
            }

            t = Math.max(FKP[k - 1 + ZERO_K][p], FKP[k + 1 + ZERO_K][p] + 1, t);

            while (t < Math.min(x.length(), y.length() - k) && x.charAt(t) == y.charAt(t + k)) {
                t++;
            }

            FKP[k + ZERO_K][p + 1] = t;
        }
    }

    /**
     * Levenshtein distance between two strings allows insertion, deletion,
     * or substitution of characters. O(mn) time and O(n) space.
     * Multi-thread safe.
     */
    public static int levenshtein(String x, String y) {
        // switch parameters to use the shorter one as y to save space.
        if (x.length() < y.length()) {
            String swap = x;
            x = y;
            y = swap;
        }

        int[][] d = new int[2][y.length() + 1];

        for (int j = 0; j <= y.length(); j++) {
            d[0][j] = j;
        }

        for (int i = 1; i <= x.length(); i++) {
            d[1][0] = i;

            for (int j = 1; j <= y.length(); j++) {
                int cost = x.charAt(i - 1) == y.charAt(j - 1) ? 0 : 1;
                d[1][j] = Math.min(
                        d[0][j] + 1, // deletion
                        d[1][j - 1] + 1, // insertion
                        d[0][j - 1] + cost); // substitution
            }
            int[] swap = d[0];
            d[0] = d[1];
            d[1] = swap;
        }

        return d[0][y.length()];
    }

    /**
     * Levenshtein distance between two strings allows insertion, deletion,
     * or substitution of characters. O(mn) time and O(n) space.
     * Multi-thread safe.
     */
    public static int levenshtein(char[] x, char[] y) {
        // switch parameters to use the shorter one as y to save space.
        if (x.length < y.length) {
            char[] swap = x;
            x = y;
            y = swap;
        }

        int[][] d = new int[2][y.length + 1];

        for (int j = 0; j <= y.length; j++) {
            d[0][j] = j;
        }

        for (int i = 1; i <= x.length; i++) {
            d[1][0] = i;

            for (int j = 1; j <= y.length; j++) {
                int cost = x[i - 1] == y[j - 1] ? 0 : 1;
                d[1][j] = Math.min(
                        d[0][j] + 1, // deletion
                        d[1][j - 1] + 1, // insertion
                        d[0][j - 1] + cost); // substitution
            }
            int[] swap = d[0];
            d[0] = d[1];
            d[1] = swap;
        }

        return d[0][y.length];
    }

    /**
     * Damerau-Levenshtein distance between two strings allows insertion,
     * deletion, substitution, or transposition of characters.
     * O(mn) time and O(n) space. Multi-thread safe.
     */
    public static int damerau(String x, String y) {
        // switch parameters to use the shorter one as y to save space.
        if (x.length() < y.length()) {
            String swap = x;
            x = y;
            y = swap;
        }

        int[][] d = new int[3][y.length() + 1];

        for (int j = 0; j <= y.length(); j++) {
            d[1][j] = j;
        }

        for (int i = 1; i <= x.length(); i++) {
            d[2][0] = i;

            for (int j = 1; j <= y.length(); j++) {
                int cost = x.charAt(i-1) == y.charAt(j-1) ? 0 : 1;
                d[2][j] = Math.min(
                        d[1][j] + 1,       // deletion
                        d[2][j-1] + 1,       // insertion
                        d[1][j-1] + cost); // substitution

                if (i > 1 && j > 1) {
                    if (x.charAt(i-1) == y.charAt(j-2) && x.charAt(i-2) == y.charAt(j-1))
                        d[2][j] = Math.min(d[2][j], d[0][j-2] + cost);   // damerau
                }
            }

            int[] swap = d[0];
            d[0] = d[1];
            d[1] = d[2];
            d[2] = swap;
        }

        return d[1][y.length()];
    }

    /**
     * Damerau-Levenshtein distance between two strings allows insertion,
     * deletion, substitution, or transposition of characters.
     * O(mn) time and O(n) space. Multi-thread safe.
     */
    public static int damerau(char[] x, char[] y) {
        // switch parameters to use the shorter one as y to save space.
        if (x.length < y.length) {
            char[] swap = x;
            x = y;
            y = swap;
        }

        int[][] d = new int[3][y.length + 1];

        for (int j = 0; j <= y.length; j++) {
            d[1][j] = j;
        }

        for (int i = 1; i <= x.length; i++) {
            d[2][0] = i;

            for (int j = 1; j <= y.length; j++) {
                int cost = x[i-1] == y[j-1] ? 0 : 1;
                d[2][j] = Math.min(
                        d[1][j] + 1,       // deletion
                        d[2][j-1] + 1,       // insertion
                        d[1][j-1] + cost); // substitution

                if (i > 1 && j > 1) {
                    if (x[i-1] == y[j-2] && x[i-2] == y[j-1])
                        d[2][j] = Math.min(d[2][j], d[0][j-2] + cost);   // damerau
                }
            }

            int[] swap = d[0];
            d[0] = d[1];
            d[1] = d[2];
            d[2] = swap;
        }

        return d[1][y.length];
    }
}

