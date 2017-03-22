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

package smile.sampling;

import java.util.ArrayList;
import smile.math.Math;

/**
 * Bagging (Bootstrap aggregating) is a way to improve the classification by
 * combining classifications of randomly generated training sets.
 *
 * @author Haifeng Li
 */
public class Bagging {

    /** The number of samples in this bag. */
    public int size;
    /**
     * Samples. The first column is the sample index while the second
     * column is the number of samples.
     */
    public int[][] samples;

    /**
     * Stratified sampling.
     *
     * @param k the number of classes.
     * @param y class labels.
     * @param classWeight Priors of the classes. The weight of each class
     *                    is roughly the ratio of samples in each class.
     *                    For example, if
     *                    there are 400 positive samples and 100 negative
     *                    samples, the classWeight should be [1, 4]
     *                    (assuming label 0 is of negative, label 1 is of
     *                    positive).
     * @param subsample sampling rate. Draw samples with replacement if it is 1.0.
     */
    public Bagging(int k, int[] y, int[] classWeight, double subsample) {
        int n = y.length;
        int[] sampling = new int[n];

        // Stratified sampling in case class is unbalanced.
        // That is, we sample each class separately.
        if (subsample == 1.0) {
            // Training samples draw with replacement.
            for (int l = 0; l < k; l++) {
                int nj = 0;
                ArrayList<Integer> cj = new ArrayList<>();
                for (int i = 0; i < n; i++) {
                    if (y[i] == l) {
                        cj.add(i);
                        nj++;
                    }
                }

                // We used to do up sampling.
                // But we switch to down sampling, which seems has better performance.
                int size = nj / classWeight[l];
                for (int i = 0; i < size; i++) {
                    int xi = smile.math.Math.randomInt(nj);
                    sampling[cj.get(xi)] += 1;
                }
            }
        } else {
            // Training samples draw without replacement.
            int[] perm = new int[n];
            for (int i = 0; i < n; i++) {
                perm[i] = i;
            }

            Math.permutate(perm);

            int[] nc = new int[k];
            for (int i = 0; i < n; i++) {
                nc[y[i]]++;
            }

            for (int l = 0; l < k; l++) {
                int subj = (int) Math.round(nc[l] * subsample / classWeight[l]);
                int count = 0;
                for (int i = 0; i < n && count < subj; i++) {
                    int xi = perm[i];
                    if (y[xi] == l) {
                        sampling[xi] += 1;
                        count++;
                    }
                }
            }
        }

        int m = 0;
        for (int s : sampling) {
            if (s != 0) {
                m++;
                size += s;
            }
        }

        this.samples = new int[m][2];
        for (int i = 0, l = 0; i < n; i++) {
            if (sampling[i] > 0) {
                samples[l][0] = i;
                samples[l][1] = sampling[i];
                l++;
            }
        }
    }
}
