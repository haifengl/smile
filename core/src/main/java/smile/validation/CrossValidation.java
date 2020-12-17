/*
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
 */

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
    static Bag[] of(int n, int k) {
        return of(n, k, true);
    }

    /**
     * Creates a k-fold cross validation.
     * @param n the number of samples.
     * @param k the number of rounds of cross validation.
     * @param shuffle whether to shuffle samples before splitting.
     * @return k-fold data splits.
     */
    static Bag[] of(int n, int k, boolean shuffle) {
        if (n < 0) {
            throw new IllegalArgumentException("Invalid sample size: " + n);
        }

        if (k < 0 || k > n) {
            throw new IllegalArgumentException("Invalid number of CV rounds: " + k);
        }

        Bag[] bags = new Bag[k];

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

            bags[i] = new Bag(train, test);
        }

        return bags;
    }

    /**
     * Cross validation with stratified folds. The folds are made by
     * preserving the percentage of samples for each group.
     *
     * @param category the strata labels.
     * @param k the number of folds.
     * @return k-fold data splits.
     */
    static Bag[] of(int[] category, int k) {
        if (k < 0) {
            throw new IllegalArgumentException("Invalid number of folds: " + k);
        }

        int[] unique = MathEx.unique(category);
        int m = unique.length;

        Arrays.sort(unique);
        IntSet encoder = new IntSet(unique);

        int n = category.length;
        int[] y = category;
        if (unique[0] != 0 || unique[m-1] != m-1) {
            y = new int[n];
            for (int i = 0; i < n; i++) {
                y[i] = encoder.indexOf(category[i]);
            }
        }

        // # of samples in each strata
        int[] ni = new int[m];
        for (int i : y) ni[i]++;

        int min = MathEx.min(ni);
        if (min < k) {
            org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(CrossValidation.class);
            logger.warn("The least populated class has only {} members, which is less than k={}.", min, k);
        }

        int[][] strata = new int[m][];
        for (int i = 0; i < m; i++) {
            strata[i] = new int[ni[i]];
        }

        int[] pos = new int[m];
        for (int i = 0; i < n; i++) {
            int j =  y[i];
            strata[j][pos[j]++] = i;
        }

        int[] chunk = new int[m];
        for (int i = 0; i < m; i++) {
            chunk[i] = Math.max(1, ni[i] / k);
        }

        Bag[] bags = new Bag[k];
        for (int i = 0; i < k; i++) {
            int p = 0;
            int q = 0;
            int[] train = new int[n];
            int[] test = new int[n];

            for (int j = 0; j < m; j++) {
                int size = ni[j];
                int start = chunk[j] * i;
                int end = chunk[j] * (i + 1);
                if (i == k - 1) end = size;

                int[] stratum = strata[j];
                for (int l = 0; l < size; l++) {
                    if (l >= start && l < end) {
                        test[q++] = stratum[l];
                    } else {
                        train[p++] = stratum[l];
                    }
                }
            }

            train = Arrays.copyOf(train, p);
            test = Arrays.copyOf(test, q);
            // Samples are in order of strata. It is better to shuffle.
            MathEx.permutate(train);
            MathEx.permutate(test);
            bags[i] = new Bag(train, test);
        }

        return bags;
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
     * @param group the group labels of the samples.
     * @param k the number of folds.
     * @return k-fold data splits.
     */
    static Bag[] nonoverlap(int[] group, int k) {
        if (k < 0) {
            throw new IllegalArgumentException("Invalid number of folds: " + k);
        }

        int[] unique = MathEx.unique(group);
        int m = unique.length;

        if (k > m) {
            throw new IllegalArgumentException("k-fold must be not greater than the than number of groups");
        }

        Arrays.sort(unique);
        IntSet encoder = new IntSet(unique);

        int n = group.length;
        int[] y = group;
        if (unique[0] != 0 || unique[m-1] != m-1) {
            y = new int[n];
            for (int i = 0; i < n; i++) {
                y[i] = encoder.indexOf(group[i]);
            }
        }

        // sort the groups by number of samples so that we can distribute
        // test samples from largest groups first
        int[] ni = new int[m];
        for (int i : y) ni[i]++;

        int[] index = QuickSort.sort(ni);

        // distribute test samples into k folds one group at a time,
        // from the largest to the smallest group.
        // always put test samples into the fold with the fewest samples.
        int[] foldSize = new int[k];
        int[] group2Fold = new int[m];

        for (int i = m - 1; i >= 0; i--) {
            int smallestFold = MathEx.whichMin(foldSize);
            foldSize[smallestFold] += ni[i];
            group2Fold[index[i]] = smallestFold;
        }

        Bag[] bags = new Bag[k];
        for (int i = 0; i < k; i++) {
            int[] train = new int[n - foldSize[i]];
            int[] test = new int[foldSize[i]];
            bags[i] = new Bag(train, test);

            for (int j = 0, trainIndex = 0, testIndex = 0; j < n; j++) {
                if (group2Fold[y[j]] == i) {
                    test[testIndex++] = j;
                } else {
                    train[trainIndex++] = j;
                }
            }
        }

        return bags;
    }

    /**
     * Runs classification cross validation.
     * @param k k-fold cross validation.
     * @param x the samples.
     * @param y the sample labels.
     * @param trainer the lambda to train a model.
     * @param <T> the data type of samples.
     * @param <M> the model type.
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
     * @param <M> the model type.
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
     * @param <T> the data type of samples.
     * @param <M> the model type.
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
     * @param <M> the model type.
     * @return the validation results.
     */
    static <M extends DataFrameRegression> RegressionValidations<M> regression(int k, Formula formula, DataFrame data, BiFunction<Formula, DataFrame, M> trainer) {
        return RegressionValidation.of(of(data.size(), k), formula, data, trainer);
    }
}
