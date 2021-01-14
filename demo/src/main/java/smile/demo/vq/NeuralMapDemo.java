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

package smile.demo.vq;

import java.awt.Dimension;
import java.util.Arrays;
import javax.swing.*;

import smile.math.MathEx;
import smile.plot.swing.LinePlot;
import smile.vq.hebb.Edge;
import smile.vq.hebb.Neuron;
import smile.vq.NeuralMap;
import smile.plot.swing.Canvas;
import smile.plot.swing.ScatterPlot;

/**
 *
 * @author Haifeng Li
 */
@SuppressWarnings("serial")
public class NeuralMapDemo extends VQDemo {

    JTextField TNumberField;
    double T = 20;

    public NeuralMapDemo() {
        TNumberField = new JTextField(Double.toString(T), 5);
        optionPane.add(new JLabel("T:"));
        optionPane.add(TNumberField);
    }

    @Override
    public JComponent learn() {
        try {
            T = Double.parseDouble(TNumberField.getText().trim());
            if (T <= 0) {
                JOptionPane.showMessageDialog(this, "Invalid T: " + T, "Error", JOptionPane.ERROR_MESSAGE);
                return null;
            }
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Invalid T: " + TNumberField.getText(), "Error", JOptionPane.ERROR_MESSAGE);
            return null;
        }

        Canvas plot = ScatterPlot.of(dataset[datasetIndex], pointLegend).canvas();

        JPanel panel = plot.panel();
        int period = dataset[datasetIndex].length / 10;
        Thread thread = new Thread(() -> {
            try {
                Thread.sleep(1000);
            } catch (Exception e) {
                e.printStackTrace();
            }

            NeuralMap cortex = new NeuralMap(T, learningRate, learningRate/5, 50, 0.995);

            for (int i = 0, k = 0; i < epochs; i++) {
                for (int j : MathEx.permutate(dataset[datasetIndex].length)) {
                    cortex.update(dataset[datasetIndex][j]);

                    if (++k % period == 0) {
                        plot.clear();
                        plot.add(ScatterPlot.of(dataset[datasetIndex], pointLegend));
                        Neuron[] neurons = cortex.neurons();
                        double[][] w = Arrays.stream(neurons).map(neuron -> neuron.w).toArray(double[][]::new);
                        plot.add(ScatterPlot.of(w, '@'));

                        double[][] lines = Arrays.stream(neurons).flatMap(neuron -> neuron.edges.stream().map(edge -> new double[][]{neuron.w, edge.neighbor.w})).toArray(double[][]::new);
                        plot.add(LinePlot.of(lines));

                        panel.repaint();

                        try {
                            Thread.sleep(100);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }

                cortex.clear(1E-5);
                System.out.format("%s epoch finishes%n", smile.util.Strings.ordinal(i+1));
            }
        });
        thread.start();

        return panel;
    }

    @Override
    public String toString() {
        return "Neural Map";
    }

    public static void main(String[] args) {
        NeuralMapDemo demo = new NeuralMapDemo();
        JFrame f = new JFrame("Neural Map");
        f.setSize(new Dimension(1000, 1000));
        f.setLocationRelativeTo(null);
        f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        f.getContentPane().add(demo);
        f.setVisible(true);
    }
}
