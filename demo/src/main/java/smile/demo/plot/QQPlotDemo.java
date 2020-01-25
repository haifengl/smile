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
import smile.plot.swing.PlotCanvas;
import smile.plot.swing.QQPlot;
import smile.stat.distribution.GaussianDistribution;

/**
 *
 * @author Haifeng Li
 */
@SuppressWarnings("serial")
public class QQPlotDemo extends JPanel {
    public QQPlotDemo() {
        super(new GridLayout(1,2));

        double[] x  = new double[5000];
        for (int j = 0; j < x.length; j++) {
            x[j] = MathEx.random();
        }
        
        double[] y  = new double[500];
        for (int j = 0; j < y.length; j++) {
            y[j] = j / 500.0;
        }

        PlotCanvas canvas = QQPlot.plot(x, y);
        canvas.setTitle("Uniform (Smile)");
        add(canvas);

        double[] x3  = new double[5000];
        for (int j = 0; j < x3.length; j++) {
            x3[j] = GaussianDistribution.getInstance().rand();
        }

        canvas = QQPlot.plot(x3);
        canvas.setTitle("Gaussian");
        add(canvas);
    }

    @Override
    public String toString() {
        return "Q-Q Plot";
    }

    public static void main(String[] args) {
        JFrame frame = new JFrame("QQ Plot");
        frame.setSize(1000, 1000);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLocationRelativeTo(null);
        frame.getContentPane().add(new QQPlotDemo());
        frame.setVisible(true);
    }
}
