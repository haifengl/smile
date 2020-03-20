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
 * One more more points in the plot.
 *
 * @author Haifeng Li
 */
public class Point extends NamedShape {

    /** The marks of point. */
    public static char[] MARKS = {'.', '+', '-', '|', '*', 'x', 'o', 'O', '@', '#', 's', 'S', 'q', 'Q'};

    /**
     * The coordinate of points.
     */
    final double[][] points;
    /**
     * The mark of points.
     */
    final char mark;

    /**
     * Constructor.
     * @param points a n-by-2 or n-by-3 matrix that are the coordinates of points.
     * @param mark the mark of points.
     * <ul>
     * <li> . : dot
     * <li> + : +
     * <li> - : -
     * <li> | : |
     * <li> * : star
     * <li> x : x
     * <li> o : circle
     * <li> O : large circle
     * <li> @ : solid circle
     * <li> # : large solid circle
     * <li> s : square
     * <li> S : large square
     * <li> q : solid square
     * <li> Q : large solid square
     * <li> others : dot
     * </ul>
     * @param color the color of points.
     * @param name the optional name.
     */
    public Point(double[][] points, char mark, Color color, String name) {
        super(name, color);
        this.points = points;
        this.mark = mark;
    }

    @Override
    public void paint(Graphics g) {
        Color c = g.getColor();
        g.setColor(color);

        for (double[] point : points) {
            g.drawPoint(mark, point);
        }

        g.setColor(c);
    }

    /**
     * Creates a Point with circle mark and black color.
     */
    public static Point of (double[][] points) {
        return new Point(points, 'o', Color.BLACK, null);
    }
}
