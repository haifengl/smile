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

package smile.demo.manifold;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridLayout;

import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;

import smile.graph.Graph;
import smile.plot.swing.PlotCanvas;
import smile.manifold.LaplacianEigenmap;
import smile.math.MathEx;

/**
 * 
 * @author Haifeng Li
 */
@SuppressWarnings("serial")
public class LaplacianEigenmapDemo extends ManifoldDemo {

    private double sigma = -1;
    private JTextField sigmaField;

    public LaplacianEigenmapDemo() {
        sigmaField = new JTextField(Double.toString(sigma), 5);
        optionPane.add(new JLabel("t:"));
        optionPane.add(sigmaField);
    }

    @Override
    public JComponent learn() {
        JPanel pane = new JPanel(new GridLayout(1, 2));

        try {
            sigma = Double.parseDouble(sigmaField.getText().trim());
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Invalid t: " + sigmaField.getText(), "Error", JOptionPane.ERROR_MESSAGE);
            return pane;
        }
        sigmaField.setEnabled(false);

        double[][] data = dataset[datasetIndex].toArray();
        if (data.length > 1000) {
            double[][] x = new double[1000][];
            for (int i = 0; i < 1000; i++) {
                x[i] = data[i];
            }
            data = x;
        }

        long clock = System.currentTimeMillis();
        LaplacianEigenmap eigenmap = LaplacianEigenmap.of(data, k, 2, sigma);
        System.out.format("Learn Laplacian Eigenmap from %d samples in %dms\n", data.length, System.currentTimeMillis() - clock);

        double[][] y = eigenmap.coordinates;

        PlotCanvas plot = new PlotCanvas(MathEx.colMin(y), MathEx.colMax(y));
        plot.points(y, 'o', Color.RED);

        int n = y.length;
        Graph graph = eigenmap.graph;
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < i; j++) {
                if (graph.hasEdge(i, j)) {
                    plot.line(y[i], y[j]);
                }
            }
        }

        plot.setTitle("Laplacian Eigenmap");
        pane.add(plot);

        sigmaField.setEnabled(true);
        return pane;
    }

    @Override
    public String toString() {
        return "Laplacian Eigenmap";
    }

    public static void main(String argv[]) {
        LaplacianEigenmapDemo demo = new LaplacianEigenmapDemo();
        JFrame f = new JFrame("Laplacian Eigenmap");
        f.setSize(new Dimension(1000, 1000));
        f.setLocationRelativeTo(null);
        f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        f.getContentPane().add(demo);
        f.setVisible(true);
    }
}
