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

import java.awt.Color;
import smile.math.Math;
import smile.sort.QuickSort;

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
public class Histogram3D extends Plot {

    /**
     * The frequencies/probabilities of bins.
     */
    private double[][] freq;
    /**
     * The location of bars.
     */
    private double[][] topNW;
    private double[][] topNE;
    private double[][] topSW;
    private double[][] topSE;
    private double[][] bottomNW;
    private double[][] bottomNE;
    private double[][] bottomSW;
    private double[][] bottomSE;
    /**
     * Z-values in camera coordinates.
     */
    private double[] zTopNW;
    private double[] zTopNE;
    private double[] zTopSW;
    private double[] zTopSE;
    private double[] zBottomNW;
    private double[] zBottomNE;
    private double[] zBottomSW;
    private double[] zBottomSE;
    /**
     * Average z-values of each surface of bars.
     */
    private double[] z;
    /**
     * Index of surfaces in descending order of z-values.
     */
    private int[] order;
    /**
     * The maximum of the frequency.
     */
    private double max;
    /**
     * The window width of values for each color.
     */
    private double width = 1.0;
    /**
     * The color palette to represent values.
     */
    private Color[] palette = null;

    /**
     * Constructor.
     * @param data a sample set.
     */
    public Histogram3D(double[][] data) {
        this(data, true);
    }

    /**
     * Constructor.
     * @param data a sample set.
     * @param palette the color palette.
     */
    public Histogram3D(double[][] data, Color[] palette) {
        this(data, true, palette);
    }

    /**
     * Constructor.
     * @param data a sample set.
     * @param prob if true, the z-axis will be in the probability scale.
     * Otherwise, z-axis will be in the frequency scale.
     */
    public Histogram3D(double[][] data, boolean prob) {
        this(data, 10);
    }

    /**
     * Constructor.
     * @param data a sample set.
     * @param prob if true, the z-axis will be in the probability scale.
     * Otherwise, z-axis will be in the frequency scale.
     * @param palette the color palette.
     */
    public Histogram3D(double[][] data, boolean prob, Color[] palette) {
        this(data, 10, palette);
    }

    /**
     * Constructor.
     * @param data a sample set.
     * @param nbins the number of bins.
     */
    public Histogram3D(double[][] data, int nbins) {
        this(data, nbins, nbins, true);
    }

    /**
     * Constructor.
     * @param data a sample set.
     * @param nbins the number of bins.
     * @param palette the color palette.
     */
    public Histogram3D(double[][] data, int nbins, Color[] palette) {
        this(data, nbins, nbins, true, palette);
    }

    /**
     * Constructor.
     * @param data a sample set.
     * @param nbins the number of bins.
     * @param prob if true, the z-axis will be in the probability scale.
     * Otherwise, z-axis will be in the frequency scale.
     */
    public Histogram3D(double[][] data, int nbins, boolean prob) {
        this(data, nbins, nbins, prob);
    }

    /**
     * Constructor.
     * @param data a sample set.
     * @param nbins the number of bins.
     * @param prob if true, the z-axis will be in the probability scale.
     * Otherwise, z-axis will be in the frequency scale.
     * @param palette the color palette.
     */
    public Histogram3D(double[][] data, int nbins, boolean prob, Color[] palette) {
        this(data, nbins, nbins, prob, palette);
    }

    /**
     * Constructor.
     * @param data a sample set.
     * @param xbins the number of bins on x-axis.
     * @param ybins the number of bins on y-axis.
     */
    public Histogram3D(double[][] data, int xbins, int ybins) {
        this(data, xbins, ybins, true);
    }

    /**
     * Constructor.
     * @param data a sample set.
     * @param xbins the number of bins on x-axis.
     * @param ybins the number of bins on y-axis.
     * @param palette the color palette.
     */
    public Histogram3D(double[][] data, int xbins, int ybins, Color[] palette) {
        this(data, xbins, ybins, true, palette);
    }

    /**
     * Constructor.
     * @param data a sample set.
     * @param xbins the number of bins on x-axis.
     * @param ybins the number of bins on y-axis.
     * @param prob if true, the z-axis will be in the probability scale.
     * Otherwise, z-axis will be in the frequency scale.
     */
    public Histogram3D(double[][] data, int xbins, int ybins, boolean prob) {
        super(Color.LIGHT_GRAY);
        init(data, xbins, ybins, prob);
    }

    /**
     * Constructor.
     * @param data a sample set.
     * @param xbins the number of bins on x-axis.
     * @param ybins the number of bins on y-axis.
     * @param prob if true, the z-axis will be in the probability scale.
     * Otherwise, z-axis will be in the frequency scale.
     * @param palette the color palette.
     */
    public Histogram3D(double[][] data, int xbins, int ybins, boolean prob, Color[] palette) {
        super(Color.LIGHT_GRAY);
        this.palette = palette;
        init(data, xbins, ybins, prob);
    }

    /**
     * Generate the frequency table.
     */
    private void init(double[][] data, int xbins, int ybins, boolean prob) {
        // Generate the histogram.
        if (data.length == 0) {
            throw new IllegalArgumentException("array is empty.");
        }

        if (data[0].length != 2) {
            throw new IllegalArgumentException("dimension is not 2.");
        }

        double xmin = data[0][0];
        double xmax = data[0][0];
        double ymin = data[0][1];
        double ymax = data[0][1];
        for (int i = 1; i < data.length; i++) {
            if (xmin > data[i][0]) {
                xmin = data[i][0];
            }
            if (xmax < data[i][0]) {
                xmax = data[i][0];
            }

            if (ymin > data[i][1]) {
                ymin = data[i][1];
            }
            if (ymax < data[i][1]) {
                ymax = data[i][1];
            }
        }

        double xspan = xmax - xmin;
        double xwidth = xspan / xbins;
        double yspan = ymax - ymin;
        double ywidth = yspan / ybins;

        freq = new double[xbins * ybins][3];
        freq[0][0] = xmin + xwidth / 2;
        freq[0][1] = ymin + ywidth / 2;
        for (int i = 0; i < xbins; i++) {
            for (int j = 0; j < ybins; j++) {
                freq[j * xbins + i][0] = freq[0][0] + xwidth * i;
                freq[j * xbins + i][1] = freq[0][1] + ywidth * j;
            }
        }

        for (int k = 0; k < data.length; k++) {
            int i = (int) ((data[k][0] - xmin) / xwidth);
            if (i >= xbins) {
                i = xbins - 1;
            }

            int j = (int) ((data[k][1] - ymin) / ywidth);
            if (j >= ybins) {
                j = ybins - 1;
            }

            freq[j * xbins + i][2]++;
        }

        if (prob) {
            for (int i = 0; i < freq.length; i++) {
                freq[i][2] /= data.length;
            }
        }

        max = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < freq.length; i++) {
            if (freq[i][2] > max) {
                max = freq[i][2];
            }
        }
        
        if (palette != null) {
            width = max / palette.length;
        }

        // calculate cube coordinates.
        topNW = new double[freq.length][3];
        topNE = new double[freq.length][3];
        topSW = new double[freq.length][3];
        topSE = new double[freq.length][3];
        bottomNW = new double[freq.length][3];
        bottomNE = new double[freq.length][3];
        bottomSW = new double[freq.length][3];
        bottomSE = new double[freq.length][3];
        for (int i = 0; i < freq.length; i++) {
            topNW[i][0] = freq[i][0] - xwidth / 2;
            topNW[i][1] = freq[i][1] + ywidth / 2;
            topNW[i][2] = freq[i][2];

            topNE[i][0] = freq[i][0] + xwidth / 2;
            topNE[i][1] = freq[i][1] + ywidth / 2;
            topNE[i][2] = freq[i][2];

            topSW[i][0] = freq[i][0] - xwidth / 2;
            topSW[i][1] = freq[i][1] - ywidth / 2;
            topSW[i][2] = freq[i][2];

            topSE[i][0] = freq[i][0] + xwidth / 2;
            topSE[i][1] = freq[i][1] - ywidth / 2;
            topSE[i][2] = freq[i][2];

            bottomNW[i][0] = freq[i][0] - xwidth / 2;
            bottomNW[i][1] = freq[i][1] + ywidth / 2;
            bottomNW[i][2] = 0;

            bottomNE[i][0] = freq[i][0] + xwidth / 2;
            bottomNE[i][1] = freq[i][1] + ywidth / 2;
            bottomNE[i][2] = 0;

            bottomSW[i][0] = freq[i][0] - xwidth / 2;
            bottomSW[i][1] = freq[i][1] - ywidth / 2;
            bottomSW[i][2] = 0;

            bottomSE[i][0] = freq[i][0] + xwidth / 2;
            bottomSE[i][1] = freq[i][1] - ywidth / 2;
            bottomSE[i][2] = 0;
        }

        z = new double[6 * freq.length];
        order = new int[6 * freq.length];
        zTopNW = new double[freq.length];
        zTopNE = new double[freq.length];
        zTopSW = new double[freq.length];
        zTopSE = new double[freq.length];
        zBottomNW = new double[freq.length];
        zBottomNE = new double[freq.length];
        zBottomSW = new double[freq.length];
        zBottomSE = new double[freq.length];
    }

    @Override
    public void paint(Graphics g) {
        Color c = g.getColor();
        Projection3D p3d = (Projection3D) g.projection;

        /**
         * Calculates z-axis values in camera coordinates.
         */
        for (int i = 0; i < freq.length; i++) {
            zTopNW[i] = p3d.z(topNW[i]);
            zTopNE[i] = p3d.z(topNE[i]);
            zTopSW[i] = p3d.z(topSW[i]);
            zTopSE[i] = p3d.z(topSE[i]);
            zBottomNW[i] = p3d.z(bottomNW[i]);
            zBottomNE[i] = p3d.z(bottomNE[i]);
            zBottomSW[i] = p3d.z(bottomSW[i]);
            zBottomSE[i] = p3d.z(bottomSE[i]);
        }

        /**
         * Calculate (average) z-value for each surface.
         * Note that this is actually just sum, which is sufficient
         * for us to sort them.
         */
        for (int i = 0, k = 0; i < freq.length; i++, k += 6) {
            z[k] = (zTopNW[i] + zTopNE[i] + zTopSE[i] + zTopSW[i]);
            z[k + 1] = (zTopNW[i] + zTopNE[i] + zBottomNE[i] + zBottomNW[i]);
            z[k + 2] = (zTopSW[i] + zTopSE[i] + zBottomSE[i] + zBottomSW[i]);
            z[k + 3] = (zTopNE[i] + zTopSE[i] + zBottomSE[i] + zBottomNE[i]);
            z[k + 4] = (zTopNW[i] + zTopSW[i] + zBottomSW[i] + zBottomNW[i]);
            z[k + 5] = (zBottomNW[i] + zBottomNE[i] + zBottomSE[i] + zBottomSW[i]);
        }

        /**
         * Sorts surfaces by z-values and paint them from furthest to
         * nearest, i.e. painter's algorithm. Although the painter's
         * algorithm is computationally and conceptually much easier than
         * most alternatives, it does suffer from several flaws. The most
         * obvious example of where the painter's algorithm falls short
         * is with intersecting surfaces.
         */
        for (int i = 0; i < order.length; i++) {
            order[i] = i;
        }
        QuickSort.sort(z, order);

        for (int k : order) {
            int i = k / 6;
            if (topNW[i][2] != bottomNW[i][2]) {
                if (palette == null) {
                    g.setColor(getColor());
                } else {
                    int p = (int) (freq[i][2] / width);
                    if (p == palette.length) {
                        p = palette.length - 1;
                    }
                    
                    g.setColor(palette[p]);
                }

                int j = k % 6;
                switch (j) {
                    case 0:
                        g.fillPolygon(topNW[i], topNE[i], topSE[i], topSW[i]);
                        g.setColor(Color.BLACK);
                        g.drawLine(topNW[i], topNE[i]);
                        g.drawLine(topNE[i], topSE[i]);
                        g.drawLine(topSE[i], topSW[i]);
                        g.drawLine(topSW[i], topNW[i]);
                        break;
                    case 1:
                        g.fillPolygon(topNW[i], topNE[i], bottomNE[i], bottomNW[i]);
                        g.setColor(Color.BLACK);
                        g.drawLine(topNW[i], topNE[i]);
                        g.drawLine(bottomNW[i], topNW[i]);
                        g.drawLine(bottomNE[i], topNE[i]);
                        g.drawLine(bottomNE[i], bottomNW[i]);
                        break;
                    case 2:
                        g.fillPolygon(topSW[i], topSE[i], bottomSE[i], bottomSW[i]);
                        g.setColor(Color.BLACK);
                        g.drawLine(topSW[i], topSE[i]);
                        g.drawLine(bottomSW[i], topSW[i]);
                        g.drawLine(bottomSE[i], topSE[i]);
                        g.drawLine(bottomSE[i], bottomSW[i]);
                        break;
                    case 3:
                        g.fillPolygon(topNE[i], topSE[i], bottomSE[i], bottomNE[i]);
                        g.setColor(Color.BLACK);
                        g.drawLine(topNE[i], topSE[i]);
                        g.drawLine(bottomSE[i], topSE[i]);
                        g.drawLine(bottomNE[i], topNE[i]);
                        g.drawLine(bottomSE[i], bottomNE[i]);
                        break;
                    case 4:
                        g.fillPolygon(topNW[i], topSW[i], bottomSW[i], bottomNW[i]);
                        g.setColor(Color.BLACK);
                        g.drawLine(topNW[i], topSW[i]);
                        g.drawLine(bottomNW[i], topNW[i]);
                        g.drawLine(bottomSW[i], topSW[i]);
                        g.drawLine(bottomNW[i], bottomSW[i]);
                        break;
                    case 5:
                        g.fillPolygon(bottomNW[i], bottomNE[i], bottomSE[i], bottomSW[i]);
                        g.setColor(Color.BLACK);
                        g.drawLine(bottomNW[i], bottomNE[i]);
                        g.drawLine(bottomNE[i], bottomSE[i]);
                        g.drawLine(bottomSE[i], bottomSW[i]);
                        g.drawLine(bottomSW[i], bottomNW[i]);
                        break;
                }
            }
        }
        
        g.setColor(c);
    }

    /**
     * Get the frequencies/probabilities table.
     */
    public double[][] getHistogram() {
        return freq;
    }

    /**
     * Create a plot canvas with the histogram plot.
     * @param data a sample set.
     */
    public static PlotCanvas plot(double[][] data) {
        return plot(data);
    }

    /**
     * Create a plot canvas with the histogram plot.
     * @param data a sample set.
     * @param palette the color palette.
     */
    public static PlotCanvas plot(double[][] data, Color[] palette) {
        Histogram3D histogram = new Histogram3D(data, palette);

        double[] min = Math.colMin(data);
        double[] max = Math.colMax(data);
        double[] lowerBound = {min[0], min[1], 0};
        double[] upperBound = {max[0], max[1], 0};
        double[][] freq = histogram.getHistogram();
        for (int i = 0; i < freq.length; i++) {
            if (freq[i][2] > upperBound[2]) {
                upperBound[2] = freq[i][2];
            }
        }

        PlotCanvas canvas = new PlotCanvas(lowerBound, upperBound);
        canvas.add(histogram);

        return canvas;
    }

    /**
     * Create a plot canvas with the histogram plot of given data.
     * @param data a sample set.
     * @param k the number of bins.
     */
    public static PlotCanvas plot(double[][] data, int k) {
        return plot(data, k, null);
    }

    /**
     * Create a plot canvas with the histogram plot of given data.
     * @param data a sample set.
     * @param k the number of bins.
     * @param palette the color palette.
     */
    public static PlotCanvas plot(double[][] data, int k, Color[] palette) {
        return plot(data, k, false, palette);
    }
    
    /**
     * Create a plot canvas with the histogram plot of given data.
     * @param data a sample set.
     * @param k the number of bins.
     * @param prob if true, the z-axis will be in the probability scale.
     * @param palette the color palette.
     */
    public static PlotCanvas plot(double[][] data, int k, boolean prob, Color[] palette) {
        return plot(data, k, k, prob, palette);
    }
    
    /**
     * Create a plot canvas with the histogram plot of given data.
     * @param data a sample set.
     * @param xbins the number of bins on x-axis.
     * @param ybins the number of bins on y-axis.
     * @param prob if true, the z-axis will be in the probability scale.
     * @param palette the color palette.
     */
    public static PlotCanvas plot(double[][] data, int xbins, int ybins, boolean prob, Color[] palette) {
        Histogram3D histogram = new Histogram3D(data, xbins, ybins, prob, palette);

        double[] min = Math.colMin(data);
        double[] max = Math.colMax(data);
        double[] lowerBound = {min[0], min[1], 0};
        double[] upperBound = {max[0], max[1], 0};
        double[][] freq = histogram.getHistogram();
        for (int i = 0; i < freq.length; i++) {
            if (freq[i][2] > upperBound[2]) {
                upperBound[2] = freq[i][2];
            }
        }

        PlotCanvas canvas = new PlotCanvas(lowerBound, upperBound);
        canvas.add(histogram);

        return canvas;
    }

    /**
     * Create a plot canvas with the histogram plot of given data.
     * @param data a sample set.
     * @param xbins the number of bins on x-axis.
     * @param ybins the number of bins on y-axis.
     */
    public static PlotCanvas plot(double[][] data, int xbins, int ybins) {
        return plot(data, xbins, ybins, null);
    }

    /**
     * Create a plot canvas with the histogram plot of given data.
     * @param data a sample set.
     * @param xbins the number of bins on x-axis.
     * @param ybins the number of bins on y-axis.
     * @param palette the color palette.
     */
    public static PlotCanvas plot(double[][] data, int xbins, int ybins, Color[] palette) {
        Histogram3D histogram = new Histogram3D(data, xbins, ybins, palette);

        double[] min = Math.colMin(data);
        double[] max = Math.colMax(data);
        double[] lowerBound = {min[0], min[1], 0};
        double[] upperBound = {max[0], max[1], 0};
        double[][] freq = histogram.getHistogram();
        for (int i = 0; i < freq.length; i++) {
            if (freq[i][2] > upperBound[2]) {
                upperBound[2] = freq[i][2];
            }
        }

        PlotCanvas canvas = new PlotCanvas(lowerBound, upperBound);
        canvas.add(histogram);

        return canvas;
    }
}