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

package smile.demo.projection;

import java.awt.Dimension;
import java.awt.GridLayout;

import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JPanel;

import smile.math.matrix.DenseMatrix;
import smile.plot.swing.Palette;
import smile.plot.swing.PlotCanvas;
import smile.projection.PCA;
import smile.projection.RandomProjection;
import smile.math.MathEx;

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

        PlotCanvas plot = new PlotCanvas(MathEx.colMin(y), MathEx.colMax(y));
        if (names != null) {
            plot.points(y, names);
        } else if (labels != null) {
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

        plot = new PlotCanvas(MathEx.colMin(y), MathEx.colMax(y));
        if (names != null) {
            plot.points(y, names);
        } else if (labels != null) {
            for (int i = 0; i < y.length; i++) {
                plot.point(pointLegend, Palette.COLORS[labels[i]], y[i]);
            }
        } else {
            plot.points(y, pointLegend);
        }

        plot.setTitle("PCA");
        pane.add(plot);

        RandomProjection rp = sparseBox.isSelected() ? RandomProjection.sparse(data[0].length, 0) : RandomProjection.of(data[0].length, 2);
        System.out.format("%d x %d Random Projection:\n", data[0].length, 3);
        DenseMatrix projection = rp.getProjection();
        for (int i = 0; i < projection.nrows(); i++) {
            for (int j = 0; j < projection.ncols(); j++) {
                System.out.format("% .4f ", projection.get(i, j));
            }
            System.out.println();
        }

        y = rp.project(data);
        plot = new PlotCanvas(MathEx.colMin(y), MathEx.colMax(y));
        if (names != null) {
            plot.points(y, names);
        } else if (labels != null) {
            for (int i = 0; i < y.length; i++) {
                plot.point(pointLegend, Palette.COLORS[labels[i]], y[i]);
            }
        } else {
            plot.points(y, pointLegend);
        }

        plot.setTitle("Random Projection");
        pane.add(plot);

        rp = sparseBox.isSelected() ?  RandomProjection.sparse(data[0].length, 3) : RandomProjection.of(data[0].length, 3);
        System.out.format("%d x %d Random Projection:\n", data[0].length, 3);
        projection = rp.getProjection();
        for (int i = 0; i < projection.nrows(); i++) {
            for (int j = 0; j < projection.ncols(); j++) {
                System.out.format("% .4f ", projection.get(i, j));
            }
            System.out.println();
        }

        y = rp.project(data);
        plot = new PlotCanvas(MathEx.colMin(y), MathEx.colMax(y));
        if (names != null) {
            plot.points(y, names);
        } else if (labels != null) {
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
