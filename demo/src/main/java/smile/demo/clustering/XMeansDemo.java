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

import smile.clustering.XMeans;
import smile.plot.swing.Canvas;
import smile.plot.swing.ScatterPlot;

/**
 *
 * @author Haifeng Li
 */
@SuppressWarnings("serial")
public class XMeansDemo extends ClusteringDemo {
    JTextField maxClusterNumberField;
    int maxClusterNumber = 50;

    public XMeansDemo() {
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
        XMeans xmeans = XMeans.fit(dataset[datasetIndex], maxClusterNumber);
        System.out.format("X-Means clusterings %d samples in %dms\n", dataset[datasetIndex].length, System.currentTimeMillis()-clock);

        Canvas plot = ScatterPlot.of(dataset[datasetIndex], xmeans.y, mark).canvas();
        plot.add(ScatterPlot.of(xmeans.centroids, '@'));
        return plot.panel();
    }

    @Override
    public String toString() {
        return "X-Means";
    }

    public static void main(String[] args) {
        ClusteringDemo demo = new XMeansDemo();
        JFrame f = new JFrame("X-Means");
        f.setSize(new Dimension(1000, 1000));
        f.setLocationRelativeTo(null);
        f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        f.getContentPane().add(demo);
        f.setVisible(true);
    }
}
