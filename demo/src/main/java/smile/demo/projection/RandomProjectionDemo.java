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

import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JPanel;

import smile.math.matrix.DenseMatrix;
import smile.plot.Palette;
import smile.plot.PlotCanvas;
import smile.projection.PCA;
import smile.projection.RandomProjection;
import smile.math.Math;

/**
 *
 * @author Haifeng Li
 */
@SuppressWarnings("serial")
public class RandomProjectionDemo extends ProjectionDemo {

    JCheckBox sparseBox;

    public RandomProjectionDemo() {
        sparseBox = new JCheckBox("Sparse Random Projection");
        optionPane.add(sparseBox);
    }

    @Override
    public JComponent learn() {
        JPanel pane = new JPanel(new GridLayout(2, 2));
        double[][] data = dataset[datasetIndex].toArray(new double[dataset[datasetIndex].size()][]);
        String[] names = dataset[datasetIndex].toArray(new String[dataset[datasetIndex].size()]);
        if (names[0] == null) {
            names = null;
        }

        long clock = System.currentTimeMillis();
        PCA pca = new PCA(data, true);
        System.out.format("Learn PCA from %d samples in %dms\n", data.length, System.currentTimeMillis() - clock);

        pca.setProjection(2);
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

        plot.setTitle("PCA");
        pane.add(plot);

        pca.setProjection(3);
        y = pca.project(data);

        plot = new PlotCanvas(Math.colMin(y), Math.colMax(y));
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

        plot.setTitle("PCA");
        pane.add(plot);

        RandomProjection rp = new RandomProjection(data[0].length, 2, sparseBox.isSelected());
        System.out.format("%d x %d Random Projection:\n", data[0].length, 3);
        DenseMatrix projection = rp.getProjection();
        for (int i = 0; i < projection.nrows(); i++) {
            for (int j = 0; j < projection.ncols(); j++) {
                System.out.format("% .4f ", projection.get(i, j));
            }
            System.out.println();
        }

        y = rp.project(data);
        plot = new PlotCanvas(Math.colMin(y), Math.colMax(y));
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

        plot.setTitle("Random Projection");
        pane.add(plot);

        rp = new RandomProjection(data[0].length, 3, sparseBox.isSelected());
        System.out.format("%d x %d Random Projection:\n", data[0].length, 3);
        projection = rp.getProjection();
        for (int i = 0; i < projection.nrows(); i++) {
            for (int j = 0; j < projection.ncols(); j++) {
                System.out.format("% .4f ", projection.get(i, j));
            }
            System.out.println();
        }

        y = rp.project(data);
        plot = new PlotCanvas(Math.colMin(y), Math.colMax(y));
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

        plot.setTitle("Random Projection");
        pane.add(plot);

        return pane;
    }

    @Override
    public String toString() {
        return "Random Projection";
    }

    public static void main(String argv[]) {
        RandomProjectionDemo demo = new RandomProjectionDemo();
        JFrame f = new JFrame("Random Projection");
        f.setSize(new Dimension(1000, 1000));
        f.setLocationRelativeTo(null);
        f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        f.getContentPane().add(demo);
        f.setVisible(true);
    }
}
