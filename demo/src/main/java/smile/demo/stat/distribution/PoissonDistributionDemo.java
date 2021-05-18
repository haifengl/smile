/*
 * Copyright (c) 2010-2020 Haifeng Li. All rights reserved.
 *
 * Smile is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of
 * the License, or (at your option) any later version.
 *
 * Smile is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Smile.  If not, see <https://www.gnu.org/licenses/>.
 */

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

import smile.plot.swing.Canvas;
import smile.plot.swing.BarPlot;
import smile.plot.swing.Histogram;
import smile.plot.swing.Staircase;
import smile.stat.distribution.PoissonDistribution;

/**
 *
 * @author Haifeng Li
 */
@SuppressWarnings("serial")
public class PoissonDistributionDemo extends JPanel implements ChangeListener {
    private JPanel optionPane;
    private JPanel canvas;
    private Canvas pdf;
    private Canvas cdf;
    private Canvas histogram;
    private JSlider lambdaSlider;
    private double lambda = 3;

    public PoissonDistributionDemo() {
        super(new BorderLayout());

        Hashtable<Integer, JLabel> labelTable = new Hashtable<>();
        for (int i = 0; i <= 10; i+=2) {
            labelTable.put(i, new JLabel(String.valueOf(i)));
        }

        lambdaSlider = new JSlider(0, 10, (int) Math.round(lambda));
        lambdaSlider.addChangeListener(this);
        lambdaSlider.setLabelTable(labelTable);
        lambdaSlider.setMajorTickSpacing(5);
        lambdaSlider.setMinorTickSpacing(1);
        lambdaSlider.setPaintTicks(true);
        lambdaSlider.setPaintLabels(true);

        optionPane = new JPanel(new FlowLayout(FlowLayout.LEFT));
        optionPane.setBorder(BorderFactory.createRaisedBevelBorder());
        optionPane.add(new JLabel("\u03BB:"));
        optionPane.add(lambdaSlider);

        add(optionPane, BorderLayout.NORTH);

        canvas = new JPanel(new GridLayout(1, 3));
        add(canvas, BorderLayout.CENTER);

        PoissonDistribution dist = new PoissonDistribution(lambda);

        double[] p = new double[21];
        double[][] q = new double[21][2];
        for (int i = 0; i < p.length; i++) {
            p[i] = dist.p(i);
            q[i][0] = i;
            q[i][1] = dist.cdf(i);
        }

        double[] lowerBound = {0.0, 0.0};
        double[] upperBound = {20.0, 1.0};
        pdf = new Canvas(lowerBound, upperBound);
        pdf.add(BarPlot.of(p));
        pdf.setTitle("PDF");
        canvas.add(pdf.panel());

        cdf = new Canvas(lowerBound, upperBound);
        cdf.add(Staircase.of(q));
        cdf.setTitle("CDF");
        canvas.add(cdf.panel());

        double[] data = new double[500];
        for (int i = 0; i < data.length; i++) {
            data[i] = dist.rand();
        }

        histogram = Histogram.of(data, 10, true).canvas();
        histogram.setTitle("Histogram");
        canvas.add(histogram.panel());
    }

    @Override
    public void stateChanged(ChangeEvent e) {
        if (e.getSource() == lambdaSlider) {
            lambda = lambdaSlider.getValue();
            if (lambda == 0) lambda = 1;

            PoissonDistribution dist = new PoissonDistribution(lambda);

            double[] p = new double[21];
            double[][] q = new double[21][2];
            for (int i = 0; i < p.length; i++) {
                p[i] = dist.p(i);
                q[i][0] = i;
                q[i][1] = dist.cdf(i);
            }

            pdf.clear();
            pdf.add(BarPlot.of(p));

            cdf.clear();
            cdf.add(Staircase.of(q));

            double[] data = new double[500];
            for (int i = 0; i < data.length; i++) {
                data[i] = dist.rand();
            }

            histogram.clear();
            histogram.add(Histogram.of(data, 10, true));
            canvas.repaint();
        }
    }

    @Override
    public String toString() {
        return "Poisson";
    }

    public static void main(String[] args) {
        JFrame frame = new JFrame("Poisson Distribution");
        frame.setSize(1000, 1000);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLocationRelativeTo(null);
        frame.getContentPane().add(new PoissonDistributionDemo());
        frame.setVisible(true);
    }
}
