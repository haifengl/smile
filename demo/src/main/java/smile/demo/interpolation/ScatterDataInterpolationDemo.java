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

package smile.demo.interpolation;

import java.awt.Color;
import java.awt.GridLayout;
import java.util.ArrayList;

import javax.swing.JFrame;
import javax.swing.JPanel;

import smile.plot.swing.Palette;
import smile.plot.swing.PlotCanvas;
import smile.interpolation.BicubicInterpolation;
import smile.interpolation.KrigingInterpolation;
import smile.interpolation.RBFInterpolation;
import smile.interpolation.ShepardInterpolation;
import smile.math.rbf.GaussianRadialBasis;
import smile.plot.swing.Heatmap;

/**
 *
 * @author Haifeng Li
 */
@SuppressWarnings("serial")
public class ScatterDataInterpolationDemo extends JPanel {
    public ScatterDataInterpolationDemo() {
        super(new GridLayout(2,3));
        setBackground(Color.WHITE);

        double[] x1 = {0, 1, 2, 3};
        double[] x2 = {0, 1, 2, 3};
        double[][] y = {
            {1, 2, 4, 1},
            {6, 3, 5, 2},
            {4, 2, 1, 5},
            {5, 4, 2, 3}
        };

        BicubicInterpolation bicubic = new BicubicInterpolation(x1, x2, y);
        double[][] yy = new double[26][26];
        for (int i = 0; i <= 25; i++) {
            for (int j = 0; j <= 25; j++) {
                yy[i][j] = bicubic.interpolate(i * 0.12, j * 0.12);
            }
        }

        PlotCanvas canvas = Heatmap.plot(yy, Palette.jet(256));
        canvas.setTitle("Original");
        add(canvas);

        double[][] ww = new double[26][26];
        ArrayList<double[]> xx = new ArrayList<>();
        ArrayList<Double> zz = new ArrayList<>();
        for (int i = 0; i <= 25; i++) {
            for (int j = 0; j <= 25; j++) {
                if (Math.random() < 0.2)
                    ww[i][j] = Double.NaN;
                else
                    ww[i][j] = yy[i][j];

                if (!Double.isNaN(ww[i][j])) {
                    double[] pos = new double[2];
                    pos[0] = j * 0.12;
                    pos[1] = i * 0.12;
                    xx.add(pos);
                    zz.add(ww[i][j]);
                }
            }
        }

        double[][] p = new double[xx.size()][];
        double[] q = new double[xx.size()];
        for (int i = 0; i < p.length; i++) {
            p[i] = xx.get(i);
            q[i] = zz.get(i);
        }

        canvas = Heatmap.plot(ww, Palette.jet(256));
        canvas.setTitle("Missing Values");
        add(canvas);

        KrigingInterpolation kriging = new KrigingInterpolation(p, q);
        double[][] uu = new double[26][26];
        for (int i = 0; i <= 25; i++) {
            for (int j = 0; j <= 25; j++) {
                if (Double.isNaN(ww[i][j])) {
                    uu[i][j] = kriging.interpolate(j*0.12, i*0.12);
                } else {
                    uu[i][j] = ww[i][j];
                }
            }
        }
        canvas = Heatmap.plot(uu, Palette.jet(256));
        canvas.setTitle("Kriging");
        add(canvas);

        RBFInterpolation rbf = new RBFInterpolation(p, q, new GaussianRadialBasis(0.25));
        double[][] vv = new double[26][26];
        for (int i = 0; i <= 25; i++) {
            for (int j = 0; j <= 25; j++) {
                if (Double.isNaN(ww[i][j])) {
                    vv[i][j] = rbf.interpolate(j*0.12, i*0.12);
                } else {
                    vv[i][j] = ww[i][j];
                }
            }
        }
        canvas = Heatmap.plot(vv, Palette.jet(256));
        canvas.setTitle("RBF");
        add(canvas);

        ShepardInterpolation shepard = new ShepardInterpolation(p, q, 3);
        double[][] rr = new double[26][26];
        for (int i = 0; i <= 25; i++) {
            for (int j = 0; j <= 25; j++) {
                if (Double.isNaN(ww[i][j])) {
                    rr[i][j] = shepard.interpolate(j*0.12, i*0.12);
                } else {
                    rr[i][j] = ww[i][j];
                }
            }
        }
        canvas = Heatmap.plot(rr, Palette.jet(256));
        canvas.setTitle("Shepard");
        add(canvas);
    }

    @Override
    public String toString() {
        return "Scatter Data";
    }

    public static void main(String[] args) {
        JFrame frame = new JFrame("Scatter Data Interpolation");
        frame.setSize(1000, 1000);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLocationRelativeTo(null);
        frame.getContentPane().add(new ScatterDataInterpolationDemo());
        frame.setVisible(true);
    }
}
