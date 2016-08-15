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

import smile.math.Math;
import smile.plot.Histogram;
import smile.plot.PlotCanvas;
import smile.plot.Line;
import smile.plot.LinePlot;
import smile.plot.QQPlot;
import smile.stat.distribution.LogisticDistribution;

/**
 *
 * @author Haifeng Li
 */
@SuppressWarnings("serial")
public class LogisticDistributionDemo extends JPanel implements ChangeListener {
    private JPanel optionPane;
    private JPanel canvas;
    private PlotCanvas pdf;
    private PlotCanvas cdf;
    private PlotCanvas histogram;
    private PlotCanvas qqplot;
    private JSlider sigmaSlider;
    private double sigma = 1;

    public LogisticDistributionDemo() {
        super(new BorderLayout());

        Hashtable<Integer, JLabel> labelTable = new Hashtable<>();
        for (int i = 0; i <= 50; i+=10) {
            labelTable.put(new Integer(i), new JLabel(String.valueOf(i/10)));
        }

        sigmaSlider = new JSlider(0, 50, (int) Math.round(sigma * 10));
        sigmaSlider.addChangeListener(this);
        sigmaSlider.setLabelTable(labelTable);
        sigmaSlider.setMajorTickSpacing(10);
        sigmaSlider.setMinorTickSpacing(2);
        sigmaSlider.setPaintTicks(true);
        sigmaSlider.setPaintLabels(true);

        optionPane = new JPanel(new FlowLayout(FlowLayout.LEFT));
        optionPane.setBorder(BorderFactory.createRaisedBevelBorder());
        optionPane.add(new JLabel("Scale:"));
        optionPane.add(sigmaSlider);

        add(optionPane, BorderLayout.NORTH);

        canvas = new JPanel(new GridLayout(2, 2));
        add(canvas, BorderLayout.CENTER);

        LogisticDistribution dist = new LogisticDistribution(0, sigma);

        double[][] p = new double[200][2];
        double[][] q = new double[200][2];
        for (int i = 0; i < p.length; i++) {
            p[i][0] = (i - 100) / 10.0;
            p[i][1] = dist.p(p[i][0]);
            q[i][0] = (i - 100) / 10.0;
            q[i][1] = dist.cdf(p[i][0]);
        }

        pdf = LinePlot.plot(p, Line.Style.SOLID, Color.BLUE);
        pdf.setTitle("PDF");
        canvas.add(pdf);

        cdf = LinePlot.plot(q, Line.Style.SOLID, Color.BLUE);
        cdf.setTitle("CDF");
        canvas.add(cdf);

        double[] data = new double[500];
        for (int i = 0; i < data.length; i++) {
            data[i] = dist.rand();
        }

        histogram = Histogram.plot(data, 20);
        histogram.setTitle("Histogram");
        canvas.add(histogram);

        qqplot = QQPlot.plot(data, dist);
        qqplot.setTitle("Q-Q Plot");
        canvas.add(qqplot);
    }

    @Override
    public void stateChanged(ChangeEvent e) {
        if (e.getSource() == sigmaSlider) {
            sigma = sigmaSlider.getValue() / 10.0;
            if (sigma == 0) sigma = 0.01;

            LogisticDistribution dist = new LogisticDistribution(0, sigma);

            double[][] p = new double[200][2];
            double[][] q = new double[200][2];
            for (int i = 0; i < p.length; i++) {
                p[i][0] = (i - 100) / 10.0;
                p[i][1] = dist.p(p[i][0]);
                q[i][0] = (i - 100) / 10.0;
                q[i][1] = dist.cdf(p[i][0]);
            }

            pdf.clear();
            pdf.line(p, Line.Style.SOLID, Color.BLUE);

            cdf.clear();
            cdf.line(q, Line.Style.SOLID, Color.BLUE);

            double[] data = new double[500];
            for (int i = 0; i < data.length; i++) {
                data[i] = dist.rand();
            }

            histogram.clear();
            histogram.histogram(data, 20, Color.BLUE);

            qqplot.clear();
            qqplot.add(new QQPlot(data, dist));
            canvas.repaint();
        }
    }

    @Override
    public String toString() {
        return "Logistic";
    }

    public static void main(String[] args) {
        JFrame frame = new JFrame("Logistic Distribution");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLocationRelativeTo(null);
        frame.getContentPane().add(new LogisticDistributionDemo());
        frame.setVisible(true);
    }
}
