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

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.util.Hashtable;

import javax.swing.BorderFactory;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import smile.plot.PlotCanvas;
import smile.plot.BarPlot;
import smile.plot.Histogram;
import smile.stat.distribution.BernoulliDistribution;

/**
 *
 * @author Haifeng Li
 */
@SuppressWarnings("serial")
public class BernoulliDistributionDemo extends JPanel implements ChangeListener {
    private JPanel optionPane;
    private JPanel canvas;
    private PlotCanvas pdf;
    private PlotCanvas cdf;
    private PlotCanvas histogram;
    private JSlider probSlider;
    private double prob = 0.3;

    public BernoulliDistributionDemo() {
        super(new BorderLayout());

        Hashtable<Integer, JLabel> labelTable = new Hashtable<>();
        for (int i = 1; i < 10; i+=2) {
            labelTable.put(new Integer(i), new JLabel(String.valueOf(i/10.0)));
        }

        probSlider = new JSlider(1, 9, (int) Math.round(prob * 10));
        probSlider.addChangeListener(this);
        probSlider.setLabelTable(labelTable);
        probSlider.setMajorTickSpacing(5);
        probSlider.setMinorTickSpacing(1);
        probSlider.setPaintTicks(true);
        probSlider.setPaintLabels(true);

        optionPane = new JPanel(new FlowLayout(FlowLayout.LEFT));
        optionPane.setBorder(BorderFactory.createRaisedBevelBorder());
        optionPane.add(new JLabel("Probability:"));
        optionPane.add(probSlider);

        add(optionPane, BorderLayout.NORTH);

        canvas = new JPanel(new GridLayout(1, 3));
        add(canvas, BorderLayout.CENTER);

        BernoulliDistribution dist = new BernoulliDistribution(prob);

        double[][] p = new double[2][2];
        double[][] q = new double[2][2];
        for (int i = 0; i < p.length; i++) {
            p[i][0] = i;
            p[i][1] = dist.p(p[i][0]);
            q[i][0] = i;
            q[i][1] = dist.cdf(p[i][0]);
        }

        double[] lowerBound = {0.0, 0.0};
        double[] upperBound = {1.0, 1.0};
        pdf = new PlotCanvas(lowerBound, upperBound);
        pdf.add(new BarPlot(p));
        pdf.setTitle("PDF");
        canvas.add(pdf);

        cdf = new PlotCanvas(lowerBound, upperBound);
        cdf.staircase(q, Color.BLACK);
        cdf.setTitle("CDF");
        canvas.add(cdf);

        double[] data = new double[500];
        for (int i = 0; i < data.length; i++) {
            data[i] = dist.rand();
        }

        histogram = Histogram.plot(data, 2);
        histogram.setTitle("Histogram");
        canvas.add(histogram);
    }

    @Override
    public void stateChanged(ChangeEvent e) {
        if (e.getSource() == probSlider) {
            prob = probSlider.getValue() / 10.0;

            BernoulliDistribution dist = new BernoulliDistribution(prob);

            double[][] p = new double[2][2];
            double[][] q = new double[2][2];
            for (int i = 0; i < p.length; i++) {
                p[i][0] = i;
                p[i][1] = dist.p(p[i][0]);
                q[i][0] = i;
                q[i][1] = dist.cdf(p[i][0]);
            }

            pdf.clear();
            pdf.add(new BarPlot(p));

            cdf.clear();
            cdf.staircase(q, Color.BLACK);

            double[] data = new double[500];
            for (int i = 0; i < data.length; i++) {
                data[i] = dist.rand();
            }

            histogram.clear();
            histogram.histogram(data, 2, Color.BLUE);
        }
    }

    @Override
    public String toString() {
        return "Bernoulli";
    }

    public static void main(String[] args) {
        JFrame frame = new JFrame("Bernoulli Distribution");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLocationRelativeTo(null);
        frame.getContentPane().add(new BernoulliDistributionDemo());
        frame.setVisible(true);
    }
}
