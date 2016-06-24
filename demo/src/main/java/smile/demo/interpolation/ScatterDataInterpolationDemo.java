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

package smile.demo.interpolation;

import java.awt.Color;
import java.awt.GridLayout;
import java.util.ArrayList;

import javax.swing.JFrame;
import javax.swing.JPanel;

import smile.plot.Palette;
import smile.plot.PlotCanvas;
import smile.interpolation.BicubicInterpolation;
import smile.interpolation.KrigingInterpolation;
import smile.interpolation.RBFInterpolation;
import smile.interpolation.ShepardInterpolation;
import smile.math.rbf.GaussianRadialBasis;
import smile.plot.Heatmap;

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
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLocationRelativeTo(null);
        frame.getContentPane().add(new ScatterDataInterpolationDemo());
        frame.setVisible(true);
    }
}
