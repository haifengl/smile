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

package smile.plot.swing;

import java.awt.Color;
import java.awt.Stroke;
import smile.math.MathEx;

/**
 * Line plot is a special scatter plot which connects points by straight lines.
 *
 * @author Haifeng Li
 */
public class LinePlot extends ScatterPlot {

    /**
     * If draw the legend of points.
     */
    private boolean drawDot = false;

    /**
     * The poly line of points.
     */
    private Line line;

    /**
     * Constructor.
     */
    public LinePlot(double[][] data) {
        this(data, Line.Style.SOLID);
    }

    /**
     * Constructor.
     */
    public LinePlot(double[][] data, Line.Style style) {
        super(data, '.');
        line = new Line(style, data);
    }

    @Override
    public LinePlot setColor(Color color) {
        super.setColor(color);
        line.setColor(color);
        return this;
    }

    @Override
    public Color getColor() {
        return line.getColor();
    }

    /**
     * Returns the line stroke style.
     */
    public Stroke getStroke() {
        return line.getStroke();
    }

    /**
     * Set the line stroke style.
     */
    public LinePlot setStroke(Stroke stroke) {
        line.setStroke(stroke);
        return this;
    }

    @Override
    public LinePlot setLegend(char legend) {
        super.setLegend(legend);
        drawDot = true;
        return this;
    }

    @Override
    public void paint(Graphics g) {
        line.paint(g);

        if (drawDot) {
            super.paint(g);
        }
    }
    
    /**
     * Create a plot canvas with the poly line plot of given data.
     * @param y a data vector that describes y coordinates of points. The x
     * coordinates will be [0, n), where n is the length of y.
     */
    public static PlotCanvas plot(double[] y) {
        return plot(null, y);
    }

    /**
     * Create a plot canvas with the poly line plot of given data.
     * @param id the id of the plot.
     * @param y a data vector that describes y coordinates of points. The x
     * coordinates will be [0, n), where n is the length of y.
     */
    public static PlotCanvas plot(String id, double[] y) {
        double[] lowerBound = {0, MathEx.min(y)};
        double[] upperBound = {y.length, MathEx.max(y)};
        PlotCanvas canvas = new PlotCanvas(lowerBound, upperBound, false);
        canvas.base.extendBound(1);

        double[][] data = new double[y.length][2];
        for (int i = 0; i < data.length; i++) {
            data[i][0] = i;
            data[i][1] = y[i];
        }

        LinePlot plot = new LinePlot(data);
        plot.setID(id);
        canvas.add(plot);
        return canvas;
    }

    /**
     * Create a plot canvas with the poly line plot of given data.
     * @param y a data vector that describes y coordinates of points. The x
     * coordinates will be [0, n), where n is the length of y.
     * @param style the stroke style of line.
     */
    public static PlotCanvas plot(double[] y, Line.Style style) {
        return plot(null, y, style);
    }

    /**
     * Create a plot canvas with the poly line plot of given data.
     * @param id the id of the plot.
     * @param y a data vector that describes y coordinates of points. The x
     * coordinates will be [0, n), where n is the length of y.
     * @param style the stroke style of line.
     */
    public static PlotCanvas plot(String id, double[] y, Line.Style style) {
        double[] lowerBound = {0, MathEx.min(y)};
        double[] upperBound = {y.length, MathEx.max(y)};
        PlotCanvas canvas = new PlotCanvas(lowerBound, upperBound, false);
        canvas.base.extendBound(1);

        double[][] data = new double[y.length][2];
        for (int i = 0; i < data.length; i++) {
            data[i][0] = i;
            data[i][1] = y[i];
        }

        LinePlot plot = new LinePlot(data, style);
        plot.setID(id);
        canvas.add(plot);
        return canvas;
    }

    /**
     * Create a plot canvas with the poly line plot of given data.
     * @param y a data vector that describes y coordinates of points. The x
     * coordinates will be [0, n), where n is the length of y.
     * @param style the stroke style of line.
     * @param color the color of line.
     */
    public static PlotCanvas plot(double[] y, Line.Style style, Color color) {
        return plot(null, y, style, color);
    }

    /**
     * Create a plot canvas with the poly line plot of given data.
     * @param id the id of the plot.
     * @param y a data vector that describes y coordinates of points. The x
     * coordinates will be [0, n), where n is the length of y.
     * @param style the stroke style of line.
     * @param color the color of line.
     */
    public static PlotCanvas plot(String id, double[] y, Line.Style style, Color color) {
        double[] lowerBound = {0, MathEx.min(y)};
        double[] upperBound = {y.length, MathEx.max(y)};
        PlotCanvas canvas = new PlotCanvas(lowerBound, upperBound, false);
        canvas.base.extendBound(1);

        double[][] data = new double[y.length][2];
        for (int i = 0; i < data.length; i++) {
            data[i][0] = i;
            data[i][1] = y[i];
        }

        LinePlot plot = new LinePlot(data, style);
        plot.setID(id);
        plot.setColor(color);
        canvas.add(plot);
        return canvas;
    }

    /**
     * Create a plot canvas with the poly line plot of given data.
     * @param data a n-by-2 or n-by-3 matrix that describes coordinates of points.
     * @param style the stroke style of line.
     */
    public static PlotCanvas plot(double[][] data, Line.Style style) {
        return plot(null, data, style);
    }

    /**
     * Create a plot canvas with the poly line plot of given data.
     * @param id the id of the plot.
     * @param data a n-by-2 or n-by-3 matrix that describes coordinates of points.
     * @param style the stroke style of line.
     */
    public static PlotCanvas plot(String id, double[][] data, Line.Style style) {
        if (data[0].length != 2 && data[0].length != 3) {
            throw new IllegalArgumentException("Invalid data dimension: " + data[0].length);
        }

        double[] lowerBound = MathEx.colMin(data);
        double[] upperBound = MathEx.colMax(data);
        PlotCanvas canvas = new PlotCanvas(lowerBound, upperBound);

        LinePlot plot = new LinePlot(data, style);
        plot.setID(id);
        canvas.add(plot);
        return canvas;
    }

    /**
     * Create a plot canvas with the poly line plot of given data.
     * @param data a n-by-2 or n-by-3 matrix that describes coordinates of points.
     * @param style the stroke style of line.
     * @param color the color of line.
     */
    public static PlotCanvas plot(double[][] data, Line.Style style, Color color) {
        return plot(null, data, style, color);
    }

    /**
     * Create a plot canvas with the poly line plot of given data.
     * @param id the id of the plot.
     * @param data a n-by-2 or n-by-3 matrix that describes coordinates of points.
     * @param style the stroke style of line.
     * @param color the color of line.
     */
    public static PlotCanvas plot(String id, double[][] data, Line.Style style, Color color) {
        if (data[0].length != 2 && data[0].length != 3) {
            throw new IllegalArgumentException("Invalid data dimension: " + data[0].length);
        }

        double[] lowerBound = MathEx.colMin(data);
        double[] upperBound = MathEx.colMax(data);
        PlotCanvas canvas = new PlotCanvas(lowerBound, upperBound);

        LinePlot plot = new LinePlot(data, style);
        plot.setID(id);
        plot.setColor(color);
        canvas.add(plot);

        return canvas;
    }
}
