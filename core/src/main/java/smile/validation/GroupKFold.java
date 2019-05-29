/*******************************************************************************
 * Copyright (c) 2010 Haifeng Li
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *******************************************************************************/

package smile.validation;

import smile.math.Histogram;
import smile.math.Math;
import smile.sort.QuickSort;

import java.util.Arrays;

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

        int[] uniqueGroups = Math.unique(groups);
        int numGroups = uniqueGroups.length;

        if (k > numGroups) {
            throw new IllegalArgumentException("Number of splits mustn't be greater than number of groups");
        }

        Arrays.sort(uniqueGroups);
        for (int i = 0; i < numGroups; i++) {
            if (uniqueGroups[i] != i) {
                throw new IllegalArgumentException(
                        "Invalid encoding of groups, all group indices between [0, numGroups) have to exist");
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
        int[] numSamplesPerGroup = Arrays.stream(Histogram.histogram(groups, numGroups)[2])
                .mapToInt(x -> (int) x)
                .toArray();

        int[] toOriginalGroupIndex = QuickSort.sort(numSamplesPerGroup);

        // distribute test samples into k folds one group at a time, from the largest to the smallest group,
        // always putting test samples into the fold with the fewest samples
        int[] numTestSamplesPerFold = new int[k];
        int[] groupToTestFoldIndex = new int[numGroups];

        for (int i = numGroups - 1; i >= 0; i--) {
            int smallestFoldIndex = Math.whichMin(numTestSamplesPerFold);
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
}
