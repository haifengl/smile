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
 * A boxplot is a convenient way of graphically depicting groups of numerical
 * data through their five-number summaries (the smallest observation
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
     * The input data. Each row is a variable.
     */
    private double[][] data;
    /**
     * The description of each variable.
     */
    private String[] description;
    /**
     * The quantiles of data.
     */
    private double[][] quantiles;

    /**
     * Constructor.
     */
    public BoxPlot(double[] data) {
        this.data = new double[1][];
        this.data[0] = data;
        init();
    }

    /**
     * Constructor.
     * @param data the input dataset of which each row is a set of samples
     * and will have a corresponding box plot.
     */
    public BoxPlot(double[][] data) {
        this.data = data;
        init();
    }

    /**
     * Constructor.
     * @param data the input dataset of which each row is a set of samples
     * and will have a corresponding box plot.
     */
    public BoxPlot(String[] description, double[][] data) {
        if (description.length != data.length) {
            throw new IllegalArgumentException("Data size and label size don't match.");
        }
        
        this.description = description;
        this.data = data;
        init();
    }

    /**
     * Calculate quantiles.
     */
    private void init() {
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

    /**
     * Tooltip format string.
     */
    private static String format = "<table border=\"1\"><tr><td>Median</td><td align=\"right\">%g</td></tr><tr><td>Q1</td><td align=\"right\">%g</td></tr><tr><td>Q3</td><td align=\"right\">%g</td></tr></table>";

    @Override
    public String getToolTip(double[] coord) {
        for (int i = 0; i < data.length; i++) {
            if (coord[0] < i + 0.8 && coord[0] > i + 0.2 && coord[1] < quantiles[i][3] && coord[1] > quantiles[i][1]) {
                if (description != null) {
                    return "<b>&nbsp;" + description[i] + ":</b></br>" + String.format(format, quantiles[i][2], quantiles[i][1], quantiles[i][3]);
                } else {
                    return String.format(format, quantiles[i][2], quantiles[i][1], quantiles[i][3]);
                }
            }
        }
        
        return null;        
    }
    
    @Override
    public void paint(Graphics g) {
        Color c = g.getColor();
        g.setColor(getColor());

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

        g.setColor(c);
    }

    /**
     * Create a plot canvas with the box plot of given data.
     * @param data a sample set.
     */
    public static PlotCanvas plot(double[] data) {
        double[] lowerBound = {0, Math.min(data)};
        double[] upperBound = {1, Math.max(data)};

        PlotCanvas canvas = new PlotCanvas(lowerBound, upperBound);
        canvas.add(new BoxPlot(data));

        canvas.getAxis(0).setGridVisible(false);
        canvas.getAxis(0).setLabelVisible(false);

        return canvas;
    }

    /**
     * Create a plot canvas with multiple box plots of given data.
     * @param data a data matrix of which each row will create a box plot.
     */
    public static PlotCanvas plot(double[]... data) {
        double[] lowerBound = {0, Math.min(data)};
        double[] upperBound = {data.length, Math.max(data)};

        PlotCanvas canvas = new PlotCanvas(lowerBound, upperBound);
        canvas.add(new BoxPlot(data));

        canvas.getAxis(0).setGridVisible(false);
        canvas.getAxis(0).setLabelVisible(false);

        return canvas;
    }

    /**
     * Create a plot canvas with multiple box plots of given data.
     * @param data a data matrix of which each row will create a box plot.
     * @param labels the labels for each box plot.
     */
    public static PlotCanvas plot(double[][] data, String[] labels) {
        if (data.length != labels.length) {
            throw new IllegalArgumentException("Data size and label size don't match.");
        }

        double[] lowerBound = {0, Math.min(data)};
        double[] upperBound = {data.length, Math.max(data)};

        PlotCanvas canvas = new PlotCanvas(lowerBound, upperBound);
        canvas.add(new BoxPlot(labels, data));

        double[] locations = new double[labels.length];
        for (int i = 0; i < labels.length; i++) {
            locations[i] = i + 0.5;
        }

        canvas.getAxis(0).addLabel(labels, locations);
        canvas.getAxis(0).setGridVisible(false);
        
        if (labels.length > 10) {
            canvas.getAxis(0).setRotation(-Math.PI / 2);
        }

        return canvas;
    }
}
