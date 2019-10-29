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

package smile.demo.classification;

import java.awt.Dimension;

import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JTextField;
import smile.base.rbf.RBF;
import smile.classification.RBFNetwork;

/**
 *
 * @author Haifeng Li
 */
@SuppressWarnings("serial")
public class RBFNetworkDemo extends ClassificationDemo {
    private int k = 10;
    private JTextField kField;

    /**
     * Constructor.
     */
    public RBFNetworkDemo() {
        kField = new JTextField(Integer.toString(k), 5);
        optionPane.add(new JLabel("K:"));
        optionPane.add(kField);
    }

    @Override
    public double[][] learn(double[] x, double[] y) {
        double[][] data = formula.x(dataset[datasetIndex]).toArray();
        int[] label = formula.y(dataset[datasetIndex]).toIntArray();
        
        try {
            k = Integer.parseInt(kField.getText().trim());
            if (k < 2 || k > data.length) {
                JOptionPane.showMessageDialog(this, "Invalid K: " + k, "Error", JOptionPane.ERROR_MESSAGE);
                return null;
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Invalid K: " + kField.getText(), "Error", JOptionPane.ERROR_MESSAGE);
            return null;
        }

        RBFNetwork<double[]> rbf = RBFNetwork.fit(data, label, RBF.fit(data, k));
        int[] pred = new int[label.length];
        for (int i = 0; i < label.length; i++) {
            pred[i] = rbf.predict(data[i]);
        }
        double trainError = error(label, pred);

        System.out.format("training error = %.2f%%\n", 100*trainError);

        double[][] z = new double[y.length][x.length];
        for (int i = 0; i < y.length; i++) {
            for (int j = 0; j < x.length; j++) {
                double[] p = {x[j], y[i]};
                z[i][j] = rbf.predict(p);
            }
        }

        return z;
    }

    @Override
    public String toString() {
        return "RBF Networks";
    }

    public static void main(String argv[]) {
        ClassificationDemo demo = new RBFNetworkDemo();
        JFrame f = new JFrame("RBF Networks");
        f.setSize(new Dimension(1000, 1000));
        f.setLocationRelativeTo(null);
        f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        f.getContentPane().add(demo);
        f.setVisible(true);
    }
}
