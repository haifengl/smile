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
import smile.stat.distribution.HyperGeometricDistribution;

/**
 *
 * @author Haifeng Li
 */
@SuppressWarnings("serial")
public class HyperGeometricDistributionDemo extends JPanel implements ChangeListener {
    private JPanel optionPane;
    private JPanel canvas;
    private PlotCanvas pdf;
    private PlotCanvas cdf;
    private PlotCanvas histogram;
    private JSlider mSlider;
    private JSlider nSlider;
    private int N = 100;
    private int m = 50;
    private int n = 10;

    public HyperGeometricDistributionDemo() {
        super(new BorderLayout());

        Hashtable<Integer, JLabel> labelTable = new Hashtable<>();
        labelTable.put(1, new JLabel(String.valueOf(1)));
        for (int i = 20; i <= 100; i+=20) {
            labelTable.put(new Integer(i), new JLabel(String.valueOf(i)));
        }

        mSlider = new JSlider(1, 100, m);
        mSlider.addChangeListener(this);
        mSlider.setLabelTable(labelTable);
        mSlider.setMajorTickSpacing(20);
        mSlider.setMinorTickSpacing(5);
        mSlider.setPaintTicks(true);
        mSlider.setPaintLabels(true);

        nSlider = new JSlider(0, 100, n);
        nSlider.addChangeListener(this);
        nSlider.setLabelTable(labelTable);
        nSlider.setMajorTickSpacing(20);
        nSlider.setMinorTickSpacing(5);
        nSlider.setPaintTicks(true);
        nSlider.setPaintLabels(true);

        optionPane = new JPanel(new FlowLayout(FlowLayout.LEFT));
        optionPane.setBorder(BorderFactory.createRaisedBevelBorder());
        optionPane.add(new JLabel("m:"));
        optionPane.add(mSlider);
        optionPane.add(new JLabel("n:"));
        optionPane.add(nSlider);

        add(optionPane, BorderLayout.NORTH);

        canvas = new JPanel(new GridLayout(1, 3));
        add(canvas, BorderLayout.CENTER);

        HyperGeometricDistribution dist = new HyperGeometricDistribution(N, m, n);

        double[][] p = new double[50][2];
        double[][] q = new double[50][2];
        for (int i = 0; i < p.length; i++) {
            p[i][0] = i;
            p[i][1] = dist.p(p[i][0]);
            q[i][0] = i;
            q[i][1] = dist.cdf(p[i][0]);
        }

        double[] lowerBound = {0.0, 0.0};
        double[] upperBound = {50.0, 1.0};
        pdf = new PlotCanvas(lowerBound, upperBound);
        pdf.add(new BarPlot(p));
        pdf.setTitle("PDF");
        canvas.add(pdf);

        cdf = new PlotCanvas(lowerBound, upperBound);
        cdf.staircase(q, Color.BLACK);
        cdf.setTitle("CDF");
        canvas.add(cdf);

        int[] data = new int[500];
        for (int i = 0; i < data.length; i++) {
            data[i] = (int) dist.rand();
        }

        histogram = Histogram.plot(data, 10);
        histogram.setTitle("Histogram");
        canvas.add(histogram);
    }

    @Override
    public void stateChanged(ChangeEvent e) {
        if (e.getSource() == mSlider || e.getSource() == nSlider) {
            m = mSlider.getValue();
            n = nSlider.getValue();

            HyperGeometricDistribution dist = new HyperGeometricDistribution(N, m, n);

            double[][] p = new double[50][2];
            double[][] q = new double[50][2];
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

            int[] data = new int[500];
            for (int i = 0; i < data.length; i++) {
                data[i] = (int) dist.rand();
            }

            histogram.clear();
            histogram.histogram(data, 10, Color.BLUE);
        }
    }

    @Override
    public String toString() {
        return "Hypergeometric";
    }

    public static void main(String[] args) {
        JFrame frame = new JFrame("Hyper Geometric Distribution");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLocationRelativeTo(null);
        frame.getContentPane().add(new HyperGeometricDistributionDemo());
        frame.setVisible(true);
    }
}
