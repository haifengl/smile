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

import smile.plot.Palette;
import smile.plot.PlotCanvas;
import smile.clustering.CLARANS;
import smile.math.distance.EuclideanDistance;
import smile.plot.ScatterPlot;

/**
 *
 * @author Haifeng Li
 */
@SuppressWarnings("serial")
public class CLARANSDemo extends ClusteringDemo {
    private static final String ERROR = "Error";
    JTextField numLocalField;
    int numLocal = 10;
    JTextField maxNeighborField;
    int maxNeighbor = 20;

    public CLARANSDemo() {
        numLocalField = new JTextField(Integer.toString(numLocal), 5);
        maxNeighborField = new JTextField(Integer.toString(maxNeighbor), 5);
        optionPane.add(new JLabel("NumLocal:"));
        optionPane.add(numLocalField);
        optionPane.add(new JLabel("MaxNeighbor:"));
        optionPane.add(maxNeighborField);
    }

    @Override
    public JComponent learn() {
        try {
            numLocal = Integer.parseInt(numLocalField.getText().trim());
            if (numLocal < 5) {
                JOptionPane.showMessageDialog(this, "Toll smal NumLocal: " + numLocal, ERROR, JOptionPane.ERROR_MESSAGE);
                return null;
            }
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Invalid NumLocal: " + numLocalField.getText(), ERROR, JOptionPane.ERROR_MESSAGE);
            return null;
        }

        try {
            maxNeighbor = Integer.parseInt(maxNeighborField.getText().trim());
            if (maxNeighbor < 5) {
                JOptionPane.showMessageDialog(this, "Too small MaxNeighbor: " + maxNeighbor, ERROR, JOptionPane.ERROR_MESSAGE);
                return null;
            }
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Invalid MaxNeighbor: " + maxNeighborField.getText(), ERROR, JOptionPane.ERROR_MESSAGE);
            return null;
        }

        long clock = System.currentTimeMillis();
        CLARANS<double[]> clarans = new CLARANS<>(dataset[datasetIndex], new EuclideanDistance(), clusterNumber, maxNeighbor, numLocal);
        System.out.format("CLARANS clusterings %d samples in %dms\n", dataset[datasetIndex].length, System.currentTimeMillis()-clock);

        PlotCanvas plot = ScatterPlot.plot(clarans.medoids(), '@');
        for (int k = 0; k < clusterNumber; k++) {
            if (clarans.getClusterSize()[k] > 0) {
                double[][] cluster = new double[clarans.getClusterSize()[k]][];
                for (int i = 0, j = 0; i < dataset[datasetIndex].length; i++) {
                    if (clarans.getClusterLabel()[i] == k) {
                        cluster[j++] = dataset[datasetIndex][i];
                    }
                }

                plot.points(cluster, pointLegend, Palette.COLORS[k % Palette.COLORS.length]);
            }
        }
        plot.points(clarans.medoids(), '@');
        return plot;
    }

    @Override
    public String toString() {
        return "CLARANS";
    }

    public static void main(String argv[]) {
        ClusteringDemo demo = new CLARANSDemo();
        JFrame f = new JFrame("CLARANS");
        f.setSize(new Dimension(1000, 1000));
        f.setLocationRelativeTo(null);
        f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        f.getContentPane().add(demo);
        f.setVisible(true);
    }
}
