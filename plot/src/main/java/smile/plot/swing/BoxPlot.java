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

import java.util.Arrays;
import java.util.Optional;
import smile.math.MathEx;

/**
 * A boxplot is a convenient way of graphically depicting groups of numerical
 * data through their five-number summaries the smallest observation
 * (sample minimum), lower quartile (Q1), median (Q2), upper quartile (Q3),
 * and largest observation (sample maximum). A boxplot may also indicate
 * which observations, if any, might be considered outliers.
 * <p>
 * Boxplots can be useful to display differences between populations without
 * making any assumptions of the underlying statistical distribution: they are
 * non-parametric. The spacings between the different parts of the box help
 * indicate the degree of dispersion (spread) and skewness in the data, and
 * identify outliers.
 * <p>
 * For a data set, we construct a boxplot in the following manner:
 * <ul>
 * <li> Calculate the first q<sub>1</sub>, the median q<sub>2</sub> and third
 * quartile q<sub>3</sub>.
 * <li> Calculate the interquartile range (IQR) by subtracting the first
 * quartile from the third quartile. (q<sub>3</sub> ? q<sub>1</sub>)
 * <li> Construct a box above the number line bounded on the bottom by the first
 * quartile (q<sub>1</sub>) and on the top by the third quartile (q<sub>3</sub>).
 * <li> Indicate where the median lies inside of the box with the presence of
 * a line dividing the box at the median value.
 * <li> Any data observation which lies more than 1.5*IQR lower than the first
 * quartile or 1.5IQR higher than the third quartile is considered an outlier.
 * Indicate where the smallest value that is not an outlier is by connecting it
 * to the box with a horizontal line or "whisker". Optionally, also mark the
 * position of this value more clearly using a small vertical line. Likewise,
 * connect the largest value that is not an outlier to the box by a "whisker"
 * (and optionally mark it with another small vertical line).
 * <li> Indicate outliers by dots.
 * </ul>
 *
 * @author Haifeng Li
 */
public class BoxPlot extends Plot {
    /**
     * Tooltip format string.
     */
    private static final String format = "<table border=\"1\"><tr><td>Median</td><td align=\"right\">%g</td></tr><tr><td>Q1</td><td align=\"right\">%g</td></tr><tr><td>Q3</td><td align=\"right\">%g</td></tr></table>";

    /**
     * The input data. Each row is a variable.
     */
    private final double[][] data;
    /**
     * The label of each variable.
     */
    private final String[] labels;
    /**
     * The quantiles of data.
     */
    private final double[][] quantiles;

    /**
     * Constructor.
     * @param data the input dataset of which each row is a set of samples
     *            and will have a corresponding box plot.
     */
    public BoxPlot(double[][] data, String[] labels) {
        if (labels != null && labels.length != data.length) {
            throw new IllegalArgumentException("Data size and label size don't match.");
        }
        
        this.data = data;
        this.labels = labels;

        // Calculate quantiles.
        quantiles = new double[data.length][8];
        for (int i = 0; i < data.length; i++) {
            int n = data[i].length;
            Arrays.sort(data[i]);
            quantiles[i][1] = data[i][n / 4];
            quantiles[i][2] = data[i][n / 2];
            quantiles[i][3] = data[i][3 * n / 4];
            quantiles[i][5] = quantiles[i][3] - quantiles[i][1]; // interquartile range
            quantiles[i][6] = quantiles[i][1] - 1.5 * quantiles[i][5];
            quantiles[i][7] = quantiles[i][3] + 1.5 * quantiles[i][5];
            quantiles[i][0] = quantiles[i][6] < data[i][0] ? data[i][0] : quantiles[i][6];
            quantiles[i][4] = quantiles[i][7] > data[i][data[i].length - 1] ? data[i][data[i].length - 1] : quantiles[i][7];
        }
    }

    @Override
    public Optional<String> tooltip(double[] coord) {
        String tooltip = null;
        for (int i = 0; i < data.length; i++) {
            if (coord[0] < i + 0.8 && coord[0] > i + 0.2 && coord[1] < quantiles[i][3] && coord[1] > quantiles[i][1]) {
                tooltip = String.format(format, quantiles[i][2], quantiles[i][1], quantiles[i][3]);
                break;
            }
        }
        
        return Optional.ofNullable(tooltip);
    }

    @Override
    public double[] getLowerBound() {
        double[] bound = {0, MathEx.min(data)};
        return bound;
    }

    @Override
    public double[] getUpperBound() {
        double[] bound = {data.length, MathEx.max(data)};
        return bound;
    }

    @Override
    public void paint(Graphics g) {
        g.setColor(color);

        double[] start = new double[2];
        double[] end = new double[2];
        for (int i = 0; i < data.length; i++) {
            start[0] = i + 0.4;
            start[1] = quantiles[i][0];
            end[0] = i + 0.6;
            end[1] = quantiles[i][0];
            g.drawLine(start, end);

            start[0] = i + 0.4;
            start[1] = quantiles[i][4];
            end[0] = i + 0.6;
            end[1] = quantiles[i][4];
            g.drawLine(start, end);

            start[0] = i + 0.2;
            start[1] = quantiles[i][2];
            end[0] = i + 0.8;
            end[1] = quantiles[i][2];
            g.drawLine(start, end);

            start[0] = i + 0.5;
            start[1] = quantiles[i][0];
            end[0] = i + 0.5;
            end[1] = quantiles[i][1];
            g.drawLine(start, end);

            start[0] = i + 0.5;
            start[1] = quantiles[i][4];
            end[0] = i + 0.5;
            end[1] = quantiles[i][3];
            g.drawLine(start, end);

            start[0] = i + 0.2;
            start[1] = quantiles[i][3];
            end[0] = i + 0.8;
            end[1] = quantiles[i][1];
            g.drawRect(start, end);

            start[0] = i + 0.5;
            for (int j = 0; j < data[i].length; j++) {
                if (data[i][j] < quantiles[i][6] || data[i][j] > quantiles[i][7]) {
                    start[1] = data[i][j];
                    g.drawPoint('o', start);
                }
            }
        }
    }

    @Override
    public Canvas canvas() {
        double[] lowerBound = getLowerBound();
        double[] upperBound = getUpperBound();

        Canvas canvas = new Canvas(lowerBound, upperBound);
        canvas.add(this);
        canvas.getAxis(0).setGridVisible(false);

        if (labels != null) {
            int k = labels.length;
            double[] locations = new double[k];
            for (int i = 0; i < k; i++) {
                locations[i] = i + 0.5;
            }

            canvas.getAxis(0).setTicks(labels, locations);
            if (k > 10) {
                canvas.getAxis(0).setRotation(-Math.PI / 2);
            }
        } else {
            canvas.getAxis(0).setTickVisible(false);
        }

        return canvas;
    }

    /**
     * Create a plot canvas with multiple box plots of given data.
     * @param data a data matrix of which each row will create a box plot.
     */
    public static BoxPlot of(double[]... data) {
        return new BoxPlot(data, null);
    }
}
