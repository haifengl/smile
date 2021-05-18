/*
 * Copyright (c) 2010-2020 Haifeng Li. All rights reserved.
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
 */

package smile.demo.plot;

import java.awt.Color;
import java.awt.GridLayout;

import javax.swing.JFrame;
import javax.swing.JPanel;

import smile.math.MathEx;
import smile.plot.swing.Histogram;
import smile.plot.swing.Canvas;

/**
 *
 * @author Haifeng Li
 */
@SuppressWarnings("serial")
public class HistogramDemo extends JPanel {
    public HistogramDemo() {
        super(new GridLayout(1,2));

        double[] data = new double[1000];
        for (int j = 0; j < data.length; j++) {
            double x, y, r;
            do {
                x = 2 * (MathEx.random() - 0.5);
                y = 2 * (MathEx.random() - 0.5);
                r = x * x + y * y;
            } while (r >= 1.0);

            double z = Math.sqrt(-2.0 * Math.log(r) / r);
            data[j] = 0.01 * x * z;
        }

        Canvas canvas = Histogram.of(data, smile.math.Histogram.breaks(-6/100., 6/100., 30), false, Color.BLUE).canvas();
        canvas.setTitle("Overlapped Histogram");
        add(canvas.panel());

        data = new double[1000];
        for (int j = 0; j < data.length; j++) {
            double x, y, r;
            do {
                x = 2 * (MathEx.random() - 0.5);
                y = 2 * (MathEx.random() - 0.5);
                r = x * x + y * y;
            } while (r >= 1.0);

            double z = Math.sqrt(-2.0 * Math.log(r) / r);
            data[j] = 0.01 *( 2 + x * z);
        }

        canvas.add(Histogram.of(data, smile.math.Histogram.breaks(-6/100., 6/100., 30), false, Color.RED));

        double[] prob = {0.2, 0.3, 0.1, 0.05, 0.2, 0.15};
        int[] data2 = MathEx.random(prob, 1000);

        canvas = Histogram.of(data2, 6, true, Color.BLUE).canvas();
        canvas.setTitle("Sampling with Uneuqal Probabilities");
        add(canvas.panel());
    }

    @Override
    public String toString() {
        return "Histogram";
    }

    public static void main(String[] args) {
        JFrame frame = new JFrame("Histogram");
        frame.setSize(1000, 1000);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLocationRelativeTo(null);
        frame.getContentPane().add(new HistogramDemo());
        frame.setVisible(true);
    }
}
