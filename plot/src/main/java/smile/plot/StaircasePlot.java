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
import smile.math.Math;

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
    public StaircasePlot(double[][] data) {
        this.data = data;
    }

    /**
     * Constructor.
     */
    public StaircasePlot(double[][] data, Color color) {
        super(color);
        this.data = data;
    }

    @Override
    public void paint(Graphics g) {
        Color c = g.getColor();
		g.setColor(getColor());

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
     * Create a plot canvas with the staircase line plot of given data.
     * @param data a n x 2 or n x 3 matrix that describes coordinates of points.
     */
    public static PlotCanvas plot(double[]... data) {
        return plot(null, data);
    }

    /**
     * Create a plot canvas with the staircase line plot of given data.
     * @param id the id of the plot.
     * @param data a n x 2 or n x 3 matrix that describes coordinates of points.
     */
    public static PlotCanvas plot(String id, double[]... data) {
        if (data[0].length != 2 && data[0].length != 3) {
            throw new IllegalArgumentException("Invalid data dimension: " + data[0].length);
        }

        double[] lowerBound = Math.colMin(data);
        double[] upperBound = Math.colMax(data);
        PlotCanvas canvas = new PlotCanvas(lowerBound, upperBound);

        StaircasePlot plot = new StaircasePlot(data);
        plot.setID(id);
        canvas.add(plot);
        return canvas;
    }
}