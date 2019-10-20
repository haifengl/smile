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

import smile.classification.Classifier;
import smile.classification.DataFrameClassifier;
import smile.data.DataFrame;
import smile.math.MathEx;
import smile.regression.DataFrameRegression;
import smile.regression.Regression;
import smile.sort.QuickSort;

import java.util.Arrays;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * GroupKfold is a cross validation technique that splits the data by respecting additional information about groups.
 * Each sample belongs to a certain group and each group can have multiple samples. The goal of the split is then
 * to assure that no samples from the same group are present in the training and testing subsets in a single fold.
 * This is useful when the i.i.d. assumption is known to be broken by the underlying process generating the data.
 * E.g. when we have multiple samples by the same user and want to make sure that the model learns the concept
 * we are interested in rather than user-specific features that don't generalize to unseen users, this approach could
 * be used. The implementation is inspired by scikit-learn's implementation of sklearn.model_selection.GroupKFold.
 *
 * @author Nejc Ilenic
 */
public class GroupKFold {
    /**
     * The number of folds.
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
     * @param k the number of folds.
     * @param groups the group information of samples.
     */
    public GroupKFold(int n, int k, int[] groups) {
        if (n < 0) {
            throw new IllegalArgumentException("Invalid sample size: " + n);
        }

        if (k < 0) {
            throw new IllegalArgumentException("Invalid number of folds: " + k);
        }

        if (groups.length != n) {
            throw new IllegalArgumentException("Groups array must be of size n, but length is " + groups.length);
        }

        int[] uniqueGroups = MathEx.unique(groups);
        int numGroups = uniqueGroups.length;

        if (k > numGroups) {
            throw new IllegalArgumentException("Number of splits mustn't be greater than number of groups");
        }

        Arrays.sort(uniqueGroups);
        for (int i = 0; i < numGroups; i++) {
            if (uniqueGroups[i] != i) {
                throw new IllegalArgumentException("Invalid encoding of groups, all group indices between [0, numGroups) have to exist");
            }
        }

        this.k = k;
        this.train = new int[k][];
        this.test = new int[k][];
        TestFolds testFolds = this.calculateTestFolds(groups, numGroups);

        for (int i = 0; i < k; i++) {
            train[i] = new int[n - testFolds.numTestSamplesPerFold[i]];
            test[i] = new int[testFolds.numTestSamplesPerFold[i]];

            for (int j = 0, trainIndex = 0, testIndex = 0; j < n; j++) {
                if (testFolds.groupToTestFoldIndex[groups[j]] == i) {
                    test[i][testIndex++] = j;
                }
                else {
                    train[i][trainIndex++] = j;
                }
            }
        }
    }

    private TestFolds calculateTestFolds(int[] groups, int numGroups) {
        // sort the groups by number of samples so that we can distribute test samples from largest groups first
        int[] numSamplesPerGroup = new int[numGroups];
        for (int g : groups) numSamplesPerGroup[g]++;

        int[] toOriginalGroupIndex = QuickSort.sort(numSamplesPerGroup);

        // distribute test samples into k folds one group at a time, from the largest to the smallest group,
        // always putting test samples into the fold with the fewest samples
        int[] numTestSamplesPerFold = new int[k];
        int[] groupToTestFoldIndex = new int[numGroups];

        for (int i = numGroups - 1; i >= 0; i--) {
            int smallestFoldIndex = MathEx.whichMin(numTestSamplesPerFold);
            numTestSamplesPerFold[smallestFoldIndex] += numSamplesPerGroup[i];
            groupToTestFoldIndex[toOriginalGroupIndex[i]] = smallestFoldIndex;
        }

        return new TestFolds(numTestSamplesPerFold, groupToTestFoldIndex);
    }

    private class TestFolds {
        private final int[] numTestSamplesPerFold;
        private final int[] groupToTestFoldIndex;
        private TestFolds(int[] numTestSamplesPerFold, int[] groupToTestFoldIndex) {
            this.numTestSamplesPerFold = numTestSamplesPerFold;
            this.groupToTestFoldIndex = groupToTestFoldIndex;
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
    public int[] classification(DataFrame data, Function<DataFrame, DataFrameClassifier> trainer) {
        int[] prediction = new int[data.size()];

        for (int i = 0; i < k; i++) {
            DataFrameClassifier model = trainer.apply(data.of(train[i]));
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
    public double[] regression(DataFrame data, Function<DataFrame, DataFrameRegression> trainer) {
        double[] prediction = new double[data.size()];

        for (int i = 0; i < k; i++) {
            DataFrameRegression model = trainer.apply(data.of(train[i]));

            for (int j : test[i]) {
                prediction[j] = model.predict(data.get(j));
            }
        }

        return prediction;
    }
}
