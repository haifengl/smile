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
import java.util.Arrays;
import smile.math.MathEx;
import smile.stat.distribution.Distribution;
import smile.stat.distribution.DiscreteDistribution;
import smile.stat.distribution.GaussianDistribution;

/**
 * A Q-Q plot ("Q" stands for quantile) is a probability plot, a kind of
 * graphical method for comparing two probability distributions, by
 * plotting their quantiles against each other. In addition, Q-Q plots
 * can be used as a graphical means of estimating parameters in a
 * location-scale family of distributions.

 * @author Haifeng Li
 */
public class QQPlot extends Plot {

    /**
     * The coordinates of points.
     */
    private double[][] data;

    /**
     * Constructor of one sample Q-Q plot to standard normal distribution.
     */
    public QQPlot(double[] x) {
        data = quantile(x, GaussianDistribution.getInstance());
    }

    /**
     * Constructor of one sample Q-Q plot to given distribution.
     */
    public QQPlot(double[] x, Distribution d) {
        data = quantile(x, d);
    }

    /**
     * Constructor of one sample Q-Q plot to given distribution.
     */
    public QQPlot(int[] x, DiscreteDistribution d) {
        data = quantile(x, d);
    }

    /**
     * Constructor of two sample Q-Q plot.
     */
    public QQPlot(double[] x, double[] y) {
        data = quantile(x, y);
    }

    /**
     * Constructor of two sample Q-Q plot.
     */
    public QQPlot(int[] x, int[] y) {
        data = quantile(x, y);
    }

    /**
     * Generate the quantile-quantile pairs.
     */
    private static double[][] quantile(double[] x, double[] y) {
        Arrays.sort(x);
        Arrays.sort(y);

        int n = Math.min(x.length, y.length);

        double[][] q = new double[n][2];
        for (int i = 0; i < n; i++) {
            double p = (i + 1) / (n + 1.0);
            q[i][0] = x[(int) Math.round(p * x.length)];
            q[i][1] = y[(int) Math.round(p * y.length)];
        }

        return q;
    }

    /**
     * Generate the quantile-quantile pairs.
     */
    private static double[][] quantile(int[] x, int[] y) {
        Arrays.sort(x);
        Arrays.sort(y);

        int n = Math.min(x.length, y.length);

        double[][] q = new double[n][2];
        for (int i = 0; i < n; i++) {
            double p = (i + 1) / (n + 1.0);
            q[i][0] = x[(int) Math.round(p * x.length)];
            q[i][1] = y[(int) Math.round(p * y.length)];
        }

        return q;
    }

    /**
     * Generate the quantile-quantile pairs.
     */
    private static double[][] quantile(double[] x, Distribution d) {
        Arrays.sort(x);

        int n = x.length;
        double[][] q = new double[n][2];
        for (int i = 0; i < n; i++) {
            double p = (i + 1) / (n + 1.0);
            q[i][0] = x[(int) Math.round(p * x.length)];
            q[i][1] = d.quantile(p);
        }

        return q;
    }

    /**
     * Generate the quantile-quantile pairs.
     */
    private static double[][] quantile(int[] x, DiscreteDistribution d) {
        Arrays.sort(x);

        int n = x.length;
        double[][] q = new double[n][2];
        for (int i = 0; i < n; i++) {
            double p = (i + 1) / (n + 1.0);
            q[i][0] = x[(int) Math.round(p * x.length)];
            q[i][1] = d.quantile(p);
        }

        return q;
    }

    @Override
    public void paint(Graphics g) {
        Color c = g.getColor();
        g.setColor(getColor());

        double[] lowerEnd = g.getLowerBound();
        lowerEnd[0] = Math.min(lowerEnd[0], lowerEnd[1]);
        lowerEnd[1] = lowerEnd[0];

        double[] upperEnd = g.getUpperBound();
        upperEnd[0] = Math.max(upperEnd[0], upperEnd[1]);
        upperEnd[1] = upperEnd[0];
        g.drawLine(lowerEnd, upperEnd);

        for (int i = 0; i < data.length; i++) {
            g.drawPoint('o', data[i]);
        }

        g.setColor(c);
    }
    
    /**
     * Create a plot canvas with the one sample Q-Q plot to standard normal
     * distribution. The x-axis is the quantiles of x and the y-axis is the
     * quantiles of normal distribution.
     * @param x a sample set.
     */
    public static PlotCanvas plot(double[] x) {
        double[] lowerBound = {MathEx.min(x), GaussianDistribution.getInstance().quantile(1 / (x.length + 1.0))};
        double[] upperBound = {MathEx.max(x), GaussianDistribution.getInstance().quantile(x.length / (x.length + 1.0))};
        PlotCanvas canvas = new PlotCanvas(lowerBound, upperBound);
        canvas.add(new QQPlot(x));
        return canvas;
    }

    /**
     * Create a plot canvas with the one sample Q-Q plot to given distribution.
     * The x-axis is the quantiles of x and the y-axis is the quantiles of
     * given distribution.
     * @param x a sample set.
     * @param d a distribution.
     */
    public static PlotCanvas plot(double[] x, Distribution d) {
        double[] lowerBound = {MathEx.min(x), d.quantile(1 / (x.length + 1.0))};
        double[] upperBound = {MathEx.max(x), d.quantile(x.length / (x.length + 1.0))};
        PlotCanvas canvas = new PlotCanvas(lowerBound, upperBound);
        canvas.add(new QQPlot(x, d));
        return canvas;
    }

    /**
     * Create a plot canvas with the two sample Q-Q plot.
     * The x-axis is the quantiles of x and the y-axis is the quantiles of y.
     * @param x a sample set.
     * @param y a sample set.
     */
    public static PlotCanvas plot(double[] x, double[] y) {
        double[] lowerBound = {MathEx.min(x), MathEx.min(y)};
        double[] upperBound = {MathEx.max(x), MathEx.max(y)};
        PlotCanvas canvas = new PlotCanvas(lowerBound, upperBound);
        canvas.add(new QQPlot(x, y));
        return canvas;
    }

    /**
     * Create a plot canvas with the one sample Q-Q plot to given distribution.
     * The x-axis is the quantiles of x and the y-axis is the quantiles of
     * given distribution.
     * @param x a sample set.
     * @param d a distribution.
     */
    public static PlotCanvas plot(int[] x, DiscreteDistribution d) {
        double[] lowerBound = {MathEx.min(x), d.quantile(1 / (x.length + 1.0))};
        double[] upperBound = {MathEx.max(x), d.quantile(x.length / (x.length + 1.0))};
        PlotCanvas canvas = new PlotCanvas(lowerBound, upperBound);
        canvas.add(new QQPlot(x, d));
        return canvas;
    }

    /**
     * Create a plot canvas with the two sample Q-Q plot.
     * The x-axis is the quantiles of x and the y-axis is the quantiles of y.
     * @param x a sample set.
     * @param y a sample set.
     */
    public static PlotCanvas plot(int[] x, int[] y) {
        double[] lowerBound = {MathEx.min(x), MathEx.min(y)};
        double[] upperBound = {MathEx.max(x), MathEx.max(y)};
        PlotCanvas canvas = new PlotCanvas(lowerBound, upperBound);
        canvas.add(new QQPlot(x, y));
        return canvas;
    }
}
