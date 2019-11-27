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

import smile.plot.swing.PlotCanvas;
import smile.plot.swing.StaircasePlot;

/**
 *
 * @author Haifeng Li
 */
@SuppressWarnings("serial")
public class StaircasePlotDemo extends JPanel {
    public StaircasePlotDemo() {
        super(new GridLayout(1,2));

        double[][] data = new double[10][2];
        for (int j = 0; j < data.length; j++) {
            data[j][0] = j;
            data[j][1] = Math.random();
        }

        PlotCanvas canvas = StaircasePlot.plot("Staircase Plot", data);
        canvas.setTitle("Staircase Plot");
        add(canvas);
    }
    
    @Override
    public String toString() {
        return "Staircase";
    }

    public static void main(String[] args) {
        JFrame frame = new JFrame("Staircase Plot");
        frame.setSize(1000, 1000);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLocationRelativeTo(null);
        frame.getContentPane().add(new StaircasePlotDemo());
        frame.setVisible(true);
    }
}
