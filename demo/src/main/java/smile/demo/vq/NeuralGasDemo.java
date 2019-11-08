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

package smile.demo.vq;

import java.awt.Dimension;
import javax.swing.*;

import smile.math.MathEx;
import smile.vq.LearningRate;
import smile.vq.NeuralGas;
import smile.plot.PlotCanvas;
import smile.plot.ScatterPlot;
import smile.vq.NeuralMap;

/**
 *
 * @author Haifeng Li
 */
@SuppressWarnings("serial")
public class NeuralGasDemo extends VQDemo {
    static int neuronNumber = 200;
    private JTextField neuronField = new JTextField(Integer.toString(neuronNumber), 5);

    public NeuralGasDemo() {
        optionPane.add(new JLabel("Neurons:"));
        optionPane.add(neuronField);
    }

    @Override
    public JComponent learn() {
        try {
            neuronNumber = Integer.parseInt(neuronField.getText().trim());
            if (neuronNumber < 1) {
                JOptionPane.showMessageDialog(this, "Invalid number of neurons: " + neuronNumber, "Error", JOptionPane.ERROR_MESSAGE);
                return null;
            }
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Invalid width: " + neuronField.getText(), "Error", JOptionPane.ERROR_MESSAGE);
            return null;
        }

        long clock = System.currentTimeMillis();
        int epochs = 20;
        NeuralGas gas = new NeuralGas(NeuralGas.random(neuronNumber, dataset[datasetIndex]),
                LearningRate.exp(3, dataset[datasetIndex].length * epochs / 10),
                LearningRate.exp(30, dataset[datasetIndex].length * epochs / 1.25),
                LearningRate.exp(20, dataset[datasetIndex].length * epochs / -2.5));
        for (int i = 0; i < epochs; i++) {
            for (int j : MathEx.permutate(dataset[datasetIndex].length)) {
                gas.update(dataset[datasetIndex][j]);
            }
        }

        System.out.format("Train Neural Gas with %d samples for %d epochs in %dms\n", dataset[datasetIndex].length, epochs, System.currentTimeMillis()-clock);

        PlotCanvas plot = ScatterPlot.plot(dataset[datasetIndex], pointLegend);

        double[][] neurons = gas.neurons();
        smile.graph.Graph graph = gas.network();
        for (int i = 0; i < neuronNumber; i++) {
            for (smile.graph.Graph.Edge e : graph.getEdges(i)) {
                plot.line(neurons[i], neurons[e.v2]);

            }
        }
        plot.points(neurons, '@');

        return plot;
    }

    @Override
    public String toString() {
        return "Neural Gas";
    }

    public static void main(String argv[]) {
        VQDemo demo = new NeuralGasDemo();
        JFrame f = new JFrame("Neural Gas");
        f.setSize(new Dimension(1000, 1000));
        f.setLocationRelativeTo(null);
        f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        f.getContentPane().add(demo);
        f.setVisible(true);
    }
}
