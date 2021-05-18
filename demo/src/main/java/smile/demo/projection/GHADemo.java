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
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JPanel;

import smile.math.TimeFunction;
import smile.plot.swing.Canvas;
import smile.plot.swing.ScatterPlot;
import smile.plot.swing.TextPlot;
import smile.projection.GHA;
import smile.projection.PCA;

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

        clock = System.currentTimeMillis();
        TimeFunction r = TimeFunction.constant(0.00001);
        GHA gha = new GHA(data[0].length, 2, r);
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
        if (names != null) {
            plot = TextPlot.of(names, y).canvas();
        } else if (labels != null) {
            plot = ScatterPlot.of(y, labels, mark).canvas();
        } else {
            plot = ScatterPlot.of(y).canvas();
        }

        plot.setTitle("GHA");
        pane.add(plot.panel());

        clock = System.currentTimeMillis();
        gha = new GHA(data[0].length, 3, r);
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
        if (names != null) {
            plot = TextPlot.of(names, y).canvas();
        } else if (labels != null) {
            plot = ScatterPlot.of(y, labels, mark).canvas();
        } else {
            plot = ScatterPlot.of(y).canvas();
        }

        plot.setTitle("GHA");
        pane.add(plot.panel());

        return pane;
    }

    @Override
    public String toString() {
        return "Generalized Hebbian Algorithm";
    }

    public static void main(String[] args) {
        GHADemo demo = new GHADemo();
        JFrame f = new JFrame("Generalized Hebbian Algorithm");
        f.setSize(new Dimension(1000, 1000));
        f.setLocationRelativeTo(null);
        f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        f.getContentPane().add(demo);
        f.setVisible(true);
    }
}
