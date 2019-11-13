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

package smile.data;

import smile.math.MathEx;
import smile.math.matrix.Matrix;
import smile.stat.distribution.MultivariateGaussianDistribution;

/**
 *
 * @author Haifeng
 */
public class GaussianMixture {

    public static double[][] x = new double[10000][];
    public static int[] y = new int[10000];

    static {
        MathEx.setSeed(19650218); // to get repeatable results.

        double[] mu1 = {1.0, 1.0, 1.0};
        double[][] sigma1 = {{1.0, 0.0, 0.0}, {0.0, 1.0, 0.0}, {0.0, 0.0, 1.0}};
        double[] mu2 = {-2.0, -2.0, -2.0};
        double[][] sigma2 = {{1.0, 0.3, 0.8}, {0.3, 1.0, 0.5}, {0.8, 0.5, 1.0}};
        double[] mu3 = {4.0, 2.0, 3.0};
        double[][] sigma3 = {{1.0, 0.8, 0.3}, {0.8, 1.0, 0.5}, {0.3, 0.5, 1.0}};
        double[] mu4 = {3.0, 5.0, 1.0};
        double[][] sigma4 = {{1.0, 0.5, 0.5}, {0.5, 1.0, 0.5}, {0.5, 0.5, 1.0}};

        MultivariateGaussianDistribution g1 = new MultivariateGaussianDistribution(mu1, Matrix.of(sigma1));
        for (int i = 0; i < 2000; i++) {
            x[i] = g1.rand();
            y[i] = 0;
        }

        MultivariateGaussianDistribution g2 = new MultivariateGaussianDistribution(mu2, Matrix.of(sigma2));
        for (int i = 0; i < 3000; i++) {
            x[2000 + i] = g2.rand();
            y[i] = 1;
        }

        MultivariateGaussianDistribution g3 = new MultivariateGaussianDistribution(mu3, Matrix.of(sigma3));
        for (int i = 0; i < 3000; i++) {
            x[5000 + i] = g3.rand();
            y[i] = 2;
        }

        MultivariateGaussianDistribution g4 = new MultivariateGaussianDistribution(mu4, Matrix.of(sigma4));
        for (int i = 0; i < 2000; i++) {
            x[8000 + i] = g4.rand();
            y[i] = 3;
        }
    }
}
