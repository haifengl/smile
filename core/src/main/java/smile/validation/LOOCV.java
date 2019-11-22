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
 * Leave-one-out cross validation. LOOCV uses a single observation
 * from the original sample as the validation data, and the remaining
 * observations as the training data. This is repeated such that each
 * observation in the sample is used once as the validation data. This is
 * the same as a K-fold cross-validation with K being equal to the number of
 * observations in the original sample. Leave-one-out cross-validation is
 * usually very expensive from a computational point of view because of the
 * large number of times the training process is repeated.
 * 
 * @author Haifeng Li
 */
public class LOOCV {
    /**
     * The index of training instances.
     */
    public final int[][] train;
    /**
     * The index of testing instances.
     */
    public final int[] test;

    /**
     * Constructor.
     * @param n the number of samples.
     */
    public LOOCV(int n) {
        if (n < 0) {
            throw new IllegalArgumentException("Invalid sample size: " + n);
        }

        train = new int[n][n-1];
        test = new int[n];

        for (int i = 0; i < n; i++) {
            test[i] = i;
            for (int j = 0, p = 0; j < n; j++) {
                if (j != i) {
                    train[i][p++] = j;
                }
            }
        }
    }

    /**
     * Runs leave-one-out cross validation tests.
     * @return the predictions.
     */
    public static <T> int[] classification(T[] x, int[] y, BiFunction<T[], int[], Classifier<T>> trainer) {
        int n = x.length;
        LOOCV cv = new LOOCV(n);
        int[] prediction = new int[n];

        for (int i = 0; i < n; i++) {
            T[] trainx = MathEx.slice(x, cv.train[i]);
            int[] trainy = MathEx.slice(y, cv.train[i]);

            Classifier<T> model = trainer.apply(trainx, trainy);
            prediction[cv.test[i]] = model.predict(x[cv.test[i]]);
        }

        return prediction;
    }

    /**
     * Runs leave-one-out cross validation tests.
     * @return the predictions.
     */
    public static int[] classification(Formula formula, DataFrame data, BiFunction<Formula, DataFrame, DataFrameClassifier> trainer) {
        int n = data.size();
        LOOCV cv = new LOOCV(n);
        int[] prediction = new int[n];

        for (int i = 0; i < n; i++) {
            DataFrameClassifier model = trainer.apply(formula, data.of(cv.train[i]));
            prediction[cv.test[i]] = model.predict(data.get(cv.test[i]));
        }

        return prediction;
    }

    /**
     * Runs leave-one-out cross validation tests.
     * @return the predictions.
     */
    public static <T> double[] regression(T[] x, double[] y, BiFunction<T[], double[], Regression<T>> trainer) {
        int n = x.length;
        LOOCV cv = new LOOCV(n);
        double[] prediction = new double[n];

        for (int i = 0; i < n; i++) {
            T[] trainx = MathEx.slice(x, cv.train[i]);
            double[] trainy = MathEx.slice(y, cv.train[i]);

            Regression<T> model = trainer.apply(trainx, trainy);
            prediction[cv.test[i]] = model.predict(x[cv.test[i]]);
        }

        return prediction;
    }

    /**
     * Runs leave-one-out cross validation tests.
     * @return the predictions.
     */
    public static double[] regression(Formula formula, DataFrame data, BiFunction<Formula, DataFrame, DataFrameRegression> trainer) {
        int n = data.size();
        LOOCV cv = new LOOCV(n);
        double[] prediction = new double[n];

        for (int i = 0; i < n; i++) {
            DataFrameRegression model = trainer.apply(formula, data.of(cv.train[i]));
            prediction[cv.test[i]] = model.predict(data.get(cv.test[i]));
        }

        return prediction;
    }
}
