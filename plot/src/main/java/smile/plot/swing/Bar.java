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
 * Bars with heights proportional to the value.
 *
 * @author Haifeng Li
 */
public class Bar extends Shape {

    /**
     * The bar location and height.
     */
    final double[][] data;
    /**
     * The width of bar.
     */
    final double width;
    /**
     * The left top coordinates of bar.
     */
    final double[][] leftTop;
    /**
     * The right top coordinates of bar.
     */
    final double[][] rightTop;
    /**
     * The left bottom coordinates of bar.
     */
    final double[][] leftBottom;
    /**
     * The right bottom coordinates of bar.
     */
    final double[][] rightBottom;

    /**
     * Constructor.
     * @param data n x 2 array. data[][0] is the x coordinate of bar, data[][1] is the height of bar.
     * @param width the width of bars.
     * @param color the color of bars.
     */
    public Bar(double[][] data, double width, Color color) {
        super(color);

        if (data[0].length != 2) {
            throw new IllegalArgumentException("Data is not 2-dimensional.");
        }

        this.data = data;
        this.width = width;

        int n = data.length;
        leftTop = new double[n][2];
        rightTop = new double[n][2];
        leftBottom = new double[n][2];
        rightBottom = new double[n][2];

        for (int i = 0; i < n; i++) {
            leftTop[i][0] = data[i][0] - width / 2;
            leftTop[i][1] = data[i][1];

            rightTop[i][0] = data[i][0] + width / 2;
            rightTop[i][1] = data[i][1];

            leftBottom[i][0] = data[i][0] - width / 2;
            leftBottom[i][1] = 0;

            rightBottom[i][0] = data[i][0] + width / 2;
            rightBottom[i][1] = 0;
        }
    }

    @Override
    public void paint(Graphics g) {
        g.setColor(Color.BLACK);
        for (int i = 0; i < data.length; i++) {
            g.drawLine(leftBottom[i], leftTop[i]);
            g.drawLine(leftTop[i], rightTop[i]);
            g.drawLine(rightTop[i], rightBottom[i]);
            g.drawLine(rightBottom[i], leftBottom[i]);
        }

        g.setColor(color);
        for (int i = 0; i < data.length; i++) {
            g.fillPolygon(0.2f, leftBottom[i], leftTop[i], rightTop[i], rightBottom[i]);
        }
    }

    /**
     * Creates a bar plot.
     */
    public static Bar of(int[] x) {
        double[][] data = new double[x.length][2];
        for (int i = 0; i < x.length; i++) {
            data[i][0] = i + 0.5;
            data[i][1] = x[i];
        }

        return new Bar(data, 1.0, Color.BLUE);
    }

    /**
     * Creates a bar plot.
     */
    public static Bar of(double[] x) {
        double[][] data = new double[x.length][2];
        for (int i = 0; i < x.length; i++) {
            data[i][0] = i + 0.5;
            data[i][1] = x[i];
        }

        return new Bar(data, 1.0, Color.BLUE);
    }
}
