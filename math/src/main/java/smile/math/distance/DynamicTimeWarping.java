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
 * @author Haifeng Li
 */
public class DynamicTimeWarping<T> implements Distance<T[]>, Serializable {
    private static final long serialVersionUID = 1L;

    private Distance<T> distance;
    private double width = 1;

    /**
     * Constructor. Dynamic time warping without path constraints.
     */
    public DynamicTimeWarping(Distance<T> distance) {
        this.distance = distance;
    }

    /**
     * Dynamic time warping with Sakoe-Chiba band, which primarily to prevent
     * unreasonable warping and also improve computational cost.
     * @param radius the window width of Sakoe-Chiba band in terms of percentage of sequence length.
     */
    public DynamicTimeWarping(Distance<T> distance, double radius) {
        if (radius < 0 || radius > 1)
            throw new IllegalArgumentException("radius = " + radius);

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
     * @param radius the window width of Sakoe-Chiba band.
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
     * @param radius the window width of Sakoe-Chiba band.
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
     * @param radius the window width of Sakoe-Chiba band.
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