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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import smile.data.DataFrame;
import smile.math.MathEx;

/**
 * The data is displayed as a collection of points.
 *
 * @author Haifeng Li
 */
public class ScatterPlot extends Plot {

    /**
     * The set of points which may have different marks and/or colors.
     */
    final Point[] points;
    /**
     * The legends of each point group.
     */
    final Optional<Legend[]> legends;

    /**
     * Constructor.
     */
    public ScatterPlot(Point... points) {
        this.points = points;
        legends = Optional.empty();
    }

    /**
     * Constructor.
     */
    public ScatterPlot(Point[] points, Legend[] legends) {
        this.points = points;
        this.legends = Optional.of(legends);
    }

    @Override
    public void paint(Graphics g) {
        for (Point point : points) {
            point.paint(g);
        }
    }

    @Override
    public Optional<Legend[]> legends() {
        return legends;
    }

    @Override
    public double[] getLowerBound() {
        double[] bound = MathEx.colMin(points[0].points);
        for (int k = 1; k < points.length; k++) {
            for (double[] x : points[k].points) {
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
        double[] bound = MathEx.colMax(points[0].points);
        for (int k = 1; k < points.length; k++) {
            for (double[] x : points[k].points) {
                for (int i = 0; i < x.length; i++) {
                    if (bound[i] < x[i]) {
                        bound[i] = x[i];
                    }
                }
            }
        }

        return bound;
    }

    /**
     * Create a scatter plot.
     * @param points a n-by-2 or n-by-3 matrix that describes coordinates of n points.
     */
    public static ScatterPlot of(double[][] points) {
        return new ScatterPlot(Point.of(points));
    }

    /**
     * Create a scatter plot.
     * @param points a n-by-2 or n-by-3 matrix that describes coordinates of n points.
     */
    public static ScatterPlot of(double[][] points, Color color) {
        return new ScatterPlot(Point.of(points, color));
    }

    /**
     * Create a scatter plot.
     * @param points a n-by-2 or n-by-3 matrix that describes coordinates of n points.
     */
    public static ScatterPlot of(double[][] points, char mark) {
        return new ScatterPlot(Point.of(points, mark));
    }

    /**
     * Create a scatter plot.
     * @param points a n-by-2 or n-by-3 matrix that describes coordinates of n points.
     */
    public static ScatterPlot of(double[][] points, char mark, Color color) {
        return new ScatterPlot(new Point(points, mark, color));
    }

    /**
     * Creates a scatter plot of multiple groups of data.
     * @param x the data points. The elements should be of dimension 2 or 3.
     * @param y the group label of data points.
     */
    public static ScatterPlot of(double[][] x, String[] y, char mark) {
        if (x.length != y.length) {
            throw new IllegalArgumentException("The number of points and that of labels are not the same.");
        }

        Map<String, List<Integer>> groups = IntStream.range(0, x.length).boxed().collect(Collectors.groupingBy(i -> y[i]));
        Point[] points = new Point[groups.size()];
        Legend[] legends = new Legend[groups.size()];
        int k = 0;
        for (Map.Entry<String, List<Integer>> group : groups.entrySet()) {
            Color color = Palette.COLORS[k % Palette.COLORS.length];
            points[k] = new Point(
                    group.getValue().stream().map(i -> x[i]).toArray(double[][]::new),
                    mark,
                    color
            );
            legends[k] = new Legend(group.getKey(), color);
            k++;
        }

        return new ScatterPlot(points, legends);
    }

    /**
     * Creates a scatter plot of multiple groups of data.
     * @param x the data points. The elements should be of dimension 2 or 3.
     * @param y the group label of data points.
     */
    public static ScatterPlot of(double[][] x, int[] y, char mark) {
        return of(x, Arrays.stream(y).mapToObj(i -> String.format("class %d", i)).toArray(String[]::new), mark);
    }

    /**
     * Creates a scatter plot from a data frame.
     * @param data the data frame.
     * @param x the column as x-axis.
     * @param y the column as y-axis.
     */
    public static ScatterPlot of(DataFrame data, String x, String y, char mark, Color color) {
        int ix = data.indexOf(x);
        int iy = data.indexOf(y);
        double[][] xy = data.stream().map(row -> new double[]{row.getDouble(ix), row.getDouble(iy)}).toArray(double[][]::new);
        return of(xy, mark, color);
    }

    /**
     * Creates a scatter plot from a data frame.
     * @param data the data frame.
     * @param x the column as x-axis.
     * @param y the column as y-axis.
     * @param category the category column for coloring.
     */
    public static ScatterPlot of(DataFrame data, String x, String y, String category, char mark) {
        int ix = data.indexOf(x);
        int iy = data.indexOf(y);
        double[][] xy = data.stream().map(row -> new double[]{row.getDouble(ix), row.getDouble(iy)}).toArray(double[][]::new);
        String[] label = data.column(category).toStringArray();
        return of(xy, label, mark);
    }

    /**
     * Creates a scatter plot from a data frame.
     * @param data the data frame.
     * @param x the column as x-axis.
     * @param y the column as y-axis.
     * @param z the column as z-axis.
     */
    public static ScatterPlot of(DataFrame data, String x, String y, String z, char mark, Color color) {
        int ix = data.indexOf(x);
        int iy = data.indexOf(y);
        int iz = data.indexOf(z);
        double[][] xyz = data.stream().map(row -> new double[]{row.getDouble(ix), row.getDouble(iy), row.getDouble(iz)}).toArray(double[][]::new);
        return of(xyz, mark, color);
    }

    /**
     * Creates a scatter plot from a data frame.
     * @param data the data frame.
     * @param x the column as x-axis.
     * @param y the column as y-axis.
     * @param z the column as z-axis.
     */
    public static ScatterPlot of(DataFrame data, String x, String y, String z, String category, char mark) {
        int ix = data.indexOf(x);
        int iy = data.indexOf(y);
        int iz = data.indexOf(z);
        double[][] xyz = data.stream().map(row -> new double[]{row.getDouble(ix), row.getDouble(iy), row.getDouble(iz)}).toArray(double[][]::new);
        String[] label = data.column(category).toStringArray();
        return of(xyz, label, mark);
    }
}
