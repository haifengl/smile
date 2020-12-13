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
 * Staircase plot is a special case of line which is most useful to display
 * empirical distribution.
 *
 * @author Haifeng Li
 */
public class StaircasePlot extends Plot {

    /**
     * The set of lines which may have different stroke, marks, and/or colors.
     */
    final Staircase[] lines;
    /**
     * The legends of each line.
     */
    final Optional<Legend[]> legends;

    /**
     * Constructor.
     */
    public StaircasePlot(Staircase... lines) {
        this.lines = lines;
        legends = Optional.empty();
    }

    /**
     * Constructor.
     */
    public StaircasePlot(Staircase[] lines, Legend[] legends) {
        this.lines = lines;
        this.legends = Optional.of(legends);
    }

    @Override
    public Canvas canvas() {
        Canvas canvas = new Canvas(getLowerBound(), getUpperBound(), false);
        canvas.base.extendBound(1);
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
        for (Staircase line : lines) {
            line.paint(g);
        }
    }

    /**
     * Creates a line plot.
     */
    public static StaircasePlot of(double[][] data) {
        return new StaircasePlot(Staircase.of(data));
    }


    /**
     * Creates a line plot.
     */
    public static StaircasePlot of(double[][] data, Color color, String label) {
        Staircase[] line = {new Staircase(data, color)};
        Legend[] legend = {new Legend(label, color)};
        return new StaircasePlot(line, legend);
    }
}
