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
import java.awt.GridLayout;

import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;

import smile.plot.Palette;
import smile.vq.LatticeNeighborhood;
import smile.vq.LearningRate;
import smile.vq.SOM;
import smile.math.MathEx;
import smile.plot.Hexmap;
import smile.plot.PlotCanvas;
import smile.plot.ScatterPlot;

/**
 *
 * @author Haifeng Li
 */
@SuppressWarnings("serial")
public class SOMDemo  extends VQDemo {
    JTextField widthField;
    JTextField heightField;
    int width = 10;
    int height = 10;

    public SOMDemo() {
        widthField = new JTextField(Integer.toString(width), 10);
        optionPane.add(new JLabel("Width:"));
        optionPane.add(widthField);

        heightField = new JTextField(Integer.toString(height), 10);
        optionPane.add(new JLabel("Height:"));
        optionPane.add(heightField);
    }

    @Override
    public JComponent learn() {
        try {
            width = Integer.parseInt(widthField.getText().trim());
            if (width < 1) {
                JOptionPane.showMessageDialog(this, "Invalid width: " + width, "Error", JOptionPane.ERROR_MESSAGE);
                return null;
            }
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Invalid width: " + widthField.getText(), "Error", JOptionPane.ERROR_MESSAGE);
            return null;
        }

        try {
            height = Integer.parseInt(heightField.getText().trim());
            if (height < 1) {
                JOptionPane.showMessageDialog(this, "Invalid height: " + height, "Error", JOptionPane.ERROR_MESSAGE);
                return null;
            }
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Invalid height: " + heightField.getText(), "Error", JOptionPane.ERROR_MESSAGE);
            return null;
        }

        long clock = System.currentTimeMillis();
        int epochs = 20;
        double[][][] lattice = SOM.lattice(width, height, dataset[datasetIndex]);
        SOM som = new SOM(lattice,
                LearningRate.inverse(0.85, dataset[datasetIndex].length * epochs / 10),
                LatticeNeighborhood.Gaussian(5, dataset[datasetIndex].length * epochs / 4));
        for (int i = 0; i < epochs; i++) {
            for (int j : MathEx.permutate(dataset[datasetIndex].length)) {
                som.update(dataset[datasetIndex][j]);
            }
        }
        System.out.format("Train SOM with %d samples for %d epochs in %dms\n", dataset[datasetIndex].length, epochs, System.currentTimeMillis()-clock);

        JPanel pane = new JPanel(new GridLayout(1, 2));
        PlotCanvas plot = ScatterPlot.plot(dataset[datasetIndex], pointLegend);
        plot.grid(som.neurons());
        plot.setTitle("SOM");
        pane.add(plot);

        double[][] umatrix = som.umatrix();
        plot = Hexmap.plot(umatrix, Palette.jet(256));
        plot.setTitle("U-Matrix");
        pane.add(plot);
        return pane;
    }

    @Override
    public String toString() {
        return "SOM";
    }

    public static void main(String argv[]) {
        VQDemo demo = new SOMDemo();
        JFrame f = new JFrame("SOM");
        f.setSize(new Dimension(1000, 1000));
        f.setLocationRelativeTo(null);
        f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        f.getContentPane().add(demo);
        f.setVisible(true);
    }
}
