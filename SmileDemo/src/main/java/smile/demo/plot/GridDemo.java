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

import java.awt.GridLayout;

import javax.swing.JFrame;
import javax.swing.JPanel;

import smile.plot.Grid;
import smile.plot.PlotCanvas;

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
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLocationRelativeTo(null);
        frame.getContentPane().add(new GridDemo());
        frame.setVisible(true);
    }
}
