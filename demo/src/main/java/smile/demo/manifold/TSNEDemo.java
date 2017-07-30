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

import java.awt.Dimension;
import java.awt.GridLayout;

import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JPanel;

import smile.plot.Palette;
import smile.plot.PlotCanvas;
import smile.manifold.TSNE;
import smile.demo.projection.ProjectionDemo;
import smile.math.Math;

/**
 *
 * @author Haifeng Li
 */
@SuppressWarnings("serial")
public class TSNEDemo extends ProjectionDemo {

    public TSNEDemo() {
    }

    @Override
    public JComponent learn() {
        JPanel pane = new JPanel(new GridLayout(1, 2));
        double[][] data = dataset[datasetIndex].toArray(new double[dataset[datasetIndex].size()][]);
        String[] names = dataset[datasetIndex].toArray(new String[dataset[datasetIndex].size()]);
        if (names[0] == null) {
            names = null;
        }

        long clock = System.currentTimeMillis();
        TSNE tsne = new TSNE(data, 2);
        System.out.format("Learn t-SNE from %d samples in %dms\n", data.length, System.currentTimeMillis() - clock);

        double[][] y = tsne.getCoordinates();

        PlotCanvas plot = new PlotCanvas(Math.colMin(y), Math.colMax(y));
        if (names != null) {
            plot.points(y, names);
        } else if (dataset[datasetIndex].response() != null) {
            int[] labels = dataset[datasetIndex].toArray(new int[dataset[datasetIndex].size()]);
            for (int i = 0; i < y.length; i++) {
                plot.point(pointLegend, Palette.COLORS[labels[i]], y[i]);
            }
        } else {
            plot.points(y, pointLegend);
        }

        plot.setTitle("tSNE");
        pane.add(plot);

        return pane;
    }

    @Override
    public String toString() {
        return "t-SNE";
    }

    public static void main(String argv[]) {
        TSNEDemo demo = new TSNEDemo();
        JFrame f = new JFrame("t-SNE");
        f.setSize(new Dimension(1000, 1000));
        f.setLocationRelativeTo(null);
        f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        f.getContentPane().add(demo);
        f.setVisible(true);
    }
}
