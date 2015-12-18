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

import smile.plot.Line;
import smile.plot.LinePlot;
import smile.plot.PlotCanvas;

/**
 *
 * @author Haifeng Li
 */
@SuppressWarnings("serial")
public class LinePlotDemo extends JPanel {
    public LinePlotDemo() {
        super(new GridLayout(1,2));

        double[][] data = new double[100][2];
        for (int j = 0; j < data.length; j++) {
            data[j][0] = 2 * Math.PI * (double) j / data.length;
            data[j][1] = Math.sin(data[j][0]);
        }
        PlotCanvas canvas = LinePlot.plot("Line 1", data, Line.Style.DASH, Color.RED);
        canvas.setTitle("2D Lines");
        add(canvas);

        data = new double[100][2];
        for (int j = 0; j < data.length; j++) {
            data[j][0] = 2 * Math.PI * (double) j / data.length;
            data[j][1] = Math.cos(data[j][0]);
        }
        LinePlot plot = canvas.line(data, Line.Style.DOT_DASH, Color.BLUE);
        plot.setID("Line 2");

        data = new double[100][2];
        for (int j = 0; j < data.length; j++) {
            data[j][0] = 2 * Math.PI * (double) j / data.length;
            data[j][1] = Math.sin(data[j][0]) + Math.cos(data[j][0]);
        }
        plot = canvas.line(data, Line.Style.LONG_DASH, Color.GREEN);
        plot.setID("Line 3");

        data = new double[100][3];
        for (int j = 0; j < data.length; j++) {
            data[j][0] = 2 * Math.PI * (double) j / data.length;
            data[j][1] = Math.sin(data[j][0]);
            data[j][2] = Math.sin(data[j][0]) * Math.cos(data[j][1]);
        }
        PlotCanvas canvas3d = LinePlot.plot(data, Line.Style.SOLID);
        canvas3d.setTitle("3D Line");
        add(canvas3d);
    }

    @Override
    public String toString() {
        return "Line Plot";
    }

    public static void main(String[] args) {
        JFrame frame = new JFrame("Line Plot");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLocationRelativeTo(null);
        frame.getContentPane().add(new LinePlotDemo());
        frame.setVisible(true);
    }
}
