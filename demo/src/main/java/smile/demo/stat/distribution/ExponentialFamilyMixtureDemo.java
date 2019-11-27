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

package smile.demo.stat.distribution;

import java.awt.Color;
import java.awt.GridLayout;
import javax.swing.JFrame;
import javax.swing.JPanel;

import smile.math.MathEx;
import smile.plot.swing.Histogram;
import smile.plot.swing.PlotCanvas;
import smile.plot.swing.QQPlot;
import smile.stat.distribution.ExponentialDistribution;
import smile.stat.distribution.ExponentialFamilyMixture;
import smile.stat.distribution.GammaDistribution;
import smile.stat.distribution.GaussianDistribution;
import smile.stat.distribution.Mixture;

/**
 *
 * @author Haifeng Li
 */
@SuppressWarnings("serial")
public class ExponentialFamilyMixtureDemo extends JPanel {
    public ExponentialFamilyMixtureDemo() {
        super(new GridLayout(1, 2));

        // Mixture of Gaussian, Exponential, and Gamma.
        double[] data = new double[2000];

        GaussianDistribution gaussian = new GaussianDistribution(-2.0, 1.0);
        for (int i = 0; i < 500; i++)
            data[i] = gaussian.rand();

        ExponentialDistribution exp = new ExponentialDistribution(0.8);
        for (int i = 500; i < 1000; i++)
            data[i] = exp.rand();

        GammaDistribution gamma = new GammaDistribution(2.0, 3.0);
        for (int i = 1000; i < 2000; i++)
            data[i] = gamma.rand();

        ExponentialFamilyMixture mixture = ExponentialFamilyMixture.fit(data,
                new Mixture.Component(0.25, new GaussianDistribution(0.0, 1.0)),
                new Mixture.Component(0.25, new ExponentialDistribution(1.0)),
                new Mixture.Component(0.5, new GammaDistribution(1.0, 2.0))
                );

        PlotCanvas canvas = Histogram.plot(data, 50);
        canvas.setTitle("Mixture of Gaussian, Exponential, and Gamma");
        add(canvas);

        double width = (MathEx.max(data) - MathEx.min(data)) / 50;
        double[][] p = new double[400][2];
        for (int i = 0; i < p.length; i++) {
            p[i][0] = -10 + i*0.1;
            p[i][1] = mixture.p(p[i][0]) * width;
        }

        canvas.line(p, Color.RED);

        canvas = QQPlot.plot(data, mixture);
        canvas.setTitle("Q-Q Plot");
        add(canvas);
    }

    @Override
    public String toString() {
        return "Exponential Family Mixture";
    }

    public static void main(String[] args) {
        // Mixture of Gaussian, Exponential, and Gamma.
        double[] data = new double[2000];

        GaussianDistribution gaussian = new GaussianDistribution(-2.0, 1.0);
        for (int i = 0; i < 500; i++)
            data[i] = gaussian.rand();

        ExponentialDistribution exp = new ExponentialDistribution(0.8);
        for (int i = 500; i < 1000; i++)
            data[i] = exp.rand();

        GammaDistribution gamma = new GammaDistribution(2.0, 3.0);
        for (int i = 1000; i < 2000; i++)
            data[i] = gamma.rand();

        ExponentialFamilyMixture mixture = ExponentialFamilyMixture.fit(data,
                new Mixture.Component(0.25, new GaussianDistribution(0.0, 1.0)),
                new Mixture.Component(0.25, new ExponentialDistribution(1.0)),
                new Mixture.Component(0.5, new GammaDistribution(1.0, 2.0))
        );
        System.out.println(mixture);

        JFrame frame = new JFrame("Mixture of Exponential Family Distributions");
        frame.setSize(1000, 1000);
        PlotCanvas canvas = Histogram.plot(data, 50);
        frame.add(canvas);

        double width = (MathEx.max(data) - MathEx.min(data)) / 50;
        double[][] p = new double[400][2];
        for (int i = 0; i < p.length; i++) {
            p[i][0] = -10 + i*0.1;
            p[i][1] = mixture.p(p[i][0]) * width;
        }

        canvas.line(p, Color.RED);

        frame.add(QQPlot.plot(data, mixture));

        frame.setVisible(true);
    }
}
