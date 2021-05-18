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

package smile.plot.swing;

import java.awt.Color;

/**
 * A histogram is a graphical display of tabulated frequencies, shown as bars.
 * It shows what proportion of cases fall into each of several categories:
 * it is a form of data binning. The categories are usually specified as
 * non-overlapping intervals of some variable. The categories (bars) must
 * be adjacent. The intervals (or bands, or bins) are generally of the same
 * size, and are most easily interpreted if they are.
 * 
 * @author Haifeng Li
 */
public class Histogram {
    /**
     * Creates a histogram plot.
     * The number of bins will be determined by square-root rule
     * and the y-axis will be in the probability scale.
     * @param data a sample set.
     */
    public static BarPlot of(int[] data) {
        return of(data, smile.math.Histogram.bins(data.length), true);
    }

    /**
     * Creates a histogram plot.
     * @param data a sample set.
     * @param k the number of bins.
     * @param prob if true, the y-axis will be in the probability scale.
     * Otherwise, y-axis will be in the frequency scale.
     */
    public static BarPlot of(int[] data, int k, boolean prob) {
        return of(data, k, prob, Color.BLUE);
    }

    /**
     * Creates a histogram plot.
     * @param data a sample set.
     * @param k the number of bins.
     * @param prob if true, the y-axis will be in the probability scale.
     * Otherwise, y-axis will be in the frequency scale.
     */
    public static BarPlot of(int[] data, int k, boolean prob, Color color) {
        double[][] hist = smile.math.Histogram.of(data, k);

        // The number of bins may be extended to cover all data.
        k = hist[0].length;
        double[][] freq = new double[k][2];
        for (int i = 0; i < k; i++) {
            freq[i][0] = (hist[0][i] + hist[1][i]) / 2.0;
            freq[i][1] = hist[2][i];
        }

        if (prob) {
            double n = data.length;
            for (int i = 0; i < k; i++) {
                freq[i][1] /= n;
            }
        }

        return new BarPlot(new Bar(freq, width(freq), color));
    }

    /**
     * Creates a histogram plot.
     * @param data a sample set.
     * @param breaks an array of size k+1 giving the breakpoints between
     * histogram cells. Must be in ascending order.
     * @param prob if true, the y-axis will be in the probability scale.
     * Otherwise, y-axis will be in the frequency scale.
     */
    public static BarPlot of(int[] data, double[] breaks, boolean prob) {
        return of(data, breaks, prob, Color.BLUE);
    }

    /**
     * Creates a histogram plot.
     * @param data a sample set.
     * @param breaks an array of size k+1 giving the breakpoints between
     * histogram cells. Must be in ascending order.
     * @param prob if true, the y-axis will be in the probability scale.
     * Otherwise, y-axis will be in the frequency scale.
     */
    public static BarPlot of(int[] data, double[] breaks, boolean prob, Color color) {
        int k = breaks.length - 1;
        if (k <= 1) {
            throw new IllegalArgumentException("Invalid number of bins: " + k);
        }

        double[][] hist = smile.math.Histogram.of(data, breaks);

        double[][] freq = new double[k][2];
        for (int i = 0; i < k; i++) {
            freq[i][0] = (hist[0][i] + hist[1][i]) / 2.0;
            freq[i][1] = hist[2][i];
        }

        if (prob) {
            double n = data.length;
            for (int i = 0; i < k; i++) {
                freq[i][1] /= n;
            }
        }

        return new BarPlot(new Bar(freq, width(freq), color));
    }

    /**
     * Creates a histogram plot.
     * The number of bins will be determined by square-root rule
     * and the y-axis will be in the probability scale.
     * @param data a sample set.
     */
    public static BarPlot of(double[] data) {
        return of(data, smile.math.Histogram.bins(data.length), true, Color.BLUE);
    }

    /**
     * Creates a histogram plot.
     * @param data a sample set.
     * @param k the number of bins.
     * @param prob if true, the y-axis will be in the probability scale.
     * Otherwise, y-axis will be in the frequency scale.
     */
    public static BarPlot of(double[] data, int k, boolean prob) {
        return of(data, k, prob, Color.BLUE);
    }

    /**
     * Creates a histogram plot.
     * @param data a sample set.
     * @param k the number of bins.
     * @param prob if true, the y-axis will be in the probability scale.
     * Otherwise, y-axis will be in the frequency scale.
     */
    public static BarPlot of(double[] data, int k, boolean prob, Color color) {
        double[][] hist = smile.math.Histogram.of(data, k);

        // The number of bins may be extended to cover all data.
        k = hist[0].length;
        double[][] freq = new double[k][2];
        for (int i = 0; i < k; i++) {
            freq[i][0] = (hist[0][i] + hist[1][i]) / 2.0;
            freq[i][1] = hist[2][i];
        }

        if (prob) {
            double n = data.length;
            for (int i = 0; i < k; i++) {
                freq[i][1] /= n;
            }
        }

        return new BarPlot(new Bar(freq, width(freq), color));
    }

    /**
     * Creates a histogram plot.
     * @param data a sample set.
     * @param breaks an array of size k+1 giving the breakpoints between
     * histogram cells. Must be in ascending order.
     * @param prob if true, the y-axis will be in the probability scale.
     * Otherwise, y-axis will be in the frequency scale.
     */
    public static BarPlot of(double[] data, double[] breaks, boolean prob) {
        return of(data, breaks, prob, Color.BLUE);
    }

    /**
     * Creates a histogram plot.
     * @param data a sample set.
     * @param breaks an array of size k+1 giving the breakpoints between
     * histogram cells. Must be in ascending order.
     * @param prob if true, the y-axis will be in the probability scale.
     * Otherwise, y-axis will be in the frequency scale.
     */
    public static BarPlot of(double[] data, double[] breaks, boolean prob, Color color) {
        int k = breaks.length - 1;
        if (k <= 1) {
            throw new IllegalArgumentException("Invalid number of bins: " + k);
        }

        double[][] hist = smile.math.Histogram.of(data, breaks);

        double[][] freq = new double[k][2];
        for (int i = 0; i < k; i++) {
            freq[i][0] = (hist[0][i] + hist[1][i]) / 2.0;
            freq[i][1] = hist[2][i];
        }

        if (prob) {
            double n = data.length;
            for (int i = 0; i < k; i++) {
                freq[i][1] /= n;
            }
        }

        return new BarPlot(new Bar(freq, width(freq), color));
    }

    /** Calculates the width of bins. */
    private static double width(double[][] freq) {
        double width = Double.MAX_VALUE;
        for (int i = 1; i < freq.length; i++) {
            double w = Math.abs(freq[i][0] - freq[i - 1][0]);
            if (width > w) width = w;
        }
        return width;
    }
}
