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

import smile.clustering.DENCLUE;
import smile.plot.swing.Canvas;
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

        Canvas plot = ScatterPlot.of(dataset[datasetIndex], denclue.y, mark).canvas();
        plot.add(ScatterPlot.of(denclue.attractors, '@'));
        return plot.panel();
    }

    @Override
    public String toString() {
        return "DENCLUE";
    }

    public static void main(String[] args) {
        ClusteringDemo demo = new DENCLUEDemo();
        JFrame f = new JFrame("DENCLUE");
        f.setSize(new Dimension(1000, 1000));
        f.setLocationRelativeTo(null);
        f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        f.getContentPane().add(demo);
        f.setVisible(true);
    }
}
