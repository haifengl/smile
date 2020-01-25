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

package smile.demo.plot;

import java.awt.GridLayout;
import javax.swing.JFrame;
import javax.swing.JPanel;
import smile.plot.swing.Palette;
import smile.plot.swing.PlotCanvas;
import smile.plot.swing.Surface;

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
        frame.setSize(1000, 1000);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLocationRelativeTo(null);
        frame.getContentPane().add(new SurfaceDemo());
        frame.setVisible(true);
    }
}
