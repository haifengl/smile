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

package smile.validation;

import java.util.function.Function;
import java.util.function.BiFunction;
import smile.math.MathEx;
import smile.regression.Regression;

/**
 * Cross-validation is a technique for assessing how the results of a
 * statistical analysis will generalize to an independent data set.
 * It is mainly used in settings where the goal is prediction, and one
 * wants to estimate how accurately a predictive model will perform in
 * practice. One round of cross-validation involves partitioning a sample
 * of data into complementary subsets, performing the analysis on one subset
 * (called the training set), and validating the analysis on the other subset
 * (called the validation set or testing set). To reduce variability, multiple
 * rounds of cross-validation are performed using different partitions, and the
 * validation results are averaged over the rounds.
 *
 * @author Haifeng Li
 */
public class CrossValidation {
    /**
     * The number of rounds of cross validation.
     */
    public final int k;
    /**
     * The index of training instances.
     */
    public final int[][] train;
    /**
     * The index of testing instances.
     */
    public final int[][] test;

    /**
     * Constructor.
     * @param n the number of samples.
     * @param k the number of rounds of cross validation.
     */
    public CrossValidation(int n, int k) {
        this(n, k, true);
    }
    
    /**
     * Constructor.
     * @param n the number of samples.
     * @param k the number of rounds of cross validation.
     * @param permutate determiner of index permutation
     */
    public CrossValidation(int n, int k, boolean permutate) {
        if (n < 0) {
            throw new IllegalArgumentException("Invalid sample size: " + n);
        }

        if (k < 0 || k > n) {
            throw new IllegalArgumentException("Invalid number of CV rounds: " + k);
        }

        this.k = k;

        int[] index;
        if (permutate){
            index = MathEx.permutate(n);
        }
        else{
            index = new int[n];
            for (int i = 0; i < n; i++) {
                index[i] = i;
            }
        }

        train = new int[k][];
        test = new int[k][];

        int chunk = n / k;
        for (int i = 0; i < k; i++) {
            int start = chunk * i;
            int end = chunk * (i + 1);
            if (i == k-1) end = n;

            train[i] = new int[n - end + start];
            test[i] = new int[end - start];
            for (int j = 0, p = 0, q = 0; j < n; j++) {
                if (j >= start && j < end) {
                    test[i][p++] = index[j];
                } else {
                    train[i][q++] = index[j];
                }
            }
        }
    }

    public <T> double test(Function<int[], Regression<T>> trainer, Function<int[], double[]> tester) {
        double rss = 0.0;
        for (int i = 0; i < k; i++) {
            Regression<T> model = trainer.apply(train[i]);
            double[] error = tester.apply(test[i]);
            for (double e : error) {
                rss += e * e;
            }
        }

        return rss;
    }

    public double test(double[][] x, double[] y, BiFunction<double[][], double[], Regression<double[]>> trainer) {
        double rss = 0.0;

        for (int i = 0; i < k; i++) {
            double[][] trainx = MathEx.slice(x, train[i]);
            double[] trainy = MathEx.slice(y, train[i]);
            double[][] testx = MathEx.slice(x, test[i]);
            double[] testy = MathEx.slice(y, test[i]);

            Regression<double[]> model = trainer.apply(trainx, trainy);
            for (int j = 0; j < testx.length; j++) {
                double r = testy[j] - model.predict(testx[j]);
                rss += r * r;
            }
        }

        return Math.sqrt(rss / x.length);
    }
}
