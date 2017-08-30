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

package smile.demo.projection;

import java.awt.Dimension;
import java.awt.GridLayout;

import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;

import smile.plot.Palette;
import smile.plot.PlotCanvas;
import smile.projection.PCA;
import smile.math.Math;

/**
 *
 * @author Haifeng Li
 */
@SuppressWarnings("serial")
public class PCADemo extends ProjectionDemo {
    JComboBox<String> corBox;

    public PCADemo() {
        corBox = new JComboBox<>();
        corBox.addItem("Covariance");
        corBox.addItem("Correlation");
        corBox.setSelectedIndex(0);

        optionPane.add(new JLabel("Scaling:"));
        optionPane.add(corBox);
    }

    @Override
    public JComponent learn() {
        double[][] data = dataset[datasetIndex].toArray(new double[dataset[datasetIndex].size()][]);
        String[] names = dataset[datasetIndex].toArray(new String[dataset[datasetIndex].size()]);
        if (names[0] == null) {
            names = null;
        }
        boolean cor = corBox.getSelectedIndex() != 0;

        long clock = System.currentTimeMillis();
        PCA pca = new PCA(data, cor);
        System.out.format("Learn PCA from %d samples in %dms\n", data.length, System.currentTimeMillis()-clock);

        JPanel pane = new JPanel(new GridLayout(1, 2));
        PlotCanvas scree = PlotCanvas.screeplot(pca);
        scree.setTitle("Variance");
        pane.add(scree);

        pca.setProjection(3);
        double[][] y = pca.project(data);

        PlotCanvas plot = new PlotCanvas(Math.colMin(y), Math.colMax(y));
        if (names != null) {
            plot.points(y, names);
        } else if (dataset[datasetIndex].responseAttribute() != null) {
            int[] labels = dataset[datasetIndex].toArray(new int[dataset[datasetIndex].size()]);
            for (int i = 0; i < y.length; i++) {
                plot.point(pointLegend, Palette.COLORS[labels[i]], y[i]);
            }
        } else {
            plot.points(y, pointLegend);
        }

        plot.setTitle("Scatter Plot");
        pane.add(plot);
        return pane;
    }

    @Override
    public String toString() {
        return "Principal Component Analysis";
    }

    public static void main(String argv[]) {
        PCADemo demo = new PCADemo();
        JFrame f = new JFrame("Principal Component Analysis");
        f.setSize(new Dimension(1000, 1000));
        f.setLocationRelativeTo(null);
        f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        f.getContentPane().add(demo);
        f.setVisible(true);
    }
}
