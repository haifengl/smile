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

package smile.demo.plot;

import java.awt.Color;
import java.awt.GridLayout;

import javax.swing.JFrame;
import javax.swing.JPanel;

import smile.plot.PlotCanvas;
import smile.plot.ScatterPlot;

/**
 *
 * @author Haifeng Li
 */
@SuppressWarnings("serial")
public class ScatterPlotDemo extends JPanel {
    public ScatterPlotDemo() {
        super(new GridLayout(1,2));
        
        double[][] data = new double[10][2];
        for (int j = 0; j < data.length; j++) {
            data[j][0] = 10000 + Math.random();
            data[j][1] = Math.random();
        }

        PlotCanvas canvas = ScatterPlot.plot(data);
        canvas.setTitle("2D Scatter Plot");
        canvas.setAxisLabels("X Axis", "A Long Label");
        add(canvas);

        data = new double[10][2];
        for (int j = 0; j < data.length; j++) {
            data[j][0] = 10001 + Math.random();
            data[j][1] = 1+Math.random();
        }
        canvas.points(data, '*', Color.RED);

        data = new double[10][2];
        for (int j = 0; j < data.length; j++) {
            data[j][0] = 10002 + Math.random();
            data[j][1] = Math.random();
        }
        canvas.points(data, '+', Color.BLUE);

        data = new double[10][2];
        for (int j = 0; j < data.length; j++) {
            data[j][0] = 10000 + Math.random();
            data[j][1] = 2+Math.random();
        }
        canvas.points(data, '-', Color.GREEN);

        data = new double[10][2];
        for (int j = 0; j < data.length; j++) {
            data[j][0] = 10001 + Math.random();
            data[j][1] = Math.random();
        }
        canvas.points(data, 'x', Color.CYAN);

        data = new double[10][2];
        for (int j = 0; j < data.length; j++) {
            data[j][0] = 10000 + Math.random();
            data[j][1] = 1+Math.random();
        }
        canvas.points(data, '@', Color.MAGENTA);

        data = new double[10][2];
        for (int j = 0; j < data.length; j++) {
            data[j][0] = 10002 + Math.random();
            data[j][1] = Math.random();
        }
        canvas.points(data, 's', Color.ORANGE);

        data = new double[10][2];
        for (int j = 0; j < data.length; j++) {
            data[j][0] = 10000 + Math.random();
            data[j][1] = 2+Math.random();
        }
        canvas.points(data, 'S', Color.PINK);

        data = new double[10][2];
        for (int j = 0; j < data.length; j++) {
            data[j][0] = 10002+Math.random();
            data[j][1] = 2+Math.random();
        }
        canvas.points(data, 'a', Color.YELLOW);

        data = new double[10][2];
        for (int j = 0; j < data.length; j++) {
            data[j][0] = 10001.5+Math.random();
            data[j][1] = 1.5+Math.random();
        }
        canvas.points(data, 'A', Color.DARK_GRAY);

        data = new double[100][2];
        for (int j = 0; j < data.length; j++) {
            data[j][0] = 10000 + 3*Math.random();
            data[j][1] = 3*Math.random();
        }
        canvas.points(data, '.', Color.BLACK);

        data = new double[100][3];
        for (int j = 0; j < data.length; j++) {
            data[j][0] = Math.random();
            data[j][1] = Math.random();
            data[j][2] = Math.random();
        }

        PlotCanvas canvas3d = ScatterPlot.plot(data);
        canvas3d.setTitle("3D Scatter Plot");
        add(canvas3d);
    }

    @Override
    public String toString() {
        return "Scatter Plot";
    }

    public static void main(String[] args) {
        JFrame frame = new JFrame("Staircase Plot");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLocationRelativeTo(null);
        frame.getContentPane().add(new ScatterPlotDemo());
        frame.setVisible(true);
    }
}
