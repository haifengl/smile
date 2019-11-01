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

package smile.math.distance;

import smile.math.MathEx;
import smile.util.IntArray2D;

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
public class EditDistance implements Metric<String> {
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
     * calculate edit distance. But the whole point of Berghel & Roach
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
     */
    public EditDistance(int[][] weight) {
        this(weight, -1);
    }

    /**
     * Constructor. Weighted Levenshtein distance with
     * Sakoe-Chiba band, which improve computational cost. Only
     * insertion, deletion, and substitution operations are supported.
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
                return String.format("Damerau-Levenshtein Distance(radius = %d, weight = %s)", r, weight.toString());
            else
                return "Damerau-Levenshtein Distance";
        } else {
            if (weight != null)
                return String.format("Levenshtein Distance(radius = %d, weight = %s)", r, weight.toString());
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
    public double d(String x, String y) {
        if (weight != null)
            return weightedEdit(x, y);
        else if (FKP == null || x.length() == 1 || y.length() == 1)
            return damerau ? damerau(x, y) : levenshtein(x, y);
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
        else if (FKP == null || x.length == 1 || y.length == 1)
            return damerau ? damerau(x, y) : levenshtein(x, y);
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
            d[0][j] = d[0][j - 1] + weight.get(0, y[j]);
        }

        for (int i = 1; i <= x.length; i++) {
            d[1][0] = d[0][0] + weight.get(x[i], 0);

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
                double cost = weight.get(x[i - 1], y[j - 1]);
                d[1][j] = MathEx.min(
                        d[0][j] + weight.get(x[i - 1], 0), // deletion
                        d[1][j - 1] + weight.get(0, y[j - 1]), // insertion
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
            d[0][j] = d[0][j - 1] + weight.get(0, y.charAt(j));
        }

        for (int i = 1; i <= x.length(); i++) {
            d[1][0] = d[0][0] + weight.get(x.charAt(i), 0);

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
                double cost = weight.get(x.charAt(i - 1), y.charAt(j - 1));
                d[1][j] = MathEx.min(
                        d[0][j] + weight.get(x.charAt(i - 1), 0), // deletion
                        d[1][j - 1] + weight.get(0, y.charAt(j - 1)), // insertion
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

        if (n+2 > FKP.ncols())
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
                brf.f(x, y, FKP, ZERO_K, n-m+i, p-i);
            }

            for (int i = (n-m+p)/2; i >= 1; i--) {
                brf.f(x, y, FKP, ZERO_K, n-m-i, p-i);
            }

            brf.f(x, y, FKP, ZERO_K, n - m, p);
        } while (FKP.get((n - m) + ZERO_K, p) != m);

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

        if (n+3 > FKP.ncols())
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
                brf.f(x, y, FKP, ZERO_K, n-m+i, p-i);
            }

            for (int i = (n-m+p)/2; i >= 1; i--) {
                brf.f(x, y, FKP, ZERO_K, n-m-i, p-i);
            }

            brf.f(x, y, FKP, ZERO_K, n - m, p);
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
                d[1][j] = MathEx.min(
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
                d[1][j] = MathEx.min(
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
                d[2][j] = MathEx.min(
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
                d[2][j] = MathEx.min(
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

