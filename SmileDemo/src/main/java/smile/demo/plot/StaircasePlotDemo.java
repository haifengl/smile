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

import smile.plot.PlotCanvas;
import smile.plot.StaircasePlot;

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
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLocationRelativeTo(null);
        frame.getContentPane().add(new StaircasePlotDemo());
        frame.setVisible(true);
    }
}
