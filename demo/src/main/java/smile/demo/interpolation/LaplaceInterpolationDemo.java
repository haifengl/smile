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

import smile.interpolation.BicubicInterpolation;
import smile.interpolation.LaplaceInterpolation;
import smile.plot.Heatmap;
import smile.plot.Palette;
import smile.plot.PlotCanvas;

/**
 *
 * @author Haifeng Li
 */
@SuppressWarnings("serial")
public class LaplaceInterpolationDemo extends JPanel {
    public LaplaceInterpolationDemo() {
        super(new GridLayout(2,2));
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
        double[][] yy = new double[101][101];
        for (int i = 0; i <= 100; i++) {
            for (int j = 0; j <= 100; j++) {
                yy[i][j] = bicubic.interpolate(i * 0.03, j * 0.03);
            }
        }

        PlotCanvas canvas = Heatmap.plot(yy, Palette.jet(256));
        canvas.setTitle("Original");
        add(canvas);

        double[][] zz = new double[101][101];
        double[][] ww = new double[101][101];
        for (int i = 0; i <= 100; i++) {
            for (int j = 0; j <= 100; j++) {
                if (Math.random() < 0.2)
                    zz[i][j] = Double.NaN;
                else
                    zz[i][j] = yy[i][j];

                ww[i][j] = zz[i][j];
            }
        }

        canvas = Heatmap.plot(ww, Palette.jet(256));
        canvas.setTitle("Missing Values");
        add(canvas);

        LaplaceInterpolation.interpolate(zz);
        canvas = Heatmap.plot(zz, Palette.jet(256));
        canvas.setTitle("Laplace");
        add(canvas);
    }

    @Override
    public String toString() {
        return "Laplace";
    }

    public static void main(String[] args) {
        JFrame frame = new JFrame("Laplace Interpolation");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLocationRelativeTo(null);
        frame.getContentPane().add(new LaplaceInterpolationDemo());
        frame.setVisible(true);
    }
}
