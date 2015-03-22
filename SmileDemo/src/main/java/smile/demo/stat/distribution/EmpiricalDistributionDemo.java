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

import smile.plot.Histogram;
import smile.plot.PlotCanvas;
import smile.plot.StaircasePlot;
import smile.stat.distribution.EmpiricalDistribution;

/**
 *
 * @author Haifeng Li
 */
@SuppressWarnings("serial")
public class EmpiricalDistributionDemo extends JPanel {
    public EmpiricalDistributionDemo() {
        super(new GridLayout(2,2));
        
        double[] prob = new double[10];
        for (int i = 0; i < prob.length; i++)
            prob[i] = 1.0 / prob.length;

        EmpiricalDistribution emp = new EmpiricalDistribution(prob);
        double[] data = new double[500];
        for (int i = 0; i < 500; i++) {
            data[i] = emp.rand();
        }

        PlotCanvas canvas = Histogram.plot(data, 10);
        canvas.setTitle("Equal Probabilities");
        add(canvas);

        double[][] p = new double[10][2];
        for (int i = 0; i < p.length; i++) {
            p[i][0] = i;
            p[i][1] = emp.p(p[i][0]);
        }

        canvas.line(p, Color.RED);

        p = new double[11][2];
        for (int i = 0; i < p.length; i++) {
            p[i][0] = i;
            p[i][1] = emp.cdf(p[i][0]);
        }

        canvas = StaircasePlot.plot(p);
        canvas.setTitle("CDF");
        add(canvas);

        prob = new double[10];
        for (int i = 0; i < prob.length; i++)
            prob[i] = 1.0 / prob.length;
        prob[1] = 0.2;
        prob[3] = 0.05;
        prob[4] = 0.05;
        prob[6] = 0.08;
        prob[7] = 0.12;

        emp = new EmpiricalDistribution(prob);
        for (int i = 0; i < 500; i++) {
            data[i] = emp.rand();
        }

        canvas = Histogram.plot(data, 10);
        canvas.setTitle("Non-Equal Probabilities");
        add(canvas);

        p = new double[10][2];
        for (int i = 0; i < p.length; i++) {
            p[i][0] = i;
            p[i][1] = emp.p(p[i][0]);
        }

        canvas.line(p, Color.RED);

        p = new double[11][2];
        for (int i = 0; i < p.length; i++) {
            p[i][0] = i;
            p[i][1] = emp.cdf(p[i][0]);
        }

        canvas = StaircasePlot.plot(p);
        canvas.setTitle("CDF");
        add(canvas);
    }
    
    @Override
    public String toString() {
        return "Empirical";
    }

    public static void main(String[] args) {
        JFrame frame = new JFrame("Empirical Distribution");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLocationRelativeTo(null);
        frame.getContentPane().add(new EmpiricalDistributionDemo());
        frame.setVisible(true);
    }
}
