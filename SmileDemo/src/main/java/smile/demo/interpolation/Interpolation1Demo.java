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

import javax.swing.JFrame;
import javax.swing.JPanel;

import smile.plot.PlotCanvas;
import smile.interpolation.CubicSplineInterpolation1D;
import smile.interpolation.KrigingInterpolation1D;
import smile.interpolation.LinearInterpolation;
import smile.interpolation.RBFInterpolation1D;
import smile.interpolation.ShepardInterpolation1D;
import smile.math.rbf.GaussianRadialBasis;
import smile.plot.ScatterPlot;

/**
 *
 * @author Haifeng Li
 */
@SuppressWarnings("serial")
public class Interpolation1Demo extends JPanel {
    public Interpolation1Demo() {
        super(new GridLayout(2,3));
        setBackground(Color.WHITE);

        double[] x = {0, 1, 2, 3, 4, 5, 6};
        double[] y = {0, 0.8415, 0.9093, 0.1411, -0.7568, -0.9589, -0.2794};

        double[][] controls = new double[x.length][2];
        for (int i = 0; i < controls.length; i++) {
            controls[i][0] = x[i];
            controls[i][1] = y[i];
        }

        PlotCanvas canvas = ScatterPlot.plot(controls, '@');
        canvas.setTitle("Linear");
        LinearInterpolation linear = new LinearInterpolation(x, y);
        double[][] yy = new double[61][2];
        for (int i = 0; i <= 60; i++) {
            yy[i][0] = i * 0.1;
            yy[i][1] = linear.interpolate(yy[i][0]);
        }
        canvas.line(yy, Color.RED);
        add(canvas);

        canvas = ScatterPlot.plot(controls, '@');
        canvas.setTitle("Cubic Spline");
        CubicSplineInterpolation1D spline = new CubicSplineInterpolation1D(x, y);
        double[][] zz = new double[61][2];
        for (int i = 0; i <= 60; i++) {
            zz[i][0] = i * 0.1;
            zz[i][1] = spline.interpolate(zz[i][0]);
        }
        canvas.line(zz, Color.BLUE);
        add(canvas);

        canvas = ScatterPlot.plot(controls, '@');
        canvas.setTitle("RBF");
        RBFInterpolation1D rbf = new RBFInterpolation1D(x, y, new GaussianRadialBasis());
        double[][] ww = new double[61][2];
        for (int i = 0; i <= 60; i++) {
            ww[i][0] = i * 0.1;
            ww[i][1] = rbf.interpolate(zz[i][0]);
        }
        canvas.line(ww, Color.GREEN);
        add(canvas);

        canvas = ScatterPlot.plot(controls, '@');
        canvas.setTitle("Kriging");
        KrigingInterpolation1D kriging = new KrigingInterpolation1D(x, y);
        double[][] uu = new double[61][2];
        for (int i = 0; i <= 60; i++) {
            uu[i][0] = i * 0.1;
            uu[i][1] = kriging.interpolate(zz[i][0]);
        }
        canvas.line(uu, Color.PINK);
        add(canvas);

        canvas = ScatterPlot.plot(controls, '@');
        canvas.setTitle("Shepard");
        ShepardInterpolation1D shepard = new ShepardInterpolation1D(x, y, 3);
        double[][] vv = new double[61][2];
        for (int i = 0; i <= 60; i++) {
            vv[i][0] = i * 0.1;
            vv[i][1] = shepard.interpolate(zz[i][0]);
        }
        canvas.line(vv, Color.CYAN);
        add(canvas);
    }

    @Override
    public String toString() {
        return "1D";
    }

    public static void main(String[] args) {
        JFrame frame = new JFrame("Interpolation 1D");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLocationRelativeTo(null);
        frame.getContentPane().add(new Interpolation1Demo());
        frame.setVisible(true);
    }
}
