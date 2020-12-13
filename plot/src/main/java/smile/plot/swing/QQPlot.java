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
 *
 * @author Haifeng Li
 */
public class QQPlot extends Plot {

    /**
     * The coordinates of points.
     */
    private double[][] points;

    /**
     * Constructor.
     * @param points the points in the plot. A point (x, y) on the plot
     *               corresponds to one of the quantiles of the second
     *               distribution (y-coordinate) plotted against the
     *               same quantile of the first distribution (x-coordinate).
     */
    public QQPlot(double[][] points) {
        this.points = points;
    }

    @Override
    public void paint(Graphics g) {
        g.setColor(color);

        double[] lowerEnd = g.getLowerBound();
        lowerEnd[0] = Math.min(lowerEnd[0], lowerEnd[1]);
        lowerEnd[1] = lowerEnd[0];

        double[] upperEnd = g.getUpperBound();
        upperEnd[0] = Math.max(upperEnd[0], upperEnd[1]);
        upperEnd[1] = upperEnd[0];
        g.drawLine(lowerEnd, upperEnd);

        for (double[] point : points) {
            g.drawPoint('o', point);
        }
    }

    @Override
    public double[] getLowerBound() {
        return MathEx.colMin(points);
    }

    @Override
    public double[] getUpperBound() {
        return MathEx.colMax(points);
    }

    /**
     * One sample Q-Q plot to standard normal distribution.
     */
    public static QQPlot of(double[] x) {
        return of(x, GaussianDistribution.getInstance());
    }

    /**
     * One sample Q-Q plot to given distribution.
     */
    public static QQPlot of(double[] x, Distribution d) {
        Arrays.sort(x);

        int n = x.length;
        double[][] q = new double[n][2];
        for (int i = 0; i < n; i++) {
            double p = (i + 1) / (n + 1.0);
            q[i][0] = x[(int) Math.round(p * x.length)];
            q[i][1] = d.quantile(p);
        }

        return new QQPlot(q);
    }

    /**
     * One sample Q-Q plot to given discrete distribution.
     */
    public static QQPlot of(int[] x, DiscreteDistribution d) {
        Arrays.sort(x);

        int n = x.length;
        double[][] q = new double[n][2];
        for (int i = 0; i < n; i++) {
            double p = (i + 1) / (n + 1.0);
            q[i][0] = x[(int) Math.round(p * x.length)];
            q[i][1] = d.quantile(p);
        }

        return new QQPlot(q);
    }

    /**
     * Two sample Q-Q plot.
     */
    public static QQPlot of(double[] x, double[] y) {
        Arrays.sort(x);
        Arrays.sort(y);

        int n = Math.min(x.length, y.length);

        double[][] q = new double[n][2];
        for (int i = 0; i < n; i++) {
            double p = (i + 1) / (n + 1.0);
            q[i][0] = x[(int) Math.round(p * x.length)];
            q[i][1] = y[(int) Math.round(p * y.length)];
        }

        return new QQPlot(q);
    }

    /**
     * Two sample Q-Q plot.
     */
    public static QQPlot of(int[] x, int[] y) {
        Arrays.sort(x);
        Arrays.sort(y);

        int n = Math.min(x.length, y.length);

        double[][] q = new double[n][2];
        for (int i = 0; i < n; i++) {
            double p = (i + 1) / (n + 1.0);
            q[i][0] = x[(int) Math.round(p * x.length)];
            q[i][1] = y[(int) Math.round(p * y.length)];
        }

        return new QQPlot(q);
    }
}
