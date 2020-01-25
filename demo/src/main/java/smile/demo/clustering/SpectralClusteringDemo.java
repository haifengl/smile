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

import smile.clustering.SpectralClustering;
import smile.plot.swing.Palette;
import smile.plot.swing.PlotCanvas;
import smile.plot.swing.ScatterPlot;

/**
 *
 * @author Haifeng Li
 */
@SuppressWarnings("serial")
public class SpectralClusteringDemo extends ClusteringDemo {
    JTextField gaussianWidthField;
    double gaussianWidth = 1;

    public SpectralClusteringDemo() {
        gaussianWidthField = new JTextField(Double.toString(gaussianWidth), 5);
        optionPane.add(new JLabel("Width:"));
        optionPane.add(gaussianWidthField);
    }

    @Override
    public JComponent learn() {
        try {
            gaussianWidth = Double.parseDouble(gaussianWidthField.getText().trim());
            if (gaussianWidth <= 0) {
                JOptionPane.showMessageDialog(this, "Invalid Gaussian Width: " + gaussianWidth, "Error", JOptionPane.ERROR_MESSAGE);
                return null;
            }
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Invalid Gaussian Width: " + gaussianWidthField.getText(), "Error", JOptionPane.ERROR_MESSAGE);
            return null;
        }

        long clock = System.currentTimeMillis();
        SpectralClustering spectral = SpectralClustering.fit(dataset[datasetIndex], clusterNumber, gaussianWidth);
        System.out.format("Spectral Clustering clusterings %d samples in %dms\n", dataset[datasetIndex].length, System.currentTimeMillis()-clock);

        PlotCanvas plot = ScatterPlot.plot(dataset[datasetIndex], pointLegend);
        for (int k = 0; k < spectral.k; k++) {
                double[][] cluster = new double[spectral.size[k]][];
                for (int i = 0, j = 0; i < dataset[datasetIndex].length; i++) {
                    if (spectral.y[i] == k) {
                        cluster[j++] = dataset[datasetIndex][i];
                    }
                }

                plot.points(cluster, pointLegend, Palette.COLORS[k % Palette.COLORS.length]);
        }
        return plot;
    }

    @Override
    public String toString() {
        return "Spectral Clustering";
    }

    public static void main(String argv[]) {
        ClusteringDemo demo = new SpectralClusteringDemo();
        JFrame f = new JFrame("Spectral Clustering");
        f.setSize(new Dimension(1000, 1000));
        f.setLocationRelativeTo(null);
        f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        f.getContentPane().add(demo);
        f.setVisible(true);
    }
}
