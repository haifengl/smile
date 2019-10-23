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

import smile.base.mlp.Layer;
import smile.base.mlp.OutputFunction;
import smile.base.mlp.OutputLayer;
import smile.classification.MLP;
import smile.math.MathEx;

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

        double[][] data = formula.x(dataset[datasetIndex]).toArray();
        int[] label = formula.y(dataset[datasetIndex]).toIntArray();
        
        int k = MathEx.max(label) + 1;
        MLP net;
        if (k == 2) {
            net = new MLP(2, Layer.sigmoid(units), Layer.mle(1, OutputFunction.SIGMOID));
        } else {
            net = new MLP(2, Layer.sigmoid(units), Layer.mle(k, OutputFunction.SOFTMAX));
        }
        
        for (int i = 0; i < epochs; i++) {
            for (int j = 0; j < data.length; j++) {
                net.update(data[j], label[j]);
            }
        }

        int[] pred = new int[label.length];
        for (int i = 0; i < label.length; i++) {
            pred[i] = net.predict(data[i]);
        }
        double trainError = error(label, pred);

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
