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

/**
 * This class represents a poly line in the plot.
 *
 * @author Haifeng Li
 */
public class Staircase extends Shape {

    /**
     * The end points of the line.
     */
    final double[][] points;

    /**
     * Constructor.
     * @param points a n-by-2 or n-by-3 matrix that are the coordinates of points.
     * @param color the color of points.
     */
    public Staircase(double[][] points, Color color) {
        super(color);
        this.points = points;
    }

    @Override
    public void paint(Graphics g) {
        g.setColor(color);

        int n = points.length;
        int d = points[0].length;
        double[] begin = new double[d];
        double[] end = new double[d];

        for (int i = 0; i < n - 1; i++) {
            for (int j = 0; j < d; j++) {
                begin[j] = points[i][j];
                end[j] = points[i+1][j];
            }
            end[d - 1] = points[i][d - 1];
            g.drawLine(begin, end);
        }

        for (int i = 1; i < n; i++) {
            for (int j = 0; j < d; j++) {
                begin[j] = points[i][j];
                end[j] = points[i][j];
            }
            begin[d - 1] = points[i-1][d - 1];
            g.drawLine(begin, end);
        }
    }

    /**
     * Creates a Staircase with solid stroke and black color.
     */
    public static Staircase of(double[][] points) {
        return new Staircase(points, Color.BLACK);
    }
}
