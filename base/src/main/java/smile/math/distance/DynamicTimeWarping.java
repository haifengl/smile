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

import java.io.Serial;

/**
 * Dynamic time warping is an algorithm for measuring similarity between two
 * sequences which may vary in time or speed. DTW has been applied to video,
 * audio, and graphics - indeed, any data which can be turned into a linear
 * representation can be analyzed with DTW. A well known application has been
 * automatic speech recognition, to cope with different speaking speeds.
 * <p>
 * In general, DTW is a method that allows a computer to find an optimal
 * match between two given sequences (e.g. time series) with certain
 * restrictions. The sequences are "warped" non-linearly in the time dimension
 * to determine a measure of their similarity independent of certain non-linear
 * variations in the time dimension. This sequence alignment method is often
 * used in the context of hidden Markov models.
 * <p>
 * One example of the restrictions imposed on the matching of the sequences
 * is on the monotonicity of the mapping in the time dimension. Continuity
 * is less important in DTW than in other pattern matching algorithms;
 * DTW is an algorithm particularly suited to matching sequences with
 * missing information, provided there are long enough segments for matching
 * to occur.
 * <p>
 * The optimization process is performed using dynamic programming, hence the name.
 * <p>
 * The extension of the problem for two-dimensional "series" like images
 * (planar warping) is NP-complete, while the problem for one-dimensional
 * signals like time series can be solved in polynomial time.
 *
 * @param <T> the input type of distance function.
 *
 * @author Haifeng Li
 */
public class DynamicTimeWarping<T> implements Distance<T[]> {
    @Serial
    private static final long serialVersionUID = 1L;

    /** The distance function. */
    private final Distance<T> distance;
    /** The window width of Sakoe-Chiba band. */
    private final double width;

    /**
     * Constructor. Dynamic time warping without path constraints.
     * @param distance the distance function.
     */
    public DynamicTimeWarping(Distance<T> distance) {
        this(distance, 1.0);
    }

    /**
     * Dynamic time warping with Sakoe-Chiba band, which primarily to prevent
     * unreasonable warping and also improve computational cost.
     * @param distance the distance function.
     * @param radius the window width of Sakoe-Chiba band in terms of percentage of sequence length.
     */
    public DynamicTimeWarping(Distance<T> distance, double radius) {
        if (radius < 0 || radius > 1) {
            throw new IllegalArgumentException("radius = " + radius);
        }

        this.distance = distance;
        this.width = radius;
    }

    @Override
    public String toString() {
        return "Dynamic Time Warping";
    }

    @Override
    public double d(T[] x1, T[] x2) {
        int n1 = x1.length;
        int n2 = x2.length;
        int radius = (int) Math.round(width * Math.max(n1, n2));

        double[][] table = new double[2][n2 + 1];

        table[0][0] = 0;

        for (int i = 1; i <= n2; i++) {
            table[0][i] = Double.POSITIVE_INFINITY;
        }

        for (int i = 1; i <= n1; i++) {
            int start = 1;
            int end = n2;
            if (radius > 0) {
                start = Math.max(1, i - radius);
                end = i + radius;
                if (end < n2)
                    table[1][end+1] = Double.POSITIVE_INFINITY;
                else
                    end = n2;
            }

            table[1][start - 1] = Double.POSITIVE_INFINITY;

            for (int j = start; j <= end; j++) {
                double cost = distance.d(x1[i-1], x2[j-1]);

                double min = table[0][j - 1];

                if (min > table[0][j]) {
                    min = table[0][j];
                }

                if (min > table[1][j - 1]) {
                    min = table[1][j - 1];
                }

                table[1][j] = cost + min;
            }

            double[] swap = table[0];
            table[0] = table[1];
            table[1] = swap;
        }

        return table[0][n2];
    }

    /**
     * Dynamic time warping without path constraints.
     * @param x1 a vector.
     * @param x2 a vector.
     * @return the distance.
     */
    public static double d(int[] x1, int[] x2) {
        int n1 = x1.length;
        int n2 = x2.length;
        double[][] table = new double[2][n2 + 1];

        table[0][0] = 0;

        for (int i = 1; i <= n2; i++) {
            table[0][i] = Double.POSITIVE_INFINITY;
        }

        for (int i = 1; i <= n1; i++) {
            table[1][0] = Double.POSITIVE_INFINITY;

            for (int j = 1; j <= n2; j++) {
                double cost = Math.abs(x1[i-1] - x2[j-1]);

                double min = table[0][j - 1];

                if (min > table[0][j]) {
                    min = table[0][j];
                }

                if (min > table[1][j - 1]) {
                    min = table[1][j - 1];
                }

                table[1][j] = cost + min;
            }

            double[] swap = table[0];
            table[0] = table[1];
            table[1] = swap;
        }

        return table[0][n2];
    }

    /**
     * Dynamic time warping with Sakoe-Chiba band, which primarily to prevent
     * unreasonable warping and also improve computational cost.
     * @param x1 a vector.
     * @param x2 a vector.
     * @param radius the window width of Sakoe-Chiba band.
     * @return the distance.
     */
    public static double d(int[] x1, int[] x2, int radius) {
        int n1 = x1.length;
        int n2 = x2.length;
        double[][] table = new double[2][n2 + 1];

        table[0][0] = 0;

        for (int i = 1; i <= n2; i++) {
            table[0][i] = Double.POSITIVE_INFINITY;
        }

        for (int i = 1; i <= n1; i++) {
            int start = Math.max(1, i - radius);
            int end = Math.min(n2, i + radius);

            table[1][start-1] = Double.POSITIVE_INFINITY;
            if (end < n2) table[1][end+1] = Double.POSITIVE_INFINITY;

            for (int j = start; j <= end; j++) {
                double cost = Math.abs(x1[i-1] - x2[j-1]);

                double min = table[0][j - 1];

                if (min > table[0][j]) {
                    min = table[0][j];
                }

                if (min > table[1][j - 1]) {
                    min = table[1][j - 1];
                }

                table[1][j] = cost + min;
            }

            double[] swap = table[0];
            table[0] = table[1];
            table[1] = swap;
        }

        return table[0][n2];
    }

    /**
     * Dynamic time warping without path constraints.
     * @param x1 a vector.
     * @param x2 a vector.
     * @return the distance.
     */
    public static double d(float[] x1, float[] x2) {
        int n1 = x1.length;
        int n2 = x2.length;
        double[][] table = new double[2][n2 + 1];

        table[0][0] = 0;

        for (int i = 1; i <= n2; i++) {
            table[0][i] = Double.POSITIVE_INFINITY;
        }

        for (int i = 1; i <= n1; i++) {
            table[1][0] = Double.POSITIVE_INFINITY;

            for (int j = 1; j <= n2; j++) {
                double cost = Math.abs(x1[i-1] - x2[j-1]);

                double min = table[0][j - 1];

                if (min > table[0][j]) {
                    min = table[0][j];
                }

                if (min > table[1][j - 1]) {
                    min = table[1][j - 1];
                }

                table[1][j] = cost + min;
            }

            double[] swap = table[0];
            table[0] = table[1];
            table[1] = swap;
        }

        return table[0][n2];
    }

    /**
     * Dynamic time warping with Sakoe-Chiba band, which primarily to prevent
     * unreasonable warping and also improve computational cost.
     * @param x1 a vector.
     * @param x2 a vector.
     * @param radius the window width of Sakoe-Chiba band.
     * @return the distance.
     */
    public static double d(float[] x1, float[] x2, int radius) {
        int n1 = x1.length;
        int n2 = x2.length;
        double[][] table = new double[2][n2 + 1];

        table[0][0] = 0;

        for (int i = 1; i <= n2; i++) {
            table[0][i] = Double.POSITIVE_INFINITY;
        }

        for (int i = 1; i <= n1; i++) {
            int start = Math.max(1, i - radius);
            int end = Math.min(n2, i + radius);

            table[1][start-1] = Double.POSITIVE_INFINITY;
            if (end < n2) table[1][end+1] = Double.POSITIVE_INFINITY;

            for (int j = start; j <= end; j++) {
                double cost = Math.abs(x1[i-1] - x2[j-1]);

                double min = table[0][j - 1];

                if (min > table[0][j]) {
                    min = table[0][j];
                }

                if (min > table[1][j - 1]) {
                    min = table[1][j - 1];
                }

                table[1][j] = cost + min;
            }

            double[] swap = table[0];
            table[0] = table[1];
            table[1] = swap;
        }

        return table[0][n2];
    }

    /**
     * Dynamic time warping without path constraints.
     * @param x1 a vector.
     * @param x2 a vector.
     * @return the distance.
     */
    public static double d(double[] x1, double[] x2) {
        int n1 = x1.length;
        int n2 = x2.length;
        double[][] table = new double[2][n2 + 1];

        table[0][0] = 0;

        for (int i = 1; i <= n2; i++) {
            table[0][i] = Double.POSITIVE_INFINITY;
        }

        for (int i = 1; i <= n1; i++) {
            table[1][0] = Double.POSITIVE_INFINITY;

            for (int j = 1; j <= n2; j++) {
                double cost = Math.abs(x1[i-1] - x2[j-1]);

                double min = table[0][j - 1];

                if (min > table[0][j]) {
                    min = table[0][j];
                }

                if (min > table[1][j - 1]) {
                    min = table[1][j - 1];
                }

                table[1][j] = cost + min;
            }

            double[] swap = table[0];
            table[0] = table[1];
            table[1] = swap;
        }

        return table[0][n2];
    }

    /**
     * Dynamic time warping with Sakoe-Chiba band, which primarily to prevent
     * unreasonable warping and also improve computational cost.
     * @param x1 a vector.
     * @param x2 a vector.
     * @param radius the window width of Sakoe-Chiba band.
     * @return the distance.
     */
    public static double d(double[] x1, double[] x2, int radius) {
        int n1 = x1.length;
        int n2 = x2.length;
        double[][] table = new double[2][n2 + 1];

        table[0][0] = 0;

        for (int i = 1; i <= n2; i++) {
            table[0][i] = Double.POSITIVE_INFINITY;
        }

        for (int i = 1; i <= n1; i++) {
            int start = Math.max(1, i - radius);
            int end = Math.min(n2, i + radius);

            table[1][start-1] = Double.POSITIVE_INFINITY;
            if (end < n2) table[1][end+1] = Double.POSITIVE_INFINITY;

            for (int j = start; j <= end; j++) {
                double cost = Math.abs(x1[i-1] - x2[j-1]);

                double min = table[0][j - 1];

                if (min > table[0][j]) {
                    min = table[0][j];
                }

                if (min > table[1][j - 1]) {
                    min = table[1][j - 1];
                }

                table[1][j] = cost + min;
            }

            double[] swap = table[0];
            table[0] = table[1];
            table[1] = swap;
        }

        return table[0][n2];
    }
}