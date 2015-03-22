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
import smile.manifold.IsoMap;
import smile.math.Math;

/**
 *
 * @author Haifeng Li
 */
@SuppressWarnings("serial")
public class IsoMapDemo extends ManifoldDemo {

    public IsoMapDemo() {
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
        IsoMap isomap = new IsoMap(data, 2, k);
        System.out.format("Learn IsoMap from %d samples in %dms\n", data.length, System.currentTimeMillis() - clock);

        double[][] y = isomap.getCoordinates();

        PlotCanvas plot = new PlotCanvas(Math.colMin(y), Math.colMax(y));
        plot.points(y, 'o', Color.RED);

        int n = y.length;
        Graph graph = isomap.getNearestNeighborGraph();
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < i; j++) {
                if (graph.hasEdge(i, j)) {
                    plot.line(y[i], y[j]);
                }
            }
        }

        plot.setTitle("IsoMap");
        pane.add(plot);

        return pane;
    }

    @Override
    public String toString() {
        return "IsoMap";
    }

    public static void main(String argv[]) {
        IsoMapDemo demo = new IsoMapDemo();
        JFrame f = new JFrame("IsoMap");
        f.setSize(new Dimension(1000, 1000));
        f.setLocationRelativeTo(null);
        f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        f.getContentPane().add(demo);
        f.setVisible(true);
    }
}
