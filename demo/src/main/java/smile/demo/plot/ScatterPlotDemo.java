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

package smile.demo.plot;

import java.awt.Color;
import java.awt.GridLayout;
import javax.swing.JFrame;
import javax.swing.JPanel;

import smile.plot.swing.PlotCanvas;
import smile.plot.swing.ScatterPlot;

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
        frame.setSize(1000, 1000);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLocationRelativeTo(null);
        frame.getContentPane().add(new ScatterPlotDemo());
        frame.setVisible(true);
    }
}
