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

import smile.classification.SVM;
import smile.math.MathEx;
import smile.math.kernel.GaussianKernel;

/**
 *
 * @author Haifeng Li
 */
@SuppressWarnings("serial")
public class SVMDemo extends ClassificationDemo {
    private double gamma = 1.0;
    private JTextField gammaField;
    private double C = 1.0;
    private JTextField cField;

    /**
     * Constructor.
     */
    public SVMDemo() {
        gammaField = new JTextField(Double.toString(gamma), 5);
        optionPane.add(new JLabel("\u02E0:"));
        optionPane.add(gammaField);
        cField = new JTextField(Double.toString(C), 5);
        optionPane.add(new JLabel("C:"));
        optionPane.add(cField);
    }

    @Override
    public double[][] learn(double[] x, double[] y) {
        try {
            gamma = Double.parseDouble(gammaField.getText().trim());
            if (gamma <= 0) {
                JOptionPane.showMessageDialog(this, "Invalid \u02E0: " + gamma, "Error", JOptionPane.ERROR_MESSAGE);
                return null;
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Invalid \u02E0: " + gammaField.getText(), "Error", JOptionPane.ERROR_MESSAGE);
            return null;
        }

        try {
            C = Double.parseDouble(cField.getText().trim());
            if (C < 0) {
                JOptionPane.showMessageDialog(this, "Invalid C: " + C, "Error", JOptionPane.ERROR_MESSAGE);
                return null;
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Invalid C: " + cField.getText(), "Error", JOptionPane.ERROR_MESSAGE);
            return null;
        }

        double[][] data = formula.x(dataset[datasetIndex]).toArray();
        int[] label = formula.y(dataset[datasetIndex]).toIntArray();
        int n = label.length;
        int[] y2 = new int[n];
        for (int i = 0; i < n; i++) {
            y2[i] = label[i] * 2 - 1;
        }

        GaussianKernel kernel = new GaussianKernel(gamma);
        SVM<double[]> svm = SVM.fit(data, y2, kernel, C, 1E-3);

        int[] pred = new int[label.length];
        for (int i = 0; i < label.length; i++) {
            pred[i] = (svm.predict(data[i]) + 1) / 2;
        }
        double trainError = error(label, pred);

        System.out.format("training error = %.2f%%\n", 100*trainError);

        double[][] z = new double[y.length][x.length];
        for (int i = 0; i < y.length; i++) {
            for (int j = 0; j < x.length; j++) {
                double[] p = {x[j], y[i]};
                z[i][j] = (svm.predict(p) + 1) / 2;
            }
        }

        return z;
    }

    @Override
    public String toString() {
        return "Support Vector Machines";
    }

    public static void main(String argv[]) {
        ClassificationDemo demo = new SVMDemo();
        JFrame f = new JFrame("Support Vector Machines");
        f.setSize(new Dimension(1000, 1000));
        f.setLocationRelativeTo(null);
        f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        f.getContentPane().add(demo);
        f.setVisible(true);
    }
}
