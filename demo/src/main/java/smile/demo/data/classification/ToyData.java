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

package smile.demo.data.classification;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintStream;

import smile.math.MathEx;
import smile.stat.distribution.MultivariateGaussianDistribution;

/**
 * A class to generate toy data for classification by the model in the book
 * The Elements of Statistical Learning, Section 2.3.3.
 *
 * @author Haifeng Li
 */
public class ToyData {

    private int k = 10;
    private double[] prob = new double[k];
    private double[][] m;
    private double[] m1 = {0, 1};
    private double[] m2 = {1, 0};
    private double[] var = {1, 1};
    private double[] v = {0.2, 0.2};

    /**
     * Constructor.
     */
    public ToyData() {
        for (int i = 0; i < k; i++) {
            prob[i] = 1.0 / k;
        }

        m = new double[2 * k][];

        MultivariateGaussianDistribution gauss = new MultivariateGaussianDistribution(m1, var);
        for (int i = 0; i < k; i++) {
            m[i] = gauss.rand();
        }

        gauss = new MultivariateGaussianDistribution(m2, var);
        for (int i = 0; i < k; i++) {
            m[k + i] = gauss.rand();
        }
    }

    /**
     * Generate n samples from each class.
     */
    public double[][] sample(int n) {
        double[][] samples = new double[2 * n][];

        MultivariateGaussianDistribution[] gauss = new MultivariateGaussianDistribution[k];
        for (int i = 0; i < k; i++) {
            gauss[i] = new MultivariateGaussianDistribution(m[i], v);
        }

        for (int i = 0; i < n; i++) {
            samples[i] = gauss[MathEx.random(prob)].rand();
        }

        for (int i = 0; i < k; i++) {
            gauss[i] = new MultivariateGaussianDistribution(m[k + i], v);
        }

        for (int i = 0; i < n; i++) {
            samples[n + i] = gauss[MathEx.random(prob)].rand();
        }

        return samples;
    }

    public static void main(String[] argv) {
        ToyData toy = new ToyData();
        int n = 100;
        double[][] s = toy.sample(n);

        try (PrintStream p = new PrintStream(new FileOutputStream("toy-train.txt")) ) {

            for (int i = 0; i < s.length; i++) {
                int label = i / n;
                p.format("%d\t% .4f\t% .4f\n", label, s[i][0], s[i][1]);
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

        n = 10000;
        s = toy.sample(n);
        try (PrintStream p = new PrintStream(new FileOutputStream("toy-test.txt")) ) {

            for (int i = 0; i < s.length; i++) {
                int label = i / n;
                p.format("%d\t% .4f\t% .4f\n", label, s[i][0], s[i][1]);
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
    }
}
