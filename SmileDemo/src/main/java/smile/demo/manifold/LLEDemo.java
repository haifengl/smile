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
package smile.demo.manifold;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridLayout;

import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JPanel;

import smile.graph.Graph;
import smile.plot.PlotCanvas;
import smile.manifold.LLE;
import smile.math.Math;

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
        double[][] data = dataset[datasetIndex].toArray(new double[dataset[datasetIndex].size()][]);        
        if (data.length > 1000) {
            double[][] x = new double[1000][];
            for (int i = 0; i < 1000; i++)
                x[i] = data[i];
            data = x;
        }

        long clock = System.currentTimeMillis();
        LLE lle = new LLE(data, 2, k);
        System.out.format("Learn LLE from %d samples in %dms\n", data.length, System.currentTimeMillis() - clock);

        double[][] y = lle.getCoordinates();

        PlotCanvas plot = new PlotCanvas(Math.colMin(y), Math.colMax(y));
        plot.points(y, 'o', Color.RED);

        int n = y.length;
        Graph graph = lle.getNearestNeighborGraph();
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
