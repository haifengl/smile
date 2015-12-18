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

import smile.clustering.GMeans;
import smile.plot.Palette;
import smile.plot.PlotCanvas;
import smile.plot.ScatterPlot;

/**
 *
 * @author Haifeng Li
 */
@SuppressWarnings("serial")
public class GMeansDemo extends ClusteringDemo {
    JTextField maxClusterNumberField;
    int maxClusterNumber = 50;

    public GMeansDemo() {
        // Remove K TextFile
        optionPane.remove(optionPane.getComponentCount() - 1);
        optionPane.remove(optionPane.getComponentCount() - 1);

        maxClusterNumberField = new JTextField(Integer.toString(maxClusterNumber), 5);
        optionPane.add(new JLabel("Max K:"));
        optionPane.add(maxClusterNumberField);
    }

    @Override
    public JComponent learn() {
        try {
            maxClusterNumber = Integer.parseInt(maxClusterNumberField.getText().trim());
            if (maxClusterNumber < 2) {
                JOptionPane.showMessageDialog(this, "Invalid Max K: " + maxClusterNumber, "Error", JOptionPane.ERROR_MESSAGE);
                return null;
            }
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Invalid Max K: " + maxClusterNumberField.getText(), "Error", JOptionPane.ERROR_MESSAGE);
            return null;
        }

        long clock = System.currentTimeMillis();
        GMeans gmeans = new GMeans(dataset[datasetIndex], maxClusterNumber);
        System.out.format("G-Means clusterings %d samples in %dms\n", dataset[datasetIndex].length, System.currentTimeMillis()-clock);

        PlotCanvas plot = ScatterPlot.plot(gmeans.centroids(), '@');
        for (int k = 0; k < gmeans.getNumClusters(); k++) {
            if (gmeans.getClusterSize()[k] > 0) {
                double[][] cluster = new double[gmeans.getClusterSize()[k]][];
                for (int i = 0, j = 0; i < dataset[datasetIndex].length; i++) {
                    if (gmeans.getClusterLabel()[i] == k) {
                        cluster[j++] = dataset[datasetIndex][i];
                    }
                }

                plot.points(cluster, pointLegend, Palette.COLORS[k % Palette.COLORS.length]);
            }
        }
        plot.points(gmeans.centroids(), '@');
        return plot;
    }

    @Override
    public String toString() {
        return "G-Means";
    }

    public static void main(String argv[]) {
        ClusteringDemo demo = new GMeansDemo();
        JFrame f = new JFrame("G-Means");
        f.setSize(new Dimension(1000, 1000));
        f.setLocationRelativeTo(null);
        f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        f.getContentPane().add(demo);
        f.setVisible(true);
    }
}
