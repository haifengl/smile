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

import smile.plot.Histogram;
import smile.plot.PlotCanvas;
import smile.plot.Line;
import smile.plot.LinePlot;
import smile.plot.QQPlot;
import smile.stat.distribution.FDistribution;

/**
 *
 * @author Haifeng Li
 */
@SuppressWarnings("serial")
public class FDistributionDemo extends JPanel implements ChangeListener {
    private JPanel optionPane;
    private JPanel canvas;
    private PlotCanvas pdf;
    private PlotCanvas cdf;
    private PlotCanvas histogram;
    private PlotCanvas qqplot;
    private JSlider d1Slider;
    private JSlider d2Slider;
    private int d1 = 20;
    private int d2 = 20;

    public FDistributionDemo() {
        super(new BorderLayout());

        Hashtable<Integer, JLabel> labelTable = new Hashtable<>();
        labelTable.put(1, new JLabel(String.valueOf(1)));
        for (int i = 20; i <= 100; i += 20) {
            labelTable.put(new Integer(i), new JLabel(String.valueOf(i)));
        }

        d1Slider = new JSlider(1, 100, d1);
        d1Slider.addChangeListener(this);
        d1Slider.setLabelTable(labelTable);
        d1Slider.setMajorTickSpacing(10);
        d1Slider.setMinorTickSpacing(2);
        d1Slider.setPaintTicks(true);
        d1Slider.setPaintLabels(true);

        d2Slider = new JSlider(1, 100, d2);
        d2Slider.addChangeListener(this);
        d2Slider.setLabelTable(labelTable);
        d2Slider.setMajorTickSpacing(10);
        d2Slider.setMinorTickSpacing(2);
        d2Slider.setPaintTicks(true);
        d2Slider.setPaintLabels(true);

        optionPane = new JPanel(new FlowLayout(FlowLayout.LEFT));
        optionPane.setBorder(BorderFactory.createRaisedBevelBorder());
        optionPane.add(new JLabel("Degree of Freedom 1:"));
        optionPane.add(d1Slider);
        optionPane.add(new JLabel("Degree of Freedom 2:"));
        optionPane.add(d2Slider);

        add(optionPane, BorderLayout.NORTH);

        canvas = new JPanel(new GridLayout(2, 2));
        add(canvas, BorderLayout.CENTER);

        FDistribution dist = new FDistribution(d1, d2);

        double[][] p = new double[100][2];
        double[][] q = new double[100][2];
        for (int i = 0; i < p.length; i++) {
            p[i][0] = (i+1) / 20.0;
            p[i][1] = dist.p(p[i][0]);
            q[i][0] = (i+1) / 20.0;
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
        if (e.getSource() == d1Slider || e.getSource() == d2Slider) {
            d1 = d1Slider.getValue();
            if (d1 == 0) d1 = 1;
            d2 = d2Slider.getValue();
            if (d2 == 0) d2 = 1;

            FDistribution dist = new FDistribution(d1, d2);

            double[][] p = new double[100][2];
            double[][] q = new double[100][2];
            for (int i = 0; i < p.length; i++) {
                p[i][0] = (i + 1) / 20.0;
                p[i][1] = dist.p(p[i][0]);
                q[i][0] = (i + 1) / 20.0;
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
        return "F";
    }

    public static void main(String[] args) {
        JFrame frame = new JFrame("F-Distribution");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLocationRelativeTo(null);
        frame.getContentPane().add(new FDistributionDemo());
        frame.setVisible(true);
    }
}
