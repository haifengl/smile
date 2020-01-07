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
import smile.plot.swing.BarPlot;
import smile.plot.swing.PlotCanvas;

/**
 *
 * @author Haifeng Li
 */
@SuppressWarnings("serial")
public class BarPlotDemo extends JPanel {

    private static final String BAR_PLOT = "Bar Plot";

    public BarPlotDemo() {
        super(new GridLayout(1,1));

        String[] labels = new String[10];
        double[] data = new double[10];
        for (int j = 0; j < data.length; j++) {
            labels[j] = "V" + (j+1);
            data[j] = Math.random() - 0.5;
        }
        PlotCanvas canvas = BarPlot.plot(data, labels);
        canvas.setTitle(BAR_PLOT);
        add(canvas);
    }

    @Override
    public String toString() {
        return BAR_PLOT;
    }

    public static void main(String[] args) {
        JFrame frame = new JFrame(BAR_PLOT);
        frame.setSize(1000, 1000);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLocationRelativeTo(null);
        frame.getContentPane().add(new BarPlotDemo());
        frame.setVisible(true);
    }
}
