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

package smile.demo.stat.distribution;

import java.awt.GridLayout;

import javax.swing.JFrame;
import javax.swing.JPanel;

import smile.math.Math;
import smile.plot.Histogram;
import smile.plot.PlotCanvas;
import smile.plot.Surface;
import smile.stat.distribution.MultivariateGaussianDistribution;
import smile.stat.distribution.MultivariateGaussianMixture;

/**
 *
 * @author Haifeng Li
 */
@SuppressWarnings("serial")
public class MultivariateGaussianMixtureDemo extends JPanel {
    public MultivariateGaussianMixtureDemo() {
        super(new GridLayout(1,1));

        double[] mu1 = {1.0, 1.0};
        double[][] sigma1 = {{1.0, 0.0}, {0.0, 1.0}};

        double[] mu2 = {-2.0, -2.0};
        double[][] sigma2 = {{1.0, 0.3}, {0.3, 1.0}};

        double[] mu3 = {4.0, 2.0};
        double[][] sigma3 = {{1.0, 0.8}, {0.8, 1.0}};

        double[] mu4 = {3.0, 5.0};
        double[][] sigma4 = {{1.0, 0.5}, {0.5, 1.0}};

        double[][] data = new double[3000][];

        MultivariateGaussianDistribution g1 = new MultivariateGaussianDistribution(mu1, sigma1);
        double[][] data1 = new double[500][];
        for (int i = 0; i < data1.length; i++) {
            data[i] = g1.rand();
            data1[i] = data[i];
        }

        MultivariateGaussianDistribution g2 = new MultivariateGaussianDistribution(mu2, sigma2);
        double[][] data2 = new double[1000][];
        for (int i = 0; i < data2.length; i++) {
            data[500+i] = g2.rand();
            data2[i] = data[500+i];
        }

        MultivariateGaussianDistribution g3 = new MultivariateGaussianDistribution(mu3, sigma3);
        double[][] data3 = new double[1000][];
        for (int i = 0; i < data3.length; i++) {
            data[1500+i] = g3.rand();
            data3[i] = data[1500+i];
        }

        MultivariateGaussianDistribution g4 = new MultivariateGaussianDistribution(mu4, sigma4);
        double[][] data4 = new double[500][];
        for (int i = 0; i < data4.length; i++) {
            data[2500+i] = g4.rand();
            data4[i] = data[2500+i];
        }

        MultivariateGaussianMixture mixture = new MultivariateGaussianMixture(data, 4);

        int n = 40;
        int m = 40;
        double[][][] z = new double[m][n][3];
        for (int i = 0; i < m; i++) {
            for (int j = 0; j < n; j++) {
                z[i][j][0] = 10.0 * (i - 12) / m;
                z[i][j][1] = 10.0 * (j - 12) / n;
                double[] point = {z[i][j][0], z[i][j][1]};
                z[i][j][2] = mixture.p(point);
            }
        }

        PlotCanvas canvas = Surface.plot(z);
        canvas.setTitle("Gaussian Mixture");
        add(canvas);
    }
    
    @Override
    public String toString() {
        return "Multivariate Gaussian Mixture";
    }

    public static void main(String[] args) {
        double[] mu1 = {1.0, 1.0, 1.0};
        double[][] sigma1 = {{1.0, 0.0, 0.0}, {0.0, 1.0, 0.0}, {0.0, 0.0, 1.0}};

        double[] mu2 = {-2.0, -2.0, -2.0};
        double[][] sigma2 = {{1.0, 0.3, 0.8}, {0.3, 1.0, 0.5}, {0.8, 0.5, 1.0}};

        double[] mu3 = {4.0, 2.0, 3.0};
        double[][] sigma3 = {{1.0, 0.8, 0.3}, {0.8, 1.0, 0.5}, {0.3, 0.5, 1.0}};

        double[] mu4 = {3.0, 5.0, 1.0};
        double[][] sigma4 = {{1.0, 0.5, 0.5}, {0.5, 1.0, 0.5}, {0.5, 0.5, 1.0}};

        double[][] data = new double[3000][];

        MultivariateGaussianDistribution g1 = new MultivariateGaussianDistribution(mu1, sigma1);
        double[][] data1 = new double[500][];
        for (int i = 0; i < data1.length; i++) {
            data[i] = g1.rand();
            data1[i] = data[i];
        }

        MultivariateGaussianDistribution g2 = new MultivariateGaussianDistribution(mu2, sigma2);
        double[][] data2 = new double[1000][];
        for (int i = 0; i < data2.length; i++) {
            data[500+i] = g2.rand();
            data2[i] = data[500+i];
        }

        MultivariateGaussianDistribution g3 = new MultivariateGaussianDistribution(mu3, sigma3);
        double[][] data3 = new double[1000][];
        for (int i = 0; i < data3.length; i++) {
            data[1500+i] = g3.rand();
            data3[i] = data[1500+i];
        }

        MultivariateGaussianDistribution g4 = new MultivariateGaussianDistribution(mu4, sigma4);
        double[][] data4 = new double[500][];
        for (int i = 0; i < data4.length; i++) {
            data[2500+i] = g4.rand();
            data4[i] = data[2500+i];
        }

        System.out.println(new MultivariateGaussianDistribution(data1));
        System.out.println(new MultivariateGaussianDistribution(data2));
        System.out.println(new MultivariateGaussianDistribution(data3));
        System.out.println(new MultivariateGaussianDistribution(data4));

        MultivariateGaussianMixture mixture = new MultivariateGaussianMixture(data, 4);
        System.out.println(mixture);

        mixture = new MultivariateGaussianMixture(data, 6);
        System.out.println(mixture);

        mixture = new MultivariateGaussianMixture(data);
        System.out.println(mixture);

        double[] mu100 = new double[20];
        double[] sigma100 = new double[20];
        for (int i = 0; i < sigma100.length; i++)
            sigma100[i] = 1;
        MultivariateGaussianDistribution g100 = new MultivariateGaussianDistribution(mu100, sigma100);
        double[] data100 = new double[500000];
        for (int i = 0; i < data100.length; i++) {
            double[] x = g100.rand();
            data100[i] = Math.norm(x);
        }

        System.out.println(Math.mean(data100));
        System.out.println(Math.sd(data100));

        JFrame frame = new JFrame("Norm of Samples of Gaussian Mixture of Dimension 100");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLocationRelativeTo(null);
        frame.getContentPane().add(Histogram.plot(data100, 100));
        frame.setVisible(true);
    }
}
