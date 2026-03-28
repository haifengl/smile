/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * SMILE is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with SMILE. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.plot.swing;

import java.awt.Color;
import java.util.Arrays;
import smile.math.MathEx;
import smile.tensor.SparseMatrix;

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
    private final SparseMatrix matrix;
    /**
     * The x coordinate of matrix entries.
     */
    private final double[] x;
    /**
     * The y coordinate of matrix entries.
     */
    private final double[] y;
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
     * @param matrix the sparse matrix.
     * @param color the color of plot.
     */
    public SparseMatrixPlot(SparseMatrix matrix, Color color) {
        super(color);
        this.matrix = matrix;

        int m = matrix.nrow();
        int n = matrix.ncol();
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
     * Constructor.
     * @param matrix the sparse matrix.
     * @param palette the color palette.
     */
    public SparseMatrixPlot(SparseMatrix matrix, Color[] palette) {
        this.matrix = matrix;
        this.palette = palette;

        int m = matrix.nrow();
        int n = matrix.ncol();
        x = new double[n];
        for (int i = 0; i < x.length; i++) {
            x[i] = i + 0.5;
        }

        y = new double[m];
        for (int i = 0; i < y.length; i++) {
            y[i] = y.length - i - 0.5;
        }

        // In case of outliers, we use 1% and 99% quantiles as lower and
        // upper limits instead of min and max.
        double[] values = matrix.nonzeros().mapToDouble(e -> e.x).filter(x -> !Double.isNaN(x)).toArray();

        if (values.length == 0) {
            throw new IllegalArgumentException("Sparse matrix has no non-zero values");
        }

        Arrays.sort(values);
        min = values[(int) Math.round(0.01 * values.length)];
        max = values[(int) Math.round(0.99 * (values.length-1))];
        width = (max - min) / palette.length;
    }

    @Override
    public double[] getLowerBound() {
        return new double[]{0, 0};
    }

    @Override
    public double[] getUpperBound() {
        return new double[]{matrix.ncol(), matrix.nrow()};
    }

    @Override
    public void paint(Renderer g) {
        double[] start = new double[2];
        double[] end = new double[2];

        g.setColor(color);
        for (SparseMatrix.Entry cell : matrix) {
            int i = cell.i;
            int j = cell.j;
            double z = cell.x;

            if (palette != null) {
                int k = (int) ((z - min) / width);

                if (k < 0) k = 0;
                if (k >= palette.length) k = palette.length - 1;

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

        if (palette != null) {
            g.clearClip();

            double height = 0.7 / palette.length;
            start[0] = 1.1;
            start[1] = 0.15;
            end[0] = 1.13;
            end[1] = start[1] - height;

            for (var color : palette) {
                g.setColor(color);
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
            g.drawTextBaseRatio(String.valueOf(MathEx.round(max, decimal)), start, 0.0, 1.0);

            start[1] = 0.15 - height;
            log = Math.log10(Math.abs(min));
            decimal = 1;
            if (log < 0) {
                decimal = (int) -log + 1;
            }
            g.drawTextBaseRatio(String.valueOf(MathEx.round(min, decimal)), start, 0.0, 0.0);
        }
    }

    @Override
    public Figure figure() {
        double[] lowerBound = {0, 0};
        double[] upperBound = {matrix.ncol(), matrix.nrow()};
        Figure canvas = new Figure(lowerBound, upperBound, false);
        canvas.add(this);

        canvas.getAxis(0).setTickVisible(false);
        canvas.getAxis(0).setGridVisible(false);
        canvas.getAxis(1).setTickVisible(false);
        canvas.getAxis(1).setGridVisible(false);

        return canvas;
    }

    /**
     * Creates a sparse matrix plot with blue color for nonzero entries.
     * @param matrix the sparse matrix.
     * @return the plot.
     */
    public static SparseMatrixPlot of(SparseMatrix matrix) {
        return new SparseMatrixPlot(matrix, Color.BLUE);
    }

    /**
     * Creates a sparse matrix plot with the jet color palette.
     * @param matrix the sparse matrix.
     * @param k the number of colors in the palette.
     * @return the plot.
     */
    public static SparseMatrixPlot of(SparseMatrix matrix, int k) {
        return new SparseMatrixPlot(matrix, Palette.jet(k, 1.0f));
    }
}
