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
import java.awt.GridLayout;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;

import smile.plot.swing.Palette;
import smile.plot.swing.PlotCanvas;
import smile.clustering.DBSCAN;
import smile.math.distance.EuclideanDistance;
import smile.plot.swing.ScatterPlot;

/**
 *
 * @author Haifeng Li
 */
@SuppressWarnings("serial")
public class DBSCANDemo extends ClusteringDemo {
    JTextField minPtsField;
    JTextField rangeField;
    int minPts = 10;
    double range = 10;

    public DBSCANDemo() {
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
        DBSCAN<double[]> dbscan = DBSCAN.fit(dataset[datasetIndex], new EuclideanDistance(), minPts, range);
        System.out.format("DBSCAN clusterings %d samples in %dms\n", dataset[datasetIndex].length, System.currentTimeMillis()-clock);

        JPanel pane = new JPanel(new GridLayout(1, 2));
        PlotCanvas plot = ScatterPlot.plot(dataset[datasetIndex], pointLegend);
        for (int k = 0; k < dbscan.k; k++) {
                double[][] cluster = new double[dbscan.size[k]][];
                for (int i = 0, j = 0; i < dataset[datasetIndex].length; i++) {
                    if (dbscan.y[i] == k) {
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
        return "DBSCAN";
    }

    public static void main(String argv[]) {
        ClusteringDemo demo = new DBSCANDemo();
        JFrame f = new JFrame("DBSCAN");
        f.setSize(new Dimension(1000, 1000));
        f.setLocationRelativeTo(null);
        f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        f.getContentPane().add(demo);
        f.setVisible(true);
    }
}
