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

/**
 * A single point in the plot.
 *
 * @author Haifeng Li
 */
public class Point extends Shape {

    /**
     * The coordinate of point.
     */
    double[] point;
    /**
     * The legend of points.
     */
    private char legend;

    /**
     * Constructor.
     */
    public Point(double... point) {
        this('o', point);
    }

    /**
     * Constructor.
     */
    public Point(char legend, double... point) {
        this(legend, Color.BLACK, point);
    }

    /**
     * Constructor.
     */
    public Point(Color color, double... point) {
        this('o', color, point);
    }

    /**
     * Constructor.
     */
    public Point(char legend, Color color, double... point) {
        super(color);
        this.legend = legend;
        this.point = point;
    }

    /**
     * Returns the legend of point.
     */
    public char getLegend() {
        return legend;
    }

    /**
     * Set the legend of point.
     */
    public Point setLegend(char legend) {
        this.legend = legend;
        return this;
    }

    @Override
    public void paint(Graphics g) {
        Color c = g.getColor();
        g.setColor(getColor());

        g.drawPoint(legend, point);
        g.setColor(c);
    }
}
