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

import smile.classification.SVM;
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

        double[][] data = dataset[datasetIndex].toArray(new double[dataset[datasetIndex].size()][]);
        int[] label = dataset[datasetIndex].toArray(new int[dataset[datasetIndex].size()]);
        
        SVM<double[]> svm = new SVM<>(new GaussianKernel(gamma), C);
        svm.learn(data, label);
        svm.finish();
        
        for (int i = 0; i < label.length; i++) {
            label[i] = svm.predict(data[i]);
        }
        double trainError = error(label, label);

        System.out.format("training error = %.2f%%\n", 100*trainError);

        double[][] z = new double[y.length][x.length];
        for (int i = 0; i < y.length; i++) {
            for (int j = 0; j < x.length; j++) {
                double[] p = {x[j], y[i]};
                z[i][j] = svm.predict(p);
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
