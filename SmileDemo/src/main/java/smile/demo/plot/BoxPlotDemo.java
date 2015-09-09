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

import smile.plot.BoxPlot;
import smile.plot.PlotCanvas;

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
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLocationRelativeTo(null);
        frame.getContentPane().add(new BoxPlotDemo());
        frame.setVisible(true);
    }
}
