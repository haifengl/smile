/*******************************************************************************
 * Copyright (c) 2010-2019 Haifeng Li
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
 *******************************************************************************/

package smile.demo.clustering;

import java.awt.Dimension;

import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JTextField;

import smile.math.MathEx;
import smile.plot.swing.Palette;
import smile.plot.swing.PlotCanvas;
import smile.clustering.CLARANS;
import smile.plot.swing.ScatterPlot;

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
        CLARANS<double[]> clarans = CLARANS.fit(dataset[datasetIndex], MathEx::distance, clusterNumber, maxNeighbor);
        System.out.format("CLARANS clusterings %d samples in %dms\n", dataset[datasetIndex].length, System.currentTimeMillis()-clock);

        PlotCanvas plot = ScatterPlot.plot(clarans.centroids, '@');
        for (int k = 0; k < clusterNumber; k++) {
            if (clarans.size[k] > 0) {
                double[][] cluster = new double[clarans.size[k]][];
                for (int i = 0, j = 0; i < dataset[datasetIndex].length; i++) {
                    if (clarans.y[i] == k) {
                        cluster[j++] = dataset[datasetIndex][i];
                    }
                }

                plot.points(cluster, pointLegend, Palette.COLORS[k % Palette.COLORS.length]);
            }
        }
        plot.points(clarans.centroids, '@');
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
