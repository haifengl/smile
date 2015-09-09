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
     * Constructor.
     * @param data an m x n x 2 array which are coordinates of m x n grid.
     */
    public Grid(double[][][] data) {
        this(data, Color.BLACK);
    }

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
        Color c = g.getColor();
        g.setColor(getColor());

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
        
        g.setColor(c);
    }
    
    /**
     * Create a 2D grid plot canvas.
     * @param data an m x n x 2 array which are coordinates of m x n grid.
     */
    public static PlotCanvas plot(double[][][] data) {
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
        canvas.add(grid);

        return canvas;
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
        grid.setID(id);
        canvas.add(grid);

        return canvas;
    }
}