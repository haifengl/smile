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
 * Hexmap is a variant of heat map by replacing rectangle cells with hexagon cells.
 * 
 * @author Haifeng Li
 */
public class Hexmap extends Plot {
    /**
     * The two-dimensional data matrix.
     */
    private double[][] z;
    /**
     * Descriptions for each cell in data matrix.
     */
    private String[][] labels;
    /**
     * The coordinates of hexagons for each cell in data matrix.
     */
    private double[][][][] hexagon;
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
     * @param z a data matrix to be shown in hexmap.
     */
    public Hexmap(double[][] z) {
        this(z, 16);
    }

    /**
     * Constructor. Use jet color palette.
     * @param z a data matrix to be shown in hexmap.
     * @param k the number of colors in the palette.
     */
    public Hexmap(double[][] z, int k) {
        this(z, Palette.jet(k, 1.0f));
    }

    /**
     * Constructor.
     * @param z a data matrix to be shown in hexmap.
     * @param palette the color palette.
     */
    public Hexmap(double[][] z, Color[] palette) {
        this(null, z, palette);
    }
    
    /**
     * Constructor. Use 16-color jet color palette.
     * @param labels the descriptions of each cell in the data matrix.
     * @param z a data matrix to be shown in hexmap.
     */
    public Hexmap(String[][] labels, double[][] z) {
        this(labels, z, 16);
    }

    /**
     * Constructor. Use jet color palette.
     * @param labels the descriptions of each cell in the data matrix.
     * @param z a data matrix to be shown in hexmap.
     * @param k the number of colors in the palette.
     */
    public Hexmap(String[][] labels, double[][] z, int k) {
        this(labels, z, Palette.jet(k, 1.0f));
    }

    /**
     * Constructor.
     * @param labels the descriptions of each cell in the data matrix.
     * @param z a data matrix to be shown in hexmap.
     * @param palette the color palette.
     */
    public Hexmap(String[][] labels, double[][] z, Color[] palette) {
        this.labels = labels;
        this.z = z;
        this.palette = palette;
        
        double s = Math.sqrt(0.75);
        hexagon = new double[z.length][z[0].length][6][2];
        for (int i = 0; i < z.length; i++) {
            for (int j = 0; j < z[i].length; j++) {
                for (int r = 0; r < hexagon[i][j].length; r++) {
                    double a = Math.PI / 3.0 * r;
                    hexagon[i][j][r][0] = j + Math.sin(a)/2;
                    if (i % 2 == 1) hexagon[i][j][r][0] += 0.5;
                    hexagon[i][j][r][1] = (z.length-i)*s + Math.cos(a)/2;
                }                
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
        if (labels == null) {
            return null;
        }
        
        if (coord[0] < -0.5 || coord[0] > z[0].length || coord[1] < 0.36 || coord[1] > z.length * 0.87 + 0.5) {
            return null;
        }
    
        int x = (int) (coord[0] + 0.5);
        int y = (int) (z.length - (coord[1]-0.5) / 0.87);
        for (int i = -3; i < 3; i++) {
            for (int j = -3; j < 3; j++) {
                int xi = x + i;
                int yj = y + j;
                if (xi >= 0 && xi < hexagon[0].length && yj >= 0 && yj < hexagon.length) {
                    if (Math.contains(hexagon[yj][xi], coord)) {
                        return labels[yj][xi];
                    }
                }
            }
        }
        
        return null;
    }
    
    @Override
    public void paint(Graphics g) {
        Color c = g.getColor();

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
                
                g.fillPolygon(hexagon[i][j]);
            }
        }

        g.clearClip();

        double height = 0.7 / palette.length;
        double[] start = new double[2];
        start[0] = 1.1;
        start[1] = 0.15;
        double[] end = new double[2];
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
        if (log < 0) decimal = (int) -log + 1;
        g.drawTextBaseRatio(String.valueOf(Math.round(max, decimal)), 0.0, 1.0, start);

        start[1] = 0.15 - height;
        log = Math.log10(Math.abs(min));
        decimal = 1;
        if (log < 0) decimal = (int) -log + 1;
        g.drawTextBaseRatio(String.valueOf(Math.round(min, decimal)), 0.0, 0.0, start);

        g.setColor(c);
    }

    /**
     * Create a plot canvas with the pseudo hexmap plot of given data.
     * @param data a data matrix to be shown in hexmap.
     */
    public static PlotCanvas plot(double[][] data) {
        double[] lowerBound = {-0.5, 0.36};
        double[] upperBound = {data[0].length, data.length * 0.87 + 0.5};
        PlotCanvas canvas = new PlotCanvas(lowerBound, upperBound, false);
        canvas.add(new Hexmap(data));

        canvas.getAxis(0).setFrameVisible(false);
        canvas.getAxis(0).setLabelVisible(false);
        canvas.getAxis(0).setGridVisible(false);
        canvas.getAxis(1).setFrameVisible(false);
        canvas.getAxis(1).setLabelVisible(false);
        canvas.getAxis(1).setGridVisible(false);

        return canvas;
    }

    /**
     * Create a plot canvas with the pseudo hexmap plot of given data.
     * @param data a data matrix to be shown in hexmap.
     * @param palette the color palette.
     */
    public static PlotCanvas plot(double[][] data, Color[] palette) {
        double[] lowerBound = {-0.5, 0.36};
        double[] upperBound = {data[0].length, data.length * 0.87 + 0.5};
        PlotCanvas canvas = new PlotCanvas(lowerBound, upperBound, false);
        canvas.add(new Hexmap(data, palette));

        canvas.getAxis(0).setFrameVisible(false);
        canvas.getAxis(0).setLabelVisible(false);
        canvas.getAxis(0).setGridVisible(false);
        canvas.getAxis(1).setFrameVisible(false);
        canvas.getAxis(1).setLabelVisible(false);
        canvas.getAxis(1).setGridVisible(false);

        return canvas;
    }

    /**
     * Create a plot canvas with the pseudo hexmap plot of given data.
     * @param labels the descriptions of each cell in the data matrix.
     * @param data a data matrix to be shown in hexmap.
     */
    public static PlotCanvas plot(String[][] labels, double[][] data) {
        double[] lowerBound = {-0.5, 0.36};
        double[] upperBound = {data[0].length, data.length * 0.87 + 0.5};
        PlotCanvas canvas = new PlotCanvas(lowerBound, upperBound, false);
        canvas.add(new Hexmap(labels, data));

        canvas.getAxis(0).setFrameVisible(false);
        canvas.getAxis(0).setLabelVisible(false);
        canvas.getAxis(0).setGridVisible(false);
        canvas.getAxis(1).setFrameVisible(false);
        canvas.getAxis(1).setLabelVisible(false);
        canvas.getAxis(1).setGridVisible(false);

        return canvas;
    }

    /**
     * Create a plot canvas with the pseudo hexmap plot of given data.
     * @param labels the descriptions of each cell in the data matrix.
     * @param data a data matrix to be shown in hexmap.
     * @param palette the color palette.
     */
    public static PlotCanvas plot(String[][] labels, double[][] data, Color[] palette) {
        double[] lowerBound = {-0.5, 0.36};
        double[] upperBound = {data[0].length, data.length * 0.87 + 0.5};
        PlotCanvas canvas = new PlotCanvas(lowerBound, upperBound, false);
        canvas.add(new Hexmap(labels, data, palette));

        canvas.getAxis(0).setFrameVisible(false);
        canvas.getAxis(0).setLabelVisible(false);
        canvas.getAxis(0).setGridVisible(false);
        canvas.getAxis(1).setFrameVisible(false);
        canvas.getAxis(1).setLabelVisible(false);
        canvas.getAxis(1).setGridVisible(false);

        return canvas;
    }
}
