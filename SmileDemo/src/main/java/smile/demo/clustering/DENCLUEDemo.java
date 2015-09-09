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

package smile.demo.clustering;

import java.awt.Dimension;
import java.awt.GridLayout;

import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;

import smile.clustering.DENCLUE;
import smile.plot.Palette;
import smile.plot.PlotCanvas;
import smile.plot.ScatterPlot;

/**
 *
 * @author Haifeng Li
 */
@SuppressWarnings("serial")
public class DENCLUEDemo  extends ClusteringDemo {
    JTextField kField;
    JTextField sigmaField;
    int k = 20;
    double sigma = 1;

    public DENCLUEDemo() {
        // Remove K TextFile
        optionPane.remove(optionPane.getComponentCount() - 1);

        kField = new JTextField(Integer.toString(k), 5);
        optionPane.add(kField);

        sigmaField = new JTextField(Double.toString(sigma), 5);
        optionPane.add(new JLabel("Sigma:"));
        optionPane.add(sigmaField);
    }

    @Override
    public JComponent learn() {
        try {
            k = Integer.parseInt(kField.getText().trim());
            if (k < 1) {
                JOptionPane.showMessageDialog(this, "Invalid K: " + k, "Error", JOptionPane.ERROR_MESSAGE);
                return null;
            }
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Invalid K: " + kField.getText(), "Error", JOptionPane.ERROR_MESSAGE);
            return null;
        }

        try {
            sigma = Double.parseDouble(sigmaField.getText().trim());
            if (sigma <= 0) {
                JOptionPane.showMessageDialog(this, "Invalid Sigma: " + sigma, "Error", JOptionPane.ERROR_MESSAGE);
                return null;
            }
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Invalid Sigma: " + sigmaField.getText(), "Error", JOptionPane.ERROR_MESSAGE);
            return null;
        }

        long clock = System.currentTimeMillis();
        DENCLUE denclue = new DENCLUE(dataset[datasetIndex], sigma, k);
        System.out.format("DENCLUE clusterings %d samples in %dms\n", dataset[datasetIndex].length, System.currentTimeMillis()-clock);

        JPanel pane = new JPanel(new GridLayout(1, 2));
        PlotCanvas plot = ScatterPlot.plot(dataset[datasetIndex], pointLegend);
        for (int l = 0; l < denclue.getNumClusters(); l++) {
                double[][] cluster = new double[denclue.getClusterSize()[l]][];
                for (int i = 0, j = 0; i < dataset[datasetIndex].length; i++) {
                    if (denclue.getClusterLabel()[i] == l) {
                        cluster[j++] = dataset[datasetIndex][i];
                    }
                }

                plot.points(cluster, pointLegend, Palette.COLORS[l % Palette.COLORS.length]);
        }
        plot.points(denclue.getDensityAttractors(), '@');
        pane.add(plot);

        return pane;
    }

    @Override
    public String toString() {
        return "DENCLUE";
    }

    public static void main(String argv[]) {
        ClusteringDemo demo = new DENCLUEDemo();
        JFrame f = new JFrame("DENCLUE");
        f.setSize(new Dimension(1000, 1000));
        f.setLocationRelativeTo(null);
        f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        f.getContentPane().add(demo);
        f.setVisible(true);
    }
}
