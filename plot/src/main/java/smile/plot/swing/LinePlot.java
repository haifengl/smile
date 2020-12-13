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
import java.util.Optional;
import smile.math.MathEx;

/**
 * Line plot is a special scatter plot which connects points by straight lines.
 *
 * @author Haifeng Li
 */
public class LinePlot extends Plot {
    /**
     * The set of lines which may have different stroke, marks, and/or colors.
     */
    final Line[] lines;
    /**
     * The legends of each line.
     */
    final Optional<Legend[]> legends;

    /**
     * Constructor.
     */
    public LinePlot(Line... lines) {
        this.lines = lines;
        legends = Optional.empty();
    }

    /**
     * Constructor.
     */
    public LinePlot(Line[] lines, Legend[] legends) {
        this.lines = lines;
        this.legends = Optional.of(legends);
    }

    @Override
    public Optional<Legend[]> legends() {
        return legends;
    }

    @Override
    public Canvas canvas() {
        Canvas canvas = new Canvas(getLowerBound(), getUpperBound());
        canvas.add(this);
        return canvas;
    }

    @Override
    public double[] getLowerBound() {
        double[] bound = MathEx.colMin(lines[0].points);
        for (int k = 1; k < lines.length; k++) {
            for (double[] x : lines[k].points) {
                for (int i = 0; i < x.length; i++) {
                    if (bound[i] > x[i]) {
                        bound[i] = x[i];
                    }
                }
            }
        }

        return bound;
    }

    @Override
    public double[] getUpperBound() {
        double[] bound = MathEx.colMax(lines[0].points);
        for (int k = 1; k < lines.length; k++) {
            for (double[] x : lines[k].points) {
                for (int i = 0; i < x.length; i++) {
                    if (bound[i] < x[i]) {
                        bound[i] = x[i];
                    }
                }
            }
        }

        return bound;
    }

    @Override
    public void paint(Graphics g) {
        for (Line line : lines) {
            line.paint(g);
        }
    }

    /**
     * Creates a line plot.
     */
    public static LinePlot of(double[][] data) {
        return new LinePlot(Line.of(data));
    }

    /**
     * Creates a line plot.
     */
    public static LinePlot of(double[][] data, Line.Style style) {
        return new LinePlot(Line.of(data, style));
    }

    /**
     * Creates a line plot.
     */
    public static LinePlot of(double[][] data, Color color) {
        return new LinePlot(Line.of(data, color));
    }

    /**
     * Creates a line plot.
     */
    public static LinePlot of(double[][] data, Line.Style style, Color color) {
        return new LinePlot(Line.of(data, style, color));
    }

    /**
     * Creates a line plot.
     */
    public static LinePlot of(double[][] data, Line.Style style, Color color, String label) {
        Line[] line = {Line.of(data, style, color)};
        Legend[] legend = {new Legend(label, color)};
        return new LinePlot(line, legend);
    }

    /**
     * Creates a line plot with the index as the x coordinate.
     * @param y the data vector of y coordinates.
     *          The x coordinates will be [0, n), where n is the length of y.
     */
    public static LinePlot of(double[] y) {
        return of(Line.zipWithIndex(y));
    }

    /**
     * Creates a line plot with the index as the x coordinate.
     * @param y the data vector of y coordinates.
     *          The x coordinates will be [0, n), where n is the length of y.
     */
    public static LinePlot of(double[] y, Line.Style style) {
        return new LinePlot(Line.of(Line.zipWithIndex(y), style));
    }

    /**
     * Creates a line plot with the index as the x coordinate.
     * @param y the data vector of y coordinates.
     *          The x coordinates will be [0, n), where n is the length of y.
     */
    public static LinePlot of(double[] y, Color color) {
        return new LinePlot(Line.of(Line.zipWithIndex(y), color));
    }

    /**
     * Creates a line plot with the index as the x coordinate.
     * @param y the data vector of y coordinates.
     *          The x coordinates will be [0, n), where n is the length of y.
     */
    public static LinePlot of(double[] y, Line.Style style, Color color) {
        return new LinePlot(Line.of(Line.zipWithIndex(y), style, color));
    }

    /**
     * Creates a line plot with the index as the x coordinate.
     * @param y the data vector of y coordinates.
     *          The x coordinates will be [0, n), where n is the length of y.
     */
    public static LinePlot of(double[] y, Line.Style style, Color color, String label) {
        Line[] line = {Line.of(Line.zipWithIndex(y), style, color)};
        Legend[] legend = {new Legend(label, color)};
        return new LinePlot(line, legend);
    }
}
