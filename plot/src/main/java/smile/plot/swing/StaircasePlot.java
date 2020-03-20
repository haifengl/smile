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
import smile.math.MathEx;

/**
 * Staircase plot is a special case of line which is most useful to display
 * empirical distribution.
 *
 * @author Haifeng Li
 */
public class StaircasePlot extends Plot {

    /**
     * The coordinates of points.
     */
    private double[][] data;

    /**
     * Constructor.
     */
    public StaircasePlot(double[][] data, Color color) {
        super(color);
        this.data = data;
    }

    @Override
    public double[] getLowerBound() {
        return MathEx.colMin(data);
    }

    @Override
    public double[] getUpperBound() {
        return MathEx.colMax(data);
    }

    @Override
    public void paint(Graphics g) {
        Color c = g.getColor();
        g.setColor(color);

        double[] begin = new double[data[0].length];
        double[] end = new double[data[0].length];

        for (int i = 0; i < data.length - 1; i++) {
            for (int j = 0; j < data[0].length; j++) {
                begin[j] = data[i][j];
                end[j] = data[i+1][j];
            }
            end[end.length - 1] = data[i][end.length - 1];
            g.drawLine(begin, end);
        }

        for (int i = 1; i < data.length - 1; i++) {
            for (int j = 0; j < data[0].length; j++) {
                begin[j] = data[i][j];
                end[j] = data[i][j];
            }
            begin[end.length - 1] = data[i-1][end.length - 1];
            g.drawLine(begin, end);
        }

        g.setColor(c);
    }

    /**
     * Creates a staircase plot.
     */
    public static StaircasePlot of(double[][] data) {
        return new StaircasePlot(data, Color.BLACK);
    }
}
