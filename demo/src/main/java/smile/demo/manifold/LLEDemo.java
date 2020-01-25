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
import javax.swing.JPanel;

import smile.graph.Graph;
import smile.plot.swing.PlotCanvas;
import smile.manifold.LLE;
import smile.math.MathEx;

/**
 *
 * @author Haifeng Li
 */
@SuppressWarnings("serial")
public class LLEDemo extends ManifoldDemo {

    public LLEDemo() {
    }

    @Override
    public JComponent learn() {
        JPanel pane = new JPanel(new GridLayout(1, 2));
        double[][] data = dataset[datasetIndex].toArray();
        if (data.length > 1000) {
            double[][] x = new double[1000][];
            for (int i = 0; i < 1000; i++)
                x[i] = data[i];
            data = x;
        }

        long clock = System.currentTimeMillis();
        LLE lle = LLE.of(data, k);
        System.out.format("Learn LLE from %d samples in %dms\n", data.length, System.currentTimeMillis() - clock);

        double[][] y = lle.coordinates;

        PlotCanvas plot = new PlotCanvas(MathEx.colMin(y), MathEx.colMax(y));
        plot.points(y, 'o', Color.RED);

        int n = y.length;
        Graph graph = lle.graph;
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < i; j++) {
                if (graph.hasEdge(i, j)) {
                    plot.line(y[i], y[j]);
                }
            }
        }

        plot.setTitle("LLE");
        pane.add(plot);

        return pane;
    }

    @Override
    public String toString() {
        return "Locally Linear Embedding";
    }

    public static void main(String argv[]) {
        LLEDemo demo = new LLEDemo();
        JFrame f = new JFrame("Locally Linear Embedding");
        f.setSize(new Dimension(1000, 1000));
        f.setLocationRelativeTo(null);
        f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        f.getContentPane().add(demo);
        f.setVisible(true);
    }
}
