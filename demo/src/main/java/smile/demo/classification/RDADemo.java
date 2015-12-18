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

import smile.classification.RDA;

/**
 *
 * @author Haifeng Li
 */
@SuppressWarnings("serial")
public class RDADemo extends ClassificationDemo {
    private double alpha = 0.5;
    private JTextField alphaField;

    /**
     * Constructor.
     */
    public RDADemo() {
        alphaField = new JTextField(Double.toString(alpha), 5);
        optionPane.add(new JLabel("\u03B1:"));
        optionPane.add(alphaField);
    }

    @Override
    public double[][] learn(double[] x, double[] y) {
        try {
            alpha = Double.parseDouble(alphaField.getText().trim());
            if (alpha < 0 || alpha > 1) {
                JOptionPane.showMessageDialog(this, "Invalid \u03B1: " + alpha, "Error", JOptionPane.ERROR_MESSAGE);
                return null;
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Invalid \u03B1: " + alphaField.getText(), "Error", JOptionPane.ERROR_MESSAGE);
            return null;
        }

        double[][] data = dataset[datasetIndex].toArray(new double[dataset[datasetIndex].size()][]);
        int[] label = dataset[datasetIndex].toArray(new int[dataset[datasetIndex].size()]);
        
        RDA qda = new RDA(data, label, alpha);
        for (int i = 0; i < label.length; i++) {
            label[i] = qda.predict(data[i]);
        }
        double trainError = error(label, label);

        System.out.format("training error = %.2f%%\n", 100*trainError);

        double[][] z = new double[y.length][x.length];
        for (int i = 0; i < y.length; i++) {
            for (int j = 0; j < x.length; j++) {
                double[] p = {x[j], y[i]};
                z[i][j] = qda.predict(p);
            }
        }

        return z;
    }

    @Override
    public String toString() {
        return "Regularized Disiminant Analysis";
    }

    public static void main(String argv[]) {
        ClassificationDemo demo = new RDADemo();
        JFrame f = new JFrame("Regularized Disiminant Analysis");
        f.setSize(new Dimension(1000, 1000));
        f.setLocationRelativeTo(null);
        f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        f.getContentPane().add(demo);
        f.setVisible(true);
    }
}
