/*
 * Copyright (c) 2010-2020 Haifeng Li. All rights reserved.
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
 */

package smile.demo.projection;

import java.awt.Dimension;
import java.awt.GridLayout;

import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JPanel;

import smile.math.matrix.Matrix;
import smile.plot.swing.Canvas;
import smile.plot.swing.ScatterPlot;
import smile.plot.swing.TextPlot;
import smile.projection.PCA;
import smile.projection.RandomProjection;

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
    public JComponent learn(double[][] data, int[] labels, String[] names) {
        JPanel pane = new JPanel(new GridLayout(2, 2));

        long clock = System.currentTimeMillis();
        PCA pca = PCA.cor(data);
        System.out.format("Learn PCA from %d samples in %dms\n", data.length, System.currentTimeMillis() - clock);

        pca.setProjection(2);
        double[][] y = pca.project(data);

        Canvas plot;
        if (names != null) {
            plot = TextPlot.of(names, y).canvas();
        } else if (labels != null) {
            plot = ScatterPlot.of(y, labels, mark).canvas();
        } else {
            plot = ScatterPlot.of(y).canvas();
        }

        plot.setTitle("PCA");
        pane.add(plot.panel());

        pca.setProjection(3);
        y = pca.project(data);

        if (names != null) {
            plot = TextPlot.of(names, y).canvas();
        } else if (labels != null) {
            plot = ScatterPlot.of(y, labels, mark).canvas();
        } else {
            plot = ScatterPlot.of(y).canvas();
        }

        plot.setTitle("PCA");
        pane.add(plot.panel());

        RandomProjection rp = sparseBox.isSelected() ? RandomProjection.sparse(data[0].length, 0) : RandomProjection.of(data[0].length, 2);
        System.out.format("%d x %d Random Projection:\n", data[0].length, 3);
        Matrix projection = rp.projection();
        for (int i = 0; i < projection.nrow(); i++) {
            for (int j = 0; j < projection.ncol(); j++) {
                System.out.format("% .4f ", projection.get(i, j));
            }
            System.out.println();
        }

        y = rp.project(data);
        if (names != null) {
            plot = TextPlot.of(names, y).canvas();
        } else if (labels != null) {
            plot = ScatterPlot.of(y, labels, mark).canvas();
        } else {
            plot = ScatterPlot.of(y).canvas();
        }

        plot.setTitle("Random Projection");
        pane.add(plot.panel());

        rp = sparseBox.isSelected() ?  RandomProjection.sparse(data[0].length, 3) : RandomProjection.of(data[0].length, 3);
        System.out.format("%d x %d Random Projection:\n", data[0].length, 3);
        projection = rp.projection();
        for (int i = 0; i < projection.nrow(); i++) {
            for (int j = 0; j < projection.ncol(); j++) {
                System.out.format("% .4f ", projection.get(i, j));
            }
            System.out.println();
        }

        y = rp.project(data);
        if (names != null) {
            plot = TextPlot.of(names, y).canvas();
        } else if (labels != null) {
            plot = ScatterPlot.of(y, labels, mark).canvas();
        } else {
            plot = ScatterPlot.of(y).canvas();
        }

        plot.setTitle("Random Projection");
        pane.add(plot.panel());

        return pane;
    }

    @Override
    public String toString() {
        return "Random Projection";
    }

    public static void main(String[] args) {
        RandomProjectionDemo demo = new RandomProjectionDemo();
        JFrame f = new JFrame("Random Projection");
        f.setSize(new Dimension(1000, 1000));
        f.setLocationRelativeTo(null);
        f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        f.getContentPane().add(demo);
        f.setVisible(true);
    }
}
