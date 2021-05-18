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
import java.util.Arrays;
import java.util.Optional;
import smile.math.MathEx;

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
     * The y coordinate for rows of data matrix.
     */
    private double[] y;
    /**
     * The two-dimensional data matrix.
     */
    private double[][] z;
    /**
     * The labels for columns of data matrix.
     */
    private String[] columnLabels;
    /**
     * The labels for rows of data matrix.
     */
    private String[] rowLabels;
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
     * If show axis marks.
     */
    private boolean isLabelVisible;

    /**
     * Constructor.
     * @param rowLabels the labels of rows.
     * @param columnLabels the labels of columns.
     * @param z a data matrix to be shown in pseudo heat map.
     * @param palette the color palette.
     */
    public Heatmap(String[] rowLabels, String[] columnLabels, double[][] z, Color[] palette) {
        if (columnLabels.length != z[0].length) {
            throw new IllegalArgumentException("columnLabels.length != z[0].length");
        }

        if (rowLabels.length != z.length) {
            throw new IllegalArgumentException("rowLabels.length != z.length");
        }

        this.z = z;
        this.columnLabels = columnLabels;
        this.rowLabels = rowLabels;
        this.palette = palette;
        init();
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
        isLabelVisible = x != null || y != null || rowLabels != null || columnLabels != null;

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

        if (x.length != z[0].length) {
            throw new IllegalArgumentException("x.length != z[0].length");
        }

        if (y.length != z.length) {
            throw new IllegalArgumentException("y.length != z.length");
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
            min = values[(int) Math.round(0.01 * i)];
            max = values[(int) Math.round(0.99 * (i-1))];
            width = (max - min) / palette.length;
        }
    }

    @Override
    public Optional<String> tooltip(double[] coord) {
        if (rowLabels == null || columnLabels == null) {
            return Optional.empty();
        }
        
        if (coord[0] < 0.0 || coord[0] > z[0].length || coord[1] < 0.0 || coord[1] > z.length) {
            return Optional.empty();
        }

        int i = (int) coord[0];
        int j = (int) (y.length - coord[1]);
        
        return Optional.of(String.format("%s, %s", rowLabels[j], columnLabels[i]));
    }

    @Override
    public double[] getLowerBound() {
        double[] bound = {MathEx.min(x), MathEx.min(y)};
        return bound;
    }

    @Override
    public double[] getUpperBound() {
        double[] bound = {MathEx.max(x), MathEx.max(y)};
        return bound;
    }

    @Override
    public void paint(Graphics g) {
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
        g.drawTextBaseRatio(String.valueOf(MathEx.round(max, decimal)), start,0.0, 1.0);

        start[1] = 0.15 - height;
        log = Math.log10(Math.abs(min));
        decimal = 1;
        if (log < 0) {
            decimal = (int) -log + 1;
        }
        g.drawTextBaseRatio(String.valueOf(MathEx.round(min, decimal)), start,0.0, 0.0);
    }
    
    @Override
    public Canvas canvas() {
        Canvas canvas = new Canvas(getLowerBound(), getUpperBound(), false);
        canvas.add(this);

        canvas.getAxis(0).setGridVisible(false);
        canvas.getAxis(1).setGridVisible(false);

        if (!isLabelVisible) {
            canvas.getAxis(0).setTickVisible(false);
            canvas.getAxis(0).setFrameVisible(false);
            canvas.getAxis(1).setTickVisible(false);
            canvas.getAxis(1).setFrameVisible(false);
        }

        if (rowLabels != null) {
            double[] locations = new double[rowLabels.length];
            for (int i = 0; i < rowLabels.length; i++) {
                locations[i] = z.length - i - 0.5;
            }
            canvas.getAxis(1).setTicks(rowLabels, locations);
        }

        if (columnLabels != null) {
            canvas.getAxis(0).setRotation(-Math.PI / 2);

            double[] locations = new double[columnLabels.length];
            for (int i = 0; i < columnLabels.length; i++) {
                locations[i] = i + 0.5;
            }
            canvas.getAxis(0).setTicks(columnLabels, locations);
        }

        return canvas;
    }

    /**
     * Constructor. Use 16-color jet color palette.
     */
    public static Heatmap of(double[][] z) {
        return of(z, 16);
    }

    /**
     * Creates a heatmap with jet color palette.
     * @param z a data matrix to be shown in pseudo heat map.
     * @param k the number of colors in the palette.
     */
    public static Heatmap of(double[][] z, int k) {
        return of(z, Palette.jet(k, 1.0f));
    }

    /**
     * Constructor.
     * @param z a data matrix to be shown in pseudo heat map.
     * @param palette the color palette.
     */
    public static Heatmap of(double[][] z, Color[] palette) {
        return new Heatmap((double[]) null, null, z, palette);
    }

    /**
     * Constructor. Use 16-color jet color palette.
     */
    public static Heatmap of(String[] rowLabels, String[] columnLabels, double[][] z) {
        return of(rowLabels, columnLabels, z, 16);
    }

    /**
     * Constructor. Use jet color palette.
     * @param z a data matrix to be shown in pseudo heat map.
     * @param k the number of colors in the palette.
     */
    public static Heatmap of(String[] rowLabels, String[] columnLabels, double[][] z, int k) {
        return new Heatmap(rowLabels, columnLabels, z, Palette.jet(k, 1.0f));
    }

    /**
     * Constructor. Use 16-color jet color palette.
     * @param x x coordinate of data matrix cells. Must be in ascending order.
     * @param y y coordinate of data matrix cells. Must be in ascending order.
     * @param z a data matrix to be shown in pseudo heat map.
     */
    public static Heatmap of(double[] x, double[] y, double[][] z) {
        return of(x, y, z, 16);
    }

    /**
     * Constructor. Use jet color palette.
     * @param x x coordinate of data matrix cells. Must be in ascending order.
     * @param y y coordinate of data matrix cells. Must be in ascending order.
     * @param z a data matrix to be shown in pseudo heat map.
     * @param k the number of colors in the palette.
     */
    public static Heatmap of(double[] x, double[] y, double[][] z, int k) {
        return new Heatmap(x, y, z, Palette.jet(k, 1.0f));
    }
}
