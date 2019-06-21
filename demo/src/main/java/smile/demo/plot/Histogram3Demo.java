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

import smile.math.MathEx;
import smile.plot.Histogram3D;
import smile.plot.Palette;
import smile.plot.PlotCanvas;

/**
 *
 * @author Haifeng Li
 */
@SuppressWarnings("serial")
public class Histogram3Demo extends JPanel {
    public Histogram3Demo() {
        super(new GridLayout(1,2));
        
        double[][] data = new double[10000][2];
        for (int j = 0; j < data.length; j++) {
            double x, y, r;
            do {
                x = 2 * (MathEx.random() - 0.5);
                y = 2 * (MathEx.random() - 0.5);
                r = x * x + y * y;
            } while (r >= 1.0);

            double z = Math.sqrt(-2.0 * Math.log(r) / r);
            data[j][0] = new Double(x * z);
            data[j][1] = new Double(y * z);
        }
        
        PlotCanvas canvas = Histogram3D.plot(data, 20);
        canvas.setTitle("Histogram 3D");
        add(canvas);
        
        canvas = Histogram3D.plot(data, 20, Palette.jet(16));
        canvas.setTitle("Histogram 3D with Colormap");
        add(canvas);
    }

    @Override
    public String toString() {
        return "Histogram 3D";
    }

    public static void main(String[] args) {
        JFrame frame = new JFrame("Histogram 3D");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLocationRelativeTo(null);
        frame.getContentPane().add(new Histogram3Demo());
        frame.setVisible(true);
    }
}
