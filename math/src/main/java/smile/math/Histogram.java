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

package smile.math;

import java.util.Arrays;

/**
 * Histogram utilities. A histogram is a graphical display of tabulated
 * frequencies, shown as bars. It shows what proportion of cases fall into
 * each of several categories: it is a form of data binning. The categories
 * are usually specified as non-overlapping intervals of some variable.
 * <p>
 * There is no "best" number of bins, and different bin sizes can reveal
 * different features of the data. Depending on the actual data distribution
 * and the goals of the analysis, different bin widths may be appropriate,
 * so experimentation is usually needed to determine an appropriate width.
 * <p>
 * Note that this class provides only tools to choose the bin width or the
 * number of bins and frequency counting. It does NOT providing plotting
 * services.
 * 
 * @author Haifeng Li
 */
public interface Histogram {
    /**
     * Generate the histogram of given data. The number of bins k is decided by
     * square-root choice.
     * @param data the data points.
     * @return a 3-by-k bins array of which first row is the lower bound of bins,
     * second row is the upper bound of bins, and the third row is the frequence
     * count.
     */
    static double[][] of(int[] data) {
        return of(data, bins(data.length));
    }
    
    /**
     * Generate the histogram of given data. The number of bins k is decided by
     * square-root choice.
     * @param data the data points.
     * @return a 3-by-k bins array of which first row is the lower bound of bins,
     * second row is the upper bound of bins, and the third row is the frequence
     * count.
     */
    static double[][] of(float[] data) {
        return of(data, bins(data.length));
    }
    
    /**
     * Generate the histogram of given data. The number of bins k is decided by
     * square-root choice.
     * @param data the data points.
     * @return a 3-by-k bins array of which first row is the lower bound of bins,
     * second row is the upper bound of bins, and the third row is the frequence
     * count.
     */
    static double[][] of(double[] data) {
        return of(data, bins(data.length));
    }
    
    /**
     * Generate the histogram of k bins.
     * @param data the data points.
     * @param k the number of bins.
     * @return a 3-by-k bins array of which first row is the lower bound of bins,
     * second row is the upper bound of bins, and the third row is the frequence
     * count.
     */
    static double[][] of(int[] data, int k) {
        if (k <= 1) {
            throw new IllegalArgumentException("Invalid number of bins: " + k);
        }
        
        int min = MathEx.min(data);
        int max = MathEx.max(data);
        int span = max - min + 1;
        
        int width = 1;
        int residual = 1;
        while (residual > 0) {
            width = span / k;
            if (width == 0) {
                width = 1;
            }

            residual = span - k * width;
            if (residual > 0) {
                k += 1;
            }
        }
        
        double center = width / 2.0;
        
        double[] breaks = new double[k + 1];
        breaks[0] = min - center;
        for (int i = 1; i <= k; i++) {
            breaks[i] = breaks[i - 1] + width;
        }
        
        return of(data, breaks);
    }

    /**
     * Generate the histogram of n bins.
     * @param data the data points.
     * @param breaks an array of size k+1 giving the breakpoints between
     * histogram cells. Must be in ascending order.
     * @return a 3-by-n bins array of which first row is the lower bound of bins,
     * second row is the upper bound of bins, and the third row is the frequence
     * count.
     */
    static double[][] of(int[] data, double[] breaks) {
        int k = breaks.length - 1;
        if (k <= 1) {
            throw new IllegalArgumentException("Invalid number of bins: " + k);
        }
        
        double[][] freq = new double[3][k];
        for (int i = 0; i < k; i++) {
            freq[0][i] = breaks[i];
            freq[1][i] = breaks[i + 1];
            freq[2][i] = 0;
        }

        for (int d : data) {
            int j = Arrays.binarySearch(breaks, d);
            
            if (j >= k) {
                j = k - 1;
            }

            if (j < -1 && j >= -breaks.length) {
                j = -j - 2;
            }
            
            if (j >= 0) {
                freq[2][j]++;
            }
        }

        return freq;
    }

    /**
     * Generate the histogram of n bins.
     * @param data the data points.
     * @param k the number of bins.
     * @return a 3-by-k bins array of which first row is the lower bound of bins,
     * second row is the upper bound of bins, and the third row is the frequence
     * count.
     */
    static double[][] of(float[] data, int k) {
        if (k <= 1) {
            throw new IllegalArgumentException("Invalid number of bins: " + k);
        }
        
        float min = MathEx.min(data);
        float max = MathEx.max(data);
        float span = max - min;
        if (span == 0) {
            span = k;
        }
        float width = span / k;

        float[] breaks = new float[k + 1];
        breaks[0] = min;
        for (int i = 1; i < k; i++) {
            breaks[i] = breaks[i - 1] + width;
        }
        breaks[k] = max;

        return of(data, breaks);
    }

    /**
     * Generate the histogram of n bins.
     * @param data the data points.
     * @param breaks an array of size k+1 giving the breakpoints between
     * histogram cells. Must be in ascending order.
     * @return a 3-by-k bins array of which first row is the lower bound of bins,
     * second row is the upper bound of bins, and the third row is the frequence
     * count.
     */
    static double[][] of(float[] data, float[] breaks) {
        int k = breaks.length - 1;
        if (k <= 1) {
            throw new IllegalArgumentException("Invalid number of bins: " + k);
        }
        
        double[][] freq = new double[3][k];
        for (int i = 0; i < k; i++) {
            freq[0][i] = breaks[i];
            freq[1][i] = breaks[i + 1];
            freq[2][i] = 0.0f;
        }

        for (float d : data) {
            int j = Arrays.binarySearch(breaks, d);
            
            if (j >= k) {
                j = k - 1;
            }

            if (j < -1 && j >= -breaks.length) {
                j = -j - 2;
            }
            
            if (j >= 0) {
                freq[2][j]++;
            }
        }

        return freq;
    }

    /**
     * Generate the histogram of n bins.
     * @param data the data points.
     * @param k the number of bins.
     * @return a 3-by-k array of which first row is the lower bound of bins,
     * second row is the upper bound of bins, and the third row is the frequence
     * count.
     */
    static double[][] of(double[] data, int k) {
        double min = MathEx.min(data);
        double max = MathEx.max(data);
        double span = max - min;
        if (span == 0) {
            span = k;
        }
        double width = span / k;

        double[] breaks = new double[k + 1];
        breaks[0] = min;
        for (int i = 1; i < k; i++) {
            breaks[i] = breaks[i - 1] + width;
        }
        breaks[k] = max;

        return of(data, breaks);
    }

    /**
     * Generate the histogram of n bins.
     * @param data the data points.
     * @param breaks an array of size k+1 giving the breakpoints between
     * histogram cells. Must be in ascending order.
     * @return a 3-by-k bins array of which first row is the lower bound of bins,
     * second row is the upper bound of bins, and the third row is the frequence
     * count.
     */
    static double[][] of(double[] data, double[] breaks) {
        int k = breaks.length - 1;
        if (k <= 1) {
            throw new IllegalArgumentException("Invalid number of bins: " + k);
        }
        
        double[][] freq = new double[3][k];
        for (int i = 0; i < k; i++) {
            freq[0][i] = breaks[i];
            freq[1][i] = breaks[i + 1];
            freq[2][i] = 0.0f;
        }

        for (double d : data) {
            int j = Arrays.binarySearch(breaks, d);

            if (j >= k) {
                j = k - 1;
            }

            if (j < -1 && j >= -breaks.length) {
                j = -j - 2;
            }
            
            if (j >= 0) {
                freq[2][j]++;
            }
        }

        return freq;
    }

    /**
     * Returns the breakpoints between histogram cells for a dataset based on a
     * suggested bin width h.
     * @param x the data set.
     * @param h the bin width.
     * @return the breakpoints between histogram cells
     */
    static double[] breaks(double[] x, double h) {
        return breaks(MathEx.min(x), MathEx.max(x), h);
    }
    
    /**
     * Returns the breakpoints between histogram cells for a given range based
     * on a suggested bin width h.
     * @param min the lower bound of bins.
     * @param max the upper bound of bins.
     * @param h the bin width.
     * @return the breakpoints between histogram cells
     */
    static double[] breaks(double min, double max, double h) {
        if (h <= 0.0) {
            throw new IllegalArgumentException("Invalid bin width: " + h);
        }

        if (min > max) {
            throw new IllegalArgumentException("Invalid lower and upper bounds: " + min + " > " + max);
        }
        
        int k = (int) Math.ceil((max-min) / h);
        double[] breaks = new double[k + 1];
        
        breaks[0] = min - (h * k - (max - min)) / 2;
        breaks[k] = max + (h * k - (max - min)) / 2;
        for (int i = 1; i < k; i++) {
            breaks[i] = breaks[i - 1] + h;
        }
        
        return breaks;
    }
    
    /**
     * Returns the breakpoints between histogram cells for a dataset.
     * @param x the data set.
     * @param k the number of bins.
     * @return the breakpoints between histogram cells
     */
    static double[] breaks(double[] x, int k) {
        return breaks(MathEx.min(x), MathEx.max(x), k);
    }
    
    /**
     * Returns the breakpoints between histogram cells for a given range.
     * @param min the lower bound of bins.
     * @param max the upper bound of bins.
     * @param k the number of bins.
     * @return the breakpoints between histogram cells
     */
    static double[] breaks(double min, double max, int k) {
        if (k <= 1) {
            throw new IllegalArgumentException("Invalid number of bins: " + k);
        }

        if (min > max) {
            throw new IllegalArgumentException("Invalid lower and upper bounds: " + min + " > " + max);
        }
        
        double h = (max - min) / k;
        return breaks(min, max, h);
    }
    
    /**
     * Returns the number of bins for a data based on a suggested bin width h.
     * @param x the data set.
     * @param h the bin width.
     * @return the number of bins k = ceil((max - min) / h)
     */
    static int bins(double[] x, double h) {
        if (h <= 0.0) {
            throw new IllegalArgumentException("Invalid bin width: " + h);
        }
        
        double max = MathEx.max(x);
        double min = MathEx.min(x);
        
        return (int) Math.ceil((max-min) / h);
    }
    
    /**
     * Returns the number of bins by square-root rule, which takes the square
     * root of the number of data points in the sample (used by Excel histograms
     * and many others).
     * @param n the number of data points.
     * @return the number of bins
     */
    static int bins(int n) {
        int k = (int) Math.sqrt(n);
        if (k < 5) k = 5;
        return k;
    }
    
    /**
     * Returns the number of bins by Sturges' rule k = ceil(log2(n) + 1).
     * @param n the number of data points.
     * @return the number of bins
     */
    static int sturges(int n) {
        int k = (int) Math.ceil(MathEx.log2(n) + 1);
        if (k < 5) k = 5;
        return k;
    }
        
    /**
     * Returns the number of bins by Scott's rule h = 3.5 * &sigma; / (n<sup>1/3</sup>).
     * @param x the data set.
     * @return the number of bins
     */
    static int scott(double[] x) {
        double h = Math.ceil(3.5 * MathEx.sd(x) / Math.pow(x.length, 1.0/3));
        return bins(x, h);
    }
}
