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
import smile.classification.Classifier;
import smile.classification.DataFrameClassifier;
import smile.data.DataFrame;
import smile.data.formula.Formula;
import smile.math.MathEx;
import smile.regression.DataFrameRegression;
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

    /**
     * Runs cross validation tests.
     * @return the predictions.
     */
    public <T> int[] classification(T[] x, int[] y, BiFunction<T[], int[], Classifier<T>> trainer) {
        int[] prediction = new int[x.length];

        for (int i = 0; i < k; i++) {
            T[] trainx = MathEx.slice(x, train[i]);
            int[] trainy = MathEx.slice(y, train[i]);

            Classifier<T> model = trainer.apply(trainx, trainy);

            for (int j : test[i]) {
                prediction[j] = model.predict(x[j]);
            }
        }

        return prediction;
    }

    /**
     * Runs cross validation tests.
     * @return the predictions.
     */
    public int[] classification(Formula formula, DataFrame data, BiFunction<Formula, DataFrame, DataFrameClassifier> trainer) {
        int[] prediction = new int[data.size()];

        for (int i = 0; i < k; i++) {
            DataFrameClassifier model = trainer.apply(formula, data.of(train[i]));
            for (int j : test[i]) {
                prediction[j] = model.predict(data.get(j));
            }
        }

        return prediction;
    }

    /**
     * Runs cross validation tests.
     * @return the predictions.
     */
    public <T> double[] regression(T[] x, double[] y, BiFunction<T[], double[], Regression<T>> trainer) {
        double[] prediction = new double[x.length];

        for (int i = 0; i < k; i++) {
            T[] trainx = MathEx.slice(x, train[i]);
            double[] trainy = MathEx.slice(y, train[i]);

            Regression<T> model = trainer.apply(trainx, trainy);

            for (int j : test[i]) {
                prediction[j] = model.predict(x[j]);
            }
        }

        return prediction;
    }

    /**
     * Runs cross validation tests.
     * @return the predictions.
     */
    public double[] regression(Formula formula, DataFrame data, BiFunction<Formula, DataFrame, DataFrameRegression> trainer) {
        double[] prediction = new double[data.size()];

        for (int i = 0; i < k; i++) {
            DataFrameRegression model = trainer.apply(formula, data.of(train[i]));

            for (int j : test[i]) {
                prediction[j] = model.predict(data.get(j));
            }
        }

        return prediction;
    }

    /**
     * Runs cross validation tests.
     * @return the predictions.
     */
    public static <T> int[] classification(int k, T[] x, int[] y, BiFunction<T[], int[], Classifier<T>> trainer) {
        CrossValidation cv = new CrossValidation(x.length, k);
        return cv.classification(x, y, trainer);
    }

    /**
     * Runs cross validation tests.
     * @return the predictions.
     */
    public static int[] classification(int k, Formula formula, DataFrame data, BiFunction<Formula, DataFrame, DataFrameClassifier> trainer) {
        CrossValidation cv = new CrossValidation(data.size(), k);
        return cv.classification(formula, data, trainer);
    }

    /**
     * Runs cross validation tests.
     * @return the predictions.
     */
    public static <T> double[] regression(int k, T[] x, double[] y, BiFunction<T[], double[], Regression<T>> trainer) {
        CrossValidation cv = new CrossValidation(x.length, k);
        return cv.regression(x, y, trainer);
    }

    /**
     * Runs cross validation tests.
     * @return the predictions.
     */
    public static double[] regression(int k, Formula formula, DataFrame data, BiFunction<Formula, DataFrame, DataFrameRegression> trainer) {
        CrossValidation cv = new CrossValidation(data.size(), k);
        return cv.regression(formula, data, trainer);
    }
}
