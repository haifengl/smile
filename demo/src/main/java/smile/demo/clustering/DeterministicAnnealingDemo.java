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

import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JTextField;

import smile.clustering.DeterministicAnnealing;
import smile.plot.Palette;
import smile.plot.PlotCanvas;
import smile.plot.ScatterPlot;

/**
 *
 * @author Haifeng Li
 */
@SuppressWarnings("serial")
public class DeterministicAnnealingDemo extends ClusteringDemo {

    JTextField alphaField;
    double alpha = 0.9;

    public DeterministicAnnealingDemo() {
        alphaField = new JTextField(Double.toString(alpha), 5);
        optionPane.add(new JLabel("alpha:"));
        optionPane.add(alphaField);
    }

    @Override
    public JComponent learn() {
        try {
            alpha = Double.parseDouble(alphaField.getText().trim());
            if (alpha <= 0 || alpha >= 1) {
                JOptionPane.showMessageDialog(this, "Invalid alpha: " + alpha, "Error", JOptionPane.ERROR_MESSAGE);
                return null;
            }
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Invalid K: " + alphaField.getText(), "Error", JOptionPane.ERROR_MESSAGE);
            return null;
        }

        long clock = System.currentTimeMillis();
        DeterministicAnnealing annealing = new DeterministicAnnealing(dataset[datasetIndex], clusterNumber, alpha);
        System.out.format("Deterministic Annealing clusterings %d samples in %dms\n", dataset[datasetIndex].length, System.currentTimeMillis()-clock);

        PlotCanvas plot = ScatterPlot.plot(annealing.centroids(), '@');
        for (int k = 0; k < clusterNumber; k++) {
            if (annealing.getClusterSize()[k] > 0) {
                double[][] cluster = new double[annealing.getClusterSize()[k]][];
                for (int i = 0, j = 0; i < dataset[datasetIndex].length; i++) {
                    if (annealing.getClusterLabel()[i] == k) {
                        cluster[j++] = dataset[datasetIndex][i];
                    }
                }

                plot.points(cluster, pointLegend, Palette.COLORS[k % Palette.COLORS.length]);
            }
        }
        plot.points(annealing.centroids(), '@');
        return plot;
    }

    @Override
    public String toString() {
        return "Deterministic Annealing";
    }

    public static void main(String argv[]) {
        ClusteringDemo demo = new DeterministicAnnealingDemo();
        JFrame f = new JFrame("Deterministic Annealing");
        f.setSize(new Dimension(1000, 1000));
        f.setLocationRelativeTo(null);
        f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        f.getContentPane().add(demo);
        f.setVisible(true);
    }
}
