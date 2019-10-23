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

import smile.classification.LogisticRegression;

/**
 *
 * @author Haifeng Li
 */
@SuppressWarnings("serial")
public class LogisticRegressionDemo extends ClassificationDemo {
    private double lambda = 0.1;
    private JTextField lambdaField;

    /**
     * Constructor.
     */
    public LogisticRegressionDemo() {
        lambdaField = new JTextField(Double.toString(lambda), 5);
        optionPane.add(new JLabel("\u03BB:"));
        optionPane.add(lambdaField);
    }

    @Override
    public double[][] learn(double[] x, double[] y) {
        try {
            lambda = Double.parseDouble(lambdaField.getText().trim());
            if (lambda < 0.0) {
                JOptionPane.showMessageDialog(this, "Invalid \u03BB: " + lambda, "Error", JOptionPane.ERROR_MESSAGE);
                return null;
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Invalid \u03BB: " + lambdaField.getText(), "Error", JOptionPane.ERROR_MESSAGE);
            return null;
        }

        double[][] data = formula.x(dataset[datasetIndex]).toArray();
        int[] label = formula.y(dataset[datasetIndex]).toIntArray();
        
        LogisticRegression logit = LogisticRegression.fit(data, label, lambda, 1E-5, 500);
        int[] pred = new int[label.length];
        for (int i = 0; i < label.length; i++) {
            pred[i] = logit.predict(data[i]);
        }
        double trainError = error(label, pred);

        System.out.format("training error = %.2f%%\n", 100*trainError);

        double[][] z = new double[y.length][x.length];
        for (int i = 0; i < y.length; i++) {
            for (int j = 0; j < x.length; j++) {
                double[] p = {x[j], y[i]};
                z[i][j] = logit.predict(p);
            }
        }

        return z;
    }

    @Override
    public String toString() {
        return "Logistic Regression";
    }

    public static void main(String argv[]) {
        ClassificationDemo demo = new LogisticRegressionDemo();
        JFrame f = new JFrame("Logistic Regression");
        f.setSize(new Dimension(1000, 1000));
        f.setLocationRelativeTo(null);
        f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        f.getContentPane().add(demo);
        f.setVisible(true);
    }
}
