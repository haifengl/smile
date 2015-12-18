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

import java.awt.Color;
import java.awt.GridLayout;

import javax.swing.JFrame;
import javax.swing.JPanel;

import smile.math.Math;
import smile.plot.Histogram;
import smile.plot.PlotCanvas;

/**
 *
 * @author Haifeng Li
 */
@SuppressWarnings("serial")
public class HistogramDemo extends JPanel {
    public HistogramDemo() {
        super(new GridLayout(1,2));

        double[] data = new double[1000];
        for (int j = 0; j < data.length; j++) {
            double x, y, r;
            do {
                x = 2 * (Math.random() - 0.5);
                y = 2 * (Math.random() - 0.5);
                r = x * x + y * y;
            } while (r >= 1.0);

            double z = Math.sqrt(-2.0 * Math.log(r) / r);
            data[j] = 0.01 * x * z;
        }

        PlotCanvas canvas = Histogram.plot("Dataset 1", data, smile.math.Histogram.breaks(-6/100., 6/100., 30));
        canvas.setTitle("Overlapped Histogram");
        add(canvas);

        data = new double[1000];
        for (int j = 0; j < data.length; j++) {
            double x, y, r;
            do {
                x = 2 * (Math.random() - 0.5);
                y = 2 * (Math.random() - 0.5);
                r = x * x + y * y;
            } while (r >= 1.0);

            double z = Math.sqrt(-2.0 * Math.log(r) / r);
            data[j] = 0.01 *( 2 + x * z);
        }

        canvas.histogram("Dataset 2", data, smile.math.Histogram.breaks(-6/100., 6/100., 30), Color.RED);

        double[] prob = {0.2, 0.3, 0.1, 0.05, 0.2, 0.15};
        int[] data2 = Math.random(prob, 1000);

        canvas = Histogram.plot(data2, 6);
        canvas.setTitle("Sampling with Uneuqal Probabilities");
        add(canvas);
    }

    @Override
    public String toString() {
        return "Histogram";
    }

    public static void main(String[] args) {
        JFrame frame = new JFrame("Histogram");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLocationRelativeTo(null);
        frame.getContentPane().add(new HistogramDemo());
        frame.setVisible(true);
    }
}
