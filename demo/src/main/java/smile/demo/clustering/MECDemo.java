/*
 * Copyright (c) 2010-2020 Haifeng Li. All rights reserved.
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
 */

package smile.demo.clustering;

import java.awt.Dimension;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JTextField;

import smile.clustering.MEC;
import smile.math.distance.EuclideanDistance;
import smile.plot.swing.Canvas;
import smile.plot.swing.ScatterPlot;

/**
 *
 * @author Haifeng Li
 */
@SuppressWarnings("serial")
public class MECDemo extends ClusteringDemo {
    JTextField rangeField;
    double range = 10;

    public MECDemo() {
        rangeField = new JTextField(Double.toString(range), 5);
        optionPane.add(new JLabel("Range:"));
        optionPane.add(rangeField);
    }

    @Override
    public JComponent learn() {
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
        MEC<double[]> mec = MEC.fit(dataset[datasetIndex], new EuclideanDistance(), clusterNumber, range);
        System.out.format("MEC clusterings %d samples in %dms\n", dataset[datasetIndex].length, System.currentTimeMillis()-clock);

        Canvas plot = ScatterPlot.of(dataset[datasetIndex], mec.y, mark).canvas();
        return plot.panel();
    }

    @Override
    public String toString() {
        return "Minimum Entropy Clustering";
    }

    public static void main(String[] args) {
        ClusteringDemo demo = new MECDemo();
        JFrame f = new JFrame("MEC");
        f.setSize(new Dimension(1000, 1000));
        f.setLocationRelativeTo(null);
        f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        f.getContentPane().add(demo);
        f.setVisible(true);
    }
}
