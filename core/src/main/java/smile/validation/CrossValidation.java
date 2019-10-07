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

import java.util.Optional;
import java.util.function.Function;
import java.util.function.BiFunction;

import smile.classification.Classifier;
import smile.data.DataFrame;
import smile.data.formula.Formula;
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

    /**
     * Runs cross validation tests.
     * @return the number of errors.
     */
    public <T> int classification(T[] x, int[] y, BiFunction<T[], int[], Classifier<T>> trainer) {
        int error = 0;

        for (int i = 0; i < k; i++) {
            T[] trainx = MathEx.slice(x, train[i]);
            int[] trainy = MathEx.slice(y, train[i]);
            T[] testx = MathEx.slice(x, test[i]);
            int[] testy = MathEx.slice(y, test[i]);

            Classifier<T> model = trainer.apply(trainx, trainy);
            for (int j = 0; j < testx.length; j++) {
                if (testy[j] != model.predict(testx[j])) error++;
            }
        }

        return error;
    }

    /**
     * Runs cross validation tests.
     * @return root mean squared errors.
     */
    public <T> int classification(DataFrame data, Function<DataFrame, Classifier<T>> trainer) {
        int error = 0;

        for (int i = 0; i < k; i++) {
            Classifier<T> model = trainer.apply(data.of(train[i]));
            DataFrame oob = data.of(test[i]);
            int[] prediction = model.predict(oob);
            int[] y = model.formula().get().response(oob).toIntArray();

            for (int j = 0; j < y.length; j++) {
                if (y[j] != prediction[j]) error++;
            }
        }

        return error;
    }

    /**
     * Runs cross validation tests.
     * @return root mean squared error.
     */
    public <T> double regression(T[] x, double[] y, BiFunction<T[], double[], Regression<T>> trainer) {
        double rmse = 0.0;

        for (int i = 0; i < k; i++) {
            T[] trainx = MathEx.slice(x, train[i]);
            double[] trainy = MathEx.slice(y, train[i]);
            T[] testx = MathEx.slice(x, test[i]);
            double[] testy = MathEx.slice(y, test[i]);

            Regression<T> model = trainer.apply(trainx, trainy);
            for (int j = 0; j < testx.length; j++) {
                double r = testy[j] - model.predict(testx[j]);
                rmse += r * r;
            }
        }

        return Math.sqrt(rmse / x.length);
    }

    /**
     * Runs cross validation tests.
     * @return root mean squared error.
     */
    public <T> double regression(DataFrame data, Function<DataFrame, Regression<T>> trainer) {
        double rmse = 0.0;

        for (int i = 0; i < k; i++) {
            Regression<T> model = trainer.apply(data.of(train[i]));
            DataFrame oob = data.of(test[i]);
            double[] prediction = model.predict(oob);
            double[] y = model.formula().get().response(oob).toDoubleArray();

            for (int j = 0; j < y.length; j++) {
                double r = y[j] - prediction[j];
                rmse += r * r;
            }
        }

        return Math.sqrt(rmse / data.size());
    }

    /**
     * Runs cross validation tests.
     * @return the number of errors.
     */
    public static <T> int classification(int k, T[] x, int[] y, BiFunction<T[], int[], Classifier<T>> trainer) {
        CrossValidation cv = new CrossValidation(x.length, k);
        return cv.classification(x, y, trainer);
    }

    /**
     * Runs cross validation tests.
     * @return the number of errors.
     */
    public static <T> int classification(int k, DataFrame data, Function<DataFrame, Classifier<T>> trainer) {
        CrossValidation cv = new CrossValidation(data.size(), k);
        return cv.classification(data, trainer);
    }

    /**
     * Runs cross validation tests.
     * @return root mean squared error.
     */
    public static <T> double regression(int k, T[] x, double[] y, BiFunction<T[], double[], Regression<T>> trainer) {
        CrossValidation cv = new CrossValidation(x.length, k);
        return cv.regression(x, y, trainer);
    }

    /**
     * Runs cross validation tests.
     * @return root mean squared error.
     */
    public static <T> double regression(int k, DataFrame data, Function<DataFrame, Regression<T>> trainer) {
        CrossValidation cv = new CrossValidation(data.size(), k);
        return cv.regression(data, trainer);
    }
}
