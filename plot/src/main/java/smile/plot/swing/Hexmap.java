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
 * Hexmap is a variant of heat map by replacing rectangle cells with hexagon cells.
 * 
 * @author Haifeng Li
 */
public class Hexmap extends Plot {
    /** The lambda interface to retrieve the tooltip of cell. */
    public interface Tooltip {
        /** Gets the tooltip of cell at (i, j). */
        String get(int i, int j);
    }

    /**
     * The two-dimensional data matrix.
     */
    private double[][] z;
    /**
     * Tooltip lambda.
     */
    private Tooltip tooltip;
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
     * Constructor.
     * @param z a data matrix to be shown in hexmap.
     * @param palette the color palette.
     * @param tooltip the lambda to return the description of cells.
     */
    public Hexmap(double[][] z, Color[] palette, Tooltip tooltip) {
        this.z = z;
        this.palette = palette;
        this.tooltip = tooltip;

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
            min = values[(int) Math.round(0.01 * i)];
            max = values[(int) Math.round(0.99 * (i-1))];
            width = (max - min) / palette.length;
        }
    }

    @Override
    public Optional<String> tooltip(double[] coord) {
        if (tooltip == null) return Optional.empty();

        if (coord[0] < -0.5 || coord[0] > z[0].length || coord[1] < 0.36 || coord[1] > z.length * 0.87 + 0.5) {
            return Optional.empty();
        }
    
        int x = (int) (coord[0] + 0.5);
        int y = (int) (z.length - (coord[1]-0.5) / 0.87);
        for (int i = -3; i < 3; i++) {
            for (int j = -3; j < 3; j++) {
                int xi = x + i;
                int yj = y + j;
                if (xi >= 0 && xi < hexagon[0].length && yj >= 0 && yj < hexagon.length) {
                    if (MathEx.contains(hexagon[yj][xi], coord)) {
                        return Optional.of(tooltip.get(yj, xi));
                    }
                }
            }
        }

        return Optional.empty();
    }

    @Override
    public double[] getLowerBound() {
        double[] bound = {-0.5, 0.36};
        return bound;
    }

    @Override
    public double[] getUpperBound() {
        double[] bound = {z[0].length, z.length * 0.87 + 0.5};
        return bound;
    }

    @Override
    public void paint(Graphics g) {
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
        g.drawTextBaseRatio(String.valueOf(MathEx.round(max, decimal)), start,0.0, 1.0);

        start[1] = 0.15 - height;
        log = Math.log10(Math.abs(min));
        decimal = 1;
        if (log < 0) decimal = (int) -log + 1;
        g.drawTextBaseRatio(String.valueOf(MathEx.round(min, decimal)), start,0.0, 0.0);
    }

    @Override
    public Canvas canvas() {
        Canvas canvas = new Canvas(getLowerBound(), getUpperBound(), false);
        canvas.add(this);

        canvas.getAxis(0).setFrameVisible(false);
        canvas.getAxis(0).setTickVisible(false);
        canvas.getAxis(0).setGridVisible(false);
        canvas.getAxis(1).setFrameVisible(false);
        canvas.getAxis(1).setTickVisible(false);
        canvas.getAxis(1).setGridVisible(false);

        return canvas;
    }

    /**
     * Creates a hexmap with 16-color jet color palette.
     * @param z a data matrix to be shown in hexmap.
     */
    public static Hexmap of(double[][] z) {
        return of(z, 16);
    }

    /**
     * Creates a hexmap with the jet color palette.
     * @param z a data matrix to be shown in hexmap.
     * @param k the number of colors in the palette.
     */
    public static Hexmap  of(double[][] z, int k) {
        return of(z, Palette.jet(k, 1.0f));
    }

    /**
     * Constructor.
     * @param z a data matrix to be shown in hexmap.
     * @param palette the color palette.
     */
    public static Hexmap of(double[][] z, Color[] palette) {
        return new Hexmap(z, palette, null);
    }
}
