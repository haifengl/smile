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

import smile.math.Math;

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
public class Bootstrap {
    /**
     * The number of rounds of cross validation.
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
     * @param k the number of rounds of bootstrap.
     */
    public Bootstrap(int n, int k) {
        if (n < 0) {
            throw new IllegalArgumentException("Invalid sample size: " + n);
        }

        if (k < 0) {
            throw new IllegalArgumentException("Invalid number of bootstrap: " + k);
        }

        this.k = k;
        train = new int[k][n];
        test = new int[k][];

        for (int j = 0; j < k; j++) {
            boolean[] hit = new boolean[n];
            int hits = 0;

            for (int i = 0; i < n; i++) {
                int r = Math.randomInt(n);
                train[j][i] = r;
                if (!hit[r]) {
                    hits++;
                    hit[r] = true;
                }
            }

            test[j] = new int[n - hits];
            for (int i = 0, p = 0; i < n; i++) {
                if (!hit[i]) {
                    test[j][p++] = i;
                }
            }
        }
    }
}
