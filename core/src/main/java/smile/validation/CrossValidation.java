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
import smile.stat.Sampling;
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
        if (n < 0) {
            throw new IllegalArgumentException("Invalid sample size: " + n);
        }

        if (k < 0 || k > n) {
            throw new IllegalArgumentException("Invalid number of CV rounds: " + k);
        }

        Bag[] bags = new Bag[k];
        int[] index = MathEx.permutate(n);

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
    static Bag[] stratify(int[] category, int k) {
        if (k < 0) {
            throw new IllegalArgumentException("Invalid number of folds: " + k);
        }

        int[][] strata = Sampling.strata(category);

        int min = Arrays.stream(strata).mapToInt(stratum -> stratum.length).min().getAsInt();
        if (min < k) {
            org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(CrossValidation.class);
            logger.warn("The least populated class has only {} members, which is less than k={}.", min, k);
        }

        int n = category.length;
        int m = strata.length;

        // Shuffle every strata so that we can get different
        // splits in repeated cross validation.
        for (int[] stratum : strata) {
            MathEx.permutate(stratum);
        }

        int[] chunk = new int[m];
        for (int i = 0; i < m; i++) {
            chunk[i] = Math.max(1, strata[i].length / k);
        }

        Bag[] bags = new Bag[k];
        for (int i = 0; i < k; i++) {
            int p = 0;
            int q = 0;
            int[] train = new int[n];
            int[] test = new int[n];

            for (int j = 0; j < m; j++) {
                int size = strata[j].length;
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
     * Cross validation of classification.
     * @param k k-fold cross validation.
     * @param x the samples.
     * @param y the sample labels.
     * @param trainer the lambda to train a model.
     * @param <T> the data type of samples.
     * @param <M> the model type.
     * @return the validation results.
     */
    static <T, M extends Classifier<T>> ClassificationValidations<M> classification(int k, T[] x, int[] y, BiFunction<T[], int[], M> trainer) {
        Bag[] bags = of(x.length, k);
        return ClassificationValidation.of(bags, x, y, trainer);
    }

    /**
     * Cross validation of classification.
     * @param k k-fold cross validation.
     * @param formula the model specification.
     * @param data the training/validation data.
     * @param trainer the lambda to train a model.
     * @param <M> the model type.
     * @return the validation results.
     */
    static <M extends DataFrameClassifier> ClassificationValidations<M> classification(int k, Formula formula, DataFrame data, BiFunction<Formula, DataFrame, M> trainer) {
        Bag[] bags = of(data.size(), k);
        return ClassificationValidation.of(bags, formula, data, trainer);
    }

    /**
     * Repeated cross validation of classification.
     * @param round the number of rounds of repeated cross validation.
     * @param k k-fold cross validation.
     * @param x the samples.
     * @param y the sample labels.
     * @param trainer the lambda to train a model.
     * @param <T> the data type of samples.
     * @param <M> the model type.
     * @return the validation results.
     */
    static <T, M extends Classifier<T>> ClassificationValidations<M> classification(int round, int k, T[] x, int[] y, BiFunction<T[], int[], M> trainer) {
        if (round < 1) {
            throw new IllegalArgumentException("Invalid round: " + round);
        }

        Bag[] bags = IntStream.range(0, round)
                .mapToObj(i -> of(x.length, k))
                .flatMap(Arrays::stream)
                .toArray(Bag[]::new);
        return ClassificationValidation.of(bags, x, y, trainer);
    }

    /**
     * Repeated cross validation of classification.
     * @param round the number of rounds of repeated cross validation.
     * @param k k-fold cross validation.
     * @param formula the model specification.
     * @param data the training/validation data.
     * @param trainer the lambda to train a model.
     * @param <M> the model type.
     * @return the validation results.
     */
    static <M extends DataFrameClassifier> ClassificationValidations<M> classification(int round, int k, Formula formula, DataFrame data, BiFunction<Formula, DataFrame, M> trainer) {
        if (round < 1) {
            throw new IllegalArgumentException("Invalid round: " + round);
        }

        Bag[] bags = IntStream.range(0, round)
                .mapToObj(i -> of(data.size(), k))
                .flatMap(Arrays::stream)
                .toArray(Bag[]::new);
        return ClassificationValidation.of(bags, formula, data, trainer);
    }

    /**
     * Stratified cross validation of classification.
     * @param k k-fold cross validation.
     * @param x the samples.
     * @param y the sample labels.
     * @param trainer the lambda to train a model.
     * @param <T> the data type of samples.
     * @param <M> the model type.
     * @return the validation results.
     */
    static <T, M extends Classifier<T>> ClassificationValidations<M> stratify(int k, T[] x, int[] y, BiFunction<T[], int[], M> trainer) {
        Bag[] bags = stratify(y, k);
        return ClassificationValidation.of(bags, x, y, trainer);
    }

    /**
     * Stratified cross validation of classification.
     * @param k k-fold cross validation.
     * @param formula the model specification.
     * @param data the training/validation data.
     * @param trainer the lambda to train a model.
     * @param <M> the model type.
     * @return the validation results.
     */
    static <M extends DataFrameClassifier> ClassificationValidations<M> stratify(int k, Formula formula, DataFrame data, BiFunction<Formula, DataFrame, M> trainer) {
        int[] y = formula.y(data).toIntArray();
        Bag[] bags = stratify(y, k);
        return ClassificationValidation.of(bags, formula, data, trainer);
    }

    /**
     * Repeated stratified cross validation of classification.
     * @param round the number of rounds of repeated cross validation.
     * @param k k-fold cross validation.
     * @param x the samples.
     * @param y the sample labels.
     * @param trainer the lambda to train a model.
     * @param <T> the data type of samples.
     * @param <M> the model type.
     * @return the validation results.
     */
    static <T, M extends Classifier<T>> ClassificationValidations<M> stratify(int round, int k, T[] x, int[] y, BiFunction<T[], int[], M> trainer) {
        if (round < 1) {
            throw new IllegalArgumentException("Invalid round: " + round);
        }

        Bag[] bags = IntStream.range(0, round)
                .mapToObj(i -> stratify(y, k))
                .flatMap(Arrays::stream)
                .toArray(Bag[]::new);
        return ClassificationValidation.of(bags, x, y, trainer);
    }

    /**
     * Repeated stratified cross validation of classification.
     * @param round the number of rounds of repeated cross validation.
     * @param k k-fold cross validation.
     * @param formula the model specification.
     * @param data the training/validation data.
     * @param trainer the lambda to train a model.
     * @param <M> the model type.
     * @return the validation results.
     */
    static <M extends DataFrameClassifier> ClassificationValidations<M> stratify(int round, int k, Formula formula, DataFrame data, BiFunction<Formula, DataFrame, M> trainer) {
        if (round < 1) {
            throw new IllegalArgumentException("Invalid round: " + round);
        }

        int[] y = formula.y(data).toIntArray();
        Bag[] bags = IntStream.range(0, round)
                .mapToObj(i -> stratify(y, k))
                .flatMap(Arrays::stream)
                .toArray(Bag[]::new);
        return ClassificationValidation.of(bags, formula, data, trainer);
    }

    /**
     * Cross validation of regression.
     * @param k k-fold cross validation.
     * @param x the samples.
     * @param y the response variable.
     * @param trainer the lambda to train a model.
     * @param <T> the data type of samples.
     * @param <M> the model type.
     * @return the validation results.
     */
    static <T, M extends Regression<T>> RegressionValidations<M> regression(int k, T[] x, double[] y, BiFunction<T[], double[], M> trainer) {
        Bag[] bags = of(x.length, k);
        return RegressionValidation.of(bags, x, y, trainer);
    }

    /**
     * Cross validation of regression.
     * @param k k-fold cross validation.
     * @param formula the model specification.
     * @param data the training/validation data.
     * @param trainer the lambda to train a model.
     * @param <M> the model type.
     * @return the validation results.
     */
    static <M extends DataFrameRegression> RegressionValidations<M> regression(int k, Formula formula, DataFrame data, BiFunction<Formula, DataFrame, M> trainer) {
        Bag[] bags = of(data.size(), k);
        return RegressionValidation.of(bags, formula, data, trainer);
    }

    /**
     * Repeated cross validation of regression.
     * @param round the number of rounds of repeated cross validation.
     * @param k k-fold cross validation.
     * @param x the samples.
     * @param y the response variable.
     * @param trainer the lambda to train a model.
     * @param <T> the data type of samples.
     * @param <M> the model type.
     * @return the validation results.
     */
    static <T, M extends Regression<T>> RegressionValidations<M> regression(int round, int k, T[] x, double[] y, BiFunction<T[], double[], M> trainer) {
        if (round < 1) {
            throw new IllegalArgumentException("Invalid round: " + round);
        }

        Bag[] bags = IntStream.range(0, round)
                .mapToObj(i -> of(x.length, k))
                .flatMap(Arrays::stream)
                .toArray(Bag[]::new);
        return RegressionValidation.of(bags, x, y, trainer);
    }

    /**
     * Repeated cross validation of regression.
     * @param round the number of rounds of repeated cross validation.
     * @param k k-fold cross validation.
     * @param formula the model specification.
     * @param data the training/validation data.
     * @param trainer the lambda to train a model.
     * @param <M> the model type.
     * @return the validation results.
     */
    static <M extends DataFrameRegression> RegressionValidations<M> regression(int round, int k, Formula formula, DataFrame data, BiFunction<Formula, DataFrame, M> trainer) {
        if (round < 1) {
            throw new IllegalArgumentException("Invalid round: " + round);
        }

        Bag[] bags = IntStream.range(0, round)
                .mapToObj(i -> of(data.size(), k))
                .flatMap(Arrays::stream)
                .toArray(Bag[]::new);
        return RegressionValidation.of(bags, formula, data, trainer);
    }
}
