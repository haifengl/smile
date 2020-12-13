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
 * A barplot draws bars with heights proportional to the value.
 * 
 * @author Haifeng Li
 */
public class BarPlot extends Plot {
    /**
     * The bar groups which may have different colors.
     */
    final Bar[] bars;
    /**
     * The legends of each bar group.
     */
    final Optional<Legend[]> legends;

    /**
     * Constructor.
     */
    public BarPlot(Bar... bars) {
        this.bars = bars;
        legends = Optional.empty();
    }

    /**
     * Constructor.
     */
    public BarPlot(Bar[] bars, Legend[] legends) {
        this.bars = bars;
        this.legends = Optional.of(legends);
    }

    @Override
    public void paint(Graphics g) {
        for (Bar bar : bars) {
            bar.paint(g);
        }
    }

    @Override
    public double[] getLowerBound() {
        double[] bound = MathEx.colMin(bars[0].data);
        bound[0] -= bars[0].width / 2;

        for (int k = 1; k < bars.length; k++) {
            for (double[] x : bars[k].data) {
                if (bound[0] > x[0] - bars[k].width / 2) {
                    bound[0] = x[0] - bars[k].width / 2;
                }
                if (bound[1] > x[1]) {
                    bound[1] = x[1];
                }
            }
        }

        return bound;
    }

    @Override
    public double[] getUpperBound() {
        double[] bound = MathEx.colMax(bars[0].data);
        bound[0] += bars[0].width / 2;

        for (int k = 1; k < bars.length; k++) {
            for (double[] x : bars[k].data) {
                if (bound[0] > x[0] + bars[k].width / 2) {
                    bound[0] = x[0] + bars[k].width / 2;
                }
                if (bound[1] > x[1]) {
                    bound[1] = x[1];
                }
            }
        }

        return bound;
    }

    @Override
    public Optional<Legend[]> legends() {
        return legends;
    }

    @Override
    public Canvas canvas() {
        Canvas canvas = new Canvas(getLowerBound(), getUpperBound());
        canvas.add(this);
        canvas.getAxis(0).setGridVisible(false);
        return canvas;
    }

    /**
     * Creates a bar plot.
     */
    public static BarPlot of(double[] data) {
        return new BarPlot(Bar.of(data));
    }

    /**
     * Creates a bar plot.
     */
    public static BarPlot of(int[] data) {
        return new BarPlot(Bar.of(data));
    }

    /**
     * Creates a bar plot of multiple groups/colors.
     * @param data each row is a data set of bars (bar height).
     * @param labels the group label of data points.
     */
    public static BarPlot of(double[][] data, String[] labels) {
        if (data.length != labels.length) {
            throw new IllegalArgumentException("The number of data groups and that of labels are not the same.");
        }

        int n = data.length;
        double width = 0.5 / n;
        Bar[] bars = new Bar[n];
        Legend[] legends = new Legend[n];
        for (int i = 0; i < n; i++) {
            double[][] x = new double[data[i].length][2];
            for (int j = 0; j < x.length; j++) {
                x[j][0] = j + (i+1) * width;
                x[j][1] = data[i][j];
            }

            Color color = Palette.COLORS[i];
            bars[i] = new Bar(x, width, color);
            legends[i] = new Legend(labels[i], color);
        }

        return new BarPlot(bars, legends);
    }
}
