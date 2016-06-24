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
import smile.stat.distribution.GammaDistribution;

/**
 *
 * @author Haifeng Li
 */
@SuppressWarnings("serial")
public class GammaDistributionDemo extends JPanel implements ChangeListener {
    private JPanel optionPane;
    private JPanel canvas;
    private PlotCanvas pdf;
    private PlotCanvas cdf;
    private PlotCanvas histogram;
    private PlotCanvas qqplot;
    private JSlider scaleSlider;
    private JSlider shapeSlider;
    private double scale = 2;
    private double shape = 3;

    public GammaDistributionDemo() {
        super(new BorderLayout());

        Hashtable<Integer, JLabel> shapeLabelTable = new Hashtable<>();
        for (int i = 0; i <= 10; i+=2) {
            shapeLabelTable.put(new Integer(i), new JLabel(String.valueOf(i)));
        }

        shapeSlider = new JSlider(0, 10, (int) Math.round(shape));
        shapeSlider.addChangeListener(this);
        shapeSlider.setLabelTable(shapeLabelTable);
        shapeSlider.setMajorTickSpacing(2);
        shapeSlider.setMinorTickSpacing(1);
        shapeSlider.setPaintTicks(true);
        shapeSlider.setPaintLabels(true);

        Hashtable<Integer, JLabel> scaleLabelTable = new Hashtable<>();
        for (int i = 0; i <= 50; i+=10) {
            scaleLabelTable.put(new Integer(i), new JLabel(String.valueOf(i/10)));
        }

        scaleSlider = new JSlider(0, 50, (int) Math.round(scale*10));
        scaleSlider.addChangeListener(this);
        scaleSlider.setLabelTable(scaleLabelTable);
        scaleSlider.setMajorTickSpacing(10);
        scaleSlider.setMinorTickSpacing(2);
        scaleSlider.setPaintTicks(true);
        scaleSlider.setPaintLabels(true);

        optionPane = new JPanel(new FlowLayout(FlowLayout.LEFT));
        optionPane.setBorder(BorderFactory.createRaisedBevelBorder());
        optionPane.add(new JLabel("Shape:"));
        optionPane.add(shapeSlider);
        optionPane.add(new JLabel("Scale:"));
        optionPane.add(scaleSlider);

        add(optionPane, BorderLayout.NORTH);

        canvas = new JPanel(new GridLayout(2, 2));
        add(canvas, BorderLayout.CENTER);

        GammaDistribution dist = new GammaDistribution(shape, scale);

        double[][] p = new double[100][2];
        double[][] q = new double[100][2];
        for (int i = 0; i < p.length; i++) {
            p[i][0] = i / 5.0;
            p[i][1] = dist.p(p[i][0]);
            q[i][0] = i / 5.0;
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
        if (e.getSource() == scaleSlider || e.getSource() == shapeSlider) {
            scale = scaleSlider.getValue() / 10.0;
            shape = shapeSlider.getValue();
            if (scale == 0) scale = 0.01;
            if (shape == 0) shape = 0.01;

            GammaDistribution dist = new GammaDistribution(shape, scale);

            double[][] p = new double[100][2];
            double[][] q = new double[100][2];
            for (int i = 0; i < p.length; i++) {
                p[i][0] = i / 5.0;
                p[i][1] = dist.p(p[i][0]);
                q[i][0] = i / 5.0;
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
        return "Gamma";
    }

    public static void main(String[] args) {
        JFrame frame = new JFrame("Gamma Distributions");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLocationRelativeTo(null);
        frame.getContentPane().add(new GammaDistributionDemo());
        frame.setVisible(true);
    }
}
