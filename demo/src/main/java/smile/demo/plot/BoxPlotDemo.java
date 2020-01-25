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

import java.awt.GridLayout;
import javax.swing.JFrame;
import javax.swing.JPanel;
import smile.plot.swing.BoxPlot;
import smile.plot.swing.PlotCanvas;

/**
 *
 * @author Haifeng Li
 */
@SuppressWarnings("serial")
public class BoxPlotDemo extends JPanel {
    public BoxPlotDemo() {
        super(new GridLayout(1,2));

        double[][] data = new double[5][100];
        for (int i = 0; i < data.length; i++) {
            for (int j = 0; j < data[i].length; j++) {
                double x, y, r;
                do {
                    x = 2 * (Math.random() - 0.5);
                    y = 2 * (Math.random() - 0.5);
                    r = x * x + y * y;
                } while (r >= 1.0);

                double z = Math.sqrt(-2.0 * Math.log(r) / r);
                data[i][j] = new Double(x * z);
            }
        }

        PlotCanvas canvas = BoxPlot.plot(data, new String[] {"Group A", "Group B", "Big Group C", "Group D", "Very Long Group E"});
        canvas.setTitle("Box Plot A");
        canvas.getAxis(0).setRotation(-Math.PI / 2);
        add(canvas);

        canvas = BoxPlot.plot(data[0]);
        canvas.setTitle("Box Plot B");
        add(canvas);
    }

    @Override
    public String toString() {
        return "Box Plot";
    }

    public static void main(String[] args) {
        JFrame frame = new JFrame("Box Plot");
        frame.setSize(1000, 1000);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLocationRelativeTo(null);
        frame.getContentPane().add(new BoxPlotDemo());
        frame.setVisible(true);
    }
}
