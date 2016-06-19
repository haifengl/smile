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
import java.util.Arrays;
import smile.math.Math;

/**
 * A heat map is a graphical representation of data where the values taken by
 * a variable in a two-dimensional map are represented as colors.
 * 
 * @author Haifeng Li
 */
public class Heatmap extends Plot {

    /**
     * The x coordinate for columns of data matrix.
     */
    private double[] x;
    /**
     * The labels for columns of data matrix.
     */
    private String[] columnLabels;
    /**
     * The labels for rows of data matrix.
     */
    private String[] rowLabels;
    /**
     * The y coordinate for rows of data matrix.
     */
    private double[] y;
    /**
     * The two-dimensional data matrix.
     */
    private double[][] z;
    /**
     * The minimum of the data.
     */
    private double min;
    /**
     * The minimum of the data.
     */
    private double max;
    /**
     * The window width of values for each color.
     */
    private double width;
    /**
     * The color palette to represent values.
     */
    private Color[] palette;

    /**
     * Constructor. Use 16-color jet color palette.
     */
    public Heatmap(double[][] z) {
        this(z, 16);
    }

    /**
     * Constructor. Use jet color palette.
     * @param z a data matrix to be shown in pseudo heat map.
     * @param k the number of colors in the palette.
     */
    public Heatmap(double[][] z, int k) {
        this(z, Palette.jet(k, 1.0f));
    }

    /**
     * Constructor.
     * @param z a data matrix to be shown in pseudo heat map.
     * @param palette the color palette.
     */
    public Heatmap(double[][] z, Color[] palette) {
        this.z = z;
        this.palette = palette;
        init();
    }

    /**
     * Constructor. Use 16-color jet color palette.
     */
    public Heatmap(String[] rowLabels, String[] columnLabels, double[][] z) {
        this(rowLabels, columnLabels, z, 16);
    }

    /**
     * Constructor. Use jet color palette.
     * @param z a data matrix to be shown in pseudo heat map.
     * @param k the number of colors in the palette.
     */
    public Heatmap(String[] rowLabels, String[] columnLabels, double[][] z, int k) {
        this(rowLabels, columnLabels, z, Palette.jet(k, 1.0f));
    }

    /**
     * Constructor.
     * @param z a data matrix to be shown in pseudo heat map.
     * @param palette the color palette.
     */
    public Heatmap(String[] rowLabels, String[] columnLabels, double[][] z, Color[] palette) {
        this.z = z;
        this.columnLabels = columnLabels;
        this.rowLabels = rowLabels;
        this.palette = palette;
        init();
    }

    /**
     * Constructor. Use 16-color jet color palette.
     * @param x x coordinate of data matrix cells. Must be in ascending order.
     * @param y y coordinate of data matrix cells. Must be in ascending order.
     * @param z a data matrix to be shown in pseudo heat map.
     */
    public Heatmap(double[] x, double[] y, double[][] z) {
        this(z, 16);
    }

    /**
     * Constructor. Use jet color palette.
     * @param x x coordinate of data matrix cells. Must be in ascending order.
     * @param y y coordinate of data matrix cells. Must be in ascending order.
     * @param z a data matrix to be shown in pseudo heat map.
     * @param k the number of colors in the palette.
     */
    public Heatmap(double[] x, double[] y, double[][] z, int k) {
        this(x, y, z, Palette.jet(k, 1.0f));
    }

    /**
     * Constructor.
     * @param x x coordinate of data matrix cells. Must be in ascending order.
     * @param y y coordinate of data matrix cells. Must be in ascending order.
     * @param z a data matrix to be shown in pseudo heat map.
     * @param palette the color palette.
     */
    public Heatmap(double[] x, double[] y, double[][] z, Color[] palette) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.palette = palette;
        init();
    }

    /**
     * Initialize the internal variables.
     */
    private void init() {
        if (x == null) {
            x = new double[z[0].length];
            for (int i = 0; i < x.length; i++) {
                x[i] = i + 0.5;
            }
        }

        if (y == null) {
            y = new double[z.length];
            for (int i = 0; i < y.length; i++) {
                y[i] = y.length - i - 0.5;
            }
        }

        // In case of outliers, we use 1% and 99% quantiles as lower and
        // upper limits instead of min and max.
        int n = z.length * z[0].length;
        double[] values = new double[n];
        int i = 0;
        for (double[] zi : z) {
            for (double zij : zi) {
                if (!Double.isNaN(zij)) {
                    values[i++] = zij;
                }
            }
        }
        
        if (i > 0) {
            Arrays.sort(values, 0, i);
            min = values[(int)Math.round(0.01 * i)];
            max = values[(int)Math.round(0.99 * (i-1))];
            width = (max - min) / palette.length;
        }
    }

    @Override
    public String getToolTip(double[] coord) {
        if (rowLabels == null || columnLabels == null) {
            return null;
        }
        
        if (coord[0] < 0.0 || coord[0] > z[0].length || coord[1] < 0.0 || coord[1] > z.length) {
            return null;
        }

        int i = (int) coord[0];
        int j = (int) (y.length - coord[1]);
        
        return String.format("%s, %s", rowLabels[j], columnLabels[i]);
    }
    
    @Override
    public void paint(Graphics g) {
        Color c = g.getColor();

        double[] start = new double[2];
        double[] end = new double[2];

        for (int i = 0; i < z.length; i++) {
            for (int j = 0; j < z[i].length; j++) {
                if (Double.isNaN(z[i][j])) {
                    g.setColor(Color.WHITE);
                } else {
                    int k = (int) ((z[i][j] - min) / width);
                    
                    if (k < 0) {
                        k = 0;
                    }
                    
                    if (k >= palette.length) {
                        k = palette.length - 1;
                    }
                    
                    g.setColor(palette[k]);
                }

                start[0] = x[j];
                if (j == 0) {
                    start[0] -= Math.abs(x[j + 1] - x[j]) / 2;
                } else {
                    start[0] -= Math.abs(x[j] - x[j - 1]) / 2;
                }

                start[1] = y[i];
                if (i == 0) {
                    start[1] += Math.abs(y[i + 1] - y[i]) / 2;
                } else {
                    start[1] += Math.abs(y[i] - y[i - 1]) / 2;
                }

                end[0] = x[j];
                if (j == x.length - 1) {
                    end[0] += Math.abs(x[j] - x[j - 1]) / 2;
                } else {
                    end[0] += Math.abs(x[j + 1] - x[j]) / 2;
                }

                end[1] = y[i];
                if (i == y.length - 1) {
                    end[1] -= Math.abs(y[i] - y[i - 1]) / 2;
                } else {
                    end[1] -= Math.abs(y[i + 1] - y[i]) / 2;
                }

                g.fillRect(start, end);
            }
        }

        g.clearClip();

        double height = 0.7 / palette.length;
        start[0] = 1.1;
        start[1] = 0.15;
        end[0] = 1.13;
        end[1] = start[1] - height;

        for (int i = 0; i < palette.length; i++) {
            g.setColor(palette[i]);
            g.fillRectBaseRatio(start, end);
            start[1] += height;
            end[1] += height;
        }

        g.setColor(Color.BLACK);
        start[1] -= height;
        end[1] = 0.15 - height;
        g.drawRectBaseRatio(start, end);
        start[0] = 1.14;
        double log = Math.log10(Math.abs(max));
        int decimal = 1;
        if (log < 0) {
            decimal = (int) -log + 1;
        }
        g.drawTextBaseRatio(String.valueOf(Math.round(max, decimal)), 0.0, 1.0, start);

        start[1] = 0.15 - height;
        log = Math.log10(Math.abs(min));
        decimal = 1;
        if (log < 0) {
            decimal = (int) -log + 1;
        }
        g.drawTextBaseRatio(String.valueOf(Math.round(min, decimal)), 0.0, 0.0, start);

        g.setColor(c);
    }
    
    /**
     * Create a plot canvas with the pseudo heat map plot of given data.
     * @param z a data matrix to be shown in pseudo heat map.
     */
    public static PlotCanvas plot(double[][] z) {
        double[] lowerBound = {0, 0};
        double[] upperBound = {z[0].length, z.length};
        PlotCanvas canvas = new PlotCanvas(lowerBound, upperBound, false);
        canvas.add(new Heatmap(z));

        canvas.getAxis(0).setFrameVisible(false);
        canvas.getAxis(0).setLabelVisible(false);
        canvas.getAxis(0).setGridVisible(false);
        canvas.getAxis(1).setFrameVisible(false);
        canvas.getAxis(1).setLabelVisible(false);
        canvas.getAxis(1).setGridVisible(false);

        return canvas;
    }

    /**
     * Create a plot canvas with the pseudo heat map plot of given data.
     * @param z a data matrix to be shown in pseudo heat map.
     * @param palette the color palette.
     */
    public static PlotCanvas plot(double[][] z, Color[] palette) {
        double[] lowerBound = {0, 0};
        double[] upperBound = {z[0].length, z.length};
        PlotCanvas canvas = new PlotCanvas(lowerBound, upperBound, false);
        canvas.add(new Heatmap(z, palette));

        canvas.getAxis(0).setFrameVisible(false);
        canvas.getAxis(0).setLabelVisible(false);
        canvas.getAxis(0).setGridVisible(false);
        canvas.getAxis(1).setFrameVisible(false);
        canvas.getAxis(1).setLabelVisible(false);
        canvas.getAxis(1).setGridVisible(false);

        return canvas;
    }

    /**
     * Create a plot canvas with the pseudo heat map plot of given data.
     * @param x x coordinate of data matrix cells. Must be in ascending order.
     * @param y y coordinate of data matrix cells. Must be in ascending order.
     * @param z a data matrix to be shown in pseudo heat map.
     */
    public static PlotCanvas plot(double[] x, double[] y, double[][] z) {
        double[] lowerBound = {Math.min(x), Math.min(y)};
        double[] upperBound = {Math.max(x), Math.max(y)};
        PlotCanvas canvas = new PlotCanvas(lowerBound, upperBound, false);
        canvas.add(new Heatmap(x, y, z));

        canvas.getAxis(0).setGridVisible(false);
        canvas.getAxis(1).setGridVisible(false);

        return canvas;
    }

    /**
     * Create a plot canvas with the pseudo heat map plot of given data.
     * @param x x coordinate of data matrix cells. Must be in ascending order.
     * @param y y coordinate of data matrix cells. Must be in ascending order.
     * @param z a data matrix to be shown in pseudo heat map.
     * @param palette the color palette.
     */
    public static PlotCanvas plot(double[] x, double[] y, double[][] z, Color[] palette) {
        double[] lowerBound = {Math.min(x), Math.min(y)};
        double[] upperBound = {Math.max(x), Math.max(y)};
        PlotCanvas canvas = new PlotCanvas(lowerBound, upperBound, false);
        canvas.add(new Heatmap(x, y, z, palette));

        canvas.getAxis(0).setGridVisible(false);
        canvas.getAxis(1).setGridVisible(false);

        return canvas;
    }

    /**
     * Create a plot canvas with the pseudo heat map plot of given data.
     * @param z a data matrix to be shown in pseudo heat map.
     * @param rowLabels the labels for rows of data matrix.
     * @param columnLabels the labels for columns of data matrix.
     */
    public static PlotCanvas plot(String[] rowLabels, String[] columnLabels, double[][] z) {
        if (z.length != rowLabels.length || z[0].length != columnLabels.length) {
            throw new IllegalArgumentException("Data size and label size don't match.");
        }

        double[] lowerBound = {0, 0};
        double[] upperBound = {z[0].length, z.length};
        PlotCanvas canvas = new PlotCanvas(lowerBound, upperBound, false);
        canvas.add(new Heatmap(rowLabels, columnLabels, z));

        canvas.getAxis(0).setFrameVisible(false);
        canvas.getAxis(0).setGridVisible(false);
        canvas.getAxis(0).setRotation(-Math.PI / 2);
        canvas.getAxis(1).setFrameVisible(false);
        canvas.getAxis(1).setGridVisible(false);

        double[] locations = new double[rowLabels.length];
        for (int i = 0; i < rowLabels.length; i++) {
            locations[i] = z.length - i - 0.5;
        }
        canvas.getAxis(1).addLabel(rowLabels, locations);

        locations = new double[columnLabels.length];
        for (int i = 0; i < columnLabels.length; i++) {
            locations[i] = i + 0.5;
        }
        canvas.getAxis(0).addLabel(columnLabels, locations);

        return canvas;
    }

    /**
     * Create a plot canvas with the pseudo heat map plot of given data.
     * @param z a data matrix to be shown in pseudo heat map.
     * @param rowLabels the labels for rows of data matrix.
     * @param columnLabels the labels for columns of data matrix.
     * @param palette the color palette.
     */
    public static PlotCanvas plot(String[] rowLabels, String[] columnLabels, double[][] z, Color[] palette) {
        if (z.length != rowLabels.length || z[0].length != columnLabels.length) {
            throw new IllegalArgumentException("Data size and label size don't match.");
        }

        double[] lowerBound = {0, 0};
        double[] upperBound = {z[0].length, z.length};
        PlotCanvas canvas = new PlotCanvas(lowerBound, upperBound, false);
        canvas.add(new Heatmap(rowLabels, columnLabels, z, palette));

        canvas.getAxis(0).setFrameVisible(false);
        canvas.getAxis(0).setGridVisible(false);
        canvas.getAxis(0).setRotation(-Math.PI / 2);
        canvas.getAxis(1).setFrameVisible(false);
        canvas.getAxis(1).setGridVisible(false);

        double[] locations = new double[rowLabels.length];
        for (int i = 0; i < rowLabels.length; i++) {
            locations[i] = z.length - i - 0.5;
        }
        canvas.getAxis(1).addLabel(rowLabels, locations);

        locations = new double[columnLabels.length];
        for (int i = 0; i < columnLabels.length; i++) {
            locations[i] = i + 0.5;
        }
        canvas.getAxis(0).addLabel(columnLabels, locations);

        return canvas;
    }
}
