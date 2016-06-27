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

import smile.plot.Palette;
import smile.plot.PlotCanvas;
import smile.clustering.DBScan;
import smile.math.distance.EuclideanDistance;
import smile.plot.ScatterPlot;

/**
 *
 * @author Haifeng Li
 */
@SuppressWarnings("serial")
public class DBScanDemo extends ClusteringDemo {
    JTextField minPtsField;
    JTextField rangeField;
    int minPts = 10;
    double range = 10;

    public DBScanDemo() {
        // Remove K TextFile
        optionPane.remove(optionPane.getComponentCount() - 1);
        optionPane.remove(optionPane.getComponentCount() - 1);

        minPtsField = new JTextField(Integer.toString(minPts), 5);
        optionPane.add(new JLabel("MinPts:"));
        optionPane.add(minPtsField);

        rangeField = new JTextField(Double.toString(range), 5);
        optionPane.add(new JLabel("Range:"));
        optionPane.add(rangeField);
    }

    @Override
    public JComponent learn() {
        try {
            minPts = Integer.parseInt(minPtsField.getText().trim());
            if (minPts < 1) {
                JOptionPane.showMessageDialog(this, "Invalid MinPts: " + minPts, "Error", JOptionPane.ERROR_MESSAGE);
                return null;
            }
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Invalid MinPts: " + minPtsField.getText(), "Error", JOptionPane.ERROR_MESSAGE);
            return null;
        }

        try {
            range = Double.parseDouble(rangeField.getText().trim());
            if (range <= 0) {
                JOptionPane.showMessageDialog(this, "Invalid Range: " + range, "Error", JOptionPane.ERROR_MESSAGE);
                return null;
            }
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Invalid range: " + rangeField.getText(), "Error", JOptionPane.ERROR_MESSAGE);
            return null;
        }

        long clock = System.currentTimeMillis();
        DBScan<double[]> dbscan = new DBScan<>(dataset[datasetIndex], new EuclideanDistance(), minPts, range);
        System.out.format("DBSCAN clusterings %d samples in %dms\n", dataset[datasetIndex].length, System.currentTimeMillis()-clock);

        JPanel pane = new JPanel(new GridLayout(1, 2));
        PlotCanvas plot = ScatterPlot.plot(dataset[datasetIndex], pointLegend);
        for (int k = 0; k < dbscan.getNumClusters(); k++) {
                double[][] cluster = new double[dbscan.getClusterSize()[k]][];
                for (int i = 0, j = 0; i < dataset[datasetIndex].length; i++) {
                    if (dbscan.getClusterLabel()[i] == k) {
                        cluster[j++] = dataset[datasetIndex][i];
                    }
                }

                plot.points(cluster, pointLegend, Palette.COLORS[k % Palette.COLORS.length]);
        }
        pane.add(plot);

        return pane;
    }

    @Override
    public String toString() {
        return "DBScan";
    }

    public static void main(String argv[]) {
        ClusteringDemo demo = new DBScanDemo();
        JFrame f = new JFrame("DBScan");
        f.setSize(new Dimension(1000, 1000));
        f.setLocationRelativeTo(null);
        f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        f.getContentPane().add(demo);
        f.setVisible(true);
    }
}
