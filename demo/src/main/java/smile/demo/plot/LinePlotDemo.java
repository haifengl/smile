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

package smile.demo.plot;

import java.awt.Color;
import java.awt.GridLayout;
import javax.swing.JFrame;
import javax.swing.JPanel;

import smile.plot.swing.Line;
import smile.plot.swing.LinePlot;
import smile.plot.swing.Canvas;

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
        Canvas canvas = LinePlot.of(data, Line.Style.DASH, Color.RED, "Line 1").canvas();
        canvas.setTitle("2D Lines");
        add(canvas.panel());

        data = new double[100][2];
        for (int j = 0; j < data.length; j++) {
            data[j][0] = 2 * Math.PI * (double) j / data.length;
            data[j][1] = Math.cos(data[j][0]);
        }
        canvas.add(LinePlot.of(data, Line.Style.DOT_DASH, Color.BLUE, "Line 2"));

        data = new double[100][2];
        for (int j = 0; j < data.length; j++) {
            data[j][0] = 2 * Math.PI * (double) j / data.length;
            data[j][1] = Math.sin(data[j][0]) + Math.cos(data[j][0]);
        }
        canvas.add(LinePlot.of(data, Line.Style.LONG_DASH, Color.GREEN, "Line 3"));

        data = new double[100][3];
        for (int j = 0; j < data.length; j++) {
            data[j][0] = 2 * Math.PI * (double) j / data.length;
            data[j][1] = Math.sin(data[j][0]);
            data[j][2] = Math.sin(data[j][0]) * Math.cos(data[j][1]);
        }
        Canvas canvas3d = LinePlot.of(data, Line.Style.SOLID).canvas();
        add(canvas3d.panel());
    }

    @Override
    public String toString() {
        return "Line Plot";
    }

    public static void main(String[] args) {
        JFrame frame = new JFrame("Line Plot");
        frame.setSize(1000, 1000);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLocationRelativeTo(null);
        frame.getContentPane().add(new LinePlotDemo());
        frame.setVisible(true);
    }
}
