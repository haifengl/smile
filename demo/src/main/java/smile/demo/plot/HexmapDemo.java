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

import java.awt.Color;
import java.awt.GridLayout;
import javax.swing.JFrame;
import javax.swing.JPanel;
import smile.plot.swing.Hexmap;
import smile.plot.swing.Palette;
import smile.plot.swing.PlotCanvas;

/**
 *
 * @author Haifeng Li
 */
@SuppressWarnings("serial")
public class HexmapDemo extends JPanel {
    public HexmapDemo() {
        super(new GridLayout(2,4));
        setBackground(Color.white);

        int n = 41;
        double[] x = new double[n];
        for (int i = 0; i < n; i++)
            x[i] = -2.0 + 0.1 * i;

        int m = 41;
        double[] y = new double[m];
        for (int i = 0; i < m; i++)
            y[i] = -2.0 + 0.1 * i;

        double[][] z = new double[m][n];
        for (int i = 0; i < m; i++) {
            for (int j = 0; j < n; j++)
                z[i][j] = x[j] * Math.exp(-x[j]*x[j] - y[i]*y[i]);
        }

        PlotCanvas canvas = Hexmap.plot(z, Palette.jet(256));
        canvas.setTitle("jet");
        add(canvas);
        canvas = Hexmap.plot(z, Palette.redblue(256));
        canvas.setTitle("redblue");
        add(canvas);
        canvas = Hexmap.plot(z, Palette.redgreen(256));
        canvas.setTitle("redgreen");
        add(canvas);
        canvas = Hexmap.plot(z, Palette.heat(256));
        canvas.setTitle("heat");
        add(canvas);
        canvas = Hexmap.plot(z, Palette.terrain(256));
        canvas.setTitle("terrain");
        add(canvas);
        canvas = Hexmap.plot(z, Palette.rainbow(256));
        canvas.setTitle("rainbow");
        add(canvas);
        canvas = Hexmap.plot(z, Palette.topo(256));
        canvas.setTitle("topo");
        add(canvas);
    }

    @Override
    public String toString() {
        return "Hexmap";
    }

    public static void main(String[] args) {
        JFrame frame = new JFrame("Hexmap");
        frame.setSize(1000, 1000);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLocationRelativeTo(null);
        frame.getContentPane().add(new HexmapDemo());
        frame.setVisible(true);
    }
}
