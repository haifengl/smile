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

package smile.plot.swing;

import java.util.Optional;
import smile.math.MathEx;

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
public class Histogram extends BarPlot {
    /**
     * If true, the y-axis will be in the probability scale.
     * Otherwise, y-axis will be in the frequency scale.
     */
    private boolean prob;

    /**
     * Constructor. The number of bins will be determined by square-root rule
     * and the y-axis will be in the probability scale.
     * @param data a sample set.
     */
    public Histogram(int[] data) {
        this(data, true);
    }

    /**
     * Constructor. The number of bins will be determined by square-root rule.
     * @param data a sample set.
     * @param prob if true, the y-axis will be in the probability scale.
     * Otherwise, y-axis will be in the frequency scale.
     */
    public Histogram(int[] data, boolean prob) {
        this(data, smile.math.Histogram.bins(data.length), prob);
    }

    /**
     * Constructor. The number of bins will be determined by square-root rule
     * and the y-axis will be in the probability scale.
     * @param data a sample set.
     * @param k the number of bins.
     */
    public Histogram(int[] data, int k) {
        this(data, k, true);
    }

    /**
     * Constructor.
     * @param data a sample set.
     * @param k the number of bins.
     * @param prob if true, the y-axis will be in the probability scale.
     * Otherwise, y-axis will be in the frequency scale.
     */
    public Histogram(int[] data, int k, boolean prob) {
        super(histogram(data, k, prob));
        this.prob = prob;
    }

    /**
     * Constructor. The number of bins will be determined by square-root rule
     * and the y-axis will be in the probability scale.
     * @param data a sample set.
     * @param breaks an array of size k+1 giving the breakpoints between
     * histogram cells. Must be in ascending order.
     */
    public Histogram(int[] data, double[] breaks) {
        this(data, breaks, true);
    }

    /**
     * Constructor.
     * @param data a sample set.
     * @param breaks an array of size k+1 giving the breakpoints between
     * histogram cells. Must be in ascending order.
     * @param prob if true, the y-axis will be in the probability scale.
     * Otherwise, y-axis will be in the frequency scale.
     */
    public Histogram(int[] data, double[] breaks, boolean prob) {
        super(histogram(data, breaks, prob));
        this.prob = prob;
    }

    /**
     * Constructor. The number of bins will be determined by square-root rule
     * and the y-axis will be in the probability scale.
     * @param data a sample set.
     */
    public Histogram(double[] data) {
        this(data, true);
    }

    /**
     * Constructor. The number of bins will be determined by square-root rule.
     * @param data a sample set.
     * @param prob if true, the y-axis will be in the probability scale.
     * Otherwise, y-axis will be in the frequency scale.
     */
    public Histogram(double[] data, boolean prob) {
        this(data, smile.math.Histogram.bins(data.length), prob);
    }

    /**
     * Constructor. The y-axis will be in the probability scale.
     * @param data a sample set.
     * @param k the number of bins.
     */
    public Histogram(double[] data, int k) {
        this(data, k, true);
    }

    /**
     * Constructor.
     * @param data a sample set.
     * @param k the number of bins.
     * @param prob if true, the y-axis will be in the probability scale.
     * Otherwise, y-axis will be in the frequency scale.
     */
    public Histogram(double[] data, int k, boolean prob) {
        super(histogram(data, k, prob));
        this.prob = prob;
    }

    /**
     * Constructor. The y-axis will be in the probability scale.
     * @param data a sample set.
     * @param breaks an array of size k+1 giving the breakpoints between
     * histogram cells. Must be in ascending order.
     */
    public Histogram(double[] data, double[] breaks) {
        this(data, breaks, true);
    }

    /**
     * Constructor.
     * @param data a sample set.
     * @param breaks an array of size k+1 giving the breakpoints between
     * histogram cells. Must be in ascending order.
     * @param prob if true, the y-axis will be in the probability scale.
     * Otherwise, y-axis will be in the frequency scale.
     */
    public Histogram(double[] data, double[] breaks, boolean prob) {
        super(histogram(data, breaks, prob));
        this.prob = prob;
    }

    /**
     * Returns the bin centers and frequencies/probabilities.
     * @return a n x 2 array, where n is the number of bins. a[][0] are the
     * centers of bins and a[][1] are frequencies or probabilities.
     */
    public double[][] getHistogram() {
        return data;
    }
    
    /**
     * Returns the number of bins in the histogram.
     * @return the number of bins.
     */
    public int getNumBins() {
        return data.length;
    }
    
    @Override
    public Optional<String> getToolTip(double[] coord) {
        for (int i = 0; i < data.length; i++) {
            if (coord[0] < rightBottom[i][0] && coord[0] > leftBottom[i][0] && coord[1] < rightTop[i][1] && coord[1] > rightBottom[i][1]) {
                double lower = leftBottom[i][0];
                double upper = rightBottom[i][0];

                int precision = (int) Math.round(Math.log10(Math.abs(upper - lower)));
                if (precision > 0) {
                    precision = 0;
                } else {
                    precision = -precision + 1;
                }

                String format = String.format(" in [%%.%df, %%.%df]", precision, precision);
                String tooltip = prob ?
                        String.format("%.1f%%" + format, 100.0 * data[i][1], lower, upper) :
                        String.format("%d" + format, (int) data[i][1], lower, upper);

                return Optional.of(tooltip);
            }
        }
        
        return Optional.empty();
    }

    /**
     * Generate the histogram of k bins.
     *
     * @param k the number of bins.
     */
    private static double[][] histogram(int[] data, int k, boolean prob) {
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

        return freq;
    }

    /**
     * Generate the histogram of k bins.
     *
     * @param k the number of bins.
     */
    private static double[][] histogram(double[] data, int k, boolean prob) {
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

        return freq;
    }

    /**
     * Generate the histogram of k bins.
     *
     * @param breaks an array of size k+1 giving the breakpoints between
     * histogram cells. Must be in ascending order.
     */
    private static double[][] histogram(int[] data, double[] breaks, boolean prob) {
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

        return freq;
    }

    /**
     * Generate the histogram of k bins.
     *
     * @param breaks an array of size k+1 giving the breakpoints between
     * histogram cells. Must be in ascending order.
     */
    private static double[][] histogram(double[] data, double[] breaks, boolean prob) {
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

        return freq;
    }
}
