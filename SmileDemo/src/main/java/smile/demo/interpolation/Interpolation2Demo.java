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

import java.awt.GridLayout;

import javax.swing.JFrame;
import javax.swing.JPanel;

import smile.interpolation.BicubicInterpolation;
import smile.interpolation.BilinearInterpolation;
import smile.interpolation.CubicSplineInterpolation2D;
import smile.plot.Heatmap;
import smile.plot.Palette;
import smile.plot.PlotCanvas;

/**
 *
 * @author Haifeng Li
 */
@SuppressWarnings("serial")
public class Interpolation2Demo extends JPanel {
    public Interpolation2Demo() {
        super(new GridLayout(2,2));

        double[] x1 = {0, 1, 2, 3};
        double[] x2 = {0, 1, 2, 3};
        double[][] y = {
            {1, 2, 4, 1},
            {6, 3, 5, 2},
            {4, 2, 1, 5},
            {5, 4, 2, 3}
        };

        PlotCanvas canvas = Heatmap.plot(y, Palette.jet(256));
        canvas.setTitle("Original");
        add(canvas);

        BicubicInterpolation bicubic = new BicubicInterpolation(x1, x2, y);
        double[][] yy = new double[101][101];
        for (int i = 0; i <= 100; i++)
            for (int j = 0; j <= 100; j++)
                yy[i][j] = bicubic.interpolate(i*0.03, j*0.03);

        canvas = Heatmap.plot(yy, Palette.jet(256));
        canvas.setTitle("Bicubic");
        add(canvas);

        BilinearInterpolation bilinear = new BilinearInterpolation(x1, x2, y);
        double[][] zz = new double[101][101];
        for (int i = 0; i <= 100; i++)
            for (int j = 0; j <= 100; j++)
                zz[i][j] = bilinear.interpolate(i*0.03, j*0.03);

        canvas = Heatmap.plot(zz, Palette.jet(256));
        canvas.setTitle("Blinear");
        add(canvas);

        CubicSplineInterpolation2D spline = new CubicSplineInterpolation2D(x1, x2, y);
        double[][] ww = new double[101][101];
        for (int i = 0; i <= 100; i++)
            for (int j = 0; j <= 100; j++)
                ww[i][j] = spline.interpolate(i*0.03, j*0.03);

        canvas = Heatmap.plot(ww, Palette.jet(256));
        canvas.setTitle("Cubic Spline");
        add(canvas);
    }

    @Override
    public String toString() {
        return "2D";
    }

    public static void main(String[] args) {
        JFrame frame = new JFrame("Interpolation 2D");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLocationRelativeTo(null);
        frame.getContentPane().add(new Interpolation2Demo());
        frame.setVisible(true);
    }
}
