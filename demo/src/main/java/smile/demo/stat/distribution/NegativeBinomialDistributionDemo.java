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
import smile.stat.distribution.NegativeBinomialDistribution;

/**
 *
 * @author Haifeng Li
 */
@SuppressWarnings("serial")
public class NegativeBinomialDistributionDemo extends JPanel implements ChangeListener {
    private JPanel optionPane;
    private JPanel canvas;
    private Canvas pdf;
    private Canvas cdf;
    private Canvas histogram;
    private JSlider nSlider;
    private JSlider probSlider;
    private int n = 10;
    private double prob = 0.3;

    public NegativeBinomialDistributionDemo() {
        super(new BorderLayout());

        Hashtable<Integer, JLabel> nLabelTable = new Hashtable<>();
        nLabelTable.put(1, new JLabel(String.valueOf(1)));
        for (int i = 10; i <= 50; i+=10) {
            nLabelTable.put(i, new JLabel(String.valueOf(i)));
        }

        nSlider = new JSlider(1, 50, n);
        nSlider.addChangeListener(this);
        nSlider.setLabelTable(nLabelTable);
        nSlider.setMajorTickSpacing(10);
        nSlider.setMinorTickSpacing(2);
        nSlider.setPaintTicks(true);
        nSlider.setPaintLabels(true);

        Hashtable<Integer, JLabel> probLabelTable = new Hashtable<>();
        for (int i = 1; i < 10; i+=2) {
            probLabelTable.put(i, new JLabel(String.valueOf(i/10.0)));
        }

        probSlider = new JSlider(1, 9, (int) Math.round(prob * 10));
        probSlider.addChangeListener(this);
        probSlider.setLabelTable(probLabelTable);
        probSlider.setMajorTickSpacing(5);
        probSlider.setMinorTickSpacing(1);
        probSlider.setPaintTicks(true);
        probSlider.setPaintLabels(true);

        optionPane = new JPanel(new FlowLayout(FlowLayout.LEFT));
        optionPane.setBorder(BorderFactory.createRaisedBevelBorder());
        optionPane.add(new JLabel("N:"));
        optionPane.add(nSlider);
        optionPane.add(new JLabel("Probability:"));
        optionPane.add(probSlider);

        add(optionPane, BorderLayout.NORTH);

        canvas = new JPanel(new GridLayout(1, 3));
        add(canvas, BorderLayout.CENTER);

        NegativeBinomialDistribution dist = new NegativeBinomialDistribution(n, prob);

        double[] p = new double[50];
        double[][] q = new double[50][2];
        for (int i = 0; i < p.length; i++) {
            p[i] = dist.p(i);
            q[i][0] = i;
            q[i][1] = dist.cdf(i);
        }

        double[] lowerBound = {0.0, 0.0};
        double[] upperBound = {50.0, 1.0};
        pdf = new Canvas(lowerBound, upperBound);
        pdf.add(BarPlot.of(p));
        pdf.setTitle("PDF");
        canvas.add(pdf.panel());

        cdf = new Canvas(lowerBound, upperBound);
        cdf.add(Staircase.of(q));
        cdf.setTitle("CDF");
        canvas.add(cdf.panel());

        int[] data = new int[500];
        for (int i = 0; i < data.length; i++) {
            data[i] = (int) dist.rand();
        }

        histogram = Histogram.of(data, 20, true).canvas();
        histogram.setTitle("Histogram");
        canvas.add(histogram.panel());
    }

    @Override
    public void stateChanged(ChangeEvent e) {
        if (e.getSource() == probSlider || e.getSource() == nSlider) {
            n = nSlider.getValue();
            if (n == 0) n = 1;
            prob = probSlider.getValue() / 10.0;
            if (prob == 0) prob = 0.01;

            NegativeBinomialDistribution dist = new NegativeBinomialDistribution(n, prob);

            double[] p = new double[50];
            double[][] q = new double[50][2];
            for (int i = 0; i < p.length; i++) {
                p[i] = dist.p(i);
                q[i][0] = i;
                q[i][1] = dist.cdf(i);
            }

            pdf.clear();
            pdf.add(BarPlot.of(p));

            cdf.clear();
            cdf.add(Staircase.of(q));

            int[] data = new int[500];
            for (int i = 0; i < data.length; i++) {
                data[i] = (int) dist.rand();
            }

            histogram.clear();
            histogram.add(Histogram.of(data, 20, true));
            canvas.repaint();
        }
    }

    @Override
    public String toString() {
        return "Negative Binomial";
    }

    public static void main(String[] args) {
        JFrame frame = new JFrame("Negative Binomial Distribution");
        frame.setSize(1000, 1000);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLocationRelativeTo(null);
        frame.getContentPane().add(new NegativeBinomialDistributionDemo());
        frame.setVisible(true);
    }
}
