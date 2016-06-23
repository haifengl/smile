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

package smile.demo.vq;

import java.awt.Dimension;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JTextField;
import smile.clustering.BIRCH;
import smile.clustering.Clustering;
import smile.plot.Palette;
import smile.plot.PlotCanvas;
import smile.plot.ScatterPlot;

/**
 *
 * @author Haifeng Li
 */
@SuppressWarnings("serial")
public class BIRCHDemo extends VQDemo {
    private static final String ERROR = "Error";
    JTextField BNumberField;
    int B = 5;

    JTextField TNumberField;
    double T = 0.5;

    JTextField minPtsNumberField;
    int minPts = 5;

    public BIRCHDemo() {
        BNumberField = new JTextField(Integer.toString(B), 5);
        TNumberField = new JTextField(Double.toString(T), 5);
        minPtsNumberField = new JTextField(Integer.toString(minPts), 5);
        optionPane.add(new JLabel("B:"));
        optionPane.add(BNumberField);
        optionPane.add(new JLabel("T:"));
        optionPane.add(TNumberField);
        optionPane.add(new JLabel("minPts:"));
        optionPane.add(minPtsNumberField);
    }

    @Override
    public JComponent learn() {
        try {
            B = Integer.parseInt(BNumberField.getText().trim());
            if (B < 2) {
                JOptionPane.showMessageDialog(this, "Invalid B: " + B, ERROR, JOptionPane.ERROR_MESSAGE);
                return null;
            }
        } catch (Exception e) {
            JOptionPane.showMessageDialog(null, "Invalid B: " + BNumberField.getText(), ERROR, JOptionPane.ERROR_MESSAGE);
            return null;
        }

        try {
            T = Double.parseDouble(TNumberField.getText().trim());
            if (T <= 0) {
                JOptionPane.showMessageDialog(this, "Invalid T: " + T, ERROR, JOptionPane.ERROR_MESSAGE);
                return null;
            }
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Invalid T: " + TNumberField.getText(), ERROR, JOptionPane.ERROR_MESSAGE);
            return null;
        }

        try {
            minPts = Integer.parseInt(minPtsNumberField.getText().trim());
            if (minPts < 0) {
                JOptionPane.showMessageDialog(this, "Invalid minPts: " + minPts, ERROR, JOptionPane.ERROR_MESSAGE);
                return null;
            }
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Invalid minPts: " + minPtsNumberField.getText(), ERROR, JOptionPane.ERROR_MESSAGE);
            return null;
        }

        long clock = System.currentTimeMillis();
        BIRCH birch = new BIRCH(2, B, T);
        for (int i = 0; i < dataset[datasetIndex].length; i++)
            birch.add(dataset[datasetIndex][i]);

        if (birch.partition(clusterNumber, minPts) < clusterNumber) {
            JOptionPane.showMessageDialog(this, "The number of non-outlier leaves is less than " + clusterNumber + ". Try larger T.", "ERROR", JOptionPane.ERROR_MESSAGE);
            return null;
        }

        int[] membership = new int[dataset[datasetIndex].length];
        int[] clusterSize = new int[clusterNumber];
        for (int i = 0; i < dataset[datasetIndex].length; i++) {
            membership[i] = birch.predict(dataset[datasetIndex][i]);
            if (membership[i] != Clustering.OUTLIER) {
                clusterSize[membership[i]]++;
            }
        }
        System.out.format("BIRCH clusterings %d samples in %dms\n", dataset[datasetIndex].length, System.currentTimeMillis()-clock);

        PlotCanvas plot = ScatterPlot.plot(birch.centroids(), '@');
        for (int k = 0; k < clusterNumber; k++) {
            if (clusterSize[k] > 0) {
                double[][] cluster = new double[clusterSize[k]][];
                for (int i = 0, j = 0; i < dataset[datasetIndex].length; i++) {
                    if (membership[i] == k) {
                        cluster[j++] = dataset[datasetIndex][i];
                    }
                }

                plot.points(cluster, pointLegend, Palette.COLORS[k % Palette.COLORS.length]);
            }
        }
        plot.points(birch.centroids(), '@');
        return plot;
    }

    @Override
    public String toString() {
        return "BIRCH";
    }

    public static void main(String argv[]) {
        BIRCHDemo demo = new BIRCHDemo();
        JFrame f = new JFrame("BIRCH");
        f.setSize(new Dimension(1000, 1000));
        f.setLocationRelativeTo(null);
        f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        f.getContentPane().add(demo);
        f.setVisible(true);
    }
}
