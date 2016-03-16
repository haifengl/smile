/*******************************************************************************
 * Copyright (c) 2010 Haifeng Li
 *   
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *  
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *******************************************************************************/

package smile.plot;

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