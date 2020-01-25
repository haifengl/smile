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

import java.util.Arrays;
import java.awt.Dimension;
import javax.swing.JComponent;
import javax.swing.JFrame;

import smile.math.MathEx;
import smile.vq.GrowingNeuralGas;
import smile.vq.hebb.Edge;
import smile.vq.hebb.Neuron;
import smile.plot.swing.PlotCanvas;
import smile.plot.swing.ScatterPlot;

/**
 *
 * @author Haifeng Li
 */
@SuppressWarnings("serial")
public class GrowingNeuralGasDemo extends VQDemo {
    public GrowingNeuralGasDemo() {
    }

    @Override
    public JComponent learn() {
        PlotCanvas plot = ScatterPlot.plot(dataset[datasetIndex], pointLegend);

        int period = dataset[datasetIndex].length / 10;
        Thread thread = new Thread(() -> {
            try {
                Thread.sleep(1000);
            } catch (Exception e) {
                e.printStackTrace();
            }

            GrowingNeuralGas gas = new GrowingNeuralGas(2);

            for (int i = 0, k = 0; i < epochs; i++) {
                for (int j : MathEx.permutate(dataset[datasetIndex].length)) {
                    gas.update(dataset[datasetIndex][j]);

                    if (++k % period == 0) {
                        plot.clear();
                        plot.points(dataset[datasetIndex], pointLegend);
                        Neuron[] neurons = gas.neurons();
                        double[][] w = Arrays.stream(neurons).map(neuron -> neuron.w).toArray(double[][]::new);
                        plot.points(w, '@');

                        for (Neuron neuron : neurons) {
                            for (Edge e : neuron.edges) {
                                plot.line(neuron.w, e.neighbor.w);
                            }
                        }
                        plot.repaint();

                        try {
                            Thread.sleep(100);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }
                System.out.format("%s epoch finishes%n", smile.util.Strings.ordinal(i+1));
            }
        });
        thread.start();

        return plot;
    }

    @Override
    public String toString() {
        return "Growing Neural Gas";
    }

    public static void main(String argv[]) {
        GrowingNeuralGasDemo demo = new GrowingNeuralGasDemo();
        JFrame f = new JFrame("Growing Neural Gas");
        f.setSize(new Dimension(1000, 1000));
        f.setLocationRelativeTo(null);
        f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        f.getContentPane().add(demo);
        f.setVisible(true);
    }
}
