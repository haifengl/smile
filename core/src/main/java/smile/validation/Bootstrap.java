/*******************************************************************************
 * Copyright (c) 2010-2020 Haifeng Li. All rights reserved.
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
 ******************************************************************************/

package smile.validation;

import java.io.Serializable;
import java.util.function.BiFunction;
import smile.classification.Classifier;
import smile.data.DataFrame;
import smile.data.Tuple;
import smile.data.formula.Formula;
import smile.math.MathEx;
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
public class Bootstrap implements Serializable {
    private static final long serialVersionUID = 2L;

    /**
     * The bootstrap samples and OOB (out-of-bag) samples as test data.
     */
    public final Split[] splits;


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

        splits = new Split[k];

        for (int j = 0; j < k; j++) {
            boolean[] hit = new boolean[n];
            int hits = 0;

            int[] train = new int[n];
            for (int i = 0; i < n; i++) {
                int r = MathEx.randomInt(n);
                train[i] = r;
                if (!hit[r]) {
                    hits++;
                    hit[r] = true;
                }
            }

            int[] test = new int[n - hits];
            for (int i = 0, p = 0; i < n; i++) {
                if (!hit[i]) {
                    test[p++] = i;
                }
            }

            splits[j] = new Split(train, test);
        }
    }

    /**
     * Runs classification bootstrap validation.
     * @param k k-fold bootstrap sampling.
     * @param x the samples.
     * @param y the sample labels.
     * @param trainer the lambda to train a model.
     * @return the validation results.
     */
    public static <T, M extends Classifier<T>> ClassificationValidations<T, M> classification(int k, T[] x, int[] y, BiFunction<T[], int[], M> trainer) {
        Bootstrap cv = new Bootstrap(x.length, k);
        return ClassificationValidation.of(cv.splits, x, y, trainer);
    }

    /**
     * Runs classification bootstrap validation.
     * @param k k-fold bootstrap sampling.
     * @param formula the model specification.
     * @param data the training/validation data.
     * @param trainer the lambda to train a model.
     * @return the validation results.
     */
    public static <M extends Classifier<Tuple>> ClassificationValidations<Tuple, M> classification(int k, Formula formula, DataFrame data, BiFunction<Formula, DataFrame, M> trainer) {
        Bootstrap cv = new Bootstrap(data.size(), k);
        return ClassificationValidation.of(cv.splits, formula, data, trainer);
    }

    /**
     * Runs regression bootstrap validation.
     * @param k k-fold bootstrap sampling.
     * @param x the samples.
     * @param y the response variable.
     * @param trainer the lambda to train a model.
     * @return the validation results.
     */
    public static <T, M extends Regression<T>> RegressionValidations<T, M> regression(int k, T[] x, double[] y, BiFunction<T[], double[], M> trainer) {
        Bootstrap cv = new Bootstrap(x.length, k);
        return RegressionValidation.of(cv.splits, x, y, trainer);
    }

    /**
     * Runs regression bootstrap validation.
     * @param k k-fold bootstrap sampling.
     * @param formula the model specification.
     * @param data the training/validation data.
     * @param trainer the lambda to train a model.
     * @return the validation results.
     */
    public static <M extends Regression<Tuple>> RegressionValidations<Tuple, M> regression(int k, Formula formula, DataFrame data, BiFunction<Formula, DataFrame, M> trainer) {
        Bootstrap cv = new Bootstrap(data.size(), k);
        return RegressionValidation.of(cv.splits, formula, data, trainer);
    }
}
