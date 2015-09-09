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
import smile.math.matrix.SparseMatrix;

/**
 * A graphical representation of sparse matrix data. Optionally, the values
 * in the matrix can be represented as colors.
 * 
 * @author Haifeng Li
 */
public class SparseMatrixPlot extends Plot {

    /**
     * The sparse matrix.
     */
    private SparseMatrix sparse;
    /**
     * The x coordinate of matrix entries.
     */
    private double[] x;
    /**
     * The y coordinate of matrix entries.
     */
    private double[] y;
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
     * Constructor. Use blue color for nonzero entries.
     */
    public SparseMatrixPlot(SparseMatrix sparse) {
        this(sparse, Color.BLUE);
    }

    /**
     * Constructor.
     */
    public SparseMatrixPlot(SparseMatrix sparse, Color color) {
        super(color);
        this.sparse = sparse;

        int m = sparse.nrows();
        int n = sparse.ncols();
        x = new double[n];
        for (int i = 0; i < x.length; i++) {
            x[i] = i + 0.5;
        }

        y = new double[m];
        for (int i = 0; i < y.length; i++) {
            y[i] = y.length - i - 0.5;
        }
    }

    /**
     * Constructor. Use jet color palette.
     * @param k the number of colors in the palette.
     */
    public SparseMatrixPlot(SparseMatrix sparse, int k) {
        this(sparse, Palette.jet(k, 1.0f));
    }

    /**
     * Constructor.
     * @param palette the color palette.
     */
    public SparseMatrixPlot(SparseMatrix sparse, Color[] palette) {
        this.sparse = sparse;
        this.palette = palette;

        int m = sparse.nrows();
        int n = sparse.ncols();
        x = new double[n];
        for (int i = 0; i < x.length; i++) {
            x[i] = i + 0.5;
        }

        y = new double[m];
        for (int i = 0; i < y.length; i++) {
            y[i] = y.length - i - 0.5;
        }

        min = Double.POSITIVE_INFINITY;
        max = Double.NEGATIVE_INFINITY;
        for (double z : sparse.values()) {
            if (z < min) {
                min = z;
            }
            if (z > max) {
                max = z;
            }
        }

        // In case of outliers, we use 1% and 99% quantiles as lower and
        // upper limits instead of min and max.
        double[] values = new double[sparse.size()];
        int i = 0;
        for (double z : sparse.values()) {
            if (!Double.isNaN(z)) {
                values[i++] = z;
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
    public void paint(Graphics g) {
        Color c = g.getColor();

        double[] start = new double[2];
        double[] end = new double[2];

        if (palette != null) {
            int m = sparse.nrows();
            int n = sparse.ncols();
            for (int i = 0; i < m; i++) {
                for (int j = 0; j < n; j++) {
                    double z = sparse.get(i, j);
                    if (z != 0.0 && !Double.isNaN(z)) {
                        int k = (int) ((z - min) / width);
                        
                        if (k < 0) {
                            k = 0;
                        }

                        if (k >= palette.length) {
                            k = palette.length - 1;
                        }
                        
                        g.setColor(palette[k]);

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
            }
        } else {
            g.setColor(getColor());
            int m = sparse.nrows();
            int n = sparse.ncols();
            for (int i = 0; i < m; i++) {
                for (int j = 0; j < n; j++) {
                    double z = sparse.get(i, j);
                    if (z != 0.0) {
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
            }
        }

        if (palette != null) {
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
        }

        g.setColor(c);
    }

    /**
     * Create a sparse matrix plot canvas.
     * @param sparse a sparse matrix.
     */
    public static PlotCanvas plot(SparseMatrix sparse) {
        double[] lowerBound = {0, 0};
        double[] upperBound = {sparse.ncols(), sparse.nrows()};
        PlotCanvas canvas = new PlotCanvas(lowerBound, upperBound, false);
        canvas.add(new SparseMatrixPlot(sparse));

        canvas.getAxis(0).setLabelVisible(false);
        canvas.getAxis(0).setGridVisible(false);
        canvas.getAxis(1).setLabelVisible(false);
        canvas.getAxis(1).setGridVisible(false);

        return canvas;
    }

    /**
     * Create a sparse matrix plot canvas.
     * @param sparse a sparse matrix.
     */
    public static PlotCanvas plot(SparseMatrix sparse, Color color) {
        double[] lowerBound = {0, 0};
        double[] upperBound = {sparse.ncols(), sparse.nrows()};
        PlotCanvas canvas = new PlotCanvas(lowerBound, upperBound, false);
        canvas.add(new SparseMatrixPlot(sparse, color));

        canvas.getAxis(0).setLabelVisible(false);
        canvas.getAxis(0).setGridVisible(false);
        canvas.getAxis(1).setLabelVisible(false);
        canvas.getAxis(1).setGridVisible(false);

        return canvas;
    }

    /**
     * Create a sparse matrix plot canvas.
     * @param sparse a sparse matrix.
     */
    public static PlotCanvas plot(SparseMatrix sparse, Color[] palette) {
        double[] lowerBound = {0, 0};
        double[] upperBound = {sparse.ncols(), sparse.nrows()};
        PlotCanvas canvas = new PlotCanvas(lowerBound, upperBound, false);
        canvas.add(new SparseMatrixPlot(sparse, palette));

        canvas.getAxis(0).setLabelVisible(false);
        canvas.getAxis(0).setGridVisible(false);
        canvas.getAxis(1).setLabelVisible(false);
        canvas.getAxis(1).setGridVisible(false);

        return canvas;
    }
}
