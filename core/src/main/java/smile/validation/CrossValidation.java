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
public class CrossValidation implements Serializable {
    private static final long serialVersionUID = 2L;

    /**
     * The k-fold splits.
     */
    public final Split[] splits;

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

        splits = new Split[k];

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

        int chunk = n / k;
        for (int i = 0; i < k; i++) {
            int start = chunk * i;
            int end = chunk * (i + 1);
            if (i == k-1) end = n;

            int[] train = new int[n - end + start];
            int[] test = new int[end - start];
            for (int j = 0, p = 0, q = 0; j < n; j++) {
                if (j >= start && j < end) {
                    test[p++] = index[j];
                } else {
                    train[q++] = index[j];
                }
            }

            splits[i] = new Split(train, test);
        }
    }

    /**
     * Runs classification cross validation.
     * @param k k-fold cross validation.
     * @param x the samples.
     * @param y the sample labels.
     * @param trainer the lambda to train a model.
     * @return the validation results.
     */
    public static <T, M extends Classifier<T>> ClassificationValidations<T, M> classification(int k, T[] x, int[] y, BiFunction<T[], int[], M> trainer) {
        CrossValidation cv = new CrossValidation(x.length, k);
        return ClassificationValidation.of(cv.splits, x, y, trainer);
    }

    /**
     * Runs classification cross validation.
     * @param k k-fold cross validation.
     * @param formula the model specification.
     * @param data the training/validation data.
     * @param trainer the lambda to train a model.
     * @return the validation results.
     */
    public static <M extends Classifier<Tuple>> ClassificationValidations<Tuple, M> classification(int k, Formula formula, DataFrame data, BiFunction<Formula, DataFrame, M> trainer) {
        CrossValidation cv = new CrossValidation(data.size(), k);
        return ClassificationValidation.of(cv.splits, formula, data, trainer);
    }

    /**
     * Runs regression cross validation.
     * @param k k-fold cross validation.
     * @param x the samples.
     * @param y the response variable.
     * @param trainer the lambda to train a model.
     * @return the validation results.
     */
    public static <T, M extends Regression<T>> RegressionValidations<T, M> regression(int k, T[] x, double[] y, BiFunction<T[], double[], M> trainer) {
        CrossValidation cv = new CrossValidation(x.length, k);
        return RegressionValidation.of(cv.splits, x, y, trainer);
    }

    /**
     * Runs regression cross validation.
     * @param k k-fold cross validation.
     * @param formula the model specification.
     * @param data the training/validation data.
     * @param trainer the lambda to train a model.
     * @return the validation results.
     */
    public static <M extends Regression<Tuple>> RegressionValidations<Tuple, M> regression(int k, Formula formula, DataFrame data, BiFunction<Formula, DataFrame, M> trainer) {
        CrossValidation cv = new CrossValidation(data.size(), k);
        return RegressionValidation.of(cv.splits, formula, data, trainer);
    }
}
