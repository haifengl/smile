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

import java.util.Arrays;
import java.util.function.BiFunction;
import java.util.stream.IntStream;
import smile.classification.Classifier;
import smile.classification.DataFrameClassifier;
import smile.data.DataFrame;
import smile.data.formula.Formula;
import smile.math.MathEx;
import smile.regression.Regression;
import smile.regression.DataFrameRegression;
import smile.sort.QuickSort;
import smile.util.IntSet;

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
public interface CrossValidation {
    /**
     * Creates a k-fold cross validation.
     * @param n the number of samples.
     * @param k the number of rounds of cross validation.
     * @return k-fold data splits.
     */
    static Split[] of(int n, int k) {
        return of(n, k, true);
    }

    /**
     * Creates a k-fold cross validation.
     * @param n the number of samples.
     * @param k the number of rounds of cross validation.
     * @param shuffle whether to shuffle samples before splitting.
     * @return k-fold data splits.
     */
    static Split[] of(int n, int k, boolean shuffle) {
        if (n < 0) {
            throw new IllegalArgumentException("Invalid sample size: " + n);
        }

        if (k < 0 || k > n) {
            throw new IllegalArgumentException("Invalid number of CV rounds: " + k);
        }

        Split[] splits = new Split[k];

        int[] index = IntStream.range(0, n).toArray();
        if (shuffle){
            MathEx.permutate(index);
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

        return splits;
    }

    /**
     * Cross validation with non-overlapping groups.
     * The same group will not appear in two different folds (the number
     * of distinct groups has to be at least equal to the number of folds).
     * The folds are approximately balanced in the sense that the number
     * of distinct groups is approximately the same in each fold.
     * <p>
     * This is useful when the i.i.d. assumption is known to be broken by
     * the underlying process generating the data. For example, when we have
     * multiple samples by the same user and want to make sure that the model
     * doesn't learn user-specific features that don't generalize to unseen
     * users, this approach could be used.
     *
     * @param group the group labels for the samples in [0, g), where g
     *              is the number of groups.
     * @param k the number of folds.
     */
    static Split[] group(int[] group, int k) {
        if (k < 0) {
            throw new IllegalArgumentException("Invalid number of folds: " + k);
        }

        int[] unique = MathEx.unique(group);
        int g = unique.length;

        if (k > g) {
            throw new IllegalArgumentException("k-fold must be not greater than the than number of groups");
        }

        Arrays.sort(unique);
        IntSet encoder = new IntSet(unique);

        int n = group.length;
        if (unique[0] != 0 || unique[g-1] != g-1) {
            int[] y = new int[n];
            for (int i = 0; i < n; i++) {
                y[i] = encoder.indexOf(group[i]);
            }
            group = y;
        }

        // sort the groups by number of samples so that we can distribute
        // test samples from largest groups first
        int[] ni = new int[g];
        for (int i : group) ni[i]++;

        int[] index = QuickSort.sort(ni);

        // distribute test samples into k folds one group at a time,
        // from the largest to the smallest group,
        // always putting test samples into the fold with the fewest samples
        int[] foldSize = new int[k];
        int[] group2Fold = new int[g];

        for (int i = g - 1; i >= 0; i--) {
            int smallestFold = MathEx.whichMin(foldSize);
            foldSize[smallestFold] += ni[i];
            group2Fold[index[i]] = smallestFold;
        }

        Split[] splits = new Split[k];
        for (int i = 0; i < k; i++) {
            int[] train = new int[n - foldSize[i]];
            int[] test = new int[foldSize[i]];
            splits[i] = new Split(train, test);

            for (int j = 0, trainIndex = 0, testIndex = 0; j < n; j++) {
                if (group2Fold[group[j]] == i) {
                    test[testIndex++] = j;
                } else {
                    train[trainIndex++] = j;
                }
            }
        }

        return splits;
    }

    /**
     * Runs classification cross validation.
     * @param k k-fold cross validation.
     * @param x the samples.
     * @param y the sample labels.
     * @param trainer the lambda to train a model.
     * @return the validation results.
     */
    static <T, M extends Classifier<T>> ClassificationValidations<M> classification(int k, T[] x, int[] y, BiFunction<T[], int[], M> trainer) {
        return ClassificationValidation.of(of(x.length, k), x, y, trainer);
    }

    /**
     * Runs classification cross validation.
     * @param k k-fold cross validation.
     * @param formula the model specification.
     * @param data the training/validation data.
     * @param trainer the lambda to train a model.
     * @return the validation results.
     */
    static <M extends DataFrameClassifier> ClassificationValidations<M> classification(int k, Formula formula, DataFrame data, BiFunction<Formula, DataFrame, M> trainer) {
        return ClassificationValidation.of(of(data.size(), k), formula, data, trainer);
    }

    /**
     * Runs regression cross validation.
     * @param k k-fold cross validation.
     * @param x the samples.
     * @param y the response variable.
     * @param trainer the lambda to train a model.
     * @return the validation results.
     */
    static <T, M extends Regression<T>> RegressionValidations<M> regression(int k, T[] x, double[] y, BiFunction<T[], double[], M> trainer) {
        return RegressionValidation.of(of(x.length, k), x, y, trainer);
    }

    /**
     * Runs regression cross validation.
     * @param k k-fold cross validation.
     * @param formula the model specification.
     * @param data the training/validation data.
     * @param trainer the lambda to train a model.
     * @return the validation results.
     */
    static <M extends DataFrameRegression> RegressionValidations<M> regression(int k, Formula formula, DataFrame data, BiFunction<Formula, DataFrame, M> trainer) {
        return RegressionValidation.of(of(data.size(), k), formula, data, trainer);
    }
}
