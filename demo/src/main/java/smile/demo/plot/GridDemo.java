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
import smile.plot.swing.Grid;
import smile.plot.swing.PlotCanvas;

/**
 *
 * @author Haifeng Li
 */
@SuppressWarnings("serial")
public class GridDemo extends JPanel {
    public GridDemo() {
        super(new GridLayout(1,1));

        int n = 20;
        int m = 20;
        double[][][] z = new double[m][n][2];

        for (int i = 0; i < m; i++) {
            for (int j = 0; j < n; j++) {
                z[i][j][0] = i + 0.5*Math.random();
                z[i][j][1] = j + 0.5*Math.random();
            }
        }

        PlotCanvas canvas = Grid.plot("Grid Plot", z);
        canvas.setTitle("Grid Plot");
        add(canvas);
    }

    @Override
    public String toString() {
        return "Grid";
    }

    public static void main(String[] args) {
        JFrame frame = new JFrame("Grid Plot");
        frame.setSize(1000, 1000);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLocationRelativeTo(null);
        frame.getContentPane().add(new GridDemo());
        frame.setVisible(true);
    }
}
