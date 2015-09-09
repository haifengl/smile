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

package smile.demo.plot;

import java.awt.GridLayout;

import javax.swing.JFrame;
import javax.swing.JPanel;

import smile.plot.Palette;
import smile.plot.PlotCanvas;
import smile.plot.Surface;

/**
 *
 * @author Haifeng Li
 */
@SuppressWarnings("serial")
public class SurfaceDemo extends JPanel {
    public SurfaceDemo() {
        super(new GridLayout(1,2));

        int n = 50;
        int m = 50;
        double[][][] z = new double[m][n][3];

        for (int i = 0; i < m; i++) {
            for (int j = 0; j < n; j++) {
                z[i][j][0] = 6.0 * (i - m/2) / m;
                z[i][j][1] = 6.0 * (j - n/2) / n;
                z[i][j][2] = Math.exp((z[i][j][0]*z[i][j][0] + z[i][j][1]*z[i][j][1]) / -2) / Math.sqrt(2*Math.PI);
            }
        }

        PlotCanvas canvas = Surface.plot(z);
        canvas.setTitle("Gaussian");
        add(canvas);

        z = new double[m][n][3];
        for (int i = 0; i < m; i++) {
            for (int j = 0; j < n; j++) {
                z[i][j][0] = 6.0 * (i - m/2) / m;
                z[i][j][1] = 6.0 * (j - n/2) / n;
                double t = z[i][j][0]*z[i][j][0] + z[i][j][1]*z[i][j][1];
                z[i][j][2] = (1 - t) * Math.exp(t / -2) / Math.sqrt(2*Math.PI);
            }
        }

        canvas = Surface.plot(z, Palette.jet(256, 1.0f));
        canvas.setTitle("Mexican Hat");
        add(canvas);
    }

    @Override
    public String toString() {
        return "Surface";
    }

    public static void main(String[] args) {
        JFrame frame = new JFrame("Surface Plot");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLocationRelativeTo(null);
        frame.getContentPane().add(new SurfaceDemo());
        frame.setVisible(true);
    }
}
