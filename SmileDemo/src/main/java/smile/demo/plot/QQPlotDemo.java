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

import smile.math.Math;
import smile.plot.PlotCanvas;
import smile.plot.QQPlot;
import smile.stat.distribution.GaussianDistribution;

/**
 *
 * @author Haifeng Li
 */
@SuppressWarnings("serial")
public class QQPlotDemo extends JPanel {
    public QQPlotDemo() {
        super(new GridLayout(1,2));

        double[] x  = new double[5000];
        for (int j = 0; j < x.length; j++) {
            x[j] = Math.random();
        }
        
        double[] y  = new double[500];
        for (int j = 0; j < y.length; j++) {
            y[j] = j / 500.0;
        }

        PlotCanvas canvas = QQPlot.plot(x, y);
        canvas.setTitle("Uniform (Smile)");
        add(canvas);

        double[] x3  = new double[5000];
        for (int j = 0; j < x3.length; j++) {
            x3[j] = GaussianDistribution.getInstance().rand();
        }

        canvas = QQPlot.plot(x3);
        canvas.setTitle("Gaussian");
        add(canvas);
    }

    @Override
    public String toString() {
        return "Q-Q Plot";
    }

    public static void main(String[] args) {
        JFrame frame = new JFrame("QQ Plot");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLocationRelativeTo(null);
        frame.getContentPane().add(new QQPlotDemo());
        frame.setVisible(true);
    }
}
