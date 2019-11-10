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
import java.util.List;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JTextField;
import smile.vq.NeuralMap;
import smile.plot.Palette;
import smile.plot.PlotCanvas;
import smile.plot.ScatterPlot;

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

        long clock = System.currentTimeMillis();
        NeuralMap cortex = new NeuralMap(2, T, 0.05, 0.0006, 5, 3);

        for (int i = 0; i < 5; i++) {
            for (double[] x : dataset[datasetIndex]) {
                cortex.update(x);
            }
        }

        cortex.purge(16);
        System.out.format("Cortex clusterings %d samples in %dms\n", dataset[datasetIndex].length, System.currentTimeMillis() - clock);

        List<NeuralMap.Neuron> nodes = cortex.neurons();
        double[][] x = new double[nodes.size()][];
        for (int i = 0; i < x.length; i++) {
            x[i] = nodes.get(i).w;
        }

        PlotCanvas plot = ScatterPlot.plot(x, '@');

        for (int i = 0; i < nodes.size(); i++) {
            NeuralMap.Neuron neuron = nodes.get(i);
            for (NeuralMap.Neuron neighbor : neuron.neighbors) {
                plot.line(neuron.w, neighbor.w);
            }
        }
        plot.points(x, '@');

        return plot;
    }

    @Override
    public String toString() {
        return "Neural Map";
    }

    public static void main(String argv[]) {
        NeuralMapDemo demo = new NeuralMapDemo();
        JFrame f = new JFrame("Neural Map");
        f.setSize(new Dimension(1000, 1000));
        f.setLocationRelativeTo(null);
        f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        f.getContentPane().add(demo);
        f.setVisible(true);
    }
}
