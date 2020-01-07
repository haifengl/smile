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

import smile.clustering.DENCLUE;
import smile.plot.swing.Palette;
import smile.plot.swing.PlotCanvas;
import smile.plot.swing.ScatterPlot;

/**
 *
 * @author Haifeng Li
 */
@SuppressWarnings("serial")
public class DENCLUEDemo  extends ClusteringDemo {
    JTextField kField;
    JTextField sigmaField;
    int k = 20;
    double sigma = 1;

    public DENCLUEDemo() {
        // Remove K TextFile
        optionPane.remove(optionPane.getComponentCount() - 1);

        kField = new JTextField(Integer.toString(k), 5);
        optionPane.add(kField);

        sigmaField = new JTextField(Double.toString(sigma), 5);
        optionPane.add(new JLabel("Sigma:"));
        optionPane.add(sigmaField);
    }

    @Override
    public JComponent learn() {
        try {
            k = Integer.parseInt(kField.getText().trim());
            if (k < 1) {
                JOptionPane.showMessageDialog(this, "Invalid K: " + k, "Error", JOptionPane.ERROR_MESSAGE);
                return null;
            }
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Invalid K: " + kField.getText(), "Error", JOptionPane.ERROR_MESSAGE);
            return null;
        }

        try {
            sigma = Double.parseDouble(sigmaField.getText().trim());
            if (sigma <= 0) {
                JOptionPane.showMessageDialog(this, "Invalid Sigma: " + sigma, "Error", JOptionPane.ERROR_MESSAGE);
                return null;
            }
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Invalid Sigma: " + sigmaField.getText(), "Error", JOptionPane.ERROR_MESSAGE);
            return null;
        }

        long clock = System.currentTimeMillis();
        DENCLUE denclue = DENCLUE.fit(dataset[datasetIndex], sigma, k);
        System.out.format("DENCLUE clusterings %d samples in %dms\n", dataset[datasetIndex].length, System.currentTimeMillis()-clock);
        System.out.println(denclue);

        JPanel pane = new JPanel(new GridLayout(1, 2));
        PlotCanvas plot = ScatterPlot.plot(dataset[datasetIndex], pointLegend);
        for (int l = 0; l < denclue.k; l++) {
                double[][] cluster = new double[denclue.size[l]][];
                for (int i = 0, j = 0; i < dataset[datasetIndex].length; i++) {
                    if (denclue.y[i] == l) {
                        cluster[j++] = dataset[datasetIndex][i];
                    }
                }

                plot.points(cluster, pointLegend, Palette.COLORS[l % Palette.COLORS.length]);
        }
        plot.points(denclue.attractors, '@');
        pane.add(plot);

        return pane;
    }

    @Override
    public String toString() {
        return "DENCLUE";
    }

    public static void main(String argv[]) {
        ClusteringDemo demo = new DENCLUEDemo();
        JFrame f = new JFrame("DENCLUE");
        f.setSize(new Dimension(1000, 1000));
        f.setLocationRelativeTo(null);
        f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        f.getContentPane().add(demo);
        f.setVisible(true);
    }
}
