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

package smile.plot.swing;

import java.awt.Color;
import smile.math.MathEx;
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
     * The original data.
     */
    private final double[][] data;
    /**
     * The frequencies/probabilities of bins.
     */
    private final double[][] freq;
    /**
     * The location of bars.
     */
    private final double[][] topNW;
    private final double[][] topNE;
    private final double[][] topSW;
    private final double[][] topSE;
    private final double[][] bottomNW;
    private final double[][] bottomNE;
    private final double[][] bottomSW;
    private final double[][] bottomSE;
    /**
     * Z-values in camera coordinates.
     */
    private final double[] zTopNW;
    private final double[] zTopNE;
    private final double[] zTopSW;
    private final double[] zTopSE;
    private final double[] zBottomNW;
    private final double[] zBottomNE;
    private final double[] zBottomSW;
    private final double[] zBottomSE;
    /**
     * Average z-values of each surface of bars.
     */
    private final double[] z;
    /**
     * Index of surfaces in descending order of z-values.
     */
    private final int[] order;
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
    private final Color[] palette;

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

        if (data[0].length != 2) {
            throw new IllegalArgumentException("dimension is not 2.");
        }

        this.data = data;
        this.palette = palette;

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

        for (var datum : data) {
            int i = (int) ((datum[0] - xmin) / xwidth);
            if (i >= xbins) {
                i = xbins - 1;
            }

            int j = (int) ((datum[1] - ymin) / ywidth);
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
        for (var count : freq) {
            if (count[2] > max) {
                max = count[2];
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
    public double[] getLowerBound() {
        double[] min = MathEx.colMin(data);
        double[] bound = {min[0], min[1], 0};
        return bound;
    }

    @Override
    public double[] getUpperBound() {
        double[] max = MathEx.colMax(data);
        double[] bound = {max[0], max[1], 0};
        for (var count : freq) {
            if (count[2] > bound[2]) {
                bound[2] = count[2];
            }
        }

        return bound;
    }

    @Override
    public void paint(Graphics g) {
        Projection3D p3d = (Projection3D) g.projection;

        // Calculates z-axis values in camera coordinates.
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

        /*
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

        /*
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
                    g.setColor(color);
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
    }

    /**
     * Creates a 3D histogram plot.
     * @param data a sample set.
     */
    public static Histogram3D of(double[][] data) {
        return of(data, 10, true);
    }

    /**
     * Creates a 3D histogram plot.
     * @param data a sample set.
     * @param nbins the number of bins.
     * @param palette the color palette.
     */
    public static Histogram3D of(double[][] data, int nbins, Color[] palette) {
        return new Histogram3D(data, nbins, nbins, true, palette);
    }

    /**
     * Creates a 3D histogram plot.
     * @param data a sample set.
     * @param nbins the number of bins.
     * @param prob if true, the z-axis will be in the probability scale.
     * Otherwise, z-axis will be in the frequency scale.
     */
    public static Histogram3D of(double[][] data, int nbins, boolean prob) {
        return new Histogram3D(data, nbins, nbins, prob, null);
    }

    /**
     * Creates a 3D histogram plot.
     * @param data a sample set.
     * @param nbins the number of bins.
     * @param prob if true, the z-axis will be in the probability scale.
     * Otherwise, z-axis will be in the frequency scale.
     * @param palette the color palette.
     */
    public static Histogram3D of(double[][] data, int nbins, boolean prob, Color[] palette) {
        return new Histogram3D(data, nbins, nbins, prob, palette);
    }
}
