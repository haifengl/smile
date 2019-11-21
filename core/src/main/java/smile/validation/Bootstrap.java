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

import java.util.function.BiFunction;
import java.util.function.Function;
import smile.classification.Classifier;
import smile.classification.DataFrameClassifier;
import smile.data.DataFrame;
import smile.data.formula.Formula;
import smile.math.MathEx;
import smile.regression.DataFrameRegression;
import smile.regression.Regression;

/**
 * The bootstrap is a general tool for assessing statistical accuracy. The basic
 * idea is to randomly draw datasets with replacement from the training data,
 * each samples the same size as the original training set. This is done many
 * times (say k = 100), producing k bootstrap datasets. Then we refit the model
 * to each of the bootstrap datasets and examine the behavior of the fits over
 * the k replications.
 *
 * @author Haifeng Li
 */
public class Bootstrap {
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
     * @param k the number of rounds of bootstrap.
     */
    public Bootstrap(int n, int k) {
        if (n < 0) {
            throw new IllegalArgumentException("Invalid sample size: " + n);
        }

        if (k < 0) {
            throw new IllegalArgumentException("Invalid number of bootstrap: " + k);
        }

        this.k = k;
        train = new int[k][n];
        test = new int[k][];

        for (int j = 0; j < k; j++) {
            boolean[] hit = new boolean[n];
            int hits = 0;

            for (int i = 0; i < n; i++) {
                int r = MathEx.randomInt(n);
                train[j][i] = r;
                if (!hit[r]) {
                    hits++;
                    hit[r] = true;
                }
            }

            test[j] = new int[n - hits];
            for (int i = 0, p = 0; i < n; i++) {
                if (!hit[i]) {
                    test[j][p++] = i;
                }
            }
        }
    }

    /**
     * Runs cross validation tests.
     * @return the error rates of each round.
     */
    public <T> double[] classification(T[] x, int[] y, BiFunction<T[], int[], Classifier<T>> trainer) {
        double[] error = new double[k];

        for (int i = 0; i < k; i++) {
            T[] trainx = MathEx.slice(x, train[i]);
            int[] trainy = MathEx.slice(y, train[i]);
            T[] testx = MathEx.slice(x, test[i]);
            int[] testy = MathEx.slice(y, test[i]);

            Classifier<T> model = trainer.apply(trainx, trainy);
            int[] prediction = model.predict(testx);
            error[i] = 1 - Accuracy.of(testy, prediction);
        }

        return error;
    }

    /**
     * Runs cross validation tests.
     * @return the error rates of each round.
     */
    public double[] classification(Formula formula, DataFrame data, BiFunction<Formula, DataFrame, DataFrameClassifier> trainer) {
        double[] error = new double[k];

        for (int i = 0; i < k; i++) {
            DataFrameClassifier model = trainer.apply(formula, data.of(train[i]));

            DataFrame oob = data.of(test[i]);
            int[] prediction = model.predict(oob);
            int[] testy = model.formula().y(oob).toIntArray();

            error[i] = 1 - Accuracy.of(testy, prediction);
        }

        return error;
    }

    /**
     * Runs bootstrap tests.
     * @return the root mean squared error of each round.
     */
    public <T> double[] regression(T[] x, double[] y, BiFunction<T[], double[], Regression<T>> trainer) {
        double[] rmse = new double[k];

        for (int i = 0; i < k; i++) {
            T[] trainx = MathEx.slice(x, train[i]);
            double[] trainy = MathEx.slice(y, train[i]);
            T[] testx = MathEx.slice(x, test[i]);
            double[] testy = MathEx.slice(y, test[i]);

            Regression<T> model = trainer.apply(trainx, trainy);
            double[] prediction = model.predict(testx);
            rmse[i] = RMSE.of(testy, prediction);
        }

        return rmse;
    }

    /**
     * Runs bootstrap tests.
     * @return the root mean squared error of each round.
     */
    public double[] regression(Formula formula, DataFrame data, BiFunction<Formula, DataFrame, DataFrameRegression> trainer) {
        double[] rmse = new double[k];

        for (int i = 0; i < k; i++) {
            DataFrameRegression model = trainer.apply(formula, data.of(train[i]));
            DataFrame oob = data.of(test[i]);
            double[] prediction = model.predict(oob);
            double[] testy = model.formula().y(oob).toDoubleArray();

            rmse[i] = RMSE.of(testy, prediction);
        }

        return rmse;
    }

    /**
     * Runs cross validation tests.
     * @return the error rates of each round.
     */
    public static <T> double[] classification(int k, T[] x, int[] y, BiFunction<T[], int[], Classifier<T>> trainer) {
        Bootstrap cv = new Bootstrap(x.length, k);
        return cv.classification(x, y, trainer);
    }

    /**
     * Runs cross validation tests.
     * @return the error rates of each round.
     */
    public static double[] classification(int k, Formula formula, DataFrame data, BiFunction<Formula, DataFrame, DataFrameClassifier> trainer) {
        Bootstrap cv = new Bootstrap(data.size(), k);
        return cv.classification(formula, data, trainer);
    }

    /**
     * Runs bootstrap tests.
     * @return the root mean squared error of each round.
     */
    public static <T> double[] regression(int k, T[] x, double[] y, BiFunction<T[], double[], Regression<T>> trainer) {
        Bootstrap cv = new Bootstrap(x.length, k);
        return cv.regression(x, y, trainer);
    }

    /**
     * Runs bootstrap tests.
     * @return the root mean squared error of each round.
     */
    public static double[] regression(int k, Formula formula, DataFrame data, BiFunction<Formula, DataFrame, DataFrameRegression> trainer) {
        Bootstrap cv = new Bootstrap(data.size(), k);
        return cv.regression(formula, data, trainer);
    }
}
