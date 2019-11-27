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

import smile.plot.swing.Contour;
import smile.plot.swing.Palette;

/**
 *
 * @author Haifeng Li
 */
@SuppressWarnings("serial")
public class ContourDemo extends JPanel {
    public ContourDemo() {
        super(new GridLayout(1,2));
        setBackground(Color.white);

        int n = 41;
        double[] x = new double[n];
        for (int i = 0; i < n; i++)
            x[i] = -2.0 + 0.1 * i;

        int m = 51;
        double[] y = new double[m];
        for (int i = 0; i < m; i++)
            y[i] = -2.0 + 0.1 * i;

        double[][] z = new double[m][n];
        for (int i = 0; i < m; i++) {
            for (int j = 0; j < n; j++)
                z[i][j] = x[j] * Math.exp(-x[j]*x[j] - y[i]*y[i]);
        }

        add(Contour.plot(x, y, z));

        double[] c = new double[9];
        for (int i = 0; i < c.length; i++) {
            c[i] = -0.4 + 0.1 * i;
        }
        add(Contour.plot(x, y, z, c, Palette.jet(c.length)));
    }

    @Override
    public String toString() {
        return "Contour";
    }

    public static void main(String[] args) {
        JFrame frame = new JFrame("Contour");
        frame.setSize(1000, 1000);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLocationRelativeTo(null);
        frame.getContentPane().add(new ContourDemo());
        frame.setVisible(true);
    }
}
