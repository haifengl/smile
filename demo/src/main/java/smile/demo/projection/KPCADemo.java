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
package smile.demo.projection;

import java.awt.Dimension;
import java.awt.GridLayout;

import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;

import smile.plot.Palette;
import smile.plot.PlotCanvas;
import smile.projection.KPCA;
import smile.projection.PCA;
import smile.math.Math;
import smile.math.kernel.GaussianKernel;

/**
 *
 * @author Haifeng Li
 */
@SuppressWarnings("serial")
public class KPCADemo extends ProjectionDemo {

    private double[] gamma = new double[dataset.length];
    JTextField gammaNumberField;

    public KPCADemo() {
        gammaNumberField = new JTextField(Double.toString(gamma[datasetIndex]), 5);
        optionPane.add(new JLabel("Gaussian Kernel gamma:"));
        optionPane.add(gammaNumberField);
    }

    @Override
    public JComponent learn() {
        JPanel pane = new JPanel(new GridLayout(2, 2));
        double[][] data = dataset[datasetIndex].toArray(new double[dataset[datasetIndex].size()][]);
        String[] names = dataset[datasetIndex].toArray(new String[dataset[datasetIndex].size()]);
        if (names[0] == null) {
            names = null;
        }

        if (gamma[datasetIndex] == 0.0) {
            int n = 0;
            for (int i = 0; i < data.length; i++) {
                for (int j = 0; j < i; j++, n++) {
                    gamma[datasetIndex] += Math.squaredDistance(data[i], data[j]);
                }
            }

            gamma[datasetIndex] = Math.sqrt(gamma[datasetIndex] / n) / 4;
        } else {
            try {
                gamma[datasetIndex] = Double.parseDouble(gammaNumberField.getText().trim());
                if (gamma[datasetIndex] <= 0) {
                    JOptionPane.showMessageDialog(this, "Invalid parameter: " + gamma[datasetIndex], "Error", JOptionPane.ERROR_MESSAGE);
                    return null;
                }
            } catch (Exception e) {
                JOptionPane.showMessageDialog(this, "Invalid parameter: " + gammaNumberField.getText(), "Error", JOptionPane.ERROR_MESSAGE);
                return null;
            }
        }
        gammaNumberField.setText(String.format("%.4f", gamma[datasetIndex]));

        long clock = System.currentTimeMillis();
        PCA pca = new PCA(data, true);
        System.out.format("Learn PCA from %d samples in %dms\n", data.length, System.currentTimeMillis() - clock);

        pca.setProjection(2);
        double[][] y = pca.project(data);

        PlotCanvas plot = new PlotCanvas(Math.colMin(y), Math.colMax(y));
        if (names != null) {
            plot.points(y, names);
        } else if (dataset[datasetIndex].responseAttribute() != null) {
            int[] labels = dataset[datasetIndex].toArray(new int[dataset[datasetIndex].size()]);
            for (int i = 0; i < y.length; i++) {
                plot.point(pointLegend, Palette.COLORS[labels[i]], y[i]);
            }
        } else {
            plot.points(y, pointLegend);
        }

        plot.setTitle("PCA");
        pane.add(plot);

        pca.setProjection(3);
        y = pca.project(data);

        plot = new PlotCanvas(Math.colMin(y), Math.colMax(y));
        if (names != null) {
            plot.points(y, names);
        } else if (dataset[datasetIndex].responseAttribute() != null) {
            int[] labels = dataset[datasetIndex].toArray(new int[dataset[datasetIndex].size()]);
            for (int i = 0; i < y.length; i++) {
                plot.point(pointLegend, Palette.COLORS[labels[i]], y[i]);
            }
        } else {
            plot.points(y, pointLegend);
        }

        plot.setTitle("PCA");
        pane.add(plot);

        KPCA<double[]> kpca = new KPCA<>(data, new GaussianKernel(gamma[datasetIndex]), 2);

        y = kpca.getCoordinates();
        plot = new PlotCanvas(Math.colMin(y), Math.colMax(y));
        if (names != null) {
            plot.points(y, names);
        } else if (dataset[datasetIndex].responseAttribute() != null) {
            int[] labels = dataset[datasetIndex].toArray(new int[dataset[datasetIndex].size()]);
            for (int i = 0; i < y.length; i++) {
                plot.point(pointLegend, Palette.COLORS[labels[i]], y[i]);
            }
        } else {
            plot.points(y, pointLegend);
        }

        plot.setTitle("KPCA");
        pane.add(plot);

        clock = System.currentTimeMillis();
        kpca = new KPCA<>(data, new GaussianKernel(gamma[datasetIndex]), 3);
        System.out.format("Learn KPCA from %d samples in %dms\n", data.length, System.currentTimeMillis() - clock);

        y = kpca.getCoordinates();
        plot = new PlotCanvas(Math.colMin(y), Math.colMax(y));
        if (names != null) {
            plot.points(y, names);
        } else if (dataset[datasetIndex].responseAttribute() != null) {
            int[] labels = dataset[datasetIndex].toArray(new int[dataset[datasetIndex].size()]);
            for (int i = 0; i < y.length; i++) {
                plot.point(pointLegend, Palette.COLORS[labels[i]], y[i]);
            }
        } else {
            plot.points(y, pointLegend);
        }

        plot.setTitle("KPCA");
        pane.add(plot);

        return pane;
    }

    @Override
    public String toString() {
        return "Kernel Principal Component Analysis";
    }

    public static void main(String argv[]) {
        KPCADemo demo = new KPCADemo();
        JFrame f = new JFrame("Kernel Principal Component Analysis");
        f.setSize(new Dimension(1000, 1000));
        f.setLocationRelativeTo(null);
        f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        f.getContentPane().add(demo);
        f.setVisible(true);
    }
}
