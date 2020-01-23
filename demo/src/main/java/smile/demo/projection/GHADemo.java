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
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JPanel;

import smile.plot.swing.Palette;
import smile.plot.swing.PlotCanvas;
import smile.projection.GHA;
import smile.projection.PCA;
import smile.math.MathEx;

/**
 *
 * @author Haifeng Li
 */
@SuppressWarnings("serial")
public class GHADemo extends ProjectionDemo {

    public GHADemo() {
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
                plot.point(pointLegend, Palette.COLORS[labels[i]], labels[i]);
            }
        } else {
            plot.points(y, pointLegend);
        }

        plot.setTitle("PCA");
        pane.add(plot);

        clock = System.currentTimeMillis();
        GHA gha = new GHA(data[0].length, 2, 0.00001);
        for (int iter = 1; iter <= 500; iter++) {
            double error = 0.0;
            for (int i = 0; i < data.length; i++) {
                error += gha.update(data[i]);
            }
            error /= data.length;

            if (iter % 100 == 0) {
                System.out.format("Iter %3d, Error = %.5g\n", iter, error);
            }
        }
        System.out.format("Learn GHA from %d samples in %dms\n", data.length, System.currentTimeMillis() - clock);
        y = gha.project(data);
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

        plot.setTitle("GHA");
        pane.add(plot);

        clock = System.currentTimeMillis();
        gha = new GHA(data[0].length, 3, 0.00001);
        for (int iter = 1; iter <= 500; iter++) {
            double error = 0.0;
            for (int i = 0; i < data.length; i++) {
                error += gha.update(data[i]);
            }
            error /= data.length;

            if (iter % 100 == 0) {
                System.out.format("Iter %3d, Error = %.5g\n", iter, error);
            }
        }
        System.out.format("Learn GHA from %d samples in %dms\n", data.length, System.currentTimeMillis() - clock);
        y = gha.project(data);
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

        plot.setTitle("GHA");
        pane.add(plot);

        return pane;
    }

    @Override
    public String toString() {
        return "Generalized Hebbian Algorithm";
    }

    public static void main(String argv[]) {
        GHADemo demo = new GHADemo();
        JFrame f = new JFrame("Generalized Hebbian Algorithm");
        f.setSize(new Dimension(1000, 1000));
        f.setLocationRelativeTo(null);
        f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        f.getContentPane().add(demo);
        f.setVisible(true);
    }
}
