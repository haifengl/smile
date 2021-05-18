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
 * A 2D grid plot.
 *
 * @author Haifeng Li
 */
public class Grid extends Plot {

    /**
     * The vertex locations of 2D grid.
     */
    private double[][][] data;

    /**
     * Constructor.
     * @param data an m x n x 2 array which are coordinates of m x n grid.
     */
    public Grid(double[][][] data, Color color) {
        super(color);
        this.data = data;
    }

    @Override
    public void paint(Graphics g) {
        g.setColor(color);

        for (int i = 0; i < data.length; i++) {
            for (int j = 0; j < data[i].length - 1; j++) {
                g.drawLine(data[i][j], data[i][j+1]);
            }
        }

        for (int i = 0; i < data.length - 1; i++) {
            for (int j = 0; j < data[i].length; j++) {
                g.drawLine(data[i][j], data[i+1][j]);
            }
        }
    }
    
    @Override
    public double[] getLowerBound() {
        double[] bound = {data[0][0][0], data[0][0][1]};
        for (int i = 0; i < data.length; i++) {
            for (int j = 0; j < data[i].length; j++) {
                if (data[i][j][0] < bound[0]) {
                    bound[0] = data[i][j][0];
                }
                if (data[i][j][1] < bound[1]) {
                    bound[1] = data[i][j][1];
                }
            }
        }

        return bound;
    }

    @Override
    public double[] getUpperBound() {
        double[] bound = {data[0][0][0], data[0][0][1]};
        for (int i = 0; i < data.length; i++) {
            for (int j = 0; j < data[i].length; j++) {
                if (data[i][j][0] > bound[0]) {
                    bound[0] = data[i][j][0];
                }
                if (data[i][j][1] > bound[1]) {
                    bound[1] = data[i][j][1];
                }
            }
        }

        return bound;
    }

    /**
     * Creates a grid with black lines.
     * @param data an m x n x 2 array which are coordinates of m x n grid.
     */
    public static Grid of(double[][][] data) {
        return new Grid(data, Color.BLACK);
    }
}
