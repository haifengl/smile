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

import java.awt.Color;
import java.awt.GridLayout;

import javax.swing.JFrame;
import javax.swing.JPanel;

import smile.plot.Contour;
import smile.plot.Palette;

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
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLocationRelativeTo(null);
        frame.getContentPane().add(new ContourDemo());
        frame.setVisible(true);
    }
}
