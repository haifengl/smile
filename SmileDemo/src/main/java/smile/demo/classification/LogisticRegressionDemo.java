/*******************************************************************************
 * Copyright (c) 2010 Haifeng Li
 *   
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *  
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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

        double[][] data = dataset[datasetIndex].toArray(new double[dataset[datasetIndex].size()][]);
        int[] label = dataset[datasetIndex].toArray(new int[dataset[datasetIndex].size()]);
        
        LogisticRegression logit = new LogisticRegression(data, label, lambda);
        for (int i = 0; i < label.length; i++) {
            label[i] = logit.predict(data[i]);
        }
        double trainError = error(label, label);

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
