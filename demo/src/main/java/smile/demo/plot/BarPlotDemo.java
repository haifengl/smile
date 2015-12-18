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

import smile.plot.BarPlot;
import smile.plot.PlotCanvas;

/**
 *
 * @author Haifeng Li
 */
@SuppressWarnings("serial")
public class BarPlotDemo extends JPanel {
    public BarPlotDemo() {
        super(new GridLayout(1,1));

        String[] labels = new String[10];
        double[] data = new double[10];
        for (int j = 0; j < data.length; j++) {
            labels[j] = "V" + (j+1);
            data[j] = Math.random() - 0.5;
        }
        PlotCanvas canvas = BarPlot.plot(data, labels);
        canvas.setTitle("Bar Plot");
        add(canvas);
    }

    @Override
    public String toString() {
        return "Bar Plot";
    }

    public static void main(String[] args) {
        JFrame frame = new JFrame("Bar Plot");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLocationRelativeTo(null);
        frame.getContentPane().add(new BarPlotDemo());
        frame.setVisible(true);
    }
}
