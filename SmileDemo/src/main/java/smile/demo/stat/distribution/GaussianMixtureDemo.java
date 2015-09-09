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

package smile.demo.stat.distribution;

import java.awt.Color;
import java.awt.GridLayout;

import javax.swing.JFrame;
import javax.swing.JPanel;

import smile.math.Math;
import smile.plot.Histogram;
import smile.plot.PlotCanvas;
import smile.plot.QQPlot;
import smile.stat.distribution.ExponentialFamilyMixture;
import smile.stat.distribution.GaussianDistribution;
import smile.stat.distribution.GaussianMixture;

/**
 *
 * @author Haifeng Li
 */
@SuppressWarnings("serial")
public class GaussianMixtureDemo extends JPanel {
    public GaussianMixtureDemo() {
        super(new GridLayout(2, 2));

        // Gaussian mixture with singular component.
        double[] data = {
            23.0, 23.0, 22.0, 22.0, 21.0, 24.0, 24.0, 24.0, 24.0,
            24.0, 24.0, 24.0, 24.0, 22.0, 22.0, 16.0, 16.0, 16.0,
            23.0, 23.0, 15.0, 21.0, 21.0, 21.0, 21.0, 24.0, 24.0,
            21.0, 21.0, 24.0, 24.0, 24.0, 24.0,  1.0,  1.0, 23.0,
            23.0, 22.0, 22.0, 14.0, 24.0, 24.0, 23.0, 23.0, 18.0,
            18.0, 23.0, 23.0, 24.0, 24.0, 22.0, 22.0, 17.0, 17.0,
            17.0, 21.0, 21.0, 15.0, 14.0
        };

        ExponentialFamilyMixture mixture = new GaussianMixture(data);

        PlotCanvas canvas = Histogram.plot(data, 24);
        canvas.setTitle("Gaussian Mixture with Singular Component");
        add(canvas);

        double width = (Math.max(data) - Math.min(data)) / 24;
        double[][] p = new double[50][2];
        for (int i = 0; i < p.length; i++) {
            p[i][0] = i*0.5;
            p[i][1] = mixture.p(p[i][0]) * width;
        }

        canvas.line(p, Color.RED);

        canvas = QQPlot.plot(data, mixture);
        canvas.setTitle("Q-Q Plot");
        add(canvas);

        // Gaussian mixture of five components.
        data = new double[3000];

        GaussianDistribution g1 = new GaussianDistribution(1.0, 1.0);
        for (int i = 0; i < 500; i++)
            data[i] = g1.rand();

        GaussianDistribution g2 = new GaussianDistribution(4.0, 1.0);
        for (int i = 500; i < 1000; i++)
            data[i] = g2.rand();

        GaussianDistribution g3 = new GaussianDistribution(8.0, 1.0);
        for (int i = 1000; i < 2000; i++)
            data[i] = g3.rand();

        GaussianDistribution g4 = new GaussianDistribution(-3.0, 1.0);
        for (int i = 2000; i < 2500; i++)
            data[i] = g4.rand();

        GaussianDistribution g5 = new GaussianDistribution(-6.0, 1.0);
        for (int i = 2500; i < 3000; i++)
            data[i] = g5.rand();

        mixture = new GaussianMixture(data, 5);

        canvas = Histogram.plot(data, 50);
        canvas.setTitle("Gaussian Mixture of Five Components");
        add(canvas);

        width = (Math.max(data) - Math.min(data)) / 50;
        p = new double[220][2];
        for (int i = 0; i < p.length; i++) {
            p[i][0] = -10 + i*0.1;
            p[i][1] = mixture.p(p[i][0]) * width;
        }

        canvas.line(p, Color.RED);

        canvas = QQPlot.plot(data, mixture);
        canvas.setTitle("Q-Q Plot");
        add(canvas);
    }
    
    @Override
    public String toString() {
        return "Gaussian Mixture";
    }

    public static void main(String[] args) {
        JFrame frame = new JFrame("Gaussian Mixture");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLocationRelativeTo(null);
        frame.getContentPane().add(new GaussianMixtureDemo());
        frame.setVisible(true);
    }
}
