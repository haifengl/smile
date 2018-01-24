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

import smile.classification.NeuralNetwork;
import smile.math.Math;

/**
 *
 * @author Haifeng Li
 */
@SuppressWarnings("serial")
public class NeuralNetworkDemo extends ClassificationDemo {
    private int units = 10;
    private int epochs = 20;
    private JTextField unitsField;
    private JTextField epochsField;

    /**
     * Constructor.
     */
    public NeuralNetworkDemo() {
        unitsField = new JTextField(Integer.toString(units), 5);
        optionPane.add(new JLabel("Hidden Neurons:"));
        optionPane.add(unitsField);
        epochsField = new JTextField(Integer.toString(epochs), 5);
        optionPane.add(new JLabel("Epochs:"));
        optionPane.add(epochsField);
    }

    @Override
    public double[][] learn(double[] x, double[] y) {
        try {
            units = Integer.parseInt(unitsField.getText().trim());
            if (units <= 0) {
                JOptionPane.showMessageDialog(this, "Invalid number of hidden neurons: " + units, "Error", JOptionPane.ERROR_MESSAGE);
                return null;
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Invalid number of hidden neurons: " + unitsField.getText(), "Error", JOptionPane.ERROR_MESSAGE);
            return null;
        }

        try {
            epochs = Integer.parseInt(epochsField.getText().trim());
            if (epochs <= 0) {
                JOptionPane.showMessageDialog(this, "Invalid number of epochs: " + epochs, "Error", JOptionPane.ERROR_MESSAGE);
                return null;
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Invalid number of epochs: " + epochsField.getText(), "Error", JOptionPane.ERROR_MESSAGE);
            return null;
        }

        double[][] data = dataset[datasetIndex].toArray(new double[dataset[datasetIndex].size()][]);
        int[] label = dataset[datasetIndex].toArray(new int[dataset[datasetIndex].size()]);
        
        int k = Math.max(label) + 1;
        NeuralNetwork net = null;
        if (k == 2) {
            net = new NeuralNetwork(NeuralNetwork.ErrorFunction.CROSS_ENTROPY, NeuralNetwork.ActivationFunction.LOGISTIC_SIGMOID, data[0].length, units, 1);
        } else {
            net = new NeuralNetwork(NeuralNetwork.ErrorFunction.CROSS_ENTROPY, NeuralNetwork.ActivationFunction.SOFTMAX, data[0].length, units, k);
        }
        
        for (int i = 0; i < epochs; i++) {
            net.learn(data, label);
        }
        
        for (int i = 0; i < label.length; i++) {
            label[i] = net.predict(data[i]);
        }
        double trainError = error(label, label);

        System.out.format("training error = %.2f%%\n", 100*trainError);

        double[][] z = new double[y.length][x.length];
        for (int i = 0; i < y.length; i++) {
            for (int j = 0; j < x.length; j++) {
                double[] p = {x[j], y[i]};
                z[i][j] = net.predict(p);
            }
        }

        return z;
    }

    @Override
    public String toString() {
        return "Neural Network";
    }

    public static void main(String argv[]) {
        ClassificationDemo demo = new NeuralNetworkDemo();
        JFrame f = new JFrame("Neural Network");
        f.setSize(new Dimension(1000, 1000));
        f.setLocationRelativeTo(null);
        f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        f.getContentPane().add(demo);
        f.setVisible(true);
    }
}
