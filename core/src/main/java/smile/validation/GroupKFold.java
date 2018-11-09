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

public class GroupKFold {
    /**
     * The number of rounds of group K-fold.
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

        if (k < 0 || k > n) {
            throw new IllegalArgumentException("Invalid number of folds: " + k);
        }

        if (groups.length != n) {
            throw new IllegalArgumentException("Groups array must be of size n, but length is " + groups.length);
        }

        this.k = k;
        this.train = new int[k][];
        this.test = new int[k][];

        int numGroups = Math.unique(groups).length;
        if (k > numGroups) {
            throw new IllegalArgumentException("Number of splits mustn't be greater than number of groups");
        }

        // sort the groups by number of samples so that we can distribute samples from largest groups first
        int[] numSamplesPerGroup = Arrays
                .stream(Histogram.histogram(groups, numGroups)[2])
                .mapToInt(x -> (int) x)
                .toArray();
        int[] toOriginalGroupIndex = QuickSort.sort(numSamplesPerGroup);

        // distribute samples into k folds one group at a time, from the largest to the lightest group
        int[] numSamplesPerFold = new int[k];
        int[] groupToFoldIndex = new int[numGroups];

        for (int i = numSamplesPerGroup.length - 1; i >= 0; i--) {
            int lightestFoldIndex = Math.whichMin(numSamplesPerFold);
            numSamplesPerFold[lightestFoldIndex] += numSamplesPerGroup[i];
            groupToFoldIndex[toOriginalGroupIndex[i]] = lightestFoldIndex;
        }

        for (int i = 0; i < k; i++) {
            train[i] = new int[n - numSamplesPerFold[i]];
            test[i] = new int[numSamplesPerFold[i]];

            for (int j = 0, trainIndex = 0, testIndex = 0; j < n; j++) {
                if (groupToFoldIndex[groups[j]] == i) {
                    test[i][testIndex++] = j;
                }
                else {
                    train[i][trainIndex++] = j;
                }
            }
        }
    }
}
