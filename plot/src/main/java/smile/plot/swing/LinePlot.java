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

import smile.math.MathEx;

/**
 * Line plot is a special scatter plot which connects points by straight lines.
 *
 * @author Haifeng Li
 */
public class LinePlot extends Plot {
    /**
     * The poly line of points.
     */
    final Line[] lines;

    /**
     * Constructor.
     */
    public LinePlot(Line... lines) {
        this.lines = lines;
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
     * Creates a line plot with the index as the x coordinate.
     * @param y the data vector of y coordinates.
     *          The x coordinates will be [0, n), where n is the length of y.
     */
    public static LinePlot of(double[] y) {
        return of(Line.zipWithIndex(y));
    }
}
