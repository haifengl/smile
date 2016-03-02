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

package smile.plot;

import smile.math.Math;

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
    public String getToolTip(double[] coord) {
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

                if (prob) {
                    return String.format("%.1f%%" + format, 100.0 * data[i][1], lower, upper);
                } else {
                    return String.format("%d" + format, (int) data[i][1], lower, upper);
                }
            }
        }
        
        return null;        
    }
    
    /**
     * Generate the histogram of k bins.
     *
     * @param k the number of bins.
     */
    private static double[][] histogram(int[] data, int k, boolean prob) {
        double[][] hist = smile.math.Histogram.histogram(data, k);

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
        double[][] hist = smile.math.Histogram.histogram(data, k);

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
        
        double[][] hist = smile.math.Histogram.histogram(data, breaks);

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
        
        double[][] hist = smile.math.Histogram.histogram(data, breaks);

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
     * Create a plot canvas with the histogram plot.
     * @param data a sample set.
     */
    public static PlotCanvas plot(double[] data) {
        return plot((String) null, data);
    }

    /**
     * Create a plot canvas with the histogram plot.
     * @param id the id of the plot.
     * @param data a sample set.
     */
    public static PlotCanvas plot(String id, double[] data) {
        Histogram histogram = new Histogram(data);
        histogram.setID(id);

        double[] lowerBound = {Math.min(data), 0};
        double[] upperBound = {Math.max(data), 0};
        
        double[][] freq = histogram.getHistogram();
        for (int i = 0; i < freq.length; i++) {
            if (freq[i][1] > upperBound[1]) {
                upperBound[1] = freq[i][1];
            }
        }

        PlotCanvas canvas = new PlotCanvas(lowerBound, upperBound);
        canvas.getAxis(0).setGridVisible(false);
        canvas.add(histogram);

        return canvas;
    }

    /**
     * Create a plot canvas with the histogram plot of given data.
     * @param data a sample set.
     * @param k the number of bins.
     */
    public static PlotCanvas plot(double[] data, int k) {
        return plot(null, data, k);
    }

    /**
     * Create a plot canvas with the histogram plot of given data.
     * @param id the id of the plot.
     * @param data a sample set.
     * @param k the number of bins.
     */
    public static PlotCanvas plot(String id, double[] data, int k) {
        Histogram histogram = new Histogram(data, k);
        histogram.setID(id);

        double[] lowerBound = {Math.min(data), 0};
        double[] upperBound = {Math.max(data), 0};
        
        double[][] freq = histogram.getHistogram();
        for (int i = 0; i < freq.length; i++) {
            if (freq[i][1] > upperBound[1]) {
                upperBound[1] = freq[i][1];
            }
        }

        PlotCanvas canvas = new PlotCanvas(lowerBound, upperBound);
        canvas.getAxis(0).setGridVisible(false);
        canvas.add(histogram);

        return canvas;
    }

    /**
     * Create a plot canvas with the histogram plot of given data.
     * @param data a sample set.
     * @param breaks an array of size k+1 giving the breakpoints between
     * histogram cells. Must be in ascending order.
     */
    public static PlotCanvas plot(double[] data, double[] breaks) {
        return plot(null, data, breaks);
    }

    /**
     * Create a plot canvas with the histogram plot of given data.
     * @param id the id of the plot.
     * @param data a sample set.
     * @param breaks an array of size k+1 giving the breakpoints between
     * histogram cells. Must be in ascending order.
     */
    public static PlotCanvas plot(String id, double[] data, double[] breaks) {
        return plot(id, data, breaks, true);
    }
    
    /**
     * Create a plot canvas with the histogram plot of given data.
     * @param id the id of the plot.
     * @param data a sample set.
     * @param breaks an array of size k+1 giving the breakpoints between
     * histogram cells. Must be in ascending order.
     */
    public static PlotCanvas plot(String id, double[] data, double[] breaks, boolean prob) {
        Histogram histogram = new Histogram(data, breaks, prob);
        histogram.setID(id);

        double[] lowerBound = {Math.min(data), 0};
        double[] upperBound = {Math.max(data), 0};
        
        double[][] freq = histogram.getHistogram();
        for (int i = 0; i < freq.length; i++) {
            if (freq[i][1] > upperBound[1]) {
                upperBound[1] = freq[i][1];
            }
        }

        PlotCanvas canvas = new PlotCanvas(lowerBound, upperBound);
        canvas.getAxis(0).setGridVisible(false);
        canvas.add(histogram);

        return canvas;
    }
    
    /**
     * Create a plot canvas with the histogram plot.
     * @param data a sample set.
     */
    public static PlotCanvas plot(int[] data) {
        return plot((String) null, data);
    }

    /**
     * Create a plot canvas with the histogram plot.
     * @param id the id of the plot.
     * @param data a sample set.
     */
    public static PlotCanvas plot(String id, int[] data) {
        Histogram histogram = new Histogram(data);
        histogram.setID(id);

        double[] lowerBound = {Math.min(data) - 0.5, 0};
        double[] upperBound = {Math.max(data) + 0.5, 0};
        double[][] freq = histogram.getHistogram();
        for (int i = 0; i < freq.length; i++) {
            if (freq[i][1] > upperBound[1]) {
                upperBound[1] = freq[i][1];
            }
        }

        PlotCanvas canvas = new PlotCanvas(lowerBound, upperBound);
        canvas.getAxis(0).setGridVisible(false);
        canvas.add(histogram);

        return canvas;
    }

    /**
     * Create a plot canvas with the histogram plot of given data.
     * @param data a sample set.
     * @param k the number of bins.
     */
    public static PlotCanvas plot(int[] data, int k) {
        return plot(null, data, k);
    }

    /**
     * Create a plot canvas with the histogram plot of given data.
     * @param id the id of the plot.
     * @param data a sample set.
     * @param k the number of bins.
     */
    public static PlotCanvas plot(String id, int[] data, int k) {
        Histogram histogram = new Histogram(data, k);
        histogram.setID(id);

        double[] lowerBound = {Math.min(data) - 0.5, 0};
        double[] upperBound = {Math.max(data) + 0.5, 0};
        double[][] freq = histogram.getHistogram();
        for (int i = 0; i < freq.length; i++) {
            if (freq[i][1] > upperBound[1]) {
                upperBound[1] = freq[i][1];
            }
        }

        PlotCanvas canvas = new PlotCanvas(lowerBound, upperBound);
        canvas.getAxis(0).setGridVisible(false);
        canvas.add(histogram);

        return canvas;
    }

    /**
     * Create a plot canvas with the histogram plot of given data.
     * @param data a sample set.
     * @param breaks an array of size k+1 giving the breakpoints between
     * histogram cells. Must be in ascending order.
     */
    public static PlotCanvas plot(int[] data, double[] breaks) {
        return plot(null, data, breaks);
    }

    /**
     * Create a plot canvas with the histogram plot of given data.
     * @param id the id of the plot.
     * @param data a sample set.
     * @param breaks an array of size k+1 giving the breakpoints between
     * histogram cells. Must be in ascending order.
     */
    public static PlotCanvas plot(String id, int[] data, double[] breaks) {
        Histogram histogram = new Histogram(data, breaks);
        histogram.setID(id);

        double[] lowerBound = {Math.min(data) - 0.5, 0};
        double[] upperBound = {Math.max(data) + 0.5, 0};
        double[][] freq = histogram.getHistogram();
        for (int i = 0; i < freq.length; i++) {
            if (freq[i][1] > upperBound[1]) {
                upperBound[1] = freq[i][1];
            }
        }

        PlotCanvas canvas = new PlotCanvas(lowerBound, upperBound);
        canvas.getAxis(0).setGridVisible(false);
        canvas.add(histogram);

        return canvas;
    }
}