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
import java.util.Optional;
import smile.math.MathEx;

/**
 * A barplot draws bars with heights proportional to the value.
 * 
 * @author Haifeng Li
 */
public class BarPlot extends Plot {

    /**
     * The input data.
     */
    double[][] data;
    /**
     * The description of each data point.
     */
    String[] description;
    /**
     * The width of bar.
     */
    double width;
    /**
     * The left top coordinates of bar.
     */
    double[][] leftTop;
    /**
     * The right top coordinates of bar.
     */
    double[][] rightTop;
    /**
     * The left bottom coordinates of bar.
     */
    double[][] leftBottom;
    /**
     * The right bottom coordinates of bar.
     */
    double[][] rightBottom;

    /**
     * Constructor.
     */
    public BarPlot(int[] data) {
        super(Color.BLUE);

        this.data = new double[data.length][2];
        for (int i = 0; i < data.length; i++) {
            this.data[i][0] = i + 0.5;
            this.data[i][1] = data[i];
        }

        init();
    }

    /**
     * Constructor.
     */
    public BarPlot(double[] data) {
        super(Color.BLUE);

        this.data = new double[data.length][2];
        for (int i = 0; i < data.length; i++) {
            this.data[i][0] = i + 0.5;
            this.data[i][1] = data[i];
        }

        init();
    }

    /**
     * Constructor.
     */
    public BarPlot(String[] description, double[] data) {
        super(Color.BLUE);

        if (data.length != description.length) {
            throw new IllegalArgumentException("Data size and label size don't match.");
        }

        this.description = description;
        this.data = new double[data.length][2];
        for (int i = 0; i < data.length; i++) {
            this.data[i][0] = i + 0.5;
            this.data[i][1] = data[i];
        }

        init();
    }

    /**
     * Constructor.
     */
    public BarPlot(int[][] data) {
        super(Color.BLUE);

        if (data[0].length != 2) {
            throw new IllegalArgumentException("Invalid data dimension: " + data[0].length);
        }

        this.data = new double[data.length][2];
        for (int i = 0; i < data.length; i++) {
            this.data[i][0] = data[i][0];
            this.data[i][1] = data[i][1];
        }

        init();
    }

    /**
     * Constructor.
     */
    public BarPlot(double[][] data) {
        super(Color.BLUE);

        if (data[0].length != 2) {
            throw new IllegalArgumentException("Dataset is not 2-dimensional.");
        }

        this.data = data;

        init();
    }

    /**
     * Calculate bar width and position.
     */
    private void init() {
        width = Double.MAX_VALUE;
        for (int i = 1; i < data.length; i++) {
            double w = Math.abs(data[i][0] - data[i - 1][0]);
            if (width > w) {
                width = w;
            }
        }

        leftTop = new double[data.length][2];
        rightTop = new double[data.length][2];
        leftBottom = new double[data.length][2];
        rightBottom = new double[data.length][2];

        for (int i = 0; i < data.length; i++) {
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

    /**
     * Returns the width of bar.
     */
    public double getWidth() {
        return width;
    }

    @Override
    public Optional<String> getToolTip(double[] coord) {
        String tooltip = null;
        boolean inBar = false;
        for (int i = 0; i < data.length; i++) {
            if (rightTop[i][1] > rightBottom[i][1]) {
                if (coord[0] < rightBottom[i][0] && coord[0] > leftBottom[i][0] && coord[1] < rightTop[i][1] && coord[1] > rightBottom[i][1]) {
                    inBar = true;
                }
            } else {
                if (coord[0] < rightBottom[i][0] && coord[0] > leftBottom[i][0] && coord[1] > rightTop[i][1] && coord[1] < rightBottom[i][1]) {
                    inBar = true;
                }
            }

            if (inBar) {
                if (description == null) {
                    tooltip = String.format("data[%d] = %G", i, data[i][1]);
                } else {
                    tooltip = String.format("%s = %G", description[i], data[i][1]);
                }
                break;
            }
        }
        
        return Optional.ofNullable(tooltip);
    }
    
    @Override
    public void paint(Graphics g) {
        Color c = g.getColor();
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
        g.setColor(c);
    }

    @Override
    public double[] getLowerBound() {
        return MathEx.colMin(data);
    }

    @Override
    public double[] getUpperBound() {
        return MathEx.colMax(data);
    }
}
