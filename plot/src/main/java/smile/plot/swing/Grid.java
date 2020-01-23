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
     * If true, draw the nodes as a circle.
     */
    private boolean drawNodes = false;

    /**
     * Constructor.
     * @param data an m x n x 2 array which are coordinates of m x n grid.
     */
    public Grid(double[][][] data) {
        this(data, false);
    }

    /**
     * Constructor.
     * @param data an m x n x 2 array which are coordinates of m x n grid.
     */
    public Grid(double[][][] data, boolean drawNodes) {
        this(data, drawNodes, Color.BLACK);
    }

    /**
     * Constructor.
     * @param data an m x n x 2 array which are coordinates of m x n grid.
     */
    public Grid(double[][][] data, boolean drawNodes, Color color) {
        super(color);
        this.data = data;
        this.drawNodes = drawNodes;
    }

    /**
     * Gets if draw the nodes.
     */
    public boolean setDrawNodes() {
        return drawNodes;
    }

    /**
     * Sets if draw the nodes.
     */
    public void setDrawNodes(boolean drawNodes) {
        this.drawNodes = drawNodes;
    }

    @Override
    public void paint(Graphics g) {
        Color c = g.getColor();
        g.setColor(getColor());

        for (int i = 0; i < data.length; i++) {
            for (int j = 0; j < data[i].length - 1; j++) {
                if (drawNodes) g.drawPoint('o', data[i][j]);
                g.drawLine(data[i][j], data[i][j+1]);
            }
            if (drawNodes) g.drawPoint('o', data[i][data[i].length - 1]);
        }

        for (int i = 0; i < data.length - 1; i++) {
            for (int j = 0; j < data[i].length; j++) {
                g.drawLine(data[i][j], data[i+1][j]);
            }
        }
        
        g.setColor(c);
    }
    
    /**
     * Create a 2D grid plot canvas.
     * @param data an m x n x 2 array which are coordinates of m x n grid.
     */
    public static PlotCanvas plot(double[][][] data) {
        return plot(null, data);
    }
    
    /**
     * Create a 2D grid plot canvas.
     * @param id the id of the plot.
     * @param data an m x n x 2 array which are coordinates of m x n grid.
     */
    public static PlotCanvas plot(String id, double[][][] data) {
        double[] lowerBound = {data[0][0][0], data[0][0][1]};
        double[] upperBound = {data[0][0][0], data[0][0][1]};
        for (int i = 0; i < data.length; i++) {
            for (int j = 0; j < data[i].length; j++) {
                if (data[i][j][0] < lowerBound[0]) {
                    lowerBound[0] = data[i][j][0];
                }
                if (data[i][j][0] > upperBound[0]) {
                    upperBound[0] = data[i][j][0];
                }
                if (data[i][j][1] < lowerBound[1]) {
                    lowerBound[1] = data[i][j][1];
                }
                if (data[i][j][1] > upperBound[1]) {
                    upperBound[1] = data[i][j][1];
                }
            }
        }

        PlotCanvas canvas = new PlotCanvas(lowerBound, upperBound);

        Grid grid = new Grid(data);
        if (id != null) grid.setID(id);
        canvas.add(grid);

        return canvas;
    }
}
